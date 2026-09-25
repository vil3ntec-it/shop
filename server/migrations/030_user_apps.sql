-- ============================================================
--  هر حساب از کدام برنامه آمده — «user_apps»
--
--  گزارشِ صاحب سامانه (۱۴۰۵/۰۷/۱۳): «حسابی که در برنامهٔ پمپ ساختم در
--  پنل دیده نمی‌شود.» فهرستِ «افرادِ پمپ‌ها» (`/api/admin/pump/users`) از
--  درِ `station_members` می‌آمد، پس کسی که حساب ساخته ولی هنوز پمپش را
--  نساخته هیچ‌جا نبود — درست همان کسی که مدیر باید ببیند و راهش بیندازد.
--
--  ⚠️ `tokens.app` کافی نیست: ردیفِ منقضی پاک می‌شود (`pruneExpired`)،
--  پس حسابی که یک ماه نیامده از فهرست می‌افتاد. این جدول ماندگار است و
--  با هر نشستِ تازه فقط `last_seen_at`ش جلو می‌رود.
--
--  ⚠️ دفترِ دوم نیست: فقط «این شخص از این برنامه آمده» را نگه می‌دارد،
--  هیچ دادهٔ حسابی در آن نیست.
-- ============================================================
CREATE TABLE IF NOT EXISTS user_apps (
  user_id        text   NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  app            text   NOT NULL,
  first_seen_at  bigint NOT NULL,
  last_seen_at   bigint NOT NULL,
  PRIMARY KEY (user_id, app)
);
CREATE INDEX IF NOT EXISTS idx_user_apps_app ON user_apps(app, first_seen_at DESC);

--  حساب‌های امروزی: هر نشستی که هنوز روی دیسک هست
INSERT INTO user_apps (user_id, app, first_seen_at, last_seen_at)
SELECT t.subject_id, t.app, MIN(t.issued_at), MAX(t.issued_at)
  FROM tokens t
  JOIN users u ON u.id = t.subject_id
 WHERE t.kind IN ('access', 'refresh')
 GROUP BY t.subject_id, t.app
ON CONFLICT (user_id, app) DO NOTHING;

--  و هر عضوِ پمپ — حتی اگر نشستش مدت‌ها پیش پاک شده باشد
INSERT INTO user_apps (user_id, app, first_seen_at, last_seen_at)
SELECT m.user_id, 'pump', MIN(m.created_at), MAX(m.created_at)
  FROM station_members m
  JOIN users u ON u.id = m.user_id
 GROUP BY m.user_id
ON CONFLICT (user_id, app) DO NOTHING;
