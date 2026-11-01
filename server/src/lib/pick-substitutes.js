'use strict';
/**
 * جایگزینی هنگامِ جمع‌آوری (PCK-05، PCK-06؛ سناریوی S37) — پشتِ همان پرچمِ خاموشِ
 * `shop.orders.picking` (`order-picking.flagOf`؛ درها در `routes/order-picking.js` و
 * `routes/pick-substitutes-store.js` می‌سنجند). ⛔ خاموش ⇒ هیچ ردیف و هیچ رفتارِ تازه.
 *
 *   پیشنهاد ── برای قلمِ «پیدا نشد»: دو جایگزینِ امن (`pick-core.rankSubstitutes`) از دفترِ کالای همین
 *              دکان — هم‌دسته، هم‌حلال، هم‌نوعِ واحد (PCK-06). جمع‌آور یکی را **اسکن** می‌کند (کالای
 *              بارکددار بی بارکدِ درست پذیرفته نمی‌شود، همان PCK-02) و پیشنهاد با مهلت ثبت می‌شود.
 *   قیمت ───── مشتری قیمتِ واحدِ **کمتر** را می‌پردازد (`substituteCharge`)؛ مقدار هرگز بیش از سفارش.
 *   تأیید ──── مشتری با لینکِ پیگیریِ سفارش (همان توکنِ امضاشدهٔ ویترین) یا کارمند پس از تماس/واتس‌اپ
 *              (`orders.manage`). پس از مهلت ترجیحِ ذخیره‌شدهٔ قلم (CHK-15) اعمال می‌شود: فقط «بهترین
 *              مشابه» = پذیرش؛ «جایگزین نه/بازپرداخت» اصلاً پیشنهاد نمی‌گیرد.
 *   حساب ───── پذیرش ⇒ قلمِ برگه `substituted` با مقدار و قیمتِ جایگزین؛ رد ⇒ قلم «پیدا نشد» می‌ماند و
 *              بسته‌بندی آن را حذف و کسر می‌کند (PCK-07). مبلغ را فقط سرور می‌سازد.
 *
 * ⛔ مهلتِ گذشته هر جا خوانده شود «تمام‌شده» است: بسته‌بندی و تصمیم پیش از هر کار آن را با ترجیح
 * می‌بندند (`applyTimeouts`) و نمایش‌ها `expired` و `willApply` را نشان می‌دهند — بی کارِ زمان‌بندی‌شده.
 * ⛔ ترتیبِ قفل‌ها همان جمع‌آوری: `order_picks` ⇐ قلم ⇐ پیشنهاد.
 */
const crypto = require('crypto');
const db = require('../db');
const { now, tx } = db;
const P = require('./pick-core');
const { ulid } = require('./ulid');
const { stable } = require('./idempotency');
const { toMinor } = require('./sync-v3-convert');
const { can } = require('./permissions');
const config = require('../config');
const { badRequest, notFound, conflict } = require('../middleware/errors');

const KEY_RE = /^[A-Za-z0-9_.:-]{8,128}$/;
const ID_RE = /^[0-9A-Z]{26}$/;
const PRODUCT_RE = /^[A-Za-z0-9_.:-]{1,80}$/;
const MAX_QTY_MILLI = 10_000_000;
const DEFAULT_WAIT_MIN = 10;
const MIN_WAIT_MIN = 2;
const MAX_WAIT_MIN = 60;
const SUGGESTIONS = 2;
const MAX_SCAN = 500;
const MINUTE = 60 * 1000;

const str = (v) => (typeof v === 'string' ? v.trim() : '');
const asJson = (x) => {
  if (x && typeof x === 'object') return x;
  try { return JSON.parse(x) || {}; } catch { return {}; }
};
const hashOf = (obj) => crypto.createHash('sha256').update(stable(obj)).digest('hex');

function withData(err, data) {
  err.data = data;
  return err;
}

function orderIdOf(raw) {
  const id = str(raw);
  if (!ID_RE.test(id)) throw notFound('سفارش پیدا نشد', 'order_not_found');
  return id;
}

function lineNoOf(raw) {
  const n = Number(raw);
  if (!Number.isSafeInteger(n) || n < 1 || n > 10_000) throw badRequest('شمارهٔ قلم معتبر نیست', 'bad_line');
  return n;
}

// ════════════════════════════════════════════════════════════════════
//  دفترِ کالای دکان: دسته، زیردسته، برند، واحد، قیمت، بارکد و حلال
// ════════════════════════════════════════════════════════════════════

