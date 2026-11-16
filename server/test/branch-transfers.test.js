'use strict';
/**
 * انتقالِ موجودی میانِ شعبه‌ها — INV-11/S21، فازِ ۴ ِ `docs/BRANCHES-fa.md` (§۱۰)، مهاجرتِ ۹۳۵؛ از راهِ واقعی (HTTP):
 *   ⛔ پرچمِ خاموش (`shop.branches` یا `shop.catalog`) ⇒ هر درِ تازه بایت‌به‌بایت همان درِ ناشناخته، با و بی سرآیند.
 *   ⛔ ارسال از الف ⇒ مبدأ ِ الف کم و «در مسیر» ِ الف زیاد؛ دریافتِ ناقص/پایانی در ب ⇒ از «در مسیر» ِ الف به مقصدِ ب؛ کسری با
 *      دلیل و وظیفه؛ رد ⇒ ماندهٔ در مسیر به مبدأ ِ الف. جمعِ کالا (الف + ب + کسری) ثابت؛ تطبیقِ دفتر در هر دو شعبه سالم.
 *   ⛔ کلیدِ یکتایی، ماشینِ وضعیت (و تریگرِ پایگاه)، ردِ موجودیِ منفی بی هیچ ردیفِ مانده، هم‌زمانی (دریافت و رد ⇒ یک نتیجه).
 *   ⛔ مجوز: فرستنده فقط با الف، دریافت/رد فقط با ب و فقط مقصدِ همان انتقال؛ سرآیندِ جعلی ⇒ ۴۰۳؛ مقصدِ بیگانه ⇒ یک پاسخ؛
 *      شعبهٔ سوم هیچ نمی‌بیند؛ نما فقط آن‌چه انتقال دارد. سابقه و گام برای شعبهٔ کننده.
 *   ⛔ مهاجرتِ ۹۳۵: برگشت با انتقالِ باز رد می‌شود؛ بی آن پایین و دوباره بالا.
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

// ── کمکی‌ها ────────────────────────────────────────────────────────────

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
const H = () => hlc.format({ wall: Date.now() - 600_000 + (++opN) * 10, counter: 0, node: 'brxfer' });
const v3op = (table, rowId, fields) => ({ op_id: `01JBX${String(Date.now()).slice(-6)}${String(++opN).padStart(15, '0')}`, table, row_id: rowId, kind: 'insert', hlc: H(), fields });
const product3 = (id) => v3op('products', id, { name: `کالای ${id}`, unit: 'دانه', salePriceMinor: 5000, purchasePriceMinor: 4000, active: true });
const entry3 = (id, pid, units) => v3op('warehouseEntries', id, { productId: pid, unitsMilli: units * 1000, createdAt: Date.now() });
async function pushV3(who, ops, branch) {
  const r = await h.api('POST', '/api/sync/v3/push', { token: who.token, body: { device_id: who.device, protocol: 3, ops }, headers: B(branch) });
  assert.equal(r.status, 200, JSON.stringify(r.body));
  for (const x of r.body.results || []) assert.notEqual(x.status, 'rejected', JSON.stringify(x));
}
let keyN = 0;
const fresh = () => `bxfer-key-${++keyN}-${Date.now()}`;
const cat = (who, p, branch, { method = 'GET', body = null, key, headers = {} } = {}) => h.api(method, `/api/shop/catalog${p}`, {
  token: who.token, body, headers: { ...B(branch), ...(key ? { 'Idempotency-Key': key } : {}), ...headers },
});
const bx = (who, p, branch, o = {}) => cat(who, `/branch-transfers${p}`, branch, o);
const send = (who, branch, body, key = fresh()) => bx(who, '', branch, { method: 'POST', body, key });
const receive = (who, branch, id, body, key = fresh()) => bx(who, `/${id}/receive`, branch, { method: 'POST', body, key });
const reject = (who, branch, id, body, key = fresh()) => bx(who, `/${id}/reject`, branch, { method: 'POST', body, key });

/** مانده‌های سرورِ یک کالا در یک شعبه: { shop_floor, warehouse, in_transit, … } */
async function levels(shopId, branchId, pid) {
  const rows = await many('SELECT location, on_hand_milli FROM stock_levels WHERE shop_id=$1 AND branch_id=$2 AND product_id=$3', [shopId, branchId, pid]);
  return Object.fromEntries(rows.filter((r) => Number(r.on_hand_milli) !== 0).map((r) => [r.location, Number(r.on_hand_milli)]));
}
const total = (lv) => Object.values(lv).reduce((s, v) => s + v, 0);

