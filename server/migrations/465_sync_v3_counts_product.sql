-- ============================================================
--  ۴۶۵ — نمایهٔ «شمارشِ دیررسیده» ِ Sync v3 (فقط نمایه؛ PERF-02، docs/PERF-LOAD-fa.md ۱۴۰۵/۰۸/۱۹ (سوم)).
--
--  هر حرکتِ کالا با مقدارِ ناصفر (هر قلمِ فروش، خرید، تعدیل، و تعدیلِ خودِ شمارش) تازه‌ترین شمارشِ همان کالا با HLC ≥ حرکت را
--  می‌جوید: `sync-v3-store.lateCount` (راهِ تک‌تک) و شاخهٔ شمارشِ `STOCK_SQL` در `sync-v3-batch` (`productId = ANY`).
--  تنها راهِ پیشین پیشوندِ کلیدِ `sync_v3_rows` تا `table_name` بود ⇒ همهٔ ردیف‌های شمارشِ دکان خوانده و `productId` فیلتر
--  می‌شد — خطی با شمارِ ردیف‌های شمارش (هر انبارگردانیِ کامل یک ردیف برای هر کالا). با این نمایه فقط ردیف‌های همان کالا.
--  هم‌شکلِ `ix_sync_v3_rows_moves_product` (۱۹۸)؛ `row_hlc` برای `ORDER BY row_hlc DESC LIMIT 1` و `row_hlc >= $5`.
--
--  جزئی (فقط `stockCounts` ِ دکان): ردیف‌های دیگر هزینهٔ نوشتن ندارند. هیچ ستون، جدول یا داده‌ای عوض نمی‌شود؛ پمپ دست نمی‌خورد.
--  ⚠️ روی پایگاهِ بزرگِ در حالِ کار، پیش از به‌روزرسانی همین را با `CREATE INDEX CONCURRENTLY` (بیرون از تراکنش) بسازید؛
--     این مهاجرت با `IF NOT EXISTS` آن‌گاه کاری نمی‌کند.
--  rollback: migrations/rollback/465_sync_v3_counts_product.down.sql
-- ============================================================
CREATE INDEX IF NOT EXISTS ix_sync_v3_rows_counts_product
  ON sync_v3_rows (account_id, (data->>'productId'), row_hlc)
  WHERE app = 'shop' AND table_name = 'stockCounts';
