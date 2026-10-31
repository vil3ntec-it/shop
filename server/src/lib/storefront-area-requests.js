'use strict';
/**
 * «وقتی به محلِ شما رسیدیم خبرم کن» ِ ویترین (فاز ۴؛ CHK-10، S34).
 * شرحِ جدول: ‎migrations/176_storefront_area_requests.sql‎.
 *
 *   مشتری ── پینِ بیرون از همهٔ ناحیه‌های فعال + شمارهٔ تأییدشده (بلیتِ شماره یا نشستِ
 *             حساب) ⇒ یک درخواست برای همان (دکان، شماره)؛ تکرار همان ردیف را تازه می‌کند.
 *             پینِ درون (یا دکانِ بی ناحیه) ⇒ ۴۰۹ `in_zone`: همین حالا سفارش بدهید؛ هیچ ردیف.
 *             «پس گرفتن» ⇒ ردیف پاک.
 *   دکان ──── شمارِ درخواست‌ها به ازای خانهٔ شبکه (بی شماره) تا تقاضا دیده شود؛ شماره فقط
 *             برای درخواستی که ناحیهٔ فعالِ امروز آن را می‌پوشاند — همان کسی که باید خبرش کرد.
 *             «خبر دادم» ⇒ ردیف پاک می‌شود (کارِ رضایت تمام شد)؛ در سابقه شماره نمی‌آید.
 *   نگه‌داری ─ درخواستِ کهنه‌تر از `RETAIN_MS` دیده نمی‌شود و با نوشتنِ بعدیِ همان دکان پاک می‌شود.
 *
 * ⛔ پین هرگز خام ذخیره نمی‌شود: فقط مرکزِ خانهٔ ۳۰۰ متریِ شبکه (`snapPin`) — همان دقتی که
 *    «درون/بیرون» ِ ویترین با آن سنجیده می‌شود، پس پاسخ هم دقیق‌تر از یک خانه نیست.
 * ⛔ پرچمِ پیش‌فرض خاموشِ `shop.storefront.notify_area`، روی `shop.storefront.zones`: خاموش ⇒
 *    درِ مشتری همان پاسخِ درِ ناشناختهٔ main و درِ دکان ۴۰۳ `notify_area_disabled`.
 * ⛔ سرور پیامی نمی‌فرستد (پیامک/واتس‌اپ ِ خودکار: D-09)؛ دکان خودش خبر می‌دهد.
 */
const db = require('../db');
const { tx, now, many } = db;
const orders = require('./orders');
const sfd = require('./storefront-delivery');
const { ulid } = require('./ulid');
const { badRequest, conflict, notFound } = require('../middleware/errors');

const FLAG_KEY = 'shop.storefront.notify_area';
const MAX_PER_SHOP = 5000;
const MAX_CONTACTS = 500;
const RETAIN_MS = 365 * 24 * 60 * 60 * 1000;
const PHONE_RE = /^\+[1-9]\d{7,14}$/;

/** هر دو پرچم: بی ناحیه «بیرون از محدوده» معنا ندارد. */
async function flagOn(shopId) {
  return (await orders.flagOf(shopId, FLAG_KEY)) && (await sfd.flagOn(shopId));
}

function withData(err, data) {
  return Object.assign(err, { data });
}

function pinOf(body) {
  const b = body && typeof body === 'object' ? body : {};
  const lat = b.lat;
  const lng = b.lng;
  if (typeof lat !== 'number' || !Number.isFinite(lat) || lat < -90 || lat > 90
    || typeof lng !== 'number' || !Number.isFinite(lng) || lng < -180 || lng > 180) {
    throw badRequest('مختصاتِ پین معتبر نیست', 'bad_address');
  }
  return sfd.snapPin(lat, lng);
}

const activeZones = async (shopId, run) => (await sfd.zonesOf(shopId, run)).filter((z) => z.active);

