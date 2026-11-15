/*
 * ویترین — «پاک کردنِ داده‌های من در این دکان» (SEC-10، درِ `POST /api/store/<slug>/account/erase` ِ `lib/customer-erasure.js`).
 * فقط با `features.erasure` (پرچمِ `shop.customer_erasure`) و نشستِ حساب؛ دیربار از acct.js («سفارش‌های من»).
 *
 *   ۱) «ببینم چه پاک می‌شود» ⇒ `dryRun` (سرور در تراکنشِ برگشتی می‌شمارد، چیزی نمی‌نویسد) ⇒ شمارِ هر دسته؛ سفارشِ باز ⇒
 *      فقط پیام (دکمهٔ پاک کردن نیست)؛ هیچ ⇒ «داده‌ای نمانده».
 *   ۲) «می‌دانم برگشت ندارد» ⇒ دکمهٔ پاک کردن باز. سرور بلیتِ **تازهٔ همان ایمیلِ حساب** می‌خواهد («فقط ایمیل»): ۴۰۱/۴۰۳ ⇒
 *      همین‌جا کد برای ایمیلِ حساب (نه ایمیلِ دیگر) و پس از تأیید همان درخواست دوباره.
 *   ۳) پاک شد ⇒ فهرستِ «سفارش‌های من» ِ همین دکان روی این دستگاه هم (پیوندهای پیگیری دیگر باز نمی‌شوند).
 * ⛔ همهٔ متن با textContent (`K.el`)؛ ایمیل فقط ایمیلِ نشستِ خودِ حساب؛ واژه‌نامهٔ `kit/i18n/erase.<زبان>.json` نیامد ⇒ هیچ گره‌ای.
 */
