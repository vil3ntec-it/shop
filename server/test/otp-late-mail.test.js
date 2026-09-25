'use strict';
/**
 * «کد آمد اما سرور آن را نمی‌بیند» و «کد ده جا نوشته شده» — ۱۴۰۵/۰۷/۱۳.
 *
 * ⛔ گزارشِ صاحب سامانه با عکس: دو ایمیلِ کد پشتِ سرِ هم و دیر رسیدند؛ کدِ
 * نخست که زده شد، سرور فقط تازه‌ترین را می‌سنجید و «کد درست نیست» می‌گفت.
 * و در اعلانِ گوشی کد در عنوان و پیش‌نمایش پیدا بود.
 */
const test = require('node:test');
const assert = require('node:assert/strict');
const h = require('./helpers');
const { query, one } = require('../src/db');
const otp = require('../src/lib/otp');
const config = require('../src/config');

test.before(async () => { await h.start(); });
test.after(async () => { await h.stop(); });

/** دو کد برای یک نشانی، با فاصله‌ای که سقفِ «دوباره بفرست» را رد کند. */
async function twoCodes(email, purpose) {
  await otp.request(email, { purpose, app: 'pump' });
  //  دو دقیقه به عقب — همان «یک بار دیگر بفرست»ِ کاربر
  await query('UPDATE otp_codes SET created_at = created_at - $3 WHERE destination=$1 AND purpose=$2',
    [email, purpose, config.otp.resendMs + 1000]);
  await otp.request(email, { purpose, app: 'pump' });
  const rows = await require('../src/db').many(
    'SELECT id FROM otp_codes WHERE destination=$1 AND purpose=$2 ORDER BY created_at ASC', [email, purpose]);
  assert.equal(rows.length, 2);
  return Promise.all(rows.map(async (r) => (await otp.reveal(r.id)).code));
}

test('⛔ کدِ نخست (که دیرتر رسید) هنوز پذیرفته می‌شود، و بعدش هیچ کدی از همان نشانی نه', async () => {
  const [first, second] = await twoCodes('late@example.com', 'login');
  assert.notEqual(first, second);
  assert.equal(await otp.verify('late@example.com', first, { purpose: 'login' }), true);
  await assert.rejects(() => otp.verify('late@example.com', second, { purpose: 'login' }),
    (e) => ['otp_not_found', 'otp_used'].includes(e.code), 'کدِ دوم هم باطل شد');
});

test('⛔ کدِ غلط همچنان رد می‌شود و سقفِ تلاش سرِ جایش است', async () => {
  await twoCodes('guess@example.com', 'login');
  await assert.rejects(() => otp.verify('guess@example.com', '000000', { purpose: 'login' }), (e) => e.code === 'otp_wrong');
  const { attempts } = await one(
    "SELECT attempts FROM otp_codes WHERE destination='guess@example.com' ORDER BY created_at DESC LIMIT 1");
  assert.equal(attempts, 1, 'یک خانه از سقف');
});

test('⛔ کدِ منقضی پذیرفته نمی‌شود، حتی اگر درست باشد', async () => {
  const [a, b] = await twoCodes('old@example.com', 'login');
  await query("UPDATE otp_codes SET expires_at = 1 WHERE destination='old@example.com'");
  await assert.rejects(() => otp.verify('old@example.com', a, { purpose: 'login' }), (e) => e.code === 'otp_expired');
  await assert.rejects(() => otp.verify('old@example.com', b, { purpose: 'login' }), (e) => e.code === 'otp_expired');
});

test('کد ده دقیقه وقت دارد — همان که قالبِ ایمیل می‌گوید', () => {
  assert.equal(config.otp.ttlMs, 10 * 60 * 1000);
});

test('⛔ ایمیلِ کد: کد نه در عنوان است، نه در پیش‌نمایش؛ فرستنده نامِ همان برنامه', async () => {
  const mailer = require('../src/lib/mailer');
  const orig = mailer.send;
  const mails = [];
  mailer.send = async (m) => { mails.push(m); return { delivered: true }; };
  try {
    await otp.senders.email('who@example.com', '735102', '', { app: 'pump', purpose: 'login' });
    await otp.senders.email('who@example.com', '735103', '', { app: 'shop', purpose: 'login' });
    await otp.senders.email('who@example.com', '735104', '', { app: 'pump', purpose: 'reset' });
  } finally {
    mailer.send = orig;
  }
  const [pump, shop, reset] = mails;
  assert.equal(pump.subject, 'کد ورود ویلن');
  assert.equal(shop.subject, 'کد ورود VILL3N Shop');
  assert.equal(reset.subject, 'کد بازیابیِ رمز');
  for (const m of mails) {
    assert.ok(!/\d{6}/.test(m.subject), `عنوان بی کد: ${m.subject}`);
    //  پیش‌نمایش = نخستین نوشتهٔ نامه؛ خطِ پنهانِ سرِ نامه کد ندارد
    const head = m.html.slice(0, m.html.indexOf('</div>'));
    assert.ok(/display:none/.test(head) && !/\d{6}/.test(head), 'پیش‌نمایش بی کد');
    assert.ok(!/\d{6}/.test(m.text.split('\n')[0]), 'خطِ نخستِ متن بی کد');
  }
  assert.equal(pump.fromName, 'ویلن');
  assert.equal(shop.fromName, 'VILL3N Shop');
  assert.ok(pump.html.includes('735102'), 'کد داخلِ خودِ نامه هست');
});

test('⛔ نامِ فرستندهٔ هر نامه واقعاً در «From» می‌نشیند', async () => {
  const src = require('node:fs').readFileSync(require.resolve('../src/lib/mailer'), 'utf8');
  assert.match(src, /const eff = mail\.fromName \? \{ \.\.\.cfg, fromName: String\(mail\.fromName\) \} : cfg;/);
  assert.match(src, /return smtpSend\(eff, /);
});
