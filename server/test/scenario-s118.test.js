'use strict';
/**
 * S118 — چت و فایل (§۲۱.۱۹): «کاربرِ غیرمجاز پیام/عکسِ سفارشِ دیگر را نبیند؛ نوع/اندازهٔ جعلیِ فایل رد شود؛ retry ِ فایل
 * تکراری نسازد و URL ِ خصوصی پس از انقضا قابلِ استفاده نباشد.»
 *
 * دو بندِ اول در `shop-chat.test.js` (CHAT-09، CHAT-10) سبزند. این‌جا دو بندِ مانده، سر تا ته از راهِ HTTP ِ برنامهٔ دکان:
 *   S118.retry   بارگذاریِ دوبارهٔ همان فایل — پیاپی، هم‌زمان (پاسخِ گم‌شده و تلاشِ موازی) و پس از ارسالِ پیام — یک ردیف و
 *                یک شناسه است؛ ارسالِ دوبارهٔ پیام با همان clientId پیامِ دوم نمی‌سازد.
 *   S118.expiry  نشانیِ خصوصیِ رسانه (`…/media/:mediaId`، بی نشانیِ عمومی و بی پیوندِ امضاشده) فقط با نشستِ زنده باز می‌شود:
 *                توکنِ منقضی یا باطل ⇒ ۴۰۱؛ و پس از سرآمدنِ نگهداریِ پیام (`retentionDays` و پاک‌سازی) نه دکان و نه مشتری
 *                دیگر به آن نمی‌رسند (۴۰۴ ِ `media_gone`)، حتی با نشستِ درست.
 */
const test = require('node:test');
const assert = require('node:assert/strict');
const h = require('./helpers');
const { query, one, newId, now } = require('../src/db');
const chat = require('../src/lib/shop-chat');
const crypto = require('node:crypto');

let admin = '';
let seq = 0;
const cid = () => `client-s118-${Date.now().toString(36)}${++seq}-0000`;
const base = '/api/shop/chat';
const DAY = 24 * 3600 * 1000;

test.before(async () => {
  await h.start();
  const pw = require('../src/lib/password');
  await query(`INSERT INTO admins (id, username, name, password_hash, role, status, created_at) VALUES ($1,'admin','مدیر',$2,'superadmin','active',$3)`,
    [newId('adm'), await pw.hashPassword('Admin!12345'), now()]);
  admin = (await h.post('/api/admin/login', { username: 'admin', password: 'Admin!12345' })).body.token;
  assert.ok(admin);
});
test.after(async () => { await h.stop(); });

async function chatShop(name) {
  const u = await h.newUser(name, 'shop');
  const made = await h.post('/api/shop', { name: `دکانِ ${name}` }, { token: u.accessToken });
  assert.equal(made.status, 201, JSON.stringify(made.body));
  const r = await h.put('/api/admin/pump/live-config', { scope: made.body.shop.id, key: 'shop.chat', value: true }, { token: admin });
  assert.equal(r.status, 200, JSON.stringify(r.body));
  return { token: u.accessToken, userId: u.user.id, shopId: made.body.shop.id };
}

function seg(marker, payload) {
  const head = Buffer.alloc(4);
  head.writeUInt16BE(0xff00 | marker, 0);
  head.writeUInt16BE(payload.length + 2, 2);
  return Buffer.concat([head, payload]);
}
const jpeg = (fill) => Buffer.concat([Buffer.from([0xff, 0xd8]), seg(0xdb, Buffer.alloc(65, fill)), seg(0xda, Buffer.from([1, 1, 0, 0, 0x3f, 0])), Buffer.alloc(300, fill), Buffer.from([0xff, 0xd9])]);
const upload = (token, conv, buf) => h.raw('POST', `${base}/conversations/${conv}/media`, buf, { token, headers: { 'Content-Type': 'image/jpeg' } });
const mediaUrl = (conv, id) => `${base}/conversations/${conv}/media/${id}`;
/** همان کلیدِ جدولِ `tokens` (sha256 ِ خودِ توکن) — فقط همین نشست، نه نشست‌های دیگرِ کاربر */
const hashOf = (token) => crypto.createHash('sha256').update(token).digest('hex');
const rows = async (conv) => Number((await one('SELECT COUNT(*)::int AS n FROM shop_chat_media WHERE conversation_id=$1', [conv])).n);

