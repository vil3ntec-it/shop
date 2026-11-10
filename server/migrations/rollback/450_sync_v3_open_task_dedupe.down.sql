-- ============================================================
--  Rollback ِ 450_sync_v3_open_task_dedupe.sql — فقط با دست.
--
--  فقط نمایه برداشته می‌شود؛ رفتار همان است و جست‌وجوی وظیفهٔ باز دوباره همهٔ بازهای دکان را می‌خواند.
--
--  اجرا (در یک تراکنش):
--    psql "$DATABASE_URL" -1 -f migrations/rollback/450_sync_v3_open_task_dedupe.down.sql
-- ============================================================

DROP INDEX IF EXISTS ix_sync_v3_conflicts_open_dedupe;

DELETE FROM schema_migrations WHERE version = '450_sync_v3_open_task_dedupe.sql';
