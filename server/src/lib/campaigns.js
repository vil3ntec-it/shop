'use strict';
/**
 * پیوندِ کمپین و QR ِ دکان — MKT-11 (پیوند با ردیابیِ منبع) و MKT-10 (QR ِ روی
 * دیوار و رسید که مشتریِ حضوری را به ویترین می‌برد)، بخشِ ۶.۲۰. مهاجرتِ ۱۵۳.
 *
 * ── چه چیزی ─────────────────────────────────────────────────────────
 * دکان پیوندِ کوتاهِ `/store/go/<code>` می‌سازد (مثلاً «QR ِ پیشخوان»، «اینستاگرام»).
 * مشتری که آن را باز می‌کند به ویترینِ همان دکان می‌رود و بازدیدش یک بار در
 * روز شمرده می‌شود؛ سفارشی که پس از آن ثبت شود به همان پیوند نسبت داده می‌شود.
 * برنامهٔ مدیریت فقط شمار می‌بیند: بازدید، سفارش و فروشِ تحویل‌شده.
 *
 * ── قاعده‌ها ─────────────────────────────────────────────────────────
 * ⛔ پیش‌فرض خاموش: پرچمِ `shop.storefront.campaigns` و همان دروازه‌های ویترین.
 *    پرچمِ خاموش، پیوندِ ناشناخته و ویترینِ بسته یک پاسخ‌اند (۴۰۴، «این ویترین
 *    پیدا نشد») — هیچ پیشگویی دربارهٔ بودنِ پیوند نیست.
 * ⛔ پیوند به دکان گره دارد، نه به slug: نشانیِ ویترین عوض شود، QR ِ چاپ‌شده
 *    همان دکان را باز می‌کند. پیوندِ بایگانی‌شده هنوز به ویترین می‌برد (کاغذِ
 *    دیوار نمی‌شکند) ولی دیگر نه بازدید می‌شمرد و نه سفارش.
 * ⛔ حریمِ خصوصی: نه IP، نه مرورگر، نه شماره ذخیره می‌شود. حذفِ تکرارِ همان روز با
 *    HMAC ِ (پیوند، روز، IP، مرورگر) با کلیدِ سرور است و بیش از سه روز نمی‌ماند.
 *    نمای دکان فقط شمار و جمع است؛ نامِ کسی و هیچ سفارشِ تکی در آن نیست.
 * ⛔ نسبت دادنِ سفارش هرگز ثبتِ سفارش را نمی‌بندد: کدِ نادرست، پیوندِ بیگانه یا
 *    بایگانی‌شده فقط نادیده گرفته می‌شود. قیمت و جمعِ سفارش را دست نمی‌زند.
 * ⛔ shop_id ِ نوشتن فقط از عضویتِ توکن؛ در مسیرِ عمومی فقط از خودِ پیوند.
 * • `client_id` کلیدِ یکتاییِ برنامه است: دوبار زدن (یا صفِ آفلاین) ⇒ همان پیوند.
 */
const crypto = require('crypto');
const config = require('../config');
const { query, one, many, tx, now } = require('../db');
const { ulid, ALPHABET } = require('./ulid');
const { badRequest, conflict, notFound } = require('../middleware/errors');

const FLAG_KEY = 'shop.storefront.campaigns';
/** منبع‌ها — نخستین دوتا همان QR ِ MKT-10 */
const SOURCES = Object.freeze(['qr_wall', 'qr_receipt', 'social', 'messaging', 'sms', 'print', 'other']);
const CODE_LEN = 10;
const CODE_RE = /^[0-9A-HJKMNP-TV-Z]{10}$/;
const CLIENT_ID_RE = /^[A-Za-z0-9_-]{4,64}$/;
const MAX_NAME = 60;
/** سقفِ پیوند برای هر دکان (بایگانی‌شده هم می‌شمارد) — جدول بی‌کران نمی‌شود */
const MAX_PER_SHOP = 200;
const MAX_DAYS = 365;
const DEFAULT_DAYS = 30;
/** ردیفِ حذفِ تکرار پس از این همه روز پاک می‌شود */
const VISITOR_KEEP_DAYS = 3;
const SWEEP_EVERY_MS = 60 * 60 * 1000;
/** «فروشِ انجام‌شده»: تحویل یا برداشته شده (پیش از بازپرداخت) */
const DONE = Object.freeze(['delivered', 'picked_up', 'return_requested', 'refunded_partial']);
const DAY_MS = 24 * 60 * 60 * 1000;

