package ir.vil3ntec.tohid.core.hub

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 *  S29 / SYN-09 — دفترِ «فقط‌افزودنیِ» opهای v1 در هابِ شبکهٔ دکان (`docs/SYN-09-HUB-fa.md` §۴.۱).
 *
 *  گوشی‌ای که push ِ v1 اش به سرور نرسید **همان بدنهٔ** `POST /sync/v1/push` را به هاب می‌دهد. هاب:
 *  - هر دسته را با `seq` ِ یکنوا **به ته** می‌افزاید و هرگز عوضش نمی‌کند (نه ادغام، نه بریدن، نه op ِ تازه)؛
 *  - وقتی اینترنت برگشت، دسته‌ها را به ترتیب و با **توکنِ خودِ همان دستگاه** از همان راهِ همیشگیِ push ِ برنامه
 *    به سرور می‌دهد (`HubLogForwarder`)؛ سرور با `op_id` ِ یکتا تکرار را «duplicate» می‌کند، پس هاب، خودِ
 *    دستگاه یا هر دو — هر op فقط یک ردیف؛
 *  - opهای دستگاه‌های دیگر را به گوشیِ جفت‌شده می‌دهد ([feed]) تا بی اینترنت هم موجودی و فروشِ هم را ببینند.
 *  ⛔ هاب مرجع نیست و هیچ op ای را در دفترِ سرور «اعمال» نمی‌کند؛ فقط رله است. op ِ رسیده از هاب روی گوشیِ دیگر
 *     «موقت» است و pull ِ سرور (همان cursor ِ پیشین) همان opها را به ترتیبِ سرور دوباره می‌آورد و مرجع است.
 *  ⚠️ پیشرفتِ فرستادن جدا از دفتر نگه داشته می‌شود ([HubMarks])؛ پاک کردن فقط «فشرده‌سازی» ِ دسته‌های فرستاده‌شدهٔ
 *     کهنه است ([compact]) و `seq` هرگز دوباره به کار نمی‌رود. دفترِ از نو ساخته‌شده `epoch` ِ تازه دارد تا گوشی
 *     cursor ِ هابش را صفر کند.
 */
@Serializable
data class HubEntry(
  val seq: Long,
  val deviceId: String,
  val opIds: List<String>,
  /** بدنهٔ دقیقِ push ِ v1 ِ دستگاه */
  val body: String,
  val app: String = "",
  val appVersion: String = "",
  /** `X-Branch-Id` ِ دستگاه (اگر فرستاد) — با همان دسته به سرور می‌رود */
  val branch: String = "",
  val receivedAt: Long = 0,
  val bytes: Int = 0,
)

@Serializable
data class HubMarks(
  val version: Int = 1,
  /** شناسهٔ همین دفتر — دفترِ تازه (پس از خرابی) شناسهٔ تازه */
  val epoch: String = "",
  /** دسته‌هایی که سرور پاسخِ ۲۰۰ داد */
  val done: List<Long> = emptyList(),
  /** دسته‌هایی که سرور کلشان را رد کرد یا کهنه شدند */
  val dropped: List<Long> = emptyList(),
  /** `op_id` ⇒ `server_seq` (پذیرفته یا تکراری) — گوشیِ جلوتر از آن را دوباره نمی‌گیرد */
  val serverSeq: Map<String, Long> = emptyMap(),
  /** `op_id`هایی که سرور تک‌تک رد کرد — هرگز به گوشیِ دیگر داده نمی‌شوند */
  val rejected: List<String> = emptyList(),
  val forwarded: Long = 0,
  val droppedCount: Long = 0,
  /** بزرگ‌ترین `seq` ِ فشرده‌شده — شمارهٔ بعدی همیشه بزرگ‌تر */
  val compactedTo: Long = 0,
)

data class HubLogState(val entries: List<HubEntry> = emptyList(), val marks: HubMarks = HubMarks()) {
  val head: Long get() = maxOf(entries.lastOrNull()?.seq ?: 0L, marks.compactedTo)
}

