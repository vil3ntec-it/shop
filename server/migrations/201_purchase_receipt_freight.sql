-- ============================================================
--  کرایهٔ رسیدِ سفارشِ خرید — PUR-06 (S19: ۲۸۰ افغانی به حمل‌کننده)
--
--  purchase_receipts.freight_minor  کرایه/هزینهٔ جانبیِ همین رسید (پول به حمل‌کننده، نه تأمین‌کننده).
--                                   برنامه آن را به نسبتِ ارزشِ ردیف‌های پذیرفته روی بهای همان ردیف‌ها
--                                   پخش می‌کند (`StockLedger.spreadLanded`)؛ به بدهیِ تأمین‌کننده نمی‌رود.
--
--  فقط افزودنی با پیش‌فرضِ ۰ ⇒ هر رسیدِ پیشین همان می‌ماند. برگشت: rollback/201_purchase_receipt_freight.down.sql
-- ============================================================

ALTER TABLE purchase_receipts
  ADD COLUMN IF NOT EXISTS freight_minor bigint NOT NULL DEFAULT 0
    CHECK (freight_minor BETWEEN 0 AND 100000000000);
