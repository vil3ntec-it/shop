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
 *   سفارش ──── رزرو درونِ همان تراکنشِ ساختِ سفارش (`onOrderCreated`)؛ گذارِ
 *              وضعیت رزرو را تمدید/آزاد/قطعی می‌کند (`onOrderStatus`).
 *   نمایش ──── «در قفسه» / «در انبار، آماده‌سازی لازم» / «تمام شده» /
 *              «در حال بررسی» / «قابلِ سفارش نیست» — موجودیِ نامعلوم یا کهنه
 *              هرگز «موجود» نیست (INV-02، INV-14).
 *
 * ⛔ پرچمِ `shop.catalog` پیش‌فرض خاموش؛ خاموش ⇒ هیچ ردیفی، همان رفتارِ پیشین.
 */
const crypto = require('crypto');
const { query, one, now, tx } = require('../db');
const core = require('./stock-core');
const stock = require('./stock');
const { toMinor, toMilli } = require('./sync-v3-convert');
const { stable } = require('./idempotency');
const { badRequest, notFound, conflict, forbidden } = require('../middleware/errors');

const TABLES = Object.freeze({ v1: 'sync_rows', v3: 'sync_v3_rows' });
const DEVICE_TABLES = ['products', 'warehouseEntries', 'sales', 'saleItems'];
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

