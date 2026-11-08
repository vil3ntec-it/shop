'use strict';
/**
 * محدودهٔ خدمتِ هر پیک در سرور — S83 (§۱۹.۱۲: «پیکِ خارج از محدوده ⇒ سپردنِ خودکار رد و سفارش به صفِ دستی»).
 *
 * آن‌چه این پرونده قفل می‌کند:
 *   ۱) بردارِ مشترکِ `core/test-vectors/courier-area-v1.json` (همان که `CourierAreaVectorsTest.kt` می‌خواند).
 *   ۲) بی محدوده (همهٔ پیک‌های امروز) رفتار همان پیشین است؛ ثبت/ویرایش/پاک‌کردن و ورودیِ نامعتبر.
 *   ۳) سپردن به پیکِ بیرون از محدوده ⇒ ۴۰۹ `courier_out_of_area` و هیچ مأموریتی، مگر مدیر آگاهانه
 *      `overrideArea` بزند (در رویدادِ ساخت ثبت)؛ ناحیهٔ ویترین بر «ناحیه» ِ نشانی مقدم است؛ سفارشِ بی ناحیه آزاد.
 *   ۴) شاگرد محدوده را عوض نمی‌کند؛ دکانِ دیگر نه پیک را می‌بیند و نه ناحیه‌های این دکان را در گزینه‌ها.
 */
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('fs');
const path = require('path');
const h = require('./helpers');
const { query, one, newId, now } = require('../src/db');
const A = require('../src/lib/delivery-courier-area');

const vec = JSON.parse(fs.readFileSync(path.join(__dirname, '..', '..', 'core', 'test-vectors', 'courier-area-v1.json'), 'utf8'));

let admin = '';

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

async function shopOwner(name) {
  const u = await h.newUser(name, 'shop');
  const made = await h.post('/api/shop', { name: `دکانِ ${name}` }, { token: u.accessToken });
  assert.equal(made.status, 201, JSON.stringify(made.body));
  return { ...u, token: u.accessToken, shopId: made.body.shop.id };
}

async function member(shopId, role, name) {
  const u = await h.newUser(name, 'shop');
  await query(
    `INSERT INTO shop_members (id, shop_id, user_id, role, status, created_at, updated_at)
     VALUES ($1,$2,$3,$4,'active',$5,$5)`,
    [newId('mem'), shopId, u.user.id, role, now()]
  );
  return { ...u, token: u.accessToken };
}

async function enable(shopId) {
  for (const key of ['shop.orders', 'shop.delivery']) {
    const r = await h.put('/api/admin/pump/live-config', { scope: shopId, key, value: true }, { token: admin });
    assert.equal(r.status, 200, JSON.stringify(r.body));
  }
}

let seq = 0;
const freshKey = (p = 'k') => `${p}-${++seq}-${Date.now()}`;

/** سفارشِ تلفنیِ آماده با «ناحیه» ِ دلخواه در نشانی */
async function readyOrder(owner, area) {
  const r = await h.post('/api/shop/orders', {
    source: 'phone', fulfillment: 'delivery', paymentMethod: 'cod',
    customer: { name: 'احمد', phone: '0791234567' },
    address: { landmark: 'پشتِ مسجد', ...(area ? { area } : {}) },
    lines: [{ name: 'نان', qtyMilli: 1000, unitPriceMinor: 1_000 }],
  }, { token: owner.token, headers: { 'Idempotency-Key': freshKey('order') } });
  assert.equal(r.status, 201, JSON.stringify(r.body));
  for (const to of ['accepted', 'picking', 'packed']) {
    const t = await h.post(`/api/shop/orders/${r.body.order.id}/transition`, { to }, { token: owner.token });
    assert.equal(t.status, 200, JSON.stringify(t.body));
  }
  return r.body.order;
}

async function addCourier(owner, name, over = {}) {
  const m = await member(owner.shopId, 'staff', name);
  const r = await h.post('/api/shop/delivery/couriers', { userId: m.user.id, ...over }, { token: owner.token });
  return { ...m, res: r, courierId: r.body.courier?.id };
}

const assign = (owner, orderId, courierId, extra = {}) =>
  h.post(`/api/shop/delivery/orders/${orderId}/assign`, { mode: 'shop_courier', courierId, ...extra }, { token: owner.token });
const update = (token, courierId, body) => h.post(`/api/shop/delivery/couriers/${courierId}`, body, { token });
const liveOf = async (orderId) => Number((await one('SELECT COUNT(*)::int AS n FROM delivery_assignments WHERE order_id=$1', [orderId])).n);
const createdDetail = async (assignmentId) => {
  const ev = await one(`SELECT detail FROM delivery_events WHERE assignment_id=$1 AND kind='created'`, [assignmentId]);
  return typeof ev.detail === 'string' ? JSON.parse(ev.detail) : ev.detail;
};

