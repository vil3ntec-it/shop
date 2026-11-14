'use strict';
/**
 * RET-11 — «درخواستِ برگشت» ِ مشتری از ویترین (`lib/order-return-requests*.js`، مهاجرتِ ۸۶۶؛ تصمیمِ ۱۳ «ب»).
 *
 *   ۱) ⛔ پرچمِ خاموش: پیگیری بیت‌به‌بیت همان (بی `returnRequest`)، درهای مشتری درِ ناشناخته، دکان ۴۰۳، هیچ ردیفی.
 *   ۲) فقط صاحبِ همان سفارش با نشستِ حساب: بی نشست ۴۰۱، حسابِ دیگر ۴۰۴، دکانِ دیگر نمی‌بیند؛ مالکیت = «سفارش‌های من».
 *   ۳) فقط سفارشِ تحویل‌شده؛ تعداد ≤ خریده‌شده منهای درخواست‌های پیشین (ردشده آزاد می‌شود)؛ یک درخواستِ باز؛ تکرار/هم‌زمانی یکی.
 *   ۴) سیاستِ همان دکان (همگام‌سازیِ تنظیمات): مهلت و قلمِ غیرقابلِ برگشت ⇒ پیام و ۴۰۹.
 *   ۵) دکان: پذیرفتن ⇒ ثبت‌شد با شناسهٔ همان سند، رد با دلیل؛ نقش/لایهٔ «برگشت ممنوع» ۴۰۳؛ اعلانِ مشتری یک بار برای هر تصمیم.
 *   ۶) عکس از همان راهِ امن (نوعِ نادرست ۴۰۰، سقفِ ۳)؛ سقفِ نرخِ هر حساب (۴۲۹)؛ «حذفِ داده‌های من» متن و عکس را پاک می‌کند.
 */
process.env.RATE_STORE_CHECKOUT_MAX = '500';
process.env.RATE_STORE_RETURNS_MAX = '12';
const test = require('node:test');
const assert = require('node:assert/strict');
const h = require('./helpers');
const F = require('./image-fixtures');
const { query, one, newId, now } = require('../src/db');
const { resetAll } = require('../src/middleware/ratelimit');

let admin = '';
let seq = 0;
const uniq = () => `${Date.now().toString(36)}${++seq}`;
const FLAG = 'shop.storefront.return_requests';
const DAY = 24 * 60 * 60 * 1000;

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
test.after(async () => { await h.stop(); });
test.beforeEach(() => resetAll());

async function setFlag(scope, key, value) {
  const r = await h.put('/api/admin/pump/live-config', { scope, key, value }, { token: admin });
  assert.equal(r.status, 200, JSON.stringify(r.body));
}
const op = (rowId, fields) => ({ op_id: `op-${uniq()}`, ts: Date.now(), table: 'products', row_id: rowId, type: 'insert', fields });

async function shop(label, flags = [FLAG]) {
  const u = await h.newUser(label, 'shop');
  const made = await h.post('/api/shop', { name: `دکانِ ${label}` }, { token: u.accessToken });
  assert.equal(made.status, 201, JSON.stringify(made.body));
  const s = { ...u, token: u.accessToken, shopId: made.body.shop.id, slug: `rr-${uniq()}` };
  for (const k of ['shop.orders', 'shop.storefront', ...flags]) await setFlag(s.shopId, k, true);
  assert.equal((await h.put('/api/shop/storefront', { slug: s.slug, enabled: true, title: `ویترینِ ${label}`, contactPhone: '0700000001' }, { token: s.token })).status, 200);
  const p = await h.api('POST', '/api/sync/v1/push', {
    token: s.token, headers: { 'X-App': 'shop', 'X-Device': 'dev-rr' },
    body: { device_id: 'dev-rr', schema_version: 2, ops: [
      op('rice', { name: 'برنج', unit: 'کیلو', salePrice: 120, category: 'خوراکی', barcodes: ['6260001'] }),
      op('oil', { name: 'روغن', unit: 'بوتل', salePrice: 300, category: 'روغنیات' }),
    ] },
  });
  assert.equal(p.status, 200, JSON.stringify(p.body));
  for (const id of ['rice', 'oil']) assert.equal((await h.put(`/api/shop/storefront/products/${id}`, { published: true }, { token: s.token })).status, 200);
  return s;
}

