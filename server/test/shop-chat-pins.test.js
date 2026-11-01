'use strict';
/**
 * CHAT-14 (v3 §۳۳.۱ و §۳۳.۴): سنجاقِ پیام فقط در محدودهٔ مجاز.
 *
 *   ۱) هر طرف سنجاقِ خودش: سنجاقِ دکان را کارمندِ دیگرِ همان دکان می‌بیند و
 *      مشتری نه؛ سنجاقِ مشتری را دکان نمی‌بیند. شناسهٔ کارمند به مشتری نمی‌رسد.
 *   ۲) محدوده: پیامِ گفت‌وگوی دیگر، دکانِ دیگر، مشتریِ دیگر، پاک‌شده و «حذف برای
 *      من» ⇒ همان ۴۰۴؛ سقفِ ۵ سنجاق برای هر طرف ⇒ ۴۰۹؛ تکرار بی‌اثر.
 *   ۳) «حذف برای همه» سنجاقِ هر دو طرف را برمی‌دارد؛ «حذف برای من» ِ مشتری سنجاقِ خودش را.
 *   ۴) دستگاهِ دیگرِ دکان با long-poll همان دم می‌فهمد؛ پرچمِ خاموش ⇒ ۴۰۴.
 *   ۵) مهاجرتِ ۱۴۱ برگشت‌پذیر است (همان خطِ «برگشت» در خودِ فایل).
 */
const fs = require('node:fs');
const path = require('node:path');
const test = require('node:test');
const assert = require('node:assert/strict');
const h = require('./helpers');
const { query, one, newId, now } = require('../src/db');
const chat = require('../src/lib/shop-chat');

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
test.after(() => h.stop());

async function setFlag(scope, key, value) {
  const r = await h.put('/api/admin/pump/live-config', { scope, key, value }, { token: admin });
  assert.equal(r.status, 200, JSON.stringify(r.body));
}

async function chatShop(name, { flag = true } = {}) {
  const u = await h.newUser(name, 'shop');
  const made = await h.post('/api/shop', { name: `دکانِ ${name}` }, { token: u.accessToken });
  assert.equal(made.status, 201, JSON.stringify(made.body));
  const shopId = made.body.shop.id;
  if (flag) await setFlag(shopId, 'shop.chat', true);
  return { token: u.accessToken, shopId, userId: u.user.id };
}

async function member(shopId, role, name) {
  const u = await h.newUser(name, 'shop');
  await query(
    `INSERT INTO shop_members (id, shop_id, user_id, role, status, created_at, updated_at)
     VALUES ($1,$2,$3,$4,'active',$5,$5)`,
    [newId('mem'), shopId, u.user.id, role, now()]
  );
  return { token: u.accessToken, userId: u.user.id };
}

const customer = (shopId, phone) => ({ side: 'customer', shopId, phone });
const pinUrl = (conv, mid) => `${base}/conversations/${conv}/pins/${mid}`;
const pinsOf = (r) => r.body.conversation.pinned.map((p) => p.messageId);