/** دکانِ v3 با موجودیِ سرور، سه شعبه؛ الف ۱۰ دانه p1 و ۵ دانه p2 روی قفسه */
async function world(tag, { branchesOn = true } = {}) {
  const s = await shop(`انتقالِ شعبه ${tag}`);
  await flag(s, 'shop.catalog');
  assert.equal((await cat(s, '/settings', undefined, { method: 'PUT', body: { syncSource: 'v3' } })).status, 200);
  await pushV3(s, [product3('p1'), product3('p2'), entry3('m-e1', 'p1', 4)]);
  assert.equal((await cat(s, '/products/p1')).status, 200);
  const w = { s, staffA: await member(s, 'staff', `کارمندِ الف ${tag}`), mgrA: await member(s, 'manager', `مدیرِ الف ${tag}`),
    mgrB: await member(s, 'manager', `مدیرِ ب ${tag}`), staffB: await member(s, 'staff', `کارمندِ ب ${tag}`),
    mgrC: await member(s, 'manager', `مدیرِ ج ${tag}`) };
  if (!branchesOn) return w;
  await flag(s, 'shop.branches');
  for (const [k, name] of [['A', 'الف'], ['Bid', 'ب'], ['C', 'ج']]) {
    const r = await h.post('/api/shop/branches', { name }, { token: s.token });
    assert.equal(r.status, 201, JSON.stringify(r.body));
    w[k] = r.body.branch.id;
  }
  for (const [m, id] of [[w.staffA, w.A], [w.mgrA, w.A], [w.mgrB, w.Bid], [w.staffB, w.Bid], [w.mgrC, w.C]]) {
    assert.equal((await h.put(`/api/shop/branches/members/${m.memberId}`, { branches: [id] }, { token: s.token })).status, 200);
  }
  await pushV3(w.mgrA, [entry3('a-e1', 'p1', 10), entry3('a-e2', 'p2', 5)], w.A);
  assert.equal((await cat(w.mgrA, '/products/p1', w.A)).body.product.stock.levels.shop_floor, 10000);
  return w;
}
const body1 = (w, extra = {}) => ({ toBranch: w.Bid, from: 'shop_floor', to: 'warehouse', lines: [{ productId: 'p1', qtyMilli: 3000 }], note: 'بارِ یک', ...extra });

// ── پرچمِ خاموش: همان درِ ناشناخته ─────────────────────────────────────

test('پرچمِ خاموش ⇒ هر درِ انتقالِ میانِ شعبه همان پاسخِ درِ ناشناخته، با و بی سرآیند؛ انتقالِ درونِ شعبهٔ دیگر هنوز branch_unsupported', async () => {
  const doors = [['GET', ''], ['GET', '/destinations'], ['GET', '/bxf_000000000000000000000000'],
    ['POST', '', { toBranch: '', from: 'shop_floor', to: 'warehouse', lines: [{ productId: 'p1', qtyMilli: 1 }] }],
    ['POST', '/bxf_000000000000000000000000/receive', {}], ['POST', '/bxf_000000000000000000000000/reject', { reason: 'x' }]];
  const unknown = async (who, method, branch) => {
    const r = await cat(who, '/zz-no-such-door', branch, { method, body: method === 'GET' ? null : {}, key: fresh() });
    return [r.status, r.body];
  };
  const parity = async (w, label) => {
    for (const [m, p, bd] of doors) {
      for (const who of [w.s, w.staffA]) {
        for (const claim of [undefined, 'brn_000000000000000000000000', 'main']) {
          resetAll();
          const r = await bx(who, p, claim, { method: m, body: bd || null, key: fresh() });
          assert.deepEqual([r.status, r.body], await unknown(who, m, claim), `${label} ${m} ${p} ${claim}`);
          if (claim === undefined || label === 'branches off') assert.equal(r.status, 404, `${label} ${m} ${p}`);
        }
      }
    }
  };
  //  ۱) shop.branches خاموش (کاتالوگ روشن)
  const w = await world('خاموش', { branchesOn: false });
  await parity(w, 'branches off');
  //  ۲) shop.branches روشن، shop.catalog خاموش
  const s2 = await shop('انتقالِ شعبه بی کاتالوگ');
  await flag(s2, 'shop.branches');
  const st2 = await member(s2, 'staff', 'کارمندِ بی کاتالوگ');
  await parity({ s: s2, staffA: st2 }, 'catalog off');
  assert.equal((await one('SELECT COUNT(*)::int AS n FROM branch_transfers')).n, 0);
  //  ۳) روشن: انتقالِ درونِ شعبهٔ غیرِ اصلی همان رفتارِ فازِ ۳ (فقط اصلی)
  const on = await world('درونِ شعبه');
  const r = await cat(on.mgrA, '/transfers', on.A, { method: 'POST', key: fresh(), body: { from: 'shop_floor', to: 'warehouse', lines: [{ productId: 'p1', qtyMilli: 1000 }] } });
  assert.equal(r.status, 403);
  assert.equal(r.body.error.code, 'branch_unsupported');
});

// ── راهِ اصلی: ارسال ⇒ در مسیر ⇒ دریافتِ ناقص ⇒ دریافتِ پایانی با کسری ────

