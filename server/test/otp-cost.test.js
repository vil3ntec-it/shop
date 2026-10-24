'use strict';
/**
 * SEC-06 — سقفِ هزینهٔ پیامک و ضدِ SMS pumping در lib/otp.js.
 *
 *   - شمارهٔ ناآشنا: سقفِ IP، بلوکِ هزارتایی و شمارهٔ خارجی (ساعتی) ⇒ ۴۲۹.
 *   - شمارهٔ آشنا (کدی را درست زده / مشتریِ ویترین) از هر سه معاف است ⇒ NAT ِ
 *     مشترک کاربرِ واقعی را قفل نمی‌کند؛ سقفِ روزانهٔ جدا دارد.
 *   - تلاشِ ناموفق در سنجه‌ها می‌ماند، ولی «فاصلهٔ ارسالِ دوباره»، سقفِ هر شماره و
 *     کدِ زندهٔ پیشین را خراب نمی‌کند.
 *   - سقفِ روزانهٔ سراسری از sms_usage: فقط پیامکِ واقعیِ رفته (نه log، نه ناموفق،
 *     نه کهنه‌تر از ۲۴ ساعت)؛ پر شدنش یک بار در سابقه.
 *   - خطای دفتر: نوشتن ⇒ کد می‌رود؛ خواندن ⇒ باز (فقط لاگ).
 *   - resend سقفِ هر شماره و سقفِ سراسری را می‌سنجد.
 *   - راهِ `log` و ایمیل سقف نمی‌گیرند.
 */
Object.assign(process.env, {
  OTP_SMS_IP_PHONES: '3', OTP_SMS_BLOCK_PHONES: '3', OTP_SMS_FOREIGN_PHONES: '4',
  OTP_SMS_DAILY_MAX: '40', OTP_SMS_KNOWN_DAILY_MAX: '60', OTP_DAILY_MAX: '4', OTP_RESEND_SECONDS: '120',
});
const test = require('node:test');
const assert = require('node:assert/strict');
const h = require('./helpers');
const { query, one, newId, now } = require('../src/db');
const otp = require('../src/lib/otp');
const usage = require('../src/lib/sms-usage');
const settings = require('../src/lib/sms-settings');
const L = require('../src/config').otp.smsGuard;

const realWebhook = otp.senders.webhook;
let sent = [];
let failNext = 0;
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
  //  پیامکِ «واقعی» بی شبکه: راهِ webhook با فرستندهٔ ساختگی
  otp.senders.webhook = async (to) => {
    if (failNext > 0) { failNext--; throw new Error('provider down'); }
    sent.push(to);
  };
  await settings.save({ provider: 'webhook' });
});
test.after(async () => {
  otp.senders.webhook = realWebhook;
  await h.stop();
});
test.beforeEach(() => { sent = []; failNext = 0; });

const code = (p) => p.then(() => 'ok', (e) => e.code || e.message);
const pad = (i, n = 3) => String(i).padStart(n, '0');
const seed = async (n, at, { ok = true, via = 'webhook' } = {}) => query(
  `INSERT INTO sms_usage (id, shop_id, app, purpose, via, ok, parts, cost_minor, at)
   SELECT 'smsu_seed_' || g || '_' || $3::text, '', 'shop', 'login', $5::text, $4::boolean, 1, 0, $2::bigint
     FROM generate_series(1, $1::int) AS g`,
  [n, at, newId('s'), ok, via]
);
const clearSeed = () => query(`DELETE FROM sms_usage WHERE id LIKE 'smsu_seed_%'`);

/** کدی بگیر و همان را درست بزن ⇒ شماره آشنا می‌شود. */
async function makeKnown(phone, ip) {
  await otp.request(phone, { ip, purpose: 'reauth' });
  const row = await one(`SELECT id FROM otp_codes WHERE destination=$1 ORDER BY created_at DESC LIMIT 1`, [phone]);
  await query(`UPDATE otp_codes SET consumed_at=$2 WHERE id=$1`, [row.id, now()]);
}

