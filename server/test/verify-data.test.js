'use strict';
/**
 * DATA-* (بخشِ ۲۹.۵) — `npm run verify-data`: هر بررسی روی دفترِ سالم صفر است و
 * روی همان دفتر با **یک** خرابیِ عمدی، همان بررسی روشن می‌شود.
 *
 * دفترِ سالم از همان راهِ برنامه می‌آید (Sync v1 push، سفارشِ دستی، ویترین)؛
 * خرابی‌ها با SQLِ مستقیم ساخته می‌شوند — همان چیزی که ابزار باید پیدا کند:
 * دستکاری یا باگی که از کنارِ API گذشته است. هر خرابی در دکانِ خودش است و
 * بررسی با `--shop` فقط همان دکان را می‌بیند.
 */
const test = require('node:test');
const assert = require('node:assert/strict');
const h = require('./helpers');
const { query, one, newId, now } = require('../src/db');
const vd = require('../src/lib/verify-data');
const cli = require('../scripts/verify-data');

let admin = '';
let seq = 0;
const uniq = () => `${Date.now().toString(36)}${++seq}`;

test.before(async () => {
  await h.start();
  //  کاشتنِ دادهٔ مرجع چند ثانیه بی درخواست است؛ اتصالِ keep-alive ِ fetch نباید زیرِ پایش بسته شود
  h.server().keepAliveTimeout = 120_000;
  const pw = require('../src/lib/password');
  await query(
    `INSERT INTO admins (id, username, name, password_hash, role, status, created_at)
     VALUES ($1,'vdadmin','مدیر',$2,'superadmin','active',$3)`,
    [newId('adm'), await pw.hashPassword('Admin!12345'), now()]
  );
  admin = (await h.post('/api/admin/login', { username: 'vdadmin', password: 'Admin!12345' })).body.token;
  assert.ok(admin);
});
test.after(async () => { await h.stop(); });

async function setFlag(scope, key, value) {
  const r = await h.put('/api/admin/pump/live-config', { scope, key, value }, { token: admin });
  assert.equal(r.status, 200, JSON.stringify(r.body));
}

const push = (token, ops, device = 'dev-vd') => h.api('POST', '/api/sync/v1/push', {
  token, headers: { 'X-App': 'shop', 'X-Device': device },
  body: { device_id: device, schema_version: 2, ops },
});
const op = (table, rowId, fields, type = 'insert') => ({
  op_id: `vd-${uniq()}`, ts: Date.now(), table, row_id: rowId, type, ...(type === 'delete' ? {} : { fields }),
});

/**
 * دکانِ سالم: دو کالا (یکی کیلویی)، یک قرض‌دار، یک فاکتورِ نسیه با دو قلم و تخفیف،
 * یک مرجوعی، تراکنشِ نسیه، ورودیِ انبار، حرکتِ کالا؛ و یک سفارشِ آنلاین که
 * پذیرفته شده، و یک کالای ویترین.
 */
