/*
 * ویترین — عکسِ دروازه و یادداشتِ صوتیِ نشانی روی صفحهٔ پیگیری (CHK-08، §۴.۴؛ `lib/order-address-media.js`، پرچمِ خاموشِ
 * `shop.storefront.address_media`). فقط وقتی پاسخِ پیگیری کلیدِ `addressMedia` را دارد (سفارشِ ارسالیِ هنوز به پیک نرسیده) بار
 * می‌شود (`order.js`)؛ پرچمِ خاموش ⇒ نه این اسکریپت، نه واژه‌نامه‌اش (`kit/i18n/addrmedia.<زبان>.json`)، نه هیچ گره.
 *
 *   عکس ── «عکسِ دروازه» ⇒ انتخاب یا دوربین (`input[type=file]`) ⇒ `StoreChatPhoto.prepare` (کوچک، JPEG ِ تازه بی EXIF) ⇒
 *           `PUT …/address-media/gate_photo`. دوباره ⇒ جایگزین؛ «برداشتن» ⇒ `DELETE`.
 *   صدا ── «ضبطِ یادداشتِ صوتی» (میکروفون فقط با همین زدن) ⇒ تا [MAX_MS] ⇒ «پایان و فرستادن» یا «لغو» ⇒ `PUT …/voice_note`.
 *           بیرون رفتن از ضبط همیشه میکروفون را می‌بندد؛ پنهان شدنِ برگه ضبط را لغو می‌کند.
 * ⛔ هر متن با textContent (`K.el`)؛ «فرستاده شد» فقط پس از پاسخِ سرور. نوع و اندازه را سرور از بایت می‌سنجد (مرز همان است).
 */
