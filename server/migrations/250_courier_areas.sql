-- ============================================================
--  ۲۵۰ — محدودهٔ خدمتِ هر پیک (S83، §۱۹.۱۲): «پیکِ خارج از محدوده ⇒ سپردنِ خودکار رد و سفارش به صفِ دستی».
--
--  `shop_couriers.areas`: فهرستِ نامِ ناحیه‌هایی که این پیک می‌برد (نامِ ناحیهٔ ارسالِ ویترین یا «ناحیه» ِ
--  نشانیِ سفارشِ تلفنی). مقایسه با کلیدِ `text-core.compareKey` در `lib/delivery-courier-area.js`، نه با شناسه:
--  شناسهٔ `storefront_zones` با هر ذخیرهٔ تنظیماتِ ارسال از نو ساخته می‌شود.
--  ⛔ فهرستِ خالی = همه‌جا. همهٔ ردیف‌های پیشین خالی می‌گیرند ⇒ رفتارِ امروز؛ هیچ داده‌ای عوض نمی‌شود.
--  rollback: migrations/rollback/250_courier_areas.down.sql
-- ============================================================

ALTER TABLE shop_couriers ADD COLUMN IF NOT EXISTS areas jsonb NOT NULL DEFAULT '[]'::jsonb;
ALTER TABLE shop_couriers DROP CONSTRAINT IF EXISTS shop_couriers_areas_check;
ALTER TABLE shop_couriers ADD CONSTRAINT shop_couriers_areas_check CHECK (
  CASE WHEN jsonb_typeof(areas) = 'array' THEN jsonb_array_length(areas) <= 30 ELSE false END);
