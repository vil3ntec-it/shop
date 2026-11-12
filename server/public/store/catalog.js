/*
 * ویترین — بخش‌های دیربارِ کاتالوگ: ردیف‌های «پیشنهادِ همین دکان»، «ارزان‌تر از قیمتِ دکان» و «تازه در ویترین»
 * (WEB-02)، راه‌اندازیِ «دیده‌شده‌های اخیر» (WEB-13، `recent.js`)، دلخواه‌ها و فهرستِ خرید (WEB-11) و بنرها (WEB-24). پس از بارِ
 * نخست و هم‌زمان با کالاها بار می‌شود (`loadMore` در store.js) تا JS ِ بارِ اولِ ویترین زیرِ سقفِ ۷۵KB بماند (WEB-15،
 * §۳.۱۰)؛ کارت، دسته، جست‌وجو، برگهٔ کالا و سبد همان store.js است. نیامد (شبکه) ⇒ همان کاتالوگ، بی این بخش‌ها.
 *
 * WEB-11 فقط با `features.favorites` ِ سرور (پرچمِ `shop.storefront.favorites`؛ خاموش ⇒ هیچ‌کدام): ♥ روی کارت و در
 * برگهٔ کالا و ردیفِ «دلخواه‌ها»؛ «فهرستِ خرید» شناسه و مقدارِ سبد را نگه می‌دارد و «همه به سبد» همان «خریدِ دوباره»
 * ِ store.js است (قیمت و موجودیِ امروز). هر دو مثلِ سبدِ مهمان (S132) روی همین دستگاه‌اند؛ با نشستِ حساب (`K.sess()`)
 * ♥ با حساب هم‌گام است (`/account/favorites`): نخستین بارِ هر نشست ⇒ ♥ ِ این دستگاه به حساب می‌رود (ادغام)، سپس حساب حرفِ
 * آخر را می‌زند؛ ♥ ِ این دستگاه در صف (`store-favsync:<slug>`) و یکی‌یکی تا رسیدن (بی اینترنت/۴۲۹ ⇒ `online` یا بارِ بعد).
 * کالای نبوده (۴۰۴) و حسابِ پر (۴۰۹) ⇒ کنار؛ ۴۰۱ ⇒ همان محلی. «فهرستِ خرید» هم با نشست همراهِ حساب است
 * (`/account/shopping-list`، همان پرچم): نخستین بارِ هر نشست ادغام (بیشینهٔ مقدار؛ پر ⇒ فهرستِ این دستگاه جای فهرستِ حساب)،
 * سپس هر تغییر یک کارِ صف (`store-listsync:<slug>`: «همه» یا «برداشتنِ یکی») روی نسخهٔ دیده‌شده؛ ۴۰۹ ِ نسخه ⇒ همان کار روی
 * فهرستِ تازه؛ صف خالی ⇒ حساب حرفِ آخر را می‌زند. حسابِ پر از فهرست (`too_many_lists`) ⇒ همین دستگاه، محلی.
 * WEB-24 فقط با `features.banners` (پرچمِ `shop.storefront.banners`): بنرهای همین لحظه برای ناحیهٔ مشتری از
 * `GET /banners?zone=` (ساعت، ناحیه و کالای فروختنی را سرور می‌سنجد)، ردیفِ افقی بی چرخشِ خودکار؛ پیوندِ کالا ⇒ برگهٔ
 * کالا، پیوندِ دسته ⇒ همان دسته (فقط اگر دسته در ویترین هست)؛ ناحیه عوض شد ⇒ دوباره، با برگشت به کاتالوگ.
 * NTF-09 فقط با `features.reviews` (پرچمِ `shop.orders.reviews`): `store/reviews.js` ِ دیربار نظرهای تأییدشده را پیش از «اخیر» می‌نشاند.
 * متن‌ها در واژه‌نامهٔ جدای `kit/i18n/catalog.<زبان>.json`؛ نیامد ⇒ این بخش‌ها نیستند (کلیدِ خام هرگز).
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
   * ctx = { api, slug, store, cart, zone (getter‌ها)، rail, card, items, ids, reorder, category, recent, features } از store.js.
   * برمی‌گرداند { picks, seen, card, cart }: `picks()` ردیف‌ها را پر می‌کند و هرگز رد نمی‌شود؛ `seen(p)` کالای برگهٔ
   * باز؛ `card(li, p)` ♥ ِ کارت؛ `cart()` پس از هر تغییرِ سبد.
   */
  //  WEB-11 (ممیزیِ ۱۴۰۵/۰۸/۱۷، مشکلِ ۱): کیت ♥ نداشت و به‌جایش ⓘ کشیده می‌شد (همان نشانهٔ بازارچه)
  K.ICONS.heart = K.ICONS.heart || 'M12 20s-7.5-4.6-9-9.2C2 7.6 4.2 5 7 5c2 0 3.3 1.1 5 3 1.7-1.9 3-3 5-3 2.8 0 5 2.6 4 5.8-1.5 4.6-9 9.2-9 9.2z';

  window.StoreCatalog = function (ctx) {
    var api = ctx.api; var rail = ctx.rail;

    /**
     * سربرگِ دکان، دیربار (بودجهٔ بارِ نخست): WEB-01 (مشکلِ ۵) لوگو و تصویرِ سرِ خودِ دکان (`store.media`، پرچمِ
     * `shop.storefront.media`؛ نبود ⇒ همان حرفِ نخست)، WEB-02/CHK-11 (مشکلِ ۶) نشانی و «مسیر روی نقشه» (`K.place`،
     * kit/order-kit.js فقط وقتی دکان نشانی یا پین دارد). store.js پس از هر `renderHead` دوباره صدا می‌زند.
     */
    function head() {
      var s = ctx.store; var m = s.media || {};
      if (m.logo) {
        ['store-avatar', 'bar-avatar', 'nav-avatar'].forEach(function (id) {
          var a = $(id) && $(id).firstChild;
          if (!a || a.classList.contains('logo')) return;
          a.textContent = ''; a.removeAttribute('style'); a.classList.add('logo');
          a.appendChild(el('img', { attrs: { src: m.logo.url, alt: '' } }));
        });
      }
      if (m.header && !$('store-cover').firstChild) {
        $('store-cover').appendChild(el('img', { attrs: { src: m.header.url, alt: '', width: String(m.header.width), height: String(m.header.height) } }));
      }
      $('store-cover').hidden = !m.header;
      if (s.place) K.js('/kit/order-kit.js').then(function () { K.clear($('store-place')).appendChild(K.place(s)); }, function () { /* بی نشانی */ });
    }
    head();

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
    var KEY_SYNC = 'store-favsync:' + ctx.slug; var busy = false;
    var KEY_LSYNC = 'store-listsync:' + ctx.slug; var lbusy = false;

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
      var st = state();
      if (st) { st.ops = st.ops.filter(function (o) { return o[0] !== p.id; }).concat([[p.id, i < 0 ? 1 : 0]]); K.save(KEY_SYNC, st); flush(false); }
    }
    /** صفِ هم‌گامیِ همین نشست؛ نشستِ تازه ⇒ ادغام: همهٔ ♥ ِ این دستگاه، کهنه‌ترین اول تا ترتیبِ حساب همان بماند */
    function state() {
      var s = K.sess(); if (!s) return null;
      var st = K.load(KEY_SYNC, null);
      if (!st || st.t !== s.expiresAt || !Array.isArray(st.ops)) {
        st = { t: s.expiresAt, ops: favs.slice().reverse().map(function (id) { return [id, 1]; }) };
        K.save(KEY_SYNC, st);
      }
      return st;
    }
    /** صف یکی‌یکی؛ `pull` ⇒ پس از خالی شدن، فهرستِ حساب جای فهرستِ دستگاه */
    function flush(pull) {
      var st = state();
      if (!st || busy || navigator.onLine === false) return;
      var op = st.ops[0];
      if (!op) { if (pull) adopt(); return; }
      busy = true;
      //  صف از حافظه دوباره خوانده می‌شود: ♥ ِ زده‌شده در میانهٔ درخواست گم نمی‌شود
      var done = function () {
        busy = false;
        var now = state();
        if (now) { now.ops = now.ops.filter(function (o) { return !(o[0] === op[0] && o[1] === op[1]); }); K.save(KEY_SYNC, now); }
        flush(pull);
      };
      api('/account/favorites/' + encodeURIComponent(op[0]), { method: op[1] ? 'PUT' : 'DELETE' }).then(done, function (e) {
        if (e.status === 400 || e.status === 404 || e.status === 409) done(); else busy = false;
      });
    }
    function adopt() {
      busy = true;
      api('/account/favorites').then(function (r) {
        busy = false;
        var st = state();
        if (!st || st.ops.length) { flush(true); return; }
        r.items.forEach(function (p) { ctx.items[p.id] = ctx.items[p.id] || p; });
        favs = cleanFavs(r.items.map(function (p) { return p.id; }));
        K.save(KEY_FAV, favs);
        Array.prototype.forEach.call(document.querySelectorAll('.fav[data-fav]'), function (b) { var p = ctx.items[b.getAttribute('data-fav')]; if (p) paint(b, p); });
        paintSheet();
        drawFavs();
      }, function () { busy = false; });
    }
    /** کالایی که دیگر در ویترین نیست دیده نمی‌شود ولی می‌ماند (همان قاعدهٔ علاقه‌مندیِ حساب) */
    function drawFavs() {
      var ul = K.clear($('favs-list'));
      favs.forEach(function (id) { if (ctx.items[id]) ul.appendChild(ctx.card(ctx.items[id])); });
      $('favs').hidden = !ul.children.length;
    }
    function saveList() { K.save(KEY_LIST, list); drawList(); }

    // ── فهرستِ خرید با حساب: { t: نشست، v: نسخهٔ سرور، s: قلم‌های سرور، m: ادغام شد، ops } ──
    function lstate() {
      var s = K.sess(); if (!s) return null;
      var st = K.load(KEY_LSYNC, null);
      if (!st || st.t !== s.expiresAt || !Array.isArray(st.ops)) { st = { t: s.expiresAt, v: 0, s: [], m: false, ops: [] }; K.save(KEY_LSYNC, st); }
      return st.off ? null : st;
    }
    function wire(a) { return a.map(function (x) { return { productId: x.id, qtyMilli: x.q }; }); }
    function norm(lines) { return lines.map(function (l) { return { productId: l.productId, qtyMilli: l.qtyMilli }; }); }
    /** کارِ تازه؛ «همه» کارهای پیشین را بی‌اثر می‌کند */
    function lop(o) {
      var st = lstate(); if (!st) return;
      st.ops = o[0] === 'all' ? [o] : st.ops.concat([o]); K.save(KEY_LSYNC, st); lflush(false);
    }
    /** پاسخِ سرور؛ `op` انجام شد. صف خالی ⇒ فهرستِ حساب جای فهرستِ این دستگاه */
    function took(srv, op) {
      var st = lstate(); if (!st) return;
      st.v = srv.version; st.m = true; st.s = norm(srv.lines);
      var j = JSON.stringify(op);
      for (var i = 0; op && i < st.ops.length; i++) if (JSON.stringify(st.ops[i]) === j) { st.ops.splice(i, 1); break; }
      K.save(KEY_LSYNC, st);
      if (st.ops.length) return;
      srv.items.forEach(function (p) { ctx.items[p.id] = ctx.items[p.id] || p; });
      list = cleanList(st.s.map(function (l) { return { id: l.productId, q: l.qtyMilli }; }));
      saveList();
    }
    function lflush(pull, tries) {
      var st = lstate();
      if (!st || lbusy || navigator.onLine === false) return;
      var op = st.m ? st.ops[0] : null;
      if (st.m && !op && !pull) return;
      lbusy = true;
      var req = !st.m ? api('/account/shopping-list/merge', { method: 'POST', body: { items: wire(list) } })
        : op ? api('/account/shopping-list', { method: 'PUT', body: { version: st.v, items: op[0] === 'all' ? wire(op[1])
          : st.s.filter(function (l) { return l.productId !== op[1]; }) } })
          : api('/account/shopping-list');
      req.then(function (r) { lbusy = false; took(r.list, op); lflush(false); }, function (e) {
        lbusy = false;
        var cur = lstate(); var d = e.data && e.data.list;
        if (!cur) return;
        if (e.code === 'list_changed' && d && (tries || 0) < 3) { cur.v = d.version; cur.s = norm(d.lines); K.save(KEY_LSYNC, cur); lflush(pull, (tries || 0) + 1); return; }
        //  ادغام جا نشد ⇒ فهرستِ این دستگاه جای فهرستِ حساب (هیچ چیز بی‌صدا گم نمی‌شود)
        if (e.code === 'list_full' && d) { cur.v = d.version; cur.s = norm(d.lines); cur.m = true; cur.ops = [['all', list]].concat(cur.ops); K.save(KEY_LSYNC, cur); lflush(pull); return; }
        if (e.code === 'too_many_lists') { cur.off = true; K.save(KEY_LSYNC, cur); return; }
        //  کارِ ردشده کنار می‌رود؛ ادغام/خواندنِ ردشده (پرچم خاموش شد…) ⇒ همان محلی تا بارِ بعد. شبکه/۴۲۹/۴۰۱ ⇒ `online` یا بارِ بعد
        if (op && (e.status === 400 || e.status === 404 || e.status === 409)) {
          cur.ops = cur.ops.filter(function (o) { return JSON.stringify(o) !== JSON.stringify(op); });
          K.save(KEY_LSYNC, cur); lflush(pull);
        }
      });
    }
    function drawList() {
      var ul = K.clear($('list-lines'));
      list.forEach(function (x) {
        var p = ctx.items[x.id];
        if (!p) return;
        var rm = el('button', { class: 'btn btn-ghost btn-sm', attrs: { type: 'button', 'aria-label': t('list.remove', { name: p.name }) },
          on: { click: function () { list = list.filter(function (y) { return y.id !== x.id; }); saveList(); lop(['rm', x.id]); } } }, K.icon('close'));
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
        lop(['all', list]);
        K.toast(t('list.saved'));
      });
      $('list-add').addEventListener('click', function () {
        ctx.reorder({ lines: list.map(function (x) { return { productId: x.id, qtyMilli: x.q }; }) }, function (e) { K.toast(e.message); });
      });
      $('list-clear').addEventListener('click', function () { list = []; saveList(); lop(['all', []]); K.toast(t('list.cleared')); });
      drawList();
      fetchMissing().then(function () { drawFavs(); drawList(); });
      flush(true);
      lflush(true);
      window.addEventListener('online', function () { flush(true); lflush(true); });
    }

    // ── WEB-24: بنرها ──
    var bnZone = null;
    function banners() {
      var z = ctx.zone ? ctx.zone.id : '';
      if (bnZone === z) return;
      bnZone = z;
      api('/banners' + (z ? '?zone=' + encodeURIComponent(z) : '')).then(function (r) {
        if (bnZone !== z) return;
        var cats = ctx.store.categories || [];
        var ul = K.clear($('banners-list'));
        (r.banners || []).forEach(function (b) {
          var go = null;
          if (b.link.type === 'product') go = el('a', { class: 'btn btn-sm', text: t('bn.product'), attrs: { href: '#/p/' + encodeURIComponent(b.link.value) } });
          else if (b.link.type === 'category' && cats.indexOf(b.link.value) >= 0) {
            go = el('button', { class: 'btn btn-sm', text: t('bn.category'), attrs: { type: 'button' },
              on: { click: function () { ctx.category(b.link.value); $('cats').scrollIntoView({ block: 'start' }); } } });
          }
          ul.appendChild(el('li', { class: 'banner card' }, el('b', { text: b.title }), b.body ? el('p', { text: b.body }) : null, go));
        });
        $('banners').hidden = !ul.children.length;
      }).catch(function () { if (bnZone === z) bnZone = null; });
    }
    function startBanners() {
      $('banners').setAttribute('aria-label', t('bn.title'));
      banners();
      window.addEventListener('hashchange', function () { if (/^#?\/?$/.test(location.hash)) banners(); });
    }

    var f = ctx.features;
    if (f.favorites || f.banners) {
      K.part('catalog', $('saved')).then(function (ok) {
        if (ok && f.favorites) start();
        if (ok && f.banners) startBanners();
      });
    }
    //  WEB-07/10: فیلتر و «خبرم کن» (store/filters.js ِ دیربار) فقط با `features.filters`/`features.alerts`
    var finder = null;
    if (f.filters || f.alerts) {
      new Promise(function (ok, no) {
        document.head.appendChild(el('script', { attrs: { src: '/store/filters.js' }, on: { load: ok, error: no } }));
      }).then(function () {
        finder = window.StoreFinder({ api: ctx.api, features: f, filter: ctx.filter, slug: ctx.slug });
        if (sheetP) finder.seen(sheetP);
      }).catch(function () { /* بی filters.js (شبکه) همان کاتالوگ */ });
    }
    //  NTF-09: نظرهای تأییدشده — store/reviews.js فقط با `features.reviews` (پرچمِ خاموش ⇒ نه اسکریپت، نه واژه‌نامه، نه گره)
    if (f.reviews) {
      new Promise(function (ok, no) {
        document.head.appendChild(el('script', { attrs: { src: '/store/reviews.js' }, on: { load: ok, error: no } }));
      }).then(function () { return window.StoreReviews({ api: api, anchor: $('recent') }); })
        .catch(function () { /* بی reviews.js (شبکه) همان کاتالوگ، بی نظرها */ });
    }
    //  AI-11: «جست‌وجو با عکس» — store/visual-search.js فقط با `features.visualSearch` (پرچمِ خاموش ⇒ نه اسکریپت، نه درخواست، نه دکمه)
    if (f.visualSearch) {
      new Promise(function (ok, no) {
        document.head.appendChild(el('script', { attrs: { src: '/store/visual-search.js' }, on: { load: ok, error: no } }));
      }).then(function () { return window.StoreVisual({ base: '/api/store/' + encodeURIComponent(ctx.slug), anchor: $('search-form') }); })
        .catch(function () { /* بی visual-search.js (شبکه) همان جست‌وجوی متنی */ });
    }

    //  ABUSE-01: «گزارشِ مشکل» (store/trust.js ِ دیربار) فقط با `features.trust` (پرچمِ `market.trust`؛ خاموش ⇒ نه اسکریپت، نه گره)
    var trust = null;
    if (f.trust) {
      new Promise(function (ok, no) {
        document.head.appendChild(el('script', { attrs: { src: '/store/trust.js' }, on: { load: ok, error: no } }));
      }).then(function () { return window.StoreTrust({ api: api, slug: ctx.slug, into: $('view-catalog').firstElementChild }); })
        .then(function (x) { trust = x; if (trust && sheetP) trust.seen(sheetP); })
        .catch(function () { /* بی trust.js (شبکه) همان کاتالوگ */ });
    }

    return {
      head: head,
      picks: picks,
      seen: function (p) {
        if (recent) recent.then(function (r) { r.seen(p); }, function () { /* بی فهرستِ اخیر */ });
        sheetP = p;
        paintSheet();
        if (finder) finder.seen(p);
        if (trust) trust.seen(p);
      },
      card: function (li, p) { if (on) li.appendChild(favBtn(p)); },
      cart: function () { if (on) drawList(); },
    };
  };
})();
