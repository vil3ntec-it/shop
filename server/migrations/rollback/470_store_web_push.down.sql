-- ============================================================
--  Rollback ِ 470_store_web_push.sql — فقط با دست.
--
--  پوشِ وب ِ مشتریِ ویترین برداشته می‌شود؛ پیامِ مشتری همان رفتارِ پیش از ۴۷۰ را دارد (کانالِ `push` بی سرویس‌دهنده ⇒
--  `not_configured`). اشتراک‌های مرورگر روی سرور می‌روند؛ مرورگرِ مشتری اشتراکِ خودش را نگه می‌دارد ولی دیگر پیامی نمی‌گیرد.
--  پیش از اجرا کدِ پیشین را برگردانید (یا پرچمِ `shop.storefront.web_push` را خاموش کنید): درِ
--  `/api/store/:slug/orders/:token/push` و کارگرِ `customer-notify` بی این جدول و ستون ۵۰۰ می‌دهند.
--
--  اجرا (در یک تراکنش):
--    psql "$DATABASE_URL" -1 -f migrations/rollback/470_store_web_push.down.sql
-- ============================================================

ALTER TABLE customer_notify_outbox DROP COLUMN IF EXISTS only_channel;
DROP INDEX IF EXISTS ix_store_push_subs_created;
DROP INDEX IF EXISTS ix_store_push_subs_order;
DROP TABLE IF EXISTS store_push_subs;

DELETE FROM schema_migrations WHERE version = '470_store_web_push.sql';