test('S83 ۱) بردارِ مشترک: ناحیهٔ سفارش، پوشش، پاک‌سازی و ردِ ورودیِ نامعتبر', () => {
  assert.equal(vec.version, 1);
  assert.ok(vec.covers.length >= 8 && vec.orderArea.length >= 5 && vec.parse.length >= 3 && vec.reject.length >= 5);
  for (const c of vec.orderArea) assert.equal(A.orderArea(c.address), c.want, c.name);
  for (const c of vec.orderArea) assert.equal(A.orderArea(JSON.stringify(c.address)), c.want, `${c.name} (متنِ JSON)`);
  for (const c of vec.covers) assert.equal(A.covers(c.areas, c.area), c.want, c.name);
  for (const c of vec.parse) assert.deepEqual(A.parse(c.in), c.want, c.name);
  for (const c of vec.reject) {
    assert.throws(() => A.parse(c.in), (e) => e.status === 400 && e.code === 'bad_areas', c.name);
  }
  assert.deepEqual(A.parse(null), [], 'null ⇒ همه‌جا');
});

test('S83 ۲) بی محدوده همان رفتارِ پیشین؛ ثبت، ویرایش، نیامدن همان قبلی، null و [] پاک، نامعتبر ۴۰۰', async () => {
  const a = await shopOwner('محدوده');
  await enable(a.shopId);
  const free = await addCourier(a, 'پیکِ همه‌جا');
  assert.equal(free.res.status, 201, JSON.stringify(free.res.body));
  assert.deepEqual(free.res.body.courier.areas, [], 'پیش‌فرض: همه‌جا');
  for (const area of ['کارته', 'خیرخانه', '']) {
    const o = await readyOrder(a, area);
    const r = await assign(a, o.id, free.courierId);
    assert.equal(r.status, 201, `بی محدوده (${area || 'بی ناحیه'}): ${JSON.stringify(r.body)}`);
    assert.equal((await createdDetail(r.body.assignment.id)).outOfArea, undefined);
  }

  const limited = await addCourier(a, 'پیکِ خیرخانه', { areas: [' خیر‌خانه ', 'خیر خانه', 'شهرنو'] });
  assert.equal(limited.res.status, 201, JSON.stringify(limited.res.body));
  assert.deepEqual(limited.res.body.courier.areas, ['خیر‌خانه', 'شهرنو'], 'پاک و بی تکرار');
  const rename = await update(a.token, limited.courierId, { name: 'پیکِ شمال' });
  assert.deepEqual(rename.body.courier.areas, ['خیر‌خانه', 'شهرنو'], 'نیامدنِ areas محدوده را دست نمی‌زند');
  for (const bad of ['کارته', [''], [7], [null], Array.from({ length: 31 }, (_, i) => `n${i}`), ['ا'.repeat(81)]]) {
    const r = await update(a.token, limited.courierId, { areas: bad });
    assert.equal(r.status, 400, `${JSON.stringify(bad).slice(0, 40)}: ${JSON.stringify(r.body)}`);
    assert.equal(r.body.error.code, 'bad_areas');
  }
  const cleared = await update(a.token, limited.courierId, { areas: null });
  assert.deepEqual(cleared.body.courier.areas, []);
  const back = await update(a.token, limited.courierId, { areas: ['کارته'] });
  assert.deepEqual(back.body.courier.areas, ['کارته']);
  const empty = await update(a.token, limited.courierId, { areas: [] });
  assert.deepEqual(empty.body.courier.areas, []);
  await update(a.token, limited.courierId, { areas: ['کارته', 'شهرنو'] });
  const list = await h.get('/api/shop/delivery/couriers', { token: a.token });
  assert.deepEqual(list.body.items.find((c) => c.id === limited.courierId).areas, ['کارته', 'شهرنو']);
  assert.deepEqual(list.body.items.find((c) => c.id === free.courierId).areas, []);
  const audit = await one(
    `SELECT detail FROM audit_logs WHERE shop_id=$1 AND action='delivery.courier.update' ORDER BY created_at DESC, id DESC LIMIT 1`, [a.shopId]);
  const ad = typeof audit.detail === 'string' ? JSON.parse(audit.detail) : audit.detail;
  assert.equal(ad.areas, 'کارته، شهرنو', 'تغییرِ محدوده در سابقهٔ دکان');
});

