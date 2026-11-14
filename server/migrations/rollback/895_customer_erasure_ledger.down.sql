-- ============================================================
--  Rollback ِ 895_customer_erasure_ledger.sql — فقط با دست، پس از برگرداندنِ کد.
--  فرمان‌های هنوز انجام‌نشده گم می‌شوند (دفترِ دستگاه همان امروز می‌ماند)؛ هیچ دادهٔ دیگری دست نمی‌خورد.
-- ============================================================
DROP INDEX IF EXISTS ix_customer_erasure_ledger_pending;
DROP TABLE IF EXISTS customer_erasure_ledger;
DELETE FROM schema_migrations WHERE version = '895_customer_erasure_ledger.sql';
