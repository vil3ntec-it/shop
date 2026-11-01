/*
 * ویترین — بخش‌های دیربارِ کاتالوگ: ردیف‌های «پیشنهادِ همین دکان»، «ارزان‌تر از قیمتِ دکان» و «تازه در ویترین»
 * (WEB-02)، راه‌اندازیِ «دیده‌شده‌های اخیر» (WEB-13، `recent.js`) و دلخواه‌ها و فهرستِ خرید (WEB-11). پس از بارِ
 * نخست و هم‌زمان با کالاها بار می‌شود (`loadMore` در store.js) تا JS ِ بارِ اولِ ویترین زیرِ سقفِ ۷۵KB بماند (WEB-15،
 * §۳.۱۰)؛ کارت، دسته، جست‌وجو، برگهٔ کالا و سبد همان store.js است. نیامد (شبکه) ⇒ همان کاتالوگ، بی این بخش‌ها.
 *
 * WEB-11 فقط با `features.favorites` ِ سرور (پرچمِ `shop.storefront.favorites`؛ خاموش ⇒ هیچ‌کدام): ♥ روی کارت و در
 * برگهٔ کالا و ردیفِ «دلخواه‌ها»؛ «فهرستِ خرید» شناسه و مقدارِ سبد را نگه می‌دارد و «همه به سبد» همان «خریدِ دوباره»
 * ِ store.js است (قیمت و موجودیِ امروز). هر دو مثلِ سبدِ مهمان (S132) و دلخواه‌های بازارچه فقط روی همین دستگاه‌اند.
 * متن‌ها در واژه‌نامهٔ جدای `kit/i18n/catalog.<زبان>.json`؛ نیامد ⇒ این بخش نیست (کلیدِ خام هرگز).
 *
 * ⛔ فقط دادهٔ واقعیِ سرور؛ نبود ⇒ بخش پنهان. قیمت هرگز ذخیره نمی‌شود. متن فقط با textContent.
 */
