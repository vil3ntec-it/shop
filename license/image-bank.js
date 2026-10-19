/* ==========================================================
   بانکِ تصاویر — سمتِ برنامهٔ وب (v3 ۳۴.۲، ۳۶.۸، ۳۷.۵ تا ۳۷.۷)
   ----------------------------------------------------------
   سرور: ‎server/src/routes/shop-images.js‎ (‎/api/shop/images‎).

   • «انتخاب از بانکِ تصاویر» در فرمِ کالا: جست‌وجو با بارکد یا نام،
     پیش‌نمایش، و انتخاب. تطبیقِ «دقیق» (بارکد/SKU) و «محتمل» (نام) جدا
     و با دلیل نشان داده می‌شوند؛ هیچ امتیازِ عددی‌ای قطعیت وانمود نمی‌شود.
     تطبیقِ غیرِ دقیق بی تیکِ «همین کالاست» انتخاب نمی‌شود.
   • آماده‌سازیِ عکس روی خودِ دستگاه: چرخش طبقِ EXIF روی پیکسل‌ها می‌نشیند،
     کوچک می‌شود (نمایش ۱۲۰۰، کوچک ۴۰۰ پیکسل — همان سقف‌های سرور) و چون
     دوباره کدگذاری می‌شود هیچ EXIF/GPS/توضیحی با آن نمی‌رود.
   • ورودِ گروهیِ عکس‌ها برای صاحبِ دکان: هر فایل با بارکد یا نامِ دقیقِ
     کالا (یا یک CSV) به کالا وصل می‌شود؛ مبهم و بی‌تطبیق حدس زده نمی‌شوند؛
     نتیجهٔ هر ردیف جدا؛ کار و فایل‌ها در IndexedDB می‌مانند تا پس از بستنِ
     برنامه از همان‌جا ادامه یابد.

   ⛔ پرچمِ `shop.image_bank` خاموش ⇒ `/config` ۴۰۳ است، دکمهٔ بانک پنهان
      می‌ماند و هیچ درخواستی به بانک نمی‌رود؛ عکسِ کالا همان راهِ امروز را
      می‌رود (IndexedDB + ‎/api/sync/v1/photos‎).
   ⛔ قیمت، موجودی و SKUِ دکان از بانک کپی نمی‌شود؛ فقط نام/بارکدِ خالیِ
      ردیف پر می‌شود و عکس.
   ⛔ هیچ قابلیتِ هوشِ مصنوعی این‌جا نیست (BLOCKED-EXTERNAL) — دکمه‌اش هم نه.
   ========================================================== */
