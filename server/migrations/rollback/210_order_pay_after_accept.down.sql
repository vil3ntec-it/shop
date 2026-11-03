-- ============================================================
--  Rollback ِ 210_order_pay_after_accept.sql — فقط با دست.
--
--  تنظیمِ «پرداخت پس از پذیرش» ِ ویترین‌ها فراموش می‌شود (همه به رفتارِ پیشین: پرداختِ آنلاین از «تازه»).
--  انتخاب‌های ثبت‌شدهٔ مشتری (رویدادِ `note` ِ `pay_choice`) و فاکتورهای سفارش (`note` ِ `invoice`) می‌مانند و
--  کدِ پیشین آن‌ها را نادیده می‌گیرد. پیش از اجرا کدِ پیش از این نسخه را برگردانید.
--
--  اجرا (در یک تراکنش):
--    psql "$DATABASE_URL" -1 -f migrations/rollback/210_order_pay_after_accept.down.sql
-- ============================================================

ALTER TABLE storefronts DROP COLUMN IF EXISTS pay_after_accept;
DELETE FROM schema_migrations WHERE version = '210_order_pay_after_accept.sql';
