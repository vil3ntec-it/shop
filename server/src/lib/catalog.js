'use strict';
/**
 * کاتالوگِ واحد و موجودیِ هوشمند — v3 §۳۲ و §۳۸.۳.
 *
 *   کالا ───── یک بار در دستگاهِ دکان تعریف می‌شود؛ این‌جا از دفترِ همگام‌سازی
 *              (`sync_rows`؛ پس از کوچ `sync_v3_rows`) با **همان شناسه** خوانده
 *              می‌شود (INV-01). دادهٔ همگام‌سازی فقط خوانده می‌شود.
 *   دستگاه ─── منبعِ حقیقتِ فروشِ حضوری است (آفلاین‌اول، D-05). موجودیِ دستگاه
 *              امروز = Σ ورودِ انبار − Σ (فروش − برگشت)ِ فاکتورهای لغونشده
 *              (`license/shop-sync.js` و `ShopStore.kt`). هر سندِ دستگاه
 *              «سهمی» دارد؛ همگام‌سازیِ دوباره فقط تفاوت را با حالتِ recorded
 *              در دفتر می‌نویسد ⇒ هیچ فروشی دو بار کم نمی‌شود (INV-07) و هیچ
 *              فروشِ ثبت‌شده‌ای رد نمی‌شود (INV-19).
 *   سفارش ──── ⛔ تصمیمِ صاحبِ مخزن (۱۴۰۵/۰۷/۲۷): سفارشِ آنلاین موجودی را **نه رزرو
 *              می‌کند نه کم**. دکان کالا را از برنامهٔ خودش تحویل می‌دهد و همان‌جا یک
 *              فروشِ عادی (با `orderId`) ثبت می‌شود؛ این کاتالوگ فقط دفترِ همگام‌شدهٔ
 *              دستگاه‌ها را می‌خواند. ساختِ سفارش فقط می‌سنجد (`checkOrder`، بی نوشتن).
 *   نمایش ──── «در قفسه» / «در انبار، آماده‌سازی لازم» / «تمام شده» /
 *              «در حال بررسی» / «قابلِ سفارش نیست» — موجودیِ نامعلوم یا کهنه
 *              هرگز «موجود» نیست (INV-02، INV-14).
 *
 * ⛔ پرچمِ `shop.catalog` پیش‌فرض خاموش؛ خاموش ⇒ هیچ ردیفی، همان رفتارِ پیشین.
 */
const { query, one, now, tx } = require('../db');
const core = require('./stock-core');
const stock = require('./stock');
const absorbLedger = require('./count-absorb-ledger');
const { toMinor, toMilli } = require('./sync-v3-convert');
const { badRequest, notFound, conflict, forbidden } = require('../middleware/errors');

//  O-06 = ب (ORD-20/CHK-18): نگه‌داشتنِ نرم — آزاد = دفتر − نگه‌داشتهٔ فعال (پرچمِ خاموشِ `shop.stock_reserve`)
const reserve = () => require('./stock-reserve');

const TABLES = Object.freeze({ v1: 'sync_rows', v3: 'sync_v3_rows' });
//  `stockMovements` فقط برای INV-10 (`count-absorb-ledger.collect`): موجودی همچنان از ورود و فروش است
const DEVICE_TABLES = ['products', 'warehouseEntries', 'sales', 'saleItems', 'auditLog', 'stockMovements'];
//  از `auditLog` فقط گام‌های انتقال (INV-11، `stock-transfers.ingestDevice`)؛ بقیهٔ سابقه هرگز خوانده نمی‌شود
const DEVICE_ROWS = "(table_name <> 'auditLog' OR data->>'type' = 'stock_transfer')";
const PAGE = 5000;
const KEY_RE = /^[A-Za-z0-9_.:-]{8,128}$/;
const MAX_LIST = 100;
const MAX_QTY_MILLI = 1_000_000_000;   // یک میلیون واحد

const str = (v) => (typeof v === 'string' ? v.trim() : '');
const num = (v) => (v === null || v === undefined ? 0 : Number(v));

function text(v, max) {
  const { stripControl } = require('./validate');
  return stripControl(str(v)).slice(0, max);
}

function decode(v) {
  if (v === null || v === undefined) return null;
  if (typeof v !== 'string') return v;
  try { return JSON.parse(v); } catch { return null; }
}

// ════════════════════════════════════════════════════════════════════
//  خواندنِ دفترِ همگام‌سازی
// ════════════════════════════════════════════════════════════════════

/** فیلدِ عددی در هر دو قالب: v1 اعشاری با نامِ قدیم، v3 صحیح با پسوند. */
const reader = (fmt) => ({
  qty(d, f) {
    const v = fmt === 'v3' ? d[`${f}Milli`] : toMilli(d[f]);
    return Number.isSafeInteger(v) ? v : 0;
  },
  money(d, f) {
    const v = fmt === 'v3' ? d[`${f}Minor`] : toMinor(d[f]);
    return Number.isSafeInteger(v) ? v : null;
  },
});

function barcodesOf(d) {
  const list = Array.isArray(d.barcodes) ? d.barcodes : (typeof d.barcode === 'string' ? [d.barcode] : []);
  return [...new Set(list.filter((b) => typeof b === 'string' && b.trim()).map((b) => b.trim().slice(0, 64)))].slice(0, 20);
}

