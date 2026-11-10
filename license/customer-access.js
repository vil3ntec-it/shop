/**
 * CRM-15 در نسخهٔ وبِ دکان — «دسترسیِ محدود» و «سابقهٔ دسترسی»، همان قاعدهٔ برنامه (`ContactPrivacy`، `CustomerAccessNote`،
 * `CustomerAccessPanel`). کاربرِ آیفون فقط همین وب را دارد؛ پیش‌تر وب هیچ‌کدام را نداشت.
 *
 *   • شمارهٔ مشتری برای اصلِ بی `customers.contact` (پیش‌فرضِ انباردار و جمع‌آور، یا لایهٔ صاحب) پوشیده — هر رقم جز دو
 *     رقمِ آخر «•» ([mask] = `ContactMask.phone` = `maskPhone` ِ سرور): فهرستِ قرض‌داران (کارت و جدول)، خروجی/چاپِ برگهٔ
 *     قرض‌داران، و در ویرایشِ پرونده کادرِ شماره پنهان (مقدار دست نمی‌خورد ⇒ ذخیره همان شماره را می‌نویسد؛ سرور هم
 *     تماسِ ردیفِ موجود را از این اصل نمی‌پذیرد). سرور از همگام‌سازی همان شماره را اصلاً نمی‌دهد؛ این پوشاندن برای
 *     دفترِ ازپیش‌همگام‌شدهٔ همین مرورگر است. فقط نمایش است، نه امنیت.
 *   • هر دیدنِ یک مشتری ⇒ `POST /api/shop/customer-access` (درِ `app`؛ بیننده، نقش و دستگاه از توکن): باز شدنِ حساب
 *     (`detail`؛ صفحهٔ حسابِ وب شماره ندارد)، ویرایشِ پروندهٔ با شمارهٔ پیدا (`phone`)، صورت‌حساب CSV/چاپ (`export`)، و
 *     خروجی/چاپِ برگهٔ قرض‌داران (`*` ⇒ `export`). هر (مشتری، نوع) در ۱۰ دقیقه یک بار از همین صفحه؛ سرور هم یکی می‌کند.
 *     بی حساب یا بی اینترنت ⇒ هیچ (همان دفترِ محلیِ بی سرور). ⛔ هیچ نام، شماره یا نشانی در درخواست نیست.
 *   • «چه کسی این پرونده را دید» در صفحهٔ حساب فقط برای `audit.view` (صاحب همیشه؛ پیش‌فرض مدیر و حسابدار) از
 *     `GET /api/shop/customer-access?customerId=` — نام، نقش، نوع، زمان و شمار؛ خطای شبکه ⇒ بخش پنهان.
 * مجوز از `policy.effective` ِ `GET /api/v1/shop/me` (`shop-sync.js` ‹refreshShopInfo›، همان `ShopRole.allows` ِ برنامه)؛
 * بی `policy` (نسخهٔ کهنهٔ حالت) ⇒ پیش‌فرضِ سه نقشِ قدیم. سرور در هر حال داور است.
 *
 * `index.html` یخ‌زده است ⇒ بارکننده `shop-sync.js` و هیچ خطی در صفحه: `openAccount`، `openEditDebtor`، `renderDebtors`،
 * `statementRows` و `buildReportCsvRows` ِ صفحه پوشانده می‌شوند (همان راهِ `product-cycle.js`).
 * ⛔ CSP: بی innerHTML/style/eval؛ متن فقط کلیدهای واژه‌نامهٔ صفحه (`web-i18n.js` انگلیسی و پشتو را برمی‌گرداند)؛ نامِ
 *    بیننده در ظرفِ `data-raw`.
 * آزمون: `server/test/web-customer-access.test.js` (منطق) و `server/test/web-customer-access.mjs` (صفحه تا سرور).
 */
