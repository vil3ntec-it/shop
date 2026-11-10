-- ============================================================
--  Rollback ِ 495_sync_conflict_restore_claims.sql — فقط با دست.
--
--  پیش از اجرا کدِ پیشین را برگردانید: کدِ تازه بی این جدول هر بازگرداندن را با خطای ۵xx رد می‌کند. هیچ داده‌ای از دست
--  نمی‌رود (نشان‌ها فقط تا پایانِ همان درخواست زنده‌اند؛ «برگردانده شد» در `sync_conflicts.restored_at` می‌ماند).
--
--  اجرا (در یک تراکنش):
--    psql "$DATABASE_URL" -1 -f migrations/rollback/495_sync_conflict_restore_claims.down.sql
-- ============================================================

DROP TABLE IF EXISTS sync_conflict_restore_claims;

DELETE FROM schema_migrations WHERE version = '495_sync_conflict_restore_claims.sql';
