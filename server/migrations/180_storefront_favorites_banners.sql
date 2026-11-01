-- WEB-11 و WEB-24 (ویترین): علاقه‌مندی‌های حسابِ مشتری و بنرهای زمان‌بندی‌شدهٔ هر ناحیه.
-- فقط با پرچم‌های خاموشِ `shop.storefront.favorites` و `shop.storefront.banners` نوشته می‌شوند.
-- فقط افزودنی — هیچ جدولِ موجودی دست نمی‌خورد.
-- برگشت: DROP TABLE store_customer_favorites; DROP TABLE storefront_banners;

--  WEB-11: کالای دلخواهِ هر مشتری در هر ویترین (حسابِ مشتری سراسری است، فهرست مالِ هر دکان)
CREATE TABLE IF NOT EXISTS store_customer_favorites (
  customer_id  text    NOT NULL REFERENCES store_customers(id) ON DELETE CASCADE,
  shop_id      text    NOT NULL REFERENCES shops(id) ON DELETE CASCADE,
  product_id   text    NOT NULL CHECK (char_length(product_id) BETWEEN 1 AND 80),
  created_at   bigint  NOT NULL,
  PRIMARY KEY (customer_id, shop_id, product_id)
);

--  WEB-24: بنرِ دکان با بازهٔ نمایش و ناحیه‌ها (خالی = همه‌جا)
CREATE TABLE IF NOT EXISTS storefront_banners (
  id          text    PRIMARY KEY,                        -- ULID
  shop_id     text    NOT NULL REFERENCES shops(id) ON DELETE CASCADE,
  title       text    NOT NULL CHECK (char_length(title) BETWEEN 1 AND 80),
  body        text    NOT NULL DEFAULT '' CHECK (char_length(body) <= 200),
  link_type   text    NOT NULL DEFAULT 'none' CHECK (link_type IN ('none','product','category')),
  link_value  text    NOT NULL DEFAULT '',
  zone_ids    jsonb   NOT NULL DEFAULT '[]'::jsonb,
  start_at    bigint  NOT NULL,
  end_at      bigint  NOT NULL,
  sort        integer NOT NULL DEFAULT 0,
  updated_by  text    NOT NULL DEFAULT '',
  updated_at  bigint  NOT NULL,
  CHECK (end_at > start_at)
);
CREATE INDEX IF NOT EXISTS ix_storefront_banners_shop ON storefront_banners(shop_id, end_at);
