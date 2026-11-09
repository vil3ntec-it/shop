/**
 * MKT-06 — «چرخهٔ مصرف (روز)» در ویرایشِ کالای نسخهٔ وبِ دکان؛ همان کادرِ «بیشتر» ِ گوشی و پنلِ کامپیوتر
 * (`android-native/.../data/ProductCycle.kt`، بردارِ مشترکِ `license/test-vectors/product-cycle-v1.json`).
 * فیلدِ اختیاریِ `consumptionDays` ِ ردیفِ کالا (روزِ صحیحِ ۱ تا ۳۶۵) را همان صفِ همگام‌سازیِ وب (v1/v3) می‌برد و ویترین
 * فقط برای کالای منتشرشده در نمای عمومی نشانش می‌دهد (`storefront.js` ‹cycleOf›) تا برنامهٔ خرید «وقتِ دوباره خریدن؟» بسازد.
 *
 * `index.html` یخ‌زده است ⇒ بارکننده `shop-sync.js` و هیچ خطی در صفحه:
 *   • `openEditProduct` ِ صفحه پوشانده می‌شود: کادر (یک بار ساخته) در «جزئیاتِ بیشتر» (`#bp-extras`) با مقدارِ همان کالا پر
 *     می‌شود. کالای تازه کادر ندارد (`#bp-extras` فقط در ویرایش؛ همان `ProductFormModel` ِ برنامه).
 *   • کادرِ خراب جلوی ذخیره را می‌گیرد: شنوندهٔ capture ِ «ذخیره» پیش از دستگیرهٔ صفحه ⇒ هیچ فیلدی از کالا عوض نمی‌شود.
 *   • نوشتن فقط در همان ویرایشِ موفق: صفحه `productChanges(before, p)` را درست پس از نشستنِ بقیهٔ فیلدها و پیش از
 *     `logAudit` و `saveState` (تنها درِ نوشتنِ دفتر) صدا می‌زند؛ همان‌جا چرخه روی همان `p` می‌نشیند و تکهٔ سابقه افزوده
 *     می‌شود. شکستِ پیشینِ صفحه (بارکد یا نامِ تکراری، ضریب) پیش از آن برمی‌گردد ⇒ چیزی نوشته نمی‌شود.
 *   ⛔ کالای بی چرخه بایت‌به‌بایت همان (هیچ کلیدی، حتی null)؛ پاک کردن = برداشتنِ کلید، که `sync-core.changedFields` آن را
 *     null ِ صریح می‌فرستد و سرور برای `client: 'web'` می‌نشاند (`newer-fields.js`).
 *   ⛔ CSP: بی innerHTML/style/eval؛ متن فقط کلیدهای واژه‌نامهٔ صفحه (`web-i18n.js` انگلیسی و پشتو را برمی‌گرداند).
 * آزمون: `server/test/web-product-cycle.test.js` (بردار و دفتر) و `server/test/web-product-cycle.mjs` (صفحه تا ویترین).
 */
