'use strict';
/**
 * یک رشتهٔ چت برای هر سفارش و چرخهٔ عمرِ آن (`lib/shop-chat-life.js`، مهاجرتِ ۲۰۹):
 *
 *   ۱) سفارشِ تمام‌شده (تحویل + پرداخت) ⇒ «بسته شد»: پیام، ویرایش و رسانه از هر دو طرف ۴۰۹ `chat_closed`؛
 *      خواندن باز؛ سفارشِ تحویل‌شدهٔ پرداخت‌نشده باز می‌ماند.
 *   ۲) «مشکل دارم» تا ۷ روز ⇒ همان رشته باز (هر دو طرف می‌نویسند)؛ پس از ۷ روز ۴۰۹ `issue_window_passed`؛
 *      گذارِ تازهٔ سفارش پس از آن ⇒ دوباره بسته.
 *   ۳) سفارشِ تازه ⇒ رشتهٔ تازه؛ رشته‌های پیشین در «تاریخچه» ِ دکان (فقط همان مشتری).
 *   ۴) «گفت‌وگوی عمومی» پس از ۷ روز بی‌کاری بسته؛ «گفت‌وگوی تازه» دورِ تازه در همان رشته.
 *   ۵) دکان و مشتریِ دیگر با شناسهٔ همین رشته به هیچ‌کدام نمی‌رسند (۴۰۴)؛ دکان «مشکل دارم» نمی‌زند.
 *   ۶) صندوقِ دکان: رشتهٔ بسته از «باز» بیرون و در «بسته».
 *   ۷) درِ مشتری با بلیتِ ویترین (`POST …/issue`)؛ مهاجرتِ ۲۰۹ برگشت‌پذیر.
 */
const fs = require('node:fs');
const path = require('node:path');
const test = require('node:test');
const assert = require('node:assert/strict');
const h = require('./helpers');
const { query, one, newId, now } = require('../src/db');
const chat = require('../src/lib/shop-chat');
const mailOf = (p) => `c${String(p).replace(/\D/g, '').slice(-9)}@test.local`;   //  «فقط ایمیل»: بلیتِ مشتری با ایمیل (شمارهٔ تماس جدا)

const DAY = 24 * 3600 * 1000;
const base = '/api/shop/chat';
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
test.after(() => h.stop());

async function setFlag(scope, key, value) {
  const r = await h.put('/api/admin/pump/live-config', { scope, key, value }, { token: admin });
  assert.equal(r.status, 200, JSON.stringify(r.body));
}

async function shop(name) {
  const u = await h.newUser(name, 'shop');
  const made = await h.post('/api/shop', { name: `دکانِ ${name}` }, { token: u.accessToken });
  assert.equal(made.status, 201, JSON.stringify(made.body));
  const shopId = made.body.shop.id;
  for (const k of ['shop.chat', 'shop.orders']) await setFlag(shopId, k, true);
  return { token: u.accessToken, shopId, userId: u.user.id };
}

/** سفارشِ دریافت از دکان (نقد) تا [to] با گذارهای واقعیِ سرور (رویدادها هم) */
/** «فقط ایمیل»: سفارشِ مشتری با ایمیلِ تأییدشده‌اش به او بسته است (`customer_email`، همان که ویترین می‌نشاند)؛ `email: false` ⇒
 *  سفارشِ دستیِ فقط‌شماره که هیچ مشتری‌ای با شماره به آن نمی‌رسد. */
async function order(s, phone, { upTo = 'picked_up', fulfillment = 'pickup', email = true } = {}) {
  const r = await h.post('/api/shop/orders', {
    source: 'phone', fulfillment, paymentMethod: 'cod', customer: { name: 'کریم', phone },
    ...(fulfillment === 'delivery' ? { address: { landmark: 'نزدیکِ مسجد', text: 'کارتهٔ چهار', lat: 34.5, lng: 69.1 } } : {}),
    lines: [{ name: 'برنج', qtyMilli: 1000, unitPriceMinor: 12_000 }],
  }, { token: s.token, headers: { 'Idempotency-Key': `ord-${uniq()}` } });
  assert.equal(r.status, 201, JSON.stringify(r.body));
  if (email) await query('UPDATE online_orders SET customer_email=$1 WHERE id=$2', [mailOf(phone), r.body.order.id]);
  const path = fulfillment === 'pickup'
    ? ['accepted', 'picking', 'packed', 'ready_for_pickup', 'picked_up']
    : ['accepted', 'picking', 'packed', 'out_for_delivery', 'delivered'];
  for (const to of path.slice(0, path.indexOf(upTo) + 1)) {
    const t = await h.post(`/api/shop/orders/${r.body.order.id}/transition`, { to }, { token: s.token });
    assert.equal(t.status, 200, `${to}: ${JSON.stringify(t.body)}`);
  }
  return r.body.order;
}

