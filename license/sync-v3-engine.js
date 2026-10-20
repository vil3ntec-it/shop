/* ==========================================================
   فروشگاه من | موتورِ Sync v3 — سمتِ برنامه (وب)
   ----------------------------------------------------------
   بخشِ ۳.۸ِ مشخصات؛ SYN-01..08، SYN-10، SYN-11. قرارداد همان
   `server/src/routes/sync-v3.js` است.

   ⛔ **پیش‌فرض خاموش.** تنظیمِ هر دستگاه (`tohid-sync-engine`) پیش‌فرض
      `v1` است و تا کسی آگاهانه `v3` نکند، این فایل فقط تعریف می‌کند و
      هیچ کاری نمی‌کند: نه زمان‌سنج، نه درخواست، نه نوشتن.
   ⛔ **هم‌زادِ کاتلین** (`core/sync/v3/*` در android-native و `Hlc.kt` در
      core). هر سه با یک فایلِ بردار سنجیده می‌شوند (`core/test-vectors/
      hlc-v1.json` و `sync-v3-convert-v1.json`) که سرور از کدِ خودش می‌سازد.
   ⛔ **قاعدهٔ یگانهٔ مبلغ.** تبدیلِ دفترِ امروز (افغانیِ اعشاری) به
      `…Minor` فقط با `TohidMoney.moneyFromLegacy` (`license/money.js`) —
      همان `fromLegacy`ِ سرور و کاتلین؛ هیچ گردکردنِ دومی این‌جا نیست.

   ── چرخه ───────────────────────────────────────────────────────
     record   تفاضلِ دفتر با سایه (همان `TohidSyncCore.diff`ِ v1) ⇒ opِ v3
              با HLC؛ صف و سایه در **یک تراکنشِ IndexedDB** (SYN-02)
     push     دسته‌های کوچک (۶۴ تا ۲۵۶KB، وفقی)، عقب‌نشینیِ نمایی با
              پراکندگی؛ ۴۲۶ ⇒ نگه داشتن و «برنامه را به‌روز کنید» (SYN-11)
     pull     ادامه از cursor؛ ژورنال ⇐ دفتر ⇐ سایه + cursor (بازیابی پس از
              بسته شدنِ وسطِ کار، همان الگوی `SyncCommit`ِ کاتلین)
     conflicts فهرستِ صندوقِ تعارض برای وضعیت (SYN-07، SYN-08)

   ⛔ opی که سرور رد کرد از صف بیرون می‌رود ولی **گم نمی‌شود**: با دلیل در
      فهرستِ «ردشده» می‌ماند و سندِ محلی دست نمی‌خورد (بخشِ ۲۸.۴ و ۲۹.۵).
   ========================================================== */
