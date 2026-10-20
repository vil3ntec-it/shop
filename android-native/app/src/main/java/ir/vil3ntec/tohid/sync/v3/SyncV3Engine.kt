package ir.vil3ntec.tohid.sync.v3

import ir.vil3ntec.tohid.core.sync.v3.HlcClock
import ir.vil3ntec.tohid.data.ShopData
import ir.vil3ntec.tohid.sync.v1.LedgerDiff
import ir.vil3ntec.tohid.sync.v1.SyncCommit
import ir.vil3ntec.tohid.sync.v1.SyncOp
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlin.coroutines.cancellation.CancellationException
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToLong

/**
 *  موتورِ Sync v3 سمتِ برنامه (اندروید و کامپیوتر) — بخشِ ۳.۸؛ SYN-01..08، 10، 11.
 *  قرینهٔ `license/sync-v3-engine.js`؛ قرارداد همان `server/src/routes/sync-v3.js`.
 *
 *  ⛔ **پیش‌فرض خاموش** (`SyncChoice`، پیش‌فرض v1). وقتی خاموش است هیچ‌جای v1 به
 *     این فایل نمی‌رسد.
 *
 *  ── چرخه ──────────────────────────────────────────────────────────
 *   record  تفاضلِ دفتر با سایه (همان `LedgerDiff`ِ v1) ⇒ opِ v3 با HLC. صف و سایه
 *           با **همان ژورنالِ `SyncCommit`** (F0-05) جلو می‌روند: یا هر دو یا هیچ،
 *           و opِ بازیابی‌شده همان `op_id` را دارد ⇒ سرور «تکراری» می‌شمارد.
 *   push    دسته‌های کوچک (۶۴ تا ۲۵۶KB، وفقی)؛ عقب‌نشینیِ نمایی با پراکندگی؛
 *           ۴۲۶ ⇒ صف می‌ماند و موتور می‌ایستد («برنامه را به‌روز کنید»).
 *   pull    ادامه از cursor؛ دفتر + سایه + cursor با `SyncCommit.absorb`.
 *   conflicts فهرستِ صندوقِ تعارض برای وضعیت (SYN-07، SYN-08).
 *
 *  ⛔ opی که سرور رد کرد از صف بیرون می‌رود ولی **گم نمی‌شود** — با دلیل در
 *     «ردشده‌ها» می‌ماند و سندِ محلی دست نمی‌خورد (بخش‌های ۲۸.۴ و ۲۹.۵).
 */

/** درخواستِ HTTP؛ فقط قطعِ شبکه پرتاب می‌کند، پاسخِ خطا `status` دارد. */
fun interface V3Transport {
  suspend fun request(method: String, path: String, body: JsonObject?): V3Response
}

data class V3Response(val status: Int, val body: JsonObject?)

/** دفترِ دکان — همان «تنها درِ نوشتن» (`ShopStore`). */
interface V3Ledger {
  fun read(): ShopData
  /** باید به دیسک برسد یا پرتاب کند */
  fun write(next: ShopData)
}

@Serializable
data class V3Entry(
  val key: String,
  val id: String,
  val fmt: String,
  val table: String,
  val rowId: String,
  val at: Long,
  val ops: List<JsonObject> = emptyList(),
  val op: JsonObject? = null,
  val why: String = "",
  val reasons: List<String> = emptyList(),
  val rejectedAt: Long = 0,
) {
  val size: Int get() = if (fmt == V3) ops.size else 1

  companion object {
    const val V3 = "v3"
    const val V2 = "v2"
  }
}

@Serializable
data class V3Queue(
  val outbox: List<V3Entry> = emptyList(),
  val rejected: List<V3Entry> = emptyList(),
  val seq: Long = 0,
)

@Serializable
data class V3Meta(
  val initialized: Boolean = false,
  val shadow: String = "",
  val cursor: Long = 0,
  val clock: String = "",
  val offsetMs: Long = 0,
  val lastOkAt: Long = 0,
  val lastPushAt: Long = 0,
  val lastPullAt: Long = 0,
  val lastError: String = "",
  val lastErrorAt: Long = 0,
  val attempt: Int = 0,
  val upgradeProtocol: Int = 0,
  val upgradeMessage: String = "",
  val conflictsOpen: Int = 0,
  val conflictsDirty: Boolean = false,
  val pushBytes: Int = SyncV3Engine.MIN_BYTES,
  val pullBytes: Int = SyncV3Engine.MIN_BYTES,
)

