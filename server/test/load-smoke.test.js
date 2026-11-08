'use strict';
/**
 * PERF-02/PERF-04 — دودآزمونِ ابزارِ بار (`scripts/load-test.mjs`): چند ثانیه بارِ باز روی همان
 * پایگاهِ آزمونِ این اجرا (PostgreSQL یا PGlite)، با سرورِ جدا، کاشتن، گرم‌کردن و همهٔ سناریوها.
 *
 * ⛔ هیچ سنجهٔ زمانی این‌جا نیست (ماشینِ CI مرجع نیست و زمان لرزان است): فقط «ابزار سالم کار
 * می‌کند، هر مسیر پاسخِ ۲xx می‌دهد، و نمونهٔ حافظه/استخرِ سرور گرفته می‌شود». خودِ عددها با
 * اجرای کامل در `docs/PERF-LOAD-fa.md` ثبت‌اند.
 */
const test = require('node:test');
const assert = require('node:assert/strict');
const { spawnSync } = require('node:child_process');
const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');

const ROOT = path.join(__dirname, '..');
const DB = process.env.TEST_DATABASE_URL || 'postgres://shop:shoppass@127.0.0.1:5432/shop_test';

//  ⛔ ریشهٔ سرخیِ «زیرِ اجرای هم‌زمان»: بار «باز» است (نرخِ ثابت، هر چه سرور کند باشد) و PGlite روی یک رشته پرس‌وجو
//  می‌کند. با ۳۰ درخواست در ثانیه همین دودآزمون روی ماشینِ بیکار هم در مرزِ اشباع بود (۲۷ در ثانیه، p99 ≈ ۳s)؛ با چند
//  اجرای هم‌زمان صف بلند می‌شد و دو سنجهٔ پنهانِ «سرعتِ ماشین» سرخ می‌کردند: مهلتِ ۱۰ ثانیهٔ ابزار درخواستِ سالم را
//  «timeout» = خطا می‌شمرد (۵ از ۶ اجرای هم‌زمان)، و چهار دستگاهِ همگام‌سازی همه «مشغول» می‌ماندند تا `sync3.push` اصلاً
//  نرود (۸ از ۲۰). این آزمون سنجهٔ زمانی ندارد، پس: همان ۱۵۰ درخواست با یک‌سومِ نرخ (۱۰ در ثانیه در ۱۵ ثانیه، دور از
//  اشباع) و مهلتِ ۱۲۰ ثانیه — گیرِ واقعی (پاسخِ هرگز) هنوز خطاست و زیرِ مهلتِ ۲۸۰ ثانیهٔ کل دیده می‌شود.
const RATE = 10;
const DURATION_SEC = 15;
const REQUEST_TIMEOUT_MS = 120_000;

test('ابزارِ بار: چند ثانیه بار روی همهٔ سناریوها، بی هیچ پاسخِ بیرون از ۲xx', { timeout: 300_000 }, () => {
  const out = path.join(os.tmpdir(), `load-smoke-${process.pid}.json`);
  const r = spawnSync(process.execPath, [
    'scripts/load-test.mjs', '--db', DB, '--profile', 'smoke', '--no-budget', '--quiet',
    '--shops', '2', '--devices', '2', '--scale', '0.002', '--small-scale', '0.001', '--history', '300',
    '--warmup', '1', '--rate', String(RATE), '--duration', String(DURATION_SEC), '--timeout', String(REQUEST_TIMEOUT_MS), '--json', out,
  ], { cwd: ROOT, encoding: 'utf8', timeout: 280_000 });
  assert.equal(r.status, 0, `${r.stdout.slice(-1500)}\n${r.stderr.slice(-1500)}`);
  const res = JSON.parse(fs.readFileSync(out, 'utf8'));
  fs.rmSync(out, { force: true });

  //  گرم‌کردن خودش ورود، تازه‌سازیِ نشست، سفارش، تاریخچهٔ v3 و pull تا سرِ دفتر را رفته (شکستش ⇒ خروجِ ناصفر)
  assert.equal(res.setups.length, 1);
  assert.ok(res.setups[0].dataset.rows > 0 && res.setups[0].dataset.bridge.ops > 0, JSON.stringify(res.setups[0].dataset));
  const [p] = res.profiles;
  assert.equal(p.profile, 'smoke');
  assert.equal(p.dropped, 0, 'بارساز اشباع نشد');
  assert.equal(p.total.errors, 0, JSON.stringify(Object.fromEntries(Object.entries(p.endpoints).filter(([, e]) => e.errors))));
  //  مسیرهای پروزن همیشه در ۱۵۰ درخواست هستند (وزنِ ≥ ۹٪ ⇒ احتمالِ نبودن < ۱e-6)
  for (const label of ['store.products', 'store.search', 'store.product', 'sync3.push', 'sync3.pull', 'desk.list']) {
    assert.ok(p.endpoints[label] && p.endpoints[label].n > 0, `${label} سنجیده شد`);
    for (const k of ['p50', 'p95', 'p99']) assert.ok(Number.isFinite(p.endpoints[label][k]), `${label}.${k}`);
  }
  //  نمونهٔ سرور: حافظه پس از GC در آغاز و پایان؛ استخر فقط روی PostgreSQL
  assert.ok(p.server.samples.length >= 2);
  assert.ok(p.server.heapAfterGcStartMB > 0 && p.server.heapAfterGcEndMB > 0);
  if (!/^pglite:/i.test(DB)) assert.ok(p.server.poolTotalMax >= 1, 'استخرِ PostgreSQL دیده شد');
});
