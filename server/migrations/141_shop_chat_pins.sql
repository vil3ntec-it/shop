-- ============================================================
--  چتِ مشتری ↔ دکان — سنجاقِ پیام (v3 §۳۳.۱؛ CHAT-14)
--
--  ⛔ هر طرف سنجاقِ خودش را دارد: side = 'shop' (همهٔ کارمندانِ همان دکان
--  می‌بینند) یا 'customer' (فقط همان مشتری). طرفِ دیگر سنجاق را نمی‌بیند.
--  ⛔ فقط پیامِ همین گفت‌وگو؛ «حذف برای همه» سنجاق را در همان تراکنش
--  برمی‌دارد و پاک شدنِ پیام (نگه‌داری) آن را با ON DELETE CASCADE می‌برد.
--  ⛔ سقفِ شمار در `lib/shop-chat.js` (MAX_PINS) زیرِ قفلِ ردیفِ گفت‌وگو.
--
--  فقط افزودنی — هیچ داده‌ای عوض نمی‌شود. پشتِ همان پرچمِ خاموشِ `shop.chat`.
--  برگشت (آزموده در test/shop-chat-pins.test.js؛ پیش از آن revert ِ کد):
--    DROP TABLE IF EXISTS shop_chat_pins;
-- ============================================================

CREATE TABLE IF NOT EXISTS shop_chat_pins (
  conversation_id    text    NOT NULL REFERENCES shop_chat_conversations(id) ON DELETE CASCADE,
  message_id         text    NOT NULL REFERENCES shop_chat_messages(id) ON DELETE CASCADE,
  side               text    NOT NULL CHECK (side IN ('customer','shop')),
  shop_id            text    NOT NULL,
  --  کارمندی که سنجاق کرد — هرگز به مشتری نشان داده نمی‌شود
  pinned_by_user_id  text    NOT NULL DEFAULT '',
  created_at         bigint  NOT NULL,
  PRIMARY KEY (conversation_id, side, message_id)
);
CREATE INDEX IF NOT EXISTS idx_shop_chat_pins_message ON shop_chat_pins(message_id);
