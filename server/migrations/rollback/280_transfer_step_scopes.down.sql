-- ============================================================
--  Rollback ِ 280_transfer_step_scopes.sql — فقط با دست.
--
--  گام‌های دستگاهی که هنوز خوانده نشده‌اند دیگر با محدودهٔ عضوِ فرستنده سنجیده نمی‌شوند (همان رفتارِ پیش از ۲۸۰:
--  فقط نقش با `doc-roles`). گام‌هایی که پیش‌تر نپذیرفته ثبت شده‌اند همان‌طور می‌مانند. پیش از اجرا کدِ پیشین را برگردانید.
--
--  اجرا (در یک تراکنش):
--    psql "$DATABASE_URL" -1 -f migrations/rollback/280_transfer_step_scopes.down.sql
-- ============================================================

DROP TABLE IF EXISTS stock_transfer_step_scopes;

DELETE FROM schema_migrations WHERE version = '280_transfer_step_scopes.sql';
