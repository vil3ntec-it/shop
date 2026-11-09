'use strict';
/**
 * بندهای بازِ ممیزیِ ۳۵ (`docs/SECURITY-AUDIT-fa.md` بخشِ B2، X1 تا X4) — هر بند پیش از رفع روی همین پایه سرخ بود.
 *
 *   X1  `GET /api/admin/sync/errors?limit=-1` ⇒ ۵۰۰ ِ LIMIT ِ منفی؛ NUL در پرسشِ `admin/sync/status|conflicts` ⇒ ۵۰۰.
 *   X2  NUL (`%00`) در پرسشِ درهای مدیر (`image-bank/queue|review`، `logins`، `otp`، `shop-desk/groups`) ⇒ ۵۰۰ ِ
 *       پایگاه‌داده (۲۲۰۲۱). رفع سراسری مثلِ مسیر (B4): ۴۰۰ ِ `bad_query` پیش از هر در؛ همگام‌سازی و پمپ دست‌نخورده.
 *   X3  سقفِ پنج کلیدِ تأییدکننده بشمار-بعد-بنویس بود (روی PostgreSQL ِ واقعی پیش از رفع بیش از پنج)؛ و یک کلیدِ
 *       عمومی که دو کاربر هم‌زمان ثبت کنند هر دو ۲۰۱ می‌گرفتند و دومی برچسب و دستگاهِ کلیدِ اولی را رونویسی می‌کرد.
 *   X4  کدِ شش‌رقمیِ وی‌آی‌پی فقط سطلِ هر IP داشت ⇒ حدس از IP های گوناگون بی‌سقف. حالا سطلِ «تلاشِ ناموفق» به‌ازای هر
 *       حساب و (برای کارمندها) هر دکان؛ موقت، فقط همین در، و کارمند هرگز صاحب را نمی‌بندد.
 */
process.env.TRUST_PROXY = 'true';
process.env.RATE_VIP_FAIL_MAX = '4';
process.env.RATE_VIP_FAIL_WINDOW_MS = '60000';
const test = require('node:test');
const assert = require('node:assert/strict');
const crypto = require('node:crypto');
const h = require('./helpers');
const { query, one, newId, now } = require('../src/db');
const { resetAll } = require('../src/middleware/ratelimit');

let admin = '';
let ipSeq = 0;
const freshIp = () => { ipSeq += 1; return `198.51.${(ipSeq >> 8) & 255}.${ipSeq & 255}`; };
const fromIp = (token) => ({ token, headers: { 'X-Forwarded-For': freshIp() } });

async function ownerWithShop(name) {
  const u = await h.newUser(name);
  const made = await h.post('/api/shop', { name: `دکانِ ${name}` }, { token: u.accessToken });
  assert.equal(made.status, 201, JSON.stringify(made.body));
  return { ...u, token: u.accessToken, userId: u.user.id, shopId: made.body.shop.id };
}
async function member(shopId, role, name) {
  const u = await h.newUser(name);
  await query(
    `INSERT INTO shop_members (id, shop_id, user_id, role, status, created_at, updated_at)
     VALUES ($1,$2,$3,$4,'active',$5,$5)`,
    [newId('mem'), shopId, u.user.id, role, now()]
  );
  return { ...u, token: u.accessToken, userId: u.user.id, shopId };
}

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

/* ── X1 ──────────────────────────────────────────────────────────── */

test('X1: admin/sync/errors با limit ِ منفی یا صفر ⇒ ۲۰۰ و فهرستِ درست، نه ۵۰۰ ِ LIMIT', async () => {
  await h.post('/api/errors', { app: 'shop', message: 'x1-probe', schema_version: 1 });
  for (const q of ['?limit=-1', '?limit=-1&app=shop', '?limit=0', '?limit=-99999999999', '?limit=1e309&app=pump']) {
    const r = await h.get(`/api/admin/sync/errors${q}`, { token: admin });
    assert.equal(r.status, 200, `${q} ⇒ ${r.status} ${JSON.stringify(r.body)}`);
    assert.ok(Array.isArray(r.body.errors) && r.body.errors.length <= 500, q);
  }
  const one1 = await h.get('/api/admin/sync/errors?limit=1', { token: admin });
  assert.equal(one1.body.errors.length, 1);
});

/* ── X1 + X2 ─────────────────────────────────────────────────────── */

