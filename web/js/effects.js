/* FaceCam — analog film effects engine.
 * Pure canvas. Colour curve comes from the preset's CSS filter; everything else
 * (grain, light leak, vignette, dust/scratches, frame, date stamp) is drawn here
 * with randomised intensity so no two photos look identical.
 */
const Effects = (() => {
  const rand = (a, b) => a + Math.random() * (b - a);
  const randInt = (a, b) => Math.floor(rand(a, b + 1));
  const pick = (arr) => arr[Math.floor(Math.random() * arr.length)];

  function drawCover(ctx, source, w, h) {
    const sw = source.videoWidth || source.naturalWidth || source.width;
    const sh = source.videoHeight || source.naturalHeight || source.height;
    if (!sw || !sh) return;
    const scale = Math.max(w / sw, h / sh);
    const dw = sw * scale, dh = sh * scale;
    ctx.drawImage(source, (w - dw) / 2, (h - dh) / 2, dw, dh);
  }

  /* Grab a frame from a video/image and apply the colour curve. */
  function capture(source, w, h, filter) {
    const c = document.createElement('canvas');
    c.width = w; c.height = h;
    const ctx = c.getContext('2d');
    ctx.filter = filter || 'none';
    drawCover(ctx, source, w, h);
    ctx.filter = 'none';
    return c;
  }

  function grain(ctx, w, h, amount) {
    if (amount <= 0) return;
    const size = 160;
    const n = document.createElement('canvas');
    n.width = size; n.height = size;
    const nctx = n.getContext('2d');
    const img = nctx.createImageData(size, size);
    for (let i = 0; i < img.data.length; i += 4) {
      const v = 128 + (Math.random() * 2 - 1) * 127;
      img.data[i] = img.data[i + 1] = img.data[i + 2] = v;
      img.data[i + 3] = 255;
    }
    nctx.putImageData(img, 0, 0);
    ctx.save();
    ctx.globalAlpha = Math.min(0.85, amount * 0.55);
    ctx.globalCompositeOperation = 'overlay';
    const pat = ctx.createPattern(n, 'repeat');
    ctx.fillStyle = pat;
    ctx.fillRect(0, 0, w, h);
    ctx.restore();
  }

  function lightLeak(ctx, w, h, amount, colors) {
    if (amount <= 0 || !colors || !colors.length) return;
    ctx.save();
    ctx.globalCompositeOperation = 'screen';
    const c = pick(colors);
    const edge = randInt(0, 3);
    let g;
    if (edge === 0) g = ctx.createLinearGradient(0, 0, w * rand(0.35, 0.6), 0);
    else if (edge === 1) g = ctx.createLinearGradient(w, 0, w * rand(0.4, 0.65), 0);
    else if (edge === 2) g = ctx.createLinearGradient(0, 0, 0, h * rand(0.35, 0.6));
    else g = ctx.createLinearGradient(0, h, 0, h * rand(0.4, 0.65));
    const a = Math.min(0.75, amount * 0.55);
    g.addColorStop(0, hexToRgba(c, a));
    g.addColorStop(0.5, hexToRgba(c, a * 0.35));
    g.addColorStop(1, 'rgba(0,0,0,0)');
    ctx.fillStyle = g;
    ctx.fillRect(0, 0, w, h);
    ctx.restore();
  }

  function vignette(ctx, w, h, amount) {
    const g = ctx.createRadialGradient(
      w / 2, h / 2, Math.min(w, h) * 0.28,
      w / 2, h / 2, Math.max(w, h) * 0.72
    );
    g.addColorStop(0, 'rgba(0,0,0,0)');
    g.addColorStop(1, 'rgba(0,0,0,' + Math.min(0.9, 0.12 + amount * 0.62).toFixed(3) + ')');
    ctx.save();
    ctx.fillStyle = g;
    ctx.fillRect(0, 0, w, h);
    ctx.restore();
  }

  function dust(ctx, w, h, amount) {
    if (amount <= 0) return;
    ctx.save();
    const count = Math.floor(amount * 150);
    for (let i = 0; i < count; i++) {
      const x = Math.random() * w, y = Math.random() * h;
      const r = rand(0.3, 1.8);
      ctx.globalAlpha = rand(0.12, 0.5);
      ctx.fillStyle = Math.random() < 0.6 ? '#ffffff' : '#101010';
      ctx.beginPath();
      ctx.arc(x, y, r, 0, Math.PI * 2);
      ctx.fill();
    }
    const scratches = Math.floor(amount * 5);
    for (let i = 0; i < scratches; i++) {
      ctx.globalAlpha = rand(0.06, 0.2);
      ctx.strokeStyle = Math.random() < 0.5 ? '#ffffff' : '#181818';
      ctx.lineWidth = rand(0.5, 1.4);
      const x = Math.random() * w;
      ctx.beginPath();
      ctx.moveTo(x, rand(0, h * 0.35));
      ctx.lineTo(x + rand(-10, 10), h - rand(0, h * 0.35));
      ctx.stroke();
    }
    ctx.restore();
  }

  function frame(ctx, w, h, frame) {
    if (!frame) return;
    ctx.save();
    const min = Math.min(w, h);
    if (frame.style === 'instant') {
      const b = Math.round(w * 0.055);
      const bottom = Math.round(b * 3.2);
      ctx.fillStyle = frame.color;
      ctx.fillRect(0, 0, w, b);
      ctx.fillRect(0, h - bottom, w, bottom);
      ctx.fillRect(0, 0, b, h);
      ctx.fillRect(w - b, 0, b, h);
    } else if (frame.style === 'thick') {
      const b = Math.round(min * 0.05);
      ctx.strokeStyle = frame.color;
      ctx.lineWidth = b;
      ctx.strokeRect(b / 2, b / 2, w - b, h - b);
    } else if (frame.style === 'cinema') {
      const b = Math.round(h * 0.06);
      ctx.fillStyle = frame.color;
      ctx.fillRect(0, 0, w, b);
      ctx.fillRect(0, h - b, w, b);
    } else {
      const b = Math.max(6, Math.round(min * 0.025));
      ctx.strokeStyle = frame.color;
      ctx.lineWidth = b;
      ctx.strokeRect(b / 2, b / 2, w - b, h - b);
    }
    ctx.restore();
  }

  function dateStamp(ctx, w, h) {
    const d = new Date();
    const txt = "'" + String(d.getFullYear()).slice(2) + ' ' +
      String(d.getMonth() + 1).padStart(2, '0') + ' ' +
      String(d.getDate()).padStart(2, '0');
    const size = Math.max(14, Math.round(w * 0.045));
    ctx.save();
    ctx.font = 'bold ' + size + 'px "Courier New", monospace';
    ctx.textAlign = 'right';
    ctx.textBaseline = 'bottom';
    ctx.globalAlpha = 0.92;
    ctx.fillStyle = '#ff7a18';
    ctx.shadowColor = 'rgba(0,0,0,0.55)';
    ctx.shadowBlur = size * 0.35;
    ctx.fillText(txt, w - Math.round(w * 0.07), h - Math.round(h * 0.06));
    ctx.restore();
  }

  /* Run the full randomised pipeline over a captured canvas. */
  function process(canvas, preset, settings) {
    settings = settings || {};
    const ctx = canvas.getContext('2d');
    const w = canvas.width, h = canvas.height;
    const g = Math.min(1, preset.grain * rand(0.6, 1.4));
    const l = preset.leak * rand(0.4, 1.6);
    const v = Math.min(1, preset.vignette * rand(0.7, 1.3));
    const d = Math.min(1, preset.dust * rand(0.5, 1.5));

    grain(ctx, w, h, g);
    lightLeak(ctx, w, h, l, preset.leakColors);
    vignette(ctx, w, h, v);
    dust(ctx, w, h, d);
    if (settings.border !== false) frame(ctx, w, h, preset.frame);
    if (settings.dateStamp && preset.dateStamp) dateStamp(ctx, w, h);
    return canvas;
  }

  /* Merge two frames for double exposure. */
  function blend(a, b) {
    const ctx = a.getContext('2d');
    ctx.save();
    ctx.globalCompositeOperation = 'screen';
    ctx.globalAlpha = 0.85;
    ctx.drawImage(b, 0, 0, a.width, a.height);
    ctx.restore();
    return a;
  }

  function hexToRgba(hex, a) {
    const h = hex.replace('#', '');
    const n = parseInt(h.length === 3 ? h.split('').map(c => c + c).join('') : h, 16);
    return 'rgba(' + ((n >> 16) & 255) + ',' + ((n >> 8) & 255) + ',' + (n & 255) + ',' + a + ')';
  }

  return { capture, process, blend, rand, pick };
})();

if (typeof module !== 'undefined') { module.exports = { Effects }; }
