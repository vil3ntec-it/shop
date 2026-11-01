'use strict';
/**
 * پنلِ مرکزی ۴.۶ — مدیریتِ بازار، **فقط سمتِ مدیریت** (سمتِ خریدار و برنامهٔ خرید بیرون است، §۴۰.۵).
 * فقط خواندن، از جدول‌های واقعی؛ هیچ عددی ساخته نمی‌شود و نبودنِ چیزی در مدل «نیست» گفته می‌شود، نه صفر.
 *
 * ⛔ مدلِ واقعی (docs/MARKETPLACE-CONTRACT-fa.md):
 *   • «فروشنده» = همان دکانِ VILL3N (`shops`) با ویترین (`storefronts`) و ردیفِ فهرستِ بازار (`market_listings`).
 *     **فروشندهٔ مستقلِ بیرون از VILL3N در مدل نیست** — `sellerKinds.independent = null` و دلیلش.
 *   • «تأییدِ فروشنده» در مدل نیست: بودن در فهرست را **خودِ صاحبِ دکان** روشن می‌کند (`listed`) و سامانه با
 *     دروازه‌ها می‌بندد: پرچمِ `market.directory` (همه و هر دکان)، حالتِ «غیرفعالِ سرویس» (`shop_holds`)، و
 *     جریمهٔ «تعلیقِ ویترین» (`trust_sanctions`). همین دروازه‌ها یکی‌یکی گزارش می‌شوند (`gates`).
 *   • سفارشِ بازار = سفارشِ آنلاینِ ویترین (`online_orders.source = 'storefront'`)، **تک‌دکانی**. سبدِ چنددکانی،
 *     کارمزد و کمیسیون فقط با ستون‌های مهاجرتِ ۱۳۰ (PRِ باز #432) معنا دارند؛ تا نیامده‌اند «نیست» گفته می‌شود.
 *   • پولِ مشتری فقط نقد هنگامِ تحویل است (D-08) و به سامانه نمی‌رسد ⇒ «تسویه با فروشنده» در مدل نیست؛ تسویهٔ
 *     موجود «پیک ⇐ دکان» است (`courier_settlements`) و در ۴.۷ دیده می‌شود.
 * ⛔ هیچ دادهٔ شخصیِ مشتری (نام، شماره، نشانی) در هیچ پاسخی نیست؛ فقط شمارهٔ سفارش، وضعیت و مبلغ.
 */
const { one, many, now } = require('../db');
const market = require('./market');
const orders = require('./orders');
const storefront = require('./storefront');
const watch = require('./order-watch');
const { badRequest } = require('../middleware/errors');

const DAY = 24 * 60 * 60 * 1000;
const N = (x) => (x === null || x === undefined ? null : Number(x));
const MARKET_SERVICES = Object.freeze(['market.directory', 'shop.storefront', 'shop.orders', 'shop.delivery']);
const MAX_WINDOW_DAYS = 400;

const STATUS_GROUPS = Object.freeze({
  awaiting: ['new'],
  in_progress: ['accepted', 'picking', 'packed', 'ready_for_pickup', 'out_for_delivery', 'delivery_failed', 'retry_scheduled'],
  completed: ['delivered', 'picked_up'],
  cancelled: ['rejected', 'auto_rejected', 'cancelled_by_customer', 'cancelled'],
  returns: ['returned_to_store', 'restocked', 'return_requested', 'refunded_partial', 'refunded_full'],
});

function intIn(v, { min, max, def, field }) {
  if (v === undefined || v === null || v === '') return def;
  const n = Number(v);
  if (!Number.isSafeInteger(n) || n < min || n > max) throw badRequest(`${field} در بازهٔ مجاز نیست`, 'bad_query');
  return n;
}
function idOrEmpty(v) {
  const s = typeof v === 'string' ? v.trim() : '';
  if (!s) return '';
  if (s.length > 80 || !/^[A-Za-z0-9_.:-]+$/.test(s)) throw badRequest('شناسهٔ دکان معتبر نیست', 'bad_query');
  return s;
}

