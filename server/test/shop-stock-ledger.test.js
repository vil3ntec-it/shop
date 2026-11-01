'use strict';
/**
 * پرچمِ `shop.stock_v2` (#315): درِ `GET /api/shop/stock-ledger/config`.
 * پیش‌فرض خاموش؛ روشن برای همین دکان یا `all`؛ دکانِ دیگر خاموش می‌ماند؛ بی توکن ۴۰۱.
 */
const test = require('node:test');
const assert = require('node:assert/strict');
const h = require('./helpers');
const { query } = require('../src/db');

test.before(async () => { await h.start(); });
test.after(async () => { await h.stop(); });

const C = '/api/shop/stock-ledger/config';

async function shopWithStaff(label) {
  const owner = await h.newUser(`صاحب ${label}`);
  const made = await h.post('/api/shop', { name: `دکان ${label}` }, { token: owner.accessToken });
  const code = (await h.get('/api/shop/staff-code', { token: owner.accessToken })).body.code;
  const staff = await h.newUser(`شاگرد ${label}`);
  assert.ok([200, 201].includes((await h.post('/api/shop/join', { code }, { token: staff.accessToken })).status));
  return { shopId: made.body.shop.id, owner: owner.accessToken, staff: staff.accessToken };
}

test('پرچمِ shop.stock_v2: پیش‌فرض خاموش؛ روشن برای همین دکان؛ دکانِ دیگر خاموش؛ all برای همه', async () => {
  const t = await shopWithStaff('موجودی-الف');
  const u = await shopWithStaff('موجودی-ب');
  assert.equal((await h.get(C)).status, 401);
  assert.deepEqual((await h.get(C, { token: t.staff })).body, { enabled: false });
  await query("INSERT INTO live_config (scope, key, value, updated_at) VALUES ($1,'shop.stock_v2','true',1)", [t.shopId]);
  //  شاگرد هم می‌خواند — صندوقِ او باید همان روشِ بهای صاحب را اجرا کند
  assert.deepEqual((await h.get(C, { token: t.staff })).body, { enabled: true });
  assert.deepEqual((await h.get(C, { token: u.owner })).body, { enabled: false });
  await query("INSERT INTO live_config (scope, key, value, updated_at) VALUES ('all','shop.stock_v2','true',1)");
  assert.deepEqual((await h.get(C, { token: u.owner })).body, { enabled: true });
  //  مقدارِ خودِ دکان بر all جلو می‌افتد
  await query("UPDATE live_config SET value='false' WHERE scope=$1 AND key='shop.stock_v2'", [t.shopId]);
  assert.deepEqual((await h.get(C, { token: t.owner })).body, { enabled: false });
  await query("DELETE FROM live_config WHERE key='shop.stock_v2'");
});
