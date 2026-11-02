-- ============================================================
--  Rollback ِ 192_storefront_promo_guard.sql — فقط با دست، پس از برگرداندنِ کد و وقتی
--  `shop.storefront.promo_guard` همه‌جا خاموش است.
--
--  ⚠️ تنظیمِ «اولین خرید» و دفترِ مصرفِ تخفیفِ ویترین پاک می‌شوند؛ خودِ سفارش‌ها (online_orders) و
--  تخفیفِ نشسته روی آن‌ها دست نمی‌خورند. پس از اجرای دوبارهٔ ۱۹۲، شماره‌ای که پیش‌تر اولین خرید
--  را گرفته بود با «سفارشِ پیشین» باز هم بیرون می‌ماند؛ فقط قیدِ دستگاه از نو آغاز می‌شود.
--
--  اجرا (در یک تراکنش):
--    psql "$DATABASE_URL" -1 -f migrations/rollback/192_storefront_promo_guard.down.sql
-- ============================================================

DROP TABLE IF EXISTS storefront_promo_claims;
DROP TABLE IF EXISTS storefront_promo_guard;
DELETE FROM schema_migrations WHERE version = '192_storefront_promo_guard.sql';
