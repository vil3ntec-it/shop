'use strict';
/**
 * بازبینیِ امنیتیِ دومِ دستهٔ ۴۵ (`stf-2`، `crm-2`، `web-2`، `pos-3`، `pur-2`، `prd-prm`، `syn-3`، `hw-2`؛ ادغام روی دستهٔ ۴۴).
 *
 *   SR45-1  CRM-15: ردیفِ «خروجیِ برگهٔ همهٔ مشتریان» (`*`) در فهرستِ **هر** مشتری می‌آید و فهرست یک `LIMIT` ِ مشترک داشت
 *           ⇒ هر عضو (شاگرد هم؛ `POST` ِ درِ `app`) با یک «خروجیِ همه» در هر ۱۰ دقیقه، یا همان انباشتِ عادیِ خروجی‌ها، دیدنِ
 *           واقعیِ شمارهٔ یک مشتری را از ۲۰۰ ردیفِ تازهٔ فهرستِ صاحب بیرون می‌راند (بی صفحه‌بندی ⇒ دیگر دیده نمی‌شد).
 *   SR45-2  STF-04: `setPin` اثرِ PBKDF2 (۶۰۰٬۰۰۰ دور) را **پیش از** سنجشِ اختیار و بودنِ عضو می‌ساخت (۴۰۳/۴۰۴ پس از کارِ
 *           سنگین) و نوشتنِ PIN فقط سطلِ ۱۲۰ در دقیقهٔ همهٔ درها را داشت (شش برابرِ بودجهٔ `/verify` با همان هزینه).
 *   SR45-3  STF-04: برداشتن یا تعلیقِ عضو PIN ِ صندوقش و نوبتِ بازش را دست نمی‌زد ⇒ تا ۱۲ ساعت «چه کسی پشتِ دستگاه بود»
 *           (`sessions`، `whoAt`) عضوِ رفته را نشان می‌داد، و عضوِ دوباره‌فعال با همان PIN ِ کهنه برمی‌گشت.
 *   R14     STF-04: مدیر با «۵ حدس، بازکردنِ قفل (تأییدِ دوباره)، ۵ حدس…» PIN ِ زیردست را پیدا می‌کرد (`unlock` پلهٔ دوبرابر را
 *           هم صفر می‌کرد) ⇒ حالا پله می‌ماند و دهمین خطا از آخرین سنجشِ درست PIN را باطل می‌کند (فقط PIN ِ تازه)؛ صاحب بیرون نمی‌ماند.
 *   R15     STF-04: `/verify` به هر عضو می‌گفت عضوِ دیگر PIN ِ سرور دارد یا نه (۴۰۹ `till_pin_not_set` در برابرِ ۴۰۳) ⇒ حالا به جز خودِ
 *           عضو و صاحب/مدیر همان پاسخ، همان PBKDF2، همان شمار و همان قفل.
 */
process.env.TILL_PIN_ITER = '1000';

const crypto = require('node:crypto');
//  SR45-2: شمارِ PBKDF2 — پیش از بار شدنِ برنامه، چون `lib/till-pin.js` همان `crypto.pbkdf2` را promisify می‌کند (R15: و دورِ آخرین)
let pbkdf2Calls = 0;
let pbkdf2Iter = 0;
const realPbkdf2 = crypto.pbkdf2;
crypto.pbkdf2 = function countedPbkdf2(...args) { pbkdf2Calls += 1; pbkdf2Iter = args[2]; return realPbkdf2.apply(this, args); };

const test = require('node:test');
const assert = require('node:assert/strict');
const h = require('./helpers');
const { query, one, newId, now } = require('../src/db');
const { hashToken } = require('../src/lib/tokens');
const { resetAll } = require('../src/middleware/ratelimit');

const MIN = 60 * 1000;
const DAY = 24 * 60 * MIN;
let root = '';
let A;

