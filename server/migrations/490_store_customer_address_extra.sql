-- ============================================================
--  ۴۹۰ — دفترِ نشانیِ حسابِ مشتری: کلیدهای کمکیِ فرمِ ثبت (CRM-09، §۴.۴؛ ۱۴۰۵/۰۸/۲۰)
--
--  نشانیِ نام‌دارِ ویترین و بازارچه (CHK-09، مهاجرتِ ۱۱۲) تا امروز فقط نام، متن، نشانه و پین داشت؛ فرمِ ثبت
--  (`orders.parseAddress`) ناحیه، رنگِ دروازه، ولایت، گذر یا کوچه، منزل و طبقه، شمارهٔ دوم و Plus Code هم
--  می‌گیرد ⇒ مشتریِ همیشگی هر بار این‌ها را از نو می‌نوشت. همه اختیاری و فقط متنِ کمکی‌اند: جای پین،
--  نشانه یا متن را نمی‌گیرند (همان CHECK ِ ۱۱۲). سنجش و سقف همان `orders.addressExtras` ِ ثبت.
--  ⛔ ستونِ دیگری نیست (LOC-10). ستون‌ها خالی‌اند تا مشتری بنویسد؛ ردیفِ پیشین دست نمی‌خورد.
--  فقط افزودنی. rollback: migrations/rollback/490_store_customer_address_extra.down.sql
-- ============================================================

ALTER TABLE store_customer_addresses ADD COLUMN IF NOT EXISTS area       text NOT NULL DEFAULT '' CHECK (length(area) <= 80);
ALTER TABLE store_customer_addresses ADD COLUMN IF NOT EXISTS gate_color text NOT NULL DEFAULT '' CHECK (length(gate_color) <= 40);
ALTER TABLE store_customer_addresses ADD COLUMN IF NOT EXISTS province   text NOT NULL DEFAULT '' CHECK (length(province) <= 60);
ALTER TABLE store_customer_addresses ADD COLUMN IF NOT EXISTS street     text NOT NULL DEFAULT '' CHECK (length(street) <= 120);
ALTER TABLE store_customer_addresses ADD COLUMN IF NOT EXISTS house      text NOT NULL DEFAULT '' CHECK (length(house) <= 60);
ALTER TABLE store_customer_addresses ADD COLUMN IF NOT EXISTS phone2     text NOT NULL DEFAULT '' CHECK (length(phone2) <= 20);
ALTER TABLE store_customer_addresses ADD COLUMN IF NOT EXISTS plus_code  text NOT NULL DEFAULT '' CHECK (length(plus_code) <= 60);
