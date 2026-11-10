/*
 * ویترین — «خبرم کن روی همین دستگاه» ِ صفحهٔ پیگیری: پوشِ وب برای همین سفارش (`lib/store-push.js`، CHK-20، NTF-01).
 * فقط وقتی پاسخِ پیگیری کلیدِ `push` را دارد بار می‌شود (`order.js`؛ پرچمِ `shop.storefront.web_push` + `shop.customer_notify`
 * و سفارشِ هنوز باز). پرچمِ خاموش ⇒ نه این اسکریپت، نه واژه‌نامه‌اش (`kit/i18n/push.<زبان>.json`)، نه کارگرِ سرویس.
 *
 *   «خبرم کن» (لمسِ خودِ مشتری) ⇒ اجازهٔ اعلانِ مرورگر ⇒ کارگرِ `store/sw.js` با دامنهٔ همین ویترین ⇒ اشتراکِ پوش با کلیدِ
 *   عمومیِ سرور ⇒ `POST …/orders/<توکن>/push`. «خاموش کن» ⇒ `DELETE` ِ همان اشتراک برای همین سفارش.
 *   پس از نخستین «خبرم کن» در همین دکان، سفارشِ تازهٔ همین مرورگر بی پرسشِ دوباره وصل می‌شود (اجازه از پیش داده شده).
 *
 * ⛔ هرگز بی لمسِ مشتری اجازه نمی‌پرسد. هر متن فقط با textContent (`K.el`). «روشن است» فقط پس از پاسخِ سرور.
 * مرورگرِ بی پوش (سافاریِ آیفون بیرون از «افزودن به صفحهٔ اصلی»، مرورگرِ قدیمی) ⇒ هیچ بخشی.
 */
