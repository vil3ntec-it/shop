/**
 * ویترین با کرومیومِ واقعی: «جایگزینی در انتظارِ پاسخِ شما» در صفحهٔ پیگیری (S93؛ `pick-substitutes.trackView`، `store/order.js`).
 *
 *     npm run test:web-store      (پس از web-store-weigh.mjs)
 *
 * pick-substitutes.test.js «S93» همین را روی API قفل می‌کند؛ این‌جا خودِ صفحه، در عرضِ گوشی (۳۶۰):
 *   • دو پیشنهادِ باز ⇒ نشانِ وضعیت «جایگزینی در انتظارِ پاسخِ شما» (warn، نه «جمع‌آوری»)، سرِ کارت شمار و نزدیک‌ترین
 *     مهلت و «بی جواب…»؛ axe و بی بیرون‌زدگی؛ پیگیریِ ۲۰ ثانیه‌ای نشان را برنمی‌گرداند.
 *   • «قبول» ِ یکی ⇒ پس از پاسخِ سرور شمار یک؛ ردِ دیگری ⇒ نشان همان «در حال جمع‌آوری» و سرِ کارت پنهان.
 *   • انگلیسی با یک پیشنهاد ⇒ «Substitute awaiting your reply» و شکلِ مفرد.
 *   • مهلتِ گذشته یا پرچمِ خاموش ⇒ نشانِ «در حال جمع‌آوری» و هیچ سرِ کارتِ «در انتظار»؛ بی نقضِ CSP و بی خطای جاوااسکریپت.
 */
import { createRequire } from 'node:module';
import fs from 'node:fs';
import path from 'node:path';
import { missingTool, PLAYWRIGHT_INSTALL, animationsSettled } from './browser-guard.mjs';
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
const email = 'owner@subwait.local', password = 'Passw0rd!test';
const s1 = await call('POST', '/api/auth/register/start', { name: 'صاحب', email, password });
const v1 = await call('POST', '/api/auth/register/verify', { email, code: s1.body.devCode });
const d1 = await call('POST', '/api/auth/register/complete', { ticket: v1.body.ticket, name: 'صاحب', password,
  device: { deviceId: 'dev-owner', name: 'تست', platform: 'test' }, app: 'shop', terms: { accepted: true } });
const T = d1.body.accessToken;
const shopId = (await call('POST', '/api/shop', { name: 'دکانِ جایگزین' }, T)).body.shop.id;
const A = (await call('POST', '/api/admin/login', { username: 'admin', password: 'Admin!12345' })).body.token;
const flag = (key, value) => call('PUT', '/api/admin/pump/live-config', { scope: shopId, key, value }, A);
for (const key of ['shop.orders', 'shop.storefront', 'shop.orders.picking']) await flag(key, true);
const slug = 'subwait-store';
const put = await call('PUT', '/api/shop/storefront', { slug, enabled: true, title: 'ویترینِ جایگزین', contactPhone: '0700000001',
  acceptPickup: true, minOrderMinor: 0 }, T);
ok(put.status === 200, 'ویترین روشن', JSON.stringify(put.body).slice(0, 120));
let seq = 0;
/** برای هر سفارش کالاهای تازه (کالای «پیدا نشد» در ویترین نگه داشته می‌شود، PCK) — هر جفت هم‌دسته: اصل و جایگزینِ ارزان‌تر */
async function products(tag, kinds) {
  const rows = kinds.flatMap((k) => [
    [`${k}-${tag}`, { name: k === 'oil' ? 'روغن' : 'برنج', unit: k === 'oil' ? 'بوتل' : 'کیلو', salePrice: k === 'oil' ? 300 : 120, category: 'خوراکی', subcategory: k, active: true }],
    [`${k}2-${tag}`, { name: k === 'oil' ? 'روغنِ دوم' : 'برنجِ دوم', unit: k === 'oil' ? 'بوتل' : 'کیلو', salePrice: k === 'oil' ? 280 : 110, category: 'خوراکی', subcategory: k, active: true }],
  ]);
  const r = await call('POST', '/api/sync/v1/push', { device_id: 'dev-owner', schema_version: 2,
    ops: rows.map(([id, fields]) => ({ op_id: `op-${id}-${Date.now()}-${++seq}`, ts: Date.now(), table: 'products', row_id: id, type: 'insert', fields })) },
  T, { 'X-App': 'shop', 'X-Device': 'dev-owner' });
  ok(r.status === 200, `${tag}: کالاها`, JSON.stringify(r.body).slice(0, 120));
  for (const [id] of rows) await call('PUT', `/api/shop/storefront/products/${id}`, { published: true }, T);
}

