package ir.vil3ntec.tohid.core.hub

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 *  SYN-09 (§۳.۸.۵) — صفِ پایدارِ هابِ محلی (`docs/SYN-09-HUB-fa.md`).
 *
 *  دسته‌ای که دستگاهِ شبکهٔ دکان برای `POST /sync/v3/push` ساخته بود، وقتی سرور در دسترسش نیست به هاب
 *  می‌رسد و هاب **همان بایت‌ها** را (همان `device_id`، همان `op_id`ها) بعداً به سرور می‌دهد.
 *
 *  ⛔ هاب مرجع نیست: هیچ op ای نمی‌سازد، عوض نمی‌کند و نمی‌شکند (گروهِ `tx` دست نمی‌خورد). سرور با
 *     `op_id` ِ یکتا تکرار را «duplicate» می‌کند (`server/test/sync-v3.test.js`، SYN-03)، پس فرستادنِ
 *     دوباره — از هاب یا خودِ دستگاه — هرگز ردیفِ دوم نمی‌سازد.
 *  ⛔ دستگاه opهایش را تا پاسخِ **خودِ سرور** در صفِ خودش نگه می‌دارد؛ گم شدنِ هاب چیزی را گم نمی‌کند.
 *  ⚠️ ترتیب: هر دستگاه FIFO ِ خودش (هرگز دستهٔ دوم پیش از اول). میانِ دستگاه‌ها ترتیبِ رسیدن، جز
 *     دستگاهی که کنار گذاشته شده (توکنش در حافظه نیست) — سرور میانِ دستگاه‌ها با HLC می‌سنجد، نه ترتیب.
 */
@Serializable
data class HubBatch(
  /** ترتیبِ رسیدن به هاب — یکنوا، هرگز دوباره به کار نمی‌رود */
  val seq: Long,
  val deviceId: String,
  val opIds: List<String>,
  /** بدنهٔ دقیقِ push ِ دستگاه */
  val body: String,
  /** `X-App` و `X-App-Version` ِ دستگاه — تورِ `newer-fields` ِ سرور از نسخه می‌خواند */
  val app: String = "",
  val appVersion: String = "",
  val receivedAt: Long = 0,
  val bytes: Int = 0,
)

@Serializable
data class HubQueueState(
  val version: Int = 1,
  val seq: Long = 0,
  val batches: List<HubBatch> = emptyList(),
  /** `op_id`هایی که سرور پاسخِ قطعی‌شان را داد — تازه‌ترین آخر، با سقف */
  val settled: List<String> = emptyList(),
  val forwarded: Long = 0,
  val dropped: Long = 0,
)

/** سقف‌ها — همان سقف‌های سرور (`MAX_OPS` ۵۰۰، ۲۵۶ کیلوبایت برای opها) با جای سرآیندِ بدنه */
data class HubLimits(
  val maxOps: Int = 500,
  val maxBodyBytes: Int = 320 * 1024,
  val perDevice: Int = 200,
  val maxBatches: Int = 2_000,
  val maxBytes: Long = 16L * 1024 * 1024,
  val settledKeep: Int = 20_000,
  /** دسته‌ای که دو هفته نرفت کنار می‌رود — دستگاه هنوز خودش دارد */
  val maxAgeMs: Long = 14L * 24 * 3_600_000,
)

sealed class HubParse {
  data class Ok(val opIds: List<String>) : HubParse()
  data class Bad(val code: String) : HubParse()
}

sealed class HubOffer {
  /** تازه بود و در صف نشست */
  data class Accepted(val batch: HubBatch) : HubOffer()
  /** هر opاش پیش‌تر در صف بود یا پاسخِ سرور را گرفته بود — چیزی ذخیره نشد */
  data class Known(val settled: Boolean) : HubOffer()
  data class Full(val code: String) : HubOffer()
}

