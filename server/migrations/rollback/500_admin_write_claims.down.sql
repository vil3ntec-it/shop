-- ============================================================
--  Rollback ِ 500_admin_write_claims.sql — فقط با دست.
--
--  پیش از اجرا کدِ پیشین را برگردانید: کدِ تازه بی این جدول هر نوشتنِ مدیرِ **کلیددار** (دستگاه، بانکِ عکس، پشتیبان)
--  را با خطای ۵xx رد می‌کند؛ بی‌کلیدها همان رفتارِ پیشین‌اند. هیچ داده‌ای از دست نمی‌رود (نشان‌ها فقط تا پایانِ همان
--  درخواست زنده‌اند؛ پاسخ‌های ثبت‌شده در `idempotency_keys` می‌مانند).
--
--  اجرا (در یک تراکنش):
--    psql "$DATABASE_URL" -1 -f migrations/rollback/500_admin_write_claims.down.sql
-- ============================================================

DROP TABLE IF EXISTS admin_write_claims;

DELETE FROM schema_migrations WHERE version = '500_admin_write_claims.sql';
