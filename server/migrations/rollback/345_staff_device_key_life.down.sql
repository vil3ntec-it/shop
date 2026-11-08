-- ============================================================
--  Rollback ِ 345_staff_device_key_life.sql — فقط با دست، پیش از rollback ِ ۳۲۰.
--
--  ⚠️ نسخهٔ تازهٔ سرور بی این ستون‌ها باطل کردنِ رازِ دستگاه را با خطای پایگاه رد می‌کند (و با آن «خروج از همه»،
--  برداشتنِ عضو و حذفِ حساب را) ⇒ اول همان نسخهٔ پیشینِ سرور را بالا بیاورید. رازهای باطل‌شده باطل می‌مانند
--  (`revoked_at` دست نمی‌خورد)؛ فقط دلیل و نگهبانِ «برنگشتن» می‌روند.
--
--  اجرا (در یک تراکنش):
--    psql "$DATABASE_URL" -1 -f migrations/rollback/345_staff_device_key_life.down.sql
-- ============================================================

DROP INDEX IF EXISTS ix_staff_device_keys_shop_live;
DROP TRIGGER IF EXISTS staff_device_keys_guard ON staff_device_keys;
DROP FUNCTION IF EXISTS staff_device_keys_guard();
ALTER TABLE staff_device_keys DROP COLUMN IF EXISTS revoked_by;
ALTER TABLE staff_device_keys DROP COLUMN IF EXISTS revoked_reason;
DELETE FROM schema_migrations WHERE version = '345_staff_device_key_life.sql';
