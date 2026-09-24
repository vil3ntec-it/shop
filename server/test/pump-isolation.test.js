'use strict';
/**
 * سه سؤالِ صاحب مخزن، با آزمونِ واقعی — نه با ادعا.
 *
 *   ۱) «ببین هر شخص جدا است یا نه، با شاپ که یکی نمی‌شود؟ چون هر دو
 *      دارند از یک دامنه استفاده می‌کنند.»
 *   ۲) «کد می‌رود و حسابِ طرف ذخیره می‌شود توی فایل‌های فولدرِ سرور، که
 *      بعداً اگر سرور را عوض کردم بردم سرِ کامپیوترِ دیگر کار کند؟»
 *   ۳) «ببین اطلاعاتِ هر شخصی جدا است یا نه، که قاطی نشوند.»
 *
 * هر بخشِ زیر جوابِ یکی از این‌هاست. آزمونِ «سرور را جابه‌جا کن»
 * پشتیبانِ **واقعی** می‌گیرد (`pg_dump`)، کلِ دیتابیس را پاک می‌کند و
 * برمی‌گرداند — نه شبیه‌سازی.
 */
const test = require('node:test');
const assert = require('node:assert/strict');
const { spawn } = require('node:child_process');
const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');
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

async function adminToken() {
  const r = await h.post('/api/admin/login', { username: 'admin', password: 'Admin!12345' });
  return r.body.token;
}

/** یک آدم با پمپِ خودش. */
async function owner(name, code) {
  const u = await h.newUser(name, 'pump');
  const made = await h.post('/api/pump', { name: `پمپ ${name}`, code }, { token: u.accessToken });
  assert.equal(made.status, 201, JSON.stringify(made.body));
  return { ...u, stationId: made.body.station.id, code: made.body.station.code };
}

/** کارمندی که عضوِ پمپِ داده‌شده می‌شود. */
async function staffOf(stationId, name) {
  const u = await h.newUser(name, 'pump');
  await query(
    `INSERT INTO station_members (id, station_id, user_id, role, status, created_at, updated_at)
     VALUES ($1,$2,$3,'staff','active',$4,$4)`,
    [newId('mem'), stationId, u.user.id, now()]
  );
  return u;
}

/** کدِ شش‌رقمیِ پمپ از پنلِ مدیریت. */
async function pumpCode(t, body = {}) {
  const r = await h.post('/api/admin/pump/vip-codes',
    { plan: 'custom', days: 30, ...body }, { token: t });
  assert.equal(r.status, 201, JSON.stringify(r.body));
  return r.body.code;
}

// ════════════════════════════════════════════════════════════════════
//  ۱) اطلاعاتِ هر شخص جدا است — قاطی نمی‌شوند
// ════════════════════════════════════════════════════════════════════

test('دو پمپِ جدا: دفتر، پوشه، نشانی و رمز هیچ‌کدام به هم نمی‌رسند', async () => {
  const a = await owner('احمد', 'iso-ahmad');
  const b = await owner('بشیر', 'iso-bashir');

  //  هر کدام دفترِ خودش را پر می‌کند
  await h.put('/api/pump/files/live.json',
    { data: { safe: 111, note: 'مالِ احمد' } }, { token: a.accessToken });
  await h.put('/api/pump/files/live.json',
    { data: { safe: 222, note: 'مالِ بشیر' } }, { token: b.accessToken });

  await h.post('/api/pump/home',
    { homeUrl: 'https://ahmad.example.ir', readKey: 'rk-ahmad' }, { token: a.accessToken });
  await h.post('/api/pump/home',
    { homeUrl: 'https://bashir.example.ir', readKey: 'rk-bashir' }, { token: b.accessToken });

  //  هر کدام فقط مالِ خودش را می‌بیند
  const seenA = await h.get('/api/pump/files/live.json', { token: a.accessToken });
  const seenB = await h.get('/api/pump/files/live.json', { token: b.accessToken });
  assert.equal(seenA.body.data.safe, 111);
  assert.equal(seenB.body.data.safe, 222);
  assert.equal(seenA.body.data.note, 'مالِ احمد');
  assert.equal(seenB.body.data.note, 'مالِ بشیر');

  const meA = await h.get('/api/pump/me', { token: a.accessToken });
  const meB = await h.get('/api/pump/me', { token: b.accessToken });
  assert.equal(meA.body.home.readKey, 'rk-ahmad');
  assert.equal(meB.body.home.readKey, 'rk-bashir');
  assert.equal(meA.body.home.url, 'https://ahmad.example.ir');
  assert.equal(meB.body.home.url, 'https://bashir.example.ir');

  //  و فهرستِ پوشه‌ی هر کدام فقط فایل‌های خودش را دارد
  const listA = await h.get('/api/pump/files', { token: a.accessToken });
  assert.equal(listA.body.files.length, 1);

  //  ⚠️ مهم‌ترین سنجه: پاسخِ a هیچ ردّی از b ندارد
  assert.ok(!JSON.stringify(meA.body).includes('bashir'), 'پاسخِ احمد نباید نامی از بشیر داشته باشد');
  assert.ok(!JSON.stringify(seenA.body).includes('بشیر'));
});

