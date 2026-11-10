'use strict';
/**
 * استودیوی عکسِ کالا — پردازشِ محلی و قطعی روی سرورِ خودمان (AI-02، IMG-04، IMG-PRO-01…04، §۳۴.۱، §۳۶.۴–۵).
 * D-16 (الف، ۱۴۰۵/۰۸/۱۳): روی سرورِ خودمان، بی سرویسِ بیرونی، پشتِ پرچمِ خاموش (‎lib/image-studio-jobs.js‎).
 *
 *   normalize(body)                    ⇒ { preset, options }   (۴۰۰ برای هر گزینهٔ نادرست)
 *   render(buf, info, request, max)    ⇒ { buf, width, height, derivatives: { thumb?, display? }, report }
 *   fidelity(a, b, alpha)              ⇒ { structure, tile_min } — همان نگهبانی که هر خروجی از آن می‌گذرد
 *
 * زنجیره (هر مرحله جدا روشن/خاموش و در `report.steps` با «انجام شد / لازم نبود / خاموش» و دلیل):
 *   جهتِ EXIF ⇒ کوچک‌سازی تا WORK_SIDE ⇒ تراز سفیدی ⇒ نور و کنتراست ⇒ وضوحِ ملایم ⇒ جداسازیِ زمینهٔ یک‌دست
 *   ⇒ زمینهٔ سفید ⇒ سایهٔ طبیعی ⇒ کادر و نسبت ⇒ سنجشِ وفاداری ⇒ JPEG ِ بی فراداده + نسخهٔ فهرست و صفحهٔ کالا
 *
 * ⛔ وفاداری به کالا (۳۶.۵): هیچ مرحلهٔ مولّد نیست. نور/کنتراست یک منحنی روی روشنایی با یک ضریب برای هر سه کانال (فام و اشباع همان)، تراز سفیدی
 *    سه ضریبِ سراسری با سقفِ ±۲۵٪ (هرگز اشباعِ بیشتر یا رنگِ دلخواه)، وضوح فقط Unsharp Mask ِ روشنایی (هیچ
 *    نوشته یا جزئیاتِ تازه)، زمینه و سایه فقط بیرون از مرزِ کالا (alpha = ۱ دست نمی‌خورد)، کادر فقط بزرگ‌تر
 *    از مرزِ کالا + حاشیهٔ امن (هرگز بریدنِ کالا). هیچ مرحله‌ای بزرگ‌نمایی نمی‌کند.
 * ⛔ نتیجهٔ نامطمئن هرگز «آماده» نیست: لبهٔ کم‌تضاد (شیشه، پلاستیکِ شفاف، مو)، تغییرِ ساختار در محدودهٔ کالا،
 *    ضریبِ رنگِ بزرگ یا عکسِ واقعاً تار ⇒ `risk` و بی «تأییدِ ریسک» منتشر نمی‌شود (‎image-studio-jobs.review‎).
 * ⛔ حذفِ پس‌زمینه این‌جا فقط برای زمینهٔ یک‌دستِ متصل به لبه است (پر کردن از لبه‌ها، نه مدل)؛ زمینهٔ شلوغ ⇒
 *    «لازم نبود/نشد» با دلیل `background_not_uniform`. مدلِ خودمیزبان (AI-01) هنوز نیست.
 * ⛔ آستانه‌ها روی مجموعهٔ مصنوعیِ ‎test/image-studio.test.js‎ آزموده شده‌اند، نه روی عکسِ واقعیِ دکان‌ها (§۳۶.۱۰).
 */
const codec = require('./image-codec');
const quality = require('./image-quality');
const { badRequest, ApiError } = require('../middleware/errors');

const LIMITS = Object.freeze({
  WORK_SIDE: 1600,            // ضلعِ بلندِ نسخهٔ پردازش‌شده (≤ zoom ِ بانک)
  THUMB_SIDE: 400, THUMB_BYTES: 150 * 1024,
  DISPLAY_SIDE: 1200,
  QUALITIES: [88, 82, 76, 70, 64],   // همه بالای کفِ ۵۰ ِ IMG-PRO-16
  LEVEL_LO: 0.005, LEVEL_HI: 0.995, LEVEL_GAIN_MAX: 1.6, LEVEL_MIN_CHANGE: 3,
  DARK_MEAN: 0.38, GAMMA_TARGET: 0.45, GAMMA_MIN: 0.7,
  WB_GAIN_MIN: 0.8, WB_GAIN_MAX: 1.25, WB_NEEDED: 0.02,
  WB_NEUTRAL_CHROMA: 0.18, WB_BG_CHROMA: 0.35, WB_MIN_SHARE: 0.03,
  //  پهنای تاریِ لبه‌ها (σ ِ گاوسیِ هم‌ارز، پیکسل در اندازهٔ کار) = √(E[|∇Y|²] / 2·E[(ΔY)²]) — به شمارِ لبه و
  //  تضاد وابسته نیست (واریانسِ لاپلاسینِ IMG-15 به محتوا وابسته است و این‌جا فقط گزارش می‌شود)
  SHARPEN_ABOVE_PX: 0.9,      // تارتر ⇒ Unsharp Mask
  RESHOOT_ABOVE_PX: 1.8,      // تارتر ⇒ جزئیاتِ واقعی نیست: «عکس‌برداریِ دوباره»، نه جزئیاتِ ساختگی
  USM_AMOUNT: 0.8, USM_THRESHOLD: 3,
  BORDER_SHARE: 0.02, BG_UNIFORM_MIN: 0.8, BG_NEAR: 24, BG_TOL_MIN: 16, BG_TOL_MAX: 48,
  SUBJECT_MIN: 0.01, SUBJECT_MAX: 0.92, WEAK_EDGE_K: 3, WEAK_EDGE_MAX: 0.35,
  MARGIN: 0.06, SHADOW_ALPHA: 0.22,
  STRUCT_MIN: 0.85, TILE_MIN: 0.6, COLOR_SHIFT_MAX: 0.15,
});