/** بازهٔ [from, to) ِ زمانِ سرور؛ پیش‌فرض `days` روزِ گذشته؛ حداکثر ۴۰۰ روز. */
function windowOf(q = {}, at = now(), defDays = 30) {
  const to = q.to === undefined || q.to === '' ? at : Number(q.to);
  const from = q.from === undefined || q.from === '' ? to - intIn(q.days, { min: 1, max: MAX_WINDOW_DAYS, def: defDays, field: 'روز' }) * DAY : Number(q.from);
  if (!Number.isSafeInteger(from) || !Number.isSafeInteger(to) || to <= from || to - from > MAX_WINDOW_DAYS * DAY) {
    throw badRequest('بازهٔ زمانی معتبر نیست', 'bad_range');
  }
  return { from, to };
}

/** ستون‌های موجودِ یک جدول (برای چیزهایی که فقط با مهاجرتِ PRهای باز می‌آیند) — بی حدس. */
async function columnsOf(table, names) {
  const rows = await many(
    `SELECT column_name FROM information_schema.columns
      WHERE table_schema = current_schema() AND table_name = $1 AND column_name = ANY($2::text[])`,
    [table, names]
  );
  const have = new Set(rows.map((r) => r.column_name));
  return Object.fromEntries(names.map((n) => [n, have.has(n)]));
}

const decode = (t) => { try { return JSON.parse(t); } catch { return null; } };

/* ───────────────────────────── نمای کلی ───────────────────────────── */

async function overview(q = {}) {
  const at = now();
  const w = windowOf(q, at, 30);
  const [directory, sellers, flagRows, holds, sanctions, cases, orderRows, cols] = await Promise.all([
    market.directoryOn(),
    one(
      `WITH ids AS (SELECT shop_id FROM storefronts UNION SELECT shop_id FROM market_listings)
       SELECT COUNT(*)::int AS total,
              COUNT(*) FILTER (WHERE sf.enabled)::int AS storefront_on,
              COUNT(*) FILTER (WHERE COALESCE(ml.listed, false))::int AS listed,
              COUNT(*) FILTER (WHERE sf.enabled AND s.status = 'active' AND COALESCE(ml.listed, false))::int AS listed_open,
              COUNT(*) FILTER (WHERE s.status <> 'active')::int AS shop_inactive
         FROM ids JOIN shops s ON s.id = ids.shop_id
         LEFT JOIN storefronts sf ON sf.shop_id = s.id
         LEFT JOIN market_listings ml ON ml.shop_id = s.id`
    ),
    many(`SELECT scope, value FROM live_config WHERE key = 'market.directory' AND scope <> 'all'`),
    many(
      `SELECT service, COUNT(*)::int AS n FROM shop_holds
        WHERE kind = 'service_disabled' AND lifted_at IS NULL AND service = ANY($1::text[]) GROUP BY service`,
      [MARKET_SERVICES]
    ),
    many(`SELECT kind, COUNT(*)::int AS n FROM trust_sanctions WHERE lifted_at IS NULL GROUP BY kind`),
    many(
      `SELECT kind, subject, status, COUNT(*)::int AS n FROM trust_cases
        WHERE status IN ('open','in_review','appealed') GROUP BY kind, subject, status`
    ),
    many(
      `SELECT status, currency, COUNT(*)::int AS n, COALESCE(SUM(total_minor), 0) AS total
         FROM online_orders WHERE source = 'storefront' AND is_test = false AND created_at >= $1 AND created_at < $2
        GROUP BY status, currency`,
      [w.from, w.to]
    ),
    columnsOf('online_orders', ['market_order_group_id', 'platform_fee_minor', 'commission_minor', 'channel']),
  ]);

  const groups = Object.fromEntries(Object.keys(STATUS_GROUPS).map((g) => [g, 0]));
  const completedTotals = {};
  let ordersTotal = 0;
  for (const r of orderRows) {
    const g = Object.keys(STATUS_GROUPS).find((k) => STATUS_GROUPS[k].includes(r.status));
    if (g) groups[g] += Number(r.n);
    ordersTotal += Number(r.n);
    if (g === 'completed') completedTotals[r.currency] = (completedTotals[r.currency] || 0) + Number(r.total);
  }
  const perShopFlag = { on: 0, off: 0 };
  for (const r of flagRows) (decode(r.value) === true ? perShopFlag.on++ : perShopFlag.off++);
  const pending = { productReports: 0, complaints: 0, appealed: 0 };
  for (const r of cases) {
    if (r.status === 'appealed') pending.appealed += Number(r.n);
    else if (r.kind === 'report') pending.productReports += Number(r.n);
    else pending.complaints += Number(r.n);
  }
  const problems = await problemCounts(w);

  return {
    serverTime: at,
    window: w,
    directory: { on: directory, flag: market.FLAG_KEY, perShop: perShopFlag },
    sellers: {
      total: sellers.total, storefrontOn: sellers.storefront_on, listed: sellers.listed, listedOpen: sellers.listed_open,
      shopInactive: sellers.shop_inactive,
    },
    sellerKinds: {
      vill3nShops: sellers.total,
      independent: null,
      note: 'not_in_model',
    },
    holds: Object.fromEntries(MARKET_SERVICES.map((s) => [s, Number(holds.find((h) => h.service === s)?.n || 0)])),
    sanctions: Object.fromEntries(['warning', 'hide_listing', 'suspend_storefront'].map((k) => [k, Number(sanctions.find((x) => x.kind === k)?.n || 0)])),
    trust: pending,
    orders: { total: ordersTotal, groups, completedTotalMinor: completedTotals, problems },
    multiSeller: cols.market_order_group_id
      ? { inModel: true, groups: Number((await one(
        `SELECT COUNT(DISTINCT market_order_group_id)::int AS n FROM online_orders
          WHERE market_order_group_id IS NOT NULL AND created_at >= $1 AND created_at < $2`, [w.from, w.to])).n) }
      : { inModel: false, groups: null, needs: 'online_orders.market_order_group_id (مهاجرتِ ۱۳۰، PRِ #432)' },
    commission: { inModel: cols.platform_fee_minor && cols.commission_minor },
  };
}

