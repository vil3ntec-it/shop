-- ============================================================
--  Rollback ِ 333_subscription_clock_evidence.sql — فقط با دست.
--
--  بستنِ خودکار دوباره برای همیشه می‌شود (همان رفتارِ پیش از ۳۳۳). ردیفی که با شاهدِ ساعت بسته و هنوز برنگشته،
--  `expired` می‌ماند. پیش از اجرا کدِ پیشین را برگردانید.
--
--  اجرا (در یک تراکنش):
--    psql "$DATABASE_URL" -1 -f migrations/rollback/333_subscription_clock_evidence.down.sql
-- ============================================================

DROP INDEX IF EXISTS ix_subs_active_grace_end;
DROP INDEX IF EXISTS ix_subs_auto_expired;
ALTER TABLE subscriptions DROP COLUMN IF EXISTS auto_expired_at;

DELETE FROM schema_migrations WHERE version = '333_subscription_clock_evidence.sql';