test('نوشتنِ یکی، دفترِ دیگری را عوض نمی‌کند', async () => {
  const a = await owner('نویسنده', 'iso-writer');
  const b = await owner('همسایه', 'iso-neighbor');

  await h.put('/api/pump/files/live.json', { data: { n: 1 } }, { token: a.accessToken });
  const before = await h.get('/api/pump/files', { token: b.accessToken });

  //  a ده بار می‌نویسد
  for (let i = 2; i <= 11; i++) {
    await h.put('/api/pump/files/live.json', { data: { n: i } }, { token: a.accessToken });
  }

  const after = await h.get('/api/pump/files', { token: b.accessToken });
  assert.equal(after.body.rev, before.body.rev, 'شمارندهٔ همسایه نباید تکان بخورد');
  assert.equal(after.body.files.length, 0, 'پوشهٔ همسایه باید خالی بماند');
});

test('کارمندِ یک پمپ به پمپِ دیگر دسترسی ندارد', async () => {
  const a = await owner('صاحبِ یک', 'iso-one');
  const b = await owner('صاحبِ دو', 'iso-two');
  await h.put('/api/pump/files/live.json', { data: { secret: 'رازِ یک' } }, { token: a.accessToken });
  await h.post('/api/pump/home',
    { homeUrl: 'https://one.example.ir', readKey: 'rk-one' }, { token: a.accessToken });

  const staffB = await staffOf(b.stationId, 'کارمندِ دو');

  //  کارمندِ b پوشهٔ a را نمی‌بیند — چون شناسه از توکن می‌آید، نه از مسیر
  const peek = await h.get('/api/pump/files/live.json', { token: staffB.accessToken });
  assert.equal(peek.status, 404);

  const me = await h.get('/api/pump/me', { token: staffB.accessToken });
  assert.equal(me.body.station.code, 'iso-two');
  assert.notEqual(me.body.home.readKey, 'rk-one');
  assert.ok(!JSON.stringify(me.body).includes('rk-one'));
});

test('عضوی که برداشته شود، همان لحظه بیرون می‌ماند', async () => {
  const o = await owner('صاحب', 'iso-revoke');
  const staff = await staffOf(o.stationId, 'کارمندِ اخراجی');
  await h.put('/api/pump/files/live.json', { data: { x: 1 } }, { token: o.accessToken });

  //  تا وقتی عضو است، می‌بیند
  const before = await h.get('/api/pump/files/live.json', { token: staff.accessToken });
  assert.equal(before.status, 200);

  const members = await h.get('/api/pump/members', { token: o.accessToken });
  const row = members.body.members.find(m => m.user_id === staff.user.id);
  const off = await h.patch(`/api/pump/members/${row.id}`,
    { status: 'removed' }, { token: o.accessToken });
  assert.equal(off.status, 200);

  //  نشستش همان لحظه باطل می‌شود
  const after = await h.get('/api/pump/files/live.json', { token: staff.accessToken });
  assert.equal(after.status, 401, 'توکنِ عضوِ برداشته‌شده باید باطل شود');
});

test('اشتراکِ یک پمپ، پمپِ دیگر را باز نمی‌کند', async () => {
  const plans = require('../src/lib/plans');
  await plans.setConfig('pump_trial_days', '0');

  const a = await owner('اشتراک‌دار', 'iso-paid');
  const b = await owner('بی‌اشتراک', 'iso-free');

  const t = await adminToken();
  const code = await pumpCode(t, { days: 60 });
  const used = await h.post('/api/pump/vip/redeem', { code }, { token: a.accessToken });
  assert.equal(used.status, 201);

  const stA = await h.get('/api/pump/subscription', { token: a.accessToken });
  const stB = await h.get('/api/pump/subscription', { token: b.accessToken });
  assert.equal(stA.body.entitlement.source, 'subscription');
  assert.equal(stB.body.entitlement.source, 'free', 'پمپِ دوم نباید از اشتراکِ اولی بهره ببرد');

  await plans.setConfig('pump_trial_days', '14');
});

