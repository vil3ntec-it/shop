'use strict';
/**
 * میزِ سفارشِ دکان (lib/order-desk.js، مهاجرتِ ۱۵۷) — ORD-08، ORD-09، ORD-04.
 *
 *   - پرچمِ `shop.orders.desk` خاموش ⇒ `GET` فقط `{enabled:false}`، نوشتن ۴۰۳، ویترین و صف دست‌نخورده، هیچ ردیف.
 *   - «شلوغم» (ORD-08): ساعتِ سرور، پایانِ خودکار با گذشتِ زمان، `busy` در وضعیتِ عمومیِ ویترین؛ ۰ = پایان.
 *   - توقفِ موقت (ORD-08): سفارشِ تازهٔ ویترین ۴۰۹ `store_paused`؛ تکرارِ سفارشِ پیشین آزاد؛ سفارشِ دستیِ کارمند
 *     آزاد؛ وضعیتِ عمومی `orderAcceptance: 'paused'` بی دلیل و توضیحِ داخلی؛ «ادامه» و پایانِ خودکار.
 *   - کلیدِ یکتا: تکرار ⇒ بی اثرِ دوم؛ همان شناسه با بدنهٔ دیگر ⇒ ۴۰۹؛ تنظیم با `expectedVersion`.
 *   - قبولِ خودکار (ORD-04): فقط وقتی دکان «می‌پذیرد»، با سقفِ صف، زمانِ آماده (+ «شلوغم»)، بازیگرِ سیستم؛
 *     زمان‌بندی‌شده و دکانِ بی‌اتصال نه؛ سفارش‌های هم‌زمان از سقف نمی‌گذرند.
 *   - توقفِ خودکار (ORD-09): صفِ بیش از حد یا سفارشِ بی‌پاسخ ⇒ یک بار توقف با سابقه؛ «ادامه» ِ دستی مهلت دارد.
 *   - اجازه‌ها، اشتراک (SUB-10) و جداییِ دکان‌ها.
 */
process.env.RATE_STORE_CHECKOUT_MAX = '500';
process.env.RATE_STORE_CODE_MAX = '500';
const test = require('node:test');
const assert = require('node:assert/strict');
const h = require('./helpers');
const { query, one, many, newId, now } = require('../src/db');
const { resetAll } = require('../src/middleware/ratelimit');
const desk = require('../src/lib/order-desk');

const MIN = 60_000;
const DAY = 24 * 60 * MIN;
let admin = '';
let seq = 0;
const uniq = () => `${Date.now().toString(36)}${++seq}`;
const ev = () => `ev-${uniq()}-desk`;

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
test.after(async () => { desk.stop(); await h.stop(); });
test.beforeEach(() => resetAll());

async function setFlag(scope, key, value) {
  const r = await h.put('/api/admin/pump/live-config', { scope, key, value }, { token: admin });
  assert.equal(r.status, 200, JSON.stringify(r.body));
}

/** دکانِ روشن با ویترین، یک کالا و دستگاهی که همین حالا همگام شد ⇒ «همین لحظه می‌پذیرد». */
async function liveStore(label, { deskOn = true } = {}) {
  const u = await h.newUser(label, 'shop');
  const made = await h.post('/api/shop', { name: `دکانِ ${label}` }, { token: u.accessToken });
  assert.equal(made.status, 201, JSON.stringify(made.body));
  const o = { ...u, token: u.accessToken, shopId: made.body.shop.id };
  await setFlag(o.shopId, 'shop.orders', true);
  await setFlag(o.shopId, 'shop.storefront', true);
  if (deskOn) await setFlag(o.shopId, 'shop.orders.desk', true);
  const slug = `dk-${uniq()}`;
  const put = await h.put('/api/shop/storefront', { slug, enabled: true, title: `ویترینِ ${label}`, contactPhone: '0700000001' },
    { token: o.token });
  assert.equal(put.status, 200, JSON.stringify(put.body));
  const dev = `dev-${uniq()}`;
  const p = await h.api('POST', '/api/sync/v1/push', {
    token: o.token, headers: { 'X-App': 'shop', 'X-Device': dev },
    body: { device_id: dev, schema_version: 2, ops: [{
      op_id: `op-${uniq()}`, ts: Date.now(), table: 'products', row_id: 'rice', type: 'insert',
      fields: { name: 'برنج', unit: 'کیلو', salePrice: 120, category: 'خوراکی' },
    }] },
  });
  assert.equal(p.status, 200, JSON.stringify(p.body));
  const r = await h.put('/api/shop/storefront/products/rice', { published: true }, { token: o.token });
  assert.equal(r.status, 200, JSON.stringify(r.body));
  return { ...o, slug };
}

