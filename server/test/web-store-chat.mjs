/**
 * ویترین با کرومیومِ واقعی: گفت‌وگوی مشتری با دکان — یک رشته برای هر سفارش (`store/chat.js`، سرور:
 * `lib/shop-chat-life.js`).
 *
 *     npm run test:web-store      (پس از web-store-method.mjs)
 *
 * shop-chat-life.test.js همین را روی API قفل می‌کند؛ این‌جا خودِ صفحه، در عرضِ گوشی (۳۶۰):
 *   • پیگیریِ سفارش: کارتِ «گفت‌وگو با دکان» ⇒ برگه با پیام و عکسِ بستهٔ دکان (عکس با بلیت، data:)؛ پاسخِ مشتری به سرور؛
 *   • دکان سفارش را تحویل می‌دهد ⇒ همان برگهٔ باز (long-poll) «بسته شد»: نوشتن پنهان، «مشکل دارم» پیدا ⇒ زدن ⇒ همان
 *     رشته باز و پیامِ مشتری به سرور؛
 *   • سفارشِ دیگرِ تمام‌شده پس از ۷ روز: «مهلت گذشت»، بی «مشکل دارم»، بی نوشتن؛ رشته‌های پیشین جمع‌شده بالای رشته؛
 *   • CHAT-04: «رفت / رسید / خوانده شد» ِ پیامِ خودِ مشتری فقط با رسیدِ واقعیِ دکان، زنده (long-poll) و پس از بازکردنِ
 *     دوباره، هرگز عقب؛ برگهٔ پنهان «خواندم» نمی‌دهد؛
 *   • CHAT-08: «حذف برای همه» ِ مشتری با پرسشِ تأیید (دوبار زدن یک درخواست) به برگهٔ دیگر و long-poll ِ دکان می‌رسد؛ مهلتِ
 *     سرور و رشتهٔ بسته (#907) آن را برمی‌دارند؛ «حذف برای من» فقط نمای همین مشتری — هر دو برگه، پیش‌نمایش و شمارِ نخواندهٔ
 *     صندوق، پس از بازکردنِ دوباره هم؛ بی‌اینترنت پیامِ روشن؛
 *   • «پرسش از دکان» ِ سرِ ویترین ⇒ گفت‌وگوی عمومی؛ پس از ۷ روز بی‌کاری دورِ تازه و پیام‌های پیشین جمع‌شده؛
 *   • پرچمِ `shop.chat` خاموش ⇒ نه دکمه، نه اسکریپت؛ بی نقضِ CSP و بی خطای جاوااسکریپت.
 */
import { createRequire } from 'node:module';
import { missingTool, PLAYWRIGHT_INSTALL } from './browser-guard.mjs';
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
const { query, one, newId, now, closeDb } = require('../src/db');
const migrate = require('../src/migrate');
const pw = require('../src/lib/password');
const F = require('./image-fixtures');

const DAY = 24 * 3600 * 1000;
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
const email = 'owner@chat.local', password = 'Passw0rd!test';
const s1 = await call('POST', '/api/auth/register/start', { name: 'صاحب', email, password });
const v1 = await call('POST', '/api/auth/register/verify', { email, code: s1.body.devCode });
const d1 = await call('POST', '/api/auth/register/complete', { ticket: v1.body.ticket, name: 'صاحب', password,
  device: { deviceId: 'dev-owner', name: 'تست', platform: 'test' }, app: 'shop', terms: { accepted: true } });
const T = d1.body.accessToken;
const shopId = (await call('POST', '/api/shop', { name: 'دکانِ گفت‌وگو' }, T)).body.shop.id;
const A = (await call('POST', '/api/admin/login', { username: 'admin', password: 'Admin!12345' })).body.token;
const flag = (key, value) => call('PUT', '/api/admin/pump/live-config', { scope: shopId, key, value }, A);
for (const key of ['shop.orders', 'shop.storefront', 'shop.chat']) await flag(key, true);
const slug = 'chat-store';
const put = await call('PUT', '/api/shop/storefront', { slug, enabled: true, title: 'ویترینِ گفت‌وگو', contactPhone: '0700000001',
  acceptPickup: true, deliveryFeeMinor: 0, minOrderMinor: 0 }, T);
ok(put.status === 200, 'ویترین ساخته و روشن شد', JSON.stringify(put.body).slice(0, 120));
const pushed = await call('POST', '/api/sync/v1/push', { device_id: 'dev-owner', schema_version: 2, ops: [{
  op_id: `op-rice-${Date.now()}`, ts: Date.now(), table: 'products', row_id: 'rice', type: 'insert',
  fields: { name: 'برنج', unit: 'دانه', salePrice: 300, category: 'خوراکی', active: true },
}] }, T, { 'X-App': 'shop', 'X-Device': 'dev-owner' });
ok(pushed.status === 200, 'کالا از راهِ همگام‌سازیِ واقعی رسید');
await call('PUT', '/api/shop/storefront/products/rice', { published: true }, T);