async function cleanShop(label) {
  const u = await h.newUser(label, 'shop');
  const made = await h.post('/api/shop', { name: `دکانِ ${label}` }, { token: u.accessToken });
  assert.equal(made.status, 201, JSON.stringify(made.body));
  const o = { token: u.accessToken, shopId: made.body.shop.id };
  //  دفترِ موجودیِ سرور (shop.catalog) هم روشن: فروش و ورودیِ انبار ⇒ stock_moves، سفارش ⇒ رزرو
  await setFlag(o.shopId, 'shop.orders', true);
  await setFlag(o.shopId, 'shop.storefront', true);
  await setFlag(o.shopId, 'shop.catalog', true);
  const p = await push(o.token, [
    op('products', 'p1', { name: 'برنج', unit: 'عدد', salePrice: 120.5, purchasePrice: 100, category: 'خوراکی' }),
    op('products', 'p2', { name: 'شکر', unit: 'کیلوگرم', salePrice: 80, purchasePrice: 60, category: 'خوراکی' }),
    op('debtors', 'd1', { name: 'مشتری', phone: '0700000000' }),
    op('sales', 's1', {
      invoiceNumber: 1000, total: 361, discountType: 'amount', discountValue: 11, discount: 11, finalTotal: 350,
      paymentMethod: 'credit', debtorId: 'd1', paidAmount: 200, remaining: 150, status: 'completed',
      debtGiven: 150, debtSettled: 0, createdAt: 1, date: '2026-10-19',
    }),
    op('saleItems', 'i1', { saleId: 's1', productId: 'p1', quantity: 2, unitPrice: 120.5, purchasePrice: 100, totalPrice: 241, returnedQty: 1 }),
    op('saleItems', 'i2', { saleId: 's1', productId: 'p2', quantity: 1.5, unitPrice: 80, purchasePrice: 60, totalPrice: 120, returnedQty: 0 }),
    op('returns', 'r1', { saleId: 's1', saleItemId: 'i1', productId: 'p1', quantity: 1, amount: 116, reason: '', date: '2026-10-19', createdAt: 2 }),
    op('transactions', 't1', { debtorId: 'd1', type: 'give', amount: 150, date: '2026-10-19', notes: '', createdAt: 1 }),
    op('warehouseEntries', 'w1', { productId: 'p1', cartons: 0, perCarton: 1, units: 10, unit: 'عدد', price: 100, date: '2026-10-01', notes: '' }),
    op('stockMovements', 'm1', { productId: 'p1', type: 'sale', qty: -2, date: '2026-10-19', notes: '', refId: 's1' }),
  ]);
  assert.equal(p.status, 200, JSON.stringify(p.body));
  assert.ok(p.body.results.every((r) => r.status === 'applied'), JSON.stringify(p.body.results));
  const rf = await h.post('/api/shop/catalog/refresh', {}, { token: o.token });
  assert.ok(rf.status < 300, JSON.stringify(rf.body));

  const ord = await h.post('/api/shop/orders', {
    source: 'phone', fulfillment: 'pickup', paymentMethod: 'cod', customer: { name: 'خریدار', phone: '0791234567' },
    lines: [{ productId: 'p1', name: 'برنج', qtyMilli: 1500, unitPriceMinor: 3333 }, { name: 'نوشابه', qtyMilli: 2000, unitPriceMinor: 1250 }],
  }, { token: o.token, headers: { 'Idempotency-Key': `vd-order-${uniq()}` } });
  assert.equal(ord.status, 201, JSON.stringify(ord.body));
  o.orderId = ord.body.order.id;
  const tr = await h.post(`/api/shop/orders/${o.orderId}/transition`, { to: 'accepted', expectedStatus: 'new' }, { token: o.token });
  assert.equal(tr.status, 200, JSON.stringify(tr.body));
  const sf = await h.put('/api/shop/storefront', { slug: `vd-${uniq()}`, enabled: true, title: 'ویترین', contactPhone: '0700000001' }, { token: o.token });
  assert.equal(sf.status, 200, JSON.stringify(sf.body));
  const pub = await h.put('/api/shop/storefront/products/p1', { published: true }, { token: o.token });
  assert.equal(pub.status, 200, JSON.stringify(pub.body));
  return o;
}

const byId = (rep) => Object.fromEntries(rep.checks.map((c) => [c.id, c]));
const setField = (shopId, table, rowId, path, json) => query(
  `UPDATE sync_rows SET data = jsonb_set(data, $4::text[], $5::jsonb)
    WHERE app='shop' AND account_kind='shop' AND account_id=$1 AND table_name=$2 AND row_id=$3`,
  [shopId, table, rowId, path, json]
);

