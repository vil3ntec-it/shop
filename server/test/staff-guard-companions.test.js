'use strict';
/**
 * نگهبانِ تأیید (STF-02/03): ردِ یک سندِ پولی «کلِ سند» است، نه فقط سرش — v1 و v3.
 *
 *   (a) فروشِ نسیهٔ بیش از سقف ⇒ ردیفِ «برد» (give) هم برنمی‌نشیند
 *   (b) لغوِ نیازمندِ تأیید ⇒ «رسیدِ» لغو (receive) هم نه
 *   (c) برگشتِ بیش از سقف ⇒ debtSettled ِ فاکتور، returnedQty ِ قلم و رسیدش هم نه
 *   (d) اصلاحِ انبارِ برگشت (#318، ضایعات/بی رسید) هم همراهِ برگشت است
 *   (e) سند و همراه‌ها در دو دسته (بُرشِ ۲۰۰تاییِ v1، یا tx ِ بزرگ در v3) ⇒ همراهِ دستهٔ بعد هم رد —
 *       فقط همان دستگاه، فقط صندوقِ باز، و نه وقتی خودِ سند در همان دسته پذیرفته شد
 *   (f) برنامهٔ v3 ِ کهنه بی tx (ثبتِ بزرگ‌تر از ۵۰) ⇒ سند و همراه‌ها با هم
 *   «همان ثبت» = `ts` ِ op ِ v1 (هر دو برنامه یک ts برای یک diff)؛ دفترِ پایه (چند سند زیرِ یک ts)
 *   createdAt ِ نزدیک هم لازم دارد. ردیفی که معلوم نیست مالِ سند است اعمال می‌شود — هیچ وضعیتِ
 *   تازه‌ای (deferred و مانند آن) برنمی‌گردد: برنامهٔ کهنه هر چه جز applied/duplicate را دور می‌ریزد.
 *   سقفِ برگشت تجمعیِ هر فاکتور است (همهٔ برگشت‌های آن فاکتور)؛ برگهٔ سند `returns/<docId>`.
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

/** برنامهٔ کهنه فقط این سه را می‌شناسد؛ هر چیزِ دیگر «ردشده» است و از صف بیرون می‌رود */
const OLD_CLIENT = new Set(['applied', 'duplicate', 'rejected']);
async function push1(who, device, ops) {
  const r = await h.api('POST', '/api/sync/v1/push', { token: who.accessToken, body: { device_id: device, schema_version: 1, ops } });
  if (r.status === 200) {
    for (const x of r.body.results) assert.ok(OLD_CLIENT.has(x.status), `وضعیتی که برنامهٔ کهنه نمی‌شناسد: ${JSON.stringify(x)}`);
  }
  return r;
}
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
const boxes = (opId) => many('SELECT id, kind, status, detail FROM sync_v3_conflicts WHERE account_id=$1 AND loser_op_id=$2', [shopId, opId]);
const why = (r) => r.body.results.map((x) => (x.status === 'applied' || x.status === 'duplicate' ? x.status : x.reason || x.status));