const phone = '0791112244';
const vs = await call('POST', `/api/store/${slug}/verify/start`, { phone });
const vc = await call('POST', `/api/store/${slug}/verify/check`, { phone, code: vs.body.devCode });
ok(vc.status === 200 && vc.body.ticket, 'شماره تأیید شد');
let seqKey = 0;
const checkout = async () => {
  const ck = await call('POST', `/api/store/${slug}/checkout`, {
    fulfillment: 'pickup', customer: { name: 'مشتری', phone }, lines: [{ productId: 'rice', qtyMilli: 1000 }],
  }, null, { 'Idempotency-Key': `web-chat-${Date.now()}-${++seqKey}`, 'X-Store-Ticket': vc.body.ticket });
  ok(ck.status === 201, 'سفارشِ «دریافت از دکان» ثبت شد', JSON.stringify(ck.body).slice(0, 120));
  const id = (await one('SELECT id FROM online_orders WHERE shop_id=$1 AND order_no=$2', [shopId, ck.body.order.orderNo])).id;
  return { id, token: ck.body.order.token, orderNo: ck.body.order.orderNo };
};
const move = async (orderId, steps) => {
  for (const to of steps) {
    const r = await call('POST', `/api/shop/orders/${orderId}/transition`, { to }, T);
    if (r.status !== 200) ok(false, `گذارِ ${to}`, JSON.stringify(r.body).slice(0, 160));
  }
};
const age = async (orderId, days) => {
  await query('UPDATE online_order_events SET created_at = created_at - $2 WHERE order_id=$1', [orderId, days * DAY]);
  await query('UPDATE online_orders SET updated_at = updated_at - $2 WHERE id=$1', [orderId, days * DAY]);
};
const lastFrom = async (convId, sender) => (await one(
  'SELECT body, kind FROM shop_chat_messages WHERE conversation_id=$1 AND sender=$2 ORDER BY seq DESC LIMIT 1', [convId, sender]));

//  ── سفارشِ ۱: دکان بسته را آماده می‌کند و عکس و پیام می‌فرستد (همان درهای برنامهٔ دکان) ──
const o1 = await checkout();
await move(o1.id, ['accepted', 'picking', 'packed', 'ready_for_pickup']);
const conv1 = (await call('POST', '/api/shop/chat/conversations', { orderId: o1.id }, T)).body.conversation;
ok(conv1 && conv1.life.kind === 'order' && conv1.life.state === 'open', 'دکان رشتهٔ همان سفارش را باز کرد');
const pic = await fetch(`${base}/api/shop/chat/conversations/${conv1.id}/media`, {
  method: 'POST', headers: { Authorization: `Bearer ${T}`, 'Content-Type': 'image/jpeg' }, body: F.jpeg(320, 240, { seed: 41 }),
}).then((r) => r.json());
const sentPic = await call('POST', `/api/shop/chat/conversations/${conv1.id}/messages`,
  { clientId: `client-pic-${Date.now()}`, kind: 'image', mediaId: pic.mediaId, body: 'بستهٔ شما آماده است', meta: { width: 320, height: 240 } }, T);
ok(sentPic.status === 201, 'عکسِ بسته از درِ امنِ بارگذاری رفت', JSON.stringify(sentPic.body).slice(0, 120));

const browser = await chromium.launch({ executablePath: '/opt/pw-browsers/chromium/chrome-linux/chrome' }).catch(() => chromium.launch());
const errors = [];
const violations = [];
const ctx = await browser.newContext({ viewport: { width: 360, height: 760 }, locale: 'fa-AF' });
await ctx.addInitScript((t) => {
  try {
    if (sessionStorage.getItem('seeded')) return;
    sessionStorage.setItem('seeded', '1');
    localStorage.setItem('store-ticket', JSON.stringify(t));
  } catch { /* about:blank */ }
}, { phone: vc.body.phone, ticket: vc.body.ticket, expiresAt: vc.body.expiresAt });
const page = await ctx.newPage();
page.on('pageerror', (e) => errors.push(String(e)));
page.on('console', (m) => { if (/Content Security Policy/i.test(m.text())) violations.push(m.text()); });
const shown = (id) => page.evaluate((x) => { const e = document.getElementById(x); return Boolean(e) && !e.hidden; }, id);
const openOrder = async (o) => {
  await page.goto(`${base}/store/${slug}#/o/${encodeURIComponent(o.token)}`);
  await page.waitForFunction(() => { const b = document.getElementById('o-chat'); return b && !b.hidden && b.textContent.length > 0; }, null, { timeout: 15000 });
};

//  ── پیگیری ⇒ «گفت‌وگو با دکان» ──
await openOrder(o1);
await page.click('#o-chat-open');
await page.waitForFunction(() => document.querySelectorAll('#ct-list .ct-msg').length > 0, null, { timeout: 15000 });
const title1 = await page.textContent('#ct-title');
ok(/سفارش/.test(title1), 'برگهٔ گفت‌وگو با نامِ همان سفارش', title1);
await page.waitForFunction(() => /^data:image\/jpeg;/.test((document.querySelector('#ct-list img.ct-img') || {}).src || ''), null, { timeout: 10000 });
ok(true, 'عکسِ بستهٔ دکان دیده می‌شود (با بلیت، data:)');
ok(/بستهٔ شما آماده است/.test(await page.textContent('#ct-list')), 'متنِ دکان زیرِ عکس');
await page.fill('#ct-text', 'تا یک ساعت دیگر می‌آیم');
await page.click('#ct-send');
await page.waitForFunction(() => /یک ساعت دیگر/.test(document.getElementById('ct-list').textContent), null, { timeout: 10000 });
ok((await lastFrom(conv1.id, 'customer'))?.body === 'تا یک ساعت دیگر می‌آیم', 'پیامِ مشتری روی سرور، در همان رشته');

