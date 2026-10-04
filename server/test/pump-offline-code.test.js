'use strict';
/**
 * ══ کدِ اشتراکِ آفلاین — سه پلن، بسته به یک کامپیوتر ═══════════════════
 *
 * «برای کسانی که نت ندارن هم اشتراک بدم… و اگه یارو اینترنت پیدا کرد،
 * سرور همون کد رو ببینه و بگه آره این حساب اشتراک داره.»
 *
 * هر بند از همان درهای واقعی می‌رود: پنلِ مدیر (‎/api/admin/pump/offline-codes‎)
 * و دستگاهِ بندشده (‎/api/pump/device/offline-code‎)، روی دیتابیسِ واقعی.
 */
const test = require('node:test');
const assert = require('node:assert/strict');
const crypto = require('crypto');
const h = require('./helpers');
const { query, one, newId, now } = require('../src/db');
const offline = require('../src/lib/offline-codes');
const license = require('../src/lib/license');

test.before(async () => {
  await h.start();
  const pw = require('../src/lib/password');
  await query(
    `INSERT INTO admins (id, username, name, password_hash, role, status, created_at)
     VALUES ($1,'admin','مدیر',$2,'superadmin','active',$3)`,
    [newId('adm'), await pw.hashPassword('Admin!12345'), now()]
  );
});
test.after(async () => { await h.stop(); });

const DAY = 86400000;
const PC_A = offline.computerOfFingerprint('m-' + 'a'.repeat(32));
const PC_B = offline.computerOfFingerprint('m-' + 'b'.repeat(32));

async function admin() {
  return (await h.post('/api/admin/login', { username: 'admin', password: 'Admin!12345' })).body.token;
}

async function issue(body) {
  const r = await h.post('/api/admin/pump/offline-codes', body, { token: await admin() });
  return r;
}

/** حسابِ تازه با پمپ و دستگاهِ بندشده ⇒ توکنِ دستگاه */
async function boundDevice(uid) {
  const u = await h.newUser('صاحبِ پمپ ' + uid, 'pump');
  const st = await h.post('/api/pump', { name: 'پمپِ ' + uid }, { token: u.accessToken });
  assert.equal(st.status, 201, JSON.stringify(st.body));
  const b = await h.post('/api/pump/device/bind',
    { device: { uid, name: 'کامپیوتر', platform: 'windows' } }, { token: u.accessToken });
  assert.equal(b.status, 201, JSON.stringify(b.body));
  return { token: b.body.deviceToken, stationId: st.body.station.id, email: u.email, userId: u.user?.id };
}

test('کدِ کامپیوتر: ۱۶ نویسه، نویسهٔ سنجش خطای تایپ را می‌گیرد', () => {
  assert.match(PC_A, /^[0-9A-Z]{4}-[0-9A-Z]{4}-[0-9A-Z]{4}-[0-9A-Z]{4}$/);
  assert.ok(offline.parseComputer(PC_A));
  assert.ok(offline.parseComputer(PC_A.toLowerCase().replace(/-/g, ' ')), 'حروفِ کوچک و فاصله هم');
  //  یک نویسه عوض ⇒ رد
  const bad = (PC_A[0] === 'A' ? 'B' : 'A') + PC_A.slice(1);
  assert.equal(offline.parseComputer(bad), null);
  //  دو نویسهٔ کنارِ هم جابه‌جا ⇒ رد (وزن‌دار است، نه جمعِ ساده)
  const s = PC_A.replace(/-/g, '');
  if (s[0] !== s[1]) assert.equal(offline.parseComputer(s[1] + s[0] + s.slice(2)), null);
  assert.equal(offline.parseComputer('ABCD'), null);
});

test('صدورِ سه پلن: امضا با کلیدِ مجوز، فایلِ ‎.pumpkey‎، و شکلِ دودویی', async () => {
  for (const [plan, days, perm] of [['std', 30, false], ['vip', 365, false], ['perm', null, true]]) {
    const r = await issue({ plan, computer: PC_A, days, note: 'کامپیوترِ قدیمی' });
    assert.equal(r.status, 201, JSON.stringify(r.body));
    const { code, offline: o, file } = r.body;
    assert.match(code, /^([0-9A-Z]{5}-)+[0-9A-Z]{1,5}$/);
    assert.equal(code.replace(/-/g, '').length, 138, 'نسخهٔ ۲: کوتاه‌تر (بود ۱۵۷)');
    assert.equal(o.plan, plan);
    assert.equal(o.permanent, perm);
    assert.equal(o.computer, PC_A);
    assert.equal(file.format, 'pumpyaqobi-offline-key');
    assert.equal(file.code, code);

    const p = await offline.verify(code);
    assert.ok(p, 'امضای خودِ سرور پذیرفته است');
    assert.equal(p.plan, plan);
    assert.equal(p.serial, o.serial);
    assert.equal(p.version, 2);
    //  دستِ‌کم `days` روزِ کامل از همین لحظه
    if (!perm) assert.ok(p.endsAt - now() >= days * DAY && p.endsAt - now() <= (days + 1) * DAY);
    assert.ok(p.issuedAt <= now() && now() - p.issuedAt < DAY, 'روزِ صدور، نه آینده');
    //  امضا واقعاً ES256 روی «PYOC2\0» + بدنه است، با کلیدِ عمومیِ منتشرشده
    const spki = Buffer.from(await license.publicKey(), 'base64');
    const key = crypto.createPublicKey({ key: spki, format: 'der', type: 'spki' });
    assert.ok(crypto.verify('sha256', Buffer.concat([Buffer.from('PYOC2\0', 'latin1'), p.body]),
      { key, dsaEncoding: 'ieee-p1363' }, p.sig));
  }
});

