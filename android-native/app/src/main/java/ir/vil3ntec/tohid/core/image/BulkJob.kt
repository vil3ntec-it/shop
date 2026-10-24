package ir.vil3ntec.tohid.core.image

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put

/**
 *  کارِ ورودِ گروهیِ عکس — حالتِ ادامه‌پذیر (v3 ۳۶.۸، IMG-14): هر ردیف و
 *  پیشرفتِ بارگذاری‌اش پس از هر قدم روی دیسک می‌نشیند؛ بستنِ برنامه وسطِ کار
 *  ⇒ همان‌جا ادامه، بی بارگذاریِ دوباره. همتای وب: `plan`/`counts`ِ
 *  `license/image-bank.js`.
 *
 *  ⛔ کالایی که عکس دارد بی تیکِ «جایگزینی» دست نمی‌خورد (۳۶.۶ بندِ آخر).
 *  ⛔ دو فایلِ هم‌محتوا (checksum یکسان) ⇒ دومی «تکراری»، نه دو بارگذاری.
 */
class BulkJob(val id: String, var replace: Boolean = false, val rows: MutableList<Row> = ArrayList()) {

  enum class Status(val wire: String, val label: String) {
    READY("ready", "آمادهٔ ورود"),
    WORKING("working", "در حالِ کار…"),
    DONE("done", "انجام شد"),
    FAILED("failed", "ناموفق"),
    DUPLICATE("duplicate", "تکراری"),
    SAME_PRODUCT("same_product", "تکراری برای همین کالا"),
    AMBIGUOUS("ambiguous", "مبهم — انتخاب کنید"),
    UNMATCHED("unmatched", "بی‌تطبیق"),
    HAS_PHOTO("has_photo", "کالا عکس دارد"),
    PENDING_BANK("pending_bank", "روی دستگاه گذاشته شد؛ منتظرِ بانک"),
    NOT_IMAGE("not_image", "عکس نیست"),
    NEW("", "");

    companion object { fun of(s: String?) = entries.firstOrNull { it.wire == s } ?: NEW }
  }

  /** پیشرفتِ بارگذاری روی بانک — ادامه از همان قدم */
  data class Upload(
    var imageId: String = "",
    var version: Int = 1,
    var display: Boolean = false,
    var thumb: Boolean = false,
    var selected: Boolean = false,
  )

  data class Row(
    val key: Int,
    val file: String,
    val size: Long,
    val sum: String,
    var status: Status = Status.NEW,
    var productId: String = "",
    var candidates: List<String> = emptyList(),
    var how: String = "",
    var note: String = "",
    /** عکسِ کالا روی همین دستگاه گذاشته شد */
    var local: Boolean = false,
    val upload: Upload = Upload(),
    /** ضلعِ بزرگِ اصل زیرِ `ImagePrep.MIN_SIDE` بود (هشدار، نه رد) */
    var small: Boolean = false,
  )

  data class Counts(
    val done: Int, val failed: Int, val duplicate: Int, val ambiguous: Int,
    val unmatched: Int, val hasPhoto: Int, val waiting: Int,
  )

  private val FINAL = setOf(Status.DONE, Status.FAILED, Status.PENDING_BANK, Status.WORKING, Status.NOT_IMAGE)

  /**
   *  هیچ ردیفی دیگر کاری با فایلش ندارد: انجام‌شده، عکس‌نبود، تکراری، یا ناموفق.
   *  (مبهم، بی‌تطبیق و عکس‌دار هنوز با انتخابِ کاربر راه می‌افتند.)
   *  ⇒ `BulkStore.tidy` پوشهٔ فایل‌ها را کامل پاک می‌کند.
   */
  fun settled(): Boolean = rows.all { it.status in SETTLED }

  /** ردیف هنوز ممکن است فایلِ آماده‌اش را بخواهد */
  fun needsFile(r: Row): Boolean = r.status !in SETTLED || r.status == Status.FAILED

  /**
   *  وضعیتِ ردیف‌هایی که هنوز کارشان شروع نشده: تکراری، کالای یکسان،
   *  مبهم/بی‌تطبیق، عکس‌دار یا آماده. `photoOf(id)` ⇒ null یعنی کالا دیگر نیست.
   */
  fun plan(photoOf: (String) -> Boolean?): BulkJob {
    val seenSum = HashSet<String>()
    val seenProduct = HashSet<String>()
    for (r in rows) {
      if (r.status in FINAL) {
        if (r.productId.isNotEmpty()) seenProduct += r.productId
        seenSum += r.sum
        continue
      }
      if (!seenSum.add(r.sum)) { r.status = Status.DUPLICATE; continue }
      if (r.productId.isEmpty()) {
        //  نامزدِ حذف‌شده نامزد نیست؛ «مبهم» حتی با یک نامزد (دو راهِ تطبیق دو جواب دادند)
        r.candidates = r.candidates.filter { photoOf(it) != null }
        r.status = if (r.candidates.isNotEmpty()) Status.AMBIGUOUS else Status.UNMATCHED
        continue
      }
      if (!seenProduct.add(r.productId)) { r.status = Status.SAME_PRODUCT; continue }
      val has = photoOf(r.productId)
      if (has == null) {
        r.candidates = r.candidates.filter { it != r.productId && photoOf(it) != null }
        r.productId = ""
        r.status = if (r.candidates.isNotEmpty()) Status.AMBIGUOUS else Status.UNMATCHED
        continue
      }
      r.status = if (has && !replace) Status.HAS_PHOTO else Status.READY
    }
    return this
  }