/** مشتریِ حساب‌دار: بلیتِ شماره برای ثبت و نشستِ حساب برای درخواست */
async function shopper(s, phone = '0791112233') {
  const st = await h.post(`/api/store/${s.slug}/verify/start`, { phone });
  const c = await h.post(`/api/store/${s.slug}/verify/check`, { phone, code: st.body.devCode });
  assert.equal(c.status, 200, JSON.stringify(c.body));
  const a = await h.post(`/api/store/${s.slug}/account`, {}, { headers: { 'X-Store-Ticket': c.body.ticket } });
  assert.equal(a.status, 201, JSON.stringify(a.body));
  return { phone, ticket: c.body.ticket, session: a.body.session, headers: { 'X-Store-Session': a.body.session } };
}

async function storeOrder(s, who, lines = [{ productId: 'rice', qtyMilli: 5000 }, { productId: 'oil', qtyMilli: 2000 }]) {
  const r = await h.post(`/api/store/${s.slug}/checkout`, { fulfillment: 'pickup', customer: { name: 'مشتری', phone: who.phone }, lines },
    { headers: { 'Idempotency-Key': `rr-${uniq()}-key`, 'X-Store-Ticket': who.ticket } });
  assert.equal(r.status, 201, JSON.stringify(r.body));
  const row = await one('SELECT id FROM online_orders WHERE shop_id=$1 AND order_no=$2', [s.shopId, r.body.order.orderNo]);
  return { id: row.id, token: r.body.order.token, no: r.body.order.orderNo };
}
const deliver = async (s, id, upTo = 'picked_up') => {
  for (const to of ['accepted', 'picking', 'packed', 'ready_for_pickup', 'picked_up']) {
    const t = await h.post(`/api/shop/orders/${id}/transition`, { to }, { token: s.token });
    assert.equal(t.status, 200, `${to}: ${JSON.stringify(t.body)}`);
    if (to === upTo) return;
  }
};
const trackRaw = (s, token) => h.get(`/api/store/${s.slug}/orders/${token}`);
const track = async (s, token) => { const r = await trackRaw(s, token); assert.equal(r.status, 200, JSON.stringify(r.body)); return r.body.order; };
const ask = (s, o, who, body = {}) => h.post(`/api/store/${s.slug}/orders/${o.token}/return-requests`, {
  lines: [{ lineNo: 1, qtyMilli: 2000 }], reason: 'damaged', text: 'کیسه پاره بود', method: 'drop_off', requestKey: `rq-${uniq()}`, ...body,
}, { headers: who ? who.headers : {} });
const photo = (s, o, who, id, b64) => h.post(`/api/store/${s.slug}/orders/${o.token}/return-requests/${id}/photos`, { imageBase64: b64 }, { headers: who.headers });
const img = (seed = 1, o = {}) => F.jpeg(320, 240, { seed, ...o }).toString('base64');
const list = (s, status = '', token = s.token) => h.get(`/api/shop/order-returns${status ? `?status=${status}` : ''}`, { token });
const act = (s, id, what, body = {}, token = s.token) => h.post(`/api/shop/order-returns/${id}/${what}`, body, { token });
const count = async (table, shopId) => (await one(`SELECT COUNT(*)::int AS n FROM ${table} WHERE shop_id=$1`, [shopId])).n;
const code = (r) => r.body && r.body.error && r.body.error.code;

// ════════════════════════════════════════════════════════════════════

