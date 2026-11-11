'use strict';
/**
 * S86 (§۱۹.۱۲) — بازسازیِ projection ِ نگه‌داشتهٔ موجودی (`stock_reservations` ِ نرمِ O-06 ب، پرچمِ `shop.stock_reserve`)
 * از دفترِ موجودی و سفارش‌های باز: `stock-reserve.rebuild` و `npm run stock-rebuild -- --shop <id> --reserve [--apply]`.
 *
 * آن‌چه این پرونده قفل می‌کند:
 *   ۱) پیش‌فرض فقط نشان می‌دهد (هیچ نوشتن، هیچ سابقه)؛ با apply هر پنج ناهمخوانی درست می‌شود — ردیفِ گم (از دفتر، همان شناسه)،
 *      سفارشِ بسته، مهلتِ گذشته، فروشِ تحویلِ نشسته، قلمِ عوض‌شده — و تطبیقِ INV-16 (`stock.reconcile`) پس از آن سالم است.
 *   ۲) زیرِ قفلِ موجودیِ دکان، یک تراکنش با یک سابقهٔ `reserve.rebuild`؛ بارِ دوم بی‌اثر؛ دکانِ دیگر دست نمی‌خورد.
 *   ۳) projection پاک ⇒ از نو همان شمار، شناسه، قلم، مقدار، مهلت و جای صف (S86: «حذف و بازسازی، تطبیقِ تعداد و وضعیت»).
 *   ۴) ⛔ R6: پرچمِ خاموش ⇒ قلمِ بزرگ‌شده فقط آزاد می‌شود (نگه‌داشتنِ تازه نه)؛ دستور: کدِ ۲ / ۰.
 */
process.env.RATE_LOGIN_EDGE_MAX = process.env.RATE_LOGIN_EDGE_MAX || '100000';

const test = require('node:test');
const assert = require('node:assert/strict');
const h = require('./helpers');
const { query, one, many, newId, now } = require('../src/db');
const syncV1 = require('../src/lib/sync-v1');
const stock = require('../src/lib/stock');

let admin = '';
const lib = () => require('../src/lib/stock-reserve');

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

// ── کمکی‌ها (همان شکلِ stock-reserve.test.js) ─────────────────────────

async function live(scope, key, value) {
  const r = await h.put('/api/admin/pump/live-config', { scope, key, value }, { token: admin });
  assert.equal(r.status, 200, JSON.stringify(r.body));
}
async function shop(name) {
  const u = await h.newUser(name, 'shop');
  const made = await h.post('/api/shop', { name: `دکانِ ${name}` }, { token: u.accessToken });
  assert.equal(made.status, 201, JSON.stringify(made.body));
  const s = await h.signIn(u, 'shop');
  const out = { token: s.accessToken, shopId: made.body.shop.id, device: `dev-rr-${made.body.shop.id}`.slice(0, 60) };
  for (const key of ['shop.orders', 'shop.catalog', 'shop.stock_reserve']) await live(out.shopId, key, true);
  return out;
}

let opN = 0;
const opId = () => `01JRR${String(Date.now()).slice(-8)}${String(++opN).padStart(8, '0')}`;
const op = (table, rowId, fields) => ({ op_id: opId(), ts: Date.now(), table, row_id: rowId, type: 'insert', fields });
async function stocked(s, pid, units) {
  const r = await h.api('POST', '/api/sync/v1/push', {
    token: s.token, headers: { 'X-App': 'shop', 'X-Device': s.device },
    body: { device_id: s.device, schema_version: 2, ops: [
      op('products', pid, { name: `کالای ${pid}`, unit: 'دانه', salePrice: 50, purchasePrice: 40, active: true, barcodes: [], createdAt: Date.now() }),
      op('warehouseEntries', `${pid}-e`, { productId: pid, units, cartons: 0, perCarton: 0, unit: 'دانه', price: 40, createdAt: Date.now() }),
    ] },
  });
  assert.equal(r.status, 200, JSON.stringify(r.body));
  assert.equal((await h.post('/api/shop/catalog/refresh', {}, { token: s.token })).status, 200);
}
let keyN = 0;
const line = (pid, qtyMilli) => ({ productId: pid, name: `کالای ${pid}`, unit: 'دانه', qtyMilli, unitPriceMinor: 5000 });
async function order(s, lines) {
  const r = await h.post('/api/shop/orders', {
    source: 'phone', fulfillment: 'pickup', paymentMethod: 'cod', customer: { name: 'مشتری' }, lines,
  }, { token: s.token, headers: { 'Idempotency-Key': `rsv-rebuild-${++keyN}-${Date.now()}` } });
  assert.equal(r.status, 201, JSON.stringify(r.body));
  assert.equal(r.body.order.stockHold.status, 'held', 'نگه‌داشته ساخته شد');
  return r.body.order;
}
const go = async (s, id, to) => assert.equal((await h.post(`/api/shop/orders/${id}/transition`, { to }, { token: s.token })).status, 200);