const tickets = new Map();
async function ticketFor(slug, phone) {
  const k = `${slug}|${phone}`;
  if (tickets.has(k)) return tickets.get(k);
  const s = await h.post(`/api/store/${slug}/verify/start`, { phone });
  const c = await h.post(`/api/store/${slug}/verify/check`, { phone, code: s.body.devCode });
  assert.equal(c.status, 200, JSON.stringify(c.body));
  tickets.set(k, c.body.ticket);
  return c.body.ticket;
}

const checkout = async (s, { phone = '0791113101', key = `ck-${uniq()}-key`, over = {} } = {}) => {
  const r = await h.post(`/api/store/${s.slug}/checkout`, {
    fulfillment: 'pickup', customer: { name: 'مشتری', phone }, lines: [{ productId: 'rice', qtyMilli: 1000 }], ...over,
  }, { headers: { 'Idempotency-Key': key, 'X-Store-Ticket': await ticketFor(s.slug, phone) } });
  return { ...r, key };
};
const rowOfKey = (s, key) => one('SELECT id, status, ready_at, created_at FROM online_orders WHERE shop_id=$1 AND client_key=$2', [s.shopId, key]);
const deskGet = (s) => h.get('/api/shop/orders/desk', { token: s.token });
const post = (s, path, body, token = s.token) => h.post(`/api/shop/orders/desk/${path}`, body, { token });
const put = (s, body, token = s.token) => h.put('/api/shop/orders/desk/settings', body, { token });
const pub = async (s) => {
  const r = await h.get(`/api/store/${s.slug}`);
  assert.equal(r.status, 200, JSON.stringify(r.body));
  return r.body.store.status;
};
const events = (s) => many('SELECT kind, actor_type, reason, until_at FROM order_desk_events WHERE shop_id=$1 ORDER BY created_at, id', [s.shopId]);
const staffOrder = (s, token = s.token) => h.post('/api/shop/orders', {
  source: 'phone', fulfillment: 'pickup', customer: { name: 'تلفنی' }, lines: [{ name: 'چای', qtyMilli: 1000, unitPriceMinor: 5000 }],
}, { token, headers: { 'Idempotency-Key': `st-${uniq()}-key` } });

test('پرچم خاموش: GET ⇒ enabled:false؛ نوشتن ۴۰۳؛ ویترین و صف همان؛ هیچ ردیف', async () => {
  const s = await liveStore('خاموش', { deskOn: false });
  assert.deepEqual((await deskGet(s)).body, { enabled: false });
  for (const [path, body] of [['busy', { eventId: ev(), minutes: 30 }], ['pause', { eventId: ev(), minutes: 30, reason: 'staff' }], ['resume', { eventId: ev() }]]) {
    const r = await post(s, path, body);
    assert.equal(r.status, 403, JSON.stringify(r.body));
    assert.equal(r.body.error.code, 'order_desk_disabled');
  }
  assert.equal((await put(s, { autoAccept: { enabled: true } })).body.error.code, 'order_desk_disabled');
  //  حتی اگر ردیفی (از روشنِ پیشین) مانده باشد، خاموش ⇒ بی اثر
  await query(`INSERT INTO order_desk (shop_id, paused_until, pause_source, busy_until, busy_extra_minutes, updated_at)
               VALUES ($1,$2,'user',$2,20,$3)`, [s.shopId, Date.now() + 60 * MIN, Date.now()]);
  const st = await pub(s);
  assert.equal(st.busy, undefined);
  assert.equal(st.pause, undefined);
  assert.equal(st.orderAcceptance, 'accepting');
  const c = await checkout(s);
  assert.equal(c.status, 201, JSON.stringify(c.body));
  assert.equal((await rowOfKey(s, c.key)).status, 'new');
  assert.equal(await desk.sweep(), 0);
  assert.equal((await events(s)).length, 0);
});

