/**
 * نسخهٔ وب — حالِ همگام‌سازی همیشه پیدا (SYN-08) و قفلِ خروج با دادهٔ نرفته (SYN-10).
 *
 *   • نقطهٔ سربرگ شمارِ **واقعیِ** صف را نشان می‌دهد (همان `TohidSync.status().queued`)؛
 *     روی صفحهٔ پهن زمانِ آخرین همگام‌سازی و خطای آخر هم پیداست
 *   • صفِ پُر ⇒ «خروج از حساب» بسته است، با پیام و دکمهٔ «همین حالا همگام کن»
 *   • صف که در همان حال خالی شد ⇒ پنجره خودش خروج را باز می‌کند
 *   • خروجِ اجباری فقط پس از دانلودِ پشتیبان و تأییدِ دوم
 *   • صفِ خالی ⇒ همان پرسشِ همیشگی، بی قفل
 *
 * سرور در کار نیست: درخواست‌های API در مرورگر جواب می‌گیرند. موتورِ همگام‌سازی،
 * صف و دکمه‌ها همان کدِ واقعیِ صفحه‌اند.
 *
 * اجرا:  node test/web-sync-ux.mjs
 */
import { createRequire } from 'node:module';
import { missingTool, PLAYWRIGHT_INSTALL } from './browser-guard.mjs';
import assert from 'node:assert/strict';
import { execSync } from 'node:child_process';
import http from 'node:http';
import fs from 'node:fs';
import path from 'node:path';

const require = createRequire(import.meta.url);
const globalRoot = (() => { try { return execSync('npm root -g', { encoding: 'utf8' }).trim(); } catch { return ''; } })();
const pick = (name) => { for (const p of [`${globalRoot}/${name}`, name]) { try { return require(p); } catch { /* بعدی */ } } return null; };
const pw = pick('playwright');
if (!pw) missingTool('پلی‌رایت', PLAYWRIGHT_INSTALL);

const ROOT = path.join(import.meta.dirname, '..', '..');
const TYPES = { '.html': 'text/html', '.js': 'text/javascript', '.css': 'text/css', '.json': 'application/json', '.png': 'image/png', '.svg': 'image/svg+xml', '.webmanifest': 'application/manifest+json' };
const server = http.createServer((req, res) => {
  const file = path.join(ROOT, decodeURIComponent(req.url.split('?')[0]).replace(/^\/+/, '') || 'index.html');
  if (!file.startsWith(ROOT) || !fs.existsSync(file) || fs.statSync(file).isDirectory()) { res.statusCode = 404; return res.end(); }
  res.setHeader('Content-Type', TYPES[path.extname(file)] || 'application/octet-stream');
  fs.createReadStream(file).pipe(res);
});
await new Promise(r => server.listen(0, '127.0.0.1', r));
const url = `http://127.0.0.1:${server.address().port}/index.html`;

const LEDGER = {
  products: [{ id: 'p1', name: 'برنج', unit: 'کیلوگرم', salePrice: 1250000, purchasePrice: 990000 }],
  debtors: [{ id: 'c1', name: 'احمد کریمی' }],
  transactions: [], sales: [], saleItems: [],
};
const ACCOUNT = {
  accessToken: 'test-access', refreshToken: 'test-refresh',
  accessExpiresAt: Date.now() + 86400000, userId: 'u-sync-ux', userLabel: 'آزمون',
};
const FA = (n) => String(n).replace(/[0-9]/g, (d) => '۰۱۲۳۴۵۶۷۸۹'[+d]);

const browser = await pw.chromium.launch();
let failed = 0;
const step = async (name, fn) => {
  try { await fn(); console.log(`✅  ${name}`); } catch (e) { failed++; console.log(`❌  ${name}\n    ${e.message}`); }
};

