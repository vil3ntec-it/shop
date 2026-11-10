-- ============================================================
--  Rollback ِ 610_customer_access_app_door.sql — فقط با دست.
--
--  ردیف‌های درِ `app` (دیدنی که وب یا حسابدار از دفترِ خودش گزارش داد) پاک می‌شوند و CHECK ِ ۳۸۰ برمی‌گردد؛
--  ردیف‌های درهای سرور (`debtor`، `orders`) دست نمی‌خورند. پیش از اجرا کدِ پیش از این نسخه را برگردانید (کدِ
--  تازه با CHECK ِ پیشین هر `POST /api/shop/customer-access` را ۵۰۳ می‌گرفت؛ خواندنِ پرونده نمی‌افتاد).
--
--  اجرا (در یک تراکنش):
--    psql "$DATABASE_URL" -1 -f migrations/rollback/610_customer_access_app_door.down.sql
-- ============================================================

DELETE FROM customer_access_log WHERE door = 'app';
ALTER TABLE customer_access_log DROP CONSTRAINT IF EXISTS customer_access_log_door_check;
ALTER TABLE customer_access_log ADD CONSTRAINT customer_access_log_door_check
  CHECK (door IN ('debtor', 'orders'));

DELETE FROM schema_migrations WHERE version = '610_customer_access_app_door.sql';
