-- ============================================================
--  Rollback ِ 208_purchase_bills.sql — فقط با دست.
--
--  فاکتورهای جدای تأمین‌کننده (PUR-14) پاک می‌شوند؛ سفارش‌های خرید، رسیدها و دفترِ دکان دست نمی‌خورند
--  (برنامه بی فاکتورِ جدا به تطبیق با ردیف‌های خریدِ دفتر برمی‌گردد). پیش از اجرا کدِ پیش از این نسخه را برگردانید.
--
--  اجرا (در یک تراکنش):
--    psql "$DATABASE_URL" -1 -f migrations/rollback/208_purchase_bills.down.sql
-- ============================================================

DROP TABLE IF EXISTS purchase_bills;
DELETE FROM schema_migrations WHERE version = '208_purchase_bills.sql';