/** پاسخِ سرور به یک دسته */
enum class HubOutcome {
  /** ۲۰۰ با `results` ⇒ از صف بیرون */
  DONE,
  /** نرسید، ۴۰۴ (v3 خاموش)، ۴۰۸، ۴۲۹، ۵xx ⇒ همان‌جا می‌ماند و این دور تمام */
  RETRY,
  /** ۴۰۱/۴۰۳ ⇒ توکنِ همان دستگاه کنار می‌رود و دستگاه تا توکنِ تازه منتظر */
  AUTH,
  /** ۴۲۶ ⇒ سرور چیزی اعمال نکرد؛ هیچ دسته‌ای نمی‌رود */
  UPGRADE,
  /** ۴xx ِ دیگر ⇒ خودِ دسته رد شد؛ از صفِ هاب بیرون (دستگاه نسخهٔ خودش را دارد و خطا را خودش می‌بیند) */
  DROPPED,
}

object HubQueue {

  /** پاسخ‌هایی که سرور دیگر عوضشان نمی‌کند — همان `DONE` ِ موتور به‌اضافهٔ «رد» */
  val FINAL: Set<String> = setOf("applied", "partial", "conflict", "duplicate", "rejected")

  private val json = Json { ignoreUnknownKeys = true }

  private fun idChar(c: Char) = c in 'A'..'Z' || c in 'a'..'z' || c in '0'..'9' || c == '_' || c == '.' || c == '-'

  /** همان `OP_ID_RE` ِ سرور */
  fun validOpId(id: String?): Boolean = id != null && id.length in 1..80 && id.all(::idChar)

  /** همان سقفِ ۶۴ نویسهٔ `device_id` ِ سرور، بی نویسهٔ ناامن */
  fun validDevice(id: String?): Boolean = id != null && id.length in 1..64 && id.all(::idChar)

  /** بدنهٔ push را می‌سنجد و `op_id`ها را بیرون می‌کشد؛ چیزی را عوض نمی‌کند */
  fun parse(body: String, deviceId: String, limits: HubLimits = HubLimits()): HubParse {
    if (body.encodeToByteArray().size > limits.maxBodyBytes) return HubParse.Bad("hub_too_large")
    val root = runCatching { json.parseToJsonElement(body) }.getOrNull() as? JsonObject
      ?: return HubParse.Bad("hub_bad_json")
    val dev = (root["device_id"] as? JsonPrimitive)?.takeIf { it.isString }?.content
    if (dev != deviceId) return HubParse.Bad("hub_device_mismatch")
    val ops = root["ops"] as? JsonArray ?: return HubParse.Bad("hub_no_ops")
    if (ops.isEmpty() || ops.size > limits.maxOps) return HubParse.Bad("hub_ops_count")
    val ids = ArrayList<String>(ops.size)
    for (o in ops) {
      val obj = o as? JsonObject ?: return HubParse.Bad("hub_bad_op")
      val id = ((obj["op_id"] ?: obj["opId"]) as? JsonPrimitive)?.takeIf { it.isString }?.content
      if (!validOpId(id)) return HubParse.Bad("hub_bad_op_id")
      ids += id!!
    }
    return HubParse.Ok(ids.distinct())
  }

  /**
   *  دسته‌ای که دست‌کم یک op ِ تازه دارد **کامل** ذخیره می‌شود (بدنه بریده نمی‌شود؛ تکراری‌هایش را سرور
   *  «duplicate» می‌کند). دسته‌ای که همهٔ opهایش را هاب پیش‌تر داشت یا فرستاده بود ذخیره نمی‌شود.
   */
  fun offer(
    state: HubQueueState, deviceId: String, opIds: List<String>, body: String,
    app: String, appVersion: String, now: Long, limits: HubLimits = HubLimits(),
  ): Pair<HubQueueState, HubOffer> {
    val mine = state.batches.filter { it.deviceId == deviceId }
    val queued = mine.flatMapTo(HashSet()) { it.opIds }
    val settled = state.settled.toHashSet()
    if (opIds.all { it in queued || it in settled }) return state to HubOffer.Known(opIds.all { it in settled })
    val bytes = body.encodeToByteArray().size
    if (mine.size >= limits.perDevice) return state to HubOffer.Full("hub_device_full")
    if (state.batches.size >= limits.maxBatches || state.batches.sumOf { it.bytes.toLong() } + bytes > limits.maxBytes) {
      return state to HubOffer.Full("hub_full")
    }
    val b = HubBatch(
      seq = state.seq + 1, deviceId = deviceId, opIds = opIds, body = body,
      app = app, appVersion = appVersion, receivedAt = now, bytes = bytes,
    )
    return state.copy(seq = b.seq, batches = state.batches + b) to HubOffer.Accepted(b)
  }

