/**
 * POS-04 — جست‌وجوی مقاومِ نامِ کالا در کادرِ صندوقِ نسخهٔ وبِ دکان؛ همزادِ `core/text/CatalogMatch.kt` ِ صندوقِ کامپیوتر
 * (بردارِ مشترکِ `core/test-vectors/catalog-match-v1.json`؛ `CatalogMatchVectorsTest` ِ Kotlin همان را می‌خواند). یک‌شکل کردنِ
 * متن فقط از هستهٔ مشترکِ `license/text-core.js` (همتای `TextNormalizer.kt`): «ي/ی»، «ك/ک»، «ۀ/ه»، «أ/ا»، رقمِ فارسی/عربی،
 * نیم‌فاصله، اعراب و کشیده؛ هر کلمه به هر ترتیب؛ غلطِ تایپی فقط وقتی جورِ دقیق کمتر از ۸ است. ترتیب: بارکدِ کامل ⇐ آغازِ نام ⇐
 * همهٔ کلمه‌ها ⇐ آغازِ بارکد ⇐ غلطِ تایپی، هر گروه به ترتیبِ کلیدِ نام و سپس شناسه (UTF-16، همان `compareTo` ِ JVM).
 * مترادف را خودِ هسته می‌پذیرد ولی کادرِ وب هیچ فرهنگی نمی‌دهد (تصمیمِ صاحب‌کار، OWNER-PENDING 4-1).
 *
 * ⛔ پیش‌فرض خاموش (تنظیمِ همین مرورگر، `tohid-pos-name-search-v1`؛ «تنظیمات» ⇐ «دوربین بارکدخوان»). خاموش ⇒ هیچ شنونده‌ای
 *    کاری نمی‌کند و کادر بایت‌به‌بایت همان دیروز (نامِ کالا + Enter ⇒ «بارکد ناشناس»، پنجرهٔ «محصول جدید»).
 * روشن:
 *   • بارکدِ کامل (همان `barcodeIndex` ِ صفحه، رقمِ فارسی هم) همیشه اول و همان راهِ امروزِ صفحه (`handleBarcodeScanned`)؛
 *     با پیشوندِ روشن («۳*کد») همان `cart-query.js`. فهرستِ نام برای متنی که بارکد است اصلاً باز نمی‌شود.
 *   • وگرنه زیرِ کادر فهرستِ کالاهای فعال (حداکثر ۸، listbox ِ ARIA): ↑ ↓ برجسته را جابه‌جا می‌کند، Enter یا «جستجو» برجسته
 *     (پیش‌فرض نخستین، همان `hint = 0` ِ کامپیوتر) را برمی‌دارد، لمس یا کلیک هر ردیف همان را، Esc فهرست را می‌بندد.
 *     شمار همان «هر اسکن: ×N» ِ صفحه؛ با پیشوندِ روشن «۳*نام» ⇒ سه تا (اعشار برای کالای شمردنی ⇒ همان `quantityProblem`).
 *   • هیچ جورِ نامی ⇒ همان راهِ امروز (بارکدِ ناشناس ⇒ «محصول جدید»).
 *   • افزودن با `addToCart` ِ خودِ صفحه (همان راهِ کاشیِ «انتخاب محصول»؛ کالای بی بارکد بارکدی برای `handleBarcodeScanned` ندارد)،
 *     سپس همان کارهای اسکنِ کالای شناخته‌شده: بوق، برجسته شدنِ ردیفِ سبد و پیامِ «… به سبد اضافه شد».
 *   • این دستگاه بارکدخوانِ کامپیوتر است (`TohidPhotoStudio.scanner`) ⇒ همان راهِ امروز (سبد روی کامپیوتر است).
 *   • نمایه فقط با تغییرِ کالاها از نو ساخته می‌شود (`rebuildBarcodeIndex` ِ صفحه، یا فهرستِ تازه)، نه با هر حرف.
 * `index.html` یخ‌زده است ⇒ بارکننده `shop-sync.js`. ⛔ CSP: بی innerHTML/صفتِ style/eval؛ متن فقط کلیدهای واژه‌نامهٔ صفحه.
 * آزمون: `server/test/web-catalog-match.test.js` (بردار و تصمیم) و `server/test/web-pos-name.mjs` (صفحه، axe).
 */