test('X1/X2: NUL در پرسشِ درهای مدیر ⇒ ۴۰۰ ِ bad_query، نه ۵۰۰ ِ پایگاه‌داده', async () => {
  for (const path of [
    '/api/admin/sync/status?account=a%00b',
    '/api/admin/sync/conflicts?account=a%00b',
    '/api/admin/image-bank/queue?cursor=a%00b',
    '/api/admin/image-bank/review?cursor=a%00b',
    '/api/admin/logins?email=a%00b',
    '/api/admin/otp?destination=a%00b',
    '/api/admin/shop-desk/groups?q=a%00b',
    //  نامِ کلید هم، و هر بزرگی و کوچکیِ نوشتن (`%00` حرف ندارد؛ این‌جا فقط جای NUL عوض می‌شود)
    '/api/admin/shop-desk/groups?a%00=1',
    '/api/admin/logins?email=x&email=%00',
  ]) {
    const r = await h.get(path, { token: admin });
    assert.equal(r.status, 400, `${path} ⇒ ${r.status} ${JSON.stringify(r.body)}`);
    assert.equal(r.body.error.code, 'bad_query', path);
  }
  //  بی NUL همان درها سالم‌اند؛ `%2500` (متنِ «%00») NUL نیست و دست نمی‌خورد
  for (const path of ['/api/admin/sync/status?account=x%2500', '/api/admin/logins?email=a%2500b', '/api/admin/shop-desk/groups?q=%2500']) {
    const r = await h.get(path, { token: admin });
    assert.equal(r.status, 200, `${path} ⇒ ${r.status} ${JSON.stringify(r.body)}`);
  }
});

test('X2: ردِ NUL در پرسش سراسری است (بی‌توکن هم) ولی همگام‌سازی و پمپ (مشترک) همان پیشین می‌مانند', async () => {
  //  پیش از احراز: درِ بی‌توکن هم ۴۰۰ می‌گیرد، نه ۴۰۱ یا ۵۰۰
  const anon = await h.get('/api/admin/sync/status?account=%00');
  assert.deepEqual([anon.status, anon.body.error.code], [400, 'bad_query']);
  //  درِ عمومیِ دکان هم (پیش از این فقط چند در خودشان می‌سنجیدند)
  const pub = await h.get('/api/auth/shop/request-status?request_id=ok&x=%00');
  assert.deepEqual([pub.status, pub.body.error.code], [400, 'bad_query']);
  //  راه‌های مشترک با پمپ این لایه را ندارند (مثلِ بدنهٔ B4): به خودِ در می‌رسند و پاسخ همانی است که در می‌دهد
  resetAll();
  const o = await ownerWithShop('همگام‌سازیِ دست‌نخورده');
  const sync = { token: o.token, headers: { 'X-App': 'shop', 'X-Device': 'dev-x2' } };
  for (const path of ['/api/sync/v1/status?x=%00', '/api/sync/status?x=%00', '/api/shop/sync/status?x=%00']) {
    const r = await h.api('GET', path, sync);
    assert.equal(r.status, 200, `${path} ⇒ ${r.status} ${JSON.stringify(r.body).slice(0, 200)}`);
  }
  const pump = await h.get('/api/pump/device/state?x=%00');
  assert.notEqual(pump.body?.error?.code, 'bad_query');
});

/* ── X3 (رقابت؛ روی PostgreSQL ِ واقعی پیش از رفع سرخ) ─────────────── */

const spki = () => crypto.generateKeyPairSync('ec', { namedCurve: 'prime256v1' })
  .publicKey.export({ format: 'der', type: 'spki' }).toString('base64');

