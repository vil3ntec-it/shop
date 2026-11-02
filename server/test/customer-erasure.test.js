'use strict';
/**
 * SEC-10 — حذفِ دادهٔ شخصیِ مشتریِ دکان با نگه‌داشتنِ سندِ مالیِ ناشناس (`lib/customer-erasure.js`).
 *
 * آن‌چه این پرونده قفل می‌کند:
 *   ۱) ⛔ پرچمِ خاموشِ `shop.customer_erasure` ⇒ درِ صاحب و درِ مشتری همان پاسخِ درِ ناشناختهٔ امروز؛ هیچ ردیف.
 *   ۲) صاحبِ دکان: پیش‌نمایش هیچ نمی‌نویسد؛ اجرا دادهٔ شخصی را از **همهٔ** جدول‌های فهرست می‌برد؛ جمع و
 *      شمارِ مالی (سفارش، قلم، بازپرداخت، سندِ دفتر، مأموریت) بایت‌به‌بایت همان؛ مشتریِ دیگر دست نمی‌خورد؛
 *      سابقه بی شماره/نام/نشانی؛ تکرار ⇒ صفر و هیچ تغییر.
 *   ۳) رد: شاگرد، دکانِ دیگر (با شماره یا شناسهٔ سفارشِ «الف»)، نشستِ کهنه بی تأییدِ دوباره، سفارشِ باز.
 *   ۴) مشتری: فقط با بلیتِ تازهٔ همان شماره؛ مشتریِ دیگر/بلیتِ شمارهٔ دیگر رد؛ فقط همین دکان؛ حساب می‌ماند.
 */
process.env.RATE_STORE_CHECKOUT_MAX = '1000';
const test = require('node:test');
const assert = require('node:assert/strict');
const h = require('./helpers');
const { query, one, many, newId, now } = require('../src/db');
const { resetAll } = require('../src/middleware/ratelimit');

const FLAG = 'shop.customer_erasure';
const PHONE = '+93700111222';
const OTHER = '+93700333444';
const NAME = 'رحیمِ آزمونی';
const SECRETS = [PHONE, '700111222', NAME, 'مسجدِ سبز', 'کوچهٔ هفتم', 'همسایهٔ دست‌چپ', 'زنگِ دوم را بزنید',
  'بی‌پیاز', 'سلام دکان، نشانیِ من', 'عکسِ خانه', '8FVC9G8F+6W', '34.5551', '69.2075'];
let admin = '';
let seq = 0;
const uniq = () => `${Date.now().toString(36)}${++seq}`;

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

async function liveStore(label, { erasure = true } = {}) {
  const u = await h.newUser(label, 'shop');
  const made = await h.post('/api/shop', { name: `دکانِ ${label}` }, { token: u.accessToken });
  assert.equal(made.status, 201, JSON.stringify(made.body));
  const o = { ...u, token: u.accessToken, shopId: made.body.shop.id };
  await setFlag(o.shopId, 'shop.orders', true);
  await setFlag(o.shopId, 'shop.storefront', true);
  if (erasure) await setFlag(o.shopId, FLAG, true);
  const slug = `e-${uniq()}`;
  const put = await h.put('/api/shop/storefront', { slug, enabled: true, title: `ویترینِ ${label}`, minOrderMinor: 0 }, { token: o.token });
  assert.equal(put.status, 200, JSON.stringify(put.body));
  return { ...o, slug };
}

async function ticketOf(slug, phone) {
  const s = await h.post(`/api/store/${slug}/verify/start`, { phone });
  assert.equal(s.status, 200, JSON.stringify(s.body));
  const c = await h.post(`/api/store/${slug}/verify/check`, { phone, code: s.body.devCode });
  assert.equal(c.status, 200, JSON.stringify(c.body));
  return c.body.ticket;
}

/** نشستِ حسابِ این شماره (و شناسهٔ حساب). */
async function account(slug, phone) {
  const a = await h.post(`/api/store/${slug}/account`, {}, { headers: { 'X-Store-Ticket': await ticketOf(slug, phone) } });
  assert.equal(a.status, 201, JSON.stringify(a.body));
  return { session: a.body.session, id: a.body.customer.id };
}