async function shopOwner(name) {
  const u = await h.newUser(name, 'shop');
  const made = await h.post('/api/shop', { name: `دکانِ ${name}` }, { token: u.accessToken });
  assert.equal(made.status, 201, JSON.stringify(made.body));
  return { ...u, token: u.accessToken, shopId: made.body.shop.id };
}
async function member(s, role, name) {
  const u = await h.newUser(name, 'shop');
  const id = newId('mem');
  await query(
    `INSERT INTO shop_members (id, shop_id, user_id, role, status, created_at, updated_at) VALUES ($1,$2,$3,$4,'active',$5,$5)`,
    [id, s.shopId, u.user.id, role, now()],
  );
  return { ...u, token: u.accessToken, memberId: id };
}
async function tillFlag(s) {
  const r = await h.put('/api/admin/pump/live-config', { scope: s.shopId, key: 'shop.till_pin', value: true }, { token: root });
  assert.equal(r.status, 200, JSON.stringify(r.body));
}
const ok = (r, code = 200) => { assert.equal(r.status, code, JSON.stringify(r.body)); return r.body; };
const setPinFor = (by, target, pin) => h.put(`/api/shop/till-pin/members/${target.memberId}`, { pin }, { token: by.token });
const verify = (host, target, pin) => h.post('/api/shop/till-pin/verify', { memberId: target.memberId, pin }, { token: host.token });

test.before(async () => {
  await h.start();
  const pw = require('../src/lib/password');
  await query(
    `INSERT INTO admins (id, username, name, password_hash, role, status, created_at) VALUES ($1,'sr45-root','مدیر',$2,'superadmin','active',$3)`,
    [newId('adm'), await pw.hashPassword('Admin!12345'), now()],
  );
  root = (await h.post('/api/admin/login', { username: 'sr45-root', password: 'Admin!12345' })).body.token;
  A = await shopOwner('الف ۴۵');
  A.memberId = (await one('SELECT id FROM shop_members WHERE shop_id=$1 AND user_id=$2', [A.shopId, A.user.id])).id;
  A.manager = await member(A, 'manager', 'مدیرِ ۴۵');
  A.staff = await member(A, 'staff', 'شاگردِ ۴۵');
});
test.after(async () => { await require('../src/lib/customer-access').settle(); await h.stop(); });
test.beforeEach(() => resetAll());

test('SR45-1: «خروجیِ برگهٔ همه» (`*`) دیدنِ واقعیِ همان مشتری را از فهرستِ صاحب بیرون نمی‌راند', async () => {
  const access = require('../src/lib/customer-access');
  const t = now();
  //  دیدنِ واقعیِ شمارهٔ مشتری به دستِ مدیر، سه روز پیش
  await access.record({
    shopId: A.shopId, viewerId: A.manager.user.id, role: 'manager', customerRef: 'd-sr45', kind: 'phone', door: 'debtor', at: t - 3 * DAY,
  });
  //  هر عضو «خروجیِ همه» را می‌نویسد (شاگرد هم) — یک ردیف در هر ۱۰ دقیقه؛ ۲۰۰ ردیف ⇒ ~۳۷ ساعت (کهنه‌ترین اول، مثلِ زمانِ واقعی)
  for (let k = 199; k >= 1; k--) {
    await access.record({
      shopId: A.shopId, viewerId: A.staff.user.id, role: 'staff', customerRef: '*', kind: 'export', door: 'app', at: t - k * 11 * MIN,
    });
  }
  ok(await h.post('/api/shop/customer-access', { customerId: '*', kind: 'export' }, { token: A.staff.token }));
  assert.equal((await one("SELECT count(*)::int AS n FROM customer_access_log WHERE shop_id=$1 AND customer_ref='*'", [A.shopId])).n, 200);
  for (const limit of [200, 100, 10]) {
    const list = ok(await h.get(`/api/shop/customer-access?customerId=d-sr45&limit=${limit}`, { token: A.token }));
    assert.ok(list.entries.length <= limit, `${list.entries.length} > ${limit}`);
    const own = list.entries.filter((e) => !e.all);
    assert.equal(own.length, 1, `limit=${limit}: دیدنِ واقعی در فهرست نیست (${list.entries.length} ردیفِ «همه»)`);
    assert.equal(own[0].viewerId, A.manager.user.id);
    assert.equal(own[0].kind, 'phone');
    assert.ok(list.entries.some((e) => e.all === true), 'خروجی‌های «همه» هم دیده می‌شوند');
    //  همان ترتیبِ تازه‌ترین اول
    for (let i = 1; i < list.entries.length; i++) assert.ok(list.entries[i - 1].lastAt >= list.entries[i].lastAt);
  }
  //  مشتریِ بی هیچ دیدنِ خودی: همهٔ جا برای `*` (رفتارِ پیشین)
  const other = ok(await h.get('/api/shop/customer-access?customerId=d-sr45-none&limit=50', { token: A.token }));
  assert.equal(other.entries.length, 50);
  assert.ok(other.entries.every((e) => e.all === true));
});