test('⛔ یک بیتِ عوض‌شده ⇒ کد باطل؛ پلن و کامپیوترِ غلط ⇒ ۴۰۰', async () => {
  const r = await issue({ plan: 'vip', computer: PC_A, days: 10 });
  const raw = offline.b32decode(r.body.code, offline.TOTAL2);
  raw[1] = 3;                                   // وی‌آی‌پی ⇒ دائمی
  assert.equal(await offline.verify(offline.b32encode(raw)), null);

  assert.equal((await issue({ plan: 'gold', computer: PC_A })).status, 400);
  assert.equal((await issue({ plan: 'std', computer: 'ABCD-EFGH' })).status, 400);
  assert.equal((await issue({ plan: 'std', computer: PC_A, days: 0 })).status, 400);
});

test('آنلاین شد ⇒ همان کد اشتراکِ پمپ می‌شود، و مجوزِ همان دور قفل‌ها را باز دارد', async () => {
  const d = await boundDevice('pc-offline-1');
  const r = await issue({ plan: 'vip', computer: PC_A, days: 400 });
  const red = await h.post('/api/pump/device/offline-code',
    { code: r.body.code, computer: PC_A }, { token: d.token });
  assert.equal(red.status, 200, JSON.stringify(red.body));
  assert.equal(red.body.status, 'granted');
  assert.equal(red.body.source, 'subscription');
  assert.ok(red.body.license, 'مجوزِ امضاشده در همان پاسخ');
  assert.ok(red.body.features.includes('kar_app'), 'پلنِ وی‌آی‌پی');

  const sub = await one(`SELECT * FROM station_subscriptions WHERE station_id=$1 AND status='active'`, [d.stationId]);
  assert.equal(sub.plan, 'vip');
  assert.equal(Number(sub.ends_at), r.body.offline.endsAt);

  const row = await one(`SELECT * FROM pump_offline_codes WHERE serial=$1`, [r.body.offline.serial]);
  assert.equal(row.redeemed_station_id, d.stationId, 'دفترِ پنل می‌داند کجا نشست');

  //  دوباره آوردنِ همان کد ⇒ بی‌خطر، روزی اضافه نمی‌شود
  const again = await h.post('/api/pump/device/offline-code',
    { code: r.body.code, computer: PC_A }, { token: d.token });
  assert.equal(again.status, 200);
  assert.equal(again.body.status, 'covered');
  const sub2 = await one(`SELECT * FROM station_subscriptions WHERE station_id=$1 AND status='active'`, [d.stationId]);
  assert.equal(Number(sub2.ends_at), Number(sub.ends_at));
});

test('⛔ کامپیوترِ دیگر · پمپِ دیگر · کدِ باطل‌شده', async () => {
  const d1 = await boundDevice('pc-offline-2');
  const d2 = await boundDevice('pc-offline-3');
  const r = await issue({ plan: 'std', computer: PC_A, days: 30 });

  const wrongPc = await h.post('/api/pump/device/offline-code',
    { code: r.body.code, computer: PC_B }, { token: d1.token });
  assert.equal(wrongPc.status, 400);
  assert.equal(wrongPc.body.error?.code || wrongPc.body.code, 'computer_mismatch');

  assert.equal((await h.post('/api/pump/device/offline-code',
    { code: r.body.code, computer: PC_A }, { token: d1.token })).status, 200);
  const other = await h.post('/api/pump/device/offline-code',
    { code: r.body.code, computer: PC_A }, { token: d2.token });
  assert.equal(other.status, 409, 'یک کد، یک پمپ');

  const r2 = await issue({ plan: 'vip', computer: PC_A, days: 30 });
  const rev = await h.post(`/api/admin/pump/offline-codes/${r2.body.offline.id}/revoke`, {}, { token: await admin() });
  assert.equal(rev.status, 200);
  assert.equal(rev.body.offline.status, 'revoked');
  const gone = await h.post('/api/pump/device/offline-code',
    { code: r2.body.code, computer: PC_A }, { token: d2.token });
  assert.equal(gone.status, 410);
});

