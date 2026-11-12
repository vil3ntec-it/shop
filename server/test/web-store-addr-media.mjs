/**
 * ویترین با کرومیومِ واقعی: عکسِ دروازه و یادداشتِ صوتیِ نشانی در صفحهٔ پیگیری (CHK-08؛ `lib/order-address-media.js`،
 * `store/addr-media.js` ِ دیربار، پرچمِ خاموشِ `shop.storefront.address_media`).
 *
 *     npm run test:web-store      (پس از web-store-weigh.mjs)
 *
 * order-address-media.test.js همین را روی API قفل می‌کند؛ این‌جا خودِ صفحه، در عرضِ گوشی (۳۶۰):
 *   • پرچمِ خاموش ⇒ نه addr-media.js، نه #o-am، نه درخواست.
 *   • روشن، سفارشِ ارسالیِ باز ⇒ کارتِ «به پیک کمک کنید» (axe)؛ انتخابِ عکس (PNG) ⇒ JPEG ِ کوچک‌شده روی سرور و «فرستاده شد»
 *     فقط پس از پاسخِ سرور؛ «برداشتنِ عکس» ⇒ ردیف رفت.
 *   • ضبطِ صدا با میکروفونِ ساختگیِ کرومیوم ⇒ «پایان و فرستادن» ⇒ ردیفِ `voice_note` با صدای شناخته‌شده؛ «لغو» ⇒ هیچ.
 *   • سفارشِ تحویل از دکان ⇒ کارت نیست؛ بی نقضِ CSP و بی خطای جاوااسکریپت.
 */
import { createRequire } from 'node:module';
import fs from 'node:fs';
import path from 'node:path';
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
process.env.RATE_STORE_CHECKOUT_MAX = '1000';
process.env.OTP_RESEND_SECONDS = '0';
process.env.OTP_UNIFORM_MS = '0';

const { createApp } = require('../src/app');
const { query, one, newId, now, closeDb } = require('../src/db');
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

//  ── دکانِ آزمایشی (دادهٔ ساختگی، نه دکانِ واقعی) ──
const email = 'owner@addrmedia.local', password = 'Passw0rd!test';
const s1 = await call('POST', '/api/auth/register/start', { name: 'صاحب', email, password });
const v1 = await call('POST', '/api/auth/register/verify', { email, code: s1.body.devCode });
const d1 = await call('POST', '/api/auth/register/complete', { ticket: v1.body.ticket, name: 'صاحب', password,
  device: { deviceId: 'dev-owner', name: 'تست', platform: 'test' }, app: 'shop', terms: { accepted: true } });
const T = d1.body.accessToken;
const shopId = (await call('POST', '/api/shop', { name: 'دکانِ دروازه' }, T)).body.shop.id;
const A = (await call('POST', '/api/admin/login', { username: 'admin', password: 'Admin!12345' })).body.token;
const flag = (key, value) => call('PUT', '/api/admin/pump/live-config', { scope: shopId, key, value }, A);
for (const key of ['shop.orders', 'shop.storefront']) await flag(key, true);
for (const key of require('../src/lib/flag-defaults').DEFAULT_ON) await flag(key, false);
const slug = 'gate-store';
const put = await call('PUT', '/api/shop/storefront', { slug, enabled: true, title: 'ویترینِ دروازه', contactPhone: '0700000001',
  acceptPickup: true, deliveryFeeMinor: 5000, minOrderMinor: 0 }, T);
ok(put.status === 200, 'ویترین روشن', JSON.stringify(put.body).slice(0, 120));
await call('POST', '/api/sync/v1/push', { device_id: 'dev-owner', schema_version: 2, ops: [
  { op_id: `op-rice-${Date.now()}`, ts: Date.now(), table: 'products', row_id: 'rice', type: 'insert',
    fields: { name: 'برنج', unit: 'دانه', salePrice: 300, category: 'خوراکی', active: true } },
] }, T, { 'X-App': 'shop', 'X-Device': 'dev-owner' });
await call('PUT', '/api/shop/storefront/products/rice', { published: true }, T);

