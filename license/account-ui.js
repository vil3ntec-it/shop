/* ==========================================================
   توحید | ورود با کد، «اشتراکِ من»، اعلان‌ها و قفلِ نرم — سمتِ وب
   ----------------------------------------------------------
   ⚠️ همه‌ی این صفحه‌ها این‌جا **ساخته** می‌شوند، نه در `index.html`.
   دلیلش سلیقه نیست: آن فایل سیزده هزار خط است و هر دست بردن در
   نشانه‌گذاری‌اش خطرِ شکستنِ چیزی بی‌ربط دارد. این‌جا DOM از صفر ساخته
   می‌شود و اگر این فایل بالا نیاید، برنامه دقیقاً مثلِ دیروز کار
   می‌کند.

   ⛔ **هیچ عددِ قیمتی این‌جا نوشته نمی‌شود.** قیمت‌ها فقط از سرور
      می‌آیند — همان قاعدهٔ همیشگیِ مخزن.
   ⛔ **کادرِ نشانیِ سرور ساخته نمی‌شود.** پرامپتِ بندِ ۲۱ آن را خواسته
      بود؛ قاعدهٔ صاحبِ مخزن («نشانی در کد قفل است») بالاتر است.
   ⛔ **قفلِ نرم هیچ داده‌ای را پاک نمی‌کند** و خروجی و چاپ را هم
      نمی‌بندد.
   ========================================================== */
