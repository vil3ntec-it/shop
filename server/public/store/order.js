/*
 * ویترین — رسید و پیگیریِ یک سفارش (CHK-20/22، S136/S137، ORD-16) و «پرداخت با حساب‌پی» (PAY-21).
 * فقط با بازشدنِ صفحهٔ سفارش بار می‌شود (`loadOrderView` در store.js) تا JS ِ بارِ اولِ ویترین زیرِ
 * سقفِ ۷۵KB بماند (WEB-15، §۳.۱۰)؛ خواندنِ سفارش، پیگیریِ زنده و مسیر همان store.js است.
 *
 * ⛔ نشانی و مبلغِ پرداخت را سرور می‌سازد و «پرداخت شد» فقط از وضعیتِ سرور می‌آید، هرگز از
 *    برگشت از صفحهٔ حساب‌پی. نقد هنگامِ تحویل همان می‌ماند.
 */
(function () {
  'use strict';
  var $ = K.$; var el = K.el; var t = K.t;

  /** `ctx` از store.js: `api(path, opts)`، `slug` ِ دکان و `reorder(o)` (سبد در store.js است) */
  window.StoreOrder = function (ctx) {
    var api = ctx.api;

    function renderOrder(o, token) {
      $('o-title').textContent = t('od.no', { n: K.fa(o.orderNo, 0) });
      var st = $('o-status');
      var why = K.reason(o.statusReason);
      st.className = 'pill ' + K.orderKind(o.status);
      st.textContent = K.order(o.status) + (why ? ' — ' + why : '');
      $('o-meta').textContent = (o.fulfillment === 'pickup' ? t('pickup') : t('od.ship')) + t('sep') + K.when(o.createdAt);
      //  ORD-16: هر تغییر با زمانش (خلاصه در پله‌ها، همه این‌جا)
      var tl = K.clear($('o-timeline'));
      (o.timeline || []).forEach(function (e) {
        tl.appendChild(el('li', {}, el('span', { text: K.order(e.status) }), el('span', { class: 'num', text: K.when(e.at) })));
      });
      var steps = K.clear($('o-steps'));
      K.orderSteps(o).forEach(function (s) {
        var dot = el('span', { class: 'dot' });
        if (s.state === 'done') dot.appendChild(K.icon('check'));
        if (s.state === 'stop') dot.appendChild(K.icon('close'));
        steps.appendChild(el('li', { class: 'step ' + s.state, attrs: s.state === 'now' ? { 'aria-current': 'step' } : {} },
          dot, el('span', { class: 'lbl' }, el('b', { text: s.label }), s.at ? el('small', { text: K.when(s.at) }) : null)));
      });
      var ul = K.clear($('o-lines'));
      o.lines.forEach(function (l) {
        ul.appendChild(el('li', {}, el('span', { class: 'grow' }, l.name, el('small', { text: K.qty(l.qtyMilli) + ' ' + (l.unit || '') + ' × ' + K.money(l.unitPriceMinor) })),
          el('span', { class: 'amt num', text: K.money(l.lineTotalMinor) })));
      });
      var paid = o.paymentStatus === 'paid' ? o.totalMinor : 0;
      var rows = [['od.sub', K.money(o.subtotalMinor)]];
      if (o.discountMinor) rows.push(['od.disc', K.money(o.discountMinor)]);
      if (o.fulfillment === 'delivery') rows.push(['ck.fee', o.deliveryFeeMinor ? K.money(o.deliveryFeeMinor) : t('free')]);
      rows.push(['od.total', K.money(o.totalMinor)], ['od.paid', K.money(paid)], ['od.due', K.money(o.totalMinor - paid)]);
      var tt = K.clear($('o-totals'));
      rows.forEach(function (r) {
        var g = r[0] === 'od.total';
        tt.append(el('span', { class: g ? 'grand' : '', text: t(r[0]) }), el('span', { class: g ? 'grand num' : 'num', text: r[1] }));
      });
      $('o-cancel').hidden = !o.canCancel;
      $('o-reorder').hidden = false;
      $('o-cancel').onclick = function () {
        K.confirm(t('od.cancel.q'), t('od.cancel.yes')).then(function (yes) {
          if (!yes) return;
          api('/orders/' + encodeURIComponent(token) + '/cancel', { method: 'POST', body: {} }).then(function (r) {
            renderOrder(r.order, token);
            K.toast(t('od.cancelled'));
          }).catch(function (e) { K.note($('o-msg'), 'err', e.message); });
        });
      };
      $('o-reorder').onclick = function () { ctx.reorder(o); };
      renderPay(o, token);
      return o;
    }

    //  PAY-21: «پرداخت با حساب‌پی» — نشانی و مبلغ را سرور می‌سازد؛ «پرداخت شد» فقط از وضعیتِ سرور
    //  (وب‌هوک + تأییدِ دوم)، هرگز از برگشت از صفحهٔ حساب‌پی. نقد هنگامِ تحویل همان می‌ماند.
    function renderPay(o, token) {
      var st = o.paymentStatus;
      //  «در انتظار» هم: مشتریِ برگشته بی پرداخت همان جلسه را دوباره باز می‌کند (همان کلید ⇒ همان نشانی)
      var can = Boolean(o.onlinePay && o.onlinePay.available) && (st === 'unpaid' || st === 'failed' || st === 'pending');
      $('o-pay').hidden = !can;
      $('o-payhint').hidden = !can;
      $('o-pay').disabled = false;
      if (o.paymentMethod === 'online' && st === 'paid') K.note($('o-paynote'), 'ok', t('od.pay.paid'));
      else if (st === 'pending') K.note($('o-paynote'), 'warn', t('od.pay.pending'));
      else if (st === 'failed' && can) K.note($('o-paynote'), 'warn', t('od.pay.failed'));
      else $('o-paynote').hidden = true;
      $('o-pay').onclick = function () { payOnline(token, true); };
    }

    function payOnline(token, retry) {
      var scope = 'pay:' + ctx.slug + ':' + token;
      $('o-pay').disabled = true;
      K.note($('o-paynote'), 'ok', t('od.pay.redirect'));
      api('/orders/' + encodeURIComponent(token) + '/pay', { method: 'POST', body: {}, key: K.keyFor(scope, { token: token }) }).then(function (r) {
        var p = r.payment || {};
        //  تلاشِ پیشینِ همین کلید بسته شده (ناموفق/منقضی/لغو) ⇒ یک بار با کلیدِ تازه
        if (p.status !== 'pending' && p.status !== 'created') {
          K.dropKey(scope);
          if (retry) return payOnline(token, false);
          throw new Error(t('od.pay.failed'));
        }
        if (!/^https:\/\//.test(p.checkoutUrl || '')) throw new Error(t('err.generic'));
        location.assign(p.checkoutUrl);
      }).catch(function (e) {
        $('o-pay').disabled = false;
        K.note($('o-paynote'), 'err', e.message);
      });
    }

    return { render: renderOrder };
  };
})();
