-- S5 (قراردادِ بازار §۳، §۴۰.۱): نمای پیک برای مشتری — هر دو پشتِ پرچمِ خاموش.
--
--   code_nonce ─ دانهٔ تصادفیِ کدِ تحویل. فقط وقتی پر می‌شود که پرچمِ
--                `shop.delivery_customer_view` هنگامِ ساختِ کد روشن باشد؛ کد از
--                HMAC(رمزِ سرور، شناسهٔ مأموریت، دانه) دوباره ساخته می‌شود تا مشتری
--                آن را ببیند. خودِ کد هنوز هیچ‌جا نوشته نمی‌شود. پس از تحویل یا
--                پایانِ مأموریت پاک می‌شود ⇒ دیگر حتی سرور هم کد را نمی‌سازد.
--   eta_at ──── «تقریباً کی می‌رسد» که خودِ پیک می‌گوید (زمانِ سرور).
--   delivery_locations ─ فقط آخرین نقطهٔ پیک برای هر مأموریت (نه تاریخچه)، فقط
--                بینِ «گرفتنِ بسته» و «رسیدم»، پشتِ پرچمِ جدای
--                `shop.delivery_live_location`. هر گذار به وضعیتِ دیگر ردیف را در
--                همان تراکنش پاک می‌کند؛ ردیفِ کهنه‌تر از چند دقیقه هم پاک می‌شود.
--
-- فقط افزودنی — هیچ داده‌ای عوض نمی‌شود.
-- ROLLBACK:
-- DROP TABLE IF EXISTS delivery_locations;
-- ALTER TABLE delivery_assignments DROP COLUMN IF EXISTS eta_at;
-- ALTER TABLE delivery_assignments DROP COLUMN IF EXISTS code_nonce;
ALTER TABLE delivery_assignments ADD COLUMN IF NOT EXISTS code_nonce text NOT NULL DEFAULT '';
ALTER TABLE delivery_assignments ADD COLUMN IF NOT EXISTS eta_at bigint;

CREATE TABLE IF NOT EXISTS delivery_locations (
  assignment_id  text    PRIMARY KEY REFERENCES delivery_assignments(id) ON DELETE CASCADE,
  shop_id        text    NOT NULL,
  lat            double precision NOT NULL CHECK (lat BETWEEN -90 AND 90),
  lng            double precision NOT NULL CHECK (lng BETWEEN -180 AND 180),
  accuracy_m     integer CHECK (accuracy_m IS NULL OR accuracy_m >= 0),
  device_time    bigint,
  server_time    bigint  NOT NULL
);
CREATE INDEX IF NOT EXISTS ix_delivery_locations_server_time ON delivery_locations(server_time);
