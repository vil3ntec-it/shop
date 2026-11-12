package ir.vil3ntec.tohid.core.image

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put

/**
 *  ۴-۲۳ پرسشِ ۱ «الف» (AI-02، IMG-PRO-01..04) — صفحهٔ «استودیوی عکس» در برنامه روی درِ سرورِ `/api/shop/images/studio`
 *  (`server/src/routes/shop-image-studio.js`). منطقِ خالص، commonMain (گوشی، کامپیوتر و آیفون).
 *
 *  ⛔ پیش‌فرض خاموش: `shop.image_bank` + `shop.image_studio`؛ هر کدام خاموش ⇒ سرور ۴۰۳ ⇒ صفحه «روشن نیست».
 *  ⛔ خروجیِ استودیو نسخهٔ تازهٔ «نیازمندِ بررسی» است و اصل دست نمی‌خورد؛ فقط تأییدِ صریحِ همان نسخه منتشرش می‌کند.
 *     نسخه‌ای که سرور «خطر» برایش گفته (`risk`) فقط با تأییدِ جدای همان خطر (`confirm_risk`) — هرگز یک‌جا.
 *  ⛔ ساختِ دسته با کلیدِ یکتایی ⇒ دوباره فرستادنِ همان کلید (پس از قطعِ شبکه) همان دسته را برمی‌گرداند، نه دستهٔ دوم.
 */
object ImageStudio {

  const val PATH = "/shop/images/studio"
  const val MINE = "/shop/images/mine"
  /** همان سه presetِ سرور (`image-studio.PRESETS`) */
  val PRESETS = listOf("catalog", "enhance", "ad")
  val ACTIONS = setOf("pause", "resume", "cancel", "retry")

  val configPath: String get() = "$PATH/config"
  fun batchesPath(limit: Int = 20): String = "$PATH/batches?limit=" + limit.coerceIn(1, 50)
  val createPath: String get() = "$PATH/batches"
  fun batchPath(id: String, after: String = ""): String =
    "$PATH/batches/" + ImageChanges.encode(id) + (if (after.isNotEmpty()) "?after=" + ImageChanges.encode(after) else "")
  fun actionPath(id: String): String = "$PATH/batches/" + ImageChanges.encode(id) + "/action"
  fun reviewPath(imageId: String, version: Int): String =
    "$PATH/images/" + ImageChanges.encode(imageId) + "/versions/" + version + "/review"
  fun minePath(cursor: String = "", limit: Int = 60): String =
    "$MINE?limit=" + limit.coerceIn(1, 100) + (if (cursor.isNotEmpty()) "&cursor=" + ImageChanges.encode(cursor) else "")

  // ── خواندنِ پاسخ ────────────────────────────────────────────

  data class Config(val presets: List<String>, val batchMax: Int, val openMax: Int)

  /** یک دسته در فهرست */
  data class Summary(val id: String, val state: String, val preset: String, val total: Int, val finished: Int, val createdAt: Long) {
    val percent: Int get() = if (total <= 0) 0 else (finished * 100 / total).coerceIn(0, 100)
  }

  /** یک عکس در دسته: پیش از کار (`before`) و خروجی (`after`، تا کار تمام نشده null) */
  data class Item(
    val imageId: String,
    /** `queued` | `running` | `done` | `failed` | `cancelled` */
    val status: String,
    val error: String?,
    val sourceVersion: Int,
    val resultVersion: Int?,
    /** `needs_review` | `approved` | `rejected` | null */
    val review: String?,
    val risk: List<String>,
    val before: String,
    val after: String?,
  ) {
    val reviewable: Boolean get() = status == "done" && resultVersion != null && review == "needs_review"
  }

  data class Progress(val queued: Int, val running: Int, val done: Int, val failed: Int, val cancelled: Int, val finished: Int, val percent: Int)

  data class Batch(
    val id: String,
    /** `active` | `paused` | `cancelled` (دستهٔ تمام‌شده هم `active` می‌ماند؛ «تمام» از پیشرفت) */
    val state: String,
    val preset: String,
    val total: Int,
    val progress: Progress,
    val needsReview: Int,
    val items: List<Item>,
    val next: String?,
  ) {
    val finished: Boolean get() = state == "cancelled" || progress.finished >= total
    /** کدام کارها برای این حالِ دسته معنا دارند (همان قاعدهٔ `batchAction` ِ سرور؛ این فقط دکمه‌ها را پنهان می‌کند) */
    val actions: List<String> get() = buildList {
      if (state == "cancelled") return@buildList
      if (state == "active" && !finished) add("pause")
      if (state == "paused") add("resume")
      if (!finished) add("cancel")
      if (progress.failed > 0) add("retry")
    }
  }

  /** یک عکسِ خودِ دکان برای «دستهٔ تازه» */
  data class Mine(val id: String, val productId: String, val thumb: String?)