const orders = () => require('./orders');
const storefront = () => require('./storefront');

const obj = (x) => (x && typeof x === 'object' && !Array.isArray(x) ? x : {});
const str = (x) => (typeof x === 'string' ? x : '');

async function flagOn(shopId) {
  return orders().flagOf(shopId, FLAG_KEY);
}

/** کدِ کوتاه از ورودیِ کاربر (کوچک ⇒ بزرگ)؛ نادرست ⇒ '' */
function normCode(raw) {
  const s = str(raw).trim().toUpperCase();
  return CODE_RE.test(s) ? s : '';
}

function newCode() {
  const bytes = crypto.randomBytes(CODE_LEN);
  let out = '';
  for (let i = 0; i < CODE_LEN; i++) out += ALPHABET[bytes[i] & 31];
  return out;
}

/** نام: یک خط، بی نویسهٔ کنترلی، ۱ تا ۶۰ نویسه */
function nameOf(v) {
  const s = str(v).replace(/[\u0000-\u001f\u007f\u2028\u2029]/g, ' ').replace(/\s+/g, ' ').trim();
  if (!s) throw badRequest('نامِ پیوند را بنویسید', 'bad_name');
  if ([...s].length > MAX_NAME) throw badRequest(`نامِ پیوند حداکثر ${MAX_NAME} نویسه`, 'bad_name');
  return s;
}

function sourceOf(v) {
  const s = str(v);
  if (!SOURCES.includes(s)) throw badRequest('منبعِ پیوند معتبر نیست', 'bad_source');
  return s;
}

const path = (code) => `/store/go/${code}`;

/** نمای دکان — بی created_by و بی هیچ شناسهٔ کارمند */
function view(r, stats) {
  return {
    id: r.id,
    clientId: r.client_id,
    code: r.code,
    path: path(r.code),
    name: r.name,
    source: r.source,
    archived: r.archived === true,
    createdAt: Number(r.created_at),
    updatedAt: Number(r.updated_at),
    ...(stats ? { stats } : {}),
  };
}

// ════════════════════════════════════════════════════════════════════
//  دکان: ساختن، ویرایش، فهرست
// ════════════════════════════════════════════════════════════════════

async function create(shopId, body, userId = '', at = now()) {
  const b = obj(body);
  const clientId = str(b.clientId);
  if (!CLIENT_ID_RE.test(clientId)) throw badRequest('شناسهٔ برنامه (clientId) معتبر نیست', 'bad_client_id');
  const name = nameOf(b.name);
  const source = sourceOf(b.source);
  return tx(async (c) => {
    //  ساختن‌های هم‌زمانِ یک دکان پشتِ هم: سقف دقیق می‌ماند
    await c.query('SELECT id FROM shops WHERE id=$1 FOR UPDATE', [shopId]);
    const prev = (await c.query('SELECT * FROM store_campaigns WHERE shop_id=$1 AND client_id=$2', [shopId, clientId])).rows[0];
    if (prev) return { campaign: view(prev), replayed: true };
    const n = (await c.query('SELECT COUNT(*)::int AS n FROM store_campaigns WHERE shop_id=$1', [shopId])).rows[0].n;
    if (n >= MAX_PER_SHOP) throw conflict(`حداکثر ${MAX_PER_SHOP} پیوند برای هر دکان`, 'campaign_limit');
    for (let i = 0; i < 5; i++) {
      const row = (await c.query(
        `INSERT INTO store_campaigns (id, shop_id, client_id, code, name, source, created_at, created_by, updated_at)
         VALUES ($1,$2,$3,$4,$5,$6,$7,$8,$7)
         ON CONFLICT (code) DO NOTHING RETURNING *`,
        [ulid(at), shopId, clientId, newCode(), name, source, at, String(userId || '')]
      )).rows[0];
      if (row) return { campaign: view(row), replayed: false };
    }
    throw conflict('ساختنِ پیوند نشد؛ دوباره بزنید', 'campaign_code_busy');
  });
}

