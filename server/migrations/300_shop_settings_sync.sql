-- ============================================================
--  ۳۰۰ — همگام‌سازیِ زندهٔ تنظیماتِ دکان (`docs/DATA-SAFETY-fa.md` «تنظیماتِ دکان»، ۱۴۰۵/۰۸/۱۸)
--
--  پیش‌تر قاعده‌های دکان (`tohid-policy`) فقط با پشتیبانِ ابریِ دوازده‌ساعته به سرور می‌رسیدند. حالا هر کلیدِ
--  «مالِ دکان» (فهرست و دلیلِ هر کلید: `ShopSettingsKeys.kt` و DATA-SAFETY §۶) یک ردیف است: آخرین نویسنده با
--  ساعتِ **سرور** برنده (`rev` ِ پیاپیِ هر دکان، زیرِ قفلِ همان دکان)، و هر تغییر یک ردیفِ سابقه.
--
--  ⛔ پشتِ پرچمِ `shop.settings_sync` (پیش‌فرض خاموش): خاموش ⇒ هیچ ردیفی نوشته نمی‌شود.
--  ⛔ فقط صاحب و مدیر می‌نویسند (`settings.write`)؛ shop_id فقط از عضویتِ توکن.
--  فقط افزودنی. rollback: migrations/rollback/300_shop_settings_sync.down.sql
-- ============================================================

CREATE TABLE IF NOT EXISTS shop_setting_keys (
  shop_id     text    NOT NULL REFERENCES shops(id) ON DELETE CASCADE,
  --  انبارِ برنامه (`SharedPreferences`)
  store       text    NOT NULL CHECK (store IN ('tohid-policy','tohid-receipt','tohid-modules')),
  key         text    NOT NULL CHECK (key ~ '^[a-z0-9_]{1,64}$'),
  --  نوعِ مقدار (همان قالبِ `ShopSettingsBundle`)؛ `del` = کلید برداشته شد
  type        text    NOT NULL CHECK (type IN ('s','i','l','f','b','set','del')),
  value       jsonb   NOT NULL,
  --  نسخهٔ پیاپیِ همان دکان (هر نوشتن یکی بیش از بزرگ‌ترینِ پیشین)
  rev         bigint  NOT NULL,
  --  ساعتِ سرور، نه دستگاه
  updated_at  bigint  NOT NULL,
  updated_by  text    NOT NULL,
  device_id   text    NOT NULL DEFAULT '',
  PRIMARY KEY (shop_id, store, key)
);
CREATE INDEX IF NOT EXISTS ix_shop_setting_keys_rev ON shop_setting_keys(shop_id, rev);

--  سابقهٔ هر تغییر (پیش و پس، چه کسی، کدام دستگاه، ساعتِ سرور) — پاک نمی‌شود مگر با دکان
CREATE TABLE IF NOT EXISTS shop_setting_keys_log (
  id          text    PRIMARY KEY,
  shop_id     text    NOT NULL REFERENCES shops(id) ON DELETE CASCADE,
  store       text    NOT NULL,
  key         text    NOT NULL,
  old_type    text,
  old_value   jsonb,
  new_type    text    NOT NULL,
  new_value   jsonb   NOT NULL,
  rev         bigint  NOT NULL,
  at          bigint  NOT NULL,
  by_user     text    NOT NULL,
  role        text    NOT NULL DEFAULT '',
  device_id   text    NOT NULL DEFAULT ''
);
CREATE INDEX IF NOT EXISTS ix_shop_setting_keys_log_shop ON shop_setting_keys_log(shop_id, at DESC);
