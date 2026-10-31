'use strict';
/**
 * عکسِ سفارشِ بسته‌بندی‌شده به‌عنوانِ مدرک (PCK-08) — پشتِ پرچمِ خاموشِ `shop.orders.picking`.
 *
 * آن‌چه این پرونده قفل می‌کند:
 *   ۱) ⛔ خاموش ⇒ ۴۰۳ و هیچ بایت.
 *   ۲) فقط از بسته (`parcelOnly`)، فقط پس از بسته‌بندی و پیش از تحویل؛ نوعِ واقعیِ عکس، سقفِ اندازه و ۳ عکس.
 *   ۳) کلیدِ یکتا: تکرار ⇒ همان؛ همان کلید با عکسِ دیگر ⇒ ۴۰۹. بایت‌ها همان که آمد؛ دیدن در سابقه.
 *   ۴) ⛔ دکانِ دیگر و سفارشِ دیگر ⇒ ۴۰۴.
 */
const test = require('node:test');
const assert = require('node:assert/strict');
const h = require('./helpers');
const F = require('./image-fixtures');
const { query, one, newId, now } = require('../src/db');
const { resetAll } = require('../src/middleware/ratelimit');

let admin = '';
let seq = 0;
const uniq = () => `${Date.now().toString(36)}${++seq}`;
const key = (p = 'k') => `${p}-${uniq()}-key`;

test.before(async () => {
  await h.start();
  const pw = require('../src/lib/password');
  await query(
    `INSERT INTO admins (id, username, name, password_hash, role, status, created_at)
     VALUES ($1,'admin','مدیر',$2,'superadmin','active',$3)`,
    [newId('adm'), await pw.hashPassword('Admin!12345'), now()]
  );
  admin = (await h.post('/api/admin/login', { username: 'admin', password: 'Admin!12345' })).body.token;
  assert.ok(admin, 'ورودِ مدیر');
});
test.after(async () => { await h.stop(); });
test.beforeEach(() => resetAll());

async function shop(name, { picking = true } = {}) {
  const u = await h.newUser(name, 'shop');
  const made = await h.post('/api/shop', { name: `دکانِ ${name}` }, { token: u.accessToken });
  assert.equal(made.status, 201, JSON.stringify(made.body));
  const o = { ...u, token: u.accessToken, shopId: made.body.shop.id };
  for (const [k, on] of [['shop.orders', true], ['shop.orders.picking', picking]]) {
    if (!on) continue;
    const r = await h.put('/api/admin/pump/live-config', { scope: o.shopId, key: k, value: true }, { token: admin });
    assert.equal(r.status, 200, JSON.stringify(r.body));
  }
  return o;
}

/** سفارشِ دستی ⇒ قبول ⇒ برگه ⇒ برداشته ⇒ (بسته با دو کیسه) */
async function order(o, { packIt = true, start = true } = {}) {
  const r = await h.post('/api/shop/orders', {
    source: 'phone', fulfillment: 'pickup', paymentMethod: 'cod', customer: { name: 'احمد' },
    lines: [{ name: 'بستهٔ دستی', qtyMilli: 1000, unitPriceMinor: 1000 }],
  }, { token: o.token, headers: { 'Idempotency-Key': key('order') } });
  assert.equal(r.status, 201, JSON.stringify(r.body));
  const id = r.body.order.id;
  assert.equal((await h.post(`/api/shop/orders/${id}/transition`, { to: 'accepted' }, { token: o.token })).status, 200);
  if (!start) return id;
  assert.equal((await h.post(`/api/shop/orders/${id}/pick/start`, {}, { token: o.token })).status, 201);
  const e = await h.post(`/api/shop/orders/${id}/pick/events`, { events: [{ eventId: key('ev'), lineNo: 1, type: 'pick', qtyMilli: 1000 }] }, { token: o.token });
  assert.equal(e.body.results[0].status, 'accepted');
  if (packIt) {
    const p = await h.post(`/api/shop/orders/${id}/pick/pack`, { bags: 2 }, { token: o.token, headers: { 'Idempotency-Key': key('pack') } });
    assert.equal(p.status, 200, JSON.stringify(p.body));
  }
  return id;
}

const img = (seed = 1) => F.jpeg(320, 240, { seed }).toString('base64');
const upload = (o, id, body) => h.post(`/api/shop/orders/${id}/pick/photos`, { eventId: key('ph'), parcelOnly: true, imageBase64: img(), ...body }, { token: o.token });

test('⛔ پرچمِ جمع‌آوری خاموش: درهای عکس ۴۰۳ و هیچ بایت', async () => {
  const o = await shop('عکس-خاموش', { picking: false });
  const id = await order(o, { start: false });
  for (const r of [
    await upload(o, id, {}),
    await h.get(`/api/shop/orders/${id}/pick/photos`, { token: o.token }),
    await h.get(`/api/shop/orders/${id}/pick/photos/01ARZ3NDEKTSV4RRFFQ69G5FAV`, { token: o.token }),
  ]) {
    assert.equal(r.status, 403, JSON.stringify(r.body));
    assert.equal(r.body.error.code, 'picking_disabled');
  }
  assert.equal((await one('SELECT COUNT(*)::int AS n FROM order_pick_photos WHERE shop_id=$1', [o.shopId])).n, 0);
});