test('S21 میانِ شعبه‌ها: ارسال از الف، دو دریافت در ب (ناقص، سپس پایانی با کسریِ دلیل‌دار)، جمعِ کالا ثابت، سابقه و گام برای هر شعبه', async () => {
  const w = await world('راه');
  const { s, A, Bid } = w;
  const dest = await bx(w.staffA, '/destinations', A);
  assert.equal(dest.status, 200, JSON.stringify(dest.body));
  assert.deepEqual(dest.body.destinations.map((d) => d.id).sort(), ['', Bid, w.C].sort());
  assert.ok(!dest.body.destinations.some((d) => d.id === A));

  const sent = await send(w.staffA, A, body1(w, { lines: [{ productId: 'p1', qtyMilli: 3000 }, { productId: 'p2', qtyMilli: 2000 }] }));
  assert.equal(sent.status, 201, JSON.stringify(sent.body));
  const t = sent.body.transfer;
  assert.match(t.id, /^bxf_[0-9a-f]{24}$/);
  assert.equal(t.status, 'in_transit');
  assert.equal(t.direction, 'out');
  assert.deepEqual([t.fromBranch.id, t.toBranch.id, t.toBranch.name], [A, Bid, 'ب']);
  assert.equal(t.pendingMilli, 5000);
  assert.deepEqual(await levels(s.shopId, A, 'p1'), { shop_floor: 7000, in_transit: 3000 });
  assert.deepEqual(await levels(s.shopId, A, 'p2'), { shop_floor: 3000, in_transit: 2000 });
  assert.deepEqual(await levels(s.shopId, Bid, 'p1'), {}, 'ب تا دریافت هیچ نمی‌گیرد');

  //  ب فقط «به ب» را می‌بیند؛ الف فقط «از الف»
  const inB = await bx(w.mgrB, '?direction=in&status=open', Bid);
  assert.deepEqual(inB.body.transfers.map((x) => [x.id, x.direction]), [[t.id, 'in']]);
  assert.deepEqual((await bx(w.mgrB, '?direction=out', Bid)).body.transfers, []);
  assert.deepEqual((await bx(w.mgrA, '?direction=out', A)).body.transfers.map((x) => x.id), [t.id]);

  //  دریافتِ ناقص: ۱ از ۳ ِ p1 و ۲ از ۲ ِ p2
  const part = await receive(w.staffB, Bid, t.id, { final: false, lines: [{ productId: 'p1', receivedMilli: 1000 }, { productId: 'p2', receivedMilli: 2000 }] });
  assert.equal(part.status, 200, JSON.stringify(part.body));
  assert.equal(part.body.transfer.status, 'partial');
  assert.equal(part.body.transfer.pendingMilli, 2000);
  assert.deepEqual(await levels(s.shopId, A, 'p1'), { shop_floor: 7000, in_transit: 2000 });
  assert.deepEqual(await levels(s.shopId, A, 'p2'), { shop_floor: 3000 });
  assert.deepEqual(await levels(s.shopId, Bid, 'p1'), { warehouse: 1000 });
  assert.deepEqual(await levels(s.shopId, Bid, 'p2'), { warehouse: 2000 });
  //  بیش از ماندهٔ در مسیر و گامِ خالی رد
  assert.equal((await receive(w.staffB, Bid, t.id, { final: false, lines: [{ productId: 'p1', receivedMilli: 2001 }] })).body.error.code, 'over_received');
  assert.equal((await receive(w.staffB, Bid, t.id, { final: false, lines: [] })).body.error.code, 'nothing_received');
  //  پایانی با کسری: بی دلیل نه
  const noWhy = await receive(w.staffB, Bid, t.id, { lines: [{ productId: 'p1', receivedMilli: 1500 }] });
  assert.equal(noWhy.status, 400);
  assert.equal(noWhy.body.error.code, 'reason_required');
  const fin = await receive(w.staffB, Bid, t.id, { lines: [{ productId: 'p1', receivedMilli: 1500 }], reason: 'یک کارتن شکسته رسید' });
  assert.equal(fin.status, 200, JSON.stringify(fin.body));
  const ft = fin.body.transfer;
  assert.equal(ft.status, 'received');
  assert.equal(ft.shortMilli, 500);
  assert.deepEqual(ft.lines.map((l) => [l.productId, l.receivedMilli, l.shortMilli]), [['p1', 2500, 500], ['p2', 2000, 0]]);
  assert.deepEqual(await levels(s.shopId, A, 'p1'), { shop_floor: 7000 });
  assert.deepEqual(await levels(s.shopId, Bid, 'p1'), { warehouse: 2500 });
  //  جمع ثابت: ۱۰ = ۷ (الف) + ۲٫۵ (ب) + ۰٫۵ کسری
  assert.equal(total(await levels(s.shopId, A, 'p1')) + total(await levels(s.shopId, Bid, 'p1')) + ft.shortMilli, 10000);
  //  کسری هرگز بی‌صدا: وظیفه در شعبهٔ فرستنده
  const task = await one("SELECT * FROM stock_tasks WHERE shop_id=$1 AND branch_id=$2 AND kind='transfer_short'", [s.shopId, A]);
  assert.ok(task, 'وظیفهٔ کسری در الف');
  const td = typeof task.detail === 'string' ? JSON.parse(task.detail) : task.detail;
  assert.deepEqual([td.branchTransferId, td.toBranch, td.shortMilli, td.reason], [t.id, Bid, 500, 'یک کارتن شکسته رسید']);
  //  تطبیقِ دفتر در هر دو شعبه سالم
  assert.equal((await cat(w.mgrA, '/report', A)).body.ok, true);
  assert.equal((await cat(w.mgrB, '/report', Bid)).body.ok, true);
  //  دفترِ هر شعبه حرکت‌های خودش با ارجاع به انتقال
  const movesA = await many("SELECT type, location, qty_milli FROM stock_moves WHERE shop_id=$1 AND branch_id=$2 AND ref_type='branch_transfer' ORDER BY source_key", [s.shopId, A]);
  const movesB = await many("SELECT type, location, qty_milli FROM stock_moves WHERE shop_id=$1 AND branch_id=$2 AND ref_type='branch_transfer' ORDER BY source_key", [s.shopId, Bid]);
  assert.ok(movesA.length >= 6 && movesB.length === 3, JSON.stringify({ movesA, movesB }));
  assert.ok(movesB.every((m) => m.type === 'transfer_in' && m.location === 'warehouse'));
  //  گام‌ها: ارسال در الف، دو دریافت در ب؛ سابقه با شعبهٔ کننده
  const steps = await many('SELECT branch_id, kind, status_from, status_to FROM branch_transfer_steps WHERE transfer_id=$1 ORDER BY created_at, step_key', [t.id]);
  assert.deepEqual(steps.map((x) => [x.branch_id, x.kind, x.status_from, x.status_to]),
    [[A, 'send', '', 'in_transit'], [Bid, 'receive', 'in_transit', 'partial'], [Bid, 'receive', 'partial', 'received']]);
  const logs = await many("SELECT action, detail FROM audit_logs WHERE shop_id=$1 AND target_id=$2 ORDER BY created_at, id", [s.shopId, t.id]);
  const dets = logs.map((l) => [l.action, (typeof l.detail === 'string' ? JSON.parse(l.detail) : l.detail)]);
  assert.deepEqual(dets.map(([a, d]) => [a, d.branch, d.side]), [
    ['stock.branch_transfer_send', A, 'from'], ['stock.branch_transfer_receive', Bid, 'to'], ['stock.branch_transfer_receive', Bid, 'to']]);
  //  جزئیات با گام‌ها برای هر دو سر
  const g = await bx(w.mgrA, `/${t.id}`, A);
  assert.equal(g.status, 200, JSON.stringify(g.body));
  assert.deepEqual(g.body.steps.map((x) => [x.kind, x.side]), [['send', 'from'], ['receive', 'to'], ['receive', 'to']]);
  //  بسته دوباره بسته نمی‌شود
  const again = await reject(w.mgrB, Bid, t.id, { reason: 'دیر' });
  assert.equal(again.status, 409);
  assert.equal(again.body.error.code, 'transfer_closed');
});

