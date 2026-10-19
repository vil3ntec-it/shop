-- ============================================================
--  بانکِ تصاویر — انبارِ عکس، نسخه‌ها و هویتِ کالا (v3 ۳۴، ۳۶.۹، ۳۷)
--
--  همه تازه‌اند؛ هیچ جدولِ موجودی (از جمله `product_photos`ِ ۰۴۵) دست
--  نمی‌خورد. تا پرچم‌های `shop.image_bank` (دکان) و `image_bank.central`
--  (پنلِ مدیر) در «تنظیماتِ زنده» روشن نشوند هیچ ردیفی نوشته نمی‌شود.
--
--  ⛔ سه فضای جدا (۳۷.۲) در ستونِ `images.space`:
--       central  بانکِ مرکزیِ VILL3N — فقط پس از تأییدِ مدیرِ ارشد
--       shop     عکسِ اختصاصیِ یک دکان — فقط همان دکان (owner_shop_id)
--       pending  در انتظارِ بررسی — بارگذاریِ مدیر یا پیشنهادِ دکان با اجازهٔ صریح
--  ⛔ فایلِ اصلی هرگز بازنویسی نمی‌شود: هر تغییر نسخهٔ تازه است (۳۶.۱).
--  ⛔ بایت‌ها در `image_blobs` با کلیدِ hashِ محتوا — یک بار ذخیره، بی‌تکرار.
--     دسترسی هرگز با hash نیست؛ همیشه از راهِ ردیفِ `images` و فضای آن.
--  ⚠️ تصمیمِ معماری (۳۶.۹): object storage هنوز نیست؛ عکس مثلِ ۰۴۵ در
--     جدولی جدا (نه در جدولِ کالا) و با سقفِ حجم نگه داشته می‌شود.
-- ============================================================

CREATE SEQUENCE IF NOT EXISTS image_bank_rev;

CREATE TABLE IF NOT EXISTS image_blobs (
  hash        text    PRIMARY KEY,               -- sha256 کامل (hex)
  mime        text    NOT NULL CHECK (mime IN ('image/jpeg','image/png','image/webp')),
  size        integer NOT NULL,
  width       integer NOT NULL,
  height      integer NOT NULL,
  data        bytea   NOT NULL,
  created_at  bigint  NOT NULL
);

--  هویتِ کالای بانک (۳۷.۴) — قیمت، موجودی و قفسه این‌جا نیستند و نخواهند بود
CREATE TABLE IF NOT EXISTS bank_products (
  id             text    PRIMARY KEY,
  gtin           text    NOT NULL DEFAULT '',      -- همان‌طور که وارد شد (رقمِ لاتین)
  gtin14         text    NOT NULL DEFAULT '',      -- فقط بارکدِ معتبر، ۱۴ رقمی
  sku            text    NOT NULL DEFAULT '',
  name           text    NOT NULL,                 -- نامِ اصلی، دست‌نخورده
  alt_names      text    NOT NULL DEFAULT '[]',    -- JSON: نام‌های دری/پشتو/انگلیسی
  brand          text    NOT NULL DEFAULT '',
  category       text    NOT NULL DEFAULT '',
  variant        text    NOT NULL DEFAULT '',      -- طعم/مدل
  size_value     text    NOT NULL DEFAULT '',
  size_unit      text    NOT NULL DEFAULT '',
  pack_count     integer NOT NULL DEFAULT 0,
  packaging      text    NOT NULL DEFAULT '',
  --  کلیدها (هستهٔ مشترکِ متن) — فقط برای جست‌وجو
  name_key       text    NOT NULL DEFAULT '',
  brand_key      text    NOT NULL DEFAULT '',
  category_key   text    NOT NULL DEFAULT '',
  variant_key    text    NOT NULL DEFAULT '',
  packaging_key  text    NOT NULL DEFAULT '',
  size_base      numeric NOT NULL DEFAULT 0,
  size_dim       text    NOT NULL DEFAULT '',
  search_key     text    NOT NULL DEFAULT '',
  status         text    NOT NULL DEFAULT 'active' CHECK (status IN ('active','archived')),
  created_by     text    NOT NULL DEFAULT '',
  created_at     bigint  NOT NULL,
  updated_at     bigint  NOT NULL
);
CREATE INDEX IF NOT EXISTS bank_products_gtin14 ON bank_products (gtin14);
CREATE INDEX IF NOT EXISTS bank_products_sku ON bank_products (sku);
CREATE INDEX IF NOT EXISTS bank_products_name ON bank_products (name_key);
CREATE INDEX IF NOT EXISTS bank_products_brand ON bank_products (brand_key, category_key);