test('IP: شمارهٔ ناآشنای چهارم از یک IP ⇒ sms_ip_limit؛ شمارهٔ آشنا، همان شماره و IP ِ دیگر آزاد', async () => {
  assert.equal(L.ipPhones, 3);
  const ip = '198.51.100.7';
  //  شمارهٔ آشنا پیش از پر شدن ساخته می‌شود (خودش هم یکی از سه است)
  await makeKnown('+93781000123', '198.51.100.99');
  for (let i = 0; i < L.ipPhones; i++) assert.equal(await code(otp.request(`+93791${pad(i)}123`, { ip })), 'ok');
  assert.equal(await code(otp.request('+93791999123', { ip })), 'sms_ip_limit');
  assert.equal(sent.includes('+93791999123'), false, 'پیامکی نرفت');
  assert.equal((await one(`SELECT COUNT(*)::int AS n FROM otp_codes WHERE destination='+93791999123'`)).n, 0);
  //  ⛔ NAT ِ مشترک: کاربرِ آشنا پشتِ همان IP ِ پر هم کد می‌گیرد
  assert.equal(await code(otp.request('+93781000123', { ip })), 'ok');
  //  مشتریِ ویترین هم آشناست
  await query(`INSERT INTO store_customers (id, phone, created_at, updated_at) VALUES ($1,'+93782000123',$2,$2)`,
    [newId('scu'), now()]);
  assert.equal(await code(otp.request('+93782000123', { ip })), 'ok');
  //  همان شمارهٔ پیشین (هدفِ دیگر) به «جدا» نمی‌افزاید؛ IP ِ دیگر آزاد
  assert.equal(await code(otp.request('+93791000123', { ip, purpose: 'link' })), 'ok');
  assert.equal(await code(otp.request('+93791999123', { ip: '198.51.100.8' })), 'ok');
});

test('بلوک و خارجی: ناآشنا بسته، آشنا آزاد؛ تلاشِ ناموفق هم شمرده می‌شود', async () => {
  //  بلوک: دو ارسالِ موفق + یک ناموفق ⇒ سومی پر است
  //  آشنا از راهِ «مشتریِ ویترین» — بی ردیفِ کد در همین بلوک
  const known = '+93792000777';
  await query(`INSERT INTO store_customers (id, phone, created_at, updated_at) VALUES ($1,$2,$3,$3)`, [newId('scu'), known, now()]);
  for (let i = 0; i < L.blockPhones; i++) {
    if (i === L.blockPhones - 1) failNext = 1;
    const out = await code(otp.request(`+93792000${pad(i)}`, { ip: `203.0.113.${i + 1}` }));
    assert.equal(out, i === L.blockPhones - 1 ? 'delivery_failed' : 'ok');
  }
  assert.equal(await code(otp.request('+93792000999', { ip: '203.0.113.200' })), 'sms_prefix_limit');
  assert.equal(await code(otp.request(known, { ip: '203.0.113.201' })), 'ok', 'آشنا در همان بلوک آزاد');
  assert.equal(await code(otp.request('+93792001999', { ip: '203.0.113.202' })), 'ok', 'بلوکِ دیگر آزاد');

  //  خارجی
  for (let i = 0; i < L.foreignPhones; i++) {
    assert.equal(await code(otp.request(`+4477${pad(i)}00123`, { ip: `192.0.2.${i + 1}` })), 'ok');
  }
  assert.equal(await code(otp.request('+4477999001234', { ip: '192.0.2.200' })), 'sms_foreign_limit');
  assert.equal(await code(otp.request('+93793000123', { ip: '192.0.2.201' })), 'ok');
});

