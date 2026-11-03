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
 *  IMG-PRO-17 — «تغییرِ دسته‌ایِ عکسِ کالاها و برگشتش» در برنامهٔ مدیریت، روی درِ سرورِ `/api/shop/images/changes`
 *  (`server/src/lib/image-changes.js`، #882). منطقِ خالص، commonMain (گوشی، کامپیوتر و آیفون).
 *
 *  ⛔ همان پرچمِ خاموشِ `shop.image_bank` ⇒ سرور ۴۰۳ `image_bank_disabled` ⇒ صفحه و راهش پنهان (همان امروز).
 *  ⛔ هر قلم همان قاعدهٔ `POST /api/shop/images/select` ِ سرور است؛ این‌جا فقط «کدام کالا با کدام کار» ساخته می‌شود و
 *     کالایی که کار برایش بی‌معناست **پیش از فرستادن** با دلیل کنار می‌رود (نه خطای سرور، نه حدس).
 *  ⛔ تطبیقِ نامطمئن (۴۰۹ `needs_confirmation`) فقط با تأییدِ صریحِ همان کالا (`confirmed`) دوباره فرستاده می‌شود —
 *     هرگز تأییدِ یک‌جا برای همه (IMG-PRO-07). بارکدِ متفاوت (`barcode_mismatch`) تأیید ندارد: کالا کنار می‌رود.
 *  ⛔ برگشت یا همه یا هیچ؛ ۴۰۹ `changed_since` فهرستِ کالاهایی را می‌دهد که پس از دسته دوباره عوض شدند.
 */
object ImageChanges {

  const val PATH = "/shop/images/changes"
  const val OFF = "image_bank_disabled"
  /** همان `MAX_ITEMS` ِ سرور */
  const val MAX_ITEMS = 100
  const val NOTE_MAX = 200
  const val REASON_MAX = 200

  fun productPath(productId: String): String = "/shop/images/products/" + encode(productId)
  fun detailPath(id: String): String = "$PATH/" + encode(id)
  fun rollbackPath(id: String): String = detailPath(id) + "/rollback"
  fun listPath(limit: Int = 50): String = "$PATH?limit=" + limit.coerceIn(1, 50)

  /** درصد-رمزگذاریِ یک پارهٔ نشانی (UTF-8)؛ شناسه‌ها معمولاً ULID‌اند ولی کالای کهنه هر نویسه‌ای دارد */
  fun encode(s: String): String = buildString {
    for (b in s.encodeToByteArray()) {
      val c = b.toInt() and 0xff
      val ch = c.toChar()
      if (ch in 'a'..'z' || ch in 'A'..'Z' || ch in '0'..'9' || ch == '-' || ch == '_' || ch == '.' || ch == '~') append(ch)
      else { append('%'); append("0123456789ABCDEF"[c shr 4]); append("0123456789ABCDEF"[c and 15]) }
    }
  }

  // ── خواندنِ پاسخ ────────────────────────────────────────────

  /** «کدام عکس برای این کالا» در یک لحظه (ستون‌های تصمیمِ سرور) */
  data class Pick(val source: String, val imageId: String, val version: Int, val match: String)

  data class Item(val productId: String, val before: Pick?, val after: Pick?)

  data class Change(
    val id: String,
    val note: String,
    /** `applied` | `rolled_back` */
    val status: String,
    val itemCount: Int,
    val createdBy: String,
    val createdAt: Long,
    val rolledBackAt: Long?,
    val rollbackReason: String,
    val items: List<Item> = emptyList(),
  ) {
    val rolledBack: Boolean get() = status == "rolled_back"
  }

  private fun JsonElement?.obj(): JsonObject? = this as? JsonObject
  private fun JsonElement?.long(): Long? = (this as? JsonPrimitive)?.takeUnless { it is JsonNull || it.isString }?.longOrNull
  private fun JsonElement?.text(): String = (this as? JsonPrimitive)?.takeIf { it.isString }?.content.orEmpty()
  /** `true` ِ JSON فقط — رشتهٔ "true" نه */
  private fun JsonElement?.flag(): Boolean = (this as? JsonPrimitive)?.takeUnless { it.isString }?.booleanOrNull == true

  private fun pickOf(o: JsonObject?): Pick? = o?.let {
    Pick(it["source"].text(), it["image_id"].text(), it["version"].long()?.toInt() ?: 0, it["match_status"].text())
  }

  fun changeOf(o: JsonObject?): Change? {
    if (o == null) return null
    val id = o["id"].text().takeIf { it.isNotBlank() } ?: return null
    return Change(
      id = id,
      note = o["note"].text(),
      status = o["status"].text(),
      itemCount = o["item_count"].long()?.toInt() ?: 0,
      createdBy = o["created_by"].text(),
      createdAt = o["created_at"].long() ?: 0L,
      rolledBackAt = o["rolled_back_at"].long(),
      rollbackReason = o["rollback_reason"].text(),
      items = (o["items"] as? JsonArray).orEmpty().mapNotNull { e ->
        val r = e.obj() ?: return@mapNotNull null
        val pid = r["product_id"].text().takeIf { it.isNotBlank() } ?: return@mapNotNull null
        Item(pid, pickOf(r["before"].obj()), pickOf(r["after"].obj()))
      },
    )
  }

  /** `GET /changes` ⇒ تازه‌ترین بالا (همان ترتیبِ سرور) */
  fun changesOf(o: JsonObject): List<Change> = (o["changes"] as? JsonArray).orEmpty().mapNotNull { changeOf(it.obj()) }

  /** `GET /changes/:id`، پاسخِ اعمال و پاسخِ برگشت — همه `{change}` */
  fun of(o: JsonObject): Change? = changeOf(o["change"].obj())

  /** پاسخِ برگشت: «پیش‌تر برگشته بود» (تکرار پس از قطعِ شبکه) */
  fun already(o: JsonObject): Boolean = o["already"].flag()

  // ── وضعِ کنونیِ یک کالا (`GET /products/:id`) ────────────────

  data class Current(
    val productId: String,
    /** `central` | `shop` | `none` | `legacy_photo` */
    val source: String,
    val imageId: String,
    val version: Int,
    /** آخرین عکسِ مرکزیِ سنجاق‌شده (برای «برگشت به مرکزی»)؛ null ⇒ هرگز مرکزی نداشت */
    val centralImageId: String?,
    val centralVersion: Int,
    /** نسخهٔ تازه‌ترِ تأییدشدهٔ همان عکسِ مرکزی؛ ۰ ⇒ نیست */
    val updateAvailable: Int,
    val archived: Boolean,
  )

  fun currentOf(o: JsonObject): Current {
    val central = o["central"].obj()
    return Current(
      productId = o["product_id"].text(),
      source = o["source"].text().ifEmpty { "none" },
      imageId = o["image_id"].text(),
      version = o["version"].long()?.toInt() ?: 0,
      centralImageId = central?.get("image_id").text().takeIf { it.isNotBlank() },
      centralVersion = central?.get("version").long()?.toInt() ?: 0,
      updateAvailable = o["update_available"].long()?.toInt() ?: 0,
      archived = o["archived"].flag(),
    )
  }

  // ── ساختنِ دسته ─────────────────────────────────────────────

  enum class Action {
    /** نسخهٔ تازه‌ترِ تأییدشدهٔ عکسِ مرکزیِ همان کالا */
    LATEST_CENTRAL,
    /** برگشت به آخرین عکسِ مرکزیِ همان کالا (همان نسخهٔ سنجاق‌شده؛ IMG-BANK-16) */
    REVERT_CENTRAL,
  }

  enum class Skip {
    /** هرگز عکسِ مرکزی نداشت */
    NO_CENTRAL,
    /** همین حالا همان است */
    ALREADY,
    /** عکسِ مرکزی بایگانی شده و دیگر انتخاب نمی‌شود */
    ARCHIVED,
    /** وضعِ کالا از سرور نیامد */
    UNKNOWN,
  }

  data class Product(val id: String, val name: String, val barcode: String)

  /** یک قلمِ آمادهٔ فرستادن (همان شکلِ `items[]` ِ سرور) */
  data class Line(val productId: String, val source: String, val imageId: String?, val version: Int?, val name: String, val barcode: String)

  sealed interface Planned {
    data class Send(val line: Line) : Planned
    data class Skipped(val productId: String, val why: Skip) : Planned
  }

  /** کارِ [action] برای یک کالا با وضعِ [current] (`null` ⇒ نیامد) */
  fun plan(action: Action, p: Product, current: Current?): Planned {
    if (current == null) return Planned.Skipped(p.id, Skip.UNKNOWN)
    return when (action) {
      Action.LATEST_CENTRAL -> {
        val onCentral = current.source == "central" && current.imageId.isNotBlank()
        val image = if (onCentral) current.imageId else current.centralImageId
        when {
          image == null -> Planned.Skipped(p.id, Skip.NO_CENTRAL)
          onCentral && current.archived -> Planned.Skipped(p.id, Skip.ARCHIVED)
          onCentral && current.updateAvailable <= 0 -> Planned.Skipped(p.id, Skip.ALREADY)
          //  نسخهٔ تازه همان `update_available` ِ سرور؛ کالایی که حالا مرکزی نیست ⇒ بی نسخه (سرور: نسخهٔ جاریِ تأییدشده)
          else -> Planned.Send(
            Line(p.id, "central", image, if (onCentral) current.updateAvailable else null, p.name, p.barcode),
          )
        }
      }
      Action.REVERT_CENTRAL -> when {
        current.centralImageId == null -> Planned.Skipped(p.id, Skip.NO_CENTRAL)
        current.source == "central" -> Planned.Skipped(p.id, Skip.ALREADY)
        //  بی image_id ⇒ سرور همان آخرین مرکزی را با همان تطبیقِ پیشین برمی‌گرداند
        else -> Planned.Send(Line(p.id, "central", null, null, p.name, p.barcode))
      }
    }
  }

  /**
   *  بدنهٔ `POST /changes`. [confirmed] فقط کالاهایی که کاربر تطبیقِ نامطمئنشان را **یکی‌یکی** تأیید کرد.
   *  `expect` (بارکد و نام) فقط برای انتخابِ عکسِ مرکزیِ مشخص — همان `UseCentralVersion` ِ صفحهٔ کالا.
   */
  fun body(key: String, note: String, lines: List<Line>, confirmed: Set<String>): JsonObject {
    require(lines.size in 1..MAX_ITEMS) { "۱ تا $MAX_ITEMS کالا" }
    return buildJsonObject {
      put("key", key)
      note.trim().take(NOTE_MAX).takeIf { it.isNotEmpty() }?.let { put("note", it) }
      put(
        "items",
        buildJsonArray {
          lines.forEach { l ->
            add(
              buildJsonObject {
                put("product_id", l.productId)
                put("source", l.source)
                l.imageId?.let { put("image_id", it) }
                l.version?.let { put("version", it) }
                if (l.imageId != null) put("expect", buildJsonObject { put("barcode", l.barcode); put("name", l.name) })
                if (l.productId in confirmed) put("confirm", true)
              },
            )
          }
        },
      )
    }
  }

  fun rollbackBody(reason: String): JsonObject = buildJsonObject {
    reason.trim().take(REASON_MAX).takeIf { it.isNotEmpty() }?.let { put("reason", it) }
  }

  // ── خطاهای سرور ─────────────────────────────────────────────

  /** خطای اعمال یا برگشت: کد، کالای همان قلم (اعمال) یا کالاهای عوض‌شده (`changed_since`) */
  data class Problem(val code: String, val productId: String?, val products: List<String>) {
    /** تطبیقِ نامطمئن ⇒ تأییدِ همین یک کالا و فرستادنِ دوباره */
    val confirmable: Boolean get() = code == "needs_confirmation" && productId != null
  }

  /** از کدِ خطا و شیءِ `error` ِ پاسخ (`ApiFailure.detail`) */
  fun problem(code: String, detail: JsonObject?, sent: List<Line> = emptyList()): Problem {
    val index = detail?.get("index").long()?.toInt()
    val pid = detail?.get("product_id").text().takeIf { it.isNotBlank() }
      ?: index?.let { sent.getOrNull(it)?.productId }
    val products = (detail?.get("products") as? JsonArray).orEmpty().mapNotNull { (it as? JsonPrimitive)?.takeIf { p -> p.isString }?.content }
    return Problem(code, pid, products)
  }
}
