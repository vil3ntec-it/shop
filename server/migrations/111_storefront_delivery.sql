-- ============================================================
--  ویترین، گامِ ۲: ناحیهٔ ارسال، بازهٔ زمان‌بندی و جزئیاتِ کالا
--  (فاز ۴؛ CHK-05/06/07/10/12، WEB-02/08/12)
--
--  ناحیه ── چندضلعی ([[lat,lng],…]) یا دایره (مرکز + شعاع)، با هزینه و
--           حداقلِ سفارشِ خودش و «ارسالِ رایگان از…» ِ اختیاری. بررسیِ
--           «داخلِ محدوده» فقط با پین و بی ژئوکدر (CHK-10).
--           دکانِ بی ناحیه ⇒ همان هزینه و حداقلِ ثابتِ `storefronts` (رفتارِ امروز).
--  بازه ── قاعدهٔ هفتگی برای «ارسال» یا «دریافت از دکان»: روزها (بیت‌های
--           ۰=یکشنبه … ۶=شنبه)، آغاز/پایان به دقیقه از نیمه‌شبِ محلیِ دکان،
--           ظرفیت (تعدادِ سفارش) و مهلتِ سفارش پیش از آغاز (CHK-12).
--           ظرفیت از خودِ `online_orders` شمرده می‌شود (scheduled_for در بازه،
--           وضعیتِ زنده) و زیرِ قفلِ سفارشِ همان دکان — جدولِ رزروِ جدا نیست.
--  کالا ── توضیح و مشخصاتِ عمومی (یادداشتِ داخلیِ دفتر هرگز بیرون نمی‌رود)
--           و زمانِ نخستین انتشار برای «تازه‌ها».
--
--  ⛔ ویترین خودش پشتِ پرچم‌های خاموشِ `shop.orders` + `shop.storefront` است؛
--     این جدول‌ها تا دکان ناحیه یا بازه نسازد خالی‌اند و هیچ رفتاری عوض نمی‌شود.
--  بازگشت: DROP دو جدول و DROP COLUMN ستون‌های افزوده — دادهٔ دیگری دست نمی‌خورد.
-- ============================================================

CREATE TABLE IF NOT EXISTS storefront_zones (
  id               text    PRIMARY KEY,                       -- ULID
  shop_id          text    NOT NULL REFERENCES shops(id) ON DELETE CASCADE,
  name             text    NOT NULL CHECK (length(name) BETWEEN 1 AND 60),
  kind             text    NOT NULL CHECK (kind IN ('polygon','radius')),
  polygon          jsonb   NOT NULL DEFAULT '[]'::jsonb,
  center_lat       double precision,
  center_lng       double precision,
  radius_m         integer CHECK (radius_m IS NULL OR radius_m BETWEEN 50 AND 50000),
  fee_minor        bigint  NOT NULL DEFAULT 0 CHECK (fee_minor >= 0),
  min_order_minor  bigint  NOT NULL DEFAULT 0 CHECK (min_order_minor >= 0),
  free_over_minor  bigint  CHECK (free_over_minor IS NULL OR free_over_minor > 0),
  active           boolean NOT NULL DEFAULT true,
  sort             integer NOT NULL DEFAULT 0,
  updated_by       text    NOT NULL DEFAULT '',
  created_at       bigint  NOT NULL,
  updated_at       bigint  NOT NULL,
  CHECK (kind <> 'radius' OR (center_lat IS NOT NULL AND center_lng IS NOT NULL AND radius_m IS NOT NULL)),
  CHECK (kind <> 'polygon' OR jsonb_array_length(polygon) >= 3)
);
CREATE INDEX IF NOT EXISTS ix_storefront_zones_shop ON storefront_zones(shop_id, sort, id);

CREATE TABLE IF NOT EXISTS storefront_slot_rules (
  id               text    PRIMARY KEY,                       -- ULID
  shop_id          text    NOT NULL REFERENCES shops(id) ON DELETE CASCADE,
  fulfillment      text    NOT NULL CHECK (fulfillment IN ('delivery','pickup')),
  weekdays         integer NOT NULL CHECK (weekdays BETWEEN 1 AND 127),
  start_min        integer NOT NULL CHECK (start_min BETWEEN 0 AND 1439),
  end_min          integer NOT NULL CHECK (end_min BETWEEN 1 AND 1440),
  capacity         integer NOT NULL CHECK (capacity BETWEEN 1 AND 1000),
  cutoff_min       integer NOT NULL DEFAULT 60 CHECK (cutoff_min BETWEEN 0 AND 10080),
  active           boolean NOT NULL DEFAULT true,
  sort             integer NOT NULL DEFAULT 0,
  updated_by       text    NOT NULL DEFAULT '',
  created_at       bigint  NOT NULL,
  updated_at       bigint  NOT NULL,
  CHECK (end_min > start_min)
);
CREATE INDEX IF NOT EXISTS ix_storefront_slot_rules_shop ON storefront_slot_rules(shop_id, fulfillment, sort, id);

--  «هرچه زودتر» هم پذیرفته شود؟ (خاموش ⇒ هر روشی که بازه دارد بازه می‌خواهد)
ALTER TABLE storefronts ADD COLUMN IF NOT EXISTS allow_asap boolean NOT NULL DEFAULT true;
--  چند روزِ پیشِ رو بازه نشان داده شود
ALTER TABLE storefronts ADD COLUMN IF NOT EXISTS slot_days integer NOT NULL DEFAULT 3;
ALTER TABLE storefronts DROP CONSTRAINT IF EXISTS storefronts_slot_days_check;
ALTER TABLE storefronts ADD CONSTRAINT storefronts_slot_days_check CHECK (slot_days BETWEEN 1 AND 14);

ALTER TABLE storefront_products ADD COLUMN IF NOT EXISTS description text NOT NULL DEFAULT '';
ALTER TABLE storefront_products ADD COLUMN IF NOT EXISTS specs jsonb NOT NULL DEFAULT '[]'::jsonb;
ALTER TABLE storefront_products ADD COLUMN IF NOT EXISTS published_at bigint;
UPDATE storefront_products SET published_at = updated_at WHERE published AND published_at IS NULL;
CREATE INDEX IF NOT EXISTS ix_storefront_products_new
  ON storefront_products(shop_id, published_at DESC) WHERE published;

--  ظرفیتِ بازه و «پرفروش‌ها/مکمل‌ها» — پرس‌وجوی محدود به همان دکان
CREATE INDEX IF NOT EXISTS ix_online_orders_shop_scheduled
  ON online_orders(shop_id, scheduled_for) WHERE scheduled_for IS NOT NULL;
CREATE INDEX IF NOT EXISTS ix_online_order_lines_shop_product
  ON online_order_lines(shop_id, product_id);
