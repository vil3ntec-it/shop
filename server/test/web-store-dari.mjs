/**
 * ویترین با کرومیومِ واقعی: زبانِ «دری» (`prs`، LOC-06/LOC-08) — فارسی + لایهٔ واژهٔ `kit/dari.js`.
 *
 *     npm run test:web-store      (پس از web-store-method.mjs)
 *
 * dari-text.test.js خودِ لایه را بی مرورگر قفل می‌کند؛ این‌جا خودِ صفحه، در عرضِ گوشی (۳۶۰):
 *   • فارسی دست‌نخورده: «شمارهٔ موبایل» و dari.js هرگز خواسته نمی‌شود؛
 *   • گزینش‌گرِ زبان: فارسی · دری · English · پښتو؛ «دری» ⇒ بارِ دوباره با lang="prs" و dir="rtl"، متنِ صفحه
 *     («شمارهٔ مبایل»، «کرایهٔ ارسال») دری، ولی نامِ دکان («فروشگاه کریم»)، نامِ کالا («گوشی سامسونگ») و دستهٔ
 *     کالا («موبایل») همان که فروشنده نوشته؛ واژه‌نامهٔ هر بخش (`K.part`) هم از fa و هم دری؛
 *   • بارِ دوباره بی ‎?lang=‎ ⇒ همان دری (حافظهٔ مرورگر)؛
 *   • dari.js نیامد ⇒ همان فارسی با lang="fa" (نه ادعای دری)، بی کلیدِ خام و بی خطا؛
 *   • بی نقضِ CSP و بی خطای جاوااسکریپت. هر گام برای نتیجهٔ واقعی (متن/ویژگی) صبر می‌کند، نه فقط رفتنِ یک گره.
 */
import { createRequire } from 'node:module';
import { missingTool, PLAYWRIGHT_INSTALL } from './browser-guard.mjs';
import { execSync } from 'node:child_process';

const require = createRequire(import.meta.url);
const globalRoot = (() => { try { return execSync('npm root -g', { encoding: 'utf8' }).trim(); } catch { return ''; } })();
let chromium = null;
for (const p of [globalRoot && `${globalRoot}/playwright`, 'playwright']) {
  if (!p) continue;
  try { ({ chromium } = require(p)); break; } catch { /* بعدی */ }
}
if (!chromium) missingTool('پلی‌رایت', PLAYWRIGHT_INSTALL);

process.env.NODE_ENV = 'test';
process.env.DATABASE_URL = process.env.TEST_DATABASE_URL || 'postgres://shop:shoppass@127.0.0.1:5432/shop_test';
process.env.API_SECRET = 'test-secret-test-secret-test-sec';
process.env.OTP_SECRET = 'test-otp-secret-test-otp-secret1';
process.env.BACKUP_ENABLED = 'false';
process.env.RATE_GENERAL_MAX = '100000';
process.env.OTP_RESEND_SECONDS = '0';
process.env.OTP_UNIFORM_MS = '0';

const { createApp } = require('../src/app');
const { query, newId, now, closeDb } = require('../src/db');
const migrate = require('../src/migrate');
const pw = require('../src/lib/password');

let bad = 0;
const ok = (c, m, d = '') => { if (!c) bad++; console.log((c ? '✅ ' : '❌ ') + m + (d ? ` — ${d}` : '')); };

await query('DROP SCHEMA public CASCADE');
await query('CREATE SCHEMA public');
await migrate.run();
await query(`INSERT INTO admins (id, username, name, password_hash, role, status, created_at)
             VALUES ($1,'admin','مدیر',$2,'superadmin','active',$3)`, [newId('adm'), await pw.hashPassword('Admin!12345'), now()]);
const app = await createApp({ runMigrations: false });
const server = await new Promise((r) => { const s = app.listen(0, '127.0.0.1', () => r(s)); });
const base = `http://127.0.0.1:${server.address().port}`;

const call = async (method, p, b, t, headers = {}) => {
  const r = await fetch(base + p, { method, headers: { 'Content-Type': 'application/json', ...(t ? { Authorization: `Bearer ${t}` } : {}), ...headers },
    body: b === undefined || b === null ? undefined : JSON.stringify(b) });
  return { status: r.status, body: await r.json().catch(() => null) };
};

