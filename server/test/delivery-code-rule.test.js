'use strict';
/**
 * DLV-08 — مدرکِ تحویل: «کدِ تحویلِ مشتری برای سفارشِ نقد هنگامِ تحویل یا گران‌قیمت» (§۶.۱۸).
 *
 * آن‌چه این پرونده قفل می‌کند:
 *   ۱) ⛔ پیش‌فرض خاموش: بی پرچمِ `shop.delivery_code_rule` سپردنِ سفارشِ نقد به پیک بی
 *      `requireCode` همان رفتارِ پیشین است (بی کد، بی `codeRule` در سابقه).
 *   ۲) روشن: نقدِ هنگامِ تحویل ⇒ سرور خودش کد می‌سازد؛ پیک بی کد یا با کدِ نادرست تحویل
 *      نمی‌دهد و با کد می‌دهد؛ چرایی (`codeRule: 'cod'`) در سابقهٔ رویداد است.
 *   ۳) «گران‌قیمت»: پیش‌پرداخت از آستانه به بالا ⇒ کد (`high_value`)؛ زیرِ آستانه یا بی آستانه
 *      یا آستانهٔ نامعتبر ⇒ بی کد.
 *   ۴) شرکتِ بیرونی عوض نمی‌شود؛ مقدارِ خودِ دکان بر `all` جلو می‌افتد؛ تکرارِ همان کلید کدِ دوم
 *      نمی‌سازد؛ با نمای مشتری، مشتری همان کدی را می‌بیند که پیک می‌پرسد.
 */
const test = require('node:test');
const assert = require('node:assert/strict');
const h = require('./helpers');
const { query, one, many, newId, now } = require('../src/db');
const { resetAll } = require('../src/middleware/ratelimit');
const delivery = require('../src/lib/delivery');
const rule = require('../src/lib/delivery-code-rule');

let admin = '';
let seq = 0;
const uniq = () => `${Date.now().toString(36)}${++seq}`;
const TOTAL = 12_000;   //  یک کیلو برنج × ۱۲۰ افغانی

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

async function shopOwner(name) {
  const u = await h.newUser(name, 'shop');
  const made = await h.post('/api/shop', { name: `دکانِ ${name}` }, { token: u.accessToken });
  assert.equal(made.status, 201, JSON.stringify(made.body));
  return { ...u, token: u.accessToken, shopId: made.body.shop.id };
}

const push = (token, ops) => h.api('POST', '/api/sync/v1/push', {
  token, headers: { 'X-App': 'shop', 'X-Device': 'dev-store' },
  body: { device_id: 'dev-store', schema_version: 2, ops },
});
const op = (rowId, fields) => ({ op_id: `op-${uniq()}`, ts: Date.now(), table: 'products', row_id: rowId, type: 'insert', fields });

/** دکانِ روشن با ویترین و تحویل. */
async function liveShop(label) {
  const o = await shopOwner(label);
  for (const k of ['shop.orders', 'shop.storefront', 'shop.delivery']) await setFlag(o.shopId, k, true);
  const slug = `s-${uniq()}`;
  const put = await h.put('/api/shop/storefront', { slug, enabled: true, title: `ویترینِ ${label}`, contactPhone: '0700000001' }, { token: o.token });
  assert.equal(put.status, 200, JSON.stringify(put.body));
  assert.equal((await push(o.token, [op('rice', { name: 'برنج', unit: 'کیلو', salePrice: 120, category: 'خوراکی' })])).status, 200);
  assert.equal((await h.put('/api/shop/storefront/products/rice', { published: true }, { token: o.token })).status, 200);
  return { ...o, slug };
}

async function addCourier(owner, name) {
  const u = await h.newUser(`پیک ${uniq()}`, 'shop');
  await query(
    `INSERT INTO shop_members (id, shop_id, user_id, role, status, created_at, updated_at)
     VALUES ($1,$2,$3,'staff','active',$4,$4)`,
    [newId('mem'), owner.shopId, u.user.id, now()]
  );
  const r = await h.post('/api/shop/delivery/couriers', { userId: u.user.id, name, phone: '0799999999' }, { token: owner.token });
  assert.equal(r.status, 201, JSON.stringify(r.body));
  return { token: u.accessToken, userId: u.user.id, courierId: r.body.courier.id };
}

const tickets = new Map();
async function ticketFor(slug, phone) {
  const k = `${slug}|${phone}`;
  if (tickets.has(k)) return tickets.get(k);
  const s = await h.post(`/api/store/${slug}/verify/start`, { phone });
  assert.equal(s.status, 200, JSON.stringify(s.body));
  const c = await h.post(`/api/store/${slug}/verify/check`, { phone, code: s.body.devCode });
  assert.equal(c.status, 200, JSON.stringify(c.body));
  tickets.set(k, c.body.ticket);
  return c.body.ticket;
}

