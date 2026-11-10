/* ویترینِ یک دکان: کاتالوگ، برگهٔ کالا، سبد، پیگیری (WEB، CHK، §۲۳، §۳۱.۱، §۳۲.۳)؛ ثبت checkout.js، رسید order.js.
 * ⛔ قیمت را سرور می‌گوید (expectedTotalMinor فقط CHK-17)؛ پرداختِ آنلاین فقط حساب‌پی ِ سرور (PAY-21)؛ −/+ فقط در سبد.
 * قاعده‌ها: docs/RULES-fa.md «v3 · ویترین». */
(function () {
  'use strict';
  var $ = K.$; var el = K.el; var t = K.t;
  var slug = slugOfPage();
  var BASE = '/api/store/' + encodeURIComponent(slug);
  var KEY_CART = K.cartKey(slug);
  var KEY_ORDERS = 'store-orders:' + slug;
  var store = null; var marketOn = false; var verifyAvailable = true;
  var stockList = false; var cap = {}; // E2E-MKT-02: سقفِ ۴۰۹، در حافظه
  var cart = sanitize(K.load(KEY_CART, {}));
  var catalog = { q: '', category: '', cursor: null, items: {} };
  var searchTimer = 0;
  var share = null; // WEB-20: share.js
  //  MKT-11: `?c=<کد>` ِ کمپین تا پایانِ نشست با ثبت می‌رود
  var campaign = campaignOfPage();

  function slugOfPage() {
    var parts = location.pathname.split('/').filter(Boolean);
    if (parts[0] === 'store' && parts[1]) return parts[1].toLowerCase();
    return location.hostname.split('.')[0].toLowerCase();
  }
  function campaignOfPage() {
    var key = 'store-campaign:' + slug;
    var m = /[?&]c=([0-9A-Za-z]{10})(?:&|#|$)/.exec(location.search);
    if (m) { K.saveS(key, m[1].toUpperCase()); return m[1].toUpperCase(); }
    var v = K.loadS(key);
    return typeof v === 'string' ? v : '';
  }
  function sanitize(c) {
    var out = {};
    if (!c || typeof c !== 'object') return out;
    Object.keys(c).forEach(function (id) {
      var l = c[id];
      if (l && l.qtyMilli > 0 && Number.isFinite(l.priceMinor)) out[id] = l;
    });
    return out;
  }
  function api(path, opts) { return K.api(BASE + path, opts); }
  //  RPT-14: تپشِ بالای قیف، یک بار برای هر گام در هر بارِ صفحه، فقط با `features.funnel`
  var beats = {};
  function beat(s) {
    if (!store || !store.features.funnel || beats[s]) return;
    beats[s] = 1;
    try { navigator.sendBeacon(BASE + '/funnel/' + s); } catch (e) { /* بی تپش */ }
  }
  //  WEB-16: کارت thumb، برگه photo؛ IMG-12: v = photoVersion
  function photoUrl(id, big, v) { return BASE + '/products/' + encodeURIComponent(id) + (big ? '/photo' : '/thumb') + (v ? '?v=' + encodeURIComponent(v) : ''); }

  var VIEWS = ['view-missing', 'view-catalog', 'view-checkout', 'view-order', 'view-orders'];
  function show(id) {
    VIEWS.forEach(function (v) { $(v).hidden = v !== id; });
    $('boot').hidden = true;
    var inner = id !== 'view-catalog';
    $('to-catalog').hidden = !inner || id === 'view-missing';
    $('to-market').hidden = inner || !marketOn;
    document.querySelector('.topbar').classList.toggle('inner', inner);
    K.nav(id === 'view-orders' ? 'orders' : (id === 'view-missing' ? '' : 'shop'));
    //  اول حالتِ سبد (جای پیمایش)، بعد بالای صفحه
    renderBar();
    if (inner) window.scrollTo(0, 0);
  }

  // ── سربرگ و وضعیتِ دکان (۳۱.۱) ──
  function renderHead() {
    document.title = store.title;
    $('store-title').textContent = store.title;
    $('bar-title').textContent = store.title;
    $('store-tagline').textContent = store.tagline || '';
    $('store-tagline').hidden = !store.tagline;
    K.clear($('store-avatar')).appendChild(K.avatar(store.title, store.accent, slug, 'lg'));
    K.clear($('bar-avatar')).appendChild(K.avatar(store.title, store.accent, slug, 'sm'));
    K.clear($('nav-avatar')).appendChild(K.avatar(store.title, store.accent, slug, 'sm'));
    $('nav-title').textContent = store.title;
    Array.prototype.forEach.call(document.querySelectorAll('.mk'), function (a) { a.hidden = !marketOn; });
    $('side-logo').href = marketOn ? '/market/' : '#/';
    if (store.contactPhone) { $('store-call').href = 'tel:' + store.contactPhone; $('store-call').hidden = false; }
    //  گفت‌وگو: chat.js ِ دیربار
    $('store-chat').hidden = !store.features.chat;
    $('store-chat').onclick = function () { K.js('/store/chat.js').then(function () { window.StoreChat({ api: api, slug: slug }).general(); }); };

    var s = store.status;
    var box = K.clear($('store-status'));
    if (s) {
      box.appendChild(K.pill(s));
      var hours = K.hours(s);
      if (hours) box.appendChild(el('span', {}, hours));
      //  ۳۱.۱: آخرین اتصال فقط وقتی تأیید نیست
      if (s.connection !== 'confirmed') box.appendChild(el('span', { text: s.lastConfirmedAt ? t('sh.seen', { t: K.clock(s.lastConfirmedAt, s.timezone) }) : t('sh.unseen') }));
      box.appendChild(el('span', { class: 'accept', text: accept(s) }));
      if (s.notice) box.appendChild(el('span', { class: 'notice', text: s.notice }));
    }
    var meta = K.clear($('store-meta'));
    if (store.fulfillment.delivery) meta.appendChild(el('span', {}, K.icon('truck'), shipText()));
    if (store.fulfillment.pickup) meta.appendChild(el('span', {}, K.icon('box'), t('pickup')));
    if (store.minOrderMinor > 0) meta.appendChild(el('span', { text: t('min.order', { m: K.money(store.minOrderMinor) }) }));
    meta.appendChild(el('span', {}, K.icon('cash'), t('cod')));
    $('accept-line').textContent = s || closed() ? accept(s) : '';
    if (more) more.head();
  }
  function closed() { return Boolean(store && store.ordering && store.ordering.accepting === false); }
  function accept(s) { return closed() ? t('err.store_not_accepting_orders') : K.accept(s.orderAcceptance); }

  // ── کاتالوگ ──
  function renderCats() {
    var box = K.clear($('cats'));
    var cats = store.categories || [];
    box.hidden = cats.length === 0;
    [''].concat(cats).forEach(function (c) {
      box.appendChild(el('button', {
        class: 'chip plain', text: c || t('all'), attrs: { type: 'button', 'aria-pressed': String(catalog.category === c) },
        on: { click: function () { catalog.category = c; renderCats(); loadProducts(false); } },
      }));
    });
  }

  function card(p) {
    var li = K.productCard(p, {
      photo: photoUrl(p.id, 0, p.photoVersion), qty: cart[p.id] ? cart[p.id].qtyMilli : 0, onAdd: addOne,
      onOpen: function () { location.hash = '#/p/' + encodeURIComponent(p.id); },
    });
    if (more) more.card(li, p);
    return li;
  }

  function refreshCard(id) {
    var p = catalog.items[id];
    if (!p) return;
    Array.prototype.forEach.call(document.querySelectorAll('li.p-card[data-id="' + CSS.escape(id) + '"]'), function (li) {
      li.replaceChild(K.addBtn(p, cart[id] ? cart[id].qtyMilli : 0, addOne), li.querySelector('.p-add'));
    });
  }

  /** ردیفِ کارت (`<id>-list`)؛ خالی ⇒ پنهان */
  function rail(id, list) {
    var ul = K.clear($(id + '-list'));
    list.forEach(function (p) { catalog.items[p.id] = catalog.items[p.id] || p; ul.appendChild(card(p)); });
    $(id).hidden = !list.length;
  }

  // ── ردیف‌ها، اخیر، ♥ و بنرها: catalog.js ِ دیربار (WEB-15) ──
  var more = null;
  function loadMore(r) {
    return K.js('/store/catalog.js').then(function () {
      more = window.StoreCatalog({
        api: api, slug: slug, get store() { return store; }, get cart() { return cart; }, get zone() { return zone; }, rail: rail, card: card, items: catalog.items,
        ids: ids, reorder: reorder, recent: Boolean(r.recent), features: r.features || {},
        category: function (c) { catalog.category = c; renderCats(); loadProducts(false); },
        filter: function (qs) { catalog.extra = qs; loadProducts(false); }, // WEB-07: store/filters.js
      });
      return more.picks();
    }).catch(function () { /* همان کاتالوگ */ });
  }

  function loadProducts(more) {
    var ul = $('products');
    var qs = '?limit=40&sort=' + $('sort').value + (catalog.q ? '&q=' + encodeURIComponent(catalog.q) : '') +
      (catalog.category ? '&category=' + encodeURIComponent(catalog.category) : '') +
      (catalog.extra || '') + (more && catalog.cursor ? '&cursor=' + encodeURIComponent(catalog.cursor) : '');
    if (!more) {
      K.clear(ul);
      for (var i = 0; i < 8; i++) ul.appendChild(el('li', { class: 'skel tall', attrs: { 'aria-hidden': 'true' } }));
    }
    ul.setAttribute('aria-busy', 'true');
    return api('/products' + qs).then(function (r) {
      if (!more) { K.clear(ul); $('cat-count').textContent = r.total ? t('n.items', { n: K.fa(r.total, 0) }) : ''; }
      r.items.forEach(function (p) { catalog.items[p.id] = p; ul.appendChild(card(p)); });
      var unknown = r.items.some(function (p) { return !K.stockOf(p).known; }) || (more && !$('stock-note').hidden);
      $('stock-note').hidden = !unknown;
      catalog.cursor = r.nextCursor;
      $('more').hidden = !r.nextCursor;
      var empty = ul.children.length === 0;
      $('catalog-empty').hidden = !empty;
      $('catalog-empty-text').textContent = catalog.q ? t('cat.nohit', { q: catalog.q }) : t('cat.none');
      $('catalog-msg').hidden = true;
    }).catch(function (e) {
      if (!more) K.clear(ul);
      K.note($('catalog-msg'), 'err', e.message);
    }).then(function () { ul.setAttribute('aria-busy', 'false'); });
  }

  // ── برگهٔ کالا؛ جزئیات در product.js ِ دیربار (WEB-08) ──
  var detail = null; var detailP = null;
  function loadProductView() {
    detailP = detailP || K.js('/store/product.js').then(function () { detail = window.StoreProduct({ rail: rail, slug: slug }); return detail; }, function (e) { detailP = null; throw e; });
    return detailP;
  }
  function openProduct(id) {
    var p = catalog.items[id];
    beat('view');
    //  پاسخِ دیررس برگه را باز نمی‌کند
    var here = function () { return location.hash === '#/p/' + encodeURIComponent(id); };
    var fill = function (x) {
      catalog.items[x.id] = x;
      var st = K.stockOf(x);
      $('ps-cat').textContent = x.category || '';
      $('ps-cat').hidden = !x.category;
      K.clear($('ps-img')).appendChild(x.photo ? K.photo({ src: photoUrl(x.id, 1, x.photoVersion), alt: '', width: '300', height: '300', fetchpriority: 'high' }, x.name)
        : el('span', { class: 'ph', text: String(x.name || '?').slice(0, 1), attrs: { 'aria-hidden': 'true' } }));
      $('ps-img').classList.toggle('photo', Boolean(x.photo));
      $('ps-img').classList.toggle('tile', Boolean(x.photo));
      $('ps-name').textContent = x.name;
      K.clear($('ps-price')).append(K.money(x.priceMinor), x.unit ? el('small', { text: ' / ' + x.unit }) : '');
      K.clear($('ps-stock')).appendChild(el('span', { class: 'pill ' + st.kind, text: st.text }));
      $('ps-note').textContent = x.maxQtyMilli ? t('maxq', { q: K.qty(x.maxQtyMilli) + (x.unit ? ' ' + x.unit : '') }) : '';
      $('ps-share').hidden = !share;
      $('ps-share').onclick = function () { share(x); };
      renderAddSheet(x);
      if (more) more.seen(x);
      if (K.sheetOpen() !== $('product-sheet')) K.openSheet($('product-sheet'), closeToCatalog);
    };
    if (detail) detail(null);
    if (p) fill(p);
    //  بی product.js برگه بی جزئیات
    Promise.all([api('/products/' + encodeURIComponent(id)), loadProductView().catch(function () { return null; })]).then(function (r) {
      if (!here()) return;
      fill(r[0].product);
      if (r[1]) r[1](r[0]);
    }).catch(function (e) {
      if (!here()) return;
      if (e.status === 404) { K.toast(t('p.gone')); closeToCatalog(); }
      else if (!p) K.toast(e.message);
    });
  }
  function renderAddSheet(p) {
    var b = $('ps-add'); var st = K.stockOf(p); var line = cart[p.id];
    b.disabled = !st.orderable;
    b.textContent = !st.orderable ? st.text : (line ? t('ps.more', { q: K.qty(line.qtyMilli) }) : t('ps.add', { p: K.money(p.priceMinor) }));
    b.onclick = function () { addOne(p); renderAddSheet(p); };
  }

  // ── سبد (مهمان؛ فقط روی همین دستگاه — S132) ──
  function saveCart() {
    K.save(KEY_CART, cart);
    if (store) K.save(K.cartMetaKey(slug), { title: store.title, accent: store.accent, at: Date.now() });
    renderBar();
    if (K.cartIsOpen()) renderSheet();
    if (checkout && !$('view-checkout').hidden) checkout.render();
    if (more) more.cart();
    if (acct) acct.push();
  }
  function addOne(p) {
    var cur = cart[p.id] ? cart[p.id].qtyMilli : 0;
    if (p.maxQtyMilli && cur + 1000 > p.maxQtyMilli) { K.toast(t('maxq', { q: K.qty(p.maxQtyMilli) })); return; }
    var focused = document.activeElement && document.activeElement.classList && document.activeElement.classList.contains('p-add');
    setQty(p.id, cur + 1000, p);
    if (K.isPhone() && navigator.vibrate) { try { navigator.vibrate(8); } catch (e) { /* بی لرزش */ } }
    var li = document.querySelector('#products li[data-id="' + CSS.escape(p.id) + '"] .p-add');
    if (focused && li) li.focus();
  }
  function setQty(id, milli, p) {
    var line = cart[id];
    var src = p || line;
    if (!src) return;
    if (src.maxQtyMilli && milli > src.maxQtyMilli) milli = src.maxQtyMilli;
    if (milli <= 0) delete cart[id];
    else {
      if (!line || milli > line.qtyMilli) beat('cart');
      //  CHK-15/16: یادداشت و «اگر نبود» می‌مانند
      cart[id] = Object.assign({}, line, {
        qtyMilli: milli, name: src.name, unit: src.unit || '', priceMinor: src.priceMinor,
        maxQtyMilli: src.maxQtyMilli || null, photo: Boolean(src.photo), photoVersion: src.photoVersion, unavailable: line ? line.unavailable === true : false,
      });
    }
    saveCart();
    if (catalog.items[id]) refreshCard(id);
  }
  function ids() { return Object.keys(cart); }
  function subtotal() { return ids().reduce(function (s, id) { return s + K.lineTotal(cart[id].qtyMilli, cart[id].priceMinor); }, 0); }
  function defaultFulfillment() { return store && store.fulfillment.delivery ? 'delivery' : 'pickup'; }
  // ── CHK-05/06/07/10 ناحیه (`store.delivery.zoned`)؛ ندانسته ⇒ «پس از ناحیه»؛ حساب همان `forCheckout` ِ سرور ──
  var zone = null;
  function zones() { return store && store.delivery && store.delivery.zoned ? store.delivery.zones : null; }
  function setZone(z) {
    var all = zones() || [];
    zone = z && all.filter(function (x) { return x.id === z.id; })[0] || null;
    K.saveS('zone:' + slug, zone && zone.id);
  }
  function zoneFee(z, sub) {
    return (z.freeOverMinor !== null && sub >= z.freeOverMinor ? 0 : z.feeMinor) + (z.smallBasketFeeMinor && sub < z.minOrderMinor ? z.smallBasketFeeMinor : 0);
  }
  function feeFor(f) {
    if (f !== 'delivery' || !store) return 0;
    return zones() ? (zone ? zoneFee(zone, subtotal()) : 0) : store.deliveryFeeMinor;
  }
  /** حداقلی که سبد را می‌بندد؛ «سبدِ کوچک» با هزینه باز (CHK-06) */
  function minFor(f) {
    if (!store) return 0;
    if (f !== 'delivery' || !zones()) return store.minOrderMinor;
    return zone && !zone.smallBasketFeeMinor ? zone.minOrderMinor : 0;
  }
  function shipText() {
    var zs = zones();
    if (!zs) return store.deliveryFeeMinor > 0 ? t('ship.fee', { fee: K.money(store.deliveryFeeMinor) }) : t('ship.free');
    var low = Math.min.apply(null, zs.map(function (z) { return z.feeMinor; }));
    return low > 0 ? t('ship.from', { fee: K.money(low) }) : t('ship.free');
  }
  function hasBad() { return ids().some(function (id) { return cart[id].unavailable; }); }
  function short(id) { return cap[id] > 0 && cart[id].qtyMilli > cap[id]; }
  function hasShort() { return ids().some(short); }
  function shortText(id) { return t('line.short', { q: K.qty(cap[id]) + ' ' + (cart[id].unit || t('unit')) }); }

  function thumb(id, l) {
    var th = el('span', { class: 'thumb' + (l.photo ? ' photo tile' : '') });
    if (l.photo) th.appendChild(K.photo({ src: photoUrl(id, 0, l.photoVersion), alt: '', loading: 'lazy', width: '44', height: '44' }, l.name));
    else th.textContent = String(l.name || '?').slice(0, 1);
    return th;
  }

  // ── گوشی r3/r2، تبلت برگه، کامپیوتر کشو ──
  var editing = '';
  function applyMode() { K.cartMode(ids().length > 0 && !$('view-catalog').hidden); }
  function openCart() {
    renderSheet();
    if (!ids().length) return;
    loadCheckout().catch(function () { /* «بعدی» دوباره می‌کوشد */ });
    K.cartOpen(function () { closeCart(); closeToCatalog(); });
    renderBar();
  }
  function closeCart() { editing = ''; K.cartClose(); renderBar(); }
  function closeAll() { closeCart(); if (K.sheetOpen()) K.closeSheet(true); }

  function renderBar() {
    applyMode();
    var n = ids().length;
    var bar = $('cart-bar');
    bar.hidden = n === 0 || $('view-catalog').hidden || (K.sheetOpen() === $('cart-sheet'));
    bar.inert = K.cartIsOpen();
    document.body.classList.toggle('has-bar', !bar.hidden);
    if (!n) { K.stripReset(); return; }
    K.strip({
      list: ids().slice(0, 3).map(function (id) { return { id: id, node: thumb(id, cart[id]) }; }), n: n,
      qty: ids().reduce(function (s, id) { return s + cart[id].qtyMilli; }, 0),
      count: K.fa(n, 0), sum: K.money(subtotal()),
      label: t('bar.label', { n: K.fa(n, 0), sum: K.money(subtotal()) }),
    });
  }

  function renderSheet() {
    $('cart-shop').textContent = store ? store.title : '';
    var ul = K.clear($('sheet-lines'));
    ids().forEach(function (id) {
      var l = cart[id];
      var minus = el('button', { attrs: { type: 'button', 'aria-label': t('qty.less', { name: l.name }) }, on: { click: function () { setQty(id, l.qtyMilli - 1000); } } }, K.icon('minus'));
      var plus = el('button', { attrs: { type: 'button', 'aria-label': t('qty.more', { name: l.name }) }, on: { click: function () { setQty(id, l.qtyMilli + 1000); } } }, K.icon('plus'));
      if (l.maxQtyMilli && l.qtyMilli + 1000 > l.maxQtyMilli) plus.disabled = true;
      var main = el('button', {
        class: 'cline-main', attrs: { type: 'button', 'aria-expanded': String(editing === id), 'aria-label': t('line.label', { name: l.name, q: K.qty(l.qtyMilli) + ' ' + (l.unit || t('unit')), sum: K.money(K.lineTotal(l.qtyMilli, l.priceMinor)) }) },
        on: { click: function () { editing = editing === id ? '' : id; renderSheet(); } },
      },
      thumb(id, l),
      el('span', { class: 'q num', text: K.qty(l.qtyMilli) + ' ×' }),
      el('span', { class: 'name' }, l.name, el('small', { text: l.unavailable ? t('line.gone') : short(id) ? shortText(id) : K.money(l.priceMinor) + (l.unit ? ' / ' + l.unit : '') })),
      el('span', { class: 'price num', text: K.money(K.lineTotal(l.qtyMilli, l.priceMinor)) }));
      ul.appendChild(el('li', { class: 'cline' + (l.unavailable ? ' bad' : '') + (editing === id ? ' editing' : '') },
        main, el('span', { class: 'stepper' }, minus, el('output', { text: K.qty(l.qtyMilli), attrs: { 'aria-live': 'polite' } }), plus)));
    });
    var sub = subtotal();
    var f = defaultFulfillment();
    var fee = feeFor(f);
    var min = minFor(f);
    var unknown = f === 'delivery' && zones() && !zone;
    var d = K.clear($('sheet-deliv'));
    var txt = el('div', { class: 'txt' },
      el('b', {}, f === 'delivery' ? t('ship') : t('pickup'), el('span', { text: unknown ? '—' : fee > 0 ? K.money(fee) : t('free') })));
    var progress = function (to, label) {
      var pct = Math.min(100, Math.round(sub * 100 / to));
      var bar = el('i'); bar.style.setProperty('width', pct + '%');
      txt.appendChild(el('div', { class: 'progress', attrs: { role: 'progressbar', 'aria-valuemin': '0', 'aria-valuemax': '100', 'aria-valuenow': String(pct), 'aria-label': label } }, bar));
    };
    if (unknown) {
      txt.appendChild(el('p', { text: t('zone.after') }));
    } else if (min > sub) {
      txt.appendChild(el('p', { text: t('min.left', { left: K.money(min - sub), m: K.money(min) }) }));
      progress(min, t('min.progress'));
    } else if (f === 'delivery' && zone && zone.smallBasketFeeMinor && sub < zone.minOrderMinor) {
      txt.appendChild(el('p', { text: t('zone.small', { fee: K.money(zone.smallBasketFeeMinor), m: K.money(zone.minOrderMinor) }) }));
    } else if (f === 'delivery' && zone && zone.freeOverMinor !== null && sub < zone.freeOverMinor && fee > 0) {
      txt.appendChild(el('p', { text: t('zone.free.left', { left: K.money(zone.freeOverMinor - sub) }) }));
      progress(zone.freeOverMinor, t('zone.free.progress'));
    } else if (f === 'delivery' && store.fulfillment.pickup) {
      txt.appendChild(el('p', { text: t('pickup.hint') }));
    }
    d.append(el('span', { class: 'ico' }, K.icon(f === 'delivery' ? 'truck' : 'box')), txt);
    $('sheet-total').textContent = K.money(sub + fee);
    var block = '';
    if (closed()) block = accept();
    else if (hasBad()) block = t('cart.bad');
    else if (hasShort()) block = t('cart.short');
    else if (minFor(f) > sub) block = t('cart.min');
    var m = $('sheet-msg');
    if (block) K.note(m, 'warn', block); else m.hidden = true;
    $('cart-next').disabled = Boolean(block) || ids().length === 0;
    if (!ids().length) { closeCart(); closeToCatalog(); }
  }

  /** WEB-12/۳۲.۳: قیمت و «هنوز هست» ِ سبد از سرور، نه بی‌صدا */
  function refreshCart() {
    var list = ids();
    if (!list.length) return Promise.resolve();
    return api('/products?ids=' + list.map(encodeURIComponent).join(',')).then(function (r) {
      var by = {}; r.items.forEach(function (p) { by[p.id] = p; catalog.items[p.id] = catalog.items[p.id] || p; });
      var changed = 0; var gone = 0;
      list.forEach(function (id) {
        var l = cart[id]; var p = by[id];
        if (!p || !K.stockOf(p).orderable) { if (!l.unavailable) gone++; l.unavailable = true; return; }
        if (p.priceMinor !== l.priceMinor) changed++;
        l.unavailable = false; l.priceMinor = p.priceMinor; l.name = p.name; l.unit = p.unit; l.maxQtyMilli = p.maxQtyMilli || null; l.photo = Boolean(p.photo); l.photoVersion = p.photoVersion;
      });
      saveCart();
      if (changed) K.toast(t('cart.repriced', { n: K.fa(changed, 0) }));
      else if (gone) K.toast(t('cart.gone', { n: K.fa(gone, 0) }));
    }).catch(function () { /* ثبت دوباره می‌سنجد */ });
  }

  function closeToCatalog() {
    if (/^#\/(cart|p\/)/.test(location.hash)) history.replaceState(null, '', location.pathname + location.search + '#/');
    closeAll();
    renderBar();
  }

  // ── ثبت: checkout.js + order-kit.js ِ دیربار، با سبد یا صفحهٔ ثبت ──
  var checkout = null; var checkoutP = null;
  var ORDER_KIT = '/kit/order-kit.js';
  function loadCheckout() {
    checkoutP = checkoutP || Promise.all([K.js('/store/checkout.js'), K.js(ORDER_KIT)]).then(function () { return K.part('checkout', $('view-checkout')); }).then(function () {
      checkout = window.StoreCheckout({
        api: api, slug: slug, orders: KEY_ORDERS, show: show, verifyAvailable: verifyAvailable,
        ids: ids, subtotal: subtotal, feeFor: feeFor, minFor: minFor, zones: zones, setZone: setZone, get zone() { return zone; }, shipText: shipText,
        defaultFulfillment: defaultFulfillment, hasBad: hasBad,
        short: short, hasShort: hasShort, shortText: shortText, thumb: thumb, setQty: setQty, saveCart: saveCart,
        get store() { return store; }, get cart() { return cart; }, get cap() { return cap; }, get stockList() { return stockList; },
        empty: function () { cart = {}; cap = {}; saveCart(); },
        campaign: campaign, acct: account,
        //  subscribers_only ⇒ «ثبت» بسته
        closed: closed, renderHead: renderHead,
      });
      return checkout;
    }).catch(function () { checkoutP = null; throw new Error(t('err.network')); });
    return checkoutP;
  }
  function openCheckout() {
    //  نیامد ⇒ برگشت به سبد با پیام
    loadCheckout().then(function (c) { if (location.hash === '#/checkout') c.open(); })
      .catch(function (e) { K.toast(e.message); if (location.hash === '#/checkout') location.replace('#/cart'); });
  }

  // ── رسید و پیگیری: order.js + order-kit.js + واژه‌نامه، هم‌زمان و دیربار ──
  var orderView = null; var ov = null;
  function loadOrderView() {
    orderView = orderView || Promise.all([K.js('/store/order.js'), K.js(ORDER_KIT), K.part('order', $('view-order'))]).then(function () { ov = window.StoreOrder({ api: api, slug: slug, store: store, reorder: reorder, cart: function () { return cart; }, emptyCart: function () { cart = {}; cap = {}; saveCart(); }, acct: account }); return ov; })
      .catch(function () { orderView = null; throw new Error(t('err.network')); });
    return orderView;
  }
  function stopOrder() { if (ov) ov.stop(); }

  var orderTok = null;   //  سفارشِ همین مسیر؛ دیگری ⇒ پیشین پنهان تا پاسخ (`wait` ِ order.js) و پاسخِ دیرش نمی‌نشیند
  function openOrder(token) {
    show('view-order');
    document.title = t('od.title') + ' — ' + store.title;
    stopOrder();
    orderTok = token;
    if (ov) ov.wait(token);
    $('o-msg').hidden = true;
    if (K.loadS('placed:' + token)) K.note($('o-placed'), 'ok', t('od.placed'));
    else $('o-placed').hidden = true;
    //  order.js هم‌زمان با سفارش (`open`، `fail`)
    Promise.all([api('/orders/' + encodeURIComponent(token)), loadOrderView()]).then(function (r) { if (orderTok === token) r[1].open(token, r[0].order); }).catch(function (e) {
      if (orderTok !== token) return;
      if (ov) { ov.fail(e); return; }
      //  order.js نیامد ⇒ فقط پیامِ خطا
      $('o-title').textContent = t('od.title');
      $('o-status').className = 'pill err';
      $('o-status').textContent = e.status === 404 ? t('od.nf') : t('od.err');
      $('o-meta').textContent = e.message;
    });
  }

  /** WEB-12: همان اقلام با قیمت و موجودیِ امروز (فهرستِ خرید هم، با `fail` ِ خودش) */
  function reorder(o, fail) {
    var want = {}; o.lines.forEach(function (l) { want[l.productId] = l.qtyMilli; });
    api('/products?ids=' + Object.keys(want).map(encodeURIComponent).join(',')).then(function (r) {
      var added = 0;
      r.items.forEach(function (p) {
        if (!K.stockOf(p).orderable) return;
        catalog.items[p.id] = catalog.items[p.id] || p;
        var q = want[p.id]; if (p.maxQtyMilli && q > p.maxQtyMilli) q = p.maxQtyMilli;
        setQty(p.id, Math.max(q, cart[p.id] ? cart[p.id].qtyMilli : 0), p);
        added++;
      });
      var missing = Object.keys(want).length - added;
      location.hash = '#/cart';
      K.toast(missing ? t('re.some', { a: K.fa(added, 0), m: K.fa(missing, 0) }) : t('re.all'));
    }).catch(fail || function (e) { K.note($('o-msg'), 'err', e.message); });
  }

  // CRM-13 (account.js)
  var accountView = null;
  function loadAccount() {
    accountView = accountView || Promise.all([K.js('/store/account.js'), K.part('account', $('view-orders'))]).then(function () { return window.StoreAccount({ api: api, root: $('view-orders').firstElementChild }); })
      .catch(function () { accountView = null; throw new Error(t('err.network')); });
    return accountView;
  }

  // CHK-01/03/09، WEB-14: acct.js
  var acct = null;
  function account() {
    return store.features.webAccount ? K.js('/store/acct.js').then(function () { return (acct = acct || window.StoreAcct({ api: api, slug: slug, store: store, cart: function () { return cart; }, set: function (x) { cart = sanitize(x); saveCart(); refreshCart(); } })); }) : Promise.reject();
  }

  // ── سفارش‌های من در همین دکان ──
  function openOrders() {
    show('view-orders');
    document.title = t('my.orders') + ' — ' + store.title;
    var mine = false;
    var draw = function (list) {
      if (mine) return;
      var ul = K.clear($('my-orders'));
      list.forEach(function (o) {
        var end = el('span', { class: 'end' });
        if (o.totalMinor !== undefined && o.totalMinor !== null) end.appendChild(el('span', { text: K.money(o.totalMinor) }));
        if (o.status) end.appendChild(el('span', { class: 'pill ' + K.orderKind(o.status), text: K.order(o.status) }));
        ul.appendChild(el('li', {}, el('a', { class: 'card prow', attrs: { href: '#/o/' + encodeURIComponent(o.token) } },
          el('span', { class: 'pimg' }, K.icon('receipt')),
          el('span', { class: 'grow' }, el('b', { text: t('od.no', { n: K.fa(o.orderNo, 0) }) }), el('small', { text: K.when(o.at || o.createdAt) })),
          end)));
      });
      $('my-orders-empty').hidden = list.length > 0;
    };
    draw(K.load(KEY_ORDERS, []) || []);
    //  S138: سفارش‌های همین شماره از هر دستگاه
    if (K.ticket() || K.sess()) api('/orders').then(function (r) { if (r.orders.length) draw(r.orders); }).catch(function () { /* همان محلی */ });
    account().then(function (a) { a.mine(); }, function () {});
    // CRM-13
    var acc = Boolean(store.features.accountHistory && K.ticket());
    if ($('my-account')) $('my-account').hidden = !acc;
    if (!acc) return;
    $('my-orders').hidden = false;
    loadAccount().then(function (a) { return a.open(); }).then(function (ok) {
      mine = ok;
      if (ok) { $('my-orders').hidden = true; $('my-orders-empty').hidden = true; }
    }).catch(function () { /* همان فهرستِ امروز */ });
  }

  // ── مسیر ──
  function route() {
    if (!store) return;
    var h = location.hash || '#/';
    if (h.indexOf('#/o/') === 0) { closeAll(); try { return openOrder(decodeURIComponent(h.slice(4))); } catch (e) { return openOrder(''); } }
    stopOrder();
    if (h === '#/orders') { closeAll(); return openOrders(); }
    if (h === '#/checkout') { closeAll(); return openCheckout(); }
    if ($('view-catalog').hidden) show('view-catalog');
    document.title = store.title;
    if (h === '#/cart') {
      if (!ids().length) { closeToCatalog(); return; }
      if (K.sheetOpen() && K.sheetOpen() !== $('cart-sheet')) K.closeSheet(true);
      openCart();
      return;
    }
    if (h.indexOf('#/p/') === 0) { closeCart(); return openProduct(decodeURIComponent(h.slice(4))); }
    closeAll();
    renderBar();
  }

  /** WEB-20: `/p/<id>` ⇒ `#/p/<id>` */
  function fromProductPath() {
    var m = /^(.*?)\/p\/([^/]+)\/?$/.exec(location.pathname);
    if (!m || (location.hash && location.hash !== '#/')) return;
    history.replaceState(null, '', (m[1] || '/') + location.search + '#/p/' + m[2]);
  }

  function boot() {
    fromProductPath();
    K.offline();
    $('to-market').appendChild(K.icon('back'));
    $('to-catalog').appendChild(K.icon('back'));
    $('store-call').appendChild(K.icon('phone'));
    $('store-chat').appendChild(K.icon('chat'));
    $('cart-close').appendChild(K.icon('close'));
    $('ps-close').appendChild(K.icon('close'));
    $('missing-art').appendChild(K.icon('store'));
    $('empty-art').appendChild(K.icon('search'));
    $('orders-art').appendChild(K.icon('receipt'));
    if (!/^[a-z0-9][a-z0-9-]{1,30}[a-z0-9]$/.test(slug)) { show('view-missing'); return; }
    api('').then(function (r) {
      store = r.store;
      marketOn = Boolean(r.market && r.market.available);
      stockList = Boolean(r.features && r.features.stockList);
      store.addressExtra = Boolean(r.features && r.features.addressExtra);
      store.features = r.features || {};
      verifyAvailable = !r.verification || r.verification.available !== false;
      if (r.seo && typeof r.seo.productBase === 'string') {
        //  برگهٔ باز پیش از share.js ⇒ دکمه همان‌جا
        K.js('/store/share.js').then(function () { share = window.StoreShare(r.seo.productBase); if (K.sheetOpen() === $('product-sheet')) $('ps-share').hidden = false; }, function () { /* بی «فرستادن» */ });
      }
      renderHead();
      beat('visit');
      renderCats();
      setZone({ id: K.loadS('zone:' + slug) });
      var link = el('link', { attrs: { rel: 'manifest', href: '/store/' + encodeURIComponent(slug) + '/manifest.webmanifest' } });
      document.head.appendChild(link);
      if (ids().length) K.save(K.cartMetaKey(slug), { title: store.title, accent: store.accent, at: Date.now() });
      //  CHK-03: سبدِ حساب ⇒ قیمت از سرور (cart-sync.js)
      var own = K.sess() && store.features.accountCart;
      if (K.sess() || K.load('store-cartsync:' + slug)) account().catch(function () { if (own) refreshCart(); });
      return Promise.all([loadProducts(false), own ? 0 : refreshCart(), loadMore(r)]);
    }).then(route).catch(function (e) {
      if (e.status === 404) {
        show('view-missing');
        K.api('/api/market').then(function () { $('missing-market').hidden = false; }).catch(function () { /* بازارچه خاموش */ });
      } else { $('boot').textContent = e.message; }
    });
  }

  K.ready(function () {
    $('search-form').addEventListener('submit', function (e) { e.preventDefault(); clearTimeout(searchTimer); catalog.q = $('q').value.trim(); loadProducts(false); });
    $('q').addEventListener('input', function () {
      clearTimeout(searchTimer);
      searchTimer = setTimeout(function () { catalog.q = $('q').value.trim(); loadProducts(false); }, 300);
    });
    $('clear-search').addEventListener('click', function () { catalog.q = ''; catalog.category = ''; $('q').value = ''; renderCats(); loadProducts(false); });
    $('more').addEventListener('click', function () { loadProducts(true); });
    $('sort').addEventListener('change', function () { loadProducts(false); });
    $('cart-bar').addEventListener('click', function () { location.hash = '#/cart'; });
    $('cart-close').addEventListener('click', function () { closeCart(); closeToCatalog(); });
    K.onPhone(function () { if (document.body.classList.contains('cart-open')) { closeCart(); closeToCatalog(); } renderBar(); });
    $('ps-close').addEventListener('click', function () { K.closeSheet(); });
    $('cart-next').addEventListener('click', function () { closeCart(); location.hash = '#/checkout'; });
    window.addEventListener('hashchange', route);
    window.addEventListener('storage', function (e) { if (e.key === KEY_CART) { cart = sanitize(K.load(KEY_CART, {})); renderBar(); } });
    boot();
  });
})();
