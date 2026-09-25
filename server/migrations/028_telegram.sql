-- ============================================================
--  باتِ تلگرامِ پمپ — هشدارِ «حسابِ قرض‌دار» و «مخزن» در تلگرام
--
--  خواستهٔ صاحب مخزن (۱۴۰۵/۰۷/۱۳): «بات تلگرام رو روی سرور بساز و برای
--  پمپ باشد و دیگه جای نشتی نکنه… هر حساب کاربری که روی سرور است و حساب
--  پمپ داره بتونن برن توی بات… حساب کاربری شونو وارد کنن و سرور پیدا کنه
--  و به اون حساب متصل بشه… و بتونه بات رو به گروه تلگرام اش وصل کنه.»
--
--  ⛔ فقط پمپ. کلیدِ خارجیِ هر ردیف به `stations` می‌خورد، پس هیچ ردیفی
--  نمی‌تواند به دکانی برسد و پاک شدنِ پمپ همه‌چیزش را می‌برد.
-- ============================================================

--  هر گفت‌وگوی تلگرام (خصوصی یا گروه) — و این‌که به کدام پمپ وصل است.
CREATE TABLE IF NOT EXISTS telegram_chats (
  --  شناسهٔ خودِ تلگرام؛ گروه‌ها منفی‌اند و از int32 بزرگ‌ترند، پس متن.
  chat_id        text    PRIMARY KEY,
  kind           text    NOT NULL DEFAULT 'private'
                 CHECK (kind IN ('private','group')),
  title          text    NOT NULL DEFAULT '',

  --  به کدام پمپ، و به دستِ کدام حساب. ⛔ هر دو با هم پر یا خالی‌اند.
  --  هشدار فقط وقتی می‌رود که همین حساب **هنوز** عضوِ فعالِ همین پمپ
  --  باشد (در زمانِ فرستادن سنجیده می‌شود، نه فقط هنگامِ وصل شدن).
  station_id     text    REFERENCES stations(id) ON DELETE CASCADE,
  user_id        text    REFERENCES users(id) ON DELETE CASCADE,
  linked_at      bigint,

  --  گفت‌وگوی وصل‌شدن: '' | 'email' | 'code'
  state          text    NOT NULL DEFAULT '',
  pending_email  text    NOT NULL DEFAULT '',

  --  سقفِ درخواستِ کد از یک گفت‌وگو — تا کسی از تلگرام صندوقِ ایمیلِ
  --  دیگری را پر نکند (سقفِ خودِ `otp` به‌ازای **مقصد** است، این به‌ازای **فرستنده**).
  code_requests  integer NOT NULL DEFAULT 0,
  window_at      bigint  NOT NULL DEFAULT 0,

  --  «فقط تمام‌شده‌ها»: گروهِ شلوغ «کم مانده» را نمی‌خواهد.
  only_out       boolean NOT NULL DEFAULT false,

  created_at     bigint  NOT NULL,
  updated_at     bigint  NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_telegram_chats_station
  ON telegram_chats(station_id) WHERE station_id IS NOT NULL;

--  نشانیِ یک‌بارمصرفِ «افزودن به گروه». خودِ نشانه هیچ‌جا نمی‌ماند، فقط هشش.
CREATE TABLE IF NOT EXISTS telegram_link_tokens (
  token_hash  text   PRIMARY KEY,
  station_id  text   NOT NULL REFERENCES stations(id) ON DELETE CASCADE,
  user_id     text   NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  expires_at  bigint NOT NULL,
  used_at     bigint,
  created_at  bigint NOT NULL
);

--  صفِ فرستادن. ⛔ «رفت» با «ساخته شد» یکی نیست: هر پیام تا وقتی تلگرام
--  «ok» نگفته این‌جا می‌ماند و دوباره تلاش می‌شود — قطعیِ اینترنتِ سرور
--  یا خاموشیِ تلگرام هشدار را گم نمی‌کند.
CREATE TABLE IF NOT EXISTS telegram_outbox (
  id          text    PRIMARY KEY,
  chat_id     text    NOT NULL,
  station_id  text    REFERENCES stations(id) ON DELETE CASCADE,
  body        text    NOT NULL,
  attempts    integer NOT NULL DEFAULT 0,
  next_at     bigint  NOT NULL,
  sent_at     bigint,
  failed_at   bigint,
  error       text    NOT NULL DEFAULT '',
  created_at  bigint  NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_telegram_outbox_due
  ON telegram_outbox(next_at) WHERE sent_at IS NULL AND failed_at IS NULL;