test('کدی که به نامِ یک پمپ صادر شده، پمپِ دیگر خرجش نمی‌کند', async () => {
  const t = await adminToken();
  const a = await owner('نام‌دار', 'iso-named');
  const b = await owner('بی‌نام', 'iso-unnamed');

  //  کد فقط برای پمپِ a
  const code = await pumpCode(t, { days: 30, stationId: a.stationId });

  const wrong = await h.post('/api/pump/vip/redeem', { code }, { token: b.accessToken });
  assert.equal(wrong.status, 403);
  assert.equal(wrong.body.error.code, 'code_other_station');

  //  و خودِ a همان کد را می‌گیرد
  const right = await h.post('/api/pump/vip/redeem', { code }, { token: a.accessToken });
  assert.equal(right.status, 201);
});

// ════════════════════════════════════════════════════════════════════
//  ۲) پمپ و شاپ روی یک دامنه، ولی قاطی نمی‌شوند
// ════════════════════════════════════════════════════════════════════

test('یک آدم، هم دکان هم پمپ: دو دفترِ کاملاً جدا', async () => {
  const plans = require('../src/lib/plans');
  await plans.setConfig('pump_trial_days', '0');
  await plans.setConfig('trial_days', '0');

  /*
   *  ⚠️ یک آدم، **دو نشست**.
   *
   *  از وقتی توکن به بخشش مهر می‌خورد، یک توکن هر دو بخش را باز
   *  نمی‌کند. این خودش بخشی از همان «هیچ ربطی به هم نداشته باشند»
   *  است: در هر برنامه جدا وارد می‌شوی، مثلِ دو برنامهٔ بی‌ربط.
   */
  const u = await h.newUser('دوکاره', 'shop');
  const asPump = await h.signIn(u, 'pump');

  const shop = await h.post('/api/shop', { name: 'دکانِ من' }, { token: u.accessToken });
  const pump = await h.post('/api/pump', { code: 'both-one' }, { token: asPump.accessToken });
  assert.equal(shop.status, 201);
  assert.equal(pump.status, 201);

  //  و این مرزِ تازه را همین‌جا می‌سنجیم: توکنِ دکان روی پمپ «نشستی
  //  نیست»، نه «دسترسی نداری»
  const wrongWay = await h.get('/api/pump/me', { token: u.accessToken });
  assert.equal(wrongWay.status, 401, 'توکنِ دکان نباید روی پمپ شناخته شود');
  const otherWay = await h.get('/api/me', { token: asPump.accessToken });
  assert.equal(otherWay.status, 401, 'توکنِ پمپ نباید روی دکان شناخته شود');

  //  یک حساب، دو مستأجرِ جدا
  assert.ok(shop.body.shop.id.startsWith('shp'));
  assert.ok(pump.body.station.id.startsWith('stn'));

  const t = await adminToken();

  //  فقط دکان اشتراک می‌گیرد
  const shopCode = await h.post('/api/admin/vip-codes',
    { plan: 'custom', days: 30 }, { token: t });
  await h.post('/api/vip/redeem', { code: shopCode.body.code }, { token: u.accessToken });

  const shopState = await h.get('/api/me/subscription', { token: u.accessToken });
  const pumpState = await h.get('/api/pump/subscription', { token: asPump.accessToken });
  assert.equal(shopState.body.source, 'subscription');
  assert.equal(pumpState.body.entitlement.source, 'free',
    'اشتراکِ دکان نباید پمپِ همان آدم را باز کند');

  //  حالا فقط پمپ اشتراک می‌گیرد
  const pCode = await pumpCode(t, { days: 30 });
  await h.post('/api/pump/vip/redeem', { code: pCode }, { token: asPump.accessToken });

  const pumpAfter = await h.get('/api/pump/subscription', { token: asPump.accessToken });
  assert.equal(pumpAfter.body.entitlement.source, 'subscription');

  //  و دفترِ اشتراک‌ها واقعاً دو ردیفِ جدا در دو جدولِ جداست
  const inShop = await one('SELECT COUNT(*)::int n FROM subscriptions WHERE shop_id=$1',
    [shop.body.shop.id]);
  const inPump = await one('SELECT COUNT(*)::int n FROM station_subscriptions WHERE station_id=$1',
    [pump.body.station.id]);
  assert.equal(inShop.n, 1);
  assert.equal(inPump.n, 1);

  await plans.setConfig('pump_trial_days', '14');
  await plans.setConfig('trial_days', '14');
});

test('کدِ پمپ روی دکان کار نمی‌کند، و کدِ دکان روی پمپ', async () => {
  const t = await adminToken();
  const u = await h.newUser('مرزی', 'shop');
  const asPump = await h.signIn(u, 'pump');
  await h.post('/api/shop', { name: 'دکانِ مرزی' }, { token: u.accessToken });
  await h.post('/api/pump', { code: 'cross-line' }, { token: asPump.accessToken });

  const pCode = await pumpCode(t, { days: 30 });
  const onShop = await h.post('/api/vip/redeem', { code: pCode }, { token: u.accessToken });
  assert.equal(onShop.status, 404, 'کدِ پمپ در دفترِ کدهای دکان اصلاً وجود ندارد');

  const sCode = await h.post('/api/admin/vip-codes', { plan: 'custom', days: 30 }, { token: t });
  const onPump = await h.post('/api/pump/vip/redeem',
    { code: sCode.body.code }, { token: asPump.accessToken });
  assert.equal(onPump.status, 404, 'کدِ دکان در دفترِ کدهای پمپ اصلاً وجود ندارد');
});

