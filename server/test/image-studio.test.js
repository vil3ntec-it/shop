'use strict';
/**
 * استودیوی عکسِ کالا — پردازشِ محلی و قطعی (AI-02، AI-03، IMG-04…06، IMG-PRO-01…04، IMG-PRO-08؛ §۳۴.۱، §۳۶.۴–۵).
 * بی پایگاه: رمزگشا/رمزگذار (‎lib/image-codec.js‎) و زنجیره (‎lib/image-studio.js‎) روی عکس‌های مصنوعیِ قطعی.
 * ⛔ آستانه‌ها روی همین مجموعهٔ مصنوعی آزموده شده‌اند، نه عکسِ واقعیِ دکان‌ها (§۳۶.۱۰؛ GATE-NEW-04 بیرونی).
 */
const test = require('node:test');
const assert = require('node:assert/strict');
const F = require('./image-fixtures');
const bytes = require('../src/lib/image-bytes');
const codec = require('../src/lib/image-codec');
const studio = require('../src/lib/image-studio');

const L = studio.LIMITS;
const MAX = 600 * 1024;
const W = 900, H = 700;
const RED = [200, 30, 40];
/** کالا: جعبهٔ قرمز روی زمینهٔ ۲۴۵ با برچسبِ سفیدِ نوشته‌دار درونش (نوشتهٔ سفید به لبه وصل نیست) */
const product = (x, y) => {
  const u = x / W, v = y / H;
  if (u <= 0.3 || u >= 0.7 || v <= 0.2 || v >= 0.85) return [245, 245, 245];
  if (u > 0.38 && u < 0.62 && v > 0.45 && v < 0.6) {
    const tx = Math.floor((u - 0.38) * 200), ty = Math.floor((v - 0.45) * 200);
    return ty % 6 < 3 && tx % 5 < 3 ? [20, 20, 20] : [250, 250, 250];
  }
  return RED;
};
const cast = (fn, k) => (x, y) => fn(x, y).map((c, i) => c * k[i]);
const blurredRgb = (fn, r) => {
  const ch = [0, 1, 2].map((c) => F.blurred(W, H, (x, y) => fn(x, y)[c], r));
  return (x, y) => ch.map((f) => f(x, y));
};
const jpg = (fn, w = W, h = H) => F.jpegImage(w, h, fn, { quality: 92, color: true });
const render = (buf, req = {}) => studio.render(buf, bytes.inspect(buf), req, MAX);
const decoded = (buf) => codec.decode(buf, bytes.inspect(buf));
const px = (img, x, y) => [0, 1, 2].map((c) => img.rgb[(y * img.w + x) * 3 + c]);
const hueSat = ([r, g, b]) => {
  const mx = Math.max(r, g, b), mn = Math.min(r, g, b), d = mx - mn;
  let hh = 0;
  if (d) hh = mx === r ? ((g - b) / d) % 6 : mx === g ? (b - r) / d + 2 : (r - g) / d + 4;
  return { h: (hh * 60 + 360) % 360, s: mx ? d / mx : 0 };
};

/* ------------------------------------------------------------ رمزگشا/رمزگذار */

test('JPEG ِ ساختهٔ سرور: رمزگشاییِ دوباره نزدیکِ اصل، بی هیچ فراداده و با ابعادِ درست', () => {
  const w = 333, h = 251, rgb = new Uint8Array(w * h * 3);
  for (let y = 0; y < h; y++) for (let x = 0; x < w; x++) {
    const n = (y * w + x) * 3;
    rgb[n] = 128 + 100 * Math.sin(x / 37); rgb[n + 1] = 128 + 90 * Math.cos(y / 53); rgb[n + 2] = 128 + 60 * Math.sin((x + y) / 71);
  }
  const buf = codec.encodeJpeg({ w, h, rgb }, 88);
  const info = bytes.inspect(buf);
  assert.deepEqual([info.mime, info.width, info.height, info.orientation], ['image/jpeg', w, h, 0]);
  assert.equal(bytes.stripMetadata(buf).length, buf.length, 'هیچ EXIF/XMP/ICC برای برداشتن نیست');
  const d = codec.decode(buf, info);
  let se = 0;
  for (let i = 0; i < rgb.length; i++) se += (d.rgb[i] - rgb[i]) ** 2;
  assert.ok(10 * Math.log10(65025 / (se / rgb.length)) > 38, 'PSNR بالای ۳۸');
});