/** سفارشِ ارسالیِ ویترین (نقد هنگامِ تحویل)، بسته‌بندی‌شده. */
async function packedOrder(s, phone = '0791112233') {
  const r = await h.post(`/api/store/${s.slug}/checkout`, {
    fulfillment: 'delivery',
    customer: { name: 'مشتری', phone },
    address: { lat: 34.523456, lng: 69.181234, landmark: 'نزدیکِ مسجد' },
    lines: [{ productId: 'rice', qtyMilli: 1000 }],
  }, { headers: { 'Idempotency-Key': `ck-${uniq()}-key`, 'X-Store-Ticket': await ticketFor(s.slug, phone) } });
  assert.equal(r.status, 201, JSON.stringify(r.body));
  const id = (await one('SELECT id FROM online_orders WHERE shop_id=$1 AND order_no=$2', [s.shopId, r.body.order.orderNo])).id;
  for (const to of ['accepted', 'picking', 'packed']) {
    const t = await h.post(`/api/shop/orders/${id}/transition`, { to }, { token: s.token });
    assert.equal(t.status, 200, `${to}: ${JSON.stringify(t.body)}`);
  }
  assert.equal(Number((await one('SELECT total_minor FROM online_orders WHERE id=$1', [id])).total_minor), TOTAL);
  return { id, token: r.body.order.token };
}

/** همان راهی که پرداختِ آنلاین سفارش را «پرداخت‌شده» می‌کند (`payment-intents`). */
const markPrepaid = (orderId) => query(
  `UPDATE online_orders SET payment_status='paid', payment_method='online', cod_status='' WHERE id=$1`, [orderId]
);

const assign = (s, orderId, body, key) => h.post(`/api/shop/delivery/orders/${orderId}/assign`,
  { overrideCashCap: true, ...body }, { token: s.token, headers: key ? { 'Idempotency-Key': key } : {} });
const toCourier = (s, orderId, courierId, extra = {}, key) => assign(s, orderId, { mode: 'shop_courier', courierId, ...extra }, key);

let evSeq = 0;
const ev = (assignmentId, type, extra = {}) => ({ eventId: `ev-${Date.now()}-${++evSeq}`, assignmentId, type, deviceTime: Date.now(), ...extra });
async function send(token, events) {
  const r = await h.post('/api/courier/events', { deviceId: 'courier-phone', events }, { token });
  assert.equal(r.status, 200, JSON.stringify(r.body));
  return r.body.results;
}
async function sendOk(token, events) {
  for (const x of await send(token, events)) assert.equal(x.status, 'applied', JSON.stringify(x));
}
/** پیک می‌پذیرد، می‌گیرد، راه می‌افتد و می‌رسد. */
const toDoor = (courier, id) => sendOk(courier.token, [ev(id, 'assigned'), ev(id, 'picked_up'), ev(id, 'moving'), ev(id, 'arrived')]);

const createdDetail = async (id) => (await one(
  `SELECT detail FROM delivery_events WHERE assignment_id=$1 AND kind='created'`, [id]
)).detail;
const card = async (courier, id) => {
  const r = await h.get('/api/courier/missions', { token: courier.token });
  assert.equal(r.status, 200, JSON.stringify(r.body));
  return r.body.items.find((x) => x.id === id);
};

/* ══════════════════════════════════════════════════════════════════ */

test('قاعدهٔ خالص: فقط پیکِ دکان، نقدِ هنگامِ تحویل یا جمع از آستانه به بالا', () => {
  const on = { on: true, minTotalMinor: 50_000 };
  assert.equal(rule.requires(rule.OFF, { mode: 'shop_courier', totalMinor: 99_000, expectedCollectMinor: 99_000 }), '', 'خاموش ⇒ هیچ');
  assert.equal(rule.requires(on, { mode: 'shop_courier', totalMinor: 1_000, expectedCollectMinor: 1_000 }), 'cod');
  assert.equal(rule.requires(on, { mode: 'shop_courier', totalMinor: 50_000, expectedCollectMinor: 0 }), 'high_value', 'برابرِ آستانه هم');
  assert.equal(rule.requires(on, { mode: 'shop_courier', totalMinor: 49_999, expectedCollectMinor: 0 }), '');
  assert.equal(rule.requires({ on: true, minTotalMinor: 0 }, { mode: 'shop_courier', totalMinor: 9e9, expectedCollectMinor: 0 }), '', 'بی آستانه ⇒ «گران‌قیمت» خاموش');
  for (const mode of ['external', 'pickup']) {
    assert.equal(rule.requires(on, { mode, totalMinor: 99_000, expectedCollectMinor: 99_000 }), '', mode);
  }
});