test('پوشهٔ پمپ از راهِ مسیرهای دکان در دسترس نیست', async () => {
  const u = await h.newUser('کنجکاو', 'shop');
  const asPump = await h.signIn(u, 'pump');
  await h.post('/api/shop', { name: 'دکانِ کنجکاو' }, { token: u.accessToken });
  await h.post('/api/pump', { code: 'nocross' }, { token: asPump.accessToken });
  await h.put('/api/pump/files/live.json',
    { data: { رازِ‌پمپ: 'نباید در دکان دیده شود' } }, { token: asPump.accessToken });

  //  همگام‌سازیِ دکان هیچ‌چیزِ پمپ را برنمی‌گرداند
  const pull = await h.get('/api/sync/pull?since=0', { token: u.accessToken });
  if (pull.status === 200) {
    assert.ok(!JSON.stringify(pull.body).includes('نباید در دکان دیده شود'),
      'دادهٔ پمپ نباید در همگام‌سازیِ دکان بیاید');
  }
});

test('پمپِ خاموش‌شده، دکانِ همان آدم را از کار نمی‌اندازد', async () => {
  const t = await adminToken();
  const u = await h.newUser('نیمه‌خاموش', 'shop');
  const asPump = await h.signIn(u, 'pump');
  const shop = await h.post('/api/shop', { name: 'دکانِ زنده' }, { token: u.accessToken });
  const pump = await h.post('/api/pump', { code: 'half-off' }, { token: asPump.accessToken });

  const off = await h.post(`/api/admin/pump/stations/${pump.body.station.id}/status`,
    { status: 'disabled' }, { token: t });
  assert.equal(off.status, 200);

  //  پمپ رفت
  const pumpMe = await h.get('/api/pump/me', { token: asPump.accessToken });
  assert.equal(pumpMe.body.station, null);

  //  ولی دکان سرِ جایش است
  const shopMe = await h.get('/api/me', { token: u.accessToken });
  assert.equal(shopMe.status, 200);
  assert.ok(JSON.stringify(shopMe.body).includes(shop.body.shop.id));
});

test('مجوزِ دکان و مجوزِ پمپ دو شنوندهٔ جدا دارند', async () => {
  const u = await h.newUser('دومجوزه', 'shop');
  const asPump = await h.signIn(u, 'pump');
  const shop = await h.post('/api/shop', { name: 'دکانِ مجوز' }, { token: u.accessToken });
  const pump = await h.post('/api/pump', { code: 'two-aud' }, { token: asPump.accessToken });

  await require('../src/lib/subscriptions').grant(shop.body.shop.id, { plan: 'custom', days: 30 });
  await require('../src/lib/subscriptions').pump.grant(pump.body.station.id,
    { plan: 'custom', days: 30 });

  const sLic = await h.post('/api/license/sync',
    { device: { uid: 'dev-x' } }, { token: u.accessToken });
  //  ⛔ مجوزِ پمپ فقط برای کامپیوترِ ثبت‌شده — اول بند می‌شود
  await h.post('/api/pump/device/bind', { device: { uid: 'dev-x' } }, { token: asPump.accessToken });
  const pLic = await h.post('/api/pump/license',
    { device: { uid: 'dev-x' } }, { token: asPump.accessToken });

  const aud = (tok) => JSON.parse(Buffer.from(tok.split('.')[1], 'base64').toString('utf8')).aud;
  assert.equal(aud(sLic.body.license), 'tohid-shop-app');
  assert.equal(aud(pLic.body.license), 'tohid-pump-app');
});

// ════════════════════════════════════════════════════════════════════
//  ۳) سرور را جابه‌جا کن — پشتیبانِ واقعی، پاک کردنِ واقعی، برگرداندنِ واقعی
// ════════════════════════════════════════════════════════════════════

/** `pg_dump`/`psql`ی واقعی روی همین دیتابیسِ آزمون. */
function run(cmd, args, { input = null } = {}) {
  return new Promise((resolve, reject) => {
    const p = spawn(cmd, args, { stdio: ['pipe', 'pipe', 'pipe'] });
    let out = ''; let err = '';
    p.stdout.on('data', d => { out += d; });
    p.stderr.on('data', d => { err += d; });
    p.on('error', reject);
    p.on('close', code => (code === 0 ? resolve(out) : reject(new Error(`${cmd}: ${err}`))));
    if (input !== null) { p.stdin.write(input); }
    p.stdin.end();
  });
}

