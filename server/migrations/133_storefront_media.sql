-- M3 (قراردادِ بازار §۱.۱، §۳): لوگو و تصویرِ سرِ ویترینِ دکان.
--
-- ⛔ پشتِ پرچمِ `shop.storefront.media` (پیش‌فرض خاموش) — بی پرچم هیچ ردیفی
-- ساخته نمی‌شود و نمای عمومی همان امروز است.
--
--   kind     ── فقط `logo` (مربع) یا `header` (۳ به ۱). اندازه‌های ثابت را
--               `lib/storefront-media.js` (SPECS) می‌سنجد؛ برنامه می‌سازد.
--   version  ── هر بارگذاری یک نسخهٔ تازه، هرگز تکرار نمی‌شود (حتی پس از
--               «برداشتن»)؛ نشانیِ عمومیِ هر نسخه `immutable` است.
--   current  ── نسخهٔ نمایش‌داده؛ در هر (دکان، نوع) حداکثر یکی. «برداشتن» یعنی
--               هیچ ردیفی current نیست. فقط دو نسخهٔ آخر نگه داشته می‌شوند.
--   data     ── بایت‌های پاک (بی EXIF/GPS/XMP)، جدا از `image_blobs` تا
--               پاک‌سازیِ بانکِ عکس یا لوگوی فاکتور هرگز به این‌ها دست نزند.
--
-- فقط افزودنی. برگشت: DROP TABLE storefront_media.
CREATE TABLE IF NOT EXISTS storefront_media (
  shop_id     text    NOT NULL REFERENCES shops(id) ON DELETE CASCADE,
  kind        text    NOT NULL CHECK (kind IN ('logo', 'header')),
  version     integer NOT NULL CHECK (version >= 1),
  mime        text    NOT NULL CHECK (mime IN ('image/jpeg', 'image/png', 'image/webp')),
  width       integer NOT NULL CHECK (width > 0),
  height      integer NOT NULL CHECK (height > 0),
  bytes       integer NOT NULL CHECK (bytes > 0),
  hash        text    NOT NULL,
  data        bytea   NOT NULL,
  current     boolean NOT NULL DEFAULT true,
  created_by  text    NOT NULL DEFAULT '',
  created_at  bigint  NOT NULL,
  PRIMARY KEY (shop_id, kind, version)
);

CREATE UNIQUE INDEX IF NOT EXISTS ux_storefront_media_current
  ON storefront_media(shop_id, kind) WHERE current;
