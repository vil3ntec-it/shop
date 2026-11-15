-- ============================================================
--  Rollback ِ 945_storefront_area_email.sql — فقط با دست، پس از برگرداندنِ کد.
--  درخواست‌هایی که نامهٔ «رسیدیم» گرفتند (`notice = 'sent'`) پاک می‌شوند — همان «خبر دادم» ِ دستی؛ بی آن، کدِ پیشین
--  دوباره نشانشان می‌داد. بقیهٔ درخواست‌ها با همان خانه و زمان می‌مانند.
-- ============================================================
DELETE FROM storefront_area_requests WHERE notice = 'sent';
DROP INDEX IF EXISTS ix_storefront_area_requests_sent;
DROP INDEX IF EXISTS ix_storefront_area_requests_waiting;
ALTER TABLE storefront_area_requests DROP COLUMN IF EXISTS sent_at;
ALTER TABLE storefront_area_requests DROP COLUMN IF EXISTS tried;
ALTER TABLE storefront_area_requests DROP COLUMN IF EXISTS notice;
ALTER TABLE storefront_area_requests DROP COLUMN IF EXISTS lang;
DELETE FROM schema_migrations WHERE version = '945_storefront_area_email.sql';