(function (root, factory) {
  'use strict';
  const api = factory(root);
  if (typeof module !== 'undefined' && module.exports) module.exports = api;
  if (typeof window !== 'undefined' && root === window) {
    root.TohidCustomerAccess = api;
    api.install();
  }
})(typeof window !== 'undefined' ? window : globalThis, function (root) {
  'use strict';

  const PATH = '/api/shop/customer-access';
  const ALL = '*';
  const WINDOW_MS = 10 * 60 * 1000;
  const DOT = '•';
  const ND = /^\p{Nd}$/u;
  const ID = /^[A-Za-z0-9_.:-]{1,80}$/;
  /** پیش‌فرضِ نقش بی `policy` (فقط سه نقشِ قدیمِ حالتِ کهنه) — همان `perm-core` */
  const DEFAULTS = {
    'customers.contact': () => true,
    'audit.view': (role) => role === 'manager',
  };
  /** متن — کلیدِ واژه‌نامهٔ صفحه (`license/i18n/web.*.json`)، همان `StrCustomerAccess` و `StrAccount.role*` ِ برنامه */
  const TEXT = Object.freeze({
    title: 'چه کسی این پرونده را دید',
    hint: 'باز کردنِ پرونده، دیدنِ شماره و خروجی؛ از هر کس در هر ۱۰ دقیقه یک بار. فقط صاحب و کسی که «سابقهٔ عملیات» را می‌بیند.',
    empty: 'هنوز کسی این پرونده را ندیده است.',
    unknown: 'ناشناس',
    times: '{0} بار',
    kind: { detail: 'پرونده', phone: 'شماره و نشانی', export: 'خروجی', all: 'خروجیِ همهٔ مشتریان' },
    role: {
      owner: 'صاحب دکان', manager: 'مدیر', staff: 'شاگرد', cashier: 'صندوق‌دار', storekeeper: 'مسئولِ انبار',
      picker: 'جمع‌آورِ سفارش', accountant: 'حسابدار (فقط دیدن)',
    },
  });

  /** هر رقم (یونیکد Nd) جز [keep] رقمِ آخر ⇒ «•»؛ نشانه‌ها و جهت‌نماها همان — `ContactMask.phone` / `maskPhone` ِ سرور */
  function mask(text, keep = 2) {
    const s = String(text == null ? '' : text);
    const chars = Array.from(s);
    const digits = chars.filter((c) => ND.test(c)).length;
    let seen = 0;
    return chars.map((c) => {
      if (!ND.test(c)) return c;
      seen++;
      return seen > digits - keep ? c : DOT;
    }).join('');
  }

  /**
   * این اصل [key] را دارد؟ `shop` = `TohidShop.state.shop` (`role`، `isOwner`، `policy`). بی دکان (بی حساب، پیش از
   * نخستین پرسش) ⇒ بله: همان «صاحب فرض می‌شود» ِ صفحه — دفترِ خودِ همین مرورگر است و قفلِ واقعی سرور.
   */
  function allows(shop, key) {
    if (!shop || typeof shop !== 'object') return true;
    if (shop.isOwner === true || shop.role === 'owner') return true;
    const eff = shop.policy && typeof shop.policy === 'object' ? shop.policy.effective : null;
    const r = eff && typeof eff === 'object' && Object.prototype.hasOwnProperty.call(eff, key) ? eff[key] : null;
    if (r && typeof r.mode === 'string') return r.mode === 'allow';
    const d = Object.prototype.hasOwnProperty.call(DEFAULTS, key) ? DEFAULTS[key] : null;
    return d ? d(String(shop.role || '')) : false;
  }

  /** بدنهٔ `POST` — فقط شناسه و نوع؛ شناسهٔ ناامن ⇒ null؛ `*` فقط با `export` (همان `CustomerAccess.noteBody`) */
  function noteBody(customerId, kind) {
    const id = typeof customerId === 'string' ? customerId.trim() : '';
    if (!['detail', 'phone', 'export'].includes(kind)) return null;
    const ok = id === ALL ? kind === 'export' : ID.test(id) && !id.startsWith('erased:') && !id.startsWith('ph:');
    return ok ? { customerId: id, kind } : null;
  }

  /** یک ردیفِ فهرست ⇒ تکه‌های نمایش: `[{text, raw?}]` (نامِ بیننده داده است) */
  function parts(e, fmt) {
    const out = [];
    const name = e && typeof e.viewerName === 'string' && e.viewerName.trim() ? e.viewerName.trim() : '';
    out.push(name ? { text: name, raw: true } : { text: TEXT.unknown });
    const role = e && typeof e.role === 'string' && Object.prototype.hasOwnProperty.call(TEXT.role, e.role) ? TEXT.role[e.role] : '';
    if (role) out.push({ text: role });
    const kind = e && e.kind === 'export' && e.all === true ? 'all' : e && e.kind;
    if (Object.prototype.hasOwnProperty.call(TEXT.kind, kind)) out.push({ text: TEXT.kind[kind] });
    const at = e && Number.isFinite(e.lastAt) ? e.lastAt : (e && Number.isFinite(e.firstAt) ? e.firstAt : 0);
    if (at > 0 && fmt) out.push({ text: fmt(at), raw: true });
    const n = e && Number.isInteger(e.count) ? e.count : 1;
    if (n > 1) out.push({ text: TEXT.times.replace('{0}', String(n)) });
    return out;
  }

  /* ───────────────────────── صفحه ───────────────────────── */

  const sent = new Map();
  let lastAccount = '';

  const shopState = () => {
    const s = root.TohidShop && root.TohidShop.state;
    return s && s.shop ? s.shop : null;
  };
  //  وارد شده است (دکان را سرور از توکن می‌داند؛ پیش از نخستین `refreshShopInfo` هم — بی دکان سرور ۴۰۳ می‌دهد و بس)
  const online = () => {
    const t = root.TohidShop;
    return !!(t && t.account && t.account.loggedIn && typeof t.apiAuth === 'function');
  };
  const contactsVisible = () => allows(shopState(), 'customers.contact');

  /** یک دیدن به سرور — بی انتظار، بی خطا؛ همان (مشتری، نوع) در ۱۰ دقیقه یک بار از همین صفحه */
  function note(customerId, kind) {
    const body = noteBody(customerId, kind);
    if (!body || !online()) return Promise.resolve(false);
    const key = `${body.customerId}\n${body.kind}`;
    const t = Date.now();
    if (t - (sent.get(key) || 0) < WINDOW_MS) return Promise.resolve(false);
    sent.set(key, t);
    return root.TohidShop.apiAuth(PATH, { method: 'POST', body })
      .then(() => true, () => { sent.delete(key); return false; });
  }

  function localStamp(at) {
    const d = new Date(at);
    const pad = (x) => String(x).padStart(2, '0');
    const iso = `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`;
    const day = typeof root.formatDate === 'function' ? root.formatDate(iso) : iso;
    return `${day} ${pad(d.getHours())}:${pad(d.getMinutes())}`;
  }

  function panel() {
    let el = document.getElementById('acc-access-panel');
    if (el) return el;
    const anchor = document.getElementById('acc-sales-panel');
    if (!anchor || !anchor.parentNode) return null;
    el = document.createElement('div');
    el.className = 'panel';
    el.id = 'acc-access-panel';
    el.hidden = true;
    const head = document.createElement('div');
    head.className = 'panel-head';
    const h = document.createElement('h2');
    h.textContent = TEXT.title;
    head.appendChild(h);
    const hint = document.createElement('p');
    hint.className = 'empty-sub';
    hint.textContent = TEXT.hint;
    const list = document.createElement('ul');
    list.id = 'acc-access-list';
    list.style.listStyle = 'none';
    list.style.margin = '8px 0 0';
    list.style.padding = '0';
    const empty = document.createElement('p');
    empty.className = 'empty-sub';
    empty.id = 'acc-access-empty';
    empty.textContent = TEXT.empty;
    empty.hidden = true;
    el.append(head, hint, list, empty);
    anchor.parentNode.insertBefore(el, anchor.nextSibling);
    return el;
  }

  let shownFor = '';
  function renderPanel(id, entries) {
    const el = panel();
    if (!el || id !== lastAccount) return;
    const list = el.querySelector('#acc-access-list');
    while (list.firstChild) list.removeChild(list.firstChild);   //  نه replaceChildren: سافاریِ آیفونِ کهنه (پیش از ۱۴)
    for (const e of Array.isArray(entries) ? entries : []) {
      const li = document.createElement('li');
      li.className = 'acc-access-row';
      li.style.padding = '6px 0';
      parts(e, localStamp).forEach((p, i) => {
        if (i) li.append(' · ');
        const s = document.createElement('span');
        if (p.raw) s.setAttribute('data-raw', '');
        s.textContent = p.text;
        li.appendChild(s);
      });
      list.appendChild(li);
    }
    el.querySelector('#acc-access-empty').hidden = list.childElementCount > 0;
    el.hidden = false;
    shownFor = id;
  }

  function loadPanel(id) {
    const el = panel();
    if (!el) return;
    if (shownFor !== id) el.hidden = true;
    if (!online() || !allows(shopState(), 'audit.view') || !noteBody(id, 'detail')) { el.hidden = true; shownFor = ''; return; }
    root.TohidShop.apiAuth(`${PATH}?customerId=${encodeURIComponent(id)}`)
      .then((r) => renderPanel(id, r && r.entries), () => { if (id === lastAccount) { el.hidden = true; shownFor = ''; } });
  }

  function maskList() {
    if (contactsVisible()) return;
    document.querySelectorAll('#debtor-list .debtor-meta span').forEach((s) => {
      const t = s.textContent || '';
      if (!t.includes(DOT)) s.textContent = mask(t);
    });
  }

  function wrap(name, after) {
    const orig = root[name];
    if (typeof orig !== 'function' || orig.__crmAccess) return;
    const fn = function (...args) {
      const out = orig.apply(this, args);
      try { after(args, out); } catch { /* صفحه هرگز نمی‌افتد */ }
      return out;
    };
    fn.__crmAccess = true;
    root[name] = fn;
  }

  let installed = false;
  function install() {
    if (installed || typeof document === 'undefined' || !document.getElementById('page-debtor-account')) return;
    installed = true;
    wrap('openAccount', ([id]) => {
      lastAccount = typeof id === 'string' ? id : '';
      note(lastAccount, 'detail').then(() => loadPanel(lastAccount));
    });
    wrap('openEditDebtor', ([id]) => {
      const input = document.getElementById('in-debtor-phone');
      const group = input && input.closest('.form-group');
      const has = !!(input && input.value && input.value.trim());
      const visible = contactsVisible();
      if (group) group.hidden = has && !visible;
      if (has && visible) note(id, 'phone');
    });
    wrap('openAddDebtor', () => {
      const input = document.getElementById('in-debtor-phone');
      const group = input && input.closest('.form-group');
      if (group) group.hidden = false;
    });
    wrap('renderDebtors', maskList);
    //  مجوزِ تازه از سرور (`shop-sync.js` ‹refreshShopInfo›): فهرستِ همین لحظه هم پوشیده شود (جست‌وجوی کاربر دست نمی‌خورد)
    root.addEventListener('tohid:shop-info', maskList);
    wrap('statementRows', ([id]) => { note(id, 'export'); });
    wrap('buildReportCsvRows', ([tab], out) => {
      if (tab !== 'debtors') return;
      note(ALL, 'export');
      if (contactsVisible() || !out || !Array.isArray(out.rows)) return;
      out.rows.slice(1).forEach((r) => { if (Array.isArray(r) && r[1]) r[1] = mask(r[1]); });
    });
    maskList();
  }

  return { PATH, ALL, WINDOW_MS, TEXT, mask, allows, noteBody, parts, note, install };
});