test('SR45-2: PIN ِ عضوِ بی‌اختیار یا ناموجود ⇒ ۴۰۳/۴۰۴ بی هیچ PBKDF2؛ نوشتنِ PIN بودجهٔ PBKDF2 ِ `/verify` را دارد', async () => {
  await tillFlag(A);
  const before = pbkdf2Calls;
  const up = await setPinFor(A.manager, A, '902817');
  assert.equal(up.status, 403, JSON.stringify(up.body));
  assert.equal(up.body.error?.code, 'till_pin_forbidden');
  const ghost = await setPinFor(A.manager, { memberId: 'mem_sr45_ghost' }, '902817');
  assert.equal(ghost.status, 404, JSON.stringify(ghost.body));
  assert.equal(pbkdf2Calls, before, 'اثرِ PBKDF2 پیش از سنجشِ اختیار ساخته شد');
  //  درست: یک PBKDF2
  ok(await setPinFor(A.manager, A.staff, '615083'));
  assert.equal(pbkdf2Calls, before + 1);

  //  سطلِ نوشتنِ PIN برای هر کاربر = همان ۲۰ در دقیقهٔ `/verify` (نه ۱۲۰ ِ همهٔ درها)
  resetAll();
  for (let i = 0; i < 20; i++) {
    const r = await h.put('/api/shop/till-pin/me', { pin: '12345' }, { token: A.manager.token });
    assert.equal(r.status, 400, `${i}: ${JSON.stringify(r.body)}`);
  }
  const over = await h.put('/api/shop/till-pin/me', { pin: '480317' }, { token: A.manager.token });
  assert.equal(over.status, 429, JSON.stringify(over.body));
  assert.equal((await setPinFor(A.manager, A.staff, '615083')).status, 429, 'همان سطل برای PIN ِ دیگری');
  ok(await h.put('/api/shop/till-pin/me', { pin: '480317' }, { token: A.token }));   //  کاربرِ دیگر باز
});

test('SR45-3: برداشتن یا تعلیقِ عضو PIN ِ صندوقش را پاک و نوبتِ بازش را می‌بندد؛ پس از آن «پشتِ دستگاه» او نیست', async () => {
  await tillFlag(A);
  const tp = require('../src/lib/till-pin');
  const shops = require('../src/lib/shops');
  const dev = await one('SELECT d.id FROM tokens t JOIN devices d ON d.id = t.device_id WHERE t.token_hash=$1', [hashToken(A.token)]);
  assert.ok(dev, 'نشستِ صاحب دستگاه دارد');
  ok(await setPinFor(A, A.staff, '615083'));

  const gone = await member(A, 'cashier', 'صندوق‌دارِ رفته');
  ok(await setPinFor(A, gone, '730264'));
  const s = ok(await verify(A, gone, '730264')).session;
  assert.equal(await tp.whoAt(A.shopId, dev.id, now() + 1), gone.memberId);
  ok(await h.del(`/api/shop/members/${gone.memberId}`, { token: A.token }));
  const after = now() + 1000;
  const row = await one('SELECT ended_at, end_reason FROM shop_till_sessions WHERE shop_id=$1 AND id=$2', [A.shopId, s.id]);
  assert.notEqual(row.ended_at, null, 'نوبتِ عضوِ برداشته باز ماند');
  assert.equal(row.end_reason, 'pin_removed');
  assert.equal(await tp.whoAt(A.shopId, dev.id, after), null);
  assert.ok(!(await one('SELECT 1 AS x FROM shop_till_pins WHERE shop_id=$1 AND member_id=$2', [A.shopId, gone.memberId])), 'PIN ِ عضوِ برداشته ماند');
  const hist = ok(await h.get(`/api/shop/till-pin/sessions?member=${gone.memberId}`, { token: A.token })).sessions;
  assert.equal(hist.length, 1);
  assert.notEqual(hist[0].endedAt, null);

  //  تعلیق ⇒ همان؛ دوباره فعال ⇒ PIN ِ کهنه زنده نمی‌شود (همان قاعدهٔ کلیدِ دستگاه: برگشت فقط با PIN ِ تازه)
  const held = await member(A, 'staff', 'شاگردِ معلق');
  ok(await setPinFor(A, held, '274096'));
  const s2 = ok(await verify(A, held, '274096')).session;
  await shops.updateMember(A.shopId, held.memberId, { status: 'suspended' });
  assert.notEqual((await one('SELECT ended_at FROM shop_till_sessions WHERE shop_id=$1 AND id=$2', [A.shopId, s2.id])).ended_at, null);
  await shops.updateMember(A.shopId, held.memberId, { status: 'active' });
  const back = await verify(A, held, '274096');
  assert.equal(back.status, 409, JSON.stringify(back.body));
  assert.equal(back.body.error?.code, 'till_pin_not_set');
  //  نوبت‌ها و PIN ِ عضوهای دیگر دست نخوردند
  assert.ok(await one('SELECT 1 AS x FROM shop_till_pins WHERE shop_id=$1 AND member_id=$2', [A.shopId, A.staff.memberId]));
});