(function () {
  'use strict';
  var $ = K.$; var el = K.el; var t = K.t;
  var SW = '/store/sw.js';

  window.StoreOrderPush = function (ctx) {
    var api = ctx.api; var slug = ctx.slug;
    var OPT = 'store-push:' + slug;          //  مشتری در این دکان «خبرم کن» زده است
    var ATT = 'store-push-at:' + slug;       //  { توکن: نشانیِ اشتراک } — سفارش‌هایی که همین حالا وصل‌اند
    var page = /^\/store\//.test(location.pathname) ? '/store/' + slug : '/';
    var supported = Boolean(window.isSecureContext && navigator.serviceWorker && window.PushManager && window.Notification);
    var box = null; var cur = { token: '', key: '' }; var busy = false;

    function ensure() {
      if (box) return box;
      box = el('div', { class: 'card card-lg stack', hidden: true, attrs: { id: 'o-np', role: 'group', 'aria-labelledby': 'o-np-h' } });
      box.appendChild(el('h2', { class: 'label-lg', text: t('np.title'), attrs: { id: 'o-np-h' } }));
      box.appendChild(el('p', { class: 'muted small', attrs: { id: 'o-np-state' } }));
      box.appendChild(el('button', { class: 'btn btn-block', text: t('np.on'), attrs: { id: 'o-np-on', type: 'button' }, on: { click: turnOn } }));
      box.appendChild(el('button', { class: 'btn btn-ghost btn-block', text: t('np.off'), hidden: true, attrs: { id: 'o-np-off', type: 'button' }, on: { click: turnOff } }));
      box.appendChild(el('div', { class: 'note', hidden: true, attrs: { id: 'o-np-msg', role: 'status' } }));
      var at = $('o-prefs');
      at.parentNode.insertBefore(box, at);
      return box;
    }

    function attached() { var m = K.load(ATT, {}); return m && typeof m === 'object' ? m : {}; }
    function remember(token, endpoint) {
      var m = attached();
      if (endpoint) m[token] = endpoint; else delete m[token];
      var keys = Object.keys(m);
      while (keys.length > 20) delete m[keys.shift()];   //  کوچک می‌ماند
      K.save(ATT, m);
    }

    function view(state, note) {
      ensure().hidden = false;
      var on = state === 'on';
      $('o-np-state').textContent = t(on ? 'np.state.on' : (state === 'denied' ? 'np.denied' : (state === 'off' ? 'np.state.off' : 'np.hint')));
      $('o-np-on').hidden = on || state === 'denied';
      $('o-np-off').hidden = !on;
      $('o-np-on').disabled = busy; $('o-np-off').disabled = busy;
      $('o-np-on').textContent = busy ? t('np.wait') : t('np.on');
      if (note) K.note($('o-np-msg'), 'err', note); else $('o-np-msg').hidden = true;
    }

    function bytes(b64) {
      var s = String(b64).replace(/-/g, '+').replace(/_/g, '/');
      while (s.length % 4) s += '=';
      var raw = atob(s); var out = new Uint8Array(raw.length);
      for (var i = 0; i < raw.length; i++) out[i] = raw.charCodeAt(i);
      return out;
    }
    function sameKey(sub, key) {
      try {
        var a = new Uint8Array(sub.options.applicationServerKey); var b = bytes(key);
        if (a.length !== b.length) return false;
        for (var i = 0; i < a.length; i++) if (a[i] !== b[i]) return false;
        return true;
      } catch (e) { return true; }   //  مرورگری که کلید را نمی‌گوید: همان اشتراک (سرور ناجور را خودش رد می‌کند)
    }
    function active(reg) {
      if (reg.active) return Promise.resolve(reg);
      var w = reg.installing || reg.waiting;
      return new Promise(function (ok, no) {
        if (!w) { no(new Error('sw')); return; }
        w.addEventListener('statechange', function () {
          if (w.state === 'activated') ok(reg); else if (w.state === 'redundant') no(new Error('sw'));
        });
      });
    }
    /** اشتراکِ همین مرورگر برای همین ویترین (کلیدِ کهنهٔ سرور ⇒ اشتراکِ تازه، بی پرسشِ دوباره) */
    function subscription(key) {
      return navigator.serviceWorker.register(SW, { scope: page }).then(active).then(function (reg) {
        return reg.pushManager.getSubscription().then(function (sub) {
          if (sub && sameKey(sub, key)) return sub;
          return (sub ? sub.unsubscribe().catch(function () { return false; }) : Promise.resolve(false)).then(function () {
            return reg.pushManager.subscribe({ userVisibleOnly: true, applicationServerKey: bytes(key) });
          });
        });
      });
    }
    function attach(token, key) {
      return subscription(key).then(function (sub) {
        var json = sub.toJSON();
        return api('/orders/' + encodeURIComponent(token) + '/push', { method: 'POST', body: { subscription: { endpoint: json.endpoint, keys: json.keys }, page: page } })
          .then(function () { remember(token, json.endpoint); return json.endpoint; });
      });
    }
    function permission() {
      if (Notification.permission !== 'default') return Promise.resolve(Notification.permission);
      return new Promise(function (ok) {
        var p = Notification.requestPermission(ok);   //  سافاریِ قدیمی فقط callback
        if (p && p.then) p.then(ok, function () { ok('denied'); });
      });
    }

    function turnOn() {
      if (busy || !cur.token) return;
      var token = cur.token; var key = cur.key;
      busy = true; view('idle');
      permission().then(function (p) {
        if (p !== 'granted') { busy = false; view(p === 'denied' ? 'denied' : 'idle'); return null; }
        return attach(token, key).then(function () {
          busy = false;
          K.save(OPT, 1);
          if (cur.token === token) view('on');
          K.toast(t('np.state.on'));
        });
      }).catch(function (e) {
        busy = false;
        if (cur.token === token) view('idle', e && e.status ? e.message : t('np.fail'));
      });
    }

    function turnOff() {
      if (busy || !cur.token) return;
      var token = cur.token; var endpoint = attached()[token];
      busy = true; view('on');
      var done = function () { busy = false; remember(token, ''); K.save(OPT, null); if (cur.token === token) view('off'); };
      if (!endpoint) { done(); return; }
      api('/orders/' + encodeURIComponent(token) + '/push', { method: 'DELETE', body: { endpoint: endpoint } }).then(done, function (e) {
        busy = false;
        if (cur.token === token) view('on', e.message);
      });
    }

    /** با هر کشیدنِ صفحه (پیگیری هر ۲۰ ثانیه): فقط بارِ نخستِ هر سفارش کاری می‌کند */
    function render(o, token) {
      if (!supported || !o.push || !o.push.key) { if (box) box.hidden = true; return; }
      if (cur.token === token && cur.key === o.push.key) { box.hidden = false; return; }
      cur = { token: token, key: o.push.key };
      if (Notification.permission === 'denied') { view('denied'); return; }
      if (attached()[token]) { view('on'); return; }
      //  همین مرورگر در همین دکان پیش‌تر «خبرم کن» زده و اجازه دارد ⇒ سفارشِ تازه هم، بی پرسش
      if (K.load(OPT, null) === 1 && Notification.permission === 'granted') {
        busy = true; view('idle');
        attach(token, o.push.key).then(function () { busy = false; if (cur.token === token) view('on'); },
          function () { busy = false; if (cur.token === token) view('idle'); });
        return;
      }
      view('idle');
    }

    return K.part('push', $('view-order')).then(function (ok) {
      if (!ok) throw new Error('i18n');
      return { render: render };
    });
  };
})();