data class HubLogLimits(
  val queue: HubLimits = HubLimits(),
  /** دستهٔ فرستاده‌شده تا این مدت برای گوشی‌های دیگر می‌ماند */
  val feedKeepMs: Long = 24L * 3_600_000,
  /** بیشینهٔ op در یک پاسخِ [HubLog.feed] */
  val feedOps: Int = 500,
  val keepSeqs: Int = 50_000,
)

sealed class HubAppend {
  data class Accepted(val entry: HubEntry) : HubAppend()
  /** همهٔ opها پیش‌تر در دفتر بودند — چیزی افزوده نشد */
  object Known : HubAppend()
  data class Full(val code: String) : HubAppend()
}

data class HubFeed(val ops: List<JsonObject>, val next: Long, val more: Boolean, val epoch: String)

object HubLog {
  const val PROTOCOL: Int = 1
  private val json = Json { ignoreUnknownKeys = true }
  private val ACCEPTED = setOf("applied", "duplicate")

  fun isOpen(s: HubLogState, e: HubEntry): Boolean = e.seq !in s.marks.done && e.seq !in s.marks.dropped

  fun pending(s: HubLogState): List<HubEntry> {
    val closed = (s.marks.done + s.marks.dropped).toHashSet()
    return s.entries.filter { it.seq !in closed }
  }

  fun pendingFor(s: HubLogState, deviceId: String): Int = pending(s).count { it.deviceId == deviceId }

  /**
   *  دسته‌ای با دست‌کم یک op ِ تازه **کامل** به ته افزوده می‌شود (تکراری‌هایش را سرور «duplicate» می‌کند)؛
   *  دسته‌ای که همهٔ opهایش را همین دستگاه پیش‌تر به هاب داده بود افزوده نمی‌شود.
   */
  fun append(
    s: HubLogState, deviceId: String, opIds: List<String>, body: String, app: String, appVersion: String,
    branch: String, now: Long, limits: HubLogLimits = HubLogLimits(),
  ): Pair<HubLogState, HubAppend> {
    val mine = s.entries.filter { it.deviceId == deviceId }.flatMapTo(HashSet()) { it.opIds }
    if (opIds.all { it in mine || it in s.marks.serverSeq }) return s to HubAppend.Known
    val q = limits.queue
    val open = pending(s)
    if (open.count { it.deviceId == deviceId } >= q.perDevice) return s to HubAppend.Full("hub_device_full")
    val bytes = body.encodeToByteArray().size
    if (s.entries.size >= q.maxBatches || s.entries.sumOf { it.bytes.toLong() } + bytes > q.maxBytes) return s to HubAppend.Full("hub_full")
    val e = HubEntry(s.head + 1, deviceId, opIds, body, app, appVersion, branch, now, bytes)
    return s.copy(entries = s.entries + e) to HubAppend.Accepted(e)
  }

  /** نخستین دستهٔ باز به ترتیبِ رسیدن، از دستگاهی که کنار گذاشته نشده — FIFO ِ هر دستگاه نمی‌شکند */
  fun next(s: HubLogState, skip: Set<String>): HubEntry? {
    val closed = (s.marks.done + s.marks.dropped).toHashSet()
    return s.entries.firstOrNull { it.seq !in closed && it.deviceId !in skip }
  }

  /** پاسخِ ۲۰۰ ِ سرور: `op_id` ⇒ (وضعیت، `server_seq`) */
  fun resultsOf(body: String?): Map<String, Pair<String, Long>>? {
    val root = body?.let { runCatching { json.parseToJsonElement(it) }.getOrNull() } as? JsonObject ?: return null
    val arr = root["results"] as? JsonArray ?: return null
    val out = HashMap<String, Pair<String, Long>>()
    for (r in arr) {
      val o = r as? JsonObject ?: continue
      val id = (o["op_id"] as? JsonPrimitive)?.content ?: continue
      out[id] = (o["status"] as? JsonPrimitive)?.content.orEmpty() to ((o["server_seq"] as? JsonPrimitive)?.content?.toLongOrNull() ?: 0L)
    }
    return out
  }