test('X3: بیست ثبتِ هم‌زمانِ کلیدِ تأیید برای یک کاربر ⇒ دقیقاً پنج کلیدِ فعال، بقیه ۴۰۰ too_many_keys', async () => {
  resetAll();
  //  سه دور با سه کاربرِ تازه: بی قفل هر دور جداگانه ~۸۰٪ بیش از پنج می‌ساخت (PostgreSQL)، سه دور با هم بی‌شک سرخ
  let o; let rs;
  for (let round = 0; round < 3; round++) {
    o = await ownerWithShop(`رقابتِ کلید ${round}`);
    const keys = Array.from({ length: 20 }, spki);
    rs = await Promise.all(keys.map((public_key, i) =>
      h.post('/api/shop/approver-keys', { public_key, label: `pos-${i}` }, { token: o.token })));
    const codes = rs.map((r) => r.status).sort();
    assert.deepEqual(codes, [...Array(5).fill(201), ...Array(15).fill(400)],
      `دورِ ${round}: ${JSON.stringify(rs.map((r) => r.body?.error?.code || r.status))}`);
    for (const r of rs) if (r.status === 400) assert.equal(r.body.error.code, 'too_many_keys');
    const active = await one('SELECT COUNT(*)::int AS n FROM shop_approver_keys WHERE shop_id=$1 AND user_id=$2 AND revoked_at IS NULL',
      [o.shopId, o.userId]);
    assert.equal(active.n, 5);
  }
  //  باطل کردنِ یکی ⇒ جای یکی باز می‌شود (سقف همان سقف است، نه قفلِ همیشگی)
  const k0 = rs.find((r) => r.status === 201).body.key.id;
  assert.equal((await h.api('DELETE', `/api/shop/approver-keys/${k0}`, { token: o.token })).status, 200);
  assert.equal((await h.post('/api/shop/approver-keys', { public_key: spki() }, { token: o.token })).status, 201);
  assert.equal((await h.post('/api/shop/approver-keys', { public_key: spki() }, { token: o.token })).status, 400);
});

test('X3: یک کلیدِ عمومی که دو کاربر هم‌زمان ثبت کنند ⇒ یکی ۲۰۱، دیگری ۴۰۹ key_taken؛ برچسبِ برنده دست نمی‌خورد', async () => {
  resetAll();
  for (let round = 0; round < 4; round++) {
    const a = await ownerWithShop(`کلیدِ مشترکِ الف ${round}`);
    const b = await ownerWithShop(`کلیدِ مشترکِ ب ${round}`);
    const pk = spki();
    const [ra, rb] = await Promise.all([
      h.post('/api/shop/approver-keys', { public_key: pk, label: 'label-a', device_id: 'dev-a' }, { token: a.token }),
      h.post('/api/shop/approver-keys', { public_key: pk, label: 'label-b', device_id: 'dev-b' }, { token: b.token }),
    ]);
    assert.deepEqual([ra.status, rb.status].sort(), [201, 409], JSON.stringify([ra.body, rb.body]));
    const [win, lose, winner] = ra.status === 201 ? [ra, rb, a] : [rb, ra, b];
    assert.equal(lose.body.error.code, 'key_taken');
    assert.equal(win.body.key.userId, winner.userId);
    const row = await one('SELECT * FROM shop_approver_keys WHERE id=$1', [win.body.key.id]);
    assert.deepEqual([row.shop_id, row.user_id, row.label, row.device_id],
      [winner.shopId, winner.userId, winner === a ? 'label-a' : 'label-b', winner === a ? 'dev-a' : 'dev-b']);
  }
  //  همان کاربر کلیدِ باطل‌شدهٔ خودش را دوباره ثبت می‌کند ⇒ زنده می‌شود (رفتارِ پیشین)
  const c = await ownerWithShop('کلیدِ دوباره');
  const pk = spki();
  const first = await h.post('/api/shop/approver-keys', { public_key: pk, label: 'one' }, { token: c.token });
  assert.equal((await h.api('DELETE', `/api/shop/approver-keys/${first.body.key.id}`, { token: c.token })).status, 200);
  const again = await h.post('/api/shop/approver-keys', { public_key: pk, label: 'two' }, { token: c.token });
  assert.equal(again.status, 201);
  assert.deepEqual([again.body.key.id, again.body.key.revokedAt, again.body.key.label], [first.body.key.id, null, 'two']);
});

/* ── X4 ──────────────────────────────────────────────────────────── */

const issued = new Set();
async function vipCode() {
  const r = await h.post('/api/admin/vip-codes', { plan: 'm1', days: 30 }, { token: admin });
  assert.equal(r.status, 201, JSON.stringify(r.body));
  issued.add(r.body.code);
  return r.body.code;
}
/** شش رقمی که هیچ کدِ صادرشده‌ای نیست ⇒ «حدسِ» نادرستِ قطعی */
let wrongSeq = 100000;
function wrong() {
  do { wrongSeq += 7919; } while (issued.has(String(wrongSeq % 1000000).padStart(6, '0')));
  return String(wrongSeq % 1000000).padStart(6, '0');
}
const redeem = (who, code) => h.post('/api/vip/redeem', { code }, fromIp(who.token));
const codeRow = (code) => one(`SELECT status FROM vip_codes WHERE code_hint=$1 ORDER BY created_at DESC LIMIT 1`, [code.slice(-2)]);

