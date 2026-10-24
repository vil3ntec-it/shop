'use strict';
/**
 * توکن و نشانیِ QRِ فاکتور (فاکتورِ نسخهٔ ۲) با بردارهای مشترکِ
 * `core/test-vectors/receipt-v1.json` — همان فایلی که `ReceiptToken.kt` می‌خواند.
 * به‌علاوه: کپیِ سرورِ هستهٔ تقویم همان `license/calendar-core.js` است و توکن در لاگ نمی‌آید.
 */
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const R = require('../src/lib/receipt-token');
const { redactPath } = require('../src/lib/log-redact');
const { formatQty } = require('../src/lib/receipt-public');

const ROOT = path.join(__dirname, '..', '..');
const V = JSON.parse(fs.readFileSync(path.join(ROOT, 'core', 'test-vectors', 'receipt-v1.json'), 'utf8'));

test('الفبا همان Crockford ِ کوچک است', () => {
  assert.equal(R.ALPHABET, V.alphabet);
  assert.equal(new Set(V.alphabet).size, 32);
});

test('۱۶ بایت ⇒ ۲۶ نویسه، بزرگ‌مرتبه اول؛ نویسهٔ اول همیشه ۰ تا ۷', () => {
  for (const c of V.encode) {
    const t = R.encodeToken(Buffer.from(c.hex, 'hex'));
    assert.equal(t, c.token, c.hex);
    assert.equal(R.normalizeToken(t), t);
    assert.match(t[0], /[0-7]/);
  }
  assert.throws(() => R.encodeToken(Buffer.alloc(15)));
});

test('یکدست‌سازی: بزرگ و فاصله پذیرفته؛ نویسهٔ بیرون از الفبا، طولِ دیگر و سرِ ۸+ رد', () => {
  for (const c of V.normalize) assert.equal(R.normalizeToken(c.raw), c.token, JSON.stringify(c.raw));
});

test('نشانِ دکان: ۸ نویسه از sha256، برای هر شناسه ثابت', () => {
  for (const c of V.tags) {
    assert.equal(R.shopTag(c.shopId), c.tag, c.shopId);
    assert.equal(R.normalizeTag(c.tag.toUpperCase()), c.tag);
  }
  assert.notEqual(R.shopTag('a'), R.shopTag('b'));
  assert.equal(R.normalizeTag('abc'), null);
});

test('نشانیِ QR: ریشه بی / ِ آخر + /r/<نشان>/<توکن>', () => {
  for (const c of V.urls) assert.equal(R.receiptUrl(c.base, c.shopId, c.token), c.url);
});

test('توکنِ فاکتور در لاگ :tok می‌شود؛ نشانِ کوتاهِ دکان می‌ماند', () => {
  const { tag, token } = { tag: V.tags[0].tag, token: V.encode[4].token };
  assert.equal(redactPath(`/r/${tag}/${token}`), `/r/${tag}/:tok`);
  assert.equal(redactPath(`/api/receipt/${tag}/${token}?x=1`), `/api/receipt/${tag}/:tok`);
  for (const c of V.encode) assert.equal(redactPath(`/r/${tag}/${c.token}`), `/r/${tag}/:tok`, c.token);
});

test('کپیِ سرورِ هستهٔ تقویم بایت‌به‌بایت همان license/calendar-core.js است (یک منطق، نه دو)', () => {
  const shared = fs.readFileSync(path.join(ROOT, 'license', 'calendar-core.js'));
  const copy = fs.readFileSync(path.join(__dirname, '..', 'src', 'lib', 'calendar-core.js'));
  assert.ok(shared.equals(copy), 'server/src/lib/calendar-core.js را با cp از license/calendar-core.js تازه کنید');
});

test('مقدارِ قلم: تا سه رقمِ اعشار، بی صفرِ آخر، با رقمِ انتخابی', () => {
  assert.equal(formatQty(2, 'latin'), '2');
  assert.equal(formatQty(1.5, 'latin'), '1.5');
  assert.equal(formatQty(0.125, 'latin'), '0.125');
  assert.equal(formatQty(0.1 + 0.2, 'latin'), '0.3');
  assert.equal(formatQty(12, 'fa'), '۱۲');
  assert.equal(formatQty('x', 'latin'), '0');
});
