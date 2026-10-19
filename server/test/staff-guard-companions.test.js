'use strict';
/**
 * نگهبانِ تأیید (STF-02/03): ردِ یک سندِ پولی «کلِ سند» است، نه فقط سرش — v1 و v3.
 *
 *   (a) فروشِ نسیهٔ بیش از سقف ⇒ ردیفِ «برد» (give) هم برنمی‌نشیند
 *   (b) لغوِ نیازمندِ تأیید ⇒ «رسیدِ» لغو (receive) هم نه
 *   (c) برگشتِ بیش از سقف ⇒ debtSettled ِ فاکتور، returnedQty ِ قلم و رسیدش هم نه
 *   (d) اصلاحِ انبارِ برگشت (#318، ضایعات/بی رسید) هم همراهِ برگشت است
 *   (e) سند و همراه‌ها در دو دسته (بُرشِ ۲۰۰تایی v1، یا tx ِ بزرگ در v3) ⇒ همراهِ دستهٔ بعد هم رد
 *   (f) tx ِ بیش از ۵۰ op (برنامهٔ تازه همیشه tx می‌گذارد) ⇒ کلِ ثبت با هم
 *   سقفِ برگشت روی «جمعِ سند» (docId یا tx یا همان فاکتور و همان لحظه) است، نه هر ردیف؛
 *   برگهٔ برگشت `returns/<docId>`.
 *
 * ردیفِ برنامهٔ قدیمی که refId ندارد: با «همان ثبت» (createdAt، قرض‌دار) شناخته می‌شود؛
 * اگر نشود و پس از سندِ ردشده آمده، «deferred» است — نه اعمال و نه گم.
 */
const test = require('node:test');
const assert = require('node:assert/strict');
const crypto = require('node:crypto');
const h = require('./helpers');
const { one, many } = require('../src/db');
const hlc = require('../src/lib/hlc');
const syncV1 = require('../src/lib/sync-v1');
const slipCore = require('../src/lib/approval-core');

let shopId;
let owner;
let manager;
let staff;
let ownerKey;
let n = 0;
const oid = () => `01JGC${String(++n).padStart(21, '0')}`;
let tick = 0;
const H = (node) => hlc.format({ wall: Date.now() + (++tick), counter: 0, node });

const push1 = (who, device, ops) => h.api('POST', '/api/sync/v1/push', {
  token: who.accessToken, body: { device_id: device, schema_version: 1, ops },
});
const push3 = (who, device, ops) => h.api('POST', '/api/sync/v3/push', {
  token: who.accessToken, body: { device_id: device, protocol: 3, ops },
});
const setRole = (role, perms) => h.put(`/api/shop/permissions/roles/${role}`, { perms }, { token: owner.accessToken });
const row1 = (table, id) => one(
  `SELECT * FROM sync_rows WHERE app='shop' AND account_id=$1 AND table_name=$2 AND row_id=$3`, [shopId, table, id]
);
const row3 = (table, id) => one(
  `SELECT * FROM sync_v3_rows WHERE app='shop' AND account_id=$1 AND table_name=$2 AND row_id=$3`, [shopId, table, id]
);
const boxes = (opId) => many('SELECT kind, detail FROM sync_v3_conflicts WHERE account_id=$1 AND loser_op_id=$2', [shopId, opId]);
const why = (r) => r.body.results.map((x) => (x.status === 'applied' || x.status === 'duplicate' ? x.status : x.reason || x.status));

/** یک «ثبت» ِ برنامهٔ v1: همهٔ opها یک ts (همان `LedgerDiff.diff(now)`) */
function save(at = Date.now() + (++tick) * 1000) {
  return {
    at,
    op: (table, rowId, type, fields) => ({ op_id: oid(), table, row_id: rowId, type, ts: at, fields }),
  };
}