async function upsertProduct(c, shopId, row, fmt, at, track = false) {
  const d = decode(row.data) || {};
  const rd = reader(fmt);
  const price = rd.money(d, 'salePrice');
  //  ضریبِ واحدِ فروش در هر دو قالب اعشاری است (فهرستِ qtyِ v3 فقط minStock دارد)
  const factor = toMilli(d.unitFactor);
  await c.query(
    `INSERT INTO catalog_products (shop_id, product_id, parent_id, variant, name, description, category, subcategory,
       brand, barcodes, unit, sale_unit, unit_factor_milli, price_minor, min_stock_milli, active, deleted,
       source, source_seq, updated_at)
     VALUES ($1,$2,$3,$4::jsonb,$5,$6,$7,$8,$9,$10::jsonb,$11,$12,$13,$14,$15,$16,$17,$18,$19,$20)
     ON CONFLICT (shop_id, product_id) DO UPDATE SET parent_id=excluded.parent_id, variant=excluded.variant,
       name=excluded.name, description=excluded.description, category=excluded.category,
       subcategory=excluded.subcategory, brand=excluded.brand, barcodes=excluded.barcodes, unit=excluded.unit,
       sale_unit=excluded.sale_unit, unit_factor_milli=excluded.unit_factor_milli, price_minor=excluded.price_minor,
       min_stock_milli=excluded.min_stock_milli, active=excluded.active, deleted=excluded.deleted,
       source=excluded.source, source_seq=excluded.source_seq, updated_at=excluded.updated_at`,
    [shopId, row.row_id, text(d.parentId, 80),
      JSON.stringify(d.variant && typeof d.variant === 'object' && !Array.isArray(d.variant) ? d.variant : {}),
      text(d.name, 200), text(d.description, 2000), text(d.category, 80), text(d.subcategory, 80), text(d.brand, 80),
      JSON.stringify(barcodesOf(d)), text(d.unit, 30), text(d.saleUnit, 30),
      Number.isSafeInteger(factor) && factor > 0 ? factor : 1000,
      price !== null && price > 0 ? price : null, Math.max(0, rd.qty(d, 'minStock')),
      d.active !== false && d.active !== 'false', row.deleted_at !== null && row.deleted_at !== undefined,
      fmt, num(row.updated_seq), at]
  ).then(() => track && require('./market-changes').catalogProduct(c, shopId, row, text(d.name, 200), price, d.active !== false, at));   //  S6
}

const isDeleted = (row) => row.deleted_at !== null && row.deleted_at !== undefined;
const cancelled = (row, d) => isDeleted(row) || d.status === 'cancelled';

/** زمانِ ثبت در دستگاه، وگرنه همین حالا. */
function atOf(d, at) {
  const t = Number(d.createdAt);
  return Number.isSafeInteger(t) && t > 0 ? t : at;
}

/**
 * دفترِ همگام‌سازیِ دستگاه ⇒ کاتالوگ + حرکت‌های recorded (فقط تفاوت).
 * درونِ تراکنشِ فراخواننده؛ قفلِ موجودیِ دکان را می‌گیرد.
 * ⇒ { products, moves, source }
 */
async function ingestTx(c, shopId, at, settings, { full = false } = {}) {
  await stock.lock(c, shopId);
  //  کنار گذاشتنِ موجودیِ سفارش‌محورِ پیشین — فقط دکانی که ردیفِ آن را دارد؛ پس از COMMIT در
  //  `refresh` در دفترِ ممیزی نوشته و «انجام‌شده» علامت می‌خورد
  let retiredNow = null;
  if (!retired.has(shopId)) {
    retiredNow = await retireOrderStock(c, shopId, at);
    if (retiredNow.touched.length) await stock.checkProducts(c, shopId, retiredNow.touched.sort(), settings, at);
  }
  const fmt = settings.syncSource === 'v3' ? 'v3' : 'v1';
  const T = TABLES[fmt];
  const rd = reader(fmt);
  const st = (await c.query('SELECT source, watermark FROM catalog_sync_state WHERE shop_id=$1', [shopId])).rows[0];
  //  منبع عوض شد ⇒ از اول خوانده می‌شود؛ سهم‌ها کلیدِ مستقل از قالب دارند، پس دوباره کم نمی‌شود
  let wm = !st || st.source !== fmt || full ? 0 : num(st.watermark);
  const ACC = "app='shop' AND account_kind='shop' AND account_id=$1";
  const track = await require('./market-changes').enabled(shopId, c);   //  S6: پرچمِ `market.changes` یک بار برای کلِ همین خواندن
  let products = 0;
  let moves = 0;
  const transfers = { applied: 0, refused: 0 };
  const touched = new Set();
  const counted = new Set();   //  INV-10: کالاهایی که ردیفِ شمارش یا حرکتِ دستگاهشان در این خواندن عوض شد
  for (;;) {
    const page = (await c.query(
      `SELECT table_name, row_id, data, deleted_at, updated_seq, created_at FROM ${T}
        WHERE ${ACC} AND table_name = ANY($2::text[]) AND ${DEVICE_ROWS} AND updated_seq > $3
        ORDER BY updated_seq, table_name, row_id LIMIT $4`,
      [shopId, DEVICE_TABLES, wm, PAGE]
    )).rows;
    if (!page.length) break;
    let rows = page;
    if (page.length === PAGE) {
      //  مرزِ صفحه وسطِ یک شماره نیفتد
      const last = num(page[page.length - 1].updated_seq);
      const cut = page.filter((r) => num(r.updated_seq) < last);
      if (cut.length) rows = cut;
    }

    const entries = [];
    const items = new Map();
    const cancelledSales = [];
    const steps = [];
    for (const r of rows) {
      if (r.table_name === 'auditLog') { steps.push(r); continue; }
      if (r.table_name === 'products') { await upsertProduct(c, shopId, r, fmt, at, track); products += 1; }
      else if (r.table_name === 'warehouseEntries') entries.push(r);
      else if (r.table_name === 'saleItems') items.set(r.row_id, r);
      else if (r.table_name === 'sales' && cancelled(r, decode(r.data) || {})) cancelledSales.push(r.row_id);
    }
    //  فاکتورِ لغوشده ⇒ قلم‌هایش (حتی اگر خودشان عوض نشده‌اند) سهمی ندارند
    if (cancelledSales.length) {
      const extra = (await c.query(
        `SELECT table_name, row_id, data, deleted_at, updated_seq FROM ${T}
          WHERE ${ACC} AND table_name='saleItems' AND data->>'saleId' = ANY($2::text[])`,
        [shopId, cancelledSales]
      )).rows;
      for (const r of extra) items.set(r.row_id, r);
    }
    const saleIds = [...new Set([...items.values()].map((r) => str((decode(r.data) || {}).saleId)).filter(Boolean))];
    const saleOff = new Set();
    if (saleIds.length) {
      for (const s of (await c.query(
        `SELECT row_id, data, deleted_at FROM ${T} WHERE ${ACC} AND table_name='sales' AND row_id = ANY($2::text[])`,
        [shopId, saleIds]
      )).rows) {
        if (cancelled(s, decode(s.data) || {})) saleOff.add(s.row_id);
      }
    }

    //  سهمِ مطلوبِ هر سند: { key ⇒ { want: Map<pid, milli>, kind, refType, refId, at, cancel } }
    const contrib = new Map();
    for (const r of entries) {
      const d = decode(r.data) || {};
      const want = new Map();
      const pid = str(d.productId);
      const q = rd.qty(d, 'units');
      if (pid && q !== 0 && !cancelled(r, d)) want.set(pid, q);
      contrib.set(`we:${r.row_id}`, { want, kind: 'we', refType: 'warehouseEntries', refId: r.row_id, at: atOf(d, at) });
    }
    for (const r of items.values()) {
      const d = decode(r.data) || {};
      const want = new Map();
      const pid = str(d.productId);
      const off = isDeleted(r) || saleOff.has(str(d.saleId));
      const q = rd.qty(d, 'quantity') - rd.qty(d, 'returnedQty');
      if (pid && q !== 0 && !off) want.set(pid, -q);
      contrib.set(`si:${r.row_id}`, { want, kind: 'si', refType: 'saleItems', refId: r.row_id, at: atOf(d, at), cancel: off });
    }

    if (contrib.size) {
      const posted = new Map();
      for (const p of (await c.query(
        'SELECT source_key, product_id, posted_milli, rev FROM stock_sources WHERE shop_id=$1 AND source_key = ANY($2::text[])',
        [shopId, [...contrib.keys()]]
      )).rows) {
        if (!posted.has(p.source_key)) posted.set(p.source_key, new Map());
        posted.get(p.source_key).set(p.product_id, { milli: num(p.posted_milli), rev: num(p.rev) });
      }
      for (const [key, k] of contrib) {
        const have = posted.get(key) || new Map();
        for (const pid of new Set([...k.want.keys(), ...have.keys()])) {
          const want = k.want.get(pid) || 0;
          const prev = have.get(pid) || { milli: 0, rev: 0 };
          const delta = want - prev.milli;
          if (delta === 0) continue;
          let type;
          let reason;
          if (k.kind === 'we') {
            type = delta > 0 ? 'purchase_receipt' : 'adjustment';
            reason = delta > 0 ? 'device_receipt' : 'device_correction';
          } else {
            type = delta < 0 ? 'sale' : 'customer_return';
            reason = delta < 0 ? 'device_sale' : (k.cancel ? 'device_cancel' : 'device_return');
          }
          const rev = prev.rev + 1;
          await stock.postMove(c, shopId, {
            productId: pid, location: settings.deviceLocation, type,
            qtyMilli: type === 'adjustment' ? delta : Math.abs(delta),
            mode: 'recorded', source: 'device', sourceKey: `dev:${key}:${pid}:${rev}`,
            refType: k.refType, refId: k.refId, actorType: 'device', actorId: '', reason, at: k.at,
          }, at);
          await c.query(
            `INSERT INTO stock_sources (shop_id, source_key, product_id, posted_milli, rev, updated_at)
             VALUES ($1,$2,$3,$4,$5,$6)
             ON CONFLICT (shop_id, source_key, product_id) DO UPDATE
               SET posted_milli=excluded.posted_milli, rev=excluded.rev, updated_at=excluded.updated_at`,
            [shopId, key, pid, want, rev, at]
          );
          touched.add(pid);
          moves += 1;
        }
      }
    }
    for (const pid of await absorbLedger.collect(c, shopId, rows, rd, at)) counted.add(pid);
    //  INV-11: گام‌های انتقالِ بی‌اینترنت پس از ورود و فروشِ همین صفحه، به ترتیبِ رسیدن (یک بار برای هر ردیف)
    if (steps.length) {
      const x = await require('./stock-transfers').ingestDevice(c, shopId, steps, settings, at);
      transfers.applied += x.applied;
      transfers.refused += x.refused;
    }
    wm = num(rows[rows.length - 1].updated_seq);
    if (page.length < PAGE) break;
  }

  await c.query(
    `INSERT INTO catalog_sync_state (shop_id, source, watermark, last_run_at) VALUES ($1,$2,$3,$4)
     ON CONFLICT (shop_id) DO UPDATE SET source=excluded.source, watermark=excluded.watermark, last_run_at=excluded.last_run_at`,
    [shopId, fmt, wm, at]
  );
  //  INV-10: فروشِ پیش از شمارش که پس از ثبتِ شمارش رسید دوباره کم نمی‌شود (پرچمِ `shop.count_absorb`)
  for (const pid of await absorbLedger.settle(c, shopId, settings, at, counted)) { touched.add(pid); moves += 1; }
  if (touched.size) await stock.checkProducts(c, shopId, [...touched].sort(), settings, at);
  return { products, moves, source: fmt, retired: retiredNow, ...(transfers.applied || transfers.refused ? { transfers } : {}) };
}

