'use strict';
/**
 * لوگو و تصویرِ سرِ ویترین (M3، قراردادِ بازار §۱.۱). جدول: ‎migrations/133‎.
 *
 *   پرچم ── `shop.storefront.media` (scope = دکان یا `all`) — پیش‌فرض خاموش؛
 *           خاموش ⇒ هیچ ردیفی نوشته نمی‌شود و نمای عمومی `media` ِ تهی دارد.
 *   بایت ── همان خطِ `lib/image-pipeline.js`: `validate` (نوع فقط از محتوا،
 *           ابعاد، ساختار)، `strip_metadata` (بی EXIF/GPS/XMP؛ ICC می‌ماند)،
 *           `dedupe_hash`. سرور کوچک نمی‌کند؛ برنامه به اندازهٔ ثابتِ SPECS
 *           می‌سازد و این‌جا فقط سنجیده می‌شود.
 *   نسخه ── هر بارگذاری نسخهٔ تازه (هرگز تکراری)؛ همان محتوای نسخهٔ جاری ⇒
 *           همان نسخه (تکرارِ بی‌خطر). فقط دو نسخهٔ آخر نگه داشته می‌شوند.
 *
 * ⛔ shop_id فقط از نشستِ کارمند (route) یا ویترینِ روشن (عمومی)، هرگز از بدنه.
 */
const { one, many, now, tx } = require('../db');
const pipeline = require('./image-pipeline');
const { ApiError, badRequest } = require('../middleware/errors');

const FLAG_KEY = 'shop.storefront.media';
const MIMES = ['image/jpeg', 'image/png', 'image/webp'];
/** اندازه‌های ثابت — همتای برنامه: `core/image/StorefrontMediaSpec.kt` */
const SPECS = {
  logo: { ratio: 1, minW: 128, maxW: 512, bytes: 150 * 1024 },
  header: { ratio: 3, minW: 600, maxW: 1500, bytes: 400 * 1024 },
};
const KINDS = Object.keys(SPECS);
const MAX_BYTES = Math.max(...KINDS.map((k) => SPECS[k].bytes));
const RATIO_TOLERANCE = 0.02;
const KEEP = 2;

const flagOn = (shopId) => require('./orders').flagOf(shopId, FLAG_KEY);

function kindOf(v) {
  const k = String(v || '');
  if (!KINDS.includes(k)) throw badRequest('نوع فقط logo یا header', 'bad_kind');
  return k;
}

const tooLarge = (max) => new ApiError(413, 'too_large', `عکس بزرگ‌تر از ${Math.round(max / 1024)} کیلوبایت است`);

/** سنجش و پاک‌سازی؛ بایت‌های پاک و مشخصات — یا ۴۰۰/۴۱۳ بی هیچ اثری. */
function check(kind, buf) {
  const spec = SPECS[kind];
  if (!Buffer.isBuffer(buf) || !buf.length) throw badRequest('عکس خالی است', 'image_empty');
  if (buf.length > spec.bytes) throw tooLarge(spec.bytes);
  const info = pipeline.run('validate', buf);
  if (!MIMES.includes(info.mime)) throw badRequest('فقط JPG، PNG یا WebP', 'bad_type');
  if (info.orientation > 1) {
    throw badRequest('عکس باید پیش از فرستادن چرخانده شده باشد (EXIF orientation)', 'orientation_not_applied');
  }
  const r = info.width / info.height;
  if (Math.abs(r - spec.ratio) / spec.ratio > RATIO_TOLERANCE) {
    throw badRequest(kind === 'logo' ? 'لوگو باید مربع باشد' : 'تصویرِ سر باید سه به یک باشد', 'aspect_mismatch');
  }
  if (info.width < spec.minW) throw badRequest(`عرضِ ${kind} دست‌کم ${spec.minW} پیکسل`, 'too_small');
  if (info.width > spec.maxW) throw badRequest(`عرضِ ${kind} حداکثر ${spec.maxW} پیکسل`, 'too_big');
  const clean = pipeline.run('strip_metadata', buf);
  return { clean, info, hash: pipeline.run('dedupe_hash', clean) };
}

const view = (r, url) => (r ? {
  version: Number(r.version), mime: r.mime, width: Number(r.width), height: Number(r.height),
  bytes: Number(r.bytes), hash: r.hash, updatedAt: Number(r.created_at), url: url(r),
} : null);

