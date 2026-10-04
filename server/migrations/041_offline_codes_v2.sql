-- ============================================================
--  ۰۴۱ — کدِ اشتراکِ آفلاین، نسخهٔ ۲ (۱۴۰۵/۰۷/۲۰)
--
--  «برای هر کامپیوتر و هر حساب متفاوت باشه… یک بار استفاده بشه.» کدِ
--  نسخهٔ ۲ کوتاه‌تر است (۱۳۸ نویسه، بود ۱۵۷) و جز کامپیوتر به یک حساب هم
--  بسته می‌شود. این‌جا فقط کدام حساب (برای دیدن در پنل و سنجشِ /redeem).
-- ============================================================
ALTER TABLE pump_offline_codes ADD COLUMN IF NOT EXISTS account_user_id text NOT NULL DEFAULT '';
ALTER TABLE pump_offline_codes ADD COLUMN IF NOT EXISTS account_email   text NOT NULL DEFAULT '';
ALTER TABLE pump_offline_codes ADD COLUMN IF NOT EXISTS version         integer NOT NULL DEFAULT 1;