test('سرور را به کامپیوترِ دیگر ببر: پشتیبان بگیر، پاک کن، برگردان — همه‌چیز سرِ جایش', async () => {
  const t = await adminToken();

  // ── حالتی که باید زنده بماند ──────────────────────────────────
  const a = await owner('کوچنده‌الف', 'move-a');
  const b = await owner('کوچنده‌ب', 'move-b');
  const staffA = await staffOf(a.stationId, 'کارمندِ الف');

  //  کد می‌رود و اشتراک فعال می‌شود
  const code = await pumpCode(t, { days: 90, note: 'پیش از کوچ' });
  const used = await h.post('/api/pump/vip/redeem', { code }, { token: a.accessToken });
  assert.equal(used.status, 201);

  //  دفتر و نشانی و رمز
  await h.post('/api/pump/home',
    { homeUrl: 'https://move-a.example.ir', readKey: 'rk-move-a' }, { token: a.accessToken });
  await h.post('/api/pump/home',
    { homeUrl: 'https://move-b.example.ir', readKey: 'rk-move-b' }, { token: b.accessToken });
  await h.put('/api/pump/files/live.json',
    { data: { safe: 9876, نام: 'دفترِ الف' } }, { token: a.accessToken });
  await h.put('/api/pump/files/station.json',
    { data: { شهر: 'کابل' } }, { token: a.accessToken });
  await h.put('/api/pump/files/live.json',
    { data: { safe: 1234, نام: 'دفترِ ب' } }, { token: b.accessToken });

  //  عکسی از «قبل»
  const beforeA = await h.get('/api/pump/me', { token: a.accessToken });
  const beforeSub = await h.get('/api/pump/subscription', { token: a.accessToken });
  assert.equal(beforeSub.body.entitlement.source, 'subscription');
  assert.equal(beforeA.body.home.readKey, 'rk-move-a');

  // ── پشتیبانِ واقعی ────────────────────────────────────────────
  const dir = fs.mkdtempSync(path.join(os.tmpdir(), 'pump-move-'));
  const dumpFile = path.join(dir, 'backup.sql');
  const sql = await run('pg_dump', [
    process.env.DATABASE_URL, '--no-owner', '--no-privileges',
    '--format=plain', '--encoding=UTF8',
  ]);
  fs.writeFileSync(dumpFile, sql);
  assert.ok(sql.includes('stations'), 'پشتیبان باید جدولِ پمپ‌ها را داشته باشد');
  assert.ok(sql.includes('station_files'), 'پشتیبان باید پوشهٔ پمپ‌ها را داشته باشد');
  assert.ok(sql.includes('station_subscriptions'), 'پشتیبان باید اشتراکِ پمپ‌ها را داشته باشد');

  // ── کامپیوترِ تازه: همه‌چیز پاک ───────────────────────────────
  //  ⚠️ این واقعاً کلِ دیتابیس را پاک می‌کند. دقیقاً همان کاری که
  //  بردنِ سرور به ماشینِ دیگر می‌کند — از صفر.
  await query('DROP SCHEMA public CASCADE');
  await query('CREATE SCHEMA public');

  const gone = await one(
    `SELECT COUNT(*)::int n FROM information_schema.tables
      WHERE table_schema='public' AND table_name='stations'`
  );
  assert.equal(gone.n, 0, 'باید واقعاً پاک شده باشد');

  // ── برگرداندن ────────────────────────────────────────────────
  await run('psql', [process.env.DATABASE_URL, '-v', 'ON_ERROR_STOP=1', '-q', '-f', dumpFile]);

  /*
   *  ⚠️ حافظهٔ کلید باید واقعاً پاک شود.
   *
   *  بی این، آزمون با کلیدی کار می‌کرد که در حافظهٔ همین پروسه مانده
   *  — یعنی دقیقاً همان چیزی را که می‌خواهد ثابت کند فرض می‌گرفت.
   *  کامپیوترِ تازه حافظه‌ای ندارد؛ باید کلید را از دیتابیس پیدا کند.
   */
  require('../src/lib/stations')._forgetKey();

  // ── همه‌چیز باید سرِ جایش باشد ───────────────────────────────
  const afterA = await h.get('/api/pump/me', { token: a.accessToken });
  assert.equal(afterA.status, 200, 'حسابِ الف باید بعد از کوچ کار کند');
  assert.equal(afterA.body.station.code, 'move-a');
  assert.equal(afterA.body.home.url, 'https://move-a.example.ir');
  //  ⚠️ قلبِ ماجرا: رمز باید هنوز خوانا باشد. اگر کلید با دیتابیس سفر
  //  نمی‌کرد، این‌جا رشتهٔ خالی برمی‌گشت و هیچ اپِ کارمندی وصل نمی‌شد.
  assert.equal(afterA.body.home.readKey, 'rk-move-a', 'رمز باید بعد از کوچ خوانا بماند');

  const afterSub = await h.get('/api/pump/subscription', { token: a.accessToken });
  assert.equal(afterSub.body.entitlement.source, 'subscription', 'اشتراک باید بماند');
  assert.equal(afterSub.body.subscription.endsAt, beforeSub.body.subscription.endsAt,
    'تاریخِ پایانِ اشتراک نباید تکان بخورد');

  //  پوشه، کامل
  const files = await h.get('/api/pump/files', { token: a.accessToken });
  assert.equal(files.body.files.length, 2);
  const live = await h.get('/api/pump/files/live.json', { token: a.accessToken });
  assert.equal(live.body.data.safe, 9876);
  assert.equal(live.body.data['نام'], 'دفترِ الف');

  //  کارمند هم هنوز عضو است
  const staffMe = await h.get('/api/pump/me', { token: staffA.accessToken });
  assert.equal(staffMe.body.station.code, 'move-a');
  assert.equal(staffMe.body.home.readKey, 'rk-move-a');

  //  و جداییِ دو پمپ هم دست‌نخورده مانده
  const afterB = await h.get('/api/pump/me', { token: b.accessToken });
  assert.equal(afterB.body.station.code, 'move-b');
  assert.equal(afterB.body.home.readKey, 'rk-move-b');
  const liveB = await h.get('/api/pump/files/live.json', { token: b.accessToken });
  assert.equal(liveB.body.data.safe, 1234);

  //  همان کد دوباره خرج نمی‌شود — «یک بار مصرف» هم کوچ کرده
  const twice = await h.post('/api/pump/vip/redeem', { code }, { token: b.accessToken });
  assert.equal(twice.status, 403);

  fs.rmSync(dir, { recursive: true, force: true });
});

