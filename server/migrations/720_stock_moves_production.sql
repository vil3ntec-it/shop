-- ============================================================
--  ۷۲۰ — نوعِ `production` در دفترِ موجودیِ سرور (INV-21: بسته‌بندیِ دوباره و ساختِ بسته؛ ۱۴۰۵/۰۸/۱۹)
--
--  شکستنِ کارتن به دانه و ساختِ بسته از اجزا یک سندِ تولید است: یک حرکتِ منفی از هر ورودی و یک حرکتِ مثبت به
--  خروجی، هر دو با همان `ref_type`/`ref_id` (‎lib/catalog-repack.js‎). نوعش همان `production` ِ هستهٔ مشترکِ
--  `license/stock-core.js` (علامت‌دار، بی دلیلِ اجباری) است که برنامه هم برای همین کار می‌نویسد؛ قیدِ ۰۸۳ آن را
--  نداشت، پس سرور سندِ تولیدِ دستگاه را «رسیدِ خرید» + «اصلاح» می‌نوشت.
--
--  فقط گشادتر کردنِ یک قید: هیچ ردیفی عوض نمی‌شود. ⛔ `stock_moves` فقط افزودنی می‌ماند.
--  ⛔ پیش‌فرض خاموش: ردیفِ `production` فقط با پرچمِ `shop.catalog_repack` (و `shop.catalog`) نوشته می‌شود.
--  برگشت: migrations/rollback/720_stock_moves_production.down.sql
-- ============================================================

ALTER TABLE stock_moves DROP CONSTRAINT IF EXISTS stock_moves_type_check;
ALTER TABLE stock_moves ADD CONSTRAINT stock_moves_type_check CHECK (type IN (
  'purchase_receipt','sale','customer_return','supplier_return','adjustment',
  'count_adjust','damage','expired','transfer_out','transfer_in','reserve','release','production'));
