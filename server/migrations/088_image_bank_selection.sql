-- ============================================================
--  بانکِ تصاویر — کدام عکس برای کالای دکان نمایش داده می‌شود (v3 ۳۷.۶، ۳۷.۷)
--
--  هر ردیف «انتخابِ صریحِ» یک دکان برای یک کالای خودش است. قیمت، موجودی،
--  قفسه و SKUِ دکان این‌جا نیستند (۳۷.۶ بندِ ۶) — فقط منبعِ عکس.
--
--  ⛔ عکسِ مرکزی به نسخهٔ مشخص سنجاق می‌شود: نسخهٔ تازهٔ مرکزی فقط
--     «پیشنهاد» است و بی اجازهٔ دکان جایگزین نمی‌شود (۳۷.۷، IMG-BANK-09).
--  ⛔ انتخابِ عکسِ خودِ دکان، آخرین انتخابِ مرکزی را نگه می‌دارد تا بازگشت
--     به عکسِ مرکزی ممکن بماند (IMG-BANK-16).
--  ⚠️ کالای بی ردیف همان رفتارِ امروز را دارد: عکسِ همگام‌شدهٔ
--     `product_photos` (۰۴۵) — هیچ عکسِ مرکزی خودکار جای آن نمی‌نشیند.
-- ============================================================

CREATE TABLE IF NOT EXISTS shop_product_images (
  shop_id          text    NOT NULL REFERENCES shops(id) ON DELETE CASCADE,
  product_id       text    NOT NULL,              -- شناسهٔ کالای خودِ دکان
  source           text    NOT NULL CHECK (source IN ('central','shop','none')),
  image_id         text    NOT NULL DEFAULT '',
  version          integer NOT NULL DEFAULT 0,
  match_status     text    NOT NULL DEFAULT 'unconfirmed',
  confirmed_by     text    NOT NULL DEFAULT '',  -- تأییدِ دستیِ تطبیقِ غیرِ قطعی
  central_image_id text    NOT NULL DEFAULT '',  -- آخرین عکسِ مرکزیِ انتخاب‌شده
  central_version  integer NOT NULL DEFAULT 0,
  central_match    text    NOT NULL DEFAULT '',  -- تطبیقِ همان انتخابِ مرکزی (برای بازگشت)
  central_confirmed_by text NOT NULL DEFAULT '',
  bank_product_id  text    NOT NULL DEFAULT '',
  updated_by       text    NOT NULL DEFAULT '',
  updated_at       bigint  NOT NULL,
  PRIMARY KEY (shop_id, product_id)
);
CREATE INDEX IF NOT EXISTS shop_product_images_image ON shop_product_images (image_id) WHERE image_id <> '';

--  «عکسِ اشتباه / بستهٔ قدیمی» (۳۷.۷) — صفِ بررسیِ مدیر
CREATE TABLE IF NOT EXISTS image_reports (
  id          text    PRIMARY KEY,
  image_id    text    NOT NULL,
  shop_id     text    NOT NULL,
  kind        text    NOT NULL CHECK (kind IN ('wrong_product','old_packaging','quality','license','other')),
  note        text    NOT NULL DEFAULT '',
  status      text    NOT NULL DEFAULT 'open' CHECK (status IN ('open','resolved','dismissed')),
  created_by  text    NOT NULL DEFAULT '',
  created_at  bigint  NOT NULL
);
CREATE INDEX IF NOT EXISTS image_reports_open ON image_reports (status, created_at);
