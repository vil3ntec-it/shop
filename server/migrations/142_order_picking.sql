-- ============================================================
--  جمع‌آوری و بسته‌بندیِ سفارشِ آنلاین — PCK (v3 §۶.۱۷، سناریوهای S31 و S37)
--
--  ⛔ پشتِ پرچمِ خاموشِ `shop.orders.picking` (`lib/order-picking.js`): تا روشن نشود
--  هیچ ردیفی در این جدول‌ها نوشته نمی‌شود و هیچ رفتاری عوض نمی‌شود.
--
--  order_picks       برگهٔ جمع‌آوریِ هر سفارش (یکی برای هر سفارش)؛ از «در حال جمع‌آوری»
--                    تا «بسته‌بندی‌شده». مبلغِ پیش و پس از جمع‌آوری (PCK-04، PCK-07)، تعدادِ
--                    کیسه (PCK-09)، قفسهٔ انتظار (PCK-10) و دستهٔ جمع‌آوریِ گروهی (PCK-12).
--  order_pick_lines  هر قلمِ سفارش: مقدارِ سفارش، مقدارِ برداشته (وزنِ واقعی برای کالای وزنی)،
--                    وضعیت، اسکن‌های درست و نادرست (PCK-02) و جای قفسه در لحظهٔ شروع (PCK-01).
--  order_pick_events هر کارِ جمع‌آور با کلیدِ یکتا (صفِ آفلاین بی‌خطر دوباره می‌فرستد) — سابقه و
--                    شاخص‌ها (PCK-13) از همین‌جاست. فقط افزوده می‌شود.
--  pick_locations    جای هر کالا در دکان (راهرو، قفسه، طبقه) — PCK-01.
--  pick_issues       «پیدا نکردم» ⇒ وظیفهٔ بررسی برای مدیر و برداشتنِ کالا از ویترین تا رسیدگی
--                    (PCK-14). برای هر کالا فقط یک وظیفهٔ باز.
--
--  عمداً جدولِ stock_tasks (۰۸۴) دست نمی‌خورد: CHECK ِ kind ِ آن را #464 (مهاجرتِ ۱۳۷) هم عوض
--  می‌کند و دو مهاجرتِ ناهم‌ترتیب یکدیگر را پاک می‌کردند.
--
--  برگشت: DROP TABLE pick_issues, pick_locations, order_pick_events, order_pick_lines, order_picks
--  (به همین ترتیب). سفارش‌ها و قلم‌هایشان در `online_orders`/`online_order_lines` می‌مانند.
-- ============================================================

CREATE TABLE IF NOT EXISTS order_picks (
  order_id              text    PRIMARY KEY REFERENCES online_orders(id) ON DELETE CASCADE,
  shop_id               text    NOT NULL REFERENCES shops(id) ON DELETE CASCADE,
  status                text    NOT NULL DEFAULT 'picking' CHECK (status IN ('picking','packed','cancelled')),
  picker_id             text    NOT NULL DEFAULT '',
  --  PCK-12: جمع‌آوریِ چند سفارش با هم (خالی = تکی)
  batch_id              text    NOT NULL DEFAULT '',
  --  PCK-09
  bags                  integer NOT NULL DEFAULT 0 CHECK (bags BETWEEN 0 AND 50),
  --  PCK-10: قفسهٔ انتظار و لحظهٔ اسکنِ «آماده»
  shelf_slot            text    NOT NULL DEFAULT '',
  ready_scanned_at      bigint,
  --  مبلغِ سفارش پیش از جمع‌آوری و پس از بسته‌بندی (PCK-04، PCK-07)
  original_subtotal_minor bigint NOT NULL CHECK (original_subtotal_minor >= 0),
  original_total_minor  bigint  NOT NULL CHECK (original_total_minor >= 0),
  final_total_minor     bigint  CHECK (final_total_minor IS NULL OR final_total_minor >= 0),
  pack_key              text    NOT NULL DEFAULT '',
  started_at            bigint  NOT NULL,
  packed_at             bigint,
  version               integer NOT NULL DEFAULT 1,
  updated_at            bigint  NOT NULL
);
CREATE INDEX IF NOT EXISTS ix_order_picks_shop ON order_picks(shop_id, status, started_at);

