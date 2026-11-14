'use strict';
/**
 * S44 — دکمهٔ «بازیابی از پشتیبان» ِ دستگاهِ غیرفعال (گوشیِ نسخهٔ ۳ و کامپیوتر) فقط با پرچمِ `shop.lost_device_replay`:
 * `GET /api/shop/devices` با پرچمِ روشن `replay: true` دارد و با خاموش **همان پاسخِ پیشین** (هیچ کلیدِ تازه)؛ همان در
 * برای شاگرد (بی `members.view`) ۴۰۳ و برای دکانِ دیگر فقط دستگاه‌های خودش. خودِ بازیابی: `scenario-s44.e2e.mjs`
 * (گامِ `S44.replay`) و `LostDeviceRestoreTest` (برنامه).
 */
process.env.RATE_LOGIN_EDGE_MAX = process.env.RATE_LOGIN_EDGE_MAX || '100000';

const test = require('node:test');
const assert = require('node:assert/strict');
const h = require('./helpers');
const { query } = require('../src/db');

let owner;
let staff;
let shopId;

test.before(async () => {
  await h.start();
  owner = await h.newUser('صاحبِ دستگاهِ گم‌شده');
  shopId = (await h.post('/api/shop', { name: 'دکانِ دستگاه‌ها' }, { token: owner.accessToken })).body.shop.id;
  const code = (await h.get('/api/shop/staff-code', { token: owner.accessToken })).body.code;
  staff = await h.newUser('شاگردِ دستگاه');
  assert.ok([200, 201].includes((await h.post('/api/shop/join', { code }, { token: staff.accessToken })).status));
});
test.after(async () => { await h.stop(); });

test('پرچمِ خاموش: فهرستِ دستگاه‌ها همان پیشین، بی `replay`', async () => {
  const r = await h.get('/api/shop/devices', { token: owner.accessToken });
  assert.equal(r.status, 200, JSON.stringify(r.body));
  assert.deepEqual(Object.keys(r.body), ['devices']);
  assert.ok(r.body.devices.length >= 1);
});

test('پرچمِ روشن: `replay: true`؛ شاگرد ۴۰۳؛ دکانِ دیگر نه پرچم و نه دستگاهِ این دکان', async () => {
  await query("INSERT INTO live_config (scope, key, value, updated_at) VALUES ($1,'shop.lost_device_replay','true',1)", [shopId]);
  const r = await h.get('/api/shop/devices', { token: owner.accessToken });
  assert.equal(r.status, 200);
  assert.equal(r.body.replay, true);
  assert.deepEqual(Object.keys(r.body).sort(), ['devices', 'replay']);
  assert.equal((await h.get('/api/shop/devices', { token: staff.accessToken })).status, 403);
  assert.equal((await h.get('/api/shop/devices')).status, 401);

  const other = await h.newUser('صاحبِ دیگر');
  await h.post('/api/shop', { name: 'دکانِ دیگر' }, { token: other.accessToken });
  const o = await h.get('/api/shop/devices', { token: other.accessToken });
  assert.equal(o.status, 200);
  assert.equal('replay' in o.body, false);
  const mine = new Set(r.body.devices.map((d) => d.id));
  assert.equal(o.body.devices.some((d) => mine.has(d.id)), false);
});
