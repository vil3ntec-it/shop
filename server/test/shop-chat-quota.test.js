'use strict';
/**
 * CHAT-11 (v3 §۳۳.۴): تلاشِ دوبارهٔ دستگاه **سهمیه را دو بار نمی‌خورد**.
 *
 * صفِ چت (`ChatQueue`) روی اینترنتِ ضعیف همان پیام را با همان `clientId`
 * دوباره می‌فرستد و همان بایت را دوباره بارگذاری می‌کند؛ سرور هر دو را
 * «همان قبلی» می‌شناسد (`Idempotent-Replayed`، `reused`). این پرونده قفل
 * می‌کند که چنین تکراری از سقفِ ارسال/بارگذاریِ فرستنده چیزی کم نکند:
 * سقف فقط پیام و فایلِ **تازه** را می‌شمارد، و پیامِ تکراری پوشِ دوم هم نمی‌سازد.
 *
 * سقف‌ها این‌جا عمداً کوچک‌اند تا تکرار زود به ۴۲۹ برسد اگر شمرده شود.
 */
process.env.RATE_CHAT_SEND_MAX = '3';
process.env.RATE_CHAT_UPLOAD_MAX = '2';

const test = require('node:test');
const assert = require('node:assert/strict');
const h = require('./helpers');
const { query, newId, now } = require('../src/db');
const chat = require('../src/lib/shop-chat');
const push = require('../src/lib/push');

let admin = '';
let seq = 0;
const uniq = () => `${Date.now().toString(36)}${++seq}`;
const cid = () => `client-${uniq()}-0000`;
const base = '/api/shop/chat';

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
  return { token: u.accessToken, shopId: made.body.shop.id, userId: u.user.id };
}

function seg(marker, payload) {
  const head = Buffer.alloc(4);
  head.writeUInt16BE(0xff00 | marker, 0);
  head.writeUInt16BE(payload.length + 2, 2);
  return Buffer.concat([head, payload]);
}
const jpeg = (fill) => Buffer.concat([Buffer.from([0xff, 0xd8]), seg(0xdb, Buffer.alloc(65, fill)),
  seg(0xda, Buffer.from([1, 1, 0, 0, 0x3f, 0])), Buffer.alloc(100, fill), Buffer.from([0xff, 0xd9])]);

const messageCount = async (convId) =>
  Number((await query('SELECT COUNT(*)::int AS n FROM shop_chat_messages WHERE conversation_id=$1', [convId])).rows[0].n);
const mediaCount = async (convId) =>
  Number((await query('SELECT COUNT(*)::int AS n FROM shop_chat_media WHERE conversation_id=$1', [convId])).rows[0].n);

test('CHAT-11: دکان — تکرارِ همان پیام و همان فایل سهمیهٔ ارسال/بارگذاری را نمی‌خورد؛ پیام و فایلِ تازه می‌خورد', async () => {
  const o = await shopOwner('سهمیه');
  await setFlag(o.shopId, 'shop.chat', true);
  const conv = (await chat.open({ side: 'customer', shopId: o.shopId, phone: '+93790000101' }, {})).conversation.id;
  const send = (body) => h.post(`${base}/conversations/${conv}/messages`, body, { token: o.token });

  const first = { clientId: cid(), body: 'پیامِ یکم' };
  const a = await send(first);
  assert.equal(a.status, 201, JSON.stringify(a.body));
  //  شبکه پاسخ را گم کرد و صف چهار بار دیگر همان را فرستاد (سقف ۳ است)
  for (let i = 0; i < 4; i++) {
    const r = await send(first);
    assert.equal(r.status, 200, `تکرارِ ${i + 1}: ${JSON.stringify(r.body)}`);
    assert.equal(r.headers.get('idempotent-replayed'), 'true');
    assert.equal(r.body.message.id, a.body.message.id);
  }
  //  سقف فقط پیامِ تازه را می‌شمارد: دو پیامِ دیگر جا دارند، سومی نه
  assert.equal((await send({ clientId: cid(), body: 'پیامِ دوم' })).status, 201);
  assert.equal((await send({ clientId: cid(), body: 'پیامِ سوم' })).status, 201);
  const over = await send({ clientId: cid(), body: 'پیامِ چهارم' });
  assert.equal(over.status, 429, 'سقفِ پیامِ تازه همچنان سر جایش است');
  assert.ok(Number(over.headers.get('retry-after')) > 0);
  assert.equal(await messageCount(conv), 3);

  //  بارگذاری: همان بایت چهار بار (سقف ۲) ⇒ یک ردیف و بی ۴۲۹
  const put = (buf) => h.raw('POST', `${base}/conversations/${conv}/media`, buf, { token: o.token });
  const p1 = await put(jpeg(1));
  assert.equal(p1.status, 201, JSON.stringify(p1.body));
  assert.equal(p1.body.reused, false);
  for (let i = 0; i < 3; i++) {
    const r = await put(jpeg(1));
    assert.equal(r.status, 201, `بارگذاریِ دوباره ${i + 1}: ${JSON.stringify(r.body)}`);
    assert.equal(r.body.reused, true);
    assert.equal(r.body.mediaId, p1.body.mediaId);
  }
  assert.equal((await put(jpeg(2))).status, 201, 'فایلِ تازهٔ دوم در سقف جا دارد');
  assert.equal((await put(jpeg(3))).status, 429, 'فایلِ تازهٔ سوم از سقف بیرون است');
  assert.equal(await mediaCount(conv), 2);
});

