-- ============================================================
--  ثبت‌نام سه‌مرحله‌ای و لوکیشن دستگاه
--
--  قرار صاحب مخزن: شماره‌ی موبایل از ثبت‌نام برداشته شد — همان ایمیل
--  بس است. ثبت‌نام سه پله دارد:
--
--    ۱) نام، ایمیل، رمز و تکرارش
--    ۲) کد شش‌رقمی که به همان ایمیل رفته
--    ۳) لوکیشن دکان + پذیرش شرایط و ضوابط
--
--  بین پله‌ی دوم و سوم چیزی در جدول users ساخته نمی‌شود؛ کاربر تازه
--  در پله‌ی سوم — بعد از پذیرش شرایط — ساخته می‌شود. تا آن لحظه فقط یک
--  «بلیت ثبت‌نام» (توکنِ kind='register') وجود دارد که ایمیلِ
--  تأییدشده را نگه می‌دارد.
-- ============================================================

--  بلیت ثبت‌نام یک توکن است مثل بقیه، پس همان‌جا می‌نشیند. بدون این
--  تغییر، CHECK جدول توکن‌ها آن را رد می‌کرد.
ALTER TABLE tokens DROP CONSTRAINT IF EXISTS tokens_kind_check;
ALTER TABLE tokens ADD CONSTRAINT tokens_kind_check
  CHECK (kind IN ('access','refresh','admin','register'));

-- ---------- پذیرش شرایط و ضوابط ----------
--  چه کسی، چه نسخه‌ای و کِی را پذیرفته. نسخه ذخیره می‌شود تا اگر متن
--  عوض شد، بشود فهمید کاربر کدام متن را دیده بوده.
ALTER TABLE users ADD COLUMN IF NOT EXISTS terms_version     text   NOT NULL DEFAULT '';
ALTER TABLE users ADD COLUMN IF NOT EXISTS terms_accepted_at bigint;

-- ---------- لوکیشن ----------
--
--  چرا جدولِ جدا و نه چند ستون روی users: لوکیشن حتی پیش از ثبت‌نام هم
--  گرفته می‌شود. دستگاهی که هنوز حساب ندارد کاربری ندارد که ستونش را
--  پر کند، ولی شناسه‌ی دستگاه را دارد. پس ردیف به «دستگاه» بسته است و
--  user_id هر وقت حساب ساخته شد پر می‌شود.
CREATE TABLE IF NOT EXISTS device_locations (
  id          text   PRIMARY KEY,
  device_uid  text   NOT NULL,
  --  خالی یعنی هنوز حساب نساخته
  user_id     text   NOT NULL DEFAULT '',
  lat         double precision NOT NULL,
  lng         double precision NOT NULL,
  --  دقت به متر؛ منفی یعنی دستگاه نگفته
  accuracy    double precision NOT NULL DEFAULT -1,
  --  gps | network | manual | signup
  source      text   NOT NULL DEFAULT '',
  --  اگر برنامه نام محل را هم داشته باشد
  label       text   NOT NULL DEFAULT '',
  ip          text   NOT NULL DEFAULT '',
  created_at  bigint NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_locations_device ON device_locations(device_uid, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_locations_user   ON device_locations(user_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_locations_time   ON device_locations(created_at DESC);

--  آخرین لوکیشنِ هر کاربر، برای اینکه پنل مدیریت لازم نباشد هر بار کل
--  تاریخچه را بخواند
ALTER TABLE users ADD COLUMN IF NOT EXISTS last_lat         double precision;
ALTER TABLE users ADD COLUMN IF NOT EXISTS last_lng         double precision;
ALTER TABLE users ADD COLUMN IF NOT EXISTS last_location_at bigint;