CREATE TABLE IF NOT EXISTS order_pick_lines (
  order_id              text    NOT NULL REFERENCES order_picks(order_id) ON DELETE CASCADE,
  shop_id               text    NOT NULL,
  line_no               integer NOT NULL,
  product_id            text    NOT NULL DEFAULT '',
  name                  text    NOT NULL DEFAULT '',
  unit                  text    NOT NULL DEFAULT '',
  --  pending: هنوز نه · picked: کامل · short: کمتر از سفارش (یا وزنِ کمتر از حدِ مجاز)
  --  not_found: پیدا نشد · substituted / removed: برای PCK-05/06/07
  status                text    NOT NULL DEFAULT 'pending'
                        CHECK (status IN ('pending','picked','short','not_found','substituted','removed')),
  ordered_qty_milli     bigint  NOT NULL CHECK (ordered_qty_milli > 0),
  picked_qty_milli      bigint  NOT NULL DEFAULT 0 CHECK (picked_qty_milli >= 0),
  unit_price_minor      bigint  NOT NULL CHECK (unit_price_minor >= 0),
  original_total_minor  bigint  NOT NULL CHECK (original_total_minor >= 0),
  weighed               boolean NOT NULL DEFAULT false,
  --  PCK-01: جای قفسه در لحظهٔ شروع (برای ترتیب؛ تغییرِ بعدیِ جا برگهٔ باز را به هم نمی‌ریزد)
  aisle                 text    NOT NULL DEFAULT '',
  shelf                 text    NOT NULL DEFAULT '',
  level                 text    NOT NULL DEFAULT '',
  --  PCK-02
  scans                 integer NOT NULL DEFAULT 0,
  wrong_scans           integer NOT NULL DEFAULT 0,
  --  PCK-05/06 (جایگزین) — بعداً
  substitute            jsonb   NOT NULL DEFAULT '{}'::jsonb,
  updated_at            bigint  NOT NULL,
  PRIMARY KEY (order_id, line_no)
);

CREATE TABLE IF NOT EXISTS order_pick_events (
  id                    text    PRIMARY KEY,                   -- ULID
  order_id              text    NOT NULL REFERENCES order_picks(order_id) ON DELETE CASCADE,
  shop_id               text    NOT NULL,
  --  کلیدِ یکتای دستگاه (همان `eventId`)؛ همان کلید با بدنهٔ دیگر ⇒ ۴۰۹
  event_key             text    NOT NULL,
  request_hash          text    NOT NULL,
  kind                  text    NOT NULL CHECK (kind IN ('start','scan','pick','weigh','not_found','reset','pack')),
  line_no               integer,
  --  accepted | rejected (کالای اشتباه و …) — ردشده هم می‌ماند تا تکرارش همان جواب را بگیرد
  outcome               text    NOT NULL CHECK (outcome IN ('accepted','rejected')),
  code                  text    NOT NULL DEFAULT '',
  detail                jsonb   NOT NULL DEFAULT '{}'::jsonb,
  actor_id              text    NOT NULL DEFAULT '',
  created_at            bigint  NOT NULL,
  UNIQUE (shop_id, event_key)
);
CREATE INDEX IF NOT EXISTS ix_order_pick_events_order ON order_pick_events(order_id, created_at);
CREATE INDEX IF NOT EXISTS ix_order_pick_events_shop ON order_pick_events(shop_id, created_at);

CREATE TABLE IF NOT EXISTS pick_locations (
  shop_id               text    NOT NULL REFERENCES shops(id) ON DELETE CASCADE,
  product_id            text    NOT NULL,
  aisle                 text    NOT NULL DEFAULT '',
  shelf                 text    NOT NULL DEFAULT '',
  level                 text    NOT NULL DEFAULT '',
  updated_by            text    NOT NULL DEFAULT '',
  updated_at            bigint  NOT NULL,
  PRIMARY KEY (shop_id, product_id)
);

CREATE TABLE IF NOT EXISTS pick_issues (
  id                    text    PRIMARY KEY,                   -- ULID
  shop_id               text    NOT NULL REFERENCES shops(id) ON DELETE CASCADE,
  kind                  text    NOT NULL DEFAULT 'not_found' CHECK (kind IN ('not_found')),
  product_id            text    NOT NULL,
  --  نخستین و آخرین سفارشی که کالا در آن پیدا نشد
  order_id              text    NOT NULL DEFAULT '',
  last_order_id         text    NOT NULL DEFAULT '',
  status                text    NOT NULL DEFAULT 'open' CHECK (status IN ('open','resolved','dismissed')),
  hits                  integer NOT NULL DEFAULT 1,
  detail                jsonb   NOT NULL DEFAULT '{}'::jsonb,
  created_at            bigint  NOT NULL,
  updated_at            bigint  NOT NULL,
  resolved_by           text    NOT NULL DEFAULT '',
  resolved_at           bigint,
  note                  text    NOT NULL DEFAULT ''
);
CREATE UNIQUE INDEX IF NOT EXISTS ux_pick_issues_open ON pick_issues(shop_id, product_id) WHERE status = 'open';
CREATE INDEX IF NOT EXISTS ix_pick_issues_shop ON pick_issues(shop_id, status, created_at, id);