test('RET-11 ⛔ پرچمِ خاموش: پیگیری همان، درهای مشتری درِ ناشناخته، دکان ۴۰۳، هیچ ردیفی', async () => {
  const s = await shop('خاموش', []);
  const who = await shopper(s);
  const o = await storeOrder(s, who);
  await deliver(s, o.id);
  const before = JSON.stringify((await trackRaw(s, o.token)).body);
  await shop('همسایهٔ روشن');
  assert.equal(JSON.stringify((await trackRaw(s, o.token)).body), before);
  assert.equal('returnRequest' in JSON.parse(before).order, false);
  assert.equal('returnRequests' in ((await h.get(`/api/store/${s.slug}`)).body.features || {}), false);
  //  «همان درِ ناشناخته»: همان پاسخِ دری که هرگز نبوده (دروازهٔ پایانیِ `/api`)، پیش از نشست و بدنه
  const unknown = await h.post(`/api/store/${s.slug}/orders/${o.token}/no-such-door`, {}, { headers: who.headers });
  for (const r of [await ask(s, o, who), await ask(s, o, who, { lines: 'خراب' }), await ask(s, o, null),
    await photo(s, o, who, '01ARZ3NDEKTSV4RRFFQ69G5FAV', img())]) {
    assert.deepEqual([r.status, code(r)], [unknown.status, code(unknown)], JSON.stringify(r.body));
  }
  const unknownGet = await h.get(`/api/store/${s.slug}/account/no-such-door`, { headers: who.headers });
  const g = await h.get(`/api/store/${s.slug}/account/return-requests`, { headers: who.headers });
  assert.deepEqual([g.status, code(g)], [unknownGet.status, code(unknownGet)]);
  const l = await list(s);
  assert.equal(l.status, 403);
  assert.equal(code(l), 'return_requests_disabled');
  assert.equal(code(await act(s, '01ARZ3NDEKTSV4RRFFQ69G5FAV', 'accept')), 'return_requests_disabled');
  assert.equal(await count('order_return_requests', s.shopId), 0);
});

test('RET-11: فقط صاحبِ همان سفارش با نشستِ حساب؛ فقط پس از تحویل؛ تکرار و هم‌زمانی یکی؛ «سفارش‌های من»', async () => {
  const s = await shop('مالکیت');
  const who = await shopper(s);
  const o = await storeOrder(s, who);
  await deliver(s, o.id, 'packed');
  //  پیش از تحویل: بی کلید در پیگیری، و در ۴۰۹
  assert.equal('returnRequest' in (await track(s, o.token)), false);
  const early = await ask(s, o, who);
  assert.equal(early.status, 409, JSON.stringify(early.body));
  assert.equal(code(early), 'not_delivered');
  await h.post(`/api/shop/orders/${o.id}/transition`, { to: 'ready_for_pickup' }, { token: s.token });
  await h.post(`/api/shop/orders/${o.id}/transition`, { to: 'picked_up' }, { token: s.token });
  const v = (await track(s, o.token)).returnRequest;
  assert.equal(v.canRequest, true);
  assert.deepEqual(v.methods, ['drop_off'], 'بی پیکِ دکان فقط «آوردن به دکان»');
  assert.deepEqual(v.lines.map((l) => [l.lineNo, l.qtyMilli, l.leftMilli, l.blocked]), [[1, 5000, 5000, false], [2, 2000, 2000, false]]);
  assert.equal(v.days, 0, 'بی همگام‌سازیِ تنظیمات ⇒ بی مهلت');
  assert.equal((await h.get(`/api/store/${s.slug}`)).body.features.returnRequests, true);

  //  بی نشست ⇒ ۴۰۱؛ حسابِ دیگر (شمارهٔ دیگر) ⇒ ۴۰۴؛ توکنِ نادرست ⇒ ۴۰۴
  const anon = await ask(s, o, null);
  assert.equal(anon.status, 401);
  assert.equal(code(anon), 'session_required');
  const stranger = await shopper(s, '0792223344');
  const foreign = await ask(s, o, stranger);
  assert.equal(foreign.status, 404, JSON.stringify(foreign.body));
  assert.equal(code(foreign), 'order_not_found');
  assert.equal((await ask(s, { token: h.tampered(o.token) }, who)).status, 404);
  //  دکانِ دیگر با همان توکن ⇒ ۴۰۴
  const other = await shop('همسایه');
  assert.equal((await h.post(`/api/store/${other.slug}/orders/${o.token}/return-requests`, { lines: [{ lineNo: 1, qtyMilli: 1 }], reason: 'damaged', method: 'drop_off', requestKey: `rq-${uniq()}` }, { headers: who.headers })).status, 404);
  assert.equal(await count('order_return_requests', s.shopId), 0);

  //  مالکیت = «سفارش‌های من» ِ همان حساب: هر سفارشِ فهرست پذیرفته است، سفارشِ دیگری نه
  const mine = (await h.get(`/api/store/${s.slug}/account/orders`, { headers: who.headers })).body.orders;
  assert.ok(mine.some((x) => x.orderNo === o.no), 'سفارش در «سفارش‌های من» ِ همین حساب است');
  assert.ok(!(await h.get(`/api/store/${s.slug}/account/orders`, { headers: stranger.headers })).body.orders.some((x) => x.orderNo === o.no));

  //  ساختن، تکرار با همان کلید ⇒ همان؛ همان کلید با بدنهٔ دیگر ⇒ ۴۰۹؛ درخواستِ دوم تا بسته شدنِ اولی ⇒ ۴۰۹
  const key = `rq-${uniq()}`;
  const made = await ask(s, o, who, { requestKey: key });
  assert.equal(made.status, 201, JSON.stringify(made.body));
  assert.equal(made.body.request.status, 'pending');
  assert.deepEqual(made.body.request.lines, [{ lineNo: 1, qtyMilli: 2000 }]);
  const again = await ask(s, o, who, { requestKey: key });
  assert.equal(again.status, 200);
  assert.equal(again.body.request.id, made.body.request.id);
  assert.equal(code(await ask(s, o, who, { requestKey: key, text: 'متنِ دیگر' })), 'request_key_reused');
  assert.equal(code(await ask(s, o, who)), 'return_open');
  assert.equal(await count('order_return_requests', s.shopId), 1);

  //  پیگیری و «سفارش‌های من»
  const t = (await track(s, o.token)).returnRequest;
  assert.equal(t.canRequest, false);
  assert.equal(t.why, 'open');
  assert.equal(t.lines[0].leftMilli, 3000);
  assert.equal(t.requests[0].status, 'pending');
  const my = await h.get(`/api/store/${s.slug}/account/return-requests`, { headers: who.headers });
  assert.equal(my.status, 200, JSON.stringify(my.body));
  assert.deepEqual(my.body.requests.map((x) => [x.orderNo, x.status, x.items]), [[o.no, 'pending', 1]]);
  assert.ok(my.body.requests[0].token);
  assert.deepEqual((await h.get(`/api/store/${s.slug}/account/return-requests`, { headers: stranger.headers })).body.requests, []);
  assert.equal((await h.get(`/api/store/${s.slug}/account/return-requests`)).status, 401);
});

