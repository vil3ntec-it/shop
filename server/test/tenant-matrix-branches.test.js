'use strict';
/**
 * ماتریسِ جداسازیِ **شعبه‌ها** درونِ یک دکان — همان روشِ `tenant-matrix.test.js` (دکان ⇐ دکان) برای شعبه ⇐ شعبه
 * (`docs/BRANCHES-fa.md` §۴–§۵، فازِ ۳؛ پرچمِ `shop.branches` روشن).
 *
 * فهرستِ درها از **خودِ سورس** (`route-inventory`): هر درِ `/api/shop/*` و هر درِ `/api/sync/v3/*`. شعبهٔ «ب» دادهٔ واقعی دارد
 * (دفترِ بخش، موجودیِ سرور، وظیفه، درخواستِ تأیید) با نشانِ `bsec`؛ مدیرِ «الف» (و کارمندِ «الف») با سرآیندِ «ب»، بی سرآیند،
 * با سرآیندِ «الف» و با شناسهٔ واقعیِ «ب» در نشانی/بدنه/پارامتر:
 *   ⛔ هیچ پاسخی نشانِ «ب» ندارد و هیچ ردیفِ «ب» عوض نمی‌شود (اثرِ انگشتِ همهٔ جدول‌های شعبه‌دار پیش و پس)؛
 *   ⛔ با سرآیندِ «ب» هیچ دری ۲xx نمی‌دهد مگر درهای بی دادهٔ شعبه که این‌جا یک‌به‌یک با دلیل نام برده شده‌اند — درِ تازه‌ای
 *      که با سرآیندِ «ب» پاسخ بدهد و در این فهرست نباشد آزمون را سرخ می‌کند؛
 *   ⛔ همگام‌سازیِ v3 با سرآیندِ «ب» ⇒ همه ۴۰۳.
 * آزمون بی‌اثر نیست: صاحب با سرآیندِ «ب» همان نشان را از کاتالوگ و همگام‌سازی می‌گیرد.
 *
 * فازِ ۳ب (مهاجرتِ ۹۳۰): «ب» در درهای تازهٔ شعبه‌شناس هم داده دارد — نگه‌داشته، سفارشِ خرید + رسید + فاکتور، نوبتِ صندوق و
 * «کاربرِ سنجیدهٔ صندوق» (S46)، سندِ ردشدهٔ صندوقِ تعارض، خبر — و `/api/events*` و `/api/me/backups*` (فقط اصلی) هم در فهرست‌اند.
 * شناسهٔ واقعیِ سفارش و تعارضِ «ب» روی درهای همان گروه، و سرآیندِ ساختگی (`brn_0…`) روی درهای ۳ب هم آزموده می‌شود.
 */
process.env.SYNC_V3_ENABLED = '1';
process.env.RATE_LOGIN_EDGE_MAX = process.env.RATE_LOGIN_EDGE_MAX || '100000';

const test = require('node:test');
const assert = require('node:assert/strict');
const inventory = require('./route-inventory');
const h = require('./helpers');
const { query, one, many, newId, now } = require('../src/db');
const { resetAll } = require('../src/middleware/ratelimit');
const hlc = require('../src/lib/hlc');