/** متن‌های کاشته‌شدهٔ هر مشتری — مشتریِ دیگر متن‌های خودش را دارد تا جست‌وجوی نشت فقط همین را بگیرد. */
const T = { landmark: 'مسجدِ سبز', text: 'کوچهٔ هفتم', leave: 'همسایهٔ دست‌چپ', note: 'زنگِ دوم را بزنید', line: 'بی‌پیاز',
  photo: 'عکسِ خانه', body: 'سلام دکان، نشانیِ من', lat: 34.5551, lng: 69.2075, plus: '8FVC9G8F+6W' };
const T2 = { landmark: 'بازارِ کهنه', text: 'سرکِ دوم', leave: 'نگهبان', note: 'تند بیاورید', line: 'تازه',
  photo: 'عکسِ دیگر', body: 'پیامِ مشتریِ دیگر', lat: 31.1111, lng: 65.1111, plus: '7FG9V2XC+2Q' };
let orderNo = 0;
async function order(shopId, phone, { status = 'delivered', name = NAME, totalMinor = 25000, t = T } = {}) {
  const id = newId('ord');
  const at = now();
  orderNo += 1;
  await query(
    `INSERT INTO online_orders (id, shop_id, order_no, client_key, request_hash, source, status, fulfillment,
       customer_name, customer_phone, address, delivery, note, items_count, subtotal_minor, delivery_fee_minor, total_minor,
       created_at, updated_at)
     VALUES ($1,$2,$3,$4,'h','storefront',$5,'delivery',$6,$7,$8::jsonb,$9::jsonb,$10,2,$11,5000,$12,$13,$13)`,
    [id, shopId, orderNo, `ck-${id}`, status, name, phone,
      JSON.stringify({ lat: t.lat, lng: t.lng, landmark: t.landmark, text: t.text, plusCode: t.plus, zone: { id: 'z1', feeMinor: 5000 } }),
      JSON.stringify({ leaveWith: t.leave, callBeforeArrival: true, cashTenderMinor: 50000 }),
      t.note, totalMinor - 5000, totalMinor, at]
  );
  await query(
    `INSERT INTO online_order_lines (id, order_id, shop_id, line_no, product_id, name, unit, qty_milli, unit_price_minor, line_total_minor, note)
     VALUES ($1,$2,$3,1,'rice','برنج','کیلو',2000,$4,$5,$6)`,
    [newId('oln'), id, shopId, (totalMinor - 5000) / 2, totalMinor - 5000, t.line]
  );
  await query(
    `INSERT INTO online_order_events (id, order_id, shop_id, seq, kind, actor_type, note, reason, detail, created_at)
     VALUES ($1,$2,$3,1,'note','customer',$4,'',$5::jsonb,$6)`,
    [newId('oev'), id, shopId, t.note, JSON.stringify({ type: 'customer_note', fields: ['landmark'], amountMinor: 700,
      before: { landmark: t.landmark, lat: t.lat }, phone2: phone, text: t.text, who: name }), at]
  );
  const asg = newId('asg');
  await query(
    `INSERT INTO delivery_assignments (id, shop_id, order_id, attempt_no, mode, status, expected_collect_minor, cash_collected_minor, fare_minor, created_at, updated_at)
     VALUES ($1,$2,$3,1,'shop_courier','delivered',$4,$4,3000,$5,$5)`,
    [asg, shopId, id, totalMinor, at]
  );
  await query(
    `INSERT INTO delivery_events (id, shop_id, assignment_id, order_id, seq, event_key, kind, actor_type, note, reason, detail, server_time)
     VALUES ($1,$2,$3,$4,1,$5,'status','courier',$6,'no_answer',$7::jsonb,$8)`,
    [newId('dev'), shopId, asg, id, `k-${id}`, `${t.leave} گرفت`, JSON.stringify({ lat: t.lat, lng: t.lng, collectedMinor: totalMinor, to: 'delivered' }), at]
  );
  const jpeg = Buffer.from(t.photo);
  await query(
    `INSERT INTO delivery_photos (id, shop_id, assignment_id, order_id, event_key, mime, size, hash, data, created_at)
     VALUES ($1,$2,$3,$4,$5,'image/jpeg',$6,'x',$7,$8)`,
    [newId('dph'), shopId, asg, id, `p-${id}`, jpeg.length, jpeg, at]
  );
  await query(
    `INSERT INTO order_picks (order_id, shop_id, status, original_subtotal_minor, original_total_minor, started_at, updated_at)
     VALUES ($1,$2,'packed',$3,$4,$5,$5)`, [id, shopId, totalMinor - 5000, totalMinor, at]);
  await query(
    `INSERT INTO order_pick_photos (id, shop_id, order_id, event_key, mime, size, hash, data, created_at)
     VALUES ($1,$2,$3,$4,'image/jpeg',$5,'x',$6,$7)`,
    [newId('pph'), shopId, id, `pp-${id}`, jpeg.length, jpeg, at]
  );
  await query(
    'INSERT INTO delivery_locations (assignment_id, shop_id, lat, lng, server_time) VALUES ($1,$2,$3,$4,$5)',
    [asg, shopId, t.lat, t.lng, at]
  );
  await query(
    `INSERT INTO customer_notify_outbox (id, shop_id, order_id, event, phone_key, status, last_error, next_attempt_at, created_at, updated_at)
     VALUES ($1,$2,$3,'order_completed','k-phone','pending',$4,$5,$5,$5)`,
    [newId('cno'), shopId, id, `sms to ${phone} failed`, at]
  );
  return id;
}

