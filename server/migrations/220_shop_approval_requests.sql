-- ============================================================
--  ۲۲۰ — «درخواستِ تأیید» از راهِ دور (STF-02): شاگرد از صندوق می‌پرسد، صاحب یا مدیر روی **دستگاهِ خودش**
--  تأیید یا رد می‌کند؛ تصمیم فقط در سرور است (`lib/approval-requests.js`، پشتِ پرچمِ خاموشِ `shop.approval_requests`).
--
--  هر ردیف بسته به دکان + کار (`perm`) + سند (`doc` = جدول/شناسهٔ ردیف) + سقفِ درصد و مبلغ است، عمرِ کوتاه دارد
--  (`expires_at`، حداکثر ۱۵ دقیقه، همان `approval-core.TTL_MAX_MS`) و فقط یک بار مصرف می‌شود: opِ همگام‌سازی که
--  `approval: {v:1, request:<id>}` بیاورد آن را `used` می‌کند و ردیفِ `shop_approvals` (همان دفترِ یک‌بارمصرفِ برگه‌ها،
--  nonce = `req_<id>`) سابقهٔ نهایی است. `request_key` = کلیدِ بی‌خطرِ تکرارِ همان درخواست از همان کاربر.
--  «منقضی» ستون نیست: pending/approved ِ گذشته از `expires_at` همان منقضی است.
--
--  rollback: migrations/rollback/220_shop_approval_requests.down.sql
-- ============================================================

CREATE TABLE IF NOT EXISTS shop_approval_requests (
  id               text    PRIMARY KEY,
  shop_id          text    NOT NULL REFERENCES shops(id) ON DELETE CASCADE,
  request_key      text    NOT NULL,
  requester_id     text    NOT NULL,
  requester_device text    NOT NULL DEFAULT '',
  perm             text    NOT NULL,
  doc              text    NOT NULL,
  pct_bp           integer CHECK (pct_bp IS NULL OR (pct_bp >= 0 AND pct_bp <= 10000)),
  amount           bigint  CHECK (amount IS NULL OR amount >= 0),
  reason           text    NOT NULL,
  status           text    NOT NULL DEFAULT 'pending'
                           CHECK (status IN ('pending','approved','denied','cancelled','used')),
  created_at       bigint  NOT NULL,
  expires_at       bigint  NOT NULL,
  decided_by       text    NOT NULL DEFAULT '',
  decided_at       bigint,
  decision_note    text    NOT NULL DEFAULT '',
  used_op_id       text    NOT NULL DEFAULT '',
  used_at          bigint,
  UNIQUE (shop_id, requester_id, request_key)
);
CREATE INDEX IF NOT EXISTS idx_shop_approval_requests_open
  ON shop_approval_requests(shop_id, status, created_at DESC);
