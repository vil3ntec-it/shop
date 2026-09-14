-- ============================================================
--  «هیچ ربطی به هم نداشته باشند، حتی یک ذره»
--
--  خواستهٔ صاحب مخزن: «دامنه و تونل یکی‌اند، ولی برنامهٔ شاپ و
--  پمپ‌بنزین قاطی نشوند. مثلِ این‌که برای یک دامنه می‌شود تونلِ
--  جدا ساخت، برای این‌ها هم کاری کن که ربطی به هم نداشته باشند.»
--
--  تا امروز سه چیز واقعاً مشترک مانده بود و هر سه معنای
--  تجاری داشتند:
--
--   ۱) جدولِ `plans` — یعنی قیمت و پلنِ شاپ همان قیمت و پلنِ
--      پمپ بود. یک تخفیفِ دکان‌ها روی پمپ‌ها هم می‌نشست.
--
--   ۲) توکنِ نشست — یک توکن هم `/api/shop/…` را باز می‌کرد هم
--      `/api/pump/…` را. یعنی دو بخش از دیدِ دسترسی یکی بودند.
--
--   ۳) (در کد، نه این‌جا) نوشتن روی پوشهٔ پمپ هیچ بررسیِ
--      اشتراکی نداشت — اشتراکِ تمام‌شده هم می‌نوشت.
--
--  این مهاجرت دوتای اول را می‌بندد.
--
--  ⚠️ هیچ ردیفی جابه‌جا نمی‌شود: هر چه از قبل هست `shop` علامت
--  می‌خورد، پس بخشِ زندهٔ دکان ذره‌ای عوض نمی‌شود.
-- ============================================================

-- ---------- ۱) پلن و قیمتِ هر بخش، جدا ----------
ALTER TABLE plans ADD COLUMN IF NOT EXISTS app text NOT NULL DEFAULT 'shop';

--  `code` تا امروز در کلِ جدول یکتا بود. حالا باید در هر بخش یکتا
--  باشد — وگرنه پمپ نمی‌توانست پلنی به نامِ «m1» داشته باشد چون
--  دکان‌ها آن را گرفته‌اند.
ALTER TABLE plans DROP CONSTRAINT IF EXISTS plans_code_key;
CREATE UNIQUE INDEX IF NOT EXISTS idx_plans_app_code ON plans(app, code);
CREATE INDEX IF NOT EXISTS idx_plans_app ON plans(app, active);

-- ---------- ۲) توکنِ نشست به بخشش بسته می‌شود ----------
--
--  از این به بعد توکنی که برای دکان صادر شده، روی مسیرهای پمپ
--  «انگار اصلاً وجود ندارد» — و برعکس.
--
--  پیش‌فرضِ `shop` عمدی است: هر توکنی که همین حالا در دستِ
--  کاربران است مالِ بخشِ دکان بوده، پس هیچ‌کس بیرون نمی‌افتد.
ALTER TABLE tokens ADD COLUMN IF NOT EXISTS app text NOT NULL DEFAULT 'shop';
CREATE INDEX IF NOT EXISTS idx_tokens_app ON tokens(subject_id, app, kind);

-- ---------- ۳) دستگاه‌های پمپ ----------
--
--  ── چرا این جدول، و چرا جدا از `devices` ──────────────────
--  برنامهٔ کامپیوترِ پمپ باید بی هیچ حسابِ گوگلی به سرور وصل شود:
--  صاحبِ پمپ کدِ شش‌رقمی را در برنامه می‌زند و از همان لحظه
--  اشتراکش از سرور می‌آید. این «حسابِ کاربر» نیست، «دستگاهِ
--  ثبت‌شده» است.
--
--  اگر همان `devices`ِ دکان‌ها را به کار می‌بردیم، دوباره یک
--  ریسمان بین دو بخش می‌بستیم — همان چیزی که قرار است نباشد.
--
--  توکن مثل هر توکنِ دیگری فقط به شکلِ هش می‌ماند؛ لو رفتنِ
--  دیتابیس، توکنِ زنده‌ای به کسی نمی‌دهد.
CREATE TABLE IF NOT EXISTS station_devices (
  id           text    PRIMARY KEY,
  station_id   text    NOT NULL REFERENCES stations(id) ON DELETE CASCADE,
  token_hash   text    NOT NULL UNIQUE,
  device_uid   text    NOT NULL,
  name         text    NOT NULL DEFAULT '',
  platform     text    NOT NULL DEFAULT '',
  status       text    NOT NULL DEFAULT 'active' CHECK (status IN ('active','revoked')),
  created_at   bigint  NOT NULL,
  last_seen_at bigint,
  last_ip      text    NOT NULL DEFAULT '',
  UNIQUE (station_id, device_uid)
);
CREATE INDEX IF NOT EXISTS idx_station_devices ON station_devices(station_id, status);

-- ---------- ۴) پمپی که با کد فعال شده، هنوز صاحبِ گوگلی ندارد ----------
--
--  تا امروز هر پمپ باید یک `owner_user_id` می‌داشت، چون تنها راهِ
--  ساختش ورود با گوگل بود. حالا برنامهٔ کامپیوتر با کد فعال
--  می‌شود و ممکن است ماه‌ها هیچ گوشی‌ای به آن وصل نشود.
--
--  پس صاحب اختیاری شد. کارمند یا کارفرما بعداً با
--  `POST /api/pump/claim` و کدِ پیوستنی که خودِ برنامه نشان
--  می‌دهد، به همان پمپ می‌پیوندد.
ALTER TABLE stations ALTER COLUMN owner_user_id DROP NOT NULL;

-- ---------- ۵) کدِ پیوستن ----------
--
--  کدی که برنامهٔ کامپیوتر روی صفحه نشان می‌دهد تا گوشیِ کارمند
--  به همان پمپ بپیوندد. عمرِ کوتاه دارد و یک‌بارمصرف نیست (چند
--  کارمند با یک کد می‌آیند) ولی سقفِ مصرف دارد.
--
--  مثل هر کدِ دیگری در این سامانه، فقط هشش ذخیره می‌شود.
CREATE TABLE IF NOT EXISTS station_join_codes (
  id          text    PRIMARY KEY,
  station_id  text    NOT NULL REFERENCES stations(id) ON DELETE CASCADE,
  code_hash   text    NOT NULL UNIQUE,
  code_hint   text    NOT NULL DEFAULT '',
  role        text    NOT NULL DEFAULT 'staff' CHECK (role IN ('owner','manager','staff')),
  created_at  bigint  NOT NULL,
  expires_at  bigint,
  max_uses    integer NOT NULL DEFAULT 10,
  used_count  integer NOT NULL DEFAULT 0,
  status      text    NOT NULL DEFAULT 'active' CHECK (status IN ('active','revoked','exhausted'))
);
CREATE INDEX IF NOT EXISTS idx_station_join ON station_join_codes(station_id, status);
