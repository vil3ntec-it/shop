-- ============================================================
--  اعتماد، شکایت و تخلف در بازارچه — پرونده، تاریخچه و مدرک
--  (v3 §۳۸.۶، سناریوی ABUSE-01؛ RSK-07/08 در محدودهٔ شکایت)
--
--  پرچم ── `market.trust` در «تنظیماتِ زنده» (scope = شناسهٔ دکان یا `all`).
--          ⛔ پیش‌فرض خاموش ⇒ درهای مشتری و دکان ۴۰۴ و هیچ ردیفی این‌جا
--          ساخته نمی‌شود.
--
--  ⛔ هویتِ گزارش‌دهنده هرگز در این جدول‌ها نیست: نه شماره، نه IP، نه
--  بلیت. فقط HMAC ِ کلیددار (`reporter_key`، `ip_key`) برای سقف و تکرار، و
--  یک نشانهٔ پوشیده (`reporter_hint`) که فقط مدیرِ ارشد می‌بیند. دکان هیچ‌کدام
--  را نمی‌بیند (نمای دکان فقط از فهرستِ سفید ساخته می‌شود).
--  ⛔ توکنِ پیگیری ذخیره نمی‌شود: HMAC ِ (دکان، پرونده) است و هر بار دوباره
--  ساخته و زمان‌ثابت سنجیده می‌شود (همان الگوی توکنِ سفارشِ ویترین).
--  ⛔ برچسب‌های خودکار (`flags`: متنِ یکسان، IP ِ یکسان) فقط نشانه‌اند،
--  حکم نیستند (§۳۸.۶ بندِ ۲): تصمیم فقط با مدیرِ انسانی و دلیلِ نوشته.
-- ============================================================

CREATE TABLE IF NOT EXISTS trust_cases (
  id                  text    PRIMARY KEY,
  shop_id             text    NOT NULL REFERENCES shops(id) ON DELETE CASCADE,
  --  complaint = شکایت از سفارش یا دکان؛ report = گزارشِ کالا/آگهیِ ویترین
  kind                text    NOT NULL CHECK (kind IN ('complaint','report')),
  subject             text    NOT NULL CHECK (subject IN ('order','shop','product')),
  order_id            text    NOT NULL DEFAULT '',
  order_no            bigint,
  product_id          text    NOT NULL DEFAULT '',
  category            text    NOT NULL,
  severity            text    NOT NULL CHECK (severity IN ('low','medium','high')),
  body                text    NOT NULL DEFAULT '',
  status              text    NOT NULL DEFAULT 'open'
                      CHECK (status IN ('open','in_review','decided','appealed','closed')),
  --  مسئولِ رسیدگی (شناسهٔ مدیر)
  assignee            text    NOT NULL DEFAULT '',
  outcome             text    NOT NULL DEFAULT '' CHECK (outcome IN ('','upheld','dismissed')),
  decision_reason     text    NOT NULL DEFAULT '',
  decided_by          text    NOT NULL DEFAULT '',
  decided_at          bigint,
  appeal_body         text    NOT NULL DEFAULT '',
  appealed_by         text    NOT NULL DEFAULT '',
  appealed_at         bigint,
  appeal_outcome      text    NOT NULL DEFAULT '' CHECK (appeal_outcome IN ('','upheld','reversed')),
  appeal_reason       text    NOT NULL DEFAULT '',
  appeal_decided_by   text    NOT NULL DEFAULT '',
  appeal_decided_at   bigint,
  reporter_key        text    NOT NULL,
  reporter_hint       text    NOT NULL DEFAULT '',
  ip_key              text    NOT NULL DEFAULT '',
  body_hash           text    NOT NULL DEFAULT '',
  duplicate_of        text    NOT NULL DEFAULT '',
  flags               jsonb   NOT NULL DEFAULT '[]'::jsonb,
  created_at          bigint  NOT NULL,
  updated_at          bigint  NOT NULL,
  CHECK ((subject = 'product') = (kind = 'report')),
  CHECK ((subject = 'order') = (order_id <> '')),
  CHECK ((subject = 'product') = (product_id <> ''))
);
CREATE INDEX IF NOT EXISTS ix_trust_cases_shop ON trust_cases(shop_id, created_at DESC, id);
CREATE INDEX IF NOT EXISTS ix_trust_cases_queue ON trust_cases(status, created_at, id);
CREATE INDEX IF NOT EXISTS ix_trust_cases_reporter ON trust_cases(reporter_key, created_at);
CREATE INDEX IF NOT EXISTS ix_trust_cases_ip ON trust_cases(ip_key, created_at);
--  تکرار: یک پروندهٔ باز برای هر (گزارش‌دهنده، دکان، موضوع) — درخواستِ هم‌زمانِ
--  دوم ردیفِ دوم نمی‌سازد و همان پرونده را برمی‌گرداند.
CREATE UNIQUE INDEX IF NOT EXISTS ux_trust_cases_open
  ON trust_cases(reporter_key, shop_id, kind, subject, order_id, product_id)
  WHERE status IN ('open','in_review');

--  تاریخچهٔ پرونده — فقط افزودنی. نمای هر طرف از روی `type` ساخته می‌شود.
CREATE TABLE IF NOT EXISTS trust_case_events (
  id                  text    PRIMARY KEY,
  case_id             text    NOT NULL REFERENCES trust_cases(id) ON DELETE CASCADE,
  shop_id             text    NOT NULL,
  actor               text    NOT NULL CHECK (actor IN ('customer','shop','admin','system')),
  --  شناسهٔ کاربرِ دکان یا مدیر؛ برای مشتری همیشه خالی
  actor_id            text    NOT NULL DEFAULT '',
  type                text    NOT NULL,
  body                text    NOT NULL DEFAULT '',
  data                jsonb   NOT NULL DEFAULT '{}'::jsonb,
  created_at          bigint  NOT NULL
);
CREATE INDEX IF NOT EXISTS ix_trust_case_events_case ON trust_case_events(case_id, created_at, id);

--  مدرک (عکس) — نوع از روی بایت‌ها، EXIF ِ مکان پیش از ذخیره پاک (همان
--  `chat-media`). همان بایت‌ها در همان پرونده یک ردیف‌اند.
CREATE TABLE IF NOT EXISTS trust_evidence (
  id                  text    PRIMARY KEY,
  case_id             text    NOT NULL REFERENCES trust_cases(id) ON DELETE CASCADE,
  shop_id             text    NOT NULL,
  mime                text    NOT NULL,
  size                integer NOT NULL,
  sha256              text    NOT NULL,
  data                bytea   NOT NULL,
  created_at          bigint  NOT NULL,
  UNIQUE (case_id, sha256)
);
