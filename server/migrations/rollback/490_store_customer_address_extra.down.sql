-- ============================================================
--  Rollback ِ 490_store_customer_address_extra.sql — فقط با دست.
--
--  کلیدهای کمکیِ نشانیِ نام‌دار برداشته می‌شوند؛ نام، متن، نشانه و پین می‌مانند (همان رفتارِ پیش از ۴۹۰).
--  کدِ تازه بی این ستون‌ها هم می‌خواند (کلیدِ نبوده ⇒ خالی)؛ نوشتن با کلیدهای کمکی پیش از برگرداندنِ کد ۵۰۰ می‌دهد.
--
--  اجرا (در یک تراکنش):
--    psql "$DATABASE_URL" -1 -f migrations/rollback/490_store_customer_address_extra.down.sql
-- ============================================================

ALTER TABLE store_customer_addresses DROP COLUMN IF EXISTS area;
ALTER TABLE store_customer_addresses DROP COLUMN IF EXISTS gate_color;
ALTER TABLE store_customer_addresses DROP COLUMN IF EXISTS province;
ALTER TABLE store_customer_addresses DROP COLUMN IF EXISTS street;
ALTER TABLE store_customer_addresses DROP COLUMN IF EXISTS house;
ALTER TABLE store_customer_addresses DROP COLUMN IF EXISTS phone2;
ALTER TABLE store_customer_addresses DROP COLUMN IF EXISTS plus_code;

DELETE FROM schema_migrations WHERE version = '490_store_customer_address_extra.sql';