test('پیش‌فرض خاموش: سفارشِ نقد بی requireCode ⇒ بی کد، و تحویل فقط با نقد — همان پیشین', async () => {
  const s = await liveShop('خاموش');
  const courier = await addCourier(s, 'کریم احمدی');
  const o = await packedOrder(s);
  const a = await toCourier(s, o.id, courier.courierId);
  assert.equal(a.status, 201, JSON.stringify(a.body));
  assert.equal('code' in a.body, false);
  assert.equal(a.body.assignment.hasCode, false);
  const id = a.body.assignment.id;
  const d = await createdDetail(id);
  assert.equal(d.requireCode, false);
  assert.equal('codeRule' in d, false, 'سابقه همان شکلِ پیشین');
  assert.equal((await card(courier, id)).requiresCode, false);

  await toDoor(courier, id);
  await sendOk(courier.token, [ev(id, 'delivered', { cashCollectedMinor: TOTAL })]);
  assert.equal((await one('SELECT status FROM delivery_assignments WHERE id=$1', [id])).status, 'delivered');
});

test('روشن: نقدِ هنگامِ تحویل ⇒ کدِ اجباری؛ پیک بی کد یا با کدِ نادرست نه، با کد بله', async () => {
  const s = await liveShop('نقد');
  await setFlag(s.shopId, rule.FLAG, true);
  const courier = await addCourier(s, 'محمود رحیمی');
  const o = await packedOrder(s);
  const a = await toCourier(s, o.id, courier.courierId);   //  دکان کد نخواست
  assert.equal(a.status, 201, JSON.stringify(a.body));
  const code = a.body.code;
  assert.match(code, /^\d{6}$/, 'کد فقط همین یک بار به کارمند');
  assert.equal(a.body.assignment.hasCode, true);
  const id = a.body.assignment.id;
  const stored = await one('SELECT code_hash, code_nonce FROM delivery_assignments WHERE id=$1', [id]);
  assert.ok(stored.code_hash && !stored.code_hash.includes(code), 'فقط HMAC');
  assert.equal(stored.code_nonce, '', 'بی نمای مشتری کد بازسازی‌پذیر نیست');
  const d = await createdDetail(id);
  assert.equal(d.requireCode, true);
  assert.equal(d.codeRule, 'cod', 'چرایی در سابقه');
  assert.equal((await card(courier, id)).requiresCode, true, 'گوشیِ پیک کد را می‌پرسد');

  await toDoor(courier, id);
  const [noCode] = await send(courier.token, [ev(id, 'delivered', { cashCollectedMinor: TOTAL })]);
  assert.equal(noCode.code, 'code_required');
  const [wrong] = await send(courier.token, [ev(id, 'delivered', { cashCollectedMinor: TOTAL, code: code === '000000' ? '111111' : '000000' })]);
  assert.equal(wrong.code, 'bad_code');
  assert.equal((await one('SELECT status FROM delivery_assignments WHERE id=$1', [id])).status, 'arrived');
  await sendOk(courier.token, [ev(id, 'delivered', { cashCollectedMinor: TOTAL, code })]);
  const done = await one('SELECT status, code_used_at FROM delivery_assignments WHERE id=$1', [id]);
  assert.equal(done.status, 'delivered');
  assert.ok(done.code_used_at != null);
});

test('روشن: «گران‌قیمت» فقط پیش‌پرداختِ از آستانه به بالا؛ بی آستانه یا نامعتبر ⇒ بی کد', async () => {
  const s = await liveShop('گران');
  await setFlag(s.shopId, rule.FLAG, true);
  const courier = await addCourier(s, 'نادر');
  const run = async () => {
    const o = await packedOrder(s);
    await markPrepaid(o.id);
    const a = await toCourier(s, o.id, courier.courierId);
    assert.equal(a.status, 201, JSON.stringify(a.body));
    assert.equal(a.body.assignment.expectedCollectMinor, 0, 'پیش‌پرداخت نقد نمی‌گیرد');
    return a.body;
  };

  //  بی آستانه
  let b = await run();
  assert.equal(b.assignment.hasCode, false);
  assert.equal('codeRule' in await createdDetail(b.assignment.id), false);
  //  آستانهٔ نامعتبر ⇒ همان «بی آستانه»
  for (const bad of [-5, 1.5, '20000', true, 0]) {
    await setFlag(s.shopId, rule.MIN_TOTAL_KEY, bad);
    b = await run();
    assert.equal(b.assignment.hasCode, false, `آستانهٔ ${JSON.stringify(bad)}`);
  }
  //  زیرِ آستانه
  await setFlag(s.shopId, rule.MIN_TOTAL_KEY, TOTAL + 1);
  b = await run();
  assert.equal(b.assignment.hasCode, false);
  //  برابر و بالاتر
  for (const min of [TOTAL, TOTAL - 1]) {
    await setFlag(s.shopId, rule.MIN_TOTAL_KEY, min);
    b = await run();
    assert.equal(b.assignment.hasCode, true, `آستانهٔ ${min}`);
    assert.match(b.code, /^\d{6}$/);
    assert.equal((await createdDetail(b.assignment.id)).codeRule, 'high_value');
  }
});

