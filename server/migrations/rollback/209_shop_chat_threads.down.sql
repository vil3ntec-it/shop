-- ============================================================
--  Rollback ِ 209_shop_chat_threads.sql — فقط با دست.
--
--  «مشکل دارم» ِ زده‌شده و آغازِ دورِ تازهٔ گفت‌وگوی عمومی فراموش می‌شوند؛ هیچ گفت‌وگو، پیام یا رسانه‌ای پاک
--  نمی‌شود و کلیدِ یکتای (دکان، شماره، سفارش) همان امروز است. پیش از اجرا کدِ پیش از این نسخه را برگردانید.
--
--  اجرا (در یک تراکنش):
--    psql "$DATABASE_URL" -1 -f migrations/rollback/209_shop_chat_threads.down.sql
-- ============================================================

ALTER TABLE shop_chat_conversations DROP COLUMN IF EXISTS session_at;
ALTER TABLE shop_chat_conversations DROP COLUMN IF EXISTS issue_opened_at;
DELETE FROM schema_migrations WHERE version = '209_shop_chat_threads.sql';
