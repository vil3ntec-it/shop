'use strict';
/**
 * STF-03 — سقفِ تغییرِ قیمتِ ردیف و «تخفیفِ پنهان» در سرور (`lib/price-cap.js`، مجوزِ `sales.price` + `sales.discount`،
 * پرچمِ خاموشِ `shop.price_cap`؛ قاعده همان `lib/sale-caps.js` = `core/perm/SaleCaps.kt`).
 *
 * سنجه‌ها: پرچمِ خاموش همان امروز (سقف ذخیره نمی‌شود، قیمتِ دستی و «جمعِ نهایی» ِ کم می‌نشیند)؛ با پرچمِ روشن در Sync v3
 * کاهشِ قیمتِ ردیف و شکافِ جمعِ نهایی تخفیف‌اند (شاگردِ بی تخفیف ⇒ `discount_not_allowed`، مدیرِ سقف‌دار ⇒ تأیید)، تغییرِ
 * قیمتِ بیش از `maxPct` ⇒ `approval_required` یا `price_not_allowed`، «درخواستِ تأیید» در `priceApproval` یک‌بارمصرف؛ همهٔ
 * قلم‌ها با سرشان برمی‌گردند؛ قلمِ دیرآمده جمعِ تازه را می‌سنجد؛ قیمتِ فهرست/تخفیفِ خودکار آزاد؛ REST ۴۰۳؛ صاحب هرگز؛
 * دکانِ دیگر پرچم را نمی‌بیند.
 * تصمیمِ ۱-۱۱ (۱۴۰۵/۰۸/۱۹): قیمتِ عمده فقط برای مشتریِ لیستِ عمده (ردیفِ مشتریِ همین سرور) «فهرست» است — وگرنه تخفیفِ پنهان
 * (ادعای `priceList` ِ فاکتور پذیرفته نیست)؛ قلمِ آزاد (بی کارت یا کالای ناشناخته) مجوزِ `sales.free_line` با سقفِ قیمتِ واحد،
 * «با تأیید» (`freeLineApproval`) یا «ممنوع» دارد — v3، قلمِ دیرآمده و REST؛ پرچمِ خاموش ⇒ ذخیرهٔ مجوز `not_enforceable_yet`.
 */
process.env.SYNC_V3_ENABLED = '1';

const test = require('node:test');
const assert = require('node:assert/strict');
const h = require('./helpers');
const { one, many, query, newId, now } = require('../src/db');
const hlc = require('../src/lib/hlc');
const { resetAll } = require('../src/middleware/ratelimit');

let admin = '';
let shopId;
let shopB;
let owner;
let manager;
let staff;
let staffB;
let ownerB;
let n = 0;
const oid = () => `01JPC${String(++n).padStart(21, '0')}`;
let tick = 0;
const H = (node) => hlc.format({ wall: Date.now() + (++tick), counter: 0, node });

const liveFlag = (key, value, scope) => h.put('/api/admin/pump/live-config', { scope, key, value }, { token: admin })
  .then((r) => assert.equal(r.status, 200, JSON.stringify(r.body)));
const setRole = (role, perms, who = owner) => h.put(`/api/shop/permissions/roles/${role}`, { perms }, { token: who.accessToken });
const push3 = (who, device, ops) => h.api('POST', '/api/sync/v3/push', { token: who.accessToken, body: { device_id: device, protocol: 3, ops } });
const why = (r) => r.body.results.map((x) => (x.status === 'applied' || x.status === 'duplicate' ? x.status : x.reason || x.status));
const row3 = (table, id, shop = shopId) => one(
  `SELECT data FROM sync_v3_rows WHERE app='shop' AND account_id=$1 AND table_name=$2 AND row_id=$3`, [shop, table, id]
);
const boxOf = (opId) => one(`SELECT kind, detail FROM sync_v3_conflicts WHERE account_id=$1 AND loser_op_id=$2`, [shopId, opId]);

/** کالای v3 (Minor) */
const product3 = (id, fields) => ({ op_id: oid(), hlc: H('own'), table: 'products', row_id: id, kind: 'insert', fields: { name: id, ...fields } });

/**
 * فاکتورِ v3 با قلم‌ها در یک tx. `lines` = [[productId, unitMinor, qtyMilli, extra?]]؛ جمع از قلم‌ها، مگر `head` بگوید.
 */
