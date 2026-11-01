-- RPT-14: جست‌وجوهای بی‌نتیجهٔ ویترین برای «گزارشِ فروشِ اینترنتی» (lib/order-online-report.js).
-- هر ردیف = یک عبارتِ نرمال‌شده (حداکثر ۶۰ نویسه، بی شبهِ شماره تلفن) در یک روزِ UTC ِ یک دکان
-- و شمارش. فقط با پرچمِ `shop.orders.online_report` نوشته می‌شود؛ سقفِ عبارتِ تازه برای هر دکان در
-- هر روز در کد است و ۹۲ روز می‌ماند (sweep ِ نگهداری).
-- فقط افزودنی — هیچ جدولِ موجودی دست نمی‌خورد. برگشت: DROP TABLE storefront_search_misses.
CREATE TABLE IF NOT EXISTS storefront_search_misses (
  shop_id  text    NOT NULL REFERENCES shops(id) ON DELETE CASCADE,
  day      integer NOT NULL,                 -- floor(ms / 86400000)
  q        text    NOT NULL CHECK (char_length(q) BETWEEN 2 AND 60),
  n        integer NOT NULL DEFAULT 1 CHECK (n > 0),
  last_at  bigint  NOT NULL,
  PRIMARY KEY (shop_id, day, q)
);
CREATE INDEX IF NOT EXISTS ix_storefront_search_misses_day ON storefront_search_misses(day);