//  ── دکانِ آزمایشی (دادهٔ ساختگی): نام و کالاهایی که واژهٔ فهرست دارند — دادهٔ فروشنده، پس هرگز دری نمی‌شوند ──
const SHOP = 'فروشگاه کریم';
const PRODUCT = 'گوشی سامسونگ';
const CATEGORY = 'موبایل';
const email = 'owner@dari.local', password = 'Passw0rd!test';
const s1 = await call('POST', '/api/auth/register/start', { name: 'صاحب', email, password });
const v1 = await call('POST', '/api/auth/register/verify', { email, code: s1.body.devCode });
const d1 = await call('POST', '/api/auth/register/complete', { ticket: v1.body.ticket, name: 'صاحب', password,
  device: { deviceId: 'dev-owner', name: 'تست', platform: 'test' }, app: 'shop', terms: { accepted: true } });
const T = d1.body.accessToken;
const shopId = (await call('POST', '/api/shop', { name: SHOP }, T)).body.shop.id;
const A = (await call('POST', '/api/admin/login', { username: 'admin', password: 'Admin!12345' })).body.token;
for (const key of ['shop.orders', 'shop.storefront']) await call('PUT', '/api/admin/pump/live-config', { scope: shopId, key, value: true }, A);
const slug = 'dari-store';
const put = await call('PUT', '/api/shop/storefront', { slug, enabled: true, title: SHOP, contactPhone: '0700000001',
  deliveryFeeMinor: 5000, minOrderMinor: 0 }, T);
ok(put.status === 200, 'ویترین ساخته و روشن شد', JSON.stringify(put.body).slice(0, 120));
const pushed = await call('POST', '/api/sync/v1/push', { device_id: 'dev-owner', schema_version: 2, ops: [{
  op_id: `op-phone-${Date.now()}`, ts: Date.now(), table: 'products', row_id: 'phone', type: 'insert',
  fields: { name: PRODUCT, unit: 'دانه', salePrice: 9000, category: CATEGORY, active: true },
}] }, T, { 'X-App': 'shop', 'X-Device': 'dev-owner' });
ok(pushed.status === 200, 'کالا از راهِ همگام‌سازیِ واقعی رسید');
await call('PUT', '/api/shop/storefront/products/phone', { published: true }, T);

const browser = await chromium.launch({ executablePath: '/opt/pw-browsers/chromium/chrome-linux/chrome' }).catch(() => chromium.launch());
const errors = [];
const violations = [];
async function context(route) {
  const ctx = await browser.newContext({ viewport: { width: 360, height: 760 }, locale: 'fa-AF' });
  const seen = [];
  ctx.on('request', (r) => { if (r.url().includes('/kit/')) seen.push(new URL(r.url()).pathname); });
  if (route) await ctx.route('**/kit/dari.js', route);
  const page = await ctx.newPage();
  page.on('pageerror', (e) => errors.push(String(e)));
  page.on('console', (m) => { if (/Content Security Policy/i.test(m.text())) violations.push(m.text()); });
  return { ctx, page, seen };
}
const html = (page) => page.evaluate(() => ({ lang: document.documentElement.lang, dir: document.documentElement.dir }));
/** کاتالوگ با نامِ کالا رسید و کیت متن را جایگزین کرد */
async function catalog(page) {
  await page.waitForFunction((name) => !document.documentElement.hasAttribute('data-i18n')
    && [...document.querySelectorAll('#products .p-name')].some((e) => e.textContent === name), PRODUCT, { timeout: 15000 });
}
/** برگهٔ ثبت با یک قلم؛ صبر تا برچسبِ هزینهٔ ارسال همان متنِ خواسته شود («فقط ایمیل»: برچسبِ شماره حالا «شمارهٔ تماس برای
 *  پیک» است که واژهٔ دری‌شدنی ندارد، پس نشانهٔ دری همان «کرایهٔ ارسال» است) */
