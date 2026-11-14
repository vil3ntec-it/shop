/**
 * ویترین با کرومیومِ واقعی: «درخواستِ برگشت» — RET-11 (صفحه: `store/return-request.js` ِ دیربار از `store/order.js` و
 * `store/acct.js`؛ سرور: `lib/order-return-requests*.js`؛ قرارداد: `order-return-requests.test.js`).
 *
 *     npm run test:web-store
 *
 *   • ⛔ پرچمِ `shop.storefront.return_requests` خاموش ⇒ نه return-request.js، نه واژه‌نامه‌اش، نه #o-rr؛
 *   • روشن و سفارشِ تحویل‌شده، بی نشست ⇒ «نخست وارد حسابِ خود شوید» و پیوندِ ورود، بی فرم؛
 *   • با نشست ⇒ فرم: قلم و اندازه (رقمِ فارسی)، دلیل، متن، عکس (از راهِ chat-photo.js)، روشِ رساندن ⇒ یک درخواست با یک عکس،
 *     وضعیتِ «در انتظارِ بررسی» در همان صفحه و در «سفارش‌های من»؛ پذیرفتنِ دکان ⇒ «پذیرفته شد» و «کالا را به دکان بیاورید»؛
 *   • بیرون از مهلت ⇒ پیامِ روشن، بی دکمه؛ en و ps از واژه‌نامهٔ خودشان؛
 *   • راست‌چین، بی بیرون‌زدگی در ۳۶۰، بی نقضِ CSP، بی خطای جاوااسکریپت و (axe-core) بی خطای دسترس‌پذیریِ همان بخش.
 */
import { createRequire } from 'node:module';
import { missingTool, PLAYWRIGHT_INSTALL, SKIP } from './browser-guard.mjs';
import { execSync } from 'node:child_process';
import fs from 'node:fs';
import path from 'node:path';

const require = createRequire(import.meta.url);
const globalRoot = (() => { try { return execSync('npm root -g', { encoding: 'utf8' }).trim(); } catch { return ''; } })();
let chromium = null;
for (const p of [globalRoot && `${globalRoot}/playwright`, 'playwright']) {
  if (!p) continue;
  try { ({ chromium } = require(p)); break; } catch { /* بعدی */ }
}
if (!chromium) missingTool('پلی‌رایت', PLAYWRIGHT_INSTALL);
let axeSrc = null;
for (const p of [`${globalRoot}/axe-core/axe.min.js`, path.join(import.meta.dirname, '..', 'node_modules', 'axe-core', 'axe.min.js'), process.env.AXE_PATH || '']) {
  if (p && fs.existsSync(p)) { axeSrc = fs.readFileSync(p, 'utf8'); break; }
}

process.env.NODE_ENV = 'test';
process.env.DATABASE_URL = process.env.TEST_DATABASE_URL || 'postgres://shop:shoppass@127.0.0.1:5432/shop_test';
process.env.API_SECRET = 'test-secret-test-secret-test-sec';
process.env.OTP_SECRET = 'test-otp-secret-test-otp-secret1';
process.env.BACKUP_ENABLED = 'false';
process.env.RATE_GENERAL_MAX = '100000';
process.env.RATE_STORE_CHECKOUT_MAX = '100000';
process.env.OTP_RESEND_SECONDS = '0';
process.env.OTP_UNIFORM_MS = '0';

const { createApp } = require('../src/app');
const { query, one, newId, now, closeDb } = require('../src/db');
const migrate = require('../src/migrate');
const pw = require('../src/lib/password');
const F = require('./image-fixtures');

let bad = 0;
const ok = (c, m, d = '') => { if (!c) bad++; console.log((c ? '✅ ' : '❌ ') + m + (d ? ` — ${d}` : '')); };
const dictOf = (l) => ({ ...JSON.parse(fs.readFileSync(path.join(import.meta.dirname, '..', 'public', 'kit', 'i18n', `returns.${l}.json`), 'utf8')) });
const D = { fa: dictOf('fa'), en: dictOf('en'), ps: dictOf('ps') };

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

//  ── دکانِ آزمایشی (دادهٔ ساختگی، نه دکانِ واقعی) ──
const email = 'owner@returns.local', password = 'Passw0rd!test';
const s1 = await call('POST', '/api/auth/register/start', { name: 'صاحب', email, password });
const v1 = await call('POST', '/api/auth/register/verify', { email, code: s1.body.devCode });
const d1 = await call('POST', '/api/auth/register/complete', { ticket: v1.body.ticket, name: 'صاحب', password,
  device: { deviceId: 'dev-owner', name: 'تست', platform: 'test' }, app: 'shop', terms: { accepted: true } });