test('PCK-08: عکسِ بسته پس از بسته‌بندی — همان بایت‌ها، کیسه‌ها، تکرارِ بی‌خطر، دیدن در سابقه', async () => {
  const o = await shop('عکس-مدرک');
  const id = await order(o);
  const b64 = img(7);
  const k = key('ph');
  const r = await upload(o, id, { eventId: k, imageBase64: b64, deviceTime: 1700000000000 });
  assert.equal(r.status, 201, JSON.stringify(r.body));
  assert.equal(r.body.photo.mime, 'image/jpeg');
  assert.equal(r.body.photo.bags, 2);
  assert.equal(r.body.photo.deviceTime, 1700000000000);
  const again = await upload(o, id, { eventId: k, imageBase64: b64 });
  assert.equal(again.status, 200);
  assert.equal(again.body.replayed, true);
  assert.equal(again.body.photo.id, r.body.photo.id);
  const reused = await upload(o, id, { eventId: k, imageBase64: img(8) });
  assert.equal(reused.status, 409);
  assert.equal(reused.body.error.code, 'event_key_reused');

  const list = await h.get(`/api/shop/orders/${id}/pick/photos`, { token: o.token });
  assert.equal(list.status, 200);
  assert.deepEqual(list.body.photos.map((p) => p.id), [r.body.photo.id]);
  assert.equal(JSON.stringify(list.body).includes(b64.slice(0, 40)), false, 'فهرست بی بایت');
  const got = await h.download(`/api/shop/orders/${id}/pick/photos/${r.body.photo.id}`, { token: o.token });
  assert.equal(got.status, 200);
  assert.match(got.headers.get('content-type'), /image\/jpeg/);
  assert.equal(got.headers.get('cache-control'), 'private, no-store');
  assert.ok(got.buffer.equals(Buffer.from(b64, 'base64')), 'همان بایت‌ها');
  const viewed = await one(`SELECT COUNT(*)::int AS n FROM audit_logs WHERE action='pick.photo.view' AND shop_id=$1`, [o.shopId]);
  assert.equal(viewed.n, 1, 'دیدنِ مدرک در سابقه');
  //  شاگرد هم (جمع‌آور) می‌گیرد و می‌بیند؛ سقفِ ۳
  assert.equal((await upload(o, id, {})).status, 201);
  assert.equal((await upload(o, id, {})).status, 201);
  const four = await upload(o, id, {});
  assert.equal(four.status, 409);
  assert.equal(four.body.error.code, 'too_many_photos');
});

test('PCK-08: فقط بسته، فقط عکسِ واقعی، فقط پس از بسته‌بندی و پیش از تحویل', async () => {
  const o = await shop('عکس-رد');
  const id = await order(o);
  let r = await upload(o, id, { parcelOnly: false });
  assert.equal(r.body.error.code, 'parcel_only_required');
  r = await upload(o, id, { imageBase64: Buffer.from('<svg xmlns="http://www.w3.org/2000/svg">'.repeat(10)).toString('base64') });
  assert.equal(r.body.error.code, 'bad_photo');
  r = await upload(o, id, { imageBase64: Buffer.alloc(700 * 1024, 1).toString('base64') });
  assert.equal(r.body.error.code, 'photo_too_large');
  r = await upload(o, id, { imageBase64: F.png(64, 64).toString('base64') });
  assert.equal(r.status, 201, 'PNG هم');
  const open = await order(o, { packIt: false });
  r = await upload(o, open, {});
  assert.equal(r.status, 409);
  assert.equal(r.body.error.code, 'photo_not_allowed_now');
  const notStarted = await order(o, { start: false });
  r = await upload(o, notStarted, {});
  assert.equal(r.status, 404);
  //  تحویل شد ⇒ دیگر نه
  assert.equal((await h.post(`/api/shop/orders/${id}/transition`, { to: 'ready_for_pickup' }, { token: o.token })).status, 200);
  assert.equal((await upload(o, id, {})).status, 201, 'آمادهٔ دریافت هنوز در دکان است');
  assert.equal((await h.post(`/api/shop/orders/${id}/transition`, { to: 'picked_up' }, { token: o.token })).status, 200);
  r = await upload(o, id, {});
  assert.equal(r.body.error.code, 'photo_not_allowed_now');
});

test('⛔ دکانِ دیگر و سفارشِ دیگر: عکسِ «الف» ۴۰۴', async () => {
  const a = await shop('عکس-الف');
  const b = await shop('عکس-ب');
  const id = await order(a);
  const other = await order(a);
  const r = await upload(a, id, {});
  const pid = r.body.photo.id;
  assert.equal((await h.get(`/api/shop/orders/${id}/pick/photos`, { token: b.token })).status, 404);
  assert.equal((await h.raw('GET', `/api/shop/orders/${id}/pick/photos/${pid}`, undefined, { token: b.token })).status, 404);
  assert.equal((await h.raw('GET', `/api/shop/orders/${other}/pick/photos/${pid}`, undefined, { token: a.token })).status, 404, 'عکس فقط از همان سفارش');
  assert.equal((await upload(b, id, {})).status, 404);
  assert.equal((await one('SELECT COUNT(*)::int AS n FROM order_pick_photos WHERE order_id=$1', [id])).n, 1);
});