/** بررسیِ `id` روشن است و هیچ بررسیِ دیگری (جز `also`) در همان دکان روشن نیست */
async function expectOnly(shopId, id, also = []) {
  const rep = await vd.verify({ shopId });
  const map = byId(rep);
  assert.ok(map[id], `بررسیِ ${id} اجرا نشد`);
  assert.ok(map[id].count >= 1, `${id} روشن نشد: ${JSON.stringify(rep.checks.filter((c) => c.count))}`);
  const others = rep.checks.filter((c) => c.count > 0 && c.id !== id && !also.includes(c.id));
  assert.deepEqual(others.map((c) => c.id), [], `بررسی‌های دیگر هم روشن شدند: ${JSON.stringify(others)}`);
  return map[id];
}

test('دفترِ سالم: همهٔ بررسی‌ها اجرا می‌شوند و هیچ تخلفی نیست', async () => {
  const o = await cleanShop('سالم');
  const rep = await vd.verify({ shopId: o.shopId });
  const dirty = rep.checks.filter((c) => c.count > 0);
  assert.deepEqual(dirty, [], JSON.stringify(dirty, null, 1));
  assert.equal(rep.ok, true);
  assert.equal(rep.errors, 0);
  assert.equal(rep.warnings, 0);
  //  همهٔ بررسی‌های فهرست واقعاً دویدند (v3 هم، چون جدول‌هایش در migration هست)
  const ran = new Set(rep.checks.map((c) => c.id));
  for (const c of vd.listChecks()) assert.ok(ran.has(c.id), `بررسیِ ${c.id} اجرا نشد`);
  //  دفترِ موجودی واقعاً پر است — بررسی‌های sum.stock_* روی دادهٔ واقعیِ برنامه صفرند، نه روی جدولِ خالی
  const st = (await query(
    `SELECT (SELECT count(*) FROM stock_moves WHERE shop_id=$1)::int AS moves,
            (SELECT count(*) FROM stock_levels WHERE shop_id=$1 AND on_hand_milli <> 0)::int AS levels,
            (SELECT count(*) FROM stock_reservations WHERE shop_id=$1 AND status='held')::int AS held`, [o.shopId]
  )).rows[0];
  assert.ok(st.moves > 0 && st.levels > 0 && st.held > 0, JSON.stringify(st));
});

