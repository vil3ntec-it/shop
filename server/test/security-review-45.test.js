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
 */
process.env.TILL_PIN_ITER = '1000';

const crypto = require('node:crypto');
//  SR45-2: شمارِ PBKDF2 — پیش از بار شدنِ برنامه، چون `lib/till-pin.js` همان `crypto.pbkdf2` را promisify می‌کند
let pbkdf2Calls = 0;
const realPbkdf2 = crypto.pbkdf2;
crypto.pbkdf2 = function countedPbkdf2(...args) { pbkdf2Calls += 1; return realPbkdf2.apply(this, args); };

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
