'use strict';
/**
 * شعبه درونِ یک دکان — فازِ ۳ ِ سرور (`docs/BRANCHES-fa.md` §۸؛ پرچمِ خاموشِ `shop.branches`).
 *
 * از راهِ واقعی (درخواستِ HTTP):
 *   ⛔ پرچمِ خاموش ⇒ `/api/shop/catalog/*` بایت‌به‌بایت همان، با و بی سرآیندِ `X-Branch-Id`؛ پرچمِ روشن ⇒ شعبهٔ اصلی همان پاسخ و
 *      همان ردیف‌ها (`branch_id = ''`) حتی وقتی شعبه‌های دیگر موجودی دارند.
 *   ⛔ INV-01: موجودیِ سرورِ هر شعبه از دفترِ بخشِ خودش (`branch_id` ِ همان شعبه)؛ کالا همان کاتالوگِ مشترکِ دکان.
 *   ⛔ نفوذ: کارمندِ الف با سرآیندِ ب، شعبهٔ ساختگی، شناسهٔ ب در بدنه/پارامتر، وظیفهٔ ب با شناسهٔ واقعی ⇒ ۴۰۳/۴۰۴، هیچ ردیفِ ب.
 *   ⛔ آینهٔ کاتالوگ: کالای اصلی با op ِ سرور در بخشِ شعبه؛ ویرایش و حذف هم؛ بی تکرار.
 *   ⛔ RPT-18: گزارشِ هر شعبه نامِ کالا را از کاتالوگِ دکان و «کم‌موجود» را از موجودیِ همان شعبه؛ جمع = جمعِ شعبه‌ها.
 *   ⛔ مهاجرتِ ۹۰۰: برگشت فقط موجودیِ شعبه‌ها و ردیفِ خودش را برمی‌دارد؛ دوباره بالا آمدن و خواندنِ دوباره همان عددها.
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
const syncV1 = require('../src/lib/sync-v1');
const hlc = require('../src/lib/hlc');

let admin = '';

test.before(async () => {
  await h.start();
  await syncV1.syncSchemaTable();
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
    `INSERT INTO shop_members (id, shop_id, user_id, role, status, created_at, updated_at)
     VALUES ($1,$2,$3,$4,'active',$5,$5)`,
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
let tick = 0;
const H = () => hlc.format({ wall: Date.now() - 600_000 + (++tick) * 10, counter: 0, node: 'brstock' });
const oid = () => `01JBS${String(Date.now()).slice(-6)}${String(++opN).padStart(15, '0')}`;
const v3op = (table, rowId, fields, kind = 'insert') => ({ op_id: oid(), table, row_id: rowId, kind, hlc: H(), fields });
const product3 = (id, extra = {}) => v3op('products', id, {
  name: `کالای ${id}`, unit: 'دانه', salePriceMinor: 5000, purchasePriceMinor: 4000, active: true, ...extra,
});
const entry3 = (id, pid, units) => v3op('warehouseEntries', id, { productId: pid, unitsMilli: units * 1000, createdAt: Date.now() });
function sale3(id, pid, units, priceMinor = 5000) {
  const total = units * priceMinor;
  return [
    v3op('sales', id, {
      totalMinor: total, discountMinor: 0, finalTotalMinor: total, paidAmountMinor: total, remainingMinor: 0,
      paymentMethod: 'cash', status: 'completed', createdAt: Date.now(), date: '',
    }),
    v3op('saleItems', `${id}-i`, {
      saleId: id, productId: pid, quantityMilli: units * 1000, unitPriceMinor: priceMinor, totalPriceMinor: total, createdAt: Date.now(),
    }),
  ];
}

async function pushV3(who, ops, branch, { ok = true } = {}) {
  const r = await h.api('POST', '/api/sync/v3/push', {
    token: who.token, body: { device_id: who.device, protocol: 3, ops }, headers: B(branch),
  });
  if (ok) {
    assert.equal(r.status, 200, JSON.stringify(r.body));
    for (const x of r.body.results || []) assert.notEqual(x.status, 'rejected', JSON.stringify(x));
  }
  return r;
}
const v3 = (who, p, branch) => h.api('GET', `/api/sync/v3/${p}`, { token: who.token, headers: B(branch) });
const cat = (who, p, branch, { method = 'GET', body = null, key } = {}) => h.api(method, `/api/shop/catalog${p}`, {
  token: who.token, body, headers: { ...B(branch), ...(key ? { 'Idempotency-Key': key } : {}) },
});
let keyN = 0;
const fresh = () => `brstock-key-${++keyN}-${Date.now()}`;

async function levelOf(who, pid, branch) {
  const r = await cat(who, `/products/${pid}`, branch);
  assert.equal(r.status, 200, JSON.stringify(r.body));
  return r.body.product.stock.levels.shop_floor;
}

/** ردیف‌های موجودیِ سرورِ یک شعبه، همهٔ ستون‌ها (برای «دست نخورد») */
async function stockRows(shopId, branchId) {
  const out = {};
  out.moves = await many('SELECT * FROM stock_moves WHERE shop_id=$1 AND branch_id=$2 ORDER BY id', [shopId, branchId]);
  out.levels = await many('SELECT * FROM stock_levels WHERE shop_id=$1 AND branch_id=$2 ORDER BY product_id, location', [shopId, branchId]);
  out.tasks = await many('SELECT * FROM stock_tasks WHERE shop_id=$1 AND branch_id=$2 ORDER BY id', [shopId, branchId]);
  out.sources = branchId
    ? await many("SELECT * FROM stock_sources WHERE shop_id=$1 AND source_key LIKE $2 ORDER BY source_key, product_id", [shopId, `${branchId}|%`])
    : await many("SELECT * FROM stock_sources WHERE shop_id=$1 AND source_key NOT LIKE 'brn\\_%' ORDER BY source_key, product_id", [shopId]);
  return JSON.parse(JSON.stringify(out));
}

