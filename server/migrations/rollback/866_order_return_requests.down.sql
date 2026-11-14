-- ============================================================
--  Rollback ِ 866_order_return_requests.sql — فقط با دست.
--
--  پیش از اجرا پرچمِ `shop.storefront.return_requests` را برای همه خاموش کنید و کدِ پیشین را برگردانید (کدِ تازه با
--  CHECK ِ پیشین در نوشتنِ پیامِ درخواستِ برگشت خطا می‌گرفت). درخواست‌های برگشت، عکس‌هایشان و پیام‌هایشان پاک
--  می‌شوند؛ سندهای برگشتی که برنامهٔ دکان از راهِ برگشتِ موجود نوشته (`o-<سفارش>-rq-<درخواست>`) در دفترِ دکان می‌مانند.
--  در حالتِ انبارِ شیء شیءهای عکس می‌مانند تا `image-store-migrate gc --apply`.
--
--  اجرا (در یک تراکنش):
--    psql "$DATABASE_URL" -1 -f migrations/rollback/866_order_return_requests.down.sql
-- ============================================================

DELETE FROM customer_notify_outbox WHERE return_request_id IS NOT NULL;
ALTER TABLE customer_notify_outbox DROP CONSTRAINT IF EXISTS customer_notify_outbox_event_check;
ALTER TABLE customer_notify_outbox DROP CONSTRAINT IF EXISTS customer_notify_outbox_return_fk;
ALTER TABLE customer_notify_outbox DROP COLUMN IF EXISTS return_request_id;
ALTER TABLE customer_notify_outbox ADD CONSTRAINT customer_notify_outbox_event_check CHECK (
  (refund_id IS NULL AND event IN (
    'order_received', 'order_accepted', 'order_rejected', 'order_ready',
    'order_on_the_way', 'order_delivery_failed', 'order_delivery_retry',
    'order_completed', 'order_cancelled', 'order_late_apology'))
  OR (refund_id IS NOT NULL AND event = 'order_refund:' || refund_id));

DROP TABLE IF EXISTS order_return_request_photos;
DROP TABLE IF EXISTS order_return_requests;

DELETE FROM schema_migrations WHERE version = '866_order_return_requests.sql';
