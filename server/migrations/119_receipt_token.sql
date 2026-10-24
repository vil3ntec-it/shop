-- ════════════════════════════════════════════════════════════════════
--  ۱۱۹ — فاکتورِ نسخهٔ ۲: QRِ عمومیِ هر فروش (`lib/receipt-public.js`)
-- ════════════════════════════════════════════════════════════════════
--  دستگاه هنگامِ ثبتِ فروش `receiptToken` (۲۶ نویسه، ۱۲۸ بیتِ تصادفی) را روی
--  ردیفِ `sales` می‌گذارد و همگام می‌کند؛ صفحهٔ `/r/<نشانِ دکان>/<توکن>` فروش را
--  با همین توکن پیدا می‌کند و قلم‌هایش را با `saleId`.
--
--  فقط نمایه، بی ستون و بی تغییرِ داده. همه جزئی و فقط `app = 'shop'`:
--  ردیف‌های پمپ هرگز نمایه نمی‌شوند و `sync-v1.js` دست نمی‌خورد.
--  پرس‌وجوها همین شرط‌ها (و `data ? 'receiptToken'`) را صریح دارند تا نمایه گرفته شود.

-- فروش با توکنِ فاکتور (Sync v1 و v3)
CREATE INDEX IF NOT EXISTS idx_sync_rows_receipt_token
  ON sync_rows ((data->>'receiptToken'))
  WHERE app = 'shop' AND table_name = 'sales' AND data ? 'receiptToken';

CREATE INDEX IF NOT EXISTS idx_sync_v3_rows_receipt_token
  ON sync_v3_rows ((data->>'receiptToken'))
  WHERE app = 'shop' AND table_name = 'sales' AND data ? 'receiptToken';

-- قلم‌های یک فروش، بی پیمودنِ همهٔ قلم‌های دکان: درِ عمومی با یک توکنِ معتبر
-- هر بار همین را می‌پرسد، پس زمانش باید به اندازهٔ دکان بسته نباشد.
CREATE INDEX IF NOT EXISTS idx_sync_rows_sale_items_by_sale
  ON sync_rows (account_id, (data->>'saleId'))
  WHERE app = 'shop' AND table_name = 'saleItems';

CREATE INDEX IF NOT EXISTS idx_sync_v3_rows_sale_items_by_sale
  ON sync_v3_rows (account_id, (data->>'saleId'))
  WHERE app = 'shop' AND table_name = 'saleItems';
