-- ---------- چتِ پشتیبانیِ مشتری با صاحبِ پمپ ----------
--  خواستهٔ صاحب مخزن: «داخلِ کیو‌آر که اسکن می‌زند و توی حسابش می‌رود، یک چتِ
--  پشتیبانی با من داشته باشد… عینِ واتساپ: اسمش دیده شود، پیام‌ها پاک شوند،
--  عکس و ویدیو و صدا بفرستد، بتوانم کسی را بلاک کنم، و حتی مرورگرش بسته
--  باشد پیام برایش برود.»
--
--  هر حسابِ کیو‌آردار (‎acct‎ = d12 / c9) یک گفت‌وگو دارد. مشتری با رمزِ همان
--  حساب (‎k‎ی کیو‌آر) می‌نویسد، صاحبِ پمپ با توکنِ دستگاه. رسانه در همین
--  دیتابیس می‌نشیند (bytea) تا پوشهٔ پمپ یک‌جا بماند و بکاپ همه‌چیز را ببرد.
CREATE TABLE IF NOT EXISTS station_chat_media (
  id          text    PRIMARY KEY,
  station_id  text    NOT NULL REFERENCES stations(id) ON DELETE CASCADE,
  acct        text    NOT NULL,
  mime        text    NOT NULL,
  size        integer NOT NULL,
  data        bytea   NOT NULL,
  created_at  bigint  NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_chat_media_acct ON station_chat_media(station_id, acct);

CREATE TABLE IF NOT EXISTS station_chat_messages (
  id          text    PRIMARY KEY,
  seq         bigserial,
  station_id  text    NOT NULL REFERENCES stations(id) ON DELETE CASCADE,
  acct        text    NOT NULL,
  --  c = مشتری، o = صاحبِ پمپ
  from_side   text    NOT NULL CHECK (from_side IN ('c','o')),
  name        text    NOT NULL DEFAULT '',
  kind        text    NOT NULL DEFAULT 'text' CHECK (kind IN ('text','image','video','audio')),
  text        text    NOT NULL DEFAULT '',
  media_id    text    REFERENCES station_chat_media(id) ON DELETE SET NULL,
  created_at  bigint  NOT NULL,
  deleted_at  bigint
);
CREATE INDEX IF NOT EXISTS idx_chat_msg_seq ON station_chat_messages(station_id, acct, seq);
CREATE INDEX IF NOT EXISTS idx_chat_msg_station_seq ON station_chat_messages(station_id, seq);

--  یک ردیف برای هر گفت‌وگو: نامِ مشتری، بلاک، تا کجا خوانده شده، و
--  اشتراک‌های پوشِ مرورگرش (چند دستگاه ⇒ چند اشتراک).
CREATE TABLE IF NOT EXISTS station_chat_threads (
  station_id      text    NOT NULL REFERENCES stations(id) ON DELETE CASCADE,
  acct            text    NOT NULL,
  name            text    NOT NULL DEFAULT '',
  blocked_at      bigint,
  owner_seen_seq  bigint  NOT NULL DEFAULT 0,
  cust_seen_seq   bigint  NOT NULL DEFAULT 0,
  push            jsonb   NOT NULL DEFAULT '[]'::jsonb,
  updated_at      bigint  NOT NULL,
  PRIMARY KEY (station_id, acct)
);
