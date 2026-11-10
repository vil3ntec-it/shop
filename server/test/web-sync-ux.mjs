/**
 * نسخهٔ وب — حالِ همگام‌سازی همیشه پیدا (SYN-08) و قفلِ خروج با دادهٔ نرفته (SYN-10).
 *
 *   • نقطهٔ سربرگ شمارِ **واقعیِ** صف را نشان می‌دهد (همان `TohidSync.status().queued`)؛
 *     روی صفحهٔ پهن زمانِ آخرین همگام‌سازی و خطای آخر هم پیداست
 *   • صفِ پُر ⇒ «خروج از حساب» بسته است، با پیام و دکمهٔ «همین حالا همگام کن»
 *   • صف که در همان حال خالی شد ⇒ پنجره خودش خروج را باز می‌کند
 *   • خروجِ اجباری فقط پس از دانلودِ پشتیبان و تأییدِ دوم
 *   • صفِ خالی ⇒ همان پرسشِ همیشگی، بی قفل
 *   • SYN-12: شکستِ کوتاهِ شبکه سرخ نیست («دوباره می‌کوشد»، خطای آخر همچنان پیدا)؛ قطعیِ ماندگار ⇒ نوارِ آرامِ
 *     «به سرور نمی‌رسیم» روی صفحه و در پنجرهٔ همگام‌سازی (axe، روز و شب)؛ ۵۰۳ ⇒ «سرور»؛ بی‌نت ⇒ خاکستری و بی نوار؛
 *     ۴۰۰ ⇒ همان سرخ؛ برگشتنِ سرور ⇒ سبز و نوار رفته
 *
 * سرور در کار نیست: درخواست‌های API در مرورگر جواب می‌گیرند. موتورِ همگام‌سازی،
 * صف و دکمه‌ها همان کدِ واقعیِ صفحه‌اند.
 *
 * اجرا:  node test/web-sync-ux.mjs
 */
import { createRequire } from 'node:module';
import { missingTool, PLAYWRIGHT_INSTALL, closeSafeRoute, SKIP } from './browser-guard.mjs';
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

let axeSrc = null;
for (const p of [`${globalRoot}/axe-core/axe.min.js`, path.join(import.meta.dirname, '..', 'node_modules', 'axe-core', 'axe.min.js'), process.env.AXE_PATH || '']) {
  if (p && fs.existsSync(p)) { axeSrc = fs.readFileSync(p, 'utf8'); break; }
}

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
  await pg.route(`${api}/**`, closeSafeRoute(r => r.abort('connectionrefused')));
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
  //  SYN-12: نخستین شکستِ شبکه «دوباره می‌کوشد» است، نه سرخ — خطای آخر همچنان پیداست (SYN-08)
  assert.equal(await pg.locator('#sync-dot-btn').getAttribute('data-dot'), 'yellow');
  assert.equal(await pg.locator('#sync-dot-btn .sync-dot-text').innerText(), 'دوباره می‌کوشد');
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
  await pg.route(`${api}/api/sync/v1/push`, closeSafeRoute(async (r) => {
    const body = JSON.parse(r.request().postData() || '{}');
    await r.fulfill({
      status: 200, contentType: 'application/json',
      body: JSON.stringify({ results: (body.ops || []).map(o => ({ op_id: o.op_id, status: 'applied' })), head: 1, schema_version: body.schema_version }),
    });
  }));
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

/* ══════════════════ SYN-12: قطعیِ کوتاه، ماندگار، سرور، بی‌نت ══════════════════ */

/** ساعتِ صفحه (Date.now) جلو می‌رود؛ موتور مدتِ شکست را از همین می‌خواند */
const skew = (pg, ms) => pg.evaluate((d) => {
  if (!window.__realNow) { window.__realNow = Date.now.bind(Date); window.__skew = 0; Date.now = () => window.__realNow() + window.__skew; }
  window.__skew += d;
}, ms);
const dotOf = (pg) => pg.locator('#sync-dot-btn').getAttribute('data-dot');
const sync = (pg) => pg.evaluate(async () => { await window.TohidSync.sync(); window.TohidAccountUI.refreshHeader(); return window.TohidSync.status(); });