  fun settle(s: HubLogState, e: HubEntry, results: Map<String, Pair<String, Long>>, limits: HubLogLimits = HubLogLimits()): HubLogState {
    if (!isOpen(s, e)) return s
    val seqs = LinkedHashMap(s.marks.serverSeq)
    val rejected = ArrayList(s.marks.rejected)
    for (id in e.opIds) {
      val (st, seq) = results[id] ?: continue
      if (st in ACCEPTED && seq > 0) seqs[id] = seq
      if (st == "rejected" && id !in rejected) rejected += id
    }
    val trimmed = if (seqs.size > limits.keepSeqs) LinkedHashMap(seqs.entries.toList().takeLast(limits.keepSeqs).associate { it.key to it.value }) else seqs
    return s.copy(
      marks = s.marks.copy(
        done = s.marks.done + e.seq, serverSeq = trimmed, rejected = rejected.takeLast(limits.keepSeqs),
        forwarded = s.marks.forwarded + 1,
      ),
    )
  }

  fun drop(s: HubLogState, e: HubEntry): HubLogState {
    if (!isOpen(s, e)) return s
    return s.copy(marks = s.marks.copy(dropped = s.marks.dropped + e.seq, droppedCount = s.marks.droppedCount + 1))
  }

  /**
   *  فشرده‌سازی: دستهٔ بسته (فرستاده یا ردشده) کهنه‌تر از `feedKeepMs` بیرون؛ دستهٔ بازِ کهنه‌تر از `maxAgeMs`
   *  «ردشده» (دستگاه خودش دارد). ساعتِ عقب‌رفته چیزی پاک نمی‌کند. `seq` ها دست نمی‌خورند.
   */
  fun compact(s: HubLogState, now: Long, limits: HubLogLimits = HubLogLimits()): HubLogState {
    var st = s
    for (e in s.entries) if (isOpen(st, e) && now - e.receivedAt > limits.queue.maxAgeMs) st = drop(st, e)
    val closed = (st.marks.done + st.marks.dropped).toHashSet()
    val keep = st.entries.filter { it.seq !in closed || now - it.receivedAt <= limits.feedKeepMs }
    if (keep.size == st.entries.size) return st
    val gone = st.entries.filter { it !in keep }
    val left = keep.mapTo(HashSet()) { it.seq }
    return st.copy(
      entries = keep,
      marks = st.marks.copy(
        done = st.marks.done.filter { it in left }, dropped = st.marks.dropped.filter { it in left },
        compactedTo = maxOf(st.marks.compactedTo, gone.maxOf { it.seq }),
      ),
    )
  }

  /**
   *  opهای دستگاه‌های **دیگر** پس از `since` (cursor ِ هابِ گوشی)، با `device_id` و `hub_seq` ِ هر op.
   *  - op ِ ردشدهٔ سرور، op ِ دسته‌ی ردشده، و op ای که `server_seq` ش ≤ [cloudCursor] است (گوشی آن را از سرور
   *    گرفته) داده نمی‌شود؛ هر `op_id` یک بار.
   *  - دسته نیمه داده نمی‌شود: `next` همیشه `seq` ِ آخرین دستهٔ کامل است.
   */
  fun feed(s: HubLogState, deviceId: String, since: Long, cloudCursor: Long, limits: HubLogLimits = HubLogLimits()): HubFeed {
    val out = ArrayList<JsonObject>()
    val ids = HashSet<String>()
    val rejected = s.marks.rejected.toHashSet()
    val dropped = s.marks.dropped.toHashSet()
    var next = maxOf(since, 0L)
    var more = false
    for (e in s.entries) {
      if (e.seq <= since) continue
      val ops = if (e.deviceId == deviceId || e.seq in dropped) emptyList() else opsOf(e)
      val take = ops.filter { o ->
        val id = (o["op_id"] as? JsonPrimitive)?.content.orEmpty()
        val seq = s.marks.serverSeq[id]
        id.isNotEmpty() && id !in rejected && id !in ids && (seq == null || seq > cloudCursor)
      }
      if (out.isNotEmpty() && out.size + take.size > limits.feedOps) { more = true; break }
      for (o in take) {
        val id = (o["op_id"] as JsonPrimitive).content
        ids += id
        val seq = s.marks.serverSeq[id]
        out += JsonObject(o + mapOf("device_id" to JsonPrimitive(e.deviceId), "hub_seq" to JsonPrimitive(e.seq)) +
          (if (seq != null) mapOf("server_seq" to JsonPrimitive(seq)) else emptyMap()))
      }
      next = e.seq
    }
    if (!more) next = maxOf(next, s.head.coerceAtLeast(since))
    return HubFeed(out, next, more, s.marks.epoch)
  }

