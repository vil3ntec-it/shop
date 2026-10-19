'use strict';
/**
 * سفارشِ آنلاینِ تحویل‌شده ⇒ فاکتورِ فروشِ سریِ W در دفترِ خودِ دکان —
 * ORD-20، v3 §۳۸.۲ و §۲۲.۲ (مرحلهٔ A، بندِ ۶).
 *
 *   پرچم ──── `shop.orders.ledger` در «تنظیماتِ زنده» (scope = دکان یا `all`).
 *             ⛔ پیش‌فرض خاموش: خاموش ⇒ هیچ ردیفی، همان رفتارِ پیشین.
 *   فروش ──── گذارِ سفارش به `picked_up` یا `delivered` ⇒ در **همان تراکنشِ
 *             گذار** یک فاکتورِ W با شمارهٔ پشتِ سرِ همِ دکان (W-1، W-2، …)
 *             ساخته می‌شود: ردیف‌های `sales`، `saleItems`، `stockMovements`
 *             و `auditLog` با **همان شکلی که دستگاه برای فروش می‌نویسد**
 *             (`SalesEngine.record`)، در دفترِ Sync v1 (`oplog` + `sync_rows`).
 *             دستگاه‌ها آن را با همان pull/snapshot ِ همیشگی می‌گیرند؛ موجودی و
 *             گزارشِ دستگاه فروشِ اینترنتی را می‌شمارند و فروشنده دوباره
 *             فاکتور نمی‌زند.
 *   یکتایی ── هر سفارش یک سندِ فروش و یک سندِ برگشت (UNIQUE(order_id, kind))؛
 *             شناسهٔ ردیف‌ها و opها از سند می‌آید ⇒ تکرار هیچ چیزِ تازه‌ای
 *             نمی‌نویسد.
 *   برگشت ─── `refunded_full` پس از تحویل ⇒ سندِ برگشت (ردیف‌های `returns`،
 *             `returnedQty: {$inc}` و `stockMovements` از نوعِ customer_return)
 *             — همان کاری که مرجوعیِ دستگاه می‌کند. فاکتور هرگز لغو، ویرایش یا
 *             پاک نمی‌شود. فقط ماندهٔ برنگشته برمی‌گردد (مرجوعیِ دستگاه پیش از
 *             آن دو بار شمرده نمی‌شود). برگشت حتی با پرچمِ خاموش‌شده انجام
 *             می‌شود تا فاکتورِ W بی‌جبران نماند.
 *   زنجیره ── هر سند یک حلقه در زنجیرهٔ هشِ سری (`orders/<دکان>/W`، جدولِ
 *             `hash_chain`، migration 070) — `scripts/verify-chain.js` آن را
 *             با بقیهٔ زنجیره‌ها می‌سنجد.
 *
 * ⛔ `lib/sync-v1.js` دست نمی‌خورد: از همان `validateOp` / `applyOp` /
 * `hashOf` ِ صادرشده‌اش استفاده می‌شود، زیرِ همان قفلِ مشورتیِ push
 * (`sync-v1:shop:shop:<دکان>`)، با دستگاهِ `server`. ردیفی در `sync_devices`
 * ساخته نمی‌شود (سرور دستگاه نیست و نباید «تازگیِ» دکان را جعل کند).
 *
 * ⛔ کاتالوگ و موجودیِ سرور (#263، `shop.catalog`): اگر رزروِ این سفارش هنگامِ
 * تحویل «کسرِ قطعی» شده باشد، سهمِ قلم‌های W در `stock_sources` از پیش ثبت
 * می‌شود تا خواندنِ دفترِ دستگاه (`catalog.ingestTx`) آن فروش را دوباره کم
 * نکند. نبودِ جدول‌ها (پیش از ادغامِ #263) ⇒ هیچ.
 *
 * ⛔ هنوز نیست (و عمداً): بازپرداختِ جزئی (refunded_partial) سندِ کالایی
 * نمی‌سازد، چون سفارش نمی‌گوید کدام قلم برگشته (ORD-15، PCK-07)؛ لحظهٔ
 * «بسته‌بندی» (ORD-20: قابل تنظیم) تا هم‌قدم شدنِ لحظهٔ کسرِ قطعیِ #263؛
 * دفترِ Sync v3.
 */