const held = (s) => many(`SELECT * FROM stock_reservations WHERE shop_id=$1 AND status='held' ORDER BY created_at, id`, [s.shopId]);
/** هر ردیفِ دکان، بی ستون‌های بستن — برای «هیچ نوشتنی» */
const rows = (s) => many('SELECT id, status, qty_milli, expires_at FROM stock_reservations WHERE shop_id=$1 ORDER BY id', [s.shopId]);
const moveCount = async (s) => Number((await one('SELECT COUNT(*)::int AS n FROM stock_moves WHERE shop_id=$1', [s.shopId])).n);
const audits = (s) => many(`SELECT * FROM audit_logs WHERE shop_id=$1 AND action='reserve.rebuild'`, [s.shopId]);
const reconciled = async (s) => (await stock.reconcile(s.shopId)).ok;
const view = (r) => ({ order: r.order_id, line: Number(r.line_no), product: r.product_id, qty: Number(r.qty_milli),
  expires: r.expires_at === null ? null : Number(r.expires_at), created: Number(r.created_at) });

// ════════════════════════════════════════════════════════════════════

test('S86 نگه‌داشته: نمایش بی نوشتن؛ apply ⇒ گم، بسته، گذشته، تحویل و قلمِ عوض‌شده درست، INV-16 سالم؛ دکانِ دیگر دست‌نخورده؛ بارِ دوم بی‌اثر', async () => {
  const a = await shop('بازسازیِ رزرو');
  const b = await shop('همسایهٔ بازسازیِ رزرو');
  await stocked(a, 'p1', 10);
  await stocked(a, 'p2', 10);
  await stocked(b, 'p1', 10);
  const o1 = await order(a, [line('p1', 2000)]);                      // تازه — قلمش کوچک می‌شود
  const o2 = await order(a, [line('p1', 1000), line('p2', 1000)]);    // قبول — p2 گم، p1 فروخته
  await go(a, o2.id, 'accepted');
  const o3 = await order(a, [line('p2', 1000)]);                      // بسته بی گذار
  const o4 = await order(a, [line('p1', 1000)]);                      // مهلت گذشته
  await order(b, [line('p1', 3000)]);
  assert.equal((await held(a)).length, 5);
  assert.equal(await reconciled(a), true, 'پیش از خرابی سالم');

  //  projection ِ خراب (منبع درست است)
  const lost = await one(`SELECT * FROM stock_reservations WHERE order_id=$1 AND product_id='p2'`, [o2.id]);
  await query('DELETE FROM stock_reservations WHERE id=$1', [lost.id]);
  await query(`UPDATE online_orders SET status='cancelled' WHERE id=$1`, [o3.id]);
  await query('UPDATE stock_reservations SET expires_at=$2 WHERE order_id=$1', [o4.id, Date.now() - 1000]);
  await query('UPDATE online_order_lines SET qty_milli=1000 WHERE order_id=$1', [o1.id]);
  const sold = await one(`SELECT id, line_no FROM stock_reservations WHERE order_id=$1 AND product_id='p1'`, [o2.id]);
  await query(`INSERT INTO stock_sources (shop_id, source_key, product_id, posted_milli, rev, updated_at) VALUES ($1,$2,'p1',1000,1,$3)`,
    [a.shopId, `si:o-${o2.id}-${sold.line_no}`, Date.now()]);
  const was1 = await one('SELECT * FROM stock_reservations WHERE order_id=$1', [o1.id]);
  await query('DELETE FROM stock_reservations WHERE shop_id=$1', [b.shopId]);   //  دکانِ دیگر: خرابیِ خودش
  assert.equal(await reconciled(a), false, 'تطبیق ناهمخوانی را می‌بیند');

  const rowsA = await rows(a);
  const movesA = await moveCount(a);
  const name = { [o1.id]: 'o1', [o2.id]: 'o2', [o3.id]: 'o3', [o4.id]: 'o4' };
  const dry = await lib().rebuild(a.shopId);
  assert.equal(dry.applied, false);
  assert.equal(dry.ok, false);
  assert.equal(dry.flag, true);
  assert.deepEqual(dry.changes.map((x) => `${x.kind}:${name[x.orderId]}:${x.productId}:${x.qtyMilli}${x.toMilli !== undefined ? `>${x.toMilli}` : ''}`).sort(),
    ['committed:o2:p1:1000', 'expired:o4:p1:1000', 'released:o3:p2:1000', 'resized:o1:p1:2000>1000', 'restored:o2:p2:1000']);
  assert.deepEqual(dry.count, { projection: 4, source: 2 });
  assert.deepEqual(await rows(a), rowsA, 'نمایش هیچ ردیفی نمی‌نویسد');
  assert.equal(await moveCount(a), movesA, 'نمایش دفتر را نمی‌نویسد');
  assert.equal((await audits(a)).length, 0);

  const done = await lib().rebuild(a.shopId, { apply: true, by: 'آزمون' });
  assert.equal(done.applied, true);
  assert.equal(done.ok, true);
  assert.equal(done.changes.length, 5);
  const now1 = await held(a);
  assert.deepEqual(now1.map((r) => `${name[r.order_id]}:${r.product_id}:${r.qty_milli}`).sort(), ['o1:p1:1000', 'o2:p2:1000']);
  const back = now1.find((r) => r.order_id === o2.id);
  assert.equal(back.id, lost.id, 'ردیفِ گم با همان شناسهٔ دفتر');
  assert.equal(back.expires_at, null, 'سفارشِ قبول‌شده ⇒ تا پایانِ سفارش');
  const small = now1.find((r) => r.order_id === o1.id);
  assert.notEqual(small.id, was1.id);
  assert.equal(Number(small.expires_at), Number(was1.expires_at), 'همان مهلتِ سفارشِ تازه');
  assert.equal(Number(small.created_at), Number(was1.created_at), 'کوچک شد ⇒ جای صف همان');
  const st = async (id) => one('SELECT status, close_reason FROM stock_reservations WHERE id=$1', [id]);
  assert.deepEqual(await st(was1.id), { status: 'released', close_reason: 'rebuild_line_changed' });
  assert.deepEqual(await st(sold.id), { status: 'committed', close_reason: 'fulfilled' });
  assert.equal((await one('SELECT status FROM stock_reservations WHERE order_id=$1', [o3.id])).status, 'released');
  assert.equal((await one('SELECT status FROM stock_reservations WHERE order_id=$1', [o4.id])).status, 'expired');
  assert.equal(await reconciled(a), true, JSON.stringify((await stock.reconcile(a.shopId)).mismatches));
  const log = await audits(a);
  assert.equal(log.length, 1);
  assert.equal(log[0].user_id, 'آزمون');
  assert.equal(log[0].detail.changes, 5);
  //  سفارش‌ها دست نخوردند؛ دکانِ دیگر همان خرابیِ خودش را دارد
  assert.equal((await one('SELECT status FROM online_orders WHERE id=$1', [o2.id])).status, 'accepted');
  assert.equal((await held(b)).length, 0);
  assert.equal(await reconciled(b), false);

  const again = await lib().rebuild(a.shopId, { apply: true });
  assert.equal(again.ok, true);
  assert.equal(again.applied, false);
  assert.deepEqual(again.changes, []);
  assert.equal((await audits(a)).length, 1, 'بی تغییر ⇒ بی سابقهٔ تازه');
});