/**
 * خواندنِ دفترِ دستگاه **در تراکنشِ خودش**، پیش از تراکنشِ سفارش (QA #263): خواندنِ دفتر
 * درونِ تراکنشِ ساختِ سفارشِ مشتری و زیرِ قفلِ `orders:<دکان>` نمی‌ماند؛ سنجشِ فقط‌خواندنیِ
 * `checkOrder` با همین عددِ تازه است. بی ردیفِ تازه ⇒ هیچ. پرچم خاموش ⇒ هیچ.
 */
async function prepare(shopId, { at = now() } = {}) {
  if (!(await stock.flagOf(shopId))) return null;
  //  ساختِ سفارش کم‌شمار است ⇒ بی مهلتِ پرسش (فقط خواندنِ ویترین/بازار مهلت دارد)
  return refresh(shopId, { maxAgeMs: 1, at, throttle: false });
}

/**
 * آیا دفترِ دستگاه‌ها از آخرین خواندن ردیفِ تازه‌ای دارد؟ (یک پرسشِ نمایه‌دار؛ منبعِ
 * عوض‌شده یا هرگز‌نخوانده ⇒ «بله».)
 */
async function hasNewer(shopId) {
  const st = await one('SELECT source, watermark FROM catalog_sync_state WHERE shop_id=$1', [shopId]);
  if (!st) return true;
  const fmt = (await stock.settingsOf(shopId)).syncSource === 'v3' ? 'v3' : 'v1';
  if (st.source !== fmt) return true;
  const r = await one(
    `SELECT 1 AS x FROM ${TABLES[fmt]} WHERE app='shop' AND account_kind='shop' AND account_id=$1
        AND table_name = ANY($2::text[]) AND ${DEVICE_ROWS} AND updated_seq > $3 LIMIT 1`,
    [shopId, DEVICE_TABLES, num(st.watermark)]
  );
  return !!r;
}