test('روشن: شرکتِ بیرونی عوض نمی‌شود؛ دکان بر `all` جلو می‌افتد؛ تکرارِ کلید کدِ دوم نمی‌سازد', async () => {
  const s = await liveShop('مرز');
  const courier = await addCourier(s, 'جواد');
  try {
    //  `all` روشن، خودِ دکان خاموش ⇒ خاموش
    await setFlag('all', rule.FLAG, true);
    await setFlag(s.shopId, rule.FLAG, false);
    let a = await toCourier(s, (await packedOrder(s)).id, courier.courierId);
    assert.equal(a.status, 201, JSON.stringify(a.body));
    assert.equal(a.body.assignment.hasCode, false, 'مقدارِ دکان بر all');
    //  فقط `all` روشن ⇒ روشن
    const other = await liveShop('همه');
    const c2 = await addCourier(other, 'سلیم');
    a = await toCourier(other, (await packedOrder(other)).id, c2.courierId);
    assert.equal(a.body.assignment.hasCode, true, 'all برای دکانِ بی مقدار');
  } finally {
    await setFlag('all', rule.FLAG, false);
  }

  await setFlag(s.shopId, rule.FLAG, true);
  //  شرکتِ بیرونی: کارمند ثبت می‌کند؛ قاعده کدی نمی‌سازد
  const ext = await assign(s, (await packedOrder(s)).id, { mode: 'external', externalName: 'پیکِ شهر', externalRef: 'R-1' });
  assert.equal(ext.status, 201, JSON.stringify(ext.body));
  assert.equal(ext.body.assignment.hasCode, false);
  assert.equal('code' in ext.body, false);

  //  همان کلید و همان درخواست ⇒ همان مأموریت، بی کدِ دوم و بی ردیفِ دوم
  const o = await packedOrder(s);
  const key = `asg-${uniq()}-key`;
  const first = await toCourier(s, o.id, courier.courierId, {}, key);
  assert.equal(first.status, 201, JSON.stringify(first.body));
  assert.match(first.body.code, /^\d{6}$/);
  const hash = (await one('SELECT code_hash FROM delivery_assignments WHERE id=$1', [first.body.assignment.id])).code_hash;
  const again = await toCourier(s, o.id, courier.courierId, {}, key);
  assert.equal(again.status, 200, JSON.stringify(again.body));
  assert.equal(again.body.replayed, true);
  assert.equal('code' in again.body, false);
  const rows = await many('SELECT id, code_hash FROM delivery_assignments WHERE order_id=$1', [o.id]);
  assert.deepEqual(rows.map((r) => [r.id, r.code_hash]), [[first.body.assignment.id, hash]], 'کد عوض نشد');
});

test('روشن با نمای مشتری: مشتری همان کدی را می‌بیند که پیک می‌پرسد', async () => {
  const s = await liveShop('نما');
  await setFlag(s.shopId, rule.FLAG, true);
  await setFlag(s.shopId, delivery.VIEW_FLAG, true);
  const courier = await addCourier(s, 'رحیم');
  const o = await packedOrder(s);
  const a = await toCourier(s, o.id, courier.courierId);
  assert.equal(a.status, 201, JSON.stringify(a.body));
  const id = a.body.assignment.id;
  assert.notEqual((await one('SELECT code_nonce FROM delivery_assignments WHERE id=$1', [id])).code_nonce, '');
  const t = await h.get(`/api/store/${s.slug}/orders/${o.token}`);
  assert.equal(t.status, 200, JSON.stringify(t.body));
  assert.equal(t.body.order.delivery.code, a.body.code);

  await toDoor(courier, id);
  await sendOk(courier.token, [ev(id, 'delivered', { cashCollectedMinor: TOTAL, code: t.body.order.delivery.code })]);
  assert.equal((await one('SELECT status FROM delivery_assignments WHERE id=$1', [id])).status, 'delivered');
});
