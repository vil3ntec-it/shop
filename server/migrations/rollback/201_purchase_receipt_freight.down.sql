-- ============================================================
--  Rollback ِ 201_purchase_receipt_freight.sql — فقط با دست.
--
--  ستونِ کرایهٔ رسید برداشته می‌شود؛ کرایهٔ ثبت‌شده در سرور می‌رود (بهای پخش‌شده در دفترِ برنامه می‌ماند).
--
--  اجرا (در یک تراکنش):
--    psql "$DATABASE_URL" -1 -f migrations/rollback/201_purchase_receipt_freight.down.sql
-- ============================================================

ALTER TABLE purchase_receipts DROP COLUMN IF EXISTS freight_minor;
DELETE FROM schema_migrations WHERE version = '201_purchase_receipt_freight.sql';
