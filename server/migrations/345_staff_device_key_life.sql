-- ============================================================
--  ۳۴۵ — عمرِ رازِ دستگاهِ شاگرد (`src/lib/staff-device-keys.js`، پس از ۳۲۰)
--
--  رازِ دستگاه وقتی اعتماد تمام شد باطل می‌شود — تصمیمِ سرور، درونِ همان تراکنشِ کارِ اصلی: «خروج از همهٔ
--  دستگاه‌ها» (خودِ شاگرد یا صاحبِ دکان)، برداشتن یا تعلیقِ عضو، باطل کردنِ کدِ شماره‌دارِ خودِ او، حذفِ حساب، و
--  «باطل کردنِ این دستگاه» ِ صاحب. پس از آن دستگاه فقط با کدِ تازهٔ صاحب برمی‌گردد (و آن هم حسابِ تازه).
--
--  - `revoked_reason` / `revoked_by`: چرا و به دستِ چه کسی (هرگز خودِ راز یا HMAC ِ آن در سابقه)؛ ردیف‌های باطلِ
--    پیشین فقط از «رازِ تازه برای همان دستگاه» بودند ⇒ `rotated`.
--  - ⛔ نگهبان (trigger): رازِ باطل‌شده **برنمی‌گردد** — هیچ UPDATE ی روی ردیفِ باطل پذیرفته نمی‌شود، و پیوندِ
--    راز (دکان، کاربر، دستگاه، HMAC، زمانِ ساخت) هرگز عوض نمی‌شود. راهِ برگشت فقط رازِ تازه است.
--  - نمایهٔ جزئیِ «رازهای زندهٔ هر دکان» برای فهرستِ صاحب (`GET /api/shop/staff-keys`).
--  برگشت: migrations/rollback/345_staff_device_key_life.down.sql (آزموده در test/staff-key-life.test.js)
-- ============================================================

ALTER TABLE staff_device_keys ADD COLUMN IF NOT EXISTS revoked_reason text;
ALTER TABLE staff_device_keys ADD COLUMN IF NOT EXISTS revoked_by     text;

DROP TRIGGER IF EXISTS staff_device_keys_guard ON staff_device_keys;
UPDATE staff_device_keys SET revoked_reason = 'rotated' WHERE revoked_at IS NOT NULL AND revoked_reason IS NULL;

CREATE OR REPLACE FUNCTION staff_device_keys_guard() RETURNS trigger
LANGUAGE plpgsql AS $$
BEGIN
  IF OLD.revoked_at IS NOT NULL THEN
    RAISE EXCEPTION 'staff_device_keys: a revoked key is final' USING ERRCODE = 'check_violation';
  END IF;
  IF NEW.key_hash IS DISTINCT FROM OLD.key_hash OR NEW.shop_id IS DISTINCT FROM OLD.shop_id
     OR NEW.user_id IS DISTINCT FROM OLD.user_id OR NEW.device_uid IS DISTINCT FROM OLD.device_uid
     OR NEW.created_at IS DISTINCT FROM OLD.created_at THEN
    RAISE EXCEPTION 'staff_device_keys: the key binding is immutable' USING ERRCODE = 'check_violation';
  END IF;
  RETURN NEW;
END $$;

CREATE TRIGGER staff_device_keys_guard
  BEFORE UPDATE ON staff_device_keys
  FOR EACH ROW EXECUTE FUNCTION staff_device_keys_guard();

CREATE INDEX IF NOT EXISTS ix_staff_device_keys_shop_live
  ON staff_device_keys (shop_id, created_at) WHERE revoked_at IS NULL;
