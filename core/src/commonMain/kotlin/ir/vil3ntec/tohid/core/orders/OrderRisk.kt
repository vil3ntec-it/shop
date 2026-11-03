package ir.vil3ntec.tohid.core.orders

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.longOrNull

/**
 *  RSK-03 (§۶.۲۱) — «سابقهٔ رد و برگشتِ مشتری پیش از ارسال» در برنامهٔ مدیریت: خوانندهٔ پاسخِ
 *  `GET /api/shop/orders/:id/risk` (`server/src/lib/order-risk.js` ‹`riskOf`›). خالص، بی شبکه و بی ساعت.
 *
 *  ⛔ پرچمِ سرورِ `shop.order_risk` خاموش ⇒ `{enabled:false}` ⇒ [visible] نه ⇒ صفحه همان امروز.
 *  ⛔ فقط همین دکان (سرور می‌شمارد) و بی شماره — این‌جا هم شماره‌ای نیست.
 *  کدِ نشانه‌ای که این نسخه نمی‌شناسد (سرورِ تازه‌تر) کنار نمی‌رود: با شدتِ خودش و متنِ عمومی دیده می‌شود.
 */
object OrderRisk {

  enum class Severity { WARN, INFO }

  data class Flag(val code: String, val severity: Severity, val count: Long, val until: Long? = null, val orderNo: Long? = null)

  data class History(
    val orders: Long = 0,
    val completed: Long = 0,
    val open: Long = 0,
    val rejectedByShop: Long = 0,
    val cancelledByCustomer: Long = 0,
    val cancelledByShop: Long = 0,
    val noShow: Long = 0,
    val deliveryFailed: Long = 0,
    val returnedToShop: Long = 0,
    val returnsOrRefunds: Long = 0,
    val firstOrderAt: Long? = null,
    val lastOrderAt: Long? = null,
  )

  data class View(val enabled: Boolean, val known: Boolean = false, val history: History? = null, val flags: List<Flag> = emptyList())

  /** کدهایی که سرورِ امروز می‌فرستد (`riskOf`) — برای متنِ هر کدام */
  val CODES: List<String> = listOf(
    "blocklisted", "allowlisted", "new_customer", "delivery_failed_before", "no_show_before", "returned_to_shop_before",
    "returns_before", "cancels_often", "many_open", "possible_duplicate",
  )

  private fun obj(o: JsonObject?, k: String): JsonObject? = o?.get(k) as? JsonObject
  private fun num(o: JsonObject?, k: String): Long? = (o?.get(k) as? JsonPrimitive)?.takeIf { !it.isString }?.longOrNull
  private fun str(o: JsonObject?, k: String): String? = (o?.get(k) as? JsonPrimitive)?.takeIf { it.isString }?.contentOrNull
  private fun bool(o: JsonObject?, k: String): Boolean = (o?.get(k) as? JsonPrimitive)?.takeIf { !it.isString }?.booleanOrNull == true

  /** پاسخِ سرور ⇐ نما؛ شکلِ نادرست ⇒ «خاموش» (هیچ چیز نشان داده نمی‌شود، نه ادعای «بی سابقه») */
  fun parse(o: JsonObject?): View {
    if (o == null || !bool(o, "enabled")) return View(enabled = false)
    val h = obj(o, "history")?.let { x ->
      History(
        orders = num(x, "orders") ?: 0, completed = num(x, "completed") ?: 0, open = num(x, "open") ?: 0,
        rejectedByShop = num(x, "rejectedByShop") ?: 0, cancelledByCustomer = num(x, "cancelledByCustomer") ?: 0,
        cancelledByShop = num(x, "cancelledByShop") ?: 0, noShow = num(x, "noShow") ?: 0,
        deliveryFailed = num(x, "deliveryFailed") ?: 0, returnedToShop = num(x, "returnedToShop") ?: 0,
        returnsOrRefunds = num(x, "returnsOrRefunds") ?: 0, firstOrderAt = num(x, "firstOrderAt"), lastOrderAt = num(x, "lastOrderAt"),
      )
    }
    val flags = (o["flags"] as? JsonArray).orEmpty().mapNotNull { e ->
      val f = e as? JsonObject ?: return@mapNotNull null
      val code = str(f, "code")?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
      Flag(
        code = code,
        severity = if (str(f, "severity") == "warn") Severity.WARN else Severity.INFO,
        count = num(f, "count") ?: 0,
        until = num(f, "until"),
        orderNo = num(f, "orderNo"),
      )
    }
    return View(enabled = true, known = bool(o, "known"), history = h, flags = flags)
  }