const customer = (shopId, phone) => ({ side: 'customer', shopId, phone: mailOf(phone) });   //  کلیدِ مشتری = ایمیلِ تأییدشده
const shopConv = async (s, orderId) => {
  const r = await h.post(`${base}/conversations`, { orderId }, { token: s.token });
  assert.ok(r.status === 200 || r.status === 201, JSON.stringify(r.body));
  return r.body.conversation;
};
const shopSend = (s, conv, body = 'سلام') => h.post(`${base}/conversations/${conv}/messages`, { clientId: cid(), body }, { token: s.token });
const code = (r) => `${r.status}:${r.body?.error?.code || ''}`;
/** زمانِ پایانِ سفارش را [days] روز عقب می‌برد (رویدادها و خودِ سفارش) */
const age = (orderId, days) => Promise.all([
  query('UPDATE online_order_events SET created_at = created_at - $2 WHERE order_id=$1', [orderId, days * DAY]),
  query('UPDATE online_orders SET updated_at = updated_at - $2 WHERE id=$1', [orderId, days * DAY]),
]);

test('سفارشِ تمام‌شده ⇒ «بسته شد»: نوشتن از هر دو طرف ۴۰۹، خواندن باز؛ تحویل‌شدهٔ پرداخت‌نشده باز', async () => {
  const s = await shop('بستن');
  const phone = '0791110001';
  const me = customer(s.shopId, '+93791110001');
  const o = await order(s, phone, { upTo: 'ready_for_pickup' });
  const conv = await shopConv(s, o.id);
  assert.equal(conv.life.kind, 'order');
  assert.equal(conv.life.state, 'open');
  assert.equal((await shopSend(s, conv.id, 'بسته‌تان آماده است')).status, 201);
  const sent = (await chat.send(me, conv.id, { clientId: cid(), body: 'تا یک ساعت دیگر می‌آیم' })).message;

  const done = await h.post(`/api/shop/orders/${o.id}/transition`, { to: 'picked_up' }, { token: s.token });
  assert.equal(done.status, 200, JSON.stringify(done.body));
  const v = (await h.get(`${base}/conversations/${conv.id}`, { token: s.token })).body.conversation;
  assert.equal(v.life.state, 'closed');
  assert.equal(v.life.reason, 'completed');
  assert.equal(v.canSend, false);
  assert.equal(v.life.canIssue, false, 'دکان «مشکل دارم» نمی‌زند');
  assert.ok(v.life.issueUntil > now() + 6 * DAY && v.life.issueUntil <= now() + 7 * DAY);

  //  نوشتن: پیامِ دکان، پیامِ مشتری، ویرایش و رسانه ⇒ ۴۰۹
  const shopTry = await shopSend(s, conv.id, 'یک چیزِ دیگر');
  assert.equal(code(shopTry), '409:chat_closed');
  assert.equal(shopTry.body.error.life.state, 'closed');
  await assert.rejects(chat.send(me, conv.id, { clientId: cid(), body: 'هنوز این‌جا؟' }), (e) => e.status === 409 && e.code === 'chat_closed');
  await assert.rejects(chat.edit(me, conv.id, sent.id, { body: 'ویرایش' }), (e) => e.code === 'chat_closed');
  await assert.rejects(chat.putMedia(me, conv.id, Buffer.from('%PDF-1.4\n%%EOF\n')), (e) => e.code === 'chat_closed');
  //  خواندن و رسید باز
  const read = await chat.changes(me, conv.id, { after: 0 });
  assert.equal(read.messages.length, 2);
  assert.equal(read.conversation.canSend, false);
  assert.equal(read.conversation.life.canIssue, true);
  assert.equal((await h.post(`${base}/conversations/${conv.id}/receipts`, { readSeq: 2 }, { token: s.token })).status, 200);
  //  تکرارِ پیامِ پذیرفته‌شده (صفِ آفلاینِ دستگاه) همان پیام است، نه ۴۰۹
  const again = await chat.send(me, conv.id, { clientId: sent.clientId, body: 'تا یک ساعت دیگر می‌آیم' });
  assert.equal(again.replayed, true);

  //  پرداختِ آنلاین: تحویل‌شده ولی پرداخت هنوز نرسیده ⇒ باز؛ «پرداخت شد» ⇒ بسته
  const unpaid = await order(s, '0791110002', { upTo: 'picked_up' });
  await query("UPDATE online_orders SET payment_method='online', payment_status='pending', cod_status='' WHERE id=$1", [unpaid.id]);
  const c2 = await shopConv(s, unpaid.id);
  assert.equal(c2.life.state, 'open');
  assert.equal((await shopSend(s, c2.id, 'پرداخت هنوز نرسیده')).status, 201);
  await query("UPDATE online_orders SET payment_status='paid' WHERE id=$1", [unpaid.id]);
  assert.equal((await chat.viewOf(customer(s.shopId, '+93791110002'), c2.id)).life.state, 'closed');
});

