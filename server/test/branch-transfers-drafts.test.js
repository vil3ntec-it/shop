'use strict';
/**
 * پایانِ فازِ ۴ ِ شعبه‌ها (مهاجرتِ ۹۴۶، `docs/BRANCHES-fa.md` §۱۰) — از راهِ واقعی (HTTP):
 *   ⛔ ماشینِ وضعیتِ کامل: پیش‌نویس (بی اثرِ موجودی، فقط فرستنده می‌بیند) ⇒ ویرایش ⇒ ارسال ⇒ در مسیر ⇒ دریافت/رد؛ لغوِ پیش‌نویس
 *      (بی اثر) و لغوِ «در مسیر» ِ هیچ‌نرسیده (همه به مبدأ، دلیل اجباری)؛ نیمه‌رسیده لغو نمی‌شود؛ بسته‌ها پایانی (کد و تریگر).
 *   ⛔ هر گام یکتا (`Idempotency-Key`)، یک تراکنش، سابقه؛ لغو و دریافتِ هم‌زمان ⇒ دقیقاً یک نتیجه.
 *   ⛔ جدایی: شعبهٔ مقصد پیش‌نویس را نمی‌بیند (۴۰۴)، شعبهٔ سوم هیچ، مقصد گامِ فرستنده نمی‌زند (۴۰۳ `transfer_not_sender`)،
 *      سرآیندِ جعلی ۴۰۳، دکانِ دیگر ۴۰۴.
 *   ⛔ موجودیِ منفی فقط با کلیدِ صاحب (`shop_branch_settings.transfer_negative`) و هرگز بی‌صدا (وظیفهٔ `negative_stock`).
 *   ⛔ آینهٔ دفترِ دستگاه: ردیفِ «انتقالِ میانِ شعبه» ِ `warehouseEntries` (نشان + انتقالِ واقعیِ همین شعبه) دو بار شمرده نمی‌شود؛
 *      نشانِ ساختگی سهمِ عادی دارد.
 *   ⛔ مهاجرتِ ۹۴۶: با پیش‌نویسِ باز برگشت رد؛ بی آن پایین (لغوشده‌ها پاک، فرستاده‌ها همان ۹۳۵)، دوباره بالا.
 */
process.env.SYNC_V3_ENABLED = '1';
process.env.RATE_LOGIN_EDGE_MAX = process.env.RATE_LOGIN_EDGE_MAX || '100000';

const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const h = require('./helpers');
const { query, one, many, newId, now } = require('../src/db');
const { resetAll } = require('../src/middleware/ratelimit');
const hlc = require('../src/lib/hlc');

let admin = '';
test.before(async () => {
  await h.start();
  const pw = require('../src/lib/password');
  await query(
    `INSERT INTO admins (id, username, name, password_hash, role, status, created_at)
     VALUES ($1,'admin','مدیر',$2,'superadmin','active',$3)`,
    [newId('adm'), await pw.hashPassword('Admin!12345'), now()]
  );
  admin = (await h.post('/api/admin/login', { username: 'admin', password: 'Admin!12345' })).body.token;
  assert.ok(admin, 'ورودِ مدیر');
});
test.after(async () => { await h.stop(); });
test.beforeEach(() => resetAll());

// ── کمکی‌ها (همان `branch-transfers.test.js`) ───────────────────────────