test('S86 نگه‌داشته: projection پاک ⇒ از نو همان شمار، شناسه، قلم، مقدار، مهلت و جای صف؛ آزادِ ویترین همان', async () => {
  const s = await shop('رزرو-از-نو');
  await stocked(s, 'q1', 10);
  await stocked(s, 'q2', 5);
  await order(s, [line('q1', 2000), line('q2', 1000)]);
  const n2 = await order(s, [line('q1', 1000)]);
  await go(s, n2.id, 'accepted');
  const n3 = await order(s, [line('q2', 1000)]);
  const rj = await h.post(`/api/shop/orders/${n3.id}/transition`, { to: 'rejected', reason: 'out_of_stock' }, { token: s.token });
  assert.equal(rj.status, 200, JSON.stringify(rj.body));
  const before = (await held(s)).map(view);
  assert.equal(before.length, 3);
  assert.ok(before.every((r) => r.order !== n3.id), 'ردشده آزاد است');
  const free = async () => (await h.get('/api/shop/catalog/products/q1', { token: s.token })).body.product.stock;
  const stockBefore = await free();
  assert.equal(stockBefore.reservedMilli, 3000);

  await query('DELETE FROM stock_reservations WHERE shop_id=$1', [s.shopId]);
  assert.equal(await reconciled(s), false);
  const r = await lib().rebuild(s.shopId, { apply: true, by: 'آزمون' });
  assert.deepEqual(r.count, { projection: 0, source: 3 });
  assert.deepEqual(r.changes.map((x) => x.kind), ['restored', 'restored', 'restored']);
  assert.deepEqual((await held(s)).map(view), before, 'همان ردیف‌ها');
  assert.equal(await reconciled(s), true, JSON.stringify((await stock.reconcile(s.shopId)).mismatches));
  assert.deepEqual(await free(), stockBefore, 'موجودیِ آزاد همان');
  assert.equal((await lib().rebuild(s.shopId)).ok, true);
});

