-- ============================================================
--  ۲۶۰ — نقشِ سفارشی (STF-01، بخشِ ۱۰.۱ «مالک می‌تواند نقش سفارشی بسازد»؛ `lib/custom-roles.js`، پشتِ پرچمِ
--  خاموشِ `shop.custom_roles`).
--
--  نقشِ سفارشی = نام + یکی از نقش‌های ثابتِ کارمند (`base_role`) + یک لایهٔ مجوز با قالبِ `perm-core` (حالت و
--  سقف). عضوِ دارای آن در `shop_members.role` همان نقشِ پایه را دارد؛ لایه میانِ لایهٔ نقش (`shop_role_perms`) و
--  لایهٔ کاربر (`shop_member_perms`) می‌نشیند و هرگز گشادتر از پیش‌فرضِ نقشِ پایه نیست (`staff-perms.layersFor`).
--  پیوندِ عضو ⇒ نقش در `shop_member_custom_roles` (هر عضو حداکثر یک نقشِ سفارشی)؛ پاک شدنِ نقش پیوندها را هم
--  پاک می‌کند و عضو روی همان نقشِ پایه می‌ماند.
--
--  rollback: migrations/rollback/260_shop_custom_roles.down.sql
-- ============================================================

CREATE TABLE IF NOT EXISTS shop_custom_roles (
  id          text    PRIMARY KEY,
  shop_id     text    NOT NULL REFERENCES shops(id) ON DELETE CASCADE,
  name        text    NOT NULL CHECK (char_length(name) BETWEEN 1 AND 40),
  base_role   text    NOT NULL CHECK (base_role IN ('manager','staff','cashier','storekeeper','picker','accountant')),
  perms       jsonb   NOT NULL DEFAULT '{}'::jsonb,
  created_by  text    NOT NULL DEFAULT '',
  created_at  bigint  NOT NULL,
  updated_by  text    NOT NULL DEFAULT '',
  updated_at  bigint  NOT NULL
);
CREATE UNIQUE INDEX IF NOT EXISTS ux_shop_custom_roles_name ON shop_custom_roles(shop_id, lower(name));

CREATE TABLE IF NOT EXISTS shop_member_custom_roles (
  shop_id     text    NOT NULL REFERENCES shops(id) ON DELETE CASCADE,
  user_id     text    NOT NULL,
  role_id     text    NOT NULL REFERENCES shop_custom_roles(id) ON DELETE CASCADE,
  assigned_by text    NOT NULL DEFAULT '',
  assigned_at bigint  NOT NULL,
  PRIMARY KEY (shop_id, user_id)
);
CREATE INDEX IF NOT EXISTS idx_shop_member_custom_roles_role ON shop_member_custom_roles(role_id);
