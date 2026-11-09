'use strict';
/**
 * پشتیبانِ سرورِ فاکتورهای نگه‌داشته — `/api/shop/held-invoices` (مهاجرتِ ۴۲۵؛ POS-12، S09).
 *
 * فاکتورِ نگه‌داشته (`HeldInvoiceStore` ِ برنامه) تا امروز فقط در فایلِ همان دستگاه بود؛ پاک کردنِ برنامه یا دستگاهِ
 * تازه همه را می‌برد و دستگاهِ دیگرِ دکان نمی‌دیدشان. حالا هر نگه‌داشته یک ردیف است (کلید: دکان + شناسهٔ فاکتور =
 * `SaleDraft.key`). برنامه با نگه‌داشتن یا ویرایش می‌فرستد (`PUT`) و با بازگرداندن، فروش یا دور انداختن برمی‌دارد
 * (`DELETE`، سنگِ قبر)؛ دستگاهِ تازه با `GET` همه را پس می‌گیرد.
 *
 * ── قاعدهٔ تعارض ─────────────────────────────────────────────────────────────
 * هر فاکتور جدا، با **نسخهٔ دستگاه** (`version`: ساعتِ ویرایشِ همان فاکتور و همیشه بیش از نسخهٔ پیشینی که همان
 * دستگاه دیده بود). نوشتن یا برداشتنِ با نسخهٔ کهنه‌تر هرگز روی تازه‌تر نمی‌نشیند: ۴۰۹ `held_stale` با ردیفِ کنونی
 * (`current`) تا برنامه همان را بنشاند. همان نسخه با همان بدنه ⇒ ۲۰۰ بی تغییر (تکرارِ درخواست بی‌خطر است)؛ برداشتنِ
 * دوباره ⇒ ۲۰۰ بی تغییر. سنگِ قبر نمی‌گذارد نوشتنِ دیررسیدهٔ دستگاهی دیگر فاکتورِ برگشته یا فروخته را زنده کند؛
 * سنگِ قبرِ کهنه‌تر از ۳۰ روز با نوشتنِ بعدیِ همان دکان پاک می‌شود. همهٔ نوشتن‌های یک دکان زیرِ یک قفل‌اند
 * (`rev` ِ پیاپی برای `GET ?since=`).
 *
 * ⛔ پیش‌فرض خاموش: پرچمِ `shop.held_sync`؛ خاموش ⇒ `GET` فقط `{enabled:false}`، `PUT`/`DELETE` ۴۰۳
 *    `held_sync_disabled` و هیچ ردیفی.
 * ⛔ دیدن: هر کسی که در صندوق می‌فروشد (`data.write`) همهٔ نگه‌داشته‌های دکان را می‌بیند — همان قاعدهٔ برگه‌های
 *    نگه‌داشتهٔ یک دستگاه که هر فروشنده‌اش همه را می‌بیند. دور انداختنِ (`reason=deleted`) نگه‌داشتهٔ کسِ دیگر
 *    `sales.cancel` می‌خواهد (همان «لغوِ فاکتور» ِ پاک کردنِ کهنه‌ها)؛ بازگرداندن و فروش برای هر فروشنده.
 * ⛔ shop_id فقط از عضویتِ توکن. سقف‌ها: ۶۰ نگه‌داشتهٔ زنده در هر دکان، ۳۲ کیلوبایت و ۲۰۰ ردیف در هر فاکتور.
 * ⛔ سرور بدنه را نمی‌خواند و با آن هیچ پولی نمی‌سازد؛ فقط شکل و اندازه را می‌سنجد.
 */
const { one, many, tx, now } = require('../db');
const { flagOf } = require('./orders');
const { badRequest, forbidden, conflict } = require('../middleware/errors');

