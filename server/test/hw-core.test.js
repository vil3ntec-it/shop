/**
 * هستهٔ دستگاه‌ها (HW-01..04، POS-29) — بردارهای مشترکِ `core/test-vectors/hw-v1.json`.
 *
 *   ۱. پیاده‌سازیِ مرجعِ `hw-core-ref.js` همان بایت‌هایی را می‌سازد که `core/hw/*.kt`
 *      (HwCoreTest.kt) با همین فایل می‌سنجد — دو پیاده‌سازیِ جدا، یک جواب.
 *   ۲. `legacyRaster` از کدِ پیش از این تغییر گرفته شده: پیش‌فرضِ پروفایل باید عیناً
 *      همان بایت‌ها باشد (چاپگری که دیروز کار می‌کرد امروز هم کار کند).
 *   ۳. `thermal-print.js`ِ دست‌نخوردهٔ وب هم هنوز همان بایت‌ها را می‌دهد (مشخصه).
 *   ۴. پیش‌نمایش (`decode`) از روی بایت‌ها همان تصویر را پس می‌دهد.
 *   ۵. بارکدها را رمزگشای مستقلِ zxing همان رشته می‌خواند.
 *
 * چاپگرِ واقعی این‌جا نیست؛ این‌ها بایت‌ها را می‌پایند، نه کاغذ را.
 */
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const crypto = require('node:crypto');

const ROOT = path.join(__dirname, '..', '..');
const H = require('./hw-core-ref.js');
const V = JSON.parse(fs.readFileSync(path.join(ROOT, 'core', 'test-vectors', 'hw-v1.json'), 'utf8'));

const hex = (u) => Buffer.from(u).toString('hex');
const sha = (u) => crypto.createHash('sha256').update(Buffer.from(u)).digest('hex');
function pattern(w, h) {
  const d = new Uint8Array(w * h * 4);
  for (let y = 0; y < h; y++) for (let x = 0; x < w; x++) {
    const a = ((x + 2 * y) % 7 === 0) ? 100 : 255;
    d.set([(x * 37 + y * 11) & 255, (x * 13 + y * 29) & 255, (x * 7 + y * 3) & 255, a], (y * w + x) * 4);
  }
  return { rgba: d, img: H.fromRgba(d, w, h, w) };
}
function same(bytes, want, label) {
  assert.equal(bytes.length, want.len, label + ' — طول');
  assert.equal(hex(bytes.slice(0, 16)), want.head, label + ' — سر');
  assert.equal(hex(bytes.slice(-8)), want.tail, label + ' — ته');
  assert.equal(sha(bytes), want.sha256, label + ' — sha256');
}

test('پیش‌فرضِ پروفایل = بایت‌های پیشین (legacyRaster)', () => {
  for (const L of V.legacyRaster) {
    same(H.document(pattern(L.w, L.h).img, { paperDots: L.w }), L, `${L.w}x${L.h}`);
  }
});

test('thermal-print.jsِ دست‌نخوردهٔ وب هنوز همان بایت‌ها را می‌سازد (مشخصه)', () => {
  const ctx = { window: {}, navigator: {} };
  vm.createContext(ctx);
  vm.runInContext(fs.readFileSync(path.join(ROOT, 'license', 'thermal-print.js'), 'utf8'), ctx);
  const T = ctx.window.TohidThermal;
  for (const L of V.legacyRaster) {
    const { rgba } = pattern(L.w, L.h);
    const canvas = { width: L.w, height: L.h, getContext: () => ({ getImageData: () => ({ data: rgba }) }) };
    same(T.toEscPos(canvas, L.w), L, `toEscPos ${L.w}x${L.h}`);
  }
});

test('سندها: ستونیِ ESC *، برشِ کامل، کشو، بی‌برش', () => {
  for (const d of V.documents) same(H.document(pattern(d.w, d.h).img, d.profile, d.openDrawer), d, d.name);
});

test('کشو (ESC p)، برش و وضعیتِ کاغذ', () => {
  for (const d of V.drawer) assert.equal(hex(H.drawerKick(d.pin, d.onMs, d.offMs)), d.hex);
  for (const d of V.drawerOnly) assert.equal(hex(H.drawerOnly(d.profile)), d.hex);
  for (const c of V.cut) assert.equal(hex(H.cut(c.mode)), c.hex, c.mode);
  for (const s of V.paperStatus) assert.deepEqual(H.parsePaperStatus(s.byte), s.out, String(s.byte));
});

test('EAN-13 و Code128 — ماژول به ماژول', () => {
  for (const e of V.ean13) {
    assert.equal(H.ean13Valid(e.code), e.valid, e.code);
    if (e.valid) assert.equal(H.ean13Modules(e.code), e.modules, e.code);
    else assert.throws(() => H.ean13Modules(e.code));
    if (e.valid) assert.equal(e.modules.length, 95);
  }
  for (const c of V.code128) {
    assert.deepEqual(H.code128Symbols(c.text), c.symbols, c.text);
    assert.equal(H.code128Modules(c.text), c.modules, c.text);
    assert.equal(c.modules.length, 11 * (c.symbols.length - 1) + 13, c.text);
  }
  for (const bad of V.code128Errors) assert.throws(() => H.code128Symbols(bad), bad);
  for (const p of V.forProduct) {
    const s = H.barcodeForProduct(p.code);
    assert.equal(s ? s.kind : null, p.kind, p.code);
  }
});