test('S86 نگه‌داشته ⛔ R6: پرچمِ خاموش ⇒ قلمِ بزرگ‌شده فقط آزاد؛ دستورِ stock-rebuild --reserve کدِ ۲ و بی نوشتن، با --apply کدِ ۰', async () => {
  const cli = require('../scripts/stock-rebuild');
  const s = await shop('رزرو-خاموشِ-بازسازی');
  await stocked(s, 'w1', 10);
  const o = await order(s, [line('w1', 1000)]);
  await query('UPDATE online_order_lines SET qty_milli=3000 WHERE order_id=$1', [o.id]);
  await live(s.shopId, 'shop.stock_reserve', false);
  const out = [];
  assert.equal(await cli.main(['--shop', s.shopId, '--reserve'], (x) => out.push(x)), 2);
  assert.match(out.join('\n'), /resized/);
  assert.equal((await held(s)).length, 1, 'بی --apply هیچ نوشتنی');
  out.length = 0;
  assert.equal(await cli.main(['--shop', s.shopId, '--reserve', '--apply', '--by', 'اپراتور', '--json'], (x) => out.push(x)), 0);
  const res = JSON.parse(out.join('\n')).reserve;
  assert.equal(res.flag, false);
  assert.deepEqual(res.changes.map((x) => [x.kind, x.qtyMilli, x.toMilli]), [['resized', 1000, 0]]);
  assert.equal((await held(s)).length, 0, 'خاموش ⇒ نگه‌داشتنِ بزرگ‌تر ساخته نمی‌شود');
  assert.equal(await reconciled(s), true);
  assert.equal(await cli.main(['--shop', s.shopId, '--reserve'], () => {}), 0);
  assert.equal((await audits(s))[0].user_id, 'اپراتور');
});

