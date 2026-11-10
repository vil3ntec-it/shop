'use strict';
/**
 * S117 — هیچ CVV، PIN ِ کارت یا شمارهٔ کاملِ کارت در دیتابیس، گزارشِ خطا (فایلِ crash) و گزارشِ پشتیبانی نمی‌نشیند.
 *
 *   ۱) هیچ فرم، ستون یا کلیدی برای شمارهٔ کارت، CVV یا رمزِ کارت نیست: پرداخت فقط از صفحهٔ ارائه‌دهنده
 *      است و برنامه‌ها و سرور دادهٔ کارت نمی‌گیرند (نگهبان روی مهاجرت‌ها، سرور، وب‌ها و برنامه‌ها).
 *   ۲) اگر شمارهٔ کارت در متنِ خطا یا لاگِ برنامه بیاید (مثلاً کاربر آن را در کادری نوشت)، گزارشِ خطا
 *      (`POST /api/errors`) و پوشاننده‌ی پنل (`ops-errors.scrub`) آن را «[کارت]» می‌کنند — فقط رشته‌ی
 *      ۱۵ تا ۱۹ رقمی با رقمِ کنترلِ Luhn؛ بارکد، زمان و شناسه‌ی دیگر دست نمی‌خورند.
 */
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const h = require('./helpers');
const { one } = require('../src/db');
const { maskCards, scrub } = require('../src/lib/ops-errors');

test.before(async () => { await h.start(); });
test.after(async () => { await h.stop(); });

const VISA = '4111111111111111';
const AMEX = '378282246310005';

test('S117: شمارهٔ کاملِ کارت (Luhn، ۱۵ تا ۱۹ رقم، با فاصله یا خط) پوشانده می‌شود؛ بارکد و شناسهٔ دیگر نه', () => {
  assert.equal(maskCards(`کارت ${VISA} بود`), 'کارت [کارت] بود');
  assert.equal(maskCards('4111 1111 1111 1111'), '[کارت]');
  assert.equal(maskCards('4111-1111-1111-1111'), '[کارت]');
  assert.equal(maskCards(`x${AMEX}y`), 'x[کارت]y');
  assert.equal(maskCards('6011000990139424'), '[کارت]');
  //  دست نمی‌خورند: Luhn ِ نادرست، بارکدِ EAN-13، زمانِ میلی‌ثانیه، رشتهٔ رقمیِ بلندتر از کارت
  for (const keep of ['4111111111111112', '6261234567890', '1730000000000', '41111111111111111111', '12345']) {
    assert.equal(maskCards(keep), keep, keep);
  }
  assert.equal(scrub(`خطا برای ${VISA} و ali@example.com`), 'خطا برای [کارت] و [ایمیل]');
});

test('S117: گزارشِ خطای برنامه شمارهٔ کارت را در پیام، stack و لاگ نگه نمی‌دارد', async () => {
  const r = await h.api('POST', '/api/errors', {
    headers: { 'X-App': 'shop', 'X-Device': 'dev-card-1', 'X-App-Version': '2.14.5' },
    body: { message: `NumberFormatException: "${VISA}"`, stack: `at Pay.parse(${AMEX})`, log_tail: 'note=4111 1111 1111 1111' },
  });
  assert.equal(r.status, 202, JSON.stringify(r.body));
  const row = await one('SELECT message, stack, log_tail FROM client_errors WHERE id = $1', [r.body.id]);
  assert.ok(row);
  const all = `${row.message}\n${row.stack}\n${row.log_tail}`;
  assert.ok(!/\d{4}[ -]?\d{4}[ -]?\d{4}/.test(all), all);
  assert.equal(row.message, 'NumberFormatException: "[کارت]"');
  assert.equal(row.log_tail, 'note=[کارت]');
});