function barcodesOfData(d) {
  const list = Array.isArray(d.barcodes) ? d.barcodes : (typeof d.barcode === 'string' ? [d.barcode] : []);
  return list.filter((b) => typeof b === 'string' && b.trim()).slice(0, 20);
}

/** قیمتِ فروشِ دفتر به یک‌صدمِ افغانی (v1 اعشاری، v3 `salePriceMinor`) — نبود ⇒ null */
function salePriceOf(d, fmt) {
  const v = fmt === 'v3' ? d.salePriceMinor : toMinor(d.salePrice);
  return Number.isSafeInteger(v) && v > 0 ? v : null;
}

function factsOfData(id, d, fmt) {
  return {
    productId: id, name: str(d.name).slice(0, 200), unit: str(d.unit).slice(0, 30),
    category: str(d.category).slice(0, 80), subcategory: str(d.subcategory).slice(0, 80), brand: str(d.brand).slice(0, 80),
    barcodes: barcodesOfData(d), priceMinor: salePriceOf(d, fmt), active: d.active !== false,
  };
}

/**
 * کالاهای همین دکان با شناسه ⇒ Map(id ⇒ facts). همان اولویتِ `order-picking.barcodesOf`: کاتالوگِ سرور،
 * سپس دفترِ همگام‌سازی v1، سپس v3. `online` ⇒ قیمتِ آنلاینِ ویترین اگر هست (سفارشِ ویترین با همان خریده شده).
 */
async function factsOf(run, shopId, ids, { online = false } = {}) {
  const want = [...new Set((ids || []).map(String).filter((x) => PRODUCT_RE.test(x)))];
  const out = new Map();
  if (!want.length) return out;
  for (const r of (await run(
    `SELECT product_id, name, unit, category, subcategory, brand, barcodes, price_minor, active FROM catalog_products
      WHERE shop_id=$1 AND product_id = ANY($2::text[]) AND NOT deleted`, [shopId, want]
  )).rows) {
    out.set(r.product_id, {
      productId: r.product_id, name: r.name, unit: r.unit, category: r.category, subcategory: r.subcategory, brand: r.brand,
      barcodes: barcodesOfData({ barcodes: asJson(r.barcodes) }),
      priceMinor: r.price_minor === null || r.price_minor === undefined ? null : Number(r.price_minor), active: r.active === true,
    });
  }
  for (const [table, fmt] of [['sync_rows', 'v1'], ['sync_v3_rows', 'v3']]) {
    const rest = want.filter((id) => !out.has(id));
    if (!rest.length) break;
    for (const r of (await run(
      `SELECT row_id, data FROM ${table} WHERE app='shop' AND account_kind='shop' AND account_id=$1
          AND table_name='products' AND row_id = ANY($2::text[]) AND deleted_at IS NULL`, [shopId, rest]
    )).rows) {
      if (!out.has(r.row_id)) out.set(r.row_id, factsOfData(r.row_id, asJson(r.data), fmt));
    }
  }
  const found = [...out.keys()];
  if (!found.length) return out;
  for (const r of (await run(
    'SELECT product_id, halal FROM pick_product_attrs WHERE shop_id=$1 AND product_id = ANY($2::text[])', [shopId, found]
  )).rows) {
    out.get(r.product_id).halal = r.halal;
  }
  if (online) {
    for (const r of (await run(
      `SELECT product_id, online_price_minor FROM storefront_products
        WHERE shop_id=$1 AND product_id = ANY($2::text[]) AND online_price_minor IS NOT NULL`, [shopId, found]
    )).rows) {
      out.get(r.product_id).priceMinor = Number(r.online_price_minor);
    }
  }
  for (const f of out.values()) f.halal = P.halalOf(f.halal);
  return out;
}

/** شناسهٔ کالاهای فعالِ هم‌دسته در همین دکان (سنجشِ دقیق با `substituteBlock`) */
async function sameCategoryIds(run, shopId, category) {
  const cat = P.normCategory(category);
  if (!cat) return [];
  const norm = (col) => `regexp_replace(lower(btrim(${col})), '\\s+', ' ', 'g')`;
  const rows = (await run(
    `SELECT product_id AS id FROM catalog_products WHERE shop_id=$1 AND NOT deleted AND active AND ${norm('category')} = $2
     UNION SELECT row_id FROM sync_rows WHERE app='shop' AND account_kind='shop' AND account_id=$1 AND table_name='products'
        AND deleted_at IS NULL AND ${norm("data->>'category'")} = $2
     UNION SELECT row_id FROM sync_v3_rows WHERE app='shop' AND account_kind='shop' AND account_id=$1 AND table_name='products'
        AND deleted_at IS NULL AND ${norm("data->>'category'")} = $2
     LIMIT ${MAX_SCAN}`, [shopId, cat]
  )).rows;
  return rows.map((r) => r.id);
}

