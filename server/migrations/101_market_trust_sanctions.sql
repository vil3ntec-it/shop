-- ============================================================
--  جریمه‌های بازارچه — فقط سه نوع، همه برگشت‌پذیر و با دلیل (v3 §۳۸.۶ بندِ ۴)
--
--    warning             هشدار؛ فقط به دکان نشان داده می‌شود
--    hide_listing        کالای ویترین از کاتالوگ، عکس و ثبتِ سفارش کنار می‌رود
--                        (`published` ِ خودِ دکان دست نمی‌خورد ⇒ برداشتن = برگشتِ کامل)
--    suspend_storefront  درِ عمومیِ ویترین همان ۴۰۴ ِ «ویترین نیست» را می‌دهد
--                        (تنظیماتِ ویترین دست نمی‌خورد)
--
--  ⛔ هیچ ردیفی پاک نمی‌شود: برداشتن = `lifted_at` + کسی که برداشت + دلیل.
--  ⛔ هر جریمه به پرونده‌ای بسته است؛ پذیرفتنِ اعتراضِ همان پرونده فقط
--  جریمه‌های همان پرونده را برمی‌دارد.
--  ⛔ اجرای جریمه به پرچمِ `market.trust` بسته نیست: پرچم پیش‌فرض خاموش است ⇒
--  ردیفی نیست ⇒ رفتارِ ویترین همان قبلی است؛ خاموش کردنِ پرچم پس از جریمه
--  جریمه را بی‌صدا برنمی‌دارد (برداشتن فقط با مدیر و دلیل).
-- ============================================================

CREATE TABLE IF NOT EXISTS trust_sanctions (
  id                  text    PRIMARY KEY,
  shop_id             text    NOT NULL REFERENCES shops(id) ON DELETE CASCADE,
  case_id             text    NOT NULL REFERENCES trust_cases(id) ON DELETE CASCADE,
  kind                text    NOT NULL CHECK (kind IN ('warning','hide_listing','suspend_storefront')),
  product_id          text    NOT NULL DEFAULT '',
  reason              text    NOT NULL,
  created_by          text    NOT NULL,
  created_at          bigint  NOT NULL,
  lifted_at           bigint,
  lifted_by           text    NOT NULL DEFAULT '',
  lift_reason         text    NOT NULL DEFAULT '',
  CHECK ((kind = 'hide_listing') = (product_id <> ''))
);
CREATE INDEX IF NOT EXISTS ix_trust_sanctions_active
  ON trust_sanctions(shop_id, kind, product_id) WHERE lifted_at IS NULL;
CREATE INDEX IF NOT EXISTS ix_trust_sanctions_case ON trust_sanctions(case_id);
