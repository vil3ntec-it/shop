-- ============================================================
--  شعبه درونِ یک دکان — فازِ ۱ (`docs/BRANCHES-fa.md`، `lib/branches.js`؛ پشتِ پرچمِ خاموشِ `shop.branches`).
--
--  ⛔ فقط افزودنی — هیچ ردیفِ امروز عوض یا جابه‌جا نمی‌شود. «شعبهٔ اصلی» شناسهٔ '' دارد و همیشه هست
--     (بی ردیف؛ ردیفِ '' فقط وقتی صاحب نامش را عوض کند): همهٔ دادهٔ امروز همان شعبهٔ اصلی است —
--     حسابِ همگام‌سازی همان شناسهٔ دکان و `branch_id` ِ جدول‌های موجودی همان ''.
--  ⛔ دادهٔ دفترِ شعبهٔ دیگر در «بخشِ» جدای همگام‌سازی است: account_id = '<دکان>#<شعبه>' (فقط v3).
--     پاک شدنِ ردیفِ شعبه (و با cascade، پاک شدنِ دکان) همهٔ ردیف‌های همان بخش را هم پاک می‌کند (تریگرِ پایین).
--  ⛔ عضوِ بی ردیف در `shop_member_branches` = فقط شعبهٔ اصلی (همان امروز). صاحب هرگز ردیف نمی‌گیرد.
--
--  برگشت: rollback/880_shop_branches.down.sql
-- ============================================================
CREATE TABLE IF NOT EXISTS shop_branches (
  shop_id     text   NOT NULL REFERENCES shops(id) ON DELETE CASCADE,
  id          text   NOT NULL,
  name        text   NOT NULL,
  status      text   NOT NULL DEFAULT 'active' CHECK (status IN ('active','archived')),
  created_by  text   NOT NULL DEFAULT '',
  created_at  bigint NOT NULL,
  updated_at  bigint NOT NULL,
  PRIMARY KEY (shop_id, id),
  CHECK (id = '' OR id ~ '^brn_[0-9a-f]{24}$'),
  CHECK (id <> '' OR status = 'active')
);
CREATE UNIQUE INDEX IF NOT EXISTS ux_shop_branches_name
  ON shop_branches (shop_id, lower(name)) WHERE status = 'active';

CREATE TABLE IF NOT EXISTS shop_member_branches (
  shop_id     text   NOT NULL REFERENCES shops(id) ON DELETE CASCADE,
  user_id     text   NOT NULL,
  branch_id   text   NOT NULL,
  updated_by  text   NOT NULL DEFAULT '',
  updated_at  bigint NOT NULL,
  PRIMARY KEY (shop_id, user_id, branch_id)
);
CREATE INDEX IF NOT EXISTS ix_shop_member_branches_user ON shop_member_branches (user_id);

--  پاک شدنِ ردیفِ شعبه ⇒ همهٔ ردیف‌های بخشِ همگام‌سازیِ همان شعبه و انتسابِ کارمندان به آن.
--  کلیدِ بخش '#' دارد که در هیچ شناسهٔ دکان/پمپی نیست (newId: پیشوند_هگز) ⇒ هیچ حسابِ دیگری لمس نمی‌شود.
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
  RETURN OLD;
END $$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS shop_branches_purge ON shop_branches;
CREATE TRIGGER shop_branches_purge AFTER DELETE ON shop_branches
  FOR EACH ROW EXECUTE FUNCTION shop_branch_purge();
