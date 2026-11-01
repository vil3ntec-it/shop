-- ============================================================
--  عکسِ سفارشِ بسته‌بندی‌شده به‌عنوانِ مدرک — PCK-08 (v3 §۶.۱۷)
--
--  ⛔ پشتِ همان پرچمِ خاموشِ `shop.orders.picking` (`lib/pick-photos.js`): تا روشن نشود هیچ ردیفی نوشته
--  نمی‌شود.
--
--  order_pick_photos  عکسِ کیسه‌های بسته (فقط بسته، نه شخص — `parcelOnly` شرط است) پس از بسته‌بندی و پیش از
--                     تحویل؛ حداکثر ۳ برای هر سفارش. همان سقف و سنجشِ نوعِ عکسِ تحویل (`product-photos.sniff`)؛
--                     کلیدِ یکتای دستگاه ⇒ ارسالِ دوباره بی‌خطر. فقط افزوده می‌شود (مدرک ویرایش نمی‌شود).
--
--  فقط افزودنی. برگشت: DROP TABLE order_pick_photos.
-- ============================================================

CREATE TABLE IF NOT EXISTS order_pick_photos (
  id              text    PRIMARY KEY,                     -- ULID
  shop_id         text    NOT NULL REFERENCES shops(id) ON DELETE CASCADE,
  order_id        text    NOT NULL REFERENCES order_picks(order_id) ON DELETE CASCADE,
  event_key       text    NOT NULL,
  mime            text    NOT NULL,
  size            integer NOT NULL CHECK (size > 0),
  hash            text    NOT NULL,
  data            bytea   NOT NULL,
  bags            integer NOT NULL DEFAULT 0,
  device_time     bigint,
  created_by      text    NOT NULL DEFAULT '',
  created_at      bigint  NOT NULL,
  UNIQUE (shop_id, event_key)
);
CREATE INDEX IF NOT EXISTS ix_order_pick_photos_order ON order_pick_photos(order_id, created_at);
