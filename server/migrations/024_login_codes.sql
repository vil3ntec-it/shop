-- ============================================================
--  ورود با کدِ شش‌رقمیِ ایمیلی، مخصوصِ هر برنامه — پرامپتِ «ورود بی‌نقص»
--
--  همان کلیدهایی که پرامپت در Redis می‌گذاشت، این‌جا ردیف‌اند با ستونِ
--  انقضا. کامپیوترِ خانگیِ صاحبِ سامانه ویندوز است و Redis ندارد؛ و
--  «یک سرور، یک دفتر» یعنی همین PostgreSQL/PGlite. معناها عیناً همان:
--
--    otp:req      ⇒ login_requests   (کد فقط هش؛ نسخهٔ مهروموم‌شده برای
--                                     خودِ ارسال و «نمایش به مدیر»، و با
--                                     انقضا پاک می‌شود)
--    otp:status   ⇒ otp_outbox       (صفِ ارسال: queued→sending→sent|failed)
--    otp:lock     ⇒ login_locks      (۹۰۰ ثانیه بعد از ۵ کدِ غلط)
--    otp:resend · rl:ip · rl:email    ⇒ شمارشِ همین ردیف‌ها در پنجرهٔ زمانی
--    refresh:grace                    ⇒ دو ستونِ تازه روی `tokens`
--
--  ⚠️ کدِ خام هیچ‌جا نمی‌نشیند و در هیچ لاگی نوشته نمی‌شود.
-- ============================================================

CREATE TABLE IF NOT EXISTS login_requests (
  request_id        text    PRIMARY KEY,
  app               text    NOT NULL,
  email             text    NOT NULL,
  --  HMAC(app \n email \n code) — کدِ یک برنامه در برنامهٔ دیگر بی‌معناست
  code_hash         text    NOT NULL,
  --  AES-GCM با کلیدِ مشتق از رازِ سرور؛ فقط تا انقضا می‌ماند
  code_sealed       text    NOT NULL DEFAULT '',
  attempts          integer NOT NULL DEFAULT 0,
  max_attempts      integer NOT NULL DEFAULT 5,
  created_at        bigint  NOT NULL,
  expires_at        bigint  NOT NULL,
  consumed_at       bigint,
  --  کدِ تازه‌تری صادر شد ⇒ این یکی دیگر معتبر نیست («فقط آخرین کد»)
  superseded_at     bigint,
  device_id         text    NOT NULL DEFAULT '',
  device_name       text    NOT NULL DEFAULT '',
  app_version       text    NOT NULL DEFAULT '',
  ip                text    NOT NULL DEFAULT '',
  client_request_id text    NOT NULL DEFAULT '',
  revealed_at       bigint,
  revealed_by       text    NOT NULL DEFAULT ''
);
CREATE INDEX IF NOT EXISTS idx_login_requests_email ON login_requests(app, email, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_login_requests_ip    ON login_requests(ip, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_login_requests_at    ON login_requests(created_at DESC);

CREATE TABLE IF NOT EXISTS otp_outbox (
  --  همان request_id — و همان کلیدِ Idempotency که به سرویسِ ایمیل می‌رود
  id              text    PRIMARY KEY REFERENCES login_requests(request_id) ON DELETE CASCADE,
  app             text    NOT NULL,
  email           text    NOT NULL,
  status          text    NOT NULL DEFAULT 'queued' CHECK (status IN ('queued','sending','sent','failed')),
  attempts        integer NOT NULL DEFAULT 0,
  max_attempts    integer NOT NULL DEFAULT 4,
  next_attempt_at bigint  NOT NULL DEFAULT 0,
  locked_at       bigint,
  sent_at         bigint,
  failed_at       bigint,
  --  email_service_timeout | email_service_error | invalid_recipient | expired
  reason          text    NOT NULL DEFAULT '',
  last_error      text    NOT NULL DEFAULT '',
  created_at      bigint  NOT NULL,
  updated_at      bigint  NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_otp_outbox_due ON otp_outbox(status, next_attempt_at);
CREATE INDEX IF NOT EXISTS idx_otp_outbox_at  ON otp_outbox(created_at DESC);

CREATE TABLE IF NOT EXISTS login_locks (
  app           text    NOT NULL,
  email         text    NOT NULL,
  locked_until  bigint  NOT NULL,
  reason        text    NOT NULL DEFAULT 'too_many_wrong_codes',
  created_at    bigint  NOT NULL,
  PRIMARY KEY (app, email)
);

--  Refresh چرخشی با مهلتِ ۳۰ ثانیه: توکنِ قبلی می‌گوید جانشینش کیست و
--  تا کِی هنوز پذیرفته می‌شود (دو Refreshِ هم‌زمان کسی را بیرون نیندازد)
ALTER TABLE tokens ADD COLUMN IF NOT EXISTS rotated_to  text;
ALTER TABLE tokens ADD COLUMN IF NOT EXISTS grace_until bigint;
