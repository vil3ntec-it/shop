/*
 * ویترین — «پرداخت» ِ صفحهٔ پیگیری وقتی دکان «پرداخت پس از پذیرش» را روشن کرده (`lib/order-flow.js`، مهاجرتِ ۲۱۰).
 * فقط وقتی پاسخِ پیگیری کلیدِ `paymentStep` را دارد بار می‌شود (`order.js`)؛ تنظیمِ خاموش ⇒ نه این اسکریپت، نه
 * واژه‌نامه‌اش (`kit/i18n/paystep.<زبان>.json`) و نه هیچ گرهٔ تازه‌ای در صفحه.
 *
 *   پیش از قبول ⇒ «منتظرِ پذیرشِ فروشنده» (بی دکمه)؛ پس از قبول ⇒ «نقد هنگامِ تحویل/برداشت» (`POST …/pay-choice`)
 *   و حساب‌پی همان دکمهٔ `#o-pay` ِ `order.js` (فقط وقتی سرور `onlinePay.available` گفت).
 *
 * ⛔ هر متن فقط با textContent (`K.el`). مبلغ و حالت فقط از سرور؛ «انتخاب شد» فقط از پاسخِ سرور.
 */
(function () {
  'use strict';
  var $ = K.$; var el = K.el; var t = K.t;
  var KNOWN = ['awaiting_accept', 'awaiting_choice', 'cash', 'online_pending', 'paid_online', 'cash_collected', 'cash_settled', 'refunded', 'closed'];

  window.StorePayStep = function (ctx) {
    var api = ctx.api;
    var box = $('o-ps');
    if (!box) {
      box = el('div', { class: 'stack', hidden: true, attrs: { id: 'o-ps', role: 'group', 'aria-labelledby': 'o-ps-h' } });
      var at = $('o-paynote');
      at.parentNode.insertBefore(box, at);
    }

    function stateText(s) {
      var st = KNOWN.indexOf(s.state) >= 0 ? s.state : 'cash';
      if (st === 'cash') st = 'cash.' + (s.cash === 'pickup' ? 'pickup' : 'delivery');
      return t('ps.s.' + st, { a: K.money(s.amountMinor) });
    }

    function render(o, token) {
      var s = o.paymentStep;
      K.clear(box);
      box.hidden = false;
      box.appendChild(el('h2', { text: t('ps.title'), attrs: { id: 'o-ps-h' } }));
      box.appendChild(el('p', { text: stateText(s), attrs: { id: 'o-ps-state' } }));
      var note = el('div', { class: 'note', hidden: true, attrs: { id: 'o-ps-msg', role: 'status' } });
      if (s.canChoose && s.choice !== 'cash') {
        var cash = el('button', { class: 'btn btn-block', text: t(s.cash === 'pickup' ? 'ps.cash.pickup' : 'ps.cash.delivery'),
          attrs: { id: 'o-ps-cash', type: 'button' } });
        cash.onclick = function () {
          cash.disabled = true;
          api('/orders/' + encodeURIComponent(token) + '/pay-choice', { method: 'POST', body: { method: 'cash' } }).then(function (r) {
            ctx.show(r.order, token);
            K.toast(t('ps.cash.done'));
          }).catch(function (e) { cash.disabled = false; K.note(note, 'err', e.message); });
        };
        box.appendChild(cash);
      }
      if (s.canChoose && s.online) box.appendChild(el('p', { class: 'muted small', text: t('ps.online') }));
      box.appendChild(note);
    }

    return K.part('paystep', $('o-ps')).then(function (ok) {
      if (!ok) throw new Error('i18n');
      return { render: render };
    });
  };
})();
