'use strict';
/**
 * سطلِ جدای ترافیکِ اثبات‌شدهٔ مدیر و پلِ پنل (`middleware/admin-bucket.js`، ۲.۱۴.۵).
 *
 * خطای واقعیِ صاحبِ سامانه (۱۴۰۵/۰۷/۱۰): «سقفِ نرخِ سرورِ حساب پر شده». پلِ پنلِ خانگی از 127.0.0.1 و بی
 * `X-Forwarded-For` به این سرور می‌آید، پس کارت‌های داشبورد، صفحه‌های مدیر و درخواست‌های کلیدِ سرویس همه یک
 * سطلِ عمومیِ «هر IP» (`generalMax`، ۶۰۰ در ربع ساعت) را می‌خوردند و چند دقیقه کارِ تند در کنسول صاحب را از
 * پنلِ خودش بیرون می‌انداخت. این‌جا:
 *   ۱) قفل شدن بازسازی می‌شود (N+1 درخواستِ پل ⇒ پیش از اصلاح ۴۲۹)
 *   ۲) کلیدِ سرویسِ معتبر ⇒ سطلِ خودش (به‌ازای کلید، بزرگ‌تر و بی‌سقف نه)، از دو سو جدا از سطلِ عمومی
 *   ۳) کلیدِ ساختگی/باطل/توکنِ کاربر/مسیرِ غیرِمدیر/مدیرِ پمپ ⇒ همان سطلِ پیشینِ IP؛ پس از پر شدن، پرسشی از پایگاه نه
 *   ۴) `X-Forwarded-For`ِ ساختگی کمکی نمی‌کند
 *   ۵) ترافیکِ عمومی همان سقف را دارد؛ سقفِ ورود و کد دقیقاً همان
 *   ۶) نشستِ مدیر (راهِ پیشینِ پل و کنسولِ صاحب) هم، و ابطال همان دم برمی‌گرداند
 *   ۷) `RATE_ADMIN_BUCKET=0` ⇒ همان رفتارِ پیشین
 * دو انبار: پیش‌فرضِ حافظه، و با `RATE_LIMIT_STORE=pg` همین پرونده روی انبارِ مشترک.
 */
process.env.RATE_OTP_MAX = '3';
const test = require('node:test');
const assert = require('node:assert/strict');
const h = require('./helpers');
const config = require('../src/config');
const { query, newId, now } = require('../src/db');
const { resetAll } = require('../src/middleware/ratelimit');

const GENERAL = 12;
const PROVEN = 30;
const PROBE = 5;
const AUTH = 4;
const PASS = 'Admin!12345';

let admin = '';
let key = '';
let user = null;

const actor = { 'X-Actor-Id': 'panel-owner', 'X-Actor-Role': 'superadmin', 'X-Actor-Name': encodeURIComponent('صاحب') };
const bridge = (k = key, headers = {}) => h.api('GET', '/api/admin/me', { token: k, headers: { ...actor, ...headers } });
const pub = (headers = {}) => h.api('GET', '/api/plans', { headers });
const forged = () => `vsk_${require('crypto').randomBytes(32).toString('base64url')}`;

async function reset() {
  resetAll();
  if (config.rateLimit.store === 'pg') await query('DELETE FROM rate_limit_hits');
}
/** شمارهٔ نخستین ۴۲۹ (از صفر) در `n` درخواست؛ هیچ ⇒ -1. هر پاسخِ دیگر باید در `ok` باشد. */
async function firstLimited(n, send, ok = [200]) {
  for (let i = 0; i < n; i++) {
    const r = await send(i);
    if (r.status === 429) return i;
    assert.ok(ok.includes(r.status), `درخواستِ ${i}: ${r.status} ${JSON.stringify(r.body)}`);
  }
  return -1;
}
async function adminLogin() {
  const r = await h.post('/api/admin/login', { username: 'malek', password: PASS });
  assert.equal(r.status, 200, JSON.stringify(r.body));
  return r.body.token;
}
async function makeKey(name) {
  const r = await h.post('/api/admin/service-keys', { name, roleCap: 'superadmin', reason: 'پلِ پنل' }, { token: admin });
  assert.equal(r.status, 201, JSON.stringify(r.body));
  return { key: r.body.key, id: r.body.serviceKey.id };
}

