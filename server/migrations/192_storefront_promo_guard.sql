-- ============================================================
--  تخفیفِ اولین خرید و سدِ سوءاستفاده از تخفیفِ ویترین — MKT-02 و RSK-09 (v3 §۶.۲۰، §۶.۲۱)
--
--  ⛔ پشتِ پرچمِ خاموشِ `shop.storefront.promo_guard` (`lib/storefront-promo-guard.js`): تا روشن نشود
--  هیچ ردیفی نوشته نمی‌شود و ثبتِ ویترین بایت‌به‌بایت همان پیشین است.
--
--  storefront_promo_guard   تنظیمِ صاحبِ دکان (یک ردیف برای هر دکان): تخفیفِ اولین خرید (درصد یا مبلغ،
--                           سقف، کفِ سبد، ترکیب یا انحصاری) و سقف‌ها (هر کد برای هر شماره/دستگاه، سقفِ
--                           تخفیفِ هر سفارش).
--  storefront_promo_claims  دفترِ مصرفِ تخفیفِ ویترین (ردِ حسابرسی): هر تخفیفی که روی سفارشِ ویترین نشست،
--                           با کلیدِ شماره (`phoneKey`) و چکیدهٔ شناسهٔ دستگاه (SHA-256، نه خودِ شناسه).
--                           ⛔ اولین خرید: یکتا روی (دکان، شماره) و (دکان، دستگاه) — دو ثبتِ هم‌زمان هر دو
--                           آن را نمی‌گیرند (قیدِ پایگاه، نه فقط قفلِ برنامه).
--                           `order` = جفتِ (شماره، دستگاه) ِ هر ثبتِ ویترین با شناسهٔ دستگاه، بی تخفیف هم —
--                           پایهٔ فهرستِ «مشکوک» (چند شماره از یک دستگاه، چند دستگاه برای یک شماره).
--
--  فقط افزودنی: هیچ جدولِ موجودی دست نمی‌خورد و Sync هم نه.
--  برگشت: migrations/rollback/192_storefront_promo_guard.down.sql
-- ============================================================

CREATE TABLE IF NOT EXISTS storefront_promo_guard (
  shop_id     text    PRIMARY KEY REFERENCES shops(id) ON DELETE CASCADE,
  data        jsonb   NOT NULL DEFAULT '{}'::jsonb,
  updated_at  bigint  NOT NULL,
  updated_by  text    NOT NULL DEFAULT ''
);

CREATE TABLE IF NOT EXISTS storefront_promo_claims (
  shop_id         text    NOT NULL REFERENCES shops(id) ON DELETE CASCADE,
  --  کلیدِ یکتاییِ همان ثبت (`online_orders.client_key`)
  client_key      text    NOT NULL,
  kind            text    NOT NULL CHECK (kind IN ('first_order','code','auto','order')),
  --  شناسهٔ قاعدهٔ `shop_promotions`؛ اولین خرید ⇒ ''
  promo_id        text    NOT NULL DEFAULT '',
  code            text    NOT NULL DEFAULT '',
  phone_key       text    NOT NULL,
  device_key      text    NOT NULL DEFAULT '',
  discount_minor  bigint  NOT NULL CHECK (discount_minor >= 0),
  created_at      bigint  NOT NULL,
  PRIMARY KEY (shop_id, client_key, kind, promo_id)
);

--  MKT-02: یک بار برای هر شماره و یک بار برای هر دستگاه
CREATE UNIQUE INDEX IF NOT EXISTS ux_storefront_promo_claims_first_phone
  ON storefront_promo_claims (shop_id, phone_key) WHERE kind = 'first_order';
CREATE UNIQUE INDEX IF NOT EXISTS ux_storefront_promo_claims_first_device
  ON storefront_promo_claims (shop_id, device_key) WHERE kind = 'first_order' AND device_key <> '';

--  RSK-09: سقفِ هر کد برای هر شماره/دستگاه و فهرستِ «مشکوک»
CREATE INDEX IF NOT EXISTS ix_storefront_promo_claims_phone
  ON storefront_promo_claims (shop_id, phone_key, promo_id);
CREATE INDEX IF NOT EXISTS ix_storefront_promo_claims_device
  ON storefront_promo_claims (shop_id, device_key, promo_id) WHERE device_key <> '';
CREATE INDEX IF NOT EXISTS ix_storefront_promo_claims_recent
  ON storefront_promo_claims (shop_id, created_at);
