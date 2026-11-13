-- ============================================================
--  Rollback ِ 881_store_email_identity.sql — فقط با دست.
--
--  ⚠️ ردیف‌هایی که پس از ۸۸۱ با کلیدِ ایمیل ساخته شده‌اند (حسابِ بی‌شماره، «خبرم کن»، پوش، درخواستِ محدوده)
--  با قیدِ قدیم سازگار نیستند و پیش از برگرداندنِ قید پاک می‌شوند؛ سفارش‌ها می‌مانند (ستون‌هایشان می‌روند).
--
--  اجرا (در یک تراکنش):
--    psql "$DATABASE_URL" -1 -f migrations/rollback/881_store_email_identity.down.sql
-- ============================================================

DELETE FROM storefront_area_requests WHERE phone LIKE '%@%';
ALTER TABLE storefront_area_requests DROP CONSTRAINT IF EXISTS storefront_area_requests_phone_check;
ALTER TABLE storefront_area_requests ADD CONSTRAINT storefront_area_requests_phone_check CHECK (phone ~ '^\+[1-9][0-9]{7,14}$');
DELETE FROM store_alert_push WHERE phone LIKE '%@%';
ALTER TABLE store_alert_push DROP CONSTRAINT IF EXISTS store_alert_push_phone_check;
ALTER TABLE store_alert_push ADD CONSTRAINT store_alert_push_phone_check CHECK (phone ~ '^\+[1-9][0-9]{7,14}$');
DELETE FROM storefront_alerts WHERE phone LIKE '%@%';
ALTER TABLE storefront_alerts DROP CONSTRAINT IF EXISTS storefront_alerts_phone_check;
ALTER TABLE storefront_alerts ADD CONSTRAINT storefront_alerts_phone_check CHECK (phone ~ '^\+[1-9][0-9]{7,14}$');

DROP INDEX IF EXISTS ix_online_orders_store_customer;
DROP INDEX IF EXISTS ix_online_orders_storefront_email;
ALTER TABLE online_orders DROP COLUMN IF EXISTS store_customer_id;
ALTER TABLE online_orders DROP COLUMN IF EXISTS customer_email;

DELETE FROM store_customers WHERE phone IS NULL;
ALTER TABLE store_customers DROP CONSTRAINT IF EXISTS store_customers_identity;
DROP INDEX IF EXISTS ix_store_customers_phone;
ALTER TABLE store_customers ALTER COLUMN phone SET NOT NULL;
ALTER TABLE store_customers ADD CONSTRAINT store_customers_phone_key UNIQUE (phone);
DROP INDEX IF EXISTS ux_store_customers_email;
ALTER TABLE store_customers DROP CONSTRAINT IF EXISTS store_customers_email_shape;
ALTER TABLE store_customers DROP COLUMN IF EXISTS email;

DELETE FROM schema_migrations WHERE version = '881_store_email_identity.sql';
