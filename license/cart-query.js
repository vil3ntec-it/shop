/**
 * POS-03 — پیشوندِ شمار «۳*» یا «۳×» پیش از بارکد در صندوقِ نسخهٔ وبِ دکان؛ همان `data/CartQuery.kt` ِ صندوقِ کامپیوتر و گوشی
 * (بردارِ مشترکِ `license/test-vectors/cart-query-v1.json`، `CartQueryVectorsTest` ِ Kotlin همان را می‌خواند).
 *
 * ⛔ پیش‌فرض خاموش (تنظیمِ همین مرورگر، `tohid-pos-qty-prefix-v1`؛ «تنظیمات» ⇐ «دوربین بارکدخوان»). خاموش ⇒ هیچ شنونده‌ای
 *    کاری نمی‌کند و صفحه بایت‌به‌بایت همان دیروز («۳*کد» بارکدِ ناشناس است).
 * روشن:
 *   • کادرِ «بارکد را دستی وارد کنید»: «۳*کد» + Enter (یا «۳*» و بعد اسکن با بارکدخوانی که در همان کادر می‌نویسد) ⇒ همان
 *     اسکنِ صفحه با شمارِ ۳ به‌جای «هر اسکن: ×N»؛ پس از آن همان ×N ِ پیشین. کالای ناشناس ⇒ «محصول جدید» با خودِ کد (نه «۳*کد»).
 *   • فقط «۳*» در کادر ⇒ اسکنِ بعدی (دوربین، بارکدخوانِ بیرون از کادر، گوشیِ بارکدخوان) همان شمار را می‌برد و کادر خالی می‌شود.
 *   • متنی که خودش بارکدِ کالاست («12x34») همیشه همان کالا، بی پیشوند — همان `PosSearch.parse` ِ کامپیوتر و گوشی (بخشِ `fullCode` ِ بردار).
 *   • شمارِ صفر یا نادرست ⇒ پیام و هیچ افزودن؛ اعشار برای کالای شمردنی ⇒ همان پیامِ `quantityProblem` ِ صفحه و هیچ افزودن.
 *   • صفحه‌کلیدِ گوشی برای همان کادر `tel` (رقم با «*»)، نه `numeric` ِ بی «*»؛ خاموش ⇒ همان `numeric`.
 *   • این دستگاه بارکدخوانِ کامپیوتر است (`TohidPhotoStudio.scanner`) ⇒ همان راهِ امروز (شمار به کامپیوتر نمی‌رود).
 *   ⚠️ دوربینی که پیش از بارِ همین پرونده روشن شد دستگیرهٔ پیشینِ صفحه را دارد: «۳*» ِ تنها تا روشن شدنِ دوباره‌اش فقط برای
 *     بارکدخوان و Enter ِ کادر است (کادر دست نمی‌خورد، پس چیزی گم نمی‌شود).
 * `index.html` یخ‌زده است ⇒ بارکننده `shop-sync.js`؛ اسکن فقط از `handleBarcodeScanned` ِ خودِ صفحه (بی راهِ دومِ افزودن).
 * ⛔ CSP: بی innerHTML/style/eval؛ متن فقط کلیدهای واژه‌نامهٔ صفحه (`web-i18n.js` انگلیسی و پشتو را برمی‌گرداند).
 * آزمون: `server/test/web-cart-query.test.js` (بردار و تصمیم) و `server/test/web-pos-qty.mjs` (صفحه).
 */
