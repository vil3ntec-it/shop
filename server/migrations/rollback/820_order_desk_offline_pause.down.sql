-- ============================================================
--  Rollback ِ 820_order_desk_offline_pause.sql — فقط با دست.
--
--  ویترین دوباره هنگامِ قطعِ دکان همیشه «ثبت با پیامِ تأخیر» دارد (همان رفتارِ پیش از ۸۲۰)؛ بقیهٔ میزِ سفارش دست نمی‌خورد.
--  پیش از اجرا کدِ پیشین را برگردانید (یا پرچمِ `shop.orders.desk` را خاموش کنید).
--
--  اجرا (در یک تراکنش):
--    psql "$DATABASE_URL" -1 -f migrations/rollback/820_order_desk_offline_pause.down.sql
-- ============================================================

ALTER TABLE order_desk DROP COLUMN IF EXISTS offline_pause;

DELETE FROM schema_migrations WHERE version = '820_order_desk_offline_pause.sql';
