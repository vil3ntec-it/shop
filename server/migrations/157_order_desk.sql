-- ============================================================
--  میزِ سفارشِ دکان (lib/order-desk.js) — ORD-04، ORD-08، ORD-09.
--
--    «شلوغم» با پایانِ خودکار (busy_until، دقیقه‌های افزوده)، توقفِ موقتِ
--    سفارش‌گیریِ ویترین با دلیل و مدت (paused_until)، توقفِ خودکار وقتی صف از حد
--    بگذرد یا سفارش بی‌پاسخ بماند (auto_pause_*)، و قبولِ خودکار با سقفِ صف
--    (auto_accept_*). همهٔ زمان‌ها ساعتِ سرور است؛ «پایان» با گذشتنِ زمان خودش
--    می‌رسد (هیچ کارگری لازم نیست تا چیزی را خاموش کند).
--
--  ⛔ فقط افزودنی: دو جدولِ تازه؛ هیچ جدولِ موجودی دست نمی‌خورد. تا پرچمِ
--  `shop.orders.desk` روشن نشود هیچ ردیفی نوشته نمی‌شود و هیچ رفتاری عوض نمی‌شود.
--  ⛔ تکرارِ همان درخواست اثرِ دوم ندارد: (shop_id, event_id) یکتاست.
--  برگشت: DROP TABLE order_desk_events; DROP TABLE order_desk;
-- ============================================================

CREATE TABLE IF NOT EXISTS order_desk (
  shop_id                text    PRIMARY KEY REFERENCES shops(id) ON DELETE CASCADE,
  --  ORD-08 «شلوغم»: تا این لحظه، و این‌قدر دقیقه بیشتر برای آماده شدن
  busy_until             bigint,
  busy_extra_minutes     integer NOT NULL DEFAULT 0 CHECK (busy_extra_minutes BETWEEN 0 AND 240),
  --  ORD-08 توقفِ موقت / ORD-09 توقفِ خودکار: ویترین تا این لحظه سفارشِ تازه نمی‌گیرد
  paused_until           bigint,
  pause_reason           text    NOT NULL DEFAULT '',
  pause_note             text    NOT NULL DEFAULT '',
  pause_source           text    NOT NULL DEFAULT '' CHECK (pause_source IN ('', 'user', 'auto')),
  --  ORD-09 (پیش‌فرض خاموش): بیش از max_open سفارشِ باز، یا تازه‌ای قدیمی‌تر از oldest_min ⇒ توقف برای minutes
  auto_pause             boolean NOT NULL DEFAULT false,
  auto_pause_max_open    integer NOT NULL DEFAULT 10 CHECK (auto_pause_max_open BETWEEN 1 AND 500),
  auto_pause_oldest_min  integer NOT NULL DEFAULT 30 CHECK (auto_pause_oldest_min BETWEEN 1 AND 1440),
  auto_pause_minutes     integer NOT NULL DEFAULT 30 CHECK (auto_pause_minutes BETWEEN 5 AND 1440),
  --  ORD-04 (پیش‌فرض خاموش): قبولِ خودکارِ سفارشِ ویترین تا وقتی صف کمتر از max_open است
  auto_accept            boolean NOT NULL DEFAULT false,
  auto_accept_max_open   integer NOT NULL DEFAULT 5 CHECK (auto_accept_max_open BETWEEN 1 AND 500),
  auto_accept_ready_min  integer NOT NULL DEFAULT 15 CHECK (auto_accept_ready_min BETWEEN 1 AND 1440),
  version                integer NOT NULL DEFAULT 1,
  updated_by             text    NOT NULL DEFAULT '',
  updated_at             bigint  NOT NULL
);
CREATE INDEX IF NOT EXISTS ix_order_desk_auto_pause ON order_desk(shop_id) WHERE auto_pause;

--  سابقهٔ میز — فقط افزوده می‌شود؛ event_id ِ کلاینت (یا شناسهٔ دقیقهٔ کارگر) یکتاست
CREATE TABLE IF NOT EXISTS order_desk_events (
  id            text    PRIMARY KEY,
  shop_id       text    NOT NULL REFERENCES shops(id) ON DELETE CASCADE,
  event_id      text    NOT NULL,
  kind          text    NOT NULL CHECK (kind IN ('busy', 'busy_end', 'pause', 'resume', 'auto_pause', 'settings')),
  actor_type    text    NOT NULL CHECK (actor_type IN ('user', 'system')),
  actor_id      text    NOT NULL DEFAULT '',
  until_at      bigint,
  reason        text    NOT NULL DEFAULT '',
  note          text    NOT NULL DEFAULT '',
  request_hash  text    NOT NULL DEFAULT '',
  detail        jsonb   NOT NULL DEFAULT '{}'::jsonb,
  created_at    bigint  NOT NULL,
  UNIQUE (shop_id, event_id)
);
CREATE INDEX IF NOT EXISTS ix_order_desk_events_shop ON order_desk_events(shop_id, created_at);
