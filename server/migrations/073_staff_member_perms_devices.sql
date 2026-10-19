-- ============================================================
--  مجوز و سقفِ هر کاربر (STF-03)، دستگاهِ گم‌شده (STF-10) و خاموش
--  کردنِ کدِ ثابتِ شاگرد (STF-11).
--
--  ⛔ هیچ ستونی پاک یا بازنویسی نمی‌شود؛ همه افزودنی و تهی‌پذیرند، پس
--  تا صاحب دکان کاری نکند رفتار همان امروز است.
--
--  برگشت:
--    DROP TABLE shop_member_perms;
--    ALTER TABLE devices DROP COLUMN disabled_at, DROP COLUMN disabled_by,
--      DROP COLUMN disabled_shop_id, DROP COLUMN blocked_attempts, DROP COLUMN last_blocked_at;
--    ALTER TABLE staff_codes DROP COLUMN disabled_at;
--  (دستگاهِ ازکارافتاده status='revoked' دارد و با برگشت هم بسته می‌ماند.)
-- ============================================================

--  لایهٔ «کاربر» — روی لایهٔ نقش می‌نشیند (همان قالبِ perms)
CREATE TABLE IF NOT EXISTS shop_member_perms (
  shop_id     text    NOT NULL REFERENCES shops(id) ON DELETE CASCADE,
  user_id     text    NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  perms       jsonb   NOT NULL DEFAULT '{}'::jsonb,
  updated_by  text    NOT NULL DEFAULT '',
  updated_at  bigint  NOT NULL,
  PRIMARY KEY (shop_id, user_id)
);

--  STF-10: صاحب دکان دستگاهِ گم‌شده را از کار می‌اندازد. status همان
--  'revoked' می‌شود (پس هر سنجشِ امروزی آن را می‌بندد)؛ این ستون‌ها فقط
--  می‌گویند چه کسی و کِی، تا خطا روشن باشد و تلاش‌های بعدی شمرده شوند.
ALTER TABLE devices ADD COLUMN IF NOT EXISTS disabled_at      bigint;
ALTER TABLE devices ADD COLUMN IF NOT EXISTS disabled_by      text;
ALTER TABLE devices ADD COLUMN IF NOT EXISTS disabled_shop_id text;
ALTER TABLE devices ADD COLUMN IF NOT EXISTS blocked_attempts integer NOT NULL DEFAULT 0;
ALTER TABLE devices ADD COLUMN IF NOT EXISTS last_blocked_at  bigint;

--  STF-11: کدِ ثابتی که صاحب دکان «خاموش» کرده دیگر خودبه‌خود ساخته
--  نمی‌شود؛ فقط «چرخاندن» دوباره روشنش می‌کند (با کدِ تازه).
ALTER TABLE staff_codes ADD COLUMN IF NOT EXISTS disabled_at bigint;
