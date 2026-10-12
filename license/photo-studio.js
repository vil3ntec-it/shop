/* ==========================================================
   استودیوی عکسِ کالا + همگام‌سازیِ عکس + گوشی به‌عنوانِ بارکدخوان (وب)
   ----------------------------------------------------------
   همان `PhotoStudio.kt` و `PhotoSync.kt` و `PhoneScanner.kt`ِ برنامه:
   • استودیو: زمینهٔ سفید و جدا کردنِ کالا (سیل از لبه‌ها)، برش و
     هم‌اندازه (مربعِ ۸۰۰)، بهبودِ نور و رنگ، تیز کردن — همه روی
     ImageDataِ بوم، بی کتابخانه و آفلاین
   • همگام‌سازی: عکس روی حساب (‎/api/sync/v1/photos‎)؛ عکسی که روی گوشی یا
     کامپیوتر گذاشته شد این‌جا هم می‌آید
   • بارکدخوان: کدِ شش‌رقمیِ کامپیوتر ⇒ بارکدی که دوربینِ آیفون می‌خواند
     در سبدِ کامپیوتر می‌نشیند
   ========================================================== */
(function () {
  'use strict';

  /* ------------------------------ الگوریتم ------------------------------ */
  var WORK_EDGE = 1200, OUT_EDGE = 800;
  var DEFAULTS = { removeBackground: true, tolerance: 28, autoCrop: true, margin: 8, enhance: true, sharpen: 50, brightness: 0, rotate: 0, outEdge: OUT_EDGE };

  function img(w, h, px) { return { w: w, h: h, px: px || new Uint8ClampedArray(w * h * 4) }; }
  function clamp(v) { return v < 0 ? 0 : v > 255 ? 255 : v; }
  function dist2(a, i, b, j) {
    var dr = a[i] - b[j], dg = a[i + 1] - b[j + 1], db = a[i + 2] - b[j + 2];
    return (dr * dr * 0.3 + dg * dg * 0.59 + db * db * 0.11) * 1.9;
  }

  function fromCanvasImage(source, maxEdge) {
    var sw = source.naturalWidth || source.width, sh = source.naturalHeight || source.height;
    var r = Math.min(1, maxEdge / Math.max(sw, sh));
    var w = Math.max(1, Math.round(sw * r)), h = Math.max(1, Math.round(sh * r));
    var cv = document.createElement('canvas'); cv.width = w; cv.height = h;
    var ctx = cv.getContext('2d');
    ctx.imageSmoothingQuality = 'high';
    ctx.drawImage(source, 0, 0, w, h);
    return img(w, h, ctx.getImageData(0, 0, w, h).data);
  }

  function toCanvas(im) {
    var cv = document.createElement('canvas'); cv.width = im.w; cv.height = im.h;
    cv.getContext('2d').putImageData(new ImageData(new Uint8ClampedArray(im.px), im.w, im.h), 0, 0);
    return cv;
  }

  function resize(im, w, h) {
    if (w === im.w && h === im.h) return im;
    var src = toCanvas(im), cv = document.createElement('canvas'); cv.width = w; cv.height = h;
    var ctx = cv.getContext('2d'); ctx.imageSmoothingQuality = 'high';
    ctx.drawImage(src, 0, 0, w, h);
    return img(w, h, ctx.getImageData(0, 0, w, h).data);
  }

  function rotate(im, deg) {
    var d = ((deg % 360) + 360) % 360;
    if (!d) return im;
    var src = toCanvas(im), swap = d === 90 || d === 270;
    var cv = document.createElement('canvas'); cv.width = swap ? im.h : im.w; cv.height = swap ? im.w : im.h;
    var ctx = cv.getContext('2d');
    ctx.translate(cv.width / 2, cv.height / 2); ctx.rotate(d * Math.PI / 180);
    ctx.drawImage(src, -im.w / 2, -im.h / 2);
    return img(cv.width, cv.height, ctx.getImageData(0, 0, cv.width, cv.height).data);
  }

  function enhance(im) {
    var p = im.px, n = im.w * im.h;
    var hr = new Uint32Array(256), hg = new Uint32Array(256), hb = new Uint32Array(256), sr = 0, sg = 0, sb = 0;
    for (var i = 0; i < p.length; i += 4) { hr[p[i]]++; hg[p[i + 1]]++; hb[p[i + 2]]++; sr += p[i]; sg += p[i + 1]; sb += p[i + 2]; }
    function lo(h) { var s = 0; for (var k = 0; k < 256; k++) { s += h[k]; if (s > n * 0.005) return k; } return 0; }
    function hi(h) { var s = 0; for (var k = 255; k >= 0; k--) { s += h[k]; if (s > n * 0.005) return k; } return 255; }
    var l = Math.min(lo(hr), lo(hg), lo(hb)), u = Math.max(hi(hr), hi(hg), hi(hb)), span = Math.max(1, u - l);
    var avg = (sr + sg + sb) / (3 * n);
    function gain(s) { var m = s / n; return m < 1 ? 1 : 1 + (avg / m - 1) * 0.5; }
    var gr = gain(sr), gg = gain(sg), gb = gain(sb);
    var lut = new Uint8ClampedArray(256);
    for (var k = 0; k < 256; k++) lut[k] = clamp(Math.round((k - l) * 255 / span));
    var out = new Uint8ClampedArray(p.length);
    for (var j = 0; j < p.length; j += 4) {
      out[j] = clamp(lut[p[j]] * gr); out[j + 1] = clamp(lut[p[j + 1]] * gg); out[j + 2] = clamp(lut[p[j + 2]] * gb); out[j + 3] = p[j + 3];
    }
    return img(im.w, im.h, out);
  }

  function brightness(im, amount) {
    var k = Math.max(-50, Math.min(50, amount)) * 2.2, p = im.px, out = new Uint8ClampedArray(p.length);
    for (var i = 0; i < p.length; i += 4) { out[i] = p[i] + k; out[i + 1] = p[i + 1] + k; out[i + 2] = p[i + 2] + k; out[i + 3] = p[i + 3]; }
    return img(im.w, im.h, out);
  }

  function sharpen(im, amount) {
    var w = im.w, h = im.h, p = im.px, out = new Uint8ClampedArray(p), a = Math.max(0, Math.min(1, amount)) * 1.2;
    if (w < 3 || h < 3) return im;
    for (var y = 1; y < h - 1; y++) for (var x = 1; x < w - 1; x++) {
      var i = (y * w + x) * 4;
      for (var c = 0; c < 3; c++) {
        var s = 0;
        for (var dy = -1; dy <= 1; dy++) for (var dx = -1; dx <= 1; dx++) s += p[i + (dy * w + dx) * 4 + c];
        var d = p[i + c] - s / 9;
        if (Math.abs(d) >= 4) out[i + c] = clamp(p[i + c] + d * a);
      }
    }
    return img(w, h, out);
  }

  function borderColor(im) {
    var w = im.w, h = im.h, p = im.px, step = Math.max(1, Math.floor((w + h) / 400)), idx = [];
    for (var x = 0; x < w; x += step) { idx.push(x * 4, ((h - 1) * w + x) * 4); }
    for (var y = 0; y < h; y += step) { idx.push(y * w * 4, (y * w + w - 1) * 4); }
    if (!idx.length) return null;
    function med(c) { var v = idx.map(function (i) { return p[i + c]; }).sort(function (a, b) { return a - b; }); return v[v.length >> 1]; }
    var bg = new Uint8ClampedArray([med(0), med(1), med(2), 255]);
    var near = 0;
    for (var k = 0; k < idx.length; k++) if (dist2(p, idx[k], bg, 0) <= 45 * 45) near++;
    return near < idx.length * 0.4 ? null : bg;
  }

  function foregroundMask(im, tolerance) {
    var w = im.w, h = im.h, n = w * h, p = im.px, bg = borderColor(im);
    if (!bg) return null;
    var tol = Math.max(5, Math.min(80, tolerance)), tol2 = tol * tol, step2 = Math.pow(tol * 0.6, 2);
    var isBg = new Uint8Array(n), q = new Int32Array(n), head = 0, tail = 0;
    function seed(i) { if (!isBg[i] && dist2(p, i * 4, bg, 0) <= tol2) { isBg[i] = 1; q[tail++] = i; } }
    for (var x = 0; x < w; x++) { seed(x); seed((h - 1) * w + x); }
    for (var y = 0; y < h; y++) { seed(y * w); seed(y * w + w - 1); }
    function go(i, j) {
      if (isBg[j]) return;
      var dbg = dist2(p, j * 4, bg, 0);
      if (dbg <= tol2 || (dist2(p, j * 4, p, i * 4) <= step2 && dbg <= tol2 * 2.2)) { isBg[j] = 1; q[tail++] = j; }
    }
    while (head < tail) {
      var i = q[head++], xx = i % w, yy = (i / w) | 0;
      if (xx > 0) go(i, i - 1); if (xx < w - 1) go(i, i + 1); if (yy > 0) go(i, i - w); if (yy < h - 1) go(i, i + w);
    }
    if (tail < n * 0.05 || tail > n * 0.985) return null;
    keepMain(isBg, w, h);
    return feather(isBg, w, h);
  }

  function keepMain(isBg, w, h) {
    var n = w * h, label = new Int32Array(n), sizes = [0], q = new Int32Array(n);
    for (var s = 0; s < n; s++) {
      if (isBg[s] || label[s]) continue;
      var id = sizes.length, head = 0, tail = 0; q[tail++] = s; label[s] = id;
      while (head < tail) {
        var i = q[head++], x = i % w, y = (i / w) | 0;
        if (x > 0 && !isBg[i - 1] && !label[i - 1]) { label[i - 1] = id; q[tail++] = i - 1; }
        if (x < w - 1 && !isBg[i + 1] && !label[i + 1]) { label[i + 1] = id; q[tail++] = i + 1; }
        if (y > 0 && !isBg[i - w] && !label[i - w]) { label[i - w] = id; q[tail++] = i - w; }
        if (y < h - 1 && !isBg[i + w] && !label[i + w]) { label[i + w] = id; q[tail++] = i + w; }
      }
      sizes.push(tail);
    }
    var big = Math.max.apply(null, sizes), keep = Math.max(16, big * 0.005);
    for (var k = 0; k < n; k++) if (!isBg[k] && sizes[label[k]] < keep) isBg[k] = 1;
  }

  function feather(isBg, w, h) {
    var m = new Float32Array(w * h), out;
    for (var i = 0; i < m.length; i++) m[i] = isBg[i] ? 0 : 1;
    out = new Float32Array(m);
    for (var y = 0; y < h; y++) for (var x = 0; x < w; x++) {
      var k = y * w + x;
      var edge = (x > 0 && isBg[k - 1] !== isBg[k]) || (x < w - 1 && isBg[k + 1] !== isBg[k]) || (y > 0 && isBg[k - w] !== isBg[k]) || (y < h - 1 && isBg[k + w] !== isBg[k]);
      if (!edge) continue;
      for (var dy = -2; dy <= 2; dy++) for (var dx = -2; dx <= 2; dx++) {
        var xx = x + dx, yy = y + dy;
        if (xx < 0 || yy < 0 || xx >= w || yy >= h) continue;
        var s = 0, c = 0;
        for (var ey = -1; ey <= 1; ey++) for (var ex = -1; ex <= 1; ex++) {
          var ax = xx + ex, ay = yy + ey;
          if (ax >= 0 && ay >= 0 && ax < w && ay < h) { s += m[ay * w + ax]; c++; }
        }
        out[yy * w + xx] = s / c;
      }
    }
    return out;
  }

  function onWhite(im, mask) {
    var p = im.px, out = new Uint8ClampedArray(p.length);
    for (var i = 0, j = 0; i < p.length; i += 4, j++) {
      var a = mask[j] * (p[i + 3] / 255);
      out[i] = p[i] * a + 255 * (1 - a); out[i + 1] = p[i + 1] * a + 255 * (1 - a); out[i + 2] = p[i + 2] * a + 255 * (1 - a); out[i + 3] = 255;
    }
    return img(im.w, im.h, out);
  }

  function bbox(mask, w, h) {
    var l = w, t = h, r = -1, b = -1;
    for (var y = 0; y < h; y++) for (var x = 0; x < w; x++) if (mask[y * w + x] > 0.5) {
      if (x < l) l = x; if (x > r) r = x; if (y < t) t = y; if (y > b) b = y;
    }
    return r < l ? null : { l: l, t: t, r: r + 1, b: b + 1 };
  }

  function placeOnSquare(im, box, margin, edge) {
    var b = box || { l: 0, t: 0, r: im.w, b: im.h };
    var src = toCanvas(im), cv = document.createElement('canvas'); cv.width = edge; cv.height = edge;
    var ctx = cv.getContext('2d'); ctx.fillStyle = '#fff'; ctx.fillRect(0, 0, edge, edge); ctx.imageSmoothingQuality = 'high';
    var bw = b.r - b.l, bh = b.b - b.t, inner = edge * (1 - 2 * Math.max(0, Math.min(25, margin)) / 100);
    var r = inner / Math.max(bw, bh), dw = Math.max(1, Math.round(bw * r)), dh = Math.max(1, Math.round(bh * r));
    ctx.drawImage(src, b.l, b.t, bw, bh, Math.floor((edge - dw) / 2), Math.floor((edge - dh) / 2), dw, dh);
    return img(edge, edge, ctx.getImageData(0, 0, edge, edge).data);
  }

  function process(source, o) {
    o = Object.assign({}, DEFAULTS, o || {});
    var im = source;
    if (Math.max(im.w, im.h) > WORK_EDGE) { var s = WORK_EDGE / Math.max(im.w, im.h); im = resize(im, Math.round(im.w * s), Math.round(im.h * s)); }
    im = rotate(im, o.rotate);
    if (o.enhance) im = enhance(im);
    if (o.brightness) im = brightness(im, o.brightness);
    var mask = o.removeBackground ? foregroundMask(im, o.tolerance) : null;
    if (mask) im = onWhite(im, mask);
    var box = o.autoCrop && mask ? bbox(mask, im.w, im.h) : null;
    im = placeOnSquare(im, box, o.margin, o.outEdge);
    if (o.sharpen > 0) im = sharpen(im, o.sharpen / 100);
    return im;
  }

  /* ------------------------------ پنجرهٔ استودیو ------------------------------ */

  function loadImage(file) {
    return new Promise(function (resolve, reject) {
      var url = URL.createObjectURL(file), el = new Image();
      el.onload = function () { URL.revokeObjectURL(url); resolve(el); };
      el.onerror = function () { URL.revokeObjectURL(url); reject(new Error('عکس باز نشد')); };
      el.src = url;
    });
  }

  function toBlob(im) {
    return new Promise(function (resolve, reject) {
      toCanvas(im).toBlob(function (b) { b ? resolve(b) : reject(new Error('ذخیره نشد')); }, 'image/jpeg', 0.8);
    });
  }

  /** پنجره را باز می‌کند؛ با «ذخیره» Blobِ JPEG، با «انصراف» null */
  function open(file) {
    return loadImage(file).then(function (el) {
      var original = fromCanvasImage(el, WORK_EDGE), small = fromCanvasImage(el, 600);
      var opts = Object.assign({}, DEFAULTS);
      return new Promise(function (resolve) {
        var wrap = document.createElement('div');
        wrap.className = 'ps-scrim';
        wrap.innerHTML =
          '<div class="ps-box" role="dialog" aria-label="استودیوی عکس">' +
          '<h3>استودیوی عکسِ کالا</h3>' +
          '<div class="ps-previews"><figure><figcaption>پیش</figcaption><canvas data-ps-before></canvas></figure>' +
          '<figure><figcaption>پس — همین ذخیره می‌شود</figcaption><canvas data-ps-after></canvas></figure></div>' +
          '<div class="ps-controls">' +
          '<label><input type="checkbox" data-ps="removeBackground" checked> زمینهٔ سفید و جدا کردنِ کالا</label>' +
          '<label class="ps-range">حساسیتِ زمینه <input type="range" min="5" max="80" value="28" data-ps="tolerance"></label>' +
          '<label><input type="checkbox" data-ps="autoCrop" checked> برش و هم‌اندازه (مربع)</label>' +
          '<label class="ps-range">فاصله تا لبه <input type="range" min="0" max="25" value="8" data-ps="margin"></label>' +
          '<label><input type="checkbox" data-ps="enhance" checked> بهبودِ کیفیت (نور، رنگ، وضوح)</label>' +
          '<label class="ps-range">تیزی <input type="range" min="0" max="100" value="50" data-ps="sharpen"></label>' +
          '<label class="ps-range">روشنایی <input type="range" min="-50" max="50" value="0" data-ps="brightness"></label>' +
          '<button type="button" class="btn btn-sm" data-ps-rotate>چرخش ۹۰°</button>' +
          '</div>' +
          '<div class="ps-actions"><button type="button" class="btn btn-primary" data-ps-save>ذخیره</button>' +
          '<button type="button" class="btn" data-ps-plain>ذخیرهٔ بی ویرایش</button>' +
          '<button type="button" class="btn" data-ps-cancel>انصراف</button></div></div>';
        document.body.appendChild(wrap);
        var before = wrap.querySelector('[data-ps-before]'), after = wrap.querySelector('[data-ps-after]');
        before.width = small.w; before.height = small.h;
        before.getContext('2d').putImageData(new ImageData(new Uint8ClampedArray(small.px), small.w, small.h), 0, 0);
        var timer = 0;
        function render() {
          clearTimeout(timer);
          timer = setTimeout(function () {
            var out = process(small, Object.assign({}, opts, { outEdge: 400 }));
            after.width = out.w; after.height = out.h;
            after.getContext('2d').putImageData(new ImageData(out.px, out.w, out.h), 0, 0);
          }, 120);
        }
        wrap.addEventListener('input', function (e) {
          var k = e.target && e.target.dataset && e.target.dataset.ps;
          if (!k) return;
          opts[k] = e.target.type === 'checkbox' ? e.target.checked : Number(e.target.value);
          render();
        });
        function close(v) { clearTimeout(timer); wrap.remove(); resolve(v); }
        wrap.querySelector('[data-ps-rotate]').onclick = function () { opts.rotate = (opts.rotate + 90) % 360; render(); };
        wrap.querySelector('[data-ps-cancel]').onclick = function () { close(null); };
        wrap.querySelector('[data-ps-save]').onclick = function () { toBlob(process(original, opts)).then(close, function () { close(null); }); };
        wrap.querySelector('[data-ps-plain]').onclick = function () {
          var s = Math.min(1, OUT_EDGE / Math.max(original.w, original.h));
          toBlob(resize(original, Math.round(original.w * s), Math.round(original.h * s))).then(close, function () { close(null); });
        };
        render();
      });
    });
  }

  /* ------------------------------ همگام‌سازیِ عکس ------------------------------ */

  var acc = window.TohidAccount;
  var LS = 'tohid-photo-sync';
  function st() { try { return JSON.parse(localStorage.getItem(LS) || '{}'); } catch (e) { return {}; } }
  function save(s) { try { localStorage.setItem(LS, JSON.stringify(s)); } catch (e) { /* پر */ } }
  var running = false, again = false;
  var hooks = null;   // { get(id) ⇒ Promise<Blob|null>, put(id, blob), remove(id), localIds() ⇒ Promise<string[]>, changed() }

  function b64(blob) {
    return new Promise(function (res, rej) {
      var r = new FileReader();
      r.onload = function () { res(String(r.result).split(',')[1] || ''); };
      r.onerror = rej; r.readAsDataURL(blob);
    });
  }
  function unb64(s, type) {
    var bin = atob(s), u = new Uint8Array(bin.length);
    for (var i = 0; i < bin.length; i++) u[i] = bin.charCodeAt(i);
    return new Blob([u], { type: type || 'image/jpeg' });
  }

  function mark(kind, id) {
    var s = st(); s.up = s.up || []; s.del = s.del || [];
    s.up = s.up.filter(function (x) { return x !== id; }); s.del = s.del.filter(function (x) { return x !== id; });
    s[kind].push(id); save(s); kick();
  }

  async function run() {
    if (!hooks || !acc || !acc.isSignedIn || !acc.isSignedIn() || navigator.onLine === false) return;
    var s = st(); s.up = s.up || []; s.del = s.del || []; s.h = s.h || {};
    if (!s.seeded) { (await hooks.localIds()).forEach(function (id) { if (s.up.indexOf(id) < 0) s.up.push(id); }); s.seeded = true; save(s); }
    var dev = acc.deviceId ? acc.deviceId() : '';
    for (var id of s.up.slice()) {
      var blob = await hooks.get(id);
      if (blob) {
        var r = await acc.authFetch('/api/sync/v1/photos/' + encodeURIComponent(id), { method: 'PUT', body: { data: await b64(blob), device_id: dev } });
        if (r && r.hash) s.h[id] = r.hash;
      }
      s.up = s.up.filter(function (x) { return x !== id; }); save(s);
    }
    for (var d of s.del.slice()) {
      await acc.authFetch('/api/sync/v1/photos/' + encodeURIComponent(d) + '?device_id=' + encodeURIComponent(dev), { method: 'DELETE' });
      s.del = s.del.filter(function (x) { return x !== d; }); delete s.h[d]; save(s);
    }
    var got = 0;
    for (var page = 0; page < 40; page++) {
      var list = await acc.authFetch('/api/sync/v1/photos?since=' + (s.rev || 0));
      for (var it of (list.items || [])) {
        if (s.up.indexOf(it.product_id) >= 0) continue;
        if (it.deleted) { await hooks.remove(it.product_id); delete s.h[it.product_id]; got++; }
        else if (it.hash && s.h[it.product_id] !== it.hash) {
          var one = await acc.authFetch('/api/sync/v1/photos/' + encodeURIComponent(it.product_id));
          if (one && one.data) { await hooks.put(it.product_id, unb64(one.data, one.type)); s.h[it.product_id] = it.hash; got++; }
        }
      }
      s.rev = list.head || s.rev || 0; save(s);
      if (!list.has_more) break;
    }
    if (got && hooks.changed) hooks.changed();
  }

  function kick() {
    if (running) { again = true; return; }
    running = true;
    run().catch(function () { /* بارِ بعد */ }).then(function () {
      running = false;
      if (again) { again = false; kick(); }
    });
  }

  /* ------------------------------ گوشی به‌عنوانِ بارکدخوان ------------------------------ */

  var SC = 'tohid-phone-scanner';
  function sc() { try { return JSON.parse(localStorage.getItem(SC) || '{}'); } catch (e) { return {}; } }
  function scSave(v) { try { localStorage.setItem(SC, JSON.stringify(v)); } catch (e) { /* پر */ } }
  var scanner = {
    get listenCode() { return sc().listen || null; },
    get sendCode() { return sc().send || null; },
    startListening: function () { var v = sc(); v.listen = String(100000 + Math.floor(Math.random() * 900000)); scSave(v); return v.listen; },
    stopListening: function () { var v = sc(); delete v.listen; scSave(v); },
    pairTo: function (code) {
      var c = String(code || '').replace(/[۰-۹]/g, function (d) { return '۰۱۲۳۴۵۶۷۸۹'.indexOf(d); }).replace(/\D/g, '');
      if (c.length !== 6) return false;
      var v = sc(); v.send = c; scSave(v); return true;
    },
    unpair: function () { var v = sc(); delete v.send; scSave(v); },
    /** آیفون: بارکد به کامپیوتر برود؟ */
    send: function (code) {
      var pair = sc().send;
      if (!pair || !window.TohidSync || !window.TohidSync.relay) return false;
      return window.TohidSync.relay({ type: 'relay', event: 'scan', code: String(code), pair: pair });
    },
  };

  window.addEventListener('tohid:live', function (e) {
    var msg = e.detail || {};
    if (msg.event === 'photos' || msg.event === 'hello') kick();
    if (msg.event === 'scan' && msg.code && sc().listen && msg.pair === sc().listen) {
      window.dispatchEvent(new CustomEvent('tohid:phone-scan', { detail: { code: String(msg.code) } }));
    }
  });

  window.TohidPhotoStudio = {
    open: open, process: process, DEFAULTS: DEFAULTS,
    sync: {
      bind: function (h) { hooks = h; kick(); },
      changed: function (id) { mark('up', id); },
      removed: function (id) { mark('del', id); },
      kick: kick,
    },
    scanner: scanner,
  };
})();
