/**
 * CHK-08، تصمیمِ ۴-۳ «الف» — «انتخاب روی نقشه» برای پینِ نشانی در برگهٔ ثبتِ ویترین. دیربار از checkout.js، فقط وقتی
 * `features.mapPin` آمده (پرچمِ `shop.storefront.map_pin` + `STORE_MAP_TILES` ِ سرور؛ `server/src/lib/store-map.js`).
 *
 * بی کتابخانه: فقط تکه‌های ۲۵۶ پیکسلیِ همان الگو (`{z}/{x}/{y}`) با `<img>` ِ بی کوکی و Referer ِ فقط مبدأ — CSP ِ ویترین
 * همان یک مبدأ را در `img-src` دارد و اسکریپتی از بیرون نمی‌آید. نشانهٔ وسط ثابت است و نقشه زیرش جابه‌جا می‌شود
 * (کشیدن، کلیدهای جهت، +/−)؛ «همین‌جا» ⇒ همان `setPin` ِ «موقعیتِ من» (`GET /delivery`، ناحیه را فقط سرور می‌گوید).
 *
 *   window.StorePinMap.open(host, { tiles, attribution, copyright, center: {lat,lng}, t, onPick(p, text), onClose() }) ⇒ { close() }
 */
(function () {
  'use strict';
  var $ = K.$; var el = K.el;
  var SIZE = 256;
  var MIN_Z = 3; var MAX_Z = 19; var START_Z = 16;
  var STEP = 64;                         //  پیکسلِ هر فشارِ کلیدِ جهت
  var LAT_MAX = 85.05112878;

  function clampLat(v) { return Math.max(-LAT_MAX, Math.min(LAT_MAX, v)); }
  //  مختصات ⇄ پیکسلِ جهان در بزرگنمایی z (Web Mercator)
  function toPx(lat, lng, z) {
    var n = SIZE * Math.pow(2, z);
    var s = Math.sin(clampLat(lat) * Math.PI / 180);
    return { x: (lng + 180) / 360 * n, y: (0.5 - Math.log((1 + s) / (1 - s)) / (4 * Math.PI)) * n };
  }
  function toLL(x, y, z) {
    var n = SIZE * Math.pow(2, z);
    var lng = x / n * 360 - 180;
    var k = Math.PI - 2 * Math.PI * y / n;
    var lat = 180 / Math.PI * Math.atan(0.5 * (Math.exp(k) - Math.exp(-k)));
    lng = ((lng + 180) % 360 + 360) % 360 - 180;
    return { lat: Math.round(clampLat(lat) * 1e6) / 1e6, lng: Math.round(lng * 1e6) / 1e6 };
  }
  function url(tpl, z, x, y) { return tpl.replace('{z}', String(z)).replace('{x}', String(x)).replace('{y}', String(y)); }

  function open(host, o) {
    var t = o.t;
    var z = START_Z;
    var c0 = o.center && isFinite(o.center.lat) && isFinite(o.center.lng) ? o.center : { lat: 34.5553, lng: 69.2075 };
    var c = toPx(c0.lat, c0.lng, z);
    var layer = el('div', { class: 'pmap-tiles' });
    var view = el('div', { class: 'pmap-view', attrs: { tabindex: '0', role: 'group', 'aria-label': t('map.view'),
      'aria-describedby': 'pmap-help' } }, layer, el('span', { class: 'pmap-mark', attrs: { 'aria-hidden': 'true' } }));
    var where = el('span', { class: 'small muted', attrs: { 'aria-live': 'polite' } });
    var btn = function (id, key, fn) {
      return el('button', { class: 'btn btn-soft btn-sm', text: t(key), attrs: { id: id, type: 'button' }, on: { click: fn } });
    };
    var box = el('div', { class: 'pmap', attrs: { id: 'pin-map-box' } },
      view,
      el('p', { class: 'small muted', text: t('map.help'), attrs: { id: 'pmap-help' } }),
      el('div', { class: 'pin-row' },
        btn('pmap-in', 'map.in', function () { zoom(1); }),
        btn('pmap-out', 'map.out', function () { zoom(-1); }),
        el('button', { class: 'btn btn-sm', text: t('map.pick'), attrs: { id: 'pmap-pick', type: 'button' }, on: { click: pick } }),
        btn('pmap-close', 'map.close', close)),
      el('p', { class: 'small muted' }, where, ' · ' + t('map.src') + ' © ',
        el('a', { text: o.attribution, attrs: { href: o.copyright, target: '_blank', rel: 'noopener noreferrer' } })));
    host.appendChild(box);
    K.part('map', $('pin-map-box'));

    var cache = {};
    //  هیچ تکه‌ای نیامد (سرورِ نقشه بسته یا اینترنت قطع) ⇒ پیامِ روشن به جای نقشهٔ خاکستری؛ «همین‌جا» هنوز همان مختصات
    var loaded = 0; var failed = false;
    function onLoad() { loaded++; if (failed) { failed = false; draw(); } }
    function onFail() { if (!loaded && !failed) { failed = true; draw(); } }
    function draw() {
      var w = view.clientWidth || 320; var h = view.clientHeight || 240;
      var n = Math.pow(2, z);
      var left = c.x - w / 2; var top = c.y - h / 2;
      var want = {};
      for (var ty = Math.floor(top / SIZE); ty <= Math.floor((top + h) / SIZE); ty++) {
        if (ty < 0 || ty >= n) continue;
        for (var tx = Math.floor(left / SIZE); tx <= Math.floor((left + w) / SIZE); tx++) {
          var key = z + '/' + tx + '/' + ty;
          var img = cache[key];
          if (!img) {
            img = el('img', { attrs: { alt: '', width: String(SIZE), height: String(SIZE), draggable: 'false', decoding: 'async' } });
            img.referrerPolicy = 'strict-origin';
            img.onerror = onFail;
            img.onload = onLoad;
            img.src = url(o.tiles, z, ((tx % n) + n) % n, ty);
            cache[key] = img;
          }
          img.style.left = Math.round(tx * SIZE - left) + 'px';
          img.style.top = Math.round(ty * SIZE - top) + 'px';
          want[key] = true;
          if (img.parentNode !== layer) layer.appendChild(img);
        }
      }
      Object.keys(cache).forEach(function (k) {
        if (!want[k]) { if (cache[k].parentNode) layer.removeChild(cache[k]); delete cache[k]; }
      });
      var p = here();
      where.textContent = failed ? t('map.fail') : t('map.where', { c: K.fa(p.lat, 5) + ', ' + K.fa(p.lng, 5) });
    }
    function here() { return toLL(c.x, c.y, z); }
    function move(dx, dy) {
      var n = SIZE * Math.pow(2, z);
      c.x += dx;
      c.y = Math.max(0, Math.min(n, c.y + dy));
      draw();
    }
    function zoom(d) {
      var nz = Math.max(MIN_Z, Math.min(MAX_Z, z + d));
      if (nz === z) return;
      var p = here();
      z = nz; c = toPx(p.lat, p.lng, z);
      Object.keys(cache).forEach(function (k) { if (cache[k].parentNode) layer.removeChild(cache[k]); });
      cache = {};
      draw();
    }
    function pick() { var p = here(); o.onPick(p, t('pin.map.ok', { c: K.fa(p.lat, 5) + ', ' + K.fa(p.lng, 5) })); close(); }
    function close() {
      if (box.parentNode) box.parentNode.removeChild(box);
      if (o.onClose) o.onClose();
    }

    view.addEventListener('keydown', function (e) {
      var k = e.key;
      if (k === 'ArrowLeft') move(-STEP, 0); else if (k === 'ArrowRight') move(STEP, 0);
      else if (k === 'ArrowUp') move(0, -STEP); else if (k === 'ArrowDown') move(0, STEP);
      else if (k === '+' || k === '=') zoom(1); else if (k === '-') zoom(-1);
      else if (k === 'Enter') pick(); else if (k === 'Escape') close();
      else return;
      e.preventDefault();
    });
    var drag = null;
    view.addEventListener('pointerdown', function (e) {
      drag = { id: e.pointerId, x: e.clientX, y: e.clientY };
      try { view.setPointerCapture(e.pointerId); } catch (_) { /* بی گرفتن هم کار می‌کند */ }
    });
    view.addEventListener('pointermove', function (e) {
      if (!drag || drag.id !== e.pointerId) return;
      var dx = e.clientX - drag.x; var dy = e.clientY - drag.y;
      drag.x = e.clientX; drag.y = e.clientY;
      move(-dx, -dy);
    });
    var end = function () { drag = null; };
    view.addEventListener('pointerup', end);
    view.addEventListener('pointercancel', end);

    draw();
    view.focus();
    return { close: close };
  }

  window.StorePinMap = { open: open, toPx: toPx, toLL: toLL };
})();
