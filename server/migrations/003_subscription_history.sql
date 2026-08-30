-- ============================================================
--  تاریخچه‌ی اشتراک
--
--  تا امروز grant() ردیف اشتراک را جایگزین می‌کرد: تاریخ پایان تازه
--  روی قبلی می‌نشست و «قبلاً تا کِی بود» از بین می‌رفت. برای سیستمی که
--  پول دستی گرفته می‌شود این پذیرفتنی نیست — باید بشود ثابت کرد چه کسی
--  کِی چه چیزی داد.
--
--  این جدول فقط اضافه می‌شود؛ هیچ داده‌ای دست نمی‌خورد و هیچ ستونی از
--  جدول‌های موجود عوض نمی‌شود.
-- ============================================================

CREATE TABLE IF NOT EXISTS subscription_history (
  id              text   PRIMARY KEY,
  subscription_id text   NOT NULL,
  shop_id         text   NOT NULL REFERENCES shops(id) ON DELETE CASCADE,

  --  چه شد: grant (صدور یا تمدید) یا status (عوض شدن وضعیت)
  action          text   NOT NULL CHECK (action IN ('grant', 'renew', 'status', 'expire')),
  plan            text   NOT NULL DEFAULT '',

  --  پیش و پس؛ برای تمدید، prev_ends_at همان چیزی است که بوده
  prev_status     text   NOT NULL DEFAULT '',
  new_status      text   NOT NULL DEFAULT '',
  prev_ends_at    bigint,
  new_ends_at     bigint,

  --  چه کسی: شناسه‌ی مدیر، یا 'system' برای کارهای خودکار
  actor           text   NOT NULL DEFAULT '',
  note            text   NOT NULL DEFAULT '',
  created_at      bigint NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_sub_hist_shop ON subscription_history(shop_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_sub_hist_sub  ON subscription_history(subscription_id, created_at DESC);