test('RET-11: هم‌زمانی ⇒ یک درخواست؛ تعداد ≤ خریده‌شده؛ سنجش‌های بدنه پیش از نوشتن', async () => {
  const s = await shop('هم‌زمان');
  const who = await shopper(s);
  const o = await storeOrder(s, who);
  await deliver(s, o.id);
  assert.equal(code(await ask(s, o, who, { lines: [{ lineNo: 1, qtyMilli: 5001 }] })), 'qty_over');
  assert.equal(code(await ask(s, o, who, { lines: [{ lineNo: 9, qtyMilli: 1 }] })), 'bad_lines');
  assert.equal(code(await ask(s, o, who, { lines: [{ lineNo: 1, qtyMilli: 1 }, { lineNo: 1, qtyMilli: 1 }] })), 'bad_lines');
  assert.equal(code(await ask(s, o, who, { reason: 'boredom' })), 'bad_reason');
  assert.equal(code(await ask(s, o, who, { reason: 'other', text: '' })), 'bad_text');
  assert.equal(code(await ask(s, o, who, { method: 'courier' })), 'bad_method', 'بی پیکِ دکان «پیک» نه');
  assert.equal(code(await ask(s, o, who, { requestKey: 'x' })), 'bad_request_key');
  assert.equal(await count('order_return_requests', s.shopId), 0);
  const all = await Promise.all([1, 2, 3, 4].map(() => ask(s, o, who)));
  assert.deepEqual(all.map((r) => r.status).sort(), [201, 409, 409, 409], JSON.stringify(all.map((r) => r.body)));
  assert.equal(await count('order_return_requests', s.shopId), 1);
});