async function approverKey(who) {
  const { privateKey, publicKey } = crypto.generateKeyPairSync('ec', { namedCurve: 'prime256v1' });
  const spki = publicKey.export({ format: 'der', type: 'spki' }).toString('base64');
  const r = await h.post('/api/shop/approver-keys', { public_key: spki, label: 'صندوق', device_id: 'pos-1' }, { token: who.accessToken });
  return { priv: privateKey, keyId: r.body?.key?.id, userId: who.user.id };
}
function slip(signer, { doc, perm, pct_bp = null, amount = null }) {
  const iat = Date.now() - 1000;
  const s = { v: 1, shop: shopId, doc, perm, pct_bp, amount, by: signer.userId, key: signer.keyId,
    iat, exp: iat + 10 * 60_000, nonce: crypto.randomBytes(12).toString('base64url'), reason: 'تأییدِ صاحب' };
  s.sig = crypto.sign('sha256', Buffer.from(slipCore.payload(s), 'utf8'), { key: signer.priv, dsaEncoding: 'der' }).toString('base64');
  return s;
}

/** فروشِ نسیهٔ v1 با قلم، حرکت، «برد» و سطرِ دفترِ کار؛ `legacy` = برنامهٔ قدیمی (برد بی refId/kind) */
function v1CreditSale(sv, id, { total = 100, discount = 30, debtor = 'd-1', legacy = false } = {}) {
  const final = total - discount;
  const give = { debtorId: debtor, type: 'give', amount: final, date: '2026-10-19', notes: 'فروش نسیه', createdAt: sv.at };
  if (!legacy) Object.assign(give, { refId: id, kind: 'sale_credit' });
  return [
    sv.op('sales', id, 'insert', { total, discount, finalTotal: final, paymentMethod: 'credit', debtorId: debtor,
      paidAmount: 0, remaining: final, debtGiven: final, debtSettled: 0, status: 'completed', createdAt: sv.at }),
    sv.op('saleItems', `${id}-i1`, 'insert', { saleId: id, productId: 'p-gc', quantity: 3, unitPrice: total / 3, totalPrice: total, returnedQty: 0 }),
    sv.op('transactions', `${id}-give`, 'insert', give),
    sv.op('stockMovements', `${id}-m1`, 'insert', { productId: 'p-gc', type: 'sale', qty: -3, refId: id, createdAt: sv.at }),
    sv.op('auditLog', `${id}-a1`, 'insert', { type: 'sale', refId: id, notes: 'ثبت فروش', createdAt: sv.at }),
  ];
}

test.before(async () => {
  process.env.SYNC_V3_ENABLED = '1';
  require('../src/config').syncV3.enabled = true;
  await h.start();
  await syncV1.syncSchemaTable();
  const o = await h.newUser('صاحبِ دکانِ همراه', 'shop');
  const shop = await h.post('/api/shop', { name: 'دکانِ همراه' }, { token: o.accessToken });
  shopId = shop.body?.shop?.id || shop.body?.id;
  const code = (await h.get('/api/shop/staff-code', { token: o.accessToken })).body.code;
  const s = await h.newUser('شاگردِ همراه', 'shop');
  assert.ok([200, 201].includes((await h.post('/api/shop/join', { code }, { token: s.accessToken })).status));
  const inv = await h.post('/api/shop/invite', { role: 'manager' }, { token: o.accessToken });
  const m = await h.newUser('مدیرِ همراه', 'shop');
  assert.ok([200, 201].includes((await h.post('/api/shop/join', { code: inv.body.code }, { token: m.accessToken })).status));
  owner = await h.signIn(o, 'shop');
  manager = await h.signIn(m, 'shop');
  staff = await h.signIn(s, 'shop');
  ownerKey = await approverKey(owner);
  assert.ok(ownerKey.keyId);
  assert.equal((await setRole('manager', { 'sales.discount': { caps: { maxPct: 10 } }, 'sales.cancel': { mode: 'approve' } })).status, 200);
  assert.equal((await setRole('staff', { 'returns.create': { caps: { maxAmount: 50000 } } })).status, 200);
});
test.after(async () => { await h.stop(); });