//  ── CHAT-04: «رفت / رسید / خوانده شد» ِ پیامِ خودِ مشتری فقط از رسیدِ واقعیِ دکان؛ زنده با long-poll ِ همان برگه ──
const sleep = (ms) => new Promise((r) => setTimeout(r, ms));
const until = async (fn, ms = 15000) => {
  for (const end = Date.now() + ms; Date.now() < end; await sleep(200)) if (await fn()) return true;
  return false;
};
const msgOf = async (body) => one('SELECT id, seq FROM shop_chat_messages WHERE conversation_id=$1 AND body=$2 ORDER BY seq DESC LIMIT 1', [conv1.id, body]);
const stOf = (p, id) => p.evaluate((x) => {
  const s = document.querySelector(`#ct-list li[data-id="${x}"] .ct-st`);
  return s ? `${s.dataset.st}|${s.textContent}` : '';
}, id);
const shopReceipt = (b) => call('POST', `/api/shop/chat/conversations/${conv1.id}/receipts`, b, T);
const hour = await msgOf('تا یک ساعت دیگر می‌آیم');
await page.waitForFunction((x) => document.querySelector(`#ct-list li[data-id="${x}"] .ct-st`), hour.id, { timeout: 10000 });
ok(await stOf(page, hour.id) === 'sent|رفت', 'پیامِ تازهٔ مشتری: «رفت» — دکان هنوز رسیدی نداده', await stOf(page, hour.id));
await sleep(1200);
ok(await stOf(page, hour.id) === 'sent|رفت', 'بی رسیدِ دکان هیچ وضعیتی خودش جلو نمی‌رود (جعلی نه)');
await shopReceipt({ deliveredSeq: Number(hour.seq) });
ok(await until(async () => (await stOf(page, hour.id)) === 'delivered|رسید'), 'دکان «رسید» داد ⇒ همان برگهٔ باز «رسید» (long-poll)', await stOf(page, hour.id));
await shopReceipt({ readSeq: Number(hour.seq) });
ok(await until(async () => (await stOf(page, hour.id)) === 'read|خوانده شد'), 'دکان «خواندم» داد ⇒ «خوانده شد»', await stOf(page, hour.id));
const back = await shopReceipt({ deliveredSeq: 1, readSeq: 1 });
await sleep(1200);
ok(back.status === 200 && Number(back.body.conversation.receipts.peerReadSeq) >= Number(hour.seq) && await stOf(page, hour.id) === 'read|خوانده شد',
  'رسیدِ کهنه‌تر (seq ِ ۱) هیچ چیز را عقب نمی‌برد — نه سرور، نه برگه');

//  ── CHAT-02/11: بی‌اینترنت ⇒ متن در صف می‌ماند و با وصل شدن خودش یک بار می‌رود؛ جوابِ گم‌شده + زدنِ دوباره ⇒ همان شناسه ──
const countFrom = async (convId, body) => Number((await one(
  "SELECT COUNT(*)::int AS n FROM shop_chat_messages WHERE conversation_id=$1 AND sender='customer' AND body=$2", [convId, body])).n);
const bubbles = (text, p = page) => p.evaluate((x) => [...document.querySelectorAll('#ct-list .ct-msg')].filter((li) => li.textContent.includes(x)).length, text);
await ctx.setOffline(true);
await page.fill('#ct-text', 'پیامِ بی‌اینترنت');
await page.click('#ct-send');
await page.waitForFunction(() => !document.getElementById('ct-msg').hidden, null, { timeout: 10000 });
ok(await countFrom(conv1.id, 'پیامِ بی‌اینترنت') === 0 && (await page.inputValue('#ct-text')) === 'پیامِ بی‌اینترنت',
  'بی‌اینترنت: متن در کادر و در صف می‌ماند', await page.textContent('#ct-msg'));
await ctx.setOffline(false);
ok(await until(async () => (await countFrom(conv1.id, 'پیامِ بی‌اینترنت')) === 1, 20000), 'وصل شد ⇒ خودش رفت، بی زدنِ دوباره');
await page.waitForFunction(() => /بی‌اینترنت/.test(document.getElementById('ct-list').textContent), null, { timeout: 15000 });
ok((await page.inputValue('#ct-text')) === '' && await bubbles('پیامِ بی‌اینترنت') === 1, 'پس از رسیدن: کادر خالی و یک حباب');
const offMsg = await msgOf('پیامِ بی‌اینترنت');
ok(await stOf(page, offMsg.id) === 'sent|رفت' && await stOf(page, hour.id) === 'read|خوانده شد',
  'پیامِ تازه‌تر از «خواندم» ِ دکان «رفت» می‌ماند؛ هر پیام از seq ِ خودش', `${await stOf(page, offMsg.id)} / ${await stOf(page, hour.id)}`);

let drop = 1;
const postUrl = `**/api/store/${slug}/chat/conversations/${conv1.id}/messages`;
await page.route(postUrl, async (route) => {
  if (route.request().method() !== 'POST' || drop < 1) return route.fallback();
  drop--;
  await route.fetch();            //  سرور ثبت می‌کند…
  return route.abort('failed');   //  …و جواب به صفحه نمی‌رسد
});
await page.fill('#ct-text', 'جوابِ گم‌شده');
await page.click('#ct-send');
await page.waitForFunction(() => !document.getElementById('ct-msg').hidden, null, { timeout: 10000 });
await page.click('#ct-send');     //  کاربر دوباره می‌زند
await page.waitForFunction(() => /جوابِ گم‌شده/.test(document.getElementById('ct-list').textContent), null, { timeout: 15000 });
await new Promise((r) => setTimeout(r, 1500));
ok(await countFrom(conv1.id, 'جوابِ گم‌شده') === 1 && await bubbles('جوابِ گم‌شده') === 1,
  'جوابِ گم‌شده و زدنِ دوباره ⇒ یک پیام روی سرور و یک حباب', `${await countFrom(conv1.id, 'جوابِ گم‌شده')} / ${await bubbles('جوابِ گم‌شده')}`);
await page.unroute(postUrl);

