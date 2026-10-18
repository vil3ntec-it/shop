-- ============================================================
--  مجوزهای نقش در هر دکان — STF-01 تا STF-03 (بخشِ ۱۰.۱)
--
--  لایهٔ «نقش» روی پیش‌فرضِ هسته (`lib/perm-core.js`). نبودنِ ردیف یعنی
--  همان رفتارِ امروز؛ پس این مهاجرت به تنهایی اجازهٔ هیچ‌کس را عوض نمی‌کند.
--
--  perms = { "<کلید>": { "mode": "allow|approve|deny", "caps": { … } } }
--  هر ردیف پیش از ذخیره با `validate`ِ هسته سنجیده می‌شود. مالک لایه ندارد
--  (همیشه همه‌کاره) و لایهٔ «restrict» هرگز بیش از نقش نمی‌دهد.
--
--  برگشت: DROP TABLE shop_role_perms; — ردیفی جز این جدول دست نمی‌خورد.
-- ============================================================

CREATE TABLE IF NOT EXISTS shop_role_perms (
  shop_id     text    NOT NULL REFERENCES shops(id) ON DELETE CASCADE,
  role        text    NOT NULL CHECK (role IN ('manager','staff')),
  perms       jsonb   NOT NULL DEFAULT '{}'::jsonb,
  updated_by  text    NOT NULL DEFAULT '',
  updated_at  bigint  NOT NULL,
  PRIMARY KEY (shop_id, role)
);
