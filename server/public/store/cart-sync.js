/*
 * ویترین — سبدِ حساب روی سرور (CHK-03، WEB-14، ۲۳ S140): دیربار از acct.js، فقط با `features.accountCart` (پرچم‌های
 * خاموشِ `shop.storefront.cart` و `.web_account`) و نشستِ زنده؛ وگرنه خواسته نمی‌شود و سبدِ مهمان همان امروز.
 * درها `GET`/`PUT /account/cart` و `POST /account/cart/merge` (`lib/store-cart.js`)؛ فقط شناسه، مقدار و یادداشت می‌رود.
 *
 *   پایه ───── `store-cartsync:<slug>` = { v: نسخهٔ سرور، k: قلم‌های همان نسخه، merged }. «در صف» = سبدِ دستگاه ≠ k
 *              (بستنِ صفحه گمش نمی‌کند؛ بارِ بعد اول همان نوشته می‌شود).
 *   ورود ───── بی `merged` ⇒ یک بار `/merge` با سبدِ مهمان (بیشینه؛ تکرارِ پس از قطع بی اثر)؛ پاسخ جای سبدِ دستگاه را
 *              می‌گیرد و `merged` ⇒ همان سبدِ مهمان دوباره ادغام نمی‌شود. بازگشت به برگه پس از ≥ ۱ دقیقه ⇒ `GET`.
 *   تغییر ──── `PUT` ِ ۴۰۰ms پس از آخرین تغییر با نسخهٔ پایه. ۴۰۹ `cart_changed` ⇒ سه‌طرفه: فقط تغییرِ همین دستگاه
 *              (دستگاه − پایه) روی سبدِ تازهٔ سرور و یک بار دوباره؛ ۴۰۹ ِ دوم ⇒ همان ادغام روی دستگاه می‌ماند، نوبتِ بعد با
 *              درنگ. هرگز «همهٔ سبدِ من روی سبدِ تو» (دو برگه/دستگاه یکدیگر را پاک نمی‌کنند) و هرگز چرخهٔ بی‌درنگ.
 *   شکست ──── شبکه/۴۲۹/۵xx/۴۰۰ ⇒ بی پیامِ خطا، سبد روی دستگاه کار می‌کند؛ دوباره با درنگِ دوبرابرشونده (۲s، ۴۲۹ ⇒ ۳۰s،
 *              سقف ۵ دقیقه) یا `online`. تغییرِ کاربر درنگ را نمی‌شکند ⇒ سطلِ `store-acct-sync` ِ سرور دور زده نمی‌شود.
 *   نشست ──── ۴۰۱ `session_required` (خروج یا پاک شدنِ حساب) ⇒ `ctx.out()`. ۴۰۱ ِ دیگر (پرچمِ خاموش = درِ ناشناخته)،
 *              ۴۰۳ یا ۴۰۴ ⇒ فقط هم‌گامی می‌ایستد، هرگز خروج. پاسخِ نشستِ پیشین (خروج/ورودِ میانه) دور ریخته می‌شود.
 *   سرور برنده  قیمت، «دیگر نیست» و «ناموجود» از پاسخ، با یک پیامِ کوتاه (`cart.repriced`، `cart.gone`، `ac.cart.out`).
 */
