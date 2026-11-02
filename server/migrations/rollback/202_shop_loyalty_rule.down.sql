-- ============================================================
--  Rollback ِ 202_shop_loyalty_rule.sql — فقط با دست، پس از برگرداندنِ کد.
--
--  ⚠️ فقط قاعدهٔ یکسانِ امتیازِ دکان‌ها پاک می‌شود؛ دفترِ امتیاز (ردیف‌های `loyalty_*` ِ همگام‌سازی) دست نمی‌خورد و
--  هر دستگاه به قاعدهٔ خودش برمی‌گردد.
--
--  اجرا (در یک تراکنش):
--    psql "$DATABASE_URL" -1 -f migrations/rollback/202_shop_loyalty_rule.down.sql
-- ============================================================

DROP TABLE IF EXISTS shop_loyalty_rules;
DELETE FROM schema_migrations WHERE version = '202_shop_loyalty_rule.sql';
