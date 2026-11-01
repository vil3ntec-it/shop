'use strict';
/**
 * نگهبانِ `test/e2e/lib/known-gaps.mjs` — فهرستِ «شکافِ شناخته» نباید جای سبز کردن شود.
 *
 *   • هر ردیف به سناریو و گامی می‌خورد که واقعاً هست (ردیفِ یتیم = گامی که دیگر سنجیده نمی‌شود)
 *   • هر ردیف مرجع دارد: شناسهٔ سند (§، RET-، …)، شمارهٔ PR ِ اصلاح، یا BLOCKED
 *   • فقط همان خطای ثبت‌شده «شکاف» است؛ خطای دیگرِ همان گام قرمز می‌ماند؛ سبز شدن ⇒ xpass
 */
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

const DIR = path.join(__dirname, 'e2e');

test('هر ردیفِ شکافِ شناخته به گامِ موجودی می‌خورد و مرجع دارد', async () => {
  const { KNOWN_GAPS } = await import(path.join(DIR, 'lib', 'known-gaps.mjs'));
  assert.ok(Array.isArray(KNOWN_GAPS));
  for (const g of KNOWN_GAPS) {
    const file = path.join(DIR, `${g.scenario}.e2e.mjs`);
    assert.ok(fs.existsSync(file), `سناریوی ${g.scenario} نیست`);
    const src = fs.readFileSync(file, 'utf8');
    assert.ok(src.includes(`E.step('${g.step}'`), `${g.scenario}: گامِ ${g.step} نیست — ردیفِ یتیم`);
    assert.ok(g.match instanceof RegExp, `${g.step}: match باید RegExp باشد`);
    assert.match(g.ref, /§|RET-|INV-|CHK-|PCK-|PR #\d+|BLOCKED|MISSING/, `${g.step}: مرجعِ روشن ندارد`);
  }
  const keys = KNOWN_GAPS.map((g) => `${g.scenario}/${g.step}`);
  assert.equal(new Set(keys).size, keys.length, 'ردیفِ تکراری');
});

test('فقط همان خطای ثبت‌شده شکاف است؛ خطای دیگر قرمز؛ سبز شدن xpass', async () => {
  const { KNOWN_GAPS, classify } = await import(path.join(DIR, 'lib', 'known-gaps.mjs'));
  assert.equal(classify('sync', 'گامِ-بی‌ردیف', new Error('x')).status, 'fail');
  assert.equal(classify('sync', 'گامِ-بی‌ردیف', null).status, 'pass');
  //  فهرستِ خالی (همهٔ شکاف‌ها بسته شدند، مثلاً S13.waste با RET-07 و OPS-14 با #335) هم درست است
  if (!KNOWN_GAPS.length) return;
  const g = KNOWN_GAPS[0];
  const sample = g.match.source.replace(/\\/g, '');
  assert.equal(classify(g.scenario, g.step, new Error(`... ${sample} ...`)).status, 'known-gap');
  assert.equal(classify(g.scenario, g.step, new Error('خطای دیگری')).status, 'fail');
  assert.equal(classify(g.scenario, g.step, null).status, 'xpass');
  assert.equal(classify('sync', 'گامِ-بی‌ردیف', new Error('x')).status, 'fail');
  assert.equal(classify('sync', 'گامِ-بی‌ردیف', null).status, 'pass');
});
