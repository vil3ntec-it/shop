/*
 * ویترینِ یک دکان — کاتالوگ، برگهٔ کالا، نوار و برگهٔ تیرهٔ سبد (r3/r2)، ثبتِ
 * سفارش با شمارهٔ تأییدشده، پیگیری و خریدِ دوباره؛ برگهٔ رسید، لغو و پرداختِ آنلاین در `order.js`
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
  var cart = sanitize(K.load(KEY_CART, {}));
  var catalog = { q: '', category: '', cursor: null, items: {} };
  var pin = null; var pollTimer = 0; var searchTimer = 0; var quoteNote = '';
  var recent = null; // WEB-13: recent.js، فقط با کلیدِ `recent` ِ سرور

  function slugOfPage() {
    var parts = location.pathname.split('/').filter(Boolean);
    if (parts[0] === 'store' && parts[1]) return parts[1].toLowerCase();
    return location.hostname.split('.')[0].toLowerCase();
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
  function photoUrl(id) { return BASE + '/products/' + encodeURIComponent(id) + '/photo'; }

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
      box.appendChild(el('span', { class: 'accept', text: K.accept(s.orderAcceptance) }));
      if (s.notice) box.appendChild(el('span', { class: 'notice', text: s.notice }));
    }
    var meta = K.clear($('store-meta'));
    if (store.fulfillment.delivery) meta.appendChild(el('span', {}, K.icon('truck'), store.deliveryFeeMinor > 0 ? t('ship.fee', { fee: K.money(store.deliveryFeeMinor) }) : t('ship.free')));
    if (store.fulfillment.pickup) meta.appendChild(el('span', {}, K.icon('box'), t('pickup')));
    if (store.minOrderMinor > 0) meta.appendChild(el('span', { text: t('min.order', { m: K.money(store.minOrderMinor) }) }));
    meta.appendChild(el('span', {}, K.icon('cash'), t('cod')));
    $('accept-line').textContent = s ? K.accept(s.orderAcceptance) : '';
  }

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
    return K.productCard(p, {
      photo: photoUrl(p.id), qty: cart[p.id] ? cart[p.id].qtyMilli : 0, onAdd: addOne,
      onOpen: function () { location.hash = '#/p/' + encodeURIComponent(p.id); },
    });
  }

  function refreshCard(id) {
    var p = catalog.items[id];
    if (!p) return;
    Array.prototype.forEach.call(document.querySelectorAll('li.p-card[data-id="' + CSS.escape(id) + '"]'), function (li) {
      li.replaceChild(K.addBtn(p, cart[id] ? cart[id].qtyMilli : 0, addOne), li.querySelector('.p-add'));
    });
  }

  /** «پیشنهادِ همین دکان»: فقط پرفروش/تازهٔ واقعیِ سرور (#301)؛ نبودِ داده ⇒ هیچ. */
  function loadPicks() {
    //  سرورِ بی #301 این مسیر را ندارد؛ #301 همراهِ `store.delivery` می‌آید
    if (!store.delivery) return Promise.resolve();
    return api('/highlights').then(function (r) {
      var best = (r.bestsellers || []).length > 0;
      var list = best ? r.bestsellers : (r.newArrivals || []);
      var ul = K.clear($('picks-list'));
      list.forEach(function (p) { catalog.items[p.id] = catalog.items[p.id] || p; ul.appendChild(card(p)); });
      $('picks-why').textContent = best ? t('picks.best', { d: K.fa(r.windowDays || 30, 0) }) : t('picks.new');
      $('picks').hidden = list.length === 0;
    }).catch(function () { $('picks').hidden = true; });
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

  // ── برگهٔ کالا ──
  function openProduct(id) {
    var p = catalog.items[id];
    var fill = function (x) {
      catalog.items[x.id] = x;
      var st = K.stockOf(x);
      $('ps-cat').textContent = x.category || '';
      $('ps-cat').hidden = !x.category;
      K.clear($('ps-img')).appendChild(x.photo ? el('img', { attrs: { src: photoUrl(x.id), alt: '', width: '300', height: '300' } })
        : el('span', { class: 'ph', text: String(x.name || '?').slice(0, 1), attrs: { 'aria-hidden': 'true' } }));
      $('ps-img').classList.toggle('photo', Boolean(x.photo));
      $('ps-img').classList.toggle('tile', Boolean(x.photo));
      $('ps-name').textContent = x.name;
      K.clear($('ps-price')).append(K.money(x.priceMinor), x.unit ? el('small', { text: ' / ' + x.unit }) : '');
      K.clear($('ps-stock')).appendChild(el('span', { class: 'pill ' + st.kind, text: st.text }));
      $('ps-note').textContent = x.maxQtyMilli ? t('maxq', { q: K.qty(x.maxQtyMilli) + (x.unit ? ' ' + x.unit : '') }) : '';
      renderAddSheet(x);
      if (recent) recent.seen(x);
      if (K.sheetOpen() !== $('product-sheet')) K.openSheet($('product-sheet'), closeToCatalog);
    };
    if (p) return fill(p);
    api('/products?ids=' + encodeURIComponent(id)).then(function (r) {
      if (r.items[0]) fill(r.items[0]);
      else { K.toast(t('p.gone')); closeToCatalog(); }
    }).catch(function (e) { K.toast(e.message); });
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
    if (!$('view-checkout').hidden) renderCheckout();
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
  function feeFor(f) { return f === 'delivery' && store ? store.deliveryFeeMinor : 0; }
  function hasBad() { return ids().some(function (id) { return cart[id].unavailable; }); }

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
      el('span', { class: 'name' }, l.name, el('small', { text: l.unavailable ? t('line.gone') : K.money(l.priceMinor) + (l.unit ? ' / ' + l.unit : '') })),
      el('span', { class: 'price num', text: K.money(K.lineTotal(l.qtyMilli, l.priceMinor)) }));
      ul.appendChild(el('li', { class: 'cline' + (l.unavailable ? ' bad' : '') + (editing === id ? ' editing' : '') },
        main, el('span', { class: 'stepper' }, minus, el('output', { text: K.qty(l.qtyMilli), attrs: { 'aria-live': 'polite' } }), plus)));
    });
    var sub = subtotal();
    var f = defaultFulfillment();
    var fee = feeFor(f);
    var d = K.clear($('sheet-deliv'));
    var txt = el('div', { class: 'txt' },
      el('b', {}, f === 'delivery' ? t('ship') : t('pickup'), el('span', { text: fee > 0 ? K.money(fee) : t('free') })));
    if (store && store.minOrderMinor > sub) {
      var pct = Math.min(100, Math.round(sub * 100 / store.minOrderMinor));
      txt.appendChild(el('p', { text: t('min.left', { left: K.money(store.minOrderMinor - sub), m: K.money(store.minOrderMinor) }) }));
      var bar = el('i'); bar.style.setProperty('width', pct + '%');
      txt.appendChild(el('div', { class: 'progress', attrs: { role: 'progressbar', 'aria-valuemin': '0', 'aria-valuemax': '100', 'aria-valuenow': String(pct), 'aria-label': t('min.progress') } }, bar));
    } else if (f === 'delivery' && store.fulfillment.pickup) {
      txt.appendChild(el('p', { text: t('pickup.hint') }));
    }
    d.append(el('span', { class: 'ico' }, K.icon(f === 'delivery' ? 'truck' : 'box')), txt);
    $('sheet-total').textContent = K.money(sub + fee);
    var block = '';
    if (hasBad()) block = t('cart.bad');
    else if (store && store.minOrderMinor > sub) block = t('cart.min');
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

  // ── ثبتِ سفارش (۲۳.۳، CHK) ──
  function fulfillment() {
    var r = document.querySelector('input[name="fulfillment"]:checked');
    return r ? r.value : '';
  }
  function verifiedFor(phone) { var tk = K.ticket(); return Boolean(tk && phone && tk.phone === K.normPhone(phone)); }

  function renderCheckout() {
    var ul = K.clear($('cart-lines'));
    ids().forEach(function (id) {
      var l = cart[id];
      ul.appendChild(el('li', { class: l.unavailable ? 'bad' : '' }, thumb(id, l),
        el('span', { class: 'grow' }, l.name, el('small', { text: K.qty(l.qtyMilli) + ' ' + (l.unit || '') + ' × ' + K.money(l.priceMinor) })),
        el('span', { class: 'amt num', text: K.money(K.lineTotal(l.qtyMilli, l.priceMinor)) })));
    });
    $('cart-empty').hidden = ids().length > 0;
    var f = fulfillment();
    var sub = subtotal(); var fee = feeFor(f);
    $('t-subtotal').textContent = K.money(sub);
    $('t-fee').textContent = f === 'delivery' ? (fee > 0 ? K.money(fee) : t('free')) : '—';
    $('t-fee-label').textContent = f === 'pickup' ? t('pickup') : t('ck.fee');
    $('t-total').textContent = K.money(sub + fee);
    $('address-box').hidden = f !== 'delivery';
    var below = store.minOrderMinor > sub;
    if (below) K.note($('min-order'), 'warn', t('ck.min', { m: K.money(store.minOrderMinor) })); else $('min-order').hidden = true;
    renderVerify();
    var ok = verifiedFor($('c-phone').value);
    var btn = $('submit-order');
    btn.disabled = !ids().length || !f || below || hasBad() || !ok;
    btn.textContent = t(quoteNote ? 'ck.accept' : 'ck.submit', { sum: K.money(sub + fee) });
  }

  function renderVerify() {
    var ok = verifiedFor($('c-phone').value);
    $('verify-send').hidden = ok || !verifyAvailable;
    if (ok) { K.note($('verify-msg'), 'ok', t('v.ok')); $('verify-code-row').hidden = true; $('verify-text').hidden = true; }
    else {
      $('verify-text').hidden = false;
      if (!verifyAvailable) {
        K.note($('verify-msg'), 'warn', store.contactPhone ? t('v.off.call', { phone: K.localPhone(store.contactPhone) }) : t('v.off.shop'));
      } else if ($('verify-msg').className.indexOf(' ok') >= 0) $('verify-msg').hidden = true;
    }
  }

  function sendCode() {
    var phone = $('c-phone').value;
    if (!phone.trim()) { K.note($('verify-msg'), 'err', t('v.need')); $('c-phone').focus(); return; }
    var b = $('verify-send'); b.disabled = true;
    api('/verify/start', { method: 'POST', body: { phone: phone } }).then(function (r) {
      $('verify-code-row').hidden = false;
      K.note($('verify-msg'), 'info', t('v.sent') + (r.devCode ? ' ' + t('v.dev', { code: r.devCode }) : ''));
      $('v-code').focus();
    }).catch(function (e) {
      if (e.code === 'verification_unavailable') verifyAvailable = false;
      K.note($('verify-msg'), e.code === 'verification_unavailable' ? 'warn' : 'err', e.code === 'bad_request' ? t('err.bad_phone') : e.message);
    }).then(function () { b.disabled = false; renderCheckout(); });
  }

  function checkCode() {
    var b = $('verify-check'); b.disabled = true;
    api('/verify/check', { method: 'POST', body: { phone: $('c-phone').value, code: $('v-code').value } }).then(function (r) {
      K.setTicket(r);
      K.account();
      $('v-code').value = '';
      renderCheckout();
    }).catch(function (e) {
      //  ۲۳.۴: خطای شبکه «کدِ اشتباه» نیست
      K.note($('verify-msg'), 'err', e.message);
    }).then(function () { b.disabled = false; });
  }

  function takePin() {
    if (!navigator.geolocation) { $('pin-text').textContent = t('pin.none'); return; }
    $('pin-text').textContent = t('pin.wait');
    navigator.geolocation.getCurrentPosition(function (pos) {
      pin = { lat: Math.round(pos.coords.latitude * 1e6) / 1e6, lng: Math.round(pos.coords.longitude * 1e6) / 1e6 };
      $('pin-text').textContent = t('pin.ok', { m: K.fa(Math.round(pos.coords.accuracy || 0), 0) });
      $('pin-btn').textContent = t('pin.again');
    }, function () {
      pin = null;
      $('pin-text').textContent = t('pin.fail');
    }, { enableHighAccuracy: true, timeout: 10000 });
  }

  function orderBody() {
    var f = fulfillment();
    var body = {
      fulfillment: f, paymentMethod: 'cod',
      customer: { name: $('c-name').value.trim(), phone: $('c-phone').value.trim() },
      lines: ids().map(function (id) { return { productId: id, qtyMilli: cart[id].qtyMilli }; }),
      note: $('c-note').value.trim(),
      expectedTotalMinor: subtotal() + feeFor(f),
    };
    if (f === 'delivery') {
      body.address = { text: $('a-text').value.trim(), landmark: $('a-landmark').value.trim(), area: $('a-area').value.trim(), gateColor: $('a-gate').value.trim() };
      if (pin) { body.address.lat = pin.lat; body.address.lng = pin.lng; }
      body.delivery = { callBeforeArrival: $('d-call').checked, leaveWith: $('d-leave').value.trim() };
    }
    return body;
  }

  function submitOrder(ev) {
    ev.preventDefault();
    var msg = $('checkout-msg');
    var body = orderBody();
    if (!body.customer.name) { K.note(msg, 'err', t('ck.name')); $('c-name').focus(); return; }
    if (body.fulfillment === 'delivery' && !body.address.text && !body.address.landmark && !pin) {
      K.note(msg, 'err', t('ck.addr')); $('a-text').focus(); return;
    }
    var btn = $('submit-order');
    btn.disabled = true;
    K.note(msg, 'info', t('ck.sending'));
    api('/checkout', { method: 'POST', body: body, key: K.keyFor('checkout:' + slug, body) }).then(function (r) {
      K.dropKey('checkout:' + slug);
      var list = K.load(KEY_ORDERS, []) || [];
      if (!list.some(function (o) { return o.token === r.order.token; })) {
        list.unshift({ token: r.order.token, orderNo: r.order.orderNo, at: r.order.createdAt, totalMinor: r.order.totalMinor, storeTitle: store.title });
        K.save(KEY_ORDERS, list.slice(0, 30));
      }
      K.saveS('placed:' + r.order.token, true);
      //  فقط روی همین دستگاه، برای سفارشِ بعدی (نام و آخرین نشانی)
      K.save('store-customer', { name: body.customer.name, addressText: body.address ? body.address.text : '', landmark: body.address ? body.address.landmark : '' });
      cart = {}; quoteNote = ''; saveCart();
      msg.hidden = true;
      location.hash = '#/o/' + encodeURIComponent(r.order.token);
    }).catch(function (e) {
      var d = e.data || {};
      if (e.code === 'price_changed' && d.quote) {
        //  S135: تغییر روشن نشان داده می‌شود؛ بی پذیرشِ دوباره ثبت نمی‌شود
        var changed = [];
        d.quote.lines.forEach(function (l) {
          if (cart[l.productId] && cart[l.productId].priceMinor !== l.unitPriceMinor) {
            changed.push(l.name + ': ' + K.money(cart[l.productId].priceMinor) + ' ← ' + K.money(l.unitPriceMinor));
            cart[l.productId].priceMinor = l.unitPriceMinor;
          }
        });
        quoteNote = t('ck.repriced', { list: changed.length ? changed.join(t('sep')) + '. ' : '', sum: K.money(d.quote.totalMinor) });
        saveCart();
        K.note(msg, 'warn', quoteNote);
      } else if (e.code === 'product_unavailable' && d.unavailable) {
        d.unavailable.forEach(function (id) { if (cart[id]) cart[id].unavailable = true; });
        saveCart();
        K.note(msg, 'err', e.message);
      } else if (e.code === 'qty_over_limit') {
        K.note(msg, 'err', e.message + ' (' + t('maxq', { q: K.qty(d.maxQtyMilli) }) + ')');
      } else if (e.code === 'below_min_order') {
        K.note(msg, 'err', t('err.below_min_order') + ' (' + K.money(d.minOrderMinor) + ')');
      } else if (e.code === 'verification_required' || e.code === 'phone_not_verified') {
        K.setTicket(null);
        K.note(msg, 'err', e.message);
      } else {
        K.note(msg, 'err', e.message);
      }
    }).then(function () { if (!$('view-checkout').hidden) renderCheckout(); });
  }

  function openCheckout() {
    if (!ids().length) { location.hash = '#/'; return; }
    show('view-checkout');
    document.title = t('ck.title') + ' — ' + store.title;
    if (!fulfillment()) {
      var f = defaultFulfillment();
      var r = document.querySelector('input[name="fulfillment"][value="' + f + '"]');
      if (r) r.checked = true;
    }
    var tk = K.ticket();
    if (tk && !$('c-phone').value) $('c-phone').value = K.localPhone(tk.phone);
    var me = K.load('store-customer', null);
    if (me) {
      if (!$('c-name').value && me.name) $('c-name').value = me.name;
      if (!$('a-text').value && me.addressText) $('a-text').value = me.addressText;
      if (!$('a-landmark').value && me.landmark) $('a-landmark').value = me.landmark;
    }
    renderCheckout();
  }

  // ── رسید و پیگیری: store/order.js، فقط با بازشدنِ صفحهٔ سفارش (بودجهٔ JS ِ بارِ اول، WEB-15) ──
  var orderView = null;
  function loadOrderView() {
    orderView = orderView || new Promise(function (ok, no) {
      document.head.appendChild(el('script', { attrs: { src: '/store/order.js' }, on: { load: ok, error: no } }));
    }).then(function () { return window.StoreOrder({ api: api, slug: slug, reorder: reorder }); })
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

  /** WEB-12: همان اقلام با قیمت و موجودیِ امروز؛ آن‌چه نیست گفته می‌شود. */
  function reorder(o) {
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
    }).catch(function (e) { K.note($('o-msg'), 'err', e.message); });
  }

  // ── سفارش‌های من در همین دکان ──
  function openOrders() {
    show('view-orders');
    document.title = t('my.orders') + ' — ' + store.title;
    var draw = function (list) {
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

  function boot() {
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
      verifyAvailable = !r.verification || r.verification.available !== false;
      if (r.recent) {
        var rs = el('script', { attrs: { src: '/store/recent.js' } });
        rs.onload = function () { recent = window.StoreRecent.start({ slug: slug, api: api, card: card, items: catalog.items }); };
        document.head.appendChild(rs);
      }
      renderHead();
      renderCats();
      $('opt-delivery').hidden = !store.fulfillment.delivery;
      $('opt-pickup').hidden = !store.fulfillment.pickup;
      $('opt-delivery-fee').textContent = store.deliveryFeeMinor > 0 ? K.money(store.deliveryFeeMinor) : t('free');
      var link = el('link', { attrs: { rel: 'manifest', href: '/store/' + encodeURIComponent(slug) + '/manifest.webmanifest' } });
      document.head.appendChild(link);
      if (ids().length) K.save(K.cartMetaKey(slug), { title: store.title, accent: store.accent, at: Date.now() });
      return Promise.all([loadProducts(false), refreshCart(), loadPicks()]);
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
    Array.prototype.forEach.call(document.querySelectorAll('input[name="fulfillment"]'), function (r) { r.addEventListener('change', renderCheckout); });
    $('c-phone').addEventListener('input', renderCheckout);
    $('verify-send').addEventListener('click', sendCode);
    $('verify-check').addEventListener('click', checkCode);
    $('v-code').addEventListener('keydown', function (e) { if (e.key === 'Enter') { e.preventDefault(); checkCode(); } });
    $('pin-btn').addEventListener('click', takePin);
    $('checkout-form').addEventListener('submit', submitOrder);
    window.addEventListener('hashchange', route);
    window.addEventListener('storage', function (e) { if (e.key === KEY_CART) { cart = sanitize(K.load(KEY_CART, {})); renderBar(); } });
    boot();
  });
})();
