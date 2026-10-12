-- ============================================================
--  کدِ اشتراکِ آفلاینِ فروشگاه — بسته به یک کامپیوتر، یک بار
--
--  خواستهٔ صاحب سامانه (۱۴۰۵/۰۷/۲۰): «کد افلاین اشتراک باشه که کسی اگه
--  نت هم نداشت بتونه از برنامه اشتراک بگیره؛ برای هر کامپیوتر یک ایدی
--  درست بشه و از سرور به اون ادرس کد داده میشه و روی همون کد فقط کار
--  کنه و یک بار بیشتر هم استفاده نشه.»
--
--  همان طرحِ `pump_offline_codes` (۰۳۶ و ۰۴۱): خودِ کد این‌جا نیست، فقط
--  سریال. کد بی کلیدِ خصوصی ساختنی نیست.
-- ============================================================
CREATE TABLE IF NOT EXISTS shop_offline_codes (
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
  account_user_id     text NOT NULL DEFAULT '',
  account_email       text NOT NULL DEFAULT '',
  redeemed_shop_id    text,
  redeemed_user_id    text NOT NULL DEFAULT '',
  redeemed_at         bigint,
  created_at          bigint NOT NULL
);
CREATE INDEX IF NOT EXISTS ix_shop_offline_codes_computer ON shop_offline_codes(computer);
CREATE INDEX IF NOT EXISTS ix_shop_offline_codes_created ON shop_offline_codes(created_at DESC);
