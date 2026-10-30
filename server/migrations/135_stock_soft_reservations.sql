-- M5 (قراردادِ بازار §۱.۴ و §۳): رزروِ «نرم» ِ موجودی برای سفارشِ آنلاین، پشتِ پرچمِ خاموشِ
-- `shop.stock_reserve` (و `shop.catalog`)، فقط وقتی موجودی تازه است (lib/stock-reserve.js).
--   kind     legacy = ردیفِ کدِ پیشین (پیش از تصمیمِ ۱۴۰۵/۰۷/۲۷) که `catalog.retireOrderStock` کنار می‌گذارد
--            soft   = رزروِ نرمِ M5 با انقضای `catalog_settings.reserve_ttl_sec`؛ retire به آن دست نمی‌زند
--   short_at فروشِ حضوری (INV-19، هرگز رد نمی‌شود) موجودیِ رزروشده را برد ⇒ سفارش «نیازمندِ بررسی»
--            (NULL = پوشش دارد)
-- فقط افزودنی — همهٔ ردیف‌های پیشین `legacy` می‌شوند و رفتارشان همان می‌ماند.
-- برگشت: DROP INDEX و DROP COLUMN.
ALTER TABLE stock_reservations ADD COLUMN IF NOT EXISTS kind text NOT NULL DEFAULT 'legacy'
  CHECK (kind IN ('legacy', 'soft'));
ALTER TABLE stock_reservations ADD COLUMN IF NOT EXISTS short_at bigint;
CREATE INDEX IF NOT EXISTS ix_stock_reservations_soft_held
  ON stock_reservations(shop_id, product_id, created_at) WHERE status = 'held' AND kind = 'soft';