(function () {
  'use strict';
  var $ = K.$; var el = K.el; var t = K.t;
  var MAX_MS = 60 * 1000;
  var TYPES = ['audio/mp4;codecs=mp4a.40.2', 'audio/webm;codecs=opus', 'audio/ogg;codecs=opus', 'audio/mp4', 'audio/webm'];

  window.StoreAddrMedia = function (ctx) {
    var base = '/api/store/' + encodeURIComponent(ctx.slug) + '/orders/';
    var box = $('o-am');
    if (!box) {
      box = el('div', { class: 'card card-lg stack', hidden: true, attrs: { id: 'o-am', role: 'group', 'aria-labelledby': 'o-am-h' } });
      var at = $('o-subs');
      at.parentNode.insertBefore(box, at);
    }
    var token = '';
    var view = null;     //  آخرین `addressMedia` ِ پیگیری
    var busy = false;    //  بارگذاری یا ضبط در راه ⇒ پیگیریِ دوره‌ای کارت را از نو نمی‌سازد
    var rec = null;      //  { mr, stream, chunks, timer, started, cancelled }
    var msg = null;

    function url(kind) { return base + encodeURIComponent(token) + '/address-media' + (kind ? '/' + kind : ''); }
    function fail(e) { K.note(msg, 'err', e && e.message ? e.message : t('err.generic')); }
    function has(kind) { return view && (view.items || []).some(function (x) { return x.kind === kind; }); }

    function send(kind, blob) {
      return fetch(url(kind), {
        method: 'PUT', body: blob, credentials: 'omit', cache: 'no-store',
        headers: { Accept: 'application/json', 'Accept-Language': K.lang, 'Content-Type': blob.type || 'application/octet-stream' },
      }).then(function (res) {
        return res.json().catch(function () { return {}; }).then(function (b) {
          if (res.ok) return b;
          var c = (b && b.error && b.error.code) || '';
          throw new Error(K.has('am.err.' + c) ? t('am.err.' + c) : res.status === 429 ? t('err.rate')
            : (K.lang === 'fa' && b && b.error && b.error.message ? b.error.message : t('err.generic')));
        });
      }, function () { throw new Error(t('err.network')); });
    }

    function reload(say) {
      return K.api(url('')).then(function (r) {
        view = { photoMax: view.photoMax, voiceMax: view.voiceMax, items: r.items || [] };
        busy = false; draw();
        if (say) K.note(msg, 'ok', say);
      }, function (e) { busy = false; draw(); fail(e); });
    }

    function upload(kind, blob, say) {
      busy = true; draw();
      K.note(msg, 'info', t('am.sending'));
      return send(kind, blob).then(function () { return reload(say); }, function (e) { busy = false; draw(); fail(e); });
    }

    function remove(kind) {
      busy = true; draw();
      K.api(url(kind), { method: 'DELETE' }).then(function () { return reload(t('am.removed')); }, function (e) { busy = false; draw(); fail(e); });
    }

    function pickPhoto(input) {
      var f = input.files && input.files[0];
      input.value = '';
      if (!f) return;
      busy = true; draw();
      K.js('/store/chat-photo.js').then(function () {
        return window.StoreChatPhoto.prepare(f, { imageMax: view.photoMax, thumbMax: 16 * 1024 });
      }).then(function (p) { busy = false; return upload('gate_photo', p.main, t('am.photo.ok')); }, function () {
        busy = false; draw(); K.note(msg, 'err', t('am.photo.bad'));
      });
    }

    function stopTracks(r) { if (r && r.stream) r.stream.getTracks().forEach(function (tr) { tr.stop(); }); }
    function endRec(cancel) {
      if (!rec) return;
      rec.cancelled = cancel;
      clearInterval(rec.timer);
      if (rec.mr.state !== 'inactive') { rec.mr.stop(); return; }
      stopTracks(rec); rec = null; busy = false; draw();
    }
    function onHide() { if (document.hidden) endRec(true); }

    function startVoice() {
      if (!window.MediaRecorder || !navigator.mediaDevices || !navigator.mediaDevices.getUserMedia) { K.note(msg, 'err', t('am.voice.none')); return; }
      busy = true; draw();
      navigator.mediaDevices.getUserMedia({ audio: true }).then(function (stream) {
        var type = TYPES.filter(function (x) { return MediaRecorder.isTypeSupported && MediaRecorder.isTypeSupported(x); })[0];
        var mr = type ? new MediaRecorder(stream, { mimeType: type, audioBitsPerSecond: 32000 }) : new MediaRecorder(stream);
        rec = { mr: mr, stream: stream, chunks: [], started: Date.now(), cancelled: false, timer: 0 };
        mr.ondataavailable = function (e) { if (e.data && e.data.size) rec.chunks.push(e.data); };
        mr.onstop = function () {
          var r = rec; rec = null;
          stopTracks(r);
          document.removeEventListener('visibilitychange', onHide);
          if (!r || r.cancelled || !r.chunks.length) { busy = false; draw(); K.note(msg, 'info', t('am.voice.cancelled')); return; }
          var blob = new Blob(r.chunks, { type: (mr.mimeType || type || 'audio/webm').split(';')[0] });
          if (blob.size > view.voiceMax) { busy = false; draw(); K.note(msg, 'err', t('am.err.too_large')); return; }
          upload('voice_note', blob, t('am.voice.ok'));
        };
        document.addEventListener('visibilitychange', onHide);
        mr.start(1000);
        rec.timer = setInterval(function () {
          var ms = Date.now() - rec.started;
          if (ms >= MAX_MS) { endRec(false); return; }
          var left = $('o-am-left');
          if (left) left.textContent = t('am.voice.left', { n: K.fa(Math.ceil((MAX_MS - ms) / 1000), 0) });
        }, 500);
        draw();
        K.note(msg, 'info', t('am.voice.rec'));
      }, function () { busy = false; draw(); K.note(msg, 'err', t('am.voice.denied')); });
    }

    function row(title, done, actions) {
      return el('div', { class: 'stack' },
        el('strong', { text: title }),
        el('small', { class: 'muted', text: done ? t('am.has') : t('am.none') }),
        el('div', { class: 'row wrap-row' }, actions));
    }
    function btn(text, cls, click, off) {
      return el('button', { class: cls, text: text, attrs: { type: 'button', disabled: off ? 'disabled' : null }, on: { click: click } });
    }

    function draw() {
      K.clear(box);
      box.hidden = !view;
      if (!view) return;
      box.appendChild(el('h2', { text: t('am.title'), attrs: { id: 'o-am-h' } }));
      box.appendChild(el('p', { class: 'muted', text: t('am.hint') }));
      var input = el('input', { attrs: { type: 'file', accept: 'image/jpeg,image/png,image/webp', capture: 'environment', 'aria-label': t('am.photo'), id: 'o-am-file' }, hidden: true,
        on: { change: function () { pickPhoto(input); } } });
      var photoActs = el('div', { class: 'row wrap-row' },
        btn(has('gate_photo') ? t('am.photo.again') : t('am.photo'), 'btn btn-accent', function () { input.click(); }, busy),
        has('gate_photo') ? btn(t('am.photo.remove'), 'btn btn-ghost', function () { remove('gate_photo'); }, busy) : null,
        input);
      box.appendChild(row(t('am.photo.title'), has('gate_photo'), photoActs));
      var voiceActs = rec
        ? el('div', { class: 'row wrap-row' },
          btn(t('am.voice.stop'), 'btn btn-accent', function () { endRec(false); }),
          btn(t('am.voice.cancel'), 'btn btn-ghost', function () { endRec(true); }),
          el('small', { class: 'muted', text: '', attrs: { id: 'o-am-left', 'aria-live': 'off' } }))
        : el('div', { class: 'row wrap-row' },
          btn(has('voice_note') ? t('am.voice.again') : t('am.voice'), 'btn btn-accent', startVoice, busy),
          has('voice_note') ? btn(t('am.voice.remove'), 'btn btn-ghost', function () { remove('voice_note'); }, busy) : null);
      box.appendChild(row(t('am.voice.title'), has('voice_note'), voiceActs));
      box.appendChild(msg);
    }

    function render(o, tk) {
      if (busy || rec) return;
      token = tk;
      view = o.addressMedia || null;
      draw();
    }

    return K.part('addrmedia', $('o-am')).then(function (ok) {
      if (!ok) throw new Error('i18n');
      msg = el('div', { class: 'note', hidden: true, attrs: { role: 'status', id: 'o-am-msg' } });
      return { render: render };
    });
  };
})();
