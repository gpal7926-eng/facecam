/* FaceCam — camera presets.
 *
 * Two families:
 *   group: 'vintage'  film simulations — grain, leaks, vignette, frames.
 *                     Includes the decade looks (1950s…1990s) and B&W.
 *   group: 'beauty'   clean, iPhone-like enhance — smooth, sharp, natural.
 *
 * Vintage presets use: filter, tone, halation, chroma, grain, dust, vignette,
 *                      leak, frame, dateStamp
 * Beauty presets use:  filter, beauty { exposure, contrast, sat, warmth,
 *                      smooth, sharpen, glow }
 */
const CAMERAS = [
  /* ------------------------------------------------------------------ *
   * BEAUTY — clean, natural, iPhone-like
   * ------------------------------------------------------------------ */
  {
    id: 'beauty_natural', name: 'Natural', tag: 'Clean & true', group: 'beauty',
    desc: 'Balanced, true-to-life enhance. Soft skin, crisp detail, no colour shift.',
    filter: 'contrast(1.04) saturate(1.06) brightness(1.04)',
    beauty: { exposure: 0.045, contrast: 1.07, sat: 1.08, warmth: 0.015, smooth: 0.86, sharpen: 0.75, glow: 0.30 },
    paid: false, price: 0
  },
  {
    id: 'beauty_bright', name: 'Bright', tag: 'Airy daylight', group: 'beauty',
    desc: 'Lifted, airy exposure with soft highlights. Great indoors and in shade.',
    filter: 'contrast(1.02) saturate(1.04) brightness(1.10)',
    beauty: { exposure: 0.105, contrast: 1.03, sat: 1.05, warmth: 0.005, smooth: 0.90, sharpen: 0.60, glow: 0.55 },
    paid: false, price: 0
  },
  {
    id: 'beauty_warm', name: 'Warm', tag: 'Golden skin', group: 'beauty',
    desc: 'Golden, flattering warmth tuned for skin tones.',
    filter: 'sepia(0.10) saturate(1.12) brightness(1.05)',
    beauty: { exposure: 0.055, contrast: 1.08, sat: 1.20, warmth: 0.105, smooth: 0.88, sharpen: 0.72, glow: 0.42 },
    paid: false, price: 0
  },
  {
    id: 'beauty_portrait', name: 'Portrait', tag: 'Soft & focused', group: 'beauty',
    desc: 'Stronger smoothing with crisp centre detail — a portrait-mode feel.',
    filter: 'contrast(1.08) saturate(1.05) brightness(1.04)',
    beauty: { exposure: 0.050, contrast: 1.10, sat: 1.06, warmth: 0.030, smooth: 1.00, sharpen: 1.00, glow: 0.48 },
    paid: false, price: 0
  },
  {
    id: 'beauty_soft', name: 'Soft Glam', tag: 'Dreamy skin', group: 'beauty',
    desc: 'Heavy, silky skin smoothing with a strong soft-glow — a glamour look.',
    filter: 'contrast(1.03) saturate(1.06) brightness(1.07)',
    beauty: { exposure: 0.075, contrast: 1.05, sat: 1.09, warmth: 0.060, smooth: 1.15, sharpen: 0.55, glow: 0.72 },
    paid: false, price: 0
  },
  {
    id: 'beauty_vivid', name: 'Vivid Pop', tag: 'Punchy colour', group: 'beauty',
    desc: 'Bright, saturated and crisp — colours that jump off the screen.',
    filter: 'contrast(1.12) saturate(1.26) brightness(1.05)',
    beauty: { exposure: 0.050, contrast: 1.16, sat: 1.32, warmth: 0.020, smooth: 0.80, sharpen: 0.98, glow: 0.34 },
    paid: false, price: 0
  },
  {
    id: 'beauty_cool', name: 'Cool Clean', tag: 'Crisp & cool', group: 'beauty',
    desc: 'A cool white balance with clean, sharp detail. Modern and airy.',
    filter: 'contrast(1.06) saturate(1.02) brightness(1.06) hue-rotate(-3deg)',
    beauty: { exposure: 0.080, contrast: 1.09, sat: 1.00, warmth: -0.050, smooth: 0.85, sharpen: 0.82, glow: 0.34 },
    paid: false, price: 0
  },
  {
    id: 'beauty_golden', name: 'Golden Hour', tag: 'Sunset glow', group: 'beauty',
    desc: 'Warm golden light with a rich highlight glow — the hour before dusk.',
    filter: 'sepia(0.12) saturate(1.13) brightness(1.06)',
    beauty: { exposure: 0.090, contrast: 1.05, sat: 1.15, warmth: 0.120, smooth: 0.95, sharpen: 0.70, glow: 0.78 },
    paid: false, price: 0
  },
  {
    id: 'beauty_mono', name: 'Mono Beauty', tag: 'Clean B&W', group: 'beauty', bw: true,
    desc: 'A clean black-and-white beauty look: smooth skin, crisp detail, no colour.',
    filter: 'grayscale(1) contrast(1.08) brightness(1.06)',
    beauty: { exposure: 0.080, contrast: 1.12, sat: 0.0, warmth: 0.0, smooth: 0.95, sharpen: 0.86, glow: 0.46 },
    paid: false, price: 0
  },

  /* ------------------------------------------------------------------ *
   * VINTAGE — film simulations
   * ------------------------------------------------------------------ */
  {
    id: 'nomo_135_b', name: 'FaceCam 135 B', tag: '35mm Classic', group: 'vintage',
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
    id: 'nomo_135_m', name: 'FaceCam 135 M', tag: '35mm Moody', group: 'vintage',
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
    id: 'nomo_135_p', name: 'FaceCam 135 P', tag: '35mm Punchy', group: 'vintage',
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
    id: 'toy_f', name: 'FaceCam TOY F', tag: 'Toy Camera', group: 'vintage',
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
    id: 'toy_k', name: 'FaceCam TOY K', tag: 'Toy Camera', group: 'vintage',
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
    id: 'roma', name: 'FaceCam ROMA', tag: 'Italian Summer', group: 'vintage',
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
    id: 'fr2', name: 'FaceCam FR2', tag: 'French New Wave', group: 'vintage',
    desc: 'Cinematic teal-and-orange with gentle halation.',
    filter: 'contrast(1.14) saturate(1.05) hue-rotate(-14deg) brightness(0.99)',
    tone: { lift: 0.02, gamma: 1.08, gain: 1.00, warmth: 0.045, sat: 1.02 },
    halation: 0.65, chroma: 0.16,
    grain: 0.36, dust: 0.18, vignette: 0.40, leak: 0.28,
    leakColors: ['#ff8a5c', '#ffd08a'],
    frame: { style: 'cinema', color: '#0d0d0d' }, dateStamp: false,
    paid: false, price: 0
  },
  {
    id: 'film_2007', name: 'FaceCam 2007', tag: 'Digicam Nostalgia', group: 'vintage',
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
    id: 'eats', name: 'FaceCam EATS', tag: 'Food Camera', group: 'vintage',
    desc: 'Tuned for food: warm, appetising, boosted mid-tones.',
    filter: 'contrast(1.10) saturate(1.45) sepia(0.12) brightness(1.08)',
    tone: { lift: 0.02, gamma: 0.98, gain: 1.07, warmth: 0.075, sat: 1.40 },
    halation: 0.30, chroma: 0.14,
    grain: 0.22, dust: 0.12, vignette: 0.26, leak: 0.30,
    leakColors: ['#ffcf5c', '#ff9f43'],
    frame: { style: 'thin', color: '#fff6e6' }, dateStamp: false,
    paid: false, price: 0
  },
  {
    id: 'ins_2', name: 'FaceCam INS 2', tag: 'Instant / Polaroid', group: 'vintage',
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
    id: 'swirly_2', name: 'FaceCam SWIRLY 2', tag: 'Swirly Bokeh', group: 'vintage',
    desc: 'Petzval-style swirl. Soft corners, dreamy centre.',
    filter: 'contrast(1.05) saturate(1.10) blur(0.4px) brightness(1.04)',
    tone: { lift: 0.03, gamma: 1.00, gain: 1.04, warmth: 0.02, sat: 1.10 },
    halation: 0.75, chroma: 0.20,
    grain: 0.30, dust: 0.16, vignette: 0.62, leak: 0.34,
    leakColors: ['#c9a7ff', '#7fd8ff'],
    frame: { style: 'thin', color: '#f2ecff' }, dateStamp: false,
    paid: false, price: 0
  },
  {
    id: 'range_67', name: 'FaceCam Range 67', tag: 'Rangefinder 6x7', group: 'vintage',
    desc: 'Medium-format rangefinder. Rich, dense, professional colour.',
    filter: 'contrast(1.16) saturate(1.12) brightness(0.99) sepia(0.06)',
    tone: { lift: -0.025, gamma: 1.10, gain: 1.01, warmth: 0.03, sat: 1.12 },
    halation: 0.28, chroma: 0.12,
    grain: 0.26, dust: 0.14, vignette: 0.30, leak: 0.18,
    leakColors: ['#ffc9a0', '#ff8f6b'],
    frame: { style: 'cinema', color: '#141414' }, dateStamp: true,
    paid: false, price: 0
  },
  {
    id: 'wide_17', name: 'FaceCam Wide 17', tag: 'Ultra Wide', group: 'vintage',
    desc: 'Wide-lens look with lifted blacks and a cool cast.',
    filter: 'contrast(1.08) saturate(0.95) hue-rotate(-6deg) brightness(1.02)',
    tone: { lift: 0.04, gamma: 1.04, gain: 1.02, warmth: -0.045, sat: 0.98 },
    halation: 0.35, chroma: 0.16,
    grain: 0.30, dust: 0.18, vignette: 0.42, leak: 0.26,
    leakColors: ['#8fd8ff', '#c6f0ff'],
    frame: { style: 'thin', color: '#eaf6ff' }, dateStamp: false,
    paid: false, price: 0
  },

  /* ------------------------------------------------------------------ *
   * VINTAGE — DECADE LOOKS  (50s · 60s · 70s · 80s · 90s)
   * ------------------------------------------------------------------ */
  {
    id: 'decade_50s', name: '1950s', tag: 'Sepia Cinema', group: 'vintage', era: '50s',
    desc: 'Soft, faded sepia with deep corners — a hand-tinted 1950s print.',
    filter: 'sepia(0.55) contrast(0.92) saturate(0.82) brightness(1.04)',
    tone: { lift: 0.06, gamma: 0.94, gain: 1.03, warmth: 0.10, sat: 0.58 },
    halation: 0.50, chroma: 0.10,
    grain: 0.50, dust: 0.30, vignette: 0.56, leak: 0.34,
    leakColors: ['#f3c98b', '#d9a05b'],
    frame: { style: 'thin', color: '#efe3c8' }, dateStamp: false,
    paid: false, price: 0
  },
  {
    id: 'decade_60s', name: '1960s', tag: 'Kodachrome', group: 'vintage', era: '60s',
    desc: 'Vivid, saturated Kodachrome warmth with a gentle vintage fade.',
    filter: 'saturate(1.26) contrast(1.06) sepia(0.14) brightness(1.03)',
    tone: { lift: 0.03, gamma: 1.02, gain: 1.03, warmth: 0.06, sat: 1.26 },
    halation: 0.45, chroma: 0.20,
    grain: 0.34, dust: 0.20, vignette: 0.36, leak: 0.30,
    leakColors: ['#ffcf7a', '#ff9d5c'],
    frame: { style: 'thin', color: '#f7edda' }, dateStamp: true,
    paid: false, price: 0
  },
  {
    id: 'decade_70s', name: '1970s', tag: 'Warm Fade', group: 'vintage', era: '70s',
    desc: 'Warm brown-orange cast, lifted blacks and heavy grain. Pure 70s.',
    filter: 'sepia(0.32) saturate(1.10) contrast(0.94) brightness(1.05)',
    tone: { lift: 0.075, gamma: 0.95, gain: 1.04, warmth: 0.12, sat: 1.06 },
    halation: 0.60, chroma: 0.28,
    grain: 0.56, dust: 0.36, vignette: 0.42, leak: 0.50,
    leakColors: ['#ff9a3c', '#ffcf6b'],
    frame: { style: 'thin', color: '#f2e2c4' }, dateStamp: true,
    paid: false, price: 0
  },
  {
    id: 'decade_80s', name: '1980s', tag: 'Neon VHS', group: 'vintage', era: '80s',
    desc: 'Magenta-and-cyan neon with a VHS glow and blotchy chroma noise.',
    filter: 'contrast(1.14) saturate(1.35) hue-rotate(-10deg) brightness(1.04)',
    tone: { lift: 0.0, gamma: 1.08, gain: 1.04, warmth: 0.04, sat: 1.30 },
    halation: 0.70, chroma: 0.55,
    grain: 0.50, dust: 0.22, vignette: 0.38, leak: 0.40,
    leakColors: ['#ff4fd8', '#4fd8ff'],
    frame: { style: 'thin', color: '#ffffff' }, dateStamp: true,
    paid: false, price: 0
  },
  {
    id: 'decade_90s', name: '1990s', tag: 'Point & Shoot', group: 'vintage', era: '90s',
    desc: 'Cool, contrasty compact-camera colour with a slight modern fade.',
    filter: 'contrast(1.12) saturate(1.05) hue-rotate(-4deg) brightness(1.00)',
    tone: { lift: -0.01, gamma: 1.08, gain: 1.00, warmth: -0.03, sat: 1.02 },
    halation: 0.30, chroma: 0.18,
    grain: 0.34, dust: 0.18, vignette: 0.34, leak: 0.20,
    leakColors: ['#9fd3ff', '#ffd0a0'],
    frame: { style: 'thin', color: '#f0f0f0' }, dateStamp: true,
    paid: false, price: 0
  },

  /* ------------------------------------------------------------------ *
   * VINTAGE — BLACK & WHITE
   * ------------------------------------------------------------------ */
  {
    id: 'mono_bw', name: 'B&W Classic', tag: 'Black & White', group: 'vintage', bw: true,
    desc: 'Classic black-and-white film: full tonal range, grain and a soft vignette.',
    filter: 'grayscale(1) contrast(1.08) brightness(1.02)',
    tone: { lift: 0.01, gamma: 1.05, gain: 1.02, warmth: 0.0, sat: 0.0 },
    halation: 0.35, chroma: 0.06,
    grain: 0.44, dust: 0.24, vignette: 0.44, leak: 0.06,
    leakColors: ['#ffffff', '#dddddd'],
    frame: { style: 'thin', color: '#f0f0f0' }, dateStamp: false,
    paid: false, price: 0
  },
  {
    id: 'mono_noir', name: 'B&W Noir', tag: 'High-Contrast B&W', group: 'vintage', bw: true,
    desc: 'Dramatic high-contrast monochrome with crushed blacks and heavy falloff.',
    filter: 'grayscale(1) contrast(1.38) brightness(0.98)',
    tone: { lift: -0.04, gamma: 1.24, gain: 1.00, warmth: 0.0, sat: 0.0 },
    halation: 0.25, chroma: 0.05,
    grain: 0.50, dust: 0.28, vignette: 0.62, leak: 0.05,
    leakColors: ['#ffffff'],
    frame: { style: 'cinema', color: '#0d0d0d' }, dateStamp: false,
    paid: false, price: 0
  }
];

if (typeof module !== 'undefined') { module.exports = { CAMERAS }; }
