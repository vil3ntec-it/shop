/*
 * ویترینِ اینترنتی — صفحهٔ مشتری (فاز ۴؛ WEB، CHK، بخش‌های ۲۳، ۳۱.۱ و ۳۲.۳).
 *
 *   دیدن و سبد ── بی حساب (۲۳.۲). سبد فقط در همین مرورگر: شناسه، نام، مقدار، قیمت.
 *   ثبت ──────── فقط با شمارهٔ تأییدشده (کدِ یک‌بارمصرف) و کلیدِ یکتایی؛ کلید
 *                 تا وقتی بدنه همان است می‌ماند ⇒ زدنِ دوباره پس از قطعِ شبکه
 *                 سفارشِ دوم نمی‌سازد (CHK-19).
 *   قیمت ─────── را سرور می‌گوید؛ جمعِ دیده‌شده هم فرستاده می‌شود تا اگر عوض
 *                 شده بود، سفارش ثبت نشود و تغییر نشان داده شود (CHK-17).
 *   گامِ ۲ ──── صفحهٔ کالا، پرفروش/تازه، عکسِ کوچک (WEB-02/08/16)، ناحیه و
 *                 هزینهٔ ارسال با پین (CHK-07/10)، بازهٔ زمانی (CHK-12)، حسابِ
 *                 مشتری با نشانی و تاریخچه، خریدِ دوباره (۲۳، CHK-09، WEB-12).
 *                 ظاهرِ نوار و برگهٔ سبد مالِ صفحهٔ گوشی است؛ این‌جا فقط داده.
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
  //  نشستِ حسابِ مشتری — همان حساب در همهٔ ویترین‌های همین مبدأ
  var KEY_SESSION = 'store-session';

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
    var se = session();
    if (se) headers['X-Store-Session'] = se.session;
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
        //  نشستِ منقضی یا لغوشده ⇒ این دستگاه دیگر واردشده نیست
        if (err.code === 'session_required') save(KEY_SESSION, null);
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

  function session() {
    var t = load(KEY_SESSION, null);
    if (!t || !t.session || !(t.expiresAt > Date.now())) return null;
    return t;
  }

  function show(id) {
    ['view-missing', 'view-catalog', 'view-product', 'view-checkout', 'view-order', 'view-orders', 'view-account'].forEach(function (v) {
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
      renderShelves();
    }).catch(function (e) {
      $('catalog-empty').hidden = false;
      $('catalog-empty-text').textContent = e.message;
    });
  }

  // ── عکس، موجودی و کارت ──
  function imgSrc(id, size) {
    return '/api/store/' + encodeURIComponent(slug) + '/products/' + encodeURIComponent(id) + '/' + (size === 'thumb' ? 'thumb' : 'photo');
  }
  function picture(p, size, px) {
    var img;
    if (p.photo) {
      img = document.createElement('img');
      img.loading = 'lazy'; img.decoding = 'async'; img.alt = p.name;
      img.width = px; img.height = px;
      img.src = imgSrc(p.id, size);
    } else {
      img = document.createElement('div');
      img.setAttribute('aria-hidden', 'true');
      img.textContent = p.name.slice(0, 1);
    }
    img.className = 'thumb';
    return img;
  }
  function soldOut(p) { return Boolean(p.stock && (p.stock.status === 'out_of_stock' || p.stock.status === 'unavailable')); }
  //  ۳۲.۳: دادهٔ تازهٔ موجودی نیست ⇒ همین را می‌گوییم، نه «موجود»
  function stockText(st) {
    if (!st) return '';
    if (st.status === 'unverified') return 'موجودی در حال بررسی است';
    if (st.status === 'in_stock') return 'موجود';
    if (st.status === 'in_warehouse') return 'موجود در انبار' + (st.prepMinutes ? ' · آماده‌سازی حدودِ ' + fa(st.prepMinutes, 0) + ' دقیقه' : '');
    if (st.status === 'out_of_stock' || st.status === 'unavailable') return 'ناموجود';
    return '';
  }

  function card(p) {
    var li = document.createElement('li');
    li.className = 'card';
    li.dataset.id = p.id;
    var link = document.createElement('a');
    link.className = 'card-link';
    link.href = '#/p/' + encodeURIComponent(p.id);
    var h = document.createElement('h3'); h.textContent = p.name;
    link.append(picture(p, 'thumb', 300), h);
    var price = document.createElement('div'); price.className = 'price';
    price.textContent = money(p.priceMinor) + (p.unit ? ' / ' + p.unit : '');
    var stock = document.createElement('div'); stock.className = 'stock';
    stock.textContent = stockText(p.stock);
    if (soldOut(p)) stock.classList.add('out');
    var act = document.createElement('div');
    li.append(link, price, stock, act);
    renderStepper(act, p);
    return li;
  }

  /** ردیفِ افقیِ کالا (پرفروش، تازه، مشابه، مکمل) — خالی ⇒ پنهان. */
  function shelf(section, items) {
    var ul = section.querySelector('.shelf-row');
    ul.textContent = '';
    (items || []).forEach(function (p) {
      catalog.items[p.id] = catalog.items[p.id] || p;
      var li = document.createElement('li');
      var a = document.createElement('a');
      a.className = 'mini';
      a.href = '#/p/' + encodeURIComponent(p.id);
      var n = document.createElement('span'); n.className = 'mini-name'; n.textContent = p.name;
      var pr = document.createElement('span'); pr.className = 'mini-price'; pr.textContent = money(p.priceMinor);
      a.append(picture(p, 'thumb', 160), n, pr);
      li.appendChild(a);
      ul.appendChild(li);
    });
    section.hidden = !(items && items.length);
  }

  var highlights = null;
  function renderShelves() {
    var home = !catalog.q && !catalog.category;
    if (!home || !highlights) { $('shelf-best').hidden = true; $('shelf-new').hidden = true; return; }
    shelf($('shelf-best'), highlights.bestsellers);
    shelf($('shelf-new'), highlights.newArrivals);
  }
  function loadHighlights() {
    return api('/highlights').then(function (r) { highlights = r; renderShelves(); }).catch(function () { /* بی ردیف */ });
  }

  // ── WEB-08: صفحهٔ کالا ──
  function openProduct(id) {
    show('view-product');
    ['p-name', 'p-price', 'p-stock', 'p-desc', 'p-max'].forEach(function (k) { $(k).textContent = ''; });
    $('p-media').textContent = ''; $('p-act').textContent = ''; $('p-specs').textContent = '';
    $('p-desc').hidden = $('p-specs').hidden = $('p-max').hidden = $('p-msg').hidden = true;
    $('p-similar').hidden = $('p-complementary').hidden = true;
    api('/products/' + encodeURIComponent(id)).then(function (r) {
      var p = r.product;
      catalog.items[p.id] = p;
      document.title = p.name + ' · ' + store.title;
      $('p-media').appendChild(picture(p, 'display', 600));
      $('p-name').textContent = p.name;
      $('p-price').textContent = money(p.priceMinor) + (p.unit ? ' / ' + p.unit : '');
      $('p-stock').textContent = stockText(p.stock);
      $('p-stock').classList.toggle('out', soldOut(p));
      if (p.maxQtyMilli) { $('p-max').textContent = 'حداکثر در هر سفارش: ' + qty(p.maxQtyMilli) + ' ' + (p.unit || ''); $('p-max').hidden = false; }
      if (p.description) { $('p-desc').textContent = p.description; $('p-desc').hidden = false; }
      (p.specs || []).forEach(function (x) {
        var dt = document.createElement('dt'); dt.textContent = x.name;
        var dd = document.createElement('dd'); dd.textContent = x.value;
        $('p-specs').append(dt, dd);
      });
      $('p-specs').hidden = !(p.specs && p.specs.length);
      renderStepper($('p-act'), p);
      shelf($('p-similar'), r.similar);
      shelf($('p-complementary'), r.complementary);
    }).catch(function (e) {
      $('p-name').textContent = e.status === 404 ? 'این کالا در ویترین نیست' : '';
      if (e.status !== 404) note($('p-msg'), 'err', e.message);
    });
  }

  function renderStepper(box, p) {
    box.textContent = '';
    box.classList.remove('stepper');
    var line = cart[p.id];
    if (!line && soldOut(p)) {
      var none = document.createElement('button');
      none.type = 'button'; none.className = 'btn btn-sm btn-block'; none.disabled = true; none.textContent = 'ناموجود';
      box.appendChild(none);
      return;
    }
    if (!line) {
      var add = document.createElement('button');
      add.type = 'button'; add.className = 'btn btn-sm btn-block'; add.textContent = 'افزودن به سبد';
      add.setAttribute('aria-label', 'افزودنِ ' + p.name + ' به سبد');
      add.addEventListener('click', function () { setQty(p, 1000); renderStepper(box, p); });
      box.appendChild(add);
      return;
    }
    box.classList.add('stepper');
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
    var t = totals();
    $('t-subtotal').textContent = money(t.subtotal);
    $('t-fee').textContent = t.fee === null ? 'پس از پین یا ناحیه' : (t.fee === 0 && t.free ? 'رایگان' : money(t.fee));
    $('t-fee').hidden = $('t-fee-label').hidden = fulfillment() !== 'delivery';
    $('t-total').textContent = t.fee === null ? money(t.subtotal) + ' + ارسال' : money(t.total);
    var short = t.min - t.subtotal;
    $('min-order').hidden = !(short > 0);
    $('min-order').textContent = short > 0 ? 'حداقلِ سفارش ' + money(t.min) + ' است؛ ' + money(short) + ' دیگر لازم است.' : '';
    //  CHK-05: «X تا ارسالِ رایگان»
    var z = t.zone;
    var toFree = z && z.freeOverMinor !== null ? z.freeOverMinor - t.subtotal : 0;
    $('free-note').hidden = !(toFree > 0);
    $('free-note').textContent = toFree > 0 ? money(toFree) + ' دیگر تا ارسالِ رایگان' : '';
    $('address-box').hidden = fulfillment() !== 'delivery';
    renderZone();
    renderSaved();
    renderVerify();
    $('submit-order').disabled = !count() || short > 0 || !verifiedFor($('c-phone').value) || !fulfillment() ||
      t.fee === null || zoneState.checking || !slotReady();
  }

  /**
   * همه‌چیزِ سبد از یک‌جا (CHK-04) — همان قاعدهٔ سرور: هزینه و حداقل از ناحیه
   * (بی ناحیه ⇒ ثابتِ دکان)، «رایگان از…» با جمعِ کالاها. `fee: null` یعنی
   * هنوز معلوم نیست (پین یا ناحیه نیامده). برگهٔ سبدِ گوشی همین را نشان می‌دهد.
   */
  function totals() {
    var sub = subtotal();
    var f = fulfillment();
    var fee = 0, min = store.minOrderMinor, free = false, z = null;
    if (f === 'delivery') {
      if (!zoned()) fee = store.deliveryFeeMinor;
      else {
        z = chosenZone();
        if (!z) { fee = null; min = 0; }
        else {
          min = z.minOrderMinor;
          free = z.freeOverMinor !== null && sub >= z.freeOverMinor;
          fee = free ? 0 : z.feeMinor;
        }
      }
    }
    return { subtotal: sub, fee: fee, free: free, min: min, total: sub + (fee || 0), zone: z, slot: slotChosen() };
  }

  // ── CHK-07/10: ناحیهٔ ارسال ──
  var zoneState = { checking: false, inZone: null, zone: null };
  function zoned() { return Boolean(store && store.delivery && store.delivery.zoned); }
  function chosenZone() {
    if (!zoned()) return null;
    if (pin) return zoneState.inZone ? zoneState.zone : null;
    var id = $('zone-select').value;
    return (store.delivery.zones || []).filter(function (z) { return z.id === id; })[0] || null;
  }
  function checkPin() {
    if (!pin || !zoned()) { renderCheckout(); return; }
    zoneState = { checking: true, inZone: null, zone: null };
    renderCheckout();
    api('/delivery?lat=' + pin.lat + '&lng=' + pin.lng).then(function (r) {
      zoneState = { checking: false, inZone: r.inZone, zone: r.zone };
    }).catch(function (e) {
      zoneState = { checking: false, inZone: null, zone: null, error: e.message };
    }).then(renderCheckout);
  }
  function renderZone() {
    var on = fulfillment() === 'delivery' && zoned();
    $('zone-box').hidden = !on;
    if (!on) return;
    var sel = $('zone-select');
    if (!sel.options.length) {
      var o0 = document.createElement('option'); o0.value = ''; o0.textContent = '— ناحیه را برگزینید —';
      sel.appendChild(o0);
      store.delivery.zones.forEach(function (z) {
        var o = document.createElement('option'); o.value = z.id;
        o.textContent = z.name + ' · ارسال ' + money(z.feeMinor);
        sel.appendChild(o);
      });
    }
    $('zone-pick').hidden = Boolean(pin);
    var msg = $('zone-msg');
    var out = Boolean(pin) && zoneState.inZone === false;
    $('zone-pickup').hidden = !(out && store.fulfillment.pickup);
    if (pin && zoneState.checking) note(msg, 'info', 'در حالِ بررسیِ محدودهٔ ارسال…');
    else if (pin && zoneState.error) note(msg, 'err', zoneState.error);
    else if (out) note(msg, 'err', 'این پین بیرون از محدودهٔ ارسالِ دکان است.' + (store.fulfillment.pickup ? ' می‌توانید خودتان از دکان بگیرید.' : ''));
    else if (pin && zoneState.zone) note(msg, 'ok', 'داخلِ ناحیهٔ «' + zoneState.zone.name + '»');
    else if (!pin) note(msg, 'info', 'ارسال فقط در ناحیه‌های فهرست است؛ پین بگذارید تا خودکار سنجیده شود، یا ناحیه را برگزینید.');
  }

  // ── CHK-12: بازهٔ زمانی ──
  var slots = { f: '', data: null };
  function loadSlots(force) {
    var f = fulfillment();
    var has = Boolean(f && store.delivery && store.delivery.scheduling && store.delivery.scheduling[f]);
    if (!has) { slots = { f: f, data: null }; renderSlots(); return; }
    if (!force && slots.f === f && slots.data) { renderSlots(); return; }
    slots = { f: f, data: null };
    renderSlots();
    api('/slots?fulfillment=' + f).then(function (r) {
      if (fulfillment() === f) slots = { f: f, data: r };
    }).catch(function (e) { note($('slot-msg'), 'err', e.message); }).then(function () { renderSlots(); renderCheckout(); });
  }
  function slotDay(at, tz) {
    try { return new Intl.DateTimeFormat('fa-AF', { timeZone: tz, weekday: 'long', day: 'numeric', month: 'long' }).format(new Date(at)); } catch (e) { return ''; }
  }
  function slotRange(startsAt, endsAt, tz) { return clock(startsAt, tz) + '–' + clock(endsAt, tz); }
  function renderSlots() {
    var r = slots.data;
    $('slot-box').hidden = !r;
    if (!r) return;
    $('slot-label').textContent = r.fulfillment === 'pickup' ? 'زمانِ دریافت از دکان' : 'زمانِ تحویل';
    var sel = $('slot');
    var keep = sel.value;
    sel.textContent = '';
    var first = document.createElement('option'); first.value = '';
    first.textContent = r.allowAsap ? 'هرچه زودتر' : '— یک بازه برگزینید —';
    sel.appendChild(first);
    var group = null, day = '';
    r.slots.forEach(function (x) {
      if (x.date !== day) {
        day = x.date;
        group = document.createElement('optgroup'); group.label = slotDay(x.startsAt, r.timezone);
        sel.appendChild(group);
      }
      var o = document.createElement('option');
      o.value = String(x.startsAt);
      o.textContent = slotRange(x.startsAt, x.endsAt, r.timezone) + (x.available ? (x.few ? ' · کم مانده' : '') : ' · پر است');
      o.disabled = !x.available;
      group.appendChild(o);
    });
    sel.value = keep;
    if (sel.selectedIndex < 0 || (sel.options[sel.selectedIndex] && sel.options[sel.selectedIndex].disabled)) sel.value = '';
    if (!r.slots.length) note($('slot-msg'), 'warn', 'در روزهای پیشِ رو بازهٔ خالی نیست' + (r.allowAsap ? '؛ سفارش «هرچه زودتر» ثبت می‌شود.' : '.'));
    else $('slot-msg').hidden = true;
  }
  function slotChosen() { var v = $('slot').value; return v && slots.data ? Number(v) : null; }
  function slotReady() {
    var f = fulfillment();
    if (!f || !store.delivery || !store.delivery.scheduling || !store.delivery.scheduling[f]) return true;
    if (!slots.data) return false;
    return slots.data.allowAsap || slotChosen() !== null;
  }

  // ── CHK-09: نشانی‌های حساب ──
  var account = null;
  function renderSaved() {
    var list = account && session() ? account.addresses : [];
    $('saved-box').hidden = !list.length;
    $('save-address-box').hidden = !session();
    $('save-label').hidden = !$('save-address').checked;
    var box = $('saved-addresses');
    var sig = list.map(function (a) { return a.id; }).join(',');
    if (box.dataset.sig === sig) return;
    box.textContent = '';
    box.dataset.sig = sig;
    list.forEach(function (a) {
      var b = document.createElement('button');
      b.type = 'button'; b.className = 'chip'; b.textContent = a.label;
      b.setAttribute('aria-pressed', 'false');
      b.addEventListener('click', function () {
        $('a-text').value = a.text || '';
        $('a-landmark').value = a.landmark || '';
        pin = a.lat !== null && a.lat !== undefined ? { lat: a.lat, lng: a.lng } : null;
        $('pin-text').textContent = pin ? 'پینِ «' + a.label + '»' : 'پین اختیاری است.';
        Array.prototype.forEach.call(box.children, function (c) { c.setAttribute('aria-pressed', String(c === b)); });
        $('save-address').checked = false;
        checkPin();
      });
      box.appendChild(b);
    });
  }
  function loadAccount() {
    if (!session()) { account = null; return Promise.resolve(null); }
    return api('/account').then(function (r) { account = r; return r; }).catch(function () { account = null; return null; });
  }

  function normPhone(v) {
    var s = String(v || '').replace(/[۰-۹]/g, function (d) { return String(d.charCodeAt(0) - 0x06F0); })
      .replace(/[٠-٩]/g, function (d) { return String(d.charCodeAt(0) - 0x0660); }).replace(/[\s\-().]/g, '').replace(/^00/, '+');
    if (!s) return '';
    if (s.charAt(0) === '0') s = '+93' + s.slice(1);
    else if (s.charAt(0) !== '+') s = '+93' + s;
    return s;
  }
  function verifiedFor(phone) {
    var t = ticket(), se = session(), n = normPhone(phone);
    return Boolean(phone && ((t && t.phone === n) || (se && se.phone === n)));
  }

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
      checkPin();
    }, function () {
      pin = null;
      $('pin-text').textContent = zoned() ? 'موقعیت گرفته نشد؛ ناحیهٔ خود را از فهرست برگزینید.' : 'موقعیت گرفته نشد؛ نشانی و نشانه بس است.';
      renderCheckout();
    }, { enableHighAccuracy: true, timeout: 10000 });
  }

  function orderBody() {
    var f = fulfillment();
    var body = {
      fulfillment: f, paymentMethod: 'cod',
      customer: { name: $('c-name').value.trim(), phone: $('c-phone').value.trim() },
      lines: Object.keys(cart).map(function (id) { return { productId: id, qtyMilli: cart[id].qtyMilli }; }),
      note: $('c-note').value.trim(),
      expectedTotalMinor: totals().total,
    };
    var slot = slotChosen();
    if (slot !== null) body.slotStartsAt = slot;
    if (f === 'delivery') {
      body.address = { text: $('a-text').value.trim(), landmark: $('a-landmark').value.trim() };
      if (pin) { body.address.lat = pin.lat; body.address.lng = pin.lng; }
      else if (zoned() && chosenZone()) body.address.zoneId = chosenZone().id;
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
    var keepAddress = session() && body.fulfillment === 'delivery' && $('save-address').checked ? {
      label: $('save-label').value.trim() || 'نشانیِ تازه', text: body.address.text, landmark: body.address.landmark,
      lat: pin ? pin.lat : null, lng: pin ? pin.lng : null,
    } : null;
    if (!body.customer.name) { note(msg, 'err', 'نام را بنویسید.'); $('c-name').focus(); return; }
    var btn = $('submit-order');
    btn.disabled = true;
    note(msg, 'info', 'در حالِ ثبت…');
    api('/checkout', { method: 'POST', body: body, key: keyFor(body) }).then(function (r) {
      saveS(KEY_PENDING, null);
      //  CHK-09: نگه داشتنِ نشانی پس از ثبت — شکستش سفارش را خراب نمی‌کند
      if (keepAddress) {
        api('/account/addresses', { method: 'POST', body: keepAddress }).then(loadAccount).catch(function () { /* بعداً */ });
        $('save-address').checked = false;
      }
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
      } else if (e.code === 'out_of_zone') {
        zoneState = { checking: false, inZone: false, zone: null };
        renderCheckout();
        note(msg, 'err', e.message + (d.pickup ? ' — می‌توانید «خودم از دکان می‌گیرم» را بزنید.' : ''));
      } else if ((e.code === 'slot_full' || e.code === 'slot_unavailable' || e.code === 'slot_required') && d.slots && slots.data) {
        slots.data.slots = d.slots;
        renderSlots();
        note(msg, 'err', e.message);
      } else if (e.code === 'below_min_order' && d.minOrderMinor !== undefined) {
        note(msg, 'err', e.message + ' (حداقل: ' + money(d.minOrderMinor) + ')');
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
    var whenBits = [];
    if (o.scheduledFor) {
      var tz = (store.status && store.status.timezone) || 'Asia/Kabul';
      whenBits.push((o.fulfillment === 'pickup' ? 'زمانِ دریافت: ' : 'زمانِ تحویل: ') + slotDay(o.scheduledFor, tz) + ' ' +
        (o.slotEndsAt ? slotRange(o.scheduledFor, o.slotEndsAt, tz) : clock(o.scheduledFor, tz)));
    }
    if (o.zoneName) whenBits.push('ناحیه: ' + o.zoneName);
    $('o-when').textContent = whenBits.join(' · ');
    $('o-when').hidden = !whenBits.length;
    $('o-reorder').hidden = !o.lines.length;
    //  CHK-01: مهمانِ تأییدشده ⇒ پیشنهادِ حساب با همان شماره
    $('o-account').hidden = Boolean(session()) || !ticket();
  }

  /** WEB-12: «خریدِ دوباره» — سبد با قیمتِ امروز پر می‌شود؛ تغییرها گفته می‌شوند. */
  var reorderNote = '';
  function reorder(token, msgEl) {
    return api('/orders/' + encodeURIComponent(token) + '/reorder').then(function (r) {
      var changed = [], missing = [];
      r.lines.forEach(function (l) {
        if (!l.available) { missing.push(l.name); return; }
        cart[l.productId] = { qtyMilli: l.qtyMilli, name: l.name, unit: l.unit, priceMinor: l.priceMinor, maxQtyMilli: l.maxQtyMilli || null };
        if (l.priceMinor !== l.previousPriceMinor) changed.push(l.name + ': ' + money(l.previousPriceMinor) + ' ← ' + money(l.priceMinor));
      });
      saveCart();
      var bits = ['اقلامِ سفارشِ ' + fa(r.orderNo, 0) + ' با قیمتِ امروز در سبد است.'];
      if (changed.length) bits.push('قیمتِ تازه: ' + changed.join('؛ '));
      if (missing.length) bits.push('دیگر نیست: ' + missing.join('، '));
      reorderNote = bits.join(' ');
      location.hash = '#/cart';
    }).catch(function (e) { note(msgEl, 'err', e.message); });
  }

  /** حساب با بلیتِ تازهٔ همین دستگاه (کدِ یک‌بارمصرف) — سبد دست نمی‌خورد (S140). */
  function signInWithTicket() {
    var nm = $('c-name').value.trim();
    return api('/account', { method: 'POST', body: nm ? { name: nm } : {} }).then(function (r) {
      save(KEY_SESSION, { session: r.session, expiresAt: r.expiresAt, phone: r.customer.phone });
      return loadAccount();
    });
  }

  function openOrder(token) {
    show('view-order');
    $('o-msg').hidden = true;
    $('o-when').hidden = true; $('o-reorder').hidden = true; $('o-account').hidden = true;
    $('o-reorder').onclick = function () { reorder(token, $('o-msg')); };
    $('o-account-btn').onclick = function () {
      signInWithTicket().then(function () {
        $('o-account').hidden = true;
        note($('o-msg'), 'ok', 'حساب ساخته شد؛ سفارش‌ها در «حسابِ من» هم هست.');
      }).catch(function (e) { note($('o-msg'), 'err', e.message); });
    };
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

  // ── حسابِ مشتری (۲۳، CHK-02/09، S138) ──
  var hist = { cursor: null };
  function statusName(s) { return STATUS[s] || s; }

  function openAccount() {
    show('view-account');
    var se = session();
    $('acc-signin').hidden = Boolean(se);
    $('acc-home').hidden = !se;
    $('acc-msg').hidden = true; $('acc-home-msg').hidden = true;
    if (!se) {
      var t = ticket();
      if (t && !$('acc-phone').value) $('acc-phone').value = t.phone.replace(/^\+93/, '0');
      $('acc-code-row').hidden = true;
      return;
    }
    $('acc-who').textContent = se.phone;
    loadAccount().then(function (r) {
      if (!r) { openAccount(); return; }
      $('acc-who').textContent = r.customer.phone;
      $('acc-name').value = r.customer.name || '';
      renderAddresses();
    });
    $('acc-orders').textContent = '';
    loadHistory(false);
  }

  function renderAddresses() {
    var ul = $('acc-addresses'); ul.textContent = '';
    var list = (account && account.addresses) || [];
    list.forEach(function (a) {
      var li = document.createElement('li');
      var t = document.createElement('span'); t.className = 'line-name';
      t.textContent = a.label + ' — ' + [a.text, a.landmark].filter(Boolean).join('، ') + (a.lat !== null ? ' · پین دارد' : '');
      var rm = document.createElement('button');
      rm.type = 'button'; rm.className = 'btn btn-ghost btn-sm'; rm.textContent = 'حذف';
      rm.setAttribute('aria-label', 'حذفِ نشانیِ ' + a.label);
      rm.addEventListener('click', function () {
        api('/account/addresses/' + encodeURIComponent(a.id), { method: 'DELETE' }).then(loadAccount).then(renderAddresses)
          .catch(function (e) { note($('acc-home-msg'), 'err', e.message); });
      });
      li.append(t, rm);
      ul.appendChild(li);
    });
    $('acc-addresses-empty').hidden = list.length > 0;
  }

  function loadHistory(more) {
    var qs = '?limit=10' + (more && hist.cursor ? '&cursor=' + encodeURIComponent(hist.cursor) : '');
    return api('/account/orders' + qs).then(function (r) {
      var ul = $('acc-orders');
      if (!more) ul.textContent = '';
      r.orders.forEach(function (o) {
        var li = document.createElement('li');
        li.className = 'order-row';
        var a = document.createElement('a');
        a.href = '#/o/' + o.token;
        a.textContent = 'سفارشِ ' + fa(o.orderNo, 0) + ' — ' + when(o.createdAt);
        var meta = document.createElement('span'); meta.className = 'muted';
        meta.textContent = statusName(o.status) + ' · ' + money(o.totalMinor) + (o.preview.length ? ' · ' + o.preview.join('، ') : '');
        var info = document.createElement('div'); info.className = 'line-name';
        info.append(a, meta);
        var again = document.createElement('button');
        again.type = 'button'; again.className = 'btn btn-ghost btn-sm'; again.textContent = 'خریدِ دوباره';
        again.setAttribute('aria-label', 'خریدِ دوبارهٔ سفارشِ ' + fa(o.orderNo, 0));
        again.addEventListener('click', function () { reorder(o.token, $('acc-home-msg')); });
        li.append(info, again);
        ul.appendChild(li);
      });
      hist.cursor = r.nextCursor;
      $('acc-more').hidden = !r.nextCursor;
      $('acc-orders-empty').hidden = ul.childElementCount > 0;
    }).catch(function (e) { note($('acc-home-msg'), 'err', e.message); });
  }

  function accSend() {
    var phone = $('acc-phone').value;
    if (!phone) { note($('acc-msg'), 'err', 'اول شمارهٔ موبایل را بنویسید.'); $('acc-phone').focus(); return; }
    $('acc-send').disabled = true;
    api('/verify/start', { method: 'POST', body: { phone: phone } }).then(function (r) {
      $('acc-code-row').hidden = false;
      note($('acc-msg'), 'info', 'کد فرستاده شد.' + (r.devCode ? ' (کدِ آزمایشی: ' + r.devCode + ')' : ''));
      $('acc-code').focus();
    }).catch(function (e) { note($('acc-msg'), 'err', e.message); }).then(function () { $('acc-send').disabled = false; });
  }

  function accCheck() {
    api('/verify/check', { method: 'POST', body: { phone: $('acc-phone').value, code: $('acc-code').value } }).then(function (r) {
      save(KEY_TICKET, { phone: r.phone, ticket: r.ticket, expiresAt: r.expiresAt });
      $('acc-code').value = '';
      return signInWithTicket();
    }).then(function () { openAccount(); }).catch(function (e) {
      //  ۲۳.۴: خطای شبکه «کدِ اشتباه» نیست
      note($('acc-msg'), 'err', e.code === 'network' ? e.message : (e.message || 'کد درست نیست'));
    });
  }

  // ── مسیر ──
  function route() {
    if (!store) return;
    var h = location.hash || '#/';
    if (h.indexOf('#/o/') === 0) return openOrder(h.slice(4));
    if (h === '#/orders') return openOrders();
    if (h === '#/account') return openAccount();
    if (h.indexOf('#/p/') === 0) return openProduct(decodeURIComponent(h.slice(4)));
    if (h === '#/cart') {
      show('view-checkout');
      note($('reorder-note'), 'info', reorderNote);
      reorderNote = '';
      var se = session();
      if (se && !$('c-phone').value) $('c-phone').value = se.phone.replace(/^\+93/, '0');
      if (account && account.customer.name && !$('c-name').value) $('c-name').value = account.customer.name;
      loadSlots(true);
      return renderCheckout();
    }
    document.title = store.title;
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
      loadHighlights();
      return Promise.all([loadProducts(false), loadAccount()]);
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
    Array.prototype.forEach.call(document.querySelectorAll('input[name="fulfillment"]'), function (r) {
      r.addEventListener('change', function () { loadSlots(false); renderCheckout(); });
    });
    $('zone-select').addEventListener('change', renderCheckout);
    $('slot').addEventListener('change', renderCheckout);
    $('save-address').addEventListener('change', renderCheckout);
    $('zone-pickup').addEventListener('click', function () {
      document.querySelector('input[name="fulfillment"][value="pickup"]').checked = true;
      loadSlots(false);
      renderCheckout();
    });
    $('acc-send').addEventListener('click', accSend);
    $('acc-check').addEventListener('click', accCheck);
    $('acc-more').addEventListener('click', function () { loadHistory(true); });
    $('acc-save-name').addEventListener('click', function () {
      api('/account', { method: 'PUT', body: { name: $('acc-name').value.trim() } }).then(function () {
        note($('acc-home-msg'), 'ok', 'نام ذخیره شد.');
        return loadAccount();
      }).catch(function (e) { note($('acc-home-msg'), 'err', e.message); });
    });
    $('acc-logout').addEventListener('click', function () {
      api('/account/logout', { method: 'POST', body: {} }).catch(function () { /* بی شبکه هم این دستگاه خارج می‌شود */ }).then(function () {
        save(KEY_SESSION, null); account = null; openAccount();
      });
    });
    $('acc-delete').addEventListener('click', function () {
      if (!window.confirm('حساب و نشانی‌های نگه‌داشته پاک شود؟ سفارش‌های ثبت‌شده نزدِ دکان می‌ماند.')) return;
      api('/account', { method: 'DELETE' }).then(function () {
        save(KEY_SESSION, null); account = null; openAccount();
      }).catch(function (e) { note($('acc-home-msg'), 'err', e.message); });
    });
    $('c-phone').addEventListener('input', renderCheckout);
    $('verify-send').addEventListener('click', sendCode);
    $('verify-check').addEventListener('click', checkCode);
    $('pin-btn').addEventListener('click', takePin);
    $('checkout-form').addEventListener('submit', submitOrder);
    window.addEventListener('hashchange', route);
    window.addEventListener('storage', function (e) { if (e.key === KEY_CART) { cart = load(KEY_CART, {}); route(); } });
    boot();
  });
})();
