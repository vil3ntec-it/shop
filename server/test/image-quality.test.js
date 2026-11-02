'use strict';
/**
 * IMG-15 — معیارهای کیفیتِ عکس (‎lib/image-quality.js‎) روی مجموعهٔ آزمونِ مصنوعی و قطعی.
 *
 *   رمزگشاها: PNG (هر پنج صافی، ۸/۱۶ بیت، شفافی روی سفید) و JPEG ِ baseline در برابرِ
 *   خروجیِ libjpeg: ‎test/fixtures/libjpeg/*.jpg‎ با Pillow 12.3 ساخته شده‌اند (۴:۲:۰ کیفیتِ ۸۰،
 *   ۴:۴:۴ با Huffman ِ بهینهٔ کیفیتِ ۶۰، ۴:۲:۲ با RST هر ۳ بلوک کیفیتِ ۸۵، خاکستریِ ۷۰، progressive)
 *   و ‎*.y‎ کانالِ Y ِ همان فایل با `draft('YCbCr')` ِ خودِ libjpeg است.
 *   مجموعه: تیز، تار، تاریک، روشن، یک‌رنگ، پس‌زمینهٔ شلوغ، کوچک، نسبتِ نامعمول، حجیم،
 *   فشرده، تکراری (همان صحنه در اندازه و قالبِ دیگر) و غیرتکراری — هر کدام دقیقاً نشانِ خودش.
 */
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('fs');
const path = require('path');
const zlib = require('zlib');
const Q = require('../src/lib/image-quality');
const bytes = require('../src/lib/image-bytes');
const F = require('./image-fixtures');

const L = Q.LIMITS;
const run = (buf) => Q.analyze(buf, bytes.inspect(buf));
const lumaOf = (v) => (typeof v === 'number' ? v : 0.299 * v[0] + 0.587 * v[1] + 0.114 * v[2]);

const scene = F.scene;
const blurred = F.blurred;

//  مجموعهٔ آزمون — نام ⇒ [بایت‌ها، نشان‌های مورد انتظار]
const R = F.rng(42);
const SET = {
  sharp: [F.pngImage(800, 600, scene(800, 600)), []],
  blurred: [F.pngImage(800, 600, blurred(800, 600, scene(800, 600), 4)), ['blurry']],
  dark: [F.pngImage(800, 600, scene(800, 600, { bg: 25, lo: 0, hi: 60 })), ['too_dark']],
  bright: [F.pngImage(800, 600, scene(800, 600, { bg: 250, lo: 218, hi: 255, cells: 100 })), ['too_bright']],
  blank: [F.pngImage(800, 600, () => 128), ['blank']],
  busy: [F.pngImage(800, 600, scene(800, 600, { box: [0, 0, 1, 1] })), ['busy_background']],
  tiny: [F.pngImage(200, 150, scene(200, 150)), []],                       // too_small را checkOriginal می‌زند
  aspect: [F.pngImage(1200, 300, scene(1200, 300)), ['aspect_extreme']],
  heavy: [F.pngImage(600, 600, (x, y) => (x > 100 && x < 500 && y > 100 && y < 500 ? [R() * 255, R() * 255, R() * 255] : 255),
    { mode: 'rgb' }), ['heavy_file']],
  lowq: [F.jpegImage(800, 600, scene(800, 600), { quality: 15 }), ['over_compressed']],
  dupJpeg: [F.jpegImage(640, 480, scene(640, 480), { quality: 80, color: true }), []],
  other: [F.pngImage(800, 600, scene(800, 600, { box: [0.05, 0.1, 0.45, 0.5] })), []],
};

test('IMG-15 رمزگشای PNG: هر پنج صافی، خاکستری/RGB/RGBA و ۱۶ بیت ⇒ روشناییِ دقیق؛ شفافی روی سفید', () => {
  const fn = (x, y) => [(x * 37) % 256, (y * 53) % 256, (x * y) % 256];
  for (const [mode, depth] of [['gray', 8], ['rgb', 8], ['rgba', 8], ['gray', 16], ['rgb', 16]]) {
    const px = mode === 'gray' ? (x, y) => (x * 7 + y * 3) % 256 : mode === 'rgba' ? (x, y) => [...fn(x, y), (x * 11) % 256] : fn;
    const d = Q.pngLuma(F.pngImage(23, 17, px, { mode, depth }));
    assert.deepEqual([d.w, d.h], [23, 17]);
    for (let y = 0; y < 17; y++) for (let x = 0; x < 23; x++) {
      const v = px(x, y);
      const a = mode === 'rgba' ? v[3] : 255;
      const want = Math.round((lumaOf(v) * a + 255 * (255 - a)) / 255);
      assert.equal(d.luma[y * 23 + x], want, `${mode}/${depth} @${x},${y}`);
    }
  }
});

