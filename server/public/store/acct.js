/*
 * ویترین — حسابِ مشتری در وب (CHK-01/03/09، WEB-14). فقط با `features.webAccount` (پرچمِ `shop.storefront.web_account`)
 * و دیربار از store.js (`account()`)؛ همان درهای `/api/store/<slug>/account…` ِ سرور (`lib/store-customers.js`).
 * نشست در `store-session` (کیت: `K.sess`؛ `K.api` آن را کنارِ بلیت می‌فرستد ⇒ ثبت و «سفارش‌های من» با نشستِ ۱۸۰ روزه).
 *
 *   ساختن/ورود ── فقط با بلیتِ تازهٔ «شماره تأیید شد» (`POST /account`؛ سرور ≤ ۱۰ دقیقه)؛ کهنه ⇒ همین‌جا کد و تأیید.
 *   پیشنهاد ──── CHK-01: صفحهٔ تأییدِ سفارشِ تازه (`offer`، فقط بی نشست)؛ «نه، ممنون» ۳۰ روز به یاد می‌ماند.
 *   نشانی ────── CHK-09: برگهٔ ثبت (`checkout`): گزینش از نشانی‌های نام‌دار، «نگه‌داشتن با نام» فقط پس از ثبتِ موفق، حذف.
 *   سبد ──────── CHK-03/WEB-14، فقط با `features.accountCart` (پرچمِ `shop.storefront.cart`): شناسه، مقدار و یادداشت —
 *                 قیمت هرگز از این‌جا نمی‌رود (هر خواندن قیمتِ امروز را از سرور می‌گیرد). نخستین بارِ پس از ورود ⇒ ادغامِ
 *                 سبدِ مهمان (`/merge`، بیشینه؛ تکرارش بی اثر)؛ سپس سبدِ سرور. تغییرِ این دستگاه ⇒ `PUT` با نسخهٔ دیده‌شده؛
 *                 بی اینترنت ⇒ در صف (`store-cartsync:<slug>` = نسخه و نشانِ آخرین هم‌گامی) تا `online` یا بارِ بعد.
 *                 ۴۰۹ `cart_changed` ⇒ تغییرِ در صفِ همین دستگاه روی نسخهٔ تازه (حداکثر ۳ بار). نشستِ نامعتبر ⇒ خروج؛
 *                 سبد روی دستگاه می‌ماند.
 * ⛔ همهٔ متن با textContent (`K.el`)؛ واژه‌نامهٔ `kit/i18n/acct.<زبان>.json` نیامد ⇒ هیچ گره‌ای (سبد همان هم‌گام می‌شود).
 */
