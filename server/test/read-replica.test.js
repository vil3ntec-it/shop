'use strict';
/**
 * نسخهٔ فقط-خواندنی (`DATABASE_REPLICA_URL`، `lib/read-replica.js`؛ docs/SCALING-fa.md).
 *
 * «نسخه» در این آزمون همان پایگاهِ آزمون است با `search_path=rr_snap,public`: جدولِ `rr_snap.sync_rows` برشی
 * **کهنه** از کالاها است ⇒ هر پاسخی که نامِ کهنه را نشان دهد از نسخه آمده و نامِ تازه از پایگاهِ اصلی. پس مسیرِ هر
 * خواندن از خودِ پاسخ پیدا است، نه از شمارنده‌ای که کدِ زیرِ آزمون خودش می‌شمارد.
 *
 * آن‌چه قفل می‌شود:
 *   ۱) کاتالوگِ عمومیِ ویترین (GET، بی‌نام) از نسخه می‌خواند؛ خواندنِ داوطلب بیرون از مسیرِ داوطلب از اصلی.
 *   ۲) ⛔ همان مشتری/کاربر پس از نوشتن (همان IP، همان توکن یا همان نشستِ ویترین) ⇒ اصلی؛ IP ِ دیگر ⇒ نسخه.
 *   ۳) ⛔ درونِ تراکنش ⇒ اصلی؛ همان درخواست پس از نوشتن ⇒ اصلی (و `SELECT … FOR UPDATE`، advisory هم نوشتن‌اند).
 *   ۴) نسخهٔ عقب (تأخیرِ سنجیده > `REPLICA_MAX_LAG_MS`) یا افتاده ⇒ اصلی، بی هیچ خطا؛ برگشتِ نسخه ⇒ دوباره نسخه.
 *   ۵) خطای اتصال در میانهٔ خواندن ⇒ همان پرسش روی اصلی و نسخه تا سنجشِ بعدی کنار.
 *   ۶) PGlite: پیکربندی رد می‌شود (`validate`) و هیچ چیزی عوض نمی‌شود.
 */
const PGLITE = /^pglite:/i.test(process.env.TEST_DATABASE_URL || '');
const PRIMARY_URL = process.env.TEST_DATABASE_URL || 'postgres://shop:shoppass@127.0.0.1:5432/shop_test';
const REPLICA_URL = (() => {
  if (PGLITE) return 'postgres://shop:shoppass@127.0.0.1:1/never';
  const u = new URL(PRIMARY_URL);
  u.searchParams.set('options', '-c search_path=rr_snap,public');
  return u.toString();
})();
process.env.DATABASE_REPLICA_URL = REPLICA_URL;
process.env.REPLICA_CHECK_MS = '200';
process.env.REPLICA_MAX_LAG_MS = '1000';
process.env.REPLICA_STICKY_MS = '60000';   //  پیش‌فرض ۵ ثانیه؛ این‌جا بلند تا آزمونِ کند هم درست بسنجد
process.env.TRUST_PROXY = 'true';

const test = require('node:test');
const assert = require('node:assert/strict');
const h = require('./helpers');
const db = require('../src/db');
const config = require('../src/config');
const rr = require('../src/lib/read-replica');
const { resetAll } = require('../src/middleware/ratelimit');

let admin = '';
let seq = 0;
const uniq = () => `${Date.now().toString(36)}${++seq}`;

test.before(async () => {
  await h.start();
  const pw = require('../src/lib/password');
  await db.query(
    `INSERT INTO admins (id, username, name, password_hash, role, status, created_at)
     VALUES ($1,'admin','مدیر',$2,'superadmin','active',$3)`,
    [db.newId('adm'), await pw.hashPassword('Admin!12345'), db.now()]
  );
  admin = (await h.post('/api/admin/login', { username: 'admin', password: 'Admin!12345' })).body.token;
  assert.ok(admin);
});
test.after(async () => {
  await rr.stop();
  if (!PGLITE) await db.query('DROP SCHEMA IF EXISTS rr_snap CASCADE').catch(() => {});
  await h.stop();
});
test.beforeEach(() => resetAll());

const ip = () => `203.0.113.${(++seq % 250) + 1}`;
async function setFlag(scope, key, value) {
  const r = await h.put('/api/admin/pump/live-config', { scope, key, value }, { token: admin });
  assert.equal(r.status, 200, JSON.stringify(r.body));
}
const op = (rowId, fields) => ({ op_id: `op-${uniq()}`, ts: Date.now(), table: 'products', row_id: rowId, type: 'insert', fields });
const push = (token, ops, from) => h.api('POST', '/api/sync/v1/push', {
  token, headers: { 'X-App': 'shop', 'X-Device': 'dev-rr', 'X-Forwarded-For': from },
  body: { device_id: 'dev-rr', schema_version: 2, ops },
});

