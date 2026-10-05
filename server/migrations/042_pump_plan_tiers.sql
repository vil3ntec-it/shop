-- ============================================================
--  ۰۴۲ — سه پلنِ پمپ، از نو (۱۴۰۵/۰۷/۲۰)
--
--  خواستهٔ صاحب سامانه:
--   ۱) استاندارد: کیو‌آر، اپِ گوشی، باتِ تلگرام، بکاپِ روی سرور و هر
--      اتصالِ دیگری به سرور بسته — «فقط از سرور اشتراک بتونه دریافت
--      کنه»؛ مفاد/ضرر تار؛ داشبورد و تاریخچه‌ها باز.
--   ۲) وی‌آی‌پی: همه‌چیز.
--   ۳) دائمی: همه‌چیز، ولی **خدماتِ سرور** (بکاپ، کیو‌آر، اپ، بات،
--      همگام‌سازی) سالِ اول رایگان و بعد فقط با تمدیدِ مدیر.
--
--  ⇒ ستونِ `services_until`: پایانِ خدماتِ سرورِ یک اشتراکِ دائمی.
--     null یعنی «از خودِ اشتراک حساب کن» (‎lib/pump-services.js‎) — پس
--     دائمی‌های امروزی بی هیچ نوشتنی «یک سال از شروع» می‌گیرند.
-- ============================================================
ALTER TABLE station_subscriptions ADD COLUMN IF NOT EXISTS services_until bigint;

--  تمدیدِ خدمات هم در تاریخچه می‌نشیند
ALTER TABLE station_subscription_history DROP CONSTRAINT IF EXISTS station_subscription_history_action_check;
ALTER TABLE station_subscription_history ADD CONSTRAINT station_subscription_history_action_check
  CHECK (action IN ('grant','renew','status','expire','discount','addon','permanent','services'));

--  استاندارد: بی خدماتِ سرور؛ داشبورد و تاریخچه باز. فقط اگر کسی دستی عوضش نکرده.
UPDATE plans SET features = '["dashboard","history","multi_device"]'::jsonb
 WHERE id = 'plan_pump_std' AND features = '["cloudbackup"]'::jsonb;

--  اشتراک‌های استانداردِ امروزی همان فهرستِ کپی‌شده را دارند
UPDATE station_subscriptions SET features = '["dashboard","history","multi_device"]'::jsonb
 WHERE plan = 'std' AND features = '["cloudbackup"]'::jsonb;
