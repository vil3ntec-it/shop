/*
 * بازارچه — خانه (جست‌وجو، دسته، کارتِ دکان‌ها، نزدیکِ من) و «سفارش‌های من».
 * داده فقط از `/api/market/*` (پرچمِ `market.directory`)؛ خرید و پیگیری در صفحهٔ
 * هر دکان (`/store/<slug>`). بی innerHTML برای داده؛ CSP ِ سخت.
 */
(function () {
  'use strict';
  var $ = K.$; var el = K.el;
  var API = '/api/market';
  var state = { overview: null, category: '', near: null, offset: 0, q: '', verifyAvailable: true };
  var searchTimer = 0; var searchSeq = 0;

  function show(id) {
    ['view-home', 'view-orders', 'view-off'].forEach(function (v) { $(v).hidden = v !== id; });
    $('boot').hidden = true;
  }

  // ── کارتِ دکان ──
  function shopCard(s) {
    var meta = el('div', { class: 'meta' });
    if (s.fulfillment.delivery) {
      meta.appendChild(el('span', {}, K.icon('truck'), s.deliveryFeeMinor > 0 ? 'ارسال ' + K.money(s.deliveryFeeMinor) : 'ارسالِ رایگان'));
    }
    if (s.fulfillment.pickup) meta.appendChild(el('span', {}, K.icon('box'), 'دریافت از دکان'));
    if (s.minOrderMinor > 0) meta.appendChild(el('span', { text: 'حداقل ' + K.money(s.minOrderMinor) }));
    if (s.distanceKm !== null && s.distanceKm !== undefined) meta.appendChild(el('span', {}, K.icon('pin'), '≈ ' + K.fa(s.distanceKm, 1) + ' کیلومتر'));
    var hours = K.hours(s.status);
    if (hours) meta.appendChild(el('span', {}, K.icon('clock'), hours));
    var sub = [s.categoryLabel, s.area].filter(Boolean).join(' · ') || (K.fa(s.productCount, 0) + ' کالا');
    var closed = s.status && (s.status.badge === 'closed' || s.status.badge === 'offline');
    var a = el('a', { class: 'card shop-card' + (closed ? ' closed' : ''), attrs: { href: '/store/' + encodeURIComponent(s.slug), 'data-slug': s.slug } },
      K.avatar(s.title, s.accent, s.slug),
      el('div', { class: 'grow' },
        el('div', { class: 'top' }, el('h3', { text: s.title }), K.pill(s.status)),
        el('p', { class: 'sub', text: sub }),
        meta));
    return el('li', {}, a);
  }

  function skeleton(ul, n) {
    K.clear(ul);
    for (var i = 0; i < n; i++) ul.appendChild(el('li', { class: 'skel', attrs: { 'aria-hidden': 'true' } }));
  }

  // ── دسته‌ها (یک ردیف) ──
  function renderCats() {
    var box = K.clear($('cats'));
    var cats = [{ key: '', label: 'همه', count: state.overview.shopCount }].concat(state.overview.categories);
    cats.forEach(function (c) {
      box.appendChild(el('button', {
        class: 'chip', attrs: { type: 'button', 'aria-pressed': String(state.category === c.key), 'data-cat': c.key },
        on: { click: function () { selectCategory(c.key); } },
      }, c.label, el('span', { class: 'count', text: K.fa(c.count, 0) })));
    });
  }
  function selectCategory(key) {
    state.category = key;
    renderCats();
    loadShops(false);
  }

  // ── فهرستِ دکان‌ها ──
  function loadShops(more) {
    var ul = $('shops');
    if (!more) { state.offset = 0; skeleton(ul, 6); }
    ul.setAttribute('aria-busy', 'true');
    var qs = '?limit=24&offset=' + state.offset + (state.category ? '&category=' + encodeURIComponent(state.category) : '');
    if (state.near) qs += '&lat=' + state.near.lat + '&lng=' + state.near.lng;
    return K.api(API + '/shops' + qs).then(function (r) {
      if (!more) K.clear(ul);
      r.shops.forEach(function (s) { ul.appendChild(shopCard(s)); });
      state.offset = r.nextOffset || 0;
      $('more').hidden = !r.nextOffset;
      var cat = state.overview.categories.filter(function (c) { return c.key === state.category; })[0];
      $('shops-title').textContent = cat ? cat.label : (state.near ? 'همهٔ دکان‌ها · نزدیک‌ترها اول' : 'همهٔ دکان‌ها');
      $('shops-empty').hidden = r.total > 0;
      $('shops-empty-title').textContent = state.category ? 'هنوز دکانی در این دسته نیست' : 'هنوز دکانی در بازارچه نیست';
    }).catch(function (e) {
      K.clear(ul);
      K.note($('home-note'), 'err', e.message);
    }).then(function () { ul.setAttribute('aria-busy', 'false'); });
  }

  // ── نزدیکِ من (اختیاری؛ هرگز لازم نیست) ──
  function locate() {
    if (!navigator.geolocation) { K.toast('این مرورگر موقعیت نمی‌دهد؛ همهٔ دکان‌ها دیده می‌شوند.'); return; }
    var b = $('locate'); b.disabled = true; b.textContent = 'در حالِ یافتن…';
    navigator.geolocation.getCurrentPosition(function (pos) {
      //  همین‌جا به حدودِ یک کیلومتر گرد می‌شود؛ ذخیره نمی‌شود (privacy.html)
      state.near = { lat: Math.round(pos.coords.latitude * 100) / 100, lng: Math.round(pos.coords.longitude * 100) / 100 };
      b.disabled = false; b.textContent = 'نزدیکِ من ✓';
      b.setAttribute('aria-pressed', 'true');
      loadNear();
      loadShops(false);
    }, function () {
      b.disabled = false; b.textContent = 'نزدیکِ من';
      K.toast('موقعیت گرفته نشد؛ بی آن هم همهٔ دکان‌ها دیده می‌شوند.');
    }, { enableHighAccuracy: false, timeout: 10000, maximumAge: 600000 });
  }
  function loadNear() {
    if (!state.near) return;
    K.api(API + '/shops?limit=8&lat=' + state.near.lat + '&lng=' + state.near.lng).then(function (r) {
      var withDist = r.shops.filter(function (s) { return s.distanceKm !== null; });
      var ul = K.clear($('near'));
      withDist.forEach(function (s) { ul.appendChild(shopCard(s)); });
      $('near-sec').hidden = withDist.length === 0 || Boolean(state.q);
      if (!withDist.length) K.toast('هنوز دکانی موقعیتِ خودش را نداده است؛ همهٔ دکان‌ها پایین‌اند.');
    }).catch(function () { /* فهرستِ اصلی سرِ جایش است */ });
  }

  // ── ادامهٔ خرید (سبدهای همین دستگاه) ──
  function renderCarts() {
    var carts = K.openCarts();
    var ul = K.clear($('carts'));
    carts.slice(0, 6).forEach(function (c) {
      ul.appendChild(el('li', {}, el('a', { class: 'card prow', attrs: { href: '/store/' + encodeURIComponent(c.slug) + '#/cart' } },
        K.avatar(c.title, c.accent, c.slug, 'sm'),
        el('span', { class: 'grow' }, el('b', { text: c.title }), el('small', { text: K.fa(c.count, 0) + ' قلم در سبد' })),
        el('span', { class: 'end', text: K.money(c.totalMinor) }),
        K.icon('chev'))));
    });
    $('carts-sec').hidden = carts.length === 0 || Boolean(state.q);
  }

  // ── جست‌وجو: دکان و کالا با یک جعبه (WEB-05/06) ──
  function homeSections(on) {
    $('results').hidden = on;
    ['shops-sec'].forEach(function (id) { $(id).hidden = !on; });
    $('near-sec').hidden = !on || !state.near || !$('near').children.length;
    if (on) renderCarts(); else $('carts-sec').hidden = true;
  }
  function photoUrl(slug, id) {
    return '/api/store/' + encodeURIComponent(slug) + '/products/' + encodeURIComponent(id) + '/photo';
  }
  function productRow(p) {
    var img = el('span', { class: 'pimg' });
    if (p.photo) img.appendChild(el('img', { attrs: { src: photoUrl(p.shop.slug, p.id), alt: '', loading: 'lazy', decoding: 'async', width: '64', height: '64' } }));
    else img.appendChild(el('span', { class: 'ph', text: p.name.slice(0, 1), attrs: { 'aria-hidden': 'true' } }));
    return el('li', {}, el('a', { class: 'card prow', attrs: { href: '/store/' + encodeURIComponent(p.shop.slug) + '#/p/' + encodeURIComponent(p.id) } },
      img,
      el('span', { class: 'grow' }, el('b', { text: p.name }), el('small', {}, p.shop.title, K.pill({ badge: p.shop.badge }))),
      el('span', { class: 'end' }, K.money(p.priceMinor), p.unit ? el('small', { class: 'muted', text: p.unit }) : null)));
  }
  function runSearch() {
    var q = $('q').value.trim();
    state.q = q;
    $('q-clear').hidden = !q;
    if (q.length < 2) { homeSections(true); return; }
    var seq = ++searchSeq;
    homeSections(false);
    skeleton($('r-shops'), 2);
    $('r-shops-sec').hidden = false; $('r-products-sec').hidden = true; $('r-empty').hidden = true;
    var qs = '?q=' + encodeURIComponent(q) + (state.near ? '&lat=' + state.near.lat + '&lng=' + state.near.lng : '');
    K.api(API + '/search' + qs).then(function (r) {
      if (seq !== searchSeq) return;
      if (r.category) {
        //  عبارت همان نامِ دستهٔ دکان بود ⇒ همان دسته
        $('q').value = ''; state.q = ''; $('q-clear').hidden = true;
        homeSections(true);
        selectCategory(r.category);
        return;
      }
      var us = K.clear($('r-shops'));
      r.shops.forEach(function (s) { us.appendChild(shopCard(s)); });
      var up = K.clear($('r-products'));
      r.products.forEach(function (p) { up.appendChild(productRow(p)); });
      $('r-shops-sec').hidden = r.shops.length === 0;
      $('r-products-sec').hidden = r.products.length === 0;
      $('r-empty').hidden = r.shops.length + r.products.length > 0;
    }).catch(function (e) {
      if (seq !== searchSeq) return;
      K.clear($('r-shops'));
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
    if (o.status) end.appendChild(el('span', { class: 'pill ' + K.orderKind(o.status), text: K.ORDER[o.status] || o.status }));
    return el('li', {}, el('a', { class: 'card prow', attrs: { href: '/store/' + encodeURIComponent(o.store.slug) + '#/o/' + o.token } },
      K.avatar(o.store.title, o.store.accent, o.store.slug, 'sm'),
      el('span', { class: 'grow' }, el('b', { text: o.store.title }),
        el('small', { text: 'سفارشِ ' + K.fa(o.orderNo, 0) + (o.createdAt ? ' · ' + K.when(o.createdAt) : '') })),
      end));
  }
  function drawOrders(list) {
    var ul = K.clear($('orders'));
    list.forEach(function (o) { ul.appendChild(orderRow(o)); });
    $('orders-empty').hidden = list.length > 0;
  }
  function openOrders() {
    show('view-orders');
    document.title = 'سفارش‌های من · بازارچه';
    var t = K.ticket();
    $('verify-card').hidden = Boolean(t);
    $('who').hidden = !t;
    $('o-msg').hidden = true;
    if (!t) {
      if (!state.verifyAvailable) {
        K.note($('v-msg'), 'warn', 'تأییدِ شماره هنوز روی این سرور راه‌اندازی نشده است. سفارش‌های همین دستگاه پایین‌اند؛ برای بقیه با خودِ دکان تماس بگیرید.');
        $('v-send').hidden = true;
      }
      drawOrders(localOrders());
      return;
    }
    $('who-phone').textContent = K.localPhone(t.phone);
    skeleton($('orders'), 3);
    $('orders-empty').hidden = true;
    K.api(API + '/orders').then(function (r) { drawOrders(r.orders); }).catch(function (e) {
      if (e.code === 'verification_required') { K.setTicket(null); return openOrders(); }
      drawOrders(localOrders());
      K.note($('o-msg'), 'err', e.message + ' سفارش‌های همین دستگاه نشان داده شد.');
    });
  }
  function sendCode() {
    var phone = $('v-phone').value.trim();
    if (!phone) { K.note($('v-msg'), 'err', 'اول شمارهٔ موبایل را بنویسید.'); $('v-phone').focus(); return; }
    var b = $('v-send'); b.disabled = true;
    K.api(API + '/verify/start', { method: 'POST', body: { phone: phone } }).then(function (r) {
      $('v-code-row').hidden = false;
      K.note($('v-msg'), 'info', 'کد فرستاده شد.' + (r.devCode ? ' (کدِ آزمایشی: ' + r.devCode + ')' : ''));
      $('v-code').focus();
    }).catch(function (e) {
      if (e.code === 'verification_unavailable') { state.verifyAvailable = false; b.hidden = true; }
      K.note($('v-msg'), e.code === 'verification_unavailable' ? 'warn' : 'err', e.message);
    }).then(function () { b.disabled = false; });
  }
  function checkCode(ev) {
    if (ev) ev.preventDefault();
    var b = $('v-check'); b.disabled = true;
    K.api(API + '/verify/check', { method: 'POST', body: { phone: $('v-phone').value, code: $('v-code').value } }).then(function (r) {
      K.setTicket(r);
      $('v-code').value = ''; $('v-code-row').hidden = true; $('v-msg').hidden = true;
      openOrders();
    }).catch(function (e) {
      //  ۲۳.۴: خطای شبکه «کدِ اشتباه» نیست
      K.note($('v-msg'), 'err', e.code === 'network' ? e.message : (e.message || 'کد درست نیست'));
    }).then(function () { b.disabled = false; });
  }

  // ── مسیر ──
  function route() {
    if (!state.overview) return;
    var h = location.hash || '#/';
    if (h === '#/orders') return openOrders();
    show('view-home');
    document.title = 'بازارچه';
    renderCarts();
  }

  function boot() {
    K.offline();
    $('q-clear').appendChild(K.icon('close'));
    $('r-empty-art').appendChild(K.icon('search'));
    $('shops-empty-art').appendChild(K.icon('store'));
    $('orders-empty-art').appendChild(K.icon('receipt'));
    $('off-art').appendChild(K.icon('store'));
    $('nav-orders').insertBefore(K.icon('receipt'), $('nav-orders').firstChild);
    $('locate').insertBefore(K.icon('locate'), $('locate').firstChild);
    Array.prototype.forEach.call(document.querySelectorAll('#facts [data-i]'), function (s) { s.insertBefore(K.icon(s.getAttribute('data-i')), s.firstChild); });
    K.api(API).then(function (r) {
      state.overview = r;
      state.verifyAvailable = !r.verification || r.verification.available !== false;
      renderCats();
      loadShops(false);
      route();
    }).catch(function (e) {
      if (e.status === 404) { show('view-off'); document.title = 'بازارچه'; $('nav-orders').hidden = true; }
      else { $('boot').textContent = e.message; }
    });
  }

  document.addEventListener('DOMContentLoaded', function () {
    $('search-form').addEventListener('submit', function (e) { e.preventDefault(); clearTimeout(searchTimer); runSearch(); });
    $('q').addEventListener('input', function () { clearTimeout(searchTimer); searchTimer = setTimeout(runSearch, 300); });
    $('q-clear').addEventListener('click', clearSearch);
    $('r-clear').addEventListener('click', clearSearch);
    $('more').addEventListener('click', function () { loadShops(true); });
    $('locate').addEventListener('click', locate);
    $('v-send').addEventListener('click', sendCode);
    $('verify-card').addEventListener('submit', checkCode);
    $('who-out').addEventListener('click', function () { K.setTicket(null); openOrders(); });
    window.addEventListener('hashchange', route);
    boot();
  });
})();
