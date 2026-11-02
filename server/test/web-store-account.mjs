/**
 * ویترین با کرومیومِ واقعی: «حسابِ من» — CRM-13 (سرور: `store-account-history.js`، صفحه: `store/account.js`).
 *
 *     npm run test:web-store      (پس از web-store-web.mjs)
 *
 * دکانِ واقعی با قرض‌دارِ همین شماره (فروشِ حضوری و دفترِ قرض از راهِ همگام‌سازیِ واقعی):
 *   • پرچمِ `shop.storefront.account_history` خاموش ⇒ «سفارش‌های من» همان امروز، بی بخشِ «حسابِ من»؛
 *   • روشن و شمارهٔ تأییدشده ⇒ بخشِ آماده (`data-state=ready`)، ماندهٔ «… بدهکارید»، خریدِ حضوری و دفتر؛
 *   • راست‌چین، بی نقضِ CSP، بی خطای جاوااسکریپت و (با axe-core) بی خطای دسترس‌پذیریِ همان بخش.
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

//  ── دکانِ آزمایشی (دادهٔ ساختگی، نه دکانِ واقعی) ──
const email = 'owner@account.local', password = 'Passw0rd!test';
const s1 = await call('POST', '/api/auth/register/start', { name: 'صاحب', email, password });
const v1 = await call('POST', '/api/auth/register/verify', { email, code: s1.body.devCode });
const d1 = await call('POST', '/api/auth/register/complete', { ticket: v1.body.ticket, name: 'صاحب', password,
  device: { deviceId: 'dev-owner', name: 'تست', platform: 'test' }, app: 'shop', terms: { accepted: true } });
const T = d1.body.accessToken;
const shopId = (await call('POST', '/api/shop', { name: 'دکانِ حساب' }, T)).body.shop.id;
const A = (await call('POST', '/api/admin/login', { username: 'admin', password: 'Admin!12345' })).body.token;
const flag = (key, value) => call('PUT', '/api/admin/pump/live-config', { scope: shopId, key, value }, A);
for (const key of ['shop.orders', 'shop.storefront']) await flag(key, true);
const slug = 'account-store';
const put = await call('PUT', '/api/shop/storefront', { slug, enabled: true, title: 'ویترینِ حساب', contactPhone: '0700000001', acceptPickup: true, minOrderMinor: 0 }, T);
ok(put.status === 200, 'ویترین ساخته و روشن شد', JSON.stringify(put.body).slice(0, 120));

const AT = Date.UTC(2026, 9, 20, 8, 0);
const op = (table, id, fields) => ({ op_id: `op-${id}-${Date.now()}`, ts: Date.now(), table, row_id: id, type: 'insert', fields });
const pushed = await call('POST', '/api/sync/v1/push', { device_id: 'dev-owner', schema_version: 2, ops: [
  op('debtors', 'd1', { name: 'مشتری', phone: '0799 100 020', createdAt: AT }),
  op('sales', 's1', { invoiceNumber: 12, total: 1500, finalTotal: 1500, paymentMethod: 'credit', paidAmount: 0, remaining: 1500,
    status: 'completed', createdAt: AT, date: '2026-10-20', seller: 'فروشنده', debtorId: 'd1' }),
  op('saleItems', 's1-i1', { saleId: 's1', productId: '', name: 'برنج', quantity: 3, unitPrice: 500, totalPrice: 1500, returnedQty: 0 }),
  op('transactions', 't1', { debtorId: 'd1', type: 'give', amount: 1500, date: '2026-10-20', notes: 'فاکتور #12', createdAt: AT }),
  op('transactions', 't2', { debtorId: 'd1', type: 'receive', amount: 500, date: '2026-10-22', createdAt: AT + 1 }),
] }, T, { 'X-App': 'shop', 'X-Device': 'dev-owner' });
ok(pushed.status === 200, 'قرض‌دار، فروش و دفتر از راهِ همگام‌سازیِ واقعی رسید', JSON.stringify(pushed.body).slice(0, 120));

const phone = '+93799100020';
const vs = await call('POST', `/api/store/${slug}/verify/start`, { phone });
const vc = await call('POST', `/api/store/${slug}/verify/check`, { phone, code: vs.body.devCode });
ok(Boolean(vc.body && vc.body.ticket), 'شماره تأیید شد');

const browser = await chromium.launch({ executablePath: '/opt/pw-browsers/chromium/chrome-linux/chrome' }).catch(() => chromium.launch());
const errors = [];
const violations = [];
const ctx = await browser.newContext({ viewport: { width: 360, height: 760 }, locale: 'fa-AF' });
await ctx.addInitScript((t) => { localStorage.setItem('store-ticket', JSON.stringify(t)); }, { phone, ticket: vc.body.ticket, expiresAt: vc.body.expiresAt });
const page = await ctx.newPage();
page.on('pageerror', (e) => errors.push(String(e)));
page.on('console', (m) => { if (/Content Security Policy/i.test(m.text())) violations.push(m.text()); });
const text = async (sel) => ((await page.textContent(sel)) || '').trim();

//  ── پرچم خاموش: همان «سفارش‌های من» ──
const ordersSeen = page.waitForResponse((r) => r.url().endsWith(`/api/store/${slug}/orders`), { timeout: 15000 });
await page.goto(`${base}/store/${slug}#/orders`);
await page.waitForSelector('#view-orders:not([hidden])', { timeout: 15000 });
await ordersSeen;
await page.waitForLoadState('networkidle');
ok(!(await page.$('#my-account')), 'خاموش ⇒ بخشِ «حسابِ من» نیست');
ok(!(await page.$('script[src="/store/account.js"]')), 'خاموش ⇒ account.js بار نشد');
ok(await page.isVisible('#my-orders-empty'), 'خاموش ⇒ همان «هنوز سفارشی نیست»');

//  ── پرچم روشن ──
await flag('shop.storefront.account_history', true);
await page.reload();
await page.waitForSelector('#my-account[data-state="ready"]', { timeout: 15000 });
ok((await text('#ah-balance')).includes('۱٬۰۰۰') && (await text('#ah-balance')).includes('بدهکارید'), 'مانده: ۱٬۰۰۰ افغانی بدهکارید', await text('#ah-balance'));
ok(await page.locator('#ah-purchases li').count() === 1, 'یک خریدِ حضوری');
ok((await text('#ah-purchases')).includes('۱٬۵۰۰') && (await text('#ah-purchases')).includes('نسیه'), 'خریدِ حضوری با جمع و «نسیه»', await text('#ah-purchases'));
ok(!(await text('#my-account')).includes('فروشنده'), 'نامِ فروشنده دیده نمی‌شود');
ok(await page.locator('#ah-ledger li').count() === 2, 'دو ردیفِ دفترِ قرض');
ok(await page.isHidden('#my-orders') && await page.isHidden('#my-orders-empty'), 'فهرستِ قدیم جایش را به «حسابِ من» داد');
ok(await page.evaluate(() => getComputedStyle(document.getElementById('my-account')).direction) === 'rtl', 'راست‌چین');
ok(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth + 1), 'بی بیرون‌زدگی در ۳۶۰');

if (axeSrc) {
  const axeCtx = await browser.newContext({ viewport: { width: 360, height: 760 }, bypassCSP: true });
  await axeCtx.addInitScript((t) => { localStorage.setItem('store-ticket', JSON.stringify(t)); }, { phone, ticket: vc.body.ticket, expiresAt: vc.body.expiresAt });
  const ap = await axeCtx.newPage();
  await ap.goto(`${base}/store/${slug}#/orders`);
  await ap.waitForSelector('#my-account[data-state="ready"]', { timeout: 15000 });
  await ap.addScriptTag({ content: axeSrc });
  const v = await ap.evaluate(async () => (await window.axe.run('#my-account',
    { runOnly: ['color-contrast', 'button-name', 'link-name', 'list', 'listitem', 'heading-order'] })).violations
    .map((x) => `${x.id}: ${x.nodes.slice(0, 2).map((n) => n.target.join(' ')).join(' ; ')}`));
  ok(v.length === 0, 'دسترس‌پذیری (axe-core) ِ «حسابِ من»', v.join(' | '));
  await axeCtx.close();
} else if (SKIP) {
  console.log('⏭  axe-core نیست و SKIP_BROWSER_TESTS=1 — سنجهٔ دسترس‌پذیری اجرا نشد');
} else {
  ok(false, 'axe-core پیدا نشد (npm i -g axe-core@4.10.3، یا AXE_PATH)');
}

ok(violations.length === 0, 'هیچ نقضِ CSP', violations.join(' | '));
ok(errors.length === 0, 'بی خطای جاوااسکریپت', errors.join(' | '));

await ctx.close();
await browser.close();
server.close();
await closeDb();
console.log(bad ? `❌ ${bad} ایراد` : '✅ ویترین: «حسابِ من» — خریدِ حضوری و ماندهٔ دفترِ قرض');
process.exit(bad ? 1 : 0);