test('رمزگشا: JPEG ِ رنگیِ ۴:۲:۰ با RST، PNG ِ ۱۶ بیتیِ شفاف؛ WebP و دادهٔ خراب ⇒ دلیل، نه خطا', () => {
  const j = F.jpegImage(120, 90, (x, y) => [x * 2, y * 2, 128], { quality: 95, color: true, restart: 7 });
  const d = codec.decode(j, bytes.inspect(j));
  const [r, g, b] = px(d, 60, 45);
  assert.ok(Math.abs(r - 120) <= 6 && Math.abs(g - 90) <= 6 && Math.abs(b - 128) <= 6, `${r},${g},${b}`);
  const p = F.pngImage(50, 40, (x, y) => [x * 5, y * 6, 77, x < 10 ? 0 : 255], { mode: 'rgba', depth: 16 });
  const pd = codec.decode(p, bytes.inspect(p));
  assert.deepEqual(px(pd, 20, 10), [100, 60, 77]);
  assert.equal(pd.alpha[10 * 50 + 5], 0);
  assert.equal(pd.alpha[10 * 50 + 20], 255);
  assert.equal(codec.decode(F.webp(64, 64), bytes.inspect(F.webp(64, 64))).reason, 'format');
  const broken = Buffer.from(j);
  broken.fill(0xff, broken.length - 300, broken.length - 2);
  assert.equal(codec.decode(broken, bytes.inspect(j)).reason, 'undecodable');
});

test('جهتِ EXIF روی پیکسل (۶ = ۹۰° ساعتگرد، ۸ = پادساعتگرد) و کوچک‌سازی هرگز بزرگ نمی‌کند', () => {
  const w = 4, h = 2, rgb = new Uint8Array(w * h * 3);
  rgb[0] = 255;                                   // گوشهٔ بالا-چپ قرمز
  const six = codec.orient({ w, h, rgb, alpha: null }, 6), eight = codec.orient({ w, h, rgb, alpha: null }, 8);
  assert.deepEqual([six.w, six.h, eight.w, eight.h], [2, 4, 2, 4]);
  assert.equal(px(six, 1, 0)[0], 255, '۶: بالا-چپ ⇒ بالا-راست');
  assert.equal(px(eight, 0, 3)[0], 255, '۸: بالا-چپ ⇒ پایین-چپ');
  const small = { w: 300, h: 200, rgb: new Uint8Array(300 * 200 * 3), alpha: null };
  assert.equal(codec.resize(small, 1600), small);
  const half = codec.resize(small, 150);
  assert.deepEqual([half.w, half.h], [150, 100]);
});

test('درخواست: preset ِ بسته، گزینهٔ ناشناخته/نادرست ⇒ ۴۰۰، سایه فقط با زمینهٔ سفید، برچسب فقط از preset', () => {
  assert.deepEqual(studio.normalize({}).options, studio.PRESETS.catalog);
  assert.equal(studio.normalize({ preset: 'ad' }).label, 'promo');
  assert.equal(studio.normalize({ preset: 'enhance', options: { aspect: '4:3' } }).label, 'product');
  for (const [body, code] of [
    [{ preset: 'poster' }, 'bad_preset'], [{ preset: '__proto__' }, 'bad_preset'], [{ options: { label: 'promo' } }, 'bad_option'],
    [{ options: { light: 'yes' } }, 'bad_option'], [{ options: { aspect: '2:1' } }, 'bad_option'],
    [{ options: { background: 'black' } }, 'bad_option'], [{ preset: 'enhance', options: { shadow: true } }, 'shadow_needs_white'],
  ]) {
    assert.throws(() => studio.normalize(body), (e) => e.status === 400 && e.code === code, JSON.stringify(body));
  }
});

/* ------------------------------------------------------------ زنجیره */