const { createHash } = require('crypto');
const { newId } = require('../db');
const config = require('../config');
const syncV1 = require('./sync-v1');
const migrations = require('./sync-v1-migrations');
const hc = require('./hash-chain');
const { ulid } = require('./ulid');

const FLAG_KEY = 'shop.orders.ledger';
const SERIES = 'W';
/** لحظهٔ «کسرِ قطعی» — همان COMMIT_ON ِ لایهٔ موجودیِ سرور (#263) */
const POST_ON = Object.freeze(['picked_up', 'delivered']);
/** برگشتِ کامل پس از تحویل */
const REVERSE_ON = Object.freeze(['refunded_full']);
const APP = 'shop';
const ACC = "app='shop' AND account_kind='shop' AND account_id=$1";

function decode(v) {
  if (typeof v !== 'string') return v;
  try { return JSON.parse(v); } catch { return null; }
}
const num = (v) => (v === null || v === undefined || v === '' ? 0 : Number(v));
/** واحدِ کوچک (یک‌صدمِ افغانی) ⇒ عددِ افغانی، همان شکلِ دفترِ دستگاه */
const afn = (minor) => Number(minor) / 100;
/** مقدار با سه رقمِ اعشار — همان گردکردنِ `SalesEngine.toStockQty` */
const q3 = (x) => Math.round(x * 1000) / 1000;
const part = (v) => String(v || '').replace(/[^A-Za-z0-9_.:-]/g, '_').slice(0, 60) || '-';

/**
 * روشِ پرداختِ فاکتورِ W — **هرگز «نقد در دخل» نیست**:
 *   online ⇒ `paymentMethod: 'online'`؛ ردیفِ PAY از نوعِ `other`. «تأییدشده» با سطحِ
 *            `provider` فقط وقتی سفارش `paid` است (PAY-08)؛ وگرنه `pending`.
 *   cod    ⇒ `paymentMethod: 'cod'`؛ نوعِ `cash` ولی دستِ پیک/تحویل‌دهنده
 *            (`custody: 'courier'`) و تا تسویهٔ پیک (`courier_settlements`، DLV) پولِ دخل
 *            نیست ⇒ `pending`. سند تغییرناپذیر است؛ وضعیتِ تسویه را `cod_status` ِ سفارش
 *            و سندِ تسویه نگه می‌دارند، نه این ردیف.
 * مبلغِ ردیف به واحدِ کوچک (پول) — همان قراردادِ هستهٔ PAY (`PaymentRow`).
 */
function paymentOf(order, totalMinor) {
  if (order.payment_method === 'online') {
    const paid = order.payment_status === 'paid';
    return {
      method: 'online',
      rows: [{ method: 'online', kind: 'other', amount: totalMinor, ref: null,
        status: paid ? 'confirmed' : 'pending', verification: paid ? 'provider' : null }],
    };
  }
  return {
    method: 'cod',
    rows: [{ method: 'cod', kind: 'cash', amount: totalMinor, ref: null, status: 'pending', verification: null, custody: 'courier' }],
  };
}

/** پرچمِ دکان درونِ تراکنشِ فراخواننده: مقدارِ خودِ دکان بر `all` جلو می‌افتد؛ فقط `true` روشن است. */
async function flagOn(c, shopId) {
  const rows = (await c.query(
    'SELECT scope, value FROM live_config WHERE key=$1 AND scope IN ($2,$3)', [FLAG_KEY, 'all', String(shopId)]
  )).rows;
  let v;
  for (const r of rows) if (r.scope === 'all') v = decode(r.value);
  for (const r of rows) if (r.scope !== 'all') v = decode(r.value);
  return v === true;
}

/** نامِ زنجیرهٔ هشِ سریِ W ِ یک دکان */
function chainOf(shopId) {
  return `orders/${part(shopId)}/${SERIES}`;
}

