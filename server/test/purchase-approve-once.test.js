'use strict';
/**
 * SUB-06 (§۲۹.۸): «retry پرداخت/تمدید اثر دوم نسازد» — تأییدِ درخواستِ خرید.
 *
 * درخواستِ خرید (`POST /api/me/purchase-request`) یعنی مشتری یک دوره را خریده و مدیر پس از
 * دیدنِ پول آن را تأیید می‌کند (`POST /api/admin/purchase-requests/:id/approve` ⇒ `grant`).
 * تا ۲.۱۴.۴ مسیر اول `status='pending'` را می‌خواند، بعد `grant` می‌زد و بعد بی هیچ شرطی
 * `status='approved'` می‌نوشت: دو کلیک، یا تکرار پس از قطعِ شبکه، هر دو «pending» می‌دیدند و
 * **دو دوره** برای یک پرداخت می‌نشست. حالا گرفتنِ درخواست (`pending ⇒ approved`) و `grant` در
 * یک تراکنش‌اند و فقط یکی از درخواست‌های هم‌زمان آن را می‌گیرد.
 *
 * پنجرهٔ مسابقه واقعی است: پس از `grant` خبرِ «تمدید شد» (ایمیل/پیام) پیش از نوشتنِ `approved` می‌رفت و
 * SMTP ِ کند ثانیه‌ها طول می‌کشد. آزمون همین را با کُند کردنِ `notices.onSubscription` بازمی‌سازد؛ بی آن، در
 * PGlite ِ درون‌فرآیندی مسیرِ اول پیش از رسیدنِ دومی تمام می‌شد و آزمون روی کدِ کهنه هم سبز بود.
 *
 * آن‌چه این پرونده قفل می‌کند:
 *   ۱) پنج تأییدِ هم‌زمانِ یک درخواست ⇒ یک دوره، یک ردیفِ تاریخچه، یک سابقه؛ بقیه ۴۰۴ (همان پاسخِ امروزِ تکرار).
 *   ۲) تمدیدِ اشتراکِ زنده با تکرارِ تأیید ⇒ فقط یک دوره از پایانِ قبلی.
 *   ۳) تأیید و ردِ هم‌زمان ⇒ فقط یکی برنده؛ هرگز «ردشده» با اشتراکِ نشسته.
 *   ۴) شکستِ `grant` (پلنِ بی‌مدت) ⇒ درخواست pending می‌ماند و بعداً با مدت تأیید می‌شود (نیمه‌کاره نمی‌ماند).
 */
const test = require('node:test');
const assert = require('node:assert/strict');
const h = require('./helpers');
const { query, one, many, newId, now, tx } = require('../src/db');
const notices = require('../src/lib/notices');

const DAY = 24 * 60 * 60 * 1000;
let admin = '';

test.before(async () => {
  await h.start();
  const pw = require('../src/lib/password');
  await query(
    `INSERT INTO admins (id, username, name, password_hash, role, status, created_at)
     VALUES ($1,'admin','مدیر',$2,'superadmin','active',$3)`,
    [newId('adm'), await pw.hashPassword('Admin!12345'), now()]
  );
  admin = (await h.post('/api/admin/login', { username: 'admin', password: 'Admin!12345' })).body.token;
  assert.ok(admin);
});
test.after(async () => { await h.stop(); });

async function shopWithRequest(name, plan = 'custom') {
  const u = await h.newUser(name, 'shop');
  const made = await h.post('/api/shop', { name: `دکانِ ${name}` }, { token: u.accessToken });
  assert.equal(made.status, 201, JSON.stringify(made.body));
  const r = await h.post('/api/me/purchase-request', { plan }, { token: u.accessToken });
  assert.equal(r.status, 201, JSON.stringify(r.body));
  return { shopId: made.body.shop.id, requestId: r.body.request.id };
}

const approve = (id, body = { days: 30 }) => h.post(`/api/admin/purchase-requests/${id}/approve`, body, { token: admin });
const reject = (id) => h.post(`/api/admin/purchase-requests/${id}/reject`, {}, { token: admin });
const subsOf = (shopId) => many('SELECT * FROM subscriptions WHERE shop_id=$1', [shopId]);
const historyOf = (shopId) => many(`SELECT * FROM subscription_history WHERE shop_id=$1 AND action IN ('grant','renew')`, [shopId]);
const approvals = (id) => many(`SELECT * FROM audit_logs WHERE action='admin.request_approved' AND target_id=$1`, [id]);
const requestOf = (id) => one('SELECT * FROM purchase_requests WHERE id=$1', [id]);

/**
 * بدترین ترتیب: دیتابیس نگه داشته می‌شود تا همهٔ درخواست‌ها برسند و پشتِ هم صف بکشند، بعد رها می‌شود.
 * در PGlite (یک اتصال، صفِ FIFO) یعنی همه با هم از بررسیِ `pending` ِ پیش از تراکنش می‌گذرند، پس تنها
 * سدِ «دو دوره» همان UPDATE ِ شرط‌دار در تراکنشِ اعطاست. روی PostgreSQL نگه‌داشتن بی‌اثر است و آزمون همان می‌ماند.
 */
async function allPastPrecheck(fn) {
  let release;
  const hold = tx(() => new Promise((r) => { release = r; }));
  const out = fn();
  await new Promise((r) => setTimeout(r, 300));
  release();
  await hold;
  return out;
}

