'use strict';
/**
 * عکسِ QR ِ حساب‌پیِ دکان (تصمیمِ صاحب‌کار ۱۴۰۵/۰۸/۲۶ — «فعلاً API ِ حساب‌پی را نمی‌گیرم»؛ مهاجرتِ ۹۷۰).
 *
 *   صاحب عکس یا اسکرین‌شاتِ QR ِ حساب‌پیِ خودش را در «حساب‌پیِ دکان» بار می‌کند؛ گامِ پرداخت و نمایشگرِ مشتری همان عکس را
 *   بزرگ نشان می‌دهند (کنارِ متنِ QR ِ اسکن‌شده، نه به جایش). همان درِ تغییرِ گیرنده (`hesabpay-account.saveAccount`): دستگاهِ
 *   مطمئن + رمزِ حساب‌پی + تأییدِ دوبارهٔ ورود، سابقه، پوش، و ۲۴ ساعت «در انتظار» برای هر تغییر پس از نخستین حساب.
 *
 *   prepare(raw)            ⇒ { hash, mime, width, height, bytes } — عکسِ بار‌شده (base64، با یا بی `data:`) دوباره رمزگذاری:
 *                             چرخشِ EXIF روی پیکسل، شفافی روی سفید، ضلعِ بلند ≤ ۱۰۲۴، JPEG ِ بی EXIF/GPS؛ عکسی که رمزگشای
 *                             بی‌کتابخانهٔ سرور نمی‌خواند (JPEG ِ progressive، PNG ِ درهم) فقط اگر کوچک باشد، بی فراداده.
 *   storeIn(c, …) / pruneIn(c, …)   درونِ همان تراکنشِ ذخیرهٔ حساب؛ فقط عکسِ فعال و در انتظار می‌مانند.
 *   forTill(shopId, hash)   فقط عکسِ **فعالِ** حالتِ ساده (عکسِ حسابِ در انتظار هرگز به صندوق نمی‌رسد) ⇒ { mime, data } یا null.
 *
 * ⛔ عکس گیرندهٔ پول نیست: `parseAccount` فقط همراهِ متنِ QR ای می‌پذیردش که شمارهٔ درونش همان شمارهٔ حساب است (متن را
 *    برنامه از خودِ عکس خوانده)، و صندوق پیش از نشان دادن عکس را دوباره می‌خواند و فقط اگر همان متن بود نشانش می‌دهد.
 * ⛔ نوع فقط از محتوا (`image-bytes.inspect`)، سقفِ بایت و پیکسل پیش از رمزگشایی؛ هیچ فراداده‌ای (جا، دستگاه، زمان) نمی‌ماند.
 */
const crypto = require('crypto');
const { one } = require('../db');
const imageBytes = require('./image-bytes');
const codec = require('./image-codec');
const { badRequest } = require('../middleware/errors');

/** بایتِ خامِ بارشده (بدنهٔ JSON ِ سرور ۲ مگابایت است؛ برنامه پیش از فرستادن کوچکش می‌کند) */
const MAX_INPUT = 1536 * 1024;
/** ضلعِ بلندِ عکسِ ذخیره‌شده — QR ِ تمام‌صفحه در صندوق و نمایشگرِ مشتری هنوز تیز است */
const MAX_SIDE = 1024;
/** عکسی که سرور رمزگشایی نمی‌کند فقط تا این اندازه (بایت و ضلع) همان‌طور، بی فراداده */
const KEEP_MAX_BYTES = 768 * 1024;
const KEEP_MAX_SIDE = 2048;
const STORE_MAX = 2 * 1024 * 1024;
const HASH_RE = /^[0-9a-f]{64}$/;

const bad = (message, code = 'hesabpay_qr_image_bad') => badRequest(message, code);

/** base64 (با یا بی `data:image/…;base64,`) ⇒ Buffer؛ نویسهٔ بیگانه یا بیش از سقف ⇒ ۴۰۰ */
function bytesOf(raw) {
  if (typeof raw !== 'string') throw bad('عکسِ QR باید فایلِ عکس باشد.');
  let s = raw.trim();
  const m = /^data:([a-z0-9.+/-]+);base64,/i.exec(s);
  if (m) s = s.slice(m[0].length);
  s = s.replace(/\s+/g, '');
  if (!s || !/^[A-Za-z0-9+/_-]+={0,2}$/.test(s)) throw bad('عکسِ QR خراب است؛ دوباره انتخابش کنید.');
  if (Math.floor(s.length * 3 / 4) > MAX_INPUT + 3) throw bad('عکسِ QR بزرگ است (تا ۱٫۵ مگابایت).', 'hesabpay_qr_image_too_big');
  const buf = Buffer.from(s.replace(/-/g, '+').replace(/_/g, '/'), 'base64');
  if (buf.length > MAX_INPUT) throw bad('عکسِ QR بزرگ است (تا ۱٫۵ مگابایت).', 'hesabpay_qr_image_too_big');
  return buf;
}