/** شناسه‌های ثابتِ ردیف‌های دفترِ دستگاه برای یک سفارش */
const ids = {
  sale: (orderId) => `w-${orderId}`,
  item: (orderId, lineNo) => `w-${orderId}-${lineNo}`,
  move: (orderId, lineNo) => `w-${orderId}-${lineNo}-m`,
  audit: (orderId) => `w-${orderId}-a`,
  ret: (itemId) => `${itemId}-r`,
  retMove: (itemId) => `${itemId}-rm`,
  retAudit: (orderId) => `w-${orderId}-ra`,
};

/** تاریخِ محلیِ دکان به شکلِ `YYYY-MM-DD` ِ دفترِ دستگاه */
async function localDate(c, shopId, at) {
  let tz = config.defaults.timezone || 'Asia/Kabul';
  try {
    const r = (await c.query("SELECT to_regclass('storefronts') AS t")).rows[0];
    if (r && r.t) {
      const sf = (await c.query('SELECT timezone FROM storefronts WHERE shop_id=$1', [shopId])).rows[0];
      if (sf && sf.timezone) tz = sf.timezone;
    }
  } catch { /* بی ویترین ⇒ منطقهٔ پیش‌فرض */ }
  try {
    return new Intl.DateTimeFormat('en-CA', { timeZone: tz, year: 'numeric', month: '2-digit', day: '2-digit' })
      .format(new Date(at));
  } catch {
    return new Date(at).toISOString().slice(0, 10);
  }
}

async function lock(c, shopId) {
  await c.query('SELECT pg_advisory_xact_lock(hashtext($1))', [`order-ledger:${shopId}`]);
}

async function nextNo(c, shopId, kind) {
  const r = (await c.query(
    'SELECT COALESCE(MAX(doc_no), 0) + 1 AS n FROM order_ledger_docs WHERE shop_id=$1 AND kind=$2', [shopId, kind]
  )).rows[0];
  return Number(r.n);
}

/** کالاهای دفترِ دستگاه (فقط خواندن) */
async function productsOf(c, shopId, productIds) {
  const want = [...new Set(productIds.filter(Boolean))];
  const out = new Map();
  if (!want.length) return out;
  for (const r of (await c.query(
    `SELECT row_id, data FROM sync_rows WHERE ${ACC} AND table_name='products' AND row_id = ANY($2::text[])
        AND deleted_at IS NULL`, [shopId, want]
  )).rows) out.set(r.row_id, decode(r.data) || {});
  return out;
}

/**
 * opها ⇒ `oplog` + `sync_rows`، درونِ تراکنشِ فراخواننده — همان دو INSERT ِ
 * `sync-v1.push`، با `validateOp` / `applyOp` ِ خودش و زیرِ همان قفل.
 * op با شناسهٔ تکراری (تکرارِ همین سند) بی‌صدا رد می‌شود.
 * ⇒ { written, first, last }
 */
async function appendOps(c, shopId, ops, at) {
  await c.query('SELECT pg_advisory_xact_lock(hashtext($1))', [`sync-v1:${APP}:shop:${shopId}`]);
  const schema = migrations.currentVersion(APP);
  let written = 0;
  let first = null;
  let last = null;
  for (const raw of ops) {
    const val = syncV1.validateOp(APP, raw);
    if (!val.ok) throw new Error(`order-ledger: op ${val.op_id} رد شد (${val.reason})`);
    const op = val.op;
    if ((await c.query('SELECT 1 FROM oplog WHERE op_id=$1', [op.op_id])).rows.length) continue;
    const row = (await c.query(
      `SELECT * FROM sync_rows WHERE ${ACC} AND table_name=$2 AND row_id=$3 FOR UPDATE`, [shopId, op.table, op.row_id]
    )).rows[0] || null;
    if (row) {
      row.data = decode(row.data) || {};
      row.field_seq = decode(row.field_seq) || {};
      row.field_src = decode(row.field_src) || {};
    }
    const seq = Number((await c.query(
      `INSERT INTO oplog (id, app, account_kind, account_id, device_id, op_id, client_ts, table_name, row_id,
                          op_type, fields, hash, schema_version, received_at)
       VALUES ($1,$2,'shop',$3,$4,$5,$6,$7,$8,$9,$10::jsonb,$11,$12,$13) RETURNING server_seq`,
      [newId('op'), APP, shopId, syncV1.SERVER_DEVICE, op.op_id, at, op.table, op.row_id, op.type,
        JSON.stringify(op.fields || {}), op.hash || '', schema, at]
    )).rows[0].server_seq);
    //  سرور همه چیز را «دیده» است ⇒ cursorِ بی‌نهایت، هیچ تعارضی ساخته نمی‌شود
    const out = syncV1.applyOp(row, op, seq, syncV1.SERVER_DEVICE, at, Number.MAX_SAFE_INTEGER);
    await c.query(
      `INSERT INTO sync_rows (app, account_kind, account_id, table_name, row_id, data, field_seq, field_src,
                              deleted_at, updated_seq, created_at, updated_at)
       VALUES ($1,'shop',$2,$3,$4,$5::jsonb,$6::jsonb,$7::jsonb,$8,$9,$10,$10)
       ON CONFLICT (app, account_kind, account_id, table_name, row_id) DO UPDATE SET
         data = excluded.data, field_seq = excluded.field_seq, field_src = excluded.field_src,
         deleted_at = excluded.deleted_at, updated_seq = excluded.updated_seq, updated_at = excluded.updated_at`,
      [APP, shopId, op.table, op.row_id, JSON.stringify(out.data), JSON.stringify(out.field_seq),
        JSON.stringify(out.field_src), out.deleted_at, seq, at]
    );
    written += 1;
    if (first === null) first = seq;
    last = seq;
  }
  return { written, first, last };
}

