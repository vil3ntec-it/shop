-- ============================================================
--  شعبه درونِ یک دکان — فازِ ۳ب ِ سرور (`docs/BRANCHES-fa.md` §۹؛ پشتِ پرچمِ خاموشِ `shop.branches`).
--
--  درهای مانده شعبه‌شناس می‌شوند: نگه‌داشته، سفارشِ خرید/رسید/صورت‌حساب، نوبت‌های صندوق و «کاربرِ سنجیدهٔ صندوق» (S46)،
--  و خبرها. هر ردیفِ امروز ⇐ شعبهٔ اصلی (`branch_id = ''`، پیش‌فرضِ ستون) ⇒ دادهٔ امروز بایت‌به‌بایت همان، بی کوچ.
--  ⛔ کلیدهای یکتا شعبه را هم دارند: شناسهٔ دستگاه‌ساخته (نگه‌داشته، رسید، صورت‌حساب، op) یا شمارهٔ پیاپی (`po_no`) در
--     شعبهٔ دیگر هرگز با ردیفِ «ب» برخورد نمی‌کند — نه «تکراری/تعارض» (نشانِ وجود)، نه نوشتن روی آن.
--  ⛔ خبرهای شعبهٔ غیرِ اصلی در جدول‌های جدا (`shop_branch_events`، کلیدِ `<دکان>#<شعبه>`) تا `lib/events.js` ِ مشترک با
--     پمپ و `shop_events` ِ امروز دست نخورند.
--  ⛔ پاک شدنِ ردیفِ شعبه (و با cascade پاک شدنِ دکان) همهٔ این ردیف‌های همان شعبه را هم پاک می‌کند (تابعِ پایین).
--
--  برگشت: rollback/930_branch_doors.down.sql
-- ============================================================

-- ── نگه‌داشته (POS-12) ──────────────────────────────────────────────
ALTER TABLE shop_held_invoices ADD COLUMN IF NOT EXISTS branch_id text NOT NULL DEFAULT ''
  CHECK (branch_id = '' OR branch_id ~ '^brn_[0-9a-f]{24}$');
ALTER TABLE shop_held_invoices DROP CONSTRAINT IF EXISTS shop_held_invoices_pkey;
ALTER TABLE shop_held_invoices ADD PRIMARY KEY (shop_id, branch_id, id);
DROP INDEX IF EXISTS ix_shop_held_invoices_rev;
CREATE INDEX IF NOT EXISTS ix_shop_held_invoices_rev ON shop_held_invoices (shop_id, branch_id, rev);
DROP INDEX IF EXISTS ix_shop_held_invoices_live;
CREATE INDEX IF NOT EXISTS ix_shop_held_invoices_live ON shop_held_invoices (shop_id, branch_id) WHERE NOT deleted;

-- ── سفارشِ خرید، رسید و صورت‌حساب (PUR-02/04/14) ───────────────────
--  کلیدِ بیگانهٔ رسید/صورت‌حساب به کلیدِ اصلیِ سفارش بسته است ⇒ اول برداشته، سپس با شعبه دوباره
DO $$
DECLARE r record;
BEGIN
  FOR r IN SELECT conrelid::regclass AS t, conname FROM pg_constraint
            WHERE confrelid = 'purchase_orders'::regclass AND contype = 'f'
  LOOP EXECUTE format('ALTER TABLE %s DROP CONSTRAINT %I', r.t, r.conname); END LOOP;
  FOR r IN SELECT conname FROM pg_constraint WHERE conrelid = 'purchase_orders'::regclass AND contype = 'u'
  LOOP EXECUTE format('ALTER TABLE purchase_orders DROP CONSTRAINT %I', r.conname); END LOOP;
END $$;
ALTER TABLE purchase_orders ADD COLUMN IF NOT EXISTS branch_id text NOT NULL DEFAULT ''
  CHECK (branch_id = '' OR branch_id ~ '^brn_[0-9a-f]{24}$');
