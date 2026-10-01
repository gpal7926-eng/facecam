/* FaceCam — camera presets.
 *
 * Three families:
 *   group: 'vintage'  film simulations — grain, leaks, vignette, frames
 *   group: 'bw'       black & white — desaturated, silver-grain
 *   group: 'beauty'   clean, iPhone-like enhance — smooth, sharp, natural
 *
 * vintage / bw use: filter, tone, halation, chroma, grain, dust, vignette,
 *                   leak, frame, dateStamp
 * beauty uses:      filter, beauty { exposure, contrast, sat, warmth,
 *                   smooth, sharpen, glow }
 */
const CAMERAS = [
  /* ================================================================== *
   * BEAUTY — clean, natural, phone-camera enhance
   * ================================================================== */
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
    id: 'beauty_soft', name: 'Soft', tag: 'Dreamy', group: 'beauty',
    desc: 'Dreamy, low-contrast glow with the strongest skin softening.',
    filter: 'contrast(0.96) saturate(1.02) brightness(1.08)',
    beauty: { exposure: 0.075, contrast: 0.97, sat: 1.02, warmth: 0.045, smooth: 1.00, sharpen: 0.45, glow: 0.70 },
    paid: false, price: 0
  },
  {
    id: 'beauty_glow', name: 'Glow', tag: 'Lit from within', group: 'beauty',
    desc: 'Bright with a strong bloom — that lit-from-within look.',
    filter: 'contrast(1.03) saturate(1.10) brightness(1.12)',
    beauty: { exposure: 0.120, contrast: 1.04, sat: 1.10, warmth: 0.030, smooth: 0.84, sharpen: 0.55, glow: 0.85 },
    paid: false, price: 0
  },
  {
    id: 'beauty_radiance', name: 'Radiance', tag: 'Crisp & clear', group: 'beauty',
    desc: 'Crisp, clear, bright skin with sharp detail throughout.',
    filter: 'contrast(1.10) saturate(1.08) brightness(1.06)',
    beauty: { exposure: 0.060, contrast: 1.12, sat: 1.08, warmth: 0.020, smooth: 0.74, sharpen: 1.00, glow: 0.34 },
    paid: false, price: 0
  },
  {
    id: 'beauty_matte', name: 'Matte', tag: 'Soft matte', group: 'beauty',
    desc: 'Lifted blacks and a matte finish, with natural, even skin.',
    filter: 'contrast(0.98) saturate(1.04) brightness(1.05)',
    beauty: { exposure: 0.085, contrast: 0.99, sat: 1.04, warmth: 0.035, smooth: 0.88, sharpen: 0.62, glow: 0.38 },
    paid: false, price: 0
  },
  {
    id: 'beauty_clean', name: 'Clean', tag: 'Neutral & even', group: 'beauty',
    desc: 'Neutral, true-to-life skin with a light, even enhance.',
    filter: 'contrast(1.03) saturate(1.02) brightness(1.05)',
    beauty: { exposure: 0.060, contrast: 1.04, sat: 1.04, warmth: 0.0, smooth: 0.72, sharpen: 0.82, glow: 0.18 },
    paid: false, price: 0
  },
  {
    id: 'beauty_rich', name: 'Rich', tag: 'Polished colour', group: 'beauty',
    desc: 'Warmer, richer colour and contrast for a polished portrait.',
    filter: 'sepia(0.08) saturate(1.16) contrast(1.08) brightness(1.06)',
    beauty: { exposure: 0.120, contrast: 1.16, sat: 1.22, warmth: 0.100, smooth: 0.78, sharpen: 0.92, glow: 0.40 },
    paid: false, price: 0
  },

  /* ================================================================== *
   * BLACK & WHITE
   * ================================================================== */
  {
    id: 'bw_classic', name: 'B&W Classic', tag: 'Silver grey', group: 'bw',
    desc: 'Balanced silver-grey monochrome with medium contrast.',
    filter: 'grayscale(1) contrast(1.06) brightness(1.02)',
    tone: { lift: 0.015, gamma: 1.04, gain: 1.01, warmth: 0, sat: 0 },
    halation: 0.30, chroma: 0,
    grain: 0.34, dust: 0.20, vignette: 0.34, leak: 0.10,
    leakColors: ['#dddddd', '#bbbbbb'],
    frame: { style: 'thin', color: '#f2f2f2' }, dateStamp: false,
    paid: false, price: 0
  },
  {
    id: 'bw_high', name: 'B&W Contrast', tag: 'Deep blacks', group: 'bw',
    desc: 'High-contrast monochrome. Punchy blacks, bright whites.',
    filter: 'grayscale(1) contrast(1.35) brightness(1.00)',
    tone: { lift: -0.045, gamma: 1.22, gain: 1.02, warmth: 0, sat: 0 },
    halation: 0.22, chroma: 0,
    grain: 0.46, dust: 0.26, vignette: 0.48, leak: 0.08,
    leakColors: ['#ffffff', '#cccccc'],
    frame: { style: 'thick', color: '#ffffff' }, dateStamp: false,
    paid: false, price: 0
  },
  {
    id: 'bw_warm', name: 'B&W Sepia', tag: 'Warm tone', group: 'bw',
    desc: 'Warm sepia-toned monochrome — an old print look.',
    filter: 'grayscale(1) sepia(0.55) contrast(1.02) brightness(1.04)',
    tone: { lift: 0.045, gamma: 1.00, gain: 1.03, warmth: 0.075, sat: 0.18 },
    halation: 0.42, chroma: 0.10,
    grain: 0.40, dust: 0.30, vignette: 0.44, leak: 0.20,
    leakColors: ['#e8c9a0', '#d9b183'],
    frame: { style: 'thin', color: '#f4ead9' }, dateStamp: true,
    paid: false, price: 0
  },
  {
    id: 'bw_cool', name: 'B&W Cool', tag: 'Cool mono', group: 'bw',
    desc: 'Cool, blue-toned monochrome with a clean modern feel.',
    filter: 'grayscale(1) contrast(1.06) brightness(1.02)',
    tone: { lift: 0.005, gamma: 1.06, gain: 1.0, warmth: -0.03, sat: 0 },
    halation: 0.28, chroma: 0,
    grain: 0.30, dust: 0.16, vignette: 0.30, leak: 0.06,
    leakColors: ['#cfe8ff', '#9fd0ff'],
    frame: { style: 'thin', color: '#eef2f6' }, dateStamp: true,
    paid: false, price: 0
  },
  {
    id: 'bw_fade', name: 'B&W Fade', tag: 'Faded mono', group: 'bw',
    desc: 'Low-contrast faded monochrome with soft lifted blacks.',
    filter: 'grayscale(1) contrast(0.86) brightness(1.06)',
    tone: { lift: 0.075, gamma: 0.92, gain: 1.04, warmth: 0, sat: 0 },
    halation: 0.30, chroma: 0,
    grain: 0.38, dust: 0.20, vignette: 0.24, leak: 0.10,
    leakColors: ['#ffffff', '#dddddd'],
    frame: { style: 'thin', color: '#f2f2f2' }, dateStamp: true,
    paid: false, price: 0
  },

  /* ================================================================== *
   * VINTAGE — decades
   * ================================================================== */
  {
    id: 'vintage_50s', name: '50s Kodachrome', tag: '1950s', group: 'vintage',
    desc: 'Warm, muted 1950s Kodachrome. Low contrast, heavy grain.',
    filter: 'sepia(0.22) saturate(0.88) contrast(0.92) brightness(1.04)',
    tone: { lift: 0.045, gamma: 0.94, gain: 1.03, warmth: 0.065, sat: 0.92 },
    halation: 0.45, chroma: 0.22,
    grain: 0.62, dust: 0.42, vignette: 0.42, leak: 0.30,
    leakColors: ['#e8b17a', '#d99a63'],
    frame: { style: 'thin', color: '#f0e6d2' }, dateStamp: true,
    paid: false, price: 0
  },
  {
    id: 'vintage_60s', name: '60s Ektachrome', tag: '1960s', group: 'vintage',
    desc: 'Cooler, cyan-leaning 1960s slide film. Faded and clean.',
    filter: 'hue-rotate(8deg) saturate(0.95) contrast(0.96) brightness(1.05)',
    tone: { lift: 0.055, gamma: 0.97, gain: 1.04, warmth: -0.055, sat: 0.96 },
    halation: 0.38, chroma: 0.24,
    grain: 0.44, dust: 0.30, vignette: 0.36, leak: 0.34,
    leakColors: ['#9fd8e8', '#8fc4dd'],
    frame: { style: 'thin', color: '#e6f0f2' }, dateStamp: false,
    paid: false, price: 0
  },
  {
    id: 'vintage_70s', name: '70s Faded', tag: '1970s', group: 'vintage',
    desc: 'Orange-brown 1970s cast, lifted blacks and big light leaks.',
    filter: 'sepia(0.40) saturate(1.10) contrast(0.90) brightness(1.06)',
    tone: { lift: 0.080, gamma: 0.92, gain: 1.06, warmth: 0.120, sat: 1.12 },
    halation: 0.62, chroma: 0.34,
    grain: 0.58, dust: 0.46, vignette: 0.44, leak: 0.72,
    leakColors: ['#ffb257', '#ff8a3d'],
    frame: { style: 'thin', color: '#f6e7cd' }, dateStamp: true,
    paid: false, price: 0
  },
  {
    id: 'vintage_80s', name: '80s Punch', tag: '1980s', group: 'vintage',
    desc: 'Punchy, saturated 1980s colour. High contrast, slight magenta.',
    filter: 'contrast(1.28) saturate(1.42) hue-rotate(4deg) brightness(1.04)',
    tone: { lift: -0.015, gamma: 1.14, gain: 1.03, warmth: 0.045, sat: 1.40 },
    halation: 0.34, chroma: 0.40,
    grain: 0.34, dust: 0.22, vignette: 0.40, leak: 0.36,
    leakColors: ['#ff6fae', '#ff9ecb'],
    frame: { style: 'thin', color: '#ffffff' }, dateStamp: false,
    paid: false, price: 0
  },
  {
    id: 'vintage_90s', name: '90s Disposable', tag: '1990s', group: 'vintage',
    desc: '1990s disposable camera: harsh flash, magenta cast, strong vignette.',
    filter: 'contrast(1.22) saturate(1.20) hue-rotate(10deg) brightness(1.07)',
    tone: { lift: -0.010, gamma: 1.12, gain: 1.07, warmth: 0.070, sat: 1.22 },
    halation: 0.50, chroma: 0.62,
    grain: 0.66, dust: 0.34, vignette: 0.58, leak: 0.24,
    leakColors: ['#ff77c8', '#ffd0f0'],
    frame: { style: 'thin', color: '#ffffff' }, dateStamp: true,
    paid: false, price: 0
  },

  /* ================================================================== *
   * VINTAGE — classic film simulations
   * ================================================================== */
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

  /* ================================================================== *
   * VINTAGE — character cameras
   * ================================================================== */
  {
    id: 'flash_cam', name: 'Flash Camera', tag: 'Harsh direct flash', group: 'vintage',
    desc: 'Hard on-camera flash: bright centre, deep falloff, cool white light.',
    filter: 'brightness(1.12) contrast(1.10) saturate(1.05)',
    tone: { lift: -0.03, gamma: 1.10, gain: 1.10, warmth: -0.02, sat: 1.05 },
    halation: 0.50, chroma: 0.20,
    grain: 0.40, dust: 0.20, vignette: 0.62, leak: 0.10,
    leakColors: ['#ffffff', '#dfe8ff'],
    frame: { style: 'thin', color: '#ffffff' }, dateStamp: true,
    paid: false, price: 0
  },
  {
    id: 'night_film', name: 'Night Film', tag: 'Low light', group: 'vintage',
    desc: 'Deep blue night stock: crushed shadows, big grain and a soft glow.',
    filter: 'contrast(1.18) saturate(0.95) hue-rotate(-8deg) brightness(0.92)',
    tone: { lift: -0.06, gamma: 1.20, gain: 0.96, warmth: -0.06, sat: 0.95 },
    halation: 0.80, chroma: 0.45,
    grain: 0.60, dust: 0.30, vignette: 0.60, leak: 0.20,
    leakColors: ['#4fa8ff', '#8fd0ff'],
    frame: { style: 'thin', color: '#dfe8f5' }, dateStamp: true,
    paid: false, price: 0
  },
  {
    id: 'cinematic', name: 'Cinematic Film', tag: 'Widescreen', group: 'vintage',
    desc: 'Teal-and-orange movie grade with a letterbox frame.',
    filter: 'contrast(1.16) saturate(1.06) hue-rotate(-12deg) brightness(0.99)',
    tone: { lift: 0.0, gamma: 1.10, gain: 1.0, warmth: 0.05, sat: 1.04 },
    halation: 0.60, chroma: 0.12,
    grain: 0.24, dust: 0.12, vignette: 0.34, leak: 0.22,
    leakColors: ['#ff8a5c', '#ffd08a'],
    frame: { style: 'cinema', color: '#0d0d0d' }, dateStamp: false,
    paid: false, price: 0
  },
  {
    id: 'light_leak', name: 'Light Leak Film', tag: 'Leaky roll', group: 'vintage',
    desc: 'A badly-sealed roll: strong, random coloured light streaks.',
    filter: 'contrast(1.04) saturate(1.10) brightness(1.05)',
    tone: { lift: 0.04, gamma: 1.0, gain: 1.05, warmth: 0.06, sat: 1.10 },
    halation: 0.70, chroma: 0.20,
    grain: 0.32, dust: 0.20, vignette: 0.28, leak: 0.95,
    leakColors: ['#ff5f6d', '#ffc371', '#c9a7ff'],
    frame: { style: 'thin', color: '#fff7ea' }, dateStamp: true,
    paid: false, price: 0
  },
  {
    id: 'dreamy', name: 'Dreamy Film', tag: 'Soft & pastel', group: 'vintage',
    desc: 'Low-contrast pastel with a heavy bloom — soft, dreamy, airy.',
    filter: 'contrast(0.94) saturate(1.05) brightness(1.10) blur(0.3px)',
    tone: { lift: 0.07, gamma: 0.92, gain: 1.08, warmth: 0.03, sat: 1.06 },
    halation: 0.90, chroma: 0.14,
    grain: 0.20, dust: 0.10, vignette: 0.22, leak: 0.40,
    leakColors: ['#ffd6ef', '#c9e7ff'],
    frame: { style: 'thin', color: '#fdf5ff' }, dateStamp: false,
    paid: false, price: 0
  },
  {
    id: 'old_digital', name: 'Old Digital Camera', tag: 'Early digicam', group: 'vintage',
    desc: 'Early-2000s compact: oversharpened, magenta cast, noisy sensor.',
    filter: 'contrast(1.22) saturate(1.22) hue-rotate(6deg) brightness(1.03)',
    tone: { lift: -0.02, gamma: 1.12, gain: 1.04, warmth: 0.04, sat: 1.20 },
    halation: 0.40, chroma: 0.60,
    grain: 0.55, dust: 0.30, vignette: 0.40, leak: 0.15,
    leakColors: ['#ff77c8', '#ffd0f0'],
    frame: { style: 'thin', color: '#ffffff' }, dateStamp: true,
    paid: false, price: 0
  },
  {
    id: 'vhs', name: 'VHS / Retro', tag: 'Tape look', group: 'vintage',
    desc: 'VHS tape: pumped colour, chroma bleed and a soft video glow.',
    filter: 'contrast(1.14) saturate(1.40) hue-rotate(-6deg) brightness(1.04)',
    tone: { lift: 0.02, gamma: 1.06, gain: 1.05, warmth: 0.02, sat: 1.35 },
    halation: 0.60, chroma: 0.70,
    grain: 0.50, dust: 0.20, vignette: 0.34, leak: 0.30,
    leakColors: ['#4fd8ff', '#ff4fd8'],
    frame: { style: 'thin', color: '#ffffff' }, dateStamp: true,
    paid: false, price: 0
  },
  {
    id: 'kodak_warm', name: 'Kodak Warm', tag: 'Golden film', group: 'vintage',
    desc: 'Warm, saturated consumer film — golden skin and rich colour.',
    filter: 'sepia(0.16) saturate(1.22) contrast(1.06) brightness(1.04)',
    tone: { lift: 0.02, gamma: 1.02, gain: 1.04, warmth: 0.075, sat: 1.20 },
    halation: 0.40, chroma: 0.16,
    grain: 0.26, dust: 0.14, vignette: 0.30, leak: 0.30,
    leakColors: ['#ffcf7a', '#ff9d5c'],
    frame: { style: 'thin', color: '#fbf3e3' }, dateStamp: true,
    paid: false, price: 0
  },
  {
    id: 'cold_blue', name: 'Cold Blue Film', tag: 'Cool stock', group: 'vintage',
    desc: 'Cool blue daylight stock — icy, clean and slightly muted.',
    filter: 'contrast(1.08) saturate(0.98) hue-rotate(-10deg) brightness(1.00)',
    tone: { lift: 0.03, gamma: 1.04, gain: 1.0, warmth: -0.075, sat: 1.0 },
    halation: 0.34, chroma: 0.18,
    grain: 0.30, dust: 0.16, vignette: 0.36, leak: 0.24,
    leakColors: ['#8fd8ff', '#c6f0ff'],
    frame: { style: 'thin', color: '#eaf6ff' }, dateStamp: false,
    paid: false, price: 0
  },
  {
    id: 'faded', name: 'Faded Film', tag: 'Washed out', group: 'vintage',
    desc: 'Sun-faded, low-contrast film with lifted blacks and muted colour.',
    filter: 'contrast(0.88) saturate(0.82) brightness(1.08)',
    tone: { lift: 0.09, gamma: 0.90, gain: 1.06, warmth: 0.02, sat: 0.85 },
    halation: 0.40, chroma: 0.12,
    grain: 0.34, dust: 0.20, vignette: 0.30, leak: 0.35,
    leakColors: ['#ffd9b0', '#cfe3ff'],
    frame: { style: 'thin', color: '#f4efe6' }, dateStamp: true,
    paid: false, price: 0
  },

  /* ================================================================== *
   * VINTAGE — movie stocks (great for video)
   * ================================================================== */
  {
    id: 'film_8mm', name: '8mm Film', tag: 'Home movie', group: 'vintage',
    desc: 'Heavy grain, dust, scratches and flicker — an old home-movie reel.',
    filter: 'sepia(0.30) saturate(0.90) contrast(1.08) brightness(1.03)',
    tone: { lift: 0.06, gamma: 1.02, gain: 1.03, warmth: 0.09, sat: 0.92 },
    halation: 0.60, chroma: 0.25,
    grain: 0.75, dust: 0.60, vignette: 0.55, leak: 0.40,
    leakColors: ['#ffd9a0', '#ff9a5c'],
    frame: { style: 'thick', color: '#f2ead8' }, dateStamp: true,
    paid: false, price: 0
  },
  {
    id: 'super_8', name: 'Super 8', tag: 'Home reel', group: 'vintage',
    desc: 'Soft focus, warm leaks and a flickering exposure — classic Super 8.',
    filter: 'sepia(0.22) saturate(1.05) contrast(0.98) brightness(1.06) blur(0.3px)',
    tone: { lift: 0.07, gamma: 0.98, gain: 1.05, warmth: 0.07, sat: 1.02 },
    halation: 0.85, chroma: 0.20,
    grain: 0.55, dust: 0.45, vignette: 0.42, leak: 0.60,
    leakColors: ['#ffcf8f', '#ff8f6b'],
    frame: { style: 'thin', color: '#f6ecd6' }, dateStamp: true,
    paid: false, price: 0
  },
  {
    id: 'minidv', name: 'MiniDV', tag: 'Tape camcorder', group: 'vintage',
    desc: 'Early digital camcorder: noisy sensor, crushed colour, sharpened tape look.',
    filter: 'contrast(1.12) saturate(1.08) hue-rotate(4deg) brightness(1.02)',
    tone: { lift: -0.01, gamma: 1.08, gain: 1.02, warmth: 0.03, sat: 1.10 },
    halation: 0.35, chroma: 0.50,
    grain: 0.42, dust: 0.18, vignette: 0.36, leak: 0.18,
    leakColors: ['#ffd0f0', '#cfe0ff'],
    frame: { style: 'thin', color: '#ffffff' }, dateStamp: true,
    paid: false, price: 0
  }
];

if (typeof module !== 'undefined') { module.exports = { CAMERAS }; }
