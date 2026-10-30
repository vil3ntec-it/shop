-- ============================================================
--  Rollback ِ 122_storefront_public_id.sql — فقط با دست، پس از برگرداندنِ کد.
--
--  ⚠️ public_id و نشانی‌های پیشین پاک می‌شوند. اگر پرچمِ `market.public_id`
--  روشن بوده و شناسه‌ها بیرون رفته‌اند (تبلیغ، API)، پیش از اجرا پشتیبان بگیرید:
--    COPY (SELECT shop_id, public_id FROM storefronts) TO …;
--  اجرای دوبارهٔ ۱۲۲ شناسهٔ تازه می‌سازد، نه همان.
--
--  اجرا (در یک تراکنش):
--    psql "$DATABASE_URL" -1 -f migrations/rollback/122_storefront_public_id.down.sql
-- ============================================================

DROP TABLE IF EXISTS storefront_slug_redirects;
DROP TRIGGER IF EXISTS storefronts_public_id_guard ON storefronts;
DROP FUNCTION IF EXISTS storefronts_public_id_guard();
DROP INDEX IF EXISTS ux_storefronts_public_id;
ALTER TABLE storefronts DROP CONSTRAINT IF EXISTS storefronts_public_id_format;
ALTER TABLE storefronts DROP COLUMN IF EXISTS public_id;
DROP FUNCTION IF EXISTS storefront_public_ulid(bigint);
DELETE FROM schema_migrations WHERE version = '122_storefront_public_id.sql';
