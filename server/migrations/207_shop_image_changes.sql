-- ============================================================
--  ۲۰۷ — IMG-PRO-17 (§۳۶.۱۲): تغییرِ دسته‌ایِ عکسِ کالاهای دکان و برگرداندنِ همهٔ آن یک‌جا.
--
--  هر «دسته» چند انتخابِ عکس (همان قاعدهٔ `image-bank.select`) است که در یک تراکنش نشسته‌اند؛ برای هر کالا وضعِ
--  پیش و پسِ ردیفِ `shop_product_images` نگه داشته می‌شود تا «برگرداندن» دقیقاً همان وضعِ پیشین را بنشاند.
--  پشتِ همان پرچمِ خاموشِ `shop.image_bank`؛ بی پرچم هیچ ردیفی این‌جا نوشته نمی‌شود.
--  rollback: migrations/rollback/207_shop_image_changes.down.sql
-- ============================================================

CREATE TABLE IF NOT EXISTS shop_image_changes (
  id               text    PRIMARY KEY,
  shop_id          text    NOT NULL REFERENCES shops(id) ON DELETE CASCADE,
  client_key       text    NOT NULL CHECK (char_length(client_key) BETWEEN 1 AND 80),   -- کلیدِ تکرارِ همان کار
  request_digest   text    NOT NULL,                                                   -- sha256 ِ قلم‌ها: کلیدِ تکراری با قلمِ دیگر ⇒ ۴۰۹
  note             text    NOT NULL DEFAULT '' CHECK (char_length(note) <= 200),
  status           text    NOT NULL DEFAULT 'applied' CHECK (status IN ('applied', 'rolled_back')),
  item_count       integer NOT NULL CHECK (item_count BETWEEN 1 AND 100),
  created_by       text    NOT NULL DEFAULT '',
  created_at       bigint  NOT NULL,
  rolled_back_by   text    NOT NULL DEFAULT '',
  rolled_back_at   bigint,
  rollback_reason  text    NOT NULL DEFAULT '' CHECK (char_length(rollback_reason) <= 200),
  UNIQUE (shop_id, client_key),
  CHECK ((status = 'applied') = (rolled_back_at IS NULL))
);
CREATE INDEX IF NOT EXISTS ix_shop_image_changes_shop ON shop_image_changes (shop_id, created_at DESC);

CREATE TABLE IF NOT EXISTS shop_image_change_items (
  change_id   text    NOT NULL REFERENCES shop_image_changes(id) ON DELETE CASCADE,
  shop_id     text    NOT NULL REFERENCES shops(id) ON DELETE CASCADE,
  seq         integer NOT NULL CHECK (seq >= 0),
  product_id  text    NOT NULL,
  before_row  text,             -- JSON ِ انتخابِ پیشین؛ NULL = کالا ردیفی نداشت (عکسِ همگام‌شده یا «بی عکس»)
  after_row   text    NOT NULL, -- JSON ِ انتخابی که همین دسته نشاند
  PRIMARY KEY (change_id, product_id),
  UNIQUE (change_id, seq)
);
