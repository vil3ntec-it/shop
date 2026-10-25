-- ORD-05: مهلتِ قبولِ سفارشِ ویترین (زمان‌سنجِ قبول، lib/order-timeout.js).
-- فقط وقتی پر می‌شود که دکان همان لحظهٔ ثبت سفارش می‌پذیرفت (orderAcceptance = accepting)؛
-- NULL = بی زمان‌سنج (سفارشِ «بعداً»، سفارشِ دستیِ کارمند، همهٔ ردیف‌های پیشین).
-- فقط افزودنی — هیچ داده‌ای عوض نمی‌شود. برگشت: DROP INDEX و DROP COLUMN.
ALTER TABLE online_orders ADD COLUMN IF NOT EXISTS accept_deadline_at bigint;
CREATE INDEX IF NOT EXISTS ix_online_orders_accept_deadline
  ON online_orders(accept_deadline_at) WHERE status = 'new' AND accept_deadline_at IS NOT NULL;
