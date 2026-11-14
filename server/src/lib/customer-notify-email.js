'use strict';
/**
 * کانالِ `email` ِ اعلانِ مشتری (NTF-01، «فقط ایمیل» ِ صاحب‌کار ۱۴۰۵/۰۸/۲۳: زنجیره «پوش ⇒ ایمیل ⇒ واتس‌اپ ⇒ تلگرام»، بی پیامک).
 *
 *   • گیرنده فقط ایمیلِ **تأییدشدهٔ** همان سفارش/هشدار (`msg.to.email` ⇐ `online_orders.customer_email` یا کلیدِ «خبرم کن»)؛
 *     سفارشِ دستیِ کارمند یا کهنهٔ بی ایمیل ⇒ `rejected` ِ `no_email` و زنجیره کانالِ بعدی را می‌آزماید.
 *   • همان سرویسِ ایمیلِ کدها (`lib/mailer`)؛ راهِ `log` (تنظیم‌نشده) ⇒ این کانال نیست (`builtin` ⇒ null = `not_configured`).
 *     هیچ حسابِ پیامک یا بیرونیِ تازه‌ای لازم نیست.
 *   • متن همان قالبِ NTF-03 (نامِ ویترین، شمارهٔ سفارش، وضعیت؛ لینکِ پیگیری اگر هست) — بی نام، شماره، نشانی یا اقلام.
 *     عنوان همان نامِ ویترین. هر نویسهٔ HTML گریز داده می‌شود.
 *   • نتیجه: پذیرفت ⇒ `accepted`؛ ردِ همیشگیِ SMTP (۵xx) ⇒ `rejected`؛ هر خطای دیگر ⇒ `retry` (همان قاعدهٔ NTF-04).
 */
const esc = (s) => String(s || '').replace(/[&<>"']/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));

const provider = Object.freeze({
  async send(msg) {
    const to = msg && msg.to && msg.to.email;
    if (!to || !String(to).includes('@')) return { status: 'rejected', code: 'no_email' };
    const mailer = require('./mailer');
    const title = String((msg && msg.title) || '').slice(0, 120) || 'سفارش';
    const link = msg && msg.link ? String(msg.link) : '';
    try {
      await mailer.send({
        to,
        subject: title,
        text: link ? `${msg.text}\n\n${link}` : String(msg.text || ''),
        html: mailer.card({
          title: esc(title),
          lead: esc(msg.text),
          body: link ? `<a href="${esc(link)}">${esc(link)}</a>` : '',
        }),
      });
      return { status: 'accepted', ref: 'email' };
    } catch (err) {
      const smtp = Number(err && err.smtpCode);
      return smtp >= 500 ? { status: 'rejected', code: `smtp_${smtp}` } : { status: 'retry', code: String((err && err.message) || err).slice(0, 80) };
    }
  },
});

/** کانالِ ایمیل اگر سرویسِ ایمیلِ سرور واقعاً می‌فرستد (`log` ⇒ null). */
async function builtin() {
  const cfg = await require('./mailer').current();
  return cfg && cfg.provider && cfg.provider !== 'log' ? provider : null;
}

module.exports = { provider, builtin, esc };