async function shop(name) {
  const u = await h.newUser(name, 'shop');
  const made = await h.post('/api/shop', { name: `دکانِ ${name}` }, { token: u.accessToken });
  assert.equal(made.status, 201, JSON.stringify(made.body));
  const s = await h.signIn(u, 'shop');
  return { ...s, token: s.accessToken, shopId: made.body.shop.id, device: `dev-o-${made.body.shop.id}`.slice(0, 60) };
}
async function member(s, role, name) {
  const u = await h.newUser(name, 'shop');
  const id = newId('mem');
  await query(
    `INSERT INTO shop_members (id, shop_id, user_id, role, status, created_at, updated_at) VALUES ($1,$2,$3,$4,'active',$5,$5)`,
    [id, s.shopId, u.user.id, role, now()]
  );
  return { ...u, memberId: id, userId: u.user.id, token: u.accessToken, device: `dev-m-${u.user.id}`.slice(0, 60) };
}
async function flag(s, key, value = true) {
  const r = await h.put('/api/admin/pump/live-config', { scope: s.shopId, key, value }, { token: admin });
  assert.equal(r.status, 200, JSON.stringify(r.body));
}
const B = (branch) => (branch === undefined ? {} : { 'X-Branch-Id': branch });
let opN = 0;
const H = () => hlc.format({ wall: Date.now() - 600_000 + (++opN) * 10, counter: 0, node: 'brdraft' });
const v3op = (table, rowId, fields) => ({ op_id: `01JBD${String(Date.now()).slice(-6)}${String(++opN).padStart(15, '0')}`, table, row_id: rowId, kind: 'insert', hlc: H(), fields });
const product3 = (id) => v3op('products', id, { name: `کالای ${id}`, unit: 'دانه', salePriceMinor: 5000, purchasePriceMinor: 4000, active: true });
const entry3 = (id, pid, units, extra = {}) => v3op('warehouseEntries', id, { productId: pid, unitsMilli: units * 1000, createdAt: Date.now(), ...extra });
async function pushV3(who, ops, branch) {
  const r = await h.api('POST', '/api/sync/v3/push', { token: who.token, body: { device_id: who.device, protocol: 3, ops }, headers: B(branch) });
  assert.equal(r.status, 200, JSON.stringify(r.body));
  for (const x of r.body.results || []) assert.notEqual(x.status, 'rejected', JSON.stringify(x));
}
let keyN = 0;
const fresh = () => `bxdraft-key-${++keyN}-${Date.now()}`;
const cat = (who, p, branch, { method = 'GET', body = null, key, headers = {} } = {}) => h.api(method, `/api/shop/catalog${p}`, {
  token: who.token, body, headers: { ...B(branch), ...(key ? { 'Idempotency-Key': key } : {}), ...headers },
});
const bx = (who, p, branch, o = {}) => cat(who, `/branch-transfers${p}`, branch, o);
const send = (who, branch, body, key = fresh()) => bx(who, '', branch, { method: 'POST', body, key });
const edit = (who, branch, id, body, key = fresh()) => bx(who, `/${id}`, branch, { method: 'PUT', body, key });
const sendDraft = (who, branch, id, key = fresh()) => bx(who, `/${id}/send`, branch, { method: 'POST', body: {}, key });
const cancel = (who, branch, id, body = {}, key = fresh()) => bx(who, `/${id}/cancel`, branch, { method: 'POST', body, key });
const receive = (who, branch, id, body, key = fresh()) => bx(who, `/${id}/receive`, branch, { method: 'POST', body, key });

async function levels(shopId, branchId, pid) {
  const rows = await many('SELECT location, on_hand_milli FROM stock_levels WHERE shop_id=$1 AND branch_id=$2 AND product_id=$3', [shopId, branchId, pid]);
  return Object.fromEntries(rows.filter((r) => Number(r.on_hand_milli) !== 0).map((r) => [r.location, Number(r.on_hand_milli)]));
}

/** دکانِ v3 با موجودیِ سرور و سه شعبه؛ الف ۱۰ دانه p1 و ۵ دانه p2 روی قفسه */
async function world(tag) {
  const s = await shop(`پیش‌نویسِ شعبه ${tag}`);
  await flag(s, 'shop.catalog');
  assert.equal((await cat(s, '/settings', undefined, { method: 'PUT', body: { syncSource: 'v3' } })).status, 200);
  await pushV3(s, [product3('p1'), product3('p2'), entry3('m-e1', 'p1', 4)]);
  assert.equal((await cat(s, '/products/p1')).status, 200);
  const w = { s, staffA: await member(s, 'staff', `کارمندِ الف ${tag}`), mgrA: await member(s, 'manager', `مدیرِ الف ${tag}`),
    mgrB: await member(s, 'manager', `مدیرِ ب ${tag}`), mgrC: await member(s, 'manager', `مدیرِ ج ${tag}`),
    cashierA: await member(s, 'cashier', `صندوق‌دارِ الف ${tag}`) };
  await flag(s, 'shop.branches');
  for (const [k, name] of [['A', 'الف'], ['Bid', 'ب'], ['C', 'ج']]) {
    const r = await h.post('/api/shop/branches', { name }, { token: s.token });
    assert.equal(r.status, 201, JSON.stringify(r.body));
    w[k] = r.body.branch.id;
  }
  for (const [m, id] of [[w.staffA, w.A], [w.mgrA, w.A], [w.mgrB, w.Bid], [w.mgrC, w.C], [w.cashierA, w.A]]) {
    assert.equal((await h.put(`/api/shop/branches/members/${m.memberId}`, { branches: [id] }, { token: s.token })).status, 200);
  }
  await pushV3(w.mgrA, [entry3('a-e1', 'p1', 10), entry3('a-e2', 'p2', 5)], w.A);
  assert.equal((await cat(w.mgrA, '/products/p1', w.A)).body.product.stock.levels.shop_floor, 10000);
  return w;
}
const body1 = (w, extra = {}) => ({ toBranch: w.Bid, from: 'shop_floor', to: 'warehouse', lines: [{ productId: 'p1', qtyMilli: 3000 }], note: 'بارِ یک', ...extra });
const movesOf = (shopId) => many('SELECT * FROM stock_moves WHERE shop_id=$1 ORDER BY id', [shopId]);