test.before(async () => {
  //  ⚠️ پیش از ساختنِ برنامه: سقف‌ها همان لحظه خوانده می‌شوند
  config.rateLimit.generalMax = GENERAL;
  config.rateLimit.authMax = AUTH;
  config.rateLimit.adminProvenMax = PROVEN;
  config.rateLimit.adminProbeMax = PROBE;
  await h.start();
  await require('../src/lib/sync-v1').syncSchemaTable();
  const pw = require('../src/lib/password');
  await query(
    `INSERT INTO admins (id, username, name, password_hash, role, status, created_at) VALUES ($1,'malek','صاحب',$2,'superadmin','active',$3)`,
    [newId('adm'), await pw.hashPassword(PASS), now()]
  );
  admin = await adminLogin();
  key = (await makeKey('پنلِ خانه')).key;
  user = await h.newUser('کاربرِ سطل', 'shop');
});
test.after(() => h.stop());
test.beforeEach(reset);

test('⛔ بازسازی: N+1 درخواستِ پل از loopback دیگر ۴۲۹ نمی‌گیرد — ولی سطلِ پل هم سقف دارد', async () => {
  //  پیش از اصلاح: درخواستِ سیزدهم (GENERAL) ۴۲۹ ⇒ «سقفِ نرخِ سرورِ حساب پر شده»
  const n = GENERAL + 5;
  for (let i = 0; i < n; i++) {
    const r = await bridge();
    assert.equal(r.status, 200, `درخواستِ ${i} ِ پل: ${r.status} ${JSON.stringify(r.body)}`);
    assert.equal(r.body.actor.id, 'panel-owner');
  }
  //  بی‌سقف نه: کلیدِ دزدیده‌شده سرور را غرق نمی‌کند
  assert.equal(await firstLimited(PROVEN + 5, () => bridge()), PROVEN - n);
});

test('سطلِ پل و سطلِ عمومی از دو سو جدا‌اند', async () => {
  //  ترافیکِ عمومیِ همان IP سطلِ خودش را تا ته پر می‌کند…
  assert.equal(await firstLimited(GENERAL + 3, () => pub()), GENERAL);
  //  …و پل همچنان کار می‌کند
  assert.equal((await bridge()).status, 200);

  await reset();
  //  و برعکس: انبوهِ کارِ پل سهمِ ترافیکِ عمومی را نمی‌خورد
  for (let i = 0; i < GENERAL + 5; i++) assert.equal((await bridge()).status, 200);
  assert.equal(await firstLimited(GENERAL + 3, () => pub()), GENERAL);
});

test('کلیدِ ساختگی، باطل، توکنِ کاربر و توکنِ دست‌کاری‌شده ⇒ همان سطلِ IP ِ پیشین', async () => {
  const revoked = await makeKey('باطل‌شده');
  const rv = await h.post(`/api/admin/service-keys/${revoked.id}/revoke`, { reason: 'آزمون' }, { token: admin });
  assert.equal(rv.status, 200, JSON.stringify(rv.body));
  for (const [name, tok] of [
    ['ساختگی', forged()], ['باطل', revoked.key], ['کاربر', user.accessToken], ['دست‌کاری', h.tampered(admin)], ['بی‌توکن', null],
  ]) {
    await reset();
    assert.equal(await firstLimited(GENERAL + 3, () => bridge(tok), [401]), GENERAL, name);
  }
  //  ساختگی و عمومی یک سطل‌اند (همان «سطلِ قدیم»)
  await reset();
  for (let i = 0; i < GENERAL / 2; i++) assert.equal((await bridge(forged())).status, 401);
  assert.equal(await firstLimited(GENERAL, () => pub()), GENERAL / 2);
});

test('⛔ سقفِ IP پیش از احراز: پس از پر شدنِ سطل‌ها کلیدِ ساختگی هیچ پرسشی از پایگاه نمی‌سازد', async (t) => {
  const svc = require('../src/lib/admin-service');
  const tokens = require('../src/lib/tokens');
  const orig = { svc: svc.verify, tok: tokens.verify };
  let lookups = 0;
  svc.verify = async (...a) => { lookups += 1; return orig.svc(...a); };
  tokens.verify = async (...a) => { lookups += 1; return orig.tok(...a); };
  t.after(() => { svc.verify = orig.svc; tokens.verify = orig.tok; });
  assert.equal(await firstLimited(GENERAL + 3, () => bridge(forged()), [401]), GENERAL);
  const before = lookups;
  for (let i = 0; i < 10; i++) assert.equal((await bridge(forged())).status, 429);
  assert.equal(lookups, before, 'ساختگیِ پس از سقف نباید به پایگاه برسد');
});

