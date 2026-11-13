-- ============================================================
--  Rollback ِ 880_phone_not_identity.sql — فقط با دست.
--
--  یکتاییِ `users.phone` برمی‌گردد. ⚠️ اگر پس از ۸۸۰ دو حساب یک شماره گرفته باشند، UNIQUE ساخته نمی‌شود؛
--  پیش از اجرا تکراری‌ها را ببینید: SELECT phone, count(*) FROM users WHERE phone IS NOT NULL GROUP BY 1 HAVING count(*) > 1;
--
--  اجرا (در یک تراکنش):
--    psql "$DATABASE_URL" -1 -f migrations/rollback/880_phone_not_identity.down.sql
-- ============================================================

DROP INDEX IF EXISTS ix_users_phone;
ALTER TABLE users ADD CONSTRAINT users_phone_key UNIQUE (phone);

DELETE FROM schema_migrations WHERE version = '880_phone_not_identity.sql';