(function (root, factory) {
  'use strict';
  const api = factory(root);
  if (typeof module !== 'undefined' && module.exports) module.exports = api;
  if (typeof window !== 'undefined' && root === window) {
    root.TohidCartQuery = api;
    api.install();
  }
})(typeof window !== 'undefined' ? window : globalThis, function (root) {
  'use strict';

  const PREF = 'tohid-pos-qty-prefix-v1';
  /** متن — کلیدِ واژه‌نامهٔ صفحه (`license/i18n/web.*.json`) */
  const TEXT = Object.freeze({
    label: 'پیشوندِ شمار پیش از بارکد',
    hint: '«۳*» یا «۳×» پیش از بارکد در کادرِ فروش یعنی سه تا از همان کالا؛ «۳*» تنها، اسکنِ بعدی را سه تا می‌کند. فقط روی همین دستگاه.',
    next: 'اسکنِ بعدی {0} تا به سبد می‌رود',
    bad: 'شمارِ پیش از «*» باید بیشتر از صفر باشد',
  });

  /*
   *  همان `Regex("^\\s*([0-9]+(?:\\.[0-9]+)?)\\s*[*×xX]\\s*(.*)$")` ِ Kotlin روی JVM (java.util.regex، بی پرچم):
   *  `\s` = [ \t\n\x0B\f\r] (نه فاصله‌های یونیکدِ JS)، `.` = هر نویسه جز \n \r U+0085 U+2028 U+2029، و `$` پایانِ متن یا
   *  پیش از یک پایانِ خطِ پایانی (\r\n یکی است). جای بخشِ پس از ستاره = درازای سه گروهِ نخست (بی پرچمِ `d` ِ مرورگرِ نو).
   */
  const WS = '[ \\t\\n\\x0B\\f\\r]';
  const EOL = '\\n\\r\\u0085\\u2028\\u2029';
  const COUNT = new RegExp(`^(${WS}*)([0-9]+(?:\\.[0-9]+)?)(${WS}*[*\\u00D7xX]${WS}*)[^${EOL}]*(?:\\r\\n|[${EOL}])?$`);

  /** همان `latinDigits` ِ برنامه: ۰-۹ و ٠-٩ ⇐ لاتین، «٫» و «٬» ⇐ «.»؛ یک نویسه به یک نویسه */
  function latinDigits(s) {
    return s.replace(/[۰-۹٠-٩٫٬]/g, (c) => {
      const n = c.charCodeAt(0);
      if (n === 0x066B || n === 0x066C) return '.';
      return String.fromCharCode(48 + (n >= 0x06F0 ? n - 0x06F0 : n - 0x0660));
    });
  }

  /** «۳*کد» ⇒ {count: 3, term: 'کد'}؛ بی پیشوند ⇒ {count: 1, term: همان متن} — همان `CartQuery.parse` */
  function parse(query) {
    const q = typeof query === 'string' ? query : '';
    const m = COUNT.exec(latinDigits(q));
    if (!m) return { count: 1, term: q };
    const at = m[1].length + m[2].length + m[3].length;
    return { count: Number(m[2]), term: q.slice(Math.min(at, q.length)) };
  }

  const PAGE = Object.freeze({ kind: 'page' });

  /**
   *  کادرِ دستی ⇒ کار: `page` (همان راهِ صفحه: بی پیشوند یا خودِ متن بارکدِ کالاست)، `add` (اسکنِ `code` با `count`)،
   *  `wait` (فقط پیشوند: اسکنِ بعدی با `count`) یا `bad` (شمارِ صفر یا نادرست؛ `wait` = بی کد).
   *  `isBarcode(متن)` همان جست‌وجوی صفحه است؛ کد همان trim ِ صفحه.
   */
  function decide(text, isBarcode) {
    const raw = typeof text === 'string' ? text : '';
    if (!raw.trim() || (typeof isBarcode === 'function' && isBarcode(raw))) return PAGE;
    const p = parse(raw);
    if (p.term.length === raw.length) return PAGE;
    const code = p.term.trim();
    if (!(p.count > 0) || !Number.isFinite(p.count)) return { kind: 'bad', wait: !code };
    return code ? { kind: 'add', count: p.count, code } : { kind: 'wait', count: p.count };
  }

  /* ---------------- صفحه ---------------- */
  const st = { installed: false, busy: false, scan: null, box: null };

  function on() {
    try { return root.localStorage.getItem(PREF) === '1'; } catch (e) { return false; }
  }
  function setOn(v) {
    try { if (v) root.localStorage.setItem(PREF, '1'); else root.localStorage.removeItem(PREF); } catch (e) { /* حافظهٔ بسته */ }
  }

  const manual = () => document.getElementById('in-manual-barcode');
  const toast = (msg, kind) => { if (typeof root.showToast === 'function') root.showToast(msg, kind); };
  /** این دستگاه بارکدخوانِ کامپیوتر است ⇒ کد به آن‌جا می‌رود، شمار نه */
  function forwarding() {
    const s = root.TohidPhotoStudio && root.TohidPhotoStudio.scanner;
    return !!(s && s.sendCode);
  }

  /** کالای همان کد — همان `barcodeIndex` و یک‌شکل کردنِ رقمِ `handleBarcodeScanned` ِ صفحه */
  function productOf(text) {
    try {
      const raw = String(text || '').trim();
      const code = typeof root.normalizeDigits === 'function' ? root.normalizeDigits(raw) : raw;
      /* eslint-disable-next-line no-undef */
      const id = barcodeIndex.get(code) || barcodeIndex.get(raw);
      /* eslint-disable-next-line no-undef */
      return id ? (products.find((x) => x && x.id === id) || null) : null;
    } catch (e) {
      return null;
    }
  }
  const isBarcode = (text) => !!productOf(text);

  /** اعشار برای کالای شمردنی ⇒ همان پیامِ صفحه و هیچ افزودن؛ ناشناس و غیرفعال را خودِ صفحه می‌گوید */
  function fits(code, count) {
    const p = productOf(code);
    if (!p || p.active === false || typeof root.quantityProblem !== 'function') return true;
    const why = root.quantityProblem(p, count);
    if (why) { toast(why, 'danger'); return false; }
    return true;
  }

  /** همان اسکنِ صفحه با این شمار به‌جای «هر اسکن: ×N»؛ پس از آن همان ×N */
  function scanWith(count, code, opts) {
    const scan = st.scan;
    let prev;
    try {
      /* eslint-disable-next-line no-undef */
      prev = scanMultiplier;
    } catch (e) {
      return scan.call(root, code, opts);
    }
    st.busy = true;
    try {
      /* eslint-disable-next-line no-undef */
      scanMultiplier = count;
      return scan.call(root, code, opts);
    } finally {
      /* eslint-disable-next-line no-undef */
      scanMultiplier = prev;
      st.busy = false;
    }
  }

  /** «جستجو» یا Enter ِ کادر (صفحه Enter را به همین دکمه می‌دهد) — پیش از دستگیرهٔ صفحه */
  function onLookup(e) {
    const t = e.target;
    if (!on() || !st.scan || !t || typeof t.closest !== 'function' || !t.closest('#btn-manual-lookup') || forwarding()) return;
    const input = manual();
    if (!input) return;
    const d = decide(input.value, isBarcode);
    if (d.kind === 'page') return;
    e.preventDefault();
    e.stopImmediatePropagation();
    if (d.kind === 'bad') { toast(TEXT.bad, 'danger'); return; }   //  متن می‌ماند تا درست شود
    if (d.kind === 'wait') { toast(TEXT.next.replace('{0}', String(d.count))); input.focus(); return; }
    if (!fits(d.code, d.count)) return;
    scanWith(d.count, d.code, { skipDedup: true });
    input.value = '';
    input.focus();
  }

  /** اسکنِ دیگر (دوربین، بارکدخوانِ بیرون از کادر، گوشی) وقتی کادر فقط «۳*» دارد ⇒ همان شمار، و کادر خالی */
  function pendingScan(orig) {
    return function (code, opts) {
      const fromPhone = !!(opts && opts.fromPhone);
      const input = manual();
      if (st.busy || !on() || !input || (!fromPhone && forwarding())) return orig.apply(this, arguments);
      const d = decide(input.value, isBarcode);
      if (d.kind === 'bad' && d.wait) { toast(TEXT.bad, 'danger'); return undefined; }
      if (d.kind !== 'wait') return orig.apply(this, arguments);
      if (!fits(code, d.count)) return undefined;
      input.value = '';
      return scanWith(d.count, code, opts);
    };
  }

  /** کلیدِ تنظیم، یک بار، در «دوربین بارکدخوان» ِ تنظیمات */
  function mount() {
    if (st.box && st.box.isConnected) return;
    const badge = document.getElementById('settings-camera-support-badge');
    const panel = badge && typeof badge.closest === 'function' ? badge.closest('.panel') : null;
    if (!panel) return;
    const el = (tag, props) => Object.assign(document.createElement(tag), props);
    const input = el('input', { type: 'checkbox', id: 'in-pos-qty-prefix', checked: on() });
    input.setAttribute('aria-describedby', 'pos-qty-prefix-hint');
    const label = el('label', { className: 'check-row' });
    label.append(input, document.createTextNode(TEXT.label));
    Object.assign(label.style, { display: 'flex', gap: '8px', alignItems: 'center', marginTop: '14px' });
    const hint = el('p', { className: 'settings-hint', id: 'pos-qty-prefix-hint', textContent: TEXT.hint });
    panel.append(label, hint);
    input.addEventListener('change', () => { setOn(input.checked); keypad(); });
    st.box = input;
  }

  /**
   *  صفحه‌کلیدِ گوشی برای کادر: `numeric` ِ صفحه «*» ندارد ⇒ روشن ⇐ `tel` (همان رقم‌ها، با «*»؛ رقمِ فارسی را صفحه همان‌جا
   *  لاتین می‌کند)، خاموش ⇐ همان `numeric`. کادری که صفحه چیزِ دیگری برایش گذاشته دست نمی‌خورد.
   */
  function keypad() {
    const input = manual();
    if (!input || (input.inputMode !== 'numeric' && input.inputMode !== 'tel')) return;
    input.inputMode = on() ? 'tel' : 'numeric';
  }

  function install() {
    if (st.installed || typeof document === 'undefined') return;
    const scan = root.handleBarcodeScanned;
    if (typeof scan !== 'function') return;   //  صفحهٔ دیگر
    st.installed = true;
    st.scan = scan;
    root.handleBarcodeScanned = pendingScan(scan);
    document.addEventListener('click', onLookup, true);
    const ready = () => { mount(); keypad(); };
    if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', ready, { once: true });
    else ready();
  }

  return { PREF, TEXT, parse, decide, latinDigits, install };
});
