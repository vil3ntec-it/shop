/*
 * ویترین — نظرهای تأییدشدهٔ خریداران (NTF-09) روی صفحهٔ دکان: ستاره و نوشته، ردیفِ افقی پیش از «دیده‌شده‌های اخیر».
 * فقط وقتی سرور `features.reviews` داده (پرچمِ `shop.orders.reviews`) و پس از بارِ نخست بار می‌شود (`catalog.js` ِ دیربار)؛
 * پرچمِ خاموش ⇒ نه این اسکریپت، نه واژه‌نامه‌اش (`kit/i18n/reviews.<زبان>.json`) و نه هیچ گره‌ای در صفحه.
 * داده فقط از `GET /api/store/:slug/reviews`: فقط تأییدشده‌ها، ستاره و نوشته؛ بی نام، شماره و شمارهٔ سفارش.
 *
 * ⛔ نوشتهٔ مشتری فقط با textContent (`K.el`) — هرگز HTML. هیچ نظری نیست، شبکه یا واژه‌نامه نیامد ⇒ بخشی ساخته نمی‌شود.
 */
(function () {
  'use strict';
  var $ = K.$; var el = K.el; var t = K.t;
  var MAX = 5;
  var LV = ['rv.lv1', 'rv.lv2', 'rv.lv3', 'rv.lv4', 'rv.lv5'];

  function stars(n) {
    var s = '';
    for (var i = 1; i <= MAX; i++) s += i <= n ? '★' : '☆';
    return s;
  }
  function clean(r) {
    return (r && Array.isArray(r.reviews) ? r.reviews : []).filter(function (x) {
      return x && Number.isSafeInteger(x.stars) && x.stars >= 1 && x.stars <= MAX && (x.comment === undefined || x.comment === null || typeof x.comment === 'string');
    });
  }

  function card(x) {
    var n = K.fa(x.stars, 0);
    return el('li', { class: 'banner card' },
      el('div', null,
        el('span', { class: 'rv-stars', text: stars(x.stars), attrs: { role: 'img', 'aria-label': t('rv.stars', { n: n }) } }),
        ' ', el('b', { text: t(LV[x.stars - 1]) })),
      x.comment ? el('p', { class: 'rv-text', text: x.comment }) : el('p', { class: 'rv-text', text: t('rv.only') }));
  }

  /** ctx = { api, anchor }: بخش پیش از `anchor` می‌نشیند. برمی‌گرداند Promise<boolean> (ساخته شد؟) — هرگز رد نمی‌شود. */
  window.StoreReviews = function (ctx) {
    return ctx.api('/reviews').then(function (r) {
      var list = clean(r);
      if (!list.length || !ctx.anchor || !ctx.anchor.parentNode) return false;
      var sec = el('section', { class: 'section', hidden: true, attrs: { id: 'rv', 'aria-labelledby': 'rv-title' } });
      ctx.anchor.parentNode.insertBefore(sec, ctx.anchor);
      return K.part('reviews', $('rv')).then(function (ok) {
        if (!ok) { sec.parentNode.removeChild(sec); return false; }
        var count = Number.isSafeInteger(r.count) && r.count > 0 ? r.count : list.length;
        var sum = [t('rv.count', { n: K.fa(count, 0) })];
        if (Number.isSafeInteger(r.avgTenths)) sum.unshift(t('rv.avg', { a: K.fa(r.avgTenths / 10, 1) }));
        var ul = el('ul', { class: 'banners', attrs: { id: 'rv-list', 'aria-label': t('rv.list') } });
        list.forEach(function (x) { ul.appendChild(card(x)); });
        sec.appendChild(el('div', { class: 'section-head' },
          el('h2', { text: t('rv.title'), attrs: { id: 'rv-title' } }),
          el('p', { text: sum.join(t('sep')), attrs: { id: 'rv-sum' } })));
        sec.appendChild(el('p', { class: 'small muted', text: t('rv.hint') }));
        sec.appendChild(ul);
        sec.hidden = false;
        return true;
      });
    }).catch(function () { return false; });
  };
})();
