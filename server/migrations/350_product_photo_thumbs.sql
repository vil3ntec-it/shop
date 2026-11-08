-- ============================================================
--  ۳۵۰ — نسخهٔ کارتِ عکسِ همگام‌شدهٔ کالا (WEB-15، IMG-12؛ `lib/product-photos.js`)
--
--  کارتِ ویترین (`/api/store/:slug/products/:id/thumb`) تا امروز همان عکسِ کامل (~۳۰KB) را می‌گرفت: سرور رمزگشای
--  تصویر ندارد (`lib/image-pipeline.js`، `resize` = missing). پس نسخهٔ ۳۲۰ پیکسلی را برنامه می‌سازد و این‌جا فقط
--  سنجیده و نگه داشته می‌شود — یک ردیف برای هر عکسِ کالا.
--
--  ⛔ `of_hash` = همان `product_photos.hash` ِ اصلی که این نسخه از آن ساخته شد. نسخه فقط وقتی سرو می‌شود که با
--     اصلِ امروز یکی باشد؛ اصلِ تازه بی نسخهٔ کوچک ⇒ همان عکسِ کامل (هرگز نسخهٔ کوچکِ عکسِ پیشین).
--  ⛔ هر پرس‌وجو `app` و `account_id` را شرط می‌کند (مثلِ ۰۴۵)؛ بایت مثلِ ۲۵۵ در `data` یا انبارِ شیء (`store_key`).
--  ⛔ فقط دکان (`app = 'shop'`، قیدِ پایین): عکسِ پمپ کارت ندارد و درِ کارت (`routes/sync-photo-thumbs.js`) پمپ را ۴۰۳ می‌کند.
--  ⛔ فقط افزودنی: جدولِ تازه، هیچ ستونی از ۰۴۵ دست نمی‌خورد.
--  برگشت: migrations/rollback/350_product_photo_thumbs.down.sql (آزموده در test/product-photo-thumbs.test.js)
-- ============================================================

CREATE TABLE IF NOT EXISTS product_photo_thumbs (
  app         text    NOT NULL,
  account_id  text    NOT NULL,
  product_id  text    NOT NULL,
  of_hash     text    NOT NULL,
  data        bytea,
  store_key   text    NOT NULL DEFAULT '',
  hash        text    NOT NULL,
  mime        text    NOT NULL,
  width       integer NOT NULL,
  height      integer NOT NULL,
  size        integer NOT NULL,
  updated_at  bigint  NOT NULL,
  PRIMARY KEY (app, account_id, product_id),
  FOREIGN KEY (app, account_id, product_id) REFERENCES product_photos (app, account_id, product_id) ON DELETE CASCADE,
  CONSTRAINT product_photo_thumbs_shop CHECK (app = 'shop'),
  CONSTRAINT product_photo_thumbs_bytes CHECK (data IS NOT NULL OR store_key <> ''),
  CONSTRAINT product_photo_thumbs_dims CHECK (width BETWEEN 1 AND 400 AND height BETWEEN 1 AND 400),
  CONSTRAINT product_photo_thumbs_size CHECK (size BETWEEN 1 AND 65536),
  CONSTRAINT product_photo_thumbs_mime CHECK (mime IN ('image/jpeg', 'image/png', 'image/webp'))
);

--  `gc` ِ ابزارِ انبار («این شیء ردیف دارد؟») — همان الگوی ۲۵۵
CREATE INDEX IF NOT EXISTS ix_product_photo_thumbs_store_key ON product_photo_thumbs (store_key) WHERE store_key <> '';
