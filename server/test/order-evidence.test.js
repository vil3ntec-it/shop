'use strict';
/**
 * RSK-08 — پروندهٔ مدرکِ اختلافِ سفارش (`lib/order-evidence.js`، `routes/order-evidence.js`).
 *
 *   ۱) پرونده همهٔ ثبت‌های سفارشِ نمونه را دارد (خطِ زمانی، رویدادهای پیک با زمانِ دستگاه/سرور، فاصله تا پین، اسکن‌ها،
 *      عکس‌ها، تأییدِ تحویل، گزارشِ مشتری، تماس و گفت‌وگو) و هیچ موقعیت یا شمارهٔ خامی ندارد؛ چاپ escape می‌کند.
 *   ۲) دکانِ دیگر ۴۰۴، پیک ۴۰۳، کارمندِ بی `orders.manage` ۴۰۳؛ کارمندِ دارای آن عکسِ تحویل را نمی‌بیند.
 *   ۳) چرخهٔ اختلاف فقط افزودنی: یک اختلافِ باز، کلیدِ یکتا، بستن یک بار، ردیف‌های پیشین بایت‌به‌بایت همان.
 *   ۴) ⛔ پرچمِ خاموش ⇒ ۴۰۳ و هیچ ردیفی؛ پاسخِ سفارش و مأموریت با پرچمِ روشن/خاموش و پس از اختلاف بیت‌به‌بیت همان.
 */
const test = require('node:test');
const assert = require('node:assert/strict');
const crypto = require('node:crypto');
const h = require('./helpers');
const F = require('./image-fixtures');
const { query, one, many, newId, now } = require('../src/db');
const { ulid } = require('../src/lib/ulid');
const { resetAll } = require('../src/middleware/ratelimit');

const FLAG = 'shop.orders.dispute_file';
let admin = '';
let seq = 0;
const uniq = () => `${Date.now().toString(36)}${++seq}`;
const key = (p = 'rk') => `${p}-${uniq()}-key`;

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

async function setFlag(scope, k, value) {
  const r = await h.put('/api/admin/pump/live-config', { scope, key: k, value }, { token: admin });
  assert.equal(r.status, 200, JSON.stringify(r.body));
}

async function shop(label, flags = [FLAG]) {
  const u = await h.newUser(label, 'shop');
  const made = await h.post('/api/shop', { name: `دکانِ ${label}` }, { token: u.accessToken });
  assert.equal(made.status, 201, JSON.stringify(made.body));
  const s = { ...u, token: u.accessToken, shopId: made.body.shop.id };
  for (const k of ['shop.orders', 'shop.delivery', ...flags]) await setFlag(s.shopId, k, true);
  return s;
}

async function member(s, role) {
  const inv = await h.post('/api/shop/invite', { role }, { token: s.token });
  assert.equal(inv.status, 201, JSON.stringify(inv.body));
  if (role === 'courier') {
    const r = await h.post('/api/auth/staff', { code: inv.body.code, name: 'پیک', device: { uid: `c-${uniq()}`, name: 'گوشی', platform: 'android' } });
    assert.equal(r.status, 201, JSON.stringify(r.body));
    return { token: r.body.accessToken };
  }
  const u = await h.newUser(`${role}-${uniq()}`, 'shop');
  const j = await h.post('/api/shop/join', { code: inv.body.code }, { token: u.accessToken });
  assert.ok([200, 201].includes(j.status), JSON.stringify(j.body));
  const signed = await h.signIn(u, 'shop');
  assert.equal(signed.shop.actualRole, role);
  return { token: signed.accessToken, userId: u.user.id };
}