/** هر درِ خواندنیِ کاتالوگ برای یک کالا — پاسخِ کامل */
async function reads(who, pid, branch) {
  const out = {};
  for (const p of ['/config', '/products', `/products/${pid}`, `/moves?productId=${pid}`, '/tasks', '/report', `/report?productId=${pid}`]) {
    const r = await cat(who, p, branch);
    out[p] = [r.status, r.body];
  }
  return out;
}

/** دکانِ v3 با موجودیِ سرورِ روشن: کالای p1 (کمینه ۵ دانه) و ۱۰ دانه در شعبهٔ اصلی، سپس پرچمِ شعبه و دو شعبه */
async function world(tag, { branchesOn = true } = {}) {
  const s = await shop(`موجودیِ شعبه ${tag}`);
  await flag(s, 'shop.catalog');
  const st = await cat(s, '/settings', undefined, { method: 'PUT', body: { syncSource: 'v3' } });
  assert.equal(st.status, 200, JSON.stringify(st.body));
  await pushV3(s, [product3('p1', { minStockMilli: 5000 }), entry3('m-e1', 'p1', 10)]);
  assert.equal(await levelOf(s, 'p1'), 10000);
  const w = { s, main: await member(s, 'staff', `اصلی ${tag}`), onlyA: await member(s, 'staff', `الف ${tag}`),
    mgrA: await member(s, 'manager', `مدیرِ الف ${tag}`), mgrB: await member(s, 'manager', `مدیرِ ب ${tag}`) };
  if (!branchesOn) return w;
  await flag(s, 'shop.branches');
  const a = await h.post('/api/shop/branches', { name: 'شعبهٔ الف' }, { token: s.token });
  const b = await h.post('/api/shop/branches', { name: 'شعبهٔ ب' }, { token: s.token });
  assert.equal(a.status, 201); assert.equal(b.status, 201);
  w.A = a.body.branch.id;
  w.Bid = b.body.branch.id;
  for (const [m, ids] of [[w.onlyA, [w.A]], [w.mgrA, [w.A]], [w.mgrB, [w.Bid]]]) {
    const r = await h.put(`/api/shop/branches/members/${m.memberId}`, { branches: ids }, { token: s.token });
    assert.equal(r.status, 200, JSON.stringify(r.body));
  }
  return w;
}

// ── برابری: پرچمِ خاموش و شعبهٔ اصلی ──────────────────────────────────

test('پرچمِ خاموش ⇒ /shop/catalog بایت‌به‌بایت همان با و بی سرآیند؛ روشن ⇒ شعبهٔ اصلی همان پاسخ و همان ردیف‌ها', async () => {
  const w = await world('برابری', { branchesOn: false });
  const { s } = w;
  await pushV3(s, sale3('m-s1', 'p1', 2));
  const base = await reads(s, 'p1');
  assert.equal(base['/products/p1'][0], 200);
  assert.equal(base['/products/p1'][1].product.stock.levels.shop_floor, 8000);
  assert.equal(base['/products/p1'][1].product.stock.levels.shop_floor, 8000);
  const rows = await stockRows(s.shopId, '');
  assert.ok(rows.moves.length >= 2 && rows.sources.length >= 2, JSON.stringify(rows.moves.length));
  //  خاموش: سرآیندِ هر شعبه (حتی ساختگی) بی اثر — همان پاسخ، همان ردیف‌ها
  for (const fake of ['brn_000000000000000000000000', 'main', "x' OR 1=1 --"]) {
    assert.deepEqual(await reads(s, 'p1', fake), base, fake);
    assert.deepEqual(await reads(w.main, 'p1', fake), await reads(w.main, 'p1'), fake);
  }
  assert.deepEqual(await stockRows(s.shopId, ''), rows);
  assert.equal(base['/config'][1].branch, undefined);

  //  روشن + دو شعبه با موجودیِ خودشان ⇒ شعبهٔ اصلی (صاحب بی سرآیند، با `main` و با '') همان پاسخ و همان ردیف‌ها
  await flag(s, 'shop.branches');
  const a = await h.post('/api/shop/branches', { name: 'شعبهٔ الف' }, { token: s.token });
  const A = a.body.branch.id;
  await h.put(`/api/shop/branches/members/${w.onlyA.memberId}`, { branches: [A] }, { token: s.token });
  await pushV3(w.onlyA, [entry3('a-e1', 'p1', 4), ...sale3('a-s1', 'p1', 1)], A);
  assert.equal(await levelOf(w.onlyA, 'p1', A), 3000);
  assert.deepEqual(await reads(s, 'p1'), base);
  assert.deepEqual(await reads(s, 'p1', 'main'), base);
  assert.deepEqual(await reads(w.main, 'p1'), await reads(w.main, 'p1', ''));
  assert.deepEqual(await stockRows(s.shopId, ''), rows, 'ردیف‌های شعبهٔ اصلی دست نخورد');
  //  دوباره خاموش ⇒ سرآیندِ الف بی اثر و همان اصلی
  await flag(s, 'shop.branches', false);
  assert.deepEqual(await reads(s, 'p1', A), base);
  assert.deepEqual(await stockRows(s.shopId, ''), rows);
});