test('sum.stock_levels: موجودیِ یک جا دستی عوض شده، بی حرکت در دفتر', async () => {
  const o = await cleanShop('موجودی');
  await query(`UPDATE stock_levels SET on_hand_milli = on_hand_milli + 1000 WHERE shop_id=$1 AND product_id='p1' AND on_hand_milli <> 0`, [o.shopId]);
  const c = await expectOnly(o.shopId, 'sum.stock_levels');
  assert.match(c.samples[0].ref, /^p1\//);
});

test('sum.stock_reserved: رزروِ باز بی حرکتِ رزرو', async () => {
  const o = await cleanShop('رزرو');
  await query(`UPDATE stock_reservations SET qty_milli = qty_milli + 1 WHERE shop_id=$1 AND status='held'`, [o.shopId]);
  await expectOnly(o.shopId, 'sum.stock_reserved');
});

test('orphan.order_ledger_doc: سندِ سریِ W برای سفارشِ ناموجود', async () => {
  const o = await cleanShop('سندW');
  await query(
    `INSERT INTO order_ledger_docs (id, shop_id, order_id, kind, doc_no, sale_row_id, on_status, total_minor, chain, chain_seq, hash, created_at)
     VALUES ($1,$2,'ghost-order','sale',1,'x','delivered',100,'c',1,'h',$3)`,
    [newId('old'), o.shopId, now()]
  );
  const c = await expectOnly(o.shopId, 'orphan.order_ledger_doc');
  assert.equal(c.samples[0].value, 'ghost-order');
});

test('دادهٔ مرجعِ سنجش (اعشار، تخفیف، نسیه، مرجوعی، لغو) هیچ تخلفِ کاذبی نمی‌سازد', async () => {
  const path = require('node:path');
  const { pathToFileURL } = require('node:url');
  const { makeReference, syncRowsOf } = await import(pathToFileURL(path.join(__dirname, '..', '..', 'bench', 'datagen.mjs')).href);
  const u = await h.newUser('مرجع', 'shop');
  const shopId = (await h.post('/api/shop', { name: 'دکانِ مرجع' }, { token: u.accessToken })).body.shop.id;
  const ledger = makeReference({ products: 300, sales: 2500, saleItems: 3800, customers: 100, activeDebtors: 40, suppliers: 5, expenses: 20 });
  const rows = [...syncRowsOf(ledger)].map((r) => ({ t: r.table, i: r.id, d: r.data }));
  //  همان کاشتنِ bench/server.mjs: هر ردیف یک opِ درج و ردیفی که به همان op اشاره می‌کند
  for (let i = 0; i < rows.length; i += 2000) {
    await query(
      `WITH src AS (SELECT x->>'t' AS t, x->>'i' AS i, x->'d' AS d, ord FROM jsonb_array_elements($1::jsonb) WITH ORDINALITY AS a(x, ord)),
       ins AS (INSERT INTO oplog (id, app, account_kind, account_id, device_id, op_id, client_ts, table_name, row_id, op_type, fields, received_at)
               SELECT 'op_vd_' || $2 || t || i, 'shop', 'shop', $2, 'seed', 'vd-ref-' || $2 || t || i, 0, t, i, 'insert', d, $3 FROM src ORDER BY ord
               RETURNING table_name, row_id, server_seq, fields)
       INSERT INTO sync_rows (app, account_kind, account_id, table_name, row_id, data, updated_seq, created_at, updated_at)
       SELECT 'shop', 'shop', $2, table_name, row_id, fields, server_seq, $3, $3 FROM ins`,
      [JSON.stringify(rows.slice(i, i + 2000)), shopId, now()]
    );
  }
  const rep = await vd.verify({ shopId });
  const dirty = rep.checks.filter((c) => c.count > 0);
  assert.deepEqual(dirty, [], JSON.stringify(dirty, null, 1));
  assert.ok(ledger.returns.length > 0 && ledger.sales.some((s) => s.status === 'cancelled') && ledger.saleItems.some((x) => x.quantity % 1 !== 0),
    'دادهٔ مرجع باید لبه‌ها را داشته باشد');
});

test('فقط خواندنی: verify هیچ ردیفی را عوض نمی‌کند', async () => {
  const o = await cleanShop('فقط‌خواندنی');
  const snap = async () => (await query(
    `SELECT (SELECT md5(string_agg(row_id || data::text || updated_seq, ',' ORDER BY table_name, row_id)) FROM sync_rows WHERE account_id=$1) AS rows,
            (SELECT count(*) FROM oplog WHERE account_id=$1)::int AS ops,
            (SELECT md5(string_agg(id || status || version, ',' ORDER BY id)) FROM online_orders WHERE shop_id=$1) AS orders`, [o.shopId]
  )).rows[0];
  const before = await snap();
  await vd.verify({ shopId: o.shopId });
  await vd.verify();
  assert.deepEqual(await snap(), before);
});

test('money.minor_units: کسرِ پول (سه رقمِ اعشار) و مبلغِ رشته‌ای پیدا می‌شوند، بی خطای SQL', async () => {
  const o = await cleanShop('پول');
  await setField(o.shopId, 'products', 'p1', '{purchasePrice}', '100.125');
  await setField(o.shopId, 'transactions', 't1', '{amount}', '"۱۵۰"');
  const c = await expectOnly(o.shopId, 'money.minor_units');
  assert.equal(c.count, 2);
  assert.deepEqual(c.samples.map((s) => s.ref).sort(), ['products/p1.purchasePrice', 'transactions/t1.amount']);
});

test('money.minor_units: مبلغِ بیرون از ۲^۵۳−۱ (به واحدِ کوچک) پیدا می‌شود', async () => {
  const o = await cleanShop('پول‌بزرگ');
  await setField(o.shopId, 'products', 'p2', '{purchasePrice}', '90071992547409.92');
  await expectOnly(o.shopId, 'money.minor_units');
});

test('qty.thousandths: مقدار با چهار رقمِ اعشار، و مرجوعیِ صفر', async () => {
  const o = await cleanShop('مقدار');
  await setField(o.shopId, 'stockMovements', 'm1', '{qty}', '-2.0005');
  const c = await expectOnly(o.shopId, 'qty.thousandths');
  assert.equal(c.samples[0].ref, 'stockMovements/m1.qty');

  const o2 = await cleanShop('مقدار۲');
  await setField(o2.shopId, 'returns', 'r1', '{quantity}', '0');
  //  مرجوعیِ صفر جمعِ مرجوعیِ قلم را هم به هم می‌زند — همان یک خرابی، دو ناوردا
  const c2 = await expectOnly(o2.shopId, 'qty.thousandths', ['sum.returned_qty']);
  assert.equal(c2.samples[0].ref, 'returns/r1.quantity');
});

test('Sync v3: همان بررسی‌ها روی sync_v3_rows (پشتِ پرچم) هم می‌دوند', async () => {
  const o = await cleanShop('v3');
  const ins = await query(
    `INSERT INTO sync_v3_ops (app, account_kind, account_id, op_id, device_id, hlc, table_name, row_id, kind, fields, status, received_at)
     VALUES ('shop','shop',$1,$2,'d','0000000000001-0000-d','products','v3p','insert','{}'::jsonb,'applied',$3) RETURNING server_seq`,
    [o.shopId, `vd-v3-${uniq()}`, now()]
  );
  await query(
    `INSERT INTO sync_v3_rows (app, account_kind, account_id, table_name, row_id, data, updated_seq, created_at, updated_at)
     VALUES ('shop','shop',$1,'products','v3p','{"name":"x","salePrice":"ده"}'::jsonb,$2,$3,$3)`,
    [o.shopId, ins.rows[0].server_seq, now()]
  );
  const c = await expectOnly(o.shopId, 'money.minor_units@v3');
  assert.equal(c.samples[0].ref, 'products/v3p.salePrice');
});

test('orphan.ledger_row: تراکنش به قرض‌دارِ ناموجود', async () => {
  const o = await cleanShop('یتیم');
  await setField(o.shopId, 'transactions', 't1', '{debtorId}', '"ghost"');
  const c = await expectOnly(o.shopId, 'orphan.ledger_row');
  assert.deepEqual(c.samples.map((s) => [s.ref, s.value]), [['transactions/t1.debtorId', 'ghost']]);
});

test('orphan.ledger_account: دفترِ همگام‌سازیِ دکانی که دیگر نیست', async () => {
  const ghost = `shp_ghost${uniq()}`;
  const ins = await query(
    `INSERT INTO oplog (id, app, account_kind, account_id, device_id, op_id, table_name, row_id, op_type, fields, received_at)
     VALUES ($1,'shop','shop',$2,'d',$3,'products','x','insert','{}'::jsonb,$4) RETURNING server_seq`,
    [newId('op'), ghost, `vd-ghost-${uniq()}`, now()]
  );
  await query(
    `INSERT INTO sync_rows (app, account_kind, account_id, table_name, row_id, data, updated_seq, created_at, updated_at)
     VALUES ('shop','shop',$1,'products','x','{}'::jsonb,$2,$3,$3)`,
    [ghost, ins.rows[0].server_seq, now()]
  );
  const c = await expectOnly(ghost, 'orphan.ledger_account');
  assert.equal(c.samples[0].ref, ghost);
});

test('append_only.oplog_missing: opِ نویسندهٔ فاکتور از oplog پاک شده', async () => {
  const o = await cleanShop('دفترِ‌op');
  await query(`DELETE FROM oplog WHERE account_id=$1 AND table_name='sales' AND row_id='s1'`, [o.shopId]);
  const c = await expectOnly(o.shopId, 'append_only.oplog_missing');
  assert.equal(c.samples[0].ref, 'sales/s1');
});

test('append_only.posted_deleted: حذفِ تراکنشِ قطعی هشدار است، نه خطا (کدِ خروج ۰)', async () => {
  const o = await cleanShop('حذف');
  const p = await push(o.token, [op('transactions', 't1', undefined, 'delete')]);
  assert.equal(p.status, 200, JSON.stringify(p.body));
  const c = await expectOnly(o.shopId, 'append_only.posted_deleted');
  assert.equal(c.severity, 'warn');
  const rep = await vd.verify({ shopId: o.shopId });
  assert.equal(rep.ok, true);
  assert.equal(rep.warnings, 1);
  const lines = [];
  assert.equal(await cli.main(['--shop', o.shopId], (s) => lines.push(s)), 0);
});

test('sum.sale_total: جمعِ فاکتور با قلم‌ها نمی‌خواند', async () => {
  const o = await cleanShop('جمع');
  //  جمع، نهایی و مانده با هم ⇒ فقط همین یک ناوردا شکسته است
  await setField(o.shopId, 'sales', 's1', '{total}', '362');
  await setField(o.shopId, 'sales', 's1', '{finalTotal}', '351');
  await setField(o.shopId, 'sales', 's1', '{remaining}', '151');
  await setField(o.shopId, 'sales', 's1', '{debtGiven}', '151');
  const c = await expectOnly(o.shopId, 'sum.sale_total');
  assert.match(c.samples[0].value, /^362 ≠ 361/);
});

test('sum.sale_final: پرداخت + مانده ≠ نهایی', async () => {
  const o = await cleanShop('نهایی');
  await setField(o.shopId, 'sales', 's1', '{remaining}', '140');
  await expectOnly(o.shopId, 'sum.sale_final');
});

test('sum.sale_final: تسویه بیشتر از نسیه', async () => {
  const o = await cleanShop('تسویه');
  await setField(o.shopId, 'sales', 's1', '{debtSettled}', '151');
  await expectOnly(o.shopId, 'sum.sale_final');
});

test('sum.sale_item_line: قیمتِ قلم با مبلغش نمی‌خواند (هشدار)', async () => {
  const o = await cleanShop('قلم');
  await setField(o.shopId, 'saleItems', 'i1', '{unitPrice}', '125');
  const c = await expectOnly(o.shopId, 'sum.sale_item_line');
  assert.equal(c.severity, 'warn');
});

test('sum.returned_qty: مرجوعیِ قلم بیش از ردیف‌های مرجوعی', async () => {
  const o = await cleanShop('مرجوعی');
  await setField(o.shopId, 'saleItems', 'i1', '{returnedQty}', '2');
  await expectOnly(o.shopId, 'sum.returned_qty');
});

test('sum.order_lines: مبلغِ ردیفِ سفارش دست‌کاری شده', async () => {
  const o = await cleanShop('سفارش‌جمع');
  await query(`UPDATE online_order_lines SET line_total_minor = line_total_minor + 100 WHERE order_id=$1 AND line_no=1`, [o.orderId]);
  await expectOnly(o.shopId, 'sum.order_lines');
});

test('sum.order_refunds و append_only.order_refunds: بازپرداختِ بیش از مبلغ، و شمارهٔ جاافتاده', async () => {
  const o = await cleanShop('بازپرداخت');
  const total = Number((await one('SELECT total_minor FROM online_orders WHERE id=$1', [o.orderId])).total_minor);
  const refund = (s, amount) => query(
    `INSERT INTO online_order_refunds (id, order_id, shop_id, seq, client_key, request_hash, amount_minor, reason, created_at)
     VALUES ($1,$2,$3,$4,$5,'h',$6,'آزمون',$7)`,
    [newId('rf'), o.orderId, o.shopId, s, `vd-${uniq()}`, amount, now()]
  );
  await refund(1, total + 100);
  await expectOnly(o.shopId, 'sum.order_refunds');

  const o2 = await cleanShop('بازپرداخت۲');
  await query(
    `INSERT INTO online_order_refunds (id, order_id, shop_id, seq, client_key, request_hash, amount_minor, reason, created_at)
     VALUES ($1,$2,$3,2,$4,'h',100,'آزمون',$5)`,
    [newId('rf'), o2.orderId, o2.shopId, `vd-${uniq()}`, now()]
  );
  await expectOnly(o2.shopId, 'append_only.order_refunds');
});

test('orphan.order_tenant: رویدادِ سفارش با shop_idِ دکانِ دیگر', async () => {
  const o = await cleanShop('مرز');
  await query(`UPDATE online_order_events SET shop_id='shp_other' WHERE order_id=$1 AND seq=2`, [o.orderId]);
  const c = await expectOnly(o.shopId, 'orphan.order_tenant');
  assert.match(c.samples[0].ref, /^online_order_events\//);
});

test('orphan.storefront_product: کالای ویترین بی ردیفِ دفتر', async () => {
  const o = await cleanShop('ویترین');
  await query(`INSERT INTO storefront_products (shop_id, product_id, published, updated_at) VALUES ($1,'ghost',true,$2)`, [o.shopId, now()]);
  await expectOnly(o.shopId, 'orphan.storefront_product');
});

test('append_only.order_events: رویدادِ وضعیت پاک شده (شماره‌ها پیوسته نیست و آخرین وضعیت نمی‌خواند)', async () => {
  const o = await cleanShop('رویداد');
  await query(`DELETE FROM online_order_events WHERE order_id=$1 AND seq=2`, [o.orderId]);
  await expectOnly(o.shopId, 'append_only.order_events');
});

test('append_only.order_events: وضعیتِ سفارش بی رویداد عوض شده', async () => {
  const o = await cleanShop('وضعیت');
  await query(`UPDATE online_orders SET status='picking' WHERE id=$1`, [o.orderId]);
  await expectOnly(o.shopId, 'append_only.order_events');
});

test('append_only.hash_chain: حلقه‌ای که متنش با هشش نمی‌خواند', async () => {
  const o = await cleanShop('زنجیره');
  await query(
    `INSERT INTO hash_chain (chain, seq, prev_hash, hash, body, created_at) VALUES ($1, 1, $2, $3, 'x', $4)`,
    [`audit/${o.shopId}/user/vd`, '0'.repeat(64), 'a'.repeat(64), now()]
  );
  const c = await expectOnly(o.shopId, 'append_only.hash_chain');
  assert.equal(c.count, 1);
});

test('خطِ فرمان: --list، --json، --check و کدِ خروجِ ۲ برای خطا', async () => {
  const o = await cleanShop('خط‌فرمان');
  const listed = [];
  assert.equal(await cli.main(['--list'], (s) => listed.push(s)), 0);
  assert.equal(listed.length, vd.listChecks().length);

  const clean = [];
  assert.equal(await cli.main(['--shop', o.shopId, '--json'], (s) => clean.push(s)), 0);
  assert.equal(JSON.parse(clean.join('\n')).ok, true);

  await setField(o.shopId, 'sales', 's1', '{remaining}', '1');
  const out = [];
  assert.equal(await cli.main(['--shop', o.shopId], (s) => out.push(s)), 2);
  assert.ok(out.some((l) => l.startsWith('✖ sum.sale_final')), out.join('\n'));
  assert.ok(!out.join('\n').includes('0700000000'), 'نمونه‌ها نباید تلفن یا نامِ مشتری داشته باشند');

  const only = [];
  assert.equal(await cli.main(['--shop', o.shopId, '--check', 'orphan', '--json'], (s) => only.push(s)), 0);
  const rep = JSON.parse(only.join('\n'));
  assert.ok(rep.checks.length > 0 && rep.checks.every((c) => c.id.startsWith('orphan.')), JSON.stringify(rep.checks.map((c) => c.id)));
  assert.throws(() => cli.parseArgs(['--nope']), /ناشناخته/);
});