test('IMG-PRO-02: نورِ زرد اصلاح می‌شود ولی رنگِ واقعیِ کالا همان می‌ماند (نه رنگِ تبلیغاتیِ دلخواه)', () => {
  const r = render(jpg(cast(product, [1, 0.93, 0.75])), { preset: 'enhance' });
  assert.equal(r.report.steps.white_balance.status, 'applied');
  assert.equal(r.report.steps.white_balance.params.source, 'background');
  for (const g of r.report.steps.white_balance.params.gains) assert.ok(g >= L.WB_GAIN_MIN && g <= L.WB_GAIN_MAX);
  const out = decoded(r.buf);
  const bg = px(out, 20, 20);
  assert.ok(Math.max(...bg) - Math.min(...bg) <= 6, `زمینه خنثی شد: ${bg}`);
  const red = px(out, Math.round(W * 0.34), Math.round(H * 0.3)), truth = hueSat(RED), got = hueSat(red);
  const dh = Math.min(Math.abs(got.h - truth.h), 360 - Math.abs(got.h - truth.h));
  assert.ok(dh <= 8, `فامِ کالا همان (${got.h} در برابرِ ${truth.h})`);
  assert.ok(got.s <= truth.s + 0.08, `اشباع بیشتر از رنگِ واقعی نشد (${got.s})`);
  //  ضریبِ بزرگِ رنگ ⇒ بازبینِ انسانی رنگِ واقعی را می‌سنجد
  assert.ok(r.report.risk.includes('color_changed'));
  const mild = render(jpg(cast(product, [1, 0.97, 0.92])), { preset: 'enhance' });
  assert.ok(!mild.report.risk.includes('color_changed'), JSON.stringify(mild.report.steps.white_balance));
});

test('IMG-04 و IMG-PRO-01: تارِ ملایم ⇒ وضوحِ ملایم و «بهتر شد»؛ تارِ واقعی ⇒ «عکس‌برداریِ دوباره»، بی نوشتهٔ ساختگی', () => {
  const sharp = render(jpg(product));
  assert.equal(sharp.report.steps.sharpen.status, 'skipped');
  assert.equal(sharp.report.steps.sharpen.reason, 'sharp_enough');
  const mild = render(jpg(blurredRgb(product, 1)));
  assert.equal(mild.report.steps.sharpen.status, 'applied');
  assert.equal(mild.report.verdict, 'improved');
  assert.ok(mild.report.before.blur_px > L.SHARPEN_ABOVE_PX && mild.report.before.blur_px <= L.RESHOOT_ABOVE_PX, JSON.stringify(mild.report.before));
  assert.ok(mild.report.after.blur_px < mild.report.before.blur_px, 'قبل/بعد سنجیده و گزارش شد');
  const heavy = render(jpg(blurredRgb(product, 3)));
  assert.equal(heavy.report.verdict, 'reshoot');
  assert.ok(heavy.report.risk.includes('reshoot_recommended'));
  //  Unsharp Mask فقط لبه‌های موجود را تیز می‌کند: ساختارِ خروجی همان ساختارِ ورودی است
  assert.ok(heavy.report.fidelity.structure >= L.STRUCT_MIN && heavy.report.fidelity.tile_min >= L.TILE_MIN, JSON.stringify(heavy.report.fidelity));
  assert.ok(!heavy.report.risk.includes('content_changed'));
});

test('کادرِ کاتالوگ: مربع، زمینهٔ سفید، کالا با حاشیهٔ امن و هرگز بریده؛ نوشتهٔ سفیدِ درونِ کالا می‌ماند', () => {
  const r = render(jpg(product));
  assert.equal(r.width, r.height);
  assert.deepEqual(r.report.risk, []);
  assert.equal(r.report.mask.source, 'edge_fill');
  const out = decoded(r.buf);
  for (let k = 0; k < out.w; k += 7) {
    for (const [x, y] of [[k, 0], [k, out.h - 1], [0, k], [out.w - 1, k]]) assert.ok(Math.min(...px(out, x, y)) >= 245, `لبه سفید (${x},${y})`);
  }
  //  بیرونی‌ترین ردیف/ستونِ قرمز دست‌کم به اندازهٔ حاشیه از لبه فاصله دارد
  let minX = out.w, maxX = 0, minY = out.h, maxY = 0;
  for (let y = 0; y < out.h; y++) for (let x = 0; x < out.w; x++) {
    const [rr, g, b] = px(out, x, y);
    if (rr > 150 && g < 80 && b < 90) { minX = Math.min(minX, x); maxX = Math.max(maxX, x); minY = Math.min(minY, y); maxY = Math.max(maxY, y); }
  }
  const m = Math.floor(L.MARGIN * Math.max(maxX - minX, maxY - minY)) - 2;
  assert.ok(minY >= m && out.h - 1 - maxY >= m && minX >= 0 && out.w - 1 - maxX >= 0, `حاشیه ${minX},${minY},${maxX},${maxY} / ${out.w}`);
  assert.ok(Math.abs((maxX - minX) / (maxY - minY) - (0.4 * W) / (0.65 * H)) < 0.03, 'نسبتِ خودِ کالا عوض نشد');
  //  برچسبِ سفید با نوشته درونِ کالا: سفید و نوشته‌دار ماند (به زمینه وصل نیست)
  const cx = Math.round((minX + maxX) / 2), cy = Math.round(minY + (maxY - minY) * ((0.525 - 0.2) / 0.65));
  let dark = 0, light = 0;
  for (let y = cy - 12; y <= cy + 12; y++) for (let x = cx - 30; x <= cx + 30; x++) { const v = px(out, x, y)[0]; if (v < 90) dark++; if (v > 200) light++; }
  assert.ok(dark > 100 && light > 100, `برچسب ماند (${dark}/${light})`);
  assert.ok(r.derivatives.thumb === undefined || Math.max(...['width', 'height'].map((k) => bytes.inspect(r.derivatives.thumb)[k])) <= 400);
});