ALTER TABLE purchase_receipts ADD COLUMN IF NOT EXISTS branch_id text NOT NULL DEFAULT '';
ALTER TABLE purchase_bills ADD COLUMN IF NOT EXISTS branch_id text NOT NULL DEFAULT '';
ALTER TABLE purchase_orders DROP CONSTRAINT IF EXISTS purchase_orders_pkey;
ALTER TABLE purchase_orders ADD PRIMARY KEY (shop_id, branch_id, id);
ALTER TABLE purchase_orders ADD CONSTRAINT ux_purchase_orders_no UNIQUE (shop_id, branch_id, po_no);
ALTER TABLE purchase_orders ADD CONSTRAINT ux_purchase_orders_key UNIQUE (shop_id, branch_id, client_key);
DROP INDEX IF EXISTS ix_purchase_orders_recent;
CREATE INDEX IF NOT EXISTS ix_purchase_orders_recent ON purchase_orders (shop_id, branch_id, updated_at DESC, id);

ALTER TABLE purchase_receipts DROP CONSTRAINT IF EXISTS purchase_receipts_pkey;
ALTER TABLE purchase_receipts ADD PRIMARY KEY (shop_id, branch_id, id);
ALTER TABLE purchase_receipts ADD CONSTRAINT fk_purchase_receipts_po
  FOREIGN KEY (shop_id, branch_id, po_id) REFERENCES purchase_orders (shop_id, branch_id, id) ON DELETE CASCADE;
DROP INDEX IF EXISTS ix_purchase_receipts_po;
CREATE INDEX IF NOT EXISTS ix_purchase_receipts_po ON purchase_receipts (shop_id, branch_id, po_id, created_at);

ALTER TABLE purchase_bills DROP CONSTRAINT IF EXISTS purchase_bills_pkey;
ALTER TABLE purchase_bills ADD PRIMARY KEY (shop_id, branch_id, id);
ALTER TABLE purchase_bills ADD CONSTRAINT fk_purchase_bills_po
  FOREIGN KEY (shop_id, branch_id, po_id) REFERENCES purchase_orders (shop_id, branch_id, id) ON DELETE CASCADE;
DROP INDEX IF EXISTS ix_purchase_bills_po;
CREATE INDEX IF NOT EXISTS ix_purchase_bills_po ON purchase_bills (shop_id, branch_id, po_id, created_at);
DROP INDEX IF EXISTS ux_purchase_bills_no;
CREATE UNIQUE INDEX IF NOT EXISTS ux_purchase_bills_no ON purchase_bills (shop_id, branch_id, po_id, bill_no) WHERE voided_at IS NULL;

-- ── نوبتِ صندوق (STF-04) و «کاربرِ سنجیدهٔ صندوق» (S46) ─────────────
--  PIN ِ هر عضو سطحِ دکان می‌ماند (`shop_till_pins`: هویتِ عضو)؛ نوبت مالِ شعبه‌ای است که دستگاه در آن صندوق باز کرد.
--  یک نوبتِ باز برای هر (دستگاه، شعبه): رفتنِ دستگاه به شعبهٔ دیگر نوبتِ شعبهٔ پیشین را نمی‌نویسد.
ALTER TABLE shop_till_sessions ADD COLUMN IF NOT EXISTS branch_id text NOT NULL DEFAULT ''
  CHECK (branch_id = '' OR branch_id ~ '^brn_[0-9a-f]{24}$');
DROP INDEX IF EXISTS ux_shop_till_sessions_open;
CREATE UNIQUE INDEX IF NOT EXISTS ux_shop_till_sessions_open
  ON shop_till_sessions (shop_id, branch_id, device_id) WHERE ended_at IS NULL;
CREATE INDEX IF NOT EXISTS ix_shop_till_sessions_branch ON shop_till_sessions (shop_id, branch_id, started_at);

ALTER TABLE shop_till_op_users ADD COLUMN IF NOT EXISTS branch_id text NOT NULL DEFAULT ''
  CHECK (branch_id = '' OR branch_id ~ '^brn_[0-9a-f]{24}$');
ALTER TABLE shop_till_op_users DROP CONSTRAINT IF EXISTS shop_till_op_users_pkey;
ALTER TABLE shop_till_op_users ADD PRIMARY KEY (shop_id, branch_id, op_id);

