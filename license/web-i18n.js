/**
 * زبانِ نسخهٔ وبِ دکان — فارسی (پیش‌فرض)، دری، انگلیسی و پشتو؛ همان چهار زبانِ برنامه (`core/i18n/I18n.kt`).
 *
 * `index.html` یخ‌زده است، پس این لایه متنِ صفحه را **روی خودِ صفحه** می‌گرداند، نه در کدِ آن:
 *   • فارسی = خودِ صفحه. هیچ واژه‌نامه‌ای بار نمی‌شود، هیچ ناظری نیست، هیچ تابعی پوشانده نمی‌شود — فارسی بایت‌به‌بایت همان دیروز.
 *   • انگلیسی و پشتو: `license/i18n/web.<زبان>.json` (کلید = همان متنِ فارسیِ صفحه، مثلِ `DariText.PHRASES`؛ `{0}` = جای خالی
 *     که مقدارش دست‌نخورده می‌ماند). فهرستِ همهٔ کلیدها `web.fa.json` است و آزمونِ `web-shop-i18n.test.js` هر سه را هم‌کلید و
 *     هر متنِ فارسیِ `index.html` و `license/*.js` را یا در آن یا «داده» می‌خواهد.
 *   • دری: همان کلیدهای فارسی از همان لایهٔ واژهٔ ویترین (`license/dari.js` = رونوشتِ `server/public/kit/dari.js` از
 *     `DariText.kt`)؛ متنی که کلید نیست هرگز به لایه نمی‌رسد.
 *   • ⛔ داده دست نمی‌خورد: فقط متنی که دقیقاً یکی از کلیدهاست عوض می‌شود؛ هر متنی که با نامِ کالا، واحد، دسته، مشتری،
 *     تأمین‌کننده، مصرف یا نامِ دکانِ همین دفتر یکی است (`dataSet`) همان‌طور که هست می‌ماند، و جای خالی همیشه همان مقدار است.
 *     کادرِ نوشتن (`input`، `textarea`، `contenteditable`) هرگز.
 *   • جهت: انگلیسی چپ‌به‌راست، بقیه راست‌به‌چپ. رقم: انگلیسی همیشه لاتین؛ بقیه همان انتخابِ دکان. ماه: پشتو همیشه وری، غویی…،
 *     انگلیسی Hamal، Sawr… (`I18n.solarMonths`)، فارسی و دری انتخابِ دکان.
 *   • فاکتور (پیش‌نمایش و چاپِ حرارتی): برچسب‌ها از `receipt.<زبان>.json` که از خودِ `StrReceipt.kt` ساخته می‌شود
 *     (`server/scripts/receipt-labels.js`)؛ `receipt.web.json` می‌گوید کدام برچسبِ صفحه کدام کلیدِ StrReceipt است.
 *
 * ⛔ CSP: نه اسکریپت یا رویدادِ درون‌خطی، نه `eval`، نه صفتِ `style` (فقط CSSOM)؛ همان `script-src 'self'`.
 * ⛔ زبان فقط روی همین مرورگر (`tohid-shop-lang-v1`)، مثلِ `LocaleChoice.lang` ِ برنامه؛ هیچ داده یا همگام‌سازی‌ای عوض نمی‌شود.
 * بارکننده: `shop-sync.js` (هیچ خطی به index.html اضافه نمی‌شود).
 */