/** گفت‌وگو با پیام، رسانه، سنجاق و گزارش. */
async function chat(shopId, phone, { customerId = null, orderId = '', name = NAME, t = T } = {}) {
  const conv = newId('cnv');
  const at = now();
  await query(
    `INSERT INTO shop_chat_conversations (id, shop_id, customer_phone, customer_name, order_id, customer_id, seq, rev, created_at, updated_at)
     VALUES ($1,$2,$3,$4,$5,$6,2,2,$7,$7)`,
    [conv, shopId, phone, name, orderId, customerId, at]
  );
  const media = newId('cmd');
  const img = Buffer.from(t.photo);
  await query(
    `INSERT INTO shop_chat_media (id, shop_id, conversation_id, uploader, role, kind, mime, size, sha256, data, created_at)
     VALUES ($1,$2,$3,'customer','main','image','image/jpeg',$4,'x',$5,$6)`,
    [media, shopId, conv, img.length, img, at]
  );
  const m1 = newId('cmsg');
  await query(
    `INSERT INTO shop_chat_messages (id, conversation_id, shop_id, seq, rev, sender, client_id, request_hash, kind, body, meta, created_at)
     VALUES ($1,$2,$3,1,1,'customer',$4,'h','text',$5,$6::jsonb,$7)`,
    [m1, conv, shopId, `c-${m1}`, `${t.body} ${t.text} است`, JSON.stringify({ geo: { lat: t.lat } }), at]
  );
  await query(
    `INSERT INTO shop_chat_messages (id, conversation_id, shop_id, seq, rev, sender, client_id, request_hash, kind, body, media_id, created_at)
     VALUES ($1,$2,$3,2,2,'customer',$4,'h','image','',$5,$6)`,
    [newId('cmsg'), conv, shopId, `c2-${m1}`, media, at]
  );
  await query("INSERT INTO shop_chat_pins (conversation_id, message_id, side, shop_id, created_at) VALUES ($1,$2,'shop',$3,$4)", [conv, m1, shopId, at]);
  await query(
    `INSERT INTO shop_chat_reports (id, shop_id, conversation_id, message_id, reporter, reason, note, snapshot, created_at)
     VALUES ($1,$2,$3,$4,'shop','abuse',$5,$6::jsonb,$7)`,
    [newId('crp'), shopId, conv, m1, `مزاحمت از ${phone}`, JSON.stringify({ body: t.body, name }), at]
  );
  return conv;
}

async function extras(shopId, customerId, phone) {
  const at = now();
  if (customerId) {
    await query("INSERT INTO store_customer_carts (customer_id, shop_id, items, updated_at) VALUES ($1,$2,'[{\"productId\":\"rice\",\"qtyMilli\":1000}]'::jsonb,$3)", [customerId, shopId, at]);
    await query("INSERT INTO store_customer_favorites (customer_id, shop_id, product_id, created_at) VALUES ($1,$2,'rice',$3)", [customerId, shopId, at]);
  }
  await query(
    'INSERT INTO storefront_area_requests (id, shop_id, phone, cell_lat, cell_lng, created_at, updated_at) VALUES ($1,$2,$3,34.55,69.2,$4,$4)',
    [newId('sar'), shopId, phone, at]
  );
}

