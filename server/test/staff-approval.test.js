'use strict';
/**
 * سقفِ تخفیف/لغو/برگشت و «برگهٔ امضاشده»ی تأیید — STF-02، STF-03، S07، S08
 * (بخشِ ۱۰.۱). هر دو راه: Sync v3 (`authorize`) و Sync v1 (`v1Guard`).
 *
 * آزمونِ مرجع اول: بی لایه، همان امروز (مدیر تخفیف می‌دهد، شاگرد نه، برگشت
 * برای همه). بعد: opِ بیش از سقف بی برگه رد و در صندوقِ تعارض؛ با برگهٔ
 * معتبر پذیرفته؛ برگهٔ تکراری، دست‌خورده، خودتأیید، تأییدکنندهٔ کم‌اختیار،
 * کلیدِ باطل و برگهٔ کهنه رد.
 */
process.env.SYNC_V3_ENABLED = '1';

const test = require('node:test');
const assert = require('node:assert/strict');
const crypto = require('node:crypto');
const fs = require('node:fs');
const path = require('node:path');
const h = require('./helpers');
const { one, many } = require('../src/db');
const hlc = require('../src/lib/hlc');
const syncV1 = require('../src/lib/sync-v1');
const slipCore = require('../src/lib/approval-core');

const ROOT = path.join(__dirname, '..', '..');
const VEC = JSON.parse(fs.readFileSync(path.join(ROOT, 'core', 'test-vectors', 'approval-v1.json'), 'utf8'));

let shopId;
let owner;
let manager;
let staff;
let n = 0;
const oid = () => `01JAP${String(++n).padStart(21, '0')}`;
let tick = 0;
const H = (node, wall = Date.now() + (++tick)) => hlc.format({ wall, counter: 0, node });
const nonce = () => crypto.randomBytes(12).toString('base64url');

const push3 = (who, device, ops, extra = {}) => h.api('POST', '/api/sync/v3/push', {
  token: who.accessToken, body: { device_id: device, protocol: 3, ops, ...extra },
});
const push1 = (who, device, ops) => h.api('POST', '/api/sync/v1/push', {
  token: who.accessToken, body: { device_id: device, schema_version: 1, ops },
});
const row3 = (table, id) => one(
  `SELECT * FROM sync_v3_rows WHERE app='shop' AND account_id=$1 AND table_name=$2 AND row_id=$3`, [shopId, table, id]
);
const boxOf = (opId) => one(
  `SELECT * FROM sync_v3_conflicts WHERE account_id=$1 AND loser_op_id=$2 AND status='open'`, [shopId, opId]
);
const setRole = (role, perms) => h.put(`/api/shop/permissions/roles/${role}`, { perms }, { token: owner.accessToken });

/** کلیدِ ES256ِ یک تأییدکننده، ثبت‌شده در سرور */
async function approverKey(who, label) {
  const { privateKey, publicKey } = crypto.generateKeyPairSync('ec', { namedCurve: 'prime256v1' });
  const spki = publicKey.export({ format: 'der', type: 'spki' }).toString('base64');
  const r = await h.post('/api/shop/approver-keys', { public_key: spki, label, device_id: 'pos-1' }, { token: who.accessToken });
  return { r, priv: privateKey, keyId: r.body?.key?.id, userId: who.user.id };
}

function slip(signer, { doc, perm, pct_bp = null, amount = null, iat = Date.now() - 1000, ttl = 10 * 60_000,
  reason = 'مشتریِ همیشگی', encoding = 'der' }) {
  const s = { v: 1, shop: shopId, doc, perm, pct_bp, amount, by: signer.userId, key: signer.keyId,
    iat, exp: iat + ttl, nonce: nonce(), reason };
  s.sig = crypto.sign('sha256', Buffer.from(slipCore.payload(s), 'utf8'),
    { key: signer.priv, dsaEncoding: encoding }).toString('base64');
  return s;
}