(function (root, factory) {
  'use strict';
  const api = factory(root);
  if (typeof module !== 'undefined' && module.exports) module.exports = api;
  if (typeof window !== 'undefined' && root === window) {
    root.TohidProductCycle = api;
    api.install();
  }
})(typeof window !== 'undefined' ? window : globalThis, function (root) {
  'use strict';

  const MAX = 365;
  const KEY = 'consumptionDays';
  /** متنِ کادر — کلیدِ واژه‌نامهٔ صفحه (`license/i18n/web.*.json`)، همان `StrProducts.cycle*` ِ برنامه */
  const TEXT = Object.freeze({
    label: 'چرخهٔ مصرف (روز)',
    hint: 'اختیاری — پس از این چند روز، برنامهٔ خریدِ مشتری «وقتِ دوباره خریدن؟» نشان می‌دهد',
    bad: 'عددِ صحیحِ ۱ تا ۳۶۵ روز بنویسید',
  });
  /** فاصلهٔ دو سر = `Char.isWhitespace` ِ Kotlin روی JVM، نه trim ِ JS (U+001C..U+001F بله، U+FEFF نه) */
  const WS = '[\\t\\n\\v\\f\\r\\u001c-\\u0020\\u00a0\\u1680\\u2000-\\u200a\\u2028\\u2029\\u202f\\u205f\\u3000]';
  const EDGES = new RegExp(`^${WS}+|${WS}+$`, 'g');

  /** همان `latinDigits` ِ برنامه برای رقم (۰-۹ و ٠-٩)؛ «٫» به هر حال رقم نیست و خراب است */
  function latinDigits(s) {
    return s
      .replace(/[۰-۹]/g, (c) => String.fromCharCode(c.charCodeAt(0) - 0x06F0 + 48))
      .replace(/[٠-٩]/g, (c) => String.fromCharCode(c.charCodeAt(0) - 0x0660 + 48));
  }

  /** متنِ کادر ⇒ `{kind:'clear'}` (برداشتن)، `{kind:'days', days}` یا `{kind:'bad'}` — همان `ProductCycle.parse` */
  function parse(boxText) {
    if (typeof boxText !== 'string') return { kind: 'bad' };
    const t = latinDigits(boxText).replace(EDGES, '');
    if (!t) return { kind: 'clear' };
    if (!/^[0-9]{1,3}$/.test(t)) return { kind: 'bad' };
    const n = Number(t);
    return n >= 1 && n <= MAX ? { kind: 'days', days: n } : { kind: 'bad' };
  }

  /** مقدارِ دفتر ⇒ متنِ کادر؛ هر چیزِ جز عددِ صحیحِ ۱ تا ۳۶۵ خالی (همان `ProductCycle.text`) */
  function text(days) {
    return Number.isSafeInteger(days) && days >= 1 && days <= MAX ? String(days) : '';
  }

  /**
   *  چرخهٔ کادر را روی همان کالا می‌نشاند (همان `ProductCycle.apply`). بی‌تغییر یا کادرِ خراب ⇒ null و شیء دست‌نخورده؛
   *  وگرنه `{from, to}` (مقدارِ نادرستِ پیشین `from = null`). پاک کردن کلید را برمی‌دارد، null نمی‌نویسد.
   */
  function applyTo(p, boxText) {
    if (!p || typeof p !== 'object') return null;
    const r = parse(boxText);
    if (r.kind === 'bad') return null;
    const has = Object.prototype.hasOwnProperty.call(p, KEY);
    const from = has && text(p[KEY]) ? p[KEY] : null;
    if (r.kind === 'clear') {
      if (!has) return null;
      delete p[KEY];
      return { from, to: null };
    }
    if (has && p[KEY] === r.days) return null;
    p[KEY] = r.days;
    return { from, to: r.days };
  }

  /** تکهٔ «ویرایشِ کالا» در سابقهٔ عملیات (دادهٔ دفتر، مثلِ `productChanges` ِ صفحه)؛ بی‌اثر ⇒ '' */
  function changeText(c) {
    if (!c || c.from === c.to) return '';
    const v = (n) => (n == null ? '—' : String(n));
    return `چرخهٔ مصرف ${v(c.from)} ⇐ ${v(c.to)} روز`; // داده: تکهٔ سابقهٔ عملیات که در دفتر نوشته می‌شود
  }

  /* ---------------- صفحه ---------------- */
  const st = { installed: false, editing: null, box: null, group: null };

  /** کالاهای دفترِ صفحه (`let products` ِ index.html) — فقط برای پر کردنِ کادر */
  function ledgerProducts() {
    try {
      /* eslint-disable-next-line no-undef */
      return Array.isArray(products) ? products : [];
    } catch (e) {
      return [];
    }
  }

  const extras = () => document.getElementById('bp-extras');
  /** «جزئیاتِ بیشتر» دیده می‌شود (پنجرهٔ ویرایش باز است) */
  const extrasShown = () => { const x = extras(); return !!x && x.getClientRects().length > 0; };

  function setError(on) {
    if (!st.group) return;
    st.group.classList.toggle('error', !!on);
    st.box.setAttribute('aria-invalid', on ? 'true' : 'false');
  }

  /** کادر یک بار ساخته می‌شود، پیش از «فعال در فروش»؛ نبودِ ظرف ⇒ هیچ */
  function mount() {
    if (st.box && st.box.isConnected) return st.box;
    const box = extras();
    if (!box) return null;
    const el = (tag, props) => Object.assign(document.createElement(tag), props);
    const group = el('div', { className: 'form-group', id: 'fg-bp-cycle' });
    const input = el('input', { className: 'form-input', id: 'in-bp-cycle', type: 'text', inputMode: 'numeric', maxLength: 8, autocomplete: 'off' });
    input.setAttribute('aria-describedby', 'bp-cycle-hint bp-cycle-error');
    group.append(
      el('label', { htmlFor: 'in-bp-cycle', textContent: TEXT.label }),
      input,
      el('p', { className: 'settings-hint', id: 'bp-cycle-hint', textContent: TEXT.hint }),
      el('div', { className: 'field-error', id: 'bp-cycle-error', textContent: TEXT.bad }),
    );
    const active = box.querySelector(':scope > .check-row');
    box.insertBefore(group, active);
    input.addEventListener('input', () => setError(false));
    st.box = input;
    st.group = group;
    return input;
  }

  function onOpen(id) {
    st.editing = null;
    const input = mount();
    if (!input) return;
    const p = ledgerProducts().find((x) => x && x.id === id);
    input.value = p ? text(p[KEY]) : '';
    setError(false);
    st.editing = p ? p.id : null;
  }

  /** پیش از دستگیرهٔ «ذخیره» ِ صفحه: کادرِ خراب ⇒ هیچ چیزی ذخیره نمی‌شود */
  function onSaveClick(e) {
    const t = e.target;
    if (!st.editing || !st.box || !t || typeof t.closest !== 'function' || !t.closest('#btn-bp-save') || !extrasShown()) return;
    if (parse(st.box.value).kind !== 'bad') { setError(false); return; }
    e.preventDefault();
    e.stopImmediatePropagation();
    setError(true);
    try { st.box.focus(); } catch (err) { /* کادر پنهان */ }
  }

  /** درونِ ذخیرهٔ موفقِ همان ویرایش (پیش از سابقه و `saveState`): چرخه روی همان کالا + تکهٔ سابقه */
  function onChanges(after, out) {
    if (!st.editing || !after || after.id !== st.editing || !st.box || !st.box.isConnected || !extrasShown()) return out;
    st.editing = null;
    const line = changeText(applyTo(after, st.box.value));
    if (line && Array.isArray(out)) out.push(line);
    return out;
  }

  function install() {
    if (st.installed || typeof document === 'undefined') return;
    const open = root.openEditProduct;
    const changes = root.productChanges;
    if (typeof open !== 'function' || typeof changes !== 'function') return;   //  صفحهٔ دیگر
    st.installed = true;
    root.openEditProduct = function (id) {
      const r = open.apply(this, arguments);
      try { onOpen(id); } catch (e) { console.error('[product-cycle]', e); }
      return r;
    };
    root.productChanges = function (a, b) {
      const out = changes.apply(this, arguments);
      try { return onChanges(b, out); } catch (e) { console.error('[product-cycle]', e); return out; }
    };
    document.addEventListener('click', onSaveClick, true);
  }

  return { MAX, KEY, TEXT, parse, text, applyTo, changeText, install };
});
