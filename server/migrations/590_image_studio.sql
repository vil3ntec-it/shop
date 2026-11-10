-- ============================================================
--  ۵۹۰ — استودیوی عکسِ کالا: صفِ پردازشِ گروهی روی سرورِ خودمان (AI-02، AI-05، IMG-PRO-14؛ ۱۴۰۵/۰۸/۲۰)
--
--  هر «دسته» یک درخواستِ دکان است (یک تا ۱۰۰۰ عکسِ خودِ دکان، یک preset)؛ هر «کار» یک عکس.
--  خروجی نسخهٔ تازهٔ همان عکس در `image_versions` (kind = 'processed'، review = 'needs_review') است؛
--  اصل هرگز دست نمی‌خورد و هیچ نسخه‌ای پیش از تأییدِ دکان انتخاب‌شدنی نیست (‎lib/image-bank.js‎ ‎selectIn‎).
--
--  ⛔ یکتایی در خودِ پایگاه:
--    • (shop_id, idem_key) ِ دسته ⇒ همان درخواستِ دوباره همان دسته است، نه دستهٔ دوم.
--    • (batch_id, image_id) ⇒ هر عکس یک بار در هر دسته.
--    • `result_version` فقط در همان تراکنشی نوشته می‌شود که نسخه را می‌سازد و فقط با همان `lease` ِ کار
--      (‎lib/image-studio-jobs.js‎)؛ کارِ تکراری با همان `digest` (همان عکس، نسخه و تنظیمات) خروجیِ پیشین را
--      دوباره به کار می‌برد ⇒ تلاشِ دوباره هرگز نسخهٔ دوم نمی‌سازد.
--  ⛔ پیش‌فرض خاموش: ردیف فقط با پرچمِ `shop.image_studio` (و `shop.image_bank`) نوشته می‌شود.
--  فقط افزودنی (دو جدولِ تازه). rollback: migrations/rollback/590_image_studio.down.sql
-- ============================================================

CREATE TABLE IF NOT EXISTS image_studio_batches (
  id              text    PRIMARY KEY,
  shop_id         text    NOT NULL,
  idem_key        text    NOT NULL,
  request_digest  text    NOT NULL,
  preset          text    NOT NULL CHECK (preset IN ('catalog','enhance','ad')),
  options         text    NOT NULL DEFAULT '{}',
  state           text    NOT NULL DEFAULT 'active' CHECK (state IN ('active','paused','cancelled')),
  total           integer NOT NULL CHECK (total BETWEEN 1 AND 1000),
  created_by      text    NOT NULL DEFAULT '',
  created_at      bigint  NOT NULL,
  updated_at      bigint  NOT NULL,
  UNIQUE (shop_id, idem_key)
);
CREATE INDEX IF NOT EXISTS image_studio_batches_shop ON image_studio_batches (shop_id, created_at DESC);

CREATE TABLE IF NOT EXISTS image_studio_jobs (
  id              text    PRIMARY KEY,
  batch_id        text    NOT NULL REFERENCES image_studio_batches(id),
  shop_id         text    NOT NULL,
  image_id        text    NOT NULL REFERENCES images(id),
  source_version  integer NOT NULL,
  digest          text    NOT NULL,
  status          text    NOT NULL DEFAULT 'queued' CHECK (status IN ('queued','running','done','failed','cancelled')),
  attempts        integer NOT NULL DEFAULT 0,
  next_at         bigint  NOT NULL DEFAULT 0,
  lease           text    NOT NULL DEFAULT '',
  lease_until     bigint  NOT NULL DEFAULT 0,
  result_version  integer NOT NULL DEFAULT 0,
  reused          boolean NOT NULL DEFAULT false,
  result          text    NOT NULL DEFAULT '{}',
  error           text    NOT NULL DEFAULT '',
  created_at      bigint  NOT NULL,
  updated_at      bigint  NOT NULL,
  UNIQUE (batch_id, image_id)
);
--  برداشتنِ کارِ وقت‌رسیده (صف و اجاره‌های تمام‌شده) و شمارِ «در کار» ِ هر دکان
CREATE INDEX IF NOT EXISTS image_studio_jobs_due ON image_studio_jobs (status, next_at);
CREATE INDEX IF NOT EXISTS image_studio_jobs_shop ON image_studio_jobs (shop_id, status);
--  خروجیِ آماده برای همان کار (همان عکس، نسخه و تنظیمات) ⇒ دوباره به کار می‌رود
CREATE INDEX IF NOT EXISTS image_studio_jobs_digest ON image_studio_jobs (shop_id, digest) WHERE status = 'done';