test('IMG-PRO-04: سایهٔ طبیعی فقط بیرون از مرزِ کالا، زیرِ آن، و خاموش‌شدنی', () => {
  const w = 200, h = 160, rgb = new Uint8Array(w * h * 3).fill(255), alpha = new Uint8Array(w * h);
  for (let y = 40; y < 120; y++) for (let x = 60; x < 140; x++) { alpha[y * w + x] = 255; rgb.fill(90, (y * w + x) * 3, (y * w + x) * 3 + 3); }
  const before = Uint8Array.from(rgb);
  const s = studio.shadow({ w, h, rgb, alpha: null }, { alpha, bbox: { x0: 60, y0: 40, x1: 139, y1: 119 } });
  let changed = 0;
  for (let i = 0; i < w * h; i++) {
    const diff = [0, 1, 2].some((c) => rgb[i * 3 + c] !== before[i * 3 + c]);
    if (!diff) continue;
    changed++;
    assert.equal(alpha[i], 0, 'پیکسلِ کالا دست نخورد');
    assert.ok(Math.floor(i / w) >= 119 - 4, 'سایه فقط کنارِ پایینِ کالا');
    assert.ok(rgb[i * 3] < 255 && rgb[i * 3] >= 255 * (1 - L.SHADOW_ALPHA) - 1, 'سایهٔ ملایم، نه لکه');
  }
  assert.ok(changed > 50, 'سایه ساخته شد');
  assert.ok(s.bottom > 119);
  const on = render(jpg(product), { options: { shadow: true } }), off = render(jpg(product));
  assert.equal(on.report.steps.shadow.status, 'applied');
  assert.equal(off.report.steps.shadow.status, 'off');
});

test('IMG-PRO-03 و IMG-05: لبهٔ کم‌تضادِ بطریِ شفاف ⇒ «لبهٔ نامطمئن» و بازبینی؛ زمینهٔ شلوغ ⇒ حذف نمی‌شود', () => {
  const glass = (x, y) => {
    const u = x / W, v = y / H, inB = u > 0.4 && u < 0.6 && v > 0.15 && v < 0.9;
    const edge = inB && (u < 0.41 || u > 0.59 || v < 0.16 || v > 0.89);
    return edge ? [205, 215, 225] : inB ? [235, 238, 242] : [245, 245, 245];
  };
  const g = render(jpg(glass));
  assert.equal(g.report.steps.background.status, 'applied');
  assert.ok(g.report.risk.includes('uncertain_edges'), JSON.stringify(g.report.mask));
  //  درونِ بطری (محتوا و بازتاب) پاک نشد: به لبهٔ عکس وصل نیست
  const out = decoded(g.buf);
  const inside = px(out, Math.round(out.w / 2), Math.round(out.h / 2));
  assert.ok(inside[2] - inside[0] >= 4, `رنگِ درونِ بطری ماند: ${inside}`);
  const rnd = F.rng(5), noise = Array.from({ length: W * H }, () => rnd() * 255);
  const busy = render(jpg((x, y) => (product(x, y)[0] === 245 ? [noise[y * W + x], 120, 200 - noise[y * W + x] / 2] : product(x, y))));
  assert.equal(busy.report.steps.background.status, 'skipped');
  assert.equal(busy.report.steps.background.reason, 'background_not_uniform');
  assert.equal(busy.report.steps.frame.status, 'skipped');
  assert.deepEqual([busy.width, busy.height], [W, H], 'نه بریدن، نه زمینهٔ ساختگی');
});

