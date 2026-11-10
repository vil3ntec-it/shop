package ir.vil3ntec.tohid.core.crm

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.longOrNull

/**
 *  CRM-15 — «سابقهٔ دسترسی»: چه کسی پروندهٔ یک مشتری را دید. منطقِ خالص، commonMain (گوشیِ نسخهٔ ۳، کامپیوتر، آیفون).
 *
 *  دو منبع، یک فهرست:
 *   • **دستگاه** (بی‌اینترنت هم): هر دیدنِ پرونده روی همین دستگاه یک ردیفِ `auditLog` ِ دفتر با `type = customer_view`
 *     ([TYPE]) است و با همان همگام‌سازیِ امروز به سرور و دستگاه‌های دیگرِ دکان می‌رسد (`data/CustomerAccessLog.kt`).
 *     همان بیننده + مشتری + نوع در [WINDOW_MS] ⇐ ردیفِ تازه نیست ([isRepeat]) — سابقه فقط‌افزودنی است و ردیف ویرایش نمی‌شود.
 *   • **سرور**: خواندنِ پرونده از درِ سرور (`GET /api/debtors/:id`، سفارش‌های یک مشتری) با شمار ([parse] ِ پاسخِ [path])،
 *     و دیدنی که نقشِ بی `auditLog` (حسابدار) از دفترِ خودش به سرور می‌گوید ([noteBody] ⇐ `POST` ِ [PATH]، درِ `app`).
 *  ⛔ بی دادهٔ شخصیِ مشتری: فقط شناسهٔ پرونده، بیننده (شناسه، نام، نقش)، دستگاه، نوع و زمان.
 *  ⛔ دیدنِ فهرست فقط صاحب و `audit.view` (همان «سابقهٔ عملیات»)؛ سرور هم همان را می‌خواهد.
 */
object CustomerAccess {

  const val PATH = "/shop/customer-access"
  /** `auditLog.type` ِ دیدنِ پرونده روی دستگاه */
  const val TYPE = "customer_view"
  /** `refId` ِ خروجیِ فهرستِ همهٔ مشتریان (گزارش) — در پروندهٔ هر مشتری هم دیده می‌شود */
  const val ALL = "*"
  /** همان پنجرهٔ سرور (`lib/customer-access.js` ‹`WINDOW_MS`›) */
  const val WINDOW_MS = 10 * 60 * 1000L
  /** بیشینهٔ ردیفِ نشان‌داده در پرونده */
  const val SHOWN = 50

  /** برگه‌های گزارش که نام/شمارهٔ مشتریان را دارند (`ReportCsv.of`) ⇐ خروجیِ آن‌ها `export` ِ [ALL] */
  val CUSTOMER_SHEETS: Set<String> = setOf("debtors", "aging", "rfm", "duplicates", "reminders")

  enum class Kind(val key: String) {
    /** پرونده باز شد (نام، مانده، تاریخچه؛ شماره پوشیده یا نیست) */
    DETAIL("detail"),
    /** شماره یا نشانیِ کامل دیده یا به‌کار رفت (پروندهٔ با `customers.contact`، زنگ، پیامک، واتس‌اپ) */
    PHONE("phone"),
    /** خروجی: صورت‌حساب (CSV/اکسل)، چاپ، تصویرِ صورت‌حساب، یا برگهٔ همهٔ مشتریان */
    EXPORT("export"),
    ;

    companion object {
      fun of(key: String?): Kind? = entries.firstOrNull { it.key == key }
    }
  }

  /** یک ردیف — از دفترِ دستگاه (`server = false`، شمار ۱) یا از سرور (شمارِ دیدن‌ها در پنجره) */
  data class View(
    val id: String,
    val customerId: String,
    val viewerId: String,
    val viewerName: String,
    val role: String,
    val device: String,
    val kind: Kind,
    val at: Long,
    val count: Int = 1,
    val server: Boolean = false,
  )

  private val ID = Regex("^[A-Za-z0-9_.:-]{1,80}$")

