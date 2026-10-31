'use strict';
/**
 * چتِ مشتری ↔ دکان — هستهٔ سرور (v3 §۳۳؛ سناریوهای CHAT-xx).
 *
 * آن‌چه این پرونده قفل می‌کند:
 *   ۰) ⛔ پیش‌فرض خاموش: بی `shop.chat` همهٔ درها ۴۰۴ و هیچ ردیفی ساخته نمی‌شود.
 *   CHAT-01  متن یک بار ثبت و یک بار در طرفِ دیگر دیده می‌شود.
 *   CHAT-02  صفِ آفلاین با تکرار: ترتیبِ درست، بی پیامِ دوم؛ همان شناسه با متنِ دیگر ⇒ ۴۰۹.
 *   CHAT-03  برنامهٔ بسته‌شده همه‌چیز را از سرور (after=0) می‌گیرد؛ long-poll پیامِ تازه را زود می‌رساند.
 *   CHAT-04  «رسید/خوانده» فقط با اعلامِ گیرنده؛ خواندنِ فهرست چیزی را عوض نمی‌کند؛ عقب نمی‌رود.
 *   CHAT-05/06  عکس: نوع از بایت، EXIF ِ مکان/XMP/توضیح پاک، جهت می‌ماند؛ پیش‌نمایش؛ بایت‌ها سالم.
 *   CHAT-08  حذف برای من فقط در نمای همان؛ حذف برای همه سنگِ قبر به دستگاهِ آفلاین می‌رسد و رسانه می‌رود.
 *   CHAT-09  دکان/مشتریِ دیگر با عوض کردنِ شناسه به گفت‌وگو، پیام یا رسانه نمی‌رسد.
 *   CHAT-10  فایلِ اجرایی، MIME ِ جعلی، SVG و فایلِ بزرگ رد می‌شوند و سرویس سرپا می‌ماند.
 *   CHAT-11  تکرارِ ارسال فقط یک پوش (بی متن) می‌سازد.
 *   CHAT-14  جست‌وجو/پاسخ/ویرایش فقط در محدودهٔ مجاز؛ پاک‌شده و پنهان در جست‌وجو نمی‌آیند.
 *   §۳۳.۳    مشتری شناسهٔ کارمند نمی‌بیند؛ شمارهٔ کامل فقط با `orders.contact`؛ بلاک/گزارش؛ پاک‌سازیِ یتیم.
 *   درِ مشتری (`/api/store/:slug/chat`) با بلیتِ ویترین — اگر ماژولِ ویترین روی سرور هست.
 */
const test = require('node:test');
const assert = require('node:assert/strict');
const h = require('./helpers');
const { query, one, newId, now } = require('../src/db');
const chat = require('../src/lib/shop-chat');
const chatMedia = require('../src/lib/chat-media');
const push = require('../src/lib/push');

let admin = '';
let seq = 0;
const uniq = () => `${Date.now().toString(36)}${++seq}`;
const cid = () => `client-${uniq()}-0000`;

test.before(async () => {
  await h.start();
  const pw = require('../src/lib/password');
  await query(
    `INSERT INTO admins (id, username, name, password_hash, role, status, created_at)
     VALUES ($1,'admin','مدیر',$2,'superadmin','active',$3)`,
    [newId('adm'), await pw.hashPassword('Admin!12345'), now()]
  );
  admin = (await h.post('/api/admin/login', { username: 'admin', password: 'Admin!12345' })).body.token;
  assert.ok(admin);
});
test.after(async () => { push.setDeliver(null); await h.stop(); });

async function setFlag(scope, key, value) {
  const r = await h.put('/api/admin/pump/live-config', { scope, key, value }, { token: admin });
  assert.equal(r.status, 200, JSON.stringify(r.body));
}

async function shopOwner(name) {
  const u = await h.newUser(name, 'shop');
  const made = await h.post('/api/shop', { name: `دکانِ ${name}` }, { token: u.accessToken });
  assert.equal(made.status, 201, JSON.stringify(made.body));
  return { ...u, token: u.accessToken, shopId: made.body.shop.id, userId: u.user.id };
}

async function member(shopId, role, name) {
  const u = await h.newUser(name, 'shop');
  await query(
    `INSERT INTO shop_members (id, shop_id, user_id, role, status, created_at, updated_at)
     VALUES ($1,$2,$3,$4,'active',$5,$5)`,
    [newId('mem'), shopId, u.user.id, role, now()]
  );
  return { ...u, token: u.accessToken, userId: u.user.id };
}

async function chatShop(name) {
  const o = await shopOwner(name);
  await setFlag(o.shopId, 'shop.chat', true);
  return o;
}

const customer = (shopId, phone = '+93791112233') => ({ side: 'customer', shopId, phone });
const count = async (table, shopId) => Number((await one(`SELECT COUNT(*)::int AS n FROM ${table} WHERE shop_id=$1`, [shopId])).n);
const base = '/api/shop/chat';
const msgs = (token, convId, after = 0, extra = '') => h.get(`${base}/conversations/${convId}/messages?after=${after}${extra}`, { token });
const sendHttp = (token, convId, body) => h.post(`${base}/conversations/${convId}/messages`, body, { token });

// ── بایت‌های آزمایشی ────────────────────────────────────────────────

function seg(marker, payload) {
  const head = Buffer.alloc(4);
  head.writeUInt16BE(0xff00 | marker, 0);
  head.writeUInt16BE(payload.length + 2, 2);
  return Buffer.concat([head, payload]);
}

/** JPEG با Exif (جهت = ۶ + GPS)، XMP و توضیح — دادهٔ تصویر ساختگی ولی ساختار درست. */
function jpegWithGps(orientation = 6) {
  const secret = Buffer.from('LATLONG-SECRET-1', 'latin1');
  const tiff = Buffer.alloc(56 + secret.length);
  tiff.write('MM', 0, 'latin1'); tiff.writeUInt16BE(42, 2); tiff.writeUInt32BE(8, 4);
  tiff.writeUInt16BE(2, 8);
  tiff.writeUInt16BE(0x0112, 10); tiff.writeUInt16BE(3, 12); tiff.writeUInt32BE(1, 14); tiff.writeUInt16BE(orientation, 18);
  tiff.writeUInt16BE(0x8825, 22); tiff.writeUInt16BE(4, 24); tiff.writeUInt32BE(1, 26); tiff.writeUInt32BE(38, 30);
  tiff.writeUInt32BE(0, 34);
  tiff.writeUInt16BE(1, 38);
  tiff.writeUInt16BE(0x001b, 40); tiff.writeUInt16BE(7, 42); tiff.writeUInt32BE(secret.length, 44); tiff.writeUInt32BE(56, 48);
  tiff.writeUInt32BE(0, 52);
  secret.copy(tiff, 56);
  const jfif = Buffer.from([0x4a, 0x46, 0x49, 0x46, 0, 1, 1, 0, 0, 1, 0, 1, 0, 0]);
  return Buffer.concat([
    Buffer.from([0xff, 0xd8]),
    seg(0xe0, jfif),
    seg(0xe1, Buffer.concat([Buffer.from('Exif\0\0', 'latin1'), tiff])),
    seg(0xe1, Buffer.from('http://ns.adobe.com/xap/1.0/\0<x:xmpmeta>GPS-XMP-SECRET</x:xmpmeta>', 'latin1')),
    seg(0xfe, Buffer.from('comment-secret', 'latin1')),
    seg(0xdb, Buffer.alloc(65, 1)),
    seg(0xda, Buffer.from([1, 1, 0, 0, 0x3f, 0])),
    Buffer.alloc(200, 0x55),
    Buffer.from([0xff, 0xd9]),
  ]);
}

