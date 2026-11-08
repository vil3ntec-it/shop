-- ============================================================
--  ۳۸۰ — «سابقهٔ دسترسی»: چه کسی پروندهٔ یک مشتری را دید (CRM-15، §۶.۷؛ `lib/customer-access.js`).
--
--  هر ردیف = یک بیننده (صاحب یا کارمندِ همین دکان) × یک مشتری × یک نوع (`detail` پرونده، `phone` شماره/نشانیِ
--  کامل، `export` خروجی) در پنجرهٔ ۱۰ دقیقه از نخستین دیدن؛ دیدنِ دوباره در همان پنجره فقط `hits` و `last_at`
--  را بالا می‌برد. `door` درِ سرور است (`debtor` ⇐ `GET /api/debtors/:id`، `orders` ⇐ سفارش‌های یک مشتری).
--  ⛔ بی دادهٔ شخصیِ مشتری: `customer_ref` شناسهٔ پرونده است، یا `ph:` + HMAC ِ شماره (رازِ سرور) برای خواندنِ
--  با شماره، یا پس از حذفِ SEC-10 `erased:<شناسهٔ حذف>`.
--  ⛔ فقط‌افزودنی: تریگر هر ویرایشی جز بالا رفتنِ `hits`/`last_at` و ناشناس شدنِ `customer_ref` را رد می‌کند؛
--  پاک شدن فقط با نگه‌داری (`CUSTOMER_ACCESS_RETENTION_DAYS`، ۰ = سرور پاک نمی‌کند) یا پاک شدنِ دکان.
--
--  rollback: migrations/rollback/380_customer_access_log.down.sql
-- ============================================================

CREATE TABLE IF NOT EXISTS customer_access_log (
  id            text     PRIMARY KEY,
  shop_id       text     NOT NULL REFERENCES shops(id) ON DELETE CASCADE,
  viewer_id     text     NOT NULL CHECK (char_length(viewer_id) BETWEEN 1 AND 80),
  role          text     NOT NULL DEFAULT '' CHECK (char_length(role) <= 32),
  device_id     text     NOT NULL DEFAULT '' CHECK (char_length(device_id) <= 80),
  customer_ref  text     NOT NULL CHECK (char_length(customer_ref) BETWEEN 1 AND 120),
  kind          text     NOT NULL CHECK (kind IN ('detail','phone','export')),
  door          text     NOT NULL CHECK (door IN ('debtor','orders')),
  first_at      bigint   NOT NULL,
  last_at       bigint   NOT NULL,
  hits          integer  NOT NULL DEFAULT 1 CHECK (hits >= 1)
);
CREATE INDEX IF NOT EXISTS ix_customer_access_customer ON customer_access_log(shop_id, customer_ref, last_at DESC);
CREATE INDEX IF NOT EXISTS ix_customer_access_window ON customer_access_log(shop_id, viewer_id, customer_ref, kind, first_at DESC);
CREATE INDEX IF NOT EXISTS ix_customer_access_age ON customer_access_log(last_at);

CREATE OR REPLACE FUNCTION customer_access_log_guard() RETURNS trigger
LANGUAGE plpgsql AS $$
BEGIN
  IF NEW.id <> OLD.id OR NEW.shop_id <> OLD.shop_id OR NEW.viewer_id <> OLD.viewer_id OR NEW.role <> OLD.role
     OR NEW.device_id <> OLD.device_id OR NEW.kind <> OLD.kind OR NEW.door <> OLD.door OR NEW.first_at <> OLD.first_at
     OR NEW.hits < OLD.hits OR NEW.last_at < OLD.last_at
     OR (NEW.customer_ref <> OLD.customer_ref AND NEW.customer_ref NOT LIKE 'erased:%') THEN
    RAISE EXCEPTION 'customer_access_log is append-only (only hits/last_at grow; customer_ref only becomes erased:)';
  END IF;
  RETURN NEW;
END;
$$;

DROP TRIGGER IF EXISTS customer_access_log_append_only ON customer_access_log;
CREATE TRIGGER customer_access_log_append_only
  BEFORE UPDATE ON customer_access_log
  FOR EACH ROW EXECUTE FUNCTION customer_access_log_guard();
