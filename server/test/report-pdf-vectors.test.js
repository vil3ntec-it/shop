'use strict';
/**
 * RPT-11 — PDF ِ مستقیمِ گزارش در وب: `write` ِ `license/report-pdf.js` در برابرِ بردارِ مشترکِ
 * `core/test-vectors/image-pdf-v1.json` — همان که `ImagePdfVectorsTest` (core، jvmTest) با `core/pdf/ImagePdf.kt` می‌خواند.
 * بایت‌ها یکی‌اند؛ و هر PDF ِ بردار با xref ِ درست (جای هر شیء) و RLE ِ برگشت‌پذیر. بی پایگاه و بی مرورگر.
 */
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

const ROOT = path.join(__dirname, '..', '..');
const V = JSON.parse(fs.readFileSync(path.join(ROOT, 'core/test-vectors/image-pdf-v1.json'), 'utf8'));
const P = require(path.join(ROOT, 'license/report-pdf.js'));
const bytes = (hex) => Uint8Array.from(Buffer.from(hex, 'hex'));
const hex = (u8) => Buffer.from(u8).toString('hex');

test('pt و عنوانِ UTF-16 همان بردار', () => {
  assert.equal(V.version, 1);
  for (const c of V.pt) assert.equal(P.pt(c.px, c.dpi), c.out, JSON.stringify(c));
  for (const c of V.utf16) assert.equal(P.utf16Hex(c.in), c.out, c.in);
});

test('RunLength: همان بایت‌ها و برگشت به ورودی', () => {
  assert.ok(V.runLength.length >= 8);
  for (const c of V.runLength) {
    assert.equal(hex(P.runLength(bytes(c.in))), c.out, c.in.slice(0, 40));
    assert.equal(hex(P.runLengthDecode(bytes(c.out))), c.in);
  }
});

test('PDF: بایت‌به‌بایت همان ImagePdf، با xref ِ درست و تصویرِ برگشت‌پذیر', () => {
  assert.ok(V.pdf.length >= 3);
  for (const c of V.pdf) {
    const pages = c.pages.map((p) => ({ width: p.width, height: p.height, bits: bytes(p.bits) }));
    const out = P.write(pages, c.dpi, c.title);
    assert.equal(hex(out), c.out, c.name);
    const text = Buffer.from(out).toString('latin1');
    assert.ok(text.startsWith('%PDF-1.4\n') && text.endsWith('%%EOF\n'), c.name);
    //  هر ردیفِ xref دقیقاً سرِ «n 0 obj» است
    const xref = Number(/startxref\n(\d+)\n/.exec(text)[1]);
    const rows = text.slice(xref).split('\n').slice(3).filter((l) => / 00000 n $/.test(l)).map((l) => Number(l.slice(0, 10)));
    assert.equal(rows.length, 3 + pages.length * 3, c.name);
    rows.forEach((at, i) => assert.ok(text.startsWith(`${i + 1} 0 obj\n`, at), `${c.name}: شیءِ ${i + 1}`));
    assert.ok(text.includes(`/Count ${pages.length} `), c.name);
  }
});

test('ورودیِ نادرست رد می‌شود، نه PDF ِ خراب', () => {
  assert.throws(() => P.write([], 150, ''));
  assert.throws(() => P.write([{ width: 8, height: 1, bits: new Uint8Array(1) }], 20, ''));
  assert.throws(() => P.write([{ width: 9, height: 1, bits: new Uint8Array(1) }], 150, ''));
});