test('RET-11: سیاستِ همان دکان — قلمِ غیرقابلِ برگشت و مهلت (همگام‌سازیِ تنظیمات)', async () => {
  const s = await shop('سیاست', [FLAG, 'shop.settings_sync']);
  const who = await shopper(s);
  const o = await storeOrder(s, who);
  await deliver(s, o.id);
  const set = (key, type, value) => query(
    `INSERT INTO shop_setting_keys (shop_id, store, key, type, value, rev, updated_at, updated_by) VALUES ($1,'tohid-policy',$2,$3,$4::jsonb,1,$5,'t')
     ON CONFLICT (shop_id, store, key) DO UPDATE SET type=EXCLUDED.type, value=EXCLUDED.value`, [s.shopId, key, type, JSON.stringify(value), now()]);
  await set('return_blocked', 's', 'روغنیات\n۶۲۶۰۰۰۱');   //  دستهٔ روغن و بارکدِ برنج (رقمِ فارسی)
  await set('return_window_days', 'i', 7);
  const v = (await track(s, o.token)).returnRequest;
  assert.deepEqual(v.lines.map((l) => l.blocked), [true, true]);
  assert.equal(v.canRequest, false);
  assert.equal(v.why, 'none_left');
  assert.equal(v.days, 7);
  assert.match(v.lastDay, /^\d{4}-\d{2}-\d{2}$/);
  assert.ok(v.deadlineAt > Date.now());
  assert.equal(code(await ask(s, o, who)), 'item_not_returnable');
  await set('return_blocked', 's', 'روغنیات');
  assert.equal((await track(s, o.token)).returnRequest.canRequest, true);
  //  تحویلِ ده روز پیش ⇒ بیرون از مهلت
  await query(`UPDATE online_order_events SET created_at = created_at - $3 WHERE shop_id=$1 AND order_id=$2 AND to_status='picked_up'`, [s.shopId, o.id, 10 * DAY]);
  const late = (await track(s, o.token)).returnRequest;
  assert.equal(late.why, 'late');
  assert.ok(late.deadlineAt < Date.now());
  const r = await ask(s, o, who);
  assert.equal(r.status, 409);
  assert.equal(code(r), 'return_window_passed');
  //  همگام‌سازیِ تنظیمات خاموش ⇒ سیاستی که سرور نمی‌داند اثر ندارد (تصمیم با دکان)
  await setFlag(s.shopId, 'shop.settings_sync', false);
  assert.equal((await ask(s, o, who)).status, 201);
  assert.equal(await count('order_return_requests', s.shopId), 1);
});

