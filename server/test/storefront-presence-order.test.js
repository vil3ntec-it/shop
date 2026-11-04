'use strict';
/**
 * PRES-08 — ترتیبِ اعلام‌های حضور با زمانِ سرور، نه ساعتِ خامِ دستگاه.
 *
 *   ۱) ساعتِ جلوافتادهٔ یک دستگاه (فردا) وضعیت را قفل نمی‌کند: اعلامِ بعدیِ دستگاهِ درست
 *      «تازه‌تر» است، چون اعلامی پیش از رسیدنش به سرور نمی‌تواند بعد از آن رخ داده باشد.
 *      (پیش‌تر `clientEventAt` ِ فردا تا فردا هر اعلامِ دیگر را `applied: false` می‌کرد.)
 *   ۲) صفِ آفلاین همچنان با زمانِ دستگاه: اعلامِ کهنه‌ای که دیر می‌رسد وضعیتِ تازه را
 *      بی‌صدا برنمی‌گرداند (`applied: false`) و در تاریخچه می‌ماند.
 *   ۳) چند اعلامِ هم‌زمان از چند دستگاه با هر ترتیبِ رسیدن ⇒ وضعیتِ پایانی همانِ تازه‌ترین.
 *   ۴) دکانِ دیگر از اعلام‌های این دکان اثر نمی‌گیرد.
 */
const test = require('node:test');
const assert = require('node:assert/strict');
const h = require('./helpers');
const { query, one, newId, now } = require('../src/db');
const { resetAll } = require('../src/middleware/ratelimit');

let admin = '';
let seq = 0;
const uniq = () => `${Date.now().toString(36)}${++seq}`;
const DAY = 24 * 3600_000;

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

/** دکانِ تازه با ویترینِ روشن */
async function store(label) {
  const u = await h.newUser(label, 'shop');
  const made = await h.post('/api/shop', { name: `دکانِ ${label}` }, { token: u.accessToken });
  assert.equal(made.status, 201, JSON.stringify(made.body));
  const o = { token: u.accessToken, shopId: made.body.shop.id };
  await setFlag(o.shopId, 'shop.orders', true);
  await setFlag(o.shopId, 'shop.storefront', true);
  const put = await h.put('/api/shop/storefront', { slug: `po-${uniq()}`, enabled: true, title: `ویترینِ ${label}`, contactPhone: '0700000001' },
    { token: o.token });
  assert.equal(put.status, 200, JSON.stringify(put.body));
  return o;
}

const declare = (s, body) => h.post('/api/shop/storefront/presence', { eventId: `ev-${uniq()}-x`, ...body }, { token: s.token });
const modeOf = async (shopId) => (await one('SELECT presence_mode FROM storefronts WHERE shop_id = $1', [shopId])).presence_mode;

test('PRES-08: ساعتِ فردای یک دستگاه وضعیت را قفل نمی‌کند؛ اعلامِ بعدیِ دستگاهِ درست می‌نشیند', async () => {
  const s = await store('ساعت‌جلو');
  const skewed = await declare(s, { mode: 'closed', clientEventAt: Date.now() + DAY });
  assert.equal(skewed.status, 200, JSON.stringify(skewed.body));
  assert.equal(skewed.body.applied, true);
  assert.equal(await modeOf(s.shopId), 'closed');

  const later = await declare(s, { mode: 'online', clientEventAt: Date.now() });
  assert.equal(later.status, 200, JSON.stringify(later.body));
  assert.equal(later.body.applied, true, 'اعلامِ پس از رسیدنِ اعلامِ «فردا» تازه‌تر است');
  assert.equal(later.body.status.mode, 'online');
  assert.equal(await modeOf(s.shopId), 'online');
  //  زمانِ خامِ دستگاه برای ممیزی دست‌نخورده می‌ماند
  const raw = await one(
    `SELECT client_event_at FROM storefront_presence_events WHERE shop_id = $1 AND status = 'closed'`, [s.shopId]);
  assert.ok(Number(raw.client_event_at) > Date.now() + DAY / 2);
});

test('PRES-08: اعلامِ کهنهٔ صفِ آفلاین که دیر می‌رسد وضعیتِ تازه را برنمی‌گرداند', async () => {
  const s = await store('صف‌آفلاین');
  const t0 = Date.now();
  const fresh = await declare(s, { mode: 'online', clientEventAt: t0 });
  assert.equal(fresh.body.applied, true);
  const late = await declare(s, { mode: 'closed', clientEventAt: t0 - 10 * 60_000 });
  assert.equal(late.status, 200, JSON.stringify(late.body));
  assert.equal(late.body.applied, false);
  assert.equal(late.body.status.mode, 'online');
  assert.equal(await modeOf(s.shopId), 'online');
  const n = await one(`SELECT COUNT(*)::int AS n FROM storefront_presence_events WHERE shop_id = $1 AND kind = 'presence'`, [s.shopId]);
  assert.equal(n.n, 2, 'اعلامِ کهنه در تاریخچه می‌نشیند');
});

test('PRES-08: اعلام‌های هم‌زمانِ چند دستگاه با هر ترتیبِ رسیدن ⇒ تازه‌ترین می‌ماند؛ دکانِ دیگر دست نمی‌خورد', async () => {
  const s = await store('هم‌زمان');
  const other = await store('همسایه');
  const base = Date.now() - 60_000;
  const modes = ['offline', 'closed', 'auto', 'online', 'closed', 'offline'];
  //  تازه‌ترین (online با بزرگ‌ترین زمانِ دستگاه) وسطِ فهرست، و رسیدنِ درهم
  const plan = modes.map((mode, i) => ({ mode, clientEventAt: base + (i === 3 ? 50_000 : i * 1000), deviceId: `dev-${i}` }));
  const order = [5, 3, 0, 4, 1, 2];
  const res = await Promise.all(order.map((i) => declare(s, plan[i])));
  for (const r of res) assert.equal(r.status, 200, JSON.stringify(r.body));
  assert.equal(await modeOf(s.shopId), 'online');
  const pub = await one('SELECT presence_mode FROM storefronts WHERE shop_id = $1', [other.shopId]);
  assert.equal(pub.presence_mode, 'auto', 'دکانِ همسایه همان پیش‌فرض');
});
