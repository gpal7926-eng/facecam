/* FaceCam — web preview app (HTML / CSS / JavaScript).
 *
 * Mirrors the Android app's feature set and is fully self-contained: no build
 * step, no dependencies, no network calls.
 *
 *   localStorage  -> settings, owned cameras, PRO flag, save counter
 *   IndexedDB     -> captured photos
 *   getUserMedia  -> live viewfinder   (falls back to a built-in demo scene)
 *   <canvas>      -> the whole film pipeline
 */
(function () {
  'use strict';

  /* ------------------------------------------------------------------ *
   * Local storage (settings / ownership / PRO)
   * ------------------------------------------------------------------ */
  const LS = {
    get(key, fallback) {
      try {
        const v = localStorage.getItem('facecam.' + key);
        return v === null ? fallback : JSON.parse(v);
      } catch (e) { return fallback; }
    },
    set(key, value) {
      try { localStorage.setItem('facecam.' + key, JSON.stringify(value)); } catch (e) {}
    }
  };

  const FREE_IDS = CAMERAS.filter(c => !c.paid).map(c => c.id);

  const store = {
    owned() {
      const o = LS.get('owned', null);
      return o === null ? FREE_IDS.slice() : o;
    },
    owns(id) { return this.owned().indexOf(id) !== -1; },
    grant(id) {
      const o = this.owned();
      if (o.indexOf(id) === -1) { o.push(id); LS.set('owned', o); }
    },
    isPro() { return LS.get('pro', false); },
    setPro(v) { LS.set('pro', !!v); },
    settings() {
      return LS.get('settings', {
        dateStamp: true, border: true, sound: true, defaultCamera: 'nomo_135_b'
      });
    },
    saveSettings(s) { LS.set('settings', s); },
    saves() { return LS.get('saves', 0); },
    bumpSaves() { const n = this.saves() + 1; LS.set('saves', n); return n; },
    seen() { return LS.get('seen', false); },
    markSeen() { LS.set('seen', true); },
    reset() {
      ['owned', 'pro', 'settings', 'saves', 'seen'].forEach(k => localStorage.removeItem('facecam.' + k));
    }
  };

  /* ------------------------------------------------------------------ *
   * IndexedDB photo store
   * ------------------------------------------------------------------ */
  const Photos = {
    db: null,
    mem: [],
    nextId: 1,
    useMem: false,

    /* Opens IndexedDB, but never blocks or hangs the UI: if the database is
       unavailable (private mode, blocked storage, old browser) we silently
       fall back to an in-memory store so the gallery still works. */
    open() {
      return new Promise((resolve) => {
        if (!window.indexedDB) { this.useMem = true; resolve(null); return; }
        let settled = false;
        const giveUp = setTimeout(() => {
          if (!settled) { settled = true; this.useMem = true; resolve(null); }
        }, 4000);
        let req;
        try { req = indexedDB.open('facecam', 1); }
        catch (e) { clearTimeout(giveUp); this.useMem = true; resolve(null); return; }
        req.onupgradeneeded = () => {
          const db = req.result;
          if (!db.objectStoreNames.contains('photos')) {
            db.createObjectStore('photos', { keyPath: 'id', autoIncrement: true });
          }
        };
        req.onsuccess = () => {
          this.db = req.result;
          clearTimeout(giveUp);
          if (!settled) { settled = true; resolve(this.db); }
        };
        req.onerror = () => {
          clearTimeout(giveUp);
          this.useMem = true;
          if (!settled) { settled = true; resolve(null); }
        };
      });
    },

    /* Write-through: every photo is kept in memory straight away (so the
       gallery is instant and never blocks), and mirrored into IndexedDB for
       persistence across reloads. If IndexedDB misbehaves, the session still
       works — only cross-reload persistence is lost. */
    add(photo) {
      const rec = Object.assign({}, photo, { id: this.nextId++ });
      this.mem.push(rec);
      if (this.db) {
        try {
          this.db.transaction('photos', 'readwrite').objectStore('photos').add(photo);
        } catch (e) { /* ignore — memory copy already holds it */ }
      }
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
      if (this.db) {
        try { this.db.transaction('photos', 'readwrite').objectStore('photos').delete(id); }
        catch (e) { /* ignore */ }
      }
      return Promise.resolve();
    }
  };

  /* ------------------------------------------------------------------ *
   * App state + small helpers
   * ------------------------------------------------------------------ */
  const state = {
    cameraId: 'nomo_135_b',
    facing: 'environment',
    flash: false,
    torchAvailable: false,
    timer: 0,
    stream: null,
    demo: false,
    demoSource: null,
    lastPhoto: null,
    lastOriginal: null,
    showingOriginal: false,
    double: false,
    firstShot: null,
    pendingTimer: null,
    busy: false,
    camGen: 0
  };

  const $ = (sel) => document.querySelector(sel);
  const $$ = (sel) => Array.prototype.slice.call(document.querySelectorAll(sel));
  const cameraById = (id) => CAMERAS.filter(c => c.id === id)[0] || CAMERAS[0];
  const rupee = (n) => '\u20B9' + n;

  function withTimeout(promise, ms) {
    return new Promise((resolve, reject) => {
      const t = setTimeout(() => reject(new Error('camera-timeout')), ms);
      promise.then(
        v => { clearTimeout(t); resolve(v); },
        e => { clearTimeout(t); reject(e); }
      );
    });
  }

  function toast(msg) {
    const t = $('#toast');
    t.textContent = msg;
    t.classList.add('show');
    clearTimeout(t._h);
    t._h = setTimeout(() => t.classList.remove('show'), 1900);
  }

  function show(id) {
    $$('.screen').forEach(s => s.classList.toggle('active', s.id === id));
    document.body.dataset.screen = id;
    if (id !== 'screen-viewfinder' && state.pendingTimer) {
      clearInterval(state.pendingTimer);
      state.pendingTimer = null;
      $('#countdown').classList.remove('show');
    }
  }

  /* ------------------------------------------------------------------ *
   * Built-in demo scene (used when no camera is available, so the whole
   * pipeline — shutter, film effects, save, gallery — still works)
   * ------------------------------------------------------------------ */
  function drawSampleScene(canvas) {
    canvas.width = 540; canvas.height = 960;
    const g = canvas.getContext('2d');
    const grad = g.createLinearGradient(0, 0, 0, 960);
    grad.addColorStop(0, '#1d3d72');
    grad.addColorStop(0.42, '#e2814a');
    grad.addColorStop(0.62, '#f7c46d');
    grad.addColorStop(1, '#6b4a3a');
    g.fillStyle = grad; g.fillRect(0, 0, 540, 960);

    g.beginPath(); g.arc(355, 330, 150, 0, Math.PI * 2);
    g.fillStyle = 'rgba(255,220,140,0.20)'; g.fill();
    g.beginPath(); g.arc(355, 330, 92, 0, Math.PI * 2);
    g.fillStyle = '#fff3cd'; g.fill();

    g.fillStyle = '#4b3a55';
    g.beginPath(); g.moveTo(-40, 700); g.lineTo(190, 470); g.lineTo(430, 700); g.closePath(); g.fill();
    g.fillStyle = '#38293f';
    g.beginPath(); g.moveTo(250, 720); g.lineTo(470, 520); g.lineTo(640, 720); g.closePath(); g.fill();
    g.fillStyle = '#241c2c'; g.fillRect(0, 690, 540, 270);

    for (let i = 0; i < 5; i++) {
      const x = 60 + i * 105, y = 690;
      g.fillStyle = '#2f2438'; g.fillRect(x - 4, y - 40, 8, 45);
      g.beginPath(); g.arc(x, y - 52, 22, 0, Math.PI * 2);
      g.fillStyle = '#3d6b4f'; g.fill();
    }

    g.fillStyle = 'rgba(255,255,255,0.85)';
    g.font = '600 26px -apple-system, Segoe UI, sans-serif';
    g.fillText('13:42', 40, 70);

    g.fillStyle = 'rgba(255,255,255,0.75)';
    g.font = '600 17px -apple-system, Segoe UI, sans-serif';
    g.fillText('FaceCam demo scene', 40, 930);
  }

  /* ------------------------------------------------------------------ *
   * Camera
   * ------------------------------------------------------------------ */
  async function startCamera() {
    const gen = ++state.camGen;   // invalidates any earlier in-flight attempt
    stopCamera();
    state.demo = false;
    $('#demo-canvas').style.display = 'none';

    if (!navigator.mediaDevices || !navigator.mediaDevices.getUserMedia) {
      showFallback('Is browser mein camera support nahi hai.');
      return;
    }
    try {
      const stream = await withTimeout(
        navigator.mediaDevices.getUserMedia({
          video: { facingMode: state.facing, width: { ideal: 1440 }, height: { ideal: 1920 } },
          audio: false
        }),
        7000
      );
      if (!stream || !stream.getVideoTracks().length) throw new Error('no-video-track');
      if (gen !== state.camGen) { stream.getTracks().forEach(t => t.stop()); return; }
      state.stream = stream;
      const video = $('#video');
      video.srcObject = stream;
      await video.play().catch(() => {});
      $('#cam-fallback').classList.remove('show');
      video.style.display = 'block';
      applyTorch();
    } catch (e) {
      if (gen !== state.camGen || state.demo) return;   // a newer attempt / demo mode won
      let why = 'Camera is device par available nahi hai.';
      if (e && e.name === 'NotAllowedError') why = 'Camera permission deny ho gayi.';
      else if (e && e.name === 'NotFoundError') why = 'Koi camera nahi mila.';
      else if (e && e.message === 'camera-timeout') why = 'Camera start nahi ho paya (timeout).';
      showFallback(why);
    }
  }

  function showFallback(reason) {
    $('#fallback-text').textContent = reason;
    $('#cam-fallback').classList.add('show');
    $('#video').style.display = 'none';
  }

  function stopCamera() {
    if (state.stream) {
      state.stream.getTracks().forEach(t => t.stop());
      state.stream = null;
    }
    state.torchAvailable = false;
  }

  /* Real torch if the device exposes it, otherwise we fall back to a
     screen-flash at capture time. */
  async function applyTorch() {
    if (!state.stream) return;
    const track = state.stream.getVideoTracks()[0];
    if (!track || !track.getCapabilities) return;
    const caps = track.getCapabilities();
    if (caps && caps.torch) {
      try {
        await track.applyConstraints({ advanced: [{ torch: state.flash }] });
        state.torchAvailable = true;
        return;
      } catch (e) { /* fall through to screen flash */ }
    }
    state.torchAvailable = false;
  }

  function startDemo() {
    state.camGen++;   // cancel any pending camera attempt so it can't clobber this
    const cv = $('#demo-canvas');
    if (!cv.dataset.drawn) { drawSampleScene(cv); cv.dataset.drawn = '1'; }
    cv.style.display = 'block';
    $('#video').style.display = 'none';
    $('#cam-fallback').classList.remove('show');
    state.demo = true;
    state.demoSource = cv;
    applyLiveLook();
    toast('Demo scene ready — shutter dabayein');
  }

  function applyLiveLook() {
    const cam = cameraById(state.cameraId);
    $('#video').style.filter = cam.filter;
    $('#demo-canvas').style.filter = cam.filter;
    $('#live-vignette').style.opacity = Math.min(0.85, cam.vignette * 0.8).toFixed(2);
    const frameEl = $('#live-frame');
    frameEl.className = 'live-frame ' + (cam.frame ? cam.frame.style : '');
    frameEl.style.setProperty('--frame-color', cam.frame ? cam.frame.color : '#fff');
    $('#cam-name').textContent = cam.name;
    $('#cam-tag').textContent = cam.tag;
  }

  function hasSource() { return !!state.stream || state.demo; }

  /* ------------------------------------------------------------------ *
   * Capture
   * ------------------------------------------------------------------ */
  function grabCanvas(extraFilter) {
    const cam = cameraById(state.cameraId);
    const source = state.demo ? state.demoSource : $('#video');
    let w = source.videoWidth || source.width || 1080;
    let h = source.videoHeight || source.height || 1440;
    const maxDim = 1400;
    if (Math.max(w, h) > maxDim) {
      const s = maxDim / Math.max(w, h);
      w = Math.round(w * s); h = Math.round(h * s);
    }
    return Effects.capture(source, w, h, cam.filter + (extraFilter || ''));
  }

  function screenFlash() {
    const f = $('#flash-overlay');
    f.classList.add('go');
    setTimeout(() => f.classList.remove('go'), 220);
  }

  function shutterPressed() {
    if (state.busy) return;
    if (!hasSource()) {
      toast('Pehle camera on karein ya demo scene chalayein');
      return;
    }
    if (state.timer > 0) {
      let n = state.timer;
      const overlay = $('#countdown');
      overlay.classList.add('show');
      overlay.textContent = n;
      state.pendingTimer = setInterval(() => {
        n -= 1;
        if (n <= 0) {
          clearInterval(state.pendingTimer);
          state.pendingTimer = null;
          overlay.classList.remove('show');
          shoot();
        } else {
          overlay.textContent = n;
        }
      }, 1000);
    } else {
      shoot();
    }
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
        toast('Double exposure: pehla shot ho gaya — ab dusra lein');
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
    if (cam.instant && !store.isPro()) {
      const developed = canvas.cloneNode(false);
      developed.width = canvas.width; developed.height = canvas.height;
      developed.getContext('2d').drawImage(canvas, 0, 0);
      Effects.process(developed, cam, store.settings());
      state.lastPhoto = { dataUrl: developed.toDataURL('image/jpeg', 0.92), camId: cam.id, camName: cam.name };
      runDeveloping();
    } else {
      Effects.process(canvas, cam, store.settings());
      state.lastPhoto = { dataUrl: canvas.toDataURL('image/jpeg', 0.92), camId: cam.id, camName: cam.name };
      if (store.settings().sound) beep();
      openResult();
    }
  }

  function runDeveloping() {
    const img = $('#dev-img');
    img.src = state.lastPhoto.dataUrl;
    img.classList.remove('developed');
    show('screen-developing');
    setTimeout(() => {
      img.classList.add('developed');
      setTimeout(openResult, 1300);
    }, 2500);
  }

  function openResult() {
    state.showingOriginal = false;
    const img = $('#result-img');
    img.src = state.lastPhoto.dataUrl;
    $('#result-cam').textContent = state.lastPhoto.camName;
    $('#btn-before').textContent = 'Original';
    $('#btn-before').classList.remove('on');
    show('screen-result');
  }

  function toggleBefore() {
    if (!state.lastPhoto) return;
    state.showingOriginal = !state.showingOriginal;
    $('#result-img').src = state.showingOriginal ? state.lastOriginal : state.lastPhoto.dataUrl;
    $('#btn-before').textContent = state.showingOriginal ? 'Film' : 'Original';
    $('#btn-before').classList.toggle('on', state.showingOriginal);
  }

  function beep() {
    try {
      const ctx = new (window.AudioContext || window.webkitAudioContext)();
      const o = ctx.createOscillator();
      const g = ctx.createGain();
      o.type = 'square';
      o.frequency.value = 880;
      g.gain.value = 0.05;
      o.connect(g); g.connect(ctx.destination);
      o.start();
      o.stop(ctx.currentTime + 0.05);
      setTimeout(() => ctx.close(), 200);
    } catch (e) {}
  }

  /* ------------------------------------------------------------------ *
   * Result actions
   * ------------------------------------------------------------------ */
  async function savePhoto() {
    if (!state.lastPhoto) return;
    try {
      await Photos.add({
        dataUrl: state.lastPhoto.dataUrl,
        camId: state.lastPhoto.camId,
        camName: state.lastPhoto.camName,
        ts: Date.now()
      });
    } catch (e) { /* storage unavailable — still deliver the file */ }
    download(state.lastPhoto.dataUrl, 'FaceCam_' + Date.now() + '.jpg');
    const n = store.bumpSaves();
    toast('Gallery mein save ho gaya');
    if (!store.isPro() && n % 3 === 0) showInterstitial();
  }

  function download(dataUrl, name) {
    const a = document.createElement('a');
    a.href = dataUrl;
    a.download = name;
    document.body.appendChild(a);
    a.click();
    a.remove();
  }

  async function sharePhoto() {
    if (!state.lastPhoto) return;
    try {
      const blob = await (await fetch(state.lastPhoto.dataUrl)).blob();
      const file = new File([blob], 'FaceCam.jpg', { type: 'image/jpeg' });
      if (navigator.canShare && navigator.canShare({ files: [file] })) {
        await navigator.share({ files: [file], title: 'FaceCam' });
        return;
      }
    } catch (e) {}
    download(state.lastPhoto.dataUrl, 'FaceCam_' + Date.now() + '.jpg');
    toast('Share support nahi — photo download ho gayi');
  }

  function showInterstitial() {
    const ad = $('#interstitial');
    ad.classList.add('show');
    setTimeout(() => ad.classList.remove('show'), 2500);
  }

  /* ------------------------------------------------------------------ *
   * Gallery
   * ------------------------------------------------------------------ */
  function paintGallery(photos) {
    const grid = $('#gallery-grid');
    photos.sort((a, b) => b.ts - a.ts);
    grid.innerHTML = '';
    $('#gallery-count').textContent = photos.length ? ' \u00B7 ' + photos.length : '';
    $('#gallery-empty').style.display = photos.length ? 'none' : 'block';

    const groups = {};
    photos.forEach(p => { (groups[p.camName] = groups[p.camName] || []).push(p); });

    Object.keys(groups).forEach(name => {
      const h = document.createElement('div');
      h.className = 'gallery-group-title';
      h.textContent = name + '  (' + groups[name].length + ')';
      grid.appendChild(h);
      groups[name].forEach(p => {
        const cell = document.createElement('button');
        cell.className = 'gallery-cell';
        cell.innerHTML = '<img src="' + p.dataUrl + '" alt="">';
        cell.addEventListener('click', () => openPhoto(p));
        grid.appendChild(cell);
      });
    });

    if (photos.length) {
      const clear = document.createElement('button');
      clear.className = 'link-btn danger';
      clear.textContent = 'Clear gallery';
      clear.style.gridColumn = '1 / -1';
      clear.addEventListener('click', async () => {
        if (confirm('Poori gallery delete karein?')) {
          for (const p of photos) { try { await Photos.del(p.id); } catch (e) {} }
          renderGallery();
        }
      });
      grid.appendChild(clear);
    }
  }

  /* Paint instantly from the in-memory copy so the gallery never waits on
     storage, then refresh once the database answers. */
  function renderGallery() {
    paintGallery(Photos.mem.slice());
    Photos.all().then(list => paintGallery(list)).catch(() => {});
  }

  function openPhoto(p) {
    $('#viewer-img').src = p.dataUrl;
    $('#viewer').classList.add('show');
    $('#viewer-delete').onclick = async () => {
      try { await Photos.del(p.id); } catch (e) {}
      $('#viewer').classList.remove('show');
      renderGallery();
    };
    $('#viewer-share').onclick = async () => {
      try {
        const blob = await (await fetch(p.dataUrl)).blob();
        const file = new File([blob], 'FaceCam.jpg', { type: 'image/jpeg' });
        if (navigator.canShare && navigator.canShare({ files: [file] })) {
          await navigator.share({ files: [file] });
          return;
        }
      } catch (e) {}
      download(p.dataUrl, 'FaceCam_' + p.ts + '.jpg');
    };
    $('#viewer-close').onclick = () => $('#viewer').classList.remove('show');
  }

  /* ------------------------------------------------------------------ *
   * Camera picker + shop
   * ------------------------------------------------------------------ */
  function renderPicker() {
    const wrap = $('#picker-list');
    wrap.innerHTML = '';
    CAMERAS.forEach(cam => {
      const owned = store.owns(cam.id) || store.isPro();
      const item = document.createElement('button');
      item.className = 'picker-item' + (cam.id === state.cameraId ? ' selected' : '');
      item.innerHTML =
        '<span class="swatch" style="filter:' + cam.filter + '"></span>' +
        '<span class="pi-text"><strong>' + cam.name + '</strong><small>' + cam.tag + '</small></span>' +
        (owned ? '<span class="pi-state">' + (cam.id === state.cameraId ? 'In use' : 'Owned') + '</span>'
               : '<span class="pi-state locked">' + rupee(cam.price) + '</span>');
      item.addEventListener('click', () => {
        if (!owned) { toast('Pehle ise shop se unlock karein'); closeSheet('#sheet-picker'); openShop(); return; }
        selectCamera(cam.id);
        closeSheet('#sheet-picker');
      });
      wrap.appendChild(item);
    });
  }

  function renderShop() {
    const grid = $('#shop-grid');
    grid.innerHTML = '';
    CAMERAS.forEach(cam => {
      const owned = store.owns(cam.id) || store.isPro();
      const card = document.createElement('div');
      card.className = 'shop-card';
      card.innerHTML =
        '<div class="shop-preview" style="filter:' + cam.filter + '">' +
        '<div class="sp-vig"></div></div>' +
        '<div class="shop-body"><strong>' + cam.name + '</strong>' +
        '<small>' + cam.tag + '</small>' +
        '<p>' + cam.desc + '</p>' +
        '<button class="buy-btn ' + (owned ? 'owned' : '') + '">' +
        (owned ? 'Owned' : (cam.paid ? rupee(cam.price) : 'Free')) + '</button></div>';
      const btn = card.querySelector('.buy-btn');
      btn.addEventListener('click', () => {
        if (owned) { selectCamera(cam.id); show('screen-viewfinder'); return; }
        if (cam.paid && !store.isPro()) { openPaywall(); return; }
        store.grant(cam.id);
        renderShop(); renderPicker();
        toast(cam.name + ' unlocked');
      });
      grid.appendChild(card);
    });
    $('#shop-pro-banner').classList.toggle('pro', store.isPro());
    $('#shop-pro-text').textContent = store.isPro()
      ? 'PRO active — saare cameras aur tools unlocked.'
      : 'PRO unlock karein — saare cameras + import + no ads.';
  }

  function selectCamera(id) {
    state.cameraId = id;
    const s = store.settings();
    s.defaultCamera = id;
    store.saveSettings(s);
    applyLiveLook();
  }

  function openShop() { renderShop(); show('screen-shop'); }
  function openPaywall() { show('screen-paywall'); }
  function closeSheet(sel) { const el = $(sel); if (el) el.classList.remove('show'); }
  function openSheet(sel) { const el = $(sel); if (el) el.classList.add('show'); }

  /* ------------------------------------------------------------------ *
   * Settings
   * ------------------------------------------------------------------ */
  function renderSettings() {
    const s = store.settings();
    $('#set-datestamp').checked = !!s.dateStamp;
    $('#set-border').checked = s.border !== false;
    $('#set-sound').checked = !!s.sound;
    const sel = $('#set-default');
    sel.innerHTML = '';
    CAMERAS.forEach(c => {
      const opt = document.createElement('option');
      opt.value = c.id; opt.textContent = c.name;
      if (c.id === s.defaultCamera) opt.selected = true;
      sel.appendChild(opt);
    });
    $('#set-pro-state').textContent = store.isPro() ? 'PRO active' : 'Free plan';
  }

  /* ------------------------------------------------------------------ *
   * Import (PRO)
   * ------------------------------------------------------------------ */
  function importPhoto(file) {
    const img = new Image();
    img.onload = () => {
      const cam = cameraById(state.cameraId);
      let w = img.naturalWidth, h = img.naturalHeight;
      const maxDim = 1400;
      if (Math.max(w, h) > maxDim) { const s = maxDim / Math.max(w, h); w = Math.round(w * s); h = Math.round(h * s); }
      const canvas = Effects.capture(img, w, h, cam.filter);
      state.lastOriginal = canvas.toDataURL('image/jpeg', 0.9);
      Effects.process(canvas, cam, store.settings());
      state.lastPhoto = { dataUrl: canvas.toDataURL('image/jpeg', 0.92), camId: cam.id, camName: cam.name };
      openResult();
    };
    img.onerror = () => toast('Ye image load nahi ho payi');
    img.src = URL.createObjectURL(file);
  }

  function requestImport() {
    if (!store.isPro()) { openPaywall(); toast('Import PRO feature hai'); return; }
    $('#import-input').click();
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
      await startCamera();
      applyLiveLook();
      toast(state.facing === 'user' ? 'Front camera' : 'Back camera');
    });

    $('#btn-flash').addEventListener('click', async () => {
      state.flash = !state.flash;
      $('#btn-flash').classList.toggle('on', state.flash);
      if (state.flash) await applyTorch();
      toast(state.flash
        ? (state.torchAvailable ? 'Flash (torch) on' : 'Flash on — screen flash')
        : 'Flash off');
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
      toast(state.double ? 'Double exposure ON — 2 shots lein' : 'Double exposure off');
    });

    $('#btn-picker').addEventListener('click', () => { renderPicker(); openSheet('#sheet-picker'); });
    $('#btn-shop').addEventListener('click', openShop);
    /* Navigate immediately, then fill the grid — never block the UI on storage. */
    $('#btn-gallery').addEventListener('click', () => { show('screen-gallery'); renderGallery(); });
    $('#btn-settings').addEventListener('click', () => { renderSettings(); show('screen-settings'); });
    $('#btn-paywall').addEventListener('click', openPaywall);
    $('#shop-pro-banner').addEventListener('click', openPaywall);
    $('#btn-import').addEventListener('click', requestImport);

    $$('[data-back]').forEach(b => b.addEventListener('click', () => {
      const target = b.getAttribute('data-back');
      show('screen-' + target);
      if (target === 'viewfinder') { applyLiveLook(); }
    }));

    $$('[data-close-sheet]').forEach(b => b.addEventListener('click', () => closeSheet('#' + b.getAttribute('data-close-sheet'))));
    $$('.sheet-backdrop').forEach(b => b.addEventListener('click', () => b.parentElement.classList.remove('show')));

    $('#result-retake').addEventListener('click', () => { show('screen-viewfinder'); applyLiveLook(); });
    $('#result-save').addEventListener('click', savePhoto);
    $('#result-share').addEventListener('click', sharePhoto);
    $('#btn-before').addEventListener('click', toggleBefore);

    $('#paywall-buy').addEventListener('click', () => {
      store.setPro(true);
      renderSettings(); renderShop(); renderPicker();
      toast('PRO unlocked — sab kuch khul gaya');
      show('screen-viewfinder');
    });
    $('#paywall-restore').addEventListener('click', () => {
      toast(store.isPro() ? 'PRO restore ho gaya' : 'Koi purchase nahi mili');
    });

    $('#set-datestamp').addEventListener('change', e => { const s = store.settings(); s.dateStamp = e.target.checked; store.saveSettings(s); });
    $('#set-border').addEventListener('change', e => { const s = store.settings(); s.border = e.target.checked; store.saveSettings(s); });
    $('#set-sound').addEventListener('change', e => { const s = store.settings(); s.sound = e.target.checked; store.saveSettings(s); });
    $('#set-default').addEventListener('change', e => selectCamera(e.target.value));
    $('#set-reset').addEventListener('click', () => {
      if (confirm('App reset karein? Saari settings aur unlocks hat jayenge.')) {
        store.reset(); renderSettings(); renderPicker(); renderShop(); toast('Reset ho gaya');
      }
    });

    $('#import-input').addEventListener('change', e => {
      const f = e.target.files[0];
      if (f) importPhoto(f);
      e.target.value = '';
    });

    $('#fallback-demo').addEventListener('click', startDemo);
    $('#fallback-pick').addEventListener('click', requestImport);

    document.addEventListener('keydown', e => {
      const onVF = document.body.dataset.screen === 'screen-viewfinder';
      if (e.code === 'Space' && onVF) { e.preventDefault(); shutterPressed(); }
      if (e.code === 'Escape') { closeSheet('#sheet-picker'); $('#viewer').classList.remove('show'); }
    });

    document.addEventListener('visibilitychange', () => {
      if (document.hidden) stopCamera();
      else if (document.body.dataset.screen === 'screen-viewfinder' && !state.demo) startCamera();
    });

    window.addEventListener('beforeunload', stopCamera);
  }

  /* ------------------------------------------------------------------ *
   * Boot
   * ------------------------------------------------------------------ */
  async function boot() {
    Photos.open().catch(() => {});
    const s = store.settings();
    state.cameraId = (s.defaultCamera && CAMERAS.some(c => c.id === s.defaultCamera))
      ? s.defaultCamera : 'nomo_135_b';
    bind();
    applyLiveLook();
    show('screen-splash');
    setTimeout(() => {
      if (store.seen()) {
        show('screen-viewfinder');
        startCamera().then(applyLiveLook);
      } else {
        show('screen-onboarding');
      }
    }, 1400);
  }

  if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', boot);
  else boot();
})();