async function update(shopId, id, body, at = now()) {
  const b = obj(body);
  const sets = [];
  const args = [shopId, String(id || '')];
  if (b.name !== undefined) { args.push(nameOf(b.name)); sets.push(`name=$${args.length}`); }
  if (b.archived !== undefined) {
    if (typeof b.archived !== 'boolean') throw badRequest('بایگانی: true یا false', 'bad_archived');
    args.push(b.archived); sets.push(`archived=$${args.length}`);
  }
  if (!sets.length) throw badRequest('چیزی برای تغییر نیست', 'nothing_to_update');
  args.push(at);
  const row = await one(
    `UPDATE store_campaigns SET ${sets.join(', ')}, updated_at=$${args.length}
      WHERE shop_id=$1 AND id=$2 RETURNING *`, args
  );
  if (!row) throw notFound('پیوند پیدا نشد', 'campaign_not_found');
  return { campaign: view(row) };
}

/** روزِ تقویمِ دکان (YYYY-MM-DD) — ساعتِ گوشیِ مشتری مرجع نیست */
function dayOf(at, timezone) {
  return storefront().localParts(at, timezone || 'Asia/Kabul').date;
}

async function timezoneOfShop(shopId) {
  const r = await one('SELECT timezone FROM storefronts WHERE shop_id=$1', [shopId]);
  return (r && r.timezone) || 'Asia/Kabul';
}

function daysOf(v) {
  if (v === undefined || v === null || v === '') return DEFAULT_DAYS;
  const n = Number(v);
  if (!Number.isInteger(n) || n < 1 || n > MAX_DAYS) throw badRequest(`بازه: ۱ تا ${MAX_DAYS} روز`, 'bad_days');
  return n;
}

/**
 * پیوندها با شمارِ `days` روزِ اخیر (امروز هم): بازدیدِ یکتا، سفارش (بی آزمایشی)،
 * سفارشِ انجام‌شده و جمعِ آن. فقط شمار — هیچ نام، شماره یا سفارشِ تکی.
 */
async function list(shopId, { days, at = now() } = {}) {
  const span = daysOf(days);
  const tz = await timezoneOfShop(shopId);
  const fromDay = dayOf(at - (span - 1) * DAY_MS, tz);
  const fromAt = at - span * DAY_MS;
  const rows = await many(
    `SELECT c.*, COALESCE(v.visits, 0)::int AS visits,
            COALESCE(o.orders, 0)::int AS orders, COALESCE(o.done, 0)::int AS done,
            COALESCE(o.done_minor, 0)::bigint AS done_minor
       FROM store_campaigns c
       LEFT JOIN (SELECT campaign_id, SUM(visits) AS visits FROM store_campaign_days
                   WHERE shop_id=$1 AND day >= $2 GROUP BY campaign_id) v ON v.campaign_id = c.id
       LEFT JOIN (SELECT a.campaign_id, COUNT(*) AS orders,
                         COUNT(*) FILTER (WHERE o.status = ANY($4::text[])) AS done,
                         SUM(CASE WHEN o.status = ANY($4::text[]) THEN o.total_minor ELSE 0 END) AS done_minor
                    FROM store_campaign_orders a
                    JOIN online_orders o ON o.id = a.order_id AND o.shop_id = a.shop_id
                   WHERE a.shop_id=$1 AND a.at >= $3 AND NOT o.is_test
                   GROUP BY a.campaign_id) o ON o.campaign_id = c.id
      WHERE c.shop_id=$1
      ORDER BY c.archived, c.created_at DESC, c.id DESC
      LIMIT ${MAX_PER_SHOP}`,
    [shopId, fromDay, fromAt, DONE]
  );
  return {
    days: span,
    sources: SOURCES,
    campaigns: rows.map((r) => view(r, {
      visits: r.visits, orders: r.orders, doneOrders: r.done, doneMinor: Number(r.done_minor),
    })),
  };
}

// ════════════════════════════════════════════════════════════════════
//  عمومی: باز کردنِ پیوند و نسبت دادنِ سفارش
// ════════════════════════════════════════════════════════════════════

