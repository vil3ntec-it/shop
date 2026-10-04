/**
 * شورا، چ۳ — زبانهٔ «نمایندگی» در پورتالِ مشتری، با کرومیومِ واقعی.
 *
 *     npm run test:portal-rep
 *
 * نماینده با همان پورتال وارد می‌شود و فقط فروش‌های **خودش** را می‌بیند؛
 * مشتریِ معمولی اصلاً زبانه را نمی‌بیند. داده از سرورِ واقعی (همان مسیرهای
 * مدیر) ساخته می‌شود و عددِ روی صفحه با پاسخِ خودِ سرور سنجیده می‌شود.
 */
import { createRequire } from 'node:module';
import { execSync } from 'node:child_process';

const require = createRequire(import.meta.url);
let chromium = null;
for (const p of [(() => { try { return `${execSync('npm root -g', { encoding: 'utf8' }).trim()}/playwright`; } catch { return ''; } })(), 'playwright']) {
  if (!p) continue;
  try { ({ chromium } = require(p)); break; } catch { /* بعدی */ }
}
if (!chromium) { console.log('⏭  پلی‌رایت پیدا نشد — این سنجه رد شد'); process.exit(0); }

process.env.NODE_ENV = 'test';
process.env.DATABASE_URL = process.env.TEST_DATABASE_URL || 'postgres://shop:shoppass@127.0.0.1:5432/shop_test';
process.env.API_SECRET = 'test-secret-test-secret-test-sec';
process.env.OTP_SECRET = 'test-otp-secret-test-otp-secret1';
process.env.BACKUP_ENABLED = 'false';
process.env.RATE_GENERAL_MAX = '100000';
process.env.OTP_RESEND_SECONDS = '0';

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

const call = async (method, p, b, t) => {
  const r = await fetch(base + p, { method, headers: { 'Content-Type': 'application/json', ...(t ? { Authorization: `Bearer ${t}` } : {}) },
    body: b === undefined || b === null ? undefined : JSON.stringify(b) });
  return { status: r.status, body: await r.json().catch(() => null) };
};
async function user(name) {
  const email = `${name}@rep.local`, password = 'Passw0rd!test';
  const s = await call('POST', '/api/auth/register/start', { name, email, password });
  const v = await call('POST', '/api/auth/register/verify', { email, code: s.body.devCode });
  const d = await call('POST', '/api/auth/register/complete', { ticket: v.body.ticket, name, password,
    device: { deviceId: `dev-${name}`, name: 'تست', platform: 'test' }, app: 'shop', terms: { accepted: true } });
  return { email, token: d.body.accessToken };
}

const T = (await call('POST', '/api/admin/login', { username: 'admin', password: 'Admin!12345' })).body.token;
const repU = await user('repone'), otherRep = await user('reptwo');
const r1 = (await call('POST', '/api/admin/reps', { app: 'shop', email: repU.email, commissionPct: 10 }, T)).body.rep;
const r2 = (await call('POST', '/api/admin/reps', { app: 'shop', email: otherRep.email, commissionPct: 5 }, T)).body.rep;
await call('POST', '/api/admin/discount-codes', { app: 'shop', code: 'REPONE', kind: 'percent', value: 10, repId: r1.id }, T);
await call('POST', '/api/admin/discount-codes', { app: 'shop', code: 'REPTWO', kind: 'percent', value: 10, repId: r2.id }, T);
const plan = (await call('GET', '/api/plans?app=shop')).body.plans.find((p) => Number(p.price) > 0);
for (const [name, code] of [['مشتریِ‌یک', 'REPONE'], ['مشتریِ‌دو', 'REPTWO']]) {
  const c = await user(name === 'مشتریِ‌یک' ? 'custone' : 'custtwo');
  const shop = await call('POST', '/api/shop', { name }, c.token);
  await call('POST', '/api/admin/subscriptions', { shopId: shop.body.shop.id, plan: plan.code, discountCode: code }, T);
}
const cust = await user('justcustomer');
await call('POST', '/api/shop', { name: 'دکانِ ساده' }, cust.token);
const truth = (await call('GET', '/api/rep/me', null, repU.token)).body;

const browser = await chromium.launch({ executablePath: '/opt/pw-browsers/chromium/chrome-linux/chrome' }).catch(() => chromium.launch());
const errors = [];
async function open(tok) {
  const ctx = await browser.newContext();
  await ctx.addInitScript((t) => sessionStorage.setItem('vill3n-portal-token', t), tok);
  const page = await ctx.newPage();
  page.on('pageerror', (e) => errors.push(String(e)));
  await page.goto(`${base}/portal/`);
  await page.waitForFunction(() => !document.getElementById('app-view').classList.contains('hidden'), null, { timeout: 15000 });
  await page.waitForTimeout(600);
  return page;
}

const p1 = await open(repU.token);
ok(await p1.isVisible('#tab-btn-rep'), 'نماینده زبانهٔ «نمایندگی» را می‌بیند');
await p1.click('#tab-btn-rep');
await p1.waitForFunction(() => document.querySelectorAll('#rep-body tr').length > 0, null, { timeout: 10000 });
const body = await p1.textContent('#rep-body');
ok(body.includes('مشتریِ‌یک') && !body.includes('مشتریِ‌دو'), '⛔ فقط مشتریِ خودش — نه مشتریِ نمایندهٔ دیگر');
ok((await p1.textContent('#rep-codes')).includes('REPONE') && !(await p1.textContent('#rep-codes')).includes('REPTWO'), 'فقط کدِ خودش');
const c = truth.totals.byCurrency[0];
const faNum = (n) => Number(n).toLocaleString('fa-AF');
ok((await p1.textContent('#rep-totals')).includes(faNum(c.commission)), 'کمیسیونِ روی صفحه همان عددِ خودِ سرور', `${c.commission}`);

const p2 = await open(cust.token);
ok(!(await p2.isVisible('#tab-btn-rep')), '⛔ مشتریِ معمولی زبانهٔ «نمایندگی» را نمی‌بیند');
ok(errors.length === 0, 'بی خطای جاوااسکریپت', errors.join(' | '));

await browser.close();
server.close();
await closeDb();
console.log(bad ? `❌ ${bad} ایراد` : '✅ نمایندگی در پورتال: فقط فروش‌های خودش');
process.exit(bad ? 1 : 0);
