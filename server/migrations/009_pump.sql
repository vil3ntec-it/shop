-- ============================================================
--  بخشِ پمپ‌بنزین — همسایهٔ بخشِ شاپ، نه ادامهٔ آن
--
--  ── خواستهٔ صاحب مخزن ──────────────────────────────────────
--  «سرور و ادرس و دامنه یکی باشه اما جدا باشن، چون هم برنامه
--  جداگانه است هم حساب کتاب‌ها. برای هر حساب تو فولدرِ سرورها
--  حسابِ طرف با اطلاعات بره، و به هر حساب اشتراک بدم یا ندم،
--  مثل بخشِ شاپ.»
--
--  ── چرا جدول‌های جدا و نه یک ستونِ app روی جدول‌های شاپ ─────
--  چون `subscriptions.shop_id` کلیدِ خارجی به `shops` دارد. اگر
--  می‌خواستیم پمپ هم در همان ردیف بنشیند، یا باید آن کلید را
--  برمی‌داشتیم (و شاپ بی‌حفاظ می‌شد) یا ستونی می‌ساختیم که نیمی از
--  ردیف‌ها همیشه خالی‌اش بگذارند. هر دو، دفترِ زندهٔ شاپ را به خطر
--  می‌اندازد برای چیزی که تازه دارد ساخته می‌شود.
--
--  پس: جدول‌های جدا، و کدِ مشترک. `lib/tenancy.js` به هر دو بخش
--  یک دستگاهِ اشتراک می‌دهد بی آنکه ردیف‌هاشان به هم برسد.
--
--  ── چه چیزی مشترک می‌ماند ──────────────────────────────────
--  فقط `users`. یک نفر با یک لاگینِ گوگل هم دکان دارد هم پمپ؛ دو
--  حساب ساختن برای یک آدم، همان چیزی است که «یک سرور، یک حساب»
--  در CLAUDE.md منعش کرده.
-- ============================================================

