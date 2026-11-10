-- ============================================================
--  Rollback ِ 640_shop_till_pins.sql — فقط با دست.
--
--  PIN ِ صندوق دوباره فقط روی هر دستگاه می‌ماند (همان رفتارِ پیش از ۶۴۰) و سرور نوبت‌های سنجیدهٔ صندوق را از دست
--  می‌دهد؛ هیچ فروش یا سندی دست نمی‌خورد. پیش از اجرا کدِ پیشین را برگردانید (یا پرچمِ `shop.till_pin` را خاموش کنید).
--
--  اجرا (در یک تراکنش):
--    psql "$DATABASE_URL" -1 -f migrations/rollback/640_shop_till_pins.down.sql
-- ============================================================

DROP INDEX IF EXISTS ix_shop_till_sessions_member;
DROP INDEX IF EXISTS ix_shop_till_sessions_started;
DROP INDEX IF EXISTS ux_shop_till_sessions_open;
DROP TABLE IF EXISTS shop_till_sessions;
DROP TABLE IF EXISTS shop_till_pins;

DELETE FROM schema_migrations WHERE version = '640_shop_till_pins.sql';
