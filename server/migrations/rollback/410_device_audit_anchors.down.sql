-- ============================================================
--  Rollback ِ 410_device_audit_anchors.sql — فقط با دست.
--
--  جدولِ لنگرها برداشته می‌شود؛ ردیف‌های سابقه (`sync_rows`) دست نمی‌خورند و سنجشِ سرور بی آن همان سنجشِ پیشین
--  است (ویرایش، حذف، جابه‌جایی، بازنویسی؛ فقط بریدنِ دُمِ همگام‌نشده دیگر دیده نمی‌شود). دستگاه‌ها پس از اجرای
--  دوبارهٔ ۴۱۰ سرِ تازه را دوباره می‌فرستند (لنگرِ کهنه‌تر برنمی‌گردد).
--
--  اجرا (در یک تراکنش):
--    psql "$DATABASE_URL" -1 -f migrations/rollback/410_device_audit_anchors.down.sql
-- ============================================================

DROP INDEX IF EXISTS ix_device_audit_anchors_device;
DROP TABLE IF EXISTS device_audit_anchors;

DELETE FROM schema_migrations WHERE version = '410_device_audit_anchors.sql';