test('«شلوغم» (ORD-08): ساعتِ سرور، در وضعیتِ عمومی، پایانِ خودکار و ۰ = پایان؛ تکرار بی اثرِ دوم', async () => {
  const s = await liveStore('شلوغ');
  const g = (await deskGet(s)).body;
  assert.equal(g.enabled, true);
  assert.deepEqual([g.busy, g.pause, g.autoPause.enabled, g.autoAccept.enabled], [null, null, false, false]);
  assert.deepEqual(g.permissions, { manage: true, settings: true });
  const t0 = Date.now();
  const id = ev();
  const r = await post(s, 'busy', { eventId: id, minutes: 30, extraMinutes: 20 });
  assert.equal(r.status, 200, JSON.stringify(r.body));
  const until = r.body.state.busy.until;
  assert.ok(until >= t0 + 30 * MIN && until <= Date.now() + 30 * MIN);
  assert.equal(r.body.state.busy.extraMinutes, 20);
  assert.deepEqual((await pub(s)).busy, { until, extraMinutes: 20 });
  //  تکرار ⇒ همان، بی رویدادِ دوم؛ همان شناسه با بدنهٔ دیگر ⇒ ۴۰۹
  const again = await post(s, 'busy', { eventId: id, minutes: 30, extraMinutes: 20 });
  assert.equal(again.body.replayed, true);
  assert.equal(again.body.state.busy.until, until);
  assert.equal((await post(s, 'busy', { eventId: id, minutes: 45, extraMinutes: 20 })).body.error.code, 'idempotency_key_reused');
  assert.equal((await events(s)).length, 1);
  assert.equal((await one(`SELECT COUNT(*)::int n FROM audit_logs WHERE shop_id=$1 AND action='order_desk.busy'`, [s.shopId])).n, 1);
  //  پایانِ خودکار: زمان گذشت ⇒ نه در میز، نه در ویترین
  await query('UPDATE order_desk SET busy_until=$2 WHERE shop_id=$1', [s.shopId, Date.now() - 1000]);
  assert.equal((await deskGet(s)).body.busy, null);
  assert.equal((await pub(s)).busy, undefined);
  //  دوباره، و ۰ = پایانِ همین حالا
  assert.equal((await post(s, 'busy', { eventId: ev(), minutes: 10 })).body.state.busy.extraMinutes, 20, 'پیش‌فرضِ ۲۰');
  const end = await post(s, 'busy', { eventId: ev(), minutes: 0 });
  assert.equal(end.status, 200);
  assert.equal(end.body.state.busy, null);
  assert.deepEqual((await events(s)).map((e) => e.kind), ['busy', 'busy', 'busy_end']);
  for (const body of [{ minutes: 241 }, { minutes: -1 }, { minutes: '30' }, { minutes: 30, extraMinutes: 4 }, { minutes: 30, extraMinutes: 121 }]) {
    const bad = await post(s, 'busy', { eventId: ev(), ...body });
    assert.equal(bad.status, 400, JSON.stringify(body));
  }
  assert.equal((await post(s, 'busy', { minutes: 30 })).body.error.code, 'bad_event_id');
});