const T = d1.body.accessToken;
const shopId = (await call('POST', '/api/shop', { name: 'دکانِ برگشت' }, T)).body.shop.id;
const A = (await call('POST', '/api/admin/login', { username: 'admin', password: 'Admin!12345' })).body.token;
const flag = (key, value) => call('PUT', '/api/admin/pump/live-config', { scope: shopId, key, value }, A);
for (const key of ['shop.orders', 'shop.storefront', 'shop.storefront.web_account']) await flag(key, true);
const slug = 'returns-store';
const put = await call('PUT', '/api/shop/storefront', { slug, enabled: true, title: 'ویترینِ برگشت', contactPhone: '0700000001',
  acceptPickup: true, deliveryFeeMinor: 0, minOrderMinor: 0 }, T);
ok(put.status === 200, 'ویترین ساخته و روشن شد', JSON.stringify(put.body).slice(0, 120));
const pushed = await call('POST', '/api/sync/v1/push', { device_id: 'dev-owner', schema_version: 2, ops: [
  { op_id: `op-rice-${Date.now()}`, ts: Date.now(), table: 'products', row_id: 'rice', type: 'insert', fields: { name: 'برنج', unit: 'کیلو', salePrice: 300, category: 'خوراکی', active: true } },
] }, T, { 'X-App': 'shop', 'X-Device': 'dev-owner' });
ok(pushed.status === 200, 'کالا از راهِ همگام‌سازیِ واقعی رسید');
await call('PUT', '/api/shop/storefront/products/rice', { published: true, onlinePriceMinor: null, maxQtyMilli: null }, T);

const phone = '0791114455';
const vs = await call('POST', `/api/store/${slug}/verify/start`, { phone });
const vc = await call('POST', `/api/store/${slug}/verify/check`, { phone, code: vs.body.devCode });
const acc = await call('POST', `/api/store/${slug}/account`, { name: 'مشتری' }, null, { 'X-Store-Ticket': vc.body.ticket });
ok(acc.status === 201, 'حسابِ وبِ مشتری ساخته شد', JSON.stringify(acc.body).slice(0, 120));
const placed = await call('POST', `/api/store/${slug}/checkout`, { fulfillment: 'pickup', customer: { name: 'مشتری', phone }, lines: [{ productId: 'rice', qtyMilli: 5000 }] },
  null, { 'X-Store-Ticket': vc.body.ticket, 'Idempotency-Key': 'returns-web-order-1' });
ok(placed.status === 201, 'یک سفارش با همین حساب', JSON.stringify(placed.body).slice(0, 160));
const orderId = (await one('SELECT id FROM online_orders WHERE shop_id=$1 AND order_no=$2', [shopId, placed.body.order.orderNo])).id;
for (const to of ['accepted', 'picking', 'packed', 'ready_for_pickup', 'picked_up']) await call('POST', `/api/shop/orders/${orderId}/transition`, { to }, T);
ok((await one('SELECT status FROM online_orders WHERE id=$1', [orderId])).status === 'picked_up', 'سفارش تحویل شد');
const token = placed.body.order.token;

const browser = await chromium.launch({ executablePath: '/opt/pw-browsers/chromium/chrome-linux/chrome' }).catch(() => chromium.launch());
const errors = [];
const violations = [];
const loads = [];
const session = { token: acc.body.session, expiresAt: acc.body.expiresAt, phone: acc.body.customer.phone, name: 'مشتری' };
const seed = (c, withSession) => c.addInitScript(([s, sess, order]) => {
  if (localStorage.getItem('seeded')) return;
  localStorage.setItem('seeded', '1');
  if (sess) localStorage.setItem('store-session', JSON.stringify(sess));
  localStorage.setItem(`store-orders:${s}`, JSON.stringify([order]));
}, [slug, withSession ? session : null, { token, orderNo: placed.body.order.orderNo, at: Date.now() }]);
async function pageOf(withSession = true, lang = '') {
  const ctx = await browser.newContext({ viewport: { width: 360, height: 760 }, locale: lang === 'en' ? 'en-GB' : 'fa-AF' });
  await seed(ctx, withSession);
  const page = await ctx.newPage();
  page.on('pageerror', (e) => errors.push(String(e)));
  page.on('console', (m) => { if (/Content Security Policy/i.test(m.text())) violations.push(m.text()); });
  page.on('request', (r) => { if (/\/store\/return-request\.js|returns\.\w+\.json/.test(r.url())) loads.push(r.url()); });
  return { ctx, page };
}
const openOrder = async (page, lang = '') => {
  await page.goto(`${base}/store/${slug}?r=${Date.now()}${lang ? `&lang=${lang}` : ''}#/o/${encodeURIComponent(token)}`);
  await page.waitForSelector('#view-order:not([hidden]) #o-status', { timeout: 15000 });
  await page.waitForLoadState('networkidle');
};
const text = async (page, sel) => ((await page.textContent(sel)) || '').trim();