test('IMG-15 رمزگشای JPEG در برابرِ libjpeg (Pillow): ۴:۲:۰، ۴:۴:۴ ِ بهینه، RST، خاکستری ⇒ اختلاف ≤ ۱؛ progressive سنجیده نمی‌شود', () => {
  const dir = path.join(__dirname, 'fixtures', 'libjpeg');
  const want = { 420: 80, '444o': 60, rst: 85, gray: 70 };
  for (const [name, q] of Object.entries(want)) {
    const jpg = fs.readFileSync(path.join(dir, name + '.jpg')), y = fs.readFileSync(path.join(dir, name + '.y'));
    const d = Q.jpegLuma(jpg);
    assert.ok(d, name);
    assert.deepEqual([d.w, d.h, d.luma.length], [61, 45, y.length]);
    let worst = 0;
    for (let i = 0; i < y.length; i++) worst = Math.max(worst, Math.abs(d.luma[i] - y[i]));
    assert.ok(worst <= 1, `${name}: ${worst}`);
    assert.equal(Q.jpegQuality(jpg), q, name);
  }
  assert.ok(fs.readFileSync(path.join(dir, 'rst.jpg')).includes(Buffer.from([0xff, 0xdd])), 'rst.jpg باید DRI داشته باشد');
  const prog = fs.readFileSync(path.join(dir, 'prog.jpg'));
  assert.equal(Q.jpegLuma(prog), null);
  assert.equal(Q.jpegQuality(prog), 80);
  const a = run(prog);
  assert.deepEqual([a.metrics.measured, a.metrics.reason, a.flags], [false, 'undecodable', ['quality_unmeasured']]);
});

test('IMG-15 JPEG ِ خودساخته: خاکستری، ۴:۲:۰ و RST ⇒ نزدیک به منبع؛ تخمینِ کیفیت از جدولِ کوانتیزه', () => {
  const fn = (x, y) => [(x * 3) % 256, (y * 5) % 256, 128];
  for (const opt of [{}, { color: true }, { color: true, restart: 2 }, { restart: 3 }]) {
    const d = Q.jpegLuma(F.jpegImage(37, 29, fn, { quality: 95, ...opt }));
    let worst = 0;
    for (let y = 0; y < 29; y++) for (let x = 0; x < 37; x++) worst = Math.max(worst, Math.abs(d.luma[y * 37 + x] - lumaOf(fn(x, y))));
    assert.ok(worst <= 2, JSON.stringify(opt) + ' ' + worst);
  }
  for (const q of [20, 49, 50, 51, 75, 95]) assert.equal(Q.jpegQuality(F.jpegImage(16, 16, fn, { quality: q })), q);
  assert.equal(Q.jpegQuality(F.jpeg(800, 600)), null);           // بی DQT ⇒ تخمین نیست، نه خطا
});

test('IMG-15 مجموعهٔ آزمونِ مصنوعی: هر معیار دقیقاً همان عکسِ خودش را نشان می‌زند', () => {
  const seen = {};
  for (const [name, [buf, want]] of Object.entries(SET)) {
    const a = run(buf);
    seen[name] = a;
    assert.equal(a.metrics.measured, true, name);
    assert.deepEqual(a.flags, want, `${name}: ${JSON.stringify(a.metrics)}`);
  }
  //  مقدارهای سنجه با فاصلهٔ امن از آستانه (نه تصادفاً روی مرز)
  assert.ok(seen.sharp.metrics.sharpness > 10 * L.BLUR_VAR_MIN);
  assert.ok(seen.blurred.metrics.sharpness < L.BLUR_VAR_MIN / 5);
  assert.ok(seen.bright.metrics.sharpness > 2 * L.BLUR_VAR_MIN);
  assert.ok(seen.dark.metrics.sharpness > 2 * L.BLUR_VAR_MIN);
  assert.deepEqual([seen.dark.metrics.p95, seen.bright.metrics.p05], [60, 218]);
  assert.equal(seen.lowq.metrics.jpeg_quality, 17);                 // ۱۵ با سقفِ ۲۵۵ ِ جدول ⇒ تخمینِ ۱۷
  assert.equal(seen.dupJpeg.metrics.jpeg_quality, 80);
  assert.ok(seen.heavy.metrics.bytes > L.HEAVY_BYTES && seen.heavy.metrics.bytes_per_pixel > L.HEAVY_BPP);
  assert.equal(seen.blank.dhash, '');                                // یک‌رنگ هش ندارد
  assert.equal(seen.tiny.metrics.scale, 1);
  assert.deepEqual([seen.aspect.metrics.scale, ...seen.aspect.metrics.grid], [2, 600, 150]);   // ۱۲۰۰ > ۱۰۲۴ ⇒ جعبهٔ ۲×۲
});

