-- ============================================================
--  موجودیِ هوشمند — دفترِ گردشِ فقط‌افزودنی، مانده‌ها و رزرو
--  v3 §۳۲.۱ / §۳۲.۲ / §۳۸.۳ — INV-01..16@32، INV-19، CHK-18
--
--  ⛔ `stock_moves` فقط افزوده می‌شود: نه UPDATE، نه DELETE. اصلاح = حرکتِ
--  جبرانی با دلیل و کاربر. هر حرکت کلیدِ یکتایی دارد (`source_key`)، پس
--  همگام‌سازیِ دوباره یا تکرارِ درخواست اثرِ دوم نمی‌گذارد (INV-07، INV-15).
--  ⛔ نوع‌ها همان نوع‌های `license/stock-core.js` (بخشِ ۳.۶) است و هر
--  تصمیم (کمبود، موجودیِ منفی، وظیفهٔ بررسی) با همان هسته گرفته می‌شود.
--  ⛔ `stock_levels` مدلِ خواندنی است: همیشه برابر با جمعِ `delta_on_hand_milli`
--  همان کالا/مکان (آزمونِ تطبیقِ INV-16).
--  ⛔ رزرو در سطحِ کالا (و شعبه) است، نه مکان: «قابل‌فروش» = جمعِ موجودیِ
--  مکان‌های قابل‌فروش − رزروِ فعال (۳.۶). خراب و در‌راه هرگز قابل‌فروش نیستند.
--  ⛔ تا پرچمِ `shop.catalog` روشن نشود هیچ ردیفی نوشته نمی‌شود.
-- ============================================================

CREATE TABLE IF NOT EXISTS stock_moves (
  id                    text    PRIMARY KEY,               -- ULID
  shop_id               text    NOT NULL REFERENCES shops(id) ON DELETE CASCADE,
  product_id            text    NOT NULL,
  --  '' = شعبهٔ اصلی (چند شعبه: §۳۸.۳)
  branch_id             text    NOT NULL DEFAULT '',
  --  رزرو/آزادسازی در سطحِ کالاست ⇒ مکانِ ''
  location              text    NOT NULL CHECK (location IN ('','shop_floor','warehouse','damaged','in_transit')),
  type                  text    NOT NULL CHECK (type IN (
                          'purchase_receipt','sale','customer_return','supplier_return','adjustment',
                          'count_adjust','damage','expired','transfer_out','transfer_in','reserve','release')),
  --  همان qtyMilliِ سندِ stock-core (برای dirِ صفر علامت‌دار، وگرنه مثبت)
  qty_milli             bigint  NOT NULL,
  delta_on_hand_milli   bigint  NOT NULL DEFAULT 0,
  delta_reserved_milli  bigint  NOT NULL DEFAULT 0,
  --  local: سرور می‌داند و کمبود را رد می‌کند · recorded: سندِ ثبت‌شدهٔ دستگاه، هرگز رد نمی‌شود (INV-19)
  mode                  text    NOT NULL CHECK (mode IN ('local','recorded')),
  source                text    NOT NULL CHECK (source IN ('device','order','staff','system')),
  source_key            text    NOT NULL,
  ref_type              text    NOT NULL DEFAULT '',
  ref_id                text    NOT NULL DEFAULT '',
  actor_type            text    NOT NULL CHECK (actor_type IN ('user','system','customer','device')),
  actor_id              text    NOT NULL DEFAULT '',
  reason                text    NOT NULL DEFAULT '',
  note                  text    NOT NULL DEFAULT '',
  --  زمانِ رویداد (برای سندِ دستگاه: زمانِ ثبت در دستگاه) و زمانِ رسیدن به سرور
  at                    bigint  NOT NULL,
  created_at            bigint  NOT NULL,
  UNIQUE (shop_id, source_key)
);
CREATE INDEX IF NOT EXISTS ix_stock_moves_product ON stock_moves(shop_id, product_id, created_at, id);
CREATE INDEX IF NOT EXISTS ix_stock_moves_shop ON stock_moves(shop_id, created_at, id);

CREATE TABLE IF NOT EXISTS stock_levels (
  shop_id         text    NOT NULL REFERENCES shops(id) ON DELETE CASCADE,
  product_id      text    NOT NULL,
  branch_id       text    NOT NULL DEFAULT '',
  location        text    NOT NULL CHECK (location IN ('shop_floor','warehouse','damaged','in_transit')),
  on_hand_milli   bigint  NOT NULL DEFAULT 0,
  --  آستانهٔ «موجودیِ کم» برای همین مکان؛ null ⇒ آستانهٔ کالا
  low_milli       bigint  CHECK (low_milli IS NULL OR low_milli >= 0),
  updated_at      bigint  NOT NULL,
  PRIMARY KEY (shop_id, product_id, branch_id, location)
);

CREATE TABLE IF NOT EXISTS stock_reservations (
  id              text    PRIMARY KEY,                     -- ULID
  shop_id         text    NOT NULL REFERENCES shops(id) ON DELETE CASCADE,
  order_id        text    NOT NULL,
  line_no         integer NOT NULL,
  product_id      text    NOT NULL,
  branch_id       text    NOT NULL DEFAULT '',
  --  به واحدِ موجودی (پس از ضریبِ تبدیل)
  qty_milli       bigint  NOT NULL CHECK (qty_milli > 0),
  status          text    NOT NULL DEFAULT 'held' CHECK (status IN ('held','released','committed','expired')),
  --  null ⇒ تا پایانِ سفارش (پس از قبول)
  expires_at      bigint,
  created_at      bigint  NOT NULL,
  closed_at       bigint,
  close_reason    text    NOT NULL DEFAULT ''
);
--  هر قلمِ سفارش در هر لحظه فقط یک رزروِ فعال دارد
CREATE UNIQUE INDEX IF NOT EXISTS ux_stock_reservations_held
  ON stock_reservations(shop_id, order_id, line_no) WHERE status = 'held';
CREATE INDEX IF NOT EXISTS ix_stock_reservations_product
  ON stock_reservations(shop_id, product_id, branch_id) WHERE status = 'held';
CREATE INDEX IF NOT EXISTS ix_stock_reservations_expiry
  ON stock_reservations(expires_at) WHERE status = 'held' AND expires_at IS NOT NULL;
CREATE INDEX IF NOT EXISTS ix_stock_reservations_order ON stock_reservations(shop_id, order_id);

--  سهمِ هر سندِ دستگاه که تا حالا در دفتر نشسته — همگام‌سازیِ دوباره فقط
--  «تفاوت» را می‌نویسد، پس یک فروش هرگز دو بار کم نمی‌شود (INV-07)
CREATE TABLE IF NOT EXISTS stock_sources (
  shop_id         text    NOT NULL REFERENCES shops(id) ON DELETE CASCADE,
  source_key      text    NOT NULL,
  product_id      text    NOT NULL,
  posted_milli    bigint  NOT NULL DEFAULT 0,
  rev             integer NOT NULL DEFAULT 0,
  updated_at      bigint  NOT NULL,
  PRIMARY KEY (shop_id, source_key, product_id)
);
