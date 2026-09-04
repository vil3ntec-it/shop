'use strict';
/**
 * لوکیشن دستگاه.
 *
 * قرار صاحب مخزن: «لوکیشن باید روشن باشد و ثبت شود، حتی اگر طرف اصلاً
 * ثبت‌نام نکرده باشد.» پس این لایه به حساب کاربری بند نیست: شناسه‌ی
 * دستگاه کافی است، و هر وقت همان دستگاه حساب ساخت، ردیف‌های قبلی‌اش هم
 * به آن حساب وصل می‌شوند.
 *
 * چیزی که ذخیره می‌شود فقط طول و عرض جغرافیایی و دقت است — نه نشانیِ
 * خانه، نه تاریخچه‌ی حرکت. تاریخچه هم برای این می‌ماند که اگر دکان جابه‌جا
 * شد، معلوم باشد از کِی.
 */
const { query, one, newId, now } = require('../db');
const v = require('./validate');
const { badRequest } = require('../middleware/errors');

const SOURCES = ['gps', 'network', 'manual', 'signup', 'startup', ''];

/** یک عدد اعشاری در بازه‌ی مشخص؛ هر چیز دیگری رد می‌شود. */
function coord(value, { field, min, max }) {
  const n = Number(value);
  if (!Number.isFinite(n) || n < min || n > max) {
    throw badRequest(`${field} معتبر نیست`, 'bad_location');
  }
  //  شش رقم اعشار ≈ ده سانتی‌متر. بیشتر از این نه دقتِ واقعی است و نه
  //  به درد می‌خورد؛ فقط ردیف را بزرگ‌تر می‌کند.
  return Math.round(n * 1e6) / 1e6;
}

/**
 * خواندن لوکیشن از بدنه‌ی درخواست.
 * اگر چیزی نیامده باشد null برمی‌گردد — لوکیشن هیچ‌جا اجباری نیست.
 */
function parse(raw) {
  if (!raw || typeof raw !== 'object') return null;
  if (raw.lat === undefined || raw.lng === undefined) return null;
  const source = String(raw.source || '').toLowerCase();
  return {
    lat: coord(raw.lat, { field: 'عرض جغرافیایی', min: -90, max: 90 }),
    lng: coord(raw.lng, { field: 'طول جغرافیایی', min: -180, max: 180 }),
    accuracy: Number.isFinite(Number(raw.accuracy)) ? Math.max(-1, Math.round(Number(raw.accuracy))) : -1,
    source: SOURCES.includes(source) ? source : '',
    label: v.text(raw.label, { max: 120 }),
  };
}

/**
 * ثبت یک لوکیشن.
 *
 * `deviceUid` یا `userId` — یکی از این دو باید باشد. اگر هیچ‌کدام نبود
 * ردیف بی‌صاحب می‌شد و به درد هیچ‌کس نمی‌خورد.
 */
async function record({ deviceUid = '', userId = '', ip = '' }, raw) {
  const loc = parse(raw);
  if (!loc) return null;
  const uid = v.id(deviceUid, { field: 'شناسه دستگاه', required: false, max: 64 });
  if (!uid && !userId) throw badRequest('شناسه دستگاه لازم است', 'device_required');

  const t = now();
  const row = await one(
    `INSERT INTO device_locations
       (id, device_uid, user_id, lat, lng, accuracy, source, label, ip, created_at)
     VALUES ($1,$2,$3,$4,$5,$6,$7,$8,$9,$10) RETURNING *`,
    [newId('loc'), uid, userId, loc.lat, loc.lng, loc.accuracy, loc.source, loc.label, ip, t]
  );
  if (userId) {
    await query(
      'UPDATE users SET last_lat=$2, last_lng=$3, last_location_at=$4 WHERE id=$1',
      [userId, loc.lat, loc.lng, t]
    );
  }
  return { id: row.id, lat: loc.lat, lng: loc.lng, at: t };
}

/**
 * ردیف‌هایی که پیش از ساختن حساب، از همین دستگاه ثبت شده‌اند را به
 * حساب تازه می‌چسباند.
 *
 * بدون این، لوکیشنی که موقع باز کردن برنامه گرفته شده بود برای همیشه
 * بی‌نام می‌ماند و صاحب سامانه فقط لوکیشنِ لحظه‌ی ثبت‌نام را می‌دید.
 */
async function claimDevice(deviceUid, userId) {
  const uid = v.id(deviceUid, { field: 'شناسه دستگاه', required: false, max: 64 });
  if (!uid || !userId) return 0;
  const r = await query(
    "UPDATE device_locations SET user_id=$2 WHERE device_uid=$1 AND user_id=''",
    [uid, userId]
  );
  return r.rowCount;
}

/** آخرین لوکیشنِ یک کاربر — برای پنل مدیریت. */
async function latestOf(userId) {
  return one(
    'SELECT * FROM device_locations WHERE user_id=$1 ORDER BY created_at DESC LIMIT 1',
    [userId]
  );
}

module.exports = { parse, record, claimDevice, latestOf, SOURCES };