/** opِ آماده با اثرِ انگشت؛ `docId` + شماره ⇒ op_idِ ثابت (تکرار ⇒ «تکراری») */
function opsOf(docId, list) {
  return list.map(([table, rowId, type, fields], i) => {
    const op = { op_id: `w_${docId}_${i + 1}`, ts: 0, table, row_id: rowId, type, fields };
    op.hash = syncV1.hashOf(op);
    return op;
  });
}

/** حلقهٔ زنجیرهٔ هشِ سری، درونِ همان تراکنش (قفلِ دکان نوبت را نگه می‌دارد) */
async function chainLink(c, shopId, doc, at) {
  const chain = chainOf(shopId);
  const head = (await c.query(
    'SELECT seq, hash FROM hash_chain WHERE chain=$1 ORDER BY seq DESC LIMIT 1', [chain]
  )).rows[0];
  const seq = head ? Number(head.seq) + 1 : 1;
  const prevHash = head ? head.hash : hc.GENESIS;
  const body = hc.canonicalJson({ ...doc, chain, seq });
  const hash = hc.sha256Hex(prevHash + body);
  await c.query(
    'INSERT INTO hash_chain (chain, seq, prev_hash, hash, body, created_at) VALUES ($1,$2,$3,$4,$5,$6)',
    [chain, seq, prevHash, hash, body, at]
  );
  return { chain, seq, hash };
}

const linesHash = (lines) => createHash('sha256').update(hc.canonicalJson(lines)).digest('hex');

async function insertDoc(c, d) {
  await c.query(
    `INSERT INTO order_ledger_docs (id, shop_id, order_id, kind, series, doc_no, sale_row_id, on_status, total_minor,
       lines, ops, first_seq, last_seq, chain, chain_seq, hash, actor_type, actor_id, created_at)
     VALUES ($1,$2,$3,$4,$5,$6,$7,$8,$9,$10::jsonb,$11,$12,$13,$14,$15,$16,$17,$18,$19)`,
    [d.id, d.shopId, d.orderId, d.kind, SERIES, d.docNo, d.saleRowId, d.onStatus, d.totalMinor,
      JSON.stringify(d.lines), d.ops, d.first, d.last, d.chain, d.chainSeq, d.hash, d.actorType, d.actorId, d.at]
  );
}

function docView(r) {
  if (!r) return null;
  return {
    id: r.id, orderId: r.order_id, kind: r.kind, series: r.series, docNo: Number(r.doc_no),
    label: `${r.series}-${Number(r.doc_no)}`, saleRowId: r.sale_row_id, onStatus: r.on_status,
    totalMinor: Number(r.total_minor), lines: decode(r.lines) || [], ops: Number(r.ops),
    firstSeq: r.first_seq === null ? null : Number(r.first_seq), lastSeq: r.last_seq === null ? null : Number(r.last_seq),
    chain: r.chain, chainSeq: Number(r.chain_seq), hash: r.hash, createdAt: Number(r.created_at),
  };
}

