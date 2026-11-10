/*
 * ویترین — پایهٔ مشترکِ پوشِ وب ِ مشتری: «خبرم کن روی همین دستگاه» ِ صفحهٔ پیگیری (`store/order-push.js`) و «روی همین
 * دستگاه هم خبرم کن» ِ «خبرم کن» ِ کالا (`store/alert-push.js`). یک کارگرِ سرویس (`store/sw.js`) با دامنهٔ همین ویترین و یک
 * اشتراکِ مرورگر با کلیدِ عمومیِ سرور برای هر دو؛ سرور هر کدام را در جدولِ خودش نگه می‌دارد (`lib/store-push.js`).
 * دیربار: فقط همان دو اسکریپت پس از پاسخِ سرور با کلیدِ `push` آن را می‌خواهند (پرچمِ خاموش ⇒ هیچ بایتی).
 *
 * ⛔ هرگز خودش اجازه نمی‌پرسد: `permission()` فقط از دستگیرهٔ لمسِ مشتری صدا زده می‌شود. نه متنی، نه درخواستی به سرور.
 */
(function () {
  'use strict';
  var SW = '/store/sw.js';

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

  window.StorePush = {
    /** مرورگرِ بی پوش (سافاریِ آیفون بیرون از «افزودن به صفحهٔ اصلی»، مرورگرِ قدیمی) ⇒ هیچ بخشی */
    supported: Boolean(window.isSecureContext && navigator.serviceWorker && window.PushManager && window.Notification),
    /** صفحهٔ ویترینِ مشتری: نشانیِ خودِ دکان (`/`) یا مسیرِ `/store/<slug>` — همان دامنهٔ کارگرِ سرویس */
    page: function (slug) { return /^\/store\//.test(location.pathname) ? '/store/' + slug : '/'; },
    /** اشتراکِ همین مرورگر برای همین ویترین (کلیدِ کهنهٔ سرور ⇒ اشتراکِ تازه، بی پرسشِ دوباره) ⇒ `{endpoint, keys}` */
    subscription: function (key, page) {
      return navigator.serviceWorker.register(SW, { scope: page }).then(active).then(function (reg) {
        return reg.pushManager.getSubscription().then(function (sub) {
          if (sub && sameKey(sub, key)) return sub;
          return (sub ? sub.unsubscribe().catch(function () { return false; }) : Promise.resolve(false)).then(function () {
            return reg.pushManager.subscribe({ userVisibleOnly: true, applicationServerKey: bytes(key) });
          });
        });
      }).then(function (sub) { var j = sub.toJSON(); return { endpoint: j.endpoint, keys: j.keys }; });
    },
    /** اجازهٔ اعلان — ⛔ فقط از دستگیرهٔ لمس */
    permission: function () {
      if (Notification.permission !== 'default') return Promise.resolve(Notification.permission);
      return new Promise(function (ok) {
        var p = Notification.requestPermission(ok);   //  سافاریِ قدیمی فقط callback
        if (p && p.then) p.then(ok, function () { ok('denied'); });
      });
    },
  };
})();
