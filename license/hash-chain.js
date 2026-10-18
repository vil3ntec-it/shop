/* ==========================================================
   فروشگاه من | زنجیرهٔ هش و «سندِ ثبت‌شده دست نمی‌خورد»
   ----------------------------------------------------------
   SEC-02 و STF-09 (زنجیرهٔ هش، بخشِ ۱۰.۳) و SEC-03 (اصلاح فقط با
   سندِ معکوس). خالص: نه DOM، نه fetch، نه localStorage — همان کدی که در
   مرورگر می‌دود در آزمونِ Node دویده می‌شود.

   ── قرارداد: «tohid-hashchain-v1» ──────────────────────────────
       hash = hex( SHA-256( ASCII(prevHash) ‖ UTF-8(canonicalJson(doc)) ) )
       prevHash ِ نخستین حلقه = GENESIS (۶۴ صفر)
   ⛔ همین قرارداد در `server/src/lib/hash-chain.js` و
   `android-native/.../core/audit/HashChain.kt` هست و هر سه با بردارهای
   `license/test-vectors/hashchain-v1.json` سنجیده می‌شوند. قالب عوض شد؟
   بردارِ v2 بساز؛ v1 دست نمی‌خورد.

   canonicalJson = زیرمجموعهٔ RFC 8785 (JCS): کلیدها به ترتیبِ UTF-16،
   رشته مثلِ JSON.stringify، نیم‌جفتِ تنها رد، عدد **فقط صحیح** با
   |n| ≤ 2^53−1 (مبلغ به کوچک‌ترین واحد)، ویژگیِ undefined نوشته نمی‌شود.

   ⚠️ SHA-256ِ همگام از `sync-core.js` می‌آید (TohidSyncCore.sha256Hex)؛ پس
   در صفحه این فایل **بعد از** sync-core.js بار می‌شود.

   ⚠️ هنوز به هیچ صفحه‌ای وصل نیست (پرچمِ `hashchain.*` خاموش است)؛ فقط
   منطق و آزمون. سیم‌کشیِ صندوق کارِ جداست.
   ========================================================== */
