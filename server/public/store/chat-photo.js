/*
 * ویترین — آماده‌سازیِ عکسِ مشتری برای گفت‌وگو (CHAT-05/06). دیربار: `store/chat.js` فقط وقتی مشتری عکسی برمی‌گزیند
 * این را می‌خواهد (`K.js`)؛ هیچ متنِ رابطی این‌جا نیست.
 *
 * همان قاعدهٔ برنامه (`core/chat/ChatImage.kt`): بلندترین ضلع ≤ ۱۶۰۰px و پیش‌نمایش ≤ ۳۲۰px، هرگز بزرگ‌تر از اصل؛ JPEG ِ
 * تازه از روی پیکسل با کیفیتِ ۸۲ تا ۴۰ (پیش‌نمایش ۶۰ تا ۴۰) تا زیرِ سقفی که سرور با گشودنِ گفت‌وگو می‌دهد (`limits`)،
 * و اگر باز بزرگ بود ابعاد ۰٫۸ برابر؛ شفافیت روی سفید؛ جهتِ EXIF پیش از فشرده‌سازی.
 * ⛔ رمزگشایی بی هیچ نشانی (`createImageBitmap(blob)`)؛ مرورگرِ بی آن با `data:` (CSP ِ ویترین: img-src 'self' data:) — نه blob:.
 * ⛔ JPEG ِ بوم هیچ EXIF ای ندارد (مکان، دستگاه، زمان). این‌جا فقط بار کم می‌شود، نه مرزِ امنیت: سرور نوع را از بایت،
 *   حجم، ابعادِ سرآیند و metadata را خودش دوباره می‌سنجد (`chat-media.cleanImage`).
 * ⛔ جهت: بعضی مرورگرها EXIF را هنگامِ رمزگشایی خودشان اعمال می‌کنند و بعضی نه ⇒ یک بار JPEG ِ ۲×۱ با جهتِ ۶ رمزگشایی
 *   می‌شود؛ ۱×۲ یعنی مرورگر می‌چرخاند، وگرنه چرخش/آینه این‌جا (`draw`) با جهتی که از خودِ بایت‌ها خوانده شده.
 */
