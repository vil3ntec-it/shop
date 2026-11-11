-- ============================================================
--  ۷۶۰ — وزنِ بیش از حد تا پاسخِ مشتری (S56، §۱۶؛ PCK-04، ۱۴۰۵/۰۸/۲۰)
--
--  تا امروز وزنِ واقعیِ بیش از حدِ مجاز (±۱۰٪) فقط رد می‌شد (`over_tolerance`) و جمع‌آور باید کمتر برمی‌داشت.
--  حالا با پرچمِ خاموشِ `shop.orders.weight_hold` (lib/pick-weight-hold.js) همان وزن یک «نگه‌داشت» می‌سازد:
--  قلم باز می‌ماند و سفارش تا پاسخِ مشتری (لینکِ پیگیری) یا کارمند (پس از تماس) بسته‌بندی نمی‌شود (۴۰۹
--  `weight_hold_pending`). پذیرش ⇒ قلم با همان وزن و مبلغ؛ رد یا بی‌پاسخ تا مهلت ⇒ قلم حذف و از جمع کسر.
--  ⛔ خاموش ⇒ هیچ ردیفی در این جدول نوشته نمی‌شود و همان ۴۰۰ ِ پیشین.
--
--  هر قلم حداکثر یک نگه‌داشتِ «در انتظار» یا «پذیرفته»؛ ردشده/پس‌گرفته می‌ماند (سابقه).
--  فقط افزودنی. rollback: migrations/rollback/760_pick_weight_holds.down.sql
-- ============================================================

CREATE TABLE IF NOT EXISTS order_pick_weight_holds (
  id                    text    PRIMARY KEY,                   -- ULID
  order_id              text    NOT NULL REFERENCES order_picks(order_id) ON DELETE CASCADE,
  shop_id               text    NOT NULL,
  line_no               integer NOT NULL,
  --  آن‌چه مشتری می‌بیند: سفارش، وزنِ واقعی و مبلغِ همان وزن (قیمتِ واحدِ ثبت‌شدهٔ سفارش)
  ordered_qty_milli     bigint  NOT NULL CHECK (ordered_qty_milli > 0),
  actual_qty_milli      bigint  NOT NULL CHECK (actual_qty_milli > ordered_qty_milli),
  unit_price_minor      bigint  NOT NULL CHECK (unit_price_minor >= 0),
  original_total_minor  bigint  NOT NULL CHECK (original_total_minor >= 0),
  total_minor           bigint  NOT NULL CHECK (total_minor >= 0),
  status                text    NOT NULL DEFAULT 'pending' CHECK (status IN ('pending','accepted','rejected','withdrawn')),
  decided_by            text    NOT NULL DEFAULT '' CHECK (decided_by IN ('','customer','staff','timeout','picker')),
  decided_actor         text    NOT NULL DEFAULT '',
  decided_at            bigint,
  deadline_at           bigint  NOT NULL,
  --  همان کلیدِ رویدادِ «وزن» ِ جمع‌آور (صفِ آفلاین بی‌خطر دوباره می‌فرستد)
  event_key             text    NOT NULL,
  proposed_by           text    NOT NULL DEFAULT '',
  created_at            bigint  NOT NULL,
  updated_at            bigint  NOT NULL,
  UNIQUE (shop_id, event_key)
);
CREATE INDEX IF NOT EXISTS ix_pick_weight_holds_order ON order_pick_weight_holds(order_id, line_no, created_at);
CREATE UNIQUE INDEX IF NOT EXISTS ux_pick_weight_holds_live ON order_pick_weight_holds(order_id, line_no)
  WHERE status IN ('pending','accepted');
