'use strict';
/**
 * عکسِ کالاها روی حساب — شرح در ‎migrations/045_product_photos.sql‎.
 *
 *   list(ctx, since)        ⇒ تغییرهای پس از rev (فقط نشانه، نه خودِ عکس)
 *   get(ctx, productId)     ⇒ { data, hash } یا null
 *   put(ctx, productId, buf, deviceId)
 *   putThumb(ctx, productId, buf, of)   ⇒ فقط دکان: نسخهٔ کارتِ اصلِ امروز (`routes/sync-photo-thumbs.js`)
 *   forStore(ctx, productId, size)      ⇒ ویترین: کارت (`thumb`) یا صفحه، با نسخهٔ نشانی
 *   remove(ctx, productId, deviceId)
 *
 * ⛔ فقط JPEG/PNG/WebP و حداکثر ۶۰۰ کیلوبایت — برنامه پیش از فرستادن عکس را
 *    ۸۰۰ پیکسلی و فشرده می‌کند (حدودِ ۴۰ تا ۸۰ کیلوبایت).
 * ⛔ سقفِ ۲۰٬۰۰۰ عکس برای هر دکان: جلوی پر کردنِ دیسکِ سرور.
 * ⛔ نسخهٔ کارت (مهاجرتِ ۳۵۰): سرور رمزگشا ندارد (`image-pipeline` ِ `resize` = missing)؛ برنامه ۳۲۰ پیکسلی می‌سازد و
 *    این‌جا همان زنجیره می‌سنجد (`checkThumb`). نسخه فقط برای همان اصلی سرو می‌شود که از آن ساخته شد (`of_hash`).
 *    فقط دکان (قیدِ جدول هم): پمپ همان پرس‌وجوها و همان پاسخِ پیش از ۳۵۰ را دارد (`isShop`).
 */
const crypto = require('crypto');
const { one, many, tx } = require('../db');
const store = require('./image-store');
const { SHOP } = require('./tenancy');
const { ApiError, badRequest: apiBad, notFound, conflict } = require('../middleware/errors');

const MAX_BYTES = 600 * 1024;
const MAX_PHOTOS = 20000;
//  نسخهٔ کارت: برنامه ≤ ۳۲۰px و ≤ ۳۲KB می‌سازد؛ سرور تا ۴۰۰px (همان `thumb` ِ بانک) و ۶۴KB می‌پذیرد
const THUMB_MAX_BYTES = 64 * 1024;
const THUMB_MAX_SIDE = 400;
const sha32 = (b) => crypto.createHash('sha256').update(b).digest('hex').slice(0, 32);
/** کارت فقط برای عکسِ دکان (`tenancy.SHOP.app`)؛ پمپ هیچ پرس‌وجو یا کلیدِ تازه‌ای نمی‌بیند */
const isShop = (ctx) => !!ctx && ctx.app === SHOP.app;
//  «کارتِ همین اصل هست؟» برای فهرست (فقط دکان) — ستون‌ها بی نامِ مستعار تا متنِ پرس‌وجوی پمپ همان بماند
const THUMB_COL = `, EXISTS (SELECT 1 FROM product_photo_thumbs t WHERE t.app = product_photos.app
      AND t.account_id = product_photos.account_id AND t.product_id = product_photos.product_id
      AND t.of_hash = product_photos.hash AND product_photos.hash <> '') AS thumb`;

function badRequest(message) { const e = new Error(message); e.status = 400; return e; }

//  نوع فقط از محتوا — یک نسخه برای همه، در `lib/image-store.js` (خواندنِ انبار هم همان را می‌سنجد)
const { sniff } = store;
//  بایتِ عکس در `data` یا در انبارِ شیء (`store_key`، مهاجرتِ ۲۵۵) — همیشه از `image-store`
const tenantOf = (ctx) => store.accountTenant(ctx.app, ctx.accountId);
const HAS = store.has();

function checkId(productId) {
  const id = String(productId || '');
  if (!/^[A-Za-z0-9_.:-]{1,64}$/.test(id)) throw badRequest('شناسهٔ کالا درست نیست');
  return id;
}