  /** انتخابِ دستیِ کالا برای ردیفِ مبهم/بی‌تطبیق */
  fun choose(key: Int, productId: String, photoOf: (String) -> Boolean?) {
    val r = rows.firstOrNull { it.key == key } ?: return
    r.productId = productId
    r.how = "manual"
    r.status = Status.NEW
    plan(photoOf)
  }

  /** پس از بازشدنِ برنامه: «در حالِ کار» یعنی نیمه‌کاره ⇒ دوباره آماده */
  fun reopen(): BulkJob {
    rows.forEach { if (it.status == Status.WORKING) it.status = if (it.local) Status.PENDING_BANK else Status.READY }
    return this
  }

  fun next(): Row? = rows.firstOrNull { it.status == Status.READY || it.status == Status.PENDING_BANK }
  fun startable(): Boolean = next() != null
  fun resumed(): Boolean = startable() && rows.any { it.status == Status.DONE || it.status == Status.PENDING_BANK || it.status == Status.FAILED }

  fun counts(): Counts {
    var done = 0; var failed = 0; var dup = 0; var amb = 0; var un = 0; var has = 0; var wait = 0
    for (r in rows) when (r.status) {
      Status.DONE -> done++
      Status.FAILED, Status.NOT_IMAGE -> failed++
      Status.DUPLICATE, Status.SAME_PRODUCT -> dup++
      Status.AMBIGUOUS -> amb++
      Status.UNMATCHED -> un++
      Status.HAS_PHOTO -> has++
      else -> wait++
    }
    return Counts(done, failed, dup, amb, un, has, wait)
  }

  fun toJson(): String = buildJsonObject {
    put("v", 1)
    put("id", id)
    put("replace", replace)
    put("rows", JsonArray(rows.map { r ->
      buildJsonObject {
        put("key", r.key); put("file", r.file); put("size", r.size); put("sum", r.sum)
        put("status", r.status.wire); put("product", r.productId); put("how", r.how); put("note", r.note)
        put("local", r.local)
        put("small", r.small)
        put("candidates", JsonArray(r.candidates.map { JsonPrimitive(it) }))
        put("up", buildJsonObject {
          put("image", r.upload.imageId); put("version", r.upload.version)
          put("display", r.upload.display); put("thumb", r.upload.thumb); put("selected", r.upload.selected)
        })
      }
    }))
  }.toString()

  companion object {
    private val SETTLED = setOf(Status.DONE, Status.FAILED, Status.NOT_IMAGE, Status.DUPLICATE, Status.SAME_PRODUCT)
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    /** خراب یا نسخهٔ ناشناخته ⇒ null (کارِ تازه، نه برنامهٔ خراب) */
    fun fromJson(text: String): BulkJob? = runCatching {
      val o = json.parseToJsonElement(text).jsonObject
      if ((o["v"]?.jsonPrimitive?.longOrNull ?: 0L) != 1L) return null
      fun JsonObject.s(k: String) = this[k]?.jsonPrimitive?.contentOrNull.orEmpty()
      fun JsonObject.b(k: String) = this[k]?.jsonPrimitive?.booleanOrNull == true
      fun JsonObject.n(k: String) = this[k]?.jsonPrimitive?.longOrNull ?: 0L
      val rows = o["rows"]!!.jsonArray.map { e ->
        val r = e.jsonObject
        val up = r["up"]?.jsonObject ?: JsonObject(emptyMap())
        Row(
          key = r.n("key").toInt(), file = r.s("file"), size = r.n("size"), sum = r.s("sum"),
          status = Status.of(r.s("status")), productId = r.s("product"), how = r.s("how"), note = r.s("note"),
          local = r.b("local"), small = r.b("small"),
          candidates = r["candidates"]?.jsonArray?.mapNotNull { it.jsonPrimitive.contentOrNull }.orEmpty(),
          upload = Upload(up.s("image"), up.n("version").toInt().coerceAtLeast(1), up.b("display"), up.b("thumb"), up.b("selected")),
        )
      }.toMutableList()
      BulkJob(o.s("id"), o.b("replace"), rows)
    }.getOrNull()
  }
}
