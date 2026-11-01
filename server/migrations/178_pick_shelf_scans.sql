-- ============================================================
--  قفسهٔ انتظار و اسکنِ «آماده» — PCK-10 (v3 §۶.۱۷)
--
--  ⛔ پشتِ همان پرچمِ خاموشِ `shop.orders.picking` (`lib/pick-shelf.js`): تا روشن نشود هیچ ردیفی
--  نوشته نمی‌شود.
--
--  order_pick_shelf_scans  هر کیسهٔ بسته‌شده که با برچسبِ QR ِ خودش (`VLNB-<سفارش>-<کیسه>-<همه>`، PCK-09)
--                          روی قفسهٔ انتظار اسکن شد: کدام جا، کی، چه کسی. هر کیسه یک ردیف (اسکنِ دوباره
--                          فقط جا و زمان را تازه می‌کند). همهٔ کیسه‌ها اسکن شد ⇒ `order_picks.ready_scanned_at`
--                          و `shelf_slot` (ستون‌های آمادهٔ ۱۴۲) و برای «دریافت از دکان» گذارِ «آمادهٔ دریافت».
--
--  جمع‌آوریِ گروهی (PCK-12) جدولِ تازه نمی‌خواهد: `order_picks.batch_id` (۱۴۲).
--  فقط افزودنی. برگشت: DROP TABLE order_pick_shelf_scans.
-- ============================================================

CREATE TABLE IF NOT EXISTS order_pick_shelf_scans (
  order_id              text    NOT NULL REFERENCES order_picks(order_id) ON DELETE CASCADE,
  shop_id               text    NOT NULL,
  bag                   integer NOT NULL CHECK (bag BETWEEN 1 AND 50),
  slot                  text    NOT NULL,
  scanned_by            text    NOT NULL DEFAULT '',
  scanned_at            bigint  NOT NULL,
  PRIMARY KEY (order_id, bag)
);
CREATE INDEX IF NOT EXISTS ix_pick_shelf_scans_shop ON order_pick_shelf_scans(shop_id, scanned_at);
--  جمع‌آوریِ گروهی: برگه‌های یک دسته
CREATE INDEX IF NOT EXISTS ix_order_picks_batch ON order_picks(shop_id, batch_id) WHERE batch_id <> '';
