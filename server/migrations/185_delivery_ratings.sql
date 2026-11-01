-- ============================================================
--  امتیازِ دوطرفهٔ تحویل — DLV-18 (v3 §۶.۱۸)
--
--  ⛔ پشتِ پرچمِ خاموشِ `shop.delivery_ratings` (`lib/delivery-ratings.js`): تا روشن نشود هیچ ردیفی نوشته
--  نمی‌شود.
--
--  delivery_ratings  پس از «تحویل شد» ِ پیکِ دکان، یک بار از هر سو: مشتری به پیک (با توکنِ پیگیری) و پیک به
--                    تحویل (گرفتنِ بسته از مشتری). فقط ستاره و برچسب از فهرستِ ثابت — بی متنِ آزاد. فقط افزوده
--                    می‌شود؛ تکرارِ همان امتیاز بی‌اثر و امتیازِ دیگر رد. فقط صاحب و مدیرِ دکان می‌بینند.
--
--  فقط افزودنی. برگشت: DROP TABLE delivery_ratings.
-- ============================================================

CREATE TABLE IF NOT EXISTS delivery_ratings (
  id              text     PRIMARY KEY,                    -- ULID
  shop_id         text     NOT NULL REFERENCES shops(id) ON DELETE CASCADE,
  assignment_id   text     NOT NULL REFERENCES delivery_assignments(id) ON DELETE CASCADE,
  order_id        text     NOT NULL,
  courier_id      text     NOT NULL DEFAULT '',
  rater           text     NOT NULL CHECK (rater IN ('customer','courier')),
  stars           smallint NOT NULL CHECK (stars BETWEEN 1 AND 5),
  tags            jsonb    NOT NULL DEFAULT '[]'::jsonb,
  request_hash    text     NOT NULL,
  created_at      bigint   NOT NULL,
  UNIQUE (assignment_id, rater)
);
CREATE INDEX IF NOT EXISTS ix_delivery_ratings_shop ON delivery_ratings(shop_id, created_at);
CREATE INDEX IF NOT EXISTS ix_delivery_ratings_courier ON delivery_ratings(shop_id, courier_id, rater);
