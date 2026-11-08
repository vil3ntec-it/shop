'use strict';
/**
 * بدنهٔ خامِ `express.raw` ⇒ همان Buffer؛ هر چیزِ دیگر (رشته، آرایه، شیئی که parser ِ دیگری
 * ساخته، یا بی بدنه) ⇒ `fallback`.
 *
 * ⛔ نوع پیش از `Buffer.isBuffer` با `typeof` و `Array.isArray` سنجیده می‌شود: CodeQL
 *    `Buffer.isBuffer` را نگهبانِ نوع نمی‌شناسد و `.length` ِ بعدی (مثلاً `putBlob`) را
 *    «type confusion through parameter tampering» می‌خواند. رفتار همان
 *    `Buffer.isBuffer(req.body) ? req.body : fallback` است، چون Buffer همیشه شیءِ ناتهی و غیرِآرایه است.
 */
function rawBody(req, fallback = Buffer.alloc(0)) {
  return bufferOf(req.body, fallback);
}

/**
 * همان نگهبان برای مقداری که از درخواست به یک تابعِ کتابخانه رسیده (`mediaBytes` ِ `putMedia` ِ
 * پشتیبانی و گفت‌وگو و `putEvidence` ِ پرونده): Buffer ⇒ خودش؛ هر چیزِ دیگر ⇒ `fallback`.
 */
function bufferOf(value, fallback = Buffer.alloc(0)) {
  if (typeof value !== 'object' || value === null || Array.isArray(value)) return fallback;
  return Buffer.isBuffer(value) ? value : fallback;
}

/** بایتِ رسانه: Buffer ِ ناتهی، وگرنه همان ۴۰۰ ِ `bad_media` ِ پیشین («فایل خالی است»). */
function mediaBytes(value) {
  const buf = bufferOf(value, null);
  if (!buf || !buf.length) throw require('../middleware/errors').badRequest('فایل خالی است', 'bad_media');
  return buf;
}

module.exports = { rawBody, bufferOf, mediaBytes };
