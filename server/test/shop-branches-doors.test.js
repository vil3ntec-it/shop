'use strict';
/**
 * شعبه درونِ یک دکان — فازِ ۳ب ِ سرور (`docs/BRANCHES-fa.md` §۹؛ پرچمِ خاموشِ `shop.branches`؛ مهاجرتِ ۹۳۰).
 *
 * درهای مانده شعبه‌شناس شدند — نگه‌داشته، سفارشِ خرید/رسید/فاکتور، نوبت‌های صندوق و S46، صندوقِ تعارض، خبرها — و سابقه‌های
 * بهترین‌کوششِ CRM-03/STF-03 برای شعبه. از راهِ واقعی (درخواستِ HTTP):
 *   ⛔ پرچمِ خاموش ⇒ همهٔ این درها بایت‌به‌بایت همان با و بی سرآیندِ `X-Branch-Id`؛ روشن ⇒ شعبهٔ اصلی همان پاسخ و همان ردیف‌ها.
 *   ⛔ جدایی: دادهٔ «ب» به مدیر/کارمندِ «الف» نمی‌رسد؛ سرآیندِ «ب»، شعبهٔ ساختگی، شعبهٔ دکانِ دیگر و `main` ِ بی‌اصلی ⇒ ۴۰۳؛
 *      همان شناسه‌ها (نگه‌داشته، رسید، فاکتور) در «الف» با «ب» برخورد نمی‌کنند و ردیفِ «ب» عوض نمی‌شود.
 *   ⛔ صندوق: نوبت مالِ شعبه؛ PIN ِ عضوِ شعبهٔ دیگر «عضو نیست» بی هیچ شمارِ خطا؛ S46 با نوبتِ همان شعبه.
 *   ⛔ CRM-03/STF-03 در بخشِ شعبه: سابقه در دکان با `detail.branch` و هدفِ `<شعبه>|<فاکتور>`.
 *   ⛔ صندوقِ تعارض: هر شعبه سندهای ردشدهٔ خودش؛ «دوباره ثبت» در همان بخش (v3)، هرگز در اصلی.
 *   ⛔ خبرها: دفترِ جدا و زنگ فقط به اعضای همان شعبه.
 *   ⛔ مهاجرتِ ۹۳۰: برگشت فقط دادهٔ شعبه‌های دیگر را برمی‌دارد و کلیدهای امروز را برمی‌گرداند؛ دوباره بالا می‌آید.
 */
process.env.SYNC_V3_ENABLED = '1';
process.env.TILL_PIN_ITER = '1000';
process.env.RATE_LOGIN_EDGE_MAX = process.env.RATE_LOGIN_EDGE_MAX || '100000';

const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const h = require('./helpers');
const { query, one, many, newId, now } = require('../src/db');
const { resetAll } = require('../src/middleware/ratelimit');
const push = require('../src/lib/push');
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
test.after(async () => { push.setDeliver(null); await h.stop(); });
test.beforeEach(() => resetAll());

// ── کمکی‌ها ────────────────────────────────────────────────────────────

const HELD = '/api/shop/held-invoices';
const PO = '/api/shop/purchase-orders';
const TILL = '/api/shop/till-pin';
const INBOX = '/api/shop/conflict-inbox';
const EVENTS = '/api/events';
const FAKE = 'brn_000000000000000000000000';
const PIN = { a: '615083', b: '730264', main: '482915' };

