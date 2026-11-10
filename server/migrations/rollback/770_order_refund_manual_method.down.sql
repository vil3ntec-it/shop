-- ============================================================
--  Rollback ِ 770_order_refund_manual_method.sql — فقط با دست.
--
--  CHECK ِ روشِ دفترِ بازپرداخت دوباره فقط `cash`. پیش از اجرا کدِ پیشین را برگردانید (یا `shop.orders.manual_refund`
--  را خاموش کنید). ⛔ ردیفِ ثبت‌شده با `hesabpay` پاک نمی‌شود (سابقهٔ مالی): اگر چنین ردیفی هست، این rollback
--  شکست می‌خورد و هیچ چیز عوض نمی‌شود — همان را بخواهید، چون آن پول واقعاً با حساب‌پی برگشته است.
--
--  اجرا (در یک تراکنش):
--    psql "$DATABASE_URL" -1 -f migrations/rollback/770_order_refund_manual_method.down.sql
-- ============================================================

ALTER TABLE online_order_refunds DROP CONSTRAINT IF EXISTS online_order_refunds_method_check;
ALTER TABLE online_order_refunds ADD CONSTRAINT online_order_refunds_method_check CHECK (method IN ('cash'));

DELETE FROM schema_migrations WHERE version = '770_order_refund_manual_method.sql';