test('CHAT-14: هر طرف سنجاقِ خودش — کارمندِ دیگرِ همان دکان می‌بیند، مشتری نه؛ سنجاقِ مشتری را دکان نمی‌بیند', async () => {
  const o = await chatShop('سنجاق');
  const staff = await member(o.shopId, 'staff', 'شاگردِ سنجاق');
  const me = customer(o.shopId, '+93790000301');
  const conv = (await chat.open(me, {})).conversation.id;
  const m1 = (await chat.send(me, conv, { clientId: cid(), body: 'نشانی: کوچهٔ سوم' })).message;
  const m2 = (await h.post(`${base}/conversations/${conv}/messages`, { clientId: cid(), body: 'فردا ساعتِ ده' }, { token: o.token })).body.message;

  const put = await h.put(pinUrl(conv, m1.id), {}, { token: o.token });
  assert.equal(put.status, 200, JSON.stringify(put.body));
  assert.deepEqual(put.body.conversation.pinned, [{ messageId: m1.id, seq: 1, pinnedAt: put.body.conversation.pinned[0].pinnedAt }]);
  assert.ok(put.body.conversation.pinned[0].pinnedAt > 0);
  //  کارمندِ دیگرِ همان دکان همان سنجاق را می‌بیند (فهرست، گفت‌وگو و تغییرها)
  const byStaff = await h.get(`${base}/conversations/${conv}`, { token: staff.token });
  assert.deepEqual(pinsOf(byStaff), [m1.id]);
  const listed = await h.get(`${base}/conversations`, { token: staff.token });
  assert.deepEqual(listed.body.conversations.find((c) => c.id === conv).pinned.map((p) => p.messageId), [m1.id]);
  assert.deepEqual((await chat.changes({ side: 'shop', shopId: o.shopId, userId: staff.userId, role: 'staff' }, conv, { after: 0 }))
    .conversation.pinned.map((p) => p.messageId), [m1.id]);
  //  مشتری سنجاقِ دکان را نمی‌بیند و هیچ نشانی از کارمند نمی‌گیرد
  const seen = await chat.changes(me, conv, { after: 0 });
  assert.deepEqual(seen.conversation.pinned, []);
  assert.ok(!JSON.stringify(seen).includes(o.userId));

  //  مشتری پیامِ دکان را برای خودش سنجاق می‌کند؛ دکان آن را نمی‌بیند
  const mine = await chat.pin(me, conv, m2.id, true);
  assert.deepEqual(mine.pinned.map((p) => p.messageId), [m2.id]);
  assert.deepEqual(pinsOf(await h.get(`${base}/conversations/${conv}`, { token: o.token })), [m1.id]);
  //  ردیفِ سنجاقِ مشتری کارمند ندارد
  const row = await one("SELECT pinned_by_user_id FROM shop_chat_pins WHERE conversation_id=$1 AND side='customer'", [conv]);
  assert.equal(row.pinned_by_user_id, '');

  //  برداشتن: فقط سنجاقِ همین طرف؛ تکرار بی‌اثر
  const del = await h.del(pinUrl(conv, m1.id), { token: staff.token });
  assert.equal(del.status, 200, JSON.stringify(del.body));
  assert.deepEqual(pinsOf(del), []);
  assert.equal((await h.del(pinUrl(conv, m1.id), { token: o.token })).status, 200);
  assert.deepEqual((await chat.viewOf(me, conv)).pinned.map((p) => p.messageId), [m2.id], 'سنجاقِ مشتری سرِ جایش ماند');
});

test('CHAT-14: محدودهٔ مجاز — پیامِ گفت‌وگو/دکان/مشتریِ دیگر، پاک‌شده و «حذف برای من» ⇒ ۴۰۴؛ سقفِ ۵ ⇒ ۴۰۹؛ تکرار بی‌اثر', async () => {
  const a = await chatShop('سنجاقِ الف');
  const b = await chatShop('سنجاقِ ب');
  const ali = customer(a.shopId, '+93790000311');
  const reza = customer(a.shopId, '+93790000312');
  const c1 = (await chat.open(ali, {})).conversation.id;
  const c2 = (await chat.open(reza, {})).conversation.id;
  const inC1 = [];
  for (let k = 0; k < 7; k++) inC1.push((await chat.send(ali, c1, { clientId: cid(), body: `پیامِ ${k}` })).message.id);
  const inC2 = (await chat.send(reza, c2, { clientId: cid(), body: 'رضا' })).message.id;
  const bConv = (await chat.open(customer(b.shopId, '+93790000313'), {})).conversation.id;
  const inB = (await chat.send(customer(b.shopId, '+93790000313'), bConv, { clientId: cid(), body: 'دکانِ ب' })).message.id;

  const status = async (token, conv, mid) => {
    const r = await h.put(pinUrl(conv, mid), {}, { token });
    return `${r.status}:${r.body?.error?.code || ''}`;
  };
  //  پیامِ گفت‌وگوی دیگرِ همان دکان، پیامِ دکانِ دیگر، شناسهٔ ساختگی/بدشکل
  assert.equal(await status(a.token, c1, inC2), '404:message_not_found');
  assert.equal(await status(a.token, c1, inB), '404:message_not_found');
  assert.equal(await status(a.token, c1, 'scm_nope'), '404:message_not_found');
  assert.equal(await status(a.token, c1, 'bad id!'), '404:message_not_found');
  //  گفت‌وگوی دکانِ دیگر با توکنِ «الف» و برعکس
  assert.equal(await status(a.token, bConv, inB), '404:conversation_not_found');
  assert.equal(await status(b.token, c1, inC1[0]), '404:conversation_not_found');
  //  مشتریِ دیگر با همان شناسه‌ها
  await assert.rejects(chat.pin(reza, c1, inC1[0], true), (e) => e.status === 404 && e.code === 'conversation_not_found');
  await assert.rejects(chat.pin(ali, c1, inC2, true), (e) => e.status === 404 && e.code === 'message_not_found');
  //  پاک‌شده برای همه و «حذف برای من» ِ همین بیننده
  await chat.remove(ali, c1, inC1[5], 'all');
  assert.equal(await status(a.token, c1, inC1[5]), '404:message_not_found');
  await h.del(`${base}/conversations/${c1}/messages/${inC1[6]}?for=me`, { token: a.token });
  assert.equal(await status(a.token, c1, inC1[6]), '404:message_not_found');
  const pinRows = async () => Number((await one('SELECT COUNT(*)::int AS n FROM shop_chat_pins WHERE shop_id IN ($1,$2)', [a.shopId, b.shopId])).n);
  assert.equal(await pinRows(), 0, 'هیچ ردیفی از تلاش‌های بیرونِ محدوده نماند');

  //  سقف و تکرار
  for (let k = 0; k < chat.MAX_PINS; k++) assert.equal(await status(a.token, c1, inC1[k]), '200:');
  assert.equal(await status(a.token, c1, inC1[0]), '200:', 'سنجاقِ دوباره بی‌اثر است، نه خطا');
  //  پیامی تازه برای ششمین سنجاق
  const sixth = (await chat.send(ali, c1, { clientId: cid(), body: 'ششم' })).message.id;
  assert.equal(await status(a.token, c1, sixth), '409:pin_limit');
  const view = await h.get(`${base}/conversations/${c1}`, { token: a.token });
  assert.equal(view.body.conversation.pinned.length, chat.MAX_PINS);
  //  سقفِ هر طرف جداست: مشتری هنوز جا دارد
  assert.equal((await chat.pin(ali, c1, sixth, true)).pinned.length, 1);
  //  سنجاقِ دکان: تازه‌ترین اول
  const at = view.body.conversation.pinned.map((p) => p.pinnedAt);
  assert.ok(at.every((t, i) => i === 0 || at[i - 1] >= t), `ترتیبِ سنجاق‌ها: ${at}`);
  assert.equal(await pinRows(), chat.MAX_PINS + 1);
});

