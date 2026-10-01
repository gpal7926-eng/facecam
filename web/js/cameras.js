/* FaceCam — film simulation presets.
 *
 * `filter`   CSS filter string — used for the live viewfinder preview and as the
 *            base colour curve at capture time.
 * `tone`     per-channel film tone curve applied per pixel (the realistic part):
 *              lift  -0.1..0.12  raises/lowers the black point (faded vs deep blacks)
 *              gamma  0.8..1.25  mid-tone contrast
 *              gain   0.9..1.1   overall brightness
 *              warmth -0.15..0.15 red/blue split (positive = warmer)
 *              sat    0.8..1.4   saturation
 * `halation` 0..1  glow that bleeds around bright highlights (very filmic)
 * `chroma`   0..1  colour noise, on top of the luminance grain
 * `grain` `dust` `vignette` `leak`  0..1  the randomised analog effects
 */
const CAMERAS = [
  {
    id: 'nomo_135_b', name: 'FaceCam 135 B', tag: '35mm Classic',
    desc: 'Balanced classic 35mm. Soft contrast, warm highlights.',
    filter: 'contrast(1.06) saturate(1.08) sepia(0.10) brightness(1.02)',
    tone: { lift: 0.015, gamma: 1.02, gain: 1.02, warmth: 0.035, sat: 1.08 },
    halation: 0.35, chroma: 0.18,
    grain: 0.28, dust: 0.18, vignette: 0.34, leak: 0.22,
    leakColors: ['#ffb347', '#ff7043'],
    frame: { style: 'thin', color: '#f6efe1' }, dateStamp: false,
    paid: false, price: 0
  },
  {
    id: 'nomo_135_m', name: 'FaceCam 135 M', tag: '35mm Moody',
    desc: 'Cool, contrasty, monochrome-leaning look with deep shadows.',
    filter: 'contrast(1.18) saturate(0.72) brightness(0.98) hue-rotate(-8deg)',
    tone: { lift: -0.035, gamma: 1.14, gain: 0.99, warmth: -0.05, sat: 0.82 },
    halation: 0.22, chroma: 0.10,
    grain: 0.42, dust: 0.24, vignette: 0.48, leak: 0.14,
    leakColors: ['#9fd3ff', '#6b8cff'],
    frame: { style: 'thin', color: '#e9e4d8' }, dateStamp: false,
    paid: false, price: 0
  },
  {
    id: 'nomo_135_p', name: 'FaceCam 135 P', tag: '35mm Punchy',
    desc: 'Vivid, high-saturation film. Great for double exposures.',
    filter: 'contrast(1.12) saturate(1.35) brightness(1.03)',
    tone: { lift: 0.005, gamma: 1.06, gain: 1.02, warmth: 0.02, sat: 1.32 },
    halation: 0.30, chroma: 0.22,
    grain: 0.32, dust: 0.20, vignette: 0.30, leak: 0.30,
    leakColors: ['#ff5f6d', '#ffc371'],
    frame: { style: 'thin', color: '#fff7ea' }, dateStamp: false,
    paid: false, price: 0
  },
  {
    id: 'toy_f', name: 'FaceCam TOY F', tag: 'Toy Camera',
    desc: 'Cheap plastic-lens toy look. Heavy vignette, punchy colours.',
    filter: 'contrast(1.35) saturate(1.6) brightness(1.05)',
    tone: { lift: -0.01, gamma: 1.18, gain: 1.03, warmth: 0.05, sat: 1.55 },
    halation: 0.55, chroma: 0.40,
    grain: 0.55, dust: 0.35, vignette: 0.78, leak: 0.45,
    leakColors: ['#ff3d6e', '#ffd166'],
    frame: { style: 'thick', color: '#ffffff' }, dateStamp: false,
    paid: false, price: 0
  },
  {
    id: 'toy_k', name: 'FaceCam TOY K', tag: 'Toy Camera',
    desc: 'Softer toy rendering with a cyan shift and strong falloff.',
    filter: 'contrast(1.22) saturate(1.3) hue-rotate(12deg) brightness(1.04)',
    tone: { lift: -0.005, gamma: 1.12, gain: 1.03, warmth: -0.04, sat: 1.28 },
    halation: 0.50, chroma: 0.34,
    grain: 0.50, dust: 0.32, vignette: 0.70, leak: 0.38,
    leakColors: ['#3ddcff', '#5b7bff'],
    frame: { style: 'thick', color: '#ffffff' }, dateStamp: false,
    paid: false, price: 0
  },
  {
    id: 'roma', name: 'FaceCam ROMA', tag: 'Italian Summer',
    desc: 'Golden Mediterranean warmth. Faded blacks, sun-bleached colour.',
    filter: 'sepia(0.35) saturate(1.15) contrast(0.95) brightness(1.06)',
    tone: { lift: 0.055, gamma: 0.97, gain: 1.05, warmth: 0.09, sat: 1.10 },
    halation: 0.60, chroma: 0.20,
    grain: 0.30, dust: 0.22, vignette: 0.32, leak: 0.50,
    leakColors: ['#ffd27f', '#ff9a3c'],
    frame: { style: 'thin', color: '#fbf3e3' }, dateStamp: true,
    paid: false, price: 0
  },
  {
    id: 'fr2', name: 'FaceCam FR2', tag: 'French New Wave',
    desc: 'Cinematic teal-and-orange with gentle halation.',
    filter: 'contrast(1.14) saturate(1.05) hue-rotate(-14deg) brightness(0.99)',
    tone: { lift: 0.02, gamma: 1.08, gain: 1.00, warmth: 0.045, sat: 1.02 },
    halation: 0.65, chroma: 0.16,
    grain: 0.36, dust: 0.18, vignette: 0.40, leak: 0.28,
    leakColors: ['#ff8a5c', '#ffd08a'],
    frame: { style: 'cinema', color: '#0d0d0d' }, dateStamp: false,
    paid: true, price: 99
  },
  {
    id: 'film_2007', name: 'FaceCam 2007', tag: 'Digicam Nostalgia',
    desc: 'Early-2000s compact camera: harsh flash, oversharpened, magenta.',
    filter: 'contrast(1.25) saturate(1.25) hue-rotate(6deg) brightness(1.05)',
    tone: { lift: -0.02, gamma: 1.10, gain: 1.05, warmth: 0.055, sat: 1.22 },
    halation: 0.45, chroma: 0.55,
    grain: 0.60, dust: 0.30, vignette: 0.44, leak: 0.20,
    leakColors: ['#ff77c8', '#ffd0f0'],
    frame: { style: 'thin', color: '#ffffff' }, dateStamp: true,
    paid: false, price: 0
  },
  {
    id: 'eats', name: 'FaceCam EATS', tag: 'Food Camera',
    desc: 'Tuned for food: warm, appetising, boosted mid-tones.',
    filter: 'contrast(1.10) saturate(1.45) sepia(0.12) brightness(1.08)',
    tone: { lift: 0.02, gamma: 0.98, gain: 1.07, warmth: 0.075, sat: 1.40 },
    halation: 0.30, chroma: 0.14,
    grain: 0.22, dust: 0.12, vignette: 0.26, leak: 0.30,
    leakColors: ['#ffcf5c', '#ff9f43'],
    frame: { style: 'thin', color: '#fff6e6' }, dateStamp: false,
    paid: true, price: 99
  },
  {
    id: 'ins_2', name: 'FaceCam INS 2', tag: 'Instant / Polaroid',
    desc: 'Instant film with a thick white border and a developing wait.',
    filter: 'contrast(0.94) saturate(0.92) sepia(0.20) brightness(1.10)',
    tone: { lift: 0.075, gamma: 0.92, gain: 1.08, warmth: 0.05, sat: 0.95 },
    halation: 0.40, chroma: 0.18,
    grain: 0.34, dust: 0.26, vignette: 0.20, leak: 0.55,
    leakColors: ['#ffe6a7', '#ffb26b'],
    frame: { style: 'instant', color: '#ffffff' }, dateStamp: false,
    instant: true,
    paid: false, price: 0
  },
  {
    id: 'swirly_2', name: 'FaceCam SWIRLY 2', tag: 'Swirly Bokeh',
    desc: 'Petzval-style swirl. Soft corners, dreamy centre.',
    filter: 'contrast(1.05) saturate(1.10) blur(0.4px) brightness(1.04)',
    tone: { lift: 0.03, gamma: 1.00, gain: 1.04, warmth: 0.02, sat: 1.10 },
    halation: 0.75, chroma: 0.20,
    grain: 0.30, dust: 0.16, vignette: 0.62, leak: 0.34,
    leakColors: ['#c9a7ff', '#7fd8ff'],
    frame: { style: 'thin', color: '#f2ecff' }, dateStamp: false,
    paid: true, price: 149
  },
  {
    id: 'range_67', name: 'FaceCam Range 67', tag: 'Rangefinder 6x7',
    desc: 'Medium-format rangefinder. Rich, dense, professional colour.',
    filter: 'contrast(1.16) saturate(1.12) brightness(0.99) sepia(0.06)',
    tone: { lift: -0.025, gamma: 1.10, gain: 1.01, warmth: 0.03, sat: 1.12 },
    halation: 0.28, chroma: 0.12,
    grain: 0.26, dust: 0.14, vignette: 0.30, leak: 0.18,
    leakColors: ['#ffc9a0', '#ff8f6b'],
    frame: { style: 'cinema', color: '#141414' }, dateStamp: true,
    paid: true, price: 199
  },
  {
    id: 'wide_17', name: 'FaceCam Wide 17', tag: 'Ultra Wide',
    desc: 'Wide-lens look with lifted blacks and a cool cast.',
    filter: 'contrast(1.08) saturate(0.95) hue-rotate(-6deg) brightness(1.02)',
    tone: { lift: 0.04, gamma: 1.04, gain: 1.02, warmth: -0.045, sat: 0.98 },
    halation: 0.35, chroma: 0.16,
    grain: 0.30, dust: 0.18, vignette: 0.42, leak: 0.26,
    leakColors: ['#8fd8ff', '#c6f0ff'],
    frame: { style: 'thin', color: '#eaf6ff' }, dateStamp: false,
    paid: false, price: 0
  }
];

if (typeof module !== 'undefined') { module.exports = { CAMERAS }; }
