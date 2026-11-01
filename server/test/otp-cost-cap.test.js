'use strict';
/**
 * SEC-06 — سقفِ **هزینهٔ** روزانهٔ پیامک (بخشِ ۳.۱۲: «سقف روزانه هزینه برای هر فروشگاه و
 * هر کانال»)، روی دفترِ یگانهٔ sms_usage. سقفِ شمار و ضدِ pumping را otp-cost.test.js می‌سنجد.
 *
 *   - OTP_SMS_DAILY_COST_MAX_MINOR: جمعِ cost_minor ِ ۲۴ ساعتِ همین کانال (راهِ ارسال) در کلِ
 *     سرور ⇒ ۴۲۹ sms_cost_ceiling؛ برای همه، آشنا یا نه (بودجهٔ سخت)
 *   - OTP_SMS_SHOP_DAILY_COST_MAX_MINOR: همان برای دکانِ همین درخواست ⇒ ۴۲۹
 *     sms_shop_cost_ceiling؛ دکانِ دیگر و بی‌دکان آزاد
 *   - ۰ (پیش‌فرض) = خاموش؛ کهنه‌تر از ۲۴ ساعت و کانالِ دیگر شمرده نمی‌شوند؛ ایمیل و log سقف ندارند
 *   - پر شدن روزی یک بار برای هر سهم در سابقه (`sms.cost_ceiling`)؛ خطای خواندن ⇒ باز
 *   - resend ِ میزِ مدیر هم سقفِ کلِ سرور را می‌سنجد
 */
Object.assign(process.env, {
  OTP_SMS_DAILY_COST_MAX_MINOR: '1000', OTP_SMS_SHOP_DAILY_COST_MAX_MINOR: '300',
  OTP_SMS_IP_PHONES: '1000', OTP_SMS_BLOCK_PHONES: '1000', OTP_SMS_DAILY_MAX: '100000',
  OTP_DAILY_MAX: '100', OTP_RESEND_SECONDS: '0',
});
const test = require('node:test');
const assert = require('node:assert/strict');
const h = require('./helpers');
const { query, one, many, newId, now } = require('../src/db');
const otp = require('../src/lib/otp');
const usage = require('../src/lib/sms-usage');
const settings = require('../src/lib/sms-settings');
const L = require('../src/config').otp.smsGuard;

const realWebhook = otp.senders.webhook;
const DAY = 24 * 60 * 60 * 1000;
let sent = [];

test.before(async () => {
  await h.start();
  await query(
    `INSERT INTO admins (id, username, name, password_hash, role, status, created_at)
     VALUES ($1,'admin','مدیر','x','superadmin','active',$2)`, ['adm_cost', now()]
  );
  otp.senders.webhook = async (to) => { sent.push(to); return { delivered: true, via: 'webhook' }; };
  await settings.save({ provider: 'webhook' });
  await usage.setUnitCost(100, 'adm_cost');   // هر تکه ۱۰۰ واحدِ خُرد
});
test.after(async () => {
  otp.senders.webhook = realWebhook;
  await h.stop();
});
test.beforeEach(async () => {
  sent = [];
  await query('DELETE FROM sms_usage');
});

const code = (p) => p.then(() => 'ok', (e) => e.code || e.message);
const spend = (cost, { shopId = '', via = 'webhook', at = now() } = {}) => query(
  `INSERT INTO sms_usage (id, shop_id, app, purpose, via, ok, parts, cost_minor, at)
   VALUES ($1,$2,'shop','login',$3,true,1,$4,$5)`, [newId('smsu'), shopId, via, cost, at]
);
let n = 0;
const phone = () => `+93795${String(100000 + (n++)).slice(-6)}`;

test('پیش‌فرض‌ها: سقفِ هزینه از محیط خوانده می‌شود (۰ یعنی خاموش)', () => {
  assert.equal(L.dailyCostMax, 1000);
  assert.equal(L.shopDailyCostMax, 300);
});

test('کلِ سرور: تا زیرِ سقف می‌رود، پس از پر شدن ⇒ sms_cost_ceiling بی پیامک و بی ردیفِ کد', async () => {
  await spend(950);
  await spend(5000, { at: now() - DAY - 60_000 });          // کهنه‌تر از ۲۴ ساعت
  await spend(5000, { via: 'whatsapp' });                   // کانالِ دیگر
  const first = phone();
  assert.equal(await code(otp.request(first, { ip: '198.51.100.1' })), 'ok', '۹۵۰ < ۱۰۰۰');
  assert.equal(await usage.costSince({ since: now() - DAY, via: 'webhook' }), 1050);
  const blocked = phone();
  assert.equal(await code(otp.request(blocked, { ip: '198.51.100.2' })), 'sms_cost_ceiling');
  assert.deepEqual(sent, [first], 'پیامکِ دوم نرفت');
  assert.equal((await one('SELECT COUNT(*)::int AS n FROM otp_codes WHERE destination=$1', [blocked])).n, 0);
  //  ⛔ بودجهٔ سخت: شمارهٔ آشنا هم
  await query(`INSERT INTO store_customers (id, phone, created_at, updated_at) VALUES ($1,$2,$3,$3)`,
    [newId('scu'), '+93795999999', now()]);
  assert.equal(await code(otp.request('+93795999999', { ip: '198.51.100.3' })), 'sms_cost_ceiling');
  //  ایمیل سقفِ پیامک ندارد
  assert.equal(await code(otp.request(`cost-${Date.now()}@example.com`, { ip: '198.51.100.4' })), 'ok');
});

