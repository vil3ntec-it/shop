-- ============================================================
--  چتِ مشتری ↔ دکان — هسته (v3 §۳۳؛ CHAT-01…15)
--
--  جدولِ تازه؛ هیچ جدولِ موجودی (پشتیبانی، چتِ پمپ، رلهٔ ۱۵روزه)
--  دست نمی‌خورد. تا پرچمِ `shop.chat` در «تنظیماتِ زنده» روشن نشود هیچ
--  ردیفی در این‌ها نوشته نمی‌شود.
--
--  ⛔ سرور منبعِ پیام است، نه سوکت (۳۳.۲ بندِ ۷): هر پیام شمارهٔ ترتیبیِ
--  `seq` (ترتیبِ نمایش) و `rev` (مکانِ تغییر برای همگام‌سازی) دارد. هر
--  درج، ویرایش، حذف و «حذف برای من» `rev` ِ تازه می‌گیرد ⇒ دستگاهِ آفلاین
--  با `after=<آخرین rev>` سنگِ قبرِ حذف را هم می‌گیرد (CHAT-08).
--  ⛔ تکرارِ همان ارسال پیامِ دوم نمی‌سازد: (گفت‌وگو، طرف، client_id) یکتاست
--  (CHAT-02، CHAT-11).
--  ⛔ رسید فقط با رویدادِ واقعیِ گیرنده جلو می‌رود: `*_delivered_seq` و
--  `*_read_seq` آب‌خط‌اند و هرگز عقب نمی‌روند (CHAT-04).
--  ⛔ شمارهٔ مشتری فقط برای کاربرِ مجازِ دکان کامل دیده می‌شود؛ مشتری هیچ
--  شناسه یا شماره‌ای از کارمند نمی‌بیند (§۳۳.۳).
-- ============================================================

CREATE TABLE IF NOT EXISTS shop_chat_conversations (
  id                      text    PRIMARY KEY,
  shop_id                 text    NOT NULL REFERENCES shops(id) ON DELETE CASCADE,
  --  شمارهٔ تأییدشدهٔ مشتری (همان قالبِ `validate.phone`: ‎+93…‎)
  customer_phone          text    NOT NULL,
  customer_name           text    NOT NULL DEFAULT '',
  --  '' = گفت‌وگوی عمومی با دکان؛ پر = سفارشِ آنلاینِ همین دکان و همین شماره
  order_id                text    NOT NULL DEFAULT '',
  status                  text    NOT NULL DEFAULT 'open' CHECK (status IN ('open','closed')),
  seq                     bigint  NOT NULL DEFAULT 0,
  rev                     bigint  NOT NULL DEFAULT 0,
  shop_delivered_seq      bigint  NOT NULL DEFAULT 0,
  shop_read_seq           bigint  NOT NULL DEFAULT 0,
  customer_delivered_seq  bigint  NOT NULL DEFAULT 0,
  customer_read_seq       bigint  NOT NULL DEFAULT 0,
  last_message_at         bigint,
  last_sender             text    NOT NULL DEFAULT '',
  created_at              bigint  NOT NULL,
  updated_at              bigint  NOT NULL,
  UNIQUE (shop_id, customer_phone, order_id)
);
CREATE INDEX IF NOT EXISTS idx_shop_chat_conv_inbox ON shop_chat_conversations(shop_id, updated_at DESC, id DESC);

CREATE TABLE IF NOT EXISTS shop_chat_messages (
  id               text    PRIMARY KEY,
  conversation_id  text    NOT NULL REFERENCES shop_chat_conversations(id) ON DELETE CASCADE,
  --  همان دکانِ گفت‌وگو — برای جداسازیِ مستأجر در هر پرسش
  shop_id          text    NOT NULL,
  seq              bigint  NOT NULL,
  rev              bigint  NOT NULL,
  sender           text    NOT NULL CHECK (sender IN ('customer','shop')),
  --  کارمندِ فرستنده — هرگز به مشتری نشان داده نمی‌شود
  sender_user_id   text    NOT NULL DEFAULT '',
  client_id        text    NOT NULL,
  request_hash     text    NOT NULL,
  kind             text    NOT NULL CHECK (kind IN ('text','image','audio','file')),
  body             text    NOT NULL DEFAULT '',
  reply_to         text,
  media_id         text,
  thumb_id         text,
  --  {durationMs, width, height} — فقط عددِ بازه‌دار
  meta             jsonb   NOT NULL DEFAULT '{}'::jsonb,
  edited_at        bigint,
  deleted_at       bigint,
  deleted_by       text    NOT NULL DEFAULT '',
  created_at       bigint  NOT NULL,
  UNIQUE (conversation_id, seq),
  UNIQUE (conversation_id, sender, client_id)
);
CREATE INDEX IF NOT EXISTS idx_shop_chat_msg_rev   ON shop_chat_messages(conversation_id, rev);
CREATE INDEX IF NOT EXISTS idx_shop_chat_msg_shop  ON shop_chat_messages(shop_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_shop_chat_msg_media ON shop_chat_messages(media_id) WHERE media_id IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_shop_chat_msg_thumb ON shop_chat_messages(thumb_id) WHERE thumb_id IS NOT NULL;

--  «حذف برای من» — فقط در نمای همان بیننده (CHAT-08).
--  viewer: 'customer' (همهٔ دستگاه‌های همان شماره) یا 'user:<شناسهٔ کارمند>'
CREATE TABLE IF NOT EXISTS shop_chat_hidden (
  message_id       text    NOT NULL REFERENCES shop_chat_messages(id) ON DELETE CASCADE,
  viewer           text    NOT NULL,
  created_at       bigint  NOT NULL,
  PRIMARY KEY (message_id, viewer)
);

--  بلاک به‌ازای (دکان، شماره) — گفت‌وگوی تازهٔ همان شماره هم بسته می‌ماند.
--  blocker: 'shop' (دکان مشتری را بست) یا 'customer' (مشتری دکان را بست)
CREATE TABLE IF NOT EXISTS shop_chat_blocks (
  shop_id          text    NOT NULL REFERENCES shops(id) ON DELETE CASCADE,
  customer_phone   text    NOT NULL,
  blocker          text    NOT NULL CHECK (blocker IN ('shop','customer')),
  by_user_id       text    NOT NULL DEFAULT '',
  created_at       bigint  NOT NULL,
  PRIMARY KEY (shop_id, customer_phone, blocker)
);
