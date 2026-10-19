-- ============================================================
--  نقشِ عضویتِ «پیک» و دامنهٔ توکن — DLV-01، v3 §۳.۱۱.
--
--  ۱) `shop_members.role` و `staff_codes.role` مقدارِ تازهٔ 'courier' را
--     می‌پذیرند. صاحب دکان از صفحهٔ کارمندان «دعوتِ پیک» می‌سازد (فقط با
--     پرچمِ روشنِ `shop.delivery`)؛ کسی که با آن کد وارد شود عضوِ پیک است.
--  ۲) `tokens.scope` — توکنِ عضوِ پیک `scope='courier'` می‌گیرد و فقط
--     `/api/courier/*` را باز می‌کند. خالی یعنی همان توکنِ امروز.
--
--  ⛔ هیچ ردیفی عوض نمی‌شود: پیش‌فرضِ ستون خالی است و تا صاحب دکانی
--  پیک دعوت نکند هیچ عضوی نقشِ تازه نمی‌گیرد — رفتار همان امروز است.
--
--  برگشت:
--    UPDATE shop_members SET role='staff' WHERE role='courier';
--    UPDATE staff_codes SET status='revoked' WHERE role='courier';
--    UPDATE staff_codes SET role='staff' WHERE role='courier';
--    ALTER TABLE shop_members DROP CONSTRAINT shop_members_role_check;
--    ALTER TABLE shop_members ADD CONSTRAINT shop_members_role_check CHECK (role IN ('owner','manager','staff'));
--    ALTER TABLE staff_codes DROP CONSTRAINT staff_codes_role_check;
--    ALTER TABLE staff_codes ADD CONSTRAINT staff_codes_role_check CHECK (role IN ('manager','staff'));
--    UPDATE tokens SET revoked_at = <now> WHERE scope <> '' AND revoked_at IS NULL;
--    ALTER TABLE tokens DROP COLUMN scope;
--  (کدِ پیش از این مهاجرت ستونِ scope را نمی‌خواند؛ پس پیش از برداشتنِ
--  ستون، توکن‌های دامنه‌دار باید باطل شوند تا بی‌دامنه پذیرفته نشوند.)
-- ============================================================

ALTER TABLE shop_members DROP CONSTRAINT IF EXISTS shop_members_role_check;
ALTER TABLE shop_members ADD CONSTRAINT shop_members_role_check
  CHECK (role IN ('owner','manager','staff','courier'));

ALTER TABLE staff_codes DROP CONSTRAINT IF EXISTS staff_codes_role_check;
ALTER TABLE staff_codes ADD CONSTRAINT staff_codes_role_check
  CHECK (role IN ('manager','staff','courier'));

ALTER TABLE tokens ADD COLUMN IF NOT EXISTS scope text NOT NULL DEFAULT '';