const FLAG = 'shop.held_sync';
const ID_RE = /^[0-9A-Za-z_-]{1,64}$/;
const REASONS = Object.freeze(['resumed', 'sold', 'deleted']);
const MAX_LIVE = 60;
const MAX_BYTES = 32 * 1024;
const MAX_LINES = 200;
const MAX_TEXT = 512;
const PAGE = 200;
const TOMB_MS = 30 * 24 * 60 * 60 * 1000;
/** رشته‌های بالادستِ بدنه (`HeldInvoice`) */
const TEXTS = Object.freeze(['label', 'discount', 'note', 'device', 'payment', 'paid', 'debtorId', 'saleNote', 'priceList']);

const flagOn = (shopId) => flagOf(shopId, FLAG);

const plain = (v) => v !== null && typeof v === 'object' && !Array.isArray(v);
const bad = (m = 'فاکتورِ نگه‌داشته درست نیست') => badRequest(m, 'bad_held');

function idOf(raw) {
  if (typeof raw !== 'string' || !ID_RE.test(raw)) throw bad('شناسهٔ فاکتورِ نگه‌داشته درست نیست');
  return raw;
}

function versionOf(raw) {
  const v = typeof raw === 'string' && /^\d{1,16}$/.test(raw) ? Number(raw) : raw;
  if (!Number.isSafeInteger(v) || v <= 0) throw bad('نسخهٔ فاکتورِ نگه‌داشته درست نیست');
  return v;
}

/** بدنهٔ `HeldInvoice` ِ برنامه — فقط شکل و اندازه؛ سرور آن را نمی‌خواند */
function bodyOf(id, raw) {
  if (!plain(raw)) throw bad();
  if (raw.id !== id) throw bad('شناسهٔ بدنه با نشانی یکی نیست');
  if (raw.number !== undefined && (!Number.isInteger(raw.number) || raw.number < 0 || raw.number > 99)) throw bad();
  if (raw.heldAt !== undefined && !Number.isSafeInteger(raw.heldAt)) throw bad();
  for (const k of TEXTS) {
    const v = raw[k];
    if (v !== undefined && v !== null && (typeof v !== 'string' || v.length > MAX_TEXT)) throw bad();
  }
  const lines = raw.lines;
  if (!Array.isArray(lines) || lines.length === 0) throw bad('فاکتورِ نگه‌داشتهٔ بی ردیف');
  if (lines.length > MAX_LINES) throw badRequest(`بیش از ${MAX_LINES} ردیف در یک فاکتورِ نگه‌داشته`, 'held_too_large');
  for (const l of lines) {
    if (!plain(l) || typeof l.productId !== 'string' || l.productId.length > 128) throw bad();
    if (typeof l.quantity !== 'number' || !Number.isFinite(l.quantity)) throw bad();
    if (l.unitPrice !== undefined && l.unitPrice !== null && (typeof l.unitPrice !== 'number' || !Number.isFinite(l.unitPrice))) throw bad();
  }
  if (raw.coupons !== undefined && (!Array.isArray(raw.coupons) || raw.coupons.length > 50 || raw.coupons.some((c) => typeof c !== 'string' || c.length > 64))) throw bad();
  const text = JSON.stringify(raw);
  const bytes = Buffer.byteLength(text, 'utf8');
  if (bytes > MAX_BYTES) throw badRequest(`فاکتورِ نگه‌داشته از ${MAX_BYTES / 1024} کیلوبایت بزرگ‌تر است`, 'held_too_large');
  return { body: raw, text, bytes };
}

/** JSON ِ هم‌سنج (کلیدها مرتب) — jsonb ترتیبِ کلیدها را نگه نمی‌دارد */
function canon(v) {
  if (Array.isArray(v)) return `[${v.map(canon).join(',')}]`;
  if (plain(v)) return `{${Object.keys(v).sort().map((k) => `${JSON.stringify(k)}:${canon(v[k])}`).join(',')}}`;
  return JSON.stringify(v);
}

function view(r, me) {
  const base = { id: r.id, version: Number(r.version), rev: Number(r.rev) };
  if (r.deleted) return { ...base, deleted: true, reason: r.reason };
  return {
    ...base, body: r.body, mine: r.held_by === me, held_by: r.held_by,
    device_id: r.device_id, at: Number(r.updated_at),
  };
}