-- ── خبرهای شعبهٔ غیرِ اصلی (همان منطقِ `lib/events.js` روی این دو جدول؛ `lib/branch-events.js`) ──
CREATE TABLE IF NOT EXISTS shop_branch_events (
  id          text   PRIMARY KEY,
  branch_key  text   NOT NULL CHECK (branch_key ~ '^[A-Za-z0-9_-]{1,64}#brn_[0-9a-f]{24}$'),
  user_id     text   NOT NULL DEFAULT '',
  user_name   text   NOT NULL DEFAULT '',
  kind        text   NOT NULL,
  title       text   NOT NULL DEFAULT '',
  body        text   NOT NULL DEFAULT '',
  data        jsonb  NOT NULL DEFAULT '{}'::jsonb,
  client_id   text   NOT NULL DEFAULT '',
  created_at  bigint NOT NULL
);
CREATE INDEX IF NOT EXISTS ix_shop_branch_events_key ON shop_branch_events (branch_key, created_at DESC);
CREATE UNIQUE INDEX IF NOT EXISTS ux_shop_branch_events_client
  ON shop_branch_events (branch_key, client_id) WHERE client_id <> '';
CREATE TABLE IF NOT EXISTS shop_branch_event_reads (
  branch_key  text   NOT NULL CHECK (branch_key ~ '^[A-Za-z0-9_-]{1,64}#brn_[0-9a-f]{24}$'),
  user_id     text   NOT NULL,
  seen_at     bigint NOT NULL DEFAULT 0,
  PRIMARY KEY (branch_key, user_id)
);

-- ── پاک‌سازی: همان ۹۰۰ + همین درها ─────────────────────────────────
CREATE OR REPLACE FUNCTION shop_branch_purge() RETURNS trigger AS $$
DECLARE k text;
BEGIN
  IF OLD.id = '' THEN RETURN OLD; END IF;
  k := OLD.shop_id || '#' || OLD.id;
  DELETE FROM sync_v3_ops WHERE account_id = k;
  DELETE FROM sync_v3_rows WHERE account_id = k;
  DELETE FROM sync_v3_conflicts WHERE account_id = k;
  DELETE FROM sync_v3_devices WHERE account_id = k;
  DELETE FROM sync_v3_bridge WHERE account_id = k;
  DELETE FROM sync_v3_v1_out WHERE account_id = k;
  DELETE FROM sync_v3_v1_state WHERE account_id = k;
  DELETE FROM oplog WHERE account_id = k;
  DELETE FROM sync_rows WHERE account_id = k;
  DELETE FROM sync_conflicts WHERE account_id = k;
  DELETE FROM sync_devices WHERE account_id = k;
  DELETE FROM sync_op_authors WHERE account_id = k;
  DELETE FROM shop_member_branches WHERE shop_id = OLD.shop_id AND branch_id = OLD.id;
  DELETE FROM stock_moves WHERE shop_id = OLD.shop_id AND branch_id = OLD.id;
  DELETE FROM stock_levels WHERE shop_id = OLD.shop_id AND branch_id = OLD.id;
  DELETE FROM stock_tasks WHERE shop_id = OLD.shop_id AND branch_id = OLD.id;
  DELETE FROM stock_sources WHERE shop_id = OLD.shop_id AND left(source_key, length(OLD.id) + 1) = OLD.id || '|';
  DELETE FROM shop_held_invoices WHERE shop_id = OLD.shop_id AND branch_id = OLD.id;
  DELETE FROM purchase_orders WHERE shop_id = OLD.shop_id AND branch_id = OLD.id;
  DELETE FROM shop_till_sessions WHERE shop_id = OLD.shop_id AND branch_id = OLD.id;
  DELETE FROM shop_till_op_users WHERE shop_id = OLD.shop_id AND branch_id = OLD.id;
  DELETE FROM shop_branch_events WHERE branch_key = k;
  DELETE FROM shop_branch_event_reads WHERE branch_key = k;
  RETURN OLD;
END $$ LANGUAGE plpgsql;
