-- ============================================================
--  «برگهٔ امضاشده»ی تأیید — STF-02، STF-03 (بخشِ ۱۰.۱، سناریوهای S07 و S08)
--
--  ۱) shop_approver_keys: کلیدِ عمومیِ هر مدیر/صاحب روی هر دستگاه.
--     کلیدِ خصوصی هرگز از دستگاه بیرون نمی‌آید و PIN فقط آن را باز می‌کند؛
--     هشِ PIN هرگز به سرور نمی‌رسد (بخشِ ۱۰.۲).
--  ۲) shop_approvals: هر برگه‌ای که سرور پذیرفت — یک‌بارمصرف (کلیدِ اصلی
--     shop_id + nonce)، و همان «سابقه»ی درخواست‌کننده، تأییدکننده، مقدار و
--     دلیل که گزارشِ ضدتقلب (RPT-09) می‌خواند. opِ ردشده جدا در
--     sync_v3_conflicts می‌نشیند (kind: approval_required | approval_rejected
--     | perm_denied).
--
--  ⛔ فقط افزودنی است. تا صاحب دکان لایه‌ای برای تخفیف، لغو یا برگشت نگذارد،
--     هیچ opی به این جدول‌ها نمی‌رسد و رفتار همان امروز است.
--
--  برگشت: DROP TABLE shop_approvals; DROP TABLE shop_approver_keys;
-- ============================================================

CREATE TABLE IF NOT EXISTS shop_approver_keys (
  id           text    PRIMARY KEY,                 -- ak_ + ۲۴ نویسهٔ اولِ SHA-256ِ کلیدِ عمومی
  shop_id      text    NOT NULL REFERENCES shops(id) ON DELETE CASCADE,
  user_id      text    NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  alg          text    NOT NULL DEFAULT 'ES256' CHECK (alg IN ('ES256')),
  public_key   text    NOT NULL,                    -- SPKI DER، base64
  label        text    NOT NULL DEFAULT '',
  device_id    text    NOT NULL DEFAULT '',
  created_at   bigint  NOT NULL,
  revoked_at   bigint,
  revoked_by   text    NOT NULL DEFAULT ''
);
CREATE INDEX IF NOT EXISTS idx_shop_approver_keys_user
  ON shop_approver_keys(shop_id, user_id) WHERE revoked_at IS NULL;

CREATE TABLE IF NOT EXISTS shop_approvals (
  shop_id      text    NOT NULL REFERENCES shops(id) ON DELETE CASCADE,
  nonce        text    NOT NULL,
  op_id        text    NOT NULL,
  doc          text    NOT NULL,
  perm         text    NOT NULL,
  pct_bp       integer,
  amount       bigint,
  approver_id  text    NOT NULL,
  requester_id text    NOT NULL DEFAULT '',
  key_id       text    NOT NULL,
  device_id    text    NOT NULL DEFAULT '',
  path         text    NOT NULL DEFAULT 'v3' CHECK (path IN ('v1','v2','v3')),
  reason       text    NOT NULL DEFAULT '',
  issued_at    bigint  NOT NULL,
  used_at      bigint  NOT NULL,
  PRIMARY KEY (shop_id, nonce)
);
CREATE INDEX IF NOT EXISTS idx_shop_approvals_used ON shop_approvals(shop_id, used_at DESC);
