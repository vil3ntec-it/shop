-- ============================================================
--  Rollback ِ 435_stock_soft_reservations.sql — فقط با دست.
--
--  پیش از اجرا پرچمِ `shop.stock_reserve` را برای همه خاموش کنید و کدِ پیشین را برگردانید: کدِ تازه بی این
--  ستون‌ها ۵۰۰ می‌دهد. ردیف‌ها پاک نمی‌شوند: کدِ پیشین هر نگه‌داشتهٔ باز را در نخستین خواندنِ کاتالوگ
--  (`catalog.retireOrderStock`) با حرکتِ release ِ دفتر آزاد می‌کند، پس تطبیقِ INV-16 سالم می‌ماند.
--
--  اجرا (در یک تراکنش):
--    psql "$DATABASE_URL" -1 -f migrations/rollback/435_stock_soft_reservations.down.sql
-- ============================================================

DROP INDEX IF EXISTS ix_stock_reservations_soft_held;
ALTER TABLE stock_reservations DROP CONSTRAINT IF EXISTS stock_reservations_kind_check;
ALTER TABLE stock_reservations DROP COLUMN IF EXISTS short_at;
ALTER TABLE stock_reservations DROP COLUMN IF EXISTS kind;

DELETE FROM schema_migrations WHERE version = '435_stock_soft_reservations.sql';