/** فاصلهٔ کمینهٔ دو پرسشِ «ردیفِ تازه؟» برای یک دکان در خواندنِ پرترافیکِ ویترین/بازار */
const REFRESH_THROTTLE_MS = (() => {
  const v = Number(process.env.CATALOG_REFRESH_THROTTLE_MS);
  return Number.isSafeInteger(v) && v >= 0 ? v : 500;
})();
const lastCheck = new Map();
/** خواندنِ ویترینی که همین حالا در همین فرایند برای هر دکان در جریان است (`refresh` با `maxAgeMs`) */
const inflight = new Map();

/**
 * همگام‌سازیِ کاتالوگ در تراکنشِ خودش. `maxAgeMs` (خواندنِ ویترین/بازار) ⇒ فقط وقتی
 * دفترِ دستگاه‌ها ردیفِ تازه دارد خوانده می‌شود، و برای هر دکان حداکثر هر
 * [REFRESH_THROTTLE_MS] یک بار پرسیده می‌شود؛ پس فروشِ رسیده حداکثر همین‌قدر بعد در
 * ویترین است و خواندنِ پیاپیِ بی‌تغییر بار ندارد.
 *
 * ⛔ خواندنِ هم‌زمان (ویترین از #1010 کالاها، دکان و برجسته‌ها را با هم می‌خواهد): درخواستی که در
 * پنجرهٔ [REFRESH_THROTTLE_MS] می‌رسد و خواندنِ همان دکان هنوز تمام نشده، **منتظرِ همان** می‌ماند
 * و نه بی‌صبر رد می‌شود — وگرنه کالایی که همین حالا رسیده پیش از کامیت خوانده می‌شود و «قابلِ سفارش
 * نیست» (`unavailable`) می‌گیرد. خطای آن خواندن به این درخواست نمی‌رسد (همان رفتارِ «رد شد» ِ پیشین).
 */
async function refresh(shopId, { full = false, maxAgeMs = 0, at = now(), throttle = true } = {}) {
  if (maxAgeMs > 0 && !full) {
    const t = Date.now();
    if (throttle && t - (lastCheck.get(shopId) || 0) < REFRESH_THROTTLE_MS) {
      const running = inflight.get(shopId);
      if (running) await running.catch(() => {});
      return { skipped: true, throttled: true, ...(running ? { joined: true } : {}) };
    }
    lastCheck.set(shopId, t);
    if (lastCheck.size > 10_000) lastCheck.clear();
    const run = (async () => {
      if (!(await hasNewer(shopId))) return { skipped: true };
      return ingest(shopId, at, full);
    })();
    inflight.set(shopId, run);
    try {
      return await run;
    } finally {
      if (inflight.get(shopId) === run) inflight.delete(shopId);
    }
  }
  return ingest(shopId, at, full);
}

async function ingest(shopId, at, full) {
  const out = await tx(async (c) => ingestTx(c, shopId, at, await stock.settingsOf(shopId, c), { full }));
  await afterIngest(shopId, out);
  return out;
}

/**
 * پس از **کامیتِ** تراکنشی که `ingestTx` در آن دوید (خواندنِ کاتالوگ یا حرکتِ دستی): کنار
 * گذاشتنِ کسرِ سفارشیِ پیشین انجام‌شده است ⇒ دکان دیگر سنجیده نمی‌شود، و اگر چیزی عوض شد یک
 * سطر در دفترِ ممیزیِ دکان (دورِ دوم ⇒ هیچ). تراکنشِ برگشت‌خورده به این‌جا نمی‌رسد.
 */
async function afterIngest(shopId, out) {
  if (!out || !out.retired) return;
  retired.add(shopId);
  if (!out.retired.changed && !out.retired.needsAudit) return;
  //  یک سطر برای هر دکان، حتی با دو afterIngest ِ هم‌زمان یا دو فرایند (`once`: وجود در همان INSERT)
  await require('./audit').log({
    shopId, actorType: 'system', action: 'stock.order_stock_retired', targetType: 'shop', targetId: String(shopId),
    detail: out.retired.audit, once: true,
  });
}

// ════════════════════════════════════════════════════════════════════
//  کنار گذاشتنِ موجودیِ سفارش‌محورِ پیشین (یک بار برای هر دکان)
// ════════════════════════════════════════════════════════════════════

const retired = new Set();

/** آیا کسرِ سفارشیِ [m] قلمِ W ِ خودش را دارد؟ (دستگاه همان را کم کرده ⇒ نگه داشتن) */
async function keptByW(c, shopId, m) {
  const has = async (sql, key) => (await c.query(sql, [shopId, key])).rows.length > 0;
  const res = /^res:([^:]+):sale:/.exec(String(m.source_key || ''));
  if (res) {
    const line = (await c.query(
      'SELECT order_id, line_no FROM stock_reservations WHERE shop_id=$1 AND id=$2', [shopId, res[1]]
    )).rows[0];
    if (line) {
      return has('SELECT 1 FROM stock_sources WHERE shop_id=$1 AND source_key=$2 LIMIT 1', `si:w-${line.order_id}-${Number(line.line_no)}`);
    }
  }
  return has('SELECT 1 FROM stock_sources WHERE shop_id=$1 AND source_key LIKE $2 LIMIT 1', `si:w-${m.ref_id}-%`);
}

