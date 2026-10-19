'use strict';
/**
 * بازارچهٔ عمومی — فهرستِ دکان‌ها و جست‌وجوی کالا در همهٔ ویترین‌ها
 * (WEB-01/02/05/06، §۲۳.۲ «دیدن بی حساب»، §۳۱.۱، §۳۲.۳).
 *
 *   روشن ── پرچمِ `market.directory` با scope = `all` (پیش‌فرض خاموش). خاموش ⇒
 *           همهٔ `/api/market/*` ۴۰۴ و ویترینِ هر دکان همان رفتارِ امروز را دارد.
 *   دکان ── فقط دکانی که همین حالا ویترینش با `storefront.open` باز می‌شود (همان
 *           دروازه: دو پرچم، اشتراک، «روشن» ِ خودِ دکان، دکانِ فعال — و هر قفلِ
 *           بعدیِ همان تابع) + پرچمِ `market.directory` ِ خودش (مقدارِ دکان بر
 *           `all` جلو می‌افتد ⇒ مدیر می‌تواند یک دکان را بیرون نگه دارد) + `listed`
 *           ِ خودِ صاحب دکان (`market_listings`؛ نبودِ ردیف = در فهرست) + دست‌کم
 *           یک کالای منتشرشده. «روشن»، «فعال» و `listed` در هر درخواست زنده
 *           خوانده می‌شوند؛ نتیجهٔ پرچم/اشتراک فقط `config.market.gateCacheMs` در
 *           حافظه می‌ماند.
 *   کالا ── فقط از راهِ `PRODUCT_FROM` و `LIVE_PRODUCT` ِ خودِ ویترین (منتشرشده،
 *           حذف‌نشده، فعال، قیمت‌دار) — همان چیزی که ثبتِ سفارش می‌پذیرد.
 *   حریم ── هیچ شناسهٔ داخلی، شماره، ایمیل، نامِ صاحب یا مختصات در پاسخ نیست؛
 *           «نزدیکِ من» فقط فاصلهٔ گردشده می‌دهد و مکانِ مشتری ذخیره یا لاگ نمی‌شود
 *           (لاگِ درخواست بی querystring است).
 */
const { one, many, now } = require('../db');
const config = require('../config');
const sf = require('./storefront');
const orders = require('./orders');
const { stripControl } = require('./validate');
const { badRequest, notFound, forbidden, ApiError } = require('../middleware/errors');

const FLAG_KEY = 'market.directory';

/** دسته‌های ثابتِ دکان — کلیدِ انگلیسی در داده، برچسبِ دری در نما. */
const CATEGORIES = Object.freeze([
  { key: 'grocery', label: 'سوپرمارکت و خواربار' },
  { key: 'bakery', label: 'نانوایی' },
  { key: 'produce', label: 'میوه و سبزی' },
  { key: 'butcher', label: 'قصابی و مرغ' },
  { key: 'dairy', label: 'لبنیات' },
  { key: 'sweets', label: 'شیرینی و خشکبار' },
  { key: 'beauty', label: 'بهداشتی و آرایشی' },
  { key: 'household', label: 'لوازمِ خانه' },
  { key: 'stationery', label: 'قرطاسیه و کتاب' },
  { key: 'clothing', label: 'پوشاک' },
  { key: 'electronics', label: 'موبایل و برقی' },
  { key: 'other', label: 'دیگر' },
]);
const CATEGORY_KEYS = new Set(CATEGORIES.map((c) => c.key));

const MAX_CANDIDATES = 2000;
const PAGE = 24;
const MAX_PAGE = 60;
const MAX_PRODUCTS = 40;
const GATE_BATCH = 16;

const str = (v) => (typeof v === 'string' ? v.trim() : '');
const text = (v, max) => stripControl(str(v)).slice(0, max);
const obj = (v) => (v && typeof v === 'object' && !Array.isArray(v) ? v : {});

// ════════════════════════════════════════════════════════════════════
//  روشن یا خاموش
// ════════════════════════════════════════════════════════════════════

async function directoryOn() {
  const r = await one("SELECT value FROM live_config WHERE key = $1 AND scope = 'all'", [FLAG_KEY]);
  if (!r) return false;
  try { return JSON.parse(r.value) === true; } catch { return false; }
}

