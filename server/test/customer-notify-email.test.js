'use strict';
/**
 * کانالِ `email` ِ اعلانِ مشتری (`lib/customer-notify-email.js`؛ «فقط ایمیل»، ۱۴۰۵/۰۸/۲۳: زنجیرهٔ پوش ⇒ ایمیل ⇒ …، بی پیامک).
 *   • فقط به ایمیلِ تأییدشدهٔ همان پیام (`to.email`)؛ بی ایمیل ⇒ `rejected` ِ `no_email` (کانالِ بعدی).
 *   • همان `mailer`؛ راهِ `log` ⇒ کانال نیست (`builtin` ⇒ null)؛ عنوان = نامِ ویترین؛ HTML گریز داده‌شده؛ لینک اگر هست.
 *   • ۵xx ِ SMTP ⇒ `rejected`؛ هر خطای دیگر ⇒ `retry` (NTF-04: شاید رفته باشد، کانالِ بعدی نه).
 */
const test = require('node:test');
const assert = require('node:assert/strict');
const mailer = require('../src/lib/mailer');
const E = require('../src/lib/customer-notify-email');

test('ایمیل فقط به ایمیلِ تأییدشده، با متنِ همان قالب و HTML ِ گریز داده‌شده؛ بی ایمیل ⇒ rejected', async (t) => {
  const keep = { send: mailer.send, current: mailer.current };
  t.after(() => Object.assign(mailer, keep));
  const sent = [];
  mailer.send = async (m) => { sent.push(m); };
  const ok = await E.provider.send({
    to: { phone: '+93790001111', email: 'buyer@test.local' }, title: 'دکانِ <گل>', text: 'سفارشِ ۱۲ آماده است', link: 'https://x.test/s#/o/a"b',
  });
  assert.deepEqual(ok, { status: 'accepted', ref: 'email' });
  assert.equal(sent.length, 1);
  assert.equal(sent[0].to, 'buyer@test.local', 'هرگز به شماره');
  assert.equal(sent[0].subject, 'دکانِ <گل>');
  assert.ok(sent[0].text.includes('سفارشِ ۱۲ آماده است') && sent[0].text.includes('https://x.test/s#/o/a"b'));
  assert.ok(!sent[0].html.includes('<گل>') && sent[0].html.includes('&lt;گل&gt;'), 'HTML گریز');
  assert.ok(!sent[0].html.includes('a"b') && sent[0].html.includes('a&quot;b'));

  const none = await E.provider.send({ to: { phone: '+93790001111' }, title: 'x', text: 'y' });
  assert.deepEqual(none, { status: 'rejected', code: 'no_email' });
  assert.equal(sent.length, 1, 'بی ایمیل هیچ فرستاده نشد');
});

test('خطای SMTP: ۵xx ⇒ rejected (کانالِ بعدی)؛ گذرا ⇒ retry؛ راهِ log ⇒ کانال نیست', async (t) => {
  const keep = { send: mailer.send, current: mailer.current };
  t.after(() => Object.assign(mailer, keep));
  mailer.send = async () => { throw Object.assign(new Error('mailbox unavailable'), { smtpCode: 550 }); };
  assert.deepEqual(await E.provider.send({ to: { email: 'a@test.local' }, title: 't', text: 'x' }), { status: 'rejected', code: 'smtp_550' });
  mailer.send = async () => { throw Object.assign(new Error('try again later'), { smtpCode: 421 }); };
  assert.equal((await E.provider.send({ to: { email: 'a@test.local' }, title: 't', text: 'x' })).status, 'retry');
  mailer.send = async () => { throw new Error('ECONNRESET'); };
  assert.equal((await E.provider.send({ to: { email: 'a@test.local' }, title: 't', text: 'x' })).status, 'retry');

  mailer.current = async () => ({ provider: 'log' });
  assert.equal(await E.builtin(), null, 'سرویسِ ایمیل تنظیم‌نشده ⇒ not_configured');
  mailer.current = async () => ({ provider: 'smtp' });
  assert.equal(await E.builtin(), E.provider);
});