//  ── CHAT-04: برگهٔ پنهانِ مرورگر «رسید» می‌دهد ولی «خواندم» نه؛ دیده شدن ⇒ «خواندم» ──
const vis = (s, p = page) => p.evaluate((v) => {
  Object.defineProperty(document, 'visibilityState', { configurable: true, get: () => v });
  Object.defineProperty(document, 'hidden', { configurable: true, get: () => v === 'hidden' });
  document.dispatchEvent(new Event('visibilitychange'));
}, s);
const marks = async () => one('SELECT customer_delivered_seq AS d, customer_read_seq AS r FROM shop_chat_conversations WHERE id=$1', [conv1.id]);
await vis('hidden');
const late = await call('POST', `/api/shop/chat/conversations/${conv1.id}/messages`, { clientId: `client-late-${Date.now()}`, body: 'تا ساعتِ پنج باز هستیم' }, T);
const lateSeq = Number(late.body.message.seq);
await page.waitForFunction(() => /ساعتِ پنج/.test(document.getElementById('ct-list').textContent), null, { timeout: 30000 });
ok(await until(async () => Number((await marks()).d) >= lateSeq), 'برگهٔ پنهان: پیام رسید و «رسید» به سرور رفت');
ok(Number((await marks()).r) < lateSeq, 'برگهٔ پنهان: «خواندم» نرفت', JSON.stringify(await marks()));
const shopStatus = async (id) => (await call('GET', `/api/shop/chat/conversations/${conv1.id}/messages?after=0`, null, T)).body.messages.find((m) => m.id === id)?.status;
ok(await shopStatus(late.body.message.id) === 'delivered', 'برگهٔ پنهان: دکان پیامش را «رسید» می‌بیند، نه «خوانده شد»');
await vis('visible');
ok(await until(async () => Number((await marks()).r) >= lateSeq), 'برگه دیده شد ⇒ «خواندم» رفت', JSON.stringify(await marks()));
ok(await shopStatus(late.body.message.id) === 'read', 'برگه دیده شد ⇒ دکان «خوانده شد» می‌بیند');

//  ── CHAT-08: «حذف برای همه» ِ دکان همان دم به برگهٔ بازِ مشتری می‌رسد (long-poll)، فقط سنگِ قبر ──
const gone = await call('DELETE', `/api/shop/chat/conversations/${conv1.id}/messages/${late.body.message.id}?for=all`, null, T);
await page.waitForFunction(() => !/ساعتِ پنج/.test(document.getElementById('ct-list').textContent), null, { timeout: 30000 });
ok(gone.status === 200 && await bubbles('این پیام پاک شد') === 1, '«حذف برای همه» ِ دکان ⇒ برگهٔ بازِ مشتری فقط «این پیام پاک شد»');

//  ── CHAT-03، CHAT-01 و CHAT-08: صفحه از نو ⇒ پیام‌ها از سرور، هر کدام یک بار، و پاک‌شده همچنان پاک ──
await page.reload();
await page.waitForFunction(() => { const b = document.getElementById('o-chat-open'); return b && b.offsetParent !== null; }, null, { timeout: 15000 });
await page.click('#o-chat-open');
await page.waitForFunction(() => /جوابِ گم‌شده/.test(document.getElementById('ct-list').textContent), null, { timeout: 15000 });
ok(await bubbles('پیامِ بی‌اینترنت') === 1 && await bubbles('جوابِ گم‌شده') === 1 && await bubbles('یک ساعت دیگر') === 1,
  'پس از بازکردنِ دوباره: پیام‌های مشتری از سرور، هر کدام یک بار');
ok(await bubbles('ساعتِ پنج') === 0 && await bubbles('این پیام پاک شد') === 1, 'پس از بازکردنِ دوباره: پیامِ پاک‌شده فقط سنگِ قبر');
await page.waitForFunction((x) => document.querySelector(`#ct-list li[data-id="${x}"] .ct-st`), hour.id, { timeout: 10000 });
ok(await stOf(page, hour.id) === 'read|خوانده شد' && await stOf(page, offMsg.id) === 'sent|رفت',
  'پس از بازکردنِ دوباره: همان وضعیت‌ها از سرور', `${await stOf(page, hour.id)} / ${await stOf(page, offMsg.id)}`);

//  ── CHAT-08 (مشتری): «حذف برای همه» با پرسشِ تأیید، دوبار زدن یک درخواست؛ به برگهٔ دیگر و دکان همان دم می‌رسد ──
const li = (id, rest = '') => `#ct-list li[data-id="${id}"] ${rest}`.trim();
const ticketH = { 'X-Store-Ticket': vc.body.ticket };
const page2 = await ctx.newPage();
page2.on('pageerror', (e) => errors.push(`برگهٔ ۲: ${e}`));
page2.on('console', (m) => { if (/Content Security Policy/i.test(m.text())) violations.push(m.text()); });
await page2.goto(`${base}/store/${slug}#/o/${encodeURIComponent(o1.token)}`);
await page2.waitForFunction(() => { const b = document.getElementById('o-chat-open'); return b && b.offsetParent !== null; }, null, { timeout: 15000 });
await page2.click('#o-chat-open');
await page2.waitForFunction(() => /جوابِ گم‌شده/.test(document.getElementById('ct-list').textContent), null, { timeout: 15000 });
await page.fill('#ct-text', 'این را پاک می‌کنم');
await page.click('#ct-send');
await page2.waitForFunction(() => /این را پاک می‌کنم/.test(document.getElementById('ct-list').textContent), null, { timeout: 30000 });
const doomed = await msgOf('این را پاک می‌کنم');
const shopCur = (await call('GET', `/api/shop/chat/conversations/${conv1.id}/messages?after=0`, null, T)).body.cursor;
const shopWait = call('GET', `/api/shop/chat/conversations/${conv1.id}/messages?after=${shopCur}&wait=20000`, null, T);
const dels = [];
const onDel = (r) => { if (r.method() === 'DELETE') dels.push(r.url()); };
page.on('request', onDel);
await page.click(li(doomed.id, '.ct-more'));
await page.waitForSelector(li(doomed.id, '[data-del="all"]'));
ok(await page.getAttribute(li(doomed.id, '.ct-more'), 'aria-expanded') === 'true' && Boolean(await page.getAttribute(li(doomed.id, '.ct-more'), 'aria-label')) &&
  await page.evaluate(() => Boolean(document.activeElement) && document.activeElement.dataset.del === 'me'),
'دکمهٔ «کارهای این پیام» با نام و aria-expanded؛ تمرکز روی نخستین کار');
ok(await page.evaluate(() => { const b = document.getElementById('ct-body'); return document.documentElement.scrollWidth <= innerWidth && b.scrollWidth <= b.clientWidth + 1; }),
  'عرضِ گوشی (۳۶۰): نوارِ کارها بی پیمایشِ افقی');