/** «بازارچه نیست» — همان جوابِ نشانیِ ناموجود. */
async function requireOn() {
  if (!(await directoryOn())) throw notFound('بازارچه هنوز باز نیست', 'market_not_found');
}

// ── دروازهٔ هر دکان (با حافظهٔ کوتاه) ──────────────────────────────

const gates = new Map();

async function storeOpen(slug) {
  try {
    await sf.open(slug);
    return true;
  } catch (err) {
    if (err && err.status === 404) return false;
    throw err;
  }
}

/** ویترینِ باز + پرچمِ بازارچهٔ همین دکان. */
async function gateOf(row) {
  const ttl = config.market.gateCacheMs;
  const t = Date.now();
  const hit = gates.get(row.shop_id);
  if (ttl > 0 && hit && hit.slug === row.slug && t - hit.at < ttl) return hit.ok;
  const ok = (await storeOpen(row.slug)) && (await orders.flagOf(row.shop_id, FLAG_KEY));
  if (gates.size > 10_000) gates.clear();
  gates.set(row.shop_id, { ok, at: t, slug: row.slug });
  return ok;
}

function clearCache() { gates.clear(); }

async function filterAsync(list, fn) {
  const out = [];
  for (let i = 0; i < list.length; i += GATE_BATCH) {
    const part = list.slice(i, i + GATE_BATCH);
    const oks = await Promise.all(part.map(fn));
    part.forEach((x, j) => { if (oks[j]) out.push(x); });
  }
  return out;
}

// ════════════════════════════════════════════════════════════════════
//  فهرستِ دکان‌ها
// ════════════════════════════════════════════════════════════════════

/** دکان‌های در فهرست، با وضعیت و شمارِ کالا — «روشن» و «فعال» زنده خوانده می‌شوند. */
async function eligible(at = now()) {
  const rows = await many(
    `SELECT sf.*, s.status AS shop_status, ml.category AS m_category, ml.area AS m_area,
            ml.geo_lat AS m_lat, ml.geo_lng AS m_lng
       FROM storefronts sf
       JOIN shops s ON s.id = sf.shop_id
       LEFT JOIN market_listings ml ON ml.shop_id = sf.shop_id
      WHERE sf.enabled AND s.status = 'active' AND COALESCE(ml.listed, true)
      ORDER BY sf.slug LIMIT ${MAX_CANDIDATES}`
  );
  const open = await filterAsync(rows, gateOf);
  if (!open.length) return [];
  const ids = open.map((r) => r.shop_id);
  const [counts, seen] = await Promise.all([
    many(
      `SELECT sp.shop_id, COUNT(*)::int AS n FROM ${sf.PRODUCT_FROM}
        WHERE sp.shop_id = ANY($1::text[]) AND sp.published AND ${sf.LIVE_PRODUCT}
          AND COALESCE(r.data->>'name', '') <> ''
        GROUP BY sp.shop_id`,
      [ids]
    ),
    many(
      `SELECT account_id, MAX(last_seen_at) AS t FROM sync_devices
        WHERE app = 'shop' AND account_kind = 'shop' AND account_id = ANY($1::text[])
        GROUP BY account_id`,
      [ids]
    ),
  ]);
  const n = new Map(counts.map((r) => [r.shop_id, Number(r.n)]));
  const last = new Map(seen.map((r) => [r.account_id, r.t === null || r.t === undefined ? 0 : Number(r.t)]));
  return open
    .filter((r) => (n.get(r.shop_id) || 0) > 0)
    .map((r) => ({ row: r, productCount: n.get(r.shop_id), status: sf.presenceOf(r, at, last.get(r.shop_id) || 0) }));
}

const ACCEPT_RANK = { accepting: 0, delayed: 1, later: 2 };

/** مختصاتِ درشت (یک‌صدمِ درجه) از ورودی — یا null. */
function geoOf(lat, lng) {
  if ((lat === undefined || lat === '' || lat === null) && (lng === undefined || lng === '' || lng === null)) return null;
  const a = Number(lat);
  const b = Number(lng);
  if (!Number.isFinite(a) || !Number.isFinite(b) || Math.abs(a) > 90 || Math.abs(b) > 180) {
    throw badRequest('موقعیت معتبر نیست', 'bad_location');
  }
  return { lat: Math.round(a * 100), lng: Math.round(b * 100) };
}

