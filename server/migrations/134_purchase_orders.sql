-- PUR-02، PUR-04 (§۶.۶): سفارشِ خرید از تأمین‌کننده و رسیدهای دریافتِ آن — پشتِ پرچمِ خاموشِ
-- `shop.purchase_orders` (lib/purchase-orders.js). قاعده‌ها: lib/purchase-order-core.js و همتای Kotlin.
--
-- ⛔ وضعیتِ ذخیره‌شده فقط draft | sent | cancelled است؛ «بخشی دریافت‌شده» و «کامل» از رسیدها
--    درمی‌آیند و هرگز ستون نیستند.
-- ⛔ رسید فقط افزودنی است: کلیدِ یکتا = شناسهٔ رسید (همان کلید ⇒ «تکراری»، بی اثرِ دوم).
-- پول یک‌صدمِ افغانی و مقدار هزارم (عددِ صحیح، درونِ jsonb ِ ردیف‌ها).
-- فقط جدولِ تازه — هیچ دادهٔ موجودی عوض نمی‌شود. برگشت: DROP TABLE purchase_receipts, purchase_orders.

CREATE TABLE IF NOT EXISTS purchase_orders (
  shop_id        text    NOT NULL REFERENCES shops(id) ON DELETE CASCADE,
  id             text    NOT NULL,
  po_no          integer NOT NULL,
  client_key     text    NOT NULL,
  request_hash   text    NOT NULL,
  supplier_id    text    NOT NULL,
  supplier_name  text    NOT NULL DEFAULT '',
  state          text    NOT NULL CHECK (state IN ('draft', 'sent', 'cancelled')),
  lines          jsonb   NOT NULL,
  expected_date  text,
  note           text    NOT NULL DEFAULT '',
  version        integer NOT NULL DEFAULT 1,
  created_by     text    NOT NULL DEFAULT '',
  created_at     bigint  NOT NULL,
  updated_at     bigint  NOT NULL,
  sent_at        bigint,
  cancelled_at   bigint,
  cancel_reason  text,
  PRIMARY KEY (shop_id, id),
  UNIQUE (shop_id, po_no),
  UNIQUE (shop_id, client_key)
);
CREATE INDEX IF NOT EXISTS ix_purchase_orders_recent ON purchase_orders (shop_id, updated_at DESC, id);

CREATE TABLE IF NOT EXISTS purchase_receipts (
  shop_id     text   NOT NULL,
  id          text   NOT NULL,
  po_id       text   NOT NULL,
  lines       jsonb  NOT NULL,
  posted      jsonb  NOT NULL,
  total_minor bigint NOT NULL,
  note        text   NOT NULL DEFAULT '',
  created_by  text   NOT NULL DEFAULT '',
  created_at  bigint NOT NULL,
  PRIMARY KEY (shop_id, id),
  FOREIGN KEY (shop_id, po_id) REFERENCES purchase_orders (shop_id, id) ON DELETE CASCADE
);
CREATE INDEX IF NOT EXISTS ix_purchase_receipts_po ON purchase_receipts (shop_id, po_id, created_at);