test('S83 ۳) بیرون از محدوده ⇒ ۴۰۹ مگر آگاهانه؛ ناحیهٔ ویترین مقدم؛ سفارشِ بی ناحیه آزاد', async () => {
  const a = await shopOwner('بیرون');
  await enable(a.shopId);
  const c = await addCourier(a, 'پیکِ خیرخانه', { areas: ['خیرخانه'] });
  assert.equal(c.res.status, 201, JSON.stringify(c.res.body));

  const far = await readyOrder(a, 'کارته');
  const denied = await assign(a, far.id, c.courierId);
  assert.equal(denied.status, 409, JSON.stringify(denied.body));
  assert.equal(denied.body.error.code, 'courier_out_of_area');
  assert.equal(await liveOf(far.id), 0, 'هیچ مأموریتی ساخته نشد — سفارش در صفِ سپردنِ دستی می‌ماند');
  const bad = await assign(a, far.id, c.courierId, { overrideArea: 'yes' });
  assert.equal(bad.status, 400);
  assert.equal(bad.body.error.code, 'bad_override');
  const forced = await assign(a, far.id, c.courierId, { overrideArea: true });
  assert.equal(forced.status, 201, JSON.stringify(forced.body));
  assert.equal((await createdDetail(forced.body.assignment.id)).outOfArea, true, 'پذیرشِ آگاهانه در رویداد');

  const near = await readyOrder(a, 'خیر خانه');
  const ok = await assign(a, near.id, c.courierId);
  assert.equal(ok.status, 201, `همان ناحیه با نوشتنِ دیگر: ${JSON.stringify(ok.body)}`);
  assert.equal((await createdDetail(ok.body.assignment.id)).outOfArea, undefined);

  const nowhere = await readyOrder(a, '');
  const loose = await assign(a, nowhere.id, c.courierId);
  assert.equal(loose.status, 201, `بی ناحیه چیزی برای سنجیدن ندارد: ${JSON.stringify(loose.body)}`);

  //  ناحیهٔ ارسالِ ویترین (`address.zone`) بر «ناحیه» ِ نوشتهٔ مشتری مقدم است
  const zoned = await readyOrder(a, 'کارته');
  await query(`UPDATE online_orders SET address = address || $2::jsonb WHERE id=$1`,
    [zoned.id, JSON.stringify({ zone: { id: 'z-1', name: 'خیرخانه' } })]);
  const byZone = await assign(a, zoned.id, c.courierId);
  assert.equal(byZone.status, 201, `ناحیهٔ ویترین در محدوده: ${JSON.stringify(byZone.body)}`);
  const zonedFar = await readyOrder(a, 'خیرخانه');
  await query(`UPDATE online_orders SET address = address || $2::jsonb WHERE id=$1`,
    [zonedFar.id, JSON.stringify({ zone: { id: 'z-2', name: 'کارته' } })]);
  const byZoneFar = await assign(a, zonedFar.id, c.courierId);
  assert.equal(byZoneFar.body.error?.code, 'courier_out_of_area', JSON.stringify(byZoneFar.body));

  //  برداشتنِ محدوده ⇒ دوباره همه‌جا
  await update(a.token, c.courierId, { areas: [] });
  const again = await assign(a, zonedFar.id, c.courierId);
  assert.equal(again.status, 201, JSON.stringify(again.body));
});

test('S83 ۴) شاگرد محدوده را عوض نمی‌کند؛ دکانِ دیگر نه پیک را می‌بیند نه ناحیه‌های این دکان را', async () => {
  const a = await shopOwner('جدا');
  await enable(a.shopId);
  const b = await shopOwner('دکانِ دیگرِ محدوده');
  await enable(b.shopId);
  const c = await addCourier(a, 'پیکِ جدا', { areas: ['کارته'] });
  const at = now();
  for (const [shopId, name, sort] of [[a.shopId, 'کارته', 0], [a.shopId, 'خیرخانه', 1], [a.shopId, 'خیر‌خانه', 2], [b.shopId, 'ناحیهٔ پنهانِ دکانِ دیگر', 0]]) {
    await query(
      `INSERT INTO storefront_zones (id, shop_id, name, kind, center_lat, center_lng, radius_m, sort, created_at, updated_at)
       VALUES ($1,$2,$3,'radius',34.5,69.1,1000,$4,$5,$5)`, [newId('zon'), shopId, name, sort, at]);
  }
  const cfgA = await h.get('/api/shop/delivery/config', { token: a.token });
  assert.deepEqual(cfgA.body.areaChoices, ['کارته', 'خیرخانه'], 'ناحیه‌های همین دکان، بی تکرار');
  const cfgB = await h.get('/api/shop/delivery/config', { token: b.token });
  assert.deepEqual(cfgB.body.areaChoices, ['ناحیهٔ پنهانِ دکانِ دیگر']);

  const apprentice = await member(a.shopId, 'staff', 'شاگردِ محدوده');
  const denied = await update(apprentice.token, c.courierId, { areas: [] });
  assert.equal(denied.status, 403, JSON.stringify(denied.body));
  const other = await update(b.token, c.courierId, { areas: [] });
  assert.equal(other.status, 404, JSON.stringify(other.body));
  const otherList = await h.get('/api/shop/delivery/couriers', { token: b.token });
  assert.ok(!otherList.body.items.some((x) => x.id === c.courierId));
  const row = await one('SELECT areas FROM shop_couriers WHERE id=$1', [c.courierId]);
  assert.deepEqual(typeof row.areas === 'string' ? JSON.parse(row.areas) : row.areas, ['کارته']);
  //  پیکِ دکانِ دیگر به سفارشِ این دکان سپرده نمی‌شود (همان مرزِ پیشین)
  const cb = await addCourier(b, 'پیکِ دکانِ دیگر');
  const o = await readyOrder(a, 'کارته');
  const cross = await assign(a, o.id, cb.courierId, { overrideArea: true });
  assert.equal(cross.status, 404, JSON.stringify(cross.body));
});
