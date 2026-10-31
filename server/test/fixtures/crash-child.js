'use strict';
/**
 * فرآیندِ جدای سرور برای `crash-kill.test.js` (DATA-03) — دو کار:
 *
 *   node test/fixtures/crash-child.js setup
 *     دیتابیس را از نو می‌سازد، دادهٔ سه صحنه را از راهِ HTTP ِ واقعی می‌چیند، یک خطِ
 *     «SETUP <json>» چاپ می‌کند، دیتابیس را درست می‌بندد و بیرون می‌رود.
 *
 *   CRASH_AT='<regex>' node test/fixtures/crash-child.js serve
 *     همان سرورِ واقعی را روی همان دیتابیس (بی پاک کردن) بالا می‌آورد و «LISTEN <port>» چاپ
 *     می‌کند. نخستین دستورِ SQL که با CRASH_AT جور شود — درونِ تراکنش (`db.tx`) یا بیرونِ آن
 *     (`query`/`one`/`many`) — اجرا **نمی‌شود**: «CRASH_POINT» چاپ می‌شود و فرآیند همان‌جا، با
 *     تراکنشِ باز، می‌ایستد تا آزمون آن را با SIGKILL بکشد — همان لحظه‌ای که برق می‌رود یا سرور
 *     از کار می‌افتد. هیچ کدی از سرور عوض نمی‌شود؛ فقط لحظهٔ مرگ انتخاب می‌شود. (نوشتنی که به
 *     اشتباه بیرون از تراکنش برود، پیش از مرگ COMMIT شده و آزمون آن را می‌بیند.)
 *
 * دیتابیس از TEST_DATABASE_URL می‌آید (helpers): PostgreSQL، یا پوشهٔ PGlite روی دیسک.
 */
const mode = process.argv[2];

if (mode === 'serve') {
  //  پیش از بارِ مسیرها: `delivery.js` و `sync-v3.js` در بارگذاری `tx`/`one`/… را برمی‌دارند
  const db = require('../../src/db');
  const at = new RegExp(process.env.CRASH_AT || '^$');
  const halt = () => {
    process.stdout.write('CRASH_POINT\n');
    setInterval(() => {}, 60_000);   //  زنده بماند تا کشته شود
    return new Promise(() => {});
  };
  const real = { tx: db.tx, query: db.query, one: db.one, many: db.many };
  for (const k of ['query', 'one', 'many']) {
    db[k] = (text, params) => (at.test(String(text)) ? halt() : real[k](text, params));
  }
  db.tx = (fn) => real.tx((c) => fn({
    query: (text, params) => (at.test(String(text)) ? halt() : c.query(text, params)),
    release() {},
  }));
}

const h = require('../helpers');
const { query, newId, now, closeDb } = require('../../src/db');

const say = (line) => process.stdout.write(`${line}\n`);

async function serve() {
  const { createApp } = require('../../src/app');
  const app = await createApp({ runMigrations: false });
  const server = app.listen(0, '127.0.0.1', () => say(`LISTEN ${server.address().port}`));
}

function ok(r, status, what) {
  if (r.status !== status) throw new Error(`${what}: ${r.status} ${JSON.stringify(r.body)}`);
  return r.body;
}

