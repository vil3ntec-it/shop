-- ============================================================
--  Rollback ِ 855_trust_assignee_name.sql — فقط با دست.
--
--  نامِ مسئول می‌رود؛ شناسهٔ مسئول (`assignee`) و همهٔ پرونده‌ها دست نمی‌خورند. کدِ پیشین این ستون را نمی‌شناسد.
--
--  اجرا (در یک تراکنش):
--    psql "$DATABASE_URL" -1 -f migrations/rollback/855_trust_assignee_name.down.sql
-- ============================================================

ALTER TABLE trust_cases DROP COLUMN IF EXISTS assignee_name;