/** کالاهایی که «پیدا نشد» ِ بازشان هنوز رسیدگی نشده — جایگزین نمی‌شوند (همان PCK-14) */
async function heldIds(run, shopId, ids) {
  if (!ids.length) return new Set();
  const rows = (await run(
    `SELECT product_id FROM pick_issues WHERE shop_id=$1 AND status='open' AND product_id = ANY($2::text[])`, [shopId, ids]
  )).rows;
  return new Set(rows.map((r) => r.product_id));
}

// ════════════════════════════════════════════════════════════════════
//  خواندن: برگه، قلم و پیشنهاد
// ════════════════════════════════════════════════════════════════════

async function orderOf(run, shopId, id, lock = false) {
  return (await run(
    `SELECT id, shop_id, status, source, customer_phone, payment_method FROM online_orders WHERE shop_id=$1 AND id=$2${lock ? ' FOR UPDATE' : ''}`,
    [shopId, id]
  )).rows[0];
}

async function pickOf(run, shopId, id, lock = false) {
  return (await run(
    `SELECT * FROM order_picks WHERE shop_id=$1 AND order_id=$2${lock ? ' FOR UPDATE' : ''}`, [shopId, id]
  )).rows[0];
}

/** ⇒ برگه یا ۴۰۴ (سفارشِ دکانِ دیگر همان «سفارش پیدا نشد») */
async function needPick(run, shopId, id, lock = false) {
  const pick = await pickOf(run, shopId, id, lock);
  if (pick) return pick;
  if (!(await orderOf(run, shopId, id))) throw notFound('سفارش پیدا نشد', 'order_not_found');
  throw notFound('جمع‌آوریِ این سفارش هنوز شروع نشده است', 'pick_not_started');
}

/** ترجیحِ جایگزینیِ همان قلم (CHK-15) — قلمِ سفارش تا بسته‌بندی دست نمی‌خورد */
async function preferenceOf(run, orderId, lineNo) {
  const r = (await run('SELECT substitution FROM online_order_lines WHERE order_id=$1 AND line_no=$2', [orderId, lineNo])).rows[0];
  const p = r ? String(r.substitution || '') : '';
  return P.PREFERENCES.includes(p) ? p : '';
}

function subView(r, at = now()) {
  const pending = r.status === 'pending';
  const expired = pending && Number(r.deadline_at) <= at;
  return {
    id: r.id,
    lineNo: Number(r.line_no),
    productId: r.product_id,
    name: r.name,
    unit: r.unit,
    qtyMilli: Number(r.qty_milli),
    shelfUnitPriceMinor: Number(r.shelf_unit_price_minor),
    chargedUnitPriceMinor: Number(r.charged_unit_price_minor),
    totalMinor: Number(r.total_minor),
    preference: r.preference,
    status: r.status,
    decidedBy: r.decided_by,
    decidedAt: r.decided_at === null || r.decided_at === undefined ? null : Number(r.decided_at),
    deadlineAt: Number(r.deadline_at),
    expired,
    //  مهلت گذشته و هنوز بسته نشده: همین اعمال می‌شود (بسته‌بندی یا تصمیمِ بعدی آن را می‌بندد)
    ...(expired ? { willApply: P.timeoutDecision(r.preference) } : {}),
    createdAt: Number(r.created_at),
  };
}

/** همهٔ پیشنهادهای یک برگه (برای برگهٔ جمع‌آور) */
async function forSheet(run, orderId, at = now()) {
  const rows = (await run(
    'SELECT * FROM order_pick_substitutions WHERE order_id=$1 ORDER BY line_no, created_at, id', [orderId]
  )).rows;
  return rows.map((r) => subView(r, at));
}

/** قلمِ اصل برای سنجش: قیمتِ واحدِ سفارش + دسته/حلالِ کالای اصل از دفتر */
function origOf(line, facts) {
  const f = facts || {};
  return {
    productId: line.product_id, name: line.name, unit: line.unit || f.unit || '',
    category: f.category || '', subcategory: f.subcategory || '', brand: f.brand || '', halal: f.halal || '',
    priceMinor: Number(line.unit_price_minor),
  };
}

/**
 * دو جایگزینِ امنِ پیشنهادی برای یک قلم (S37). قلمِ بی‌کالا یا کالای بی‌دسته ⇒ هیچ (`reason`).
 * ⇒ `{lineNo, status, preference, proposable, reason?, suggestions:[…]}`
 */
