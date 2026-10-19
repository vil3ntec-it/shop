-- ============================================================
--  بانکِ تصاویر — ورودِ گروهی با CSV و فایل (v3 ۳۴.۲، ۳۷.۳، IMG-BANK-01)
--
--  ۱) فایل‌ها یکی‌یکی به دسته بارگذاری می‌شوند (`image_batch_files`): هر
--     فایل جدا سنجیده می‌شود؛ خرابی یکی دسته را نمی‌خواباند.
--  ۲) ردیف‌های CSV پیش‌نمایش (dry run) و سپس ثبت می‌شوند؛ نتیجهٔ هر ردیف
--     (`image_batch_rows`) با کلیدِ (batch_id, row_no) یکتاست ⇒ تکرارِ همان
--     درخواست ردیف یا عکسِ دوم نمی‌سازد (retryِ امن).
--  ⛔ منبع و مجوزِ هر دسته ثبت می‌شود (IMG-10)؛ هیچ ردیفی مستقیم مرکزی
--     نمی‌شود — همه به فضای pending و صفِ تأییدِ مدیرِ ارشد می‌روند.
-- ============================================================

CREATE TABLE IF NOT EXISTS image_batches (
  id              text    PRIMARY KEY,
  status          text    NOT NULL DEFAULT 'open' CHECK (status IN ('open','paused','cancelled','done')),
  license_source  text    NOT NULL DEFAULT '',
  license_holder  text    NOT NULL DEFAULT '',
  license_type    text    NOT NULL DEFAULT '',
  license_status  text    NOT NULL DEFAULT 'unknown' CHECK (license_status IN ('unknown','licensed','restricted','denied')),
  commercial_use  boolean NOT NULL DEFAULT false,
  note            text    NOT NULL DEFAULT '',
  created_by      text    NOT NULL DEFAULT '',
  created_at      bigint  NOT NULL,
  updated_at      bigint  NOT NULL
);

CREATE TABLE IF NOT EXISTS image_batch_files (
  batch_id    text    NOT NULL REFERENCES image_batches(id),
  name        text    NOT NULL,
  blob_hash   text    NOT NULL DEFAULT '',
  status      text    NOT NULL CHECK (status IN ('ok','failed')),
  error       text    NOT NULL DEFAULT '',
  warnings    text    NOT NULL DEFAULT '[]',   -- نشانه‌های کیفیت (کوچک، ناقص، پسوندِ ناجور)
  orientation integer NOT NULL DEFAULT 0,
  created_at  bigint  NOT NULL,
  PRIMARY KEY (batch_id, name)
);

CREATE TABLE IF NOT EXISTS image_batch_rows (
  batch_id     text    NOT NULL REFERENCES image_batches(id),
  row_no       integer NOT NULL,
  status       text    NOT NULL CHECK (status IN ('ok','failed','duplicate','ambiguous','needs_review')),
  error        text    NOT NULL DEFAULT '',
  image_id     text    NOT NULL DEFAULT '',
  product_id   text    NOT NULL DEFAULT '',
  candidates   text    NOT NULL DEFAULT '[]',
  input        text    NOT NULL DEFAULT '{}',
  created_at   bigint  NOT NULL,
  PRIMARY KEY (batch_id, row_no)
);