/** صفحه با حسابِ واردشده؛ هر درخواستِ API بی‌جواب (سرورِ دور از دسترس). */
async function open(width) {
  const pg = await browser.newPage({ viewport: { width, height: 800 }, acceptDownloads: true });
  const errors = [];
  const dialogs = [];
  pg.on('pageerror', e => errors.push(String(e)));
  pg.on('dialog', d => { dialogs.push(d.message()); d.accept(); });
  await pg.goto(url);
  const api = await pg.evaluate(() => window.TohidApiConfig.baseUrl());
  await pg.route(`${api}/**`, r => r.abort('connectionrefused'));
  await pg.evaluate(([d, a]) => {
    localStorage.setItem('tohid-shop-auth-v1', JSON.stringify({ method: 'local', value: '', name: 'آزمون', loginAt: 1 }));
    localStorage.setItem('tohid-shop-data-v1', JSON.stringify(d));
    localStorage.setItem('tohid-license-v1', JSON.stringify(a));
  }, [LEDGER, ACCOUNT]);
  await pg.reload();
  await pg.waitForFunction(() => window.TohidSync && window.TohidAccountUI && document.getElementById('sync-dot-btn'));
  await pg.waitForTimeout(800);
  return { pg, api, errors, dialogs };
}

/** صفِ واقعی: کلِ دفتر یک بار op می‌شود و ارسال (بی سرور) شکست می‌خورد. */
async function fillQueue(pg) {
  return pg.evaluate(async () => {
    window.TohidSync.pushBaseline();
    await window.TohidSync.sync();
    return window.TohidSync.status();
  });
}

const signedIn = (pg) => pg.evaluate(() => !!(JSON.parse(localStorage.getItem('tohid-license-v1') || '{}') || {}).accessToken);

await step('SYN-08: گوشی — نقطه و شمارِ واقعیِ صف در سربرگ', async () => {
  const { pg, errors } = await open(390);
  const st = await fillQueue(pg);
  assert.ok(st.queued > 0, 'صف پر نشد');
  await pg.waitForFunction(() => !document.querySelector('#sync-dot-btn .sync-dot-n').hidden);
  const n = await pg.locator('#sync-dot-btn .sync-dot-n');
  assert.equal(await n.isVisible(), true, 'شمارِ صف روی گوشی دیده نمی‌شود');
  assert.equal(await n.innerText(), FA(st.queued));
  const label = await pg.locator('#sync-dot-btn').getAttribute('aria-label');
  assert.match(label, new RegExp(`در صف: ${FA(st.queued)}`));
  assert.match(label, /آخرین همگام‌سازی: هنوز همگام نشده/);
  const over = await pg.evaluate(() => document.documentElement.scrollWidth - window.innerWidth);
  assert.ok(over <= 1, `سربرگ بیرون زد (+${over}px)`);
  assert.deepEqual(errors, []);
  await pg.close();
});

await step('SYN-08: پهن — زمانِ آخرین همگام‌سازی و خطای آخر پیداست', async () => {
  const { pg, errors } = await open(1366);
  const st = await fillQueue(pg);
  assert.ok(st.lastError, 'ارسالِ بی‌سرور باید خطا بگذارد');
  await pg.waitForFunction(() => !document.querySelector('#sync-dot-btn .sync-dot-err').hidden);
  assert.equal(await pg.locator('#sync-dot-btn').getAttribute('data-dot'), 'red');
  assert.equal(await pg.locator('#sync-dot-btn .sync-dot-when').innerText(), 'هنوز همگام نشده');
  assert.equal(await pg.locator('#sync-dot-btn .sync-dot-err').isVisible(), true);
  assert.equal(await pg.locator('#sync-dot-btn .sync-dot-err').textContent(), st.lastError);
  assert.equal(await pg.locator('#sync-dot-btn .sync-dot-n').innerText(), FA(st.queued));
  assert.deepEqual(errors, []);
  await pg.close();
});