/** دکانِ روشن با ویترین و دو کالای منتشرشده */
async function liveStore(label) {
  const u = await h.newUser(label, 'shop');
  const made = await h.post('/api/shop', { name: `دکانِ ${label}` }, { token: u.accessToken });
  assert.equal(made.status, 201, JSON.stringify(made.body));
  const o = { token: u.accessToken, shopId: made.body.shop.id, ip: ip() };
  await setFlag(o.shopId, 'shop.orders', true);
  await setFlag(o.shopId, 'shop.storefront', true);
  const slug = `rr-${uniq()}`;
  const put = await h.put('/api/shop/storefront', { slug, enabled: true, title: `ویترینِ ${label}`, contactPhone: '0700000003' }, { token: o.token });
  assert.equal(put.status, 200, JSON.stringify(put.body));
  const p = await push(o.token, [op('apple', { name: 'سیب', unit: 'کیلو', salePrice: 50, category: 'میوه' }),
    op('orange', { name: 'پرتقال', unit: 'کیلو', salePrice: 70, category: 'میوه' })], o.ip);
  assert.equal(p.status, 200, JSON.stringify(p.body));
  for (const id of ['apple', 'orange']) {
    const r = await h.put(`/api/shop/storefront/products/${id}`, { published: true }, { token: o.token });
    assert.equal(r.status, 200, JSON.stringify(r.body));
  }
  return { ...o, slug };
}

const names = async (s, { from, token, headers = {} } = {}) => {
  const r = await h.get(`/api/store/${s.slug}/products`, { token, headers: { 'X-Forwarded-For': from || ip(), ...headers } });
  assert.equal(r.status, 200, JSON.stringify(r.body));
  return r.body.items.map((p) => p.name).sort();
};
const OLD = ['سیب', 'پرتقال'].sort();
const NEW = ['سیبِ تازه', 'پرتقال'].sort();

/** برشِ کهنهٔ «نسخه» از همین حالا؛ سپس نامِ سیب روی اصلی عوض می‌شود (نوشتنِ صاحبِ دکان از IP ِ خودش). */
async function staleReplica(s) {
  await db.query('DROP SCHEMA IF EXISTS rr_snap CASCADE');
  await db.query('CREATE SCHEMA rr_snap');
  await db.query('CREATE TABLE rr_snap.sync_rows AS SELECT * FROM public.sync_rows');
  const p = await push(s.token, [op('apple', { name: 'سیبِ تازه', unit: 'کیلو', salePrice: 50, category: 'میوه' })], s.ip);
  assert.equal(p.status, 200, JSON.stringify(p.body));
  await rr.probe();
  assert.equal(rr.usable(), true, JSON.stringify(rr.stats()));
}

test('PGlite: نسخهٔ خواندنی پیکربندی نمی‌شود و هیچ چیزی عوض نمی‌شود', { skip: !PGLITE && 'فقط PGlite' }, async () => {
  assert.equal(rr.configured(), false);
  assert.ok(config.validate().some((p) => /DATABASE_REPLICA_URL/.test(p)), 'validate رد می‌کند');
  const s = await liveStore('rr-lite');
  assert.deepEqual(await names(s), OLD);
  assert.deepEqual(rr.stats(), { configured: false });
});

