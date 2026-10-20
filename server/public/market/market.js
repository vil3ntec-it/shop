/*
 * بازارچه (VILL3N) — خانه «اول کالا» (پرفروش و ارزان‌ترِ واقعی، کالاهای دکان‌ها با
 * «افزودن»، دکان‌های پیشنهادی)، همهٔ دکان‌ها، «سفارش‌های من» و سبدهای همین دستگاه.
 * داده فقط از `/api/market/*` (پرچمِ `market.directory`)؛ ثبت و پیگیری در صفحهٔ هر
 * دکان (`/store/<slug>`). بی innerHTML برای داده؛ CSP ِ سخت.
 * ⛔ هیچ عدد/امکانِ ساختگی: نه امتیاز، نه زمانِ تحویلِ ساختگی، نه «موجود» ِ بی‌شاهد.
 */
(function () {
  'use strict';
  var $ = K.$; var el = K.el; var t = K.t;
  var API = '/api/market';
  var state = { overview: null, category: '', near: null, offset: 0, q: '', verifyAvailable: true, view: '' };
  var searchTimer = 0; var searchSeq = 0; var feedSeq = 0;

  //  نشانه‌های دسته‌ها (فقط بازارچه؛ کیت سبک می‌ماند)
  var I = K.ICONS;
  I.cart = 'M3 4h2l2.4 11h10.2L20 7.5H6.2 M9.5 20a1 1 0 1 0 0-2 1 1 0 0 0 0 2z M17 20a1 1 0 1 0 0-2 1 1 0 0 0 0 2z';
  I.bread = 'M5 11a3.5 3.5 0 0 1 0-7h14a3.5 3.5 0 0 1 0 7v9H5z M9.5 8v3 M14.5 8v3';
  I.apple = 'M12 7.5c-2-1.5-6.5-1-7 3.5-.4 3.6 1.6 8.5 4.5 9 1 .2 1.7-.4 2.5-.4s1.5.6 2.5.4c2.9-.5 4.9-5.4 4.5-9-.5-4.5-5-5-7-3.5z M12 7.5c0-2 1-3.5 3-4.5';
  I.meat = 'M13.5 4.5a6 6 0 1 1 6 6c-2 0-3 1-4.5 2.5L10 18l-4-4 5-5c1.5-1.5 2.5-2.5 2.5-4.5z M7.5 15.5l-3.5 3.5';
  I.milk = 'M8.5 3h7 M9.5 3v3l-2.5 3.5V21h10V9.5L14.5 6V3 M7 13h10';
  I.candy = 'M10 3a6 6 0 1 0 0 12 6 6 0 0 0 0-12z M10 9a2 2 0 1 1 2 2 M14.5 13.5L20 21';
  I.spark = 'M12 3l1.8 5.2L19 10l-5.2 1.8L12 17l-1.8-5.2L5 10l5.2-1.8z M19 16l.8 2.2 2.2.8-2.2.8L19 22l-.8-2.2L16 19l2.2-.8z';
  I.sofa = 'M4 11V8a2 2 0 0 1 2-2h12a2 2 0 0 1 2 2v3 M3 12a1.5 1.5 0 0 1 3 0v2h12v-2a1.5 1.5 0 0 1 3 0v6H3z M5 18v2 M19 18v2';
  I.pencil = 'M4 20l1-5L16 4l4 4L9 19z M14 6l4 4';
  I.shirt = 'M8 3L3 6l2 4 2-1v12h10V9l2 1 2-4-5-3c-.5 1.5-2 2.5-4 2.5S8.5 4.5 8 3z';
  I.device = 'M7 2.5h10v19H7z M11 18.5h2';
  /** نشانه و رنگِ هر دسته (رنگ‌های کمکیِ سامانه؛ قرمز فقط برای هشدارِ واقعی) */
  var CAT = {
    '': ['grid', 5], grocery: ['cart', 2], bakery: ['bread', 3], produce: ['apple', 1], butcher: ['meat', 4], dairy: ['milk', 2],
    sweets: ['candy', 3], beauty: ['spark', 4], household: ['sofa', 5], stationery: ['pencil', 2],
    clothing: ['shirt', 3], electronics: ['device', 2], other: ['grid', 5],
  };

  var VIEWS = ['view-home', 'view-shops', 'view-orders', 'view-off'];
  function show(id) {
    VIEWS.forEach(function (v) { $(v).hidden = v !== id; });
    $('boot').hidden = true;
    state.view = id;
    K.nav({ 'view-home': 'home', 'view-shops': 'shops', 'view-orders': 'orders' }[id] || '');
    renderCarts();
  }

  // ── کارتِ دکان: جلد از عکسِ یکی از کالاهای خودش، وگرنه حرفِ اول با رنگِ دکان ──
  function shopCard(s) {
    var meta = el('div', { class: 'meta' });
    meta.appendChild(el('span', {}, K.icon('bag'), t('n.items', { n: K.fa(s.productCount, 0) })));
    if (s.fulfillment.delivery) meta.appendChild(el('span', {}, K.icon('truck'), s.deliveryFeeMinor > 0 ? t('ship.fee', { fee: K.money(s.deliveryFeeMinor) }) : t('ship.free')));
    if (s.fulfillment.pickup) meta.appendChild(el('span', {}, K.icon('box'), t('pickup')));
    if (s.minOrderMinor > 0) meta.appendChild(el('span', { text: t('min.order', { m: K.money(s.minOrderMinor) }) }));
    if (s.distanceKm !== null && s.distanceKm !== undefined) meta.appendChild(el('span', {}, K.icon('pin'), t('dist', { n: K.fa(s.distanceKm, 1) })));
    var hours = K.hours(s.status);
    if (hours) meta.appendChild(el('span', {}, K.icon('clock'), hours));
    var cover = el('span', { class: 'cover' + (s.cover ? ' tile' : '') });
    if (s.cover) cover.appendChild(el('img', { attrs: { src: K.photo(s.slug, s.cover), alt: '', loading: 'lazy', decoding: 'async', width: '76', height: '76' } }));
    else cover.appendChild(K.avatar(s.title, s.accent, s.slug));
    var pills = el('div', { class: 'pills' }, K.pill(s.status));
    if (s.category) pills.appendChild(el('span', { class: 'pill plain', text: t('cat.' + s.category) }));
    var sub = s.tagline || s.area || '';
    var closed = s.status && (s.status.badge === 'closed' || s.status.badge === 'offline');
    var a = el('a', { class: 'card shop-card' + (closed ? ' closed' : ''), attrs: { href: '/store/' + encodeURIComponent(s.slug), 'data-slug': s.slug } },
      el('span', { class: 'top' }, cover, el('span', { class: 'grow' }, el('h3', { text: s.title }), pills, sub ? el('p', { class: 'sub', text: sub }) : null)),
      meta,
      el('span', { class: 'go' }, t('shop.go'), K.icon('chev')));
    return el('li', {}, a);
  }

  function skeleton(ul, n, tall) {
    K.clear(ul);
    for (var i = 0; i < n; i++) ul.appendChild(el('li', { class: 'skel' + (tall ? ' tall' : ''), attrs: { 'aria-hidden': 'true' } }));
  }

  // ── کارتِ کالا با «افزودن» به سبدِ همان دکان (روی همین دستگاه) ──
  function cartOf(slug) { return K.load(K.cartKey(slug), {}) || {}; }
  function productCard(p) {
    var li = K.productCard(p, {
      href: '/store/' + encodeURIComponent(p.shop.slug) + '#/p/' + encodeURIComponent(p.id),
      photo: K.photo(p.shop.slug, p.id), qty: (cartOf(p.shop.slug)[p.id] || {}).qtyMilli || 0, onAdd: add, shop: true,
    });
    li.setAttribute('data-shop', p.shop.slug);
    return li;
  }
  function add(p) {
    var c = cartOf(p.shop.slug);
    var cur = c[p.id] ? c[p.id].qtyMilli : 0;
    if (p.maxQtyMilli && cur + 1000 > p.maxQtyMilli) { K.toast(t('maxq', { q: K.qty(p.maxQtyMilli) })); return; }
    c[p.id] = { qtyMilli: cur + 1000, name: p.name, unit: p.unit || '', priceMinor: p.priceMinor, maxQtyMilli: p.maxQtyMilli || null, photo: Boolean(p.photo), unavailable: false };
    K.save(K.cartKey(p.shop.slug), c);
    K.save(K.cartMetaKey(p.shop.slug), { title: p.shop.title, accent: p.shop.accent, at: Date.now() });
    var focused = document.activeElement;
    Array.prototype.forEach.call(document.querySelectorAll('li.p-card[data-shop="' + CSS.escape(p.shop.slug) + '"][data-id="' + CSS.escape(p.id) + '"]'), function (li) {
      var old = li.querySelector('.p-add');
      var nb = K.addBtn(p, cur + 1000, add);
      li.replaceChild(nb, old);
      if (old === focused) nb.focus();
    });
    if (!cur) K.toast(t('added', { shop: p.shop.title }));
    renderCarts();
  }

  // ── دسته‌ها: تراشه با نشانهٔ رنگی ──
  function chip(c, on) {
    var k = CAT[c.key] || CAT.other;
    return el('button', {
      class: 'chip', attrs: { type: 'button', 'aria-pressed': String(state.category === c.key), 'data-cat': c.key },
      on: { click: function () { on(c.key); } },
    }, el('span', { class: 'ico k' + k[1] }, K.icon(k[0])), c.key ? t('cat.' + c.key) : t('all'), el('span', { class: 'count', text: K.fa(c.count, 0) }));
  }
  function renderCats() {
    var cats = [{ key: '', count: state.overview.shopCount }].concat(state.overview.categories);
    ['cats', 'shop-cats'].forEach(function (id) {
      var box = K.clear($(id));
      cats.forEach(function (c) { box.appendChild(chip(c, selectCategory)); });
    });
  }
  function selectCategory(key) {
    state.category = key;
    renderCats();
    if (state.view === 'view-shops') loadShops(false); else loadFeed();
  }
  function catLabel() {
    var c = state.overview.categories.filter(function (x) { return x.key === state.category; })[0];
    return c ? t('cat.' + c.key) : '';
  }
  function nearQs() { return state.near ? '&lat=' + state.near.lat + '&lng=' + state.near.lng : ''; }

  // ── خانه: اول کالا ──
  function fill(id, list) {
    var ul = K.clear($(id));
    list.forEach(function (p) { ul.appendChild(productCard(p)); });
    return list.length;
  }
  function loadFeed() {
    var seq = ++feedSeq;
    skeleton($('items'), 8, true);
    $('items').setAttribute('aria-busy', 'true');
    var qs = '?limit=24' + (state.category ? '&category=' + encodeURIComponent(state.category) : '') + nearQs();
    return K.api(API + '/products' + qs).then(function (r) {
      if (seq !== feedSeq) return;
      $('pop-sec').hidden = fill('pop', r.popular) === 0;
      $('pop-why').textContent = t('pop.why', { d: K.fa(r.windowDays || 30, 0) });
      $('deal-sec').hidden = fill('deal', r.deals) === 0;
      var n = fill('items', r.items);
      $('items-title').lastChild.textContent = state.near ? t('items.near') : (catLabel() ? t('items.cat', { c: catLabel() }) : t('items.all'));
      $('items-empty').hidden = n > 0;
      $('items-empty-title').textContent = t(state.category ? 'items.none.cat' : 'items.none');
      var sug = K.clear($('sug'));
      r.shops.forEach(function (s) { sug.appendChild(shopCard(s)); });
      $('sug-sec').hidden = r.shops.length === 0;
      $('sug-more').hidden = r.shopTotal <= r.shops.length;
      $('home-note').hidden = true;
    }).catch(function (e) {
      if (seq !== feedSeq) return;
      K.clear($('items'));
      K.note($('home-note'), 'err', e.message);
    }).then(function () { $('items').setAttribute('aria-busy', 'false'); });
  }

  // ── همهٔ دکان‌ها ──
  function loadShops(more) {
    var ul = $('shops');
    if (!more) { state.offset = 0; skeleton(ul, 6); }
    ul.setAttribute('aria-busy', 'true');
    var qs = '?limit=24&offset=' + state.offset + (state.category ? '&category=' + encodeURIComponent(state.category) : '') + nearQs();
    return K.api(API + '/shops' + qs).then(function (r) {
      if (!more) K.clear(ul);
      r.shops.forEach(function (s) { ul.appendChild(shopCard(s)); });
      state.offset = r.nextOffset || 0;
      $('more').hidden = !r.nextOffset;
      $('shops-title').textContent = catLabel() || t(state.near ? 'shops.near' : 'shops.all');
      $('shops-empty').hidden = r.total > 0;
      $('shops-empty-title').textContent = t(state.category ? 'shops.none.cat' : 'shops.none');
    }).catch(function (e) {
      K.clear(ul);
      K.toast(e.message);
    }).then(function () { ul.setAttribute('aria-busy', 'false'); });
  }

  // ── نزدیکِ من (اختیاری؛ هرگز لازم نیست) ──
  function locate(ev) {
    var b = ev.currentTarget;
    if (!navigator.geolocation) { K.toast(t('loc.none')); return; }
    b.disabled = true;
    navigator.geolocation.getCurrentPosition(function (pos) {
      //  همین‌جا به حدودِ یک کیلومتر گرد می‌شود؛ ذخیره نمی‌شود (privacy.html)
      state.near = { lat: Math.round(pos.coords.latitude * 100) / 100, lng: Math.round(pos.coords.longitude * 100) / 100 };
      b.disabled = false;
      ['locate', 'locate2'].forEach(function (id) { $(id).setAttribute('aria-pressed', 'true'); });
      if (state.view === 'view-shops') loadShops(false); else loadFeed();
    }, function () {
      b.disabled = false;
      K.toast(t('loc.fail'));
    }, { enableHighAccuracy: false, timeout: 10000, maximumAge: 600000 });
  }

  // ── سبدهای همین دستگاه: نوارِ تیره (r3) و برگهٔ باز (r2)؛ هر دکان جدا ──
  function openCarts() {
    var out = [];
    try {
      for (var i = 0; i < localStorage.length; i++) {
        var k = localStorage.key(i);
        if (!k || k.indexOf('store-cart:') !== 0) continue;
        var slug = k.slice(11);
        var cart = K.load(k, {}) || {};
        var ids = Object.keys(cart);
        if (!ids.length) continue;
        var meta = K.load(K.cartMetaKey(slug), {}) || {};
        out.push({
          slug: slug, title: meta.title || slug, accent: meta.accent || '', count: ids.length, at: meta.at || 0,
          qty: ids.reduce(function (s, id) { return s + cart[id].qtyMilli; }, 0),
          totalMinor: ids.reduce(function (s, id) { return s + K.lineTotal(cart[id].qtyMilli, cart[id].priceMinor); }, 0),
        });
      }
    } catch (e) { /* بی حافظه */ }
    return out.sort(function (a, b) { return b.at - a.at; });
  }
  function cartHref(c) { return '/store/' + encodeURIComponent(c.slug) + '#/cart'; }
  function renderCarts() {
    var carts = openCarts();
    var onList = state.view === 'view-home' || state.view === 'view-shops';
    var bar = $('cart-bar');
    bar.hidden = carts.length === 0 || !onList || K.sheetOpen() === $('cart-sheet');
    K.cartMode(carts.length > 0 && onList);
    document.body.classList.toggle('has-bar', !bar.hidden);
    var side = K.clear($('side-carts'));
    carts.slice(0, 4).forEach(function (c) {
      side.appendChild(el('li', {}, el('a', { class: 'side-cart', attrs: { href: cartHref(c) } }, K.avatar(c.title, c.accent, c.slug, 'sm'),
        el('span', { class: 'grow' }, el('b', { text: c.title }), el('small', { text: t('n.lines', { n: K.fa(c.count, 0) }) + t('sep') + K.money(c.totalMinor) })))));
    });
    $('side-carts-sec').hidden = carts.length === 0;
    if (!carts.length) { K.stripReset(); if (K.cartIsOpen()) closeCart(); return; }
    var items = carts.reduce(function (s, c) { return s + c.count; }, 0);
    var sum = carts.reduce(function (s, c) { return s + c.totalMinor; }, 0);
    K.strip({
      list: carts.slice(0, 3).map(function (c) { return { id: c.slug, node: el('span', { class: 'thumb' }, K.avatar(c.title, c.accent, c.slug)) }; }),
      n: carts.length, qty: carts.reduce(function (s, c) { return s + c.qty; }, 0), count: K.fa(items, 0), sum: K.money(sum),
      label: t('carts.label', { n: K.fa(items, 0), s: K.fa(carts.length, 0) }),
    });
    if (K.cartIsOpen()) renderSheet(carts);
  }
  function renderSheet(carts) {
    var ul = K.clear($('sheet-lines'));
    carts.forEach(function (c) {
      ul.appendChild(el('li', { class: 'cline shop' }, el('a', { class: 'cline-main', attrs: { href: cartHref(c) } },
        el('span', { class: 'thumb' }, K.avatar(c.title, c.accent, c.slug)),
        el('span', { class: 'name' }, c.title, el('small', { text: t('n.lines', { n: K.fa(c.count, 0) }) })),
        el('span', { class: 'price num', text: K.money(c.totalMinor) }), K.icon('chev', 'chev'))));
    });
    var next = $('cart-next');
    next.hidden = carts.length !== 1;
    next.onclick = function () { location.href = cartHref(carts[0]); };
  }
  function openCart() {
    var carts = openCarts();
    if (!carts.length) return;
    renderSheet(carts);
    K.cartOpen(closeCart);
    renderCarts();
  }
  function closeCart() { K.cartClose(); renderCarts(); }

  // ── جست‌وجو: دکان و کالا با یک جعبه (WEB-05/06) ──
  function homeSections(on) {
    $('results').hidden = on;
    $('feed').hidden = !on;
  }
  function runSearch() {
    var q = $('q').value.trim();
    state.q = q;
    $('q-clear').hidden = !q;
    if (q.length < 2) { homeSections(true); return; }
    var seq = ++searchSeq;
    homeSections(false);
    skeleton($('r-products'), 4, true);
    $('r-products-sec').hidden = false; $('r-shops-sec').hidden = true; $('r-empty').hidden = true;
    K.api(API + '/search?q=' + encodeURIComponent(q) + nearQs()).then(function (r) {
      if (seq !== searchSeq) return;
      if (r.category) {
        //  عبارت همان نامِ دستهٔ دکان بود ⇒ همان دسته
        $('q').value = ''; state.q = ''; $('q-clear').hidden = true;
        homeSections(true);
        selectCategory(r.category);
        return;
      }
      var up = K.clear($('r-products'));
      r.products.forEach(function (p) { up.appendChild(productCard(p)); });
      var us = K.clear($('r-shops'));
      r.shops.forEach(function (s) { us.appendChild(shopCard(s)); });
      $('r-products-sec').hidden = r.products.length === 0;
      $('r-shops-sec').hidden = r.shops.length === 0;
      $('r-empty').hidden = r.shops.length + r.products.length > 0;
    }).catch(function (e) {
      if (seq !== searchSeq) return;
      K.clear($('r-products'));
      K.note($('home-note'), 'err', e.message);
    });
  }
  function clearSearch() {
    $('q').value = '';
    runSearch();
    $('q').focus();
  }

  // ── سفارش‌های من ──
  function localOrders() {
    var out = [];
    try {
      for (var i = 0; i < localStorage.length; i++) {
        var k = localStorage.key(i);
        if (!k || k.indexOf('store-orders:') !== 0) continue;
        var slug = k.slice(13);
        var meta = K.load(K.cartMetaKey(slug), {}) || {};
        (K.load(k, []) || []).forEach(function (o) {
          out.push({ token: o.token, orderNo: o.orderNo, createdAt: o.at, totalMinor: o.totalMinor, store: { slug: slug, title: o.storeTitle || meta.title || slug, accent: meta.accent || '' } });
        });
      }
    } catch (e) { /* بی حافظه */ }
    return out.sort(function (a, b) { return (b.createdAt || 0) - (a.createdAt || 0); });
  }
  function orderRow(o) {
    var end = el('span', { class: 'end' });
    if (o.totalMinor !== undefined && o.totalMinor !== null) end.appendChild(el('span', { text: K.money(o.totalMinor) }));
    if (o.status) end.appendChild(el('span', { class: 'pill ' + K.orderKind(o.status), text: K.order(o.status) }));
    return el('li', {}, el('a', { class: 'card prow', attrs: { href: '/store/' + encodeURIComponent(o.store.slug) + '#/o/' + o.token } },
      K.avatar(o.store.title, o.store.accent, o.store.slug, 'sm'),
      el('span', { class: 'grow' }, el('b', { text: o.store.title }),
        el('small', { text: t('od.no', { n: K.fa(o.orderNo, 0) }) + (o.createdAt ? t('sep') + K.when(o.createdAt) : '') })),
      end));
  }
  function drawOrders(list) {
    var ul = K.clear($('orders'));
    list.forEach(function (o) { ul.appendChild(orderRow(o)); });
    $('orders-empty').hidden = list.length > 0;
  }
  function openOrders() {
    show('view-orders');
    document.title = t('my.orders') + ' — VILL3N';
    var tk = K.ticket();
    $('verify-card').hidden = Boolean(tk);
    $('who').hidden = !tk;
    $('o-msg').hidden = true;
    if (!tk) {
      if (!state.verifyAvailable) {
        K.note($('v-msg'), 'warn', t('v.off.market'));
        $('v-send').hidden = true;
      }
      drawOrders(localOrders());
      return;
    }
    $('who-phone').textContent = K.localPhone(tk.phone);
    skeleton($('orders'), 3);
    $('orders-empty').hidden = true;
    K.api(API + '/orders').then(function (r) { drawOrders(r.orders); }).catch(function (e) {
      if (e.code === 'verification_required') { K.setTicket(null); K.account(); return openOrders(); }
      drawOrders(localOrders());
      K.note($('o-msg'), 'err', e.message + ' ' + t('orders.local'));
    });
  }
  function sendCode() {
    var phone = $('v-phone').value.trim();
    if (!phone) { K.note($('v-msg'), 'err', t('v.need')); $('v-phone').focus(); return; }
    var b = $('v-send'); b.disabled = true;
    K.api(API + '/verify/start', { method: 'POST', body: { phone: phone } }).then(function (r) {
      $('v-code-row').hidden = false;
      K.note($('v-msg'), 'info', t('v.sent') + (r.devCode ? ' ' + t('v.dev', { code: r.devCode }) : ''));
      $('v-code').focus();
    }).catch(function (e) {
      if (e.code === 'verification_unavailable') { state.verifyAvailable = false; b.hidden = true; }
      K.note($('v-msg'), e.code === 'verification_unavailable' ? 'warn' : 'err', e.code === 'bad_request' ? t('err.bad_phone') : e.message);
    }).then(function () { b.disabled = false; });
  }
  function checkCode(ev) {
    if (ev) ev.preventDefault();
    var b = $('v-check'); b.disabled = true;
    K.api(API + '/verify/check', { method: 'POST', body: { phone: $('v-phone').value, code: $('v-code').value } }).then(function (r) {
      K.setTicket(r);
      K.account();
      $('v-code').value = ''; $('v-code-row').hidden = true; $('v-msg').hidden = true;
      openOrders();
    }).catch(function (e) {
      //  ۲۳.۴: خطای شبکه «کدِ اشتباه» نیست
      K.note($('v-msg'), 'err', e.message);
    }).then(function () { b.disabled = false; });
  }

  // ── مسیر ──
  function route() {
    if (!state.overview) return;
    var h = location.hash || '#/';
    if (K.cartIsOpen()) K.cartClose();
    if (h === '#/orders') return openOrders();
    if (h === '#/shops') {
      var first = state.view !== 'view-shops';
      show('view-shops');
      document.title = t('nav.shops') + ' — VILL3N';
      if (first) { window.scrollTo(0, 0); loadShops(false); }
      return;
    }
    var back = state.view && state.view !== 'view-home';
    show('view-home');
    document.title = t('title.market');
    if (back) loadFeed();
  }

  function boot() {
    K.offline();
    $('q-clear').appendChild(K.icon('close'));
    K.api(API).then(function (r) {
      state.overview = r;
      state.verifyAvailable = !r.verification || r.verification.available !== false;
      renderCats();
      route();
      if (state.view === 'view-home') loadFeed();
    }).catch(function (e) {
      if (e.status === 404) { show('view-off'); document.title = t('title.market'); }
      else { $('boot').textContent = e.message; }
    });
  }

  K.ready(function () {
    $('search-form').addEventListener('submit', function (e) { e.preventDefault(); clearTimeout(searchTimer); runSearch(); });
    $('q').addEventListener('input', function () { clearTimeout(searchTimer); searchTimer = setTimeout(runSearch, 300); });
    $('q-clear').addEventListener('click', clearSearch);
    $('r-clear').addEventListener('click', clearSearch);
    $('find').addEventListener('click', function () {
      if (location.hash && location.hash !== '#/') location.hash = '#/';
      setTimeout(function () { $('q').focus(); }, 30);
    });
    $('more').addEventListener('click', function () { loadShops(true); });
    $('locate').addEventListener('click', locate);
    $('locate2').addEventListener('click', locate);
    $('v-send').addEventListener('click', sendCode);
    $('verify-card').addEventListener('submit', checkCode);
    $('who-out').addEventListener('click', function () { K.setTicket(null); K.account(); openOrders(); });
    $('cart-bar').addEventListener('click', openCart);
    $('cart-close').addEventListener('click', closeCart);
    K.onPhone(function () { if (K.cartIsOpen()) closeCart(); renderCarts(); });
    window.addEventListener('hashchange', route);
    //  سبدی که در زبانهٔ دیگر (ویترینِ دکان) عوض شد
    window.addEventListener('storage', function (e) { if (e.key && e.key.indexOf('store-cart') === 0) renderCarts(); });
    window.addEventListener('pageshow', function (e) { if (e.persisted) renderCarts(); });
    boot();
  });
})();
