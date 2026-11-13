'use strict';
/**
 * CRM-15 — «سابقهٔ دسترسی»: چه کسی پروندهٔ یک مشتری را دید (`customer_access_log`، مهاجرتِ ۳۸۰).
 *
 * چه ثبت می‌شود — هر خواندنِ **یک** مشتری از درِ سرور به دستِ صاحب یا کارمند:
 *   door `debtor`  (درِ `GET /api/debtors/:id` ۱۴۰۵/۰۸/۲۲ برداشته شد — هیچ برنامه‌ای صدایش نمی‌زد؛ ردیف‌های کهنه‌اش
 *                  می‌مانند و در فهرست همان‌اند)؛
 *   door `orders`  `GET /api/shop/orders/customer` (CRM-07، سفارش‌های یک مشتری با شمارهٔ او) — kind `detail`.
 *   door `app`     `POST /api/shop/customer-access` ([noteApp]، مهاجرتِ ۶۱۰): دیدنی که **برنامه** از دفترِ خودش گزارش
 *                  می‌دهد — وب (`license/customer-access.js`، دفترِ مرورگر) و نقشی که `auditLog` نمی‌نویسد (حسابدار،
 *                  `perm-core.canWrite`)؛ `*` = خروجیِ برگهٔ همهٔ مشتریان (فقط `export`).
 *   فهرست‌ها و همگام‌سازی خواندنِ «یک مشتری» نیستند و این‌جا نمی‌آیند. خواندنِ دفترِ خودِ
 *   دستگاه (بی‌اینترنت) را برنامه در `auditLog` ِ دفتر می‌نویسد (`type = customer_view`) و با همان همگام‌سازیِ
 *   امروز به سرور می‌رسد — نه این جدول (`data/CustomerAccessLog.kt`).
 *
 * ⛔ هیچ دادهٔ شخصیِ مشتری: فقط شناسهٔ پرونده (`customer_ref` = شناسهٔ قرض‌دار)، یا برای خواندنِ با شماره بی
 *    شناسه `ph:` + HMAC ِ کلیدِ شماره (رازِ سرور؛ همان روشِ `customer-notify.phoneKey`). نام، شماره، نشانی و متن نه.
 * ⛔ یکی کردنِ تکرار: همان بیننده + همان مشتری + همان نوع در ۱۰ دقیقه از نخستین دیدنِ ردیف ⇐ همان ردیف با
 *    `hits + 1` (قفلِ تراکنشیِ همان کلید، تا دو درخواستِ هم‌زمان دو ردیف نسازند).
 * ⛔ نوشتن هرگز خواندن را نگه نمی‌دارد و نمی‌اندازد: [noteLookup] منتظر نمی‌ماند و هر خطا فقط شمرده می‌شود ([stats]،
 *    در «سلامت» ِ پنلِ مرکزی) — پاسخِ خواندن همان پاسخِ پیشین است.
 * ⛔ فقط‌افزودنی: تریگرِ جدول جز بالا رفتنِ `hits`/`last_at` و ناشناس شدنِ `customer_ref` (⇐ `erased:…`، SEC-10)
 *    هیچ ویرایشی را نمی‌پذیرد. پاک شدن فقط با نگه‌داری ([sweep]، `CUSTOMER_ACCESS_RETENTION_DAYS`؛ ۰ = همان
 *    قاعدهٔ `audit_logs`: سرور خودش پاک نمی‌کند تا عددِ نگه‌داری تصویب شود) یا پاک شدنِ خودِ دکان.
 * ⛔ دکان فقط از توکن (`shop_id`)؛ خواندنِ فهرست ([list]) پشتِ `audit.view` (صاحب همیشه) — `routes/shop-customer-access.js`.
 */
const crypto = require('crypto');
const config = require('../config');
const { tx, query, many, newId, now } = require('../db');
const v = require('./validate');
const { serverSecret } = require('./server-secret');
const { badRequest } = require('../middleware/errors');