function pngWithText() {
  const chunk = (type, data) => {
    const len = Buffer.alloc(4); len.writeUInt32BE(data.length, 0);
    return Buffer.concat([len, Buffer.from(type, 'latin1'), data, Buffer.alloc(4)]);
  };
  return Buffer.concat([
    Buffer.from([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a]),
    chunk('IHDR', Buffer.alloc(13, 1)),
    chunk('tEXt', Buffer.from('Comment\0png-secret', 'latin1')),
    chunk('eXIf', Buffer.from('MM\0*exif-secret', 'latin1')),
    chunk('IDAT', Buffer.alloc(120, 7)),
    chunk('IEND', Buffer.alloc(0)),
  ]);
}

function webpWithExif() {
  const chunk = (type, data) => {
    const len = Buffer.alloc(4); len.writeUInt32LE(data.length, 0);
    return Buffer.concat([Buffer.from(type, 'latin1'), len, data, data.length & 1 ? Buffer.alloc(1) : Buffer.alloc(0)]);
  };
  const vp8x = Buffer.alloc(10); vp8x[0] = 0x08 | 0x04 | 0x10;
  const body = Buffer.concat([chunk('VP8X', vp8x), chunk('VP8 ', Buffer.alloc(40, 9)), chunk('EXIF', Buffer.from('webp-secret', 'latin1')), chunk('XMP ', Buffer.from('xmp-secret', 'latin1'))]);
  const head = Buffer.alloc(12); head.write('RIFF', 0, 'latin1'); head.writeUInt32LE(body.length + 4, 4); head.write('WEBP', 8, 'latin1');
  return Buffer.concat([head, body]);
}

const ogg = () => Buffer.concat([Buffer.from('OggS', 'latin1'), Buffer.alloc(300, 3)]);
const smallJpeg = (fill = 1) => Buffer.concat([Buffer.from([0xff, 0xd8]), seg(0xdb, Buffer.alloc(65, fill)), seg(0xda, Buffer.from([1, 1, 0, 0, 0x3f, 0])), Buffer.alloc(100, fill), Buffer.from([0xff, 0xd9])]);
const upload = (token, convId, buf, { role = '', type = 'application/octet-stream' } = {}) =>
  h.raw('POST', `${base}/conversations/${convId}/media${role ? `?role=${role}` : ''}`, buf, { token, headers: { 'Content-Type': type } });

// ════════════════════════════════════════════════════════════════════

test('۰) پیش‌فرض خاموش: بی پرچمِ shop.chat همهٔ درها ۴۰۴ و هیچ ردیفی', async () => {
  const o = await shopOwner('خاموش');
  const cfg = await h.get(`${base}/config`, { token: o.token });
  assert.equal(cfg.status, 200);
  assert.equal(cfg.body.enabled, false);
  for (const [method, path, body] of [
    ['GET', `${base}/conversations`],
    ['POST', `${base}/conversations`, { orderId: 'x' }],
    ['GET', `${base}/search?q=سلام`],
    ['GET', `${base}/conversations/sch_x/messages`],
    ['POST', `${base}/conversations/sch_x/messages`, { clientId: cid(), body: 'سلام' }],
  ]) {
    const r = await h.api(method, path, { token: o.token, body: body || null });
    assert.equal(r.status, 404, `${method} ${path}`);
    assert.equal(r.body.error.code, 'chat_disabled');
  }
  assert.equal(await chat.flagOf(o.shopId), false);
  assert.equal(await count('shop_chat_conversations', o.shopId), 0);
  //  درِ مشتری بی ویترینِ روشن: همان «ویترین نیست»
  const pub = await h.get('/api/store/no-such-store/chat/conversations');
  assert.equal(pub.status, 404);
  assert.equal(pub.body.error.code, 'store_not_found');
  //  روشن برای `all` و خاموش برای همین دکان ⇒ خاموش
  await setFlag('all', 'shop.chat', true);
  await setFlag(o.shopId, 'shop.chat', false);
  assert.equal(await chat.flagOf(o.shopId), false);
  await setFlag('all', 'shop.chat', false);
});

test('CHAT-01: متن یک بار ثبت و یک بار در دکان دیده می‌شود؛ پاسخِ دکان بی شناسهٔ کارمند به مشتری می‌رسد', async () => {
  const o = await chatShop('یک');
  const me = customer(o.shopId);
  const opened = await chat.open(me, { name: 'احمد' });
  assert.equal(opened.created, true);
  const again = await chat.open(me, {});
  assert.equal(again.created, false);
  assert.equal(again.conversation.id, opened.conversation.id);
  const conv = opened.conversation.id;

  const sent = await chat.send(me, conv, { clientId: cid(), body: 'سلام، برنج دارید؟' });
  assert.equal(sent.replayed, false);
  assert.equal(sent.message.seq, 1);
  assert.equal(sent.message.status, 'sent');

  const inbox = await h.get(`${base}/conversations`, { token: o.token });
  assert.equal(inbox.status, 200, JSON.stringify(inbox.body));
  assert.equal(inbox.body.conversations.length, 1);
  const c = inbox.body.conversations[0];
  assert.equal(c.unread, 1);
  assert.equal(c.customer.name, 'احمد');
  assert.equal(c.lastMessage.preview, 'سلام، برنج دارید؟');

  const got = await msgs(o.token, conv);
  assert.equal(got.status, 200);
  assert.equal(got.body.messages.length, 1);
  assert.equal(got.body.messages[0].body, 'سلام، برنج دارید؟');
  assert.equal(got.body.messages[0].sender, 'customer');
  assert.equal(got.body.messages[0].clientId, undefined, 'شناسهٔ دستگاهِ طرفِ دیگر لازم نیست');

  const r = await sendHttp(o.token, conv, { clientId: cid(), body: 'بله، دارد' });
  assert.equal(r.status, 201, JSON.stringify(r.body));
  assert.equal(r.body.message.senderUserId, o.userId);

  const back = await chat.changes(me, conv, { after: sent.message.rev });
  assert.equal(back.messages.length, 1);
  const m = back.messages[0];
  assert.equal(m.body, 'بله، دارد');
  assert.equal(m.sender, 'shop');
  const raw = JSON.stringify(back);
  assert.ok(!raw.includes(o.userId), 'شناسهٔ کارمند هرگز به مشتری نمی‌رسد');
  assert.ok(!('senderUserId' in m));
  assert.equal(back.conversation.shop.name, 'دکانِ یک');
  assert.equal(await count('shop_chat_messages', o.shopId), 2);
});