test('«مشکل دارم» تا ۷ روز همان رشته را باز می‌کند؛ پس از آن ۴۰۹؛ گذارِ تازه دوباره می‌بندد', async () => {
  const s = await shop('مشکل');
  const me = customer(s.shopId, '+93791110011');
  const o = await order(s, '0791110011');
  const conv = await shopConv(s, o.id);
  assert.equal(conv.life.state, 'closed');
  await age(o.id, 6);
  const opened = await chat.issue(me, conv.id);
  assert.equal(opened.reopened, true);
  assert.equal(opened.conversation.id, conv.id, 'همان رشته، نه رشتهٔ تازه');
  assert.equal(opened.conversation.life.state, 'reopened');
  assert.equal(opened.conversation.canSend, true);
  assert.equal((await chat.issue(me, conv.id)).reopened, false, 'دوباره زدن بی‌اثر');
  //  هر دو طرف می‌نویسند
  assert.equal((await chat.send(me, conv.id, { clientId: cid(), body: 'برنج خیس بود' })).message.sender, 'customer');
  assert.equal((await shopSend(s, conv.id, 'عوضش می‌کنیم')).status, 201);
  const byShop = (await h.get(`${base}/conversations/${conv.id}`, { token: s.token })).body.conversation;
  assert.equal(byShop.life.state, 'reopened');
  assert.ok(byShop.life.reopenedAt > 0);

  //  برگشت و بازپرداخت ⇒ پایانِ تازه ⇒ دوباره بسته (و مهلتِ تازه از همین پایان)
  for (const to of ['return_requested', 'refunded_full']) {
    const t = await h.post(`/api/shop/orders/${o.id}/transition`, { to }, { token: s.token });
    if (t.status !== 200) {
      //  بازپرداخت دسترسیِ جدا می‌خواهد؛ همان پایان با تحویلِ دوباره سنجیده نمی‌شود — پس رویداد را مستقیم می‌نشانیم
      await query("UPDATE online_orders SET status='refunded_full' WHERE id=$1", [o.id]);
      await query(`INSERT INTO online_order_events (id, order_id, shop_id, seq, kind, from_status, to_status, actor_type, created_at)
        VALUES ($1,$2,$3,(SELECT COALESCE(MAX(seq),0)+1 FROM online_order_events WHERE order_id=$2),'status','return_requested','refunded_full','user',$4)`,
      [newId('oev'), o.id, s.shopId, now() + 1]);
      break;
    }
  }
  const again = await chat.viewOf(me, conv.id);
  assert.equal(again.life.state, 'closed');
  assert.equal(again.life.reason, 'ended');

  //  پس از ۷ روز: «مشکل دارم» ⇒ ۴۰۹ و رشته بسته می‌ماند
  const late = await order(s, '0791110011');
  const c2 = await shopConv(s, late.id);
  await age(late.id, 8);
  await assert.rejects(chat.issue(me, c2.id), (e) => e.status === 409 && e.code === 'issue_window_passed');
  const v = await chat.viewOf(me, c2.id);
  assert.equal(v.life.state, 'closed');
  assert.equal(v.life.canIssue, false);
  await assert.rejects(chat.send(me, c2.id, { clientId: cid(), body: 'دیر شد؟' }), (e) => e.code === 'chat_closed');
  //  دکان مهلتِ گذشته را نمی‌بیند (برنامه ساعتِ خودش را نمی‌خواهد)؛ در مهلت می‌بیند
  assert.equal((await h.get(`${base}/conversations/${c2.id}`, { token: s.token })).body.conversation.life.issueUntil, null);
  //  گفت‌وگوی عمومی «مشکل دارم» ندارد؛ کارمند هم نمی‌زند
  const general = (await chat.open(me, {})).conversation.id;
  await assert.rejects(chat.issue(me, general), (e) => e.code === 'issue_order_only');
  await assert.rejects(chat.issue({ side: 'shop', shopId: s.shopId, userId: s.userId, role: 'owner' }, conv.id), (e) => e.status === 403);
});

