/* FaceCam — video engine.
 *
 * Records video with the selected vintage/beauty look baked in:
 *   - draws each camera frame through the preset's colour curve (ctx.filter)
 *   - adds film grain, vignette and the frame for vintage looks
 *   - burns the caption / live subtitle onto the frame
 *   - captures the canvas + the microphone with MediaRecorder
 *
 * Also provides slow-motion baking (re-records the clip at 0.5x / 0.25x) and
 * live captions via the Web Speech API where the browser offers it.
 *
 * Everything is local. Nothing is uploaded.
 */
const Video = (() => {
  let opts = { getSource: () => null, getPreset: () => null, getSettings: () => ({}) };

  let canvas = null, ctx = null, noise = null;
  let recorder = null, chunks = [];
  let frameTimer = null, tickTimer = null;
  let micStream = null;
  let recording = false;
  let startedAt = 0;
  let caption = '';
  let liveText = '';
  let recognition = null;
  let liveOn = false;

  function init(o) { opts = Object.assign(opts, o || {}); }

  function supported() {
    return typeof MediaRecorder !== 'undefined' &&
           typeof HTMLCanvasElement !== 'undefined' &&
           !!HTMLCanvasElement.prototype.captureStream;
  }

  function pickMime() {
    const list = [
      'video/webm;codecs=vp9,opus',
      'video/webm;codecs=vp8,opus',
      'video/webm;codecs=vp9',
      'video/webm;codecs=vp8',
      'video/webm',
      'video/mp4'
    ];
    for (const m of list) {
      try { if (MediaRecorder.isTypeSupported(m)) return m; } catch (e) {}
    }
    return '';
  }

  function buildNoise() {
    const n = document.createElement('canvas');
    n.width = 200; n.height = 200;
    const c = n.getContext('2d');
    const img = c.createImageData(200, 200);
    for (let i = 0; i < img.data.length; i += 4) {
      const v = 128 + (Math.random() * 2 - 1) * 127;
      img.data[i] = img.data[i + 1] = img.data[i + 2] = v;
      img.data[i + 3] = 255;
    }
    c.putImageData(img, 0, 0);
    noise = n;
  }

  function drawFrame() {
    const src = opts.getSource();
    const preset = opts.getPreset();
    if (!src || !canvas || !ctx) return;
    const sw = src.videoWidth || src.width;
    const sh = src.videoHeight || src.height;
    if (!sw || !sh) return;
    if (canvas.width !== sw || canvas.height !== sh) { canvas.width = sw; canvas.height = sh; }

    const w = canvas.width, h = canvas.height;
    const vintage = (preset && preset.group || 'vintage') === 'vintage';

    ctx.filter = (preset && preset.filter) || 'none';
    ctx.drawImage(src, 0, 0, w, h);
    ctx.filter = 'none';

    if (vintage) {
      // grain (cheap: one tiled drawImage)
      if (!noise) buildNoise();
      ctx.save();
      ctx.globalAlpha = Math.min(0.5, ((preset.grain || 0) * 0.45));
      ctx.globalCompositeOperation = 'overlay';
      const pat = ctx.createPattern(noise, 'repeat');
      ctx.fillStyle = pat;
      ctx.fillRect(0, 0, w, h);
      ctx.restore();

      // vignette
      const g = ctx.createRadialGradient(w / 2, h / 2, Math.min(w, h) * 0.30, w / 2, h / 2, Math.max(w, h) * 0.72);
      g.addColorStop(0, 'rgba(0,0,0,0)');
      g.addColorStop(1, 'rgba(0,0,0,' + Math.min(0.85, 0.10 + (preset.vignette || 0) * 0.55).toFixed(3) + ')');
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

  async function start() {
    if (recording) return false;
    if (!supported()) return false;
    const src = opts.getSource();
    if (!src) return false;

    canvas = document.createElement('canvas');
    canvas.width = src.videoWidth || 720;
    canvas.height = src.videoHeight || 1280;
    ctx = canvas.getContext('2d', { willReadFrequently: false });

    drawFrame();
    const cstream = canvas.captureStream(30);

    // Try to capture the microphone too, but NEVER let it block the recording:
    // some environments hang on the audio permission, and video must still work.
    micStream = null;
    try {
      micStream = await Promise.race([
        navigator.mediaDevices.getUserMedia({ audio: true }),
        new Promise((_, rej) => setTimeout(() => rej(new Error('mic-timeout')), 2500))
      ]);
      micStream.getAudioTracks().forEach(t => cstream.addTrack(t));
    } catch (e) { micStream = null; }

    const mime = pickMime();
    try {
      recorder = new MediaRecorder(cstream, mime ? { mimeType: mime } : undefined);
    } catch (e) {
      recorder = new MediaRecorder(cstream);
    }
    chunks = [];
    recorder.ondataavailable = e => { if (e.data && e.data.size) chunks.push(e.data); };

    frameTimer = setInterval(drawFrame, 33);
    recorder.start(250);
    recording = true;
    startedAt = Date.now();
    return true;
  }

  function elapsed() { return recording ? Date.now() - startedAt : 0; }

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
    recording = false;
    if (frameTimer) { clearInterval(frameTimer); frameTimer = null; }
    if (micStream) { micStream.getTracks().forEach(t => t.stop()); micStream = null; }
    recorder = null;
    chunks = [];
  }

  function isRecording() { return recording; }

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
        const draw = () => {
          if (v.ended) return;
          g.drawImage(v, 0, 0, c.width, c.height);
        };
        v.onended = () => { if (t) clearInterval(t); setTimeout(() => { try { mr.stop(); } catch (e) {} }, 250); };
        mr.start(250);
        v.playbackRate = rate;
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
    init, supported, start, stop, isRecording, elapsed,
    bakeSlowMotion, setCaption, getCaption,
    liveSupported, setLiveCaptions
  };
})();
