'use strict';
/**
 * SR46-1 (بازبینیِ امنیتیِ دستهٔ ۴۶) — تصمیمِ ۱-۱۱ (۳) با پرچمِ `shop.price_cap`: قیمتِ عمده فقط برای مشتریِ لیستِ عمدهٔ همین
 * سرور «قیمتِ فهرست» است. پیش از این، کارمندی که تخفیف ندارد خودش مشتری را «عمده» می‌کرد (درجِ مشتریِ تازه یا تغییرِ
 * `priceList` ِ مشتریِ خرده — در همان دسته یا دستهٔ پیشین) و بعد به قیمتِ عمده می‌فروخت: همان «ادعای صندوق» از راهِ ردیفِ مشتری.
 *
 * سنجه‌ها: با پرچمِ روشن، کارمندِ بی «تخفیفِ آزاد» مشتریِ تازهٔ عمده نمی‌سازد و مشتریِ خرده را عمده نمی‌کند (v3 ⇒
 * `discount_not_allowed` و صندوقِ تعارض؛ راهِ قدیم ⇒ ۴۰۳ و کلِ دسته برمی‌گردد)؛ ویرایشِ مشتریِ عمدهٔ موجود (بی تغییرِ لیست) و
 * برگرداندن به خرده آزاد؛ مدیرِ دارای تخفیفِ آزاد و صاحب آزاد؛ پرچمِ خاموش همان امروز.
 */
process.env.SYNC_V3_ENABLED = '1';

const test = require('node:test');
const assert = require('node:assert/strict');
const h = require('./helpers');
const { one, query, newId, now } = require('../src/db');
const hlc = require('../src/lib/hlc');

let admin = '';
let shopId;
let owner;
let manager;
let staff;
let n = 0;
const oid = () => `01JPL${String(++n).padStart(21, '0')}`;
let tick = 0;
const H = (node) => hlc.format({ wall: Date.now() + (++tick), counter: 0, node });

const liveFlag = (key, value, scope) => h.put('/api/admin/pump/live-config', { scope, key, value }, { token: admin })
  .then((r) => assert.equal(r.status, 200, JSON.stringify(r.body)));
const push3 = (who, device, ops) => h.api('POST', '/api/sync/v3/push', { token: who.accessToken, body: { device_id: device, protocol: 3, ops } });
const why = (r) => r.body.results.map((x) => (x.status === 'applied' || x.status === 'duplicate' ? x.status : x.reason || x.status));
const row3 = (table, id) => one(
  `SELECT data FROM sync_v3_rows WHERE app='shop' AND account_id=$1 AND table_name=$2 AND row_id=$3`, [shopId, table, id]
);
const boxOf = (opId) => one(`SELECT kind, detail FROM sync_v3_conflicts WHERE account_id=$1 AND loser_op_id=$2`, [shopId, opId]);
const debtor = (id, fields, kind = 'insert') => ({ op_id: oid(), hlc: H('pos'), table: 'debtors', row_id: id, kind, fields });

function sale3(id, debtorId, unit) {
  const tx = `tx.${oid()}`;
  const head = { totalMinor: unit, discountMinor: 0, finalTotalMinor: unit, paidAmountMinor: unit, remainingMinor: 0,
    debtGivenMinor: 0, paymentMethod: 'cash', status: 'completed', date: '2026-11-08', debtorId };
  return [
    { op_id: oid(), hlc: H('pos'), table: 'sales', row_id: id, kind: 'insert', tx, fields: head },
    { op_id: oid(), hlc: H('pos'), table: 'saleItems', row_id: `${id}-i0`, kind: 'insert', tx,
      fields: { saleId: id, productId: 'pl-rice', unitPriceMinor: unit, quantityMilli: 1000, totalPriceMinor: unit } },
  ];
}