/** فاصلهٔ دو نقطهٔ درشت به کیلومتر، گردشده به نیم کیلومتر (کمینه ۰٫۵). */
function distanceKm(a, b) {
  const rad = (x) => (x / 100) * Math.PI / 180;
  const dLat = rad(b.lat - a.lat);
  const dLng = rad(b.lng - a.lng);
  const h = Math.sin(dLat / 2) ** 2 + Math.cos(rad(a.lat)) * Math.cos(rad(b.lat)) * Math.sin(dLng / 2) ** 2;
  const km = 2 * 6371 * Math.asin(Math.min(1, Math.sqrt(h)));
  return Math.max(0.5, Math.round(km * 2) / 2);
}

function categoryOf(key) {
  return CATEGORIES.find((c) => c.key === key) || null;
}

/** کارتِ عمومیِ دکان — بی شناسهٔ داخلی، شماره، صاحب یا مختصات. */
function cardOf(e, near) {
  const r = e.row;
  const s = e.status;
  const cat = categoryOf(r.m_category);
  const hasGeo = near && r.m_lat !== null && r.m_lat !== undefined && r.m_lng !== null && r.m_lng !== undefined;
  return {
    slug: r.slug,
    title: r.title,
    tagline: r.tagline,
    accent: r.accent,
    category: cat ? cat.key : '',
    categoryLabel: cat ? cat.label : '',
    area: r.m_area || '',
    status: {
      badge: s.badge, orderAcceptance: s.orderAcceptance, connection: s.connection,
      lastConfirmedAt: s.lastConfirmedAt, declaredUntil: s.declaredUntil, timezone: s.timezone,
      localTime: s.localTime, hoursSet: s.hoursSet, holiday: s.holiday, today: s.today, notice: s.notice,
    },
    fulfillment: { delivery: r.accept_delivery === true, pickup: r.accept_pickup === true },
    deliveryFeeMinor: Number(r.delivery_fee_minor),
    minOrderMinor: Number(r.min_order_minor),
    currency: 'AFN',
    productCount: e.productCount,
    distanceKm: hasGeo ? distanceKm(near, { lat: Number(r.m_lat), lng: Number(r.m_lng) }) : null,
  };
}

function rank(near) {
  return (a, b) => {
    const x = (ACCEPT_RANK[a.status.orderAcceptance] ?? 3) - (ACCEPT_RANK[b.status.orderAcceptance] ?? 3);
    if (x) return x;
    if (near) {
      const da = a.distanceKm === null ? Infinity : a.distanceKm;
      const db = b.distanceKm === null ? Infinity : b.distanceKm;
      if (da !== db) return da - db;
    }
    return String(a.title).localeCompare(String(b.title), 'fa') || String(a.slug).localeCompare(String(b.slug));
  };
}

function matchesShop(c, needle) {
  if (!needle) return true;
  return [c.title, c.tagline, c.area, c.categoryLabel].some((v) => sf.normalize(v).includes(needle));
}

/** نمای اولِ بازارچه: دسته‌ها با شمارِ دکانِ هر کدام. */
async function overview(at = now()) {
  const list = await eligible(at);
  const count = new Map();
  for (const e of list) {
    const k = categoryOf(e.row.m_category) ? e.row.m_category : '';
    count.set(k, (count.get(k) || 0) + 1);
  }
  return {
    shopCount: list.length,
    categories: CATEGORIES.map((c) => ({ ...c, count: count.get(c.key) || 0 })).filter((c) => c.count > 0),
    verification: { available: await sf.verificationAvailable() },
  };
}

