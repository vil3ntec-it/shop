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
 *   • CHAT-07: پیامِ صوتیِ دکان ⇒ پخش‌کننده (پخش/توقف، زمان، پیشرفت)؛ بایت فقط با زدنِ «پخش» و با بلیت؛ پخش از blob:
 *     (readyState ≥ ۲ و زمانِ پیش‌رونده)؛ بی‌اینترنت ⇒ «تلاشِ دوباره»؛ نوعِ ناشناخته (AMR) بی درخواست ⇒ پیامِ روشن؛
 *     پیامِ پاک‌شده در برگهٔ عقب‌مانده ⇒ «پاک شده یا مهلتش گذشته» و سپس سنگِ قبر؛ بستنِ برگه صدا را می‌ایستاند؛
 *   • CHAT-05: مشتری عکس می‌فرستد — PNG ِ بزرگ ⇒ پیش‌نمایش (data:) ⇒ ۱۶۰۰px زیرِ ۱٫۵MB و پیش‌نمایشِ ۳۲۰px زیرِ ۶۴KB روی سرور،
 *     بی EXIF/«مکان»؛ JPEG ِ خوابیده با جهتِ ۶ ⇒ عمودی؛ شفافیت روی سفید؛ رویدادهای پیشرفتِ XHR؛ «لغو» بارگذاری را می‌بُرد؛
 *     افتادنِ شبکه در میانِ بارگذاری و پیام + «تلاشِ دوباره» ⇒ یک پیام؛ بی‌اینترنت ⇒ صف و رفتنِ خودکار؛ دکان عکس را می‌بیند؛
 *     کدِ عکس دیربار؛ axe ِ کنترل‌های تازه (با دوربینِ گوشیِ لمسی)؛
 *   • CHAT-02 (صفِ متن، `chat-out.js`): بی‌اینترنت متن به حبابِ «در صف» با «دوباره»/«لغو» می‌رود (localStorage فقط متن و شماره)،
 *     جوابِ گم‌شده + «دوباره» ِ حباب ⇒ همان clientId؛ برگهٔ بسته ⇒ صف با همان شناسه می‌ماند و گشودنِ دوباره می‌فرستد؛ ۴۰۱ صف را تا
 *     تأییدِ دوباره نگه می‌دارد؛ شمارهٔ دیگر ⇒ صف پاک؛
 *   • CHAT-14 (`chat-acts.js`): پاسخ (replyTo و نقل)، ویرایش تا `editUntil` ِ سرور (۴۰۳ ِ دیرهنگام ⇒ پیامِ روشن)، سنجاقِ فقط برای
 *     خودم، جست‌وجو و رفتن به پیام؛ axe ِ برچسبِ پاسخ، جست‌وجو و حبابِ «در صف»؛
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

//  ── CHAT-02/11: بی‌اینترنت ⇒ متن به حبابِ «در صف» می‌رود و با وصل شدن خودش یک بار می‌رود؛ جوابِ گم‌شده + «دوباره» ⇒ همان شناسه ──
const countFrom = async (convId, body) => Number((await one(
  "SELECT COUNT(*)::int AS n FROM shop_chat_messages WHERE conversation_id=$1 AND sender='customer' AND body=$2", [convId, body])).n);
const bubbles = (text, p = page) => p.evaluate((x) => [...document.querySelectorAll('#ct-list .ct-msg')].filter((li) => li.textContent.includes(x)).length, text);
//  حبابِ صف (`#ct-out`): data-st ِ هر حبابی که این متن را دارد؛ صفِ روی دیسک (`store-chat-out`)
const queued = (text, p = page) => p.evaluate((x) => [...document.querySelectorAll('#ct-out .ct-q')].filter((li) => li.textContent.includes(x)).map((li) => li.dataset.st).join(','), text);
const stored = (p = page) => p.evaluate(() => { try { return JSON.parse(localStorage.getItem('store-chat-out')); } catch { return 'bad'; } });
await ctx.setOffline(true);
await page.fill('#ct-text', 'پیامِ بی‌اینترنت');
await page.click('#ct-send');
await page.waitForFunction(() => document.querySelector('#ct-out .ct-q[data-st="waiting"]'), null, { timeout: 10000 });
//  ⇐ تغییرِ عمدی (CHAT-02 در وب): پیش‌تر متن در کادر می‌ماند و فقط یادداشتِ «در صف» داشت؛ حالا از کادر به حبابِ «در صف»
//  با «دوباره» و «لغو» می‌رود (کادر برای پیامِ بعدی آزاد، صف پس از بستنِ برگه هم می‌ماند)
ok(await countFrom(conv1.id, 'پیامِ بی‌اینترنت') === 0 && (await page.inputValue('#ct-text')) === '' &&
  await queued('پیامِ بی‌اینترنت') === 'waiting' && await bubbles('پیامِ بی‌اینترنت') === 0,
'بی‌اینترنت: متن از کادر به حبابِ «در صف» رفت — نه روی سرور، نه حبابِ «رفت»', await page.textContent('#ct-out'));
ok(await page.$('#ct-out .ct-q [data-q="retry"]') !== null && await page.$('#ct-out .ct-q [data-q="cancel"]') !== null,
  'حبابِ «در صف»: «دوباره» و «لغو»');
const disk1 = await stored();
ok(disk1 && disk1.phone === vc.body.phone && disk1.items.length === 1 && disk1.items[0].body === 'پیامِ بی‌اینترنت' &&
  !Object.keys(disk1.items[0]).some((k) => /media|kind|bytes|photo/i.test(k)),
'صف روی localStorage ِ store-chat-out: فقط متن، با برچسبِ همان شماره', JSON.stringify(disk1 && Object.keys(disk1.items[0] || {})));
await ctx.setOffline(false);
ok(await until(async () => (await countFrom(conv1.id, 'پیامِ بی‌اینترنت')) === 1, 20000), 'وصل شد ⇒ خودش رفت، بی زدنِ دوباره');
await page.waitForFunction(() => /بی‌اینترنت/.test(document.getElementById('ct-list').textContent), null, { timeout: 15000 });
ok((await page.inputValue('#ct-text')) === '' && await bubbles('پیامِ بی‌اینترنت') === 1 && await queued('پیامِ بی‌اینترنت') === '' &&
  (await stored()) === null, 'پس از رسیدن: یک حباب، صف و localStorage تهی');
const offMsg = await msgOf('پیامِ بی‌اینترنت');
ok(await stOf(page, offMsg.id) === 'sent|رفت' && await stOf(page, hour.id) === 'read|خوانده شد',
  'پیامِ تازه‌تر از «خواندم» ِ دکان «رفت» می‌ماند؛ هر پیام از seq ِ خودش', `${await stOf(page, offMsg.id)} / ${await stOf(page, hour.id)}`);

let drop = 1;
const posted = [];
const postUrl = `**/api/store/${slug}/chat/conversations/${conv1.id}/messages`;
await page.route(postUrl, async (route) => {
  if (route.request().method() !== 'POST') return route.fallback();
  posted.push(JSON.parse(route.request().postData() || '{}').clientId);
  if (drop < 1) return route.fallback();
  drop--;
  await route.fetch();            //  سرور ثبت می‌کند…
  return route.abort('failed');   //  …و جواب به صفحه نمی‌رسد
});
await page.fill('#ct-text', 'جوابِ گم‌شده');
await page.click('#ct-send');
await page.waitForSelector('#ct-out .ct-q[data-st="waiting"] [data-q="retry"]', { timeout: 10000 });
//  ⇐ تغییرِ عمدی: «زدنِ دوباره» پیش‌تر دوباره زدنِ «فرستادن» با همان متنِ کادر بود؛ متن حالا در حباب است ⇒ «دوباره» ِ همان حباب
await page.click('#ct-out .ct-q [data-q="retry"]');
await page.waitForFunction(() => /جوابِ گم‌شده/.test(document.getElementById('ct-list').textContent), null, { timeout: 15000 });
await new Promise((r) => setTimeout(r, 1500));
ok(await countFrom(conv1.id, 'جوابِ گم‌شده') === 1 && await bubbles('جوابِ گم‌شده') === 1 && await queued('جوابِ گم‌شده') === '' &&
  posted.length === 2 && posted[0] === posted[1],
'جوابِ گم‌شده و «دوباره» ِ حباب ⇒ همان clientId، یک پیام روی سرور و یک حباب', `${await countFrom(conv1.id, 'جوابِ گم‌شده')} / ${await bubbles('جوابِ گم‌شده')} / ${posted.length}`);
await page.unroute(postUrl);

//  ── CHAT-02: صف بستنِ برگه را تاب می‌آورد و با گشودنِ دوبارهٔ گفت‌وگو خودش می‌رود؛ «لغو»؛ ۴۰۱ صف را نگه می‌دارد؛ شمارهٔ دیگر ⇒ پاک ──
//  زمینهٔ جدا (localStorage ِ خودش) تا برگهٔ بازِ دیگری صف را نفرستد
const qc = await browser.newContext({ viewport: { width: 360, height: 760 }, locale: 'fa-AF' });
await qc.addInitScript((t) => {
  try { if (!localStorage.getItem('qc-seeded')) { localStorage.setItem('qc-seeded', '1'); localStorage.setItem('store-ticket', JSON.stringify(t)); } } catch { /* about:blank */ }
}, { phone: vc.body.phone, ticket: vc.body.ticket, expiresAt: vc.body.expiresAt });
const qOpen = async () => {
  const p = await qc.newPage();
  p.on('pageerror', (e) => errors.push(String(e)));
  p.on('console', (m) => { if (/Content Security Policy/i.test(m.text())) violations.push(m.text()); });
  await p.goto(`${base}/store/${slug}#/o/${encodeURIComponent(o1.token)}`);
  await p.waitForSelector('#o-chat-open', { timeout: 15000 });
  await p.click('#o-chat-open');
  await p.waitForFunction(() => document.querySelectorAll('#ct-list .ct-msg').length > 0, null, { timeout: 15000 });
  return p;
};
let qp = await qOpen();
ok(await qp.$('script[src="/store/chat-out.js"]') !== null && await qp.$('script[src="/store/chat-acts.js"]') !== null,
  'صف و کارهای گفت‌وگو فقط با گشودنِ برگه بار شدند (دیربار)');