test('توقفِ موقت (ORD-08): ویترین ۴۰۹ store_paused؛ تکرارِ سفارشِ پیشین و سفارشِ کارمند آزاد؛ ادامه و پایانِ خودکار', async () => {
  const s = await liveStore('توقف');
  const before = await checkout(s);
  assert.equal(before.status, 201, JSON.stringify(before.body));
  const p = await post(s, 'pause', { eventId: ev(), minutes: 30, reason: 'staff', note: 'یک نفر نیامده' });
  assert.equal(p.status, 200, JSON.stringify(p.body));
  assert.deepEqual([p.body.state.pause.reason, p.body.state.pause.note, p.body.state.pause.source], ['staff', 'یک نفر نیامده', 'user']);
  const until = p.body.state.pause.until;

  const refused = await checkout(s);
  assert.equal(refused.status, 409, JSON.stringify(refused.body));
  assert.equal(refused.body.error.code, 'store_paused');
  assert.equal(refused.body.error.pausedUntil, until);
  assert.equal(await rowOfKey(s, refused.key), null, 'هیچ سفارشی ساخته نشد');
  //  پاسخِ گم‌شدهٔ سفارشِ پیش از توقف ⇒ همان سفارش
  const replay = await checkout(s, { key: before.key });
  assert.equal(replay.status, 200, JSON.stringify(replay.body));
  assert.equal(replay.body.replayed, true);
  //  سفارشِ تلفنیِ کارمند تصمیمِ خودِ دکان است
  assert.equal((await staffOrder(s)).status, 201);
  //  مشتری فقط «تا کی» را می‌بیند، نه دلیل و توضیح
  const st = await pub(s);
  assert.deepEqual(st.pause, { until });
  assert.equal(st.orderAcceptance, 'paused');
  assert.ok(!JSON.stringify(st).includes('نیامده') && !JSON.stringify(st).includes('staff'));

  const res = await post(s, 'resume', { eventId: ev() });
  assert.equal(res.status, 200);
  assert.equal(res.body.state.pause, null);
  assert.equal((await checkout(s)).status, 201);
  assert.equal((await pub(s)).orderAcceptance, 'accepting');

  //  پایانِ خودکار با گذشتِ زمان
  assert.equal((await post(s, 'pause', { eventId: ev(), minutes: 5, reason: 'technical' })).status, 200);
  assert.equal((await checkout(s)).status, 409);
  await query('UPDATE order_desk SET paused_until=$2 WHERE shop_id=$1', [s.shopId, Date.now() - 1000]);
  assert.equal((await checkout(s)).status, 201);
  assert.equal((await deskGet(s)).body.pause, null);

  for (const [body, code] of [
    [{ minutes: 30 }, 'bad_reason'], [{ minutes: 30, reason: 'lazy' }, 'bad_reason'], [{ minutes: 30, reason: 'other' }, 'note_required'],
    [{ minutes: 4, reason: 'staff' }, 'bad_number'], [{ minutes: 1441, reason: 'staff' }, 'bad_number'],
  ]) {
    const bad = await post(s, 'pause', { eventId: ev(), ...body });
    assert.equal(bad.status, 400, JSON.stringify(body));
    assert.equal(bad.body.error.code, code);
  }
  assert.deepEqual((await events(s)).map((e) => [e.kind, e.reason]), [['pause', 'staff'], ['resume', ''], ['pause', 'technical']]);
});

