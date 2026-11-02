-- ============================================================
--  STF-06: دسترسیِ کارمند محدود به مکانِ موجودی («شعبه یا انبار»)
--  (lib/staff-scope.js، پشتِ پرچمِ خاموشِ live_config ِ `shop.staff_scope`).
--
--  ⛔ فقط افزودنی — هیچ داده‌ای عوض نمی‌شود. نبودنِ ردیف = همهٔ مکان‌ها،
--  یعنی همان رفتارِ امروز. مالک هرگز ردیف نمی‌گیرد (و اگر بگیرد خوانده نمی‌شود).
--  `places` زیرمجموعهٔ سرهای انتقال (`stock-transfers.ENDPOINTS`): shop_floor، warehouse.
--
--  برگشت: DROP TABLE shop_member_scopes; (rollback/189_staff_scope.down.sql)
-- ============================================================
CREATE TABLE IF NOT EXISTS shop_member_scopes (
  shop_id     text   NOT NULL REFERENCES shops(id) ON DELETE CASCADE,
  user_id     text   NOT NULL,
  places      jsonb  NOT NULL DEFAULT '[]'::jsonb,
  updated_by  text   NOT NULL DEFAULT '',
  updated_at  bigint NOT NULL,
  PRIMARY KEY (shop_id, user_id)
);