const pinRow = (s, who) => one('SELECT pin_hash, failures, locked_until, set_by FROM shop_till_pins WHERE shop_id=$1 AND member_id=$2', [s.shopId, who.memberId]);
const auditCount = (s, who, action) => one(
  'SELECT count(*)::int AS n FROM audit_logs WHERE shop_id=$1 AND action=$2 AND target_id=$3', [s.shopId, action, who.memberId],
).then((r) => r.n);

test('R14: «۵ حدس، بازکردنِ قفل، ۵ حدس…» — بازکردن پلهٔ دوبرابر را نگه می‌دارد و دهمین خطا از آخرین درستی PIN را باطل می‌کند', async () => {
  await tillFlag(A);
  const sub = await member(A, 'cashier', 'زیردستِ R14');
  ok(await setPinFor(A, sub, '730264'));
  const unlock = () => h.post(`/api/shop/till-pin/members/${sub.memberId}/unlock`, {}, { token: A.manager.token });
  const round = async (prefix) => {
    for (let i = 0; i < 4; i++) {
      const r = await verify(A.manager, sub, `${prefix}${i}`);
      assert.equal(r.status, 403, `${prefix}${i}: ${JSON.stringify(r.body)}`);
    }
    const fifth = await verify(A.manager, sub, `${prefix}9`);
    assert.equal(fifth.status, 423, JSON.stringify(fifth.body));
    return fifth.body.error.lockedUntil;
  };
  //  دورِ اول ⇒ ۱۵ دقیقه؛ مدیر (با تأییدِ تازه) قفل را باز می‌کند ⇒ قفل نیست ولی شمار همان ۵
  assert.ok((await round('11122')) < now() + 16 * MIN);
  const opened = ok(await unlock());
  assert.equal((await pinRow(A, sub)).failures, 5, 'بازکردنِ قفل پلهٔ دوبرابر را صفر کرد');
  assert.equal(Number((await pinRow(A, sub)).locked_until), 0);
  assert.deepEqual([opened.unlocked, opened.untilInvalid], [true, 5]);
  //  دورِ دوم ⇒ پلهٔ دوبرابر (۳۰ دقیقه) و PIN باطل (دهمین خطا از آخرین درستی)
  assert.ok((await round('22233')) > now() + 29 * MIN, 'بازکردنِ قفل پلهٔ دوبرابر را از سر گرفت');
  assert.equal((await pinRow(A, sub)).pin_hash, '', 'PIN پس از ۱۰ خطا باطل نشد');
  //  بازکردنِ دوباره PIN ِ باطل را برنمی‌گرداند، پایانِ قفل هم نه: PIN ِ درست هم دیگر نه
  const again = await unlock();
  assert.equal(again.status, 409, JSON.stringify(again.body));
  assert.equal(again.body.error?.code, 'till_pin_invalidated');
  await query('UPDATE shop_till_pins SET locked_until=0 WHERE shop_id=$1 AND member_id=$2', [A.shopId, sub.memberId]);
  const right = await verify(A.manager, sub, '730264');
  assert.equal(right.status, 409, JSON.stringify(right.body));
  assert.equal(right.body.error?.code, 'till_pin_invalidated');
  const other = await verify(A.staff, sub, '730264');
  assert.equal(other.status, 403, 'به دیگران همان «غلط» (R15)');
  assert.equal(other.body.error?.code, 'till_pin_wrong');
  //  سابقه: یک بازکردن (دومی رد شد)، یک قفل، یک باطل شدن
  assert.equal(await auditCount(A, sub, 'till_pin.unlocked'), 1);
  assert.equal(await auditCount(A, sub, 'till_pin.locked'), 1);
  assert.equal(await auditCount(A, sub, 'till_pin.invalidated'), 1);
  //  صاحب/مدیر و خودِ عضو می‌بینند که PIN ِ تازه لازم است
  const seen = ok(await h.get('/api/shop/till-pin/members', { token: A.manager.token })).members.find((m) => m.memberId === sub.memberId);
  assert.deepEqual([seen.pinSet, seen.invalidated, seen.locked], [false, true, false]);
  const mine = ok(await h.get('/api/shop/till-pin/config', { token: sub.token }));
  assert.deepEqual([mine.pinSet, mine.invalidated], [false, true]);
  //  خودِ عضو (تأییدِ تازه) PIN ِ تازه می‌گذارد ⇒ شمار و قفل صفر و سنجش درست
  ok(await h.put('/api/shop/till-pin/me', { pin: '902817' }, { token: sub.token }));
  const fresh = await pinRow(A, sub);
  assert.deepEqual([fresh.failures, Number(fresh.locked_until), fresh.pin_hash !== ''], [0, 0, true]);
  ok(await verify(A.manager, sub, '902817'));
  //  PIN ِ درست شمار را صفر می‌کند: ۴ غلط، یک درست ⇒ ۰
  for (let i = 0; i < 4; i++) assert.equal((await verify(A.manager, sub, `55566${i}`)).status, 403);
  ok(await verify(A.manager, sub, '902817'));
  assert.equal((await pinRow(A, sub)).failures, 0);
  ok(await h.post('/api/shop/till-pin/current/end', { reason: 'lock' }, { token: A.manager.token }));
});

