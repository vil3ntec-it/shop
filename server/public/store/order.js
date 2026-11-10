/*
 * ویترین — رسید و پیگیریِ یک سفارش (CHK-20/22، S136/S137، ORD-16)، «پرداخت با حساب‌پی» (PAY-21) و
 * تأییدِ جایگزینِ کالای پیدانشده (PCK-05)؛ گزارشِ مشکل و نظر (NTF-07/09) در `order-feedback.js` ِ دیربار و
 * پیشنهادِ ویرایش و پیگیریِ بازپرداخت (ORD-14، NTF-08) در `order-changes.js` ِ دیربار؛ «پرداخت پس از پذیرش» در
 * `pay-step.js` ِ دیربار؛ «خبرم کن روی همین دستگاه» (پوشِ وب، CHK-20/NTF-01) در `order-push.js` ِ دیربار.
 * فقط با بازشدنِ صفحهٔ سفارش بار می‌شود (`loadOrderView` در store.js) تا JS ِ بارِ اولِ ویترین زیرِ
 * سقف بماند (WEB-15، §۳.۱۰)؛ مسیر و نخستین خواندنِ سفارش در store.js، پیگیریِ زنده و برگهٔ خطا همین‌جا.
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
      if (want !== null && token !== want) return o;   //  پاسخِ دیرِ سفارشی که دیگر این‌جا نیست (لغو، جایگزین، روش…)
      seen(token);
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
      //  CHK-16/15: یادداشت و «اگر نبود» ِ مشتری زیرِ همان قلم (store/line-prefs.js ِ دیربار، فقط وقتی هست)
      if (o.substitution || o.lines.some(function (l) { return l.note || l.substitution; })) {
        K.js('/store/line-prefs.js').then(function () { return window.StoreLines({}); })
          .then(function (m) { m.receipt(ul, o); }, function () { /* بی واژه‌نامه ⇒ همان فهرست */ });
      }
      var rows = [['od.sub', K.money(o.subtotalMinor)]];
      if (o.discountMinor) rows.push(['od.disc', K.money(o.discountMinor)]);
      if (o.fulfillment === 'delivery') rows.push(['ck.fee', o.deliveryFeeMinor ? K.money(o.deliveryFeeMinor) : t('free')]);
      rows.push(['od.total', K.money(o.totalMinor)]);
      //  CHK-20/ORD-15/PCK-07: یک خلاصهٔ پولِ سازگار برای هر حال — بسته (رد، لغو، برگشت به دکان) ⇒ فقط جمع؛ رسیده به
      //  دستِ مشتری با نقد ⇒ «پرداخت‌شده (نقد…)» = جمع (بازپرداخت جدا در بخشِ خودش)؛ باز ⇒ «پرداخت‌شده» فقط برای آنلاینِ
      //  پرداخت‌شده و «مانده» فقط وقتی هنوز چیزی مانده — هرگز «پرداخت‌شده ۰» یا «مانده» کنارِ «پرداختی لازم نیست»
      var online = ['paid', 'partially_refunded', 'refunded'].indexOf(o.paymentStatus) >= 0;
      var done = ['delivered', 'picked_up', 'return_requested', 'refunded_partial', 'refunded_full'].indexOf(o.status) >= 0;
      if (online) rows.push(['od.paid', K.money(o.totalMinor)]);
      else if (done) rows.push(['od.paid', K.money(o.totalMinor), t('od.paid') + ' (' + t('cod') + ')']);
      else if (!/^(rejected|auto_rejected|cancelled|cancelled_by_customer|returned_to_store|restocked)$/.test(o.status)) rows.push(['od.due', K.money(o.totalMinor)]);
      var tt = K.clear($('o-totals'));
      rows.forEach(function (r) {
        var g = r[0] === 'od.total';
        tt.append(el('span', { class: g ? 'grand' : '', text: r[2] || t(r[0]) }), el('span', { class: g ? 'grand num' : 'num', text: r[1] }));
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
      renderMethod(o, token);
      renderPay(o, token);
      renderSubs(o, token);
      renderRate(o, token);
      renderPrefs(o, token);
      renderFeedback(o, token);
      renderChanges(o, token);
      renderChat(o);
      renderAdd(o, token);
      renderApology(o);
      renderAlt(o);
      renderPlace(o);
      renderPush(o, token);
      renderFacts(o, token);
      return o;
    }

    //  CHK-20 (§۱۹.۲ بندِ ۸): دکانِ مسئول، ناحیه، بازهٔ برگزیده و «کپیِ نشانیِ پیگیری» — store/order-facts.js ِ دیربار
    var facts = null;
    function renderFacts(o, token) {
      facts = facts || K.js('/store/order-facts.js').then(function () { return window.StoreOrderFacts({ store: ctx.store }); })
        .catch(function () { facts = null; return null; });
      facts.then(function (f) { if (f && (want === null || want === token)) f.render(o, token); });
    }

    //  CHK-20/NTF-01: «خبرم کن روی همین دستگاه» (پوشِ وب) — store/order-push.js فقط وقتی پیگیری کلیدِ `push` را داد
    //  (پرچمِ خاموش یا سفارشِ پایان‌یافته ⇒ نه اسکریپت، نه واژه‌نامه، نه کارگرِ سرویس، نه گره)
    var push = null;
    function renderPush(o, token) {
      if (!o.push) { if ($('o-np')) $('o-np').hidden = true; return; }
      push = push || K.js('/store/order-push.js').then(function () { return window.StoreOrderPush({ api: api, slug: ctx.slug }); })
        .catch(function () { push = null; return null; });
      push.then(function (f) { if (f) f.render(o, token); });
    }

    //  ORD-13/PCK-05 (ممیزیِ ۱۴۰۵/۰۸/۱۷، مشکلِ ۱۰): سفارشِ ردشده/لغوشده به‌خاطرِ «ناموجود» ⇒ کالاهای مشابهِ فروختنیِ همین
    //  دکان (همان «مشابه» ِ برگهٔ کالا، `GET /products/:id`) به‌جای تنها «خریدِ دوباره» ِ همان کالای ناموجود. «+» ⇒ سبد.
    var altFor = '';
    function renderAlt(o) {
      var box = $('o-alt');
      var want = o.statusReason === 'out_of_stock' && K.orderKind(o.status) === 'err';
      if (!want) { if (box) box.hidden = true; return; }
      if (altFor === o.token) return;
      altFor = o.token;
      var had = {}; o.lines.forEach(function (l) { had[l.productId] = true; });
      Promise.all(o.lines.slice(0, 3).map(function (l) {
        return api('/products/' + encodeURIComponent(l.productId)).then(function (r) { return r.similar || []; }, function () { return []; });
      })).then(function (lists) {
        var seen = {}; var list = [];
        lists.forEach(function (xs) {
          xs.forEach(function (p) { if (!had[p.id] && !seen[p.id] && K.stockOf(p).orderable && list.length < 6) { seen[p.id] = true; list.push(p); } });
        });
        if (!list.length) return;
        if (!box) {
          box = el('div', { class: 'card card-lg', attrs: { id: 'o-alt' } }, el('h2', { text: t('ps.similar') }), el('ul', { class: 'rail products section', attrs: { id: 'o-alt-list' } }));
          $('o-subs').parentNode.insertBefore(box, $('o-subs').nextSibling);
        }
        var ul = K.clear($('o-alt-list'));
        list.forEach(function (p) {
          ul.appendChild(K.productCard(p, {
            href: '#/p/' + encodeURIComponent(p.id), qty: 0,
            photo: '/api/store/' + encodeURIComponent(ctx.slug) + '/products/' + encodeURIComponent(p.id) + '/thumb' + (p.photoVersion ? '?v=' + encodeURIComponent(p.photoVersion) : ''),
            onAdd: function (x) { ctx.reorder({ lines: [{ productId: x.id, qtyMilli: 1000 }] }); },
          }));
        });
        box.hidden = false;
      });
    }

    //  CHK-11 (ممیزی، مشکلِ ۶): سفارشِ «دریافت از دکان» ⇒ نشانی و «مسیر روی نقشه» ِ دکان (نبود ⇒ هیچ)
    function renderPlace(o) {
      var box = $('o-place');
      var node = o.fulfillment === 'pickup' && K.orderKind(o.status) !== 'err' && K.place(ctx.store);
      if (!node) { if (box) box.hidden = true; return; }
      if (!box) {
        box = el('div', { attrs: { id: 'o-place' } });
        $('o-meta').parentNode.insertBefore(box, $('o-meta').nextSibling);
      }
      K.clear(box).appendChild(node);
      box.hidden = false;
    }

    //  بخشِ پرچم‌دار فقط وقتی سرور کلیدش را داد ساخته می‌شود (پیش از «خریدِ دوباره») — خاموش ⇒ همان صفحهٔ امروز
    function part(id, build) {
      if (!$(id)) $('o-reorder').parentNode.insertBefore(build(), $('o-reorder'));
      return $(id);
    }

    //  CHK-21: تا پیش از جمع‌آوری، سبدِ همین دستگاه به همین سفارش (پرچمِ `shop.orders.add_items`؛ نبودِ `addItems` ⇒ هیچ).
    //  تصمیمِ آخر با سرور است (قیمتِ امروز، موجودی، وضعیت زیرِ قفل)؛ پس از جمع‌آوری پیامِ «در حال آماده‌سازی» (S38).
    function renderAdd(o, token) {
      var a = o.addItems;
      if (!a || (!a.allowed && !a.preparing)) { if ($('o-add')) $('o-add').hidden = true; return; }
      var box = part('o-add', function () {
        return el('div', { class: 'stack', attrs: { id: 'o-add' } }, el('div', { class: 'note', hidden: true, attrs: { id: 'o-add-note', role: 'status' } }),
          el('button', { class: 'btn btn-block', text: t('od.add'), attrs: { id: 'o-add-btn', type: 'button' } }));
      });
      var note = $('o-add-note'); var btn = $('o-add-btn');
      box.hidden = false;
      btn.hidden = !a.allowed;
      if (!a.allowed) { K.note(note, 'warn', t('err.order_preparing')); return; }
      note.hidden = true;
      btn.disabled = false;
      btn.onclick = function () {
        var cart = ctx.cart();
        var lines = Object.keys(cart).map(function (id) { return { productId: id, qtyMilli: cart[id].qtyMilli }; });
        if (!lines.length) { K.note(note, 'warn', t('od.add.empty')); return; }
        K.confirm(t('od.add.q', { n: K.fa(lines.length, 0) }), t('od.add.yes')).then(function (yes) {
          if (!yes) return;
          var scope = 'add:' + ctx.slug + ':' + token; var body = { lines: lines };
          btn.disabled = true;
          api('/orders/' + encodeURIComponent(token) + '/items', { method: 'POST', body: body, key: K.keyFor(scope, body) }).then(function (r) {
            K.dropKey(scope);
            ctx.emptyCart();
            renderOrder(r.order, token);
            K.note($('o-add-note'), 'ok', t('od.add.done'));
          }).catch(function (e) { btn.disabled = false; K.note(note, 'err', e.message); });
        });
      };
    }

    //  NTF-10: پوزشِ تأخیر و کدِ تخفیفِ یک‌بارمصرفِ همین شماره (پرچمِ `shop.orders.late_apology`؛ نبودِ `apology` ⇒ پنهان)
    function renderApology(o) {
      var a = o.apology; var c = a && a.coupon;
      if (!a) { if ($('o-apology')) $('o-apology').hidden = true; return; }
      var box = part('o-apology', function () { return el('div', { class: 'note', attrs: { id: 'o-apology', role: 'status' } }); });
      var v = c ? (c.kind === 'amount' ? K.money(c.amountMinor) : t('od.ap.pct', { n: K.fa(c.bp / 100, 0) })) : '';
      K.note(box, 'ok', t('od.ap') + (c && c.usable ? ' ' + t('od.ap.code', { code: c.code, v: v, d: K.when(c.expiresAt) }) : c && c.redeemed ? ' ' + t('od.ap.used') : ''));
    }

    //  NTF-07/09: «گزارشِ مشکل» با عکس و نظرِ سفارش — store/order-feedback.js فقط وقتی سرور کلیدش را داد
    //  (پرچم‌های خاموش ⇒ نه کلید، نه اسکریپت، نه واژه‌نامه، نه گره‌ای در صفحه)
    var feedback = null;
    function renderFeedback(o, token) {
      if (o.issueReports === undefined && o.review === undefined) { if ($('o-fb')) $('o-fb').hidden = true; return; }
      feedback = feedback || new Promise(function (ok, no) {
        document.head.appendChild(el('script', { attrs: { src: '/store/order-feedback.js' }, on: { load: ok, error: no } }));
      }).then(function () { return window.StoreFeedback({ api: api }); }).catch(function () { feedback = null; return null; });
      feedback.then(function (f) { if (f) f.render(o, token); });
    }

    //  ORD-14/NTF-08: پیشنهادِ ویرایشِ دکان (پذیرفتن/رد) و پیگیریِ بازپرداخت — store/order-changes.js فقط وقتی سرور
    //  کلیدِ پُرِ `amendment`/`refund` را داد (پرچم‌های خاموش یا تهی ⇒ نه اسکریپت، نه واژه‌نامه، نه گره‌ای در صفحه)
    var changes = null;
    function renderChanges(o, token) {
      if (!o.amendment && !o.refund) { if ($('o-ch')) $('o-ch').hidden = true; return; }
      changes = changes || new Promise(function (ok, no) {
        document.head.appendChild(el('script', { attrs: { src: '/store/order-changes.js' }, on: { load: ok, error: no } }));
      }).then(function () { return window.StoreChanges({ api: api, show: renderOrder }); }).catch(function () { changes = null; return null; });
      changes.then(function (f) { if (f) f.render(o, token); });
    }
    //  گفت‌وگو با دکان دربارهٔ همین سفارش (یک رشته برای هر سفارش، «بسته شد»/«مشکل دارم») — store/chat.js فقط وقتی
    //  پیگیری کلیدِ `chat` را داد (پرچمِ `shop.chat`؛ خاموش ⇒ نه اسکریپت، نه واژه‌نامه، نه گره)
    function renderChat(o) {
      if (!o.chat) { if ($('o-chat')) $('o-chat').hidden = true; return; }
      K.js('/store/chat.js').then(function () { window.StoreChat({ api: api, slug: ctx.slug }).entry(o); }, function () { /* بی گفت‌وگو */ });
    }
    //  S71/S72: روش و مسئولِ تحویل و «تغییرِ روش» — store/delivery-method.js فقط وقتی سرور `deliveryMethod` را داد
    var method = null;
    function renderMethod(o, token) {
      if (!o.deliveryMethod) { if ($('o-dm')) $('o-dm').hidden = true; return; }
      method = method || (window.StoreMethod ? Promise.resolve() : new Promise(function (ok, no) {
        document.head.appendChild(el('script', { attrs: { src: '/store/delivery-method.js' }, on: { load: ok, error: no } }));
      })).then(function () { return window.StoreMethod({ api: api, slug: ctx.slug }); }).catch(function () { method = null; return null; });
      method.then(function (m) {
        if (m) m.order(o, token, function () { api('/orders/' + encodeURIComponent(token)).then(function (r) { renderOrder(r.order, token); }); });
      });
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
      if (!o.deliveryRating || o.status !== 'delivered' || o.fulfillment !== 'delivery') { box.hidden = true; return; }
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
            ul.appendChild(el('li', {}, el('small', { class: 'muted', text: t(yes ? 'od.sub.ok' : 'od.sub.rm') })));
          }
        });
      }).catch(function () { box.hidden = true; });
    }

    function choose(o, token, id, decision) {
      api('/orders/' + encodeURIComponent(token) + '/substitutions/' + encodeURIComponent(id), { method: 'POST', body: { decision: decision } })
        .then(function () { renderSubs(o, token); })
        .catch(function (e) { K.note($('o-msg'), 'err', e.message); renderSubs(o, token); });
    }

    // ── CHK-24: ویرایشِ دستورِ تحویل تا پیش از ارسال — همزادِ `PrefsRules` ِ برنامهٔ خرید (core/.../shopper/ShopperPrefs.kt) ──
    //  کارت فقط با `o.deliveryPrefs` (پرچمِ `shop.orders.delivery_prefs`)، ارسال، و نه ردشده/لغوشده (`K.orderKind` = err).
    //  پیگیری هر ۲۰ ثانیه دوباره می‌کشد: کادرها فقط با نسخهٔ تازهٔ همان سفارش پُر می‌شوند تا نوشتهٔ مشتری پاک نشود.
    //  «با کدام اسکناس…» فقط با «نقد هنگامِ تحویل» (`checkTender`): `K.tenders` ِ جمعِ سفارش + اسکناسِ کنونی اگر هنوز درست است؛
    //  «پول خرد لازم نیست» ⇒ null ِ صریح؛ ⛔ هرگز مبلغی که سرور رد می‌کند. یک درخواست در هر لحظه؛ ۴xx (جز ۴۲۹) ⇒ پیگیری از نو و
    //  نمای تازه همان فرستاده ⇒ «ثبت شد» (`PrefsRules.landed`؛ با نشانه هرگز)، وگرنه پیامِ همان کد تا نسخهٔ بعد.
    var busy = false; var sent = null;
    function renderPrefs(o, token) {
      var p = o.deliveryPrefs; var f = $('o-prefs'); var msg = $('op-msg'); var sel = $('op-tn');
      f.hidden = !p || o.fulfillment !== 'delivery' || K.orderKind(o.status) === 'err';
      if (f.hidden) return;
      var v = token + ':' + p.version;
      var cash = o.paymentMethod === 'cod';
      var list = cash ? K.tenders(o.totalMinor, p.cashTenderMinor) : [];
      $('op-tn-row').hidden = !cash;
      if (f.getAttribute('data-v') !== v) {
        $('op-call').checked = p.callBeforeArrival; $('op-leave').value = p.leaveWith; $('op-lm').value = '';
        K.clear(sel).appendChild(el('option', { attrs: { value: '' }, text: t('od.tn.no') }));
        list.forEach(function (x) { sel.appendChild(el('option', { attrs: { value: String(x) }, text: t('od.tn.o', { m: K.money(x), c: K.money(x - o.totalMinor) }) })); });
        sel.value = list.indexOf(p.cashTenderMinor) >= 0 ? String(p.cashTenderMinor) : '';
        f.setAttribute('data-v', v);
      }
      if (sent && sent.token === token) {
        var s = sent; sent = null;
        if (!s.lm && p.callBeforeArrival === s.d.callBeforeArrival && p.leaveWith === s.d.leaveWith && (!cash || p.cashTenderMinor === s.d.cashTenderMinor)) {
          K.note(msg, 'ok', t('od.pf.ok'));
        }
        msg.setAttribute('data-ok', v);
      }
      ['op-call', 'op-leave', 'op-lm', 'op-tn'].forEach(function (id) { $(id).disabled = !p.editable; });
      $('op-save').disabled = !p.editable || busy;
      if (!p.editable) K.note(msg, 'info', t('err.delivery_locked'));
      else if (msg.getAttribute('data-ok') !== v) msg.hidden = true;
      f.onsubmit = function (ev) {
        ev.preventDefault();
        if (busy || !p.editable) return;
        var d = { callBeforeArrival: $('op-call').checked, leaveWith: $('op-leave').value.trim().slice(0, 80) };
        var x = Number(sel.value) || 0;
        if (cash) d.cashTenderMinor = list.indexOf(x) >= 0 ? x : null;
        //  پرداخت دیگر نقد نیست (حساب‌پی پس از ثبت) ⇒ اسکناسِ مانده را سرور با هر ذخیره رد می‌کرد (`bad_cash_tender`)
        else if (p.cashTenderMinor) d.cashTenderMinor = null;
        var body = { delivery: d, expectedVersion: p.version };
        var lm = $('op-lm').value.trim().slice(0, 200);
        if (lm) body.address = { landmark: lm };
        busy = true; sent = null; $('op-save').disabled = true; K.note(msg, '', '');
        api('/orders/' + encodeURIComponent(token) + '/delivery', { method: 'POST', body: body }).then(function (r) {
          busy = false;
          var np = r.order && r.order.deliveryPrefs;
          if (np) { msg.setAttribute('data-ok', token + ':' + np.version); renderPrefs(r.order, token); }
          K.note(msg, 'ok', t('od.pf.ok'));
        }).catch(function (e) {
          busy = false; $('op-save').disabled = false;
          var tn = /^(cash_tender_short|cash_tender_excess|bad_cash_tender)$/.test(e.code);
          //  اسکناسِ ردشده برگزیده نمی‌ماند: همان که سرور دارد (اگر هنوز درست است) یا «پول خرد لازم نیست»
          if (tn) sel.value = list.indexOf(p.cashTenderMinor) >= 0 ? String(p.cashTenderMinor) : '';
          K.note(msg, 'err', tn ? t('od.tn.x') : e.message);
          sent = { token: token, d: d, lm: lm };
          //  نسخهٔ کهنه، قفل، پرچمِ خاموش یا اسکناسِ ردشده ⇒ نمای امروز؛ شبکه/۴۲۹/۵xx ⇒ همین کادرها می‌مانند
          if (e.status >= 400 && e.status < 500 && e.status !== 429 && location.hash === '#/o/' + encodeURIComponent(token)) {
            api('/orders/' + encodeURIComponent(token)).then(function (r) { open(token, r.order); }, function () { /* همان کادرها */ });
          }
        });
      };
    }

    //  DLV-06 (S5): پیک برای مشتری فقط از نمای سرور (`delivery`، پشتِ پرچمِ shop.delivery_customer_view):
    //  نامِ کوچک، رسیدنِ تقریبی و کدِ تحویل — نه شماره، نه شناسه. بی کلید یا چیزی برای گفتن ⇒ پنهان.
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
      else if (st === 'pending') K.note($('o-paynote'), 'warn', t('od.pay.wait'));
      else if (st === 'failed' && can) K.note($('o-paynote'), 'warn', t('od.pay.fail'));
      else $('o-paynote').hidden = true;
      $('o-pay').onclick = function () { payOnline(token, true); };
      renderPayStep(o, token);
    }

    //  «پرداخت پس از پذیرش» (lib/order-flow): کارتِ «پرداخت» با «نقد هنگامِ تحویل/برداشت» — store/pay-step.js فقط وقتی
    //  پیگیری کلیدِ `paymentStep` را داد (تنظیمِ خاموشِ دکان ⇒ نه اسکریپت، نه واژه‌نامه، نه گره)؛ حساب‌پی همان `#o-pay`
    var payStep = null;
    function renderPayStep(o, token) {
      if (!o.paymentStep) { if ($('o-ps')) $('o-ps').hidden = true; return; }
      payStep = payStep || K.js('/store/pay-step.js').then(function () { return window.StorePayStep({ api: api, show: renderOrder }); })
        .catch(function () { payStep = null; return null; });
      payStep.then(function (f) { if (f) f.render(o, token); });
    }

    function payOnline(token, retry) {
      var scope = 'pay:' + ctx.slug + ':' + token;
      $('o-pay').disabled = true;
      K.note($('o-paynote'), 'ok', t('od.pay.go'));
      api('/orders/' + encodeURIComponent(token) + '/pay', { method: 'POST', body: {}, key: K.keyFor(scope, { token: token }) }).then(function (r) {
        var p = r.payment || {};
        //  تلاشِ پیشینِ همین کلید بسته شده (ناموفق/منقضی/لغو) ⇒ یک بار با کلیدِ تازه
        if (p.status !== 'pending' && p.status !== 'created') {
          K.dropKey(scope);
          if (retry) return payOnline(token, false);
          throw new Error(t('od.pay.fail'));
        }
        if (!/^https:\/\//.test(p.checkoutUrl || '')) throw new Error(t('err.generic'));
        location.assign(p.checkoutUrl);
      }).catch(function (e) {
        $('o-pay').disabled = false;
        K.note($('o-paynote'), 'err', e.message);
      });
    }

    // ── خواندن و پیگیریِ زنده؛ store.js نخستین پاسخ را هم‌زمان با همین پرونده می‌گیرد و `open` را می‌خواند ──
    //  پیگیریِ زنده فقط جلوی چشم و تا سفارش تمام نشده
    //  پرداختِ در انتظار (برگشت از حساب‌پی) ⇒ هر ۵ ثانیه تا سرور «پرداخت‌شده/ناموفق» بگوید
    var FINAL = ['rejected', 'auto_rejected', 'cancelled_by_customer', 'cancelled', 'delivered', 'picked_up', 'restocked', 'refunded_full', 'refunded_partial'];
    var poll = 0;
    //  سفارشِ همین نشانی (`want`) و سفارشی که صفحه نشان می‌دهد (`shown`): سفارشِ دیگر در همان سند (فقط `#/o/…` ِ دیگر) ⇒
    //  محتوا و کارهای سفارشِ پیشین (لغو، پرداخت، گفت‌وگو…) تا پاسخِ همین پنهان، و پاسخِ دیرِ سفارشِ پیشین (پیگیری، لغو…)
    //  نمی‌نشیند — پیش‌تر روی شبکهٔ کند کارتِ «گفت‌وگو» ِ سفارشِ پیشین زدنی ماند و عکس به رشتهٔ همان رفت
    var want = null; var shown = null;
    function body() { return $('view-order').querySelector('.split'); }
    function wait(token) {
      clearTimeout(poll); want = token;
      var stale = shown !== token; var w = $('o-wait');
      if (stale && !w) { w = el('p', { class: 'muted', attrs: { id: 'o-wait', role: 'status' }, text: t('loading') }); body().parentNode.insertBefore(w, body()); }
      body().hidden = stale;
      if (w) w.hidden = !stale;
    }
    function seen(token) { shown = token; body().hidden = false; if ($('o-wait')) $('o-wait').hidden = true; }
    function open(token, order) {
      if (want !== null && token !== want) return;
      want = token;
      clearTimeout(poll);
      var load = function () { api('/orders/' + encodeURIComponent(token)).then(function (r) { open(token, r.order); }).catch(function (e) { if (token === want) fail(e); }); };
      var o = renderOrder(order, token);
      //  CHK-01: پیشنهادِ ساختنِ حساب پس از ثبت (acct.js، فقط با `features.webAccount` و بی نشست)
      if (ctx.acct) ctx.acct().then(function (a) { a.offer(token); }, function () {});
      var pend = o.paymentStatus === 'pending';
      if (pend || FINAL.indexOf(o.status) < 0) poll = setTimeout(function () { if (!document.hidden && location.hash === '#/o/' + encodeURIComponent(token)) load(); else poll = setTimeout(load, 20000); }, pend ? 5000 : 20000);
    }
    function fail(e) {
      seen(null);
      $('o-title').textContent = t('od.title');
      K.clear($('o-steps')); K.clear($('o-lines')); K.clear($('o-totals')); K.clear($('o-timeline'));
      $('o-status').className = 'pill err';
      $('o-status').textContent = e.status === 404 ? t('od.nf') : t('od.err');
      $('o-meta').textContent = e.message;
      $('o-cancel').hidden = true; $('o-reorder').hidden = true; $('o-pay').hidden = true; $('o-payhint').hidden = true; $('o-paynote').hidden = true;
      $('o-dlv').hidden = true; ['o-add', 'o-apology', 'o-chat', 'o-np', 'o-facts', 'o-link'].forEach(function (id) { if ($(id)) $(id).hidden = true; });
    }

    return { open: open, fail: fail, wait: wait, stop: function () { clearTimeout(poll); } };
  };
})();