async function upsertProduct(c, shopId, row, fmt, at) {
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
      d.active !== false, row.deleted_at !== null && row.deleted_at !== undefined,
      fmt, num(row.updated_seq), at]
  );
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
  const fmt = settings.syncSource === 'v3' ? 'v3' : 'v1';
  const T = TABLES[fmt];
  const rd = reader(fmt);
  const st = (await c.query('SELECT source, watermark FROM catalog_sync_state WHERE shop_id=$1', [shopId])).rows[0];
  //  منبع عوض شد ⇒ از اول خوانده می‌شود؛ سهم‌ها کلیدِ مستقل از قالب دارند، پس دوباره کم نمی‌شود
  let wm = !st || st.source !== fmt || full ? 0 : num(st.watermark);
  const ACC = "app='shop' AND account_kind='shop' AND account_id=$1";

  let products = 0;
  let moves = 0;
  const touched = new Set();
  for (;;) {
    const page = (await c.query(
      `SELECT table_name, row_id, data, deleted_at, updated_seq FROM ${T}
        WHERE ${ACC} AND table_name = ANY($2::text[]) AND updated_seq > $3
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
    for (const r of rows) {
      if (r.table_name === 'products') { await upsertProduct(c, shopId, r, fmt, at); products += 1; }
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
    wm = num(rows[rows.length - 1].updated_seq);
    if (page.length < PAGE) break;
  }

  await c.query(
    `INSERT INTO catalog_sync_state (shop_id, source, watermark, last_run_at) VALUES ($1,$2,$3,$4)
     ON CONFLICT (shop_id) DO UPDATE SET source=excluded.source, watermark=excluded.watermark, last_run_at=excluded.last_run_at`,
    [shopId, fmt, wm, at]
  );
  if (touched.size) await stock.checkProducts(c, shopId, [...touched].sort(), settings, at);
  return { products, moves, source: fmt };
}

/**
 * نخستین خواندنِ دفترِ دستگاه (یا پس از عوض شدنِ منبعِ v1/v3) **در تراکنشِ خودش**، پیش از
 * تراکنشِ سفارش (QA #263): خواندنِ کاملِ دفتر دیگر درونِ تراکنشِ ساختِ سفارشِ مشتری و زیرِ
 * قفلِ `orders:<دکان>` نمی‌ماند. کاتالوگِ از پیش خوانده‌شده ⇒ هیچ (همان خواندنِ تفاوتِ کوچک
 * درونِ تراکنشِ سفارش می‌ماند تا رزرو با موجودیِ تازه سنجیده شود). پرچم خاموش ⇒ هیچ.
 */
async function prepare(shopId, { at = now() } = {}) {
  if (!(await stock.flagOf(shopId))) return null;
  const settings = await stock.settingsOf(shopId);
  const fmt = settings.syncSource === 'v3' ? 'v3' : 'v1';
  const st = await one('SELECT source FROM catalog_sync_state WHERE shop_id=$1', [shopId]);
  if (st && st.source === fmt) return null;
  return tx(async (c) => ingestTx(c, shopId, at, await stock.settingsOf(shopId, c)));
}

/** همگام‌سازیِ کاتالوگ در تراکنشِ خودش؛ `maxAgeMs` ⇒ اگر تازه خوانده شده، نه. */
async function refresh(shopId, { full = false, maxAgeMs = 0, at = now() } = {}) {
  if (maxAgeMs > 0 && !full) {
    const st = await one('SELECT last_run_at FROM catalog_sync_state WHERE shop_id=$1', [shopId]);
    if (st && at - num(st.last_run_at) < maxAgeMs) return { skipped: true };
  }
  return tx(async (c) => ingestTx(c, shopId, at, await stock.settingsOf(shopId, c), { full }));
}

// ════════════════════════════════════════════════════════════════════
//  تازگی و وضعیتِ نمایش (INV-02، INV-03، INV-14)
// ════════════════════════════════════════════════════════════════════

/**
 * موجودی فقط وقتی «تازه» است که کاتالوگ دست‌کم یک بار خوانده شده و یکی از
 * دستگاه‌های دکان در مهلتِ `staleAfterSec` دیده شده و هیچ دستگاهِ تازه‌دیده‌ای
 * صفِ نفرستاده ندارد.
 */
async function freshness(shopId, settings, at) {
  const since = at - settings.staleAfterSec * 1000;
  const v1 = await one(
    `SELECT COALESCE(MAX(last_seen_at),0)::bigint AS seen,
            COALESCE(MAX(CASE WHEN last_seen_at >= $2 THEN queued_count ELSE 0 END),0)::int AS queued
       FROM sync_devices WHERE app='shop' AND account_kind='shop' AND account_id=$1`, [shopId, since]
  );
  const v3 = await one(
    `SELECT COALESCE(MAX(last_seen_at),0)::bigint AS seen,
            COALESCE(MAX(CASE WHEN last_seen_at >= $2 THEN queued ELSE 0 END),0)::int AS queued
       FROM sync_v3_devices WHERE app='shop' AND account_kind='shop' AND account_id=$1`, [shopId, since]
  );
  const st = await one('SELECT last_run_at FROM catalog_sync_state WHERE shop_id=$1', [shopId]);
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
                            AND ph.product_id=cp.product_id AND ph.data IS NOT NULL) AS has_photo
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
  const [ps, lvs, held] = await Promise.all([productsOf(shopId, ids), stock.levelsOf(null, shopId, ids), stock.heldOf(null, shopId, ids)]);
  const out = new Map();
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
  const [ps, lvs, held] = await Promise.all([productsOf(shopId, page), stock.levelsOf(null, shopId, page), stock.heldOf(null, shopId, page)]);
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
  const held = (await stock.heldOf(null, shopId, [id])).get(id) || 0;
  const reservations = (await query(
    `SELECT id, order_id, line_no, qty_milli, expires_at, created_at FROM stock_reservations
      WHERE shop_id=$1 AND product_id=$2 AND status='held' ORDER BY created_at, id`, [shopId, id]
  )).rows.map((r) => ({
    id: r.id, orderId: r.order_id, lineNo: r.line_no, qtyMilli: num(r.qty_milli),
    expiresAt: r.expires_at === null ? null : num(r.expires_at), createdAt: num(r.created_at),
  }));
  const lows = {};
  for (const loc of stock.LOCATIONS) if (lv && lv[loc] && lv[loc].low !== null) lows[loc] = lv[loc].low;
  return { product: productView(p, lv, held, settings, fr), reservations, lowByLocation: lows, freshness: fr };
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
async function moves(shopId, { productId, cursor, limit } = {}) {
  const lim = Math.min(Math.max(Number(limit) || 50, 1), MAX_LIST);
  const params = [shopId];
  const where = ['shop_id=$1'];
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

async function reservationsOf(shopId, orderId) {
  return (await query(
    `SELECT id, line_no, product_id, qty_milli, status, expires_at, created_at, closed_at, close_reason
       FROM stock_reservations WHERE shop_id=$1 AND order_id=$2 ORDER BY created_at, line_no, id`,
    [shopId, String(orderId || '')]
  )).rows.map((r) => ({
    id: r.id, lineNo: r.line_no, productId: r.product_id, qtyMilli: num(r.qty_milli), status: r.status,
    expiresAt: r.expires_at === null ? null : num(r.expires_at), createdAt: num(r.created_at),
    closedAt: r.closed_at === null ? null : num(r.closed_at), closeReason: r.close_reason,
  }));
}

// ════════════════════════════════════════════════════════════════════
//  وظیفه‌ها (INV-19)
// ════════════════════════════════════════════════════════════════════

async function tasks(shopId, { status = 'open', limit } = {}) {
  if (!['open', 'resolved', 'dismissed'].includes(status)) throw badRequest('وضعیتِ وظیفه معتبر نیست', 'bad_status');
  const lim = Math.min(Math.max(Number(limit) || 50, 1), MAX_LIST);
  return (await query(
    `SELECT * FROM stock_tasks WHERE shop_id=$1 AND status=$2 ORDER BY created_at DESC, id DESC LIMIT $3`,
    [shopId, status, lim]
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

// ════════════════════════════════════════════════════════════════════
//  حرکتِ دستیِ کارمند (INV-08، INV-12، INV-15)
// ════════════════════════════════════════════════════════════════════

//  نوعِ درخواست ⇒ اجازه و مکان‌های مجاز
const MANUAL = Object.freeze({
  transfer: { permission: 'stock.move', reason: false },
  damage: { permission: 'stock.adjust', reason: true },
  writeoff: { permission: 'stock.adjust', reason: true },
  return: { permission: 'stock.adjust', reason: true },
  adjust: { permission: 'stock.adjust', reason: true },
  count: { permission: 'stock.adjust', reason: true },
});
const TRANSFER_LOCS = ['shop_floor', 'warehouse', 'in_transit'];

function qtyOf(v, field = 'مقدار') {
  if (typeof v !== 'number' || !Number.isSafeInteger(v) || v <= 0 || v > MAX_QTY_MILLI) {
    throw badRequest(`${field} باید عددِ صحیحِ مثبت (هزارم) باشد`, 'bad_qty');
  }
  return v;
}

function locOf(v, allowed, def) {
  const l = str(v) || def;
  if (!allowed.includes(l)) throw badRequest('مکانِ موجودی معتبر نیست', 'bad_location');
  return l;
}

/** درخواست ⇒ حرکت‌ها (پیش از تراکنش؛ مقدارِ «شمارش» درون تراکنش حساب می‌شود). */
function parseManual(body) {
  const b = body && typeof body === 'object' ? body : {};
  const kind = str(b.kind);
  const spec = MANUAL[kind];
  if (!spec) throw badRequest('نوعِ حرکت معتبر نیست', 'bad_kind');
  const productId = str(b.productId);
  if (!productId) throw badRequest('کالا لازم است', 'product_required');
  const reason = text(b.reason, 80);
  if (spec.reason && !reason) throw badRequest('دلیل لازم است', 'reason_required');
  const note = text(b.note, 300);
  const out = { kind, spec, productId, reason, note };
  if (kind === 'transfer') {
    out.from = locOf(b.from, TRANSFER_LOCS);
    out.to = locOf(b.to, TRANSFER_LOCS);
    if (out.from === out.to) throw badRequest('مبدأ و مقصد یکی است', 'bad_location');
    out.qtyMilli = qtyOf(b.qtyMilli);
  } else if (kind === 'damage') {
    out.from = locOf(b.from, TRANSFER_LOCS, 'shop_floor');
    out.qtyMilli = qtyOf(b.qtyMilli);
  } else if (kind === 'writeoff') {
    out.location = locOf(b.location, stock.LOCATIONS, 'damaged');
    out.qtyMilli = qtyOf(b.qtyMilli);
  } else if (kind === 'return') {
    out.location = locOf(b.location, stock.LOCATIONS, 'shop_floor');
    out.qtyMilli = qtyOf(b.qtyMilli);
    out.orderId = text(b.orderId, 80);
  } else if (kind === 'adjust') {
    out.location = locOf(b.location, stock.LOCATIONS);
    const q = b.qtyMilli;
    if (typeof q !== 'number' || !Number.isSafeInteger(q) || q === 0 || Math.abs(q) > MAX_QTY_MILLI) {
      throw badRequest('مقدارِ اصلاح باید عددِ صحیحِ غیرصفر (هزارم) باشد', 'bad_qty');
    }
    out.qtyMilli = q;
  } else {
    out.location = locOf(b.location, stock.LOCATIONS);
    const q = b.countedMilli;
    if (typeof q !== 'number' || !Number.isSafeInteger(q) || q < 0 || q > MAX_QTY_MILLI) {
      throw badRequest('مقدارِ شمرده‌شده باید عددِ صحیحِ نامنفی (هزارم) باشد', 'bad_qty');
    }
    out.countedMilli = q;
  }
  return out;
}

function hashOf(body) {
  return crypto.createHash('sha256').update(stable(body || {})).digest('hex').slice(0, 32);
}

//  هر درخواستِ دستی حداکثر دو حرکت دارد (انتقال = خروج + ورود) ⇒ کلیدهای دقیق، نه LIKE
const MAX_PLAN = 2;
const staffKeys = (k) => Array.from({ length: MAX_PLAN }, (_, i) => `staff:${k}:${i + 1}`);

function keyOf(raw) {
  const key = str(raw);
  if (!key) throw badRequest('کلیدِ یکتاییِ حرکت لازم است', 'idempotency_key_required');
  if (!KEY_RE.test(key)) throw badRequest('کلیدِ یکتایی نامعتبر است', 'bad_idempotency_key');
  return key;
}

/**
 * حرکتِ دستی با کلیدِ یکتایی؛ تکرار ⇒ همان نتیجه، بی اثرِ دوم.
 * اجازه را فراخواننده با `can(role, MANUAL[kind].permission)` می‌سنجد.
 * ⇒ { moves, replayed, unchanged }
 */
async function manualMove(shopId, { key, body, actor }) {
  const k = keyOf(key);
  const m = parseManual(body);
  const hash = hashOf(body);
  const at = now();
  return tx(async (c) => {
    await stock.lock(c, shopId);
    const prev = (await c.query(
      `SELECT * FROM stock_moves WHERE shop_id=$1 AND source_key = ANY($2::text[]) ORDER BY source_key`, [shopId, staffKeys(k)]
    )).rows;
    if (prev.length) {
      if (prev[0].ref_id !== hash) throw conflict('این کلیدِ یکتایی پیش‌تر برای حرکتِ دیگری به کار رفته است', 'idempotency_key_reused');
      return { moves: prev.map(moveView), replayed: true, unchanged: false };
    }
    const settings = await stock.settingsOf(shopId, c);
    //  دفترِ دستگاه پیش از اصلاح: شمارش و انتقال روی موجودیِ امروز حساب شوند
    await ingestTx(c, shopId, at, settings);
    const p = (await c.query('SELECT product_id FROM catalog_products WHERE shop_id=$1 AND product_id=$2', [shopId, m.productId])).rows[0];
    if (!p) throw notFound('کالا پیدا نشد', 'product_not_found');

    const base = {
      productId: m.productId, mode: 'local', source: 'staff', refType: 'staff', refId: hash,
      actorType: 'user', actorId: actor.id, note: m.note, at,
    };
    const plan = [];
    if (m.kind === 'transfer' || m.kind === 'damage') {
      const to = m.kind === 'damage' ? 'damaged' : m.to;
      const reason = m.reason || (m.kind === 'damage' ? 'damage' : 'transfer');
      plan.push({ ...base, location: m.from, type: 'transfer_out', qtyMilli: m.qtyMilli, reason });
      plan.push({ ...base, location: to, type: 'transfer_in', qtyMilli: m.qtyMilli, reason });
    } else if (m.kind === 'writeoff') {
      plan.push({ ...base, location: m.location, type: 'damage', qtyMilli: m.qtyMilli, reason: m.reason });
    } else if (m.kind === 'return') {
      plan.push({ ...base, location: m.location, type: 'customer_return', qtyMilli: m.qtyMilli, reason: m.reason,
        note: m.orderId ? `order:${m.orderId}${m.note ? ` ${m.note}` : ''}` : m.note });
    } else if (m.kind === 'adjust') {
      plan.push({ ...base, location: m.location, type: 'adjustment', qtyMilli: m.qtyMilli, reason: m.reason });
    } else {
      const lv = (await stock.levelsOf(c, shopId, [m.productId])).get(m.productId);
      const diff = m.countedMilli - stock.onHandAt(lv, m.location);
      if (diff === 0) return { moves: [], replayed: false, unchanged: true };
      plan.push({ ...base, location: m.location, type: 'count_adjust', qtyMilli: diff, reason: m.reason });
    }
    for (let i = 0; i < plan.length; i++) {
      await stock.postMove(c, shopId, { ...plan[i], sourceKey: `staff:${k}:${i + 1}` }, at);
    }
    await stock.checkProducts(c, shopId, [m.productId], settings, at);
    const rows = (await c.query(
      `SELECT * FROM stock_moves WHERE shop_id=$1 AND source_key = ANY($2::text[]) ORDER BY source_key`, [shopId, staffKeys(k)]
    )).rows;
    return { moves: rows.map(moveView), replayed: false, unchanged: false };
  });
}

/** آستانهٔ «موجودیِ کم» برای یک مکان (§۳۲.۲ بندِ ۷)؛ null ⇒ برداشتنِ آستانه. */
async function setLow(shopId, productId, body) {
  const b = body && typeof body === 'object' ? body : {};
  const location = locOf(b.location, stock.LOCATIONS);
  let low = null;
  if (b.lowMilli !== null && b.lowMilli !== undefined) {
    if (typeof b.lowMilli !== 'number' || !Number.isSafeInteger(b.lowMilli) || b.lowMilli < 0 || b.lowMilli > MAX_QTY_MILLI) {
      throw badRequest('آستانه باید عددِ صحیحِ نامنفی (هزارم) باشد', 'bad_qty');
    }
    low = b.lowMilli;
  }
  const id = String(productId || '');
  const p = await one('SELECT 1 FROM catalog_products WHERE shop_id=$1 AND product_id=$2', [shopId, id]);
  if (!p) throw notFound('کالا پیدا نشد', 'product_not_found');
  await query(
    `INSERT INTO stock_levels (shop_id, product_id, branch_id, location, on_hand_milli, low_milli, updated_at)
     VALUES ($1,$2,'',$3,0,$4,$5)
     ON CONFLICT (shop_id, product_id, branch_id, location) DO UPDATE SET low_milli=excluded.low_milli`,
    [shopId, id, location, low, now()]
  );
  return { productId: id, location, lowMilli: low };
}

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
 * درونِ تراکنشِ `orders.create`، پس از نوشتنِ قلم‌ها: پرچم روشن ⇒ دفترِ
 * دستگاه خوانده و قلم‌های کالادار رزرو می‌شوند؛ کمبود ⇒ خطا و **کلِ سفارش**
 * برمی‌گردد. پرچم خاموش ⇒ null، بی هیچ نوشتنی.
 */
async function onOrderCreated(c, shopId, { orderId, lines, at, actor, isTest = false }) {
  //  سفارشِ آزمایشیِ کارمند موجودیِ واقعی را نه رزرو می‌کند نه کم (QA #263)
  if (isTest) return null;
  const withProduct = (lines || []).filter((l) => str(l.productId));
  if (!withProduct.length) return null;
  if (!(await stock.flagOf(shopId, c))) return null;
  await stock.lock(c, shopId);
  const settings = await stock.settingsOf(shopId, c);
  await ingestTx(c, shopId, at, settings);
  await stock.expireDue(c, shopId, at);
  const { lines: sl, names } = await toStockLines(c, shopId, withProduct);
  return stock.reserve(c, shopId, {
    orderId, lines: sl, at, expiresAt: at + settings.reserveTtlSec * 1000, actor, settings, names,
  });
}

const RELEASE_ON = new Set(['rejected', 'auto_rejected', 'cancelled_by_customer', 'cancelled', 'restocked']);
const COMMIT_ON = new Set(['picked_up', 'delivered']);

/**
 * درونِ تراکنشِ گذارِ سفارش، پس از عوض شدنِ وضعیت:
 *   accepted ⇒ رزرو تا پایان می‌ماند؛ رزروِ منقضی/نبوده ⇒ دوباره رزرو (کمبود ⇒ ۴۰۹)
 *   رد/لغو/بازگشت به قفسه ⇒ آزادسازی · تحویل ⇒ کسرِ قطعی
 */
async function onOrderStatus(c, shopId, { id, to }, actor, at) {
  if (to !== 'accepted' && !RELEASE_ON.has(to) && !COMMIT_ON.has(to)) return null;
  const has = (await c.query(
    `SELECT 1 FROM stock_reservations WHERE shop_id=$1 AND order_id=$2 AND status='held' LIMIT 1`, [shopId, id]
  )).rows.length > 0;
  if (!has && to !== 'accepted') return null;
  const test = (await c.query('SELECT is_test FROM online_orders WHERE shop_id=$1 AND id=$2', [shopId, id])).rows[0];
  if (test && test.is_test === true) {
    //  سفارشِ آزمایشی: هرگز رزرو یا کسرِ قطعی (فروشِ واقعی در دفترِ موجودی) نه.
    //  رزروِ مانده از پیش از این اصلاح در پایانِ کار آزاد می‌شود، کسر نمی‌شود.
    if (!has || to === 'accepted') return null;
    await stock.lock(c, shopId);
    return { released: await stock.release(c, shopId, id, { reason: 'test_order', actor, at }) };
  }
  const flag = await stock.flagOf(shopId, c);
  if (!has && !flag) return null;
  await stock.lock(c, shopId);
  const settings = await stock.settingsOf(shopId, c);
  if (RELEASE_ON.has(to)) return { released: await stock.release(c, shopId, id, { reason: to, actor, at }) };
  if (COMMIT_ON.has(to)) return { committed: await stock.commit(c, shopId, id, { actor, at, settings }) };

  await stock.expireDue(c, shopId, at);
  //  CHK-18: رزروی که فروشِ حضوریِ هم‌زمان پشتوانه‌اش را برده (INV-06) با «قبول» بی‌مهلت
  //  نمی‌شود؛ ۴۰۹ و گذار برمی‌گردد — دکان رد می‌کند یا موجودی را درست می‌کند. اول فروش‌های
  //  تازه‌رسیدهٔ دستگاه‌ها خوانده می‌شوند، وگرنه داوری با موجودیِ کهنه است.
  const held = await c.query(
    `SELECT 1 FROM stock_reservations WHERE shop_id=$1 AND order_id=$2 AND status='held' LIMIT 1`, [shopId, id]
  );
  if (held.rows.length && flag) await ingestTx(c, shopId, at, settings);
  const short = held.rows.length ? await stock.shortOf(c, shopId, id, settings) : [];
  if (short.length) {
    const names = new Map((await c.query(
      'SELECT product_id, name, unit FROM catalog_products WHERE shop_id=$1 AND product_id = ANY($2::text[])',
      [shopId, short.map((x) => x.productId)]
    )).rows.map((r) => [r.product_id, r]));
    throw stock.outOfStock(short.map((x) => ({ ...x, name: names.get(x.productId)?.name || '', unit: names.get(x.productId)?.unit || '' })));
  }
  if (await stock.extend(c, shopId, id)) return { extended: true };
  if (!flag) return null;
  const lines = (await c.query(
    `SELECT line_no AS "lineNo", product_id AS "productId", unit, qty_milli AS "qtyMilli"
       FROM online_order_lines WHERE order_id=$1 AND shop_id=$2 AND product_id <> '' ORDER BY line_no`, [id, shopId]
  )).rows;
  if (!lines.length) return null;
  await ingestTx(c, shopId, at, settings);
  const { lines: sl, names } = await toStockLines(c, shopId, lines);
  return { reserved: await stock.reserve(c, shopId, { orderId: id, lines: sl, at, expiresAt: null, actor, settings, names }) };
}

/** برای مسیرهای HTTP: پرچم لازم است. */
async function requireEnabled(shopId) {
  if (!(await stock.flagOf(shopId))) throw forbidden('کاتالوگ و موجودیِ سرور برای این دکان روشن نیست', 'catalog_disabled');
}

module.exports = {
  MANUAL, TRANSFER_LOCS,
  ingestTx, refresh, prepare, freshness, badgeOf, publicStock,
  list, detail, moves, reservationsOf, tasks, resolveTask,
  parseManual, manualMove, setLow,
  toStockLines, onOrderCreated, onOrderStatus, requireEnabled,
};