async function currentRows(shopId) {
  const rows = await many(
    `SELECT kind, version, mime, width, height, bytes, hash, created_at
       FROM storefront_media WHERE shop_id=$1 AND current`, [shopId]);
  return Object.fromEntries(KINDS.map((k) => [k, rows.find((r) => r.kind === k) || null]));
}

/** نمای کارمند: نسخهٔ جاری و نشانیِ خواندنِ با توکن. */
async function staffView(shopId, base) {
  const cur = await currentRows(shopId);
  const out = {};
  for (const k of KINDS) out[k] = view(cur[k], (r) => `${base}/${k}/${r.version}`);
  return out;
}

/** نمای عمومی برای ویترینِ روشن؛ پرچم خاموش ⇒ هر دو null (بی خواندنِ جدول). */
async function publicView(sf) {
  const empty = Object.fromEntries(KINDS.map((k) => [k, null]));
  if (!(await flagOn(sf.shop_id))) return empty;
  const cur = await currentRows(sf.shop_id);
  const base = `/api/store/${encodeURIComponent(sf.slug)}/media`;
  const out = {};
  for (const k of KINDS) {
    const v = view(cur[k], (r) => `${base}/${k}/${r.version}`);
    out[k] = v && { version: v.version, width: v.width, height: v.height, mime: v.mime, url: v.url };
  }
  return out;
}

/** بارگذاری: نسخهٔ تازه ⇒ جاری. همان محتوای نسخهٔ جاری ⇒ همان، بی ردیفِ تازه. */
async function put(shopId, kind, buf, userId) {
  const { clean, info, hash } = check(kind, buf);
  return tx(async (c) => {
    //  دو بارگذاریِ هم‌زمانِ یک دکان/نوع پشتِ سرِ هم ⇒ شمارهٔ نسخه یکتا و یک جاری
    await c.query('SELECT pg_advisory_xact_lock(hashtext($1))', [`sf-media:${shopId}:${kind}`]);
    const cur = (await c.query(
      'SELECT version, hash FROM storefront_media WHERE shop_id=$1 AND kind=$2 AND current', [shopId, kind])).rows[0];
    if (cur && cur.hash === hash) return { kind, version: Number(cur.version), replayed: true };
    const last = (await c.query(
      'SELECT COALESCE(MAX(version),0) AS v FROM storefront_media WHERE shop_id=$1 AND kind=$2', [shopId, kind])).rows[0];
    const version = Number(last.v) + 1;
    await c.query('UPDATE storefront_media SET current=false WHERE shop_id=$1 AND kind=$2 AND current', [shopId, kind]);
    await c.query(
      `INSERT INTO storefront_media (shop_id, kind, version, mime, width, height, bytes, hash, data, current, created_by, created_at)
       VALUES ($1,$2,$3,$4,$5,$6,$7,$8,$9,true,$10,$11)`,
      [shopId, kind, version, info.mime, info.width, info.height, clean.length, hash, clean, String(userId || ''), now()]);
    await c.query('DELETE FROM storefront_media WHERE shop_id=$1 AND kind=$2 AND version<=$3',
      [shopId, kind, version - KEEP]);
    return { kind, version, replayed: false };
  });
}

/** برداشتن: هیچ نسخه‌ای جاری نیست (ردیف‌ها تا بارگذاریِ بعدی می‌مانند). */
async function remove(shopId, kind) {
  const rows = await many(
    'UPDATE storefront_media SET current=false WHERE shop_id=$1 AND kind=$2 AND current RETURNING version', [shopId, kind]);
  return { kind, removed: rows.length > 0 };
}

/** بایت‌های یک نسخهٔ مشخص؛ `onlyCurrent` برای عمومی (نسخهٔ کهنه دیده نمی‌شود). */
async function file(shopId, kind, version, { onlyCurrent = false } = {}) {
  const v = Number(version);
  if (!Number.isInteger(v) || v < 1 || v > 1e9) return null;
  const r = await one(
    `SELECT mime, hash, data FROM storefront_media WHERE shop_id=$1 AND kind=$2 AND version=$3${onlyCurrent ? ' AND current' : ''}`,
    [shopId, kind, v]);
  return r ? { mime: r.mime, hash: r.hash, data: Buffer.from(r.data) } : null;
}

module.exports = { FLAG_KEY, SPECS, KINDS, MIMES, MAX_BYTES, flagOn, kindOf, check, staffView, publicView, put, remove, file };