/** ردیف‌های ناشناس‌نشدنیِ مالی و شمارش‌ها — باید بایت‌به‌بایت همان بمانند. */
async function money(shopId) {
  return {
    orders: await many(
      `SELECT id, order_no, status, payment_status, cod_status, items_count, subtotal_minor, discount_minor, delivery_fee_minor,
              total_minor, currency, created_at, updated_at, address->'zone' AS zone,
              (delivery->>'cashTenderMinor') AS tender FROM online_orders WHERE shop_id=$1 ORDER BY id`, [shopId]),
    totals: await one(
      `SELECT COUNT(*)::int AS n, COALESCE(SUM(total_minor),0)::text AS total, COUNT(DISTINCT customer_phone)::int AS customers
         FROM online_orders WHERE shop_id=$1`, [shopId]),
    lines: await many('SELECT id, order_id, product_id, name, unit, qty_milli, unit_price_minor, line_total_minor FROM online_order_lines WHERE shop_id=$1 ORDER BY id', [shopId]),
    refunds: await many('SELECT * FROM online_order_refunds WHERE shop_id=$1 ORDER BY id', [shopId]),
    ledger: await many('SELECT * FROM order_ledger_docs WHERE shop_id=$1 ORDER BY id', [shopId]),
    assignments: await many('SELECT * FROM delivery_assignments WHERE shop_id=$1 ORDER BY id', [shopId]),
    events: await one('SELECT COUNT(*)::int AS n FROM online_order_events WHERE shop_id=$1', [shopId]),
    messages: await one('SELECT COUNT(*)::int AS n FROM shop_chat_messages WHERE shop_id=$1', [shopId]),
  };
}

const PII_TABLES = ['online_orders', 'online_order_lines', 'online_order_events', 'delivery_events', 'delivery_photos',
  'order_pick_photos', 'delivery_locations', 'customer_notify_outbox', 'shop_chat_conversations', 'shop_chat_messages',
  'shop_chat_media', 'shop_chat_pins', 'shop_chat_reports', 'store_customer_carts', 'store_customer_favorites', 'storefront_area_requests'];

/** متنِ کاملِ هر ردیفِ این دکان در جدول‌های فهرست (bytea به متن). */
async function dump(shopId, where = '') {
  const out = {};
  for (const t of PII_TABLES) {
    const rows = await many(`SELECT row_to_json(x)::text AS j, ${t === 'delivery_photos' || t === 'order_pick_photos' || t === 'shop_chat_media' ? "convert_from(data, 'UTF8')" : "''"} AS b
                               FROM ${t} x WHERE shop_id=$1 ${where} ORDER BY 1`, [shopId]);
    out[t] = rows.map((r) => r.j + r.b);
  }
  return out;
}
const leaks = (d, secrets = SECRETS) => Object.entries(d).flatMap(([t, rows]) =>
  rows.flatMap((r) => secrets.filter((s) => r.includes(s)).map((s) => `${t}: ${s}`)));

async function seedCustomer(s, customerId = null) {
  const o1 = await order(s.shopId, PHONE);
  const o2 = await order(s.shopId, PHONE, { status: 'refunded_full', totalMinor: 18000 });
  await query(
    `INSERT INTO online_order_refunds (id, order_id, shop_id, seq, client_key, request_hash, amount_minor, reason, created_at)
     VALUES ($1,$2,$3,1,'rk','h',18000,'کالا خراب بود',$4)`, [newId('orf'), o2, s.shopId, now()]);
  await query(
    `INSERT INTO order_ledger_docs (id, shop_id, order_id, kind, doc_no, sale_row_id, on_status, total_minor, chain, chain_seq, hash, created_at)
     VALUES ($1,$2,$3,'sale',$4,'sale-1','delivered',25000,'W',1,'00',$5)`, [newId('old'), s.shopId, o1, orderNo, now()]);
  await chat(s.shopId, PHONE, { customerId });
  await chat(s.shopId, PHONE, { orderId: o1 });
  await extras(s.shopId, customerId, PHONE);
  //  مشتریِ دیگرِ همین دکان — نباید دست بخورد
  const other = await order(s.shopId, OTHER, { name: 'کریمِ دیگر', t: T2 });
  await chat(s.shopId, OTHER, { name: 'کریمِ دیگر', t: T2 });
  return { o1, o2, other };
}

const erase = (s, body, token = s.token) => h.post('/api/shop/customer-erasure', body, { token });

