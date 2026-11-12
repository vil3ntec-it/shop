/*
 * ویترین — «عکسِ دروازه» و «یادداشتِ صوتی» ِ اختیاریِ نشانیِ نام‌دار (CRM-09/CHK-08، §۴.۴). دیربار: acct.js فقط با
 * `features.addressMedia` (پرچمِ خاموشِ `shop.storefront.address_media`)، نشستِ حساب و نشانیِ برگزیدهٔ برگهٔ ثبت می‌خواهد.
 *
 * عکس: انتخاب یا دوربینِ گوشی (`capture`)، کوچک‌شده روی همین دستگاه به JPEG (لبهٔ بلند ۱۲۸۰) ⇒ EXIF و مکانِ دوربین نمی‌روند.
 * صدا: ضبط با MediaRecorder تا ۶۰ ثانیه (قالب‌های chat-voice.js)، پیش‌شنوایی (blob: ِ همین صفحه)، سپس «فرستادن» یا «لغو»؛
 * بیرون رفتن از ضبط همیشه میکروفون را می‌بندد. پیش‌نمایشِ عکسِ ذخیره data: است (CSP ِ ویترین img-src blob: ندارد).
 * مرز سرور است (`lib/store-address-media.js`): نوع از بایت، سقفِ حجم، فقط نشانیِ همان حساب.
 */
