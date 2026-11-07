-- ============================================================
--  Rollback ِ 215_cluster_state.sql — فقط با دست.
--
--  هر دو جدول حالتِ گذرای خوشه‌اند (سقفِ نرخ، بلیتِ مصرف‌شده)؛ برداشتنشان هیچ دادهٔ کسب‌وکاری را نمی‌برد.
--  پیش از اجرا کدِ پیش از این نسخه را برگردانید یا `CLUSTER_ENABLED` و `RATE_LIMIT_STORE=pg` را بردارید.
--
--  اجرا (در یک تراکنش):
--    psql "$DATABASE_URL" -1 -f migrations/rollback/215_cluster_state.down.sql
-- ============================================================

DROP TABLE IF EXISTS live_ticket_uses;
DROP TABLE IF EXISTS rate_limit_hits;
DELETE FROM schema_migrations WHERE version = '215_cluster_state.sql';