test('ارسالِ ناموفق: ردیف بی کد می‌ماند، ولی تلاشِ دوباره، سقفِ هر شماره و کدِ زندهٔ پیشین سالم‌اند', async () => {
  const phone = '+93794100123';
  failNext = 1;
  assert.equal(await code(otp.request(phone, { ip: '192.0.2.70' })), 'delivery_failed');
  const failed = await one(`SELECT via, code_sealed, code_hash FROM otp_codes WHERE destination=$1`, [phone]);
  assert.deepEqual([failed.via, failed.code_sealed, failed.code_hash], ['failed', null, '']);
  //  همان لحظه دوباره (بی «۱۲۰ ثانیه صبر کنید»)
  assert.equal(await code(otp.request(phone, { ip: '192.0.2.70' })), 'ok');
  //  کدِ زنده درست است، حتی اگر بعدش ارسالی شکست بخورد
  const live = await one(`SELECT id FROM otp_codes WHERE destination=$1 AND via='webhook'`, [phone]);
  const { reveal } = otp;
  const shown = await reveal(live.id);
  await query(`UPDATE otp_codes SET created_at = created_at - 200000 WHERE destination=$1`, [phone]);
  failNext = 1;
  assert.equal(await code(otp.request(phone, { ip: '192.0.2.70' })), 'delivery_failed');
  assert.equal(await otp.verify(phone, shown.code), true);
  //  ردیف‌های ناموفق سقفِ روزانهٔ همین شماره را پر نمی‌کنند (OTP_DAILY_MAX=4)
  const other = '+93794200123';
  failNext = 5;
  for (let i = 0; i < 5; i++) {
    assert.equal(await code(otp.request(other, { ip: `192.0.2.${80 + i}` })), 'delivery_failed');
  }
  assert.equal(await code(otp.request(other, { ip: '192.0.2.90' })), 'ok');
});

test('سقفِ روزانه: فقط پیامکِ واقعیِ رفته در ۲۴ ساعت؛ آشنا سهمِ جدا؛ یک ردیفِ هشدار؛ log و ایمیل بی‌سقف', async () => {
  const t = now();
  await clearSeed();
  const base = (await usage.totals({ since: t - 24 * 3600_000 })).sent;
  const known = '+93795900123';
  await makeKnown(known, '192.0.2.150');
  //  کهنه، ناموفق و log شمرده نمی‌شوند
  await seed(L.knownDailyMax, t - 25 * 3600_000);
  await seed(L.knownDailyMax, t - 3600_000, { ok: false });
  await seed(L.knownDailyMax, t - 3600_000, { via: 'log' });
  assert.equal(await code(otp.request('+93795000123', { ip: '192.0.2.100' })), 'ok');
  //  ناآشنا به dailyMax می‌رسد؛ آشنا هنوز نه
  await seed(L.dailyMax - base - 2, t - 3600_000);
  assert.equal(await code(otp.request('+93795001123', { ip: '192.0.2.101' })), 'sms_daily_ceiling');
  assert.equal(await code(otp.request('+93795002123', { ip: '192.0.2.102' })), 'sms_daily_ceiling');
  assert.equal(await code(otp.request(known, { ip: '192.0.2.103' })), 'ok', 'آشنا سهمِ جدای خودش را دارد');
  const alerts = await query(`SELECT detail FROM audit_logs WHERE action='sms.daily_ceiling'`);
  assert.equal(alerts.rows.length, 1, 'هشدار روزی یک بار، نه به‌ازای هر درخواست');
  //  آشنا هم سقفِ خودش را دارد
  await seed(L.knownDailyMax, t - 3600_000);
  await query(`UPDATE otp_codes SET created_at = created_at - 200000 WHERE destination=$1`, [known]);
  assert.equal(await code(otp.request(known, { ip: '192.0.2.104' })), 'sms_daily_ceiling');
  //  ایمیل و راهِ log هزینه ندارند ⇒ سقف نمی‌گیرند
  assert.equal(await code(otp.request('ceiling@test.local', { ip: '192.0.2.105' })), 'ok');
  await settings.save({ provider: 'log' });
  try {
    assert.equal(await code(otp.request('+93795003123', { ip: '192.0.2.106' })), 'ok');
    const r = await one(`SELECT via, ok FROM sms_usage WHERE id NOT LIKE 'smsu_seed_%' ORDER BY at DESC, id DESC LIMIT 1`);
    assert.deepEqual([r.via, r.ok], ['log', true], 'پیامکِ log هم در دفتر هست (شمرده نمی‌شود)');
  } finally {
    await settings.save({ provider: 'webhook' });
  }

  //  resend: سقفِ سراسری را می‌سنجد
  const row = await one(`SELECT id FROM otp_codes WHERE destination='+93795000123' AND via='webhook'`);
  const rs = await otp.resend(row.id);
  assert.deepEqual([rs.ok, rs.error], [false, 'sms_daily_ceiling']);
  await clearSeed();
  assert.equal((await otp.resend(row.id)).ok, true);
});

