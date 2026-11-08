-- ============================================================
--  ۳۵۵ — لحظهٔ شمارشِ هر کالا و حرکت‌های دستگاه برای «فروشِ پیش از شمارش که دیر می‌رسد» (INV-10، §۶.۵)
--
--  دستگاهِ A آفلاین ۳ فروخت (۱۰:۰۰)، دستگاهِ B ساعتِ ۱۰:۳۰ ۱۷ شمرد (−۳)؛ فروشِ A ساعتِ ۱۱:۰۰ می‌رسد و جمعِ دفتر ۱۴ می‌شد.
--  ردیفِ شمارشِ دستگاه (`warehouseEntries` با `countedAt`/`seenMilli`) و حرکت‌های دستگاه (`stockMovements`) از دفترِ
--  همگام‌سازی (v1 یا v3، فقط خواندن) این‌جا نگه داشته می‌شوند تا `lib/count-absorb-ledger.js` همان قاعدهٔ
--  `license/count-absorb-core.js` ِ برنامه را با نمایه حساب کند و تفاوت را با حرکتِ افزودنیِ `adjustment` ِ
--  «پیش از شمارش ثبت شده بود — در شمارش آمده است» (`reason = count_absorb`) در `stock_moves` بنشاند.
--
--  ⛔ پرچمِ `shop.count_absorb` (live_config، پیش‌فرض خاموش): خاموش ⇒ هیچ حرکتی؛ این دو جدول فقط نگه داشته می‌شوند.
--  ⛔ `stock_moves` فقط افزودنی می‌ماند؛ `stock_levels` = جمعِ حرکت‌ها (`stock-rebuild` همان).
--  فقط افزودنی: دو جدولِ تازه و یک ستونِ پیش‌فرض‌دار.
--  برگشت: migrations/rollback/355_stock_count_absorb.down.sql
-- ============================================================

CREATE TABLE IF NOT EXISTS stock_count_marks (
  shop_id       text    NOT NULL REFERENCES shops(id) ON DELETE CASCADE,
  row_id        text    NOT NULL,          -- شناسهٔ ردیفِ انبارِ شمارش (همان دفترِ دستگاه)
  product_id    text    NOT NULL,
  counted_at    bigint  NOT NULL,          -- لحظهٔ شمردن (ساعتِ دستگاه)؛ ردیفِ بی لحظه = created_at
  seen_milli    bigint,                    -- دانستهٔ شمارنده؛ NULL ⇒ ردیفِ بی لحظه، جذب نمی‌کند
  created_at    bigint  NOT NULL,          -- ساختِ ردیف روی دستگاه (`createdAt`)
  corrected_at  bigint,                    -- «تاریخِ تصحیح‌شده» ِ سرورِ v3
  server_at     bigint  NOT NULL,          -- ساختِ همان ردیف روی سرور (ردیفِ دفترِ همگام‌سازی)
  updated_at    bigint  NOT NULL,
  PRIMARY KEY (shop_id, row_id)
);
CREATE INDEX IF NOT EXISTS ix_stock_count_marks_product ON stock_count_marks (shop_id, product_id);

CREATE TABLE IF NOT EXISTS stock_device_events (
  shop_id       text    NOT NULL REFERENCES shops(id) ON DELETE CASCADE,
  row_id        text    NOT NULL,          -- شناسهٔ ردیفِ `stockMovements` ِ دستگاه
  product_id    text    NOT NULL,
  type          text    NOT NULL DEFAULT '',
  qty_milli     bigint  NOT NULL,
  at            bigint  NOT NULL,          -- `createdAt` ِ دستگاهِ سازنده
  ref_id        text,
  corrected_at  bigint,
  server_at     bigint  NOT NULL,
  updated_at    bigint  NOT NULL,
  PRIMARY KEY (shop_id, row_id)
);
CREATE INDEX IF NOT EXISTS ix_stock_device_events_product ON stock_device_events (shop_id, product_id, at);

ALTER TABLE catalog_sync_state ADD COLUMN IF NOT EXISTS count_absorb boolean NOT NULL DEFAULT false;