async function setup() {
  await h.start();
  const pw = require('../../src/lib/password');
  await query(
    `INSERT INTO admins (id, username, name, password_hash, role, status, created_at)
     VALUES ($1,'admin','مدیر',$2,'superadmin','active',$3)`,
    [newId('adm'), await pw.hashPassword('Admin!12345'), now()]
  );
  const admin = ok(await h.post('/api/admin/login', { username: 'admin', password: 'Admin!12345' }), 200, 'ورودِ مدیر').token;
  const flag = async (scope, key) =>
    ok(await h.put('/api/admin/pump/live-config', { scope, key, value: true }, { token: admin }), 200, key);

  //  ── دکان با سفارش و پیک (shop.orders + shop.delivery) ──
  const u = await h.newUser('صاحبِ دکانِ خاموشی', 'shop');
  const shopId = ok(await h.post('/api/shop', { name: 'دکانِ خاموشی' }, { token: u.accessToken }), 201, 'دکان').shop.id;
  const owner = u.accessToken;
  await flag(shopId, 'shop.orders');
  await flag(shopId, 'shop.delivery');
  const m = await h.newUser('پیکِ خاموشی', 'shop');
  await query(
    `INSERT INTO shop_members (id, shop_id, user_id, role, status, created_at, updated_at)
     VALUES ($1,$2,$3,'staff','active',$4,$4)`,
    [newId('mem'), shopId, m.user.id, now()]
  );
  const courierId = ok(await h.post('/api/shop/delivery/couriers', { userId: m.user.id }, { token: owner }), 201, 'پیک').courier.id;
  const courier = m.accessToken;

  let n = 0;
  const order = async () => {
    const body = {
      source: 'phone', fulfillment: 'delivery', paymentMethod: 'cod',
      customer: { name: 'احمد', phone: '0791234567' },
      address: { landmark: 'نزدیکِ مسجدِ جامع', text: 'کارتهٔ چهار' },
      lines: [{ name: 'برنج', qtyMilli: 1000, unitPriceMinor: 1_200_000 }],
      deliveryFeeMinor: 40_000,
    };
    const o = ok(await h.post('/api/shop/orders', body, { token: owner, headers: { 'Idempotency-Key': `crash-order-${++n}` } }), 201, 'سفارش').order;
    for (const to of ['accepted', 'picking', 'packed']) {
      ok(await h.post(`/api/shop/orders/${o.id}/transition`, { to }, { token: owner }), 200, to);
    }
    const a = ok(await h.post(`/api/shop/delivery/orders/${o.id}/assign`,
      { mode: 'shop_courier', courierId, overrideCashCap: true }, { token: owner }), 201, 'سپردن').assignment;
    for (const type of ['assigned', 'picked_up', 'moving', 'arrived']) {
      const r = ok(await h.post('/api/courier/events', {
        deviceId: 'crash-courier', events: [{ eventId: `crash-${a.id}-${type}`, assignmentId: a.id, type, deviceTime: Date.now() }],
      }, { token: courier }), 200, type);
      if (r.results[0].status !== 'applied') throw new Error(`${type}: ${JSON.stringify(r.results[0])}`);
    }
    return { orderId: o.id, assignmentId: a.id, totalMinor: Number(o.totalMinor) };
  };
  //  صحنهٔ ۱: «رسیدم» ⇒ «تحویل» در فرآیندِ کشته‌شده
  const deliver = await order();
  //  صحنهٔ ۲: مأموریتِ تحویل‌شده ⇒ تسویه در فرآیندِ کشته‌شده
  const settle = await order();
  const done = ok(await h.post('/api/courier/events', {
    deviceId: 'crash-courier',
    events: [{ eventId: `crash-${settle.assignmentId}-delivered`, assignmentId: settle.assignmentId, type: 'delivered',
      cashCollectedMinor: settle.totalMinor, deviceTime: Date.now() }],
  }, { token: courier }), 200, 'تحویلِ صحنهٔ ۲');
  if (done.results[0].status !== 'applied') throw new Error(`تحویلِ صحنهٔ ۲: ${JSON.stringify(done.results[0])}`);

  //  ── صحنهٔ ۳: Sync v3 (SYNC_V3_ENABLED=1) ──
  const v = await h.newUser('صاحبِ دکانِ v3 ِ خاموشی', 'shop');
  const v3ShopId = ok(await h.post('/api/shop', { name: 'دکانِ v3 ِ خاموشی' }, { token: v.accessToken }), 201, 'دکانِ v3').shop.id;
  const v3Owner = (await h.signIn(v, 'shop')).accessToken;

  say(`SETUP ${JSON.stringify({ shopId, owner, courier, courierId, deliver, settle, v3ShopId, v3Owner })}`);
  await h.stop();
  await closeDb();
}

(mode === 'serve' ? serve() : mode === 'setup' ? setup().then(() => process.exit(0)) : Promise.reject(new Error(`حالتِ ناشناخته: ${mode}`)))
  .catch((err) => { console.error(err && err.stack || err); process.exit(1); });
