'use strict';
/**
 * دفترِ مصرف و هزینهٔ پیامک — OPS-09 (و پایهٔ سقفِ SEC-06).
 *
 *   ۱) راهِ واقعی و پذیرفته ⇒ هزینه = بهای واحدِ همان لحظه × تکه.
 *   ۲) راهِ `log` (آزمایشی) و ناموفق ⇒ هزینهٔ ۰؛ هر دو جدا شمرده می‌شوند.
 *   ۳) عوض شدنِ بها گذشته را عوض نمی‌کند.
 *   ۴) بهای نادرست ۴۰۰ و بی‌تغییر.
 *   ۵) گزارشِ هر دکان (پرهزینه بالا)، بی‌دکان جدا، بیرونِ بازه نه؛ جمعِ سراسری.
 *   ۶) ⛔ جدول ستونِ شماره، متن یا کد ندارد و `record` آن‌ها را نمی‌نویسد.
 */
const test = require('node:test');
const assert = require('node:assert/strict');
const h = require('./helpers');
const { query, many, now } = require('../src/db');
const usage = require('../src/lib/sms-usage');

const DAY = 24 * 60 * 60 * 1000;
let shopA = '';
let shopB = '';

async function makeShop(name) {
  const u = await h.newUser(name, 'shop');
  const made = await h.post('/api/shop', { name: `دکانِ ${name}` }, { token: u.accessToken });
  assert.equal(made.status, 201, JSON.stringify(made.body));
  return made.body.shop.id;
}

test.before(async () => {
  await h.start();
  shopA = await makeShop('پیامکی');
  shopB = await makeShop('کم‌پیامک');
});
test.after(async () => { await h.stop(); });

test('بها: پیش‌فرض ۰؛ فقط عددِ صحیحِ نامنفی، نادرست ۴۰۰ و بی‌تغییر', async () => {
  assert.equal(await usage.unitCost(), 0);
  const out = await usage.setUnitCost(150);
  assert.deepEqual(out, { before: 0, unitCostMinor: 150, currency: 'AFN' });
  assert.equal((await usage.setUnitCost('150')).unitCostMinor, 150);
  for (const bad of [-1, 1.5, 'abc', '12a', 1e12, null, undefined, '']) {
    await assert.rejects(usage.setUnitCost(bad), (e) => e.status === 400 && e.code === 'bad_sms_cost', String(bad));
  }
  assert.equal(await usage.unitCost(), 150);
});

test('هزینه: واقعی × تکه؛ لاگ و ناموفق ۰؛ تغییرِ بها گذشته را عوض نمی‌کند', async () => {
  await usage.setUnitCost(150);
  const real = await usage.record({ shopId: shopA, app: 'shop', purpose: 'store_phone', via: 'sms', parts: 2 });
  assert.equal(real.costMinor, 300);
  assert.equal((await usage.record({ shopId: shopA, purpose: 'store_phone', via: 'whatsapp' })).costMinor, 150);
  assert.equal((await usage.record({ shopId: shopA, purpose: 'store_phone', via: 'log' })).costMinor, 0);
  assert.equal((await usage.record({ shopId: shopA, purpose: 'store_phone', via: 'sms', ok: false })).costMinor, 0);
  //  تکهٔ نادرست ⇒ ۱؛ سقفِ ۲۰
  assert.equal((await usage.record({ shopId: shopB, purpose: 'vip_code', via: 'webhook', parts: 0 })).costMinor, 150);
  assert.equal((await usage.record({ purpose: 'login', via: 'sms', parts: 999 })).costMinor, 150 * usage.LIMITS.maxParts);

  await usage.setUnitCost(1000);
  const a = await usage.totals({ shopId: shopA });
  assert.deepEqual(a, { attempts: 4, sent: 2, failed: 1, mock: 1, parts: 3, costMinor: 450 });
  assert.equal((await usage.totals({ shopId: shopB })).costMinor, 150);
  const all = await usage.totals({});
  assert.equal(all.attempts, 6);
  assert.equal(all.costMinor, 450 + 150 + 3000);
});