/* ───────────────────────────── فروشنده‌ها ───────────────────────────── */

async function sellers(q = {}) {
  const limit = intIn(q.limit, { min: 1, max: 50, def: 25, field: 'تعداد' });
  const offset = intIn(q.offset, { min: 0, max: 100_000, def: 0, field: 'از' });
  const needle = typeof q.q === 'string' ? q.q.trim().slice(0, 60) : '';
  const state = typeof q.state === 'string' ? q.state : '';
  if (state && !['listed', 'unlisted', 'held', 'sanctioned', 'inactive'].includes(state)) throw badRequest('پالایش درست نیست', 'bad_query');
  const args = [];
  const where = [];
  if (needle) {
    args.push(`%${needle.toLowerCase().replace(/[%_\\]/g, (c) => `\\${c}`)}%`);
    where.push(`(lower(s.name) LIKE $${args.length} OR lower(COALESCE(sf.slug, '')) LIKE $${args.length} OR s.id = $${args.length + 1})`);
    args.push(needle);
  }
  if (state === 'listed') where.push('COALESCE(ml.listed, false)');
  if (state === 'unlisted') where.push('NOT COALESCE(ml.listed, false)');
  if (state === 'inactive') where.push(`s.status <> 'active'`);
  if (state === 'held') {
    where.push(`EXISTS (SELECT 1 FROM shop_holds h WHERE h.shop_id = s.id AND h.lifted_at IS NULL
                         AND (h.kind <> 'service_disabled' OR h.service = ANY('{market.directory,shop.storefront,shop.orders}'::text[])))`);
  }
  if (state === 'sanctioned') where.push('EXISTS (SELECT 1 FROM trust_sanctions t WHERE t.shop_id = s.id AND t.lifted_at IS NULL)');
  const from = `(SELECT shop_id FROM storefronts UNION SELECT shop_id FROM market_listings) ids
                JOIN shops s ON s.id = ids.shop_id
                LEFT JOIN storefronts sf ON sf.shop_id = s.id
                LEFT JOIN market_listings ml ON ml.shop_id = s.id
                ${where.length ? `WHERE ${where.join(' AND ')}` : ''}`;
  const at = now();
  const since = at - 30 * DAY;
  const [total, rows, directory] = await Promise.all([
    one(`SELECT COUNT(*)::int AS n FROM ${from}`, args),
    many(
      `SELECT s.id, s.name, s.status, s.created_at, sf.slug, sf.enabled AS sf_enabled, ml.listed, ml.category,
              ml.show_deals, ml.updated_at AS listing_updated_at,
              (SELECT MAX(d.last_seen_at) FROM sync_devices d
                WHERE d.app = 'shop' AND d.account_kind = 'shop' AND d.account_id = s.id) AS last_seen_at,
              (SELECT COUNT(*)::int FROM storefront_products p WHERE p.shop_id = s.id AND p.published) AS published,
              (SELECT COALESCE(json_agg(json_build_object('id', h.id, 'kind', h.kind, 'service', h.service,
                                                          'placedAt', h.placed_at) ORDER BY h.placed_at), '[]'::json)
                 FROM shop_holds h WHERE h.shop_id = s.id AND h.lifted_at IS NULL) AS holds,
              (SELECT COALESCE(json_agg(json_build_object('id', t.id, 'kind', t.kind, 'productId', t.product_id,
                                                          'caseId', t.case_id, 'at', t.created_at) ORDER BY t.created_at), '[]'::json)
                 FROM trust_sanctions t WHERE t.shop_id = s.id AND t.lifted_at IS NULL) AS sanctions,
              (SELECT COUNT(*)::int FROM trust_cases c WHERE c.shop_id = s.id AND c.status IN ('open','in_review','appealed')) AS cases_pending,
              (SELECT COUNT(*)::int FROM online_orders o WHERE o.shop_id = s.id AND o.source = 'storefront'
                  AND o.is_test = false AND o.created_at >= $${args.length + 3}) AS orders_30d,
              (SELECT COUNT(*) FILTER (WHERE c.active)::int FROM shop_couriers c WHERE c.shop_id = s.id) AS couriers_active,
              (SELECT COUNT(*)::int FROM shop_couriers c WHERE c.shop_id = s.id) AS couriers_total
         FROM ${from}
        ORDER BY s.name, s.id
        LIMIT $${args.length + 1} OFFSET $${args.length + 2}`,
      [...args, limit, offset, since]
    ),
    market.directoryOn(),
  ]);
  const json = (v) => (typeof v === 'string' ? decode(v) || [] : Array.isArray(v) ? v : []);
  const out = [];
  for (const r of rows) {
    //  دروازه‌های واقعیِ همان سرور — همان توابعی که ویترین و بازار با آن باز/بسته می‌شوند
    const [sfState, marketFlag, deliveryFlag] = await Promise.all([
      storefront.stateOf(r.id), orders.flagOf(r.id, 'market.directory'), orders.flagOf(r.id, 'shop.delivery'),
    ]);
    const holdsOpen = json(r.holds);
    const sanctionsOpen = json(r.sanctions);
    const gates = {
      directory,
      marketFlag,
      storefrontFlag: sfState.storefrontFlag,
      ordersFlag: sfState.ordersFlag,
      entitled: sfState.entitled,
      storefrontEnabled: r.sf_enabled === true,
      shopActive: r.status === 'active',
      listed: r.listed === true,
      notSuspended: !sanctionsOpen.some((x) => x.kind === 'suspend_storefront'),
      hasPublished: Number(r.published) > 0,
    };
    out.push({
      shopId: r.id, name: r.name, shopStatus: r.status, createdAt: N(r.created_at), kind: 'vill3n_shop',
      slug: r.slug || null, category: r.category || null, showDeals: r.show_deals === true,
      listingUpdatedAt: N(r.listing_updated_at), lastSeenAt: N(r.last_seen_at) || null,
      publishedProducts: Number(r.published),
      gates, blockedBy: Object.entries(gates).filter(([, v]) => v !== true).map(([k]) => k),
      holds: holdsOpen.map((h) => ({ ...h, placedAt: N(h.placedAt) })),
      sanctions: sanctionsOpen.map((x) => ({ ...x, at: N(x.at) })),
      casesPending: Number(r.cases_pending),
      orders30d: Number(r.orders_30d),
      delivery: { flag: deliveryFlag, couriersActive: Number(r.couriers_active), couriersTotal: Number(r.couriers_total) },
    });
  }
  return { serverTime: at, total: total.n, limit, offset, sellers: out, categories: market.CATEGORIES || [] };
}

