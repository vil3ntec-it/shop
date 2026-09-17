'use strict';
/**
 * پشتیبانیِ صاحبِ پمپ ↔ مدیرِ سامانه، و پیامِ همگانی که به پمپ هم می‌رسد.
 *
 * ── دو حفره‌ای که این‌جا بسته می‌شود ────────────────────────────────
 *
 * ۱) پمپ‌دار هیچ راهی برای پیام دادن به مدیر نداشت. `/api/support`
 *    مالِ دکان بود و `/pump/device/chat` گفت‌وگوی **مشتریِ کیوآر** با
 *    صاحبِ پمپ است، نه با کسی که برنامه را ساخته.
 *
 * ۲) `/admin/support/broadcast` فقط جدولِ `shops` را می‌گرفت. هر پیامِ
 *    همگانی‌ای که مدیر می‌فرستاد به هیچ پمپی نمی‌رسید — و پاسخ هم
 *    «۴۲ نفر» می‌گفت، پس هیچ‌کس خبردار نمی‌شد.
 */
const test = require('node:test');
const assert = require('node:assert/strict');
const h = require('./helpers');
const { query, one, newId, now } = require('../src/db');

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

const adminToken = async () =>
  (await h.post('/api/admin/login', { username: 'admin', password: 'Admin!12345' })).body.token;

/** برنامهٔ کامپیوترِ پمپ — بی حساب، فقط کدِ شش‌رقمی. */
async function pumpDevice(uid, days = 90) {
  const t = await adminToken();
  const made = await h.post('/api/admin/pump/vip-codes', { plan: 'custom', days }, { token: t });
  assert.equal(made.status, 201, JSON.stringify(made.body));
  const on = await h.post('/api/pump/device/activate', {
    code: made.body.code,
    device: { uid, name: 'کامپیوترِ پمپ', platform: 'windows' },
    station: { code: uid, name: `پمپِ ${uid}` },
  });
  assert.equal(on.status, 201, JSON.stringify(on.body));
  return { token: on.body.deviceToken, stationId: on.body.station.id };
}

async function pumpOwner(name, code = '') {
  const u = await h.newUser(name, 'pump');
  const made = await h.post('/api/pump', { name: `پمپِ ${name}`, code }, { token: u.accessToken });
  assert.equal(made.status, 201, JSON.stringify(made.body));
  return { ...u, stationId: made.body.station.id };
}

// ══════════════════════════════════════════════════════════════════
//  ۱) کامپیوترِ پمپ — بی حساب، ولی صدایش شنیده می‌شود
// ══════════════════════════════════════════════════════════════════

test('کامپیوترِ پمپ بی هیچ حسابی به مدیر پیام می‌دهد و جواب می‌گیرد', async () => {
  const d = await pumpDevice('pc-sup-1');
  const t = await adminToken();

  const first = await h.get('/api/pump/device/support/thread', { token: d.token });
  assert.equal(first.status, 200, JSON.stringify(first.body));
  assert.equal(first.body.thread.app, 'pump');
  assert.equal(first.body.thread.stationId, d.stationId);
  assert.deepEqual(first.body.messages, []);

  const sent = await h.post('/api/pump/device/support/messages',
    { body: 'چاپ کار نمی‌کند' }, { token: d.token });
  assert.equal(sent.status, 201, JSON.stringify(sent.body));
  assert.equal(sent.body.message.sender, 'user');
  assert.equal(sent.body.thread.unreadAdmin, 1);

  //  مدیر می‌بیندش — و می‌داند از کدام پمپ است
  const threads = await h.get('/api/admin/support/threads?app=pump', { token: t });
  assert.equal(threads.status, 200);
  const mine = threads.body.threads.find(x => x.stationId === d.stationId);
  assert.ok(mine, 'رشتهٔ این پمپ باید در فهرستِ مدیر باشد');
  assert.equal(mine.stationName, 'پمپِ pc-sup-1', 'نامِ پمپ باید بیاید، نه فقط شناسه');

  const reply = await h.post(`/api/admin/support/threads/${mine.id}/messages`,
    { body: 'درایورِ چاپگر را دوباره نصب کنید' }, { token: t });
  assert.equal(reply.status, 201, JSON.stringify(reply.body));

  const after = await h.get('/api/pump/device/support/thread', { token: d.token });
  assert.equal(after.body.messages.length, 2);
  assert.equal(after.body.messages[1].sender, 'admin');
  assert.equal(after.body.thread.unreadUser, 1);

  const read = await h.post('/api/pump/device/support/read', {}, { token: d.token });
  assert.equal(read.status, 200);
  const cleared = await h.get('/api/pump/device/support/thread', { token: d.token });
  assert.equal(cleared.body.thread.unreadUser, 0);
});

