-- ============================================================
--  Rollback ِ 204_shop_hesabpay.sql — فقط با دست.
--
--  حساب‌پیِ هر دکان (حالت، شماره، کلیدهای رمزشده، رمزِ حساب‌پی) پاک می‌شود. پیش از اجرا کدِ پیش از این
--  نسخه را برگردانید (کدِ همین نسخه این جدول را می‌خواند).
--
--  اجرا (در یک تراکنش):
--    psql "$DATABASE_URL" -1 -f migrations/rollback/204_shop_hesabpay.down.sql
-- ============================================================

DROP TABLE IF EXISTS shop_hesabpay;
DELETE FROM schema_migrations WHERE version = '204_shop_hesabpay.sql';
