-- ============================================================
--  Rollback ِ 250_courier_areas.sql — فقط با دست.
--
--  محدودهٔ خدمتِ پیک‌ها پاک می‌شود و هر پیک دوباره همه‌جا را می‌برد (رفتارِ پیش از ۲۵۰). مأموریت و رویدادها
--  دست نمی‌خورند (`outOfArea` ِ رویدادِ «created» فقط سابقه است). پیش از اجرا کدِ پیش از این نسخه را برگردانید.
--
--  اجرا (در یک تراکنش):
--    psql "$DATABASE_URL" -1 -f migrations/rollback/250_courier_areas.down.sql
-- ============================================================

ALTER TABLE shop_couriers DROP CONSTRAINT IF EXISTS shop_couriers_areas_check;
ALTER TABLE shop_couriers DROP COLUMN IF EXISTS areas;
DELETE FROM schema_migrations WHERE version = '250_courier_areas.sql';