test('رمزِ پمپ فقط با خودِ دیتابیس باز می‌شود — بی هیچ چیزِ بیرونی', async () => {
  /*
   *  سنجهٔ مستقل، بی اعتماد به هیچ کدِ برنامه.
   *
   *  کلید را از همان ردیفِ `app_config` برمی‌داریم، با رمزنگاریِ خام
   *  خودمان بازش می‌کنیم، و می‌بینیم همان رمزی درمی‌آید که نوشته بودیم.
   *  یعنی: هر کسی که فقط `pg_dump` را داشته باشد، می‌تواند سرور را
   *  جای دیگری بالا بیاورد — و هیچ فایل یا متغیرِ محیطیِ دیگری لازم
   *  نیست.
   */
  const crypto = require('node:crypto');
  const o = await owner('مستقل', 'proof-key');
  await h.post('/api/pump/home',
    { homeUrl: 'https://proof.example.ir', readKey: 'rk-proof-999' }, { token: o.accessToken });

  const cfg = await one(`SELECT value FROM app_config WHERE key='station_read_key'`);
  assert.ok(cfg && cfg.value, 'کلید باید در app_config باشد');

  const row = await one('SELECT read_key_enc FROM stations WHERE id=$1', [o.stationId]);
  const [, ivB, bodyB, tagB] = row.read_key_enc.split('.');

  const key = crypto.createHash('sha256').update(cfg.value).digest();
  const d = crypto.createDecipheriv('aes-256-gcm', key, Buffer.from(ivB, 'base64url'));
  d.setAuthTag(Buffer.from(tagB, 'base64url'));
  const plain = Buffer.concat([d.update(Buffer.from(bodyB, 'base64url')), d.final()]).toString('utf8');

  assert.equal(plain, 'rk-proof-999',
    'با کلیدِ داخلِ دیتابیس باید باز شود — پس پشتیبان به تنهایی کافی است');
});

test('کلیدها در خودِ دیتابیس می‌نشینند', async () => {
  //  اگر این ردیف نباشد، «سرور را جابه‌جا کن» کار نمی‌کند — چون کلید
  //  جای دیگری می‌ماند و با پشتیبان سفر نمی‌کند.
  const row = await one(`SELECT value FROM app_config WHERE key='station_read_key'`);
  assert.ok(row && row.value, 'کلیدِ رمزگذاری باید در app_config باشد');

  //  و کلیدِ مجوز هم همان‌جاست — همان الگو، همان دلیل
  const lic = await one(`SELECT value FROM app_config WHERE key='license_private_key'`);
  assert.ok(lic && lic.value, 'کلیدِ مجوز هم باید با دیتابیس سفر کند');
});

