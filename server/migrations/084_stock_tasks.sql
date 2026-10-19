-- ============================================================
--  «وظیفهٔ بررسیِ موجودی» — v3 §۳.۶، INV-19، §۳۲.۲ بندِ ۷، §۳۸.۳
--
--  فروشِ ثبت‌شدهٔ دستگاه (آفلاین) هرگز رد نمی‌شود؛ اگر موجودی منفی شد یا
--  رزروِ سفارشی بی‌پشتوانه ماند، این‌جا یک وظیفه برای مدیر باز می‌شود —
--  تعارض بی‌صدا حل نمی‌شود. برای هر کلید (`dedupe`) فقط یک وظیفهٔ باز.
--  ⛔ تا پرچمِ `shop.catalog` روشن نشود هیچ ردیفی نوشته نمی‌شود.
-- ============================================================

CREATE TABLE IF NOT EXISTS stock_tasks (
  id            text    PRIMARY KEY,                       -- ULID
  shop_id       text    NOT NULL REFERENCES shops(id) ON DELETE CASCADE,
  --  negative_stock: موجودیِ مکان منفی شد · reservation_short: رزروِ فعال بیش از موجودیِ قابل‌فروش
  --  transfer_needed: رزرو از انبار برداشت؛ به قفسه بیاید · low_stock: به آستانه رسید
  kind          text    NOT NULL CHECK (kind IN ('negative_stock','reservation_short','transfer_needed','low_stock')),
  product_id    text    NOT NULL,
  branch_id     text    NOT NULL DEFAULT '',
  location      text    NOT NULL DEFAULT '',
  dedupe        text    NOT NULL,
  status        text    NOT NULL DEFAULT 'open' CHECK (status IN ('open','resolved','dismissed')),
  detail        jsonb   NOT NULL DEFAULT '{}'::jsonb,
  hits          integer NOT NULL DEFAULT 1,
  created_at    bigint  NOT NULL,
  updated_at    bigint  NOT NULL,
  resolved_by   text    NOT NULL DEFAULT '',
  resolved_at   bigint,
  note          text    NOT NULL DEFAULT ''
);
CREATE UNIQUE INDEX IF NOT EXISTS ux_stock_tasks_open ON stock_tasks(shop_id, dedupe) WHERE status = 'open';
CREATE INDEX IF NOT EXISTS ix_stock_tasks_shop ON stock_tasks(shop_id, status, created_at, id);
