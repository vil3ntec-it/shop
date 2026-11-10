package ir.vil3ntec.tohid.core.promo

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put

/**
 *  PRM-02 — آزادِ دستیِ کدِ نگه‌داشته به دستِ صاحب/مدیر.
 *
 *  صندوقداری که فاکتور را با «درخواستِ تأیید» لغو کرد اجازهٔ آزاد کردن ندارد (`release_not_allowed`،
 *  `PromoRedeem.releaseDecision` ⇒ DROP) و ظرفیتِ کد تا ابد گرفته می‌ماند. صاحب/مدیر فهرستِ کدهای نگه‌داشته را
 *  از `GET /api/shop/promos/redemptions` می‌گیرد (فقط دارندهٔ `sales.cancel`؛ پشتِ پرچمِ `shop.promo_uses`) و هر فاکتورِ
 *  لغوشده را با همان `POST /redeem/release` ِ موجود آزاد می‌کند.
 *
 *  ⛔ آزاد کردن فقط برای فاکتوری که در دفترِ همین دستگاه **لغوشده** است — همان قاعدهٔ `PromoLive.releaseOnVoid`
 *     («آزاد کردن فقط پس از نشستنِ لغو در دفتر»). فاکتورِ زنده کد را درست مصرف کرده؛ فاکتوری که این‌جا نیست شاید
 *     هنوز از دستگاهِ دیگر نرسیده — هیچ‌کدام دکمه ندارند.
 *  ⛔ خواندنِ نرم: سرورِ کهنه (۴۰۴)، پرچمِ خاموش و پاسخِ بدشکل ⇒ هیچ رابطی، نه خطا.
 *
 *  منطقِ خالص (بی `java.*`/`android.*`، §۴۰.۳).
 */
object PromoHeld {

  const val PATH = "/shop/promos/redemptions"
  const val RELEASE_PATH = "/shop/promos/redeem/release"

  /** همان `SALE_RE` ِ سرور (`routes/shop-promos.js`) — شناسهٔ دیگری هرگز به آزاد کردن نمی‌رود */
  private val SALE_RE = Regex("^[A-Za-z0-9_-]{1,64}$")

  data class Row(val saleId: String, val code: String, val promoId: String, val at: Long)

  data class Page(val rows: List<Row>, val more: Boolean)

  private fun JsonElement?.str(): String? = (this as? JsonPrimitive)?.takeIf { it.isString && it.content.isNotBlank() }?.content
  private fun JsonElement?.num(): Long? = (this as? JsonPrimitive)?.takeUnless { it.isString }?.longOrNull

  /** پاسخِ فهرست؛ ردیفِ بی شناسهٔ درستِ فاکتور کنار می‌رود */
  fun parse(o: JsonObject?): Page {
    val arr = o?.get("held") as? JsonArray ?: return Page(emptyList(), false)
    val rows = arr.mapNotNull { e ->
      val r = e as? JsonObject ?: return@mapNotNull null
      val sale = r["saleId"].str()?.takeIf { SALE_RE.matches(it) } ?: return@mapNotNull null
      Row(sale, r["code"].str().orEmpty(), r["promoId"].str().orEmpty(), r["at"].num() ?: 0L)
    }
    val more = (o?.get("more") as? JsonPrimitive)?.takeUnless { it.isString }?.booleanOrNull == true
    return Page(rows, more)
  }

  /** فاکتور در دفترِ همین دستگاه */
  enum class Sale {
    /** لغوشده ⇒ آزاد کردنی */
    CANCELLED,
    /** زنده ⇒ کد درست مصرف شده */
    ACTIVE,
    /** این‌جا نیست (هنوز نرسیده، یا سبدی که ثبت نشد) */
    MISSING,
  }

  /** همهٔ کدهای نگه‌داشتهٔ یک فاکتور */
  data class Group(val saleId: String, val codes: List<String>, val at: Long, val sale: Sale) {
    val releasable: Boolean get() = sale == Sale.CANCELLED
  }

  /**
   *  ردیف‌ها ⇒ فاکتورها: لغوشده اول (کارِ صاحب)، سپس زنده، سپس نارسیده؛ در هر دسته تازه‌ترین اول.
   *  @param statusOf `Sale.status` ِ فاکتور در دفترِ همین دستگاه، یا null اگر نیست
   */
  fun groups(rows: List<Row>, statusOf: (String) -> String?): List<Group> {
    val bySale = LinkedHashMap<String, MutableList<Row>>()
    for (r in rows) bySale.getOrPut(r.saleId) { ArrayList() }.add(r)
    return bySale.map { (id, rs) ->
      val st = statusOf(id)
      Group(
        saleId = id,
        codes = rs.map { it.code }.filter { it.isNotBlank() }.distinct(),
        at = rs.maxOf { it.at },
        sale = when {
          st == null -> Sale.MISSING
          st == "cancelled" -> Sale.CANCELLED
          else -> Sale.ACTIVE
        },
      )
    }.sortedWith(compareBy<Group> { it.sale.ordinal }.thenByDescending { it.at }.thenBy { it.saleId })
  }

  /** راه به صفحه فقط با شمارشِ روشن و اجازهٔ لغوِ فاکتور (همان `mayCancel` ِ سرور) */
  fun shown(redeemOn: Boolean, canCancel: Boolean): Boolean = redeemOn && canCancel

  /** بدنهٔ `POST /redeem/release` — بی `codes` ⇒ همهٔ کدهای همان فاکتور (همان `PromoRepository.release`) */
  fun releaseBody(saleId: String): JsonObject {
    require(SALE_RE.matches(saleId)) { "bad sale id" }
    return buildJsonObject { put("saleId", saleId) }
  }

  /** `released` ِ پاسخِ آزاد کردن */
  fun releasedOf(o: JsonObject?): Int = (o?.get("released")).num()?.toInt()?.coerceAtLeast(0) ?: 0

  enum class Access {
    /** پرچمِ خاموش یا سرورِ کهنه ⇒ «روشن نیست» */
    OFF,
    /** اجازهٔ لغوِ فاکتور ندارد ⇒ «فقط صاحب یا مدیر» */
    DENIED,
  }

  /** پاسخِ ناموفقِ فهرست ⇒ حالِ صفحه؛ null ⇒ گذرا (اینترنت/سرور) و «دوباره» */
  fun access(status: Int, code: String): Access? = when {
    status == 403 && code == "release_not_allowed" -> Access.DENIED
    status == 403 || status == 404 -> Access.OFF
    else -> null
  }

  enum class Done {
    /** کد(ها) آزاد شد و ظرفیت برگشت */
    RELEASED,
    /** چیزی نگه‌داشته نبود (پیش‌تر آزاد شده) */
    NOTHING,
    /** شمارش در سرور خاموش شد */
    OFF,
    /** اجازه نیست یا ورودی را نپذیرفت */
    DENIED,
    /** پاسخی نیامد ⇒ دوباره */
    FAILED,
  }

  /** پاسخِ `POST /redeem/release` ⇒ پیامِ صفحه — همان مرزهای `PromoRedeem.releaseDecision` */
  fun releaseOutcome(ok: Boolean, released: Int, status: Int, code: String): Done = when {
    ok -> if (released > 0) Done.RELEASED else Done.NOTHING
    status == 403 && code == "promo_uses_disabled" -> Done.OFF
    status == 403 || status == 400 || status == 404 -> Done.DENIED
    else -> Done.FAILED
  }
}
