/* ==========================================================
   فروشگاه من | «برگهٔ امضاشده»ی تأیید (STF-02، بخشِ ۱۰.۱)
   ----------------------------------------------------------
   همتای دقیقِ `core/src/commonMain/kotlin/ir/vil3ntec/tohid/core/perm/ApprovalSlip.kt`.
   هر دو با یک فایلِ مرجع سنجیده می‌شوند: `core/test-vectors/approval-v1.json`.
   سرور نسخهٔ عیناً همین فایل را در `server/src/lib/approval-core.js` دارد و
   آزمون برابریِ بایت‌به‌بایتِ دو نسخه را می‌سنجد. ⛔ یکی را بی دیگری عوض نکنید.

   ── چه چیزی امضا می‌شود ───────────────────────────────────────────
   مدیر یا صاحبِ دکان روی همان دستگاه PINِ خودش را می‌زند؛ PIN کلیدِ خصوصیِ
   او را باز می‌کند و این متن (UTF-8، خط‌ها با \n، بی خطِ خالیِ آخر) امضا
   می‌شود — ECDSA P-256 با SHA-256 (ES256):

     tohid-approval/1
     shop=<شناسهٔ دکان>
     doc=<جدول>/<شناسهٔ ردیف>      همان سندی که op رویش کار می‌کند
     perm=<کلیدِ مجوز>             sales.discount | sales.cancel | returns.create
     pct_bp=<صدمِ درصد یا خالی>    سقفِ درصدی که تأیید شد (۱۰٪ = ۱۰۰۰)
     amount=<Minor یا خالی>        سقفِ مبلغی که تأیید شد
     by=<شناسهٔ کاربرِ تأییدکننده>
     key=<شناسهٔ کلیدِ ثبت‌شده در سرور>
     iat=<میلی‌ثانیه>  exp=<میلی‌ثانیه>   حداکثر ۱۵ دقیقه
     nonce=<۱۶ تا ۶۴ نویسهٔ [A-Za-z0-9_-]>  یک‌بارمصرف
     reason=<دلیل، بی خطِ تازه، حداکثر ۲۰۰ نویسه>

   چرا «سند» و نه op_id: صندوق تأیید را پیش از ساختنِ op می‌گیرد (op بعد از
   بستنِ فاکتور در صف ساخته می‌شود)، ولی شناسهٔ سند همان لحظه معلوم است.
   سندِ مالی فقط‌افزودنی است، پس یک سند فقط یک بار درج و یک بار لغو می‌شود؛
   با nonceِ یک‌بارمصرف همان اثرِ op_id را دارد.

   این فایل هیچ چیزی از مرورگر نمی‌خواهد؛ همان کد در Node هم می‌دود.
   ========================================================== */
