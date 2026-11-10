-- ============================================================
--  Rollback ِ 491_storefront_funnel.sql — فقط با دست.
--
--  شمارِ بازدید/دیدن/سبدِ ویترینِ وب برداشته می‌شود؛ گزارش همان `visits: null` ِ پیش از ۴۹۱ را می‌دهد و تپشِ
--  ویترین بی‌صدا هیچ نمی‌نویسد (کدِ تازه بی این جدول‌ها نمی‌افتد).
--
--  اجرا (در یک تراکنش):
--    psql "$DATABASE_URL" -1 -f migrations/rollback/491_storefront_funnel.down.sql
-- ============================================================

DROP INDEX IF EXISTS ix_storefront_funnel_days_day;
DROP TABLE IF EXISTS storefront_funnel_days;
DROP INDEX IF EXISTS ix_storefront_funnel_seen_day;
DROP TABLE IF EXISTS storefront_funnel_seen;

DELETE FROM schema_migrations WHERE version = '491_storefront_funnel.sql';