// ── پرچمِ خاموش ───────────────────────────────────────────────────────

test('پرچمِ خاموش ⇒ درهای تازهٔ پیش‌نویس/ارسال/لغو همان درِ ناشناخته؛ تنظیم‌های شعبه هم', async () => {
  const s = await shop('پیش‌نویس خاموش');
  await flag(s, 'shop.catalog');
  const id = 'bxf_000000000000000000000000';
  for (const [m, p] of [['PUT', `/${id}`], ['POST', `/${id}/send`], ['POST', `/${id}/cancel`]]) {
    const r = await bx(s, p, undefined, { method: m, body: {}, key: fresh() });
    const u = await cat(s, '/zz-no-such-door', undefined, { method: m, body: {}, key: fresh() });
    assert.deepEqual([r.status, r.body], [u.status, u.body], `${m} ${p}`);
    assert.equal(r.status, 404);
  }
  for (const m of ['GET', 'PUT']) {
    const r = await h.api(m, '/api/shop/branches/settings', { token: s.token, body: m === 'PUT' ? { staffOtherStock: true } : null });
    assert.equal(r.status, 404, `${m} settings`);
  }
  assert.equal((await one('SELECT COUNT(*)::int AS n FROM shop_branch_settings WHERE shop_id=$1', [s.shopId])).n, 0);
});

// ── پیش‌نویس ⇒ ویرایش ⇒ ارسال ─────────────────────────────────────────

