'use strict';
/**
 * جای قفسهٔ کالا (PCK-01) و وظیفهٔ بررسیِ «پیدا نکردم» با برداشتنِ کالا از ویترین تا رسیدگی (PCK-14) — از
 * `lib/order-picking.js` جدا شد تا آن پرونده زیرِ سقفِ اندازه بماند (S56 جای «وزنِ بیش از حد» را آن‌جا می‌خواست).
 * همان پرچمِ خاموشِ `shop.orders.picking` (`order-picking.flagOf`)؛ درها در `routes/order-picking.js` می‌سنجند و
 * `order-picking` همین‌ها را دوباره صادر می‌کند (خواننده‌های پیشین — ویترین، افزودنِ قلم، قفسهٔ ویترین — بی تغییر).
 *
 *   جای قفسه ── هر سه خالی ⇒ پاک؛ فقط برای برگه‌های **تازه** (برگهٔ باز همان می‌ماند).
 *   وظیفه ───── «پیدا نکردم» ⇒ یک وظیفهٔ باز برای هر کالا (برخوردِ دوباره ⇒ شمار)؛ «از نو» ِ تنها برخورد کنارش
 *               می‌گذارد؛ رسیدگیِ مدیر با توضیح. کالای وظیفه‌دار تا رسیدگی در ویترین «تمام شده» است و سفارش نمی‌گیرد.
 */
const db = require('../db');
const { many, now } = db;
const P = require('./pick-core');
const { ulid } = require('./ulid');
const { badRequest, notFound } = require('../middleware/errors');
//  پرچمِ جمع‌آوری — دیر بار می‌شود (order-picking همین پرونده را بار می‌کند)
const picking = () => require('./order-picking');

const ID_RE = /^[0-9A-Z]{26}$/;
const PRODUCT_RE = /^[A-Za-z0-9_.:-]{1,80}$/;
const str = (v) => (typeof v === 'string' ? v.trim() : '');
const num = (v) => (v === null || v === undefined ? null : Number(v));
const asJson = (x) => {
  if (x && typeof x === 'object') return x;
  try { return JSON.parse(x) || {}; } catch { return {}; }
};
function text(v, max) {
  const { stripControl } = require('./validate');
  return stripControl(str(v)).slice(0, max);
}

// ════════════════════════════════════════════════════════════════════
//  جای قفسه (PCK-01)
// ════════════════════════════════════════════════════════════════════

async function productExists(shopId, productId) {
  const hit = await db.query(
    `SELECT 1 FROM catalog_products WHERE shop_id=$1 AND product_id=$2 AND NOT deleted
     UNION ALL SELECT 1 FROM sync_rows WHERE app='shop' AND account_kind='shop' AND account_id=$1
        AND table_name='products' AND row_id=$2 AND deleted_at IS NULL
     UNION ALL SELECT 1 FROM sync_v3_rows WHERE app='shop' AND account_kind='shop' AND account_id=$1
        AND table_name='products' AND row_id=$2 AND deleted_at IS NULL
     LIMIT 1`, [shopId, productId]
  );
  return hit.rows.length > 0;
}

/** جای کالا در دکان. هر سه خالی ⇒ پاک. فقط برای برگه‌های **تازه**؛ برگهٔ باز همان می‌ماند. */
async function setLocation(shopId, productId, body, userId) {
  const pid = str(productId);
  if (!PRODUCT_RE.test(pid)) throw notFound('کالا پیدا نشد', 'product_not_found');
  if (!(await productExists(shopId, pid))) throw notFound('کالا پیدا نشد', 'product_not_found');
  const b = body && typeof body === 'object' ? body : {};
  const loc = { aisle: P.locPart(text(b.aisle, 40)), shelf: P.locPart(text(b.shelf, 40)), level: P.locPart(text(b.level, 40)) };
  const at = now();
  if (!loc.aisle && !loc.shelf && !loc.level) {
    await db.query('DELETE FROM pick_locations WHERE shop_id=$1 AND product_id=$2', [shopId, pid]);
    return { productId: pid, location: null };
  }
  await db.query(
    `INSERT INTO pick_locations (shop_id, product_id, aisle, shelf, level, updated_by, updated_at)
     VALUES ($1,$2,$3,$4,$5,$6,$7)
     ON CONFLICT (shop_id, product_id) DO UPDATE SET aisle=excluded.aisle, shelf=excluded.shelf, level=excluded.level,
       updated_by=excluded.updated_by, updated_at=excluded.updated_at`,
    [shopId, pid, loc.aisle, loc.shelf, loc.level, userId || '', at]
  );
  return { productId: pid, location: loc };
}

// ════════════════════════════════════════════════════════════════════
//  وظیفهٔ بررسی و ویترین (PCK-14)
// ════════════════════════════════════════════════════════════════════

function issueView(r) {
  return {
    id: r.id, kind: r.kind, productId: r.product_id, orderId: r.order_id, lastOrderId: r.last_order_id,
    status: r.status, hits: Number(r.hits), detail: asJson(r.detail), createdAt: Number(r.created_at),
    updatedAt: Number(r.updated_at), resolvedBy: r.resolved_by, resolvedAt: num(r.resolved_at), note: r.note,
  };
}