function sale3(id, lines, head = {}, slips = {}) {
  const tx = `tx.${oid()}`;
  const items = lines.map(([pid, unit, qty, extra = {}], i) => ({
    op_id: oid(), hlc: H('pos'), table: 'saleItems', row_id: `${id}-i${i}`, kind: 'insert', tx,
    fields: { saleId: id, productId: pid, unitPriceMinor: unit, quantityMilli: qty, totalPriceMinor: Math.round(unit * qty / 1000), ...extra },
  }));
  const total = items.reduce((a, x) => a + x.fields.totalPriceMinor, 0);
  const fields = { totalMinor: total, discountMinor: 0, finalTotalMinor: total, paidAmountMinor: total, remainingMinor: 0,
    debtGivenMinor: 0, paymentMethod: 'cash', status: 'completed', date: '2026-11-08', ...head };
  return [{ op_id: oid(), hlc: H('pos'), table: 'sales', row_id: id, kind: 'insert', tx, fields, ...slips }, ...items];
}

async function approved(perm, doc, value, who = staff) {
  resetAll();
  const r = await h.post('/api/shop/approval-requests', { key: `pc-${++n}-${Date.now().toString(36)}`, perm, doc, ...value, reason: 'مشتریِ همیشگی' },
    { token: who.accessToken });
  assert.equal(r.status, 201, JSON.stringify(r.body));
  resetAll();
  const a = await h.post(`/api/shop/approval-requests/${r.body.request.id}/approve`, {}, { token: owner.accessToken });
  assert.equal(a.status, 200, JSON.stringify(a.body));
  return r.body.request.id;
}

test.before(async () => {
  await h.start();
  const pw = require('../src/lib/password');
  await query(
    `INSERT INTO admins (id, username, name, password_hash, role, status, created_at)
     VALUES ($1,'pc-admin','مدیر',$2,'superadmin','active',$3)`,
    [newId('adm'), await pw.hashPassword('Admin!12345'), now()],
  );
  admin = (await h.post('/api/admin/login', { username: 'pc-admin', password: 'Admin!12345' })).body.token;
  async function shopWithStaff(label, withManager) {
    const o = await h.newUser(`صاحبِ ${label}`, 'shop');
    const shop = await h.post('/api/shop', { name: `دکانِ ${label}` }, { token: o.accessToken });
    const id = shop.body?.shop?.id || shop.body?.id;
    const code = (await h.get('/api/shop/staff-code', { token: o.accessToken })).body.code;
    const s = await h.newUser(`شاگردِ ${label}`, 'shop');
    assert.ok([200, 201].includes((await h.post('/api/shop/join', { code }, { token: s.accessToken })).status));
    let m = null;
    if (withManager) {
      const inv = await h.post('/api/shop/invite', { role: 'manager' }, { token: o.accessToken });
      m = await h.newUser(`مدیرِ ${label}`, 'shop');
      assert.ok([200, 201].includes((await h.post('/api/shop/join', { code: inv.body.code }, { token: m.accessToken })).status));
    }
    return { id, owner: await h.signIn(o, 'shop'), staff: await h.signIn(s, 'shop'), manager: m ? await h.signIn(m, 'shop') : null };
  }
  ({ id: shopId, owner, staff, manager } = await shopWithStaff('قیمت', true));
  ({ id: shopB, owner: ownerB, staff: staffB } = await shopWithStaff('قیمتِ ب', false));
  //  کالاها: برنج ۱۰۰۰ (عمده ۸۰۰)، روغن ۵۰۰ (همان دکانِ «ب» هم)
  for (const [who, dev] of [[owner, 'own'], [ownerB, 'ownB']]) {
    const r = await push3(who, dev, [
      product3('pc-rice', { salePriceMinor: 100000, wholesalePriceMinor: 80000 }),
      product3('pc-oil', { salePriceMinor: 50000 }),
    ]);
    assert.deepEqual(why(r), ['applied', 'applied'], JSON.stringify(r.body));
  }
  for (const [who, id, price] of [[owner, 'pc-rice', 1000], [owner, 'pc-oil', 500]]) {
    const r = await h.post('/api/products', { id, name: id, salePrice: price, wholesalePrice: 0 }, { token: who.accessToken });
    assert.ok([200, 201].includes(r.status), JSON.stringify(r.body));
  }
});
test.after(async () => { await h.stop(); });