async function suggestions(shopId, orderId, rawLineNo) {
  const id = orderIdOf(orderId);
  const lineNo = lineNoOf(rawLineNo);
  const run = (t, p) => db.query(t, p);
  const order = await orderOf(run, shopId, id);
  if (!order) throw notFound('سفارش پیدا نشد', 'order_not_found');
  //  برگه هنوز نیست ⇒ از قلمِ خودِ سفارش (پیش‌نمایش؛ پیشنهاد دادن برگه می‌خواهد)
  const line = (await pickOf(run, shopId, id))
    ? (await run('SELECT * FROM order_pick_lines WHERE order_id=$1 AND line_no=$2', [id, lineNo])).rows[0]
    : (await run(
      `SELECT product_id, name, unit, qty_milli AS ordered_qty_milli, unit_price_minor, '' AS status
         FROM online_order_lines WHERE order_id=$1 AND line_no=$2`, [id, lineNo]
    )).rows[0];
  if (!line) throw notFound('این قلم در سفارش نیست', 'line_not_found');
  const preference = await preferenceOf(run, id, lineNo);
  const base = { lineNo, status: line.status, preference, proposable: P.proposable(preference), suggestions: [] };
  const own = line.product_id ? (await factsOf(run, shopId, [line.product_id])).get(line.product_id) : null;
  const orig = origOf(line, own);
  if (!P.normCategory(orig.category)) return { ...base, reason: 'category_unknown' };
  const ids = (await sameCategoryIds(run, shopId, orig.category)).filter((x) => x !== line.product_id);
  const held = await heldIds(run, shopId, ids);
  const facts = await factsOf(run, shopId, ids.filter((x) => !held.has(x)), { online: order.source === 'storefront' });
  const cands = [...facts.values()].filter((f) => f.active);
  const ranked = P.rankSubstitutes(orig, cands, SUGGESTIONS);
  const ordered = Number(line.ordered_qty_milli);
  return {
    ...base,
    suggestions: ranked.map((pid) => {
      const f = facts.get(pid);
      const ch = P.substituteCharge(orig.priceMinor, f.priceMinor, ordered);
      return {
        productId: pid, name: f.name, unit: f.unit, category: f.category, brand: f.brand, halal: f.halal,
        shelfUnitPriceMinor: f.priceMinor, chargedUnitPriceMinor: ch.chargedUnitPriceMinor, totalMinor: ch.totalMinor,
        qtyMilli: ordered, needsScan: f.barcodes.length > 0,
      };
    }),
  };
}

// ════════════════════════════════════════════════════════════════════
//  پیشنهاد (جمع‌آور)
// ════════════════════════════════════════════════════════════════════

function parsePropose(body) {
  const b = body && typeof body === 'object' ? body : {};
  const eventId = str(b.eventId);
  if (!eventId) throw badRequest('شناسهٔ یکتای رویداد لازم است', 'event_id_required');
  if (!KEY_RE.test(eventId)) throw badRequest('شناسهٔ یکتای رویداد نامعتبر است', 'bad_event_id');
  const lineNo = lineNoOf(b.lineNo);
  const productId = str(b.productId);
  if (!PRODUCT_RE.test(productId)) throw badRequest('کالای جایگزین معتبر نیست', 'bad_product');
  const barcode = b.barcode === undefined || b.barcode === null ? '' : P.normBarcode(b.barcode);
  let qtyMilli = null;
  if (b.qtyMilli !== undefined && b.qtyMilli !== null) {
    if (typeof b.qtyMilli !== 'number' || !Number.isSafeInteger(b.qtyMilli) || b.qtyMilli <= 0 || b.qtyMilli > MAX_QTY_MILLI) {
      throw badRequest('مقدار باید عددِ صحیحِ مثبت (هزارم) باشد', 'bad_qty');
    }
    qtyMilli = b.qtyMilli;
  }
  let waitMinutes = DEFAULT_WAIT_MIN;
  if (b.waitMinutes !== undefined && b.waitMinutes !== null) {
    if (typeof b.waitMinutes !== 'number' || !Number.isSafeInteger(b.waitMinutes)
      || b.waitMinutes < MIN_WAIT_MIN || b.waitMinutes > MAX_WAIT_MIN) {
      throw badRequest(`مهلت ${MIN_WAIT_MIN} تا ${MAX_WAIT_MIN} دقیقه`, 'bad_wait');
    }
    waitMinutes = b.waitMinutes;
  }
  return { eventId, lineNo, productId, barcode, qtyMilli, waitMinutes };
}

