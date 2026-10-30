-- DLV-03 (P1): سقفِ کارِ هم‌زمانِ هر پیک — سرور سپردنِ تازه به پیکی که به سقف رسیده را
-- رد می‌کند (۴۰۹ `courier_busy`) مگر مدیر آگاهانه `overrideLiveCap` بزند (در رویداد ثبت).
-- NULL = بی‌سقف (همهٔ ردیف‌های پیشین ⇒ رفتارِ امروز). مأموریتِ زنده همان شمارشِ `cashStats` است.
-- فقط افزودنی — هیچ داده‌ای عوض نمی‌شود. برگشت: ALTER TABLE shop_couriers DROP COLUMN IF EXISTS max_live;
ALTER TABLE shop_couriers ADD COLUMN IF NOT EXISTS max_live integer
  CHECK (max_live IS NULL OR (max_live >= 1 AND max_live <= 50));