const KINDS = Object.freeze(['detail', 'phone', 'export']);
const DOORS = Object.freeze(['debtor', 'orders', 'app']);
/** `customer_ref` ِ خروجیِ برگهٔ همهٔ مشتریان (همان `CustomerAccess.ALL` ِ برنامه) — در پروندهٔ هر مشتری دیده می‌شود */
const ALL = '*';
const WINDOW_MS = 10 * 60 * 1000;
const PHONE_PREFIX = 'ph:';
const ERASED_PREFIX = 'erased:';
const LIST_MAX = 200;
/** SR45-1: سهمِ بیشینهٔ `*` از صفحهٔ فهرست وقتی ردیفِ خودیِ همان مشتری بیشتر است (۱ از ۴) */
const ALL_SHARE = 4;
const REF_RE = /^[A-Za-z0-9_.:-]{1,80}$/;

//  ── شمارِ ops (در حافظهٔ همین نمونه) ─────────────────────────────────
const counters = { recorded: 0, failed: 0, lastError: '', lastFailedAt: 0 };
const pending = new Set();
let lastWarnAt = 0;

/** شناسهٔ پرونده‌ای که می‌شود نوشت؛ هر چیزِ دیگر ⇐ null (بی خطا) */
function refOf(x) {
  return typeof x === 'string' && REF_RE.test(x) && !x.startsWith(ERASED_PREFIX) && !x.startsWith(PHONE_PREFIX) ? x : null;
}

/** `ph:` + HMAC ِ کلیدِ شماره (‎+930…‎ و ‎+93…‎ یکی)؛ شمارهٔ نادرست ⇐ null */
function phoneRef(phone) {
  let p;
  try { p = v.phone(phone, { required: true }); } catch { return null; }
  const key = crypto.createHash('sha256')
    .update(`customer-access-phone\n${serverSecret(['api', 'jwt'], 'customer-access')}`).digest();
  return PHONE_PREFIX + crypto.createHmac('sha256', key).update(v.phoneKey(p)).digest('hex').slice(0, 32);
}

/**
 * یک خواندن ⇒ ردیفِ تازه یا `hits + 1` ِ ردیفِ ۱۰ دقیقهٔ اخیر. خطای پایگاه داده بالا می‌رود ([noteLookup] می‌شمارد، [noteApp] بالا می‌برد).
 * @returns {Promise<{id: string, hits: number}>}
 */
async function record({ shopId, viewerId, role = '', deviceId = '', customerRef, kind, door, at = now() }) {
  if (!shopId || !viewerId) throw new Error('customer_access_actor');
  if (!KINDS.includes(kind) || !DOORS.includes(door)) throw new Error('customer_access_kind');
  if (typeof customerRef !== 'string' || !customerRef || customerRef.length > 120) throw new Error('customer_access_ref');
  const r = String(role || '').slice(0, 32);
  const dev = String(deviceId || '').slice(0, 80);
  return tx(async (c) => {
    await c.query('SELECT pg_advisory_xact_lock(hashtext($1))', [`customer-access:${shopId}:${viewerId}:${customerRef}:${kind}`]);
    const bumped = (await c.query(
      `UPDATE customer_access_log SET hits = hits + 1, last_at = GREATEST(last_at, $6)
        WHERE id = (SELECT id FROM customer_access_log
                     WHERE shop_id=$1 AND viewer_id=$2 AND customer_ref=$3 AND kind=$4 AND first_at > $5
                     ORDER BY first_at DESC LIMIT 1)
        RETURNING id, hits`,
      [shopId, viewerId, customerRef, kind, at - WINDOW_MS, at]
    )).rows[0];
    if (bumped) return { id: bumped.id, hits: Number(bumped.hits) };
    const id = newId('cav');
    await c.query(
      `INSERT INTO customer_access_log (id, shop_id, viewer_id, role, device_id, customer_ref, kind, door, first_at, last_at, hits)
       VALUES ($1,$2,$3,$4,$5,$6,$7,$8,$9,$9,1)`,
      [id, shopId, viewerId, r, dev, customerRef, kind, door, at]
    );
    return { id, hits: 1 };
  });
}

function failed(err) {
  counters.failed += 1;
  counters.lastError = String(err?.code || err?.message || err?.name || 'error').slice(0, 60);
  counters.lastFailedAt = Date.now();
  //  ⛔ بی دادهٔ شخصی: فقط کدِ خطا، حداکثر یک بار در دقیقه
  if (counters.lastFailedAt - lastWarnAt > 60_000) {
    lastWarnAt = counters.lastFailedAt;
    console.warn(`[customer-access] ثبتِ سابقهٔ دسترسی نشد (${counters.lastError}) — شمارِ خطا: ${counters.failed}`);
  }
}