test('یک پمپ، یک گفت‌وگو — کامپیوتر و گوشیِ صاحب به همان می‌رسند', async () => {
  const d = await pumpDevice('pc-sup-2');
  await h.post('/api/pump/device/support/messages', { body: 'از کامپیوتر' }, { token: d.token });

  //  صاحبِ پمپ با کدِ پیوستن می‌آید
  const owner = await h.newUser('صاحبِ دوم', 'pump');
  const join = await h.post('/api/pump/device/join-code', {}, { token: d.token });
  const claimed = await h.post('/api/pump/claim', { code: join.body.code }, { token: owner.accessToken });
  assert.equal(claimed.status, 201, JSON.stringify(claimed.body));

  const fromPhone = await h.get('/api/pump/support/thread', { token: owner.accessToken });
  assert.equal(fromPhone.status, 200, JSON.stringify(fromPhone.body));
  assert.equal(fromPhone.body.messages.length, 1, 'همان گفت‌وگو، نه یکی تازه');
  assert.equal(fromPhone.body.messages[0].body, 'از کامپیوتر');

  await h.post('/api/pump/support/messages', { body: 'از گوشی' }, { token: owner.accessToken });

  const backOnPc = await h.get('/api/pump/device/support/thread', { token: d.token });
  assert.equal(backOnPc.body.messages.length, 2, 'کامپیوتر باید پیامِ گوشی را ببیند');
});

test('هر پمپ فقط گفت‌وگوی خودش را می‌بیند', async () => {
  const a = await pumpDevice('pc-sup-a');
  const b = await pumpDevice('pc-sup-b');
  await h.post('/api/pump/device/support/messages', { body: 'رازِ الف' }, { token: a.token });

  const theirs = await h.get('/api/pump/device/support/thread', { token: b.token });
  assert.deepEqual(theirs.body.messages, [], 'پمپِ ب نباید پیامِ الف را ببیند');
  assert.notEqual(theirs.body.thread.id, undefined);
});

test('پمپ‌دارِ بی‌پمپ می‌فهمد چرا نمی‌شود', async () => {
  const u = await h.newUser('بی‌پمپِ پشتیبانی', 'pump');
  const r = await h.get('/api/pump/support/thread', { token: u.accessToken });
  assert.equal(r.status, 403);
  assert.equal(r.body.error.code, 'no_station');
});

test('توکنِ دکان درِ پشتیبانیِ پمپ را باز نمی‌کند', async () => {
  const s = await h.newUser('دکان‌دارِ کنجکاو');
  const r = await h.get('/api/pump/support/thread', { token: s.accessToken });
  assert.equal(r.status, 401);
});

