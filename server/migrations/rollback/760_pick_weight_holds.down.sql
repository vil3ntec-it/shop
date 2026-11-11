-- ============================================================
--  Rollback ِ 760_pick_weight_holds.sql — فقط با دست.
--
--  نگه‌داشت‌های وزن (و سابقهٔ پاسخِ مشتری) می‌روند؛ قلم‌های برگه همان وضعیتی را دارند که تصمیم داده بود.
--  پیش از اجرا کدِ پیشین را برگردانید (یا پرچمِ `shop.orders.weight_hold` را خاموش کنید و نگه‌داشتِ باز نماند).
--
--  اجرا (در یک تراکنش):
--    psql "$DATABASE_URL" -1 -f migrations/rollback/760_pick_weight_holds.down.sql
-- ============================================================

DROP TABLE IF EXISTS order_pick_weight_holds;

DELETE FROM schema_migrations WHERE version = '760_pick_weight_holds.sql';
