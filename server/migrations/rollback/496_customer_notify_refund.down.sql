-- ============================================================
--  Rollback ِ 496_customer_notify_refund.sql — فقط با دست.
--
--  پیامِ هر بازپرداخت برداشته می‌شود؛ صفِ اعلانِ مشتری همان رفتارِ پیش از ۴۹۶ را دارد. ردیف‌های پیامِ بازپرداخت
--  (رفته یا در صف) پاک می‌شوند؛ دفترِ بازپرداخت (`online_order_refunds`) و دیگر پیام‌ها دست نمی‌خورند.
--  پیش از اجرا کدِ پیشین را برگردانید (یا `shop.orders.refund_tracking` را خاموش کنید): کدِ تازه بی ستونِ
--  `refund_id` پیامِ بازپرداخت را نمی‌نویسد و کارگر خطا می‌دهد.
--
--  اجرا (در یک تراکنش):
--    psql "$DATABASE_URL" -1 -f migrations/rollback/496_customer_notify_refund.down.sql
-- ============================================================

DELETE FROM customer_notify_outbox WHERE refund_id IS NOT NULL;
ALTER TABLE customer_notify_outbox DROP CONSTRAINT IF EXISTS customer_notify_outbox_event_check;
ALTER TABLE customer_notify_outbox ADD CONSTRAINT customer_notify_outbox_event_check CHECK (event IN (
  'order_received', 'order_accepted', 'order_rejected', 'order_ready',
  'order_on_the_way', 'order_delivery_failed', 'order_delivery_retry',
  'order_completed', 'order_cancelled', 'order_late_apology'));
ALTER TABLE customer_notify_outbox DROP CONSTRAINT IF EXISTS customer_notify_outbox_refund_fk;
ALTER TABLE customer_notify_outbox DROP COLUMN IF EXISTS refund_id;
ALTER TABLE online_order_refunds DROP CONSTRAINT IF EXISTS online_order_refunds_id_order_key;

DELETE FROM schema_migrations WHERE version = '496_customer_notify_refund.sql';
