-- ============================================================
--  پلن‌های واقعیِ بخشِ پمپ — استاندارد · وی‌آی‌پی · دائمی
-- ------------------------------------------------------------
--  تصمیمِ صاحب مخزن، نوشته در ریپوی پمپ (`native/docs/PLANS-fa.md`):
--
--    | رایگان | استاندارد    | وی‌آی‌پی     | دائمی            |
--    |   —    | ۱۲۰ دالر/سال | ۱۵۰ دالر/سال | ۶۰۰ دالر، یک‌بار |
--
--  ⚠️ «رایگان» هیچ‌جا **دیده نمی‌شود** — جملهٔ خودش: «رایگان لازم به
--  دیده شدن نیست.» پس ردیفی هم ندارد؛ فقط حالتی است که برنامهٔ
--  فعال‌نشده در آن می‌ماند و سرور برایش مجوز صادر نمی‌کند.
--
--  ── چرا این‌جا و نه در `seedDefaults` ─────────────────────────────
--  آن تابع فقط وقتی کار می‌کند که بخش **هیچ** پلنی نداشته باشد
--  (`if (n > 0) continue`). سرورِ امروزِ صاحب مخزن سه پلنِ پیش‌فرضِ
--  شاپ‌شکل روی پمپ دارد (m1 · m6 · y1 با قیمتِ افغانی و `features`ِ
--  خالی)، پس بی این مهاجرت هیچ‌وقت پلنِ درست را نمی‌دید.
--
--  ── و چرا پلنِ کهنه پاک نمی‌شود ───────────────────────────────────
--  ممکن است اشتراکی روی همان کد فروخته شده باشد؛ `subscriptions.plan`
--  همان رشته را نگه می‌دارد و پاک کردنِ ردیف، نامِ پلنِ آن مشتری را
--  بی‌معنا می‌کرد. پس فقط **خاموش** می‌شود (`active=false`) — و آن هم
--  تنها اگر دست‌نخورده باشد: پلنی که صاحب مخزن قیمتش را عوض کرده یا
--  قابلیتی رویش گذاشته، دست نمی‌خورد.
-- ============================================================

--  ۱) پلن‌های تازه — اگر نیستند
INSERT INTO plans
  (id, code, title, amount, unit, price_afn, negotiable, features, max_devices,
   badge, sort_order, active, created_at, updated_at, app)
SELECT p.id, p.code, p.title, p.amount, p.unit, p.price, false, p.features::jsonb,
       p.devices, p.badge, p.sort, true,
       (EXTRACT(EPOCH FROM now())::bigint * 1000),
       (EXTRACT(EPOCH FROM now())::bigint * 1000),
       'pump'
  FROM (VALUES
    ('plan_pump_std',  'std',  'استاندارد', 1,  'year', 120,
     '["cloudbackup"]', 5,  '', 10),
    ('plan_pump_vip',  'vip',  'وی‌آی‌پی',   1,  'year', 150,
     '["dashboard","kar_app","bot","messenger","cloud","cloudbackup","profit","history","multi_device"]', 10,
     'پیشنهاد ما', 20),
    ('plan_pump_perm', 'perm', 'دائمی',     50, 'year', 600,
     '["dashboard","kar_app","bot","messenger","cloud","cloudbackup","profit","history","multi_device"]', 10,
     'یک‌بار برای همیشه', 30)
  ) AS p(id, code, title, amount, unit, price, features, devices, badge, sort)
 WHERE NOT EXISTS (SELECT 1 FROM plans x WHERE x.app = 'pump' AND x.code = p.code);

--  ۲) پلن‌های پیش‌فرضِ شاپ‌شکل روی پمپ — فقط اگر دست‌نخورده‌اند
UPDATE plans SET active = false,
       updated_at = (EXTRACT(EPOCH FROM now())::bigint * 1000)
 WHERE app = 'pump'
   AND code IN ('m1','m6','y1')
   AND features = '[]'::jsonb
   AND discount_percent = 0
   AND discount_price IS NULL
   AND ((code='m1' AND price_afn=500) OR (code='m6' AND price_afn=2000) OR (code='y1' AND price_afn=3000));

--  ۳) واحدِ پولِ بخشِ پمپ — دالر، نه افغانی
INSERT INTO app_config (key, value, updated_at)
VALUES ('pump_currency', 'دالر', (EXTRACT(EPOCH FROM now())::bigint * 1000))
ON CONFLICT (key) DO NOTHING;