test('CHAT-02 و CHAT-11: صفِ آفلاین با تکرار — ترتیبِ درست، بی تکرار، یک پوش برای هر پیام', async () => {
  const o = await chatShop('صف');
  await query(
    `INSERT INTO push_tokens (id, app, token, provider, user_id, shop_id, status, created_at, updated_at)
     VALUES ($1,'shop',$2,'fcm',$3,$4,'active',$5,$5)`,
    [newId('ptk'), `tok-${uniq()}`, o.userId, o.shopId, now()]
  );
  const pushes = [];
  push.setDeliver(async (row, message) => { pushes.push({ row, message }); });
  try {
    const me = customer(o.shopId, '+93790000002');
    const conv = (await chat.open(me, {})).conversation.id;
    const q = [1, 2, 3].map((i) => ({ clientId: cid(), body: `پیامِ ${i}` }));
    //  دستگاه دوباره‌کاری می‌کند: هر پیام دو یا سه بار، و یکی هم‌زمان
    await chat.send(me, conv, q[0]);
    await chat.send(me, conv, q[0]);
    await Promise.all([chat.send(me, conv, q[1]), chat.send(me, conv, q[1]), chat.send(me, conv, q[1])]);
    await chat.send(me, conv, q[0]);
    const third = await chat.send(me, conv, q[2]);
    const replay = await chat.send(me, conv, q[2]);
    assert.equal(replay.replayed, true);
    assert.equal(replay.message.id, third.message.id);

    const all = await chat.changes(me, conv, { after: 0 });
    assert.deepEqual(all.messages.map((m) => [m.seq, m.body]), [[1, 'پیامِ 1'], [2, 'پیامِ 2'], [3, 'پیامِ 3']]);
    assert.equal(await count('shop_chat_messages', o.shopId), 3);
    assert.equal(pushes.length, 3, 'هر پیام یک پوش، نه هر تلاش');
    for (const p of pushes) {
      assert.equal(p.message.data.type, 'shop_chat');
      assert.ok(!JSON.stringify(p.message).includes('پیامِ 1'), 'متنِ پیام از راهِ پوش بیرون نمی‌رود');
    }

    //  همان شناسه با متنِ دیگر ⇒ ۴۰۹ و هیچ تغییری
    await assert.rejects(chat.send(me, conv, { clientId: q[0].clientId, body: 'متنِ دیگر' }), (e) => e.status === 409 && e.code === 'client_id_reused');
    //  از HTTP هم: تکرار ⇒ ۲۰۰ با سرآیندِ Idempotent-Replayed
    const body = { clientId: cid(), body: 'از دکان' };
    const a = await sendHttp(o.token, conv, body);
    const b = await sendHttp(o.token, conv, body);
    assert.equal(a.status, 201);
    assert.equal(b.status, 200);
    assert.equal(b.headers.get('idempotent-replayed'), 'true');
    assert.equal(b.body.message.id, a.body.message.id);
    assert.equal(await count('shop_chat_messages', o.shopId), 4);
    assert.equal(pushes.length, 3, 'پیامِ خودِ دکان به دکان پوش نمی‌دهد');
    //  شناسهٔ نامعتبر/نبودنش
    const bad = await sendHttp(o.token, conv, { body: 'بی شناسه' });
    assert.equal(bad.status, 400);
    assert.equal(bad.body.error.code, 'client_id_required');
  } finally {
    push.setDeliver(null);
  }
});

test('CHAT-03 و CHAT-15: برنامهٔ بسته همه‌چیز را از سرور می‌گیرد؛ long-poll پیامِ تازه را بی‌درنگ می‌رساند', async () => {
  const o = await chatShop('بازیابی');
  const me = customer(o.shopId, '+93790000003');
  const conv = (await chat.open(me, {})).conversation.id;
  for (let i = 1; i <= 5; i++) await chat.send(me, conv, { clientId: cid(), body: `p${i}` });
  //  دستگاهِ تازهٔ دکان: صفحه‌به‌صفحه از صفر
  const p1 = await msgs(o.token, conv, 0, '&limit=2');
  assert.equal(p1.body.messages.length, 2);
  assert.equal(p1.body.hasMore, true);
  const p2 = await msgs(o.token, conv, p1.body.cursor, '&limit=10');
  assert.deepEqual([...p1.body.messages, ...p2.body.messages].map((m) => m.body), ['p1', 'p2', 'p3', 'p4', 'p5']);
  assert.equal(p2.body.hasMore, false);
  const cursor = p2.body.cursor;
  const empty = await msgs(o.token, conv, cursor);
  assert.equal(empty.body.messages.length, 0);
  assert.equal(empty.body.cursor, cursor);

  //  long-poll: درخواست منتظر می‌ماند و با پیامِ مشتری فوراً برمی‌گردد
  const t0 = Date.now();
  const waiting = msgs(o.token, conv, cursor, '&wait=8000');
  await new Promise((r) => setTimeout(r, 150));
  await chat.send(me, conv, { clientId: cid(), body: 'تازه' });
  const got = await waiting;
  assert.equal(got.status, 200);
  assert.deepEqual(got.body.messages.map((m) => m.body), ['تازه']);
  assert.ok(Date.now() - t0 < 5000, 'منتظرِ پایانِ مهلت نماند');
  //  مهلتِ بی‌خبر ⇒ پاسخِ خالی، نه خطا
  const idle = await msgs(o.token, conv, got.body.cursor, '&wait=300');
  assert.equal(idle.status, 200);
  assert.equal(idle.body.messages.length, 0);
});

test('CHAT-04: رسید فقط با اعلامِ گیرنده؛ خواندنِ پیام چیزی را عوض نمی‌کند؛ عقب نمی‌رود', async () => {
  const o = await chatShop('رسید');
  const me = customer(o.shopId, '+93790000004');
  const conv = (await chat.open(me, {})).conversation.id;
  const a = await chat.send(me, conv, { clientId: cid(), body: 'یک' });
  await chat.send(me, conv, { clientId: cid(), body: 'دو' });
  const status = async () => (await chat.changes(me, conv, { after: 0 })).messages.map((m) => m.status);

  //  دکان فهرست و پیام‌ها را می‌خواند — هنوز هیچ رسیدی نیامده
  await h.get(`${base}/conversations`, { token: o.token });
  await msgs(o.token, conv);
  assert.deepEqual(await status(), ['sent', 'sent']);

  const rcpt = (body) => h.post(`${base}/conversations/${conv}/receipts`, body, { token: o.token });
  let r = await rcpt({ deliveredSeq: 2 });
  assert.equal(r.status, 200, JSON.stringify(r.body));
  assert.deepEqual(await status(), ['delivered', 'delivered']);
  r = await rcpt({ readSeq: a.message.seq });
  assert.deepEqual(await status(), ['read', 'delivered']);
  assert.equal(r.body.conversation.unread, 1);
  //  عقب نمی‌رود و از آخرین پیام جلوتر نمی‌رود
  r = await rcpt({ readSeq: 0, deliveredSeq: 0 });
  assert.deepEqual(await status(), ['read', 'delivered']);
  r = await rcpt({ readSeq: 999 });
  assert.equal(r.body.conversation.receipts.myReadSeq, 2);
  assert.equal(r.body.conversation.unread, 0);
  assert.deepEqual(await status(), ['read', 'read']);
  const bad = await rcpt({ readSeq: -1 });
  assert.equal(bad.status, 400);
});

