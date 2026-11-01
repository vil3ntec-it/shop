'use strict';
/**
 * TEN-03 — «لینک خصوصی منقضی/لغوشده کار نکند» (§۲۹.۸؛ §۲۹.۷ بندِ ۴).
 *
 * لینکِ پیگیریِ سفارشِ ویترین (`/api/store/:slug/orders/:token` و `/reorder` و `/cancel`) با
 * پرچمِ `shop.storefront.link_expiry` زمان‌دار و باطل‌شدنی است (`lib/storefront.js`، مهاجرتِ
 * ۱۴۰). پرچمِ خاموش ⇒ همان رفتارِ امروز (آزمونِ مرجع).
 */
process.env.RATE_STORE_CHECKOUT_MAX = '50';
const test = require('node:test');
const assert = require('node:assert/strict');
const h = require('./helpers');
const config = require('../src/config');
const { query, one, newId, now } = require('../src/db');
const { resetAll } = require('../src/middleware/ratelimit');
const sf = require('../src/lib/storefront');

let admin = '';
let seq = 0;
const uniq = () => `${Date.now().toString(36)}${++seq}`;

test.before(async () => {
  await h.start();
  const pw = require('../src/lib/password');
  await query(
    `INSERT INTO admins (id, username, name, password_hash, role, status, created_at)
     VALUES ($1,'link-admin','مدیر',$2,'superadmin','active',$3)`,
    [newId('adm'), await pw.hashPassword('Admin!12345'), now()]
  );
  admin = (await h.post('/api/admin/login', { username: 'link-admin', password: 'Admin!12345' })).body.token;
  assert.ok(admin);
});
test.after(async () => { await h.stop(); });
test.beforeEach(() => resetAll());

async function setFlag(scope, key, value) {
  const r = await h.put('/api/admin/pump/live-config', { scope, key, value }, { token: admin });
  assert.equal(r.status, 200, JSON.stringify(r.body));
}

/** دکانِ روشن با ویترین و یک کالای منتشرشده */
async function liveStore(label) {
  const o = await h.newUser(label);
  const made = await h.post('/api/shop', { name: `دکانِ ${label}` }, { token: o.accessToken });
  assert.equal(made.status, 201, JSON.stringify(made.body));
  const shopId = made.body.shop.id;
  await setFlag(shopId, 'shop.orders', true);
  await setFlag(shopId, 'shop.storefront', true);
  const slug = `l-${uniq()}`;
  const put = await h.put('/api/shop/storefront', { slug, enabled: true, title: `ویترینِ ${label}`, contactPhone: '0700000001' }, { token: o.accessToken });
  assert.equal(put.status, 200, JSON.stringify(put.body));
  const device = 'dev-link';
  const p = await h.api('POST', '/api/sync/v1/push', {
    token: o.accessToken, headers: { 'X-App': 'shop', 'X-Device': device },
    body: { device_id: device, schema_version: 2, ops: [{ op_id: `op-${uniq()}`, ts: Date.now(), table: 'products', row_id: 'rice', type: 'insert', fields: { name: 'برنج', unit: 'کیلو', salePrice: 100 } }] },
  });
  assert.equal(p.status, 200, JSON.stringify(p.body));
  assert.equal((await h.put('/api/shop/storefront/products/rice', { published: true }, { token: o.accessToken })).status, 200);
  return { ...o, token: o.accessToken, shopId, slug };
}

async function ticketFor(slug, phone) {
  const s = await h.post(`/api/store/${slug}/verify/start`, { phone });
  assert.equal(s.status, 200, JSON.stringify(s.body));
  const c = await h.post(`/api/store/${slug}/verify/check`, { phone, code: s.body.devCode });
  assert.equal(c.status, 200, JSON.stringify(c.body));
  return c.body.ticket;
}

