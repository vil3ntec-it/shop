-- ============================================================
--  PERF-03 — نمایهٔ ترتیبِ نامِ کالا برای کاتالوگِ ویترین (فقط نمایه).
--
--  کاتالوگِ عمومی (`lib/storefront.js` ⇒ catalog) به ترتیبِ نام و با
--  صفحه‌بندیِ کلیدیِ (نام، شناسه) می‌خواند:
--    ORDER BY COALESCE(r.data->>'name', ''), r.row_id
--    ... AND (COALESCE(r.data->>'name', ''), r.row_id) > ($n, $m)
--  بی این نمایه، هر صفحه همهٔ کالاهای دکان را می‌خواند و مرتب می‌کرد؛ با آن،
--  planner همان چند ده ردیفِ صفحه را به ترتیب از نمایه برمی‌دارد.
--  سنجه (`bench/server.mjs`، شرح در docs/spec/bench): صفحهٔ اولِ کاتالوگ.
--
--  جزئی است: فقط کالاهای زندهٔ دکان‌ها (`table_name = 'products'`)، پس نوشتنِ
--  فاکتور و قلم و تراکنش هیچ هزینهٔ تازه‌ای ندارد. عبارتِ نمایه دقیقاً همان
--  عبارتِ ORDER BY ِ کاتالوگ است — اگر آن عوض شود، این نمایه بی‌مصرف می‌شود.
--  هیچ ستون، جدول یا داده‌ای عوض نمی‌شود. برگشت: DROP INDEX همین نام.
-- ============================================================
CREATE INDEX IF NOT EXISTS ix_sync_rows_shop_product_name
  ON sync_rows (account_id, (COALESCE(data->>'name', '')), row_id)
  WHERE app = 'shop' AND account_kind = 'shop' AND table_name = 'products' AND deleted_at IS NULL;
