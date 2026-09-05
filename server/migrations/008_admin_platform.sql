-- ============================================================
--  سکوی مدیریت — آنچه برنامه‌ی مدیریت تا امروز نداشت
--
--  هفت چیز اینجا اضافه می‌شود و هیچ‌کدام به داده‌ی موجود دست نمی‌زند:
--
--    ۱) تخفیف روی نرخ‌های وی‌آی‌پی
--    ۲) کد شش‌رقمیِ وی‌آی‌پی که خودش با ایمیل می‌رود
--    ۳) بازدیدکننده‌ها — کسانی که آمده‌اند ولی هنوز حساب نساخته‌اند
--    ۴) چت پشتیبانی، یکی برای هر دکان/دستگاه
--    ۵) توکن پوش، تا پیامِ پشتیبانی به گوشیِ بسته هم برسد
--    ۶) برنامه‌ها و سایت‌های دیگرِ صاحب سامانه
--    ۷) هشدارِ اشتراکی که دارد تمام می‌شود (از روی همین جدول‌ها خوانده
--       می‌شود، جدول تازه نمی‌خواهد)
-- ============================================================

-- ---------- ۱) تخفیف روی پلن‌ها ----------
--
--  قیمت اصلی همان `price_afn` می‌ماند و دست نمی‌خورد؛ تخفیف کنارش
--  می‌نشیند. پس وقتی تخفیف تمام شد، قیمت خودش برمی‌گردد و کسی لازم
--  نیست عدد قبلی را به یاد داشته باشد.
--
--  دو راه: درصد یا قیمتِ ثابتِ تخفیفی. اگر هر دو پر باشند، قیمتِ ثابت
--  می‌چربد — چون صریح‌تر است.
ALTER TABLE plans ADD COLUMN IF NOT EXISTS discount_percent integer NOT NULL DEFAULT 0;
ALTER TABLE plans ADD COLUMN IF NOT EXISTS discount_price   integer;
ALTER TABLE plans ADD COLUMN IF NOT EXISTS discount_label   text   NOT NULL DEFAULT '';
--  NULL یعنی بی‌مهلت
ALTER TABLE plans ADD COLUMN IF NOT EXISTS discount_until   bigint;

-- ---------- ۲) کد وی‌آی‌پی ----------
--
--  مدیر کد می‌سازد، سرور خودش ایمیلش را می‌فرستد، و کاربر همان شش رقم
--  را در برنامه (یا سایت) می‌زند و اشتراکش فعال می‌شود.
--
--  خودِ کد مثل کد شاگرد فقط به شکل HMAC نگه داشته می‌شود؛ چهار رقمِ
--  آخرش برای شناختن در فهرست می‌ماند.
CREATE TABLE IF NOT EXISTS vip_codes (
  id            text PRIMARY KEY,
  code_hash     text   NOT NULL UNIQUE,
  code_hint     text   NOT NULL DEFAULT '',
  --  پلن و روزها: اگر روزها پر باشد همان، وگرنه از مدتِ پلن حساب می‌شود
  plan          text   NOT NULL DEFAULT 'custom',
  days          integer,
  features      jsonb  NOT NULL DEFAULT '[]'::jsonb,
  max_devices   integer NOT NULL DEFAULT 10,
  note          text   NOT NULL DEFAULT '',
  --  به کدام ایمیل فرستاده شد و آیا رفت
  email         text   NOT NULL DEFAULT '',
  email_status  text   NOT NULL DEFAULT 'none'
                CHECK (email_status IN ('none','queued','sent','failed')),
  email_error   text   NOT NULL DEFAULT '',
  email_sent_at bigint,
  --  اگر برای دکانِ مشخصی صادر شده باشد، فقط همان می‌تواند خرجش کند
  shop_id       text,
  created_by    text   NOT NULL DEFAULT '',
  created_at    bigint NOT NULL,
  expires_at    bigint,
  status        text   NOT NULL DEFAULT 'active'
                CHECK (status IN ('active','used','revoked','expired')),
  used_at       bigint,
  used_by       text   NOT NULL DEFAULT '',
  used_shop_id  text   NOT NULL DEFAULT ''
);
CREATE INDEX IF NOT EXISTS idx_vip_codes_status ON vip_codes(status, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_vip_codes_email  ON vip_codes(email);

-- ---------- ۳) بازدیدکننده‌ها ----------
--
--  تا امروز فقط کسی دیده می‌شد که ثبت‌نام کرده بود. کسی که برنامه را باز
--  کرده یا سایت را آورده و حساب نساخته، هیچ‌جا نبود — یعنی مدیر
--  نمی‌دانست چند نفر آمده‌اند و از کجا.
--
--  ردیف به «دستگاه» بسته است نه به کاربر، چون مهمان کاربری ندارد. اگر
--  بعداً حساب ساخت، `user_id` همان ردیف پر می‌شود و تاریخِ آمدنش
--  گم نمی‌شود.
CREATE TABLE IF NOT EXISTS app_visitors (
  id            text PRIMARY KEY,
  --  کدام برنامه: shop | admin | یا slug هر برنامه‌ی دیگری در managed_apps
  app           text   NOT NULL DEFAULT 'shop',
  device_uid    text   NOT NULL,
  --  web | android | ios | desktop
  platform      text   NOT NULL DEFAULT '',
  app_version   text   NOT NULL DEFAULT '',
  --  خالی یعنی هنوز حساب نساخته — همان «مهمان»
  user_id       text   NOT NULL DEFAULT '',
  shop_id       text   NOT NULL DEFAULT '',
  name          text   NOT NULL DEFAULT '',
  ip            text   NOT NULL DEFAULT '',
  user_agent    text   NOT NULL DEFAULT '',
  language      text   NOT NULL DEFAULT '',
  --  آخرین نقطه‌ای که خودِ دستگاه داده
  lat           double precision,
  lng           double precision,
  accuracy      double precision,
  place         text   NOT NULL DEFAULT '',
  first_seen_at bigint NOT NULL,
  last_seen_at  bigint NOT NULL,
  visits        integer NOT NULL DEFAULT 1
);
CREATE UNIQUE INDEX IF NOT EXISTS idx_visitors_device ON app_visitors(app, device_uid);
CREATE INDEX IF NOT EXISTS idx_visitors_seen   ON app_visitors(last_seen_at DESC);
CREATE INDEX IF NOT EXISTS idx_visitors_user   ON app_visitors(user_id);

