-- ============================================================
--  دفترِ مصرفِ کدِ تخفیف — PRM-02 (بخشِ ۶.۴)، پشتِ پرچمِ خاموشِ `shop.promo_uses`
--
--  هر ردیف = یک کد روی یک فاکتور. «مصرف» ِ هر قاعده = شمارِ ردیف‌های آزادنشده؛
--  سقف (`maxUses`) با قفلِ ردیفِ قاعده (`FOR UPDATE`) در همان تراکنش سنجیده
--  می‌شود، پس دو صندوق هم‌زمان آخرین ظرفیت را دو بار نمی‌گیرند.
--
--  ⛔ کلیدِ اصلی (دکان، قاعده، فاکتور): تلاشِ دوبارهٔ همان فاکتور مصرفِ دوم نمی‌سازد.
--  ⛔ آزاد کردن نرم است (`released_at`)؛ ردیف پاک نمی‌شود — سابقهٔ «کدِ مصرف‌شده» می‌ماند.
--  ⛔ shop_id فقط از عضویتِ کاربرِ توکن می‌آید؛ قاعدهٔ دکانِ دیگر با کلیدِ خارجی هم بسته است.
--
--  فقط افزودنی: هیچ جدولِ موجودی دست نمی‌خورد و Sync هم نه.
--  بازگشت (فقط وقتی `shop.promo_uses` همه‌جا خاموش است؛ سابقهٔ مصرف از دست می‌رود):
--    DROP TABLE IF EXISTS shop_promo_redemptions;
--    DELETE FROM schema_migrations WHERE version = '136_promo_redemptions.sql';
--  هیچ مسیری جز `/api/shop/promos` به آن نمی‌رسد و `uses` ِ قاعده‌ها دوباره همان ۰ ِ پیشین می‌شود.
-- ============================================================

CREATE TABLE IF NOT EXISTS shop_promo_redemptions (
  shop_id      text    NOT NULL,
  promo_id     text    NOT NULL,
  sale_id      text    NOT NULL,
  --  کدِ نرمال‌شده همان لحظه (قاعده بعداً کدِ دیگری بگیرد، سابقه همان می‌ماند)
  code         text    NOT NULL,
  user_id      text    NOT NULL DEFAULT '',
  device_id    text    NOT NULL DEFAULT '',
  created_at   bigint  NOT NULL,
  updated_at   bigint  NOT NULL,
  released_at  bigint,
  released_by  text,
  PRIMARY KEY (shop_id, promo_id, sale_id),
  FOREIGN KEY (shop_id, promo_id) REFERENCES shop_promotions (shop_id, id) ON DELETE CASCADE
);

--  شمارشِ مصرفِ زنده (سقف و `uses` ِ GET)
CREATE INDEX IF NOT EXISTS idx_shop_promo_redemptions_live
  ON shop_promo_redemptions (shop_id, promo_id) WHERE released_at IS NULL;

--  آزاد کردنِ همهٔ کدهای یک فاکتور
CREATE INDEX IF NOT EXISTS idx_shop_promo_redemptions_sale
  ON shop_promo_redemptions (shop_id, sale_id);