async function order(s) {
  const r = await h.post('/api/shop/orders', {
    source: 'phone', fulfillment: 'delivery', paymentMethod: 'cod',
    customer: { name: 'احمد', phone: '0791234567' },
    address: { landmark: 'نزدیکِ مسجد', text: 'کوچهٔ سوم', lat: 34.5123, lng: 69.1789 },
    delivery: { photoConsent: true },
    lines: [{ name: 'برنج', qtyMilli: 1000, unitPriceMinor: 120_000 }],
  }, { token: s.token, headers: { 'Idempotency-Key': key('order') } });
  assert.equal(r.status, 201, JSON.stringify(r.body));
  for (const to of ['accepted', 'picking', 'packed']) {
    const t = await h.post(`/api/shop/orders/${r.body.order.id}/transition`, { to }, { token: s.token });
    assert.equal(t.status, 200, `${to}: ${JSON.stringify(t.body)}`);
  }
  return r.body.order;
}

/** همهٔ مدرک‌های امروزی برای سفارش — همان ستون‌هایی که درهای خودشان می‌نویسند. */
async function seedEvidence(s, o) {
  const t0 = now() - 60 * 60 * 1000;
  const jpeg = F.jpeg(64, 48, { seed: 7 });
  const sum = crypto.createHash('sha256').update(jpeg).digest('hex');
  const courierUser = `usr_${uniq()}`;
  const courierId = ulid(t0);
  await query(
    `INSERT INTO shop_couriers (id, shop_id, user_id, name, phone, created_at, updated_at) VALUES ($1,$2,$3,'کریمِ پیک','0700000009',$4,$4)`,
    [courierId, s.shopId, courierUser, t0]);
  const aId = ulid(t0 + 1);
  await query(
    `INSERT INTO delivery_assignments (id, shop_id, order_id, attempt_no, mode, courier_id, status, expected_collect_minor,
       cash_collected_minor, code_hash, code_used_at, delivered_at, created_at, updated_at)
     VALUES ($1,$2,$3,1,'shop_courier',$4,'delivered',120000,120000,'h',$5,$5,$6,$5)`, [aId, s.shopId, o.id, courierId, t0 + 30_000, t0]);
  const ev = (n, kind, from, to, actorType, actorId, deviceTime, serverTime, detail) => query(
    `INSERT INTO delivery_events (id, shop_id, assignment_id, order_id, seq, event_key, kind, from_status, to_status, actor_type,
       actor_id, device_id, device_time, device_seq, server_time, detail)
     VALUES ($1,$2,$3,$4,$5,$6,$7,$8,$9,$10,$11,'dev-c',$12,$15,$13,$14)`,
    [ulid(serverTime), s.shopId, aId, o.id, n, `k-${uniq()}`, kind, from, to, actorType, actorId, deviceTime, serverTime, JSON.stringify(detail), n]);
  await ev(1, 'created', '', 'assigned', 'user', s.user.id, null, t0, { mode: 'shop_courier' });
  await ev(2, 'status', 'assigned', 'picked_up', 'courier', courierUser, t0 + 10_000 - 2000, t0 + 10_000, { bagsScanned: 2 });
  //  ساعتِ دستگاه ۱۰ دقیقه جلو ⇒ هشدار
  await ev(3, 'status', 'arrived', 'delivered', 'courier', courierUser, t0 + 30_000 + 600_000, t0 + 30_000,
    { distanceM: 42, codeVerified: true, cashCollectedMinor: 120000 });
  await query(
    `INSERT INTO delivery_photos (id, shop_id, assignment_id, order_id, courier_id, event_key, mime, size, hash, data, device_time, created_by, created_at)
     VALUES ($1,$2,$3,$4,$5,$6,'image/jpeg',$7,$8,$9,$10,$11,$12)`,
    [ulid(t0 + 29_000), s.shopId, aId, o.id, courierId, `ph-${uniq()}`, jpeg.length, sum, jpeg, t0 + 29_000, courierUser, t0 + 29_000]);
  await query(
    `INSERT INTO order_picks (order_id, shop_id, status, picker_id, bags, shelf_slot, original_subtotal_minor, original_total_minor,
       started_at, packed_at, updated_at) VALUES ($1,$2,'packed',$3,2,'A1',120000,120000,$4,$5,$5)`, [o.id, s.shopId, s.user.id, t0 - 5000, t0 - 1000]);
  await query(
    `INSERT INTO order_pick_events (id, order_id, shop_id, event_key, request_hash, kind, line_no, outcome, code, detail, actor_id, created_at)
     VALUES ($1,$2,$3,$4,'h','scan',1,'accepted','','{}',$5,$6)`, [ulid(t0 - 4000), o.id, s.shopId, `pe-${uniq()}`, s.user.id, t0 - 4000]);
  await query(
    'INSERT INTO order_pick_shelf_scans (order_id, shop_id, bag, slot, scanned_by, scanned_at) VALUES ($1,$2,1,$3,$4,$5)',
    [o.id, s.shopId, 'A1', s.user.id, t0 - 900]);
  await query(
    `INSERT INTO order_pick_photos (id, shop_id, order_id, event_key, mime, size, hash, data, bags, device_time, created_by, created_at)
     VALUES ($1,$2,$3,$4,'image/jpeg',$5,$6,$7,2,$8,$9,$8)`, [ulid(t0 - 2000), s.shopId, o.id, `pp-${uniq()}`, jpeg.length, sum, jpeg, t0 - 2000, s.user.id]);
  const issueId = ulid(t0 + 40_000);
  await query(
    `INSERT INTO order_issues (id, shop_id, order_id, kind, body, request_key, request_hash, created_at)
     VALUES ($1,$2,$3,'missing_item','یک کیسه نرسید',$4,'h',$5)`, [issueId, s.shopId, o.id, key('issue'), t0 + 40_000]);
  await query(
    `INSERT INTO order_issue_photos (id, shop_id, issue_id, mime, size, width, height, hash, data, created_at)
     VALUES ($1,$2,$3,'image/jpeg',$4,64,48,$5,$6,$7)`, [ulid(t0 + 41_000), s.shopId, issueId, jpeg.length, sum, jpeg, t0 + 41_000]);
  await query(
    `INSERT INTO audit_logs (id, shop_id, user_id, action, target_type, target_id, detail, created_at)
     VALUES ($1,$2,$3,'delivery.courier.call','delivery_assignment',$4,$5,$6)`,
    [newId('aud'), s.shopId, courierUser, aId, JSON.stringify({ orderId: o.id, courierId, status: 'moving' }), t0 + 20_000]);
  const conv = `cv_${uniq()}`;
  await query(
    `INSERT INTO shop_chat_conversations (id, shop_id, customer_phone, order_id, created_at, updated_at) VALUES ($1,$2,'0791234567',$3,$4,$4)`,
    [conv, s.shopId, o.id, t0 + 35_000]);
  for (const [n, sender] of [[1, 'customer'], [2, 'shop'], [3, 'customer']]) {
    await query(
      `INSERT INTO shop_chat_messages (id, conversation_id, shop_id, seq, rev, sender, client_id, request_hash, kind, body, created_at)
       VALUES ($1,$2,$3,$4,$4,$5,$6,'h','text','کجاست؟',$7)`, [`m_${uniq()}`, conv, s.shopId, n, sender, `cl-${uniq()}`, t0 + 35_000 + n]);
  }
  return { aId, courierId, issueId };
}

