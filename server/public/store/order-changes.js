/*
 * ویترین — «تغییرهای سفارش» در صفحهٔ پیگیری: پیشنهادِ ویرایشِ دکان با «می‌پذیرم/نمی‌پذیرم» (ORD-14) و
 * پیگیریِ بازپرداخت (NTF-08). فقط وقتی پاسخِ پیگیری کلیدِ پُرِ `amendment` (پرچمِ `shop.orders.amend`) یا
 * `refund` (پرچمِ `shop.orders.refund_tracking`) دارد بار می‌شود (`order.js`)؛ پرچم‌های خاموش یا کلیدِ تهی ⇒
 * نه این اسکریپت، نه واژه‌نامه‌اش (`kit/i18n/changes.<زبان>.json`) و نه هیچ گرهٔ تازه‌ای در صفحه.
 *
 * ⛔ هر متن فقط با textContent (`K.el`) — هرگز HTML. مبلغ و وضعیت فقط از سرور؛ «پذیرفته شد» فقط از پاسخِ سرور.
 * ⛔ تصمیم یک بار: دکمه‌ها تا پاسخ خاموش‌اند؛ ۴۰۹ (مهلت گذشت، سفارش عوض شد، دیگر باز نیست) ⇒ پیام و خواندنِ دوبارهٔ سفارش.
 */