const phone = '0791112277';
const vs = await call('POST', `/api/store/${slug}/verify/start`, { phone });
const vc = await call('POST', `/api/store/${slug}/verify/check`, { phone, code: vs.body.devCode });
ok(vc.status === 200 && vc.body.ticket, 'شماره تأیید شد');

/** سفارشِ ویترین (روغن + برنج)، قبول، جمع‌آوری؛ قلم‌های `missing` «پیدا نشد» و برای هر کدام جایگزینِ هم‌دسته پیشنهاد می‌شود */
async function waitingOrder(label, missing = ['oil', 'rice']) {
  const tag = `o${++seq}`;
  await products(tag, ['oil', 'rice']);
  const ck = await call('POST', `/api/store/${slug}/checkout`, { fulfillment: 'pickup', customer: { name: 'مشتری', phone },
    lines: [{ productId: `oil-${tag}`, qtyMilli: 1000 }, { productId: `rice-${tag}`, qtyMilli: 1000 }] },
  null, { 'Idempotency-Key': `web-subwait-${++seq}-${Date.now()}`, 'X-Store-Ticket': vc.body.ticket });
  ok(ck.status === 201, `${label}: سفارش ثبت شد`, JSON.stringify(ck.body).slice(0, 120));
  const id = (await one('SELECT id FROM online_orders WHERE shop_id=$1 ORDER BY order_no DESC LIMIT 1', [shopId])).id;
  await call('POST', `/api/shop/orders/${id}/transition`, { to: 'accepted' }, T);
  await call('POST', `/api/shop/orders/${id}/pick/start`, {}, T);
  const sheet = (await call('GET', `/api/shop/orders/${id}/pick`, null, T)).body.pick;
  const subs = [];
  for (const k of missing) {
    const lineNo = sheet.lines.find((l) => l.productId === `${k}-${tag}`).lineNo;
    await call('POST', `/api/shop/orders/${id}/pick/events`, { events: [{ eventId: `ev-subwait-${++seq}-key`, lineNo, type: 'not_found' }] }, T);
    const r = await call('POST', `/api/shop/orders/${id}/pick/substitutions`, { eventId: `sub-subwait-${++seq}-key`, lineNo, productId: `${k}2-${tag}`,
      waitMinutes: k === 'oil' ? 10 : 5 }, T);
    ok(r.status === 201 && r.body.substitution.status === 'pending', `${label}: پیشنهادِ «${k}2»`, JSON.stringify(r.body).slice(0, 160));
    subs.push(r.body.substitution);
  }
  return { id, token: ck.body.order.token, subs };
}

const browser = await chromium.launch({ executablePath: '/opt/pw-browsers/chromium/chrome-linux/chrome' }).catch(() => chromium.launch());
const errors = [];
const violations = [];
const ctx = await browser.newContext({ viewport: { width: 360, height: 760 }, locale: 'fa-AF' });
await ctx.addInitScript((t) => {
  if (!/^http/.test(location.protocol) || sessionStorage.getItem('seeded')) return;   //  about:blank ِ میانِ دو بار
  sessionStorage.setItem('seeded', '1');
  localStorage.setItem('store-ticket', JSON.stringify(t));
}, { phone: vc.body.phone, ticket: vc.body.ticket, expiresAt: vc.body.expiresAt });
const page = await ctx.newPage();
page.on('pageerror', (e) => errors.push(String(e)));
page.on('console', (m) => { if (/Content Security Policy/i.test(m.text())) violations.push(m.text()); });

