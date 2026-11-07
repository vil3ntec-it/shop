-- ============================================================
--  Rollback ِ 227_cluster_instances.sql — فقط با دست.
--
--  جدولِ گذرای نبضِ نمونه‌ها است؛ برداشتنش هیچ دادهٔ کسب‌وکاری را نمی‌برد. پیش از اجرا کدِ پیش از این نسخه را
--  برگردانید یا `CLUSTER_ENABLED` را بردارید (وگرنه هر تپش فقط یک خطای لاگ می‌دهد).
--
--  اجرا (در یک تراکنش):
--    psql "$DATABASE_URL" -1 -f migrations/rollback/227_cluster_instances.down.sql
-- ============================================================

DROP TABLE IF EXISTS cluster_instances;
DELETE FROM schema_migrations WHERE version = '227_cluster_instances.sql';