(function () {
  'use strict';
  var GAP = 400; var LOOK = 60000; var MAX = 300000;

  /** `ctx` از acct.js: `api`، `key`، `slug`، `cart()`، `set(سبد)`، `on()` (نشست + پرچم)، `out()` (نشست رفت) */
  window.StoreCartSync = function (ctx) {
    var api = ctx.api; var KEY = ctx.key;
    var timer = 0; var busy = false; var again = false; var stop = false; var wait = 0; var hold = 0; var seen = 0; var fell = false;

    function st() { return K.load(KEY, null) || {}; }
    function who() { var s = K.sess(); return s ? s.token : ''; }
    function note(x) { return String((x && x.note) || '').trim().slice(0, 200); }
    /** قلم‌های دستگاه برای سرور — فقط شناسه، مقدار و یادداشت، به ترتیبِ شناسه */
    function items() {
      var c = ctx.cart();
      return Object.keys(c).sort().map(function (id) {
        var x = { productId: id, qtyMilli: c[id].qtyMilli }; var n = note(c[id]);
        if (n) x.note = n;
        return x;
      });
    }
    function sig(i) { return i ? i.qtyMilli + '|' + note(i) : ''; }
    function sigs(list) { var o = {}; list.forEach(function (i) { o[i.productId] = sig(i); }); return o; }
    /** نشانِ هم‌سنجی (همان شکلِ پیشینِ `k`؛ ترتیبِ سرور مهم نیست) */
    function mark(list) {
      return JSON.stringify(list.map(function (i) { return [i.productId, i.qtyMilli, note(i)]; })
        .sort(function (a, b) { return a[0] < b[0] ? -1 : a[0] > b[0] ? 1 : 0; }));
    }
    function base() {
      var o = {};
      try { JSON.parse(st().k).forEach(function (r) { o[r[0]] = r[1] + '|' + r[2]; }); } catch (e) { /* پایهٔ خالی */ }
      return o;
    }
    function pending() { var s = st(); return s.merged === true && mark(items()) !== s.k; }

    /** سطرِ سرور ⇒ سطرِ دستگاه (نام/عکس/«اگر نبود» ِ همان شناسه می‌ماند)؛ `n` = [قیمتِ تازه، دیگر نیست، ناموجود] */
    function line(l, o, n) {
      var bad = !l.available || !K.stockOf(l).orderable;
      if (bad && !o.unavailable) n[l.available ? 2 : 1]++;
      else if (!bad && !o.unavailable && typeof o.priceMinor === 'number' && o.priceMinor !== l.priceMinor) n[0]++;
      var x = Object.assign({}, o, {
        qtyMilli: l.qtyMilli, name: l.name || o.name || '', unit: l.unit || o.unit || '',
        priceMinor: typeof l.priceMinor === 'number' ? l.priceMinor : (Number(o.priceMinor) || 0),
        maxQtyMilli: l.maxQtyMilli || null, photo: Boolean(o.photo), photoVersion: o.photoVersion, unavailable: bad,
      });
      if (l.note) x.note = l.note; else delete x.note;
      return x;
    }
    function shape(c) {
      return JSON.stringify(Object.keys(c).sort().map(function (id) {
        var l = c[id]; return [id, l.qtyMilli, note(l), l.priceMinor, Boolean(l.unavailable), l.name];
      }));
    }
    /**
     * سبدِ سرور ⇒ دستگاه (سرور برنده) و پایهٔ تازه. `was` = پایهٔ همان درخواست: قلمی که دستگاه از آن پس عوض کرده
     * (افزوده، مقدار، یادداشت، برداشته) روی سبدِ سرور می‌ماند؛ بقیه همان سرور.
     */
    function land(cart, was) {
      var cur = ctx.cart(); var now = {}; var srv = {}; var next = {}; var n = [0, 0, 0];
      items().forEach(function (i) { now[i.productId] = i; });
      cart.lines.forEach(function (l) { srv[l.productId] = l; });
      Object.keys(Object.assign({}, srv, now)).forEach(function (id) {
        var own = (was[id] || '') !== sig(now[id]);
        var l = srv[id];
        if (own && !now[id]) return;
        if (!l) { if (own) next[id] = cur[id]; return; }
        var x = line(l, cur[id] || {}, n);
        if (own) { x.qtyMilli = now[id].qtyMilli; if (now[id].note) x.note = now[id].note; else delete x.note; }
        next[id] = x;
      });
      K.save(KEY, { v: cart.version, k: mark(cart.lines), merged: true });
      seen = Date.now();
      if (shape(next) !== shape(cur)) {
        ctx.set(next);
        var m = [];
        if (n[0]) m.push(K.t('cart.repriced', { n: K.fa(n[0], 0) }));
        if (n[1]) m.push(K.t('cart.gone', { n: K.fa(n[1], 0) }));
        if (n[2]) m.push(K.t('ac.cart.out', { n: K.fa(n[2], 0) }));
        if (m.length) K.toast(m.join(' '));
      }
      if (pending()) push();
    }

    function push() { if (!stop) { clearTimeout(timer); timer = setTimeout(flush, Math.max(GAP, hold - Date.now())); } }
    function later(e) {
      wait = Math.min(Math.max(wait * 2, e.status === 429 ? 30000 : 2000), MAX);
      hold = Date.now() + wait + Math.floor(Math.random() * 1000);
      push();
      return true;
    }
    /** خواندنِ نخست نشد: سبدِ دستگاه با قیمتِ امروزِ کالاها (همان راهِ مهمان، یک بار) و دوباره با درنگ */
    function fallback(e) {
      later(e);
      if (!fell) { fell = true; ctx.set(ctx.cart()); }
      return true;
    }
    /** یک درخواستِ هم‌گامی. `fail(e)` ⇒ true/وعده = رسیدگی شد؛ پاسخِ نشستِ پیشین دور ریخته می‌شود */
    function call(path, opts, done, fail) {
      var tok = who(); busy = true;
      return api(path, opts).then(function (r) {
        busy = false;
        if (who() !== tok || !r || !r.cart) return;
        wait = 0; hold = 0; done(r.cart);
      }, function (e) {
        busy = false;
        if (who() !== tok) return;
        if (e.status === 401 && e.code === 'session_required') { clearTimeout(timer); return ctx.out(); }
        if (e.status === 401 || e.status === 403 || e.status === 404) { stop = true; clearTimeout(timer); return; }
        return (fail && fail(e)) || later(e);
      }).then(function () {
        if (again) { again = false; start(); } else if (!busy && pending()) push();
      });
    }

    function start() {
      if (stop || !ctx.on() || navigator.onLine === false) return;
      if (busy) { again = true; return; }
      var s = st();
      if (s.merged && pending()) { flush(); return; }
      var list = items();
      if (s.merged) { call('/account/cart', null, function (c) { land(c, sigs(list)); }, fallback); return; }
      call('/account/cart/merge', { method: 'POST', body: { items: list } }, function (c) { land(c, sigs(list)); }, function (e) {
        if (e.code !== 'cart_full' || !e.data || !e.data.cart) return fallback(e);
        //  بیش از سقفِ قلم ⇒ هیچ ادغام؛ سبدِ همین دستگاه (همانی که دیده می‌شود) روی نسخهٔ سرور نوشته می‌شود
        K.save(KEY, { v: e.data.cart.version, k: mark(e.data.cart.lines), merged: true });
        push();
        return true;
      });
    }
    function put(v, tries) {
      var list = items();
      return call('/account/cart', { method: 'PUT', body: { version: v, items: list } }, function (c) { land(c, sigs(list)); }, function (e) {
        var fresh = e.code === 'cart_changed' && e.data && e.data.cart;
        if (!fresh) return false;
        land(fresh, base());
        if (!pending()) return true;
        return tries > 0 ? put(fresh.version, tries - 1) : later(e);
      });
    }
    function flush() {
      clearTimeout(timer);
      if (stop || busy || !ctx.on()) return;
      if (Date.now() < hold) { push(); return; }
      if (!st().merged) { start(); return; }
      if (pending() && navigator.onLine !== false) put(Number(st().v) || 0, 1);
    }
    /** «خروج»: تغییرِ در صف (پس از درخواستِ در راه) پیش از خروج یک بار نوشته می‌شود؛ حداکثر ۴ ثانیه، نشد ⇒ خروج همان */
    function settle() {
      clearTimeout(timer);
      var end = Date.now() + 4000;
      var go = new Promise(function (done) {
        (function step() {
          if (stop || !ctx.on() || navigator.onLine === false || Date.now() > end) return done();
          if (busy) return setTimeout(step, 100);
          if (pending()) put(Number(st().v) || 0, 1).then(done, done); else done();
        })();
      });
      return Promise.race([go, new Promise(function (r) { setTimeout(r, 4000); })]);
    }

    window.addEventListener('online', function () {
      wait = 0; hold = 0;
      if (pending() || !st().merged || Date.now() - seen > LOOK) start();
    });
    document.addEventListener('visibilitychange', function () {
      if (document.visibilityState === 'visible' && Date.now() - seen > LOOK) start();
    });
    //  برگهٔ دیگرِ همین مرورگر سبد را عوض کرد: خودش می‌نویسد؛ این‌جا فقط اگر پس از ۳ ثانیه هنوز در صف بود
    window.addEventListener('storage', function (e) {
      if (e.key === K.cartKey(ctx.slug) && !busy) { clearTimeout(timer); timer = setTimeout(flush, Math.max(3000, hold - Date.now())); }
    });
    return { start: start, push: push, settle: settle };
  };
})();