test('IMG-15 مشابهِ احتمالی (dHash): همان صحنه در اندازه/قالبِ دیگر یا تار ⇒ مشابه؛ صحنهٔ دیگر نه؛ مرزِ همینگ ۶/۷', () => {
  const h = Object.fromEntries(Object.entries(SET).map(([k, [buf]]) => [k, run(buf).dhash]));
  assert.match(h.sharp, /^[0-9a-f]{16}$/);
  for (const k of ['dupJpeg', 'tiny', 'lowq', 'blurred']) assert.ok(Q.isNearDuplicate(h.sharp, h[k]), `${k}: ${Q.hamming(h.sharp, h[k])}`);
  for (const k of ['other', 'dark', 'busy', 'heavy']) assert.ok(!Q.isNearDuplicate(h.sharp, h[k]), `${k}: ${Q.hamming(h.sharp, h[k])}`);
  //  ⚠️ dHash فقط ترکیب‌بندی را می‌بیند (نور را نه): همان جعبهٔ وسط با شطرنجِ ریزتر روی زمینهٔ روشن
  //  (bright) ممکن است «مشابه» شود — برای همین فقط نشانِ «احتمالی» است و تصمیم با بازبین
  assert.equal(Q.isNearDuplicate(h.sharp, h.blank), false);           // بی هش هرگز مشابه نیست
  //  مرز
  assert.equal(L.DUP_HAMMING_MAX, 6);
  assert.equal(Q.hamming('0000000000000000', '000000000000003f'), 6);
  assert.equal(Q.isNearDuplicate('0000000000000000', '000000000000003f'), true);
  assert.equal(Q.isNearDuplicate('0000000000000000', '000000000000007f'), false);
  assert.equal(Q.hamming('ffffffffffffffff', '0000000000000000'), 64);
  assert.equal(Q.isNearDuplicate('-', '-'), false);
});

test('IMG-15 مرزِ هر آستانه: مقدارِ دقیقِ آستانه نشان نمی‌گیرد، یک گام آن‌سو می‌گیرد', () => {
  const base = { aspect: 1, jpeg_quality: 90, bytes: 1000, bytes_per_pixel: 0.1, measured: true,
    luma_std: 50, p05: 10, p95: 240, sharpness: 500, background_std: 5 };
  const f = (o) => Q.flagsFrom({ ...base, ...o });
  assert.deepEqual(f({}), []);
  const cases = [
    ['aspect', L.ASPECT_MAX, L.ASPECT_MAX + 0.01, 'aspect_extreme'],
    ['jpeg_quality', L.JPEG_QUALITY_MIN, L.JPEG_QUALITY_MIN - 1, 'over_compressed'],
    ['sharpness', L.BLUR_VAR_MIN, L.BLUR_VAR_MIN - 0.01, 'blurry'],
    ['p95', L.DARK_P95, L.DARK_P95 - 1, 'too_dark'],
    ['p05', L.BRIGHT_P05, L.BRIGHT_P05 + 1, 'too_bright'],
    ['background_std', L.BG_STD_MAX, L.BG_STD_MAX + 0.01, 'busy_background'],
    ['luma_std', L.BLANK_STD, L.BLANK_STD - 0.01, 'blank'],
  ];
  for (const [k, at, over, flag] of cases) {
    assert.deepEqual(f({ [k]: at }), [], `${k}=${at}`);
    assert.deepEqual(f({ [k]: over }), [flag], `${k}=${over}`);
  }
  //  حجم: هر دو شرط با هم
  assert.deepEqual(f({ bytes: L.HEAVY_BYTES, bytes_per_pixel: 3 }), []);
  assert.deepEqual(f({ bytes: L.HEAVY_BYTES + 1, bytes_per_pixel: 3 }), ['heavy_file']);
  assert.deepEqual(f({ bytes: 5e6, bytes_per_pixel: L.HEAVY_BPP }), []);
  //  یک‌رنگ: تاری و پس‌زمینه بی‌معنا ⇒ فقط blank (نور هنوز سنجیده می‌شود)
  assert.deepEqual(f({ luma_std: 0, sharpness: 0, background_std: 99, p95: 20 }), ['blank', 'too_dark']);
  //  PNG بی کیفیتِ JPEG ⇒ نشانِ فشرده‌سازی هرگز
  assert.deepEqual(f({ jpeg_quality: null }), []);
  //  سنجش‌نشده ⇒ فقط نشان‌های سرآیند + quality_unmeasured
  assert.deepEqual(f({ measured: false, aspect: 5 }), ['aspect_extreme', 'quality_unmeasured']);
  assert.deepEqual([L.ASPECT_MAX, L.JPEG_QUALITY_MIN, L.BLUR_VAR_MIN, L.DARK_P95, L.BRIGHT_P05, L.BG_STD_MAX, L.BLANK_STD,
    L.HEAVY_BYTES, L.HEAVY_BPP], [3, 50, 100, 70, 215, 40, 4, 262144, 1]);
});

