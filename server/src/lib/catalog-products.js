'use strict';
/**
 * فقط بخشِ کالای کاتالوگ — برای ویترین و بازار (S1 ِ قراردادِ بازار)، مستقل از `shop.catalog`.
 * از `lib/catalog.js` جدا شد تا زیرِ سقفِ اندازه بماند؛ نوشتنِ هر ردیف همان `upsertProduct` ِ آن‌جاست.
 *
 * ویترینِ دکانی که پرچمِ `storefront.catalog_source` دارد از `catalog_products` می‌خواند؛
 * این‌جا فقط ردیف‌های `products` ِ دفترِ دستگاه‌ها در همان جدول نوشته می‌شود — نه موجودی،
 * نه `catalog_sync_state` (که آبراههٔ موجودی است و اگر جلو برود، روشن کردنِ بعدیِ
 * `shop.catalog` ورودها و فروش‌های پیشین را نمی‌خواند). آبراهه = بیشترین `source_seq` ِ
 * کالاهای همین منبع؛ نوشتن زیرِ همان قفلِ `ingestTx` ⇒ دو خواننده دادهٔ کهنه را روی تازه نمی‌نشانند.
 */
const { one, now, tx } = require('../db');
const stock = require('./stock');
const catalog = require('./catalog');

const num = (v) => (v === null || v === undefined ? 0 : Number(v));

/**
 * منبعِ کالاهای دکان: انتخابِ صریحِ صاحب دکان (`catalog_settings.sync_source`) اگر هست؛
 * وگرنه با `shop.catalog` روشن همان پیش‌فرضِ `ingestTx` (v1)؛ وگرنه v3 اگر دستگاه‌های
 * دکان کالا در دفترِ v3 دارند (کوچ‌کرده؛ پلِ v1 ⇒ v3 کالای v1 را هم آن‌جا می‌آورد)، وگرنه v1.
 */
async function productSourceOf(shopId) {
  const s = await one('SELECT sync_source FROM catalog_settings WHERE shop_id=$1', [shopId]);
  if (s) return s.sync_source === 'v3' ? 'v3' : 'v1';
  if (await stock.flagOf(shopId)) return 'v1';
  const v3 = await one(
    `SELECT 1 AS x FROM sync_v3_rows WHERE app='shop' AND account_kind='shop' AND account_id=$1
        AND table_name='products' LIMIT 1`, [shopId]
  );
  return v3 ? 'v3' : 'v1';
}

const productChecks = new Map();
/** تازه‌کردنِ ویترینیِ (`throttle`) هر دکان که همین حالا در همین فرایند در جریان است */
const productRuns = new Map();

/**
 * کالاهای تازهٔ دفترِ دستگاه‌ها ⇒ `catalog_products` (فقط کالا). `throttle` (خواندنِ ویترین/بازار)
 * ⇒ برای هر دکان حداکثر هر [REFRESH_THROTTLE_MS] یک پرسشِ «ردیفِ تازه؟»؛ ثبتِ سفارش بی مهلت.
 * ⇒ { products, source } یا { skipped }
 *
 * ⛔ خواندنِ هم‌زمان (ویترین از #1010 کالاها، دکان و برجسته‌ها را با هم می‌خواهد): درخواستی که در پنجره
 * می‌رسد و تازه‌کردنِ همان دکان هنوز تمام نشده **منتظرِ همان** می‌ماند — وگرنه روی PostgreSQL (استخرِ چند
 * اتصالی) پیش از کامیت می‌خواند و قیمت/کالای کهنه می‌بیند (PGlite یک اتصال دارد و این را پنهان می‌کرد).
 * خطای آن تازه‌کردن به این درخواست نمی‌رسد (همان رفتارِ «رد شد» ِ پیشین؛ همان قاعدهٔ `catalog.refresh`).
 */