test('CHAT-05 و CHAT-06: عکس — نوع از بایت، EXIF ِ مکان پاک و جهت باقی؛ پیش‌نمایش؛ بایت‌های سالم', async () => {
  //  خودِ پاک‌کننده
  const clean = chatMedia.stripImageMetadata(jpegWithGps(6), 'image/jpeg');
  const s = clean.toString('latin1');
  for (const secret of ['LATLONG-SECRET-1', 'GPS-XMP-SECRET', 'comment-secret']) assert.ok(!s.includes(secret), secret);
  assert.ok(s.includes('JFIF'));
  assert.equal(chatMedia.orientationOf(clean.slice(s.indexOf('Exif\0\0') + 6)), 6, 'جهتِ عکس می‌ماند');
  assert.ok(!chatMedia.stripImageMetadata(jpegWithGps(1), 'image/jpeg').toString('latin1').includes('Exif'), 'جهتِ ۱ ⇒ هیچ Exif');
  assert.deepEqual(clean.slice(-202), jpegWithGps(6).slice(-202), 'دادهٔ تصویر بایت‌به‌بایت همان');
  const png = chatMedia.stripImageMetadata(pngWithText(), 'image/png').toString('latin1');
  assert.ok(!png.includes('png-secret') && !png.includes('exif-secret') && png.includes('IDAT') && png.endsWith('IEND\0\0\0\0'));
  const webp = chatMedia.stripImageMetadata(webpWithExif(), 'image/webp');
  assert.ok(!webp.toString('latin1').includes('secret'));
  assert.equal(webp.readUInt32LE(4), webp.length - 8, 'اندازهٔ RIFF درست');
  assert.equal(webp[20] & 0x0c, 0, 'بیتِ EXIF/XMP در VP8X صفر');
  assert.equal(webp[20] & 0x10, 0x10, 'بیت‌های دیگر دست نخورده');
  assert.throws(() => chatMedia.stripImageMetadata(Buffer.from([0xff, 0xd8, 0xff, 0xe1, 0xff, 0xff, 1]), 'image/jpeg'), (e) => e.code === 'bad_media');

  //  از راهِ HTTP، با گفت‌وگو و پیام
  const o = await chatShop('عکس');
  const me = customer(o.shopId, '+93790000005');
  const conv = (await chat.open(me, {})).conversation.id;
  const big = await upload(o.token, conv, jpegWithGps(6), { type: 'image/png' });
  assert.equal(big.status, 201, JSON.stringify(big.body));
  assert.equal(big.body.mime, 'image/jpeg', 'سرآیندِ کلاینت ملاک نیست');
  assert.equal(big.body.size, clean.length);
  const again = await upload(o.token, conv, jpegWithGps(6));
  assert.equal(again.body.mediaId, big.body.mediaId, 'همان بایت‌ها ⇒ همان ردیف');
  assert.equal(again.body.reused, true);
  const thumb = await upload(o.token, conv, smallJpeg(2), { role: 'thumb' });
  assert.equal(thumb.status, 201);
  const sent = await sendHttp(o.token, conv, {
    clientId: cid(), kind: 'image', body: 'عکسِ کالا', mediaId: big.body.mediaId, thumbId: thumb.body.mediaId,
    meta: { width: 1280, height: 960, junk: 'x' },
  });
  assert.equal(sent.status, 201, JSON.stringify(sent.body));
  assert.deepEqual(sent.body.message.meta, { width: 1280, height: 960 });
  assert.equal(sent.body.message.media.size, clean.length);
  assert.equal(sent.body.message.media.thumbId, thumb.body.mediaId);

  //  مشتری همان بایت‌های پاک‌شده را می‌گیرد
  const m = await chat.getMedia(me, conv, big.body.mediaId);
  assert.deepEqual(m.data, clean);
  const dl = await h.download(`${base}/conversations/${conv}/media/${big.body.mediaId}`, { token: o.token });
  assert.equal(dl.status, 200);
  assert.deepEqual(dl.buffer, clean);
  assert.equal(dl.headers.get('x-content-type-options'), 'nosniff');
  assert.match(dl.headers.get('content-security-policy'), /sandbox/);
  assert.match(dl.headers.get('cache-control'), /no-store/);

  //  صدا و PDF
  const voice = await chat.putMedia(me, conv, ogg());
  assert.equal(voice.kind, 'audio');
  assert.equal(voice.mime, 'audio/ogg');
  const sv = await chat.send(me, conv, { clientId: cid(), kind: 'audio', mediaId: voice.mediaId, meta: { durationMs: 4200 } });
  assert.equal(sv.message.meta.durationMs, 4200);
  const pdf = await upload(o.token, conv, Buffer.concat([Buffer.from('%PDF-1.4\n', 'latin1'), Buffer.alloc(200, 32)]));
  assert.equal(pdf.body.kind, 'file');
  await sendHttp(o.token, conv, { clientId: cid(), kind: 'file', mediaId: pdf.body.mediaId });
  const pd = await chat.getMedia(me, conv, pdf.body.mediaId);
  assert.equal(pd.mime, 'application/pdf');
  const pdfDl = await h.download(`${base}/conversations/${conv}/media/${pdf.body.mediaId}`, { token: o.token });
  assert.match(pdfDl.headers.get('content-disposition'), /^attachment/);

  //  نوع و رسانه باید بخوانند؛ رسانهٔ طرفِ دیگر در پیامِ این طرف نمی‌نشیند
  const mismatch = await sendHttp(o.token, conv, { clientId: cid(), kind: 'audio', mediaId: big.body.mediaId });
  assert.equal(mismatch.body.error.code, 'bad_media');
  const others = await sendHttp(o.token, conv, { clientId: cid(), kind: 'audio', mediaId: voice.mediaId });
  assert.equal(others.body.error.code, 'bad_media');
});

