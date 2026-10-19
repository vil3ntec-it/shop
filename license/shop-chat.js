/* ---------------------------------------------------------------------------
 *  چتِ مشتری ↔ دکان — نسخهٔ وب (v3 §۳۳؛ سمتِ کارمند)
 *
 *  سرور: `/api/shop/chat` (PR #259). ⛔ پیش‌فرض خاموش: تا `GET /config`
 *  نگوید `enabled: true`، نه ردیفی در منو می‌آید و نه صفحه‌ای باز می‌شود.
 *
 *    صندوقِ گفت‌وگو ── جست‌وجو، باز/بسته/همه، نمای کارتی/جدولی (ترجیحِ همین
 *                      کاربر روی همین دستگاه؛ گوشی همیشه کارتی — §۱٫۵).
 *    گفت‌وگو ───────── long-poll با `after=<rev>`؛ رسیدِ «رسید/خوانده» فقط وقتی
 *                      پیام واقعاً به این دستگاه آمده/جلوی چشم است.
 *    ارسال ─────────── صفِ محلی (`TohidChatCore.Queue`) با clientId و backoff؛
 *                      بی اینترنت نوشته می‌شود و پس از وصل یک بار می‌رود.
 *    عکس ───────────── روی همین دستگاه کوچک و از نو JPEG می‌شود (EXIF و مکان
 *                      نمی‌ماند) + پیش‌نمایشِ کوچک؛ عکسِ کامل فقط با زدن.
 *    صدا ───────────── Opus/AAC با MediaRecorder؛ مرورگری که ندارد دکمه را
 *                      نمی‌بیند و جمله‌ای می‌گوید چرا.
 *    بلاک/گزارش ────── بلاک فقط با `chat.moderate`؛ شمارهٔ کامل فقط با
 *                      `orders.contact` (وگرنه همان شمارهٔ پوشیدهٔ سرور).
 *
 *  ⛔ متنِ پیام هیچ‌جا لاگ نمی‌شود؛ هر متنِ کاربر با textContent.
 * ------------------------------------------------------------------------- */
