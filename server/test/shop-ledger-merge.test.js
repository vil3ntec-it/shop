'use strict';
/**
 * پرچمِ ادغامِ سه‌طرفهٔ دفترِ برنامه — `GET /api/shop/ledger-merge/config` (#407، ذخیرهٔ کهنه).
 * پیش‌فرض خاموش؛ روشن فقط با `live_config` برای همان دکان یا `all`؛ مقدارِ دکان بر `all` جلو می‌افتد؛
 * فقط `true` روشن است؛ بی توکن ۴۰۱.
 */
process.env.RATE_LOGIN_EDGE_MAX = process.env.RATE_LOGIN_EDGE_MAX || '100000';

const test = require('node:test');
const assert = require('node:assert/strict');
const h = require('./helpers');
const { query } = require('../src/db');

test.before(async () => { await h.start(); });
test.after(async () => { await h.stop(); });

const C = '/api/shop/ledger-merge/config';

async function shop(label) {
  const owner = await h.newUser(`صاحب ${label}`);
  const made = await h.post('/api/shop', { name: `دکان ${label}` }, { token: owner.accessToken });
  return { shopId: made.body.shop.id, token: owner.accessToken };
}

const flag = (scope, value) => query(
  "INSERT INTO live_config (scope, key, value, updated_at) VALUES ($1,'shop.ledger_merge',$2,1) "
  + 'ON CONFLICT (scope, key) DO UPDATE SET value=EXCLUDED.value',
  [String(scope), value],
);

test('پرچمِ shop.ledger_merge: پیش‌فرض خاموش؛ روشن برای همین دکان یا all؛ دکان بر all جلو؛ بی توکن ۴۰۱', async () => {
  const a = await shop('الف');
  const b = await shop('ب');
  assert.equal((await h.get(C)).status, 401);
  assert.deepEqual((await h.get(C, { token: a.token })).body, { enabled: false });

  await flag(a.shopId, 'true');
  assert.deepEqual((await h.get(C, { token: a.token })).body, { enabled: true });
  assert.deepEqual((await h.get(C, { token: b.token })).body, { enabled: false }, 'دکانِ دیگر خاموش می‌ماند');

  await flag('all', 'true');
  assert.deepEqual((await h.get(C, { token: b.token })).body, { enabled: true });
  await flag(a.shopId, 'false');
  assert.deepEqual((await h.get(C, { token: a.token })).body, { enabled: false }, 'مقدارِ دکان بر all جلو می‌افتد');

  await flag(b.shopId, '"yes"');
  assert.deepEqual((await h.get(C, { token: b.token })).body, { enabled: false }, 'فقط true روشن است');
  await query("DELETE FROM live_config WHERE key='shop.ledger_merge'");
});