-- ---------- ۴) چت پشتیبانی ----------
--
--  هر «گفت‌وگو» یک نفر است، نه یک موضوع: کاربر پیام می‌دهد و همان
--  رشته باز می‌ماند. مهمانِ بی‌حساب هم می‌تواند بنویسد — با شناسه‌ی
--  دستگاهش — وگرنه کسی که هنوز ثبت‌نام نکرده و مشکل دارد، راهی برای
--  پرسیدن نداشت.
CREATE TABLE IF NOT EXISTS support_threads (
  id             text PRIMARY KEY,
  app            text   NOT NULL DEFAULT 'shop',
  user_id        text   NOT NULL DEFAULT '',
  shop_id        text   NOT NULL DEFAULT '',
  device_uid     text   NOT NULL DEFAULT '',
  subject        text   NOT NULL DEFAULT '',
  --  نامِ نمایشی، تا مدیر لازم نباشد برای هر رشته یک کوئری دیگر بزند
  who            text   NOT NULL DEFAULT '',
  contact        text   NOT NULL DEFAULT '',
  status         text   NOT NULL DEFAULT 'open'
                 CHECK (status IN ('open','pending','closed')),
  --  چند پیامِ خوانده‌نشده از هر طرف
  unread_admin   integer NOT NULL DEFAULT 0,
  unread_user    integer NOT NULL DEFAULT 0,
  last_message   text   NOT NULL DEFAULT '',
  last_sender    text   NOT NULL DEFAULT '',
  created_at     bigint NOT NULL,
  updated_at     bigint NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_threads_updated ON support_threads(updated_at DESC);
CREATE INDEX IF NOT EXISTS idx_threads_status  ON support_threads(status, updated_at DESC);
CREATE INDEX IF NOT EXISTS idx_threads_user    ON support_threads(user_id);
CREATE INDEX IF NOT EXISTS idx_threads_device  ON support_threads(device_uid);

CREATE TABLE IF NOT EXISTS support_messages (
  id          text PRIMARY KEY,
  thread_id   text   NOT NULL REFERENCES support_threads(id) ON DELETE CASCADE,
  --  user | admin | system
  sender      text   NOT NULL DEFAULT 'user',
  sender_id   text   NOT NULL DEFAULT '',
  sender_name text   NOT NULL DEFAULT '',
  body        text   NOT NULL,
  --  برای پیام‌های خودکار (مثلاً «اشتراکت سه روز دیگر تمام می‌شود»)
  kind        text   NOT NULL DEFAULT 'text',
  read_at     bigint,
  created_at  bigint NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_messages_thread ON support_messages(thread_id, created_at);

-- ---------- ۵) توکن پوش ----------
--
--  «حتی برنامه‌اش که بسته بود پیام برود» فقط با این ممکن است: خودِ
--  گوشی توکنی می‌دهد و سرور پیام را به سرویسِ پوش می‌سپارد، نه به
--  برنامه‌ای که باز نیست.
CREATE TABLE IF NOT EXISTS push_tokens (
  id          text PRIMARY KEY,
  app         text   NOT NULL DEFAULT 'shop',
  token       text   NOT NULL,
  --  fcm | webpush
  provider    text   NOT NULL DEFAULT 'fcm',
  user_id     text   NOT NULL DEFAULT '',
  admin_id    text   NOT NULL DEFAULT '',
  device_uid  text   NOT NULL DEFAULT '',
  platform    text   NOT NULL DEFAULT '',
  status      text   NOT NULL DEFAULT 'active' CHECK (status IN ('active','stale')),
  created_at  bigint NOT NULL,
  updated_at  bigint NOT NULL,
  last_ok_at  bigint,
  last_error  text   NOT NULL DEFAULT ''
);
CREATE UNIQUE INDEX IF NOT EXISTS idx_push_token ON push_tokens(app, token);
CREATE INDEX IF NOT EXISTS idx_push_user  ON push_tokens(user_id)  WHERE user_id  <> '';
CREATE INDEX IF NOT EXISTS idx_push_admin ON push_tokens(admin_id) WHERE admin_id <> '';

-- ---------- ۶) برنامه‌ها و سایت‌های دیگر ----------
--
--  قرار صاحب مخزن: این پنل فقط برای فروشگاه نباشد. هر برنامه یا سایتِ
--  دیگری هم از همین‌جا دیده و اداره شود.
--
--  `api_key_hash` برای برنامه‌ای است که خودش می‌خواهد به این سرور خبر
--  بدهد (بازدید، خطا، تپش). کلید خام هرگز ذخیره نمی‌شود.
CREATE TABLE IF NOT EXISTS managed_apps (
  id            text PRIMARY KEY,
  slug          text   NOT NULL UNIQUE,
  title         text   NOT NULL DEFAULT '',
  --  app | site | service
  kind          text   NOT NULL DEFAULT 'app',
  url           text   NOT NULL DEFAULT '',
  --  اگر پر باشد، سرور هر چند دقیقه سلامتش را می‌سنجد
  health_url    text   NOT NULL DEFAULT '',
  icon          text   NOT NULL DEFAULT '',
  color         text   NOT NULL DEFAULT '',
  note          text   NOT NULL DEFAULT '',
  api_key_hash  text   NOT NULL DEFAULT '',
  api_key_hint  text   NOT NULL DEFAULT '',
  status        text   NOT NULL DEFAULT 'active' CHECK (status IN ('active','paused','archived')),
  --  آخرین سنجشِ سلامت
  last_check_at bigint,
  last_ok       boolean,
  last_status   integer,
  last_ms       integer,
  last_error    text   NOT NULL DEFAULT '',
  created_at    bigint NOT NULL,
  updated_at    bigint NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_apps_status ON managed_apps(status, slug);

--  فروشگاه و پنلِ خودش، از همان اول در فهرست باشند تا صفحه خالی نیاید
INSERT INTO managed_apps (id, slug, title, kind, url, status, created_at, updated_at)
VALUES
  ('app_shop',  'shop',  'فروشگاه توحید', 'app',  'https://vil3ntec-it.github.io/shop/', 'active',
   (EXTRACT(EPOCH FROM now()) * 1000)::bigint, (EXTRACT(EPOCH FROM now()) * 1000)::bigint),
  ('app_admin', 'admin', 'برنامه‌ی مدیریت', 'app', '', 'active',
   (EXTRACT(EPOCH FROM now()) * 1000)::bigint, (EXTRACT(EPOCH FROM now()) * 1000)::bigint)
ON CONFLICT (slug) DO NOTHING;
