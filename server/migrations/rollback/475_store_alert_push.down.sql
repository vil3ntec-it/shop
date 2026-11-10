-- ============================================================
--  Rollback ِ 475_store_alert_push.sql — فقط با دست.
--
--  پوشِ وب ِ «خبرم کن» ِ کالا برداشته می‌شود؛ هشدارها همان رفتارِ پیش از ۴۷۵ را دارند (کانالِ `push` ِ «خبرم کن» بی
--  سرویس‌دهنده ⇒ `not_configured`). اشتراک‌های مرورگر روی سرور می‌روند؛ مرورگرِ مشتری اشتراکِ خودش را نگه می‌دارد ولی
--  دیگر پیامِ «خبرم کن» نمی‌گیرد (پوشِ پیگیریِ سفارش ِ ۴۷۰ دست نمی‌خورد).
--  پیش از اجرا کدِ پیشین را برگردانید (یا پرچمِ `shop.storefront.web_push` را خاموش کنید): درِ
--  `/api/store/:slug/alerts/push`، کارگرِ `storefront-alerts`، حذفِ دادهٔ مشتری و پاک کردنِ حساب بی این جدول ۵۰۰ می‌دهند.
--
--  اجرا (در یک تراکنش):
--    psql "$DATABASE_URL" -1 -f migrations/rollback/475_store_alert_push.down.sql
-- ============================================================

DROP INDEX IF EXISTS ix_store_alert_push_expires;
DROP INDEX IF EXISTS ix_store_alert_push_phone;
DROP TABLE IF EXISTS store_alert_push;

DELETE FROM schema_migrations WHERE version = '475_store_alert_push.sql';