// ── INV-01: موجودیِ سرورِ هر شعبه ──────────────────────────────────────

test('INV-01: موجودیِ سرورِ هر شعبه از دفترِ همان شعبه؛ کالا همان کاتالوگِ دکان؛ حرکتِ دستی، آستانه و وظیفه فقط در همان شعبه', async () => {
  const w = await world('INV-01');
  const { s, A, Bid } = w;
  await pushV3(w.onlyA, [entry3('a-e1', 'p1', 4), ...sale3('a-s1', 'p1', 1)], A);
  await pushV3(w.mgrB, [entry3('b-e1', 'p1', 7)], Bid);
  assert.equal(await levelOf(s, 'p1'), 10000, 'اصلی');
  assert.equal(await levelOf(w.onlyA, 'p1', A), 3000, 'الف');
  assert.equal(await levelOf(w.mgrB, 'p1', Bid), 7000, 'ب');
  assert.equal(await levelOf(s, 'p1', A), 3000, 'صاحب در الف');
  //  فهرست: همان کالای دکان با موجودیِ همان شعبه
  const la = await cat(w.onlyA, '/products', A);
  assert.equal(la.status, 200, JSON.stringify(la.body));
  assert.deepEqual(la.body.items.map((p) => [p.id, p.name, p.stock.levels.shop_floor]), [['p1', 'کالای p1', 3000]]);
  //  دفتر و تطبیق: فقط حرکت‌های الف
  const ma = await cat(w.onlyA, '/moves', A);
  assert.equal(ma.body.items.reduce((x, m) => x + m.deltaOnHandMilli, 0), 3000);
  assert.ok(ma.body.items.every((m) => ['warehouseEntries', 'saleItems'].includes(m.refType) && m.refId.startsWith('a-')), JSON.stringify(ma.body.items));
  const rep = await cat(w.mgrA, '/report', A);
  assert.equal(rep.status, 200, JSON.stringify(rep.body));
  assert.equal(rep.body.ok, true, JSON.stringify(rep.body.mismatches));
  assert.equal((await cat(s, '/report')).body.ok, true);
  const keys = await many('SELECT source_key FROM stock_moves WHERE shop_id=$1 AND branch_id=$2', [s.shopId, A]);
  assert.ok(keys.length === 2 && keys.every((k) => k.source_key.startsWith(`${A}|dev:`)), JSON.stringify(keys));

  //  حرکتِ دستی در الف (مدیرِ الف) ⇒ فقط الف؛ همان کلید در الف دوباره ⇒ همان؛ کلیدِ هر شعبه جداست
  const key = fresh();
  const mv = await cat(w.mgrA, '/moves', A, { method: 'POST', key, body: { kind: 'adjust', productId: 'p1', location: 'shop_floor', qtyMilli: 500, reason: 'شمارش' } });
  assert.equal(mv.status, 201, JSON.stringify(mv.body));
  const again = await cat(w.mgrA, '/moves', A, { method: 'POST', key, body: { kind: 'adjust', productId: 'p1', location: 'shop_floor', qtyMilli: 500, reason: 'شمارش' } });
  assert.equal(again.status, 200); assert.equal(again.headers.get('idempotent-replayed'), 'true');
  assert.equal(await levelOf(w.mgrA, 'p1', A), 3500);
  assert.equal(await levelOf(s, 'p1'), 10000);
  assert.equal(await levelOf(w.mgrB, 'p1', Bid), 7000);
  //  دستکاریِ بدنه/پارامتر: شناسهٔ ب در بدنه و querystring با سرآیندِ الف ⇒ همچنان الف
  const forged = await h.api('POST', `/api/shop/catalog/moves?branch=${Bid}&branchId=${Bid}`, {
    token: w.mgrA.token, headers: { ...B(A), 'Idempotency-Key': fresh() },
    body: { kind: 'adjust', productId: 'p1', location: 'shop_floor', qtyMilli: -100, reason: 'دستکاری', branchId: Bid, branch: Bid },
  });
  assert.equal(forged.status, 201, JSON.stringify(forged.body));
  assert.equal(await levelOf(w.mgrA, 'p1', A), 3400);
  assert.equal(await levelOf(w.mgrB, 'p1', Bid), 7000, 'ب دست نخورد');

  //  آستانهٔ مکان و وظیفهٔ «کم‌موجود»: فقط الف
  const low = await cat(w.mgrA, '/products/p1/low', A, { method: 'PUT', body: { location: 'shop_floor', lowMilli: 4000 } });
  assert.equal(low.status, 200, JSON.stringify(low.body));
  assert.deepEqual((await cat(w.mgrA, '/products/p1', A)).body.lowByLocation, { shop_floor: 4000 });
  assert.deepEqual((await cat(s, '/products/p1')).body.lowByLocation, {});
  await cat(w.mgrA, '/moves', A, { method: 'POST', key: fresh(), body: { kind: 'adjust', productId: 'p1', location: 'shop_floor', qtyMilli: -100, reason: 'شکست' } });
  const ta = await cat(w.mgrA, '/tasks', A);
  assert.ok(ta.body.tasks.some((t) => t.kind === 'low_stock' && t.location === 'shop_floor'), JSON.stringify(ta.body));
  const tm = await cat(s, '/tasks');
  assert.ok(!tm.body.tasks.some((t) => ta.body.tasks.some((x) => x.id === t.id)), 'وظیفهٔ الف در اصلی نیست');
  assert.deepEqual((await cat(w.mgrB, '/tasks', Bid)).body.tasks, []);
  //  بستنِ وظیفهٔ الف با شناسهٔ واقعی از اصلی و از ب ⇒ «نیست»؛ از الف ⇒ بسته
  const tid = ta.body.tasks.find((t) => t.kind === 'low_stock').id;
  const body = { action: 'resolved', note: 'رسیدگی شد' };
  assert.equal((await cat(s, `/tasks/${tid}/resolve`, undefined, { method: 'POST', body })).status, 404);
  assert.equal((await cat(w.mgrB, `/tasks/${tid}/resolve`, Bid, { method: 'POST', body })).status, 404);
  assert.equal((await cat(w.mgrA, `/tasks/${tid}/resolve`, A, { method: 'POST', body })).status, 200);
  //  درهای سطحِ دکان یا فقط‌اصلی در شعبهٔ دیگر ⇒ ۴۰۳ branch_unsupported (صاحب هم)
  for (const [m, p, bd] of [['PUT', '/settings', { showQty: 'exact' }], ['GET', '/reserved'], ['GET', '/transfers'],
    ['POST', '/transfers', { from: 'shop_floor', to: 'warehouse', lines: [{ productId: 'p1', qtyMilli: 1000 }] }]]) {
    const r = await cat(s, p, A, { method: m, body: bd, key: fresh() });
    assert.equal(r.status, 403, `${m} ${p} ${JSON.stringify(r.body)}`);
    assert.equal(r.body.error.code, 'branch_unsupported', p);
  }
  //  کالای تازه فقط در اصلی ⇒ همان لحظه در فهرستِ الف (کاتالوگِ مشترک)، با موجودیِ صفرِ الف
  await pushV3(s, [product3('p2'), entry3('m-e2', 'p2', 3)]);
  const la2 = await cat(w.onlyA, '/products', A);
  assert.deepEqual(la2.body.items.map((p) => [p.id, p.stock.levels.shop_floor]), [['p1', 3300], ['p2', 0]]);
  assert.equal(await levelOf(s, 'p2'), 3000);
});