async function docOf(c, orderId, kind) {
  return (await c.query('SELECT * FROM order_ledger_docs WHERE order_id=$1 AND kind=$2', [orderId, kind])).rows[0] || null;
}

/**
 * لایهٔ موجودیِ سرور (#263): رزروِ «کسرِ قطعی‌شده» ِ همین سفارش ⇒ سهمِ قلمِ W
 * از پیش در `stock_sources` (کلیدِ `si:<قلم>`) تا `catalog.ingestTx` همان
 * فروش را دوباره کم نکند. هر بخشی که رزرو نداشت را خودِ ingest کم می‌کند ⇒
 * جمعِ کسرِ سرور همیشه = مقدارِ قلم. نبودِ جدول‌ها ⇒ ۰.
 */
async function catalogPrePost(c, shopId, orderId, items, at) {
  const t = (await c.query(
    "SELECT to_regclass('stock_sources') AS s, to_regclass('stock_reservations') AS r"
  )).rows[0];
  if (!t || !t.s || !t.r) return 0;
  const committed = new Map();
  for (const r of (await c.query(
    `SELECT line_no, product_id, COALESCE(SUM(qty_milli), 0) AS q FROM stock_reservations
      WHERE shop_id=$1 AND order_id=$2 AND status='committed' GROUP BY line_no, product_id`, [shopId, orderId]
  )).rows) committed.set(`${Number(r.line_no)}:${r.product_id}`, num(r.q));
  let n = 0;
  for (const it of items) {
    if (!it.productId) continue;
    const have = committed.get(`${it.lineNo}:${it.productId}`) || 0;
    const posted = Math.min(have, Math.round(it.quantity * 1000));
    if (posted <= 0) continue;
    await c.query(
      `INSERT INTO stock_sources (shop_id, source_key, product_id, posted_milli, rev, updated_at)
       VALUES ($1,$2,$3,$4,0,$5) ON CONFLICT DO NOTHING`,
      [shopId, `si:${it.id}`, it.productId, -posted, at]
    );
    n += 1;
  }
  return n;
}

/**
 * سندِ فروشِ W ِ یک سفارشِ تحویل‌شده — درونِ تراکنشِ گذار.
 * ⇒ null (پرچم خاموش / آزمایشی) | { doc, replayed, head? }
 */
