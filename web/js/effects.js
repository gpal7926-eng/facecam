/* FaceCam — analog film effects engine.
 *
 * The realistic pipeline, in order:
 *   1. film tone curve      per-channel lift / gamma / gain + warmth + saturation
 *   2. halation             bright-pass blur screen-blended back with a warm tint
 *   3. luminance grain      randomised silver-halide noise
 *   4. chroma noise         blotchy colour noise (film is not clean)
 *   5. light leak          randomised coloured edge streak
 *   6. vignette            soft corner falloff
 *   7. dust & scratches    specks and hairs from a dirty negative
 *   8. frame               the camera's border
 *   9. date stamp          optional burned-in date
 *  10. branding band       "FaceCam" caption appended BELOW the photo
 *
 * Everything is pure canvas — no libraries, no network.
 */
const Effects = (() => {
  const rand = (a, b) => a + Math.random() * (b - a);
  const randInt = (a, b) => Math.floor(rand(a, b + 1));
  const pick = (arr) => arr[Math.floor(Math.random() * arr.length)];
  const clamp01 = (v) => (v < 0 ? 0 : v > 1 ? 1 : v);

  function drawCover(ctx, source, w, h) {
    const sw = source.videoWidth || source.naturalWidth || source.width;
    const sh = source.videoHeight || source.naturalHeight || source.height;
    if (!sw || !sh) return;
    const scale = Math.max(w / sw, h / sh);
    const dw = sw * scale, dh = sh * scale;
    ctx.drawImage(source, (w - dw) / 2, (h - dh) / 2, dw, dh);
  }

  /* Grab a frame from a video/image and apply the base colour curve. */
  function capture(source, w, h, filter) {
    const c = document.createElement('canvas');
    c.width = w; c.height = h;
    const ctx = c.getContext('2d');
    ctx.filter = filter || 'none';
    drawCover(ctx, source, w, h);
    ctx.filter = 'none';
    return c;
  }

  /* ---------- 1. film tone curve ---------- */
  function buildCurveLUT(tone) {
    tone = tone || {};
    const lift = tone.lift || 0;
    const gamma = tone.gamma || 1;
    const gain = tone.gain || 1;
    const warmth = tone.warmth || 0;
    const r = new Uint8ClampedArray(256);
    const g = new Uint8ClampedArray(256);
    const b = new Uint8ClampedArray(256);
    for (let i = 0; i < 256; i++) {
      let v = i / 255;
      v = clamp01(v + lift);              // raise / lower the black point
      v = Math.pow(v, 1 / gamma);         // mid-tone contrast
      v = clamp01(v * gain);              // overall exposure
      r[i] = clamp01(v + warmth * 0.55) * 255;
      g[i] = clamp01(v + warmth * 0.05) * 255;
      b[i] = clamp01(v - warmth * 0.55) * 255;
    }
    return { r, g, b };
  }

  function applyTone(canvas, tone) {
    if (!tone) return;
    const ctx = canvas.getContext('2d');
    const img = ctx.getImageData(0, 0, canvas.width, canvas.height);
    const d = img.data;
    const lut = buildCurveLUT(tone);
    const sat = tone.sat == null ? 1 : tone.sat;
    for (let i = 0; i < d.length; i += 4) {
      let r = lut.r[d[i]], g = lut.g[d[i + 1]], b = lut.b[d[i + 2]];
      if (sat !== 1) {
        const luma = 0.2126 * r + 0.7152 * g + 0.0722 * b;
        r = luma + (r - luma) * sat;
        g = luma + (g - luma) * sat;
        b = luma + (b - luma) * sat;
      }
      d[i] = r < 0 ? 0 : r > 255 ? 255 : r;
      d[i + 1] = g < 0 ? 0 : g > 255 ? 255 : g;
      d[i + 2] = b < 0 ? 0 : b > 255 ? 255 : b;
    }
    ctx.putImageData(img, 0, 0);
  }

  /* ---------- 2. halation / bloom ---------- */
  function halation(ctx, w, h, amount) {
    if (amount <= 0) return;
    const tmp = document.createElement('canvas');
    tmp.width = w; tmp.height = h;
    const t = tmp.getContext('2d');
    const blur = Math.max(2, Math.round(Math.min(w, h) * 0.014));
    t.filter = 'brightness(1.55) contrast(2.6) blur(' + blur + 'px)';
    t.drawImage(ctx.canvas, 0, 0);
    t.filter = 'none';
    // warm the glow
    t.globalCompositeOperation = 'multiply';
    t.fillStyle = 'rgb(255,196,146)';
    t.fillRect(0, 0, w, h);
    t.globalCompositeOperation = 'source-over';

    ctx.save();
    ctx.globalCompositeOperation = 'screen';
    ctx.globalAlpha = Math.min(0.55, amount * 0.45);
    ctx.drawImage(tmp, 0, 0);
    ctx.restore();
  }

  /* ---------- 3. luminance grain ---------- */
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
    ctx.globalAlpha = Math.min(0.8, amount * 0.5);
    ctx.globalCompositeOperation = 'overlay';
    ctx.fillStyle = ctx.createPattern(n, 'repeat');
    ctx.fillRect(0, 0, w, h);
    ctx.restore();
  }

  /* ---------- 4. chroma noise ---------- */
  function chromaNoise(ctx, w, h, amount) {
    if (amount <= 0) return;
    const size = 96;
    const n = document.createElement('canvas');
    n.width = size; n.height = size;
    const nc = n.getContext('2d');
    const img = nc.createImageData(size, size);
    for (let i = 0; i < img.data.length; i += 4) {
      img.data[i] = Math.random() * 255;
      img.data[i + 1] = Math.random() * 255;
      img.data[i + 2] = Math.random() * 255;
      img.data[i + 3] = Math.round(18 + Math.random() * 55 * amount);
    }
    nc.putImageData(img, 0, 0);
    ctx.save();
    ctx.globalCompositeOperation = 'overlay';
    ctx.globalAlpha = Math.min(0.45, amount * 0.3);
    ctx.fillStyle = ctx.createPattern(n, 'repeat');
    ctx.fillRect(0, 0, w, h);
    ctx.restore();
  }

  /* ---------- 5. light leak ---------- */
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
    const a = Math.min(0.7, amount * 0.5);
    g.addColorStop(0, hexToRgba(c, a));
    g.addColorStop(0.5, hexToRgba(c, a * 0.35));
    g.addColorStop(1, 'rgba(0,0,0,0)');
    ctx.fillStyle = g;
    ctx.fillRect(0, 0, w, h);
    ctx.restore();
  }

  /* ---------- 6. vignette ---------- */
  function vignette(ctx, w, h, amount) {
    const g = ctx.createRadialGradient(
      w / 2, h / 2, Math.min(w, h) * 0.30,
      w / 2, h / 2, Math.max(w, h) * 0.72
    );
    g.addColorStop(0, 'rgba(0,0,0,0)');
    g.addColorStop(0.65, 'rgba(0,0,0,' + Math.min(0.5, amount * 0.25).toFixed(3) + ')');
    g.addColorStop(1, 'rgba(0,0,0,' + Math.min(0.9, 0.14 + amount * 0.6).toFixed(3) + ')');
    ctx.save();
    ctx.fillStyle = g;
    ctx.fillRect(0, 0, w, h);
    ctx.restore();
  }

  /* ---------- 7. dust & scratches ---------- */
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

  /* ---------- 8. frame ---------- */
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

  /* ---------- 9. date stamp ---------- */
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

  /* ---------- run the full randomised pipeline ---------- */
  function process(canvas, preset, settings) {
    settings = settings || {};
    const ctx = canvas.getContext('2d');
    const w = canvas.width, h = canvas.height;

    applyTone(canvas, preset.tone);
    halation(ctx, w, h, (preset.halation || 0) * rand(0.7, 1.3));
    grain(ctx, w, h, Math.min(1, preset.grain * rand(0.6, 1.4)));
    chromaNoise(ctx, w, h, Math.min(1, (preset.chroma || 0) * rand(0.6, 1.4)));
    lightLeak(ctx, w, h, preset.leak * rand(0.4, 1.6), preset.leakColors);
    vignette(ctx, w, h, Math.min(1, preset.vignette * rand(0.7, 1.3)));
    dust(ctx, w, h, Math.min(1, preset.dust * rand(0.5, 1.5)));
    if (settings.border !== false) frame(ctx, w, h, preset.frame);
    if (settings.dateStamp && preset.dateStamp) dateStamp(ctx, w, h);
    return canvas;
  }

  /* ---------- 10. FaceCam branding band, appended below the photo ---------- */
  function drawSpaced(ctx, text, x, y, extra) {
    let cx = x;
    for (const ch of text) {
      ctx.fillText(ch, cx, y);
      cx += ctx.measureText(ch).width + extra;
    }
  }

  function isLight(hex) {
    const h = String(hex || '#ffffff').replace('#', '');
    const n = parseInt(h.length === 3 ? h.split('').map(c => c + c).join('') : h, 16);
    const r = ((n >> 16) & 255) / 255, g = ((n >> 8) & 255) / 255, b = (n & 255) / 255;
    return (0.2126 * r + 0.7152 * g + 0.0722 * b) > 0.55;
  }

  function withBranding(src, preset, settings) {
    settings = settings || {};
    if (settings.branding === false) return src;

    const band = Math.max(30, Math.round(src.height * 0.075));
    const out = document.createElement('canvas');
    out.width = src.width;
    out.height = src.height + band;
    const ctx = out.getContext('2d');
    ctx.drawImage(src, 0, 0);

    const bg = (preset && preset.frame && preset.frame.color) || '#f6efe1';
    ctx.fillStyle = bg;
    ctx.fillRect(0, src.height, src.width, band);

    ctx.fillStyle = 'rgba(0,0,0,0.16)';
    ctx.fillRect(0, src.height, src.width, Math.max(1, Math.round(band * 0.035)));

    const light = isLight(bg);
    const pad = Math.round(src.width * 0.05);
    const midY = src.height + band / 2;

    const fs = Math.round(band * 0.44);
    ctx.font = '700 ' + fs + 'px "Helvetica Neue", Helvetica, Arial, sans-serif';
    ctx.textBaseline = 'middle';
    ctx.textAlign = 'left';
    ctx.fillStyle = light ? '#141414' : '#f2efe9';
    drawSpaced(ctx, 'FaceCam', pad, midY, Math.max(1, fs * 0.055));

    const ts = Math.round(band * 0.30);
    ctx.font = '500 ' + ts + 'px "Helvetica Neue", Helvetica, Arial, sans-serif';
    ctx.textAlign = 'right';
    ctx.fillStyle = light ? 'rgba(20,20,20,0.55)' : 'rgba(242,239,233,0.6)';
    ctx.fillText(String((preset && (preset.tag || preset.name)) || '').toUpperCase(),
                 src.width - pad, midY);
    ctx.textAlign = 'left';
    return out;
  }

  /* ---------- beauty (iPhone-like enhance) ---------- */
  function blurCopy(src, px) {
    const c = document.createElement('canvas');
    c.width = src.width; c.height = src.height;
    const x = c.getContext('2d', { willReadFrequently: true });
    x.filter = 'blur(' + px + 'px)';
    x.drawImage(src, 0, 0);
    x.filter = 'none';
    return c;
  }

  /* Clean, natural, phone-camera enhance: exposure + gentle S-curve + natural
     saturation, edge-aware skin smoothing, soft highlight glow, and unsharp
     sharpening. No grain, no leaks, no vignette, no frame. */
  function applyBeauty(canvas, b) {
    b = b || {};
    const ctx = canvas.getContext('2d', { willReadFrequently: true });
    const w = canvas.width, h = canvas.height;
    const min = Math.min(w, h);

    applyTone(canvas, {
      lift: b.exposure || 0,
      gamma: b.contrast || 1,
      gain: 1,
      warmth: b.warmth || 0,
      sat: b.sat == null ? 1 : b.sat
    });

    const smooth = b.smooth || 0;
    if (smooth > 0) {
      const blurred = blurCopy(canvas, Math.max(2, Math.round(min * 0.012)));
      const img = ctx.getImageData(0, 0, w, h);
      const od = img.data;
      const bd = blurred.getContext('2d').getImageData(0, 0, w, h).data;
      const thr = 24;
      for (let i = 0; i < od.length; i += 4) {
        const dr = Math.abs(od[i] - bd[i]);
        const dg = Math.abs(od[i + 1] - bd[i + 1]);
        const db = Math.abs(od[i + 2] - bd[i + 2]);
        const detail = (dr + dg + db) / 3;
        let k = 1 - detail / thr;
        if (k < 0) k = 0;
        k *= smooth;
        if (k > 0) {
          od[i] += (bd[i] - od[i]) * k;
          od[i + 1] += (bd[i + 1] - od[i + 1]) * k;
          od[i + 2] += (bd[i + 2] - od[i + 2]) * k;
        }
      }
      ctx.putImageData(img, 0, 0);
    }

    const glow = b.glow || 0;
    if (glow > 0) {
      const t = document.createElement('canvas');
      t.width = w; t.height = h;
      const tc = t.getContext('2d');
      tc.filter = 'brightness(1.4) contrast(2.0) blur(' + Math.max(2, Math.round(min * 0.010)) + 'px)';
      tc.drawImage(canvas, 0, 0);
      tc.filter = 'none';
      ctx.save();
      ctx.globalCompositeOperation = 'screen';
      ctx.globalAlpha = Math.min(0.4, glow * 0.32);
      ctx.drawImage(t, 0, 0);
      ctx.restore();
    }

    const sharpen = b.sharpen || 0;
    if (sharpen > 0) {
      const blurred = blurCopy(canvas, Math.max(1, Math.round(min * 0.0035)));
      const img = ctx.getImageData(0, 0, w, h);
      const od = img.data;
      const bd = blurred.getContext('2d').getImageData(0, 0, w, h).data;
      for (let i = 0; i < od.length; i += 4) {
        const r = od[i] + (od[i] - bd[i]) * sharpen;
        const g = od[i + 1] + (od[i + 1] - bd[i + 1]) * sharpen;
        const bl = od[i + 2] + (od[i + 2] - bd[i + 2]) * sharpen;
        od[i] = r < 0 ? 0 : r > 255 ? 255 : r;
        od[i + 1] = g < 0 ? 0 : g > 255 ? 255 : g;
        od[i + 2] = bl < 0 ? 0 : bl > 255 ? 255 : bl;
      }
      ctx.putImageData(img, 0, 0);
    }
    return canvas;
  }

  /* ---------- double exposure ---------- */
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

  return { capture, process, withBranding, applyBeauty, blend, rand, pick };
})();

if (typeof module !== 'undefined') { module.exports = { Effects }; }