test('کلید حتی با ساختِ هم‌زمان هم یکی می‌ماند', async () => {
  /*
   *  سرورِ تازه، چند درخواست در یک لحظه. اگر ساختِ کلید اتمی نباشد،
   *  دومی اولی را روی‌نویسی می‌کند و رمزِ پمپ‌هایی که با کلیدِ اول
   *  نوشته شده‌اند بی‌صدا ناخوانا می‌شود.
   */
  const stations = require('../src/lib/stations');
  await query(`DELETE FROM app_config WHERE key='station_read_key'`);
  stations._forgetKey();

  //  ده نفر با هم، از صفر
  const keys = await Promise.all(
    Array.from({ length: 10 }, () => {
      stations._forgetKey();
      return stations.encryptKey('rk-race');
    })
  );

  //  همه باید با همان یک کلید باز شوند
  for (const sealed of keys) {
    assert.equal(await stations.decryptKey(sealed), 'rk-race');
  }

  const rows = await one(`SELECT COUNT(*)::int n FROM app_config WHERE key='station_read_key'`);
  assert.equal(rows.n, 1, 'فقط یک کلید باید ساخته شده باشد');
});

// ════════════════════════════════════════════════════════════════════
//  ۴) «حتی یک ذره ربط نداشته باشند» — و «نتوانند دور بزنند»
// ════════════════════════════════════════════════════════════════════

test('توکنِ هر بخش در بخشِ دیگر انگار وجود ندارد', async () => {
  const u = await h.newUser('دوتوکنه', 'shop');
  const asPump = await h.signIn(u, 'pump');

  //  توکنِ دکان روی مسیرهای پمپ
  for (const path of ['/api/pump/me', '/api/pump/subscription', '/api/pump/files']) {
    const r = await h.get(path, { token: u.accessToken });
    assert.equal(r.status, 401, `${path} نباید توکنِ دکان را بشناسد`);
  }

  //  و توکنِ پمپ روی مسیرهای دکان
  for (const path of ['/api/me', '/api/me/subscription', '/api/sync/pull?since=0']) {
    const r = await h.get(path, { token: asPump.accessToken });
    assert.equal(r.status, 401, `${path} نباید توکنِ پمپ را بشناسد`);
  }
});

test('توکنِ تازه‌سازی هم بخشش را عوض نمی‌کند', async () => {
  /*
   *  اگر `refresh` بخش را از بدنهٔ درخواست می‌گرفت، کسی می‌توانست با
   *  توکنِ تازه‌سازیِ دکان، توکنِ دسترسیِ پمپ بسازد — یعنی همان مرزی که
   *  تازه گذاشتیم را از پشت دور بزند.
   */
  const u = await h.newUser('تازه‌شونده', 'shop');
  const r = await h.post('/api/auth/refresh', {
    refreshToken: u.refreshToken,
    app: 'pump',                    // ادعای دروغ
    device: { deviceId: 'dev-refresh-1' },
  });
  assert.equal(r.status, 200);

  //  توکنِ تازه باید هنوز مالِ دکان باشد، نه پمپ
  const onPump = await h.get('/api/pump/me', { token: r.body.accessToken });
  assert.equal(onPump.status, 401, 'ادعای بخش در بدنه نباید کارگر باشد');
  const onShop = await h.get('/api/me', { token: r.body.accessToken });
  assert.equal(onShop.status, 200);
});

test('قیمت و پلنِ دو بخش یکی نیست', async () => {
  const t = await adminToken();

  /*
   *  ⚠️ این سنجه تا دیروز فرض می‌کرد کدِ پلن‌های دو بخش یکی است
   *  (`m1`/`m6`/`y1` در هر دو) و همان کد را روی دکان `PATCH` می‌کرد.
   *  از ۱۴۰۵/۰۶/۳۱ پلن‌های پمپ کدِ خودشان را دارند (`std`/`vip`/`perm`
   *  با قیمتِ دالری)، پس فرضِ قدیمی ۴۰۴ می‌گرفت.
   *
   *  حالا جداییِ واقعی سنجیده می‌شود، و در **هر دو جهت**: عوض کردنِ
   *  قیمتِ یک بخش نباید هیچ پلنِ بخشِ دیگر را تکان بدهد.
   */
  const pumpBefore = await h.get('/api/pump/plans');
  const shopBefore = await h.get('/api/plans');
  assert.equal(pumpBefore.status, 200);
  assert.equal(shopBefore.status, 200);
  assert.ok(pumpBefore.body.plans.length, 'بخشِ پمپ باید پلن داشته باشد');
  assert.ok(shopBefore.body.plans.length, 'بخشِ دکان باید پلن داشته باشد');

  const shopPlan = shopBefore.body.plans[0];
  const pumpPlan = pumpBefore.body.plans[0];

  //  ۱) قیمتِ دکان عوض می‌شود ⇒ پمپ نباید تکان بخورد
  const onShop = await h.patch(`/api/admin/plans/${shopPlan.code}`,
    { price: 99999 }, { token: t });
  assert.equal(onShop.status, 200, JSON.stringify(onShop.body));

  const pumpAfter = await h.get('/api/pump/plans');
  for (const p of pumpBefore.body.plans) {
    const same = pumpAfter.body.plans.find(x => x.code === p.code);
    assert.ok(same, `پلنِ ${p.code} باید سرِ جایش باشد`);
    assert.equal(same.price, p.price, 'قیمتِ دکان نباید قیمتِ پمپ را عوض کند');
  }
  const shopAfter = await h.get('/api/plans');
  assert.equal(shopAfter.body.plans.find(p => p.code === shopPlan.code).price, 99999);

  //  ۲) و برعکس: قیمتِ پمپ عوض می‌شود ⇒ دکان نباید تکان بخورد
  const onPump = await h.patch(`/api/admin/plans/${pumpPlan.code}?app=pump`,
    { price: 77777 }, { token: t });
  assert.equal(onPump.status, 200, JSON.stringify(onPump.body));

  const shopAgain = await h.get('/api/plans');
  for (const p of shopAfter.body.plans) {
    const same = shopAgain.body.plans.find(x => x.code === p.code);
    assert.equal(same.price, p.price, 'قیمتِ پمپ نباید قیمتِ دکان را عوض کند');
  }
  const pumpAgain = await h.get('/api/pump/plans');
  assert.equal(pumpAgain.body.plans.find(p => p.code === pumpPlan.code).price, 77777);
});