//  ── ⛔ پرچمِ خاموش ──
let { ctx, page } = await pageOf();
await openOrder(page);
ok(!(await page.$('#o-rr')), 'خاموش ⇒ بخشِ برگشت نیست');
ok(loads.length === 0, 'خاموش ⇒ نه return-request.js، نه واژه‌نامه‌اش', loads.join(' '));

//  ── روشن، بی نشست ⇒ پیامِ ورود ──
await flag('shop.storefront.return_requests', true);
const anon = await pageOf(false);
await openOrder(anon.page);
await anon.page.waitForSelector('#o-rr:not([hidden]) #rr-signin', { timeout: 15000 });
ok((await text(anon.page, '#rr-why')) === D.fa['rr.signin'], 'بی نشست ⇒ «نخست وارد حسابِ خود شوید»', await text(anon.page, '#rr-why'));
ok(!(await anon.page.$('#rr-start')) && !(await anon.page.$('#rr-form')), 'بی نشست ⇒ بی فرم');
ok((await anon.page.getAttribute('#rr-signin', 'href')) === '#/orders', 'پیوندِ ورود به «سفارش‌های من»');
await anon.ctx.close();

//  ── با نشست: فرم و فرستادن ──
await openOrder(page);
await page.waitForSelector('#o-rr:not([hidden]) #rr-start', { timeout: 15000 });
ok(loads.some((u) => /return-request\.js/.test(u)), 'روشن ⇒ return-request.js دیربار آمد');
ok((await text(page, '#rr-h')) === D.fa['rr.title'], 'عنوان از واژه‌نامه', await text(page, '#rr-h'));
await page.click('#rr-start');
await page.waitForSelector('#rr-form', { timeout: 15000 });
ok((await page.$$('input[name="rr-method"]')).length === 1, 'بی پیکِ دکان فقط «خودم می‌آورم»');
//  هیچ قلمی ⇒ پیامِ روشن، بی درخواست
await page.click('#rr-send');
ok((await text(page, '#rr-msg')) === D.fa['rr.pick'], 'بی قلم ⇒ «دست‌کم یک قلم …»', await text(page, '#rr-msg'));
await page.check('#rr-form input[type="checkbox"]');
await page.fill('#rr-form input[inputmode="decimal"]', '۲٫۵');
await page.selectOption('#rr-reason', 'damaged');
await page.fill('#rr-text', 'کیسه پاره رسید');
await page.setInputFiles('#rr-photos', { name: 'bag.jpg', mimeType: 'image/jpeg', buffer: F.jpegImage(96, 64, (x, y) => (x * 3 + y * 2) % 256) });
ok(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth + 1), 'فرم: بی بیرون‌زدگی در ۳۶۰');
ok(await page.evaluate(() => getComputedStyle(document.getElementById('o-rr')).direction) === 'rtl', 'راست‌چین');
await page.click('#rr-send');
await page.waitForSelector('#rr-list li', { timeout: 20000 });
const row = await one('SELECT id, status, reason, body, method, lines FROM order_return_requests WHERE order_id=$1', [orderId]);
ok(row && row.status === 'pending' && row.reason === 'damaged' && row.body === 'کیسه پاره رسید' && row.method === 'drop_off', 'یک درخواست با همان دلیل، متن و روش', JSON.stringify(row));
ok(row && JSON.stringify(row.lines) === JSON.stringify([{ lineNo: 1, qtyMilli: 2500 }]), 'اندازهٔ ۲٫۵ ⇒ ۲۵۰۰ هزارم', JSON.stringify(row && row.lines));
await page.waitForFunction(() => /۱ عکس/.test((document.getElementById('rr-list') || {}).textContent || ''), null, { timeout: 20000 }).catch(() => null);
const photos = (await one('SELECT COUNT(*)::int AS n, MIN(mime) AS mime FROM order_return_request_photos WHERE request_id=$1', [row.id]));
ok(photos.n === 1 && photos.mime === 'image/jpeg', 'عکس از راهِ chat-photo.js رسید (JPEG)', JSON.stringify(photos));
ok((await text(page, '#rr-list')).includes(D.fa['rr.s.pending']), 'وضعیت: «در انتظارِ بررسی»', await text(page, '#rr-list'));
ok((await text(page, '#rr-why')) === D.fa['rr.why.open'], 'درخواستِ باز ⇒ بی فرمِ دوم', await text(page, '#rr-why'));