const PRESETS = Object.freeze({
  //  «عکسِ کاتالوگ» ِ ۳۶.۴: زمینهٔ سفید، کالا در مرکز با حاشیهٔ ثابت، مربع، بی سایه و بی هیچ نوشته
  catalog: { light: true, white_balance: true, sharpen: true, background: 'white', crop: true, aspect: '1:1', shadow: false },
  //  فقط نور، رنگ و وضوح — هندسه و زمینه همان
  enhance: { light: true, white_balance: true, sharpen: true, background: 'keep', crop: false, aspect: 'original', shadow: false },
  //  تصویرِ تبلیغ (AI-03): برچسبِ جدای `promo` و هرگز جای عکسِ واقعیِ کالا (‎image-bank.selectIn‎)
  ad: { light: true, white_balance: true, sharpen: true, background: 'white', crop: true, aspect: '16:9', shadow: true },
});
const ASPECTS = Object.freeze({ original: 0, '1:1': 1, '4:3': 4 / 3, '3:4': 3 / 4, '16:9': 16 / 9 });
const BOOL = ['light', 'white_balance', 'sharpen', 'crop', 'shadow'];

/** درخواستِ کاربر ⇒ preset و گزینه‌های بسته؛ برچسب فقط از preset (کاربر نمی‌تواند عکسِ کالا را تبلیغ بنامد یا برعکس) */
function normalize(body) {
  const b = body && typeof body === 'object' ? body : {};
  const preset = b.preset == null ? 'catalog' : String(b.preset);
  if (!Object.prototype.hasOwnProperty.call(PRESETS, preset)) throw badRequest('preset فقط catalog، enhance یا ad', 'bad_preset');
  const o = { ...PRESETS[preset] };
  const opt = b.options && typeof b.options === 'object' && !Array.isArray(b.options) ? b.options : {};
  for (const k of Object.keys(opt)) {
    if (BOOL.includes(k)) {
      if (typeof opt[k] !== 'boolean') throw badRequest(`گزینهٔ ${k} فقط true یا false`, 'bad_option');
      o[k] = opt[k];
    } else if (k === 'background') {
      if (opt[k] !== 'white' && opt[k] !== 'keep') throw badRequest('زمینه فقط white یا keep', 'bad_option');
      o[k] = opt[k];
    } else if (k === 'aspect') {
      if (typeof opt[k] !== 'string' || !Object.prototype.hasOwnProperty.call(ASPECTS, opt[k])) {
        throw badRequest('نسبت فقط original، 1:1، 4:3، 3:4 یا 16:9', 'bad_option');
      }
      o[k] = opt[k];
    } else throw badRequest(`گزینهٔ ناشناخته: ${String(k).slice(0, 32)}`, 'bad_option');
  }
  //  سایه فقط روی زمینهٔ تازه و بیرون از مرزِ کالا معنا دارد (۳۶.۵)
  if (o.shadow && o.background !== 'white') throw badRequest('سایهٔ طبیعی فقط با زمینهٔ سفید', 'shadow_needs_white');
  return { preset, options: o, label: preset === 'ad' ? 'promo' : 'product' };
}

/* ------------------------------------------------------- کمک‌ها */

const r3 = (x) => Math.round(x * 1000) / 1000;
const lumaOf = (rgb, n) => 0.299 * rgb[n * 3] + 0.587 * rgb[n * 3 + 1] + 0.114 * rgb[n * 3 + 2];
const clamp8 = (v) => (v < 0 ? 0 : v > 255 ? 255 : Math.round(v));
const chroma = (r, g, b) => { const mx = Math.max(r, g, b); return mx ? (mx - Math.min(r, g, b)) / mx : 0; };
const copy = (img) => ({ w: img.w, h: img.h, rgb: Uint8Array.from(img.rgb), alpha: img.alpha ? Uint8Array.from(img.alpha) : null });

function lumaPlane(img) {
  const n = img.w * img.h, out = new Uint8Array(n);
  for (let i = 0; i < n; i++) out[i] = clamp8(lumaOf(img.rgb, i));
  return out;
}