/** فهرستِ دکان‌ها: جست‌وجو در نام/شعار/ناحیه، دسته، و «نزدیکِ من» اختیاری. */
async function shops({ q, category, lat, lng, limit, offset } = {}, at = now()) {
  const lim = Math.min(MAX_PAGE, Math.max(1, Number.parseInt(limit, 10) || PAGE));
  const off = Math.max(0, Math.min(MAX_CANDIDATES, Number.parseInt(offset, 10) || 0));
  const near = geoOf(lat, lng);
  const cat = str(category);
  if (cat && !CATEGORY_KEYS.has(cat)) throw badRequest('دستهٔ دکان معتبر نیست', 'bad_category');
  const needle = sf.normalize(text(q, 60));
  const cards = (await eligible(at))
    .map((e) => cardOf(e, near))
    .filter((c) => (!cat || c.category === cat) && matchesShop(c, needle))
    .sort(rank(near));
  const page = cards.slice(off, off + lim);
  return { shops: page, total: cards.length, nextOffset: off + lim < cards.length ? off + lim : null };
}

/**
 * یک جعبهٔ جست‌وجو برای دکان و کالا (WEB-05). اگر عبارت همان نامِ یک دستهٔ
 * دکان بود، `category` برمی‌گردد تا صفحه همان دسته را باز کند.
 */
async function search({ q, lat, lng, limit } = {}, at = now()) {
  const raw = text(q, 60);
  const needle = sf.normalize(raw);
  if (needle.length < 2) throw badRequest('دست‌کم دو حرف بنویسید', 'query_too_short');
  const near = geoOf(lat, lng);
  const lim = Math.min(MAX_PRODUCTS, Math.max(1, Number.parseInt(limit, 10) || 24));
  const list = await eligible(at);
  const cards = list.map((e) => cardOf(e, near));
  const matched = CATEGORIES.find((c) => sf.normalize(c.label) === needle || c.label.split(' و ').some((p) => sf.normalize(p) === needle));
  const shopHits = cards.filter((c) => matchesShop(c, needle)).sort(rank(near)).slice(0, 12);

  let products = [];
  if (list.length) {
    const bySlug = new Map(cards.map((c) => [c.slug, c]));
    const slugOfShop = new Map(list.map((e) => [e.row.shop_id, e.row.slug]));
    const like = `%${needle.replace(/[\\%_]/g, (c) => `\\${c}`)}%`;
    const rows = await many(
      `SELECT sp.shop_id, ${sf.PRODUCT_COLS} FROM ${sf.PRODUCT_FROM}
        WHERE sp.shop_id = ANY($1::text[]) AND sp.published AND ${sf.LIVE_PRODUCT} AND ${sf.SQL_NAME} LIKE $2
        ORDER BY COALESCE(r.data->>'name', ''), sp.shop_id, r.row_id LIMIT $3`,
      [list.map((e) => e.row.shop_id), like, lim * 2]
    );
    products = rows
      .map((r) => ({ shop: bySlug.get(slugOfShop.get(r.shop_id)), p: sf.productView(r) }))
      .filter((x) => x.shop && x.p.priceMinor !== null && x.p.name)
      .sort((a, b) => rank(near)(a.shop, b.shop) || a.p.name.localeCompare(b.p.name, 'fa'))
      .slice(0, lim)
      .map(({ shop, p }) => ({
        id: p.id, name: p.name, unit: p.unit, category: p.category, priceMinor: p.priceMinor, photo: p.photo,
        shop: {
          slug: shop.slug, title: shop.title, accent: shop.accent,
          badge: shop.status.badge, orderAcceptance: shop.status.orderAcceptance, distanceKm: shop.distanceKm,
        },
      }));
  }
  return { q: raw, category: matched ? matched.key : '', shops: shopHits, products };
}

// ════════════════════════════════════════════════════════════════════
//  «سفارش‌های من» از همهٔ ویترین‌ها (S138)
// ════════════════════════════════════════════════════════════════════

async function myOrders(ticket) {
  const phone = sf.phoneOfTicket(ticket);
  if (!phone) throw new ApiError(401, 'verification_required', 'برای دیدنِ سفارش‌ها، شمارهٔ خود را تأیید کنید');
  const rows = await many(
    `SELECT o.id, o.shop_id, o.order_no, o.status, o.total_minor, o.items_count, o.fulfillment, o.created_at,
            sf.slug, sf.title, sf.accent
       FROM online_orders o JOIN storefronts sf ON sf.shop_id = o.shop_id
      WHERE o.source = 'storefront' AND o.customer_phone = $1
      ORDER BY o.created_at DESC, o.id DESC LIMIT 50`,
    [phone]
  );
  const slugs = [...new Set(rows.map((r) => r.slug))];
  const open = new Set(await filterAsync(slugs, storeOpen));
  return rows.filter((r) => open.has(r.slug)).map((r) => ({
    orderNo: Number(r.order_no), status: r.status, totalMinor: Number(r.total_minor),
    itemsCount: Number(r.items_count), fulfillment: r.fulfillment, createdAt: Number(r.created_at),
    token: sf.tokenOf(r.shop_id, r.id),
    store: { slug: r.slug, title: r.title, accent: r.accent },
  }));
}