  private fun opsOf(e: HubEntry): List<JsonObject> = runCatching {
    ((json.parseToJsonElement(e.body) as JsonObject)["ops"] as JsonArray).mapNotNull { it as? JsonObject }
  }.getOrDefault(emptyList())

  fun encodeEntry(e: HubEntry): String = json.encodeToString(HubEntry.serializer(), e)

  fun decodeEntry(text: String?): HubEntry? = text?.let { runCatching { json.decodeFromString(HubEntry.serializer(), it) }.getOrNull() }

  fun encodeMarks(m: HubMarks): String = json.encodeToString(HubMarks.serializer(), m)

  fun decodeMarks(text: String?): HubMarks? = text?.let {
    runCatching { json.decodeFromString(HubMarks.serializer(), it) }.getOrNull()
  }?.takeIf { it.version == 1 }
}

/** درگاهِ سرور برای دفترِ v1 — همان `POST /sync/v1/push` ِ برنامه با توکن و سرآیندهای همان دستگاه */
interface HubV1Cloud {
  fun pushV1(entry: HubEntry, token: String): HubReply
}

/** نگه‌دارندهٔ دفتر — `update` اتمی است و پیش از برگشتن روی دیسک می‌نشیند */
interface HubLogBox {
  fun read(): HubLogState
  fun update(change: (HubLogState) -> HubLogState): HubLogState
}

/**
 *  فرستادنِ دفترِ v1 به سرور، به ترتیب؛ همان قاعدهٔ `HubForwarder` (تماس بیرون از قفل، برداشتن با `seq`،
 *  سقوط میانِ پاسخ و ذخیره ⇒ همان دسته دوباره ⇒ «duplicate»).
 */
class HubLogForwarder(private val box: HubLogBox, private val cloud: HubV1Cloud, private val limits: HubLogLimits = HubLogLimits()) {

  fun drain(tokenOf: (String) -> String?, onAuthFailed: (deviceId: String, token: String) -> Unit, max: Int = 100): HubForwarder.Pass {
    val skip = HashSet<String>()
    val waiting = HashSet<String>()
    val authFailed = HashSet<String>()
    var forwarded = 0
    var dropped = 0
    for (i in 0 until max) {
      val e = HubLog.next(box.read(), skip) ?: break
      val token = tokenOf(e.deviceId)
      if (token == null) { skip += e.deviceId; waiting += e.deviceId; continue }
      val reply = runCatching { cloud.pushV1(e, token) }.getOrElse { HubReply(0, null) }
      when (HubQueue.outcomeOf(reply.status, reply.body)) {
        HubOutcome.DONE -> { box.update { HubLog.settle(it, e, HubLog.resultsOf(reply.body).orEmpty(), limits) }; forwarded++ }
        HubOutcome.DROPPED -> { box.update { HubLog.drop(it, e) }; dropped++ }
        HubOutcome.AUTH -> { onAuthFailed(e.deviceId, token); skip += e.deviceId; authFailed += e.deviceId }
        HubOutcome.RETRY -> return HubForwarder.Pass(forwarded, dropped, retry = true, waiting = waiting, authFailed = authFailed)
        HubOutcome.UPGRADE -> return HubForwarder.Pass(forwarded, dropped, upgrade = true, waiting = waiting, authFailed = authFailed)
      }
    }
    return HubForwarder.Pass(forwarded, dropped, waiting = waiting, authFailed = authFailed)
  }
}
