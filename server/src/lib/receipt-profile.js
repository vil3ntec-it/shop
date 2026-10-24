'use strict';
/**
 * سربرگِ فاکتورِ دکان (فاکتورِ نسخهٔ ۲): نام، نشانی، تلفن، متنِ پایین، QR روشن/خاموش
 * و لوگو — یک منبع برای همهٔ دستگاه‌های دکان و صفحهٔ عمومیِ QR.
 *
 * ── کجا ─────────────────────────────────────────────────────────────
 * • متن‌ها و نشانیِ لوگو: کلیدِ `receipt` در `shop_settings.data` (جدولِ موجود).
 *   فقط همین ماژول آن کلید را می‌نویسد، با ادغامِ اتمی (`||`)؛ `PUT /api/shop/settings`
 *   که کلِ سند را جایگزین می‌کند آن را دست نمی‌زند (`routes/shop.js`).
 * • بایت‌های لوگو: `image_blobs` (انبارِ محتوانشانیِ بانکِ تصاویر، `putBlob`)؛
 *   نوع فقط از محتوا (`image-bytes.inspect`) و فراداده (EXIF…) برداشته می‌شود.
 *
 * دستگاه‌ها سربرگ را می‌گیرند و برای چاپِ بی اینترنت نگه می‌دارند (ReceiptProfileStore).
 */
const db = require('../db');
const { one, now } = db;
const bytes = require('./image-bytes');
const { putBlob } = require('./image-bank');
const v = require('./validate');
const { badRequest } = require('../middleware/errors');

const KEY = 'receipt';
const LOGO_MAX_BYTES = 200 * 1024;
const LOGO_MAX_SIDE = 1024;
const TEXT_MAX = Object.freeze({ name: 80, address: 160, phone: 32, footer: 160 });
const LOGO_MODES = Object.freeze(['auto', 'line', 'photo']);
const DIGITS = Object.freeze(['latin', 'fa']);
const SYMBOLS = Object.freeze(['word', 'sign']);
const DEFAULTS = Object.freeze({
  name: '', address: '', phone: '', footer: '', qr: true,
  logoMode: 'auto', digits: 'latin', symbol: 'word', logo: null,
});

function asObject(x) {
  if (typeof x === 'string') { try { x = JSON.parse(x); } catch { return {}; } }
  return x && typeof x === 'object' && !Array.isArray(x) ? x : {};
}

function logoView(raw) {
  const l = asObject(raw);
  if (!/^[0-9a-f]{64}$/.test(String(l.hash || ''))) return null;
  return { hash: l.hash, mime: String(l.mime || ''), width: Number(l.width) || 0, height: Number(l.height) || 0 };
}

/** سندِ ذخیره‌شده ⇒ سربرگِ کامل با پیش‌فرض‌ها (مقدارِ ناشناخته ⇒ پیش‌فرض) */
function view(raw) {
  const r = asObject(raw);
  const text = (k) => (typeof r[k] === 'string' ? r[k].slice(0, TEXT_MAX[k]) : DEFAULTS[k]);
  const pick = (k, allowed) => (allowed.includes(r[k]) ? r[k] : DEFAULTS[k]);
  return {
    name: text('name'), address: text('address'), phone: text('phone'), footer: text('footer'),
    qr: r.qr !== false,
    logoMode: pick('logoMode', LOGO_MODES), digits: pick('digits', DIGITS), symbol: pick('symbol', SYMBOLS),
    logo: logoView(r.logo),
  };
}

/** بدنهٔ PUT ⇒ فقط کلیدهای آمده، سنجیده. لوگو از این راه نوشته نمی‌شود. */
function patchOf(body) {
  const b = asObject(body);
  const out = {};
  for (const k of Object.keys(TEXT_MAX)) {
    if (b[k] !== undefined) out[k] = v.text(b[k], { max: TEXT_MAX[k], field: k }).trim();
  }
  if (b.qr !== undefined) {
    if (typeof b.qr !== 'boolean') throw badRequest('qr باید true یا false باشد', 'bad_receipt_profile');
    out.qr = b.qr;
  }
  if (b.logoMode !== undefined) out.logoMode = v.oneOf(b.logoMode, LOGO_MODES, { field: 'logoMode' });
  if (b.digits !== undefined) out.digits = v.oneOf(b.digits, DIGITS, { field: 'digits' });
  if (b.symbol !== undefined) out.symbol = v.oneOf(b.symbol, SYMBOLS, { field: 'symbol' });
  if (!Object.keys(out).length) throw badRequest('هیچ تغییری فرستاده نشد', 'bad_receipt_profile');
  return out;
}

async function read(shopId) {
  const row = await one('SELECT data, rev FROM shop_settings WHERE shop_id=$1', [shopId]);
  return { profile: view(asObject(row?.data)[KEY]), rev: Number(row?.rev || 0) };
}

/** ادغامِ اتمیِ [patch] در `data.receipt`؛ بقیهٔ تنظیماتِ دکان دست نمی‌خورد. */
async function merge(shopId, patch) {
  const row = await one(
    `INSERT INTO shop_settings (shop_id, data, rev, updated_at)
     VALUES ($1, jsonb_build_object('${KEY}', $2::jsonb), 1, $3)
     ON CONFLICT (shop_id) DO UPDATE SET
       data = shop_settings.data || jsonb_build_object('${KEY}',
         CASE WHEN jsonb_typeof(shop_settings.data->'${KEY}') = 'object'
              THEN shop_settings.data->'${KEY}' ELSE '{}'::jsonb END || $2::jsonb),
       rev = shop_settings.rev + 1, updated_at = excluded.updated_at
     RETURNING data, rev`,
    [shopId, JSON.stringify(patch), now()]
  );
  return { profile: view(asObject(row.data)[KEY]), rev: Number(row.rev) };
}

const update = (shopId, body) => merge(shopId, patchOf(body));

/** لوگوی تازه: فقط JPG/PNG/WebP ِ سالم، حداکثر ۲۰۰ کیلوبایت و ۱۰۲۴ پیکسل، بی فراداده */
async function putLogo(shopId, buf) {
  if (!Buffer.isBuffer(buf) || !buf.length) throw badRequest('عکسِ لوگو خالی است', 'image_empty');
  if (buf.length > LOGO_MAX_BYTES) throw badRequest('لوگو حداکثر ۲۰۰ کیلوبایت', 'logo_too_large');
  const info = bytes.inspect(buf);
  if (info.width > LOGO_MAX_SIDE || info.height > LOGO_MAX_SIDE) {
    throw badRequest('لوگو حداکثر ۱۰۲۴ پیکسل در هر بُعد', 'logo_too_large');
  }
  const clean = bytes.stripMetadata(buf);
  const hash = await putBlob(db, clean, info);
  return merge(shopId, { logo: { hash, mime: info.mime, width: info.width, height: info.height } });
}

const removeLogo = (shopId) => merge(shopId, { logo: null });

/** بایت‌های یک لوگو از `image_blobs`، یا null */
async function blob(hash) {
  const b = await one('SELECT data, mime, hash FROM image_blobs WHERE hash=$1', [hash]);
  return b ? { data: Buffer.from(b.data), mime: b.mime, hash: b.hash } : null;
}

/** بایت‌های لوگوی دکان، یا null */
async function logoOf(shopId) {
  const { profile } = await read(shopId);
  return profile.logo ? blob(profile.logo.hash) : null;
}

module.exports = { KEY, LOGO_MAX_BYTES, LOGO_MAX_SIDE, DEFAULTS, view, patchOf, read, update, putLogo, removeLogo, blob, logoOf };