// ── نفوذ و سرآیندِ جعلی ────────────────────────────────────────────────

const CATALOG_DOORS = [
  ['GET', '/config'], ['PUT', '/settings', { showQty: 'exact' }], ['POST', '/refresh', {}], ['GET', '/products'],
  ['GET', '/products/p1'], ['PUT', '/products/p1/low', { location: 'shop_floor', lowMilli: 1 }], ['GET', '/moves'],
  ['GET', '/report'], ['GET', '/reserved'], ['GET', '/tasks'], ['POST', '/tasks/x/resolve', { action: 'resolved', note: 'x' }],
  ['POST', '/moves', { kind: 'adjust', productId: 'p1', location: 'shop_floor', qtyMilli: 1, reason: 'x' }],
  ['GET', '/transfers'], ['POST', '/transfers', { from: 'shop_floor', to: 'warehouse', lines: [{ productId: 'p1', qtyMilli: 1 }] }],
  ['POST', '/transfers/x/receive', {}], ['POST', '/transfers/x/reject', { reason: 'x' }],
];

test('نفوذ: کارمندِ الف با سرآیندِ ب، ساختگی، اصلی یا بی سرآیند ⇒ ۴۰۳ روی هر درِ کاتالوگ؛ هیچ ردیفِ ب و اصلی عوض نمی‌شود', async () => {
  const w = await world('نفوذ');
  const { s, A, Bid } = w;
  await pushV3(w.mgrB, [entry3('b-e1', 'p1', 7)], Bid);
  assert.equal(await levelOf(w.mgrB, 'p1', Bid), 7000);
  const beforeB = await stockRows(s.shopId, Bid);
  const beforeM = await stockRows(s.shopId, '');
  for (const who of [w.onlyA, w.mgrA]) {
    for (const [m, p, body] of CATALOG_DOORS) {
      for (const [claim, code] of [[Bid, 'branch_forbidden'], ['brn_abcdefabcdefabcdefabcdef', 'branch_forbidden'],
        ["x' OR 1=1 --", 'branch_forbidden'], ['main', 'branch_forbidden'], [undefined, 'branch_required']]) {
        resetAll();
        const r = await cat(who, p, claim, { method: m, body, key: fresh() });
        assert.equal(r.status, 403, `${m} ${p} ${claim} ${JSON.stringify(r.body)}`);
        assert.equal(r.body.error.code, code, `${m} ${p} ${claim}`);
        assert.ok(!JSON.stringify(r.body).includes('b-e1'), `${m} ${p}`);
      }
    }
  }
  //  کارمندِ اصلی روی الف یا ب ⇒ ۴۰۳؛ شعبهٔ بایگانی‌شده از دسترسی بیرون (حتی کارمندِ خودش)
  assert.equal((await cat(w.main, '/products', A)).body.error.code, 'branch_forbidden');
  await h.api('PATCH', `/api/shop/branches/${Bid}`, { token: s.token, body: { status: 'archived' } });
  assert.equal((await cat(w.mgrB, '/products', Bid)).status, 403);
  assert.equal((await pushV3(w.mgrB, [entry3('b-late', 'p1', 1)], Bid, { ok: false })).status, 403);
  assert.deepEqual(await stockRows(s.shopId, Bid), beforeB, 'ردیف‌های ب دست نخورد');
  assert.deepEqual(await stockRows(s.shopId, ''), beforeM, 'ردیف‌های اصلی دست نخورد');
  //  دکانِ دیگر: صاحبِ دکانِ دیگر با شناسهٔ واقعیِ الف ⇒ همان ۴۰۳ (شعبه در دکانِ خودش جست‌وجو می‌شود)
  const other = await world('بیگانه');
  const r = await cat(other.s, '/products', A);
  assert.equal(r.status, 403); assert.equal(r.body.error.code, 'branch_forbidden');
});