await page.keyboard.press('Tab');
ok(await page.evaluate(() => document.activeElement.dataset.del === 'all'), 'صفحه‌کلید: Tab ⇒ «حذف برای همه»');
await page.keyboard.press('Enter');
await page.waitForSelector(li(doomed.id, '[data-yes="all"]'));
ok(/برای همه/.test(await page.textContent(li(doomed.id, '.ct-acts'))) && await page.evaluate(() => document.activeElement.hasAttribute('data-no')) && dels.length === 0,
  'پیش از پاک کردن پرسشِ تأیید (تمرکز روی «نه») و هنوز هیچ درخواستی');
await page.dblclick(li(doomed.id, '[data-yes="all"]'));
await page.waitForFunction((x) => { const e = document.querySelector(`#ct-list li[data-id="${x}"]`); return e && /این پیام پاک شد/.test(e.textContent); }, doomed.id, { timeout: 10000 });
await sleep(600);
page.off('request', onDel);
ok(dels.length === 1, 'دوبار زدنِ «بله» ⇒ یک درخواستِ حذف', String(dels.length));
ok(await page.evaluate(() => Boolean(document.activeElement) && document.activeElement.classList.contains('ct-more')) && /برای همه پاک شد/.test(await page.textContent('#ct-msg')),
  'پس از حذف: تمرکز روی دکمهٔ همان سنگِ قبر و پیامِ «پاک شد»');
await page2.waitForFunction(() => !/این را پاک می‌کنم/.test(document.getElementById('ct-list').textContent), null, { timeout: 30000 });
ok(await bubbles('این پیام پاک شد', page2) === 2, 'برگهٔ دیگرِ همان مشتری همان دم فقط سنگِ قبر (long-poll)');
const sw = await shopWait;
const tombShop = (sw.body.messages || []).find((m) => m.id === doomed.id);
ok(sw.status === 200 && tombShop && tombShop.deleted === true && tombShop.body === undefined, 'long-poll ِ دکان همان دم سنگِ قبر را گرفت (بی متن)', JSON.stringify(tombShop));
const dbRow = await one('SELECT body, deleted_at FROM shop_chat_messages WHERE id=$1', [doomed.id]);
ok(dbRow.body === '' && Boolean(dbRow.deleted_at), 'سرور: متن رفت و فقط سنگِ قبر ماند');
const twice = await call('DELETE', `/api/store/${slug}/chat/conversations/${conv1.id}/messages/${doomed.id}?for=all`, null, null, ticketH);
ok(twice.status === 200 && twice.body.message.deleted === true, '«حذف برای همه» ِ دوباره (برگهٔ دیگر) ⇒ همان سنگِ قبر، بی خطا');
await page.click(li(doomed.id, '.ct-more'));
await page.waitForSelector(li(doomed.id, '[data-del="me"]'));
ok(await page.$(li(doomed.id, '[data-del="all"]')) === null, 'سنگِ قبر فقط «حذف برای من» دارد');
await page.click(li(doomed.id, '.ct-more'));
ok(await page.$(li(doomed.id, '.ct-acts')) === null && await page.getAttribute(li(doomed.id, '.ct-more'), 'aria-expanded') === 'false', 'دوباره زدنِ دکمه نوار را می‌بندد');

//  مهلت همین حالا روی سرور گذشت (صفحه هنوز نمی‌داند) ⇒ پیامِ روشن، پیام سرِ جایش و «حذف برای همه» می‌رود
const lostMsg = await msgOf('جوابِ گم‌شده');
await query('UPDATE shop_chat_messages SET created_at = created_at - $2 WHERE id=$1', [lostMsg.id, 2 * DAY]);
await page.click(li(lostMsg.id, '.ct-more'));
await page.click(li(lostMsg.id, '[data-del="all"]'));
await page.click(li(lostMsg.id, '[data-yes="all"]'));
await page.waitForFunction((x) => /حذف برای همه» ِ این پیام گذشته/.test((document.querySelector(`#ct-list li[data-id="${x}"] .ct-acts`) || {}).textContent || ''), lostMsg.id, { timeout: 10000 });
ok(await page.$(li(lostMsg.id, '[data-del="all"]')) === null && await bubbles('جوابِ گم‌شده') === 1 &&
  (await one('SELECT deleted_at FROM shop_chat_messages WHERE id=$1', [lostMsg.id])).deleted_at === null,
'مهلتِ سرور گذشته ⇒ «وقتِ حذف برای همه… گذشته است»، پیام سرِ جایش و دکمه‌اش رفت', await page.textContent(li(lostMsg.id, '.ct-acts')));
await page.click(li(lostMsg.id, '.ct-more'));

//  ── CHAT-08 (مشتری): «حذف برای من» فقط نمای همین مشتری — هر دو برگه و صندوق (پیش‌نمایش و نخوانده)، پس از بازکردنِ دوباره هم ──
await vis('hidden');
await vis('hidden', page2);
const ask = await call('POST', `/api/shop/chat/conversations/${conv1.id}/messages`, { clientId: `client-ask-${Date.now()}`, body: 'کی می‌آیید؟' }, T);
const hideMe = (await call('POST', `/api/shop/chat/conversations/${conv1.id}/messages`, { clientId: `client-hide-${Date.now()}`, body: 'شمارهٔ دوم را بفرستید' }, T)).body.message;
for (const p of [page, page2]) await p.waitForFunction((x) => document.querySelector(`#ct-list li[data-id="${x}"]`), hideMe.id, { timeout: 30000 });
const inbox = async () => (await call('GET', `/api/store/${slug}/chat/conversations?limit=50`, null, null, ticketH)).body.conversations.find((c) => c.id === conv1.id);
const before = await inbox();
ok(before.unread === 2 && before.lastMessage.preview === 'شمارهٔ دوم را بفرستید', 'پیش از حذف: صندوقِ مشتری دو نخوانده و پیش‌نمایشِ همان پیام',
  JSON.stringify({ u: before.unread, l: before.lastMessage }));