async function list(ctx, since = 0, limit = 500) {
  const rows = await many(
    `SELECT product_id, hash, size, rev, updated_at, (data IS NULL AND store_key = '') AS deleted${isShop(ctx) ? THUMB_COL : ''}
       FROM product_photos WHERE app=$1 AND account_id=$2 AND rev > $3
      ORDER BY rev ASC LIMIT $4`,
    [ctx.app, ctx.accountId, Number(since) || 0, Math.min(1000, Math.max(1, Number(limit) || 500))],
  );
  const items = rows.map(r => ({
    product_id: r.product_id, hash: r.hash, size: Number(r.size), rev: Number(r.rev),
    updated_at: Number(r.updated_at), deleted: !!r.deleted, ...(isShop(ctx) && { thumb: r.thumb === true }),
  }));
  return { items, head: items.length ? items[items.length - 1].rev : Number(since) || 0, has_more: items.length >= limit };
}

async function get(ctx, productId) {
  const r = await one(
    `SELECT data, store_key, hash FROM product_photos WHERE app=$1 AND account_id=$2 AND product_id=$3 AND ${HAS}`,
    [ctx.app, ctx.accountId, checkId(productId)],
  );
  const data = await store.bytes(tenantOf(ctx), r);
  return data ? { data, hash: r.hash } : null;
}

async function put(ctx, productId, buf, deviceId = '') {
  const id = checkId(productId);
  if (!Buffer.isBuffer(buf) || buf.length < 100) throw badRequest('عکس خالی است');
  if (buf.length > MAX_BYTES) throw badRequest('عکس بزرگ‌تر از ۶۰۰ کیلوبایت است');
  if (!sniff(buf)) throw badRequest('فقط عکسِ JPG، PNG یا WebP');
  const count = await one(
    `SELECT COUNT(*)::int AS n FROM product_photos WHERE app=$1 AND account_id=$2 AND ${HAS}`,
    [ctx.app, ctx.accountId],
  );
  if (Number(count?.n || 0) >= MAX_PHOTOS) {
    const exists = await one(`SELECT 1 FROM product_photos WHERE app=$1 AND account_id=$2 AND product_id=$3 AND ${HAS}`,
      [ctx.app, ctx.accountId, id]);
    if (!exists) throw badRequest('سقفِ عکس‌های این دکان پر است');
  }
  const hash = crypto.createHash('sha256').update(buf).digest('hex').slice(0, 32);
  //  شیءِ عکسِ پیشین (اگر بود) پس از نوشتنِ ردیف پاک می‌شود؛ کلیدش در همان تراکنش و با قفلِ ردیف خوانده می‌شود
  const row = await store.withStaged(tenantOf(ctx), buf, (s) => tx(async (c) => {
    const old = (await c.query('SELECT store_key FROM product_photos WHERE app=$1 AND account_id=$2 AND product_id=$3 FOR UPDATE',
      [ctx.app, ctx.accountId, id])).rows[0];
    const r = (await c.query(
      `INSERT INTO product_photos (app, account_id, product_id, data, store_key, hash, size, rev, updated_at, device_id)
       VALUES ($1,$2,$3,$4,$9,$5,$6,nextval('product_photo_rev'),$7,$8)
       ON CONFLICT (app, account_id, product_id) DO UPDATE
         SET data=EXCLUDED.data, store_key=EXCLUDED.store_key, hash=EXCLUDED.hash, size=EXCLUDED.size, rev=EXCLUDED.rev,
             updated_at=EXCLUDED.updated_at, device_id=EXCLUDED.device_id
       RETURNING rev`,
      [ctx.app, ctx.accountId, id, s.data, hash, buf.length, Date.now(), String(deviceId || '').slice(0, 64), s.key],
    )).rows[0];
    s.used = true;
    //  WEB-15 (فقط دکان): کارتِ اصلِ پیشین با اصلِ تازه می‌رود (همان اصل دوباره ⇒ کارتش می‌ماند)؛ کارتِ تازه پس از
    //  همین با `putThumb` می‌آید
    const th = isShop(ctx) ? (await c.query(
      `DELETE FROM product_photo_thumbs WHERE app=$1 AND account_id=$2 AND product_id=$3 AND of_hash <> $4 RETURNING store_key`,
      [ctx.app, ctx.accountId, id, hash])).rows[0] : null;
    return { rev: r.rev, old_key: old ? old.store_key : '', thumb_key: th ? th.store_key : '' };
  }));
  if (row.old_key) await store.drop(tenantOf(ctx), [row.old_key]);
  if (row.thumb_key) await store.drop(tenantOf(ctx), [row.thumb_key]);
  return { product_id: id, hash, size: buf.length, rev: Number(row.rev) };
}

