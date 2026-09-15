-- ---------- پیامکِ کدِ شش‌رقمی ----------
--  خواستهٔ صاحب مخزن: «باتِ ارسالِ خودکارِ کدِ شش‌رقمی داره یا نداره؟» تا امروز
--  کد فقط ایمیل می‌شد. حالا اگر شمارهٔ موبایل داده شود، همان لحظه با همان
--  سرویسِ پیامکی که کدِ ورود را می‌فرستد (SMS_API_*) پیامک هم می‌رود.
--  ⚠️ خودِ کد ذخیره نمی‌شود — فقط شماره و «رفت / نرفت».
ALTER TABLE station_vip_codes ADD COLUMN IF NOT EXISTS phone        text   NOT NULL DEFAULT '';
ALTER TABLE station_vip_codes ADD COLUMN IF NOT EXISTS sms_status   text   NOT NULL DEFAULT 'none';
ALTER TABLE station_vip_codes ADD COLUMN IF NOT EXISTS sms_error    text   NOT NULL DEFAULT '';
ALTER TABLE station_vip_codes ADD COLUMN IF NOT EXISTS sms_sent_at  bigint;

ALTER TABLE vip_codes ADD COLUMN IF NOT EXISTS phone        text   NOT NULL DEFAULT '';
ALTER TABLE vip_codes ADD COLUMN IF NOT EXISTS sms_status   text   NOT NULL DEFAULT 'none';
ALTER TABLE vip_codes ADD COLUMN IF NOT EXISTS sms_error    text   NOT NULL DEFAULT '';
ALTER TABLE vip_codes ADD COLUMN IF NOT EXISTS sms_sent_at  bigint;