/** پهنای تاری (σ ِ هم‌ارز، پیکسل): لبهٔ پلّه‌ایِ تارشده با گاوسیِ σ دقیقاً σ می‌دهد؛ عکسِ بی لبه ⇒ null */
function blurPx(Y, w, h) {
  let gg = 0, ll = 0;
  for (let y = 1; y < h - 1; y++) for (let x = 1; x < w - 1; x++) {
    const i = y * w + x, gx = (Y[i + 1] - Y[i - 1]) / 2, gy = (Y[i + w] - Y[i - w]) / 2;
    const l = Y[i - 1] + Y[i + 1] + Y[i - w] + Y[i + w] - 4 * Y[i];
    gg += gx * gx + gy * gy; ll += l * l;
  }
  return ll > 1e-6 ? r3(Math.sqrt(gg / (2 * ll))) : null;
}

/** همان سنجه‌های IMG-15 (‎image-quality.lumaMetrics‎) روی همین پیکسل‌ها، و پهنای تاری */
function metricsOf(img) {
  const Y = lumaPlane(img), m = quality.lumaMetrics({ w: img.w, h: img.h, luma: Y });
  return m && { sharpness: m.sharpness, blur_px: blurPx(Y, img.w, img.h), luma_mean: m.luma_mean, luma_std: m.luma_std, p05: m.p05, p95: m.p95 };
}

/** نوارِ لبه: شاخص‌های پیکسل‌های نوارِ b پیکسلی دورِ عکس */
function borderIndex(w, h) {
  const b = Math.max(2, Math.round(LIMITS.BORDER_SHARE * Math.min(w, h))), out = [];
  for (let y = 0; y < h; y++) for (let x = 0; x < w; x++) {
    if (x < b || y < b || x >= w - b || y >= h - b) out.push(y * w + x);
  }
  return out;
}

function median(values) {
  const a = Float64Array.from(values).sort();
  return a.length ? a[Math.floor(a.length / 2)] : 0;
}

/** رنگِ زمینه از نوارِ لبه: میانهٔ هر کانال و سهمِ نزدیک به آن */
function borderStats(img, idx) {
  const bg = [0, 1, 2].map((c) => median(idx.map((n) => img.rgb[n * 3 + c])));
  const d = idx.map((n) => Math.max(...[0, 1, 2].map((c) => Math.abs(img.rgb[n * 3 + c] - bg[c]))));
  const near = d.filter((v) => v <= LIMITS.BG_NEAR).length / Math.max(1, d.length);
  return { bg, near, noise: median(d) };
}

/* ---------------------------------------------------- مرحله‌ها */

/** تراز سفیدی: مرجع = زمینهٔ یک‌دست و روشنِ لبه، وگرنه پیکسل‌های تقریباً خنثیِ درون؛ سه ضریبِ سراسریِ سقف‌دار */
function whiteBalance(img) {
  const L = LIMITS, idx = borderIndex(img.w, img.h), bs = borderStats(img, idx);
  let ref = [], source = '';
  const [br, bgc, bb] = bs.bg;
  if (bs.near >= L.BG_UNIFORM_MIN && 0.299 * br + 0.587 * bgc + 0.114 * bb >= 110 && chroma(br, bgc, bb) <= L.WB_BG_CHROMA) {
    ref = idx.filter((n) => Math.max(...[0, 1, 2].map((c) => Math.abs(img.rgb[n * 3 + c] - bs.bg[c]))) <= L.BG_NEAR);
    source = 'background';
  } else {
    const n = img.w * img.h;
    for (let i = 0; i < n; i++) {
      const r = img.rgb[i * 3], g = img.rgb[i * 3 + 1], b = img.rgb[i * 3 + 2], y = lumaOf(img.rgb, i);
      if (y >= 60 && y <= 235 && chroma(r, g, b) < L.WB_NEUTRAL_CHROMA) ref.push(i);
    }
    source = 'neutral';
    if (ref.length < L.WB_MIN_SHARE * n) return { status: 'skipped', reason: 'no_neutral_reference' };
  }
  const m = [0, 1, 2].map((c) => ref.reduce((a, n) => a + img.rgb[n * 3 + c], 0) / ref.length);
  if (m.some((v) => v < 1)) return { status: 'skipped', reason: 'no_neutral_reference' };
  const t = (m[0] + m[1] + m[2]) / 3;
  const gains = m.map((v) => Math.min(L.WB_GAIN_MAX, Math.max(L.WB_GAIN_MIN, t / v)));
  if (gains.every((g) => Math.abs(g - 1) < L.WB_NEEDED)) return { status: 'skipped', reason: 'not_needed', params: { source } };
  const n = img.w * img.h;
  for (let i = 0; i < n; i++) for (let c = 0; c < 3; c++) img.rgb[i * 3 + c] = clamp8(img.rgb[i * 3 + c] * gains[c]);
  return { status: 'applied', params: { source, gains: gains.map(r3) }, shift: Math.max(...gains.map((g) => Math.abs(g - 1))) };
}

