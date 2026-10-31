'use strict';
/**
 * بارگذاریِ مطمئنِ فایل در بانکِ تصاویر — IMG-14، IMG-PRO-06 (شرحِ بانک: ‎lib/image-bank.js‎).
 *
 *   verifyDigest(buf, claimed)   `X-Content-SHA256` ِ کلاینت: ناجور ⇒ ۴۲۲ پیش از هر نوشتن
 *   batchFiles(id, { after, limit })   فایل‌های رسیدهٔ یک دسته، برای ادامه پس از قطع
 *   resumeEnabled()              پرچمِ `image_bank.resume` (فقط `all`، پیش‌فرض خاموش) — رفتارِ پنل
 *
 * ⛔ بی سرآیند همه‌چیز همان پیشین است؛ سرآیندِ درست هیچ رفتاری را عوض نمی‌کند.
 * ⛔ ادامه فقط بر پایهٔ «همان نام + همان sha256 ِ موفق»، هرگز فقط نام.
 */
const crypto = require('crypto');
const { one, many } = require('../db');
const { ApiError, badRequest, notFound } = require('../middleware/errors');
const bank = require('./image-bank');

const RESUME_FLAG = 'image_bank.resume';
const FILES_PAGE_MAX = 1000;

const resumeEnabled = () => bank.flagOf(RESUME_FLAG, 'all');

/**
 * رسیدنِ کاملِ فایل: فایلِ نیمه‌رسیده (شبکهٔ ضعیف، پراکسیِ بُرنده) نه بلاب می‌سازد و نه
 * جای نسخهٔ سالمِ همان نام را می‌گیرد. بدشکل ⇒ ۴۰۰ `bad_checksum`.
 */
function verifyDigest(buf, claimed) {
  if (claimed == null || claimed === '') return;
  const want = String(claimed).trim().toLowerCase();
  if (!/^[0-9a-f]{64}$/.test(want)) throw badRequest('X-Content-SHA256 باید ۶۴ رقمِ هگز باشد', 'bad_checksum');
  const got = crypto.createHash('sha256').update(Buffer.isBuffer(buf) ? buf : Buffer.alloc(0)).digest('hex');
  if (got !== want) throw new ApiError(422, 'checksum_mismatch', 'فایل کامل نرسید (sha256 نمی‌خواند) — دوباره بفرستید');
}

/**
 * به ترتیبِ نام، صفحه‌به‌صفحه (`after` = نامِ آخرین ردیفِ صفحهٔ پیش). sha256 فقط برای
 * فایلِ «موفق» (همان کلیدِ بلاب)؛ فایلِ ناموفق دوباره فرستاده و سنجیده می‌شود.
 */
async function batchFiles(id, { after = '', limit } = {}) {
  const b = await one('SELECT id FROM image_batches WHERE id=$1', [String(id || '').slice(0, 40)]);
  if (!b) throw notFound('دسته پیدا نشد');
  const lim = Number(limit);
  const n = Number.isInteger(lim) && lim >= 1 && lim <= FILES_PAGE_MAX ? lim : FILES_PAGE_MAX;
  const rows = await many(
    `SELECT name, blob_hash, status, error, warnings FROM image_batch_files
      WHERE batch_id=$1 AND name > $2 ORDER BY name LIMIT $3`, [b.id, String(after || '').slice(0, 200), n + 1]);
  const items = rows.slice(0, n).map((r) => {
    let flags = [];
    try { flags = JSON.parse(r.warnings); } catch { flags = []; }
    return { name: r.name, status: r.status, error: r.error, sha256: r.blob_hash, quality_flags: flags };
  });
  return { items, next_after: rows.length > n ? items[items.length - 1].name : null };
}

module.exports = { RESUME_FLAG, FILES_PAGE_MAX, resumeEnabled, verifyDigest, batchFiles };