/**
 * پیش از تصمیمِ صاحبِ مخزن، `shop.catalog` سفارش را رزرو و هنگامِ تحویل «کسرِ قطعی»
 * (`res:<رزرو>:sale:<مکان>`، `source = order`) می‌کرد — کسری که دستگاه هرگز ندید. حالا موجودیِ
 * سرور فقط دفترِ دستگاه‌هاست، پس:
 *   ۱) هر کسرِ سفارشی که فاکتورِ W ندارد ⇒ یک حرکتِ جبرانیِ هم‌اندازه (`customer_return`،
 *      `source = system`، کلیدِ `undo:<کلید>`). دفتر فقط افزوده می‌شود، چیزی پاک یا ویرایش
 *      نمی‌شود و تطبیق (INV-16) همان می‌ماند. کسری که قلمِ فاکتورِ W دارد دست نمی‌خورد: دستگاه
 *      همان W را کم کرده، پس سرور و دستگاه یکی‌اند. سنجش **هر قلم جدا** ([keptByW]): رزروِ همان
 *      کسر (`res:<رزرو>:…`) ⇒ قلمِ سفارش ⇒ پیش‌ثبتِ همان قلمِ W (`si:w-<سفارش>-<قلم>`)؛ رزروِ
 *      گم‌شده ⇒ هر قلمِ W ِ همان سفارش (محتاطانه: نگه داشتن).
 *      (کنار گذاشتنِ این حرکت‌ها هنگامِ خواندن امن‌تر نبود: سهمِ W را دوباره بی‌کسر می‌کرد.)
 *   ۲) رزروِ «نگه‌داشته» ⇒ آزاد، با همان حرکتِ release (`res:<رزرو>:release`).
 *   ۳) وظیفه‌های بازِ «رزروِ بی‌پشتوانه» و «انتقال برای رزرو» ⇒ کنار گذاشته، با توضیح.
 *
 * فقط دکانی که `shop.catalog` برایش **روزی روشن بوده**: ingest فقط با پرچمِ روشن می‌دود، و
 * کاری انجام می‌شود فقط اگر ردیفِ سفارش‌محوری (`stock_reservations` یا حرکتِ `source = order`)
 * هست — که هر دو را فقط کدِ پیشین و فقط با پرچمِ روشن می‌نوشت. دیگر دکان‌ها ⇒ یک پرسشِ خالی.
 * تکرارپذیر: کلیدِ `undo:` یکتاست، رزروِ آزادشده دیگر «نگه‌داشته» نیست، وظیفهٔ بسته باز نیست ⇒
 * دورِ دوم (حتی پس از راه‌اندازیِ دوباره) هیچ نمی‌نویسد. شمارش‌ها در `audit` ⇒ دفترِ ممیزی.
 * درونِ تراکنشِ ingest (قفلِ دکان گرفته شده).
 */
/** توضیحِ وظیفه‌های رزروی که کنار گذاشته شد (نشانِ همان کار در دورِ بعد) */
const RETIRED_NOTE = 'سفارشِ آنلاین دیگر موجودی رزرو نمی‌کند (تصمیمِ صاحبِ مخزن)';

async function retireOrderStock(c, shopId, at) {
  const touched = new Set();
  const none = { changed: false, touched: [], audit: null };
  const legacy = (await c.query(
    `SELECT EXISTS (SELECT 1 FROM stock_reservations WHERE shop_id=$1 AND kind='legacy')
         OR EXISTS (SELECT 1 FROM stock_moves WHERE shop_id=$1 AND source='order' AND type='sale') AS any`, [shopId]
  )).rows[0];
  if (!legacy || !legacy.any) return none;
  let undoneMilli = 0;
  let keptWithW = 0;
  const sold = (await c.query(
    `SELECT m.product_id, m.location, m.qty_milli, m.source_key, m.ref_id FROM stock_moves m
      WHERE m.shop_id=$1 AND m.source='order' AND m.type='sale'
        AND NOT EXISTS (SELECT 1 FROM stock_moves x WHERE x.shop_id=m.shop_id AND x.source_key = 'undo:' || m.source_key)
      ORDER BY m.created_at, m.id`, [shopId]
  )).rows;
  for (const m of sold) {
    if (await keptByW(c, shopId, m)) { keptWithW += 1; continue; }
    undoneMilli += num(m.qty_milli);
    await stock.postMove(c, shopId, {
      productId: m.product_id, location: m.location, type: 'customer_return', qtyMilli: num(m.qty_milli),
      mode: 'recorded', source: 'system', sourceKey: `undo:${m.source_key}`, refType: 'order', refId: m.ref_id,
      actorType: 'system', actorId: '', reason: 'order_stock_retired', at,
    }, at);
    touched.add(m.product_id);
  }
  const held = (await c.query(
    `UPDATE stock_reservations SET status='released', closed_at=$2, close_reason='reservations_retired'
      WHERE shop_id=$1 AND status='held' AND kind='legacy' RETURNING id, product_id, order_id, qty_milli`, [shopId, at]
  )).rows;
  for (const r of held) {
    await stock.insertMove(c, shopId, {
      productId: r.product_id, location: '', type: 'release', qtyMilli: num(r.qty_milli),
      deltaOnHand: 0, deltaReserved: -num(r.qty_milli), mode: 'local', source: 'system',
      sourceKey: `res:${r.id}:release`, refType: 'order', refId: r.order_id,
      actorType: 'system', actorId: '', reason: 'reservations_retired', at,
    }, at);
  }
  const tasks = await c.query(
    `UPDATE stock_tasks SET status='dismissed', resolved_by='system', resolved_at=$2, updated_at=$2,
            note=$3
      WHERE shop_id=$1 AND status='open' AND kind IN ('reservation_short', 'transfer_needed')
        AND dedupe NOT LIKE 'soft-short:%'`, [shopId, at, RETIRED_NOTE]
  );
  const undone = sold.length - keptWithW;
  const dismissed = tasks.rowCount || 0;
  const audit = {
    reason: 'online_orders_no_longer_touch_stock', undoneMoves: undone, undoneMilli, keptWithW,
    releasedHolds: held.length, releasedMilli: held.reduce((s, r) => s + num(r.qty_milli), 0),
    dismissedTasks: dismissed, products: [...touched].sort().slice(0, 50),
  };
  const changed = undone + held.length + dismissed > 0;
  if (changed) return { changed, touched: [...touched], audit };
  //  دورِ پیشین کنار گذاشت ولی سطرِ ممیزی‌اش نوشته نشد (خطا یا افتادنِ سرور پس از COMMIT) ⇒ همان
  //  سطر از روی خودِ دفتر (`recovered`)؛ سطر هست ⇒ هیچ
  const done = (await c.query(
    `SELECT (SELECT COUNT(*) FROM stock_moves WHERE shop_id=$1 AND source='system' AND source_key LIKE 'undo:%')::int AS moves,
            (SELECT COALESCE(SUM(qty_milli),0) FROM stock_moves WHERE shop_id=$1 AND source='system' AND source_key LIKE 'undo:%')::bigint AS milli,
            (SELECT COUNT(*) FROM stock_reservations WHERE shop_id=$1 AND close_reason='reservations_retired')::int AS holds,
            (SELECT COALESCE(SUM(qty_milli),0) FROM stock_reservations WHERE shop_id=$1 AND close_reason='reservations_retired')::bigint AS held_milli,
            (SELECT COUNT(*) FROM stock_tasks WHERE shop_id=$1 AND status='dismissed' AND resolved_by='system' AND note=$2)::int AS tasks,
            EXISTS (SELECT 1 FROM audit_logs WHERE shop_id=$1 AND action='stock.order_stock_retired') AS logged`,
    [shopId, RETIRED_NOTE]
  )).rows[0];
  if (done.logged || num(done.moves) + num(done.holds) + num(done.tasks) === 0) return { changed, touched: [...touched], audit };
  const products = (await c.query(
    `SELECT DISTINCT product_id FROM stock_moves WHERE shop_id=$1 AND source='system' AND source_key LIKE 'undo:%' ORDER BY 1 LIMIT 50`,
    [shopId]
  )).rows.map((r) => r.product_id);
  return {
    changed, touched: [...touched], needsAudit: true,
    audit: {
      reason: audit.reason, undoneMoves: num(done.moves), undoneMilli: num(done.milli), keptWithW,
      releasedHolds: num(done.holds), releasedMilli: num(done.held_milli), dismissedTasks: num(done.tasks),
      products, recovered: true,
    },
  };
}