test('R14: صاحب هرگز بیرون نمی‌ماند — PIN ِ باطل‌شدهٔ صاحب با تأییدِ دوبارهٔ ورود از نو، قفل و شمار و باطل شدن پاک', async () => {
  await tillFlag(A);
  ok(await h.put('/api/shop/till-pin/me', { pin: '482915' }, { token: A.token }));
  for (let r = 0; r < 2; r++) {
    for (let i = 0; i < 5; i++) await verify(A.staff, A, `33344${i}`);
    assert.ok(Number((await pinRow(A, A)).locked_until) > now(), `دورِ ${r}`);
    //  پایانِ قفل (شاگرد و مدیر قفلِ صاحب را باز نمی‌کنند)
    await query('UPDATE shop_till_pins SET locked_until=$3 WHERE shop_id=$1 AND member_id=$2', [A.shopId, A.memberId, now() - 1]);
  }
  assert.equal((await pinRow(A, A)).pin_hash, '');
  const self = await verify(A, A, '482915');
  assert.equal(self.status, 409, JSON.stringify(self.body));
  assert.equal(self.body.error?.code, 'till_pin_invalidated');
  //  نشستِ کهنه ⇒ تأییدِ دوبارهٔ ورود با رمزِ حساب ⇒ PIN ِ تازه ⇒ همه پاک
  await query(`DELETE FROM login_attempts WHERE scope='session_auth' AND identifier=$1`, [hashToken(A.token)]);
  assert.equal((await h.put('/api/shop/till-pin/me', { pin: '518206' }, { token: A.token })).body.error?.code, 'reauth_required');
  ok(await h.post('/api/auth/reauth', { password: A.password }, { token: A.token }));
  ok(await h.put('/api/shop/till-pin/me', { pin: '518206' }, { token: A.token }));
  const row = await pinRow(A, A);
  assert.deepEqual([row.failures, Number(row.locked_until), row.pin_hash !== '', row.set_by], [0, 0, true, A.memberId]);
  const c = ok(await h.get('/api/shop/till-pin/config', { token: A.token }));
  assert.deepEqual([c.pinSet, c.invalidated, c.locked], [true, false, false]);
  assert.equal(ok(await verify(A.manager, A, '518206')).session.memberId, A.memberId);
  ok(await h.post('/api/shop/till-pin/current/end', { reason: 'lock' }, { token: A.manager.token }));
});

