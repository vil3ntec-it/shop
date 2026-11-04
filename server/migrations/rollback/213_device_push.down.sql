-- ============================================================
--  Rollback ِ 213_device_push.sql — فقط با دست.
--
--  ردیف‌های صفِ اعلانِ «پرداخت شد»/«مشتری لغو کرد» پاک می‌شوند (فقط خبر بودند؛ سفارش دست نمی‌خورد) و CHECK های
--  پیشین برمی‌گردند. توکن‌هایی که این مهاجرت یا کدِ تازه کهنه کرد کهنه می‌مانند (برنامه دوباره ثبت می‌کند).
--  پیش از اجرا کدِ پیش از این نسخه را برگردانید.
--
--  اجرا (در یک تراکنش):
--    psql "$DATABASE_URL" -1 -f migrations/rollback/213_device_push.down.sql
-- ============================================================

DELETE FROM order_notify_outbox WHERE event IN ('order_paid', 'order_cancelled');
ALTER TABLE order_notify_outbox DROP CONSTRAINT IF EXISTS order_notify_outbox_event_check;
ALTER TABLE order_notify_outbox ADD CONSTRAINT order_notify_outbox_event_check CHECK (event IN ('order_created'));
ALTER TABLE order_notify_outbox DROP CONSTRAINT IF EXISTS order_notify_outbox_status_check;
ALTER TABLE order_notify_outbox ADD CONSTRAINT order_notify_outbox_status_check CHECK (status IN (
  'pending', 'sending', 'provider_accepted', 'acked', 'failed', 'unacked', 'cancelled'));
DROP INDEX IF EXISTS idx_push_device;
ALTER TABLE push_tokens DROP COLUMN IF EXISTS stale_at;
ALTER TABLE push_tokens DROP COLUMN IF EXISTS stale_reason;
DELETE FROM schema_migrations WHERE version = '213_device_push.sql';
