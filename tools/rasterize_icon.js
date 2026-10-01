/* Rasterise tools/facecam_foreground.svg into Android icon PNGs at every density,
 * plus the Play-store master and the web icon. Android cannot use SVG in res/.
 *
 * Usage:  node tools/rasterize_icon.js
 * Needs a local Chromium (puppeteer-core).  Set CHROME=/path/to/chromium to override.
 */
const puppeteer = require('puppeteer-core');
const fs = require('fs');
const path = require('path');

const ROOT = path.dirname(path.dirname(path.abspath(__file__)));
const SRC = path.join(ROOT, 'tools', 'facecam_foreground.svg');
const RES = path.join(ROOT, 'android', 'app', 'src', 'main', 'res');
const CHROME = process.env.CHROME || '/usr/local/bin/chromium';

const svg = fs.readFileSync(SRC, 'utf8');

// Content-only variant for the adaptive foreground: drop the two background
// rounded-rects (the adaptive background layer supplies the backdrop) and crop
// to the artwork bounds.
let content = svg
  .replace(/<!-- Background -->[\s\S]*?filter="url\(#soft\)"\/>/, '')
  .replace(/<!-- subtle rim glow -->[\s\S]*?filter="url\(#glowBlue\)"\/>/, '')
  .replace('viewBox="0 0 1024 1024"', 'viewBox="210 130 620 800"')
  .replace('width="1024" height="1024"', 'width="620" height="800"');

const DENSITIES = [
  { name: 'mdpi', legacy: 48, fg: 108 },
  { name: 'hdpi', legacy: 72, fg: 162 },
  { name: 'xhdpi', legacy: 96, fg: 216 },
  { name: 'xxhdpi', legacy: 144, fg: 324 },
  { name: 'xxxhdpi', legacy: 192, fg: 432 },
];

(async () => {
  const browser = await puppeteer.launch({
    executablePath: CHROME, headless: 'new',
    args: ['--no-sandbox', '--disable-setuid-sandbox', '--disable-gpu'],
  });
  const page = await browser.newPage();
  await page.setContent('<html><body></body></html>');

  const results = await page.evaluate(async (svg, content, densities) => {
    function draw(svgString, size, opt) {
      return new Promise((resolve) => {
        const url = URL.createObjectURL(new Blob([svgString], { type: 'image/svg+xml' }));
        const img = new Image();
        img.onload = () => {
          const c = document.createElement('canvas');
          c.width = size; c.height = size;
          const g = c.getContext('2d');
          g.imageSmoothingEnabled = true; g.imageSmoothingQuality = 'high';
          if (opt.round) { g.save(); g.beginPath(); g.arc(size / 2, size / 2, size / 2, 0, 6.2832); g.clip(); }
          if (opt.fit) {
            const s = size * opt.scale;
            const w = s * opt.fit[0], h = s * opt.fit[1];
            g.drawImage(img, (size - w) / 2, (size - h) / 2, w, h);
          } else {
            g.drawImage(img, 0, 0, size, size);
          }
          if (opt.round) g.restore();
          URL.revokeObjectURL(url);
          resolve(c.toDataURL('image/png'));
        };
        img.onerror = () => resolve('');
        img.src = url;
      });
    }
    const out = { legacy: {}, round: {}, fg: {}, play: '', web: '' };
    for (const d of densities) {
      out.legacy[d.name] = await draw(svg, d.legacy, {});
      out.round[d.name] = await draw(svg, d.legacy, { round: true });
      out.fg[d.name] = await draw(content, d.fg, { scale: 1, fit: [620 / 800, 1] });
    }
    out.play = await draw(svg, 512, {});
    out.web = await draw(svg, 256, {});
    return out;
  }, svg, content, DENSITIES);

  function save(dataUrl, file) {
    fs.mkdirSync(path.dirname(file), { recursive: true });
    fs.writeFileSync(file, Buffer.from(dataUrl.split(',')[1], 'base64'));
  }

  for (const d of DENSITIES) {
    const dir = path.join(RES, 'mipmap-' + d.name);
    save(results.legacy[d.name], path.join(dir, 'ic_launcher.png'));
    save(results.round[d.name], path.join(dir, 'ic_launcher_round.png'));
    save(results.fg[d.name], path.join(dir, 'ic_launcher_foreground.png'));
  }
  save(results.play, path.join(ROOT, 'docs', 'icon-512.png'));
  save(results.web, path.join(ROOT, 'web', 'icon-256.png'));
  console.log('rasterised icon: 5 densities + docs/icon-512.png + web/icon-256.png');
  await browser.close();
})().catch(e => { console.error('FATAL', e); process.exit(1); });