await step('SYN-12: قطعیِ ماندگار ⇒ نوارِ آرامِ «به سرور نمی‌رسیم» (نه سرخ)؛ ۵۰۳ ⇒ «سرور»؛ بی‌نت ⇒ خاکستری؛ برگشت ⇒ سبز', async () => {
  const { pg, api, errors } = await open(1366);
  await fillQueue(pg);
  assert.equal(await pg.locator('#sync-outage').isVisible(), false, 'شکستِ کوتاه نوار نمی‌گذارد');
  await skew(pg, 61_000);
  let st = await sync(pg);
  assert.deepEqual(st.outage, { kind: 'unreachable', level: 'notice' });
  await pg.waitForSelector('#sync-outage.show');
  assert.equal(await dotOf(pg), 'yellow');
  assert.equal(await pg.locator('#sync-dot-btn .sync-dot-text').innerText(), 'به سرور نمی‌رسیم');
  assert.equal(await pg.locator('#sync-dot-btn .sync-dot-err').textContent(), st.lastError, 'خطای آخر پیداست (SYN-08)');
  assert.match(await pg.locator('#sync-outage').innerText(), /به سرور نمی‌رسیم[\s\S]*چیزی گم نمی‌شود/);
  assert.equal(await pg.locator('#sync-outage').getAttribute('role'), 'status');
  assert.ok(st.queued > 0, 'صف سرِ جایش');

  //  «جزئیات» ⇒ پنجرهٔ همگام‌سازی با همان نوار
  await pg.locator('#sync-outage-more').click();
  await pg.waitForSelector('#acct-sync.open #sy-outage');
  assert.match(await pg.locator('#sy-outage').innerText(), /اینترنتِ بیرون/);
  await pg.keyboard.press('Escape');

  //  سرور پاسخ داد ولی ۵۰۳: همان رشته ⇒ «سرور»
  await pg.unroute(`${api}/**`);
  await pg.route(`${api}/**`, closeSafeRoute((r) => r.fulfill({ status: 503, contentType: 'application/json', body: JSON.stringify({ error: 'db_busy', message: 'سرور الان شلوغ است' }) })));
  st = await sync(pg);
  assert.equal(st.outage.kind, 'server');
  await pg.waitForFunction(() => document.querySelector('#sync-outage').getAttribute('data-kind') === 'server');
  assert.equal(await dotOf(pg), 'yellow');

  //  بی‌نت ⇒ خاکستری، نوار می‌رود (تقصیرِ سرور نیست)
  await pg.context().setOffline(true);
  await pg.evaluate(() => window.TohidAccountUI.refreshHeader());
  assert.equal(await dotOf(pg), 'grey');
  assert.equal(await pg.locator('#sync-outage').isVisible(), false);

  //  سرور برگشت (و اینترنت) ⇒ سبز و نوار رفته — «online» خودش یک دور می‌زند
  await pg.unroute(`${api}/**`);
  await pg.route(`${api}/**`, closeSafeRoute(async (r) => {
    const u = r.request().url();
    if (u.includes('/api/sync/v1/push')) {
      const body = JSON.parse(r.request().postData() || '{}');
      return r.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({ results: (body.ops || []).map((o) => ({ op_id: o.op_id, status: 'applied' })), head: 1, schema_version: body.schema_version }) });
    }
    if (u.includes('/api/sync/v1/pull')) return r.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({ ops: [], cursor: 1, has_more: false }) });
    return r.fulfill({ status: 404, contentType: 'application/json', body: '{"error":"not_found"}' });
  }));
  await pg.context().setOffline(false);
  for (let i = 0; i < 40; i++) {
    //  دورِ «online» شاید هنوز در راه است (busy) — تا تمام شدنش صبر، بعد یک دورِ دیگر
    st = await pg.evaluate(async () => { if (!window.TohidSync.status().busy) await window.TohidSync.sync(); return window.TohidSync.status(); });
    if (!st.busy && !st.lastError) break;
    await pg.waitForTimeout(200);
  }
  await pg.evaluate(() => window.TohidAccountUI.refreshHeader());
  assert.equal(st.lastError, '');
  assert.equal(st.queued, 0);
  assert.equal(await dotOf(pg), 'green');
  assert.equal(await pg.locator('#sync-outage').isVisible(), false);
  assert.deepEqual(errors, []);
  await pg.close();
});

