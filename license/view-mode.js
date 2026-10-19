/* نمای «کارتی» یا «جدولی» برای فهرست‌های اصلیِ وب — VISUAL-DIRECTION §۱.۵.
 *
 * همان منطقِ `ui/components/common/ViewModePref.kt` (برنامهٔ گوشی و کامپیوتر)، قاعده به قاعده:
 *   • پیش‌فرض: صفحه‌های مدیریت جدول، فروش کارت، گوشی **همیشه** کارت؛
 *   • انتخاب برای هر کاربر و هر صفحه روی همین مرورگر می‌ماند (localStorage)؛
 *     ترجیحِ نمایشی است، همگام نمی‌شود و در پشتیبان نمی‌رود؛
 *   • روی گوشی چیزی نوشته نمی‌شود (نوشتنِ «کارت» انتخابِ جدولِ همین کاربر روی
 *     پنجرهٔ پهن را پاک می‌کرد) و کلیدِ عوض‌کردن هم دیده نمی‌شود.
 * کلید: `tohid-ui:view_mode:<کاربر>:<صفحه>` — بخشِ پس از `tohid-ui:` همان `ViewModePref.key`.
 * گوشی یعنی پهنای ≤ ۵۹۹ پیکسل (کلاسِ «فشرده»ِ Material).
 */
(function (root) {
  'use strict';
  var MODES = ['card', 'table'];
  var PREFIX = 'view_mode';
  var STORE = 'tohid-ui:';
  var LOCAL_USER = 'local';
  var PHONE_MAX = 599;

  /** مقدارِ ذخیره‌شده ⇐ نما؛ هر چیزِ ناشناخته null (یعنی پیش‌فرض) */
  function parse(raw) {
    var v = String(raw == null ? '' : raw).trim().toLowerCase();
    return MODES.indexOf(v) >= 0 ? v : null;
  }
  /** kind: 'sale' | 'management'؛ form: 'phone' | 'large' */
  function defaultMode(kind, form) {
    if (form === 'phone') return 'card';
    return kind === 'sale' ? 'card' : 'table';
  }
  function resolve(stored, kind, form) {
    return form === 'phone' ? 'card' : (parse(stored) || defaultMode(kind, form));
  }
  function toggleVisible(form) { return form !== 'phone'; }
  function other(mode) { return mode === 'card' ? 'table' : 'card'; }
  function clean(s) {
    return String(s == null ? '' : s).trim().replace(/[^\p{L}\p{N}\-_.@]/gu, '_');
  }
  function key(userId, screen) {
    if (!String(screen == null ? '' : screen).trim()) throw new Error('screen id is required');
    return PREFIX + ':' + (clean(userId) || LOCAL_USER) + ':' + clean(screen);
  }

  /* ───────── بخشِ مرورگر ───────── */
  function formFactor(width) {
    var w = width == null ? (root.innerWidth || 1024) : width;
    return w <= PHONE_MAX ? 'phone' : 'large';
  }
  /** کاربرِ واردشده (همان `userId`ِ لایهٔ اشتراک)؛ بی‌حساب = local */
  function currentUser() {
    try {
      var a = JSON.parse(root.localStorage.getItem('tohid-license-v1') || 'null');
      return (a && a.userId) || '';
    } catch (e) { return ''; }
  }
  function load(screen, kind, form, userId) {
    var raw = null;
    try { raw = root.localStorage.getItem(STORE + key(userId == null ? currentUser() : userId, screen)); } catch (e) { raw = null; }
    return resolve(raw, kind, form || formFactor());
  }
  function save(screen, mode, form, userId) {
    if ((form || formFactor()) === 'phone' || !parse(mode)) return;
    try { root.localStorage.setItem(STORE + key(userId == null ? currentUser() : userId, screen), parse(mode)); } catch (e) { /* حافظهٔ پر یا بسته: فقط همین بار */ }
  }

  var ICON_CARD = '<svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" aria-hidden="true"><rect x="3" y="3" width="7" height="7" rx="1.5"/><rect x="14" y="3" width="7" height="7" rx="1.5"/><rect x="3" y="14" width="7" height="7" rx="1.5"/><rect x="14" y="14" width="7" height="7" rx="1.5"/></svg>';
  var ICON_TABLE = '<svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" aria-hidden="true"><line x1="9" y1="6" x2="20" y2="6"/><line x1="9" y1="12" x2="20" y2="12"/><line x1="9" y1="18" x2="20" y2="18"/><circle cx="4.5" cy="6" r="1"/><circle cx="4.5" cy="12" r="1"/><circle cx="4.5" cy="18" r="1"/></svg>';

  /**
   * کلیدِ دوحالته را در host می‌سازد و نمای مؤثر را نگه می‌دارد.
   * opts: { screen, kind, onChange(mode) } ⇐ { mode(), set(mode) }
   * onChange فقط وقتی صدا می‌شود که نمای مؤثر عوض شده باشد (کلیک یا عبور از مرزِ گوشی).
   */
  function mount(host, opts) {
    var screen = opts.screen, kind = opts.kind || 'management';
    var mode = load(screen, kind);
    host.classList.add('ro-view');
    host.setAttribute('role', 'group');
    host.setAttribute('aria-label', 'نمای فهرست');
    host.innerHTML =
      '<button type="button" class="ro-view-btn" data-view="card" aria-label="نمای کارتی" title="نمای کارتی">' + ICON_CARD + '</button>' +
      '<button type="button" class="ro-view-btn" data-view="table" aria-label="نمای جدولی" title="نمای جدولی">' + ICON_TABLE + '</button>';
    function paint() {
      host.hidden = !toggleVisible(formFactor());
      var bs = host.querySelectorAll('[data-view]');
      for (var i = 0; i < bs.length; i++) bs[i].setAttribute('aria-pressed', String(bs[i].getAttribute('data-view') === mode));
    }
    function set(next, persist) {
      next = parse(next) || mode;
      if (persist) save(screen, next);
      var eff = resolve(next, kind, formFactor());
      var changed = eff !== mode;
      mode = eff;
      paint();
      if (changed && opts.onChange) opts.onChange(mode);
    }
    host.addEventListener('click', function (e) {
      var b = e.target.closest && e.target.closest('[data-view]');
      if (b) set(b.getAttribute('data-view'), true);
    });
    var lastForm = formFactor();
    root.addEventListener('resize', function () {
      var f = formFactor();
      if (f === lastForm) return;
      lastForm = f;
      var eff = load(screen, kind, f);
      var changed = eff !== mode;
      mode = eff; paint();
      if (changed && opts.onChange) opts.onChange(mode);
    });
    paint();
    return { mode: function () { return mode; }, set: function (m) { set(m, true); } };
  }

  var api = {
    MODES: MODES, PHONE_MAX: PHONE_MAX, LOCAL_USER: LOCAL_USER, STORE: STORE,
    parse: parse, defaultMode: defaultMode, resolve: resolve, toggleVisible: toggleVisible, other: other, key: key,
    formFactor: formFactor, currentUser: currentUser, load: load, save: save, mount: mount,
  };
  if (typeof module === 'object' && module.exports) module.exports = api;
  else root.TohidViewMode = api;
})(typeof window !== 'undefined' ? window : globalThis);