test('countSince: همهٔ دکان‌ها از یک لحظه؛ okOnly فقط پذیرفته‌ها (سقفِ SEC-06)', async () => {
  //  تا این‌جا ۶ ردیفِ تازه: ۵ پذیرفته (۴ واقعی + ۱ لاگ) و ۱ ناموفق
  const t = now();
  assert.equal(await usage.countSince(t - DAY), 6);
  assert.equal(await usage.countSince(t - DAY, { okOnly: true }), 5);
  assert.equal(await usage.countSince(t + DAY), 0);
  await usage.record({ shopId: shopB, purpose: 'vip_code', via: 'sms', at: t - 2 * DAY });
  assert.equal(await usage.countSince(t - DAY), 6, 'ردیفِ پیش از مرز شمرده شد');
  assert.equal(await usage.countSince(t - 3 * DAY), 7);
  assert.equal(await usage.countSince('bad'), 7, 'مرزِ نادرست ⇒ از آغاز');
  await query('DELETE FROM sms_usage WHERE at < $1', [t - DAY]);
});

test('گزارش: هر دکان جدا، پرهزینه بالا، بی‌دکان جدا، بیرونِ بازه نه', async () => {
  //  ردیفِ ۴۰ روزه در گزارشِ ۳۰ روزه نیست ولی در ۶۰ روزه هست
  await usage.record({ shopId: shopB, purpose: 'vip_code', via: 'sms', at: now() - 40 * DAY });
  const r = await usage.report({});
  assert.equal(r.days, 30);
  assert.equal(r.currency, 'AFN');
  assert.equal(r.unitCostMinor, 1000);
  assert.deepEqual(r.shops.map((s) => s.shopId), ['', shopA, shopB]);
  const a = r.shops.find((s) => s.shopId === shopA);
  assert.equal(a.shopName, 'دکانِ پیامکی');
  assert.deepEqual([a.sent, a.failed, a.mock, a.costMinor], [2, 1, 1, 450]);
  assert.ok(a.lastAt > 0);
  assert.equal(r.shops.find((s) => s.shopId === shopB).attempts, 1);
  assert.equal(r.total.attempts, 6);
  const wide = await usage.report({ days: 60 });
  assert.equal(wide.shops.find((s) => s.shopId === shopB).attempts, 2);
  //  روزِ نادرست ⇒ مرزها
  assert.equal((await usage.report({ days: 'x' })).days, 30);
  assert.equal((await usage.report({ days: 99999 })).days, usage.LIMITS.maxDays);
  assert.equal((await usage.report({ days: -5 })).days, 1);
});

test('⛔ نه ستونِ شماره/متن/کد، نه نوشتنِ آن', async () => {
  const cols = (await many(
    `SELECT column_name FROM information_schema.columns WHERE table_name = 'sms_usage' ORDER BY ordinal_position`
  )).map((c) => c.column_name);
  assert.deepEqual(cols, ['id', 'shop_id', 'app', 'purpose', 'via', 'ok', 'parts', 'cost_minor', 'at']);
  await usage.record({ shopId: shopA, purpose: 'login', via: 'log', phone: '+93700999888', message: 'کد: 123456', code: '123456' });
  const dump = JSON.stringify(await many('SELECT * FROM sms_usage'));
  for (const s of ['+93700999888', '123456']) assert.ok(!dump.includes(s), s);
  //  رشتهٔ بلند بریده می‌شود
  await usage.record({ purpose: 'p'.repeat(200), via: 'log' });
  assert.ok((await many(`SELECT purpose FROM sms_usage WHERE purpose LIKE 'ppp%'`))[0].purpose.length <= 40);
  await query('DELETE FROM sms_usage');
});
