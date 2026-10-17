-- ============================================================
--  سفارشِ آنلاین — پایهٔ سرورِ فاز ۴ (بخشِ ۳.۹ بندِ ۲؛ ORD و CHK)
--
--  سه جدولِ تازه؛ هیچ جدولِ موجودی دست نمی‌خورد و تا پرچمِ `shop.orders`
--  در «تنظیماتِ زنده» روشن نشود هیچ ردیفی در آن‌ها نوشته نمی‌شود.
--
--  ⛔ وضعیت فقط از راهِ گذارِ مجازِ `lib/order-states.js` عوض می‌شود و هر
--  گذار یک ردیف در `online_order_events` دارد (زمان، کاربر، دلیل).
--  ⛔ پول به واحدِ کوچک (یک‌صدمِ افغانی، bigint) و مقدار به هزارم —
--  همان `Money.minor` و `Qty.milli` ی بخشِ ۳.۴؛ هیچ ممیزِ شناوری نیست.
--  ⛔ تکرارِ همان درخواست سفارشِ دوم نمی‌سازد: (shop_id, client_key) یکتاست
--  (CHK-19).
-- ============================================================

CREATE TABLE IF NOT EXISTS online_orders (
  id                  text    PRIMARY KEY,                 -- ULID
  shop_id             text    NOT NULL REFERENCES shops(id) ON DELETE CASCADE,
  --  شمارهٔ نمایشیِ هر دکان (۱، ۲، …) — سندِ فروش نیست؛ سریِ W جداست (ORD-20)
  order_no            bigint  NOT NULL,
  client_key          text    NOT NULL,
  request_hash        text    NOT NULL,
  --  ORD-21: صفِ یکپارچه. storefront فقط از درِ ویترین (هنوز ساخته نشده)
  source              text    NOT NULL CHECK (source IN ('storefront','phone','telegram','whatsapp','manual')),
  is_test             boolean NOT NULL DEFAULT false,
  status              text    NOT NULL DEFAULT 'new' CHECK (status IN (
                        'new','accepted','rejected','auto_rejected','cancelled_by_customer',
                        'picking','packed','ready_for_pickup','out_for_delivery','picked_up',
                        'cancelled','delivered','delivery_failed','retry_scheduled',
                        'returned_to_store','restocked','return_requested',
                        'refunded_partial','refunded_full')),
  fulfillment         text    NOT NULL CHECK (fulfillment IN ('delivery','pickup')),
  payment_method      text    NOT NULL DEFAULT 'cod' CHECK (payment_method IN ('cod','online')),
  payment_status      text    NOT NULL DEFAULT 'unpaid' CHECK (payment_status IN (
                        'unpaid','pending','paid','partially_refunded','refunded','failed')),
  --  فقط برای نقد هنگامِ تحویل؛ پرداختِ آنلاین ⇒ ''
  cod_status          text    NOT NULL DEFAULT '' CHECK (cod_status IN (
                        '','to_collect','collected_by_courier','collected_in_store','settled_to_shop')),
  customer_name       text    NOT NULL DEFAULT '',
  customer_phone      text    NOT NULL DEFAULT '',
  --  پین منبعِ اصلی است (CHK-08): {lat, lng, landmark, text}
  address             jsonb   NOT NULL DEFAULT '{}'::jsonb,
  --  CHK-23: {callBeforeArrival, leaveWith}
  delivery            jsonb   NOT NULL DEFAULT '{}'::jsonb,
  note                text    NOT NULL DEFAULT '',
  currency            text    NOT NULL DEFAULT 'AFN',
  items_count         integer NOT NULL DEFAULT 0,
  subtotal_minor      bigint  NOT NULL DEFAULT 0,
  discount_minor      bigint  NOT NULL DEFAULT 0 CHECK (discount_minor >= 0),
  delivery_fee_minor  bigint  NOT NULL DEFAULT 0 CHECK (delivery_fee_minor >= 0),
  total_minor         bigint  NOT NULL DEFAULT 0 CHECK (total_minor >= 0),
  --  null = فوری؛ پر = زمان‌بندی‌شده (CHK-12، برگهٔ «زمان‌بندی‌شده»)
  scheduled_for       bigint,
  --  زمانِ آماده‌شدنی که هنگامِ قبول گفته شد (ORD-07)
  ready_at            bigint,
  --  آخرین دلیلِ رد/لغو/ناموفق — تاریخچهٔ کامل در رویدادهاست
  status_reason       text    NOT NULL DEFAULT '',
  version             integer NOT NULL DEFAULT 1,
  created_by          text    NOT NULL DEFAULT '',
  created_at          bigint  NOT NULL,
  updated_at          bigint  NOT NULL,
  UNIQUE (shop_id, client_key),
  UNIQUE (shop_id, order_no)
);
CREATE INDEX IF NOT EXISTS ix_online_orders_shop_status
  ON online_orders(shop_id, status, created_at, id);
CREATE INDEX IF NOT EXISTS ix_online_orders_shop_updated
  ON online_orders(shop_id, updated_at DESC, id);

CREATE TABLE IF NOT EXISTS online_order_lines (
  id                text    PRIMARY KEY,                   -- ULID
  order_id          text    NOT NULL REFERENCES online_orders(id) ON DELETE CASCADE,
  shop_id           text    NOT NULL,
  line_no           integer NOT NULL,
  --  شناسهٔ کالا در دفترِ دکان؛ قلمِ دستی (تلفنی) می‌تواند بی‌کالا باشد
  product_id        text    NOT NULL DEFAULT '',
  name              text    NOT NULL,
  unit              text    NOT NULL DEFAULT '',
  qty_milli         bigint  NOT NULL CHECK (qty_milli > 0),
  unit_price_minor  bigint  NOT NULL CHECK (unit_price_minor >= 0),
  line_total_minor  bigint  NOT NULL CHECK (line_total_minor >= 0),
  --  CHK-16
  note              text    NOT NULL DEFAULT '',
  --  CHK-15: ''، best_match، specific، none، refund
  substitution      text    NOT NULL DEFAULT '' CHECK (substitution IN ('','best_match','specific','none','refund')),
  UNIQUE (order_id, line_no)
);
CREATE INDEX IF NOT EXISTS ix_online_order_lines_order ON online_order_lines(order_id, line_no);

--  خطِ زمانیِ سفارش (ORD-16) — فقط افزوده می‌شود، هرگز ویرایش یا پاک نمی‌شود
CREATE TABLE IF NOT EXISTS online_order_events (
  id            text    PRIMARY KEY,                       -- ULID
  order_id      text    NOT NULL REFERENCES online_orders(id) ON DELETE CASCADE,
  shop_id       text    NOT NULL,
  seq           integer NOT NULL,
  kind          text    NOT NULL CHECK (kind IN ('created','status','payment','cod','note')),
  from_status   text    NOT NULL DEFAULT '',
  to_status     text    NOT NULL DEFAULT '',
  actor_type    text    NOT NULL CHECK (actor_type IN ('user','system','customer')),
  actor_id      text    NOT NULL DEFAULT '',
  reason        text    NOT NULL DEFAULT '',
  note          text    NOT NULL DEFAULT '',
  detail        jsonb   NOT NULL DEFAULT '{}'::jsonb,
  created_at    bigint  NOT NULL,
  UNIQUE (order_id, seq)
);
CREATE INDEX IF NOT EXISTS ix_online_order_events_shop ON online_order_events(shop_id, created_at);