(function (root, factory) {
  'use strict';
  const api = factory(root);
  if (typeof module !== 'undefined' && module.exports) module.exports = api;
  if (typeof window !== 'undefined' && root === window) {
    root.TohidCatalogMatch = api;
    api.install();
  }
})(typeof window !== 'undefined' ? window : globalThis, function (root) {
  'use strict';

  const PREF = 'tohid-pos-name-search-v1';
  const QTY_PREF = 'tohid-pos-qty-prefix-v1';   //  همان `PREF` ِ cart-query.js
  const LIST_ID = 'pos-name-results';
  const MAX = 8;
  /** متن — کلیدِ واژه‌نامهٔ صفحه (`license/i18n/web.*.json`) */
  const TEXT = Object.freeze({
    label: 'جست‌وجوی نامِ کالا در کادرِ فروش',
    hint: 'در کادرِ فروش نامِ کالا را هم می‌شود نوشت: بارکدِ کامل اول است، سپس کالاهایی که نامشان جور است («ي» و «ی»، «ك» و «ک»، رقمِ فارسی و نیم‌فاصله یکی‌اند؛ غلطِ کوچکِ تایپی هم). با ↑ ↓ و Enter یا با لمس انتخاب کنید. فقط روی همین دستگاه.',
    list: 'کالاهایی که نامشان جور است',
    placeholder: 'بارکد یا نامِ کالا را بنویسید',
    placeholderOff: 'یا بارکد را دستی وارد کنید',
    added: '{0} به سبد اضافه شد',
    price: '{0} افغانی',
  });

  /* ---------------- هسته: همزادِ CatalogMatch.kt ---------------- */

  /** هستهٔ متن (`text-core.js` = `TextNormalizer.kt`)؛ در مرورگر همان `TohidText` ِ صفحه */
  function text() {
    if (root.TohidText) return root.TohidText;
    if (typeof require === 'function') return require('./text-core.js');
    throw new Error('license/text-core.js is not loaded');
  }

  /** `Char.isWhitespace` ِ Kotlin روی JVM (`Character.isWhitespace || isSpaceChar`) — همان `String.trim()` ِ هسته */
  function ktSpace(c) {
    return (c >= 0x09 && c <= 0x0D) || (c >= 0x1C && c <= 0x20) || c === 0xA0 || c === 0x1680 ||
      (c >= 0x2000 && c <= 0x200A) || c === 0x2028 || c === 0x2029 || c === 0x202F || c === 0x205F || c === 0x3000;
  }
  function ktTrim(s) {
    let a = 0;
    let b = s.length;
    while (a < b && ktSpace(s.charCodeAt(a))) a++;
    while (b > a && ktSpace(s.charCodeAt(b - 1))) b--;
    return s.slice(a, b);
  }
  /** `compareTo` ِ رشته در JVM: واحدِ UTF-16 به واحد (نه `localeCompare`) */
  const cmp = (a, b) => (a < b ? -1 : a > b ? 1 : 0);
  const uniq = (xs) => [...new Set(xs)];
  const list = (x) => (Array.isArray(x) ? x.map((v) => String(v == null ? '' : v)) : []);

  /** یک بار با هر تغییرِ کاتالوگ: `{id, name, barcodes?, aliases?, keywords?}` ⇒ نمایه (همان `CatalogMatch.index`) */
  function index(items) {
    const T = text();
    const entries = (Array.isArray(items) ? items : []).map((item) => {
      const name = String(item.name == null ? '' : item.name);
      const aliases = list(item.aliases);
      const keywords = list(item.keywords);
      return {
        id: String(item.id),
        nameKey: T.compareKey(name),
        words: uniq([name, ...aliases, ...keywords].flatMap((t) => T.searchKey(t).split(' '))).filter((w) => w.length > 0),
        codes: list(item.barcodes).map((c) => T.compareKey(c)).filter((c) => c.length > 0),
        aliasKeys: aliases.map((c) => T.compareKey(c)).filter((c) => c.length > 0),
        keywordKeys: keywords.map((c) => T.compareKey(c)).filter((c) => c.length > 0),
      };
    });
    entries.sort((a, b) => cmp(a.nameKey, b.nameKey) || cmp(a.id, b.id));
    return { entries, size: entries.length };
  }

  /** کلید در نام، نامِ دیگر یا واژه هست؟ — هر متن جدا */
  const has = (e, k) => e.nameKey.includes(k) || e.aliasKeys.some((x) => x.includes(k)) || e.keywordKeys.some((x) => x.includes(k));
  const starts = (e, k) => e.nameKey.startsWith(k) || e.aliasKeys.some((x) => x.startsWith(k));

  /** گروهِ مترادف‌های یک کلید (خودش هم هست) */
  function alternatives(key, groups) {
    if (!groups.length) return [key];
    const out = [key];
    for (const g of groups) if (g.includes(key)) for (const k of g) if (!out.includes(k)) out.push(k);
    return out;
  }

  /** همان `CatalogMatch.distance`: Levenshtein روی کلیدهای آماده؛ بیش از max ⇒ max+1، زود دست می‌کشد */
  function distance(x, y, max) {
    if (Math.abs(x.length - y.length) > max) return max + 1;
    let prev = [];
    let cur = [];
    for (let j = 0; j <= y.length; j++) prev[j] = j;
    for (let i = 1; i <= x.length; i++) {
      cur[0] = i;
      let best = i;
      for (let j = 1; j <= y.length; j++) {
        const cost = x.charCodeAt(i - 1) === y.charCodeAt(j - 1) ? 0 : 1;
        const v = Math.min(prev[j] + 1, cur[j - 1] + 1, prev[j - 1] + cost);
        cur[j] = v;
        if (v < best) best = v;
      }
      if (best > max) return max + 1;
      const t = prev; prev = cur; cur = t;
    }
    return prev[y.length] > max ? max + 1 : prev[y.length];
  }

  function fuzzy(e, tokens, near, T) {
    return tokens.every((t, i) => {
      if (has(e, t)) return true;
      const budget = T.typoBudget(t.length);
      return budget > 0 && e.words.some((w) => {
        let v = near[i].get(w);
        if (v === undefined) {
          v = Math.abs(w.length - t.length) <= budget && distance(w, t, budget) <= budget;
          near[i].set(w, v);
        }
        return v;
      });
    });
  }

  /** شناسه‌های جورشده به ترتیبِ نمایش؛ پرس‌وجوی خالی ⇒ هیچ (همان `CatalogMatch.search`) */
  function search(ix, query, synonyms, fuzzyBelow) {
    const T = text();
    const below = fuzzyBelow === undefined ? 8 : fuzzyBelow;
    const q = ktTrim(T.searchKey(query == null ? '' : String(query)));
    if (!q || !ix || !Array.isArray(ix.entries)) return [];
    const whole = q.split(' ').join('');
    const tokens = q.split(' ').filter((t) => t.length > 0);
    const groups = (Array.isArray(synonyms) ? synonyms : []).map((g) => list(g).map((k) => T.compareKey(k)).filter((k) => k.length > 0));
    const wholeAlt = alternatives(whole, groups);
    const tokenAlt = tokens.map((t) => alternatives(t, groups));
    const code = [];
    const prefix = [];
    const words = [];
    const codePrefix = [];
    const rest = [];
    for (const e of ix.entries) {
      if (e.codes.some((c) => c === whole)) code.push(e.id);
      else if (starts(e, whole)) prefix.push(e.id);
      else if (wholeAlt.some((k) => has(e, k)) || tokenAlt.every((alt) => alt.some((k) => has(e, k)))) words.push(e.id);
      else if (e.codes.some((c) => c.startsWith(whole))) codePrefix.push(e.id);
      else rest.push(e);
    }
    const exact = [...code, ...prefix, ...words, ...codePrefix];
    if (exact.length >= below) return exact;
    const near = tokens.map(() => new Map());
    return exact.concat(rest.filter((e) => fuzzy(e, tokens, near, T)).map((e) => e.id));
  }

  /* ---------------- کادرِ صندوق ---------------- */

  const st = { installed: false, list: null, box: null, items: [], active: -1, shown: null, open: false, ix: null, ver: 0 };

  const read = (k) => { try { return root.localStorage.getItem(k) === '1'; } catch (e) { return false; } };
  const on = () => read(PREF);
  function setOn(v) {
    try { if (v) root.localStorage.setItem(PREF, '1'); else root.localStorage.removeItem(PREF); } catch (e) { /* حافظهٔ بسته */ }
  }
  const manual = () => document.getElementById('in-manual-barcode');
  const toast = (msg, kind) => { if (typeof root.showToast === 'function') root.showToast(msg, kind); };
  /** این دستگاه بارکدخوانِ کامپیوتر است ⇒ همان راهِ امروز */
  function forwarding() {
    const s = root.TohidPhotoStudio && root.TohidPhotoStudio.scanner;
    return !!(s && s.sendCode);
  }

  /** کالای همان بارکدِ کامل — همان `barcodeIndex` و یک‌شکل کردنِ رقمِ `handleBarcodeScanned` ِ صفحه (غیرفعال هم: پیامش با صفحه) */
  function isBarcode(textIn) {
    try {
      const raw = String(textIn || '').trim();
      const code = typeof root.normalizeDigits === 'function' ? root.normalizeDigits(raw) : raw;
      /* eslint-disable-next-line no-undef */
      return !!(barcodeIndex.get(code) || barcodeIndex.get(raw));
    } catch (e) {
      return false;
    }
  }

  /** «هر اسکن: ×N» ِ صفحه */
  function multiplier() {
    try {
      /* eslint-disable-next-line no-undef */
      const n = Number(scanMultiplier);
      return n > 0 ? n : 1;
    } catch (e) {
      return 1;
    }
  }

  /**
   *  متنِ کادر ⇒ `{count, term}` برای جست‌وجوی نام، یا `null` = کارِ صفحه یا cart-query.js (کادرِ خالی، بارکدِ کامل، «۳*کد» با
   *  بارکد، «۳*» ِ تنها یا شمارِ نادرست). پیشوند فقط با تنظیمِ روشنِ خودش (همان `TohidCartQuery.parse`).
   */
  function query(raw, isCode) {
    const s = typeof raw === 'string' ? raw : '';
    if (!s.trim() || isCode(s)) return null;
    const CQ = root.TohidCartQuery;
    if (read(QTY_PREF) && CQ && typeof CQ.parse === 'function') {
      const p = CQ.parse(s);
      if (p.term.length !== s.length) {
        const term = p.term.trim();
        if (!term || isCode(term) || !(p.count > 0) || !Number.isFinite(p.count)) return null;
        return { count: p.count, term };
      }
    }
    return { count: multiplier(), term: s.trim() };
  }

  /** نمایهٔ کالاهای فعالِ صفحه — فقط با فهرستِ تازه یا `rebuildBarcodeIndex` از نو */
  function catalog() {
    let all;
    try {
      /* eslint-disable-next-line no-undef */
      all = products;
    } catch (e) {
      return null;
    }
    if (!Array.isArray(all)) return null;
    const c = st.ix;
    if (c && c.from === all && c.n === all.length && c.ver === st.ver) return c;
    const live = all.filter((p) => p && p.id != null && p.active !== false);
    st.ix = {
      from: all, n: all.length, ver: st.ver,
      index: index(live.map((p) => ({ id: p.id, name: p.name, barcodes: (p.barcodes || []).filter(Boolean) }))),
      byId: new Map(live.map((p) => [String(p.id), p])),
    };
    return st.ix;
  }

  /** کالاهای جورشده برای متنِ کادر (نه بیش از ۸) */
  function results(raw) {
    const q = query(raw, isBarcode);
    const c = q && catalog();
    if (!c) return { q, items: [] };
    const items = [];
    for (const id of search(c.index, q.term)) {
      const p = c.byId.get(id);
      if (p) items.push(p);
      if (items.length >= MAX) break;
    }
    return { q, items };
  }

  function hide() {
    st.items = [];
    st.active = -1;
    st.shown = null;
    st.open = false;
    paint();
  }

  function ensureList() {
    if (st.list && st.list.isConnected) return st.list;
    const input = manual();
    const row = input && typeof input.closest === 'function' ? input.closest('.manual-scan-row') : null;
    if (!row) return null;
    const ul = document.createElement('ul');
    ul.id = LIST_ID;
    ul.setAttribute('role', 'listbox');
    ul.setAttribute('aria-label', TEXT.list);
    //  روی صفحه شناور (نه در جریان): باز و بسته شدن چیزی را زیرِ انگشت یا موس جابه‌جا نمی‌کند
    row.style.position = 'relative';
    Object.assign(ul.style, {
      display: 'none', position: 'absolute', top: 'calc(100% + 4px)', left: '0', right: '0', zIndex: '16',
      maxHeight: 'min(50vh, 380px)', overflowY: 'auto', listStyle: 'none', margin: '0', padding: '4px',
      border: '1px solid var(--border)', borderRadius: 'var(--radius-sm)', background: 'var(--surface-solid)', boxShadow: 'var(--shadow-md)',
    });
    //  کلیکِ ردیف تمرکز را از کادر نمی‌گیرد (پس فهرست با blur بسته نمی‌شود و Enter ِ بعدی همان‌جاست)
    ul.addEventListener('mousedown', (e) => e.preventDefault());
    ul.addEventListener('click', onPick);
    row.append(ul);
    st.list = ul;
    return ul;
  }

  function option(p, i) {
    const li = document.createElement('li');
    li.id = `${LIST_ID}-${i}`;
    li.setAttribute('role', 'option');
    li.setAttribute('aria-selected', i === st.active ? 'true' : 'false');
    li.dataset.pick = String(i);
    Object.assign(li.style, {
      display: 'flex', justifyContent: 'space-between', alignItems: 'center', gap: '12px', minHeight: '44px',
      padding: '6px 10px', borderRadius: 'var(--radius-sm)', cursor: 'pointer', color: 'var(--text)',
      background: i === st.active ? 'var(--primary-tint)' : 'transparent',
      outline: i === st.active ? '2px solid var(--primary)' : 'none', outlineOffset: '-2px',
    });
    const name = document.createElement('span');
    name.textContent = String(p.name || '');
    name.setAttribute('data-raw', '');   //  نامِ کالا داده است و ترجمه نمی‌شود
    const money = typeof root.formatMoney === 'function' ? root.formatMoney(p.salePrice || 0) : String(p.salePrice || 0);
    const price = document.createElement('span');
    price.textContent = TEXT.price.replace('{0}', money);
    price.style.color = 'var(--muted)';
    price.style.whiteSpace = 'nowrap';
    li.append(name, price);
    return li;
  }

  /** فهرست و صفت‌های combobox ِ کادر را با `st` هم‌خوان می‌کند */
  function paint() {
    const input = manual();
    const ul = st.list && st.list.isConnected ? st.list : (st.items.length ? ensureList() : null);
    const open = st.open && st.items.length > 0;
    if (ul) {
      ul.replaceChildren(...st.items.map(option));
      ul.style.display = open ? 'block' : 'none';
    }
    if (!input || input.getAttribute('role') !== 'combobox') return;
    input.setAttribute('aria-expanded', open ? 'true' : 'false');
    if (open && st.active >= 0) input.setAttribute('aria-activedescendant', `${LIST_ID}-${st.active}`);
    else input.removeAttribute('aria-activedescendant');
  }

  /** هر نوشتن در کادر */
  function render() {
    const input = manual();
    if (!input || !on() || forwarding()) { if (st.items.length) hide(); return; }
    const raw = input.value;
    const { items } = results(raw);
    st.items = items;
    st.active = items.length ? 0 : -1;
    st.shown = raw;
    st.open = items.length > 0;
    paint();
  }

  function move(step) {
    if (!st.items.length) return;
    st.open = true;
    st.active = (st.active + step + st.items.length) % st.items.length;
    paint();
    const li = document.getElementById(`${LIST_ID}-${st.active}`);
    if (li && typeof li.scrollIntoView === 'function') li.scrollIntoView({ block: 'nearest' });
  }

  function onKey(e) {
    if (!on() || !st.items.length) return;
    if (e.key === 'Escape' && !st.open) return;
    if (e.key === 'ArrowDown') { e.preventDefault(); move(1); } else if (e.key === 'ArrowUp') { e.preventDefault(); move(-1); } else if (e.key === 'Escape') {
      e.preventDefault();
      e.stopPropagation();
      st.open = false;   //  ↓ دوباره بازش می‌کند
      paint();
    }
  }

  /** همان افزودنِ اسکنِ کالای شناخته‌شده (بوق، ردیفِ برجستهٔ سبد، پیام)، با `addToCart` ِ صفحه */
  function add(p, count) {
    const input = manual();
    if (!p || p.active === false || typeof root.addToCart !== 'function') return;
    if (typeof root.quantityProblem === 'function') {
      const why = root.quantityProblem(p, count);
      if (why) { toast(why, 'danger'); return; }   //  متن می‌ماند تا درست شود
    }
    if (typeof root.playScanBeep === 'function') root.playScanBeep();
    root.addToCart(p.id, count);
    try {
      /* eslint-disable-next-line no-undef */
      lastScannedProductId = p.id;
      /* eslint-disable-next-line no-undef */
      selectedCartId = p.id;
    } catch (e) { /* صفحهٔ دیگر */ }
    if (typeof root.renderCart === 'function') root.renderCart();
    toast(TEXT.added.replace('{0}', String(p.name || '')));
    if (root.navigator && typeof root.navigator.vibrate === 'function') root.navigator.vibrate(60);
    hide();
    if (input) { input.value = ''; input.focus(); }
  }

  /** «جستجو» یا Enter ِ کادر (صفحه Enter را به همین دکمه می‌دهد) — پیش از cart-query.js و دستگیرهٔ صفحه */
  function onLookup(e) {
    const t = e.target;
    if (!on() || !t || typeof t.closest !== 'function' || !t.closest('#btn-manual-lookup') || forwarding()) return;
    const input = manual();
    if (!input) return;
    const raw = input.value;
    const { q, items } = results(raw);
    if (!q || !items.length) { hide(); return; }   //  بارکدِ کامل، پیشوند یا بی جور ⇒ همان راهِ امروز
    const i = st.shown === raw && st.active >= 0 && st.active < items.length ? st.active : 0;
    e.preventDefault();
    e.stopImmediatePropagation();
    add(items[i], q.count);
  }

  function onPick(e) {
    const li = e.target && typeof e.target.closest === 'function' ? e.target.closest('[data-pick]') : null;
    const input = manual();
    if (!li || !input || !on()) return;
    const p = st.items[Number(li.dataset.pick)];
    const q = query(input.value, isBarcode);
    if (p && q) add(p, q.count);
  }

  /** صفت‌های کادر: روشن ⇒ combobox با صفحه‌کلیدِ حرف؛ خاموش ⇒ همان صفت‌های دیروز */
  function apply() {
    const input = manual();
    if (!input) return;
    if (on()) {
      input.setAttribute('role', 'combobox');
      input.setAttribute('aria-autocomplete', 'list');
      input.setAttribute('aria-controls', LIST_ID);
      input.setAttribute('autocomplete', 'off');
      input.setAttribute('placeholder', TEXT.placeholder);
      if (input.inputMode === 'numeric' || input.inputMode === 'tel') input.inputMode = 'text';
      Object.assign(input.style, { unicodeBidi: 'plaintext', textAlign: 'start' });
      ensureList();
      paint();
      return;
    }
    if (input.getAttribute('role') !== 'combobox') return;
    hide();
    if (st.list) {
      if (st.list.parentElement) st.list.parentElement.style.position = '';
      st.list.remove();
      st.list = null;
    }
    for (const a of ['role', 'aria-autocomplete', 'aria-controls', 'aria-expanded', 'aria-activedescendant', 'autocomplete']) input.removeAttribute(a);
    input.setAttribute('placeholder', TEXT.placeholderOff);
    if (input.inputMode === 'text') input.inputMode = read(QTY_PREF) ? 'tel' : 'numeric';
    Object.assign(input.style, { unicodeBidi: '', textAlign: '' });
  }

  /** کلیدِ تنظیم، یک بار، در «دوربین بارکدخوان» ِ تنظیمات */
  function mount() {
    if (st.box && st.box.isConnected) return;
    const badge = document.getElementById('settings-camera-support-badge');
    const panel = badge && typeof badge.closest === 'function' ? badge.closest('.panel') : null;
    if (!panel) return;
    const el = (tag, props) => Object.assign(document.createElement(tag), props);
    const input = el('input', { type: 'checkbox', id: 'in-pos-name-search', checked: on() });
    input.setAttribute('aria-describedby', 'pos-name-search-hint');
    const label = el('label', { className: 'check-row' });
    label.append(input, document.createTextNode(TEXT.label));
    Object.assign(label.style, { display: 'flex', gap: '8px', alignItems: 'center', marginTop: '14px' });
    const hint = el('p', { className: 'settings-hint', id: 'pos-name-search-hint', textContent: TEXT.hint });
    panel.append(label, hint);
    input.addEventListener('change', () => { setOn(input.checked); apply(); });
    st.box = input;
  }

  function install() {
    if (st.installed || typeof document === 'undefined') return;
    if (typeof root.handleBarcodeScanned !== 'function') return;   //  صفحهٔ دیگر
    st.installed = true;
    //  نمایه با هر تغییرِ کالاها کهنه می‌شود: صفحه پس از هر تغییرِ کالا همین را صدا می‌زند
    const rebuild = root.rebuildBarcodeIndex;
    if (typeof rebuild === 'function') {
      root.rebuildBarcodeIndex = function () { st.ver++; return rebuild.apply(this, arguments); };
    }
    //  window (نه document): پیش از شنوندهٔ document ِ cart-query.js، تا «۳*نام» به «محصول جدید» نرود
    root.addEventListener('click', onLookup, true);
    const ready = () => {
      mount();
      apply();
      const input = manual();
      if (!input) return;
      input.addEventListener('input', render);
      input.addEventListener('keydown', onKey);
      //  تمرکز رفت ⇒ فقط بسته (برجسته می‌ماند: لمسِ «جستجو» تمرکز را از کادر می‌گیرد و همان برجسته را برمی‌دارد)
      input.addEventListener('blur', () => { if (st.open) { st.open = false; paint(); } });
      input.addEventListener('focus', () => { if (st.items.length && st.shown === input.value) { st.open = true; paint(); } });
    };
    if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', ready, { once: true });
    else ready();
  }

  return { PREF, TEXT, index, search, distance, ktTrim, query, install };
});