test('کلیدِ معتبر بیرون از درهای مدیر، روی مدیرِ پمپ و روی ورود ⇒ همان سطلِ عمومی', async () => {
  const auth = { Authorization: `Bearer ${key}`, ...actor };
  assert.equal(await firstLimited(GENERAL + 3, () => pub(auth)), GENERAL, 'مسیرِ عمومی');
  await reset();
  //  ⛔ `admin-pump` دست نمی‌خورد
  assert.equal(await firstLimited(GENERAL + 3, () => h.api('GET', '/api/admin/pump/features', { headers: auth })), GENERAL, 'admin-pump');
  await reset();
  assert.equal(await firstLimited(GENERAL + 3, () => h.api('GET', '/api/v1/admin/PUMP/features', { headers: auth })), GENERAL, 'admin-pump (v1، حروفِ بزرگ)');
  await reset();
  //  ورودِ مدیر با کلیدِ معتبر در سرآیند هم سطلِ عمومی را می‌خورد
  for (let i = 0; i < 3; i++) {
    assert.equal((await h.api('POST', '/api/admin/login', { headers: auth, body: { username: 'malek', password: 'nope' } })).status, 401);
  }
  assert.equal(await firstLimited(GENERAL, () => pub()), GENERAL - 3);
});

test('⛔ X-Forwarded-For ِ ساختگی کمکی نمی‌کند', async (t) => {
  const keep = { trust: config.trustProxy, from: config.trustProxyFrom };
  t.after(() => { config.trustProxy = keep.trust; config.trustProxyFrom = keep.from; });
  config.trustProxy = true;

  //  همسایهٔ نامطمئن (این‌جا loopback بیرون از `TRUST_PROXY_FROM`): XFF ِ تازه در هر درخواست سطل نمی‌سازد
  config.trustProxyFrom = ['10.9.0.0/16'];
  assert.equal(await firstLimited(GENERAL + 3, (i) => bridge(forged(), { 'X-Forwarded-For': `198.51.100.${i + 1}` }), [401]), GENERAL);

  //  پشتِ درگاهِ مطمئن: «من خودِ پنل‌ام» (XFF = 127.0.0.1 / ::1) بی کلید ⇒ همان سطلِ IP، نه سطلِ پل
  config.trustProxyFrom = [];
  await reset();
  assert.equal(await firstLimited(GENERAL + 3, (i) => bridge(i % 2 ? null : forged(), { 'X-Forwarded-For': '127.0.0.1' }), [401]), GENERAL);
  await reset();
  assert.equal(await firstLimited(GENERAL + 3, () => pub({ 'X-Forwarded-For': '::1' })), GENERAL);

  //  کلیدِ معتبر سطلش را از کلید می‌گیرد، نه از IP: چرخاندنِ XFF با کلیدِ دزدیده سطلِ تازه نمی‌سازد
  await reset();
  assert.equal(await firstLimited(PROVEN + 3, (i) => bridge(key, { 'X-Forwarded-For': `203.0.113.${(i % 3) + 1}` })), PROVEN);
});

test('ترافیکِ عمومی همان سقف را دارد — هر مشتری پشتِ درگاه سطلِ خودش', async (t) => {
  const keep = { trust: config.trustProxy, from: config.trustProxyFrom };
  t.after(() => { config.trustProxy = keep.trust; config.trustProxyFrom = keep.from; });
  config.trustProxy = true;
  config.trustProxyFrom = [];
  assert.equal(await firstLimited(GENERAL + 3, () => pub({ 'X-Forwarded-For': '198.51.100.20' })), GENERAL);
  assert.equal(await firstLimited(GENERAL + 3, () => pub({ 'X-Forwarded-For': '198.51.100.21' })), GENERAL);
  assert.equal(await firstLimited(GENERAL + 3, () => pub()), GENERAL, 'خودِ loopback هم');
});