/**
 * ⛔ «سفارش‌های یک مشتری» (`GET /api/shop/orders/customer`): هر شماره‌ای که پرس‌وجو **واقعاً** خواند (همان دو شمارهٔ
 * نخستِ `orders.forCustomer`) ثبت می‌شود، نه فقط نخستین و نه فقط `customerId` ِ فرستاده. پیش‌تر `?phone=x&phone=<شماره>`
 * (نخستینِ نادرست ⇒ هیچ ردیف) یا `customerId` ِ ساختگی دیدن را از پروندهٔ همان مشتری پنهان می‌کرد. `customerId` ِ درست
 * (شمارهٔ پرس‌وجو در دفترِ سرورِ همان پرونده) ⇒ فقط یک ردیف با شناسهٔ پرونده، همان پیشین؛ هر شمارهٔ دیگر ردیفِ `ph:` ِ خودش.
 * بی انتظار و بی خطا: خواندن همیشه همان پاسخِ پیشین را می‌گیرد.
 */
function noteLookup(req, { customerId, phones, kind = 'detail', door = 'orders' }) {
  try {
    if (!req || !req.shopId || !req.user) return;
    const cid = refOf(customerId);
    const asked = (Array.isArray(phones) ? phones : []).slice(0, 2)
      .map((p) => (typeof p === 'string' && p.trim() && p.length <= 40 ? phoneRef(p) : null)).filter(Boolean);
    if (!cid && !asked.length) return;
    const who = {
      shopId: req.shopId, viewerId: req.user.id, role: req.role || req.member?.role || '',
      deviceId: req.device?.device_uid || '', kind, door,
    };
    const p = Promise.resolve()
      .then(async () => {
        const covered = new Set(cid && asked.length ? await phoneRefsOf({ query }, req.shopId, cid) : []);
        const refs = [...new Set([...(cid ? [cid] : []), ...asked.filter((r) => !covered.has(r))])];
        for (const customerRef of refs) {
          try {
            await api.record({ ...who, customerRef });
            counters.recorded += 1;
          } catch (err) { failed(err); }
        }
      })
      .catch(failed)
      .finally(() => pending.delete(p));
    pending.add(p);
  } catch (err) { failed(err); }
}

/**
 * دیدنی که **برنامه** از دفترِ خودش گزارش می‌دهد (`POST /api/shop/customer-access`، درِ `app`) — وب و نقشی که `auditLog`
 * نمی‌نویسد. ⛔ بیننده، نقش، دکان و دستگاه فقط از توکن؛ از بدنه فقط `customerId` و `kind`. ⛔ `phone` از اصلی که
 * `customers.contact` ندارد ⇐ `detail` (شمارهٔ کامل هرگز به او نرسیده؛ سرور داور است). `*` فقط با `export`. همان
 * یکی کردنِ ۱۰ دقیقه. شناسهٔ بدشکل یا نوعِ ناشناخته ⇐ ۴۰۰؛ خطای پایگاه شمرده می‌شود و بالا می‌رود.
 * @returns {Promise<{kind: string, hits: number}>}
 */
async function noteApp(req, { customerId, kind }) {
  if (typeof kind !== 'string' || !KINDS.includes(kind)) throw badRequest('نوعِ دیدن درست نیست', 'bad_kind');
  const ref = customerId === ALL ? (kind === 'export' ? ALL : null) : refOf(customerId);
  if (!ref) throw badRequest('شناسهٔ پرونده درست نیست', 'bad_customer');
  const recorded = kind === 'phone' && (await require('./customer-contact').hiddenForReq(req)) ? 'detail' : kind;
  try {
    const r = await api.record({
      shopId: req.shopId, viewerId: req.user.id, role: req.role || req.member?.role || '',
      deviceId: req.device?.device_uid || '', customerRef: ref, kind: recorded, door: 'app',
    });
    counters.recorded += 1;
    return { kind: recorded, hits: r.hits };
  } catch (err) { failed(err); throw err; }
}

