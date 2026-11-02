-- ============================================================
--  نگهبانِ حسابِ گیرندهٔ حساب‌پی (درخواستِ صاحب‌کار ۱۴۰۵/۰۸/۱۱: «کسی نتواند کارت/حساب یا QR ِ خودش را
--  جای حسابِ دکان بگذارد»؛ PAY-21، PAY-06) — `lib/hesabpay-guard.js`.
--
--  shop_hesabpay:
--    account_name / merchant_last4   نامِ صاحبِ حساب و چهار رقمِ آخر ⇒ «پول به حسابِ <نام> — <۴ رقم>» ِ صندوق
--    payee_fp                        اثرِ انگشتِ حسابِ **فعال** (هشِ دکان + حالت + شماره/شناسهٔ پذیرنده)
--    active_since / first_set_at     کی فعال شد / نخستین حسابِ این دکان کی گذاشته شد (NULL = هرگز ⇒ نخستین بار فوری)
--    pending + pending_until         حسابِ تازه‌ای که هنوز فعال نیست (۲۴ ساعت؛ کلیدها همان‌طور رمزشده)؛ تا آن دم
--                                    حسابِ کهنه فعال می‌ماند. «این من نبودم» ⇒ پاک و section_locked_until.
--  hesabpay_payees     هر حسابی که روزی فعال بود (اثرِ انگشت، نام، ۴ رقم) ⇒ گزارشِ «پول به کدام حساب رفت».
--  payment_intents.payee_fp  اثرِ انگشتِ حسابِ فعال هنگامِ ساختنِ قصد؛ «پرداخت شد» فقط اگر هنوز همان باشد.
--
--  فقط افزودنی — هیچ ستون یا ردیفِ موجودی پاک نمی‌شود. بازگشت: `rollback/205_hesabpay_payee_guard.down.sql`.
-- ============================================================

ALTER TABLE shop_hesabpay ADD COLUMN IF NOT EXISTS account_name        text   NOT NULL DEFAULT '';
ALTER TABLE shop_hesabpay ADD COLUMN IF NOT EXISTS merchant_last4      text   NOT NULL DEFAULT '';
ALTER TABLE shop_hesabpay ADD COLUMN IF NOT EXISTS payee_fp            text   NOT NULL DEFAULT '';
ALTER TABLE shop_hesabpay ADD COLUMN IF NOT EXISTS active_since        bigint;
ALTER TABLE shop_hesabpay ADD COLUMN IF NOT EXISTS first_set_at        bigint;
ALTER TABLE shop_hesabpay ADD COLUMN IF NOT EXISTS pending             jsonb;
ALTER TABLE shop_hesabpay ADD COLUMN IF NOT EXISTS pending_until       bigint NOT NULL DEFAULT 0;
ALTER TABLE shop_hesabpay ADD COLUMN IF NOT EXISTS section_locked_until bigint NOT NULL DEFAULT 0;

--  حسابِ موجود (پیش از این نسخه) همان «نخستین حساب» است و فعال می‌ماند؛ اثرِ انگشتش در نخستین خواندن ساخته می‌شود
UPDATE shop_hesabpay SET first_set_at = updated_at, active_since = updated_at
 WHERE mode <> 'off' AND first_set_at IS NULL;

CREATE TABLE IF NOT EXISTS hesabpay_payees (
  shop_id      text   NOT NULL REFERENCES shops(id) ON DELETE CASCADE,
  fp           text   NOT NULL,
  mode         text   NOT NULL CHECK (mode IN ('simple','auto')),
  name         text   NOT NULL DEFAULT '',
  last4        text   NOT NULL DEFAULT '',
  active_from  bigint NOT NULL,
  retired_at   bigint,
  PRIMARY KEY (shop_id, fp)
);

ALTER TABLE payment_intents ADD COLUMN IF NOT EXISTS payee_fp text NOT NULL DEFAULT '';