test('سفارشِ تازه ⇒ رشتهٔ تازه؛ تاریخچهٔ دکان فقط رشته‌های همین مشتری', async () => {
  const s = await shop('رشته');
  const me = customer(s.shopId, '+93791110021');
  const first = await order(s, '0791110021');
  const c1 = await shopConv(s, first.id);
  await chat.send(me, (await chat.open(me, {})).conversation.id, { clientId: cid(), body: 'روغن دارید؟' });
  const second = await order(s, '0791110021', { upTo: 'accepted' });
  const c2 = await shopConv(s, second.id);
  assert.notEqual(c2.id, c1.id);
  assert.equal(c2.life.state, 'open');
  assert.equal(c1.life.state, 'closed', 'رشتهٔ پیشین بسته می‌ماند');
  assert.equal((await shopSend(s, c2.id, 'سفارشِ تازه رسید')).status, 201);
  //  مشتری با شمارهٔ سفارش به همان رشتهٔ هر سفارش می‌رسد
  assert.equal((await chat.open(me, { orderNo: second.orderNo })).conversation.id, c2.id);
  assert.equal((await chat.open(me, { orderNo: first.orderNo })).conversation.id, c1.id);
  //  سفارشِ دستیِ فقط‌شماره با همان شماره: مشتری با شماره به آن نمی‌رسد (شماره هویت نیست)
  const phoneOnly = await order(s, '0791110021', { upTo: 'accepted', email: false });
  await assert.rejects(chat.open(me, { orderNo: phoneOnly.orderNo }), (e) => e.code === 'order_not_found');
  //  مشتریِ دیگرِ همین دکان
  const other = await order(s, '0791110022', { upTo: 'accepted' });
  const c3 = await shopConv(s, other.id);

  const hist = await h.get(`${base}/conversations/${c2.id}/history`, { token: s.token });
  assert.equal(hist.status, 200, JSON.stringify(hist.body));
  const ids = hist.body.conversations.map((c) => c.id);
  assert.ok(ids.includes(c1.id), 'رشتهٔ سفارشِ پیشین');
  assert.equal(ids.length, 2, 'سفارشِ پیشین و گفت‌وگوی عمومی');
  assert.ok(!ids.includes(c2.id) && !ids.includes(c3.id), 'نه خودش، نه مشتریِ دیگر');
  assert.equal(hist.body.conversations.find((c) => c.id === c1.id).life.state, 'closed');
  //  مشتری همهٔ رشته‌هایش را در فهرستِ خودش دارد، نه رشتهٔ مشتریِ دیگر
  const mine = (await chat.list(me)).conversations.map((c) => c.id);
  assert.ok(mine.includes(c1.id) && mine.includes(c2.id) && !mine.includes(c3.id));
});

