-- ============================================================
--  945 — CHK-10: پیامِ خودکارِ «رسیدیم» به ایمیلِ تأییدشدهٔ «خبرم کن» ِ بیرونِ محدوده (`lib/storefront-area-email.js`،
--  پرچمِ خاموشِ `shop.storefront.area_email`، روی `shop.storefront.notify_area` و `shop.storefront.zones`).
--
--  همان ردیفِ `storefront_area_requests` (مهاجرتِ ۱۷۶، کلیدِ مشتری از ۸۸۱) حالِ نامه را نگه می‌دارد، نه دفترِ دوم:
--    lang    — زبانِ ویترین هنگامِ درخواست (fa، ps، en) برای متنِ نامه.
--    notice  — '' منتظر · sending در راه (گرفته‌شده؛ دوباره گرفته نمی‌شود) · sent رفت = «انجام شد» (دیگر در فهرستِ دکان
--              نیست) · failed نرفت یا مبهم (دوباره نه؛ دکان همان راهِ دستیِ «تماس»/«خبر دادم» را دارد).
--    tried   — همان شکلِ `tried` ِ صندوق‌های اعلان (کانال، نتیجه، `costMicros`) برای سقفِ مشترکِ هزینهٔ NTF-02.
--    sent_at — زمانِ «پذیرفت» ِ سرویسِ ایمیل (سقفِ روزانهٔ هر دکان و هر گیرنده).
--  ⛔ درخواستِ دوبارهٔ همان مشتری (`request`) حال را به '' برمی‌گرداند. پرچمِ خاموش ⇒ ستون‌ها همان پیش‌فرض.
--  بازگشت: `rollback/945_storefront_area_email.down.sql`.
-- ============================================================
ALTER TABLE storefront_area_requests ADD COLUMN IF NOT EXISTS lang text NOT NULL DEFAULT 'fa'
  CHECK (lang IN ('fa', 'ps', 'en'));
ALTER TABLE storefront_area_requests ADD COLUMN IF NOT EXISTS notice text NOT NULL DEFAULT ''
  CHECK (notice IN ('', 'sending', 'sent', 'failed'));
ALTER TABLE storefront_area_requests ADD COLUMN IF NOT EXISTS tried jsonb NOT NULL DEFAULT '[]'::jsonb;
ALTER TABLE storefront_area_requests ADD COLUMN IF NOT EXISTS sent_at bigint;
--  منتظرها (کارگر) و رفته‌های ۲۴ ساعت (سقف‌ها)
CREATE INDEX IF NOT EXISTS ix_storefront_area_requests_waiting ON storefront_area_requests(shop_id) WHERE notice = '';
CREATE INDEX IF NOT EXISTS ix_storefront_area_requests_sent ON storefront_area_requests(sent_at) WHERE notice = 'sent';
