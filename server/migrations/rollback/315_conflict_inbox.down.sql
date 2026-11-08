-- ============================================================
--  Rollback ِ 315_conflict_inbox.sql — فقط با دست.
--
--  ردیف‌هایی که «دوباره ثبت» یا «اصلاح» شدند حالِ پیش از ۳۱۵ را می‌گیرند (`dismissed`) — سندِ دوباره‌ثبت‌شده خودش در
--  دفتر هست و دست نمی‌خورد؛ فقط برچسبِ صندوق ساده می‌شود. یادداشت‌ها با ستون می‌روند (سابقه‌اش در `audit_logs` می‌ماند).
--  پیش از اجرا کدِ پیشین را برگردانید.
--
--  اجرا (در یک تراکنش):
--    psql "$DATABASE_URL" -1 -f migrations/rollback/315_conflict_inbox.down.sql
-- ============================================================

UPDATE sync_v3_conflicts SET status = 'dismissed' WHERE status IN ('resubmitted', 'fixed');
ALTER TABLE sync_v3_conflicts DROP CONSTRAINT IF EXISTS sync_v3_conflicts_status_check;
ALTER TABLE sync_v3_conflicts ADD CONSTRAINT sync_v3_conflicts_status_check
  CHECK (status IN ('open','kept','reverted','dismissed'));
ALTER TABLE sync_v3_conflicts DROP COLUMN IF EXISTS resolution_note;

DELETE FROM schema_migrations WHERE version = '315_conflict_inbox.sql';