async function refreshProducts(shopId, { at = now(), throttle = true } = {}) {
  if (!throttle) return readProducts(shopId, at);
  const t = Date.now();
  if (t - (productChecks.get(shopId) || 0) < catalog.REFRESH_THROTTLE_MS) {
    const running = productRuns.get(shopId);
    if (running) await running.catch(() => {});
    return { skipped: true, throttled: true, ...(running ? { joined: true } : {}) };
  }
  productChecks.set(shopId, t);
  if (productChecks.size > 10_000) productChecks.clear();
  const run = readProducts(shopId, at);
  productRuns.set(shopId, run);
  try {
    return await run;
  } finally {
    if (productRuns.get(shopId) === run) productRuns.delete(shopId);
  }
}

async function readProducts(shopId, at) {
  const { TABLES, PAGE, upsertProduct } = catalog;
  const fmt = await productSourceOf(shopId);
  const T = TABLES[fmt];
  const ACC = "app='shop' AND account_kind='shop' AND account_id=$1";
  const WM = "COALESCE((SELECT MAX(source_seq) FROM catalog_products WHERE shop_id=$1 AND source=$2), 0)";
  const newer = await one(
    `SELECT 1 AS x FROM ${T} WHERE ${ACC} AND table_name='products' AND updated_seq > ${WM} LIMIT 1`, [shopId, fmt]
  );
  if (!newer) return { skipped: true, source: fmt };
  return tx(async (c) => {
    await stock.lock(c, shopId);
    let wm = num((await c.query(`SELECT ${WM} AS wm`, [shopId, fmt])).rows[0].wm);
    let products = 0;
    for (;;) {
      const page = (await c.query(
        `SELECT row_id, data, deleted_at, updated_seq FROM ${T}
          WHERE ${ACC} AND table_name='products' AND updated_seq > $2
          ORDER BY updated_seq, row_id LIMIT $3`,
        [shopId, wm, PAGE]
      )).rows;
      if (!page.length) break;
      let rows = page;
      if (page.length === PAGE) {
        //  مرزِ صفحه وسطِ یک شماره نیفتد (همان قاعدهٔ `ingestTx`)
        const last = num(page[page.length - 1].updated_seq);
        const cut = page.filter((r) => num(r.updated_seq) < last);
        if (cut.length) rows = cut;
      }
      for (const r of rows) { await upsertProduct(c, shopId, r, fmt, at); products += 1; }
      wm = num(rows[rows.length - 1].updated_seq);
      if (page.length < PAGE) break;
    }
    return { products, source: fmt };
  });
}

// ════════════════════════════════════════════════════════════════════
//  S86 (§۱۹.۱۲) — بازسازیِ projection ِ کاتالوگ
// ════════════════════════════════════════════════════════════════════

/** ستون‌هایی که projection از ردیفِ منبع می‌سازد (`upsertProduct`) — `updated_at` فقط زمانِ نوشتن است */
const FIELDS = ['parent_id', 'variant', 'name', 'description', 'category', 'subcategory', 'brand', 'barcodes', 'unit',
  'sale_unit', 'unit_factor_milli', 'price_minor', 'min_stock_milli', 'active', 'deleted', 'source', 'source_seq'];
const same = (a, b) => (a && typeof a === 'object') || (b && typeof b === 'object')
  ? JSON.stringify(a) === JSON.stringify(b) : String(a) === String(b);