/* ------------------------------------------------------------ Sync v1 */

test('v1 (a): فروشِ نسیهٔ بیش از سقف ⇒ «برد»ِ قرض‌دار هم نمی‌نشیند (برنامهٔ تازه با refId، قدیمی با همان ثبت)', async () => {
  const sv = save();
  const ops = v1CreditSale(sv, 's-a-new');
  const r = await push1(manager, 'm-a', ops);
  assert.equal(r.status, 200, JSON.stringify(r.body));
  assert.deepEqual(why(r), ['approval_required', 'tx_rejected', 'tx_rejected', 'tx_rejected', 'tx_rejected']);
  assert.equal(await row1('transactions', 's-a-new-give'), null);

  const old = save();
  const legacy = v1CreditSale(old, 's-a-old', { legacy: true });
  //  پرداختِ دستیِ دیگری در همان دسته (قرض‌دارِ دیگر، لحظهٔ دیگر) می‌نشیند
  const other = old.op('transactions', 't-a-manual', 'insert',
    { debtorId: 'd-2', type: 'receive', amount: 5, date: '2026-10-19', notes: 'دستی', createdAt: old.at - 60_000 });
  const r2 = await push1(manager, 'm-a', [...legacy, other]);
  assert.deepEqual(why(r2), ['approval_required', 'tx_rejected', 'tx_rejected', 'tx_rejected', 'tx_rejected', 'applied']);
  assert.equal(await row1('transactions', 's-a-old-give'), null);
  assert.ok(await row1('transactions', 't-a-manual'));
});

test('v1 (b): لغوِ نیازمندِ تأیید ⇒ «رسیدِ» لغو و برگشتِ انبار هم نمی‌نشیند', async () => {
  const base = save();
  const b = await push1(owner, 'o-b', v1CreditSale(base, 's-b', { discount: 0 }));
  assert.ok(b.body.results.every((x) => x.status === 'applied'), JSON.stringify(b.body));

  const sv = save();
  const ops = [
    sv.op('sales', 's-b', 'update', { status: 'cancelled', cancelReason: 'اشتباه', debtSettled: { $inc: 100 } }),
    sv.op('transactions', 's-b-rcv', 'insert', { debtorId: 'd-1', type: 'receive', amount: 100, notes: 'لغو', createdAt: sv.at,
      refId: 's-b', kind: 'sale_cancel' }),
    sv.op('stockMovements', 's-b-back', 'insert', { productId: 'p-gc', type: 'sale_cancel', qty: 3, refId: 's-b', createdAt: sv.at }),
    sv.op('auditLog', 's-b-a2', 'insert', { type: 'cancel_sale', refId: 's-b', createdAt: sv.at }),
  ];
  const r = await push1(manager, 'm-b', ops);
  assert.deepEqual(why(r), ['approval_required', 'tx_rejected', 'tx_rejected', 'tx_rejected']);
  assert.equal(await row1('transactions', 's-b-rcv'), null);
  assert.equal((await row1('sales', 's-b')).data.status, 'completed');

  //  برنامهٔ قدیمی: رسید بی refId/kind — با لحظهٔ همان لغو (سطرِ cancel_sale) شناخته می‌شود
  const old = save();
  const legacy = [
    old.op('sales', 's-b', 'update', { status: 'cancelled', debtSettled: { $inc: 100 } }),
    old.op('transactions', 's-b-rcv-old', 'insert', { debtorId: 'd-1', type: 'receive', amount: 100, notes: 'لغو', createdAt: old.at }),
    old.op('auditLog', 's-b-a3', 'insert', { type: 'cancel_sale', refId: 's-b', createdAt: old.at }),
  ];
  const r2 = await push1(manager, 'm-b', legacy);
  assert.deepEqual(why(r2), ['approval_required', 'tx_rejected', 'tx_rejected']);
  assert.equal(await row1('transactions', 's-b-rcv-old'), null);
});

