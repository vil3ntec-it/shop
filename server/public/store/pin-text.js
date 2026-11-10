/*
 * ویترین — پین از پیوند یا مختصاتِ برنامهٔ نقشه (CHK-08، §۴.۴: «پین منبعِ اصلی»)، بی شبکه و بی ژئوکدر.
 * همزادِ `PinText.parse` ِ `core/.../shopper/ShopperAddress.kt` ِ برنامهٔ خرید با بردارِ مشترکِ
 * `core/test-vectors/pin-text-v1.json` (`server/test/store-pin-text.test.js` و `PinTextVectorsTest`): همان الگوها به همان
 * ترتیب، همان رقمِ فارسی/عربی، همان بازه و (۰، ۰) و همان گردِ شش‌رقمیِ سرور (`Math.round`، نیمه رو به بالا).
 * پیوندِ کوتاه (`maps.app.goo.gl/…`) بی شبکه باز نمی‌شود ⇒ null. دیربار از checkout.js، فقط با `store.addressExtra`.
 * ⛔ «درون/بیرونِ محدوده» را این‌جا هیچ کس نمی‌سنجد: پین به `GET /delivery` و ثبت می‌رود و سرور داور است.
 */
(function (root) {
  'use strict';
  //  `\s` ِ جاوا (بی یونیکد)، نه `\s` ِ JS که فاصله‌های یونیکد را هم می‌گیرد
  var S = '[ \\t\\n\\x0B\\f\\r]';
  var N = '(-?\\d{1,3}(?:\\.\\d+)?)';
  var PATTERNS = [
    new RegExp('^' + S + '*geo:' + N + ',' + N, 'i'),
    new RegExp('!3d' + N + '!4d' + N),
    new RegExp('[?&](?:mlat)=' + N + '&(?:amp;)?mlon=' + N),
    new RegExp('[?&](?:q|query|ll|daddr|destination|center|sll)=(?:loc:)?' + N + '(?:,|%2C)' + S + '*(?:\\+|%20)?' + N, 'i'),
    new RegExp('@' + N + ',' + N),
    new RegExp('#map=\\d{1,2}/' + N + '/' + N),
    //  مختصاتِ خالی: هر دو با اعشار (تا «۱۲ ۵» ِ بی‌معنا پین نشود)
    new RegExp('^' + S + '*(-?\\d{1,3}\\.\\d+)' + S + '*[,\u060C \\t\\n\\x0B\\f\\r]' + S + '*(-?\\d{1,3}\\.\\d+)' + S + '*$'),
  ];

  /** رقمِ فارسی و عربی ⇒ لاتین، «٫» ⇒ «.» و «٬» ⇒ «,» (همان `LocaleFormat.asciiDigits`) */
  function ascii(s) {
    return s.replace(/[\u06F0-\u06F9\u0660-\u0669\u066B\u066C]/g, function (c) {
      var k = c.charCodeAt(0);
      if (k === 0x066B) return '.';
      if (k === 0x066C) return ',';
      return String(k >= 0x06F0 ? k - 0x06F0 : k - 0x0660);
    });
  }
  function round6(v) { return Math.round(v * 1e6) / 1e6; }
  //  (۰، ۰) تقریباً همیشه «موقعیت نیامد» است، نه نشانیِ واقعی
  function valid(lat, lng) {
    return isFinite(lat) && isFinite(lng) && lat >= -90 && lat <= 90 && lng >= -180 && lng <= 180 &&
      !(Math.abs(lat) < 1e-9 && Math.abs(lng) < 1e-9);
  }

  /** نوشتهٔ چسبانده ⇒ `{lat, lng}` (شش رقمِ اعشار) یا null */
  function parse(raw) {
    var s = ascii(String(raw == null ? '' : raw)).trim();
    if (!s || s.length > 2000) return null;
    for (var i = 0; i < PATTERNS.length; i++) {
      var m = PATTERNS[i].exec(s);
      if (!m) continue;
      var lat = Number(m[1]);
      var lng = Number(m[2]);
      if (valid(lat, lng)) return { lat: round6(lat), lng: round6(lng) };
    }
    return null;
  }

  root.StorePinText = { parse: parse };
})(typeof window !== 'undefined' ? window : globalThis);
