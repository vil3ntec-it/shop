-- ============================================================
--  895 — CRM-15/SEC-10 (د۱۸): «فرمانِ ناشناس کردن» ِ دفترِ دستگاه پس از حذفِ دادهٔ مشتری.
--  حذفِ سرور (lib/customer-erasure.js) پرونده‌های قرض‌دارِ همان شماره را در دفترِ دستگاه دست نمی‌زد. با پرچمِ خاموشِ
--  `shop.customer_erasure_ledger` هر پرونده یک ردیف این‌جا می‌گیرد؛ برنامهٔ صاحب/مدیر پس از همگام‌سازی نام/شماره/نشانی/
--  یادداشتِ همان پرونده را در دفترش پاک می‌کند (op ِ عادی ⇒ همهٔ دستگاه‌ها) و سرور فقط وقتی «انجام شد» می‌پذیرد که دفترِ
--  سرور (sync_rows/sync_v3_rows) دیگر شماره‌ای برای آن پرونده ندارد. بی دادهٔ شخصی: فقط شناسهٔ پرونده و شناسهٔ حذف.
-- ============================================================
CREATE TABLE IF NOT EXISTS customer_erasure_ledger (
  shop_id     TEXT   NOT NULL,
  debtor_id   TEXT   NOT NULL,
  erasure_id  TEXT   NOT NULL,
  created_at  BIGINT NOT NULL,
  applied_at  BIGINT,
  PRIMARY KEY (shop_id, debtor_id)
);
CREATE INDEX IF NOT EXISTS ix_customer_erasure_ledger_pending ON customer_erasure_ledger(shop_id) WHERE applied_at IS NULL;
