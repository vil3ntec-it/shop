-- برگشتِ مهاجرتِ ۱۲۲ (M4). دستی و فقط پس از برگرداندنِ کدِ پیشین اجرا شود:
--   psql "$DATABASE_URL" -f migrations/rollback/122_market_product_details.down.sql
-- این پوشه را `src/migrate.js` نمی‌خواند (فقط *.sql ِ خودِ migrations).
-- `market_search` نمایه است (نسخه‌ای از دادهٔ دیگر جدول‌ها) ⇒ بی دست‌رفتنِ داده؛
-- فقط نگاشتِ دستهٔ دکان‌ها (`category_map`) پاک می‌شود.
BEGIN;
DROP INDEX IF EXISTS ix_market_search_barcodes;
DROP INDEX IF EXISTS ix_market_search_brand;
DROP INDEX IF EXISTS ix_market_search_bank;
ALTER TABLE market_search DROP COLUMN IF EXISTS brand_norm;
ALTER TABLE market_search DROP COLUMN IF EXISTS barcodes;
ALTER TABLE market_search DROP COLUMN IF EXISTS bank_product_id;
ALTER TABLE market_listings DROP COLUMN IF EXISTS category_map;
--  امضاها پاک ⇒ نمایه با کدِ پیشین از نو ساخته می‌شود
DELETE FROM market_search_shops;
DELETE FROM schema_migrations WHERE version = '122_market_product_details.sql';
COMMIT;
