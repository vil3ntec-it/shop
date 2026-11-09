/*
 * ویترین — ضبطِ پیامِ صوتیِ مشتری (CHAT-07). دیربار: `store/chat.js` فقط با زدنِ دکمهٔ میکروفون (پرچمِ خاموشِ
 * `shop.chat.voice` ⇒ `limits.voice`) این را می‌خواهد (`K.js`)؛ واژه‌نامه‌اش هم جدا (`K.part('voice')`). پخشِ پیامِ رفته همان
 * `voice` ِ chat.js است.
 *
 * ماشینِ حالت همان `core/chat/VoiceFlow.kt`، راهِ «زدن» (وب نگه‌داشتن ندارد): «شروع» ⇒ ضبط ⇒ «پایان و فرستادن» (بزرگ‌تر از
 * `audioMax` ⇒ همان ردیفِ پیش از فرستادن با دلیل) یا «لغو». سقفِ زمان (۵ دقیقه، همان `ChatMedia.VOICE_MAX_MS`)، نزدیکِ سقفِ
 * حجم یا پنهان شدنِ برگه ⇒ ایستادن و ردیفِ «فرستادن/لغو» با پیش‌شنوایی (blob: ِ همین صفحه؛ CSP: media-src 'self' blob:).
 * ناحیهٔ زنده فقط با گذرها عوض می‌شود، نه با تیکِ زمان. بیرون رفتن از ضبط همیشه میکروفون را می‌بندد (هر track)؛ بستنِ
 * برگه یا رفتن از صفحه ضبط را لغو می‌کند.
 * ⛔ میکروفون فقط با زدنِ دکمه (Permissions-Policy ِ ویترین: microphone=(self)). رد، نبودِ میکروفون یا مرورگرِ بی
 *   MediaRecorder ⇒ پیامِ روشن. بارگذاری همان XHR ِ عکس (پیشرفت، «لغو»، صفِ بی‌اینترنت) با یک `clientId` برای هر ضبط و همان
 *   بایت ⇒ تکرار پیامِ دوم یا سهمِ دوم نمی‌سازد (CHAT-11). مرز سرور است: نوع از بایت، `audioMax`، مدت ≤ ۱۵ دقیقه، سطلِ بارگذاری.
 */
