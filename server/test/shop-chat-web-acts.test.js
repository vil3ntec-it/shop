'use strict';
/**
 * پاسخ، ویرایش، سنجاق و جست‌وجوی مشتری روی درهای ویترین (`/api/store/:slug/chat`، CHAT-14 در وب؛ `store/chat-acts.js`).
 *
 * آن‌چه این پرونده قفل می‌کند (همه از راهِ HTTP و بلیتِ واقعیِ ویترین):
 *   • `editUntil` ِ ساعتِ سرور (`editWindowMs`) فقط روی پیامِ زندهٔ خودِ مشتری — مثلِ `deleteUntil`؛ نه پیامِ دکان، نه
 *     نمای دکان، نه سنگِ قبر؛ صفحه دکمهٔ «ویرایش» را از همین می‌سازد و سرور باز هم می‌سنجد؛
 *   • ویرایشِ پیامِ دیگری ⇒ ۴۰۳ `not_author`؛ ویرایشِ دیرهنگام ⇒ ۴۰۳ `edit_window_passed` و متن دست نمی‌خورد؛
 *   • مشتریِ دیگر با شناسهٔ گفت‌وگوی مشتریِ اول ⇒ ۴۰۴، بی پیام و بی نشت؛
 *   • پاسخ به پیامِ گفت‌وگوی دیگری ⇒ ۴۰۰ `bad_reply`؛
 *   • جست‌وجو فقط پیام‌های همین دکان و همین مشتری؛
 *   • سنجاقِ پیامِ دیگری ⇒ ۴۰۴؛ پاسخِ سنجاق متنِ پیام ندارد (فقط شناسه، seq و زمان).
 */
const test = require('node:test');
const assert = require('node:assert/strict');
const h = require('./helpers');
const { query, one, newId, now } = require('../src/db');
const { resetAll } = require('../src/middleware/ratelimit');

let admin = '';
let seq = 0;
const uniq = () => `${Date.now().toString(36)}${++seq}`;
const cid = () => `client-${uniq()}-0000`;
const errOf = (r) => r.body?.error?.code;
const as = (t) => ({ headers: { 'X-Store-Ticket': t } });
//  دکانِ «S» با دو مشتری (T1/C1 و T2/C2)
let S;
let T1;
let T2;
let C1;
let C2;

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
  S = await store('کارهای وب');
  T1 = await ticketFor(S, '0795510001');
  T2 = await ticketFor(S, '0795510002');
  C1 = await openConv(S, T1);
  C2 = await openConv(S, T2);
});
test.after(() => h.stop());

async function setFlag(scope, key, value) {
  const r = await h.put('/api/admin/pump/live-config', { scope, key, value }, { token: admin });
  assert.equal(r.status, 200, JSON.stringify(r.body));
}

/** دکانِ تازه با ویترین و چتِ روشن */
async function store(name) {
  const u = await h.newUser(name, 'shop');
  const made = await h.post('/api/shop', { name: `دکانِ ${name}` }, { token: u.accessToken });
  assert.equal(made.status, 201, JSON.stringify(made.body));
  const shopId = made.body.shop.id;
  for (const key of ['shop.orders', 'shop.storefront', 'shop.chat']) await setFlag(shopId, key, true);
  const slug = `acts-${uniq()}`;
  const sf = await h.put('/api/shop/storefront', { slug, enabled: true, title: `ویترینِ ${name}`, contactPhone: '0700000005' }, { token: u.accessToken });
  assert.equal(sf.status, 200, JSON.stringify(sf.body));
  return { token: u.accessToken, shopId, slug, base: `/api/store/${slug}/chat` };
}

async function ticketFor(s, phone) {
  resetAll();
  const st = await h.post(`/api/store/${s.slug}/verify/start`, { phone });
  assert.equal(st.status, 200, JSON.stringify(st.body));
  const c = await h.post(`/api/store/${s.slug}/verify/check`, { phone, code: st.body.devCode });
  assert.equal(c.status, 200, JSON.stringify(c.body));
  return c.body.ticket;
}

async function openConv(s, t) {
  const r = await h.post(`${s.base}/conversations`, {}, as(t));
  assert.ok([200, 201].includes(r.status), JSON.stringify(r.body));
  return r.body.conversation.id;
}

async function say(s, t, conv, body, extra = {}) {
  const r = await h.post(`${s.base}/conversations/${conv}/messages`, { clientId: cid(), body, ...extra }, as(t));
  assert.equal(r.status, 201, JSON.stringify(r.body));
  return r.body.message;
}

async function shopSay(s, conv, body) {
  const r = await h.post(`/api/shop/chat/conversations/${conv}/messages`, { clientId: cid(), body }, { token: s.token });
  assert.equal(r.status, 201, JSON.stringify(r.body));
  return r.body.message;
}