test('اشتراکِ تمام‌شده روی پوشهٔ ابری نمی‌نویسد', async () => {
  /*
   *  همان «دور زدن». تا دیروز این مسیر فقط نقش را می‌سنجید، پس پمپی
   *  بی اشتراک و بی دورهٔ آزمایشی هم بی‌محدودیت می‌نوشت — و «پوشهٔ
   *  ابری» خودش در کاتالوگ پولی علامت خورده.
   */
  const plans = require('../src/lib/plans');
  await plans.setConfig('pump_trial_days', '0');
  const o = await owner('بی‌اشتراک', 'bypass-1');

  const blocked = await h.put('/api/pump/files/live.json',
    { data: { safe: 1 } }, { token: o.accessToken });
  assert.equal(blocked.status, 403);
  assert.equal(blocked.body.error.code, 'subscription_required');

  //  ولی خواندن باز است — دادهٔ پمپ مالِ خودش است
  const read = await h.get('/api/pump/files', { token: o.accessToken });
  assert.equal(read.status, 200);

  //  و با اشتراک، همان لحظه باز می‌شود
  await require('../src/lib/subscriptions').pump.grant(o.stationId,
    { plan: 'custom', days: 30 });
  const ok = await h.put('/api/pump/files/live.json',
    { data: { safe: 1 } }, { token: o.accessToken });
  assert.equal(ok.status, 200);

  await plans.setConfig('pump_trial_days', '14');
});

test('کارمند هم بی اشتراک در صندوقِ ورودی نمی‌نویسد', async () => {
  const plans = require('../src/lib/plans');
  await plans.setConfig('pump_trial_days', '0');
  const o = await owner('صاحبِ بی‌اشتراک', 'bypass-2');
  const staff = await staffOf(o.stationId, 'کارمندِ بی‌اشتراک');

  const blocked = await h.put('/api/pump/files/inbox.json',
    { data: { m: [] } }, { token: staff.accessToken });
  assert.equal(blocked.status, 403);
  assert.equal(blocked.body.error.code, 'subscription_required');

  await plans.setConfig('pump_trial_days', '14');
});

test('مجوز به همان پمپ بسته است، نه فقط به دستگاه', async () => {
  /*
   *  بی این، کسی که دو پمپ را روی یک کامپیوتر اداره می‌کند می‌توانست
   *  مجوزِ پمپِ اشتراک‌دار را روی پمپِ بی‌اشتراک بگذارد: امضا درست،
   *  دستگاه درست، و برنامه هیچ دلیلی برای ردش نداشت.
   */
  const a = await owner('مجوزِ الف', 'lic-a');
  await require('../src/lib/subscriptions').pump.grant(a.stationId,
    { plan: 'custom', days: 30 });

  //  ⛔ مجوزِ پمپ فقط برای کامپیوترِ ثبت‌شده — اول بند می‌شود
  await h.post('/api/pump/device/bind', { device: { uid: 'same-pc' } }, { token: a.accessToken });
  const r = await h.post('/api/pump/license',
    { device: { uid: 'same-pc' } }, { token: a.accessToken });
  assert.equal(r.status, 200);

  const payload = JSON.parse(
    Buffer.from(r.body.license.split('.')[1], 'base64').toString('utf8')
  );
  assert.equal(payload.stn, a.stationId, 'مجوز باید شناسهٔ پمپ را در خود داشته باشد');
  assert.equal(payload.aud, 'tohid-pump-app');
});
