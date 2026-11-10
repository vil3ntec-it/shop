-- ============================================================
--  ۴۳۵ — نگه‌داشتنِ کوتاهِ موجودی برای سفارشِ آنلاین (O-06 = ب؛ ORD-20، CHK-18؛ ۱۴۰۵/۰۸/۲۰)
--
--  پشتِ پرچمِ پیش‌فرض خاموشِ `shop.stock_reserve` (با `shop.catalog`) و فقط با موجودیِ «تازه»
--  (`lib/stock-reserve.js`). جدولِ `stock_reservations` (۰۸۳) همان می‌ماند؛ فقط دو ستون:
--    kind      legacy = ردیفِ کدِ پیش از تصمیمِ ۱۴۰۵/۰۷/۲۷ که `catalog.retireOrderStock` کنار می‌گذارد
--              soft   = نگه‌داشتنِ تازه با انقضا؛ retire هرگز به آن دست نمی‌زند
--    short_at  فروشِ حضوری (INV-19، هرگز رد نمی‌شود) موجودیِ نگه‌داشته را برد ⇒ سفارش «در بررسی»
--              (NULL = پوشش دارد)
--  فقط افزودنی: همهٔ ردیف‌های پیشین `legacy` می‌شوند و رفتارشان همان می‌ماند. تا پرچم روشن نشود
--  هیچ ردیفِ `soft` نوشته نمی‌شود.
--  rollback: migrations/rollback/435_stock_soft_reservations.down.sql
-- ============================================================

ALTER TABLE stock_reservations ADD COLUMN IF NOT EXISTS kind text NOT NULL DEFAULT 'legacy';
ALTER TABLE stock_reservations DROP CONSTRAINT IF EXISTS stock_reservations_kind_check;
ALTER TABLE stock_reservations ADD CONSTRAINT stock_reservations_kind_check CHECK (kind IN ('legacy', 'soft'));
ALTER TABLE stock_reservations ADD COLUMN IF NOT EXISTS short_at bigint;
--  «آزاد = قابل‌فروش − نگه‌داشتهٔ فعال» و «این دکان نگه‌داشتهٔ نرم دارد؟» (هر خواندنِ ویترین/کارمند)
CREATE INDEX IF NOT EXISTS ix_stock_reservations_soft_held
  ON stock_reservations(shop_id, product_id, created_at) WHERE status = 'held' AND kind = 'soft';