/** لینکِ پیگیریِ مشتری (همان صفحهٔ سفارشِ ویترین) و واتس‌اپ — فقط سفارشِ ویترین؛ شماره فقط با `orders.contact` */
async function customerLinks(shopId, order, actor) {
  if (!order || order.source !== 'storefront') return { link: '', whatsappUrl: '' };
  const sfRow = (await db.query('SELECT slug FROM storefronts WHERE shop_id=$1', [shopId])).rows[0];
  if (!sfRow || !sfRow.slug) return { link: '', whatsappUrl: '' };
  const sf = require('./storefront');
  const token = sf.tokenOf(shopId, order.id);
  const base = String(config.serverUrl || '').trim().replace(/\/+$/, '');
  const page = base ? `${base}/store/${encodeURIComponent(sfRow.slug)}` : `https://${sfRow.slug}.${config.storefront.domain}/`;
  const link = `${page}#/o/${encodeURIComponent(token)}`;
  const digits = String(order.customer_phone || '').replace(/[^0-9]/g, '').replace(/^0/, '93');
  //  متنِ پیام فقط خودِ لینک (بی‌زبان) — صفحه به زبانِ خودِ مشتری توضیح می‌دهد
  const whatsappUrl = digits && actor && can(actor.role, 'orders.contact')
    ? `https://wa.me/${digits}?text=${encodeURIComponent(link)}` : '';
  return { link, whatsappUrl };
}

/**
 * جمع‌آور جایگزین را (با اسکن) برای قلمِ «پیدا نشد» پیشنهاد می‌دهد. تکرارِ همان رویداد ⇒ همان پیشنهاد.
 * ⇒ `{substitution, replayed, customer:{link, whatsappUrl}}`
 */
async function propose(shopId, orderId, body, actor) {
  const id = orderIdOf(orderId);
  const ev = parsePropose(body);
  const hash = hashOf({ orderId: id, ...ev });
  const out = await tx(async (c) => {
    const run = (t, p) => c.query(t, p);
    const pick = await needPick(run, shopId, id, true);
    const prev = (await c.query(
      'SELECT * FROM order_pick_substitutions WHERE shop_id=$1 AND event_key=$2', [shopId, ev.eventId]
    )).rows[0];
    if (prev) {
      if (prev.request_hash !== hash || prev.order_id !== id) {
        throw conflict('این شناسهٔ رویداد پیش‌تر برای کارِ دیگری به کار رفته است', 'event_key_reused');
      }
      return { row: prev, replayed: true };
    }
    const order = await orderOf(run, shopId, id);
    if (pick.status !== 'picking' || order.status !== 'picking') {
      throw conflict('برگهٔ جمع‌آوریِ این سفارش بسته است', 'pick_closed');
    }
    const line = (await c.query(
      'SELECT * FROM order_pick_lines WHERE order_id=$1 AND line_no=$2 FOR UPDATE', [id, ev.lineNo]
    )).rows[0];
    if (!line) throw notFound('این قلم در سفارش نیست', 'line_not_found');
    if (line.status !== 'not_found') {
      throw withData(conflict('جایگزین فقط برای قلمِ «پیدا نشد»', 'line_not_missing'), { lineStatus: line.status });
    }
    const live = (await c.query(
      `SELECT id, status FROM order_pick_substitutions WHERE order_id=$1 AND line_no=$2 AND status IN ('pending','accepted')`,
      [id, ev.lineNo]
    )).rows[0];
    if (live) throw withData(conflict('برای این قلم پیشنهادِ باز هست', 'substitution_open'), { substitutionId: live.id, status: live.status });

    const preference = await preferenceOf(run, id, ev.lineNo);
    if (!P.proposable(preference)) {
      throw withData(conflict('مشتری جایگزین نخواسته است؛ قلم حذف و کسر می‌شود', 'no_substitutes_wanted'), { preference });
    }
    //  جایگزین با قیمتِ امروزش (سفارشِ ویترین: قیمتِ آنلاین اگر هست)؛ اصل همیشه قیمتِ ثبت‌شدهٔ سفارش
    const cand = (await factsOf(run, shopId, [ev.productId], { online: order.source === 'storefront' })).get(ev.productId);
    if (!cand || !cand.active) throw notFound('کالای جایگزین پیدا نشد', 'product_not_found');
    const orig = origOf(line, line.product_id ? (await factsOf(run, shopId, [line.product_id])).get(line.product_id) : null);
    const why = P.substituteBlock(orig, cand);
    if (why) throw withData(conflict('این جایگزین امن نیست', 'unsafe_substitute'), { reason: why });
    if ((await heldIds(run, shopId, [ev.productId])).size) {
      throw conflict('این کالا هم «پیدا نشد» دارد و هنوز رسیدگی نشده است', 'substitute_unavailable');
    }
    //  PCK-02: کالای بارکددار فقط با اسکنِ بارکدِ خودش
    if (cand.barcodes.length) {
      if (!ev.barcode) throw conflict('این کالا بارکد دارد؛ اسکن کنید', 'scan_required');
      if (!P.barcodeMatches(cand.barcodes, ev.barcode)) {
        throw withData(conflict('این بارکد مالِ کالای جایگزین نیست', 'wrong_item'), { barcode: ev.barcode });
      }
    }
    const ordered = Number(line.ordered_qty_milli);
    const qty = ev.qtyMilli === null ? ordered : ev.qtyMilli;
    if (qty > ordered) throw withData(conflict('بیش از مقدارِ سفارش', 'over_qty'), { orderedMilli: ordered });
    const ch = P.substituteCharge(orig.priceMinor, cand.priceMinor, qty);
    const at = now();
    const row = (await c.query(
      `INSERT INTO order_pick_substitutions (id, order_id, shop_id, line_no, product_id, name, unit, qty_milli,
         shelf_unit_price_minor, charged_unit_price_minor, total_minor, preference, status, deadline_at, event_key,
         request_hash, barcode, proposed_by, created_at, updated_at)
       VALUES ($1,$2,$3,$4,$5,$6,$7,$8,$9,$10,$11,$12,'pending',$13,$14,$15,$16,$17,$18,$18) RETURNING *`,
      [ulid(at), id, shopId, ev.lineNo, ev.productId, cand.name, cand.unit, qty, cand.priceMinor, ch.chargedUnitPriceMinor,
        ch.totalMinor, preference, at + ev.waitMinutes * MINUTE, ev.eventId, hash, ev.barcode, actor.id || '', at]
    )).rows[0];
    await c.query('UPDATE order_picks SET version = version + 1, updated_at=$2 WHERE order_id=$1', [id, at]);
    return { row, replayed: false };
  });
  const order = await orderOf((t, p) => db.query(t, p), shopId, id);
  return { substitution: subView(out.row), replayed: out.replayed, customer: await customerLinks(shopId, order, actor) };
}