/** انبارِ محلی؛ هر `save` یک نوشتنِ اتمی (فایلِ کناری + fsync + جابه‌جایی). */
internal interface V3Store {
  fun loadMeta(): V3Meta?
  fun saveMeta(meta: V3Meta)
  fun loadQueue(): V3Queue?
  fun saveQueue(queue: V3Queue)
  val journal: SyncCommit.Journal
}

/** SYN-08 و بخشِ ۲۹.۵ بندِ ۸: «روی دستگاه ثبت شد» جدا از «سرور تأیید کرد». */
data class V3Status(
  val state: String,
  val queued: Int,
  val rejected: Int,
  val conflictsOpen: Int,
  val lastOkAt: Long,
  val lastError: String,
  val lastErrorAt: Long,
  val upgradeRequired: Boolean,
  val clockOffsetMs: Long,
  val initialized: Boolean,
  val busy: Boolean,
) {
  /** SYN-10: تا صفر نشده، خروج و حذفِ دستگاه فقط با خروجیِ اجباری */
  val unsynced: Boolean get() = queued > 0
}

/** نتیجهٔ یک قدم */
data class V3Step(
  val ok: Boolean,
  val sent: Int = 0,
  val done: Int = 0,
  val rejected: Int = 0,
  val applied: Int = 0,
  val hasMore: Boolean = false,
  val status: Int = 0,
  val upgrade: Boolean = false,
  val stopped: Boolean = false,
  val retryInMs: Long = 0,
)

