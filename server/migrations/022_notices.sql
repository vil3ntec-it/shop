-- ============================================================
--  مرکزِ اعلان — بخشِ ۱۱.۳.۳ی پرامپت
--
--  تا امروز تنها راهِ خبر دادن به مشتری‌ها «پیامِ همگانی» بود: یک متن،
--  در چتِ پشتیبانی، بی گزارش. مدیر «۴۲ نفر» می‌دید و نمی‌دانست کدامشان
--  خواندند، کدامشان ایمیل گرفتند و کدامشان نگرفتند.
--
--  سه جدول:
--    notices            خودِ اعلان: گیرنده (فیلتر)، کانال‌ها، متن، زمان‌بندی
--    notice_deliveries  یک ردیف برای هر گیرنده در هر کانال = گزارشِ ارسال
--    notice_templates   قالب‌های آماده، قابلِ ویرایش از پنل
--
--  ⛔ همهٔ زمان‌ها epoch میلی‌ثانیه‌اند، مثلِ بقیهٔ سرور.
-- ============================================================

CREATE TABLE IF NOT EXISTS notices (
  id            text   PRIMARY KEY,
  --  shop | pump | both — «both» یعنی گیرنده‌ها در هر دو دفتر پیدا می‌شوند
  app           text   NOT NULL DEFAULT 'shop' CHECK (app IN ('shop','pump','both')),
  --  {kind: all|app|filter|user, city, plan, expiring_days, expired,
  --   permanent, user_id, tenant_id}
  audience      jsonb  NOT NULL DEFAULT '{"kind":"all"}'::jsonb,
  --  ["inapp","push","email"] — هر ترکیبی
  channels      jsonb  NOT NULL DEFAULT '["inapp"]'::jsonb,
  title         text   NOT NULL DEFAULT '',
  body          text   NOT NULL DEFAULT '',
  --  اگر از قالب ساخته شده باشد، کلیدش — فقط برای ردگیری
  template_key  text   NOT NULL DEFAULT '',
  --  متغیرهای ثابتِ این اعلان، مثلِ {"کد-تخفیف":"EID20"}
  variables     jsonb  NOT NULL DEFAULT '{}'::jsonb,
  schedule_at   bigint,
  repeat        text   NOT NULL DEFAULT 'none' CHECK (repeat IN ('none','monthly')),
  status        text   NOT NULL DEFAULT 'draft'
                CHECK (status IN ('draft','scheduled','sending','sent','failed')),
  --  اعلانِ خودکارِ سامانه (رو به پایان، منقضی، تمدید) با این نشان جدا می‌شود
  system        boolean NOT NULL DEFAULT false,
  created_by    text   NOT NULL DEFAULT '',
  created_at    bigint NOT NULL,
  updated_at    bigint NOT NULL,
  sent_at       bigint,
  --  چند بار فرستاده شده (برای تکرارِ ماهانه)
  runs          integer NOT NULL DEFAULT 0,
  --  {recipients, sent, error, read} — خلاصهٔ آخرین ارسال
  counts        jsonb  NOT NULL DEFAULT '{}'::jsonb
);
CREATE INDEX IF NOT EXISTS idx_notices_status   ON notices(status, schedule_at);
CREATE INDEX IF NOT EXISTS idx_notices_created  ON notices(created_at DESC);

CREATE TABLE IF NOT EXISTS notice_deliveries (
  id           text   PRIMARY KEY,
  notice_id    text   NOT NULL REFERENCES notices(id) ON DELETE CASCADE,
  --  shop | pump — هر گیرنده مالِ یک بخش است، حتی وقتی اعلان «both» است
  app          text   NOT NULL DEFAULT 'shop' CHECK (app IN ('shop','pump')),
  user_id      text   NOT NULL DEFAULT '',
  --  شناسهٔ دکان یا پمپ — بی این، پمپی که صاحب ندارد از گزارش می‌افتاد
  tenant_id    text   NOT NULL DEFAULT '',
  channel      text   NOT NULL CHECK (channel IN ('inapp','push','email')),
  status       text   NOT NULL DEFAULT 'queued'
               CHECK (status IN ('queued','sent','delivered','read','error')),
  --  نام و نشانی، همان لحظهٔ ارسال — تا گزارش بعد از عوض شدنِ ایمیل هم درست بماند
  who          text   NOT NULL DEFAULT '',
  address      text   NOT NULL DEFAULT '',
  --  متنِ نهایی با متغیرهای پرشده — همان چیزی که گیرنده دید
  title        text   NOT NULL DEFAULT '',
  body         text   NOT NULL DEFAULT '',
  error        text   NOT NULL DEFAULT '',
  --  شمارهٔ اجرا (اعلانِ ماهانه چند بار می‌رود)
  run          integer NOT NULL DEFAULT 1,
  created_at   bigint NOT NULL,
  sent_at      bigint,
  delivered_at bigint,
  read_at      bigint
);
CREATE INDEX IF NOT EXISTS idx_ndeliv_notice ON notice_deliveries(notice_id, created_at DESC);
--  «اعلان‌های من» در برنامه: به کاربر یا به دکان/پمپ
CREATE INDEX IF NOT EXISTS idx_ndeliv_user   ON notice_deliveries(app, user_id, created_at DESC)
  WHERE channel = 'inapp';
CREATE INDEX IF NOT EXISTS idx_ndeliv_tenant ON notice_deliveries(app, tenant_id, created_at DESC)
  WHERE channel = 'inapp';

CREATE TABLE IF NOT EXISTS notice_templates (
  key         text   NOT NULL,
  --  shop | pump | both — قالبِ «both» برای هر دو بخش است
  app         text   NOT NULL DEFAULT 'both' CHECK (app IN ('shop','pump','both')),
  title       text   NOT NULL DEFAULT '',
  body        text   NOT NULL DEFAULT '',
  --  کانال‌های پیش‌فرضِ قالب
  channels    jsonb  NOT NULL DEFAULT '["inapp","email"]'::jsonb,
  editable    boolean NOT NULL DEFAULT true,
  updated_at  bigint NOT NULL,
  PRIMARY KEY (key, app)
);

--  شهرِ کاربر — فیلترِ «همهٔ مشتری‌های کابل». تا امروز فقط لوکیشنِ
--  عددی داشتیم؛ فیلترِ شهر با عدد نمی‌شود. مدیر از پنل پرش می‌کند و
--  اگر خالی بود، برچسبِ آخرین لوکیشن هم سنجیده می‌شود.
ALTER TABLE users ADD COLUMN IF NOT EXISTS city text NOT NULL DEFAULT '';
