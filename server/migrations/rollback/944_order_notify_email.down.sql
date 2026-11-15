-- ============================================================
--  Rollback ِ 944_order_notify_email.sql — فقط با دست، پس از برگرداندنِ کد.
--  دو حالِ تازه به همان `not_configured` ِ پیشین برمی‌گردند (نامه‌ای که نرفت همان «تنظیم نشده» بود)؛ دادهٔ دیگری دست نمی‌خورد.
-- ============================================================
UPDATE order_notify_outbox SET fallback_state = 'not_configured' WHERE fallback_state IN ('throttled', 'no_email');
DROP INDEX IF EXISTS ix_order_notify_outbox_fallback_email;
ALTER TABLE order_notify_outbox DROP CONSTRAINT IF EXISTS order_notify_outbox_fallback_state_check;
ALTER TABLE order_notify_outbox ADD CONSTRAINT order_notify_outbox_fallback_state_check
  CHECK (fallback_state IN ('', 'not_configured', 'provider_accepted', 'provider_error'));
DELETE FROM schema_migrations WHERE version = '944_order_notify_email.sql';