test('editUntil ِ ساعتِ سرور فقط روی پیامِ زندهٔ خودِ مشتری؛ نه پیامِ دکان، نه نمای دکان، نه سنگِ قبر', async () => {
  const config = require('../src/config');
  const mine = await say(S, T1, C1, 'پیامی برای ویرایش');
  assert.equal(mine.editUntil, mine.createdAt + config.shopChat.editWindowMs, 'پاسخِ فرستادن');
  assert.equal(mine.deleteUntil, mine.createdAt + config.shopChat.deleteWindowMs);
  const fromShop = await shopSay(S, C1, 'پیامِ دکان');
  assert.equal(fromShop.editUntil, undefined, 'دکان مهلت را از /config دارد، نه از پیام');

  const seen = (await h.get(`${S.base}/conversations/${C1}/messages?after=0`, as(T1))).body.messages;
  assert.equal(seen.find((m) => m.id === mine.id).editUntil, mine.editUntil, 'خواندنِ دوباره همان مهلت');
  assert.equal(seen.find((m) => m.id === fromShop.id).editUntil, undefined, 'پیامِ دکان برای مشتری مهلتِ ویرایش ندارد');
  const shopSeen = (await h.get(`/api/shop/chat/conversations/${C1}/messages?after=0`, { token: S.token })).body.messages;
  assert.equal(shopSeen.find((m) => m.id === mine.id).editUntil, undefined, 'دکان مهلتِ مشتری را نمی‌بیند');

  //  ویرایش ⇒ همان مهلت (از زمانِ نوشتن، نه از ویرایش)
  const ed = await h.patch(`${S.base}/conversations/${C1}/messages/${mine.id}`, { body: 'پیامِ ویرایش‌شده' }, as(T1));
  assert.equal(ed.status, 200, JSON.stringify(ed.body));
  assert.equal(ed.body.message.body, 'پیامِ ویرایش‌شده');
  assert.equal(ed.body.message.editUntil, mine.editUntil);
  assert.ok(ed.body.message.editedAt >= mine.createdAt);

  //  سنگِ قبر مهلت ندارد
  const gone = await h.del(`${S.base}/conversations/${C1}/messages/${mine.id}?for=all`, as(T1));
  assert.equal(gone.status, 200, JSON.stringify(gone.body));
  assert.equal(gone.body.message.deleted, true);
  assert.equal(gone.body.message.editUntil, undefined);
});

test('ویرایشِ پیامِ دیگری ⇒ ۴۰۳ not_author؛ ویرایشِ دیرهنگام ⇒ ۴۰۳ edit_window_passed و متن سرِ جایش', async () => {
  const config = require('../src/config');
  const fromShop = await shopSay(S, C1, 'پیامِ دکان که مشتری ویرایش نمی‌کند');
  const no = await h.patch(`${S.base}/conversations/${C1}/messages/${fromShop.id}`, { body: 'دست‌کاری' }, as(T1));
  assert.deepEqual([no.status, errOf(no)], [403, 'not_author']);
  assert.equal((await one('SELECT body FROM shop_chat_messages WHERE id=$1', [fromShop.id])).body, 'پیامِ دکان که مشتری ویرایش نمی‌کند');

  const old = await say(S, T1, C1, 'پیامِ کهنه');
  await query('UPDATE shop_chat_messages SET created_at=$2 WHERE id=$1', [old.id, now() - config.shopChat.editWindowMs - 60_000]);
  const view = (await h.get(`${S.base}/conversations/${C1}/messages?after=0`, as(T1))).body.messages.find((m) => m.id === old.id);
  assert.ok(view.editUntil < Date.now(), 'مهلتِ گذشته از خودِ پیام پیداست');
  const late = await h.patch(`${S.base}/conversations/${C1}/messages/${old.id}`, { body: 'دیر' }, as(T1));
  assert.deepEqual([late.status, errOf(late)], [403, 'edit_window_passed']);
  assert.equal((await one('SELECT body, edited_at FROM shop_chat_messages WHERE id=$1', [old.id])).body, 'پیامِ کهنه');
});