test('پرچمِ خاموش (پیش‌فرض): سقفِ قیمت ذخیره نمی‌شود و قیمتِ دستی، شکافِ جمعِ نهایی و REST همان امروز', async () => {
  const set = await setRole('staff', { 'sales.price': { caps: { maxPct: 5 } } });
  assert.equal(set.status, 400, JSON.stringify(set.body));
  assert.equal(set.body.error.code, 'not_enforceable_yet');
  const page = await h.get('/api/shop/permissions', { token: owner.accessToken });
  assert.deepEqual([page.body.shop.catalog.find((c) => c.key === 'sales.price').edit], ['none']);
  assert.ok(!page.body.policy.editable.includes('sales.price'));
  //  لایهٔ ذخیره‌شدهٔ پیشین هم بی‌اثر
  await query(`INSERT INTO shop_role_perms (shop_id, role, perms, updated_by, updated_at) VALUES ($1,'staff',$2,'',$3)`,
    [shopId, JSON.stringify({ 'sales.price': { mode: 'deny' } }), now()]);
  assert.deepEqual(why(await push3(staff, 'off', sale3('pc-off-1', [['pc-rice', 50000, 1000]]))), ['applied', 'applied']);
  assert.deepEqual(why(await push3(staff, 'off', sale3('pc-off-2', [['pc-rice', 100000, 1000]], { finalTotalMinor: 60000 }))), ['applied', 'applied']);
  const rest = await h.post('/api/sales/full', {
    sale: { id: 'pc-off-r', total: 500, discount: 0, finalTotal: 500, status: 'completed' },
    items: [{ id: 'pc-off-r-i', productId: 'pc-rice', quantity: 1, unitPrice: 500 }],
  }, { token: staff.accessToken });
  assert.ok([200, 201].includes(rest.status), JSON.stringify(rest.body));
  assert.equal((await many(`SELECT 1 FROM sync_v3_conflicts WHERE account_id=$1`, [shopId])).length, 0);
  await query(`DELETE FROM shop_role_perms WHERE shop_id=$1`, [shopId]);
});