test('پیش‌نویس: بی اثرِ موجودی و فقط برای فرستنده؛ ویرایش یکتا؛ ارسال همان اثرِ ارسالِ مستقیم؛ دوباره نه', async () => {
  const w = await world('راه');
  const { s, A, Bid, C } = w;
  const before = await movesOf(s.shopId);
  const d = await send(w.staffA, A, body1(w, { draft: true }));
  assert.equal(d.status, 201, JSON.stringify(d.body));
  const t = d.body.transfer;
  assert.equal(t.status, 'draft');
  assert.equal(t.sentAt, null);
  assert.equal(t.sentBy, '');
  assert.equal(t.pendingMilli, 0);
  assert.deepEqual(await movesOf(s.shopId), before, 'پیش‌نویس هیچ حرکتی ندارد');
  assert.deepEqual(await levels(s.shopId, A, 'p1'), { shop_floor: 10000 });
  //  فقط فرستنده: الف در فهرستِ «از این شعبه» و با status=draft؛ ب و ج هیچ (فهرست خالی، شناسه ⇒ ۴۰۴)
  assert.deepEqual((await bx(w.mgrA, '?direction=out&status=draft', A)).body.transfers.map((x) => x.id), [t.id]);
  assert.deepEqual((await bx(w.mgrB, '', Bid)).body.transfers, []);
  assert.deepEqual((await bx(w.mgrB, '?status=draft', Bid)).body.transfers, []);
  for (const [who, br] of [[w.mgrB, Bid], [w.mgrC, C]]) {
    for (const r of [await bx(who, `/${t.id}`, br), await edit(who, br, t.id, body1(w)), await sendDraft(who, br, t.id), await cancel(who, br, t.id)]) {
      assert.equal(r.status, 404, JSON.stringify(r.body));
      assert.equal(r.body.error.code, 'transfer_not_found');
    }
  }
  //  ویرایش: مقصد، مکان‌ها و ردیف‌ها تا ارسال عوض می‌شوند؛ همان کلید + همان بدنه ⇒ همان؛ بدنهٔ دیگر ⇒ ۴۰۹
  const ek = fresh();
  const nb = body1(w, { toBranch: C, to: 'shop_floor', lines: [{ productId: 'p1', qtyMilli: 4000 }, { productId: 'p2', qtyMilli: 1000 }], note: 'ویرایش‌شده' });
  const e1 = await edit(w.staffA, A, t.id, nb, ek);
  assert.equal(e1.status, 200, JSON.stringify(e1.body));
  assert.deepEqual([e1.body.transfer.toBranch.id, e1.body.transfer.to, e1.body.transfer.note], [C, 'shop_floor', 'ویرایش‌شده']);
  assert.deepEqual(e1.body.transfer.lines.map((l) => [l.productId, l.sentMilli]), [['p1', 4000], ['p2', 1000]]);
  const e2 = await edit(w.staffA, A, t.id, nb, ek);
  assert.equal(e2.headers.get('idempotent-replayed'), 'true');
  assert.equal((await edit(w.staffA, A, t.id, body1(w), ek)).body.error.code, 'idempotency_key_reused');
  //  ویرایش با مقصدِ بد یا خودش ⇒ همان پاسخ‌های ارسال؛ کالای ناشناس ⇒ ۴۰۴
  assert.equal((await edit(w.staffA, A, t.id, body1(w, { toBranch: A }))).body.error.code, 'same_branch');
  assert.equal((await edit(w.staffA, A, t.id, body1(w, { toBranch: 'brn_000000000000000000000000' }))).body.error.code, 'bad_destination');
  assert.equal((await edit(w.staffA, A, t.id, body1(w, { lines: [{ productId: 'nope', qtyMilli: 1 }] }))).body.error.code, 'product_not_found');
  //  ب (مقصدِ پیشین) هم هیچ نمی‌بیند؛ ج تا ارسال هم نه
  assert.deepEqual((await bx(w.mgrC, '', C)).body.transfers, []);
  //  صندوق‌دار (بی stock.move) ⇒ ۴۰۳
  assert.equal((await sendDraft(w.cashierA, A, t.id)).status, 403);
  //  ارسال: همان اثرِ ارسالِ مستقیم
  const sk = fresh();
  const sent = await sendDraft(w.mgrA, A, t.id, sk);
  assert.equal(sent.status, 200, JSON.stringify(sent.body));
  assert.equal(sent.body.transfer.status, 'in_transit');
  assert.ok(sent.body.transfer.sentAt > 0);
  assert.equal(sent.body.transfer.sentBy, w.mgrA.userId);
  assert.equal(sent.body.transfer.pendingMilli, 5000);
  assert.deepEqual(await levels(s.shopId, A, 'p1'), { shop_floor: 6000, in_transit: 4000 });
  assert.deepEqual(await levels(s.shopId, A, 'p2'), { shop_floor: 4000, in_transit: 1000 });
  assert.equal((await sendDraft(w.mgrA, A, t.id, sk)).headers.get('idempotent-replayed'), 'true');
  assert.deepEqual(await levels(s.shopId, A, 'p1'), { shop_floor: 6000, in_transit: 4000 }, 'تکرار بی اثرِ دوم');
  //  حالا ج می‌بیند و دریافت می‌کند؛ ارسال/ویرایشِ دوباره ⇒ ۴۰۹
  assert.deepEqual((await bx(w.mgrC, '?direction=in&status=open', C)).body.transfers.map((x) => x.id), [t.id]);
  for (const r of [await sendDraft(w.mgrA, A, t.id), await edit(w.mgrA, A, t.id, body1(w))]) {
    assert.equal(r.status, 409, JSON.stringify(r.body));
    assert.equal(r.body.error.code, 'transfer_bad_state');
  }
  const got = await receive(w.mgrC, C, t.id, {});
  assert.equal(got.status, 200, JSON.stringify(got.body));
  assert.deepEqual(await levels(s.shopId, C, 'p1'), { shop_floor: 4000 });
  assert.deepEqual(await levels(s.shopId, A, 'p1'), { shop_floor: 6000 });
  //  گام‌ها و سابقه: پیش‌نویس، ویرایش، ارسال (از پیش‌نویس)، دریافت
  const g = (await bx(w.mgrA, `/${t.id}`, A)).body;
  assert.deepEqual(g.steps.map((x) => [x.kind, x.statusFrom, x.statusTo]),
    [['draft', '', 'draft'], ['edit', 'draft', 'draft'], ['send', 'draft', 'in_transit'], ['receive', 'in_transit', 'received']]);
  const acts = (await many("SELECT action FROM audit_logs WHERE shop_id=$1 AND target_id=$2 ORDER BY created_at, id", [s.shopId, t.id])).map((r) => r.action);
  assert.deepEqual(acts.sort(), ['stock.branch_transfer_draft', 'stock.branch_transfer_edit', 'stock.branch_transfer_receive', 'stock.branch_transfer_send'].sort());
  //  مقصدِ بایگانی پس از ساختنِ پیش‌نویس ⇒ ارسال رد (bad_destination) و هیچ حرکت
  const d2 = (await send(w.mgrA, A, body1(w, { draft: true }))).body.transfer;
  assert.equal((await h.api('PATCH', `/api/shop/branches/${Bid}`, { token: s.token, body: { status: 'archived' } })).status, 200);
  const m0 = await movesOf(s.shopId);
  const bad = await sendDraft(w.mgrA, A, d2.id);
  assert.equal(bad.status, 400, JSON.stringify(bad.body));
  assert.equal(bad.body.error.code, 'bad_destination');
  assert.deepEqual(await movesOf(s.shopId), m0);
  assert.equal((await one('SELECT status FROM branch_transfers WHERE id=$1', [d2.id])).status, 'draft');
});