test('CHAT-10: فایلِ اجرایی، MIME ِ جعلی، SVG و فایلِ بزرگ رد می‌شوند و سرویس سرپا می‌ماند', async () => {
  const o = await chatShop('رد');
  const conv = (await chat.open(customer(o.shopId, '+93790000010'), {})).conversation.id;
  const exe = Buffer.concat([Buffer.from('MZ', 'latin1'), Buffer.alloc(400, 0x90)]);
  const cases = [
    [exe, 'image/jpeg', 415, 'unsupported_media'],
    [Buffer.from('<svg xmlns="http://www.w3.org/2000/svg"><script>alert(1)</script></svg>'), 'image/svg+xml', 415, 'unsupported_media'],
    [Buffer.from('<!doctype html><html><script>x()</script></html>'), 'image/png', 415, 'unsupported_media'],
    [Buffer.concat([Buffer.from('GIF89a', 'latin1'), Buffer.alloc(100)]), 'image/gif', 415, 'unsupported_media'],
    [Buffer.concat([smallJpeg(), Buffer.alloc(1600 * 1024)]), 'image/jpeg', 413, 'too_large'],
    [Buffer.alloc(0), 'image/jpeg', 400, 'bad_media'],
  ];
  for (const [buf, type, status, code] of cases) {
    const r = await upload(o.token, conv, buf, { type });
    assert.equal(r.status, status, `${type} ${buf.length}`);
    assert.equal(r.body.error.code, code);
  }
  //  پیش‌نمایش فقط عکسِ کوچک
  const t = await upload(o.token, conv, ogg(), { role: 'thumb' });
  assert.equal(t.status, 415);
  const tb = await upload(o.token, conv, Buffer.concat([smallJpeg(), Buffer.alloc(70 * 1024)]), { role: 'thumb' });
  assert.equal(tb.status, 413);
  //  بدنهٔ بزرگ‌تر از بزرگ‌ترین سقف پیش از خوانده شدن
  const huge = await upload(o.token, conv, Buffer.alloc(6 * 1024 * 1024, 1));
  assert.equal(huge.status, 413);
  assert.equal(await count('shop_chat_media', o.shopId), 0);
  const alive = await h.get(`${base}/config`, { token: o.token });
  assert.equal(alive.status, 200);
});

test('CHAT-08: حذف برای من فقط در نمای همان؛ حذف برای همه سنگِ قبر به دستگاهِ آفلاین می‌رسد و رسانه می‌رود', async () => {
  const o = await chatShop('حذف');
  const mgr = await member(o.shopId, 'manager', 'مدیرِ حذف');
  const staff = await member(o.shopId, 'staff', 'شاگردِ حذف');
  const me = customer(o.shopId, '+93790000008');
  const conv = (await chat.open(me, {})).conversation.id;
  const c1 = await chat.send(me, conv, { clientId: cid(), body: 'پیامِ مشتری' });
  const s1 = (await sendHttp(staff.token, conv, { clientId: cid(), body: 'پیامِ شاگرد' })).body.message;
  //  دستگاهِ آفلاینِ دکان تا این‌جا همگام است
  const offlineCursor = (await msgs(o.token, conv)).body.cursor;

  //  حذف برای من (مشتری، پیامِ دکان): فقط نمای مشتری
  const hid = await chat.remove(me, conv, s1.id, 'me');
  assert.equal(hid.message.hidden, true);
  const mine = (await chat.changes(me, conv, { after: 0 })).messages.find((m) => m.id === s1.id);
  assert.deepEqual(Object.keys(mine).sort(), ['createdAt', 'hidden', 'id', 'rev', 'sender', 'seq']);
  const shopSide = (await msgs(o.token, conv)).body.messages.find((m) => m.id === s1.id);
  assert.equal(shopSide.body, 'پیامِ شاگرد');
  await chat.remove(me, conv, s1.id, 'me'); // دوباره ⇒ بی‌اثر
  assert.equal(Number((await one('SELECT COUNT(*)::int AS n FROM shop_chat_hidden')).n) >= 1, true);

  //  دکان پیامِ مشتری را «برای همه» پاک نمی‌کند
  const no = await h.del(`${base}/conversations/${conv}/messages/${c1.message.id}?for=all`, { token: mgr.token });
  assert.equal(no.status, 403);
  assert.equal(no.body.error.code, 'delete_not_allowed');
  //  شاگرد پیامِ کارمندِ دیگر را نه؛ مدیر (chat.moderate) بله
  const own = (await sendHttp(o.token, conv, { clientId: cid(), body: 'پیامِ صاحب' })).body.message;
  const st = await h.del(`${base}/conversations/${conv}/messages/${own.id}?for=all`, { token: staff.token });
  assert.equal(st.status, 403);

  //  مشتری عکسِ خودش را برای همه پاک می‌کند ⇒ متن و رسانه می‌روند
  const pic = await chat.putMedia(me, conv, smallJpeg(4));
  const withPic = await chat.send(me, conv, { clientId: cid(), kind: 'image', body: 'عکسِ من', mediaId: pic.mediaId });
  const del = await chat.remove(me, conv, withPic.message.id, 'all');
  assert.equal(del.message.deleted, true);
  assert.equal(del.message.body, undefined);
  const row = await one('SELECT body, media_id, deleted_at FROM shop_chat_messages WHERE id=$1', [withPic.message.id]);
  assert.equal(row.body, '');
  assert.equal(row.media_id, null);
  assert.ok(row.deleted_at);
  assert.equal(await one('SELECT id FROM shop_chat_media WHERE id=$1', [pic.mediaId]), null, 'بایت‌ها از سرور رفتند');
  const goneDl = await h.download(`${base}/conversations/${conv}/media/${pic.mediaId}`, { token: o.token });
  assert.equal(goneDl.status, 404);

  //  مدیر پیامِ شاگرد را برای همه پاک می‌کند (با سابقهٔ عملیات، بی متن)
  const md = await h.del(`${base}/conversations/${conv}/messages/${s1.id}?for=all`, { token: mgr.token });
  assert.equal(md.status, 200, JSON.stringify(md.body));
  const aud = await one("SELECT detail FROM audit_logs WHERE action='chat.message_deleted' AND target_id=$1", [s1.id]);
  assert.ok(aud);
  assert.ok(!JSON.stringify(aud.detail).includes('پیامِ شاگرد'));

  //  دستگاهِ آفلاین با مکانِ قدیمی هر دو سنگِ قبر را می‌گیرد
  const later = (await msgs(o.token, conv, offlineCursor)).body.messages;
  const tomb = later.filter((m) => m.deleted).map((m) => m.id).sort();
  assert.deepEqual(tomb, [s1.id, withPic.message.id].sort());
  for (const m of later.filter((x) => x.deleted)) assert.equal(m.body, undefined);

  //  مهلتِ حذفِ نویسنده
  await query('UPDATE shop_chat_messages SET created_at=$2 WHERE id=$1', [c1.message.id, now() - 2 * 24 * 3600 * 1000]);
  await assert.rejects(chat.remove(me, conv, c1.message.id, 'all'), (e) => e.code === 'delete_window_passed');
  await assert.rejects(chat.remove(me, conv, c1.message.id, 'x'), (e) => e.code === 'bad_scope');
});

