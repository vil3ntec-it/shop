package ir.vil3ntec.tohid.core.report

import ir.vil3ntec.tohid.core.calendar.CivilDays
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.longOrNull

/**
 *  RPT-18 (§۶.۱۰، فاز ۶) — «گزارشِ همهٔ شعبه‌ها» در برنامهٔ مدیریت: خواندنِ پاسخِ `GET /api/shop/branches-report`
 *  (`server/src/lib/branches-report.js`). منطقِ خالص، commonMain (گوشی، کامپیوتر و آیفون).
 *
 *  ⛔ هیچ عددی این‌جا حساب نمی‌شود: خلاصهٔ هر شعبه و «جمعِ همه» همان پاسخِ سرورند (به پول، واحدِ کوچک)؛ عددِ
 *     نیامده یا بدشکل `null` می‌ماند («—»)، نه صفر.
 *  ⛔ پرچمِ `shop.branches_report` (پیش‌فرض خاموش) ⇒ سرور فقط `{enabled:false}` ⇒ [Answer.Off] ⇒ نه راه، نه صفحه.
 *     مدیر و کارمند ⇒ `{enabled:true, owner:false}` ⇒ [Answer.NotOwner] ⇒ باز هم نه راه (فقط صاحب عدد می‌بیند).
 *  بازه همان قاعدهٔ سرور: روزِ محلیِ هر دکان (YYYY-MM-DD)، حداکثر [MAX_DAYS] روز؛ «امروز» یعنی بی بازه.
 */
object BranchesReport {

  const val PATH = "/shop/branches-report"
  const val MAX_DAYS = 31

  /** بازه‌های صفحه به روز: امروز (بی بازه، روزِ محلیِ هر شعبه)، ۷ و ۳۱ (سقفِ سرور) */
  val SPANS = listOf(1, 7, MAX_DAYS)

  /** کلیدهای پولیِ خلاصه به ترتیبِ صفحه (همان `SUM_KEYS` ِ سرور) */
  val SUM_KEYS = listOf("invoices", "sales", "discount", "returnCount", "returns", "netSales", "cashNet", "otherNet")

  /** شمارنده‌ها (نه پول) */
  val COUNT_KEYS = setOf("invoices", "returnCount")

  /**
   *  نشانیِ بازهٔ [days] روزِ تا [todayIso] (روزِ همین دستگاه). یک روز ⇒ بی بازه (سرور «امروزِ هر دکان» را می‌گیرد)؛
   *  بیرون از ۱..۳۱ به مرز می‌چسبد؛ تاریخِ نادرست ⇒ بی بازه.
   */
  fun path(days: Int, todayIso: String): String {
    val d = days.coerceIn(1, MAX_DAYS)
    if (d == 1) return PATH
    val from = CivilDays.addDays(todayIso, -(d - 1)) ?: return PATH
    return "$PATH?from=$from&to=$todayIso"
  }

  data class Pay(val method: String, val netSales: Long?)
  data class Top(val name: String, val netSales: Long?)

  data class Summary(
    /** کلید ⇐ عدد (به پول؛ `invoices`/`returnCount` شمار) */
    val values: Map<String, Long?>,
    val payments: List<Pay> = emptyList(),
    val top: List<Top> = emptyList(),
    val lowStock: Long? = null,
    /** ردیف‌ها از سقفِ یک گزارش بیشتر بود ⇒ عددها ناقص‌اند */
    val partial: Boolean = false,
  ) {
    operator fun get(key: String): Long? = values[key]
    /** هیچ فاکتور و برگشتی در بازه نیست */
    val empty: Boolean get() = (values["invoices"] ?: 0L) == 0L && (values["returnCount"] ?: 0L) == 0L
  }

  data class Branch(
    val shopId: String,
    val name: String,
    /** دکانِ همین نشست */
    val current: Boolean,
    val timezone: String,
    val from: String?,
    val to: String?,
    val summary: Summary,
  )