(function () {
  'use strict';
  var $ = K.$; var el = K.el; var t = K.t;
  var AM = { pending: 'info', applied: 'ok', rejected: 'warn', withdrawn: 'warn', expired: 'warn', stale: 'warn' };
  var RF = { requested: 'info', in_progress: 'info', partial: 'ok', full: 'ok' };
  var REASONS = ['out_of_stock', 'customer_request', 'price_error'];

  function qty(m, unit) { return K.qty(m) + (unit ? ' ' + unit : ''); }

  window.StoreChanges = function (ctx) {
    var api = ctx.api;
    var box = $('o-ch');
    if (!box) {
      box = el('div', { class: 'card card-lg stack', attrs: { id: 'o-ch' }, hidden: true },
        el('div', { class: 'stack', attrs: { id: 'o-ch-am' }, hidden: true }),
        el('div', { class: 'stack', attrs: { id: 'o-ch-rf' }, hidden: true }));
      var at = $('o-subs');
      at.parentNode.insertBefore(box, at.nextSibling);
    }

    function path(token, rest) { return '/orders/' + encodeURIComponent(token) + rest; }
    //  پیامِ ۴۰۹ که باید پس از خواندنِ دوبارهٔ سفارش هم بماند (رندرِ تازه آن را جای وضعیت می‌نشاند)
    var flash = '';

    // ── ORD-14: پیشنهادِ ویرایش ──
    function renderAmend(o, token) {
      var a = o.amendment; var wrap = $('o-ch-am');
      wrap.hidden = !a;
      if (!a) return;
      K.clear(wrap);
      wrap.appendChild(el('h2', { text: t('ch.am.title') }));
      if (REASONS.indexOf(a.reason) >= 0) wrap.appendChild(el('p', { class: 'muted', text: t('ch.am.r.' + a.reason) }));
      var list = el('ul', { class: 'sum-lines', attrs: { id: 'o-ch-lines' } });
      (a.changes || []).forEach(function (c) {
        var to = c.toQtyMilli > 0 ? qty(c.toQtyMilli, c.unit) : t('ch.am.removed');
        list.appendChild(el('li', {}, el('span', { class: 'grow', text: t('ch.am.line', { n: c.name, a: qty(c.fromQtyMilli, c.unit), b: to }) }),
          el('span', { class: 'num', text: K.money(c.toLineTotalMinor) })));
      });
      wrap.appendChild(list);
      wrap.appendChild(el('p', { attrs: { id: 'o-ch-total' }, text: t('ch.am.total', { a: K.money(a.prevTotalMinor), b: K.money(a.totalMinor) }) }));
      //  سفارشِ پرداخت‌شده: تفاوت برمی‌گردد یا اضافه پرداخت می‌شود (`settlement` ِ سرور)
      var s = a.settlement;
      if (s && s.amountMinor > 0 && (s.kind === 'refund' || s.kind === 'charge')) {
        wrap.appendChild(el('p', { class: 'muted', text: t(s.kind === 'refund' ? 'ch.am.back' : 'ch.am.more', { a: K.money(s.amountMinor) }) }));
      }
      var note = el('div', { class: 'note', hidden: true, attrs: { id: 'o-ch-msg', role: 'status' } });
      if (a.status === 'pending') {
        if (a.expiresAt) wrap.appendChild(el('p', { class: 'muted small', text: t('ch.am.until', { d: K.when(a.expiresAt) }) }));
        //  ORD-14 (ممیزیِ ۱۴۰۵/۰۸/۱۷، مشکلِ ۱۰): «می‌پذیرم» دکمهٔ اصلیِ پررنگ، «نمی‌پذیرم» دکمهٔ دومِ قاب‌دار (پیش‌تر وارونه دیده می‌شد)
        var yes = el('button', { class: 'btn btn-block', text: t('ch.am.accept'), attrs: { id: 'o-ch-yes', type: 'button' } });
        var no = el('button', { class: 'btn btn-ghost btn-block', text: t('ch.am.reject'), attrs: { id: 'o-ch-no', type: 'button' } });
        var decide = function (what) {
          yes.disabled = true; no.disabled = true;
          api(path(token, '/amendments/' + encodeURIComponent(a.id) + '/' + what), { method: 'POST', body: {} }).then(function (r) {
            ctx.show(r.order, token);
          }).catch(function (e) {
            var code = e.code || '';
            var msg = K.has('ch.err.' + code) ? t('ch.err.' + code) : e.message;
            K.note(note, 'err', msg);
            if (e.status !== 409) { yes.disabled = false; no.disabled = false; return; }
            //  مهلت گذشت / سفارش عوض شد / بسته شد ⇒ وضعیتِ تازه از سرور (پیام می‌ماند)
            api(path(token, '')).then(function (r) { flash = msg; ctx.show(r.order, token); })
              .catch(function () { /* پیام بس است */ });
          });
        };
        yes.onclick = function () { decide('accept'); };
        no.onclick = function () {
          K.confirm(t('ch.am.reject.q'), t('ch.am.reject')).then(function (ok) { if (ok) decide('reject'); });
        };
        wrap.appendChild(yes);
        wrap.appendChild(no);
      } else {
        K.note(note, AM[a.status] || 'warn', t('ch.am.s.' + (AM[a.status] ? a.status : 'stale')));
      }
      if (flash) { K.note(note, 'err', flash + ' ' + (a.status === 'pending' ? '' : t('ch.am.s.' + (AM[a.status] ? a.status : 'stale')))); flash = ''; }
      wrap.appendChild(note);
    }

    // ── NTF-08: پیگیریِ بازپرداخت ──
    function renderRefund(o) {
      var r = o.refund; var wrap = $('o-ch-rf');
      wrap.hidden = !r;
      if (!r) return;
      K.clear(wrap);
      wrap.appendChild(el('h2', { text: t('ch.rf.title') }));
      var st = el('div', { class: 'note', attrs: { id: 'o-ch-rf-st', role: 'status' } });
      K.note(st, RF[r.status] || 'info', t('ch.rf.s.' + (RF[r.status] ? r.status : 'requested')));
      wrap.appendChild(st);
      wrap.appendChild(el('p', { attrs: { id: 'o-ch-rf-sum' }, text: t('ch.rf.sum', { a: K.money(r.refundedMinor), b: K.money(r.remainingMinor) }) }));
      if (r.entries && r.entries.length) {
        var list = el('ul', { class: 'sum-lines', attrs: { id: 'o-ch-rf-list' } });
        r.entries.forEach(function (e) {
          list.appendChild(el('li', {}, el('span', { text: K.when(e.at) + ' — ' + t(e.method === 'cash' ? 'ch.rf.cash' : 'ch.rf.other') }),
            el('span', { class: 'num', text: K.money(e.amountMinor) })));
        });
        wrap.appendChild(list);
      }
      if (r.requestedAt) wrap.appendChild(el('p', { class: 'muted small', text: t('ch.rf.asked', { d: K.when(r.requestedAt) }) }));
      //  E2E-RET-11: رسیدِ اصلاحیِ هر ردیفِ دفتر (پرچمِ `shop.orders.refund_receipt`؛ عددها همه از سرور)
      (r.receipts || []).forEach(function (x) {
        wrap.appendChild(el('div', { class: 'stack', attrs: { id: 'o-ch-rc-' + x.seq } },
          el('h3', { text: t('ch.rc.title', { no: x.no }) }),
          el('p', { class: 'small', text: t('ch.rc.line', { d: K.when(x.at), a: K.money(x.totalMinor), b: K.money(x.amountMinor), c: K.money(x.netMinor) }) })));
      });
    }

    function render(o, token) {
      renderAmend(o, token);
      renderRefund(o);
      box.hidden = !o.amendment && !o.refund;
    }

    return K.part('changes', $('o-ch')).then(function (ok) {
      if (!ok) throw new Error('i18n');
      return { render: render };
    });
  };
})();