async function shop(name) {
  const u = await h.newUser(name, 'shop');
  const made = await h.post('/api/shop', { name: `دکانِ ${name}` }, { token: u.accessToken });
  assert.equal(made.status, 201, JSON.stringify(made.body));
  const s = await h.signIn(u, 'shop');
  return { ...s, token: s.accessToken, userId: s.user.id, shopId: made.body.shop.id, device: `dev-o-${made.body.shop.id}`.slice(0, 60) };
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
const call = (who, method, p, { branch, body = null, headers = {} } = {}) => {
  resetAll();
  return h.api(method, p, { token: who.token, body, headers: { ...B(branch), ...headers } });
};
const ok = (r, code = 200) => { assert.equal(r.status, code, JSON.stringify(r.body)); return r.body; };
/** پاسخ بی «ساعتِ سرور» (تنها چیزی که میانِ دو خواندن عوض می‌شود) */
const norm = (x) => JSON.parse(JSON.stringify(x, (k, v) => (k === 'serverTime' || k === 'server_time' ? undefined : v)));

let opN = 0;
let tick = 0;
/** op ِ v3 با ساعتِ اکنون (برای S46: نه پیش از نوبت، نه «دیر») */
const H = () => hlc.format({ wall: Date.now() + (++tick), counter: 0, node: 'brdoors' });
const oid = () => `01JBD${String(Date.now()).slice(-6)}${String(++opN).padStart(15, '0')}`;
const ins3 = (table, rowId, fields) => ({ op_id: oid(), hlc: H(), table, row_id: rowId, kind: 'insert', fields });
const cashSale = (id, total = 5000) => ins3('sales', id, {
  totalMinor: total, discountMinor: 0, finalTotalMinor: total, paidAmountMinor: total, remainingMinor: 0,
  paymentMethod: 'cash', status: 'completed', createdAt: Date.now(), date: '',
});
async function push3(who, ops, branch) {
  const r = await call(who, 'POST', '/api/sync/v3/push', { branch, body: { device_id: who.device, protocol: 3, ops } });
  assert.equal(r.status, 200, JSON.stringify(r.body));
  return r.body.results;
}
const inv = (id, label) => ({
  id, number: 1, label, heldAt: 1700000000000, device: 'A',
  lines: [{ productId: 'p1', quantity: 2.0, unitPrice: 95.0, specialPrice: 95.0, seller: 'زهرا' }],
});
const PO_BODY = (note) => ({
  supplierId: 'sup-1', supplierName: 'شرکتِ نمونه', expectedDate: '2026-11-20', note,
  lines: [{ productId: 'p1', name: 'کالای مشترک', unit: 'عدد', qtyMilli: 10000, priceMinor: 10000 }],
});
let keyN = 0;
const pkey = () => `brdoors-key-${Date.now()}-${++keyN}`;

/** دکانِ v3 با همهٔ پرچم‌های درهای ۳ب؛ صاحب، کارمندِ اصلی، کارمند و مدیرِ «الف» و «ب» */
async function world(tag, { branchesOn = true } = {}) {
  const s = await shop(`درهای شعبه ${tag}`);
  for (const k of ['shop.held_sync', 'shop.purchase_orders', 'shop.till_pin', 'shop.till_op_user']) await flag(s, k);
  s.memberId = (await call(s, 'GET', `${TILL}/config`)).body.memberId;
  const w = { s, main: await member(s, 'staff', `اصلی ${tag}`), staffA: await member(s, 'staff', `الف ${tag}`),
    mgrA: await member(s, 'manager', `مدیرِ الف ${tag}`), staffB: await member(s, 'staff', `ب ${tag}`),
    mgrB: await member(s, 'manager', `مدیرِ ب ${tag}`) };
  await push3(s, [ins3('products', 'p1', { name: 'کالای مشترک', unit: 'دانه', salePriceMinor: 5000, active: true })]);
  if (branchesOn) await branchesFor(w);
  return w;
}
async function branchesFor(w) {
  const { s } = w;
  await flag(s, 'shop.branches');
  w.A = ok(await call(s, 'POST', '/api/shop/branches', { body: { name: 'شعبهٔ الف' } }), 201).branch.id;
  w.Bid = ok(await call(s, 'POST', '/api/shop/branches', { body: { name: 'شعبهٔ ب' } }), 201).branch.id;
  for (const [m, id] of [[w.staffA, w.A], [w.mgrA, w.A], [w.staffB, w.Bid], [w.mgrB, w.Bid]]) {
    ok(await call(s, 'PUT', `/api/shop/branches/members/${m.memberId}`, { body: { branches: [id] } }));
  }
}

/** دادهٔ یک شعبه از درهای ۳ب با نشانِ [tag]: نگه‌داشته، سفارش + رسید + فاکتور، خبر، نوبتِ صندوق و op ِ نشسته */
async function seed(w, who, cashier, branch, tag, pin, label = tag) {
  ok(await call(who, 'PUT', `${HELD}/${tag}-h1`, { branch, body: { version: 1, body: inv(`${tag}-h1`, `${label}-نگه‌داشته`) } }));
  const o = ok(await call(who, 'POST', PO, { branch, body: PO_BODY(`${label}-سفارش`), headers: { 'Idempotency-Key': pkey() } }), 201).order;
  ok(await call(who, 'POST', `${PO}/${o.id}/send`, { branch, body: {} }));
  ok(await call(who, 'POST', `${PO}/${o.id}/receipts`, { branch, body: { id: `${tag}-rc1`, lines: [{ lineId: o.lines[0].id, receivedMilli: 4000 }] } }), 201);
  ok(await call(who, 'POST', `${PO}/${o.id}/bills`, { branch, body: { id: `${tag}-bl1`, billNo: `${tag}-F1`,
    lines: [{ lineId: o.lines[0].id, qtyMilli: 4000, totalMinor: 40000 }] } }), 201);
  ok(await call(who, 'POST', EVENTS, { branch, body: { kind: 'note', title: `${label}-خبر`, clientId: `${tag}-c1` } }), 201);
  ok(await call(w.s, 'PUT', `${TILL}/members/${cashier.memberId}`, { body: { pin } }));
  ok(await call(who, 'POST', `${TILL}/verify`, { branch, body: { memberId: cashier.memberId, pin } }));
  await push3(who, [cashSale(`${tag}-s1`)], branch);
  return { po: o.id };
}

/** هر درِ خواندنیِ ۳ب — پاسخِ کامل بی ساعتِ سرور */
/** بازهٔ ثابتِ همهٔ خواندن‌های نوبت/op (پاسخ همان بازه را برمی‌گرداند) */
const FROM = Date.now() - 3600_000;
const RANGE = `from=${FROM}&to=${FROM + 4 * 3600_000}`;
async function reads(who, branch, poId) {
  const out = {};
  for (const p of [HELD, `${HELD}?since=1`, `${PO}/config`, PO, `${PO}/${poId}`, `${TILL}/config`, `${TILL}/current`,
    `${TILL}/sessions?${RANGE}`, `${TILL}/ops?${RANGE}`, INBOX, `${INBOX}/mine`, EVENTS]) {
    const r = await call(who, 'GET', p, { branch });
    out[p.replace(RANGE, '')] = [r.status, norm(r.body)];
  }
  return out;
}

/** ردیف‌های شعبهٔ اصلیِ همهٔ جدول‌های این درها (همهٔ ستون‌ها) */
async function rowsOf(shopId, branchId) {
  const q = (sql) => many(sql, [shopId, branchId]);
  return JSON.parse(JSON.stringify({
    held: await q('SELECT * FROM shop_held_invoices WHERE shop_id=$1 AND branch_id=$2 ORDER BY id'),
    po: await q('SELECT * FROM purchase_orders WHERE shop_id=$1 AND branch_id=$2 ORDER BY id'),
    rc: await q('SELECT * FROM purchase_receipts WHERE shop_id=$1 AND branch_id=$2 ORDER BY id'),
    bills: await q('SELECT * FROM purchase_bills WHERE shop_id=$1 AND branch_id=$2 ORDER BY id'),
    sessions: await q('SELECT * FROM shop_till_sessions WHERE shop_id=$1 AND branch_id=$2 ORDER BY id'),
    ops: await q('SELECT * FROM shop_till_op_users WHERE shop_id=$1 AND branch_id=$2 ORDER BY op_id'),
    events: branchId
      ? await q("SELECT * FROM shop_branch_events WHERE branch_key = $1 || '#' || $2 ORDER BY id")
      : await q("SELECT * FROM shop_events WHERE shop_id=$1 AND $2 = '' ORDER BY id"),
  }));
}

// ── برابری: پرچمِ خاموش و شعبهٔ اصلی ──────────────────────────────────

test('پرچمِ خاموش ⇒ درهای ۳ب بایت‌به‌بایت همان با و بی سرآیند؛ روشن ⇒ شعبهٔ اصلی همان پاسخ و همان ردیف‌ها', async () => {
  const w = await world('برابری', { branchesOn: false });
  const { s } = w;
  const m = await seed(w, s, w.main, undefined, 'msec', PIN.main);
  const base = await reads(s, undefined, m.po);
  assert.equal(base[HELD][1].items.length, 1);
  assert.equal(base[PO][1].orders.length, 1);
  assert.equal(base[`${TILL}/current`][1].session.memberId, w.main.memberId);
  assert.equal(base[EVENTS][1].events.length, 1);
  assert.ok(base[`${TILL}/ops?`][1].ops.some((o) => o.rowId === 'msec-s1' && o.tillUser && o.tillUser.memberId === w.main.memberId),
    JSON.stringify(base[`${TILL}/ops?`]));
  const rows = await rowsOf(s.shopId, '');
  assert.ok(rows.held.length && rows.po.length && rows.rc.length && rows.bills.length && rows.sessions.length && rows.ops.length);
  //  خاموش: سرآیندِ هر شعبه (حتی ساختگی یا بدشکل) بی اثر
  for (const fake of [FAKE, 'main', "x' OR 1=1 --"]) assert.deepEqual(await reads(s, fake, m.po), base, fake);
  assert.deepEqual(await rowsOf(s.shopId, ''), rows);

  //  روشن + دو شعبه، «ب» با دادهٔ خودش در همهٔ درها ⇒ صاحب در اصلی (بی سرآیند، `main`) همان پاسخ و همان ردیف‌ها
  await branchesFor(w);
  await seed(w, w.mgrB, w.staffB, w.Bid, 'bsec', PIN.b);
  assert.equal((await rowsOf(s.shopId, w.Bid)).po.length, 1);
  assert.deepEqual(await reads(s, undefined, m.po), base);
  assert.deepEqual(await reads(s, 'main', m.po), base);
  assert.deepEqual(await rowsOf(s.shopId, ''), rows, 'ردیف‌های شعبهٔ اصلی دست نخورد');
  //  دوباره خاموش ⇒ سرآیندِ «ب» بی اثر و همان اصلی
  await flag(s, 'shop.branches', false);
  assert.deepEqual(await reads(s, w.Bid, m.po), base);
});

// ── جدایی و سرآیندِ جعلی ─────────────────────────────────────────────

test('جدایی: دادهٔ «ب» در هیچ درِ ۳ب به «الف» نمی‌رسد؛ سرآیندِ جعلی ⇒ ۴۰۳؛ همان شناسه‌ها در «الف» با «ب» برخورد نمی‌کنند', async () => {
  const w = await world('جدایی');
  const { s, A, Bid } = w;
  const b = await seed(w, w.mgrB, w.staffB, Bid, 'bsec', PIN.b);
  const beforeA = await rowsOf(s.shopId, Bid);
  //  همان شناسه‌ها (نگه‌داشته، رسید، فاکتور، کلیدِ خبر) در «الف»: ردیفِ تازهٔ «الف»، نه «تکراری/تعارض» و نه نوشتن روی «ب»
  const a = await seed(w, w.mgrA, w.staffA, A, 'bsec', PIN.a, 'asec');
  assert.deepEqual(await rowsOf(s.shopId, Bid), beforeA, 'شناسه‌های یکسانِ «الف» دادهٔ «ب» را عوض کرد');
  const other = await shop('دکانِ دیگرِ درها');
  await flag(other, 'shop.branches');
  const X = ok(await call(other, 'POST', '/api/shop/branches', { body: { name: 'شعبهٔ بیگانه' } }), 201).branch.id;
  const before = await rowsOf(s.shopId, Bid);
  const doors = [
    ['GET', HELD], ['PUT', `${HELD}/bsec-h1`, { version: 9, body: inv('bsec-h1', 'دزد') }], ['DELETE', `${HELD}/bsec-h1?version=9&reason=deleted`],
    ['GET', PO], ['GET', `${PO}/${b.po}`], ['PUT', `${PO}/${b.po}`, { version: 3, ...PO_BODY('دزد') }], ['POST', `${PO}/${b.po}/cancel`, { reason: 'x' }],
    ['POST', `${PO}/${b.po}/receipts`, { id: 'bsec-rc1', lines: [] }], ['POST', `${PO}/${b.po}/bills/bsec-bl1/void`, { reason: 'x' }],
    ['GET', `${TILL}/current`], ['GET', `${TILL}/sessions`], ['GET', `${TILL}/ops`], ['GET', `${TILL}/ops/row?table=sales&row_id=bsec-s1`],
    ['POST', `${TILL}/verify`, { memberId: w.staffB.memberId, pin: PIN.b }], ['POST', `${TILL}/current/end`, { reason: 'lock' }],
    ['GET', INBOX], ['GET', `${INBOX}/mine`], ['GET', EVENTS], ['POST', EVENTS, { kind: 'note', title: 'x' }], ['POST', `${EVENTS}/seen`, {}],
  ];
  let calls = 0;
  for (const who of [w.mgrA, w.staffA]) {
    for (const [method, p, body] of doors) {
      for (const claim of [Bid, FAKE, X, 'main', undefined, ` ${Bid} `.repeat(3)]) {
        const r = await call(who, method, p, { branch: claim, body: body || null });
        calls += 1;
        assert.equal(r.status, 403, `${method} ${p} [${claim}] ${JSON.stringify(r.body)}`);
        assert.ok(['branch_forbidden', 'branch_required'].includes(r.body.error.code), `${method} ${p} ${r.body.error.code}`);
        assert.ok(!JSON.stringify(r.body).includes('bsec'), `${method} ${p}`);
      }
      //  با سرآیندِ «الف»: هیچ نشانِ «ب»؛ شناسهٔ واقعیِ «ب» ⇒ نیست
      const r = await call(who, method, p, { branch: A, body: body || null });
      calls += 1;
      assert.ok(!JSON.stringify(r.body).includes('bsec-نگه') && !JSON.stringify(r.body).includes('bsec-سفارش'), `${method} ${p}`);
      assert.ok(!JSON.stringify(r.body).includes('bsec-خبر'), `${method} ${p}`);
    }
  }
  assert.ok(calls > 250);
  assert.equal((await call(w.mgrA, 'GET', `${PO}/${b.po}`, { branch: A })).status, 404);
  assert.deepEqual(ok(await call(w.mgrA, 'GET', PO, { branch: A })).orders.map((o) => [o.id, o.note]), [[a.po, 'asec-سفارش']]);
  assert.ok(ok(await call(w.staffA, 'GET', HELD, { branch: A })).items.every((x) => !JSON.stringify(x).includes('bsec-نگه')));
  //  شمارهٔ سفارشِ هر شعبه از ۱
  const ao = ok(await call(w.mgrA, 'GET', `${PO}/${a.po}`, { branch: A })).order;
  assert.equal(ao.no, 1);
  assert.equal(ok(await call(w.mgrB, 'GET', `${PO}/${b.po}`, { branch: Bid })).order.no, 1);
  assert.deepEqual(await rowsOf(s.shopId, Bid), before, 'دادهٔ «ب» عوض شد');
  //  بی‌اثر نیست: صاحب با سرآیندِ «ب» دادهٔ «ب» را می‌بیند؛ بی سرآیند (اصلی) نه
  const ownB = ok(await call(s, 'GET', HELD, { branch: Bid }));
  assert.deepEqual(ownB.items.map((x) => x.body.label), ['bsec-نگه‌داشته']);
  assert.ok(JSON.stringify(ok(await call(s, 'GET', `${PO}/${b.po}`, { branch: Bid }))).includes('bsec-سفارش'));
  assert.equal(ok(await call(s, 'GET', EVENTS, { branch: Bid })).events.length, 1);
  assert.deepEqual(ok(await call(s, 'GET', HELD)).items, []);
  assert.equal((await call(s, 'GET', `${PO}/${b.po}`)).status, 404);
  assert.deepEqual(ok(await call(s, 'GET', EVENTS)).events, []);
  //  سابقهٔ کارِ «ب» در دکان با شعبه
  const logs = await many("SELECT action, detail FROM audit_logs WHERE shop_id=$1 AND action LIKE 'purchase_order.%' ORDER BY created_at, id", [s.shopId]);
  assert.ok(logs.length >= 6 && logs.every((l) => [Bid, A].includes(l.detail.branch)), JSON.stringify(logs));
});

// ── صندوق: نوبتِ هر شعبه و S46 ────────────────────────────────────────

test('صندوق: نوبت مالِ شعبه؛ PIN ِ عضوِ شعبهٔ دیگر «عضو نیست» بی شمارِ خطا؛ S46 با نوبتِ همان شعبه', async () => {
  const w = await world('صندوق');
  const { s, A, Bid } = w;
  ok(await call(s, 'PUT', `${TILL}/members/${w.staffA.memberId}`, { body: { pin: PIN.a } }));
  ok(await call(s, 'PUT', `${TILL}/members/${w.staffB.memberId}`, { body: { pin: PIN.b } }));
  //  مدیرِ الف PIN ِ کارمندِ «ب» را (حتی درست) روی دستگاهِ «الف» ⇒ «عضو نیست»؛ شمار و قفلِ PIN ِ او دست نمی‌خورد
  const pinRow = () => one('SELECT failures, locked_until, pin_hash FROM shop_till_pins WHERE shop_id=$1 AND member_id=$2', [s.shopId, w.staffB.memberId]);
  const pin0 = await pinRow();
  for (const pin of [PIN.b, '111111']) {
    const r = await call(w.mgrA, 'POST', `${TILL}/verify`, { branch: A, body: { memberId: w.staffB.memberId, pin } });
    assert.equal(r.status, 404, JSON.stringify(r.body));
    assert.equal(r.body.error.code, 'member_not_found');
  }
  assert.deepEqual(await pinRow(), pin0);
  //  هر شعبه نوبتِ خودش؛ دستگاهِ صاحب در دو شعبه دو نوبتِ جدا (یکی برای هر شعبه)
  assert.equal(ok(await call(w.mgrB, 'POST', `${TILL}/verify`, { branch: Bid, body: { memberId: w.staffB.memberId, pin: PIN.b } })).session.memberId, w.staffB.memberId);
  assert.equal(ok(await call(w.mgrA, 'POST', `${TILL}/verify`, { branch: A, body: { memberId: w.staffA.memberId, pin: PIN.a } })).session.memberId, w.staffA.memberId);
  ok(await call(s, 'POST', `${TILL}/verify`, { branch: A, body: { memberId: w.staffA.memberId, pin: PIN.a } }));
  ok(await call(s, 'POST', `${TILL}/verify`, { branch: Bid, body: { memberId: w.staffB.memberId, pin: PIN.b } }));
  assert.equal(ok(await call(s, 'GET', `${TILL}/current`, { branch: A })).session.memberId, w.staffA.memberId);
  assert.equal(ok(await call(s, 'GET', `${TILL}/current`, { branch: Bid })).session.memberId, w.staffB.memberId);
  assert.equal(ok(await call(s, 'GET', `${TILL}/current`)).session, null, 'شعبهٔ اصلی نوبتی ندارد');
  const open = await many('SELECT branch_id FROM shop_till_sessions WHERE shop_id=$1 AND ended_at IS NULL ORDER BY branch_id', [s.shopId]);
  assert.deepEqual(open.map((x) => x.branch_id).sort(), [A, A, Bid, Bid].sort());
  const sess = (who, branch) => call(who, 'GET', `${TILL}/sessions`, { branch }).then((r) => ok(r).sessions.map((x) => x.memberId));
  assert.deepEqual([...new Set(await sess(w.mgrA, A))], [w.staffA.memberId]);
  assert.deepEqual([...new Set(await sess(w.mgrB, Bid))], [w.staffB.memberId]);
  assert.deepEqual(await sess(s, undefined), []);
  //  S46: op ِ بخشِ «ب» به نوبتِ همان شعبه روی همان دستگاه (نه نوبتِ «الف» ِ همان دستگاهِ صاحب)
  await push3(s, [cashSale('s46-b')], Bid);
  await push3(s, [cashSale('s46-a')], A);
  await push3(w.mgrB, [cashSale('s46-b2')], Bid);
  const ops = await many(`SELECT branch_id, row_id, till_member_id, basis FROM shop_till_op_users
    WHERE shop_id=$1 AND table_name='sales' ORDER BY row_id`, [s.shopId]);
  assert.deepEqual(ops.map((o) => [o.row_id, o.branch_id, o.till_member_id, o.basis]), [
    ['s46-a', A, w.staffA.memberId, 'session'], ['s46-b', Bid, w.staffB.memberId, 'session'], ['s46-b2', Bid, w.staffB.memberId, 'session'],
  ]);
  const listed = (who, branch) => call(who, 'GET', `${TILL}/ops?table=sales`, { branch }).then((r) => ok(r).ops.map((o) => o.rowId).sort());
  assert.deepEqual(await listed(w.mgrB, Bid), ['s46-b', 's46-b2']);
  assert.deepEqual(await listed(w.mgrA, A), ['s46-a']);
  assert.deepEqual(await listed(s, undefined), []);
  //  قفلِ صندوق فقط نوبتِ همین شعبهٔ همین دستگاه را می‌بندد
  assert.equal(ok(await call(s, 'POST', `${TILL}/current/end`, { branch: A, body: { reason: 'lock' } })).ended, 1);
  assert.equal(ok(await call(s, 'GET', `${TILL}/current`, { branch: Bid })).session.memberId, w.staffB.memberId);
  //  برداشتنِ PIN (سطحِ دکان، فقط اصلی) نوبت‌های بازِ آن عضو را در همهٔ شعبه‌ها می‌بندد
  ok(await call(s, 'DELETE', `${TILL}/members/${w.staffB.memberId}`));
  assert.equal((await one('SELECT COUNT(*)::int AS n FROM shop_till_sessions WHERE shop_id=$1 AND till_member_id=$2 AND ended_at IS NULL',
    [s.shopId, w.staffB.memberId])).n, 0);
  //  مدیریتِ PIN ِ دیگران فقط از شعبهٔ اصلی (همان `branch-gate`)
  const g = await call(w.mgrB, 'GET', `${TILL}/members`, { branch: Bid });
  assert.equal(g.status, 403); assert.equal(g.body.error.code, 'branch_unsupported');
});

// ── سابقه‌های بهترین‌کوشش: CRM-03 و STF-03 در بخشِ شعبه ─────────────────

test('CRM-03 و STF-03 در بخشِ شعبه: دفترِ همان بخش، سابقه در دکان با شعبه و هدفِ جدا؛ اصلی هیچ', async () => {
  const w = await world('سابقه');
  const { s, A, Bid } = w;
  await flag(s, 'shop.credit_limit_review');
  await flag(s, 'shop.promo_loyalty_review');
  const at = Date.now();
  const credit = (id, debt) => [
    ins3('sales', id, { totalMinor: 1000000, finalTotalMinor: 1000000, paidAmountMinor: 1000000 - debt, remainingMinor: debt,
      debtGivenMinor: debt, debtorId: 'd3-1', paymentMethod: 'credit', status: 'completed', createdAt: at }),
    ins3('transactions', `${id}-give`, { debtorId: 'd3-1', type: 'give', amountMinor: debt, refId: id, kind: 'sale_credit', createdAt: at }),
  ];
  const promoSale = (id) => [
    ins3('sales', id, { totalMinor: 90000, discountMinor: 0, finalTotalMinor: 90000, paidAmountMinor: 90000, remainingMinor: 0,
      debtGivenMinor: 0, paymentMethod: 'cash', status: 'completed', date: '2026-11-14', createdAt: at }),
    ins3('saleItems', `${id}-i0`, { saleId: id, productId: 'p1', quantityMilli: 1000, unitPriceMinor: 90000, totalPriceMinor: 90000,
      listPriceMinor: 100000, promo: [{ id: 'p10', rev: 1, name: 'ده درصد', discountMinor: 10000 }] }),
  ];
  for (const [who, branch] of [[w.staffB, Bid], [w.staffA, A]]) {
    const r0 = await push3(who, [ins3('debtors', 'd3-1', { name: 'مشتریِ سقف', phone: '0700000003', creditLimitMinor: 500000 }),
      ins3('transactions', 'd3-1-old', { debtorId: 'd3-1', type: 'give', amountMinor: 400000, createdAt: at - 10000 })], branch);
    assert.deepEqual(r0.map((x) => x.status), ['applied', 'applied']);
    assert.ok((await push3(who, credit('s3-over', 200000), branch)).every((x) => x.status === 'applied'));
    assert.ok((await push3(who, promoSale('sp-1'), branch)).every((x) => x.status === 'applied'));
  }
  const flags = async (action) => (await many(
    'SELECT target_id, detail FROM audit_logs WHERE shop_id=$1 AND action=$2', [s.shopId, action]
  )).map((r) => [r.target_id, r.detail.branch, r.detail.path]).sort();
  const both = (id) => [[`${A}|${id}`, A, 'v3'], [`${Bid}|${id}`, Bid, 'v3']].sort();
  assert.deepEqual(await flags('sale.credit_over_limit'), both('s3-over'));
  assert.deepEqual(await flags('sale.promo_review'), both('sp-1'));
  const credit3 = await one("SELECT detail FROM audit_logs WHERE shop_id=$1 AND action='sale.credit_over_limit' AND target_id=$2", [s.shopId, `${Bid}|s3-over`]);
  assert.deepEqual([credit3.detail.balanceMinor, credit3.detail.addMinor, credit3.detail.excessMinor], [400000, 200000, 100000], 'ماندهٔ همان بخش');
  //  هیچ سابقه‌ای در هیچ دکانِ دیگری (بخشِ شعبه هرگز «دکان» نمی‌شود)
  assert.equal((await one("SELECT COUNT(*)::int AS n FROM audit_logs WHERE shop_id LIKE '%#%'")).n, 0);
});

// ── صندوقِ تعارضِ هر شعبه ─────────────────────────────────────────────

test('صندوقِ تعارض: سندِ ردشدهٔ «ب» فقط در صندوقِ «ب»؛ «دوباره ثبت» در همان بخش؛ «الف» و اصلی هیچ', async () => {
  const w = await world('تعارض');
  const { s, A, Bid } = w;
  await flag(s, 'shop.credit_cap');
  ok(await h.roleLayer(s.token, 'staff', { 'sales.credit': { caps: { maxAmount: 500000 } } }));
  await push3(w.mgrB, [ins3('debtors', 'd-1', { name: 'احمد', phone: '0793330001' })], Bid);
  const sale = ins3('sales', 'bsec-cf1', { totalMinor: 1000000, finalTotalMinor: 1000000, paidAmountMinor: 399999, remainingMinor: 600001,
    debtGivenMinor: 600001, debtorId: 'd-1', paymentMethod: 'credit', status: 'completed' });
  const item = ins3('saleItems', 'bsec-cf1-i1', { saleId: 'bsec-cf1', productId: 'p1', quantityMilli: 2000, unitPriceMinor: 500000, totalPriceMinor: 1000000 });
  const res = await push3(w.staffB, [sale, item], Bid);
  assert.equal(res[0].status, 'rejected', JSON.stringify(res));
  const box = await one(`SELECT id FROM sync_v3_conflicts WHERE account_id=$1 AND loser_op_id=$2`, [`${s.shopId}#${Bid}`, sale.op_id]);
  assert.ok(box, 'تعارض در بخشِ «ب»');
  const list = (who, branch) => call(who, 'GET', INBOX, { branch }).then((r) => ok(r).conflicts.map((c) => c.id));
  assert.deepEqual(await list(w.mgrB, Bid), [box.id]);
  assert.deepEqual(await list(w.mgrA, A), []);
  assert.deepEqual(await list(s, undefined), []);
  assert.deepEqual(await list(s, Bid), [box.id]);
  assert.deepEqual(ok(await call(w.staffB, 'GET', `${INBOX}/mine`, { branch: Bid })).conflicts.map((c) => c.id), [box.id]);
  //  شناسهٔ واقعیِ «ب» از «الف» یا اصلی ⇒ «پیدا نشد»
  for (const [who, branch] of [[w.mgrA, A], [s, undefined]]) {
    for (const [m, p, body] of [['GET', `${INBOX}/${box.id}`], ['POST', `${INBOX}/${box.id}/resubmit`, {}], ['POST', `${INBOX}/${box.id}/dismiss`, { reason: 'دزد' }]]) {
      const r = await call(who, m, p, { branch, body });
      assert.equal(r.status, 404, `${m} ${p} ${JSON.stringify(r.body)}`);
    }
  }
  //  صاحب با سرآیندِ «ب» دوباره ثبت می‌کند ⇒ فاکتور در بخشِ «ب»، نه در اصلی؛ سابقه و برگهٔ تأیید در دکان با شعبه
  ok(await call(s, 'POST', `${INBOX}/${box.id}/resubmit`, { branch: Bid, body: {} }));
  assert.ok(await one(`SELECT 1 AS x FROM sync_v3_rows WHERE account_id=$1 AND table_name='sales' AND row_id='bsec-cf1'`, [`${s.shopId}#${Bid}`]));
  assert.equal(await one(`SELECT 1 AS x FROM sync_v3_rows WHERE account_id=$1 AND row_id='bsec-cf1'`, [s.shopId]), null);
  const log = await one("SELECT detail FROM audit_logs WHERE shop_id=$1 AND action='conflict_inbox.resubmit'", [s.shopId]);
  assert.equal(log.detail.branch, Bid);
  assert.ok(await one('SELECT 1 AS x FROM shop_approvals WHERE shop_id=$1 AND nonce=$2', [s.shopId, `cf:${box.id}`]));
  assert.deepEqual(await list(w.mgrB, Bid), [], 'بسته شد');
});

// ── خبرهای هر شعبه و زنگ ──────────────────────────────────────────────

test('خبرها: دفترِ جدای هر شعبه؛ «دیده شد» جدا؛ زنگ فقط به اعضای همان شعبه', async () => {
  const w = await world('خبر');
  const { s, A, Bid } = w;
  for (const who of [s, w.main, w.staffA, w.mgrA, w.staffB, w.mgrB]) {
    await query(`INSERT INTO push_tokens (id, app, token, provider, user_id, shop_id, status, created_at, updated_at)
                 VALUES ($1,'shop',$2,'fcm',$3,$4,'active',$5,$5)`, [newId('ptk'), `tok-${who.userId}`, who.userId, s.shopId, now()]);
  }
  const rang = [];
  push.setDeliver(async (row) => { rang.push(row.user_id); });
  try {
    ok(await call(w.mgrB, 'POST', EVENTS, { branch: Bid, body: { events: [{ kind: 'stock_out', title: 'bsec-تمام شد', clientId: 'e1' },
      { kind: 'stock_out', title: 'bsec-تمام شد', clientId: 'e1' }] } }), 201);
    assert.deepEqual(rang.sort(), [s.userId, w.staffB.userId].sort(), 'فقط صاحب و کارمندِ «ب» (نه کننده، نه «الف»، نه اصلی)');
    rang.length = 0;
    ok(await call(w.main, 'POST', EVENTS, { body: { kind: 'stock_out', title: 'msec-تمام شد' } }), 201);
    assert.ok(rang.includes(s.userId) && !rang.includes(w.main.userId));
  } finally { push.setDeliver(null); }
  const ev = (who, branch) => call(who, 'GET', EVENTS, { branch }).then(ok);
  const b = await ev(w.staffB, Bid);
  assert.deepEqual([b.events.map((e) => e.title), b.unread], [['bsec-تمام شد'], 1], 'تکراری با clientId یکی');
  assert.deepEqual((await ev(w.mgrA, A)).events, []);
  assert.deepEqual((await ev(s, undefined)).events.map((e) => e.title), ['msec-تمام شد']);
  ok(await call(w.staffB, 'POST', `${EVENTS}/seen`, { branch: Bid, body: {} }));
  assert.equal((await ev(w.staffB, Bid)).unread, 0);
  assert.equal((await ev(s, Bid)).unread, 1, '«دیده شد» ِ هر کاربر جدا');
  assert.equal((await one('SELECT COUNT(*)::int AS n FROM shop_events WHERE shop_id=$1', [s.shopId])).n, 1, 'خبرِ «ب» در دفترِ امروز نیست');
});

// ── مهاجرتِ ۹۳۰: برگشت و دوباره ─────────────────────────────────────────

test('مهاجرتِ ۹۳۰: برگشت فقط دادهٔ شعبه‌های دیگر را برمی‌دارد و کلیدهای امروز را برمی‌گرداند؛ دوباره بالا می‌آید', async () => {
  const w = await world('مهاجرت');
  const { s, Bid } = w;
  const m = await seed(w, s, w.main, undefined, 'msec', PIN.main);
  await seed(w, w.mgrB, w.staffB, Bid, 'bsec', PIN.b);
  const main = await rowsOf(s.shopId, '');
  const strip = (x) => JSON.parse(JSON.stringify(x, (k, v) => (k === 'branch_id' ? undefined : v)));
  const mainRead = await reads(s, undefined, m.po);
  const down = fs.readFileSync(path.join(__dirname, '..', 'migrations', 'rollback', '930_branch_doors.down.sql'), 'utf8');
  await query(down);
  assert.equal((await one("SELECT COUNT(*)::int AS n FROM schema_migrations WHERE version='930_branch_doors.sql'")).n, 0);
  const col = async (t) => (await one("SELECT COUNT(*)::int AS n FROM information_schema.columns WHERE table_name=$1 AND column_name='branch_id'", [t])).n;
  for (const t of ['shop_held_invoices', 'purchase_orders', 'purchase_receipts', 'purchase_bills', 'shop_till_sessions', 'shop_till_op_users']) {
    assert.equal(await col(t), 0, t);
  }
  assert.equal((await one("SELECT to_regclass('shop_branch_events') AS r")).r, null);
  const q = (sql) => many(sql, [s.shopId]);
  assert.deepEqual(JSON.parse(JSON.stringify({
    held: await q('SELECT * FROM shop_held_invoices WHERE shop_id=$1 ORDER BY id'),
    po: await q('SELECT * FROM purchase_orders WHERE shop_id=$1 ORDER BY id'),
  })), strip({ held: main.held, po: main.po }), 'شعبهٔ اصلی دست نخورد؛ «ب» رفت');
  //  کلیدهای امروز: همان شمارهٔ سفارش در همان دکان دوباره نه
  await assert.rejects(query(`INSERT INTO purchase_orders (shop_id, id, po_no, client_key, request_hash, supplier_id, state, lines, created_at, updated_at)
    SELECT shop_id, 'dup', po_no, 'k-dup', 'h', 's', 'draft', '[]'::jsonb, 1, 1 FROM purchase_orders WHERE shop_id=$1 LIMIT 1`, [s.shopId]));

  assert.deepEqual(await require('../src/migrate').run(), ['930_branch_doors.sql']);
  assert.deepEqual(await rowsOf(s.shopId, ''), main);
  assert.deepEqual(await reads(s, undefined, m.po), mainRead);
  //  «ب» دوباره از نو کار می‌کند
  ok(await call(w.mgrB, 'PUT', `${HELD}/bsec-h2`, { branch: Bid, body: { version: 1, body: inv('bsec-h2', 'تازه') } }));
  //  پاک شدنِ دکان ⇒ خبرِ شعبه‌ها هم (تابعِ پاک‌سازی)
  ok(await call(w.mgrB, 'POST', EVENTS, { branch: Bid, body: { kind: 'note', title: 'پایان' } }), 201);
  await query('DELETE FROM shops WHERE id=$1', [s.shopId]);
  assert.equal((await one("SELECT COUNT(*)::int AS n FROM shop_branch_events WHERE branch_key LIKE $1", [`${s.shopId}#%`])).n, 0);
  assert.equal((await one('SELECT COUNT(*)::int AS n FROM shop_held_invoices WHERE shop_id=$1', [s.shopId])).n, 0);
});
