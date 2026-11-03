-- ============================================================
--  فاکتورِ جدای تأمین‌کننده برای سفارشِ خرید — PUR-14 (§۶.۶، تطبیقِ سه‌طرفهٔ سفارش ⇔ رسید ⇔ فاکتور)
--
--  purchase_bills  فاکتوری که خودِ تأمین‌کننده داده (شماره، تاریخ، ردیف‌ها)، جدا از رسیدِ دریافت. برنامه آن را
--                  با `PurchaseOrderCore.match` کنارِ سفارش و رسیدها می‌گذارد؛ پشتِ همان پرچمِ خاموشِ
--                  `shop.purchase_orders` (lib/purchase-orders.js).
--
--  ⛔ فقط افزودنی: ویرایش ندارد؛ فاکتورِ نادرست با «باطل» (دلیل، کاربر، زمان) کنار می‌رود و شماره‌اش آزاد می‌شود.
--  ⛔ کلیدِ یکتا = شناسهٔ فاکتور که برنامه می‌سازد (همان کلید و همان بدنه ⇒ «تکراری»، بدنهٔ دیگر ⇒ ۴۰۹).
--  ⛔ یک شمارهٔ فاکتورِ فعال در هر سفارش (نمایهٔ یکتای جزئی).
--  پول یک‌صدمِ افغانی و مقدار هزارم (عددِ صحیح، درونِ jsonb ِ ردیف‌ها).
--  فقط جدولِ تازه — هیچ دادهٔ موجودی عوض نمی‌شود. برگشت: rollback/208_purchase_bills.down.sql
-- ============================================================

CREATE TABLE IF NOT EXISTS purchase_bills (
  shop_id       text   NOT NULL,
  id            text   NOT NULL,
  po_id         text   NOT NULL,
  bill_no       text   NOT NULL,
  bill_date     text,
  lines         jsonb  NOT NULL,
  total_minor   bigint NOT NULL CHECK (total_minor >= 0),
  request_hash  text   NOT NULL,
  note          text   NOT NULL DEFAULT '',
  created_by    text   NOT NULL DEFAULT '',
  created_at    bigint NOT NULL,
  voided_at     bigint,
  voided_by     text,
  void_reason   text,
  PRIMARY KEY (shop_id, id),
  FOREIGN KEY (shop_id, po_id) REFERENCES purchase_orders (shop_id, id) ON DELETE CASCADE
);
CREATE INDEX IF NOT EXISTS ix_purchase_bills_po ON purchase_bills (shop_id, po_id, created_at);
CREATE UNIQUE INDEX IF NOT EXISTS ux_purchase_bills_no ON purchase_bills (shop_id, po_id, bill_no) WHERE voided_at IS NULL;