//  هر بار صفحهٔ تازه (نه فقط تغییرِ #)؛ «پیدا بودن» فقط پس از رسیدنِ پاسخِ تازهٔ همین سفارش (عنوانِ همین شماره)
const open = async (o, q = '') => {
  await page.goto('about:blank');
  await page.goto(`${base}/store/${slug}${q}#/o/${encodeURIComponent(o.token)}`);
  await page.waitForFunction(() => document.querySelectorAll('#o-lines li').length > 0, null, { timeout: 15000 });
};
const text = (sel) => page.$eval(sel, (e) => e.textContent).catch(() => '');
const shown = (sel) => page.$eval(sel, (e) => e.checkVisibility()).catch(() => false);
const pillIs = (want) => page.waitForFunction((w) => (document.getElementById('o-status') || {}).textContent === w, want, { timeout: 15000 })
  .then(() => true, () => false);
const subRow = (id) => one('SELECT status, decided_by FROM order_pick_substitutions WHERE id=$1', [id]);
const WAIT = 'جایگزینی در انتظارِ پاسخِ شما';
const PICKING = 'در حالِ جمع‌آوری';

//  ── دو پیشنهادِ باز (فارسی) ──
const a = await waitingOrder('انتظار');
await open(a);
ok(await pillIs(WAIT), 'نشانِ وضعیت: «جایگزینی در انتظارِ پاسخِ شما»', await text('#o-status'));
ok((await page.$eval('#o-status', (e) => e.className)) === 'pill warn', 'نشان «warn»، نه «prep» ِ جمع‌آوری', await page.$eval('#o-status', (e) => e.className));
await page.waitForSelector('#o-sub-list button.btn-accent', { state: 'visible', timeout: 15000 });
const clock = (ms) => new Intl.DateTimeFormat('fa-AF', { hour: '2-digit', minute: '2-digit', hour12: false }).format(new Date(ms));
const head = await text('#o-sub-wait');
ok(await shown('#o-sub-wait') && /برای ۲ کالا جایگزین پیشنهاد شده است/.test(head), 'سرِ کارت: شمارِ پیشنهادهای باز', head);
ok(head.includes('جواب دهید') && /اگر تا آن وقت جواب ندهید، همان «اگر نبود»/.test(head), 'سرِ کارت: مهلت و «بی جواب ⇒ اگر نبود ِ خودتان»', head);
ok((await page.$$('#o-sub-list button.btn-accent')).length === 2, 'دو «قبول»');
ok(await page.$eval('#o-sub-wait', (e) => e.getAttribute('role')) === 'status', 'سرِ کارت role=status');
if (axeSrc) {
  await animationsSettled(page, '#o-subs');
  await page.evaluate(axeSrc);
  const v = await page.evaluate(async () => (await window.axe.run(document, { runOnly: { type: 'tag', values: ['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa'] } }))
    .violations.map((x) => `${x.id}: ${x.nodes.slice(0, 3).map((nd) => nd.target.join(' ')).join(' ; ')}`));
  ok(v.length === 0, 'دسترس‌پذیری (axe-core، WCAG 2.1 A/AA): پیگیری با «در انتظارِ پاسخ»', v.join(' | '));
} else ok(false, 'axe-core پیدا نشد (AXE_PATH)');
const wide = await page.evaluate(() => document.documentElement.scrollWidth - document.documentElement.clientWidth);
ok(wide <= 0, 'بی بیرون‌زدگی در ۳۶۰', String(wide));
//  نزدیک‌ترین مهلت (برنج، ۵ دقیقه) در سرِ کارت
const near = Math.min(...a.subs.map((s) => s.deadlineAt));
const far = Math.max(...a.subs.map((s) => s.deadlineAt));
ok(head.includes(clock(near)) && !head.includes(clock(far)), 'نزدیک‌ترین مهلت (برنج، ۵ دقیقه)، نه دورتر', `${head} / ${clock(near)}`);