  fun configOf(o: JsonObject): Config? {
    if (o["enabled"].flag().not()) return null
    val presets = (o["presets"] as? JsonObject)?.keys?.filter { it in PRESETS }.orEmpty().ifEmpty { PRESETS }
    val l = o["limits"].obj()
    return Config(presets, l?.get("batch_max").long()?.toInt()?.coerceAtLeast(1) ?: 100, l?.get("open_max").long()?.toInt() ?: 3)
  }

  fun batchesOf(o: JsonObject): List<Summary> = (o["items"] as? JsonArray).orEmpty().mapNotNull { e ->
    val b = e.obj() ?: return@mapNotNull null
    val id = b["id"].text().ifEmpty { return@mapNotNull null }
    Summary(id, b["state"].text(), b["preset"].text(), b["total"].long()?.toInt() ?: 0, b["finished"].long()?.toInt() ?: 0, b["created_at"].long() ?: 0L)
  }

  fun batchOf(o: JsonObject): Batch? {
    val id = o["id"].text().ifEmpty { return null }
    val p = o["progress"].obj()
    fun n(k: String) = p?.get(k).long()?.toInt() ?: 0
    val items = (o["items"] as? JsonArray).orEmpty().mapNotNull { itemOf(it.obj()) }
    return Batch(
      id, o["state"].text(), o["preset"].text(), o["total"].long()?.toInt() ?: items.size,
      Progress(n("queued"), n("running"), n("done"), n("failed"), n("cancelled"), n("finished"), n("percent").coerceIn(0, 100)),
      o["review"].obj()?.get("needs_review").long()?.toInt() ?: 0,
      items, o["next"].text().ifEmpty { null },
    )
  }

  private fun itemOf(i: JsonObject?): Item? {
    val id = i?.get("image_id").text().ifEmpty { return null }
    return Item(
      id, i!!["status"].text(), i["error"].text().ifEmpty { null },
      i["source_version"].long()?.toInt() ?: 0, i["result_version"].long()?.toInt()?.takeIf { it > 0 },
      i["review"].text().ifEmpty { null },
      (i["risk"] as? JsonArray).orEmpty().map { it.text() }.filter { it.isNotEmpty() },
      i["before"].text(), i["after"].text().ifEmpty { null },
    )
  }

  fun mineOf(o: JsonObject): Pair<List<Mine>, String?> =
    (o["items"] as? JsonArray).orEmpty().mapNotNull { e ->
      val m = e.obj() ?: return@mapNotNull null
      val id = m["id"].text().ifEmpty { return@mapNotNull null }
      Mine(id, m["product_id"].text(), m["urls"].obj()?.get("thumb").text().ifEmpty { null })
    } to o["next_cursor"].text().ifEmpty { null }

  // ── ساختنِ درخواست ───────────────────────────────────────────

  sealed interface Check {
    data object Ok : Check
    data object Empty : Check
    data class TooMany(val max: Int) : Check
    data object BadPreset : Check
  }

  /** پیش از فرستادن: دستِ‌کم یک عکس، نه بیش از سقفِ سرور، presetِ شناخته (بی تکرار) */
  fun check(imageIds: Collection<String>, preset: String, max: Int): Check = when {
    preset !in PRESETS -> Check.BadPreset
    imageIds.toSet().isEmpty() -> Check.Empty
    imageIds.toSet().size > max -> Check.TooMany(max)
    else -> Check.Ok
  }

  fun createBody(key: String, imageIds: Collection<String>, preset: String): JsonObject = buildJsonObject {
    put("key", key)
    put("image_ids", buildJsonArray { imageIds.distinct().forEach { add(JsonPrimitive(it)) } })
    put("preset", preset)
  }

  fun actionBody(action: String): JsonObject {
    require(action in ACTIONS) { "action" }
    return buildJsonObject { put("action", action) }
  }

  /** تأیید یا رد؛ `confirmRisk` فقط وقتی همین نسخه خطر دارد و کاربر همان را جدا تأیید کرد */
  fun reviewBody(approve: Boolean, confirmRisk: Boolean): JsonObject = buildJsonObject {
    put("decision", if (approve) "approve" else "reject")
    if (approve && confirmRisk) put("confirm_risk", true)
  }

  private fun JsonElement?.obj(): JsonObject? = this as? JsonObject
  private fun JsonElement?.long(): Long? = (this as? JsonPrimitive)?.takeUnless { it is JsonNull || it.isString }?.longOrNull
  private fun JsonElement?.text(): String = (this as? JsonPrimitive)?.takeIf { it.isString }?.content.orEmpty()
  private fun JsonElement?.flag(): Boolean = (this as? JsonPrimitive)?.takeUnless { it.isString }?.booleanOrNull == true
}