test('v1 (c): برگشتِ بیش از سقف ⇒ debtSettled ِ فاکتور، returnedQty ِ قلم، رسید و حرکتِ انبارش هم نه', async () => {
  const base = save();
  assert.ok((await push1(owner, 'o-c', v1CreditSale(base, 's-c', { total: 900, discount: 0 }))).body.results
    .every((x) => x.status === 'applied'));
  for (const legacy of [false, true]) {
    const sv = save();
    const R = legacy ? 'r-c-old' : 'r-c-new';
    const rcv = { debtorId: 'd-1', type: 'receive', amount: 600, notes: 'مرجوعی', createdAt: sv.at };
    if (!legacy) Object.assign(rcv, { refId: 's-c', kind: 'customer_return' });
    //  ترتیبِ `LedgerDiff.COLLECTIONS`: sales و saleItems پیش از returns
    const ops = [
      sv.op('sales', 's-c', 'update', { debtSettled: { $inc: 600 } }),
      sv.op('saleItems', 's-c-i1', 'update', { returnedQty: { $inc: 2 } }),
      sv.op('returns', R, 'insert', { saleId: 's-c', saleItemId: 's-c-i1', productId: 'p-gc', quantity: 2, amount: 600, createdAt: sv.at }),
      sv.op('transactions', `${R}-rcv`, 'insert', rcv),
      sv.op('stockMovements', `${R}-m`, 'insert', { productId: 'p-gc', type: 'customer_return', qty: 2, refId: R, createdAt: sv.at }),
    ];
    const r = await push1(staff, 's-c', ops);
    assert.deepEqual(why(r), ['tx_rejected', 'tx_rejected', 'approval_required', 'tx_rejected', 'tx_rejected'], `legacy=${legacy}`);
    assert.equal(Number((await row1('sales', 's-c')).data.debtSettled), 0);
    assert.equal(Number((await row1('saleItems', 's-c-i1')).data.returnedQty), 0);
    assert.equal(await row1('transactions', `${R}-rcv`), null);
  }
});

test('v1 (d): اصلاحِ انبارِ برگشتِ #318 (refId = docId؛ یا بی refId ولی همان ثبت) همراهِ برگشت است', async () => {
  const sv = save();
  const doc = 'rd-d1';
  const ops = [
    sv.op('warehouseEntries', 'w-d-1', 'insert', { productId: 'p-gc', units: -2, isAdjustment: true, refId: doc, createdAt: sv.at }),
    sv.op('warehouseEntries', 'w-d-2', 'insert', { productId: 'p-gc', units: 1, isAdjustment: true, createdAt: sv.at }),
    sv.op('returns', 'r-d-1', 'insert', { docId: doc, saleId: '', productId: 'p-gc', quantity: 2, amount: 400, disposition: 'damaged', createdAt: sv.at }),
    sv.op('returns', 'r-d-2', 'insert', { docId: doc, saleId: '', productId: 'p-gc', quantity: 1, amount: 200, disposition: 'restock', createdAt: sv.at }),
    sv.op('stockMovements', 'm-d-1', 'insert', { productId: 'p-gc', type: 'customer_return', qty: 2, refId: 'r-d-1', createdAt: sv.at }),
  ];
  const r = await push1(staff, 's-d', ops);
  assert.deepEqual(why(r), ['tx_rejected', 'tx_rejected', 'approval_required', 'approval_required', 'tx_rejected']);
  assert.equal(await row1('warehouseEntries', 'w-d-1'), null);
  assert.equal(await row1('warehouseEntries', 'w-d-2'), null);
});

