-- S9 (docs/MARKETPLACE-CONTRACT-fa.md §۳): گفت‌وگوی مشتری ↔ دکان به حسابِ مشتری (`store_customers.id`) گره می‌خورد.
--
-- NULL = گفت‌وگوی بی‌حساب، با کلیدِ `customer_phone` مثلِ امروز (همهٔ ردیف‌های پیشین).
-- پر   = مالِ همین حساب. فقط پشتِ پرچمِ `market.account` پر می‌شود (نشستِ حساب در چتِ ویترین، یا تغییرِ شماره)؛
--        پرچمِ خاموش ⇒ این ستون همیشه NULL می‌ماند و رفتار همان امروز است.
-- `customer_phone` برای سازگاری می‌ماند و عوض نمی‌شود: شمارهٔ زمانِ ساختنِ گفت‌وگو و کلیدِ یکتاییِ امروز است.
-- پاک کردنِ حساب ⇒ NULL (گفت‌وگو سندِ دکان است و می‌ماند).
--
-- فقط افزودنی — هیچ داده‌ای عوض نمی‌شود.
-- برگشت (آزموده در test/market-account.test.js):
--   DROP INDEX IF EXISTS ix_shop_chat_conv_customer;
--   ALTER TABLE shop_chat_conversations DROP COLUMN IF EXISTS customer_id;
ALTER TABLE shop_chat_conversations
  ADD COLUMN IF NOT EXISTS customer_id text REFERENCES store_customers(id) ON DELETE SET NULL;
CREATE INDEX IF NOT EXISTS ix_shop_chat_conv_customer
  ON shop_chat_conversations(customer_id, shop_id) WHERE customer_id IS NOT NULL;
