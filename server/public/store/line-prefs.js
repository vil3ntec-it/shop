/*
 * ویترین — یادداشتِ هر قلم (CHK-16) و «اگر نبود…» (CHK-15) در برگهٔ ثبت، و همان دو زیرِ قلم در صفحهٔ پیگیری.
 * دیربار: checkout.js با هر بازشدنِ برگهٔ ثبت، order.js فقط وقتی سفارش یادداشت یا ترجیح دارد. واژه‌نامه
 * `kit/i18n/lines.<زبان>.json`؛ نیامد ⇒ هیچ گره‌ای (همان برگهٔ پیشین) و بارِ بعد دوباره.
 *
 *   یادداشت ── همیشه روشن (پول را عوض نمی‌کند): «+ یادداشت» زیرِ هر قلم، ۲۰۰ نویسه ⇒ `lines[].note`؛ همراهِ
 *              قلمِ سبد روی همین دستگاه می‌ماند.
 *   جایگزین ── فقط با `features.substitution` (پرچمِ `shop.orders.substitution`): یک گزینش برای کلِ سفارش، و
 *              گزینشِ قلم **فقط برای قلمِ کم‌موجود** (§۶.۱۵ CHK-15): سقفِ ۴۰۹ ِ `out_of_stock`، یا موجودیِ اعلام‌شدهٔ
 *              دکان (`stock.sellableMilli` ِ `/products?ids=`، فقط با «نمایشِ عدد») کمتر از دو برابرِ مقدارِ سبد.
 *              «کالای مشخص» فقط کالای فروختنیِ همین ویترین (جست‌وجو)؛ نامش را سرور در ثبت می‌خواند و نبودش ⇒
 *              ۴۰۹ `substitute_unavailable` ⇒ گزینش پاک و پیامِ روشن.
 * ⛔ متن فقط با textContent (`K.el`)؛ گزینشِ قلمی که دیگر کم‌موجود نیست (پنهان) فرستاده نمی‌شود.
 */