/* ───────────────────────────── سفارش‌های بازار ───────────────────────────── */

const ORDER_COLS = `o.id, o.shop_id, s.name AS shop_name, o.order_no, o.source, o.status, o.fulfillment, o.payment_method,
  o.payment_status, o.cod_status, o.currency, o.total_minor, o.items_count, o.created_at, o.updated_at`;
/** آخرین مأموریتِ هر سفارش (بیشترین `attempt_no`) با پیشوندِ `a_` — همان شکلی که `order-watch.classify` می‌خواهد. */
const LAST_MISSION = `LEFT JOIN LATERAL (
    SELECT a.id AS a_id, a.status AS a_status, a.mode AS a_mode, a.courier_id AS a_courier_id, a.updated_at AS a_updated_at,
           a.cash_review AS a_cash_review, a.cash_collected_minor AS a_cash_collected_minor,
           a.expected_collect_minor AS a_expected_collect_minor, a.recall_outcome AS a_recall_outcome,
           a.recall_requested_at AS a_recall_requested_at, a.attempt_no AS a_attempt_no
      FROM delivery_assignments a WHERE a.order_id = o.id ORDER BY a.attempt_no DESC LIMIT 1) la ON true`;

function orderView(r, cls, extra = {}) {
  return {
    id: r.id, shopId: r.shop_id, shopName: r.shop_name || '', orderNo: N(r.order_no), source: r.source, status: r.status,
    fulfillment: r.fulfillment, payment: { method: r.payment_method, status: r.payment_status, cod: r.cod_status || null },
    currency: r.currency, totalMinor: N(r.total_minor), itemsCount: N(r.items_count),
    createdAt: N(r.created_at), updatedAt: N(r.updated_at),
    mission: r.a_id ? {
      id: r.a_id, status: r.a_status, mode: r.a_mode, attemptNo: N(r.a_attempt_no), updatedAt: N(r.a_updated_at),
      cashReview: r.a_cash_review === true, recall: r.a_recall_outcome || null,
    } : null,
    issues: cls,
    ...extra,
  };
}

