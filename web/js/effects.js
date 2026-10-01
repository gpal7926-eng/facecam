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
  function dateStamp(ctx, w, h, opts) {
    opts = opts || {};
    const style = opts.stampStyle || 'retro';
    if (style === 'off') return;
    const d = new Date();
    const pad = (n) => String(n).padStart(2, '0');
    const ymd = d.getFullYear() + '/' + pad(d.getMonth() + 1) + '/' + pad(d.getDate());
    const dmy = pad(d.getDate()) + '/' + pad(d.getMonth() + 1) + '/' + d.getFullYear();
    const time = pad(d.getHours()) + ':' + pad(d.getMinutes());
    const dateOnly = (opts.dateFormat === 'dmy') ? dmy : ymd;

    let txt, color;
    if (style === 'custom') { txt = (opts.customText || 'FaceCam'); color = '#ffb347'; }
    else if (style === 'date') { txt = dateOnly; color = '#ffb347'; }
    else if (style === 'datetime') { txt = dateOnly + '  ' + time; color = '#ffb347'; }
    else { txt = "'" + String(d.getFullYear()).slice(2) + ' ' + pad(d.getMonth() + 1) + ' ' + pad(d.getDate()); color = '#ff7a18'; }

    const size = Math.max(14, Math.round(w * 0.045));
    ctx.save();
    ctx.font = 'bold ' + size + 'px "Courier New", monospace';
    ctx.textAlign = 'right';
    ctx.textBaseline = 'bottom';
    ctx.globalAlpha = 0.92;
    ctx.fillStyle = color;
    ctx.shadowColor = 'rgba(0,0,0,0.55)';
    ctx.shadowBlur = size * 0.35;
    ctx.fillText(txt, w - Math.round(w * 0.07), h - Math.round(h * 0.06));
    ctx.restore();
  }

  /* ---------- look intensity: scale a preset's strength by k (0.4..1.6) ---------- */
  function scaleTone(tone, k) {
    if (!tone || k === 1) return tone;
    return {
      lift: (tone.lift || 0) * k,
      gamma: 1 + ((tone.gamma == null ? 1 : tone.gamma) - 1) * k,
      gain: 1 + ((tone.gain == null ? 1 : tone.gain) - 1) * k,
      warmth: (tone.warmth || 0) * k,
      sat: 1 + ((tone.sat == null ? 1 : tone.sat) - 1) * k
    };
  }

  /* ---------- run the full randomised pipeline ---------- */
  function process(canvas, preset, settings) {
    settings = settings || {};
    const ctx = canvas.getContext('2d');
    const w = canvas.width, h = canvas.height;
    const k = settings.intensity == null ? 1 : settings.intensity;
    const gs = settings.grainScale == null ? 1 : settings.grainScale;

    applyTone(canvas, scaleTone(preset.tone, k));
    halation(ctx, w, h, (preset.halation || 0) * k * rand(0.7, 1.3));
    grain(ctx, w, h, Math.min(1, preset.grain * k * gs * rand(0.6, 1.4)));
    chromaNoise(ctx, w, h, Math.min(1, (preset.chroma || 0) * k * rand(0.6, 1.4)));
    lightLeak(ctx, w, h, preset.leak * k * rand(0.4, 1.6), preset.leakColors);
    vignette(ctx, w, h, Math.min(1, preset.vignette * k * rand(0.7, 1.3)));
    dust(ctx, w, h, Math.min(1, preset.dust * k * rand(0.5, 1.5)));
    if (settings.border !== false) frame(ctx, w, h, preset.frame);
    if (settings.dateStamp && preset.dateStamp) dateStamp(ctx, w, h, settings);
    return canvas;
  }

  /* ---------- post-capture editor ----------
   * A manual adjustment pipeline applied to an already-developed photo.
   * Params (all optional, neutral defaults):
   *   exposure  stops (-1..1)   brightness -0.3..0.3   contrast 0.5..1.8
   *   saturation 0..2           temperature -1..1      tint -1..1
   *   fade 0..1                 grain 0..1             vignette 0..1
   *   blur 0..8 px              sharpen 0..2           leak 0..1
   */
  const DEFAULT_LEAK = ['#ff5f6d', '#ffc371', '#c9a7ff'];

  function edit(src, p) {
    p = p || {};
    const w = src.width, h = src.height;
    const out = document.createElement('canvas');
    out.width = w; out.height = h;
    const ctx = out.getContext('2d', { willReadFrequently: true });
    ctx.drawImage(src, 0, 0);

    // 1. colour grade (per pixel)
    const exp = Math.pow(2, p.exposure || 0);
    const bright = p.brightness || 0;
    const con = p.contrast == null ? 1 : p.contrast;
    const sat = p.saturation == null ? 1 : p.saturation;
    const temp = p.temperature || 0;
    const tint = p.tint || 0;
    const fade = p.fade || 0;
    const img = ctx.getImageData(0, 0, w, h);
    const d = img.data;
    for (let i = 0; i < d.length; i += 4) {
      let r = d[i] / 255, g = d[i + 1] / 255, b = d[i + 2] / 255;
      r *= exp; g *= exp; b *= exp;
      r += bright; g += bright; b += bright;
      r += temp * 0.12 + tint * 0.06;
      g += -tint * 0.10;
      b += -temp * 0.12 + tint * 0.06;
      r = (r - 0.5) * con + 0.5; g = (g - 0.5) * con + 0.5; b = (b - 0.5) * con + 0.5;
      const l = 0.2126 * r + 0.7152 * g + 0.0722 * b;
      r = l + (r - l) * sat; g = l + (g - l) * sat; b = l + (b - l) * sat;
      if (fade > 0) { r = r * (1 - fade * 0.4) + fade * 0.20; g = g * (1 - fade * 0.4) + fade * 0.20; b = b * (1 - fade * 0.4) + fade * 0.20; }
      d[i] = clamp01(r) * 255; d[i + 1] = clamp01(g) * 255; d[i + 2] = clamp01(b) * 255;
    }
    ctx.putImageData(img, 0, 0);

    // 2. blur
    if ((p.blur || 0) > 0) {
      const tmp = document.createElement('canvas'); tmp.width = w; tmp.height = h;
      const tc = tmp.getContext('2d');
      tc.filter = 'blur(' + p.blur + 'px)';
      tc.drawImage(out, 0, 0);
      tc.filter = 'none';
      ctx.clearRect(0, 0, w, h);
      ctx.drawImage(tmp, 0, 0);
    }

    // 3. sharpen (unsharp mask)
    if ((p.sharpen || 0) > 0) {
      const soft = blurCopy(out, Math.max(1, Math.round(Math.min(w, h) * 0.004)));
      const im2 = ctx.getImageData(0, 0, w, h);
      const o = im2.data;
      const sd = soft.getContext('2d').getImageData(0, 0, w, h).data;
      for (let i = 0; i < o.length; i += 4) {
        for (let c = 0; c < 3; c++) {
          const v = o[i + c] + (o[i + c] - sd[i + c]) * p.sharpen * 1.4;
          o[i + c] = v < 0 ? 0 : v > 255 ? 255 : v;
        }
      }
      ctx.putImageData(im2, 0, 0);
    }

    // 4. grain, 5. vignette, 6. light leak
    if ((p.grain || 0) > 0) grain(ctx, w, h, Math.min(1, p.grain));
    if ((p.vignette || 0) > 0) vignette(ctx, w, h, Math.min(1, p.vignette));
    if ((p.leak || 0) > 0) lightLeak(ctx, w, h, p.leak, p.leakColors || DEFAULT_LEAK);

    return out;
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
  /* Simple skin-tone detector (0..1). Used so smoothing targets skin, not eyes. */
  function skinWeight(r, g, b) {
    const mx = Math.max(r, g, b);
    if (mx < 45) return 0;
    const rg = r - g, rb = r - b;
    if (rg < 6 || rb < 10) return 0;
    if (rb > 135) return 0;
    const lum = 0.2126 * r + 0.7152 * g + 0.0722 * b;
    let s = 1 - Math.abs(lum - 155) / 175;
    if (s < 0) s = 0;
    return Math.min(1, s * 1.35);
  }

  /* Clean, natural, phone-camera enhance. */
  function applyBeauty(canvas, b, intensity) {
    b = b || {};
    const k = intensity == null ? 1 : intensity;
    const ctx = canvas.getContext('2d', { willReadFrequently: true });
    const w = canvas.width, h = canvas.height;
    const min = Math.min(w, h);

    // 1. base grade: exposure, gentle contrast, natural saturation, warmth
    applyTone(canvas, {
      lift: (b.exposure || 0) * k,
      gamma: 1 + ((b.contrast == null ? 1 : b.contrast) - 1) * k,
      gain: 1,
      warmth: (b.warmth || 0) * k,
      sat: 1 + ((b.sat == null ? 1 : b.sat) - 1) * k
    });

    // 2. skin-aware smoothing. Blemishes are LOW-amplitude detail, so a high
    //    threshold smooths them away while eyes, brows and hair (high detail)
    //    survive. Weighting is strongest on skin tones.
    const smooth = (b.smooth || 0) * k;
    if (smooth > 0) {
      const blurred = blurCopy(canvas, Math.max(3, Math.round(min * 0.020)));
      const img = ctx.getImageData(0, 0, w, h);
      const od = img.data;
      const bd = blurred.getContext('2d').getImageData(0, 0, w, h).data;
      const thr = 165;
      for (let i = 0; i < od.length; i += 4) {
        const r = od[i], g = od[i + 1], bl = od[i + 2];
        const detail = (Math.abs(r - bd[i]) + Math.abs(g - bd[i + 1]) + Math.abs(bl - bd[i + 2])) / 3;
        let k = 1 - detail / thr;
        if (k < 0) k = 0;
        const lum = 0.2126 * r + 0.7152 * g + 0.0722 * bl;
        const darkGuard = lum > 85 ? 1 : lum / 85;   // protect eyes, brows, hair
        const sk = skinWeight(r, g, bl) * darkGuard;
        const wgt = smooth * k * (0.05 + 0.95 * sk);
        if (wgt > 0.002) {
          od[i] = r + (bd[i] - r) * wgt;
          od[i + 1] = g + (bd[i + 1] - g) * wgt;
          od[i + 2] = bl + (bd[i + 2] - bl) * wgt;
        }
      }
      ctx.putImageData(img, 0, 0);

      // second, wider soft-focus pass — evens out skin tone like a beauty camera
      const wide = blurCopy(canvas, Math.max(6, Math.round(min * 0.045)));
      const img2 = ctx.getImageData(0, 0, w, h);
      const o2 = img2.data;
      const wd = wide.getContext('2d').getImageData(0, 0, w, h).data;
      for (let i = 0; i < o2.length; i += 4) {
        const r = o2[i], g = o2[i + 1], bl = o2[i + 2];
        const detail2 = (Math.abs(r - wd[i]) + Math.abs(g - wd[i + 1]) + Math.abs(bl - wd[i + 2])) / 3;
        let k2 = 1 - detail2 / 70;                 // keep real features crisp
        if (k2 < 0) k2 = 0;
        const lum2 = 0.2126 * r + 0.7152 * g + 0.0722 * bl;
        const darkGuard2 = lum2 > 85 ? 1 : lum2 / 85;
        const sk = skinWeight(r, g, bl) * darkGuard2;
        const wgt = smooth * 0.46 * k2 * (0.02 + 0.98 * sk);
        if (wgt > 0.002) {
          o2[i] = r + (wd[i] - r) * wgt;
          o2[i + 1] = g + (wd[i + 1] - g) * wgt;
          o2[i + 2] = bl + (wd[i + 2] - bl) * wgt;
        }
      }
      ctx.putImageData(img2, 0, 0);
    }

    // 3. soft highlight glow
    const glow = (b.glow || 0) * k;
    if (glow > 0) {
      const t = document.createElement('canvas');
      t.width = w; t.height = h;
      const tc = t.getContext('2d');
      tc.filter = 'brightness(1.45) contrast(2.2) blur(' + Math.max(2, Math.round(min * 0.012)) + 'px)';
      tc.drawImage(canvas, 0, 0);
      tc.filter = 'none';
      ctx.save();
      ctx.globalCompositeOperation = 'screen';
      ctx.globalAlpha = Math.min(0.42, glow * 0.30);
      ctx.drawImage(t, 0, 0);
      ctx.restore();
    }

    // 4. clarity + unsharp, but ONLY on real edges. Without this gate the
    //    sharpening puts the blemishes straight back that step 2 just removed.
    const sharpen = (b.sharpen || 0) * k;
    if (sharpen > 0) {
      const soft = blurCopy(canvas, Math.max(2, Math.round(min * 0.012)));
      const fine = blurCopy(canvas, Math.max(1, Math.round(min * 0.0035)));
      const img = ctx.getImageData(0, 0, w, h);
      const od = img.data;
      const sd = soft.getContext('2d').getImageData(0, 0, w, h).data;
      const fd = fine.getContext('2d').getImageData(0, 0, w, h).data;
      for (let i = 0; i < od.length; i += 4) {
        for (let c = 0; c < 3; c++) {
          const o = od[i + c];
          const df = Math.abs(o - fd[i + c]);
          let gate = (df - 4) / 14;           // 0 on skin/flat, 1 on hard edges
          gate = gate < 0 ? 0 : gate > 1 ? 1 : gate;
          let v = o + (o - fd[i + c]) * sharpen * gate * 1.8;
          v += (o - sd[i + c]) * 0.34 * gate;
          od[i + c] = v < 0 ? 0 : v > 255 ? 255 : v;
        }
      }
      ctx.putImageData(img, 0, 0);
    }
    return canvas;
  }

  /* ---------- double exposure ---------- */
  function blend(a, b, opacity, mode) {
    const ctx = a.getContext('2d');
    ctx.save();
    ctx.globalCompositeOperation = mode || 'screen';
    ctx.globalAlpha = opacity == null ? 0.85 : opacity;
    ctx.drawImage(b, 0, 0, a.width, a.height);
    ctx.restore();
    return a;
  }

  function hexToRgba(hex, a) {
    const h = hex.replace('#', '');
    const n = parseInt(h.length === 3 ? h.split('').map(c => c + c).join('') : h, 16);
    return 'rgba(' + ((n >> 16) & 255) + ',' + ((n >> 8) & 255) + ',' + (n & 255) + ',' + a + ')';
  }

  return { capture, process, withBranding, applyBeauty, blend, edit, dateStamp, rand, pick, scaleTone };
})();

if (typeof module !== 'undefined') { module.exports = { Effects }; }
