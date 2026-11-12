package ir.vil3ntec.tohid.core.stock

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put

/*
 *  POS-05 / INV-03 — رزروِ سفارشِ اینترنتی در صندوق: «قابل‌فروش» ِ کاشی = موجودی − نگه‌داشته‌های همین صندوق − نگه‌داشتهٔ فعالِ
 *  سفارش‌های اینترنتی (O-06 = ب، پرچمِ خاموشِ سرورِ `shop.stock_reserve` با `shop.catalog`).
 *
 *  داور سرور است (`GET /api/shop/catalog/reserved`، `stock-reserve.posView`): هر قلمِ نگه‌داشته یک ردیف با کلیدِ فروشِ تحویلِ
 *  همان قلم (`o-<سفارش>-<قلم>` = `OrderFulfillment.itemIdOf`). این‌جا فقط خواندن و شمردن، بی هیچ نوشتنی در دفتر:
 *   • قلمی که فروشِ تحویلش همین حالا در دفترِ دستگاه هست ([reserved] ‹sold›) شمرده نمی‌شود — موجودیِ دستگاه همان را کم کرده و
 *     سرور با رسیدنِ همان فروش رزرو را می‌بندد؛ بی آن، تا همگام‌سازی (یا بی اینترنت) دو بار کم می‌شد.
 *   • مهلت با ساعتِ سرور: `expiresAt − now` ِ همان پاسخ ([Line.leftMs]) و گذشتِ زمان از لحظهٔ گرفتن روی همین دستگاه؛ ساعتِ
 *     دستگاه هیچ‌گاه با ساعتِ سرور مقایسه نمی‌شود. ساعتِ برگشته یا نمای کهنه‌تر از [MAX_AGE_MS] ⇒ هیچ (همان رفتارِ پیشین).
 *   • پرچمِ خاموش، سرورِ پیشین، نقشِ بی `orders.view` ⇒ هیچ ⇒ «قابل‌فروش» بایت‌به‌بایت همان امروز.
 *  فقط نمایش است: فروشِ حضوری هرگز با آن رد نمی‌شود (INV-19). آزمون: `OnlineHoldsTest` (commonTest).
 */
object OnlineHolds {

  /** نسبی به `/api` (همان `Backend.api`) */
  const val PATH = "/shop/catalog/reserved"
  /** همان `POS_MAX` ِ سرور */
  const val MAX_LINES = 2000
  /** صندوق هر چند وقت یک بار می‌پرسد (سقفِ سرور: ۳۰ در دقیقه برای هر کاربر) */
  const val REFRESH_MS = 60_000L
  /** سرور گفت «روشن نیست» (یا نقش/سرورِ پیشین) ⇒ پرسشِ بعد دیرتر (همان `StockTransferFlag.CHECK_MS`) */
  const val IDLE_MS = 10 * 60_000L
  /** بی اینترنتِ درازتر از این ⇒ نمای کهنه کنار (همان مهلتِ پشتیبانِ پس از تحویلِ سرور، `HANDOVER_GRACE_MS`) */
  const val MAX_AGE_MS = 24 * 60 * 60_000L
  /** ساعتِ دستگاه بیش از این عقب رفت ⇒ نمای پیشین نامعتبر */
  const val CLOCK_SLACK_MS = 5 * 60_000L

  /** یک قلمِ نگه‌داشته؛ [leftMs] مهلتِ مانده در لحظهٔ گرفتن (null ⇒ تا پایانِ سفارش، پس از قبول) */
  data class Line(val productId: String, val qtyMilli: Long, val saleItemId: String, val leftMs: Long?)

  /** نمای سرور؛ [fetchedAt] ساعتِ **همین دستگاه** در لحظهٔ گرفتن */
  data class Snapshot(val lines: List<Line>, val fetchedAt: Long)

  private fun JsonObject.s(k: String): String = (this[k] as? JsonPrimitive)?.takeIf { it.isString }?.contentOrNull.orEmpty()
  private fun JsonObject.l(k: String): Long? = (this[k] as? JsonPrimitive)?.takeIf { !it.isString }?.longOrNull

