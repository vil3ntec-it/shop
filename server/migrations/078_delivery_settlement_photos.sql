-- ============================================================
--  تحویل و پیک — تسویهٔ پیک و عکسِ تحویل (۲۲.۲ C بندهای ۴ و ۵؛ DLV-08/11/12)
--
--  دو جدولِ تازه؛ هیچ جدولِ موجودی دست نمی‌خورد. پشتِ همان پرچم‌های
--  `shop.orders` + `shop.delivery` (پیش‌فرض خاموش).
-- ============================================================

--  تسویهٔ پیک — سندِ بسته. ویرایش نمی‌شود؛ هر مأموریت حداکثر در یک تسویه
--  (`delivery_assignments.settlement_id`) و تکرارِ همان درخواست (کلیدِ یکتایی)
--  تسویهٔ دوم نمی‌سازد (S57، §۱۹.۹ بندِ ۶).
CREATE TABLE IF NOT EXISTS courier_settlements (
  id                     text    PRIMARY KEY,              -- ULID
  shop_id                text    NOT NULL REFERENCES shops(id) ON DELETE CASCADE,
  courier_id             text    NOT NULL,
  settle_no              bigint  NOT NULL,
  client_key             text    NOT NULL,
  request_hash           text    NOT NULL,
  status                 text    NOT NULL DEFAULT 'confirmed' CHECK (status IN ('confirmed')),
  currency               text    NOT NULL DEFAULT 'AFN',
  assignments_count      integer NOT NULL,
  --  دوره به زمانِ **سرور**ِ «تحویل شد» — نه زمانِ دستگاه
  period_from            bigint,
  period_to              bigint,
  expected_cash_minor    bigint  NOT NULL,
  collected_cash_minor   bigint  NOT NULL,
  collection_diff_minor  bigint  NOT NULL,
  fare_total_minor       bigint  NOT NULL,
  fare_paid_from_cash    boolean NOT NULL DEFAULT false,
  handover_due_minor     bigint  NOT NULL,
  handed_over_minor      bigint  NOT NULL CHECK (handed_over_minor >= 0),
  received_minor         bigint  NOT NULL CHECK (received_minor >= 0),
  shortage_minor         bigint  NOT NULL CHECK (shortage_minor >= 0),
  excess_minor           bigint  NOT NULL CHECK (excess_minor >= 0),
  reason                 text    NOT NULL DEFAULT '',
  note                   text    NOT NULL DEFAULT '',
  --  تأییدِ مدیر (DLV-12) — همان کسی که با `delivery.settle` ثبت می‌کند
  confirmed_by           text    NOT NULL,
  created_at             bigint  NOT NULL,
  UNIQUE (shop_id, client_key),
  UNIQUE (shop_id, settle_no)
);
CREATE INDEX IF NOT EXISTS ix_courier_settlements_courier
  ON courier_settlements(shop_id, courier_id, created_at DESC);

--  عکسِ تحویل — فقط با رضایتِ ثبت‌شده در سفارش و تأییدِ «فقط بسته» از پیک.
--  همان الگوی `product_photos`: bytea، نوع از روی بایت‌ها، حداکثر ۶۰۰KB.
--  ⛔ فقط صاحب و مدیر (`delivery.photo`) می‌بینند؛ پیک و شاگرد نه.
CREATE TABLE IF NOT EXISTS delivery_photos (
  id              text    PRIMARY KEY,                     -- ULID
  shop_id         text    NOT NULL REFERENCES shops(id) ON DELETE CASCADE,
  assignment_id   text    NOT NULL REFERENCES delivery_assignments(id) ON DELETE CASCADE,
  order_id        text    NOT NULL,
  courier_id      text    NOT NULL DEFAULT '',
  event_key       text    NOT NULL,
  mime            text    NOT NULL,
  size            integer NOT NULL,
  hash            text    NOT NULL,
  data            bytea   NOT NULL,
  consent         jsonb   NOT NULL DEFAULT '{}'::jsonb,
  device_time     bigint,
  created_by      text    NOT NULL DEFAULT '',
  created_at      bigint  NOT NULL,
  UNIQUE (shop_id, event_key)
);
CREATE INDEX IF NOT EXISTS ix_delivery_photos_assignment ON delivery_photos(assignment_id, created_at);