const CARD_FIELD = /card_?(number|no|num)\b|cardNumber|\bpan\b|\bcvv2?\b|\bcvc\b|card_?pin|شمار[هۀ]ٔ?\s?کارت|رمز[ِ\s]+دوم/i;
//  استثنای دقیق (پرونده + همهٔ سطر)، نه الگوی سست‌تر: کدِ ISO 3166-2:AF ِ ولایتِ پنجشیر (`AF-PAN`) در فهرستِ ولایت‌های
//  S109 نامِ ستون یا کلیدِ کارت نیست و همان کدی است که مهاجرتِ ۲۰۶ نگه می‌دارد. هر سطرِ دیگری با `pan` (و همین سطر اگر
//  عوض شود) همچنان سرخ است، و استثنای بی‌کار هم سرخ است.
const CARD_FIELD_EXACT_OK = new Set([
  "server/src/lib/af-provinces.js|['PAN', 'پنجشیر', ['Panjshir', 'پنجشیر']],",
]);
const EXT = new Set(['.js', '.mjs', '.cjs', '.html', '.json', '.sql', '.kt', '.kts']);
const SKIP_DIR = new Set(['node_modules', 'vendor', 'build', '.gradle', 'test', 'tests']);
function walk(dir, out) {
  for (const e of fs.readdirSync(dir, { withFileTypes: true })) {
    if (e.isDirectory()) { if (!SKIP_DIR.has(e.name)) walk(path.join(dir, e.name), out); }
    else if (EXT.has(path.extname(e.name)) && !e.name.endsWith('.min.js')) out.push(path.join(dir, e.name));
  }
  return out;
}

test('S117: هیچ ستون، کلید یا کادری برای شمارهٔ کارت، CVV یا رمزِ کارت نیست (مهاجرت‌ها، سرور، وب‌ها، برنامه‌ها)', () => {
  const root = path.join(__dirname, '..', '..');
  const files = [];
  for (const r of ['index.html', 'server/src', 'server/public', 'server/migrations', 'android-native/app/src/main', 'desktop/src/main', 'core/src/commonMain']) {
    const abs = path.join(root, r);
    assert.ok(fs.existsSync(abs), `پوشهٔ ${r} نیست`);
    if (fs.statSync(abs).isDirectory()) walk(abs, files); else files.push(abs);
  }
  assert.ok(files.length > 300, `فقط ${files.length} پرونده`);
  const hits = [];
  const usedOk = new Set();
  for (const f of files) {
    const rel = path.relative(root, f).split(path.sep).join('/');
    fs.readFileSync(f, 'utf8').split('\n').forEach((line, i) => {
      if (!CARD_FIELD.test(line)) return;
      const k = `${rel}|${line.trim()}`;
      if (CARD_FIELD_EXACT_OK.has(k)) { usedOk.add(k); return; }
      hits.push(`${rel}:${i + 1}: ${line.trim().slice(0, 120)}`);
    });
  }
  assert.deepEqual(hits, [], 'دادهٔ کارت فقط در صفحهٔ ارائه‌دهنده گرفته می‌شود (S117)');
  assert.deepEqual([...CARD_FIELD_EXACT_OK].filter((k) => !usedOk.has(k)), [], 'استثنای بی‌کار در CARD_FIELD_EXACT_OK');
});

/* ── متنِ آزادِ کاربر: گفت‌وگوی پشتیبانی و یادداشتِ درخواستِ خرید ─────────────────────────────── */