const sale = (id, total, discount, extra = {}) => ({
  op_id: oid(), hlc: H('pos'), table: 'sales', row_id: id, kind: 'insert',
  fields: { totalMinor: total, discountMinor: discount, finalTotalMinor: total - discount, status: 'completed' }, ...extra,
});

let ownerKey;
let managerKey;

test.before(async () => {
  await h.start();
  await syncV1.syncSchemaTable();
  const o = await h.newUser('صاحبِ دکانِ تأیید', 'shop');
  const shop = await h.post('/api/shop', { name: 'دکانِ تأیید' }, { token: o.accessToken });
  shopId = shop.body?.shop?.id || shop.body?.id;
  const code = (await h.get('/api/shop/staff-code', { token: o.accessToken })).body.code;
  const s = await h.newUser('شاگردِ تأیید', 'shop');
  assert.ok([200, 201].includes((await h.post('/api/shop/join', { code }, { token: s.accessToken })).status));
  const inv = await h.post('/api/shop/invite', { role: 'manager' }, { token: o.accessToken });
  const m = await h.newUser('مدیرِ تأیید', 'shop');
  assert.ok([200, 201].includes((await h.post('/api/shop/join', { code: inv.body.code }, { token: m.accessToken })).status));
  owner = await h.signIn(o, 'shop');
  manager = await h.signIn(m, 'shop');
  staff = await h.signIn(s, 'shop');
  ownerKey = await approverKey(owner, 'صندوقِ ۱');
  managerKey = await approverKey(manager, 'گوشیِ مدیر');
});
test.after(async () => { await h.stop(); });

/* ------------------------------------------------- بردارها و هسته */

test('هسته: نسخهٔ سرور عیناً نسخهٔ وب است و بردارهای approval-v1 را می‌گذراند', () => {
  assert.equal(
    fs.readFileSync(path.join(__dirname, '..', 'src', 'lib', 'approval-core.js'), 'utf8'),
    fs.readFileSync(path.join(ROOT, 'license', 'approval-core.js'), 'utf8')
  );
  const A = require(path.join(ROOT, 'license', 'approval-core.js'));
  assert.equal(A.VERSION, VEC.version);
  assert.equal(A.TTL_MAX_MS, VEC.ttl_max_ms);
  assert.equal(A.SKEW_MS, VEC.skew_ms);
  for (const c of VEC.payload) assert.equal(A.payload(c.slip), c.out);
  for (const c of VEC.check) assert.equal(A.check(c.slip), c.out, JSON.stringify(c));
  for (const c of VEC.covers) assert.equal(A.covers(c.slip, c.need), c.out, JSON.stringify(c));
});

test('امضای بردارها: DER (اندروید/JVM) و خام (WebCrypto) هر دو، و متنِ دست‌خورده نه', () => {
  const approvals = require('../src/lib/staff-approval');
  const k = VEC.test_key;
  assert.equal(approvals.keyIdOf(Buffer.from(k.spki_b64, 'base64')), k.key_id);
  for (const s of VEC.signatures) {
    const text = VEC.payload[s.payload].out;
    assert.equal(approvals.verifySignature(k.spki_b64, text, s.der_b64), true);
    assert.equal(approvals.verifySignature(k.spki_b64, text, s.p1363_b64), true);
    assert.equal(approvals.verifySignature(k.spki_b64, text + ' ', s.der_b64), false);
    assert.equal(approvals.verifySignature(k.spki_b64, text.replace('\nreason=', '\nreason=x'), s.p1363_b64), false);
    assert.equal(approvals.verifySignature(k.spki_b64, text.replace('\namount=', '\namount=9'), s.der_b64), false);
  }
});

/* --------------------------------------------------- کلیدِ تأیید */