async function postSale(c, shopId, orderId, actor, at, onStatus) {
  const order = (await c.query('SELECT * FROM online_orders WHERE shop_id=$1 AND id=$2', [shopId, orderId])).rows[0];
  if (!order || order.is_test) return null;
  if (!(await flagOn(c, shopId))) return null;
  await lock(c, shopId);
  const have = await docOf(c, order.id, 'sale');
  if (have) return { doc: docView(have), replayed: true };

  const lines = (await c.query(
    'SELECT * FROM online_order_lines WHERE order_id=$1 AND shop_id=$2 ORDER BY line_no', [order.id, shopId]
  )).rows;
  const products = await productsOf(c, shopId, lines.map((l) => l.product_id));
  const docNo = await nextNo(c, shopId, 'sale');
  const docId = ulid(at);
  const date = await localDate(c, shopId, at);
  const saleId = ids.sale(order.id);
  const label = `${SERIES}-${docNo}`;

  //  قلم‌ها به شکلِ `SaleItem` ِ دستگاه: مقدار به واحدِ موجودی، مبلغ همان ِ سفارش
  const items = [];
  for (const l of lines) {
    const p = l.product_id ? products.get(l.product_id) : null;
    let qty = num(l.qty_milli) / 1000;
    const factor = p ? num(p.unitFactor) : 0;
    if (p && p.saleUnit && l.unit && l.unit === p.saleUnit && l.unit !== p.unit && factor > 0) qty = q3(qty * factor);
    const totalPrice = afn(l.line_total_minor);
    items.push({
      id: ids.item(order.id, l.line_no), lineNo: Number(l.line_no),
      //  کالایی که در دفترِ دستگاه نیست ⇒ قلمِ آزاد (بی اثر بر موجودی)
      productId: p ? l.product_id : '',
      name: l.name,
      quantity: qty,
      unitPrice: qty > 0 ? Math.round((totalPrice / qty) * 100) / 100 : afn(l.unit_price_minor),
      purchasePrice: p ? num(p.purchasePrice) : 0,
      totalPrice,
      unitMinor: Number(l.unit_price_minor),
      totalMinor: Number(l.line_total_minor),
    });
  }
  const feeMinor = Number(order.delivery_fee_minor);
  if (feeMinor > 0) {
    items.push({
      id: `w-${order.id}-fee`, lineNo: 0, productId: '', name: 'هزینهٔ ارسال', quantity: 1,
      unitPrice: afn(feeMinor), purchasePrice: 0, totalPrice: afn(feeMinor), unitMinor: feeMinor, totalMinor: feeMinor,
    });
  }

  const totalMinor = Number(order.total_minor);
  const discountMinor = Number(order.discount_minor);
  const pay = paymentOf(order, totalMinor);
  const sale = {
    invoiceNumber: docNo,
    total: afn(totalMinor + discountMinor),
    discountType: 'amount',
    discountValue: afn(discountMinor),
    discount: afn(discountMinor),
    finalTotal: afn(totalMinor),
    //  روشِ واقعی (online | cod)، نه `cash`. برنامهٔ قدیمی هر چه «credit» نباشد را «نقد»
    //  می‌نویسد و صندوقِ قدیمی آن را در دخل می‌شمارد ⇒ پرچم را فقط با نسخهٔ تازه روشن کنید.
    paymentMethod: pay.method,
    debtorId: null,
    //  معنای قدیمی می‌ماند: مشتری همه را داده (آنلاین یا به پیک)، بدهی‌ای نیست. «کجاست» را
    //  `payments` و `channel` می‌گویند؛ صندوق (CashShift) فاکتورِ اینترنتی را در دخل نمی‌شمارد.
    paidAmount: afn(totalMinor),
    remaining: 0,
    status: 'completed',
    debtGiven: 0,
    debtSettled: 0,
    createdAt: at,
    date,
    syncStatus: 'synced',
    cancelReason: '',
    seller: '',
    series: SERIES,
    //  افزوده‌های سرور — برنامهٔ قدیمی نادیده می‌گیرد؛ گزارش «کانال» را از `channel` می‌خواند
    channel: 'online',
    orderId: order.id,
    orderNo: Number(order.order_no),
    payments: pay.rows,
  };

  const list = [['sales', saleId, 'insert', sale]];
  for (const it of items) {
    list.push(['saleItems', it.id, 'insert', {
      saleId, productId: it.productId, name: it.name, quantity: it.quantity, unitPrice: it.unitPrice,
      purchasePrice: it.purchasePrice, totalPrice: it.totalPrice, returnedQty: 0,
    }]);
  }
  for (const it of items) {
    if (!it.productId) continue;
    list.push(['stockMovements', ids.move(order.id, it.lineNo), 'insert', {
      productId: it.productId, type: 'sale', qty: -it.quantity, date,
      notes: `فاکتور #${label} — سفارشِ اینترنتی #${Number(order.order_no)}`, refId: saleId, createdAt: at,
    }]);
  }
  list.push(['auditLog', ids.audit(order.id), 'insert', {
    type: 'sale', date, refId: saleId,
    notes: `ثبت فروش فاکتور #${label} (سفارشِ اینترنتی #${Number(order.order_no)}) به مبلغ ${afn(totalMinor)} افغانی`,
    createdAt: at, amount: 0,
  }]);

  const written = await appendOps(c, shopId, opsOf(docId, list), at);
  await catalogPrePost(c, shopId, order.id, items, at);

  const snapshot = items.map((it) => ({
    lineNo: it.lineNo, itemId: it.id, productId: it.productId, name: it.name,
    qtyMilli: Math.round(it.quantity * 1000), unitPriceMinor: it.unitMinor, totalMinor: it.totalMinor,
  }));
  const link = await chainLink(c, shopId, {
    kind: 'order_doc', series: SERIES, docKind: 'sale', docNo, shopId: String(shopId), orderId: order.id,
    saleRowId: saleId, totalMinor, linesHash: linesHash(snapshot), at,
  }, at);
  await insertDoc(c, {
    id: docId, shopId, orderId: order.id, kind: 'sale', docNo, saleRowId: saleId, onStatus, totalMinor,
    lines: snapshot, ops: written.written, first: written.first, last: written.last,
    chain: link.chain, chainSeq: link.seq, hash: link.hash, actorType: actor.type || '', actorId: actor.id || '', at,
  });
  return { doc: docView(await docOf(c, order.id, 'sale')), replayed: false, head: written.last };
}