//  بارِ دوبارهٔ سفارش (پس از «قبول»، همان `renderOrder` ِ پیگیریِ ۲۰ ثانیه‌ای) نشان را لحظه‌ای هم به «جمع‌آوری» برنمی‌گرداند
await page.evaluate(() => {
  window.__pills = [];
  new MutationObserver(() => window.__pills.push(document.getElementById('o-status').textContent)).observe(document.getElementById('o-status'), { childList: true, characterData: true, subtree: true });
});
//  «قبول» ِ نخستین ⇒ پس از پاسخِ سرور «برای ۱ کالا…»
const first = await page.$eval('#o-sub-list', (ul) => ul.textContent);
await page.click('#o-sub-list button.btn-accent');
await page.waitForFunction(() => /برای ۱ کالا/.test((document.getElementById('o-sub-wait') || {}).textContent || ''), null, { timeout: 10000 }).catch(() => {});
ok(/برای ۱ کالا/.test(await text('#o-sub-wait')), 'پس از «قبول»: شمار از سرور یک شد', await text('#o-sub-wait'));
ok(await pillIs(WAIT), 'هنوز یکی باز ⇒ همان نشان');
const pills = await page.evaluate(() => window.__pills);
ok(pills.length > 0 && !pills.some((x) => x.startsWith(PICKING)), 'بارِ دوباره: نشان هیچ لحظه «جمع‌آوری» نشد', JSON.stringify(pills));
const decided = (await Promise.all(a.subs.map((s) => subRow(s.id)))).filter((r) => r.status === 'accepted' && r.decided_by === 'customer');
ok(decided.length === 1, 'سرور: یکی پذیرفته (مشتری)', first.slice(0, 80));
//  ردِ دومی ⇒ نشانِ عادی و سرِ کارت پنهان
await page.click('#o-sub-list button.btn-ghost');
ok(await pillIs(PICKING), 'هیچ پیشنهادِ باز ⇒ نشان «در حال جمع‌آوری»', await text('#o-status'));
await page.waitForFunction(() => { const n = document.getElementById('o-sub-wait'); return n && n.hidden; }, null, { timeout: 10000 }).catch(() => {});
ok(!(await shown('#o-sub-wait')), 'سرِ کارتِ «در انتظار» پنهان');
ok((await page.$eval('#o-status', (e) => e.className)) === 'pill prep', 'نشانِ «prep» ِ جمع‌آوری');

//  ── یک پیشنهاد (انگلیسی) ⇒ شکلِ مفرد ──
const b = await waitingOrder('انگلیسی', ['oil']);
await open(b, '?lang=en');
ok(await pillIs('Substitute awaiting your reply'), 'انگلیسی: «Substitute awaiting your reply»', await text('#o-status'));
await page.waitForSelector('#o-sub-wait', { state: 'visible', timeout: 15000 }).catch(() => {});
ok(/A substitute is suggested for 1 item; please answer by/.test(await text('#o-sub-wait')), 'انگلیسی: شکلِ مفرد', await text('#o-sub-wait'));
ok((await text('#o-subs h2')) === 'Substitute items', 'عنوانِ کارت از واژه‌نامهٔ subs.en', await text('#o-subs h2'));

//  ── مهلتِ گذشته ⇒ دیگر «در انتظار» نیست ──
const c = await waitingOrder('مهلت', ['oil']);
await query('UPDATE order_pick_substitutions SET deadline_at=$2 WHERE id=$1', [c.subs[0].id, Date.now() - 1000]);
await open(c, '?lang=fa');
await page.waitForFunction(() => !document.getElementById('o-subs').hidden, null, { timeout: 15000 }).catch(() => {});
ok(await pillIs(PICKING), 'مهلت گذشت ⇒ نشان «در حال جمع‌آوری»', await text('#o-status'));
ok(!(await shown('#o-sub-wait')), 'مهلت گذشت ⇒ بی سرِ کارتِ «در انتظار»');

//  ── پرچمِ خاموش ⇒ همان صفحهٔ پیشین (بی کارت، بی نشانِ تازه) ──
const d = await waitingOrder('خاموش', ['oil']);
await flag('shop.orders.picking', false);
await open(d);
await page.waitForTimeout(800);
ok(await pillIs(PICKING), 'پرچمِ خاموش ⇒ نشان «در حال جمع‌آوری»', await text('#o-status'));
ok(!(await shown('#o-subs')), 'پرچمِ خاموش ⇒ کارتِ جایگزین پنهان');
ok((await subRow(d.subs[0].id)).status === 'pending', 'پرچمِ خاموش: هیچ نوشتن');

ok(violations.length === 0, 'هیچ نقضِ CSP', violations.join(' | '));
ok(errors.length === 0, 'بی خطای جاوااسکریپت', errors.join(' | '));

await ctx.close();
await browser.close();
server.close();
await closeDb();
console.log(bad ? `❌ ${bad} ایراد` : '✅ ویترین: «جایگزینی در انتظارِ پاسخِ شما» در صفحهٔ پیگیری');
process.exit(bad ? 1 : 0);