test('گفت‌وگوی عمومی پس از ۷ روز بی‌کاری بسته؛ «گفت‌وگوی تازه» دورِ تازه در همان رشته', async () => {
  const s = await shop('عمومی');
  const me = customer(s.shopId, '+93791110031');
  const conv = (await chat.open(me, { name: 'نادر' })).conversation;
  assert.equal(conv.life.kind, 'general');
  assert.equal(conv.life.state, 'open');
  await chat.send(me, conv.id, { clientId: cid(), body: 'آرد دارید؟' });
  await query('UPDATE shop_chat_conversations SET created_at = created_at - $2, last_message_at = last_message_at - $2 WHERE id=$1', [conv.id, 8 * DAY]);
  const closed = await chat.viewOf(me, conv.id);
  assert.equal(closed.life.state, 'closed');
  assert.equal(closed.life.reason, 'idle');
  assert.equal(closed.life.canRestart, true);
  assert.equal(closed.canSend, false);
  await assert.rejects(chat.send(me, conv.id, { clientId: cid(), body: 'هنوز؟' }), (e) => e.code === 'chat_closed');
  assert.equal(code(await shopSend(s, conv.id, 'بله')), '409:chat_closed');

  const fresh = (await chat.open(me, {})).conversation;
  assert.equal(fresh.id, conv.id, 'همان رشته');
  assert.equal(fresh.life.state, 'open');
  assert.ok(fresh.life.sessionAt > 0);
  await chat.send(me, conv.id, { clientId: cid(), body: 'شکر دارید؟' });
  const all = await chat.changes(me, conv.id, { after: 0 });
  assert.equal(all.messages.length, 2, 'پیامِ دورِ پیشین می‌ماند');
  assert.ok(all.messages[0].createdAt < fresh.life.sessionAt && all.messages[1].createdAt >= fresh.life.sessionAt);
  //  باز کردنِ دوباره در دورِ باز آغازِ دور را جابه‌جا نمی‌کند
  assert.equal((await chat.open(me, {})).conversation.life.sessionAt, fresh.life.sessionAt);
});

test('رشتهٔ بسته: «حذف برای همه» از هر دو طرف ۴۰۹ `chat_closed`؛ «حذف برای من» باز؛ «مشکل دارم» ⇒ دوباره شدنی', async () => {
  const s = await shop('حذفِ بسته');
  const me = customer(s.shopId, '+93791110051');
  const o = await order(s, '0791110051', { upTo: 'ready_for_pickup' });
  const conv = await shopConv(s, o.id);
  const fromShop = (await shopSend(s, conv.id, 'بسته‌تان آماده است')).body.message;
  const fromMe = (await chat.send(me, conv.id, { clientId: cid(), body: 'می‌آیم' })).message;
  assert.equal((await h.post(`/api/shop/orders/${o.id}/transition`, { to: 'picked_up' }, { token: s.token })).status, 200);

  //  پیام‌ها تازه‌اند (درونِ مهلتِ حذف) ولی رشته بسته است ⇒ پاک کردن برای طرفِ دیگر هم نوشتن است
  const shopDel = await h.del(`${base}/conversations/${conv.id}/messages/${fromShop.id}?for=all`, { token: s.token });
  assert.equal(code(shopDel), '409:chat_closed');
  await assert.rejects(chat.remove(me, conv.id, fromMe.id, 'all'), (e) => e.status === 409 && e.code === 'chat_closed');
  const kept = await chat.changes(me, conv.id, { after: 0 });
  assert.deepEqual(kept.messages.map((m) => !!m.deleted), [false, false], 'هیچ پیامی برای طرفِ دیگر پاک نشد');
  //  «حذف برای من» فقط نمای خودِ بیننده است و باز می‌ماند
  assert.equal((await chat.remove(me, conv.id, fromShop.id, 'me')).message.hidden, true);
  //  «مشکل دارم» ⇒ رشته باز ⇒ حذف برای همه مثلِ همیشه
  assert.equal((await chat.issue(me, conv.id)).reopened, true);
  assert.equal((await chat.remove(me, conv.id, fromMe.id, 'all')).message.deleted, true);
});

test('جداسازی: دکان و مشتریِ دیگر به رشته، «مشکل دارم» و تاریخچه نمی‌رسند', async () => {
  const a = await shop('جدا الف');
  const b = await shop('جدا ب');
  const ali = customer(a.shopId, '+93791110041');
  const o = await order(a, '0791110041');
  const conv = await shopConv(a, o.id);
  //  مشتریِ دیگرِ همان دکان، و همان شماره در دکانِ دیگر
  await assert.rejects(chat.issue(customer(a.shopId, '+93791110042'), conv.id), (e) => e.status === 404 && e.code === 'conversation_not_found');
  await assert.rejects(chat.issue(customer(b.shopId, '+93791110041'), conv.id), (e) => e.status === 404);
  await assert.rejects(chat.history({ side: 'shop', shopId: b.shopId, userId: b.userId, role: 'owner' }, conv.id), (e) => e.status === 404);
  assert.equal((await h.get(`${base}/conversations/${conv.id}/history`, { token: b.token })).status, 404);
  assert.equal((await h.post(`${base}/conversations`, { orderId: o.id }, { token: b.token })).status, 404);
  //  شناسهٔ بدشکل ⇒ همان ۴۰۴
  assert.equal((await h.get(`${base}/conversations/bad%20id/history`, { token: a.token })).status, 404);
  //  پرچمِ خاموش ⇒ ۴۰۴
  await setFlag(a.shopId, 'shop.chat', false);
  assert.equal((await h.get(`${base}/conversations/${conv.id}/history`, { token: a.token })).status, 404);
  await setFlag(a.shopId, 'shop.chat', true);
  //  هنوز همان مشتری می‌تواند
  assert.equal((await chat.issue(ali, conv.id)).reopened, true);
});

