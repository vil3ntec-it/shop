-- ============================================================
--  ورود شاگرد فقط با کد
--
--  تا امروز شاگرد باید اول با ایمیل یا شماره حساب می‌ساخت و بعد کد را
--  می‌زد. یعنی صاحب دکان که کد را به شاگردش می‌داد، باید یک مرحله‌ی
--  دیگر هم برایش توضیح می‌داد — و شاگردی که ایمیل ندارد، اصلاً وارد
--  نمی‌شد.
--
--  حالا خودِ کد اعتبارنامه است. ولی جدول `users` شرط داشت که هر کاربر
--  دست‌کم ایمیل یا شماره داشته باشد. این مهاجرت آن شرط را طوری باز
--  می‌کند که کاربرِ «شاگرد» بدون هیچ‌کدام بتواند وجود داشته باشد.
--
--  فقط اضافه می‌شود؛ هیچ داده‌ای دست نمی‌خورد.
-- ============================================================

--  person = کسی که خودش حساب ساخته | staff = کسی که فقط با کد آمده
ALTER TABLE users ADD COLUMN IF NOT EXISTS kind text NOT NULL DEFAULT 'person';

ALTER TABLE users DROP CONSTRAINT IF EXISTS users_need_identifier;
ALTER TABLE users ADD CONSTRAINT users_need_identifier
  CHECK (email IS NOT NULL OR phone IS NOT NULL OR kind = 'staff');

--  همان دستگاه با همان کد، همان حساب را دوباره می‌گیرد — نه یک عضوِ
--  تازه در هر بار ورود، که ظرفیت دکان را پر کند.
CREATE INDEX IF NOT EXISTS idx_users_kind ON users(kind) WHERE kind = 'staff';