test('تنظیم‌ها: فقط صاحب و مدیر؛ نسخهٔ کهنه ⇒ ۴۰۹ version_conflict؛ ورودیِ بد ۴۰۰', async () => {
  const s = await liveStore('تنظیم');
  const staff = await h.newUser('شاگردِ میز', 'shop');
  await query(`INSERT INTO shop_members (id, shop_id, user_id, role, status, created_at, updated_at) VALUES ($1,$2,$3,'staff','active',$4,$4)`,
    [newId('mem'), s.shopId, staff.user.id, now()]);
  assert.deepEqual((await h.get('/api/shop/orders/desk', { token: staff.accessToken })).body.permissions, { manage: true, settings: false });
  assert.equal((await put(s, { eventId: ev(), autoAccept: { enabled: true } }, staff.accessToken)).status, 403);
  //  شاگرد «شلوغم» و توقف را می‌زند (orders.manage)
  assert.equal((await post(s, 'busy', { eventId: ev(), minutes: 15 }, staff.accessToken)).status, 200);

  const v0 = (await deskGet(s)).body.version;
  const ok = await put(s, { eventId: ev(), expectedVersion: v0, autoPause: { enabled: true, maxOpen: 7 }, autoAccept: { readyMinutes: 25 } });
  assert.equal(ok.status, 200, JSON.stringify(ok.body));
  assert.deepEqual(ok.body.state.autoPause, { enabled: true, maxOpen: 7, oldestMinutes: 30, minutes: 30 });
  assert.deepEqual(ok.body.state.autoAccept, { enabled: false, maxOpen: 5, readyMinutes: 25 });
  const stale = await put(s, { eventId: ev(), expectedVersion: v0, autoPause: { enabled: false } });
  assert.equal(stale.status, 409);
  assert.equal(stale.body.error.code, 'version_conflict');
  assert.equal(stale.body.error.version, ok.body.state.version);
  assert.equal((await deskGet(s)).body.autoPause.enabled, true, 'نسخهٔ کهنه چیزی را عوض نکرد');
  for (const body of [{}, { autoPause: 5 }, { autoPause: { maxOpen: 0 } }, { autoAccept: { readyMinutes: 1441 } },
    { autoPause: { minutes: 4 } }, { autoAccept: { enabled: true }, expectedVersion: 'x' }]) {
    const bad = await put(s, { eventId: ev(), ...body });
    assert.equal(bad.status, 400, JSON.stringify(body));
  }
});

test('قبولِ خودکار (ORD-04): با سقفِ صف، زمانِ آماده (+ شلوغم)، بازیگرِ سیستم؛ نه زمان‌بندی‌شده، نه بی‌اتصال', async () => {
  const s = await liveStore('قبول');
  assert.equal((await put(s, { eventId: ev(), autoAccept: { enabled: true, maxOpen: 2, readyMinutes: 10 } })).status, 200);
  const t0 = Date.now();
  const a = await checkout(s, { phone: '0791113201' });
  const b = await checkout(s, { phone: '0791113202' });
  const c = await checkout(s, { phone: '0791113203' });
  for (const x of [a, b, c]) assert.equal(x.status, 201, JSON.stringify(x.body));
  const [ra, rb, rc] = [await rowOfKey(s, a.key), await rowOfKey(s, b.key), await rowOfKey(s, c.key)];
  assert.deepEqual([ra.status, rb.status, rc.status], ['accepted', 'accepted', 'new'], 'سقفِ صف: دو در آماده‌سازی');
  assert.ok(Number(ra.ready_at) >= t0 + 10 * MIN && Number(ra.ready_at) <= Date.now() + 10 * MIN);
  const evs = await many(`SELECT to_status, actor_type FROM online_order_events WHERE order_id=$1 AND kind='status'`, [ra.id]);
  assert.deepEqual(evs.map((e) => [e.to_status, e.actor_type]), [['accepted', 'system']]);
  assert.equal((await one(`SELECT COUNT(*)::int n FROM audit_logs WHERE shop_id=$1 AND action='order.auto_accepted'`, [s.shopId])).n, 2);

  //  یکی تحویل شد ⇒ جا باز شد؛ «شلوغم» ⇒ زمانِ آماده + ۲۰
  for (const to of ['picking', 'packed', 'ready_for_pickup']) {
    assert.equal((await h.post(`/api/shop/orders/${ra.id}/transition`, { to }, { token: s.token })).status, 200);
  }
  await post(s, 'busy', { eventId: ev(), minutes: 60, extraMinutes: 20 });
  const t1 = Date.now();
  const d = await checkout(s, { phone: '0791113204' });
  const rd = await rowOfKey(s, d.key);
  assert.equal(rd.status, 'accepted');
  assert.ok(Number(rd.ready_at) >= t1 + 30 * MIN && Number(rd.ready_at) <= Date.now() + 30 * MIN, 'زمانِ آماده + دقیقه‌های شلوغم');

  //  زمان‌بندی‌شده نه
  await query(`UPDATE online_orders SET status='picked_up' WHERE shop_id=$1 AND status IN ('accepted','picking','packed')`, [s.shopId]);
  const sch = await checkout(s, { phone: '0791113205' });
  await query('UPDATE online_orders SET status=$2, scheduled_for=$3 WHERE id=$1',
    [(await rowOfKey(s, sch.key)).id, 'new', Date.now() + 3 * 60 * MIN]);
  const schRow = await rowOfKey(s, sch.key);
  assert.equal((await desk.afterOrder(await one('SELECT * FROM storefronts WHERE shop_id=$1', [s.shopId]), schRow.id)).autoAccepted, false);
  assert.equal((await one('SELECT status FROM online_orders WHERE id=$1', [schRow.id])).status, 'new');

  //  بی اتصالِ تأییدشده (دستگاهِ دکان مدتی همگام نشده) ⇒ نه
  await query(`UPDATE sync_devices SET last_seen_at=$2 WHERE app='shop' AND account_kind='shop' AND account_id=$1`,
    [s.shopId, Date.now() - 20 * MIN]);
  const e = await checkout(s, { phone: '0791113206' });
  assert.equal(e.status, 201, JSON.stringify(e.body));
  assert.equal((await rowOfKey(s, e.key)).status, 'new');
});

