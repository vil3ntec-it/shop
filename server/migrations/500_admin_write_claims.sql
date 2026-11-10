-- ============================================================
--  ۵۰۰ — نشانِ «در راه» ِ نوشتن‌های مدیر با `Idempotency-Key` (درخواستِ پنلِ مرکزی ۱.۵۰.۷۵، ممیزی §۳-۴؛ ۱۴۰۵/۰۸/۲۰)
--
--  ازکارانداختن/برگرداندنِ دستگاهِ کارمند، بازبینیِ بانکِ عکس و پشتیبانِ دستی هر کدام تراکنشِ خودشان را دارند (یا
--  اصلاً تراکنش نیستند: pg_dump)، پس «یک بار» را نمی‌شود در همان تراکنش گرفت (`lib/admin-write-once.js`). این جدول
--  نشانِ کوتاه‌عمرِ همان یک کلید است: پیش از رفتن زیرِ قفلِ همان کلید می‌نشیند، پس از پایان (چه شد چه نشد) برداشته
--  می‌شود و پاسخ در `idempotency_keys` (مهاجرتِ ۰۵۰) می‌ماند. دو درخواستِ هم‌زمان با یک کلید ⇒ دومی ۴۰۹
--  `idempotency_in_progress`؛ پس از پایان همان پاسخ. نشانِ فرآیندِ مُرده پس از `STALE_MS` کنار می‌رود و فقط همان یک
--  کلید را تا آن‌وقت می‌بندد (کلیدِ تازه آزاد است).
--
--  فقط افزودنی؛ هیچ جدولِ دیگری دست نمی‌خورد. rollback: migrations/rollback/500_admin_write_claims.down.sql
-- ============================================================

CREATE TABLE IF NOT EXISTS admin_write_claims (
  scope         text    NOT NULL,
  key           text    NOT NULL,
  token         text    NOT NULL,
  request_hash  text    NOT NULL,
  claimed_at    bigint  NOT NULL,
  PRIMARY KEY (scope, key)
);
CREATE INDEX IF NOT EXISTS ix_admin_write_claims_claimed ON admin_write_claims(claimed_at);