let phoneN = 0;
async function order(s) {
  const phone = `07955${String(10000 + (++phoneN)).slice(-5)}`;
  const ticket = await ticketFor(s.slug, phone);
  const r = await h.post(`/api/store/${s.slug}/checkout`, {
    fulfillment: 'pickup', customer: { name: 'مشتری', phone }, lines: [{ productId: 'rice', qtyMilli: 1000 }],
  }, { headers: { 'Idempotency-Key': `ck-${uniq()}-key`, 'X-Store-Ticket': ticket } });
  assert.equal(r.status, 201, JSON.stringify(r.body));
  const id = (await one(`SELECT id FROM online_orders WHERE shop_id=$1 ORDER BY created_at DESC LIMIT 1`, [s.shopId])).id;
  return { token: r.body.order.token, id, ticket };
}
const track = (s, token) => h.get(`/api/store/${s.slug}/orders/${encodeURIComponent(token)}`);
const reorder = (s, token) => h.get(`/api/store/${s.slug}/orders/${encodeURIComponent(token)}/reorder`);
const cancel = (s, token) => h.post(`/api/store/${s.slug}/orders/${encodeURIComponent(token)}/cancel`, {});
const revoke = (s, id, token = s.token) => h.post(`/api/shop/orders/${id}/track-link/revoke`, {}, { token });
const TTL = () => config.storefront.trackLinkTtlMs;
const isV1 = (t) => /^[0-9A-Z]{26}\.[A-Za-z0-9_-]{32}$/.test(t);
const isV2 = (t) => /^[0-9A-Z]{26}\.[0-9a-z]+\.[0-9a-z]+\.[A-Za-z0-9_-]{32}$/.test(t);

test('آزمونِ مرجع، پرچمِ خاموش: توکنِ v1 ِ امروز، بی انقضا، و درِ باطل کردن بسته', async () => {
  const s = await liveStore('بی‌پرچمِ لینک');
  const o = await order(s);
  assert.ok(isV1(o.token), o.token);
  assert.equal((await track(s, o.token)).status, 200);
  //  سفارشِ یک‌ساله با همان لینک هنوز باز می‌شود
  await query('UPDATE online_orders SET created_at=$2 WHERE id=$1', [o.id, now() - 400 * 24 * 3600 * 1000]);
  assert.equal((await track(s, o.token)).status, 200);
  const r = await revoke(s, o.id);
  assert.equal(r.status, 403, JSON.stringify(r.body));
  assert.equal(r.body.error.code, 'link_policy_disabled');
  assert.equal((await one('SELECT track_gen FROM online_orders WHERE id=$1', [o.id])).track_gen, 0);
  assert.equal((await track(s, o.token)).status, 200);
});

test('پرچمِ روشن: باطل کردن ⇒ هر لینکِ پیشین (v1 و v2) در پیگیری، خریدِ دوباره و لغو ۴۱۰؛ لینکِ تازه کار می‌کند', async () => {
  const s = await liveStore('باطل‌شدنی');
  await setFlag(s.shopId, 'shop.storefront.link_expiry', true);
  const o = await order(s);
  assert.ok(isV2(o.token), o.token);
  const v1 = sf.tokenOf(s.shopId, o.id);
  assert.equal((await track(s, o.token)).status, 200);
  assert.equal((await track(s, v1)).status, 200, 'لینکِ v1 ِ پیشین تا پیش از باطل شدن کار می‌کند');

  const r = await revoke(s, o.id);
  assert.equal(r.status, 200, JSON.stringify(r.body));
  assert.ok(isV2(r.body.token) && r.body.token !== o.token);
  const log = await one(`SELECT * FROM audit_logs WHERE action='order.track_link_revoked' AND target_id=$1`, [o.id]);
  assert.ok(log, 'سابقه نشست');

  for (const old of [o.token, v1]) {
    for (const [name, call] of [['track', track], ['reorder', reorder], ['cancel', cancel]]) {
      const res = await call(s, old);
      assert.equal(res.status, 410, `${name} ⇒ ${res.status} ${JSON.stringify(res.body)}`);
      assert.equal(res.body.error.code, 'link_revoked', name);
      assert.ok(!JSON.stringify(res.body).includes('برنج'), 'هیچ دادهٔ سفارشی برنگشت');
    }
  }
  assert.equal((await one('SELECT status FROM online_orders WHERE id=$1', [o.id])).status, 'new', 'لینکِ باطل لغو نکرد');
  const fresh = await track(s, r.body.token);
  assert.equal(fresh.status, 200, JSON.stringify(fresh.body));
  assert.equal(fresh.body.order.lines[0].name, 'برنج');

  //  «سفارش‌های من» با شمارهٔ تأییدشده لینکِ نسلِ جاری می‌دهد
  const mine = await h.get(`/api/store/${s.slug}/orders`, { headers: { 'X-Store-Ticket': o.ticket } });
  assert.equal(mine.status, 200, JSON.stringify(mine.body));
  assert.equal((await track(s, mine.body.orders[0].token)).status, 200);
});