test('v1: سقفِ برگشت روی جمعِ سند است — دو ردیفِ ۳۰۰ زیرِ سقفِ ۵۰۰ رد؛ برگهٔ returns/<docId> هر دو را می‌نشاند', async () => {
  //  برگشتِ قدیمی: بی docId، همان فاکتور و همان لحظه
  const sv = save();
  const legacy = [
    sv.op('returns', 'r-s-1', 'insert', { saleId: 's-c', saleItemId: 's-c-i1', quantity: 1, amount: 300, createdAt: sv.at }),
    sv.op('returns', 'r-s-2', 'insert', { saleId: 's-c', saleItemId: 's-c-i2', quantity: 1, amount: 300, createdAt: sv.at }),
  ];
  assert.deepEqual(why(await push1(staff, 's-s', legacy)), ['approval_required', 'approval_required']);

  const doc = 'rd-sum';
  const sv2 = save();
  const rows = (extra = {}) => [
    sv2.op('returns', `${doc}-1`, 'insert', { docId: doc, saleId: 's-c', quantity: 1, amount: 300, createdAt: sv2.at, ...extra }),
    sv2.op('returns', `${doc}-2`, 'insert', { docId: doc, saleId: 's-c', quantity: 1, amount: 300, createdAt: sv2.at }),
  ];
  const bare = await push1(staff, 's-s', rows());
  assert.deepEqual(why(bare), ['approval_required', 'approval_required']);
  assert.equal((await boxes(bare.body.results[0].op_id))[0].detail.amount, 60000);

  const s = slip(ownerKey, { doc: `returns/${doc}`, perm: 'returns.create', amount: 60000 });
  const ok = await push1(staff, 's-s', rows({ approval: s }));
  assert.deepEqual(why(ok), ['applied', 'applied'], JSON.stringify(ok.body));
  //  یک ردیف زیرِ سقف هنوز همان امروز
  const sv3 = save();
  assert.deepEqual(why(await push1(staff, 's-s', [sv3.op('returns', 'r-s-one', 'insert', { saleId: 's-c', amount: 300, createdAt: sv3.at })])), ['applied']);
});

test('v1 (e): سند در یک دسته، همراه‌ها در دستهٔ بعد ⇒ همراه‌ها هم رد؛ پس از نشستنِ سند، آزاد', async () => {
  const sv = save();
  const [sale, item, give, move, audit] = v1CreditSale(sv, 's-e');
  assert.deepEqual(why(await push1(manager, 'm-e', [sale])), ['approval_required']);
  const r = await push1(manager, 'm-e', [item, give, move, audit]);
  assert.deepEqual(why(r), ['tx_rejected', 'tx_rejected', 'tx_rejected', 'tx_rejected']);

  //  برنامهٔ قدیمی: «برد» ِ بی refId در دستهٔ بعد — همان دستگاه، همان لحظه، همان قرض‌دار
  const old = save();
  const [osale, , ogive] = v1CreditSale(old, 's-e-old', { legacy: true });
  assert.deepEqual(why(await push1(manager, 'm-e', [osale])), ['approval_required']);
  assert.deepEqual(why(await push1(manager, 'm-e', [ogive])), ['tx_rejected']);
  assert.equal(await row1('transactions', 's-e-old-give'), null);

  //  سند بعداً با برگه می‌نشیند ⇒ همراهِ تازه‌اش دیگر رد نمی‌شود
  const s = slip(ownerKey, { doc: 'sales/s-e', perm: 'sales.discount', pct_bp: 3000, amount: 3000 });
  const again = { ...sale, op_id: oid(), fields: { ...sale.fields, approval: s } };
  assert.deepEqual(why(await push1(manager, 'm-e', [again])), ['applied']);
  const later = { ...item, op_id: oid() };
  assert.deepEqual(why(await push1(manager, 'm-e', [later])), ['applied']);
});

