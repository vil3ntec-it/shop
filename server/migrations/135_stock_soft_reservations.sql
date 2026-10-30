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
-- «سفارش به بررسی رفت»: همان صفِ پایدارِ اعلانِ سفارش (۰۸۰، پشتِ `shop.order_notify`) رویدادِ دوم می‌گیرد
-- (`order_review`، هر سفارش یک بار با همان یکتاییِ order_id + event). بازه فقط بازتر می‌شود.
-- برگشت: ردیف‌های `order_review` را پاک کنید و همان CHECK ِ ۰۸۰ را بگذارید.
ALTER TABLE order_notify_outbox DROP CONSTRAINT IF EXISTS order_notify_outbox_event_check;
ALTER TABLE order_notify_outbox ADD CONSTRAINT order_notify_outbox_event_check
  CHECK (event IN ('order_created', 'order_review'));