/** درهایی که با سرآیندِ «ب» هم پاسخ می‌دهند چون **دادهٔ هیچ شعبه‌ای** ندارند — هر کدام با دلیل. */
const BRANCH_FREE = {
  'GET /api/shop/branches': 'فهرستِ شعبه‌های خودِ همین عضو (سرآیند خوانده نمی‌شود؛ «ب» در آن نیست — جدا سنجیده می‌شود)',
  'GET /api/shop/branches/report': 'گزارشِ شعبه‌های خودِ همین عضو (پارامترِ `branch` ِ «ب» ⇒ ۴۰۳؛ جدا سنجیده می‌شود)',
  'GET /api/shop/approver-keys': 'کلیدِ تأییدکنندهٔ خودِ همین عضو — سطحِ دکان، بی دادهٔ شعبه (`branch-gate`)',
  'GET /api/shop/approval-requests': 'درخواست‌های شعبه‌های خودِ همین مدیر (STF-06؛ «ب» در آن نیست)',
  'GET /api/shop/approval-requests/config': 'پرچم و نقشِ خودِ عضو و توانِ سرور — بی دادهٔ هیچ شعبه‌ای',
  //  فازِ ۲ (`claude/e-rows-54p`): «دکانِ من» سطحِ دکان است و از `branch-gate` بیرون آمد تا برنامهٔ کارمندِ فقط شعبهٔ دیگر نقشش را بگیرد
  'GET /api/shop': 'دکان، نقش، مجوز و اشتراکِ خودِ همین عضو — سطحِ دکان، بی دادهٔ هیچ شعبه‌ای (نشانِ «ب» همچنان سنجیده می‌شود)',
  'GET /api/shop/me': 'همان «دکانِ من» — سطحِ دکان، بی دادهٔ هیچ شعبه‌ای',
};

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
  assert.ok(admin);
});
test.after(async () => { await h.stop(); });

async function member(s, role, name) {
  const u = await h.newUser(name, 'shop');
  const id = newId('mem');
  await query(
    `INSERT INTO shop_members (id, shop_id, user_id, role, status, created_at, updated_at) VALUES ($1,$2,$3,$4,'active',$5,$5)`,
    [id, s.shopId, u.user.id, role, now()]
  );
  return { memberId: id, userId: u.user.id, token: u.accessToken, device: `dev-m-${u.user.id}`.slice(0, 60) };
}
let opN = 0;
const H = () => hlc.format({ wall: Date.now() - 600_000 + (++opN) * 10, counter: 0, node: 'brmatrix' });
const op = (table, rowId, fields) => ({ op_id: `01JBM${String(++opN).padStart(21, '0')}`, table, row_id: rowId, kind: 'insert', hlc: H(), fields });

/** اثرِ انگشتِ همهٔ دادهٔ شعبهٔ «ب» */
async function printOf(shopId, branchId) {
  const k = `${shopId}#${branchId}`;
  const q = async (sql, p) => JSON.stringify(await many(sql, p));
  return [
    await q('SELECT op_id, server_seq, effect FROM sync_v3_ops WHERE account_id=$1 ORDER BY server_seq', [k]),
    await q('SELECT table_name, row_id, data, deleted_at, updated_seq FROM sync_v3_rows WHERE account_id=$1 ORDER BY table_name, row_id', [k]),
    await q('SELECT id, status FROM sync_v3_conflicts WHERE account_id=$1 ORDER BY id', [k]),
    await q('SELECT * FROM stock_moves WHERE shop_id=$1 AND branch_id=$2 ORDER BY id', [shopId, branchId]),
    await q('SELECT * FROM stock_levels WHERE shop_id=$1 AND branch_id=$2 ORDER BY product_id, location', [shopId, branchId]),
    await q('SELECT * FROM stock_tasks WHERE shop_id=$1 AND branch_id=$2 ORDER BY id', [shopId, branchId]),
    await q('SELECT * FROM shop_approval_requests WHERE shop_id=$1 AND branch_id=$2 ORDER BY id', [shopId, branchId]),
    await q('SELECT * FROM shop_branches WHERE shop_id=$1 AND id=$2', [shopId, branchId]),
    await q('SELECT user_id FROM shop_member_branches WHERE shop_id=$1 AND branch_id=$2 ORDER BY user_id', [shopId, branchId]),
    //  فازِ ۳ب
    await q('SELECT * FROM shop_held_invoices WHERE shop_id=$1 AND branch_id=$2 ORDER BY id', [shopId, branchId]),
    await q('SELECT * FROM purchase_orders WHERE shop_id=$1 AND branch_id=$2 ORDER BY id', [shopId, branchId]),
    await q('SELECT * FROM purchase_receipts WHERE shop_id=$1 AND branch_id=$2 ORDER BY id', [shopId, branchId]),
    await q('SELECT * FROM purchase_bills WHERE shop_id=$1 AND branch_id=$2 ORDER BY id', [shopId, branchId]),
    await q('SELECT * FROM shop_till_sessions WHERE shop_id=$1 AND branch_id=$2 ORDER BY id', [shopId, branchId]),
    await q('SELECT * FROM shop_till_op_users WHERE shop_id=$1 AND branch_id=$2 ORDER BY op_id', [shopId, branchId]),
    await q('SELECT * FROM shop_branch_events WHERE branch_key=$1 ORDER BY id', [k]),
    await q('SELECT * FROM shop_branch_event_reads WHERE branch_key=$1 ORDER BY user_id', [k]),
    await q('SELECT id, status, detail FROM sync_v3_conflicts WHERE account_id=$1 ORDER BY id', [k]),
  ];
}