  /** کارت دیده شود؟ فقط با پرچمِ روشن و چیزی برای گفتن (سابقه یا نشانه) */
  fun visible(v: View): Boolean = v.enabled && (v.known || v.flags.isNotEmpty())

  /** نشانه‌ها برای نمایش: هشدارها اول، به ترتیبِ سرور در هر گروه */
  fun shown(v: View): List<Flag> = v.flags.filter { it.severity == Severity.WARN } + v.flags.filter { it.severity == Severity.INFO }

  /** بیشترین شدت (رنگِ کارت): هشدار اگر یکی هست */
  fun worst(v: View): Severity = if (v.flags.any { it.severity == Severity.WARN }) Severity.WARN else Severity.INFO

  /* ── RSK-04: فهرستِ بسته و مطمئنِ دکان (`/api/shop/orders/risk/lists`، `server/src/lib/risk-lists.js`) ── */

  const val BLOCK = "block"
  const val ALLOW = "allow"
  const val REASON_MIN = 3
  const val REASON_MAX = 200
  const val DAYS_MAX = 365
  const val DAYS_DEFAULT = 30

  /** ردیفِ فعالِ فهرست: شماره یا پین (پین فقط «بسته»)؛ دلیل فقط برای صاحب و مدیر */
  data class Entry(
    val id: String, val list: String, val kind: String, val phone: String?, val lat: Double?, val lng: Double?, val radiusM: Long?,
    val reason: String, val expiresAt: Long,
  )

  /** `GET …/risk/lists` ⇐ ردیف‌ها (همان ترتیبِ سرور: تازه‌ترین بالا)؛ ردیفِ بدشکل کنار */
  fun entries(o: JsonObject?): List<Entry> = (o?.get("items") as? JsonArray).orEmpty().mapNotNull { e ->
    val x = e as? JsonObject ?: return@mapNotNull null
    val id = str(x, "id")?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
    val list = str(x, "list")?.takeIf { it == BLOCK || it == ALLOW } ?: return@mapNotNull null
    val kind = str(x, "kind") ?: return@mapNotNull null
    val pin = obj(x, "pin")
    fun dbl(o: JsonObject?, k: String): Double? = (o?.get(k) as? JsonPrimitive)?.takeIf { !it.isString }?.contentOrNull?.toDoubleOrNull()
    Entry(id, list, kind, str(x, "phone"), dbl(pin, "lat"), dbl(pin, "lng"), num(pin, "radiusM"), str(x, "reason").orEmpty(), num(x, "expiresAt") ?: 0)
  }

  /** فرمِ «بستن/مطمئن کردنِ این شماره» ⇐ کدِ خطای درجا یا `null` (همان مرزهای سرور: دلیل ۳ تا ۲۰۰، ۱ تا ۳۶۵ روز) */
  fun entryError(list: String, phone: String, reason: String, days: Int?): String? = when {
    list != BLOCK && list != ALLOW -> "bad_list"
    phone.isBlank() -> "phone_required"
    reason.trim().length !in REASON_MIN..REASON_MAX -> "reason_required"
    days == null || days !in 1..DAYS_MAX -> "bad_expiry"
    else -> null
  }

  /** بدنهٔ `POST …/risk/lists` برای شمارهٔ کاملِ همین سفارش (فقط شماره؛ پین از برنامه هنوز نه) */
  fun phoneEntryBody(list: String, phone: String, reason: String, days: Int): JsonObject = buildJsonObject {
    put("list", list)
    put("kind", "phone")
    put("phone", phone)
    put("reason", reason.trim().split(Regex("\\s+")).joinToString(" "))
    put("expiresInDays", days)
  }
}
