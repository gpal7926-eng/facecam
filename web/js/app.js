/* FaceCam — web preview app (HTML / CSS / JavaScript).
 *
 * Free, offline, no ads. Two camera families (vintage film / beauty) and two
 * capture modes (photo / video). Video records with the selected look baked in,
 * supports slow motion, and burns in subtitles.
 *
 *   localStorage  -> settings, seen flag
 *   IndexedDB     -> photos and video blobs (with an in-memory write-through copy)
 *   getUserMedia  -> viewfinder  (falls back to a built-in demo scene)
 *   <canvas>      -> film + beauty pipelines, and the video frame pipeline
 */
(function () {
  'use strict';

  /* ------------------------------------------------------------------ *
   * Storage
   * ------------------------------------------------------------------ */
  const LS = {
    get(k, f) { try { const v = localStorage.getItem('facecam.' + k); return v === null ? f : JSON.parse(v); } catch (e) { return f; } },
    set(k, v) { try { localStorage.setItem('facecam.' + k, JSON.stringify(v)); } catch (e) {} }
  };

  const store = {
    settings() {
      return LS.get('settings', {
        dateStamp: true, border: true, sound: true, branding: true, defaultCamera: 'nomo_135_b'
      });
    },
    saveSettings(s) { LS.set('settings', s); },
    seen() { return LS.get('seen', false); },
    markSeen() { LS.set('seen', true); },
    reset() { ['settings', 'saves', 'seen'].forEach(k => localStorage.removeItem('facecam.' + k)); }
  };

  /* Write-through media store: memory first (instant), IndexedDB for persistence. */
  const Media = {
    db: null, mem: [], nextId: 1,
    open() {
      return new Promise((resolve) => {
        if (!window.indexedDB) { resolve(null); return; }
        let settled = false;
        const giveUp = setTimeout(() => { if (!settled) { settled = true; resolve(null); } }, 4000);
        let req;
        try { req = indexedDB.open('facecam', 1); }
        catch (e) { clearTimeout(giveUp); resolve(null); return; }
        req.onupgradeneeded = () => {
          const db = req.result;
          if (!db.objectStoreNames.contains('photos')) db.createObjectStore('photos', { keyPath: 'id', autoIncrement: true });
        };
        req.onsuccess = () => { this.db = req.result; clearTimeout(giveUp); if (!settled) { settled = true; resolve(this.db); } };
        req.onerror = () => { clearTimeout(giveUp); if (!settled) { settled = true; resolve(null); } };
      });
    },
    add(item) {
      const rec = Object.assign({}, item, { id: this.nextId++ });
      this.mem.push(rec);
      if (this.db) { try { this.db.transaction('photos', 'readwrite').objectStore('photos').add(item); } catch (e) {} }
      return Promise.resolve(rec.id);
    },
    all() {
      return new Promise((resolve) => {
        const merge = (extra) => {
          const byKey = {};
          this.mem.forEach(p => { byKey[String(p.ts)] = p; });
          (extra || []).forEach(p => { byKey[String(p.ts)] = p; });
          resolve(Object.keys(byKey).map(k => byKey[k]).sort((a, b) => b.ts - a.ts));
        };
        if (!this.db) { merge([]); return; }
        let settled = false;
        const giveUp = setTimeout(() => { if (!settled) { settled = true; merge([]); } }, 1500);
        try {
          const r = this.db.transaction('photos', 'readonly').objectStore('photos').getAll();
          r.onsuccess = () => { if (!settled) { settled = true; clearTimeout(giveUp); merge(r.result || []); } };
          r.onerror = () => { if (!settled) { settled = true; clearTimeout(giveUp); merge([]); } };
        } catch (e) { clearTimeout(giveUp); merge([]); }
      });
    },
    del(id) {
      this.mem = this.mem.filter(p => p.id !== id);
      if (this.db) { try { this.db.transaction('photos', 'readwrite').objectStore('photos').delete(id); } catch (e) {} }
      return Promise.resolve();
    }
  };

  /* ------------------------------------------------------------------ *
   * State
   * ------------------------------------------------------------------ */
  const state = {
    cameraId: 'nomo_135_b',
    family: 'vintage',
    mode: 'photo',
    speed: 1,
    intensity: 1,
    facing: 'environment',
    flash: false,
    torchAvailable: false,
    timer: 0,
    stream: null,
    demo: false,
    demoSource: null,
    last: null,            // { kind:'photo'|'video', ... }
    lastOriginal: null,
    showingOriginal: false,
    double: false,
    firstShot: null,
    pendingTimer: null,
    recTimer: null,
    busy: false,
    camGen: 0,
    viewerItem: null
  };

  const $ = (s) => document.querySelector(s);
  const $$ = (s) => Array.prototype.slice.call(document.querySelectorAll(s));
  const cameraById = (id) => CAMERAS.filter(c => c.id === id)[0] || CAMERAS[0];
  const groupOf = (c) => (c && c.group) || 'vintage';

  function withTimeout(p, ms) {
    return new Promise((res, rej) => {
      const t = setTimeout(() => rej(new Error('camera-timeout')), ms);
      p.then(v => { clearTimeout(t); res(v); }, e => { clearTimeout(t); rej(e); });
    });
  }

  function toast(msg) {
    const t = $('#toast');
    t.textContent = msg;
    t.classList.add('show');
    clearTimeout(t._h);
    t._h = setTimeout(() => t.classList.remove('show'), 2000);
  }

  function show(id) {
    $$('.screen').forEach(s => s.classList.toggle('active', s.id === id));
    document.body.dataset.screen = id;
    if (id !== 'screen-viewfinder' && state.pendingTimer) {
      clearInterval(state.pendingTimer); state.pendingTimer = null;
      $('#countdown').classList.remove('show');
    }
  }

  function fmtTime(ms) {
    const s = Math.floor(ms / 1000);
    return Math.floor(s / 60) + ':' + String(s % 60).padStart(2, '0');
  }

  /* ------------------------------------------------------------------ *
   * Demo scene
   * ------------------------------------------------------------------ */
  function drawSampleScene(canvas) {
    canvas.width = 540; canvas.height = 960;
    const g = canvas.getContext('2d');
    const grad = g.createLinearGradient(0, 0, 0, 960);
    grad.addColorStop(0, '#1d3d72'); grad.addColorStop(0.42, '#e2814a');
    grad.addColorStop(0.62, '#f7c46d'); grad.addColorStop(1, '#6b4a3a');
    g.fillStyle = grad; g.fillRect(0, 0, 540, 960);
    g.beginPath(); g.arc(355, 330, 150, 0, Math.PI * 2); g.fillStyle = 'rgba(255,220,140,0.20)'; g.fill();
    g.beginPath(); g.arc(355, 330, 92, 0, Math.PI * 2); g.fillStyle = '#fff3cd'; g.fill();
    g.fillStyle = '#4b3a55';
    g.beginPath(); g.moveTo(-40, 700); g.lineTo(190, 470); g.lineTo(430, 700); g.closePath(); g.fill();
    g.fillStyle = '#38293f';
    g.beginPath(); g.moveTo(250, 720); g.lineTo(470, 520); g.lineTo(640, 720); g.closePath(); g.fill();
    g.fillStyle = '#241c2c'; g.fillRect(0, 690, 540, 270);
    for (let i = 0; i < 5; i++) {
      const x = 60 + i * 105, y = 690;
      g.fillStyle = '#2f2438'; g.fillRect(x - 4, y - 40, 8, 45);
      g.beginPath(); g.arc(x, y - 52, 22, 0, Math.PI * 2); g.fillStyle = '#3d6b4f'; g.fill();
    }
    g.fillStyle = 'rgba(255,255,255,0.85)';
    g.font = '600 26px -apple-system, Segoe UI, sans-serif'; g.fillText('13:42', 40, 70);
    g.fillStyle = 'rgba(255,255,255,0.75)';
    g.font = '600 17px -apple-system, Segoe UI, sans-serif'; g.fillText('FaceCam demo scene', 40, 930);
  }

  /* ------------------------------------------------------------------ *
   * Camera
   * ------------------------------------------------------------------ */
  async function startCamera() {
    const gen = ++state.camGen;
    stopCamera();
    state.demo = false;
    $('#demo-canvas').style.display = 'none';

    if (!navigator.mediaDevices || !navigator.mediaDevices.getUserMedia) {
      showFallback('Is browser mein camera support nahi hai.');
      return;
    }
    if (location.protocol === 'file:' || window.isSecureContext === false) {
      showFallback(
        'Camera isliye nahi chala kyunki aap ye file seedha kholiye hain (file://).',
        true,
        'Browsers file:// par camera block kar dete hain \u2014 ye koi app bypass nahi kar sakta. ' +
        'Live version https par kholein, ya folder ko localhost se serve karein.'
      );
      return;
    }
    try {
      const stream = await withTimeout(
        navigator.mediaDevices.getUserMedia({
          video: { facingMode: state.facing, width: { ideal: 1440 }, height: { ideal: 1920 } },
          audio: false
        }), 7000);
      if (!stream || !stream.getVideoTracks().length) throw new Error('no-video-track');
      if (gen !== state.camGen) { stream.getTracks().forEach(t => t.stop()); return; }
      state.stream = stream;
      const v = $('#video');
      v.srcObject = stream;
      await v.play().catch(() => {});
      $('#cam-fallback').classList.remove('show');
      v.style.display = 'block';
      applyTorch();
      if (typeof Pro !== 'undefined' && Pro.refresh) Pro.refresh();
    } catch (e) {
      if (gen !== state.camGen || state.demo) return;
      let why = 'Camera is device par available nahi hai.';
      if (e && e.name === 'NotAllowedError') why = 'Camera permission deny ho gayi.';
      else if (e && e.name === 'NotFoundError') why = 'Koi camera nahi mila.';
      else if (e && e.message === 'camera-timeout') why = 'Camera start nahi ho paya (timeout).';
      showFallback(why);
    }
  }

  function showFallback(reason, showLive, fine) {
    $('#fallback-text').textContent = reason;
    if (fine) $('#fallback-fine').textContent = fine;
    $('#cam-fallback').classList.add('show');
    $('#video').style.display = 'none';
    const live = $('#fallback-live');
    if (live) live.style.display = showLive ? 'block' : 'none';
  }

  function stopCamera() {
    if (state.stream) { state.stream.getTracks().forEach(t => t.stop()); state.stream = null; }
    state.torchAvailable = false;
  }

  async function applyTorch() {
    if (!state.stream) return;
    const track = state.stream.getVideoTracks()[0];
    if (!track || !track.getCapabilities) return;
    const caps = track.getCapabilities();
    if (caps && caps.torch) {
      try { await track.applyConstraints({ advanced: [{ torch: state.flash }] }); state.torchAvailable = true; return; }
      catch (e) {}
    }
    state.torchAvailable = false;
  }

  function startDemo() {
    state.camGen++;
    const cv = $('#demo-canvas');
    if (!cv.dataset.drawn) { drawSampleScene(cv); cv.dataset.drawn = '1'; }
    cv.style.display = 'block';
    $('#video').style.display = 'none';
    $('#cam-fallback').classList.remove('show');
    state.demo = true;
    state.demoSource = cv;
    applyLiveLook();
    toast('Demo scene ready');
  }

  function applyLiveLook() {
    const cam = cameraById(state.cameraId);
    $('#video').style.filter = cam.filter;
    $('#demo-canvas').style.filter = cam.filter;
    const vintage = groupOf(cam) === 'vintage';
    $('#live-vignette').style.opacity = vintage ? Math.min(0.85, (cam.vignette || 0) * 0.8).toFixed(2) : '0';
    const f = $('#live-frame');
    f.className = 'live-frame ' + (vintage && cam.frame ? cam.frame.style : '');
    f.style.setProperty('--frame-color', (vintage && cam.frame) ? cam.frame.color : 'transparent');
    $('#cam-name').textContent = cam.name;
    $('#cam-tag').textContent = cam.tag;
  }

  function hasSource() { return !!state.stream || state.demo; }

  /* ------------------------------------------------------------------ *
   * Photo capture
   * ------------------------------------------------------------------ */
  function grabCanvas(extraFilter) {
    const cam = cameraById(state.cameraId);
    const source = state.demo ? state.demoSource : $('#video');
    let w = source.videoWidth || source.width || 1080;
    let h = source.videoHeight || source.height || 1440;
    const maxDim = 1300;
    if (Math.max(w, h) > maxDim) { const s = maxDim / Math.max(w, h); w = Math.round(w * s); h = Math.round(h * s); }
    return Effects.capture(source, w, h, cam.filter + (extraFilter || ''));
  }

  function developShot(canvas, cam, settings) {
    const k = state.intensity == null ? 1 : state.intensity;
    if (groupOf(cam) === 'beauty') { Effects.applyBeauty(canvas, cam.beauty, k); return canvas; }
    Effects.process(canvas, cam, Object.assign({}, settings, { intensity: k }));
    return Effects.withBranding(canvas, cam, settings);
  }

  function screenFlash() {
    const f = $('#flash-overlay');
    f.classList.add('go');
    setTimeout(() => f.classList.remove('go'), 220);
  }

  function shutterPressed() {
    if (!hasSource()) { toast('Pehle camera on karein ya demo scene chalayein'); return; }
    if (state.mode === 'video') { toggleRecording(); return; }
    if (state.busy) return;
    if (state.timer > 0) {
      let n = state.timer;
      const o = $('#countdown');
      o.classList.add('show'); o.textContent = n;
      state.pendingTimer = setInterval(() => {
        n -= 1;
        if (n <= 0) { clearInterval(state.pendingTimer); state.pendingTimer = null; o.classList.remove('show'); shoot(); }
        else o.textContent = n;
      }, 1000);
    } else shoot();
  }

  function shoot() {
    if (state.busy || !hasSource()) return;
    state.busy = true;
    setTimeout(() => { state.busy = false; }, 350);
    const cam = cameraById(state.cameraId);
    const useFlash = state.flash && !state.torchAvailable;
    if (state.flash) screenFlash();
    const raw = grabCanvas(useFlash ? ' brightness(1.35) contrast(1.05)' : '');
    state.lastOriginal = raw.toDataURL('image/jpeg', 0.9);

    if (state.double) {
      if (!state.firstShot) {
        state.firstShot = raw;
        const chip = $('#double-chip');
        chip.querySelector('img').src = state.lastOriginal;
        chip.classList.add('show');
        toast('Double exposure: pehla shot ho gaya');
        return;
      }
      const merged = Effects.blend(state.firstShot, raw);
      state.firstShot = null;
      $('#double-chip').classList.remove('show');
      state.double = false;
      $('#btn-double').classList.remove('on');
      afterCapture(cam, merged);
      return;
    }
    afterCapture(cam, raw);
  }

  function afterCapture(cam, canvas) {
    if (cam.instant) {
      const d = canvas.cloneNode(false);
      d.width = canvas.width; d.height = canvas.height;
      d.getContext('2d').drawImage(canvas, 0, 0);
      const out = developShot(d, cam, store.settings());
      state.last = { kind: 'photo', dataUrl: out.toDataURL('image/jpeg', 0.92), camId: cam.id, camName: cam.name };
      runDeveloping();
    } else {
      const out = developShot(canvas, cam, store.settings());
      state.last = { kind: 'photo', dataUrl: out.toDataURL('image/jpeg', 0.92), camId: cam.id, camName: cam.name };
      if (store.settings().sound) beep();
      openResult();
    }
  }

  function runDeveloping() {
    const img = $('#dev-img');
    img.src = state.last.dataUrl;
    img.classList.remove('developed');
    show('screen-developing');
    setTimeout(() => {
      img.classList.add('developed');
      setTimeout(openResult, 1300);
    }, 2500);
  }

  function openResult() {
    const isVideo = state.last && state.last.kind === 'video';
    state.showingOriginal = false;
    $('#result-img').style.display = isVideo ? 'none' : 'block';
    $('#result-video').style.display = isVideo ? 'block' : 'none';
    $('#btn-before').style.display = isVideo ? 'none' : 'inline-block';
    if (isVideo) {
      const v = $('#result-video');
      v.src = state.last.url;
      v.loop = true;
    } else {
      $('#result-img').src = state.last.dataUrl;
      $('#btn-before').textContent = 'Original';
      $('#btn-before').classList.remove('on');
    }
    $('#result-cam').textContent = state.last.camName + (isVideo ? (state.last.slow ? ' \u00B7 ' + state.last.slow + 'x slow-mo' : ' \u00B7 video') : '');
    show('screen-result');
  }

  function toggleBefore() {
    if (!state.last || state.last.kind !== 'photo') return;
    state.showingOriginal = !state.showingOriginal;
    $('#result-img').src = state.showingOriginal ? state.lastOriginal : state.last.dataUrl;
    $('#btn-before').textContent = state.showingOriginal ? 'Edited' : 'Original';
    $('#btn-before').classList.toggle('on', state.showingOriginal);
  }

  function beep() {
    try {
      const ctx = new (window.AudioContext || window.webkitAudioContext)();
      const o = ctx.createOscillator(), g = ctx.createGain();
      o.type = 'square'; o.frequency.value = 880; g.gain.value = 0.05;
      o.connect(g); g.connect(ctx.destination);
      o.start(); o.stop(ctx.currentTime + 0.05);
      setTimeout(() => ctx.close(), 200);
    } catch (e) {}
  }

  /* ------------------------------------------------------------------ *
   * Video capture
   * ------------------------------------------------------------------ */
  async function toggleRecording() {
    if (Video.isRecording()) { await stopRecording(); return; }
    if (!Video.supported()) { toast('Is browser mein video recording support nahi hai'); return; }

    const cam = cameraById(state.cameraId);
    Video.setCaption($('#caption-input').value);
    const ok = await Video.start();
    if (!ok) { toast('Recording start nahi ho payi'); return; }

    $('#btn-shutter').classList.add('rec');
    $('#rec-badge').classList.add('show');
    $('#rec-time').textContent = '0:00';
    state.recTimer = setInterval(() => {
      $('#rec-time').textContent = fmtTime(Video.elapsed());
      if (Video.elapsed() > 60000) stopRecording();
    }, 250);
    toast('Recording...');
  }

  async function stopRecording() {
    clearInterval(state.recTimer); state.recTimer = null;
    $('#btn-shutter').classList.remove('rec');
    $('#rec-badge').classList.remove('show');
    if (Video.setLiveCaptions) Video.setLiveCaptions(false);
    const live = $('#live-captions'); if (live) live.checked = false;

    const cam = cameraById(state.cameraId);
    const blob = await Video.stop();
    if (!blob || !blob.size) { toast('Recording khaali rahi'); return; }

    let finalBlob = blob, slow = 0;
    if (state.speed !== 1) {
      toast('Slow motion bana rahe hain...');
      try { finalBlob = await Video.bakeSlowMotion(blob, state.speed); slow = state.speed; }
      catch (e) { finalBlob = blob; slow = 0; toast('Slow motion fail — normal speed rakha'); }
    }

    state.last = {
      kind: 'video',
      blob: finalBlob,
      url: URL.createObjectURL(finalBlob),
      camId: cam.id,
      camName: cam.name,
      caption: Video.getCaption(),
      slow: slow,
      size: finalBlob.size
    };
    openResult();
  }

  /* ------------------------------------------------------------------ *
   * Result actions
   * ------------------------------------------------------------------ */
  async function saveResult() {
    if (!state.last) return;
    if (state.last.kind === 'video') {
      await Media.add({
        kind: 'video', blob: state.last.blob, camId: state.last.camId,
        camName: state.last.camName, caption: state.last.caption,
        slow: state.last.slow, ts: Date.now()
      });
      downloadUrl(state.last.url, 'FaceCam_' + Date.now() + '.webm');
      toast('Video gallery mein save ho gaya');
    } else {
      await Media.add({
        kind: 'photo', dataUrl: state.last.dataUrl, camId: state.last.camId,
        camName: state.last.camName, ts: Date.now()
      });
      download(state.last.dataUrl, 'FaceCam_' + Date.now() + '.jpg');
      toast('Gallery mein save ho gaya');
    }
    renderMiniThumb();
  }

  function download(dataUrl, name) {
    const a = document.createElement('a');
    a.href = dataUrl; a.download = name;
    document.body.appendChild(a); a.click(); a.remove();
  }
  function downloadUrl(url, name) {
    const a = document.createElement('a');
    a.href = url; a.download = name;
    document.body.appendChild(a); a.click(); a.remove();
  }

  async function shareResult() {
    if (!state.last) return;
    try {
      let blob, file;
      if (state.last.kind === 'video') {
        blob = state.last.blob;
        file = new File([blob], 'FaceCam.webm', { type: blob.type || 'video/webm' });
      } else {
        blob = await (await fetch(state.last.dataUrl)).blob();
        file = new File([blob], 'FaceCam.jpg', { type: 'image/jpeg' });
      }
      if (navigator.canShare && navigator.canShare({ files: [file] })) {
        await navigator.share({ files: [file], title: 'FaceCam' });
        return;
      }
    } catch (e) {}
    if (state.last.kind === 'video') downloadUrl(state.last.url, 'FaceCam_' + Date.now() + '.webm');
    else download(state.last.dataUrl, 'FaceCam_' + Date.now() + '.jpg');
    toast('Share support nahi — file download ho gayi');
  }

  /* ------------------------------------------------------------------ *
   * Gallery
   * ------------------------------------------------------------------ */
  function paintGallery(items) {
    const grid = $('#gallery-grid');
    items.sort((a, b) => b.ts - a.ts);
    grid.innerHTML = '';
    $('#gallery-count').textContent = items.length ? ' \u00B7 ' + items.length : '';
    $('#gallery-empty').style.display = items.length ? 'none' : 'block';

    const groups = {};
    items.forEach(p => { (groups[p.camName] = groups[p.camName] || []).push(p); });

    Object.keys(groups).forEach(name => {
      const h = document.createElement('div');
      h.className = 'gallery-group-title';
      h.textContent = name + '  (' + groups[name].length + ')';
      grid.appendChild(h);
      groups[name].forEach(p => {
        const cell = document.createElement('button');
        cell.className = 'gallery-cell';
        if (p.kind === 'video') {
          const url = URL.createObjectURL(p.blob);
          p._url = url;
          cell.innerHTML = '<video src="' + url + '#t=0.1" muted playsinline preload="metadata"></video>' +
                           '<span class="vid-badge">' + (p.slow ? p.slow + 'x' : 'VIDEO') + '</span>';
        } else {
          cell.innerHTML = '<img src="' + p.dataUrl + '" alt="">';
        }
        cell.addEventListener('click', () => openItem(p));
        grid.appendChild(cell);
      });
    });

    if (items.length) {
      const clear = document.createElement('button');
      clear.className = 'link-btn danger';
      clear.textContent = 'Clear gallery';
      clear.style.gridColumn = '1 / -1';
      clear.addEventListener('click', async () => {
        if (confirm('Poori gallery delete karein?')) {
          for (const p of items) { try { await Media.del(p.id); } catch (e) {} }
          renderGallery();
        }
      });
      grid.appendChild(clear);
    }
  }

  function renderGallery() {
    paintGallery(Media.mem.slice());
    Media.all().then(list => paintGallery(list)).catch(() => {});
  }

  function renderMiniThumb() {
    const el = $('#btn-gallery-mini span');
    const last = Media.mem[Media.mem.length - 1];
    if (!el || !last) return;
    el.classList.add('has');
    if (last.kind === 'video') { el.style.background = 'linear-gradient(135deg,#3a2a44,#5a3a55)'; }
    else el.style.backgroundImage = 'url(' + last.dataUrl + ')';
  }

  function openItem(p) {
    state.viewerItem = p;
    const isVideo = p.kind === 'video';
    $('#viewer-img').style.display = isVideo ? 'none' : 'block';
    $('#viewer-video').style.display = isVideo ? 'block' : 'none';
    $('#viewer-speed').style.display = isVideo ? 'flex' : 'none';

    if (isVideo) {
      const v = $('#viewer-video');
      v.src = p._url || (p._url = URL.createObjectURL(p.blob));
      v.playbackRate = 1;
      $$('#viewer-speed .speed').forEach(b => b.classList.toggle('active', b.getAttribute('data-vspeed') === '1'));
    } else {
      $('#viewer-img').src = p.dataUrl;
    }
    $('#viewer').classList.add('show');

    $('#viewer-delete').onclick = async () => {
      try { await Media.del(p.id); } catch (e) {}
      $('#viewer').classList.remove('show');
      renderGallery(); renderMiniThumb();
    };
    $('#viewer-share').onclick = async () => {
      try {
        let blob, file;
        if (isVideo) { blob = p.blob; file = new File([blob], 'FaceCam.webm', { type: blob.type || 'video/webm' }); }
        else { blob = await (await fetch(p.dataUrl)).blob(); file = new File([blob], 'FaceCam.jpg', { type: 'image/jpeg' }); }
        if (navigator.canShare && navigator.canShare({ files: [file] })) { await navigator.share({ files: [file] }); return; }
      } catch (e) {}
      if (isVideo) downloadUrl(p._url, 'FaceCam_' + p.ts + '.webm');
      else download(p.dataUrl, 'FaceCam_' + p.ts + '.jpg');
    };
    $('#viewer-close').onclick = () => {
      const v = $('#viewer-video'); if (v) { try { v.pause(); } catch (e) {} }
      $('#viewer').classList.remove('show');
    };
  }

  /* ------------------------------------------------------------------ *
   * Camera strip + modes browser
   * ------------------------------------------------------------------ */
  function renderStrip() {
    const strip = $('#cam-strip');
    strip.innerHTML = '';
    CAMERAS.filter(c => groupOf(c) === state.family).forEach(cam => {
      const chip = document.createElement('button');
      chip.className = 'cam-chip' + (cam.id === state.cameraId ? ' active' : '');
      chip.innerHTML = '<span class="dot" style="filter:' + cam.filter + '"></span><span>' + cam.name + '</span>';
      chip.addEventListener('click', () => selectCamera(cam.id));
      strip.appendChild(chip);
    });
    const active = strip.querySelector('.cam-chip.active');
    if (active && active.scrollIntoView) active.scrollIntoView({ inline: 'center', block: 'nearest' });
  }

  function setFamily(g) {
    state.family = g;
    document.body.dataset.family = g;
    $$('#family-row .fam').forEach(b => b.classList.toggle('active', b.getAttribute('data-group') === g));
    const first = CAMERAS.filter(c => groupOf(c) === g)[0];
    if (first && groupOf(cameraById(state.cameraId)) !== g) selectCamera(first.id);
    else renderStrip();
  }

  function setMode(m) {
    state.mode = m;
    $$('#mode-row .mode-chip').forEach(b => b.classList.toggle('active', b.getAttribute('data-mode') === m));
    $('#video-extras').classList.toggle('show', m === 'video');
    $('#btn-shutter').title = m === 'video' ? 'Record' : 'Shutter';
    $('#btn-double').style.display = m === 'video' ? 'none' : 'inline-flex';
  }

  function selectCamera(id) {
    state.cameraId = id;
    const s = store.settings();
    s.defaultCamera = id;
    store.saveSettings(s);
    applyLiveLook();
    renderStrip();
  }

  function tabGroup(sel) {
    const a = $(sel + ' .tab.active');
    return a ? a.getAttribute('data-group') : 'vintage';
  }
  function setTab(sel, g) {
    $$(sel + ' .tab').forEach(t => t.classList.toggle('active', t.getAttribute('data-group') === g));
  }

  function renderModes(group) {
    group = group || tabGroup('#shop-tabs');
    const grid = $('#shop-grid');
    grid.innerHTML = '';
    CAMERAS.filter(c => groupOf(c) === group).forEach(cam => {
      const card = document.createElement('div');
      card.className = 'shop-card' + (cam.id === state.cameraId ? ' active' : '');
      card.innerHTML =
        '<div class="shop-preview" style="filter:' + cam.filter + '"><div class="sp-vig"></div></div>' +
        '<div class="shop-body"><strong>' + cam.name + '</strong><small>' + cam.tag + '</small>' +
        '<p>' + cam.desc + '</p>' +
        '<button class="buy-btn ' + (cam.id === state.cameraId ? 'owned' : '') + '">' +
        (cam.id === state.cameraId ? 'In use' : 'Use') + '</button></div>';
      card.querySelector('.buy-btn').addEventListener('click', () => {
        selectCamera(cam.id); setFamily(groupOf(cam)); renderModes(group); toast(cam.name + ' selected');
      });
      grid.appendChild(card);
    });
  }

  function openModes() {
    const g = groupOf(cameraById(state.cameraId));
    setTab('#shop-tabs', g);
    renderModes(g);
    show('screen-shop');
  }

  /* ------------------------------------------------------------------ *
   * Settings
   * ------------------------------------------------------------------ */
  function renderSettings() {
    const s = store.settings();
    $('#set-datestamp').checked = !!s.dateStamp;
    $('#set-border').checked = s.border !== false;
    $('#set-sound').checked = !!s.sound;
    $('#set-branding').checked = s.branding !== false;
    const sel = $('#set-default');
    sel.innerHTML = '';
    ['beauty', 'vintage'].forEach(g => {
      const og = document.createElement('optgroup');
      og.label = g === 'beauty' ? 'Beauty' : 'Vintage';
      CAMERAS.filter(c => groupOf(c) === g).forEach(c => {
        const o = document.createElement('option');
        o.value = c.id; o.textContent = c.name;
        if (c.id === s.defaultCamera) o.selected = true;
        og.appendChild(o);
      });
      sel.appendChild(og);
    });
  }

  /* ------------------------------------------------------------------ *
   * Import
   * ------------------------------------------------------------------ */
  function importPhoto(file) {
    const img = new Image();
    img.onload = () => {
      const cam = cameraById(state.cameraId);
      let w = img.naturalWidth, h = img.naturalHeight;
      const maxDim = 1300;
      if (Math.max(w, h) > maxDim) { const s = maxDim / Math.max(w, h); w = Math.round(w * s); h = Math.round(h * s); }
      const canvas = Effects.capture(img, w, h, cam.filter);
      state.lastOriginal = canvas.toDataURL('image/jpeg', 0.9);
      const out = developShot(canvas, cam, store.settings());
      state.last = { kind: 'photo', dataUrl: out.toDataURL('image/jpeg', 0.92), camId: cam.id, camName: cam.name };
      openResult();
    };
    img.onerror = () => toast('Ye image load nahi ho payi');
    img.src = URL.createObjectURL(file);
  }

  /* ------------------------------------------------------------------ *
   * Wiring
   * ------------------------------------------------------------------ */
  function bind() {
    $('#btn-onboard').addEventListener('click', async () => {
      store.markSeen();
      show('screen-viewfinder');
      await startCamera();
      applyLiveLook();
    });

    $('#btn-shutter').addEventListener('click', shutterPressed);

    $('#btn-switch').addEventListener('click', async () => {
      if (state.demo) { toast('Demo mode mein camera switch nahi hota'); return; }
      state.facing = state.facing === 'environment' ? 'user' : 'environment';
      await startCamera(); applyLiveLook();
      toast(state.facing === 'user' ? 'Front camera' : 'Back camera');
    });

    $('#btn-flash').addEventListener('click', async () => {
      state.flash = !state.flash;
      $('#btn-flash').classList.toggle('on', state.flash);
      if (state.flash) await applyTorch();
      toast(state.flash ? (state.torchAvailable ? 'Flash (torch) on' : 'Flash on — screen flash') : 'Flash off');
    });

    $('#btn-timer').addEventListener('click', () => {
      state.timer = state.timer === 0 ? 3 : (state.timer === 3 ? 10 : 0);
      $('#btn-timer').classList.toggle('on', state.timer > 0);
      $('#btn-timer').textContent = state.timer > 0 ? state.timer + 's' : '\u23F1';
    });

    $('#btn-double').addEventListener('click', () => {
      state.double = !state.double;
      state.firstShot = null;
      $('#double-chip').classList.remove('show');
      $('#btn-double').classList.toggle('on', state.double);
      toast(state.double ? 'Double exposure ON' : 'Double exposure off');
    });

    $('#btn-import').addEventListener('click', () => $('#import-input').click());
    $('#import-input').addEventListener('change', e => {
      const f = e.target.files[0];
      if (f) importPhoto(f);
      e.target.value = '';
    });

    $('#btn-settings').addEventListener('click', () => { renderSettings(); show('screen-settings'); });
    $('#btn-gallery-mini').addEventListener('click', () => { show('screen-gallery'); renderGallery(); });

    $('#btn-pro').addEventListener('click', () => {
      const on = !Pro.isEnabled();
      Pro.setEnabled(on);
      $('#btn-pro').classList.toggle('on', on);
      toast(on ? 'Manual mode on' : 'Manual mode off');
    });

    $$('#family-row .fam').forEach(b => b.addEventListener('click', () => setFamily(b.getAttribute('data-group'))));
    $$('#mode-row .mode-chip').forEach(b => b.addEventListener('click', () => setMode(b.getAttribute('data-mode'))));

    $$('#speed-row .speed').forEach(b => b.addEventListener('click', () => {
      state.speed = parseFloat(b.getAttribute('data-speed'));
      $$('#speed-row .speed').forEach(x => x.classList.toggle('active', x === b));
    }));

    $('#caption-input').addEventListener('input', e => Video.setCaption(e.target.value));

    const intensity = $('#intensity');
    if (intensity) {
      intensity.addEventListener('input', () => {
        state.intensity = parseFloat(intensity.value);
        const v = $('#intensity-val');
        if (v) v.textContent = Math.round(state.intensity * 100) + '%';
      });
    }

    $('#live-captions').addEventListener('change', e => {
      if (e.target.checked) {
        const ok = Video.setLiveCaptions(true, txt => { /* drawn onto the recording */ });
        if (!ok) { e.target.checked = false; toast('Is browser mein live captions support nahi hai'); }
        else toast('Live captions on');
      } else {
        Video.setLiveCaptions(false);
      }
    });

    $$('[data-back]').forEach(b => b.addEventListener('click', () => {
      show('screen-' + b.getAttribute('data-back'));
      applyLiveLook();
    }));

    $$('.nav-btn').forEach(b => b.addEventListener('click', () => {
      const n = b.getAttribute('data-nav');
      if (n === 'gallery') { show('screen-gallery'); renderGallery(); }
      else if (n === 'modes') openModes();
      else { show('screen-viewfinder'); applyLiveLook(); }
    }));

    $$('#shop-tabs .tab').forEach(t => t.addEventListener('click', () => {
      setTab('#shop-tabs', t.getAttribute('data-group'));
      renderModes(t.getAttribute('data-group'));
    }));

    $('#result-retake').addEventListener('click', () => { show('screen-viewfinder'); applyLiveLook(); });
    $('#result-save').addEventListener('click', saveResult);
    $('#result-share').addEventListener('click', shareResult);
    $('#btn-before').addEventListener('click', toggleBefore);

    $$('#viewer-speed .speed').forEach(b => b.addEventListener('click', () => {
      const rate = parseFloat(b.getAttribute('data-vspeed'));
      const v = $('#viewer-video');
      if (v) v.playbackRate = rate;
      $$('#viewer-speed .speed').forEach(x => x.classList.toggle('active', x === b));
    }));
    $('#speed-bake').addEventListener('click', async () => {
      const p = state.viewerItem;
      if (!p || p.kind !== 'video') return;
      const rate = parseFloat(($('#viewer-speed .speed.active') || {}).getAttribute
        ? $('#viewer-speed .speed.active').getAttribute('data-vspeed') : '1');
      if (rate === 1) { toast('Pehle 0.5x ya 0.25x chunein'); return; }
      toast('Slow motion bana rahe hain...');
      try {
        const slow = await Video.bakeSlowMotion(p.blob, rate);
        await Media.del(p.id);
        await Media.add({ kind: 'video', blob: slow, camId: p.camId, camName: p.camName, slow: rate, ts: Date.now() });
        $('#viewer').classList.remove('show');
        renderGallery();
        toast('Slow-mo save ho gaya');
      } catch (e) { toast('Slow motion fail ho gaya'); }
    });

    $('#set-datestamp').addEventListener('change', e => { const s = store.settings(); s.dateStamp = e.target.checked; store.saveSettings(s); });
    $('#set-border').addEventListener('change', e => { const s = store.settings(); s.border = e.target.checked; store.saveSettings(s); });
    $('#set-sound').addEventListener('change', e => { const s = store.settings(); s.sound = e.target.checked; store.saveSettings(s); });
    $('#set-branding').addEventListener('change', e => { const s = store.settings(); s.branding = e.target.checked; store.saveSettings(s); });
    $('#set-default').addEventListener('change', e => selectCamera(e.target.value));
    $('#set-reset').addEventListener('click', () => {
      if (confirm('App reset karein?')) { store.reset(); renderSettings(); toast('Reset ho gaya'); }
    });

    $('#fallback-demo').addEventListener('click', startDemo);
    $('#fallback-pick').addEventListener('click', () => $('#import-input').click());

    document.addEventListener('keydown', e => {
      const onVF = document.body.dataset.screen === 'screen-viewfinder';
      if (e.code === 'Space' && onVF) { e.preventDefault(); shutterPressed(); }
      if (e.code === 'Escape') { $('#viewer').classList.remove('show'); }
    });

    document.addEventListener('visibilitychange', () => {
      if (document.hidden) { if (!Video.isRecording()) stopCamera(); }
      else if (document.body.dataset.screen === 'screen-viewfinder' && !state.demo && !Video.isRecording()) startCamera();
    });

    window.addEventListener('beforeunload', () => { if (!Video.isRecording()) stopCamera(); });
  }

  /* ------------------------------------------------------------------ *
   * Boot
   * ------------------------------------------------------------------ */
  async function boot() {
    Media.open().catch(() => {});
    const s = store.settings();
    state.cameraId = (s.defaultCamera && CAMERAS.some(c => c.id === s.defaultCamera)) ? s.defaultCamera : 'nomo_135_b';
    state.family = groupOf(cameraById(state.cameraId));
    document.body.dataset.family = state.family;

    bind();
    Pro.init({ getTrack: () => (state.stream ? state.stream.getVideoTracks()[0] : null) });
    Video.init({
      getSource: () => (state.demo ? state.demoSource : $('#video')),
      getPreset: () => cameraById(state.cameraId),
      getSettings: () => store.settings(),
      getIntensity: () => (state.intensity == null ? 1 : state.intensity)
    });

    applyLiveLook();
    renderStrip();
    setMode('photo');
    renderMiniThumb();
    runIntro();
  }

  /* ------------------------------------------------------------------ *
   * 3D intro
   * ------------------------------------------------------------------ */
  function proceedFromIntro() {
    if (store.seen()) { show('screen-viewfinder'); startCamera().then(applyLiveLook); }
    else show('screen-onboarding');
  }

  function runIntro() {
    const el = $('#screen-intro');
    show('screen-intro');
    let done = false;
    const finish = () => {
      if (done) return;
      done = true;
      if (el) el.classList.remove('playing');
      proceedFromIntro();
    };
    if (!el) { proceedFromIntro(); return; }
    el.classList.remove('playing');
    void el.offsetWidth;            // force the animation to restart
    el.classList.add('playing');
    setTimeout(finish, 3000);
    el.addEventListener('click', finish, { once: true });
    const skip = $('#intro-skip');
    if (skip) skip.addEventListener('click', (e) => { e.stopPropagation(); finish(); }, { once: true });
  }

  if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', boot);
  else boot();
})();