test('SEC-10 ⛔: پرچمِ خاموش ⇒ درِ صاحب و درِ مشتری همان پاسخِ درِ ناشناختهٔ امروز؛ هیچ ردیفی عوض نمی‌شود', async () => {
  const s = await liveStore('حذفِ خاموش', { erasure: false });
  const acc = await account(s.slug, PHONE);
  await seedCustomer(s, acc.id);
  const before = await dump(s.shopId);
  const unknownShop = await h.post('/api/shop/no-such-door', { phone: PHONE }, { token: s.token });
  const off = await erase(s, { phone: PHONE });
  assert.equal(off.status, unknownShop.status);
  assert.deepEqual(off.body, unknownShop.body, 'همان پاسخِ درِ ناشناختهٔ /api/shop');
  const unauth = await h.post('/api/shop/customer-erasure', { phone: PHONE });
  const unknownAnon = await h.post('/api/shop/no-such-door', { phone: PHONE });
  assert.deepEqual([unauth.status, unauth.body], [unknownAnon.status, unknownAnon.body], 'بی توکن هم همان');

  const headers = { 'X-Store-Session': acc.session, 'X-Store-Ticket': await ticketOf(s.slug, PHONE) };
  const unknownStore = await h.post(`/api/store/${s.slug}/account/no-such-door`, {}, { headers });
  const offStore = await h.post(`/api/store/${s.slug}/account/erase`, {}, { headers });
  assert.equal(offStore.status, unknownStore.status);
  assert.deepEqual(offStore.body, unknownStore.body, 'همان پاسخِ درِ ناشناختهٔ /api/store');
  const ghost = await h.post('/api/store/no-such-store-xyz/account/erase', {}, { headers });
  const ghostUnknown = await h.post('/api/store/no-such-store-xyz/account/no-such-door', {}, { headers });
  assert.deepEqual([ghost.status, ghost.body], [ghostUnknown.status, ghostUnknown.body], 'نشانیِ ناشناس هم همان');
  assert.deepEqual(await dump(s.shopId), before, 'هیچ ردیفی عوض نشد');
  assert.equal((await one("SELECT COUNT(*)::int AS n FROM audit_logs WHERE action LIKE '%erased'")).n, 0);
});

