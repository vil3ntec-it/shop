-- پنلِ مرکزی ۴.۶ و ۴.۷ (مدیریتِ بازار، نظارت بر پیک): نمایه برای پرسش‌های **همهٔ دکان‌ها** ِ مدیر
-- (`lib/admin-market.js`، `lib/admin-delivery.js`). نمایه‌های پیشین همه با `shop_id` آغاز می‌شوند؛ نمای مدیر
-- بی دکان است و بی این‌ها هر بار کلِ سفارش‌ها و رویدادها را پیمایش می‌کرد.
--
-- ⛔ فقط نمایه — هیچ ستون، جدول یا داده‌ای عوض نمی‌شود. شمارهٔ ۱۷۲ سهمِ «بازار/پیک» ِ پنلِ مرکزی است.
-- برگشت (هر خط جدا؛ داده دست نمی‌خورد):
--   rollback: DROP INDEX IF EXISTS ix_online_orders_updated_all;
--   rollback: DROP INDEX IF EXISTS ix_online_orders_open_status;
--   rollback: DROP INDEX IF EXISTS ix_online_orders_storefront_created;
--   rollback: DROP INDEX IF EXISTS ix_delivery_assignments_live_status;
--   rollback: DROP INDEX IF EXISTS ix_delivery_events_time;
--   rollback: DELETE FROM schema_migrations WHERE version = '172_console_market_delivery.sql';
CREATE INDEX IF NOT EXISTS ix_online_orders_updated_all ON online_orders(updated_at DESC, id);
CREATE INDEX IF NOT EXISTS ix_online_orders_open_status ON online_orders(status)
  WHERE status IN ('new','accepted','picking','packed','ready_for_pickup','out_for_delivery','delivery_failed',
                   'retry_scheduled','returned_to_store','return_requested');
CREATE INDEX IF NOT EXISTS ix_online_orders_storefront_created ON online_orders(created_at DESC, id)
  WHERE source = 'storefront';
CREATE INDEX IF NOT EXISTS ix_delivery_assignments_live_status ON delivery_assignments(status, updated_at)
  WHERE status IN ('requested','assigned','picked_up','moving','arrived','failed');
CREATE INDEX IF NOT EXISTS ix_delivery_events_time ON delivery_events(server_time DESC, id);