  /**
   *  پاسخِ سرور ⇒ نما، یا `null` وقتی سرور «روشن نیست» می‌گوید (`enabled` ≠ true). ردیفِ ناجور (بی کالا، بی کلید، مقدارِ نامثبت)
   *  کنار می‌رود؛ بیش از [MAX_LINES] بریده می‌شود.
   */
  fun parse(o: JsonObject, fetchedAt: Long): Snapshot? {
    if ((o["enabled"] as? JsonPrimitive)?.booleanOrNull != true) return null
    val serverNow = o.l("now") ?: return null
    val lines = (o["lines"] as? JsonArray).orEmpty().asSequence().mapNotNull { it as? JsonObject }.mapNotNull { r ->
      val pid = r.s("productId")
      val key = r.s("saleItemId")
      val q = r.l("qtyMilli") ?: return@mapNotNull null
      if (pid.isBlank() || key.isBlank() || q <= 0) return@mapNotNull null
      val exp = r["expiresAt"]
      val left = if (exp == null || exp is JsonNull) null else ((r.l("expiresAt") ?: return@mapNotNull null) - serverNow).coerceAtLeast(0)
      Line(pid, q, key, left)
    }.take(MAX_LINES).toList()
    return Snapshot(lines, fetchedAt)
  }

  /**
   *  مقدارِ رزروشدهٔ هر کالا (واحدِ موجودی، همان `ShopStore.stock`) در لحظهٔ [now] ِ همین دستگاه. [sold]: آیا فروشِ تحویلِ این
   *  کلید در دفترِ دستگاه هست. بی نما، نمای کهنه یا ساعتِ برگشته ⇒ خالی.
   */
  fun reserved(s: Snapshot?, now: Long, sold: (String) -> Boolean): Map<String, Double> {
    if (s == null || s.lines.isEmpty()) return emptyMap()
    val elapsed = now - s.fetchedAt
    if (elapsed < -CLOCK_SLACK_MS || elapsed > MAX_AGE_MS) return emptyMap()
    val gone = elapsed.coerceAtLeast(0)
    val milli = LinkedHashMap<String, Long>()
    for (l in s.lines) {
      if (l.leftMs != null && l.leftMs <= gone) continue
      if (sold(l.saleItemId)) continue
      milli[l.productId] = (milli[l.productId] ?: 0L) + l.qtyMilli
    }
    return milli.mapValues { it.value / 1000.0 }
  }

  /** وقتِ پرسشِ دوباره؟ [last] پرسشِ پیشین (null ⇒ هرگز)، [on] آخرین نما هست؛ ساعتِ برگشته ⇒ همین حالا */
  fun due(last: Long?, now: Long, on: Boolean): Boolean =
    last == null || now < last || now - last >= (if (on) REFRESH_MS else IDLE_MS)

  /** جمعِ دو نقشهٔ «کنار رفته» (نگه‌داشته‌های صندوق + رزروِ اینترنتی) — ورودیِ `HeldStock.sellable` */
  fun merge(a: Map<String, Double>, b: Map<String, Double>): Map<String, Double> {
    if (b.isEmpty()) return a
    if (a.isEmpty()) return b
    val out = LinkedHashMap(a)
    for ((k, v) in b) out[k] = (out[k] ?: 0.0) + v
    return out
  }

  /* ------------------------------------------------------------ نگه‌داشتن روی دستگاه (بی اینترنت) */

  fun encode(s: Snapshot): String = buildJsonObject {
    put("v", 1)
    put("at", s.fetchedAt)
    put("lines", buildJsonArray {
      for (l in s.lines) add(buildJsonObject {
        put("p", l.productId); put("q", l.qtyMilli); put("k", l.saleItemId)
        if (l.leftMs != null) put("left", l.leftMs)
      })
    })
  }.toString()

  /** خراب یا نسخهٔ ناشناخته ⇒ `null` (نمای پیشین نیست؛ دورِ بعد از سرور) */
  fun decode(raw: String?): Snapshot? {
    if (raw.isNullOrBlank()) return null
    val o = runCatching { kotlinx.serialization.json.Json.parseToJsonElement(raw) as? JsonObject }.getOrNull() ?: return null
    if (o.l("v") != 1L) return null
    val at = o.l("at") ?: return null
    val lines = (o["lines"] as? JsonArray).orEmpty().mapNotNull { e ->
      val r = e as? JsonObject ?: return@mapNotNull null
      val q = r.l("q") ?: return@mapNotNull null
      Line(r.s("p"), q, r.s("k"), r.l("left")).takeIf { it.productId.isNotBlank() && it.saleItemId.isNotBlank() && q > 0 }
    }
    return Snapshot(lines.take(MAX_LINES), at)
  }
}
