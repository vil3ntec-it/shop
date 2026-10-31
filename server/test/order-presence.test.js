'use strict';
/**
 * ORD-03 — نشانگرِ «باز / بسته» در بخشِ «سفارش‌ها»ی کارمند (lib/order-presence.js).
 *
 *   - پرچمِ `shop.orders.presence` خاموش ⇒ `summary` همان پاسخِ پیشین (بی `presence`) و `/config` ⇒ `presence: false`.
 *   - روشن ⇒ `summary.presence` همان داوریِ ویترین است که مشتری می‌بیند (`badge`، `connection`، `orderAcceptance`)
 *     — با همگام‌سازیِ تازه «باز»، با اعلامِ «بسته» بسته، با «باز تا …» زمانِ پایان، با تماسِ کهنه «آفلاین».
 *   - بی ویترین ⇒ `presence: null` (سفارشِ تلفنی همچنان)؛ ویترینِ خاموش ⇒ `published: false`.
 *   - بی ورود ⇒ ۴۰۱؛ پرچمِ سفارش خاموش ⇒ ۴۰۳ مثلِ پیش.
 */
const test = require('node:test');
const assert = require('node:assert/strict');
const h = require('./helpers');
const { query, newId, now } = require('../src/db');
const { resetAll } = require('../src/middleware/ratelimit');
const { staleMs, offlineMs } = require('../src/config').storefront;

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

async function shop(label, { presence = true, storefront = true } = {}) {
  const u = await h.newUser(label, 'shop');
  const made = await h.post('/api/shop', { name: `دکانِ ${label}` }, { token: u.accessToken });
  assert.equal(made.status, 201, JSON.stringify(made.body));
  const o = { ...u, token: u.accessToken, shopId: made.body.shop.id };
  await setFlag(o.shopId, 'shop.orders', true);
  if (presence) await setFlag(o.shopId, 'shop.orders.presence', true);
  if (storefront) {
    await setFlag(o.shopId, 'shop.storefront', true);
    o.slug = `pr-${uniq()}`;
    const put = await h.put('/api/shop/storefront', { slug: o.slug, enabled: true, title: `ویترینِ ${label}`, contactPhone: '0700000001' },
      { token: o.token });
    assert.equal(put.status, 200, JSON.stringify(put.body));
    //  یک دستگاهِ دکان همین حالا همگام شد ⇒ «اتصالِ تأییدشده»
    const dev = `dev-${uniq()}`;
    const p = await h.api('POST', '/api/sync/v1/push', {
      token: o.token, headers: { 'X-App': 'shop', 'X-Device': dev },
      body: { device_id: dev, schema_version: 2, ops: [] },
    });
    assert.equal(p.status, 200, JSON.stringify(p.body));
  }
  return o;
}

const summary = (s) => h.get('/api/shop/orders/summary', { token: s.token });
const seen = (s, t) => query(
  `UPDATE sync_devices SET last_seen_at=$2 WHERE app='shop' AND account_kind='shop' AND account_id=$1`, [s.shopId, t]);
const declare = (s, body) => h.post('/api/shop/storefront/presence', { eventId: `ev-${uniq()}-x`, ...body }, { token: s.token });
/** همان «وضعیت» ِ ویترینِ عمومی که مشتری می‌بیند */
const publicStatus = async (s) => {
  const r = await h.get(`/api/store/${s.slug}`);
  assert.equal(r.status, 200, JSON.stringify(r.body));
  return (r.body.store || r.body.profile || r.body).status;
};

test('پرچم خاموش: summary همان پاسخِ پیشین، بی presence؛ config ⇒ presence: false', async () => {
  const s = await shop('خاموش', { presence: false });
  const cfg = await h.get('/api/shop/orders/config', { token: s.token });
  assert.equal(cfg.body.presence, false);
  const r = await summary(s);
  assert.equal(r.status, 200, JSON.stringify(r.body));
  assert.deepEqual(Object.keys(r.body).sort(), ['counts', 'oldestNewAt', 'rev', 'serverTime']);
});