async function thresholds() {
  const row = await one('SELECT value, updated_at, updated_by FROM live_config WHERE key = $1 AND scope = $2', [watch.KEY, 'all']);
  const t = watch.normalizeThresholds(row ? decode(row.value) : null);
  return { ...t, key: watch.KEY, updatedAt: row ? N(row.updated_at) : null, updatedBy: row ? row.updated_by || null : null };
}

/**
 * سفارش‌های بازار. بی `problem`: صفحه‌بندیِ سمتِ سرور روی همهٔ سفارش‌های ویترین در بازه (و پالایشِ وضعیت/دکان).
 * با `problem=1`: فقط سفارش‌هایی که دستِ‌کم یک کدِ `order-watch` یا شکایتِ باز دارند، در همان بازه.
 */
async function orderList(q = {}) {
  const at = now();
  const w = windowOf(q, at, 30);
  const limit = intIn(q.limit, { min: 1, max: 200, def: 50, field: 'تعداد' });
  const offset = intIn(q.offset, { min: 0, max: 100_000, def: 0, field: 'از' });
  const shopId = idOrEmpty(q.shopId);
  const group = typeof q.group === 'string' ? q.group : '';
  if (group && !STATUS_GROUPS[group]) throw badRequest('گروهِ وضعیت درست نیست', 'bad_query');
  const problem = q.problem === '1' || q.problem === 'true';
  const args = [w.from, w.to];
  const where = [`o.source = 'storefront'`, 'o.is_test = false', 'o.created_at >= $1', 'o.created_at < $2'];
  if (shopId) { args.push(shopId); where.push(`o.shop_id = $${args.length}`); }
  if (group) { args.push(STATUS_GROUPS[group]); where.push(`o.status = ANY($${args.length}::text[])`); }
  const th = (await thresholds()).values;
  const complaintsOf = async (ids) => (ids.length ? new Map((await many(
    `SELECT order_id, COUNT(*)::int AS n FROM trust_cases
      WHERE order_id = ANY($1::text[]) AND status IN ('open','in_review','appealed') GROUP BY order_id`, [ids]
  )).map((r) => [r.order_id, Number(r.n)])) : new Map());

  if (!problem) {
    const [total, rows] = await Promise.all([
      one(`SELECT COUNT(*)::int AS n FROM online_orders o WHERE ${where.join(' AND ')}`, args),
      many(
        `SELECT ${ORDER_COLS}, la.* FROM online_orders o JOIN shops s ON s.id = o.shop_id ${LAST_MISSION}
          WHERE ${where.join(' AND ')} ORDER BY o.created_at DESC, o.id DESC
          LIMIT $${args.length + 1} OFFSET $${args.length + 2}`,
        [...args, limit, offset]
      ),
    ]);
    const open = await complaintsOf(rows.map((r) => r.id));
    return {
      serverTime: at, window: w, total: total.n, limit, offset, thresholds: th,
      orders: rows.map((r) => orderView(r, watch.classify(r, th, at), { complaintsOpen: open.get(r.id) || 0 })),
    };
  }
  //  «مشکل‌دار» در حافظه پالایش می‌شود (کدها در JS حساب می‌شوند) ⇒ سقفِ روشن و `truncated` ِ راست
  const CAP = 5000;
  const rows = await many(
    `SELECT ${ORDER_COLS}, la.* FROM online_orders o JOIN shops s ON s.id = o.shop_id ${LAST_MISSION}
      WHERE ${where.join(' AND ')} ORDER BY o.created_at DESC, o.id DESC LIMIT ${CAP + 1}`,
    args
  );
  const truncated = rows.length > CAP;
  const scan = rows.slice(0, CAP);
  const open = await complaintsOf(scan.map((r) => r.id));
  const hits = [];
  for (const r of scan) {
    const cls = watch.classify(r, th, at);
    const c = open.get(r.id) || 0;
    if (c || cls.stuck.length || cls.mismatch.length || cls.discrepancy.length) hits.push(orderView(r, cls, { complaintsOpen: c }));
  }
  return {
    serverTime: at, window: w, total: hits.length, limit, offset, thresholds: th, scanned: scan.length, truncated,
    orders: hits.slice(offset, offset + limit),
  };
}

