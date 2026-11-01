/*
 * ویترین — فیلترِ کاتالوگ (WEB-07) و «خبرم کن وقتی موجود/ارزان شد» (WEB-10). دیربار: catalog.js فقط با
 * `features.filters` یا `features.alerts` ِ سرور (پرچم‌های خاموشِ `shop.storefront.filters`/`.alerts`) این را
 * بار می‌کند — خاموش ⇒ همان صفحه، بی این بخش‌ها و بی بایتی در بارِ نخست (WEB-15).
 * متن‌ها در واژه‌نامهٔ جدای `kit/i18n/filters.<زبان>.json`؛ نیامد ⇒ این بخش‌ها نیستند (کلیدِ خام هرگز).
 *
 * ⛔ گزینه‌ها فقط از `GET /filters` (برند، بازهٔ قیمت، مشخصه، «فقط موجود» فقط وقتی سرور موجودی را می‌داند)؛
 *    خودِ پالایه را سرور می‌زند (`GET /products?brand=…`) و صفحه‌بندی همان «کالاهای بیشتر» است.
 * ⛔ «خبرم کن» فقط با شمارهٔ تأییدشده (همان بلیتِ ثبتِ سفارش، `K.ticket()`)؛ بی آن فقط یک راهنما، بی دکمه.
 *    «موجود شد» فقط برای کالایی که نشانش «تمام شده» است؛ «ارزان شد» برای هر کالای قیمت‌دار. متن فقط textContent.
 */
(function () {
  'use strict';
  var $ = K.$; var el = K.el; var t = K.t;

  /** ctx = { api, features, filter(qs) } از catalog.js ⇒ { seen(p) } برای برگهٔ کالا. */
  window.StoreFinder = function (ctx) {
    var f = ctx.features || {};
    var ready = K.part('filters', $('filters'));
    var attrs = [];

    // ── WEB-07: فرمِ فیلتر ──
    function option(value, text) { return el('option', { text: text, attrs: { value: value } }); }
    function fill(x) {
      var brands = x.brands || [];
      attrs = [];
      (x.attributes || []).forEach(function (a) { a.values.forEach(function (v) { attrs.push({ name: a.name, value: v }); }); });
      var b = $('fl-brand'); var at = $('fl-attr');
      brands.forEach(function (n) { b.appendChild(option(n, n)); });
      attrs.forEach(function (a, i) { at.appendChild(option(String(i), a.name + ': ' + a.value)); });
      $('fl-brand-l').hidden = !brands.length;
      $('fl-attr-l').hidden = !attrs.length;
      $('fl-price').hidden = !x.price;
      if (x.price) {
        $('fl-min').placeholder = String(Math.floor(x.price.minMinor / 100));
        $('fl-max').placeholder = String(Math.ceil(x.price.maxMinor / 100));
      }
      $('fl-stock-l').hidden = !x.inStock;
      $('filters').hidden = !(brands.length || attrs.length || x.price || x.inStock);
    }
    function afn(id) {
      var v = String($(id).value || '').trim();
      if (!v) return null;
      var n = Number(v.replace(/[۰-۹]/g, function (d) { return String(d.charCodeAt(0) - 0x06F0); }));
      return Number.isSafeInteger(n) && n >= 0 && n <= 1000000000 ? n * 100 : NaN;
    }
    function apply(e) {
      if (e) e.preventDefault();
      var lo = afn('fl-min'); var hi = afn('fl-max');
      if (Number.isNaN(lo) || Number.isNaN(hi) || (lo !== null && hi !== null && lo > hi)) {
        K.note($('fl-msg'), 'err', t('fl.bad'));
        return;
      }
      $('fl-msg').hidden = true;
      var qs = '';
      var brand = $('fl-brand').value;
      if (brand) qs += '&brand=' + encodeURIComponent(brand);
      if (lo !== null) qs += '&minPriceMinor=' + lo;
      if (hi !== null) qs += '&maxPriceMinor=' + hi;
      if (!$('fl-stock-l').hidden && $('fl-stock').checked) qs += '&inStock=1';
      var a = attrs[Number($('fl-attr').value)];
      if ($('fl-attr').value !== '' && a) qs += '&attrName=' + encodeURIComponent(a.name) + '&attrValue=' + encodeURIComponent(a.value);
      $('fl-count').textContent = qs ? t('fl.on') : '';
      ctx.filter(qs);
    }
    if (f.filters) {
      ready.then(function (ok) {
        if (!ok) return null;
        return ctx.api('/filters').then(fill);
      }).then(function () {
        $('fl-form').addEventListener('submit', apply);
        $('fl-clear').addEventListener('click', function () { $('fl-form').reset(); apply(); });
      }).catch(function () { $('filters').hidden = true; });
    }

    // ── WEB-10: «خبرم کن» در برگهٔ کالا ──
    var mine = null; var cur = null;
    function load() {
      if (!K.ticket()) return Promise.resolve({});
      mine = mine || ctx.api('/alerts').then(function (r) {
        var m = {};
        r.items.forEach(function (a) { if (a.state === 'armed') m[a.productId + ':' + a.kind] = true; });
        return m;
      }, function (e) { mine = null; throw e; });
      return mine;
    }
    function toggle(p, kind, on) {
      var path = '/alerts/' + encodeURIComponent(p.id) + '/' + kind;
      return ctx.api(path, { method: on ? 'DELETE' : 'PUT' }).then(function () {
        mine = null;
        K.toast(t(on ? 'alr.off' : 'alr.done'));
      }, function (e) { K.toast(e.message); }).then(function () { if (cur && cur.id === p.id) paint(p); });
    }
    function button(p, kind, on) {
      return el('button', {
        class: 'btn btn-ghost btn-block', text: t('alr.' + kind + (on ? '.on' : '')),
        attrs: { type: 'button', 'aria-pressed': String(on) },
        on: { click: function (e) { e.currentTarget.disabled = true; toggle(p, kind, on); } },
      });
    }
    function paint(p) {
      var box = $('ps-alerts');
      if (!box) return;
      K.clear(box).hidden = true;
      if (!f.alerts || !p) return;
      ready.then(function (ok) {
        if (!ok) return null;
        if (!K.ticket()) {
          K.clear(box).appendChild(el('p', { class: 'muted small', text: t('alr.verify') }));
          box.hidden = false;
          return null;
        }
        return load().then(function (m) {
          if (!cur || cur.id !== p.id) return;
          var out = p.stock && (p.stock.status === 'out_of_stock' || p.stock.status === 'unavailable');
          K.clear(box);
          if (out) box.appendChild(button(p, 'stock', Boolean(m[p.id + ':stock'])));
          box.appendChild(button(p, 'price', Boolean(m[p.id + ':price'])));
          box.hidden = false;
        });
      }).catch(function () { box.hidden = true; });
    }

    return { seen: function (p) { cur = p; paint(p); } };
  };
})();