test('روشن: همان «باز / بسته» ِ ویترین — همگام ⇒ باز و پذیرا؛ اعلامِ بسته ⇒ بسته؛ «باز تا» ⇒ زمانِ پایان', async () => {
  const s = await shop('باز');
  assert.equal((await h.get('/api/shop/orders/config', { token: s.token })).body.presence, true);
  let r = await summary(s);
  assert.equal(r.status, 200, JSON.stringify(r.body));
  let p = r.body.presence;
  assert.equal(p.published, true);
  assert.equal(p.connection, 'confirmed');
  assert.equal(p.badge, 'open_online');
  assert.equal(p.orderAcceptance, 'accepting');
  assert.ok(p.lastConfirmedAt > 0);
  const pub = await publicStatus(s);
  for (const k of ['badge', 'connection', 'orderAcceptance', 'mode']) assert.equal(p[k], pub[k], `همان ${k} که مشتری می‌بیند`);

  assert.equal((await declare(s, { mode: 'closed' })).status, 200);
  p = (await summary(s)).body.presence;
  assert.deepEqual([p.badge, p.mode, p.orderAcceptance], ['closed', 'closed', 'later']);
  assert.equal((await publicStatus(s)).badge, 'closed');

  const until = Date.now() + 2 * 60 * 60 * 1000;
  assert.equal((await declare(s, { mode: 'online_until', declaredUntil: until })).status, 200);
  p = (await summary(s)).body.presence;
  assert.deepEqual([p.badge, p.declaredUntil], ['declared_until', until]);

  assert.equal((await declare(s, { mode: 'auto' })).status, 200);
  p = (await summary(s)).body.presence;
  assert.equal(p.badge, 'open_online');
});

test('تماسِ کهنه ⇒ «تأیید نشده» و سپس «آفلاین» (مشتری دکان را آفلاین می‌بیند)', async () => {
  const s = await shop('کهنه');
  await seen(s, Date.now() - staleMs - 60_000);
  let p = (await summary(s)).body.presence;
  assert.deepEqual([p.connection, p.badge, p.orderAcceptance], ['unconfirmed', 'open_unconfirmed', 'delayed']);
  await seen(s, Date.now() - offlineMs - 60_000);
  p = (await summary(s)).body.presence;
  assert.deepEqual([p.connection, p.badge], ['lost', 'offline']);
  assert.equal((await publicStatus(s)).badge, 'offline');
});

test('بی ویترین ⇒ presence: null؛ ویترینِ خاموش ⇒ published: false', async () => {
  const bare = await shop('بی‌ویترین', { storefront: false });
  const r = await summary(bare);
  assert.equal(r.status, 200, JSON.stringify(r.body));
  assert.equal(r.body.presence, null);

  const s = await shop('بسته‌ویترین');
  const off = await h.put('/api/shop/storefront', { slug: s.slug, enabled: false, title: 'ویترینِ خاموش', contactPhone: '0700000001' },
    { token: s.token });
  assert.equal(off.status, 200, JSON.stringify(off.body));
  assert.equal((await summary(s)).body.presence.published, false);
  //  ویترینِ روشنِ دکان ولی پرچمِ ویترین خاموش ⇒ مشتری نمی‌بیند ⇒ باز هم «بسته»
  const on = await h.put('/api/shop/storefront', { slug: s.slug, enabled: true, title: 'ویترینِ روشن', contactPhone: '0700000001' },
    { token: s.token });
  assert.equal(on.status, 200, JSON.stringify(on.body));
  assert.equal((await summary(s)).body.presence.published, true);
  await setFlag(s.shopId, 'shop.storefront', false);
  assert.equal((await summary(s)).body.presence.published, false);
});

test('دسترسی: بی ورود ⇒ ۴۰۱؛ پرچمِ سفارش خاموش ⇒ ۴۰۳؛ دکانِ دیگر وضعیتِ خودش را می‌بیند', async () => {
  const a = await shop('الف');
  const b = await shop('ب');
  assert.equal((await declare(a, { mode: 'closed' })).status, 200);
  assert.equal((await summary(b)).body.presence.badge, 'open_online', 'اعلامِ دکانِ دیگر اثر ندارد');
  assert.equal((await h.get('/api/shop/orders/summary', {})).status, 401);
  await setFlag(a.shopId, 'shop.orders', false);
  const r = await summary(a);
  assert.equal(r.status, 403);
  assert.equal(r.body.error.code, 'orders_disabled');
});
