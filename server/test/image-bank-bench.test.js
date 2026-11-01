'use strict';
/**
 * IMG-BANK-18 و IMG-BANK-14 در CI: همان `bench/image-bank.mjs` در اندازهٔ کوچک روی سرورِ آزمون.
 * زمان سنجیده نمی‌شود (ماشینِ CI مرجع نیست)؛ فقط این‌که گزارش همهٔ بخش‌های §۳۷.۱۰ را دارد،
 * هیچ درخواستی خطا نمی‌دهد، دقت کامل است و هیچ پاسخی فایلِ اصل یا بیش از ۵۰ ردیف ندارد.
 * اجرای بزرگ: `node bench/image-bank.mjs` (perf.yml، کارِ «بانکِ تصاویر»).
 */
const test = require('node:test');
const assert = require('node:assert/strict');
const path = require('node:path');
const h = require('./helpers');

let bench;
test.before(async () => {
  await h.start();
  bench = await import(path.join(__dirname, '..', '..', 'bench', 'image-bank.mjs'));
});
test.after(async () => { await h.stop(); });

test('IMG-BANK-18 / IMG-BANK-14: گزارشِ کارایی با p50/p95/p99، خطا، منابع و دقت؛ بی فایلِ اصل', async () => {
  const r = await bench.runImageBankBench({ h, products: 400, images: 12, users: 3, ops: 10 });
  //  اندازهٔ بانک، هم‌زمانی، محیط و هدف‌ها ثبت شده‌اند
  assert.equal(r.bank.products, 400);
  assert.equal(r.bank.images, 12);
  assert.equal(r.users, 3);
  assert.ok(r.env.cores >= 1 && r.env.node && r.env.driver);
  assert.deepEqual(r.targets, { barcode: 500, name: 1000 });
  //  سرد و گرم، هر دسته با چندک‌ها
  for (const phase of ['cold', 'warm']) {
    for (const k of ['barcode', 'name', 'spelling', 'browse', 'page1']) {
      const s = r[phase][k];
      assert.ok(s.n > 0, `${phase}.${k} خالی است`);
      assert.ok(s.p50 <= s.p95 && s.p95 <= s.p99 && s.p99 <= s.max, `${phase}.${k} ${JSON.stringify(s)}`);
    }
    assert.ok(r[phase].requests > 0 && r[phase].rps > 0 && r[phase].cpuMs >= 0);
  }
  assert.ok(r.resources.peakRssMB > 0);
  assert.ok(r.verdict.barcodeP95.ms >= 0 && typeof r.verdict.nameP95.pass === 'boolean');
  //  سخت: بی خطا، دقتِ کامل، بی فایلِ اصل، صفحه ≤ ۵۰، عکسِ کوچکِ immutable
  assert.deepEqual(bench.problemsOf(r), []);
  assert.ok(r.warm.thumb.n > 0, 'هیچ عکسِ کوچکی خوانده نشد');
  assert.ok(r.payload.maxItemsPerPage <= 50);
});

test('problemsOf: خطا، دقتِ ناقص و فایلِ اصل را می‌گیرد (آزمونِ خودِ سنجه)', () => {
  const ok = { errors: 0 };
  const acc = { barcode: { ok: 3, n: 3 } };
  const base = { cold: ok, warm: ok, accuracy: { cold: acc, warm: acc }, payload: { responsesWithOriginal: 0, maxItemsPerPage: 20, thumbsNotImmutable: 0 } };
  assert.deepEqual(bench.problemsOf(base), []);
  assert.equal(bench.problemsOf({ ...base, warm: { errors: 2, firstError: 'x' } }).length, 1);
  assert.equal(bench.problemsOf({ ...base, accuracy: { cold: acc, warm: { barcode: { ok: 2, n: 3 } } } }).length, 1);
  assert.equal(bench.problemsOf({ ...base, payload: { ...base.payload, responsesWithOriginal: 1 } }).length, 1);
  assert.equal(bench.problemsOf({ ...base, payload: { ...base.payload, maxItemsPerPage: 51 } }).length, 1);
});