test('CHAT-09: دکان یا مشتریِ دیگر با عوض کردنِ شناسه به هیچ گفت‌وگو، پیام یا رسانه‌ای نمی‌رسد', async () => {
  const a = await chatShop('الف');
  const b = await chatShop('ب');
  const meA = customer(a.shopId, '+93790000009');
  const convA = (await chat.open(meA, {})).conversation.id;
  const msg = await chat.send(meA, convA, { clientId: cid(), body: 'خصوصی' });
  const pic = await chat.putMedia(meA, convA, smallJpeg(5));
  await chat.send(meA, convA, { clientId: cid(), kind: 'image', mediaId: pic.mediaId });

  //  کارمندِ دکانِ ب
  for (const path of [
    `${base}/conversations/${convA}`,
    `${base}/conversations/${convA}/messages`,
    `${base}/conversations/${convA}/media/${pic.mediaId}`,
  ]) {
    const r = await h.get(path, { token: b.token });
    assert.equal(r.status, 404, path);
  }
  const w = await sendHttp(b.token, convA, { clientId: cid(), body: 'نفوذ' });
  assert.equal(w.status, 404);
  const d = await h.del(`${base}/conversations/${convA}/messages/${msg.message.id}?for=me`, { token: b.token });
  assert.equal(d.status, 404);
  const s = await h.get(`${base}/search?q=خصوصی`, { token: b.token });
  assert.equal(s.body.results.length, 0);
  assert.equal((await h.get(`${base}/conversations`, { token: b.token })).body.conversations.length, 0);

  //  مشتریِ دیگر (شمارهٔ دیگر) در همان دکان، و همان شماره در دکانِ دیگر
  for (const v of [customer(a.shopId, '+93790000099'), customer(b.shopId, '+93790000009')]) {
    await assert.rejects(chat.changes(v, convA, {}), (e) => e.status === 404);
    await assert.rejects(chat.getMedia(v, convA, pic.mediaId), (e) => e.status === 404);
    await assert.rejects(chat.send(v, convA, { clientId: cid(), body: 'x' }), (e) => e.status === 404);
  }
  //  رسانه/پاسخِ گفت‌وگوی دیگر در گفت‌وگوی خودِ مهاجم
  const meB = customer(a.shopId, '+93790000098');
  const convB = (await chat.open(meB, {})).conversation.id;
  await assert.rejects(chat.send(meB, convB, { clientId: cid(), kind: 'image', mediaId: pic.mediaId }), (e) => e.code === 'bad_media');
  await assert.rejects(chat.send(meB, convB, { clientId: cid(), body: 'x', replyTo: msg.message.id }), (e) => e.code === 'bad_reply');
  await assert.rejects(chat.getMedia(meB, convB, pic.mediaId), (e) => e.code === 'media_gone');
  //  شناسه‌های بدشکل ⇒ همان ۴۰۴، نه ۵۰۰
  const weird = await h.get(`${base}/conversations/${encodeURIComponent("x' OR 1=1 --")}/messages`, { token: a.token });
  assert.equal(weird.status, 404);
});

test('CHAT-14: جست‌وجو، پاسخ و ویرایش فقط در محدودهٔ مجاز؛ پاک‌شده و پنهان در جست‌وجو نمی‌آیند', async () => {
  const o = await chatShop('جست');
  const staff = await member(o.shopId, 'staff', 'شاگردِ جست');
  const me = customer(o.shopId, '+93790000014');
  const conv = (await chat.open(me, {})).conversation.id;
  const q1 = await chat.send(me, conv, { clientId: cid(), body: 'قیمتِ زعفران چند است؟' });
  const q2 = await chat.send(me, conv, { clientId: cid(), body: 'زعفرانِ قائنات هم دارید؟' });
  const q3 = await chat.send(me, conv, { clientId: cid(), body: 'زعفران_۱۰۰٪ اصل؟' });
  const other = customer(o.shopId, '+93790000015');
  const conv2 = (await chat.open(other, {})).conversation.id;
  await chat.send(other, conv2, { clientId: cid(), body: 'زعفران برای مشتریِ دیگر' });

  await chat.remove(me, conv, q2.message.id, 'all');
  await h.del(`${base}/conversations/${conv}/messages/${q1.message.id}?for=me`, { token: o.token });

  const ownerSearch = (await h.get(`${base}/search?q=${encodeURIComponent('زعفران')}`, { token: o.token })).body.results;
  assert.deepEqual(ownerSearch.map((r) => r.id).sort(), [q3.message.id, (await chat.changes(other, conv2, {})).messages[0].id].sort());
  //  شاگرد q1 را پنهان نکرده ⇒ برای او هست
  const staffSearch = (await h.get(`${base}/search?q=${encodeURIComponent('زعفران')}`, { token: staff.token })).body.results;
  assert.ok(staffSearch.some((r) => r.id === q1.message.id));
  //  مشتری فقط گفت‌وگوی خودش
  const mineOnly = (await chat.search(me, 'زعفران')).results;
  assert.deepEqual(mineOnly.map((r) => r.id).sort(), [q1.message.id, q3.message.id].sort());
  //  `_` و `%` حرفی‌اند، نه wildcard
  assert.equal((await chat.search(me, 'زعفران_')).results.length, 1);
  assert.equal((await chat.search(me, '%%')).results.length, 0);
  await assert.rejects(chat.search(me, 'ز'), (e) => e.code === 'query_too_short');

  //  پاسخ
  const rep = await sendHttp(staff.token, conv, { clientId: cid(), body: 'کیلویی ...', replyTo: q1.message.id });
  assert.equal(rep.body.message.replyTo, q1.message.id);
  //  ویرایش: فقط نویسنده، در مهلت
  const ed = await h.patch(`${base}/conversations/${conv}/messages/${rep.body.message.id}`, { body: 'کیلویی ۹۰۰' }, { token: staff.token });
  assert.equal(ed.status, 200, JSON.stringify(ed.body));
  assert.equal(ed.body.message.body, 'کیلویی ۹۰۰');
  assert.ok(ed.body.message.editedAt);
  const byOwner = await h.patch(`${base}/conversations/${conv}/messages/${rep.body.message.id}`, { body: 'x' }, { token: o.token });
  assert.equal(byOwner.status, 403);
  assert.equal(byOwner.body.error.code, 'not_author');
  await assert.rejects(chat.edit(me, conv, rep.body.message.id, { body: 'x' }), (e) => e.code === 'not_author');
  const seen = (await chat.changes(me, conv, { after: 0 })).messages.find((m) => m.id === rep.body.message.id);
  assert.equal(seen.body, 'کیلویی ۹۰۰');
  await query('UPDATE shop_chat_messages SET created_at=$2 WHERE id=$1', [q3.message.id, now() - 16 * 60 * 1000]);
  await assert.rejects(chat.edit(me, conv, q3.message.id, { body: 'y' }), (e) => e.code === 'edit_window_passed');
  await assert.rejects(chat.edit(me, conv, q2.message.id, { body: 'y' }), (e) => e.code === 'message_not_found');
  //  مسیرِ کسی که پاسخ‌دادن ندارد؟ همه دارند؛ ولی متنِ بلند هرگز بی‌صدا بریده نمی‌شود
  const long = await sendHttp(o.token, conv, { clientId: cid(), body: 'x'.repeat(4001) });
  assert.equal(long.body.error.code, 'message_too_long');
});

