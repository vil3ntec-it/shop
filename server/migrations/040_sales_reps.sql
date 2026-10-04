-- ============================================================
--  نماینده‌های فروش — شورا، چ۳ (۱۴۰۵/۰۷/۲۰)
--
--  «نقشِ نماینده در سرورِ حساب و پنل: کدِ تخفیفِ هر نماینده، فروش‌های او،
--  و گزارشِ کمیسیون (درصد از پنل). نماینده فقط مشتری‌های خودش را می‌بیند.»
--
--  ⛔ دفترِ دومی برای فروش ساخته نشد: فروشِ نماینده همان ردیف‌های
--  `discount_uses` است که کدشان مالِ اوست. این جدول فقط می‌گوید «چه کسی
--  نماینده است و کمیسیونش چند درصد است».
-- ============================================================

CREATE TABLE IF NOT EXISTS sales_reps (
  id              text    PRIMARY KEY,
  app             text    NOT NULL CHECK (app IN ('shop','pump')),
  --  حسابِ خودِ نماینده (همان `users`) — با همان ایمیل و رمز وارد می‌شود
  user_id         text    NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  name            text    NOT NULL DEFAULT '',
  --  درصدِ کمیسیون × ۱۰۰ (۷٫۵٪ ⇒ ۷۵۰). ⛔ پیش‌فرض ندارد: فقط از پنل.
  commission_bp   integer NOT NULL CHECK (commission_bp >= 0 AND commission_bp <= 10000),
  status          text    NOT NULL DEFAULT 'active' CHECK (status IN ('active','disabled')),
  created_by      text    NOT NULL DEFAULT '',
  created_at      bigint  NOT NULL,
  UNIQUE (app, user_id)
);

--  کدِ تخفیفِ هر نماینده — خالی یعنی کدِ خودِ سامانه
ALTER TABLE discount_codes ADD COLUMN IF NOT EXISTS rep_id text NOT NULL DEFAULT '';
CREATE INDEX IF NOT EXISTS idx_discount_codes_rep ON discount_codes(rep_id) WHERE rep_id <> '';

--  ⛔ نماینده و درصدِ **همان لحظهٔ فروش** روی خودِ ردیف می‌نشیند: عوض شدنِ
--  درصد یا جابه‌جا شدنِ کد به نمایندهٔ دیگر، فروش‌های گذشته را دست نمی‌زند —
--  همان قاعدهٔ «اشتراکِ فروخته‌شده قیمتِ روزِ خرید را نگه می‌دارد».
ALTER TABLE discount_uses ADD COLUMN IF NOT EXISTS rep_id        text    NOT NULL DEFAULT '';
ALTER TABLE discount_uses ADD COLUMN IF NOT EXISTS commission_bp integer NOT NULL DEFAULT 0;
ALTER TABLE discount_uses ADD COLUMN IF NOT EXISTS currency      text    NOT NULL DEFAULT '';
CREATE INDEX IF NOT EXISTS idx_discount_uses_rep ON discount_uses(rep_id, created_at DESC) WHERE rep_id <> '';
