package ir.vil3ntec.tohid.core.report

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.longOrNull

/**
 *  RPT-14 — «گزارشِ فروشِ اینترنتی» در برنامهٔ مدیریت: خواندنِ پاسخِ `GET /api/shop/orders/online-report`
 *  (`server/src/lib/order-online-report.js`). منطقِ خالص، commonMain (گوشی، کامپیوتر و آیفون).
 *
 *  ⛔ هیچ عددی این‌جا حساب نمی‌شود: قیف، نرخ (ده‌هزارم)، زمان‌ها (میانه و صدکِ ۹۰) و جمعِ پول همان پاسخِ سرورند؛
 *  عددِ نیامده یا بدشکل `null` می‌ماند («—»)، نه صفر. بالای قیف ([Top]: بازدید ⇐ دیدنِ کالا ⇐ سبد، فقط ویترینِ وب،
 *  `lib/storefront-funnel.js`) تا سرور روزی را نشمرده `null` است.
 *  ⛔ پرچمِ خاموشِ `shop.orders.online_report` ⇒ سرور ۴۰۳ [OFF] ⇒ صفحه و راهش پنهان (پیش‌فرض همان امروز).
 *  بازه همان قاعدهٔ سرور: میلی‌ثانیه، حداکثر ۹۲ روز.
 */
object OnlineReport {

  const val PATH = "/shop/orders/online-report"
  const val OFF = "online_report_disabled"
  const val DAY_MS = 86_400_000L
  const val MAX_DAYS = 92

  /** بازه‌های صفحه به روز: ۲۴ ساعت (پیش‌فرضِ سرور)، ۷، ۳۰ و ۹۲ (سقفِ سرور) */
  val SPANS = listOf(1, 7, 30, MAX_DAYS)

  /** پنج گامِ قیف به ترتیب (کلیدهای `funnel` ِ پاسخ) */
  val STEPS = listOf("placed", "accepted", "packed", "handedOver", "completed")

  /** دلیل‌های از دست رفتن به ترتیبِ سرور (`LOST`)، و «ناموفقِ تحویل» جدا */
  val LOST = listOf("rejected", "auto_rejected", "cancelled_by_customer", "cancelled", "returned_to_store")
  const val DELIVERY_FAILED = "deliveryFailed"

  /** چهار زمان (کلیدهای `times`) */
  val TIMES = listOf("accept", "prep", "delivery", "total")

  /** نشانیِ بازهٔ [days] روزِ تا [nowMs]؛ بیرون از ۱..۹۲ به مرز می‌چسبد */
  fun path(days: Int, nowMs: Long): String {
    val d = days.coerceIn(1, MAX_DAYS)
    return "$PATH?from=${nowMs - d * DAY_MS}&to=$nowMs"
  }

  data class Time(val n: Int, val medianMs: Long?, val p90Ms: Long?)
  data class Miss(val q: String, val n: Int)

  /** بالای قیفِ ویترینِ وب (`top` ِ پاسخ): بازدیدکنندهٔ یکتای هر روز؛ نسبت‌ها از بازدید به ده‌هزارم، همان سرور */
  data class Top(
    val visits: Int?,
    val productViews: Int?,
    val cartAdds: Int?,
    val viewBp: Long?,
    val cartBp: Long?,
    /** آغازِ نخستین روزِ شمرده‌شده (ms) */
    val since: Long?,
    /** روزی از بازه به سقفِ روزانهٔ سرور خورد ⇒ عدد کمتر از واقعی است */
    val capped: Boolean,
  )

  data class View(
    val from: Long?,
    val to: Long?,
    /** گام ⇐ شمار؛ همیشه هر پنج گام (نیامده ⇒ ۰ نیست، null) */
    val funnel: Map<String, Int?>,
    val lost: Map<String, Int?>,
    val acceptBp: Long?,
    val completeBp: Long?,
    val lostBp: Long?,
    val deliveryFailBp: Long?,
    val times: Map<String, Time>,
    val completedTotalMinor: Long?,
    val currency: String,
    val misses: List<Miss>,
    val visits: Long?,
    val top: Top? = null,
  ) {
    /** هیچ سفارشی در بازه نیست */
    val empty: Boolean get() = (funnel["placed"] ?: 0) == 0
  }