// ════════════════════════════════════════════════════════════════════
//  تازگی و وضعیتِ نمایش (INV-02، INV-03، INV-14)
// ════════════════════════════════════════════════════════════════════

/**
 * موجودی فقط وقتی «تازه» است که کاتالوگ دست‌کم یک بار خوانده شده و یکی از
 * دستگاه‌های دکان در مهلتِ `staleAfterSec` دیده شده و هیچ دستگاهِ تازه‌دیده‌ای
 * صفِ نفرستاده ندارد.
 */
async function freshness(shopId, settings, at, c = null) {
  //  درونِ تراکنش (ساختِ سفارش، گذار) ⇒ همان اتصال: استخرِ PostgreSQL زیرِ قفل خالی نمی‌ماند
  const row = c ? async (t, p) => (await c.query(t, p)).rows[0] : one;
  const since = at - settings.staleAfterSec * 1000;
  //  `queued` ِ Sync v1 اندازهٔ صف **پیش از** همان push است (دسته را هم می‌شمارد). برنامه پس از
  //  خالی کردنِ صف pull می‌کند ⇒ pull ِ پس از آخرین push یعنی صف خالی شد؛ وگرنه پس از هر فروش
  //  ویترین تا pushِ بعدی «در حال بررسی» می‌ماند و عددِ تازه را نشان نمی‌داد.
  const v1 = await row(
    `SELECT COALESCE(MAX(last_seen_at),0)::bigint AS seen,
            COALESCE(MAX(CASE WHEN last_seen_at >= $2
                               AND NOT (last_push_at IS NOT NULL AND last_pull_at IS NOT NULL AND last_pull_at >= last_push_at)
                              THEN queued_count ELSE 0 END),0)::int AS queued
       FROM sync_devices WHERE app='shop' AND account_kind='shop' AND account_id=$1`, [shopId, since]
  );
  const v3 = await row(
    `SELECT COALESCE(MAX(last_seen_at),0)::bigint AS seen,
            COALESCE(MAX(CASE WHEN last_seen_at >= $2 THEN queued ELSE 0 END),0)::int AS queued
       FROM sync_v3_devices WHERE app='shop' AND account_kind='shop' AND account_id=$1`, [shopId, since]
  );
  const st = await row('SELECT last_run_at FROM catalog_sync_state WHERE shop_id=$1', [shopId]);
  const seen = Math.max(num(v1 && v1.seen), num(v3 && v3.seen));
  const queued = Math.max(num(v1 && v1.queued), num(v3 && v3.queued));
  let reason = '';
  if (!st) reason = 'never_synced';
  else if (seen < since) reason = 'stale';
  else if (queued > 0) reason = 'pending_sync';
  return { fresh: !reason, reason, checkedAt: seen || null };
}

/**
 * وضعیتِ موجودیِ هر کالا. `exact` ⇒ عددها هم (کارمند همیشه؛ مشتری فقط با
 * `showQty = exact`). مقدارها به هزارمِ «واحدِ موجودی» (`unit`).
 */
function badgeOf(p, lv, held, settings, fr, exact) {
  if (!p || p.deleted || !p.active) return { status: 'unavailable' };
  if (!fr.fresh) return { status: 'unverified', reason: fr.reason, checkedAt: fr.checkedAt };
  const f = stock.freeOf(lv, held, settings);
  let status = 'out_of_stock';
  if (f.readyMilli > 0) status = 'in_stock';
  else if (f.totalMilli > 0) status = 'in_warehouse';
  const out = { status, checkedAt: fr.checkedAt, unit: p.unit };
  if (status === 'in_warehouse') out.prepMinutes = settings.prepMinutes;
  if (exact) Object.assign(out, { sellableMilli: f.totalMilli, readyMilli: f.readyMilli, laterMilli: f.laterMilli });
  return out;
}

async function productsOf(shopId, ids) {
  if (!ids.length) return new Map();
  const rows = (await query(
    `SELECT cp.*, EXISTS (SELECT 1 FROM product_photos ph WHERE ph.app='shop' AND ph.account_id=cp.shop_id
                            AND ph.product_id=cp.product_id AND (ph.data IS NOT NULL OR ph.store_key <> '')) AS has_photo
       FROM catalog_products cp WHERE cp.shop_id=$1 AND cp.product_id = ANY($2::text[])`, [shopId, ids]
  )).rows;
  return new Map(rows.map((r) => [r.product_id, r]));
}

/**
 * دری برای ویترین (PR #244 و بعد): وضعیتِ موجودیِ کالاها برای مشتری.
 * پرچم خاموش ⇒ null (ویترین همان «در حال بررسی»ِ خودش را نشان می‌دهد).
 * ⇒ Map<productId, badge>
 */
async function publicStock(shopId, productIds, { at = now() } = {}) {
  if (!(await stock.flagOf(shopId))) return null;
  const ids = [...new Set((productIds || []).map(String))].slice(0, 500);
  await refresh(shopId, { maxAgeMs: 5000, at });
  const settings = await stock.settingsOf(shopId);
  const fr = await freshness(shopId, settings, at);
  const [ps, lvs, held] = await Promise.all([
    productsOf(shopId, ids), stock.levelsOf(null, shopId, ids), reserve().heldOf(null, shopId, ids, at),
  ]);
  const out = new Map();
  //  دفترِ همگام‌شدهٔ دستگاه‌ها − نگه‌داشتهٔ فعالِ سفارش‌ها (O-06 = ب؛ بی پرچم همیشه ۰) — سرور هرگز کم نمی‌کند
  for (const id of ids) {
    out.set(id, badgeOf(ps.get(id), lvs.get(id), held.get(id) || 0, settings, fr, settings.showQty === 'exact'));
  }
  return out;
}