test('کلید: فقط صاحب و مدیر، فقط P-256، و تکرارِ ثبت همان کلید را برمی‌گرداند', async () => {
  assert.equal(ownerKey.r.status, 201, JSON.stringify(ownerKey.r.body));
  assert.match(ownerKey.keyId, /^ak_[0-9a-f]{24}$/);
  const st = await approverKey(staff, 'شاگرد');
  assert.equal(st.r.status, 403);
  assert.equal(st.r.body.error.code, 'not_approver');
  const rsa = crypto.generateKeyPairSync('rsa', { modulusLength: 1024 }).publicKey.export({ format: 'der', type: 'spki' });
  const bad = await h.post('/api/shop/approver-keys', { public_key: rsa.toString('base64') }, { token: owner.accessToken });
  assert.equal(bad.body.error.code, 'bad_public_key');
  //  شاگرد کلیدِ کسی را نمی‌بیند؛ صاحب همه را
  const mine = await h.get('/api/shop/approver-keys', { token: manager.accessToken });
  assert.deepEqual(mine.body.keys.map((k) => k.id), [managerKey.keyId]);
  const all = await h.get('/api/shop/approver-keys', { token: owner.accessToken });
  assert.equal(all.body.keys.length, 2);
});

/* ------------------------------------------------------ آزمونِ مرجع */

test('آزمونِ مرجع: بی لایه همان امروز — مدیر تخفیفِ ۵۰٪ می‌دهد، شاگرد نه، برگشت برای همه', async () => {
  const a = await push3(manager, 'm-ref', [sale('s-ref-m', 10000, 5000)]);
  assert.equal(a.body.results[0].status, 'applied', JSON.stringify(a.body));
  const b = await push3(staff, 's-ref', [sale('s-ref-s', 10000, 100)]);
  assert.equal(b.body.results[0].reason, 'discount_not_allowed');
  const r = await push3(staff, 's-ref', [{ op_id: oid(), hlc: H('s'), table: 'returns', row_id: 'r-ref', kind: 'insert',
    fields: { amountMinor: 900000, saleId: 's-ref-m' } }]);
  assert.equal(r.body.results[0].status, 'applied', JSON.stringify(r.body));
  assert.equal((await many('SELECT 1 FROM shop_approvals WHERE shop_id=$1', [shopId])).length, 0);
});

/* ------------------------------------- لایه‌ها حالا ذخیره می‌شوند */

test('صاحب سقفِ تخفیف/لغو/برگشت را ذخیره می‌کند؛ شاگرد «نیازمند تأیید» می‌گیرد ولی «مجاز» نه', async () => {
  const put = await setRole('manager', { 'sales.discount': { caps: { maxPct: 10, maxAmount: 100000 } } });
  assert.equal(put.status, 200, JSON.stringify(put.body));
  const st = await setRole('staff', { 'sales.discount': { mode: 'approve' }, 'returns.create': { caps: { maxAmount: 50000 } } });
  assert.equal(st.status, 200, JSON.stringify(st.body));
  const grant = await setRole('staff', { 'sales.discount': { mode: 'allow' } });
  assert.equal(grant.body.error.code, 'exceeds_role');
  const shop = await h.get('/api/shop', { token: staff.accessToken });
  assert.equal(shop.body.policy.effective['sales.discount'].mode, 'approve');
  assert.ok(shop.body.policy.editable.includes('sales.cancel'));
  //  فهرستِ قدیمیِ permissions هنوز همان امروز است (شاگرد تخفیف ندارد)
  assert.ok(!shop.body.permissions.includes('sales.discount'));
});

/* ------------------------------------------------------------ S08 */

test('S08: تخفیفِ بیش از سقفِ مدیر بی برگه ⇒ رد و صندوقِ تعارض؛ زیرِ سقف ⇒ می‌نشیند', async () => {
  const over = sale('s-08-over', 10000, 2000);
  const r = await push3(manager, 'm-08', [over]);
  assert.equal(r.body.results[0].status, 'rejected');
  assert.equal(r.body.results[0].reason, 'approval_required');
  assert.equal(r.body.results[0].box, undefined, 'جزئیاتِ صندوق در پاسخ نمی‌آید');
  assert.equal(await row3('sales', 's-08-over'), null);
  const box = await boxOf(over.op_id);
  assert.equal(box.kind, 'approval_required');
  assert.equal(box.detail.perm, 'sales.discount');
  assert.equal(box.detail.pct_bp, 1950);
  assert.equal(box.detail.cap, 'maxPct');
  //  دوباره فرستادن صندوق را دو تا نمی‌کند
  await push3(manager, 'm-08', [over]);
  assert.equal((await many(`SELECT 1 FROM sync_v3_conflicts WHERE account_id=$1 AND loser_op_id=$2`, [shopId, over.op_id])).length, 1);

  const ok = await push3(manager, 'm-08', [sale('s-08-ok', 10000, 1000)]);
  assert.equal(ok.body.results[0].status, 'applied');
  //  سقفِ مبلغ هم: ۹٪ِ ۲۰٬۰۰۰ افغانی = ۱۸۰۰ افغانی > ۱۰۰۰
  const amt = await push3(manager, 'm-08', [sale('s-08-amt', 2_000_000, 180_000)]);
  assert.equal(amt.body.results[0].reason, 'approval_required');
});

