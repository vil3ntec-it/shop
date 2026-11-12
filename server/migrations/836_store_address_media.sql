-- ============================================================
--  CRM-09 / CHK-08 (§۴.۴): «یادداشتِ صوتی و عکسِ دروازه، اختیاری» برای نشانیِ تحویل
--
--  store_address_media: هر نشانیِ نام‌دارِ حسابِ مشتریِ ویترین (store_customer_addresses) حداکثر یک
--  «عکسِ دروازه» و یک «یادداشتِ صوتی» دارد؛ فقط خودِ مشتری می‌نویسد و پاک می‌کند. پاک شدنِ نشانی یا حساب ⇒ آبشاری.
--  بایت در `data` (حالتِ db ِ انبار) یا شیءِ انبارِ عکس در `store_key` (`lib/image-store.js`، tenant ِ `customer:<id>`).
--
--  online_order_address_refs: سفارشی که با نشانیِ نام‌دارِ همان حساب ثبت شد ⇐ همان نشانی. دکان (صاحب/مدیر،
--  `orders.contact`) رسانهٔ همان نشانی را فقط تا وقتی مشتری نگهش داشته می‌بیند — نسخه‌ای نزدِ دکان نمی‌ماند.
--
--  ⛔ پشتِ پرچمِ خاموشِ `shop.storefront.address_media` (`lib/store-address-media.js`).
--  بازگشت: migrations/rollback/836_store_address_media.down.sql
-- ============================================================

CREATE TABLE IF NOT EXISTS store_address_media (
  id           text    PRIMARY KEY,                                         -- ULID
  customer_id  text    NOT NULL REFERENCES store_customers(id) ON DELETE CASCADE,
  address_id   text    NOT NULL REFERENCES store_customer_addresses(id) ON DELETE CASCADE,
  kind         text    NOT NULL CHECK (kind IN ('gate_photo', 'voice_note')),
  mime         text    NOT NULL CHECK (length(mime) BETWEEN 3 AND 40),
  size_bytes   integer NOT NULL CHECK (size_bytes > 0),
  data         bytea,
  store_key    text    NOT NULL DEFAULT '',
  created_at   bigint  NOT NULL,
  CHECK (data IS NOT NULL OR store_key <> ''),
  UNIQUE (address_id, kind)
);
CREATE INDEX IF NOT EXISTS ix_store_address_media_customer ON store_address_media(customer_id);

CREATE TABLE IF NOT EXISTS online_order_address_refs (
  order_id     text    PRIMARY KEY REFERENCES online_orders(id) ON DELETE CASCADE,
  shop_id      text    NOT NULL,
  address_id   text    NOT NULL,
  created_at   bigint  NOT NULL
);
CREATE INDEX IF NOT EXISTS ix_online_order_address_refs_address ON online_order_address_refs(address_id);