test('پرچمِ روشن، v3: کاهشِ قیمتِ ردیف و شکافِ جمعِ نهایی «تخفیفِ پنهان»اند — شاگردِ بی تخفیف رد، همهٔ قلم‌ها با سر', async () => {
  await liveFlag('shop.price_cap', true, shopId);
  const page = await h.get('/api/shop/permissions', { token: owner.accessToken });
  assert.equal(page.body.shop.catalog.find((c) => c.key === 'sales.price').edit, 'restrict');
  assert.ok(page.body.policy.editable.includes('sales.price'));

  //  همان قیمتِ فهرست، قیمتِ بالاتر (بی لایه = همان امروز) و تخفیفِ خودکار آزادند
  assert.deepEqual(why(await push3(staff, 'p3', sale3('pc-ok', [['pc-rice', 100049, 2000], ['pc-oil', 80000, 1000]]))), ['applied', 'applied', 'applied']);
  //  تصمیمِ ۱-۱۱ (۳): فاکتورِ بی مشتریِ عمده به قیمتِ عمده = کاهش از خرده (تخفیفِ پنهان) — پیش از تصمیم «آزاد» بود
  assert.deepEqual(why(await push3(staff, 'p3', sale3('pc-whole', [['pc-rice', 80000, 1000]]))), ['discount_not_allowed', 'tx_rejected']);
  assert.deepEqual(why(await push3(staff, 'p3', sale3('pc-promo', [['pc-rice', 90000, 1000, { listPriceMinor: 100000, promo: [{ id: 'r1', rev: 1, name: 'ده', discountMinor: 10000 }] }]]))), ['applied', 'applied']);

  //  میانِ عمده و خرده، بی مشتریِ عمده ⇒ از خرده پایین (تخفیفِ پنهان؛ تصمیمِ ۱-۱۱ (۳)) — با مشتریِ عمده: آزمونِ «۱-۱۱ (۳)» پایین
  assert.deepEqual(why(await push3(staff, 'p3', sale3('pc-mid', [['pc-rice', 90000, 1000]]))), ['discount_not_allowed', 'tx_rejected']);
  //  مصرفِ امتیازِ مشتری (PRM-13) قیمتِ ردیف‌ها را پایین می‌آورد؛ ردیفِ `loyalty_spend` ِ همان ثبت آن را برمی‌گرداند
  const spent = sale3('pc-loyal', [['pc-oil', 45000, 1000], ['pc-oil', 45000, 1000]]);
  spent.push({ op_id: oid(), hlc: H('pos'), table: 'auditLog', row_id: 'pc-loyal-lp-s', kind: 'insert', tx: spent[0].tx,
    fields: { type: 'loyalty_spend', refId: 'pc-loyal', amountMinor: 10000, points: 100, date: '2026-11-08' } });
  assert.deepEqual(why(await push3(staff, 'p3', spent)), ['applied', 'applied', 'applied', 'applied']);
  const unspent = sale3('pc-unloyal', [['pc-oil', 45000, 1000], ['pc-oil', 45000, 1000]]);
  assert.deepEqual(why(await push3(staff, 'p3', unspent)), ['discount_not_allowed', 'tx_rejected', 'tx_rejected']);
  const low = sale3('pc-low', [['pc-oil', 45000, 1000], ['pc-rice', 100000, 1000]]);
  const r = await push3(staff, 'p3', low);
  assert.deepEqual(why(r), ['discount_not_allowed', 'tx_rejected', 'tx_rejected']);
  assert.equal(await row3('sales', 'pc-low'), null);
  assert.equal(await row3('saleItems', 'pc-low-i0'), null, 'قلم‌ها با سرشان برمی‌گردند');
  const box = await boxOf(low[0].op_id);
  assert.equal(box.kind, 'perm_denied');
  assert.deepEqual([box.detail.perm, box.detail.amount], ['sales.discount', 5000]);

  const gap = sale3('pc-gap', [['pc-rice', 100000, 1000]], { finalTotalMinor: 90000 });
  assert.deepEqual(why(await push3(staff, 'p3', gap)), ['discount_not_allowed', 'tx_rejected']);
  //  گردکردنِ نقدیِ تا ۵ افغانی شکاف نیست
  assert.deepEqual(why(await push3(staff, 'p3', sale3('pc-round', [['pc-rice', 100300, 1000]], { totalMinor: 100300, finalTotalMinor: 100000, roundingMinor: -300 }))), ['applied', 'applied']);
  //  صاحب هرگز
  assert.deepEqual(why(await push3(owner, 'o3', sale3('pc-own', [['pc-rice', 10000, 1000]]))), ['applied', 'applied']);
});

test('پرچمِ روشن، v3: مدیرِ سقف‌دار — تخفیفِ پنهانِ بالای سقف با «درخواستِ تأیید» ِ همان پایه می‌نشیند', async () => {
  await liveFlag('shop.approval_requests', true, shopId);
  assert.equal((await setRole('manager', { 'sales.discount': { caps: { maxPct: 5 } } })).status, 200);
  const ops = sale3('pc-mgr', [['pc-oil', 45000, 1000]]);
  assert.deepEqual(why(await push3(manager, 'm3', ops)), ['approval_required', 'tx_rejected']);
  const box = await boxOf(ops[0].op_id);
  //  پایه = جمع (۴۵٬۰۰۰) + کاهش (۵٬۰۰۰)؛ درصد با بخششِ نیم افغانی (۴٬۹۵۰ ÷ ۵۰٬۰۰۰)
  assert.deepEqual([box.detail.perm, box.detail.amount, box.detail.pct_bp, box.detail.cap], ['sales.discount', 5000, 990, 'maxPct']);
  const reqId = await approved('sales.discount', 'sales/pc-mgr', { amount: 5000, pctBp: 990 }, manager);
  const again = sale3('pc-mgr', [['pc-oil', 45000, 1000]], {}, { approval: { v: 1, request: reqId } });
  assert.deepEqual(why(await push3(manager, 'm3', again)), ['applied', 'applied']);
  assert.equal((await row3('saleItems', 'pc-mgr-i0')).data.unitPriceMinor, 45000);
  assert.equal((await setRole('manager', {})).status, 200);
});