  /** شناسه‌ای که می‌شود پرسید (همان `refOf` ِ سرور)؛ وگرنه null ⇒ هیچ پرسشی */
  fun path(customerId: String): String? =
    customerId.takeIf { ID.matches(it) && !it.startsWith("erased:") && !it.startsWith("ph:") }?.let { "$PATH?customerId=$it" }

  /**
   *  ردیفِ تازهٔ دستگاه لازم نیست؟ همان بیننده، همان مشتری، همان نوع، در [WINDOW_MS] ِ پیش از [now].
   *  ساعتِ عقب‌رفته (ردیفِ «آینده») هم تکرار است — تا ساعتِ دستکاری‌شده سابقه را پُر نکند.
   */
  fun isRepeat(existing: List<View>, viewerId: String, customerId: String, kind: Kind, now: Long): Boolean =
    existing.any { it.viewerId == viewerId && it.customerId == customerId && it.kind == kind && now - it.at < WINDOW_MS }

  /**
   *  بدنهٔ `POST` ِ [PATH] (درِ `app` ِ سرور، مهاجرتِ ۶۱۰): دیدنِ پرونده روی دستگاهی که `auditLog` نمی‌نویسد (حسابدار،
   *  `PermCore.canWrite`). بیننده، نقش و دستگاه را سرور از توکن می‌گیرد و `phone` ِ بی `customers.contact` را `detail`
   *  می‌کند. شناسهٔ ناامن ⇒ null (هیچ درخواستی)؛ [ALL] فقط با [Kind.EXPORT].
   */
  fun noteBody(customerId: String, kind: Kind): JsonObject? {
    val id = customerId.trim()
    val ok = if (id == ALL) kind == Kind.EXPORT else path(id) != null
    return if (!ok) null else JsonObject(mapOf("customerId" to JsonPrimitive(id), "kind" to JsonPrimitive(kind.key)))
  }

  private fun str(o: JsonObject, k: String): String = (o[k] as? JsonPrimitive)?.takeIf { it.isString }?.contentOrNull.orEmpty()
  private fun long(o: JsonObject, k: String): Long? = (o[k] as? JsonPrimitive)?.takeIf { !it.isString }?.longOrNull

  /**
   *  پاسخِ `GET /shop/customer-access` ⇐ ردیف‌های سرور؛ ردیفِ بدشکل یا نوعِ ناشناخته کنار می‌رود. `all: true` (خروجیِ برگهٔ
   *  همهٔ مشتریان) ⇐ مشتریِ [ALL]، همان ردیفِ دستگاه.
   */
  fun parse(o: JsonObject, customerId: String): List<View> =
    (o["entries"] as? JsonArray).orEmpty().mapNotNull { e ->
      val r = e as? JsonObject ?: return@mapNotNull null
      val kind = Kind.of(str(r, "kind")) ?: return@mapNotNull null
      val id = str(r, "id").ifEmpty { return@mapNotNull null }
      val all = (r["all"] as? JsonPrimitive)?.takeIf { !it.isString }?.contentOrNull == "true"
      View(
        id = id, customerId = if (all) ALL else customerId, viewerId = str(r, "viewerId"), viewerName = str(r, "viewerName"),
        role = str(r, "role"), device = str(r, "device"), kind = kind,
        at = long(r, "lastAt") ?: long(r, "firstAt") ?: 0L, count = (long(r, "count") ?: 1L).coerceIn(1L, 1_000_000L).toInt(),
        server = true,
      )
    }

  /**
   *  فهرستِ پرونده: ردیف‌های دستگاه برای همین مشتری و خروجیِ همهٔ مشتریان ([ALL])، با ردیف‌های سرور؛ تکراری
   *  (یک شناسه) یک بار، تازه‌ترین اول، حداکثر [SHOWN].
   */
  fun rows(local: List<View>, server: List<View>, customerId: String): List<View> =
    (server + local.filter { it.customerId == customerId || it.customerId == ALL })
      .distinctBy { (if (it.server) "s:" else "d:") + it.id }
      .sortedWith(compareByDescending<View> { it.at }.thenBy { it.id })
      .take(SHOWN)
}