test('پیامِ خالی و پیامِ خیلی بلند رد می‌شوند — نه بریده می‌شوند', async () => {
  const support = require('../src/lib/support');
  const d = await pumpDevice('pc-sup-3');

  const empty = await h.post('/api/pump/device/support/messages', { body: '   ' }, { token: d.token });
  assert.equal(empty.status, 400);
  assert.equal(empty.body.error.code, 'empty_message');

  /*
   *  ⛔ تا دیروز این ۲۰۱ می‌داد: مسیرها `v.text` می‌زدند که متن را
   *  **می‌بُرد**، پس نگهبانِ `message_too_long` هیچ‌وقت شلیک نمی‌کرد و
   *  کاربر «فرستاده شد» می‌دید در حالی که هزار نویسهٔ آخرش رفته بود.
   */
  const long = await h.post('/api/pump/device/support/messages',
    { body: 'ا'.repeat(support.MAX_BODY + 1) }, { token: d.token });
  assert.equal(long.status, 400);
  assert.equal(long.body.error.code, 'message_too_long');

  //  و درست روی خطِ سقف، می‌رود
  const edge = await h.post('/api/pump/device/support/messages',
    { body: 'ا'.repeat(support.MAX_BODY) }, { token: d.token });
  assert.equal(edge.status, 201);

  const thread = await h.get('/api/pump/device/support/thread', { token: d.token });
  assert.equal(thread.body.messages.length, 1, 'فقط همان یکی که مجاز بود');
  assert.equal(thread.body.messages[0].body.length, support.MAX_BODY, 'و بریده نشده');
});

test('همین قاعده در پشتیبانیِ دکان هم هست — یک زبان برای هر دو', async () => {
  const support = require('../src/lib/support');
  const u = await h.newUser('دکان‌دارِ پرحرف');
  const long = await h.post('/api/support/messages',
    { body: 'ب'.repeat(support.MAX_BODY + 1), deviceUid: 'dev-long-1' }, { token: u.accessToken });
  assert.equal(long.status, 400);
  assert.equal(long.body.error.code, 'message_too_long');
});

test('سقفِ پیامِ همگانی واقعاً می‌گیرد', async () => {
  /*
   *  سقف از محیط خوانده می‌شود و در آزمون‌ها باز است، وگرنه بقیهٔ
   *  سنجه‌های همین فایل همدیگر را می‌بستند. پس این‌جا مستقیم روی
   *  میان‌افزار سنجیده می‌شود، نه با زدنِ شش‌بارهٔ مسیر.
   */
  const { rateLimit } = require('../src/middleware/ratelimit');
  const limiter = rateLimit({ max: 2, keyPrefix: `bc-test-${Date.now()}` });
  const fake = () => ({ ip: '10.0.0.9', headers: {}, socket: { remoteAddress: '10.0.0.9' } });

  //  ⚠️ این میان‌افزار پاسخ نمی‌نویسد؛ خطا را به `next` می‌دهد و
  //  `errorHandler` آن را ۴۲۹ می‌کند. سنجه‌ای که فقط به `res.status`
  //  نگاه کند، همیشه ۲۰۰ می‌بیند و سبزِ دروغ می‌دهد.
  const run = () => new Promise((resolve) => {
    const res = { set() { return res; } };
    limiter(fake(), res, (err) => resolve(err ? err.status : 200));
  });
  assert.equal(await run(), 200);
  assert.equal(await run(), 200);
  assert.equal(await run(), 429, 'سومی باید بیفتد');
  assert.equal(require('../src/config').rateLimit.broadcastMax > 0, true, 'سقف باید تنظیم‌پذیر باشد');
});

test('پشتیبانی حتی با اشتراکِ تمام‌شده باز است', async () => {
  const d = await pumpDevice('pc-sup-4');
  await query("UPDATE station_subscriptions SET status='expired', ends_at=$1 WHERE station_id=$2",
    [now() - 1000, d.stationId]);
  await require('../src/lib/plans').setConfig('pump_trial_days', '0');

  const sent = await h.post('/api/pump/device/support/messages',
    { body: 'اشتراکم تمام شده، چه کنم؟' }, { token: d.token });
  assert.equal(sent.status, 201, 'پشتیبانی یکی از واجبات است و قفل نمی‌شود');

  await require('../src/lib/plans').setConfig('pump_trial_days', '14');
});

