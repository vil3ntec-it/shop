'use strict';
/**
 * RPT-03 — روشِ بهای سود و حاشیه (D-23): `COST` ِ `license/report-board.js` (گزارشِ تازهٔ وب) در برابرِ بردارِ مشترکِ
 * `core/test-vectors/cost-policy-v1.json` — همان که `CostPolicyVectorsTest` (core، jvmTest) با `core/cost/CostPolicy.kt` می‌خواند.
 * بی پایگاه و بی مرورگر.
 */
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

const ROOT = path.join(__dirname, '..', '..');
const V = JSON.parse(fs.readFileSync(path.join(ROOT, 'core/test-vectors/cost-policy-v1.json'), 'utf8'));
const { COST } = require(path.join(ROOT, 'license/report-board.js'));

test('نسخه، پیش‌فرض، مصوب و فهرستِ روش‌ها همان بردار', () => {
  assert.equal(V.version, 1);
  assert.equal(COST.DEFAULT, V.default);
  assert.equal(COST.DECIDED, V.decided);
  assert.deepEqual(COST.METHODS.map((m) => ({ id: m.id, label: m.label, note: m.note })), V.methods);
});

test('parse: مقدارِ ذخیره‌شده ⇒ روش؛ ناشناخته یا خالی ⇒ پیش‌فرض', () => {
  assert.ok(V.parse.length >= 8);
  for (const c of V.parse) assert.equal(COST.parse(c.in), c.out, JSON.stringify(c.in));
});

test('approved: فقط روشِ مصوب؛ «تصمیم نشده» هیچ', () => {
  for (const c of V.approved) assert.equal(COST.approved(c.m, c.decided), c.out, JSON.stringify(c));
});

test('label و explain: همان نوشتهٔ برنامه، نویسه‌به‌نویسه', () => {
  for (const c of V.label) assert.equal(COST.label(c.base, c.m, c.decided), c.out, JSON.stringify(c));
  for (const c of V.explain) assert.equal(COST.explain(c.m, c.decided), c.out, JSON.stringify(c));
  //  `decided` ِ نیامده = روشِ مصوب
  assert.equal(COST.label('سود ناخالص', 'mavg'), 'سود ناخالص');
  assert.equal(COST.approved('line'), false);
});
