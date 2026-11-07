-- ============================================================
--  Rollback ِ 265_stock_transfer_steps.sql — فقط با دست.
--
--  گام‌ها و نتیجهٔ گام‌های دستگاه فراموش می‌شوند؛ انتقال دوباره فقط دومرحله‌ای (یک دریافت) است.
--  ⛔ انتقالِ «نیمه‌رسیده» (`partial`) را کدِ پیشین نمی‌فهمد و با دریافتِ دوباره همهٔ فرستاده را جابه‌جا می‌کرد
--     (دو بار شمرده می‌شد) ⇒ اگر هست، این پرونده پیش از هر تغییری می‌ایستد؛ اول آن انتقال‌ها را ببندید.
--  وظیفه‌های `transfer_refused` پاک می‌شوند (بی آن CHECK ِ پیشین نمی‌نشیند). پیش از اجرا کدِ پیش از این نسخه را برگردانید.
--
--  اجرا (در یک تراکنش):
--    psql "$DATABASE_URL" -1 -f migrations/rollback/265_stock_transfer_steps.down.sql
-- ============================================================

DO $$
BEGIN
  IF EXISTS (SELECT 1 FROM stock_transfers WHERE status = 'partial') THEN
    RAISE EXCEPTION 'rollback 265: انتقالِ نیمه‌رسیده (partial) هست؛ اول ببندیدش';
  END IF;
END $$;

DROP TABLE IF EXISTS stock_transfer_steps;

ALTER TABLE stock_transfers DROP CONSTRAINT IF EXISTS stock_transfers_status_check;
ALTER TABLE stock_transfers ADD CONSTRAINT stock_transfers_status_check
  CHECK (status IN ('in_transit','received','rejected'));

DELETE FROM stock_tasks WHERE kind = 'transfer_refused';
ALTER TABLE stock_tasks DROP CONSTRAINT IF EXISTS stock_tasks_kind_check;
ALTER TABLE stock_tasks ADD CONSTRAINT stock_tasks_kind_check
  CHECK (kind IN ('negative_stock','reservation_short','transfer_needed','low_stock','transfer_short'));

DELETE FROM schema_migrations WHERE version = '265_stock_transfer_steps.sql';