(function () {
  'use strict';
  var el = K.el; var t = K.t;
  var EDGE = 1280; var Q = 0.75; var VOICE_MS = 60 * 1000;
  var TYPES = ['audio/mp4;codecs=mp4a.40.2', 'audio/webm;codecs=opus', 'audio/ogg;codecs=opus', 'audio/mp4', 'audio/webm'];
  var btn = function (text) { return el('button', { class: 'btn btn-ghost btn-sm', attrs: { type: 'button' }, text: text }); };

  function call(base, id, kind, method, blob) {
    var h = { Accept: 'application/json', 'Accept-Language': K.lang };
    if (K.sess()) h['X-Store-Session'] = K.sess().token;
    if (blob) h['Content-Type'] = 'application/octet-stream';
    return fetch(base + '/account/addresses/' + encodeURIComponent(id) + '/media/' + kind,
      { method: method, headers: h, body: blob || undefined, credentials: 'omit', cache: 'no-store' })
      .then(function (r) {
        if (r.ok) return method === 'GET' ? r.blob() : r.json();
        return r.json().catch(function () { return {}; }).then(function (b) {
          var c = (b && b.error && b.error.code) || '';
          throw new Error(K.has('am.err.' + c) ? t('am.err.' + c) : K.has('err.' + c) ? t('err.' + c) : t('err.generic'));
        });
      }, function () { throw new Error(t('err.network')); });
  }

  function dataUrl(blob) {
    return new Promise(function (ok) { var r = new FileReader(); r.onload = function () { ok(String(r.result)); }; r.onerror = function () { ok(''); }; r.readAsDataURL(blob); });
  }
  /** عکس ⇒ JPEG ِ کوچک، بی EXIF (خواندن از data: — CSP ِ ویترین img-src blob: ندارد) */
  function shrink(file) {
    return dataUrl(file).then(function (src) {
      return new Promise(function (ok, no) {
        var img = new Image();
        var bad = function () { no(new Error(t('am.err.bad_media_type'))); };
        img.onload = function () {
          var s = Math.min(1, EDGE / Math.max(img.naturalWidth || 1, img.naturalHeight || 1));
          var c = document.createElement('canvas');
          c.width = Math.max(1, Math.round(img.naturalWidth * s)); c.height = Math.max(1, Math.round(img.naturalHeight * s));
          c.getContext('2d').drawImage(img, 0, 0, c.width, c.height);
          c.toBlob(function (b) { if (b) ok(b); else bad(); }, 'image/jpeg', Q);
        };
        img.onerror = bad;
        if (src) img.src = src; else bad();
      });
    });
  }

  /** o: { base, into, address } ⇒ `{ close() }` */
  window.StoreAddrMedia = function (o) {
    var a = o.address; var has = { 'gate-photo': Boolean(a.media && a.media.gatePhoto), 'voice-note': Boolean(a.media && a.media.voiceNote) };
    var msg = el('p', { class: 'muted', attrs: { 'aria-live': 'polite', id: 'am-msg' } });
    var say = function (s) { msg.textContent = s; };
    var box = el('div', { class: 'stack', attrs: { id: 'am-box' } }, el('p', { class: 'muted', text: t('am.note') }));

    // ── عکسِ دروازه ──
    var pic = el('input', { attrs: { id: 'am-file', type: 'file', accept: 'image/*', capture: 'environment', 'aria-label': t('am.photo') }, hidden: true });
    var thumb = el('img', { class: 'am-thumb', attrs: { id: 'am-thumb', alt: t('am.photo') }, hidden: true });
    var pAdd = btn(''); var pDel = btn(t('am.del'));
    var pState = el('span', { attrs: { id: 'am-photo-state' } });
    function photoUi() {
      pAdd.textContent = has['gate-photo'] ? t('am.change') : t('am.add');
      pDel.hidden = !has['gate-photo'];
      pState.textContent = has['gate-photo'] ? t('am.saved') : t('am.none');
      if (!has['gate-photo']) { thumb.hidden = true; thumb.removeAttribute('src'); return; }
      call(o.base, a.id, 'gate-photo', 'GET').then(dataUrl).then(function (u) { if (u) { thumb.src = u; thumb.hidden = false; } }, function () {});
    }
    pAdd.addEventListener('click', function () { pic.click(); });
    pic.addEventListener('change', function () {
      var f = pic.files && pic.files[0];
      pic.value = '';
      if (!f) return;
      pAdd.disabled = true; say(t('am.sending'));
      shrink(f).then(function (b) { return call(o.base, a.id, 'gate-photo', 'PUT', b); })
        .then(function () { has['gate-photo'] = true; say(t('am.sent')); photoUi(); }, function (e) { say(e.message); })
        .then(function () { pAdd.disabled = false; });
    });
    pDel.addEventListener('click', function () {
      pDel.disabled = true;
      call(o.base, a.id, 'gate-photo', 'DELETE').then(function () { has['gate-photo'] = false; say(t('am.deleted')); photoUi(); }, function (e) { say(e.message); })
        .then(function () { pDel.disabled = false; });
    });

    // ── یادداشتِ صوتی ──
    var vRec = btn(''); var vDel = btn(t('am.del')); var vSend = btn(t('am.send')); var vCancel = btn(t('am.cancel'));
    var vState = el('span', { attrs: { id: 'am-voice-state' } });
    var audio = el('audio', { attrs: { controls: '', preload: 'none' }, hidden: true });
    var rec = null; var stream = null; var chunks = []; var timer = 0; var take = null; var url = '';
    function closeMic() { if (stream) stream.getTracks().forEach(function (k) { k.stop(); }); stream = null; clearTimeout(timer); }
    function forget() { if (url) URL.revokeObjectURL(url); url = ''; take = null; audio.hidden = true; audio.removeAttribute('src'); }
    function voiceUi() {
      var recording = Boolean(rec); var ready = Boolean(take);
      vRec.textContent = recording ? t('am.stop') : has['voice-note'] ? t('am.rerecord') : t('am.record');
      vRec.hidden = ready; vSend.hidden = !ready; vCancel.hidden = !ready && !recording;
      vDel.hidden = !has['voice-note'] || recording || ready;
      vState.textContent = recording ? t('am.recording') : has['voice-note'] ? t('am.saved') : t('am.none');
    }
    function stop() { if (rec && rec.state !== 'inactive') rec.stop(); }
    vRec.addEventListener('click', function () {
      if (rec) { stop(); return; }
      var type = window.MediaRecorder && TYPES.filter(function (x) { return MediaRecorder.isTypeSupported(x); })[0];
      if (!type || !navigator.mediaDevices) { say(t('am.err.mic')); return; }
      navigator.mediaDevices.getUserMedia({ audio: true }).then(function (s) {
        stream = s; chunks = []; forget();
        rec = new MediaRecorder(s, { mimeType: type, audioBitsPerSecond: 32000 });
        rec.ondataavailable = function (e) { if (e.data && e.data.size) chunks.push(e.data); };
        rec.onstop = function () {
          closeMic(); rec = null;
          if (chunks.length) { take = new Blob(chunks, { type: type.split(';')[0] }); url = URL.createObjectURL(take); audio.src = url; audio.hidden = false; }
          voiceUi();
        };
        rec.start(1000);
        timer = setTimeout(stop, VOICE_MS);
        voiceUi();
      }, function () { closeMic(); say(t('am.err.mic')); });
    });
    vCancel.addEventListener('click', function () { if (rec) { chunks = []; stop(); } forget(); voiceUi(); });
    vSend.addEventListener('click', function () {
      if (!take) return;
      vSend.disabled = true; say(t('am.sending'));
      call(o.base, a.id, 'voice-note', 'PUT', take).then(function () { has['voice-note'] = true; forget(); say(t('am.sent')); }, function (e) { say(e.message); })
        .then(function () { vSend.disabled = false; voiceUi(); });
    });
    vDel.addEventListener('click', function () {
      vDel.disabled = true;
      call(o.base, a.id, 'voice-note', 'DELETE').then(function () { has['voice-note'] = false; say(t('am.deleted')); }, function (e) { say(e.message); })
        .then(function () { vDel.disabled = false; voiceUi(); });
    });

    box.append(
      el('div', { class: 'spread' }, el('b', { text: t('am.photo') }), pState), thumb, pic, el('div', { class: 'row' }, pAdd, pDel),
      el('div', { class: 'spread' }, el('b', { text: t('am.voice') }), vState), audio, el('div', { class: 'row' }, vRec, vSend, vCancel, vDel),
      msg
    );
    o.into.appendChild(box);
    photoUi(); voiceUi();
    return { close: function () { if (rec) { chunks = []; stop(); } closeMic(); forget(); box.remove(); } };
  };
})();
