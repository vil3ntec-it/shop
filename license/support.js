/*
 *  پشتیبانی — گفت‌وگو با کسی که برنامه را ساخته.
 *  ==========================================================
 *
 *  ── چرا این فایل هست ───────────────────────────────────────────────
 *  تا امروز اگر کسی مشکلی داشت، تنها راهش واتساپ بود: بیرون از برنامه،
 *  بی هیچ ردی که کنارِ پروندهٔ دکانش بماند. و برای کاربرِ آیفون — که
 *  فقط همین سایت را دارد — اصلاً راهی نبود.
 *
 *  ── بدونِ حساب هم کار می‌کند ───────────────────────────────────────
 *  همان کسی که همان اولِ کار گیر کرده («چرا ثبت‌نام نمی‌شود؟») بیشتر از
 *  همه به این نیاز دارد. گفت‌وگو به شناسهٔ همین مرورگر بسته می‌شود و هر
 *  وقت حساب ساخت، سرور خودش به حسابش می‌چسباندش — پس از اول توضیح
 *  نمی‌دهد.
 *
 *  ── چرا سبک است ────────────────────────────────────────────────────
 *  قرارِ صاحب مخزن: «وب نباید کُند شود». پس:
 *    • هیچ کتابخانه‌ای، هیچ فریمی، هیچ درخواستِ بیرونی
 *    • تا وقتی پنجره باز نشده، هیچ درخواستی به سرور نمی‌رود
 *    • وقتی باز است هر ده ثانیه فقط **پیام‌های تازه** خوانده می‌شوند،
 *      نه کلِ گفت‌وگو
 *    • وقتی بسته است، فقط با تپشِ بازدید — هر شش ساعت یک بار — معلوم
 *      می‌شود پیامی هست یا نه
 *    • `backdrop-filter` ندارد؛ همان قاعده‌ی سربرگ و نوارِ پایین
 *
 *  همان چیزی که برنامه‌ی اندروید دارد، روی همان سرور و همان گفت‌وگو.
 */