test('مشتریِ دیگر با شناسهٔ گفت‌وگوی مشتریِ اول ⇒ ۴۰۴ بی نوشتن و بی نشت', async () => {
  const before = Number((await one('SELECT COUNT(*)::int AS n FROM shop_chat_messages WHERE conversation_id=$1', [C1])).n);
  const r = await h.post(`${S.base}/conversations/${C1}/messages`, { clientId: cid(), body: 'نفوذ' }, as(T2));
  assert.deepEqual([r.status, errOf(r)], [404, 'conversation_not_found']);
  for (const p of [`/conversations/${C1}`, `/conversations/${C1}/messages?after=0`]) {
    const g = await h.get(`${S.base}${p}`, as(T2));
    assert.deepEqual([g.status, errOf(g)], [404, 'conversation_not_found'], p);
    assert.ok(!JSON.stringify(g.body).includes('0795510001'), p);
  }
  const fake = await h.post(`${S.base}/conversations/${C1.slice(0, -1)}x/messages`, { clientId: cid(), body: 'نفوذ' }, as(T2));
  assert.deepEqual(r.body, fake.body, 'شناسهٔ واقعی و ساختگی همان پاسخ');
  const after = Number((await one('SELECT COUNT(*)::int AS n FROM shop_chat_messages WHERE conversation_id=$1', [C1])).n);
  assert.equal(after, before);
});

test('پاسخ به پیامِ گفت‌وگوی دیگری ⇒ ۴۰۰ bad_reply؛ پاسخ به پیامِ همین رشته ⇒ replyTo', async () => {
  const theirs = await say(S, T1, C1, 'پیامِ مشتریِ اول');
  const r = await h.post(`${S.base}/conversations/${C2}/messages`, { clientId: cid(), body: 'پاسخِ ناروا', replyTo: theirs.id }, as(T2));
  assert.deepEqual([r.status, errOf(r)], [400, 'bad_reply']);
  assert.ok(!JSON.stringify(r.body).includes('پیامِ مشتریِ اول'));
  assert.equal(Number((await one("SELECT COUNT(*)::int AS n FROM shop_chat_messages WHERE body='پاسخِ ناروا'")).n), 0);

  const fromShop = await shopSay(S, C2, 'سلام از دکان');
  const okr = await say(S, T2, C2, 'پاسخِ درست', { replyTo: fromShop.id });
  assert.equal(okr.replyTo, fromShop.id);
});

test('جست‌وجو فقط پیام‌های همین دکان و همین مشتری', async () => {
  const other = await store('دکانِ دیگر');
  const tOther = await ticketFor(other, '0795510001');   //  همان شماره در دکانِ دیگر
  const cOther = await openConv(other, tOther);
  await say(other, tOther, cOther, 'زعفرانِ دکانِ دیگر');
  await say(S, T2, C2, 'زعفرانِ مشتریِ دوم');
  const mine = await say(S, T1, C1, 'زعفرانِ مشتریِ اول');
  await shopSay(S, C1, 'زعفرانِ دکان برای اول');

  const r = await h.get(`${S.base}/search?q=${encodeURIComponent('زعفران')}`, as(T1));
  assert.equal(r.status, 200, JSON.stringify(r.body));
  const bodies = r.body.results.map((x) => x.body).sort();
  assert.deepEqual(bodies, ['زعفرانِ دکان برای اول', 'زعفرانِ مشتریِ اول'].sort());
  assert.ok(r.body.results.every((x) => x.conversationId === C1));
  assert.ok(r.body.results.every((x) => x.senderUserId === undefined), 'مشتری شناسهٔ کارمند نمی‌بیند');
  assert.ok(r.body.results.some((x) => x.id === mine.id));

  //  همان شماره در دکانِ دیگر فقط پیامِ آن دکان
  const o = await h.get(`${other.base}/search?q=${encodeURIComponent('زعفران')}`, as(tOther));
  assert.deepEqual(o.body.results.map((x) => x.body), ['زعفرانِ دکانِ دیگر']);
  //  بلیتِ دکانِ دیگر روی این ویترین همان شمارهٔ تأییدشده است ⇒ باز هم فقط محدودهٔ این دکان
  const cross = await h.get(`${S.base}/search?q=${encodeURIComponent('زعفران')}`, as(tOther));
  assert.ok(cross.status === 401 || cross.body.results.every((x) => x.conversationId === C1), JSON.stringify(cross.body));
});

