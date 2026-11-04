-- ============================================================
--  Rollback ِ 211_order_pay_after_accept_default.sql — فقط با دست.
--
--  پیش‌فرضِ «پرداخت پس از پذیرش» ِ دکان‌های تازه فراموش می‌شود: ویترینی که از این پس ساخته شود همان پیش‌فرضِ
--  ستونِ `storefronts.pay_after_accept` (خاموش، مهاجرتِ ۲۱۰) را می‌گیرد. ویترین‌های ساخته‌شده و تنظیمِ صاحبانشان
--  دست نمی‌خورند. پیش از اجرا کدِ پیش از این نسخه را برگردانید.
--
--  اجرا (در یک تراکنش):
--    psql "$DATABASE_URL" -1 -f migrations/rollback/211_order_pay_after_accept_default.down.sql
-- ============================================================

ALTER TABLE shops DROP COLUMN IF EXISTS pay_after_accept_default;
DELETE FROM schema_migrations WHERE version = '211_order_pay_after_accept_default.sql';
