-- ============================================================
--  Rollback ِ 186_owner_reports.sql — فقط با دست، پس از برگرداندنِ کد.
--
--  ⚠️ تنظیمِ گزارشِ صاحبِ دکان و گزارش‌های فرستاده‌شده پاک می‌شوند؛ اعلان‌های همان گزارش‌ها
--  (notices / notice_deliveries) دست نمی‌خورند.
--
--  اجرا (در یک تراکنش):
--    psql "$DATABASE_URL" -1 -f migrations/rollback/186_owner_reports.down.sql
-- ============================================================

DROP TABLE IF EXISTS owner_reports;
DROP TABLE IF EXISTS owner_report_settings;
DELETE FROM schema_migrations WHERE version = '186_owner_reports.sql';