await qc.setOffline(true);
for (const text of ['پیامِ ماندگار', 'پیامِ لغوشدنی']) { await qp.fill('#ct-text', text); await qp.click('#ct-send'); }
await qp.waitForFunction(() => document.querySelectorAll('#ct-out .ct-q').length === 2, null, { timeout: 10000 });
ok(await queued('پیامِ ماندگار', qp) !== '' && await queued('پیامِ لغوشدنی', qp) === 'queued',
  'دو پیامِ بی‌اینترنت: فقط سرِ صف می‌رود، دومی «در صف» پشتِ آن', `${await queued('پیامِ ماندگار', qp)} / ${await queued('پیامِ لغوشدنی', qp)}`);
await qp.click('#ct-out .ct-q[data-st="queued"] [data-q="cancel"]');
await qp.waitForFunction(() => document.querySelectorAll('#ct-out .ct-q').length === 1, null, { timeout: 5000 });
const keptId = (await stored(qp)).items[0].clientId;
ok((await stored(qp)).items.map((x) => x.body).join() === 'پیامِ ماندگار', '«لغو» ⇒ حباب و ردیفِ localStorage رفتند');
await qp.goto('about:blank');
await qp.close();
await qc.setOffline(false);
//  برگهٔ تازهٔ همان مرورگر، بی گشودنِ گفت‌وگو: صف همان‌جاست با همان شناسه. (درخواستی که برگهٔ رو به بسته شدن در راه داشت
//  شاید به سرور برسد — در Playwright گاهی پس از برداشتنِ «بی‌اینترنت» ِ برگهٔ بسته — و همان شناسه است ⇒ پایین فقط یک پیام.)
const peek = await qc.newPage();
await peek.goto(`${base}/store/${slug}`);
await peek.waitForFunction(() => { const b = document.getElementById('store-chat'); return b && !b.hidden; }, null, { timeout: 15000 });
const kept = await stored(peek);
ok(kept && kept.items.length === 1 && kept.items[0].clientId === keptId && kept.items[0].body === 'پیامِ ماندگار' &&
  await peek.$('script[src="/store/chat-out.js"]') === null, 'برگه بسته شد ⇒ پیام با همان شناسه در صفِ همین مرورگر ماند (بی گشودنِ گفت‌وگو چیزی بار نشد)',
  JSON.stringify(kept && kept.items.map((x) => x.state)));
await peek.close();
qp = await qOpen();
ok(await until(async () => (await countFrom(conv1.id, 'پیامِ ماندگار')) === 1, 20000), 'گشودنِ دوبارهٔ گفت‌وگو ⇒ پیامِ ماندهٔ صف رفت');
ok(Number((await one('SELECT COUNT(*)::int AS n FROM shop_chat_messages WHERE client_id=$1', [keptId])).n) === 1, 'همان clientId، یک پیام روی سرور');
await qp.waitForFunction(() => /پیامِ ماندگار/.test(document.getElementById('ct-list').textContent), null, { timeout: 15000 });
ok(await bubbles('پیامِ ماندگار', qp) === 1 && await queued('پیامِ ماندگار', qp) === '' && (await stored(qp)) === null &&
  await countFrom(conv1.id, 'پیامِ لغوشدنی') === 0, 'رسید: یک حباب، صفِ تهی؛ پیامِ لغوشده هرگز نرفت');

//  ۴۰۱ (بلیتِ دست‌خورده) ⇒ فرمِ تأیید و پیام «منتظرِ تأیید» می‌ماند؛ همان شماره دوباره ⇒ همان پیام می‌رود
await qp.evaluate(() => { const t = JSON.parse(localStorage.getItem('store-ticket')); t.ticket += 'xx'; localStorage.setItem('store-ticket', JSON.stringify(t)); });
await qp.fill('#ct-text', 'پس از تأیید');
await qp.click('#ct-send');
await qp.waitForFunction(() => !document.getElementById('ct-verify').hidden, null, { timeout: 10000 });
ok(await countFrom(conv1.id, 'پس از تأیید') === 0 && (await stored(qp)).items[0].state === 'verify',
  '۴۰۱ ⇒ فرمِ تأیید؛ پیام «منتظرِ تأیید» در صف ماند', JSON.stringify((await stored(qp)).items.map((x) => x.state)));
//  `refocus`: پیش از نوشتنِ کد، گفت‌وگو دوباره باز می‌شود (`verify()` و برگه دوباره) — تمرکزِ دیرِ ۶۰/۸۰ms ِ آن‌ها نباید کادرِ کدِ در
//  دست را برباید: پیش‌تر تایمرِ ۸۰ms ِ verify() میانِ fill ِ کد نشست، کد در کادرِ شماره رفت و verify/check ۴۰۰ داد (CI ِ #1022).
const reverify = async (p, num, { refocus = false } = {}) => {
  await query('UPDATE otp_codes SET created_at = created_at - $1', [10 * 60 * 1000]);   //  فاصلهٔ «کدِ تازه» (۱۲۰ ثانیه) گذشته
  await p.fill('#ct-phone', num);
  await p.click('#ct-vsend');
  await p.waitForFunction(() => /\d{4,}/.test(document.getElementById('ct-vmsg').textContent), null, { timeout: 10000 });
  const code = (await p.textContent('#ct-vmsg')).match(/(\d{4,})/)[1];
  if (refocus) {
    await p.evaluate(() => document.getElementById('store-chat').click());
    //  تایمرِ صفحه با مهلتِ بلندتر پس از تایمرهای ۶۰/۸۰ms ِ همین بازگشایی اجرا می‌شود (ترتیبِ setTimeout)
    await p.waitForFunction(() => !document.getElementById('ct-verify').hidden, null, { timeout: 10000 });
    await p.evaluate(() => new Promise((r) => setTimeout(r, 300)));
    const active = await p.evaluate(() => document.activeElement && document.activeElement.id);
    ok(active === 'ct-code', 'بازگشاییِ گفت‌وگو با کادرِ کدِ در دست ⇒ تمرکزِ دیرِ برگه و فرم آن را نربود', `تمرکز روی #${active}`);
  }
  await p.fill('#ct-code', code);
  //  CI ِ #1022 دو بار: `verify/check` ۴۰۰ (فقط کد یا شمارهٔ تهی/ناجور ۴۰۰ است؛ کدِ نادرست ۴۰۳) و فرم ماند ⇒ اگر باز شد،
  //  همان پاسخ و مقدارهای فرم در لاگ تا علت حدس نباشد (سنجش همان است: فرم باید بسته شود)
  const answer = p.waitForResponse((r) => /\/verify\/check$/.test(r.url()), { timeout: 10000 }).catch(() => null);
  await p.click('#ct-vcheck');
  try {
    await p.waitForFunction(() => document.getElementById('ct-verify').hidden, null, { timeout: 10000 });
  } catch (e) {
    const r = await answer;
    const form = await p.evaluate(() => ({ phone: document.getElementById('ct-phone').value, code: document.getElementById('ct-code').value,
      vmsg: document.getElementById('ct-vmsg').textContent, active: document.activeElement && document.activeElement.id }));
    console.log('reverify', num, 'code', code, 'form', JSON.stringify(form), 'check', r && r.status(), r && (await r.text().catch(() => '')).slice(0, 300),
      'sent', r && r.request().postData());
    throw e;
  }
};
await reverify(qp, phone);
ok(await until(async () => (await countFrom(conv1.id, 'پس از تأیید')) === 1, 15000), 'همان شماره دوباره تأیید شد ⇒ همان پیام با همان شناسه رفت');
//  شمارهٔ دیگر روی همین دستگاه ⇒ صفِ نفرِ پیشین پاک و هرگز به نامِ این یکی نمی‌رود
await qc.setOffline(true);
await qp.fill('#ct-text', 'پیامِ نفرِ اول');
await qp.click('#ct-send');
await qp.waitForFunction(() => document.querySelector('#ct-out .ct-q[data-st="waiting"]'), null, { timeout: 10000 });
await qp.evaluate(() => localStorage.removeItem('store-ticket'));   //  نفرِ اول رفت (بلیت نیست، هنوز بی‌اینترنت)
await qp.goto('about:blank');
await qp.close();
await qc.setOffline(false);
qp = await qc.newPage();
qp.on('pageerror', (e) => errors.push(String(e)));
await qp.goto(`${base}/store/${slug}`);
await qp.waitForFunction(() => { const b = document.getElementById('store-chat'); return b && !b.hidden; }, null, { timeout: 15000 });
await qp.click('#store-chat');
await qp.waitForFunction(() => { const v = document.getElementById('ct-verify'); return v && !v.hidden; }, null, { timeout: 15000 });
await reverify(qp, '0791112255', { refocus: true });
await sleep(2500);
ok((await stored(qp)) === null && await countFrom(conv1.id, 'پیامِ نفرِ اول') === 0 &&
  Number((await one("SELECT COUNT(*)::int AS n FROM shop_chat_messages WHERE body='پیامِ نفرِ اول'")).n) === 0,
'شمارهٔ دیگر تأیید شد ⇒ صف پاک؛ پیامِ نفرِ اول نه در رشتهٔ او رفت، نه به نامِ نفرِ دوم');
await qc.close();

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
//  long-poll ِ دکان با **هر** تغییرِ گفت‌وگو بیدار می‌شود (رسیدِ برگه‌ای از مشتری، …) و آن‌گاه بی سنگِ قبر برمی‌گردد (PostgreSQL ِ پربار:
//  «long-poll ِ دکان … سنگِ قبر را گرفت» سرخ با پاسخِ تهیِ ۱۸ms) ⇒ همان long-poll از cursor ِ پاسخ تا رسیدنِ سنگِ قبر، در همان ۲۰ ثانیه
const shopWait = (async () => {
  let cur = shopCur;
  let r = null;
  for (const end = Date.now() + 20000; Date.now() < end;) {
    r = await call('GET', `/api/shop/chat/conversations/${conv1.id}/messages?after=${cur}&wait=${Math.max(1, end - Date.now())}`, null, T);
    if (r.status !== 200 || (r.body.messages || []).some((m) => m.id === doomed.id)) return r;
    cur = r.body.cursor;
  }
  return r;
})();
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

//  ── CHAT-14 در وب (`chat-acts.js` ِ دیربار): پاسخ، ویرایش تا `editUntil` ِ سرور، سنجاقِ فقط برای خودم، جست‌وجو ──
const actOf = async (id, act) => {
  await page.click(li(id, '.ct-more'));
  await page.waitForSelector(li(id, '[data-del="me"]'));
  const b = await page.$(li(id, `[data-act="${act}"]`));
  if (!b) await page.click(li(id, '.ct-more'));
  return b;
};
ok(Boolean(await actOf(askId, 'reply')) && await page.$(li(askId, '[data-act="edit"]')) === null, 'پیامِ دکان: «پاسخ» هست، «ویرایش» نه');
await page.click(li(askId, '[data-act="reply"]'));
ok(/پاسخ به: کی می‌آیید؟/.test(await page.textContent('#ct-chip')) && await page.evaluate(() => document.activeElement.id === 'ct-text'),
  '«پاسخ» ⇒ برچسبِ «پاسخ به …» بالای کادر و تمرکز در کادر', await page.textContent('#ct-chip'));