test('§۳۳.۳: شمارهٔ کامل فقط با orders.contact؛ بلاک دوسویه؛ گزارش با رونوشت؛ مدیرِ سامانه با سابقه', async () => {
  const o = await chatShop('امن');
  const staff = await member(o.shopId, 'staff', 'شاگردِ امن');
  const me = customer(o.shopId, '+93791234567');
  const conv = (await chat.open(me, { name: 'مشتری' })).conversation.id;
  const bad = await chat.send(me, conv, { clientId: cid(), body: 'پیامِ زشت' });

  const asOwner = (await h.get(`${base}/conversations/${conv}`, { token: o.token })).body.conversation;
  assert.equal(asOwner.customer.phone, '+93791234567');
  const asStaff = (await h.get(`${base}/conversations/${conv}`, { token: staff.token })).body.conversation;
  assert.equal(asStaff.customer.phone, undefined);
  assert.equal(asStaff.customer.phoneMasked, '+937•••••567');
  //  شاگرد با جست‌وجوی شماره نمی‌تواند شماره را حدس بزند
  assert.equal((await h.get(`${base}/conversations?q=1234567`, { token: staff.token })).body.conversations.length, 0);
  assert.equal((await h.get(`${base}/conversations?q=1234567`, { token: o.token })).body.conversations.length, 1);
  const cfg = await h.get(`${base}/config`, { token: staff.token });
  assert.deepEqual(cfg.body.permissions, { reply: true, moderate: false, contact: false });

  //  بلاک: شاگرد اجازه ندارد؛ صاحب دارد
  const nope = await h.put(`${base}/conversations/${conv}/block`, {}, { token: staff.token });
  assert.equal(nope.status, 403);
  assert.equal(nope.body.error.code, 'permission_denied');
  const blk = await h.put(`${base}/conversations/${conv}/block`, {}, { token: o.token });
  assert.equal(blk.status, 200);
  assert.equal(blk.body.conversation.blockedCustomer, true);
  await assert.rejects(chat.send(me, conv, { clientId: cid(), body: 'باز هم' }), (e) => e.code === 'chat_blocked');
  await assert.rejects(chat.putMedia(me, conv, smallJpeg()), (e) => e.code === 'chat_blocked');
  //  گفت‌وگوی تازه (مثلاً به اسمِ سفارش) هم ساخته نمی‌شود؛ خواندنِ گذشته آزاد است
  await assert.rejects(chat.open({ ...me }, { orderNo: 77 }), (e) => e.status === 404 || e.code === 'chat_blocked');
  //  RSK-07: شکلِ دیگرِ همان گوشی (‎+930…‎، ‎+9300…‎) گفت‌وگوی تازهٔ بی‌بلاک نمی‌سازد
  for (const alt of ['+930791234567', '+9300791234567']) {
    await assert.rejects(chat.open(customer(o.shopId, alt), { name: 'مشتری' }), (e) => e.code === 'chat_blocked', alt);
  }
  assert.equal((await chat.changes(me, conv, {})).messages.length, 1);
  assert.equal((await chat.viewOf(me, conv)).canSend, false);
  await h.del(`${base}/conversations/${conv}/block`, { token: o.token });
  await chat.send(me, conv, { clientId: cid(), body: 'ببخشید' });
  //  مشتری دکان را می‌بندد ⇒ دکان نمی‌نویسد
  await chat.setBlock(me, conv, true);
  const shopSend = await sendHttp(o.token, conv, { clientId: cid(), body: 'سلام' });
  assert.equal(shopSend.status, 403);
  assert.equal(shopSend.body.error.code, 'chat_blocked_by_customer');
  await chat.setBlock(me, conv, false);
  assert.ok(await one("SELECT 1 AS x FROM audit_logs WHERE action='chat.customer_blocked' AND target_id=$1", [conv]));

  //  گزارش با رونوشت؛ تکرار ردیفِ دوم نمی‌سازد؛ پیامِ خود گزارش نمی‌شود
  const rep = await h.post(`${base}/conversations/${conv}/reports`, { messageId: bad.message.id, reason: 'abuse' }, { token: o.token });
  assert.equal(rep.status, 201, JSON.stringify(rep.body));
  const dup = await h.post(`${base}/conversations/${conv}/reports`, { messageId: bad.message.id, reason: 'abuse' }, { token: staff.token });
  assert.equal(dup.body.duplicate, true);
  await assert.rejects(chat.report(me, conv, { messageId: bad.message.id, reason: 'spam' }), (e) => e.code === 'bad_report');
  await assert.rejects(chat.report(me, conv, { reason: 'nope' }), (e) => e.code === 'bad_reason');
  //  مشتری پیامش را برای همه پاک می‌کند — رونوشتِ گزارش می‌ماند
  await chat.remove(me, conv, bad.message.id, 'all');
  const list = await h.get('/api/admin/shop-chat/reports', { token: admin });
  assert.equal(list.status, 200, JSON.stringify(list.body));
  const r = list.body.reports.find((x) => x.messageId === bad.message.id);
  assert.equal(r.snapshot.body, 'پیامِ زشت');
  assert.equal(r.reporter, 'shop');
  assert.ok(await one("SELECT 1 AS x FROM audit_logs WHERE action='admin.shop_chat_reports_viewed'"));
  const rv = await h.put(`/api/admin/shop-chat/reports/${r.id}`, {}, { token: admin });
  assert.equal(rv.body.status, 'reviewed');
  //  کاربرِ دکان به درِ مدیرِ سامانه نمی‌رسد
  assert.equal((await h.get('/api/admin/shop-chat/reports', { token: o.token })).status, 401);
  //  هیچ متنِ پیامی در سابقهٔ عملیاتِ این دکان نیست
  const logs = await query('SELECT detail FROM audit_logs WHERE shop_id=$1', [o.shopId]);
  assert.ok(!JSON.stringify(logs.rows).includes('پیامِ زشت'));
});

test('گفت‌وگو از روی سفارش: کارمند فقط سفارشِ دکانِ خودش؛ مشتری فقط سفارشِ شمارهٔ خودش', async () => {
  const o = await chatShop('سفارش');
  const other = await chatShop('سفارشِ دیگر');
  const t = now();
  const orderId = `ord${uniq()}`;
  await query(
    `INSERT INTO online_orders (id, shop_id, order_no, client_key, request_hash, source, fulfillment, customer_name, customer_phone, created_at, updated_at)
     VALUES ($1,$2,7,$3,'h','storefront','pickup','کریم','+93790000777',$4,$4)`,
    [orderId, o.shopId, `ck-${uniq()}`, t]
  );
  const made = await h.post(`${base}/conversations`, { orderId }, { token: o.token });
  assert.equal(made.status, 201, JSON.stringify(made.body));
  assert.equal(made.body.conversation.orderNo, 7);
  assert.equal(made.body.conversation.customer.name, 'کریم');
  const same = await h.post(`${base}/conversations`, { orderId }, { token: o.token });
  assert.equal(same.status, 200);
  assert.equal(same.body.conversation.id, made.body.conversation.id);
  assert.equal((await h.post(`${base}/conversations`, { orderId }, { token: other.token })).status, 404);
  assert.equal((await h.post(`${base}/conversations`, {}, { token: o.token })).body.error.code, 'order_required');

  //  مشتریِ همان شماره همان گفت‌وگو را با شمارهٔ سفارش می‌گیرد؛ شمارهٔ دیگر نه
  const mine = await chat.open(customer(o.shopId, '+93790000777'), { orderNo: 7 });
  assert.equal(mine.conversation.id, made.body.conversation.id);
  assert.equal(mine.conversation.orderNo, 7);
  await assert.rejects(chat.open(customer(o.shopId, '+93790000778'), { orderNo: 7 }), (e) => e.code === 'order_not_found');
  //  وضعیت
  const closed = await h.put(`${base}/conversations/${mine.conversation.id}/status`, { status: 'closed' }, { token: o.token });
  assert.equal(closed.body.conversation.status, 'closed');
  await chat.send(customer(o.shopId, '+93790000777'), mine.conversation.id, { clientId: cid(), body: 'هنوز نیامده' });
  assert.equal((await chat.viewOf(customer(o.shopId, '+93790000777'), mine.conversation.id)).status, 'open', 'پیامِ تازه گفت‌وگو را باز می‌کند');
});