/** شمارِ سفارش‌های مشکل‌دارِ بازار در بازه (برای نمای کلی) — همان قاعدهٔ `orderList({problem})`. */
async function problemCounts(w) {
  const at = now();
  const th = (await thresholds()).values;
  const rows = await many(
    `SELECT ${ORDER_COLS}, la.* FROM online_orders o JOIN shops s ON s.id = o.shop_id ${LAST_MISSION}
      WHERE o.source = 'storefront' AND o.is_test = false AND o.created_at >= $1 AND o.created_at < $2
      ORDER BY o.created_at DESC LIMIT 5001`,
    [w.from, w.to]
  );
  const out = { stuck: 0, mismatch: 0, discrepancy: 0, truncated: rows.length > 5000 };
  for (const r of rows.slice(0, 5000)) {
    const c = watch.classify(r, th, at);
    if (c.stuck.length) out.stuck++;
    if (c.mismatch.length) out.mismatch++;
    if (c.discrepancy.length) out.discrepancy++;
  }
  return out;
}

/* ───────────────────────────── کارمزد و کمیسیون ───────────────────────────── */

const FEE_KEYS = ['market.platform_fee_bps', 'market.platform_fee_fixed_minor', 'market.commission_bps', 'market.commission_fixed_minor'];

/**
 * ⛔ صادقانه: امروز (shop ِ main) هیچ ستونِ کارمزد/کمیسیونی روی سفارش نیست — `lib/market-fees.js` و مهاجرتِ ۱۳۰
 * در PRِ باز #432 است، و تصمیمِ صاحب محصول (۱۴۰۵/۰۸/۰۸) «فعلاً هیچ کارمزدی گرفته نمی‌شود». پس بی ستون:
 * `inModel: false` و دلیل، نه «۰ افغانی» ِ ساختگی. با ستون: جمعِ واقعیِ همان ستون‌ها در بازه.
 */