(function () {
  'use strict';
  var $ = K.$; var el = K.el; var t = K.t;
  var KEY = 'store-session';
  var NO = 'store-acct-no';
  var MONTH = 30 * 864e5;

  /** `ctx` از store.js: `api`، `slug`، `store`، `cart()` (سبدِ امروزِ دستگاه) و `set(سبد)` (نشاندن + ذخیره + تازه‌سازیِ قیمت) */
  window.StoreAcct = function (ctx) {
    var api = ctx.api;
    var SYNC = 'store-cartsync:' + ctx.slug;
    var dict = K.part('acct', $('ac-box'));
    var timer = 0; var busy = false; var retry = 0;

    function save(r) { K.save(KEY, { token: r.session, expiresAt: r.expiresAt, phone: r.customer.phone, name: r.customer.name || '' }); }
    function drop() { K.save(KEY, null); K.save(SYNC, null); }
    function btn(cls, text) { return el('button', { class: 'btn ' + cls, attrs: { type: 'button' }, text: text }); }

    // ── ساختن/ورود (CHK-01) ──
    /** بلیتِ تازه ⇒ یک لمس؛ وگرنه شماره ⇒ کد ⇒ تأیید ⇒ همان `POST /account`. `done` پس از نشستِ تازه؛ `make` = «ساختن» (پیشنهاد). */
    function signIn(box, name, done, make) {
      var tk = K.ticket();
      var msg = el('div', { class: 'note', attrs: { role: 'status' }, hidden: true });
      var phone = el('input', { attrs: { type: 'tel', inputmode: 'tel', autocomplete: 'tel', dir: 'ltr', maxlength: '20', 'aria-label': t('ac.phone'), placeholder: t('ac.phone') } });
      var code = el('input', { attrs: { inputmode: 'numeric', autocomplete: 'one-time-code', dir: 'ltr', maxlength: '8', 'aria-label': t('ac.code'), placeholder: t('ac.code') } });
      var send = btn('btn-soft btn-sm', t('ac.send'));
      var check = btn('btn-sm', t('ac.check'));
      var codeRow = el('div', { class: 'code-row', hidden: true }, code, check);
      var form = el('div', { class: 'stack', hidden: true }, phone, send, codeRow);
      var go = btn('btn-sm', tk && make ? t('ac.make', { phone: K.localPhone(tk.phone) }) : t('ac.in'));
      phone.value = tk ? K.localPhone(tk.phone) : '';
      function ask() { go.hidden = true; form.hidden = false; phone.focus(); }
      function create() {
        go.disabled = true;
        return api('/account', { method: 'POST', body: { name: name || '' } }).then(function (r) {
          save(r); K.save(SYNC, null); start(); done();
        }).catch(function (e) {
          go.disabled = false;
          if (e.code === 'verification_required') { ask(); K.note(msg, 'info', t('ac.again')); } else K.note(msg, 'err', e.message);
        });
      }
      go.addEventListener('click', function () { if (K.ticket()) create(); else ask(); });
      send.addEventListener('click', function () {
        send.disabled = true;
        api('/verify/start', { method: 'POST', body: { phone: phone.value } }).then(function (r) {
          codeRow.hidden = false;
          K.note(msg, 'info', t('ac.sent') + (r.devCode ? ' ' + t('ac.dev', { code: r.devCode }) : ''));
          code.focus();
        }).catch(function (e) { K.note(msg, e.code === 'verification_unavailable' ? 'warn' : 'err', e.message); })
          .then(function () { send.disabled = false; });
      });
      check.addEventListener('click', function () {
        check.disabled = true;
        api('/verify/check', { method: 'POST', body: { phone: phone.value, code: code.value } }).then(function (r) {
          K.setTicket(r); K.account(); code.value = '';
          return create();
        }).catch(function (e) { K.note(msg, 'err', e.message); }).then(function () { check.disabled = false; });
      });
      box.append(go, form, msg);
    }

    /** CHK-01: پس از ثبتِ سفارشِ همین مرورگر (`placed:<token>`)، بی نشست، زیرِ «سفارش ثبت شد» */
    function offer(token) {
      var cur = $('ac-offer');
      var placed = K.loadS('placed:' + token);
      if (cur && (!placed || cur.getAttribute('data-token') !== token)) cur.remove();
      if (!placed || K.sess() || $('ac-offer') || Date.now() - (Number(K.load(NO, 0)) || 0) < MONTH) return;
      dict.then(function (ok) {
        var at = $('o-placed');
        if (!ok || !at || K.sess() || $('ac-offer') || location.hash !== '#/o/' + encodeURIComponent(token)) return;
        var box = el('section', { class: 'card stack', attrs: { id: 'ac-offer', 'data-token': token, 'aria-labelledby': 'ac-offer-t' } },
          el('h2', { class: 'label-lg', attrs: { id: 'ac-offer-t' }, text: t('ac.offer') }), el('p', { class: 'muted small', text: t('ac.offer.p') }));
        var me = K.load('store-customer', null) || {};
        signIn(box, me.name, function () { K.clear(box).append(el('div', { class: 'note ok', attrs: { role: 'status' }, text: t('ac.done') })); }, true);
        var no = btn('btn-ghost btn-sm', t('ac.no'));
        no.addEventListener('click', function () { K.save(NO, Date.now()); box.remove(); });
        box.appendChild(no);
        at.parentNode.insertBefore(box, at.nextSibling);
      });
    }

    /** «سفارش‌های من»: با نشست ⇒ شماره و «خروج»؛ بی نشست ⇒ ورود (دستگاهِ تازه) */
    function mine() {
      dict.then(function (ok) {
        var list = $('my-orders');
        if (!ok || !list) return;
        var box = $('ac-mine') || list.parentNode.insertBefore(el('section', { class: 'card stack', attrs: { id: 'ac-mine' } }), list);
        K.clear(box);
        var s = K.sess();
        if (!s) { box.appendChild(el('p', { class: 'muted small', text: t('ac.in.p') })); signIn(box, '', mine); return; }
        var out = btn('btn-ghost btn-sm', t('ac.out'));
        out.addEventListener('click', function () {
          out.disabled = true;
          //  «خروج» شمارهٔ تأییدشدهٔ این دستگاه را هم می‌برد (بلیت)؛ سبد روی دستگاه می‌ماند
          api('/account/logout', { method: 'POST' }).catch(function () { /* نشستِ سرور هم سرِ مهلت می‌میرد */ })
            .then(function () { drop(); K.setTicket(null); K.account(); mine(); });
        });
        box.append(el('div', { class: 'spread' }, el('b', { text: t('ac.me') }), el('span', { class: 'ltr', text: K.localPhone(s.phone) })), out);
      });
    }

    // ── نشانی‌های نام‌دار در برگهٔ ثبت (CHK-09) ──
    /** `h.pin(p, متن)` از checkout.js: پینِ نشانیِ برگزیده یا null. ⇒ `{ placed(body) }` برای پس از ثبتِ موفق. */
    function checkout(h) {
      var pick = $('ac-addr'); var keep = $('ac-keep-row');
      if (pick) pick.remove();
      if (keep) keep.remove();
      var handle = { placed: function () {} };
      var ab = $('address-box');
      if (!K.sess() || !ab) return handle;
      Promise.all([dict, api('/account/addresses')]).then(function (r) {
        if (!r[0] || !K.sess()) return;
        var list = r[1].addresses || [];
        var sel = el('select', { attrs: { id: 'ac-pick' } }, el('option', { attrs: { value: '' }, text: t('ac.addr.new') }));
        list.forEach(function (a) { sel.appendChild(el('option', { attrs: { value: a.id }, text: a.label })); });
        var del = btn('btn-ghost btn-sm', t('ac.addr.del'));
        del.hidden = true;
        pick = el('div', { class: 'stack', attrs: { id: 'ac-addr' }, hidden: !list.length },
          el('label', { class: 'field' }, el('span', { text: t('ac.addr') }), sel), del);
        var box = el('input', { attrs: { id: 'ac-keep', type: 'checkbox' } });
        var label = el('input', { attrs: { id: 'ac-label', type: 'text', maxlength: '40', placeholder: t('ac.label'), 'aria-label': t('ac.label') }, hidden: true });
        keep = el('div', { class: 'stack', attrs: { id: 'ac-keep-row' } }, el('label', { class: 'check' }, box, ' ', el('span', { text: t('ac.keep') })), label);
        var byId = function (id) { return list.filter(function (a) { return a.id === id; })[0] || null; };
        sel.addEventListener('change', function () {
          var a = byId(sel.value);
          del.hidden = !a; keep.hidden = Boolean(a);
          $('a-text').value = a ? a.text : ''; $('a-landmark').value = a ? a.landmark : '';
          h.pin(a && a.lat !== null ? { lat: a.lat, lng: a.lng } : null, t('ac.pin'));
        });
        box.addEventListener('change', function () { label.hidden = !box.checked; if (box.checked) label.focus(); });
        del.addEventListener('click', function () {
          var a = byId(sel.value);
          if (!a) return;
          del.disabled = true;
          api('/account/addresses/' + encodeURIComponent(a.id), { method: 'DELETE' }).then(function () {
            list = list.filter(function (x) { return x.id !== a.id; });
            sel.remove(sel.selectedIndex); sel.value = ''; sel.dispatchEvent(new Event('change'));
            pick.hidden = !list.length;
          }).catch(function (e) { K.toast(e.message); }).then(function () { del.disabled = false; });
        });
        ab.insertBefore(pick, ab.firstChild);
        ab.appendChild(keep);
        handle.placed = function (body) {
          var name = label.value.trim();
          if (keep.hidden || !box.checked || !name || body.fulfillment !== 'delivery' || !body.address) return;
          var a = body.address;
          api('/account/addresses', { method: 'POST', body: { label: name, text: a.text, landmark: a.landmark, lat: a.lat, lng: a.lng } })
            .then(function (x) { K.toast(t('ac.saved', { label: x.address.label })); }).catch(function (e) { K.toast(e.message); });
        };
      }).catch(function () { /* همان فرمِ امروز */ });
      return handle;
    }

    // ── سبدِ حساب (CHK-03، WEB-14) ──
    function on() { return Boolean(K.sess() && ctx.store.features && ctx.store.features.accountCart); }
    function st() { return K.load(SYNC, null) || {}; }
    /** فهرستِ قلم‌های دستگاه برای سرور — فقط شناسه، مقدار و یادداشت (به ترتیبِ شناسه) */
    function items() {
      var c = ctx.cart();
      return Object.keys(c).sort().map(function (id) {
        var x = { productId: id, qtyMilli: c[id].qtyMilli };
        var n = String(c[id].note || '').trim().slice(0, 200);
        if (n) x.note = n;
        return x;
      });
    }
    /** نشانِ هم‌سنجی: همان قلم‌ها، مقدار و یادداشت ⇒ همان رشته (ترتیبِ سرور مهم نیست) */
    function mark(list) {
      return JSON.stringify(list.map(function (i) { return [i.productId, i.qtyMilli, String(i.note || '').trim()]; })
        .sort(function (a, b) { return a[0] < b[0] ? -1 : a[0] > b[0] ? 1 : 0; }));
    }
    function synced(cart) { K.save(SYNC, { v: cart.version, k: mark(cart.lines), merged: true }); }
    /** سبدِ سرور ⇒ سبدِ دستگاه؛ نام/عکس/«اگر نبود» ِ قلمِ هم‌شناسه می‌ماند، قیمت و «هست؟» را store.js از نو می‌خواند */
    function adopt(cart) {
      var cur = ctx.cart(); var next = {};
      cart.lines.forEach(function (l) {
        var o = cur[l.productId] || {};
        var x = Object.assign({}, o, {
          qtyMilli: l.qtyMilli, name: l.name || o.name || '', unit: l.unit || o.unit || '',
          priceMinor: l.priceMinor === null || l.priceMinor === undefined ? (Number(o.priceMinor) || 0) : l.priceMinor,
          maxQtyMilli: l.maxQtyMilli || null, photo: Boolean(o.photo), photoVersion: o.photoVersion, unavailable: !l.available,
        });
        if (l.note) x.note = l.note; else delete x.note;
        next[l.productId] = x;
      });
      synced(cart);
      ctx.set(next);
    }
    function fail(e) { if (e.status === 401) drop(); }
    function start() {
      if (!on() || busy) return;
      var s = st();
      //  WEB-14: تغییرِ در صفِ همین دستگاه پیش از خواندن ⇒ اول همان
      if (s.merged && mark(items()) !== s.k) { flush(); return; }
      busy = true;
      (s.merged ? api('/account/cart') : api('/account/cart/merge', { method: 'POST', body: { items: items() } })).then(function (r) {
        busy = false; adopt(r.cart);
      }).catch(function (e) {
        busy = false;
        //  بیش از ۱۰۰ قلم ⇒ سبدِ این دستگاه روی نسخهٔ سرور می‌رود (هیچ چیز بی‌صدا گم نمی‌شود)
        if (e.code === 'cart_full' && e.data && e.data.cart) { K.save(SYNC, { v: e.data.cart.version, k: '', merged: true }); flush(); } else fail(e);
      });
    }
    function flush() {
      if (!on() || busy) return;
      var s = st(); var list = items();
      if (!s.merged) { start(); return; }
      if (mark(list) === s.k || navigator.onLine === false) return;
      busy = true;
      api('/account/cart', { method: 'PUT', body: { version: Number(s.v) || 0, items: list } }).then(function (r) {
        busy = false; retry = 0; synced(r.cart);
        flush();
      }).catch(function (e) {
        busy = false;
        if (e.code === 'cart_changed' && e.data && e.data.cart && retry++ < 3) { K.save(SYNC, { v: e.data.cart.version, k: s.k, merged: true }); flush(); } else fail(e);
      });
    }
    function push() { if (on()) { clearTimeout(timer); timer = setTimeout(flush, 400); } }

    window.addEventListener('online', function () { if (st().merged) flush(); else start(); });
    start();
    return { push: push, offer: offer, mine: mine, checkout: checkout };
  };
})();
