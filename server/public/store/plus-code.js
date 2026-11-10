/*
 * S33 — Plus Code (Open Location Code، ده‌نویسه، دقتِ ≈ ۱۴ متر) از **پینِ** نشانی؛ همتای `core/courier/PlusCode.kt`.
 * یک منبع برای هر دو سو: ویترین (checkout.js ِ دیربار، فقط با `features.plusCode`) ⇒ `window.StorePlusCode`؛ سرور
 * (`src/lib/plus-code.js`) ⇒ `require`. هر سه با بردارِ مرجعِ `core/test-vectors/plus-code-v1.json` سنجیده می‌شوند.
 * همان حسابِ صحیحِ مرجعِ OLC: مختصات ضربدرِ دقتِ نهایی و گرد به ۶ رقم، سپس فقط عددِ صحیح؛ عرضِ ۹۰ یک پله پایین،
 * طول به [-۱۸۰، ۱۸۰). مختصاتِ نامعتبر ⇒ null. بی ژئوکدر و بی شبکه: هیچ چیز جایی فرستاده نمی‌شود.
 */
(function (root) {
  'use strict';
  var ALPHABET = '23456789CFGHJMPQRVWX';
  var LAT_MUL = 8000 * 3125;
  var LNG_MUL = 8000 * 1024;

  function encode(lat, lng) {
    if (typeof lat !== 'number' || typeof lng !== 'number' || !isFinite(lat) || !isFinite(lng)) return null;
    var la = Math.min(90, Math.max(-90, lat));
    if (la === 90) la -= 1 / 8000;
    var lo = lng;
    while (lo < -180) lo += 360;
    while (lo >= 180) lo -= 360;
    var latVal = Math.floor(Math.trunc(Math.floor((la + 90) * LAT_MUL * 1e6 + 0.5) / 1e6) / 3125);
    var lngVal = Math.floor(Math.trunc(Math.floor((lo + 180) * LNG_MUL * 1e6 + 0.5) / 1e6) / 1024);
    var out = [];
    for (var i = 4; i >= 0; i--) {
      out[i * 2] = ALPHABET.charAt(latVal % 20);
      out[i * 2 + 1] = ALPHABET.charAt(lngVal % 20);
      latVal = Math.floor(latVal / 20);
      lngVal = Math.floor(lngVal / 20);
    }
    var s = out.join('');
    return s.slice(0, 8) + '+' + s.slice(8);
  }

  var api = { encode: encode };
  if (typeof module === 'object' && module.exports) module.exports = api;
  else root.StorePlusCode = api;
})(typeof window !== 'undefined' ? window : this);
