/* FaceCam — 3D intro animation.
 *
 * Sequence (about 3.2s, tap to skip):
 *   1. a dotted 3D globe spins up out of the dark
 *   2. a camera-shutter iris closes over it with a flash  (the "photo click")
 *   3. the iris opens and the FaceCam wordmark scales in with the tagline
 *
 * Pure canvas maths — no 3D library, no assets, no network.
 */
const Intro = (() => {
  let canvas = null, ctx = null, raf = null;
  let W = 0, H = 0, dpr = 1;
  let t0 = 0, running = false, finished = false;
  let onDone = null;
  const pts = [];

  const SPIN_END = 1250;
  const IRIS_END = 1600;
  const TOTAL = 3200;

  function buildPoints() {
    if (pts.length) return;
    for (let lat = -80; lat <= 80; lat += 9) {
      const rr = Math.cos(lat * Math.PI / 180);
      const y = Math.sin(lat * Math.PI / 180);
      const n = Math.max(6, Math.round(40 * rr));
      for (let i = 0; i < n; i++) {
        const lon = (i / n) * Math.PI * 2;
        pts.push([Math.cos(lon) * rr, y, Math.sin(lon) * rr]);
      }
    }
  }

  function resize() {
    dpr = Math.min(2, window.devicePixelRatio || 1);
    const r = canvas.getBoundingClientRect();
    W = Math.max(1, Math.round(r.width));
    H = Math.max(1, Math.round(r.height));
    canvas.width = Math.round(W * dpr);
    canvas.height = Math.round(H * dpr);
    ctx.setTransform(dpr, 0, 0, dpr, 0, 0);
  }

  function ease(t) { return t < 0 ? 0 : t > 1 ? 1 : t * t * (3 - 2 * t); }
  const clamp01 = (v) => (v < 0 ? 0 : v > 1 ? 1 : v);

  function draw(now) {
    const t = now - t0;
    ctx.clearRect(0, 0, W, H);

    const bg = ctx.createRadialGradient(W / 2, H * 0.44, 0, W / 2, H * 0.44, Math.max(W, H) * 0.75);
    bg.addColorStop(0, '#1b1226');
    bg.addColorStop(0.55, '#0c0a12');
    bg.addColorStop(1, '#06060a');
    ctx.fillStyle = bg;
    ctx.fillRect(0, 0, W, H);

    const spin = ease(clamp01(t / SPIN_END));
    const ang = spin * Math.PI * 1.35;
    const grow = 0.55 + 0.45 * ease(clamp01(t / 700));
    const R = Math.min(W, H) * 0.26 * grow;
    const cx = W / 2;
    const cy = H * 0.44;
    const fade = t < IRIS_END ? 1 : 1 - clamp01((t - IRIS_END) / 500);

    if (fade > 0.01) {
      const halo = ctx.createRadialGradient(cx, cy, R * 0.6, cx, cy, R * 1.9);
      halo.addColorStop(0, 'rgba(255,122,61,0.20)');
      halo.addColorStop(1, 'rgba(255,122,61,0)');
      ctx.fillStyle = halo;
      ctx.fillRect(0, 0, W, H);

      for (let i = 0; i < pts.length; i++) {
        const p = pts[i];
        const x = p[0] * Math.cos(ang) - p[2] * Math.sin(ang);
        const z = p[0] * Math.sin(ang) + p[2] * Math.cos(ang);
        const y = p[1];
        const persp = 1 / (1.9 - z * 0.55);
        const sx = cx + x * R * persp * 1.9;
        const sy = cy + y * R * persp * 1.9;
        const depth = (z + 1) / 2;
        ctx.globalAlpha = fade * (0.16 + 0.84 * depth);
        const size = 0.9 + 1.9 * depth;
        ctx.fillStyle = depth > 0.72 ? '#ffd9b0' : '#ff8a4d';
        ctx.beginPath();
        ctx.arc(sx, sy, size, 0, Math.PI * 2);
        ctx.fill();
      }
      ctx.globalAlpha = 1;
    }

    if (t > SPIN_END - 150) {
      const c = clamp01((t - (SPIN_END - 150)) / (IRIS_END - SPIN_END + 150));
      const close = c < 0.5 ? ease(c / 0.5) : 1 - ease((c - 0.5) / 0.5);
      const maxR = Math.hypot(W, H) * 0.62;
      const openR = maxR * (1 - close);
      if (close > 0.02) {
        ctx.save();
        ctx.translate(cx, cy);
        ctx.fillStyle = '#0a0a0e';
        for (let i = 0; i < 6; i++) {
          ctx.save();
          ctx.rotate((i / 6) * Math.PI * 2 + close * 0.5);
          ctx.beginPath();
          ctx.moveTo(0, 0);
          ctx.lineTo(openR, -openR * 0.62);
          ctx.lineTo(openR, openR * 0.62);
          ctx.closePath();
          ctx.fill();
          ctx.restore();
        }
        ctx.restore();
      }
      const ft = clamp01(1 - Math.abs(t - (IRIS_END - 120)) / 220);
      if (ft > 0) {
        ctx.fillStyle = 'rgba(255,255,255,' + (ft * 0.85).toFixed(3) + ')';
        ctx.fillRect(0, 0, W, H);
      }
    }

    const wt = clamp01((t - (IRIS_END + 180)) / 900);
    if (wt > 0) {
      const e = ease(wt);
      ctx.save();
      ctx.translate(W / 2, H * 0.62);
      ctx.scale(0.82 + 0.18 * e, 0.82 + 0.18 * e);
      ctx.globalAlpha = e;
      const size = Math.min(W * 0.17, 62);
      ctx.font = '800 ' + size + 'px -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, Arial, sans-serif';
      ctx.textAlign = 'center';
      ctx.textBaseline = 'middle';
      const grad = ctx.createLinearGradient(-W * 0.3, 0, W * 0.3, 0);
      grad.addColorStop(0, '#ffb26b');
      grad.addColorStop(1, '#ff4d8d');
      ctx.fillStyle = grad;
      ctx.fillText('FaceCam', 0, 0);

      const tag = clamp01((t - (IRIS_END + 700)) / 700);
      if (tag > 0) {
        ctx.globalAlpha = ease(tag) * 0.85;
        ctx.font = '600 ' + Math.round(size * 0.26) + 'px -apple-system, "Segoe UI", Roboto, Arial, sans-serif';
        ctx.fillStyle = '#8d8a85';
        ctx.fillText('VINTAGE  \u00B7  B&W  \u00B7  BEAUTY  \u00B7  VIDEO', 0, size * 1.05);
      }
      ctx.restore();
    }

    if (t >= TOTAL && !finished) {
      finished = true;
      running = false;
      if (onDone) onDone();
      return;
    }
    if (running) raf = requestAnimationFrame(loop);
  }

  function loop(now) { draw(now); }

  function start(done) {
    canvas = document.getElementById('intro-canvas');
    if (!canvas) { if (done) done(); return; }
    ctx = canvas.getContext('2d');
    buildPoints();
    resize();
    onDone = done;
    finished = false;
    running = true;
    t0 = performance.now();
    draw(t0);
    raf = requestAnimationFrame(loop);

    canvas.addEventListener('click', skip, { once: true });
    canvas.addEventListener('touchstart', skip, { once: true });
    // safety: never let a throttled/paused tab trap the user on the intro
    setTimeout(skip, 6000);
  }

  function skip() {
    if (finished) return;
    finished = true;
    running = false;
    if (raf) cancelAnimationFrame(raf);
    if (onDone) onDone();
  }

  function stop() {
    running = false;
    if (raf) cancelAnimationFrame(raf);
    raf = null;
  }

  return { start, skip, stop };
})();