  /** نخستین دسته به ترتیبِ رسیدن، از دستگاهی که کنار گذاشته نشده — FIFO ِ هر دستگاه نمی‌شکند */
  fun next(state: HubQueueState, skip: Set<String>): HubBatch? = state.batches.firstOrNull { it.deviceId !in skip }

  /** `op_id` ⇒ وضعیت، از پاسخِ ۲۰۰ ِ سرور؛ بی `results` ⇒ `null` */
  fun resultsOf(body: String?): Map<String, String>? {
    val root = body?.let { runCatching { json.parseToJsonElement(it) }.getOrNull() } as? JsonObject ?: return null
    val arr = root["results"] as? JsonArray ?: return null
    val out = HashMap<String, String>()
    for (r in arr) {
      val o = r as? JsonObject ?: continue
      val id = (o["op_id"] as? JsonPrimitive)?.content ?: continue
      out[id] = (o["status"] as? JsonPrimitive)?.content.orEmpty()
    }
    return out
  }

  fun outcomeOf(status: Int, body: String?): HubOutcome = when {
    status == 200 -> if (resultsOf(body) != null) HubOutcome.DONE else HubOutcome.RETRY
    status == 401 || status == 403 -> HubOutcome.AUTH
    status == 426 -> HubOutcome.UPGRADE
    status <= 0 || status == 404 || status == 408 || status == 429 || status >= 500 -> HubOutcome.RETRY
    else -> HubOutcome.DROPPED
  }

  /** پاسخِ ۲۰۰: دسته بیرون، opهای قطعی‌اش در حلقهٔ «فرستاده‌شده» */
  fun settle(state: HubQueueState, batch: HubBatch, results: Map<String, String>, limits: HubLimits = HubLimits()): HubQueueState {
    if (state.batches.none { it.seq == batch.seq }) return state
    val known = state.settled.toHashSet()
    val add = batch.opIds.filter { results[it] in FINAL && it !in known }
    return state.copy(
      batches = state.batches.filter { it.seq != batch.seq },
      settled = (state.settled + add).takeLast(limits.settledKeep),
      forwarded = state.forwarded + 1,
    )
  }

  fun drop(state: HubQueueState, batch: HubBatch): HubQueueState {
    if (state.batches.none { it.seq == batch.seq }) return state
    return state.copy(batches = state.batches.filter { it.seq != batch.seq }, dropped = state.dropped + 1)
  }

  /** دسته‌های کهنه‌تر از `maxAgeMs` کنار می‌روند (ساعتِ عقب‌رفته چیزی را پاک نمی‌کند) */
  fun expire(state: HubQueueState, now: Long, limits: HubLimits = HubLimits()): HubQueueState {
    val keep = state.batches.filter { now - it.receivedAt <= limits.maxAgeMs }
    if (keep.size == state.batches.size) return state
    return state.copy(batches = keep, dropped = state.dropped + (state.batches.size - keep.size))
  }

  fun queuedFor(state: HubQueueState, deviceId: String): Int = state.batches.count { it.deviceId == deviceId }

  fun encode(state: HubQueueState): String = json.encodeToString(HubQueueState.serializer(), state)

  /** خراب یا نسخهٔ ناشناس ⇒ `null` (هاب با صفِ خالی شروع می‌کند؛ دستگاه‌ها نسخهٔ خودشان را دارند) */
  fun decode(text: String?): HubQueueState? = text?.let {
    runCatching { json.decodeFromString(HubQueueState.serializer(), it) }.getOrNull()
  }?.takeIf { it.version == 1 }
}
