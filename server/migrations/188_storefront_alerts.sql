-- ============================================================
--  «خبرم کن وقتی موجود شد / ارزان شد» ِ ویترین — WEB-10 (v3 §۶.۱۴)
--
--  ⛔ پشتِ پرچمِ خاموشِ `shop.storefront.alerts` (`lib/storefront-alerts.js`): تا روشن نشود هیچ ردیفی
--  نوشته نمی‌شود و کارگر هیچ خبری نمی‌فرستد. فقط افزودنی — هیچ جدولِ موجودی دست نمی‌خورد.
--  برگشت: DROP TABLE storefront_alerts;
--
--  مشتری = همان شمارهٔ تأییدشده‌ای که سفارشِ ویترین با آن ثبت می‌شود (بلیتِ شماره یا نشستِ حساب).
--  یکتایی (دکان، شماره، کالا، نوع). قاعدهٔ «بی اسپم»: هر هشدار یک بار می‌سوزد (`armed` ⇒ `fired`) و
--  همان دم حداکثر یک خبر؛ ثبتِ دوباره پس از سوختن همان ردیف را با مبنای تازه دوباره مسلح می‌کند.
-- ============================================================

CREATE TABLE IF NOT EXISTS storefront_alerts (
  id                 text    PRIMARY KEY,                         -- ULID
  shop_id            text    NOT NULL REFERENCES shops(id) ON DELETE CASCADE,
  phone              text    NOT NULL CHECK (phone ~ '^\+[1-9][0-9]{7,14}$'),
  product_id         text    NOT NULL CHECK (char_length(product_id) BETWEEN 1 AND 80),
  kind               text    NOT NULL CHECK (kind IN ('stock', 'price')),
  lang               text    NOT NULL DEFAULT 'fa' CHECK (lang IN ('fa', 'ps', 'en')),
  state              text    NOT NULL DEFAULT 'armed' CHECK (state IN ('armed', 'fired', 'expired')),
  base_price_minor   bigint,                                      -- price: قیمتِ لحظهٔ ثبت؛ stock: NULL
  fired_at           bigint,
  fired_price_minor  bigint,
  notice             text    NOT NULL DEFAULT '' CHECK (notice IN (
                       '',              -- هنوز نسوخته
                       'sent',          -- یک کانال پذیرفت (channel، sent_at)
                       'no_channel',    -- هیچ کانالِ بی‌پیامکی وصل نبود یا همه رد کردند
                       'cost_cap',      -- سقفِ روزانهٔ هزینهٔ دکان (NTF-02) راه نداد
                       'rate_limited',  -- سقفِ خبرِ همین شماره پر بود
                       'notify_off',    -- `shop.customer_notify` ِ دکان خاموش است
                       'failed')),      -- پاسخِ مبهم؛ دوباره فرستاده نمی‌شود (شاید رفته باشد)
  channel            text    NOT NULL DEFAULT '',
  tried              jsonb   NOT NULL DEFAULT '[]'::jsonb,        -- [{channel, result, costMicros?}]
  sent_at            bigint,
  checked_at         bigint  NOT NULL,
  created_at         bigint  NOT NULL,
  updated_at         bigint  NOT NULL,
  UNIQUE (shop_id, phone, product_id, kind)
);

--  کارگر: کهنه‌ترین سنجیده اول
CREATE INDEX IF NOT EXISTS ix_storefront_alerts_armed ON storefront_alerts(checked_at, id) WHERE state = 'armed';
--  سقفِ خبرِ هر شماره و هزینهٔ روزانهٔ هر دکان و کانال
CREATE INDEX IF NOT EXISTS ix_storefront_alerts_phone ON storefront_alerts(phone, sent_at) WHERE notice = 'sent';
CREATE INDEX IF NOT EXISTS ix_storefront_alerts_spend ON storefront_alerts(shop_id, channel, sent_at) WHERE notice = 'sent';
