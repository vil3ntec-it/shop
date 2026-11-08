-- ============================================================
--  Rollback ِ 380_customer_access_log.sql — فقط با دست.
--
--  «سابقهٔ دسترسی» ِ درِ سرور (چه کسی پروندهٔ مشتری را دید) پاک می‌شود؛ هیچ دادهٔ دیگری دست نمی‌خورد.
--  ردیف‌های `customer_view` ِ دستگاه‌ها در `auditLog` ِ دفتر (همگام‌سازی) می‌مانند. پس از اجرا، کدِ همان
--  نسخه هنوز می‌نویسد ⇒ هر نوشتن خطا می‌دهد و فقط شمرده می‌شود (خواندن نمی‌افتد)؛ با کدِ پیشین برگردید.
--
--  اجرا (در یک تراکنش):
--    psql "$DATABASE_URL" -1 -f migrations/rollback/380_customer_access_log.down.sql
-- ============================================================

DROP TRIGGER IF EXISTS customer_access_log_append_only ON customer_access_log;
DROP FUNCTION IF EXISTS customer_access_log_guard();
DROP TABLE IF EXISTS customer_access_log;
DELETE FROM schema_migrations WHERE version = '380_customer_access_log.sql';