  data class View(
    val from: String?,
    val to: String?,
    val branches: List<Branch>,
    val totals: Summary,
    /** شعبه‌هایی که پرچمِ خودشان خاموش است (فقط شمرده) */
    val skipped: Int,
    /** بیش از سقفِ سرور (۲۰ شعبه) */
    val truncated: Boolean,
  )

  sealed interface Answer {
    /** پرچمِ دکانِ همین نشست خاموش */
    data object Off : Answer
    /** روشن، ولی کاربر صاحبِ این دکان نیست (مدیر/کارمند) */
    data object NotOwner : Answer
    data class Report(val view: View) : Answer
  }

  private fun JsonElement?.obj(): JsonObject? = this as? JsonObject
  private fun JsonElement?.long(): Long? = (this as? JsonPrimitive)?.takeUnless { it is JsonNull || it.isString }?.longOrNull
  private fun JsonElement?.text(): String? = (this as? JsonPrimitive)?.takeIf { it.isString }?.content
  private fun JsonElement?.flag(): Boolean = (this as? JsonPrimitive)?.takeUnless { it.isString }?.booleanOrNull == true

  /** همان خواندنِ خلاصه برای گزارشِ شعبه‌های درونِ یک دکان (`core/branch/BranchTally`) */
  internal fun summaryOf(o: JsonObject?): Summary = Summary(
    values = SUM_KEYS.associateWith { o?.get(it).long() },
    payments = (o?.get("payments") as? JsonArray).orEmpty().mapNotNull { e ->
      val p = e.obj() ?: return@mapNotNull null
      Pay(p["method"].text().orEmpty(), p["netSales"].long())
    },
    top = (o?.get("topProducts") as? JsonArray).orEmpty().mapNotNull { e ->
      val p = e.obj() ?: return@mapNotNull null
      val name = p["name"].text()?.trim().orEmpty()
      if (name.isEmpty()) null else Top(name, p["netSales"].long())
    },
    lowStock = o?.get("lowStock").long(),
    partial = o?.get("partial").flag(),
  )

  /** پاسخِ سرور ⇐ پاسخِ صفحه؛ `enabled` ِ نادرست یا بدشکل ⇒ [Answer.Off] (یعنی «روشن نیست»، هرگز عددِ ساختگی) */
  fun of(o: JsonObject): Answer {
    if (!o["enabled"].flag()) return Answer.Off
    if (!o["owner"].flag()) return Answer.NotOwner
    val shops = o["shops"] as? JsonArray ?: return Answer.Off
    val branches = shops.mapNotNull { e ->
      val s = e.obj() ?: return@mapNotNull null
      val id = s["shop_id"].text()?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
      Branch(
        shopId = id,
        name = s["name"].text()?.trim().orEmpty(),
        current = s["current"].flag(),
        timezone = s["timezone"].text().orEmpty(),
        from = s["from"].text(),
        to = s["to"].text(),
        summary = summaryOf(s["summary"].obj()),
      )
    }
    return Answer.Report(
      View(
        from = o["from"].text(),
        to = o["to"].text(),
        branches = branches,
        totals = summaryOf(o["totals"].obj()),
        skipped = o["skipped"].long()?.coerceIn(0, Int.MAX_VALUE.toLong())?.toInt() ?: 0,
        truncated = o["truncated"].flag(),
      ),
    )
  }

  /** سهمِ هر شعبه از «خالص» ِ همه به ده‌هزارم (گردِ نیمه به بالا)؛ جمعِ صفر یا عددِ نیامده ⇒ null */
  fun shareBp(branch: Branch, totals: Summary): Long? {
    val all = totals["netSales"] ?: return null
    val mine = branch.summary["netSales"] ?: return null
    if (all <= 0 || mine < 0) return null
    return (mine * 10_000 + all / 2) / all
  }
}