await step('SYN-10: صفِ پُر ⇒ خروج بسته؛ «همین حالا همگام کن»؛ خالی شدنِ صف ⇒ خروج باز', async () => {
  const { pg, api, errors, dialogs } = await open(390);
  const st = await fillQueue(pg);
  await pg.evaluate(() => goTo('profile'));
  await pg.locator('#prof-logout').click();
  await pg.waitForSelector('#acct-leave.open');
  const text = await pg.locator('#acct-leave').innerText();
  assert.match(text, new RegExp(`${FA(st.queued)} تغییرِ این دستگاه هنوز به سرور نرسیده`));
  assert.deepEqual(dialogs, [], 'پرسشِ خروج نباید پیش از خالی شدنِ صف بیاید');
  assert.equal(await signedIn(pg), true);

  //  بی سرور: «همگام کن» صف را خالی نمی‌کند و قفل می‌ماند
  await pg.locator('#lv-sync').click();
  await pg.waitForTimeout(400);
  assert.equal(await pg.locator('#lv-why').count(), 1);
  assert.equal(await signedIn(pg), true);

  //  سرور برگشت: همان دکمه صف را واقعاً خالی می‌کند و پنجره خروج را باز
  await pg.route(`${api}/api/sync/v1/push`, async (r) => {
    const body = JSON.parse(r.request().postData() || '{}');
    await r.fulfill({
      status: 200, contentType: 'application/json',
      body: JSON.stringify({ results: (body.ops || []).map(o => ({ op_id: o.op_id, status: 'applied' })), head: 1, schema_version: body.schema_version }),
    });
  });
  await pg.locator('#lv-sync').click();
  await pg.waitForSelector('#lv-out', { timeout: 8000 });
  assert.equal(await pg.evaluate(() => window.TohidSync.status().queued), 0);
  await pg.locator('#lv-out').click();
  await pg.waitForFunction(() => !(JSON.parse(localStorage.getItem('tohid-license-v1') || '{}') || {}).accessToken);
  assert.equal(dialogs.length, 1, 'پس از خالی شدنِ صف همان پرسشِ همیشگی می‌آید');
  assert.deepEqual(errors, []);
  await pg.close();
});

await step('SYN-10: خروجِ اجباری = دانلودِ پشتیبان + تأییدِ دوم', async () => {
  const { pg, errors, dialogs } = await open(390);
  await fillQueue(pg);
  await pg.evaluate(() => goTo('profile'));
  await pg.locator('#prof-logout').click();
  await pg.waitForSelector('#acct-leave.open');
  const [dl] = await Promise.all([pg.waitForEvent('download'), pg.locator('#lv-force').click()]);
  assert.match(dl.suggestedFilename(), /^tohid-shop-backup-.*\.json$/);
  await pg.waitForSelector('#lv-force-yes');
  assert.equal(await signedIn(pg), true, 'پیش از تأییدِ دوم خارج شد');
  await pg.locator('#lv-force-yes').click();
  await pg.waitForFunction(() => !(JSON.parse(localStorage.getItem('tohid-license-v1') || '{}') || {}).accessToken);
  assert.deepEqual(dialogs, [], 'پس از دو تأیید، پرسشِ سوم نمی‌آید');
  assert.deepEqual(errors, []);
  await pg.close();
});

await step('SYN-10: صفِ خالی ⇒ همان پرسشِ همیشگی، بی قفل', async () => {
  const { pg, errors, dialogs } = await open(390);
  assert.equal(await pg.evaluate(() => window.TohidSync.status().queued), 0);
  await pg.evaluate(() => goTo('profile'));
  await pg.locator('#prof-logout').click();
  await pg.waitForFunction(() => !(JSON.parse(localStorage.getItem('tohid-license-v1') || '{}') || {}).accessToken);
  assert.equal(dialogs.length, 1);
  assert.equal(await pg.locator('#acct-leave.open').count(), 0);
  assert.deepEqual(errors, []);
  await pg.close();
});

await browser.close();
server.close();
console.log(failed ? `\n${failed} سنجه قرمز` : '\nهمه سبز');
process.exit(failed ? 1 : 0);
