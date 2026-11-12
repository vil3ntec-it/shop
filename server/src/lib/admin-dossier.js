'use strict';
/**
 * TEN-04 — بخشِ خصوصیِ دو پروندهٔ آمیختهٔ مدیر (`GET /api/admin/shops/:id` و `GET /api/admin/users/:id`، `routes/admin.js`)
 * پشتِ پرچمِ `security / admin.support_grant` (`lib/support-access.js` › `grantForDossier`):
 *
 *   خاموش (پیش‌فرض) ⇒ همان امروز: همه چیز، با سابقهٔ دیدن.
 *   روشن + اجازهٔ زندهٔ `read_private` ِ همین آدم روی همان دکان (برای کاربر: یکی از دکان‌هایی که عضوش است) ⇒ همه چیز،
 *          شمارِ استفاده و شناسهٔ اجازه در سابقهٔ همان در.
 *   روشن + بی اجازه ⇒ فقط فراداده (اشتراک، شمارها، اعضا و نقش‌ها، نام و تماسی که فهرست‌های مدیر هم می‌دهند) و
 *          `restricted: { code: 'grant_required', hidden }` تا پنل بگوید چرا خالی است — نه ۴۰۳: مالی اجازه نمی‌گیرد و
 *          پروندهٔ دکان همان جایی است که پشتیبانی از آن اجازه می‌خواهد.
 *
 * پنهان‌ها همان‌هایی‌اند که هیچ فهرستی نمی‌دهد: جای دقیق (دکان = آخرین نقطهٔ صاحبش؛ کاربر = آخرین نقطه و تاریخچه) و
 * دستگاه‌های کاربر. کاربری که عضوِ هیچ دکانی نیست (پمپ، …) بیرون از اجازه‌های دکان است ⇒ همان امروز.
 */
const { one, many } = require('../db');
const access = require('./support-access');

const restricted = (hidden) => ({ code: 'grant_required', hidden });
const auditOf = (grant) => (grant ? { detail: { grant } } : {});

/**
 *  لوکیشنِ دکان — آخرین نقطه‌ای که از صاحبش رسیده. دکان خودش ستونِ لوکیشن ندارد؛ نقطه‌ها به کاربر بسته‌اند (همان‌هایی
 *  که هنگام ثبت‌نام و بعدش فرستاده می‌شوند). پس آخرینِ صاحبِ دکان را می‌دهیم — همان چیزی که مدیر می‌خواهد بداند: این دکان کجاست.
 */
async function placeOf(ownerUserId) {
  const p = await one(
    `SELECT lat, lng, accuracy, source, label, created_at
       FROM device_locations WHERE user_id=$1 ORDER BY created_at DESC LIMIT 1`,
    [ownerUserId]
  );
  return p
    ? {
      lat: Number(p.lat), lng: Number(p.lng), accuracy: p.accuracy === null ? null : Number(p.accuracy),
      source: p.source, label: p.label, at: Number(p.created_at),
    }
    : null;
}

/** پروندهٔ دکان ⇒ { location, audit, restricted? } */
async function shop(req, row) {
  const grant = await access.grantForDossier(req, [row.id], 'shop_dossier');
  if (grant === false) return { location: null, audit: {}, restricted: restricted(['location']) };
  return { location: await placeOf(row.owner_user_id), audit: auditOf(grant) };
}

/** پروندهٔ کاربر ⇒ { devices, locations, lastLocation, audit, restricted? } — `memberships` همان ردیف‌های `shop_members` ِ او */
async function user(req, row, memberships) {
  const shopIds = [...new Set(memberships.map((m) => String(m.shop_id)))];
  const grant = shopIds.length ? await access.grantForDossier(req, shopIds, 'user_dossier') : null;
  if (grant === false) {
    return { devices: [], locations: [], lastLocation: null, audit: {}, restricted: restricted(['devices', 'locations', 'lastLocation']) };
  }
  const devices = await many('SELECT * FROM devices WHERE user_id=$1 ORDER BY last_seen_at DESC NULLS LAST', [row.id]);
  //  لوکیشن‌های همین حساب — تازه‌ترین اول. اگر دکان جابه‌جا شده باشد، از همین فهرست پیداست از کِی.
  const locations = await many(
    `SELECT lat, lng, accuracy, source, label, created_at
       FROM device_locations WHERE user_id=$1 ORDER BY created_at DESC LIMIT 10`,
    [row.id]
  );
  return {
    devices,
    locations: locations.map((r) => ({
      lat: Number(r.lat), lng: Number(r.lng), accuracy: Number(r.accuracy), source: r.source, label: r.label, at: Number(r.created_at),
    })),
    lastLocation: row.last_location_at
      ? { lat: Number(row.last_lat), lng: Number(row.last_lng), at: Number(row.last_location_at) }
      : null,
    audit: auditOf(grant),
  };
}

module.exports = { shop, user, placeOf };