test('CHAT-11: تکرارِ ردشده (۴۰۹ یا ۴۰۰) پس داده نمی‌شود — سهمیه فقط برای «همان قبلی» برمی‌گردد', async () => {
  const o = await shopOwner('سهمیهٔ خطا');
  await setFlag(o.shopId, 'shop.chat', true);
  const conv = (await chat.open({ side: 'customer', shopId: o.shopId, phone: '+93790000102' }, {})).conversation.id;
  const send = (body) => h.post(`${base}/conversations/${conv}/messages`, body, { token: o.token });
  const same = cid();
  assert.equal((await send({ clientId: same, body: 'یک' })).status, 201);
  const reused = await send({ clientId: same, body: 'متنِ دیگر با همان شناسه' });
  assert.equal(reused.status, 409);
  assert.equal(reused.body.error.code, 'client_id_reused');
  assert.equal((await send({ clientId: 'x', body: 'شناسهٔ بد' })).status, 400);
  //  سه درخواستِ شمرده‌شده (یکی پیام، دو ردشده) ⇒ چهارمی ۴۲۹
  assert.equal((await send({ clientId: cid(), body: 'تازه' })).status, 429);
  assert.equal(await messageCount(conv), 1);
});

let hasStorefront = true;
try { require.resolve('../src/lib/storefront'); } catch { hasStorefront = false; }

test('CHAT-11: مشتری از ویترین — تکرارِ پیام سهمیه نمی‌خورد و فقط یک پوش می‌سازد؛ بارگذاریِ دوباره هم', {
  skip: hasStorefront ? false : 'ویترین (lib/storefront.js) روی این شاخه نیست',
}, async () => {
  const o = await shopOwner('سهمیهٔ ویترین');
  await setFlag(o.shopId, 'shop.orders', true);
  await setFlag(o.shopId, 'shop.storefront', true);
  await setFlag(o.shopId, 'shop.chat', true);
  await query(
    `INSERT INTO push_tokens (id, app, token, provider, user_id, shop_id, status, created_at, updated_at)
     VALUES ($1,'shop',$2,'fcm',$3,$4,'active',$5,$5)`,
    [newId('ptk'), `tok-${uniq()}`, o.userId, o.shopId, now()]
  );
  const slug = `quota-${uniq()}`;
  const sf = await h.put('/api/shop/storefront', { slug, enabled: true, title: 'سهمیه', contactPhone: '0700000002' }, { token: o.token });
  assert.equal(sf.status, 200, JSON.stringify(sf.body));
  const start = await h.post(`/api/store/${slug}/verify/start`, { phone: '0790000103' });
  const check = await h.post(`/api/store/${slug}/verify/check`, { phone: '0790000103', code: start.body.devCode });
  assert.equal(check.status, 200, JSON.stringify(check.body));
  const headers = { 'X-Store-Ticket': check.body.ticket };
  const pub = (method, path, body) => h.api(method, `/api/store/${slug}/chat${path}`, { body: body || null, headers });

  const pushes = [];
  push.setDeliver(async (row, message) => { pushes.push(message); });
  try {
    //  باز کردنِ گفت‌وگو یک سهم (۱ از ۳)
    const opened = await pub('POST', '/conversations', { name: 'مشتری' });
    assert.equal(opened.status, 201, JSON.stringify(opened.body));
    const conv = opened.body.conversation.id;
    const msg = { clientId: cid(), body: 'سلام' };
    const s1 = await pub('POST', `/conversations/${conv}/messages`, msg); // ۲ از ۳
    assert.equal(s1.status, 201, JSON.stringify(s1.body));
    for (let i = 0; i < 4; i++) {
      const r = await pub('POST', `/conversations/${conv}/messages`, msg);
      assert.equal(r.status, 200, `تکرارِ ${i + 1}: ${JSON.stringify(r.body)}`);
      assert.equal(r.body.message.id, s1.body.message.id);
    }
    assert.equal(pushes.length, 1, 'یک پیام ⇒ یک پوش، هر چند بار که تکرار شود');
    assert.equal((await pub('POST', `/conversations/${conv}/messages`, { clientId: cid(), body: 'دوم' })).status, 201); // ۳ از ۳
    assert.equal((await pub('POST', `/conversations/${conv}/messages`, { clientId: cid(), body: 'سوم' })).status, 429);
    assert.equal(pushes.length, 2);
    assert.equal(await messageCount(conv), 2);

    const put = (buf) => h.raw('POST', `/api/store/${slug}/chat/conversations/${conv}/media`, buf, { headers });
    const p1 = await put(jpeg(4));
    assert.equal(p1.status, 201, JSON.stringify(p1.body));
    for (let i = 0; i < 3; i++) {
      const r = await put(jpeg(4));
      assert.equal(r.status, 201, `بارگذاریِ دوباره ${i + 1}: ${JSON.stringify(r.body)}`);
      assert.equal(r.body.reused, true);
    }
    assert.equal((await put(jpeg(5))).status, 201);
    assert.equal((await put(jpeg(6))).status, 429);
  } finally {
    push.setDeliver(null);
  }
});
