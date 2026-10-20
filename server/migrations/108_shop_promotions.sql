-- ============================================================
--  تخفیف و قاعده‌های قیمتِ دکان — PRM-01..09، 11، 12، 17 (بخشِ ۶.۴)
--
--  دو جدولِ تازه؛ هیچ جدولِ موجودی دست نمی‌خورد و Sync هم نه:
--   • `shop_promo_settings` — کلیدِ دکان «تخفیف‌های خودکار در صندوق».
--     ردیف نداشتن = خاموش (پیش‌فرض). صندوق تا روشن نشود همان جمعِ امروز را
--     حساب می‌کند.
--   • `shop_promotions` — هر قاعده یک ردیف؛ `data` همان قاعدهٔ
--     `license/promo-core.js` (`ruleOf`) است و پیش از نوشتن با `validate`
--     سنجیده می‌شود.
--
--  ⛔ پاک کردن نرم است (`deleted`): «کدِ مصرف‌شده حذف نمی‌شود» (PRM-02) و
--     کدِ تخفیف در یک دکان یکتاست، حتی میانِ ردیف‌های پاک‌شده.
--  ⛔ shop_id فقط از عضویتِ کاربرِ توکن می‌آید.
--
--  بازگشت: DROP TABLE هر دو؛ هیچ مسیری جز `/api/shop/promos` به آن‌ها نمی‌رسد.
-- ============================================================

CREATE TABLE IF NOT EXISTS shop_promo_settings (
  shop_id     text    PRIMARY KEY REFERENCES shops(id) ON DELETE CASCADE,
  enabled     boolean NOT NULL DEFAULT false,
  updated_at  bigint  NOT NULL,
  updated_by  text    NOT NULL DEFAULT ''
);

CREATE TABLE IF NOT EXISTS shop_promotions (
  shop_id     text    NOT NULL REFERENCES shops(id) ON DELETE CASCADE,
  id          text    NOT NULL,
  data        jsonb   NOT NULL,
  --  کدِ نرمال‌شده؛ '' = خودکار (بی کد)
  code        text    NOT NULL DEFAULT '',
  rev         bigint  NOT NULL DEFAULT 1,
  created_at  bigint  NOT NULL,
  updated_at  bigint  NOT NULL,
  updated_by  text    NOT NULL DEFAULT '',
  deleted     boolean NOT NULL DEFAULT false,
  PRIMARY KEY (shop_id, id)
);

CREATE UNIQUE INDEX IF NOT EXISTS idx_shop_promotions_code
  ON shop_promotions (shop_id, code) WHERE code <> '';
