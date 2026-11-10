-- ============================================================
--  Rollback ِ 520_order_review_notify.sql — فقط با دست.
--
--  ردیف‌های «سفارش به بررسی رفت» پاک می‌شوند (فقط خبر بودند؛ سفارش و نگه‌داشتن دست نمی‌خورند) و CHECK ِ ۲۱۳
--  برمی‌گردد. پیش از اجرا کدِ پیش از این نسخه را برگردانید (کدِ تازه با CHECK ِ پیشین در نوشتنِ همان ردیف خطا
--  می‌گرفت و تراکنشِ همگام‌سازی را پس می‌زد).
--
--  اجرا (در یک تراکنش):
--    psql "$DATABASE_URL" -1 -f migrations/rollback/520_order_review_notify.down.sql
-- ============================================================

DELETE FROM order_notify_outbox WHERE event = 'order_review';
ALTER TABLE order_notify_outbox DROP CONSTRAINT IF EXISTS order_notify_outbox_event_check;
ALTER TABLE order_notify_outbox ADD CONSTRAINT order_notify_outbox_event_check
  CHECK (event IN ('order_created', 'order_paid', 'order_cancelled'));

DELETE FROM schema_migrations WHERE version = '520_order_review_notify.sql';