/** نور و کنتراست: کشیدنِ صدکِ ۰٫۵ تا ۹۹٫۵ (سقفِ ضریب)، و فقط برای عکسِ تاریک گامای روشن‌کننده؛ منحنی روی روشنایی */
function levels(img) {
  const L = LIMITS, n = img.w * img.h, hist = new Uint32Array(256);
  for (let i = 0; i < n; i++) hist[clamp8(lumaOf(img.rgb, i))]++;
  const pct = (p) => {
    const rank = Math.max(1, Math.ceil(p * n));
    let c = 0;
    for (let v = 0; v < 256; v++) { c += hist[v]; if (c >= rank) return v; }
    return 255;
  };
  const lo = pct(L.LEVEL_LO), hi = pct(L.LEVEL_HI);
  if (hi - lo < 8) return { status: 'skipped', reason: 'flat_image' };
  //  کشیدنِ کامل (صدک‌ها تا ۳ و ۲۵۲) فقط تا سقفِ ضریب؛ بیشتر لازم ⇒ همان ضریبِ سقف دورِ میانگینِ خودِ عکس
  //  (روشنیِ کلی می‌ماند: کالای روشن روی زمینهٔ روشن خاکستری نمی‌شود)
  let mean0 = 0;
  for (let v = 0; v < 256; v++) mean0 += hist[v] * v;
  mean0 /= n;
  const full = 249 / (hi - lo), g = Math.min(L.LEVEL_GAIN_MAX, full);
  const stretch = (v) => (full <= 1 ? v : full <= L.LEVEL_GAIN_MAX ? 3 + (v - lo) * g : mean0 + (v - mean0) * g);
  let mean = 0;
  for (let v = 0; v < 256; v++) mean += hist[v] * Math.min(255, Math.max(0, stretch(v)));
  mean /= n * 255;
  const gamma = mean < L.DARK_MEAN && mean > 0.02
    ? Math.max(L.GAMMA_MIN, Math.min(1, Math.log(L.GAMMA_TARGET) / Math.log(mean))) : 1;
  const lut = new Float32Array(256);
  let change = 0;
  for (let v = 0; v < 256; v++) {
    lut[v] = 255 * Math.pow(Math.min(255, Math.max(0, stretch(v))) / 255, gamma);
    change = Math.max(change, Math.abs(lut[v] - v));
  }
  if (change <= L.LEVEL_MIN_CHANGE) return { status: 'skipped', reason: 'not_needed' };
  //  منحنی روی روشنایی و هر سه کانال با یک ضریب ⇒ فام و اشباع همان (کشیدنِ نقطهٔ سیاه رنگ را تندتر نمی‌کند)؛
  //  کانالی که از ۲۵۵ می‌گذرد ضریب را پایین می‌آورد، نه رنگ را
  for (let i = 0; i < n; i++) {
    const y = lumaOf(img.rgb, i);
    if (y < 0.5) continue;
    const yi = Math.min(255, Math.round(y)), mx = Math.max(img.rgb[i * 3], img.rgb[i * 3 + 1], img.rgb[i * 3 + 2]);
    const k = Math.min(lut[yi] / y, 255 / mx);
    for (let c = 0; c < 3; c++) img.rgb[i * 3 + c] = clamp8(img.rgb[i * 3 + c] * k);
  }
  return { status: 'applied', params: { low: lo, high: hi, gain: r3(full <= 1 ? 1 : g), gamma: r3(gamma) } };
}

/** صافیِ گاوسیِ ۵-نقطه‌ایِ جداشدنی روی یک صفحه */
function blur5(src, w, h) {
  const k = [1, 4, 6, 4, 1], tmp = new Float32Array(w * h), out = new Float32Array(w * h);
  for (let y = 0; y < h; y++) for (let x = 0; x < w; x++) {
    let a = 0;
    for (let j = -2; j <= 2; j++) a += k[j + 2] * src[y * w + Math.min(w - 1, Math.max(0, x + j))];
    tmp[y * w + x] = a / 16;
  }
  for (let y = 0; y < h; y++) for (let x = 0; x < w; x++) {
    let a = 0;
    for (let j = -2; j <= 2; j++) a += k[j + 2] * tmp[Math.min(h - 1, Math.max(0, y + j)) * w + x];
    out[y * w + x] = a / 16;
  }
  return out;
}

/** وضوحِ ملایم: Unsharp Mask فقط روی روشنایی (همان افزایش برای هر سه کانال ⇒ بی حاشیهٔ رنگی)، با آستانهٔ نویز */
function sharpen(img, before) {
  const L = LIMITS;
  if (!before || before.blur_px == null) return { status: 'skipped', reason: 'unmeasured' };
  if (before.blur_px <= L.SHARPEN_ABOVE_PX) return { status: 'skipped', reason: 'sharp_enough' };
  const n = img.w * img.h, Y = new Float32Array(n);
  for (let i = 0; i < n; i++) Y[i] = lumaOf(img.rgb, i);
  const b = blur5(Y, img.w, img.h);
  for (let i = 0; i < n; i++) {
    const d = Y[i] - b[i];
    if (Math.abs(d) <= L.USM_THRESHOLD) continue;
    const add = L.USM_AMOUNT * d;
    for (let c = 0; c < 3; c++) img.rgb[i * 3 + c] = clamp8(img.rgb[i * 3 + c] + add);
  }
  return { status: 'applied', params: { amount: L.USM_AMOUNT, radius: 1, threshold: L.USM_THRESHOLD } };
}