//  SR47-1: projection ِ کهنه (مثلاً ردیف‌های `stock_reservations` از پشتیبانِ پیشین) ردیفی را «نگه‌داشته» می‌گوید که دفتر پیش‌تر
//  بسته است (`res:<id>:release|commit`). داور دفتر است: همان ردیف بی هیچ حرکتِ تازه بسته می‌شود — نه آزادسازیِ دوم با پسوندِ دیگر
//  (دفترِ فقط‌افزودنی برای همیشه با نگه‌داشته‌ها نمی‌خواند)، نه ماندنِ «نگه‌داشتهٔ شبح» که نگه‌داشتنِ بازِ واقعی را کنار می‌زند.
test('SR47 نگه‌داشته: ردیفِ «نگه‌داشته» ای که دفتر بسته است ⇒ بی حرکتِ تازه بسته می‌شود؛ نگه‌داشتنِ بازِ دفتر جایش می‌آید؛ INV-16 سالم', async () => {
  const s = await shop('رزرو-کهنه');
  await stocked(s, 'k1', 10);
  await stocked(s, 'k2', 10);
  const moves = async (id) => (await many(`SELECT source_key FROM stock_moves WHERE shop_id=$1 AND source_key LIKE $2 ORDER BY source_key`,
    [s.shopId, `res:${id}:%`])).map((m) => m.source_key.split(':')[2]);

  //  ۱) تحویل‌شده (فروشِ قلم در دفتر ⇒ `commit`)، سپس کالا برگشت ⇒ سفارش در RELEASE_ON
  const a = await order(s, [line('k1', 1000)]);
  await go(s, a.id, 'accepted');
  const x = await one('SELECT * FROM stock_reservations WHERE order_id=$1', [a.id]);
  await query(`INSERT INTO stock_sources (shop_id, source_key, product_id, posted_milli, rev, updated_at) VALUES ($1,$2,'k1',1000,1,$3)`,
    [s.shopId, `si:o-${a.id}-${x.line_no}`, Date.now()]);
  assert.equal((await lib().rebuild(s.shopId, { apply: true })).changes.map((c) => c.kind).join(), 'committed');
  assert.deepEqual(await moves(x.id), ['commit', 'hold']);
  await query(`UPDATE online_orders SET status='refunded_full' WHERE id=$1`, [a.id]);

  //  ۲) قلمِ ۱ → ۲ → ۱ کیلو: X آزاد، Y آزاد، Z نگه‌داشته (هر سه در دفتر)
  const b = await order(s, [line('k2', 1000)]);
  const y0 = await one('SELECT * FROM stock_reservations WHERE order_id=$1', [b.id]);
  await query('UPDATE online_order_lines SET qty_milli=2000 WHERE order_id=$1', [b.id]);
  await lib().rebuild(s.shopId, { apply: true });
  await query('UPDATE online_order_lines SET qty_milli=1000 WHERE order_id=$1', [b.id]);
  await lib().rebuild(s.shopId, { apply: true });
  const z = await one(`SELECT * FROM stock_reservations WHERE order_id=$1 AND status='held'`, [b.id]);
  assert.notEqual(z.id, y0.id);
  assert.equal(await reconciled(s), true);

  //  پشتیبانِ کهنهٔ projection برگردانده شد: X و Y0 «نگه‌داشته»، Z نیست
  await query('DELETE FROM stock_reservations WHERE id=$1', [z.id]);
  await query(`UPDATE stock_reservations SET status='held', closed_at=NULL, close_reason='' WHERE id = ANY($1::text[])`, [[x.id, y0.id]]);
  assert.equal(await reconciled(s), false);
  const movesBefore = await moveCount(s);

  const dry = await lib().rebuild(s.shopId);
  assert.equal(dry.ok, false);
  assert.equal(await moveCount(s), movesBefore, 'نمایش دفتر را نمی‌نویسد');
  const r = await lib().rebuild(s.shopId, { apply: true, by: 'آزمون' });
  assert.equal(r.ok, true);
  assert.deepEqual(await moves(x.id), ['commit', 'hold'], 'بستنِ دوم (release) روی commit ِ دفتر نوشته نشد');
  assert.deepEqual(await moves(y0.id), ['hold', 'release']);
  assert.deepEqual(await moves(z.id), ['hold'], 'نگه‌داشتنِ بازِ دفتر آزاد نشد');
  assert.equal((await one('SELECT status FROM stock_reservations WHERE id=$1', [x.id])).status, 'committed');
  assert.equal((await one('SELECT status FROM stock_reservations WHERE id=$1', [y0.id])).status, 'released');
  assert.deepEqual((await held(s)).map((h0) => h0.id), [z.id], 'همان Z ِ دفتر، با همان شناسه');
  assert.equal(await reconciled(s), true, JSON.stringify((await stock.reconcile(s.shopId)).mismatches));
  const again = await lib().rebuild(s.shopId, { apply: true });
  assert.equal(again.ok, true);
  assert.deepEqual(again.changes, []);
});
