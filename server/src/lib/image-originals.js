'use strict';
/**
 * اصلِ عکسِ بانک: سنجش، انبارِ بلاب و نشان‌های کیفیت (IMG-15) — جداشده از ‎lib/image-bank.js‎.
 *
 *   checkOriginal(buf, max, withQuality) ⇒ { info, flags } (+ metrics، dhash با پرچم)
 *   putBlob(c, buf, info, dhash)         ⇒ hash — یک بار با کلیدِ sha256؛ dhash فقط اگر هنوز نبوده
 *   near(c, f, dups)                     ⇒ { flags, near } — «مشابهِ احتمالی» در همان دامنهٔ دید
 *   saveNear(c, id, q)                   ⇒ کلیدهای تازهٔ پاسخ (فقط با پرچم)
 *   nearOut(row)                         ⇒ مشابه‌ها یا undefined (کلید در JSON نمی‌آید)
 *
 * ⛔ پرچمِ خاموشِ `image_bank.quality` (دکان یا `all`): خاموش ⇒ نه رمزگشایی، نه نوشتن در
 *    `image_blobs.dhash` / `images.near_duplicate_of` (مهاجرتِ ۱۹۴)، پاسخ‌ها همان پیشین.
 * ⛔ نشان‌ها فقط پیشنهادند (§۳۴.۲)؛ هیچ بارگذاری رد نمی‌شود. سنجه‌ها در ‎lib/image-quality.js‎.
 * ⛔ دامنهٔ دید مثلِ `duplicatesOf`: عکسِ دکان فقط با عکس‌های همان دکان، عکسِ بانک با central+pending.
 *    شناسهٔ عکسِ دکان هرگز به بانک نمی‌رود: پیشنهاد به بانک «مشابه» ِ دکان را برمی‌دارد و دوباره
 *    در دامنهٔ بانک می‌سنجد.
 */
const crypto = require('crypto');
const { now } = require('../db');
const bytes = require('./image-bytes');
const quality = require('./image-quality');
const { ApiError, badRequest } = require('../middleware/errors');

const MIN_SIDE = 300;            // کوچک‌تر ⇒ نشانِ «too_small» برای بازبین
const NEAR_SCAN = 2000;          // سقفِ ردیف‌های dHash که برای «مشابهِ احتمالی» سنجیده می‌شوند
const NEAR_MAX = 5;

const sha256 = (buf) => crypto.createHash('sha256').update(buf).digest('hex');
const tooLarge = (max) => new ApiError(413, 'too_large', `عکس بزرگ‌تر از ${Math.round(max / 1024)} کیلوبایت است`);
const json = (t, def) => { try { return JSON.parse(t); } catch { return def; } };

/**
 * سنجشِ اصل: نوع از محتوا، ابعاد، سقفِ حجم؛ نشانه‌های کیفیت برای بازبین.
 * `withQuality` ⇒ نشان‌های IMG-15 هم، با `metrics` و `dhash` ('-' = سنجیده ولی بی هش).
 */
function checkOriginal(buf, max, withQuality = false) {
  if (!Buffer.isBuffer(buf) || !buf.length) throw badRequest('عکس خالی است', 'image_empty');
  if (buf.length > max) throw tooLarge(max);
  const info = bytes.inspect(buf);
  const flags = [...info.warnings];
  if (Math.max(info.width, info.height) < MIN_SIDE) flags.push('too_small');
  if (!withQuality) return { info, flags };
  const q = quality.analyze(buf, info);
  return { info, flags: [...flags, ...q.flags], metrics: q.metrics, dhash: q.dhash || '-' };
}

/** `dhash`: '' = هنوز سنجیده نشده (پیش‌فرضِ ستون)، '-' = سنجیده ولی بی هش. */
async function putBlob(c, buf, info, dhash = '') {
  const hash = sha256(buf);
  await c.query(
    `INSERT INTO image_blobs (hash, mime, size, width, height, data, created_at)
     VALUES ($1,$2,$3,$4,$5,$6,$7) ON CONFLICT (hash) DO NOTHING`,
    [hash, info.mime, buf.length, info.width, info.height, buf, now()]
  );
  if (dhash) await c.query(`UPDATE image_blobs SET dhash=$2 WHERE hash=$1 AND dhash=''`, [hash, dhash]);
  return hash;
}

/** dHash ِ بلاب؛ بلابِ پیش از روشن شدنِ پرچم (دسته‌ای، پیشنهاد) همین‌جا یک بار سنجیده می‌شود. */
async function blobHash(c, hash) {
  const b = (await c.query('SELECT dhash, data, mime, width, height FROM image_blobs WHERE hash=$1', [hash])).rows[0];
  if (!b) return '-';
  if (b.dhash) return b.dhash;
  const dh = quality.analyze(Buffer.from(b.data), { mime: b.mime, width: b.width, height: b.height }).dhash || '-';
  await c.query(`UPDATE image_blobs SET dhash=$2 WHERE hash=$1 AND dhash=''`, [hash, dh]);
  return dh;
}

/**
 * «مشابهِ احتمالی» (IMG-15، IMG-BANK-13) برای ردیفِ تازهٔ `images` با بلابِ `f.hash`: تا ۵ شناسه به
 * ترتیبِ فاصله. همان محتوا (`dups`، sha256) این‌جا نمی‌آید. فقط نشان، نه ادغام.
 */
async function near(c, f, dups) {
  const flags = (f.flags || []).filter((x) => x !== 'near_duplicate');
  const dh = await blobHash(c, f.hash);
  if (dh === '-') return { flags, near: [] };
  const own = f.ownerShopId || '';
  const rows = (await c.query(
    `SELECT i.id, b.dhash FROM images i JOIN image_versions v ON v.image_id=i.id JOIN image_blobs b ON b.hash=v.blob_hash
      WHERE ${own ? 'i.owner_shop_id=$2' : `i.space IN ('central','pending')`} AND b.hash<>$1 AND b.dhash NOT IN ('','-')
      ORDER BY i.id DESC LIMIT ${NEAR_SCAN}`, own ? [f.hash, own] : [f.hash])).rows;
  const best = new Map();
  for (const r of rows) {
    if (dups.includes(r.id) || !quality.isNearDuplicate(dh, r.dhash)) continue;
    const d = quality.hamming(dh, r.dhash);
    if (!best.has(r.id) || d < best.get(r.id)) best.set(r.id, d);
  }
  const list = [...best].sort((a, b) => a[1] - b[1] || (a[0] < b[0] ? -1 : 1)).slice(0, NEAR_MAX).map(([id]) => id);
  return { flags: list.length ? [...flags, 'near_duplicate'] : flags, near: list };
}

async function saveNear(c, id, q) {
  if (q.near.length) await c.query('UPDATE images SET near_duplicate_of=$2 WHERE id=$1', [id, JSON.stringify(q.near)]);
  return { near_duplicate_of: q.near, quality_flags: q.flags };
}

/** پرچمِ خاموش هرگز نمی‌نویسد ⇒ '[]' ⇒ undefined ⇒ پاسخِ پیشین بی کلیدِ تازه */
const nearOut = (r) => {
  const v = json(r.near_duplicate_of, []);
  return v.length ? v : undefined;
};

module.exports = { checkOriginal, putBlob, near, saveNear, nearOut, tooLarge, sha256, MIN_SIDE };
