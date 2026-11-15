/*
 * ویترین — «شمارهٔ تماس» و «ایمیلِ حساب» در «حسابِ من» (§۲۳.۴ ⇐ «فقط ایمیل» ِ صاحب‌کار ۱۴۰۵/۰۸/۲۳). فقط با نشستِ حساب و
 * دیربار از acct.js؛ acct.js خودش فقط با `features.webAccount` می‌آید.
 *
 *   شمارهٔ تماس ── فقط برای تماسِ پیک: `POST /api/store/<slug>/account/phone` بی هیچ کدی (فقط شکل؛ خالی ⇒ پاک).
 *   ایمیل ──────── تغییر (یا وصلِ نخستینِ ایمیل به حسابِ کهنهٔ فقط‌شماره): کد به **ایمیلِ تازه** با همان `verify/start`
 *                  (سقف‌های lib/otp) ⇒ `verify/check` ⇒ بلیتِ تازه (`fresh`) ⇒ `POST …/account/email` با ایمیلِ همان بلیت.
 *                  بلیت فقط برای همین درخواست روی دستگاه می‌نشیند و اگر سرور نپذیرفت بلیتِ پیشین برمی‌گردد. قطعِ شبکه
 *                  پس از کد ⇒ «تأیید» ِ دوباره همان بلیت را می‌فرستد، بی کدِ تازه. «حسابِ جدا دارد» (۴۰۹) فقط پس از کد.
 *                  سفارش‌های پیشین در «سفارش‌های من» ِ حساب می‌مانند.
 *   موفق ⇒ `done(customer)`: acct.js نشستِ همین دستگاه را با ایمیل/شمارهٔ تازه می‌نویسد و «حسابِ من» را از نو می‌سازد.
 * ⛔ همهٔ متن با textContent (`K.el`)؛ واژه‌نامهٔ `kit/i18n/phone.<زبان>.json` نیامد ⇒ هیچ گره‌ای. دکمه‌ها و پیامِ کدِ آزمایشی واژه‌های `acct` را دارند.
 */
(function () {
  'use strict';
  var $ = K.$; var el = K.el; var t = K.t;

  /** `ctx` از acct.js: `api`، `email` و `phone` (نشست)، `into` (کارتِ «حسابِ من»)، `done(customer)` و `lost()` */
  window.StorePhone = function (ctx) {
    var api = ctx.api;
    return K.part('phone', $('ph-box')).then(function (ok) {
      if (!ok || !ctx.into || $('ph-box')) return;
      var linking = !ctx.email;   //  حسابِ کهنهٔ فقط‌شماره: نخست ایمیل را وصل کند
      var box = el('details', { class: 'more-box', attrs: { id: 'ph-box' } }, el('summary', { text: t(linking ? 'em.link' : 'ph.title') }));
      if (linking) box.setAttribute('open', '');
      var msg = el('div', { class: 'note', attrs: { role: 'status', 'aria-live': 'polite' }, hidden: true });
      function fail(e) {
        if (e.code === 'session_required') { ctx.lost(); return; }
        K.note(msg, e.code === 'verification_unavailable' || e.code === 'email_taken' ? 'warn' : 'err', e.message);
      }

      // ── شمارهٔ تماس، بی کد ──
      var phone = el('input', { attrs: { id: 'ph-phone', type: 'tel', inputmode: 'tel', autocomplete: 'tel', dir: 'ltr', maxlength: '20', 'aria-label': t('ph.new'), placeholder: t('ph.new') } });
      phone.value = ctx.phone ? K.localPhone(ctx.phone) : '';
      var save = el('button', { class: 'btn btn-soft btn-sm', attrs: { type: 'button', id: 'ph-save' }, text: t('ph.save') });
      save.addEventListener('click', function () {
        save.disabled = true;
        api('/account/phone', { method: 'POST', body: { phone: phone.value } }).then(function (r) {
          ctx.done(r.customer);
          K.toast(r.unchanged ? t('ph.same') : (r.customer.phone ? t('ph.done', { phone: K.localPhone(r.customer.phone) }) : t('ph.cleared')));
        }).catch(fail).then(function () { save.disabled = false; });
      });

      // ── ایمیل، با کد به ایمیلِ تازه ──
      var email = el('input', { attrs: { id: 'em-email', type: 'email', inputmode: 'email', autocomplete: 'email', dir: 'ltr', maxlength: '190', 'aria-label': t('em.new'), placeholder: t('em.new') } });
      var send = el('button', { class: 'btn btn-soft btn-sm', attrs: { type: 'button', id: 'em-send' }, text: t('em.send') });
      var code = el('input', { attrs: { id: 'em-code', inputmode: 'numeric', autocomplete: 'one-time-code', dir: 'ltr', maxlength: '8', 'aria-label': t('em.code'), placeholder: t('em.code') } });
      var go = el('button', { class: 'btn btn-sm', attrs: { type: 'button', id: 'em-ok' }, text: t('ac.check') });
      var row = el('div', { class: 'code-row', hidden: true }, code, go);
      var fresh = null;   //  بلیتِ ایمیلِ تازه که همین فرم گرفته (تا تلاشِ دوباره کدِ مصرف‌شده را نخواهد)

      email.addEventListener('input', function () { fresh = null; row.hidden = true; });
      send.addEventListener('click', function () {
        if (K.normEmail(email.value) && K.normEmail(email.value) === ctx.email) { K.note(msg, 'info', t('em.same')); return; }
        send.disabled = true;
        api('/verify/start', { method: 'POST', body: { email: email.value } }).then(function (r) {
          fresh = null; row.hidden = false;
          K.note(msg, 'info', t('em.sent') + (r.devCode ? ' ' + t('ac.dev', { code: r.devCode }) : ''));
          code.focus();
        }).catch(fail).then(function () { send.disabled = false; });
      });
      /** همان در با بلیتِ تازه؛ نپذیرفت ⇒ بلیتِ پیشینِ دستگاه برمی‌گردد */
      function change() {
        var prev = K.ticket();
        K.setTicket(fresh);
        return api('/account/email', { method: 'POST', body: { email: fresh.email } }).then(function (r) {
          K.account();
          ctx.done(r.customer);
          K.toast(r.unchanged ? t('em.same') : t('em.done', { email: r.customer.email }));
        }, function (e) {
          K.setTicket(prev);
          if (e.code === 'verification_required') { fresh = null; code.value = ''; K.note(msg, 'info', t('ph.again')); return; }
          throw e;
        });
      }
      go.addEventListener('click', function () {
        go.disabled = true;
        var ready = fresh && fresh.expiresAt > Date.now() ? Promise.resolve() : api('/verify/check', { method: 'POST', body: { email: email.value, code: code.value } })
          .then(function (r) { fresh = r; });
        ready.then(change).catch(fail).then(function () { go.disabled = false; });
      });

      box.appendChild(el('div', { class: 'stack' },
        el('p', { class: 'muted small', text: t('ph.p') }), phone, save,
        el('p', { class: 'muted small', text: t(linking ? 'em.link.p' : 'em.p') }), email, send, row, msg));
      ctx.into.appendChild(box);
    });
  };
})();