/**
 * بازسازیِ `catalog_products` ِ یک دکان از **همهٔ** ردیف‌های `products` ِ دفترِ دستگاه‌ها (منبعِ [productSourceOf]،
 * همان که ویترین و ingest می‌خوانند): درونِ یک SAVEPOINT کالاهای دکان پاک و با همان `upsertProduct` از نو ساخته و
 * با projection ِ زنده سنجیده می‌شوند، سپس همان SAVEPOINT برمی‌گردد و فقط تفاوت نوشته می‌شود:
 *   • `built` (در منبع هست، این‌جا نیست)، `fixed` (نام، قیمتِ نمایش، فعال/حذف، … فرق دارد؛ `fields`)، `dropped` (در منبع نیست ⇒ پاک)؛
 *   • پیش‌فرض فقط نشان می‌دهد؛ با `apply` زیرِ همان قفلِ موجودیِ دکان (`stock.lock` ِ هر نویسندهٔ کاتالوگ) و در یک
 *     تراکنش با سابقهٔ `catalog.rebuild`؛ بی تفاوت هیچ نوشتن و هیچ سابقه ⇒ بارِ دوم بی‌اثر؛ دکانِ دیگر و دفتر دست نمی‌خورند.
 *   • با `market.changes` تفاوتِ هر کالا در خوراکِ بازار هم ثبت می‌شود (همان راهِ ingest؛ برداشته ⇒ `unpublish`).
 * @returns {{ok, applied, source, count: {projection, source}, changes: Array<{productId, kind, fields?}>}}
 *          `ok` = پس از این کار projection با منبع می‌خواند
 */
async function rebuildProducts(shopId, { apply = false, by = '' } = {}) {
  if (!shopId) throw new Error('شناسهٔ دکان لازم است');
  const fmt = await productSourceOf(shopId);
  const T = catalog.TABLES[fmt];
  return tx(async (c) => {
    await stock.lock(c, shopId);
    const read = async () => new Map((await c.query('SELECT * FROM catalog_products WHERE shop_id=$1', [shopId])).rows
      .map((r) => [r.product_id, r]));
    const live = await read();
    const rows = (await c.query(
      `SELECT row_id, data, deleted_at, updated_seq FROM ${T}
        WHERE app='shop' AND account_kind='shop' AND account_id=$1 AND table_name='products' ORDER BY row_id`, [shopId]
    )).rows;
    const at = now();
    await c.query('SAVEPOINT s86_catalog');
    await c.query('DELETE FROM catalog_products WHERE shop_id=$1', [shopId]);
    for (const r of rows) await catalog.upsertProduct(c, shopId, r, fmt, at);
    const built = await read();
    await c.query('ROLLBACK TO SAVEPOINT s86_catalog');
    const changes = [];
    for (const id of [...new Set([...live.keys(), ...built.keys()])].sort()) {
      const was = live.get(id);
      const want = built.get(id);
      if (!was) changes.push({ productId: id, kind: 'built' });
      else if (!want) changes.push({ productId: id, kind: 'dropped' });
      else {
        const fields = FIELDS.filter((f) => !same(was[f], want[f]));
        if (fields.length) changes.push({ productId: id, kind: 'fixed', fields });
      }
    }
    const count = { projection: live.size, source: built.size };
    if (!apply || !changes.length) return { ok: !changes.length, applied: false, source: fmt, count, changes };
    const market = require('./market-changes');
    const track = await market.enabled(shopId, c);
    const byId = new Map(rows.map((r) => [r.row_id, r]));
    for (const ch of changes) {
      if (ch.kind !== 'dropped') {
        await catalog.upsertProduct(c, shopId, byId.get(ch.productId), fmt, at, track);
        continue;
      }
      await c.query('DELETE FROM catalog_products WHERE shop_id=$1 AND product_id=$2', [shopId, ch.productId]);
      if (track) await market.catalogProduct(c, shopId, { row_id: ch.productId, deleted_at: at }, live.get(ch.productId).name, null, false, at);
    }
    await require('./audit').logIn(c, {
      shopId, actorType: 'system', userId: String(by || '').slice(0, 64), action: 'catalog.rebuild', targetType: 'shop', targetId: shopId,
      detail: { source: fmt, count, changes: changes.length, items: changes.slice(0, 50) },
    });
    return { ok: true, applied: true, source: fmt, count, changes };
  });
}

module.exports = { productSourceOf, refreshProducts, rebuildProducts };
