/* ==========================================================
   فروشگاه من | هستهٔ سنجشِ مجوز (STF-01 تا STF-03، بخشِ ۱۰.۱)
   ----------------------------------------------------------
   همتای دقیقِ `core/src/commonMain/kotlin/ir/vil3ntec/tohid/core/perm/PermCore.kt`.
   هر دو با یک فایلِ مرجع سنجیده می‌شوند: `core/test-vectors/perm-v1.json`.
   سرور نسخهٔ عیناً همین فایل را در `server/src/lib/perm-core.js` دارد
   (پوشهٔ ساختِ داکرِ سرور فقط `server/` است) و آزمون برابریِ بایت‌به‌بایتِ
   دو نسخه را می‌سنجد. ⛔ یکی را بی دیگری و بی فایلِ مرجع عوض نکنید.

   ── مدل ──────────────────────────────────────────────────────────
   هر مجوز سه حالت دارد: allow (مجاز)، deny (ممنوع)، approve (نیازمند تأیید).
   حالتِ پیش‌فرضِ هر نقش «رفتارِ امروز» است. روی آن، لایه‌ها به‌ترتیب
   می‌نشینند (لایهٔ نقش در این دکان، سپس لایهٔ همین کاربر):
     { "settings.write": { "mode": "deny" },
       "sales.discount": { "mode": "allow", "caps": { "maxPct": 5 } } }
   • مالک همیشه همه‌کاره است؛ هیچ لایه‌ای او را قفل نمی‌کند.
   • نقشِ ناشناخته و مجوزِ ناشناخته ⇐ ممنوع (بسته‌به‌خطا).
   • مجوزِ «restrict» فقط تنگ‌تر از پیش‌فرضِ نقش می‌شود، هرگز گشادتر:
     لایه‌ای که بیش از نقش بدهد، تا حدِ نقش بریده می‌شود. تنها استثنا
     «نیازمند تأیید» برای مجوزِ تأییدپذیر است: به خودِ کاربر هیچ قدرتی
     نمی‌دهد، چون سرور op را فقط با «برگهٔ امضاشده»ی کسی می‌پذیرد که خودش
     همان کار را «مجاز» دارد (بخشِ ۱۰.۱؛ `server/src/lib/staff-approval.js`).
   • مجوزِ «none» از لایه‌ها اثر نمی‌گیرد.
   • سقف (caps) فقط وقتی اثر دارد که حالت allow باشد؛ عددِ بالاتر از
     سقف ⇐ approve (اگر مجوز تأییدپذیر است) وگرنه deny.

   این فایل هیچ چیزی از مرورگر نمی‌خواهد؛ همان کد در Node هم می‌دود.
   ========================================================== */