test('R15: `/verify` به دیگران نمی‌گوید عضو PIN دارد یا نه — همان وضع و بدنه، همان PBKDF2، همان شمار و قفل؛ خودِ عضو و صاحب/مدیر می‌بینند', async () => {
  await tillFlag(A);
  const bare = await member(A, 'cashier', 'بی‌PIN ِ R15');
  const withPin = await member(A, 'cashier', 'با‌PIN ِ R15');
  ok(await setPinFor(A, withPin, '274096'));
  const shape = (r) => {
    const e = r.body.error || {};
    return { status: r.status, keys: Object.keys(e).sort(), code: e.code, message: e.message, left: e.attemptsLeft };
  };
  const probe = async (target, pin) => {
    const before = pbkdf2Calls;
    pbkdf2Iter = 0;
    const r = await verify(A.staff, target, pin);
    return { r, work: pbkdf2Calls - before, iter: pbkdf2Iter };
  };
  //  یکمی بدشکل (هیچ‌کدام PBKDF2 ندارند ولی شمرده می‌شود)، بقیه با یک PBKDF2 با همان دور؛ پنجمی قفل؛ ششمی قفل بی PBKDF2
  for (const [i, pin] of ['12', '405171', '405172', '405173', '405174', '405175'].entries()) {
    const a = await probe(bare, pin);
    const b = await probe(withPin, pin);
    assert.deepEqual(shape(a.r), shape(b.r), `${i}: ${JSON.stringify(a.r.body)} ≠ ${JSON.stringify(b.r.body)}`);
    assert.deepEqual([a.work, a.iter], [b.work, b.iter], `${i}: هزینهٔ سنجش یکی نیست`);
    assert.equal(a.work, i === 0 || i === 5 ? 0 : 1, `${i}`);
    assert.equal(a.r.status, i < 4 ? 403 : 423, `${i}`);
    if (i >= 4) assert.ok(Math.abs(a.r.body.error.retryAfter - b.r.body.error.retryAfter) <= 2);
  }
  //  خودِ عضو و صاحب/مدیر همان «ندارد» را می‌بینند (و نمی‌شمارد)
  for (const who of [bare, A, A.manager]) {
    const r = await verify(who, bare, '405176');
    assert.equal(r.status, 409, JSON.stringify(r.body));
    assert.equal(r.body.error?.code, 'till_pin_not_set');
  }
  const c = ok(await h.get('/api/shop/till-pin/config', { token: bare.token }));
  assert.deepEqual([c.pinSet, c.invalidated, c.locked], [false, false, false]);
  const seen = ok(await h.get('/api/shop/till-pin/members', { token: A.manager.token })).members.find((m) => m.memberId === bare.memberId);
  assert.deepEqual([seen.pinSet, seen.invalidated, seen.locked], [false, false, false]);
  //  عضو PIN ِ خودش را می‌گذارد ⇒ شمار و قفلِ جانشین پاک و سنجش درست
  ok(await h.put('/api/shop/till-pin/me', { pin: '390471' }, { token: bare.token }));
  assert.equal(ok(await verify(A.staff, bare, '390471')).session.memberId, bare.memberId);
  ok(await h.post('/api/shop/till-pin/current/end', { reason: 'lock' }, { token: A.staff.token }));
  //  حدس‌های هم‌زمان روی عضوِ بی PIN همان شکلِ عضوِ با PIN (`till-pin.test.js`): دقیقاً ۵ شمرده، بقیه ۴۲۳
  resetAll();
  const lone = await member(A, 'staff', 'بی‌PIN ِ هم‌زمان');
  const out = await Promise.all(Array.from({ length: 9 }, (_, i) => verify(A.staff, lone, `00020${i}`)));
  assert.deepEqual(out.map((r) => r.status).sort(), [403, 403, 403, 403, 423, 423, 423, 423, 423], JSON.stringify(out.map((r) => r.body)));
  assert.equal((await pinRow(A, lone)).failures, 5);
});