/** خبرِ «تمدید شد» مثلِ SMTP ِ کند (۳۰۰ میلی‌ثانیه) — همان پنجره‌ای که دو کلیک در آن دو دوره می‌ساخت. */
async function withSlowNotice(fn) {
  const real = notices.onSubscription;
  notices.onSubscription = async (...a) => { await new Promise((r) => setTimeout(r, 300)); return real(...a); };
  try { return await fn(); } finally { notices.onSubscription = real; }
}

test('SUB-06 ۱) پنج تأییدِ هم‌زمانِ یک درخواست ⇒ یک دوره، یک تاریخچه، یک سابقه', async () => {
  const s = await shopWithRequest('هم‌زمان');
  const t0 = Date.now();
  const res = await withSlowNotice(() => allPastPrecheck(() => Promise.all(Array.from({ length: 5 }, () => approve(s.requestId)))));
  const ok = res.filter((r) => r.status === 200);
  assert.equal(ok.length, 1, res.map((r) => `${r.status} ${r.body?.error?.code || ''}`).join(' | '));
  assert.ok(res.every((r) => r.status === 200 || r.status === 404), 'تکرار همان ۴۰۴ ِ امروز، نه ۵۰۰');

  const rows = await subsOf(s.shopId);
  assert.equal(rows.length, 1);
  const days = (Number(rows[0].ends_at) - t0) / DAY;
  assert.ok(days > 29.9 && days < 30.1, `یک دورهٔ ۳۰ روزه، نه ${days.toFixed(2)} روز`);
  assert.equal((await historyOf(s.shopId)).length, 1, 'یک ردیفِ grant/renew در تاریخچه');
  assert.equal((await approvals(s.requestId)).length, 1, 'یک سابقهٔ «تأیید شد»');
  assert.equal((await requestOf(s.requestId)).status, 'approved');
});

test('SUB-06 ۲) تمدیدِ اشتراکِ زنده: تکرارِ تأیید ⇒ فقط یک دوره از پایانِ قبلی', async () => {
  const s = await shopWithRequest('تمدید');
  const g = await h.post('/api/admin/subscriptions', { shopId: s.shopId, plan: 'custom', days: 10 }, { token: admin });
  assert.ok(g.status < 300, JSON.stringify(g.body));
  const before = Number((await subsOf(s.shopId))[0].ends_at);

  const res = await withSlowNotice(() => allPastPrecheck(() => Promise.all([approve(s.requestId), approve(s.requestId), approve(s.requestId)])));
  assert.equal(res.filter((r) => r.status === 200).length, 1, res.map((r) => r.status).join(','));
  const again = await approve(s.requestId);
  assert.equal(again.status, 404, 'تکرارِ پس از پاسخ (قطعِ شبکه) هم اثرِ دوم ندارد');

  const rows = await subsOf(s.shopId);
  assert.equal(rows.length, 1);
  assert.equal(Number(rows[0].ends_at), before + 30 * DAY, 'دقیقاً ۳۰ روز پس از پایانِ قبلی');
});

test('SUB-06 ۳) تأیید و ردِ هم‌زمان ⇒ فقط یکی برنده، و ردشده هرگز اشتراک نمی‌گیرد', async () => {
  for (let i = 0; i < 4; i++) {
    const s = await shopWithRequest(`تأیید-رد-${i}`);
    const [a, r] = await withSlowNotice(() => allPastPrecheck(async () => (i % 2
      ? Promise.all([approve(s.requestId), reject(s.requestId)])
      : (await Promise.all([reject(s.requestId), approve(s.requestId)])).reverse())));
    const req = await requestOf(s.requestId);
    const rows = await subsOf(s.shopId);
    const rejectedLog = await many(`SELECT 1 FROM audit_logs WHERE action='admin.request_rejected' AND target_id=$1`, [s.requestId]);
    if (req.status === 'approved') {
      assert.equal(a.status, 200);
      assert.equal(rows.length, 1);
      assert.equal(rejectedLog.length, 0, 'ردی که اثر نکرد در سابقه «رد شد» نمی‌نویسد');
    } else {
      assert.equal(req.status, 'rejected');
      assert.equal(a.status, 404, JSON.stringify(a.body));
      assert.equal(rows.length, 0, 'درخواستِ ردشده اشتراکی ننشانده');
    }
    assert.equal(r.status, 200);
  }
});

test('SUB-06 ۴) شکستِ grant ⇒ درخواست pending می‌ماند، بی اشتراکِ نیمه‌کاره؛ بعداً با مدت تأیید می‌شود', async () => {
  const s = await shopWithRequest('بی‌مدت', 'no-such-plan');
  const bad = await approve(s.requestId, {});
  assert.equal(bad.status, 400, JSON.stringify(bad.body));
  assert.equal(bad.body.error.code, 'missing_duration');
  assert.equal((await requestOf(s.requestId)).status, 'pending', 'شکست درخواست را نمی‌سوزاند');
  assert.equal((await subsOf(s.shopId)).length, 0);
  assert.equal((await approvals(s.requestId)).length, 0);

  const ok = await approve(s.requestId, { days: 7 });
  assert.equal(ok.status, 200, JSON.stringify(ok.body));
  assert.equal((await requestOf(s.requestId)).status, 'approved');
  assert.equal((await subsOf(s.shopId)).length, 1);
});
