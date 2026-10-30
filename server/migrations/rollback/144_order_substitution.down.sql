-- برگشتِ مهاجرتِ ۱۴۴ (CHK-15). دستی و فقط پس از برگرداندنِ کدِ پیشین اجرا شود:
--   psql "$DATABASE_URL" -f migrations/rollback/144_order_substitution.down.sql
-- این پوشه را `src/migrate.js` نمی‌خواند (فقط *.sql ِ خودِ migrations).
-- ترجیحِ مؤثرِ هر قلم در `online_order_lines.substitution` می‌ماند؛ فقط ترجیحِ کلی و نامِ
-- جایگزینِ «کالای مشخص» پاک می‌شوند.
BEGIN;
ALTER TABLE online_order_lines DROP CONSTRAINT IF EXISTS online_order_lines_substitute_check;
ALTER TABLE online_order_lines DROP COLUMN IF EXISTS substitute_name;
ALTER TABLE online_order_lines DROP COLUMN IF EXISTS substitute_product_id;
ALTER TABLE online_orders DROP CONSTRAINT IF EXISTS online_orders_substitution_check;
ALTER TABLE online_orders DROP COLUMN IF EXISTS substitution;
DELETE FROM schema_migrations WHERE version = '144_order_substitution.sql';
COMMIT;
