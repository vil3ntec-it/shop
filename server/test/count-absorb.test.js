'use strict';
/**
 * INV-10 — فروشِ پیش از شمارش که پس از ثبتِ شمارش می‌رسد (`license/count-absorb-core.js`).
 *
 * بردارهای مرجع (`license/test-vectors/count-absorb-v1.json`) را `CountAbsorbVectorsTest.kt`ِ اندروید/کامپیوتر هم
 * عیناً می‌دواند؛ هر دو باید یک عدد بدهند. قاعدهٔ اجرا: `gaps(lines, events)` ⇒ فقط ناصفرها با `lineId`؛
 * موجودی = `raw − gap`.
 */
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

const LICENSE = path.join(__dirname, '..', '..', 'license');
const core = require(path.join(LICENSE, 'count-absorb-core.js'));
const vectors = JSON.parse(fs.readFileSync(path.join(LICENSE, 'test-vectors', 'count-absorb-v1.json'), 'utf8'));

test('count-absorb-v1: نسخه و مرزها', () => {
  assert.equal(vectors.format, 'count-absorb-v1');
  assert.equal(core.VECTORS_FORMAT, vectors.format);
  assert.equal(core.WINDOW_MS, vectors.window_ms);
  assert.equal(core.SKEW_MS, vectors.skew_ms);
});

test('count-absorb-v1: سناریوها (جذب‌شده و موجودی)', () => {
  assert.ok(vectors.cases.length >= 20);
  for (const c of vectors.cases) {
    const g = core.gaps(c.lines, c.events);
    const got = {};
    g.forEach((x, pid) => { got[pid] = { lineId: x.lineId, gapMilli: x.gapMilli }; });
    assert.deepEqual(got, c.expect.gaps, `${c.name}: gaps`);
    const stock = {};
    for (const pid of Object.keys(c.raw)) stock[pid] = c.raw[pid] - (g.has(pid) ? g.get(pid).gapMilli : 0);
    assert.deepEqual(stock, c.expect.stock, `${c.name}: stock`);
  }
});

test('count-absorb-v1: دانستهٔ شمارنده (seen)', () => {
  for (const s of vectors.seen) assert.equal(core.seen(s.events, s.productId, s.countedAt, s.createdAt), s.expect, s.name);
});

test('count-absorb-v1: ردیفِ معتبر (trusted)', () => {
  for (const t of vectors.trusted) assert.equal(core.trusted(t.line), t.expect, t.line.id);
});

test('جهش‌آزمایی: «<» به جای «≤» در لحظهٔ شمردن، یا بی نگهبانِ ساعت ⇒ بردار سرخ', () => {
  const same = vectors.cases.find((c) => c.name.startsWith('فروشِ هم‌لحظه'));
  const strict = (lines, events) => core.gaps(lines, events.filter((e) => e.at !== lines[0].countedAt));
  assert.notEqual(strict(same.lines, same.events).size, Object.keys(same.expect.gaps).length);
  const skew = vectors.cases.find((c) => c.name.includes('سرورِ v3 زمانِ تصحیح‌شده گذاشت'));
  const blind = core.gaps(skew.lines, skew.events.map(({ correctedAt, ...e }) => e));
  assert.equal(blind.size, 1, 'بی correctedAt همان فروش جذب می‌شد — نگهبان واقعاً کار می‌کند');
  assert.equal(core.gaps(skew.lines, skew.events).size, 0);
});

test('هستهٔ سرور همان license/count-absorb-core.js است (بایت‌به‌بایت)', () => {
  const a = fs.readFileSync(path.join(LICENSE, 'count-absorb-core.js'));
  const b = fs.readFileSync(path.join(__dirname, '..', 'src', 'lib', 'count-absorb-core.js'));
  assert.ok(a.equals(b), 'server/src/lib/count-absorb-core.js باید کپیِ دقیقِ license/count-absorb-core.js باشد');
});

test('سرریز ⇒ خطا، نه عددِ غلط', () => {
  const big = Number.MAX_SAFE_INTEGER;
  assert.throws(() => core.seen([{ id: 'a', productId: 'p', at: 10, qtyMilli: big }, { id: 'b', productId: 'p', at: 10, qtyMilli: big }], 'p', 10, 10), RangeError);
});
