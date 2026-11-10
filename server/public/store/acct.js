/*
 * ویترین — حسابِ مشتری در وب (CHK-01/03/09، WEB-14). فقط با `features.webAccount` (پرچمِ `shop.storefront.web_account`)
 * و دیربار از store.js (`account()`)؛ همان درهای `/api/store/<slug>/account…` ِ سرور (`lib/store-customers.js`).
 * نشست در `store-session` (کیت: `K.sess`؛ `K.api` آن را کنارِ بلیت می‌فرستد ⇒ ثبت و «سفارش‌های من» با نشستِ ۱۸۰ روزه).
 *
 *   ساختن/ورود ── فقط با بلیتِ تازهٔ «شماره تأیید شد» (`POST /account`؛ سرور ≤ ۱۰ دقیقه)؛ کهنه ⇒ همین‌جا کد و تأیید.
 *   پیشنهاد ──── CHK-01: صفحهٔ تأییدِ سفارشِ تازه (`offer`، فقط بی نشست)؛ «نه، ممنون» ۳۰ روز به یاد می‌ماند.
 *   نشانی ────── CHK-09: برگهٔ ثبت (`checkout`): گزینش از نشانی‌های نام‌دار، «نگه‌داشتن با نام» فقط پس از ثبتِ موفق، حذف.
 *   سبد ──────── CHK-03/WEB-14، فقط با `features.accountCart` (پرچمِ `shop.storefront.cart`): `store/cart-sync.js` ِ دیربار
 *                 (ادغامِ یک‌بارهٔ سبدِ مهمان، `PUT` با نسخه، ۴۰۹ ⇒ سه‌طرفه، درنگ). نشست رفت (خروج، ۴۰۱ ِ حساب، منقضی) ⇒
 *                 نسخهٔ سبدِ حساب از این دستگاه پاک (`gone`)؛ سبدِ مهمانِ بی حساب (بی `merged`) همان می‌ماند.
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
    var cs = null; var fell = false;

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
          //  نسخهٔ سبدِ حسابِ پیشین (نشستِ مرده) با حسابِ تازه ادغام نمی‌شود؛ سبدِ مهمان یک بار
          gone(); save(r); start(); done();
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
          //  تغییرِ در صفِ سبد اول نوشته می‌شود؛ «خروج» بلیتِ این دستگاه و نسخهٔ سبدِ حساب را هم می‌برد
          sync().then(function (c) { return c && c.settle(); }).then(function () {
            return api('/account/logout', { method: 'POST' }).catch(function () { /* نشستِ سرور هم سرِ مهلت می‌میرد */ });
          }).then(function () { gone(); K.setTicket(null); K.account(); mine(); });
        });
        box.append(el('div', { class: 'spread' }, el('b', { text: t('ac.me') }), el('span', { class: 'ltr', text: K.localPhone(s.phone) })), out);
        //  SEC-10: «پاک کردنِ داده‌های من در این دکان» (erase.js ِ دیربار) فقط با `features.erasure`
        if (ctx.store.features && ctx.store.features.erasure) {
          K.js('/store/erase.js').then(function () { window.StoreErase({ api: api, slug: ctx.slug, phone: s.phone, into: box }); }, function () {});
        }
      });
    }

    // ── نشانی‌های نام‌دار در برگهٔ ثبت (CHK-09) ──
    /** CRM-09: کادرِ برگهٔ ثبت ⇐ کلیدِ نشانیِ حساب (همان کلیدهای `body.address` ِ checkout.js) */
    var XF = [['a-area', 'area'], ['a-gate', 'gateColor'], ['a-phone2', 'phone2'], ['a-plus', 'plusCode'], ['a-prov', 'province'], ['a-street', 'street'], ['a-house', 'house']];
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
          //  CRM-09: کلیدهای کمکیِ همان نشانی در کادرهای همین برگه (کادرِ نبوده، مثلاً بی `addressExtra`، کنار)
          XF.forEach(function (f) { if ($(f[0])) $(f[0]).value = (a && a[f[1]]) || ''; });
          //  پرشده‌ها پنهان نمانند: «جزئیاتِ بیشتر» باز
          if (a && $('addr-more') && XF.some(function (f) { return $(f[0]) && a[f[1]]; })) $('addr-more').open = true;
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
          var b = { label: name, text: a.text, landmark: a.landmark, lat: a.lat, lng: a.lng };
          XF.forEach(function (f) { if (a[f[1]]) b[f[1]] = a[f[1]]; });
          api('/account/addresses', { method: 'POST', body: b })
            .then(function (x) { K.toast(t('ac.saved', { label: x.address.label })); }).catch(function (e) { K.toast(e.message); });
        };
      }).catch(function () { /* همان فرمِ امروز */ });
      return handle;
    }

    // ── سبدِ حساب (CHK-03، WEB-14): `store/cart-sync.js` ِ دیربار، فقط با `features.accountCart` و نشست ──
    function on() { return Boolean(K.sess() && ctx.store.features && ctx.store.features.accountCart); }
    function sync() {
      if (!on()) return Promise.resolve(null);
      cs = cs || K.js('/store/cart-sync.js').then(function () {
        return window.StoreCartSync({ api: api, key: SYNC, slug: ctx.slug, cart: ctx.cart, set: ctx.set, on: on, out: lost });
      });
      //  نیامد ⇒ یک بار همان قیمتِ امروزِ کالاها (راهِ مهمان؛ store.js در حالتِ حساب آن را به این‌جا سپرده)؛ بارِ بعد دوباره
      return cs.catch(function () { cs = null; if (!fell) { fell = true; ctx.set(ctx.cart()); } return null; });
    }
    function start() { sync().then(function (c) { if (c) c.start(); }); }
    function push() { if (on()) sync().then(function (c) { if (c) c.push(); }); }
    /** نشست رفت (خروج، منقضی، ۴۰۱ ِ حساب): نشست و نسخهٔ سبدِ حساب از این دستگاه پاک؛ سبدِ حساب روی سرور می‌ماند */
    function gone() {
      var copy = (K.load(SYNC, null) || {}).merged;
      drop();
      if (copy) ctx.set({});
    }
    function lost() { gone(); if ($('ac-mine')) mine(); }

    if (K.sess()) start(); else gone();
    return { push: push, offer: offer, mine: mine, checkout: checkout };
  };
})();