test('CHAT-14: «حذف برای همه» سنجاقِ هر دو طرف را برمی‌دارد؛ «حذف برای من» ِ مشتری سنجاقِ خودش را؛ دستگاهِ دیگرِ دکان با long-poll می‌فهمد', async () => {
  const o = await chatShop('سنجاق و حذف');
  const me = customer(o.shopId, '+93790000321');
  const conv = (await chat.open(me, {})).conversation.id;
  const m1 = (await chat.send(me, conv, { clientId: cid(), body: 'یک' })).message.id;
  const m2 = (await chat.send(me, conv, { clientId: cid(), body: 'دو' })).message.id;
  const shop = { side: 'shop', shopId: o.shopId, userId: o.userId, role: 'owner' };

  //  دستگاهِ دیگرِ دکان منتظر است؛ سنجاق آن را همان دم بیدار می‌کند
  const cursor = (await chat.changes(shop, conv, { after: 0 })).cursor;
  const t0 = Date.now();
  const waiting = chat.changes(shop, conv, { after: cursor, wait: 5000 });
  await new Promise((r) => setTimeout(r, 30));
  await chat.pin(shop, conv, m1, true);
  const woke = await waiting;
  assert.ok(Date.now() - t0 < 2500, 'long-poll با سنجاق بیدار شد');
  assert.deepEqual(woke.conversation.pinned.map((p) => p.messageId), [m1]);

  await chat.pin(me, conv, m1, true);
  await chat.pin(me, conv, m2, true);
  //  مشتری پیامش را برای همه پاک می‌کند ⇒ سنجاقِ دکان و خودش می‌رود
  await chat.remove(me, conv, m1, 'all');
  assert.deepEqual((await chat.viewOf(shop, conv)).pinned, []);
  assert.deepEqual((await chat.viewOf(me, conv)).pinned.map((p) => p.messageId), [m2]);
  //  «حذف برای من» ِ مشتری ⇒ سنجاقِ خودش هم
  await chat.remove(me, conv, m2, 'me');
  assert.deepEqual((await chat.viewOf(me, conv)).pinned, []);
  assert.equal(Number((await one('SELECT COUNT(*)::int AS n FROM shop_chat_pins WHERE conversation_id=$1', [conv])).n), 0);
  //  پاک‌سازیِ نگه‌داری (اگر روزی روشن شود) سنجاق را هم می‌برد
  await chat.pin(shop, conv, m2, true);
  await chat.sweep({ at: now() + 10 * 86_400_000, retentionDays: 1 });
  assert.equal(Number((await one('SELECT COUNT(*)::int AS n FROM shop_chat_pins WHERE conversation_id=$1', [conv])).n), 0);
});