CREATE TABLE IF NOT EXISTS images (
  id                 text    PRIMARY KEY,
  space              text    NOT NULL CHECK (space IN ('central','shop','pending')),
  owner_shop_id      text    NOT NULL DEFAULT '',  -- فقط فضای shop
  status             text    NOT NULL CHECK (status IN ('DRAFT','PROCESSING','NEEDS_REVIEW','APPROVED','REJECTED','ARCHIVED')),
  role               text    NOT NULL DEFAULT 'main' CHECK (role IN ('main','back','label','variant','promo','detail')),
  --  کالا: بانک (central/pending) یا شناسهٔ کالای خودِ دکان (shop)
  bank_product_id    text    NOT NULL DEFAULT '',
  shop_product_id    text    NOT NULL DEFAULT '',
  match_status       text    NOT NULL DEFAULT 'unconfirmed' CHECK (match_status IN ('exact','probable','ambiguous','unconfirmed')),
  match_reasons      text    NOT NULL DEFAULT '[]',
  --  مجوز (۳۷.۳) — بی `licensed` + استفادهٔ تجاری هیچ عکسی مرکزی نمی‌شود
  license_source     text    NOT NULL DEFAULT '',
  license_holder     text    NOT NULL DEFAULT '',
  license_type       text    NOT NULL DEFAULT '',
  license_status     text    NOT NULL DEFAULT 'unknown' CHECK (license_status IN ('unknown','licensed','restricted','denied')),
  commercial_use     boolean NOT NULL DEFAULT false,
  license_checked_at bigint  NOT NULL DEFAULT 0,
  --  پیشنهادِ دکان به بانک: اجازهٔ صریح (کاربر و زمان) — بی آن کپی نمی‌شود
  origin_shop_id     text    NOT NULL DEFAULT '',
  origin_image_id    text    NOT NULL DEFAULT '',
  consent_by         text    NOT NULL DEFAULT '',
  consent_at         bigint  NOT NULL DEFAULT 0,
  proposal           text    NOT NULL DEFAULT '{}', -- JSON: هویتِ پیشنهادیِ دکان یا ردیفِ ورودی
  current_version    integer NOT NULL DEFAULT 1,
  search_enabled     boolean NOT NULL DEFAULT false,
  duplicate_of       text    NOT NULL DEFAULT '[]', -- JSON: شناسه‌های همان محتوا — فقط نشان، نه ادغام
  quality_flags      text    NOT NULL DEFAULT '[]',
  use_count          integer NOT NULL DEFAULT 0,
  batch_id           text    NOT NULL DEFAULT '',
  created_by         text    NOT NULL DEFAULT '',
  reviewed_by        text    NOT NULL DEFAULT '',
  reviewed_at        bigint  NOT NULL DEFAULT 0,
  review_note        text    NOT NULL DEFAULT '',
  rev                bigint  NOT NULL,
  created_at         bigint  NOT NULL,
  updated_at         bigint  NOT NULL,
  CHECK ((space = 'shop') = (owner_shop_id <> ''))
);
CREATE INDEX IF NOT EXISTS images_bank_product ON images (bank_product_id, space, status);
CREATE INDEX IF NOT EXISTS images_owner ON images (owner_shop_id, shop_product_id) WHERE owner_shop_id <> '';
CREATE INDEX IF NOT EXISTS images_queue ON images (space, status, created_at);
CREATE INDEX IF NOT EXISTS images_origin ON images (origin_shop_id, origin_image_id);
CREATE INDEX IF NOT EXISTS images_batch ON images (batch_id);

--  نسخه‌ها: ۱ همیشه اصل است و هرگز عوض نمی‌شود
CREATE TABLE IF NOT EXISTS image_versions (
  image_id     text    NOT NULL REFERENCES images(id),
  version      integer NOT NULL,
  kind         text    NOT NULL CHECK (kind IN ('original','processed')),
  parent       integer NOT NULL DEFAULT 0,
  blob_hash    text    NOT NULL REFERENCES image_blobs(hash),
  orientation  integer NOT NULL DEFAULT 0,       -- EXIFِ اصل؛ نسخه‌های مشتق باید چرخیده باشند
  tool         text    NOT NULL DEFAULT '',      -- ابزار/مدل/تنظیمات (۳۴.۱)
  settings     text    NOT NULL DEFAULT '{}',
  review       text    NOT NULL DEFAULT 'needs_review' CHECK (review IN ('approved','needs_review','rejected')),
  created_by   text    NOT NULL DEFAULT '',
  created_at   bigint  NOT NULL,
  PRIMARY KEY (image_id, version)
);
--  یافتنِ «همان محتوا» (تکراری) بی پیمایشِ کلِ جدول
CREATE INDEX IF NOT EXISTS image_versions_blob ON image_versions (blob_hash);

--  نسخه‌های کوچک‌شده — یک‌بارنویس (نشانیِ نسخه‌دار ⇒ cache بی‌خطر)
CREATE TABLE IF NOT EXISTS image_derivatives (
  image_id    text    NOT NULL,
  version     integer NOT NULL,
  size        text    NOT NULL CHECK (size IN ('thumb','display','zoom')),
  blob_hash   text    NOT NULL REFERENCES image_blobs(hash),
  created_at  bigint  NOT NULL,
  PRIMARY KEY (image_id, version, size),
  FOREIGN KEY (image_id, version) REFERENCES image_versions(image_id, version)
);