test('سنجاقِ پیامِ دیگری ⇒ ۴۰۴؛ پاسخِ سنجاق متنِ پیام ندارد', async () => {
  const theirs = await say(S, T1, C1, 'رازِ مشتریِ اول برای سنجاق');
  const no = await h.put(`${S.base}/conversations/${C2}/pins/${theirs.id}`, {}, as(T2));
  assert.deepEqual([no.status, errOf(no)], [404, 'message_not_found']);
  const no2 = await h.put(`${S.base}/conversations/${C1}/pins/${theirs.id}`, {}, as(T2));
  assert.equal(no2.status, 404);
  for (const r of [no, no2]) assert.ok(!JSON.stringify(r.body).includes('رازِ مشتریِ اول'));
  assert.equal(Number((await one('SELECT COUNT(*)::int AS n FROM shop_chat_pins WHERE message_id=$1', [theirs.id])).n), 0);

  const pin = await h.put(`${S.base}/conversations/${C1}/pins/${theirs.id}`, {}, as(T1));
  assert.equal(pin.status, 200, JSON.stringify(pin.body));
  //  `lastMessage.preview` ِ نمای گفت‌وگو پیامِ آخرِ همین مشتری است (همان فهرستِ گفت‌وگوها)؛ خودِ سنجاق‌ها بی متن
  assert.ok(!JSON.stringify(pin.body.conversation.pinned).includes('رازِ مشتریِ اول'), 'سنجاق‌ها بی متن');
  const p = pin.body.conversation.pinned.find((x) => x.messageId === theirs.id);
  assert.deepEqual(Object.keys(p).sort(), ['messageId', 'pinnedAt', 'seq']);
  assert.equal(p.seq, theirs.seq);
  //  دکان سنجاقِ مشتری را نمی‌بیند
  const shopView = (await h.get(`/api/shop/chat/conversations/${C1}`, { token: S.token })).body.conversation;
  assert.ok(!(shopView.pinned || []).some((x) => x.messageId === theirs.id));
  const un = await h.del(`${S.base}/conversations/${C1}/pins/${theirs.id}`, as(T1));
  assert.equal(un.status, 200, JSON.stringify(un.body));
  assert.ok(!un.body.conversation.pinned.some((x) => x.messageId === theirs.id));
});

//  برنامهٔ خریدِ مشتری (`ShopperApi.chatEdit`): HttpURLConnection ِ اندروید و JDK روش PATCH نمی‌فرستد ⇒ همان ویرایش با
//  POST (الگوی `POST /api/shop/chat/conversations/:id/messages/:mid/edit` ِ دکان). همان مرزها: نویسنده، مهلت، گفت‌وگوی خود.
test('POST …/messages/:mid/edit ِ ویترین همان PATCH است: پیامِ خودم، نه دیگری، نه دیر، نه گفت‌وگوی دیگری', async () => {
  const config = require('../src/config');
  const mine = await say(S, T1, C1, 'پیامِ برنامهٔ خرید');
  const ed = await h.post(`${S.base}/conversations/${C1}/messages/${mine.id}/edit`, { body: 'ویرایش از برنامه' }, as(T1));
  assert.equal(ed.status, 200, JSON.stringify(ed.body));
  assert.equal(ed.body.message.body, 'ویرایش از برنامه');
  assert.equal(ed.body.message.editUntil, mine.editUntil);
  assert.ok(ed.body.message.rev > mine.rev && ed.body.message.editedAt >= mine.createdAt);

  const fromShop = await shopSay(S, C1, 'پیامِ دکان، دست‌نخورده');
  const no = await h.post(`${S.base}/conversations/${C1}/messages/${fromShop.id}/edit`, { body: 'دست‌کاری' }, as(T1));
  assert.deepEqual([no.status, errOf(no)], [403, 'not_author']);
  const other = await h.post(`${S.base}/conversations/${C1}/messages/${mine.id}/edit`, { body: 'نفوذ' }, as(T2));
  assert.deepEqual([other.status, errOf(other)], [404, 'conversation_not_found']);
  const empty = await h.post(`${S.base}/conversations/${C1}/messages/${mine.id}/edit`, { body: '  ' }, as(T1));
  assert.equal(empty.status, 400, JSON.stringify(empty.body));
  const old = await say(S, T1, C1, 'پیامِ کهنهٔ برنامه');
  await query('UPDATE shop_chat_messages SET created_at=$2 WHERE id=$1', [old.id, now() - config.shopChat.editWindowMs - 60_000]);
  const late = await h.post(`${S.base}/conversations/${C1}/messages/${old.id}/edit`, { body: 'دیر' }, as(T1));
  assert.deepEqual([late.status, errOf(late)], [403, 'edit_window_passed']);
  const noTicket = await h.post(`${S.base}/conversations/${C1}/messages/${mine.id}/edit`, { body: 'بی بلیت' });
  assert.equal(noTicket.status, 401);
  const rows = await one('SELECT (SELECT body FROM shop_chat_messages WHERE id=$1) AS a, (SELECT body FROM shop_chat_messages WHERE id=$2) AS b, (SELECT body FROM shop_chat_messages WHERE id=$3) AS c',
    [mine.id, fromShop.id, old.id]);
  assert.deepEqual([rows.a, rows.b, rows.c], ['ویرایش از برنامه', 'پیامِ دکان، دست‌نخورده', 'پیامِ کهنهٔ برنامه']);
});