test('هر دکان: سقفِ جدا؛ دکانِ دیگر و بی‌دکان آزاد', async () => {
  await spend(250, { shopId: 'shop_a' });
  assert.equal(await code(otp.request(phone(), { ip: '203.0.113.1', shopId: 'shop_a', purpose: 'store_phone' })), 'ok');
  assert.equal(await usage.costSince({ since: now() - DAY, via: 'webhook', shopId: 'shop_a' }), 350);
  assert.equal(await code(otp.request(phone(), { ip: '203.0.113.2', shopId: 'shop_a', purpose: 'store_phone' })), 'sms_shop_cost_ceiling');
  assert.equal(await code(otp.request(phone(), { ip: '203.0.113.3', shopId: 'shop_b', purpose: 'store_phone' })), 'ok');
  assert.equal(await code(otp.request(phone(), { ip: '203.0.113.4' })), 'ok', 'بی‌دکان فقط سقفِ کل را دارد');
});

test('سابقه: پر شدنِ هر سهم روزی یک بار، بی شماره', async () => {
  const rows = () => many(`SELECT target_id, detail FROM audit_logs WHERE action='sms.cost_ceiling' ORDER BY created_at`);
  await spend(1000);
  await spend(300, { shopId: 'shop_c' });
  for (let i = 0; i < 3; i++) {
    assert.equal(await code(otp.request(phone(), { ip: '192.0.2.1' })), 'sms_cost_ceiling');
    assert.equal(await code(otp.request(phone(), { ip: '192.0.2.2', shopId: 'shop_c' })), 'sms_cost_ceiling', 'کل پیش از دکان');
  }
  const all = (await rows()).filter((r) => r.detail.scope === 'all');
  assert.equal(all.length, 1, 'سهمِ «کل» یک بار در روز (آزمون‌های بالا هم پرش کرده بودند)');
  assert.equal(all[0].detail.via, 'webhook');
  assert.equal(all[0].detail.cap, 1000);
  //  سهمِ دکان: فقط وقتی کل جا دارد به آن می‌رسیم
  await query('DELETE FROM sms_usage WHERE shop_id = $1', ['']);
  for (let i = 0; i < 3; i++) {
    assert.equal(await code(otp.request(phone(), { ip: '192.0.2.3', shopId: 'shop_c' })), 'sms_shop_cost_ceiling');
  }
  const shopC = (await rows()).filter((r) => r.detail.scope === 'shop' && r.target_id === 'shop_c');
  assert.equal(shopC.length, 1, 'هر دکان یک بار در روز');
  assert.doesNotMatch(JSON.stringify(await rows()), /\+93795/, 'شماره در سابقه نیست');
});

test('خطای خواندنِ دفتر ⇒ باز (فقط لاگ)، مثلِ سقفِ شمار', async () => {
  await spend(5000);
  const real = usage.costSince;
  usage.costSince = async () => { throw new Error('db gone'); };
  try {
    assert.equal(await code(otp.request(phone(), { ip: '192.0.2.50' })), 'ok');
  } finally { usage.costSince = real; }
});

test('resend ِ میزِ مدیر هم سقفِ کلِ سرور را می‌سنجد', async () => {
  const p = phone();
  assert.equal(await code(otp.request(p, { ip: '192.0.2.60' })), 'ok');
  const row = await one('SELECT id FROM otp_codes WHERE destination=$1 ORDER BY created_at DESC LIMIT 1', [p]);
  await spend(1000);
  const out = await otp.resend(row.id);
  assert.equal(out.ok, false);
  assert.equal(out.error, 'sms_cost_ceiling');
  assert.equal(sent.filter((x) => x === p).length, 1, 'دوباره نرفت');
});

test('خاموش (۰): هیچ سقفِ هزینه‌ای — رفتارِ پیش از این برش', async () => {
  const saved = { a: L.dailyCostMax, s: L.shopDailyCostMax };
  L.dailyCostMax = 0; L.shopDailyCostMax = 0;
  try {
    await spend(1_000_000);
    await spend(1_000_000, { shopId: 'shop_a' });
    assert.equal(await code(otp.request(phone(), { ip: '192.0.2.70', shopId: 'shop_a' })), 'ok');
  } finally { L.dailyCostMax = saved.a; L.shopDailyCostMax = saved.s; }
});