// ── آینهٔ کاتالوگ ───────────────────────────────────────────────────────

test('آینهٔ کاتالوگ: کالا، دسته و واحدِ اصلی با op ِ سرور در بخشِ شعبه؛ ویرایش و حذف هم؛ بی تکرار؛ نوشتنِ شعبه رد', async () => {
  const w = await world('آینه');
  const { s, A, Bid } = w;
  await pushV3(s, [v3op('productCategories', 'cat-1', { name: 'لبنیات' }), v3op('productUnits', 'u-1', { name: 'بسته' }),
    product3('p-bc', { barcodes: ['111'] }), product3('p-bc2', { barcodes: ['111'] })]);
  const rowsIn = async (key, table) => (await many(
    `SELECT row_id, data, deleted_at FROM sync_v3_rows WHERE app='shop' AND account_kind='shop' AND account_id=$1 AND table_name=$2`,
    [key, table])).map((r) => [r.row_id, r.deleted_at === null ? r.data.name : null]).sort((x, y) => (x[0] < y[0] ? -1 : 1));
  const KA = `${s.shopId}#${A}`;
  assert.deepEqual(await rowsIn(KA, 'products'), [], 'پیش از نخستین pull ِ الف هیچ');
  const p1 = await v3(w.onlyA, `pull?device_id=${w.onlyA.device}&since=0`, A);
  assert.equal(p1.status, 200, JSON.stringify(p1.body));
  const cat1 = p1.body.ops.filter((o) => ['products', 'productCategories', 'productUnits'].includes(o.table));
  assert.deepEqual(cat1.map((o) => o.row_id).sort(), ['cat-1', 'p-bc', 'p-bc2', 'p1', 'u-1']);
  assert.ok(cat1.every((o) => o.origin === 'server' && o.device_id === 'server' && o.op_id.startsWith('srv.cm.')), JSON.stringify(cat1));
  assert.deepEqual(await rowsIn(KA, 'products'), [['p-bc', 'کالای p-bc'], ['p-bc2', 'کالای p-bc2'], ['p1', 'کالای p1']]);
  //  پیامدهای op ِ دستگاه (بارکدِ تکراری) فقط در اصلی، نه در صندوقِ شعبه
  const cf = await v3(w.mgrA, 'conflicts', A);
  assert.equal(cf.status, 200);
  assert.ok(!cf.body.conflicts.some((c) => c.kind === 'duplicate_barcode'), JSON.stringify(cf.body.conflicts));
  //  دوباره ⇒ هیچ op ِ تازه
  const n0 = (await one('SELECT COUNT(*)::int AS n FROM sync_v3_ops WHERE account_id=$1', [KA])).n;
  const p2 = await v3(w.onlyA, `pull?device_id=${w.onlyA.device}&since=${p1.body.cursor}`, A);
  assert.deepEqual(p2.body.ops, []);
  assert.equal((await one('SELECT COUNT(*)::int AS n FROM sync_v3_ops WHERE account_id=$1', [KA])).n, n0);
  //  ویرایش و حذف در اصلی ⇒ همان در الف
  await pushV3(s, [v3op('products', 'p-bc', { name: 'شیرِ تازه', salePriceMinor: 7000 }, 'update'), { op_id: oid(), table: 'products', row_id: 'p-bc2', kind: 'delete', hlc: H() }]);
  const p3 = await v3(w.onlyA, `pull?device_id=${w.onlyA.device}&since=${p2.body.cursor}`, A);
  assert.deepEqual(p3.body.ops.map((o) => [o.row_id, o.kind]).sort(), [['p-bc', 'update'], ['p-bc2', 'delete']]);
  assert.deepEqual(await rowsIn(KA, 'products'), [['p-bc', 'شیرِ تازه'], ['p-bc2', null], ['p1', 'کالای p1']]);
  const snap = await v3(w.onlyA, 'snapshot', A);
  assert.equal(JSON.stringify(snap.body.tables.products).includes('شیرِ تازه'), true);
  //  نوشتنِ کاتالوگ در شعبه (کارمند و صاحب) ⇒ رد؛ ردیفِ آینه دست نمی‌خورد
  for (const [who, op] of [[w.mgrA, v3op('products', 'p-bc', { name: 'دستکاری' }, 'update')], [s, v3op('productCategories', 'cat-x', { name: 'x' })],
    [w.mgrA, v3op('productUnits', 'u-x', { name: 'x' })], [w.mgrA, { op_id: oid(), table: 'products', row_id: 'p1', kind: 'delete', hlc: H() }]]) {
    const r = await pushV3(who, [op], A, { ok: false });
    assert.equal(r.status, 200);
    assert.equal(r.body.results[0].reason, 'catalog_main_only', JSON.stringify(r.body));
  }
  assert.deepEqual(await rowsIn(KA, 'products'), [['p-bc', 'شیرِ تازه'], ['p-bc2', null], ['p1', 'کالای p1']]);
  //  ب تنبل است: تا نخستین pull/Snapshot ِ ب هیچ؛ Snapshot ِ ب همهٔ کاتالوگِ زنده
  assert.deepEqual(await rowsIn(`${s.shopId}#${Bid}`, 'products'), []);
  const sb = await v3(w.mgrB, 'snapshot', Bid);
  assert.equal(sb.status, 200);
  assert.deepEqual(await rowsIn(`${s.shopId}#${Bid}`, 'products'), [['p-bc', 'شیرِ تازه'], ['p1', 'کالای p1']]);
  //  اصلی هرگز نوشته نشد: همان ردیف‌ها و همان opهای دستگاه
  assert.equal((await one("SELECT COUNT(*)::int AS n FROM sync_v3_ops WHERE account_id=$1 AND origin='server'", [s.shopId])).n, 0);
  //  موجودیِ سرور: ردیفِ آینهٔ کالا در بخشِ شعبه کالای تازه‌ای نمی‌سازد (کاتالوگ از اصلی)
  const listed = (await cat(w.mgrA, '/products', A)).body.items.map((p) => p.id).sort();   //  خواندنِ شعبه کاتالوگِ اصلی را هم تازه می‌کند
  const cp = await many('SELECT product_id FROM catalog_products WHERE shop_id=$1 AND NOT deleted', [s.shopId]);
  assert.deepEqual(listed, cp.map((r) => r.product_id).sort());
  assert.deepEqual(listed, ['p-bc', 'p1']);
});

