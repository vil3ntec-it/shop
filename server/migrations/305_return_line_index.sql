-- ============================================================
--  ۳۰۵ — نمایهٔ «برگشت‌های هر قلمِ فاکتور» (سقفِ برگشتِ قلم، سفرِ JB5؛ `src/lib/return-line-cap.js`)
--
--  ⛔ هیچ‌کس — صاحب دکان هم — بیش از آنچه روی همان قلم فروخته شد برنمی‌گرداند. سرور برگشته‌ی هر قلم (مقدار و پول) را
--  از خودِ دفتر می‌خواند — ردیف‌های زندهٔ `returns` با همان `saleItemId` از هر سه دفتر (v3، v1، راهِ قدیم/REST) — درونِ
--  همان تراکنش و زیرِ همان قفلِ برگشت که برگشت می‌نشیند. دفترِ شمارندهٔ دوم ساخته نشد: هر نسخهٔ دوم از دفتر جدا
--  می‌افتد (پلِ v1⇄v3، حذف و لغوِ برگشت، بازیابی، نمونهٔ کهنهٔ سرور هنگامِ استقرار). این نمایه‌ها همان خواندن را از
--  پیمایشِ همهٔ برگشت‌های دکان به جست‌وجوی یک قلم می‌برند.
--
--  ⛔ CONCURRENTLY نه (همان دلیلِ ۱۹۸): هر مهاجرت با ردیفِ `schema_migrations` اش یک تراکنش است. فقط افزودنی: نه
--  جدولی، نه ستونی. نمایه‌های Sync جزئی‌اند (`app='shop'`، فقط `returns`) ⇒ ردیف‌های پمپ و Sync ِ آن دست نمی‌خورند.
--  برگشت: migrations/rollback/305_return_line_index.down.sql (آزموده در test/return-line-cap.test.js)
-- ============================================================

CREATE INDEX IF NOT EXISTS ix_sync_v3_rows_return_item
  ON sync_v3_rows (account_id, (data->>'saleItemId'))
  WHERE app = 'shop' AND table_name = 'returns';

CREATE INDEX IF NOT EXISTS ix_sync_rows_return_item
  ON sync_rows (account_id, (data->>'saleItemId'))
  WHERE app = 'shop' AND table_name = 'returns';

CREATE INDEX IF NOT EXISTS ix_sale_returns_item
  ON sale_returns (shop_id, (data->>'saleItemId'));