const COLS = 'id, body, version, deleted, reason, rev, created_by, held_by, device_id, updated_at';

/**
 * ردیف‌های تازه‌تر از [since] به ترتیبِ `rev`، حداکثر یک صفحه. `since = 0` (دستگاهِ تازه) ⇒ فقط زنده‌ها؛ پس از آن
 * سنگِ قبرها هم تا دستگاه بداند کدام برگشت، فروش رفت یا دور انداخته شد.
 */
async function read(shopId, since, me) {
  const shop = String(shopId);
  const from = Number.isSafeInteger(since) && since > 0 ? since : 0;
  const rows = await many(
    `SELECT ${COLS} FROM shop_held_invoices
      WHERE shop_id=$1 AND rev > $2::bigint AND (NOT deleted OR $2::bigint > 0)
      ORDER BY rev ASC LIMIT $3`,
    [shop, from, PAGE + 1],
  );
  const more = rows.length > PAGE;
  const page = rows.slice(0, PAGE);
  const head = await one('SELECT COALESCE(MAX(rev), 0) AS rev FROM shop_held_invoices WHERE shop_id=$1', [shop]);
  const rev = more ? Number(page[page.length - 1].rev) : Number(head ? head.rev : 0);
  const views = page.map((r) => view(r, me));
  return {
    rev, more,
    items: views.filter((v) => !v.deleted),
    gone: views.filter((v) => v.deleted),
    limits: { live: MAX_LIVE, bytes: MAX_BYTES, lines: MAX_LINES },
  };
}

async function locked(c, shop) {
  await c.query('SELECT pg_advisory_xact_lock(hashtext($1))', [`shop-held:${shop}`]);
  //  سنگِ قبرِ کهنه — همان دکان، با نوشتنِ بعدی
  await c.query('DELETE FROM shop_held_invoices WHERE shop_id=$1 AND deleted AND updated_at < $2', [shop, now() - TOMB_MS]);
}

async function nextRev(c, shop) {
  const r = (await c.query('SELECT COALESCE(MAX(rev), 0) AS rev FROM shop_held_invoices WHERE shop_id=$1', [shop])).rows[0];
  return Number(r.rev) + 1;
}

const stale = (cur, me) => Object.assign(
  conflict('نسخهٔ تازه‌تری از این فاکتورِ نگه‌داشته روی سرور است', 'held_stale'),
  { data: { current: view(cur, me) } },
);

/**
 * نوشتن (نگه‌داشتن یا ویرایش). نسخهٔ کهنه‌تر یا برابر با بدنهٔ دیگر ⇒ ۴۰۹ `held_stale`؛ همان نسخه و همان بدنه ⇒
 * بی تغییر. «نگه‌دارنده» (`held_by`) فقط با نگه‌داشتنِ تازه (ردیفِ نو یا پس از سنگِ قبر) عوض می‌شود، نه با ویرایش.
 * @returns {{ applied: boolean, item }}
 */
