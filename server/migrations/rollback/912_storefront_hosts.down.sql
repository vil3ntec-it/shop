-- ============================================================
--  Rollback ِ 912_storefront_hosts.sql — فقط با دست، پس از برگرداندنِ کد.
--  نشانی‌های دیگرِ ویترین گم می‌شوند (ویترین با همان `<slug>.<domain>` و `/store/<slug>` می‌ماند)؛ دادهٔ دیگری دست نمی‌خورد.
-- ============================================================
DROP INDEX IF EXISTS ix_storefront_hosts_shop;
DROP TABLE IF EXISTS storefront_hosts;
DELETE FROM schema_migrations WHERE version = '912_storefront_hosts.sql';