await page.click(li(hideMe.id, '.ct-more'));
await page.waitForSelector(li(hideMe.id, '[data-del="me"]'));
ok(await page.$(li(hideMe.id, '[data-del="all"]')) === null, 'پیامِ دکان برای مشتری «حذف برای همه» ندارد');
await page.click(li(hideMe.id, '[data-del="me"]'));
ok(/فقط از نمای شما/.test(await page.textContent(li(hideMe.id, '.ct-acts'))), 'پرسشِ «حذف برای من»');
await page.click(li(hideMe.id, '[data-yes="me"]'));
for (const p of [page, page2]) await p.waitForFunction((x) => !document.querySelector(`#ct-list li[data-id="${x}"]`), hideMe.id, { timeout: 30000 });
ok(await page.evaluate(() => Boolean(document.activeElement) && document.activeElement.id === 'ct-text'), '«حذف برای من» ⇒ از هر دو برگهٔ همین مشتری رفت؛ تمرکز به کادرِ نوشتن');
const after = await inbox();
ok(after.unread === 1 && after.lastMessage.preview === 'کی می‌آیید؟', 'صندوقِ مشتری: پیش‌نمایش به پیامِ دیدنیِ پیشین برگشت و پنهان در نخوانده نیست',
  JSON.stringify({ u: after.unread, l: after.lastMessage }));
const shopRow = (await call('GET', '/api/shop/chat/conversations', null, T)).body.conversations.find((c) => c.id === conv1.id);
const shopMsg = (await call('GET', `/api/shop/chat/conversations/${conv1.id}/messages?after=0`, null, T)).body.messages.find((m) => m.id === hideMe.id);
ok(shopRow.lastMessage.preview === 'شمارهٔ دوم را بفرستید' && shopMsg && shopMsg.body === 'شمارهٔ دوم را بفرستید', 'دکان پیام و پیش‌نمایشش را دست‌نخورده می‌بیند');

//  بی‌اینترنت ⇒ «پاک نشد» به زبانِ روشن؛ پیام سرِ جایش و دکمه دوباره زدنی (هیچ حذفِ خودکارِ بعدی)
await ctx.setOffline(true);
const askId = ask.body.message.id;
await page.click(li(askId, '.ct-more'));
await page.click(li(askId, '[data-del="me"]'));
await page.click(li(askId, '[data-yes="me"]'));
await page.waitForFunction((x) => /اینترنت/.test((document.querySelector(`#ct-list li[data-id="${x}"] .ct-acts`) || {}).textContent || ''), askId, { timeout: 10000 });
ok(await bubbles('کی می‌آیید؟') === 1 && !(await page.isDisabled(li(askId, '[data-del="me"]'))), 'بی‌اینترنت: «پاک نشد؛ اینترنت وصل نیست…»، پیام سرِ جایش و دکمه دوباره زدنی',
  await page.textContent(li(askId, '.ct-acts')));
await ctx.setOffline(false);
await page.click(li(askId, '.ct-more'));

//  صفحه از نو (برگه هنوز پنهان ⇒ «خواندم» نمی‌رود و شمارِ نخوانده همان می‌ماند): صندوقی که خودِ صفحه می‌گیرد هم پنهان را ندارد
await query('UPDATE shop_chat_messages SET created_at = created_at - $2 WHERE id=$1', [offMsg.id, 2 * DAY]);
await page.addInitScript(() => {
  try {
    if (sessionStorage.getItem('hide-tab') !== '1') return;
    Object.defineProperty(document, 'visibilityState', { configurable: true, get: () => 'hidden' });
    Object.defineProperty(document, 'hidden', { configurable: true, get: () => true });
  } catch { /* about:blank */ }
});
await page.evaluate(() => sessionStorage.setItem('hide-tab', '1'));
const listed = page.waitForResponse((r) => /\/chat\/conversations\?limit=50/.test(r.url()), { timeout: 20000 });
await page.reload();
await page.waitForFunction(() => { const b = document.getElementById('o-chat-open'); return b && b.offsetParent !== null; }, null, { timeout: 15000 });
await page.click('#o-chat-open');
const seenList = (await (await listed).json()).conversations.find((c) => c.id === conv1.id);
await page.waitForFunction(() => /کی می‌آیید/.test(document.getElementById('ct-list').textContent), null, { timeout: 15000 });
ok(await bubbles('شمارهٔ دوم') === 0 && seenList.unread === 1 && seenList.lastMessage.preview === 'کی می‌آیید؟',
  'پس از بازکردنِ دوباره: پیامِ پنهان نیست و صندوقی که صفحه گرفت پیش‌نمایش و نخوانده‌اش را ندارد', JSON.stringify({ u: seenList.unread, l: seenList.lastMessage }));
await page.click(li(offMsg.id, '.ct-more'));
await page.waitForSelector(li(offMsg.id, '[data-del="me"]'));
ok(await page.$(li(offMsg.id, '[data-del="all"]')) === null, 'پیامِ خودم پس از مهلتِ سرور (deleteUntil): فقط «حذف برای من»');
await page.click(li(offMsg.id, '.ct-more'));
await page.evaluate(() => sessionStorage.removeItem('hide-tab'));
await vis('visible');
await page2.close();