test('S118.retry: همان فایل — پیاپی، هم‌زمان و پس از ارسالِ پیام — یک ردیف و یک شناسه؛ پیامِ دوباره‌فرستاده یکی است', async () => {
  const o = await chatShop('تکرارِ فایل');
  const conv = (await chat.open({ side: 'customer', shopId: o.shopId, phone: '+93790001180' }, {})).conversation.id;
  const pic = jpeg(7);

  const first = await upload(o.token, conv, pic);
  assert.equal(first.status, 201, JSON.stringify(first.body));
  assert.equal(first.body.reused, false);
  //  پاسخِ گم‌شده: برنامه همان بایت‌ها را دوباره و هم‌زمان چند بار می‌فرستد
  const burst = await Promise.all(Array.from({ length: 5 }, () => upload(o.token, conv, pic)));
  for (const r of burst) {
    assert.ok([200, 201].includes(r.status), JSON.stringify(r.body));
    assert.equal(r.body.mediaId, first.body.mediaId);
    assert.equal(r.body.reused, true);
  }
  assert.equal(await rows(conv), 1, 'یک ردیفِ رسانه');

  //  ارسالِ پیام و تکرارِ همان ارسال
  const body = { clientId: cid(), kind: 'image', body: 'عکسِ رسید', mediaId: first.body.mediaId };
  const s1 = await h.post(`${base}/conversations/${conv}/messages`, body, { token: o.token });
  assert.equal(s1.status, 201, JSON.stringify(s1.body));
  const s2 = await h.post(`${base}/conversations/${conv}/messages`, body, { token: o.token });
  assert.ok([200, 201].includes(s2.status), JSON.stringify(s2.body));
  assert.equal(s2.body.message.id, s1.body.message.id);
  const n = await one("SELECT COUNT(*)::int AS n FROM shop_chat_messages WHERE conversation_id=$1 AND kind='image'", [conv]);
  assert.equal(Number(n.n), 1, 'یک پیام');

  //  بارگذاریِ دوباره پس از ارسال هم همان ردیف است، و فایلِ دیگر ردیفِ دیگر
  assert.equal((await upload(o.token, conv, pic)).body.mediaId, first.body.mediaId);
  const other = await upload(o.token, conv, jpeg(8));
  assert.notEqual(other.body.mediaId, first.body.mediaId);
  assert.equal(await rows(conv), 2);
});

test('S118.expiry: نشانیِ خصوصیِ رسانه با توکنِ منقضی یا باطل ۴۰۱ است و پس از سرآمدنِ نگهداری برای هر دو طرف ۴۰۴', async () => {
  const o = await chatShop('انقضای نشانی');
  const customer = { side: 'customer', shopId: o.shopId, phone: '+93790001181' };
  const conv = (await chat.open(customer, {})).conversation.id;
  const pic = jpeg(9);
  const up = await upload(o.token, conv, pic);
  assert.equal(up.status, 201, JSON.stringify(up.body));
  const sent = await h.post(`${base}/conversations/${conv}/messages`, { clientId: cid(), kind: 'image', mediaId: up.body.mediaId }, { token: o.token });
  assert.equal(sent.status, 201, JSON.stringify(sent.body));
  const url = mediaUrl(conv, up.body.mediaId);

  //  نشستِ زنده ⇒ همان بایت‌ها؛ مشتری هم (پیامِ زنده به آن اشاره می‌کند)
  const ok = await h.download(url, { token: o.token });
  assert.equal(ok.status, 200);
  assert.equal(ok.buffer.length, pic.length);
  assert.equal((await chat.getMedia(customer, conv, up.body.mediaId)).id, up.body.mediaId);
  //  نشانی بی نشست هیچ‌چیز نمی‌دهد (پیوندِ عمومی نیست)
  assert.equal((await h.download(url)).status, 401);

  //  توکنِ منقضی ⇒ ۴۰۱، بی بایت
  const staff = await h.newUser('شاگردِ انقضا', 'shop');
  await query(`INSERT INTO shop_members (id, shop_id, user_id, role, status, created_at, updated_at) VALUES ($1,$2,$3,'manager','active',$4,$4)`,
    [newId('mem'), o.shopId, staff.user.id, now()]);
  assert.equal((await h.download(url, { token: staff.accessToken })).status, 200, 'عضوِ دکان با نشستِ زنده');
  await query('UPDATE tokens SET expires_at=$2 WHERE token_hash=$1', [hashOf(staff.accessToken), now() - 1000]);
  const expired = await h.download(url, { token: staff.accessToken });
  assert.equal(expired.status, 401);
  assert.notEqual(expired.buffer.length, pic.length);
  //  توکنِ باطل‌شده (خروج از همهٔ دستگاه‌ها) ⇒ ۴۰۱
  await query('UPDATE tokens SET revoked_at=$2 WHERE token_hash=$1', [hashOf(o.token), now()]);
  assert.equal((await h.download(url, { token: o.token })).status, 401);
  const relog = await h.newUser('صاحبِ تازه', 'shop');
  await query(`INSERT INTO shop_members (id, shop_id, user_id, role, status, created_at, updated_at) VALUES ($1,$2,$3,'owner','active',$4,$4)`,
    [newId('mem'), o.shopId, relog.user.id, now()]);
  assert.equal((await h.download(url, { token: relog.accessToken })).status, 200, 'نشستِ تازه دوباره می‌بیند');

  //  نگهداری سر آمد (دکان ۳۰ روز نگه می‌دارد؛ ۳۱ روز بعد پاک‌سازی): پیام و رسانه از سرور می‌روند
  const swept = await chat.sweep({ at: now() + 31 * DAY, retentionDays: 30 });
  assert.ok(swept.expired >= 1 && swept.orphans >= 1, JSON.stringify(swept));
  assert.equal(await rows(conv), 0);
  const gone = await h.get(url, { token: relog.accessToken });
  assert.equal(gone.status, 404);
  assert.equal(gone.body.error.code, 'media_gone');
  await assert.rejects(chat.getMedia(customer, conv, up.body.mediaId), (e) => e.code === 'media_gone');
});