/**
 * سندِ برگشتِ کامل پس از تحویل — فقط اگر فاکتورِ W هست (پرچم هر چه باشد).
 * فقط ماندهٔ برنگشتهٔ هر قلم برمی‌گردد. فاکتورِ لغوشده ⇒ چیزی برای جبران نیست.
 * ⇒ null | { doc, replayed, head? } | { skipped }
 */
async function postReturn(c, shopId, orderId, actor, at, onStatus) {
  const sale = await docOf(c, orderId, 'sale');
  if (!sale || sale.shop_id !== shopId) return null;
  await lock(c, shopId);
  const have = await docOf(c, orderId, 'return');
  if (have) return { doc: docView(have), replayed: true };

  const saleId = sale.sale_row_id;
  const row = (await c.query(
    `SELECT data, deleted_at FROM sync_rows WHERE ${ACC} AND table_name='sales' AND row_id=$2`, [shopId, saleId]
  )).rows[0];
  const sd = row ? decode(row.data) || {} : null;
  if (!sd || row.deleted_at || sd.status === 'cancelled') return { skipped: 'sale_cancelled' };

  //  قلم‌های همین فاکتور از کارنامهٔ سند (کلیدِ یکتا، بی پیمایشِ همهٔ قلم‌های دکان)
  const itemIds = (decode(sale.lines) || []).map((l) => l.itemId).filter(Boolean);
  const itemRows = (await c.query(
    `SELECT row_id, data, deleted_at FROM sync_rows WHERE ${ACC} AND table_name='saleItems' AND row_id = ANY($2::text[])
      ORDER BY row_id`, [shopId, itemIds]
  )).rows.filter((r) => !r.deleted_at);
  const prior = (await c.query(
    `SELECT data FROM sync_rows WHERE ${ACC} AND table_name='returns' AND data->>'saleId' = $2 AND deleted_at IS NULL`,
    [shopId, saleId]
  )).rows.reduce((s, r) => s + Math.round(num((decode(r.data) || {}).amount) * 100), 0);

  const total = num(sd.total);
  const discount = num(sd.discount);
  //  سهمِ تخفیف — همان `paidRatio` ِ `SalesEngine.recordReturn`
  const ratio = total > 0 && discount > 0 ? Math.min(1, Math.max(0, (total - discount) / total)) : 1;
  const back = [];
  for (const r of itemRows) {
    const d = decode(r.data) || {};
    const quantity = num(d.quantity);
    const left = q3(quantity - num(d.returnedQty));
    if (!(left > 0)) continue;
    const amountMinor = quantity > 0 ? Math.round((num(d.totalPrice) * 100 * left / quantity) * ratio) : 0;
    back.push({ itemId: r.row_id, productId: String(d.productId || ''), qty: left, amountMinor });
  }
  if (!back.length) return { skipped: 'nothing_left' };
  //  پس از این سند هیچ قلمی نمی‌ماند ⇒ جمعِ همهٔ برگشت‌ها دقیقاً = مبلغِ پرداخت‌شده
  //  (گردکردنِ قلم‌ها و مرجوعی‌های پیشینِ دستگاه روی قلمِ آخر صاف می‌شود)
  const finalMinor = Math.round(num(sd.finalTotal) * 100);
  const sum = back.reduce((s, b) => s + b.amountMinor, 0);
  const target = Math.max(0, finalMinor - prior);
  back[back.length - 1].amountMinor = Math.max(0, back[back.length - 1].amountMinor + (target - sum));

  const docNo = await nextNo(c, shopId, 'return');
  const docId = ulid(at);
  const date = await localDate(c, shopId, at);
  const label = `${SERIES}-${Number(sd.invoiceNumber || sale.doc_no)}`;
  const reason = 'بازپرداختِ کاملِ سفارشِ اینترنتی';
  const list = [];
  for (const b of back) {
    list.push(['returns', ids.ret(b.itemId), 'insert', {
      saleId, saleItemId: b.itemId, productId: b.productId, quantity: b.qty, amount: afn(b.amountMinor),
      //  پول از راهِ سفارش برمی‌گردد، نه از دخل ⇒ صندوق (CashShift) این مرجوعی را کم نمی‌کند
      reason, date, createdAt: at, channel: 'online',
    }]);
    list.push(['saleItems', b.itemId, 'update', { returnedQty: { $inc: b.qty } }]);
    if (b.productId) {
      list.push(['stockMovements', ids.retMove(b.itemId), 'insert', {
        productId: b.productId, type: 'customer_return', qty: b.qty, date,
        notes: `مرجوعی فاکتور #${label}`, refId: ids.ret(b.itemId), createdAt: at,
      }]);
    }
  }
  const amountMinor = back.reduce((s, b) => s + b.amountMinor, 0);
  list.push(['auditLog', ids.retAudit(orderId), 'insert', {
    type: 'return', date, refId: saleId,
    notes: `مرجوعی به مبلغ ${afn(amountMinor)} افغانی — فاکتور #${label}`, createdAt: at, amount: 0,
  }]);

  const written = await appendOps(c, shopId, opsOf(docId, list), at);
  const snapshot = back.map((b) => ({
    itemId: b.itemId, productId: b.productId, qtyMilli: Math.round(b.qty * 1000), amountMinor: b.amountMinor,
  }));
  const link = await chainLink(c, shopId, {
    kind: 'order_doc', series: SERIES, docKind: 'return', docNo, shopId: String(shopId), orderId,
    saleRowId: saleId, totalMinor: amountMinor, linesHash: linesHash(snapshot), at,
  }, at);
  await insertDoc(c, {
    id: docId, shopId, orderId, kind: 'return', docNo, saleRowId: saleId, onStatus, totalMinor: amountMinor,
    lines: snapshot, ops: written.written, first: written.first, last: written.last,
    chain: link.chain, chainSeq: link.seq, hash: link.hash, actorType: actor.type || '', actorId: actor.id || '', at,
  });
  return { doc: docView(await docOf(c, orderId, 'return')), replayed: false, head: written.last };
}