// ── لغو ───────────────────────────────────────────────────────────────

test('لغو: پیش‌نویس بی اثر؛ «در مسیر» با دلیل همه به مبدأ؛ نیمه‌رسیده نه؛ فقط فرستنده؛ بسته پایانی؛ لغو و دریافتِ هم‌زمان ⇒ یک نتیجه', async () => {
  const w = await world('لغو');
  const { s, A, Bid } = w;
  //  پیش‌نویس ⇒ لغو بی دلیل و بی حرکت
  const d = (await send(w.mgrA, A, body1(w, { draft: true }))).body.transfer;
  const m0 = await movesOf(s.shopId);
  const c1 = await cancel(w.mgrA, A, d.id);
  assert.equal(c1.status, 200, JSON.stringify(c1.body));
  assert.equal(c1.body.transfer.status, 'cancelled');
  assert.ok(c1.body.transfer.closedAt > 0);
  assert.deepEqual(c1.body.transfer.lines.map((l) => l.returnedMilli), [0], 'پیش‌نویس هرگز نرفت ⇒ چیزی برنگشت');
  assert.deepEqual(await movesOf(s.shopId), m0);
  //  «در مسیر» ⇒ دلیل اجباری؛ مقصد لغو نمی‌کند؛ با دلیل همه به مبدأ
  const t = (await send(w.mgrA, A, body1(w))).body.transfer;
  assert.deepEqual(await levels(s.shopId, A, 'p1'), { shop_floor: 7000, in_transit: 3000 });
  assert.equal((await cancel(w.mgrA, A, t.id)).body.error.code, 'reason_required');
  const byB = await cancel(w.mgrB, Bid, t.id, { reason: 'نه' });
  assert.equal(byB.status, 403, JSON.stringify(byB.body));
  assert.equal(byB.body.error.code, 'transfer_not_sender');
  const ck = fresh();
  const c2 = await cancel(w.staffA, A, t.id, { reason: 'اشتباه فرستادم' }, ck);
  assert.equal(c2.status, 200, JSON.stringify(c2.body));
  assert.equal(c2.body.transfer.status, 'cancelled');
  assert.equal(c2.body.transfer.reason, 'اشتباه فرستادم');
  assert.deepEqual(c2.body.transfer.lines.map((l) => [l.sentMilli, l.returnedMilli, l.pendingMilli, l.shortMilli]), [[3000, 3000, 0, 0]]);
  assert.deepEqual(await levels(s.shopId, A, 'p1'), { shop_floor: 10000 });
  assert.equal((await cancel(w.staffA, A, t.id, { reason: 'اشتباه فرستادم' }, ck)).headers.get('idempotent-replayed'), 'true');
  assert.deepEqual(await levels(s.shopId, A, 'p1'), { shop_floor: 10000 }, 'تکرار بی اثرِ دوم');
  //  بسته: لغو/دریافتِ دوباره ⇒ ۴۰۹؛ ب هنوز سند را می‌بیند (یک سرش است) ولی هیچ «در مسیر» ندارد
  assert.equal((await cancel(w.mgrA, A, t.id, { reason: 'دوباره' })).body.error.code, 'transfer_closed');
  assert.equal((await receive(w.mgrB, Bid, t.id, {})).body.error.code, 'transfer_closed');
  assert.equal((await bx(w.mgrB, `/${t.id}`, Bid)).body.transfer.status, 'cancelled');
  assert.deepEqual((await bx(w.mgrB, '?status=open', Bid)).body.transfers, []);
  //  نیمه‌رسیده ⇒ لغو نه (۴۰۹ transfer_partial)
  const p = (await send(w.mgrA, A, body1(w, { lines: [{ productId: 'p1', qtyMilli: 4000 }] }))).body.transfer;
  assert.equal((await receive(w.mgrB, Bid, p.id, { final: false, lines: [{ productId: 'p1', receivedMilli: 1000 }] })).status, 200);
  const cp = await cancel(w.mgrA, A, p.id, { reason: 'دیر' });
  assert.equal(cp.status, 409, JSON.stringify(cp.body));
  assert.equal(cp.body.error.code, 'transfer_partial');
  //  تریگرِ پایگاه: لغوِ نیمه‌رسیده یا بازکردنِ لغوشده با UPDATE ِ مستقیم هم نه
  await assert.rejects(query("UPDATE branch_transfers SET status='cancelled', closed_at=1 WHERE id=$1", [p.id]), /not allowed/);
  await assert.rejects(query("UPDATE branch_transfers SET status='in_transit', closed_at=NULL WHERE id=$1", [t.id]), /closed/);
  await assert.rejects(query("UPDATE branch_transfers SET to_branch='' WHERE id=$1", [p.id]), /immutable/);
  //  هم‌زمانی: لغوِ فرستنده و دریافتِ مقصد روی یک «در مسیر» ⇒ دقیقاً یکی
  for (let i = 0; i < 3; i++) {
    const x = (await send(w.mgrA, A, body1(w, { lines: [{ productId: 'p2', qtyMilli: 1000 }] }))).body.transfer;
    const [a, b] = await Promise.all([cancel(w.mgrA, A, x.id, { reason: 'هم‌زمان' }), receive(w.mgrB, Bid, x.id, {})]);
    const oks = [a, b].filter((r) => r.status === 200);
    assert.equal(oks.length, 1, `${a.status} ${JSON.stringify(a.body)} / ${b.status} ${JSON.stringify(b.body)}`);
    const lose = [a, b].find((r) => r.status !== 200);
    assert.equal(lose.status, 409);
    assert.ok(['transfer_closed', 'transfer_bad_state'].includes(lose.body.error.code), lose.body.error.code);
    const row = await one('SELECT status FROM branch_transfers WHERE id=$1', [x.id]);
    assert.equal(row.status, a.status === 200 ? 'cancelled' : 'received');
  }
  //  جمعِ p2 در الف + ب ثابت (۵ دانه)
  const sum = (lv) => Object.values(lv).reduce((acc, v) => acc + v, 0);
  assert.equal(sum(await levels(s.shopId, A, 'p2')) + sum(await levels(s.shopId, Bid, 'p2')), 5000);
});