test('IMG-15 مرز روی پیکسلِ واقعی: نسبتِ ۳:۱ و صدک‌های ۵ و ۹۵', () => {
  const s = (w, h) => run(F.pngImage(w, h, scene(w, h))).flags;
  assert.deepEqual(s(900, 300), []);
  assert.deepEqual(s(901, 300), ['aspect_extreme']);
  //  ۱۰۰×۱۰۰: رتبهٔ صدکِ ۹۵ = ۹۵۰۰ ⇒ ۹۵۰۰ پیکسلِ v و ۵۰۰ پیکسلِ ۲۵۵ (ستونِ x ≥ ۹۵) ⇒ p95 = v
  const dark = (v) => run(F.pngImage(100, 100, (x) => (x >= 95 ? 255 : v)));
  assert.equal(dark(69).metrics.p95, 69);
  assert.ok(dark(69).flags.includes('too_dark'));
  assert.ok(!dark(70).flags.includes('too_dark'));
  //  رتبهٔ صدکِ ۵ = ۵۰۰ ⇒ ۵۰۰ پیکسلِ v و بقیه ۲۵۵ ⇒ p05 = v
  const bright = (v) => run(F.pngImage(100, 100, (x) => (x < 5 ? v : 255)));
  assert.equal(bright(216).metrics.p05, 216);
  assert.ok(bright(216).flags.includes('too_bright'));
  assert.ok(!bright(215).flags.includes('too_bright'));
});

test('IMG-15 سنجش‌ناپذیر ⇒ quality_unmeasured، نه خطا: WebP، دادهٔ جعلی، PNG ِ درهم/۱بیتی، zlib ِ بیش از اندازه، ناقص، باریک، بیش از سقف', () => {
  const um = (buf, reason) => {
    const a = run(buf);
    assert.equal(a.metrics.measured, false);
    assert.equal(a.metrics.reason, reason);
    assert.ok(a.flags.includes('quality_unmeasured'), JSON.stringify(a.flags));
    assert.equal(a.dhash, '');
    return a;
  };
  um(F.webp(640, 480), 'format');
  um(F.webpLossy(640, 480), 'format');
  um(F.jpeg(800, 600), 'undecodable');                 // سرآیندِ درست، دادهٔ جعلی
  um(F.png(320, 200), 'undecodable');
  um(F.pngBomb(1000, 1000), 'undecodable');            // ۱ بیت
  um(F.png(5000, 4000), 'too_many_pixels');            // ۲۰ مگاپیکسل > ۱۶: رمزگشایی شروع هم نمی‌شود
  um(F.pngImage(2000, 5, () => 128), 'too_thin');
  //  درهم (interlace = 1)
  const il = Buffer.from(F.pngImage(40, 40, scene(40, 40)));
  il[8 + 8 + 12] = 1;
  const ihdr = il.subarray(12, 12 + 4 + 13);
  il.writeUInt32BE(zlib.crc32(ihdr) >>> 0, 12 + 4 + 13);
  um(il, 'undecodable');
  //  IDAT که بیش از اندازهٔ خام باز می‌شود ⇒ maxOutputLength جلویش را می‌گیرد
  const ih = Buffer.alloc(13); ih.writeUInt32BE(10, 0); ih.writeUInt32BE(10, 4); ih[8] = 8; ih[9] = 0;
  const fat = Buffer.concat([Buffer.from([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a]), F.pngChunk('IHDR', ih),
    F.pngChunk('IDAT', zlib.deflateSync(Buffer.alloc(1 << 20))), F.pngChunk('IEND', Buffer.alloc(0))]);
  um(fat, 'undecodable');
  //  JPEG ِ واقعیِ بریده (داده پیش از پایان تمام شود)
  const j = SET.dupJpeg[0];
  um(Buffer.concat([j.subarray(0, Math.floor(j.length / 2)), Buffer.from([0xff, 0xd9])]), 'undecodable');
  //  ابعادِ SOF ناجور با داده ممکن نیست؛ ولی RST ِ گم‌شده ⇒ خراب
  const r = Buffer.from(F.jpegImage(64, 64, scene(64, 64), { restart: 4 }));
  const at = r.indexOf(Buffer.from([0xff, 0xd0]));
  r[at + 1] = 0x00;                                    // RST0 ⇒ 0xFF00 (بایتِ پرشده)
  um(r, 'undecodable');
});