// ════════════════════════════════════════════════════════════════════
//  تصمیم (مشتری، کارمند، مهلت)
// ════════════════════════════════════════════════════════════════════

/** پذیرش/رد در همان تراکنش. پذیرش ⇒ قلمِ برگه «جایگزین» با مقدار و قیمتِ کمتر. */
async function apply(c, sub, decision, by, actorId, at) {
  const status = decision === 'accept' ? 'accepted' : 'rejected';
  await c.query(
    `UPDATE order_pick_substitutions SET status=$2, decided_by=$3, decided_actor=$4, decided_at=$5, updated_at=$5
      WHERE id=$1`, [sub.id, status, by, actorId || '', at]
  );
  if (status === 'accepted') {
    const substitute = {
      id: sub.id, productId: sub.product_id, name: sub.name, unit: sub.unit, qtyMilli: Number(sub.qty_milli),
      shelfUnitPriceMinor: Number(sub.shelf_unit_price_minor), chargedUnitPriceMinor: Number(sub.charged_unit_price_minor),
      totalMinor: Number(sub.total_minor),
    };
    await c.query(
      `UPDATE order_pick_lines SET status='substituted', picked_qty_milli=$3, substitute=$4::jsonb, updated_at=$5
        WHERE order_id=$1 AND line_no=$2 AND status='not_found'`,
      [sub.order_id, Number(sub.line_no), Number(sub.qty_milli), JSON.stringify(substitute), at]
    );
  }
  await c.query('UPDATE order_picks SET version = version + 1, updated_at=$2 WHERE order_id=$1', [sub.order_id, at]);
  return status;
}

/** پیشنهادهای مهلت‌گذشتهٔ یک برگه ⇒ ترجیحِ مشتری (درونِ تراکنشِ فراخواننده، پس از قفلِ برگه) */
async function applyTimeouts(c, orderId, at = now()) {
  const rows = (await c.query(
    `SELECT * FROM order_pick_substitutions WHERE order_id=$1 AND status='pending' AND deadline_at <= $2
      ORDER BY line_no FOR UPDATE`, [orderId, at]
  )).rows;
  for (const r of rows) await apply(c, r, P.timeoutDecision(r.preference), 'timeout', 'system', at);
  return rows.length;
}

