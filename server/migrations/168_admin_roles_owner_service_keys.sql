-- فاز ۳ ِ پنلِ مرکزی (console-phase3): نقش‌های کمینه، «مالکِ اصلی» و کلیدِ سرویسِ پلِ پنل.
--
-- ۱) نقش‌های مدیر: به `admin` و `superadmin`ِ پیشین دو نقشِ باریک افزوده می‌شود — `finance` (مالی:
--    اشتراک، پرداخت، درخواستِ خرید، «پایانِ اشتراک») و `viewer` (فقط خواندن). مرزشان در
--    `lib/admin-roles.js` و در **سرور** سنجیده می‌شود. حساب‌های امروز همان نقشِ خودشان را دارند.
-- ۲) `is_owner`: مالکِ اصلیِ سامانه — مدیرِ ارشدِ دیگر نمی‌تواند او را غیرفعال یا تنزل دهد. پیشین‌ترین
--    ارشد (همان حسابِ آغازینِ سرور) مالک می‌شود؛ در هر لحظه فقط یک مالک.
-- ۳) `admin_service_keys`: هویتِ سرویسِ پلِ پنل به‌جای رمزِ ارشدِ مشترک. کلید فقط یک بار نشان داده
--    می‌شود؛ این‌جا فقط SHA-256 اش و هشت نویسهٔ آغازش (برای شناختن) می‌ماند. `role_cap` سقفِ نقشی است
--    که «انجام‌دهنده» از این کلید می‌گیرد.
--
-- فقط افزودنی؛ هیچ ردیفی پاک یا نقشی عوض نمی‌شود. برگشت: DROP TABLE admin_service_keys،
-- DROP INDEX ux_admins_owner، ALTER TABLE admins DROP COLUMN is_owner و قیدِ نقشِ پیشین
-- (فقط اگر هیچ حسابی finance/viewer نیست).
ALTER TABLE admins DROP CONSTRAINT IF EXISTS admins_role_check;
ALTER TABLE admins ADD CONSTRAINT admins_role_check CHECK (role IN ('admin','superadmin','finance','viewer'));

ALTER TABLE admins ADD COLUMN IF NOT EXISTS is_owner boolean NOT NULL DEFAULT false;
CREATE UNIQUE INDEX IF NOT EXISTS ux_admins_owner ON admins ((true)) WHERE is_owner;
UPDATE admins SET is_owner = true
 WHERE id = (SELECT id FROM admins WHERE role = 'superadmin' ORDER BY created_at ASC, id ASC LIMIT 1)
   AND NOT EXISTS (SELECT 1 FROM admins WHERE is_owner);

CREATE TABLE IF NOT EXISTS admin_service_keys (
  id            text    PRIMARY KEY,
  name          text    NOT NULL,
  key_hash      text    NOT NULL UNIQUE,
  key_prefix    text    NOT NULL,
  role_cap      text    NOT NULL CHECK (role_cap IN ('admin','superadmin','finance','viewer')),
  status        text    NOT NULL DEFAULT 'active' CHECK (status IN ('active','revoked')),
  created_by    text    NOT NULL,
  created_at    bigint  NOT NULL,
  last_used_at  bigint,
  revoked_at    bigint,
  revoked_by    text
);