test('S118.customer: درِ مشتری (/api/store/:slug/chat) با بلیتِ منقضی، دست‌خورده یا لغوشده ۴۰۱ است و نه پیام می‌دهد نه رسانه', async () => {
  const sf = require('../src/lib/storefront');
  const customers = require('../src/lib/store-customers');
  const o = await chatShop('بلیتِ منقضی');
  for (const key of ['shop.orders', 'shop.storefront']) {
    const f = await h.put('/api/admin/pump/live-config', { scope: o.shopId, key, value: true }, { token: admin });
    assert.equal(f.status, 200, JSON.stringify(f.body));
  }
  const slug = `s118-${Date.now().toString(36)}`;
  const put = await h.put('/api/shop/storefront', { slug, enabled: true, title: 'ویترینِ S118', contactPhone: '0700000118' }, { token: o.token });
  assert.equal(put.status, 200, JSON.stringify(put.body));
  const start = await h.post(`/api/store/${slug}/verify/start`, { phone: '0790001182' });
  assert.equal(start.status, 200, JSON.stringify(start.body));
  const check = await h.post(`/api/store/${slug}/verify/check`, { phone: '0790001182', code: start.body.devCode });
  assert.equal(check.status, 200, JSON.stringify(check.body));
  const ticket = check.body.ticket;
  const phone = sf.parseTicket(ticket).phone;
  const door = `/api/store/${slug}/chat`;
  const pub = (method, path, t, body = null) => h.api(method, `${door}${path}`, { body, headers: t ? { 'X-Store-Ticket': t } : {} });
  const bytes = async (path, t) => {
    const res = await fetch(`${h.base()}${door}${path}`, { headers: t ? { 'X-Store-Ticket': t } : {} });
    return { status: res.status, buffer: Buffer.from(await res.arrayBuffer()) };
  };

  //  بلیتِ زنده: گفت‌وگو، عکس و پیام از همین در
  const opened = await pub('POST', '/conversations', ticket, { name: 'نادر' });
  assert.equal(opened.status, 201, JSON.stringify(opened.body));
  const conv = opened.body.conversation.id;
  const pic = jpeg(7);
  const up = await h.raw('POST', `${door}/conversations/${conv}/media`, pic, { headers: { 'X-Store-Ticket': ticket, 'Content-Type': 'image/jpeg' } });
  assert.equal(up.status, 201, JSON.stringify(up.body));
  const sent = await pub('POST', `/conversations/${conv}/messages`, ticket, { clientId: cid(), kind: 'image', mediaId: up.body.mediaId });
  assert.equal(sent.status, 201, JSON.stringify(sent.body));
  const media = `/conversations/${conv}/media/${up.body.mediaId}`;
  assert.equal((await bytes(media, ticket)).buffer.length, pic.length);

  //  همان شماره و همان نسل، فقط زمانِ گذشته ⇒ تنها فرقِ این بلیت با بلیتِ زنده «منقضی بودن» است
  const ttl = require('../src/config').storefront.ticketTtlMs;
  const gen = await customers.ticketGen(phone);
  const expired = sf.ticketOf(phone, now() - ttl - 60_000, gen).ticket;
  const fresh = sf.ticketOf(phone, now(), gen).ticket;
  assert.equal((await pub('GET', '/conversations', fresh)).status, 200, 'بلیتِ ساختگیِ تازه با همان کلید پذیرفته است');
  const before = Number((await one('SELECT COUNT(*)::int AS n FROM shop_chat_messages WHERE conversation_id=$1', [conv])).n);
  for (const t of [expired, h.tampered(ticket), '']) {
    for (const [method, path, body] of [
      ['GET', '/conversations', null], ['GET', `/conversations/${conv}`, null], ['GET', `/conversations/${conv}/messages?after=0`, null],
      ['POST', `/conversations/${conv}/messages`, { clientId: cid(), body: 'پس از انقضا' }],
    ]) {
      const r = await pub(method, path, t, body);
      assert.equal(r.status, 401, `${method} ${path}`);
      assert.equal(r.body.error.code, 'verification_required');
      assert.equal(JSON.stringify(r.body).includes('پس از انقضا'), false);
    }
    const m = await bytes(media, t);
    assert.equal(m.status, 401);
    assert.notEqual(m.buffer.length, pic.length);
  }
  assert.equal(Number((await one('SELECT COUNT(*)::int AS n FROM shop_chat_messages WHERE conversation_id=$1', [conv])).n), before);

  //  «خروج از همه‌جا» (نسلِ تازه) ⇒ همان بلیتی که تا الان زنده بود هم ۴۰۱
  await customers.revokeTickets(phone);
  assert.equal((await pub('GET', `/conversations/${conv}/messages?after=0`, ticket)).status, 401);
  assert.equal((await bytes(media, ticket)).status, 401);
});
