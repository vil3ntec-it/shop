package ir.vil3ntec.tohid.core.branch

import ir.vil3ntec.tohid.core.calendar.CivilDays
import ir.vil3ntec.tohid.core.report.BranchesReport
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull

/**
 *  شعبه درونِ یک دکان — فازِ ۲: گزارشِ هر شعبه و «جمعِ همه» از `GET /api/shop/branches/report` (`server/src/lib/branch-report.js`).
 *
 *  ⛔ هیچ عددی در برنامه حساب نمی‌شود: خلاصهٔ هر شعبه و جمع همان پاسخِ سرورند (`BranchesReport.Summary`)؛ عددِ نیامده null.
 *  ⛔ «جمعِ همه» ([View.totals]) فقط وقتی سرور داده — یعنی فقط صاحب و فقط بی شعبهٔ خواسته؛ کارمند فقط شعبه‌های خودش را می‌گیرد.
 */
object BranchTally {

  const val PATH = "${BranchBook.PATH}/report"

  /** امروز، ۷ و ۳۱ روز (سقفِ سرور) */
  val SPANS: List<Int> = BranchesReport.SPANS

  /** نشانیِ بازهٔ [days] روزِ تا [todayIso]؛ یک روز ⇒ بی بازه («امروزِ» دکان) */
  fun path(days: Int, todayIso: String): String {
    val d = days.coerceIn(1, BranchesReport.MAX_DAYS)
    if (d == 1) return PATH
    val from = CivilDays.addDays(todayIso, -(d - 1)) ?: return PATH
    return "$PATH?from=$from&to=$todayIso"
  }

  data class Row(val id: String, val name: String, val main: Boolean, val archived: Boolean, val summary: BranchesReport.Summary)

  data class View(
    val from: String?,
    val to: String?,
    val timezone: String,
    val rows: List<Row>,
    /** فقط صاحب */
    val totals: BranchesReport.Summary?,
  )

  private fun JsonElement?.text(): String? = (this as? JsonPrimitive)?.takeIf { it.isString }?.content
  private fun JsonElement?.flag(): Boolean = (this as? JsonPrimitive)?.takeUnless { it.isString }?.booleanOrNull == true

  /** پاسخِ سرور ⇐ صفحه؛ بی `enabled: true` ⇒ null (روشن نیست) */
  fun of(o: JsonObject): View? {
    if (!o["enabled"].flag()) return null
    val rows = (o["branches"] as? JsonArray).orEmpty().mapNotNull { e ->
      val b = e as? JsonObject ?: return@mapNotNull null
      val id = BranchKey.idOf(b["id"].text()) ?: return@mapNotNull null
      Row(id, b["name"].text()?.trim().orEmpty(), BranchKey.isMain(id), b["status"].text() == "archived", BranchesReport.summaryOf(b["summary"] as? JsonObject))
    }
    return View(
      from = o["from"].text(),
      to = o["to"].text(),
      timezone = o["timezone"].text().orEmpty(),
      rows = rows,
      totals = (o["totals"] as? JsonObject)?.let(BranchesReport::summaryOf),
    )
  }
}