(function (root, factory) {
  'use strict';
  var api = factory(root);
  if (typeof module !== 'undefined' && module.exports) module.exports = api;
  if (typeof window !== 'undefined') window.TohidSyncV3 = api;
})(typeof window !== 'undefined' ? window : globalThis, function (root) {
  'use strict';

  /* وابستگی‌ها دیر خوانده می‌شوند تا ترتیبِ بارگذاری مهم نباشد */
  function dep(name, file) {
    if (root[name]) return root[name];
    if (typeof require === 'function') {
      try { return require(file); } catch (e) { /* مرورگر */ }
    }
    throw new Error(name + ' بار نشده است (' + file + ')');
  }
  function Money() { return dep('TohidMoney', './money.js'); }
  function Core() { return dep('TohidSyncCore', './sync-core.js'); }

  function isPlainObject(v) { return v !== null && typeof v === 'object' && !Array.isArray(v); }
  function clone(v) { return v === undefined ? undefined : JSON.parse(JSON.stringify(v)); }
  function utf8Len(s) {
    var n = 0;
    for (var i = 0; i < s.length; i++) {
      var c = s.charCodeAt(i);
      if (c < 0x80) n += 1;
      else if (c < 0x800) n += 2;
      else if (c >= 0xd800 && c <= 0xdbff) { n += 4; i++; } else n += 3;
    }
    return n;
  }

  /* ==========================================================
     ۱) HLC — قرینهٔ دقیقِ server/src/lib/hlc.js
     ========================================================== */
  var SKEW_MS = 5 * 60 * 1000;
  var WALL_DIGITS = 15;
  var COUNTER_DIGITS = 6;
  var MAX_COUNTER = 999999;
  var HLC_RE = /^(\d{1,15}):(\d{1,6}):([A-Za-z0-9_.-]{1,64})$/;

  function pad(n, w) { var s = String(n); while (s.length < w) s = '0' + s; return s; }
  function hlcParse(s) {
    if (typeof s !== 'string') return null;
    var m = HLC_RE.exec(s);
    if (!m) return null;
    var wall = Number(m[1]);
    var counter = Number(m[2]);
    if (!Number.isSafeInteger(wall) || !Number.isSafeInteger(counter)) return null;
    return { wall: wall, counter: counter, node: m[3] };
  }
  function hlcFormat(t) {
    return pad(Math.max(0, Math.trunc(t.wall)), WALL_DIGITS) + ':'
      + pad(Math.max(0, Math.trunc(t.counter)), COUNTER_DIGITS) + ':' + t.node;
  }
  function hlcCanonical(s) { var p = hlcParse(s); return p ? hlcFormat(p) : ''; }
  function asTuple(x) { return typeof x === 'string' ? hlcParse(x) : x; }
  function hlcCompare(a, b) {
    var x = asTuple(a);
    var y = asTuple(b);
    if (x.wall !== y.wall) return x.wall < y.wall ? -1 : 1;
    if (x.counter !== y.counter) return x.counter < y.counter ? -1 : 1;
    if (x.node === y.node) return 0;
    return x.node < y.node ? -1 : 1;
  }
  function hlcFromTs(ts, counter, node) {
    var n = Number(ts);
    var wall = Number.isFinite(n) && n > 0 ? Math.min(Math.trunc(n), Math.pow(10, WALL_DIGITS) - 1) : 0;
    return hlcFormat({ wall: wall, counter: Math.min(Math.max(0, counter | 0), MAX_COUNTER), node: node });
  }
  function createClock(o) {
    var opts = o || {};
    var node = opts.node || 'web';
    var now = opts.now || Date.now;
    var skewMs = opts.skewMs === undefined ? SKEW_MS : opts.skewMs;
    var last = { wall: 0, counter: 0 };
    function bump(wall, counter) {
      if (counter > MAX_COUNTER) { wall += 1; counter = 0; }
      last = { wall: wall, counter: counter };
      return hlcFormat({ wall: wall, counter: counter, node: node });
    }
    return {
      node: node,
      skewMs: skewMs,
      tick: function () {
        var pt = now();
        if (pt > last.wall) return bump(pt, 0);
        return bump(last.wall, last.counter + 1);
      },
      recv: function (remote) {
        var r = asTuple(remote);
        var pt = now();
        if (!r) return { skewed: false, hlc: this.tick() };
        if (r.wall > pt + skewMs) return { skewed: true, hlc: hlcFormat({ wall: last.wall, counter: last.counter, node: node }) };
        var wall = Math.max(last.wall, r.wall, pt);
        var counter;
        if (wall === last.wall && wall === r.wall) counter = Math.max(last.counter, r.counter) + 1;
        else if (wall === last.wall) counter = last.counter + 1;
        else if (wall === r.wall) counter = r.counter + 1;
        else counter = 0;
        return { skewed: false, hlc: bump(wall, counter) };
      },
      observe: function (seen) {
        var s = asTuple(seen);
        if (!s) return;
        if (s.wall > last.wall || (s.wall === last.wall && s.counter > last.counter)) {
          last = { wall: s.wall, counter: s.counter };
        }
      },
      isSkewed: function (remote) {
        var r = asTuple(remote);
        return !!r && r.wall > now() + skewMs;
      },
      peek: function () { return hlcFormat({ wall: last.wall, counter: last.counter, node: node }); },
    };
  }

  /* ==========================================================
     ۲) جدول‌ها و سیاست‌ها — قرینهٔ server/src/lib/sync-v3-schema.js
     (آزمون با `tables`ِ بردارِ مبدل که سرور می‌سازد)
     ========================================================== */
  var TABLES = {
    products: { policy: 'master', money: ['purchasePrice', 'salePrice', 'wholesalePrice'], qty: ['minStock'], derived: ['stock'] },
    debtors: { policy: 'master', derived: ['balance', 'totalDebt'] },
    suppliers: { policy: 'master', derived: ['balance'] },
    expenses: { policy: 'master', money: ['amount'] },
    productCategories: { policy: 'master' },
    productUnits: { policy: 'master' },
    expenseCategories: { policy: 'master' },
    sales: {
      policy: 'doc',
      money: ['total', 'discount', 'finalTotal', 'paidAmount', 'remaining', 'debtGiven', 'debtSettled'],
      counters: ['paidAmountMinor', 'remainingMinor', 'debtGivenMinor', 'debtSettledMinor'],
      cancel: 'sales.cancel', localOnly: ['syncStatus'],
    },
    saleItems: { policy: 'doc', money: ['unitPrice', 'purchasePrice', 'totalPrice'], qty: ['quantity', 'returnedQty'], counters: ['returnedQtyMilli'] },
    returns: { policy: 'doc', money: ['amount'], qty: ['quantity'], cancel: 'data.delete.any' },
    transactions: { policy: 'doc', money: ['amount'], cancel: 'data.delete.any' },
    purchases: {
      policy: 'doc', money: ['purchasePrice', 'totalAmount', 'paidAmount', 'debt'], qty: ['quantity'],
      counters: ['paidAmountMinor', 'debtMinor'], cancel: 'data.delete.any',
    },
    supplierPayments: { policy: 'doc', money: ['amount'], cancel: 'data.delete.any' },
    warehouseEntries: { policy: 'doc', money: ['price'], qty: ['cartons', 'perCarton', 'units'], cancel: 'data.delete.any' },
    priceHistory: { policy: 'log', money: ['oldPrice', 'newPrice'] },
    auditLog: { policy: 'log', money: ['amount'] },
    stockMovements: { policy: 'moves', qty: ['qty'] },
    stockCounts: { policy: 'count', qty: ['countedQty'], v3only: true },
  };
  var KINDS = {
    master: ['insert', 'update', 'delete', 'delta'],
    doc: ['insert', 'delta', 'cancel'],
    log: ['insert'],
    moves: ['insert'],
    count: ['insert'],
  };
  var ALL_KINDS = ['insert', 'update', 'delete', 'delta', 'cancel'];
  var OP_ID_RE = /^[A-Za-z0-9_.-]{1,80}$/;
  var MAX_FIELDS_BYTES = 64 * 1024;

  var SPECS = {};
  Object.keys(TABLES).forEach(function (name) {
    var t = TABLES[name];
    var money = t.money || [];
    var qty = t.qty || [];
    var counters = t.counters || [];
    var intFields = {};
    var legacyNames = {};
    money.forEach(function (f) { intFields[f + 'Minor'] = true; legacyNames[f] = true; });
    qty.forEach(function (f) { intFields[f + 'Milli'] = true; legacyNames[f] = true; });
    SPECS[name] = {
      name: name, policy: t.policy, money: money, qty: qty, counters: counters,
      derived: t.derived || [], localOnly: t.localOnly || [], cancel: t.cancel || '', v3only: !!t.v3only,
      kinds: KINDS[t.policy].filter(function (k) { return (k !== 'cancel' || t.cancel) && (k !== 'delta' || counters.length); }),
      intFields: intFields, legacyNames: legacyNames,
    };
  });
  function specOf(table) { return typeof table === 'string' && Object.prototype.hasOwnProperty.call(SPECS, table) ? SPECS[table] : null; }

  /* ==========================================================
     ۳) مبدل: opِ دفترِ امروز (شکلِ v1) ⇒ opِ v3
     همان `fromV2`ِ سرور، با همان پسوندها و همان دلتا/لغو؛ فرقش فقط
     این است که HLC را ساعتِ همین دستگاه می‌دهد.
     ========================================================== */
  var DEFAULT_CANCEL_REASON = 'لغو از نسخهٔ قدیمیِ برنامه';
  var DELETE_CANCEL_REASON = 'حذف از نسخهٔ قدیمیِ برنامه';

  function isInc(v) { return isPlainObject(v) && Object.keys(v).length === 1 && Number.isFinite(v.$inc); }

  /** عدد ⇒ واحدِ کوچک با **همان** fromLegacy (license/money.js)؛ نامفهوم ⇒ undefined */
  function legacyNumber(x) {
    if (typeof x === 'string' && x.trim() !== '') x = Number(x);
    return typeof x === 'number' && Number.isFinite(x) ? x : undefined;
  }
  function toMinor(x) {
    var n = legacyNumber(x);
    if (n === undefined) return undefined;
    var v;
    try { v = Money().moneyFromLegacy(n, 'AFN').minor; } catch (e) { return undefined; }
    return v === 0 ? 0 : v;
  }
  function toMilli(x) {
    var n = legacyNumber(x);
    if (n === undefined) return undefined;
    var v;
    try { v = Money().qtyFromLegacy(n).milli; } catch (e) { return undefined; }
    return v === 0 ? 0 : v;
  }

  function convertFields(spec, fields) {
    var set = {};
    var inc = {};
    var dropped = [];
    Object.keys(fields || {}).forEach(function (k) {
      var v = fields[k];
      if (k === 'id') return;
      if (spec.derived.indexOf(k) >= 0 || spec.localOnly.indexOf(k) >= 0) { dropped.push(k); return; }
      var unit = spec.money.indexOf(k) >= 0 ? ['Minor', toMinor] : spec.qty.indexOf(k) >= 0 ? ['Milli', toMilli] : null;
      if (isInc(v)) {
        if (unit) {
          var d = unit[1](v.$inc);
          if (d !== undefined) { inc[k + unit[0]] = d; return; }
        } else { inc[k] = v.$inc; return; }
        set[k] = v;
        return;
      }
      if (unit) {
        if (v === null) { set[k + unit[0]] = null; return; }
        var n = unit[1](v);
        if (n !== undefined) { set[k + unit[0]] = n; return; }
      }
      set[k] = v;
    });
    return { set: set, inc: inc, dropped: dropped };
  }

  /** @returns {{ok:boolean, ops:Array, dropped:string[], native:boolean, reason:string}} */
  function fromLegacyOp(op, stamp) {
    var spec = specOf(op && op.table);
    if (!spec || spec.v3only) return { ok: false, ops: [], dropped: [], native: false, reason: 'unknown_table' };
    var base = { tx: op.op_id, hlc: stamp, table: op.table, row_id: op.row_id };
    var conv = op.type === 'delete' ? { set: {}, inc: {}, dropped: [] } : convertFields(spec, op.fields);
    var set = conv.set;
    var parts = [];
    var counterInc = {};
    Object.keys(conv.inc).forEach(function (k) {
      if (spec.counters.indexOf(k) >= 0) counterInc[k] = conv.inc[k];
      else set[k] = { $inc: conv.inc[k] };
    });
    if (op.type === 'insert') {
      parts.push(['i', { kind: 'insert', fields: set }]);
    } else if (op.type === 'delete') {
      if (spec.cancel) parts.push(['c', { kind: 'cancel', reason: DELETE_CANCEL_REASON, fromDelete: true }]);
      else parts.push(['x', { kind: 'delete' }]);
    } else if (spec.policy === 'master') {
      if (Object.keys(set).length) parts.push(['u', { kind: 'update', fields: set }]);
    } else if (spec.cancel && set.status === 'cancelled') {
      var reason = typeof set.cancelReason === 'string' && set.cancelReason.trim()
        ? set.cancelReason.trim().slice(0, 200) : DEFAULT_CANCEL_REASON;
      delete set.status;
      delete set.cancelReason;
      parts.push(['c', { kind: 'cancel', reason: reason }]);
    }
    if (Object.keys(counterInc).length) parts.push(['d', { kind: 'delta', fields: counterInc }]);
    if (op.type === 'update' && spec.policy !== 'master' && Object.keys(set).length) {
      parts.push(['e', { kind: 'update', fields: set }]);
    }
    var ops = parts.map(function (p) {
      var o = Object.assign({}, base, p[1]);
      o.op_id = parts.length === 1 ? op.op_id : op.op_id + '.' + p[0];
      return o;
    });
    var reason2 = '';
    for (var i = 0; i < ops.length && !reason2; i++) reason2 = nativeReason(ops[i]);
    return { ok: true, ops: ops, dropped: conv.dropped, native: !reason2, reason: reason2 };
  }

  /**
   *  آیا سرور این op را **به‌عنوانِ v3ِ بومی** می‌پذیرد؟ قرینهٔ `validate(raw,'v3')`ِ
   *  سرور (بردارها می‌سنجند). opی که نپذیرد با `format: "v2"` می‌رود تا خودِ سرور
   *  با قاعدهٔ خودش تبدیلش کند (مثلاً ویرایشِ سندِ مالی ⇒ صندوقِ تعارض) — گم نمی‌شود.
   *  @returns '' یا دلیل
   */
  function nativeReason(r) {
    var opId = typeof r.op_id === 'string' ? r.op_id : '';
    if (!OP_ID_RE.test(opId)) return 'bad_op_id';
    var spec = specOf(r.table);
    if (!spec) return 'unknown_table';
    if (typeof r.row_id !== 'string' || !r.row_id || r.row_id.length > 80) return 'bad_row_id';
    if (opId.indexOf('srv.') === 0 || r.row_id.indexOf('srv.') === 0) return 'reserved_id';
    if (ALL_KINDS.indexOf(r.kind) < 0) return 'bad_kind';
    if (spec.kinds.indexOf(r.kind) < 0) return spec.policy === 'master' ? 'bad_kind' : 'append_only';
    if (!hlcCanonical(r.hlc)) return 'bad_hlc';
    if (r.kind === 'delete' || r.kind === 'cancel') {
      if (r.fields !== undefined && r.fields !== null && !(isPlainObject(r.fields) && !Object.keys(r.fields).length)) return 'unexpected_fields';
    } else {
      if (!isPlainObject(r.fields)) return 'bad_fields';
      var keys = Object.keys(r.fields);
      if (!keys.length) return 'empty_fields';
      for (var i = 0; i < keys.length; i++) {
        var k = keys[i];
        var v = r.fields[k];
        if (!k || k.length > 64 || k.charAt(0) === '$' || k === 'id') return 'bad_field_name';
        if (r.kind === 'delta') {
          if (spec.counters.indexOf(k) < 0) return 'not_a_counter';
          if (!Number.isSafeInteger(v)) return 'not_integer';
          continue;
        }
        if (spec.legacyNames[k]) return 'legacy_field';
        if (spec.derived.indexOf(k) >= 0) return 'derived_field';
        if (isPlainObject(v) && Object.keys(v).some(function (x) { return x.charAt(0) === '$'; })) return 'bad_value';
        if (spec.intFields[k] && v !== null && !Number.isSafeInteger(v)) return 'not_integer';
      }
      if (utf8Len(JSON.stringify(r.fields)) > MAX_FIELDS_BYTES) return 'fields_too_large';
      if (r.kind === 'insert') {
        if (spec.policy === 'moves' && (typeof r.fields.productId !== 'string' || !r.fields.productId
          || !Number.isSafeInteger(r.fields.qtyMilli))) return 'bad_move';
        if (spec.policy === 'count' && (typeof r.fields.productId !== 'string' || !r.fields.productId
          || !Number.isSafeInteger(r.fields.countedQtyMilli))) return 'bad_count';
      }
    }
    if (r.kind === 'cancel' && !(typeof r.reason === 'string' && r.reason.trim())) return 'reason_required';
    if (r.tx !== undefined && r.tx !== null && String(r.tx).length > 80) return 'bad_tx';
    return '';
  }

  /** دادهٔ v3 (`…Minor`/`…Milli`) ⇒ نام و واحدِ دفترِ امروز. تقسیمِ دقیق، بی گردکردن. */
  function dataToLegacy(spec, data) {
    var out = {};
    Object.keys(data || {}).forEach(function (k) {
      var v = data[k];
      var m = /^(.*)(Minor|Milli)$/.exec(k);
      if (m && (m[2] === 'Minor' ? spec.money : spec.qty).indexOf(m[1]) >= 0) {
        out[m[1]] = v === null ? null : (typeof v === 'number' ? v / (m[2] === 'Minor' ? 100 : 1000) : v);
        return;
      }
      out[k] = v;
    });
    return out;
  }
  function incToLegacy(spec, inc) {
    var out = {};
    Object.keys(inc || {}).forEach(function (k) {
      var conv = dataToLegacy(spec, (function () { var o = {}; o[k] = inc[k]; return o; })());
      var name = Object.keys(conv)[0];
      out[name] = { $inc: conv[name] };
    });
    return out;
  }

  /** یک opِ رسیده از `pull` ⇒ opِ شکلِ v1 برای `TohidSyncCore.applyRemote`؛ null یعنی ربطی به دفترِ امروز ندارد */
  function effectToLegacy(item) {
    var spec = specOf(item && item.table);
    var e = item && item.effect;
    if (!spec || spec.v3only || !isPlainObject(e)) return null;
    var base = { op_id: item.op_id, table: item.table, row_id: item.row_id, server_seq: item.seq };
    if (e.deleted) return Object.assign(base, { type: 'delete' });
    var fields = {};
    if (isPlainObject(e.set)) Object.assign(fields, dataToLegacy(spec, e.set));
    if (isPlainObject(e.inc)) Object.assign(fields, incToLegacy(spec, e.inc));
    if (!Object.keys(fields).length) return null;
    return Object.assign(base, { type: item.kind === 'insert' ? 'insert' : 'update', fields: fields });
  }

  /** پاسخِ `/snapshot`ِ v3 ⇒ همان شکلی که `fromSnapshot`ِ v1 می‌خواند */
  function snapshotToLegacy(body) {
    var tables = {};
    var src = (body && body.tables) || {};
    Object.keys(src).forEach(function (name) {
      var spec = specOf(name);
      if (!spec || spec.v3only) return;
      tables[name] = (src[name] || []).map(function (r) { return { id: r.id, data: dataToLegacy(spec, r.data) }; });
    });
    return { tables: tables, cursor: Number(body && body.cursor) || 0 };
  }

  /* ==========================================================
     ۴) انبارِ محلی — صف، ردشده‌ها، حال؛ هر commit یک تراکنش
     ========================================================== */
  function MemoryStore() {
    this.data = { meta: null, journal: null, outbox: {}, rejected: {} };
    this.commits = 0;
  }
  MemoryStore.prototype.load = function () {
    var d = this.data;
    var sorted = function (m) { return Object.keys(m).sort().map(function (k) { return clone(m[k]); }); };
    return Promise.resolve({ meta: clone(d.meta), journal: clone(d.journal), outbox: sorted(d.outbox), rejected: sorted(d.rejected) });
  };
  MemoryStore.prototype.commit = function (ch) {
    //  همه یا هیچ: روی نسخهٔ کپی می‌نویسد و آخر جا می‌اندازد
    var next = clone(this.data);
    if (this.failNext) { var f = this.failNext; this.failNext = null; return Promise.reject(f); }
    applyChange(next, ch);
    this.data = next;
    this.commits++;
    return Promise.resolve();
  };
  function applyChange(d, ch) {
    if (ch.meta) d.meta = clone(ch.meta);
    if (Object.prototype.hasOwnProperty.call(ch, 'journal')) d.journal = ch.journal ? clone(ch.journal) : null;
    (ch.removeOutbox || []).forEach(function (k) { delete d.outbox[k]; });
    (ch.addOutbox || []).forEach(function (e) { d.outbox[e.key] = clone(e); });
    (ch.addRejected || []).forEach(function (e) { d.rejected[e.key] = clone(e); });
    (ch.removeRejected || []).forEach(function (k) { delete d.rejected[k]; });
  }

  /** IndexedDB — صف، ردشده‌ها و حال در **یک** تراکنشِ readwrite (SYN-02) */
  function IdbStore(name, idb) {
    this.name = name;
    this.idb = idb || root.indexedDB;
    this.db = null;
  }
  IdbStore.prototype.open = function () {
    var self = this;
    if (self.db) return Promise.resolve(self.db);
    return new Promise(function (resolve, reject) {
      var req = self.idb.open(self.name, 1);
      req.onupgradeneeded = function () {
        var db = req.result;
        if (!db.objectStoreNames.contains('meta')) db.createObjectStore('meta');
        if (!db.objectStoreNames.contains('outbox')) db.createObjectStore('outbox', { keyPath: 'key' });
        if (!db.objectStoreNames.contains('rejected')) db.createObjectStore('rejected', { keyPath: 'key' });
      };
      req.onsuccess = function () { self.db = req.result; resolve(self.db); };
      req.onerror = function () { reject(req.error); };
    });
  };
  IdbStore.prototype.load = function () {
    return this.open().then(function (db) {
      return new Promise(function (resolve, reject) {
        var t = db.transaction(['meta', 'outbox', 'rejected'], 'readonly');
        var out = {};
        var r1 = t.objectStore('meta').get('state');
        var r2 = t.objectStore('meta').get('journal');
        var r3 = t.objectStore('outbox').getAll();
        var r4 = t.objectStore('rejected').getAll();
        t.oncomplete = function () {
          out.meta = r1.result || null;
          out.journal = r2.result || null;
          out.outbox = (r3.result || []).sort(function (a, b) { return a.key < b.key ? -1 : a.key > b.key ? 1 : 0; });
          out.rejected = (r4.result || []).sort(function (a, b) { return a.key < b.key ? -1 : a.key > b.key ? 1 : 0; });
          resolve(out);
        };
        t.onerror = function () { reject(t.error); };
        t.onabort = function () { reject(t.error || new Error('abort')); };
      });
    });
  };
  IdbStore.prototype.commit = function (ch) {
    return this.open().then(function (db) {
      return new Promise(function (resolve, reject) {
        var t = db.transaction(['meta', 'outbox', 'rejected'], 'readwrite');
        var meta = t.objectStore('meta');
        var outbox = t.objectStore('outbox');
        var rej = t.objectStore('rejected');
        if (ch.meta) meta.put(clone(ch.meta), 'state');
        if (Object.prototype.hasOwnProperty.call(ch, 'journal')) {
          if (ch.journal) meta.put(clone(ch.journal), 'journal'); else meta['delete']('journal');
        }
        (ch.removeOutbox || []).forEach(function (k) { outbox['delete'](k); });
        (ch.addOutbox || []).forEach(function (e) { outbox.put(clone(e)); });
        (ch.addRejected || []).forEach(function (e) { rej.put(clone(e)); });
        (ch.removeRejected || []).forEach(function (k) { rej['delete'](k); });
        t.oncomplete = function () { resolve(); };
        t.onerror = function () { reject(t.error); };
        t.onabort = function () { reject(t.error || new Error('abort')); };
      });
    });
  };

  /* ==========================================================
     ۵) شبکه — هر پاسخ {status, body}؛ فقط قطعِ شبکه پرتاب می‌کند
     ========================================================== */
  /** با `fetch` و توکن (آزمونِ Node و هر جای بی `TohidAccount`) */
  function FetchTransport(baseUrl, token, extraHeaders) {
    return {
      request: function (method, path, body) {
        var headers = Object.assign({ 'Content-Type': 'application/json', 'Accept-Encoding': 'gzip' }, extraHeaders || {});
        var t = typeof token === 'function' ? token() : token;
        if (t) headers.Authorization = 'Bearer ' + t;
        return fetch(baseUrl + path, {
          method: method, headers: headers, body: body === undefined ? undefined : JSON.stringify(body),
        }).then(function (res) {
          return res.text().then(function (text) {
            var json = null;
            try { json = text ? JSON.parse(text) : null; } catch (e) { json = null; }
            return { status: res.status, body: json };
          });
        });
      },
    };
  }
  /** با `TohidAccount.authFetch` (نوسازیِ توکن و سرآیندهای قرارداد از همان‌جا) */
  function AccountTransport(acc) {
    return {
      request: function (method, path, body) {
        return acc.authFetch(path, body === undefined ? { method: method } : { method: method, body: body })
          .then(function (data) { return { status: 200, body: data }; }, function (e) {
            if (e && e.status) return { status: e.status, body: e.body || { error: e.code, message: e.message } };
            throw e;
          });
      },
    };
  }

  /* ==========================================================
     ۶) موتور
     ========================================================== */
  var PROTOCOL = 3;
  var LEGACY_SCHEMA = 3;              //  همان SCHEMA_VERSIONِ sync-core.js — برای `format: "v2"`
  var DONE = { applied: true, partial: true, conflict: true, duplicate: true };
  var MIN_BYTES = 64 * 1024;
  var MAX_BYTES = 256 * 1024;
  var MAX_OPS = 200;                 //  سرور تا ۵۰۰ می‌پذیرد
  var TX_GROUP_MAX = 50;             //  ثبتی بزرگ‌تر (مثلِ نخستین بارگذاری) بی tx ِ مشترک
  var UPGRADE_MESSAGE = 'همگام‌سازی نگه داشته شد: برنامه را به‌روز کنید. تغییرهای شما روی همین دستگاه مانده‌اند.';

  function errorCode(body) {
    if (!body) return '';
    if (typeof body.error === 'string') return body.error;
    if (body.error && body.error.code) return body.error.code;
    return '';
  }
  function errorMessage(status, body) {
    if (body && typeof body.message === 'string' && body.message) return body.message;
    if (body && body.error && body.error.message) return body.error.message;
    return 'خطای سرور (' + status + ')';
  }
  function emptyMeta() {
    return {
      v: 1, initialized: false, shadow: null, cursor: 0, clock: '', offsetMs: 0, seq: 0,
      lastOkAt: 0, lastPushAt: 0, lastPullAt: 0, lastError: '', lastErrorAt: 0, attempt: 0,
      upgrade: null, conflictsOpen: 0, pushBytes: MIN_BYTES, pullBytes: MIN_BYTES,
    };
  }
  /** اثرِ انگشتِ پایدار (FNV-1a) برای ژورنال — رمزنگاری نیست، فقط «همان است؟» */
  function fingerprint(s) {
    var h = 0x811c9dc5;
    for (var i = 0; i < s.length; i++) { h ^= s.charCodeAt(i); h = Math.imul(h, 0x01000193) >>> 0; }
    return ('0000000' + h.toString(16)).slice(-8) + ':' + s.length;
  }
  function ledgerPrint(data) { return fingerprint(Core().canonical(Core().shadowOf(data || {}))); }

  /**
   *  @param o {transport, store, deviceId, ledger:{read, write}, now?, random?, protocol?, failpoint?}
   */
  function Engine(o) {
    if (!o || !o.transport || !o.store || !o.ledger) throw new Error('transport، store و ledger لازم است');
    this.transport = o.transport;
    this.store = o.store;
    this.ledger = o.ledger;
    this.deviceId = String(o.deviceId || 'web').replace(/[^A-Za-z0-9_.-]/g, '-').slice(0, 64) || 'web';
    this.rawNow = o.now || Date.now;
    this.random = o.random || Math.random;
    this.protocol = o.protocol || PROTOCOL;
    this.failpoint = o.failpoint || function () { };
    this.meta = null;
    this.outbox = [];
    this.rejectedList = [];
    this.conflictList = [];
    this.listeners = [];
    this.busy = false;
    //  هر کاری که صف، سایه یا حال را می‌نویسد پشتِ همین زنجیر است: دو ثبتِ هم‌زمان یا ثبتی
    //  وسطِ نشاندنِ Pull سایه را عقب نمی‌برد (وگرنه همان تغییر با شناسهٔ تازه دوباره می‌رفت)
    this.chain = Promise.resolve();
    var self = this;
    this.clock = createClock({ node: this.deviceId, now: function () { return self.now(); } });
  }
  Engine.prototype.locked = function (fn) {
    var self = this;
    var run = this.chain.then(function () { return fn.call(self); });
    this.chain = run.then(function () { }, function () { });
    return run;
  };

  Engine.prototype.now = function () { return this.rawNow() + ((this.meta && this.meta.offsetMs) || 0); };

  Engine.prototype.load = async function () {
    var s = await this.store.load();
    this.meta = Object.assign(emptyMeta(), s.meta || {});
    this.outbox = s.outbox || [];
    this.rejectedList = s.rejected || [];
    this.clock.observe(this.meta.clock);
    if (s.journal) await this.recover(s.journal);
    return this;
  };

  /** ژورنالِ نشاندنی که وسطش بسته شد: دفتر نشسته؟ تمام کن : برگرد (همان Pull دوباره می‌آید) */
  Engine.prototype.recover = async function (j) {
    var shadowNow = fingerprint(Core().canonical(this.meta.shadow || {}));
    var meta = clone(this.meta);
    var outcome = 'back';
    if (shadowNow === j.after) {
      outcome = 'already';
    } else if (shadowNow === j.before && ledgerPrint(this.ledger.read()) === j.ledger) {
      meta.shadow = Core().shadowOf(Core().applyRemote(this.meta.shadow || {}, j.ops).data);
      meta.cursor = j.cursor;
      outcome = 'forward';
    }
    await this.store.commit(outcome === 'forward' ? { meta: meta, journal: null } : { journal: null });
    if (outcome === 'forward') this.meta = meta;
    return outcome;
  };

  Engine.prototype.persist = function (patch, extra) {
    var meta = Object.assign(clone(this.meta), patch || {});
    meta.clock = this.clock.peek();
    var ch = Object.assign({ meta: meta }, extra || {});
    var self = this;
    return this.store.commit(ch).then(function () { self.meta = meta; return meta; });
  };

  Engine.prototype.call = async function (method, path, body) {
    var r = await this.transport.request(method, '/api/sync/v3' + path, body);
    var b = r && r.body;
    if (b && Number.isFinite(Number(b.server_time)) && Number(b.server_time) > 0) {
      //  اختلافِ ساعت از `server_time` (بخشِ ۳.۸.۱ بندِ ۸)؛ HLC و ULID از زمانِ تصحیح‌شده
      this.meta.offsetMs = Math.round(Number(b.server_time) - this.rawNow());
    }
    if (b && typeof b.hlc === 'string') this.clock.recv(b.hlc);
    return r;
  };

  /**
   *  راه‌اندازی روی این دستگاه (تازه، یا گذر از v1): Snapshot ⇒ دفتر و سایه و cursor.
   *  @param opts.keepLocalOnly ردیف‌هایی که فقط این‌جا هستند می‌مانند و با نخستین
   *         ثبت «درج» می‌روند (دستگاهی که هرگز همگام نشده). دستگاهی که با v1 همگام
   *         بوده `false` می‌دهد: ردیفِ نبود در سرور یعنی جای دیگری پاک شده است.
   */
  Engine.prototype.initialize = async function (opts) {
    var o = opts || {};
    var r = await this.call('GET', '/snapshot');
    if (r.status !== 200 || !r.body) return this.fail(r.status, r.body);
    if (r.body.complete === false) return { ok: false, reason: 'incomplete' };
    return this.locked(async function () {
    var legacy = snapshotToLegacy(r.body);
    var C = Core();
    var data = this.ledger.read() || {};
    var base = C.fromSnapshot(legacy, data);
    var next;
    var shadow;
    if (o.keepLocalOnly) {
      //  سایه = فقط آنچه سرور دارد ⇒ ردیف‌های تنهای این دستگاه با نخستین ثبت «درج» می‌روند
      next = unionRows(base, data);
      shadow = C.shadowOf(C.fromSnapshot(legacy, {}));
    } else {
      //  همان «بازیابی از سرور»ِ v1: دفتر = سرور، چیزی برای فرستادن نیست
      next = base;
      shadow = C.shadowOf(base);
    }
    this.ledger.write(next);
    await this.persist({ initialized: true, shadow: shadow, cursor: legacy.cursor, lastOkAt: this.now() });
    this.emit();
    return { ok: true, rows: Number(r.body.rows) || 0, cursor: legacy.cursor };
    });
  };

  function unionRows(base, extra) {
    var C = Core();
    var out = C.cloneLedger(base);
    C.COLLECTIONS.forEach(function (key) {
      var mine = out[key] || [];
      var ids = {};
      mine.forEach(function (r) { ids[r.id] = true; });
      (extra[key] || []).forEach(function (r) { if (r && !ids[r.id]) mine.push(clone(r)); });
      out[key] = mine;
    });
    C.LIST_COLLECTIONS.forEach(function (key) {
      if (!Array.isArray(extra[key])) return;
      var names = Array.isArray(out[key]) ? out[key].slice() : [];
      extra[key].forEach(function (n) { if (names.indexOf(n) < 0) names.push(n); });
      out[key] = names;
    });
    return out;
  }

  /** ثبتِ محلی: تفاضل ⇒ opها؛ صف و سایه در یک تراکنش. @returns چند op */
  Engine.prototype.record = function () { return this.locked(this.recordNow); };
  Engine.prototype.recordNow = async function () {
    if (!this.meta || !this.meta.initialized) return 0;
    var data = this.ledger.read();
    if (!data) return 0;
    var self = this;
    var ts = this.now();
    var out = Core().diff(this.meta.shadow || {}, data, { ts: ts, ulid: function () { return Core().ulid(self.now()); } });
    var v1ops = out.ops || [];
    if (!v1ops.length) return 0;
    var seq = this.meta.seq;
    var entries = [];
    var docOps = 0;
    v1ops.forEach(function (op) {
      var conv = fromLegacyOp(op, self.clock.tick());
      if (!conv.ok) {
        entries.push({ v1: op, fmt: 'v2', reason: conv.reason });
        return;
      }
      if (!conv.ops.length) return;        //  فقط مانده یا نشانِ محلی عوض شده بود
      if (!conv.native) { entries.push({ v1: op, fmt: 'v2', reason: conv.reason }); return; }
      var spec = specOf(op.table);
      if (spec.policy !== 'master') docOps += conv.ops.length;
      entries.push({ v1: op, fmt: 'v3', ops: conv.ops, doc: spec.policy !== 'master' });
    });
    //  سند + اقلام + حرکت + پرداختِ یک ذخیره: همه یا هیچ روی سرور (SYN-02)
    if (docOps > 1 && docOps <= TX_GROUP_MAX) {
      var tx = 'tx.' + v1ops[0].op_id;
      entries.forEach(function (e) { if (e.fmt === 'v3' && e.doc) e.ops.forEach(function (x) { x.tx = tx; }); });
    }
    var add = entries.map(function (e) {
      seq += 1;
      var item = { key: 'e' + pad(seq, 12), id: e.v1.op_id, fmt: e.fmt, table: e.v1.table, row_id: e.v1.row_id, at: ts };
      if (e.fmt === 'v3') item.ops = e.ops; else { item.op = e.v1; item.why = e.reason || ''; }
      return item;
    });
    await this.persist({ shadow: Core().shadowOf(data), seq: seq }, { addOutbox: add });
    this.outbox = this.outbox.concat(add);
    this.emit();
    return add.length;
  };

  Engine.prototype.queued = function () {
    var n = 0;
    this.outbox.forEach(function (e) { n += e.fmt === 'v3' ? e.ops.length : 1; });
    return n;
  };

  /** دستهٔ بعدی: پیشوندِ هم‌قالبِ صف، در سقفِ بایت و تعداد؛ گروهِ tx شکسته نمی‌شود مگر ناچار */
  Engine.prototype.nextBatch = function () {
    if (!this.outbox.length) return null;
    var fmt = this.outbox[0].fmt;
    var budget = Math.min(MAX_BYTES, Math.max(MIN_BYTES, this.meta.pushBytes || MIN_BYTES));
    var entries = [];
    var ops = [];
    var bytes = 2;
    for (var i = 0; i < this.outbox.length; i++) {
      var e = this.outbox[i];
      if (e.fmt !== fmt) break;
      var add = fmt === 'v3' ? e.ops : [e.op];
      var size = 0;
      add.forEach(function (x) { size += utf8Len(JSON.stringify(x)) + 1; });
      if (entries.length && (bytes + size > budget || ops.length + add.length > MAX_OPS)) {
        //  گروهِ tx ِ نیمه‌کاره را پس بده، مگر کلِ دسته همان یک گروه باشد
        var lastTx = fmt === 'v3' ? e.ops[0].tx : '';
        if (lastTx && lastTx.indexOf('tx.') === 0) {
          var keep = entries.filter(function (x) { return !(x.ops && x.ops[0].tx === lastTx); });
          if (keep.length) {
            entries = keep;
            ops = [];
            entries.forEach(function (x) { ops = ops.concat(x.ops); });
          }
        }
        break;
      }
      entries.push(e);
      ops = ops.concat(add);
      bytes += size;
    }
    return { fmt: fmt, entries: entries, ops: ops, bytes: bytes };
  };

  Engine.prototype.fail = function (status, body, extra) {
    return this.locked(function () { return this.failNow(status, body, extra); });
  };
  Engine.prototype.failNow = async function (status, body, extra) {
    var code = errorCode(body);
    var now = this.now();
    if (status === 426) {
      //  سرور و برنامه هم‌زبان نیستند: هیچ چیز اعمال نشده؛ صف دست نمی‌خورد (SYN-11)
      await this.persist({
        upgrade: { protocol: this.protocol, message: errorMessage(status, body), at: now },
        lastError: UPGRADE_MESSAGE, lastErrorAt: now,
      });
      this.emit();
      return Object.assign({ ok: false, stopped: true, upgrade: true, status: status }, extra || {});
    }
    var attempt = (this.meta.attempt || 0) + 1;
    var patch = { attempt: attempt, lastError: status ? errorMessage(status, body) : String((body && body.message) || 'به سرور نرسیدیم'), lastErrorAt: now };
    if (status === 400 && code === 'batch_too_large') patch.pushBytes = MIN_BYTES;
    if (status === 401 || status === 403) patch.attempt = this.meta.attempt || 0;
    await this.persist(patch);
    this.emit();
    var stop = status === 401 || status === 403 || status === 404;
    return Object.assign({
      ok: false, status: status, code: code, stopped: stop,
      retryInMs: stop ? 0 : Core().backoffMs(attempt - 1, this.random()),
    }, extra || {});
  };

  Engine.prototype.pushOnce = async function () {
    if (this.meta.upgrade) return { ok: false, stopped: true, upgrade: true, sent: 0 };
    var b = this.nextBatch();
    if (!b) return { ok: true, sent: 0 };
    //  `queued` = آنچه پس از همین دسته می‌ماند — نگهبانِ خروجِ سرور (SYN-10) از همین می‌خواند
    var body = { device_id: this.deviceId, protocol: this.protocol, ops: b.ops, queued: Math.max(0, this.queued() - b.ops.length),
      clock_offset_ms: this.meta.offsetMs || 0,
      //  A00: وب ردیفِ خام را نگه می‌دارد؛ سرور تورِ ساخت‌های پیشین را برایش کنار می‌گذارد (newer-fields.js)
      client: 'web' };
    if (this.meta.lastError) body.last_error = this.meta.lastError;
    if (b.fmt === 'v2') { body.format = 'v2'; body.schema_version = LEGACY_SCHEMA; }
    var r;
    try {
      r = await this.call('POST', '/push', body);
    } catch (e) {
      return this.fail(0, { message: (e && e.message) || 'به سرور نرسیدیم' }, { sent: 0 });
    }
    if (r.status !== 200 || !r.body) return this.fail(r.status, r.body, { sent: 0 });
    return this.locked(async function () {
    var results = Array.isArray(r.body.results) ? r.body.results : [];
    var done = [];
    var rejected = [];
    var conflicts = false;
    var at = 0;
    var seq = this.meta.seq;
    var now = this.now();
    b.entries.forEach(function (e) {
      var n = b.fmt === 'v3' ? e.ops.length : 1;
      var mine = results.slice(at, at + n);
      at += n;
      if (mine.length < n || mine.some(function (x) { return !x; })) return;          //  بی پاسخ ⇒ می‌ماند
      if (mine.some(function (x) { return x.status === 'rejected'; })) {
        seq += 1;
        rejected.push(Object.assign(clone(e), {
          key: 'r' + pad(seq, 12), outboxKey: e.key, rejectedAt: now,
          reasons: mine.map(function (x) { return x.reason || x.status; }),
        }));
        return;
      }
      if (mine.every(function (x) { return DONE[x.status]; })) {
        done.push(e.key);
        if (mine.some(function (x) { return x.status === 'conflict' || x.status === 'partial'; })) conflicts = true;
      }
    });
    var removing = done.concat(rejected.map(function (x) { return x.outboxKey; }));
    var grow = Math.min(MAX_BYTES, (this.meta.pushBytes || MIN_BYTES) * 2);
    await this.persist({
      seq: seq, attempt: 0, lastError: '', lastErrorAt: 0, lastOkAt: now, lastPushAt: now, pushBytes: grow,
      conflictsDirty: !!(this.meta.conflictsDirty || conflicts),
    }, { removeOutbox: removing, addRejected: rejected });
    var gone = {};
    removing.forEach(function (k) { gone[k] = true; });
    this.outbox = this.outbox.filter(function (e) { return !gone[e.key]; });
    this.rejectedList = this.rejectedList.concat(rejected);
    this.emit();
    return { ok: true, sent: b.ops.length, done: done.length, rejected: rejected.length, conflicts: conflicts, head: Number(r.body.head || r.body.cursor || 0) };
    });
  };

  /** یک صفحه Pull؛ ژورنال ⇐ دفتر ⇐ سایه + cursor */
  Engine.prototype.pullOnce = async function () {
    if (this.meta.upgrade) return { ok: false, stopped: true, upgrade: true, applied: 0 };
    //  §۹.۵: سرور برگشته و دفتر هنوز از نو ساخته نشده ⇒ Pull نه (`rebuild`)
    if (this.meta.restorePending) return { ok: true, applied: 0, hasMore: false };
    var since = Number(this.meta.cursor || 0);
    var budget = Math.min(MAX_BYTES, Math.max(MIN_BYTES, this.meta.pullBytes || MIN_BYTES));
    var r;
    try {
      r = await this.call('GET', '/pull?device_id=' + encodeURIComponent(this.deviceId) + '&since=' + since + '&max_bytes=' + budget);
    } catch (e) {
      return this.fail(0, { message: (e && e.message) || 'به سرور نرسیدیم' }, { applied: 0 });
    }
    if (r.status !== 200 || !r.body) return this.fail(r.status, r.body, { applied: 0 });
    return this.locked(async function () {
    var cursor = Number(r.body.cursor);
    if (!Number.isFinite(cursor) || cursor < since) cursor = since;
    var legacy = [];
    (r.body.ops || []).forEach(function (item) { var l = effectToLegacy(item); if (l) legacy.push(l); });
    var now = this.now();
    var patch = { cursor: cursor, lastPullAt: now, lastOkAt: now, attempt: 0, lastError: '', lastErrorAt: 0,
      pullBytes: Math.min(MAX_BYTES, budget * 2) };
    /*
     *  §۹.۵: «دورانِ بازیابیِ» سرور (`server/src/lib/sync-epoch.js`) عوض شده و این دستگاه پس از
     *  پایه چیزی دیده ⇒ «در انتظار»؛ این صفحه (از cursor ِ کهنه) نشانده نمی‌شود. وب دفترچهٔ
     *  opهای فرستاده ندارد: opهای گم‌شدهٔ خودش دوباره نمی‌روند، فقط دو بار نشاندن بسته است.
     */
    //  وب دفترچهٔ opهای فرستاده ندارد: «نگه‌دارِ محلی» در `rebuild` ردیف‌های تازهٔ همین دستگاه را نگه می‌دارد
    var ep = r.body.epoch;
    if (ep && typeof ep.id === 'string' && ep.id && Number.isFinite(Number(ep.base)) && Number.isFinite(Number(ep.head))) {
      var known = this.meta.epoch || '';
      var base = Math.min(Number(ep.base), Number(ep.head));
      var restored = (known !== '' && known !== ep.id) || since > Number(ep.head);
      if (restored && since > base) {
        await this.persist({ epoch: ep.id, restorePending: true });
        this.emit();
        return { ok: true, applied: 0, hasMore: false, restore: true };
      }
      if (known !== ep.id) patch.epoch = ep.id;
    }
    if (legacy.length) {
      var data = this.ledger.read();
      //  دفتر هنوز به صفحه بسته نیست: cursor جلو نمی‌رود
      if (!data) return { ok: false, applied: 0, reason: 'no_ledger' };
      var C = Core();
      var before = fingerprint(C.canonical(this.meta.shadow || {}));
      var shadow = C.shadowOf(C.applyRemote(this.meta.shadow || {}, legacy).data);
      var next = null;
      for (var tries = 0; tries < 5 && data && !next; tries++) {
        var planned = ledgerPrint(C.applyRemote(data, legacy).data);
        await this.store.commit({ journal: {
          stage: 'absorb', before: before, after: fingerprint(C.canonical(shadow)),
          ledger: planned, cursor: cursor, ops: legacy,
        } });
        this.failpoint('after-journal');
        //  ذخیرهٔ UI ممکن است وسطِ همین await نشسته باشد: دفتر را دوباره بخوان و op را روی نسخهٔ تازه بنشان
        //  (خواندن تا نوشتن بی await است). اگر دفترِ نشسته با ژورنال نمی‌خواند، ژورنال دوباره نوشته می‌شود
        data = this.ledger.read();
        var fresh = data ? C.applyRemote(data, legacy).data : null;
        if (fresh && ledgerPrint(fresh) === planned) next = fresh;
      }
      if (!next) {
        //  دفتر پیوسته عوض می‌شود یا نیست: چیزی نوشته نمی‌شود، cursor جلو نمی‌رود؛ همان صفحه دوباره می‌آید
        await this.store.commit({ journal: null });
        return { ok: false, applied: 0, reason: data ? 'ledger_busy' : 'no_ledger' };
      }
      this.ledger.write(next);
      this.failpoint('after-ledger');
      patch.shadow = shadow;
      await this.persist(patch, { journal: null });
    } else {
      await this.persist(patch);
    }
    this.emit();
    return { ok: true, applied: legacy.length, ops: (r.body.ops || []).length, cursor: cursor, hasMore: !!r.body.has_more };
    });
  };

  Engine.prototype.fetchConflicts = async function (status) {
    var r;
    try { r = await this.call('GET', '/conflicts?status=' + (status === 'all' ? 'all' : 'open')); } catch (e) { return null; }
    if (r.status !== 200 || !r.body) return null;
    this.conflictList = Array.isArray(r.body.conflicts) ? r.body.conflicts : [];
    var open = this.conflictList.filter(function (c) { return c.status === 'open'; }).length;
    await this.locked(function () { return this.persist({ conflictsOpen: open, conflictsDirty: false }); });
    this.emit();
    return this.conflictList;
  };

  /** keep | revert — فقط مالک یا مدیر (سرور می‌سنجد) */
  Engine.prototype.resolveConflict = async function (id, action) {
    var r = await this.call('POST', '/conflicts/' + encodeURIComponent(id) + '/resolve', { action: action });
    if (r.status !== 200) return { ok: false, status: r.status, code: errorCode(r.body), message: errorMessage(r.status, r.body) };
    await this.fetchConflicts('open');
    return { ok: true, conflict: r.body.conflict, op: r.body.op || null };
  };

  /**
   *  یک دورِ کامل: ثبت ⇐ Push تا خالی شدن ⇐ Pull تا آخر ⇐ تعارض‌ها.
   *  @returns {ok, retryInMs?, upgrade?}
   */
  Engine.prototype.sync = async function () {
    if (this.busy) return { ok: false, busy: true };
    this.busy = true;
    this.emit();
    try {
      if (!this.meta) await this.load();
      if (this.meta.upgrade) return { ok: false, upgrade: true, stopped: true };
      if (!this.meta.initialized) {
        var init = await this.initialize({ keepLocalOnly: !!this.keepLocalOnly });
        if (!init.ok) return init;
      }
      await this.record();
      var step = await this.pushAll();
      if (step) return step;
      //  §۹.۵: سرور برگشته بود و صف خالی شد ⇒ دفتر از نو (نگه‌دارِ محلی)، تنهاها می‌روند، بعد Pull
      if ((step = await this.restoreStep())) return step;
      if ((step = await this.pullAll())) return step;
      if (this.meta.restorePending) {
        if ((step = await this.pushAll())) return step;
        if ((step = await this.restoreStep())) return step;
        if ((step = await this.pullAll())) return step;
      }
      if (this.meta.conflictsDirty || !this.conflictsFetchedAt || this.now() - this.conflictsFetchedAt > 5 * 60 * 1000) {
        this.conflictsFetchedAt = this.now();
        await this.fetchConflicts('open');
      }
      return { ok: true };
    } finally {
      this.busy = false;
      this.emit();
    }
  };

  /** Push تا خالی شدنِ صف؛ شکست ⇒ همان قدم، وگرنه null */
  Engine.prototype.pushAll = async function () {
    for (var i = 0; i < 50 && this.outbox.length; i++) {
      var p = await this.pushOnce();
      if (!p.ok) return p;
      if (!p.done && !p.rejected) break;
    }
    return null;
  };

  /** Pull تا آخر؛ شکست ⇒ همان قدم، وگرنه null */
  Engine.prototype.pullAll = async function () {
    for (var j = 0; j < 50; j++) {
      var q = await this.pullOnce();
      if (!q.ok) return q;
      if (!q.hasMore) break;
    }
    return null;
  };

  /** §۹.۵: «در انتظار» و صفِ خالی ⇒ ساختنِ دوباره و فرستادنِ ردیف‌های تنها؛ شکست ⇒ همان قدم */
  Engine.prototype.restoreStep = async function () {
    if (!this.meta.restorePending || this.outbox.length) return null;
    var step = await this.rebuild();
    if (step) return step;
    return this.pushAll();
  };

  var INCOMPLETE = 'سرور پس از بازیابی هنوز دفتر را آماده نکرده است؛ چند دقیقهٔ دیگر خودش دوباره امتحان می‌کند.';

  /**
   *  §۹.۵ — «نگه‌دارِ محلی» (`core.restoreMerge`، قرینهٔ `V1Restore.kt`): دفتر = Snapshot + ردیف‌هایی
   *  که فقط این‌جا هستند (جز آنچه سرور حذف‌شده می‌داند — `POST /deleted`)؛ سایه = Snapshot ⇒
   *  تنهاها «درج» می‌روند. پیش‌تر دفتر همان Snapshot می‌شد و فروشِ پس از پشتیبانِ همین دستگاه
   *  پاک می‌شد.
   *  ⛔ فقط با صفِ خالی و تفاضلِ هیچ (پس از ثبتِ همین لحظه، زیرِ همان قفل): تغییرِ ثبت‌نشده زیرِ
   *     Snapshot نمی‌رود.
   *  ⛔ اول سایه و cursor، بعد دفتر: نوشتنِ دفتر شکست ⇒ سایه و cursor ِ پیشین برمی‌گردند.
   *  Snapshot ِ ناقص ⇒ دورِ بعد، با خطای دیدنی.
   *  @returns null یا همان قدمِ شکست
   */
  Engine.prototype.rebuild = async function () {
    var r = await this.call('GET', '/snapshot');
    if (r.status !== 200 || !r.body) return this.fail(r.status, r.body);
    if (r.body.complete === false) {
      await this.locked(function () { return this.persist({ lastError: INCOMPLETE, lastErrorAt: this.now() }); });
      this.emit();
      return { ok: false, reason: 'incomplete' };
    }
    var C = Core();
    var legacy = snapshotToLegacy(r.body);
    var ask = C.restoreLocalOnly(legacy, this.ledger.read() || {});
    var gone = [];
    for (var i = 0; i < ask.length; i += 500) {
      var d = await this.call('POST', '/deleted', { rows: ask.slice(i, i + 500) });
      if (d.status === 404) break;
      if (d.status !== 200 || !d.body) return this.fail(d.status, d.body);
      gone = gone.concat(Array.isArray(d.body.deleted) ? d.body.deleted : []);
    }
    await this.locked(async function () {
      if (!this.meta.restorePending || this.outbox.length) return;
      await this.recordNow();
      var data = this.ledger.read();
      if (this.outbox.length || !data || C.diff(this.meta.shadow || {}, data).ops.length) return;
      var merged = C.restoreMerge(legacy, data, gone);
      var before = { shadow: this.meta.shadow, cursor: this.meta.cursor };
      await this.persist({ shadow: C.shadowOf(merged.shadow), cursor: legacy.cursor });
      try {
        this.ledger.write(merged.ledger);
      } catch (e) {
        await this.persist(before);
        throw e;
      }
      await this.persist({ restorePending: false, lastOkAt: this.now() });
      //  ردیف‌های تنهای این دستگاه ⇒ «درج»
      await this.recordNow();
    });
    this.emit();
    return null;
  };

  /** SYN-08 و بخشِ ۲۹.۵ بندِ ۸: «روی دستگاه ثبت شد» جدا از «سرور تأیید کرد» */
  Engine.prototype.status = function () {
    var m = this.meta || emptyMeta();
    var queued = this.queued();
    var state = m.upgrade ? 'upgrade'
      : m.lastError ? 'failed'
        : (m.conflictsOpen || this.rejectedList.length) ? 'conflict'
          : queued ? 'pending' : 'synced';
    return {
      engine: 'v3', protocol: this.protocol, state: state, busy: this.busy,
      queued: queued, entries: this.outbox.length, rejected: this.rejectedList.length,
      conflictsOpen: Number(m.conflictsOpen || 0), cursor: Number(m.cursor || 0),
      lastOkAt: m.lastOkAt || 0, lastPushAt: m.lastPushAt || 0, lastPullAt: m.lastPullAt || 0,
      lastError: m.lastError || '', lastErrorAt: m.lastErrorAt || 0,
      upgradeRequired: !!m.upgrade, upgradeMessage: m.upgrade ? UPGRADE_MESSAGE : '',
      clockOffsetMs: m.offsetMs || 0, initialized: !!m.initialized,
      //  SYN-10: تا صف خالی نشده، خروج و حذفِ دستگاه فقط با خروجیِ اجباری
      unsynced: queued > 0,
    };
  };
  Engine.prototype.rejected = function () { return clone(this.rejectedList); };
  Engine.prototype.conflicts = function () { return clone(this.conflictList); };
  Engine.prototype.onChange = function (fn) {
    var l = this.listeners;
    l.push(fn);
    return function () { var i = l.indexOf(fn); if (i >= 0) l.splice(i, 1); };
  };
  Engine.prototype.emit = function () {
    var s = this.status();
    this.listeners.forEach(function (fn) { try { fn(s); } catch (e) { /* شنوندهٔ شکسته بقیه را نمی‌برد */ } });
  };

  /* ==========================================================
     ۷) تنظیمِ هر دستگاه — پیش‌فرض v1
     ========================================================== */
  var CHOICE_KEY = 'tohid-sync-engine';
  function choice(storage) {
    var s = storage || (typeof localStorage !== 'undefined' ? localStorage : null);
    var v = '';
    try { v = s ? String(s.getItem(CHOICE_KEY) || '') : ''; } catch (e) { v = ''; }
    return v === 'v3' ? 'v3' : 'v1';
  }
  /**
   *  گذر میانِ موتورها — فقط وقتی هیچ چیزی در صفِ موتورِ فعلی نمانده است؛
   *  وگرنه همان تغییرها از دو راه با دو شناسه می‌رفتند (دلتای دوباره).
   *  @param pending تعدادِ منتظرِ موتورِ فعلی
   */
  function setChoice(next, pending, storage) {
    var s = storage || (typeof localStorage !== 'undefined' ? localStorage : null);
    if (next !== 'v1' && next !== 'v3') return { ok: false, reason: 'bad_choice' };
    if (Number(pending) > 0) return { ok: false, reason: 'pending', message: 'اول بگذارید تغییرهای منتظر به سرور برسند.' };
    try { s.setItem(CHOICE_KEY, next); } catch (e) { return { ok: false, reason: 'storage' }; }
    return { ok: true, choice: next };
  }

  /**
   *  رابطِ `window.TohidSync` برای این دستگاه وقتی v3 برگزیده شده — همان نام‌هایی
   *  که `index.html`، `account-ui.js` و `shop-sync.js` صدا می‌زنند. سوکتِ زندهٔ v1
   *  این‌جا نیست؛ هر ۳۰ ثانیه و با «آنلاین شد» Pull می‌شود.
   *  @returns آیا نصب شد
   */
  function install(acc, opts) {
    var o = opts || {};
    if (!acc || choice(o.storage) !== 'v3' || typeof root.indexedDB === 'undefined') return false;
    var C = Core();
    var ledger = { read: function () { return null; }, write: function () { } };
    var engine = null;
    var timer = null;
    var retry = null;
    function account() { return String((acc.account() || {}).userId || 'anon').replace(/[^A-Za-z0-9_-]/g, '_'); }
    /** حالِ موتورِ v1ِ همین حساب (همان کلیدِ `sync-engine.js`) — فقط خواندن */
    function v1State() {
      try { return JSON.parse(localStorage.getItem('tohid-sync-v1-' + account() + '-state') || 'null') || {}; } catch (e) { return {}; }
    }
    function make() {
      engine = new Engine({
        transport: AccountTransport(acc),
        store: new IdbStore('tohid-sync-v3-' + account()),
        deviceId: acc.deviceId(),
        ledger: { read: function () { return ledger.read(); }, write: function (d) { ledger.write(d); } },
      });
      //  دستگاهی که با v1 همگام بوده: ردیفِ نبود در سرور یعنی جای دیگری پاک شده (برنمی‌گردد)؛
      //  دستگاهی که هرگز همگام نشده: همهٔ دفترش یک بار «درج» می‌رود (نخستین بارگذاری)
      engine.keepLocalOnly = !(v1State().lastOkAt > 0);
      engine.onChange(function (s) {
        try { root.dispatchEvent(new CustomEvent('tohid-sync', { detail: facadeStatus(s) })); } catch (e) { /* قدیمی */ }
      });
      return engine.load();
    }
    var ready = null;
    function ensure() { if (!ready) ready = make(); return ready; }
    function facadeStatus(s) {
      var st = s || (engine ? engine.status() : {});
      return Object.assign({}, st, {
        configured: acc.configured(), signedIn: acc.isSignedIn(), online: root.navigator ? root.navigator.onLine !== false : true,
        holding: !!st.upgradeRequired, live: false, dropped: st.rejected || 0, schemaVersion: C.SCHEMA_VERSION,
        dot: C.dotOf({
          error: !!st.lastError, online: root.navigator ? root.navigator.onLine !== false : true,
          signedIn: acc.isSignedIn(), configured: acc.configured(), queued: st.queued || 0, busy: !!st.busy,
        }),
      });
    }
    async function pump() {
      if (!acc.isSignedIn()) return { ok: false };
      await ensure();
      var r = await engine.sync();
      if (retry) { clearTimeout(retry); retry = null; }
      if (r && r.retryInMs) retry = setTimeout(function () { retry = null; pump(); }, r.retryInMs);
      return r;
    }
    root.TohidSync = {
      engine: 'v3',
      bind: function (hooks) {
        if (hooks && typeof hooks.read === 'function') ledger.read = hooks.read;
        if (hooks && typeof hooks.write === 'function') ledger.write = hooks.write;
      },
      start: function () {
        if (timer) return;
        timer = setInterval(function () { pump().catch(function () { }); }, 30000);
        root.addEventListener('online', function () { pump().catch(function () { }); });
        if (acc.isSignedIn()) pump().catch(function () { });
      },
      onSignedIn: function () { ready = null; pump().catch(function () { }); },
      recordChange: function () {
        ensure().then(function () { return engine.record(); }).then(function (n) {
          if (n) setTimeout(function () { pump().catch(function () { }); }, 500);
        }).catch(function () { });
        return 0;
      },
      sync: pump,
      pullOnce: function () { return ensure().then(function () { return engine.pullOnce(); }); },
      restoreFromServer: function () { return ensure().then(function () { return engine.initialize({ keepLocalOnly: false }); }); },
      firstSync: function () { return pump(); },
      pushBaseline: function () { return pump(); },
      status: function () { return facadeStatus(); },
      onChange: function (fn) {
        var off = null;
        ensure().then(function () { off = engine.onChange(function (s) { fn(facadeStatus(s)); }); });
        return function () { if (off) off(); };
      },
      queueSize: function () { return engine ? engine.queued() : 0; },
      dropped: function () { return engine ? engine.rejected() : []; },
      conflicts: function () { return engine ? engine.conflicts() : []; },
      clearError: function () {
        if (engine) engine.locked(function () { return this.persist({ lastError: '', lastErrorAt: 0 }); }).then(function () { engine.emit(); });
      },
      connectLive: function () { },
      disconnectLive: function () { },
      relay: function () { return false; },
      get liveOpen() { return false; },
      v3: function () { return engine; },
    };
    return true;
  }

  return {
    PROTOCOL: PROTOCOL,
    hlc: { SKEW_MS: SKEW_MS, parse: hlcParse, format: hlcFormat, canonical: hlcCanonical, compare: hlcCompare, fromTs: hlcFromTs, createClock: createClock },
    TABLES: TABLES,
    KINDS: KINDS,
    specOf: specOf,
    fromLegacyOp: fromLegacyOp,
    nativeReason: nativeReason,
    effectToLegacy: effectToLegacy,
    dataToLegacy: dataToLegacy,
    snapshotToLegacy: snapshotToLegacy,
    Engine: Engine,
    MemoryStore: MemoryStore,
    IdbStore: IdbStore,
    FetchTransport: FetchTransport,
    AccountTransport: AccountTransport,
    CHOICE_KEY: CHOICE_KEY,
    choice: choice,
    setChoice: setChoice,
    install: install,
    UPGRADE_MESSAGE: UPGRADE_MESSAGE,
  };
});