/**
 * مرزِ کالا: PNG ِ شفاف ⇒ همان alpha؛ وگرنه زمینهٔ یک‌دستِ لبه با پر کردن از لبه‌ها (۴-همسایه، آستانه از نویزِ
 * لبه). نوشته یا برچسبِ سفیدِ **درونِ** کالا دست نمی‌خورد چون به لبه وصل نیست. لبهٔ کم‌تضاد ⇒ `weak_edges`.
 */
function subjectMask(img) {
  const L = LIMITS, { w, h } = img, n = w * h;
  const bbox = (alpha) => {
    let x0 = w, y0 = h, x1 = -1, y1 = -1;
    for (let y = 0; y < h; y++) for (let x = 0; x < w; x++) {
      if (alpha[y * w + x] < 128) continue;
      x0 = Math.min(x0, x); x1 = Math.max(x1, x); y0 = Math.min(y0, y); y1 = Math.max(y1, y);
    }
    return x1 < 0 ? null : { x0, y0, x1, y1 };
  };
  const shareOf = (alpha) => alpha.reduce((a, v) => a + (v >= 128 ? 1 : 0), 0) / n;
  if (img.alpha) {
    const share = shareOf(img.alpha);
    if (share < L.SUBJECT_MIN) return { ok: false, reason: 'subject_not_found' };
    return { ok: true, source: 'alpha', alpha: Uint8Array.from(img.alpha), bbox: bbox(img.alpha), share, weak: 0, bg: [255, 255, 255] };
  }
  const idx = borderIndex(w, h), bs = borderStats(img, idx);
  if (bs.near < L.BG_UNIFORM_MIN) return { ok: false, reason: 'background_not_uniform' };
  const tol = Math.min(L.BG_TOL_MAX, Math.max(L.BG_TOL_MIN, 3 * bs.noise + 12));
  const dist = new Float32Array(n);
  for (let i = 0; i < n; i++) {
    dist[i] = Math.max(Math.abs(img.rgb[i * 3] - bs.bg[0]), Math.abs(img.rgb[i * 3 + 1] - bs.bg[1]), Math.abs(img.rgb[i * 3 + 2] - bs.bg[2]));
  }
  const back = new Uint8Array(n), queue = new Int32Array(n);
  let qh = 0, qt = 0;
  for (const i of idx) if (dist[i] <= tol && !back[i]) { back[i] = 1; queue[qt++] = i; }
  while (qh < qt) {
    const i = queue[qh++], x = i % w;
    const nb = [x > 0 ? i - 1 : -1, x < w - 1 ? i + 1 : -1, i - w, i + w];
    for (const j of nb) if (j >= 0 && j < n && !back[j] && dist[j] <= tol) { back[j] = 1; queue[qt++] = j; }
  }
  const alpha = new Uint8Array(n);
  for (let i = 0; i < n; i++) alpha[i] = back[i] ? 0 : 255;
  const share = shareOf(alpha);
  if (share < L.SUBJECT_MIN) return { ok: false, reason: 'subject_not_found' };
  if (share > L.SUBJECT_MAX) return { ok: false, reason: 'subject_fills_frame' };
  //  لبهٔ کالا: پیکسلِ کالا با همسایهٔ زمینه؛ «کم‌تضاد» اگر بیشترین فاصله از رنگِ زمینه در ۵×۵ ِ دورش کم‌تر از
  //  WEAK_EDGE_K برابرِ آستانه باشد (شیشه، پلاستیکِ شفاف، مو، بستهٔ هم‌رنگِ زمینه) — نه پیکسلِ گذارِ خودِ لبه
  let edge = 0, weak = 0;
  for (let i = 0; i < n; i++) {
    if (back[i]) continue;
    const x = i % w, y = (i / w) | 0;
    if ((x > 0 && back[i - 1]) || (x < w - 1 && back[i + 1]) || (i >= w && back[i - w]) || (i + w < n && back[i + w])) {
      edge++;
      let peak = 0;
      for (let dy = -2; dy <= 2; dy++) for (let dx = -2; dx <= 2; dx++) {
        const xx = x + dx, yy = y + dy;
        if (xx >= 0 && yy >= 0 && xx < w && yy < h) peak = Math.max(peak, dist[yy * w + xx]);
      }
      if (peak < L.WEAK_EDGE_K * tol) weak++;
    }
  }
  //  نرم کردنِ ۱ پیکسلیِ لبه (جعبهٔ ۳×۳) — فقط کنارِ مرز تغییر می‌کند
  const soft = Uint8Array.from(alpha);
  for (let y = 1; y < h - 1; y++) for (let x = 1; x < w - 1; x++) {
    const i = y * w + x;
    let s = 0;
    for (let dy = -1; dy <= 1; dy++) for (let dx = -1; dx <= 1; dx++) s += alpha[i + dy * w + dx];
    if (s !== 0 && s !== 9 * 255) soft[i] = Math.round(s / 9);
  }
  return { ok: true, source: 'edge_fill', alpha: soft, bbox: bbox(alpha), share, weak: edge ? weak / edge : 0, bg: bs.bg, tolerance: tol };
}

