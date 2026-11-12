'use strict';
/**
 * LOC-03 — «این هفته» ِ خلاصهٔ پیک از آغازِ هفتهٔ همگام‌شدهٔ دکان (`tohid-policy/week_start`)، نه شنبهٔ ثابت.
 * پشتِ پرچمِ خاموشِ `shop.courier_week_rule` (`docs/FLAGS-ON-fa.md` ۳۰): خاموش ⇒ همان شنبهٔ پیشین.
 */
process.env.RATE_LOGIN_EDGE_MAX = process.env.RATE_LOGIN_EDGE_MAX || '100000';

const test = require('node:test');
const assert = require('node:assert/strict');
const h = require('./helpers');
const { query } = require('../src/db');
const cs = require('../src/lib/courier-summary');

let shopId;
const D = 86_400_000;
//  سه‌شنبه ۲۰۲۶-۱۰-۲۰، ساعتِ ۱۰ ِ کابل
const TUE = Date.UTC(2026, 9, 20, 5, 30);

const flag = (on) => query(
  `INSERT INTO live_config (scope, key, value, updated_at) VALUES ($1,'shop.courier_week_rule',$2,1)
   ON CONFLICT (scope, key) DO UPDATE SET value = excluded.value`, [shopId, on ? 'true' : 'false']
);
const setting = (type, value) => query(
  `INSERT INTO shop_setting_keys (shop_id, store, key, type, value, rev, updated_at, updated_by)
   VALUES ($1,'tohid-policy','week_start',$2,$3::jsonb,1,1,'آزمون')
   ON CONFLICT (shop_id, store, key) DO UPDATE SET type = excluded.type, value = excluded.value`,
  [shopId, type, JSON.stringify(value)]
);

test.before(async () => {
  await h.start();
  const owner = await h.newUser('صاحبِ هفتهٔ دکان');
  shopId = (await h.post('/api/shop', { name: 'دکانِ هفتهٔ یکشنبه' }, { token: owner.accessToken })).body.shop.id;
});
test.after(() => h.stop());

test('periodStarts: آغازِ هفته از روزِ دادهٔ دکان؛ نادرست یا نیامده ⇒ شنبه', () => {
  const at = (w) => cs.periodStarts(TUE, 'Asia/Kabul', w);
  assert.equal(at(6).weekDate, '2026-10-17');
  assert.equal(at(7).weekDate, '2026-10-18');
  assert.equal(at(1).weekDate, '2026-10-19');
  assert.equal(at(7).week, at(7).today - 2 * D);
  assert.equal(at(1).week, at(1).today - D);
  for (const bad of [undefined, 3, 0, 8, '7', NaN]) assert.equal(at(bad).weekDate, '2026-10-17', String(bad));
  //  روزِ آغاز خودش «این هفته» را از امروز می‌گیرد
  const sun = cs.periodStarts(Date.UTC(2026, 9, 18, 5, 30), 'Asia/Kabul', 7);
  assert.equal(sun.week, sun.today);
});

test('shopWeekStart: پرچمِ خاموش ⇒ شنبه؛ روشن ⇒ کلیدِ همگام‌شدهٔ دکان؛ برداشته یا بیرون از فهرست ⇒ شنبه', async () => {
  assert.equal(await cs.shopWeekStart(shopId), 6, 'بی کلید و بی پرچم');
  await setting('i', 7);
  assert.equal(await cs.shopWeekStart(shopId), 6, 'پرچمِ خاموش همان رفتارِ پیشین');
  await flag(true);
  assert.equal(await cs.shopWeekStart(shopId), 7);
  await setting('i', 1);
  assert.equal(await cs.shopWeekStart(shopId), 1);
  await setting('i', 3);
  assert.equal(await cs.shopWeekStart(shopId), 6, 'بیرون از ShopWeek.STARTS');
  await setting('s', '7');
  assert.equal(await cs.shopWeekStart(shopId), 6, 'نوعِ نادرست');
  await setting('del', null);
  assert.equal(await cs.shopWeekStart(shopId), 6, 'کلیدِ برداشته‌شده');
  assert.equal(await cs.shopWeekStart('دکانِ دیگر'), 6, 'کلیدِ دکانِ دیگر خوانده نمی‌شود');
  await flag(false);
});

test('خلاصهٔ پیک: «این هفته» از یکشنبهٔ دکان وقتی پرچم روشن است، وگرنه از شنبه', async () => {
  const courier = { id: 'پیکِ بی‌کار', name: 'پیک', active: true, cash_cap_minor: 0 };
  await setting('i', 7);
  const off = await cs.summary(shopId, courier);
  assert.equal(off.stats.week.from, cs.periodStarts(off.serverTime, 'Asia/Kabul', 6).week);
  await flag(true);
  const on = await cs.summary(shopId, courier);
  const want = cs.periodStarts(on.serverTime, 'Asia/Kabul', 7);
  assert.equal(on.stats.week.from, want.week);
  assert.equal(on.stats.today.from, want.today);
  //  شنبه و یکشنبه آغازِ دو هفتهٔ جدا را می‌دهند (جز یکشنبه که هر دو امروز است)
  const sat = cs.periodStarts(on.serverTime, 'Asia/Kabul', 6);
  if (sat.weekDate !== want.weekDate) assert.notEqual(on.stats.week.from, sat.week);
  await flag(false);
});