test('⛔ کدِ کوتاه‌تر اشتراکِ بلندترِ موجود را کوتاه نمی‌کند', async () => {
  const d = await boundDevice('pc-offline-4');
  const t = await admin();
  const g = await h.post('/api/admin/pump/subscriptions', { stationId: d.stationId, plan: 'vip', days: 700 }, { token: t });
  assert.equal(g.status, 201);
  const r = await issue({ plan: 'std', computer: PC_A, days: 30 });
  const red = await h.post('/api/pump/device/offline-code',
    { code: r.body.code, computer: PC_A }, { token: d.token });
  assert.equal(red.body.status, 'covered');
  const sub = await one(`SELECT * FROM station_subscriptions WHERE station_id=$1 AND status='active'`, [d.stationId]);
  assert.equal(sub.plan, 'vip');
});

test('دائمی ⇒ پنجاه سال · فهرستِ پنل · بی توکنِ دستگاه ⇒ ۴۰۱', async () => {
  const d = await boundDevice('pc-offline-5');
  const r = await issue({ plan: 'perm', computer: PC_A });
  const red = await h.post('/api/pump/device/offline-code',
    { code: r.body.code, computer: PC_A }, { token: d.token });
  assert.equal(red.body.status, 'granted');
  const sub = await one(`SELECT * FROM station_subscriptions WHERE station_id=$1 AND status='active'`, [d.stationId]);
  assert.equal(sub.plan, 'perm');
  assert.ok(Number(sub.ends_at) > now() + 40 * 365 * DAY);

  const list = await h.get('/api/admin/pump/offline-codes', { token: await admin() });
  assert.equal(list.status, 200);
  assert.ok(list.body.codes.some(c => c.serial === r.body.offline.serial && c.redeemedStationId === d.stationId));
  assert.ok(!JSON.stringify(list.body).includes(r.body.code), 'خودِ کد در فهرست نیست');

  const anon = await h.post('/api/pump/device/offline-code', { code: r.body.code, computer: PC_A });
  assert.equal(anon.status, 401);
});

test('نسخهٔ ۲: بسته به حساب — حسابِ دیگر ⇒ ۴۰۹، ایمیلِ ناشناس ⇒ ۴۰۴', async () => {
  const d1 = await boundDevice('pc-offline-acct-1');
  const d2 = await boundDevice('pc-offline-acct-2');
  const r = await issue({ plan: 'vip', computer: PC_A, days: 60, account: d1.email.toUpperCase() });
  assert.equal(r.status, 201, JSON.stringify(r.body));
  assert.equal(r.body.offline.accountEmail, d1.email);
  assert.equal(r.body.file.account, d1.email);
  const p = await offline.verify(r.body.code);
  assert.ok(p.account.equals(offline.accountTag(d1.userId)), 'چهار بایتِ حساب در کد');

  const wrong = await h.post('/api/pump/device/offline-code',
    { code: r.body.code, computer: PC_A }, { token: d2.token });
  assert.equal(wrong.status, 409);
  assert.equal(wrong.body.error?.code || wrong.body.code, 'account_mismatch');
  const ok = await h.post('/api/pump/device/offline-code',
    { code: r.body.code, computer: PC_A }, { token: d1.token });
  assert.equal(ok.status, 200, JSON.stringify(ok.body));
  assert.equal(ok.body.status, 'granted');

  assert.equal((await issue({ plan: 'std', computer: PC_A, account: 'nobody@nowhere.test' })).status, 404);
  //  بی حساب ⇒ هر حسابی (کامپیوتری که هرگز آنلاین نشده)
  const any = await issue({ plan: 'std', computer: PC_A, days: 5 });
  assert.ok((await offline.verify(any.body.code)).account.equals(Buffer.alloc(4)));
});

test('⛔ کدهای نسخهٔ ۱ که پیش از این صادر شده‌اند همچنان پذیرفته‌اند', async () => {
  //  همان شکلِ دودوییِ نسخهٔ ۱، با کلیدِ همین سرور
  const pc = offline.parseComputer(PC_A);
  const body = Buffer.alloc(offline.BODY_LEN);
  body[0] = 1; body[1] = 2;
  pc.machine.copy(body, 2);
  crypto.randomBytes(6).copy(body, 12);
  const at = Math.floor(now() / 1000);
  body.writeUInt32BE(at, 18);
  body.writeUInt32BE(at + 30 * 86400, 22);
  Buffer.from(await license.keyId(), 'hex').subarray(0, 8).copy(body, 26);
  const sig = await license.signBytes(Buffer.concat([Buffer.from('PYOC1\0', 'latin1'), body]));
  const code = offline.b32encode(Buffer.concat([body, sig]));
  assert.equal(code.length, 157);
  const p = await offline.verify(code);
  assert.ok(p);
  assert.equal(p.version, 1);
  const d = await boundDevice('pc-offline-v1');
  const red = await h.post('/api/pump/device/offline-code', { code, computer: PC_A }, { token: d.token });
  assert.equal(red.status, 200, JSON.stringify(red.body));
  assert.equal(red.body.status, 'granted');
});