function whiteBackground(img, mask) {
  const n = img.w * img.h;
  for (let i = 0; i < n; i++) {
    const a = mask.alpha[i] / 255;
    if (a >= 1) continue;
    for (let c = 0; c < 3; c++) img.rgb[i * 3 + c] = clamp8(img.rgb[i * 3 + c] * a + 255 * (1 - a));
  }
  img.alpha = null;
}

/** سایهٔ تماسیِ بیضی زیرِ کالا — فقط روی پیکسل‌های زمینه (alpha < ۱)؛ کالا دست نمی‌خورد */
function shadow(img, mask) {
  const { w, h } = img, b = mask.bbox;
  const cx = (b.x0 + b.x1) / 2, cy = b.y1, rx = Math.max(4, 0.45 * (b.x1 - b.x0 + 1)), ry = Math.max(3, 0.05 * (b.y1 - b.y0 + 1));
  const yEnd = Math.min(h - 1, Math.ceil(cy + 1.5 * ry));
  for (let y = Math.max(0, Math.floor(cy - ry)); y <= yEnd; y++) {
    for (let x = Math.max(0, Math.floor(cx - 1.5 * rx)); x <= Math.min(w - 1, Math.ceil(cx + 1.5 * rx)); x++) {
      const i = y * w + x, a = mask.alpha[i] / 255;
      if (a >= 1) continue;
      const e = ((x - cx) / rx) ** 2 + ((y - cy) / ry) ** 2;
      const k = 1 - LIMITS.SHADOW_ALPHA * Math.exp(-2 * e) * (1 - a);
      for (let c = 0; c < 3; c++) img.rgb[i * 3 + c] = clamp8(img.rgb[i * 3 + c] * k);
    }
  }
  return { status: 'applied', params: { strength: LIMITS.SHADOW_ALPHA }, bottom: yEnd };
}

/** قابِ تازه: مرزِ کالا + حاشیهٔ امن، بزرگ‌شده تا نسبتِ خواسته؛ بیرون از عکس با رنگِ fill (هرگز بریدنِ کالا) */
function frameOf(w, h, box, aspect) {
  let x0 = box.x0, y0 = box.y0, x1 = box.x1 + 1, y1 = box.y1 + 1;
  if (box.margin) {
    const m = Math.round(LIMITS.MARGIN * Math.max(x1 - x0, y1 - y0));
    x0 -= m; y0 -= m; x1 += m; y1 += m;
  }
  let fw = x1 - x0, fh = y1 - y0;
  if (aspect) {
    if (fw / fh < aspect) { const nw = Math.ceil(fh * aspect); x0 -= Math.floor((nw - fw) / 2); fw = nw; }
    else { const nh = Math.ceil(fw / aspect); y0 -= Math.floor((nh - fh) / 2); fh = nh; }
  }
  return { x0, y0, w: fw, h: fh };
}

function cropTo(img, f, fill) {
  const rgb = new Uint8Array(f.w * f.h * 3), alpha = img.alpha ? new Uint8Array(f.w * f.h) : null;
  for (let y = 0; y < f.h; y++) for (let x = 0; x < f.w; x++) {
    const sx = f.x0 + x, sy = f.y0 + y, d = y * f.w + x, inside = sx >= 0 && sy >= 0 && sx < img.w && sy < img.h;
    for (let c = 0; c < 3; c++) rgb[d * 3 + c] = inside ? img.rgb[(sy * img.w + sx) * 3 + c] : fill[c];
    if (alpha) alpha[d] = inside ? img.alpha[sy * img.w + sx] : 0;
  }
  return { w: f.w, h: f.h, rgb, alpha };
}

/**
 * وفاداری (IMG-06، IMG-PRO-08): شباهتِ ساختار (کسینوسِ اندازهٔ گرادیانِ روشنایی) میانِ اصلِ هم‌تراز و خروجی،
 * فقط درونِ مرزِ کالا؛ کلّی و کم‌ترینِ خانه‌های ۸×۸. منحنیِ نور و ضریبِ رنگ کسینوس را تقریباً عوض نمی‌کنند؛
 * نوشته/لوگوی عوض‌شده، پاک‌شده یا ساختگی خانهٔ خودش را پایین می‌آورد. یک‌رنگ/بی‌کالا ⇒ ۱ (چیزی برای سنجش نیست).
 */