test('SEC-10: صاحبِ دکان — پیش‌نمایش بی نوشتن؛ اجرا دادهٔ شخصی را از همهٔ جدول‌ها می‌برد و جمعِ مالی همان می‌ماند؛ سابقه بی دادهٔ شخصی؛ تکرار بی اثر', async () => {
  const s = await liveStore('حذفِ صاحب');
  const acc = await account(s.slug, PHONE);
  const { o1, other } = await seedCustomer(s, acc.id);
  const before = await dump(s.shopId);
  assert.ok(leaks(before).length > 20, 'دادهٔ شخصی واقعاً کاشته شده');
  const moneyBefore = await money(s.shopId);
  const otherBefore = await dump(s.shopId, `AND row_to_json(x)::text LIKE '%${OTHER}%'`);

  const preview = await erase(s, { phone: '0700111222', dryRun: true });
  assert.equal(preview.status, 200, JSON.stringify(preview.body));
  assert.equal(preview.body.dryRun, true);
  assert.equal(preview.body.erasureId, undefined);
  assert.equal(preview.body.openOrders, 0);
  assert.deepEqual(await dump(s.shopId), before, 'پیش‌نمایش هیچ نمی‌نویسد');
  assert.equal((await one("SELECT COUNT(*)::int AS n FROM audit_logs WHERE action='shop.customer_erased'")).n, 0);

  const done = await erase(s, { orderId: o1 });
  assert.equal(done.status, 200, JSON.stringify(done.body));
  assert.match(done.body.erasureId, /^era_[0-9a-f]{24}$/);
  assert.equal(done.body.mode, 'order');
  assert.deepEqual(done.body.counts, preview.body.counts, 'پیش‌نمایش همان شمارشِ اجرا');
  assert.deepEqual(done.body.counts, {
    orders: 2, orderLines: 2, orderEvents: 2, deliveryEvents: 2, deliveryPhotos: 2, pickPhotos: 2, deliveryLocations: 2,
    notifyOutbox: 2, conversations: 2, messages: 4, chatMedia: 2, chatPins: 2, chatReports: 2, carts: 1, favorites: 1, areaRequests: 1,
  });
  assert.ok(done.body.kept.includes('order_ledger_docs') && done.body.kept.includes('device_sync_ledger'));

  //  دادهٔ شخصی از هیچ جدولی برنمی‌گردد — جز ردیف‌های مشتریِ دیگر که همان مانده‌اند
  const after = await dump(s.shopId);
  assert.deepEqual(leaks(after), [], 'هیچ دادهٔ شخصیِ این مشتری نماند');
  assert.deepEqual(await dump(s.shopId, `AND row_to_json(x)::text LIKE '%${OTHER}%'`), otherBefore, 'مشتریِ دیگر دست نخورد');
  const o = await one('SELECT * FROM online_orders WHERE id=$1', [o1]);
  assert.equal(o.customer_phone, `erased:${done.body.erasureId}`);
  assert.deepEqual(o.address, { zone: { id: 'z1', feeMinor: 5000 } }, 'ناحیه سندِ هزینهٔ ارسال است و می‌ماند');
  assert.deepEqual(o.delivery, { callBeforeArrival: true, cashTenderMinor: 50000 });
  const ev = await one("SELECT note, detail FROM online_order_events WHERE order_id=$1", [o1]);
  assert.deepEqual(ev, { note: '', detail: { type: 'customer_note', fields: ['landmark'], amountMinor: 700, before: {}, who: '' } }, 'کد و مبلغ می‌مانند');
  const msgs = await many("SELECT body, media_id, deleted_at, deleted_by FROM shop_chat_messages WHERE shop_id=$1 AND conversation_id IN (SELECT id FROM shop_chat_conversations WHERE customer_phone LIKE 'erased:%')", [s.shopId]);
  assert.equal(msgs.length, 4);
  assert.ok(msgs.every((m) => m.body === '' && m.media_id === null && Number(m.deleted_at) > 0 && m.deleted_by === 'erasure'));
  assert.equal((await one('SELECT COUNT(*)::int AS n FROM store_customers WHERE id=$1', [acc.id])).n, 1, 'حسابِ سراسریِ مشتری مالِ دکان نیست و می‌ماند');
  assert.ok((await one('SELECT * FROM online_orders WHERE id=$1', [other])).customer_name === 'کریمِ دیگر');

  //  جمع و شمارِ مالی بایت‌به‌بایت همان
  assert.deepEqual(await money(s.shopId), moneyBefore);

  //  سابقه: یک ردیف، بی هیچ دادهٔ شخصی یا شناسهٔ سفارش
  const logs = await many("SELECT * FROM audit_logs WHERE shop_id=$1 AND action='shop.customer_erased'", [s.shopId]);
  assert.equal(logs.length, 1);
  assert.equal(logs[0].target_id, done.body.erasureId);
  const logText = JSON.stringify(logs[0]);
  assert.deepEqual(SECRETS.filter((x) => logText.includes(x)), []);
  assert.ok(!logText.includes(o1) && !logText.includes(acc.id), 'نه شناسهٔ سفارش، نه شناسهٔ حساب');
  assert.equal(logs[0].detail.total, Object.values(done.body.counts).reduce((a, b) => a + b, 0));

  //  تکرار (با شماره یا همان سفارش): هیچ چیز برای پاک کردن نیست و هیچ ردیفی عوض نمی‌شود
  const snap = await dump(s.shopId);
  for (const body of [{ phone: PHONE }, { orderId: o1 }]) {
    const again = await erase(s, body);
    assert.equal(again.status, 200, JSON.stringify(again.body));
    assert.equal(again.body.total, 0, JSON.stringify(again.body.counts));
  }
  assert.deepEqual(await dump(s.shopId), snap, 'تکرار هیچ ردیفی را عوض نکرد');
  assert.deepEqual(await money(s.shopId), moneyBefore);
});