// ── موجودیِ منفی با کلیدِ صاحب ─────────────────────────────────────────

test('ارسالِ بیش از موجودی: پیش‌فرض ۴۰۹ و هیچ ردیف؛ با کلیدِ صاحب حرکتِ ثبت‌شده و وظیفهٔ negative_stock؛ کلید فقط صاحب', async () => {
  const w = await world('منفی');
  const { s, A } = w;
  const over = body1(w, { lines: [{ productId: 'p2', qtyMilli: 8000 }] });
  const m0 = await movesOf(s.shopId);
  const r0 = await send(w.mgrA, A, over);
  assert.equal(r0.status, 409, JSON.stringify(r0.body));
  assert.equal(r0.body.error.code, 'insufficient_stock');
  assert.deepEqual(await movesOf(s.shopId), m0);
  //  پیش‌نویسِ بیش از موجودی ساخته می‌شود (هیچ اثری ندارد) ولی ارسالش همان ۴۰۹
  const d = (await send(w.mgrA, A, { ...over, draft: true })).body.transfer;
  assert.equal(d.status, 'draft');
  assert.equal((await sendDraft(w.mgrA, A, d.id)).body.error.code, 'insufficient_stock');
  assert.equal((await one('SELECT status FROM branch_transfers WHERE id=$1', [d.id])).status, 'draft');
  //  کلید: فقط صاحب؛ کلیدِ ناشناخته و مقدارِ نابولی ⇒ ۴۰۰
  assert.equal((await h.put('/api/shop/branches/settings', { transferNegative: true }, { token: w.mgrA.token })).status, 403);
  assert.equal((await h.put('/api/shop/branches/settings', { nope: true }, { token: s.token })).body.error.code, 'unknown_setting');
  assert.equal((await h.put('/api/shop/branches/settings', { transferNegative: 'yes' }, { token: s.token })).body.error.code, 'bad_setting');
  assert.equal((await h.put('/api/shop/branches/settings', {}, { token: s.token })).body.error.code, 'bad_settings');
  const on = await h.put('/api/shop/branches/settings', { transferNegative: true }, { token: s.token });
  assert.equal(on.status, 200, JSON.stringify(on.body));
  assert.deepEqual((await h.get('/api/shop/branches/settings', { token: s.token })).body.settings, { staffOtherStock: false, transferNegative: true });
  assert.equal((await h.get('/api/shop/branches/settings', { token: w.mgrA.token })).status, 403);
  //  حالا: ارسالِ پیش‌نویس ⇒ قفسهٔ الف منفی، با وظیفهٔ بررسی (هرگز بی‌صدا) و سابقه
  const sent = await sendDraft(w.mgrA, A, d.id);
  assert.equal(sent.status, 200, JSON.stringify(sent.body));
  assert.deepEqual(await levels(s.shopId, A, 'p2'), { shop_floor: -3000, in_transit: 8000 });
  const task = await one("SELECT * FROM stock_tasks WHERE shop_id=$1 AND branch_id=$2 AND kind='negative_stock' AND product_id='p2' AND status='open'", [s.shopId, A]);
  assert.ok(task, 'وظیفهٔ موجودیِ منفی');
  const log = await one("SELECT detail FROM audit_logs WHERE shop_id=$1 AND action='stock.branch_transfer_send' AND target_id=$2", [s.shopId, d.id]);
  assert.equal((typeof log.detail === 'string' ? JSON.parse(log.detail) : log.detail).negativeAllowed, true);
});

