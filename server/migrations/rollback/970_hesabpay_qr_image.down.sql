-- ============================================================
--  Rollback ِ 970_hesabpay_qr_image.sql — فقط با دست، پس از برگرداندنِ کد.
--  متنِ QR (`qr_payload`) و حساب دست نمی‌خورند؛ فقط عکس‌ها و ستونِ هشِ عکسِ فعال می‌روند. سابقه‌ها (`audit_logs`) می‌مانند.
-- ============================================================
DROP TABLE IF EXISTS hesabpay_qr_images;
ALTER TABLE shop_hesabpay DROP COLUMN IF EXISTS qr_image;
DELETE FROM schema_migrations WHERE version = '970_hesabpay_qr_image.sql';