/** همهٔ نوشتن‌های در راه (آزمون، و پیش از بستنِ سرور) */
async function settle() { while (pending.size) await Promise.allSettled([...pending]); }

function stats() { return { ...counters, pending: pending.size, retentionDays: retentionDays() }; }

function retentionDays() {
  const n = Number(config.customerAccess.retentionDays);
  return Number.isFinite(n) && n > 0 ? Math.floor(n) : 0;
}

//  ── شمارهٔ قرض‌دار در دفترِ سرور (راهِ قدیم، v1، v3) ───────────────────
const DIGITS_SQL = (col) => `right(regexp_replace(translate(coalesce(${col}, ''), '۰۱۲۳۴۵۶۷۸۹٠١٢٣٤٥٦٧٨٩', '01234567890123456789'), '[^0-9]', '', 'g'), 9)`;
const LEDGER_DEBTORS = (legacy, synced) => `
  SELECT id AS row_id, data->>'phone' AS p1, data->>'phone2' AS p2 FROM debtors WHERE shop_id=$1 AND ${legacy}
  UNION ALL
  SELECT row_id, data->>'phone', data->>'phone2' FROM sync_rows
   WHERE app='shop' AND account_kind='shop' AND account_id=$1 AND table_name='debtors' AND ${synced}
  UNION ALL
  SELECT row_id, data->>'phone', data->>'phone2' FROM sync_v3_rows
   WHERE app='shop' AND account_kind='shop' AND account_id=$1 AND table_name='debtors' AND ${synced}`;

/** شماره‌های یک قرض‌دار (اول و دوم) در دفترِ سرورِ این دکان ⇐ کلیدهای `ph:` */
async function phoneRefsOf(c, shopId, debtorId) {
  const rows = (await c.query(LEDGER_DEBTORS('id = $2', 'row_id = $2'), [shopId, debtorId])).rows;
  const out = new Set();
  for (const r of rows) for (const p of [r.p1, r.p2]) { const k = p ? phoneRef(p) : null; if (k) out.add(k); }
  return [...out];
}

/** قرض‌دارانِ این دکان با همین شماره (اول یا دوم) ⇐ `{ row_id, p1, p2 }` — برای ناشناس کردن در SEC-10 */
async function debtorIdsByPhone(c, shopId, forms) {
  const keys = new Set((forms || []).map((f) => v.phoneKey(f)));
  const tail = [...keys][0]?.replace(/\D/g, '').slice(-9);
  if (!tail || tail.length < 7) return [];
  const where = `(${DIGITS_SQL("data->>'phone'")} = $2 OR ${DIGITS_SQL("data->>'phone2'")} = $2)`;
  const rows = (await c.query(LEDGER_DEBTORS(where, where), [shopId, tail])).rows;
  const same = (p) => { try { return keys.has(v.phoneKey(v.phone(p, { required: true }))); } catch { return false; } };
  return rows.filter((r) => same(r.p1) || same(r.p2));
}

/**
 * SEC-10: ردیف‌های همین مشتری ⇐ `customer_ref = erased:<شناسهٔ حذف>` (بیننده، نقش، زمان و شمار می‌مانند).
 * @param c اتصالِ تراکنشِ حذف  @param {string[]} forms همهٔ شکل‌های شماره (`validate.phoneForms`)
 * @returns {Promise<number>} شمارِ ردیف‌های ناشناس‌شده
 */
async function anonymize(c, shopId, forms, placeholder) {
  if (!forms || !forms.length) return 0;
  //  همین شماره، پرونده‌های قرض‌دارِ همین شماره، و شمارهٔ دیگرِ همان پرونده‌ها (اول/دوم)
  const debtors = await debtorIdsByPhone(c, shopId, forms);
  const refs = [...new Set([...forms.map(phoneRef), ...debtors.flatMap((d) => [d.row_id, d.p1 && phoneRef(d.p1), d.p2 && phoneRef(d.p2)])]
    .filter(Boolean))];
  if (!refs.length) return 0;
  const r = await c.query(
    'UPDATE customer_access_log SET customer_ref = $3 WHERE shop_id=$1 AND customer_ref = ANY($2::text[]) RETURNING id',
    [shopId, refs, placeholder]
  );
  return r.rows.length;
}

