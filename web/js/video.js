/* FaceCam — video engine.
 *
 * Records video with the selected camera look applied in real time, and with
 * the effects *animated* frame by frame (they are not static overlays):
 *   moving grain · drifting dust · random scratches · flicker · animated light
 *   leaks · film burn · frame jitter · focus breathing · VHS scanlines and
 *   tracking · chromatic aberration · digital glitches · a retro timestamp.
 *
 * Also: resolution (720/1080/2160) and frame-rate (24/30/60) selection, a
 * front-camera mirror, pause/resume, optional vintage/VHS/cassette audio
 * treatment (Web Audio), slow-motion baking, and a "photo-consistent" bake that
 * re-renders a clip through the exact photo pipeline.
 *
 * Everything is local. Nothing is uploaded.
 */
const Video = (() => {
  let opts = {
    getSource: () => null, getPreset: () => null, getSettings: () => ({}),
    getIntensity: () => 1, getMirror: () => false, getRes: () => 'source',
    getFps: () => 30, getAudioStyle: () => 'original'
  };

  let canvas = null, ctx = null, noise = null, bloom = null, leak = null;
  let recorder = null, chunks = [];
  let frameTimer = null;
  let micStream = null, audioCtx = null, audioNodes = null;
  let recording = false, paused = false, pausedAt = 0;
  let startedAt = 0, frameIndex = 0, burnStart = -1;
  let caption = '', liveText = '';
  let recognition = null, liveOn = false;

  function init(o) { opts = Object.assign(opts, o || {}); }

  function supported() {
    return typeof MediaRecorder !== 'undefined' &&
           typeof HTMLCanvasElement !== 'undefined' &&
           !!HTMLCanvasElement.prototype.captureStream;
  }

  function pickMime() {
    const list = [
      'video/webm;codecs=vp9,opus', 'video/webm;codecs=vp8,opus',
      'video/webm;codecs=vp9', 'video/webm;codecs=vp8',
      'video/webm', 'video/mp4'
    ];
    for (const m of list) {
      try { if (MediaRecorder.isTypeSupported(m)) return m; } catch (e) {}
    }
    return '';
  }

  function hexToRgba(hex, a) {
    const h = String(hex || '#ffffff').replace('#', '');
    const n = parseInt(h.length === 3 ? h.split('').map(c => c + c).join('') : h, 16);
    return 'rgba(' + ((n >> 16) & 255) + ',' + ((n >> 8) & 255) + ',' + (n & 255) + ',' + a + ')';
  }

  /* Per-preset animated-effect profile. Ids not listed fall back to a mild
     film profile derived from the preset's own grain/dust/leak/vignette. */
  function fxFor(preset) {
    const id = preset && preset.id;
    const base = {
      grain: (preset && preset.grain) || 0,
      dust: (preset && preset.dust) || 0,
      vignette: (preset && preset.vignette) || 0,
      leak: (preset && preset.leak) || 0,
      halation: (preset && preset.halation) || 0,
      scratches: 0, flicker: 0, jitter: 0, burn: 0, breathing: 0,
      scanlines: 0, tracking: 0, aberration: 0, glitch: 0
    };
    const map = {
      vhs: { scanlines: 0.55, aberration: 0.5, glitch: 0.30, tracking: 0.40, flicker: 0.25, dust: 0.20, grain: 0.5 },
      old_digital: { aberration: 0.35, glitch: 0.22, flicker: 0.20, dust: 0.30 },
      minidv: { aberration: 0.28, glitch: 0.14, flicker: 0.22, dust: 0.20 },
      film_8mm: { dust: 0.90, scratches: 1.00, flicker: 0.50, jitter: 1.00, burn: 0.50 },
      super_8: { dust: 0.70, scratches: 0.70, flicker: 0.60, jitter: 0.70, burn: 0.40, breathing: 0.50 },
      vintage_90s: { jitter: 0.40, flicker: 0.15 },
      flash_cam: { flicker: 0.40 },
      night_film: { flicker: 0.30, burn: 0.20 },
      cinematic: { breathing: 0.35, dust: 0.12 }
    };
    return Object.assign(base, map[id] || {});
  }

  function initLeak(preset) {
    const colors = (preset && preset.leakColors) || ['#ffcf7a', '#ff9d5c'];
    leak = {
      edge: Math.floor(Math.random() * 4),
      span: 0.35 + Math.random() * 0.3,
      color: colors[Math.floor(Math.random() * colors.length)]
    };
  }

  function buildNoise() {
    const n = document.createElement('canvas');
    n.width = 180; n.height = 180;
    noise = n;
    randomizeNoise();
  }
  function randomizeNoise() {
    const c = noise.getContext('2d');
    const img = c.createImageData(180, 180);
    for (let i = 0; i < img.data.length; i += 4) {
      const v = 128 + (Math.random() * 2 - 1) * 127;
      img.data[i] = img.data[i + 1] = img.data[i + 2] = v;
      img.data[i + 3] = 255;
    }
    c.putImageData(img, 0, 0);
  }

  function dustAndScratches(w, h, fx, k, t) {
    ctx.save();
    const n = Math.floor(fx.dust * 60 * k);
    for (let i = 0; i < n; i++) {
      const seed = i * 97.13;
      const x = (((Math.sin(seed) * 0.5 + 0.5) * w) + t * 0.02 * (i % 3)) % w;
      const y = (((Math.cos(seed * 1.7) * 0.5 + 0.5) * h) + t * 0.012 * (1 + (i % 4))) % h;
      const r = 0.4 + (i % 5) * 0.25;
      ctx.globalAlpha = 0.10 + (i % 7) * 0.03;
      ctx.fillStyle = (i % 2) ? '#ffffff' : '#101010';
      ctx.beginPath(); ctx.arc(x, y, r, 0, 6.283); ctx.fill();
    }
    if (fx.scratches > 0) {
      const sn = Math.max(1, Math.floor(fx.scratches * 3 * k));
      for (let i = 0; i < sn; i++) {
        const x = Math.random() * w;
        ctx.globalAlpha = 0.05 + Math.random() * 0.12;
        ctx.strokeStyle = Math.random() < 0.5 ? '#ffffff' : '#141414';
        ctx.lineWidth = 0.6 + Math.random();
        ctx.beginPath();
        ctx.moveTo(x, Math.random() * h * 0.3);
        ctx.lineTo(x + (Math.random() - 0.5) * 8, h - Math.random() * h * 0.3);
        ctx.stroke();
      }
    }
    ctx.restore();
  }

  function drawFrame(now, srcOverride, presetOverride) {
    const src = srcOverride || opts.getSource();
    const preset = presetOverride || opts.getPreset();
    if (!src || !canvas || !ctx) return;
    const sw = src.videoWidth || src.width;
    const sh = src.videoHeight || src.height;
    if (!sw || !sh) return;

    const w = canvas.width, h = canvas.height;
    const t = now || (typeof performance !== 'undefined' ? performance.now() : Date.now());
    const k = opts.getIntensity ? opts.getIntensity() : 1;
    const vintage = ((preset && preset.group) || 'vintage') !== 'beauty';
    const mirror = opts.getMirror ? opts.getMirror() : false;
    const fx = fxFor(preset);
    frameIndex++;

    const jx = fx.jitter ? (Math.sin(t * 0.013) * w * 0.0025 + (Math.random() - 0.5) * w * 0.002) * fx.jitter : 0;
    const jy = fx.jitter ? (Math.cos(t * 0.017) * h * 0.0022 + (Math.random() - 0.5) * h * 0.002) * fx.jitter : 0;
    const bs = fx.breathing ? 1 + Math.sin(t * 0.0025) * 0.01 * fx.breathing : 1;
    const flick = fx.flicker ? 1 + (Math.random() - 0.5) * 0.10 * fx.flicker : 1;

    // ---- base frame (look + jitter + breathing + mirror + flicker) ----
    ctx.save();
    ctx.filter = ((preset && preset.filter) || 'none') + (flick !== 1 ? ' brightness(' + flick.toFixed(3) + ')' : '');
    ctx.translate(jx, jy);
    if (fx.breathing) { ctx.translate(w / 2, h / 2); ctx.scale(bs, bs); ctx.translate(-w / 2, -h / 2); }
    if (mirror) { ctx.translate(w, 0); ctx.scale(-1, 1); }
    ctx.drawImage(src, 0, 0, w, h);
    ctx.restore();

    if (vintage) {
      // halation / bloom
      if (fx.halation > 0) {
        const hw = Math.max(8, Math.round(w * 0.13)), hh = Math.max(8, Math.round(h * 0.13));
        if (!bloom) bloom = document.createElement('canvas');
        bloom.width = hw; bloom.height = hh;
        const bc = bloom.getContext('2d');
        bc.filter = 'brightness(1.5) contrast(2.4) blur(2px)';
        bc.drawImage(canvas, 0, 0, hw, hh);
        bc.filter = 'none';
        ctx.save();
        ctx.globalCompositeOperation = 'screen';
        ctx.globalAlpha = Math.min(0.4, fx.halation * 0.30 * k);
        ctx.drawImage(bloom, 0, 0, w, h);
        ctx.restore();
      }

      // chromatic aberration (doubled frame)
      if (fx.aberration > 0) {
        const off = Math.max(1, Math.round(w * 0.0015 * (1 + fx.aberration * 3)));
        ctx.save();
        ctx.globalCompositeOperation = 'screen';
        ctx.globalAlpha = 0.14 * fx.aberration;
        ctx.drawImage(canvas, off, 0, w, h);
        ctx.drawImage(canvas, -off, 0, w, h);
        ctx.restore();
      }

      // moving grain
      if (fx.grain > 0) {
        if (!noise) buildNoise(); else randomizeNoise();
        ctx.save();
        ctx.globalAlpha = Math.min(0.5, fx.grain * 0.45 * k);
        ctx.globalCompositeOperation = 'overlay';
        ctx.fillStyle = ctx.createPattern(noise, 'repeat');
        ctx.fillRect(0, 0, w, h);
        ctx.restore();
      }

      // drifting dust + random scratches
      if (fx.dust > 0 || fx.scratches > 0) dustAndScratches(w, h, fx, k, t);

      // animated light leak
      if (fx.leak > 0 && leak) {
        const drift = 1 + Math.sin(t * 0.0006) * 0.25;
        const a = Math.min(0.6, fx.leak * 0.42 * k) * (0.75 + 0.25 * Math.sin(t * 0.0011));
        ctx.save();
        ctx.globalCompositeOperation = 'screen';
        let lg;
        if (leak.edge === 0) lg = ctx.createLinearGradient(0, 0, w * leak.span * drift, 0);
        else if (leak.edge === 1) lg = ctx.createLinearGradient(w, 0, w * (1 - leak.span * drift), 0);
        else if (leak.edge === 2) lg = ctx.createLinearGradient(0, 0, 0, h * leak.span * drift);
        else lg = ctx.createLinearGradient(0, h, 0, h * (1 - leak.span * drift));
        lg.addColorStop(0, hexToRgba(leak.color, a));
        lg.addColorStop(0.5, hexToRgba(leak.color, a * 0.35));
        lg.addColorStop(1, 'rgba(0,0,0,0)');
        ctx.fillStyle = lg;
        ctx.fillRect(0, 0, w, h);
        ctx.restore();
      }

      // film burn (occasional, fading)
      if (fx.burn > 0) {
        if (burnStart < 0 && Math.random() < 0.015 * fx.burn) burnStart = t;
        if (burnStart >= 0) {
          const age = t - burnStart;
          if (age > 600) { burnStart = -1; }
          else {
            const ba = (1 - age / 600) * 0.5 * fx.burn;
            const bx = (Math.sin(burnStart) * 0.5 + 0.5) * w;
            const by = (Math.cos(burnStart * 1.3) * 0.5 + 0.5) * h;
            const rad = Math.max(w, h) * 0.35;
            const bg = ctx.createRadialGradient(bx, by, 0, bx, by, rad);
            bg.addColorStop(0, 'rgba(255,214,150,' + ba.toFixed(3) + ')');
            bg.addColorStop(1, 'rgba(255,180,90,0)');
            ctx.save();
            ctx.globalCompositeOperation = 'screen';
            ctx.fillStyle = bg;
            ctx.fillRect(0, 0, w, h);
            ctx.restore();
          }
        }
      }

      // VHS scanlines
      if (fx.scanlines > 0) {
        ctx.save();
        ctx.globalAlpha = 0.12 * fx.scanlines;
        ctx.fillStyle = '#000';
        const step = 4;
        const off = (t * 0.05) % (step * 2);
        for (let y = -off; y < h; y += step * 2) ctx.fillRect(0, y, w, step);
        ctx.restore();
      }

      // VHS tracking band
      if (fx.tracking > 0) {
        const bandY = ((t * 0.05) % (h * 1.2)) - h * 0.1;
        const bh = h * 0.06;
        const dx = Math.sin(t * 0.01) * w * 0.012 * fx.tracking;
        ctx.save();
        ctx.globalAlpha = 0.55;
        ctx.drawImage(canvas, 0, bandY, w, bh, dx, bandY, w, bh);
        ctx.globalAlpha = 0.20 * fx.tracking;
        ctx.fillStyle = '#ffffff';
        ctx.fillRect(0, bandY, w, bh * 0.16);
        ctx.restore();
      }

      // digital glitch (occasional slice displacement)
      if (fx.glitch > 0 && Math.random() < 0.03 * fx.glitch) {
        const gy = Math.random() * h, gh = h * (0.02 + Math.random() * 0.06);
        const dx = (Math.random() - 0.5) * w * 0.05 * fx.glitch;
        ctx.save();
        ctx.drawImage(canvas, 0, gy, w, gh, dx, gy, w, gh);
        ctx.restore();
      }

      // vignette
      const g = ctx.createRadialGradient(w / 2, h / 2, Math.min(w, h) * 0.30, w / 2, h / 2, Math.max(w, h) * 0.72);
      g.addColorStop(0, 'rgba(0,0,0,0)');
      g.addColorStop(1, 'rgba(0,0,0,' + Math.min(0.85, 0.10 + fx.vignette * 0.55 * k).toFixed(3) + ')');
      ctx.fillStyle = g;
      ctx.fillRect(0, 0, w, h);

      // frame
      const f = preset.frame;
      if (f && f.style && f.style !== 'none') {
        const min = Math.min(w, h);
        ctx.save();
        if (f.style === 'instant') {
          const b = Math.round(w * 0.055), bottom = Math.round(b * 3.2);
          ctx.fillStyle = f.color;
          ctx.fillRect(0, 0, w, b); ctx.fillRect(0, h - bottom, w, bottom);
          ctx.fillRect(0, 0, b, h); ctx.fillRect(w - b, 0, b, h);
        } else if (f.style === 'thick') {
          const b = Math.round(min * 0.05);
          ctx.strokeStyle = f.color; ctx.lineWidth = b;
          ctx.strokeRect(b / 2, b / 2, w - b, h - b);
        } else if (f.style === 'cinema') {
          const b = Math.round(h * 0.06);
          ctx.fillStyle = f.color;
          ctx.fillRect(0, 0, w, b); ctx.fillRect(0, h - b, w, b);
        } else {
          const b = Math.max(6, Math.round(min * 0.025));
          ctx.strokeStyle = f.color; ctx.lineWidth = b;
          ctx.strokeRect(b / 2, b / 2, w - b, h - b);
        }
        ctx.restore();
      }

      // burned-in retro timestamp (same renderer as photos)
      const settings = opts.getSettings() || {};
      if (settings.dateStamp && preset.dateStamp && typeof Effects !== 'undefined' && Effects.dateStamp) {
        Effects.dateStamp(ctx, w, h, settings);
      }
    }

    // burned-in caption / live subtitle
    const line = (caption || '').trim() || (liveText || '').trim();
    if (line) {
      const size = Math.max(16, Math.round(w * 0.048));
      ctx.save();
      ctx.font = '700 ' + size + 'px -apple-system, Segoe UI, Roboto, sans-serif';
      ctx.textAlign = 'center';
      ctx.textBaseline = 'alphabetic';
      const tw = ctx.measureText(line).width;
      const padX = size * 0.7, padY = size * 0.55;
      const bx = w / 2 - tw / 2 - padX;
      const by = h - Math.round(h * 0.13) - size - padY * 2;
      ctx.fillStyle = 'rgba(0,0,0,0.55)';
      const r = size * 0.5;
      const bw = tw + padX * 2, bh = size + padY * 2;
      ctx.beginPath();
      ctx.moveTo(bx + r, by);
      ctx.arcTo(bx + bw, by, bx + bw, by + bh, r);
      ctx.arcTo(bx + bw, by + bh, bx, by + bh, r);
      ctx.arcTo(bx, by + bh, bx, by, r);
      ctx.arcTo(bx, by, bx + bw, by, r);
      ctx.closePath();
      ctx.fill();
      ctx.fillStyle = '#ffffff';
      ctx.fillText(line, w / 2, by + bh - padY);
      ctx.restore();
    }
  }

  /* ---------- audio treatment (Web Audio) ---------- */
  function makeSatCurve(amount) {
    const n = 1024, curve = new Float32Array(n);
    const k = amount * 3;
    for (let i = 0; i < n; i++) {
      const x = (i / (n - 1)) * 2 - 1;
      curve[i] = (1 + k) * x / (1 + k * Math.abs(x));
    }
    return curve;
  }

  function buildAudioTrack(mic, style) {
    if (!style || style === 'original' || !window.AudioContext) return null;
    try {
      audioCtx = new (window.AudioContext || window.webkitAudioContext)();
      const srcNode = audioCtx.createMediaStreamSource(mic);
      const dest = audioCtx.createMediaStreamDestination();
      const hp = audioCtx.createBiquadFilter(); hp.type = 'highpass';
      const lp = audioCtx.createBiquadFilter(); lp.type = 'lowpass';
      if (style === 'vintage') { hp.frequency.value = 300; lp.frequency.value = 5000; }
      else if (style === 'vhs') { hp.frequency.value = 180; lp.frequency.value = 6500; }
      else { hp.frequency.value = 120; lp.frequency.value = 7500; }
      const shaper = audioCtx.createWaveShaper();
      shaper.curve = makeSatCurve(style === 'cassette' ? 1.6 : 0.9);
      shaper.oversample = '2x';
      const gain = audioCtx.createGain(); gain.gain.value = 0.98;
      srcNode.connect(hp); hp.connect(lp); lp.connect(shaper); shaper.connect(gain); gain.connect(dest);
      let hiss = null;
      if (style === 'vhs' || style === 'cassette') {
        const buf = audioCtx.createBuffer(1, audioCtx.sampleRate * 2, audioCtx.sampleRate);
        const d = buf.getChannelData(0);
        for (let i = 0; i < d.length; i++) d[i] = (Math.random() * 2 - 1) * 0.012;
        hiss = audioCtx.createBufferSource(); hiss.buffer = buf; hiss.loop = true;
        const ng = audioCtx.createGain(); ng.gain.value = style === 'cassette' ? 0.5 : 0.35;
        hiss.connect(ng); ng.connect(dest); hiss.start();
      }
      audioNodes = { hp, lp, shaper, gain, hiss };
      return dest.stream.getAudioTracks()[0];
    } catch (e) { teardownAudio(); return null; }
  }

  function teardownAudio() {
    try { if (audioNodes && audioNodes.hiss) audioNodes.hiss.stop(); } catch (e) {}
    try { if (audioCtx) audioCtx.close(); } catch (e) {}
    audioCtx = null; audioNodes = null;
  }

  function targetDims(sw, sh) {
    const res = opts.getRes ? opts.getRes() : 'source';
    if (!res || res === 'source') return { w: sw, h: sh };
    const targetH = parseInt(res, 10);
    if (!targetH) return { w: sw, h: sh };
    const scale = targetH / sh;
    return { w: Math.round(sw * scale / 2) * 2, h: Math.round(sh * scale / 2) * 2 };
  }

  async function start() {
    if (recording) return false;
    if (!supported()) return false;
    const src = opts.getSource();
    if (!src) return false;
    const sw = src.videoWidth || src.width || 720;
    const sh = src.videoHeight || src.height || 1280;

    const dims = targetDims(sw, sh);
    canvas = document.createElement('canvas');
    canvas.width = dims.w; canvas.height = dims.h;
    ctx = canvas.getContext('2d', { willReadFrequently: false });

    frameIndex = 0; burnStart = -1;
    initLeak(opts.getPreset());
    drawFrame();

    const fps = Math.max(1, opts.getFps ? opts.getFps() : 30);
    const cstream = canvas.captureStream(fps);

    // microphone: never let it block the recording
    micStream = null;
    try {
      micStream = await Promise.race([
        navigator.mediaDevices.getUserMedia({ audio: true }),
        new Promise((_, rej) => setTimeout(() => rej(new Error('mic-timeout')), 2500))
      ]);
      const style = opts.getAudioStyle ? opts.getAudioStyle() : 'original';
      const processed = (style && style !== 'original') ? buildAudioTrack(micStream, style) : null;
      if (processed) cstream.addTrack(processed);
      else micStream.getAudioTracks().forEach(t => cstream.addTrack(t));
    } catch (e) { micStream = null; }

    const mime = pickMime();
    try { recorder = new MediaRecorder(cstream, mime ? { mimeType: mime } : undefined); }
    catch (e) { recorder = new MediaRecorder(cstream); }
    chunks = [];
    recorder.ondataavailable = e => { if (e.data && e.data.size) chunks.push(e.data); };

    frameTimer = setInterval(drawFrame, Math.round(1000 / fps));
    recorder.start(250);
    recording = true; paused = false;
    startedAt = Date.now();
    return true;
  }

  function elapsed() {
    if (!recording) return 0;
    return (paused ? pausedAt : Date.now()) - startedAt;
  }

  function pause() {
    if (!recorder || !recording || paused) return false;
    try { recorder.pause(); } catch (e) { return false; }
    paused = true; pausedAt = Date.now();
    return true;
  }

  function resume() {
    if (!recorder || !recording || !paused) return false;
    try { recorder.resume(); } catch (e) { return false; }
    startedAt += Date.now() - pausedAt;
    paused = false;
    return true;
  }

  function stop() {
    return new Promise((resolve) => {
      if (!recorder) { resolve(null); return; }
      recorder.onstop = () => {
        const type = recorder.mimeType || 'video/webm';
        const blob = new Blob(chunks, { type });
        cleanup();
        resolve(blob);
      };
      try { recorder.stop(); } catch (e) { cleanup(); resolve(null); }
    });
  }

  function cleanup() {
    recording = false; paused = false;
    if (frameTimer) { clearInterval(frameTimer); frameTimer = null; }
    teardownAudio();
    if (micStream) { micStream.getTracks().forEach(t => t.stop()); micStream = null; }
    recorder = null;
    chunks = [];
  }

  function isRecording() { return recording; }
  function isPaused() { return paused; }

  /* ---------- slow motion: re-record the clip at a slower playback rate ---------- */
  function bakeSlowMotion(blob, rate) {
    return new Promise((resolve, reject) => {
      if (!supported() || rate === 1) { resolve(blob); return; }
      const url = URL.createObjectURL(blob);
      const v = document.createElement('video');
      v.src = url; v.muted = true; v.playsInline = true; v.preload = 'auto';
      v.onloadedmetadata = () => {
        const c = document.createElement('canvas');
        c.width = v.videoWidth || 720;
        c.height = v.videoHeight || 1280;
        const g = c.getContext('2d');
        const out = c.captureStream(30);
        const mime = pickMime();
        let mr;
        try { mr = new MediaRecorder(out, mime ? { mimeType: mime } : undefined); }
        catch (e) { mr = new MediaRecorder(out); }
        const parts = [];
        mr.ondataavailable = e => { if (e.data && e.data.size) parts.push(e.data); };
        mr.onstop = () => { URL.revokeObjectURL(url); resolve(new Blob(parts, { type: mr.mimeType || 'video/webm' })); };
        let t = null;
        const draw = () => { if (v.ended) return; g.drawImage(v, 0, 0, c.width, c.height); };
        v.onended = () => { if (t) clearInterval(t); setTimeout(() => { try { mr.stop(); } catch (e) {} }, 250); };
        mr.start(250);
        v.playbackRate = rate;
        v.play().then(() => { t = setInterval(draw, 33); })
          .catch(() => { try { mr.stop(); } catch (e) {} reject(new Error('playback failed')); });
      };
      v.onerror = () => reject(new Error('video load failed'));
    });
  }

  /* ---------- photo-consistent bake ----------
   * Re-renders a recorded clip frame by frame through the exact photo pipeline
   * (Effects.process), so a video matches the still photo of the same camera. */
  function bakeLook(blob, preset, settings) {
    return new Promise((resolve, reject) => {
      if (!supported() || !preset || typeof Effects === 'undefined') { resolve(blob); return; }
      const url = URL.createObjectURL(blob);
      const v = document.createElement('video');
      v.src = url; v.muted = true; v.playsInline = true; v.preload = 'auto';
      v.onloadedmetadata = () => {
        const c = document.createElement('canvas');
        c.width = v.videoWidth || 720;
        c.height = v.videoHeight || 1280;
        const g = c.getContext('2d', { willReadFrequently: true });
        const out = c.captureStream(30);
        const mime = pickMime();
        let mr;
        try { mr = new MediaRecorder(out, mime ? { mimeType: mime } : undefined); }
        catch (e) { mr = new MediaRecorder(out); }
        const parts = [];
        mr.ondataavailable = e => { if (e.data && e.data.size) parts.push(e.data); };
        mr.onstop = () => { URL.revokeObjectURL(url); resolve(new Blob(parts, { type: mr.mimeType || 'video/webm' })); };
        let t = null;
        const draw = () => {
          if (v.ended) return;
          g.filter = (preset.filter) || 'none';
          g.drawImage(v, 0, 0, c.width, c.height);
          g.filter = 'none';
          if (preset.group === 'beauty') Effects.applyBeauty(c, preset.beauty, (settings && settings.intensity) || 1);
          else Effects.process(c, preset, settings || {});
        };
        v.onended = () => { if (t) clearInterval(t); setTimeout(() => { try { mr.stop(); } catch (e) {} }, 250); };
        mr.start(250);
        v.play().then(() => { t = setInterval(draw, 33); })
          .catch(() => { try { mr.stop(); } catch (e) {} reject(new Error('playback failed')); });
      };
      v.onerror = () => reject(new Error('video load failed'));
    });
  }

  /* ---------- live captions (Web Speech API, on-device where available) ---------- */
  function liveSupported() {
    return !!(window.SpeechRecognition || window.webkitSpeechRecognition);
  }

  function setLiveCaptions(on, onText) {
    liveOn = !!on;
    if (liveOn) {
      const SR = window.SpeechRecognition || window.webkitSpeechRecognition;
      if (!SR) return false;
      try {
        recognition = new SR();
        recognition.continuous = true;
        recognition.interimResults = true;
        recognition.lang = 'en-IN';
        recognition.onresult = (ev) => {
          let txt = '';
          for (let i = ev.resultIndex; i < ev.results.length; i++) txt += ev.results[i][0].transcript;
          liveText = txt.trim().split(' ').slice(-8).join(' ');
          if (onText) onText(liveText);
        };
        recognition.onerror = () => {};
        recognition.onend = () => { if (liveOn) { try { recognition.start(); } catch (e) {} } };
        recognition.start();
        return true;
      } catch (e) { return false; }
    }
    if (recognition) { try { recognition.stop(); } catch (e) {} recognition = null; }
    liveText = '';
    return true;
  }

  function setCaption(t) { caption = t || ''; }
  function getCaption() { return caption; }

  return {
    init, supported, start, stop, isRecording, isPaused, elapsed, pause, resume,
    bakeSlowMotion, bakeLook, setCaption, getCaption, fxFor,
    liveSupported, setLiveCaptions,
    _test: {
      drawFrame, fxFor,
      /* Render one frame of a preset onto a throwaway canvas and return a pixel
         hash — two calls with different times differ when effects animate. */
      renderOnce: (preset, w, h, t) => {
        const sc = document.createElement('canvas'); sc.width = w; sc.height = h;
        const sg = sc.getContext('2d');
        const grad = sg.createLinearGradient(0, 0, 0, h);
        grad.addColorStop(0, '#1d3d72'); grad.addColorStop(0.5, '#e2814a'); grad.addColorStop(1, '#6b4a3a');
        sg.fillStyle = grad; sg.fillRect(0, 0, w, h);
        sg.fillStyle = '#fff3cd'; sg.beginPath(); sg.arc(w * 0.6, h * 0.35, Math.min(w, h) * 0.18, 0, 6.283); sg.fill();
        const c = document.createElement('canvas'); c.width = w; c.height = h;
        const g = c.getContext('2d', { willReadFrequently: true });
        const savedCanvas = canvas, savedCtx = ctx;
        canvas = c; ctx = g;
        drawFrame(t || 0, sc, preset);
        canvas = savedCanvas; ctx = savedCtx;
        const d = g.getImageData(0, 0, w, h).data;
        let hsh = 0;
        for (let i = 0; i < d.length; i += 17) hsh = (hsh * 31 + d[i]) % 2147483647;
        return hsh;
      }
    }
  };
})();
