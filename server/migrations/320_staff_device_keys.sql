-- ============================================================
--  ۳۲۰ — رازِ دستگاهِ شاگرد (ورودِ دوباره با کدِ شاگرد؛ `src/lib/staff-device-keys.js`)
--
--  ⛔ شناسهٔ دستگاه (`device.uid` در بدنه، `X-Device`) راز نیست: همکار در Pull و هر درخواستِ همگام‌سازی آن را
--  می‌بیند. پیش‌تر `/auth/staff` عضوِ موجود را فقط با «کدِ دکان + شناسهٔ دستگاه» پیدا می‌کرد و وضعیتِ کد را هم
--  نمی‌سنجید ⇒ هر کس کدِ شاگرد (حتی باطل‌شده) و شناسهٔ دستگاهِ عضوِ دیگری را داشت نشستِ همان عضو را می‌گرفت.
--  حالا ورودِ دوبارهٔ عضوِ موجود رازی می‌خواهد که دستگاه در نخستین پیوستن گرفته و در حافظهٔ امنِ خودش نگه داشته
--  (Keystore ِ اندروید، Keychain ِ آیفون، فایلِ فقط-صاحبِ کامپیوتر). سرور فقط HMAC ِ آن را دارد، نه خودش.
--
--  یک رازِ زنده برای هر «دکان + کاربر + دستگاه» (نمایهٔ یکتای جزئی)؛ رازِ تازه پیشین را باطل می‌کند (`revoked_at`).
--  فقط افزودنی: هیچ جدول یا ستونِ موجودی دست نمی‌خورد؛ حذفِ کاربر یا دکان رازهایش را هم می‌برد (CASCADE).
--  برگشت: migrations/rollback/320_staff_device_keys.down.sql (آزموده در test/staff-session-guard.test.js)
-- ============================================================

CREATE TABLE IF NOT EXISTS staff_device_keys (
  id            text   PRIMARY KEY,
  shop_id       text   NOT NULL REFERENCES shops(id) ON DELETE CASCADE,
  user_id       text   NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  device_uid    text   NOT NULL,
  key_hash      text   NOT NULL UNIQUE,
  created_at    bigint NOT NULL,
  last_used_at  bigint,
  revoked_at    bigint
);

CREATE UNIQUE INDEX IF NOT EXISTS ux_staff_device_keys_live
  ON staff_device_keys (shop_id, user_id, device_uid) WHERE revoked_at IS NULL;

CREATE INDEX IF NOT EXISTS ix_staff_device_keys_user ON staff_device_keys (user_id);