/**
 * سابقهٔ دسترسیِ یک مشتری (درِ سرور)، تازه‌ترین اول — بیننده (شناسه و نام)، نقش، دستگاه، نوع، در، زمان و شمار
 * (و `all: true` برای خروجیِ برگهٔ همهٔ مشتریان).
 * ⛔ فقط همین دکان؛ `ph:` ِ شماره‌های همین قرض‌دار هم (خواندنِ «سفارش‌های مشتری» بی شناسهٔ پرونده)، و خروجی‌های
 *    برگهٔ همهٔ مشتریان (`*`، درِ `app`) — همان قاعدهٔ `CustomerAccess.rows` ِ برنامه.
 * ⛔ SR45-1: ردیف‌های `*` در فهرستِ **هر** مشتری می‌آیند و هر عضو هر ۱۰ دقیقه یکی می‌نویسد ⇒ با یک `LIMIT` ِ مشترک،
 *    همان انباشت (یا شاگردی که عمداً «خروجیِ همه» می‌فرستد) دیدنِ واقعیِ همین مشتری را از صفحه بیرون می‌راند. پس `*` وقتی
 *    ردیفِ خودیِ بیشتری هست حداکثر یک‌چهارمِ صفحه را می‌گیرد؛ جای خالی را هر کدام پر می‌کند و ترتیب همان تازه‌ترین اول.
 */
async function list(shopId, customerId, { limit = 100 } = {}) {
  const id = refOf(customerId);
  if (!id) return [];
  const lim = Math.min(Math.max(Number(limit) || 100, 1), LIST_MAX);
  const refs = [id, ...(await phoneRefsOf({ query }, shopId, id))];
  const of = (where, params) => many(
    `SELECT l.id, l.viewer_id, COALESCE(u.name, '') AS viewer_name, l.role, l.device_id, l.kind, l.door,
            l.first_at, l.last_at, l.hits, l.customer_ref = '*' AS all_sheet
       FROM customer_access_log l LEFT JOIN users u ON u.id = l.viewer_id
      WHERE l.shop_id = $1 AND ${where}
      ORDER BY l.last_at DESC, l.id DESC LIMIT $3`,
    [shopId, ...params, lim]
  );
  const own = await of('l.customer_ref = ANY($2::text[])', [refs]);
  const sheet = await of('l.customer_ref = $2', [ALL]);
  const sheetTake = Math.min(sheet.length, Math.max(Math.ceil(lim / ALL_SHARE), lim - own.length));
  const rows = [...own.slice(0, lim - sheetTake), ...sheet.slice(0, sheetTake)]
    .sort((a, b) => Number(b.last_at) - Number(a.last_at) || (a.id < b.id ? 1 : a.id > b.id ? -1 : 0));
  return rows.map((r) => ({
    id: r.id, viewerId: r.viewer_id, viewerName: r.viewer_name, role: r.role, device: r.device_id,
    kind: r.kind, door: r.door, firstAt: Number(r.first_at), lastAt: Number(r.last_at), count: Number(r.hits),
    //  خروجیِ برگهٔ همهٔ مشتریان (`*`) — برنامه «برگهٔ همه» می‌نویسد، نه «صورت‌حسابِ همین مشتری»
    ...(r.all_sheet ? { all: true } : {}),
  }));
}

/** نگه‌داری: ردیف‌های کهنه‌تر از `retentionDays` روز (۰ ⇒ هیچ؛ همان قاعدهٔ `audit_logs`) */
async function sweep({ at = now(), days = retentionDays() } = {}) {
  if (!(Number(days) > 0)) return 0;
  const r = await tx((c) => c.query('DELETE FROM customer_access_log WHERE last_at < $1 RETURNING id', [at - Number(days) * 86_400_000]));
  return r.rows.length;
}

const api = module.exports = {
  KINDS, DOORS, WINDOW_MS, PHONE_PREFIX, ERASED_PREFIX, ALL,
  refOf, phoneRef, record, noteLookup, noteApp, settle, stats, list, anonymize, sweep, retentionDays,
  //  برای آزمون
  _debtorIdsByPhone: debtorIdsByPhone, _counters: counters,
};
