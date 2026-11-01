-- ============================================================
--  انتقالِ دومرحله‌ایِ موجودی: «در مسیر» و قبول یا رد در مقصد — INV-11، S21 (v3 §۶.۵)
--
--  هر ردیف یک سندِ انتقال است؛ خودِ موجودی همچنان فقط در `stock_moves` (فقط‌افزودنی) و
--  `stock_levels` است. این جدول فقط وضعیتِ سند و مقدارِ فرستاده/رسیدهٔ هر کالا را نگه می‌دارد
--  (`lib/stock-transfers.js`). گذار فقط in_transit ⇒ received | rejected.
--  ⛔ تا پرچمِ `shop.catalog` روشن نشود هیچ ردیفی نوشته نمی‌شود (مسیرها پشتِ همان پرچم‌اند).
--  شعبه هنوز مدل ندارد (§۳۸.۳)؛ مبدأ و مقصد مکان‌های همین دکان‌اند.
--  برگشت: DROP TABLE stock_transfers، و CHECK ِ ۰۸۴ برای stock_tasks.kind (پس از برداشتنِ ردیف‌های transfer_short).
-- ============================================================

CREATE TABLE IF NOT EXISTS stock_transfers (
  id              text    PRIMARY KEY,
  shop_id         text    NOT NULL REFERENCES shops(id) ON DELETE CASCADE,
  from_location   text    NOT NULL CHECK (from_location IN ('shop_floor','warehouse')),
  to_location     text    NOT NULL CHECK (to_location IN ('shop_floor','warehouse')),
  status          text    NOT NULL DEFAULT 'in_transit' CHECK (status IN ('in_transit','received','rejected')),
  --  [{ productId, sentMilli, receivedMilli|null }] — حداکثر ۱۰۰ کالا (سنجش در کد)
  lines           jsonb   NOT NULL,
  note            text    NOT NULL DEFAULT '',
  --  دلیلِ رد یا کسری
  reason          text    NOT NULL DEFAULT '',
  --  کلیدِ یکتاییِ ارسال و بستن، و اثرِ انگشتِ بدنه (همان کلید با بدنهٔ دیگر ⇒ ۴۰۹)
  send_key        text    NOT NULL,
  send_hash       text    NOT NULL,
  close_key       text    NOT NULL DEFAULT '',
  close_hash      text    NOT NULL DEFAULT '',
  created_by      text    NOT NULL,
  created_at      bigint  NOT NULL,
  closed_by       text    NOT NULL DEFAULT '',
  closed_at       bigint,
  UNIQUE (shop_id, send_key),
  CHECK (from_location <> to_location)
);
CREATE INDEX IF NOT EXISTS ix_stock_transfers_shop ON stock_transfers(shop_id, status, created_at, id);

--  کسریِ دریافت هرگز بی‌صدا گم نمی‌شود: وظیفهٔ بررسیِ تازه. بازه فقط بازتر می‌شود.
ALTER TABLE stock_tasks DROP CONSTRAINT IF EXISTS stock_tasks_kind_check;
ALTER TABLE stock_tasks ADD CONSTRAINT stock_tasks_kind_check
  CHECK (kind IN ('negative_stock','reservation_short','transfer_needed','low_stock','transfer_short'));
