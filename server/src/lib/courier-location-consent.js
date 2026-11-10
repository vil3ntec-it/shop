'use strict';
/**
 * S94 (§۲۱) — رضایتِ خودِ پیک برای موقعیتِ زنده: «بدونِ رضایت ردیابیِ لحظه‌ای فعال نشود؛ لغوِ رضایت اثرِ بعدی را
 * متوقف کند و دادهٔ قبلی پاک شود.»
 *
 *   پرچم ── `shop.delivery_live_location.consent` (همان قاعدهٔ `orders.flagOf`؛ ⛔ پیش‌فرض خاموش). خاموش ⇒ همان رفتارِ
 *           امروز: پرچمِ دکان (`shop.delivery_live_location`) بس است و ستونِ رضایت خوانده نمی‌شود.
 *   رضایت ─ `shop_couriers.location_consent_at` (مهاجرتِ ۷۵۰)، فقط خودِ پیک از `PUT /api/courier/location-consent`
 *           (`{granted: true|false}`) — نه صاحب، نه مدیر، نه پنل. همان حالِ پیشین ⇒ `unchanged` و بی ردیفِ سابقه.
 *   روشن ── بی رضایت `putLocation` ۴۰۳ ِ `location_consent_required` و هیچ نقطه‌ای نمی‌نشیند؛ نمای مشتری موقعیت را
 *           نشان نمی‌دهد (حتی اگر نقطه‌ای مانده باشد).
 *   لغو ─── در همان تراکنش همهٔ نقطه‌های ذخیره‌شدهٔ مأموریت‌های همین پیک پاک می‌شوند (`purged`) — نقطهٔ پیشین دیگر به
 *           مشتری نمی‌رسد و از نگهداریِ ده‌دقیقه‌ای هم زودتر می‌رود. هر «بله» و «نه» یک ردیفِ سابقه
 *           (`courier.location_consent`) با حال و شمارِ پاک‌شده؛ بی مختصات.
 */
const { tx, now } = require('../db');
const orders = require('./orders');
const audit = require('./audit');
const { badRequest, ApiError } = require('../middleware/errors');

const FLAG = 'shop.delivery_live_location.consent';

const requiredOf = (shopId, run) => orders.flagOf(shopId, FLAG, run);
const at = (c) => (c && c.location_consent_at !== null && c.location_consent_at !== undefined ? Number(c.location_consent_at) : null);

/** حالِ رضایتِ این پیک برای `/api/courier/me`. */
async function stateOf(shopId, courier) {
  return { required: Boolean(await requiredOf(shopId)), granted: at(courier) !== null, grantedAt: at(courier) };
}

/**
 * پرچمِ روشن و بی رضایت ⇒ ۴۰۳ (درونِ تراکنشِ `putLocation`، روی ردیفِ تازهٔ پیک). SR46-2: `FOR SHARE` با `FOR UPDATE` ِ [set]
 * می‌جنگد ⇒ «نه» ِ نیمه‌کاره پیش از این خواندن تمام می‌شود (و این‌جا ۴۰۳)، یا این نقطه پیش از پاک کردنِ «نه» می‌نشیند و پاک می‌شود.
 */
async function assertIn(c, shopId, courierId) {
  if (!(await requiredOf(shopId, (t, p) => c.query(t, p)))) return;
  const r = (await c.query('SELECT location_consent_at FROM shop_couriers WHERE shop_id=$1 AND id=$2 FOR SHARE', [shopId, courierId])).rows[0];
  if (at(r) === null) {
    throw new ApiError(403, 'location_consent_required', 'برای فرستادنِ موقعیت، اول خودتان «اشتراکِ موقعیت» را روشن کنید');
  }
}

/** نمای مشتری: پرچمِ روشن و پیکِ بی رضایت ⇒ هیچ موقعیتی. */
async function visible(shopId, courierConsentAt) {
  if (!(await requiredOf(shopId))) return true;
  return courierConsentAt !== null && courierConsentAt !== undefined;
}

async function set(shopId, courier, body, { userId = '', ip = '' } = {}) {
  const b = body && typeof body === 'object' ? body : {};
  if (typeof b.granted !== 'boolean') throw badRequest('granted باید true یا false باشد', 'bad_consent');
  const out = await tx(async (c) => {
    const cur = (await c.query('SELECT location_consent_at FROM shop_couriers WHERE shop_id=$1 AND id=$2 FOR UPDATE',
      [shopId, courier.id])).rows[0];
    const was = at(cur);
    if ((was !== null) === b.granted) return { granted: b.granted, grantedAt: was, unchanged: true, purged: 0 };
    const t = now();
    await c.query('UPDATE shop_couriers SET location_consent_at=$3, updated_at=$4 WHERE shop_id=$1 AND id=$2',
      [shopId, courier.id, b.granted ? t : null, t]);
    let purged = 0;
    if (!b.granted) {
      purged = (await c.query(
        `DELETE FROM delivery_locations l USING delivery_assignments a
          WHERE l.assignment_id = a.id AND a.shop_id = $1 AND a.courier_id = $2`, [shopId, courier.id])).rowCount;
    }
    return { granted: b.granted, grantedAt: b.granted ? t : null, unchanged: false, purged };
  });
  if (!out.unchanged) {
    await audit.log({
      shopId, userId, action: 'courier.location_consent', targetType: 'shop_courier', targetId: courier.id,
      detail: { granted: out.granted, purged: out.purged }, ip,
    });
  }
  return out;
}

module.exports = { FLAG, requiredOf, stateOf, assertIn, visible, set };