test('X4: حدس از IP های گوناگون با یک حساب ⇒ پس از سقفِ تلاشِ ناموفق ۴۲۹، حتی با کدِ درست؛ کد خرج نمی‌شود', async () => {
  resetAll();
  const o = await ownerWithShop('حدسِ وی‌آی‌پی');
  const good = await vipCode();
  for (let i = 0; i < 4; i++) {
    const r = await redeem(o, wrong());
    assert.deepEqual([r.status, r.body.error.code], [404, 'bad_code'], `تلاشِ ${i + 1}`);
  }
  const blocked = await redeem(o, good);
  assert.equal(blocked.status, 429, JSON.stringify(blocked.body));
  //  موقت: Retry-After در پنجره (۶۰ ثانیهٔ این آزمون؛ انبارِ مشترک یک برشِ یک‌ثانیه‌ای بیشتر)
  const wait = Number(blocked.headers.get('retry-after'));
  assert.ok(wait > 0 && wait <= 61, String(wait));
  const sub = await h.get('/api/me/subscription', { token: o.token });
  assert.notEqual(sub.body.status, 'active', 'کدِ درست پشتِ سقف خرج نشد');
});

test('X4: قفل فقط همین در و موقت است — ورود، دکان و پنجرهٔ تازه باز؛ تلاشِ موفق شمرده نمی‌شود', async () => {
  resetAll();
  const o = await ownerWithShop('قفلِ موقت');
  const good = await vipCode();
  for (let i = 0; i < 4; i++) assert.equal((await redeem(o, wrong())).status, 404);
  assert.equal((await redeem(o, good)).status, 429);
  //  راه‌های دیگرِ صاحب: ورود با رمز، تازه‌سازیِ نشست و خودِ دکان
  const signed = await h.post('/api/auth/login', {
    identifier: o.email, password: o.password, app: 'shop',
    device: { deviceId: 'dev-x4-owner', name: 'تست', platform: 'test' },
  }, { headers: { 'X-Forwarded-For': freshIp() } });
  assert.equal(signed.status, 200, JSON.stringify(signed.body));
  assert.equal((await h.get('/api/shop', { token: signed.body.accessToken })).status, 200);
  const refreshed = await h.post('/api/auth/refresh', { refreshToken: signed.body.refreshToken });
  assert.equal(refreshed.status, 200, JSON.stringify(refreshed.body));
  //  پس از پنجره (ساعت جلو) ⇒ همان کدِ درست کار می‌کند
  const realNow = Date.now;
  try {
    Date.now = () => realNow() + 61_000;
    const used = await redeem(o, good);
    assert.equal(used.status, 201, JSON.stringify(used.body));
  } finally { Date.now = realNow; }

  //  فقط ناموفق شمرده می‌شود: سه ناموفق + یک موفق ⇒ چهارمین ناموفق هنوز ۴۰۴، پنجمی ۴۲۹
  resetAll();
  const b = await ownerWithShop('موفق شمرده نمی‌شود');
  for (let i = 0; i < 3; i++) assert.equal((await redeem(b, wrong())).status, 404);
  assert.equal((await redeem(b, await vipCode())).status, 201);
  assert.equal((await redeem(b, wrong())).status, 404);
  assert.equal((await redeem(b, wrong())).status, 429);
});

test('X4: کارمندهای یک دکان با هم سقفِ دکان را می‌خورند؛ ولی صاحبِ دکان را هرگز نمی‌بندند', async () => {
  resetAll();
  const o = await ownerWithShop('دکانِ چندکارمند');
  const s1 = await member(o.shopId, 'manager', 'مدیرِ حدس');
  const s2 = await member(o.shopId, 'cashier', 'صندوق‌دارِ حدس');
  const s3 = await member(o.shopId, 'staff', 'کارمندِ حدس');
  for (const who of [s1, s1, s2, s2]) assert.equal((await redeem(who, wrong())).status, 404);
  //  حسابِ سوم هیچ تلاشی نکرده و IP اش تازه است، ولی سقفِ کارمندانِ دکان پر است
  const third = await redeem(s3, wrong());
  assert.equal(third.status, 429, JSON.stringify(third.body));
  //  صاحب: سطلِ خودش را دارد ⇒ کدِ درست همین حالا کار می‌کند
  const used = await redeem(o, await vipCode());
  assert.equal(used.status, 201, JSON.stringify(used.body));
  //  و دکانِ دیگر دست نخورده
  const other = await ownerWithShop('دکانِ همسایه');
  assert.equal((await redeem(other, wrong())).status, 404);
});
