-- ============================================================
--  ۲۶۵ — گام‌های انتقالِ موجودی: دریافتِ چندباره (ناقص) و ثبت از دستگاهِ بی‌اینترنت — INV-11، S21 (v3 §۶.۵)
--
--  `stock_transfer_steps` ── هر گام (ارسال، دریافت، رد) یک ردیف با کلیدِ یکتایی‌اش، هم از REST
--  (`Idempotency-Key`) هم از دفترِ دستگاه (ردیفِ `auditLog` با `type = stock_transfer`؛ کلید = شناسهٔ همان ردیف).
--  تکرارِ همان گام ⇒ همان نتیجه بی اثرِ دوم؛ همان کلید با بدنهٔ دیگر ⇒ ۴۰۹. گامِ ردشدهٔ دستگاه (کمبود، کالای
--  ناشناس، انتقالِ بسته) با کد و پیامش این‌جا می‌ماند تا برنامه «رد شد» را راست نشان دهد — هرگز بی‌صدا گم نمی‌شود.
--  `stock_transfers.status` ── `partial`: بخشی رسیده و بقیه هنوز «در مسیر» است (گذار: in_transit ⇒ partial ⇒ …
--  ⇒ received | rejected). `stock_tasks.kind` ── `transfer_refused`: گامِ دستگاه که سرور نپذیرفت (برای مدیر).
--
--  فقط افزودنی؛ تا پرچمِ `shop.catalog` روشن نشود هیچ ردیفی نوشته نمی‌شود (lib/stock-transfers.js).
--  rollback: migrations/rollback/265_stock_transfer_steps.down.sql (آزموده در test/catalog-transfer-steps.test.js)
-- ============================================================

CREATE TABLE IF NOT EXISTS stock_transfer_steps (
  shop_id      text    NOT NULL REFERENCES shops(id) ON DELETE CASCADE,
  step_key     text    NOT NULL,
  --  شناسهٔ انتقال؛ ارسالِ ردشده انتقالی نساخته ⇒ ''
  transfer_id  text    NOT NULL DEFAULT '',
  --  `other`: گامِ ناشناختهٔ دستگاه (نسخهٔ تازه‌تر) — رد و ثبت می‌شود، نه بی‌صدا کنار
  kind         text    NOT NULL CHECK (kind IN ('send','receive','reject','other')),
  body_hash    text    NOT NULL,
  outcome      text    NOT NULL CHECK (outcome IN ('applied','refused')),
  --  گامِ ردشده: کد و پیامِ همان خطا (`insufficient_stock`، `product_not_found`، `transfer_closed`، …)
  code         text    NOT NULL DEFAULT '',
  message      text    NOT NULL DEFAULT '',
  --  یادداشت یا دلیلِ خودِ گام (کسری، اختلاف، رد)
  note         text    NOT NULL DEFAULT '',
  --  [{ productId, qtyMilli }] ِ همین گام (ارسال: فرستاده؛ دریافت: رسیده در همین گام)
  lines        jsonb   NOT NULL DEFAULT '[]'::jsonb,
  final        boolean NOT NULL DEFAULT true,
  origin       text    NOT NULL CHECK (origin IN ('api','device')),
  actor        text    NOT NULL DEFAULT '',
  created_at   bigint  NOT NULL,
  PRIMARY KEY (shop_id, step_key)
);
CREATE INDEX IF NOT EXISTS ix_stock_transfer_steps_xfer ON stock_transfer_steps(shop_id, transfer_id, created_at);

ALTER TABLE stock_transfers DROP CONSTRAINT IF EXISTS stock_transfers_status_check;
ALTER TABLE stock_transfers ADD CONSTRAINT stock_transfers_status_check
  CHECK (status IN ('in_transit','partial','received','rejected'));

--  بازه فقط بازتر می‌شود (همان شیوهٔ ۱۳۷)
ALTER TABLE stock_tasks DROP CONSTRAINT IF EXISTS stock_tasks_kind_check;
ALTER TABLE stock_tasks ADD CONSTRAINT stock_tasks_kind_check
  CHECK (kind IN ('negative_stock','reservation_short','transfer_needed','low_stock','transfer_short','transfer_refused'));