(function () {
  'use strict';
  var $ = K.$; var el = K.el; var t = K.t;
  var LINE = ['', 'best_match', 'specific', 'none', 'refund'];
  var ORDER = ['', 'best_match', 'none', 'refund'];
  var NAME = { best_match: 'lp.s.best', specific: 'lp.s.specific', none: 'lp.s.none', refund: 'lp.s.refund' };

  function select(values, blank, value, label) {
    var s = el('select', { attrs: label ? { 'aria-label': label } : {} });
    values.forEach(function (v) { s.appendChild(el('option', { text: v ? t(NAME[v]) : t(blank), attrs: { value: v } })); });
    s.value = values.indexOf(value) >= 0 ? value : '';
    return s;
  }

  /** پیگیری: یادداشت و ترجیحِ هر قلم زیرِ نامِ همان قلم؛ ترجیحِ کلی زیرِ فهرست (`#o-sub`) */
  function receipt(ul, o) {
    var items = ul.children;
    if (items.length !== o.lines.length) return;
    o.lines.forEach(function (l, i) {
      var g = items[i].querySelector('.grow');
      var old = items[i].querySelector('.lp-r');
      if (old) old.parentNode.removeChild(old);   //  نقاشیِ دوباره‌ای که پیش از رسیدنِ این پرونده آمد ⇒ یک بار
      var bits = [];
      if (l.note) bits.push(t('lp.r.note', { note: l.note }));
      //  سرور قلمِ بی ترجیح را با ترجیحِ کلی پر می‌کند ⇒ فقط ترجیحِ جدا از کلی زیرِ قلم
      if (l.substitution && l.substitution !== o.substitution && NAME[l.substitution]) {
        bits.push(t('lp.r.sub', { s: l.substitution === 'specific' && l.substituteName ? t('lp.chosen', { name: l.substituteName }) : t(NAME[l.substitution]) }));
      }
      if (g && bits.length) g.appendChild(el('small', { class: 'lp-r', text: bits.join(t('sep')) }));
    });
    var p = $('o-sub');
    if (!p) { p = el('p', { class: 'muted small', attrs: { id: 'o-sub' } }); ul.parentNode.insertBefore(p, ul.nextSibling); }
    p.hidden = !NAME[o.substitution];
    p.textContent = NAME[o.substitution] ? t('lp.sub.order') + ' ' + t(NAME[o.substitution]) : '';
  }

  function make(ctx) {
    var c = ctx.c; var api = ctx.api;
    var boxes = {};     // شناسهٔ کالا ⇒ { line, node, paint } — گره‌ها با هر نقاشیِ دوبارهٔ سبد همان می‌مانند
    var stock = {};     // شناسه ⇒ نشانِ موجودیِ `/products?ids=` (فقط با جایگزینی)
    var orderSub = '';
    function subOn() { return Boolean(c && c.store.features && c.store.features.substitution); }
    function low(id) {
      var l = c.cart[id]; var s = stock[id];
      if (!l || !subOn()) return false;
      return Boolean(c.short(id) || (s && typeof s.sellableMilli === 'number' && s.sellableMilli < 2 * l.qtyMilli));
    }

    function noteRow(l) {
      var add = el('button', { class: 'btn btn-ghost btn-sm', attrs: { type: 'button' }, text: t('lp.note.add') });
      var input = el('input', { class: 'input', attrs: { type: 'text', maxlength: '200', placeholder: t('lp.note.ph'), 'aria-label': t('lp.note.l', { name: l.name }) } });
      input.value = l.note || '';
      add.hidden = Boolean(l.note); input.hidden = !l.note;
      add.addEventListener('click', function () { add.hidden = true; input.hidden = false; input.focus(); });
      input.addEventListener('input', function () { l.note = input.value.slice(0, 200); });
      input.addEventListener('change', function () {
        l.note = input.value.trim().slice(0, 200);
        if (!l.note) { input.hidden = true; add.hidden = false; }
        c.saveCart();
      });
      return el('div', { class: 'lp-g' }, add, input);
    }

    function subRow(id, l) {
      var sel = select(LINE, 'lp.s.same', l.sub, t('lp.sub.line', { name: l.name }));
      var chosen = el('p', { class: 'small' });
      var find = el('input', { class: 'input', attrs: { type: 'search', maxlength: '60', placeholder: t('lp.find.ph'), 'aria-label': t('lp.find.ph') } });
      var hits = el('div', { class: 'row lp-hits' });
      var spec = el('div', { class: 'lp-g' }, chosen, find, hits);
      var timer = 0; var asked = '';
      function paint() {
        spec.hidden = sel.value !== 'specific';
        chosen.textContent = l.subId ? t('lp.chosen', { name: l.subName }) : t('lp.pick');
      }
      sel.addEventListener('change', function () {
        l.sub = LINE.indexOf(sel.value) >= 0 ? sel.value : '';
        if (l.sub !== 'specific') { l.subId = ''; l.subName = ''; }
        paint(); c.saveCart();
      });
      function search() {
        var q = find.value.trim();
        asked = q;
        if (q.length < 2) { K.clear(hits); return; }
        api('/products?q=' + encodeURIComponent(q) + '&limit=8').then(function (r) {
          if (asked !== q) return;
          K.clear(hits);
          var list = (r.items || []).filter(function (p) { return p.id !== id && p.priceMinor !== null && K.stockOf(p).orderable; }).slice(0, 5);
          if (!list.length) hits.appendChild(el('span', { class: 'muted small', text: t('lp.find.none') }));
          list.forEach(function (p) {
            hits.appendChild(el('button', { class: 'btn btn-soft btn-sm', attrs: { type: 'button' }, text: p.name + ' — ' + K.money(p.priceMinor),
              on: { click: function () { l.subId = p.id; l.subName = p.name; find.value = ''; K.clear(hits); paint(); c.saveCart(); } } }));
          });
        }, function () { if (asked === q) { K.clear(hits); hits.appendChild(el('span', { class: 'muted small', text: t('err.network') })); } });
      }
      find.addEventListener('input', function () { clearTimeout(timer); timer = setTimeout(search, 300); });
      paint();
      return el('div', { class: 'lp-g' }, el('label', { class: 'field' }, el('span', { text: t('lp.sub.l') }), sel), spec);
    }

    function build(id, l) {
      var sub = null;
      var node = el('div', { class: 'lp' }, noteRow(l));
      return {
        line: l, node: node,
        paint: function () {
          var show = low(id);
          if (show && !sub) node.appendChild(sub = subRow(id, l));
          if (sub) sub.hidden = !show;
        },
      };
    }

    /** گزینشِ کلی زیرِ «یادداشت برای دکان» (فقط با جایگزینی) */
    function orderBox() {
      var box = $('ck-sub');
      if (!box && subOn() && $('c-note')) {
        var sel = select(ORDER, 'lp.s.shop', orderSub);
        sel.addEventListener('change', function () { orderSub = ORDER.indexOf(sel.value) >= 0 ? sel.value : ''; });
        box = el('label', { class: 'field', attrs: { id: 'ck-sub' } }, el('span', { text: t('lp.sub.order') }), sel);
        var note = $('c-note').parentNode;
        note.parentNode.insertBefore(box, note.nextSibling);
      }
      if (box) box.hidden = !subOn();
    }

    return {
      receipt: receipt,
      /** هر بازشدنِ برگهٔ ثبت: گزینشِ کلی، و موجودیِ امروزِ قلم‌ها برای «کم‌موجود» */
      open: function () {
        orderBox();
        var list = c ? Object.keys(c.cart) : [];
        if (!subOn() || !list.length) return;
        api('/products?ids=' + list.map(encodeURIComponent).join(',')).then(function (r) {
          stock = {};
          (r.items || []).forEach(function (p) { if (p.stock) stock[p.id] = p.stock; });
          ctx.render();
        }, function () { /* موجودی نیامد ⇒ فقط گزینشِ کلی */ });
      },
      /** پیش از نقاشیِ دوبارهٔ سبد: کادرِ در حالِ نوشتن پس از نقاشی همان‌جا فوکوس می‌گیرد */
      hold: function () {
        var a = document.activeElement; var keep = null;
        Object.keys(boxes).forEach(function (id) {
          if (!c.cart[id]) delete boxes[id];
          else if (boxes[id].node.contains(a)) keep = a;
        });
        if (!keep) return null;
        var s = null; var e = null;
        try { s = keep.selectionStart; e = keep.selectionEnd; } catch (x) { s = null; }
        return function () {
          if (!document.body.contains(keep)) return;
          keep.focus({ preventScroll: true });
          try { if (typeof s === 'number') keep.setSelectionRange(s, e); } catch (x) { /* کادرِ بی گزینش */ }
        };
      },
      decorate: function (li, id) {
        var l = c.cart[id];
        if (!l || l.unavailable) return;
        var b = boxes[id];
        if (!b || b.line !== l) b = boxes[id] = build(id, l);
        b.paint();
        li.classList.add('lp-li');
        li.appendChild(b.node);
      },
      /** کلیدهای قلمِ بدنهٔ ثبت */
      line: function (id, x) {
        var l = c.cart[id];
        var n = l ? String(l.note || '').trim().slice(0, 200) : '';
        if (n) x.note = n;
        if (l && low(id) && LINE.indexOf(l.sub) > 0 && (l.sub !== 'specific' || l.subId)) {
          x.substitution = l.sub;
          if (l.sub === 'specific') x.substituteProductId = l.subId;
        }
        return x;
      },
      order: function (body) { if (subOn()) body.substitution = orderSub; },
      /** «کالای مشخص» بی کالا ⇒ پیامِ روشن پیش از ثبت */
      missing: function () {
        var bad = Object.keys(c.cart).filter(function (id) { var l = c.cart[id]; return low(id) && l.sub === 'specific' && !l.subId; })[0];
        return bad ? t('lp.pick.need', { name: c.cart[bad].name }) : '';
      },
      /** ۴۰۹ `substitute_unavailable`: جایگزینِ همان قلم پاک ⇒ دوباره برگزیند */
      drop: function (productId) {
        var l = c.cart[productId];
        if (l) { l.subId = ''; l.subName = ''; delete boxes[productId]; c.saveCart(); }
      },
    };
  }

  /** `ctx`: `{ api, c, render }` از checkout.js، یا `{}` از order.js (فقط `receipt`) */
  window.StoreLines = function (ctx) {
    return K.part('lines', $('cart-lines')).then(function (ok) {
      if (!ok) throw new Error('lines');
      return make(ctx || {});
    });
  };
})();