(function () {
  'use strict';
  var $ = K.$; var el = K.el; var t = K.t;
  var MAX_MS = 5 * 60 * 1000;
  var BITS = 32000;
  var HEAD = 64 * 1024;   //  حاشیهٔ سقفِ حجم: ضبط پیش از رسیدن به `audioMax` می‌ایستد (تکه‌ها یک‌ثانیه‌ای‌اند)
  //  AAC ِ MP4 اول: برنامهٔ آیفونِ دکان Opus/WebM را پخش نمی‌کند؛ سرور هر سه را از بایت می‌شناسد (`chat-media.sniff`)
  var TYPES = ['audio/mp4;codecs=mp4a.40.2', 'audio/webm;codecs=opus', 'audio/ogg;codecs=opus', 'audio/mp4', 'audio/webm'];

  /** همان `VoiceFlow.next/settle` (بی نگه‌داشتن) — منطقِ خالص */
  var Flow = {
    next: function (p, i) {
      var rec = p === 'tap';
      var same = { phase: p, effect: 'none' };
      if (i === 'start') return rec ? same : { phase: 'tap', effect: 'start', say: 'recording' };
      if (i === 'limit') return rec ? { phase: 'ready', effect: 'stop' } : same;
      if (i === 'finish') return rec ? { phase: 'ready', effect: 'stop_send' } : same;
      if (i === 'send') return p === 'ready' ? { phase: 'idle', effect: 'send', say: 'sent' } : same;
      if (i === 'cancel') {
        if (rec) return { phase: 'idle', effect: 'cancel', say: 'cancelled' };
        if (p === 'ready') return { phase: 'idle', effect: 'discard', say: 'cancelled' };
      }
      return same;
    },
    settle: function (send, size, limit, canWrite) {
      if (!size) return { phase: 'idle', effect: 'none', say: 'empty' };
      if (send && Flow.tooBig(size, limit)) return { phase: 'ready', effect: 'none', say: 'too_big' };
      if (send && canWrite) return { phase: 'idle', effect: 'send', say: 'sent' };
      return { phase: 'ready', effect: 'none', say: 'ready' };
    },
    tooBig: function (size, limit) { return limit > 0 && size > limit; },
  };

  var C = null; var v = null; var ready = null;
  function supported() { return Boolean(window.MediaRecorder && navigator.mediaDevices && navigator.mediaDevices.getUserMedia); }
  function pickType() {
    if (typeof MediaRecorder.isTypeSupported !== 'function') return '';
    for (var i = 0; i < TYPES.length; i++) if (MediaRecorder.isTypeSupported(TYPES[i])) return TYPES[i];
    return '';
  }
  function mmss(ms) {
    var s = Math.max(0, Math.floor(ms / 1000)); var r = s % 60;
    return K.fa(Math.floor(s / 60), 0) + ':' + (r < 10 ? K.fa(0, 0) : '') + K.fa(r, 0);
  }
  function cap() { return Number((C.lim() || {}).audioMax) || 0; }
  function say(text) { var s = $('ct-vc-say'); if (s) { s.textContent = ''; s.textContent = text || ''; } }

  function build() {
    if ($('ct-vc')) return;
    var form = $('ct-form');
    var box = el('div', { class: 'ct-ph ct-vc', attrs: { id: 'ct-vc', role: 'group', 'aria-label': t('vc.title'), 'data-st': 'idle' }, hidden: true },
      el('div', { class: 'ct-ph-side' },
        el('span', { class: 'num ct-time', attrs: { id: 'ct-vc-time' } }),
        el('progress', { class: 'ct-bar', attrs: { id: 'ct-vc-bar', max: '1', value: '0', 'aria-label': t('vc.len') } }),
        el('small', { attrs: { id: 'ct-vc-st' } }),
        el('audio', { attrs: { id: 'ct-vc-au', controls: '', preload: 'metadata', 'aria-label': t('vc.prev') }, hidden: true }),
        el('div', { class: 'ct-ph-acts' },
          el('button', { class: 'btn btn-accent', attrs: { id: 'ct-vc-finish', type: 'button' }, text: t('vc.finish'), hidden: true, on: { click: function () { input('finish'); } } }),
          el('button', { class: 'btn btn-accent', attrs: { id: 'ct-vc-send', type: 'button' }, text: t('vc.send'), hidden: true, on: { click: function () { input('send'); } } }),
          el('button', { class: 'btn', attrs: { id: 'ct-vc-retry', type: 'button' }, text: t('vc.retry'), hidden: true, on: { click: function () { go(v); } } }),
          el('button', { class: 'btn btn-ghost', attrs: { id: 'ct-vc-cancel', type: 'button' }, text: t('vc.cancel'), on: { click: cancel } }))));
    form.parentNode.insertBefore(box, form);
    form.parentNode.insertBefore(el('p', { class: 'sr-only', attrs: { id: 'ct-vc-say', role: 'status' } }), form);
  }

  //  data-st: rec | ready | up | post | queued | retry | err
  function mode(st, text) {
    var box = $('ct-vc');
    box.hidden = false;
    box.setAttribute('data-st', st);
    if (st === 'up' || st === 'post') box.setAttribute('aria-busy', 'true'); else box.removeAttribute('aria-busy');
    var big = v && v.blob && Flow.tooBig(v.blob.size, cap());
    $('ct-vc-finish').hidden = st !== 'rec';
    $('ct-vc-send').hidden = !(st === 'ready' && !big);
    $('ct-vc-retry').hidden = !(st === 'queued' || st === 'retry');
    $('ct-vc-cancel').disabled = st === 'post';
    $('ct-vc-au').hidden = !(v && v.url && st !== 'rec');
    $('ct-vc-st').textContent = text || '';
  }
  function tick() {
    if (!v) return;
    var ms = v.phase === 'tap' ? Date.now() - v.t0 : v.ms;
    $('ct-vc-time').textContent = mmss(ms) + (v.phase === 'tap' ? ' / ' + mmss(MAX_MS) : '');
    if (v.phase === 'tap') $('ct-vc-bar').value = Math.min(1, ms / MAX_MS);
  }
  function release(s) { if (s) s.getTracks().forEach(function (k) { k.stop(); }); }
  function clean(my) {
    clearInterval(my.timer); clearTimeout(my.again);
    release(my.stream); my.stream = null;
    if (my.xhr) my.xhr.abort();
    if (my.url) URL.revokeObjectURL(my.url);
    my.url = '';
  }
  function close(text) {
    var my = v; v = null;
    if (my) clean(my);
    var box = $('ct-vc');
    var had = Boolean(box && box.contains(document.activeElement));
    if (box) { box.hidden = true; box.setAttribute('data-st', 'idle'); $('ct-vc-au').removeAttribute('src'); }
    say(text);
    var mic = $('ct-vc-mic');
    if (had && mic && !mic.hidden) mic.focus();
  }
  /** خطایی که ضبطی نمی‌سازد (اجازه، میکروفون، مرورگر): فقط پیام و «لغو» */
  function refuse(key) {
    v = null;
    var box = $('ct-vc');
    box.hidden = false; box.setAttribute('data-st', 'err'); box.removeAttribute('aria-busy');
    ['ct-vc-finish', 'ct-vc-send', 'ct-vc-retry', 'ct-vc-au'].forEach(function (id) { $(id).hidden = true; });
    $('ct-vc-cancel').disabled = false;
    $('ct-vc-time').textContent = '';
    $('ct-vc-st').textContent = t(key);
    say(t(key));
  }
  /** «لغو» از هر حالت جز «در حالِ ثبتِ پیام»: ضبط/پیش‌نویس دور، بارگذاری بریده، میکروفون بسته (VoiceFlow: CANCEL/DISCARD) */
  function cancel() {
    var my = v;
    if (my && my.posting) return;
    if (my) {
      my.drop = true;
      if (my.rec && my.rec.state !== 'inactive') { try { my.rec.stop(); } catch (_) { /* ایستاده */ } }
    }
    close(my ? t('vc.cancelled') : '');
  }

  /** گذرِ ماشین ⇒ کارِ ضبط‌کننده */
  function input(i) {
    if (i === 'cancel') { cancel(); return; }
    if (!v) return;
    var s = Flow.next(v.phase, i);
    if (s.effect === 'none') return;
    if (i === 'send' && Flow.tooBig(v.blob ? v.blob.size : 0, cap())) return;
    v.phase = s.phase;
    if (s.effect === 'stop' || s.effect === 'stop_send') {
      var my = v; my.send = s.effect === 'stop_send'; my.why = i;
      clearInterval(my.timer);
      my.ms = Math.max(1, Math.min(15 * 60 * 1000, Date.now() - my.t0));
      try { my.rec.stop(); } catch (_) { stopped(my); }
      return;
    }
    if (s.effect === 'send') go(v);
  }
  /** ضبط ایستاد ⇒ یک Blob، میکروفون بسته، `settle` */
  function stopped(my) {
    release(my.stream); my.stream = null;
    if (my.drop || v !== my) return;
    var type = (my.rec && my.rec.mimeType) || my.type || 'audio/webm';
    my.blob = new Blob(my.chunks, { type: type });
    my.chunks = [];
    my.ctype = type.split(';')[0];
    var conv = C.conv();
    var s = Flow.settle(my.send, my.blob.size, cap(), Boolean(conv && conv.id === my.conv && conv.canSend));
    my.phase = s.phase;
    if (s.say === 'empty') { close(t('vc.empty')); return; }
    my.url = URL.createObjectURL(my.blob);
    $('ct-vc-au').src = my.url;
    tick();
    if (s.effect === 'send') { go(my); return; }
    var text = s.say === 'too_big' || Flow.tooBig(my.blob.size, cap()) ? t('vc.big')
      : my.full ? t('vc.full') : my.why === 'limit' && !my.hid ? t('vc.limit', { m: K.fa(MAX_MS / 60000, 0) }) : t('vc.ready');
    mode('ready', text);
    say(text);
    var f = $('ct-vc-send').hidden ? $('ct-vc-cancel') : $('ct-vc-send');
    f.focus();
  }

  function start() {
    var conv = C.conv();
    if (!conv || !conv.canSend) return;
    build();
    if (!supported()) { refuse('vc.unsupported'); return; }
    var my = v = { phase: 'idle', conv: conv.id, chunks: [], bytes: 0, wait: 2000,
      key: 'ct-' + Date.now().toString(36) + '-' + Math.random().toString(36).slice(2, 10) };
    mode('ask', '');
    $('ct-vc-time').textContent = ''; $('ct-vc-bar').value = 0;
    navigator.mediaDevices.getUserMedia({ audio: { echoCancellation: true, noiseSuppression: true } }).then(function (stream) {
      if (v !== my) { release(stream); return; }   //  در این میان لغو شد یا برگه بسته شد
      my.stream = stream;
      my.type = pickType();
      var opts = { audioBitsPerSecond: BITS };
      if (my.type) opts.mimeType = my.type;
      var rec;
      try { rec = new MediaRecorder(stream, opts); } catch (_) { rec = new MediaRecorder(stream); }
      my.rec = rec;
      rec.ondataavailable = function (e) { if (e.data && e.data.size) { my.chunks.push(e.data); my.bytes += e.data.size; } };
      rec.onstop = function () { stopped(my); };
      rec.onerror = function () { if (v === my) { my.drop = true; clean(my); refuse('vc.fail'); } };
      var s = Flow.next('idle', 'start');
      my.phase = s.phase;
      my.t0 = Date.now();
      rec.start(1000);
      mode('rec', t('vc.recording'));
      say(t('vc.recording'));
      tick();
      my.timer = setInterval(function () {
        if (v !== my || my.phase !== 'tap') return;
        if (K.sheetOpen() !== $('chat-sheet')) { input('cancel'); return; }
        tick();
        var c = cap();
        if (c && my.bytes > 0 && my.bytes >= c - Math.min(HEAD, c / 4)) { my.full = true; input('limit'); return; }
        if (Date.now() - my.t0 >= MAX_MS) input('limit');
      }, 250);
      $('ct-vc-finish').focus();
    }, function (e) {
      if (v !== my) return;
      var n = e && e.name;
      refuse(n === 'NotAllowedError' || n === 'SecurityError' ? 'vc.denied'
        : n === 'NotFoundError' || n === 'OverconstrainedError' ? 'vc.nomic' : n === 'NotReadableError' ? 'vc.busy' : 'vc.fail');
      $('ct-vc-cancel').focus();
    });
  }

  /** بارگذاری ⇒ پیام؛ تلاشِ دوباره همان بایت و همان `clientId` (سرور: همان رسانه `reused` و همان پیام `replayed`) */
  function go(my) {
    if (!my || v !== my || my.busy || !my.blob) return;
    clearTimeout(my.again);
    my.busy = true; my.queued = false;
    mode('up', t('vc.up'));
    var total = my.blob.size;
    var bar = function (n) { $('ct-vc-bar').value = Math.min(0.99, n / total); };
    $('ct-vc-bar').value = 0;
    Promise.resolve().then(function () {
      if (!my.mediaId) return C.upload(my, 'main', my.blob, bar, my.ctype).then(function (r) { my.mediaId = r.mediaId; });
      return null;
    }).then(function () {
      if (v !== my) { var x = new Error('gone'); x.status = -1; throw x; }
      my.posting = true; mode('post', t('vc.up')); bar(total);
      return C.api(C.path(my.conv, '/messages'), { method: 'POST', body: {
        clientId: my.key, kind: 'audio', mediaId: my.mediaId, meta: { durationMs: my.ms } } });
    }).then(function (r) {
      my.busy = my.posting = false;
      if (v === my) close(t('vc.sent'));
      C.put(r.message, my.conv);
    }).catch(function (e) {
      my.busy = my.posting = false;
      if (v !== my || e.status === -1) return;
      if (e.status === 0) {   //  بی‌اینترنت: صف — با `online` یا پس از کمی، با همان شناسه و همان بایت
        my.queued = true;
        mode('queued', t('vc.queued'));
        my.wait = Math.min(60000, my.wait * 2);
        my.again = setTimeout(function () { if (v === my) go(my); }, my.wait);
        return;
      }
      if (e.code === 'chat_closed' || e.code === 'chat_blocked') C.reload();
      mode(e.status === 401 || e.status === 429 || e.status >= 500 ? 'retry' : 'err', e.message);
      say(e.message);
      if (e.status === 401) C.fail(e);
    });
  }
  window.addEventListener('online', function () { if (v && v.queued) go(v); });
  window.addEventListener('pagehide', function () { if (v) cancel(); });
  //  برگهٔ پنهان ⇒ میکروفون بسته، ضبط تا همین‌جا نگه داشته (نه میکروفونِ روشن در پس‌زمینه)
  document.addEventListener('visibilitychange', function () { if (document.hidden && v && v.phase === 'tap') { v.hid = true; input('limit'); } });

  window.StoreChatVoice = {
    flow: Flow,
    MAX_MS: MAX_MS,
    /** دکمهٔ میکروفون: ضبطِ تازه، یا تمرکز روی ضبطِ در راه */
    open: function (ctx) {
      C = ctx;
      ready = ready || K.part('voice', $('ct-vc'));
      return ready.then(function (got) {
        if (!got) { ready = null; var n = new Error('net'); n.status = 0; throw n; }   //  واژه‌نامه نیامد ⇒ بارِ بعد دوباره
        if (v) { var f = $('ct-vc').querySelector('button:not([hidden]):not([disabled])'); if (f) f.focus(); return; }
        start();
      });
    },
    /** بستنِ برگه یا رشتهٔ دیگر: ضبط/پیش‌نویسِ صدا لغو و میکروفون بسته (بارگذاریِ پیامِ در راه هم بریده می‌شود) */
    drop: function (keepConv) {
      if (!v || (keepConv && v.conv === keepConv)) return;
      if (v.posting) return;
      v.drop = true;
      if (v.rec && v.rec.state !== 'inactive') { try { v.rec.stop(); } catch (_) { /* ایستاده */ } }
      close('');
    },
  };
})();