  private fun JsonElement?.obj(): JsonObject? = this as? JsonObject
  private fun JsonElement?.long(): Long? = (this as? JsonPrimitive)?.takeUnless { it is JsonNull || it.isString }?.longOrNull
  private fun JsonElement?.count(): Int? = long()?.takeIf { it in 0..Int.MAX_VALUE }?.toInt()

  /** پاسخِ سرور ⇐ نما؛ `enabled` ِ نادرست یا پاسخِ بی قیف ⇒ null (یعنی «این گزارش برای این دکان روشن نیست») */
  fun of(o: JsonObject): View? {
    val enabled = o["enabled"] as? JsonPrimitive
    if (enabled == null || enabled.isString || enabled.booleanOrNull != true) return null
    val funnel = o["funnel"].obj() ?: return null
    val lost = o["lost"].obj()
    val rates = o["rates"].obj()
    val times = o["times"].obj()
    val window = o["window"].obj()
    return View(
      from = window?.get("from").long(),
      to = window?.get("to").long(),
      funnel = STEPS.associateWith { funnel[it].count() },
      lost = (LOST + DELIVERY_FAILED).associateWith { lost?.get(it).count() },
      acceptBp = rates?.get("acceptBp").long(),
      completeBp = rates?.get("completeBp").long(),
      lostBp = rates?.get("lostBp").long(),
      deliveryFailBp = rates?.get("deliveryFailBp").long(),
      times = TIMES.associateWith { k ->
        val t = times?.get(k).obj()
        Time(t?.get("n").count() ?: 0, t?.get("medianMs").long(), t?.get("p90Ms").long())
      },
      completedTotalMinor = o["completedTotalMinor"].long(),
      currency = (o["currency"] as? JsonPrimitive)?.contentOrNull ?: "AFN",
      misses = (o["zeroResultSearches"] as? JsonArray).orEmpty().mapNotNull { e ->
        val m = e.obj() ?: return@mapNotNull null
        val q = (m["q"] as? JsonPrimitive)?.takeIf { it.isString }?.content?.trim().orEmpty()
        val n = m["n"].count() ?: return@mapNotNull null
        if (q.isEmpty()) null else Miss(q, n)
      },
      visits = o["visits"].long(),
      top = o["top"].obj()?.let { t ->
        Top(
          visits = t["visits"].count(),
          productViews = t["productViews"].count(),
          cartAdds = t["cartAdds"].count(),
          viewBp = t["viewBp"].long(),
          cartBp = t["cartBp"].long(),
          since = t["since"].long(),
          capped = (t["capped"] as? JsonPrimitive)?.takeUnless { it.isString }?.booleanOrNull == true,
        )
      },
    )
  }

  /** نسبتِ گامِ [step] به «ثبت» به ده‌هزارم (گردِ نیمه به بالا، همان `bp` ِ سرور)؛ بی ثبت ⇒ null */
  fun stepBp(v: View, step: String): Long? {
    val placed = v.funnel["placed"] ?: return null
    val n = v.funnel[step] ?: return null
    if (placed <= 0) return null
    return (n.toLong() * 10_000 + placed / 2) / placed
  }

  /** میلی‌ثانیه ⇐ (ساعت، دقیقه) با گردِ دقیقه به نزدیک‌ترین؛ null ⇒ null */
  fun hoursMinutes(ms: Long?): Pair<Long, Long>? {
    if (ms == null || ms < 0) return null
    val minutes = (ms + 30_000) / 60_000
    return minutes / 60 to minutes % 60
  }
}
