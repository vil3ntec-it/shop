/*
 * ویترین — «همین کالا در دکان‌های دیگر» روی برگهٔ کالا (S70، §۱۹.۱۲؛ بازار M4).
 * فقط وقتی کالا `bankProductId` دارد (پرچمِ `market.product_details`) و از product.js ِ دیربار بار می‌شود؛ داده فقط
 * `GET /api/market/same/:bankProductId` (پرچمِ `market.directory` هم): خاموش، خطا یا هیچ دکانِ دیگری ⇒ هیچ بخشی.
 *
 * ⛔ سنجش را سرور می‌کند (`lib/market-units.js`): «هر دانه/کیلو/لیتر/متر» فقط با `perUnit` ِ سرور، «ارزان‌ترین» فقط
 *    با `cheapest` ِ سرور (همان پایه، دست‌کم دو پیشنهاد)؛ واحدِ بی اندازهٔ روشن ⇒ «سنجیده نمی‌شود»، هرگز حدس.
 * ⛔ متن فقط با textContent (`K.el`)؛ پاسخِ دیررس برگهٔ کالای دیگری را پر نمی‌کند.
 */
(function () {
  'use strict';
  var $ = K.$; var el = K.el; var t = K.t;
  var LIMIT = 8;
  var BASE = { piece: 'sm.b.piece', kg: 'sm.b.kg', l: 'sm.b.l', m: 'sm.b.m' };
  var seq = 0;

  function valid(o) {
    return o && typeof o.id === 'string' && o.shop && typeof o.shop.slug === 'string' && typeof o.shop.title === 'string'
      && Number.isSafeInteger(o.priceMinor);
  }
  function perText(p) {
    return p && BASE[p.base] && Number.isSafeInteger(p.priceMinor)
      ? t('sm.per', { b: t(BASE[p.base]), p: K.money(p.priceMinor) }) : t('sm.unclear');
  }
  function row(o, here) {
    var name = here ? el('b', { text: t('sm.here') })
      : el('a', { text: o.shop.title, attrs: { href: '/store/' + encodeURIComponent(o.shop.slug) + '#/p/' + encodeURIComponent(o.id) } });
    return el('li', null,
      el('div', { class: 'grow' }, name, el('div', { class: 'small muted', text: perText(o.perUnit) })),
      o.cheapest === true ? el('span', { class: 'pill ok', text: t('sm.cheapest') }) : '',
      el('span', { class: 'num', text: K.money(o.priceMinor) + (o.unit ? ' / ' + o.unit : '') }));
  }
  function section() {
    var sec = $('ps-same');
    if (sec) return sec;
    var anchor = $('ps-sim');
    if (!anchor || !anchor.parentNode) return null;
    sec = el('section', { class: 'section', hidden: true, attrs: { id: 'ps-same', 'aria-labelledby': 'ps-same-t' } });
    anchor.parentNode.insertBefore(sec, anchor);
    return sec;
  }

  /** ctx = { slug } (دکانِ همین ویترین). برمی‌گرداند `show(product)`؛ `product` تهی یا بی `bankProductId` ⇒ بخش پنهان. */
  window.StoreSame = function (ctx) {
    return function show(p) {
      var my = ++seq;
      var sec = section();
      if (!sec) return;
      sec.hidden = true;
      K.clear(sec);
      if (!p || typeof p.bankProductId !== 'string' || !p.bankProductId) return;
      K.api('/api/market/same/' + encodeURIComponent(p.bankProductId) + '?limit=' + LIMIT).then(function (r) {
        var offers = (r && Array.isArray(r.offers) ? r.offers : []).filter(valid);
        var isHere = function (o) { return o.shop.slug === ctx.slug && o.id === p.id; };
        if (my !== seq || !offers.some(function (o) { return !isHere(o); })) return null;
        return K.part('same', $('ps-same')).then(function (ok) {
          if (!ok || my !== seq) return;
          var ul = el('ul', { class: 'list-lines', attrs: { id: 'ps-same-list', 'aria-label': t('sm.list') } });
          offers.forEach(function (o) { ul.appendChild(row(o, isHere(o))); });
          sec.appendChild(el('h3', { class: 'ps-h', text: t('sm.title'), attrs: { id: 'ps-same-t' } }));
          sec.appendChild(el('p', { class: 'small muted', text: t('sm.hint') }));
          (Array.isArray(r.compare) ? r.compare : []).forEach(function (g) {
            if (!g || !BASE[g.base] || !Number.isSafeInteger(g.lowestMinor) || !Number.isSafeInteger(g.count)) return;
            sec.appendChild(el('p', { class: 'small', text: t('sm.low', { b: t(BASE[g.base]), n: K.fa(g.count, 0), p: K.money(g.lowestMinor) }) }));
          });
          sec.appendChild(ul);
          sec.hidden = false;
        });
      }).catch(function () { /* بازارچه یا جزئیات خاموش ⇒ بی بخش */ });
    };
  };
})();
