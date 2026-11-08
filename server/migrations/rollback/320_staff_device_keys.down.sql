-- ============================================================
--  Rollback ِ 320_staff_device_keys.sql — فقط با دست.
--
--  ⚠️ پیش از اجرا پرچمِ `auth.staff_session_guard` را `false` کنید (`PUT /api/admin/flags`)؛ وگرنه نسخهٔ تازهٔ سرور
--  بی این جدول ورودِ شاگرد را با خطای پایگاه رد می‌کند. رازهای دستگاه می‌روند: پس از اجرای دوبارهٔ ۳۲۰، ورودِ
--  دوبارهٔ عضوِ موجود با کدِ شاگرد تا گرفتنِ رازِ تازه (`POST /api/auth/staff/device-key` از نشستِ باز) کدِ تازه می‌خواهد.
--
--  اجرا (در یک تراکنش):
--    psql "$DATABASE_URL" -1 -f migrations/rollback/320_staff_device_keys.down.sql
-- ============================================================

DROP INDEX IF EXISTS ix_staff_device_keys_user;
DROP INDEX IF EXISTS ux_staff_device_keys_live;
DROP TABLE IF EXISTS staff_device_keys;
DELETE FROM schema_migrations WHERE version = '320_staff_device_keys.sql';