const file = (s, id, token = s.token) => h.get(`/api/shop/orders/evidence/${id}`, { token });
const openD = (s, id, body = {}, token = s.token) => h.post(`/api/shop/orders/evidence/${id}/disputes`,
  { kind: 'not_delivered', reason: 'مشتری می‌گوید سفارش نرسیده', requestKey: key('open'), ...body }, { token });
const noteD = (s, id, body = {}, token = s.token) => h.post(`/api/shop/orders/evidence/${id}/disputes/notes`,
  { text: 'با پیک تماس گرفتیم', requestKey: key('note'), ...body }, { token });
const resolveD = (s, id, body = {}, token = s.token) => h.post(`/api/shop/orders/evidence/${id}/disputes/resolve`,
  { outcome: 'shop_favor', text: 'عکس و کدِ تحویل هست', requestKey: key('res'), ...body }, { token });
const disputeRows = (shopId, orderId) => many(
  `SELECT * FROM audit_logs WHERE shop_id=$1 AND target_id=$2 AND action LIKE 'order.dispute.%' ORDER BY created_at, id`, [shopId, orderId]);

test('RSK-08: پرونده همهٔ ثبت‌های سفارشِ نمونه را یک‌جا دارد — بی موقعیت و شمارهٔ خام', async () => {
  const s = await shop('پرونده');
  const o = await order(s);
  const seeded = await seedEvidence(s, o);
  const r = await file(s, o.id);
  assert.equal(r.status, 200, JSON.stringify(r.body));
  const f = r.body;
  assert.equal(f.order.id, o.id);
  assert.equal(f.order.customer.phoneMasked, '+937•••••567');
  assert.equal(f.order.address.hasPin, true);
  //  خطِ زمانیِ سفارش: ساخت + سه گذار
  assert.deepEqual(f.timeline.map((e) => e.toStatus).filter(Boolean).slice(-3), ['accepted', 'picking', 'packed']);
  //  مأموریت، رویدادها و زمانِ دستگاه در برابرِ سرور
  const [a] = f.delivery.assignments;
  assert.equal(a.id, seeded.aId);
  assert.deepEqual(a.events.map((e) => e.seq), [1, 2, 3]);
  assert.deepEqual(a.events.map((e) => e.skewMs), [null, -2000, 600_000]);
  assert.equal(f.clock.samples, 2);
  assert.equal(f.clock.warnings, 1);
  assert.equal(f.clock.maxAbsSkewMs, 600_000);
  //  تأییدِ تحویل
  assert.deepEqual(a.confirmation, { hasCode: true, codeUsed: true, codeLocked: false, codeVerified: true, manualConfirm: false, signature: null });
  //  موقعیت: فقط فاصله
  assert.equal(f.location.rawRetained, false);
  assert.deepEqual(f.location.atDelivery.map((x) => x.distanceM), [42]);
  //  اسکن‌ها: جمع‌آوری، قفسه، کیسه‌ها هنگامِ گرفتن
  assert.equal(f.scans.pick.bags, 2);
  assert.equal(f.scans.pickEvents.length, 1);
  assert.deepEqual(f.scans.shelfScans.map((x) => x.slot), ['A1']);
  assert.deepEqual(f.scans.bagsAtPickup.map((x) => x.bagsScanned), [2]);
  //  عکس‌ها (فقط فراداده) و راهِ بایت‌ها
  assert.equal(f.pickPhotos.length, 1);
  assert.equal(a.photos.length, 1);
  assert.match(a.photos[0].bytes, /^\/api\/shop\/delivery\/photos\//);
  const bytes = await h.get(a.photos[0].bytes, { token: s.token });
  assert.equal(bytes.status, 200);
  //  گزارشِ مشتری با عکس
  assert.equal(f.issues.length, 1);
  assert.equal(f.issues[0].id, seeded.issueId);
  assert.equal(f.issues[0].photos.length, 1);
  //  تماس و گفت‌وگو
  assert.equal(f.contacts.none, false);
  assert.equal(f.contacts.courierCalls.length, 1);
  assert.equal(f.contacts.courierCalls[0].courierId, seeded.courierId);
  assert.deepEqual(f.contacts.conversations.map((c) => [c.fromCustomer, c.fromStaff]), [[2, 1]]);
  assert.equal(f.people.couriers[seeded.courierId], 'کریمِ پیک');
  assert.ok(f.people.users[s.user.id] !== undefined);
  assert.deepEqual(f.disputes, []);
  assert.equal(f.current, null);
  //  ⛔ نه پین و نه شمارهٔ کامل و نه بایتِ عکس
  const text = JSON.stringify(f);
  for (const bad of ['34.5123', '69.1789', '791234567', '"data"']) assert.equal(text.includes(bad), false, bad);
  //  هر دیدن در سابقه
  assert.equal((await one(`SELECT COUNT(*)::int AS n FROM audit_logs WHERE shop_id=$1 AND action='order.evidence.view' AND target_id=$2`, [s.shopId, o.id])).n, 1);

  //  سفارشِ بی هیچ مدرک ⇒ «تماسی ثبت نشده» صریح
  const bare = await order(s);
  const b = (await file(s, bare.id)).body;
  assert.equal(b.contacts.none, true);
  assert.match(b.contacts.note, /هیچ تماس/);
  assert.deepEqual([b.delivery.assignments.length, b.issues.length, b.pickPhotos.length, b.scans.pick], [0, 0, 0, null]);

  //  چاپ: HTML ِ بسته با متنِ escape‌شده
  const opened = await openD(s, o.id, { reason: '<script>alert(1)</script> نرسید' });
  assert.equal(opened.status, 201, JSON.stringify(opened.body));
  const p = await h.get(`/api/shop/orders/evidence/${o.id}/print`, { token: s.token });
  assert.equal(p.status, 200);
  const html = p.body.raw;
  assert.match(p.headers.get('content-type'), /text\/html/);
  assert.match(p.headers.get('content-security-policy'), /default-src 'none'/);
  assert.ok(html.includes('&lt;script&gt;'), 'متنِ کاربر escape شده');
  assert.equal(html.includes('<script>'), false);
  assert.ok(html.includes('کریمِ پیک'));
});

test('RSK-08: دکانِ دیگر ۴۰۴، پیک ۴۰۳، کارمندِ بی orders.manage ۴۰۳؛ کارمندِ دارای آن عکسِ تحویل را نمی‌بیند', async () => {
  const s = await shop('مالک');
  const o = await order(s);
  await seedEvidence(s, o);
  const other = await shop('همسایه');
  for (const r of [await file(other, o.id), await openD(other, o.id), await h.get(`/api/shop/orders/evidence/${o.id}/print`, { token: other.token })]) {
    assert.equal(r.status, 404, JSON.stringify(r.body));
    assert.equal(r.body.error.code, 'order_not_found');
  }
  assert.equal((await disputeRows(s.shopId, o.id)).length, 0);
  assert.equal((await disputeRows(other.shopId, o.id)).length, 0);

  const courier = await member(s, 'courier');
  for (const r of [await file(s, o.id, courier.token), await openD(s, o.id, {}, courier.token)]) {
    assert.equal(r.status, 403, JSON.stringify(r.body));
  }
  const accountant = await member(s, 'accountant');
  for (const r of [await file(s, o.id, accountant.token), await openD(s, o.id, {}, accountant.token),
    await noteD(s, o.id, {}, accountant.token), await resolveD(s, o.id, {}, accountant.token)]) {
    assert.equal(r.status, 403, JSON.stringify(r.body));
    assert.equal(r.body.error.code, 'permission_denied');
  }
  assert.equal((await disputeRows(s.shopId, o.id)).length, 0);

  const staff = await member(s, 'staff');
  const f = await file(s, o.id, staff.token);
  assert.equal(f.status, 200, JSON.stringify(f.body));
  assert.equal(f.body.delivery.photosVisible, false);
  assert.equal(f.body.delivery.assignments[0].photos, null);
  assert.equal(f.body.pickPhotos.length, 1);
  assert.equal((await openD(s, o.id, {}, staff.token)).status, 201);
});

test('RSK-08: چرخهٔ اختلاف فقط افزودنی — یک باز، کلیدِ یکتا، یک بار بستن، ردیف‌های پیشین دست‌نخورده', async () => {
  const s = await shop('چرخه');
  const o = await order(s);
  //  ورودیِ نادرست ⇒ ۴۰۰، بی ردیف
  for (const [body, code] of [[{ kind: 'x' }, 'bad_kind'], [{ reason: 'ab' }, 'bad_text'], [{ requestKey: 'k' }, 'bad_request_key']]) {
    const r = await openD(s, o.id, body);
    assert.equal(r.status, 400, JSON.stringify(r.body));
    assert.equal(r.body.error.code, code);
  }
  assert.equal((await noteD(s, o.id)).body.error.code, 'no_open_dispute');
  assert.equal((await disputeRows(s.shopId, o.id)).length, 0);

  const k = key('open');
  const first = await openD(s, o.id, { requestKey: k, kind: 'damaged' });
  assert.equal(first.status, 201, JSON.stringify(first.body));
  const d = first.body.dispute;
  assert.equal(d.status, 'open');
  assert.equal(d.kind, 'damaged');
  //  ارسالِ دوباره ⇒ همان، بی ردیفِ تازه؛ همان کلید با بدنهٔ دیگر ⇒ ۴۰۹
  const again = await openD(s, o.id, { requestKey: k, kind: 'damaged' });
  assert.equal(again.status, 200);
  assert.equal(again.body.replayed, true);
  assert.equal(again.body.dispute.id, d.id);
  assert.equal((await openD(s, o.id, { requestKey: k, kind: 'cash' })).body.error.code, 'request_key_reused');
  //  اختلافِ دوم تا اولی باز است ⇒ ۴۰۹ با شناسهٔ همان
  const second = await openD(s, o.id);
  assert.equal(second.status, 409);
  assert.equal(second.body.error.code, 'dispute_open');
  assert.equal(second.body.error.disputeId, d.id);
  const snapshot = await disputeRows(s.shopId, o.id);
  assert.equal(snapshot.length, 1);

  const n1 = await noteD(s, o.id, { disputeId: d.id });
  assert.equal(n1.status, 201, JSON.stringify(n1.body));
  assert.equal((await noteD(s, o.id, { disputeId: 'aud_000000000000000000000000' })).body.error.code, 'dispute_changed');
  const n2 = await noteD(s, o.id, { text: 'مشتری عکسِ کیسه را فرستاد' });
  assert.equal(n2.body.dispute.notes.length, 2);
  const res = await resolveD(s, o.id, { disputeId: d.id, outcome: 'customer_favor', text: 'یک کیسه جا ماند؛ بازپرداخت' });
  assert.equal(res.status, 201, JSON.stringify(res.body));
  assert.equal(res.body.dispute.status, 'resolved');
  assert.equal(res.body.dispute.resolution.outcome, 'customer_favor');
  assert.equal((await resolveD(s, o.id)).body.error.code, 'no_open_dispute');
  assert.equal((await noteD(s, o.id)).body.error.code, 'no_open_dispute');
  assert.equal((await resolveD(s, o.id, { outcome: 'x' })).body.error.code, 'bad_outcome');

  //  پس از بستن، اختلافِ تازه باز می‌شود؛ پیشینی همان می‌ماند
  const next = await openD(s, o.id, { kind: 'cash' });
  assert.equal(next.status, 201);
  const rows = await disputeRows(s.shopId, o.id);
  assert.deepEqual(rows.map((r) => r.action), ['order.dispute.open', 'order.dispute.note', 'order.dispute.note', 'order.dispute.resolve', 'order.dispute.open']);
  assert.deepEqual(rows.map((r) => Number(r.detail.seq)), [1, 2, 3, 4, 5]);
  assert.deepEqual(rows[0], snapshot[0], 'ردیفِ بازکردن پس از یادداشت و بستن بایت‌به‌بایت همان');
  assert.ok(rows.every((r) => r.user_id === s.user.id && r.target_type === 'online_order'));

  const f = (await file(s, o.id)).body;
  assert.deepEqual(f.disputes.map((x) => x.status), ['resolved', 'open']);
  assert.equal(f.disputes[0].notes.length, 2);
  assert.equal(f.current.id, next.body.dispute.id);
});

const assignD = (s, id, body = {}, token = s.token) => h.post(`/api/shop/orders/evidence/${id}/disputes/assign`,
  { requestKey: key('asg'), ...body }, { token });

test('RSK-08 §۱۹.۹ بندِ ۵: مسئول و مهلتِ اختلاف — فقط صاحب/کارمندِ دارای orders.manage، مهلتِ تا ۹۰ روز، گذشتنِ مهلت فقط نما', async () => {
  const s = await shop('مسئول');
  const o = await order(s);
  const staff = await member(s, 'staff');
  const accountant = await member(s, 'accountant');
  const other = await shop('همسایهٔ مسئول');
  const hour = 60 * 60 * 1000;
  //  بی اختلافِ باز ⇒ ۴۰۹، بی ردیف
  assert.equal((await assignD(s, o.id, { assigneeId: staff.userId, dueAt: Date.now() + hour })).body.error.code, 'no_open_dispute');
  const d = (await openD(s, o.id)).body.dispute;
  assert.equal(d.assigneeId, null);
  assert.equal(d.dueAt, null);
  assert.equal(d.overdue, false);
  const before = await disputeRows(s.shopId, o.id);
  //  ورودیِ نادرست ⇒ ۴۰۰ و هیچ ردیفی
  for (const [body, code] of [
    [{ dueAt: Date.now() + hour }, 'bad_assignee'],
    [{ assigneeId: staff.userId }, 'bad_due'],
    [{ assigneeId: staff.userId, dueAt: String(Date.now() + hour) }, 'bad_due'],
    [{ assigneeId: staff.userId, dueAt: Date.now() - 24 * hour }, 'bad_due'],
    [{ assigneeId: staff.userId, dueAt: Date.now() + 91 * 24 * hour }, 'bad_due'],
    [{ assigneeId: accountant.userId, dueAt: Date.now() + hour }, 'bad_assignee'],
    [{ assigneeId: other.user.id, dueAt: Date.now() + hour }, 'bad_assignee'],
    [{ assigneeId: staff.userId, dueAt: Date.now() + hour, text: 'ab' }, 'bad_text'],
  ]) {
    const r = await assignD(s, o.id, body);
    assert.equal(r.status, 400, `${code}: ${JSON.stringify(r.body)}`);
    assert.equal(r.body.error.code, code);
  }
  assert.deepEqual(await disputeRows(s.shopId, o.id), before);
  //  حسابدار (بی orders.manage) ۴۰۳؛ دکانِ دیگر ۴۰۴
  const denied = await assignD(s, o.id, { assigneeId: staff.userId, dueAt: Date.now() + hour }, accountant.token);
  assert.equal(denied.status, 403, JSON.stringify(denied.body));
  assert.equal((await assignD(other, o.id, { assigneeId: other.user.id, dueAt: Date.now() + hour })).body.error.code, 'order_not_found');

  //  سپردن به کارمند با مهلتِ دو ساعت؛ تکرارِ همان کلید ⇒ همان، کلید با بدنهٔ دیگر ⇒ ۴۰۹
  const k = key('asg');
  const due = Date.now() + 2 * hour;
  const a1 = await assignD(s, o.id, { requestKey: k, assigneeId: staff.userId, dueAt: due, text: 'تا عصر با پیک صحبت کند' });
  assert.equal(a1.status, 201, JSON.stringify(a1.body));
  assert.equal(a1.body.dispute.assigneeId, staff.userId);
  assert.equal(a1.body.dispute.dueAt, due);
  assert.equal(a1.body.dispute.overdue, false);
  assert.equal(a1.body.dispute.assignments.length, 1);
  assert.equal(a1.body.dispute.assignments[0].text, 'تا عصر با پیک صحبت کند');
  const again = await assignD(s, o.id, { requestKey: k, assigneeId: staff.userId, dueAt: due, text: 'تا عصر با پیک صحبت کند' });
  assert.equal(again.status, 200);
  assert.equal(again.body.replayed, true);
  assert.equal((await assignD(s, o.id, { requestKey: k, assigneeId: staff.userId, dueAt: due + 1 })).body.error.code, 'request_key_reused');
  //  کارمندِ دارای orders.manage هم می‌سپارد؛ آخرین سپردن مسئولِ امروز است — مهلتِ همین حالا گذشته ⇒ overdue (فقط نما)
  const late = Date.now() - 60 * 1000;
  const a2 = await assignD(s, o.id, { assigneeId: s.user.id, dueAt: late }, staff.token);
  assert.equal(a2.status, 201, JSON.stringify(a2.body));
  assert.equal(a2.body.dispute.assigneeId, s.user.id);
  assert.equal(a2.body.dispute.overdue, true);
  assert.equal(a2.body.dispute.status, 'open', 'گذشتنِ مهلت هیچ کارِ خودکاری نمی‌کند');
  const f = (await file(s, o.id)).body;
  assert.equal(f.current.assigneeId, s.user.id);
  assert.deepEqual(f.current.assignments.map((x) => x.assigneeId), [staff.userId, s.user.id]);
  assert.ok(f.people.users[staff.userId], 'نامِ مسئول در people');
  //  فهرستِ سپردنی: صاحب و کارمندِ دارای orders.manage؛ حسابدار نه
  const can = f.assignees.map((x) => x.userId);
  assert.ok(can.includes(s.user.id) && can.includes(staff.userId));
  assert.equal(can.includes(accountant.userId), false);
  const html = (await h.get(`/api/shop/orders/evidence/${o.id}/print`, { token: s.token })).body.raw;
  assert.match(html, /مسئول:/);
  assert.match(html, /مهلت گذشته/);
  //  پس از بستن: سپردن ۴۰۹ و اختلافِ بسته دیگر «گذشته» نیست
  assert.equal((await resolveD(s, o.id)).status, 201);
  assert.equal((await assignD(s, o.id, { assigneeId: staff.userId, dueAt: Date.now() + hour })).body.error.code, 'no_open_dispute');
  const closed = (await file(s, o.id)).body.disputes[0];
  assert.equal(closed.overdue, false);
  const rows = await disputeRows(s.shopId, o.id);
  assert.deepEqual(rows.map((r) => r.action), ['order.dispute.open', 'order.dispute.assign', 'order.dispute.assign', 'order.dispute.resolve']);
  assert.deepEqual(rows[0], before[0], 'ردیفِ بازکردن پس از سپردن بایت‌به‌بایت همان');

  //  پرچمِ خاموش ⇒ ۴۰۳ و هیچ ردیفی
  const off = await shop('مسئولِ خاموش', []);
  const o2 = await order(off);
  const r = await assignD(off, o2.id, { assigneeId: off.user.id, dueAt: Date.now() + hour });
  assert.equal(r.status, 403);
  assert.equal(r.body.error.code, 'dispute_file_disabled');
  assert.equal((await disputeRows(off.shopId, o2.id)).length, 0);
});

test('RSK-08 ⛔ پرچمِ خاموش: درها ۴۰۳ و هیچ ردیفی؛ پاسخِ سفارش و مأموریت بیت‌به‌بیت همان', async () => {
  const s = await shop('خاموش', []);
  const o = await order(s);
  await seedEvidence(s, o);
  const views = async () => [
    JSON.stringify(withoutClock((await h.get(`/api/shop/orders/${o.id}`, { token: s.token })).body)),
    JSON.stringify((await h.get(`/api/shop/delivery/orders/${o.id}`, { token: s.token })).body),
  ];
  const before = await views();
  for (const r of [await file(s, o.id), await openD(s, o.id), await noteD(s, o.id), await resolveD(s, o.id),
    await h.get(`/api/shop/orders/evidence/${o.id}/print`, { token: s.token })]) {
    assert.equal(r.status, 403, JSON.stringify(r.body));
    assert.equal(r.body.error.code, 'dispute_file_disabled');
  }
  assert.equal((await one(`SELECT COUNT(*)::int AS n FROM audit_logs WHERE shop_id=$1 AND (action LIKE 'order.dispute.%' OR action='order.evidence.view')`, [s.shopId])).n, 0);
  //  پرچمِ دکانِ دیگر این دکان را باز نمی‌کند
  await shop('همسایهٔ روشن');
  assert.equal((await file(s, o.id)).status, 403);
  //  روشن ⇒ اختلاف؛ باز هم پاسخِ سفارش و مأموریت همان
  await setFlag(s.shopId, FLAG, true);
  assert.equal((await openD(s, o.id)).status, 201);
  assert.deepEqual(await views(), before);
  await setFlag(s.shopId, FLAG, false);
  assert.deepEqual(await views(), before);
  assert.equal((await file(s, o.id)).status, 403);
});

/** جزئیاتِ سفارش «زبانه» را از ساعتِ اکنون می‌سازد؛ مقایسه بی آن. */
function withoutClock(body) {
  if (body && body.order) delete body.order.tab;
  return body;
}