/** درهای فازِ ۳ب (شعبه‌شناس با `branch-rest.scope`) */
const PHASE3B = /^\/api\/(shop\/(held-invoices|purchase-orders|conflict-inbox|till-pin)|events)(\/|$)/;

test('ماتریسِ شعبه: هیچ درِ /api/shop/* و /api/sync/v3/* دادهٔ شعبهٔ «ب» را به مدیر/کارمندِ «الف» نمی‌دهد و عوض نمی‌کند', async () => {
  //  دکان: v3، موجودیِ سرور، دو شعبه؛ مدیرِ «الف»، کارمندِ «الف»، مدیرِ «ب»
  const u = await h.newUser('ماتریسِ شعبه', 'shop');
  const made = await h.post('/api/shop', { name: 'دکانِ ماتریسِ شعبه' }, { token: u.accessToken });
  const s = { ...(await h.signIn(u, 'shop')), shopId: made.body.shop.id };
  s.token = s.accessToken;
  s.device = `dev-o-${s.shopId}`.slice(0, 60);
  for (const key of ['shop.catalog', 'shop.branches', 'shop.held_sync', 'shop.purchase_orders', 'shop.till_pin', 'shop.till_op_user']) {
    assert.equal((await h.put('/api/admin/pump/live-config', { scope: s.shopId, key, value: true }, { token: admin })).status, 200);
  }
  assert.equal((await h.put('/api/shop/catalog/settings', { syncSource: 'v3' }, { token: s.token })).status, 200);
  const A = (await h.post('/api/shop/branches', { name: 'الف' }, { token: s.token })).body.branch.id;
  const Bid = (await h.post('/api/shop/branches', { name: 'ب' }, { token: s.token })).body.branch.id;
  const mgrA = await member(s, 'manager', 'مدیرِ الف');
  const staffA = await member(s, 'staff', 'کارمندِ الف');
  const mgrB = await member(s, 'manager', 'مدیرِ ب');
  for (const [m, id] of [[mgrA, A], [staffA, A], [mgrB, Bid]]) {
    assert.equal((await h.put(`/api/shop/branches/members/${m.memberId}`, { branches: [id] }, { token: s.token })).status, 200);
  }
  const push = (who, ops, branch) => h.api('POST', '/api/sync/v3/push', {
    token: who.token, body: { device_id: who.device, protocol: 3, ops }, headers: { 'X-Branch-Id': branch },
  });
  //  کالای دکان (اصلی) و دادهٔ «ب» با نشان؛ «الف» هم دادهٔ خودش را دارد
  let r = await h.api('POST', '/api/sync/v3/push', { token: s.token, body: { device_id: s.device, protocol: 3, ops: [
    op('products', 'p1', { name: 'کالای مشترک', unit: 'دانه', salePriceMinor: 5000, minStockMilli: 50000, active: true })] } });
  assert.equal(r.status, 200);
  r = await push(mgrB, [op('warehouseEntries', 'bsec-e1', { productId: 'p1', unitsMilli: 7000, createdAt: Date.now() }),
    op('expenses', 'bsec-x1', { title: 'bsec-مصرف', amountMinor: 1234, date: '2026-11-14', createdAt: Date.now() }),
    op('sales', 'bsec-s1', { totalMinor: 900, discountMinor: 0, finalTotalMinor: 900, paidAmountMinor: 900, remainingMinor: 0,
      paymentMethod: 'cash', status: 'completed', createdAt: Date.now(), date: '' })], Bid);
  assert.equal(r.status, 200, JSON.stringify(r.body));
  assert.ok(r.body.results.every((x) => x.status === 'applied'), JSON.stringify(r.body.results));
  r = await push(mgrA, [op('warehouseEntries', 'asec-e1', { productId: 'p1', unitsMilli: 2000, createdAt: Date.now() })], A);
  assert.equal(r.status, 200);
  //  موجودیِ سرورِ «ب» (حرکت + وظیفهٔ «کم‌موجود») و درخواستِ تأییدِ «ب»
  const bp = await h.api('GET', '/api/shop/catalog/products/p1', { token: mgrB.token, headers: { 'X-Branch-Id': Bid } });
  assert.equal(bp.status, 200, JSON.stringify(bp.body));
  assert.equal(bp.body.product.stock.levels.shop_floor, 7000);
  const bTask = (await one('SELECT id FROM stock_tasks WHERE shop_id=$1 AND branch_id=$2', [s.shopId, Bid])).id;
  const bReq = newId('apr');
  await query(
    `INSERT INTO shop_approval_requests (id, shop_id, branch_id, request_key, requester_id, requester_device, perm, doc, reason,
       status, created_at, expires_at) VALUES ($1,$2,$3,'bsec-key',$4,'dev-b','sales.cancel','bsec-s1','bsec-دلیل','pending',$5,$6)`,
    [bReq, s.shopId, Bid, mgrB.userId, now(), now() + 3600_000]
  );
  //  فازِ ۳ب: دادهٔ «ب» در درهای تازهٔ شعبه‌شناس (همه با سرآیندِ سنجیدهٔ «ب»)
  const asB = (who, method, p, body, headers = {}) => h.api(method, p, { token: who.token, body, headers: { 'X-Branch-Id': Bid, ...headers } });
  const okB = async (pr, code = 200) => { const x = await pr; assert.equal(x.status, code, JSON.stringify(x.body)); return x.body; };
  await okB(asB(mgrB, 'PUT', '/api/shop/held-invoices/bsec-h1', { version: 1, body: { id: 'bsec-h1', number: 1, label: 'bsec-نگه‌داشته',
    heldAt: 1700000000000, device: 'B', lines: [{ productId: 'p1', quantity: 1.0, unitPrice: 5.0, specialPrice: 5.0, seller: 'bsec' }] } }));
  const bPo = (await okB(asB(mgrB, 'POST', '/api/shop/purchase-orders', { supplierId: 'bsec-sup', supplierName: 'bsec-تأمین', note: 'bsec-سفارش',
    lines: [{ productId: 'p1', name: 'کالای مشترک', unit: 'عدد', qtyMilli: 10000, priceMinor: 1000 }] }, { 'Idempotency-Key': 'bsec-po-key-1' }), 201)).order;
  await okB(asB(mgrB, 'POST', `/api/shop/purchase-orders/${bPo.id}/send`, {}));
  await okB(asB(mgrB, 'POST', `/api/shop/purchase-orders/${bPo.id}/receipts`, { id: 'bsec-rc1', lines: [{ lineId: bPo.lines[0].id, receivedMilli: 2000 }] }), 201);
  await okB(asB(mgrB, 'POST', `/api/shop/purchase-orders/${bPo.id}/bills`, { id: 'bsec-bl1', billNo: 'bsec-F1',
    lines: [{ lineId: bPo.lines[0].id, qtyMilli: 2000, totalMinor: 2000 }] }), 201);
  await okB(asB(mgrB, 'POST', '/api/events', { kind: 'stock_out', title: 'bsec-خبر', clientId: 'bsec-ev1' }), 201);
  await okB(h.api('PUT', `/api/shop/till-pin/members/${mgrB.memberId}`, { token: s.token, body: { pin: '730264' } }));
  await okB(asB(s, 'POST', '/api/shop/till-pin/verify', { memberId: mgrB.memberId, pin: '730264' }));
  await okB(asB(s, 'POST', '/api/sync/v3/push', { device_id: s.device, protocol: 3, ops: [
    { ...op('expenses', 'bsec-x46', { title: 'bsec-صندوق', amountMinor: 77, date: '2026-11-14', createdAt: Date.now() }),
      hlc: hlc.format({ wall: Date.now(), counter: 0, node: 'brmatrix' }) }] }));
  const s46 = await one(`SELECT till_member_id, basis FROM shop_till_op_users WHERE shop_id=$1 AND branch_id=$2 AND row_id='bsec-x46'`, [s.shopId, Bid]);
  assert.deepEqual([s46.till_member_id, s46.basis], [mgrB.memberId, 'session']);
  const bCf = newId('cf');
  await query(
    `INSERT INTO sync_v3_conflicts (id, app, account_kind, account_id, kind, table_name, row_id, loser_op_id, loser_device, detail, at, loser_value)
     VALUES ($1,'shop','shop',$2,'approval_required','sales','bsec-s1','bsec-op1','dev-b',$3::jsonb,$4,$5::jsonb)`,
    [bCf, `${s.shopId}#${Bid}`, JSON.stringify({ requester: mgrB.userId, perm: 'sales.credit', reason: 'bsec-دلیل' }), now(),
      JSON.stringify({ path: 'v3', ops: [{ op_id: 'bsec-op1', table: 'sales', row_id: 'bsec-s1', kind: 'insert', fields: { note: 'bsec' } }] })]
  );
  assert.equal((await h.api('GET', `/api/shop/conflict-inbox/${bCf}`, { token: s.token, headers: { 'X-Branch-Id': Bid } })).status, 200);
  //  بی‌اثر نیست: صاحب با سرآیندِ «ب» نشانِ «ب» را می‌گیرد (pull ِ صاحب آینهٔ کاتالوگ را هم در «ب» می‌نشاند)
  const ownB = await h.api('GET', '/api/shop/catalog/moves', { token: s.token, headers: { 'X-Branch-Id': Bid } });
  assert.ok(JSON.stringify(ownB.body).includes('bsec-e1'), JSON.stringify(ownB.body));
  const ownPull = await h.api('GET', `/api/sync/v3/pull?device_id=dev-own-x&since=0`, { token: s.token, headers: { 'X-Branch-Id': Bid } });
  assert.ok(JSON.stringify(ownPull.body).includes('bsec-x1'));
  const before = await printOf(s.shopId, Bid);

  const ids = [bTask, bReq, 'bsec-s1', Bid];
  const doors = inventory.routes('/shop').map((d) => ({ method: d.method, key: `${d.method} /api${d.path}`, path: `/api${d.path}` }));
  for (const p of ['hello', 'push', 'pull', 'snapshot', 'deleted', 'status', 'report', 'conflicts', 'conflicts/:id/resolve']) {
    const method = ['push', 'deleted', 'report', 'conflicts/:id/resolve'].includes(p) ? 'POST' : 'GET';
    doors.push({ method, key: `${method} /api/sync/v3/${p}`, path: `/api/sync/v3/${p}`, v3: true });
  }
  //  فازِ ۳ب: خبرها (شعبه‌شناس) و پشتیبانِ کامل (فقط اصلی، `branch-gate`) — بیرون از `/api/shop`
  for (const d of inventory.routes('/events')) doors.push({ method: d.method, key: `${d.method} /api${d.path}`, path: `/api${d.path}` });
  for (const [method, p] of [['GET', '/me/backups'], ['POST', '/me/backups'], ['GET', '/me/backups/:id'], ['DELETE', '/me/backups/:id']]) {
    doors.push({ method, key: `${method} /api${p}`, path: `/api${p}` });
  }
  assert.ok(doors.length > 300, `فهرستِ درها کامل نیست (${doors.length})`);
  assert.ok(doors.filter((d) => PHASE3B.test(d.path)).length >= 33, 'درهای ۳ب در فهرست نیستند');
  const FAKE = 'brn_000000000000000000000000';
  const answeredB = new Set();
  const leaks = [];
  let calls = 0;
  for (const who of [mgrA, staffA]) {
    for (const d of doors) {
      const p3b = PHASE3B.test(d.path);
      //  درهای ۳ب: شناسهٔ واقعیِ سفارش و تعارضِ «ب» هم در نشانی/بدنه
      for (const id of [ids[0], ids[2], ...(p3b ? [bPo.id, bCf] : [])]) {
        const url = d.path.replace(/:\w+/g, encodeURIComponent(id)) + `?branch=${Bid}&branchId=${Bid}&id=${encodeURIComponent(id)}`;
        const body = d.method === 'GET' ? null : {
          id, branch: Bid, branchId: Bid, branch_id: Bid, productId: 'p1', device_id: who.device, protocol: 3,
          ops: [op('expenses', `mx-${++calls}`, { title: 'x', amountMinor: 1, date: '2026-11-14', createdAt: Date.now() })],
          rows: [{ table: 'expenses', row_id: 'bsec-x1' }], action: 'keep', name: 'x', note: 'x', reason: 'x',
        };
        for (const claim of p3b ? [Bid, undefined, A, FAKE] : [Bid, undefined, A]) {
          resetAll();
          const res = await h.api(d.method, url, { token: who.token, body, headers: claim === undefined ? {} : { 'X-Branch-Id': claim } });
          calls += 1;
          const text = JSON.stringify(res.body || '');
          if (text.includes('bsec')) leaks.push(`${d.key} [${claim === undefined ? 'بی سرآیند' : claim === A ? 'الف' : claim === FAKE ? 'ساختگی' : 'ب'}] ${res.status}`);
          if (claim === Bid && res.status >= 200 && res.status < 300) answeredB.add(d.key);
          if (claim === Bid && d.v3) assert.equal(res.status, 403, `${d.key} ${text}`);
          if (claim === Bid && d.path.startsWith('/api/shop/catalog')) assert.equal(res.status, 403, `${d.key} ${text}`);
          //  ۳ب: سرآیندِ «ب» یا ساختگی ⇒ ۴۰۳ ِ شعبه (هرگز ۲xx، هرگز «نیست» که وجود را بشمارد)
          if (p3b && (claim === Bid || claim === FAKE)) {
            assert.equal(res.status, 403, `${d.key} [${claim}] ${text}`);
            assert.ok(['branch_forbidden', 'branch_unsupported'].includes(res.body.error.code), `${d.key} ${text}`);
          }
        }
      }
    }
  }
  assert.deepEqual(leaks, [], 'نشانِ «ب» در پاسخ');
  assert.deepEqual([...answeredB].filter((k) => !BRANCH_FREE[k]).sort(), [], 'درِ تازه‌ای با سرآیندِ «ب» پاسخ داد');
  assert.deepEqual(await printOf(s.shopId, Bid), before, 'دادهٔ «ب» عوض شد');
  //  درهای بی دادهٔ شعبه: «ب» در پاسخِ کارمندِ «الف» نیست
  const mine = await h.get('/api/shop/branches', { token: mgrA.token });
  assert.deepEqual(mine.body.branches.map((b) => b.id), [A]);
  assert.ok(calls > 2000, `شمارِ درخواست‌ها ${calls}`);
});