(function () {
  'use strict';

  const POLL_MS = 10000;
  const VISIT_EVERY_MS = 6 * 60 * 60 * 1000;
  const MAX_BODY = 4000;

  const $ = (sel, root) => (root || document).querySelector(sel);

  function serverUrl() {
    try {
      const cfg = window.TohidApiConfig;
      return cfg ? cfg.baseUrl() : '';
    } catch { return ''; }
  }

  /** شناسهٔ همین مرورگر — همان که license-client می‌سازد، تا یکی بماند. */
  function deviceUid() {
    try {
      if (window.TohidLicense && window.TohidLicense.getDeviceUid) {
        return window.TohidLicense.getDeviceUid();
      }
    } catch { /* هنوز بالا نیامده */ }
    return '';
  }

  /**
   * توکنِ نشست.
   *
   * عمداً `getApiKey` نیست: آن یکی «کلید حساب» است که فقط به کاربر
   * نشان داده می‌شود و در پیامِ واتساپ می‌رود — با آن، هر درخواستی
   * ۴۰۱ می‌گیرد.
   *
   * اگر هیچ توکنی نبود، خالی برمی‌گردد و درخواست بی‌سرآیند می‌رود —
   * که برای پشتیبانی درست است: مهمان هم باید بتواند بنویسد.
   */
  function token() {
    try {
      if (window.TohidLicense && window.TohidLicense.getAccessToken) {
        return window.TohidLicense.getAccessToken();
      }
      //  اگر نسخه‌ی قدیمیِ license-client روی مرورگر مانده باشد
      const raw = localStorage.getItem('tohid-license-v1');
      return raw ? (JSON.parse(raw).accessToken || '') : '';
    } catch { return ''; }
  }

  function myName() {
    try {
      return (window.TohidLicense && window.TohidLicense.userLabel)
        ? window.TohidLicense.userLabel() : '';
    } catch { return ''; }
  }

  function esc(s) {
    return String(s == null ? '' : s).replace(/[&<>"']/g, c => ({
      '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;',
    }[c]));
  }

  /** ساعتِ کوتاه — تاریخِ فارسی با رقمِ لاتین، مثلِ بقیهٔ برنامه */
  function when(ms) {
    if (!ms) return '';
    try {
      return new Date(ms).toLocaleString('fa-IR-u-nu-latn', {
        month: 'numeric', day: 'numeric', hour: '2-digit', minute: '2-digit',
      });
    } catch { return ''; }
  }

  async function call(method, path, body) {
    const base = serverUrl();
    if (!base) throw new Error('نشانی سرور تنظیم نشده است');
    const key = token();
    const res = await fetch(base + '/api/v1' + path, {
      method,
      headers: Object.assign(
        { 'Content-Type': 'application/json' },
        key ? { Authorization: 'Bearer ' + key } : {},
      ),
      body: body === undefined ? undefined : JSON.stringify(body),
    });
    const data = await res.json().catch(() => ({}));
    if (!res.ok) {
      throw new Error((data.error && data.error.message) || 'به سرور نرسیدیم');
    }
    return data;
  }

  /* ============================== وضعیت ============================== */

  const State = {
    open: false,
    messages: [],
    lastAt: 0,
    unread: 0,
    timer: null,
    greeting: '',
    booted: false,
  };

  /* ============================== ساختن ============================== */

  let root = null;

  function ensure() {
    if (root) return root;
    root = document.createElement('div');
    root.id = 'sup';
    root.innerHTML = `
      <button type="button" class="sup-fab" id="sup-fab" aria-label="پشتیبانی">
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.9"
             stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
          <path d="M21 11.5a8.4 8.4 0 0 1-9 8.4 9 9 0 0 1-3.9-.9L3 21l1.9-4.6A8.4 8.4 0 0 1 12 3a8.4 8.4 0 0 1 9 8.5z"/>
        </svg>
        <span class="sup-dot" id="sup-dot" hidden></span>
      </button>

      <section class="sup-panel" id="sup-panel" hidden aria-label="گفت‌وگو با پشتیبانی">
        <header class="sup-head">
          <div class="sup-head-txt">
            <b>پشتیبانی</b>
            <i>معمولاً همان روز جواب می‌گیرید</i>
          </div>
          <button type="button" class="sup-x" id="sup-close" aria-label="بستن">&times;</button>
        </header>
        <div class="sup-body" id="sup-body"></div>
        <form class="sup-foot" id="sup-form">
          <textarea id="sup-input" rows="1" placeholder="پیام شما…"
                    maxlength="${MAX_BODY}" aria-label="پیام شما"></textarea>
          <button type="submit" class="sup-send" id="sup-send" aria-label="فرستادن">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.9"
                 stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
              <path d="M4 12h13M12 5l7 7-7 7"/>
            </svg>
          </button>
        </form>
      </section>`;
    document.body.appendChild(root);

    $('#sup-fab', root).addEventListener('click', toggle);
    $('#sup-close', root).addEventListener('click', () => setOpen(false));

    const input = $('#sup-input', root);
    //  کادر با متن بلند می‌شود، ولی نه بی‌نهایت
    input.addEventListener('input', () => {
      input.style.height = 'auto';
      input.style.height = Math.min(input.scrollHeight, 110) + 'px';
    });
    //  Enter می‌فرستد، Shift+Enter خط تازه — همان چیزی که همه بلدند
    input.addEventListener('keydown', (e) => {
      if (e.key === 'Enter' && !e.shiftKey) {
        e.preventDefault();
        $('#sup-form', root).requestSubmit();
      }
    });

    $('#sup-form', root).addEventListener('submit', (e) => {
      e.preventDefault();
      send();
    });

    return root;
  }

  /* ============================== نمایش ============================== */

  function paint() {
    const box = $('#sup-body', root);
    if (!box) return;

    if (!State.messages.length) {
      box.innerHTML = `<div class="sup-empty">${
        esc(State.greeting || 'هر مشکلی یا سؤالی دارید همین‌جا بنویسید — پاسخ می‌دهیم.')
      }</div>`;
      return;
    }

    box.innerHTML = State.messages.map(m => {
      const mine = m.sender === 'user';
      const system = m.sender === 'system';
      const cls = mine ? 'sup-mine' : (system ? 'sup-sys' : 'sup-them');
      const who = system ? 'خبر از توحید' : (mine ? '' : (m.senderName || 'پشتیبانی'));
      return `<div class="sup-msg ${cls}">
        ${who ? `<b>${esc(who)}</b>` : ''}
        <p>${esc(m.body).replace(/\n/g, '<br>')}</p>
        <time>${esc(when(m.createdAt))}</time>
      </div>`;
    }).join('');
    box.scrollTop = box.scrollHeight;
  }

  function paintDot() {
    const dot = $('#sup-dot', root);
    if (!dot) return;
    dot.hidden = State.unread <= 0;
    dot.textContent = State.unread > 9 ? '۹+' : String(State.unread);
  }

  function note(text, bad) {
    const box = $('#sup-body', root);
    if (!box) return;
    const el = document.createElement('div');
    el.className = 'sup-note' + (bad ? ' sup-note-bad' : '');
    el.textContent = text;
    box.appendChild(el);
    box.scrollTop = box.scrollHeight;
  }

  /* ============================== کارها ============================== */

  async function load(after) {
    const uid = deviceUid();
    if (!uid) return;
    const q = '?app=shop&deviceUid=' + encodeURIComponent(uid) + (after ? '&after=' + after : '');
    const data = await call('GET', '/support/thread' + q);
    const fresh = data.messages || [];
    State.messages = after ? State.messages.concat(fresh) : fresh;
    if (fresh.length) State.lastAt = fresh[fresh.length - 1].createdAt;
    if (data.greeting) State.greeting = data.greeting;
    paint();
  }

  async function send() {
    const input = $('#sup-input', root);
    const body = input.value.trim();
    if (!body) return;
    const btn = $('#sup-send', root);
    btn.disabled = true;
    try {
      await call('POST', '/support/messages', {
        app: 'shop',
        deviceUid: deviceUid(),
        name: myName(),
        body,
      });
      input.value = '';
      input.style.height = 'auto';
      await load(State.lastAt);
    } catch (err) {
      note(err.message || 'پیام نرفت — دوباره امتحان کنید.', true);
    }
    btn.disabled = false;
    input.focus();
  }

  function startPolling() {
    stopPolling();
    State.timer = setInterval(() => {
      load(State.lastAt).catch(() => { /* نتِ لحظه‌ای — دفعهٔ بعد */ });
    }, POLL_MS);
  }

  function stopPolling() {
    if (State.timer) clearInterval(State.timer);
    State.timer = null;
  }

  async function setOpen(on) {
    ensure();
    State.open = on;
    $('#sup-panel', root).hidden = !on;
    root.classList.toggle('sup-on', on);

    if (!on) { stopPolling(); return; }

    try {
      await load(0);
      //  باز کردنِ پنجره یعنی «دیدم»
      await call('POST', '/support/read', { deviceUid: deviceUid() });
      State.unread = 0;
      paintDot();
      startPolling();
      $('#sup-input', root).focus();
    } catch (err) {
      paint();
      note(err.message || 'به سرور نرسیدیم.', true);
    }
  }

  function toggle() { setOpen(!State.open); }

  /* ============================== تپشِ بازدید ==============================
   *
   *  «من آمدم» — تا صاحب سامانه بداند چند نفر آمده‌اند، نه فقط چند نفر
   *  ثبت‌نام کرده‌اند. هیچ داده‌ای از دفترِ دکان نمی‌رود: فقط شناسهٔ همین
   *  مرورگر، سکو و زبان.
   *
   *  در پاسخ فقط عددِ پیام‌های خوانده‌نشده می‌آید — همان که نقطهٔ قرمز را
   *  روشن می‌کند بی‌آنکه لازم باشد کلِ گفت‌وگو خوانده شود.
   */
  const VISIT_KEY = 'tohid-visit-at';

  async function visit() {
    const uid = deviceUid();
    if (!uid || !serverUrl()) return;
    let last = 0;
    try { last = Number(localStorage.getItem(VISIT_KEY) || 0); } catch { /* بی‌اهمیت */ }
    if (Date.now() - last < VISIT_EVERY_MS) return;

    try {
      const data = await call('POST', '/visit', {
        app: 'shop',
        deviceUid: uid,
        platform: 'web',
        language: (navigator.language || 'fa').slice(0, 20),
        version: (document.documentElement.dataset.appVersion || ''),
        location: await quietLocation(),
      });
      try { localStorage.setItem(VISIT_KEY, String(Date.now())); } catch { /* بی‌اهمیت */ }
      State.unread = data.supportUnread || 0;
      ensure();
      paintDot();
    } catch { /* سرور خاموش یا آفلاین — تپش یک خبر است، نه یک شرط */ }
  }

  /**
   * لوکیشن — فقط اگر مرورگر **از قبل** اجازه‌اش را داده باشد.
   *
   * ── چرا پنجره‌ی اجازه باز نمی‌شود ─────────────────────────────────
   * تپشِ بازدید در پس‌زمینه و بی‌خبرِ کاربر می‌رود. پرسیدنِ اجازه‌ی
   * لوکیشن همان لحظه — بی آنکه کاربر کاری کرده باشد — هم آزار است و هم
   * بیشترِ مرورگرها ردش می‌کنند. پس فقط وقتی می‌پرسیم که اجازه از
   * پیش داده شده باشد؛ آن اجازه را ثبت‌نام یا خودِ کاربر داده است.
   *
   * اگر مرورگر `permissions` را نداشته باشد (سافاریِ قدیمی)، چیزی
   * نمی‌فرستیم — نه اینکه پنجره باز کنیم.
   */
  async function quietLocation() {
    try {
      if (!navigator.geolocation || !navigator.permissions) return undefined;
      const st = await navigator.permissions.query({ name: 'geolocation' });
      if (st.state !== 'granted') return undefined;
      const pos = await new Promise((ok, no) => {
        navigator.geolocation.getCurrentPosition(ok, no, {
          timeout: 6000, maximumAge: 10 * 60 * 1000, enableHighAccuracy: false,
        });
      });
      return {
        lat: pos.coords.latitude,
        lng: pos.coords.longitude,
        accuracy: pos.coords.accuracy,
        source: 'startup',
      };
    } catch { return undefined; }
  }

  /* ============================== راه‌اندازی ============================== */

  function init() {
    if (State.booted) return;
    State.booted = true;
    ensure();
    paintDot();
    //  عمداً کمی بعد از بالا آمدنِ صفحه، تا اولین رنگ‌آمیزی را کند نکند
    setTimeout(() => { visit(); }, 2500);
  }

  window.TohidSupport = {
    open: () => setOpen(true),
    close: () => setOpen(false),
    toggle,
    get unread() { return State.unread; },
  };

  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', init, { once: true });
  } else {
    init();
  }
})();