test('صندوقِ دکان: رشتهٔ بسته از «باز» بیرون می‌رود و در «بسته» می‌آید', async () => {
  const s = await shop('صندوق');
  const done = await order(s, '0791110051');
  const live = await order(s, '0791110052', { upTo: 'accepted' });
  const cDone = await shopConv(s, done.id);
  const cLive = await shopConv(s, live.id);
  const ids = async (st) => (await h.get(`${base}/conversations?status=${st}`, { token: s.token })).body.conversations.map((c) => c.id);
  const open = await ids('open');
  assert.ok(open.includes(cLive.id) && !open.includes(cDone.id));
  assert.ok((await ids('closed')).includes(cDone.id));
  assert.equal((await one('SELECT status FROM shop_chat_conversations WHERE id=$1', [cDone.id])).status, 'closed');
});

test('درِ مشتری: POST /api/store/:slug/chat/conversations/:id/issue با بلیتِ ویترین', async () => {
  const s = await shop('ویترینِ مشکل');
  await setFlag(s.shopId, 'shop.storefront', true);
  const slug = `life-${uniq()}`;
  const put = await h.put('/api/shop/storefront', { slug, enabled: true, title: 'ویترینِ چت', contactPhone: '0700000001' }, { token: s.token });
  assert.equal(put.status, 200, JSON.stringify(put.body));
  const phone = '0791110061';
  const start = await h.post(`/api/store/${slug}/verify/start`, { email: mailOf(phone) });
  assert.equal(start.status, 200, JSON.stringify(start.body));
  const check = await h.post(`/api/store/${slug}/verify/check`, { email: mailOf(phone), code: start.body.devCode });
  const ticket = check.body.ticket;
  assert.ok(ticket, JSON.stringify(check.body));
  const o = await order(s, phone);
  const conv = await shopConv(s, o.id);
  const pub = (method, p, t = ticket) => h.api(method, `/api/store/${slug}/chat${p}`, { body: method === 'GET' ? null : {}, headers: t ? { 'X-Store-Ticket': t } : {} });
  assert.equal((await pub('POST', `/conversations/${conv.id}/issue`, '')).status, 401);
  const r = await pub('POST', `/conversations/${conv.id}/issue`);
  assert.equal(r.status, 200, JSON.stringify(r.body));
  assert.equal(r.body.reopened, true);
  assert.equal(r.body.conversation.life.state, 'reopened');
  assert.equal((await pub('GET', '/conversations')).body.conversations[0].life.kind, 'order');
});

test('مهاجرتِ ۲۰۹ برگشت‌پذیر است و دوباره می‌نشیند', async () => {
  const dir = path.join(__dirname, '..', 'migrations');
  const down = fs.readFileSync(path.join(dir, 'rollback', '209_shop_chat_threads.down.sql'), 'utf8');
  const up = fs.readFileSync(path.join(dir, '209_shop_chat_threads.sql'), 'utf8');
  const cols = async () => (await query(
    "SELECT column_name FROM information_schema.columns WHERE table_name='shop_chat_conversations' AND column_name IN ('issue_opened_at','session_at')"
  )).rows.length;
  assert.equal(await cols(), 2);
  const mark = await one("SELECT * FROM schema_migrations WHERE version='209_shop_chat_threads.sql'");
  assert.ok(mark, 'مهاجرت نشسته است');
  await query(down);
  assert.equal(await cols(), 0);
  assert.equal(await one("SELECT 1 AS x FROM schema_migrations WHERE version='209_shop_chat_threads.sql'"), null);
  await query(up);
  await query('INSERT INTO schema_migrations (version, checksum, applied_at) VALUES ($1,$2,$3)', [mark.version, mark.checksum, mark.applied_at]);
  assert.equal(await cols(), 2);
});