test('RET-11: عکس از همان راهِ امن و سقفِ ۳؛ دکان: پذیرفتن ⇒ ثبت‌شد با همان سند، رد با دلیل؛ اعلان یک بار', async () => {
  const s = await shop('دکان', [FLAG, 'shop.customer_notify', 'shop.delivery']);
  const who = await shopper(s);
  const o = await storeOrder(s, who);
  await deliver(s, o.id);
  const v = (await track(s, o.token)).returnRequest;
  assert.ok(v.methods.includes('drop_off'));
  const made = await ask(s, o, who, { lines: [{ lineNo: 1, qtyMilli: 1000 }, { lineNo: 2, qtyMilli: 2000 }], reason: 'wrong_item' });
  assert.equal(made.status, 201, JSON.stringify(made.body));
  const id = made.body.request.id;
  //  عکس: نوعِ نادرست ⇒ ۴۰۰؛ تکرار ⇒ همان؛ سقفِ ۳
  assert.equal((await photo(s, o, who, id, Buffer.from('GIF89a-not-a-photo').toString('base64'))).status, 400);
  const p1 = await photo(s, o, who, id, img(1));
  assert.equal(p1.status, 201, JSON.stringify(p1.body));
  assert.equal((await photo(s, o, who, id, img(1))).status, 200);
  assert.equal((await photo(s, o, who, id, img(2))).status, 201);
  assert.equal((await photo(s, o, who, id, img(3))).status, 201);
  assert.equal(code(await photo(s, o, who, id, img(4))), 'too_many_photos');
  assert.equal((await photo(s, o, await shopper(s, '0795556677'), id, img(5))).status, 404, 'حسابِ دیگر به درخواستِ این سفارش عکس نمی‌افزاید');

  //  دکان: فهرست با نامِ قلم‌ها، سند و عکس
  const l = await list(s);
  assert.equal(l.status, 200, JSON.stringify(l.body));
  assert.equal(l.body.waiting, 1);
  const r = l.body.requests[0];
  assert.deepEqual([r.id, r.orderNo, r.reason, r.method, r.status], [id, o.no, 'wrong_item', 'drop_off', 'pending']);
  assert.deepEqual(r.lines.map((x) => [x.lineNo, x.name, x.qtyMilli, x.boughtMilli, x.productId]), [[1, 'برنج', 1000, 5000, 'rice'], [2, 'روغن', 2000, 2000, 'oil']]);
  assert.equal(r.docId, `o-${o.id}-rq-${id}`);
  assert.equal(r.photos.length, 3);
  const bytes = await h.download(`/api/shop/order-returns/${id}/photos/${r.photos[0].id}`, { token: s.token });
  assert.equal(bytes.status, 200);
  assert.equal((await h.download(`/api/shop/order-returns/${id}/photos/${r.photos[0].id}`, { token: (await shop('بیگانه')).token })).status, 404, 'دکانِ دیگر عکس را نمی‌بیند');

  //  ثبت‌شد پیش از پذیرفتن ⇒ ۴۰۹؛ پذیرفتن (دوباره بی‌اثر)؛ سندِ دیگر ⇒ ۴۰۰؛ همان سند ⇒ ثبت‌شد (دوباره بی‌اثر)
  assert.equal(code(await act(s, id, 'complete', { returnDocId: r.docId })), 'not_accepted');
  const a1 = await act(s, id, 'accept');
  assert.equal(a1.status, 200, JSON.stringify(a1.body));
  assert.equal(a1.body.request.status, 'accepted');
  assert.equal((await act(s, id, 'accept')).body.unchanged, true);
  assert.equal(code(await act(s, id, 'complete', { returnDocId: `o-${o.id}-rq-01ARZ3NDEKTSV4RRFFQ69G5FAV` })), 'bad_return_doc');
  assert.equal(code(await act(s, id, 'complete', {})), 'bad_return_doc');
  const done = await act(s, id, 'complete', { returnDocId: r.docId });
  assert.equal(done.status, 200, JSON.stringify(done.body));
  assert.deepEqual([done.body.request.status, done.body.request.returnDocId], ['completed', r.docId]);
  assert.equal((await act(s, id, 'complete', { returnDocId: r.docId })).body.unchanged, true);
  assert.equal(code(await act(s, id, 'reject', { note: 'دیر شد' })), 'request_closed');
  assert.equal(code(await photo(s, o, who, id, img(6))), 'request_closed');
  const t = (await track(s, o.token)).returnRequest;
  assert.equal(t.requests[0].status, 'completed');
  assert.deepEqual(t.lines.map((x) => x.leftMilli), [4000, 0]);

  //  درخواستِ دوم ⇒ رد با دلیل (بی دلیل ۴۰۰)؛ ردشده تعداد را آزاد می‌کند
  const second = await ask(s, o, who, { lines: [{ lineNo: 1, qtyMilli: 4000 }] });
  assert.equal(second.status, 201, JSON.stringify(second.body));
  const sid = second.body.request.id;
  assert.equal(code(await act(s, sid, 'reject', { note: '' })), 'bad_note');
  const rej = await act(s, sid, 'reject', { note: 'کالا باز شده است' });
  assert.equal(rej.status, 200, JSON.stringify(rej.body));
  assert.equal(rej.body.request.status, 'rejected');
  assert.equal((await act(s, sid, 'reject', { note: 'کالا باز شده است' })).body.unchanged, true);
  assert.equal(code(await act(s, sid, 'reject', { note: 'دلیلِ دیگر' })), 'already_rejected');
  assert.equal(code(await act(s, sid, 'accept')), 'request_closed');
  const after = (await track(s, o.token)).returnRequest;
  assert.equal(after.lines[0].leftMilli, 4000);
  assert.equal(after.requests[1].decisionNote, 'کالا باز شده است');
  assert.equal((await list(s, 'all')).body.requests.length, 2);
  assert.equal((await list(s, 'rejected')).body.requests.length, 1);
  assert.equal(code(await list(s, 'everything')), 'bad_status');

  //  اعلانِ مشتری: یک پیام برای هر تصمیمِ هر درخواست (پذیرفته، ثبت‌شد، رد)، بی تکرار
  const events = (await query(`SELECT event, return_request_id FROM customer_notify_outbox WHERE order_id=$1 AND return_request_id IS NOT NULL ORDER BY created_at`, [o.id])).rows;
  assert.deepEqual(events.map((e) => e.event).sort(), [`order_return_accepted:${id}`, `order_return_completed:${id}`, `order_return_rejected:${sid}`].sort());
  const cn = require('../src/lib/customer-notify');
  assert.deepEqual(events.map((e) => cn.eventOf(e)).sort(), ['order_return_accepted', 'order_return_completed', 'order_return_rejected']);
  assert.match(cn.render('order_return_rejected', 'en', { no: 12, shop: 'Dokan' }), /declined the return request for order #12/);
  //  پیامِ برگشت از راهِ `onExtraIn` نمی‌رود (فقط با درخواست)
  assert.equal(await cn.onExtraIn((q, p) => query(q, p), { shopId: s.shopId, order: { id: o.id, source: 'storefront', customer_phone: '+93791112233' }, event: 'order_return_accepted' }), null);
});

test('RET-11: مجوز — جمع‌آور (برگشت ممنوع)، حسابدار و لایهٔ «برگشت ممنوع» ۴۰۳؛ دکانِ دیگر ۴۰۴', async () => {
  const s = await shop('مجوز');
  const who = await shopper(s);
  const o = await storeOrder(s, who);
  await deliver(s, o.id);
  const id = (await ask(s, o, who)).body.request.id;
  const member = async (role) => {
    const u = await h.newUser(`عضو ${uniq()}`, 'shop');
    await query(`INSERT INTO shop_members (id, shop_id, user_id, role, status, created_at, updated_at) VALUES ($1,$2,$3,$4,'active',$5,$5)`,
      [newId('mem'), s.shopId, u.user.id, role, now()]);
    return u.accessToken;
  };
  const picker = await member('picker');
  assert.equal((await list(s, '', picker)).status, 200, 'جمع‌آور می‌بیند');
  assert.equal((await act(s, id, 'accept', {}, picker)).status, 403);
  const accountant = await member('accountant');
  assert.equal((await act(s, id, 'accept', {}, accountant)).status, 403);
  const staff = await member('staff');
  assert.equal((await h.roleLayer(s.token, 'staff', { 'returns.create': { mode: 'deny' } })).status, 200);
  const denied = await act(s, id, 'accept', {}, staff);
  assert.equal(denied.status, 403, JSON.stringify(denied.body));
  assert.equal((await act(s, id, 'reject', { note: 'نه' }, staff)).status, 403);
  const stranger = await shop('بیگانهٔ مجوز');
  const foreign = await act(s, id, 'accept', {}, stranger.token);
  assert.equal(foreign.status, 404, JSON.stringify(foreign.body));
  assert.equal((await list(stranger)).body.requests.length, 0);
  assert.equal((await one('SELECT status FROM order_return_requests WHERE id=$1', [id])).status, 'pending');
  const manager = await member('manager');
  assert.equal((await act(s, id, 'accept', {}, manager)).body.request.status, 'accepted');
});

test('RET-11: سقفِ نرخِ هر حساب (۴۲۹) و «حذفِ داده‌های من» متن و عکسِ درخواست را پاک می‌کند', async () => {
  const s = await shop('سقف', [FLAG, 'shop.customer_erasure']);
  const who = await shopper(s);
  const o = await storeOrder(s, who);
  await deliver(s, o.id);
  const made = await ask(s, o, who);
  assert.equal((await photo(s, o, who, made.body.request.id, img(9))).status, 201);
  assert.equal((await act(s, made.body.request.id, 'reject', { note: 'متنِ دکان' })).status, 200);
  let last = null;
  for (let i = 0; i < 12; i++) last = await ask(s, o, who, { requestKey: `flood-${i}-${uniq()}`, lines: [{ lineNo: 1, qtyMilli: 1 }] });
  assert.equal(last.status, 429, JSON.stringify(last.body));
  //  SEC-10: متنِ مشتری و دلیلِ دکان پاک، عکس پاک؛ قلم‌ها و وضعیت می‌مانند
  const er = await h.post('/api/shop/customer-erasure', { orderId: o.id }, { token: s.token });
  assert.equal(er.status, 200, JSON.stringify(er.body));
  assert.ok(er.body.counts.returnRequests >= 1);
  assert.equal(er.body.counts.returnPhotos, 1);
  const rows = (await query('SELECT body, decision_note, status, lines FROM order_return_requests WHERE order_id=$1', [o.id])).rows;
  assert.ok(rows.every((r) => r.body === '' && r.decision_note === ''));
  assert.ok(rows.some((r) => r.status === 'rejected'));
  assert.equal(await count('order_return_request_photos', s.shopId), 0);
});