test('رد در ب: ماندهٔ در مسیر به مبدأ ِ الف برمی‌گردد، رسیده در ب می‌ماند؛ دلیل اجباری؛ مقصدِ اصلی هم', async () => {
  const w = await world('رد');
  const { s, A, Bid } = w;
  const t = (await send(w.mgrA, A, body1(w, { lines: [{ productId: 'p1', qtyMilli: 4000 }], to: 'shop_floor' }))).body.transfer;
  assert.equal((await receive(w.mgrB, Bid, t.id, { final: false, lines: [{ productId: 'p1', receivedMilli: 1000 }] })).status, 200);
  const noWhy = await reject(w.mgrB, Bid, t.id, {});
  assert.equal(noWhy.body.error.code, 'reason_required');
  const rj = await reject(w.mgrB, Bid, t.id, { reason: 'سفارشِ اشتباه' });
  assert.equal(rj.status, 200, JSON.stringify(rj.body));
  assert.equal(rj.body.transfer.status, 'rejected');
  assert.deepEqual(await levels(s.shopId, A, 'p1'), { shop_floor: 9000 });
  assert.deepEqual(await levels(s.shopId, Bid, 'p1'), { shop_floor: 1000 });
  //  به شعبهٔ اصلی ('') و دریافتِ کاملِ صاحب با سرآیندِ اصلی
  const m = await send(w.mgrA, A, body1(w, { toBranch: 'main', lines: [{ productId: 'p2', qtyMilli: 1000 }] }));
  assert.equal(m.status, 201, JSON.stringify(m.body));
  assert.equal(m.body.transfer.toBranch.id, '');
  assert.equal(m.body.transfer.toBranch.main, true);
  const got = await receive(s, undefined, m.body.transfer.id, {});
  assert.equal(got.status, 200, JSON.stringify(got.body));
  assert.deepEqual(await levels(s.shopId, '', 'p2'), { warehouse: 1000 });
  assert.equal((await cat(s, '/report')).body.ok, true);
});

