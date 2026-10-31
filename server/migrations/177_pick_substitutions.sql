-- ============================================================
--  جایگزینی هنگامِ جمع‌آوری — PCK-05، PCK-06 (v3 §۶.۱۷، سناریوی S37)
--
--  ⛔ پشتِ همان پرچمِ خاموشِ `shop.orders.picking` (`lib/pick-substitutes.js`): تا روشن نشود
--  هیچ ردیفی در این دو جدول نوشته نمی‌شود و هیچ رفتاری عوض نمی‌شود.
--
--  pick_product_attrs        ویژگی‌های کالا برای جایگزینِ امن (PCK-06): امروز فقط «حلال».
--                            '' = نامعلوم (با حلال یکی نیست). دسته و زیردسته از دفترِ کالای دکان.
--  order_pick_substitutions  هر پیشنهادِ جایگزین برای یک قلمِ «پیدا نشد»: کالای جایگزین، قیمتِ قفسه،
--                            قیمتِ کمتری که مشتری می‌پردازد (PCK-06)، ترجیحِ ذخیره‌شدهٔ مشتری در لحظهٔ
--                            پیشنهاد، مهلت و تصمیم (مشتری با لینک، کارمند پس از تماس/واتس‌اپ، یا
--                            ترجیح پس از مهلت — PCK-05). هر قلم حداکثر یک پیشنهادِ باز یا پذیرفته.
--                            پیشنهادِ ردشده/پس‌گرفته می‌ماند (سابقه).
--
--  فقط افزودنی: هیچ جدولِ موجودی عوض نمی‌شود (`order_pick_lines.substitute`/`status='substituted'`
--  از ۱۴۲ همین‌جا به کار می‌آیند).
--  برگشت: DROP TABLE order_pick_substitutions, pick_product_attrs. برگه‌ها و سفارش‌ها می‌مانند.
-- ============================================================

CREATE TABLE IF NOT EXISTS pick_product_attrs (
  shop_id               text    NOT NULL REFERENCES shops(id) ON DELETE CASCADE,
  product_id            text    NOT NULL,
  halal                 text    NOT NULL DEFAULT '' CHECK (halal IN ('','halal','non_halal')),
  updated_by            text    NOT NULL DEFAULT '',
  updated_at            bigint  NOT NULL,
  PRIMARY KEY (shop_id, product_id)
);

CREATE TABLE IF NOT EXISTS order_pick_substitutions (
  id                    text    PRIMARY KEY,                   -- ULID
  order_id              text    NOT NULL REFERENCES order_picks(order_id) ON DELETE CASCADE,
  shop_id               text    NOT NULL,
  line_no               integer NOT NULL,
  --  کالای جایگزین (همان دکان) و آن‌چه مشتری می‌بیند
  product_id            text    NOT NULL,
  name                  text    NOT NULL DEFAULT '',
  unit                  text    NOT NULL DEFAULT '',
  qty_milli             bigint  NOT NULL CHECK (qty_milli > 0),
  shelf_unit_price_minor   bigint NOT NULL CHECK (shelf_unit_price_minor > 0),
  --  PCK-06: کمترِ قیمتِ واحدِ سفارش و جایگزین
  charged_unit_price_minor bigint NOT NULL CHECK (charged_unit_price_minor >= 0),
  total_minor           bigint  NOT NULL CHECK (total_minor >= 0),
  --  ترجیحِ قلم (CHK-15) در لحظهٔ پیشنهاد — پس از مهلت همین اعمال می‌شود
  preference            text    NOT NULL DEFAULT '' CHECK (preference IN ('','best_match','specific','none','refund')),
  status                text    NOT NULL DEFAULT 'pending' CHECK (status IN ('pending','accepted','rejected','withdrawn')),
  decided_by            text    NOT NULL DEFAULT '' CHECK (decided_by IN ('','customer','staff','timeout','picker')),
  decided_actor         text    NOT NULL DEFAULT '',
  decided_at            bigint,
  deadline_at           bigint  NOT NULL,
  --  کلیدِ یکتای دستگاهِ جمع‌آور (صفِ آفلاین بی‌خطر دوباره می‌فرستد)
  event_key             text    NOT NULL,
  request_hash          text    NOT NULL,
  barcode               text    NOT NULL DEFAULT '',
  proposed_by           text    NOT NULL DEFAULT '',
  created_at            bigint  NOT NULL,
  updated_at            bigint  NOT NULL,
  UNIQUE (shop_id, event_key)
);
CREATE INDEX IF NOT EXISTS ix_pick_subs_order ON order_pick_substitutions(order_id, line_no, created_at);
--  هر قلم حداکثر یک پیشنهادِ «در انتظار» یا «پذیرفته»
CREATE UNIQUE INDEX IF NOT EXISTS ux_pick_subs_live ON order_pick_substitutions(order_id, line_no)
  WHERE status IN ('pending','accepted');
