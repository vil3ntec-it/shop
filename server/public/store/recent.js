/*
 * ویترین — «دیده‌شده‌های اخیر» (WEB-13). فقط وقتی `GET /api/store/<slug>` کلیدِ `recent`
 * دارد (پرچمِ `shop.storefront.recent`) و دیر بار می‌شود، تا بارِ نخستِ صفحه سبک بماند.
 *
 * ⛔ فهرست فقط روی همین دستگاه است (مثلِ سبدِ مهمان، S132) و به سرور نمی‌رود؛ قیمت و
 *    «هنوز در ویترین» هر بار از `/products?ids=` ِ همان ویترین تازه می‌شود.
 * قاعده‌ها همزادِ `core/product/RecentlyViewed` (Kotlin): تازه‌ترین اول، بی تکرار،
 * حداکثر ۱۲، کهنه‌تر از ۳۰ روز بیرون، ردیفِ بد کنار.
 */
(function () {
  'use strict';
  var MAX = 12;
  var TTL = 30 * 24 * 60 * 60 * 1000;
  var ID = /^[A-Za-z0-9_.:-]{1,80}$/;

  function fresh(list, now) {
    var seen = {};
    return (Array.isArray(list) ? list : [])
      .filter(function (x) {
        return x && typeof x.id === 'string' && ID.test(x.id) && Number.isFinite(x.at) && x.at > now - TTL && x.at <= now + TTL;
      })
      .sort(function (a, b) { return b.at - a.at || (a.id < b.id ? -1 : a.id > b.id ? 1 : 0); })
      .filter(function (x) { if (seen[x.id]) return false; seen[x.id] = true; return true; })
      .slice(0, MAX)
      .map(function (x) { return { id: x.id, at: x.at }; });
  }
  function add(list, id, now) {
    if (typeof id !== 'string' || !ID.test(id)) return fresh(list, now);
    return fresh([{ id: id, at: now }].concat((Array.isArray(list) ? list : []).filter(function (x) { return !x || x.id !== id; })), now);
  }

  var lib = { MAX: MAX, TTL: TTL, fresh: fresh, add: add };
  if (typeof module === 'object' && module.exports) { module.exports = lib; return; }

  /** o = { slug, api, card, items } از `store.js` */
  lib.start = function (o) {
    var $ = K.$;
    var KEY = 'store-recent:' + o.slug;
    var list = fresh(K.load(KEY, []), Date.now());
    function draw() {
      var ul = K.clear($('recent-list'));
      list.forEach(function (x) { if (o.items[x.id]) ul.appendChild(o.card(o.items[x.id])); });
      $('recent').hidden = !ul.children.length;
    }
    function load() {
      var need = list.map(function (x) { return x.id; }).filter(function (id) { return !o.items[id]; });
      if (!need.length) return draw();
      o.api('/products?ids=' + encodeURIComponent(need.join(','))).then(function (r) {
        r.items.forEach(function (p) { o.items[p.id] = o.items[p.id] || p; });
        //  کالایی که دیگر در ویترین نیست از فهرست هم می‌رود
        list = list.filter(function (x) { return o.items[x.id]; });
        K.save(KEY, list);
        draw();
      }).catch(function () { $('recent').hidden = true; });
    }
    $('recent-clear').addEventListener('click', function () { list = []; K.save(KEY, list); draw(); });
    load();
    return { seen: function (p) { list = add(list, p.id, Date.now()); K.save(KEY, list); draw(); } };
  };
  window.StoreRecent = lib;
})();