test('قبولِ خودکار: سفارش‌های هم‌زمان از سقف نمی‌گذرند؛ سفارشی که کارمند زودتر جواب داد دست نمی‌خورد', async () => {
  const s = await liveStore('هم‌زمان');
  assert.equal((await put(s, { eventId: ev(), autoAccept: { enabled: true, maxOpen: 2, readyMinutes: 10 } })).status, 200);
  const rs = await Promise.all(['0791113301', '0791113302', '0791113303', '0791113304', '0791113305']
    .map((phone) => checkout(s, { phone })));
  assert.ok(rs.every((x) => x.status === 201), JSON.stringify(rs.map((x) => x.body)));
  const st = await many('SELECT status FROM online_orders WHERE shop_id=$1', [s.shopId]);
  assert.equal(st.filter((x) => x.status === 'accepted').length, 2, JSON.stringify(st));
  assert.equal(st.filter((x) => x.status === 'new').length, 3);

  //  کارمند زودتر رد کرد ⇒ afterOrder ِ دیر چیزی را عوض نمی‌کند
  await query(`UPDATE online_orders SET status='picked_up' WHERE shop_id=$1 AND status='accepted'`, [s.shopId]);
  const late = await checkout(s, { phone: '0791113306' });
  const row = await rowOfKey(s, late.key);
  assert.equal(row.status, 'accepted');
  const n = await checkout(s, { phone: '0791113307' });
  const nrow = await rowOfKey(s, n.key);
  //  شبیه‌سازیِ «کارمند زودتر رد کرد» پیش از afterOrder ِ دوم
  await query(`UPDATE online_orders SET status='new' WHERE id=$1`, [nrow.id]);
  assert.equal((await h.post(`/api/shop/orders/${nrow.id}/transition`, { to: 'rejected', reason: 'busy' }, { token: s.token })).status, 200);
  const again = await desk.afterOrder(await one('SELECT * FROM storefronts WHERE shop_id=$1', [s.shopId]), nrow.id);
  assert.equal(again.autoAccepted, false);
  assert.equal((await one('SELECT status FROM online_orders WHERE id=$1', [nrow.id])).status, 'rejected');
});