//  ── «سفارش‌های من» ──
await page.goto(`${base}/store/${slug}?r=${Date.now()}#/orders`);
await page.waitForSelector('#rr-mine li', { timeout: 15000 });
ok((await text(page, '#rr-mine')).includes(D.fa['rr.mine']) && (await text(page, '#rr-mine')).includes(D.fa['rr.s.pending']), '«سفارش‌های من»: درخواست با وضعیت', await text(page, '#rr-mine'));
ok(Boolean(await page.$(`#rr-mine a[href="#/o/${encodeURIComponent(token)}"]`)), 'پیوند به صفحهٔ همان سفارش');

//  ── دکان می‌پذیرد ⇒ «پذیرفته شد» و گامِ بعد ──
const accepted = await call('POST', `/api/shop/order-returns/${row.id}/accept`, {}, T);
ok(accepted.status === 200 && accepted.body.request.status === 'accepted', 'دکان پذیرفت', JSON.stringify(accepted.body).slice(0, 120));
await openOrder(page);
await page.waitForSelector('#rr-list li', { timeout: 15000 });
ok((await text(page, '#rr-list')).includes(D.fa['rr.s.accepted']) && (await text(page, '#rr-list')).includes(D.fa['rr.a.drop_off']), '«پذیرفته شد» و «کالا را به دکان بیاورید»', await text(page, '#rr-list'));

if (axeSrc) {
  //  CSP ِ سختِ ویترین اسکریپتِ درون‌خطی را نمی‌پذیرد ⇒ همان صفحه در زمینهٔ bypassCSP
  const ax = await browser.newContext({ viewport: { width: 360, height: 760 }, bypassCSP: true });
  await seed(ax, true);
  const ap = await ax.newPage();
  await ap.goto(`${base}/store/${slug}#/o/${encodeURIComponent(token)}`);
  await ap.waitForSelector('#rr-list li', { timeout: 15000 });
  await ap.addScriptTag({ content: axeSrc });
  const v = await ap.evaluate(async () => (await window.axe.run('#o-rr',
    { runOnly: ['color-contrast', 'button-name', 'label', 'list', 'listitem', 'aria-allowed-attr', 'nested-interactive', 'link-name'] })).violations
    .map((x) => `${x.id}: ${x.nodes.slice(0, 2).map((n) => n.target.join(' ')).join(' ; ')}`));
  await ax.close();
  ok(v.length === 0, 'دسترس‌پذیری (axe-core) ِ بخشِ برگشت', v.join(' | '));
} else if (SKIP) {
  console.log('⏭  axe-core نیست و SKIP_BROWSER_TESTS=1 — سنجهٔ دسترس‌پذیری اجرا نشد');
} else {
  ok(false, 'axe-core پیدا نشد (npm i -g axe-core@4.10.3، یا AXE_PATH)');
}

//  ── رد ⇒ قلم آزاد؛ بیرون از مهلت ⇒ پیامِ روشن در سه زبان ──
ok((await call('POST', `/api/shop/order-returns/${row.id}/reject`, { note: 'کالا نرسید' }, T)).status === 200, 'دکان رد کرد');
await flag('shop.settings_sync', true);
await query(`INSERT INTO shop_setting_keys (shop_id, store, key, type, value, rev, updated_at, updated_by)
             VALUES ($1,'tohid-policy','return_window_days','i','3'::jsonb,1,$2,'t')`, [shopId, now()]);
await query(`UPDATE online_order_events SET created_at = created_at - $2 WHERE order_id=$1 AND to_status='picked_up'`, [orderId, 9 * 864e5]);
for (const lang of ['fa', 'en', 'ps']) {
  const p = lang === 'fa' ? { ctx, page } : await pageOf(true, lang);
  await openOrder(p.page, lang === 'fa' ? '' : lang);
  await p.page.waitForSelector('#o-rr:not([hidden]) #rr-why', { timeout: 15000 });
  const want = D[lang]['rr.why.late'].replace('{n}', lang === 'en' ? '3' : '۳');
  ok((await text(p.page, '#rr-why')) === want, `${lang}: بیرون از مهلت ⇒ «${want}»`, await text(p.page, '#rr-why'));
  ok(!(await p.page.$('#rr-start')), `${lang}: بیرون از مهلت ⇒ بی دکمه`);
  ok((await text(p.page, '#rr-list')).includes(D[lang]['rr.s.rejected']) && (await text(p.page, '#rr-list')).includes('کالا نرسید'), `${lang}: «رد شد» با دلیلِ دکان`);
  if (lang !== 'fa') await p.ctx.close();
}

ok(violations.length === 0, 'هیچ نقضِ CSP', violations.join(' | '));
ok(errors.length === 0, 'بی خطای جاوااسکریپت', errors.join(' | '));

await ctx.close();
await browser.close();
server.close();
await closeDb();
console.log(bad ? `❌ ${bad} ایراد` : '✅ ویترین: «درخواستِ برگشت»');
process.exit(bad ? 1 : 0);