// ── RPT-18: گزارشِ هر شعبه و جمع ────────────────────────────────────────

test('RPT-18: گزارشِ هر شعبه با نامِ کالای دکان و کم‌موجودِ همان شعبه؛ جمع = جمعِ شعبه‌ها؛ کارمند فقط شعبهٔ خودش', async () => {
  const w = await world('گزارش');
  const { s, A, Bid } = w;
  await pushV3(s, sale3('m-s1', 'p1', 2));
  await pushV3(w.onlyA, [entry3('a-e1', 'p1', 4), ...sale3('a-s1', 'p1', 1, 6000)], A);
  await pushV3(w.mgrB, [entry3('b-e1', 'p1', 9), ...sale3('b-s1', 'p1', 3)], Bid);
  //  موجودیِ سرورِ هر شعبه خوانده شد: اصلی ۸، الف ۳، ب ۶ دانه
  assert.deepEqual([await levelOf(s, 'p1'), await levelOf(w.onlyA, 'p1', A), await levelOf(w.mgrB, 'p1', Bid)], [8000, 3000, 6000]);
  const own = await h.get('/api/shop/branches/report', { token: s.token });
  assert.equal(own.status, 200, JSON.stringify(own.body));
  const by = Object.fromEntries(own.body.branches.map((b) => [b.id, b.summary]));
  assert.deepEqual([by[''].invoices, by[A].invoices, by[Bid].invoices], [1, 1, 1]);
  assert.deepEqual([by[''].sales, by[A].sales, by[Bid].sales], [10000, 6000, 15000]);
  //  نامِ کالای الف از کاتالوگِ دکان (بخشِ الف هنوز هیچ کالایی ندارد — آینه پس از pull)
  assert.equal((await one('SELECT COUNT(*)::int AS n FROM sync_v3_rows WHERE account_id=$1 AND table_name=$2', [`${s.shopId}#${A}`, 'products'])).n, 0);
  assert.equal(by[A].topProducts[0].name, 'کالای p1', JSON.stringify(by[A].topProducts));
  //  کم‌موجود از موجودیِ همان شعبه (کمینهٔ کالا ۵ دانه): اصلی ۸ نه، الف ۳ بله، ب ۶ نه
  assert.deepEqual([by[''].lowStock, by[A].lowStock, by[Bid].lowStock], [0, 1, 0]);
  //  جمع = جمعِ شعبه‌ها؛ «کم‌موجود» جمع نمی‌شود
  for (const k of ['invoices', 'sales', 'netSales', 'cashNet', 'otherNet', 'discount', 'returns', 'returnCount']) {
    assert.equal(own.body.totals[k], by[''][k] + by[A][k] + by[Bid][k], k);
  }
  assert.equal(own.body.totals.sales, 31000);
  assert.equal(own.body.totals.lowStock, undefined);
  //  کارمندِ الف: فقط الف و بی جمع؛ ب با پارامتر ⇒ ۴۰۳
  const a = await h.get('/api/shop/branches/report', { token: w.onlyA.token });
  assert.deepEqual(a.body.branches.map((b) => [b.id, b.summary.sales, b.summary.lowStock]), [[A, 6000, 1]]);
  assert.equal(a.body.totals, undefined);
  assert.equal((await h.get(`/api/shop/branches/report?branch=${Bid}`, { token: w.onlyA.token })).status, 403);
  //  گزارشِ تنها ِ صاحب برای ب: بی جمع
  const ob = await h.get(`/api/shop/branches/report?branch=${Bid}`, { token: s.token });
  assert.deepEqual(ob.body.branches.map((b) => b.id), [Bid]);
  assert.equal(ob.body.totals, undefined);
});