test('کاتالوگِ عمومی از نسخه؛ ⛔ همان مشتری پس از نوشتن (IP، توکن، نشستِ ویترین) از اصلی', { skip: PGLITE && 'فقط PostgreSQL' }, async () => {
  const s = await liveStore('rr-route');
  await staleReplica(s);
  const before = rr.stats();
  //  مشتریِ دیگر: نسخهٔ کهنه
  assert.deepEqual(await names(s), OLD, 'GET ِ بی‌نام از IP ِ دیگر از نسخه می‌خواند');
  assert.ok(rr.stats().reads > before.reads);
  //  همان IP ِ نویسنده (بی توکن): اصلی
  assert.deepEqual(await names(s, { from: s.ip }), NEW, 'نویسنده نوشتهٔ خودش را می‌بیند');
  //  همان توکن از IP ِ دیگر: اصلی
  assert.deepEqual(await names(s, { token: s.token }), NEW, 'همان کاربر از جای دیگر هم');
  assert.ok(rr.stats().primary.sticky >= 2, JSON.stringify(rr.stats()));
  //  مشتریِ ویترین: نوشتن با `X-Store-Session` یا `X-Store-Ticket` (هر POST) ⇒ GET ِ بعدیِ همان نشان از اصلی
  const nat = ip();
  const logout = (headers) => h.api('POST', `/api/store/${s.slug}/account/logout`, { headers: { 'X-Forwarded-For': nat, ...headers } });
  const session = `sess-${uniq()}`;
  await logout({ 'X-Store-Session': session });
  assert.deepEqual(await names(s, { headers: { 'X-Store-Session': session } }), NEW);
  assert.deepEqual(await names(s, { headers: { 'X-Store-Session': `sess-${uniq()}` } }), OLD, 'نشستِ دیگر همان نسخه');
  const ticket = `tk-${uniq()}`;
  await logout({ 'X-Store-Ticket': ticket });
  assert.deepEqual(await names(s, { headers: { 'X-Store-Ticket': ticket } }), NEW, 'بلیتِ همان مشتری (ویترین آن را با هر GET می‌فرستد)');
  //  CGNAT: مشتریِ دیگرِ پشتِ همان IP (بی آن نشان) قفل نمی‌شود
  assert.deepEqual(await names(s, { from: nat }), OLD, 'همان IP، مشتریِ دیگر ⇒ نسخه');
  //  نوشتنِ بی نشان (ورود، ثبت‌نام) ⇒ همان IP قفل
  const anon = ip();
  await h.api('POST', '/api/auth/login', { body: { identifier: 'nobody@test.local', password: 'x' }, headers: { 'X-Forwarded-For': anon } });
  assert.deepEqual(await names(s, { from: anon }), NEW, 'نوشتنِ بی نشان ⇒ IP');
  //  خواندنِ داوطلب بیرون از مسیرِ داوطلب (کارگر، مسیرِ دیگر): اصلی
  const row = await rr.one("SELECT data->>'name' AS n FROM sync_rows WHERE account_id = $1 AND table_name = 'products' AND row_id = 'apple'", [s.shopId]);
  assert.equal(row.n, 'سیبِ تازه');
  assert.equal(rr.reason(), 'unscoped');
});

test('⛔ درونِ تراکنش و پس از نوشتنِ همان درخواست ⇒ اصلی', { skip: PGLITE && 'فقط PostgreSQL' }, async () => {
  const s = await liveStore('rr-tx');
  await staleReplica(s);
  const SQL = "SELECT data->>'name' AS n FROM sync_rows WHERE account_id = $1 AND table_name = 'products' AND row_id = 'apple'";
  const scoped = (fn) => db.readScope.run({ wrote: false, sticky: false }, fn);
  assert.equal((await scoped(() => rr.one(SQL, [s.shopId]))).n, 'سیب', 'دامنهٔ باز ⇒ نسخه');
  assert.equal(await scoped(() => db.tx(async () => { assert.equal(rr.reason(), 'tx'); return (await rr.one(SQL, [s.shopId])).n; })), 'سیبِ تازه');
  for (const w of ['UPDATE sync_rows SET updated_at = updated_at WHERE false', 'SELECT 1 FROM shops FOR UPDATE', 'SELECT pg_advisory_xact_lock(1)']) {
    const n = await scoped(async () => { await db.query(w); assert.equal(rr.reason(), 'wrote', w); return (await rr.one(SQL, [s.shopId])).n; });
    assert.equal(n, 'سیبِ تازه', w);
  }
  //  SELECT ِ ساده نوشتن نیست
  assert.equal(await scoped(async () => { await db.query('SELECT 1'); return (await rr.one(SQL, [s.shopId])).n; }), 'سیب');
});