// ── آینهٔ دفترِ دستگاه ────────────────────────────────────────────────

test('آینهٔ دفترِ دستگاه: ردیفِ «انتقالِ میانِ شعبه» در الف و ب دو بار شمرده نمی‌شود؛ نشانِ ساختگی یا شعبهٔ دیگر سهمِ عادی دارد', async () => {
  const w = await world('آینه');
  const { s, A, Bid, C } = w;
  const t = (await send(w.mgrA, A, body1(w))).body.transfer;
  assert.equal((await receive(w.mgrB, Bid, t.id, {})).status, 200);
  assert.deepEqual(await levels(s.shopId, A, 'p1'), { shop_floor: 7000 });
  assert.deepEqual(await levels(s.shopId, Bid, 'p1'), { warehouse: 3000 });
  //  برنامهٔ الف «−۳» و برنامهٔ ب «+۳» را در دفترِ خودش می‌نشاند (همان `BranchTransferBook`)
  const mark = { isAdjustment: true, adjKind: 'branch_transfer', refId: t.id };
  await pushV3(w.mgrA, [entry3(`bxw-${t.id}-out-p1`, 'p1', -3, mark)], A);
  await pushV3(w.mgrB, [entry3(`bxw-${t.id}-in-p1`, 'p1', 3, mark)], Bid);
  assert.equal((await cat(w.mgrA, '/products/p1', A)).body.product.stock.levels.shop_floor, 7000, 'الف دو بار کم نشد');
  assert.equal((await cat(w.mgrB, '/products/p1', Bid)).body.product.stock.levels.warehouse || 0, 3000, 'ب دو بار زیاد نشد');
  assert.deepEqual(await levels(s.shopId, Bid, 'p1'), { warehouse: 3000 });
  //  نشانِ ساختگی (انتقالی نیست) و انتقالی که ج یک سرش نیست ⇒ سهمِ عادی (پنهان نمی‌کند)
  await pushV3(w.mgrC, [
    entry3('fake-1', 'p1', 2, { ...mark, refId: 'bxf_000000000000000000000000' }),
    entry3('fake-2', 'p1', 1, mark),
  ], C);
  assert.equal((await cat(w.mgrC, '/products/p1', C)).body.product.stock.levels.shop_floor, 3000);
});

// ── جدایی و سرآیندِ جعلی روی درهای تازه ─────────────────────────────────

test('جدایی: سرآیندِ جعلی ۴۰۳ پیش از در؛ دکانِ دیگر ۴۰۴؛ شناسهٔ شعبه در بدنه خوانده نمی‌شود', async () => {
  const w = await world('جدایی');
  const other = await world('جداییِ دکانِ دیگر');
  const { s, A, Bid } = w;
  const d = (await send(w.mgrA, A, body1(w, { draft: true }))).body.transfer;
  const snap = async () => JSON.stringify([await many('SELECT * FROM branch_transfers WHERE shop_id=$1 ORDER BY id', [s.shopId]), await movesOf(s.shopId)]);
  const before = await snap();
  for (const claim of [Bid, undefined, 'brn_000000000000000000000000', 'brn_xyz', other.A]) {
    for (const r of [await edit(w.staffA, claim, d.id, body1(w)), await sendDraft(w.staffA, claim, d.id), await cancel(w.staffA, claim, d.id)]) {
      assert.equal(r.status, 403, `${claim} ${JSON.stringify(r.body)}`);
      assert.ok(['branch_forbidden', 'branch_required'].includes(r.body.error.code), r.body.error.code);
    }
  }
  //  دکانِ دیگر با همان شناسه ⇒ ۴۰۴
  for (const r of [await bx(other.mgrA, `/${d.id}`, other.A), await sendDraft(other.mgrA, other.A, d.id), await cancel(other.mgrA, other.A, d.id)]) {
    assert.equal(r.status, 404, JSON.stringify(r.body));
  }
  //  شعبه در بدنه بی اثر: `fromBranch` ِ بدنهٔ ویرایش نادیده
  const e = await edit(w.mgrA, A, d.id, body1(w, { fromBranch: Bid, branch: Bid }));
  assert.equal(e.status, 200, JSON.stringify(e.body));
  assert.equal(e.body.transfer.fromBranch.id, A);
  assert.equal((await one("SELECT COUNT(*)::int AS n FROM branch_transfer_steps WHERE transfer_id=$1 AND kind='edit' AND branch_id=$2", [d.id, A])).n, 1);
  //  صاحب با سرآیندِ اصلی «طرف» نیست ⇒ ۴۰۴؛ با سرآیندِ الف همه‌کاره
  assert.equal((await sendDraft(s, undefined, d.id)).status, 404);
  assert.equal((await sendDraft(s, A, d.id)).status, 200);
});