(function () {
  'use strict';
  var $ = K.$; var el = K.el; var t = K.t;
  var ID = /^[A-Za-z0-9_.:-]{1,80}$/;
  var MAX_FAV = 200; // همان سقفِ علاقه‌مندی‌های حساب در سرور
  var MAX_LIST = 100;

  function cleanFavs(x) {
    return (Array.isArray(x) ? x : []).filter(function (id, i, a) { return typeof id === 'string' && ID.test(id) && a.indexOf(id) === i; }).slice(0, MAX_FAV);
  }
  function cleanList(x) {
    var seen = {};
    return (Array.isArray(x) ? x : []).filter(function (l) {
      if (!l || typeof l.id !== 'string' || !ID.test(l.id) || !Number.isSafeInteger(l.q) || l.q <= 0 || seen[l.id]) return false;
      seen[l.id] = true; return true;
    }).slice(0, MAX_LIST).map(function (l) { return { id: l.id, q: l.q }; });
  }

  /**
   * ctx = { api, slug, store, cart (getter‌ها)، rail, card, items, ids, setQty, reorder, recent, favorites } از store.js.
   * برمی‌گرداند { picks, seen, card, cart }: `picks()` ردیف‌ها را پر می‌کند و هرگز رد نمی‌شود؛ `seen(p)` کالای برگهٔ
   * باز؛ `card(li, p)` ♥ ِ کارت؛ `cart()` پس از هر تغییرِ سبد.
   */
  window.StoreCatalog = function (ctx) {
    var api = ctx.api; var rail = ctx.rail;

    //  WEB-13: فقط با کلیدِ `recent` ِ سرور؛ کالایی که پیش از رسیدنِ recent.js دیده شد در صف می‌ماند
    var recent = null;
    if (ctx.recent) {
      recent = new Promise(function (ok, no) {
        document.head.appendChild(el('script', { attrs: { src: '/store/recent.js' }, on: { load: ok, error: no } }));
      }).then(function () { return window.StoreRecent.start({ slug: ctx.slug, api: api, card: ctx.card, items: ctx.items }); });
      recent.catch(function () { recent = null; });
    }

    /** WEB-02: پرفروش (یا تازه)، «ارزان‌تر» و تازه‌ها — فقط دادهٔ واقعیِ سرور (#301)؛ نبود ⇒ هیچ */
    function picks() {
      //  سرورِ بی #301 این مسیر را ندارد؛ #301 همراهِ `store.delivery` می‌آید
      if (!ctx.store.delivery) return Promise.resolve();
      return api('/highlights').then(function (r) {
        var best = r.bestsellers || []; var fresh = r.newArrivals || [];
        rail('picks', best.length ? best : fresh);
        $('picks-why').textContent = best.length ? t('picks.best', { d: K.fa(r.windowDays || 30, 0) }) : t('picks.new');
        rail('deals', r.deals || []);
        rail('fresh', best.length ? fresh.filter(function (p) { return !best.some(function (b) { return b.id === p.id; }); }) : []);
      }).catch(function () { $('picks').hidden = true; $('deals').hidden = true; $('fresh').hidden = true; });
    }

    // ── WEB-11: دلخواه‌ها و فهرستِ خرید ──
    var KEY_FAV = 'store-favs:' + ctx.slug; var KEY_LIST = 'store-list:' + ctx.slug;
    var on = false; var favs = []; var list = []; var sheetP = null; var sb = null;

    function paint(b, p) {
      var yes = favs.indexOf(p.id) >= 0;
      b.setAttribute('aria-pressed', String(yes));
      b.setAttribute('aria-label', t(yes ? 'fav.remove' : 'fav.add', { name: p.name }));
    }
    function favBtn(p) {
      var b = el('button', { class: 'fav', attrs: { type: 'button', 'data-fav': p.id }, on: { click: function () { toggle(p); } } }, K.icon('heart'));
      paint(b, p);
      return b;
    }
    function paintSheet() {
      if (!sb || !sheetP) return;
      var yes = favs.indexOf(sheetP.id) >= 0;
      sb.setAttribute('aria-pressed', String(yes));
      K.clear(sb).append(K.icon('heart'), t(yes ? 'fav.sheet.on' : 'fav.sheet'));
      sb.hidden = false;
    }
    function toggle(p) {
      var i = favs.indexOf(p.id);
      if (i >= 0) favs.splice(i, 1); else favs = [p.id].concat(favs).slice(0, MAX_FAV);
      K.save(KEY_FAV, favs);
      Array.prototype.forEach.call(document.querySelectorAll('.fav[data-fav="' + CSS.escape(p.id) + '"]'), function (b) { paint(b, p); });
      paintSheet();
      drawFavs();
      K.toast(t(i < 0 ? 'fav.added' : 'fav.removed', { name: p.name }));
    }
    /** کالایی که دیگر در ویترین نیست دیده نمی‌شود ولی می‌ماند (همان قاعدهٔ علاقه‌مندیِ حساب) */
    function drawFavs() {
      var ul = K.clear($('favs-list'));
      favs.forEach(function (id) { if (ctx.items[id]) ul.appendChild(ctx.card(ctx.items[id])); });
      $('favs').hidden = !ul.children.length;
    }
    function saveList() { K.save(KEY_LIST, list); drawList(); }
    function drawList() {
      var ul = K.clear($('list-lines'));
      list.forEach(function (x) {
        var p = ctx.items[x.id];
        if (!p) return;
        var rm = el('button', { class: 'btn btn-ghost btn-sm', attrs: { type: 'button', 'aria-label': t('list.remove', { name: p.name }) },
          on: { click: function () { list = list.filter(function (y) { return y.id !== x.id; }); saveList(); } } }, K.icon('close'));
        ul.appendChild(el('li', {}, el('span', { class: 'grow', text: p.name }), el('span', { class: 'num', text: K.qty(x.q) + (p.unit ? ' ' + p.unit : '') }), rm));
      });
      var n = ul.children.length;
      $('list-n').textContent = n ? t('n.lines', { n: K.fa(n, 0) }) : '';
      $('list-add').hidden = !n;
      $('list-clear').hidden = !list.length;
      $('list-save').disabled = !ctx.ids().length;
      $('list').hidden = !n && !ctx.ids().length;
    }
    /** شناسه‌هایی که هنوز در حافظهٔ صفحه نیستند، هر بار حداکثر ۱۰۰ تا (سقفِ `/products?ids=`) */
    function fetchMissing() {
      var need = favs.concat(list.map(function (x) { return x.id; })).filter(function (id, i, a) { return !ctx.items[id] && a.indexOf(id) === i; });
      var parts = [];
      for (var i = 0; i < need.length; i += 100) parts.push(need.slice(i, i + 100));
      return Promise.all(parts.map(function (c) { return api('/products?ids=' + encodeURIComponent(c.join(','))); })).then(function (rs) {
        rs.forEach(function (r) { r.items.forEach(function (p) { ctx.items[p.id] = ctx.items[p.id] || p; }); });
      }, function () { /* همان آن‌چه در صفحه هست */ });
    }
    function start() {
      on = true;
      favs = cleanFavs(K.load(KEY_FAV, []));
      list = cleanList(K.load(KEY_LIST, []));
      Array.prototype.forEach.call(document.querySelectorAll('li.p-card[data-id]'), function (li) {
        var p = ctx.items[li.getAttribute('data-id')];
        if (p && !li.querySelector('.fav')) li.appendChild(favBtn(p));
      });
      sb = el('button', { class: 'btn btn-ghost btn-block', attrs: { id: 'ps-fav', type: 'button' }, on: { click: function () { if (sheetP) toggle(sheetP); } } });
      sb.hidden = true;
      $('ps-share').parentNode.insertBefore(sb, $('ps-share'));
      paintSheet();
      $('list-save').addEventListener('click', function () {
        list = cleanList(ctx.ids().map(function (id) { return { id: id, q: ctx.cart[id].qtyMilli }; }));
        saveList();
        K.toast(t('list.saved'));
      });
      $('list-add').addEventListener('click', function () {
        ctx.reorder({ lines: list.map(function (x) { return { productId: x.id, qtyMilli: x.q }; }) }, function (e) { K.toast(e.message); });
      });
      $('list-clear').addEventListener('click', function () { list = []; saveList(); K.toast(t('list.cleared')); });
      drawList();
      fetchMissing().then(function () { drawFavs(); drawList(); });
    }
    if (ctx.favorites) K.part('catalog', $('saved')).then(function (ok) { if (ok) start(); });

    return {
      picks: picks,
      seen: function (p) {
        if (recent) recent.then(function (r) { r.seen(p); }, function () { /* بی فهرستِ اخیر */ });
        sheetP = p;
        paintSheet();
      },
      card: function (li, p) { if (on) li.appendChild(favBtn(p)); },
      cart: function () { if (on) drawList(); },
    };
  };
})();
