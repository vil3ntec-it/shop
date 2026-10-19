-- ============================================================
--  کاتالوگِ واحد — v3 §۳۲.۱ (مدلِ داده) و §۳۸.۳
--
--  کالا یک بار در دستگاهِ دکان تعریف می‌شود (همگام‌سازی) و همین‌جا با
--  **همان شناسه** برای صندوق، انبار و فروشگاهِ آنلاین دیده می‌شود (INV-01).
--  `catalog_products` فقط «مدلِ خواندنی» است: از دفترِ همگام‌سازی
--  (`sync_rows` یا `sync_v3_rows`) ساخته می‌شود و هر وقت بخواهیم از نو
--  ساختنی است — دادهٔ همگام‌سازی و قالبش دست نمی‌خورد.
--
--  ⛔ پیش‌فرض خاموش: تا پرچمِ `shop.catalog` در «تنظیماتِ زنده» روشن نشود
--  هیچ ردیفی در هیچ‌کدام از این جدول‌ها نوشته نمی‌شود.
--  ⛔ پول به یک‌صدمِ افغانی (bigint) و مقدار به هزارم — بی ممیزِ شناور.
--  ⛔ بهای خرید (محرمانه) این‌جا نمی‌آید؛ فقط قیمتِ فروش.
-- ============================================================

--  سیاستِ هر دکان (§۳۲.۱ «فرمول و مکان‌های قابل‌فروش در تنظیمات مستند»)
CREATE TABLE IF NOT EXISTS catalog_settings (
  shop_id             text    PRIMARY KEY REFERENCES shops(id) ON DELETE CASCADE,
  --  مکان‌هایی که موجودیِ «قابل‌فروش» از آن‌ها جمع می‌شود (به همین ترتیبِ برداشت)
  sellable_locations  jsonb   NOT NULL DEFAULT '["shop_floor","warehouse"]'::jsonb,
  --  مهلتِ رزروِ سفارشی که هنوز قبول نشده (CHK-18)
  reserve_ttl_sec     integer NOT NULL DEFAULT 1800 CHECK (reserve_ttl_sec BETWEEN 60 AND 86400),
  --  دستگاهِ دکان این مدت دیده نشده ⇒ موجودی «در حال بررسی» (INV-14)
  stale_after_sec     integer NOT NULL DEFAULT 900 CHECK (stale_after_sec BETWEEN 60 AND 604800),
  --  عددِ دقیقِ موجودی به مشتری گفته شود یا فقط وضعیت (§۳۲.۳)
  show_qty            text    NOT NULL DEFAULT 'hidden' CHECK (show_qty IN ('hidden','exact')),
  --  زمانِ واقعیِ آماده‌سازی از انبار؛ null ⇒ زمانی نشان داده نمی‌شود
  prep_minutes        integer CHECK (prep_minutes IS NULL OR prep_minutes BETWEEN 1 AND 10080),
  --  موجودیِ ثبت‌شده در دستگاه کجا می‌نشیند (دستگاه امروز یک عدد دارد)
  device_location     text    NOT NULL DEFAULT 'shop_floor' CHECK (device_location IN ('shop_floor','warehouse')),
  --  دفترِ همگام‌سازیِ منبع: v1 (همهٔ برنامه‌های نصب‌شدهٔ امروز) یا v3 (پس از کوچِ دکان)
  sync_source         text    NOT NULL DEFAULT 'v1' CHECK (sync_source IN ('v1','v3')),
  updated_by          text    NOT NULL DEFAULT '',
  updated_at          bigint  NOT NULL
);

CREATE TABLE IF NOT EXISTS catalog_products (
  shop_id             text    NOT NULL REFERENCES shops(id) ON DELETE CASCADE,
  --  همان شناسهٔ کالا در دستگاه (row_idِ همگام‌سازی) — هرگز عوض نمی‌شود
  product_id          text    NOT NULL,
  --  variant: کالای مادر (اندازه/رنگ) و ویژگی‌ها؛ هر variant شناسه و موجودیِ خودش را دارد
  parent_id           text    NOT NULL DEFAULT '',
  variant             jsonb   NOT NULL DEFAULT '{}'::jsonb,
  name                text    NOT NULL DEFAULT '',
  description         text    NOT NULL DEFAULT '',
  category            text    NOT NULL DEFAULT '',
  subcategory         text    NOT NULL DEFAULT '',
  brand               text    NOT NULL DEFAULT '',
  barcodes            jsonb   NOT NULL DEFAULT '[]'::jsonb,
  --  واحدِ موجودی؛ واحدِ فروشِ دوم و ضریبش (یک واحدِ فروش = factor/1000 واحدِ موجودی) — INV-10
  unit                text    NOT NULL DEFAULT '',
  sale_unit           text    NOT NULL DEFAULT '',
  unit_factor_milli   bigint  NOT NULL DEFAULT 1000 CHECK (unit_factor_milli > 0),
  --  قیمتِ فروش به ازای یک «واحدِ موجودی»؛ null ⇒ قیمت ندارد
  price_minor         bigint  CHECK (price_minor IS NULL OR price_minor >= 0),
  --  آستانهٔ «موجودیِ کم» برای همین کالا (minStockِ دستگاه)
  min_stock_milli     bigint  NOT NULL DEFAULT 0,
  active              boolean NOT NULL DEFAULT true,
  deleted             boolean NOT NULL DEFAULT false,
  --  عکس این‌جا کپی نمی‌شود: هنگامِ خواندن از `product_photos` (همان عکسِ دستگاه)
  source              text    NOT NULL CHECK (source IN ('v1','v3')),
  source_seq          bigint  NOT NULL DEFAULT 0,
  updated_at          bigint  NOT NULL,
  PRIMARY KEY (shop_id, product_id)
);
CREATE INDEX IF NOT EXISTS ix_catalog_products_name ON catalog_products(shop_id, name, product_id);

--  تا کجای دفترِ همگام‌سازی خوانده شده (برای خواندنِ افزایشی)
CREATE TABLE IF NOT EXISTS catalog_sync_state (
  shop_id             text    PRIMARY KEY REFERENCES shops(id) ON DELETE CASCADE,
  source              text    NOT NULL CHECK (source IN ('v1','v3')),
  watermark           bigint  NOT NULL DEFAULT 0,
  last_run_at         bigint  NOT NULL
);