test('S117: پیامِ پشتیبانیِ دکان (کاربر و جوابِ مدیر) بی شمارهٔ کاملِ کارت ذخیره می‌شود؛ رشتهٔ پمپ دست نمی‌خورد', async () => {
  const { query, newId, now } = require('../src/db');
  const support = require('../src/lib/support');
  const pw = require('../src/lib/password');
  await query(
    `INSERT INTO admins (id, username, name, password_hash, role, status, created_at)
     VALUES ($1,'card-admin','پشتیبان',$2,'superadmin','active',$3)`,
    [newId('adm'), await pw.hashPassword('Admin!12345'), now()]
  );

  //  کاربرِ دکان (مهمان با شناسهٔ دستگاه) کارت را در متن می‌نویسد
  const sent = await h.post('/api/support/messages', {
    deviceUid: 'card-guest-1', body: `کارتم ${VISA} است و رمزِ ۴۴۴۴ — شمارهٔ فاکتور 6261234567890`,
  });
  assert.equal(sent.status, 201, JSON.stringify(sent.body));
  assert.equal(sent.body.message.body, 'کارتم [کارت] است و رمزِ ۴۴۴۴ — شمارهٔ فاکتور 6261234567890');
  const row = await one('SELECT m.body, t.last_message, t.id AS thread_id FROM support_messages m JOIN support_threads t ON t.id = m.thread_id WHERE m.id = $1', [sent.body.message.id]);
  assert.ok(!row.body.includes(VISA) && !row.last_message.includes(VISA), JSON.stringify(row));
  assert.ok(row.last_message.includes('[کارت]'));

  //  جوابِ مدیر در همان رشته (شکلِ فاصله‌دار)
  const login = await h.post('/api/admin/login', { username: 'card-admin', password: 'Admin!12345' });
  const replied = await h.post(`/api/admin/support/threads/${row.thread_id}/messages`,
    { body: `شمارهٔ ${AMEX.slice(0, 4)} ${AMEX.slice(4, 10)} ${AMEX.slice(10)} را ننویسید` }, { token: login.body.token });
  assert.equal(replied.status, 201, JSON.stringify(replied.body));
  assert.equal(replied.body.message.body, 'شمارهٔ [کارت] را ننویسید');
  const back = await h.get('/api/support/thread?deviceUid=card-guest-1');
  const seen = back.body.messages.map((m) => m.body.replace(/[ -]/g, '')).join('\n');
  assert.ok(!seen.includes(VISA) && !seen.includes(AMEX), seen);

  //  ⛔ رشتهٔ پمپ (کدِ مشترک با پروژهٔ پمپ) همان رفتارِ پیشین
  const pump = await support.threadFor({ app: 'pump', userId: '', shopId: '', deviceUid: 'card-pump-1', who: 'پمپ', contact: '' });
  const pm = await support.post(pump.id, { sender: 'user', body: `کارت ${VISA}` });
  assert.equal((await one('SELECT body FROM support_messages WHERE id = $1', [pm.id])).body, `کارت ${VISA}`);
});

test('S117: یادداشتِ درخواستِ خرید بی شمارهٔ کاملِ کارت ذخیره می‌شود', async () => {
  const u = await h.newUser('کارت‌نویس');
  const shop = await h.post('/api/shop', { name: 'دکانِ کارت' }, { token: u.accessToken });
  assert.ok(shop.status < 300, JSON.stringify(shop.body));
  const r = await h.post('/api/v1/me/purchase-request', { plan: 'm6', note: `از کارتِ ${VISA.replace(/(\d{4})(?=\d)/g, '$1-')} پرداختم` }, { token: u.accessToken });
  assert.equal(r.status, 201, JSON.stringify(r.body));
  const row = await one('SELECT note FROM purchase_requests WHERE id = $1', [r.body.request.id]);
  assert.equal(row.note, 'از کارتِ [کارت] پرداختم');
});