test.before(async () => {
  await h.start();
  const pw = require('../src/lib/password');
  await query(
    `INSERT INTO admins (id, username, name, password_hash, role, status, created_at)
     VALUES ($1,'pl-admin','مدیر',$2,'superadmin','active',$3)`,
    [newId('adm'), await pw.hashPassword('Admin!12345'), now()],
  );
  admin = (await h.post('/api/admin/login', { username: 'pl-admin', password: 'Admin!12345' })).body.token;
  const o = await h.newUser('صاحبِ لیست', 'shop');
  const shop = await h.post('/api/shop', { name: 'دکانِ لیست' }, { token: o.accessToken });
  shopId = shop.body?.shop?.id || shop.body?.id;
  const code = (await h.get('/api/shop/staff-code', { token: o.accessToken })).body.code;
  const s = await h.newUser('شاگردِ لیست', 'shop');
  assert.ok([200, 201].includes((await h.post('/api/shop/join', { code }, { token: s.accessToken })).status));
  const inv = await h.post('/api/shop/invite', { role: 'manager' }, { token: o.accessToken });
  const m = await h.newUser('مدیرِ لیست', 'shop');
  assert.ok([200, 201].includes((await h.post('/api/shop/join', { code: inv.body.code }, { token: m.accessToken })).status));
  owner = await h.signIn(o, 'shop');
  staff = await h.signIn(s, 'shop');
  manager = await h.signIn(m, 'shop');
  //  برنج ۱۰۰۰ (عمده ۸۰۰) در v3 و راهِ قدیم؛ یک مشتریِ خرده و یک عمده که صاحب ساخته
  const r = await push3(owner, 'own', [
    { op_id: oid(), hlc: H('own'), table: 'products', row_id: 'pl-rice', kind: 'insert',
      fields: { name: 'pl-rice', salePriceMinor: 100000, wholesalePriceMinor: 80000 } },
    debtor('pl-retail', { name: 'خرده', phone: '' }),
    debtor('pl-whole', { name: 'عمده', phone: '', priceList: 'wholesale' }),
  ]);
  assert.deepEqual(why(r), ['applied', 'applied', 'applied'], JSON.stringify(r.body));
  const p = await h.post('/api/products', { id: 'pl-lrice', name: 'pl-lrice', salePrice: 1000, wholesalePrice: 800 }, { token: owner.accessToken });
  assert.ok([200, 201].includes(p.status), JSON.stringify(p.body));
});
test.after(async () => { await h.stop(); });

test('پرچمِ خاموش (پیش‌فرض): کارمند مشتری را عمده می‌کند — همان امروز', async () => {
  assert.deepEqual(why(await push3(staff, 'off', [debtor('pl-off', { name: 'خاموش', phone: '', priceList: 'wholesale' })])), ['applied']);
});

