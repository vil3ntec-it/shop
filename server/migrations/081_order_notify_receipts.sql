-- ============================================================
--  رسیدِ دستگاه برای اعلانِ سفارش — v3 §۳۰.۱ بندِ ۷
--
--  «سرویسِ پوش پذیرفت» اثباتِ رسیدن نیست؛ رسیدن و باز شدن را فقط خودِ
--  دستگاه می‌تواند بگوید. هر دستگاه برای هر اعلان حداکثر یک «رسید»
--  (received) و یک «باز شد» (opened) دارد — تکرار ردیفِ دوم نمی‌سازد.
--
--  ⛔ پیش‌فرض خاموش: بی `shop.order_notify` درِ رسید بسته است و ردیفی
--  نوشته نمی‌شود. بازگشت: هیچ جای دیگری این جدول را نمی‌خواند.
-- ============================================================

CREATE TABLE IF NOT EXISTS order_notify_receipts (
  notif_id    text    NOT NULL REFERENCES order_notify_outbox(id) ON DELETE CASCADE,
  shop_id     text    NOT NULL,
  device_key  text    NOT NULL,
  kind        text    NOT NULL CHECK (kind IN ('received', 'opened')),
  user_id     text    NOT NULL DEFAULT '',
  at          bigint  NOT NULL,
  PRIMARY KEY (notif_id, device_key, kind)
);