async function issues(shopId, { status = 'open', limit } = {}) {
  if (!['open', 'resolved', 'dismissed'].includes(status)) throw badRequest('وضعیتِ وظیفه معتبر نیست', 'bad_status');
  const lim = Math.min(Math.max(Number.parseInt(limit, 10) || 50, 1), 100);
  const rows = await many(
    'SELECT * FROM pick_issues WHERE shop_id=$1 AND status=$2 ORDER BY created_at DESC, id DESC LIMIT $3',
    [shopId, status, lim]
  );
  return { issues: rows.map(issueView) };
}

/** رسیدگیِ مدیر: «رسیدگی شد» (کالا دوباره در ویترین) یا «کنار گذاشته شد» — توضیح لازم. */
async function resolveIssue(shopId, issueId, body, userId) {
  const id = str(issueId);
  if (!ID_RE.test(id)) throw notFound('وظیفهٔ باز پیدا نشد', 'issue_not_found');
  const b = body && typeof body === 'object' ? body : {};
  const action = str(b.action);
  if (!['resolved', 'dismissed'].includes(action)) throw badRequest('کار: resolved یا dismissed', 'bad_action');
  const note = text(b.note, 300);
  if (!note) throw badRequest('توضیحِ رسیدگی لازم است', 'note_required');
  const at = now();
  const r = await db.query(
    `UPDATE pick_issues SET status=$3, resolved_by=$4, resolved_at=$5, note=$6, updated_at=$5
      WHERE shop_id=$1 AND id=$2 AND status='open' RETURNING *`, [shopId, id, action, userId || '', at, note]
  );
  if (!r.rows.length) throw notFound('وظیفهٔ باز پیدا نشد', 'issue_not_found');
  return { issue: issueView(r.rows[0]) };
}

/** کالاهایی که «پیدا نشد» ِ بازشان هنوز رسیدگی نشده — پرچم خاموش ⇒ هیچ (رفتارِ پیشین). */
async function heldIn(run, shopId, productIds) {
  const ids = [...new Set((productIds || []).map(String).filter(Boolean))];
  if (!ids.length || !(await picking().flagOf(shopId, run))) return new Set();
  const rows = (await run(
    `SELECT product_id FROM pick_issues WHERE shop_id=$1 AND status='open' AND product_id = ANY($2::text[])`,
    [shopId, ids]
  )).rows;
  return new Set(rows.map((r) => r.product_id));
}

/**
 * ثبتِ سفارشِ ویترین (`storefront.pricer`): کالاهایی که سفارش نمی‌گیرند = آن‌چه ویترین خودش رد می‌کند
 * (`gone`) + کالای «پیدا نشد» ِ رسیدگی‌نشده (PCK-14؛ پرچم خاموش ⇒ فقط `gone`، رفتارِ پیشین).
 */
async function unorderable(run, shopId, ids, gone) {
  const held = await heldIn(run, shopId, ids);
  return ids.filter((id) => gone(id) || held.has(id));
}

/** نشانِ موجودیِ ویترین: کالای «پیدا نشد» تا رسیدگی «تمام شده» است (PCK-14). */
async function applyHolds(shopId, items) {
  if (!items || !items.length) return items;
  const held = await heldIn((t, p) => db.query(t, p), shopId, items.map((p) => p.id));
  if (!held.size) return items;
  for (const p of items) {
    if (held.has(p.id)) p.stock = { status: 'out_of_stock', reason: 'pick_review', checkedAt: now() };
  }
  return items;
}

/** «پیدا نکردم» ⇒ وظیفهٔ باز برای مدیر (یکی برای هر کالا) — PCK-14 */
async function openIssue(c, shopId, productId, orderId, detail, at) {
  await c.query(
    `INSERT INTO pick_issues (id, shop_id, kind, product_id, order_id, last_order_id, detail, created_at, updated_at)
     VALUES ($1,$2,'not_found',$3,$4,$4,$5::jsonb,$6,$6)
     ON CONFLICT (shop_id, product_id) WHERE status = 'open'
     DO UPDATE SET hits = pick_issues.hits + 1, last_order_id = excluded.last_order_id,
                   detail = excluded.detail, updated_at = excluded.updated_at`,
    [ulid(at), shopId, productId, orderId, JSON.stringify(detail), at]
  );
}

/** «از نو» روی قلمِ پیدانشده: اگر وظیفه فقط از همین سفارش بود، خودِ سیستم کنارش می‌گذارد */
async function undoIssue(c, shopId, productId, orderId, at) {
  const iss = (await c.query(
    `SELECT id, hits, order_id FROM pick_issues WHERE shop_id=$1 AND product_id=$2 AND status='open' FOR UPDATE`,
    [shopId, productId]
  )).rows[0];
  if (!iss) return;
  if (Number(iss.hits) <= 1 && iss.order_id === orderId) {
    await c.query(
      `UPDATE pick_issues SET status='dismissed', resolved_by='system', resolved_at=$2, updated_at=$2,
              note='جمع‌آور «پیدا نکردم» را برگرداند' WHERE id=$1`, [iss.id, at]
    );
  } else {
    await c.query('UPDATE pick_issues SET hits = GREATEST(hits - 1, 1), updated_at=$2 WHERE id=$1', [iss.id, at]);
  }
}

module.exports = { setLocation, issues, resolveIssue, heldIn, unorderable, applyHolds, openIssue, undoIssue };