test('پرچمِ روشن، v3: تغییرِ قیمتِ بیش از maxPct ⇒ approval_required؛ priceApproval یک‌بارمصرف؛ «ممنوع» ⇒ price_not_allowed', async () => {
  assert.equal((await setRole('staff', { 'sales.price': { caps: { maxPct: 5 } } })).status, 200);
  //  ۵٪ بالا ⇒ زیرِ سقف؛ ۲۵٪ بالا ⇒ تأیید
  assert.deepEqual(why(await push3(staff, 'pp', sale3('pc-up5', [['pc-rice', 105000, 1000]]))), ['applied', 'applied']);
  const up = sale3('pc-up', [['pc-rice', 125000, 1000]]);
  assert.deepEqual(why(await push3(staff, 'pp', up)), ['approval_required', 'tx_rejected']);
  const box = await boxOf(up[0].op_id);
  assert.deepEqual([box.detail.perm, box.detail.pct_bp, box.detail.cap, box.detail.limit], ['sales.price', 2500, 'maxPct', 5]);

  const reqId = await approved('sales.price', 'sales/pc-up', { pctBp: 2500 });
  const small = await approved('sales.price', 'sales/pc-up2', { pctBp: 1000 });
  assert.deepEqual(why(await push3(staff, 'pp', sale3('pc-up2', [['pc-rice', 125000, 1000]], {}, { priceApproval: { v: 1, request: small } }))), ['approval_too_small', 'tx_rejected']);
  const ok = sale3('pc-up', [['pc-rice', 125000, 1000]], {}, { priceApproval: { v: 1, request: reqId } });
  assert.deepEqual(why(await push3(staff, 'pp', ok)), ['applied', 'applied']);
  assert.deepEqual(why(await push3(staff, 'pp', ok)), ['duplicate', 'duplicate']);
  assert.deepEqual(why(await push3(staff, 'pp', sale3('pc-up3', [['pc-rice', 125000, 1000]], {}, { priceApproval: { v: 1, request: reqId } }))), ['approval_replayed', 'tx_rejected']);
  const used = await one(`SELECT perm, doc, pct_bp FROM shop_approvals WHERE shop_id=$1 AND nonce=$2`, [shopId, `req_${reqId}`]);
  assert.deepEqual({ ...used, pct_bp: Number(used.pct_bp) }, { perm: 'sales.price', doc: 'sales/pc-up', pct_bp: 2500 });

  assert.equal((await setRole('staff', { 'sales.price': { mode: 'deny' } })).status, 200);
  const denied = sale3('pc-deny', [['pc-oil', 55000, 1000]]);
  assert.deepEqual(why(await push3(staff, 'pp', denied)), ['price_not_allowed', 'tx_rejected']);
  assert.equal((await boxOf(denied[0].op_id)).kind, 'perm_denied');
  assert.deepEqual(why(await push3(staff, 'pp', sale3('pc-list', [['pc-oil', 50000, 1000]]))), ['applied', 'applied']);
  assert.equal((await setRole('staff', {})).status, 200);
});

