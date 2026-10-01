/* FaceCam — web preview app.
 * Mirrors the Android app's feature set. All state lives on-device:
 *   localStorage  -> settings, owned cameras, PRO flag, save counter
 *   IndexedDB     -> captured photos
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
    open() {
      return new Promise((resolve, reject) => {
        const req = indexedDB.open('facecam', 1);
        req.onupgradeneeded = () => {
          const db = req.result;
          if (!db.objectStoreNames.contains('photos')) {
            db.createObjectStore('photos', { keyPath: 'id', autoIncrement: true });
          }
        };
        req.onsuccess = () => { this.db = req.result; resolve(this.db); };
        req.onerror = () => reject(req.error);
      });
    },
    tx(mode) { return this.db.transaction('photos', mode).objectStore('photos'); },
    add(photo) {
      return new Promise((resolve, reject) => {
        const r = this.tx('readwrite').add(photo);
        r.onsuccess = () => resolve(r.result);
        r.onerror = () => reject(r.error);
      });
    },
    all() {
      return new Promise((resolve, reject) => {
        const r = this.tx('readonly').getAll();
        r.onsuccess = () => resolve(r.result || []);
        r.onerror = () => reject(r.error);
      });
    },
    del(id) {
      return new Promise((resolve, reject) => {
        const r = this.tx('readwrite').delete(id);
        r.onsuccess = () => resolve();
        r.onerror = () => reject(r.error);
      });
    }
  };

  /* ------------------------------------------------------------------ *
   * App state + helpers
   * ------------------------------------------------------------------ */
  const state = {
    cameraId: 'nomo_135_b',
    facing: 'environment',
    flash: false,
    timer: 0,
    stream: null,
    streamFacing: null,
    lastPhoto: null,
    double: false,
    firstShot: null,
    pendingTimer: null,
    fallbackSource: null,
    camSupported: true
  };

  const $ = (sel) => document.querySelector(sel);
  const $$ = (sel) => Array.prototype.slice.call(document.querySelectorAll(sel));
  const cameraById = (id) => CAMERAS.filter(c => c.id === id)[0] || CAMERAS[0];
  const rupee = (n) => '\u20B9' + n;

  function toast(msg) {
    const t = $('#toast');
    t.textContent = msg;
    t.classList.add('show');
    clearTimeout(t._h);
    t._h = setTimeout(() => t.classList.remove('show'), 1900);
  }

  /* ------------------------------------------------------------------ *
   * Screens
   * ------------------------------------------------------------------ */
  function show(id) {
    $$('.screen').forEach(s => s.classList.toggle('active', s.id === id));
    document.body.dataset.screen = id;
  }

  /* ------------------------------------------------------------------ *
   * Camera
   * ------------------------------------------------------------------ */
  async function startCamera() {
    stopCamera();
    if (!navigator.mediaDevices || !navigator.mediaDevices.getUserMedia) {
      state.camSupported = false;
      $('#cam-fallback').classList.add('show');
      return;
    }
    try {
      state.stream = await navigator.mediaDevices.getUserMedia({
        video: { facingMode: state.facing }, audio: false
      });
      state.streamFacing = state.facing;
      const video = $('#video');
      video.srcObject = state.stream;
      await video.play().catch(() => {});
      $('#cam-fallback').classList.remove('show');
      state.camSupported = true;
    } catch (e) {
      state.camSupported = false;
      $('#cam-fallback').classList.add('show');
    }
  }

  function stopCamera() {
    if (state.stream) {
      state.stream.getTracks().forEach(t => t.stop());
      state.stream = null;
    }
  }

  function applyLiveLook() {
    const cam = cameraById(state.cameraId);
    const video = $('#video');
    video.style.filter = cam.filter;
    const vig = $('#live-vignette');
    vig.style.opacity = Math.min(0.85, cam.vignette * 0.8).toFixed(2);
    $('#live-frame').className = 'live-frame ' + (cam.frame ? cam.frame.style : '');
    $('#live-frame').style.setProperty('--frame-color', cam.frame ? cam.frame.color : '#fff');
    $('#cam-name').textContent = cam.name;
    $('#cam-tag').textContent = cam.tag;
  }

  /* ------------------------------------------------------------------ *
   * Capture
   * ------------------------------------------------------------------ */
  function grabCanvas() {
    const video = $('#video');
    const cam = cameraById(state.cameraId);
    let w = video.videoWidth || 1080;
    let h = video.videoHeight || 1440;
    // keep a sensible cap so canvas work stays fast
    const maxDim = 1400;
    if (Math.max(w, h) > maxDim) {
      const s = maxDim / Math.max(w, h);
      w = Math.round(w * s); h = Math.round(h * s);
    }
    const source = state.fallbackSource || video;
    return Effects.capture(source, w, h, cam.filter);
  }

  function shoot() {
    const cam = cameraById(state.cameraId);
    if (cam.instant && !store.isPro()) {
      runDeveloping(cam);
    } else {
      finishShot(cam);
    }
  }

  function finishShot(cam) {
    let canvas = grabCanvas();
    if (state.double) {
      if (!state.firstShot) {
        state.firstShot = canvas;
        toast('Double exposure: pehla shot le liya. Ab dusra lein.');
        return;
      }
      canvas = Effects.blend(state.firstShot, canvas);
      state.firstShot = null;
      state.double = false;
      $('#btn-double').classList.remove('on');
    }
    Effects.process(canvas, cam, store.settings());
    if (store.settings().sound) beep();
    state.lastPhoto = { dataUrl: canvas.toDataURL('image/jpeg', 0.92), camId: cam.id, camName: cam.name };
    const img = $('#result-img');
    img.src = state.lastPhoto.dataUrl;
    img.style.filter = 'none';
    show('screen-result');
  }

  function runDeveloping(cam) {
    const canvas = grabCanvas();
    Effects.process(canvas, cam, store.settings());
    state.lastPhoto = { dataUrl: canvas.toDataURL('image/jpeg', 0.92), camId: cam.id, camName: cam.name };
    const img = $('#dev-img');
    img.src = state.lastPhoto.dataUrl;
    img.classList.remove('developed');
    show('screen-developing');
    setTimeout(() => {
      img.classList.add('developed');
      setTimeout(() => {
        const r = $('#result-img');
        r.src = state.lastPhoto.dataUrl;
        r.style.filter = 'none';
        show('screen-result');
      }, 1200);
    }, 2600);
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
   * Shutter with timer
   * ------------------------------------------------------------------ */
  function shutterPressed() {
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
  async function renderGallery() {
    const grid = $('#gallery-grid');
    const photos = (await Photos.all().catch(() => [])).sort((a, b) => b.ts - a.ts);
    grid.innerHTML = '';
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
          for (const p of photos) await Photos.del(p.id);
          renderGallery();
        }
      });
      grid.appendChild(clear);
    }
  }

  function openPhoto(p) {
    $('#viewer-img').src = p.dataUrl;
    const viewer = $('#viewer');
    viewer.classList.add('show');
    $('#viewer-delete').onclick = async () => {
      await Photos.del(p.id);
      viewer.classList.remove('show');
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
    $('#viewer-close').onclick = () => viewer.classList.remove('show');
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
        if (!owned) { toast('Pehle ise shop se unlock karein'); openShop(); return; }
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
        if (cam.paid) {
          if (store.isPro()) { store.grant(cam.id); renderShop(); renderPicker(); toast('Unlocked'); }
          else { openPaywall(); }
        } else {
          store.grant(cam.id); renderShop(); renderPicker(); toast(cam.name + ' unlocked');
        }
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
    renderPicker();
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
      Effects.process(canvas, cam, store.settings());
      state.lastPhoto = { dataUrl: canvas.toDataURL('image/jpeg', 0.92), camId: cam.id, camName: cam.name };
      $('#result-img').src = state.lastPhoto.dataUrl;
      show('screen-result');
    };
    img.src = URL.createObjectURL(file);
  }

  /* ------------------------------------------------------------------ *
   * Wiring
   * ------------------------------------------------------------------ */
  function bind() {
    $('#btn-onboard').addEventListener('click', async () => {
      store.markSeen();
      await startCamera();
      show('screen-viewfinder');
    });

    $('#btn-shutter').addEventListener('click', shutterPressed);

    $('#btn-switch').addEventListener('click', async () => {
      state.facing = state.facing === 'environment' ? 'user' : 'environment';
      await startCamera();
      applyLiveLook();
    });

    $('#btn-flash').addEventListener('click', () => {
      state.flash = !state.flash;
      $('#btn-flash').classList.toggle('on', state.flash);
      toast(state.flash ? 'Flash on' : 'Flash off');
    });

    $('#btn-timer').addEventListener('click', () => {
      state.timer = state.timer === 0 ? 3 : (state.timer === 3 ? 10 : 0);
      $('#btn-timer').classList.toggle('on', state.timer > 0);
      $('#btn-timer').textContent = state.timer > 0 ? state.timer + 's' : '\u23F1';
    });

    $('#btn-double').addEventListener('click', () => {
      state.double = !state.double;
      state.firstShot = null;
      $('#btn-double').classList.toggle('on', state.double);
      toast(state.double ? 'Double exposure ON — 2 shots' : 'Double exposure off');
    });

    $('#btn-picker').addEventListener('click', () => { renderPicker(); openSheet('#sheet-picker'); });
    $('#btn-shop').addEventListener('click', openShop);
    $('#btn-gallery').addEventListener('click', async () => { await renderGallery(); show('screen-gallery'); });
    $('#btn-settings').addEventListener('click', () => { renderSettings(); show('screen-settings'); });
    $('#btn-paywall').addEventListener('click', openPaywall);
    $('#shop-pro-banner').addEventListener('click', openPaywall);

    $$('[data-back]').forEach(b => b.addEventListener('click', () => {
      const target = b.getAttribute('data-back');
      if (target === 'viewfinder' && !state.stream) startCamera();
      show('screen-' + target);
    }));

    $$('[data-close-sheet]').forEach(b => b.addEventListener('click', () => closeSheet('#' + b.getAttribute('data-close-sheet'))));
    $$('.sheet-backdrop').forEach(b => b.addEventListener('click', () => b.parentElement.classList.remove('show')));

    $('#result-retake').addEventListener('click', () => show('screen-viewfinder'));
    $('#result-save').addEventListener('click', savePhoto);
    $('#result-share').addEventListener('click', sharePhoto);

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
      if (!store.isPro()) { toast('Import PRO feature hai'); openPaywall(); return; }
      const f = e.target.files[0];
      if (f) importPhoto(f);
      e.target.value = '';
    });
    $('#btn-import').addEventListener('click', () => {
      if (!store.isPro()) { openPaywall(); return; }
      $('#import-input').click();
    });

    $('#fallback-pick').addEventListener('click', () => $('#import-input').click());

    document.addEventListener('visibilitychange', () => {
      if (document.hidden) stopCamera();
      else if (document.body.dataset.screen === 'screen-viewfinder') startCamera();
    });

    window.addEventListener('beforeunload', stopCamera);
  }

  /* ------------------------------------------------------------------ *
   * Boot
   * ------------------------------------------------------------------ */
  async function boot() {
    Photos.open().catch(() => {});
    const s = store.settings();
    state.cameraId = s.defaultCamera && CAMERAS.some(c => c.id === s.defaultCamera) ? s.defaultCamera : 'nomo_135_b';
    bind();
    applyLiveLook();
    show('screen-splash');
    setTimeout(() => {
      if (store.seen()) {
        startCamera().then(() => show('screen-viewfinder'));
      } else {
        show('screen-onboarding');
      }
    }, 1500);
  }

  if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', boot);
  else boot();
})();
