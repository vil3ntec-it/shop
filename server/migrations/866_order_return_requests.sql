-- ============================================================
--  ۸۶۶ — درخواستِ برگشتِ مشتری از ویترین (RET-11، تصمیمِ ۱۳ «ب» ِ صاحب‌کار، ۱۴۰۵/۰۸/۲۳)
--
--  ⛔ پشتِ پرچمِ خاموشِ `shop.storefront.return_requests` (`lib/order-return-requests.js`): تا روشن نشود هیچ ردیفی
--  نوشته نمی‌شود. پول و موجودی هرگز این‌جا عوض نمی‌شوند: پذیرفتن در برنامهٔ دکان همان سندِ برگشتِ موجود
--  (`ReturnFlow.post`، شناسهٔ ثابتِ `o-<سفارش>-rq-<درخواست>`) را می‌نویسد و این ردیف فقط شناسهٔ همان سند را نگه می‌دارد.
--
--  order_return_requests        مشتری (نشستِ حساب + توکنِ پیگیریِ همان سفارش)؛ قلم‌ها و تعداد (`lines`)، دلیل، متن،
--                               روشِ رساندن. چرخه: pending ⇒ accepted ⇒ completed، یا pending/accepted ⇒ rejected.
--                               کلیدِ یکتای درخواست: ارسالِ دوباره همان ردیف. `customer_id` = حسابِ ویترین (نه شماره).
--  order_return_request_photos  حداکثر ۳ برای هر درخواست؛ همان راهِ عکسِ گزارشِ مشکل (نوع از محتوا، بی EXIF/GPS)،
--                               بایت در `data` یا انبارِ شیء (`store_key`)؛ فقط دکانِ همان سفارش می‌بیند.
--  customer_notify_outbox       `return_request_id`: پیامِ «پذیرفته/رد/ثبت شد» ِ هر درخواست یک بار — رویدادِ
--                               `order_return_<accepted|rejected|completed>:<درخواست>` با همان UNIQUE (order_id, event) ِ ۱۴۳.
--
--  فقط افزودنی (دو جدولِ تازه، ستونِ NULL‌پذیر، CHECK ِ گسترده‌تر).
--  rollback: migrations/rollback/866_order_return_requests.down.sql
-- ============================================================

CREATE TABLE IF NOT EXISTS order_return_requests (
  id             text    PRIMARY KEY,                       -- ULID
  shop_id        text    NOT NULL REFERENCES shops(id) ON DELETE CASCADE,
  order_id       text    NOT NULL REFERENCES online_orders(id) ON DELETE CASCADE,
  customer_id    text    REFERENCES store_customers(id) ON DELETE SET NULL,
  status         text    NOT NULL DEFAULT 'pending' CHECK (status IN ('pending','accepted','completed','rejected')),
  reason         text    NOT NULL CHECK (reason IN ('damaged','wrong_item','expired','not_as_described','changed_mind','other')),
  body           text    NOT NULL DEFAULT '' CHECK (length(body) <= 2000),
  method         text    NOT NULL CHECK (method IN ('drop_off','courier')),
  lines          jsonb   NOT NULL,                          -- [{lineNo, qtyMilli}]
  request_key    text    NOT NULL,
  request_hash   text    NOT NULL,
  decided_by     text    NOT NULL DEFAULT '',
  decided_at     bigint,
  decision_note  text    NOT NULL DEFAULT '' CHECK (length(decision_note) <= 2000),
  return_doc_id  text    NOT NULL DEFAULT '',
  completed_at   bigint,
  created_at     bigint  NOT NULL,
  updated_at     bigint  NOT NULL,
  UNIQUE (order_id, request_key),
  UNIQUE (id, order_id)
);
CREATE INDEX IF NOT EXISTS ix_order_return_requests_shop ON order_return_requests(shop_id, status, created_at);
CREATE INDEX IF NOT EXISTS ix_order_return_requests_order ON order_return_requests(order_id, created_at);
CREATE INDEX IF NOT EXISTS ix_order_return_requests_customer ON order_return_requests(customer_id, shop_id, created_at)
  WHERE customer_id IS NOT NULL;

CREATE TABLE IF NOT EXISTS order_return_request_photos (
  id          text     PRIMARY KEY,                        -- ULID
  shop_id     text     NOT NULL REFERENCES shops(id) ON DELETE CASCADE,
  request_id  text     NOT NULL REFERENCES order_return_requests(id) ON DELETE CASCADE,
  mime        text     NOT NULL,
  size        integer  NOT NULL,
  width       integer  NOT NULL,
  height      integer  NOT NULL,
  hash        text     NOT NULL,                            -- sha256 ِ بایت‌های رسیده (تکرار ⇒ همان عکس)
  data        bytea,                                        -- بی فراداده؛ یا در انبارِ شیء
  store_key   text     NOT NULL DEFAULT '',
  created_at  bigint   NOT NULL,
  CONSTRAINT order_return_request_photos_bytes CHECK (data IS NOT NULL OR store_key <> ''),
  UNIQUE (request_id, hash)
);
CREATE INDEX IF NOT EXISTS ix_order_return_request_photos_store_key ON order_return_request_photos (store_key) WHERE store_key <> '';

ALTER TABLE customer_notify_outbox ADD COLUMN IF NOT EXISTS return_request_id text;
ALTER TABLE customer_notify_outbox ADD CONSTRAINT customer_notify_outbox_return_fk
  FOREIGN KEY (return_request_id, order_id) REFERENCES order_return_requests(id, order_id) ON DELETE CASCADE;

ALTER TABLE customer_notify_outbox DROP CONSTRAINT IF EXISTS customer_notify_outbox_event_check;
ALTER TABLE customer_notify_outbox ADD CONSTRAINT customer_notify_outbox_event_check CHECK (
  (refund_id IS NULL AND return_request_id IS NULL AND event IN (
    'order_received', 'order_accepted', 'order_rejected', 'order_ready',
    'order_on_the_way', 'order_delivery_failed', 'order_delivery_retry',
    'order_completed', 'order_cancelled', 'order_late_apology'))
  OR (refund_id IS NOT NULL AND return_request_id IS NULL AND event = 'order_refund:' || refund_id)
  OR (return_request_id IS NOT NULL AND refund_id IS NULL AND event IN (
    'order_return_accepted:' || return_request_id,
    'order_return_rejected:' || return_request_id,
    'order_return_completed:' || return_request_id)));
