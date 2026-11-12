-- ============================================================
--  ۸۴۰ — «دستگاه» در فهرستِ سیاه و سفیدِ سفارشِ ویترین (RSK-04، پشتِ پرچمِ خاموشِ `shop.order_risk`)
--
--  شناسهٔ دستگاه همان شناسهٔ **تصادفیِ** هر مرورگر/نصبِ برنامهٔ خرید است (`deviceId` ِ بدنهٔ ثبت، MKT-02) — نه
--  شناسهٔ سخت‌افزار. سرور خودِ آن را نگه نمی‌دارد: فقط چکیدهٔ SHA-256 ِ «دکان + شناسه» (`lib/risk-device.js`)، و فقط
--  وقتی پرچمِ همان دکان روشن است. دکان هرگز چکیده را نمی‌بیند؛ «این دستگاه» را فقط از راهِ سفارشِ خودش می‌بندد.
--
--  online_order_devices   سفارشِ ویترین (دکان + کلیدِ یکتاییِ ثبت) ⇐ چکیدهٔ دستگاه؛ در همان تراکنشِ ثبت؛ با «حذفِ
--                         دادهٔ مشتری» (SEC-10) پاک می‌شود.
--  shop_risk_lists        نوعِ سومِ `device` (بسته یا مطمئن) با `device_key` و شمارهٔ سفارشی که از آن آمد (`order_no`،
--                         فقط برای نمایش)؛ یک دستگاهِ فعال فقط در یک فهرست.
--
--  ⛔ افزودنی: ستون‌ها پیش‌فرض دارند و نسخهٔ پیشین روی همین پایگاه همان ردیف‌های شماره/پین را می‌نویسد.
--  Rollback: migrations/rollback/840_risk_list_devices.down.sql
-- ============================================================

CREATE TABLE IF NOT EXISTS online_order_devices (
  shop_id     text    NOT NULL REFERENCES shops(id) ON DELETE CASCADE,
  client_key  text    NOT NULL,
  device_key  text    NOT NULL CHECK (device_key ~ '^[0-9a-f]{32}$'),
  created_at  bigint  NOT NULL,
  PRIMARY KEY (shop_id, client_key)
);

ALTER TABLE shop_risk_lists ADD COLUMN IF NOT EXISTS device_key text NOT NULL DEFAULT '';
ALTER TABLE shop_risk_lists ADD COLUMN IF NOT EXISTS order_no bigint;

ALTER TABLE shop_risk_lists DROP CONSTRAINT IF EXISTS shop_risk_lists_kind_check;
ALTER TABLE shop_risk_lists ADD CONSTRAINT shop_risk_lists_kind_check CHECK (kind IN ('phone', 'pin', 'device'));
ALTER TABLE shop_risk_lists DROP CONSTRAINT IF EXISTS shop_risk_lists_check;
ALTER TABLE shop_risk_lists DROP CONSTRAINT IF EXISTS shop_risk_lists_shape_check;
ALTER TABLE shop_risk_lists ADD CONSTRAINT shop_risk_lists_shape_check CHECK (
     (kind = 'phone' AND phone <> '' AND device_key = '' AND lat IS NULL AND lng IS NULL AND radius_m IS NULL)
  OR (kind = 'pin' AND phone = '' AND device_key = '' AND lat BETWEEN -90 AND 90 AND lng BETWEEN -180 AND 180
      AND radius_m BETWEEN 10 AND 2000 AND list = 'block')
  OR (kind = 'device' AND phone = '' AND device_key ~ '^[0-9a-f]{32}$' AND lat IS NULL AND lng IS NULL AND radius_m IS NULL)
);

CREATE UNIQUE INDEX IF NOT EXISTS ux_shop_risk_lists_device
  ON shop_risk_lists(shop_id, device_key) WHERE kind = 'device' AND removed_at IS NULL;