await page.fill('#ct-text', 'ساعتِ شش');
await page.click('#ct-send');
await page.waitForFunction(() => /ساعتِ شش/.test(document.getElementById('ct-list').textContent), null, { timeout: 10000 });
const six = await msgOf('ساعتِ شش');
const sixRow = await one('SELECT reply_to FROM shop_chat_messages WHERE id=$1', [six.id]);
ok(sixRow.reply_to === askId && /کی می‌آیید؟/.test(await page.textContent(li(six.id, '.ct-quote'))) && await page.isHidden('#ct-chip'),
  'پاسخ از همان صف رفت: replyTo روی سرور، نقلِ پیامِ اصل در حباب، برچسب برداشته شد', String(sixRow.reply_to));
await page.click(li(six.id, '.ct-quote'));
ok(await page.evaluate((x) => document.activeElement && document.activeElement.dataset.id === x, askId), 'زدنِ نقل ⇒ تمرکز روی پیامِ اصل');

ok(await actOf(lostMsg.id, 'edit') === null, 'پیامِ کهنهٔ خودم (پس از editUntil ِ سرور): «ویرایش» نیست');
ok(Boolean(await actOf(six.id, 'edit')), 'پیامِ تازهٔ خودم: «ویرایش» هست');
await page.click(li(six.id, '[data-act="edit"]'));
ok(await page.inputValue('#ct-text') === 'ساعتِ شش' && /ذخیره/.test(await page.textContent('#ct-send')), '«ویرایش» ⇒ همان متن در کادر و «ذخیره»');
await page.fill('#ct-text', 'ساعتِ هفت');
await page.click('#ct-send');
await page.waitForFunction((x) => /ساعتِ هفت/.test((document.querySelector(`#ct-list li[data-id="${x}"]`) || {}).textContent || ''), six.id, { timeout: 10000 });
const sevenRow = await one('SELECT body, edited_at FROM shop_chat_messages WHERE id=$1', [six.id]);
ok(sevenRow.body === 'ساعتِ هفت' && sevenRow.edited_at && await countFrom(conv1.id, 'ساعتِ هفت') === 1 && await page.$(li(six.id, '[data-edited]')) !== null &&
  !/ذخیره/.test(await page.textContent('#ct-send')) && await page.inputValue('#ct-text') === '',
'ویرایش ⇒ همان پیام روی سرور (نه پیامِ تازه) و «ویرایش‌شده» کنارِ زمان؛ کادر خالی', JSON.stringify(sevenRow));
//  ساعتِ سرور حرفِ آخر است: دکمه هنوز بود ولی مهلت در سرور گذشت ⇒ ۴۰۳ و پیامِ روشن؛ متن در کادر می‌ماند
await actOf(six.id, 'edit');
await page.click(li(six.id, '[data-act="edit"]'));
await page.fill('#ct-text', 'ساعتِ هشت');
await query('UPDATE shop_chat_messages SET created_at = created_at - $2 WHERE id=$1', [six.id, 3600 * 1000]);
await page.click('#ct-send');
await page.waitForFunction(() => /وقتِ ویرایش/.test(document.getElementById('ct-msg').textContent), null, { timeout: 10000 });
ok((await one('SELECT body FROM shop_chat_messages WHERE id=$1', [six.id])).body === 'ساعتِ هفت' && await page.inputValue('#ct-text') === 'ساعتِ هشت' &&
  !/ذخیره/.test(await page.textContent('#ct-send')),
'مهلتِ سرور گذشت ⇒ «وقتِ ویرایش… گذشته»، پیام همان و متن در کادر (حالتِ ویرایش بسته)', await page.textContent('#ct-msg'));
await page.fill('#ct-text', '');

ok(Boolean(await actOf(askId, 'pin')), '«سنجاق» در نوارِ کارها');
await page.click(li(askId, '[data-act="pin"]'));
await page.waitForFunction(() => !document.getElementById('ct-pins').hidden, null, { timeout: 10000 });
const pinRow = await one("SELECT side FROM shop_chat_pins WHERE message_id=$1", [askId]);
const shopPins = (await call('GET', `/api/shop/chat/conversations/${conv1.id}`, null, T)).body.conversation.pinned || [];
ok(/کی می‌آیید؟/.test(await page.textContent('#ct-pins')) && pinRow && pinRow.side === 'customer' && !shopPins.some((x) => x.messageId === askId),
  'سنجاق ⇒ نوارِ «سنجاق‌شده» با متنِ همین صفحه؛ روی سرور فقط برای مشتری، دکان نمی‌بیند', await page.textContent('#ct-pins'));
await page.click(`#ct-pins [data-pin="${askId}"]`);
ok(await page.evaluate((x) => document.activeElement && document.activeElement.dataset.id === x, askId), 'زدنِ سنجاق ⇒ رفتن به همان پیام');
ok(Boolean(await actOf(askId, 'unpin')), 'پیامِ سنجاق‌شده: «برداشتنِ سنجاق»');
await page.click(li(askId, '[data-act="unpin"]'));
await page.waitForFunction(() => document.getElementById('ct-pins').hidden, null, { timeout: 10000 });
ok(!(await one("SELECT 1 AS x FROM shop_chat_pins WHERE message_id=$1", [askId])), 'برداشتنِ سنجاق ⇒ نوار پنهان و ردیفِ سرور رفت');

await page.click('#ct-find-btn');
ok(await page.getAttribute('#ct-find-btn', 'aria-expanded') === 'true' && await page.evaluate(() => document.activeElement.id === 'ct-find-q'),
  'جست‌وجو: دکمه با aria-expanded و تمرکز در کادرِ جست‌وجو');
await page.fill('#ct-find-q', 'ه');
await page.click('#ct-find-go');
ok(/دست‌کم دو حرف/.test(await page.textContent('#ct-find-st')), 'یک حرف ⇒ «دست‌کم دو حرف»، بی درخواست');
await page.fill('#ct-find-q', 'هفت');
await page.click('#ct-find-go');
await page.waitForSelector(`#ct-find-list [data-hit="${six.id}"]`, { timeout: 10000 });
ok((await page.$$('#ct-find-list li')).length === 1 && /ساعتِ هفت/.test(await page.textContent('#ct-find-list')), 'جست‌وجو ⇒ همان پیام، فقط از همین شماره و همین دکان',
  await page.textContent('#ct-find-list'));
await page.click(`#ct-find-list [data-hit="${six.id}"]`);
ok(await page.isHidden('#ct-find') && await page.evaluate((x) => document.activeElement && document.activeElement.dataset.id === x, six.id),
  'زدنِ نتیجه ⇒ جست‌وجو بسته و تمرکز روی همان پیام');
ok(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth), 'عرضِ گوشی (۳۶۰): کارها و جست‌وجو بی پیمایشِ افقی');

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
//  ⛔ رشتهٔ همین شماره با کلیدِ یکتا (دکان، شماره، ''): گامِ «شمارهٔ دیگر» (۰۷۹۱۱۱۲۲۵۵) هم در همین دکان گفت‌وگوی عمومی دارد و
//  SELECT ِ بی شماره و بی ORDER BY هر کدام را شاید بدهد — پس از autoanalyze ِ PostgreSQL ِ CI ⇒ Seq Scan به ترتیبِ heap ⇒ رشتهٔ
//  دیگر کهنه می‌شد، این یکی باز می‌ماند و `#ct-old` هرگز نمی‌آمد (CI ِ #1024). بازتولید: ANALYZE پیش از همین SELECT.
const gen = await one("SELECT id FROM shop_chat_conversations WHERE shop_id=$1 AND customer_phone=$2 AND order_id=''", [shopId, vc.body.phone]);
ok(gen && ((await lastFrom(gen.id, 'customer')) || {}).body === 'آرد دارید؟', 'سرور: گفت‌وگوی عمومیِ همین شماره همان پیامِ تازه را دارد');
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
  //  CHAT-14 و صف (CHAT-02): برچسبِ «پاسخ به …»، کادرِ جست‌وجو با نتیجه و حبابِ «در صف» با «دوباره»/«لغو» هم زیرِ همین سنجه
  await ap.click('#ct-find-btn');
  await ap.fill('#ct-find-q', 'گم‌شده');
  await ap.click('#ct-find-go');
  await ap.waitForSelector('#ct-find-list li', { timeout: 10000 });
  await ac.setOffline(true);
  await ap.fill('#ct-text', 'پیامِ axe');
  await ap.click('#ct-send');
  await ap.waitForSelector('#ct-out .ct-q [data-q="retry"]', { timeout: 10000 });
  await ap.evaluate(() => [...document.querySelectorAll('#ct-list .ct-msg.shop .ct-more')].pop().click());
  await ap.click('#ct-list .ct-acts [data-act="reply"]');
  //  نوارِ کارها و پرسشِ تأیید هم زیرِ همان سنجه (کنتراستِ دکمه‌ها درونِ حبابِ تیره)
  await ap.evaluate(() => [...document.querySelectorAll('#ct-list .ct-msg.mine .ct-more')].pop().click());
  await ap.waitForSelector('#ct-list .ct-acts [data-del]');
  if (await ap.$('#ct-list .ct-acts [data-del="all"]')) await ap.click('#ct-list .ct-acts [data-del="all"]');
  else await ap.click('#ct-list .ct-acts [data-del="me"]');
  await ap.waitForSelector('#ct-list .ct-acts [data-yes]');
  ok(!(await ap.isHidden('#ct-chip')) && !(await ap.isHidden('#ct-find')), 'axe: برچسبِ پاسخ، جست‌وجو و حبابِ «در صف» همه پیدا');
  await ap.addScriptTag({ content: axeSrc });
  const v = await ap.evaluate(async () => (await window.axe.run(document.getElementById('chat-sheet'),
    { runOnly: ['color-contrast', 'button-name', 'label', 'image-alt', 'link-name', 'aria-allowed-attr', 'nested-interactive', 'list', 'listitem'] })).violations
    .map((x) => `${x.id}: ${x.nodes.slice(0, 2).map((n) => n.target.join(' ')).join(' ; ')}`));
  ok(v.length === 0, 'دسترس‌پذیری (axe-core): برگهٔ گفت‌وگو، کارهای CHAT-14 و صف', v.join(' | '));
  await ac.close();
} else if (process.env.SKIP_BROWSER_TESTS === '1') {
  console.log('⏭  axe-core نیست و SKIP_BROWSER_TESTS=1 — سنجهٔ دسترس‌پذیری اجرا نشد');
} else {
  ok(false, 'axe-core پیدا نشد (npm i -g axe-core@4.10.3، یا AXE_PATH)');
}