// ════════════════════════════════════════════════════════════════════
//  خواندنِ کارمند
// ════════════════════════════════════════════════════════════════════

function productView(p, lv, held, settings, fr) {
  const levels = {};
  for (const loc of stock.LOCATIONS) levels[loc] = stock.onHandAt(lv, loc);
  const f = stock.freeOf(lv, held, settings);
  return {
    id: p.product_id, name: p.name, description: p.description, category: p.category, subcategory: p.subcategory,
    brand: p.brand, barcodes: decode(p.barcodes) || [], unit: p.unit, saleUnit: p.sale_unit,
    unitFactorMilli: num(p.unit_factor_milli), priceMinor: p.price_minor === null ? null : num(p.price_minor),
    minStockMilli: num(p.min_stock_milli), active: p.active, deleted: p.deleted, parentId: p.parent_id,
    variant: decode(p.variant) || {}, photo: p.has_photo === true,
    stock: {
      levels, reservedMilli: held, sellableMilli: f.totalMilli,
      badge: badgeOf(p, lv, held, settings, fr, true),
    },
  };
}

function encodeCursor(name, id) { return Buffer.from(JSON.stringify([name, id])).toString('base64url'); }
function decodeCursor(raw) {
  if (!raw) return null;
  try {
    const v = JSON.parse(Buffer.from(String(raw), 'base64url').toString('utf8'));
    if (Array.isArray(v) && v.length === 2 && v.every((x) => typeof x === 'string')) return v;
  } catch { /* نامعتبر */ }
  throw badRequest('نشانگرِ صفحه معتبر نیست', 'bad_cursor');
}

async function list(shopId, { q, cursor, limit } = {}) {
  await refresh(shopId);
  const lim = Math.min(Math.max(Number(limit) || 50, 1), MAX_LIST);
  const params = [shopId];
  const where = ['cp.shop_id=$1', 'NOT cp.deleted'];
  const term = text(q, 80).toLowerCase();
  if (term) {
    params.push(term);
    where.push(`(position($${params.length} in lower(cp.name)) > 0 OR cp.barcodes @> to_jsonb(ARRAY[$${params.length}::text]))`);
  }
  const cur = decodeCursor(cursor);
  if (cur) {
    params.push(cur[0], cur[1]);
    where.push(`(cp.name, cp.product_id) > ($${params.length - 1}, $${params.length})`);
  }
  params.push(lim + 1);
  const rows = (await query(
    `SELECT cp.product_id FROM catalog_products cp WHERE ${where.join(' AND ')}
      ORDER BY cp.name, cp.product_id LIMIT $${params.length}`, params
  )).rows;
  const page = rows.slice(0, lim).map((r) => r.product_id);
  const settings = await stock.settingsOf(shopId);
  const fr = await freshness(shopId, settings, now());
  const [ps, lvs, held] = await Promise.all([
    productsOf(shopId, page), stock.levelsOf(null, shopId, page), reserve().heldOf(null, shopId, page),
  ]);
  const items = page.map((id) => productView(ps.get(id), lvs.get(id), held.get(id) || 0, settings, fr));
  const last = items[items.length - 1];
  return {
    items, freshness: fr,
    nextCursor: rows.length > lim && last ? encodeCursor(last.name, last.id) : null,
  };
}

async function detail(shopId, productId) {
  await refresh(shopId);
  const id = String(productId || '');
  const ps = await productsOf(shopId, [id]);
  const p = ps.get(id);
  if (!p) throw notFound('کالا پیدا نشد', 'product_not_found');
  const settings = await stock.settingsOf(shopId);
  const fr = await freshness(shopId, settings, now());
  const lv = (await stock.levelsOf(null, shopId, [id])).get(id);
  const lows = {};
  for (const loc of stock.LOCATIONS) if (lv && lv[loc] && lv[loc].low !== null) lows[loc] = lv[loc].low;
  const held = (await reserve().heldOf(null, shopId, [id])).get(id) || 0;
  return { product: productView(p, lv, held, settings, fr), lowByLocation: lows, freshness: fr };
}

function moveView(r) {
  return {
    id: r.id, productId: r.product_id, location: r.location, type: r.type, qtyMilli: num(r.qty_milli),
    deltaOnHandMilli: num(r.delta_on_hand_milli), deltaReservedMilli: num(r.delta_reserved_milli),
    mode: r.mode, source: r.source, refType: r.ref_type, refId: r.ref_id,
    actorType: r.actor_type, actorId: r.actor_id, reason: r.reason, note: r.note,
    at: num(r.at), createdAt: num(r.created_at),
  };
}

/** دفترِ گردش (INV-16) — تازه‌ترین اول، صفحه‌بندیِ کلیدی. */
async function moves(shopId, { productId, cursor, limit, places = null } = {}) {
  const lim = Math.min(Math.max(Number(limit) || 50, 1), MAX_LIST);
  const params = [shopId];
  const where = places ? ['shop_id=$1', `location = ANY($${params.push(places)}::text[])`] : ['shop_id=$1'];   //  STF-06: فقط مکان‌های محدوده
  if (productId) { params.push(String(productId)); where.push(`product_id=$${params.length}`); }
  const cur = decodeCursor(cursor);
  if (cur) {
    params.push(Number(cur[0]), cur[1]);
    where.push(`(created_at, id) < ($${params.length - 1}, $${params.length})`);
  }
  params.push(lim + 1);
  const rows = (await query(
    `SELECT * FROM stock_moves WHERE ${where.join(' AND ')} ORDER BY created_at DESC, id DESC LIMIT $${params.length}`, params
  )).rows;
  const items = rows.slice(0, lim).map(moveView);
  const last = items[items.length - 1];
  return { items, nextCursor: rows.length > lim && last ? encodeCursor(String(last.createdAt), last.id) : null };
}

// ════════════════════════════════════════════════════════════════════
//  وظیفه‌ها (INV-19)
// ════════════════════════════════════════════════════════════════════

