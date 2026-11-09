-- ============================================================
--  ۳۲۶ — نمایهٔ حلقه‌های لغوِ فاکتور (SEC-03؛ ۱۴۰۵/۰۸/۱۹)
--
--  هر لغوِ فاکتور با پرچمِ `shop.doc_chain` یک حلقه در زنجیرهٔ فقط‌افزودنیِ `hash_chain` (`void/<دکان>`) می‌شود
--  (`lib/finalized-sales.js`). «یک فاکتور ⇐ یک حلقه» با همین جدول سنجیده می‌شود، نه با گشتنِ متنِ همهٔ حلقه‌ها (که
--  با هزاران لغو هر push را کُند می‌کرد): کلید = دکان + انبار (`v1` = `sync_rows`، `rest` = راهِ قدیم/API) + شناسهٔ
--  فاکتور؛ `seq` همان حلقه در زنجیره.
--  ⛔ خودِ سند در `hash_chain` است (فقط‌افزودنی، سنجیده با `npm run verify-chain`)؛ این جدول فقط نمایه است — ردیفِ
--     پاک‌شده فقط یعنی لغوِ دوبارهٔ همان فاکتور حلقهٔ دوم می‌گیرد، هرگز سندی گم نمی‌شود.
--  ⛔ پرچمِ خاموش ⇒ هیچ ردیفی. فقط افزودنی. rollback: migrations/rollback/326_sale_void_index.down.sql
-- ============================================================

CREATE TABLE IF NOT EXISTS sale_void_index (
  shop_id     text    NOT NULL REFERENCES shops(id) ON DELETE CASCADE,
  store       text    NOT NULL CHECK (store IN ('v1', 'rest')),
  sale_id     text    NOT NULL CHECK (length(sale_id) BETWEEN 1 AND 200),
  seq         bigint  NOT NULL CHECK (seq >= 1),
  created_at  bigint  NOT NULL,
  PRIMARY KEY (shop_id, store, sale_id)
);
