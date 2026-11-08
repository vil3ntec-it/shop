-- ============================================================
--  ۲۸۰ — محدودهٔ کارمند (STF-06) برای گامِ بی‌اینترنتِ انتقال (INV-11)
--
--  گامِ انتقالِ دستگاه (ردیفِ `auditLog` با `type = stock_transfer`) شناسهٔ کارمندِ امضاشده ندارد (`by` را خودِ
--  دستگاه می‌نویسد). تنها هویتِ سنجیده همان توکنِ عضوی است که ردیف را push می‌کند. پس هنگامِ push، اگر آن عضو
--  محدود است (پرچمِ `shop.staff_scope`)، مکان‌هایش کنارِ کلیدِ گام همین‌جا می‌نشیند؛ هنگامِ خواندنِ دفتر
--  (`stock-transfers.ingestDevice`) گامِ بیرونِ محدوده با کدِ `location_out_of_scope` «نپذیرفته» ثبت می‌شود.
--
--  فقط یک بار برای هر گام (نخستین push ِ ردیف)؛ عضوِ بی‌محدودیت، مالک یا پرچمِ خاموش ⇒ هیچ ردیفی.
--  rollback: migrations/rollback/280_transfer_step_scopes.down.sql (آزموده در test/catalog-transfer-steps-v3.test.js)
-- ============================================================

CREATE TABLE IF NOT EXISTS stock_transfer_step_scopes (
  shop_id     text    NOT NULL REFERENCES shops(id) ON DELETE CASCADE,
  --  شناسهٔ ردیفِ `auditLog` ِ گام (= `stock_transfer_steps.step_key`)
  step_key    text    NOT NULL,
  --  عضوی که ردیف را push کرد (از توکن)
  user_id     text    NOT NULL,
  --  مکان‌های مجازِ همان لحظه، مثلِ `shop_member_scopes.places`
  places      jsonb   NOT NULL,
  --  v1 | v3 (راهِ push)
  via         text    NOT NULL CHECK (via IN ('v1','v3')),
  created_at  bigint  NOT NULL,
  PRIMARY KEY (shop_id, step_key)
);