(function () {
  'use strict';
  var MAIN_PX = 1600; var THUMB_PX = 320;
  var MAIN_Q = [0.82, 0.72, 0.62, 0.52, 0.4]; var THUMB_Q = [0.6, 0.5, 0.4];
  var INPUT_MAX = 40 * 1024 * 1024;   //  عکسِ گوشی چند مگابایت است؛ بیش از این را مرورگرِ گوشیِ کند باز نمی‌کند

  function u16(b, i, le) { return le ? b[i] | (b[i + 1] << 8) : (b[i] << 8) | b[i + 1]; }
  function u32(b, i, le) { return le ? u16(b, i, true) + u16(b, i + 2, true) * 65536 : u16(b, i, false) * 65536 + u16(b, i + 2, false); }

  /** جهتِ EXIF (۱ تا ۸) از آغازِ یک JPEG؛ نبود یا خراب ⇒ ۱ (همان `ChatImage.orientationOf`). */
  function orientationOf(b) {
    if (b.length < 4 || b[0] !== 0xff || b[1] !== 0xd8) return 1;
    for (var i = 2; i + 4 <= b.length;) {
      var m = b[i + 1];
      if (b[i] !== 0xff || m === 0xda || m === 0xd9) return 1;
      var len = u16(b, i + 2, false);
      if (len < 2) return 1;
      var s = i + 4;
      if (m === 0xe1 && s + 6 <= b.length && b[s] === 0x45 && b[s + 1] === 0x78 && b[s + 2] === 0x69 && b[s + 3] === 0x66 && !b[s + 4] && !b[s + 5]) {
        return tiff(b, s + 6, Math.min(b.length, i + 2 + len));
      }
      i += 2 + len;
    }
    return 1;
  }
  function tiff(b, t, end) {
    if (t + 8 > end) return 1;
    var le = b[t] === 0x49 && b[t + 1] === 0x49;
    if (!le && !(b[t] === 0x4d && b[t + 1] === 0x4d)) return 1;
    var ifd = t + u32(b, t + 4, le);
    if (ifd + 2 > end) return 1;
    for (var k = 0, n = u16(b, ifd, le); k < n; k++) {
      var e = ifd + 2 + k * 12;
      if (e + 12 > end) return 1;
      if (u16(b, e, le) === 0x0112) { var v = u16(b, e + 8, le); return v >= 1 && v <= 8 ? v : 1; }
    }
    return 1;
  }

  function bytes(blob) {
    if (blob.arrayBuffer) return blob.arrayBuffer().then(function (a) { return new Uint8Array(a); });
    return new Promise(function (ok, no) { var r = new FileReader(); r.onload = function () { ok(new Uint8Array(r.result)); }; r.onerror = no; r.readAsArrayBuffer(blob); });
  }
  function dataUrl(blob) {
    return new Promise(function (ok, no) { var r = new FileReader(); r.onload = function () { ok(String(r.result)); }; r.onerror = no; r.readAsDataURL(blob); });
  }

  /** پیکسل‌ها بی نشانیِ blob: ⇒ { src, w, h, done } */
  function decode(blob) {
    var viaImg = function () {
      return dataUrl(blob).then(function (u) {
        return new Promise(function (ok, no) {
          var img = new Image();
          img.onload = function () { ok({ src: img, w: img.naturalWidth, h: img.naturalHeight, done: function () { img.removeAttribute('src'); } }); };
          img.onerror = no;
          img.src = u;
        });
      });
    };
    if (!window.createImageBitmap) return viaImg();
    return window.createImageBitmap(blob).then(function (bm) {
      return { src: bm, w: bm.width, h: bm.height, done: function () { if (bm.close) bm.close(); } };
    }, viaImg);
  }

  function canvas(w, h) { var c = document.createElement('canvas'); c.width = w; c.height = h; return c; }
  function toBlob(c, q) {
    return new Promise(function (ok) { if (c.toBlob) c.toBlob(ok, 'image/jpeg', q); else ok(null); });
  }

  //  آیا رمزگشای همین مرورگر خودش جهتِ EXIF را اعمال می‌کند؟ (یک بار در هر بار)
  var auto = null;
  function autoOrients() {
    if (auto) return auto;
    //  APP1 ِ Exif با تنها برچسبِ Orientation = 6 درست پس از SOI (همان `withOrientation` ِ سرور، ۳۶ بایت)
    var app1 = [0xff, 0xe1, 0, 34, 0x45, 0x78, 0x69, 0x66, 0, 0, 0x4d, 0x4d, 0, 42, 0, 0, 0, 8, 0, 1,
      0x01, 0x12, 0, 3, 0, 0, 0, 1, 0, 6, 0, 0, 0, 0, 0, 0];
    auto = toBlob(canvas(2, 1), 0.9).then(bytes).then(function (j) {
      var out = new Uint8Array(j.length + app1.length);
      out.set(j.subarray(0, 2), 0); out.set(app1, 2); out.set(j.subarray(2), 2 + app1.length);
      return decode(new Blob([out], { type: 'image/jpeg' }));
    }).then(function (d) { var yes = d.w === 1 && d.h === 2; d.done(); return yes; }, function () { return true; });
    return auto;
  }

  function fit(w, h, edge) {
    var s = Math.min(1, edge / Math.max(w, h, 1));
    return [Math.max(1, Math.round(w * s)), Math.max(1, Math.round(h * s))];
  }
  //  جهت ⇐ ماتریسِ بوم (a, b, c, d و جابه‌جایی به‌ضربِ پهنا/بلندیِ خروجی)
  var T = { 2: [-1, 0, 0, 1, 1, 0], 3: [-1, 0, 0, -1, 1, 1], 4: [1, 0, 0, -1, 0, 1], 5: [0, 1, 1, 0, 0, 0],
    6: [0, 1, -1, 0, 1, 0], 7: [0, -1, -1, 0, 1, 1], 8: [0, -1, 1, 0, 0, 1] };
  /** بوم با جهتِ درست، بلندترین ضلع ≤ edge، شفافیت روی سفید. o = جهتی که هنوز اعمال نشده (۱ = هیچ). */
  function draw(src, sw, sh, o, edge) {
    var swap = o >= 5 && o <= 8;
    var f = fit(swap ? sh : sw, swap ? sw : sh, edge);
    var c = canvas(f[0], f[1]); var x = c.getContext('2d');
    x.fillStyle = '#fff'; x.fillRect(0, 0, f[0], f[1]);
    x.imageSmoothingEnabled = true; x.imageSmoothingQuality = 'high';
    var m = T[o];
    if (m) x.setTransform(m[0], m[1], m[2], m[3], m[4] * f[0], m[5] * f[1]);
    x.drawImage(src, 0, 0, swap ? f[1] : f[0], swap ? f[0] : f[1]);
    return c;
  }

  /** JPEG ِ زیرِ cap: اول کیفیت پایین می‌آید، سپس ابعاد. */
  function encode(d, o, edge, cap, qs) {
    var c = draw(d.src, d.w, d.h, o, edge); var i = 0;
    var next = function () {
      if (i >= qs.length) {
        var smaller = Math.round(Math.max(c.width, c.height) * 0.8);
        if (smaller < 64) throw new Error('size');
        return encode(d, o, smaller, cap, qs);
      }
      return toBlob(c, qs[i++]).then(function (b) {
        if (!b || b.type !== 'image/jpeg') throw new Error('encode');
        return b.size <= cap ? { blob: b, w: c.width, h: c.height } : next();
      });
    };
    return next();
  }

  /** فایلِ برگزیده ⇒ { main, thumb (Blob ِ JPEG)، width، height } — یا رد (فایلِ ناعکس، SVG، خیلی بزرگ، رمزگشایی‌نشدنی). */
  function prepare(file, lim) {
    lim = lim || {};
    var cap = lim.imageMax > 0 ? lim.imageMax : 1536 * 1024;
    var tcap = lim.thumbMax > 0 ? lim.thumbMax : 64 * 1024;
    if (!file || !file.size || file.size > INPUT_MAX || /svg/i.test(file.type || '')) return Promise.reject(new Error('input'));
    return Promise.all([bytes(file.slice(0, 131072)), autoOrients()]).then(function (r) {
      var o = r[1] ? 1 : orientationOf(r[0]);
      return decode(file).then(function (d) {
        var out = {};
        return (d.w && d.h ? Promise.resolve() : Promise.reject(new Error('decode')))
          .then(function () { return encode(d, o, MAIN_PX, cap, MAIN_Q); })
          .then(function (main) { out = { main: main.blob, width: main.w, height: main.h }; return encode(d, o, THUMB_PX, tcap, THUMB_Q); })
          .then(function (thumb) { d.done(); out.thumb = thumb.blob; return out; }, function (e) { d.done(); throw e; });
      });
    });
  }

  window.StoreChatPhoto = { prepare: prepare, dataUrl: dataUrl, orientationOf: orientationOf, draw: draw };
})();