async function remove(ctx, productId, deviceId = '') {
  const id = checkId(productId);
  const row = await tx(async (c) => {
    const old = (await c.query(`SELECT store_key FROM product_photos WHERE app=$1 AND account_id=$2 AND product_id=$3 AND ${HAS} FOR UPDATE`,
      [ctx.app, ctx.accountId, id])).rows[0];
    if (!old) return null;
    const r = (await c.query(
      `UPDATE product_photos SET data=NULL, store_key='', hash='', size=0, rev=nextval('product_photo_rev'), updated_at=$4, device_id=$5
        WHERE app=$1 AND account_id=$2 AND product_id=$3 RETURNING rev`,
      [ctx.app, ctx.accountId, id, Date.now(), String(deviceId || '').slice(0, 64)],
    )).rows[0];
    const th = isShop(ctx) ? (await c.query('DELETE FROM product_photo_thumbs WHERE app=$1 AND account_id=$2 AND product_id=$3 RETURNING store_key',
      [ctx.app, ctx.accountId, id])).rows[0] : null;
    return { rev: r.rev, old_key: old.store_key, thumb_key: th ? th.store_key : '' };
  });
  if (row && row.old_key) await store.drop(tenantOf(ctx), [row.old_key]);
  if (row && row.thumb_key) await store.drop(tenantOf(ctx), [row.thumb_key]);
  return { product_id: id, rev: row ? Number(row.rev) : 0, removed: !!row };
}

/* ------------------------------ نسخهٔ کارت ------------------------------ */
//  شرح: ‎migrations/350_product_photo_thumbs.sql‎. همان زنجیرهٔ `lib/image-pipeline.js`: `validate` (نوعِ واقعی از
//  محتوا، ساختارِ قطعه‌ها، CRC ِ PNG، سقفِ پیکسل/بمبِ فشرده) و `strip_metadata` (EXIF/GPS/XMP/IPTC؛ ICC می‌ماند)،
//  به‌علاوهٔ سقفِ بایت و ضلع، جهتِ اعمال‌شده، «بزرگ‌تر از اصل نه» و نسبتِ ابعادِ اصل (۲٪، همان بانکِ تصاویر).
//  همهٔ خطاهای کارت با پیشوندِ `thumb_` (برنامه همان ردیف را رد می‌کند و حلقه نمی‌زند).

/** ابعادِ ایستادهٔ اصل (EXIF ِ ۵ تا ۸ ⇒ جابه‌جا) — اصلِ ناخوانا ⇒ null (سنجشِ نسبت کنار می‌رود، بقیه نه) */
function uprightDims(original) {
  if (!Buffer.isBuffer(original)) return null;
  try {
    const i = require('./image-bytes').inspect(original);
    return i.orientation >= 5 && i.orientation <= 8 ? { w: i.height, h: i.width } : { w: i.width, h: i.height };
  } catch { return null; }
}

