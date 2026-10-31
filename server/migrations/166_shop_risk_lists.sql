-- ============================================================
--  فهرستِ سیاه و سفیدِ سفارشِ ویترینِ هر دکان — RSK-04 (§۶.۲۱)
--
--  هر ردیف: یک شماره یا یک پین (با شعاع) در فهرستِ «بسته» (block) یا «مطمئن»
--  (allow) همین دکان، با **دلیلِ نوشته** و **تاریخِ انقضا** (هر دو اجباری).
--  برداشتن نرم است (`removed_at`) تا سابقه بماند.
--
--  ⛔ فقط ثبتِ ویترین (`lib/order-risk.checkIn`، پشتِ پرچمِ `shop.order_risk`):
--  شمارهٔ بسته یا پینِ درونِ شعاعِ بسته ⇒ ۴۰۳ با پیامِ خنثی (دلیل به مشتری
--  گفته نمی‌شود)؛ شمارهٔ مطمئن از قاعده‌های تکرار و مشتریِ تازه رد نمی‌شود.
--  فروشِ حضوری، صندوق و سفارشِ کارمند هرگز این جدول را نمی‌خوانند.
--  ⛔ یک شمارهٔ فعال در یک دکان فقط در یکی از دو فهرست (نمایهٔ یکتای جزئی).
--  «دستگاه» هنوز نیست: ویترین شناسهٔ دستگاهِ مشتری ندارد.
--
--  بازگشت: DROP TABLE shop_risk_lists — هیچ مسیرِ دیگری آن را نمی‌خواند.
-- ============================================================

CREATE TABLE IF NOT EXISTS shop_risk_lists (
  id          text    PRIMARY KEY,                       -- ULID
  shop_id     text    NOT NULL REFERENCES shops(id) ON DELETE CASCADE,
  list        text    NOT NULL CHECK (list IN ('block', 'allow')),
  kind        text    NOT NULL CHECK (kind IN ('phone', 'pin')),
  phone       text    NOT NULL DEFAULT '',               -- شکلِ یکتای ‎+93…‎ (order-rate.canon)
  lat         double precision,
  lng         double precision,
  radius_m    integer,
  reason      text    NOT NULL CHECK (char_length(reason) BETWEEN 3 AND 200),
  expires_at  bigint  NOT NULL,
  created_by  text    NOT NULL DEFAULT '',
  created_at  bigint  NOT NULL,
  removed_at  bigint,
  removed_by  text    NOT NULL DEFAULT '',
  CHECK ((kind = 'phone' AND phone <> '' AND lat IS NULL AND lng IS NULL AND radius_m IS NULL)
      OR (kind = 'pin' AND phone = '' AND lat BETWEEN -90 AND 90 AND lng BETWEEN -180 AND 180
          AND radius_m BETWEEN 10 AND 2000 AND list = 'block'))
);

CREATE UNIQUE INDEX IF NOT EXISTS ux_shop_risk_lists_phone
  ON shop_risk_lists(shop_id, phone) WHERE kind = 'phone' AND removed_at IS NULL;
CREATE INDEX IF NOT EXISTS ix_shop_risk_lists_shop
  ON shop_risk_lists(shop_id, created_at) WHERE removed_at IS NULL;