// ── مهاجرتِ ۹۴۶ ───────────────────────────────────────────────────────

test('مهاجرتِ ۹۴۶: با پیش‌نویسِ باز برگشت رد؛ بی آن پایین (لغوشده پاک، فرستاده همان ۹۳۵) و دوباره بالا', async () => {
  const w = await world('مهاجرت');
  const { s, A, Bid } = w;
  const d = (await send(w.mgrA, A, body1(w, { draft: true }))).body.transfer;
  const down = fs.readFileSync(path.join(__dirname, '..', 'migrations', 'rollback', '946_branch_transfer_drafts.down.sql'), 'utf8');
  await assert.rejects(query(down), /draft branch transfer/);
  assert.equal((await one("SELECT COUNT(*)::int AS n FROM schema_migrations WHERE version='946_branch_transfer_drafts.sql'")).n, 1);
  //  پیش‌نویس ⇒ ارسال؛ یکی لغو
  assert.equal((await sendDraft(w.mgrA, A, d.id)).status, 200);
  const c = (await send(w.mgrA, A, body1(w, { lines: [{ productId: 'p2', qtyMilli: 1000 }] }))).body.transfer;
  assert.equal((await cancel(w.mgrA, A, c.id, { reason: 'نه' })).status, 200);
  for (const o of await many("SELECT id FROM branch_transfers WHERE status='draft'")) {
    await query("UPDATE branch_transfers SET status='cancelled', closed_at=1 WHERE id=$1", [o.id]);
  }
  const lv = await levels(s.shopId, A, 'p1');
  const moves = await movesOf(s.shopId);
  await query(down);
  assert.equal((await one("SELECT COUNT(*)::int AS n FROM schema_migrations WHERE version='946_branch_transfer_drafts.sql'")).n, 0);
  assert.equal((await one('SELECT to_regclass($1) AS r', ['shop_branch_settings'])).r, null);
  assert.equal((await one('SELECT COUNT(*)::int AS n FROM branch_transfers WHERE id=$1', [c.id])).n, 0, 'لغوشده پاک');
  const sent = await one('SELECT * FROM branch_transfers WHERE id=$1', [d.id]);
  assert.equal(sent.status, 'in_transit');
  assert.equal(sent.sent_at, undefined, 'ستونِ ۹۴۶ رفت');
  assert.deepEqual((await many('SELECT kind, status_from FROM branch_transfer_steps WHERE transfer_id=$1', [d.id])).map((x) => [x.kind, x.status_from]), [['send', '']]);
  assert.deepEqual(await levels(s.shopId, A, 'p1'), lv);
  assert.deepEqual(await movesOf(s.shopId), moves, 'حرکت‌ها دست‌نخورده');
  //  کدِ پیشین روی همان پایگاه: «در مسیر» هنوز دریافت می‌شود (CHECK ِ ۹۳۵)
  await assert.rejects(query("UPDATE branch_transfers SET status='cancelled', closed_at=1 WHERE id=$1", [d.id]));
  //  دوباره بالا
  assert.deepEqual(await require('../src/migrate').run(), ['946_branch_transfer_drafts.sql']);
  assert.equal((await receive(w.mgrB, Bid, d.id, {})).status, 200);
  const d2 = await send(w.mgrA, A, body1(w, { draft: true, lines: [{ productId: 'p1', qtyMilli: 1000 }] }));
  assert.equal(d2.status, 201, JSON.stringify(d2.body));
  assert.equal((await cancel(w.mgrA, A, d2.body.transfer.id)).status, 200);
  assert.deepEqual(await require('../src/migrate').run(), []);
});