test('S08: با برگهٔ معتبرِ صاحب دکان ⇒ می‌نشیند و سابقه نامِ تأییدکننده و دلیل را دارد', async () => {
  const s = slip(ownerKey, { doc: 'sales/s-08-ap', perm: 'sales.discount', pct_bp: 2000, amount: 2000, reason: 'مشتریِ عمده' });
  const op = sale('s-08-ap', 10000, 2000, { approval: s });
  const r = await push3(manager, 'm-08', [op]);
  assert.equal(r.body.results[0].status, 'applied', JSON.stringify(r.body));
  assert.ok(await row3('sales', 's-08-ap'));
  const list = await h.get('/api/shop/approvals', { token: owner.accessToken });
  const a = list.body.approvals.find((x) => x.doc === 'sales/s-08-ap');
  assert.equal(a.perm, 'sales.discount');
  assert.equal(a.approverId, owner.user.id);
  assert.equal(a.requesterId, manager.user.id);
  assert.equal(a.reason, 'مشتریِ عمده');
  assert.equal(a.opId, op.op_id);
  assert.ok(a.approverName);
  assert.equal((await h.get('/api/shop/approvals', { token: staff.accessToken })).status, 403);

  //  همان op دوباره (پاسخ گم شد) ⇒ «تکراری»، نه بازپخش
  const again = await push3(manager, 'm-08', [op]);
  assert.equal(again.body.results[0].status, 'duplicate');
});

test('برگهٔ تکراری (بازپخش) روی opِ دیگر ⇒ رد و صندوق', async () => {
  const s = slip(ownerKey, { doc: 'sales/s-rp', perm: 'sales.discount', pct_bp: 2000, amount: 2000 });
  const first = await push3(manager, 'm-rp', [sale('s-rp', 10000, 2000, { approval: s })]);
  assert.equal(first.body.results[0].status, 'applied');
  //  همان برگه، همان سند، opِ دیگر (مثلاً دستگاهِ دیگری که برگه را کپی کرده)
  const replay = sale('s-rp', 10000, 2000, { approval: s });
  const r = await push3(manager, 'm-rp2', [replay]);
  assert.equal(r.body.results[0].reason, 'approval_replayed');
  assert.equal((await boxOf(replay.op_id)).kind, 'approval_rejected');
  //  و روی سندِ دیگر: «نمی‌پوشاند»
  const other = await push3(manager, 'm-rp2', [sale('s-rp-2', 10000, 2000, { approval: s })]);
  assert.equal(other.body.results[0].reason, 'approval_mismatch');
});