const phone = '0791112277';
const vs = await call('POST', `/api/store/${slug}/verify/start`, { phone });
const vc = await call('POST', `/api/store/${slug}/verify/check`, { phone, code: vs.body.devCode });
ok(vc.status === 200 && vc.body.ticket, 'شماره تأیید شد');
let seq = 0;
async function placed(fulfillment) {
  const ck = await call('POST', `/api/store/${slug}/checkout`, { fulfillment, customer: { name: 'مشتری', phone },
    ...(fulfillment === 'delivery' ? { address: { landmark: 'پشتِ مسجدِ سبز' } } : {}), lines: [{ productId: 'rice', qtyMilli: 1000 }] },
  null, { 'Idempotency-Key': `web-gate-${++seq}-${Date.now()}`, 'X-Store-Ticket': vc.body.ticket });
  ok(ck.status === 201, `سفارشِ ${fulfillment} ثبت شد`, JSON.stringify(ck.body).slice(0, 120));
  const id = (await one('SELECT id FROM online_orders WHERE shop_id=$1 ORDER BY order_no DESC LIMIT 1', [shopId])).id;
  return { id, token: ck.body.order.token };
}
const rows = (o) => query('SELECT kind, mime, size FROM order_address_media WHERE order_id=$1 ORDER BY kind', [o.id]).then((r) => r.rows);

const browser = await chromium.launch({ executablePath: '/opt/pw-browsers/chromium/chrome-linux/chrome',
  args: ['--use-fake-ui-for-media-stream', '--use-fake-device-for-media-stream'] })
  .catch(() => chromium.launch({ args: ['--use-fake-ui-for-media-stream', '--use-fake-device-for-media-stream'] }));
const errors = [];
const violations = [];
const ctx = await browser.newContext({ viewport: { width: 360, height: 760 }, locale: 'fa-AF', permissions: ['microphone'] });
//  میکروفون: هر جریانِ `getUserMedia` نگه داشته می‌شود تا «پس از ضبط بسته» راستی سنجیده شود
await ctx.addInitScript(() => {
  const md = navigator.mediaDevices;
  if (!md || !md.getUserMedia) return;
  const g = md.getUserMedia.bind(md);
  window.__streams = [];
  md.getUserMedia = (c) => g(c).then((st) => { window.__streams.push(st); return st; });
});
const page = await ctx.newPage();
page.on('pageerror', (e) => errors.push(String(e)));
page.on('console', (m) => { if (/Content Security Policy/i.test(m.text())) violations.push(m.text()); });
const open = async (o) => {
  await page.goto('about:blank');
  await page.goto(`${base}/store/${slug}#/o/${encodeURIComponent(o.token)}`);
  await page.waitForFunction(() => document.querySelectorAll('#o-lines li').length > 0, null, { timeout: 15000 });
  await page.waitForTimeout(300);
};
const text = (sel) => page.$eval(sel, (e) => e.textContent).catch(() => '');
const waitNote = (re) => page.waitForFunction((src) => new RegExp(src).test((document.getElementById('o-am-msg') || {}).textContent || ''),
  re.source, { timeout: 15000 }).then(() => true, () => false);
const button = (label) => page.locator('#o-am button', { hasText: label });

//  ── پرچمِ خاموش ──
const off = await placed('delivery');
await open(off);
ok((await page.$('#o-am')) === null && (await page.$('script[src="/store/addr-media.js"]')) === null, 'پرچمِ خاموش ⇒ نه addr-media.js، نه #o-am');