/** یک «ثبت» ِ برنامهٔ v1: همهٔ opها یک ts (همان `LedgerDiff.diff(now)`)؛ createdAt ِ ردیف‌ها هم همان */
//  نزدیکِ اکنون (پنجرهٔ زمانِ برگه ۵ دقیقه پیش از صدور را می‌پذیرد)
let clock = Date.now() - 200_000;
function save(at = (clock += 1000)) {
  return {
    at,
    op: (table, rowId, type, fields, ts = at) => ({ op_id: oid(), table, row_id: rowId, type, ts, fields }),
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

/** فروشِ نسیهٔ v1 با قلم، «برد»، حرکت و سطرِ دفترِ کار؛ `legacy` = برنامهٔ قدیمی (برد بی refId/kind) */
function v1CreditSale(sv, id, { total = 100, discount = 30, debtor = 'd-1', legacy = false, at = sv.at } = {}) {
  const final = total - discount;
  const give = { debtorId: debtor, type: 'give', amount: final, date: '2026-10-19', notes: 'فروش نسیه', createdAt: at };
  if (!legacy) Object.assign(give, { refId: id, kind: 'sale_credit' });
  return [
    sv.op('sales', id, 'insert', { total, discount, finalTotal: final, paymentMethod: 'credit', debtorId: debtor,
      paidAmount: 0, remaining: final, debtGiven: final, debtSettled: 0, status: 'completed', createdAt: at }),
    sv.op('saleItems', `${id}-i1`, 'insert', { saleId: id, productId: 'p-gc', quantity: 5, unitPrice: total / 5, totalPrice: total, returnedQty: 0 }),
    sv.op('transactions', `${id}-give`, 'insert', give),
    sv.op('stockMovements', `${id}-m1`, 'insert', { productId: 'p-gc', type: 'sale', qty: -5, refId: id, createdAt: at }),
    sv.op('auditLog', `${id}-a1`, 'insert', { type: 'sale', refId: id, notes: 'ثبت فروش', createdAt: at }),
  ];
}
async function ownerSale(id, total) {
  const r = await push1(owner, 'o-base', v1CreditSale(save(), id, { total, discount: 0 }));
  assert.ok(r.body.results.every((x) => x.status === 'applied'), JSON.stringify(r.body));
}
/** برگشتِ قدیمیِ v1 (ترتیبِ `LedgerDiff.COLLECTIONS`: sales و saleItems پیش از returns) */
function v1Return(sv, R, sale, { amount, qty = 1, legacy = false, item = `${sale}-i1` }) {
  const rcv = { debtorId: 'd-1', type: 'receive', amount, notes: 'مرجوعی', createdAt: sv.at };
  if (!legacy) Object.assign(rcv, { refId: sale, kind: 'customer_return' });
  return [
    sv.op('sales', sale, 'update', { debtSettled: { $inc: amount } }),
    sv.op('saleItems', item, 'update', { returnedQty: { $inc: qty } }),
    sv.op('returns', R, 'insert', { saleId: sale, saleItemId: item, productId: 'p-gc', quantity: qty, amount, createdAt: sv.at }),
    sv.op('transactions', `${R}-rcv`, 'insert', rcv),
    sv.op('stockMovements', `${R}-m`, 'insert', { productId: 'p-gc', type: 'customer_return', qty, refId: R, createdAt: sv.at }),
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

test('v1 (a): فروشِ نسیهٔ بیش از سقف ⇒ «برد» هم نه (refId، یا بی refId در همان ثبت)؛ فروشِ نسیهٔ دیگرِ همان قرض‌دار می‌نشیند', async () => {
  const sv = save();
  const r = await push1(manager, 'm-a', v1CreditSale(sv, 's-a-new'));
  assert.equal(r.status, 200, JSON.stringify(r.body));
  assert.deepEqual(why(r), ['approval_required', 'tx_rejected', 'tx_rejected', 'tx_rejected', 'tx_rejected']);
  assert.equal(await row1('transactions', 's-a-new-give'), null);

  //  برنامهٔ قدیمی؛ در همان دسته فروشِ نسیهٔ بی‌تخفیفِ دیگری از ثبتِ بعد (همان قرض‌دار، ۳ ثانیه بعد)
  //  و رسیدِ دستیِ قرض‌دارِ دیگر — هر دو باید بنشینند
  const old = save();
  const next = save(old.at + 3000);
  const legacy = v1CreditSale(old, 's-a-old', { legacy: true });
  const plain = v1CreditSale(next, 's-a-plain', { legacy: true, discount: 0 });
  const manual = save(old.at + 60_000).op('transactions', 't-a-manual', 'insert',
    { debtorId: 'd-2', type: 'receive', amount: 5, notes: 'دستی', createdAt: old.at + 60_000 });
  const r2 = await push1(manager, 'm-a', [...legacy, ...plain, manual]);
  assert.deepEqual(why(r2), ['approval_required', 'tx_rejected', 'tx_rejected', 'tx_rejected', 'tx_rejected',
    'applied', 'applied', 'applied', 'applied', 'applied', 'applied']);
  assert.equal(await row1('transactions', 's-a-old-give'), null);
  assert.ok(await row1('transactions', 's-a-plain-give'));
  assert.ok(await row1('transactions', 't-a-manual'));
});

test('v1 (b): لغوِ نیازمندِ تأیید ⇒ «رسیدِ» لغو و برگشتِ انبارِ همان ثبت هم نمی‌نشیند', async () => {
  await ownerSale('s-b', 100);
  const sv = save();
  const r = await push1(manager, 'm-b', [
    sv.op('sales', 's-b', 'update', { status: 'cancelled', cancelReason: 'اشتباه', debtSettled: { $inc: 100 } }),
    sv.op('transactions', 's-b-rcv', 'insert', { debtorId: 'd-1', type: 'receive', amount: 100, createdAt: sv.at, refId: 's-b', kind: 'sale_cancel' }),
    sv.op('stockMovements', 's-b-back', 'insert', { productId: 'p-gc', type: 'sale_cancel', qty: 5, refId: 's-b', createdAt: sv.at }),
    sv.op('auditLog', 's-b-a2', 'insert', { type: 'cancel_sale', refId: 's-b', createdAt: sv.at }),
  ]);
  assert.deepEqual(why(r), ['approval_required', 'tx_rejected', 'tx_rejected', 'tx_rejected']);
  assert.equal(await row1('transactions', 's-b-rcv'), null);
  assert.equal((await row1('sales', 's-b')).data.status, 'completed');

  //  برنامهٔ قدیمی: رسید بی refId/kind — همان ثبت (همان ts)
  const old = save();
  const r2 = await push1(manager, 'm-b', [
    old.op('sales', 's-b', 'update', { status: 'cancelled', debtSettled: { $inc: 100 } }),
    old.op('transactions', 's-b-rcv-old', 'insert', { debtorId: 'd-1', type: 'receive', amount: 100, notes: 'لغو', createdAt: old.at }),
    old.op('auditLog', 's-b-a3', 'insert', { type: 'cancel_sale', refId: 's-b', createdAt: old.at }),
  ]);
  assert.deepEqual(why(r2), ['approval_required', 'tx_rejected', 'tx_rejected']);
  assert.equal(await row1('transactions', 's-b-rcv-old'), null);
});

test('v1 (c): برگشتِ بیش از سقف ⇒ debtSettled ِ فاکتور، returnedQty ِ قلم، رسید و حرکتِ انبارش هم نه', async () => {
  await ownerSale('s-c', 900);
  for (const legacy of [false, true]) {
    const R = legacy ? 'r-c-old' : 'r-c-new';
    const r = await push1(staff, 's-c', v1Return(save(), R, 's-c', { amount: 600, qty: 2, legacy }));
    assert.deepEqual(why(r), ['tx_rejected', 'tx_rejected', 'approval_required', 'tx_rejected', 'tx_rejected'], `legacy=${legacy}`);
    assert.equal(Number((await row1('sales', 's-c')).data.debtSettled), 0);
    assert.equal(Number((await row1('saleItems', 's-c-i1')).data.returnedQty), 0);
    assert.equal(await row1('transactions', `${R}-rcv`), null);
  }
});

test('v1 (d): اصلاحِ انبارِ برگشتِ #318 (refId = docId؛ یا بی refId در همان ثبت) همراهِ برگشت است', async () => {
  const sv = save();
  const doc = 'rd-d1';
  const r = await push1(staff, 's-d', [
    sv.op('warehouseEntries', 'w-d-1', 'insert', { productId: 'p-gc', units: -2, isAdjustment: true, refId: doc, createdAt: sv.at }),
    sv.op('warehouseEntries', 'w-d-2', 'insert', { productId: 'p-gc', units: 1, isAdjustment: true, createdAt: sv.at }),
    sv.op('returns', 'r-d-1', 'insert', { docId: doc, saleId: '', productId: 'p-gc', quantity: 2, amount: 400, disposition: 'damaged', createdAt: sv.at }),
    sv.op('returns', 'r-d-2', 'insert', { docId: doc, saleId: '', productId: 'p-gc', quantity: 1, amount: 200, disposition: 'restock', createdAt: sv.at }),
    sv.op('stockMovements', 'm-d-1', 'insert', { productId: 'p-gc', type: 'customer_return', qty: 2, refId: 'r-d-1', createdAt: sv.at }),
  ]);
  assert.deepEqual(why(r), ['tx_rejected', 'tx_rejected', 'approval_required', 'approval_required', 'tx_rejected']);
  assert.equal(await row1('warehouseEntries', 'w-d-1'), null);
  assert.equal(await row1('warehouseEntries', 'w-d-2'), null);
});

test('v1: دو ثبت در یک دسته روی همان قلم — شمارنده‌های برگشتِ ردشده برمی‌گردند، مالِ برگشتِ پذیرفته می‌نشیند', async () => {
  await ownerSale('s-tw', 1000);
  const a = v1Return(save(), 'r-tw-a', 's-tw', { amount: 600, qty: 3 });
  const b = v1Return(save(), 'r-tw-b', 's-tw', { amount: 100, qty: 1 });
  const r = await push1(staff, 's-tw', [...a, ...b]);
  assert.deepEqual(why(r), ['tx_rejected', 'tx_rejected', 'approval_required', 'tx_rejected', 'tx_rejected',
    'applied', 'applied', 'applied', 'applied', 'applied']);
  assert.equal(Number((await row1('sales', 's-tw')).data.debtSettled), 100);
  assert.equal(Number((await row1('saleItems', 's-tw-i1')).data.returnedQty), 1);
  assert.ok(await row1('returns', 'r-tw-b'));
});

test('v1: سقفِ برگشت تجمعیِ هر فاکتور است — در چند دسته و چند سند؛ برگهٔ returns/<docId> همان سند را می‌پوشاند', async () => {
  await ownerSale('s-cu', 2000);
  const one1 = save();
  assert.deepEqual(why(await push1(staff, 's-cu', [one1.op('returns', 'r-cu-1', 'insert', { saleId: 's-cu', amount: 300, createdAt: one1.at })])), ['applied']);
  //  برگشتِ دوم روی همان فاکتور، دستگاهِ دیگر: ۳۰۰ + ۳۰۰ > ۵۰۰
  const two = save();
  const second = two.op('returns', 'r-cu-2', 'insert', { saleId: 's-cu', amount: 300, createdAt: two.at });
  const r = await push1(staff, 's-cu-2', [second]);
  assert.deepEqual(why(r), ['approval_required']);
  const box = (await boxes(second.op_id))[0];
  assert.equal(box.detail.amount, 30000);
  assert.equal(box.detail.cumulative, 60000);
  const s = slip(ownerKey, { doc: 'returns/r-cu-2', perm: 'returns.create', amount: 30000 });
  const again = { ...second, op_id: oid(), fields: { ...second.fields, approval: s } };
  assert.deepEqual(why(await push1(staff, 's-cu-2', [again])), ['applied']);

  //  دو سند در یک دسته روی فاکتورِ تازه: ۲۰۰ می‌نشیند، ۴۰۰ ِ بعدی (جمع ۶۰۰) نه
  await ownerSale('s-cu3', 2000);
  const x = save();
  const y = save();
  const r3 = await push1(staff, 's-cu', [
    x.op('returns', 'r-cu3-a', 'insert', { saleId: 's-cu3', amount: 200, createdAt: x.at }),
    y.op('returns', 'r-cu3-b', 'insert', { saleId: 's-cu3', amount: 400, createdAt: y.at }),
  ]);
  assert.deepEqual(why(r3), ['applied', 'approval_required']);

  //  سندِ docId با دو ردیف: یک برگه با مبلغِ کلِ سند، یک بار مصرف
  await ownerSale('s-cu4', 2000);
  const doc = 'rd-cu4';
  const d = save();
  const rows = (extra = {}) => [
    d.op('returns', `${doc}-1`, 'insert', { docId: doc, saleId: 's-cu4', amount: 300, createdAt: d.at, ...extra }),
    d.op('returns', `${doc}-2`, 'insert', { docId: doc, saleId: 's-cu4', amount: 300, createdAt: d.at }),
  ];
  assert.deepEqual(why(await push1(staff, 's-cu', rows())), ['approval_required', 'approval_required']);
  const s2 = slip(ownerKey, { doc: `returns/${doc}`, perm: 'returns.create', amount: 60000 });
  assert.deepEqual(why(await push1(staff, 's-cu', rows({ approval: s2 }))), ['applied', 'applied']);
});

test('v1: برگشتِ وب (هر ردیف createdAt ِ خودش، یک ts برای ثبت) یک سند است — ۳۰۰+۳۰۰ زیرِ سقفِ ۵۰۰ نمی‌گذرد', async () => {
  await ownerSale('s-web', 2000);
  const sv = save();
  const r = await push1(staff, 's-web', [
    sv.op('returns', 'r-web-1', 'insert', { saleId: 's-web', saleItemId: 's-web-i1', amount: 300, createdAt: sv.at + 1 }),
    sv.op('returns', 'r-web-2', 'insert', { saleId: 's-web', saleItemId: 's-web-i1', amount: 300, createdAt: sv.at + 4 }),
  ]);
  assert.deepEqual(why(r), ['approval_required', 'approval_required']);
});

test('v1: دفترِ پایه (چند فاکتور زیرِ یک ts) — فقط «برد»ِ همان فاکتور (createdAt ِ نزدیک) رد می‌شود', async () => {
  const ts = (clock += 1000);
  const base = save(ts);
  const bad = v1CreditSale(base, 's-bulk-bad', { legacy: true, at: ts - 50_000 });
  const ok = v1CreditSale(base, 's-bulk-ok', { legacy: true, discount: 0, at: ts - 20_000 });
  const r = await push1(manager, 'm-bulk', [bad[0], ok[0], bad[1], ok[1], bad[2], ok[2]]);
  assert.deepEqual(why(r), ['approval_required', 'applied', 'tx_rejected', 'applied', 'tx_rejected', 'applied']);
  assert.ok(await row1('transactions', 's-bulk-ok-give'));
});

test('v1 (e): سند در یک دسته، همراه در دستهٔ بعدِ همان دستگاه ⇒ رد؛ دستگاهِ دیگر، صندوقِ بسته، یا سندِ پذیرفته ⇒ آزاد', async () => {
  const sv = save();
  const [sale, item, give, move, audit] = v1CreditSale(sv, 's-e');
  assert.deepEqual(why(await push1(manager, 'm-e', [sale])), ['approval_required']);
  assert.deepEqual(why(await push1(manager, 'm-e', [item, give, move, audit])), ['tx_rejected', 'tx_rejected', 'tx_rejected', 'tx_rejected']);
  //  برنامهٔ قدیمی: «برد» ِ بی refId در دستهٔ بعد — همان دستگاه، همان ts
  const old = save();
  const [osale, , ogive] = v1CreditSale(old, 's-e-old', { legacy: true });
  assert.deepEqual(why(await push1(manager, 'm-e', [osale])), ['approval_required']);
  assert.deepEqual(why(await push1(manager, 'm-e', [ogive])), ['tx_rejected']);

  //  دستگاهِ دیگرِ همان کاربر چیزی از آن سند نگه نمی‌دارد
  assert.deepEqual(why(await push1(manager, 'm-e-other', [{ ...item, op_id: oid(), row_id: 's-e-i9' }])), ['applied']);

  //  سند در همان دسته با برگه می‌نشیند ⇒ همراهش هم
  const s = slip(ownerKey, { doc: 'sales/s-e', perm: 'sales.discount', pct_bp: 3000, amount: 3000 });
  const again = { ...sale, op_id: oid(), fields: { ...sale.fields, approval: s } };
  assert.deepEqual(why(await push1(manager, 'm-e', [again, { ...item, op_id: oid() }])), ['applied', 'applied']);
});

test('v1 (B1): لغوِ ردشده و کنارگذاشته‌شده ⇒ لغوِ درستِ بعدیِ همان فاکتور (همان ts یا نه) کامل می‌نشیند', async () => {
  await ownerSale('s-x', 100);
  const sv = save();
  const cancel = sv.op('sales', 's-x', 'update', { status: 'cancelled' });
  const back = sv.op('stockMovements', 's-x-back', 'insert', { productId: 'p-gc', type: 'sale_cancel', qty: 5, refId: 's-x', createdAt: sv.at });
  assert.deepEqual(why(await push1(manager, 'm-x', [cancel, back])), ['approval_required', 'tx_rejected']);
  const box = (await boxes(cancel.op_id))[0];
  const res = await h.post(`/api/sync/v3/conflicts/${box.id}/resolve`, { action: 'dismiss' }, { token: owner.accessToken });
  assert.equal(res.status, 200, JSON.stringify(res.body));
  //  صاحب لغوِ مدیر را مجاز می‌کند؛ همان دستگاه دوباره لغو می‌کند — همهٔ ردیف‌ها می‌نشینند
  assert.equal((await setRole('manager', { 'sales.discount': { caps: { maxPct: 10 } } })).status, 200);
  const sv2 = save();
  const r = await push1(manager, 'm-x', [
    sv2.op('sales', 's-x', 'update', { status: 'cancelled' }),
    sv2.op('stockMovements', 's-x-back2', 'insert', { productId: 'p-gc', type: 'sale_cancel', qty: 5, refId: 's-x', createdAt: sv2.at }),
    sv2.op('auditLog', 's-x-a2', 'insert', { type: 'cancel_sale', refId: 's-x', createdAt: sv2.at }),
  ]);
  assert.deepEqual(why(r), ['applied', 'applied', 'applied']);
  assert.equal((await setRole('manager', { 'sales.discount': { caps: { maxPct: 10 } }, 'sales.cancel': { mode: 'approve' } })).status, 200);
});

test('v1: ردیفِ بی نشانی و بی زمان از ثبتِ دیگر هرگز رد یا «نگه‌داشته» نمی‌شود — همان دسته اعمال', async () => {
  const sv = save();
  const [sale] = v1CreditSale(sv, 's-h');
  const otherSave = save();
  const loose = otherSave.op('transactions', 't-h', 'insert', { debtorId: 'd-1', type: 'give', amount: 70, notes: 'نسیهٔ دستی' });
  const noTs = { op_id: oid(), table: 'transactions', row_id: 't-h-0', type: 'insert', fields: { debtorId: 'd-1', type: 'receive', amount: 3 } };
  const r = await push1(manager, 'm-h', [sale, loose, noTs]);
  assert.deepEqual(why(r), ['approval_required', 'applied', 'applied']);
});

test('v1: شاگرد بی اجازهٔ تخفیف ⇒ همان discount_not_allowed، همراه‌ها هم رد، و صندوقِ perm_denied (مثلِ v3)', async () => {
  const ops = v1CreditSale(save(), 's-st', { discount: 5 });
  const r = await push1(staff, 's-st', ops);
  assert.deepEqual(why(r), ['discount_not_allowed', 'tx_rejected', 'tx_rejected', 'tx_rejected', 'tx_rejected']);
  assert.equal((await boxes(ops[0].op_id))[0].kind, 'perm_denied');
});

test('v1: صاحبِ دکان بی هیچ داوری و پرس‌وجویی (همان v1)', async () => {
  const r = await push1(owner, 'o-fast', v1CreditSale(save(), 's-own', { discount: 90 }));
  assert.ok(r.body.results.every((x) => x.status === 'applied'), JSON.stringify(r.body));
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
    base.op('sales', 's3-b', 'insert', { totalMinor: 100000, finalTotalMinor: 100000, status: 'completed', debtorId: 'd-1' }),
    base.op('saleItems', 's3-b-i', 'insert', { saleId: 's3-b', productId: 'p-gc', quantityMilli: 2000, unitPriceMinor: 50000 }),
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

test('v3: سقفِ برگشت تجمعیِ فاکتور و روی جمعِ سند (tx یا docId)؛ برگهٔ returns/<docId> یک بار', async () => {
  const t = tx3('tx.sum3');
  assert.deepEqual(why(await push3(staff, 's3', [
    t.op('returns', 'r3-s1', 'insert', { saleId: 's3-b', amountMinor: 30000 }),
    t.op('returns', 'r3-s2', 'insert', { saleId: 's3-b', amountMinor: 30000 }),
  ])), ['approval_required', 'tx_rejected']);

  //  docId بی tx (برنامهٔ v3 ِ کهنه): دو ردیفِ یک سند یک واحد می‌شوند
  const d = tx3(undefined);
  const doc = 'rd3-sum';
  assert.deepEqual(why(await push3(staff, 's3', [
    d.op('returns', `${doc}-1`, 'insert', { docId: doc, saleId: 's3-b', amountMinor: 30000 }),
    d.op('returns', `${doc}-2`, 'insert', { docId: doc, saleId: 's3-b', amountMinor: 30000 }),
  ])), ['approval_required', 'tx_rejected']);
  const s = slip(ownerKey, { doc: `returns/${doc}`, perm: 'returns.create', amount: 60000 });
  const w = tx3(`tx.${doc}`);
  const ok = await push3(staff, 's3', [
    w.op('returns', `${doc}-1`, 'insert', { docId: doc, saleId: 's3-b', amountMinor: 30000 }, { approval: s }),
    w.op('returns', `${doc}-2`, 'insert', { docId: doc, saleId: 's3-b', amountMinor: 30000 }, { approval: s }),
  ]);
  assert.deepEqual(why(ok), ['applied', 'applied'], JSON.stringify(ok.body));
  //  حالا ۶۰۰ روی این فاکتور برگشته ⇒ حتی ۱۰۰ ِ تنها هم تأیید می‌خواهد
  const one3 = tx3(undefined);
  assert.deepEqual(why(await push3(staff, 's3', [one3.op('returns', 'r3-one', 'insert', { saleId: 's3-b', amountMinor: 10000 })])), ['approval_required']);
});

test('v3 (e)(f): tx ِ بزرگ (۶۰ قلم) کامل برمی‌گردد؛ دنبالهٔ همان tx در دستهٔ بعد هم رد می‌شود', async () => {
  const t = tx3('tx.big3');
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

test('v3 (f): برنامهٔ کهنهٔ بی tx — فاکتور و برگشتِ ردشده همراه‌هایشان را هم برمی‌گردانند؛ op ِ بی‌ربط می‌نشیند', async () => {
  const n3 = tx3(undefined);
  const r = await push3(manager, 'm3-old', [
    n3.op('products', 'p3-free', 'insert', { name: 'نمک' }),
    n3.op('sales', 's3-nt', 'insert', { totalMinor: 10000, discountMinor: 3000, finalTotalMinor: 7000, status: 'completed', debtorId: 'd-1' }),
    n3.op('saleItems', 's3-nt-i', 'insert', { saleId: 's3-nt', productId: 'p-gc', quantityMilli: 1000, unitPriceMinor: 10000 }),
    n3.op('transactions', 's3-nt-give', 'insert', { debtorId: 'd-1', type: 'give', amountMinor: 7000, refId: 's3-nt', kind: 'sale_credit' }),
  ]);
  assert.deepEqual(why(r), ['applied', 'approval_required', 'tx_rejected', 'tx_rejected']);
  assert.equal(await row3('transactions', 's3-nt-give'), null);

  const base = tx3('tx.nt-base');
  assert.ok((await push3(owner, 'o3', [
    base.op('sales', 's3-nr', 'insert', { totalMinor: 100000, finalTotalMinor: 100000, status: 'completed', debtorId: 'd-1' }),
    base.op('saleItems', 's3-nr-i', 'insert', { saleId: 's3-nr', productId: 'p-gc', quantityMilli: 2000, unitPriceMinor: 50000 }),
  ])).body.results.every((x) => x.status === 'applied'));
  const rr = await push3(staff, 's3-old', [
    n3.op('sales', 's3-nr', 'delta', { debtSettledMinor: 60000 }),
    n3.op('saleItems', 's3-nr-i', 'delta', { returnedQtyMilli: 1000 }),
    n3.op('returns', 'r3-nr', 'insert', { saleId: 's3-nr', saleItemId: 's3-nr-i', amountMinor: 60000 }),
    n3.op('stockMovements', 'r3-nr-m', 'insert', { productId: 'p-gc', qtyMilli: 1000, type: 'customer_return', refId: 'r3-nr' }),
  ]);
  assert.deepEqual(why(rr), ['tx_rejected', 'tx_rejected', 'approval_required', 'tx_rejected']);
  assert.equal(Number((await row3('saleItems', 's3-nr-i')).data.returnedQtyMilli || 0), 0);
});

/* ------------------------------------------- بازبینیِ سوم: دفترِ پایه، دنباله، هم‌زمانی */

test('v1 (B1): دفترِ پایهٔ بریده در دو دسته (یک ts) — «برد»ِ فاکتورهای پذیرفته با سرِ ردشدهٔ دستهٔ دوم گم نمی‌شود', async () => {
  const ts = (clock += 1000);
  const base = save(ts);
  const s1 = v1CreditSale(base, 's-pd-1', { legacy: true, discount: 0, at: ts - 90_000 });
  const s2 = v1CreditSale(base, 's-pd-2', { legacy: true, discount: 0, at: ts - 60_000 });
  const s3 = v1CreditSale(base, 's-pd-3', { legacy: true, at: ts - 30_000 });
  assert.deepEqual(why(await push1(manager, 'm-pd', [s1[0], s2[0]])), ['applied', 'applied']);
  //  دستهٔ دوم فقط یک «سر» دارد (فاکتورِ ردشده)؛ بردهای دو فاکتورِ پذیرفته همان ts و همان قرض‌دار
  const r = await push1(manager, 'm-pd', [s3[0], s1[2], s2[2], s3[2]]);
  assert.deepEqual(why(r), ['approval_required', 'applied', 'applied', 'tx_rejected']);
  assert.ok(await row1('transactions', 's-pd-1-give'));
  assert.ok(await row1('transactions', 's-pd-2-give'));
});

test('v1: دنبالهٔ سندِ برگشتِ ردشده (همان فاکتور و همان ثبت، دستهٔ بعد) جدا داوری نمی‌شود', async () => {
  await ownerSale('s-pb', 2000);
  const sv = save();
  const r1 = await push1(staff, 's-pb', [
    sv.op('returns', 'r-pb-1', 'insert', { saleId: 's-pb', amount: 300, createdAt: sv.at }),
    sv.op('returns', 'r-pb-2', 'insert', { saleId: 's-pb', amount: 300, createdAt: sv.at }),
  ]);
  assert.deepEqual(why(r1), ['approval_required', 'approval_required']);
  const r2 = await push1(staff, 's-pb', [
    sv.op('returns', 'r-pb-3', 'insert', { saleId: 's-pb', amount: 100, createdAt: sv.at }),
    sv.op('stockMovements', 'r-pb-3-m', 'insert', { productId: 'p-gc', type: 'customer_return', qty: 1, refId: 'r-pb-3', createdAt: sv.at }),
  ]);
  assert.deepEqual(why(r2), ['tx_rejected', 'tx_rejected']);
  assert.equal(await row1('returns', 'r-pb-3'), null);
  //  برگشتِ تازهٔ دیگری روی همان فاکتور (ثبتِ دیگر) جدا داوری می‌شود
  const other = save();
  assert.deepEqual(why(await push1(staff, 's-pb', [other.op('returns', 'r-pb-9', 'insert', { saleId: 's-pb', amount: 100, createdAt: other.at })])), ['applied']);
});

test('v3: دو دستگاهِ هم‌زمان روی یک فاکتور هر دو زیرِ سقف نمی‌گذرند (قفلِ درونِ تراکنشِ push)', async () => {
  const a = tx3(undefined);
  const b = tx3(undefined);
  const [ra, rb] = await Promise.all([
    push3(staff, 's3-cc-a', [a.op('returns', 'r3-cc-a', 'insert', { saleId: 's3-cc', amountMinor: 30000 })]),
    push3(staff, 's3-cc-b', [b.op('returns', 'r3-cc-b', 'insert', { saleId: 's3-cc', amountMinor: 30000 })]),
  ]);
  assert.deepEqual([...why(ra), ...why(rb)].sort(), ['applied', 'approval_required']);
});

test('درخواستِ نیمه‌کاره (قطعِ اتصال) چیزی را نگه نمی‌دارد — برگشتِ بعدی بی‌درنگ می‌نشیند (v1 و v3)', async () => {
  await ownerSale('s-ab', 5000);
  const send = (path, body, ms) => {
    const ctl = new AbortController();
    const p = fetch(`${h.base()}${path}`, {
      method: 'POST', signal: ctl.signal,
      headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${staff.accessToken}` },
      body: JSON.stringify(body),
    }).then((r) => r.text()).catch(() => null);
    setTimeout(() => ctl.abort(), ms);
    return p;
  };
  //  یک برگشتِ بزرگ در جریان است؛ درخواست‌های دیگر وسطِ کار (یا وسطِ صبر) قطع می‌شوند
  const big = save();
  const slow = push1(staff, 's-ab-big', Array.from({ length: 150 }, (_, i) =>
    big.op('returns', `r-ab-big-${i}`, 'insert', { saleId: 's-ab', amount: 1, createdAt: big.at })));
  for (const ms of [0, 2, 10, 30]) {
    const sv = save();
    await send('/api/sync/v1/push', { device_id: `s-ab-${ms}`, schema_version: 1,
      ops: [sv.op('returns', `r-ab-${ms}`, 'insert', { saleId: 's-ab', amount: 10, createdAt: sv.at })] }, ms);
    await send('/api/sync/v3/push', { device_id: `s3-ab-${ms}`, protocol: 3,
      ops: [tx3(undefined).op('returns', `r3-ab-${ms}`, 'insert', { saleId: 's-ab', amountMinor: 1000 })] }, ms);
  }
  const deadline = (p) => Promise.race([p, new Promise((_, rej) => setTimeout(() => rej(new Error('درخواستِ بعدی گیر کرد')), 5000))]);
  assert.equal((await deadline(slow)).status, 200);
  const sv = save();
  assert.equal((await deadline(push1(staff, 's-ab-next', [sv.op('returns', 'r-ab-next', 'insert', { saleId: 's-ab', amount: 10, createdAt: sv.at })]))).status, 200);
  assert.equal((await deadline(push3(staff, 's3-ab-next', [tx3(undefined).op('returns', 'r3-ab-next', 'insert', { saleId: 's-ab', amountMinor: 1000 })]))).status, 200);
});

test('سقفِ تجمعی هر دو دفتر را می‌شمارد (v1 و v3، هر ردیف یک بار) و برگشتِ لغوشده را نه', async () => {
  await ownerSale('s-led', 2000);
  //  ۳۰۰ از راهِ v3
  const t = tx3(undefined);
  assert.deepEqual(why(await push3(staff, 's-led3', [t.op('returns', 'r-led-3', 'insert', { saleId: 's-led', amountMinor: 30000 })])), ['applied']);
  //  ۳۰۰ ِ دیگر از راهِ v1 ⇒ جمع ۶۰۰
  const sv = save();
  assert.deepEqual(why(await push1(staff, 's-led1', [sv.op('returns', 'r-led-1', 'insert', { saleId: 's-led', amount: 300, createdAt: sv.at })])), ['approval_required']);
  //  صاحب برگشتِ v3 را لغو می‌کند ⇒ دیگر شمرده نمی‌شود
  const c = tx3(undefined);
  assert.deepEqual(why(await push3(owner, 'o-led', [c.op('returns', 'r-led-3', 'cancel', undefined, { reason: 'اشتباه' })])), ['applied']);
  const sv2 = save();
  assert.deepEqual(why(await push1(staff, 's-led1', [sv2.op('returns', 'r-led-1b', 'insert', { saleId: 's-led', amount: 300, createdAt: sv2.at })])), ['applied']);
});

test('v3: یک tx با برگشتِ دو فاکتور ⇒ دو سند، هر کدام با سقفِ فاکتورِ خودش', async () => {
  const t = tx3('tx.pf');
  const r = await push3(staff, 's3-pf', [
    t.op('returns', 'r3-pf-a', 'insert', { saleId: 's3-pf-a', amountMinor: 30000 }),
    t.op('returns', 'r3-pf-b', 'insert', { saleId: 's3-pf-b', amountMinor: 30000 }),
  ]);
  assert.deepEqual(why(r), ['applied', 'applied']);
});

test('v3 ِ بی tx: همراهِ دستهٔ بعدِ همان دستگاه با سندِ ردشده برمی‌گردد؛ دستگاهِ دیگر نه', async () => {
  const n3 = tx3(undefined);
  assert.deepEqual(why(await push3(manager, 'm3-pc', [
    n3.op('sales', 's3-pc', 'insert', { totalMinor: 10000, discountMinor: 3000, finalTotalMinor: 7000, status: 'completed', debtorId: 'd-1' }),
  ])), ['approval_required']);
  const r = await push3(manager, 'm3-pc', [
    n3.op('saleItems', 's3-pc-i', 'insert', { saleId: 's3-pc', productId: 'p-gc', quantityMilli: 1000, unitPriceMinor: 10000 }),
    n3.op('transactions', 's3-pc-give', 'insert', { debtorId: 'd-1', type: 'give', amountMinor: 7000, refId: 's3-pc', kind: 'sale_credit' }),
    n3.op('products', 'p3-pc-free', 'insert', { name: 'شکر' }),
  ]);
  assert.deepEqual(why(r), ['tx_rejected', 'tx_rejected', 'applied']);
  assert.deepEqual(why(await push3(manager, 'm3-pc-other', [
    n3.op('saleItems', 's3-pc-i2', 'insert', { saleId: 's3-pc', productId: 'p-gc', quantityMilli: 1000, unitPriceMinor: 10000 }),
  ])), ['applied']);
});

test('v3: دنبالهٔ tx ِ ردشده پس از کنار گذاشتنِ صندوق دیگر نگه داشته نمی‌شود', async () => {
  const t = tx3('tx.dismiss3');
  const head = t.op('sales', 's3-dm', 'insert', { totalMinor: 10000, discountMinor: 3000, finalTotalMinor: 7000, status: 'completed' });
  const item = (k) => t.op('saleItems', `s3-dm-i${k}`, 'insert', { saleId: 's3-dm', productId: 'p-gc', quantityMilli: 1000, unitPriceMinor: 10000 });
  assert.deepEqual(why(await push3(manager, 'm3-dm', [head, item(0)])), ['approval_required', 'tx_rejected']);
  assert.deepEqual(why(await push3(manager, 'm3-dm', [item(1)])), ['tx_rejected']);
  const box = (await boxes(head.op_id))[0];
  assert.equal((await h.post(`/api/sync/v3/conflicts/${box.id}/resolve`, { action: 'dismiss' }, { token: owner.accessToken })).status, 200);
  assert.deepEqual(why(await push3(manager, 'm3-dm', [item(2)])), ['applied']);
});

test('v1: ثبتِ برگشتِ بزرگ‌تر از یک دسته (برگشت‌ها پیش از شمارنده‌ها) — بخشِ اولِ زیرِ سقف، کل بیش از سقف ⇒ بقیه و شمارنده‌ها رد', async () => {
  await ownerSale('s-big', 5000);
  const sv = save();
  //  ۲۳۰ ردیف × ۲٫۵ = ۵۷۵ > ۵۰۰؛ دستهٔ اول ۲۰۰ ردیف = ۵۰۰ (زیرِ سقف)
  const rows = Array.from({ length: 230 }, (_, i) =>
    sv.op('returns', `r-big-${i}`, 'insert', { saleId: 's-big', saleItemId: 's-big-i1', quantity: 0.1, amount: 2.5, createdAt: sv.at }));
  const counters = [
    sv.op('sales', 's-big', 'update', { debtSettled: { $inc: 575 } }),
    sv.op('saleItems', 's-big-i1', 'update', { returnedQty: { $inc: 23 } }),
  ];
  const b1 = await push1(staff, 's-big', rows.slice(0, 200));
  assert.ok(b1.body.results.every((x) => x.status === 'applied'), JSON.stringify(b1.body.results.slice(0, 2)));
  const b2 = await push1(staff, 's-big', [...rows.slice(200), ...counters]);
  assert.ok(why(b2).slice(0, 30).every((x) => x === 'approval_required'), why(b2).join(','));
  assert.deepEqual(why(b2).slice(30), ['tx_rejected', 'tx_rejected']);
  const total = await one(`SELECT COALESCE(SUM((data->>'amount')::numeric),0) AS t FROM sync_rows
    WHERE account_id=$1 AND table_name='returns' AND data->>'saleId'='s-big' AND deleted_at IS NULL`, [shopId]);
  assert.ok(Number(total.t) <= 500, `برگشتِ ثبت‌شده از سقف گذشت: ${total.t}`);
  assert.equal(Number((await row1('sales', 's-big')).data.debtSettled), 0);
});

test('v3: صاحبِ دکان روی همان دستگاه با صندوقِ ردشدهٔ مدیر نگه داشته نمی‌شود', async () => {
  const n3 = tx3(undefined);
  assert.deepEqual(why(await push3(manager, 'shared-pos', [
    n3.op('sales', 's3-own', 'insert', { totalMinor: 10000, discountMinor: 3000, finalTotalMinor: 7000, status: 'completed' }),
  ])), ['approval_required']);
  assert.deepEqual(why(await push3(owner, 'shared-pos', [
    n3.op('saleItems', 's3-own-i', 'insert', { saleId: 's3-own', productId: 'p-gc', quantityMilli: 1000, unitPriceMinor: 10000 }),
  ])), ['applied']);
});
