/* ---------------------------------------------------------------------------
 *  چتِ مشتری ↔ دکان — هستهٔ مشترکِ مرورگر (v3 §۳۳؛ CHAT-02/03/04/05/11)
 *
 *  یک فایل، دو جا: نسخهٔ وبِ دکان (`license/chat-core.js`) و ویترینِ مشتری
 *  (`server/public/store/chat-core.js`). دو نسخه بایت‌به‌بایت یکی‌اند و
 *  `server/test/chat-core.test.js` همین را می‌سنجد؛ ویترین CSP ِ سخت دارد و
 *  فقط اسکریپتِ همان مبدأ را می‌پذیرد، پس کپی است نه پیوند.
 *
 *  ── صفِ ارسال ───────────────────────────────────────────────────────────
 *  هر پیام همان لحظهٔ نوشتن یک `clientId` ِ یکتا می‌گیرد و تا رسیدن همان را
 *  نگه می‌دارد. سرور با همین شناسه پیامِ تکراری نمی‌سازد (CHAT-11)، پس
 *  دوباره فرستادن پس از قطعِ شبکه یا بسته شدنِ برگه بی‌خطر است.
 *    • ترتیب: در هر گفت‌وگو فقط سرِ صف فرستاده می‌شود؛ تا او نرفته، بعدی
 *      نمی‌رود (CHAT-02 «با ترتیبِ درست»).
 *    • خطای گذرا (بی شبکه، ۴۰۸، ۴۲۹، ۵xx) ⇒ «در انتظار» با backoff ِ نمایی
 *      و لرزش؛ رویدادِ «آنلاین شد» همه را بیدار می‌کند.
 *    • خطای قطعی (۴۰۰، ۴۰۳، ۴۰۹، ۴۱۳، ۴۱۵…) ⇒ «نرفت» با پیامِ سرور؛ کاربر
 *      «دوباره» یا «لغو» می‌زند و صفِ پشتِ سرش گیر نمی‌کند.
 *    • ۴۰۱ ⇒ «نیاز به ورود/تأیید»؛ پیام می‌ماند تا هویت تازه شود.
 *    • پیامی که در لحظهٔ بسته شدن «در حال ارسال» بود، با همان شناسه
 *      دوباره صف می‌شود — سرور همان پیام را برمی‌گرداند، نه دومی.
 *
 *  ── همگام‌سازی ──────────────────────────────────────────────────────────
 *  `Thread` پیام‌ها را با شناسه نگه می‌دارد و با `after=<rev>` جلو می‌رود؛
 *  ویرایش و حذف همان ردیف را جایگزین می‌کنند. رسید فقط از عددهای واقعیِ
 *  سرور می‌آید (CHAT-04): «رسید» یعنی دستگاهِ طرف پیام را گرفت، «خوانده»
 *  یعنی طرف آن را روی صفحه دید.
 *
 *  ⛔ متنِ پیام هیچ‌جا لاگ نمی‌شود. ⛔ این‌جا هیچ درخواستی فرستاده نمی‌شود؛
 *  فرستادن کارِ صدا زننده است (`drain(queue, conv, send)`).
 * ------------------------------------------------------------------------- */