test('برگهٔ نامعتبر رد می‌شود: دست‌خورده، خودتأیید، تأییدکنندهٔ کم‌اختیار، کلیدِ باطل، کهنه، کوچک', async () => {
  const cases = [];
  //  دست‌خورده: سقف را بعد از امضا بالا برده‌اند
  const t = slip(ownerKey, { doc: 'sales/s-bad-1', perm: 'sales.discount', pct_bp: 1500, amount: 1500 });
  cases.push(['s-bad-1', 10000, 3000, { ...t, pct_bp: 3000, amount: 3000 }, 'approval_bad_signature']);
  //  مدیر خودش را تأیید نمی‌کند
  cases.push(['s-bad-2', 10000, 2000,
    slip(managerKey, { doc: 'sales/s-bad-2', perm: 'sales.discount', pct_bp: 2000, amount: 2000 }), 'approval_self']);
  //  کهنه: op دیرتر از exp + ۵ دقیقه
  cases.push(['s-bad-3', 10000, 2000, slip(ownerKey, { doc: 'sales/s-bad-3', perm: 'sales.discount', pct_bp: 2000,
    amount: 2000, iat: Date.now() - 30 * 60_000 }), 'approval_expired']);
  //  کوچک: برگهٔ ۱۵٪ برای ۲۰٪
  cases.push(['s-bad-4', 10000, 2000,
    slip(ownerKey, { doc: 'sales/s-bad-4', perm: 'sales.discount', pct_bp: 1500, amount: 2000 }), 'approval_too_small']);
  //  شکلِ بد
  cases.push(['s-bad-5', 10000, 2000, { v: 1, shop: shopId }, 'approval_invalid']);
  for (const [id, total, d, s, reason] of cases) {
    const op = sale(id, total, d, { approval: s });
    const r = await push3(manager, 'm-bad', [op]);
    assert.equal(r.body.results[0].reason, reason, id);
    assert.equal(await row3('sales', id), null, id);
    assert.equal((await boxOf(op.op_id)).kind, 'approval_rejected', id);
  }

  //  شاگرد «نیازمند تأیید» دارد؛ مدیر با سقفِ ۱۰٪ تأییدِ ۲۰٪ نمی‌دهد ولی ۵٪ را می‌دهد
  const weak = await push3(staff, 's-bad', [sale('s-bad-6', 10000, 2000, {
    approval: slip(managerKey, { doc: 'sales/s-bad-6', perm: 'sales.discount', pct_bp: 2000, amount: 2000 }) })]);
  assert.equal(weak.body.results[0].reason, 'approver_not_allowed');
  const fine = await push3(staff, 's-bad', [sale('s-ok-6', 10000, 500, {
    approval: slip(managerKey, { doc: 'sales/s-ok-6', perm: 'sales.discount', pct_bp: 500, amount: 500 }) })]);
  assert.equal(fine.body.results[0].status, 'applied', JSON.stringify(fine.body));
  //  شاگرد بی برگه
  const none = await push3(staff, 's-bad', [sale('s-bad-7', 10000, 100)]);
  assert.equal(none.body.results[0].reason, 'approval_required');

  //  کلیدِ باطل‌شده
  const k = await approverKey(owner, 'دستگاهِ گم‌شده');
  const s = slip(k, { doc: 'sales/s-bad-8', perm: 'sales.discount', pct_bp: 2000, amount: 2000 });
  assert.equal((await h.del(`/api/shop/approver-keys/${k.keyId}`, { token: owner.accessToken })).status, 200);
  const rv = await push3(manager, 'm-bad', [sale('s-bad-8', 10000, 2000, { approval: s })]);
  assert.equal(rv.body.results[0].reason, 'approval_key_unknown');
});

test('امضای خامِ WebCrypto (۶۴ بایت) هم پذیرفته است', async () => {
  const s = slip(ownerKey, { doc: 'sales/s-web', perm: 'sales.discount', pct_bp: 2000, amount: 2000, encoding: 'ieee-p1363' });
  const r = await push3(manager, 'm-web', [sale('s-web', 10000, 2000, { approval: s })]);
  assert.equal(r.body.results[0].status, 'applied', JSON.stringify(r.body));
});

test('tx: فروشِ بیش از سقف بی برگه ⇒ کلِ tx برمی‌گردد ولی صندوق می‌ماند', async () => {
  const tx = 'tx-ap-1';
  const s = sale('s-tx-ap', 10000, 3000, { tx });
  const r = await push3(manager, 'm-tx', [
    { op_id: oid(), tx, hlc: H('pos'), table: 'stockMovements', row_id: 'm-tx-ap', kind: 'insert', fields: { productId: 'p-ap', qtyMilli: -1000 } },
    s,
  ]);
  assert.deepEqual(r.body.results.map((x) => x.reason), ['tx_rejected', 'approval_required']);
  assert.equal(await row3('stockMovements', 'm-tx-ap'), null);
  assert.equal((await boxOf(s.op_id)).kind, 'approval_required');
});

