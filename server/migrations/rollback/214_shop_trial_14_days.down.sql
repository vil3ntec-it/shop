-- ============================================================
--  Rollback ِ 214_shop_trial_14_days.sql — فقط با دست.
--
--  دورهٔ آزمایشیِ دکان به ۷ روزِ پیشین برمی‌گردد، فقط اگر هنوز همان ۱۴ باشد؛ عددِ دیگرِ پنل دست نمی‌خورد.
--  پیش از اجرا کدِ پیش از این نسخه را برگردانید (`plans.TRIAL_DEFAULT.shop` = '7')، وگرنه نصبِ بی‌ردیف ۱۴ می‌ماند.
--
--  اجرا (در یک تراکنش):
--    psql "$DATABASE_URL" -1 -f migrations/rollback/214_shop_trial_14_days.down.sql
-- ============================================================

UPDATE app_config
   SET value = '7', updated_at = (EXTRACT(EPOCH FROM now()) * 1000)::bigint
 WHERE key = 'trial_days' AND btrim(value) = '14';
DELETE FROM schema_migrations WHERE version = '214_shop_trial_14_days.sql';