test('ثبتِ توکنِ پوش از کامپیوترِ پمپ پذیرفته می‌شود', async () => {
  const d = await pumpDevice('pc-sup-5');
  const r = await h.post('/api/pump/device/support/push',
    { token: 'fcm-token-pump-1234567890', platform: 'windows' }, { token: d.token });
  assert.equal(r.status, 201, JSON.stringify(r.body));

  const row = await one("SELECT * FROM push_tokens WHERE token=$1", ['fcm-token-pump-1234567890']);
  assert.ok(row, 'توکن باید واقعاً ثبت شده باشد');
  assert.equal(row.app, 'pump');

  const gone = await h.del('/api/pump/device/support/push?token=fcm-token-pump-1234567890', { token: d.token });
  assert.equal(gone.status, 200);
});

// ══════════════════════════════════════════════════════════════════
//  ۲) پیامِ همگانی — به پمپ هم می‌رسد
// ══════════════════════════════════════════════════════════════════

test('پیامِ همگانیِ پمپ به پمپِ بی‌صاحب هم می‌رسد', async () => {
  const t = await adminToken();
  const d = await pumpDevice('pc-bc-1');

  const out = await h.post('/api/admin/support/broadcast',
    { body: 'فردا سرور نیم ساعت خاموش است', target: 'all', app: 'pump' }, { token: t });
  assert.equal(out.status, 200, JSON.stringify(out.body));
  assert.equal(out.body.app, 'pump');
  assert.ok(out.body.sent >= 1, 'دستِ‌کم به همین پمپ باید رفته باشد');
  assert.equal(out.body.failed, 0);

  const seen = await h.get('/api/pump/device/support/thread', { token: d.token });
  assert.equal(seen.body.messages.length, 1);
  assert.equal(seen.body.messages[0].sender, 'system');
  assert.equal(seen.body.messages[0].body, 'فردا سرور نیم ساعت خاموش است');
});

test('پیامِ همگانیِ دکان به پمپ نمی‌رود و برعکس', async () => {
  const t = await adminToken();
  const d = await pumpDevice('pc-bc-2');

  const shopUser = await h.newUser('دکان‌دارِ خبردار');
  await h.post('/api/shop', { name: 'دکانِ خبردار' }, { token: shopUser.accessToken });

  const toShops = await h.post('/api/admin/support/broadcast',
    { body: 'فقط دکان‌ها', target: 'all', app: 'shop' }, { token: t });
  assert.equal(toShops.status, 200);

  const pumpSide = await h.get('/api/pump/device/support/thread', { token: d.token });
  assert.deepEqual(pumpSide.body.messages, [], 'پیامِ دکان نباید به پمپ برسد');

  const shopSide = await h.get('/api/support/thread', { token: shopUser.accessToken });
  assert.ok(shopSide.body.messages.some(m => m.body === 'فقط دکان‌ها'));

  //  و حالا فقط پمپ
  const toPumps = await h.post('/api/admin/support/broadcast',
    { body: 'فقط پمپ‌ها', target: 'all', app: 'pump' }, { token: t });
  assert.equal(toPumps.status, 200);

  const shopAgain = await h.get('/api/support/thread', { token: shopUser.accessToken });
  assert.ok(!shopAgain.body.messages.some(m => m.body === 'فقط پمپ‌ها'),
    'پیامِ پمپ نباید در گفت‌وگوی دکان بنشیند');
});

test('«هر دو» یعنی هر دو — و شمارش درست است', async () => {
  const t = await adminToken();
  const d = await pumpDevice('pc-bc-3');
  const shopUser = await h.newUser('دکان‌دارِ دوگانه');
  await h.post('/api/shop', { name: 'دکانِ دوگانه' }, { token: shopUser.accessToken });

  const both = await h.post('/api/admin/support/broadcast',
    { body: 'به همه', target: 'all', app: 'both' }, { token: t });
  assert.equal(both.status, 200, JSON.stringify(both.body));
  assert.equal(both.body.app, 'both');
  assert.equal(both.body.sent, both.body.targets, 'همه باید رفته باشند');

  assert.ok((await h.get('/api/pump/device/support/thread', { token: d.token }))
    .body.messages.some(m => m.body === 'به همه'));
  assert.ok((await h.get('/api/support/thread', { token: shopUser.accessToken }))
    .body.messages.some(m => m.body === 'به همه'));
});