// ════════════════════════════════════════════════════════════════════
//  سمتِ دکان: «در بازارچه چگونه دیده شوم»
// ════════════════════════════════════════════════════════════════════

function listingView(row) {
  return {
    listed: row ? row.listed === true : true,
    category: row ? row.category : '',
    area: row ? row.area : '',
    //  به صاحبِ دکان خودش برمی‌گردد (درشت)، هرگز به مشتری
    location: row && row.geo_lat !== null && row.geo_lat !== undefined
      ? { lat: Number(row.geo_lat) / 100, lng: Number(row.geo_lng) / 100 } : null,
    updatedAt: row ? Number(row.updated_at) : null,
    categories: CATEGORIES,
  };
}

async function listingOf(shopId) {
  return listingView(await one('SELECT * FROM market_listings WHERE shop_id = $1', [shopId]));
}

/** فقط فیلدهای آمده عوض می‌شوند. */
async function saveListing(shopId, body, userId) {
  const b = obj(body);
  const has = (k) => Object.prototype.hasOwnProperty.call(b, k);
  const cur = await one('SELECT * FROM market_listings WHERE shop_id = $1', [shopId]);
  const v = {
    listed: cur ? cur.listed === true : true,
    category: cur ? cur.category : '',
    area: cur ? cur.area : '',
    lat: cur && cur.geo_lat !== null ? Number(cur.geo_lat) : null,
    lng: cur && cur.geo_lng !== null ? Number(cur.geo_lng) : null,
  };
  if (has('listed')) {
    if (typeof b.listed !== 'boolean') throw badRequest('«در فهرست» باید بله یا نه باشد', 'bad_listed');
    v.listed = b.listed;
  }
  if (has('category')) {
    const c = str(b.category);
    if (c && !CATEGORY_KEYS.has(c)) throw badRequest('دستهٔ دکان معتبر نیست', 'bad_category');
    v.category = c;
  }
  if (has('area')) v.area = text(b.area, 60);
  if (has('location')) {
    if (b.location === null) { v.lat = null; v.lng = null; } else {
      const g = geoOf(obj(b.location).lat, obj(b.location).lng);
      if (!g) throw badRequest('موقعیت معتبر نیست', 'bad_location');
      v.lat = g.lat; v.lng = g.lng;
    }
  }
  const row = await one(
    `INSERT INTO market_listings (shop_id, listed, category, area, geo_lat, geo_lng, updated_by, updated_at)
     VALUES ($1,$2,$3,$4,$5,$6,$7,$8)
     ON CONFLICT (shop_id) DO UPDATE SET listed = EXCLUDED.listed, category = EXCLUDED.category,
       area = EXCLUDED.area, geo_lat = EXCLUDED.geo_lat, geo_lng = EXCLUDED.geo_lng,
       updated_by = EXCLUDED.updated_by, updated_at = EXCLUDED.updated_at
     RETURNING *`,
    [shopId, v.listed, v.category, v.area, v.lat, v.lng, userId || '', now()]
  );
  gates.delete(shopId);
  return listingView(row);
}

/** برای مسیرِ دکان: بی ویترینِ روشن (پرچم) تنظیمِ بازارچه معنا ندارد. */
async function requireStorefrontFlag(shopId) {
  const st = await sf.stateOf(shopId);
  if (!st.flag) throw forbidden('ویترین برای این دکان روشن نیست', 'storefront_disabled');
  return st;
}

module.exports = {
  FLAG_KEY, CATEGORIES,
  directoryOn, requireOn, clearCache, eligible, overview, shops, search, myOrders,
  geoOf, distanceKm, listingOf, saveListing, requireStorefrontFlag,
};
