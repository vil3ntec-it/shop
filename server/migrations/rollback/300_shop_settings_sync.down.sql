-- ============================================================
--  Rollback ِ 300_shop_settings_sync.sql — فقط با دست.
--
--  تنظیماتِ دکان دوباره فقط روی هر دستگاه و در پشتیبانِ کامل می‌مانند (همان رفتارِ پیش از ۳۰۰). هیچ دستگاهی چیزی
--  از دست نمی‌دهد: برنامه پیش از نوشتن هر کلید را روی خودِ دستگاه دارد. پیش از اجرا کدِ پیشین را برگردانید
--  (یا پرچمِ `shop.settings_sync` را خاموش کنید).
--
--  اجرا (در یک تراکنش):
--    psql "$DATABASE_URL" -1 -f migrations/rollback/300_shop_settings_sync.down.sql
-- ============================================================

DROP TABLE IF EXISTS shop_setting_keys_log;
DROP TABLE IF EXISTS shop_setting_keys;

DELETE FROM schema_migrations WHERE version = '300_shop_settings_sync.sql';