/* ------------------------------------------------- لغو و برگشت */

test('لغوِ فاکتور با «نیازمند تأیید»: بی برگه رد، با برگه می‌نشیند؛ برگهٔ لغو بازپخش نمی‌شود', async () => {
  assert.equal((await setRole('manager', { 'sales.cancel': { mode: 'approve' } })).status, 200);
  await push3(owner, 'o-c', [sale('s-c-1', 10000, 0)]);
  const bare = { op_id: oid(), hlc: H('pos'), table: 'sales', row_id: 's-c-1', kind: 'cancel', reason: 'اشتباه' };
  const r = await push3(manager, 'm-c', [bare]);
  assert.equal(r.body.results[0].reason, 'approval_required');
  assert.equal((await row3('sales', 's-c-1')).data.status, 'completed');
  const s = slip(ownerKey, { doc: 'sales/s-c-1', perm: 'sales.cancel', reason: 'اشتباهِ صندوق' });
  const ok = await push3(manager, 'm-c', [{ ...bare, op_id: oid(), hlc: H('pos'), approval: s }]);
  assert.equal(ok.body.results[0].status, 'applied', JSON.stringify(ok.body));
  assert.equal((await row3('sales', 's-c-1')).data.status, 'cancelled');
  const again = await push3(manager, 'm-c2', [{ ...bare, op_id: oid(), hlc: H('pos'), approval: s }]);
  assert.equal(again.body.results[0].reason, 'approval_replayed');
  assert.equal((await setRole('manager', {})).status, 200);
});

test('برگشت با سقفِ مبلغ: بیش از سقف بی برگه رد، زیرِ سقف و با برگه می‌نشیند', async () => {
  const ret = (id, amount, extra = {}) => ({ op_id: oid(), hlc: H('s'), table: 'returns', row_id: id, kind: 'insert',
    fields: { amountMinor: amount, saleId: 's-ref-m' }, ...extra });
  const over = ret('r-over', 60000);
  assert.equal((await push3(staff, 's-r', [over])).body.results[0].reason, 'approval_required');
  assert.equal((await boxOf(over.op_id)).detail.perm, 'returns.create');
  assert.equal((await push3(staff, 's-r', [ret('r-under', 40000)])).body.results[0].status, 'applied');
  const s = slip(ownerKey, { doc: 'returns/r-ap', perm: 'returns.create', amount: 60000, reason: 'کالای خراب' });
  assert.equal((await push3(staff, 's-r', [ret('r-ap', 60000, { approval: s })])).body.results[0].status, 'applied');
});

/* ------------------------------------------------------- v2 و v1 */

test('صفِ v2 از راهِ v3: لغو با fields.approval ⇒ برگه همراهِ op، نه «ویرایشِ سند»', async () => {
  assert.equal((await setRole('manager', { 'sales.cancel': { mode: 'approve' } })).status, 200);
  await push3(owner, 'o-v2', [sale('s-v2-c', 5000, 0)]);
  const s = slip(ownerKey, { doc: 'sales/s-v2-c', perm: 'sales.cancel', reason: 'مشتری پشیمان شد' });
  const v2 = { op_id: oid(), table: 'sales', row_id: 's-v2-c', type: 'update', ts: Date.now(),
    fields: { status: 'cancelled', cancelReason: 'پشیمانی', approval: s } };
  const r = await push3(manager, 'm-v2', [v2], { format: 'v2', schema_version: 1 });
  assert.equal(r.body.results[0].status, 'applied', JSON.stringify(r.body));
  assert.equal((await row3('sales', 's-v2-c')).data.status, 'cancelled');
  assert.equal((await many(`SELECT 1 FROM sync_v3_conflicts WHERE account_id=$1 AND row_id='s-v2-c' AND kind='append_only_edit'`,
    [shopId])).length, 0);
  const bare = await push3(manager, 'm-v2', [{ ...v2, op_id: oid(), row_id: 's-v2-c', fields: { status: 'cancelled' } }],
    { format: 'v2', schema_version: 1 });
  assert.equal(bare.body.results[0].reason, 'approval_required');
  assert.equal((await setRole('manager', {})).status, 200);
});