/** پیشنهادِ هنوز در انتظار (مهلت نگذشته) ⇒ بسته‌بندی صبر می‌کند */
async function pendingOf(c, orderId) {
  return (await c.query(
    `SELECT id, line_no, deadline_at FROM order_pick_substitutions WHERE order_id=$1 AND status='pending' ORDER BY deadline_at`,
    [orderId]
  )).rows;
}

/** «از نو» روی قلم: پیشنهادِ باز/پذیرفته پس گرفته می‌شود (سابقه می‌ماند) */
async function withdrawLine(c, orderId, lineNo, actorId, at) {
  await c.query(
    `UPDATE order_pick_substitutions SET status='withdrawn', decided_by='picker', decided_actor=$3, decided_at=$4, updated_at=$4
      WHERE order_id=$1 AND line_no=$2 AND status IN ('pending','accepted')`, [orderId, lineNo, actorId || '', at]
  );
  await c.query(`UPDATE order_pick_lines SET substitute='{}'::jsonb WHERE order_id=$1 AND line_no=$2`, [orderId, lineNo]);
}

function parseDecision(body) {
  const b = body && typeof body === 'object' ? body : {};
  const decision = str(b.decision);
  if (!['accept', 'reject'].includes(decision)) throw badRequest('تصمیم: accept یا reject', 'bad_decision');
  return decision;
}

/**
 * تصمیمِ یک پیشنهاد. `by`: `customer` (لینک) یا `staff` (پس از تماس/واتس‌اپ). تکرارِ همان تصمیم ⇒ همان
 * (`replayed`)؛ تصمیمِ دیگر پس از بسته شدن ⇒ ۴۰۹ `already_decided`؛ مهلتِ گذشته ⇒ ترجیح اعمال و ۴۰۹
 * `substitution_expired` (با نتیجه).
 */
async function decide(shopId, orderId, subId, body, { by, actorId }) {
  const id = orderIdOf(orderId);
  const sid = str(subId);
  if (!ID_RE.test(sid)) throw notFound('پیشنهاد پیدا نشد', 'substitution_not_found');
  const decision = parseDecision(body);
  const out = await tx(async (c) => {
    const run = (t, p) => c.query(t, p);
    const pick = await needPick(run, shopId, id, true);
    const sub = (await c.query(
      'SELECT * FROM order_pick_substitutions WHERE shop_id=$1 AND order_id=$2 AND id=$3 FOR UPDATE', [shopId, id, sid]
    )).rows[0];
    if (!sub) throw notFound('پیشنهاد پیدا نشد', 'substitution_not_found');
    const at = now();
    if (sub.status === 'pending' && Number(sub.deadline_at) <= at) {
      const applied = await apply(c, sub, P.timeoutDecision(sub.preference), 'timeout', 'system', at);
      return { expired: true, applied };
    }
    if (sub.status !== 'pending') {
      const same = (decision === 'accept' && sub.status === 'accepted') || (decision === 'reject' && sub.status === 'rejected');
      if (same) return { row: sub, replayed: true };
      throw withData(conflict('برای این پیشنهاد پیش‌تر تصمیم گرفته شده است', 'already_decided'), { status: sub.status, decidedBy: sub.decided_by });
    }
    if (pick.status !== 'picking') throw conflict('برگهٔ جمع‌آوریِ این سفارش بسته است', 'pick_closed');
    await apply(c, sub, decision, by, actorId, at);
    const row = (await c.query('SELECT * FROM order_pick_substitutions WHERE id=$1', [sid])).rows[0];
    return { row, replayed: false };
  });
  if (out.expired) {
    throw withData(conflict('مهلتِ این پیشنهاد گذشته است؛ ترجیحِ ذخیره‌شده اعمال شد', 'substitution_expired'), { status: out.applied });
  }
  return { substitution: subView(out.row), replayed: out.replayed };
}

// ════════════════════════════════════════════════════════════════════
//  مشتری (با توکنِ پیگیریِ ویترین)
// ════════════════════════════════════════════════════════════════════