/** نسخهٔ کارت پیش از هر نوشتن ⇒ { buf (بی فراداده)، mime، width، height } یا خطای `thumb_*` */
function checkThumb(buf, original) {
  if (Array.isArray(buf) || typeof buf === 'string' || !Buffer.isBuffer(buf) || buf.length < 100) {
    throw apiBad('نسخهٔ کارت خالی است', 'thumb_empty');
  }
  if (buf.length > THUMB_MAX_BYTES) throw new ApiError(413, 'thumb_too_large', 'نسخهٔ کارت بزرگ‌تر از ۶۴ کیلوبایت است');
  if (!sniff(buf)) throw new ApiError(415, 'thumb_bad_type', 'نسخهٔ کارت فقط JPG، PNG یا WebP');
  const pipeline = require('./image-pipeline');
  let info;
  try { info = pipeline.run('validate', buf); } catch (e) {
    //  سقفِ پیکسل/بمبِ فشرده (۱۰٬۰۰۰ در یک بُعد یا ۴۰ مگاپیکسل، فقط از سرآیند) ⇒ همان «بزرگ»
    if (e && e.code === 'image_too_large_dimensions') throw apiBad('ابعادِ نسخهٔ کارت بیش از حد بزرگ است', 'thumb_too_big');
    throw apiBad(e && e.status === 400 ? e.message : 'فایلِ نسخهٔ کارت خراب است', 'thumb_corrupt');
  }
  //  کارتِ بریده نیمه‌خاکستری کشیده می‌شود ⇒ نه (برنامه همیشه کامل می‌سازد)
  if ((info.warnings || []).includes('truncated_suspect')) throw apiBad('نسخهٔ کارت ناقص است', 'thumb_corrupt');
  if (Math.max(info.width, info.height) > THUMB_MAX_SIDE) {
    throw apiBad(`بزرگ‌ترین ضلعِ نسخهٔ کارت حداکثر ${THUMB_MAX_SIDE} پیکسل`, 'thumb_too_big');
  }
  if (info.orientation > 1) {
    throw apiBad('نسخهٔ کارت باید پیش از فرستادن چرخانده شده باشد (EXIF orientation)', 'thumb_orientation_not_applied');
  }
  const src = uprightDims(original);
  if (src) {
    if (info.width > src.w || info.height > src.h) throw apiBad('نسخهٔ کارت از اصل بزرگ‌تر است', 'thumb_upscaled');
    const want = info.width * src.h / src.w;
    if (Math.abs(info.height - want) > Math.max(1, want * 0.02)) {
      throw apiBad('نسبتِ ابعادِ نسخهٔ کارت با اصل نمی‌خواند', 'thumb_aspect_mismatch');
    }
  }
  return { buf: pipeline.run('strip_metadata', buf), mime: info.mime, width: info.width, height: info.height };
}

async function writeThumb(c, ctx, id, ofHash, t, s) {
  await c.query(
    `INSERT INTO product_photo_thumbs (app, account_id, product_id, of_hash, data, store_key, hash, mime, width, height, size, updated_at)
     VALUES ($1,$2,$3,$4,$5,$6,$7,$8,$9,$10,$11,$12)
     ON CONFLICT (app, account_id, product_id) DO UPDATE
       SET of_hash=EXCLUDED.of_hash, data=EXCLUDED.data, store_key=EXCLUDED.store_key, hash=EXCLUDED.hash, mime=EXCLUDED.mime,
           width=EXCLUDED.width, height=EXCLUDED.height, size=EXCLUDED.size, updated_at=EXCLUDED.updated_at`,
    [ctx.app, ctx.accountId, id, ofHash, s.data, s.key, sha32(t.buf), t.mime, t.width, t.height, t.buf.length, Date.now()],
  );
}

/**
 * فقط نسخهٔ کارت، برای اصلی که امروز روی حساب است (`of` = همان `hash` ِ فهرست): عکسِ پیش از این قابلیت را برنامه
 * سرِ فرصت می‌سازد. اصلِ عوض‌شده ⇒ ۴۰۹ `thumb_stale` بی نوشتن؛ همان کارت دوباره ⇒ `unchanged` (بی‌اثر در تکرار).
 * `rev` بالا نمی‌رود: دستگاه‌های دیگر کارت را نمی‌گیرند (عکسِ کاملِ خودشان را دارند).
 */