function fidelity(a, b, alpha) {
  const { w, h } = a, ya = lumaPlane(a), yb = lumaPlane(b);
  const grad = (Y, i) => {
    const x = i % w, y = (i / w) | 0;
    const gx = Y[y * w + Math.min(w - 1, x + 1)] - Y[y * w + Math.max(0, x - 1)];
    const gy = Y[Math.min(h - 1, y + 1) * w + x] - Y[Math.max(0, y - 1) * w + x];
    return Math.sqrt(gx * gx + gy * gy);
  };
  const T = 8, tiles = Array.from({ length: T * T }, () => [0, 0, 0]);
  let ab = 0, aa = 0, bb = 0;
  for (let i = 0; i < w * h; i++) {
    if (alpha && alpha[i] < 128) continue;
    const ga = grad(ya, i), gb = grad(yb, i);
    ab += ga * gb; aa += ga * ga; bb += gb * gb;
    const t = tiles[Math.min(T - 1, Math.floor(((i / w) | 0) * T / h)) * T + Math.min(T - 1, Math.floor((i % w) * T / w))];
    t[0] += ga * gb; t[1] += ga * ga; t[2] += gb * gb;
  }
  const cos = (x, y, z) => (y + z < 1e-6 ? 1 : y < 1e-6 || z < 1e-6 ? 0 : x / Math.sqrt(y * z));
  //  خانهٔ بی ساختار در هر دو (زمینهٔ صاف) سنجیده نمی‌شود؛ ساختارِ تازه یا گم‌شده ⇒ ۰
  const energy = (aa + bb) / (T * T);
  let tileMin = 1;
  for (const t of tiles) if (t[1] + t[2] > 0.05 * energy) tileMin = Math.min(tileMin, cos(t[0], t[1], t[2]));
  return { structure: r3(cos(ab, aa, bb)), tile_min: r3(tileMin) };
}

/** JPEG تا زیرِ سقفِ حجم: پله‌های کیفیت، سپس کوچک‌تر (هرگز زیرِ کفِ ۵۰) */
function encodeWithin(img, maxBytes) {
  let cur = img;
  for (let round = 0; round < 4; round++) {
    for (const q of LIMITS.QUALITIES) {
      const buf = codec.encodeJpeg(cur, q);
      if (buf.length <= maxBytes) return { buf, img: cur, quality: q };
    }
    cur = codec.resize(cur, Math.floor(Math.max(cur.w, cur.h) * 0.8));
  }
  throw new ApiError(422, 'output_too_large', 'خروجی در سقفِ حجمِ عکس جا نشد');
}

/**
 * یک عکس ⇒ نسخهٔ پردازش‌شده. `info` = ‎image-bytes.inspect‎ ِ همان بایت‌ها (نوع و جهت از محتوا).
 * خطاها همیشه `ApiError` با کدِ پایدار: ‎unsupported_format‎ (WebP، progressive، …)، ‎output_too_large‎.
 */