async function checkout(page, feeLabel) {
  if (!(await page.evaluate(() => Object.keys(JSON.parse(localStorage.getItem(`store-cart:${location.pathname.split('/').pop()}`) || '{}')).length))) {
    await page.locator('#products li[data-id="phone"] .p-add').click();
    await page.waitForFunction(() => /store-cart/.test(Object.keys(localStorage).join(' ')), null, { timeout: 5000 });
  }
  await page.evaluate(() => { location.hash = '#/checkout'; });
  await page.waitForFunction((want) => {
    const v = document.getElementById('view-checkout');
    const l = document.getElementById('t-fee-label');
    return v && !v.hidden && l && l.textContent === want;
  }, feeLabel, { timeout: 15000 });
}

//  ── ۱) فارسی دست‌نخورده ──
const fa = await context();
await fa.page.goto(`${base}/store/${slug}`);
await catalog(fa.page);
ok(JSON.stringify(await html(fa.page)) === JSON.stringify({ lang: 'fa', dir: 'rtl' }), 'فارسی: lang="fa" و dir="rtl"');
await checkout(fa.page, 'هزینهٔ ارسال');
ok((await fa.page.textContent('#t-fee-label')) === 'هزینهٔ ارسال', 'فارسی: «هزینهٔ ارسال» همان فارسی');
ok(!fa.seen.some((u) => u.endsWith('/dari.js')), 'فارسی: dari.js هرگز خواسته نشد', fa.seen.join(' '));

//  ── ۲) گزینش‌گر: «دری» ⇒ بارِ دوباره با prs ──
await fa.page.evaluate(() => { location.hash = '#/'; });
await catalog(fa.page);
await fa.page.click('.topbar [data-act="lang"]');
await fa.page.waitForSelector('dialog.confirm[open] input[name="lang"]', { timeout: 5000 });
const names = await fa.page.$$eval('dialog.confirm input[name="lang"]', (l) => l.map((r) => `${r.value}:${r.parentElement.textContent.trim()}:${r.parentElement.lang}`));
ok(names.join(' ') === 'fa:فارسی:fa prs:دری:prs en:English:en ps:پښتو:ps', 'گزینش‌گر: فارسی · دری · English · پښتو، هر کدام با lang ِ خودش', names.join(' '));
await Promise.all([
  fa.page.waitForEvent('load', { timeout: 15000 }),
  fa.page.click('dialog.confirm label:has(input[value="prs"])'),
]);
await catalog(fa.page);
const prs = fa.page;
await prs.waitForFunction(() => document.documentElement.lang === 'prs' && window.VILL3N && typeof window.VILL3N.tx === 'function', null, { timeout: 10000 });
ok(JSON.stringify(await html(prs)) === JSON.stringify({ lang: 'prs', dir: 'rtl' }), 'دری: lang="prs" و dir="rtl"');
ok((await prs.textContent('#store-title')) === SHOP, 'دری: نامِ دکان همان «فروشگاه کریم» (دادهٔ فروشنده)', await prs.textContent('#store-title'));
ok((await prs.textContent('#products li[data-id="phone"] .p-name')) === PRODUCT, 'دری: نامِ کالا همان «گوشی سامسونگ»');
const cats = await prs.evaluate(() => document.body.innerText);
ok(cats.includes(CATEGORY) && !cats.includes('مبایل سامسونگ') && !cats.includes('دکان کریم'), 'دری: دستهٔ «موبایل» و هیچ دادهٔ فروشنده دری نشد');
await checkout(prs, 'کرایهٔ ارسال');
ok((await prs.textContent('#t-fee-label')) === 'کرایهٔ ارسال', 'دری: «کرایهٔ ارسال»', await prs.textContent('#t-fee-label'));
//  واژه‌نامهٔ هر بخش (K.part): همان fa.json ِ بخش + لایه، هرگز `.prs.json`
const part = await prs.evaluate(() => window.K.part('method').then((r) => [r, window.K.t('dm.to.pickup'), window.K.t('dm.send')]));
ok(part[0] === true && part[1] === 'دریافت از دکان (بدون کرایهٔ ارسال)' && part[2] === 'دیدنِ هزینهٔ تازه',
  'دری: واژه‌نامهٔ بخش (method) از fa، لایه روی آن و جانشینیِ کلید‌به‌کلید (dm.send فارسی می‌ماند)', part.join(' | '));