//  ── CHAT-07: پیامِ صوتیِ دکان در ویترین ──
//  نمونهٔ صوتی در خودِ آزمون ساخته می‌شود (نه پروندهٔ دودویی): قاب‌های بی‌صدای MP3 (MPEG-1 لایهٔ ۳، ۱۲۸kbps، ۴۴٫۱kHz،
//  تک‌کاناله، ۴۱۷ بایت، اطلاعاتِ جانبیِ صفر)؛ AMR فقط سرآیند و قاب — نوعی که مرورگرِ رومیزی پخش نمی‌کند.
const mp3 = (frames) => {
  const f = Buffer.alloc(417); f[0] = 0xff; f[1] = 0xfb; f[2] = 0x90; f[3] = 0xc0;
  return Buffer.concat(Array.from({ length: frames }, () => f));
};
const amr = Buffer.concat([Buffer.from('#!AMR\n'), ...Array.from({ length: 10 }, () => Buffer.concat([Buffer.from([0x3c]), Buffer.alloc(31)]))]);
const o3 = await checkout();
await move(o3.id, ['accepted', 'picking', 'packed', 'ready_for_pickup']);
const conv3 = (await call('POST', '/api/shop/chat/conversations', { orderId: o3.id }, T)).body.conversation;
let vn = 0;
const sayVoice = async (buf, durationMs) => {
  const up = await fetch(`${base}/api/shop/chat/conversations/${conv3.id}/media`, {
    method: 'POST', headers: { Authorization: `Bearer ${T}`, 'Content-Type': 'application/octet-stream' }, body: buf,
  }).then((r) => r.json());
  const r = await call('POST', `/api/shop/chat/conversations/${conv3.id}/messages`,
    { clientId: `client-voice-${Date.now()}-${++vn}`, kind: 'audio', mediaId: up.mediaId, meta: { durationMs } }, T);
  return { up, msg: r.body && r.body.message, status: r.status };
};
const vA = await sayVoice(mp3(200), 5224);   //  ~۵ ثانیه
const vB = await sayVoice(mp3(30), 784);
const vC = await sayVoice(amr, 200);
ok([vA, vB, vC].every((v) => v.status === 201) && vA.up.mime === 'audio/mpeg' && vC.up.mime === 'audio/amr',
  'دکان سه پیامِ صوتی فرستاد (نوع از روی بایت: MP3، AMR)', JSON.stringify([vA.up, vC.up]).slice(0, 160));
const cspStore = (await fetch(`${base}/store/${slug}`)).headers.get('content-security-policy') || '';
ok(/(^|; )media-src 'self' blob:;/.test(cspStore) && !/media-src[^;]*(data:|\*)/.test(cspStore), "CSP ِ ویترین: media-src 'self' blob: — بی data: و بی *", cspStore);

const hits = [];
const onMedia = (r) => {
  const m = /\/chat\/conversations\/[^/]+\/media\/([^/?]+)/.exec(r.url());
  if (m) hits.push({ id: decodeURIComponent(m[1]), ticket: Boolean(r.headers()['x-store-ticket']) });
};
page.on('request', onMedia);
await openOrder(o3);
await page.click('#o-chat-open');
await page.waitForFunction(() => document.querySelectorAll('#ct-list .ct-voice').length === 3, null, { timeout: 15000 });
const voices = page.locator('#ct-list .ct-voice');
const vstate = (i) => page.evaluate((n) => {
  const v = document.querySelectorAll('#ct-list .ct-voice')[n];
  if (!v) return null;
  const b = v.querySelector('button'); const a = v.querySelector('audio'); const e = v.querySelector('.ct-aerr');
  return { st: v.getAttribute('data-st'), label: b.getAttribute('aria-label'), disabled: b.disabled, group: v.getAttribute('aria-label'),
    bar: Boolean(v.querySelector('progress[aria-label]')), time: v.querySelector('.ct-time').textContent, err: e && !e.hidden ? e.textContent : '',
    audio: a ? { rs: a.readyState, t: a.currentTime, paused: a.paused, ended: a.ended, src: a.src.slice(0, 5) } : null };
}, i);
const s0 = await vstate(0);
ok(s0 && s0.group === 'پیامِ صوتی' && s0.label === 'پخش' && s0.bar && /[۰-۹]:[۰-۹]{2}/.test(s0.time) && s0.st === 'idle',
  'حبابِ صدا: پخش‌کننده با نامِ دسترس‌پذیر، دکمهٔ «پخش»، پیشرفت و زمان (از durationMs)', JSON.stringify(s0));
await page.waitForTimeout(1200);
ok(hits.length === 0 && await page.$('#ct-list audio') === null, 'پیش از «پخش» هیچ بایتِ صدا و هیچ <audio> (بارِ دیربار)', JSON.stringify(hits));

//  بی‌اینترنت ⇒ پیامِ روشن و «تلاشِ دوباره»؛ وصل ⇒ همان دکمه صدا را می‌آورد و پخش می‌کند
await ctx.setOffline(true);
await voices.nth(0).locator('button').click();
await page.waitForFunction(() => document.querySelectorAll('#ct-list .ct-voice')[0].getAttribute('data-st') === 'err', null, { timeout: 10000 });
const sNet = await vstate(0);
ok(/اینترنت/.test(sNet.err) && sNet.label === 'تلاشِ دوباره' && !sNet.disabled && !sNet.audio, 'بی‌اینترنت: «اتصال برقرار نشد…» و دکمهٔ «تلاشِ دوباره»', JSON.stringify(sNet));
await ctx.setOffline(false);
await voices.nth(0).locator('button').click();
const played = await until(async () => { const s = await vstate(0); return s.audio && s.audio.rs >= 2 && s.audio.t > 0; }, 15000);
const sPlay = await vstate(0);
ok(played && sPlay.st === 'playing' && sPlay.label === 'توقف' && sPlay.audio.src === 'blob:' && !sPlay.err,
  'زدنِ «پخش»: صدا از blob: پخش می‌شود (readyState ≥ ۲، زمان پیش می‌رود)، دکمه «توقف»', JSON.stringify(sPlay));
ok(hits.filter((h) => h.id === vA.up.mediaId).every((h) => h.ticket) && hits.some((h) => h.id === vA.up.mediaId),
  'بایتِ صدا از همان درِ رسانهٔ چت و با بلیتِ مشتری', JSON.stringify(hits));
