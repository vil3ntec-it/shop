-- ============================================================
--  قاعدهٔ یکسانِ امتیازِ وفاداری برای همهٔ دستگاه‌های دکان — PRM-13 (`lib/loyalty-rule.js`، `/api/shop/loyalty`).
--
--  shop_loyalty_rules  یک ردیف برای هر دکان: روشن/خاموش و قاعدهٔ کسب، مصرف، سطح و انقضا (همان `Loyalty.Rule` ِ
--                      هستهٔ برنامه؛ همه عددِ صحیح). دستگاه‌ها می‌خوانند و آخرین پاسخ را برای بی‌اینترنتی نگه می‌دارند.
--
--  ⛔ پشتِ پرچمِ خاموشِ `shop.loyalty_rule`: تا روشن نشود و صاحب/مدیر ذخیره نکند هیچ ردیفی نوشته نمی‌شود؛ نبودِ ردیف =
--  هر دستگاه همان قاعدهٔ خودش. فقط افزودنی. برگشت: DROP TABLE shop_loyalty_rules.
-- ============================================================

CREATE TABLE IF NOT EXISTS shop_loyalty_rules (
  shop_id        text    PRIMARY KEY REFERENCES shops(id) ON DELETE CASCADE,
  enabled        boolean NOT NULL DEFAULT false,
  every_afn      bigint  NOT NULL CHECK (every_afn BETWEEN 1 AND 1000000),
  point_afn      bigint  NOT NULL CHECK (point_afn BETWEEN 1 AND 1000000),
  max_share_pct  integer NOT NULL CHECK (max_share_pct BETWEEN 1 AND 100),
  silver_afn     bigint  NOT NULL CHECK (silver_afn BETWEEN 1 AND 1000000000000),
  gold_afn       bigint  NOT NULL CHECK (gold_afn > silver_afn AND gold_afn <= 1000000000000),
  expiry_months  integer NOT NULL DEFAULT 0 CHECK (expiry_months BETWEEN 0 AND 120),
  version        integer NOT NULL DEFAULT 1,
  updated_by     text    NOT NULL DEFAULT '',
  updated_at     bigint  NOT NULL
);