test('Sync v1: بیش از سقف بی برگه در جای خودش «rejected»، بقیهٔ دسته می‌نشیند؛ با برگه می‌نشیند؛ بازپخش نه', async () => {
  assert.equal((await setRole('manager', { 'sales.discount': { caps: { maxPct: 10 } } })).status, 200);
  const v1sale = (id, total, discount, extra = {}) => ({ op_id: oid(), table: 'sales', row_id: id, type: 'insert',
    ts: Date.now(), fields: { total, discount, finalTotal: total - discount, status: 'completed', ...extra } });
  const before = { op_id: oid(), table: 'products', row_id: 'p-v1-a', type: 'insert', ts: Date.now(), fields: { name: 'چای' } };
  const over = v1sale('s-v1-over', 100, 30);
  const after = { op_id: oid(), table: 'products', row_id: 'p-v1-b', type: 'insert', ts: Date.now(), fields: { name: 'قند' } };
  const r = await push1(manager, 'm-v1', [before, over, after]);
  assert.equal(r.status, 200, JSON.stringify(r.body));
  assert.deepEqual(r.body.results.map((x) => x.op_id), [before.op_id, over.op_id, after.op_id]);
  assert.deepEqual(r.body.results.map((x) => x.status), ['applied', 'rejected', 'applied']);
  assert.equal(r.body.results[1].reason, 'approval_required');
  assert.equal(await one(`SELECT 1 FROM oplog WHERE op_id=$1`, [over.op_id]), null);
  assert.equal((await boxOf(over.op_id)).detail.path, 'v1');

  const s = slip(ownerKey, { doc: 'sales/s-v1-ap', perm: 'sales.discount', pct_bp: 3000, amount: 3000 });
  const ap = v1sale('s-v1-ap', 100, 30, { approval: s });
  const ok = await push1(manager, 'm-v1', [ap]);
  assert.equal(ok.body.results[0].status, 'applied', JSON.stringify(ok.body));
  //  همان op دوباره ⇒ تکراریِ خودِ v1، نه بازپخش
  assert.notEqual((await push1(manager, 'm-v1', [ap])).body.results[0].reason, 'approval_replayed');
  const replay = await push1(manager, 'm-v1b', [{ ...ap, op_id: oid() }]);
  assert.equal(replay.body.results[0].reason, 'approval_replayed');
  //  زیرِ سقف و شاگرد (همان قاعدهٔ امروزِ v1)
  assert.equal((await push1(manager, 'm-v1', [v1sale('s-v1-ok', 100, 5)])).body.results[0].status, 'applied');
  assert.equal((await push1(staff, 's-v1', [v1sale('s-v1-st', 100, 5)])).body.results[0].reason, 'discount_not_allowed');
  //  شاگردِ «نیازمند تأیید» با برگه از راهِ v1: دلیلِ روشن (v1ِ پمپ‌مشترک نقش را می‌سنجد)؛ برگه مصرف نمی‌شود
  const ss = slip(ownerKey, { doc: 'sales/s-v1-st2', perm: 'sales.discount', pct_bp: 500, amount: 500 });
  const st2 = v1sale('s-v1-st2', 100, 5, { approval: ss });
  assert.equal((await push1(staff, 's-v1', [st2])).body.results[0].reason, 'approval_needs_v3');
  assert.equal((await boxOf(st2.op_id)).detail.slip_error, 'approval_needs_v3');
  assert.equal(await one('SELECT 1 FROM shop_approvals WHERE shop_id=$1 AND nonce=$2', [shopId, ss.nonce]), null);
  assert.equal((await setRole('manager', {})).status, 200);
});
