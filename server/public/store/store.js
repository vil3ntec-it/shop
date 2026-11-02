/*
 * ویترینِ یک دکان — کاتالوگ، برگهٔ کالا، نوار و برگهٔ تیرهٔ سبد (r3/r2)، پیگیری و خریدِ دوباره؛
 * ثبتِ سفارش با شمارهٔ تأییدشده در `checkout.js`؛ برگهٔ رسید، لغو و پرداختِ آنلاین در `order.js`
 * (WEB، CHK، §۲۳، §۳۱.۱، §۳۲.۳). داده فقط از `/api/store/<slug>`؛ متن از K.t.
 *
 * ⛔ قیمت را سرور می‌گوید؛ جمعِ دیده‌شده (`expectedTotalMinor`) فقط برای این است
 *    که اگر قیمت عوض شده، سفارش ثبت نشود و برگهٔ تازه روشن نشان داده شود (CHK-17).
 * ⛔ پرداختِ آنلاین فقط «پرداخت با حساب‌پی» ِ واقعیِ سرور (PAY-21، `order.js`) — هیچ دکمهٔ ساختگی.
 * ⛔ −/+ فقط در سبد؛ کارتِ کالا «یک زدن = یک واحد» با نشانِ تعداد (DESIGN v2).
 */
(function () {
  'use strict';
  var $ = K.$; var el = K.el; var t = K.t;
  var slug = slugOfPage();
  var BASE = '/api/store/' + encodeURIComponent(slug);
  var KEY_CART = K.cartKey(slug);
  var KEY_ORDERS = 'store-orders:' + slug;
  var store = null; var marketOn = false; var verifyAvailable = true;
  var stockList = false; var cap = {}; // E2E-MKT-02: سقفِ ۴۰۹ ِ out_of_stock، فقط در حافظه
  var cart = sanitize(K.load(KEY_CART, {}));
  var catalog = { q: '', category: '', cursor: null, items: {} };
  var pollTimer = 0; var searchTimer = 0;
  var share = null; // WEB-20: share.js، فقط وقتی سرور صفحهٔ کالا را می‌دهد (`seo.productBase`)
  //  MKT-11: پیوندِ کمپین/QR (`/store/go/<کد>` ⇒ `?c=<کد>`) تا پایانِ همین نشست با ثبت می‌رود (`checkout.js`)
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
  //  WEB-16: کارت و سبد thumb؛ برگهٔ کالا photo
  function photoUrl(id, big) { return BASE + '/products/' + encodeURIComponent(id) + (big ? '/photo' : '/thumb'); }

  var VIEWS = ['view-missing', 'view-catalog', 'view-checkout', 'view-order', 'view-orders'];
  function show(id) {
    VIEWS.forEach(function (v) { $(v).hidden = v !== id; });
    $('boot').hidden = true;
    var inner = id !== 'view-catalog';
    $('to-catalog').hidden = !inner || id === 'view-missing';
    $('to-market').hidden = inner || !marketOn;
    document.querySelector('.topbar').classList.toggle('inner', inner);
    K.nav(id === 'view-orders' ? 'orders' : (id === 'view-missing' ? '' : 'shop'));
    //  اول حالتِ سبد (جای پیمایش را برمی‌گرداند)، بعد بالای صفحهٔ تازه
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
    //  پیوندهای بازارچه فقط وقتی بازارچه باز است
    Array.prototype.forEach.call(document.querySelectorAll('.mk'), function (a) { a.hidden = !marketOn; });
    $('side-logo').href = marketOn ? '/market/' : '#/';
    if (store.contactPhone) { $('store-call').href = 'tel:' + store.contactPhone; $('store-call').hidden = false; }

    var s = store.status;
    var box = K.clear($('store-status'));
    if (s) {
      box.appendChild(K.pill(s));
      var hours = K.hours(s);
      if (hours) box.appendChild(el('span', {}, hours));
      box.appendChild(el('span', { text: t('sh.local', { t: K.fa(Number(s.localTime.slice(0, 2)), 0) + ':' + K.fa(Number(s.localTime.slice(3)), 0).padStart(2, K.fa(0, 0)) }) }));
      //  ۳۱.۱: زمانِ آخرین تأییدِ اتصال فقط با زمانِ واقعیِ ثبت‌شده
      box.appendChild(el('span', { text: s.lastConfirmedAt ? t('sh.seen', { t: K.clock(s.lastConfirmedAt, s.timezone) }) : t('sh.unseen') }));
      box.appendChild(el('span', { class: 'accept', text: accept(s) }));
      if (s.notice) box.appendChild(el('span', { class: 'notice', text: s.notice }));
    }
    var meta = K.clear($('store-meta'));
    if (store.fulfillment.delivery) meta.appendChild(el('span', {}, K.icon('truck'), shipText()));
    if (store.fulfillment.pickup) meta.appendChild(el('span', {}, K.icon('box'), t('pickup')));
    if (store.minOrderMinor > 0) meta.appendChild(el('span', { text: t('min.order', { m: K.money(store.minOrderMinor) }) }));
    meta.appendChild(el('span', {}, K.icon('cash'), t('cod')));
    $('accept-line').textContent = s || closed() ? accept(s) : '';
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
      photo: photoUrl(p.id), qty: cart[p.id] ? cart[p.id].qtyMilli : 0, onAdd: addOne,
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

  // ── پیشنهادها (WEB-02)، اخیر (WEB-13)، دلخواه‌ها/فهرستِ خرید (WEB-11) و بنرها (WEB-24): store/catalog.js پس از بارِ نخست (بودجهٔ JS ِ بارِ اول، WEB-15) ──
  var more = null;
  function loadMore(r) {
    return new Promise(function (ok, no) {
      document.head.appendChild(el('script', { attrs: { src: '/store/catalog.js' }, on: { load: ok, error: no } }));
    }).then(function () {
      more = window.StoreCatalog({
        api: api, slug: slug, get store() { return store; }, get cart() { return cart; }, get zone() { return zone; }, rail: rail, card: card, items: catalog.items,
        ids: ids, reorder: reorder, recent: Boolean(r.recent), features: r.features || {},
        category: function (c) { catalog.category = c; renderCats(); loadProducts(false); },
      });
      return more.picks();
    }).catch(function () { /* بی catalog.js (شبکه) همان کاتالوگ، بی این ردیف‌ها */ });
  }

  function loadProducts(more) {
    var ul = $('products');
    var qs = '?limit=40' + (catalog.q ? '&q=' + encodeURIComponent(catalog.q) : '') +
      (catalog.category ? '&category=' + encodeURIComponent(catalog.category) : '') +
      (more && catalog.cursor ? '&cursor=' + encodeURIComponent(catalog.cursor) : '');
    if (!more) {
      K.clear(ul);
      for (var i = 0; i < 8; i++) ul.appendChild(el('li', { class: 'skel tall', attrs: { 'aria-hidden': 'true' } }));
    }
    ul.setAttribute('aria-busy', 'true');
    return api('/products' + qs).then(function (r) {
      if (!more) K.clear(ul);
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

  // ── برگهٔ کالا؛ توضیح، مشخصات، مشابه و مکمل در store/product.js، با نخستین بازشدن (WEB-08، WEB-15) ──
  var detail = null; var detailP = null;
  function loadProductView() {
    detailP = detailP || new Promise(function (ok, no) {
      document.head.appendChild(el('script', { attrs: { src: '/store/product.js' }, on: { load: ok, error: no } }));
    }).then(function () { detail = window.StoreProduct({ rail: rail }); return detail; }, function (e) { detailP = null; throw e; });
    return detailP;
  }
  function openProduct(id) {
    var p = catalog.items[id];
    //  پاسخِ دیررس (کالای دیگر یا پس از بستن) برگه را باز نمی‌کند
    var here = function () { return location.hash === '#/p/' + encodeURIComponent(id); };
    var fill = function (x) {
      catalog.items[x.id] = x;
      var st = K.stockOf(x);
      $('ps-cat').textContent = x.category || '';
      $('ps-cat').hidden = !x.category;
      K.clear($('ps-img')).appendChild(x.photo ? el('img', { attrs: { src: photoUrl(x.id, 1), alt: '', width: '300', height: '300' } })
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
    //  بی product.js (شبکه) برگه همان باز می‌شود، فقط بی جزئیات
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
      cart[id] = {
        qtyMilli: milli, name: src.name, unit: src.unit || '', priceMinor: src.priceMinor,
        maxQtyMilli: src.maxQtyMilli || null, photo: Boolean(src.photo), unavailable: line ? line.unavailable === true : false,
      };
    }
    saveCart();
    if (catalog.items[id]) refreshCard(id);
  }
  function ids() { return Object.keys(cart); }
  function subtotal() { return ids().reduce(function (s, id) { return s + K.lineTotal(cart[id].qtyMilli, cart[id].priceMinor); }, 0); }
  function defaultFulfillment() { return store && store.fulfillment.delivery ? 'delivery' : 'pickup'; }
  // ── CHK-05/06/07/10 ناحیهٔ ارسال (`store.delivery.zoned`، پرچمِ `shop.storefront.zones`؛ خاموش ⇒ هزینه و حداقلِ ثابت).
  //  ناحیه از پین (`/delivery`) یا گزینش؛ ندانسته ⇒ هزینه «پس از ناحیه» و سبد بسته نمی‌شود. حساب همان `forCheckout` ِ سرور.
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
  /** حداقلی که سبد را می‌بندد؛ ناحیهٔ «هزینهٔ سبدِ کوچک» زیرِ حداقل را با هزینه می‌پذیرد (CHK-06) */
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
    if (l.photo) th.appendChild(el('img', { attrs: { src: photoUrl(id), alt: '', loading: 'lazy', width: '44', height: '44' } }));
    else th.textContent = String(l.name || '?').slice(0, 1);
    return th;
  }

  // ── گوشی: شکلِ r3 بسته / r2 باز (kit)؛ تبلت برگه، کامپیوتر کشو ──
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

  /** WEB-12/۳۲.۳: قیمت و «هنوز در ویترین» ِ سبد با سرور تازه می‌شود؛ بی‌صدا عوض نمی‌شود. */
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
        l.unavailable = false; l.priceMinor = p.priceMinor; l.name = p.name; l.unit = p.unit; l.maxQtyMilli = p.maxQtyMilli || null; l.photo = Boolean(p.photo);
      });
      saveCart();
      if (changed) K.toast(t('cart.repriced', { n: K.fa(changed, 0) }));
      else if (gone) K.toast(t('cart.gone', { n: K.fa(gone, 0) }));
    }).catch(function () { /* سبد همان می‌ماند؛ هنگامِ ثبت سرور دوباره می‌سنجد */ });
  }

  function closeToCatalog() {
    if (/^#\/(cart|p\/)/.test(location.hash)) history.replaceState(null, '', location.pathname + location.search + '#/');
    closeAll();
    renderBar();
  }

  // ── ثبتِ سفارش: store/checkout.js، فقط با بازشدنِ سبد یا صفحهٔ ثبت (بودجهٔ JS ِ بارِ اول، WEB-15) ──
  var checkout = null; var checkoutP = null;
  function loadCheckout() {
    checkoutP = checkoutP || new Promise(function (ok, no) {
      document.head.appendChild(el('script', { attrs: { src: '/store/checkout.js' }, on: { load: ok, error: no } }));
    }).then(function () { return K.part('checkout', $('view-checkout')); }).then(function () {
      checkout = window.StoreCheckout({
        api: api, slug: slug, orders: KEY_ORDERS, show: show, verifyAvailable: verifyAvailable,
        ids: ids, subtotal: subtotal, feeFor: feeFor, minFor: minFor, zones: zones, setZone: setZone, get zone() { return zone; },
        defaultFulfillment: defaultFulfillment, hasBad: hasBad,
        short: short, hasShort: hasShort, shortText: shortText, thumb: thumb, setQty: setQty, saveCart: saveCart,
        get store() { return store; }, get cart() { return cart; }, get cap() { return cap; }, get stockList() { return stockList; },
        empty: function () { cart = {}; cap = {}; saveCart(); },
        campaign: campaign,
        //  shop.orders.subscribers_only: ویترینِ فقط‌خواندنی ⇒ «ثبت» بسته با همان پیام
        closed: closed, renderHead: renderHead,
      });
      return checkout;
    }).catch(function () { checkoutP = null; throw new Error(t('err.network')); });
    return checkoutP;
  }
  function openCheckout() {
    //  بی اینترنت در نخستین باز شدن ⇒ برگشت به سبد با پیام؛ «بعدی» دوباره می‌کوشد
    loadCheckout().then(function (c) { if (location.hash === '#/checkout') c.open(); })
      .catch(function (e) { K.toast(e.message); if (location.hash === '#/checkout') location.replace('#/cart'); });
  }

  // ── رسید و پیگیری: store/order.js، فقط با بازشدنِ صفحهٔ سفارش (بودجهٔ JS ِ بارِ اول، WEB-15) ──
  var orderView = null;
  function loadOrderView() {
    //  واژه‌نامهٔ همین صفحه (`kit/i18n/order.<زبان>.json`) هم‌زمان با اسکریپت؛ نیامد ⇒ متنِ فارسیِ HTML، بی کلیدِ خام
    orderView = orderView || Promise.all([new Promise(function (ok, no) {
      document.head.appendChild(el('script', { attrs: { src: '/store/order.js' }, on: { load: ok, error: no } }));
    }), K.part('order', $('view-order'))]).then(function () { return window.StoreOrder({ api: api, slug: slug, reorder: reorder }); })
      .catch(function () { orderView = null; throw new Error(t('err.network')); });
    return orderView;
  }

  var FINAL = ['rejected', 'auto_rejected', 'cancelled_by_customer', 'cancelled', 'delivered', 'picked_up', 'restocked', 'refunded_full', 'refunded_partial'];
  function openOrder(token) {
    show('view-order');
    document.title = t('od.title') + ' — ' + store.title;
    clearTimeout(pollTimer);
    $('o-msg').hidden = true;
    if (K.loadS('placed:' + token)) K.note($('o-placed'), 'ok', t('od.placed'));
    else $('o-placed').hidden = true;
    var load = function () {
      //  نخستین بار order.js هم‌زمان با خودِ سفارش می‌آید، نه پس از آن
      Promise.all([api('/orders/' + encodeURIComponent(token)), loadOrderView()]).then(function (r) {
        var o = r[1].render(r[0].order, token);
        //  پیگیریِ زنده فقط جلوی چشم و تا سفارش تمام نشده
        //  پرداختِ در انتظار (برگشت از حساب‌پی) ⇒ هر ۵ ثانیه تا سرور «پرداخت‌شده/ناموفق» بگوید
        var pend = o.paymentStatus === 'pending';
        if (pend || FINAL.indexOf(o.status) < 0) pollTimer = setTimeout(function () { if (!document.hidden && location.hash === '#/o/' + encodeURIComponent(token)) load(); else pollTimer = setTimeout(load, 20000); }, pend ? 5000 : 20000);
      }).catch(function (e) {
        $('o-title').textContent = t('od.title');
        K.clear($('o-steps')); K.clear($('o-lines')); K.clear($('o-totals')); K.clear($('o-timeline'));
        $('o-status').className = 'pill err';
        $('o-status').textContent = e.status === 404 ? t('od.nf') : t('od.err');
        $('o-meta').textContent = e.message;
        $('o-cancel').hidden = true; $('o-reorder').hidden = true; $('o-pay').hidden = true; $('o-payhint').hidden = true; $('o-paynote').hidden = true;
        $('o-dlv').hidden = true;
      });
    };
    load();
  }

  /** WEB-12: همان اقلام با قیمت و موجودیِ امروز؛ آن‌چه نیست گفته می‌شود. «فهرستِ خرید» (WEB-11) هم با `fail` ِ خودش. */
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

  // ── CRM-13: «حسابِ من» (store/account.js ِ دیربار، با واژه‌نامهٔ `kit/i18n/account.<زبان>.json`) ──
  var accountView = null;
  function loadAccount() {
    accountView = accountView || Promise.all([new Promise(function (ok, no) {
      document.head.appendChild(el('script', { attrs: { src: '/store/account.js' }, on: { load: ok, error: no } }));
    }), K.part('account', $('view-orders'))]).then(function () { return window.StoreAccount({ api: api, root: $('view-orders').firstElementChild }); })
      .catch(function () { accountView = null; throw new Error(t('err.network')); });
    return accountView;
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
    //  S138: با شمارهٔ تأییدشده، سفارش‌های همین شماره از هر دستگاه
    if (K.ticket()) api('/orders').then(function (r) { if (r.orders.length) draw(r.orders); }).catch(function () { /* همان محلی */ });
    //  CRM-13: پرچمِ روشن و شمارهٔ تأییدشده ⇒ «حسابِ من» (آنلاین، حضوری، مانده) جای همین فهرست؛ نشد ⇒ همان فهرست
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
    clearTimeout(pollTimer);
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

  /** صفحهٔ کالا (`/p/<id>` یا `/store/<slug>/p/<id>`، WEB-20) ⇒ همان مسیرِ `#/p/<id>` ِ همین پوسته. */
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
    $('cart-close').appendChild(K.icon('close'));
    $('ps-close').appendChild(K.icon('close'));
    $('missing-art').appendChild(K.icon('store'));
    $('empty-art').appendChild(K.icon('search'));
    $('orders-art').appendChild(K.icon('receipt'));
    $('pay-ico').appendChild(K.icon('cash'));
    if (!/^[a-z0-9][a-z0-9-]{1,30}[a-z0-9]$/.test(slug)) { show('view-missing'); return; }
    api('').then(function (r) {
      store = r.store;
      marketOn = Boolean(r.market && r.market.available);
      stockList = Boolean(r.features && r.features.stockList);
      store.addressExtra = Boolean(r.features && r.features.addressExtra);
      store.features = r.features || {};
      verifyAvailable = !r.verification || r.verification.available !== false;
      if (r.seo && typeof r.seo.productBase === 'string') {
        var ss = el('script', { attrs: { src: '/store/share.js' } });
        //  برگهٔ کالا پیش از رسیدنِ share.js باز شد (پیوندِ `/p/<id>`) ⇒ دکمه همان‌جا پیدا می‌شود
        ss.onload = function () { share = window.StoreShare(r.seo.productBase); if (K.sheetOpen() === $('product-sheet')) $('ps-share').hidden = false; };
        document.head.appendChild(ss);
      }
      renderHead();
      renderCats();
      $('opt-delivery').hidden = !store.fulfillment.delivery;
      $('opt-pickup').hidden = !store.fulfillment.pickup;
      $('opt-delivery-fee').textContent = zones() ? shipText() : store.deliveryFeeMinor > 0 ? K.money(store.deliveryFeeMinor) : t('free');
      setZone({ id: K.loadS('zone:' + slug) });
      var link = el('link', { attrs: { rel: 'manifest', href: '/store/' + encodeURIComponent(slug) + '/manifest.webmanifest' } });
      document.head.appendChild(link);
      if (ids().length) K.save(K.cartMetaKey(slug), { title: store.title, accent: store.accent, at: Date.now() });
      return Promise.all([loadProducts(false), refreshCart(), loadMore(r)]);
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
