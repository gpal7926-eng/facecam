/* FaceCam — film simulation presets.
 * Each "camera" defines a distinct vintage look. `filter` is a CSS filter string
 * used for the live viewfinder preview and the canvas colour curve. The numeric
 * fields (0..1) drive the randomised analog effects applied on capture.
 */
const CAMERAS = [
  {
    id: 'nomo_135_b', name: 'FaceCam 135 B', tag: '35mm Classic',
    desc: 'Balanced classic 35mm. Soft contrast, warm highlights.',
    filter: 'contrast(1.06) saturate(1.08) sepia(0.10) brightness(1.02)',
    grain: 0.28, dust: 0.18, vignette: 0.34, leak: 0.22,
    leakColors: ['#ffb347', '#ff7043'],
    frame: { style: 'thin', color: '#f6efe1' }, dateStamp: false,
    paid: false, price: 0
  },
  {
    id: 'nomo_135_m', name: 'FaceCam 135 M', tag: '35mm Moody',
    desc: 'Cool, contrasty monochrome-leaning look with deep shadows.',
    filter: 'contrast(1.18) saturate(0.72) brightness(0.98) hue-rotate(-8deg)',
    grain: 0.42, dust: 0.24, vignette: 0.48, leak: 0.14,
    leakColors: ['#9fd3ff', '#6b8cff'],
    frame: { style: 'thin', color: '#e9e4d8' }, dateStamp: false,
    paid: false, price: 0
  },
  {
    id: 'nomo_135_p', name: 'FaceCam 135 P', tag: '35mm Punchy',
    desc: 'Vivid, high-saturation film. Great for double exposures.',
    filter: 'contrast(1.12) saturate(1.35) brightness(1.03)',
    grain: 0.32, dust: 0.20, vignette: 0.30, leak: 0.30,
    leakColors: ['#ff5f6d', '#ffc371'],
    frame: { style: 'thin', color: '#fff7ea' }, dateStamp: false,
    paid: false, price: 0
  },
  {
    id: 'toy_f', name: 'FaceCam TOY F', tag: 'Toy Camera',
    desc: 'Cheap plastic-lens toy look. Heavy vignette, punchy colours.',
    filter: 'contrast(1.35) saturate(1.6) brightness(1.05)',
    grain: 0.55, dust: 0.35, vignette: 0.78, leak: 0.45,
    leakColors: ['#ff3d6e', '#ffd166'],
    frame: { style: 'thick', color: '#ffffff' }, dateStamp: false,
    paid: false, price: 0
  },
  {
    id: 'toy_k', name: 'FaceCam TOY K', tag: 'Toy Camera',
    desc: 'Softer toy rendering with a cyan shift and strong falloff.',
    filter: 'contrast(1.22) saturate(1.3) hue-rotate(12deg) brightness(1.04)',
    grain: 0.50, dust: 0.32, vignette: 0.70, leak: 0.38,
    leakColors: ['#3ddcff', '#5b7bff'],
    frame: { style: 'thick', color: '#ffffff' }, dateStamp: false,
    paid: false, price: 0
  },
  {
    id: 'roma', name: 'FaceCam ROMA', tag: 'Italian Summer',
    desc: 'Golden Mediterranean warmth. Faded blacks, sun-bleached colour.',
    filter: 'sepia(0.35) saturate(1.15) contrast(0.95) brightness(1.06)',
    grain: 0.30, dust: 0.22, vignette: 0.32, leak: 0.50,
    leakColors: ['#ffd27f', '#ff9a3c'],
    frame: { style: 'thin', color: '#fbf3e3' }, dateStamp: true,
    paid: false, price: 0
  },
  {
    id: 'fr2', name: 'FaceCam FR2', tag: 'French New Wave',
    desc: 'Cinematic teal-and-orange with gentle halation.',
    filter: 'contrast(1.14) saturate(1.05) hue-rotate(-14deg) brightness(0.99)',
    grain: 0.36, dust: 0.18, vignette: 0.40, leak: 0.28,
    leakColors: ['#ff8a5c', '#ffd08a'],
    frame: { style: 'cinema', color: '#0d0d0d' }, dateStamp: false,
    paid: true, price: 99
  },
  {
    id: 'film_2007', name: 'FaceCam 2007', tag: 'Digicam Nostalgia',
    desc: 'Early-2000s compact camera: harsh flash, oversharpened, magenta.',
    filter: 'contrast(1.25) saturate(1.25) hue-rotate(6deg) brightness(1.05)',
    grain: 0.60, dust: 0.30, vignette: 0.44, leak: 0.20,
    leakColors: ['#ff77c8', '#ffd0f0'],
    frame: { style: 'thin', color: '#ffffff' }, dateStamp: true,
    paid: false, price: 0
  },
  {
    id: 'eats', name: 'FaceCam EATS', tag: 'Food Camera',
    desc: 'Tuned for food: warm, appetising, boosted mid-tones.',
    filter: 'contrast(1.10) saturate(1.45) sepia(0.12) brightness(1.08)',
    grain: 0.22, dust: 0.12, vignette: 0.26, leak: 0.30,
    leakColors: ['#ffcf5c', '#ff9f43'],
    frame: { style: 'thin', color: '#fff6e6' }, dateStamp: false,
    paid: true, price: 99
  },
  {
    id: 'ins_2', name: 'FaceCam INS 2', tag: 'Instant / Polaroid',
    desc: 'Instant film with a thick white border and a developing wait.',
    filter: 'contrast(0.94) saturate(0.92) sepia(0.20) brightness(1.10)',
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
    grain: 0.30, dust: 0.16, vignette: 0.62, leak: 0.34,
    leakColors: ['#c9a7ff', '#7fd8ff'],
    frame: { style: 'thin', color: '#f2ecff' }, dateStamp: false,
    paid: true, price: 149
  },
  {
    id: 'range_67', name: 'FaceCam Range 67', tag: 'Rangefinder 6x7',
    desc: 'Medium-format rangefinder. Rich, dense, professional colour.',
    filter: 'contrast(1.16) saturate(1.12) brightness(0.99) sepia(0.06)',
    grain: 0.26, dust: 0.14, vignette: 0.30, leak: 0.18,
    leakColors: ['#ffc9a0', '#ff8f6b'],
    frame: { style: 'cinema', color: '#141414' }, dateStamp: true,
    paid: true, price: 199
  },
  {
    id: 'wide_17', name: 'FaceCam Wide 17', tag: 'Ultra Wide',
    desc: 'Wide-lens look with lifted blacks and a cool cast.',
    filter: 'contrast(1.08) saturate(0.95) hue-rotate(-6deg) brightness(1.02)',
    grain: 0.30, dust: 0.18, vignette: 0.42, leak: 0.26,
    leakColors: ['#8fd8ff', '#c6f0ff'],
    frame: { style: 'thin', color: '#eaf6ff' }, dateStamp: false,
    paid: false, price: 0
  }
];

if (typeof module !== 'undefined') { module.exports = { CAMERAS }; }