test('توقفِ خودکار (ORD-09): صفِ بیش از حد ⇒ یک بار توقف با سابقه؛ ویترین می‌بندد؛ «ادامه» مهلت دارد', async () => {
  const s = await liveStore('خودکار');
  assert.equal((await put(s, { eventId: ev(), autoPause: { enabled: true, maxOpen: 2, oldestMinutes: 600, minutes: 15 } })).status, 200);
  const autos = async () => (await events(s)).filter((e) => e.kind === 'auto_pause').length;
  await desk.sweep();
  assert.equal(await autos(), 0, 'صف خالی');
  for (let i = 0; i < 3; i++) assert.equal((await staffOrder(s)).status, 201);
  //  سفارشِ آزمایشی شمرده نمی‌شود
  await query(`UPDATE online_orders SET is_test=true WHERE id=(SELECT id FROM online_orders WHERE shop_id=$1 ORDER BY created_at LIMIT 1)`, [s.shopId]);
  await desk.sweep();
  assert.equal(await autos(), 0, 'دو سفارشِ واقعی = سقف، نه بیشتر');
  assert.equal((await staffOrder(s)).status, 201);
  const t0 = Date.now();
  await Promise.all([desk.sweep(t0), desk.sweep(t0), desk.evaluateAutoPause(s.shopId, t0)]);
  assert.equal(await autos(), 1, 'دو نمونهٔ سرور ⇒ یک توقف');
  await desk.sweep();
  assert.equal(await autos(), 1, 'متوقف ⇒ دوباره نه');
  const g = (await deskGet(s)).body;
  assert.deepEqual([g.pause.source, g.pause.reason], ['auto', 'overloaded']);
  assert.equal(g.pause.until, t0 + 15 * MIN);
  assert.equal(g.queue.open, 3);
  const evs = await events(s);
  assert.deepEqual(evs.filter((e) => e.kind === 'auto_pause').map((e) => [e.actor_type, e.reason]), [['system', 'overloaded']]);
  assert.equal((await one(`SELECT COUNT(*)::int n FROM audit_logs WHERE shop_id=$1 AND action='order.auto_paused'`, [s.shopId])).n, 1);
  assert.equal((await checkout(s)).body.error.code, 'store_paused');

  //  «ادامه» ِ دستی ⇒ تا یک دورِ توقف (۱۵ دقیقه) دوباره متوقف نمی‌شود، پس از آن می‌شود
  assert.equal((await post(s, 'resume', { eventId: ev() })).status, 200);
  await desk.sweep(Date.now() + 2 * MIN);   // دقیقه‌ای دیگر، هنوز در مهلت
  assert.equal(await autos(), 1);
  assert.equal((await checkout(s)).status, 201);
  await desk.sweep(Date.now() + 16 * MIN);
  assert.equal(await autos(), 2, 'پس از مهلت، صف هنوز پر ⇒ دوباره');
});

test('توقفِ خودکار: سفارشِ تازهٔ بی‌پاسخ قدیمی‌تر از حد ⇒ توقف (پس از هر سفارشِ ویترین هم سنجیده می‌شود)', async () => {
  const s = await liveStore('بی‌پاسخ');
  assert.equal((await put(s, { eventId: ev(), autoPause: { enabled: true, maxOpen: 100, oldestMinutes: 30, minutes: 20 } })).status, 200);
  const o = await checkout(s, { phone: '0791113401' });
  assert.equal(o.status, 201);
  assert.equal((await deskGet(s)).body.pause, null);
  //  ۳۱ دقیقه بی پاسخ (مثلاً سفارشِ «بعداً» که زمان‌سنجِ ۱۵ دقیقه‌ای ندارد)
  await query('UPDATE online_orders SET created_at=$2 WHERE shop_id=$1', [s.shopId, Date.now() - 31 * MIN]);
  const next = await checkout(s, { phone: '0791113402' });
  assert.equal(next.status, 201, 'همین سفارش ثبت می‌شود؛ توقف پس از آن');
  const g = (await deskGet(s)).body;
  assert.equal(g.pause.source, 'auto');
  assert.equal((await checkout(s, { phone: '0791113403' })).status, 409);
  //  پرچم خاموش ⇒ کارگر به این دکان دست نمی‌زند
  await post(s, 'resume', { eventId: ev() });
  await setFlag(s.shopId, 'shop.orders.desk', false);
  const autoBefore = (await events(s)).filter((e) => e.kind === 'auto_pause').length;
  await desk.sweep(Date.now() + 60 * MIN);
  assert.equal((await events(s)).filter((e) => e.kind === 'auto_pause').length, autoBefore);
  assert.equal(Number((await one('SELECT paused_until FROM order_desk WHERE shop_id=$1', [s.shopId])).paused_until || 0), 0);
});