test('تصمیمِ ۱-۱۱ (۳)، v3: قیمتِ عمده فقط برای مشتریِ لیستِ عمدهٔ همین سرور «فهرست» است؛ ادعای فاکتور نه', async () => {
  const debtor = (id, priceList) => ({ op_id: oid(), hlc: H('own'), table: 'debtors', row_id: id, kind: 'insert',
    fields: { name: id, phone: '', ...(priceList ? { priceList } : {}) } });
  assert.deepEqual(why(await push3(owner, 'own', [debtor('pc-wcust', 'wholesale'), debtor('pc-rcust', null)])), ['applied', 'applied']);
  //  مشتریِ عمده: قیمتِ عمده و «بالاتر از عمده» (تغییرِ قیمت، بی لایه آزاد) می‌نشینند
  assert.deepEqual(why(await push3(staff, 'w3', sale3('pc-w-ok', [['pc-rice', 80000, 1000]], { debtorId: 'pc-wcust' }))), ['applied', 'applied']);
  assert.deepEqual(why(await push3(staff, 'w3', sale3('pc-w-mid', [['pc-rice', 90000, 1000]], { debtorId: 'pc-wcust' }))), ['applied', 'applied']);
  //  مشتریِ خرده، مشتریِ ناشناخته، یا فقط ادعای «لیستِ عمده» در سرِ فاکتور ⇒ تخفیفِ پنهان
  for (const [id, head] of [['pc-w-r', { debtorId: 'pc-rcust' }], ['pc-w-x', { debtorId: 'pc-nobody' }], ['pc-w-c', { priceList: 'wholesale' }]]) {
    const ops = sale3(id, [['pc-rice', 80000, 1000]], head);
    assert.deepEqual(why(await push3(staff, 'w3', ops)), ['discount_not_allowed', 'tx_rejected'], id);
    assert.deepEqual([(await boxOf(ops[0].op_id)).detail.perm, (await boxOf(ops[0].op_id)).detail.amount], ['sales.discount', 20000], id);
  }
  //  مدیرِ با تخفیفِ آزاد («کسی که اجازه‌اش را دارد») همان لیست را برای مشتریِ خرده برمی‌گزیند
  assert.deepEqual(why(await push3(manager, 'w3m', sale3('pc-w-m', [['pc-rice', 80000, 1000]], { debtorId: 'pc-rcust' }))), ['applied', 'applied']);
  //  راهِ قدیم: مشتریِ عمدهٔ همین دسته یا ردیفِ نشستهٔ راهِ قدیم (کالای راهِ قدیم با عمدهٔ ۸۰۰)
  const p = await h.post('/api/products', { id: 'pc-lrice', name: 'pc-lrice', salePrice: 1000, wholesalePrice: 800 }, { token: owner.accessToken });
  assert.ok([200, 201].includes(p.status), JSON.stringify(p.body));
  const t = Date.now();
  const legacy = (changes) => h.api('POST', '/api/sync/push', { token: staff.accessToken, headers: { 'X-App': 'shop' }, body: { deviceId: 'web-w', changes } });
  const head = (id, debtorId) => ({ collection: 'sales', id, updatedAt: t,
    data: { total: 800, discount: 0, finalTotal: 800, status: 'completed', date: '2026-11-08', ...(debtorId ? { debtorId } : {}) } });
  const item = (sid) => ({ collection: 'saleItems', id: `${sid}-i`, updatedAt: t, data: { saleId: sid, productId: 'pc-lrice', quantity: 1, unitPrice: 800 } });
  const lw = await legacy([{ collection: 'debtors', id: 'pc-lw', updatedAt: t, data: { name: 'عمده‌فروش', priceList: 'wholesale' } }, head('pc-l-w', 'pc-lw'), item('pc-l-w')]);
  assert.equal(lw.status, 200, JSON.stringify(lw.body));
  const lr = await legacy([head('pc-l-r'), item('pc-l-r')]);
  assert.equal(lr.status, 403, JSON.stringify(lr.body));
  assert.equal(lr.body.error.code, 'discount_not_allowed');
  const again = await legacy([head('pc-l-w2', 'pc-lw'), item('pc-l-w2')]);
  assert.equal(again.status, 200, JSON.stringify(again.body));
});

