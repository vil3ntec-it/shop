'use strict';
/**
 * حافظهٔ کوتاهِ پرچم‌های دکان (`HOT_CACHE_MS`، `lib/hot-cache.js`؛ docs/SCALING-fa.md) — پیش‌فرض خاموش.
 * این‌جا روشن و بلند (۶۰ ثانیه) تا فقط «باطل شدن با نوشتن» تازه‌اش کند:
 *   • خواندنِ دوم از حافظه است (نوشتنِ مستقیمِ SQL دیده نمی‌شود) — همان چیزی که بار را کم می‌کند؛
 *   • هر درِ نوشتنِ پرچم (`live-config.set/remove/setOnce`، `all` یا خودِ دکان) و «غیرفعال کردنِ سرویس»
 *     (`shop-holds`) همان دم باطل می‌کند؛ خواندنِ درونِ تراکنش هرگز از حافظه نیست؛
 *   • خواندن‌های هم‌زمان یک پرسش‌اند و نوشتنِ وسطِ خواندن نتیجهٔ کهنه را در حافظه نمی‌گذارد.
 */
process.env.HOT_CACHE_MS = '60000';
const test = require('node:test');
const assert = require('node:assert/strict');
const h = require('./helpers');
const db = require('../src/db');
const hot = require('../src/lib/hot-cache');
const liveConfig = require('../src/lib/live-config');
const orders = require('../src/lib/orders');

let shopId = '';
test.before(async () => {
  await h.start();
  const u = await h.newUser('حافظه', 'shop');
  shopId = (await h.api('POST', '/api/shop', { token: u.accessToken, body: { name: 'دکانِ حافظه' } })).body.shop.id;
});
test.after(async () => { await h.stop(); });
test.beforeEach(() => hot.clear());

test('خواندنِ دوم از حافظه؛ نوشتنِ پرچم همان دم باطل می‌کند', async () => {
  assert.equal(await orders.flagOf(shopId, 'shop.orders'), false);
  await db.query("INSERT INTO live_config (scope, key, value, updated_at, updated_by) VALUES ($1, 'shop.orders', 'true', 0, 't')", [shopId]);
  assert.equal(await orders.flagOf(shopId, 'shop.orders'), false, 'نوشتنِ مستقیم (بی درِ live-config) تا ttl دیده نمی‌شود');
  assert.equal(await orders.flagOf(shopId, 'shop.orders', (t, p) => db.query(t, p)), true, 'درونِ تراکنش هرگز از حافظه نیست');
  await liveConfig.remove(shopId, 'shop.orders', 't');
  await liveConfig.set(shopId, 'shop.orders', true, 't');
  assert.equal(await orders.flagOf(shopId, 'shop.orders'), true, 'نوشتن از درِ live-config ⇒ تازه');
  await liveConfig.set('all', 'shop.orders', false, 't');
  await liveConfig.remove(shopId, 'shop.orders', 't');
  assert.equal(await orders.flagOf(shopId, 'shop.orders'), false, 'پاک کردن و `all` هم باطل می‌کنند');
  await liveConfig.set('all', 'shop.orders', true, 't');
  assert.equal(await orders.flagOf(shopId, 'shop.orders'), true, 'نوشتنِ `all` پرچمِ هر دکان را باطل می‌کند');
});

test('«غیرفعال کردنِ سرویس» (shop-holds) پرچمِ همان دکان را باطل می‌کند', async () => {
  await liveConfig.set(shopId, 'shop.storefront', true, 't');
  assert.equal(await orders.flagOf(shopId, 'shop.storefront'), true);
  const holds = require('../src/lib/shop-holds');
  const req = { admin: { id: 'adm_t', role: 'superadmin' }, headers: {}, get: () => '' };
  await holds.place(shopId, { ...req, body: { kind: 'service_disabled', service: 'shop.storefront' } }, { reason: 'آزمونِ حافظهٔ پرچم' });
  assert.equal(await orders.flagOf(shopId, 'shop.storefront'), false, 'سرویسِ غیرفعال ⇒ پرچم خاموش، همان دم');
});

test('هم‌زمان‌ها یک پرسش؛ نوشتنِ وسطِ خواندن کهنه را نگه نمی‌دارد؛ ttl ِ ۰ ⇒ بی حافظه', async () => {
  let loads = 0;
  const slow = () => new Promise((r) => setTimeout(() => r(++loads), 30));
  const vals = await Promise.all([hot.get('t', 'k', slow), hot.get('t', 'k', slow), hot.get('t', 'k', slow)]);
  assert.deepEqual(vals, [1, 1, 1]);
  assert.equal(await hot.get('t', 'k', slow), 1, 'از حافظه');
  const inflight = hot.get('t', 'x', slow);
  hot.invalidate('t', 'x');
  await inflight;
  assert.equal(await hot.get('t', 'x', slow), 3, 'خواندنِ پیش از نوشتن در حافظه نماند');
  const config = require('../src/config');
  const was = config.hotCache.ttlMs;
  config.hotCache.ttlMs = 0;
  try {
    assert.equal(await hot.get('t', 'k', slow), 4);
    assert.equal(await hot.get('t', 'k', slow), 5);
  } finally { config.hotCache.ttlMs = was; }
});