test('پاک‌سازی: رسانهٔ بی‌پیام پس از یک ساعت می‌رود؛ رسانهٔ پیام می‌ماند؛ نگهداری فقط اگر روشن شود', async () => {
  const o = await chatShop('جارو');
  const me = customer(o.shopId, '+93790000020');
  const conv = (await chat.open(me, {})).conversation.id;
  const orphan = await chat.putMedia(me, conv, smallJpeg(8));
  const used = await chat.putMedia(me, conv, smallJpeg(9));
  const fresh = await chat.putMedia(me, conv, smallJpeg(10));
  const m = await chat.send(me, conv, { clientId: cid(), kind: 'image', mediaId: used.mediaId });
  await query('UPDATE shop_chat_media SET created_at=$2 WHERE id = ANY($1)', [[orphan.mediaId, used.mediaId], now() - 2 * 3600 * 1000]);
  const out = await chat.sweep({ retentionDays: 0 });
  assert.ok(out.orphans >= 1);
  assert.equal(out.expired, 0);
  assert.equal(await one('SELECT id FROM shop_chat_media WHERE id=$1', [orphan.mediaId]), null);
  assert.ok(await one('SELECT id FROM shop_chat_media WHERE id=$1', [used.mediaId]));
  assert.ok(await one('SELECT id FROM shop_chat_media WHERE id=$1', [fresh.mediaId]), 'بارگذاریِ تازه منتظرِ ارسال است');
  //  پیش‌فرضِ نگهداری = هیچ پاک‌کردنی
  await query('UPDATE shop_chat_messages SET created_at=$2 WHERE id=$1', [m.message.id, now() - 400 * 24 * 3600 * 1000]);
  assert.equal((await chat.sweep()).expired, 0);
  assert.ok(await one('SELECT id FROM shop_chat_messages WHERE id=$1', [m.message.id]));
  const ret = await chat.sweep({ retentionDays: 365 });
  assert.equal(ret.expired, 1);
  assert.equal(await one('SELECT id FROM shop_chat_messages WHERE id=$1', [m.message.id]), null);
});

// ════════════════════════════════════════════════════════════════════
//  درِ مشتری از راهِ HTTP — فقط وقتی ویترین (PR #244) روی سرور است
// ════════════════════════════════════════════════════════════════════

let hasStorefront = true;
try { require.resolve('../src/lib/storefront'); } catch { hasStorefront = false; }

test('درِ مشتری /api/store/:slug/chat با بلیتِ ویترین', { skip: hasStorefront ? false : 'ویترین (lib/storefront.js، PR #244) هنوز روی این شاخه نیست' }, async () => {
  const o = await shopOwner('ویترینِ چت');
  await setFlag(o.shopId, 'shop.orders', true);
  await setFlag(o.shopId, 'shop.storefront', true);
  const slug = `chat-${uniq()}`;
  const put = await h.put('/api/shop/storefront', { slug, enabled: true, title: 'ویترینِ چت', contactPhone: '0700000001' }, { token: o.token });
  assert.equal(put.status, 200, JSON.stringify(put.body));
  const start = await h.post(`/api/store/${slug}/verify/start`, { phone: '0790000031' });
  assert.equal(start.status, 200, JSON.stringify(start.body));
  const check = await h.post(`/api/store/${slug}/verify/check`, { phone: '0790000031', code: start.body.devCode });
  assert.equal(check.status, 200, JSON.stringify(check.body));
  const ticket = check.body.ticket;
  const pub = (method, path, body, t = ticket) => h.api(method, `/api/store/${slug}/chat${path}`, { body: body || null, headers: t ? { 'X-Store-Ticket': t } : {} });

  //  پرچمِ چت خاموش ⇒ ۴۰۴ و بی ردیف
  let r = await pub('POST', '/conversations', {});
  assert.equal(r.status, 404);
  assert.equal(r.body.error.code, 'chat_disabled');
  assert.equal(await count('shop_chat_conversations', o.shopId), 0);
  await setFlag(o.shopId, 'shop.chat', true);
  //  بی بلیت یا با بلیتِ دست‌خورده ⇒ ۴۰۱
  assert.equal((await pub('POST', '/conversations', {}, '')).status, 401);
  assert.equal((await pub('POST', '/conversations', {}, `${ticket.slice(0, -2)}xx`)).body.error.code, 'verification_required');

  r = await pub('POST', '/conversations', { name: 'نادر' });
  assert.equal(r.status, 201, JSON.stringify(r.body));
  const conv = r.body.conversation.id;
  const body = { clientId: cid(), body: 'سلام از ویترین' };
  const s1 = await pub('POST', `/conversations/${conv}/messages`, body);
  const s2 = await pub('POST', `/conversations/${conv}/messages`, body);
  assert.equal(s1.status, 201);
  assert.equal(s2.status, 200);
  assert.equal(s2.body.message.id, s1.body.message.id);
  const pic = await h.raw('POST', `/api/store/${slug}/chat/conversations/${conv}/media`, jpegWithGps(3), { headers: { 'X-Store-Ticket': ticket } });
  assert.equal(pic.status, 201, JSON.stringify(pic.body));
  await sendHttp(o.token, conv, { clientId: cid(), body: 'سلام از دکان' });
  const back = await pub('GET', `/conversations/${conv}/messages?after=0`);
  assert.equal(back.status, 200);
  assert.deepEqual(back.body.messages.map((m) => m.body), ['سلام از ویترین', 'سلام از دکان']);
  assert.ok(!JSON.stringify(back.body).includes(o.userId));
  assert.equal(back.headers.get('cache-control'), 'no-store');
  //  ویترینِ دیگر با همان بلیت به این گفت‌وگو نمی‌رسد
  assert.equal((await h.get(`/api/store/no-such-${uniq()}/chat/conversations/${conv}`, { headers: { 'X-Store-Ticket': ticket } })).status, 404);
});