test('CHAT-14: پرچمِ خاموش ⇒ درِ سنجاق ۴۰۴ و بی ردیف', async () => {
  const o = await chatShop('سنجاقِ خاموش', { flag: false });
  const me = customer(o.shopId, '+93790000331');
  const conv = (await chat.open(me, {})).conversation.id;
  const mid = (await chat.send(me, conv, { clientId: cid(), body: 'سلام' })).message.id;
  for (const r of [await h.put(pinUrl(conv, mid), {}, { token: o.token }), await h.del(pinUrl(conv, mid), { token: o.token })]) {
    assert.equal(r.status, 404);
    assert.equal(r.body.error.code, 'chat_disabled');
  }
  assert.equal(Number((await one('SELECT COUNT(*)::int AS n FROM shop_chat_pins WHERE shop_id=$1', [o.shopId])).n), 0);
});

let hasStorefront = true;
try { require.resolve('../src/lib/storefront'); } catch { hasStorefront = false; }

test('CHAT-14: مشتری از ویترین سنجاقِ خودش را می‌گذارد و برمی‌دارد؛ دکان نمی‌بیند', {
  skip: hasStorefront ? false : 'ویترین (lib/storefront.js) روی این شاخه نیست',
}, async () => {
  const o = await chatShop('سنجاقِ ویترین');
  await setFlag(o.shopId, 'shop.orders', true);
  await setFlag(o.shopId, 'shop.storefront', true);
  const slug = `pin-${uniq()}`;
  const sf = await h.put('/api/shop/storefront', { slug, enabled: true, title: 'سنجاق', contactPhone: '0700000003' }, { token: o.token });
  assert.equal(sf.status, 200, JSON.stringify(sf.body));
  const start = await h.post(`/api/store/${slug}/verify/start`, { phone: '0790000341' });
  const check = await h.post(`/api/store/${slug}/verify/check`, { phone: '0790000341', code: start.body.devCode });
  assert.equal(check.status, 200, JSON.stringify(check.body));
  const headers = { 'X-Store-Ticket': check.body.ticket };
  const pub = (method, p, body) => h.api(method, `/api/store/${slug}/chat${p}`, { body: body || null, headers });
  const conv = (await pub('POST', '/conversations', { name: 'مشتری' })).body.conversation.id;
  const mid = (await pub('POST', `/conversations/${conv}/messages`, { clientId: cid(), body: 'سفارشِ من' })).body.message.id;

  const put = await pub('PUT', `/conversations/${conv}/pins/${mid}`);
  assert.equal(put.status, 200, JSON.stringify(put.body));
  assert.deepEqual(pinsOf(put), [mid]);
  assert.deepEqual(pinsOf(await h.get(`${base}/conversations/${conv}`, { token: o.token })), []);
  const del = await pub('DELETE', `/conversations/${conv}/pins/${mid}`);
  assert.equal(del.status, 200);
  assert.deepEqual(pinsOf(del), []);
  //  بی بلیت ⇒ ۴۰۱
  const anon = await h.api('PUT', `/api/store/${slug}/chat/conversations/${conv}/pins/${mid}`);
  assert.equal(anon.status, 401);
});

test('مهاجرتِ ۱۴۱: برگشتِ نوشته‌شده در خودِ فایل جدول را برمی‌دارد و اجرای دوبارهٔ فایل همان را می‌سازد', async () => {
  const sql = fs.readFileSync(path.join(__dirname, '..', 'migrations', '141_shop_chat_pins.sql'), 'utf8');
  const back = sql.split('\n').filter((l) => /^--\s+DROP TABLE IF EXISTS shop_chat_pins;/.test(l)).map((l) => l.replace(/^--\s+/, ''));
  assert.equal(back.length, 1, 'خطِ برگشت در فایل هست');
  const exists = async () => !!(await one("SELECT 1 AS x FROM information_schema.tables WHERE table_schema='public' AND table_name='shop_chat_pins'"));
  assert.equal(await exists(), true);
  await query(back[0]);
  assert.equal(await exists(), false);
  await query(sql);
  await query(sql);
  assert.equal(await exists(), true, 'فایل دوباره‌اجراشدنی است (IF NOT EXISTS)');
});
