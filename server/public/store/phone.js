/*
 * ویترین — «تغییرِ شمارهٔ تلفن» ِ حسابِ مشتری (§۲۳.۴، درِ `POST /api/store/<slug>/account/phone` ِ `lib/store-customers.changePhone`).
 * فقط با نشستِ حساب و دیربار از acct.js («حسابِ من»)؛ acct.js خودش فقط با `features.webAccount` می‌آید.
 *
 *   ۱) شمارهٔ تازه ⇒ همان شمارهٔ کنونی؟ فقط پیام (هیچ کدی). وگرنه کد به **شمارهٔ تازه** با همان `verify/start` (سقف‌های lib/otp).
 *   ۲) کد ⇒ `verify/check` ⇒ بلیتِ تازه (`fresh`) ⇒ همان در با `phone` ِ همان بلیت. بلیت فقط برای همین درخواست روی دستگاه
 *      می‌نشیند (K.api آن را می‌فرستد) و اگر سرور نپذیرفت بلیتِ پیشین برمی‌گردد. قطعِ شبکه یا شکستِ پس از کد ⇒ «تأیید» ِ
 *      دوباره همان بلیت را می‌فرستد، بی کدِ تازه (کدِ مصرف‌شده دوباره پذیرفته نمی‌شود).
 *   ۳) سرور: «حسابِ جدا دارد» (۴۰۹) فقط پس از کد؛ بلیتِ کهنه ⇒ کدِ تازه؛ نشستِ رفته ⇒ `lost` (همان خروجِ acct.js).
 *   ۴) موفق ⇒ `done(customer)`: acct.js نشستِ همین دستگاه را با شمارهٔ تازه می‌نویسد و «حسابِ من» را از نو می‌سازد.
 * ⛔ همهٔ متن با textContent (`K.el`)؛ واژه‌نامهٔ `kit/i18n/phone.<زبان>.json` نیامد ⇒ هیچ گره‌ای. دکمه‌ها و پیامِ کدِ آزمایشی واژه‌های `acct` را دارند.
 */
(function () {
  'use strict';
  var $ = K.$; var el = K.el; var t = K.t;

  /** `ctx` از acct.js: `api`، `phone` (شمارهٔ نشست)، `into` (کارتِ «حسابِ من»)، `done(customer)` و `lost()` */
  window.StorePhone = function (ctx) {
    var api = ctx.api;
    return K.part('phone', $('ph-box')).then(function (ok) {
      if (!ok || !ctx.into || $('ph-box')) return;
      var box = el('details', { class: 'more-box', attrs: { id: 'ph-box' } }, el('summary', { text: t('ph.title') }));
      var msg = el('div', { class: 'note', attrs: { role: 'status', 'aria-live': 'polite' }, hidden: true });
      var phone = el('input', { attrs: { id: 'ph-phone', type: 'tel', inputmode: 'tel', autocomplete: 'tel', dir: 'ltr', maxlength: '20', 'aria-label': t('ph.new'), placeholder: t('ph.new') } });
      var send = el('button', { class: 'btn btn-soft btn-sm', attrs: { type: 'button', id: 'ph-send' }, text: t('ph.send') });
      var code = el('input', { attrs: { id: 'ph-code', inputmode: 'numeric', autocomplete: 'one-time-code', dir: 'ltr', maxlength: '8', 'aria-label': t('ph.code'), placeholder: t('ph.code') } });
      var go = el('button', { class: 'btn btn-sm', attrs: { type: 'button', id: 'ph-ok' }, text: t('ac.check') });
      var row = el('div', { class: 'code-row', hidden: true }, code, go);
      box.appendChild(el('div', { class: 'stack' }, el('p', { class: 'muted small', text: t('ph.p') }), phone, send, row, msg));
      ctx.into.appendChild(box);
      var fresh = null;   //  بلیتِ شمارهٔ تازه که همین فرم گرفته (تا تلاشِ دوباره کدِ مصرف‌شده را نخواهد)

      function digits(s) { return String(s || '').replace(/[۰-۹]/g, function (d) { return String(d.charCodeAt(0) - 0x06F0); }).replace(/\D/g, ''); }
      function same(a, b) { var x = digits(a).replace(/^(0093|93|0)/, ''); return x.length > 6 && x === digits(b).replace(/^(0093|93|0)/, ''); }
      function fail(e) {
        if (e.code === 'session_required') { ctx.lost(); return; }
        if (e.code === 'verification_required') { fresh = null; code.value = ''; K.note(msg, 'info', t('ph.again')); return; }
        K.note(msg, e.code === 'verification_unavailable' || e.code === 'phone_taken' ? 'warn' : 'err', e.message);
      }

      phone.addEventListener('input', function () { fresh = null; row.hidden = true; });
      send.addEventListener('click', function () {
        if (same(phone.value, ctx.phone)) { K.note(msg, 'info', t('ph.same')); return; }
        send.disabled = true;
        api('/verify/start', { method: 'POST', body: { phone: phone.value } }).then(function (r) {
          fresh = null; row.hidden = false;
          K.note(msg, 'info', t('ph.sent') + (r.devCode ? ' ' + t('ac.dev', { code: r.devCode }) : ''));
          code.focus();
        }).catch(fail).then(function () { send.disabled = false; });
      });

      /** همان در با بلیتِ تازه؛ نپذیرفت ⇒ بلیتِ پیشینِ دستگاه برمی‌گردد */
      function change() {
        var prev = K.ticket();
        K.setTicket(fresh);
        return api('/account/phone', { method: 'POST', body: { phone: fresh.phone } }).then(function (r) {
          K.account();
          ctx.done(r.customer);
          K.toast(r.unchanged ? t('ph.same') : t('ph.done', { phone: K.localPhone(r.customer.phone) }));
        }, function (e) { K.setTicket(prev); throw e; });
      }
      go.addEventListener('click', function () {
        go.disabled = true;
        var ready = fresh && fresh.expiresAt > Date.now() ? Promise.resolve() : api('/verify/check', { method: 'POST', body: { phone: phone.value, code: code.value } })
          .then(function (r) { fresh = r; });
        ready.then(change).catch(fail).then(function () { go.disabled = false; });
      });
    });
  };
})();
