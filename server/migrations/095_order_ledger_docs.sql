-- ============================================================
--  سندِ فروشِ سریِ W برای سفارشِ آنلاینِ تحویل‌شده — ORD-20، v3 §۳۸.۲
--  (و §۲۲.۲ مرحلهٔ A بندِ ۶).
--
--  هر سفارشِ تحویل‌شده (picked_up / delivered) یک فاکتورِ فروشِ سریِ W
--  در دفترِ خودِ دکان می‌گیرد — همان ردیف‌های `sales` / `saleItems` /
--  `stockMovements` / `auditLog` که دستگاه برای فروش می‌نویسد، از راهِ
--  دفترِ Sync v1 (`oplog` + `sync_rows`، app = shop). این جدول کارنامهٔ
--  سرور از همان سند است: شماره، کلیدِ یکتایی، و حلقهٔ زنجیرهٔ هشِ سری.
--
--  ⛔ پیش‌فرض خاموش: تا پرچمِ `shop.orders.ledger` در «تنظیماتِ زنده»
--     روشن نشود هیچ ردیفی در این جدول یا دفترِ همگام‌سازی نوشته نمی‌شود.
--  ⛔ هر سفارش حداکثر یک سندِ فروش و یک سندِ برگشت دارد: (order_id, kind).
--  ⛔ سندِ ثبت‌شده ویرایش نمی‌شود؛ برگشت/بازپرداخت سندِ برگشتِ جدا است.
--     پاک شدن فقط با پاک شدنِ خودِ دکان (cascade).
--  ⛔ شمارهٔ فاکتورهای W پشتِ سرِ هم است (W-1، W-2، …)؛ سندِ برگشت
--     شمارهٔ جدای خودش را دارد تا در شمارهٔ فاکتورها جای خالی نیفتد.
-- ============================================================

CREATE TABLE IF NOT EXISTS order_ledger_docs (
  id             text    PRIMARY KEY,                     -- ULID
  shop_id        text    NOT NULL REFERENCES shops(id) ON DELETE CASCADE,
  order_id       text    NOT NULL,
  kind           text    NOT NULL CHECK (kind IN ('sale','return')),
  series         text    NOT NULL DEFAULT 'W' CHECK (series = 'W'),
  doc_no         bigint  NOT NULL CHECK (doc_no >= 1),
  --  شناسهٔ ردیفِ `sales` در دفترِ دستگاه (برای برگشت هم همان فاکتور)
  sale_row_id    text    NOT NULL,
  --  وضعیتِ سفارش که سند را ساخت: picked_up | delivered | refunded_full
  on_status      text    NOT NULL,
  total_minor    bigint  NOT NULL CHECK (total_minor >= 0),
  lines          jsonb   NOT NULL DEFAULT '[]'::jsonb,
  --  opهای نوشته‌شده در `oplog` و بازهٔ server_seqِ آن‌ها
  ops            integer NOT NULL DEFAULT 0,
  first_seq      bigint,
  last_seq       bigint,
  --  حلقهٔ زنجیرهٔ هشِ سری (`hash_chain`، migration 070)
  chain          text    NOT NULL,
  chain_seq      bigint  NOT NULL,
  hash           text    NOT NULL,
  actor_type     text    NOT NULL DEFAULT '',
  actor_id       text    NOT NULL DEFAULT '',
  created_at     bigint  NOT NULL,
  UNIQUE (order_id, kind),
  UNIQUE (shop_id, kind, doc_no)
);
CREATE INDEX IF NOT EXISTS ix_order_ledger_docs_shop ON order_ledger_docs(shop_id, created_at, id);

--  سندِ ثبت‌شده فقط با سندِ برگشت جبران می‌شود، نه ویرایش
CREATE OR REPLACE FUNCTION order_ledger_docs_refuse_update() RETURNS trigger
LANGUAGE plpgsql AS $$
BEGIN
  RAISE EXCEPTION 'order_ledger_docs is append-only (UPDATE refused)';
END;
$$;

DROP TRIGGER IF EXISTS order_ledger_docs_no_update ON order_ledger_docs;
CREATE TRIGGER order_ledger_docs_no_update
  BEFORE UPDATE ON order_ledger_docs
  FOR EACH ROW EXECUTE FUNCTION order_ledger_docs_refuse_update();