(function (root) {
  'use strict';

  const LANGS = ['fa', 'prs', 'en', 'ps'];
  const NATIVE = { fa: 'فارسی', prs: 'دری', en: 'English', ps: 'پښتو' }; // داده: نامِ هر زبان به خودِ همان زبان (گزینش‌گر)
  const STORE = 'tohid-shop-lang-v1';
  const V = 1;
  const ATTRS = ['placeholder', 'title', 'aria-label', 'alt'];
  //  زیرشاخهٔ این‌ها هرگز (متنِ textarea همان مقدارِ کادر است و `SKIP_SEL` نگهش می‌دارد؛ placeholder ِ کادرها ترجمه می‌شود)
  const SKIP_TAGS = /^(SCRIPT|STYLE|NOSCRIPT|TEMPLATE)$/;
  const SKIP_SEL = 'script,style,textarea,noscript,template,[contenteditable],[data-wi-skip]';
  const AR = /[\u0600-\u06FF]/;
  /** حرف (نه رقم و نشانه) */
  const LETTER = /[\u0621-\u064A\u066E-\u06D3\u06D5\u06FA-\u06FF]/;
  const HOLE = /\{(\d+)\}/g;
  /** نامِ ماه‌های خورشیدی به انگلیسی — همان `I18n.SOLAR_MONTHS_EN` ِ برنامه */
  const MONTHS_EN = ['Hamal', 'Sawr', 'Jawza', 'Saratan', 'Asad', 'Sunbula', 'Mizan', 'Aqrab', 'Qaws', 'Jadi', 'Dalw', 'Hut'];

  const st = {
    lang: 'fa', on: false, mo: null, gen: 0,
    exact: null, pats: null, idx: null,           //  واژه‌نامهٔ صفحه
    receipt: null, rmap: null, ui: null, uiFa: null, //  برچسب‌های فاکتور، نقشهٔ صفحه ⇐ StrReceipt، متنِ گزینش‌گر
    orig: new WeakMap(), wrote: new WeakMap(),     //  متن: اصلِ فارسی / آنچه همین لایه نوشت
    aorig: new WeakMap(), awrote: new WeakMap(),   //  صفت‌ها: همان، برای هر نام
    data: null, dataSig: '', dataAt: 0,
    hooks: {}, dialogs: null, listeners: [], ready: Promise.resolve(),
  };

  const own = (o, k) => Object.prototype.hasOwnProperty.call(o, k);
  const norm = (s) => String(s).replace(/[\s\u00a0]+/g, ' ').trim();
  const reEsc = (s) => s.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');

  // ══════════════════════════════ زبان ══════════════════════════════

  function readLang() {
    try { const v = root.localStorage.getItem(STORE); return LANGS.includes(v) ? v : 'fa'; } catch { return 'fa'; }
  }
  function writeLang(l) {
    try { root.localStorage.setItem(STORE, l); } catch { /* حافظهٔ بسته: فقط همین بار */ }
  }
  const lang = () => st.lang;
  const rtl = (l = st.lang) => l !== 'en';
  /** رقمِ صفحه و کاغذ: انگلیسی همیشه لاتین (همان `I18n.screenDigits`)، بقیه انتخابِ دکان */
  function digits() {
    if (st.lang === 'en') return 'latn';
    const f = st.hooks.shopDigits || root.shopDigits;
    try { return typeof f === 'function' ? f() : 'latn'; } catch { return 'latn'; }
  }

  // ══════════════════════════════ بار کردن ══════════════════════════════

  async function json(name) {
    const r = await fetch(`license/i18n/${name}?v=${V}`, { cache: 'no-cache' });
    if (!r.ok) throw new Error(`${name}: ${r.status}`);
    return r.json();
  }
  function dariScript() {
    if (root.TohidDari) return Promise.resolve(root.TohidDari);
    return new Promise((ok, no) => {
      const s = document.createElement('script');
      s.src = `license/dari.js?v=${V}`;
      s.onload = () => (root.TohidDari ? ok(root.TohidDari) : no(new Error('dari.js')));
      s.onerror = () => no(new Error('dari.js'));
      document.head.appendChild(s);
    });
  }

  /** کلیدِ دارای `{n}` ⇒ الگو؛ نمایه با نخستین واژهٔ فارسیِ بلندترین تکهٔ ثابت */
  function words(s) {
    return s.split(/[\s\u00a0]+/).map((w) => w.replace(/[^\u0600-\u06FF\u200C\w]/g, '')).filter((w) => w && AR.test(w));
  }
  function compile(dict) {
    const exact = new Map();
    const pats = [];
    const idx = new Map();
    for (const k of Object.keys(dict)) {
      if (!/\{\d+\}/.test(k)) { exact.set(k, dict[k]); continue; }
      const parts = k.split(/\{\d+\}/);
      const order = [...k.matchAll(HOLE)].map((m) => Number(m[1]));
      const p = {
        k, v: dict[k], order, re: new RegExp(`^${parts.map(reEsc).join('(.+?)')}$`),
        lit: parts.join('').length, edge: [parts[0] === '' ? 0 : -1, parts[parts.length - 1] === '' ? order.length - 1 : -1],
      };
      pats.push(p);
      const lit = parts.slice().sort((a, b) => words(b).join('').length - words(a).join('').length)[0];
      const w = words(lit)[0];
      if (!w) continue;
      if (!idx.has(w)) idx.set(w, []);
      idx.get(w).push(p);
    }
    return { exact, pats, idx };
  }

  async function load(l) {
    const gen = ++st.gen;
    const page = l === 'prs' ? json('web.fa.json') : json(`web.${l}.json`);
    const [dict, receipt, rmap, ui, dari] = await Promise.all([
      page,
      json(`receipt.${l === 'prs' ? 'fa' : l}.json`),
      json('receipt.web.json'),
      json(`lang.${l === 'prs' ? 'fa' : l}.json`),
      l === 'prs' ? dariScript() : null,
    ]);
    if (gen !== st.gen) return false;
    //  دری: فهرستِ کلیدها، و متن از لایهٔ واژه هنگامِ نیاز
    const d = Array.isArray(dict) ? Object.fromEntries(dict.map((k) => [k, null])) : dict;
    Object.assign(st, compile(d));
    st.dari = dari;
    st.receipt = receipt;
    st.rmap = Object.fromEntries(Object.keys(rmap).filter((s) => !s.startsWith('_')).map((s) => [s, compile(rmap[s])]));
    st.ui = ui;
    return true;
  }

  // ══════════════════════════════ ترجمه ══════════════════════════════

  /** متنِ فارسیِ یک کلید (یا الگو) در زبانِ کنونی؛ دری از لایه، با جای‌خالیِ محفوظ */
  function value(k, v) {
    if (st.lang === 'prs') return st.dari ? st.dari.t(k) : k;
    return v == null || v === '' ? null : v;
  }
  function fill(tpl, order, got) {
    return tpl.replace(HOLE, (m, i) => {
      const at = order.indexOf(Number(i));
      return at < 0 ? m : got[at];
    });
  }
  /** کلیدِ دقیق یا الگو در یک واژه‌نامهٔ ساخته (`compile`) */
  /**
   *  الگو: کلیدی با ثابتِ بیشتر جلوتر است («مجموع {0} افغانی — قدیمی‌ترین {1} روز» پیش از «{0} افغانی»). ⛔ جای خالیِ لبه
   *  (سر یا تهِ الگو) فقط عدد/لاتین یا دادهٔ دفتر می‌گیرد، یا خودش کلیدِ صفحه است — وگرنه «{0} افغانی» نیمی از یک جملهٔ
   *  فارسیِ دیگر را می‌گرداند. جای خالی‌ای که خودش کلیدِ صفحه است (نه داده) یک لایه ترجمه می‌شود.
   */
  function lookup(c, key, resolve, depth = 0) {
    if (!c) return null;
    if (c.exact.has(key)) return resolve(key, c.exact.get(key));
    const cand = new Set();
    for (const w of words(key)) for (const p of c.idx.get(w) || []) cand.add(p);
    if (!cand.size) return null;
    const data = dataSet();
    for (const p of [...cand].sort((a, b) => b.lit - a.lit)) {
      const m = p.re.exec(key);
      if (!m) continue;
      const got = m.slice(1);
      let ok = true;
      for (let i = 0; i < got.length && ok; i++) {
        //  «/ کیلوگرم» یا «(نقد)»: نشانه‌های دو سر جزوِ مقدار نیستند
        const g = norm(got[i]).replace(/^[^\p{L}\p{N}]+|[^\p{L}\p{N}]+$/gu, '');
        if (!LETTER.test(g) || data.has(g)) continue;
        const inner = depth < 1 && c === st ? lookup(st, g, value, depth + 1) : null;
        if (inner != null) { got[i] = got[i].replace(g, inner); continue; }
        if (p.edge.includes(i)) ok = false;
      }
      if (!ok) continue;
      const tpl = resolve(p.k, p.v);
      if (tpl == null) return null;
      return fill(tpl, p.order, got);
    }
    return null;
  }
  /** «الف · ب»: اگر کلِ نوشته کلید نبود، هر تکه جدا (تکهٔ داده یا ناشناخته همان می‌ماند) */
  function pieces(key) {
    const parts = key.split(/\s+·\s+/);
    if (parts.length < 2) return null;
    let hit = false;
    const out = parts.map((x, i) => {
      //  «… · حافظهٔ این مرورگر: {0}٪ پر»: کلیدِ تکهٔ دوم خودش با «·» شروع می‌شود
      let t = lookup(st, x, value);
      if (t == null && i > 0) { const d = lookup(st, `· ${x}`, value); if (d != null) t = d.replace(/^·\s*/, ''); }
      if (t != null) hit = true;
      return t == null ? x : t;
    });
    return hit ? out.join(' · ') : null;
  }

  /** متنی که همین دفتر نگه می‌دارد (نامِ کالا، واحد، دسته، مشتری، …) — هرگز ترجمه نمی‌شود */
  function dataSet() {
    const now = Date.now();
    const lists = gather();
    const sig = lists.map((a) => (Array.isArray(a) ? a.length : 0)).join(',') + (st.storeName || '');
    if (st.data && sig === st.dataSig && now - st.dataAt < 2000) return st.data;
    const set = new Set();
    const put = (v) => { if (typeof v === 'string' && AR.test(v)) set.add(norm(v)); };
    const [products, debtors, suppliers, expenses, expenseCategories, productCategories, warehouse, returns] = lists;
    (products || []).forEach((p) => { if (p) { put(p.name); put(p.unit); put(p.category); put(p.brand); put(p.notes); } });
    (debtors || []).forEach((d) => { if (d) { put(d.name); put(d.notes); } });
    (suppliers || []).forEach((s) => { if (s) { put(s.name); put(s.notes); } });
    (expenses || []).forEach((e) => { if (e) { put(e.title); put(e.category); put(e.notes); } });
    (expenseCategories || []).forEach(put);
    (productCategories || []).forEach((c) => put(typeof c === 'string' ? c : c && c.name));
    (warehouse || []).forEach((w) => { if (w) { put(w.unit); put(w.notes); } });
    (returns || []).forEach((r) => { if (r) put(r.reason); });
    put(st.storeName);
    st.data = set; st.dataSig = sig; st.dataAt = now;
    return set;
  }
  /** آرایه‌های دفترِ صفحه (`let` ِ سطحِ بالای اسکریپتِ index.html، دیدنی برای اسکریپتِ کلاسیک) */
  function gather() {
    /* eslint-disable no-undef */
    const get = [
      () => products, () => debtors, () => suppliers, () => expenses,
      () => expenseCategories, () => productCategories, () => warehouseEntries, () => returns,
    ];
    /* eslint-enable no-undef */
    st.storeName = readStoreName() || '';
    return get.map((f) => { try { return f(); } catch { return null; } });
  }
  function readStoreName() {
    /* eslint-disable-next-line no-undef */
    try { return appSettings && appSettings.storeName; } catch { return ''; }
  }

  /**
   * یک نوشتهٔ فارسیِ صفحه ⇒ زبانِ کنونی؛ null یعنی «دست نزن» (کلید نیست، داده است، یا فارسی).
   * `ctx = 'receipt'`: درونِ پیش‌نمایشِ فاکتور فقط برچسب‌های `receipt.web.json` (+ سرِ جدول) — نامِ کالا و واحد داده‌اند.
   */
  function tx(raw, ctx) {
    if (st.lang === 'fa' || !st.exact || raw == null) return null;
    const s = String(raw);
    if (!AR.test(s)) return null;
    const key = norm(s);
    if (dataSet().has(key)) return null;
    let out = null;
    if (ctx === 'receipt' || ctx === 'receipt-head') {
      out = receiptText('preview', key);
      if (out == null && ctx === 'receipt-head') out = lookup(st, key, value);
    } else {
      out = lookup(st, key, value);
      if (out == null) out = pieces(key);
    }
    if (out == null || out === key) return null;
    const lead = /^[\s\u00a0]*/.exec(s)[0];
    const trail = /[\s\u00a0]*$/.exec(s)[0];
    return lead + out + trail;
  }

  /**
   * برچسبِ فاکتور: `receipt.web.json[section]` می‌گوید متنِ صفحه کدام کلیدِ StrReceipt است (`[key]`)، یا «همان واژه‌نامهٔ
   * صفحه» (`=`). فارسی ⇒ null (همان کاغذِ دیروز)؛ دری ⇒ همان متنِ صفحه از لایهٔ واژه؛ انگلیسی/پشتو ⇒ StrReceipt.
   */
  function receiptText(section, raw) {
    if (st.lang === 'fa' || !st.rmap || raw == null) return null;
    const key = norm(raw);
    const c = st.rmap[section];
    if (!c || dataSet().has(key)) return null;
    return lookup(c, key, (k, v) => {
      if (st.lang === 'prs') return st.dari ? st.dari.t(k) : null;
      if (v === '=') { const p = lookup(st, k, value); return p == null ? null : p; }
      return String(v).replace(/\[(\w+)\]/g, (m, n) => (st.receipt && own(st.receipt, n) ? st.receipt[n] : m));
    });
  }
  /** متنِ خودِ این لایه و چاپگر (`lang.<زبان>.json`) */
  const ui = (k) => (st.ui && own(st.ui, k) ? (st.lang === 'prs' && st.dari ? st.dari.t(st.ui[k]) : st.ui[k]) : k);

  // ══════════════════════════════ صفحه ══════════════════════════════

  function ctxOf(el) {
    const area = el.closest && el.closest('#invoice-print-area');
    if (!area) return null;
    return el.closest('thead, th') ? 'receipt-head' : 'receipt';
  }
  function skipped(el) { return !el || (el.closest ? !!el.closest(SKIP_SEL) : false); }

  function doText(n) {
    const cur = n.nodeValue;
    if (st.wrote.get(n) === cur) return;
    if (!cur || !AR.test(cur)) { st.wrote.delete(n); return; }
    const p = n.parentElement;
    if (skipped(p)) return;
    st.orig.set(n, cur);
    const out = tx(cur, ctxOf(p));
    if (out == null) { st.wrote.delete(n); return; }
    st.wrote.set(n, out);
    n.nodeValue = out;
  }
  function doAttr(el, a) {
    const cur = el.getAttribute(a);
    const w = st.awrote.get(el) || {};
    if (cur == null || w[a] === cur) return;
    if (!AR.test(cur)) { delete w[a]; return; }
    const o = st.aorig.get(el) || {};
    o[a] = cur;
    st.aorig.set(el, o);
    const out = tx(cur, ctxOf(el));
    if (out == null) { delete w[a]; st.awrote.set(el, w); return; }
    w[a] = out;
    st.awrote.set(el, w);
    el.setAttribute(a, out);
  }
  function doEl(el) { for (const a of ATTRS) if (el.hasAttribute(a)) doAttr(el, a); }

  function walk(node, fn = { text: doText, el: doEl }) {
    if (node.nodeType === 3) { fn.text(node); return; }
    if (node.nodeType !== 1 || SKIP_TAGS.test(node.nodeName) || (node.closest && node.closest('[data-wi-skip]'))) return;
    fn.el(node);
    if (skipped(node)) return;
    const w = document.createTreeWalker(node, NodeFilter.SHOW_ELEMENT | NodeFilter.SHOW_TEXT, {
      acceptNode: (n) => (n.nodeType === 1 && (SKIP_TAGS.test(n.nodeName) || n.isContentEditable || n.hasAttribute('data-wi-skip'))
        ? NodeFilter.FILTER_REJECT : NodeFilter.FILTER_ACCEPT),
    });
    for (let n = w.nextNode(); n; n = w.nextNode()) { if (n.nodeType === 3) fn.text(n); else fn.el(n); }
  }

  /** هر چه این لایه نوشته بود، به همان فارسیِ صفحه برمی‌گردد */
  function restore() {
    const back = {
      text: (n) => { if (st.wrote.get(n) === n.nodeValue && st.orig.has(n)) n.nodeValue = st.orig.get(n); st.wrote.delete(n); },
      el: (el) => {
        const w = st.awrote.get(el);
        const o = st.aorig.get(el);
        if (!w || !o) return;
        for (const a of Object.keys(w)) if (el.getAttribute(a) === w[a] && own(o, a)) el.setAttribute(a, o[a]);
        st.awrote.delete(el);
      },
    };
    walk(document.documentElement, back);
  }

  function observe() {
    if (st.mo) return;
    st.mo = new MutationObserver((list) => {
      for (const m of list) {
        if (m.type === 'childList') {
          m.addedNodes.forEach((n) => { if (n.nodeType === 3 ? !skipped(n.parentElement) : true) walk(n); });
        } else if (m.type === 'characterData') {
          doText(m.target);
        } else if (m.type === 'attributes' && ATTRS.includes(m.attributeName) && !skipped(m.target)) {
          doAttr(m.target, m.attributeName);
        }
      }
    });
    st.mo.observe(document.documentElement, { subtree: true, childList: true, characterData: true, attributes: true, attributeFilter: ATTRS });
  }
  function unobserve() {
    if (!st.mo) return;
    st.mo.takeRecords();
    st.mo.disconnect();
    st.mo = null;
  }

  // ══════════════════════════════ پیوند با صفحه ══════════════════════════════

  /** پیام‌های بومیِ مرورگر (`alert`/`confirm`/`prompt`) — فقط وقتی زبان فارسی نیست */
  function wrapDialogs(on) {
    if (on && !st.dialogs) {
      const nat = { alert: root.alert, confirm: root.confirm, prompt: root.prompt };
      st.dialogs = nat;
      const m = (x) => (x == null ? x : String(x).split('\n').map((l) => tx(l) ?? l).join('\n'));
      root.alert = function (x) { return nat.alert.call(root, m(x)); };
      root.confirm = function (x) { return nat.confirm.call(root, m(x)); };
      root.prompt = function (x, d) { return nat.prompt.call(root, m(x), d); };
    } else if (!on && st.dialogs) {
      Object.assign(root, st.dialogs);
      st.dialogs = null;
    }
  }

  /** تابع‌های سراسریِ صفحه که رقم و نامِ ماه را می‌دهند (اعلانِ `function` ِ سطحِ بالا = ویژگیِ window) */
  function hook(name, fn) {
    if (typeof root[name] !== 'function') return;
    if (!own(st.hooks, name)) st.hooks[name] = root[name];
    root[name] = fn;
  }
  function unhook() {
    for (const k of Object.keys(st.hooks)) root[k] = st.hooks[k];
    st.hooks = {};
  }
  function pageHooks(l) {
    unhook();
    if (l === 'en') hook('shopDigits', () => 'latn');
    if (l === 'en' || l === 'ps') {
      const orig = root.applyMonthNames;
      hook('applyMonthNames', () => {
        if (l === 'ps') return orig('ps');
        orig('fa');
        /* eslint-disable-next-line no-undef */
        try { MONTHS_EN.forEach((n, i) => { JALALI_MONTHS[i] = n; }); } catch { /* صفحهٔ دیگر */ }
        const rep = document.getElementById('rep-sales-jmonth');
        if (rep) Array.from(rep.options).forEach((o, i) => { if (MONTHS_EN[i]) o.textContent = MONTHS_EN[i]; });
        return undefined;
      });
    }
    try { if (typeof root.applyLocaleChoice === 'function') root.applyLocaleChoice(); } catch { /* هنوز نیامده */ }
    if (l === 'en') document.documentElement.removeAttribute('data-digits');
  }
  function rerender() {
    const page = document.querySelector('.page.active');
    const id = page && page.id ? page.id.replace(/^page-/, '') : '';
    try { if (id && typeof root.goTo === 'function') root.goTo(id); } catch { /* صفحه هنوز آماده نیست */ }
  }

  // ══════════════════════════════ گزینش‌گر ══════════════════════════════

  function picker() {
    const anchor = document.getElementById('digits-toggle');
    const panel = anchor && anchor.closest('.panel');
    if (!panel) return;
    let box = document.getElementById('wi-lang-panel');
    if (!box) {
      box = document.createElement('div');
      box.className = 'panel';
      box.id = 'wi-lang-panel';
      box.setAttribute('data-wi-skip', '');
      const head = document.createElement('div');
      head.className = 'panel-head';
      const h = document.createElement('h2');
      h.id = 'wi-lang-title';
      head.appendChild(h);
      const hint = document.createElement('p');
      hint.className = 'settings-hint';
      hint.id = 'wi-lang-hint';
      const row = document.createElement('div');
      row.className = 'theme-toggle';
      row.id = 'lang-toggle';
      row.setAttribute('role', 'radiogroup');
      row.style.marginTop = '10px';
      row.style.flexWrap = 'wrap';
      for (const l of LANGS) {
        const b = document.createElement('button');
        b.type = 'button';
        b.className = 'theme-toggle-btn';
        b.dataset.langChoice = l;
        b.lang = l;
        b.dir = rtl(l) ? 'rtl' : 'ltr';
        b.setAttribute('role', 'radio');
        b.textContent = NATIVE[l];
        row.appendChild(b);
      }
      row.addEventListener('click', (e) => {
        const b = e.target.closest('[data-lang-choice]');
        if (b) setLang(b.dataset.langChoice);
      });
      box.append(head, hint, row);
      panel.parentNode.insertBefore(box, panel);
    }
    const title = document.getElementById('wi-lang-title');
    const hint = document.getElementById('wi-lang-hint');
    if (title) title.textContent = ui('title');
    if (hint) hint.textContent = ui('hint');
    box.querySelectorAll('[data-lang-choice]').forEach((b) => {
      const on = b.dataset.langChoice === st.lang;
      b.classList.toggle('active', on);
      b.setAttribute('aria-checked', on ? 'true' : 'false');
    });
  }

  // ══════════════════════════════ اجرا ══════════════════════════════

  function setHtml(l) {
    const d = document.documentElement;
    d.lang = l;
    d.dir = rtl(l) ? 'rtl' : 'ltr';
  }

  /**
   *  `initial`: بارِ نخستِ صفحه. فارسی در بارِ نخست هیچ کاری با صفحه نمی‌کند (نه hook، نه رسمِ دوباره، نه lang/dir) جز
   *  ساختنِ گزینش‌گر در تنظیمات؛ برگشت از زبانِ دیگر به فارسی همه را به همان حالِ صفحه برمی‌گرداند.
   */
  async function apply(l, initial = false) {
    const was = st.lang;
    unobserve();
    if (st.on) restore();
    st.on = false;
    wrapDialogs(false);
    st.lang = l;
    if (l === 'fa') {
      st.exact = null; st.pats = null; st.idx = null; st.rmap = null;
      ++st.gen;
      if (!initial || was !== 'fa') { setHtml('fa'); pageHooks('fa'); }
      if (!st.uiFa) { try { st.uiFa = await json('lang.fa.json'); } catch { st.uiFa = null; } }
      st.ui = st.uiFa;
    } else {
      setHtml(l);
      let ok = false;
      try { ok = await load(l); } catch (e) { console.warn('[web-i18n]', e && e.message); }
      if (st.lang !== l) return;
      if (!ok) { st.lang = 'fa'; setHtml('fa'); pageHooks('fa'); picker(); return; }
      pageHooks(l);
      walk(document.documentElement);
      observe();
      wrapDialogs(true);
      st.on = true;
    }
    picker();
    if (!initial || l !== 'fa') rerender();
    st.listeners.forEach((f) => { try { f(l); } catch { /* شنونده */ } });
    root.dispatchEvent(new CustomEvent('tohid:lang', { detail: { lang: l } }));
  }

  function setLang(l) {
    const v = LANGS.includes(l) ? l : 'fa';
    writeLang(v);
    if (v === st.lang && (v === 'fa' || st.on)) { picker(); return st.ready; }
    st.ready = apply(v);
    return st.ready;
  }

  root.TohidWebI18n = {
    LANGS, lang, rtl, digits, setLang, tx, receiptText, ui,
    ready: () => st.ready,
    onChange: (f) => { if (typeof f === 'function') st.listeners.push(f); },
  };

  const start = () => { st.ready = apply(readLang(), true); };
  if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', start, { once: true });
  else start();
})(typeof window !== 'undefined' ? window : this);
