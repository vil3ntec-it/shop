-- ============================================================
--  Rollback ِ 230_storefront_place.sql — فقط با دست.
--
--  نشانی و پینِ دکان (برای مشتریِ ویترین) فراموش می‌شوند؛ ویترین به رفتارِ پیشین برمی‌گردد (بی نشانی و نقشه).
--  پیش از اجرا کدِ پیش از این نسخه را برگردانید.
--
--  اجرا (در یک تراکنش):
--    psql "$DATABASE_URL" -1 -f migrations/rollback/230_storefront_place.down.sql
-- ============================================================

ALTER TABLE storefronts DROP COLUMN IF EXISTS pickup_lng;
ALTER TABLE storefronts DROP COLUMN IF EXISTS pickup_lat;
ALTER TABLE storefronts DROP COLUMN IF EXISTS pickup_address;
DELETE FROM schema_migrations WHERE version = '230_storefront_place.sql';
