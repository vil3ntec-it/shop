-- ============================================================
--  Rollback ِ 153_store_campaigns.sql — فقط با دست، پس از برگرداندنِ کد.
--
--  ⚠️ پیوندهای کمپین و شمارِ بازدید/سفارشِ هر پیوند پاک می‌شوند؛ خودِ سفارش‌ها
--  (online_orders) دست نمی‌خورند. QR ِ چاپ‌شده پس از اجرای دوبارهٔ ۱۵۳ دیگر کار
--  نمی‌کند (کدِ تازه ساخته می‌شود). پیش از اجرا اگر لازم است پشتیبان بگیرید:
--    COPY (SELECT * FROM store_campaigns) TO …;
--
--  اجرا (در یک تراکنش):
--    psql "$DATABASE_URL" -1 -f migrations/rollback/153_store_campaigns.down.sql
-- ============================================================

DROP TABLE IF EXISTS store_campaign_orders;
DROP TABLE IF EXISTS store_campaign_visitors;
DROP TABLE IF EXISTS store_campaign_days;
DROP TABLE IF EXISTS store_campaigns;
DELETE FROM schema_migrations WHERE version = '153_store_campaigns.sql';