test('تصمیمِ ۱-۱۱ (۲): قلمِ آزاد مجوزِ sales.free_line دارد — سقفِ قیمتِ واحد، «با تأیید» (freeLineApproval)، «ممنوع»؛ دکانِ بی پرچم نه', async () => {
  //  دکانِ «ب» (پرچم خاموش): مجوز ذخیره نمی‌شود و قلمِ آزاد همان امروز
  const off = await setRole('staff', { 'sales.free_line': { mode: 'deny' } }, ownerB);
  assert.deepEqual([off.status, off.body.error?.code], [400, 'not_enforceable_yet']);
  assert.deepEqual(why(await push3(staffB, 'fb', sale3('pc-f-b', [['', 999900, 1000, { name: 'آزاد' }]]))), ['applied', 'applied']);
  //  همین دکان (پرچم روشن): بی لایه همان امروز
  const page = await h.get('/api/shop/permissions', { token: owner.accessToken });
  assert.equal(page.body.shop.catalog.find((c) => c.key === 'sales.free_line').edit, 'restrict');
  assert.deepEqual(why(await push3(staff, 'f3', sale3('pc-f-0', [['pc-rice', 100000, 1000], ['', 700000, 2000, { name: 'آزاد' }]]))), ['applied', 'applied', 'applied']);
  //  سقفِ قیمتِ واحد ۵۰ افغانی: تا سقف آزاد، بیشتر ⇒ تأیید (مبلغ = بیشترین قیمتِ واحد، نه جمع)
  assert.equal((await setRole('staff', { 'sales.free_line': { caps: { maxAmount: 5000 } } })).status, 200);
  assert.deepEqual(why(await push3(staff, 'f3', sale3('pc-f-1', [['', 5000, 9000, { name: 'آزاد' }]]))), ['applied', 'applied']);
  const over = sale3('pc-f-2', [['', 3000, 1000, { name: 'آ' }], ['', 6000, 1000, { name: 'ب' }]]);
  assert.deepEqual(why(await push3(staff, 'f3', over)), ['approval_required', 'tx_rejected', 'tx_rejected']);
  const box = await boxOf(over[0].op_id);
  assert.deepEqual([box.detail.perm, box.detail.amount, box.detail.cap, box.detail.limit], ['sales.free_line', 6000, 'maxAmount', 5000]);
  //  کالای ناشناختهٔ سرور هم قلمِ آزاد است
  assert.deepEqual(why(await push3(staff, 'f3', sale3('pc-f-g', [['pc-ghost', 9000, 1000]]))), ['approval_required', 'tx_rejected']);
  const reqId = await approved('sales.free_line', 'sales/pc-f-2', { amount: 6000 });
  const ok = sale3('pc-f-2', [['', 3000, 1000, { name: 'آ' }], ['', 6000, 1000, { name: 'ب' }]], {}, { freeLineApproval: { v: 1, request: reqId } });
  assert.deepEqual(why(await push3(staff, 'f3', ok)), ['applied', 'applied', 'applied']);
  const used = await one(`SELECT perm, doc, amount FROM shop_approvals WHERE shop_id=$1 AND nonce=$2`, [shopId, `req_${reqId}`]);
  assert.deepEqual({ ...used, amount: Number(used.amount) }, { perm: 'sales.free_line', doc: 'sales/pc-f-2', amount: 6000 });
  //  برگهٔ قیمت جای برگهٔ قلمِ آزاد را نمی‌گیرد (قیمت هم سقف‌دار تا درخواستش معنا داشته باشد)
  assert.equal((await setRole('staff', { 'sales.free_line': { caps: { maxAmount: 5000 } }, 'sales.price': { caps: { maxPct: 5 } } })).status, 200);
  const priceReq = await approved('sales.price', 'sales/pc-f-3', { pctBp: 1000 });
  assert.deepEqual(why(await push3(staff, 'f3', sale3('pc-f-3', [['', 6000, 1000]], {}, { priceApproval: { v: 1, request: priceReq } }))), ['approval_required', 'tx_rejected']);
  //  «ممنوع»: حتی رایگان؛ قلمِ دیرآمده هم؛ صاحب هرگز
  assert.equal((await setRole('staff', { 'sales.free_line': { mode: 'deny' } })).status, 200);
  const denied = sale3('pc-f-d', [['', 0, 1000, { name: 'هدیه' }]]);
  assert.deepEqual(why(await push3(staff, 'f3', denied)), ['free_line_not_allowed', 'tx_rejected']);
  assert.equal((await boxOf(denied[0].op_id)).kind, 'perm_denied');
  const [h0, l0] = sale3('pc-f-late', [['pc-oil', 50000, 1000]]);
  delete h0.tx; delete l0.tx;
  assert.deepEqual(why(await push3(staff, 'fl', [h0, l0])), ['applied', 'applied']);
  const late = { ...l0, op_id: oid(), hlc: H('pos'), row_id: 'pc-f-late-i9', fields: { ...l0.fields, productId: '', name: 'آزاد', unitPriceMinor: 1000 } };
  assert.deepEqual(why(await push3(staff, 'fl', [late])), ['free_line_not_allowed']);
  assert.equal(await row3('saleItems', 'pc-f-late-i9'), null);
  assert.deepEqual(why(await push3(owner, 'fo', sale3('pc-f-own', [['', 999900, 1000]]))), ['applied', 'applied']);
  //  REST/راهِ قدیم
  const rest = await h.post('/api/sales/full', {
    sale: { id: 'pc-f-r', total: 10, discount: 0, finalTotal: 10, status: 'completed', date: '2026-11-08' },
    items: [{ id: 'pc-f-r-i', productId: '', name: 'آزاد', quantity: 1, unitPrice: 10 }],
  }, { token: staff.accessToken });
  assert.equal(rest.status, 403, JSON.stringify(rest.body));
  assert.equal(rest.body.error.code, 'free_line_not_allowed');
  assert.equal((await setRole('staff', {})).status, 200);
});