(function () {
  'use strict';

  var acc = window.TohidAccount;
  var sync = window.TohidSync;
  var core = window.TohidSyncCore;
  if (!acc) return;

  var FA_DIGIT = '۰۱۲۳۴۵۶۷۸۹';
  function fa(n) {
    return String(n == null ? '' : n).replace(/[0-9]/g, function (d) { return FA_DIGIT[+d]; });
  }
  function toast(msg, kind) {
    if (typeof window.showToast === 'function') { window.showToast(msg, kind || 'success'); return; }
    //  نسخه‌ای که `showToast` ندارد نباید ساکت بماند
    try { console.log('[توحید]', msg); } catch (e) { /* بی‌خیال */ }
  }
  function el(tag, cls, html) {
    var n = document.createElement(tag);
    if (cls) n.className = cls;
    if (html !== undefined) n.innerHTML = html;
    return n;
  }
  function esc(s) {
    return String(s == null ? '' : s)
      .replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
      .replace(/"/g, '&quot;');
  }
  function clock(ms) {
    if (!ms) return '—';
    try {
      return new Date(Number(ms)).toLocaleString('fa-AF', {
        hour: '2-digit', minute: '2-digit', month: 'short', day: 'numeric',
      });
    } catch (e) { return fa(new Date(Number(ms)).toISOString().slice(0, 16).replace('T', ' ')); }
  }
  function day(sec) {
    if (!sec) return '—';
    try { return new Date(Number(sec) * 1000).toLocaleDateString('fa-AF'); }
    catch (e) { return fa(new Date(Number(sec) * 1000).toISOString().slice(0, 10)); }
  }

  /* ==========================================================
     پوستهٔ پنجره — یکی برای همه
     ========================================================== */
  function sheet(id, title) {
    var old = document.getElementById(id);
    if (old) old.remove();
    var scrim = el('div', 'acct-scrim');
    scrim.id = id;
    var box = el('div', 'acct-sheet');
    var head = el('div', 'acct-head');
    head.appendChild(el('div', 'acct-title', esc(title)));
    var x = el('button', 'acct-x', '×');
    x.type = 'button';
    x.setAttribute('aria-label', 'بستن');
    head.appendChild(x);
    var body = el('div', 'acct-body');
    box.appendChild(head);
    box.appendChild(body);
    scrim.appendChild(box);
    document.body.appendChild(scrim);

    function close() { scrim.classList.remove('open'); }
    x.addEventListener('click', close);
    scrim.addEventListener('click', function (e) { if (e.target === scrim) close(); });
    document.addEventListener('keydown', function (e) {
      if (e.key === 'Escape' && scrim.classList.contains('open')) close();
    });
    return { scrim: scrim, body: body, open: function () { scrim.classList.add('open'); }, close: close };
  }

  /* ==========================================================
     ۱) ورود با کدِ شش‌رقمی
     ----------------------------------------------------------
     دو پله: ایمیل، بعد شش خانه. بندِ ۲۱٫۴ پرامپت:
     پرشِ خودکار · Paste · ارقامِ فارسی و انگلیسی · ارسالِ خودکار پس
     از رقمِ ششم · شمارشِ معکوسِ شصت ثانیه.
     ========================================================== */
  var login = null;

  function buildLogin() {
    if (login) return login;
    var s = sheet('acct-login', 'ورود با ایمیل');
    s.body.innerHTML =
      '<div class="acct-sub" id="lg-hint">'
      + 'ایمیل‌تان را بنویسید؛ یک کدِ شش‌رقمی برایتان می‌فرستیم. '
      + 'همین ایمیل روی گوشی و روی سایت یک حساب است.'
      + '</div>'
      + '<div id="lg-step-email" style="margin-top:14px">'
      + '  <input id="lg-email" class="acct-field" type="email" inputmode="email" '
      + '         autocomplete="email" placeholder="you@example.com" style="text-align:left">'
      + '  <div class="acct-btns"><button type="button" class="acct-btn" id="lg-send">فرستادنِ کد</button></div>'
      + '</div>'
      + '<div id="lg-step-code" hidden style="margin-top:14px">'
      + '  <div class="acct-sub" id="lg-to"></div>'
      + '  <div class="code-boxes" id="lg-boxes"></div>'
      + '  <div class="code-timer" id="lg-timer"></div>'
      + '  <div style="text-align:center"><button type="button" class="code-resend" id="lg-resend">فرستادنِ دوبارهٔ کد</button></div>'
      + '  <div class="acct-btns">'
      + '    <button type="button" class="acct-btn" id="lg-verify">ورود</button>'
      + '    <button type="button" class="acct-btn ghost" id="lg-back">ایمیلِ دیگر</button>'
      + '  </div>'
      + '</div>'
      + '<div class="acct-msg info" id="lg-msg"></div>';

    var boxes = [];
    var wrap = s.body.querySelector('#lg-boxes');
    for (var i = 0; i < 6; i++) {
      var b = el('input', 'code-box');
      b.type = 'text';
      b.inputMode = 'numeric';
      b.autocomplete = i === 0 ? 'one-time-code' : 'off';
      b.maxLength = 1;
      b.setAttribute('aria-label', 'رقم ' + fa(i + 1));
      wrap.appendChild(b);
      boxes.push(b);
    }

    var state = { requestId: '', email: '', resendAt: 0, timer: null, busy: false };

    function msg(text, kind) {
      var m = s.body.querySelector('#lg-msg');
      m.textContent = text || '';
      m.className = 'acct-msg ' + (kind || 'info');
    }
    function codeOf() {
      var out = '';
      for (var i = 0; i < 6; i++) out += boxes[i].value || '';
      return out;
    }
    function paint() {
      for (var i = 0; i < 6; i++) boxes[i].classList.toggle('filled', !!boxes[i].value);
    }

    /** چند رقم را از یک جا (Paste یا صفحه‌کلیدِ فارسی) پخش می‌کند. */
    function spread(text, from) {
      var digits = acc.onlyDigits(text);
      if (!digits) return;
      var at = from;
      for (var i = 0; i < digits.length && at < 6; i++, at++) boxes[at].value = digits[i];
      paint();
      var next = Math.min(5, at);
      boxes[next].focus();
      if (codeOf().length === 6) submit();
    }

    boxes.forEach(function (b, i) {
      b.addEventListener('input', function () {
        var raw = b.value;
        b.value = '';
        spread(raw, i);
      });
      b.addEventListener('keydown', function (e) {
        if (e.key === 'Backspace' && !b.value && i > 0) { boxes[i - 1].value = ''; boxes[i - 1].focus(); paint(); }
        else if (e.key === 'ArrowLeft' && i > 0) boxes[i - 1].focus();
        else if (e.key === 'ArrowRight' && i < 5) boxes[i + 1].focus();
        else if (e.key === 'Enter') submit();
      });
      b.addEventListener('paste', function (e) {
        e.preventDefault();
        spread((e.clipboardData || window.clipboardData).getData('text'), i);
      });
      b.addEventListener('focus', function () { b.select(); });
    });

    function tick() {
      var left = Math.max(0, Math.ceil((state.resendAt - Date.now()) / 1000));
      var t = s.body.querySelector('#lg-timer');
      var r = s.body.querySelector('#lg-resend');
      if (left > 0) {
        t.textContent = 'تا فرستادنِ دوبارهٔ کد: ' + fa(left) + ' ثانیه';
        r.disabled = true;
      } else {
        t.textContent = 'کد نیامد؟ پوشهٔ Spam را هم ببینید.';
        r.disabled = false;
        if (state.timer) { clearInterval(state.timer); state.timer = null; }
      }
    }

    async function send(email) {
      if (state.busy) return;
      state.busy = true;
      s.body.querySelector('#lg-send').disabled = true;
      msg('در حالِ فرستادن…', 'info');
      try {
        var r = await acc.requestCode(email);
        state.requestId = r.requestId;
        state.email = email;
        state.resendAt = Date.now() + (r.resendAfter || acc.RESEND_S) * 1000;
        s.body.querySelector('#lg-step-email').hidden = true;
        s.body.querySelector('#lg-step-code').hidden = false;
        s.body.querySelector('#lg-to').textContent =
          'کد به ' + (r.maskedEmail || email) + ' فرستاده شد. شش رقم را بنویسید.';
        boxes.forEach(function (b) { b.value = ''; });
        paint();
        boxes[0].focus();
        if (state.timer) clearInterval(state.timer);
        state.timer = setInterval(tick, 1000);
        tick();
        msg('', 'info');
        watchDelivery();
      } catch (e) {
        msg(deliveryText(e), 'err');
      } finally {
        state.busy = false;
        s.body.querySelector('#lg-send').disabled = false;
      }
    }

    /** ۴۲۹ و ۴۲۳ شمارشِ معکوس دارند، نه یک «خطا» ی بی‌راه. */
    function deliveryText(e) {
      if (!e) return 'خطای نامشخص';
      if (e.status === 429 || e.code === 'RATE_LIMITED') {
        return 'تلاشِ زیاد. ' + (e.retryAfter ? fa(e.retryAfter) + ' ثانیه صبر کنید.' : 'کمی بعد دوباره.');
      }
      if (e.status === 423 || e.code === 'LOCKED') {
        return 'پنج کدِ اشتباه. ' + (e.retryAfter ? fa(Math.ceil(e.retryAfter / 60)) + ' دقیقه' : 'پانزده دقیقه') + ' بسته است.';
      }
      if (e.code === 'CODE_WRONG' && e.attemptsLeft !== null && e.attemptsLeft !== undefined) {
        return (e.message || 'کد درست نیست') + ' — ' + fa(e.attemptsLeft) + ' تلاشِ دیگر';
      }
      return e.message || 'خطای نامشخص';
    }

    /** «ایمیل رفت یا نرفت» — بندِ ۲ قراردادِ ورود. */
    async function watchDelivery() {
      if (!state.requestId) return;
      for (var i = 0; i < 6; i++) {
        await new Promise(function (r) { setTimeout(r, 2500); });
        if (!state.requestId) return;
        var st = null;
        try { st = await acc.requestStatus(state.requestId); } catch (e) { return; }
        if (st.state === 'failed') {
          msg('ایمیل فرستاده نشد (' + (st.reason || 'نامشخص') + '). دوباره تلاش کنید.', 'err');
          return;
        }
        if (st.state === 'sent') { msg('ایمیل رفت.', 'ok'); return; }
      }
    }

    async function submit() {
      if (state.busy) return;
      var code = codeOf();
      if (code.length !== 6) { msg('شش رقم را کامل بنویسید.', 'err'); return; }
      state.busy = true;
      s.body.querySelector('#lg-verify').disabled = true;
      s.body.querySelector('#lg-boxes').classList.add('locked');
      msg('در حالِ بررسی…', 'info');
      try {
        await acc.verify(state.requestId, code);
        state.requestId = '';
        if (state.timer) { clearInterval(state.timer); state.timer = null; }
        s.close();
        toast('وارد شدید');
        acc.emit('signed-in', {});
        if (sync && sync.onSignedIn) sync.onSignedIn();
        refreshHeader();
      } catch (e) {
        msg(deliveryText(e), 'err');
        boxes.forEach(function (b) { b.value = ''; });
        paint();
        boxes[0].focus();
      } finally {
        state.busy = false;
        s.body.querySelector('#lg-verify').disabled = false;
        s.body.querySelector('#lg-boxes').classList.remove('locked');
      }
    }

    s.body.querySelector('#lg-send').addEventListener('click', function () {
      var v = String(s.body.querySelector('#lg-email').value || '').trim();
      if (!/^[^@\s]+@[^@\s.]+\.[^@\s]{2,}$/.test(v)) { msg('ایمیل درست نیست.', 'err'); return; }
      send(v);
    });
    s.body.querySelector('#lg-email').addEventListener('keydown', function (e) {
      if (e.key === 'Enter') s.body.querySelector('#lg-send').click();
    });
    s.body.querySelector('#lg-verify').addEventListener('click', submit);
    s.body.querySelector('#lg-resend').addEventListener('click', function () {
      if (state.email) send(state.email);
    });
    s.body.querySelector('#lg-back').addEventListener('click', function () {
      state.requestId = '';
      if (state.timer) { clearInterval(state.timer); state.timer = null; }
      s.body.querySelector('#lg-step-code').hidden = true;
      s.body.querySelector('#lg-step-email').hidden = false;
      msg('', 'info');
    });

    login = {
      open: function () {
        s.open();
        setTimeout(function () { s.body.querySelector('#lg-email').focus(); }, 60);
      },
      close: s.close,
      //  فقط برای سنجهٔ مرورگری: خانه‌ها و پله‌ها از بیرون دیده شوند
      _boxes: boxes,
      _submit: submit,
    };
    return login;
  }

  /* ==========================================================
     ۲) «اشتراکِ من»
     ----------------------------------------------------------
     همه از تپشِ کش‌شده می‌آید، پس آفلاین هم چیزی برای نشان دادن هست.
     ⛔ رنگ و روزِ مانده از **سرور** می‌آید (`subscriptionView` در
     `routes/portal.js`)؛ قاعدهٔ جدا این‌جا نوشته نمی‌شود، وگرنه روزی
     مشتری در برنامه سبز می‌بیند و در پورتال سرخ.
     ========================================================== */
  var planSheet = null;

  function buildPlan() {
    if (planSheet) return planSheet;
    var s = sheet('acct-plan', 'اشتراکِ من');
    s.body.innerHTML = '<div id="pl-body"><div class="acct-empty">در حالِ خواندن…</div></div>';
    planSheet = {
      open: function () { s.open(); render(); refresh(); },
      close: s.close,
      render: render,
    };

    function render() {
      var cache = acc.cachedHeartbeat() || {};
      var sub = cache.subscription || null;
      var host = s.body.querySelector('#pl-body');
      if (!acc.isSignedIn()) {
        host.innerHTML = '<div class="acct-empty">برای دیدنِ اشتراک، اول با ایمیل وارد شوید.</div>';
        return;
      }
      if (!sub) {
        host.innerHTML = '<div class="acct-empty">هنوز چیزی از سرور نیامده. اینترنت که وصل شد، همین‌جا پر می‌شود.</div>';
        return;
      }
      var color = sub.color || 'grey';
      var days = sub.permanent ? null : Number(sub.daysLeft || 0);
      var pct = sub.permanent ? 100 : Math.max(0, Math.min(100, Number(sub.progress || 0)));
      //  ⚠️ «≤ ۷ روز» همان زرد است، فقط پررنگ‌تر — همان چیزی که خودِ
      //  سرور در توضیحِ `subscriptionView` نوشته
      var soon = !sub.permanent && sub.active && days !== null && days <= 7;

      var html = ''
        + '<div class="acct-row"><span class="acct-k">پلن</span>'
        + '<span class="plan-chip">' + esc(sub.plan || '—') + '</span></div>'
        + '<div class="plan-days" data-color="' + esc(color) + '">'
        + esc(sub.label || '') + (soon ? ' — رو به پایان' : '') + '</div>'
        + '<div class="plan-bar" data-color="' + esc(color) + '"><i style="width:' + pct + '%"></i></div>'
        + '<div class="acct-row"><span class="acct-k">آغاز</span><b>' + day(sub.startsAt ? Math.floor(sub.startsAt / 1000) : 0) + '</b></div>'
        + '<div class="acct-row"><span class="acct-k">پایان</span><b>'
        + (sub.permanent ? 'دائمی ✓' : day(sub.endsAt ? Math.floor(sub.endsAt / 1000) : 0)) + '</b></div>'
        + '<div class="acct-row"><span class="acct-k">وضعیت</span><b>' + esc(sub.status || '') + '</b></div>';

      html += '<div class="acct-section-title">دستگاه‌های فعال</div><div id="pl-devices"><div class="acct-empty">…</div></div>';
      html += '<div class="acct-section-title">پرداخت‌ها</div><div id="pl-payments"><div class="acct-empty">…</div></div>';
      html += '<div class="acct-section-title">پیام‌های مدیر</div><div id="pl-notices"><div class="acct-empty">…</div></div>';
      html += '<div class="acct-btns"><button type="button" class="acct-btn ghost" id="pl-refresh">تازه کردن از سرور</button></div>';
      host.innerHTML = html;
      host.querySelector('#pl-refresh').addEventListener('click', function () { refresh(true); });
      fillLists();
    }

    async function refresh(loud) {
      if (!acc.isSignedIn()) return;
      try {
        await acc.heartbeat();
        render();
        if (loud) toast('تازه شد');
      } catch (e) {
        if (loud) toast(e.message || 'به سرور نرسیدیم', 'danger');
      }
    }

    async function fillLists() {
      var dev = s.body.querySelector('#pl-devices');
      var pay = s.body.querySelector('#pl-payments');
      var not = s.body.querySelector('#pl-notices');
      if (!dev) return;
      try {
        var r = await acc.authFetch('/api/me/devices');
        var list = (r && r.devices) || [];
        dev.innerHTML = list.length ? list.map(function (d) {
          return '<div class="acct-row"><span>' + esc(d.name || d.device_uid || 'دستگاه') + '</span>'
            + '<span class="acct-k">' + clock(Number(d.last_seen_at || 0)) + '</span></div>';
        }).join('') : '<div class="acct-empty">دستگاهی ثبت نشده.</div>';
      } catch (e) { dev.innerHTML = '<div class="acct-empty">نشد خواند.</div>'; }

      try {
        var p = await acc.authFetch('/api/me/payments');
        var pl = (p && p.payments) || [];
        //  ⛔ هیچ عددِ قیمتی این‌جا ساخته نمی‌شود؛ هرچه هست از سرور آمده
        pay.innerHTML = pl.length ? pl.map(function (x) {
          return '<div class="acct-row"><span>' + esc(x.planTitle || x.plan || '—') + '</span>'
            + '<b>' + esc(x.amountLabel || (x.amount != null ? fa(x.amount) : '—')) + '</b></div>';
        }).join('') : '<div class="acct-empty">پرداختی ثبت نشده.</div>';
      } catch (e) { pay.innerHTML = '<div class="acct-empty">نشد خواند.</div>'; }

      try {
        var n = await acc.authFetch('/api/me/notices');
        var nl = (n && n.notices) || [];
        not.innerHTML = nl.length ? nl.slice(0, 10).map(function (x) {
          return '<div class="acct-row"><span>' + esc(x.title || '') + '</span>'
            + '<span class="acct-k">' + clock(Number(x.createdAt || x.created_at || 0)) + '</span></div>';
        }).join('') : '<div class="acct-empty">پیامی نیست.</div>';
      } catch (e) { not.innerHTML = '<div class="acct-empty">نشد خواند.</div>'; }
    }

    return planSheet;
  }

  /* ==========================================================
     ۳) پنجرهٔ «حالِ همگام‌سازی» — تنظیمات، بندِ ۲۱٫۱۱
     ========================================================== */
  var syncSheet = null;

  function buildSync() {
    if (syncSheet) return syncSheet;
    var s = sheet('acct-sync', 'همگام‌سازی');
    s.body.innerHTML = '<div id="sy-body"></div>';

    function render() {
      var host = s.body.querySelector('#sy-body');
      if (!sync) { host.innerHTML = '<div class="acct-empty">لایهٔ همگام‌سازی بالا نیامده.</div>'; return; }
      var st = sync.status();
      var label = (core && core.DOT_LABEL[st.dot]) || '';
      var reports = errorReportsOn();
      var html = ''
        + '<div class="acct-row"><span class="acct-k">حال</span>'
        + '<b><span class="sync-dot" style="display:inline-block;width:9px;height:9px;border-radius:50%;margin-inline-end:6px;background:'
        + dotColor(st.dot) + '"></span>' + esc(label) + '</b></div>'
        + '<div class="acct-row"><span class="acct-k">آخرین همگام‌سازیِ موفق</span><b>' + clock(st.lastOkAt) + '</b></div>'
        + '<div class="acct-row"><span class="acct-k">در صف</span><b>' + fa(st.queued) + ' تغییر</b></div>'
        + '<div class="acct-row"><span class="acct-k">نشانگرِ سرور (cursor)</span><b>' + fa(st.cursor) + '</b></div>'
        + '<div class="acct-row"><span class="acct-k">نسخهٔ Schema</span><b>'
        + fa(st.schemaVersion) + (st.serverSchema ? ' / سرور ' + fa(st.serverSchema) : '') + '</b></div>'
        + '<div class="acct-row"><span class="acct-k">سوکتِ زنده</span><b>' + (st.live ? 'وصل' : 'قطع') + '</b></div>'
        + (st.lastError
          ? '<div class="acct-row"><span class="acct-k">آخرین خطا</span><b style="color:var(--danger)">' + esc(st.lastError) + '</b></div>'
          : '')
        + (st.holding
          ? '<div class="acct-banner warn show" style="margin:10px 0"><div class="acct-banner-body">'
          + 'سرور از برنامه عقب‌تر است. تغییرها روی همین دستگاه نگه داشته می‌شوند و هیچ‌کدام گم نمی‌شود؛ '
          + 'با به‌روز شدنِ سرور خودشان می‌روند.</div></div>' : '')
        + (st.upgradeAvailable
          ? '<div class="acct-banner show" style="margin:10px 0"><div class="acct-banner-body">نسخهٔ تازهٔ برنامه آماده است.</div></div>' : '')
        + (st.dropped
          ? '<div class="acct-row"><span class="acct-k">ردشده توسط سرور</span><b>' + fa(st.dropped) + '</b></div>' : '')
        + '<div class="acct-section-title">تنظیمات</div>'
        + '<label class="acct-row" style="cursor:pointer"><span>فرستادنِ گزارشِ خطا به سرور</span>'
        + '<input type="checkbox" id="sy-reports"' + (reports ? ' checked' : '') + '></label>'
        + '<div class="acct-btns">'
        + '  <button type="button" class="acct-btn" id="sy-now">همین حالا همگام کن</button>'
        + '  <button type="button" class="acct-btn ghost" id="sy-restore">بازیابی از سرور (Snapshot)</button>'
        + '  <button type="button" class="acct-btn ghost" id="sy-backup">پشتیبانِ رمزشدهٔ محلی</button>'
        + '</div>'
        + '<div class="acct-msg info" id="sy-msg"></div>';
      host.innerHTML = html;

      host.querySelector('#sy-reports').addEventListener('change', function (e) {
        setErrorReports(!!e.target.checked);
        toast(e.target.checked ? 'گزارشِ خطا روشن شد' : 'گزارشِ خطا خاموش شد');
      });
      host.querySelector('#sy-now').addEventListener('click', async function () {
        say('در حالِ همگام‌سازی…', 'info');
        await sync.sync();
        render();
      });
      host.querySelector('#sy-restore').addEventListener('click', async function () {
        if (!window.confirm('دفترِ روی این دستگاه با نسخهٔ سرور جایگزین می‌شود. ادامه؟')) return;
        say('در حالِ گرفتنِ نسخهٔ سرور…', 'info');
        try {
          var out = await sync.restoreFromServer();
          say(fa(out.rows) + ' ردیف از سرور آمد.', 'ok');
          toast('بازیابی شد');
        } catch (e) { say(e.message || 'نشد', 'err'); }
      });
      host.querySelector('#sy-backup').addEventListener('click', localBackup);

      function say(t, k) {
        var m = host.querySelector('#sy-msg');
        if (m) { m.textContent = t; m.className = 'acct-msg ' + (k || 'info'); }
      }
    }

    syncSheet = { open: function () { s.open(); render(); }, close: s.close, render: render };
    return syncSheet;
  }

  function dotColor(d) {
    return d === 'green' ? 'var(--success)'
      : d === 'yellow' ? 'var(--warning)'
        : d === 'red' ? 'var(--danger)' : 'var(--muted-2)';
  }

  /* ---------- گزارشِ خطا، با اجازهٔ کاربر (بندِ ۲۰.۸) ---------- */
  var REPORTS_KEY = 'tohid-error-reports-v1';
  function errorReportsOn() {
    try { return localStorage.getItem(REPORTS_KEY) !== '0'; } catch (e) { return true; }
  }
  function setErrorReports(on) {
    try { localStorage.setItem(REPORTS_KEY, on ? '1' : '0'); } catch (e) { /* حافظه بسته */ }
  }

  function reportError(message, stack, context) {
    if (!errorReportsOn() || !acc.configured()) return;
    var body = {
      message: String(message || '').slice(0, 1000),
      stack: String(stack || '').slice(0, 4000),
      version: acc.appVersion(),
      platform: 'web',
      context: Object.assign({ schema_version: core ? core.SCHEMA_VERSION : 0 }, context || {}),
    };
    //  ⚠️ بی توکن هم پذیرفته می‌شود؛ خطایی که پیش از ورود می‌افتد هم
    //  باید برسد. و هیچ چیزی از دفترِ مشتری در آن نیست.
    var fn = acc.isSignedIn() ? acc.authFetch : acc.call;
    fn('/api/errors', { method: 'POST', body: body }).catch(function () { });
  }

  /* ---------- پشتیبانِ رمزشدهٔ محلی (بندِ ۲۱٫۱۱) ---------- */
  async function localBackup() {
    var data = null;
    try { data = JSON.parse(localStorage.getItem('tohid-shop-data-v1') || 'null'); } catch (e) { data = null; }
    if (!data) { toast('دفتری برای پشتیبان نیست', 'danger'); return; }
    var pass = window.prompt('یک رمز برای این فایل بگذارید (بی این رمز باز نمی‌شود):', '');
    if (!pass) return;
    try {
      var enc = new TextEncoder();
      var salt = crypto.getRandomValues(new Uint8Array(16));
      var iv = crypto.getRandomValues(new Uint8Array(12));
      var km = await crypto.subtle.importKey('raw', enc.encode(pass), 'PBKDF2', false, ['deriveKey']);
      var key = await crypto.subtle.deriveKey(
        { name: 'PBKDF2', salt: salt, iterations: 210000, hash: 'SHA-256' },
        km, { name: 'AES-GCM', length: 256 }, false, ['encrypt']);
      var cipher = new Uint8Array(await crypto.subtle.encrypt(
        { name: 'AES-GCM', iv: iv }, key, enc.encode(JSON.stringify(data))));
      var out = new Uint8Array(salt.length + iv.length + cipher.length);
      out.set(salt, 0); out.set(iv, salt.length); out.set(cipher, salt.length + iv.length);
      var blob = new Blob([out], { type: 'application/octet-stream' });
      var a = document.createElement('a');
      a.href = URL.createObjectURL(blob);
      a.download = 'tohid-backup-' + new Date().toISOString().slice(0, 10) + '.tbk';
      a.click();
      setTimeout(function () { URL.revokeObjectURL(a.href); }, 5000);
      toast('پشتیبانِ رمزشده ساخته شد');
    } catch (e) {
      toast('رمزگذاری نشد: ' + (e.message || ''), 'danger');
    }
  }

  /* ==========================================================
     ۴) چراغِ همگام‌سازی در نوارِ بالا
     ========================================================== */
  var dotBtn = null;

  function mountDot() {
    var host = document.querySelector('.header .header-right');
    if (!host || dotBtn) return;
    dotBtn = el('button', 'sync-dot-btn', '<span class="sync-dot"></span><span class="sync-dot-text">همگام</span>');
    dotBtn.type = 'button';
    dotBtn.id = 'sync-dot-btn';
    dotBtn.setAttribute('aria-label', 'حالِ همگام‌سازی');
    dotBtn.addEventListener('click', function () { buildSync().open(); });
    host.insertBefore(dotBtn, host.firstChild);
    paintDot();
  }

  function paintDot() {
    if (!dotBtn || !sync || !core) return;
    var st = sync.status();
    dotBtn.setAttribute('data-dot', st.dot);
    dotBtn.title = core.DOT_LABEL[st.dot] + (st.queued ? ' — ' + fa(st.queued) + ' در صف' : '');
    var text = dotBtn.querySelector('.sync-dot-text');
    if (text) text.textContent = st.queued ? fa(st.queued) + ' در صف' : core.DOT_LABEL[st.dot];
  }

  /* ==========================================================
     ۵) بنرِ اعلان و قفلِ نرم
     ========================================================== */
  var banner = null;
  var DISMISS_KEY = 'tohid-banner-seen-v1';

  function mountBanner() {
    var main = document.querySelector('.main .content');
    if (!main || banner) return;
    banner = el('div', 'acct-banner');
    banner.id = 'acct-banner';
    banner.innerHTML = '<div class="acct-banner-body"></div><button type="button" class="acct-banner-x" aria-label="بستن">×</button>';
    banner.querySelector('.acct-banner-x').addEventListener('click', function () {
      banner.classList.remove('show');
      try { localStorage.setItem(DISMISS_KEY, banner.getAttribute('data-key') || ''); } catch (e) { /* بی‌خیال */ }
    });
    main.parentNode.insertBefore(banner, main);
  }

  function showBanner(key, html, kind) {
    if (!banner) return;
    var seen = '';
    try { seen = localStorage.getItem(DISMISS_KEY) || ''; } catch (e) { seen = ''; }
    if (seen === key) return;
    banner.setAttribute('data-key', key);
    banner.className = 'acct-banner show' + (kind ? ' ' + kind : '');
    banner.querySelector('.acct-banner-body').innerHTML = html;
  }
  function hideBanner() { if (banner) banner.classList.remove('show'); }

  /**
   *  قفلِ نرم — بندِ ۲۱٫۸.
   *
   *  ⛔ **هیچ داده‌ای پاک نمی‌شود** و خروجیِ اکسل/CSV و چاپ و پشتیبان
   *     همچنان کار می‌کنند. فقط دکمه‌های «نوشتن» بسته می‌شوند و
   *     می‌گویند چرا.
   *  ⚠️ این یک قفلِ **رابط کاربری** است، نه یک قفلِ داده: هر کسی که با
   *     ابزارِ توسعه‌دهندهٔ مرورگر کار بلد باشد دورش می‌زند. جای قفلِ
   *     واقعی سرور است. این را صریح می‌نویسیم تا کسی به آن تکیه نکند.
   */
  var WRITE_ID = /^btn-(add|bulk|bp|cat|exp-cat|pf-cat|qs-cat|wf-cat|wh-|new|del|edit|tx-submit|finalize|qs-finalize|supplier-add|acc-add|pick-debtor-new|clear-all-data|save-store-name|confirm-return|import-backup)/;
  var ALWAYS_OK = /^btn-(export|print|cloud-backup|force-refresh|apply-update|install-app|alerts|header|logout|open-account|manual-lookup|toggle-scanner|test-camera|stop-test-camera|retry)/;
  var softLocked = false;

  function setSoftLock(on) {
    softLocked = !!on;
    try { document.documentElement.setAttribute('data-soft-lock', on ? '1' : '0'); } catch (e) { /* بی‌خیال */ }
  }

  function wireSoftLock() {
    document.addEventListener('click', function (e) {
      if (!softLocked) return;
      var node = e.target && e.target.closest ? e.target.closest('button,[role="button"]') : null;
      if (!node) return;
      var id = node.id || '';
      if (ALWAYS_OK.test(id)) return;
      if (!WRITE_ID.test(id)) return;
      e.preventDefault();
      e.stopPropagation();
      toast('اشتراک تمام شده — برنامه فقط‌خواندنی است. داده‌هایتان سرِ جایشان‌اند.', 'danger');
      buildPlan().open();
    }, true);

    document.addEventListener('submit', function (e) {
      if (!softLocked) return;
      if (!e.target || !e.target.closest || !e.target.closest('.modal-scrim')) return;
      e.preventDefault();
      e.stopPropagation();
      toast('اشتراک تمام شده — برنامه فقط‌خواندنی است.', 'danger');
    }, true);
  }

  /** از روی تپشِ کش‌شده تصمیم می‌گیرد: بنرِ زرد، بنرِ سرخ، یا هیچ. */
  function applySubscription() {
    var cache = acc.cachedHeartbeat();
    var sub = cache && cache.subscription;
    if (!acc.isSignedIn() || !sub) { setSoftLock(false); return; }
    var days = sub.permanent ? null : Number(sub.daysLeft || 0);

    if (!sub.active && sub.status !== 'none') {
      setSoftLock(true);
      showBanner('expired:' + (sub.endsAt || 0),
        '<b>اشتراک تمام شده.</b> برنامه فقط‌خواندنی است — دفترتان دست‌نخورده سرِ جایش است و '
        + 'خروجی و چاپ هم کار می‌کند. <a href="#" id="acct-banner-plan">دیدنِ اشتراک</a>', 'bad');
    } else if (sub.active && days !== null && days <= 7) {
      setSoftLock(false);
      showBanner('soon:' + (sub.endsAt || 0),
        '<b>' + fa(days) + ' روز تا پایانِ اشتراک.</b> پس از آن برنامه فقط‌خواندنی می‌شود؛ '
        + 'هیچ داده‌ای پاک نمی‌شود. <a href="#" id="acct-banner-plan">دیدنِ اشتراک</a>', 'warn');
    } else {
      setSoftLock(false);
      hideBanner();
    }
    var link = document.getElementById('acct-banner-plan');
    if (link) {
      link.addEventListener('click', function (e) { e.preventDefault(); buildPlan().open(); });
    }
  }

  /* ==========================================================
     ۶) نوارِ بالا — دکمهٔ ورود / نامِ کاربر
     ========================================================== */
  function refreshHeader() {
    var label = document.getElementById('hdr-auth-label');
    var a = acc.account();
    if (label && a.accessToken) label.textContent = a.userLabel || 'حسابِ من';
    paintDot();
    applySubscription();
  }

  /* ==========================================================
     ۷) روشن کردن
     ========================================================== */
  function boot() {
    mountDot();
    mountBanner();
    wireSoftLock();

    if (sync) {
      sync.onChange(function () { paintDot(); });
      sync.start();
    }
    acc.on('heartbeat', function () { applySubscription(); if (planSheet) planSheet.render(); });
    acc.on('signed-out', function () { refreshHeader(); });

    //  گزارشِ خطای برنامه — با اجازهٔ کاربر، بی هیچ دادهٔ مشتری
    window.addEventListener('error', function (e) {
      reportError(e && e.message, e && e.error && e.error.stack, { where: 'window' });
    });
    window.addEventListener('unhandledrejection', function (e) {
      var r = e && e.reason;
      reportError(r && r.message ? r.message : String(r), r && r.stack, { where: 'promise' });
    });

    refreshHeader();
  }

  window.TohidAccountUI = {
    openLogin: function () { buildLogin().open(); },
    openPlan: function () { buildPlan().open(); },
    openSync: function () { buildSync().open(); },
    reportError: reportError,
    errorReportsOn: errorReportsOn,
    setErrorReports: setErrorReports,
    refreshHeader: refreshHeader,
    localBackup: localBackup,
    isSoftLocked: function () { return softLocked; },
    _applySubscription: applySubscription,
  };

  if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', boot);
  else boot();
})();