await step('SYN-12: خطای واقعی (۴۰۰) همان سرخ و بی نوار — هشدارِ لازم پنهان نمی‌شود', async () => {
  const { pg, api, errors } = await open(1366);
  await pg.unroute(`${api}/**`);
  await pg.route(`${api}/**`, closeSafeRoute((r) => r.fulfill({ status: 400, contentType: 'application/json', body: JSON.stringify({ error: 'bad_request', message: 'درخواست نادرست' }) })));
  const st = await fillQueue(pg);
  assert.equal(st.outage.kind, 'error');
  await pg.evaluate(() => window.TohidAccountUI.refreshHeader());
  assert.equal(await dotOf(pg), 'red');
  assert.equal(await pg.locator('#sync-outage').isVisible(), false);
  assert.deepEqual(errors, []);
  await pg.close();
});

await step('SYN-12: دسترس‌پذیری (axe) ِ نوارِ قطعی و پنجرهٔ همگام‌سازی — روز و شب، گوشی', async () => {
  if (!axeSrc) {
    if (SKIP) { console.log('⏭  axe-core پیدا نشد و SKIP_BROWSER_TESTS=1 است — آگاهانه رد شد'); return; }
    throw new Error('axe-core پیدا نشد (npm i -g axe-core@4.10.3، یا AXE_PATH) — «قبول» نیست');
  }
  const bad = [];
  for (const scheme of ['light', 'dark']) {
    const { pg } = await open(390);
    await pg.emulateMedia({ colorScheme: scheme });
    await fillQueue(pg);
    await skew(pg, 61_000);
    await sync(pg);
    await pg.waitForSelector('#sync-outage.show');
    const over = await pg.evaluate(() => document.documentElement.scrollWidth - window.innerWidth);
    assert.ok(over <= 1, `نوار بیرون زد (+${over}px)`);
    await pg.addScriptTag({ content: axeSrc });
    const run = (sel) => pg.evaluate(async (q) => {
      const out = await window.axe.run(document.querySelector(q), { runOnly: ['color-contrast', 'button-name', 'aria-allowed-role', 'aria-roles', 'link-name'] });
      if (!out.passes.some((x) => x.id === 'color-contrast')) return ['color-contrast اجرا نشد'];
      return out.violations.map((x) => `${x.id}: ${x.nodes.slice(0, 3).map((n) => n.target.join(' ')).join(' ; ')}`);
    }, sel);
    (await run('#sync-outage')).forEach((v) => bad.push(`${scheme} نوار — ${v}`));
    (await run('#sync-dot-btn')).forEach((v) => bad.push(`${scheme} نقطه — ${v}`));
    await pg.locator('#sync-outage-more').click();
    await pg.waitForSelector('#acct-sync.open #sy-outage');
    await pg.waitForTimeout(400);
    (await run('#acct-sync')).forEach((v) => bad.push(`${scheme} پنجره — ${v}`));
    await pg.close();
  }
  assert.deepEqual(bad, []);
});

await browser.close();
server.close();
console.log(failed ? `\n${failed} سنجه قرمز` : '\nهمه سبز');
process.exit(failed ? 1 : 0);