test('بخشِ ناشناخته در پیامِ همگانی رد می‌شود', async () => {
  const t = await adminToken();
  const r = await h.post('/api/admin/support/broadcast',
    { body: 'سلام', target: 'all', app: 'bank' }, { token: t });
  assert.equal(r.status, 400);
});

test('پیشِ‌فرضِ پیامِ همگانی همان دکان است — رفتارِ قدیمی نشکسته', async () => {
  const t = await adminToken();
  const shopUser = await h.newUser('دکان‌دارِ پیش‌فرض');
  await h.post('/api/shop', { name: 'دکانِ پیش‌فرض' }, { token: shopUser.accessToken });

  const r = await h.post('/api/admin/support/broadcast',
    { body: 'بی گفتنِ بخش', target: 'all' }, { token: t });
  assert.equal(r.status, 200);
  assert.equal(r.body.app, 'shop');
  assert.ok((await h.get('/api/support/thread', { token: shopUser.accessToken }))
    .body.messages.some(m => m.body === 'بی گفتنِ بخش'));
});

// ══════════════════════════════════════════════════════════════════
//  ۳) خبرِ پایانِ اشتراک — دو باگی که با هم بسته شدند
// ══════════════════════════════════════════════════════════════════

test('خبرِ پایانِ اشتراکِ پمپ در گفت‌وگوی پمپ می‌نشیند، نه در دکانِ همان آدم', async () => {
  const subs = require('../src/lib/subscriptions');
  //  یک آدم که هم دکان دارد هم پمپ
  const u = await h.newUser('دوکاره');
  await h.post('/api/shop', { name: 'دکانِ دوکاره' }, { token: u.accessToken });
  const pumpSide = await h.signIn(u, 'pump');
  const st = await h.post('/api/pump', { name: 'پمپِ دوکاره' }, { token: pumpSide.accessToken });
  const stationId = st.body.station.id;

  await subs.pump.grant(stationId, { plan: 'custom', days: 2 });
  const out = await subs.pump.notifyExpiring({ thresholds: [7] });
  assert.ok(out.sent >= 1, JSON.stringify(out));

  const onPump = await h.get('/api/pump/support/thread', { token: pumpSide.accessToken });
  assert.ok(onPump.body.messages.some(m => m.body.includes('پمپ شما')),
    'خبر باید در گفت‌وگوی پمپ باشد');
  assert.equal(onPump.body.thread.app, 'pump');

  const onShop = await h.get('/api/support/thread', { token: u.accessToken });
  assert.ok(!onShop.body.messages.some(m => m.body.includes('پمپ شما')),
    'و هرگز در گفت‌وگوی دکان');
});

test('پمپِ بی‌صاحب هم خبرِ پایانِ اشتراک را می‌گیرد', async () => {
  const subs = require('../src/lib/subscriptions');
  const d = await pumpDevice('pc-exp-1', 2);

  const before = await h.get('/api/pump/device/support/thread', { token: d.token });
  assert.deepEqual(before.body.messages, []);

  const out = await subs.pump.notifyExpiring({ thresholds: [7] });
  assert.ok(out.sent >= 1, JSON.stringify(out));

  const after = await h.get('/api/pump/device/support/thread', { token: d.token });
  assert.ok(after.body.messages.some(m => m.sender === 'system' && m.body.includes('پمپ شما')),
    'پمپی که صاحب ندارد هم باید بداند اشتراکش دارد تمام می‌شود');
});

test('خبرِ پایانِ اشتراک دو بار نمی‌رود', async () => {
  const subs = require('../src/lib/subscriptions');
  const d = await pumpDevice('pc-exp-2', 2);

  await subs.pump.notifyExpiring({ thresholds: [7] });
  const once = (await h.get('/api/pump/device/support/thread', { token: d.token })).body.messages.length;
  await subs.pump.notifyExpiring({ thresholds: [7] });
  const twice = (await h.get('/api/pump/device/support/thread', { token: d.token })).body.messages.length;
  assert.equal(twice, once, 'همان آستانه فقط یک بار');
});