// ── کلیدِ یکتایی و موجودیِ منفی ───────────────────────────────────────

test('کلیدِ یکتایی: تکرار ⇒ همان پاسخ بی اثرِ دوم؛ بدنهٔ دیگر ⇒ ۴۰۹؛ کلیدِ شعبهٔ دیگر هیچ پس نمی‌دهد؛ کمبود ⇒ ۴۰۹ و هیچ ردیفِ مانده', async () => {
  const w = await world('یکتایی');
  const { s, A, Bid } = w;
  const key = fresh();
  const a = await send(w.mgrA, A, body1(w), key);
  assert.equal(a.status, 201);
  const b = await send(w.mgrA, A, body1(w), key);
  assert.equal(b.status, 200);
  assert.equal(b.headers.get('idempotent-replayed'), 'true');
  assert.equal(b.body.transfer.id, a.body.transfer.id);
  assert.deepEqual(await levels(s.shopId, A, 'p1'), { shop_floor: 7000, in_transit: 3000 });
  const c = await send(w.mgrA, A, body1(w, { note: 'دیگر' }), key);
  assert.equal(c.status, 409);
  assert.equal(c.body.error.code, 'idempotency_key_reused');
  //  همان کلید در ب: فضای جدا ⇒ هیچ دادهٔ الف پس نمی‌آید (ب به خودش نمی‌فرستد)
  const x = await send(w.mgrB, Bid, body1(w), key);
  assert.equal(x.status, 400);
  assert.equal(x.body.error.code, 'same_branch');
  assert.ok(!JSON.stringify(x.body).includes(a.body.transfer.id));
  //  دریافت: تکرارِ همان گام ⇒ همان؛ بی کلید ⇒ ۴۰۰
  const rk = fresh();
  const r1 = await receive(w.mgrB, Bid, a.body.transfer.id, { lines: [{ productId: 'p1', receivedMilli: 3000 }] }, rk);
  const r2 = await receive(w.mgrB, Bid, a.body.transfer.id, { lines: [{ productId: 'p1', receivedMilli: 3000 }] }, rk);
  assert.equal(r1.status, 200);
  assert.equal(r2.status, 200);
  assert.equal(r2.headers.get('idempotent-replayed'), 'true');
  assert.deepEqual(await levels(s.shopId, Bid, 'p1'), { warehouse: 3000 });
  const nk = await bx(w.mgrB, `/${a.body.transfer.id}/reject`, Bid, { method: 'POST', body: { reason: 'x' } });
  assert.equal(nk.body.error.code, 'idempotency_key_required');
  //  کمبود: ۷ دانه هست، ۸ نه ⇒ ۴۰۹ و هیچ انتقال، گام، حرکت یا سابقه
  const before = await many('SELECT * FROM stock_moves WHERE shop_id=$1 ORDER BY id', [s.shopId]);
  const n0 = (await one('SELECT COUNT(*)::int AS n FROM branch_transfers WHERE shop_id=$1', [s.shopId])).n;
  const over = await send(w.mgrA, A, body1(w, { lines: [{ productId: 'p1', qtyMilli: 8000 }] }));
  assert.equal(over.status, 409, JSON.stringify(over.body));
  assert.equal(over.body.error.code, 'insufficient_stock');
  assert.deepEqual(await many('SELECT * FROM stock_moves WHERE shop_id=$1 ORDER BY id', [s.shopId]), before);
  assert.equal((await one('SELECT COUNT(*)::int AS n FROM branch_transfers WHERE shop_id=$1', [s.shopId])).n, n0);
  assert.equal((await one("SELECT COUNT(*)::int AS n FROM audit_logs WHERE shop_id=$1 AND action='stock.branch_transfer_send'", [s.shopId])).n, 1);
  //  کالای ناشناس ⇒ ۴۰۴
  assert.equal((await send(w.mgrA, A, body1(w, { lines: [{ productId: 'nope', qtyMilli: 1 }] }))).body.error.code, 'product_not_found');
});

// ── مجوز، سرآیندِ جعلی و جداییِ داده ──────────────────────────────────

