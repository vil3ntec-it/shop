-- ============================================================
--  کدِ اشتراکِ آفلاین — برای کامپیوتری که اینترنت ندارد
--
--  خواستهٔ صاحب سامانه (۱۴۰۵/۰۷/۱۵): «یک کد برای اشتراک می‌سازی که برای
--  کسانی که نت ندارن هم اشتراک بدم… سه نوع کد (استاندارد، وی‌آی‌پی،
--  دائمی) و یک گیرنده در برنامه تا درجا قفل‌ها باز بشه و بدون نت هم
--  اشتراک داده بشه؛ و اگه یارو اینترنت پیدا کرد، سرور همون کد رو ببینه و
--  بگه آره این حساب اشتراک داره.»
--
--  خودِ کد یک برگهٔ امضاشده است (کلیدِ مجوز، ES256) و برنامه آن را بی
--  اینترنت می‌سنجد؛ این جدول فقط **دفترِ** کدهای صادرشده است: چه کسی،
--  برای کدام کامپیوتر، کدام پلن، تا کی، باطل شده یا نه، و وقتی آنلاین شد
--  به کدام پمپ نشست.
--
--  ⚠️ خودِ کد این‌جا ذخیره نمی‌شود — فقط شمارهٔ سریالش. کد بی کلیدِ
--  خصوصی ساختنی نیست، پس داشتنِ این جدول به کسی کدی نمی‌دهد.
-- ============================================================
CREATE TABLE IF NOT EXISTS pump_offline_codes (
  id                  text PRIMARY KEY,
  serial              text NOT NULL UNIQUE,
  plan                text NOT NULL,
  computer            text NOT NULL,
  issued_at           bigint NOT NULL,
  ends_at             bigint NOT NULL DEFAULT 0,       -- ۰ = دائمی
  note                text NOT NULL DEFAULT '',
  created_by          text NOT NULL DEFAULT '',
  status              text NOT NULL DEFAULT 'issued',  -- issued | revoked
  revoked_at          bigint,
  redeemed_station_id text,
  redeemed_device_uid text NOT NULL DEFAULT '',
  redeemed_at         bigint,
  created_at          bigint NOT NULL
);
CREATE INDEX IF NOT EXISTS ix_pump_offline_codes_computer ON pump_offline_codes(computer);
CREATE INDEX IF NOT EXISTS ix_pump_offline_codes_created ON pump_offline_codes(created_at DESC);
