-- ============================================================
--  ۲۱۴ — دورهٔ آزمایشیِ دکان ۱۴ روز (D-03، گرفته‌شده به روشِ شرکت‌های بزرگ ۱۴۰۵/۰۸/۱۳، OWNER-DECISIONS).
--
--  `app_config.trial_days` فقط یک بار کاشته می‌شود (`plans.seedDefaults`، ON CONFLICT DO NOTHING)، پس تازه شدنِ
--  `plans.TRIAL_DEFAULT.shop` به ۱۴ نصبِ موجود را عوض نمی‌کرد. این‌جا فقط همان ۷ ِ کاشتهٔ پیشین ۱۴ می‌شود:
--  عددی که مدیر از پنل گذاشته (هر چیزِ جز ۷) و `pump_trial_days` دست نمی‌خورند؛ ردیفی ساخته نمی‌شود.
--  پیامدِ پذیرفته در همان تصمیم: دکانی که در ۱۴ روزِ گذشته ساخته شده و ۷ روزش تمام شده بود دوباره در دوره است
--  (آغاز همان `shops.created_at` ِ سرور است؛ `lib/entitlement.js`).
--  rollback: migrations/rollback/214_shop_trial_14_days.down.sql (آزموده در test/trial-single-source.test.js)
-- ============================================================

UPDATE app_config
   SET value = '14', updated_at = (EXTRACT(EPOCH FROM now()) * 1000)::bigint
 WHERE key = 'trial_days' AND btrim(value) = '7';