(function (factory) {
  'use strict';
  var api = factory();
  if (typeof module !== 'undefined' && module.exports) module.exports = api;
  if (typeof window !== 'undefined') window.TohidApproval = api;
  else if (typeof globalThis !== 'undefined') globalThis.TohidApproval = api;
})(function () {
  'use strict';

  var VERSION = 1;
  var HEADER = 'tohid-approval/1';
  var PERMS = ['returns.create', 'sales.cancel', 'sales.discount'];
  /** بیشترین عمرِ برگه: از PINِ مدیر تا بستنِ فاکتور */
  var TTL_MAX_MS = 15 * 60 * 1000;
  /** اختلافِ ساعتِ پذیرفتنی میانِ برگه و op */
  var SKEW_MS = 5 * 60 * 1000;
  var MAX_REASON = 200;
  var NONCE_RE = /^[A-Za-z0-9_-]{16,64}$/;
  var LINE_BREAK = /[\r\n]/;

  function has(o, k) { return o != null && Object.prototype.hasOwnProperty.call(o, k); }
  function isInt(v) { return typeof v === 'number' && isFinite(v) && Math.floor(v) === v && Math.abs(v) <= 9007199254740991; }
  function optInt(v) { return v === null || v === undefined ? '' : String(v); }
  function isText(v, max) { return typeof v === 'string' && v.length > 0 && v.length <= max && !LINE_BREAK.test(v); }

  /** متنِ دقیقی که امضا می‌شود */
  function payload(s) {
    return [
      HEADER,
      'shop=' + s.shop,
      'doc=' + s.doc,
      'perm=' + s.perm,
      'pct_bp=' + optInt(s.pct_bp),
      'amount=' + optInt(s.amount),
      'by=' + s.by,
      'key=' + s.key,
      'iat=' + s.iat,
      'exp=' + s.exp,
      'nonce=' + s.nonce,
      'reason=' + s.reason,
    ].join('\n');
  }

  /**
   *  شکلِ برگه — null یعنی درست است، وگرنه کدِ خطا.
   *  (امضا را سرور با کلیدِ ثبت‌شده می‌سنجد؛ این فقط شکل است.)
   */
  function check(s) {
    if (!s || typeof s !== 'object' || Array.isArray(s)) return 'bad_slip';
    if (s.v !== VERSION) return 'bad_version';
    if (!isText(s.shop, 80) || !isText(s.doc, 200) || !isText(s.by, 80) || !isText(s.key, 80)) return 'bad_slip';
    if (PERMS.indexOf(s.perm) < 0) return 'bad_perm';
    if (has(s, 'pct_bp') && s.pct_bp !== null && !(isInt(s.pct_bp) && s.pct_bp >= 0 && s.pct_bp <= 10000)) return 'bad_value';
    if (has(s, 'amount') && s.amount !== null && !(isInt(s.amount) && s.amount >= 0)) return 'bad_value';
    if (!isInt(s.iat) || !isInt(s.exp) || s.iat <= 0) return 'bad_time';
    if (s.exp <= s.iat || s.exp - s.iat > TTL_MAX_MS) return 'bad_time';
    if (typeof s.nonce !== 'string' || !NONCE_RE.test(s.nonce)) return 'bad_nonce';
    if (typeof s.reason !== 'string' || !s.reason.trim() || s.reason.length > MAX_REASON || LINE_BREAK.test(s.reason)) {
      return 'bad_reason';
    }
    if (typeof s.sig !== 'string' || !s.sig || s.sig.length > 200) return 'bad_sig';
    return null;
  }

  /**
   *  آیا برگه همین کار را می‌پوشاند؟ null یعنی بله، وگرنه کدِ خطا.
   *  @param need {perm, doc, pct_bp?, amount?, at}  — at = زمانِ op (میلی‌ثانیه)
   */
  function covers(s, need) {
    if (!need || s.perm !== need.perm || s.doc !== need.doc) return 'approval_mismatch';
    if (isInt(need.pct_bp) && need.pct_bp > 0) {
      if (!isInt(s.pct_bp) || need.pct_bp > s.pct_bp) return 'approval_too_small';
    }
    if (isInt(need.amount) && need.amount > 0) {
      if (!isInt(s.amount) || need.amount > s.amount) return 'approval_too_small';
    }
    if (isInt(need.at) && (need.at < s.iat - SKEW_MS || need.at > s.exp + SKEW_MS)) return 'approval_expired';
    return null;
  }

  /** درخواستِ عددی برای `PermCore.evaluate`ِ تأییدکننده — همان سقف‌هایی که امضا کرد */
  function evalReq(s) {
    var out = {};
    if (isInt(s.pct_bp)) out.pct = s.pct_bp / 100;
    if (isInt(s.amount)) out.amount = s.amount;
    return out;
  }

  return {
    VERSION: VERSION,
    HEADER: HEADER,
    PERMS: PERMS,
    TTL_MAX_MS: TTL_MAX_MS,
    SKEW_MS: SKEW_MS,
    MAX_REASON: MAX_REASON,
    payload: payload,
    check: check,
    covers: covers,
    evalReq: evalReq,
  };
});