test('برچسب: TSPL، ESC/POS، جای برگهٔ A4 و پهنای خط', () => {
  const checker = H.mono(20, 4);
  for (let y = 0; y < 4; y++) for (let x = 0; x < 20; x++) if ((x + y) % 3 === 0) H.set(checker, x, y, true);
  const t = H.tspl(checker, V.tspl.size, V.tspl.copies);
  same(t, V.tspl, 'tspl');
  assert.equal(Buffer.from(t.slice(0, V.tspl.text.length)).toString('latin1'), V.tspl.text);
  same(H.labelEscPos(checker, V.labelEscPos.copies), V.labelEscPos, 'label escpos');
  for (const m of V.moduleDots) assert.equal(H.moduleDots(m.modules, m.maxDots), m.out, JSON.stringify(m));
  for (const c of V.a4Cell) assert.deepEqual(H.a4Cell(c.index), c.out, String(c.index));
});

test('بارکدِ ترازو (S04): 2100150012507 = کالای 00150، ۱٫۲۵۰ کیلو؛ رقمِ کنترلِ غلط رد', () => {
  for (const c of V.weight.cases) {
    const r = H.parseWeight(c.code, V.weight.rules);
    const got = r.type === 'hit'
      ? { type: 'hit', prefix: r.rule.prefix, kind: r.rule.kind, itemCode: r.itemCode, value: r.value, raw: r.raw }
      : { type: r.type };
    assert.deepEqual(got, c.out, c.code);
  }
  //  بی‌قاعده هیچ بارکدی وزنی نیست — پیش‌فرضِ خاموش
  assert.equal(H.parseWeight('2100150012507', []).type, 'notScale');
  assert.equal(H.buildWeight(V.weight.rules[0], '150', 1250), '2100150012507');
});

test('پیش‌نمایش: decode(document(img)) همان تصویر است — رستری و ستونی', () => {
  for (const mode of ['raster', 'column']) {
    for (const [w, h] of [[384, 40], [576, 130]]) {
      const { img } = pattern(w, h);
      const r = H.decode(H.document(img, { paperDots: w, mode }, true), w);
      assert.deepEqual(r.unknown, [], mode);
      assert.equal(r.cuts, 1);
      assert.equal(r.drawerKicks, 1);
      const rb = (w + 7) >> 3;
      assert.ok(Buffer.from(r.paper.bits.subarray(0, rb * h)).equals(Buffer.from(img.bits)), `${mode} ${w}x${h}`);
    }
  }
  const junk = H.decode(new Uint8Array([0x1B, 0x40, 0x99]), 384);
  assert.deepEqual(junk.unknown, ['0x99@2']);
});

/*
 * بررسیِ مستقل: بارکدها را رمزگشای دیگری (zxing-js، همان که دوربینِ وب
 * با آن بارکد می‌خواند) باید همان رشته بخواند — نه فقط «با بردار یکی است».
 * همهٔ ۹۵ نویسهٔ دستهٔ B، همهٔ جفت‌های ۰۰ تا ۹۹ دستهٔ C و EAN-13های گوناگون.
 */
test('رمزگشای مستقل (zxing) همان رشته را از بارکدِ کشیده‌شده می‌خواند', () => {
  const Z = require(path.join(ROOT, 'license', 'vendor', 'zxing-0.21.3.min.js'));
  const read = (img) => {
    const lum = new Uint8ClampedArray(img.width * img.height);
    for (let y = 0; y < img.height; y++) for (let x = 0; x < img.width; x++) lum[y * img.width + x] = H.get(img, x, y) ? 0 : 255;
    const hints = new Map([[Z.DecodeHintType.TRY_HARDER, true]]);
    const r = new Z.MultiFormatReader().decode(new Z.BinaryBitmap(new Z.HybridBinarizer(new Z.RGBLuminanceSource(lum, img.width, img.height))), hints);
    return { text: r.getText(), format: Z.BarcodeFormat[r.getBarcodeFormat()] };
  };
  const codes = [];
  let printable = '';
  for (let c = 32; c <= 126; c++) printable += String.fromCharCode(c);
  for (let i = 0; i < printable.length; i += 12) codes.push(printable.slice(i, i + 12).trim() || 'x');
  let pairs = '';
  for (let n = 0; n < 100; n++) pairs += String(n).padStart(2, '0');
  for (let i = 0; i < pairs.length; i += 20) codes.push(pairs.slice(i, i + 20));
  codes.push(...V.code128.map((c) => c.text), 'X12345678Y', '9876543', 'PLU-00150');
  for (const text of codes) {
    const s = H.barcodeForProduct(text);
    assert.equal(s.kind, 'CODE128', text);
    assert.deepEqual(read(H.renderBarcode(s.modules, 2, 40)), { text, format: 'CODE_128' }, text);
  }
  const eans = V.ean13.filter((e) => e.valid).map((e) => e.code);
  for (let k = 0; k < 20; k++) {
    const body = String(100000000000 + k * 7919 * 104729).slice(0, 12);
    eans.push(body + H.ean13Check(body));
  }
  eans.push(H.buildWeight(V.weight.rules[0], '150', 1250));
  for (const code of eans) {
    const s = H.barcodeForProduct(code);
    assert.equal(s.kind, 'EAN13', code);
    //  EAN-13ِ آغازشده با ۰ همان UPC-A است و zxing آن را ۱۲ رقمی گزارش می‌کند — خط‌ها یکی‌اند
    const want = code[0] === '0' ? { text: code.slice(1), format: 'UPC_A' } : { text: code, format: 'EAN_13' };
    assert.deepEqual(read(H.renderBarcode(s.modules, 2, 40)), want, code);
  }
});