await voices.nth(0).locator('button').click();
await page.waitForFunction(() => document.querySelectorAll('#ct-list .ct-voice')[0].getAttribute('data-st') === 'ready', null, { timeout: 5000 });
const sPause = await vstate(0);
ok(sPause.label === 'پخش' && sPause.audio.paused && /\//.test(sPause.time), '«توقف» ⇒ صدا می‌ایستد، دکمه «پخش»، زمانِ گذشته / کل', JSON.stringify(sPause));
const hitsBefore = hits.length;
await voices.nth(0).locator('button').click();
await page.waitForFunction(() => { const a = document.querySelector('#ct-list .ct-voice audio'); return a && !a.paused; }, null, { timeout: 5000 });
ok(hits.length === hitsBefore, 'پخشِ دوباره از همان بایتِ آمده، بی درخواستِ تازه');

//  AMR در این مرورگر پخش نمی‌شود ⇒ پیش از هر درخواست پیامِ روشن
await voices.nth(2).locator('button').click();
await page.waitForFunction(() => document.querySelectorAll('#ct-list .ct-voice')[2].getAttribute('data-st') === 'err', null, { timeout: 5000 });
const sAmr = await vstate(2);
ok(/مرورگر/.test(sAmr.err) && sAmr.disabled && !hits.some((h) => h.id === vC.up.mediaId), 'نوعِ ناشناخته (AMR): «این مرورگر … پخش نمی‌کند»، بی بارگیری', JSON.stringify(sAmr));

//  برگهٔ عقب‌مانده: دکان پیام را «برای همه» پاک کرده و long-poll هنوز نرسیده ⇒ «پخش» پیامِ روشن؛ رسیدنش ⇒ سنگِ قبر
const held = [];
const pollOf = (u) => u.pathname === `/api/store/${slug}/chat/conversations/${conv3.id}/messages`;
await page.route(pollOf, (route) => (route.request().method() === 'GET' ? held.push(route) : route.fallback()));
await call('POST', `/api/shop/chat/conversations/${conv3.id}/messages`, { clientId: `client-wake-${Date.now()}`, body: 'صدا را گوش کنید' }, T);
await page.waitForFunction(() => /گوش کنید/.test(document.getElementById('ct-list').textContent), null, { timeout: 30000 });
ok(await until(async () => held.length > 0, 10000), 'long-poll ِ بعدیِ برگه نگه داشته شد (برگهٔ عقب‌مانده)');
const delB = await call('DELETE', `/api/shop/chat/conversations/${conv3.id}/messages/${vB.msg.id}?for=all`, null, T);
await voices.nth(1).locator('button').click();
await page.waitForFunction(() => document.querySelectorAll('#ct-list .ct-voice')[1].getAttribute('data-st') === 'err', null, { timeout: 10000 });
const sGone = await vstate(1);
ok(delB.status === 200 && /پاک شده یا مهلتش گذشته/.test(sGone.err) && sGone.disabled && !sGone.audio,
  'پیامِ صوتیِ پاک‌شده: «… دیگر روی سرور نیست؛ پاک شده یا مهلتش گذشته است.»، بی «دوباره»', JSON.stringify(sGone));
await page.unroute(pollOf);
for (const r of held) await r.continue().catch(() => { /* درخواستِ رفته */ });
await page.waitForFunction(() => document.querySelectorAll('#ct-list .ct-voice').length === 2, null, { timeout: 30000 });
ok(await bubbles('این پیام پاک شد') === 1, 'سپس long-poll ⇒ حبابِ صدا سنگِ قبر شد');

//  بستنِ برگه ⇒ صدا می‌ایستد
if (await page.evaluate(() => document.querySelector('#ct-list .ct-voice audio').paused)) await voices.nth(0).locator('button').click();
await page.waitForFunction(() => !document.querySelector('#ct-list .ct-voice audio').paused, null, { timeout: 5000 });
await page.keyboard.press('Escape');
await page.waitForFunction(() => document.getElementById('chat-sheet').hidden, null, { timeout: 5000 });
ok(await page.evaluate(() => document.querySelector('#ct-list .ct-voice audio').paused), 'بستنِ برگه در میانِ پخش ⇒ صدا ایستاد');
page.off('request', onMedia);

if (axeSrc) {
  const ac = await browser.newContext({ viewport: { width: 360, height: 760 }, bypassCSP: true });
  await ac.addInitScript((t) => { try { localStorage.setItem('store-ticket', JSON.stringify(t)); } catch { /* about:blank */ } },
    { phone: vc.body.phone, ticket: vc.body.ticket, expiresAt: vc.body.expiresAt });
  const ap = await ac.newPage();
  await ap.goto(`${base}/store/${slug}#/o/${encodeURIComponent(o3.token)}`);
  await ap.waitForSelector('#o-chat-open', { timeout: 15000 });
  await ap.click('#o-chat-open');
  await ap.waitForFunction(() => document.querySelectorAll('#ct-list .ct-voice').length > 0, null, { timeout: 15000 });
  await ap.addScriptTag({ content: axeSrc });
  const v = await ap.evaluate(async () => (await window.axe.run(document.getElementById('chat-sheet'),
    { runOnly: ['color-contrast', 'button-name', 'aria-allowed-attr', 'aria-progressbar-name', 'nested-interactive'] })).violations
    .map((x) => `${x.id}: ${x.nodes.slice(0, 2).map((n) => n.target.join(' ')).join(' ; ')}`));
  ok(v.length === 0, 'دسترس‌پذیری (axe-core): پخش‌کنندهٔ صدا', v.join(' | '));
  await ac.close();
}

//  ── CHAT-05: مشتری از ویترین عکس می‌فرستد ──
//  نمونه‌ها در خودِ آزمون ساخته می‌شوند: PNG ِ بزرگ (۲۰۰۰×۱۵۰۰، چند مگابایت، eXIf و tEXt با «مکان»)، JPEG ِ واقعیِ
//  خوابیده با EXIF ِ جهتِ ۶ (+ XMP و COM با «مکان»)، PNG ِ نیمه‌شفاف و یک JPEG ِ کوچک.
const SECRET = 'GPS-SECRET-34.5,69.1';
const seg = (m, body) => { const hd = Buffer.from([0xff, m, 0, 0]); hd.writeUInt16BE(body.length + 2, 2); return Buffer.concat([hd, body]); };
const tiffOrient = (o) => {
  const tf = Buffer.alloc(26); tf.write('MM', 0, 'latin1'); tf.writeUInt16BE(42, 2); tf.writeUInt32BE(8, 4); tf.writeUInt16BE(1, 8);
  tf.writeUInt16BE(0x0112, 10); tf.writeUInt16BE(3, 12); tf.writeUInt32BE(1, 14); tf.writeUInt16BE(o, 18);
  return tf;
};
const hash = (x, y) => { let v = (Math.imul(x, 374761393) + Math.imul(y, 668265263)) | 0; v = Math.imul(v ^ (v >>> 13), 1274126177); return (v ^ (v >>> 16)) & 255; };
const bigRaw = F.pngImage(2000, 1500, (x, y) => [x & 255, hash(x, y), y & 255], { mode: 'rgb' });   //  یک کانالِ نوفه ⇒ چند مگابایت
const bigPng = Buffer.concat([bigRaw.subarray(0, 33),
  F.pngChunk('eXIf', Buffer.concat([Buffer.from([0x4d, 0x4d, 0, 42, 0, 0, 0, 8, 0, 0, 0, 0, 0, 0]), Buffer.from(SECRET, 'latin1')])),
  F.pngChunk('tEXt', Buffer.from(`Comment\0${SECRET}`, 'latin1')), bigRaw.subarray(33)]);
const plain = F.jpegImage(416, 208, (x) => (x < 208 ? [220, 30, 30] : [30, 30, 220]), { color: true });
const rotated = Buffer.concat([plain.subarray(0, 2), seg(0xe1, Buffer.concat([Buffer.from('Exif\0\0', 'latin1'), tiffOrient(6)])),
  seg(0xe1, Buffer.from(`http://ns.adobe.com/xap/1.0/\0<x:xmpmeta>${SECRET}</x:xmpmeta>`, 'latin1')), seg(0xfe, Buffer.from(SECRET, 'latin1')), plain.subarray(2)]);
const clear = F.pngImage(300, 200, (x) => (x < 150 ? [0, 0, 0, 0] : [20, 160, 60, 255]), { mode: 'rgba' });
const small = F.jpegImage(320, 320, (x, y) => [(x * 3) & 255, 120, (y * 3) & 255], { color: true });
const chatMedia = require('../src/lib/chat-media');
ok(bigPng.length > 1536 * 1024, `PNG ِ آزمون از سقفِ سرور بزرگ‌تر است (${Math.round(bigPng.length / 1024)}KB، ۲۰۰۰×۱۵۰۰)`);

const o4 = await checkout();
await move(o4.id, ['accepted', 'picking', 'packed', 'ready_for_pickup']);
const conv4 = (await call('POST', '/api/shop/chat/conversations', { orderId: o4.id }, T)).body.conversation;
const photos = async () => Number((await one(
  "SELECT COUNT(*)::int AS n FROM shop_chat_messages WHERE conversation_id=$1 AND sender='customer' AND kind='image' AND deleted_at IS NULL", [conv4.id])).n);
const mediaRows = async () => Number((await one('SELECT COUNT(*)::int AS n FROM shop_chat_media WHERE conversation_id=$1', [conv4.id])).n);
const mine = () => page.evaluate(() => document.querySelectorAll('#ct-list .ct-msg.mine img.ct-img').length);
const phState = () => page.evaluate(() => { const b = document.getElementById('ct-ph'); return b && !b.hidden ? b.getAttribute('data-st') : 'gone'; });
const lastPhoto = async () => {
  const r = await call('GET', `/api/shop/chat/conversations/${conv4.id}/messages?after=0&limit=200`, null, T);
  const m = (r.body.messages || []).filter((x) => x.kind === 'image' && x.sender === 'customer').pop();
  const get = async (id) => Buffer.from(await (await fetch(`${base}/api/shop/chat/conversations/${conv4.id}/media/${encodeURIComponent(id)}`,
    { headers: { Authorization: `Bearer ${T}` } })).arrayBuffer());
  return { m, main: await get(m.media.id), thumb: await get(m.media.thumbId) };
};
const pixels = (buf, pts) => page.evaluate(async ({ b64, pts: at }) => {
  const bin = atob(b64); const u = new Uint8Array(bin.length);
  for (let i = 0; i < bin.length; i++) u[i] = bin.charCodeAt(i);
  const bm = await createImageBitmap(new Blob([u], { type: 'image/jpeg' }));
  const c = document.createElement('canvas'); c.width = bm.width; c.height = bm.height;
  const x = c.getContext('2d'); x.drawImage(bm, 0, 0);
  return at.map(([fx, fy]) => Array.from(x.getImageData(Math.floor(fx * (bm.width - 1)), Math.floor(fy * (bm.height - 1)), 1, 1).data.slice(0, 3)));
}, { b64: buf.toString('base64'), pts });
const clean = (b) => { const s = b.toString('latin1'); return !s.includes('Exif') && !s.includes(SECRET) && !s.includes('xmpmeta'); };
const attach = async (buffer, name, mimeType) => {
  await page.setInputFiles('#ct-file', { name, mimeType, buffer });
  await page.waitForFunction(() => { const b = document.getElementById('ct-ph'); return b && !b.hidden && /^(ready|err)$/.test(b.getAttribute('data-st')); }, null, { timeout: 20000 });
};

await openOrder(o4);
await page.click('#o-chat-open');
await page.waitForFunction(() => { const f = document.getElementById('ct-form'); return f && !f.hidden; }, null, { timeout: 15000 });
const inputs = await page.evaluate(() => ['ct-file', 'ct-cam'].map((id) => { const i = document.getElementById(id); return i && [i.type, i.accept, i.getAttribute('capture')]; }));
ok(JSON.stringify(inputs) === JSON.stringify([['file', 'image/*', null], ['file', 'image/*', 'environment']]) && Boolean(await page.getAttribute('#ct-ph-add', 'aria-label')),
  'گزینشِ عکس: دکمهٔ «افزودنِ عکس» (accept=image/*) و دوربینِ گوشی (capture=environment)', JSON.stringify(inputs));
ok(await page.$('script[src="/store/chat-photo.js"]') === null, 'کدِ کوچک‌کردنِ عکس پیش از برگزیدنِ عکس بار نشده (دیربار)');
await page.evaluate(() => {
  window.__up = { progress: 0, aborts: 0, sends: 0, last: null };
  const send = XMLHttpRequest.prototype.send;
  XMLHttpRequest.prototype.send = function (b) {
    window.__up.sends++;
    this.upload.addEventListener('progress', (e) => { window.__up.progress++; window.__up.last = [e.loaded, e.total]; });
    this.addEventListener('abort', () => { window.__up.aborts++; });
    return send.call(this, b);
  };
});

//  ۱. PNG ِ بزرگ ⇒ پیش‌نمایش ⇒ «فرستادن» ⇒ ≤ ۱۶۰۰px و زیرِ سقف، بی EXIF؛ دکان همان عکس را می‌بیند
await attach(bigPng, 'big.png', 'image/png');
const prev1 = await page.evaluate(() => { const i = document.getElementById('ct-ph-img'); return { st: document.getElementById('ct-ph').getAttribute('data-st'), src: i.src.slice(0, 15), w: i.naturalWidth, h: i.naturalHeight, shown: !i.hidden }; });
ok(prev1.st === 'ready' && prev1.src === 'data:image/jpeg' && prev1.shown && prev1.w <= 320 && prev1.h <= 320,
  'PNG ِ بزرگ ⇒ پیش‌نمایش (data:، ≤ ۳۲۰px) و دکمهٔ «فرستادنِ عکس»', JSON.stringify(prev1));
ok(await page.$('script[src="/store/chat-photo.js"]') !== null, 'کدِ عکس فقط با برگزیدنِ عکس آمد');
await page.click('#ct-ph-send');
await page.waitForFunction(() => document.getElementById('ct-ph').hidden, null, { timeout: 20000 });
ok(await until(async () => (await photos()) === 1) && await until(async () => (await mine()) === 1), 'عکس رفت: یک پیامِ عکس روی سرور و یک حباب «عکسِ شما»');
const up1 = await page.evaluate(() => window.__up);
ok(up1.progress >= 2 && up1.last && up1.last[0] === up1.last[1] && up1.sends === 2, 'پیشرفت از رویدادهای بارگذاریِ XHR (پیش‌نمایش و اصل)', JSON.stringify(up1));
const p1 = await lastPhoto();
const pd1 = chatMedia.dimsOf(p1.main, 'image/jpeg'); const pt1 = chatMedia.dimsOf(p1.thumb, 'image/jpeg');
ok(pd1.width === 1600 && pd1.height === 1200 && p1.main.length <= 1536 * 1024 && pt1.width === 320 && pt1.height === 240 && p1.thumb.length <= 64 * 1024,
  'روی سرور: اصل ۱۶۰۰×۱۲۰۰ زیرِ ۱٫۵MB و پیش‌نمایش ۳۲۰×۲۴۰ زیرِ ۶۴KB', `${JSON.stringify(pd1)} ${p1.main.length} / ${JSON.stringify(pt1)} ${p1.thumb.length}`);
ok(clean(p1.main) && clean(p1.thumb) && p1.m.meta.width === 1600 && p1.m.media.mime === 'image/jpeg', 'EXIF/eXIf/tEXt و «مکان» رفته؛ دکان JPEG ِ تازه را می‌بیند');
await page.waitForFunction(() => /^data:image\/jpeg;/.test((document.querySelector('#ct-list .ct-msg.mine img.ct-img') || {}).src || ''), null, { timeout: 10000 });
ok(await page.getAttribute('#ct-list .ct-msg.mine img.ct-img', 'alt') === 'عکسِ شما', 'حبابِ عکسِ مشتری با متنِ جایگزینِ «عکسِ شما»');

//  ۲. JPEG ِ خوابیده با جهتِ ۶ ⇒ عمودی و درست‌چرخیده روی سرور، بی EXIF؛ چرخشِ دستی (مرورگرِ بی چرخشِ خودکار) جدا سنجیده می‌شود
await attach(rotated, 'rotated.jpg', 'image/jpeg');
await page.click('#ct-ph-send');
ok(await until(async () => (await photos()) === 2), 'عکسِ چرخیده رفت');
const p2 = await lastPhoto();
const pd2 = chatMedia.dimsOf(p2.main, 'image/jpeg');
const [top2, bottom2] = await pixels(p2.main, [[0.5, 0.1], [0.5, 0.9]]);
ok(pd2.width === 208 && pd2.height === 416 && top2[0] > 150 && top2[2] < 100 && bottom2[2] > 150 && bottom2[0] < 100 && clean(p2.main),
  'جهتِ EXIF پیش از فشرده‌سازی: ۴۱۶×۲۰۸ ِ خوابیده ⇒ ۲۰۸×۴۱۶، لبهٔ چپ بالا؛ بی EXIF/XMP/COM', `${JSON.stringify(pd2)} ${top2} ${bottom2}`);
const manual = await page.evaluate((b64) => {
  const P = window.StoreChatPhoto;
  const src = document.createElement('canvas'); src.width = 4; src.height = 2;
  const x = src.getContext('2d'); x.fillStyle = '#f00'; x.fillRect(0, 0, 2, 2); x.fillStyle = '#00f'; x.fillRect(2, 0, 2, 2);
  const px = (c, a, b) => (c.getContext('2d').getImageData(a, b, 1, 1).data[0] > 128 ? 'r' : 'b');
  const out = {};
  for (const o of [1, 2, 3, 6, 8]) { const c = P.draw(src, 4, 2, o, 1600); out[o] = `${c.width}x${c.height}:${px(c, 0, 0)}${px(c, c.width - 1, c.height - 1)}`; }
  const bin = atob(b64); const u = new Uint8Array(bin.length);
  for (let i = 0; i < bin.length; i++) u[i] = bin.charCodeAt(i);
  out.exif = P.orientationOf(u);
  return out;
}, rotated.toString('base64'));
ok(JSON.stringify(manual) === JSON.stringify({ 1: '4x2:rb', 2: '4x2:br', 3: '4x2:br', 6: '2x4:rb', 8: '2x4:br', exif: 6 }),
  'چرخش/آینهٔ دستی (۲، ۳، ۶، ۸) و جهتِ ۶ از بایت‌های JPEG', JSON.stringify(manual));

//  ۳. «لغو» در میانِ بارگذاری ⇒ XHR بریده، هیچ پیامی نمی‌رود
const mainUp = (u) => u.pathname === `/api/store/${slug}/chat/conversations/${conv4.id}/media` && !u.search.includes('role=thumb');
const held4 = [];
await page.route(mainUp, (route) => { held4.push(route); });
await attach(clear, 'clear.png', 'image/png');
await page.click('#ct-ph-send');
ok(await until(async () => held4.length === 1, 10000), 'بارگذاریِ اصل در راه است (نگه داشته شد)');
ok(await page.isEnabled('#ct-ph-cancel') && await page.isVisible('#ct-ph-bar'), 'در میانِ بارگذاری: نوارِ پیشرفت پیدا و «لغو» زدنی');
await page.click('#ct-ph-cancel');
await page.waitForTimeout(1200);
const up3 = await page.evaluate(() => window.__up);
ok(up3.aborts === 1 && (await phState()) === 'gone' && (await photos()) === 2, '«لغو» ⇒ بارگذاری بریده شد و هیچ پیامی نرفت', JSON.stringify(up3));
await held4[0].abort().catch(() => { /* درخواستِ بریده */ });
await page.unroute(mainUp);

//  ۴. شبکه در میانه می‌افتد (سرور گرفت، جواب گم شد) — اول بارگذاریِ اصل، سپس خودِ پیام ⇒ «تلاشِ دوباره» ⇒ یک پیام، یک حباب
let dropUp = 1; let dropMsg = 1; let ups = 0; let posts = 0;
const msgPost = (u) => u.pathname === `/api/store/${slug}/chat/conversations/${conv4.id}/messages`;
await page.route(mainUp, async (route) => {
  ups++;
  if (dropUp-- < 1) return route.fallback();
  await route.fetch();
  return route.abort('failed');
});
await page.route(msgPost, async (route) => {
  if (route.request().method() !== 'POST') return route.fallback();
  posts++;
  if (dropMsg-- < 1) return route.fallback();
  await route.fetch();
  return route.abort('failed');
});
const rowsBefore = await mediaRows();
await attach(clear, 'clear.png', 'image/png');
await page.click('#ct-ph-send');
await page.waitForFunction(() => document.getElementById('ct-ph').getAttribute('data-st') === 'queued', null, { timeout: 15000 });
ok(/اینترنت/.test(await page.textContent('#ct-ph-st')) && await page.isVisible('#ct-ph-retry'), 'جوابِ بارگذاری گم شد ⇒ «در صف» و «تلاشِ دوباره»', await page.textContent('#ct-ph-st'));
await page.click('#ct-ph-retry');
await page.waitForFunction(() => document.getElementById('ct-ph').getAttribute('data-st') === 'queued' && document.getElementById('ct-ph').getAttribute('aria-busy') === null, null, { timeout: 15000 });
await page.click('#ct-ph-retry');
await page.waitForFunction(() => document.getElementById('ct-ph').hidden, null, { timeout: 15000 });
await page.waitForTimeout(1200);
ok(ups === 2 && posts === 2 && (await photos()) === 3 && (await mine()) === 3 && (await mediaRows()) === rowsBefore + 1,
  'تلاشِ دوباره پس از افتادنِ شبکه ⇒ همان بایت (همان رسانه) و همان clientId ⇒ یک پیام و یک حباب', `ups=${ups} posts=${posts} photos=${await photos()} bubbles=${await mine()} rows=${await mediaRows()}-${rowsBefore}`);
await page.unroute(mainUp);
await page.unroute(msgPost);
const p4 = await lastPhoto();
const [left4, right4] = await pixels(p4.main, [[0.1, 0.5], [0.9, 0.5]]);
ok(left4.every((c) => c > 235) && right4[1] > 120 && right4[0] < 80, 'شفافیت روی سفید', `${left4} ${right4}`);

//  ۵. بی‌اینترنت ⇒ همان صفِ متن: عکس می‌ماند و با وصل شدن خودش یک بار می‌رود
await ctx.setOffline(true);
await attach(small, 'small.jpg', 'image/jpeg');
await page.click('#ct-ph-send');
await page.waitForFunction(() => document.getElementById('ct-ph').getAttribute('data-st') === 'queued', null, { timeout: 15000 });
ok((await photos()) === 3, 'بی‌اینترنت: عکس «در صف» می‌ماند', await page.textContent('#ct-ph-st'));
await ctx.setOffline(false);
ok(await until(async () => (await photos()) === 4, 20000) && await until(async () => (await phState()) === 'gone'), 'وصل شد ⇒ عکس خودش رفت، بی زدنِ دوباره');
await page.waitForTimeout(800);
ok((await photos()) === 4 && (await mine()) === 4, 'پس از رسیدن: یک پیام و یک حباب');
await page.keyboard.press('Escape');

if (axeSrc) {
  //  گوشی (لمسی) ⇒ دکمهٔ دوربین هم؛ axe در دو حالت: «آماده» و «در صف» (نوار و «تلاشِ دوباره»)
  const ac = await browser.newContext({ viewport: { width: 360, height: 760 }, bypassCSP: true, hasTouch: true, isMobile: true });
  await ac.addInitScript((t) => { try { localStorage.setItem('store-ticket', JSON.stringify(t)); } catch { /* about:blank */ } },
    { phone: vc.body.phone, ticket: vc.body.ticket, expiresAt: vc.body.expiresAt });
  const ap = await ac.newPage();
  await ap.goto(`${base}/store/${slug}#/o/${encodeURIComponent(o4.token)}`);
  await ap.waitForSelector('#o-chat-open', { timeout: 15000 });
  await ap.click('#o-chat-open');
  await ap.waitForFunction(() => { const f = document.getElementById('ct-form'); return f && !f.hidden; }, null, { timeout: 15000 });
  const cam = await ap.getAttribute('#ct-ph-cam', 'aria-label').catch(() => null);
  ok(Boolean(cam), 'گوشیِ لمسی: دکمهٔ «عکس با دوربین»', String(cam));
  await ap.setInputFiles('#ct-file', { name: 'small.jpg', mimeType: 'image/jpeg', buffer: small });
  await ap.waitForFunction(() => document.getElementById('ct-ph').getAttribute('data-st') === 'ready', null, { timeout: 20000 });
  await ap.addScriptTag({ content: axeSrc });
  const rules = ['color-contrast', 'button-name', 'label', 'image-alt', 'aria-allowed-attr', 'aria-progressbar-name', 'nested-interactive', 'aria-allowed-role'];
  const run = () => ap.evaluate(async (r) => (await window.axe.run(document.getElementById('chat-sheet'), { runOnly: r })).violations
    .map((x) => `${x.id}: ${x.nodes.slice(0, 2).map((n) => n.target.join(' ')).join(' ; ')}`), rules);
  const vReady = await run();
  await ac.setOffline(true);
  await ap.click('#ct-ph-send');
  await ap.waitForFunction(() => document.getElementById('ct-ph').getAttribute('data-st') === 'queued', null, { timeout: 15000 });
  const vQueued = await run();
  ok(vReady.length === 0 && vQueued.length === 0, 'دسترس‌پذیری (axe-core): افزودنِ عکس، دوربین، پیش‌نمایش، نوارِ پیشرفت، «تلاشِ دوباره» و «لغو»', [...vReady, ...vQueued].join(' | '));
  await ac.close();
}

//  ── CHAT-07: مشتری پیامِ صوتی ضبط می‌کند — میکروفونِ ساختگیِ کرومیوم (`--use-fake-device-for-media-stream`) ──
//  پرچمِ خاموشِ `shop.chat.voice` ⇒ نه دکمه، نه اسکریپت؛ روشن ⇒ ضبط، «پایان و فرستادن»، پیش‌شنوایی پس از سقفِ زمان، «لغو»،
//  سقفِ حجم، رد شدنِ اجازه، بی‌اینترنت و افتادنِ جوابِ پیام با همان clientId؛ میکروفون پس از هر کدام بسته؛ axe؛ بی نقضِ CSP.
{
  const o5 = await checkout();
  await move(o5.id, ['accepted']);
  const conv5 = (await call('POST', '/api/shop/chat/conversations', { orderId: o5.id }, T)).body.conversation;
  const voices = async () => Number((await one(
    "SELECT COUNT(*)::int AS n FROM shop_chat_messages WHERE conversation_id=$1 AND sender='customer' AND kind='audio' AND deleted_at IS NULL", [conv5.id])).n);
  const audioRows = async () => Number((await one("SELECT COUNT(*)::int AS n FROM shop_chat_media WHERE conversation_id=$1 AND kind='audio'", [conv5.id])).n);
  const seed = { phone: vc.body.phone, ticket: vc.body.ticket, expiresAt: vc.body.expiresAt };
  const openChat = async (p) => {
    await p.goto(`${base}/store/${slug}#/o/${encodeURIComponent(o5.token)}`);
    await p.waitForSelector('#o-chat-open', { timeout: 15000 });
    await p.click('#o-chat-open');
    await p.waitForFunction(() => { const f = document.getElementById('ct-form'); return f && !f.hidden; }, null, { timeout: 15000 });
  };

  //  پرچمِ خاموش (پیش‌فرض) ⇒ دکمهٔ میکروفون پنهان و کدِ ضبط بار نمی‌شود
  await openChat(page);
  ok(!(await shown('ct-vc-mic')) && await page.$('script[src="/store/chat-voice.js"]') === null, 'پرچمِ shop.chat.voice خاموش ⇒ نه دکمهٔ میکروفون، نه chat-voice.js');
  await page.keyboard.press('Escape');
  await flag('shop.chat.voice', true);

  const vb = await chromium.launch({ executablePath: '/opt/pw-browsers/chromium/chrome-linux/chrome', args: ['--use-fake-device-for-media-stream', '--use-fake-ui-for-media-stream'] })
    .catch(() => chromium.launch({ args: ['--use-fake-device-for-media-stream', '--use-fake-ui-for-media-stream'] }));
  const vctx = await vb.newContext({ viewport: { width: 360, height: 760 }, locale: 'fa-AF' });
  await vctx.grantPermissions(['microphone'], { origin: base });
  await vctx.addInitScript((t) => {
    try { localStorage.setItem('store-ticket', JSON.stringify(t)); } catch { /* about:blank */ }
    //  شمارِ درخواستِ میکروفون و trackها — تا «بسته شد» از خودِ مرورگر سنجیده شود
    window.__mic = { calls: 0, tracks: [] };
    const md = navigator.mediaDevices;
    if (md && md.getUserMedia) {
      const real = md.getUserMedia.bind(md);
      md.getUserMedia = (c) => { window.__mic.calls++; return real(c).then((s) => { window.__mic.tracks.push(...s.getTracks()); return s; }); };
    }
  }, seed);
  const vp = await vctx.newPage();
  vp.on('pageerror', (e) => errors.push(`صدا: ${e}`));
  vp.on('console', (m) => { if (/Content Security Policy/i.test(m.text())) violations.push(m.text()); });
  let voiceUps = 0; let partGets = 0;
  vp.on('request', (r) => {
    if (r.method() === 'POST' && r.url().includes(`/chat/conversations/${conv5.id}/media`)) voiceUps++;
    if (r.url().includes('/kit/i18n/voice.')) partGets++;
  });
  const mic = () => vp.evaluate(() => ({ calls: window.__mic.calls, live: window.__mic.tracks.filter((k) => k.readyState === 'live').length }));
  const vst = () => vp.evaluate(() => { const b = document.getElementById('ct-vc'); return b && !b.hidden ? b.getAttribute('data-st') : 'gone'; });
  const said = () => vp.textContent('#ct-vc-say');
  const record = async () => {
    await vp.click('#ct-vc-mic');
    await vp.waitForFunction(() => { const b = document.getElementById('ct-vc'); return b && !b.hidden && b.getAttribute('data-st') === 'rec'; }, null, { timeout: 15000 });
  };

  await openChat(vp);
  const label = await vp.getAttribute('#ct-vc-mic', 'aria-label');
  ok(await vp.isVisible('#ct-vc-mic') && label === 'پیامِ صوتی', 'پرچمِ روشن ⇒ دکمهٔ میکروفون با نامِ «پیامِ صوتی»', String(label));
  ok(await vp.$('script[src="/store/chat-voice.js"]') === null && partGets === 0 && (await mic()).calls === 0,
    'پیش از زدن: نه chat-voice.js، نه واژه‌نامهٔ voice، نه درخواستِ میکروفون (دیربار)');

  //  ۱. ضبط ⇒ «پایان و فرستادن» ⇒ یک پیامِ صوتی روی سرور، میکروفون بسته، حبابِ «پخش»
  await record();
  ok(await vp.$('script[src="/store/chat-voice.js"]') !== null && partGets === 1, 'کدِ ضبط و واژه‌نامه‌اش فقط با زدنِ میکروفون آمدند');
  ok(/در حالِ ضبط/.test(await said()) && await vp.evaluate(() => document.activeElement && document.activeElement.id) === 'ct-vc-finish',
    'ضبط: ناحیهٔ زنده «در حالِ ضبط…» و تمرکز روی «پایان و فرستادن»', await said());
  ok((await mic()).live === 1, 'میکروفون فقط پس از زدن و فقط یک track');
  await vp.waitForTimeout(1600);
  const ticking = await vp.textContent('#ct-vc-time');
  await vp.click('#ct-vc-finish');
  ok(await until(async () => (await voices()) === 1, 20000) && await until(async () => (await vst()) === 'gone'), 'پایان و فرستادن ⇒ یک پیامِ صوتیِ مشتری روی سرور', ticking);
  ok((await mic()).live === 0, 'پس از پایان میکروفون بسته شد (هیچ track ِ زنده)');
  ok(/رفت/.test(await said()), 'ناحیهٔ زنده: «پیامِ صوتی رفت.»', await said());
  const sent1 = await one("SELECT m.meta, mm.mime, mm.size FROM shop_chat_messages m JOIN shop_chat_media mm ON mm.id = m.media_id WHERE m.conversation_id=$1 AND m.kind='audio'", [conv5.id]);
  ok(['audio/webm', 'audio/ogg', 'audio/mp4'].includes(sent1.mime) && Number(sent1.size) > 500 && sent1.meta.durationMs >= 1000 && sent1.meta.durationMs < 15000,
    'روی سرور: نوعِ صوتیِ شناخته از بایت، چند کیلوبایت و مدتِ واقعی', JSON.stringify(sent1));
  await vp.waitForSelector('#ct-list .ct-msg.mine .ct-voice', { timeout: 10000 });
  await vp.click('#ct-list .ct-msg.mine .ct-voice .ct-play');
  ok(await until(() => vp.evaluate(() => { const a = document.querySelector('#ct-list .ct-msg.mine .ct-voice audio'); return Boolean(a) && a.readyState >= 2 && /^blob:/.test(a.src); }), 10000),
    'پخشِ صدای خودِ مشتری از blob: (readyState ≥ ۲)');
  await vp.evaluate(() => { const a = document.querySelector('#ct-list .ct-msg.mine .ct-voice audio'); if (a) a.pause(); });

  //  ۲. سقفِ ۵ دقیقه ⇒ ضبط می‌ایستد و ردیفِ «فرستادن/لغو» با پیش‌شنوایی؛ «فرستادن» ⇒ پیامِ دوم با همان مدتِ ثبت‌شده
  await record();
  await vp.waitForTimeout(1200);
  await vp.evaluate(() => { const real = Date.now; window.__realNow = real; Date.now = () => real() + 5 * 60 * 1000 + 500; });
  await vp.waitForFunction(() => document.getElementById('ct-vc').getAttribute('data-st') === 'ready', null, { timeout: 10000 });
  await vp.evaluate(() => { Date.now = window.__realNow; });
  ok(/۵ دقیقه/.test(await vp.textContent('#ct-vc-st')) && await vp.isVisible('#ct-vc-send') && await vp.isVisible('#ct-vc-au') &&
    /^blob:/.test(await vp.getAttribute('#ct-vc-au', 'src')) && (await mic()).live === 0,
  'سقفِ ۵ دقیقه ⇒ ایستاد، میکروفون بسته، پیش‌شنوایی از blob: و «فرستادن»', await vp.textContent('#ct-vc-st'));
  ok(await vp.evaluate(() => document.activeElement && document.activeElement.id) === 'ct-vc-send', 'تمرکز روی «فرستادن»');
  await vp.click('#ct-vc-send');
  ok(await until(async () => (await voices()) === 2, 20000), 'پیش‌شنوایی ⇒ «فرستادن» ⇒ پیامِ دوم');
  const long = await one("SELECT meta FROM shop_chat_messages WHERE conversation_id=$1 AND kind='audio' ORDER BY seq DESC LIMIT 1", [conv5.id]);
  ok(long.meta.durationMs >= 300000 && long.meta.durationMs <= 15 * 60 * 1000, 'مدتِ ثبت‌شده همان زمانِ ضبط (سرور ≤ ۱۵ دقیقه)', JSON.stringify(long.meta));

  //  ۳. «لغو» در میانِ ضبط ⇒ هیچ بارگذاری، هیچ پیام، میکروفون بسته؛ بستنِ برگه در میانِ ضبط هم
  const upsBefore = voiceUps;
  await record();
  await vp.waitForTimeout(800);
  await vp.click('#ct-vc-cancel');
  await vp.waitForTimeout(600);
  ok((await vst()) === 'gone' && voiceUps === upsBefore && (await voices()) === 2 && (await mic()).live === 0 && /لغو/.test(await said()),
    '«لغو» ⇒ نه بارگذاری، نه پیام؛ میکروفون بسته و «ضبط لغو شد.»', `ups=${voiceUps - upsBefore}`);
  await record();
  await vp.keyboard.press('Escape');
  ok(await until(async () => (await mic()).live === 0, 5000) && voiceUps === upsBefore && (await voices()) === 2, 'بستنِ برگه در میانِ ضبط ⇒ میکروفون بسته و چیزی نرفت');

  //  ۴. سقفِ حجم (سقفِ کوچکِ ساختگی در پاسخِ گشودن) ⇒ ضبط خودش می‌ایستد؛ بزرگ‌تر از سقف ⇒ بی «فرستادن»، فقط «لغو»
  const openRoute = (u) => u.pathname === `/api/store/${slug}/chat/conversations`;
  await vp.route(openRoute, async (route) => {
    if (route.request().method() !== 'POST') return route.fallback();
    const res = await route.fetch();
    const j = await res.json();
    j.limits.audioMax = 300;
    return route.fulfill({ response: res, json: j });
  });
  await openChat(vp);
  await record();
  await vp.waitForFunction(() => document.getElementById('ct-vc').getAttribute('data-st') === 'ready', null, { timeout: 10000 });
  ok(/بزرگ‌تر/.test(await vp.textContent('#ct-vc-st')) && !(await vp.isVisible('#ct-vc-send')) && await vp.isVisible('#ct-vc-cancel') && (await mic()).live === 0,
    'نزدیکِ سقفِ حجم ⇒ خودش ایستاد؛ بزرگ‌تر از سقف ⇒ پیامِ روشن، بی «فرستادن»', await vp.textContent('#ct-vc-st'));
  await vp.click('#ct-vc-cancel');
  ok(voiceUps === upsBefore, 'صدای بزرگ‌تر از سقف هیچ بارگذاری نکرد');
  await vp.unroute(openRoute);
  await vp.keyboard.press('Escape');

  //  ۵. افتادنِ جوابِ پیام (سرور گرفت) ⇒ «تلاشِ دوباره» ⇒ همان رسانه و همان clientId ⇒ یک پیام؛ سپس بی‌اینترنت ⇒ صف و رفتنِ خودکار
  await openChat(vp);
  let drop = 1;
  const msgRoute = (u) => u.pathname === `/api/store/${slug}/chat/conversations/${conv5.id}/messages`;
  await vp.route(msgRoute, async (route) => {
    if (route.request().method() !== 'POST' || drop-- < 1) return route.fallback();
    await route.fetch();
    return route.abort('failed');
  });
  const rows5 = await audioRows();
  await record();
  await vp.waitForTimeout(1200);
  await vp.click('#ct-vc-finish');
  await vp.waitForFunction(() => document.getElementById('ct-vc').getAttribute('data-st') === 'queued', null, { timeout: 15000 });
  ok(await vp.isVisible('#ct-vc-retry') && (await voices()) === 3, 'جوابِ پیام گم شد ⇒ «در صف» و «دوباره بفرست» (سرور پیام را دارد)', await vp.textContent('#ct-vc-st'));
  await vp.click('#ct-vc-retry');
  ok(await until(async () => (await vst()) === 'gone', 15000) && (await voices()) === 3 && (await audioRows()) === rows5 + 1,
    'تلاشِ دوباره ⇒ همان رسانه و همان clientId ⇒ یک پیام، یک ردیفِ رسانه', `voices=${await voices()} rows=${(await audioRows()) - rows5}`);
  await vp.unroute(msgRoute);
  await record();
  await vp.waitForTimeout(1200);
  await vctx.setOffline(true);
  await vp.click('#ct-vc-finish');
  await vp.waitForFunction(() => document.getElementById('ct-vc').getAttribute('data-st') === 'queued', null, { timeout: 15000 });
  ok((await voices()) === 3 && (await mic()).live === 0 && /اینترنت/.test(await vp.textContent('#ct-vc-st')), 'بی‌اینترنت ⇒ صدا در صف می‌ماند و میکروفون بسته', await vp.textContent('#ct-vc-st'));
  await vctx.setOffline(false);
  ok(await until(async () => (await voices()) === 4, 25000) && await until(async () => (await vst()) === 'gone'), 'وصل شد ⇒ صدا خودش رفت، یک بار');
  await vp.keyboard.press('Escape');
  await vctx.close();

  //  ۶. اجازهٔ میکروفون رد شد / مرورگرِ بی MediaRecorder ⇒ پیامِ روشن یا بی دکمه؛ هیچ بارگذاری
  const dctx = await vb.newContext({ viewport: { width: 360, height: 760 }, locale: 'fa-AF' });
  await dctx.addInitScript((t) => {
    try { localStorage.setItem('store-ticket', JSON.stringify(t)); } catch { /* about:blank */ }
    if (navigator.mediaDevices) navigator.mediaDevices.getUserMedia = () => Promise.reject(new DOMException('denied', 'NotAllowedError'));
  }, seed);
  const dp = await dctx.newPage();
  dp.on('pageerror', (e) => errors.push(`صدا (رد): ${e}`));
  dp.on('console', (m) => { if (/Content Security Policy/i.test(m.text())) violations.push(m.text()); });
  await openChat(dp);
  await dp.click('#ct-vc-mic');
  await dp.waitForFunction(() => { const b = document.getElementById('ct-vc'); return b && !b.hidden && b.getAttribute('data-st') === 'err'; }, null, { timeout: 15000 });
  ok(/اجازهٔ میکروفون/.test(await dp.textContent('#ct-vc-st')) && !(await dp.isVisible('#ct-vc-finish')) && await dp.isVisible('#ct-vc-cancel'),
    'اجازه رد شد ⇒ «اجازهٔ میکروفون داده نشد…» و فقط «لغو»', await dp.textContent('#ct-vc-st'));
  await dp.click('#ct-vc-cancel');
  ok(!(await dp.isVisible('#ct-vc')) && (await voices()) === 4, '«لغو» ⇒ کادرِ صدا بسته، چیزی نرفت');
  await dctx.close();
  const nctx = await vb.newContext({ viewport: { width: 360, height: 760 }, locale: 'fa-AF' });
  await nctx.addInitScript((t) => { try { localStorage.setItem('store-ticket', JSON.stringify(t)); } catch { /* about:blank */ } delete window.MediaRecorder; }, seed);
  const np = await nctx.newPage();
  await openChat(np);
  ok(!(await np.isVisible('#ct-vc-mic')), 'مرورگرِ بی MediaRecorder ⇒ بی دکمهٔ میکروفون (نه دکمهٔ مرده)');
  await nctx.close();

  //  ۷. دسترس‌پذیری (axe-core) ِ کادرِ صدا: در حالِ ضبط و پیش‌شنوایی
  if (axeSrc) {
    const actx = await vb.newContext({ viewport: { width: 360, height: 760 }, bypassCSP: true });
    await actx.grantPermissions(['microphone'], { origin: base });
    await actx.addInitScript((t) => { try { localStorage.setItem('store-ticket', JSON.stringify(t)); } catch { /* about:blank */ } }, seed);
    const ap = await actx.newPage();
    await openChat(ap);
    await ap.click('#ct-vc-mic');
    await ap.waitForFunction(() => document.getElementById('ct-vc') && document.getElementById('ct-vc').getAttribute('data-st') === 'rec', null, { timeout: 15000 });
    await ap.addScriptTag({ content: axeSrc });
    const rules = ['color-contrast', 'button-name', 'label', 'aria-allowed-attr', 'aria-progressbar-name', 'nested-interactive', 'aria-allowed-role'];
    const run = () => ap.evaluate(async (r) => (await window.axe.run(document.getElementById('chat-sheet'), { runOnly: r })).violations
      .map((x) => `${x.id}: ${x.nodes.slice(0, 2).map((n) => n.target.join(' ')).join(' ; ')}`), rules);
    const vRec = await run();
    await ap.waitForTimeout(1200);
    await ap.evaluate(() => { const real = Date.now; Date.now = () => real() + 5 * 60 * 1000 + 500; });
    await ap.waitForFunction(() => document.getElementById('ct-vc').getAttribute('data-st') === 'ready', null, { timeout: 10000 });
    const vReady = await run();
    ok(vRec.length === 0 && vReady.length === 0, 'دسترس‌پذیری (axe-core): میکروفون، ضبط، نوارِ مدت، پیش‌شنوایی، «فرستادن» و «لغو»', [...vRec, ...vReady].join(' | '));
    await actx.close();
  }
  await vb.close();
}

//  ── پرچمِ خاموش ⇒ همان ویترینِ پیشین ──
await flag('shop.chat', false);
await page.goto('about:blank');   //  بارِ تازهٔ صفحه ⇒ هیچ اسکریپتِ پیشین نمی‌ماند
await page.goto(`${base}/store/${slug}#/o/${encodeURIComponent(o2.token)}`);
await page.waitForFunction(() => document.querySelectorAll('#o-lines li').length > 0, null, { timeout: 15000 });
ok(!(await shown('store-chat')) && await page.$('#o-chat') === null && await page.$('script[src="/store/chat.js"]') === null &&
  await page.$('script[src="/store/chat-out.js"]') === null && await page.$('script[src="/store/chat-acts.js"]') === null, 'پرچمِ خاموش ⇒ نه دکمه، نه کارت، نه اسکریپت');

ok(violations.length === 0, 'هیچ نقضِ CSP', violations.join(' | '));
ok(errors.length === 0, 'بی خطای جاوااسکریپت', errors.join(' | '));

await ctx.close();
await browser.close();
server.close();
await closeDb();
console.log(bad ? `❌ ${bad} ایراد` : '✅ ویترین: گفت‌وگوی هر سفارش، «بسته شد»، «مشکل دارم» و گفت‌وگوی عمومی');
process.exit(bad ? 1 : 0);
