-- ============================================================
--  Rollback ِ 203_ops_paging.sql — فقط با دست.
--
--  فقط جدول‌های خبرِ بحرانی (OPS-01@29) برداشته می‌شوند؛ خودِ هشدارها (`ops_alerts`) می‌مانند. پیش از اجرا پرچمِ
--  `ops.critical_alerts` را خاموش کنید؛ کدِ همین نسخه با پرچمِ خاموش این جدول‌ها را نمی‌خواند.
--
--  اجرا (در یک تراکنش):
--    psql "$DATABASE_URL" -1 -f migrations/rollback/203_ops_paging.down.sql
-- ============================================================

DROP TABLE IF EXISTS ops_alert_page_log;
DROP TABLE IF EXISTS ops_alert_pages;
DROP TABLE IF EXISTS ops_alert_recipients;
DELETE FROM schema_migrations WHERE version = '203_ops_paging.sql';
