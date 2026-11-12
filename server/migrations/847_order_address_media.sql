-- ============================================================
--  ۸۴۷ — عکسِ دروازه و یادداشتِ صوتیِ نشانیِ سفارش (CHK-08، §۴.۴؛ ۱۴۰۵/۰۸/۲۲)
--
--  پشتِ پرچمِ پیش‌فرض خاموشِ `shop.storefront.address_media` (`lib/order-address-media.js`؛ طراحیِ مشترک با رسانهٔ
--  نشانیِ حساب ِ ۸۳۶ در `lib/address-media.js`): مشتری با توکنِ پیگیریِ همان سفارش
--  (`PUT /api/store/:slug/orders/:token/address-media/:kind`) برای هر سفارش حداکثر یک عکسِ دروازه (`gate_photo`) و یک
--  یادداشتِ صوتی (`voice_note`) می‌گذارد؛ گذاشتنِ دوباره همان ردیف را جایگزین می‌کند. نوع فقط از روی بایت‌ها
--  (`chat-media.sniff`)، EXIF ِ عکس پیش از ذخیره پاک. خواندن از همان یک درِ دکان (`orders.contact`) و پیکِ مأموریتِ
--  همان سفارش؛ این ردیف بر رسانهٔ نشانیِ ذخیره‌شدهٔ همان سفارش (۸۳۶) مقدم است.
--  بایت در `data` (حالتِ `db`، پیش‌فرض) یا انبارِ شیء (`store_key`، همان قاعدهٔ ۲۷۵؛ `image-store-move.TABLES`).
--  فقط افزودنی؛ تا پرچم روشن نشود هیچ ردیفی نوشته نمی‌شود.
--  rollback: migrations/rollback/847_order_address_media.down.sql
-- ============================================================

CREATE TABLE IF NOT EXISTS order_address_media (
  id          text    PRIMARY KEY,
  shop_id     text    NOT NULL REFERENCES shops(id) ON DELETE CASCADE,
  order_id    text    NOT NULL,
  kind        text    NOT NULL CHECK (kind IN ('gate_photo', 'voice_note')),
  mime        text    NOT NULL,
  size        integer NOT NULL CHECK (size > 0),
  sha256      text    NOT NULL,
  data        bytea,
  store_key   text    NOT NULL DEFAULT '',
  created_at  bigint  NOT NULL,
  CONSTRAINT order_address_media_bytes CHECK (data IS NOT NULL OR store_key <> ''),
  UNIQUE (shop_id, order_id, kind)
);
