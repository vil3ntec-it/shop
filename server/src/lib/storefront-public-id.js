'use strict';
/**
 * S3 ِ قراردادِ بازار: شناسهٔ عمومیِ پایدارِ دکان (`storefronts.public_id`) و نشانی‌های پیشین
 * (`storefront_slug_redirects`، مهاجرتِ ۱۳۱) پشتِ پرچمِ `market.public_id` (پیش‌فرض خاموش ⇒ پاسخ‌ها
 * همان امروزند). از `lib/storefront.js` جدا شد تا زیرِ سقفِ اندازه بماند؛ دروازهٔ ویترین (`open`)
 * همان پرونده است و تزریق می‌شود ⇒ دکانِ بسته از هیچ راهی (نشانیِ کهنه یا شناسه) پیدا نمی‌شود.
 */
const { one } = require('../db');
const orders = require('./orders');
const { conflict } = require('../middleware/errors');

const PUBLIC_ID_FLAG = 'market.public_id';
const PUBLIC_ID_RE = /^[0-9A-HJKMNP-TV-Z]{26}$/;

module.exports = ({ open, str, SLUG_RE }) => {
/** پرچمِ S3 برای این دکان — خاموش ⇒ پاسخ‌ها همان امروزند. */
function publicIdOn(shopId) {
  return orders.flagOf(shopId, PUBLIC_ID_FLAG);
}

/** ویترینِ باز ⇒ ردیف؛ ۴۰۴ ⇒ null. */
async function openOrNull(slug) {
  try {
    return await open(slug);
  } catch (err) {
    if (err && err.status === 404) return null;
    throw err;
  }
}

/**
 * نشانیِ پیشین ⇒ نشانیِ زندهٔ همان دکان، فقط وقتی پرچم روشن و ویترین باز است؛
 * وگرنه null (همان ۴۰۴ ِ امروز — دکانِ بسته از راهِ نشانیِ کهنه هم پیدا نمی‌شود).
 */
async function movedTo(slug) {
  const s = str(slug).toLowerCase();
  if (!SLUG_RE.test(s)) return null;
  const row = await one(
    `SELECT sf.shop_id, sf.slug FROM storefront_slug_redirects r
       JOIN storefronts sf ON sf.shop_id = r.shop_id WHERE r.old_slug = $1`,
    [s]
  );
  if (!row || row.slug === s || !(await publicIdOn(row.shop_id))) return null;
  return (await openOrNull(row.slug)) ? row.slug : null;
}

/** شناسهٔ عمومی ⇒ `{ publicId, slug }` ِ ویترینِ باز؛ وگرنه null. */
async function byPublicId(publicId) {
  const p = str(publicId).toUpperCase();
  if (!PUBLIC_ID_RE.test(p)) return null;
  const row = await one('SELECT shop_id, slug, public_id FROM storefronts WHERE public_id = $1', [p]);
  if (!row || !(await publicIdOn(row.shop_id)) || !(await openOrNull(row.slug))) return null;
  return { publicId: row.public_id, slug: row.slug };
}

/**
 * درونِ تراکنشِ `saveSettings` (زیرِ قفلِ `storefront-slugs`، پیش از نوشتنِ ردیف): نشانیِ دکانِ
 * دیگر ⇒ ۴۰۹ `slug_taken` (همان قاعدهٔ پیشین)؛ S3: نشانیِ زنده بر نشانیِ پیشین مقدم است و
 * نشانیِ رهاشدهٔ همین دکان (`oldSlug`) به آن می‌رسد. نوشتن بی پرچم هم هست؛ خواندنش با پرچم.
 */
async function claimSlug(c, shopId, oldSlug, slug, at) {
  const taken = (await c.query('SELECT 1 FROM storefronts WHERE slug = $1 AND shop_id <> $2', [slug, shopId])).rows[0];
  if (taken) throw conflict('این نشانی را دکانِ دیگری گرفته است', 'slug_taken');
  await c.query('DELETE FROM storefront_slug_redirects WHERE old_slug = $1', [slug]);
  if (oldSlug && oldSlug !== slug) {
    await c.query(
      `INSERT INTO storefront_slug_redirects (old_slug, shop_id, created_at) VALUES ($1,$2,$3)
       ON CONFLICT (old_slug) DO UPDATE SET shop_id = EXCLUDED.shop_id, created_at = EXCLUDED.created_at`,
      [oldSlug, shopId, at]
    );
  }
}

return { PUBLIC_ID_FLAG, PUBLIC_ID_RE, publicIdOn, movedTo, byPublicId, claimSlug };
};
