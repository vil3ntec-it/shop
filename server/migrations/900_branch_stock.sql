-- ============================================================
--  شعبه درونِ یک دکان — فازِ ۳ ِ سرور (`docs/BRANCHES-fa.md` §۸؛ پشتِ پرچمِ خاموشِ `shop.branches`).
--
--  ⛔ فقط افزودنی — هیچ ستون یا ردیفِ امروز عوض نمی‌شود. شعبهٔ اصلی همان جدول‌های امروز را می‌خواند و می‌نویسد
--     (`catalog_sync_state`، `branch_id = ''` ِ جدول‌های موجودی) ⇒ دادهٔ امروز بایت‌به‌بایت همان.
--  ⛔ کاتالوگِ مشترک: تعریفِ کالا یک بار برای کلِ دکان (`catalog_products`، از دفترِ شعبهٔ اصلی)؛ موجودیِ هر شعبه
--     جدا با `branch_id` ِ همان شعبه در `stock_moves`/`stock_levels`/`stock_tasks` (ستون از مهاجرتِ ۰۸۳ هست).
--     کلیدِ یکتای دفترِ شعبهٔ غیرِ اصلی پیشوندِ `<شعبه>|` دارد (UNIQUE (shop_id, source_key) ِ امروز دست نمی‌خورد).
--  ⛔ آینهٔ کاتالوگ: ردیف‌های `products`/`productCategories`/`productUnits` ِ بخشِ اصلی با op ِ سرور (`srv.cm.`) در
--     بخشِ هر شعبه (`lib/branch-catalog.js`)؛ این‌جا فقط «تا کجا آینه شد».
--
--  برگشت: rollback/900_branch_stock.down.sql
-- ============================================================

--  تا کجای دفترِ همگام‌سازیِ **بخشِ شعبه** (`<دکان>#<شعبه>`، همیشه v3) برای موجودیِ سرور خوانده شده
CREATE TABLE IF NOT EXISTS catalog_branch_state (
  shop_id      text   NOT NULL,
  branch_id    text   NOT NULL CHECK (branch_id ~ '^brn_[0-9a-f]{24}$'),
  watermark    bigint NOT NULL DEFAULT 0,
  last_run_at  bigint NOT NULL,
  PRIMARY KEY (shop_id, branch_id),
  FOREIGN KEY (shop_id, branch_id) REFERENCES shop_branches (shop_id, id) ON DELETE CASCADE
);

--  آینهٔ کاتالوگ: بزرگ‌ترین `updated_seq` ِ ردیفِ کاتالوگِ اصلی که در بخشِ شعبه نشست، و سرِ دفترِ اصلی در آخرین
--  آینهٔ کامل (راهِ تند: سرِ اصلی عوض نشده ⇒ هیچ پرسشِ دیگر)
CREATE TABLE IF NOT EXISTS shop_branch_mirror (
  shop_id      text   NOT NULL,
  branch_id    text   NOT NULL CHECK (branch_id ~ '^brn_[0-9a-f]{24}$'),
  main_seq     bigint NOT NULL DEFAULT 0,
  main_head    bigint NOT NULL DEFAULT 0,
  rows         bigint NOT NULL DEFAULT 0,
  updated_at   bigint NOT NULL,
  PRIMARY KEY (shop_id, branch_id),
  FOREIGN KEY (shop_id, branch_id) REFERENCES shop_branches (shop_id, id) ON DELETE CASCADE
);

--  آینه فقط ردیف‌های کاتالوگِ تازه‌تر از نشانهٔ هر شعبه را می‌خواند (نه همهٔ کالاها در هر pull) — نمایهٔ جزئی و کوچک
CREATE INDEX IF NOT EXISTS ix_sync_v3_rows_catalog_seq ON sync_v3_rows (account_id, updated_seq)
  WHERE table_name IN ('products', 'productCategories', 'productUnits');

--  پاک شدنِ ردیفِ شعبه ⇒ همان پاک‌سازیِ ۸۸۰ (بخشِ همگام‌سازی و انتساب) + موجودیِ سرورِ همان شعبه.
--  شعبهٔ اصلی ('') هرگز (همان بندِ نخست).
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