async function commission(q = {}) {
  const at = now();
  const w = windowOf(q, at, 30);
  const cols = await columnsOf('online_orders', ['platform_fee_minor', 'commission_minor', 'channel']);
  const keys = await many(
    'SELECT scope, key, value, updated_at FROM live_config WHERE key = ANY($1::text[]) ORDER BY key, scope', [FEE_KEYS]
  );
  const policy = keys.map((r) => ({ scope: r.scope, key: r.key, value: decode(r.value), updatedAt: N(r.updated_at) }));
  const decision = { by: 'owner', date: '1405/08/08', text: 'فعلاً سامانه از سفارشِ آنلاین هیچ کارمزدی نمی‌گیرد' };
  if (!cols.platform_fee_minor || !cols.commission_minor) {
    return {
      serverTime: at, window: w, inModel: false, reason: 'not_in_schema',
      needs: 'online_orders.platform_fee_minor / commission_minor + lib/market-fees.js (مهاجرتِ ۱۳۰، PRِ #432)',
      decision, policy, policyApplied: false,
    };
  }
  const rows = await many(
    `SELECT currency, COUNT(*)::int AS n, COALESCE(SUM(platform_fee_minor),0) AS fee, COALESCE(SUM(commission_minor),0) AS com,
            COUNT(*) FILTER (WHERE platform_fee_minor > 0 OR commission_minor > 0)::int AS charged
       FROM online_orders WHERE source = 'storefront' AND is_test = false AND created_at >= $1 AND created_at < $2
      GROUP BY currency ORDER BY currency`,
    [w.from, w.to]
  );
  return {
    serverTime: at, window: w, inModel: true, decision, policy, policyApplied: true,
    totals: rows.map((r) => ({ currency: r.currency, orders: r.n, charged: r.charged, platformFeeMinor: Number(r.fee), commissionMinor: Number(r.com) })),
  };
}

/* ───────────────────────────── سابقهٔ کارهای حساس ───────────────────────────── */

/** کارهای حساسِ بازار در سابقهٔ سرورِ حساب (`audit_logs`) — دیدن‌ها (…_viewed) نه. */
const HISTORY_ACTIONS = Object.freeze([
  'admin.trust_case_assigned', 'admin.trust_case_decided', 'admin.trust_appeal_decided', 'admin.trust_sanction_lifted',
  'admin.shop_hold_placed', 'admin.shop_hold_lifted', 'admin.flag_set', 'admin.flag_removed',
  'admin.pump_live_config_set', 'admin.pump_live_config_removed', 'market.listing',
]);
const MARKET_KEYS_RE = '(^|:)(market\\.|shop\\.storefront|shop\\.orders|shop\\.delivery|console\\.delivery_thresholds)';

async function history(q = {}) {
  const limit = intIn(q.limit, { min: 1, max: 200, def: 50, field: 'تعداد' });
  const before = intIn(q.before, { min: 0, max: Number.MAX_SAFE_INTEGER, def: 0, field: 'پیش از' });
  const shopId = idOrEmpty(q.shopId);
  const args = [HISTORY_ACTIONS, MARKET_KEYS_RE, [...MARKET_SERVICES]];
  const where = [
    'action = ANY($1::text[])',
    `(action NOT IN ('admin.flag_set','admin.flag_removed','admin.pump_live_config_set','admin.pump_live_config_removed')
      OR target_id ~ $2)`,
    `(action NOT IN ('admin.shop_hold_placed','admin.shop_hold_lifted')
      OR detail->>'kind' <> 'service_disabled' OR detail->>'service' = ANY($3::text[]))`,
  ];
  if (shopId) { args.push(shopId); where.push(`shop_id = $${args.length}`); }
  if (before) { args.push(before); where.push(`created_at < $${args.length}`); }
  args.push(limit);
  const rows = await many(
    `SELECT id, shop_id, actor_type, user_id, action, target_type, target_id, detail, created_at FROM audit_logs
      WHERE ${where.join(' AND ')} ORDER BY created_at DESC, id DESC LIMIT $${args.length}`,
    args
  );
  return {
    serverTime: now(),
    entries: rows.map((r) => ({
      id: r.id, shopId: r.shop_id || null, actorType: r.actor_type, userId: r.user_id || null, action: r.action,
      targetType: r.target_type || null, targetId: r.target_id || null,
      detail: typeof r.detail === 'string' ? decode(r.detail) : r.detail, at: N(r.created_at),
    })),
  };
}

module.exports = {
  STATUS_GROUPS, MARKET_SERVICES, HISTORY_ACTIONS, FEE_KEYS, windowOf, columnsOf, thresholds,
  overview, sellers, orderList, commission, history,
};
