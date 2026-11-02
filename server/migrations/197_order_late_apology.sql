-- ============================================================
--  عذرخواهی و کوپنِ خودکار برای تأخیرِ بیش از حد — NTF-10 (v3 §۶.۱۹)
--
--  ⛔ پشتِ پرچمِ خاموشِ `shop.orders.late_apology` (`lib/order-late-apology.js`): تا روشن نشود هیچ ردیفی
--  نوشته نمی‌شود و هیچ پیامی نمی‌رود.
--
--  order_late_apology_policy  سیاستِ صاحبِ دکان: روشن/خاموش، آستانهٔ تأخیر (دقیقه پس از زمانِ وعده‌شده)
--                             و کوپنِ اختیاری (مبلغ یا درصد، مهلت به روز). نبودِ ردیف = خاموش.
--  order_late_apologies       یک ردیف برای **هر سفارش** (کلیدِ اصلی = order_id) ⇒ دو نمونهٔ سرور یا دو دورِ
--                             کارگر پوزشِ دوم نمی‌سازند. کوپن یک‌بارمصرف است (`redeemed_at`)، فقط برای همان
--                             شماره (`phone_key` = HMAC ِ شماره، نه خودِ شماره) و همان دکان، تا `coupon_expires_at`.
--                             `skipped_customer` = تأخیر کارِ خودِ مشتری بود (بی پیام، بی کوپن).
--  customer_notify_outbox     رویدادِ تازهٔ `order_late_apology` (همان صف، همان یکتاییِ (order_id, event)).
--
--  فقط افزودنی. برگشت: DROP TABLE order_late_apologies, order_late_apology_policy و CHECK ِ پیشینِ `event`
--  (مهاجرتِ ۱۴۳) پس از پاک کردنِ ردیف‌های `order_late_apology`.
-- ============================================================

CREATE TABLE IF NOT EXISTS order_late_apology_policy (
  shop_id              text    PRIMARY KEY REFERENCES shops(id) ON DELETE CASCADE,
  enabled              boolean NOT NULL DEFAULT false,
  late_minutes         integer NOT NULL DEFAULT 30 CHECK (late_minutes BETWEEN 5 AND 1440),
  coupon_kind          text    NOT NULL DEFAULT '' CHECK (coupon_kind IN ('', 'amount', 'percent')),
  coupon_amount_minor  bigint  NOT NULL DEFAULT 0 CHECK (coupon_amount_minor BETWEEN 0 AND 100000000),
  coupon_bp            integer NOT NULL DEFAULT 0 CHECK (coupon_bp BETWEEN 0 AND 5000),
  coupon_days          integer NOT NULL DEFAULT 14 CHECK (coupon_days BETWEEN 1 AND 90),
  version              integer NOT NULL DEFAULT 1,
  updated_by           text    NOT NULL DEFAULT '',
  updated_at           bigint  NOT NULL
);

CREATE TABLE IF NOT EXISTS order_late_apologies (
  order_id             text    PRIMARY KEY REFERENCES online_orders(id) ON DELETE CASCADE,
  shop_id              text    NOT NULL REFERENCES shops(id) ON DELETE CASCADE,
  outcome              text    NOT NULL CHECK (outcome IN ('apology', 'skipped_customer')),
  late_minutes         integer NOT NULL,
  due_at               bigint  NOT NULL,
  phone_key            text    NOT NULL DEFAULT '',
  notify_id            text    NOT NULL DEFAULT '',
  coupon_code          text    NOT NULL DEFAULT '',
  coupon_kind          text    NOT NULL DEFAULT '' CHECK (coupon_kind IN ('', 'amount', 'percent')),
  coupon_amount_minor  bigint  NOT NULL DEFAULT 0,
  coupon_bp            integer NOT NULL DEFAULT 0,
  coupon_expires_at    bigint,
  redeemed_sale        text    NOT NULL DEFAULT '',
  redeemed_at          bigint,
  created_at           bigint  NOT NULL
);
CREATE UNIQUE INDEX IF NOT EXISTS ux_order_late_apologies_code
  ON order_late_apologies(shop_id, coupon_code) WHERE coupon_code <> '';
CREATE INDEX IF NOT EXISTS ix_order_late_apologies_shop ON order_late_apologies(shop_id, created_at);

ALTER TABLE customer_notify_outbox DROP CONSTRAINT IF EXISTS customer_notify_outbox_event_check;
ALTER TABLE customer_notify_outbox ADD CONSTRAINT customer_notify_outbox_event_check CHECK (event IN (
  'order_received', 'order_accepted', 'order_rejected', 'order_ready',
  'order_on_the_way', 'order_delivery_failed', 'order_delivery_retry',
  'order_completed', 'order_cancelled', 'order_late_apology'));