test('SR46-1، v3: کارمندِ بی تخفیفِ آزاد مشتری را عمده نمی‌کند و پس از آن هم به قیمتِ عمده نمی‌فروشد', async () => {
  await liveFlag('shop.price_cap', true, shopId);
  //  مشتریِ تازهٔ عمده — تنها یا همراهِ فاکتورِ عمده در همان دسته
  const fresh = debtor('pl-s-new', { name: 'تازه', phone: '', priceList: 'wholesale' });
  assert.deepEqual(why(await push3(staff, 'pos', [fresh])), ['discount_not_allowed']);
  const box = await boxOf(fresh.op_id);
  assert.deepEqual([box.kind, box.detail.perm, box.detail.priceList], ['perm_denied', 'sales.discount', 'wholesale']);
  assert.equal(await row3('debtors', 'pl-s-new'), null);
  const both = [debtor('pl-s-new2', { name: 'تازه', phone: '', priceList: 'wholesale' }), ...sale3('pl-s-sale', 'pl-s-new2', 80000)];
  assert.equal(why(await push3(staff, 'pos', both))[0], 'discount_not_allowed');
  assert.equal(await row3('sales', 'pl-s-sale'), null);
  //  مشتریِ خرده ⇒ عمده (ویرایش)، و فروشِ عمده به او همان تخفیفِ پنهان می‌ماند
  assert.deepEqual(why(await push3(staff, 'pos', [debtor('pl-retail', { priceList: 'wholesale' }, 'update')])), ['discount_not_allowed']);
  assert.notEqual((await row3('debtors', 'pl-retail')).data.priceList, 'wholesale');
  assert.deepEqual(why(await push3(staff, 'pos', sale3('pl-s-r', 'pl-retail', 80000))), ['discount_not_allowed', 'tx_rejected']);
  //  مشتریِ عمدهٔ صاحب: ویرایشِ بی تغییرِ لیست و فروشِ عمده آزاد؛ برگرداندن به خرده هم آزاد (تنگ‌تر)
  assert.deepEqual(why(await push3(staff, 'pos', [debtor('pl-whole', { phone: '0700', priceList: 'wholesale' }, 'update')])), ['applied']);
  assert.deepEqual(why(await push3(staff, 'pos', sale3('pl-s-w', 'pl-whole', 80000))), ['applied', 'applied']);
  assert.deepEqual(why(await push3(staff, 'pos', [debtor('pl-s-ret', { name: 'خرده', phone: '', priceList: 'retail' })])), ['applied']);
  //  مدیرِ دارای تخفیفِ آزاد («کسی که اجازه‌اش را دارد») و صاحب
  assert.deepEqual(why(await push3(manager, 'mgr', [debtor('pl-m-new', { name: 'مدیر', phone: '', priceList: 'wholesale' })])), ['applied']);
  assert.deepEqual(why(await push3(owner, 'own', [debtor('pl-retail', { priceList: 'wholesale' }, 'update')])), ['applied']);
  assert.deepEqual(why(await push3(staff, 'pos', sale3('pl-s-r2', 'pl-retail', 80000))), ['applied', 'applied']);
});

test('SR46-1، راهِ قدیم: مشتریِ عمدهٔ تازه از کارمند ⇒ ۴۰۳ و کلِ دسته برمی‌گردد؛ همان ردیفِ عمدهٔ صاحب آزاد', async () => {
  await liveFlag('shop.price_cap', true, shopId);
  const t = Date.now();
  const legacy = (who, changes) => h.api('POST', '/api/sync/push', { token: who.accessToken, headers: { 'X-App': 'shop' }, body: { deviceId: 'web-pl', changes } });
  const dRow = (id) => ({ collection: 'debtors', id, updatedAt: t, data: { name: 'عمده‌فروش', priceList: 'wholesale' } });
  const head = (id, debtorId) => ({ collection: 'sales', id, updatedAt: t,
    data: { total: 800, discount: 0, finalTotal: 800, status: 'completed', date: '2026-11-08', debtorId } });
  const item = (sid) => ({ collection: 'saleItems', id: `${sid}-i`, updatedAt: t, data: { saleId: sid, productId: 'pl-lrice', quantity: 1, unitPrice: 800 } });
  const alone = await legacy(staff, [dRow('pl-l-1')]);
  assert.equal(alone.status, 403, JSON.stringify(alone.body));
  assert.equal(alone.body.error.code, 'discount_not_allowed');
  const withSale = await legacy(staff, [dRow('pl-l-2'), head('pl-l-s2', 'pl-l-2'), item('pl-l-s2')]);
  assert.equal(withSale.status, 403, JSON.stringify(withSale.body));
  assert.equal(await one('SELECT 1 FROM debtors WHERE shop_id=$1 AND id=$2', [shopId, 'pl-l-2']), null);
  assert.equal(await one('SELECT 1 FROM sales WHERE shop_id=$1 AND id=$2', [shopId, 'pl-l-s2']), null);
  //  صاحب می‌سازد؛ کارمند همان ردیف را (بی تغییرِ لیست) با فاکتورِ عمده می‌فرستد
  assert.equal((await legacy(owner, [dRow('pl-l-3')])).status, 200);
  const ok = await legacy(staff, [dRow('pl-l-3'), head('pl-l-s3', 'pl-l-3'), item('pl-l-s3')]);
  assert.equal(ok.status, 200, JSON.stringify(ok.body));
});