(function () {
  'use strict';

  var BASE = '/api/shop/images';
  //  همان ‎DERIV‎ِ سرور (lib/image-bank.js) — عکسِ دکان حداکثر ۶۰۰ کیلوبایت
  var SIZES = {
    display: { side: 1200, bytes: 600 * 1024 },
    thumb: { side: 400, bytes: 150 * 1024 },
  };
  var MIN_SIDE = 300;
  var MAX_PIXELS = 60 * 1000 * 1000;
  var LS_CFG = 'tohid-imgbank-cfg-v1';
  var LS_OUT = 'tohid-imgbank-outbox-v1';
  var DB_NAME = 'tohid-imgbank';

  var KIND = {
    barcode_exact: 'بارکدِ دقیق', sku_exact: 'SKUِ دقیق', name_exact: 'نامِ کاملِ یکسان',
    name_words: 'همهٔ کلمه‌های نام', spelling_tolerant: 'املای نزدیک',
  };
  var DIFF = { brand: 'برند', variant: 'طعم/مدل', size: 'وزن/حجم', pack_count: 'تعداد در بسته', packaging: 'بسته‌بندی' };

  var hooks = null;
  var state = { enabled: readJson(LS_CFG, {}).enabled === true, checked: false };

  /* ------------------------------ کمک‌ها ------------------------------ */
  function readJson(k, d) { try { var v = JSON.parse(localStorage.getItem(k) || 'null'); return v == null ? d : v; } catch (e) { return d; } }
  function writeJson(k, v) { try { localStorage.setItem(k, JSON.stringify(v)); } catch (e) { /* حالتِ خصوصی */ } }
  function esc(s) {
    return String(s == null ? '' : s).replace(/[&<>"']/g, function (c) {
      return { '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c];
    });
  }
  function T() { return window.TohidText || null; }
  function digitsOf(s) {
    var t = T();
    return (t ? t.asciiDigits(String(s || '')) : String(s || '')).replace(/[^0-9]/g, '');
  }
  function nameKey(s) {
    var t = T();
    if (t) return t.compareKey(String(s || ''));
    return String(s || '').trim().replace(/\s+/g, ' ').replace(/ي/g, 'ی').replace(/ك/g, 'ک').toLowerCase();
  }
  /** بارکدِ معتبر (GTIN-8/12/13/14 با رقمِ کنترلِ درست) ⇒ ۱۴ رقم؛ وگرنه '' — همان ‎image-identity.gtin‎ِ سرور */
  function gtin14(raw) {
    var d = digitsOf(raw);
    if ([8, 12, 13, 14].indexOf(d.length) < 0) return '';
    var sum = 0;
    for (var i = d.length - 2, w = 3; i >= 0; i--, w = 4 - w) sum += Number(d[i]) * w;
    return (10 - (sum % 10)) % 10 === Number(d[d.length - 1]) ? d.padStart(14, '0') : '';
  }
  function err(message, code, status) { var e = new Error(message); e.code = code || ''; e.status = status || 0; return e; }
  function transient(e) {
    return !!e && (e.code === 'network' || e.code === 'offline' || e.status === 429 || e.status >= 500);
  }
  function emit(name, detail) { try { window.dispatchEvent(new CustomEvent(name, { detail: detail })); } catch (e) { /* قدیمی */ } }
  function toast(msg, kind) { try { if (hooks && hooks.toast) hooks.toast(msg, kind); } catch (e) { /* بی صدا */ } }

  /* ------------------------------ شبکه ------------------------------ */
  function acc() { return window.TohidAccount || null; }
  function signedIn() { var a = acc(); return !!(a && a.isSignedIn && a.isSignedIn()); }

  async function api(path, opts) {
    if (!signedIn()) throw err('برای بانکِ تصاویر باید وارد حساب باشید', 'no_session', 401);
    if (navigator.onLine === false) throw err('اینترنت قطع است', 'offline');
    return acc().authFetch(BASE + path, opts || {});
  }

  /** بدنهٔ خام (عکس) یا پاسخِ خام (فایل) — همان سرآیندهای قراردادِ `call` */
  async function raw(method, path, body, want) {
    var a = acc();
    var base = window.TohidApiConfig && window.TohidApiConfig.baseUrl ? window.TohidApiConfig.baseUrl() : '';
    if (!a || !base) throw err('نشانی سرور تنظیم نشده است', 'no_server');
    if (navigator.onLine === false) throw err('اینترنت قطع است', 'offline');
    async function once(token) {
      var headers = { 'X-App': a.appId(), 'X-App-Id': a.appId(), 'X-Device': a.deviceId(), Authorization: 'Bearer ' + token };
      if (a.appVersion && a.appVersion()) headers['X-App-Version'] = a.appVersion();
      if (body) headers['Content-Type'] = 'application/octet-stream';
      try { return await fetch(base + BASE + path, { method: method, headers: headers, body: body || undefined }); }
      catch (e) { throw err('به سرور نرسیدیم', 'network'); }
    }
    var token = await a.accessToken();
    if (!token) throw err('برای این کار باید وارد حساب شوید', 'no_session', 401);
    var res = await once(token);
    if (res.status === 401 && a.refresh) {
      var fresh = '';
      try { fresh = await a.refresh(); } catch (e) { fresh = ''; }
      if (fresh) res = await once(fresh);
    }
    if (!res.ok) {
      var data = null;
      try { data = await res.json(); } catch (e) { data = null; }
      var x = data && data.error;
      throw err((x && x.message) || ('خطای سرور (' + res.status + ')'), (x && x.code) || ('http_' + res.status), res.status);
    }
    if (want === 'blob') return res.blob();
    try { return await res.json(); } catch (e) { return {}; }
  }

  /** نشانیِ فایلی که سرور داد (‎/api/shop/images/file/…‎) ⇒ Blob با نشستِ کاربر */
  function fileBlob(url) {
    var u = String(url || '');
    if (u.indexOf(BASE + '/file/') !== 0) return Promise.reject(err('نشانیِ عکس درست نیست', 'bad_url'));
    return raw('GET', u.slice(BASE.length), null, 'blob');
  }

  /* ------------------------------ پرچم ------------------------------ */
  function setEnabled(v) {
    state.checked = true;
    var was = state.enabled;
    state.enabled = v === true;
    writeJson(LS_CFG, { enabled: state.enabled, at: Date.now() });
    if (was !== state.enabled) emit('tohid:image-bank', { enabled: state.enabled });
    if (state.enabled) drain();
  }

  /** ‎/config‎: ۲۰۰ ⇒ روشن؛ ۴۰۳/۴۰۴/بی‌حساب ⇒ خاموش؛ بی اینترنت ⇒ آخرین حالِ دانسته */
  async function refreshConfig() {
    if (!signedIn()) { setEnabled(false); return false; }
    try {
      var c = await api('/config');
      setEnabled(!!(c && c.enabled === true));
    } catch (e) {
      if (!transient(e)) setEnabled(false);
    }
    return state.enabled;
  }

  /* ------------------------- آماده‌سازیِ عکس ------------------------- */
  /** رمزگشایی با چرخشِ EXIF روی پیکسل‌ها ('from-image') */
  async function decode(blob) {
    if (typeof createImageBitmap === 'function') {
      try { return await createImageBitmap(blob, { imageOrientation: 'from-image' }); } catch (e) { /* مرورگرِ قدیمی */ }
    }
    return new Promise(function (resolve, reject) {
      var url = URL.createObjectURL(blob);
      var im = new Image();
      im.onload = function () { URL.revokeObjectURL(url); resolve(im); };
      im.onerror = function () { URL.revokeObjectURL(url); reject(err('این فایل عکس نیست یا خراب است', 'image_corrupt')); };
      im.src = url;
    });
  }
  function dims(src) { return { w: src.naturalWidth || src.width || 0, h: src.naturalHeight || src.height || 0 }; }
  /** بزرگ‌ترین ضلع ≤ side، نسبت ثابت، هرگز بزرگ‌تر از اصل */
  function fit(w, h, side) {
    var r = Math.min(1, side / Math.max(w, h));
    return { w: Math.max(1, Math.round(w * r)), h: Math.max(1, Math.round(h * r)) };
  }
  /** کوچک‌کردنِ پله‌ای (نصف‌نصف) تا لبه‌ها دندانه‌دار نشوند؛ زمینهٔ سفید چون JPEG شفافیت ندارد */
  function draw(src, w, h) {
    var cur = src, d = dims(src);
    while (d.w / 2 >= w && d.h / 2 >= h) {
      var half = document.createElement('canvas');
      half.width = Math.round(d.w / 2); half.height = Math.round(d.h / 2);
      var hc = half.getContext('2d');
      hc.imageSmoothingQuality = 'high';
      hc.drawImage(cur, 0, 0, half.width, half.height);
      cur = half; d = { w: half.width, h: half.height };
    }
    var cv = document.createElement('canvas');
    cv.width = w; cv.height = h;
    var ctx = cv.getContext('2d');
    ctx.fillStyle = '#fff'; ctx.fillRect(0, 0, w, h);
    ctx.imageSmoothingQuality = 'high';
    ctx.drawImage(cur, 0, 0, w, h);
    return cv;
  }
  function toJpeg(cv, q) { return new Promise(function (r) { cv.toBlob(function (b) { r(b); }, 'image/jpeg', q); }); }
  async function encodeUnder(src, w, h, maxBytes) {
    var d = { w: w, h: h };
    for (var round = 0; round < 6; round++) {
      var cv = draw(src, d.w, d.h);
      var qs = [0.86, 0.8, 0.72, 0.64, 0.56];
      for (var i = 0; i < qs.length; i++) {
        var b = await toJpeg(cv, qs[i]);
        if (b && b.size <= maxBytes) return { blob: b, w: d.w, h: d.h, canvas: cv };
      }
      d = { w: Math.max(1, Math.round(d.w * 0.85)), h: Math.max(1, Math.round(d.h * 0.85)) };
    }
    throw err('عکس به اندازهٔ لازم کوچک نشد', 'prep_failed');
  }

  /**
   * فایلِ کاربر ⇒ { display, thumb, width, height, thumbWidth, thumbHeight, small, source }
   *   display  ≤۱۲۰۰ پیکسل و ≤۶۰۰KB — همان که «اصل» و «نمایش» می‌شود
   *   thumb    ≤۴۰۰ پیکسل و ≤۱۵۰KB، با همان نسبت (مرزِ ۲٪ِ سرور)
   * هیچ فرادادهٔ EXIF/GPS/XMP/توضیح نمی‌ماند: خروجی از بومِ تازه کدگذاری می‌شود.
   */
  async function prepare(blob) {
    if (!blob || !blob.size) throw err('فایل خالی است', 'image_empty');
    var src = await decode(blob);
    var d = dims(src);
    if (!d.w || !d.h) throw err('این فایل عکس نیست یا خراب است', 'image_corrupt');
    if (d.w * d.h > MAX_PIXELS) throw err('عکس بیش از اندازه بزرگ است (بیش از ۶۰ مگاپیکسل)', 'too_many_pixels');
    var fd = fit(d.w, d.h, SIZES.display.side);
    var display = await encodeUnder(src, fd.w, fd.h, SIZES.display.bytes);
    var ft = fit(display.w, display.h, SIZES.thumb.side);
    var thumb = await encodeUnder(display.canvas, ft.w, ft.h, SIZES.thumb.bytes);
    var ratio = display.w / display.h;
    var aspectOk = Math.abs(thumb.w / thumb.h - ratio) / ratio <= 0.02;
    if (src.close) { try { src.close(); } catch (e) { /* ImageBitmap */ } }
    return {
      display: display.blob, width: display.w, height: display.h,
      thumb: aspectOk ? thumb.blob : null, thumbWidth: thumb.w, thumbHeight: thumb.h,
      small: Math.max(d.w, d.h) < MIN_SIDE, source: { width: d.w, height: d.h },
    };
  }

  /* --------------------- عکسِ خودِ دکان روی بانک --------------------- */
  /**
   * بارگذاری + نسخه‌های کوچک + انتخاب به‌عنوانِ عکسِ این کالا. `st` پیشرفت
   * است و پس از هر قدم با `onStep` ذخیره می‌شود؛ ادامه از همان قدم.
   */
  async function uploadOwn(productId, prep, st, onStep) {
    st = st || {};
    var save = function () { if (onStep) onStep(st); };
    if (!st.imageId) {
      var up = await raw('POST', '?product_id=' + encodeURIComponent(productId) + '&role=main', prep.display);
      st.imageId = up.id; st.version = up.version || 1; st.duplicateOf = up.duplicate_of || [];
      st.flags = up.quality_flags || [];
      save();
    }
    var base = '/' + encodeURIComponent(st.imageId) + '/v/' + (st.version || 1) + '/';
    if (!st.display) { await raw('PUT', base + 'display', prep.display); st.display = true; save(); }
    if (prep.thumb && !st.thumb) { await raw('PUT', base + 'thumb', prep.thumb); st.thumb = true; save(); }
    if (!st.selected) {
      await api('/select', { method: 'POST', body: { product_id: productId, source: 'shop', image_id: st.imageId, version: st.version || 1 } });
      st.selected = true; save();
    }
    return st;
  }

  async function selectCentral(productId, it) {
    var body = { product_id: productId, source: 'central', image_id: it.image_id, version: it.version, expect: it.expect || {} };
    if (it.confirm) body.confirm = true;
    return api('/select', { method: 'POST', body: body });
  }

  /* ------------- صفِ کارهای پس از ذخیرهٔ کالا (آفلاین‌پذیر) ------------- */
  //  [{ k:'own'|'central', p, image_id?, version?, expect?, confirm?, st?, t }]
  function outbox() { return readJson(LS_OUT, []); }
  function setOutbox(list) { writeJson(LS_OUT, list); }
  /** کارِ تازهٔ یک کالا کارِ کهنهٔ همان کالا را کنار می‌گذارد */
  function enqueue(item) {
    if (!item || !item.p) return;
    var list = outbox().filter(function (x) { return x.p !== item.p; });
    item.t = Date.now();
    list.push(item);
    setOutbox(list);
    drain();
  }
  var draining = null;
  function drain() {
    if (draining) return draining;
    if (!state.enabled || !signedIn() || !hooks || navigator.onLine === false) return Promise.resolve();
    draining = (async function () {
      for (var guard = 0; guard < 500; guard++) {
        var list = outbox();
        if (!list.length) break;
        var it = list[0];
        try {
          if (it.k === 'central') await selectCentral(it.p, it);
          else {
            var blob = hooks.photoGet ? await hooks.photoGet(it.p) : null;
            if (blob) {
              var prep = await prepare(blob);
              await uploadOwn(it.p, prep, it.st, function (st) {
                var l2 = outbox(); l2.forEach(function (x) { if (x.p === it.p && x.t === it.t) x.st = st; }); setOutbox(l2);
              });
            }
          }
          setOutbox(outbox().filter(function (x) { return !(x.p === it.p && x.t === it.t); }));
          emit('tohid:image-bank-applied', { product_id: it.p, kind: it.k });
        } catch (e) {
          if (transient(e) || e.code === 'no_session') break;   // بعداً، همان قدم
          setOutbox(outbox().filter(function (x) { return !(x.p === it.p && x.t === it.t); }));
          var p = hooks.products ? (hooks.products() || []).find(function (x) { return x.id === it.p; }) : null;
          var what = p ? '«' + p.name + '»' : 'یک کالا';
          var msg = e.code === 'needs_confirmation' ? 'تطبیق قطعی نبود — دوباره از بانک انتخاب کنید'
            : e.code === 'barcode_mismatch' ? 'بارکدِ کالا با کالای عکس فرق دارد' : (e.message || 'خطا');
          toast('عکسِ بانکِ تصاویر برای ' + what + ' ثبت نشد: ' + msg, 'danger');
          emit('tohid:image-bank-error', { product_id: it.p, code: e.code || '', message: msg });
        }
      }
    })().finally(function () { draining = null; });
    return draining;
  }

  /* ------------------- تطبیقِ پیش از انتخاب (نمایش) ------------------- */
  /**
   * نتیجهٔ جست‌وجو در برابرِ ردیفِ کالای دکان: آیا تیکِ «همین کالاست» لازم است؟
   * همان قاعدهٔ ‎image-identity.classify‎ِ سرور؛ سرور باز خودش می‌سنجد.
   */
  function assess(item, row) {
    var p = item.product || {};
    var m = item.match || {};
    var rowG = gtin14(row && row.barcode), bankG = gtin14(p.gtin);
    if (rowG && bankG && rowG !== bankG) {
      return { block: true, needsConfirm: false, why: [], blockWhy: 'بارکدِ این کالا (' + (row.barcode || '') + ') با بارکدِ کالای بانک فرق دارد' };
    }
    var certain = !!m.certain && !item.ambiguous;
    var rowName = nameKey(row && row.name);
    var names = [p.name].concat(p.alt_names || []).map(nameKey);
    var nameDiffers = rowName && names.indexOf(rowName) < 0 && !(rowG && bankG && rowG === bankG);
    var needs = !certain || nameDiffers || (!rowG && !bankG);
    var why = [];
    if (!m.certain) why.push('تطبیق فقط با ' + (KIND[m.kind] || 'نام') + ' است، نه بارکد');
    if (item.ambiguous) why.push('چند نوعِ مشابه هست؛ فرق در: ' + (item.distinguish || []).map(function (d) { return DIFF[d] || d; }).join('، '));
    if (nameDiffers) why.push('نامِ کالای شما با نامِ کالای بانک یکی نیست');
    if (needs && !why.length) why.push('بارکدِ معتبری برای سنجش نیست؛ تطبیق را خودتان تأیید کنید');
    return { block: false, needsConfirm: needs, why: why, blockWhy: '' };
  }

  function matchPills(item) {
    var m = item.match || {};
    var out = [];
    if (m.certain) out.push('<span class="ib-pill ok"><i></i>تطبیقِ دقیق: ' + esc(KIND[m.kind] || '') + '</span>');
    else out.push('<span class="ib-pill warn"><i></i>محتمل: ' + esc(KIND[m.kind] || 'نامِ مشابه') + '</span>');
    if ((m.reasons || []).indexOf('brand_match') >= 0) out.push('<span class="ib-pill info"><i></i>برندِ مشابه</span>');
    if (item.ambiguous) {
      out.push('<span class="ib-pill danger"><i></i>چند نوع — فرق در ' +
        esc((item.distinguish || []).map(function (d) { return DIFF[d] || d; }).join('، ')) + '</span>');
    }
    return out.join('');
  }
  function specLine(p) {
    var bits = [];
    if (p.brand) bits.push(p.brand);
    if (p.variant) bits.push(p.variant);
    if (p.size_value) bits.push(p.size_value + ' ' + (p.size_unit || ''));
    if (p.pack_count > 1) bits.push(p.pack_count + ' عددی');
    if (p.packaging) bits.push(p.packaging);
    return bits.join(' · ');
  }

  /* ------------------------------ پنجره‌ها ------------------------------ */
  var urls = [];
  function objectUrl(blob) { var u = URL.createObjectURL(blob); urls.push(u); return u; }
  function revokeAll() { urls.forEach(function (u) { try { URL.revokeObjectURL(u); } catch (e) { /* بی صدا */ } }); urls = []; }

  /** عکس‌های کوچک: چهار تا هم‌زمان، با نشستِ کاربر */
  function hydrate(root) {
    var imgs = Array.prototype.slice.call(root.querySelectorAll('img[data-ib-src]'));
    var i = 0;
    function next() {
      var el = imgs[i++];
      if (!el) return Promise.resolve();
      var src = el.getAttribute('data-ib-src');
      el.removeAttribute('data-ib-src');
      return fileBlob(src).then(function (b) { el.src = objectUrl(b); el.closest('.ib-thumb').classList.add('has'); })
        .catch(function () { el.closest('.ib-thumb').classList.add('broken'); })
        .then(next);
    }
    for (var k = 0; k < 4; k++) next();
  }

  function scrim(id, title, inner, wide) {
    var old = document.getElementById(id);
    if (old) old.remove();
    var el = document.createElement('div');
    el.className = 'modal-scrim ib-scrim open';
    el.id = id;
    el.setAttribute('role', 'dialog');
    el.setAttribute('aria-modal', 'true');
    el.setAttribute('aria-label', title);
    el.innerHTML = '<div class="modal-box ib-box' + (wide ? ' ib-wide' : '') + '">' +
      '<div class="modal-head"><h3>' + esc(title) + '</h3>' +
      '<button type="button" class="modal-close" data-ib-close aria-label="بستن">' +
      '<svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round"><line x1="18" y1="6" x2="6" y2="18"/><line x1="6" y1="6" x2="18" y2="18"/></svg>' +
      '</button></div>' + inner + '</div>';
    document.body.appendChild(el);
    return el;
  }

  /**
   * «انتخاب از بانکِ تصاویر».
   *   opts: { name, barcode, hasPhoto }
   *   ⇒ null (انصراف) یا { image_id, version, blob, product, match, confirm, expect }
   * انتخاب روی سرور پس از ذخیرهٔ کالا با `enqueue({k:'central',…})` ثبت می‌شود.
   */
  function pick(opts) {
    opts = opts || {};
    return new Promise(function (resolve) {
      var row = { name: opts.name || '', barcode: opts.barcode || '' };
      var el = scrim('ib-picker', 'انتخاب از بانکِ تصاویر',
        '<form class="ib-search" data-ib-form>' +
        '<input class="form-input" type="search" data-ib-q dir="auto" autocomplete="off" placeholder="بارکد یا نامِ کالا" aria-label="بارکد یا نامِ کالا">' +
        '<button type="submit" class="btn btn-primary btn-sm">جست‌وجو</button></form>' +
        '<label class="ib-check"><input type="checkbox" data-ib-hasimg checked> فقط کالاهایی که عکسِ تأییدشده دارند</label>' +
        '<div class="ib-state" data-ib-state aria-live="polite"></div>' +
        '<div class="ib-results" data-ib-results></div>' +
        '<button type="button" class="btn btn-secondary btn-sm ib-more" data-ib-more hidden>نتیجه‌های بیشتر</button>' +
        '<div class="ib-detail" data-ib-detail hidden></div>');
      var q = el.querySelector('[data-ib-q]');
      var stateEl = el.querySelector('[data-ib-state]');
      var list = el.querySelector('[data-ib-results]');
      var more = el.querySelector('[data-ib-more]');
      var detail = el.querySelector('[data-ib-detail]');
      var items = [], cursor = null, seq = 0, done = false;
      q.value = digitsOf(row.barcode).length >= 8 ? row.barcode : row.name;

      function finish(v) {
        if (done) return;
        done = true;
        document.removeEventListener('keydown', onKey, true);
        el.remove();
        revokeAll();
        resolve(v);
      }
      function onKey(e) { if (e.key === 'Escape') { e.stopPropagation(); if (!detail.hidden) back(); else finish(null); } }
      document.addEventListener('keydown', onKey, true);
      el.addEventListener('click', function (e) {
        if (e.target === el || e.target.closest('[data-ib-close]')) finish(null);
      });

      function setState(kind, text, retry) {
        stateEl.className = 'ib-state ' + (kind || '');
        stateEl.innerHTML = text ? esc(text) + (retry ? ' <button type="button" class="btn btn-secondary btn-sm" data-ib-retry>دوباره</button>' : '') : '';
      }
      function card(it, i) {
        var p = it.product || {};
        var img = (it.images || [])[0];
        var thumb = img && (img.urls.thumb || img.urls.display);
        return '<button type="button" class="ib-card" data-ib-i="' + i + '">' +
          '<span class="ib-thumb">' + (thumb ? '<img alt="" data-ib-src="' + esc(thumb) + '">' : '') + '<span class="ib-noimg">بی عکس</span></span>' +
          '<span class="ib-body"><b>' + esc(p.name) + '</b>' +
          '<span class="ib-spec">' + esc(specLine(p)) + '</span>' +
          (p.gtin ? '<span class="ib-code" dir="ltr">' + esc(p.gtin) + '</span>' : '') +
          '<span class="ib-pills">' + matchPills(it) + '</span></span></button>';
      }
      function render(append, from) {
        if (!append) list.innerHTML = '';
        list.insertAdjacentHTML('beforeend', items.slice(from || 0).map(function (it, k) { return card(it, (from || 0) + k); }).join(''));
        more.hidden = !cursor;
        hydrate(list);
      }
      async function search(append) {
        var text = q.value.trim();
        if (!text) { setState('', 'بارکد را اسکن یا نامِ کالا را بنویسید'); list.innerHTML = ''; more.hidden = true; return; }
        var my = ++seq;
        setState('busy', 'در حالِ جست‌وجو…');
        var params = new URLSearchParams();
        params.set('q', text);
        if (digitsOf(text).length >= 8 && digitsOf(text).length === text.replace(/[\s-]/g, '').length) params.set('barcode', digitsOf(text));
        if (el.querySelector('[data-ib-hasimg]').checked) params.set('has_image', '1');
        if (append && cursor) params.set('cursor', cursor);
        try {
          var r = await api('/search?' + params.toString());
          if (my !== seq) return;
          var from = append ? items.length : 0;
          items = append ? items.concat(r.items || []) : (r.items || []);
          cursor = r.next_cursor || null;
          var note = r.barcode_valid === false ? 'این بارکد معتبر نیست (رقمِ کنترل نمی‌خواند) — نتیجه‌ها با نام است. ' : '';
          if (!items.length) setState('empty', note + 'چیزی پیدا نشد. می‌توانید عکسِ خودتان را بگذارید.');
          else setState('', note + (items.length === 1 ? 'یک نتیجه' : items.length + ' نتیجه') + (cursor ? ' (بیشتر هم هست)' : ''));
          render(append, from);
        } catch (e) {
          if (my !== seq) return;
          if (e.code === 'offline' || e.code === 'network') setState('error', 'اینترنت قطع است یا سرور در دسترس نیست — بانکِ تصاویر بی اینترنت کار نمی‌کند.', true);
          else if (e.status === 403) { setState('error', 'بانکِ تصاویر برای این دکان روشن نیست.'); setEnabled(false); }
          else setState('error', 'خطای سرور: ' + (e.message || ''), true);
        }
      }
      function back() {
        detail.hidden = true; detail.innerHTML = '';
        list.hidden = false; stateEl.hidden = false; more.hidden = !cursor;
        el.querySelector('[data-ib-form]').hidden = false;
        el.querySelector('.ib-check').hidden = false;
      }
      function open(i) {
        var it = items[i];
        if (!it) return;
        var p = it.product || {};
        var a = assess(it, row);
        var imgs = it.images || [];
        var chosen = 0;
        list.hidden = true; stateEl.hidden = true; more.hidden = true;
        el.querySelector('[data-ib-form]').hidden = true;
        el.querySelector('.ib-check').hidden = true;
        detail.hidden = false;
        detail.innerHTML =
          '<button type="button" class="btn btn-secondary btn-sm" data-ib-back>بازگشت به نتیجه‌ها</button>' +
          '<div class="ib-preview"><span class="ib-thumb ib-big">' +
          (imgs.length ? '<img alt="" data-ib-src="' + esc(imgs[0].urls.display || imgs[0].urls.thumb || '') + '">' : '') +
          '<span class="ib-noimg">این کالا در بانک عکسِ تأییدشده ندارد</span></span>' +
          '<div class="ib-facts"><b>' + esc(p.name) + '</b>' +
          '<dl>' +
          (p.brand ? '<dt>برند</dt><dd>' + esc(p.brand) + '</dd>' : '') +
          (p.variant ? '<dt>طعم/مدل</dt><dd>' + esc(p.variant) + '</dd>' : '') +
          (p.size_value ? '<dt>وزن/حجم</dt><dd>' + esc(p.size_value + ' ' + (p.size_unit || '')) + '</dd>' : '') +
          (p.pack_count > 1 ? '<dt>تعداد در بسته</dt><dd>' + esc(p.pack_count) + '</dd>' : '') +
          (p.packaging ? '<dt>بسته‌بندی</dt><dd>' + esc(p.packaging) + '</dd>' : '') +
          (p.gtin ? '<dt>بارکد</dt><dd dir="ltr">' + esc(p.gtin) + '</dd>' : '') +
          '</dl><div class="ib-pills">' + matchPills(it) + '</div>' +
          (a.why && a.why.length ? '<ul class="ib-why">' + a.why.map(function (w) { return '<li>' + esc(w) + '</li>'; }).join('') + '</ul>' : '') +
          '</div></div>' +
          (imgs.length > 1 ? '<div class="ib-strip">' + imgs.map(function (im, k) {
            return '<button type="button" class="ib-thumb ib-mini' + (k === 0 ? ' on' : '') + '" data-ib-img="' + k + '" aria-label="عکسِ ' + (k + 1) + '">' +
              (im.urls.thumb ? '<img alt="" data-ib-src="' + esc(im.urls.thumb) + '">' : '') + '<span class="ib-noimg">' + (k + 1) + '</span></button>';
          }).join('') + '</div>' : '') +
          '<p class="ib-note">قیمت، موجودی، قفسه و SKUِ شما از بانک کپی نمی‌شود؛ فقط عکس (و نام/بارکد اگر خالی باشد).</p>' +
          (opts.hasPhoto ? '<p class="ib-note warn">این کالا الان عکس دارد؛ با ذخیره، این عکس جایش را می‌گیرد.</p>' : '') +
          (a.block ? '<p class="ib-note danger">' + esc(a.blockWhy) + ' — این عکس برای این کالا انتخاب نمی‌شود.</p>' : '') +
          (!a.block && a.needsConfirm ? '<label class="ib-check ib-confirm"><input type="checkbox" data-ib-confirm> مشخصات را دیدم؛ این عکس واقعاً همین کالای من است</label>' : '') +
          '<div class="ib-actions"><button type="button" class="btn btn-primary" data-ib-choose' +
          (a.block || !imgs.length || a.needsConfirm ? ' disabled' : '') + '>انتخاب این عکس</button></div>' +
          '<div class="ib-state" data-ib-dstate aria-live="polite"></div>';
        hydrate(detail);
        detail.onclick = async function (e) {
          if (e.target.closest('[data-ib-back]')) { back(); return; }
          var mini = e.target.closest('[data-ib-img]');
          if (mini) {
            chosen = Number(mini.getAttribute('data-ib-img'));
            detail.querySelectorAll('[data-ib-img]').forEach(function (b) { b.classList.toggle('on', b === mini); });
            var big = detail.querySelector('.ib-big');
            big.classList.remove('has', 'broken');
            var bi = big.querySelector('img') || big.insertBefore(document.createElement('img'), big.firstChild);
            bi.removeAttribute('src');
            bi.setAttribute('data-ib-src', imgs[chosen].urls.display || imgs[chosen].urls.thumb || '');
            hydrate(big.parentNode);
            return;
          }
          if (!e.target.closest('[data-ib-choose]')) return;
          var btn = e.target.closest('[data-ib-choose]');
          var ds = detail.querySelector('[data-ib-dstate]');
          btn.disabled = true;
          ds.className = 'ib-state busy'; ds.textContent = 'در حالِ گرفتنِ عکس…';
          try {
            var im = imgs[chosen];
            var b = await fileBlob(im.urls.display || im.urls.thumb);
            var confirm = !!(detail.querySelector('[data-ib-confirm]') || {}).checked;
            finish({
              image_id: im.id, version: im.version, blob: b, product: p, match: it.match, ambiguous: !!it.ambiguous,
              confirm: confirm, fill: { name: p.name || '', barcode: p.gtin || '' },
            });
          } catch (x) {
            btn.disabled = false;
            ds.className = 'ib-state error';
            ds.textContent = (x.code === 'offline' || x.code === 'network') ? 'اینترنت قطع است — دوباره امتحان کنید' : ('عکس گرفته نشد: ' + (x.message || ''));
          }
        };
        detail.onchange = function (e) {
          if (e.target.matches('[data-ib-confirm]')) detail.querySelector('[data-ib-choose]').disabled = !e.target.checked || !imgs.length;
        };
      }

      el.querySelector('[data-ib-form]').addEventListener('submit', function (e) { e.preventDefault(); search(false); });
      el.querySelector('[data-ib-hasimg]').addEventListener('change', function () { search(false); });
      more.addEventListener('click', function () { search(true); });
      list.addEventListener('click', function (e) {
        var c = e.target.closest('[data-ib-i]');
        if (c) open(Number(c.getAttribute('data-ib-i')));
      });
      stateEl.addEventListener('click', function (e) { if (e.target.closest('[data-ib-retry]')) search(false); });
      setTimeout(function () { try { q.focus(); } catch (e) { /* بی صدا */ } }, 30);
      if (q.value.trim()) search(false); else setState('', 'بارکد را اسکن یا نامِ کالا را بنویسید');
    });
  }

  /* ======================= ورودِ گروهیِ عکس‌ها ======================= */
  var IMG_EXT = /\.(jpe?g|png|webp|gif|bmp|heic|heif)$/i;

  function stemOf(fileName) {
    var base = String(fileName || '').split(/[\\/]/).pop();
    var stem = base.replace(/\.[^.]+$/, '');
    //  «۶۲۶۱۲۳۴۵۶۷۸۹۰_2»، «شیر-1»، «چای (2)» ⇒ همان کالا
    var cut = stem.replace(/(?:\s*\(\d{1,2}\)|[_-]\d{1,2})$/, '');
    return cut.trim() ? cut.trim() : stem.trim();
  }

  /** CSV با سرستون: barcode | name | image_filename (یا image_path، file، filename، image) */
  function parseCsv(text) {
    var lines = String(text || '').replace(/^﻿/, '').split(/\r?\n/).filter(function (l) { return l.trim(); });
    if (!lines.length) return {};
    var sep = [',', ';', '\t'].sort(function (a, b) { return lines[0].split(b).length - lines[0].split(a).length; })[0];
    var split = function (l) {
      var out = [], cur = '', inQ = false;
      for (var i = 0; i < l.length; i++) {
        var ch = l[i];
        if (ch === '"') { if (inQ && l[i + 1] === '"') { cur += '"'; i++; } else inQ = !inQ; }
        else if (ch === sep && !inQ) { out.push(cur.trim()); cur = ''; }
        else cur += ch;
      }
      out.push(cur.trim());
      return out;
    };
    var head = split(lines[0]).map(function (h) { return h.toLowerCase(); });
    var col = function (names) { for (var i = 0; i < head.length; i++) if (names.indexOf(head[i]) >= 0) return i; return -1; };
    var fi = col(['image_filename', 'image_path', 'filename', 'file', 'image']);
    var bi = col(['barcode', 'gtin', 'بارکد']);
    var ni = col(['name', 'product', 'نام']);
    if (fi < 0 || (bi < 0 && ni < 0)) return {};
    var map = {};
    lines.slice(1).forEach(function (l) {
      var c = split(l);
      var f = String(c[fi] || '').split(/[\\/]/).pop().toLowerCase();
      if (f) map[f] = { barcode: bi >= 0 ? c[bi] || '' : '', name: ni >= 0 ? c[ni] || '' : '' };
    });
    return map;
  }

  /**
   * فایل ⇒ کالا. ترتیب: ردیفِ CSV ← بارکد در نامِ فایل ← نامِ کاملِ کالا.
   * یک کالا ⇒ matched؛ چند کالا ⇒ ambiguous (هیچ‌کدام خودکار نه)؛ هیچ ⇒ unmatched.
   * همان ‎core/image/BulkMatch.kt‎ِ برنامه.
   */
  function matchFile(fileName, products, csv) {
    var list = products || [];
    function byCode(code) {
      var d = digitsOf(code), g = gtin14(code);
      if (!d) return [];
      return list.filter(function (p) {
        return (p.barcodes || []).some(function (b) { var bd = digitsOf(b); return bd === d || (g && gtin14(b) === g); });
      });
    }
    function byName(n) {
      var k = nameKey(String(n || '').replace(/_+/g, ' '));
      return k ? list.filter(function (p) { return nameKey(p.name) === k; }) : [];
    }
    function out(hits, how, key) {
      if (hits.length === 1) return { status: 'matched', productId: hits[0].id, candidates: [hits[0].id], how: how, key: key };
      if (hits.length > 1) return { status: 'ambiguous', productId: '', candidates: hits.map(function (p) { return p.id; }), how: how, key: key };
      return { status: 'unmatched', productId: '', candidates: [], how: how, key: key };
    }
    var base = String(fileName || '').split(/[\\/]/).pop().toLowerCase();
    var row = csv && csv[base];
    if (row) {
      if (digitsOf(row.barcode)) { var r1 = out(byCode(row.barcode), 'csv_barcode', row.barcode); if (r1.status !== 'unmatched' || !row.name) return r1; }
      return out(byName(row.name), 'csv_name', row.name);
    }
    var stem = stemOf(fileName);
    var compact = T() ? T().asciiDigits(stem).replace(/[\s-]/g, '') : stem.replace(/[\s-]/g, '');
    if (/^\d{8,14}$/.test(compact)) return out(byCode(compact), 'barcode', compact);
    return out(byName(stem), 'name', stem);
  }

  /** شناسهٔ محتوا (SHA-256) — تکراری در یک دسته و ادامهٔ بی تکرار */
  async function checksum(blob) {
    var buf = await blob.arrayBuffer();
    if (window.crypto && crypto.subtle) {
      var h = await crypto.subtle.digest('SHA-256', buf);
      return Array.prototype.map.call(new Uint8Array(h), function (b) { return ('0' + b.toString(16)).slice(-2); }).join('');
    }
    var a = new Uint8Array(buf), x = 2166136261 >>> 0;
    for (var i = 0; i < a.length; i++) { x ^= a[i]; x = Math.imul(x, 16777619) >>> 0; }
    return 'fnv' + x.toString(16) + '-' + a.length;
  }

  /* ---- IndexedDB: کار و فایل‌ها تا پایان می‌مانند (ادامه پس از بستنِ برنامه) ---- */
  var dbp = null;
  function db() {
    if (dbp) return dbp;
    dbp = new Promise(function (resolve, reject) {
      var r;
      try { r = indexedDB.open(DB_NAME, 1); } catch (e) { reject(e); return; }
      r.onupgradeneeded = function () {
        var d = r.result;
        if (!d.objectStoreNames.contains('files')) d.createObjectStore('files');
        if (!d.objectStoreNames.contains('jobs')) d.createObjectStore('jobs');
      };
      r.onsuccess = function () { resolve(r.result); };
      r.onerror = function () { reject(r.error); };
    }).catch(function (e) { dbp = null; throw e; });
    return dbp;
  }
  function idb(store, mode, fn) {
    return db().then(function (d) {
      return new Promise(function (resolve, reject) {
        var tx = d.transaction(store, mode);
        var req = fn(tx.objectStore(store));
        tx.oncomplete = function () { resolve(req && req.result); };
        tx.onerror = function () { reject(tx.error); };
        tx.onabort = function () { reject(tx.error); };
      });
    });
  }
  function jobGet() { return idb('jobs', 'readonly', function (s) { return s.get('current'); }).catch(function () { return null; }); }
  function jobPut(job) { return idb('jobs', 'readwrite', function (s) { return s.put(job, 'current'); }); }
  function filePut(sum, blob) { return idb('files', 'readwrite', function (s) { return s.put(blob, sum); }); }
  function fileGet(sum) { return idb('files', 'readonly', function (s) { return s.get(sum); }); }
  function jobClear() {
    return Promise.all([
      idb('jobs', 'readwrite', function (s) { return s.clear(); }),
      idb('files', 'readwrite', function (s) { return s.clear(); }),
    ]).catch(function () { /* بی صدا */ });
  }

  var LABEL = {
    ready: ['info', 'آمادهٔ ورود'], working: ['info', 'در حالِ کار…'], done: ['ok', 'انجام شد'],
    failed: ['danger', 'ناموفق'], duplicate: ['muted', 'تکراری'], same_product: ['muted', 'تکراری برای همین کالا'],
    ambiguous: ['warn', 'مبهم — انتخاب کنید'], unmatched: ['warn', 'بی‌تطبیق'], has_photo: ['muted', 'کالا عکس دارد'],
    pending_bank: ['info', 'روی دستگاه گذاشته شد؛ منتظرِ بانک'], not_image: ['danger', 'عکس نیست'],
  };
  var HOW = {
    barcode: 'بارکد در نامِ فایل', name: 'نامِ کاملِ کالا', csv_barcode: 'بارکدِ ردیفِ CSV', csv_name: 'نامِ ردیفِ CSV', manual: 'انتخابِ شما',
  };

  function productsList() { return hooks && hooks.products ? hooks.products() || [] : []; }
  function productById(id) { return productsList().find(function (p) { return p.id === id; }) || null; }

  /** وضعیتِ ردیف‌هایی که هنوز شروع نشده‌اند — تکراری/کالای یکسان/عکس‌دار */
  function plan(job) {
    var seenSum = {}, seenProduct = {};
    job.rows.forEach(function (r) {
      if (['done', 'failed', 'pending_bank', 'working', 'not_image'].indexOf(r.status) >= 0) {
        if (r.productId) seenProduct[r.productId] = true;
        seenSum[r.sum] = true;
        return;
      }
      if (seenSum[r.sum]) { r.status = 'duplicate'; return; }
      seenSum[r.sum] = true;
      if (!r.productId) { r.status = r.candidates && r.candidates.length > 1 ? 'ambiguous' : 'unmatched'; return; }
      if (seenProduct[r.productId]) { r.status = 'same_product'; return; }
      seenProduct[r.productId] = true;
      var p = productById(r.productId);
      if (!p) { r.status = 'unmatched'; r.productId = ''; return; }
      r.status = p.photo && !job.replace ? 'has_photo' : 'ready';
    });
    return job;
  }

  function counts(job) {
    var c = { done: 0, failed: 0, duplicate: 0, ambiguous: 0, unmatched: 0, has_photo: 0, waiting: 0 };
    job.rows.forEach(function (r) {
      if (r.status === 'done') c.done++;
      else if (r.status === 'failed' || r.status === 'not_image') c.failed++;
      else if (r.status === 'duplicate' || r.status === 'same_product') c.duplicate++;
      else if (r.status === 'ambiguous') c.ambiguous++;
      else if (r.status === 'unmatched') c.unmatched++;
      else if (r.status === 'has_photo') c.has_photo++;
      else c.waiting++;
    });
    return c;
  }

  var bulk = { job: null, running: false, stop: false, el: null };

  async function addFiles(fileList) {
    var files = Array.prototype.slice.call(fileList || []);
    var job = bulk.job || { id: 'job-' + Date.now(), created: Date.now(), replace: false, rows: [] };
    var csv = {};
    for (var i = 0; i < files.length; i++) {
      if (/\.csv$/i.test(files[i].name) || files[i].type === 'text/csv') Object.assign(csv, parseCsv(await files[i].text()));
    }
    for (var k = 0; k < files.length; k++) {
      var f = files[k];
      if (/\.csv$/i.test(f.name) || f.type === 'text/csv') continue;
      var sum = await checksum(f);
      var m = matchFile(f.name, productsList(), csv);
      var row = {
        key: job.rows.length + 1, file: f.name, size: f.size, sum: sum,
        status: '', productId: m.productId, candidates: m.candidates, how: m.how, note: '', st: null,
      };
      if (!IMG_EXT.test(f.name) && !/^image\//.test(f.type || '')) { row.status = 'not_image'; row.note = 'پسوند یا نوعِ فایل عکس نیست'; }
      else await filePut(sum, f);
      job.rows.push(row);
    }
    bulk.job = plan(job);
    await jobPut(bulk.job);
    renderBulk();
  }

  async function processRow(r) {
    var blob = await fileGet(r.sum);
    if (!blob) { r.status = 'failed'; r.note = 'فایل روی این دستگاه نماند — دوباره انتخابش کنید'; return; }
    var prep;
    try { prep = await prepare(blob); }
    catch (e) { r.status = 'not_image'; r.note = e.message || 'این فایل عکس نیست یا خراب است'; return; }
    if (!r.local) {
      await hooks.setPhoto(r.productId, prep.display);
      r.local = true;
      if (prep.small) r.note = 'عکس کوچک است (زیرِ ۳۰۰ پیکسل)';
      if (!state.enabled) { r.status = 'done'; return; }
      //  پیش از بانک ثبت می‌شود: بستنِ برنامه از این‌جا ⇒ ادامه فقط بانک را می‌فرستد
      //  (کالا حالا عکس دارد و نباید «عکس‌دار» شمرده و رها شود)
      r.status = 'pending_bank';
      await jobPut(bulk.job);
    }
    if (!state.enabled) { r.status = 'done'; return; }
    try {
      r.st = await uploadOwn(r.productId, prep, r.st, function (st) { r.st = st; jobPut(bulk.job); });
      r.status = 'done';
      if (r.st.duplicateOf && r.st.duplicateOf.length) r.note = (r.note ? r.note + '؛ ' : '') + 'همین عکس پیش‌تر هم در بانکِ دکان بود';
    } catch (e) {
      if (transient(e)) { r.status = 'pending_bank'; r.note = 'روی دستگاه گذاشته شد؛ بانک منتظرِ اینترنت'; throw e; }
      r.status = 'done';
      r.note = 'روی دستگاه گذاشته شد؛ بانکِ تصاویر نپذیرفت: ' + (e.message || '');
    }
  }

  async function runBulk() {
    if (bulk.running || !bulk.job) return;
    bulk.running = true; bulk.stop = false;
    renderBulk();
    try {
      for (var i = 0; i < bulk.job.rows.length; i++) {
        if (bulk.stop) break;
        var r = bulk.job.rows[i];
        if (r.status !== 'ready' && r.status !== 'pending_bank' && r.status !== 'working') continue;
        if (r.status === 'ready') r.status = 'working';   //  فقط نمایش؛ بازکردن ⇒ دوباره «آماده»
        renderBulkRow(r);
        try {
          await processRow(r);
        } catch (e) {
          if (transient(e)) { await jobPut(bulk.job); renderBulk(); toast('اینترنت قطع شد — ورود متوقف شد؛ بعداً «ادامه» را بزنید', 'warn'); break; }
          r.status = 'failed'; r.note = e.message || 'خطا';
        }
        await jobPut(bulk.job);
        renderBulkRow(r);
        renderCounts();
      }
    } finally {
      bulk.running = false;
      await jobPut(bulk.job).catch(function () { /* بی صدا */ });
      renderBulk();
    }
  }

  function rowHtml(r) {
    var lab = LABEL[r.status] || ['muted', r.status];
    var p = r.productId ? productById(r.productId) : null;
    var action = '';
    if (r.status === 'ambiguous') {
      action = '<select class="form-input" data-ib-choose-row="' + r.key + '" aria-label="کدام کالا؟"><option value="">کدام کالا؟</option>' +
        (r.candidates || []).map(function (id) { var c = productById(id); return c ? '<option value="' + esc(id) + '">' + esc(c.name) + ((c.barcodes || [])[0] ? ' — ' + esc(c.barcodes[0]) : '') + '</option>' : ''; }).join('') + '</select>';
    } else if (r.status === 'unmatched') {
      action = '<span class="ib-inline"><input class="form-input" data-ib-code-row="' + r.key + '" placeholder="بارکد یا نامِ دقیق" aria-label="بارکد یا نامِ دقیقِ کالا">' +
        '<button type="button" class="btn btn-secondary btn-sm" data-ib-link-row="' + r.key + '">تطبیق</button></span>';
    } else if (r.status === 'failed') {
      action = '<button type="button" class="btn btn-secondary btn-sm" data-ib-retry-row="' + r.key + '">دوباره</button>';
    }
    return '<div class="ib-brow" data-ib-row="' + r.key + '">' +
      '<span class="ib-bfile" dir="auto" title="' + esc(r.file) + '">' + esc(r.file) + '</span>' +
      '<span class="ib-bprod">' + (p ? esc(p.name) + '<small>' + esc(HOW[r.how] || '') + '</small>' : '<small>—</small>') + '</span>' +
      '<span class="ib-pill ' + lab[0] + '"><i></i>' + esc(lab[1]) + '</span>' +
      '<span class="ib-bnote">' + esc(r.note || '') + '</span>' +
      '<span class="ib-bact">' + action + '</span></div>';
  }
  function renderBulkRow(r) {
    if (!bulk.el) return;
    var old = bulk.el.querySelector('[data-ib-row="' + r.key + '"]');
    if (old) old.outerHTML = rowHtml(r);
  }
  function renderCounts() {
    if (!bulk.el || !bulk.job) return;
    var c = counts(bulk.job);
    bulk.el.querySelector('[data-ib-counts]').innerHTML = [
      ['ok', 'موفق', c.done], ['danger', 'ناموفق', c.failed], ['muted', 'تکراری', c.duplicate],
      ['warn', 'مبهم', c.ambiguous], ['warn', 'بی‌تطبیق', c.unmatched], ['muted', 'عکس دارد', c.has_photo], ['info', 'مانده', c.waiting],
    ].map(function (x) { return '<span class="ib-kpi ' + x[0] + '"><b>' + x[2] + '</b>' + x[1] + '</span>'; }).join('');
  }
  function renderBulk() {
    if (!bulk.el) return;
    var job = bulk.job;
    var rows = bulk.el.querySelector('[data-ib-rows]');
    rows.innerHTML = job && job.rows.length ? job.rows.map(rowHtml).join('') : '<p class="ib-state">هنوز فایلی انتخاب نشده.</p>';
    var c = job ? counts(job) : { waiting: 0 };
    var startable = job && job.rows.some(function (r) { return r.status === 'ready' || r.status === 'pending_bank'; });
    var start = bulk.el.querySelector('[data-ib-start]');
    start.hidden = bulk.running || !startable;
    start.textContent = job && job.rows.some(function (r) { return r.status === 'done' || r.status === 'pending_bank'; }) ? 'ادامهٔ ورود' : 'شروعِ ورود';
    bulk.el.querySelector('[data-ib-stop]').hidden = !bulk.running;
    bulk.el.querySelector('[data-ib-clear]').hidden = bulk.running || !job || !job.rows.length;
    bulk.el.querySelector('[data-ib-replace]').checked = !!(job && job.replace);
    bulk.el.querySelector('[data-ib-pickfiles]').disabled = bulk.running;
    var resume = bulk.el.querySelector('[data-ib-resume]');
    resume.hidden = !(job && job.resumed && startable && !bulk.running);
    resume.textContent = job && job.resumed ? 'ورودِ قبلی نیمه‌کاره ماند — ' + c.waiting + ' ردیف مانده. «ادامهٔ ورود» از همان‌جا ادامه می‌دهد.' : '';
    renderCounts();
  }

  async function openBulk() {
    if (!hooks) return;
    bulk.el = scrim('ib-bulk', 'ورودِ گروهیِ عکسِ کالاها',
      '<p class="ib-note">نامِ هر فایل را <b>بارکد</b> یا <b>نامِ دقیقِ کالا</b> بگذارید (مثلاً <span dir="ltr">6261234567890.jpg</span>)، یا یک CSV با ستون‌های <span dir="ltr">barcode</span>/<span dir="ltr">name</span> و <span dir="ltr">image_filename</span> کنارشان انتخاب کنید. ' +
      'فایلِ مبهم یا بی‌تطبیق حدس زده نمی‌شود؛ خودتان انتخاب می‌کنید.</p>' +
      '<p class="ib-note">' + (state.enabled
        ? 'هر عکس روی دستگاه کوچک می‌شود (نمایش ۱۲۰۰ و کوچک ۴۰۰ پیکسل، بی EXIF/مکان)، عکسِ کالا می‌شود و در بانکِ تصاویرِ همین دکان هم ثبت می‌شود.'
        : 'هر عکس روی دستگاه کوچک می‌شود (بی EXIF/مکان) و عکسِ کالا می‌شود؛ مثلِ همیشه با حساب همگام می‌شود. بانکِ تصاویر برای این دکان روشن نیست.') + '</p>' +
      '<p class="ib-note warn" data-ib-resume hidden></p>' +
      '<div class="ib-drop" data-ib-drop><input type="file" multiple accept="image/*,.csv,text/csv" data-ib-files hidden>' +
      '<button type="button" class="btn btn-secondary" data-ib-pickfiles>انتخابِ عکس‌ها (و CSV)</button>' +
      '<span>یا فایل‌ها را این‌جا رها کنید</span></div>' +
      '<label class="ib-check"><input type="checkbox" data-ib-replace> عکسِ کالاهایی که از قبل عکس دارند هم جایگزین شود</label>' +
      '<div class="ib-kpis" data-ib-counts></div>' +
      '<div class="ib-brows" data-ib-rows></div>' +
      '<div class="ib-actions">' +
      '<button type="button" class="btn btn-primary" data-ib-start hidden>شروعِ ورود</button>' +
      '<button type="button" class="btn btn-secondary" data-ib-stop hidden>توقف</button>' +
      '<button type="button" class="btn btn-secondary" data-ib-clear hidden>پاک کردنِ فهرست</button></div>', true);
    var el = bulk.el;
    var job = await jobGet();
    if (job && job.rows && job.rows.length) {
      job.rows.forEach(function (r) { if (r.status === 'working') r.status = 'ready'; });
      job.resumed = job.rows.some(function (r) { return r.status === 'done' || r.status === 'pending_bank' || r.status === 'failed'; });
      bulk.job = plan(job);
    } else bulk.job = null;
    renderBulk();

    function close() {
      bulk.stop = true;
      document.removeEventListener('keydown', onKey, true);
      el.remove();
      if (bulk.el === el) bulk.el = null;
    }
    function onKey(e) { if (e.key === 'Escape') { e.stopPropagation(); close(); } }
    document.addEventListener('keydown', onKey, true);
    var input = el.querySelector('[data-ib-files]');
    input.addEventListener('change', function () { var fl = input.files; addFiles(fl).then(function () { input.value = ''; }); });
    var drop = el.querySelector('[data-ib-drop]');
    drop.addEventListener('dragover', function (e) { e.preventDefault(); drop.classList.add('over'); });
    drop.addEventListener('dragleave', function () { drop.classList.remove('over'); });
    drop.addEventListener('drop', function (e) { e.preventDefault(); drop.classList.remove('over'); if (!bulk.running) addFiles(e.dataTransfer.files); });
    el.addEventListener('change', async function (e) {
      if (e.target.matches('[data-ib-replace]')) {
        if (!bulk.job) bulk.job = { id: 'job-' + Date.now(), created: Date.now(), rows: [] };
        bulk.job.replace = e.target.checked;
        bulk.job.rows.forEach(function (r) { if (r.status === 'has_photo' || r.status === 'ready') r.status = ''; });
        plan(bulk.job); await jobPut(bulk.job); renderBulk();
        return;
      }
      var sel = e.target.closest('[data-ib-choose-row]');
      if (sel && sel.value) {
        var r = bulk.job.rows.find(function (x) { return String(x.key) === sel.getAttribute('data-ib-choose-row'); });
        r.productId = sel.value; r.how = 'manual'; r.status = '';
        plan(bulk.job); await jobPut(bulk.job); renderBulk();
      }
    });
    el.addEventListener('click', async function (e) {
      if (e.target === el || e.target.closest('[data-ib-close]')) { close(); return; }
      if (e.target.closest('[data-ib-pickfiles]')) { input.click(); return; }
      if (e.target.closest('[data-ib-start]')) { if (bulk.job) bulk.job.resumed = false; runBulk(); return; }
      if (e.target.closest('[data-ib-stop]')) { bulk.stop = true; return; }
      if (e.target.closest('[data-ib-clear]')) {
        if (bulk.job && bulk.job.rows.some(function (r) { return r.status === 'ready' || r.status === 'pending_bank'; }) &&
          !window.confirm('ردیف‌های انجام‌نشده هم از فهرست پاک می‌شوند. پاک شود؟')) return;
        await jobClear(); bulk.job = null; renderBulk();
        return;
      }
      var link = e.target.closest('[data-ib-link-row]');
      if (link) {
        var key = link.getAttribute('data-ib-link-row');
        var r = bulk.job.rows.find(function (x) { return String(x.key) === key; });
        var v = (el.querySelector('[data-ib-code-row="' + key + '"]') || {}).value || '';
        if (!v.trim()) return;
        var m = matchFile(v.trim() + '.jpg', productsList(), null);
        if (m.status === 'unmatched') { toast('کالایی با این بارکد یا نام پیدا نشد', 'danger'); return; }
        r.productId = m.productId; r.candidates = m.candidates; r.how = 'manual'; r.status = '';
        plan(bulk.job); await jobPut(bulk.job); renderBulk();
        return;
      }
      var retry = e.target.closest('[data-ib-retry-row]');
      if (retry) {
        var rr = bulk.job.rows.find(function (x) { return String(x.key) === retry.getAttribute('data-ib-retry-row'); });
        rr.status = rr.local ? 'pending_bank' : 'ready'; rr.note = '';
        await jobPut(bulk.job); renderBulk();
      }
    });
  }

  /* ------------------------------ راه‌اندازی ------------------------------ */
  function bind(h) {
    hooks = h;
    refreshConfig();
  }
  window.addEventListener('online', function () { if (hooks) refreshConfig(); });
  try {
    var a0 = acc();
    if (a0 && a0.on) {
      a0.on('signed-in', function () { if (hooks) refreshConfig(); });
      a0.on('signed-out', function () { setEnabled(false); });
    }
  } catch (e) { /* بی صدا */ }

  window.TohidImageBank = {
    bind: bind,
    enabled: function () { return state.enabled === true; },
    refreshConfig: refreshConfig,
    pick: pick,
    enqueue: enqueue,
    drain: drain,
    pending: function () { return outbox().length; },
    openBulk: openBulk,
    prepare: prepare,
    uploadOwn: uploadOwn,
    fileBlob: fileBlob,
    //  برای آزمون: منطقِ خالص
    matchFile: matchFile,
    parseCsv: parseCsv,
    stemOf: stemOf,
    gtin14: gtin14,
    assess: assess,
    SIZES: SIZES,
  };
})();