(function (factory) {
  'use strict';
  var api = factory();
  if (typeof module !== 'undefined' && module.exports) module.exports = api;
  if (typeof window !== 'undefined') window.TohidPerm = api;
  else if (typeof globalThis !== 'undefined') globalThis.TohidPerm = api;
})(function () {
  'use strict';

  var VERSION = 1;
  var MODES = ['allow', 'approve', 'deny'];
  var RANK = { deny: 0, approve: 1, allow: 2 };
  var ROLES = ['owner', 'manager', 'staff'];

  /** نامِ سقف ⇐ نامِ فیلدِ درخواست */
  var CAP_FIELDS = { maxPct: 'pct', maxAmount: 'amount' };

  /*
   *  فهرستِ مجوزهایی که از هسته می‌گذرند. پیش‌فرض‌ها = رفتارِ امروز:
   *  همان `MATRIX`ِ `server/src/lib/permissions.js` برای کلیدهای مشترک، و
   *  همان `ShopRole`ِ برنامه برای `reports.money` (شاگرد عددِ مالی نمی‌بیند).
   *    edit: none | restrict | full
   *    approvable: آیا «نیازمند تأیید» برایش معنا دارد
   *    display: فقط نمایش است (داده روی دستگاه هست؛ امنیت نیست)
   */
  var CATALOG = {
    'sales.discount': {
      defaults: { owner: 'allow', manager: 'allow', staff: 'deny' },
      edit: 'restrict', approvable: true, caps: ['maxPct', 'maxAmount'], display: false,
    },
    'sales.cancel': {
      defaults: { owner: 'allow', manager: 'allow', staff: 'deny' },
      edit: 'restrict', approvable: true, caps: [], display: false,
    },
    //  برگشتِ کالا و بازپرداخت (STF-03 «مبلغِ برگشت»): امروز هر عضوِ نویسنده
    //  ثبت می‌کند، پس پیش‌فرض برای همه allow است
    'returns.create': {
      defaults: { owner: 'allow', manager: 'allow', staff: 'allow' },
      edit: 'restrict', approvable: true, caps: ['maxAmount'], display: false,
    },
    'settings.write': {
      defaults: { owner: 'allow', manager: 'allow', staff: 'deny' },
      edit: 'restrict', approvable: false, caps: [], display: false,
    },
    'audit.view': {
      defaults: { owner: 'allow', manager: 'allow', staff: 'deny' },
      edit: 'restrict', approvable: false, caps: [], display: false,
    },
    'reports.money': {
      defaults: { owner: 'allow', manager: 'allow', staff: 'deny' },
      edit: 'full', approvable: false, caps: [], display: true,
    },
    //  HW-03: باز کردنِ دستیِ کشو بی فروش (`no_sale`)؛ شاگرد فقط با تأییدکننده — همان no_sale ِ #286
    'drawer.open': {
      defaults: { owner: 'allow', manager: 'allow', staff: 'deny' },
      edit: 'restrict', approvable: true, caps: [], display: false,
    },
    //  گاوصندوق (PAY-18): شمارش، ورود، خروج، به بانک و به دخل؛ «از دخل» فقط نوشتن می‌خواهد
    'safe.move': {
      defaults: { owner: 'allow', manager: 'allow', staff: 'deny' },
      edit: 'restrict', approvable: false, caps: [], display: false,
    },
  };

  function has(o, k) { return o != null && Object.prototype.hasOwnProperty.call(o, k); }
  function isCapValue(v) { return typeof v === 'number' && isFinite(v) && v >= 0; }
  function keys() { return Object.keys(CATALOG).sort(); }

  /** حالتِ پیش‌فرضِ یک نقش — یا null برای نقش/مجوزِ ناشناخته */
  function defaultMode(role, key) {
    var spec = CATALOG[key];
    if (!spec || !has(spec.defaults, role)) return null;
    return spec.defaults[role];
  }

  /**
   *  حالت و سقفِ نهایی.
   *  @param {string} role
   *  @param {Array<Object>|null} layers  لایه‌ها به‌ترتیب (نقش، سپس کاربر)
   *  @param {string} key
   *  @returns {{mode: string, caps: Object}}
   */
  function resolve(role, layers, key) {
    var spec = CATALOG[key];
    var base = defaultMode(role, key);
    if (!spec || base === null) return { mode: 'deny', caps: {} };
    if (role === 'owner') return { mode: 'allow', caps: {} };
    var mode = base;
    var caps = {};
    if (spec.edit !== 'none' && layers) {
      for (var i = 0; i < layers.length; i++) {
        var o = layers[i] && has(layers[i], key) ? layers[i][key] : null;
        if (!o || typeof o !== 'object') continue;
        if (MODES.indexOf(o.mode) >= 0) {
          var m = o.mode;
          if (m === 'approve' && !spec.approvable) m = 'deny';
          if (spec.edit === 'restrict' && RANK[m] > RANK[base] && m !== 'approve') m = base;
          mode = m;
        }
        if (o.caps && typeof o.caps === 'object') {
          for (var j = 0; j < spec.caps.length; j++) {
            var name = spec.caps[j];
            if (!has(o.caps, name)) continue;
            var v = o.caps[name];
            if (v === null) delete caps[name];
            else if (isCapValue(v)) caps[name] = v;
          }
        }
      }
    }
    var out = {};
    var names = Object.keys(caps).sort();
    for (var c = 0; c < names.length; c++) out[names[c]] = caps[names[c]];
    return { mode: mode, caps: out };
  }

  /**
   *  تصمیم برای یک کارِ مشخص.
   *  @param {{pct?: number, amount?: number}} [req]
   *  @returns {{decision: string, reason: string, cap?: string, limit?: number}}
   */
  function evaluate(role, layers, key, req) {
    var r = resolve(role, layers, key);
    if (r.mode === 'deny') return { decision: 'deny', reason: 'denied' };
    if (r.mode === 'approve') return { decision: 'approve', reason: 'needs_approval' };
    var spec = CATALOG[key];
    for (var i = 0; i < spec.caps.length; i++) {
      var name = spec.caps[i];
      if (!has(r.caps, name)) continue;
      var value = req ? req[CAP_FIELDS[name]] : undefined;
      if (typeof value === 'number' && isFinite(value) && value > r.caps[name]) {
        return { decision: spec.approvable ? 'approve' : 'deny', reason: 'over_cap', cap: name, limit: r.caps[name] };
      }
    }
    return { decision: 'allow', reason: 'ok' };
  }

  /*
   *  درصدِ تخفیف برای سنجشِ سقف، به «صدمِ درصد» (عددِ صحیح؛ ۵٪ = ۵۰۰).
   *  برنامه‌ها تخفیف را تا افغانیِ کامل گرد می‌کنند (۵٪ِ ۳۳۳ می‌شود ۱۷)، پس
   *  تا نیم افغانی (۵۰ Minor) بخشیده می‌شود تا ۵٪ِ درست «بیش از ۵٪» نشود.
   *  جمعِ صفر یا منفی با تخفیفِ مثبت ⇐ ۱۰۰٪.
   */
  var ROUNDING_MINOR = 50;
  function discountBp(discountMinor, totalMinor) {
    var d = Number(discountMinor);
    var t = Number(totalMinor);
    if (!isFinite(d) || d <= 0) return 0;
    if (!isFinite(t) || t <= 0) return 10000;
    var forgiven = Math.max(0, Math.floor(d) - ROUNDING_MINOR);
    return Math.min(10000, Math.floor(forgiven * 10000 / Math.floor(t)));
  }
  function discountPct(discountMinor, totalMinor) {
    return discountBp(discountMinor, totalMinor) / 100;
  }

  /** بی درخواستِ عددی: آیا دکمه دیده شود و بی‌واسطه کار کند */
  function allows(role, layers, key) {
    return evaluate(role, layers, key, null).decision === 'allow';
  }

  /** نقشهٔ کاملِ همهٔ مجوزهای هسته برای یک نقش */
  function effective(role, layers) {
    var out = {};
    var ks = keys();
    for (var i = 0; i < ks.length; i++) out[ks[i]] = resolve(role, layers, ks[i]);
    return out;
  }

  /**
   *  سنجشِ یک ردیفِ لایه پیش از ذخیره — null یعنی درست است.
   *  @returns {null|string} کدِ خطا
   */
  function validate(role, key, o) {
    var spec = CATALOG[key];
    if (!spec) return 'unknown_permission';
    if (role === 'owner') return 'owner_fixed';
    var base = defaultMode(role, key);
    if (base === null) return 'unknown_role';
    if (spec.edit === 'none') return 'not_editable';
    if (!o || typeof o !== 'object' || Array.isArray(o)) return 'bad_override';
    var fields = Object.keys(o);
    for (var i = 0; i < fields.length; i++) {
      if (fields[i] !== 'mode' && fields[i] !== 'caps') return 'bad_override';
    }
    if (has(o, 'mode')) {
      if (MODES.indexOf(o.mode) < 0) return 'bad_mode';
      if (o.mode === 'approve' && !spec.approvable) return 'not_approvable';
      if (spec.edit === 'restrict' && RANK[o.mode] > RANK[base] && o.mode !== 'approve') return 'exceeds_role';
    }
    if (has(o, 'caps')) {
      if (!o.caps || typeof o.caps !== 'object' || Array.isArray(o.caps)) return 'bad_cap';
      var names = Object.keys(o.caps);
      for (var j = 0; j < names.length; j++) {
        var n = names[j];
        if (spec.caps.indexOf(n) < 0) return 'unknown_cap';
        var v = o.caps[n];
        if (v !== null && !isCapValue(v)) return 'bad_cap';
        if (n === 'maxPct' && v !== null && v > 100) return 'bad_cap';
      }
    }
    return null;
  }

  return {
    VERSION: VERSION,
    MODES: MODES,
    ROLES: ROLES,
    CAP_FIELDS: CAP_FIELDS,
    CATALOG: CATALOG,
    keys: keys,
    defaultMode: defaultMode,
    resolve: resolve,
    evaluate: evaluate,
    allows: allows,
    effective: effective,
    validate: validate,
    ROUNDING_MINOR: ROUNDING_MINOR,
    discountBp: discountBp,
    discountPct: discountPct,
  };
});