async function putThumb(ctx, productId, buf, of) {
  if (!isShop(ctx)) throw new ApiError(403, 'shop_only', 'نسخهٔ کارتِ ویترین فقط برای دکان است');
  const id = checkId(productId);
  const want = typeof of === 'string' && /^[0-9a-f]{32}$/.test(of) ? of : '';
  if (!want) throw apiBad('نشانهٔ عکسِ اصل (of) درست نیست', 'thumb_bad_of');
  const orig = await one(`SELECT data, store_key, hash FROM product_photos WHERE app=$1 AND account_id=$2 AND product_id=$3 AND ${HAS}`,
    [ctx.app, ctx.accountId, id]);
  if (!orig) throw notFound('عکسی نیست', 'photo_not_found');
  if (orig.hash !== want) throw conflict('عکسِ اصل عوض شده است — کارت از عکسِ تازه ساخته شود', 'thumb_stale');
  const t = checkThumb(buf, await store.bytes(tenantOf(ctx), orig));
  const hash = sha32(t.buf);
  const row = await store.withStaged(tenantOf(ctx), t.buf, (s) => tx(async (c) => {
    const cur = (await c.query(`SELECT hash FROM product_photos WHERE app=$1 AND account_id=$2 AND product_id=$3 AND ${HAS} FOR UPDATE`,
      [ctx.app, ctx.accountId, id])).rows[0];
    if (!cur) throw notFound('عکسی نیست', 'photo_not_found');
    if (cur.hash !== want) throw conflict('عکسِ اصل عوض شده است — کارت از عکسِ تازه ساخته شود', 'thumb_stale');
    const had = (await c.query('SELECT store_key, of_hash, hash FROM product_photo_thumbs WHERE app=$1 AND account_id=$2 AND product_id=$3 FOR UPDATE',
      [ctx.app, ctx.accountId, id])).rows[0];
    if (had && had.of_hash === want && had.hash === hash) return { unchanged: true, old_key: '' };
    await writeThumb(c, ctx, id, want, t, s);
    s.used = true;
    return { unchanged: false, old_key: had && had.store_key !== s.key ? had.store_key : '' };
  }));
  await store.drop(tenantOf(ctx), [row.old_key]);
  return { product_id: id, of: want, hash, size: t.buf.length, width: t.width, height: t.height, mime: t.mime, unchanged: row.unchanged };
}

/**
 * ویترین: عکسِ همگام‌شده در اندازهٔ `thumb` (کارت) یا هر چیزِ دیگر (صفحهٔ کالا) — یا null.
 * `version` = نسخهٔ نشانی (`?v=`): اصل، و اگر کارتِ همان اصل هست `اصل:کارت` — رسیدنِ کارت نشانیِ تازه است، پس
 * نشانیِ کهنه‌ای که عکسِ کامل را یک روز در کش نگه داشته جای کارت نمی‌نشیند. کارتِ نبود/ناخوانا ⇒ همان عکسِ کامل.
 */
async function forStore(ctx, productId, size) {
  const id = checkId(productId);
  const big = size !== 'thumb';
  const r = await one(
    `SELECT p.hash, t.hash AS t_hash, t.mime AS t_mime${big ? '' : ', t.data AS t_data, t.store_key AS t_key'}
       FROM product_photos p
       LEFT JOIN product_photo_thumbs t ON t.app = p.app AND t.account_id = p.account_id AND t.product_id = p.product_id
                                       AND t.of_hash = p.hash
      WHERE p.app=$1 AND p.account_id=$2 AND p.product_id=$3 AND ${store.has('p.')}`,
    [ctx.app, ctx.accountId, id],
  );
  if (!r) return null;
  const version = r.t_hash ? `${r.hash}:${r.t_hash}` : r.hash;
  if (!big && r.t_hash) {
    const data = await store.bytes(tenantOf(ctx), { data: r.t_data, store_key: r.t_key });
    if (data) return { data, hash: r.t_hash, mime: r.t_mime, version };
  }
  const full = await get(ctx, id);
  if (!full) return null;
  return { ...full, version: full.hash === r.hash ? version : full.hash };
}

