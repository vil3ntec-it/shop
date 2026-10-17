-- ============================================================
--  تحویل و پیک — پایهٔ سرورِ فاز ۴ (۲۲.۲ مرحلهٔ C؛ DLV و PCK-11)
--
--  سه جدولِ تازه؛ هیچ جدولِ موجودی دست نمی‌خورد و تا پرچم‌های
--  `shop.orders` و `shop.delivery` در «تنظیماتِ زنده» روشن نشوند هیچ
--  ردیفی در آن‌ها نوشته نمی‌شود.
--
--  ⛔ وضعیتِ سفارش این‌جا نیست: هر اثرِ تحویل بر سفارش فقط از راهِ
--  `orders.transitionIn` (ماشینِ وضعیتِ سفارش) می‌رود. این‌جا فقط
--  «مأموریت» (assignment) و خطِ زمانیِ رویدادهای آن است.
--  ⛔ پول به واحدِ کوچک (یک‌صدمِ افغانی، bigint)؛ هیچ ممیزِ شناوری نیست.
--  ⛔ زمانِ دستگاه فقط نگه داشته می‌شود؛ مرجعِ تسویه `server_time` است.
-- ============================================================

--  پیکِ خودِ دکان — یک عضوِ همین دکان (حساب دارد و با همان وارد می‌شود)
CREATE TABLE IF NOT EXISTS shop_couriers (
  id              text    PRIMARY KEY,                     -- ULID
  shop_id         text    NOT NULL REFERENCES shops(id) ON DELETE CASCADE,
  user_id         text    NOT NULL,
  name            text    NOT NULL,
  phone           text    NOT NULL DEFAULT '',
  --  DLV-11: سقفِ نقدِ در دستِ پیک (پیش‌نهادِ D-18: ۱۰٬۰۰۰ افغانی)
  cash_cap_minor  bigint  NOT NULL DEFAULT 1000000 CHECK (cash_cap_minor >= 0),
  active          boolean NOT NULL DEFAULT true,
  created_by      text    NOT NULL DEFAULT '',
  created_at      bigint  NOT NULL,
  updated_at      bigint  NOT NULL,
  UNIQUE (shop_id, user_id)
);
CREATE INDEX IF NOT EXISTS ix_shop_couriers_user ON shop_couriers(user_id);