/** ثبت یا تازه کردنِ درخواستِ همین شماره. `{ created, request }`. */
async function request(store, phone, body) {
  if (!PHONE_RE.test(String(phone || ''))) throw badRequest('شماره معتبر نیست', 'bad_phone');
  if (!store.accept_delivery) throw badRequest('این ویترین ارسال ندارد', 'fulfillment_unavailable');
  const cell = pinOf(body);
  return tx(async (c) => {
    const zones = await activeZones(store.shop_id, (sql, params) => c.query(sql, params));
    const z = zones.length ? sfd.zoneAt(zones, cell.lat, cell.lng) : null;
    if (!zones.length || z) {
      throw withData(conflict('این نشانی درونِ محدودهٔ ارسال است؛ همین حالا سفارش بدهید', 'in_zone'),
        { zone: z ? sfd.publicZone(z) : null });
    }
    const at = now();
    await c.query('DELETE FROM storefront_area_requests WHERE shop_id = $1 AND updated_at < $2', [store.shop_id, at - RETAIN_MS]);
    const old = (await c.query('SELECT 1 AS x FROM storefront_area_requests WHERE shop_id = $1 AND phone = $2',
      [store.shop_id, phone])).rows[0];
    if (!old) {
      //  سقفِ نرم: دو شمارهٔ تازهٔ هم‌زمان در لبه فقط یکی دو ردیف بیشتر می‌شوند، نه سیل
      const n = Number((await c.query('SELECT COUNT(*)::int AS n FROM storefront_area_requests WHERE shop_id = $1',
        [store.shop_id])).rows[0].n);
      if (n >= MAX_PER_SHOP) throw conflict('این دکان فعلاً درخواستِ تازه نمی‌پذیرد', 'too_many_area_requests');
    }
    //  همان شماره دو بار (یا هم‌زمان) ⇒ همان ردیف، با پینِ تازه
    const r = (await c.query(
      `INSERT INTO storefront_area_requests (id, shop_id, phone, cell_lat, cell_lng, created_at, updated_at)
       VALUES ($1,$2,$3,$4,$5,$6,$6)
       ON CONFLICT (shop_id, phone) DO UPDATE
         SET cell_lat = EXCLUDED.cell_lat, cell_lng = EXCLUDED.cell_lng, updated_at = EXCLUDED.updated_at
       RETURNING id, created_at, updated_at`,
      [ulid(at), store.shop_id, phone, cell.lat, cell.lng, at]
    )).rows[0];
    return { created: !old, request: { createdAt: Number(r.created_at), updatedAt: Number(r.updated_at) } };
  });
}

/** پس گرفتن: نبودن هم همان پاسخ است (تکرارِ امن). */
async function withdraw(store, phone) {
  const r = await db.one('DELETE FROM storefront_area_requests WHERE shop_id = $1 AND phone = $2 RETURNING id',
    [store.shop_id, String(phone || '')]);
  return { withdrawn: Boolean(r) };
}

/**
 * نمای دکان. `cells`: شمارِ هر خانه و ناحیه‌ای که امروز آن را می‌پوشاند (بی شماره)؛
 * `contacts`: فقط درخواست‌های پوشیده‌شده، کهنه‌ترین اول، با شماره برای خبر دادن.
 */
async function forShop(shopId, at = now()) {
  const [zones, rows] = await Promise.all([
    activeZones(shopId),
    many(
      `SELECT id, phone, cell_lat, cell_lng, created_at, updated_at FROM storefront_area_requests
        WHERE shop_id = $1 AND updated_at >= $2 ORDER BY created_at, id LIMIT $3`,
      [shopId, at - RETAIN_MS, MAX_PER_SHOP]
    ),
  ]);
  const cells = new Map();
  const contacts = [];
  for (const r of rows) {
    const lat = Number(r.cell_lat);
    const lng = Number(r.cell_lng);
    const z = sfd.zoneAt(zones, lat, lng);
    const k = `${lat},${lng}`;
    const cell = cells.get(k) || { lat, lng, count: 0, coveredBy: z ? z.name : null };
    cell.count += 1;
    cells.set(k, cell);
    if (z && contacts.length < MAX_CONTACTS) {
      contacts.push({ id: r.id, phone: r.phone, zone: z.name, requestedAt: Number(r.created_at), updatedAt: Number(r.updated_at) });
    }
  }
  return {
    total: rows.length,
    covered: rows.filter((r) => sfd.zoneAt(zones, Number(r.cell_lat), Number(r.cell_lng))).length,
    cells: [...cells.values()].sort((a, b) => b.count - a.count || a.lat - b.lat || a.lng - b.lng),
    contacts,
  };
}

/** «خبر دادم» ⇒ ردیف پاک. فقط درخواستِ همین دکان که امروز پوشیده است. */
async function markNotified(shopId, id) {
  return tx(async (c) => {
    const r = (await c.query('SELECT id, cell_lat, cell_lng FROM storefront_area_requests WHERE id = $1 AND shop_id = $2 FOR UPDATE',
      [String(id || ''), shopId])).rows[0];
    if (!r) throw notFound('این درخواست پیدا نشد', 'area_request_not_found');
    const zones = await activeZones(shopId, (sql, params) => c.query(sql, params));
    const z = sfd.zoneAt(zones, Number(r.cell_lat), Number(r.cell_lng));
    if (!z) throw conflict('این محل هنوز در هیچ ناحیهٔ فعالی نیست', 'not_covered');
    await c.query('DELETE FROM storefront_area_requests WHERE id = $1', [r.id]);
    return { notified: true, zone: z.name };
  });
}

module.exports = { FLAG_KEY, MAX_PER_SHOP, RETAIN_MS, flagOn, request, withdraw, forShop, markNotified };