test('⛔ سقفِ ورود و کد دقیقاً همان — کلیدِ معتبر در سرآیند چیزی را باز نمی‌کند', async () => {
  const auth = { Authorization: `Bearer ${key}`, ...actor };
  const adminTry = () => h.api('POST', '/api/admin/login', { headers: auth, body: { username: 'malek', password: 'nope' } });
  assert.equal(await firstLimited(AUTH + 3, adminTry, [401]), AUTH, 'ورودِ مدیر');
  await reset();
  const userTry = () => h.api('POST', '/api/auth/login', { headers: auth, body: { identifier: user.email, password: 'nope', app: 'shop' } });
  assert.equal(await firstLimited(AUTH + 3, userTry, [401]), AUTH, 'ورودِ کاربر');
  await reset();
  const otpTry = () => h.api('POST', '/api/auth/otp/request', { headers: auth, body: { email: 'bucket-otp@example.com' } });
  assert.equal(await firstLimited(6, otpTry), 3, 'فرستادنِ کد');
  await reset();
  //  دومرحله‌ایِ مدیر (`admin-2fa`) هم با کلیدِ معتبر همان سقفِ IP را دارد
  const twoFa = () => h.api('POST', '/api/admin/2fa/setup', { token: admin, body: {} });
  const hit = await firstLimited(AUTH + 3, twoFa, [200, 201, 400, 401, 403, 409]);
  assert.equal(hit, AUTH, 'admin-2fa');
});

test('نشستِ مدیر (راهِ پیشینِ پل، کنسولِ صاحب) هم سطلِ خودش را دارد؛ خروج همان دم برمی‌گرداند', async () => {
  for (let i = 0; i < GENERAL + 5; i++) assert.equal((await h.api('GET', '/api/admin/me', { token: admin })).status, 200, `درخواستِ ${i}`);
  //  نشستِ دیگری از همان مدیر سطلِ دوم نمی‌سازد (سطل به‌ازای مدیر، نه هر توکن)
  const second = await adminLogin();
  assert.equal(await firstLimited(PROVEN + 3, () => h.api('GET', '/api/admin/me', { token: second })), PROVEN - (GENERAL + 5));

  //  خروج: نخستین درخواستِ پس از آن ۴۰۱ است و از همان دم سطلِ IP
  await reset();
  const gone = await adminLogin();
  assert.equal((await h.api('GET', '/api/admin/me', { token: gone })).status, 200);
  assert.equal((await h.api('POST', '/api/admin/logout', { token: gone })).status, 200);
  await reset();
  assert.equal(await firstLimited(GENERAL + 3, () => h.api('GET', '/api/admin/me', { token: gone }), [401]), GENERAL + 1);
});

test('کلیدی که باطل شد همان دم به سطلِ IP برمی‌گردد', async () => {
  const k = await makeKey('موقت');
  assert.equal((await bridge(k.key)).status, 200);
  assert.equal((await h.post(`/api/admin/service-keys/${k.id}/revoke`, { reason: 'آزمون' }, { token: admin })).status, 200);
  await reset();
  assert.equal(await firstLimited(GENERAL + 3, () => bridge(k.key), [401]), GENERAL + 1);
});

test('پایگاه هنگامِ سنجیدن خطا داد ⇒ همان سطلِ IP (نه ۵۰۰، نه سطلِ پل)', async (t) => {
  const k = await makeKey('خطای پایگاه');
  await reset();
  const svc = require('../src/lib/admin-service');
  const orig = svc.verify;
  //  پرسشِ سطل خطا می‌گیرد، پرسشِ `requireAdmin` نه
  svc.verify = async (...a) => {
    if (/admin-bucket\.js/.test(new Error().stack.split('\n')[2])) throw new Error('پایگاه افتاد');
    return orig(...a);
  };
  t.after(() => { svc.verify = orig; });
  assert.equal(await firstLimited(GENERAL + 3, () => bridge(k.key)), GENERAL);
});

test('`RATE_ADMIN_BUCKET=0` ⇒ همان رفتارِ پیشین (سطلِ IP برای همه)', async (t) => {
  const keep = config.rateLimit.adminBucket;
  t.after(() => { config.rateLimit.adminBucket = keep; });
  config.rateLimit.adminBucket = false;
  assert.equal(await firstLimited(GENERAL + 3, () => bridge()), GENERAL);
});