test('مجوز: کارمندِ الف هرگز در ب دریافت نمی‌کند؛ شعبه فقط از سرآیندِ سنجیده؛ مقصدِ بیگانه یک پاسخ؛ شعبهٔ سوم هیچ نمی‌بیند', async () => {
  const w = await world('مجوز');
  const { s, A, Bid, C } = w;
  const other = await world('مجوزِ دکانِ دیگر');
  const t = (await send(w.staffA, A, body1(w))).body.transfer;
  const snap = async () => JSON.stringify([
    await many('SELECT * FROM branch_transfers WHERE shop_id=$1 ORDER BY id', [s.shopId]),
    await many('SELECT * FROM stock_moves WHERE shop_id=$1 ORDER BY id', [s.shopId]),
  ]);
  const before = await snap();
  const rb = { lines: [{ productId: 'p1', receivedMilli: 3000 }] };
  //  فرستنده با سرآیندِ خودش: «فقط مقصد»
  for (const fn of [() => receive(w.staffA, A, t.id, rb), () => reject(w.staffA, A, t.id, { reason: 'x' }), () => receive(w.mgrA, A, t.id, rb)]) {
    const r = await fn();
    assert.equal(r.status, 403, JSON.stringify(r.body));
    assert.equal(r.body.error.code, 'transfer_wrong_branch');
  }
  //  سرآیندِ ب، بی سرآیند، ساختگی، بدشکل، دوتایی، شعبهٔ دکانِ دیگر ⇒ ۴۰۳ پیش از در
  for (const claim of [Bid, undefined, 'brn_000000000000000000000000', 'brn_xyz', `${A}, ${Bid}`, other.A, ` ${Bid} `]) {
    const r = await receive(w.staffA, claim, t.id, rb);
    assert.equal(r.status, 403, `${claim} ${JSON.stringify(r.body)}`);
    assert.ok(['branch_forbidden', 'branch_required', 'transfer_wrong_branch'].includes(r.body.error.code), `${claim} ${r.body.error.code}`);
  }
  //  شناسهٔ شعبه در بدنه/پارامتر خوانده نمی‌شود
  const forged = await bx(w.staffA, `/${t.id}/receive?branch=${Bid}&branchId=${Bid}`, A,
    { method: 'POST', key: fresh(), body: { ...rb, branch: Bid, branchId: Bid, branch_id: Bid, toBranch: Bid } });
  assert.equal(forged.body.error.code, 'transfer_wrong_branch');
  //  شعبهٔ سوم (ج) و مدیرِ ج: نه در فهرست، نه با شناسه، نه دریافت
  assert.deepEqual((await bx(w.mgrC, '', C)).body.transfers, []);
  for (const r of [await bx(w.mgrC, `/${t.id}`, C), await receive(w.mgrC, C, t.id, rb), await reject(w.mgrC, C, t.id, { reason: 'x' })]) {
    assert.equal(r.status, 404, JSON.stringify(r.body));
    assert.equal(r.body.error.code, 'transfer_not_found');
  }
  //  صاحب با سرآیندِ اصلی هم «طرف» نیست ⇒ ۴۰۴ (صاحب هر شعبه را دارد ولی با سرآیندِ همان شعبه)
  assert.equal((await bx(s, `/${t.id}`)).status, 404);
  assert.equal((await receive(s, A, t.id, rb)).body.error.code, 'transfer_wrong_branch');
  //  دکانِ دیگر با همان شناسه ⇒ ۴۰۴
  assert.equal((await bx(other.mgrA, `/${t.id}`, other.A)).status, 404);
  assert.equal(await snap(), before, 'هیچ چیز عوض نشد');
  //  مقصد: بیگانه، ساختگی، بدشکل، نبود، بایگانی ⇒ یک پاسخ؛ خودش ⇒ same_branch
  const arch = await h.post('/api/shop/branches', { name: 'بایگانی' }, { token: s.token });
  assert.equal((await h.api('PATCH', `/api/shop/branches/${arch.body.branch.id}`, { token: s.token, body: { status: 'archived' } })).status, 200);
  for (const toBranch of [other.Bid, 'brn_000000000000000000000000', 'x', arch.body.branch.id, undefined, 5]) {
    const r = await send(w.staffA, A, body1(w, { toBranch }));
    assert.equal(r.status, 400, `${toBranch}`);
    assert.equal(r.body.error.code, 'bad_destination', `${toBranch}`);
    assert.ok(!JSON.stringify(r.body).includes('بایگانی'));
  }
  assert.equal((await send(w.staffA, A, body1(w, { toBranch: A }))).body.error.code, 'same_branch');
  //  مقصدها: بایگانی و خودش نه
  const d = (await bx(w.staffA, '/destinations', A)).body.destinations.map((x) => x.id);
  assert.ok(!d.includes(arch.body.branch.id) && !d.includes(A) && d.includes(Bid));
  //  صاحب با سرآیندِ ب دریافت می‌کند؛ کارمندِ ب هم
  const ok = await receive(w.staffB, Bid, t.id, rb);
  assert.equal(ok.status, 200, JSON.stringify(ok.body));
  //  نمای ب فقط همان کلیدهای انتقال (هیچ موجودی یا سندِ الف)
  const seen = (await bx(w.mgrB, `/${t.id}`, Bid)).body;
  assert.deepEqual(Object.keys(seen).sort(), ['steps', 'transfer']);
  //  ۹۴۶: `sentAt`/`sentBy` (پیش‌نویس) و `returnedMilli` (برگشته به مبدأ) هم — همان کلیدهای خودِ انتقال، نه چیزِ دیگرِ الف
  assert.deepEqual(Object.keys(seen.transfer).sort(), ['closedAt', 'closedBy', 'createdAt', 'createdBy', 'direction', 'from', 'fromBranch',
    'id', 'lines', 'note', 'pendingMilli', 'reason', 'sentAt', 'sentBy', 'shortMilli', 'status', 'to', 'toBranch'].sort());
  assert.deepEqual(Object.keys(seen.transfer.lines[0]).sort(), ['pendingMilli', 'productId', 'receivedMilli', 'returnedMilli', 'sentMilli', 'shortMilli']);
});

