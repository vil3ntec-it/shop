-- ============================================================
--  Rollback ِ 206_market_regions.sql — فقط با دست.
--
--  ولایت و ولسوالیِ دکان‌ها در «نمایش در بازارچه» و نشان‌های «آزمایشی/تکراری» ِ مدیر پاک می‌شوند. پیش از اجرا
--  کدِ پیش از این نسخه را برگردانید (کدِ همین نسخه این ستون‌ها و جدول را می‌خواند).
--
--  اجرا (در یک تراکنش):
--    psql "$DATABASE_URL" -1 -f migrations/rollback/206_market_regions.down.sql
-- ============================================================

DROP TABLE IF EXISTS shop_census_marks;
ALTER TABLE market_listings DROP COLUMN IF EXISTS district;
ALTER TABLE market_listings DROP COLUMN IF EXISTS province;
DELETE FROM schema_migrations WHERE version = '206_market_regions.sql';
