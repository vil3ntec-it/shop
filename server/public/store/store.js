/*
 * ویترینِ یک دکان — کاتالوگ، برگهٔ کالا، نوار و برگهٔ تیرهٔ سبد (ref-5/6)، ثبتِ
 * سفارش با شمارهٔ تأییدشده، رسید و پیگیری، لغو و خریدِ دوباره
 * (WEB، CHK، §۲۳، §۳۱.۱، §۳۲.۳). داده فقط از `/api/store/<slug>`.
 *
 * ⛔ قیمت را سرور می‌گوید؛ جمعِ دیده‌شده (`expectedTotalMinor`) فقط برای این است
 *    که اگر قیمت عوض شده، سفارش ثبت نشود و برگهٔ تازه روشن نشان داده شود (CHK-17).
 * ⛔ پرداختِ آنلاین نیست (D-08) — هیچ دکمهٔ ساختگی.
 * ⛔ −/+ فقط در سبد؛ کارتِ کالا «یک زدن = یک واحد» با نشانِ تعداد (DESIGN v2).
 */
(function () {
  'use strict';
  var $ = K.$; var el = K.el;
  var slug = slugOfPage();
  var BASE = '/api/store/' + encodeURIComponent(slug);
  var KEY_CART = K.cartKey(slug);
  var KEY_ORDERS = 'store-orders:' + slug;
  var store = null; var marketOn = false; var verifyAvailable = true;
  var cart = sanitize(K.load(KEY_CART, {}));
  var catalog = { q: '', category: '', cursor: null, items: {} };
  var pin = null; var pollTimer = 0; var searchTimer = 0; var quoteNote = '';

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
    document.querySelector('.appbar').classList.toggle('inner', inner);
    if (inner) window.scrollTo(0, 0);
    renderBar();
  }

  // ════════════════════════════════════════════════════════════════
  //  سربرگ و وضعیتِ دکان (۳۱.۱)
  // ════════════════════════════════════════════════════════════════

  function renderHead() {
    document.title = store.title;
    $('store-title').textContent = store.title;
    $('bar-title').textContent = store.title;
    $('store-tagline').textContent = store.tagline || '';
    $('store-tagline').hidden = !store.tagline;
    K.clear($('store-avatar')).appendChild(K.avatar(store.title, store.accent, slug, 'lg'));
    K.clear($('bar-avatar')).appendChild(K.avatar(store.title, store.accent, slug, 'sm'));
    if (store.contactPhone) { $('store-call').href = 'tel:' + store.contactPhone; $('store-call').hidden = false; }

    var s = store.status;
    var box = K.clear($('store-status'));
    if (s) {
      box.appendChild(K.pill(s));
      var hours = K.hours(s);
      if (hours) box.appendChild(el('span', {}, hours));
      box.appendChild(el('span', { text: 'ساعتِ دکان ' + K.fa(Number(s.localTime.slice(0, 2)), 0) + ':' + K.fa(Number(s.localTime.slice(3)), 0).padStart(2, '۰') }));
      //  ۳۱.۱: زمانِ آخرین تأییدِ اتصال فقط با زمانِ واقعیِ ثبت‌شده
      box.appendChild(el('span', { text: s.lastConfirmedAt ? 'آخرین تأییدِ اتصال ' + K.clock(s.lastConfirmedAt, s.timezone) : 'اتصالِ دکان هنوز تأیید نشده' }));
      box.appendChild(el('span', { class: 'accept', text: K.ACCEPT[s.orderAcceptance] || '' }));
      if (s.notice) box.appendChild(el('span', { class: 'notice', text: s.notice }));
    }
    var meta = K.clear($('store-meta'));
    if (store.fulfillment.delivery) meta.appendChild(el('span', {}, K.icon('truck'), store.deliveryFeeMinor > 0 ? 'ارسال ' + K.money(store.deliveryFeeMinor) : 'ارسالِ رایگان'));
    if (store.fulfillment.pickup) meta.appendChild(el('span', {}, K.icon('box'), 'دریافت از دکان'));
    if (store.minOrderMinor > 0) meta.appendChild(el('span', { text: 'حداقلِ سفارش ' + K.money(store.minOrderMinor) }));
    meta.appendChild(el('span', {}, K.icon('cash'), 'نقد هنگامِ تحویل'));
    $('accept-line').textContent = s ? (K.ACCEPT[s.orderAcceptance] || '') : '';
  }

  // ════════════════════════════════════════════════════════════════
  //  کاتالوگ
  // ════════════════════════════════════════════════════════════════

  function renderCats() {
    var box = K.clear($('cats'));
    var cats = store.categories || [];
    box.hidden = cats.length === 0;
    [''].concat(cats).forEach(function (c) {
      box.appendChild(el('button', {
        class: 'chip', text: c || 'همه', attrs: { type: 'button', 'aria-pressed': String(catalog.category === c) },
        on: { click: function () { catalog.category = c; renderCats(); loadProducts(false); } },
      }));
    });
  }

  /** ۳۲.۳: پیامِ موجودی ساده و دقیق؛ نامعلوم هرگز «موجود» نیست. */
  function stockOf(p) {
    var s = (p && p.stock) || { status: 'unverified' };
    switch (s.status) {
      case 'in_stock': return { text: 'موجود', kind: 'ok', orderable: true, known: true };
      case 'in_warehouse': return { text: s.prepMinutes ? 'در انبار، آماده‌سازی ~' + K.fa(s.prepMinutes, 0) + ' دقیقه' : 'در انبار؛ آماده‌سازی لازم', kind: 'info', orderable: true, known: true };
      case 'out_of_stock': return { text: 'ناموجود', kind: 'err', orderable: false, known: true };
      case 'unavailable': return { text: 'قابلِ سفارش نیست', kind: 'err', orderable: false, known: true };
      default: return { text: 'موجودی در حال بررسی است', kind: 'off', orderable: true };
    }
  }

  function imgBox(cls, p) {
    var box = el('span', { class: cls + (p.photo ? ' photo tile' : '') });
    if (p.photo) box.appendChild(el('img', { attrs: { src: photoUrl(p.id), alt: '', loading: 'lazy', decoding: 'async', width: '300', height: '300' } }));
    else box.appendChild(el('span', { class: 'ph', text: String(p.name || '؟').slice(0, 1), attrs: { 'aria-hidden': 'true' } }));
    return box;
  }

  function card(p) {
    var st = stockOf(p);
    var li = el('li', { class: 'p-card' + (st.orderable ? '' : ' unavailable'), attrs: { 'data-id': p.id } });
    var open = el('button', { class: 'p-open', attrs: { type: 'button', 'aria-label': p.name + '، ' + K.money(p.priceMinor) + (p.unit ? ' برای هر ' + p.unit : '') + ' — جزئیات' },
      on: { click: function () { location.hash = '#/p/' + encodeURIComponent(p.id); } } },
      imgBox('p-img', p),
      el('span', { class: 'p-price num' }, K.fa(p.priceMinor / 100), el('small', { text: ' افغانی' })),
      el('span', { class: 'p-name', text: p.name }),
      p.unit ? el('span', { class: 'p-unit', text: 'هر ' + p.unit }) : null,
      //  ۳۲.۳: نشانِ موجودی فقط وقتی دانسته است؛ «در حال بررسی» یک بار بالای کاتالوگ
      st.known ? el('span', { class: 'p-stock' }, el('span', { class: 'pill ' + st.kind, text: st.text })) : null);
    li.append(open, addButton(p, st));
    return li;
  }

  function addButton(p, st) {
    var line = cart[p.id];
    var b = el('button', { class: 'p-add' + (line ? ' in' : ''), attrs: { type: 'button' } });
    if (!st.orderable) {
      b.disabled = true;
      b.setAttribute('aria-label', p.name + ' — ' + st.text);
      b.appendChild(K.icon('plus'));
      return b;
    }
    if (line) {
      b.textContent = K.qty(line.qtyMilli);
      b.setAttribute('aria-label', 'افزودنِ یکی دیگر ' + p.name + ' — ' + K.qty(line.qtyMilli) + ' در سبد');
    } else {
      b.appendChild(K.icon('plus'));
      b.setAttribute('aria-label', 'افزودنِ ' + p.name + ' به سبد');
    }
    b.addEventListener('click', function (e) { e.stopPropagation(); addOne(p); });
    return b;
  }

  function refreshCard(id) {
    var li = document.querySelector('#products li[data-id="' + CSS.escape(id) + '"]');
    var p = catalog.items[id];
    if (!li || !p) return;
    var old = li.querySelector('.p-add');
    var nb = addButton(p, stockOf(p));
    li.replaceChild(nb, old);
    return nb;
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
      var unknown = Object.keys(catalog.items).some(function (id) { return !stockOf(catalog.items[id]).known; });
      $('stock-note').hidden = !unknown;
      catalog.cursor = r.nextCursor;
      $('more').hidden = !r.nextCursor;
      var empty = ul.children.length === 0;
      $('catalog-empty').hidden = !empty;
      $('catalog-empty-text').textContent = catalog.q ? 'برای «' + catalog.q + '» چیزی پیدا نشد' : 'هنوز کالایی در این بخش نیست';
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
      var st = stockOf(x);
      $('ps-cat').textContent = x.category || 'کالا';
      $('ps-cat').hidden = !x.category;
      K.clear($('ps-img')).appendChild(imgBox('ps-img-in', x).firstChild);
      $('ps-img').classList.toggle('photo', Boolean(x.photo));
      $('ps-img').classList.toggle('tile', Boolean(x.photo));
      $('ps-name').textContent = x.name;
      K.clear($('ps-price')).append(K.money(x.priceMinor), x.unit ? el('small', { text: ' / ' + x.unit }) : '');
      K.clear($('ps-stock')).appendChild(el('span', { class: 'pill ' + st.kind, text: st.text }));
      $('ps-note').textContent = x.maxQtyMilli ? 'حداکثرِ هر سفارش: ' + K.qty(x.maxQtyMilli) + (x.unit ? ' ' + x.unit : '') : '';
      renderAddSheet(x);
      if (K.sheetOpen() !== $('product-sheet')) K.openSheet($('product-sheet'), closeToCatalog);
    };
    if (p) return fill(p);
    api('/products?ids=' + encodeURIComponent(id)).then(function (r) {
      if (r.items[0]) fill(r.items[0]);
      else { K.toast('این کالا دیگر در ویترین نیست.'); closeToCatalog(); }
    }).catch(function (e) { K.toast(e.message); });
  }
  function renderAddSheet(p) {
    var b = $('ps-add'); var st = stockOf(p); var line = cart[p.id];
    b.disabled = !st.orderable;
    b.textContent = !st.orderable ? st.text : (line ? 'یکی دیگر — ' + K.qty(line.qtyMilli) + ' در سبد' : 'افزودن به سبد — ' + K.money(p.priceMinor));
    b.onclick = function () { addOne(p); renderAddSheet(p); };
  }

  // ════════════════════════════════════════════════════════════════
  //  سبد (مهمان؛ فقط روی همین دستگاه — S132)
  // ════════════════════════════════════════════════════════════════

  function saveCart() {
    K.save(KEY_CART, cart);
    if (store) K.save(K.cartMetaKey(slug), { title: store.title, accent: store.accent, at: Date.now() });
    renderBar();
    if (cartOpen()) renderSheet();
    if (!$('view-checkout').hidden) renderCheckout();
  }
  function addOne(p) {
    var cur = cart[p.id] ? cart[p.id].qtyMilli : 0;
    if (p.maxQtyMilli && cur + 1000 > p.maxQtyMilli) { K.toast('حداکثرِ هر سفارش: ' + K.qty(p.maxQtyMilli)); return; }
    var focused = document.activeElement && document.activeElement.classList && document.activeElement.classList.contains('p-add');
    setQty(p.id, cur + 1000, p);
    if (isPhone() && navigator.vibrate) { try { navigator.vibrate(8); } catch (e) { /* بی لرزش */ } }
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
    var t = el('span', { class: 'thumb' + (l.photo ? ' photo tile' : '') });
    if (l.photo) t.appendChild(el('img', { attrs: { src: photoUrl(id), alt: '', loading: 'lazy', width: '44', height: '44' } }));
    else t.textContent = String(l.name || '؟').slice(0, 1);
    return t;
  }

  // ── گوشی: مدلِ مرجعِ صاحب محصول (phone-v3 r3 بسته / r2 باز) ──
  //  با سبدِ پُر، پایهٔ صفحه سیاه است و کاتالوگ روی سطحِ روشن با گوشه‌های گردِ پایین؛
  //  زدنِ نوار سطح را بالا می‌برد و سبد روی پایهٔ سیاه پیدا می‌شود. کامپیوتر/تبلت همان کشو.
  var PHONE = window.matchMedia('(max-width: 599px)');
  var phoneOpen = false; var editing = ''; var lastQty = 0; var shownIds = {};
  function isPhone() { return PHONE.matches; }
  function cartOpen() { return phoneOpen || K.sheetOpen() === $('cart-sheet'); }
  /** پایهٔ سیاه فقط در گوشی، در کاتالوگ و با سبدِ پُر؛ جای پیمایش حفظ می‌شود. */
  function applyMode() {
    var onCatalog = !$('view-catalog').hidden;
    var want = isPhone() && ids().length > 0 && onCatalog;
    var on = document.body.classList.contains('phone-cart');
    if (want === on) return;
    var y = on ? $('surface').scrollTop : window.scrollY;
    document.body.classList.toggle('phone-cart', want);
    if (want) $('surface').scrollTop = y;
    else if (onCatalog) window.scrollTo(0, y);
  }
  function openCart() {
    renderSheet();
    if (!ids().length) return;
    if (document.body.classList.contains('phone-cart')) {
      if (!phoneOpen) {
        phoneOpen = true;
        $('cart-sheet').hidden = false; $('surface-cap').hidden = false;
        $('surface').inert = true;
        requestAnimationFrame(function () { document.body.classList.add('cart-open'); });
        setTimeout(function () { $('cart-title').focus(); }, 60);
      }
    } else if (K.sheetOpen() !== $('cart-sheet')) K.openSheet($('cart-sheet'), closeToCatalog);
    renderBar();
  }
  function closeCart() {
    editing = '';
    if (phoneOpen) {
      phoneOpen = false;
      document.body.classList.remove('cart-open');
      $('surface').inert = false;
      $('surface-cap').hidden = true;
      setTimeout(function () { if (!phoneOpen) $('cart-sheet').hidden = true; }, 360);
      renderBar();
      if (!$('cart-bar').hidden) $('cart-bar').focus();
    } else if (K.sheetOpen() === $('cart-sheet')) K.closeSheet(true);
  }
  function closeAll() { closeCart(); if (K.sheetOpen()) K.closeSheet(true); }

  function renderBar() {
    applyMode();
    var n = ids().length;
    var bar = $('cart-bar');
    var onCatalog = !$('view-catalog').hidden;
    bar.hidden = n === 0 || !onCatalog || (K.sheetOpen() === $('cart-sheet'));
    bar.inert = phoneOpen;
    document.body.classList.toggle('has-bar', !bar.hidden);
    if (!n) { lastQty = 0; shownIds = {}; return; }
    var th = K.clear($('bar-thumbs'));
    var seen = {};
    ids().slice(0, 3).forEach(function (id) {
      var t = thumb(id, cart[id]);
      if (!shownIds[id] && lastQty) t.classList.add('pop');
      seen[id] = true;
      th.appendChild(t);
    });
    shownIds = seen;
    $('bar-more').hidden = n <= 3;
    $('bar-more').textContent = '+' + K.fa(n - 3, 0);
    $('bar-total').textContent = K.money(subtotal());
    var count = $('open-cart');
    count.textContent = K.fa(n, 0);
    var q = ids().reduce(function (s, id) { return s + cart[id].qtyMilli; }, 0);
    if (lastQty && q > lastQty) { count.classList.remove('bump'); void count.offsetWidth; count.classList.add('bump'); }
    lastQty = q;
    $('bar-label').textContent = '، ' + K.fa(n, 0) + ' قلم، جمع ' + K.money(subtotal()) + ' — باز کردنِ سبد';
  }

  function renderSheet() {
    $('cart-shop').textContent = store ? store.title : '';
    var ul = K.clear($('sheet-lines'));
    ids().forEach(function (id) {
      var l = cart[id];
      var minus = el('button', { attrs: { type: 'button', 'aria-label': 'یکی کمتر ' + l.name }, on: { click: function () { setQty(id, l.qtyMilli - 1000); } } }, K.icon('minus'));
      var plus = el('button', { attrs: { type: 'button', 'aria-label': 'یکی بیشتر ' + l.name }, on: { click: function () { setQty(id, l.qtyMilli + 1000); } } }, K.icon('plus'));
      if (l.maxQtyMilli && l.qtyMilli + 1000 > l.maxQtyMilli) plus.disabled = true;
      var main = el('button', {
        class: 'cline-main', attrs: { type: 'button', 'aria-expanded': String(editing === id), 'aria-label': l.name + '، ' + K.qty(l.qtyMilli) + ' ' + (l.unit || 'عدد') + '، ' + K.money(K.lineTotal(l.qtyMilli, l.priceMinor)) + ' — تغییرِ تعداد' },
        on: { click: function () { editing = editing === id ? '' : id; renderSheet(); } },
      },
      thumb(id, l),
      el('span', { class: 'q num', text: K.qty(l.qtyMilli) + ' ×' }),
      el('span', { class: 'name' }, l.name, el('small', { text: l.unavailable ? 'دیگر در ویترین نیست — بردارید' : K.money(l.priceMinor) + (l.unit ? ' / ' + l.unit : '') })),
      el('span', { class: 'price num', text: K.money(K.lineTotal(l.qtyMilli, l.priceMinor)) }));
      ul.appendChild(el('li', { class: 'cline' + (l.unavailable ? ' bad' : '') + (editing === id ? ' editing' : '') },
        main, el('span', { class: 'stepper' }, minus, el('output', { text: K.qty(l.qtyMilli), attrs: { 'aria-live': 'polite' } }), plus)));
    });
    var sub = subtotal();
    var f = defaultFulfillment();
    var fee = feeFor(f);
    var d = K.clear($('sheet-deliv'));
    var txt = el('div', { class: 'txt' },
      el('b', {}, f === 'delivery' ? 'ارسال' : 'دریافت از دکان', el('span', { text: fee > 0 ? K.money(fee) : 'رایگان' })));
    if (store && store.minOrderMinor > sub) {
      txt.appendChild(el('p', { text: K.money(store.minOrderMinor - sub) + ' تا حداقلِ سفارش (' + K.money(store.minOrderMinor) + ')' }));
      var bar = el('i'); bar.style.setProperty('width', Math.min(100, Math.round(sub * 100 / store.minOrderMinor)) + '%');
      txt.appendChild(el('div', { class: 'progress', attrs: { role: 'progressbar', 'aria-valuemin': '0', 'aria-valuemax': '100', 'aria-valuenow': String(Math.min(100, Math.round(sub * 100 / store.minOrderMinor))), 'aria-label': 'پیشرفت تا حداقلِ سفارش' } }, bar));
    } else if (f === 'delivery' && store.fulfillment.pickup) {
      txt.appendChild(el('p', { text: 'یا هنگامِ ثبت «دریافت از دکان» را بزنید؛ رایگان است.' }));
    }
    d.append(el('span', { class: 'ico' }, K.icon(f === 'delivery' ? 'truck' : 'box')), txt);
    $('sheet-total').textContent = K.money(sub + fee);
    var block = '';
    if (hasBad()) block = 'کالاهای خط‌خورده دیگر در ویترین نیستند؛ اول آن‌ها را بردارید.';
    else if (store && store.minOrderMinor > sub) block = 'برای ادامه، سبد به حداقلِ سفارش برسد.';
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
        if (!p || !stockOf(p).orderable) { if (!l.unavailable) gone++; l.unavailable = true; return; }
        if (p.priceMinor !== l.priceMinor) changed++;
        l.unavailable = false; l.priceMinor = p.priceMinor; l.name = p.name; l.unit = p.unit; l.maxQtyMilli = p.maxQtyMilli || null; l.photo = Boolean(p.photo);
      });
      saveCart();
      if (changed) K.toast('قیمتِ ' + K.fa(changed, 0) + ' کالای سبد با قیمتِ امروز تازه شد.');
      else if (gone) K.toast(K.fa(gone, 0) + ' کالای سبد دیگر در ویترین نیست.');
    }).catch(function () { /* سبد همان می‌ماند؛ هنگامِ ثبت سرور دوباره می‌سنجد */ });
  }

  function closeToCatalog() {
    if (/^#\/(cart|p\/)/.test(location.hash)) history.replaceState(null, '', location.pathname + location.search + '#/');
    closeAll();
    renderBar();
  }

  // ════════════════════════════════════════════════════════════════
  //  ثبتِ سفارش (۲۳.۳، CHK)
  // ════════════════════════════════════════════════════════════════

  function fulfillment() {
    var r = document.querySelector('input[name="fulfillment"]:checked');
    return r ? r.value : '';
  }
  function verifiedFor(phone) { var t = K.ticket(); return Boolean(t && phone && t.phone === K.normPhone(phone)); }

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
    $('t-fee').textContent = f === 'delivery' ? (fee > 0 ? K.money(fee) : 'رایگان') : '—';
    $('t-fee-label').textContent = f === 'pickup' ? 'دریافت از دکان' : 'هزینهٔ ارسال';
    $('t-total').textContent = K.money(sub + fee);
    $('address-box').hidden = f !== 'delivery';
    var below = store.minOrderMinor > sub;
    if (below) K.note($('min-order'), 'warn', 'حداقلِ سفارش ' + K.money(store.minOrderMinor) + ' است.'); else $('min-order').hidden = true;
    renderVerify();
    var ok = verifiedFor($('c-phone').value);
    var btn = $('submit-order');
    btn.disabled = !ids().length || !f || below || hasBad() || !ok;
    btn.textContent = quoteNote ? 'پذیرفتنِ قیمتِ تازه و ثبت — ' + K.money(sub + fee) : 'ثبتِ سفارش — ' + K.money(sub + fee);
  }

  function renderVerify() {
    var ok = verifiedFor($('c-phone').value);
    $('verify-send').hidden = ok || !verifyAvailable;
    if (ok) { K.note($('verify-msg'), 'ok', 'شماره تأیید شده است.'); $('verify-code-row').hidden = true; $('verify-text').hidden = true; }
    else {
      $('verify-text').hidden = false;
      if (!verifyAvailable) {
        K.note($('verify-msg'), 'warn', 'تأییدِ شماره هنوز روی این سرور راه‌اندازی نشده است؛ فعلاً برای سفارش' + (store.contactPhone ? ' با شمارهٔ دکان (' + K.localPhone(store.contactPhone) + ')' : ' با خودِ دکان') + ' تماس بگیرید.');
      } else if ($('verify-msg').className.indexOf(' ok') >= 0) $('verify-msg').hidden = true;
    }
  }

  function sendCode() {
    var phone = $('c-phone').value;
    if (!phone.trim()) { K.note($('verify-msg'), 'err', 'اول شمارهٔ موبایل را بنویسید.'); $('c-phone').focus(); return; }
    var b = $('verify-send'); b.disabled = true;
    api('/verify/start', { method: 'POST', body: { phone: phone } }).then(function (r) {
      $('verify-code-row').hidden = false;
      K.note($('verify-msg'), 'info', 'کد فرستاده شد.' + (r.devCode ? ' (کدِ آزمایشی: ' + r.devCode + ')' : ''));
      $('v-code').focus();
    }).catch(function (e) {
      if (e.code === 'verification_unavailable') verifyAvailable = false;
      K.note($('verify-msg'), e.code === 'verification_unavailable' ? 'warn' : 'err', e.message);
    }).then(function () { b.disabled = false; renderCheckout(); });
  }

  function checkCode() {
    var b = $('verify-check'); b.disabled = true;
    api('/verify/check', { method: 'POST', body: { phone: $('c-phone').value, code: $('v-code').value } }).then(function (r) {
      K.setTicket(r);
      $('v-code').value = '';
      renderCheckout();
    }).catch(function (e) {
      //  ۲۳.۴: خطای شبکه «کدِ اشتباه» نیست
      K.note($('verify-msg'), 'err', e.code === 'network' ? e.message : (e.message || 'کد درست نیست'));
    }).then(function () { b.disabled = false; });
  }

  function takePin() {
    if (!navigator.geolocation) { $('pin-text').textContent = 'این مرورگر موقعیت نمی‌دهد؛ نشانی و نشانه بس است.'; return; }
    $('pin-text').textContent = 'در حالِ گرفتنِ موقعیت…';
    navigator.geolocation.getCurrentPosition(function (pos) {
      pin = { lat: Math.round(pos.coords.latitude * 1e6) / 1e6, lng: Math.round(pos.coords.longitude * 1e6) / 1e6 };
      $('pin-text').textContent = 'پین گرفته شد (دقتِ حدود ' + K.fa(Math.round(pos.coords.accuracy || 0), 0) + ' متر).';
      $('pin-btn').textContent = 'گرفتنِ دوبارهٔ پین';
    }, function () {
      pin = null;
      $('pin-text').textContent = 'موقعیت گرفته نشد؛ نشانی و نشانه بس است.';
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
      body.address = { text: $('a-text').value.trim(), landmark: $('a-landmark').value.trim() };
      if (pin) { body.address.lat = pin.lat; body.address.lng = pin.lng; }
      body.delivery = { callBeforeArrival: $('d-call').checked, leaveWith: $('d-leave').value.trim() };
    }
    return body;
  }

  function submitOrder(ev) {
    ev.preventDefault();
    var msg = $('checkout-msg');
    var body = orderBody();
    if (!body.customer.name) { K.note(msg, 'err', 'نام را بنویسید.'); $('c-name').focus(); return; }
    if (body.fulfillment === 'delivery' && !body.address.text && !body.address.landmark && !pin) {
      K.note(msg, 'err', 'نشانی، نشانهٔ نزدیک یا پین را بدهید.'); $('a-text').focus(); return;
    }
    var btn = $('submit-order');
    btn.disabled = true;
    K.note(msg, 'info', 'در حالِ ثبت…');
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
      location.hash = '#/o/' + r.order.token;
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
        quoteNote = 'قیمت عوض شده است. ' + (changed.length ? changed.join('؛ ') + '. ' : '') + 'مبلغِ تازه: ' + K.money(d.quote.totalMinor) + '. اگر می‌پذیرید، دوباره بزنید.';
        saveCart();
        K.note(msg, 'warn', quoteNote);
      } else if (e.code === 'product_unavailable' && d.unavailable) {
        d.unavailable.forEach(function (id) { if (cart[id]) cart[id].unavailable = true; });
        saveCart();
        K.note(msg, 'err', 'برخی کالاها دیگر در ویترین نیستند؛ آن‌ها را از سبد بردارید.');
      } else if (e.code === 'qty_over_limit') {
        K.note(msg, 'err', e.message + ' (حداکثر: ' + K.qty(d.maxQtyMilli) + ')');
      } else if (e.code === 'below_min_order') {
        K.note(msg, 'err', 'سبد از حداقلِ سفارش (' + K.money(d.minOrderMinor) + ') کمتر است.');
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
    document.title = 'ثبتِ سفارش — ' + store.title;
    if (!fulfillment()) {
      var f = defaultFulfillment();
      var r = document.querySelector('input[name="fulfillment"][value="' + f + '"]');
      if (r) r.checked = true;
    }
    var t = K.ticket();
    if (t && !$('c-phone').value) $('c-phone').value = K.localPhone(t.phone);
    var me = K.load('store-customer', null);
    if (me) {
      if (!$('c-name').value && me.name) $('c-name').value = me.name;
      if (!$('a-text').value && me.addressText) $('a-text').value = me.addressText;
      if (!$('a-landmark').value && me.landmark) $('a-landmark').value = me.landmark;
    }
    renderCheckout();
  }

  // ════════════════════════════════════════════════════════════════
  //  رسید و پیگیری (CHK-20/22، S136/S137)
  // ════════════════════════════════════════════════════════════════

  function renderOrder(o, token) {
    $('o-title').textContent = 'سفارشِ شمارهٔ ' + K.fa(o.orderNo, 0);
    K.clear($('o-pill')).appendChild(el('span', { class: 'pill ' + K.orderKind(o.status), text: K.ORDER[o.status] || o.status }));
    var why = o.statusReason && K.REASON[o.statusReason] ? ' — ' + K.REASON[o.statusReason] : '';
    $('o-status').textContent = (o.fulfillment === 'pickup' ? 'دریافت از دکان' : 'ارسال به نشانیِ شما') + '، ' + K.when(o.createdAt) + why;
    var steps = K.clear($('o-steps'));
    K.orderSteps(o).forEach(function (s) {
      var dot = el('span', { class: 'dot' });
      if (s.state === 'done') dot.appendChild(K.icon('check'));
      if (s.state === 'stop') dot.appendChild(K.icon('close'));
      steps.appendChild(el('li', { class: 'step ' + s.state, attrs: s.state === 'now' ? { 'aria-current': 'step' } : {} },
        dot, el('span', { class: 'lbl' }, el('b', { text: s.label }), s.at ? el('small', { text: K.when(s.at) }) : null)));
    });
    var ul = K.clear($('o-lines'));
    o.lines.forEach(function (l) {
      ul.appendChild(el('li', {}, el('span', { class: 'grow' }, l.name, el('small', { text: K.qty(l.qtyMilli) + ' ' + (l.unit || '') + ' × ' + K.money(l.unitPriceMinor) })),
        el('span', { class: 'amt num', text: K.money(l.lineTotalMinor) })));
    });
    var paid = o.paymentStatus === 'paid' ? o.totalMinor : 0;
    var rows = [['جمعِ کالاها', K.money(o.subtotalMinor)]];
    if (o.discountMinor) rows.push(['تخفیف', K.money(o.discountMinor)]);
    if (o.fulfillment === 'delivery') rows.push(['هزینهٔ ارسال', o.deliveryFeeMinor ? K.money(o.deliveryFeeMinor) : 'رایگان']);
    rows.push(['مبلغِ نهایی', K.money(o.totalMinor)], ['پرداخت‌شده', K.money(paid)], ['مانده (نقد هنگامِ تحویل)', K.money(o.totalMinor - paid)]);
    var t = K.clear($('o-totals'));
    rows.forEach(function (r) {
      var g = r[0] === 'مبلغِ نهایی' ? 'grand num' : 'num';
      t.append(el('span', { class: r[0] === 'مبلغِ نهایی' ? 'grand' : '', text: r[0] }), el('span', { class: g, text: r[1] }));
    });
    $('o-cancel').hidden = !o.canCancel;
    $('o-reorder').hidden = false;
    $('o-cancel').onclick = function () {
      K.confirm('این سفارش لغو شود؟ تا دکان قبول نکرده، لغو رایگان است.', 'لغو کن').then(function (yes) {
        if (!yes) return;
        api('/orders/' + encodeURIComponent(token) + '/cancel', { method: 'POST', body: {} }).then(function (r) {
          renderOrder(r.order, token);
          K.toast('سفارش لغو شد.');
        }).catch(function (e) { K.note($('o-msg'), 'err', e.message); });
      });
    };
    $('o-reorder').onclick = function () { reorder(o); };
    return o;
  }

  var FINAL = ['rejected', 'auto_rejected', 'cancelled_by_customer', 'cancelled', 'delivered', 'picked_up', 'restocked', 'refunded_full', 'refunded_partial'];
  function openOrder(token) {
    show('view-order');
    document.title = 'سفارش — ' + store.title;
    clearTimeout(pollTimer);
    $('o-msg').hidden = true;
    var placed = K.loadS('placed:' + token);
    if (placed) K.note($('o-placed'), 'ok', 'سفارش ثبت شد. دکان آن را بررسی می‌کند؛ همین صفحه وضعیت را نشان می‌دهد.');
    else $('o-placed').hidden = true;
    var load = function () {
      api('/orders/' + encodeURIComponent(token)).then(function (r) {
        renderOrder(r.order, token);
        //  پیگیریِ زنده فقط جلوی چشم و تا سفارش تمام نشده
        if (FINAL.indexOf(r.order.status) < 0) pollTimer = setTimeout(function () { if (!document.hidden && location.hash === '#/o/' + token) load(); else pollTimer = setTimeout(load, 20000); }, 20000);
      }).catch(function (e) {
        $('o-title').textContent = 'سفارش';
        K.clear($('o-pill')); K.clear($('o-steps')); K.clear($('o-lines')); K.clear($('o-totals'));
        $('o-status').textContent = e.status === 404 ? 'این سفارش پیدا نشد.' : e.message;
        $('o-cancel').hidden = true; $('o-reorder').hidden = true;
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
        if (!stockOf(p).orderable) return;
        catalog.items[p.id] = catalog.items[p.id] || p;
        var q = want[p.id]; if (p.maxQtyMilli && q > p.maxQtyMilli) q = p.maxQtyMilli;
        setQty(p.id, Math.max(q, cart[p.id] ? cart[p.id].qtyMilli : 0), p);
        added++;
      });
      var missing = Object.keys(want).length - added;
      location.hash = '#/cart';
      K.toast(missing ? K.fa(added, 0) + ' کالا با قیمتِ امروز به سبد رفت؛ ' + K.fa(missing, 0) + ' کالا دیگر نیست.' : 'همهٔ اقلام با قیمتِ امروز به سبد رفت.');
    }).catch(function (e) { K.note($('o-msg'), 'err', e.message); });
  }

  // ── سفارش‌های من در همین دکان ──
  function openOrders() {
    show('view-orders');
    document.title = 'سفارش‌های من — ' + store.title;
    var draw = function (list) {
      var ul = K.clear($('my-orders'));
      list.forEach(function (o) {
        var end = el('span', { class: 'end' });
        if (o.totalMinor !== undefined && o.totalMinor !== null) end.appendChild(el('span', { text: K.money(o.totalMinor) }));
        if (o.status) end.appendChild(el('span', { class: 'pill ' + K.orderKind(o.status), text: K.ORDER[o.status] || o.status }));
        ul.appendChild(el('li', {}, el('a', { class: 'card prow', attrs: { href: '#/o/' + o.token } },
          el('span', { class: 'pimg' }, K.icon('receipt')),
          el('span', { class: 'grow' }, el('b', { text: 'سفارشِ ' + K.fa(o.orderNo, 0) }), el('small', { text: K.when(o.at || o.createdAt) })),
          end)));
      });
      $('my-orders-empty').hidden = list.length > 0;
    };
    draw(K.load(KEY_ORDERS, []) || []);
    //  S138: با شمارهٔ تأییدشده، سفارش‌های همین شماره از هر دستگاه
    if (K.ticket()) api('/orders').then(function (r) { if (r.orders.length) draw(r.orders); }).catch(function () { /* همان محلی */ });
  }

  // ════════════════════════════════════════════════════════════════
  //  مسیر
  // ════════════════════════════════════════════════════════════════

  function route() {
    if (!store) return;
    var h = location.hash || '#/';
    if (h.indexOf('#/o/') === 0) { closeAll(); return openOrder(h.slice(4)); }
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
    $('nav-orders').appendChild(K.icon('receipt'));
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
      renderHead();
      renderCats();
      $('opt-delivery').hidden = !store.fulfillment.delivery;
      $('opt-pickup').hidden = !store.fulfillment.pickup;
      $('opt-delivery-fee').textContent = store.deliveryFeeMinor > 0 ? K.money(store.deliveryFeeMinor) : 'رایگان';
      var link = el('link', { attrs: { rel: 'manifest', href: '/store/' + encodeURIComponent(slug) + '/manifest.webmanifest' } });
      document.head.appendChild(link);
      if (ids().length) K.save(K.cartMetaKey(slug), { title: store.title, accent: store.accent, at: Date.now() });
      return Promise.all([loadProducts(false), refreshCart()]);
    }).then(route).catch(function (e) {
      if (e.status === 404) {
        show('view-missing');
        K.api('/api/market').then(function () { $('missing-market').hidden = false; }).catch(function () { /* بازارچه خاموش */ });
      } else { $('boot').textContent = e.message; }
    });
  }

  document.addEventListener('DOMContentLoaded', function () {
    $('search-form').addEventListener('submit', function (e) { e.preventDefault(); clearTimeout(searchTimer); catalog.q = $('q').value.trim(); loadProducts(false); });
    $('q').addEventListener('input', function () {
      clearTimeout(searchTimer);
      searchTimer = setTimeout(function () { catalog.q = $('q').value.trim(); loadProducts(false); }, 300);
    });
    $('clear-search').addEventListener('click', function () { catalog.q = ''; catalog.category = ''; $('q').value = ''; renderCats(); loadProducts(false); });
    $('more').addEventListener('click', function () { loadProducts(true); });
    $('cart-bar').addEventListener('click', function () { location.hash = '#/cart'; });
    $('cart-close').addEventListener('click', function () { closeCart(); closeToCatalog(); });
    $('surface-cap').addEventListener('click', function () { closeCart(); closeToCatalog(); });
    document.addEventListener('keydown', function (e) { if (e.key === 'Escape' && phoneOpen) { e.preventDefault(); closeCart(); closeToCatalog(); } });
    PHONE.addEventListener('change', function () { if (phoneOpen) { closeCart(); closeToCatalog(); } renderBar(); });
    $('surface').addEventListener('scroll', function () { document.querySelector('.appbar').classList.toggle('scrolled', $('surface').scrollTop > 120); }, { passive: true });
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
    window.addEventListener('scroll', function () { document.querySelector('.appbar').classList.toggle('scrolled', window.scrollY > 120); }, { passive: true });
    window.addEventListener('storage', function (e) { if (e.key === KEY_CART) { cart = sanitize(K.load(KEY_CART, {})); renderBar(); } });
    boot();
  });
})();