//  ── دکان تحویل می‌دهد ⇒ همان برگهٔ باز «بسته شد» (long-poll) ⇒ «مشکل دارم» ⇒ همان رشته باز ──
//  CHAT-08 + #907: رشتهٔ بسته «حذف برای همه» ندارد — نوارِ بازِ پیامِ خودم همان دم (long-poll) آن را با دلیل برمی‌دارد
await page.click(li(hour.id, '.ct-more'));
await page.waitForSelector(li(hour.id, '[data-del="all"]'));
await move(o1.id, ['picked_up']);
//  بیدار کردنِ long-poll با رسیدِ واقعی (تا آخرین seq؛ «خواندم» ِ دکان پیش‌تر تا پیامِ مشتری جلو رفته، پس ۲ دیگر تغییری نیست)
const seqNow = Number((await one('SELECT seq FROM shop_chat_conversations WHERE id=$1', [conv1.id])).seq);
await call('POST', `/api/shop/chat/conversations/${conv1.id}/receipts`, { readSeq: seqNow }, T);
await page.waitForFunction(() => !document.getElementById('ct-issue').hidden, null, { timeout: 30000 });
ok(await page.$(li(hour.id, '[data-del="all"]')) === null && /بسته شد/.test(await page.textContent(li(hour.id, '.ct-acts'))),
  'رشته بسته شد ⇒ «حذف برای همه» از نوارِ باز رفت، با دلیل', await page.textContent(li(hour.id, '.ct-acts')));
await page.click(li(hour.id, '.ct-more'));
await page.click(li(hour.id, '.ct-more'));
ok(await page.$(li(hour.id, '[data-del="me"]')) !== null && await page.$(li(hour.id, '[data-del="all"]')) === null, 'رشتهٔ بسته: پیامِ تازهٔ خودم هم فقط «حذف برای من»');
const closedAll = await call('DELETE', `/api/store/${slug}/chat/conversations/${conv1.id}/messages/${hour.id}?for=all`, null, null, ticketH);
ok(closedAll.status === 409 && closedAll.body.error.code === 'chat_closed', 'سرور هم: رشتهٔ بسته ⇒ ۴۰۹ chat_closed');
await page.click(li(hour.id, '[data-del="me"]'));
await page.click(li(hour.id, '[data-yes="me"]'));
await page.waitForFunction((x) => !document.querySelector(`#ct-list li[data-id="${x}"]`), hour.id, { timeout: 10000 });
ok(Boolean(await one("SELECT 1 AS x FROM shop_chat_hidden WHERE message_id=$1 AND viewer='customer'", [hour.id])), 'رشتهٔ بسته: «حذف برای من» باز است');
ok(!(await shown('ct-form')), 'تحویل‌شده و پرداخت‌شده ⇒ بسته شد: نوشتن پنهان');
ok(/بسته شد/.test(await page.textContent('#ct-state')) && /مشکل دارم/.test(await page.textContent('#ct-state')), 'یادداشتِ «بسته شد» با مهلتِ «مشکل دارم»', await page.textContent('#ct-state'));
await page.click('#ct-issue');
await page.waitForFunction(() => !document.getElementById('ct-form').hidden, null, { timeout: 10000 });
ok(!(await shown('ct-issue')) && /دوباره باز/.test(await page.textContent('#ct-state')), '«مشکل دارم» ⇒ همان رشته دوباره باز');
await page.fill('#ct-text', 'برنج خیس بود');
await page.click('#ct-send');
await page.waitForFunction(() => /خیس/.test(document.getElementById('ct-list').textContent), null, { timeout: 10000 });
const row1 = await one('SELECT issue_opened_at, order_id FROM shop_chat_conversations WHERE id=$1', [conv1.id]);
ok(row1.issue_opened_at && row1.order_id === o1.id && (await lastFrom(conv1.id, 'customer')).body === 'برنج خیس بود', 'سرور: «مشکل دارم» و پیامِ تازه در همان رشته');

//  ── سفارشِ ۲ پس از ۷ روز: مهلت گذشت؛ رشته‌های پیشین جمع‌شده ──
const o2 = await checkout();
await move(o2.id, ['accepted', 'picking', 'packed', 'ready_for_pickup', 'picked_up']);
await age(o2.id, 8);
await openOrder(o2);
await page.click('#o-chat-open');
await page.waitForFunction(() => /گذشته/.test((document.getElementById('ct-state') || {}).textContent || ''), null, { timeout: 15000 });
ok(!(await shown('ct-issue')) && !(await shown('ct-form')), 'پس از ۷ روز: بی «مشکل دارم» و بی نوشتن');
await page.waitForFunction(() => !document.getElementById('ct-prev').hidden, null, { timeout: 10000 });
const prevOpen = await page.evaluate(() => document.getElementById('ct-prev').open);
ok(!prevOpen && /۱/.test(await page.textContent('#ct-prev-s')), 'رشتهٔ سفارشِ پیشین بالای رشته، جمع‌شده', await page.textContent('#ct-prev-s'));
await page.click('#ct-prev-s');
await page.click('#ct-prev-list summary');
await page.waitForFunction(() => /خیس/.test(document.getElementById('ct-prev-list').textContent), null, { timeout: 10000 });
ok(true, 'باز کردنِ رشتهٔ پیشین پیام‌هایش را نشان می‌دهد');
await page.keyboard.press('Escape');

