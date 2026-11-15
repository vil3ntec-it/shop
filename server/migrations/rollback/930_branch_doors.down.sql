-- ============================================================
--  Rollback ِ 930_branch_doors.sql — فقط با دست، پس از برگرداندنِ کد.
--  ⚠️ دادهٔ این درها در شعبه‌های غیرِ اصلی (`branch_id <> ''` و خبرهای `shop_branch_events`) پاک می‌شود — کدِ پیش از ۹۳۰ آن را
--  نمی‌شناسد و کلیدهای یکتای امروز (`(shop_id, id)`، `(shop_id, po_no)`…) بی شعبه با آن برخورد می‌کردند. شعبهٔ اصلی (همهٔ
--  دادهٔ پیش از این مهاجرت: `branch_id = ''`) دست نمی‌خورد و کلیدهایش همان پیشین می‌شوند.
-- ============================================================
DELETE FROM shop_held_invoices WHERE branch_id <> '';
DELETE FROM purchase_bills WHERE branch_id <> '';
DELETE FROM purchase_receipts WHERE branch_id <> '';
DELETE FROM purchase_orders WHERE branch_id <> '';
DELETE FROM shop_till_sessions WHERE branch_id <> '';
DELETE FROM shop_till_op_users WHERE branch_id <> '';
DROP TABLE IF EXISTS shop_branch_event_reads;
DROP TABLE IF EXISTS shop_branch_events;

--  نگه‌داشته (همان ۴۲۵)
ALTER TABLE shop_held_invoices DROP CONSTRAINT IF EXISTS shop_held_invoices_pkey;
ALTER TABLE shop_held_invoices DROP COLUMN IF EXISTS branch_id;
ALTER TABLE shop_held_invoices ADD PRIMARY KEY (shop_id, id);
DROP INDEX IF EXISTS ix_shop_held_invoices_rev;
CREATE INDEX IF NOT EXISTS ix_shop_held_invoices_rev ON shop_held_invoices (shop_id, rev);
DROP INDEX IF EXISTS ix_shop_held_invoices_live;
CREATE INDEX IF NOT EXISTS ix_shop_held_invoices_live ON shop_held_invoices (shop_id) WHERE NOT deleted;

--  سفارشِ خرید، رسید و صورت‌حساب (همان ۱۳۴ و ۲۰۸)
ALTER TABLE purchase_receipts DROP CONSTRAINT IF EXISTS fk_purchase_receipts_po;
ALTER TABLE purchase_bills DROP CONSTRAINT IF EXISTS fk_purchase_bills_po;
DROP INDEX IF EXISTS ux_purchase_bills_no;
DROP INDEX IF EXISTS ix_purchase_bills_po;
DROP INDEX IF EXISTS ix_purchase_receipts_po;
DROP INDEX IF EXISTS ix_purchase_orders_recent;
ALTER TABLE purchase_orders DROP CONSTRAINT IF EXISTS ux_purchase_orders_no;
ALTER TABLE purchase_orders DROP CONSTRAINT IF EXISTS ux_purchase_orders_key;
ALTER TABLE purchase_orders DROP CONSTRAINT IF EXISTS purchase_orders_pkey;
ALTER TABLE purchase_receipts DROP CONSTRAINT IF EXISTS purchase_receipts_pkey;
ALTER TABLE purchase_bills DROP CONSTRAINT IF EXISTS purchase_bills_pkey;
ALTER TABLE purchase_orders DROP COLUMN IF EXISTS branch_id;
ALTER TABLE purchase_receipts DROP COLUMN IF EXISTS branch_id;
ALTER TABLE purchase_bills DROP COLUMN IF EXISTS branch_id;
ALTER TABLE purchase_orders ADD PRIMARY KEY (shop_id, id);
ALTER TABLE purchase_orders ADD UNIQUE (shop_id, po_no);
ALTER TABLE purchase_orders ADD UNIQUE (shop_id, client_key);
CREATE INDEX IF NOT EXISTS ix_purchase_orders_recent ON purchase_orders (shop_id, updated_at DESC, id);
ALTER TABLE purchase_receipts ADD PRIMARY KEY (shop_id, id);
ALTER TABLE purchase_receipts ADD FOREIGN KEY (shop_id, po_id) REFERENCES purchase_orders (shop_id, id) ON DELETE CASCADE;
CREATE INDEX IF NOT EXISTS ix_purchase_receipts_po ON purchase_receipts (shop_id, po_id, created_at);
ALTER TABLE purchase_bills ADD PRIMARY KEY (shop_id, id);
ALTER TABLE purchase_bills ADD FOREIGN KEY (shop_id, po_id) REFERENCES purchase_orders (shop_id, id) ON DELETE CASCADE;
CREATE INDEX IF NOT EXISTS ix_purchase_bills_po ON purchase_bills (shop_id, po_id, created_at);
CREATE UNIQUE INDEX IF NOT EXISTS ux_purchase_bills_no ON purchase_bills (shop_id, po_id, bill_no) WHERE voided_at IS NULL;

--  نوبتِ صندوق و «کاربرِ سنجیدهٔ صندوق» (همان ۶۴۰ و ۷۹۰)
DROP INDEX IF EXISTS ix_shop_till_sessions_branch;
DROP INDEX IF EXISTS ux_shop_till_sessions_open;
ALTER TABLE shop_till_sessions DROP COLUMN IF EXISTS branch_id;
CREATE UNIQUE INDEX IF NOT EXISTS ux_shop_till_sessions_open ON shop_till_sessions (shop_id, device_id) WHERE ended_at IS NULL;
ALTER TABLE shop_till_op_users DROP CONSTRAINT IF EXISTS shop_till_op_users_pkey;
ALTER TABLE shop_till_op_users DROP COLUMN IF EXISTS branch_id;
ALTER TABLE shop_till_op_users ADD PRIMARY KEY (shop_id, op_id);

--  همان تابعِ پاک‌سازیِ ۹۰۰
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
  RETURN OLD;
END $$ LANGUAGE plpgsql;

DELETE FROM schema_migrations WHERE version = '930_branch_doors.sql';