/**
 * پیوند ⇒ { campaign, store } — یا ۴۰۴ ِ همان «ویترین پیدا نشد».
 * پرچمِ خاموش، کدِ ناشناخته و ویترینِ بسته هیچ فرقی در پاسخ ندارند.
 */
async function resolve(raw) {
  const gone = () => notFound('این ویترین پیدا نشد', 'store_not_found');
  const code = normCode(raw);
  if (!code) throw gone();
  const row = await one(
    `SELECT c.*, sf.slug FROM store_campaigns c JOIN storefronts sf ON sf.shop_id = c.shop_id WHERE c.code=$1`,
    [code]
  );
  if (!row || !(await flagOn(row.shop_id))) throw gone();
  const store = await storefront().open(row.slug);   // بسته/معلق/بی اشتراک ⇒ همان ۴۰۴
  return { campaign: row, store };
}

let lastSweep = 0;
function visitorKey() {
  const secret = config.secrets.api || config.secrets.jwt || '';
  return crypto.createHash('sha256').update(`store-campaign-visitor\n${secret}`).digest();
}

/**
 * بازدیدِ یکتای امروز — true اگر تازه شمرده شد. پیوندِ بایگانی‌شده نمی‌شمارد.
 * IP و مرورگر فقط در HMAC می‌روند و خودشان جایی نوشته نمی‌شوند.
 */
async function recordVisit(campaign, store, { ip = '', ua = '', at = now() } = {}) {
  if (campaign.archived === true) return false;
  const day = dayOf(at, store.timezone);
  const visitor = crypto.createHmac('sha256', visitorKey())
    .update(`${campaign.id}\n${day}\n${ip}\n${String(ua).slice(0, 300)}`).digest('base64url').slice(0, 22);
  const fresh = await tx(async (c) => {
    const ins = await c.query(
      `INSERT INTO store_campaign_visitors (campaign_id, day, visitor) VALUES ($1,$2,$3)
       ON CONFLICT DO NOTHING RETURNING 1 AS x`, [campaign.id, day, visitor]
    );
    if (!ins.rows.length) return false;
    await c.query(
      `INSERT INTO store_campaign_days (campaign_id, shop_id, day, visits) VALUES ($1,$2,$3,1)
       ON CONFLICT (campaign_id, day) DO UPDATE SET visits = store_campaign_days.visits + 1`,
      [campaign.id, campaign.shop_id, day]
    );
    return true;
  });
  if (at - lastSweep >= SWEEP_EVERY_MS) {
    lastSweep = at;
    //  روزِ تقویمِ هر دکان با UTC حداکثر یک روز فرق دارد ⇒ یک روزِ اضافه
    const cutoff = new Date(at - (VISITOR_KEEP_DAYS + 1) * DAY_MS).toISOString().slice(0, 10);
    await query('DELETE FROM store_campaign_visitors WHERE day < $1', [cutoff])
      .catch((err) => console.error('[campaigns] sweep', err.message));
  }
  return fresh;
}

/**
 * سفارشِ تازهٔ ویترین ⇐ پیوند. بی‌صدا: کدِ نادرست/بیگانه/بایگانی‌شده یا پرچمِ
 * خاموش ⇒ false. تکرار (درخواستِ تکراریِ همان سفارش) ⇒ همان یک ردیف.
 */
async function attribute(store, orderId, raw, at = now()) {
  const code = normCode(raw);
  if (!code || !orderId || !store) return false;
  if (!(await flagOn(store.shop_id))) return false;
  const r = await one(
    `INSERT INTO store_campaign_orders (order_id, shop_id, campaign_id, at)
     SELECT o.id, o.shop_id, c.id, $4
       FROM online_orders o JOIN store_campaigns c ON c.shop_id = o.shop_id
      WHERE o.id=$1 AND o.shop_id=$2 AND c.code=$3 AND NOT c.archived
     ON CONFLICT (order_id) DO NOTHING RETURNING campaign_id`,
    [orderId, store.shop_id, code, at]
  );
  return Boolean(r);
}

function resetSweep() { lastSweep = 0; }

module.exports = {
  FLAG_KEY, SOURCES, MAX_PER_SHOP, VISITOR_KEEP_DAYS, CODE_RE, DONE,
  flagOn, normCode, path, create, update, list, resolve, recordVisit, attribute, resetSweep,
};
