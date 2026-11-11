-- ============================================================
--  Rollback ِ 810_admin_access_grants.sql — فقط با دست.
--
--  اجازه‌های دسترسیِ پشتیبانی (باز، گذشته و لغوشده) پاک می‌شوند؛ سابقهٔ هر کار (`audit_logs`، `admin.support_access_*`
--  و `shop.support_access_*`) می‌ماند. پیش از اجرا کدِ پیشین را برگردانید: درهای `…/access-grants` و توانایی
--  `support-access.v1` همین جدول را می‌خوانند.
--
--  اجرا (در یک تراکنش):
--    psql "$DATABASE_URL" -1 -f migrations/rollback/810_admin_access_grants.down.sql
-- ============================================================

DROP INDEX IF EXISTS ix_admin_access_grants_live;
DROP INDEX IF EXISTS ix_admin_access_grants_tenant;
DROP TABLE IF EXISTS admin_access_grants;

DELETE FROM schema_migrations WHERE version = '810_admin_access_grants.sql';
