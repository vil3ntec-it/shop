-- ============================================================
--  ۴۹۵ — نشانِ «در حالِ بازگرداندن» ِ تعارضِ همگام‌سازی (یافتهٔ ۱.۵۰.۷۴ ِ ممیزیِ پنلِ مرکزی؛ ۱۴۰۵/۰۸/۲۰)
--
--  `POST /api/admin/sync/conflicts/:id/restore` یک opِ تازه از طرفِ سرور می‌سازد (`lib/sync-v1.js` ⇒ `restoreConflict`) و
--  آن کار تراکنشِ خودش را دارد؛ پس «فقط یک بار» را نمی‌شود در همان تراکنش گرفت. این جدول نشانِ کوتاه‌عمرِ همان یک تعارض
--  است: پیش از رفتن زیرِ قفلِ همان تعارض نشسته، پس از پایان (چه شد چه نشد) برداشته می‌شود. دو مدیر/دو پنجره/دو نمونهٔ
--  خوشه ⇒ دومی ۴۰۹ `restore_in_progress`؛ پس از پایان `sync_conflicts.restored_at` ⇒ ۴۰۹ `already_restored`.
--  نشانِ فرآیندِ مُرده پس از `STALE_MS` ِ `lib/sync-restore-once.js` کنار می‌رود (راه هرگز برای همیشه بسته نمی‌ماند).
--
--  ⛔ `sync_conflicts` (جدولِ مشترکِ دکان و پمپ) دست نمی‌خورد؛ کلیدِ خارجی هم عمداً نیست تا پاک شدنِ حساب گیر نکند.
--  فقط افزودنی. rollback: migrations/rollback/495_sync_conflict_restore_claims.down.sql
-- ============================================================

CREATE TABLE IF NOT EXISTS sync_conflict_restore_claims (
  conflict_id  bigint  PRIMARY KEY,
  token        text    NOT NULL,
  admin_id     text    NOT NULL DEFAULT '',
  idem_key     text    NOT NULL DEFAULT '',
  claimed_at   bigint  NOT NULL
);