test('پرچمِ روشن: لینکِ کهنه‌تر از مهلت ۴۱۰ link_expired؛ v1 ِ سفارشِ کهنه هم؛ «سفارش‌های من» لینکِ تازه می‌دهد', async () => {
  const s = await liveStore('زمان‌دار');
  await setFlag(s.shopId, 'shop.storefront.link_expiry', true);
  const o = await order(s);
  const old = sf.tokenOf(s.shopId, o.id, { gen: 0, at: now() - TTL() - 60_000 });
  const edge = sf.tokenOf(s.shopId, o.id, { gen: 0, at: now() - TTL() + 60_000 });
  const exp = await track(s, old);
  assert.equal(exp.status, 410, JSON.stringify(exp.body));
  assert.equal(exp.body.error.code, 'link_expired');
  assert.equal((await track(s, edge)).status, 200, 'هنوز در مهلت');

  const v1 = sf.tokenOf(s.shopId, o.id);
  assert.equal((await track(s, v1)).status, 200, 'v1 ِ سفارشِ تازه در مهلت');
  await query('UPDATE online_orders SET created_at=$2 WHERE id=$1', [o.id, now() - TTL() - 60_000]);
  assert.equal((await track(s, v1)).body.error?.code, 'link_expired', 'v1 = صادرشده هنگامِ ساختِ سفارش');
  assert.equal((await cancel(s, v1)).status, 410);

  const mine = await h.get(`/api/store/${s.slug}/orders`, { headers: { 'X-Store-Ticket': o.ticket } });
  assert.equal(mine.status, 200, JSON.stringify(mine.body));
  const again = mine.body.orders.find(() => true).token;
  assert.equal((await track(s, again)).status, 200, 'لینکِ تازهٔ «سفارش‌های من»');
});

test('پرچمِ روشن: دست بردن در صدور یا نسل، و لینکِ دکانِ دیگر ⇒ ۴۰۴ ِ یکسان (HMAC)', async () => {
  const s = await liveStore('دست‌کاری');
  const other = await liveStore('دکانِ دیگرِ لینک');
  await setFlag('all', 'shop.storefront.link_expiry', true);
  try {
    const o = await order(s);
    assert.ok(isV2(o.token), o.token);
    const [id, iat, gen, mac] = o.token.split('.');
    const later = `${id}.${(Number.parseInt(iat, 36) + 365 * 24 * 3600 * 1000).toString(36)}.${gen}.${mac}`;
    const genUp = `${id}.${iat}.${(Number.parseInt(gen, 36) + 1).toString(36)}.${mac}`;
    for (const t of [later, genUp, `${o.token}x`]) {
      const r = await track(s, t);
      assert.equal(r.status, 404, `${t} ⇒ ${r.status}`);
      assert.equal(r.body.error.code, 'order_not_found');
    }
    const cross = await track(other, o.token);
    assert.equal(cross.status, 404, 'لینکِ «الف» روی ویترینِ «ب»');
    //  درِ باطل کردن: دکانِ دیگر ۴۰۴ و ردیفِ «الف» دست نخورد
    const r = await revoke(other, o.id);
    assert.equal(r.status, 404, JSON.stringify(r.body));
    assert.equal((await one('SELECT track_gen FROM online_orders WHERE id=$1', [o.id])).track_gen, 0);
    assert.equal((await track(s, o.token)).status, 200);
  } finally {
    await setFlag('all', 'shop.storefront.link_expiry', false);
  }
});

test('پرچمِ روشن: شاگرد (بی orders.contact) لینک را باطل نمی‌کند؛ سفارشِ غیرِ ویترین لینک ندارد', async () => {
  const s = await liveStore('شاگردِ لینک');
  await setFlag(s.shopId, 'shop.storefront.link_expiry', true);
  const o = await order(s);
  const code = (await h.get('/api/shop/staff-code', { token: s.token })).body.code;
  const staff = await h.newUser('شاگردِ لینک');
  assert.ok((await h.post('/api/shop/join', { code }, { token: staff.accessToken })).status < 300);
  const r = await revoke(s, o.id, staff.accessToken);
  assert.equal(r.status, 403, JSON.stringify(r.body));
  assert.equal((await one('SELECT track_gen FROM online_orders WHERE id=$1', [o.id])).track_gen, 0);

  const phone = await h.post('/api/shop/orders', {
    source: 'phone', fulfillment: 'pickup', customer: { name: 'تلفنی', phone: '0790001122' },
    lines: [{ productId: 'rice', name: 'برنج', qtyMilli: 1000, unitPriceMinor: 10000 }],
  }, { token: s.token, headers: { 'Idempotency-Key': `ph-${uniq()}-key` } });
  assert.equal(phone.status, 201, JSON.stringify(phone.body));
  const nope = await revoke(s, phone.body.order.id);
  assert.equal(nope.status, 404, JSON.stringify(nope.body));
});
