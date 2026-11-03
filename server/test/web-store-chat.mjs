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

//  ── دکان تحویل می‌دهد ⇒ همان برگهٔ باز «بسته شد» (long-poll) ⇒ «مشکل دارم» ⇒ همان رشته باز ──
await move(o1.id, ['picked_up']);
await call('POST', `/api/shop/chat/conversations/${conv1.id}/receipts`, { readSeq: 2 }, T);   //  بیدار کردنِ long-poll با رسیدِ واقعی
await page.waitForFunction(() => !document.getElementById('ct-issue').hidden, null, { timeout: 30000 });
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
