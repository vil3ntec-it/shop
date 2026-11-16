-- ============================================================
--  Rollback ِ 960_support_requests.sql — فقط با دست، پس از برگرداندنِ کد.
--  پیام‌های همان درخواست‌ها در رشتهٔ پشتیبانی (`support_messages`) می‌مانند؛ فقط ردیفِ ساخت‌یافته و کارهای «درستش کن» می‌روند.
--  ردیف‌های سابقه (`audit_logs`، `admin.support_request_*`) عمداً می‌مانند.
-- ============================================================
DROP TABLE IF EXISTS support_request_fixes;
DROP TABLE IF EXISTS support_requests;
DELETE FROM schema_migrations WHERE version = '960_support_requests.sql';
