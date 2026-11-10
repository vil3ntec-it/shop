/*
 * ویترین — «وزنِ بیشتر از سفارش» ِ صفحهٔ پیگیری (S56، §۱۶؛ `lib/pick-weight-hold.js`، پرچمِ `shop.orders.weight_hold`).
 * فقط وقتی پاسخِ پیگیری کلیدِ `weightHold` را دارد (دستِ‌کم یک نگه‌داشتِ باز) بار می‌شود (`order.js`)؛ پرچمِ خاموش یا بی
 * نگه‌داشتِ باز ⇒ نه این اسکریپت، نه واژه‌نامه‌اش (`kit/i18n/weigh.<زبان>.json`)، نه هیچ گره‌ای در صفحه.
 *
 *   کارت ── هر قلم: وزنِ سفارش و واقعی، مبلغِ تازه به‌جای پیشین، «تا … جواب دهید — n دقیقه مانده» و «می‌پذیرم»/«نه، حذف
 *            شود» (`POST …/weight-holds/:id`)؛ پاسخ‌داده ⇒ فقط حالت. مهلت با ساعتِ سرور (`now` ِ پاسخ) شمرده می‌شود.
 *   مهلت ── سر رسید ⇒ دکمه‌ها می‌روند و «مهلت گذشت؛ حذف شد…» (policy ِ سرور: بی رضایت هرگز گران‌تر نمی‌شود).
 *
 * ⛔ هر متن فقط با textContent (`K.el`). مبلغ، مهلت و حالت فقط از سرور؛ «پذیرفته شد» فقط پس از پاسخِ سرور.
 */
(function () {
  'use strict';
  var $ = K.$; var el = K.el; var t = K.t;
  var MIN = 60 * 1000;

  window.StoreWeightHold = function (ctx) {
    var api = ctx.api;
    var box = $('o-wh');
    if (!box) {
      box = el('div', { class: 'card card-lg stack', hidden: true, attrs: { id: 'o-wh', role: 'group', 'aria-labelledby': 'o-wh-h' } });
      var at = $('o-subs');
      at.parentNode.insertBefore(box, at);
    }
    var skew = 0;     //  ساعتِ سرور − ساعتِ این گوشی
    var open = [];    //  نگه‌داشت‌های باز: { h, row, left }
    var timer = 0;
    var seq = 0;      //  پاسخِ دیرِ خواندنِ پیشین روی خواندنِ تازه نمی‌نشیند
    var busy = false; //  تصمیم در راه ⇒ پیگیریِ ۲۰ ثانیه‌ای دکمه‌ها را دوباره روشن نمی‌کند

    function stop() { if (timer) { clearInterval(timer); timer = 0; } }

    function tick() {
      if (box.hidden || !box.isConnected) { stop(); return; }
      var now = Date.now() + skew;
      open = open.filter(function (x) {
        var ms = x.h.deadlineAt - now;
        if (ms > 0) {
          x.left.textContent = t('wh.left', { t: K.clock(x.h.deadlineAt - skew), n: K.fa(Math.ceil(ms / MIN), 0) });
          return true;
        }
        K.clear(x.row).appendChild(el('small', { class: 'muted', text: t('wh.st.timeout') }));
        return false;
      });
      if (!open.length) stop();
    }

    function stateText(h) {
      var s = h.status === 'accepted' ? t('wh.st.accepted') : t(h.decidedBy === 'timeout' || h.expired ? 'wh.st.timeout' : 'wh.st.rejected');
      return h.decidedBy === 'shop' ? s + ' ' + t('wh.by.shop') : s;
    }

    function draw(r, token) {
      var list = r.weightHolds || [];
      skew = typeof r.now === 'number' ? r.now - Date.now() : 0;
      stop(); open = [];
      K.clear(box);
      box.hidden = !list.length;
      if (!list.length) return;
      box.appendChild(el('h2', { text: t('wh.title'), attrs: { id: 'o-wh-h' } }));
      box.appendChild(el('p', { class: 'muted small', text: t('wh.intro') }));
      var ul = el('ul', { class: 'sum-lines', attrs: { id: 'o-wh-list' } });
      list.forEach(function (h) {
        ul.appendChild(el('li', {}, el('span', { class: 'grow' },
          t('wh.line', { name: h.name || '', a: K.qty(h.orderedQtyMilli), b: K.qty(h.actualQtyMilli), u: h.unit || '' }),
          el('small', { text: t('wh.amount', { to: K.money(h.totalMinor), from: K.money(h.originalTotalMinor), x: K.money(h.extraMinor) }) })),
          el('span', { class: 'amt num', text: K.money(h.totalMinor) })));
        var row = el('li', { attrs: { 'data-hold': h.id } });
        if (h.status === 'pending' && !h.expired) {
          var left = el('small', { class: 'muted' });
          var yes = el('button', { class: 'btn btn-accent', text: t('wh.yes', { m: K.money(h.totalMinor) }), attrs: { type: 'button' } });
          var no = el('button', { class: 'btn btn-ghost', text: t('wh.no'), attrs: { type: 'button' } });
          yes.onclick = function () { choose(token, h, 'accept', [yes, no]); };
          no.onclick = function () { choose(token, h, 'reject', [yes, no]); };
          row.appendChild(el('div', { class: 'grow' }, left, el('div', { class: 'row wrap-row' }, yes, no)));
          open.push({ h: h, row: row, left: left });
        } else {
          row.appendChild(el('small', { class: 'muted', text: stateText(h) }));
        }
        ul.appendChild(row);
      });
      box.append(ul, el('div', { class: 'note', hidden: true, attrs: { id: 'o-wh-msg', role: 'status' } }));
      if (open.length) { tick(); timer = setInterval(tick, 5000); }
    }

    /** خواندنِ نگه‌داشت‌ها؛ هرگز رد نمی‌شود. بی شبکه ⇒ همان کارت؛ ۴۰۴ (پرچمِ خاموش) و بقیه ⇒ پنهان. */
    function load(token) {
      var my = ++seq;
      return api('/orders/' + encodeURIComponent(token) + '/weight-holds').then(function (r) {
        if (my === seq) draw(r, token);
      }, function (e) {
        if (my === seq && e.status !== 0) { box.hidden = true; stop(); }
      });
    }

    function choose(token, h, decision, btns) {
      busy = true;
      btns.forEach(function (b) { b.disabled = true; });
      api('/orders/' + encodeURIComponent(token) + '/weight-holds/' + encodeURIComponent(h.id), { method: 'POST', body: { decision: decision } })
        .then(function () {
          busy = false;
          K.toast(t(decision === 'accept' ? 'wh.done.yes' : 'wh.done.no'));
          return load(token);
        }, function (e) {
          busy = false;
          return load(token).then(function () { K.note(box.hidden ? $('o-msg') : $('o-wh-msg'), 'err', e.message); });
        });
    }

    function render(o, token) { if (!busy) load(token); }

    return K.part('weigh', $('o-wh')).then(function (ok) {
      if (!ok) throw new Error('i18n');
      return { render: render };
    });
  };
})();