//  ── «پرسش از دکان» ⇒ گفت‌وگوی عمومی؛ پس از ۷ روز دورِ تازه ──
await page.goto(`${base}/store/${slug}`);
await page.waitForFunction(() => { const b = document.getElementById('store-chat'); return b && !b.hidden; }, null, { timeout: 15000 });
ok(Boolean(await page.getAttribute('#store-chat', 'aria-label')), 'دکمهٔ «پرسش از دکان» با نامِ دسترس‌پذیر');
await page.click('#store-chat');
await page.waitForFunction(() => { const s = document.getElementById('chat-sheet'); return s && !s.hidden && !document.getElementById('ct-form').hidden; }, null, { timeout: 15000 });
ok(/عمومی/.test(await page.textContent('#ct-title')), 'گفت‌وگوی عمومی', await page.textContent('#ct-title'));
const gets = [];
const onGet = (r) => { if (r.method() === 'GET' && /\/chat\/conversations\/[^/?]+\/messages/.test(r.url())) gets.push(r.url()); };
page.on('request', onGet);
await sleep(2500);
page.off('request', onGet);
ok(gets.length <= 2, 'رشتهٔ بی‌پیام: یک long-poll ِ باز، نه پرسشِ پیاپی', String(gets.length));
await page.fill('#ct-text', 'آرد دارید؟');
await page.click('#ct-send');
await page.waitForFunction(() => /آرد/.test(document.getElementById('ct-list').textContent), null, { timeout: 10000 });
const gen = await one("SELECT id FROM shop_chat_conversations WHERE shop_id=$1 AND order_id=''", [shopId]);
await query('UPDATE shop_chat_conversations SET created_at = created_at - $2, last_message_at = last_message_at - $2 WHERE id=$1', [gen.id, 8 * DAY]);
await query('UPDATE shop_chat_messages SET created_at = created_at - $2 WHERE conversation_id=$1', [gen.id, 8 * DAY]);
await page.reload();
await page.waitForFunction(() => { const b = document.getElementById('store-chat'); return b && !b.hidden; }, null, { timeout: 15000 });
await page.click('#store-chat');
await page.waitForFunction(() => { const d = document.getElementById('ct-old'); return d && !d.hidden; }, null, { timeout: 15000 });
ok(!(await page.evaluate(() => document.getElementById('ct-old').open)) && await shown('ct-form'), 'پس از ۷ روز بی‌کاری: دورِ تازه باز و پیام‌های پیشین جمع‌شده');
const genRow = await one('SELECT id, session_at FROM shop_chat_conversations WHERE id=$1', [gen.id]);
ok(genRow.session_at > 0, 'سرور: دورِ تازه در همان رشته (session_at)');
await page.keyboard.press('Escape');

//  ── دسترس‌پذیری (axe-core) ِ برگهٔ گفت‌وگو: کنتراست، نامِ دکمه، برچسبِ فیلد، متنِ عکس ──
if (axeSrc) {
  const ac = await browser.newContext({ viewport: { width: 360, height: 760 }, bypassCSP: true });
  await ac.addInitScript((t) => { try { localStorage.setItem('store-ticket', JSON.stringify(t)); } catch { /* about:blank */ } },
    { phone: vc.body.phone, ticket: vc.body.ticket, expiresAt: vc.body.expiresAt });
  const ap = await ac.newPage();
  await ap.goto(`${base}/store/${slug}#/o/${encodeURIComponent(o1.token)}`);
  await ap.waitForSelector('#o-chat-open', { timeout: 15000 });
  await ap.click('#o-chat-open');
  await ap.waitForFunction(() => document.querySelectorAll('#ct-list .ct-msg').length > 0, null, { timeout: 15000 });
  await ap.waitForTimeout(400);
  //  نوارِ کارها و پرسشِ تأیید هم زیرِ همان سنجه (کنتراستِ دکمه‌ها درونِ حبابِ تیره)
  await ap.evaluate(() => [...document.querySelectorAll('#ct-list .ct-msg.mine .ct-more')].pop().click());
  await ap.waitForSelector('#ct-list .ct-acts [data-del]');
  if (await ap.$('#ct-list .ct-acts [data-del="all"]')) await ap.click('#ct-list .ct-acts [data-del="all"]');
  else await ap.click('#ct-list .ct-acts [data-del="me"]');
  await ap.waitForSelector('#ct-list .ct-acts [data-yes]');
  await ap.addScriptTag({ content: axeSrc });
  const v = await ap.evaluate(async () => (await window.axe.run(document.getElementById('chat-sheet'),
    { runOnly: ['color-contrast', 'button-name', 'label', 'image-alt', 'link-name'] })).violations
    .map((x) => `${x.id}: ${x.nodes.slice(0, 2).map((n) => n.target.join(' ')).join(' ; ')}`));
  ok(v.length === 0, 'دسترس‌پذیری (axe-core): برگهٔ گفت‌وگو', v.join(' | '));
  await ac.close();
} else if (process.env.SKIP_BROWSER_TESTS === '1') {
  console.log('⏭  axe-core نیست و SKIP_BROWSER_TESTS=1 — سنجهٔ دسترس‌پذیری اجرا نشد');
} else {
  ok(false, 'axe-core پیدا نشد (npm i -g axe-core@4.10.3، یا AXE_PATH)');
}

//  ── پرچمِ خاموش ⇒ همان ویترینِ پیشین ──
await flag('shop.chat', false);
await page.goto('about:blank');   //  بارِ تازهٔ صفحه ⇒ هیچ اسکریپتِ پیشین نمی‌ماند
await page.goto(`${base}/store/${slug}#/o/${encodeURIComponent(o2.token)}`);
await page.waitForFunction(() => document.querySelectorAll('#o-lines li').length > 0, null, { timeout: 15000 });
ok(!(await shown('store-chat')) && await page.$('#o-chat') === null && await page.$('script[src="/store/chat.js"]') === null, 'پرچمِ خاموش ⇒ نه دکمه، نه کارت، نه اسکریپت');

ok(violations.length === 0, 'هیچ نقضِ CSP', violations.join(' | '));
ok(errors.length === 0, 'بی خطای جاوااسکریپت', errors.join(' | '));

await ctx.close();
await browser.close();
server.close();
await closeDb();
console.log(bad ? `❌ ${bad} ایراد` : '✅ ویترین: گفت‌وگوی هر سفارش، «بسته شد»، «مشکل دارم» و گفت‌وگوی عمومی');
process.exit(bad ? 1 : 0);
