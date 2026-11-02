/*
 * ویترین — رسید و پیگیریِ یک سفارش (CHK-20/22، S136/S137، ORD-16)، «پرداخت با حساب‌پی» (PAY-21) و
 * تأییدِ جایگزینِ کالای پیدانشده (PCK-05).
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
      renderDelivery(o.delivery);
      renderEta(o);
      renderPay(o, token);
      renderSubs(o, token);
      renderRate(o, token);
      renderPrefs(o, token);
      return o;
    }

    //  DLV-18: پس از «تحویل شد» و تا ۷ روز، یک بار — مشتری به پیک ستاره و برچسب می‌دهد (بی متنِ آزاد).
    //  پرچمِ خاموش ⇒ ۴۰۴ ⇒ پنهان؛ سرور تصمیمِ آخر را می‌گیرد. فرمِ ساخته‌شده با تازه‌شدنِ صفحه پاک نمی‌شود.
    var RT = [{ tag: 'on_time', label: 'od.rt.on_time' }, { tag: 'polite', label: 'od.rt.polite' }, { tag: 'careful', label: 'od.rt.careful' },
      { tag: 'late', label: 'od.rt.late' }, { tag: 'rude', label: 'od.rt.rude' }, { tag: 'damaged', label: 'od.rt.damaged' }];
    function chip(text, aria, click) {
      return el('button', { class: 'chip plain', attrs: { type: 'button', 'aria-pressed': 'false', 'aria-label': aria }, text: text, on: { click: click } });
    }
    function renderRate(o, token) {
      var box = $('o-rate');
      if (o.status !== 'delivered' || o.fulfillment !== 'delivery') { box.hidden = true; return; }
      if (box.getAttribute('data-for') === token) return;
      box.setAttribute('data-for', token);
      var path = '/orders/' + encodeURIComponent(token) + '/rating';
      api(path).then(function (r) {
        var form = $('o-rate-form'); var msg = $('o-rate-msg'); var send = $('o-rate-send');
        box.hidden = !(r.canRate || r.rating);
        form.hidden = !r.canRate;
        if (!r.canRate) { if (r.rating) K.note(msg, 'ok', t('od.rt.done')); return; }
        var pick = { stars: 0, tags: [] };
        var sRow = K.clear($('o-rate-stars')); var tRow = K.clear($('o-rate-tags'));
        [1, 2, 3, 4, 5].forEach(function (n) {
          sRow.appendChild(chip(K.fa(n, 0) + ' ★', t('od.rt.stars', { n: K.fa(n, 0) }), function () {
            pick.stars = n; send.disabled = false;
            Array.prototype.forEach.call(sRow.children, function (c, i) { c.setAttribute('aria-pressed', String(i === n - 1)); });
          }));
        });
        RT.forEach(function (x) {
          var b = chip(t(x.label), t(x.label), function () {
            var i = pick.tags.indexOf(x.tag);
            if (i >= 0) pick.tags.splice(i, 1); else if (pick.tags.length < 4) pick.tags.push(x.tag); else return;
            b.setAttribute('aria-pressed', String(i < 0));
          });
          tRow.appendChild(b);
        });
        send.onclick = function () {
          send.disabled = true;
          api(path, { method: 'POST', body: { stars: pick.stars, tags: pick.tags } }).then(function () {
            form.hidden = true; K.note(msg, 'ok', t('od.rt.done'));
          }).catch(function (e) { send.disabled = false; K.note(msg, 'err', e.message); });
        };
      }).catch(function () { box.hidden = true; box.removeAttribute('data-for'); });
    }

    //  PCK-05: کالایی که پیدا نشد و دکان جایگزینِ هم‌دسته‌اش را پیشنهاد داده — مشتری همین‌جا می‌پذیرد یا رد
    //  می‌کند. مبلغ را سرور می‌سازد (قیمتِ کمتر)؛ پس از مهلت ترجیحِ خودِ مشتری. پرچمِ خاموش ⇒ ۴۰۴ ⇒ پنهان.
    function renderSubs(o, token) {
      var box = $('o-subs');
      if (o.status !== 'picking') { box.hidden = true; return; }
      api('/orders/' + encodeURIComponent(token) + '/substitutions').then(function (r) {
        var list = r.substitutions || [];
        box.hidden = !list.length;
        var ul = K.clear($('o-sub-list'));
        list.forEach(function (s) {
          var was = s.original || {}; var n = s.substitute || {};
          ul.appendChild(el('li', {}, el('span', { class: 'grow' }, t('od.sub.line', { a: was.name || '', b: n.name || '' }),
            el('small', { text: K.qty(n.qtyMilli) + ' ' + (n.unit || '') + ' × ' + K.money(n.unitPriceMinor) })),
            el('span', { class: 'amt num', text: K.money(n.totalMinor) })));
          if (s.status === 'pending' && !s.expired) {
            ul.appendChild(el('li', {}, el('small', { class: 'grow muted', text: t('od.sub.until', { t: K.clock(s.deadlineAt) }) }),
              el('button', { class: 'btn btn-accent', attrs: { type: 'button' }, text: t('od.sub.yes'), on: { click: function () { choose(o, token, s.id, 'accept'); } } }),
              el('button', { class: 'btn btn-ghost', attrs: { type: 'button' }, text: t('od.sub.no'), on: { click: function () { choose(o, token, s.id, 'reject'); } } })));
          } else {
            var yes = s.status === 'accepted' || s.willApply === 'accept';
            ul.appendChild(el('li', {}, el('small', { class: 'muted', text: t(yes ? 'od.sub.accepted' : 'od.sub.rejected') })));
          }
        });
      }).catch(function () { box.hidden = true; });
    }

    function choose(o, token, id, decision) {
      api('/orders/' + encodeURIComponent(token) + '/substitutions/' + encodeURIComponent(id), { method: 'POST', body: { decision: decision } })
        .then(function () { renderSubs(o, token); })
        .catch(function (e) { K.note($('o-msg'), 'err', e.message); renderSubs(o, token); });
    }

    //  DLV-06 (S5): پیک برای مشتری فقط از نمای سرور (`delivery`، پشتِ پرچمِ shop.delivery_customer_view):
    //  نامِ کوچک، رسیدنِ تقریبی و کدِ تحویل — نه شماره، نه شناسه. بی کلید یا چیزی برای گفتن ⇒ پنهان.
    // ── CHK-24: ویرایشِ دستورِ تحویل تا پیش از ارسال — فقط با `o.deliveryPrefs` (پرچمِ `shop.orders.delivery_prefs`) ──
    //  پیگیری هر ۲۰ ثانیه دوباره می‌کشد: کادرها فقط با نسخهٔ تازه از سرور پر می‌شوند تا نوشتهٔ مشتری پاک نشود
    function renderPrefs(o, token) {
      var p = o.deliveryPrefs; var f = $('o-prefs'); var msg = $('op-msg');
      f.hidden = !p;
      if (!p) return;
      if (f.getAttribute('data-v') !== String(p.version)) {
        $('op-call').checked = p.callBeforeArrival; $('op-leave').value = p.leaveWith; $('op-lm').value = '';
        f.setAttribute('data-v', String(p.version));
      }
      ['op-call', 'op-leave', 'op-lm', 'op-save'].forEach(function (id) { $(id).disabled = !p.editable; });
      if (!p.editable) K.note(msg, 'info', t('od.prefs.locked'));
      else if (msg.getAttribute('data-ok') !== String(p.version)) msg.hidden = true;
      f.onsubmit = function (ev) {
        ev.preventDefault();
        var body = { delivery: { callBeforeArrival: $('op-call').checked, leaveWith: $('op-leave').value.trim() }, expectedVersion: p.version };
        var lm = $('op-lm').value.trim();
        if (lm) body.address = { landmark: lm };
        $('op-save').disabled = true;
        api('/orders/' + encodeURIComponent(token) + '/delivery', { method: 'POST', body: body }).then(function (r) {
          var np = r.order && r.order.deliveryPrefs;
          if (np) { msg.setAttribute('data-ok', String(np.version)); f.setAttribute('data-v', ''); o.deliveryPrefs = np; renderPrefs(o, token); }
          K.note(msg, 'ok', t('od.prefs.saved'));
        }).catch(function (e) { K.note(msg, 'err', e.message); $('op-save').disabled = false; });
      };
    }

    function renderDelivery(d) {
      var name = d && d.courierName ? String(d.courierName) : '';
      var eta = d && d.etaAt ? K.clock(d.etaAt) : '';
      var code = d && d.code ? String(d.code) : '';
      var arrived = Boolean(d) && d.status === 'arrived';
      $('o-courier').hidden = !name;
      $('o-courier').textContent = name ? t(arrived ? 'od.courier.at' : 'od.courier', { name: name }) : '';
      $('o-eta').hidden = !eta;
      $('o-eta').textContent = eta ? t('od.eta', { t: eta }) : '';
      $('o-code').hidden = !code;
      $('o-code-n').textContent = code.replace(/[0-9]/g, function (c) { return K.fa(Number(c), 0); });
      $('o-dlv').hidden = !(name || eta || code);
    }

    //  WEB-04: زمانِ تقریبیِ همین سفارش (`order.eta`، پرچمِ `shop.storefront.eta`؛ خاموش ⇒ کلید نیست ⇒ پنهان)؛ با هر بارِ
    //  پیگیری از نو و به مرحله (سرور می‌سازد)؛ همیشه بازه و «تقریبی»، هرگز ساعتِ دقیق. `null` (پایان/زمان‌بندی) ⇒ پنهان.
    function renderEta(o) {
      var e = o.eta; var box = $('o-eta-live');
      box.hidden = !e;
      box.textContent = e ? t(o.fulfillment === 'pickup' ? 'od.eta.ready' : 'od.eta.live', { a: K.fa(e.minMinutes, 0), b: K.fa(e.maxMinutes, 0) }) : '';
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