-- ---------- پمپ‌بنزین‌ها ----------
--  `code` همان کدِ کوتاهی است که برنامهٔ کامپیوتر و اپِ کارمند با آن
--  خودشان را معرفی می‌کنند (`pump1`). یکتا در کلِ سامانه، چون در
--  نشانیِ سرورِ خانگی هم می‌آید.
CREATE TABLE IF NOT EXISTS stations (
  id             text    PRIMARY KEY,
  owner_user_id  text    NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
  code           text    NOT NULL UNIQUE,
  name           text    NOT NULL DEFAULT '',
  --  نشانیِ سرورِ خانگیِ همین پمپ، اگر ثبت شده باشد. اپِ کارمند این را
  --  از سرور می‌گیرد تا دیگر از کسی نشانی نپرسد.
  home_url       text    NOT NULL DEFAULT '',
  home_seen_at   bigint,
  status         text    NOT NULL DEFAULT 'active' CHECK (status IN ('active','disabled')),
  max_members    integer NOT NULL DEFAULT 10,
  created_at     bigint  NOT NULL,
  updated_at     bigint  NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_stations_owner ON stations(owner_user_id);

-- ---------- اعضای پمپ ----------
CREATE TABLE IF NOT EXISTS station_members (
  id          text   PRIMARY KEY,
  station_id  text   NOT NULL REFERENCES stations(id) ON DELETE CASCADE,
  user_id     text   NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  role        text   NOT NULL DEFAULT 'staff' CHECK (role IN ('owner','manager','staff')),
  status      text   NOT NULL DEFAULT 'active' CHECK (status IN ('active','suspended','removed')),
  created_at  bigint NOT NULL,
  updated_at  bigint NOT NULL,
  UNIQUE (station_id, user_id)
);
CREATE INDEX IF NOT EXISTS idx_station_members_user ON station_members(user_id, status);

-- ---------- اشتراکِ پمپ ----------
--  عیناً شکلِ `subscriptions`، تا `lib/tenancy.js` بتواند یک پرس‌وجو
--  برای هر دو بنویسد. هر تفاوتی اینجا، یعنی یک شرطِ اضافه در آنجا.
CREATE TABLE IF NOT EXISTS station_subscriptions (
  id           text    PRIMARY KEY,
  station_id   text    NOT NULL REFERENCES stations(id) ON DELETE CASCADE,
  plan         text    NOT NULL DEFAULT 'custom',
  status       text    NOT NULL DEFAULT 'active'
               CHECK (status IN ('active','suspended','cancelled','expired','pending')),
  starts_at    bigint  NOT NULL,
  ends_at      bigint  NOT NULL,
  features     jsonb   NOT NULL DEFAULT '[]'::jsonb,
  max_devices  integer NOT NULL DEFAULT 10,
  grace_days   integer NOT NULL DEFAULT 0,
  note         text    NOT NULL DEFAULT '',
  created_at   bigint  NOT NULL,
  updated_at   bigint  NOT NULL,
  created_by   text    NOT NULL DEFAULT ''
);
CREATE INDEX IF NOT EXISTS idx_station_subs ON station_subscriptions(station_id, status);
--  هر پمپ فقط یک اشتراکِ زنده دارد
CREATE UNIQUE INDEX IF NOT EXISTS idx_station_subs_one_live ON station_subscriptions(station_id)
  WHERE status IN ('active','suspended','pending');

-- ---------- تاریخچهٔ اشتراکِ پمپ ----------
CREATE TABLE IF NOT EXISTS station_subscription_history (
  id              text   PRIMARY KEY,
  subscription_id text   NOT NULL,
  station_id      text   NOT NULL REFERENCES stations(id) ON DELETE CASCADE,
  action          text   NOT NULL CHECK (action IN ('grant','renew','status','expire')),
  plan            text   NOT NULL DEFAULT '',
  prev_status     text   NOT NULL DEFAULT '',
  new_status      text   NOT NULL DEFAULT '',
  prev_ends_at    bigint,
  new_ends_at     bigint,
  actor           text   NOT NULL DEFAULT '',
  note            text   NOT NULL DEFAULT '',
  created_at      bigint NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_station_sub_hist
  ON station_subscription_history(station_id, created_at DESC);

-- ---------- کدِ شش‌رقمیِ پمپ ----------
--  دفترِ کدها هم جداست: کدی که برای پمپ ساخته شده نباید اشتباهی
--  اشتراکِ دکانی را باز کند، و برعکس.
CREATE TABLE IF NOT EXISTS station_vip_codes (
  id              text    PRIMARY KEY,
  code_hash       text    NOT NULL UNIQUE,
  code_hint       text    NOT NULL DEFAULT '',
  plan            text    NOT NULL DEFAULT 'custom',
  days            integer,
  features        jsonb   NOT NULL DEFAULT '[]'::jsonb,
  max_devices     integer NOT NULL DEFAULT 10,
  note            text    NOT NULL DEFAULT '',
  email           text    NOT NULL DEFAULT '',
  email_status    text    NOT NULL DEFAULT 'none'
                  CHECK (email_status IN ('none','queued','sent','failed')),
  email_error     text    NOT NULL DEFAULT '',
  email_sent_at   bigint,
  --  اگر برای پمپِ مشخصی صادر شده باشد، فقط همان خرجش می‌کند
  station_id      text,
  created_by      text    NOT NULL DEFAULT '',
  created_at      bigint  NOT NULL,
  expires_at      bigint,
  status          text    NOT NULL DEFAULT 'active'
                  CHECK (status IN ('active','used','revoked','expired')),
  used_at         bigint,
  used_by         text    NOT NULL DEFAULT '',
  used_station_id text    NOT NULL DEFAULT ''
);
CREATE INDEX IF NOT EXISTS idx_station_vip_status
  ON station_vip_codes(status, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_station_vip_email ON station_vip_codes(email);

-- ---------- پوشهٔ هر پمپ ----------
--
--  خواستهٔ صاحب مخزن: «برای هر حساب تو فولدرِ سرورها حسابِ طرف با
--  اطلاعات بره.» همان چیدمانی که سرورِ خانگی دارد
--  (`data/stations/<کد>/live.json`) اینجا هم هست، فقط به جای فایل،
--  ردیف — تا پشتیبان‌گیری، مهاجرت و تراکنش همان‌طور کار کند که برای
--  بقیهٔ جدول‌ها کار می‌کند.
--
--  `path` همان نامِ فایل است: live.json، inbox.json، station.json…
--  پس اگر فردا فایلِ تازه‌ای لازم شد، مهاجرتِ تازه لازم نیست.
--
--  ⚠️ این پوشه **نسخهٔ ابری** است، نه اصلِ داده. اصلِ دفترِ پمپ روی
--  کامپیوترِ خودِ پمپ می‌ماند — همان قاعدهٔ «برنامهٔ نیتیو اصلِ
--  اطلاعات است». اینجا برای آن است که گوشیِ کارمند از هر جای دنیا
--  ببیندش و اگر کامپیوتر سوخت، داده گم نشود.
CREATE TABLE IF NOT EXISTS station_files (
  station_id  text   NOT NULL REFERENCES stations(id) ON DELETE CASCADE,
  path        text   NOT NULL,
  data        jsonb  NOT NULL DEFAULT '{}'::jsonb,
  rev         bigint NOT NULL DEFAULT 0,
  size        integer NOT NULL DEFAULT 0,
  device_id   text   NOT NULL DEFAULT '',
  user_id     text   NOT NULL DEFAULT '',
  updated_at  bigint NOT NULL,
  PRIMARY KEY (station_id, path)
);
CREATE INDEX IF NOT EXISTS idx_station_files_rev ON station_files(station_id, rev);

-- ---------- شمارندهٔ تغییرِ هر پمپ ----------
CREATE TABLE IF NOT EXISTS station_rev (
  station_id  text   PRIMARY KEY REFERENCES stations(id) ON DELETE CASCADE,
  last_rev    bigint NOT NULL DEFAULT 0
);
