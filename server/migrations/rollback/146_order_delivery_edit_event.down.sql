-- برگشتِ مهاجرتِ ۱۴۶ (CHK-14، CHK-24). دستی و فقط پس از برگرداندنِ کدِ پیشین اجرا شود:
--   psql "$DATABASE_URL" -f migrations/rollback/146_order_delivery_edit_event.down.sql
-- این پوشه را `src/migrate.js` نمی‌خواند (فقط *.sql ِ خودِ migrations).
-- ⚠️ رویدادهای `delivery_edit` ِ ثبت‌شده باید پیش‌تر به `note` برگردند وگرنه CHECK ِ پیشین
-- نمی‌نشیند؛ همین فایل آن را می‌کند (ریزِ «پیش/پس» در `detail` می‌ماند).
BEGIN;
UPDATE online_order_events SET kind = 'note' WHERE kind = 'delivery_edit';
ALTER TABLE online_order_events DROP CONSTRAINT IF EXISTS online_order_events_kind_check;
ALTER TABLE online_order_events ADD CONSTRAINT online_order_events_kind_check
  CHECK (kind IN ('created','status','payment','cod','note'));
DELETE FROM schema_migrations WHERE version = '146_order_delivery_edit_event.sql';
COMMIT;