(function () {
  'use strict';

  const Core = window.TohidChatCore;
  if (!Core) return;

  const WAIT_MS = 20000;
  const LIST_POLL_MS = 15000;
  const REASONS = { spam: 'هرزنامه', abuse: 'توهین یا آزار', fraud: 'کلاه‌برداری', other: 'دیگر' };
  const KIND_LABEL = { image: 'عکس', audio: 'پیامِ صوتی', file: 'فایل' };

  const S = {
    user: '', config: null, convs: [], status: 'open', q: '', view: 'table',
    active: null, thread: null, gen: 0, ctrl: null, page: false,
    media: new Map(), draining: new Set(), wakeTimer: 0, listTimer: 0,
    rec: null, voice: null, reportFor: '', banner: '',
  };
  let queue = null;
  const blobs = new Core.BlobStore('tohid-shop-chat-outbox');

  // ════════════════════════════════════════════════════════════════════
  //  کمک‌ها
  // ════════════════════════════════════════════════════════════════════

  function h(tag, attrs, ...kids) {
    const el = document.createElement(tag);
    for (const [k, v] of Object.entries(attrs || {})) {
      if (v === false || v == null) continue;
      if (k === 'class') el.className = v;
      else if (k === 'text') el.textContent = v;
      else if (k.startsWith('on')) el.addEventListener(k.slice(2), v);
      else el.setAttribute(k, v === true ? '' : v);
    }
    for (const c of kids.flat()) if (c != null && c !== false) el.append(c.nodeType ? c : String(c));
    return el;
  }
  const $ = (sel, root) => (root || document).querySelector(sel);
  const ls = {
    get(k) { try { return localStorage.getItem(k); } catch { return null; } },
    set(k, v) { try { localStorage.setItem(k, v); } catch { /* پر یا بسته */ } },
  };
  function when(ms) {
    if (!ms) return '';
    try { return new Date(ms).toLocaleString('fa-IR-u-nu-latn', { month: 'numeric', day: 'numeric', hour: '2-digit', minute: '2-digit' }); } catch { return ''; }
  }
  const kb = (n) => `${Math.max(1, Math.round((n || 0) / 1024))} کیلوبایت`;
  const mmss = (ms) => { const s = Math.round((ms || 0) / 1000); return `${Math.floor(s / 60)}:${String(s % 60).padStart(2, '0')}`; };
  const sleep = (ms) => new Promise((r) => setTimeout(r, ms));
  const isPhone = () => window.matchMedia('(max-width: 639px)').matches;

  function account() {
    try { return (window.TohidShop && window.TohidShop.account) || {}; } catch { return {}; }
  }
  function token() {
    try { return (window.TohidLicense && window.TohidLicense.getAccessToken()) || ''; } catch { return ''; }
  }

  // ════════════════════════════════════════════════════════════════════
  //  سرور
  // ════════════════════════════════════════════════════════════════════

  /** نشست را همان راهِ همیشگیِ برنامه تازه می‌کند (`TohidShop.apiAuth`). */
  async function refresh() {
    try { await window.TohidShop.apiAuth('/api/v1/shop/chat/config'); return true; } catch { return false; }
  }

  async function call(method, path, opt = {}) {
    const cfg = window.TohidApiConfig;
    if (!cfg || !cfg.baseUrl()) { const e = new Error('نشانی سرور تنظیم نشده است'); e.status = 0; throw e; }
    const headers = { Accept: 'application/json' };
    const t = token();
    if (t) headers.Authorization = 'Bearer ' + t;
    let body;
    if (opt.raw) { headers['Content-Type'] = opt.raw.type || 'application/octet-stream'; body = opt.raw; }
    else if (opt.body !== undefined) { headers['Content-Type'] = 'application/json'; body = JSON.stringify(opt.body); }
    const ctrl = new AbortController();
    const timer = setTimeout(() => ctrl.abort(), (opt.wait || 0) + 20000);
    const outer = opt.signal;
    const relay = () => ctrl.abort();
    if (outer) outer.addEventListener('abort', relay);
    let res;
    try {
      res = await fetch(cfg.url('/shop/chat' + path), { method, headers, body, signal: ctrl.signal, cache: 'no-store' });
    } catch {
      const e = new Error('اتصال به سرور برقرار نشد');
      e.status = 0; e.aborted = !!(outer && outer.aborted);
      throw e;
    } finally {
      clearTimeout(timer);
      if (outer) outer.removeEventListener('abort', relay);
    }
    if (res.status === 401 && !opt.retried && await refresh()) return call(method, path, { ...opt, retried: true });
    if (!res.ok) {
      let data = null;
      try { data = await res.json(); } catch { /* بی بدنه */ }
      const e = new Error((data && data.error && data.error.message) || `خطای سرور (${res.status})`);
      e.status = res.status; e.code = (data && data.error && data.error.code) || '';
      throw e;
    }
    return /json/.test(res.headers.get('content-type') || '') ? res.json() : res.blob();
  }

  const convPath = (id) => '/conversations/' + encodeURIComponent(id);

  // ════════════════════════════════════════════════════════════════════
  //  روشن/خاموش و منو
  // ════════════════════════════════════════════════════════════════════

  const cfgKey = () => `tohid-shop-chat-config:${S.user}`;
  const enabled = () => !!(S.config && S.config.enabled);
  const perm = (k) => !!(S.config && S.config.permissions && S.config.permissions[k]);

  function ensureQueue() {
    const user = String(account().userId || '');
    if (queue && user === S.user) return;
    S.user = user;
    if (!user) { queue = null; return; }
    const key = `tohid-shop-chat-queue:${user}`;
    queue = new Core.Queue({
      load: () => JSON.parse(ls.get(key) || 'null'),
      save: (items) => ls.set(key, JSON.stringify(items)),
    });
  }

  async function loadConfig() {
    ensureQueue();
    if (!S.user || !token()) { S.config = null; return paintNav(); }
    try {
      S.config = await call('GET', '/config');
      ls.set(cfgKey(), JSON.stringify(S.config));
    } catch (e) {
      //  بی اینترنت: آخرین پاسخِ همین حساب (صف و خواندنِ گذشته بی‌وقفه)؛ پاسخِ سرور ⇒ همان
      S.config = e.status ? null : (() => { try { return JSON.parse(ls.get(cfgKey()) || 'null'); } catch { return null; } })();
    }
    paintNav();
    if (enabled()) { flushAll(); refreshList(); }
  }

  function navLink(cls) {
    const a = h('a', { class: cls, 'data-page': 'chat', href: '#chat', 'data-chat-nav': '1' });
    return a;
  }

  function paintNav() {
    document.querySelectorAll('[data-chat-nav]').forEach((el) => (el.parentElement && el.parentElement.tagName === 'LI' ? el.parentElement : el).remove());
    if (!enabled()) {
      if (S.page) { location.hash = '#more'; }
      return;
    }
    const unread = S.convs.reduce((n, c) => n + (c.unread || 0), 0);
    const badge = () => unread ? h('span', { class: 'sc-badge', 'aria-label': `${unread} پیامِ تازه`, text: String(unread) }) : null;
    const side = $('#sidebar-nav');
    if (side) {
      const a = navLink('nav-item');
      a.append(h('span', { class: 'ic', 'aria-hidden': 'true', text: '✉' }), 'گفت‌وگو با مشتری', badge() || '');
      side.append(h('li', {}, a));
    }
    const more = $('#more-nav-list');
    if (more) {
      const a = navLink('more-card');
      a.append(
        h('span', { class: 'more-card-ic', 'aria-hidden': 'true', text: '✉' }),
        h('span', { class: 'more-card-txt' }, h('b', { text: 'گفت‌وگو با مشتری' }), h('i', { text: 'پیام‌های مشتریانِ ویترین دربارهٔ سفارش‌ها' })),
        badge() || '',
      );
      more.prepend(a);
    }
  }

  // ════════════════════════════════════════════════════════════════════
  //  صفحه
  // ════════════════════════════════════════════════════════════════════

  //  همان قالبِ کلیدِ `ViewModePref` ِ برنامه و `orders-ui.js`: هر کاربر، هر صفحه، فقط همین دستگاه
  function viewKey() { return `view_mode:${String(S.user || 'local').replace(/[^\w.@-]/g, '_')}:chat-inbox`; }
  function loadView() {
    const v = ls.get(viewKey());
    S.view = v === 'card' || v === 'table' ? v : 'table';
  }
  function curView() { return isPhone() ? 'card' : S.view; }

  function ensurePage() {
    if ($('#page-chat')) return $('#page-chat');
    const anchor = $('#page-more');
    if (!anchor) return null;
    const sec = h('section', { class: 'page', id: 'page-chat' });
    sec.append(
      h('div', { class: 'page-toolbar' }, h('div', { class: 'page-title-block' },
        h('h2', { text: 'گفت‌وگو با مشتری' }),
        h('p', { text: 'پیام‌های مشتریانِ ویترین — بی اینترنت هم نوشته می‌شود و پس از وصل یک بار می‌رود' }))),
      h('div', { class: 'sc-shell' },
        h('section', { class: 'panel sc-inbox', 'aria-label': 'فهرستِ گفت‌وگوها' },
          h('div', { class: 'sc-toolbar' },
            h('label', { class: 'sc-sr', for: 'sc-q', text: 'جست‌وجوی گفت‌وگو' }),
            h('input', { id: 'sc-q', class: 'sc-search', type: 'search', placeholder: 'نامِ مشتری یا شماره…', autocomplete: 'off' }),
            h('div', { class: 'sc-seg sc-tabs', role: 'tablist', 'aria-label': 'وضعیتِ گفت‌وگو' },
              ['open', 'closed', 'all'].map((s) => h('button', { type: 'button', role: 'tab', 'data-status': s, text: { open: 'باز', closed: 'بسته', all: 'همه' }[s] }))),
            h('div', { class: 'sc-seg sc-viewtg', role: 'group', 'aria-label': 'نمایش' },
              h('button', { type: 'button', 'data-view': 'card', text: 'کارتی', 'aria-label': 'نمای کارتی' }),
              h('button', { type: 'button', 'data-view': 'table', text: 'جدولی', 'aria-label': 'نمای جدولی' }))),
          h('p', { class: 'sc-note', role: 'status', hidden: true }),
          h('div', { class: 'sc-list' })),
        h('section', { class: 'panel sc-thread', 'aria-label': 'گفت‌وگو', hidden: true },
          h('div', { class: 'sc-head' },
            h('button', { type: 'button', class: 'btn btn-secondary btn-sm sc-back', text: 'بازگشت به فهرست' }),
            h('div', { class: 'sc-who' }, h('b', { class: 'sc-name' }), h('span', { class: 'sc-sub' })),
            h('div', { class: 'sc-actions' },
              h('button', { type: 'button', class: 'btn btn-secondary btn-sm sc-status' }),
              h('button', { type: 'button', class: 'btn btn-secondary btn-sm sc-report', text: 'گزارشِ سوءاستفاده' }),
              h('button', { type: 'button', class: 'btn btn-danger btn-sm sc-block' }))),
          h('form', { class: 'sc-report-form', hidden: true },
            h('b', { class: 'sc-report-title', text: 'گزارش به مدیرِ سامانه' }),
            h('label', { for: 'sc-reason', text: 'دلیل' }),
            h('select', { id: 'sc-reason' }, Object.entries(REASONS).map(([k, v]) => h('option', { value: k, text: v }))),
            h('label', { for: 'sc-rnote', text: 'توضیح (اختیاری)' }),
            h('textarea', { id: 'sc-rnote', maxlength: '500', rows: '2' }),
            h('div', { class: 'row' },
              h('button', { type: 'submit', class: 'btn btn-primary btn-sm', text: 'فرستادنِ گزارش' }),
              h('button', { type: 'button', class: 'btn btn-secondary btn-sm sc-report-cancel', text: 'انصراف' }))),
          h('p', { class: 'sc-banner', role: 'status', hidden: true }),
          h('ol', { class: 'sc-msgs', 'aria-label': 'پیام‌ها' }),
          h('div', { class: 'sc-voice', hidden: true }),
          h('form', { class: 'sc-compose' },
            h('label', { class: 'sc-sr', for: 'sc-text', text: 'متنِ پیام' }),
            h('textarea', { id: 'sc-text', rows: '1', maxlength: '4000', placeholder: 'پیام…' }),
            h('input', { type: 'file', class: 'sc-file', accept: 'image/jpeg,image/png,image/webp', hidden: true, 'aria-label': 'انتخابِ عکس' }),
            h('button', { type: 'button', class: 'btn btn-secondary sc-img', text: 'عکس' }),
            h('button', { type: 'button', class: 'btn btn-secondary sc-mic', text: 'صدا', hidden: true }),
            h('button', { type: 'submit', class: 'btn btn-primary sc-send', text: 'فرستادن' })),
          h('p', { class: 'sc-hint' }))),
    );
    anchor.after(sec);
    wire(sec);
    return sec;
  }

  function wire(sec) {
    let qTimer = 0;
    $('#sc-q', sec).addEventListener('input', (e) => {
      clearTimeout(qTimer);
      qTimer = setTimeout(() => { S.q = e.target.value.trim(); refreshList(); }, 300);
    });
    sec.querySelectorAll('[data-status]').forEach((b) => b.addEventListener('click', () => { S.status = b.dataset.status; refreshList(); }));
    sec.querySelectorAll('[data-view]').forEach((b) => b.addEventListener('click', () => {
      S.view = b.dataset.view; ls.set(viewKey(), S.view); paintList();
    }));
    $('.sc-back', sec).addEventListener('click', () => closeThread());
    $('.sc-status', sec).addEventListener('click', toggleStatus);
    $('.sc-block', sec).addEventListener('click', toggleBlock);
    $('.sc-report', sec).addEventListener('click', () => openReport(''));
    $('.sc-report-cancel', sec).addEventListener('click', () => { $('.sc-report-form').hidden = true; });
    $('.sc-report-form', sec).addEventListener('submit', submitReport);
    $('.sc-compose', sec).addEventListener('submit', (e) => { e.preventDefault(); sendText(); });
    $('#sc-text', sec).addEventListener('keydown', (e) => {
      if (e.key === 'Enter' && !e.shiftKey && !e.isComposing) { e.preventDefault(); sendText(); }
    });
    $('.sc-img', sec).addEventListener('click', () => $('.sc-file').click());
    $('.sc-file', sec).addEventListener('change', (e) => { const f = e.target.files && e.target.files[0]; e.target.value = ''; if (f) sendImage(f); });
    $('.sc-mic', sec).addEventListener('click', () => (S.rec ? stopRec() : startRec()));
    $('.sc-msgs', sec).addEventListener('click', onMessageClick);
  }

  function note(text, bad) {
    const n = $('#page-chat .sc-note');
    if (!n) return;
    n.hidden = !text; n.textContent = text || ''; n.classList.toggle('bad', !!bad);
  }
  function banner(text, bad) {
    const n = $('#page-chat .sc-banner');
    if (!n) return;
    n.hidden = !text; n.textContent = text || ''; n.classList.toggle('bad', !!bad);
  }

  // ── صندوق ──
  async function refreshList() {
    if (!enabled()) return;
    const qs = new URLSearchParams({ status: S.status, limit: '50' });
    if (S.q) qs.set('q', S.q);
    try {
      const r = await call('GET', '/conversations?' + qs);
      S.convs = r.conversations || [];
      note('');
    } catch (e) {
      if (e.status === 404 && e.code === 'chat_disabled') { S.config = null; return paintNav(); }
      note(e.status ? e.message : 'اتصال نیست؛ فهرست پس از وصل شدن تازه می‌شود.', !!e.status);
    }
    paintList();
    paintNav();
  }

  function preview(c) {
    const m = c.lastMessage;
    if (!m) return 'هنوز پیامی نیست';
    if (m.deleted) return 'پیامِ پاک‌شده';
    const who = m.sender === 'shop' ? 'دکان: ' : '';
    return who + (m.preview || KIND_LABEL[m.kind] || '');
  }
  const phoneOf = (c) => (c.customer && (c.customer.phone || c.customer.phoneMasked)) || '';
  const nameOf = (c) => (c.customer && c.customer.name) || 'مشتری';
  function pills(c) {
    const out = [];
    out.push(h('span', { class: 'sc-pill ' + (c.status === 'open' ? 'ok' : 'mute'), text: c.status === 'open' ? 'باز' : 'بسته' }));
    if (c.unread) out.push(h('span', { class: 'sc-pill warn', text: `${c.unread} تازه` }));
    if (c.blockedCustomer) out.push(h('span', { class: 'sc-pill bad', text: 'مسدود' }));
    if (c.customerBlockedShop) out.push(h('span', { class: 'sc-pill bad', text: 'مشتری بسته است' }));
    return out;
  }

  function paintList() {
    const sec = $('#page-chat');
    if (!sec) return;
    sec.querySelectorAll('[data-status]').forEach((b) => b.setAttribute('aria-selected', String(b.dataset.status === S.status)));
    const view = curView();
    const tg = $('.sc-viewtg', sec);
    tg.hidden = isPhone();
    tg.querySelectorAll('[data-view]').forEach((b) => b.setAttribute('aria-pressed', String(b.dataset.view === view)));
    $('.sc-shell', sec).classList.toggle('split', view === 'card' && !isPhone());
    const box = $('.sc-list', sec);
    //  همان داده و همان نما ⇒ DOM دست نمی‌خورد (کلیک و فوکوس وسطِ تازه‌سازی نمی‌پرد)
    const sig = JSON.stringify([view, S.active, S.q, S.convs]);
    if (box.dataset.sig === sig) return;
    box.dataset.sig = sig;
    box.textContent = '';
    if (!S.convs.length) {
      box.append(h('p', { class: 'sc-empty', text: S.q ? 'گفت‌وگویی با این نام یا شماره نیست.' : 'هنوز گفت‌وگویی نیست. وقتی مشتری از ویترین پیام بدهد، این‌جا می‌آید.' }));
      return;
    }
    if (view === 'card') {
      for (const c of S.convs) {
        box.append(h('button', { type: 'button', class: 'sc-card', 'data-conv': c.id, 'aria-current': String(c.id === S.active), onclick: () => openThread(c.id) },
          h('b', { text: nameOf(c) }), h('span', { class: 'sc-time', text: when(c.lastMessageAt || c.updatedAt) }),
          h('span', { class: 'sc-prev', text: [phoneOf(c), c.orderNo ? `سفارشِ ${c.orderNo}` : ''].filter(Boolean).join(' · ') }),
          h('span', { class: 'sc-prev', text: preview(c) }),
          h('span', { class: 'sc-tags' }, pills(c))));
      }
      return;
    }
    const rows = S.convs.map((c) => h('tr', { 'data-conv': c.id, 'aria-current': String(c.id === S.active) },
      h('td', {}, h('button', { type: 'button', class: 'sc-row-btn', onclick: () => openThread(c.id), text: nameOf(c) })),
      h('td', { dir: 'ltr', text: phoneOf(c) }),
      h('td', { text: c.orderNo ? String(c.orderNo) : '—' }),
      h('td', { text: preview(c) }),
      h('td', { class: 'sc-time', text: when(c.lastMessageAt || c.updatedAt) }),
      h('td', {}, pills(c))));
    box.append(h('div', { class: 'sc-table-wrap' }, h('table', { class: 'sc-table' },
      h('thead', {}, h('tr', {}, ['مشتری', 'شماره', 'سفارش', 'آخرین پیام', 'زمان', 'وضعیت'].map((t) => h('th', { scope: 'col', text: t })))),
      h('tbody', {}, rows))));
  }

  // ── گفت‌وگو ──
  function openThread(id) {
    stopPoll();
    S.active = id;
    S.thread = new Core.Thread();
    const c = S.convs.find((x) => x.id === id);
    if (c) S.thread.conversation = c;
    const sec = $('#page-chat');
    $('.sc-thread', sec).hidden = false;
    $('.sc-shell', sec).classList.add('has-thread');
    $('.sc-report-form', sec).hidden = true;
    banner('');
    paintHead();
    paintThread();
    paintList();
    poll(++S.gen);
    flush(id);
    if (!isPhone()) $('#sc-text', sec).focus();
  }

  function closeThread() {
    stopPoll();
    S.active = null;
    S.thread = null;
    const sec = $('#page-chat');
    if (!sec) return;
    $('.sc-thread', sec).hidden = true;
    $('.sc-shell', sec).classList.remove('has-thread');
    cancelVoice();
    paintList();
  }

  function stopPoll() {
    S.gen++;
    if (S.ctrl) { S.ctrl.abort(); S.ctrl = null; }
  }

  async function poll(gen) {
    let fails = 0;
    let first = true;
    while (gen === S.gen && S.active) {
      const id = S.active;
      const t = S.thread;
      S.ctrl = new AbortController();
      const wait = first ? 0 : WAIT_MS;
      try {
        const res = await call('GET', `${convPath(id)}/messages?after=${t.cursor}&wait=${wait}`, { wait, signal: S.ctrl.signal });
        if (gen !== S.gen) return;
        first = false;
        fails = 0;
        const r = t.apply(res);
        if (queue) for (const k of queue.reconcile(id, (res.messages || []).filter((m) => m.mine))) blobs.del(k);
        if (S.banner) { S.banner = ''; banner(''); }
        if (r.changed) { paintHead(); paintThread(); }
        receipts();
        if (r.hasMore) continue;
      } catch (e) {
        if (gen !== S.gen || e.aborted) return;
        if (e.status === 404) {
          if (e.code === 'chat_disabled') { S.config = null; paintNav(); return; }
          banner(e.message, true); return;
        }
        fails++;
        S.banner = e.status ? e.message : 'اتصال نیست؛ پیام‌های نوشته‌شده در صف می‌مانند و پس از وصل می‌روند.';
        banner(S.banner, !!e.status);
        await sleep(Core.backoffMs(fails));
      }
    }
  }

  let receiptBusy = false;
  async function receipts() {
    const t = S.thread;
    if (!t || receiptBusy) return;
    const need = t.receiptNeed(S.page && document.visibilityState === 'visible');
    if (!need) return;
    receiptBusy = true;
    try {
      const r = await call('POST', `${convPath(S.active)}/receipts`, { body: need });
      if (S.thread === t && r.conversation) { t.conversation = r.conversation; paintThread(); }
      const c = S.convs.find((x) => x.id === r.conversation.id);
      if (c) { Object.assign(c, { unread: r.conversation.unread }); paintNav(); }
    } catch { /* دورِ بعد */ } finally { receiptBusy = false; }
  }

  function conv() { return (S.thread && S.thread.conversation) || S.convs.find((x) => x.id === S.active) || null; }

  function paintHead() {
    const sec = $('#page-chat');
    const c = conv();
    if (!sec || !c) return;
    $('.sc-name', sec).textContent = nameOf(c);
    $('.sc-sub', sec).textContent = [phoneOf(c), c.orderNo ? `سفارشِ ${c.orderNo}` : ''].filter(Boolean).join(' · ');
    $('.sc-sub', sec).dir = 'auto';
    const st = $('.sc-status', sec);
    st.hidden = !perm('reply');
    st.textContent = c.status === 'open' ? 'بستنِ گفت‌وگو' : 'باز کردنِ گفت‌وگو';
    const bl = $('.sc-block', sec);
    bl.hidden = !perm('moderate');
    bl.textContent = c.blockedCustomer ? 'برداشتنِ مسدودی' : 'مسدود کردنِ مشتری';
    bl.className = 'btn btn-sm sc-block ' + (c.blockedCustomer ? 'btn-secondary' : 'btn-danger');
    const canWrite = perm('reply') && c.canSend !== false;
    $('.sc-compose', sec).hidden = !perm('reply');
    for (const el of sec.querySelectorAll('.sc-compose textarea, .sc-compose button')) el.disabled = !canWrite;
    const vt = Core.voiceType();
    $('.sc-mic', sec).hidden = !vt;
    $('.sc-hint', sec).textContent = !perm('reply') ? 'نقشِ شما اجازهٔ پاسخ ندارد؛ فقط خواندن.'
      : c.customerBlockedShop ? 'مشتری پیامِ این دکان را بسته است.'
      : vt ? '' : 'ضبطِ صدا در این مرورگر پشتیبانی نمی‌شود؛ پیامِ صوتیِ مشتری را می‌توانید گوش کنید.';
  }

  function itemsToShow() {
    const t = S.thread;
    const server = t ? t.list() : [];
    const have = new Set(server.map((m) => m.clientId).filter(Boolean));
    const local = queue ? queue.forConv(S.active).filter((x) => !have.has(x.clientId)) : [];
    return { server, local };
  }

  function stLabel(state) { return Core.LABEL[state] || ''; }

  function bubbleMedia(m, local) {
    if (m.kind === 'image') {
      const thumbId = local ? '' : (m.media && (m.media.thumbId || m.media.id));
      const img = h('img', { alt: 'عکس', 'data-thumb': thumbId || '', 'data-local': local ? m.thumbKey || m.blobKey || '' : '' });
      if (m.meta && m.meta.width) { img.width = Math.min(240, m.meta.width); }
      return h('button', { type: 'button', class: 'sc-thumb', 'data-full': local ? '' : (m.media && m.media.id) || '', 'aria-label': 'نمایشِ عکسِ کامل' }, img);
    }
    if (m.kind === 'audio') {
      const dur = m.meta && m.meta.durationMs ? ' · ' + mmss(m.meta.durationMs) : '';
      if (local) return h('div', { class: 'sc-audio', text: 'پیامِ صوتی' + dur });
      return h('div', { class: 'sc-audio' }, h('button', { type: 'button', class: 'btn btn-secondary btn-sm sc-play', 'data-media': (m.media && m.media.id) || '', text: 'پخشِ پیامِ صوتی' + dur }));
    }
    if (m.kind === 'file') return h('p', { text: 'فایلِ PDF' });
    return null;
  }

  function paintThread() {
    const sec = $('#page-chat');
    if (!sec || !S.active) return;
    const list = $('.sc-msgs', sec);
    const nearBottom = list.scrollHeight - list.scrollTop - list.clientHeight < 80;
    const { server, local } = itemsToShow();
    const receiptsNow = conv() && conv().receipts;
    list.textContent = '';
    for (const m of server) {
      const mine = m.sender === 'shop';
      const meta = h('div', { class: 'sc-meta' }, h('time', { text: when(m.createdAt) }));
      if (m.editedAt) meta.append(h('span', { text: 'ویرایش‌شده' }));
      if (mine && !m.deleted) {
        const st = Core.statusOf(m, receiptsNow);
        meta.append(h('span', { class: 'sc-st ' + st, 'data-status': st, text: stLabel(st) }));
      }
      if (!mine && !m.deleted) meta.append(h('button', { type: 'button', class: 'sc-link', 'data-report': m.id, text: 'گزارش' }));
      const bubble = h('div', { class: 'sc-bubble' });
      if (m.deleted) bubble.append(h('p', { class: 'sc-del', text: 'این پیام پاک شد' }));
      else {
        const media = bubbleMedia(m, false);
        if (media) bubble.append(media);
        if (m.body) bubble.append(h('p', { dir: 'auto', text: m.body }));
      }
      bubble.append(meta);
      list.append(h('li', { class: 'sc-msg ' + (mine ? 'mine' : 'theirs'), 'data-id': m.id }, bubble));
    }
    for (const x of local) {
      const meta = h('div', { class: 'sc-meta' }, h('time', { text: when(x.createdAt) }),
        h('span', { class: 'sc-st ' + x.state, 'data-status': x.state, text: stLabel(x.state) + (x.state === 'failed' && x.error ? ` — ${x.error}` : '') }));
      if (x.state !== 'sending') {
        if (x.state !== 'queued') meta.append(h('button', { type: 'button', class: 'sc-link', 'data-retry': x.clientId, text: 'دوباره' }));
        meta.append(h('button', { type: 'button', class: 'sc-link', 'data-cancel': x.clientId, text: 'لغو' }));
      }
      const bubble = h('div', { class: 'sc-bubble' });
      const media = bubbleMedia(x, true);
      if (media) bubble.append(media);
      if (x.body) bubble.append(h('p', { dir: 'auto', text: x.body }));
      bubble.append(meta);
      list.append(h('li', { class: 'sc-msg mine pending', 'data-client': x.clientId }, bubble));
    }
    if (!server.length && !local.length) list.append(h('li', { class: 'sc-empty', text: 'هنوز پیامی نیست.' }));
    if (nearBottom || !list.dataset.painted) list.scrollTop = list.scrollHeight;
    list.dataset.painted = '1';
    loadThumbs(list);
  }

  async function mediaUrl(id) {
    if (S.media.has(id)) return S.media.get(id);
    const blob = await call('GET', `${convPath(S.active)}/media/${encodeURIComponent(id)}`);
    const u = URL.createObjectURL(blob);
    S.media.set(id, u);
    return u;
  }
  const localUrls = new Map();
  async function localUrl(key) {
    if (localUrls.has(key)) return localUrls.get(key);
    const b = await blobs.get(key);
    if (!b) return '';
    const u = URL.createObjectURL(b);
    localUrls.set(key, u);
    return u;
  }

  function loadThumbs(root) {
    root.querySelectorAll('img[data-thumb], img[data-local]').forEach(async (img) => {
      if (img.src) return;
      try {
        const u = img.dataset.local ? await localUrl(img.dataset.local) : img.dataset.thumb ? await mediaUrl(img.dataset.thumb) : '';
        if (u) img.src = u;
      } catch { img.alt = 'عکس باز نشد'; }
    });
  }

  async function onMessageClick(e) {
    const t = e.target.closest('button');
    if (!t) return;
    if (t.dataset.retry) { queue.retry(t.dataset.retry); paintThread(); flush(S.active); return; }
    if (t.dataset.cancel) {
      if (!window.confirm('این پیام فرستاده نشود؟')) return;
      for (const k of queue.cancel(t.dataset.cancel) || []) blobs.del(k);
      paintThread();
      return;
    }
    if (t.dataset.report) { openReport(t.dataset.report); return; }
    if (t.classList.contains('sc-thumb') && t.dataset.full) {
      t.disabled = true;
      try {
        const u = await mediaUrl(t.dataset.full);
        t.replaceWith(h('img', { class: 'sc-full', src: u, alt: 'عکس' }));
      } catch (err) { t.disabled = false; banner(err.message, true); }
      return;
    }
    if (t.classList.contains('sc-play') && t.dataset.media) {
      t.disabled = true;
      try {
        const u = await mediaUrl(t.dataset.media);
        const a = h('audio', { controls: true, src: u, preload: 'auto' });
        t.replaceWith(a);
        a.play().catch(() => { /* کاربر خودش پخش می‌زند */ });
      } catch (err) { t.disabled = false; banner(err.message, true); }
    }
  }

  // ── وضعیت، بلاک، گزارش ──
  async function toggleStatus() {
    const c = conv();
    if (!c) return;
    try {
      const r = await call('PUT', `${convPath(c.id)}/status`, { body: { status: c.status === 'open' ? 'closed' : 'open' } });
      mergeConv(r.conversation);
    } catch (e) { banner(e.message, true); }
  }
  async function toggleBlock() {
    const c = conv();
    if (!c) return;
    const on = !c.blockedCustomer;
    if (on && !window.confirm('این مشتری دیگر نتواند به دکان پیام بدهد؟ گفت‌وگوهای گذشته می‌ماند.')) return;
    try {
      const r = await call(on ? 'PUT' : 'DELETE', `${convPath(c.id)}/block`);
      mergeConv(r.conversation);
    } catch (e) { banner(e.message, true); }
  }
  function mergeConv(c) {
    if (!c) return;
    if (S.thread && S.active === c.id) S.thread.conversation = c;
    const i = S.convs.findIndex((x) => x.id === c.id);
    if (i >= 0) S.convs[i] = c;
    paintHead(); paintThread(); paintList();
  }
  function openReport(messageId) {
    S.reportFor = messageId || '';
    const f = $('#page-chat .sc-report-form');
    $('.sc-report-title', f).textContent = messageId ? 'گزارشِ این پیام به مدیرِ سامانه' : 'گزارشِ این گفت‌وگو به مدیرِ سامانه';
    f.hidden = false;
    $('#sc-reason').focus();
  }
  async function submitReport(e) {
    e.preventDefault();
    const body = { reason: $('#sc-reason').value, note: $('#sc-rnote').value.trim() };
    if (S.reportFor) body.messageId = S.reportFor;
    try {
      const r = await call('POST', `${convPath(S.active)}/reports`, { body });
      $('#page-chat .sc-report-form').hidden = true;
      $('#sc-rnote').value = '';
      banner(r.duplicate ? 'این مورد پیش‌تر گزارش شده بود.' : 'گزارش رسید؛ مدیرِ سامانه بررسی می‌کند.');
    } catch (err) { banner(err.message, true); }
  }

  // ════════════════════════════════════════════════════════════════════
  //  ارسال (صف)
  // ════════════════════════════════════════════════════════════════════

  function enqueue(msg) {
    if (!queue || !S.active) return null;
    const item = queue.add(S.active, msg);
    paintThread();
    flush(S.active);
    return item;
  }

  function sendText() {
    const ta = $('#sc-text');
    const body = ta.value.trim();
    if (!body) return;
    if (body.length > 4000) { banner('پیام خیلی بلند است — حداکثر ۴۰۰۰ نویسه', true); return; }
    ta.value = '';
    enqueue({ kind: 'text', body });
  }

  async function sendImage(file) {
    const lim = (S.config && S.config.limits) || {};
    banner('آماده کردنِ عکس…');
    try {
      const out = await Core.prepareImage(file, lim);
      const id = Core.newClientId();
      await blobs.put(id + ':main', out.main);
      await blobs.put(id + ':thumb', out.thumb);
      const caption = $('#sc-text').value.trim();
      $('#sc-text').value = '';
      enqueue({ clientId: id, kind: 'image', body: caption, blobKey: id + ':main', thumbKey: id + ':thumb', size: out.main.size, meta: { width: out.width, height: out.height } });
      banner(`عکس آماده شد (${kb(out.main.size)})`);
    } catch (e) { banner(e.message || 'عکس آماده نشد', true); }
  }

  async function upload(item, key, role) {
    const blob = await blobs.get(key);
    if (!blob) { const e = new Error('فایلِ این پیام روی این دستگاه نمانده'); e.status = 410; throw e; }
    const r = await call('POST', `${convPath(item.convId)}/media?role=${role}`, { raw: blob });
    return r.mediaId;
  }

  async function sendItem(item) {
    if (item.kind !== 'text') {
      if (!item.mediaId) queue.patch(item, { mediaId: await upload(item, item.blobKey, 'main') });
      if (item.thumbKey && !item.thumbId) queue.patch(item, { thumbId: await upload(item, item.thumbKey, 'thumb') });
    }
    const body = { clientId: item.clientId, kind: item.kind, body: item.body };
    if (item.mediaId) body.mediaId = item.mediaId;
    if (item.thumbId) body.thumbId = item.thumbId;
    if (item.meta && Object.keys(item.meta).length) body.meta = item.meta;
    const r = await call('POST', `${convPath(item.convId)}/messages`, { body });
    return r.message;
  }

  async function flush(convId) {
    if (!queue || !enabled() || S.draining.has(convId)) return;
    S.draining.add(convId);
    try {
      await Core.drain(queue, convId, sendItem, {
        onChange: () => { if (S.active === convId) paintThread(); },
        onSent: (item, message, keys) => {
          for (const k of keys) blobs.del(k);
          if (S.active === convId && S.thread && message) { S.thread.apply({ messages: [message] }); paintThread(); }
        },
      });
    } finally {
      S.draining.delete(convId);
    }
    schedule();
  }

  function flushAll() {
    if (!queue) return;
    for (const c of queue.convs()) flush(c);
  }

  function schedule() {
    clearTimeout(S.wakeTimer);
    if (!queue) return;
    const at = queue.nextWake();
    if (at === null) return;
    S.wakeTimer = setTimeout(flushAll, Math.max(0, at - Date.now()) + 50);
  }

  // ════════════════════════════════════════════════════════════════════
  //  پیامِ صوتی
  // ════════════════════════════════════════════════════════════════════

  async function startRec() {
    const type = Core.voiceType();
    if (!type || S.rec) return;
    let stream;
    try { stream = await navigator.mediaDevices.getUserMedia({ audio: true }); } catch {
      banner('بی اجازهٔ میکروفون صدا ضبط نمی‌شود.', true);
      return;
    }
    const chunks = [];
    const rec = new MediaRecorder(stream, { mimeType: type, audioBitsPerSecond: 24000 });
    const started = Date.now();
    rec.ondataavailable = (e) => { if (e.data && e.data.size) chunks.push(e.data); };
    rec.onstop = () => {
      stream.getTracks().forEach((t) => t.stop());
      const ms = Date.now() - started;
      if (S.rec && S.rec.cancelled) { S.rec = null; paintVoice(); return; }
      S.rec = null;
      S.voice = { blob: new Blob(chunks, { type: type.split(';')[0] }), ms };
      paintVoice();
    };
    S.rec = { rec, started, timer: setInterval(paintVoice, 500), limit: setTimeout(stopRec, Core.VOICE_MAX_MS) };
    rec.start(1000);
    paintVoice();
  }

  function stopRec() {
    if (!S.rec) return;
    clearInterval(S.rec.timer); clearTimeout(S.rec.limit);
    try { S.rec.rec.stop(); } catch { /* پیش‌تر ایستاده */ }
  }

  function cancelVoice() {
    if (S.rec) { S.rec.cancelled = true; stopRec(); }
    S.voice = null;
    paintVoice();
  }

  function paintVoice() {
    const box = $('#page-chat .sc-voice');
    const mic = $('#page-chat .sc-mic');
    if (!box) return;
    box.textContent = '';
    if (mic) mic.textContent = S.rec ? 'پایانِ ضبط' : 'صدا';
    if (S.rec) {
      box.hidden = false;
      box.append(h('span', { class: 'sc-rec', role: 'status', text: `● در حالِ ضبط ${mmss(Date.now() - S.rec.started)}` }),
        h('button', { type: 'button', class: 'btn btn-secondary btn-sm', text: 'لغوِ ضبط', onclick: cancelVoice }));
      return;
    }
    if (!S.voice) { box.hidden = true; return; }
    const lim = (S.config && S.config.limits && S.config.limits.audioMax) || 0;
    const big = lim && S.voice.blob.size > lim;
    const url = URL.createObjectURL(S.voice.blob);
    box.hidden = false;
    box.append(
      h('span', { text: `پیامِ صوتی ${mmss(S.voice.ms)} · ${kb(S.voice.blob.size)}` }),
      h('audio', { controls: true, src: url }),
      h('button', { type: 'button', class: 'btn btn-primary btn-sm', disabled: big, text: 'فرستادنِ صدا', onclick: sendVoice }),
      h('button', { type: 'button', class: 'btn btn-secondary btn-sm', text: 'لغو', onclick: cancelVoice }));
    if (big) box.append(h('span', { class: 'sc-st failed', text: `بزرگ‌تر از سقفِ ${kb(lim)}` }));
  }

  async function sendVoice() {
    const v = S.voice;
    if (!v) return;
    const id = Core.newClientId();
    await blobs.put(id + ':main', v.blob);
    S.voice = null;
    paintVoice();
    enqueue({ clientId: id, kind: 'audio', body: '', blobKey: id + ':main', size: v.blob.size, meta: { durationMs: Math.max(1, Math.round(v.ms)) } });
  }

  // ════════════════════════════════════════════════════════════════════
  //  راه‌اندازی
  // ════════════════════════════════════════════════════════════════════

  function onPage(pageId) {
    const was = S.page;
    S.page = pageId === 'chat';
    //  برنامه گاهی همان صفحه را دوباره «باز» می‌کند (همگام‌سازی، تازه‌سازی) — نما همان می‌ماند
    if (was && S.page && $('#page-chat')) { $('#page-chat').classList.add('active'); return; }
    clearInterval(S.listTimer);
    if (!S.page) { stopPoll(); return; }
    if (!enabled()) { location.hash = '#more'; return; }
    const sec = ensurePage();
    if (!sec) return;
    sec.classList.add('active');
    const title = $('#page-title');
    if (title) title.textContent = 'گفت‌وگو با مشتری';
    loadView();
    paintList();
    refreshList();
    S.listTimer = setInterval(() => { if (document.visibilityState === 'visible') refreshList(); }, LIST_POLL_MS);
    if (S.active) openThread(S.active);
  }

  function init() {
    ensureQueue();
    loadView();
    document.addEventListener('tohid:page-change', (e) => onPage(e.detail && e.detail.pageId));
    document.addEventListener('tohid:license-change', () => { queue = null; S.user = ''; loadConfig(); });
    window.addEventListener('online', () => { if (queue && queue.wake()) flushAll(); else flushAll(); });
    document.addEventListener('visibilitychange', () => { if (document.visibilityState === 'visible') receipts(); });
    window.addEventListener('resize', () => { if (S.page) paintList(); });
    loadConfig().then(() => {
      if ((location.hash || '').replace('#', '') === 'chat') onPage('chat');
    });
  }

  window.TohidShopChat = {
    reload: loadConfig,
    get enabled() { return enabled(); },
    get queued() { return queue ? queue.items.length : 0; },
  };

  if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', init, { once: true });
  else init();
})();
