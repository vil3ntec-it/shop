-- SEC-API-09 (docs/security/data-fa.md): شمارهٔ رسیدِ پرداختِ اشتراک یکتا و اتمی.
--
-- تا این‌جا `POST /api/admin/payments` شماره را «سال-(COUNT(*)+1)» می‌ساخت: دو ثبتِ هم‌زمان یک شماره
-- می‌گرفتند و شمارهٔ دستی هم هیچ قیدی نداشت. حالا:
--   • شمارنده یک ردیف است که در همان تراکنشِ ثبت بالا می‌رود (قفلِ ردیف ⇒ پشتِ هم، بی شکاف با ROLLBACK)،
--     از همان عددِ امروز (شمارِ همهٔ پرداخت‌ها، حذف‌شده هم) ادامه می‌دهد؛
--   • هر ردیفِ تازه `receipt_unique = true` می‌گیرد و شماره‌اش یکتاست (حذف‌شده هم، تا شمارهٔ رسیدِ باطل
--     دوباره داده نشود). ردیف‌های پیشین دست نمی‌خورند: شمارهٔ تکراریِ کهنه (اگر هست) نه پاک می‌شود نه عوض —
--     نگهبانِ سرور فقط شمارهٔ تازه را با همه می‌سنجد.
CREATE TABLE IF NOT EXISTS sub_payment_counters (
  scope  text   PRIMARY KEY,
  last   bigint NOT NULL CHECK (last >= 0)
);
INSERT INTO sub_payment_counters (scope, last)
  SELECT 'receipt', COUNT(*) FROM sub_payments
  ON CONFLICT (scope) DO NOTHING;

ALTER TABLE sub_payments ADD COLUMN IF NOT EXISTS receipt_unique boolean NOT NULL DEFAULT false;
CREATE UNIQUE INDEX IF NOT EXISTS ux_sub_payments_receipt_no
  ON sub_payments(receipt_no) WHERE receipt_unique AND receipt_no <> '';