test('v1: ردیفِ بی نشانی و بی زمان پس از سندِ ردشده «deferred» است — نه اعمال، نه گم؛ دورِ بعد می‌نشیند', async () => {
  const sv = save();
  const [sale] = v1CreditSale(sv, 's-h');
  const loose = sv.op('transactions', 't-h', 'insert', { debtorId: 'd-1', type: 'give', amount: 70, notes: 'نسیه' });
  const before = sv.op('transactions', 't-h-before', 'insert', { debtorId: 'd-9', type: 'give', amount: 1, notes: 'قبلی' });
  const r = await push1(manager, 'm-h', [before, sale, loose]);
  assert.deepEqual(r.body.results.map((x) => x.status), ['applied', 'rejected', 'deferred']);
  assert.equal(r.body.results[2].reason, 'held_after_reject');
  assert.equal(await row1('transactions', 't-h'), null);
  //  دستگاهِ تازه همان op را دوباره می‌فرستد (سرِ سند دیگر در دسته نیست) ⇒ می‌نشیند
  assert.deepEqual(why(await push1(manager, 'm-h', [loose])), ['applied']);
  //  صندوقِ تعارضِ فروش نامِ ردیفِ نگه‌داشته را دارد
  const box = (await boxes(sale.op_id))[0];
  assert.deepEqual(box.detail.held, [loose.op_id]);
});

test('v1: شاگرد بی اجازهٔ تخفیف ⇒ همان discount_not_allowed، همراه‌ها هم رد، و صندوقِ perm_denied (مثلِ v3)', async () => {
  const sv = save();
  const ops = v1CreditSale(sv, 's-st', { discount: 5 });
  const r = await push1(staff, 's-st', ops);
  assert.deepEqual(why(r), ['discount_not_allowed', 'tx_rejected', 'tx_rejected', 'tx_rejected', 'tx_rejected']);
  assert.equal((await boxes(ops[0].op_id))[0].kind, 'perm_denied');
});

/* ------------------------------------------------------------ Sync v3 */

const tx3 = (tx) => ({
  op: (table, rowId, kind, fields, extra = {}) => ({ op_id: oid(), hlc: H('pos'), tx, table, row_id: rowId, kind, fields, ...extra }),
});

test('v3 (a)(b)(c): با tx کلِ ثبت با هم برمی‌گردد — برد، رسیدِ لغو، و شمارنده‌های برگشت', async () => {
  const a = tx3('tx.a3');
  const ra = await push3(manager, 'm3', [
    a.op('sales', 's3-a', 'insert', { totalMinor: 10000, discountMinor: 3000, finalTotalMinor: 7000, status: 'completed', debtorId: 'd-1' }),
    a.op('saleItems', 's3-a-i', 'insert', { saleId: 's3-a', productId: 'p-gc', quantityMilli: 1000, unitPriceMinor: 10000 }),
    a.op('transactions', 's3-a-give', 'insert', { debtorId: 'd-1', type: 'give', amountMinor: 7000, refId: 's3-a', kind: 'sale_credit' }),
  ]);
  assert.deepEqual(why(ra), ['approval_required', 'tx_rejected', 'tx_rejected']);
  assert.equal(await row3('transactions', 's3-a-give'), null);

  const base = tx3('tx.b3-base');
  assert.ok((await push3(owner, 'o3', [
    base.op('sales', 's3-b', 'insert', { totalMinor: 10000, finalTotalMinor: 10000, status: 'completed', debtorId: 'd-1' }),
    base.op('saleItems', 's3-b-i', 'insert', { saleId: 's3-b', productId: 'p-gc', quantityMilli: 2000, unitPriceMinor: 5000 }),
  ])).body.results.every((x) => x.status === 'applied'));
  const b = tx3('tx.b3');
  const rb = await push3(manager, 'm3', [
    b.op('sales', 's3-b', 'cancel', undefined, { reason: 'اشتباه' }),
    b.op('transactions', 's3-b-rcv', 'insert', { debtorId: 'd-1', type: 'receive', amountMinor: 10000, refId: 's3-b', kind: 'sale_cancel' }),
  ]);
  assert.deepEqual(why(rb), ['approval_required', 'tx_rejected']);

  const c = tx3('tx.c3');
  const rc = await push3(staff, 's3', [
    c.op('sales', 's3-b', 'delta', { debtSettledMinor: 60000 }),
    c.op('saleItems', 's3-b-i', 'delta', { returnedQtyMilli: 1000 }),
    c.op('returns', 'r3-c', 'insert', { saleId: 's3-b', saleItemId: 's3-b-i', amountMinor: 60000 }),
    c.op('transactions', 'r3-c-rcv', 'insert', { debtorId: 'd-1', type: 'receive', amountMinor: 60000, refId: 's3-b', kind: 'customer_return' }),
  ]);
  assert.deepEqual(why(rc), ['tx_rejected', 'tx_rejected', 'approval_required', 'tx_rejected']);
});