--  مأموریتِ تحویلِ یک سفارش — هر تلاش یک ردیف (attempt_no)
CREATE TABLE IF NOT EXISTS delivery_assignments (
  id                      text    PRIMARY KEY,             -- ULID
  shop_id                 text    NOT NULL REFERENCES shops(id) ON DELETE CASCADE,
  order_id                text    NOT NULL REFERENCES online_orders(id) ON DELETE CASCADE,
  attempt_no              integer NOT NULL,
  --  ۲۰.۶: چهار روشِ جدا. «platform» تا شبکهٔ پیکِ VILL3N واقعاً فعال نشده
  --  پذیرفته نمی‌شود (کد این را می‌سنجد)؛ CHECK فقط جا را نگه می‌دارد.
  mode                    text    NOT NULL CHECK (mode IN ('shop_courier','platform','external','pickup')),
  courier_id              text    NOT NULL DEFAULT '',
  external_name           text    NOT NULL DEFAULT '',
  external_ref            text    NOT NULL DEFAULT '',
  status                  text    NOT NULL CHECK (status IN (
                            'requested','rejected','withdrawn','assigned','picked_up',
                            'moving','arrived','delivered','failed','returned')),
  status_reason           text    NOT NULL DEFAULT '',
  --  ── پول (جدا از هم؛ ۲۲.۲ C بندِ ۵) ──
  --  مبلغی که باید از مشتری گرفته شود — سرور از سفارش می‌سازد؛ پیک عوضش نمی‌کند
  expected_collect_minor  bigint  NOT NULL DEFAULT 0 CHECK (expected_collect_minor >= 0),
  --  مبلغی که پیک واقعاً گرفت (تا «تحویل شد» تهی)
  cash_collected_minor    bigint  CHECK (cash_collected_minor IS NULL OR cash_collected_minor >= 0),
  --  کرایهٔ پیک برای همین مأموریت — دکان می‌گذارد
  fare_minor              bigint  NOT NULL DEFAULT 0 CHECK (fare_minor >= 0),
  --  گرفته ≠ مورد انتظار ⇒ «تحویل با اختلاف / در انتظارِ بررسی»
  cash_review             boolean NOT NULL DEFAULT false,
  --  ── کدِ تحویل (PCK-11 / DLV-08 / DLV-15) — فقط HMAC، هرگز خودِ کد ──
  code_hash               text    NOT NULL DEFAULT '',
  code_attempts           integer NOT NULL DEFAULT 0,
  code_used_at            bigint,
  --  ── لغو پس از حرکت (۲۲.۲ C بندِ ۶) ──
  recall_requested_at     bigint,
  recall_reason           text    NOT NULL DEFAULT '',
  recall_by               text    NOT NULL DEFAULT '',
  recall_outcome          text    NOT NULL DEFAULT '' CHECK (recall_outcome IN ('','pending','returned','too_late')),
  --  زمانِ سرورِ «تحویل شد» — مرجعِ دورهٔ تسویه (نه زمانِ دستگاه)
  delivered_at            bigint,
  settlement_id           text    NOT NULL DEFAULT '',
  version                 integer NOT NULL DEFAULT 1,
  created_by              text    NOT NULL DEFAULT '',
  created_at              bigint  NOT NULL,
  updated_at              bigint  NOT NULL,
  UNIQUE (order_id, attempt_no)
);
--  تخصیصِ اتمیک: هر سفارش در هر لحظه فقط یک مأموریتِ زنده دارد (۲۰.۶)
CREATE UNIQUE INDEX IF NOT EXISTS ux_delivery_assignments_live
  ON delivery_assignments(order_id)
  WHERE status IN ('requested','assigned','picked_up','moving','arrived','failed');
CREATE INDEX IF NOT EXISTS ix_delivery_assignments_courier
  ON delivery_assignments(shop_id, courier_id, status, updated_at);
CREATE INDEX IF NOT EXISTS ix_delivery_assignments_unsettled
  ON delivery_assignments(shop_id, courier_id, delivered_at)
  WHERE status = 'delivered' AND settlement_id = '';

--  خطِ زمانیِ مأموریت — فقط افزوده می‌شود. `event_key` کلیدِ یکتاییِ
--  رویداد است (شناسهٔ رویدادِ دستگاه برای صفِ آفلاین): تکرارش بی‌اثر است.
CREATE TABLE IF NOT EXISTS delivery_events (
  id              text    PRIMARY KEY,                     -- ULID (سرور)
  shop_id         text    NOT NULL,
  assignment_id   text    NOT NULL REFERENCES delivery_assignments(id) ON DELETE CASCADE,
  order_id        text    NOT NULL,
  seq             integer NOT NULL,
  event_key       text    NOT NULL,
  request_hash    text    NOT NULL DEFAULT '',
  kind            text    NOT NULL CHECK (kind IN ('created','status','recall','code','photo')),
  from_status     text    NOT NULL DEFAULT '',
  to_status       text    NOT NULL DEFAULT '',
  actor_type      text    NOT NULL CHECK (actor_type IN ('courier','user','system')),
  actor_id        text    NOT NULL DEFAULT '',
  device_id       text    NOT NULL DEFAULT '',
  --  زمان و ترتیبِ خودِ دستگاه — فقط برای نمایش و بررسی، نه مرجعِ تسویه
  device_time     bigint,
  device_seq      bigint,
  server_time     bigint  NOT NULL,
  reason          text    NOT NULL DEFAULT '',
  note            text    NOT NULL DEFAULT '',
  detail          jsonb   NOT NULL DEFAULT '{}'::jsonb,
  UNIQUE (shop_id, event_key),
  UNIQUE (assignment_id, seq)
);
CREATE INDEX IF NOT EXISTS ix_delivery_events_order ON delivery_events(order_id, server_time);