(function (root, factory) {
  'use strict';
  if (typeof module === 'object' && module.exports) module.exports = factory();
  else root.TohidChatCore = factory();
})(typeof self !== 'undefined' ? self : this, function () {
  'use strict';

  var BACKOFF = { baseMs: 2000, maxMs: 5 * 60 * 1000 };
  var CLIENT_ID_RE = /^[A-Za-z0-9_.:-]{8,128}$/;
  var IMAGE = { mainPx: 1600, thumbPx: 320, mainQ: 0.82, thumbQ: 0.6, minQ: 0.4 };
  var VOICE_MAX_MS = 5 * 60 * 1000;
  //  به ترتیبِ ترجیح: Opus (کم‌حجم‌ترین برای گفتار)، بعد AAC
  var VOICE_TYPES = ['audio/ogg;codecs=opus', 'audio/webm;codecs=opus', 'audio/mp4;codecs=mp4a.40.2', 'audio/mp4', 'audio/aac'];

  var LABEL = {
    queued: 'در صفِ دستگاه',
    sending: 'در حالِ ارسال…',
    waiting: 'منتظرِ اینترنت',
    auth: 'منتظرِ ورود/تأییدِ دوباره',
    failed: 'نرفت',
    sent: 'به سرور رسید',
    delivered: 'به دستگاهِ طرف رسید',
    read: 'خوانده شد',
  };

  function rnd(rand) { return typeof rand === 'function' ? rand() : Math.random(); }

  /** مهلتِ تلاشِ nام (از ۱): ۲، ۴، ۸… ثانیه تا سقفِ ۵ دقیقه، با لرزشِ نیمه. */
  function backoffMs(attempt, rand) {
    var n = Math.max(1, Math.floor(attempt || 1));
    var d = Math.min(BACKOFF.maxMs, BACKOFF.baseMs * Math.pow(2, Math.min(n - 1, 20)));
    return Math.round(d / 2 + rnd(rand) * (d / 2));
  }

  /** شناسهٔ یکتای پیام — زمان + ۲۰ نویسهٔ تصادفی؛ با الگوی سرور جور است. */
  function newClientId(rand, now) {
    var abc = 'abcdefghijklmnopqrstuvwxyz0123456789';
    var s = '';
    var g = typeof crypto !== 'undefined' && crypto && crypto.getRandomValues && typeof rand !== 'function'
      ? crypto.getRandomValues(new Uint8Array(20)) : null;
    for (var i = 0; i < 20; i++) s += abc.charAt(g ? g[i] % 36 : Math.floor(rnd(rand) * 36) % 36);
    return 'c' + Number(now === undefined ? Date.now() : now).toString(36) + '-' + s;
  }

  /** خطای درخواست ⇐ «دوباره بعداً» (retry)، «هویت» (auth) یا «قطعی» (fail). */
  function classify(err) {
    var st = err && typeof err.status === 'number' ? err.status : 0;
    if (!st) return 'retry';
    if (st === 401) return 'auth';
    if (st === 408 || st === 425 || st === 429 || st >= 500) return 'retry';
    return 'fail';
  }

  // ════════════════════════════════════════════════════════════════════
  //  صف
  // ════════════════════════════════════════════════════════════════════

  /**
   * @param opts.load  () ⇒ آرایهٔ ذخیره‌شده (یا null)
   * @param opts.save  (items) ⇒ void — هر تغییر بی‌درنگ نوشته می‌شود
   * @param opts.now   () ⇒ ms (برای آزمون)
   * @param opts.rand  () ⇒ [0,1) (برای آزمون)
   */
  function Queue(opts) {
    var o = opts || {};
    this._save = o.save || function () {};
    this.now = o.now || function () { return Date.now(); };
    this.rand = o.rand;
    var raw = null;
    try { raw = o.load ? o.load() : null; } catch (e) { raw = null; }
    this.items = Array.isArray(raw) ? raw.filter(function (x) { return x && CLIENT_ID_RE.test(x.clientId) && x.convId; }) : [];
    //  برگه وسطِ ارسال بسته شد ⇒ همان شناسه دوباره در صف (سرور تکراری نمی‌سازد)
    var fixed = false;
    this.items.forEach(function (x) { if (x.state === 'sending') { x.state = 'queued'; x.nextAt = 0; fixed = true; } });
    if (fixed) this.save();
  }

  Queue.prototype.save = function () { try { this._save(this.items.slice()); } catch (e) { /* حافظه پر/بسته */ } };

  Queue.prototype.add = function (convId, msg) {
    var m = msg || {};
    var t = this.now();
    var last = this.items.length ? this.items[this.items.length - 1].createdAt : 0;
    //  ترتیب حتی با دو پیام در یک میلی‌ثانیه؛ همان عدد در شناسه ⇒ یکتا حتی با تصادفِ بد
    var at = Math.max(t, last + 1);
    var item = {
      clientId: m.clientId && CLIENT_ID_RE.test(m.clientId) ? m.clientId : newClientId(this.rand, at),
      convId: String(convId),
      kind: m.kind || 'text',
      body: m.body == null ? '' : String(m.body),
      meta: m.meta || {},
      replyTo: m.replyTo || null,
      blobKey: m.blobKey || null,
      thumbKey: m.thumbKey || null,
      size: m.size || 0,
      mediaId: null,
      thumbId: null,
      state: 'queued',
      attempts: 0,
      nextAt: 0,
      error: '',
      createdAt: at,
    };
    this.items.push(item);
    this.save();
    return item;
  };

  Queue.prototype.get = function (clientId) {
    for (var i = 0; i < this.items.length; i++) if (this.items[i].clientId === clientId) return this.items[i];
    return null;
  };

  Queue.prototype.forConv = function (convId) {
    var c = String(convId);
    return this.items.filter(function (x) { return x.convId === c; })
      .sort(function (a, b) { return a.createdAt - b.createdAt; });
  };

  Queue.prototype.convs = function () {
    var seen = {};
    var out = [];
    this.items.forEach(function (x) { if (!seen[x.convId] && x.state !== 'failed') { seen[x.convId] = 1; out.push(x.convId); } });
    return out;
  };

  /**
   * سرِ صفِ این گفت‌وگو، اگر الان وقتش است — وگرنه null.
   * «نرفته»ها رد می‌شوند (منتظرِ کاربرند)؛ «در انتظار»ِ زود و «هویت» صف را نگه می‌دارند.
   */
  Queue.prototype.next = function (convId, now) {
    var t = now === undefined ? this.now() : now;
    var list = this.forConv(convId);
    for (var i = 0; i < list.length; i++) {
      var x = list[i];
      if (x.state === 'failed') continue;
      if (x.state === 'sending' || x.state === 'auth') return null;
      if (x.state === 'waiting' && x.nextAt > t) return null;
      return x;
    }
    return null;
  };

  Queue.prototype.patch = function (item, patch) {
    for (var k in patch) if (Object.prototype.hasOwnProperty.call(patch, k)) item[k] = patch[k];
    this.save();
    return item;
  };

  Queue.prototype.sending = function (item) { return this.patch(item, { state: 'sending' }); };

  /** رفت: از صف بیرون — کلیدهای رسانهٔ محلی را برمی‌گرداند تا پاک شوند. */
  Queue.prototype.done = function (clientId) {
    var keys = [];
    this.items = this.items.filter(function (x) {
      if (x.clientId !== clientId) return true;
      if (x.blobKey) keys.push(x.blobKey);
      if (x.thumbKey) keys.push(x.thumbKey);
      return false;
    });
    this.save();
    return keys;
  };

  Queue.prototype.failed = function (item, err, now) {
    var t = now === undefined ? this.now() : now;
    var c = classify(err);
    var msg = (err && err.message) || '';
    if (c === 'retry') {
      var n = (item.attempts || 0) + 1;
      return this.patch(item, { state: 'waiting', attempts: n, nextAt: t + backoffMs(n, this.rand), error: msg });
    }
    if (c === 'auth') return this.patch(item, { state: 'auth', error: msg });
    return this.patch(item, { state: 'failed', error: msg });
  };

  /** «دوباره» ِ کاربر — همان شناسه، از سرِ نو. */
  Queue.prototype.retry = function (clientId) {
    var x = this.get(clientId);
    if (!x || x.state === 'sending') return null;
    return this.patch(x, { state: 'queued', attempts: 0, nextAt: 0, error: '' });
  };

  /** «لغو» — فقط وقتی در راه نیست. کلیدهای رسانهٔ محلی برای پاک کردن. */
  Queue.prototype.cancel = function (clientId) {
    var x = this.get(clientId);
    if (!x || x.state === 'sending') return null;
    return this.done(clientId);
  };

  /** اینترنت برگشت ⇒ هر «در انتظار» همین حالا. */
  Queue.prototype.wake = function () {
    var any = false;
    this.items.forEach(function (x) { if (x.state === 'waiting') { x.nextAt = 0; any = true; } });
    if (any) this.save();
    return any;
  };

  /** هویت تازه شد (ورود/تأییدِ شماره) ⇒ پیام‌های «منتظرِ هویت» دوباره در صف. */
  Queue.prototype.resumeAuth = function () {
    var any = false;
    this.items.forEach(function (x) { if (x.state === 'auth') { x.state = 'queued'; x.nextAt = 0; any = true; } });
    if (any) this.save();
    return any;
  };

  /** نزدیک‌ترین زمانِ بیدار شدن، یا null. */
  Queue.prototype.nextWake = function () {
    var best = null;
    this.items.forEach(function (x) { if (x.state === 'waiting' && (best === null || x.nextAt < best)) best = x.nextAt; });
    return best;
  };

  /** پیامی که سرور با همین clientId دارد دیگر در صف نمی‌ماند (نمایشِ دوباره ممنوع). */
  Queue.prototype.reconcile = function (convId, serverMessages) {
    var ids = {};
    (serverMessages || []).forEach(function (m) { if (m && m.clientId) ids[m.clientId] = 1; });
    var keys = [];
    var c = String(convId);
    var self = this;
    this.items.slice().forEach(function (x) {
      if (x.convId === c && ids[x.clientId] && x.state !== 'sending') keys = keys.concat(self.done(x.clientId));
    });
    return keys;
  };

  /**
   * فرستادنِ صفِ یک گفت‌وگو، یکی‌یکی و به ترتیب.
   * @param send (item) ⇒ Promise<پیامِ سرور>
   * @param hooks.onSent(item, message, blobKeys) / hooks.onChange()
   */
  function drain(queue, convId, send, hooks) {
    var h = hooks || {};
    function step() {
      var item = queue.next(convId);
      if (!item) return Promise.resolve();
      queue.sending(item);
      if (h.onChange) h.onChange();
      var p;
      try { p = Promise.resolve(send(item)); } catch (e) { p = Promise.reject(e); }
      return p.then(function (message) {
        var keys = queue.done(item.clientId);
        if (h.onSent) h.onSent(item, message, keys);
        if (h.onChange) h.onChange();
        return step();
      }, function (err) {
        queue.failed(item, err);
        if (h.onChange) h.onChange();
        //  خطای قطعی صفِ پشتِ سر را نگه نمی‌دارد؛ گذرا/هویت نگه می‌دارد (ترتیب)
        return item.state === 'failed' ? step() : undefined;
      });
    }
    return step();
  }

  // ════════════════════════════════════════════════════════════════════
  //  گفت‌وگو
  // ════════════════════════════════════════════════════════════════════

  function Thread() {
    this.cursor = 0;
    this.byId = {};
    this.conversation = null;
  }

  /** پاسخِ `GET …/messages?after=` را روی نما می‌نشاند. */
  Thread.prototype.apply = function (res) {
    var r = res || {};
    var changed = false;
    var self = this;
    (r.messages || []).forEach(function (m) {
      if (!m || !m.id) return;
      var old = self.byId[m.id];
      if (!old || old.rev !== m.rev) { self.byId[m.id] = m; changed = true; }
    });
    if (typeof r.cursor === 'number' && r.cursor > this.cursor) this.cursor = r.cursor;
    if (r.conversation) {
      //  رسید، بلاک، باز/بسته… هر تغییرِ گفت‌وگو نما را تازه می‌کند (long-poll با همین بیدار می‌شود)
      var before = JSON.stringify(this.conversation);
      this.conversation = r.conversation;
      if (before !== JSON.stringify(r.conversation)) changed = true;
    }
    return { changed: changed, hasMore: !!r.hasMore };
  };

  /** پیام‌های دیدنی به ترتیبِ seq («حذف برای من» دیده نمی‌شود). */
  Thread.prototype.list = function () {
    var byId = this.byId;
    return Object.keys(byId).map(function (k) { return byId[k]; })
      .filter(function (m) { return !m.hidden; })
      .sort(function (a, b) { return a.seq - b.seq; });
  };

  Thread.prototype.maxSeq = function () {
    var max = 0;
    for (var k in this.byId) if (this.byId[k].seq > max) max = this.byId[k].seq;
    return max;
  };

  /**
   * رسیدی که باید اعلام شود، یا null. «رسید» = هر چه به دستگاه آمد؛
   * «خوانده» فقط وقتی گفت‌وگو واقعاً جلوی چشم است.
   */
  Thread.prototype.receiptNeed = function (visible) {
    var c = this.conversation;
    if (!c || !c.receipts) return null;
    var top = Math.max(this.maxSeq(), 0);
    var d = top > (c.receipts.myDeliveredSeq || 0) ? top : 0;
    var r = visible && top > (c.receipts.myReadSeq || 0) ? top : 0;
    if (!d && !r) return null;
    return { deliveredSeq: Math.max(d, r, c.receipts.myDeliveredSeq || 0), readSeq: r || (c.receipts.myReadSeq || 0) };
  };

  /** وضعیتِ پیامِ خودم از روی رسیدهای واقعیِ طرف (نه حدس). */
  function statusOf(m, receipts) {
    if (!m || m.deleted || m.hidden) return '';
    if (!receipts) return m.status || 'sent';
    if (m.seq <= (receipts.peerReadSeq || 0)) return 'read';
    if (m.seq <= (receipts.peerDeliveredSeq || 0)) return 'delivered';
    return 'sent';
  }

  // ════════════════════════════════════════════════════════════════════
  //  رسانه (مرورگر)
  // ════════════════════════════════════════════════════════════════════

  /** ابعادِ کوچک‌شده با همان نسبت؛ هرگز بزرگ‌تر از اصل. */
  function fit(w, h, max) {
    var W = Math.max(1, Math.round(w || 1));
    var H = Math.max(1, Math.round(h || 1));
    var s = Math.min(1, max / Math.max(W, H));
    return { w: Math.max(1, Math.round(W * s)), h: Math.max(1, Math.round(H * s)) };
  }

  /** نوعِ ضبطِ صدا که این مرورگر واقعاً دارد — یا '' (دکمه پنهان می‌ماند). */
  function voiceType(MR) {
    var R = MR || (typeof MediaRecorder !== 'undefined' ? MediaRecorder : null);
    if (!R || typeof R.isTypeSupported !== 'function') return '';
    if (typeof navigator === 'undefined' || !navigator.mediaDevices || !navigator.mediaDevices.getUserMedia) return '';
    for (var i = 0; i < VOICE_TYPES.length; i++) if (R.isTypeSupported(VOICE_TYPES[i])) return VOICE_TYPES[i];
    return '';
  }

  function canvasBlob(canvas, q) {
    return new Promise(function (resolve, reject) {
      canvas.toBlob(function (b) { if (b) resolve(b); else reject(new Error('عکس ساخته نشد')); }, 'image/jpeg', q);
    });
  }

  function draw(bitmap, w, h) {
    var c = document.createElement('canvas');
    c.width = w; c.height = h;
    var g = c.getContext('2d');
    //  JPEG شفافیت ندارد: زمینهٔ سفید، نه سیاه
    g.fillStyle = '#fff';
    g.fillRect(0, 0, w, h);
    g.imageSmoothingQuality = 'high';
    g.drawImage(bitmap, 0, 0, w, h);
    return c;
  }

  /** در سقف جا بگیرد: کیفیت پایین‌تر، و اگر نشد ابعادِ کوچک‌تر. */
  function encodeWithin(bitmap, px, q, cap) {
    var size = fit(bitmap.width, bitmap.height, px);
    function tryAt(dim, quality) {
      return canvasBlob(draw(bitmap, dim.w, dim.h), quality).then(function (b) {
        if (!cap || b.size <= cap) return { blob: b, w: dim.w, h: dim.h };
        if (quality > IMAGE.minQ + 0.01) return tryAt(dim, Math.max(IMAGE.minQ, quality - 0.15));
        if (Math.max(dim.w, dim.h) > 160) return tryAt(fit(dim.w, dim.h, Math.round(Math.max(dim.w, dim.h) * 0.75)), q);
        throw new Error('عکس در سقفِ مجاز جا نشد');
      });
    }
    return tryAt(size, q);
  }

  /**
   * عکسِ انتخاب‌شده ⇐ نسخهٔ نمایش (≤ ۱۶۰۰px) و پیش‌نمایش (≤ ۳۲۰px)، هر دو
   * JPEG ِ تازه از روی پیکسل‌ها: EXIF (مکان، دستگاه، زمان) دیگر وجود ندارد و
   * جهتِ عکس پیش از رسم اعمال می‌شود (`imageOrientation: 'from-image'`).
   */
  function prepareImage(file, limits) {
    var L = limits || {};
    if (typeof createImageBitmap !== 'function') return Promise.reject(new Error('این مرورگر عکس را آماده نمی‌کند'));
    return createImageBitmap(file, { imageOrientation: 'from-image' }).catch(function () {
      return createImageBitmap(file);
    }).then(function (bmp) {
      return encodeWithin(bmp, IMAGE.mainPx, IMAGE.mainQ, L.imageMax).then(function (main) {
        return encodeWithin(bmp, IMAGE.thumbPx, IMAGE.thumbQ, L.thumbMax).then(function (thumb) {
          if (bmp.close) bmp.close();
          return { main: main.blob, thumb: thumb.blob, width: main.w, height: main.h };
        });
      });
    });
  }

  // ════════════════════════════════════════════════════════════════════
  //  انبارِ بایت‌های صف (IndexedDB؛ بی آن حافظهٔ همین برگه)
  // ════════════════════════════════════════════════════════════════════

  function BlobStore(name) {
    this.name = name || 'tohid-chat-outbox';
    this.mem = {};
    this.dbp = null;
  }
  BlobStore.prototype.db = function () {
    var self = this;
    if (this.dbp) return this.dbp;
    this.dbp = new Promise(function (resolve) {
      try {
        if (typeof indexedDB === 'undefined') return resolve(null);
        var req = indexedDB.open(self.name, 1);
        req.onupgradeneeded = function () { req.result.createObjectStore('b'); };
        req.onsuccess = function () { resolve(req.result); };
        req.onerror = function () { resolve(null); };
        req.onblocked = function () { resolve(null); };
      } catch (e) { resolve(null); }
    });
    return this.dbp;
  };
  BlobStore.prototype._op = function (mode, fn) {
    var self = this;
    return this.db().then(function (db) {
      if (!db) return fn(null);
      return new Promise(function (resolve) {
        try {
          var tx = db.transaction('b', mode);
          var out = fn(tx.objectStore('b'));
          tx.oncomplete = function () { resolve(out && out.result !== undefined ? out.result : out); };
          tx.onerror = tx.onabort = function () { resolve(fn(null)); };
        } catch (e) { resolve(fn(null)); }
      });
    });
  };
  BlobStore.prototype.put = function (key, blob) {
    var self = this;
    self.mem[key] = blob;
    return this._op('readwrite', function (st) { if (st) st.put(blob, key); return true; });
  };
  BlobStore.prototype.get = function (key) {
    var self = this;
    if (self.mem[key]) return Promise.resolve(self.mem[key]);
    return this._op('readonly', function (st) { return st ? st.get(key) : (self.mem[key] || null); })
      .then(function (v) { return v || null; });
  };
  BlobStore.prototype.del = function (key) {
    delete this.mem[key];
    return this._op('readwrite', function (st) { if (st) st.delete(key); return true; });
  };

  return {
    BACKOFF: BACKOFF, IMAGE: IMAGE, VOICE_MAX_MS: VOICE_MAX_MS, VOICE_TYPES: VOICE_TYPES, LABEL: LABEL,
    CLIENT_ID_RE: CLIENT_ID_RE,
    backoffMs: backoffMs, newClientId: newClientId, classify: classify,
    Queue: Queue, drain: drain, Thread: Thread, statusOf: statusOf,
    fit: fit, voiceType: voiceType, prepareImage: prepareImage, BlobStore: BlobStore,
  };
});