//  ── روشن: عکس ──
await flag('shop.storefront.address_media', true);
const o = await placed('delivery');
await open(o);
await page.waitForSelector('#o-am:not([hidden])', { timeout: 15000 });
ok((await text('#o-am-h')) === 'به پیک کمک کنید (اختیاری)', 'کارت: «به پیک کمک کنید (اختیاری)»', await text('#o-am-h'));
ok((await text('#o-am')).includes('هنوز نفرستاده‌اید'), 'پیش از فرستادن: «هنوز نفرستاده‌اید»');
if (axeSrc) {
  await page.evaluate(axeSrc);
  const v = await page.evaluate(async () => (await window.axe.run(document, { runOnly: { type: 'tag', values: ['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa'] } }))
    .violations.map((x) => `${x.id}: ${x.nodes.slice(0, 3).map((nd) => nd.target.join(' ')).join(' ; ')}`));
  ok(v.length === 0, 'دسترس‌پذیری (axe-core، WCAG 2.1 A/AA): کارتِ عکس و صدای نشانی', v.join(' | '));
} else ok(false, 'axe-core پیدا نشد (AXE_PATH)');
await page.setInputFiles('#o-am-file', path.join(import.meta.dirname, '..', 'public', 'kit', 'icon-192.png'));
ok(await waitNote(/عکسِ دروازه فرستاده شد/), '«عکسِ دروازه فرستاده شد» پس از پاسخِ سرور', await text('#o-am-msg'));
let r = await rows(o);
ok(r.length === 1 && r[0].kind === 'gate_photo' && r[0].mime === 'image/jpeg', 'سرور: JPEG ِ تازه از روی PNG ِ برگزیده', JSON.stringify(r));
await button('برداشتنِ عکس').click();
ok(await waitNote(/برداشته شد/), '«برداشتنِ عکس» ⇒ «برداشته شد»');
ok((await rows(o)).length === 0, 'سرور: ردیفِ عکس رفت');

//  ── صدا (میکروفونِ ساختگی) ──
await button('ضبطِ یادداشتِ صوتی').click();
await page.waitForSelector('#o-am-left', { timeout: 10000 });
await page.waitForTimeout(1600);
await button('لغو').click();
ok(await waitNote(/ضبط لغو شد/), '«لغو» ⇒ «ضبط لغو شد» و هیچ فرستادنی');
ok((await rows(o)).length === 0, 'لغو ⇒ هیچ ردیف');
await button('ضبطِ یادداشتِ صوتی').click();
await page.waitForSelector('#o-am-left', { timeout: 10000 });
await page.waitForTimeout(2200);
await button('پایان و فرستادن').click();
ok(await waitNote(/یادداشتِ صوتی فرستاده شد/), '«پایان و فرستادن» ⇒ «یادداشتِ صوتی فرستاده شد»', await text('#o-am-msg'));
r = await rows(o);
ok(r.length === 1 && r[0].kind === 'voice_note' && /^audio\//.test(r[0].mime) && r[0].size > 0, 'سرور: صدای شناخته‌شده از روی بایت', JSON.stringify(r));
ok(await page.evaluate(() => window.__streams.length === 2 && window.__streams.every((st) => st.getTracks().every((tr) => tr.readyState === 'ended'))),
  'میکروفون پس از «لغو» و پس از «فرستادن» بسته (هر track)', String(await page.evaluate(() => window.__streams.length)));
ok((await page.$('script[src="/store/addr-media.js"]')) !== null, 'اسکریپتِ دیربار فقط با کلیدِ addressMedia بار شد');

//  ── دریافت از دکان ⇒ کارت نیست ──
const pick = await placed('pickup');
await open(pick);
ok(!(await page.isVisible('#o-am').catch(() => false)), 'سفارشِ «دریافت از دکان» ⇒ بی کارت');

ok(violations.length === 0, 'هیچ نقضِ CSP', violations.join(' | '));
ok(errors.length === 0, 'بی خطای جاوااسکریپت', errors.join(' | '));

await ctx.close();
await browser.close();
server.close();
await closeDb();
console.log(bad ? `❌ ${bad} ایراد` : '✅ ویترین: عکسِ دروازه و یادداشتِ صوتیِ نشانی در صفحهٔ پیگیری');
process.exit(bad ? 1 : 0);