test('SEC-10: رد — شاگرد، دکانِ دیگر، نشستِ بی تأییدِ دوباره و سفارشِ باز؛ دادهٔ «الف» دست نمی‌خورد', async () => {
  const a = await liveStore('حذفِ الف');
  const b = await liveStore('حذفِ ب');
  const { o1 } = await seedCustomer(a);
  const before = await dump(a.shopId);

  //  شاگردِ «الف»
  const st = await h.newUser('شاگردِ الف', 'shop');
  await query(
    `INSERT INTO shop_members (id, shop_id, user_id, role, status, created_at, updated_at) VALUES ($1,$2,$3,'staff','active',$4,$4)`,
    [newId('mem'), a.shopId, st.user.id, now()]
  );
  const staff = await erase(a, { phone: PHONE }, st.accessToken);
  assert.equal(staff.status, 403, JSON.stringify(staff.body));
  assert.equal(staff.body.error.code, 'owner_only');
  const staffPreview = await erase(a, { phone: PHONE, dryRun: true }, st.accessToken);
  assert.equal(staffPreview.status, 403);

  //  دکانِ «ب» با شمارهٔ مشتریِ «الف» ⇒ فقط فضای «ب» (هیچ)؛ با شناسهٔ سفارشِ «الف» ⇒ ۴۰۴؛ `shopId` ِ بدنه نادیده
  const viaPhone = await erase(b, { phone: PHONE, shopId: a.shopId });
  assert.equal(viaPhone.status, 200, JSON.stringify(viaPhone.body));
  assert.equal(viaPhone.body.total, 0);
  const viaOrder = await erase(b, { orderId: o1 });
  assert.equal(viaOrder.status, 404);
  assert.equal(viaOrder.body.error.code, 'order_not_found');
  assert.ok(!JSON.stringify(viaOrder.body).includes(o1));

  //  نشستِ کهنه (بی تأییدِ دوبارهٔ هویت در چند دقیقهٔ گذشته) ⇒ ۴۰۳؛ پیش‌نمایش آزاد است
  await query("DELETE FROM login_attempts WHERE scope='session_auth'");
  const stale = await erase(a, { phone: PHONE });
  assert.equal(stale.status, 403, JSON.stringify(stale.body));
  assert.equal(stale.body.error.code, 'reauth_required');
  assert.equal((await erase(a, { phone: PHONE, dryRun: true })).status, 200);
  const fresh = await h.post('/api/auth/reauth', { password: a.password }, { token: a.token });
  assert.equal(fresh.status, 200, JSON.stringify(fresh.body));

  //  سفارشِ باز ⇒ ۴۰۹ با شمارش؛ هیچ چیز پاک نمی‌شود
  const open = await order(a.shopId, PHONE, { status: 'out_for_delivery' });
  const blocked = await erase(a, { phone: PHONE });
  assert.equal(blocked.status, 409, JSON.stringify(blocked.body));
  assert.equal(blocked.body.error.code, 'open_orders');
  assert.equal(blocked.body.error.openOrders ?? blocked.body.openOrders, 1);
  await query('DELETE FROM online_order_lines WHERE order_id=$1', [open]);
  await query('DELETE FROM online_order_events WHERE order_id=$1', [open]);
  await query('DELETE FROM delivery_photos WHERE order_id=$1', [open]);
  await query('DELETE FROM order_pick_photos WHERE order_id=$1', [open]);
  await query('DELETE FROM delivery_events WHERE order_id=$1', [open]);
  await query('DELETE FROM delivery_locations WHERE assignment_id IN (SELECT id FROM delivery_assignments WHERE order_id=$1)', [open]);
  await query('DELETE FROM customer_notify_outbox WHERE order_id=$1', [open]);
  await query('DELETE FROM delivery_assignments WHERE order_id=$1', [open]);
  await query('DELETE FROM online_orders WHERE id=$1', [open]);
  assert.deepEqual(await dump(a.shopId), before, 'هیچ ردی چیزی از «الف» عوض نکرد');
  assert.equal((await one("SELECT COUNT(*)::int AS n FROM audit_logs WHERE shop_id=$1 AND action='shop.customer_erased'", [a.shopId])).n, 0);

  //  ورودیِ بد
  const bad = await erase(a, {});
  assert.equal(bad.status, 400);
  assert.equal(bad.body.error.code, 'bad_target');
});