/* ------------------------------ صندوقِ عکس ------------------------------ */
//  شرح: ‎migrations/046_photo_inbox.sql‎. عکسِ دوربینِ گوشی تا ۲ مگابایت
//  (کامپیوتر خودش در استودیو کوچکش می‌کند)، حداکثر ۵۰ عکسِ منتظر برای هر دکان.
const INBOX_MAX_BYTES = 2 * 1024 * 1024;
const INBOX_MAX = 50;
const INBOX_TTL_MS = 24 * 3600 * 1000;

async function inboxPut(ctx, buf, { pair = '', productId = '', deviceId = '' } = {}) {
  if (!Buffer.isBuffer(buf) || buf.length < 100) throw badRequest('عکس خالی است');
  if (buf.length > INBOX_MAX_BYTES) throw badRequest('عکس بزرگ‌تر از ۲ مگابایت است');
  if (!sniff(buf)) throw badRequest('فقط عکسِ JPG، PNG یا WebP');
  const p = String(pair || '').replace(/\D/g, '').slice(0, 6);
  if (p.length !== 6) throw badRequest('کدِ جفت‌شدن درست نیست');
  const pid = productId ? checkId(productId) : '';
  const now = Date.now();
  for (const r of await many('DELETE FROM photo_inbox WHERE created_at < $1 RETURNING app, account_id, store_key', [now - INBOX_TTL_MS])) {
    if (r.store_key) await store.drop(store.accountTenant(r.app, r.account_id), [r.store_key]);
  }
  const count = await one('SELECT COUNT(*)::int AS n FROM photo_inbox WHERE app=$1 AND account_id=$2', [ctx.app, ctx.accountId]);
  if (Number(count?.n || 0) >= INBOX_MAX) throw badRequest('صندوقِ عکس پر است — عکس‌های رسیده را در کامپیوتر بردارید');
  const id = 'in_' + crypto.randomBytes(12).toString('hex');
  await store.withStaged(tenantOf(ctx), buf, async (s) => {
    await one(
      `INSERT INTO photo_inbox (id, app, account_id, pair, product_id, data, store_key, size, created_at, device_id)
       VALUES ($1,$2,$3,$4,$5,$6,$10,$7,$8,$9) RETURNING id`,
      [id, ctx.app, ctx.accountId, p, pid, s.data, buf.length, now, String(deviceId || '').slice(0, 64), s.key],
    );
    s.used = true;
  });
  return { id, pair: p, product_id: pid, size: buf.length, created_at: now };
}

async function inboxList(ctx, pair) {
  const p = String(pair || '').replace(/\D/g, '').slice(0, 6);
  const rows = await many(
    `SELECT id, pair, product_id, size, created_at FROM photo_inbox
      WHERE app=$1 AND account_id=$2 AND pair=$3 AND created_at >= $4 ORDER BY created_at ASC LIMIT 50`,
    [ctx.app, ctx.accountId, p, Date.now() - INBOX_TTL_MS],
  );
  return rows.map(r => ({ id: r.id, pair: r.pair, product_id: r.product_id, size: Number(r.size), created_at: Number(r.created_at) }));
}

async function inboxGet(ctx, id) {
  const r = await one('SELECT data, store_key, product_id FROM photo_inbox WHERE app=$1 AND account_id=$2 AND id=$3',
    [ctx.app, ctx.accountId, String(id || '').slice(0, 64)]);
  const data = await store.bytes(tenantOf(ctx), r);
  return data ? { data, product_id: r.product_id } : null;
}

async function inboxDelete(ctx, id) {
  const r = await one('DELETE FROM photo_inbox WHERE app=$1 AND account_id=$2 AND id=$3 RETURNING store_key',
    [ctx.app, ctx.accountId, String(id || '').slice(0, 64)]);
  if (r && r.store_key) await store.drop(tenantOf(ctx), [r.store_key]);
  return !!r;
}

module.exports = {
  list, get, put, remove, sniff, checkId, MAX_BYTES, MAX_PHOTOS, HAS,
  putThumb, forStore, checkThumb, THUMB_MAX_BYTES, THUMB_MAX_SIDE,
  inboxPut, inboxList, inboxGet, inboxDelete, INBOX_MAX_BYTES,
};