/** شفافی روی سفید (QR ِ اسکرین‌شاتِ بی‌زمینه سیاه نشود) */
function flatten(img) {
  if (!img.alpha) return img;
  const rgb = Buffer.alloc(img.w * img.h * 3);
  for (let n = 0; n < img.w * img.h; n++) {
    const a = img.alpha[n] / 255;
    for (let c = 0; c < 3; c++) rgb[n * 3 + c] = Math.round(img.rgb[n * 3 + c] * a + 255 * (1 - a));
  }
  return { w: img.w, h: img.h, rgb, alpha: null };
}

/** عکسِ بارشده ⇒ عکسِ ذخیره‌شدنی با هشِ محتوا */
function prepare(raw) {
  const buf = bytesOf(raw);
  const info = imageBytes.inspect(buf);
  const img = codec.decode(buf, info);
  let out;
  if (img && !img.reason) {
    const small = codec.resize(flatten(codec.orient(img, info.orientation)), MAX_SIDE);
    out = { mime: 'image/jpeg', width: small.w, height: small.h, bytes: codec.encodeJpeg(small, 92) };
  } else {
    if (info.mime === 'image/webp') throw bad('عکسِ WebP پذیرفته نمی‌شود؛ JPG یا PNG بدهید.', 'hesabpay_qr_image_format');
    if (buf.length > KEEP_MAX_BYTES || Math.max(info.width, info.height) > KEEP_MAX_SIDE) {
      throw bad('این عکس خوانده نشد؛ اسکرین‌شاتِ QR (PNG) یا عکسِ کوچک‌ترِ JPG بدهید.', 'hesabpay_qr_image_format');
    }
    //  همان بایت‌ها بی هیچ فراداده (چرخش هم نه — QR از هر سو خوانده می‌شود)
    out = { mime: info.mime, width: info.width, height: info.height, bytes: imageBytes.stripMetadata(buf) };
  }
  if (!out.bytes.length || out.bytes.length > STORE_MAX) throw bad('عکسِ QR بزرگ است.', 'hesabpay_qr_image_too_big');
  return { ...out, hash: crypto.createHash('sha256').update(out.bytes).digest('hex') };
}

/** درونِ تراکنشِ ذخیره (همان عکس دوباره ⇒ همان ردیف) */
async function storeIn(c, shopId, img, t, by = '') {
  await c.query(
    `INSERT INTO hesabpay_qr_images (shop_id, hash, mime, width, height, bytes, created_at, created_by)
     VALUES ($1,$2,$3,$4,$5,$6,$7,$8) ON CONFLICT (shop_id, hash) DO NOTHING`,
    [shopId, img.hash, img.mime, img.width, img.height, img.bytes, t, String(by || '')]
  );
}

/** فقط عکس‌های `keep` (فعال و در انتظار) می‌مانند */
async function pruneIn(c, shopId, keep) {
  const k = [...new Set((keep || []).filter((h) => HASH_RE.test(String(h || ''))))];
  await c.query('DELETE FROM hesabpay_qr_images WHERE shop_id=$1 AND NOT (hash = ANY($2::text[]))', [shopId, k]);
}

/** صندوق: فقط عکسِ فعالِ حالتِ ساده با همین هش ⇒ `{ hash, mime, width, height, data }` (base64)، وگرنه null */
async function forTill(shopId, hash) {
  if (!HASH_RE.test(String(hash || ''))) return null;
  await require('./hesabpay-guard').activateDue(shopId);
  const r = await one(
    `SELECT i.mime, i.width, i.height, i.bytes FROM shop_hesabpay h
       JOIN hesabpay_qr_images i ON i.shop_id = h.shop_id AND i.hash = h.qr_image
      WHERE h.shop_id=$1 AND h.mode='simple' AND h.qr_image=$2`,
    [shopId, hash]
  );
  if (!r) return null;
  return { hash, mime: r.mime, width: Number(r.width), height: Number(r.height), data: Buffer.from(r.bytes).toString('base64') };
}

module.exports = { prepare, storeIn, pruneIn, forTill, MAX_INPUT, MAX_SIDE, HASH_RE };
