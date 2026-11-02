-- ============================================================
--  Rollback ِ 194_image_quality.sql — فقط با دست، پس از برگرداندنِ کد.
--
--  ⚠️ فقط هشِ «مشابهِ احتمالی» و فهرستِ مشابه‌ها پاک می‌شود؛ عکس‌ها، نسخه‌ها و نشان‌های
--  `quality_flags` دست نمی‌خورند.
--
--  اجرا (در یک تراکنش):
--    psql "$DATABASE_URL" -1 -f migrations/rollback/194_image_quality.down.sql
-- ============================================================

ALTER TABLE images DROP COLUMN IF EXISTS near_duplicate_of;
ALTER TABLE image_blobs DROP COLUMN IF EXISTS dhash;
DELETE FROM schema_migrations WHERE version = '194_image_quality.sql';