test('نسخهٔ عقب یا افتاده ⇒ اصلی بی خطا؛ برگشتش ⇒ دوباره نسخه', { skip: PGLITE && 'فقط PostgreSQL' }, async () => {
  const s = await liveStore('rr-lag');
  await staleReplica(s);
  assert.deepEqual(await names(s), OLD);
  //  سنجش می‌گوید standby ۵ ثانیه عقب است (بیش از REPLICA_MAX_LAG_MS)
  rr.applyProbe({ standby: true, lag_ms: 5000 });
  assert.equal(rr.usable(), false);
  assert.deepEqual(await names(s), NEW, 'عقب ⇒ اصلی');
  assert.ok(rr.stats().primary.lag >= 1);
  //  سنجشِ کهنه (تایمر ایستاده) هم «نامعلوم» است ⇒ اصلی
  rr.applyProbe({ standby: false, lag_ms: 0 }, Date.now() - 60_000);
  assert.equal(rr.usable(), false);
  await rr.probe();
  assert.deepEqual(await names(s), OLD, 'سنجشِ تازه ⇒ دوباره نسخه');

  //  نسخه‌ای که اتصال نمی‌دهد: سنجش ⇒ down، خواندن‌ها از اصلی
  const good = config.db.replicaUrl;
  try {
    await rr._reset();
    config.db.replicaUrl = 'postgres://shop:shoppass@127.0.0.1:1/none';
    await rr.probe();
    assert.equal(rr.stats().up, false);
    assert.match(rr.stats().lastError, /ECONNREFUSED|Error/);
    assert.deepEqual(await names(s), NEW, 'افتاده ⇒ اصلی');
    //  وضعیتِ «زنده» ولی اتصال در میانهٔ خواندن می‌شکند ⇒ همان پرسش روی اصلی، نسخه کنار
    rr.applyProbe({ standby: false, lag_ms: 0 });
    assert.deepEqual(await names(s), NEW);
    const st = rr.stats();
    assert.ok(st.fallbacks >= 1, JSON.stringify(st));
    assert.equal(st.up, false, 'خطای اتصال ⇒ نسخه تا سنجشِ موفق کنار');
  } finally {
    await rr._reset();
    config.db.replicaUrl = good;
    rr.start();
  }
  await rr.probe();
  assert.deepEqual(await names(s), OLD, 'نسخهٔ برگشته');
});

test('کلیدهای «همین کاربر» فقط HMAC ِ کوتاه‌اند؛ مهلت می‌گذرد', { skip: PGLITE && 'فقط PostgreSQL' }, () => {
  const req = { get: (k) => ({ authorization: 'Bearer secret-token-xyz', 'x-store-session': 'sess-secret', 'x-store-ticket': 'tk-secret' })[k], headers: {}, socket: { remoteAddress: '198.51.100.9' }, ip: '198.51.100.9' };
  const keys = rr.keysOf(req);
  assert.equal(keys.length, 4);
  //  نوشتنِ مشتری (بی Authorization) IP را نمی‌آورد؛ نوشتنِ صاحب/کارمند می‌آورد
  const customer = { ...req, get: (k) => ({ 'x-store-ticket': 'tk-secret' })[k] };
  assert.deepEqual(rr.keysOf(customer, { write: true }), [rr.keysOf(customer)[1]]);
  assert.equal(rr.keysOf(req, { write: true }).length, 4);
  for (const k of keys) {
    assert.match(k, /^[A-Za-z0-9_-]{16}$/);
    assert.ok(!/secret|198\.51/.test(k));
  }
  rr.remember(keys, Date.now() - 120_000);
  assert.equal(rr.isSticky(keys), false, 'مهلتِ ۶۰ثانیه‌ای گذشته');
  rr.remember(keys);
  assert.equal(rr.isSticky([keys[1]]), true);
});

/*
 * standby ِ واقعی (streaming replication) فقط وقتی `TEST_REPLICA_URL` داده شده — همان پایگاهِ آزمون روی standby ِ
 * همین PostgreSQL (`pg_basebackup -R`). سنجشِ تأخیر روی standby و «نوشتهٔ اصلی پس از بازپخش از نسخه» را می‌سنجد.
 */
test('standby ِ واقعی: سنجش «standby» با تأخیرِ کم؛ نوشتهٔ اصلی پس از بازپخش از نسخه خوانده می‌شود', {
  skip: (PGLITE || !process.env.TEST_REPLICA_URL) && 'بی TEST_REPLICA_URL (standby ِ واقعی)',
}, async () => {
  const good = config.db.replicaUrl;
  try {
    await rr._reset();
    config.db.replicaUrl = process.env.TEST_REPLICA_URL;
    await rr.probe();
    const st = rr.stats();
    assert.equal(st.standby, true, JSON.stringify(st));
    assert.ok(st.lagMs <= config.db.replicaMaxLagMs, JSON.stringify(st));
    assert.equal(st.usable, true);
    const key = `rr-standby-${uniq()}`;
    await db.query('INSERT INTO app_config (key, value, updated_at) VALUES ($1, $2, $3)', [key, 'x', db.now()]);
    const t0 = Date.now();
    let seen = null;
    while (!seen && Date.now() - t0 < 5000) {
      seen = await db.readScope.run({ wrote: false, sticky: false }, () => rr.one('SELECT value FROM app_config WHERE key = $1', [key]));
      if (!seen) await new Promise((r) => setTimeout(r, 50));
    }
    assert.ok(seen, 'نوشتهٔ اصلی در ۵ ثانیه به standby نرسید');
    assert.ok(rr.stats().reads >= 1, 'از standby خوانده شد');
    await db.query('DELETE FROM app_config WHERE key = $1', [key]);
  } finally {
    await rr._reset();
    config.db.replicaUrl = good;
    rr.start();
  }
});
