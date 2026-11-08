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
  const body = req.body;
  if (typeof body !== 'object' || body === null || Array.isArray(body)) return fallback;
  return Buffer.isBuffer(body) ? body : fallback;
}

module.exports = { rawBody };