test('IMG-06 و IMG-PRO-08: نوشته/لوگوی عوض‌شده در محدودهٔ کالا ⇒ content_changed؛ نور و رنگِ سراسری ⇒ نه', () => {
  const base = decoded(jpg(product));
  const changed = { ...base, rgb: Uint8Array.from(base.rgb) }, toned = { ...base, rgb: Uint8Array.from(base.rgb) };
  //  «مدل» نوشتهٔ برچسب را عوض کرد: نوارهای افقی به‌جای نقطه‌ها
  for (let y = Math.round(H * 0.46); y < Math.round(H * 0.59); y++) for (let x = Math.round(W * 0.39); x < Math.round(W * 0.61); x++) {
    changed.rgb.fill(y % 9 < 2 ? 30 : 250, (y * W + x) * 3, (y * W + x) * 3 + 3);
  }
  for (let i = 0; i < toned.rgb.length; i++) toned.rgb[i] = Math.min(255, Math.round(255 * (toned.rgb[i] / 255) ** 0.8 * 1.05));
  const mask = studio.subjectMask(base);
  const bad = studio.fidelity(base, changed, mask.alpha), ok = studio.fidelity(base, toned, mask.alpha);
  assert.ok(bad.tile_min < L.TILE_MIN, `تغییرِ نوشته دیده شد: ${JSON.stringify(bad)}`);
  assert.ok(ok.structure >= L.STRUCT_MIN && ok.tile_min >= L.TILE_MIN, `منحنیِ نور تغییرِ محتوا نیست: ${JSON.stringify(ok)}`);
  //  پاک شدنِ نوشته هم (ساختارِ گم‌شده)
  const erased = { ...base, rgb: Uint8Array.from(base.rgb) };
  for (let y = Math.round(H * 0.46); y < Math.round(H * 0.59); y++) erased.rgb.fill(250, (y * W + Math.round(W * 0.39)) * 3, (y * W + Math.round(W * 0.61)) * 3);
  assert.ok(studio.fidelity(base, erased, mask.alpha).tile_min < L.TILE_MIN);
});

test('AI-03: تصویرِ تبلیغ برچسبِ جدای promo، نسبتِ ۱۶:۹ و سایه دارد؛ نسخهٔ فهرست ≤ ۴۰۰ پیکسل', () => {
  const r = render(jpg(product), { preset: 'ad' });
  assert.equal(r.report.label, 'promo');
  assert.ok(Math.abs(r.width / r.height - 16 / 9) < 0.01, `${r.width}×${r.height}`);
  assert.equal(r.report.steps.shadow.status, 'applied');
  const t = bytes.inspect(r.derivatives.thumb);
  assert.ok(Math.max(t.width, t.height) <= 400 && Math.abs(t.width / t.height - r.width / r.height) / (r.width / r.height) <= 0.01);
  assert.throws(() => studio.publishable({ review: 'approved', settings: JSON.stringify({ label: 'promo' }) }), (e) => e.code === 'promo_not_product_photo');
  assert.throws(() => studio.publishable({ review: 'needs_review', settings: '{}' }), (e) => e.code === 'version_not_approved');
  assert.throws(() => studio.publishable({ review: 'rejected', settings: '{}' }), (e) => e.code === 'version_not_approved');
  assert.throws(() => studio.publishable(null), (e) => e.status === 404);
  assert.ok(studio.publishable({ review: 'approved', settings: '{}' }));
});

test('جهتِ EXIF ِ ورودی روی پیکسل می‌نشیند و خروجی بی EXIF است؛ عکسِ تاریک روشن می‌شود', () => {
  const rotated = bytes.withOrientation(jpg(product), 6);
  assert.equal(bytes.inspect(rotated).orientation, 6);
  const r = render(rotated, { preset: 'enhance' });
  assert.deepEqual([r.width, r.height], [H, W], '۶ ⇒ ضلع‌ها جابه‌جا');
  assert.equal(bytes.inspect(r.buf).orientation, 0);
  const dark = render(jpg(cast(product, [0.4, 0.4, 0.4])), { preset: 'enhance' });
  assert.equal(dark.report.steps.light.status, 'applied');
  const [b0, a0] = [dark.report.before, dark.report.after];
  assert.ok(a0.p95 > b0.p95 + 30 && a0.luma_mean > b0.luma_mean + 20, JSON.stringify([b0, a0]));
  assert.deepEqual(dark.report.risk, []);
});

test('WebP و JPEG ِ progressive پردازش نمی‌شوند: ۴۲۲ ِ unsupported_format (همیشگی، بی تلاشِ دوباره)', () => {
  const webp = F.webpLossy(64, 64);
  assert.throws(() => studio.render(webp, bytes.inspect(webp), {}, MAX), (e) => e.status === 422 && e.code === 'unsupported_format');
});
