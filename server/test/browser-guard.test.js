'use strict';
/**
 * F0-17 — نبودِ پلی‌رایت دیگر «قبول» نیست.
 *
 * تا امروز هر چهار آزمونِ مرورگر (پنل، وب، چیدمانِ وب، نمایندگی) وقتی
 * پلی‌رایت پیدا نمی‌شد با کدِ ۰ بیرون می‌رفتند: CI و آدم هر دو «سبز»
 * می‌دیدند در حالی که هیچ مرورگری باز نشده بود. این‌جا هر چهار را در
 * محیطی بی پلی‌رایت اجرا می‌کنیم و می‌خواهیم **قرمز** شوند؛ و فقط با
 * SKIP_BROWSER_TESTS=1 (ردِ آگاهانه و بلند) کدِ ۰ بدهند.
 */
const test = require('node:test');
const assert = require('node:assert/strict');
const { spawnSync } = require('node:child_process');
const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');

const DIR = __dirname;
const FILES = ['admin-panel.mjs', 'web-backup.mjs', 'web-ui.mjs', 'portal-rep.mjs', 'web-sync-v3.mjs'];

// محیطی که پلی‌رایت در آن پیدا نمی‌شود: `npm root -g` به پوشه‌ای خالی
// می‌رود و NODE_PATH خالی است.
const emptyPrefix = fs.mkdtempSync(path.join(os.tmpdir(), 'no-pw-'));
const env = { ...process.env, npm_config_prefix: emptyPrefix, NODE_PATH: '' };
delete env.SKIP_BROWSER_TESTS;

// اگر پلی‌رایت با همین محیط باز هم پیدا شود (مثلاً در node_modulesِ یک
// پوشهٔ بالاتر)، این سنجه معنا ندارد و آگاهانه رد می‌شود.
const reachable = spawnSync(process.execPath, ['-e', "require.resolve('playwright')"], { cwd: DIR, env }).status === 0;

const run = (file, extra = {}) => spawnSync(process.execPath, [path.join(DIR, file)], {
  cwd: path.join(DIR, '..'), env: { ...env, ...extra }, encoding: 'utf8', timeout: 60_000,
});

for (const file of FILES) {
  test(`${file}: بی پلی‌رایت قرمز می‌شود، نه «قبول»`, { skip: reachable && 'پلی‌رایت از node_modulesِ محلی پیدا می‌شود' }, () => {
    const r = run(file);
    assert.notEqual(r.status, 0, `کدِ خروج ${r.status} — خروجی:\n${r.stdout}${r.stderr}`);
    assert.match(r.stdout + r.stderr, /پلی‌رایت پیدا نشد/);
    assert.match(r.stdout + r.stderr, /SKIP_BROWSER_TESTS=1/);
  });
}

test('SKIP_BROWSER_TESTS=1: ردِ آگاهانه با پیامِ بلند و کدِ ۰', { skip: reachable && 'پلی‌رایت از node_modulesِ محلی پیدا می‌شود' }, () => {
  const r = run('web-ui.mjs', { SKIP_BROWSER_TESTS: '1' });
  assert.equal(r.status, 0, r.stdout + r.stderr);
  assert.match(r.stdout, /آگاهانه رد شد/);
});

test('closeSafeRoute: فقط خطای «صفحه/پاسخ بسته شد» نادیده گرفته می‌شود، بقیه بالا می‌رود', async () => {
  const { closeSafeRoute, isClosedTargetError } = await import('./browser-guard.mjs');
  const closed = [
    'route.fetch: Target page, context or browser has been closed',
    'route.abort: Target closed',
    'route.fulfill: Fetch response has been disposed',
  ];
  for (const message of closed) {
    assert.equal(isClosedTargetError(new Error(message)), true, message);
    const h = closeSafeRoute(async () => { throw new Error(message); });
    assert.equal(await h({}, {}), undefined);
  }
  for (const message of ['route.continue: Route is already handled!', 'net::ERR_FAILED', 'bad']) {
    assert.equal(isClosedTargetError(new Error(message)), false, message);
    const h = closeSafeRoute(async () => { throw new Error(message); });
    await assert.rejects(h({}, {}), { message });
  }
  //  گردانندهٔ سالم: همان آرگومان‌ها و همان مقدار
  const seen = [];
  const ok = closeSafeRoute(async (route, request) => { seen.push(route, request); return 'done'; });
  assert.equal(await ok('r', 'q'), 'done');
  assert.deepEqual(seen, ['r', 'q']);
});

test.after(() => fs.rmSync(emptyPrefix, { recursive: true, force: true }));