test('resend سقفِ روزانهٔ همان شماره را می‌سنجد', async () => {
  const phone = '+93796000123';
  assert.equal(await code(otp.request(phone, { ip: '192.0.2.120' })), 'ok');
  const row = await one(`SELECT id FROM otp_codes WHERE destination=$1 AND via='webhook'`, [phone]);
  for (let i = 0; i < 3; i++) {
    await query(
      `INSERT INTO otp_codes (id, purpose, destination, code_hash, app, attempts, max_attempts, expires_at, created_at, ip, via)
       VALUES ($1,'login',$2,'x','',0,5,$3,$3,'192.0.2.120','webhook')`,
      [newId('otp'), phone, now() - 1000 * (i + 1)]
    );
  }
  const rs = await otp.resend(row.id);
  assert.deepEqual([rs.ok, rs.error], [false, 'otp_daily_limit']);
});

test('خطای دفتر: نوشتن ⇒ کد می‌رود؛ خواندن ⇒ باز (فقط لاگ)', async () => {
  const { record, totals } = usage;
  usage.record = async () => { throw new Error('ledger write down'); };
  try {
    assert.equal(await code(otp.request('+93797000123', { ip: '192.0.2.130' })), 'ok');
  } finally { usage.record = record; }
  usage.totals = async () => { throw new Error('ledger read down'); };
  try {
    assert.equal(await code(otp.request('+93797001123', { ip: '192.0.2.131' })), 'ok');
  } finally { usage.totals = totals; }
});

test('دفتر: هر تلاشِ پیامک با دکان، هدف و راه؛ ناموفق ok=false؛ ایمیل نه؛ ویترین دکانش را می‌دهد', async () => {
  const before = (await one('SELECT COUNT(*)::int AS n FROM sms_usage')).n;
  await otp.request('+93798000123', { ip: '192.0.2.140', purpose: 'login', app: 'shop', shopId: 'shp_x' });
  let r = await one(`SELECT * FROM sms_usage ORDER BY at DESC, id DESC LIMIT 1`);
  assert.deepEqual([r.shop_id, r.app, r.purpose, r.via, r.ok], ['shp_x', 'shop', 'login', 'webhook', true]);
  failNext = 1;
  assert.equal(await code(otp.request('+93798001123', { ip: '192.0.2.141' })), 'delivery_failed');
  r = await one(`SELECT * FROM sms_usage ORDER BY at DESC, id DESC LIMIT 1`);
  assert.equal(r.ok, false);
  await otp.request('ledger@test.local', { ip: '192.0.2.142' });
  assert.equal((await one('SELECT COUNT(*)::int AS n FROM sms_usage')).n, before + 2, 'ایمیل در دفترِ پیامک نیست');
  assert.ok(!JSON.stringify(r).includes('98001123'), 'شماره در دفتر نیست');

  //  ویترین: کدِ تأییدِ مشتری با شناسهٔ همان دکان
  const u = await h.newUser('دکانِ پیامک', 'shop');
  const shop = (await h.post('/api/shop', { name: 'دکانِ پیامک' }, { token: u.accessToken })).body.shop;
  for (const key of ['shop.orders', 'shop.storefront']) {
    const f = await h.put('/api/admin/pump/live-config', { scope: shop.id, key, value: true }, { token: admin });
    assert.equal(f.status, 200, JSON.stringify(f.body));
  }
  const slug = `sms-${Date.now().toString(36)}`;
  const put = await h.put('/api/shop/storefront', { slug, enabled: true, title: 'ویترین', contactPhone: '0700000001' },
    { token: u.accessToken });
  assert.equal(put.status, 200, JSON.stringify(put.body));
  const v = await h.post(`/api/store/${slug}/verify/start`, { phone: '0798500123' });
  assert.equal(v.status, 200, JSON.stringify(v.body));
  r = await one(`SELECT * FROM sms_usage WHERE purpose = 'store_order' ORDER BY at DESC LIMIT 1`);
  assert.equal(r.shop_id, shop.id);
});