test('جداییِ دکان‌ها، بی ورود ۴۰۱، اشتراکِ تمام‌شده ⇒ ۴۰۳ subscription_* (SUB-10)', async () => {
  const a = await liveStore('الفِ میز');
  const b = await liveStore('بِ میز');
  assert.equal((await post(b, 'pause', { eventId: ev(), minutes: 30, reason: 'staff', shopId: a.shopId })).status, 200);
  assert.equal((await deskGet(a)).body.pause, null, 'دکانِ دیگر فقط خودش را می‌بندد');
  assert.equal((await checkout(a)).status, 201);
  assert.equal((await h.get('/api/shop/orders/desk', {})).status, 401);
  await query('UPDATE shops SET created_at=$2 WHERE id=$1', [a.shopId, Date.now() - 400 * DAY]);
  for (const r of [await post(a, 'busy', { eventId: ev(), minutes: 10 }), await put(a, { eventId: ev(), autoAccept: { enabled: true } })]) {
    assert.equal(r.status, 403, JSON.stringify(r.body));
    assert.ok(['subscription_expired', 'subscription_required'].includes(r.body.error.code));
  }
  assert.equal((await deskGet(a)).status, 200, 'خواندن گروگان نیست');
});

test('هم‌زمانی: یک شناسه سه بار ⇒ یک رویداد؛ دو تنظیم با یک نسخه ⇒ یکی ۴۰۹؛ قبولِ خودکارِ هم‌زمان از سقف نمی‌گذرد', async () => {
  const s = await liveStore('هم‌زمانیِ میز');
  const id = ev();
  const rs = await Promise.all([1, 2, 3].map(() => post(s, 'busy', { eventId: id, minutes: 20 })));
  assert.ok(rs.every((r) => r.status === 200), JSON.stringify(rs.map((r) => r.body)));
  assert.equal(rs.filter((r) => r.body.replayed === false).length, 1);
  assert.equal((await events(s)).length, 1);

  const v = (await deskGet(s)).body.version;
  const ps = await Promise.all([true, false].map((on) => put(s, { eventId: ev(), expectedVersion: v, autoPause: { enabled: on } })));
  assert.deepEqual(ps.map((r) => r.status).sort(), [200, 409], JSON.stringify(ps.map((r) => r.body)));

  //  پنج سفارشِ تازه که پیش از روشن شدنِ قبولِ خودکار آمدند؛ پنج afterOrder ِ هم‌زمان ⇒ فقط سقف
  const keys = [];
  for (let i = 0; i < 5; i++) {
    const c = await checkout(s, { phone: `07911135${String(i).padStart(2, '0')}` });
    assert.equal(c.status, 201, JSON.stringify(c.body));
    keys.push(c.key);
  }
  assert.equal((await put(s, { eventId: ev(), autoAccept: { enabled: true, maxOpen: 2, readyMinutes: 10 } })).status, 200);
  await query('UPDATE order_desk SET busy_until=NULL WHERE shop_id=$1', [s.shopId]);
  const sfRow = await one('SELECT * FROM storefronts WHERE shop_id=$1', [s.shopId]);
  const ids = await Promise.all(keys.map(async (k) => (await rowOfKey(s, k)).id));
  const outs = await Promise.all(ids.map((oid) => desk.afterOrder(sfRow, oid)));
  assert.equal(outs.filter((o) => o.autoAccepted).length, 2);
  assert.equal((await one(`SELECT COUNT(*)::int n FROM online_orders WHERE shop_id=$1 AND status='accepted'`, [s.shopId])).n, 2);
});