(function (factory) {
  'use strict';
  var sha = null;
  if (typeof module !== 'undefined' && module.exports) {
    sha = require('./sync-core.js').sha256Hex;
  } else {
    var g = typeof window !== 'undefined' ? window : globalThis;
    sha = function (t) { return g.TohidSyncCore.sha256Hex(t); };
  }
  var api = factory(sha);
  if (typeof module !== 'undefined' && module.exports) module.exports = api;
  if (typeof window !== 'undefined') window.TohidHashChain = api;
  else if (typeof globalThis !== 'undefined') globalThis.TohidHashChain = api;
})(function (sha256Hex) {
  'use strict';

  var FORMAT = 'tohid-hashchain-v1';
  var GENESIS = '0000000000000000000000000000000000000000000000000000000000000000';
  var HEX64 = /^[0-9a-f]{64}$/;

  function fail(code, message) {
    var e = new Error(message || code);
    e.code = code;
    return e;
  }

  /* ---------- ۱) canonicalJson ---------- */
  function quote(s) {
    for (var i = 0; i < s.length; i++) {
      var c = s.charCodeAt(i);
      if (c >= 0xd800 && c <= 0xdbff) {
        var d = s.charCodeAt(i + 1);
        if (!(d >= 0xdc00 && d <= 0xdfff)) throw fail('lone_surrogate');
        i++;
      } else if (c >= 0xdc00 && c <= 0xdfff) {
        throw fail('lone_surrogate');
      }
    }
    return JSON.stringify(s);
  }

  function canonicalJson(value) {
    if (value === null) return 'null';
    var t = typeof value;
    if (t === 'boolean') return value ? 'true' : 'false';
    if (t === 'string') return quote(value);
    if (t === 'number') {
      if (!Number.isSafeInteger(value)) throw fail('not_integer', 'عدد باید صحیح باشد: ' + value);
      return String(value === 0 ? 0 : value);
    }
    if (t !== 'object') throw fail('bad_value', 'نوعِ ' + t + ' در سند نمی‌آید');
    var i;
    if (Array.isArray(value)) {
      var parts = [];
      for (i = 0; i < value.length; i++) {
        if (value[i] === undefined) throw fail('bad_value', 'undefined در آرایه');
        parts.push(canonicalJson(value[i]));
      }
      return '[' + parts.join(',') + ']';
    }
    var proto = Object.getPrototypeOf(value);
    if (proto !== Object.prototype && proto !== null) throw fail('bad_value', 'فقط شیءِ ساده');
    var keys = Object.keys(value).sort();
    var out = [];
    for (i = 0; i < keys.length; i++) {
      if (value[keys[i]] === undefined) continue;
      out.push(quote(keys[i]) + ':' + canonicalJson(value[keys[i]]));
    }
    return '{' + out.join(',') + '}';
  }

  /* ---------- ۲) حلقه و زنجیره ---------- */
  function entryHash(prevHash, doc) {
    if (typeof prevHash !== 'string' || !HEX64.test(prevHash)) throw fail('bad_prev');
    return sha256Hex(prevHash + canonicalJson(doc));
  }

  /** حلقهٔ تازه پسِ آخرین حلقهٔ `entries` (خودِ آرایه دست نمی‌خورد). */
  function append(entries, doc) {
    var last = entries.length ? entries[entries.length - 1] : null;
    var prev = last ? last.hash : GENESIS;
    return { seq: entries.length + 1, prevHash: prev, hash: entryHash(prev, doc), doc: doc };
  }

  /** ⇒ { ok, length, head } یا { ok:false, index, seq, reason, expected, actual } */
  function verifyEntries(entries) {
    var prev = GENESIS;
    for (var i = 0; i < entries.length; i++) {
      var e = entries[i] || {};
      var no = function (reason, expected, actual) {
        return { ok: false, index: i, seq: i + 1, reason: reason, expected: expected, actual: actual };
      };
      if (e.seq !== undefined && e.seq !== i + 1) return no('seq_gap', i + 1, e.seq);
      if (e.prevHash !== prev) return no('prev_mismatch', prev, e.prevHash);
      var h;
      try { h = entryHash(prev, e.doc); } catch (err) { return no('bad_doc', null, err.code || err.message); }
      if (h !== e.hash) return no('hash_mismatch', h, e.hash);
      prev = h;
    }
    return { ok: true, length: entries.length, head: prev };
  }

  /* ---------- ۳) SEC-03: سندِ ثبت‌شده ---------- *
     سندِ v1: { id, series, no, type, status, postedAt, lines:[{qty, unitPrice,
     amount…}], payments:[{amount…}], total, discount, … } — همهٔ مبلغ‌ها
     صحیح به کوچک‌ترین واحد. سندِ «posted» هرگز عوض نمی‌شود؛ اصلاح یعنی
     سندِ تازه‌ای با `reverses: <id>` که مقدارها و مبلغ‌هایش قرینه‌اند.
     قیمتِ واحد (`unitPrice`) قرینه نمی‌شود: مقدارِ منفی × قیمتِ مثبت. */
  var NEGATED_KEYS = ['qty', 'amount', 'total', 'subtotal', 'discount', 'tax', 'paid', 'change'];
  var LINE_ARRAYS = ['lines', 'payments'];

  function isPosted(doc) {
    return !!doc && doc.status === 'posted';
  }

  /** ⇒ { ok:true } یا { ok:false, code:'posted_immutable' } */
  function guardEdit(before, after) {
    if (!isPosted(before)) return { ok: true };
    if (canonicalJson(before) === canonicalJson(after)) return { ok: true };
    return { ok: false, code: 'posted_immutable' };
  }

  function findReversal(docs, id) {
    if (id === undefined || id === null) return null;
    for (var i = 0; i < (docs || []).length; i++) if (docs[i] && docs[i].reverses === id) return docs[i];
    return null;
  }

  function negateIn(obj) {
    for (var k = 0; k < NEGATED_KEYS.length; k++) {
      var key = NEGATED_KEYS[k];
      if (!Object.prototype.hasOwnProperty.call(obj, key) || obj[key] === null) continue;
      if (!Number.isSafeInteger(obj[key])) throw fail('not_integer', key + ' باید صحیح باشد');
      obj[key] = obj[key] === 0 ? 0 : -obj[key];
    }
  }

  /**
   * سندِ معکوسِ `orig`. opts: { id, no, postedAt, reason, by, existing? }
   * خطا (code): not_posted | is_reversal | already_reversed | bad_opts | not_integer
   */
  function reverseDoc(orig, opts) {
    opts = opts || {};
    if (!isPosted(orig)) throw fail('not_posted', 'فقط سندِ ثبت‌شده معکوس می‌شود');
    if (orig.reverses !== undefined && orig.reverses !== null) throw fail('is_reversal', 'سندِ معکوس دوباره معکوس نمی‌شود');
    if (findReversal(opts.existing, orig.id)) throw fail('already_reversed', 'این سند پیش‌تر معکوس شده');
    var nonEmpty = function (s) { return typeof s === 'string' && s.trim() !== ''; };
    if (!nonEmpty(opts.id) || !nonEmpty(opts.reason) || !nonEmpty(opts.by)
        || !Number.isSafeInteger(opts.no) || opts.no < 1 || !Number.isSafeInteger(opts.postedAt)) {
      throw fail('bad_opts', 'id، no، postedAt، reason و by لازم‌اند');
    }
    canonicalJson(orig); // فقط سندِ معتبر
    var rev = JSON.parse(JSON.stringify(orig));
    negateIn(rev);
    for (var a = 0; a < LINE_ARRAYS.length; a++) {
      var arr = rev[LINE_ARRAYS[a]];
      if (!Array.isArray(arr)) continue;
      for (var j = 0; j < arr.length; j++) if (arr[j] && typeof arr[j] === 'object') negateIn(arr[j]);
    }
    rev.id = opts.id;
    rev.no = opts.no;
    rev.postedAt = opts.postedAt;
    rev.status = 'posted';
    rev.reverses = orig.id;
    rev.reason = opts.reason;
    rev.by = opts.by;
    return rev;
  }

  return {
    FORMAT: FORMAT,
    GENESIS: GENESIS,
    NEGATED_KEYS: NEGATED_KEYS,
    LINE_ARRAYS: LINE_ARRAYS,
    canonicalJson: canonicalJson,
    entryHash: entryHash,
    append: append,
    verifyEntries: verifyEntries,
    isPosted: isPosted,
    guardEdit: guardEdit,
    findReversal: findReversal,
    reverseDoc: reverseDoc,
  };
});