test('پرچمِ روشن، v3: قلمِ دیرآمده (سر در دستهٔ پیشین) جمعِ تازهٔ همان فاکتور را می‌سنجد', async () => {
  const [head, line] = sale3('pc-late', [['pc-rice', 100000, 1000]]);
  delete head.tx; delete line.tx;
  assert.deepEqual(why(await push3(staff, 'pl', [head])), ['applied']);
  const cheap = { ...line, op_id: oid(), hlc: H('pos'), row_id: 'pc-late-i9', fields: { ...line.fields, unitPriceMinor: 70000 } };
  assert.deepEqual(why(await push3(staff, 'pl', [cheap])), ['discount_not_allowed']);
  assert.equal(await row3('saleItems', 'pc-late-i9'), null);
  //  همان قیمتِ فهرست آزاد
  assert.deepEqual(why(await push3(staff, 'pl', [line])), ['applied']);
});

test('پرچمِ روشن، REST/راهِ قدیم: کاهشِ قیمتِ ردیف ⇒ ۴۰۳ discount_not_allowed، تغییرِ بالای سقف ⇒ approval_required؛ فهرست آزاد', async () => {
  const post = (id, unitPrice, extra = {}) => h.post('/api/sales/full', {
    sale: { id, total: unitPrice, discount: 0, finalTotal: unitPrice, status: 'completed', date: '2026-11-08', ...extra },
    items: [{ id: `${id}-i`, productId: 'pc-rice', quantity: 1, unitPrice }],
  }, { token: staff.accessToken });
  const low = await post('pc-r-low', 900);
  assert.equal(low.status, 403, JSON.stringify(low.body));
  assert.equal(low.body.error.code, 'discount_not_allowed');
  assert.equal(await one(`SELECT 1 FROM sales WHERE shop_id=$1 AND id='pc-r-low'`, [shopId]), null);
  const gap = await post('pc-r-gap', 1000, { finalTotal: 800 });
  assert.equal(gap.body.error?.code, 'discount_not_allowed');
  assert.equal((await setRole('staff', { 'sales.price': { caps: { maxPct: 5 } } })).status, 200);
  const up = await post('pc-r-up', 1250);
  assert.equal(up.status, 403);
  assert.equal(up.body.error.code, 'approval_required');
  const ok = await post('pc-r-ok', 1000);
  assert.ok([200, 201].includes(ok.status), JSON.stringify(ok.body));
  //  راهِ قدیم کلِ ردیف را جایگزین می‌کند: سرِ نشسته با «جمعِ نهایی» ِ کمتر ⇒ تخفیفِ پنهان؛ همان ردیفِ بی‌تغییر آزاد
  const head = { total: 1000, discount: 0, finalTotal: 1000, status: 'completed', date: '2026-11-08' };
  const same = await h.put('/api/sales/pc-r-ok', { ...head, note: 'یادداشت', updatedAt: Date.now() + 1000 }, { token: staff.accessToken });
  assert.equal(same.status, 200, JSON.stringify(same.body));
  const cut = await h.put('/api/sales/pc-r-ok', { ...head, finalTotal: 700, updatedAt: Date.now() + 2000 }, { token: staff.accessToken });
  assert.equal(cut.status, 403, JSON.stringify(cut.body));
  assert.equal(cut.body.error.code, 'discount_not_allowed');
  assert.equal((await one(`SELECT data FROM sales WHERE shop_id=$1 AND id='pc-r-ok'`, [shopId])).data.finalTotal, 1000);
  assert.equal((await setRole('staff', {})).status, 200);
});

test('«پرسیدن از مدیر»: /config می‌گوید سرور Sync v3 دارد (برنامه دکمهٔ کارِ فقط-v3 را بی آن نشان نمی‌دهد)', async () => {
  const r = await h.get('/api/shop/approval-requests/config', { token: staff.accessToken });
  assert.equal(r.status, 200, JSON.stringify(r.body));
  assert.equal(r.body.v3, true);
  assert.equal(r.body.enabled, true);
});

test('پرچمِ یک دکان دکانِ دیگر را نمی‌گیرد', async () => {
  assert.deepEqual(why(await push3(staffB, 'b3', sale3('pc-b', [['pc-rice', 10000, 1000]]))), ['applied', 'applied']);
  assert.equal((await row3('sales', 'pc-b', shopB)).data.totalMinor, 10000);
});