test('STF-06: عضوِ محدود به انبار از قفسه نمی‌فرستد و در قفسهٔ مقصد دریافت نمی‌کند', async () => {
  const w = await world('محدوده');
  const { s, A, Bid } = w;
  await flag(s, 'shop.staff_scope');
  for (const m of [w.staffA, w.staffB]) {
    const r = await h.put(`/api/shop/staff-scope/members/${m.user.id}`, { places: ['warehouse'] }, { token: s.token });
    assert.equal(r.status, 200, JSON.stringify(r.body));
  }
  const r = await send(w.staffA, A, body1(w));
  assert.equal(r.status, 403);
  assert.equal(r.body.error.code, 'location_out_of_scope');
  const t = (await send(w.mgrA, A, body1(w, { to: 'shop_floor' }))).body.transfer;
  assert.equal((await receive(w.staffB, Bid, t.id, {})).body.error.code, 'location_out_of_scope');
  assert.equal((await receive(w.mgrB, Bid, t.id, {})).status, 200);
});

// ── هم‌زمانی و ماشینِ وضعیت ───────────────────────────────────────────

test('هم‌زمانی: دریافت و ردِ هم‌زمان ⇒ دقیقاً یک نتیجه؛ دو ارسالِ هم‌کلید ⇒ یک انتقال؛ دو ارسالِ بیش از موجودی ⇒ یکی', async () => {
  const w = await world('هم‌زمانی');
  const { s, A, Bid } = w;
  for (let i = 0; i < 3; i++) {
    const t = (await send(w.mgrA, A, body1(w, { lines: [{ productId: 'p1', qtyMilli: 1000 }] }))).body.transfer;
    const [a, b] = await Promise.all([receive(w.mgrB, Bid, t.id, {}), reject(w.staffB, Bid, t.id, { reason: 'هم‌زمان' })]);
    assert.deepEqual([a.status, b.status].sort(), [200, 409], JSON.stringify([a.body, b.body]));
    const loser = a.status === 409 ? a : b;
    assert.equal(loser.body.error.code, 'transfer_closed');
    const row = await one('SELECT status FROM branch_transfers WHERE id=$1', [t.id]);
    assert.equal(row.status, a.status === 200 ? 'received' : 'rejected');
    assert.equal((await one('SELECT COUNT(*)::int AS n FROM branch_transfer_steps WHERE transfer_id=$1', [t.id])).n, 2);
  }
  //  جمع ثابت پس از سه دور
  assert.equal(total(await levels(s.shopId, A, 'p1')) + total(await levels(s.shopId, Bid, 'p1')), 10000);
  assert.equal((await levels(s.shopId, A, 'p1')).in_transit, undefined);
  //  هم‌کلید
  const key = fresh();
  const n0 = (await one('SELECT COUNT(*)::int AS n FROM branch_transfers WHERE shop_id=$1', [s.shopId])).n;
  const same = await Promise.all([send(w.mgrA, A, body1(w, { lines: [{ productId: 'p2', qtyMilli: 1000 }] }), key),
    send(w.mgrA, A, body1(w, { lines: [{ productId: 'p2', qtyMilli: 1000 }] }), key)]);
  assert.deepEqual(same.map((r) => r.status).sort(), [200, 201]);
  assert.equal((await one('SELECT COUNT(*)::int AS n FROM branch_transfers WHERE shop_id=$1', [s.shopId])).n, n0 + 1);
  //  p2: ۴ مانده؛ دو ارسالِ ۳ دانه ⇒ فقط یکی
  const two = await Promise.all([send(w.mgrA, A, body1(w, { lines: [{ productId: 'p2', qtyMilli: 3000 }] })),
    send(w.staffA, A, body1(w, { lines: [{ productId: 'p2', qtyMilli: 3000 }] }))]);
  assert.deepEqual(two.map((r) => r.status).sort(), [201, 409]);
  assert.deepEqual(await levels(s.shopId, A, 'p2'), { shop_floor: 1000, in_transit: 4000 });
  assert.equal((await cat(w.mgrA, '/report', A)).body.ok, true);
});