test('v3: سقفِ برگشت روی جمعِ سند (tx یا docId)؛ برگهٔ returns/<docId> با مبلغِ کل یک بار مصرف می‌شود', async () => {
  const t = tx3('tx.sum3');
  const r = await push3(staff, 's3', [
    t.op('returns', 'r3-s1', 'insert', { saleId: 's3-b', amountMinor: 30000 }),
    t.op('returns', 'r3-s2', 'insert', { saleId: 's3-b', amountMinor: 30000 }),
  ]);
  assert.deepEqual(why(r), ['approval_required', 'tx_rejected']);

  //  docId بی tx (برنامهٔ v3 ِ قدیمی که tx نمی‌گذاشت)
  const d = tx3(undefined);
  const doc = 'rd3-sum';
  const bare = await push3(staff, 's3', [
    d.op('returns', `${doc}-1`, 'insert', { docId: doc, saleId: 's3-b', amountMinor: 30000 }),
    d.op('returns', `${doc}-2`, 'insert', { docId: doc, saleId: 's3-b', amountMinor: 30000 }),
  ]);
  assert.deepEqual(why(bare), ['approval_required', 'approval_required']);

  const s = slip(ownerKey, { doc: `returns/${doc}`, perm: 'returns.create', amount: 60000 });
  const w = tx3(`tx.${doc}`);
  const ok = await push3(staff, 's3', [
    w.op('returns', `${doc}-1`, 'insert', { docId: doc, saleId: 's3-b', amountMinor: 30000 }, { approval: s }),
    w.op('returns', `${doc}-2`, 'insert', { docId: doc, saleId: 's3-b', amountMinor: 30000 }, { approval: s }),
  ]);
  assert.deepEqual(why(ok), ['applied', 'applied'], JSON.stringify(ok.body));
});

test('v3 (e)(f): tx ِ بزرگ (۶۰ قلم) کامل برمی‌گردد؛ دنبالهٔ همان tx در دستهٔ بعد هم رد می‌شود', async () => {
  const T = 'tx.big3';
  const t = tx3(T);
  const head = [t.op('sales', 's3-big', 'insert', { totalMinor: 60000, discountMinor: 30000, finalTotalMinor: 30000, status: 'completed' })];
  for (let i = 0; i < 59; i++) head.push(t.op('saleItems', `s3-big-i${i}`, 'insert', { saleId: 's3-big', productId: 'p-gc', quantityMilli: 1000, unitPriceMinor: 1000 }));
  const r = await push3(manager, 'm3-big', head);
  assert.equal(r.body.results.length, 60);
  assert.equal(r.body.results[0].reason, 'approval_required');
  assert.ok(r.body.results.slice(1).every((x) => x.reason === 'tx_rejected'));

  const tail = [];
  for (let i = 59; i < 62; i++) tail.push(t.op('saleItems', `s3-big-i${i}`, 'insert', { saleId: 's3-big', productId: 'p-gc', quantityMilli: 1000, unitPriceMinor: 1000 }));
  tail.push(t.op('stockMovements', 's3-big-m', 'insert', { productId: 'p-gc', qtyMilli: -62000, type: 'sale', refId: 's3-big' }));
  const r2 = await push3(manager, 'm3-big', tail);
  assert.ok(r2.body.results.every((x) => x.reason === 'tx_rejected'), JSON.stringify(r2.body.results));
  assert.equal(await row3('stockMovements', 's3-big-m'), null);
});
