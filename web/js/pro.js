/* FaceCam — PRO shooting mode (Blackmagic-Camera-inspired).
 *
 * Adds a professional HUD over the live viewfinder:
 *   - rule-of-thirds grid and a level indicator
 *   - live luminance histogram
 *   - focus peaking, zebra (over-exposure), and false-colour, all computed
 *     from the real preview frames
 *   - manual ISO / shutter / white-balance / focus controls, applied to the
 *     actual camera track where the browser exposes them, and clearly marked
 *     as unsupported where it does not
 *
 * Scopes are computed on a small 120x213 analysis canvas, which keeps it fast.
 */
const Pro = (() => {
  const AW = 120, AH = 213;               // analysis resolution
  let enabled = false;
  let running = false;
  let timer = null;
  let last = 0;
  let getTrack = () => null;

  let aCanvas, actx, oCanvas, octx, hCanvas, hctx;
  let caps = {};

  const state = {
    zebra: false, peaking: false, falseColor: false, grid: true, level: true
  };

  /* ---------- false-colour ramp (IRE-style) ---------- */
  const RAMP = [
    [0.00, [40, 40, 160]], [0.18, [60, 120, 220]], [0.32, [40, 200, 210]],
    [0.46, [60, 200, 90]], [0.60, [230, 210, 70]], [0.74, [240, 150, 50]],
    [0.86, [230, 70, 60]], [1.00, [255, 255, 255]]
  ];
  function ramp(t) {
    for (let i = 1; i < RAMP.length; i++) {
      if (t <= RAMP[i][0]) {
        const a = RAMP[i - 1], b = RAMP[i];
        const k = (t - a[0]) / (b[0] - a[0] || 1);
        return [
          a[1][0] + (b[1][0] - a[1][0]) * k,
          a[1][1] + (b[1][1] - a[1][1]) * k,
          a[1][2] + (b[1][2] - a[1][2]) * k
        ];
      }
    }
    return [255, 255, 255];
  }

  /* ---------- device orientation for the level ---------- */
  let roll = 0;
  function onOrient(e) {
    if (e.gamma == null) return;
    roll = Math.max(-45, Math.min(45, e.gamma));
    const bar = document.getElementById('pro-level-bar');
    if (bar) {
      bar.style.transform = 'rotate(' + (-roll) + 'deg)';
      bar.style.background = Math.abs(roll) < 1.5 ? '#4ade80' : '#e5e7eb';
    }
    const txt = document.getElementById('pro-level-val');
    if (txt) txt.textContent = (roll >= 0 ? '+' : '') + roll.toFixed(1) + '\u00B0';
  }

  /* ---------- manual controls ---------- */
  const CONTROLS = [
    { key: 'iso', label: 'ISO', min: 50, max: 3200, step: 50, unit: '',
      fmt: v => Math.round(v) },
    { key: 'exposureTime', label: 'Shutter', min: 0.0001, max: 0.05, step: 0.0001, unit: 's',
      fmt: v => (v * 1000).toFixed(1) + 'ms \u00B7 1/' + Math.round(1 / v) },
    { key: 'colorTemperature', label: 'WB', min: 2500, max: 9000, step: 100, unit: 'K',
      fmt: v => Math.round(v) + 'K' },
    { key: 'focusDistance', label: 'Focus', min: 0, max: 10, step: 0.05, unit: '',
      fmt: v => v.toFixed(2) }
  ];

  async function applyConstraint(key, value) {
    const track = getTrack();
    if (!track || !track.applyConstraints) return false;
    try {
      await track.applyConstraints({ advanced: [{ [key]: value }] });
      return true;
    } catch (e) { return false; }
  }

  function buildControls() {
    const wrap = document.getElementById('pro-controls');
    if (!wrap) return;
    const track = getTrack();
    caps = (track && track.getCapabilities) ? track.getCapabilities() : {};
    wrap.innerHTML = '';

    CONTROLS.forEach(c => {
      const supported = Array.isArray(caps[c.key]) || typeof caps[c.key] === 'number' ||
                        (c.key === 'iso' && Array.isArray(caps.iso));
      const row = document.createElement('div');
      row.className = 'pro-ctl' + (supported ? '' : ' off');
      const val = document.createElement('span');
      val.className = 'pro-ctl-val';
      val.textContent = supported ? '' : 'n/a';
      row.innerHTML = '<label>' + c.label + '</label>';
      const input = document.createElement('input');
      input.type = 'range';
      input.min = c.min; input.max = c.max; input.step = c.step;
      input.value = (c.min + c.max) / 2;
      input.disabled = !supported;
      const show = () => { val.textContent = c.fmt(parseFloat(input.value)); };
      input.addEventListener('input', () => {
        show();
        applyConstraint(c.key, parseFloat(input.value)).then(ok => {
          if (!ok) val.textContent += ' \u26A0';
        });
      });
      row.appendChild(input);
      row.appendChild(val);
      wrap.appendChild(row);
      if (supported) show();
    });

    const note = document.getElementById('pro-support-note');
    if (note) {
      const any = CONTROLS.some(c => Array.isArray(caps[c.key]) || typeof caps[c.key] === 'number');
      note.textContent = any
        ? 'Sliders control the real camera where the browser exposes it.'
        : 'This browser does not expose manual camera controls \u2014 sliders are inert here. Scopes below are still live.';
    }
  }

  /* ---------- the render loop ---------- */
  function frame() {
    if (!running) return;
    const ts = Date.now();
    if (ts - last < 90) return;            // ~11 fps is plenty for scopes
    last = ts;

    const video = document.getElementById('video');
    const demo = document.getElementById('demo-canvas');
    const src = (demo && demo.style.display === 'block') ? demo : video;
    if (!src) return;
    const sw = src.videoWidth || src.width;
    const sh = src.videoHeight || src.height;
    if (!sw || !sh) return;

    actx.drawImage(src, 0, 0, AW, AH);
    const img = actx.getImageData(0, 0, AW, AH);
    const d = img.data;

    // luminance + histogram
    const luma = new Uint8ClampedArray(AW * AH);
    const bins = new Uint32Array(64);
    for (let i = 0, p = 0; i < d.length; i += 4, p++) {
      const l = (0.2126 * d[i] + 0.7152 * d[i + 1] + 0.0722 * d[i + 2]) | 0;
      luma[p] = l;
      bins[Math.min(63, l >> 2)]++;
    }
    drawHistogram(bins, AW * AH);

    // overlay: peaking / zebra / false colour
    const out = octx.createImageData(AW, AH);
    const o = out.data;
    const wantOverlay = state.peaking || state.zebra || state.falseColor;

    if (state.falseColor) {
      for (let p = 0, i = 0; p < luma.length; p++, i += 4) {
        const c = ramp(luma[p] / 255);
        o[i] = c[0]; o[i + 1] = c[1]; o[i + 2] = c[2]; o[i + 3] = 235;
      }
    } else if (wantOverlay) {
      for (let y = 0; y < AH; y++) {
        for (let x = 0; x < AW; x++) {
          const p = y * AW + x, i = p * 4;
          o[i + 3] = 0;
          if (state.zebra && luma[p] > 232) {
            if (((x + y) % 8) < 4) { o[i] = 255; o[i + 1] = 255; o[i + 2] = 255; o[i + 3] = 150; }
          }
          if (state.peaking && x > 0 && y > 0 && x < AW - 1 && y < AH - 1) {
            const gx = luma[p + 1] - luma[p - 1];
            const gy = luma[p + AW] - luma[p - AW];
            const mag = Math.abs(gx) + Math.abs(gy);
            if (mag > 90) { o[i] = 60; o[i + 1] = 255; o[i + 2] = 120; o[i + 3] = 200; }
          }
        }
      }
    }

    if (wantOverlay) octx.putImageData(out, 0, 0);
    else octx.clearRect(0, 0, AW, AH);
  }

  function drawHistogram(bins, total) {
    if (!hctx) return;
    const w = hCanvas.width, h = hCanvas.height;
    hctx.clearRect(0, 0, w, h);
    hctx.fillStyle = 'rgba(0,0,0,0.45)';
    hctx.fillRect(0, 0, w, h);
    let max = 1;
    for (let i = 0; i < 64; i++) if (bins[i] > max) max = bins[i];
    hctx.fillStyle = '#7dd3fc';
    const bw = w / 64;
    for (let i = 0; i < 64; i++) {
      const bh = (bins[i] / max) * (h - 4);
      hctx.fillRect(i * bw, h - bh, Math.max(1, bw - 1), bh);
    }
    hctx.strokeStyle = 'rgba(255,255,255,0.18)';
    hctx.beginPath(); hctx.moveTo(w / 2, 0); hctx.lineTo(w / 2, h); hctx.stroke();
  }

  /* ---------- public ---------- */
  function init(opts) {
    if (opts && opts.getTrack) getTrack = opts.getTrack;

    aCanvas = document.createElement('canvas'); aCanvas.width = AW; aCanvas.height = AH;
    actx = aCanvas.getContext('2d', { willReadFrequently: true });

    oCanvas = document.getElementById('pro-canvas');
    if (oCanvas) { oCanvas.width = AW; oCanvas.height = AH; octx = oCanvas.getContext('2d'); }

    hCanvas = document.getElementById('pro-hist');
    if (hCanvas) { hCanvas.width = 160; hCanvas.height = 56; hctx = hCanvas.getContext('2d'); }

    document.querySelectorAll('[data-pro-toggle]').forEach(btn => {
      const key = btn.getAttribute('data-pro-toggle');
      btn.addEventListener('click', () => {
        state[key] = !state[key];
        btn.classList.toggle('on', state[key]);
        apply();
      });
    });

    const grid = document.getElementById('pro-grid');
    if (grid) grid.style.display = 'block';

    window.addEventListener('deviceorientation', onOrient, true);
  }

  function apply() {
    const grid = document.getElementById('pro-grid');
    if (grid) grid.style.display = state.grid ? 'block' : 'none';
    const lvl = document.getElementById('pro-level');
    if (lvl) lvl.style.display = state.level ? 'flex' : 'none';
    const can = document.getElementById('pro-canvas');
    if (can) can.style.display = (state.peaking || state.zebra || state.falseColor) ? 'block' : 'none';
  }

  function setEnabled(v) {
    enabled = !!v;
    const hud = document.getElementById('pro-hud');
    if (hud) hud.classList.toggle('show', enabled);
    if (enabled) {
      buildControls();
      apply();
      if (!running) { running = true; timer = setInterval(frame, 100); }
    } else {
      running = false;
      if (timer) clearInterval(timer);
      timer = null;
      const can = document.getElementById('pro-canvas');
      if (can) octx && octx.clearRect(0, 0, AW, AH);
    }
  }

  function isEnabled() { return enabled; }
  function refresh() { if (enabled) buildControls(); }

  return { init, setEnabled, isEnabled, refresh };
})();
