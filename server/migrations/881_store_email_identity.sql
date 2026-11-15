-- ============================================================
--  ۸۸۱ — هویتِ مشتریِ ویترین و برنامهٔ خرید = ایمیلِ تأییدشده («فقط ایمیل»، تصمیمِ صاحب‌کار ۱۴۰۵/۰۸/۲۳)
--
--  پیش از این هویتِ مشتری «شمارهٔ تأییدشده با کدِ پیامکی» بود. اکنون کدِ ۶ رقمیِ همان `lib/otp` فقط به ایمیل
--  می‌رود و بلیت/حساب ایمیل را ثابت می‌کنند؛ شماره فقط «شمارهٔ تماس برای پیک» است: بی کد، بی ورود، فقط شکل.
--
--  store_customers ── `email` ِ یکتا (کوچک‌حرف) راهِ ورود؛ `phone` شمارهٔ تماس می‌شود: nullable و **نه یکتا**
--                     (دو حساب یک شمارهٔ خانه). حسابِ کهنهٔ فقط‌شماره (email NULL) دست نمی‌خورد: با نشستِ زندهٔ
--                     خودش یک بار ایمیل را با کدِ همان ایمیل وصل می‌کند (`POST /api/store/:slug/account/email`).
--  online_orders ──── `customer_email` (ایمیلِ تأییدشدهٔ ثبت‌کننده؛ سفارشِ کارمند/کهنه '') و `store_customer_id`
--                     (حسابِ همان ایمیل در لحظهٔ ثبت، یا پس از ساختن/وصلِ حساب). «سفارش‌های من» با این دو، پس
--                     سفارش‌های پیشین پس از تغییرِ ایمیل یا شمارهٔ تماس در حساب می‌مانند. `customer_phone` همان
--                     شمارهٔ تماسِ فرمِ ثبت است (پیک و دکان).
--  کلیدِ مشتری ────── ستون‌های کلیدِ «خبرم کن» (`storefront_alerts.phone`)، پوشِ آن (`store_alert_push.phone`) و
--                     «درخواستِ محدوده» (`storefront_area_requests.phone`) از این پس **کلیدِ مشتری**اند: ایمیلِ
--                     تأییدشده برای ردیفِ تازه، شمارهٔ تأییدشدهٔ پیشین برای ردیفِ کهنه. قیدِ «فقط شکلِ شماره»
--                     به «شماره یا ایمیل» باز می‌شود. (چت `customer_phone` را از اول بی قید داشت.)
--  rollback: `migrations/rollback/881_store_email_identity.down.sql`.
-- ============================================================

ALTER TABLE store_customers ADD COLUMN IF NOT EXISTS email text;
ALTER TABLE store_customers DROP CONSTRAINT IF EXISTS store_customers_email_shape;
ALTER TABLE store_customers ADD CONSTRAINT store_customers_email_shape
  CHECK (email IS NULL OR (email = lower(email) AND char_length(email) BETWEEN 3 AND 190 AND email ~ '^[^[:space:]@]+@[^[:space:]@]+$'));
CREATE UNIQUE INDEX IF NOT EXISTS ux_store_customers_email ON store_customers(email) WHERE email IS NOT NULL;
ALTER TABLE store_customers DROP CONSTRAINT IF EXISTS store_customers_phone_key;
ALTER TABLE store_customers ALTER COLUMN phone DROP NOT NULL;
CREATE INDEX IF NOT EXISTS ix_store_customers_phone ON store_customers(phone) WHERE phone IS NOT NULL;
ALTER TABLE store_customers DROP CONSTRAINT IF EXISTS store_customers_identity;
ALTER TABLE store_customers ADD CONSTRAINT store_customers_identity CHECK (email IS NOT NULL OR phone IS NOT NULL);

ALTER TABLE online_orders ADD COLUMN IF NOT EXISTS customer_email text NOT NULL DEFAULT '';
ALTER TABLE online_orders ADD COLUMN IF NOT EXISTS store_customer_id text;
CREATE INDEX IF NOT EXISTS ix_online_orders_storefront_email
  ON online_orders(customer_email, created_at DESC) WHERE source = 'storefront' AND customer_email <> '';
CREATE INDEX IF NOT EXISTS ix_online_orders_store_customer
  ON online_orders(store_customer_id, created_at DESC) WHERE store_customer_id IS NOT NULL;

ALTER TABLE storefront_alerts DROP CONSTRAINT IF EXISTS storefront_alerts_phone_check;
ALTER TABLE storefront_alerts ADD CONSTRAINT storefront_alerts_phone_check
  CHECK (phone ~ '^\+[1-9][0-9]{7,14}$' OR (char_length(phone) <= 190 AND phone ~ '^[^[:space:]@A-Z]+@[^[:space:]@A-Z]+$'));
ALTER TABLE store_alert_push DROP CONSTRAINT IF EXISTS store_alert_push_phone_check;
ALTER TABLE store_alert_push ADD CONSTRAINT store_alert_push_phone_check
  CHECK (phone ~ '^\+[1-9][0-9]{7,14}$' OR (char_length(phone) <= 190 AND phone ~ '^[^[:space:]@A-Z]+@[^[:space:]@A-Z]+$'));
ALTER TABLE storefront_area_requests DROP CONSTRAINT IF EXISTS storefront_area_requests_phone_check;
ALTER TABLE storefront_area_requests ADD CONSTRAINT storefront_area_requests_phone_check
  CHECK (phone ~ '^\+[1-9][0-9]{7,14}$' OR (char_length(phone) <= 190 AND phone ~ '^[^[:space:]@A-Z]+@[^[:space:]@A-Z]+$'));