// ── هم‌زمانی ────────────────────────────────────────────────────────────

test('هم‌زمانی: pull ِ هم‌زمانِ چند دستگاهِ شعبه یک آینه؛ حرکتِ دستیِ هم‌زمان با یک کلید یک حرکت؛ اصلی و شعبه با هم بی قاطی', async () => {
  const w = await world('هم‌زمانی');
  const { s, A } = w;
  await pushV3(s, [product3('pc1'), product3('pc2')]);
  const KA = `${s.shopId}#${A}`;
  const pulls = await Promise.all([1, 2, 3, 4, 5].map((i) => v3(w.onlyA, `pull?device_id=${w.onlyA.device.slice(0, 50)}-${i}&since=0`, A)));
  for (const p of pulls) assert.equal(p.status, 200, JSON.stringify(p.body));
  const dup = await many(
    "SELECT row_id, COUNT(*)::int AS n FROM sync_v3_ops WHERE account_id=$1 AND op_id LIKE 'srv.cm.%' GROUP BY row_id HAVING COUNT(*) > 1", [KA]);
  assert.deepEqual(dup, [], 'هر ردیفِ کاتالوگ یک بار آینه شد');
  const ids = (await many("SELECT row_id FROM sync_v3_rows WHERE account_id=$1 AND table_name='products'", [KA])).map((r) => r.row_id).sort();
  assert.deepEqual(ids, ['p1', 'pc1', 'pc2']);
  //  یک کلید، چهار درخواستِ هم‌زمان در الف ⇒ یک حرکت، بقیه همان پاسخ
  const key = fresh();
  const body = { kind: 'adjust', productId: 'p1', location: 'shop_floor', qtyMilli: 200, reason: 'شمارش' };
  const rs = await Promise.all([1, 2, 3, 4].map(() => cat(w.mgrA, '/moves', A, { method: 'POST', key, body })));
  assert.deepEqual(rs.map((r) => r.status).sort(), [200, 200, 200, 201], JSON.stringify(rs.map((r) => r.body)));
  //  اصلی و الف هم‌زمان با کلیدهای جدا ⇒ هر کدام فقط در خودش
  const both = await Promise.all([
    cat(s, '/moves', undefined, { method: 'POST', key: fresh(), body: { ...body, qtyMilli: 300 } }),
    cat(w.mgrA, '/moves', A, { method: 'POST', key: fresh(), body: { ...body, qtyMilli: 400 } }),
    cat(s, '/moves', A, { method: 'POST', key: fresh(), body: { ...body, qtyMilli: 50 } }),
    cat(s, '/moves', undefined, { method: 'POST', key: fresh(), body: { ...body, qtyMilli: 7 } }),
  ]);
  for (const r of both) assert.equal(r.status, 201, JSON.stringify(r.body));
  assert.equal(await levelOf(s, 'p1'), 10307);
  assert.equal(await levelOf(w.mgrA, 'p1', A), 650);
  assert.equal((await cat(s, '/report')).body.ok, true);
  assert.equal((await cat(w.mgrA, '/report', A)).body.ok, true);
});