(function () {
  'use strict';
  var $ = K.$; var el = K.el; var t = K.t;

  /** `ctx` از acct.js: `api`، `slug`، `email` (ایمیلِ نشست) و `into` (کارتِ «حسابِ من») */
  window.StoreErase = function (ctx) {
    var api = ctx.api;
    return K.part('erase', $('er-box')).then(function (ok) {
      if (!ok || !ctx.into || $('er-box')) return;
      var box = el('details', { class: 'more-box', attrs: { id: 'er-box' } }, el('summary', { text: t('er.title') }));
      var body = el('div', { class: 'stack' });
      var msg = el('div', { class: 'note', attrs: { role: 'status', 'aria-live': 'polite' }, hidden: true });
      var check = el('button', { class: 'btn btn-soft btn-sm', attrs: { type: 'button', id: 'er-check' }, text: t('er.check') });
      var res = el('div', { class: 'stack', attrs: { id: 'er-res' } });
      body.append(el('p', { class: 'muted small', text: t('er.p') }), check, res, msg);
      box.appendChild(body);
      ctx.into.appendChild(box);

      function sum(c, keys) { return keys.reduce(function (s, k) { return s + (Number(c[k]) || 0); }, 0); }
      function item(ul, n, text) { if (n > 0) ul.appendChild(el('li', { text: text })); }
      function fail(e) {
        if (e.code === 'open_orders') K.note(msg, 'warn', t('er.open', { n: K.fa((e.data && e.data.openOrders) || 1, 0) }));
        else K.note(msg, 'err', e.message);
      }

      function preview(r) {
        K.clear(res);
        K.note(msg, '', '');
        if (r.openOrders > 0) { K.note(msg, 'warn', t('er.open', { n: K.fa(r.openOrders, 0) })); return; }
        if (!(r.total > 0)) { K.note(msg, 'info', t('er.none')); return; }
        var c = r.counts || {};
        var ul = el('ul', { class: 'stack small', attrs: { id: 'er-list' } });
        var n = function (keys) { return sum(c, keys); };
        item(ul, n(['orders']), t('er.g.orders', { n: K.fa(n(['orders']), 0) }));
        item(ul, n(['conversations']), t('er.g.chat', { n: K.fa(n(['conversations']), 0) }));
        var media = ['deliveryPhotos', 'pickPhotos', 'issuePhotos', 'chatMedia', 'deliveryLocations'];
        item(ul, n(media), t('er.g.media', { n: K.fa(n(media), 0) }));
        item(ul, n(['carts', 'favorites', 'lists']), t('er.g.saved', { n: K.fa(n(['carts', 'favorites', 'lists']), 0) }));
        var alerts = ['areaRequests', 'alerts', 'alertPushSubs', 'pushSubs', 'notifyOutbox'];
        item(ul, n(alerts), t('er.g.alerts', { n: K.fa(n(alerts), 0) }));
        item(ul, n(['issues', 'reviews']), t('er.g.notes', { n: K.fa(n(['issues', 'reviews']), 0) }));
        if (!ul.firstChild) ul.appendChild(el('li', { text: t('er.other') }));
        var sure = el('input', { attrs: { type: 'checkbox', id: 'er-sure' } });
        var go = el('button', { class: 'btn btn-danger btn-sm', attrs: { type: 'button', id: 'er-go' }, text: t('er.go') });
        go.disabled = true;
        sure.addEventListener('change', function () { go.disabled = !sure.checked; });
        go.addEventListener('click', function () { run(go); });
        res.append(ul, el('label', { class: 'check' }, sure, ' ', el('span', { text: t('er.sure') })), go);
      }

      function run(go) {
        go.disabled = true;
        api('/account/erase', { method: 'POST', body: {} }).then(done).catch(function (e) {
          go.disabled = false;
          //  بلیتِ کهنه یا بلیتِ ایمیلِ دیگر ⇒ کد برای همان ایمیلِ حساب
          if (e.code === 'verification_required' || e.code === 'email_mismatch') verify(go);
          else fail(e);
        });
      }

      function verify(go) {
        if ($('er-code')) { $('er-code').focus(); return; }
        var email = ctx.email;
        var code = el('input', { attrs: { id: 'er-code', inputmode: 'numeric', autocomplete: 'one-time-code', dir: 'ltr', maxlength: '8', 'aria-label': t('ac.code'), placeholder: t('ac.code') } });
        var send = el('button', { class: 'btn btn-soft btn-sm', attrs: { type: 'button', id: 'er-send' }, text: t('ac.send') });
        var ok = el('button', { class: 'btn btn-sm', attrs: { type: 'button', id: 'er-ok' }, text: t('ac.check') });
        var row = el('div', { class: 'code-row', hidden: true }, code, ok);
        var form = el('div', { class: 'stack', attrs: { id: 'er-verify' } }, el('p', { class: 'small', text: t('er.verify', { email: email }) }), send, row);
        res.insertBefore(form, go);
        K.note(msg, '', '');
        send.focus();
        send.addEventListener('click', function () {
          send.disabled = true;
          api('/verify/start', { method: 'POST', body: { email: email } }).then(function (r) {
            row.hidden = false;
            K.note(msg, 'info', t('ac.sent') + (r.devCode ? ' ' + t('ac.dev', { code: r.devCode }) : ''));
            code.focus();
          }).catch(function (e) { K.note(msg, e.code === 'verification_unavailable' ? 'warn' : 'err', e.message); })
            .then(function () { send.disabled = false; });
        });
        ok.addEventListener('click', function () {
          ok.disabled = true;
          api('/verify/check', { method: 'POST', body: { email: email, code: code.value } }).then(function (r) {
            K.setTicket(r); K.account(); form.remove();
            run(go);
          }).catch(function (e) { K.note(msg, 'err', e.message); }).then(function () { ok.disabled = false; });
        });
      }

      function done() {
        K.clear(res);
        check.remove();
        K.note(msg, 'ok', t('er.done'));
        //  پیوندهای پیگیریِ پیشین دیگر باز نمی‌شوند (نسلِ پیوند بالا رفت) ⇒ فهرستِ همین دکان روی این دستگاه هم پاک
        K.save('store-orders:' + ctx.slug, null);
        if ($('my-orders')) K.clear($('my-orders'));
        if ($('my-orders-empty')) $('my-orders-empty').hidden = false;
      }

      check.addEventListener('click', function () {
        check.disabled = true;
        api('/account/erase', { method: 'POST', body: { dryRun: true } }).then(preview, fail)
          .then(function () { check.disabled = false; });
      });
    });
  };
})();
