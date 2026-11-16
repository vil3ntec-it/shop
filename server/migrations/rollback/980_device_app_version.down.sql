-- ============================================================
--  Rollback ِ 980_device_app_version.sql — فقط با دست، پس از برگرداندنِ کد (`middleware/auth.js` بی این ستون).
--  فقط همان ستون می‌رود؛ دستگاه‌ها، نشست‌ها و `last_seen_at` دست نمی‌خورند. فهرستِ دستگاه‌ها دوباره بی نسخه است.
-- ============================================================
ALTER TABLE devices DROP COLUMN IF EXISTS app_version;
DELETE FROM schema_migrations WHERE version = '980_device_app_version.sql';