internal class SyncV3Engine(
  private val transport: V3Transport,
  private val store: V3Store,
  deviceId: String,
  private val ledger: V3Ledger,
  private val rawNow: () -> Long = { System.currentTimeMillis() },
  private val random: () -> Double = { Math.random() },
  val protocol: Int = PROTOCOL,
  /** همان قفلی که `ShopStore` هنگامِ نوشتن می‌گیرد — نوشتن در دفتر و سایه زیرِ آن */
  private val withLock: (() -> Unit) -> Unit = { it() },
) {
  val deviceId: String = deviceId.replace(Regex("[^A-Za-z0-9_.-]"), "-").take(64).ifEmpty { "device" }

  var meta: V3Meta = V3Meta()
    private set
  var queue: V3Queue = V3Queue()
    private set
  var conflicts: List<JsonObject> = emptyList()
    private set
  @Volatile var busy: Boolean = false
    private set

  /** زمانِ تصحیح‌شده: ساعتِ دستگاه + اختلافی که از `server_time` نگه داشته شده */
  fun now(): Long = rawNow() + meta.offsetMs

  val clock = HlcClock(this.deviceId, { now() })

  /** همان سه تکهٔ `SyncCommit`ِ v1، این بار برای صف و سایهٔ v3 */
  private val parts = object : SyncCommit.Parts {
    override fun shadow(): String = meta.shadow

    override fun commit(shadow: String, cursor: Long?) {
      val m = meta.copy(shadow = shadow, cursor = cursor ?: meta.cursor, clock = clock.peek())
      store.saveMeta(m)
      meta = m
    }

    override fun queued(): Set<String> = queue.outbox.mapTo(HashSet()) { it.id }

    override fun push(ops: List<SyncOp>) {
      val (entries, seq) = toEntries(ops, queue.seq)
      if (entries.isEmpty()) return
      val q = queue.copy(outbox = queue.outbox + entries, seq = seq)
      store.saveQueue(q)
      queue = q
    }
  }
  private val commit = SyncCommit(store.journal, parts)

  /** opهای یک ثبت ⇒ ورودی‌های صف؛ سند + اقلام + حرکت + پرداخت با یک tx (SYN-02) */
  private fun toEntries(ops: List<SyncOp>, startSeq: Long): Pair<List<V3Entry>, Long> {
    val at = now()
    data class Draft(val op: SyncOp, val fmt: String, val ops: List<JsonObject>, val why: String, val doc: Boolean)
    val drafts = ArrayList<Draft>()
    var docOps = 0
    for (op in ops) {
      val conv = V3Convert.fromLegacyOp(op, clock.tick())
      if (!conv.ok) { drafts += Draft(op, V3Entry.V2, emptyList(), conv.reason, false); continue }
      if (conv.ops.isEmpty()) continue                 //  فقط مانده یا نشانِ محلی
      if (!conv.native) { drafts += Draft(op, V3Entry.V2, emptyList(), conv.reason, false); continue }
      val doc = V3Schema.specOf(op.table)?.policy != "master"
      if (doc) docOps += conv.ops.size
      drafts += Draft(op, V3Entry.V3, conv.ops, "", doc)
    }
    //  یک ثبت = یک سند (فاکتور با قلم‌ها، لغو، برگشتِ یک فاکتور، یک خرید) ⇒ همیشه یک tx، هر اندازه:
    //  ردِ سند (سقف/تأیید) همه را با هم برمی‌گرداند، وگرنه فاکتورِ ۶۰قلمیِ ردشده قلم و بردش را جا می‌گذاشت.
    //  بقیه (دفترِ چندفاکتوری: نخستین بارگذاری، بازگردانیِ پشتیبان؛ یا ثبتِ بی سند) ⇒ همان سقفِ [TX_GROUP_MAX]
    val single = headsOf(drafts.filter { it.doc }.map { it.op }).size == 1
    val tx = if (docOps >= 2 && (single || docOps <= TX_GROUP_MAX)) "tx.${ops.first().opId}" else null
    //  درونِ tx سرِ برگشت (ردیف‌های returns) پیش از شمارنده‌هایش (debtSettled ِ فاکتور، returnedQty ِ قلم):
    //  اگر tx ِ بزرگ ناچار در دو دسته برود، سر در دستهٔ اول داوری می‌شود و دنباله با آن برمی‌گردد
    val ordered = if (tx == null) drafts else drafts.filter { it.op.table == "returns" } + drafts.filter { it.op.table != "returns" }
    var seq = startSeq
    val entries = ordered.map { d ->
      seq += 1
      val v3ops = if (tx != null && d.doc) d.ops.map { JsonObject(it + ("tx" to JsonPrimitive(tx))) } else d.ops
      V3Entry(
        key = "e" + seq.toString().padStart(12, '0'), id = d.op.opId, fmt = d.fmt, table = d.op.table,
        rowId = d.op.rowId, at = at, ops = v3ops, op = if (d.fmt == V3Entry.V2) d.op.toJson() else null, why = d.why,
      )
    }
    return entries to seq
  }

  /**
   *  «سر»های یک ثبت: هر فاکتور/خریدی که عوض شد، و هر فاکتوری که برگشتی رویش آمد (برگشتِ بی رسید:
   *  سندِ خودش). یکی ⇒ یک سند.
   */
  private fun headsOf(ops: List<SyncOp>): Set<String> = ops.mapNotNullTo(HashSet()) { op ->
    when (op.table) {
      "sales", "purchases" -> op.table + "/" + op.rowId
      "returns" -> if (op.type != SyncOp.INSERT) null else {
        val f = op.fields
        val sale = (f?.get("saleId") as? JsonPrimitive)?.content.orEmpty()
        val doc = (f?.get("docId") as? JsonPrimitive)?.content.orEmpty()
        if (sale.isNotEmpty()) "sales/$sale" else "returns/" + doc.ifEmpty { op.rowId }
      }
      else -> null
    }
  }

  fun load(): SyncV3Engine {
    meta = store.loadMeta() ?: V3Meta()
    queue = store.loadQueue() ?: V3Queue()
    clock.observe(meta.clock)
    return this
  }

  /** commitی که برنامه وسطش بسته شد — هنگامِ شروع، با دفترِ روی دیسک */
  fun recover(onDisk: ShopData): SyncCommit.Outcome =
    if (commit.pending()) commit.recover(onDisk) else SyncCommit.Outcome.NONE

  fun queued(): Int = queue.outbox.sumOf { it.size }
  fun rejected(): List<V3Entry> = queue.rejected

  fun status(): V3Status {
    val m = meta
    val q = queued()
    val state = when {
      m.upgradeProtocol > 0 -> "upgrade"
      m.lastError.isNotEmpty() -> "failed"
      m.conflictsOpen > 0 || queue.rejected.isNotEmpty() -> "conflict"
      q > 0 -> "pending"
      else -> "synced"
    }
    return V3Status(
      state, q, queue.rejected.size, m.conflictsOpen, m.lastOkAt, m.lastError, m.lastErrorAt,
      m.upgradeProtocol > 0, m.offsetMs, m.initialized, busy,
    )
  }

  private fun save(m: V3Meta) {
    val next = m.copy(clock = clock.peek())
    store.saveMeta(next)
    meta = next
  }

  private suspend fun call(method: String, path: String, body: JsonObject? = null): V3Response {
    val r = transport.request(method, path, body)
    val b = r.body
    withLock {
      (b?.get("server_time") as? JsonPrimitive)?.content?.toDoubleOrNull()?.takeIf { it > 0 }?.let {
        meta = meta.copy(offsetMs = (it - rawNow()).roundToLong())
      }
      (b?.get("hlc") as? JsonPrimitive)?.takeIf { it.isString }?.let { clock.recv(it.content) }
    }
    return r
  }

  /** null یعنی به سرور نرسید (قطعِ شبکه)؛ لغوِ کوروتین بالا می‌رود */
  private suspend fun tryCall(method: String, path: String, body: JsonObject? = null): V3Response? = try {
    call(method, path, body)
  } catch (e: CancellationException) {
    throw e
  } catch (e: Exception) {
    null
  }

  private fun errorCode(body: JsonObject?): String {
    val e = body?.get("error") ?: return ""
    if (e is JsonPrimitive) return e.content
    return ((e as? JsonObject)?.get("code") as? JsonPrimitive)?.content.orEmpty()
  }

  private fun errorMessage(status: Int, body: JsonObject?): String {
    (body?.get("message") as? JsonPrimitive)?.content?.takeIf { it.isNotEmpty() }?.let { return it }
    (((body?.get("error") as? JsonObject)?.get("message")) as? JsonPrimitive)?.content?.let { return it }
    return if (status == 0) "به سرور نرسیدیم" else "خطای سرور ($status)"
  }

  /** همان فرمولِ v1 (`SyncV1Engine.backoffMs`، `sync-core.js → backoffMs`): ۲ث تا ۵دق، ±۲۵٪ */
  fun backoffMs(attempt: Int, jitter: Double): Long {
    val ms = min(MAX_BACKOFF_MS.toDouble(), MIN_BACKOFF_MS * 2.0.pow(max(0, attempt)))
    return (ms * (0.75 + jitter * 0.5)).roundToLong()
  }

  private fun fail(status: Int, body: JsonObject?): V3Step {
    var out = V3Step(ok = false)
    withLock { out = failNow(status, body) }
    return out
  }

  private fun failNow(status: Int, body: JsonObject?): V3Step {
    val t = now()
    if (status == 426) {
      //  سرور و برنامه هم‌زبان نیستند: هیچ چیز اعمال نشده و صف دست نمی‌خورد (SYN-11)
      save(meta.copy(upgradeProtocol = protocol, upgradeMessage = errorMessage(status, body), lastError = UPGRADE_MESSAGE, lastErrorAt = t))
      return V3Step(ok = false, status = 426, upgrade = true, stopped = true)
    }
    val auth = status == 401 || status == 403
    val attempt = if (auth) meta.attempt else meta.attempt + 1
    var m = meta.copy(attempt = attempt, lastError = errorMessage(status, body), lastErrorAt = t)
    if (status == 400 && errorCode(body) == "batch_too_large") m = m.copy(pushBytes = MIN_BYTES)
    save(m)
    val stop = auth || status == 404
    return V3Step(ok = false, status = status, stopped = stop, retryInMs = if (stop) 0 else backoffMs(attempt - 1, random()))
  }

  /**
   *  راه‌اندازی روی این دستگاه: Snapshot ⇒ دفتر، سایه و cursor.
   *  @param keepLocalOnly ردیف‌های تنهای این دستگاه می‌مانند و با نخستین ثبت «درج»
   *         می‌روند (نخستین بارگذاری). دستگاهی که با v1 همگام بوده `false` می‌دهد.
   */
  suspend fun initialize(keepLocalOnly: Boolean): V3Step {
    val r = tryCall("GET", "/snapshot") ?: return fail(0, null)
    val body = r.body
    if (r.status != 200 || body == null) return fail(r.status, body)
    if ((body["complete"] as? JsonPrimitive)?.content == "false") return V3Step(ok = false)
    val legacy = V3Convert.snapshotToLegacy(body)
    val cursor = (body["cursor"] as? JsonPrimitive)?.content?.toLongOrNull() ?: 0L
    withLock {
      val local = LedgerDiff.toJson(ledger.read())
      val base = LedgerDiff.fromSnapshot(legacy, local)
      val next = if (keepLocalOnly) LedgerDiff.unionRows(base, local) else base
      val written = LedgerDiff.fromJson(next)
      ledger.write(written)
      //  سایه از همان شکلی که مدل می‌نویسد، تا پیش‌فرض‌های مدل «تغییر» دیده نشوند
      val shadowTree = if (keepLocalOnly) shape(LedgerDiff.fromSnapshot(legacy, JsonObject(emptyMap()))) else LedgerDiff.toJson(written)
      save(meta.copy(initialized = true, shadow = LedgerDiff.shadowOf(shadowTree).toString(), cursor = cursor, lastOkAt = now()))
    }
    return V3Step(ok = true)
  }

  /**
   *  ردیف‌ها از همان درِ مدل (پیش‌فرض‌ها پر می‌شوند)، ولی فهرستی که در درخت نبود
   *  نبود می‌ماند — همان قاعدهٔ `LedgerDiff.applyRemote` (پیش‌فرضِ مدل «نامِ سرور» نیست).
   */
  private fun shape(tree: JsonObject): JsonObject {
    val shaped = try { LedgerDiff.toJson(LedgerDiff.fromJson(tree)) } catch (_: Exception) { return tree }
    return JsonObject(shaped.filterKeys { it !in LedgerDiff.LIST_COLLECTIONS || it in tree })
  }

  /** ثبتِ محلی (زیرِ همان قفلِ `ShopStore`). @return چند opِ v1 دیده شد */
  fun record(data: ShopData): Int = if (!meta.initialized) 0 else commit.record(data)

  /** بایگانیِ دفتر: ردیف‌ها از سایه هم بیرون می‌روند، بی هیچ op (همان `SyncCommit.forget`) */
  fun forget(gone: Map<String, Collection<String>>): Int = if (!meta.initialized) 0 else commit.forget(gone)

  private class Batch(val fmt: String, val entries: List<V3Entry>, val ops: List<JsonObject>)

  private fun nextBatch(): Batch? {
    val out = queue.outbox
    if (out.isEmpty()) return null
    val fmt = out.first().fmt
    val budget = meta.pushBytes.coerceIn(MIN_BYTES, MAX_BYTES)
    var entries = ArrayList<V3Entry>()
    var bytes = 2
    var count = 0
    for (e in out) {
      if (e.fmt != fmt) break
      val add = if (fmt == V3Entry.V3) e.ops else listOfNotNull(e.op)
      val size = add.sumOf { it.toString().encodeToByteArray().size + 1 }
      if (entries.isNotEmpty() && (bytes + size > budget || count + add.size > MAX_OPS)) {
        //  گروهِ tx ِ نیمه‌کاره پس داده می‌شود، مگر کلِ دسته همان یک گروه باشد
        val lastTx = if (fmt == V3Entry.V3) (e.ops.firstOrNull()?.get("tx") as? JsonPrimitive)?.content.orEmpty() else ""
        if (lastTx.startsWith("tx.")) {
          val keep = entries.filter { x -> (x.ops.firstOrNull()?.get("tx") as? JsonPrimitive)?.content != lastTx }
          if (keep.isNotEmpty()) entries = ArrayList(keep)
        }
        break
      }
      entries += e
      bytes += size
      count += add.size
    }
    val ops = entries.flatMap { if (fmt == V3Entry.V3) it.ops else listOfNotNull(it.op) }
    return Batch(fmt, entries, ops)
  }

  suspend fun pushOnce(): V3Step {
    if (meta.upgradeProtocol > 0) return V3Step(ok = false, upgrade = true, stopped = true)
    val b = nextBatch() ?: return V3Step(ok = true)
    val body = buildJsonObject {
      put("device_id", JsonPrimitive(deviceId))
      put("protocol", JsonPrimitive(protocol))
      put("ops", JsonArray(b.ops))
      //  آنچه پس از همین دسته می‌ماند — نگهبانِ خروجِ سرور (SYN-10) از همین می‌خواند
      put("queued", JsonPrimitive(max(0, queued() - b.ops.size)))
      put("clock_offset_ms", JsonPrimitive(meta.offsetMs))
      if (meta.lastError.isNotEmpty()) put("last_error", JsonPrimitive(meta.lastError))
      if (b.fmt == V3Entry.V2) {
        put("format", JsonPrimitive("v2"))
        put("schema_version", JsonPrimitive(LedgerDiff.SCHEMA_VERSION))
      }
    }
    val r = tryCall("POST", "/push", body) ?: return fail(0, null)
    val rb = r.body
    if (r.status != 200 || rb == null) return fail(r.status, rb)

    val results = (rb["results"] as? JsonArray).orEmpty().map { it as? JsonObject }
    var step = V3Step(ok = true)
    //  صف و حال زیرِ همان قفلِ ثبت: ثبتی که هم‌زمان رسید گم نمی‌شود و سایه عقب نمی‌رود
    withLock { step = settle(b, results) }
    return step
  }

  private fun settle(b: Batch, results: List<JsonObject?>): V3Step {
    val done = HashSet<String>()
    val rejected = ArrayList<V3Entry>()
    var conflict = false
    var at = 0
    val t = now()
    var seq = queue.seq
    for (e in b.entries) {
      val n = if (b.fmt == V3Entry.V3) e.ops.size else 1
      val mine = results.subList(min(at, results.size), min(at + n, results.size))
      at += n
      if (mine.size < n || mine.any { it == null }) continue          //  بی پاسخ ⇒ می‌ماند
      val statuses = mine.map { (it!!["status"] as? JsonPrimitive)?.content.orEmpty() }
      if ("rejected" in statuses) {
        seq += 1
        rejected += e.copy(
          key = "r" + seq.toString().padStart(12, '0'), rejectedAt = t,
          reasons = mine.map { (it!!["reason"] as? JsonPrimitive)?.content ?: (it["status"] as? JsonPrimitive)?.content.orEmpty() },
        )
        done += e.key
        continue
      }
      if (statuses.all { it in DONE }) {
        done += e.key
        if (statuses.any { it == "conflict" || it == "partial" }) conflict = true
      }
    }
    val q = queue.copy(outbox = queue.outbox.filter { it.key !in done }, rejected = queue.rejected + rejected, seq = seq)
    store.saveQueue(q)
    queue = q
    save(meta.copy(
      attempt = 0, lastError = "", lastErrorAt = 0, lastOkAt = t, lastPushAt = t,
      pushBytes = min(MAX_BYTES, meta.pushBytes * 2), conflictsDirty = meta.conflictsDirty || conflict,
    ))
    return V3Step(ok = true, sent = b.ops.size, done = done.size - rejected.size, rejected = rejected.size)
  }

  suspend fun pullOnce(): V3Step {
    if (meta.upgradeProtocol > 0) return V3Step(ok = false, upgrade = true, stopped = true)
    val since = meta.cursor
    val budget = meta.pullBytes.coerceIn(MIN_BYTES, MAX_BYTES)
    val r = tryCall("GET", "/pull?device_id=$deviceId&since=$since&max_bytes=$budget") ?: return fail(0, null)
    val body = r.body
    if (r.status != 200 || body == null) return fail(r.status, body)
    val cursor = ((body["cursor"] as? JsonPrimitive)?.content?.toLongOrNull() ?: since).coerceAtLeast(since)
    val items = (body["ops"] as? JsonArray).orEmpty()
    val legacy = items.mapNotNull { (it as? JsonObject)?.let(V3Convert::effectToLegacy) }
    withLock {
      if (legacy.isNotEmpty()) {
        //  ژورنال ⇐ دفتر ⇐ سایه + cursor ⇐ پاک کردنِ ژورنال (شرح سرِ `SyncCommit.absorb`)
        commit.absorb(legacy, ledger.read(), cursor) { next -> ledger.write(next) }
      }
      val t = now()
      save(meta.copy(
        cursor = cursor, lastPullAt = t, lastOkAt = t, attempt = 0, lastError = "", lastErrorAt = 0,
        pullBytes = min(MAX_BYTES, budget * 2),
      ))
    }
    val more = (body["has_more"] as? JsonPrimitive)?.content == "true"
    return V3Step(ok = true, applied = legacy.size, hasMore = more)
  }

  suspend fun fetchConflicts(all: Boolean = false): List<JsonObject>? {
    val r = tryCall("GET", "/conflicts?status=" + if (all) "all" else "open") ?: return null
    if (r.status != 200) return null
    conflicts = (r.body?.get("conflicts") as? JsonArray).orEmpty().mapNotNull { it as? JsonObject }
    val open = conflicts.count { (it["status"] as? JsonPrimitive)?.content == "open" }
    withLock { save(meta.copy(conflictsOpen = open, conflictsDirty = false)) }
    return conflicts
  }

  /** keep | revert — مالک یا مدیر (سرور می‌سنجد) */
  suspend fun resolveConflict(id: String, action: String): V3Response {
    val r = call("POST", "/conflicts/$id/resolve", buildJsonObject { put("action", JsonPrimitive(action)) })
    if (r.status == 200) fetchConflicts()
    return r
  }

  private var conflictsAt = 0L

  /** یک دورِ کامل: راه‌اندازی ⇐ ثبت ⇐ Push تا خالی ⇐ Pull تا آخر ⇐ تعارض‌ها */
  suspend fun sync(keepLocalOnly: Boolean, recordNow: () -> Unit = { withLock { record(ledger.read()) } }): V3Step {
    if (busy) return V3Step(ok = false)
    busy = true
    try {
      if (meta.upgradeProtocol > 0) return V3Step(ok = false, upgrade = true, stopped = true)
      if (!meta.initialized) {
        val init = initialize(keepLocalOnly)
        if (!init.ok) return init
      }
      recordNow()
      for (i in 0 until ROUNDS) {
        if (queue.outbox.isEmpty()) break
        val p = pushOnce()
        if (!p.ok) return p
        if (p.done == 0 && p.rejected == 0) break
      }
      for (i in 0 until ROUNDS) {
        val q = pullOnce()
        if (!q.ok) return q
        if (!q.hasMore) break
      }
      if (meta.conflictsDirty || now() - conflictsAt > 5 * 60 * 1000L) {
        conflictsAt = now()
        fetchConflicts()
      }
      return V3Step(ok = true)
    } finally {
      busy = false
    }
  }

  private fun JsonArray?.orEmpty(): List<kotlinx.serialization.json.JsonElement> = this ?: emptyList()

  companion object {
    const val PROTOCOL = 3
    const val MIN_BYTES = 64 * 1024
    const val MAX_BYTES = 256 * 1024
    const val MAX_OPS = 200
    /** سقفِ tx ِ مشترک فقط برای دفترِ چندفاکتوری؛ یک سند همیشه یک tx است (`toEntries`) */
    const val TX_GROUP_MAX = 50
    const val ROUNDS = 50
    const val MIN_BACKOFF_MS = 2000L
    const val MAX_BACKOFF_MS = 5 * 60 * 1000L
    val DONE = setOf("applied", "partial", "conflict", "duplicate")
    const val UPGRADE_MESSAGE = "همگام‌سازی نگه داشته شد: برنامه را به‌روز کنید. تغییرهای شما روی همین دستگاه مانده‌اند."
  }
}