function render(buf, info, request, maxBytes) {
  //  همیشه از همان درِ بسته (گزینه‌های کاملِ ذخیره‌شده دوباره همان می‌شوند)
  const { preset, options: o, label } = normalize(request);
  const dec = codec.decode(buf, info);
  if (!dec.rgb) {
    const e = new ApiError(422, 'unsupported_format', 'این عکس روی سرور پردازش‌شدنی نیست (WebP، progressive یا خراب)');
    e.reason = dec.reason;
    throw e;
  }
  const base = codec.resize(codec.orient(dec, info.orientation), LIMITS.WORK_SIDE);
  const img = copy(base), before = metricsOf(base), steps = {}, risk = [];
  const step = (id, on, fn) => { steps[id] = on ? fn() : { status: 'off' }; return steps[id]; };

  const wb = step('white_balance', o.white_balance, () => whiteBalance(img));
  if (wb.shift > LIMITS.COLOR_SHIFT_MAX) risk.push('color_changed');
  step('light', o.light, () => levels(img));
  step('sharpen', o.sharpen, () => sharpen(img, before));

  //  مرزِ کالا روی همان پیکسل‌های پیش از نور و رنگ (منحنیِ سراسری تضادِ لبه را عوض می‌کند، مرز را نه)
  const needMask = o.background === 'white' || o.crop || o.shadow || ASPECTS[o.aspect];
  const mask = needMask ? subjectMask(base) : null;
  let box = null, fill = [255, 255, 255];
  if (o.background === 'white') {
    step('background', true, () => {
      if (!mask.ok) return { status: 'skipped', reason: mask.reason };
      whiteBackground(img, mask);
      if (mask.source === 'edge_fill' && mask.weak > LIMITS.WEAK_EDGE_MAX) risk.push('uncertain_edges');
      return { status: 'applied', params: { source: mask.source, weak_edges: r3(mask.weak) } };
    });
  } else steps.background = { status: 'off' };
  const white = steps.background.status === 'applied';
  if (!white && mask && mask.ok) fill = borderStats(img, borderIndex(img.w, img.h)).bg.map(Math.round);
  if (mask && mask.ok) box = { ...mask.bbox, margin: true };
  step('shadow', o.shadow, () => {
    if (!white) return { status: 'skipped', reason: 'needs_white_background' };
    const s = shadow(img, mask);
    box.y1 = Math.max(box.y1, s.bottom);
    return { status: s.status, params: s.params };
  });

  //  کادر: با مرزِ کالا ⇒ مرز + حاشیه تا نسبت؛ بی مرز ⇒ فقط اگر زمینه سفید است نسبت با حاشیهٔ سفید، وگرنه همان
  let frame = null;
  const aspect = ASPECTS[o.aspect];
  if (o.crop && box) frame = frameOf(img.w, img.h, box, aspect);
  else if (aspect && (white || (mask && mask.ok))) frame = frameOf(img.w, img.h, { x0: 0, y0: 0, x1: img.w - 1, y1: img.h - 1 }, aspect);
  steps.frame = !o.crop && !aspect ? { status: 'off' }
    : frame ? { status: 'applied', params: { crop: !!(o.crop && box), aspect: o.aspect, x: frame.x0, y: frame.y0, w: frame.w, h: frame.h } }
      : { status: 'skipped', reason: mask && !mask.ok ? mask.reason : 'needs_subject' };
  let out = frame ? cropTo(img, frame, fill) : img;
  let ref = frame ? cropTo(base, frame, mask && mask.ok ? mask.bg.map(Math.round) : fill) : base;
  let subj = mask && mask.ok ? (frame ? cropTo({ ...img, alpha: mask.alpha }, frame, fill).alpha : mask.alpha) : null;
  if (Math.max(out.w, out.h) > LIMITS.WORK_SIDE) {
    //  قابِ بزرگ‌تر از عکس (حاشیهٔ نسبت) ⇒ هر سه همان کوچک‌سازی تا هم‌تراز بمانند
    const { w, h } = out;
    out = codec.resize(out, LIMITS.WORK_SIDE);
    ref = codec.resize(ref, LIMITS.WORK_SIDE);
    if (subj) subj = codec.resize({ w, h, rgb: new Uint8Array(w * h * 3), alpha: subj }, LIMITS.WORK_SIDE).alpha;
  }
  out.alpha = null;

  const fid = fidelity(ref, out, subj);
  if (fid.structure < LIMITS.STRUCT_MIN || fid.tile_min < LIMITS.TILE_MIN) risk.push('content_changed');
  const after = metricsOf(out);
  let verdict = Object.values(steps).some((s) => s.status === 'applied') ? 'improved' : 'unchanged';
  if (!before || before.luma_std < quality.LIMITS.BLANK_STD || before.blur_px == null || before.blur_px > LIMITS.RESHOOT_ABOVE_PX) {
    verdict = 'reshoot';
    risk.push('reshoot_recommended');
  }
  const enc = encodeWithin(out, maxBytes);
  const derivatives = {};
  const deriv = (side, max) => {
    if (Math.max(enc.img.w, enc.img.h) <= side) return null;
    const small = codec.resize(enc.img, side), k = enc.img.w / enc.img.h;
    if (Math.abs(small.w / small.h - k) / k > 0.01) return null;      //  گردکردنِ ضلعِ خیلی کوتاه ⇒ همان اصل، نه نسبتِ دیگر
    for (const q of LIMITS.QUALITIES) { const b = codec.encodeJpeg(small, q); if (b.length <= max) return b; }
    return null;
  };
  const thumb = deriv(LIMITS.THUMB_SIDE, LIMITS.THUMB_BYTES), display = deriv(LIMITS.DISPLAY_SIDE, maxBytes);
  if (thumb) derivatives.thumb = thumb;
  if (display) derivatives.display = display;
  return {
    buf: enc.buf, width: enc.img.w, height: enc.img.h, derivatives,
    report: {
      studio: 1, preset, label, options: o, steps, verdict, risk: [...new Set(risk)], fidelity: fid,
      mask: mask ? (mask.ok ? { source: mask.source, share: r3(mask.share), weak_edges: r3(mask.weak) } : { reason: mask.reason }) : null,
      before, after, quality: enc.quality, size: [enc.img.w, enc.img.h],
    },
  };
}

/**
 * درِ انتشار (‎image-bank.selectIn‎، عکسِ خودِ دکان): ردیفِ `image_versions` ⇒ همان ردیف، یا خطا.
 * ⛔ نسخهٔ تأییدنشده (خروجیِ استودیو تا «تأیید» ِ دکان، یا ردشده) هرگز عکسِ کالا نمی‌شود؛ تصویرِ تبلیغ
 *    (برچسبِ `promo`، AI-03) هرگز جای عکسِ واقعیِ کالا نمی‌نشیند. نسخه‌های پیشین همه `approved` بودند.
 */
function publishable(v) {
  if (!v) throw new ApiError(404, 'not_found', 'این نسخه نیست');
  if (v.review !== 'approved') throw new ApiError(409, 'version_not_approved', 'این نسخهٔ عکس هنوز تأیید نشده است');
  let label = '';
  try { label = JSON.parse(v.settings || '{}').label || ''; } catch { label = ''; }
  if (label === 'promo') throw new ApiError(409, 'promo_not_product_photo', 'تصویرِ تبلیغ جای عکسِ واقعیِ کالا نمی‌نشیند');
  return v;
}

module.exports = { normalize, render, fidelity, publishable, subjectMask, whiteBalance, levels, shadow, frameOf, LIMITS, PRESETS, ASPECTS };