test('S117: گفت‌وگوی مشتری ↔ دکان (پیام، ویرایش، پیش‌نمایش، پاسخِ دکان و گزارش) بی شمارهٔ کاملِ کارت ذخیره می‌شود', async () => {
  const { query, newId, now } = require('../src/db');
  const chat = require('../src/lib/shop-chat');
  const pw = require('../src/lib/password');
  await query(
    `INSERT INTO admins (id, username, name, password_hash, role, status, created_at)
     VALUES ($1,'card-chat-admin','مدیر',$2,'superadmin','active',$3)`,
    [newId('adm'), await pw.hashPassword('Admin!12345'), now()]
  );
  const admin = (await h.post('/api/admin/login', { username: 'card-chat-admin', password: 'Admin!12345' })).body.token;
  const u = await h.newUser('دکان‌دارِ چت', 'shop');
  const made = await h.post('/api/shop', { name: 'دکانِ چتِ کارت' }, { token: u.accessToken });
  assert.equal(made.status, 201, JSON.stringify(made.body));
  const shopId = made.body.shop.id;
  const flag = await h.put('/api/admin/pump/live-config', { scope: shopId, key: 'shop.chat', value: true }, { token: admin });
  assert.equal(flag.status, 200, JSON.stringify(flag.body));

  const me = { side: 'customer', shopId, phone: '+93791117117' };
  const conv = (await chat.open(me, { name: 'مشتری' })).conversation.id;
  const raw = `کارتم ${VISA} است؛ کدِ کالا 6261234567890`;
  const sent = await chat.send(me, conv, { clientId: 'client-card-0001', body: raw });
  assert.equal(sent.message.body, 'کارتم [کارت] است؛ کدِ کالا 6261234567890');
  //  ارسالِ دوبارهٔ همان پیام (صفِ آفلاین) همان ردیف است، نه «کلیدِ تکراری با بدنهٔ دیگر»
  const again = await chat.send(me, conv, { clientId: 'client-card-0001', body: raw });
  assert.equal(again.replayed, true);
  assert.equal(again.message.id, sent.message.id);

  const edited = await chat.edit(me, conv, sent.message.id, { body: `نه، این: ${VISA.replace(/(\d{4})(?=\d)/g, '$1-')}` });
  assert.ok(edited, 'ویرایش پذیرفته شد');
  const reply = await h.post(`/api/shop/chat/conversations/${conv}/messages`,
    { clientId: 'client-card-0002', body: `شمارهٔ ${AMEX.slice(0, 4)} ${AMEX.slice(4, 10)} ${AMEX.slice(10)} را این‌جا ننویسید` },
    { token: u.accessToken });
  assert.equal(reply.status, 201, JSON.stringify(reply.body));
  assert.equal(reply.body.message.body, 'شمارهٔ [کارت] را این‌جا ننویسید');
  await chat.report(me, conv, { reason: 'fraud', messageId: reply.body.message.id, note: `از من کارتِ ${AMEX} خواست (فاکتور 6261234567890)` });

  const inbox = await h.get('/api/shop/chat/conversations', { token: u.accessToken });
  assert.equal(inbox.status, 200, JSON.stringify(inbox.body));
  const stored = [
    ...(await require('../src/db').many('SELECT body FROM shop_chat_messages WHERE shop_id = $1', [shopId])).map((r) => r.body),
    ...(await require('../src/db').many('SELECT note, snapshot::text AS snap FROM shop_chat_reports WHERE shop_id = $1', [shopId]))
      .flatMap((r) => [r.note, r.snap]),
    JSON.stringify(inbox.body.conversations.map((c) => c.lastMessage)),
  ].join('\n').replace(/[ -]/g, '');
  assert.ok(!stored.includes(VISA) && !stored.includes(AMEX), stored);
  assert.ok(stored.includes('[کارت]'));
  assert.ok(stored.includes('6261234567890'), 'بارکد دست نخورد');
});

test('S117: یادداشتِ سفارش (ویترین و کارمند، هر قلم) و نظرِ مشتری بی شمارهٔ کاملِ کارت', () => {
  const storefront = require('../src/lib/storefront');
  const orders = require('../src/lib/orders');
  const reviews = require('../src/lib/order-reviews');
  const dashed = VISA.replace(/(\d{4})(?=\d)/g, '$1 ');
  const sf = { accept_delivery: true, accept_pickup: true };
  const c = storefront.parseCheckout({
    fulfillment: 'pickup', customer: { name: 'مشتری', phone: '0791117117' }, note: `کارت ${VISA} برای پیش‌پرداخت`,
    lines: [{ productId: 'p-card-1', qtyMilli: 1000, note: `روی بسته ${dashed} ننویسید` }],
  }, sf);
  assert.equal(c.note, 'کارت [کارت] برای پیش‌پرداخت');
  assert.equal(c.lines[0].note, 'روی بسته [کارت] ننویسید');

  const o = orders.parseCreate({
    source: 'phone', fulfillment: 'pickup', customer: { name: 'مشتری', phone: '0791117117' }, note: `کارتِ ${AMEX}`,
    lines: [{ name: 'برنج', qtyMilli: 1000, unitPriceMinor: 100, substitution: '', note: `${VISA} و بارکد 6261234567890` }],
  }, Date.now());
  assert.equal(o.note, 'کارتِ [کارت]');
  assert.equal(o.lines[0].note, '[کارت] و بارکد 6261234567890');

  const r = reviews.parse({ stars: 5, comment: `خوب بود؛ با کارتِ ${dashed} پرداختم` });
  assert.equal(r.comment, 'خوب بود؛ با کارتِ [کارت] پرداختم');
  //  همان نوشته ⇒ همان هش (تکرارِ همان نظر «تکراری» می‌ماند، نه «بدنهٔ دیگر»)
  assert.equal(reviews.parse({ stars: 5, comment: `خوب بود؛ با کارتِ ${dashed} پرداختم` }).hash, r.hash);
});
