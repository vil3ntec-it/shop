-- ============================================================
--  کد شاگردِ ثابت
--
--  تا امروز هر شاگرد یک کد تازه می‌خواست (max_uses = 1). صاحب دکان
--  باید برای هر نفر یک بار کد می‌ساخت و کد قبلی به درد نمی‌خورد.
--
--  حالا هر دکان یک کد ثابت دارد که تا وقتی خودش عوضش نکند همان می‌ماند.
--  خودِ کد باز هم در دیتابیس نیست: از shop_id و شماره‌ی نسل با راز سرور
--  ساخته می‌شود، پس هر وقت لازم شد دوباره حساب می‌شود ولی از روی
--  دیتابیس خوانده نمی‌شود.
--
--  «نسل» برای وقتی است که کد لو برود: با یک شماره جلو رفتن، کد قبلی
--  می‌میرد و کد تازه‌ای می‌آید، بدون اینکه شاگردهای فعلی بیرون بیفتند.
-- ============================================================

ALTER TABLE staff_codes ADD COLUMN IF NOT EXISTS standing    boolean NOT NULL DEFAULT false;
ALTER TABLE staff_codes ADD COLUMN IF NOT EXISTS generation  integer NOT NULL DEFAULT 0;

-- هر دکان فقط یک کد ثابتِ زنده دارد
CREATE UNIQUE INDEX IF NOT EXISTS idx_staff_codes_standing
  ON staff_codes(shop_id) WHERE standing AND status = 'active';