/** نمای مشتری — نه شناسهٔ کارمند، نه بارکد، نه قیمتِ قفسهٔ کالای اصل؛ فقط آن‌چه برای تصمیم لازم است */
function customerView(r, line, at) {
  const v = subView(r, at);
  return {
    id: v.id, lineNo: v.lineNo, status: v.status, expired: v.expired, deadlineAt: v.deadlineAt,
    ...(v.expired ? { willApply: v.willApply } : {}),
    decidedBy: v.decidedBy === 'customer' || v.decidedBy === 'timeout' ? v.decidedBy : (v.decidedBy ? 'shop' : ''),
    original: line ? {
      name: line.name, unit: line.unit, qtyMilli: Number(line.ordered_qty_milli), unitPriceMinor: Number(line.unit_price_minor),
      totalMinor: Number(line.original_total_minor),
    } : null,
    substitute: {
      name: v.name, unit: v.unit, qtyMilli: v.qtyMilli, unitPriceMinor: v.chargedUnitPriceMinor, totalMinor: v.totalMinor,
    },
  };
}

/** سفارشِ همین ویترین از توکن — یا ۴۰۴ (توکنِ نادرست، سفارشِ دیگر، پرچمِ خاموش، یا هنوز جمع‌آوری نشده) */
async function orderOfToken(store, token) {
  const sf = require('./storefront');
  const id = sf.orderIdOf(store.shop_id, token);
  if (!id) throw notFound('سفارش پیدا نشد', 'order_not_found');
  const order = await orderOf((t, p) => db.query(t, p), store.shop_id, id);
  if (!order || order.source !== 'storefront') throw notFound('سفارش پیدا نشد', 'order_not_found');
  return id;
}

async function forCustomer(store, token) {
  const id = await orderOfToken(store, token);
  const at = now();
  const rows = (await db.query(
    `SELECT * FROM order_pick_substitutions WHERE shop_id=$1 AND order_id=$2 AND status <> 'withdrawn'
      ORDER BY line_no, created_at, id`, [store.shop_id, id]
  )).rows;
  if (!rows.length) return { substitutions: [] };
  const lines = new Map((await db.query(
    'SELECT line_no, name, unit, ordered_qty_milli, unit_price_minor, original_total_minor FROM order_pick_lines WHERE order_id=$1',
    [id]
  )).rows.map((l) => [Number(l.line_no), l]));
  return { substitutions: rows.map((r) => customerView(r, lines.get(Number(r.line_no)), at)) };
}

async function decideByCustomer(store, token, subId, body) {
  const id = await orderOfToken(store, token);
  const out = await decide(store.shop_id, id, subId, body, { by: 'customer', actorId: '' });
  const line = (await db.query(
    'SELECT line_no, name, unit, ordered_qty_milli, unit_price_minor, original_total_minor FROM order_pick_lines WHERE order_id=$1 AND line_no=$2',
    [id, out.substitution.lineNo]
  )).rows[0];
  const row = (await db.query('SELECT * FROM order_pick_substitutions WHERE id=$1', [out.substitution.id])).rows[0];
  return { substitution: customerView(row, line, now()), replayed: out.replayed };
}

// ════════════════════════════════════════════════════════════════════
//  ویژگیِ کالا (PCK-06): حلال
// ════════════════════════════════════════════════════════════════════

async function setAttrs(shopId, productId, body, userId) {
  const pid = str(productId);
  if (!PRODUCT_RE.test(pid)) throw notFound('کالا پیدا نشد', 'product_not_found');
  const b = body && typeof body === 'object' ? body : {};
  const raw = b.halal === null || b.halal === undefined ? '' : str(b.halal);
  if (!['', 'halal', 'non_halal'].includes(raw)) throw badRequest('حلال: halal، non_halal یا خالی', 'bad_halal');
  const exists = (await factsOf((t, p) => db.query(t, p), shopId, [pid])).has(pid);
  if (!exists) throw notFound('کالا پیدا نشد', 'product_not_found');
  const at = now();
  if (!raw) {
    await db.query('DELETE FROM pick_product_attrs WHERE shop_id=$1 AND product_id=$2', [shopId, pid]);
  } else {
    await db.query(
      `INSERT INTO pick_product_attrs (shop_id, product_id, halal, updated_by, updated_at) VALUES ($1,$2,$3,$4,$5)
       ON CONFLICT (shop_id, product_id) DO UPDATE SET halal=excluded.halal, updated_by=excluded.updated_by, updated_at=excluded.updated_at`,
      [shopId, pid, raw, userId || '', at]
    );
  }
  return { productId: pid, halal: raw };
}

module.exports = {
  DEFAULT_WAIT_MIN, MIN_WAIT_MIN, MAX_WAIT_MIN, SUGGESTIONS,
  factsOf, suggestions, propose, decide, applyTimeouts, pendingOf, withdrawLine, forSheet,
  forCustomer, decideByCustomer, setAttrs, customerLinks,
};