async function tasks(shopId, { status = 'open', limit, places = null } = {}) {   //  places: STF-06 (`staff-scope.taskPlaces`)
  if (!['open', 'resolved', 'dismissed'].includes(status)) throw badRequest('وضعیتِ وظیفه معتبر نیست', 'bad_status');
  const lim = Math.min(Math.max(Number(limit) || 50, 1), MAX_LIST);
  return (await query(
    `SELECT * FROM stock_tasks WHERE shop_id=$1 AND status=$2${places ? " AND (location = ANY($4::text[]) OR (kind = 'transfer_short' AND detail->>'to' = ANY($4::text[])))" : ''} ORDER BY created_at DESC, id DESC LIMIT $3`,
    places ? [shopId, status, lim, places] : [shopId, status, lim]
  )).rows.map((r) => ({
    id: r.id, kind: r.kind, productId: r.product_id, location: r.location, status: r.status,
    detail: decode(r.detail) || {}, hits: num(r.hits), createdAt: num(r.created_at), updatedAt: num(r.updated_at),
    resolvedBy: r.resolved_by, resolvedAt: r.resolved_at === null ? null : num(r.resolved_at), note: r.note,
  }));
}

async function resolveTask(shopId, id, body, userId) {
  const action = str(body && body.action);
  if (!['resolved', 'dismissed'].includes(action)) throw badRequest('کار: resolved یا dismissed', 'bad_action');
  const note = text(body && body.note, 300);
  if (!note) throw badRequest('توضیحِ رسیدگی لازم است', 'note_required');
  const at = now();
  const r = await query(
    `UPDATE stock_tasks SET status=$3, resolved_by=$4, resolved_at=$5, note=$6, updated_at=$5
      WHERE shop_id=$1 AND id=$2 AND status='open' RETURNING id`, [shopId, String(id || ''), action, userId, at, note]
  );
  if (!r.rows.length) throw notFound('وظیفهٔ باز پیدا نشد', 'task_not_found');
  return { id: r.rows[0].id, status: action };
}

//  حرکتِ دستیِ کارمند (INV-08، INV-12، INV-15) — `lib/catalog-manual.js`
const { MANUAL, TRANSFER_LOCS, parseManual, manualMove, setLow } = require('./catalog-manual')({
  str, text, KEY_RE, MAX_QTY_MILLI, moveView, ingestTx, afterIngest,
});

// ════════════════════════════════════════════════════════════════════
//  سفارش (CHK-18، INV-03..05، INV-09، INV-10، INV-13)
// ════════════════════════════════════════════════════════════════════

/**
 * قلم‌های سفارش ⇒ مقدار به واحدِ موجودی. کالای ناموجود/غیرفعال/حذف‌شده ⇒
 * `product_unavailable`؛ واحدی که برای کالا تعریف نشده ⇒ `bad_unit` (واحدها
 * هرگز قاطی نمی‌شوند).
 */
async function toStockLines(c, shopId, lines) {
  const ids = [...new Set(lines.map((l) => l.productId))];
  const rows = (await c.query(
    `SELECT product_id, name, unit, sale_unit, unit_factor_milli, active, deleted FROM catalog_products
      WHERE shop_id=$1 AND product_id = ANY($2::text[])`, [shopId, ids]
  )).rows;
  const by = new Map(rows.map((r) => [r.product_id, r]));
  const unavailable = ids.filter((id) => !by.has(id) || !by.get(id).active || by.get(id).deleted).sort();
  if (unavailable.length) {
    const err = conflict('برخی کالاها دیگر قابلِ سفارش نیستند', 'product_unavailable');
    err.data = { productIds: unavailable };
    throw err;
  }
  const names = new Map(rows.map((r) => [r.product_id, { name: r.name, unit: r.unit }]));
  const out = lines.map((l) => {
    const p = by.get(l.productId);
    const u = str(l.unit);
    const q = num(l.qtyMilli);
    let qty;
    if (!u || u === p.unit) qty = q;
    else if (p.sale_unit && u === p.sale_unit) qty = core.mulDivRound(q, num(p.unit_factor_milli), 1000);
    else {
      const err = badRequest(
        `واحدِ «${u}» برای «${p.name}» تعریف نشده؛ فقط «${p.unit || '—'}»${p.sale_unit ? ` یا «${p.sale_unit}»` : ''}`,
        'bad_unit'
      );
      err.data = { productId: p.product_id, units: [p.unit, p.sale_unit].filter(Boolean) };
      throw err;
    }
    if (!(qty > 0)) throw badRequest('مقدارِ قلم معتبر نیست', 'bad_qty');
    return { lineNo: num(l.lineNo), productId: l.productId, qtyMilli: qty };
  });
  return { lines: out, names };
}

/**
 * درونِ تراکنشِ `orders.create`، پس از بررسیِ کلیدِ یکتایی: پرچم روشن ⇒ قلم‌های کالادار
 * **فقط سنجیده** می‌شوند — کالای ناموجود/غیرفعال/حذف‌شده (`product_unavailable`)، واحدِ
 * ناشناس (`bad_unit`) و مقدارِ بیش از موجودیِ قابل‌فروشِ همین لحظه (`out_of_stock`).
 * ⛔ هیچ حرکتِ مکان‌دار و هیچ کسری (تصمیمِ صاحبِ مخزن): کالا را دکان از برنامه تحویل می‌دهد و فروش
 * همان‌جا ثبت می‌شود. سفارشِ آزمایشی و پرچمِ خاموش ⇒ null. استثنای O-06 = ب (`shop.stock_reserve` +
 * موجودیِ تازه): سنجش با نگه‌داشتهٔ دیگران و زیرِ قفلِ موجودی ⇒ `hold` برای `orders.create`.
 */
async function checkOrder(c, shopId, { lines, isTest = false, at = now() }) {
  if (isTest) return null;
  const withProduct = (lines || []).filter((l) => str(l.productId));
  if (!withProduct.length) return null;
  if (!(await stock.flagOf(shopId, c))) return null;
  const settings = await stock.settingsOf(shopId, c);
  const { lines: sl, names } = await toStockLines(c, shopId, withProduct);
  return reserve().checkOrderIn(c, shopId, { lines: sl, settings, names, at });
}

/** برای مسیرهای HTTP: پرچم لازم است. */
async function requireEnabled(shopId) {
  if (!(await stock.flagOf(shopId))) throw forbidden('کاتالوگ و موجودیِ سرور برای این دکان روشن نیست', 'catalog_disabled');
}

module.exports = {
  MANUAL, TRANSFER_LOCS, TABLES, PAGE, upsertProduct,   //  آخری‌ها برای catalog-products.js (S1)
  barcodesOf, ingestTx, refresh, hasNewer, retireOrderStock, afterIngest, REFRESH_THROTTLE_MS, prepare, freshness, badgeOf, publicStock,
  list, detail, moves, tasks, resolveTask,
  parseManual, manualMove, setLow,
  toStockLines, checkOrder, requireEnabled,
};