test('ماشینِ وضعیت در پایگاه: انتقالِ بسته و دو سرش با UPDATE ِ مستقیم هم عوض نمی‌شوند؛ گامِ نامجاز رد', async () => {
  const w = await world('وضعیت');
  const { s, A } = w;
  const t = (await send(w.mgrA, A, body1(w))).body.transfer;
  await assert.rejects(query("UPDATE branch_transfers SET to_branch='' WHERE id=$1", [t.id]), /immutable/);
  await assert.rejects(query("UPDATE branch_transfers SET status='in_transit' WHERE id=$1", [t.id]), /not allowed/);
  await assert.rejects(query(
    `INSERT INTO branch_transfer_steps (shop_id, branch_id, step_key, transfer_id, kind, body_hash, status_from, status_to, created_at)
     VALUES ($1,$2,'k-bad-1234',$3,'receive','h','received','partial',1)`, [s.shopId, w.Bid, t.id]));
  assert.equal((await receive(w.mgrB, w.Bid, t.id, {})).status, 200);
  await assert.rejects(query("UPDATE branch_transfers SET status='rejected', closed_at=1 WHERE id=$1", [t.id]), /closed/);
  assert.equal((await one('SELECT status FROM branch_transfers WHERE id=$1', [t.id])).status, 'received');
});

// ── مهاجرتِ ۹۳۵ ───────────────────────────────────────────────────────

test('مهاجرتِ ۹۳۵: با انتقالِ باز برگشت رد و هیچ چیز عوض نمی‌شود؛ بی آن پایین، مانده‌ها دست‌نخورده، دوباره بالا', async () => {
  const w = await world('مهاجرت');
  const { s, A, Bid } = w;
  const t = (await send(w.mgrA, A, body1(w))).body.transfer;
  const down = fs.readFileSync(path.join(__dirname, '..', 'migrations', 'rollback', '935_branch_transfers.down.sql'), 'utf8');
  //  ۹۴۶ روی ۹۳۵ سوار است ⇒ برگشتِ واقعی به ترتیبِ وارون: اول ۹۴۶ (همان دُمِ `upgrade-rehearsal`)
  const down946 = fs.readFileSync(path.join(__dirname, '..', 'migrations', 'rollback', '946_branch_transfer_drafts.down.sql'), 'utf8');
  const has = async (n) => (await one('SELECT to_regclass($1) AS r', [n])).r !== null;
  await assert.rejects(query(down), /open branch transfer/);
  assert.ok(await has('branch_transfers') && await has('branch_transfer_steps'));
  assert.equal((await one("SELECT COUNT(*)::int AS n FROM schema_migrations WHERE version='935_branch_transfers.sql'")).n, 1);
  //  باز را ببند، سپس برگشت
  assert.equal((await receive(w.mgrB, Bid, t.id, {})).status, 200);
  const lv = { A: await levels(s.shopId, A, 'p1'), B: await levels(s.shopId, Bid, 'p1') };
  const moves = await many('SELECT * FROM stock_moves WHERE shop_id=$1 ORDER BY id', [s.shopId]);
  //  اگر دکانِ دیگری در همین پایگاه انتقالِ باز دارد، آن هم بسته شود (آزمون‌ها پایگاه را شریک‌اند)
  for (const o of await many("SELECT b.id, b.shop_id, b.to_branch FROM branch_transfers b WHERE status IN ('in_transit','partial')")) {
    await query("UPDATE branch_transfers SET status='rejected', closed_at=1 WHERE id=$1", [o.id]);
  }
  await query(down946);
  await query(down);
  assert.ok(!(await has('branch_transfers')) && !(await has('branch_transfer_steps')));
  assert.equal((await one("SELECT COUNT(*)::int AS n FROM schema_migrations WHERE version='935_branch_transfers.sql'")).n, 0);
  assert.deepEqual({ A: await levels(s.shopId, A, 'p1'), B: await levels(s.shopId, Bid, 'p1') }, lv, 'برگشت مانده‌ها را دست نزد');
  assert.deepEqual(await many('SELECT * FROM stock_moves WHERE shop_id=$1 ORDER BY id', [s.shopId]), moves);
  //  دوباره بالا: همان پرونده، جدول‌ها و تریگر؛ درها دوباره کار می‌کنند
  const done = await require('../src/migrate').run();
  assert.deepEqual(done, ['935_branch_transfers.sql', '946_branch_transfer_drafts.sql']);
  assert.ok(await has('branch_transfers') && await has('branch_transfer_steps'));
  const t2 = await send(w.mgrA, A, body1(w, { lines: [{ productId: 'p1', qtyMilli: 1000 }] }));
  assert.equal(t2.status, 201, JSON.stringify(t2.body));
  assert.deepEqual((await bx(w.mgrB, '?status=open', Bid)).body.transfers.map((x) => x.id), [t2.body.transfer.id]);
  await assert.rejects(query("UPDATE branch_transfers SET from_branch='' WHERE id=$1", [t2.body.transfer.id]), /immutable/);
  assert.deepEqual(await require('../src/migrate').run(), []);
});