async function put(shopId, actor, rawId, raw) {
  const id = idOf(rawId);
  if (!plain(raw)) throw bad();
  const version = versionOf(raw.version);
  const { body, text, bytes } = bodyOf(id, raw.body);
  const deviceId = typeof raw.device_id === 'string' ? raw.device_id.slice(0, 64) : '';
  const shop = String(shopId);
  return tx(async (c) => {
    await locked(c, shop);
    const cur = (await c.query(`SELECT ${COLS} FROM shop_held_invoices WHERE shop_id=$1 AND id=$2`, [shop, id])).rows[0];
    if (cur && Number(cur.version) >= version) {
      if (!cur.deleted && Number(cur.version) === version && canon(cur.body) === canon(body)) {
        return { applied: false, item: view(cur, actor.userId) };
      }
      throw stale(cur, actor.userId);
    }
    if (!cur || cur.deleted) {
      const live = (await c.query('SELECT COUNT(*)::int AS n FROM shop_held_invoices WHERE shop_id=$1 AND NOT deleted', [shop])).rows[0].n;
      if (live >= MAX_LIVE) throw conflict(`بیش از ${MAX_LIVE} فاکتورِ نگه‌داشته برای یک دکان`, 'too_many_held');
    }
    const rev = await nextRev(c, shop);
    const at = now();
    const heldBy = cur && !cur.deleted ? cur.held_by : actor.userId;
    const row = (await c.query(
      `INSERT INTO shop_held_invoices
         (shop_id, id, body, bytes, version, deleted, reason, rev, created_by, held_by, updated_by, device_id, created_at, updated_at)
       VALUES ($1,$2,$3::jsonb,$4,$5,false,'',$6,$7,$8,$7,$9,$10,$10)
       ON CONFLICT (shop_id, id) DO UPDATE SET
         body=EXCLUDED.body, bytes=EXCLUDED.bytes, version=EXCLUDED.version, deleted=false, reason='', rev=EXCLUDED.rev,
         held_by=EXCLUDED.held_by, updated_by=EXCLUDED.updated_by, device_id=EXCLUDED.device_id, updated_at=EXCLUDED.updated_at
       RETURNING ${COLS}`,
      [shop, id, text, bytes, version, rev, actor.userId, heldBy, deviceId, at],
    )).rows[0];
    return { applied: true, item: view(row, actor.userId) };
  });
}

/**
 * برداشتن (سنگِ قبر). نبود یا پیش‌تر برداشته ⇒ بی تغییر؛ نسخهٔ کهنه‌تر از ردیفِ زنده ⇒ ۴۰۹ `held_stale`؛ دور
 * انداختنِ نگه‌داشتهٔ کسِ دیگر بی [canCancel] ⇒ ۴۰۳ `held_not_yours`.
 * @returns {{ deleted: boolean, others: boolean, version: number }}
 */
async function remove(shopId, actor, rawId, { version: rawVersion, reason: rawReason } = {}, canCancel = false) {
  const id = idOf(rawId);
  const version = versionOf(rawVersion);
  const reason = typeof rawReason === 'string' && REASONS.includes(rawReason) ? rawReason : null;
  if (!reason) throw bad('دلیلِ برداشتن (resumed، sold یا deleted) لازم است');
  const deviceId = typeof actor.deviceId === 'string' ? actor.deviceId.slice(0, 64) : '';
  const shop = String(shopId);
  return tx(async (c) => {
    await locked(c, shop);
    const cur = (await c.query(`SELECT ${COLS} FROM shop_held_invoices WHERE shop_id=$1 AND id=$2`, [shop, id])).rows[0];
    if (!cur || cur.deleted) return { deleted: false, others: false, version: cur ? Number(cur.version) : 0 };
    if (Number(cur.version) > version) throw stale(cur, actor.userId);
    const others = cur.held_by !== actor.userId;
    if (reason === 'deleted' && others && !canCancel) {
      throw forbidden('فاکتورِ نگه‌داشتهٔ کسِ دیگر را فقط کسی که اجازهٔ لغوِ فاکتور دارد دور می‌اندازد', 'held_not_yours');
    }
    const rev = await nextRev(c, shop);
    await c.query(
      `UPDATE shop_held_invoices SET deleted=true, body=NULL, bytes=0, reason=$3, version=$4, rev=$5,
         updated_by=$6, device_id=$7, updated_at=$8 WHERE shop_id=$1 AND id=$2`,
      [shop, id, reason, version, rev, actor.userId, deviceId, now()],
    );
    return { deleted: true, others, version, rev };
  });
}

module.exports = {
  FLAG, ID_RE, REASONS, MAX_LIVE, MAX_BYTES, MAX_LINES, PAGE, TOMB_MS,
  flagOn, bodyOf, canon, read, put, remove,
};