//  دورِ ۶: جملهٔ کاملِ دری (`DariText.PHRASES`) در خودِ صفحه — خطای همگانی و شبکه، نه «دوباره بزنید»/«اینترنت را ببینید»
const whole = await prs.evaluate(() => [window.K.t('err.generic'), window.K.t('err.network')]);
ok(whole[0] === 'خطا؛ دوباره کوشش کنید.' && whole[1] === 'اتصال برقرار نشد؛ انترنت را بررسی کنید و دوباره کوشش کنید.',
  'دری: جملهٔ کاملِ خطاها (دورِ ۶)', whole.join(' | '));
ok(fa.seen.some((u) => u === '/kit/i18n/method.fa.json') && !fa.seen.some((u) => /\.prs\.json$|\/prs\.json$/.test(u)) && fa.seen.includes('/kit/dari.js'),
  'دری: fa.json و method.fa.json و dari.js خواسته شد، هیچ prs.json', fa.seen.filter((u) => /i18n|dari/.test(u)).join(' '));
const aria = await prs.getAttribute('.topbar [data-act="lang"]', 'aria-label');
ok(aria === 'زبان', 'دری: برچسبِ دکمهٔ زبان (data-ta) از همان K.t', aria);

//  ── ۳) بارِ دوباره بی ‎?lang=‎ ⇒ همان دری ──
await prs.goto(`${base}/store/${slug}#/`);
await catalog(prs);
await prs.waitForFunction(() => document.documentElement.lang === 'prs', null, { timeout: 10000 });
await checkout(prs, 'کرایهٔ ارسال');
ok(await prs.evaluate(() => localStorage.getItem('vill3n-lang')) === 'prs', 'بارِ دوباره: دری در حافظهٔ همین مرورگر ماند و صفحه دری است');
await prs.evaluate(() => { location.hash = '#/'; });
await catalog(prs);
await prs.click('.topbar [data-act="lang"]');
await prs.waitForSelector('dialog.confirm[open] input[name="lang"]', { timeout: 5000 });
ok(await prs.isChecked('dialog.confirm input[value="prs"]'), 'بارِ دوباره: «دری» در گزینش‌گر برگزیده است');
await fa.ctx.close();

//  ── ۴) dari.js نیامد ⇒ فارسیِ راستین با lang="fa" ──
const down = await context((r) => r.abort('failed'));
await down.page.goto(`${base}/store/${slug}?lang=prs`);
await catalog(down.page);
await down.page.waitForFunction(() => document.documentElement.lang === 'fa', null, { timeout: 10000 });
await checkout(down.page, 'هزینهٔ ارسال');
const dl = await html(down.page);
ok(dl.lang === 'fa' && dl.dir === 'rtl' && (await down.page.textContent('#t-fee-label')) === 'هزینهٔ ارسال',
  'لایه نیامد: همان فارسی و lang="fa" (نه ادعای دری)', JSON.stringify(dl));
ok(await down.page.evaluate(() => localStorage.getItem('vill3n-lang')) === 'prs', 'لایه نیامد: انتخابِ دری می‌ماند تا بارِ بعد دوباره بکوشد');
const raw = await down.page.evaluate(() => /\b(phone\.l|ck\.fee|ck\.title)\b/.test(document.body.innerText));
ok(!raw, 'لایه نیامد: هیچ کلیدِ خام');
await down.ctx.close();

ok(violations.length === 0, 'هیچ نقضِ CSP', violations.join(' | '));
ok(errors.length === 0, 'بی خطای جاوااسکریپت', errors.join(' | '));

await browser.close();
server.close();
await closeDb();
console.log(bad ? `❌ ${bad} ایراد` : '✅ ویترین: دری کنارِ فارسی (لایهٔ واژه، دادهٔ فروشنده دست‌نخورده)');
process.exit(bad ? 1 : 0);