// ── مهاجرتِ ۹۰۰: برگشت و دوباره ─────────────────────────────────────────

test('مهاجرتِ ۹۰۰: برگشت فقط موجودیِ شعبه‌ها و ردیفِ خودش را برمی‌دارد؛ دوباره بالا می‌آید و خواندنِ دوباره همان عددها', async () => {
  const w = await world('مهاجرت');
  const { s, A } = w;
  await pushV3(w.onlyA, [entry3('a-e1', 'p1', 4), ...sale3('a-s1', 'p1', 1)], A);
  assert.equal(await levelOf(w.onlyA, 'p1', A), 3000);
  await v3(w.onlyA, `pull?device_id=${w.onlyA.device}&since=0`, A);   //  آینه
  const main = await stockRows(s.shopId, '');
  const mainRead = await reads(s, 'p1');
  const has = async (t) => (await one('SELECT to_regclass($1) AS r', [t])).r !== null;
  assert.ok(await has('catalog_branch_state') && await has('shop_branch_mirror'));

  const down = path.join(__dirname, '..', 'migrations', 'rollback', '900_branch_stock.down.sql');
  await query(fs.readFileSync(down, 'utf8'));
  assert.equal((await one("SELECT COUNT(*)::int AS n FROM schema_migrations WHERE version='900_branch_stock.sql'")).n, 0);
  assert.ok(!(await has('catalog_branch_state')) && !(await has('shop_branch_mirror')));
  assert.equal((await one("SELECT COUNT(*)::int AS n FROM stock_moves WHERE branch_id <> ''")).n, 0);
  assert.equal((await one("SELECT COUNT(*)::int AS n FROM stock_sources WHERE source_key LIKE 'brn\\_%'")).n, 0);
  assert.deepEqual(await stockRows(s.shopId, ''), main, 'برگشت شعبهٔ اصلی را دست نزد');

  const done = await require('../src/migrate').run();
  assert.deepEqual(done, ['900_branch_stock.sql']);
  assert.ok(await has('catalog_branch_state') && await has('shop_branch_mirror'));
  //  موجودیِ الف از دفترِ بخشِ خودش دوباره ساخته می‌شود؛ اصلی همان
  assert.equal(await levelOf(w.onlyA, 'p1', A), 3000);
  assert.deepEqual(await reads(s, 'p1'), mainRead);
  assert.deepEqual(await stockRows(s.shopId, ''), main);
  //  آینه هم از نو (بی op ِ دوم: همان شناسه‌ها تکراری‌اند)
  const before = (await one('SELECT COUNT(*)::int AS n FROM sync_v3_ops WHERE account_id=$1', [`${s.shopId}#${A}`])).n;
  await v3(w.onlyA, `pull?device_id=${w.onlyA.device}&since=0`, A);
  assert.equal((await one('SELECT COUNT(*)::int AS n FROM sync_v3_ops WHERE account_id=$1', [`${s.shopId}#${A}`])).n, before);
  //  پاک شدنِ دکان ⇒ موجودیِ شعبه‌ها هم (تریگرِ تازه و cascade)
  await query('DELETE FROM shops WHERE id=$1', [s.shopId]);
  assert.equal((await one('SELECT COUNT(*)::int AS n FROM stock_moves WHERE shop_id=$1', [s.shopId])).n, 0);
  assert.equal((await one('SELECT COUNT(*)::int AS n FROM shop_branch_mirror WHERE shop_id=$1', [s.shopId])).n, 0);
});