/**
 * قلّابِ گذارِ سفارش — درونِ تراکنشِ `orders.applyTransition`، پس از نوشتنِ
 * وضعیت و رویداد (و پس از قلّابِ موجودیِ #263، اگر هست). هر مسیرِ دیگری که
 * وضعیتِ سفارش را بی `applyTransition` عوض کند (مثلِ دفترِ بازپرداختِ #255)
 * باید همین را با همان `c` صدا بزند.
 */
async function onOrderStatus(c, shopId, { id, to }, actor = {}, at = Date.now()) {
  if (POST_ON.includes(to)) return postSale(c, shopId, id, actor, at, to);
  if (REVERSE_ON.includes(to)) return postReturn(c, shopId, id, actor, at, to);
  return null;
}

/** پس از COMMIT: دستگاه‌های وصل همین حالا pull کنند (نبودِ سوکت ⇒ pullِ دوره‌ای) */
function notify(shopId, head) {
  try {
    require('./sync-v1-live').broadcast({ app: APP, accountKind: 'shop', accountId: shopId }, head);
  } catch { /* خبرِ زنده فقط شتاب است، نه درستی */ }
}

/** سندهای یک سفارش (برای پنل و آزمون) */
async function docsOf(run, orderId) {
  const q = typeof run === 'function' ? run : (t, p) => run.query(t, p);
  return (await q('SELECT * FROM order_ledger_docs WHERE order_id=$1 ORDER BY kind DESC', [orderId])).rows.map(docView);
}

module.exports = {
  FLAG_KEY, SERIES, POST_ON, REVERSE_ON,
  chainOf, ids, flagOn, onOrderStatus, postSale, postReturn, notify, docsOf, appendOps,
};
