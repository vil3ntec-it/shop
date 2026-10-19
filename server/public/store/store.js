/*
 * ویترینِ اینترنتی — صفحهٔ مشتری (فاز ۴؛ WEB، CHK، بخش‌های ۲۳، ۳۱.۱ و ۳۲.۳).
 *
 *   دیدن و سبد ── بی حساب (۲۳.۲). سبد فقط در همین مرورگر: شناسه، نام، مقدار، قیمت.
 *   ثبت ──────── فقط با شمارهٔ تأییدشده (کدِ یک‌بارمصرف) و کلیدِ یکتایی؛ کلید
 *                 تا وقتی بدنه همان است می‌ماند ⇒ زدنِ دوباره پس از قطعِ شبکه
 *                 سفارشِ دوم نمی‌سازد (CHK-19).
 *   قیمت ─────── را سرور می‌گوید؛ جمعِ دیده‌شده هم فرستاده می‌شود تا اگر عوض
 *                 شده بود، سفارش ثبت نشود و تغییر نشان داده شود (CHK-17).
 *   ⛔ بی اسکریپتِ درون‌خطی و بی `style=` (CSP)؛ همهٔ متن با textContent.
 */
(function () {
  'use strict';

  var $ = function (id) { return document.getElementById(id); };
  var fa = function (n, d) { return Number(n).toLocaleString('fa-AF', { maximumFractionDigits: d === undefined ? 2 : d }); };
  var money = function (minor) { return fa(minor / 100) + ' افغانی'; };
  var qty = function (milli) { return fa(milli / 1000, 3); };
  var store = null;
  var slug = slugOfPage();
  var KEY_CART = 'store-cart:' + slug;
  var KEY_ORDERS = 'store-orders:' + slug;
  var KEY_TICKET = 'store-ticket';
  var KEY_PENDING = 'store-pending:' + slug;

  function slugOfPage() {
    var parts = location.pathname.split('/').filter(Boolean);
    if (parts[0] === 'store' && parts[1]) return parts[1].toLowerCase();
    return location.hostname.split('.')[0].toLowerCase();
  }

  // ── حافظهٔ مرورگر (ممکن است بسته باشد؛ صفحه بی آن هم کار می‌کند) ──
  function load(key, def) {
    try { var v = JSON.parse(localStorage.getItem(key)); return v === null ? def : v; } catch (e) { return def; }
  }
  function save(key, v) { try { localStorage.setItem(key, JSON.stringify(v)); } catch (e) { /* بی حافظه */ } }
  function loadS(key) { try { return JSON.parse(sessionStorage.getItem(key)); } catch (e) { return null; } }
  function saveS(key, v) { try { sessionStorage.setItem(key, JSON.stringify(v)); } catch (e) { /* بی حافظه */ } }

  var cart = load(KEY_CART, {});
  function saveCart() { save(KEY_CART, cart); renderCartBar(); }

  // ── API ──
  function api(path, opts) {
    opts = opts || {};
    var headers = { 'Accept': 'application/json' };
    if (opts.body) headers['Content-Type'] = 'application/json';
    var t = ticket();
    if (t) headers['X-Store-Ticket'] = t.ticket;
    if (opts.key) headers['Idempotency-Key'] = opts.key;
    return fetch('/api/store/' + encodeURIComponent(slug) + path, {
      method: opts.method || 'GET', headers: headers, body: opts.body ? JSON.stringify(opts.body) : undefined,
      credentials: 'omit', cache: 'no-store',
    }).then(function (res) {
      return res.json().catch(function () { return {}; }).then(function (body) {
        if (res.ok) return body;
        var e = (body && body.error) || {};
        var err = new Error(e.message || 'خطا');
        err.status = res.status; err.code = e.code || ''; err.data = e;
        throw err;
      });
    }, function () {
      var err = new Error('اتصال برقرار نشد؛ اینترنت را ببینید و دوباره بزنید');
      err.status = 0; err.code = 'network';
      throw err;
    });
  }

  function ticket() {
    var t = load(KEY_TICKET, null);
    if (!t || !t.ticket || !(t.expiresAt > Date.now())) return null;
    return t;
  }

  function show(id) {
    ['view-missing', 'view-catalog', 'view-checkout', 'view-order', 'view-orders', 'view-chat'].forEach(function (v) {
      $(v).hidden = v !== id;
    });
    $('boot').hidden = true;
    $('cart-bar').hidden = id !== 'view-catalog' || count() === 0;
    window.scrollTo(0, 0);
  }

  function note(el, kind, text) {
    el.className = 'note ' + kind;
    el.textContent = text;
    el.hidden = !text;
  }

  // ── وضعیتِ دکان (۳۱.۱) — بی زمانِ ساختگی ──
  function clock(at, tz) {
    try {
      return new Intl.DateTimeFormat('fa-AF', { timeZone: tz, hour: '2-digit', minute: '2-digit', hourCycle: 'h23' }).format(new Date(at));
    } catch (e) { return ''; }
  }
  function hm(min) { return fa(Math.floor(min / 60), 0).padStart(2, '۰') + ':' + fa(min % 60, 0).padStart(2, '۰'); }

  var BADGE = {
    open_online: ['باز است · آنلاین', 'ok'],
    open_unconfirmed: ['باز است · اتصال تأیید نشده', 'warn'],
    declared_until: ['', 'ok'],
    closed: ['بسته است', 'off'],
    offline: ['موقتاً آفلاین', 'off'],
  };
  var ACCEPT = {
    accepting: 'سفارش همین حالا به دکان می‌رسد.',
    delayed: 'سفارش ثبت می‌شود، ولی ممکن است دیرتر بررسی شود.',
    later: 'سفارش ثبت می‌شود و پس از باز شدنِ دکان بررسی می‌شود.',
  };

  function renderStatus(s) {
    var box = $('store-status');
    box.textContent = '';
    if (!s) return;
    var b = BADGE[s.badge] || ['', 'warn'];
    var badge = document.createElement('span');
    badge.className = 'badge ' + b[1];
    badge.textContent = s.badge === 'declared_until'
      ? 'تا ساعتِ ' + clock(s.declaredUntil, s.timezone) + ' آنلاین اعلام شده'
      : b[0];
    box.appendChild(badge);
    var bits = [];
    bits.push(s.lastConfirmedAt
      ? 'آخرین تأییدِ اتصال: ' + clock(s.lastConfirmedAt, s.timezone)
      : 'اتصالِ دکان هنوز تأیید نشده');
    var hours = s.holiday ? 'امروز تعطیل' : (s.today && s.today.length
      ? 'امروز: ' + s.today.map(function (r) { return hm(r[0]) + '–' + hm(r[1]); }).join('، ')
      : (s.hoursSet ? 'امروز تعطیل' : ''));
    bits.push('ساعتِ دکان: ' + fa(Number(s.localTime.slice(0, 2)), 0).padStart(2, '۰') + ':' + fa(Number(s.localTime.slice(3)), 0).padStart(2, '۰') + (hours ? ' · ' + hours : ''));
    bits.forEach(function (t) { var x = document.createElement('span'); x.className = 'muted'; x.textContent = t; box.appendChild(x); });
    var acc = document.createElement('span');
    acc.textContent = ACCEPT[s.orderAcceptance] || '';
    box.appendChild(acc);
    if (s.notice) { var n = document.createElement('span'); n.textContent = s.notice; box.appendChild(n); }
  }

  // ── کاتالوگ ──
  var catalog = { q: '', category: '', cursor: null, items: {} };
  var searchTimer = 0;

  function renderCats() {
    var box = $('cats');
    box.textContent = '';
    [''].concat(store.categories || []).forEach(function (c) {
      var b = document.createElement('button');
      b.type = 'button'; b.className = 'chip'; b.textContent = c || 'همه';
      b.setAttribute('aria-pressed', String(c === catalog.category));
      b.addEventListener('click', function () { catalog.category = c; renderCats(); loadProducts(false); });
      box.appendChild(b);
    });
    box.hidden = !(store.categories || []).length;
  }

  function loadProducts(more) {
    var qs = '?limit=40' + (catalog.q ? '&q=' + encodeURIComponent(catalog.q) : '') +
      (catalog.category ? '&category=' + encodeURIComponent(catalog.category) : '') +
      (more && catalog.cursor ? '&cursor=' + encodeURIComponent(catalog.cursor) : '');
    return api('/products' + qs).then(function (r) {
      if (!more) $('products').textContent = '';
      r.items.forEach(function (p) { catalog.items[p.id] = p; $('products').appendChild(card(p)); });
      catalog.cursor = r.nextCursor;
      $('more').hidden = !r.nextCursor;
      var none = !more && !r.items.length;
      $('catalog-empty').hidden = !none;
      $('catalog-empty-text').textContent = catalog.q ? 'برای «' + catalog.q + '» چیزی پیدا نشد.' : 'هنوز کالایی در این ویترین نیست.';
      $('clear-search').hidden = !catalog.q && !catalog.category;
    }).catch(function (e) {
      $('catalog-empty').hidden = false;
      $('catalog-empty-text').textContent = e.message;
    });
  }

  function card(p) {
    var li = document.createElement('li');
    li.className = 'card';
    li.dataset.id = p.id;
    var img;
    if (p.photo) {
      img = document.createElement('img');
      img.loading = 'lazy'; img.decoding = 'async'; img.alt = p.name;
      img.width = 300; img.height = 300;
      img.src = '/api/store/' + encodeURIComponent(slug) + '/products/' + encodeURIComponent(p.id) + '/photo';
    } else {
      img = document.createElement('div');
      img.setAttribute('aria-hidden', 'true');
      img.textContent = p.name.slice(0, 1);
    }
    img.className = 'thumb';
    var h = document.createElement('h3'); h.textContent = p.name;
    var price = document.createElement('div'); price.className = 'price';
    price.textContent = money(p.priceMinor) + (p.unit ? ' / ' + p.unit : '');
    var stock = document.createElement('div'); stock.className = 'stock';
    //  ۳۲.۳: دادهٔ تازهٔ موجودی نیست ⇒ همین را می‌گوییم، نه «موجود»
    stock.textContent = p.stock && p.stock.status === 'unverified' ? 'موجودی در حال بررسی است' : '';
    var act = document.createElement('div');
    li.append(img, h, price, stock, act);
    renderStepper(act, p);
    return li;
  }

  function renderStepper(box, p) {
    box.textContent = '';
    var line = cart[p.id];
    if (!line) {
      var add = document.createElement('button');
      add.type = 'button'; add.className = 'btn btn-sm btn-block'; add.textContent = 'افزودن به سبد';
      add.setAttribute('aria-label', 'افزودنِ ' + p.name + ' به سبد');
      add.addEventListener('click', function () { setQty(p, 1000); renderStepper(box, p); });
      box.appendChild(add);
      return;
    }
    box.className = 'stepper';
    var minus = document.createElement('button');
    minus.type = 'button'; minus.className = 'btn btn-ghost icon-btn'; minus.textContent = '−';
    minus.setAttribute('aria-label', 'یکی کمتر ' + p.name);
    var out = document.createElement('output'); out.textContent = qty(line.qtyMilli);
    var plus = document.createElement('button');
    plus.type = 'button'; plus.className = 'btn icon-btn'; plus.textContent = '+';
    plus.setAttribute('aria-label', 'یکی بیشتر ' + p.name);
    minus.addEventListener('click', function () { setQty(p, line.qtyMilli - 1000); renderStepper(box, p); });
    plus.addEventListener('click', function () { setQty(p, line.qtyMilli + 1000); renderStepper(box, p); });
    box.append(minus, out, plus);
  }

  function setQty(p, milli) {
    if (p.maxQtyMilli && milli > p.maxQtyMilli) milli = p.maxQtyMilli;
    if (milli <= 0) delete cart[p.id];
    else cart[p.id] = { qtyMilli: milli, name: p.name, unit: p.unit, priceMinor: p.priceMinor, maxQtyMilli: p.maxQtyMilli || null };
    saveCart();
  }

  function count() { return Object.keys(cart).length; }
  function subtotal() {
    return Object.keys(cart).reduce(function (s, id) {
      var l = cart[id];
      //  همان گردکردنِ سرور: هر قلم به افغانیِ کامل (order-states.lineTotalMinor)
      return s + Math.floor((l.qtyMilli * l.priceMinor + 50000) / 100000) * 100;
    }, 0);
  }

  function renderCartBar() {
    var n = count();
    $('open-cart').textContent = 'سبد: ' + fa(n, 0) + ' قلم · ' + money(subtotal());
    $('cart-bar').hidden = n === 0 || $('view-catalog').hidden;
  }

  // ── سبد و ثبت ──
  function fulfillment() {
    var r = document.querySelector('input[name="fulfillment"]:checked');
    return r ? r.value : '';
  }

  function renderCheckout() {
    var ul = $('cart-lines');
    ul.textContent = '';
    Object.keys(cart).forEach(function (id) {
      var l = cart[id];
      var li = document.createElement('li');
      var name = document.createElement('span'); name.className = 'line-name';
      name.textContent = l.name + ' — ' + qty(l.qtyMilli) + ' ' + (l.unit || '') + ' × ' + money(l.priceMinor);
      if (l.unavailable) name.textContent += ' (دیگر در ویترین نیست)';
      var rm = document.createElement('button');
      rm.type = 'button'; rm.className = 'btn btn-ghost btn-sm'; rm.textContent = 'حذف';
      rm.setAttribute('aria-label', 'حذفِ ' + l.name + ' از سبد');
      rm.addEventListener('click', function () { delete cart[id]; saveCart(); renderCheckout(); });
      li.append(name, rm);
      ul.appendChild(li);
    });
    $('cart-empty').hidden = count() > 0;
    var fee = fulfillment() === 'delivery' ? store.deliveryFeeMinor : 0;
    $('t-subtotal').textContent = money(subtotal());
    $('t-fee').textContent = money(fee);
    $('t-fee').hidden = $('t-fee-label').hidden = fulfillment() !== 'delivery';
    $('t-total').textContent = money(subtotal() + fee);
    var short = store.minOrderMinor - subtotal();
    $('min-order').hidden = !(short > 0);
    $('min-order').textContent = short > 0 ? 'حداقلِ سفارش ' + money(store.minOrderMinor) + ' است؛ ' + money(short) + ' دیگر لازم است.' : '';
    $('address-box').hidden = fulfillment() !== 'delivery';
    renderVerify();
    $('submit-order').disabled = !count() || short > 0 || !verifiedFor($('c-phone').value) || !fulfillment();
  }

  function normPhone(v) {
    var s = String(v || '').replace(/[۰-۹]/g, function (d) { return String(d.charCodeAt(0) - 0x06F0); })
      .replace(/[٠-٩]/g, function (d) { return String(d.charCodeAt(0) - 0x0660); }).replace(/[\s\-().]/g, '').replace(/^00/, '+');
    if (!s) return '';
    if (s.charAt(0) === '0') s = '+93' + s.slice(1);
    else if (s.charAt(0) !== '+') s = '+93' + s;
    return s;
  }
  function verifiedFor(phone) { var t = ticket(); return Boolean(t && phone && t.phone === normPhone(phone)); }

  function renderVerify() {
    var ok = verifiedFor($('c-phone').value);
    $('verify-send').hidden = ok || !verifyAvailable;
    if (ok) note($('verify-msg'), 'ok', 'شماره تأیید شده است.');
    else if (!verifyAvailable) note($('verify-msg'), 'warn', 'تأییدِ شماره هنوز راه‌اندازی نشده؛ فعلاً برای سفارش با دکان تماس بگیرید.');
    else if ($('verify-msg').className.indexOf('ok') >= 0) $('verify-msg').hidden = true;
    if (ok) $('verify-code-row').hidden = true;
  }

  var verifyAvailable = true;

  function sendCode() {
    var phone = $('c-phone').value;
    if (!phone) { note($('verify-msg'), 'err', 'اول شمارهٔ موبایل را بنویسید.'); $('c-phone').focus(); return; }
    $('verify-send').disabled = true;
    api('/verify/start', { method: 'POST', body: { phone: phone } }).then(function (r) {
      $('verify-code-row').hidden = false;
      note($('verify-msg'), 'info', 'کد فرستاده شد.' + (r.devCode ? ' (کدِ آزمایشی: ' + r.devCode + ')' : ''));
      $('v-code').focus();
    }).catch(function (e) {
      if (e.code === 'verification_unavailable') verifyAvailable = false;
      note($('verify-msg'), 'err', e.message);
    }).then(function () {
      $('verify-send').disabled = false;
      renderCheckout();
    });
  }

  function checkCode() {
    api('/verify/check', { method: 'POST', body: { phone: $('c-phone').value, code: $('v-code').value } }).then(function (r) {
      save(KEY_TICKET, { phone: r.phone, ticket: r.ticket, expiresAt: r.expiresAt });
      $('v-code').value = '';
      renderCheckout();
    }).catch(function (e) {
      //  ۲۳.۴: خطای شبکه «کدِ اشتباه» نیست
      note($('verify-msg'), 'err', e.code === 'network' ? e.message : (e.message || 'کد درست نیست'));
    });
  }

  var pin = null;
  function takePin() {
    if (!navigator.geolocation) { $('pin-text').textContent = 'این مرورگر موقعیت نمی‌دهد؛ نشانی و نشانه بس است.'; return; }
    $('pin-text').textContent = 'در حالِ گرفتنِ موقعیت…';
    navigator.geolocation.getCurrentPosition(function (pos) {
      pin = { lat: Math.round(pos.coords.latitude * 1e6) / 1e6, lng: Math.round(pos.coords.longitude * 1e6) / 1e6 };
      $('pin-text').textContent = 'پین ثبت شد (' + fa(pin.lat, 5) + '، ' + fa(pin.lng, 5) + ')';
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
      lines: Object.keys(cart).map(function (id) { return { productId: id, qtyMilli: cart[id].qtyMilli }; }),
      note: $('c-note').value.trim(),
      expectedTotalMinor: subtotal() + (f === 'delivery' ? store.deliveryFeeMinor : 0),
    };
    if (f === 'delivery') {
      body.address = { text: $('a-text').value.trim(), landmark: $('a-landmark').value.trim() };
      if (pin) { body.address.lat = pin.lat; body.address.lng = pin.lng; }
      body.delivery = { callBeforeArrival: $('d-call').checked, leaveWith: $('d-leave').value.trim() };
    }
    return body;
  }

  /** کلید تا وقتی بدنه همان است می‌ماند (زدنِ دوباره پس از قطعِ شبکه). */
  function keyFor(body) {
    var json = JSON.stringify(body);
    var p = loadS(KEY_PENDING);
    if (p && p.json === json) return p.key;
    var rnd = (window.crypto && crypto.randomUUID) ? crypto.randomUUID()
      : Date.now().toString(36) + Math.random().toString(36).slice(2) + Math.random().toString(36).slice(2);
    var key = 'web-' + rnd;
    saveS(KEY_PENDING, { json: json, key: key });
    return key;
  }

  function submitOrder(ev) {
    ev.preventDefault();
    var msg = $('checkout-msg');
    var body = orderBody();
    if (!body.customer.name) { note(msg, 'err', 'نام را بنویسید.'); $('c-name').focus(); return; }
    var btn = $('submit-order');
    btn.disabled = true;
    note(msg, 'info', 'در حالِ ثبت…');
    api('/checkout', { method: 'POST', body: body, key: keyFor(body) }).then(function (r) {
      saveS(KEY_PENDING, null);
      var list = load(KEY_ORDERS, []);
      if (!list.some(function (o) { return o.token === r.order.token; })) {
        list.unshift({ token: r.order.token, orderNo: r.order.orderNo, at: r.order.createdAt });
        save(KEY_ORDERS, list.slice(0, 30));
      }
      cart = {}; saveCart();
      $('checkout-msg').hidden = true;
      location.hash = '#/o/' + r.order.token;
    }).catch(function (e) {
      var d = e.data || {};
      if (e.code === 'price_changed' && d.quote) {
        var changed = [];
        d.quote.lines.forEach(function (l) {
          if (cart[l.productId] && cart[l.productId].priceMinor !== l.unitPriceMinor) {
            changed.push(l.name + ': ' + money(cart[l.productId].priceMinor) + ' ← ' + money(l.unitPriceMinor));
            cart[l.productId].priceMinor = l.unitPriceMinor;
          }
        });
        saveCart(); renderCheckout();
        note(msg, 'warn', 'قیمت عوض شده است. ' + changed.join('؛ ') + ' — مبلغِ تازه: ' + money(d.quote.totalMinor) + '. اگر می‌پذیرید، دوباره «ثبتِ سفارش» را بزنید.');
      } else if (e.code === 'product_unavailable' && d.unavailable) {
        d.unavailable.forEach(function (id) { if (cart[id]) cart[id].unavailable = true; });
        saveCart(); renderCheckout();
        note(msg, 'err', 'برخی کالاها دیگر در ویترین نیستند؛ آن‌ها را از سبد بردارید.');
      } else if (e.code === 'qty_over_limit') {
        note(msg, 'err', e.message + ' (حداکثر: ' + qty(d.maxQtyMilli) + ')');
      } else if (e.code === 'verification_required' || e.code === 'phone_not_verified') {
        save(KEY_TICKET, null);
        note(msg, 'err', e.message);
      } else {
        note(msg, 'err', e.message);
      }
    }).then(function () { renderCheckout(); });
  }

  // ── رسید و پیگیری ──
  var STATUS = {
    new: 'ثبت شد — منتظرِ تأییدِ دکان', accepted: 'دکان قبول کرد', rejected: 'دکان نپذیرفت',
    auto_rejected: 'بی پاسخ ماند', cancelled_by_customer: 'شما لغو کردید', picking: 'در حالِ جمع‌آوری',
    packed: 'بسته‌بندی شد', ready_for_pickup: 'آمادهٔ دریافت از دکان', out_for_delivery: 'در راه',
    picked_up: 'تحویل گرفته شد', delivered: 'تحویل شد', cancelled: 'لغو شد', delivery_failed: 'تحویل نشد',
    retry_scheduled: 'تحویلِ دوباره', returned_to_store: 'به دکان برگشت', restocked: 'به دکان برگشت',
    return_requested: 'درخواستِ مرجوعی', refunded_partial: 'بخشی بازپرداخت شد', refunded_full: 'بازپرداخت شد',
  };
  var REASON = { out_of_stock: 'ناموجود', closed: 'دکان بسته بود', busy: 'دکان شلوغ بود', out_of_area: 'بیرون از محدوده', customer_request: 'به درخواستِ مشتری', no_show: 'نیامدید', other: '' };

  function when(at) {
    try { return new Date(at).toLocaleString('fa-AF', { dateStyle: 'short', timeStyle: 'short' }); } catch (e) { return ''; }
  }

  function renderOrder(o) {
    $('o-title').textContent = 'سفارشِ شمارهٔ ' + fa(o.orderNo, 0);
    $('o-status').textContent = (STATUS[o.status] || o.status) + (o.statusReason && REASON[o.statusReason] ? ' — ' + REASON[o.statusReason] : '');
    var ul = $('o-lines'); ul.textContent = '';
    o.lines.forEach(function (l) {
      var li = document.createElement('li');
      var a = document.createElement('span'); a.className = 'line-name';
      a.textContent = l.name + ' — ' + qty(l.qtyMilli) + ' ' + (l.unit || '') + ' × ' + money(l.unitPriceMinor);
      var b = document.createElement('span'); b.textContent = money(l.lineTotalMinor);
      li.append(a, b); ul.appendChild(li);
    });
    var paid = o.paymentStatus === 'paid' ? o.totalMinor : 0;
    var rows = [['جمعِ کالاها', money(o.subtotalMinor)]];
    if (o.discountMinor) rows.push(['تخفیف', money(o.discountMinor)]);
    if (o.fulfillment === 'delivery') rows.push(['هزینهٔ ارسال', money(o.deliveryFeeMinor)]);
    rows.push(['مبلغِ نهایی', money(o.totalMinor)], ['پرداخت‌شده', money(paid)], ['مانده (نقد هنگامِ تحویل)', money(o.totalMinor - paid)]);
    var t = $('o-totals'); t.textContent = '';
    rows.forEach(function (r, i) {
      var a = document.createElement('span'); a.textContent = r[0];
      var b = document.createElement('span'); b.textContent = r[1];
      if (i === 0 || r[0] === 'مبلغِ نهایی') { a.className = b.className = r[0] === 'مبلغِ نهایی' ? 'grand' : ''; }
      t.append(a, b);
    });
    var tl = $('o-timeline'); tl.textContent = '';
    o.timeline.forEach(function (e) {
      var li = document.createElement('li');
      li.textContent = when(e.at) + ' — ' + (STATUS[e.status] || e.status);
      tl.appendChild(li);
    });
    $('o-cancel').hidden = !o.canCancel;
    $('o-chat').hidden = !chat.available;
    $('o-chat').href = '#/chat/o/' + encodeURIComponent(o.orderNo);
  }

  function openOrder(token) {
    show('view-order');
    $('o-msg').hidden = true;
    api('/orders/' + encodeURIComponent(token)).then(function (r) { renderOrder(r.order); }).catch(function (e) {
      $('o-title').textContent = 'سفارش';
      $('o-status').textContent = e.status === 404 ? 'این سفارش پیدا نشد.' : e.message;
      $('o-lines').textContent = ''; $('o-totals').textContent = ''; $('o-timeline').textContent = '';
      $('o-cancel').hidden = true;
    });
    $('o-cancel').onclick = function () {
      if (!window.confirm('این سفارش لغو شود؟')) return;
      api('/orders/' + encodeURIComponent(token) + '/cancel', { method: 'POST', body: {} }).then(function (r) {
        renderOrder(r.order);
      }).catch(function (e) { note($('o-msg'), 'err', e.message); });
    };
  }

  function openOrders() {
    show('view-orders');
    var ul = $('my-orders'); ul.textContent = '';
    var local = load(KEY_ORDERS, []);
    var draw = function (list) {
      ul.textContent = '';
      list.forEach(function (o) {
        var li = document.createElement('li');
        var a = document.createElement('a');
        a.href = '#/o/' + o.token;
        a.textContent = 'سفارشِ ' + fa(o.orderNo, 0) + (o.at || o.createdAt ? ' — ' + when(o.at || o.createdAt) : '');
        li.appendChild(a);
        if (o.status) { var s = document.createElement('span'); s.className = 'muted'; s.textContent = STATUS[o.status] || o.status; li.appendChild(s); }
        ul.appendChild(li);
      });
      $('my-orders-empty').hidden = list.length > 0;
    };
    draw(local);
    //  S138: با شمارهٔ تأییدشده، سفارش‌های همین شماره از هر دستگاه
    if (ticket()) api('/orders').then(function (r) { if (r.orders.length) draw(r.orders); }).catch(function () { /* همان محلی */ });
  }

  // ════════════════════════════════════════════════════════════════════
  //  گفت‌وگو با دکان (v3 §۳۳؛ `/api/store/:slug/chat`)
  //
  //  ⛔ پیش‌فرض خاموش: تا سرور برای همین دکان چت را روشن نکرده (۴۰۴ ِ
  //  `chat_disabled`)، هیچ پیوند و دکمه‌ای از چت دیده نمی‌شود.
  //  هویت = همان بلیتِ شمارهٔ تأییدشده (X-Store-Ticket)؛ پیام‌ها در صفِ همین
  //  مرورگر می‌مانند تا اینترنت برگردد (TohidChatCore.Queue، clientId ⇒ بی تکرار).
  //  عکس روی همین دستگاه کوچک و بی EXIF می‌شود؛ صدا فقط اگر مرورگر Opus/AAC
  //  ضبط کند و کاربر اجازهٔ میکروفون بدهد (Permissions-Policy: microphone=(self)).
  // ════════════════════════════════════════════════════════════════════
  var Chat = window.TohidChatCore || null;
  var CHAT_WAIT = 20000;
  var CHAT_REASONS = { spam: 'هرزنامه', abuse: 'توهین یا آزار', fraud: 'کلاه‌برداری', other: 'دیگر' };
  //  همان سقف‌های پیش‌فرضِ سرور (config.shopChat)؛ سرورِ دیگرسقف ⇒ ۴۱۳ ⇒ «نرفت» با پیامِ سرور
  var chat = { available: false, limits: { imageMax: 1536 * 1024, thumbMax: 64 * 1024, audioMax: 3072 * 1024 }, thread: null, convId: '', gen: 0, ctrl: null, draining: {}, wake: 0, media: {}, local: {}, rec: null, voice: null, reportFor: '' };
  var chatBlobs = Chat ? new Chat.BlobStore('store-chat-outbox') : null;
  var chatQueue = Chat ? new Chat.Queue({
    load: function () { return load('store-chat-queue:' + slug, null); },
    save: function (items) { save('store-chat-queue:' + slug, items); },
  }) : null;

  function chatCall(method, path, opts) {
    opts = opts || {};
    var headers = { 'Accept': 'application/json' };
    var t = ticket();
    if (t) headers['X-Store-Ticket'] = t.ticket;
    var body;
    if (opts.raw) { headers['Content-Type'] = opts.raw.type || 'application/octet-stream'; body = opts.raw; }
    else if (opts.body) { headers['Content-Type'] = 'application/json'; body = JSON.stringify(opts.body); }
    return fetch('/api/store/' + encodeURIComponent(slug) + '/chat' + path, {
      method: method, headers: headers, body: body, signal: opts.signal, credentials: 'omit', cache: 'no-store',
    }).then(function (res) {
      if (res.ok) return /json/.test(res.headers.get('content-type') || '') ? res.json() : res.blob();
      return res.json().catch(function () { return {}; }).then(function (b) {
        var e = (b && b.error) || {};
        var err = new Error(e.message || 'خطا');
        err.status = res.status; err.code = e.code || '';
        throw err;
      });
    }, function () {
      var err = new Error('اتصال برقرار نشد؛ پیام‌ها در همین دستگاه می‌مانند و پس از وصل می‌روند');
      err.status = 0; err.code = 'network'; err.aborted = !!(opts.signal && opts.signal.aborted);
      throw err;
    });
  }

  function el(tag, cls, text) {
    var x = document.createElement(tag);
    if (cls) x.className = cls;
    if (text !== undefined && text !== null) x.textContent = text;
    return x;
  }
  function chatNote(text, kind) { note($('ch-banner'), kind || 'info', text || ''); }
  var kbOf = function (n) { return fa(Math.max(1, Math.round((n || 0) / 1024)), 0) + ' کیلوبایت'; };
  var mmss = function (ms) { var s = Math.round((ms || 0) / 1000); return fa(Math.floor(s / 60), 0) + ':' + fa(s % 60, 0).padStart(2, '۰'); };

  /** آیا این دکان چت دارد؟ ۴۰۴ ⇒ نه؛ ۲۰۰ یا ۴۰۱ (بلیت لازم) ⇒ بله. */
  function chatProbe() {
    if (!Chat) return Promise.resolve();
    return chatCall('GET', '/conversations?limit=1').then(function () { chat.available = true; }, function (e) {
      chat.available = e.status === 401;
    }).then(function () {
      $('store-chat').hidden = !chat.available;
      if (chat.available) chatFlushAll();
    });
  }

  function stopChatPoll() {
    chat.gen++;
    if (chat.ctrl) { try { chat.ctrl.abort(); } catch (e) { /* بسته */ } chat.ctrl = null; }
  }

  function chatSection(which) {
    ['ch-verify', 'ch-home', 'ch-thread'].forEach(function (id) { $(id).hidden = id !== which; });
  }

  function chatVerify() {
    chatSection('ch-verify');
    $('ch-title').textContent = 'گفت‌وگو با دکان';
    $('ch-sub').textContent = 'برای گفت‌وگو، شمارهٔ موبایلِ خود را با یک کدِ یک‌بارمصرف تأیید کنید.';
    var t = load(KEY_TICKET, null);
    if (t && t.phone && !$('ch-phone').value) $('ch-phone').value = t.phone.replace(/^\+93/, '0');
  }

  function chatSendCode() {
    var phone = $('ch-phone').value;
    if (!phone) { note($('ch-verify-msg'), 'err', 'اول شمارهٔ موبایل را بنویسید.'); return; }
    $('ch-send-code').disabled = true;
    api('/verify/start', { method: 'POST', body: { phone: phone } }).then(function (r) {
      $('ch-code-row').hidden = false;
      note($('ch-verify-msg'), 'info', 'کد فرستاده شد.' + (r.devCode ? ' (کدِ آزمایشی: ' + r.devCode + ')' : ''));
      $('ch-code').focus();
    }).catch(function (e) { note($('ch-verify-msg'), 'err', e.message); })
      .then(function () { $('ch-send-code').disabled = false; });
  }

  function chatCheckCode() {
    api('/verify/check', { method: 'POST', body: { phone: $('ch-phone').value, code: $('ch-code').value } }).then(function (r) {
      save(KEY_TICKET, { phone: r.phone, ticket: r.ticket, expiresAt: r.expiresAt });
      $('ch-code').value = '';
      $('ch-verify-msg').hidden = true;
      if (chatQueue && chatQueue.resumeAuth()) chatFlushAll();
      route();
    }).catch(function (e) { note($('ch-verify-msg'), 'err', e.message || 'کد درست نیست'); });
  }

  function openChatHome() {
    show('view-chat');
    stopChatPoll();
    if (!ticket()) return chatVerify();
    chatSection('ch-home');
    $('ch-title').textContent = 'گفت‌وگو با ' + (store ? store.title : 'دکان');
    $('ch-sub').textContent = 'سؤالی دربارهٔ کالا یا سفارش دارید؟ همین‌جا بنویسید.';
    var ul = $('ch-list');
    chatCall('GET', '/conversations').then(function (r) {
      ul.textContent = '';
      r.conversations.forEach(function (c) {
        var li = el('li');
        var a = el('a', '', c.orderNo ? 'دربارهٔ سفارشِ ' + fa(c.orderNo, 0) : 'گفت‌وگوی کلی با دکان');
        a.href = '#/chat/c/' + encodeURIComponent(c.id);
        li.appendChild(a);
        if (c.unread) li.appendChild(el('span', 'badge warn', fa(c.unread, 0) + ' پیامِ تازه'));
        ul.appendChild(li);
      });
      $('ch-empty').hidden = r.conversations.length > 0;
    }).catch(function (e) {
      if (e.status === 401) return chatVerify();
      $('ch-empty').hidden = false;
      $('ch-empty').textContent = e.message;
    });
  }

  function chatOpen(body) {
    show('view-chat');
    stopChatPoll();
    if (!ticket()) return chatVerify();
    var name = ($('c-name') && $('c-name').value.trim()) || '';
    if (name) body.name = name;
    chatSection('ch-thread');
    $('ch-msgs').textContent = '';
    chatNote('در حالِ باز کردن…');
    chatCall('POST', '/conversations', { body: body }).then(function (r) {
      location.replace('#/chat/c/' + encodeURIComponent(r.conversation.id));
    }).catch(function (e) {
      if (e.status === 401) return chatVerify();
      chatNote(e.status === 0 ? 'اتصال نیست؛ برای شروعِ گفت‌وگو اینترنت لازم است.' : e.message, 'err');
    });
  }

  function openChatThread(id) {
    show('view-chat');
    if (!ticket()) return chatVerify();
    if (chat.convId !== id || !chat.thread) { chat.thread = new Chat.Thread(); chat.convId = id; }
    stopChatPoll();
    chatSection('ch-thread');
    $('ch-report-form').hidden = true;
    chatNote('');
    paintChatHead();
    paintChat();
    chatPoll(++chat.gen);
    chatFlush(id);
  }

  function paintChatHead() {
    var c = chat.thread && chat.thread.conversation;
    $('ch-title').textContent = 'گفت‌وگو با ' + (store ? store.title : 'دکان');
    $('ch-sub').textContent = c && c.orderNo ? 'دربارهٔ سفارشِ ' + fa(c.orderNo, 0) : '';
    var blocked = !!(c && c.blockedShop);
    $('ch-block').textContent = blocked ? 'باز کردنِ پیام‌های این دکان' : 'بستنِ پیام‌های این دکان';
    var cannot = c && c.canSend === false;
    $('ch-text').disabled = cannot; $('ch-img').disabled = cannot; $('ch-send').disabled = cannot;
    var vt = Chat.voiceType();
    $('ch-mic').hidden = !vt; $('ch-mic').disabled = cannot;
    $('ch-hint').textContent = cannot ? 'این دکان فعلاً پیامِ تازه نمی‌پذیرد.'
      : vt ? '' : 'ضبطِ صدا در این مرورگر پشتیبانی نمی‌شود؛ متن و عکس کار می‌کند.';
  }

  function chatPoll(gen) {
    var fails = 0;
    var first = true;
    function loop() {
      if (gen !== chat.gen || !chat.thread) return;
      var t = chat.thread;
      var wait = first ? 0 : CHAT_WAIT;
      chat.ctrl = typeof AbortController === 'function' ? new AbortController() : null;
      chatCall('GET', '/conversations/' + encodeURIComponent(chat.convId) + '/messages?after=' + t.cursor + '&wait=' + wait,
        { signal: chat.ctrl ? chat.ctrl.signal : undefined }).then(function (res) {
        if (gen !== chat.gen) return;
        first = false; fails = 0;
        var r = t.apply(res);
        if (chatQueue) chatQueue.reconcile(chat.convId, (res.messages || []).filter(function (m) { return m.mine; }))
          .forEach(function (k) { chatBlobs.del(k); });
        if ($('ch-banner').className.indexOf('warn') >= 0) chatNote('');
        if (r.changed) { paintChatHead(); paintChat(); }
        chatReceipts();
        loop();
      }, function (e) {
        if (gen !== chat.gen || e.aborted) return;
        if (e.status === 401) return chatVerify();
        if (e.status === 404) { chatNote(e.message, 'err'); return; }
        fails++;
        chatNote(e.status ? e.message : 'اتصال نیست؛ پیام‌های شما در همین دستگاه می‌مانند و پس از وصل می‌روند.', 'warn');
        setTimeout(loop, Chat.backoffMs(fails));
      });
    }
    loop();
  }

  var chatReceiptBusy = false;
  function chatReceipts() {
    var t = chat.thread;
    if (!t || chatReceiptBusy) return;
    var need = t.receiptNeed(document.visibilityState === 'visible' && !$('view-chat').hidden);
    if (!need) return;
    chatReceiptBusy = true;
    chatCall('POST', '/conversations/' + encodeURIComponent(chat.convId) + '/receipts', { body: need }).then(function (r) {
      if (chat.thread === t) { t.conversation = r.conversation; paintChat(); }
    }, function () { /* دورِ بعد */ }).then(function () { chatReceiptBusy = false; });
  }

  function chatMediaUrl(id) {
    if (chat.media[id]) return Promise.resolve(chat.media[id]);
    return chatCall('GET', '/conversations/' + encodeURIComponent(chat.convId) + '/media/' + encodeURIComponent(id)).then(function (b) {
      chat.media[id] = URL.createObjectURL(b);
      return chat.media[id];
    });
  }
  function chatLocalUrl(key) {
    if (chat.local[key]) return Promise.resolve(chat.local[key]);
    return chatBlobs.get(key).then(function (b) {
      if (!b) return '';
      chat.local[key] = URL.createObjectURL(b);
      return chat.local[key];
    });
  }

  function chatMediaNode(m, local) {
    if (m.kind === 'image') {
      var b = el('button', 'chat-thumb');
      b.type = 'button';
      b.setAttribute('aria-label', 'نمایشِ عکسِ کامل');
      if (!local && m.media) b.dataset.full = m.media.id;
      var img = el('img');
      img.alt = 'عکس';
      var p = local ? chatLocalUrl(m.thumbKey || m.blobKey) : chatMediaUrl((m.media && (m.media.thumbId || m.media.id)) || '');
      p.then(function (u) { if (u) img.src = u; }, function () { img.alt = 'عکس باز نشد'; });
      b.appendChild(img);
      return b;
    }
    if (m.kind === 'audio') {
      var dur = m.meta && m.meta.durationMs ? ' · ' + mmss(m.meta.durationMs) : '';
      if (local || !m.media) return el('p', 'muted', 'پیامِ صوتی' + dur);
      var pb = el('button', 'btn btn-ghost btn-sm chat-play', 'پخشِ پیامِ صوتی' + dur);
      pb.type = 'button';
      pb.dataset.media = m.media.id;
      return pb;
    }
    if (m.kind === 'file') return el('p', 'muted', 'فایلِ PDF');
    return null;
  }

  function chatLink(text, data) {
    var b = el('button', 'link-btn', text);
    b.type = 'button';
    Object.keys(data).forEach(function (k) { b.dataset[k] = data[k]; });
    return b;
  }

  function paintChat() {
    var t = chat.thread;
    var list = $('ch-msgs');
    if (!t || !list) return;
    var near = list.scrollHeight - list.scrollTop - list.clientHeight < 80;
    var server = t.list();
    var have = {};
    server.forEach(function (m) { if (m.clientId) have[m.clientId] = 1; });
    var local = chatQueue ? chatQueue.forConv(chat.convId).filter(function (x) { return !have[x.clientId]; }) : [];
    var rc = t.conversation && t.conversation.receipts;
    list.textContent = '';
    server.forEach(function (m) {
      var mine = m.sender === 'customer';
      var li = el('li', 'chat-msg ' + (mine ? 'mine' : 'theirs'));
      li.dataset.id = m.id;
      var bub = el('div', 'bubble');
      if (!mine) bub.appendChild(el('b', 'chat-who', store ? store.title : 'دکان'));
      if (m.deleted) bub.appendChild(el('p', 'muted', 'این پیام پاک شد'));
      else {
        var mn = chatMediaNode(m, false);
        if (mn) bub.appendChild(mn);
        if (m.body) { var p = el('p', 'chat-text', m.body); p.dir = 'auto'; bub.appendChild(p); }
      }
      var meta = el('div', 'chat-meta');
      meta.appendChild(el('time', '', when(m.createdAt)));
      if (mine && !m.deleted) {
        var st = Chat.statusOf(m, rc);
        var s = el('span', 'chat-st ' + st, Chat.LABEL[st]);
        s.dataset.status = st;
        meta.appendChild(s);
      }
      if (!mine && !m.deleted) meta.appendChild(chatLink('گزارش', { report: m.id }));
      bub.appendChild(meta);
      li.appendChild(bub);
      list.appendChild(li);
    });
    local.forEach(function (x) {
      var li = el('li', 'chat-msg mine');
      li.dataset.client = x.clientId;
      var bub = el('div', 'bubble');
      var mn = chatMediaNode(x, true);
      if (mn) bub.appendChild(mn);
      if (x.body) { var p = el('p', 'chat-text', x.body); p.dir = 'auto'; bub.appendChild(p); }
      var meta = el('div', 'chat-meta');
      meta.appendChild(el('time', '', when(x.createdAt)));
      var s = el('span', 'chat-st ' + x.state, Chat.LABEL[x.state] + (x.state === 'failed' && x.error ? ' — ' + x.error : ''));
      s.dataset.status = x.state;
      meta.appendChild(s);
      if (x.state !== 'sending') {
        if (x.state !== 'queued') meta.appendChild(chatLink('دوباره', { retry: x.clientId }));
        meta.appendChild(chatLink('لغو', { cancel: x.clientId }));
      }
      bub.appendChild(meta);
      li.appendChild(bub);
      list.appendChild(li);
    });
    $('ch-none').hidden = server.length + local.length > 0;
    if (near || !list.dataset.painted) list.scrollTop = list.scrollHeight;
    list.dataset.painted = '1';
  }

  function onChatClick(e) {
    var b = e.target.closest && e.target.closest('button');
    if (!b) return;
    if (b.dataset.retry) { chatQueue.retry(b.dataset.retry); paintChat(); chatFlush(chat.convId); return; }
    if (b.dataset.cancel) {
      if (!window.confirm('این پیام فرستاده نشود؟')) return;
      (chatQueue.cancel(b.dataset.cancel) || []).forEach(function (k) { chatBlobs.del(k); });
      paintChat();
      return;
    }
    if (b.dataset.report) { openChatReport(b.dataset.report); return; }
    if (b.dataset.full) {
      b.disabled = true;
      chatMediaUrl(b.dataset.full).then(function (u) {
        var img = el('img', 'chat-full'); img.src = u; img.alt = 'عکس'; b.replaceWith(img);
      }, function (err) { b.disabled = false; chatNote(err.message, 'err'); });
      return;
    }
    if (b.dataset.media) {
      b.disabled = true;
      chatMediaUrl(b.dataset.media).then(function (u) {
        var a = el('audio'); a.controls = true; a.src = u; b.replaceWith(a);
        var p = a.play(); if (p && p.catch) p.catch(function () { /* کاربر خودش می‌زند */ });
      }, function (err) { b.disabled = false; chatNote(err.message, 'err'); });
    }
  }

  // ── ارسال ──
  function chatEnqueue(msg) {
    if (!chatQueue || !chat.convId) return;
    chatQueue.add(chat.convId, msg);
    paintChat();
    chatFlush(chat.convId);
  }

  function chatSendText(ev) {
    if (ev) ev.preventDefault();
    var body = $('ch-text').value.trim();
    if (!body) return;
    $('ch-text').value = '';
    chatEnqueue({ kind: 'text', body: body });
  }

  function chatSendImage(file) {
    chatNote('آماده کردنِ عکس…');
    Chat.prepareImage(file, chat.limits).then(function (out) {
      var id = Chat.newClientId();
      return chatBlobs.put(id + ':main', out.main).then(function () { return chatBlobs.put(id + ':thumb', out.thumb); }).then(function () {
        var caption = $('ch-text').value.trim();
        $('ch-text').value = '';
        chatEnqueue({ clientId: id, kind: 'image', body: caption, blobKey: id + ':main', thumbKey: id + ':thumb', size: out.main.size, meta: { width: out.width, height: out.height } });
        chatNote('عکس آماده شد (' + kbOf(out.main.size) + ')', 'ok');
      });
    }).catch(function (e) { chatNote(e.message || 'عکس آماده نشد', 'err'); });
  }

  function chatUpload(item, key, role) {
    return chatBlobs.get(key).then(function (blob) {
      if (!blob) { var e = new Error('فایلِ این پیام روی این دستگاه نمانده'); e.status = 410; throw e; }
      return chatCall('POST', '/conversations/' + encodeURIComponent(item.convId) + '/media?role=' + role, { raw: blob });
    }).then(function (r) { return r.mediaId; });
  }

  function chatSendItem(item) {
    var p = Promise.resolve();
    if (item.kind !== 'text') {
      if (!item.mediaId) p = p.then(function () { return chatUpload(item, item.blobKey, 'main'); }).then(function (id) { chatQueue.patch(item, { mediaId: id }); });
      if (item.thumbKey) p = p.then(function () { return item.thumbId ? null : chatUpload(item, item.thumbKey, 'thumb').then(function (id) { chatQueue.patch(item, { thumbId: id }); }); });
    }
    return p.then(function () {
      var body = { clientId: item.clientId, kind: item.kind, body: item.body };
      if (item.mediaId) body.mediaId = item.mediaId;
      if (item.thumbId) body.thumbId = item.thumbId;
      if (item.meta && Object.keys(item.meta).length) body.meta = item.meta;
      return chatCall('POST', '/conversations/' + encodeURIComponent(item.convId) + '/messages', { body: body });
    }).then(function (r) { return r.message; });
  }

  function chatFlush(convId) {
    if (!chatQueue || chat.draining[convId] || !ticket()) return;
    chat.draining[convId] = true;
    Chat.drain(chatQueue, convId, chatSendItem, {
      onChange: function () { if (chat.convId === convId) paintChat(); },
      onSent: function (item, message, keys) {
        keys.forEach(function (k) { chatBlobs.del(k); });
        if (chat.convId === convId && chat.thread && message) { chat.thread.apply({ messages: [message] }); paintChat(); }
      },
    }).then(function () {
      chat.draining[convId] = false;
      clearTimeout(chat.wake);
      var at = chatQueue.nextWake();
      if (at !== null) chat.wake = setTimeout(chatFlushAll, Math.max(0, at - Date.now()) + 50);
    });
  }
  function chatFlushAll() { if (chatQueue) chatQueue.convs().forEach(chatFlush); }

  // ── صدا ──
  function chatStartRec() {
    var type = Chat.voiceType();
    if (!type || chat.rec) return;
    navigator.mediaDevices.getUserMedia({ audio: true }).then(function (stream) {
      var chunks = [];
      var rec = new MediaRecorder(stream, { mimeType: type, audioBitsPerSecond: 24000 });
      var started = Date.now();
      rec.ondataavailable = function (e) { if (e.data && e.data.size) chunks.push(e.data); };
      rec.onstop = function () {
        stream.getTracks().forEach(function (t) { t.stop(); });
        var cancelled = chat.rec && chat.rec.cancelled;
        chat.rec = null;
        chat.voice = cancelled ? null : { blob: new Blob(chunks, { type: type.split(';')[0] }), ms: Date.now() - started };
        paintChatVoice();
      };
      chat.rec = { rec: rec, started: started, timer: setInterval(paintChatVoice, 500), limit: setTimeout(chatStopRec, Chat.VOICE_MAX_MS) };
      rec.start(1000);
      paintChatVoice();
    }, function () { chatNote('بی اجازهٔ میکروفون صدا ضبط نمی‌شود.', 'err'); });
  }
  function chatStopRec() {
    if (!chat.rec) return;
    clearInterval(chat.rec.timer); clearTimeout(chat.rec.limit);
    try { chat.rec.rec.stop(); } catch (e) { /* ایستاده */ }
  }
  function chatCancelVoice() {
    if (chat.rec) { chat.rec.cancelled = true; chatStopRec(); }
    chat.voice = null;
    paintChatVoice();
  }
  function paintChatVoice() {
    var box = $('ch-voice');
    box.textContent = '';
    $('ch-mic').textContent = chat.rec ? 'پایانِ ضبط' : 'پیامِ صوتی';
    if (chat.rec) {
      box.hidden = false;
      box.appendChild(el('span', 'chat-rec', '● در حالِ ضبط ' + mmss(Date.now() - chat.rec.started)));
      var c = el('button', 'btn btn-ghost btn-sm', 'لغوِ ضبط'); c.type = 'button'; c.onclick = chatCancelVoice;
      box.appendChild(c);
      return;
    }
    if (!chat.voice) { box.hidden = true; return; }
    var cap = chat.limits.audioMax || 0;
    var big = cap && chat.voice.blob.size > cap;
    box.hidden = false;
    box.appendChild(el('span', '', 'پیامِ صوتی ' + mmss(chat.voice.ms) + ' · ' + kbOf(chat.voice.blob.size)));
    var a = el('audio'); a.controls = true; a.src = URL.createObjectURL(chat.voice.blob); box.appendChild(a);
    var s = el('button', 'btn btn-sm', 'فرستادنِ صدا'); s.type = 'button'; s.disabled = !!big; s.onclick = chatSendVoice; box.appendChild(s);
    var x = el('button', 'btn btn-ghost btn-sm', 'لغو'); x.type = 'button'; x.onclick = chatCancelVoice; box.appendChild(x);
    if (big) box.appendChild(el('span', 'chat-st failed', 'بزرگ‌تر از سقفِ مجاز'));
  }
  function chatSendVoice() {
    var v = chat.voice;
    if (!v) return;
    var id = Chat.newClientId();
    chat.voice = null;
    paintChatVoice();
    chatBlobs.put(id + ':main', v.blob).then(function () {
      chatEnqueue({ clientId: id, kind: 'audio', body: '', blobKey: id + ':main', size: v.blob.size, meta: { durationMs: Math.max(1, Math.round(v.ms)) } });
    });
  }

  // ── بستن و گزارش ──
  function chatToggleBlock() {
    var c = chat.thread && chat.thread.conversation;
    if (!c) return;
    var on = !c.blockedShop;
    if (on && !window.confirm('دیگر پیامی از این دکان نگیرید؟ هر وقت بخواهید باز می‌شود.')) return;
    chatCall(on ? 'PUT' : 'DELETE', '/conversations/' + encodeURIComponent(c.id) + '/block').then(function (r) {
      chat.thread.conversation = r.conversation; paintChatHead();
      chatNote(on ? 'پیام‌های این دکان بسته شد.' : 'پیام‌های این دکان دوباره باز شد.', 'ok');
    }, function (e) { chatNote(e.message, 'err'); });
  }
  function openChatReport(messageId) {
    chat.reportFor = messageId || '';
    $('ch-report-title').textContent = messageId ? 'گزارشِ این پیام به مدیرِ سامانه' : 'گزارشِ این گفت‌وگو به مدیرِ سامانه';
    $('ch-report-form').hidden = false;
    $('ch-reason').focus();
  }
  function chatSubmitReport(ev) {
    ev.preventDefault();
    var body = { reason: $('ch-reason').value, note: $('ch-rnote').value.trim() };
    if (chat.reportFor) body.messageId = chat.reportFor;
    chatCall('POST', '/conversations/' + encodeURIComponent(chat.convId) + '/reports', { body: body }).then(function (r) {
      $('ch-report-form').hidden = true;
      $('ch-rnote').value = '';
      chatNote(r.duplicate ? 'این مورد پیش‌تر گزارش شده بود.' : 'گزارش رسید؛ مدیرِ سامانه بررسی می‌کند.', 'ok');
    }, function (e) { chatNote(e.message, 'err'); });
  }

  function wireChat() {
    if (!Chat) return;
    Object.keys(CHAT_REASONS).forEach(function (k) { var o = el('option', '', CHAT_REASONS[k]); o.value = k; $('ch-reason').appendChild(o); });
    $('ch-send-code').addEventListener('click', chatSendCode);
    $('ch-check').addEventListener('click', chatCheckCode);
    $('ch-new').addEventListener('click', function () { chatOpen({}); });
    $('ch-form').addEventListener('submit', chatSendText);
    $('ch-text').addEventListener('keydown', function (e) { if (e.key === 'Enter' && !e.shiftKey && !e.isComposing) chatSendText(e); });
    $('ch-img').addEventListener('click', function () { $('ch-file').click(); });
    $('ch-file').addEventListener('change', function (e) { var f = e.target.files && e.target.files[0]; e.target.value = ''; if (f) chatSendImage(f); });
    $('ch-mic').addEventListener('click', function () { if (chat.rec) chatStopRec(); else chatStartRec(); });
    $('ch-msgs').addEventListener('click', onChatClick);
    $('ch-block').addEventListener('click', chatToggleBlock);
    $('ch-report').addEventListener('click', function () { openChatReport(''); });
    $('ch-report-cancel').addEventListener('click', function () { $('ch-report-form').hidden = true; });
    $('ch-report-form').addEventListener('submit', chatSubmitReport);
    window.addEventListener('online', function () { if (chatQueue) { chatQueue.wake(); chatFlushAll(); } });
    document.addEventListener('visibilitychange', function () { if (document.visibilityState === 'visible') chatReceipts(); });
  }

  // ── مسیر ──
  function route() {
    if (!store) return;
    var h = location.hash || '#/';
    if (h.indexOf('#/o/') === 0) return openOrder(h.slice(4));
    if (h.indexOf('#/chat') !== 0) stopChatPoll();
    if (h === '#/orders') return openOrders();
    if (Chat && h === '#/chat') return openChatHome();
    if (Chat && h.indexOf('#/chat/o/') === 0) return chatOpen({ orderNo: decodeURIComponent(h.slice(9)) });
    if (Chat && h.indexOf('#/chat/c/') === 0) return openChatThread(decodeURIComponent(h.slice(9)));
    if (h === '#/cart') { show('view-checkout'); return renderCheckout(); }
    show('view-catalog');
    renderCartBar();
    Object.keys(catalog.items).forEach(function (id) {
      var li = document.querySelector('#products li[data-id="' + CSS.escape(id) + '"] > div:last-child');
      if (li) renderStepper(li, catalog.items[id]);
    });
  }

  function boot() {
    if (!/^[a-z0-9][a-z0-9-]{1,30}[a-z0-9]$/.test(slug)) { show('view-missing'); return; }
    api('').then(function (r) {
      store = r.store;
      verifyAvailable = !r.verification || r.verification.available !== false;
      document.title = store.title;
      $('store-title').textContent = store.title;
      $('store-tagline').textContent = store.tagline || '';
      $('store-tagline').hidden = !store.tagline;
      if (store.contactPhone) { $('store-call').href = 'tel:' + store.contactPhone; $('store-call').hidden = false; }
      if (store.accent) document.documentElement.style.setProperty('--primary', store.accent);
      renderStatus(store.status);
      renderCats();
      $('opt-delivery').hidden = !store.fulfillment.delivery;
      $('opt-pickup').hidden = !store.fulfillment.pickup;
      var only = store.fulfillment.delivery && !store.fulfillment.pickup ? 'delivery'
        : (!store.fulfillment.delivery && store.fulfillment.pickup ? 'pickup' : '');
      if (only) document.querySelector('input[name="fulfillment"][value="' + only + '"]').checked = true;
      var t = ticket();
      if (t && !$('c-phone').value) $('c-phone').value = t.phone.replace(/^\+93/, '0');
      return Promise.all([loadProducts(false), chatProbe()]);
    }).then(route).catch(function (e) {
      if (e.status === 404) show('view-missing');
      else { $('boot').textContent = e.message; }
    });
  }

  document.addEventListener('DOMContentLoaded', function () {
    $('search-form').addEventListener('submit', function (e) { e.preventDefault(); catalog.q = $('q').value.trim(); loadProducts(false); });
    $('q').addEventListener('input', function () {
      clearTimeout(searchTimer);
      searchTimer = setTimeout(function () { catalog.q = $('q').value.trim(); loadProducts(false); }, 300);
    });
    $('clear-search').addEventListener('click', function () { catalog.q = ''; catalog.category = ''; $('q').value = ''; renderCats(); loadProducts(false); });
    $('more').addEventListener('click', function () { loadProducts(true); });
    Array.prototype.forEach.call(document.querySelectorAll('input[name="fulfillment"]'), function (r) { r.addEventListener('change', renderCheckout); });
    $('c-phone').addEventListener('input', renderCheckout);
    $('verify-send').addEventListener('click', sendCode);
    $('verify-check').addEventListener('click', checkCode);
    $('pin-btn').addEventListener('click', takePin);
    $('checkout-form').addEventListener('submit', submitOrder);
    window.addEventListener('hashchange', route);
    wireChat();
    window.addEventListener('storage', function (e) { if (e.key === KEY_CART) { cart = load(KEY_CART, {}); route(); } });
    boot();
  });
})();