test('SEC-10: مشتری از حسابِ ویترین — فقط با بلیتِ تازهٔ همان شماره، فقط همین دکان؛ مشتریِ دیگر رد؛ حساب می‌ماند', async () => {
  const s = await liveStore('حذفِ مشتری');
  const t = await liveStore('دکانِ دیگرِ مشتری');
  const acc = await account(s.slug, PHONE);
  await seedCustomer(s, acc.id);
  await order(t.shopId, PHONE);
  const otherShopBefore = await dump(t.shopId);
  const url = `/api/store/${s.slug}/account/erase`;

  assert.equal((await h.post(url, {})).body.error.code, 'session_required');
  const noTicket = await h.post(url, {}, { headers: { 'X-Store-Session': acc.session } });
  assert.equal(noTicket.status, 401);
  assert.equal(noTicket.body.error.code, 'verification_required');
  //  مشتریِ دیگر (نشستِ خودش + بلیتِ شمارهٔ دیگر) فقط به دادهٔ خودش می‌رسد؛ بلیتِ شمارهٔ دیگر روی این حساب ⇒ ۴۰۳
  const mismatch = await h.post(url, {}, { headers: { 'X-Store-Session': acc.session, 'X-Store-Ticket': await ticketOf(s.slug, OTHER) } });
  assert.equal(mismatch.status, 403, JSON.stringify(mismatch.body));
  assert.equal(mismatch.body.error.code, 'phone_mismatch');
  const intruder = await account(s.slug, '+93700555666');
  const theirs = await h.post(url, {}, { headers: { 'X-Store-Session': intruder.session, 'X-Store-Ticket': await ticketOf(s.slug, '+93700555666') } });
  assert.equal(theirs.status, 200, JSON.stringify(theirs.body));
  assert.equal(theirs.body.total, 0, 'مشتریِ دیگر هیچ دادهٔ این مشتری را پاک نکرد');
  assert.ok(leaks(await dump(s.shopId)).length > 20);

  const preview = await h.post(url, { dryRun: true }, { headers: { 'X-Store-Session': acc.session } });
  assert.equal(preview.status, 200, JSON.stringify(preview.body));
  assert.equal(preview.body.mode, 'account');
  const headers = { 'X-Store-Session': acc.session, 'X-Store-Ticket': await ticketOf(s.slug, PHONE) };
  const done = await h.post(url, {}, { headers });
  assert.equal(done.status, 200, JSON.stringify(done.body));
  assert.deepEqual(done.body.counts, preview.body.counts);
  assert.ok(done.body.total > 20);
  assert.deepEqual(leaks(await dump(s.shopId)), []);
  assert.deepEqual(await dump(t.shopId), otherShopBefore, 'دکانِ دیگر دست نخورد');
  assert.equal((await one('SELECT COUNT(*)::int AS n FROM store_customers WHERE id=$1', [acc.id])).n, 1, 'حساب می‌ماند');

  const logs = await many("SELECT * FROM audit_logs WHERE shop_id=$1 AND action='store_customer.erased'", [s.shopId]);
  assert.equal(logs.length, 2, 'دو درخواستِ اجراشده (مشتریِ دیگر با صفر، و همین)');
  const mine = logs.find((l) => l.target_id === done.body.erasureId);
  assert.ok(mine);
  assert.equal(mine.actor_type, 'customer');
  assert.equal(mine.ip, '');
  const logText = JSON.stringify(logs);
  assert.deepEqual(SECRETS.filter((x) => logText.includes(x)), []);
  assert.ok(!logText.includes(acc.id));

  const again = await h.post(url, {}, { headers });
  assert.equal(again.status, 200, JSON.stringify(again.body));
  assert.equal(again.body.total, 0);
});

test('SEC-10: scrub ِ جزئیات — کد، شناسه و عدد می‌مانند؛ متنِ آزاد، شماره و مختصات نه', () => {
  const { scrub, settled } = require('../src/lib/customer-erasure');
  assert.deepEqual(
    scrub({ type: 'amend', id: '01JABCDEFGHJKMNPQRSTVWXYZ0', amountMinor: 1200, ok: true, reason: 'out_of_stock',
      free: 'لطفاً زنگ بزنید', raw: '0700111222', plus: '+93700111222', geo: { lat: 1, lng: 2, at: 5 }, list: ['a_b', 'متن'] }),
    { type: 'amend', id: '01JABCDEFGHJKMNPQRSTVWXYZ0', amountMinor: 1200, ok: true, reason: 'out_of_stock',
      free: '', raw: '', plus: '', geo: { at: 5 }, list: ['a_b', ''] }
  );
  assert.deepEqual(['delivered', 'picked_up', 'cancelled', 'refunded_full', 'restocked', 'new', 'out_for_delivery', 'return_requested', 'returned_to_store'].map(settled),
    [true, true, true, true, true, false, false, false, false]);
});
