-- نگهبانِ تأییدِ کارمند (STF-02/03، staff-approval.js → acceptedPartOf): «این سندِ برگشت دنبالهٔ
-- ثبتی است که بخشِ اولش پذیرفته شد؟» با همان دستگاه و همان ts ِ op. بی این نمایه پرس‌وجو همهٔ
-- opهای برگشتِ دکان را می‌خواند. فقط ردیف‌های returns (جدولِ دکان؛ پمپ چنین ردیفی ندارد).
-- فقط نمایه — هیچ ستون یا داده‌ای عوض نمی‌شود. برگشت: DROP INDEX.
CREATE INDEX IF NOT EXISTS ix_oplog_returns_device_ts
  ON oplog(app, account_kind, account_id, device_id, client_ts) WHERE table_name = 'returns';
