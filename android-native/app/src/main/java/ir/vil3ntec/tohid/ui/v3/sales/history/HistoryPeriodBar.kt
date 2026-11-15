package ir.vil3ntec.tohid.ui.v3.sales.history

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import ir.vil3ntec.tohid.core.i18n.I18n
import ir.vil3ntec.tohid.core.i18n.Str
import ir.vil3ntec.tohid.core.i18n.StrHistory
import ir.vil3ntec.tohid.core.i18n.StrKit
import ir.vil3ntec.tohid.core.i18n.StrLook
import ir.vil3ntec.tohid.data.SalesPeriods
import ir.vil3ntec.tohid.ui.v3.V3Chip
import ir.vil3ntec.tohid.ui.v3.V3ChipRow
import ir.vil3ntec.tohid.ui.v3.V3Choice
import ir.vil3ntec.tohid.ui.v3.V3Dropdown

/**
 *  دورهٔ تاریخچه — یک کادرِ کشویی (۶۳w، جای کلیدِ «روز | ماه | سال»): امروز، دیروز، این ماه و چند ماهِ پیشینِ دارای فروش،
 *  امسال و سال‌های پیشین. خالص و آزمون‌شده؛ کلید = «واحد:دوره» (دورهٔ خالی = دورهٔ جاری).
 */
internal object HistoryPeriods {
  const val RECENT_MONTHS = 6
  const val RECENT_YEARS = 3

  fun key(unit: SalesPeriods.Unit, period: String?): String = unit.name + ":" + period.orEmpty()

  fun selected(query: HistoryQuery): String = key(query.unit, query.picked)

  fun choices(book: HistoryBook): List<V3Choice> = buildList {
    add(V3Choice(key(SalesPeriods.Unit.DAY, null), Str.today))
    add(V3Choice(key(SalesPeriods.Unit.DAY, HistoryModel.yesterday(book.today)), StrKit.yesterday))
    add(V3Choice(key(SalesPeriods.Unit.MONTH, null), StrHistory.thisMonth, divider = true))
    book.older(SalesPeriods.Unit.MONTH).filter { it.key.isNotEmpty() }.take(RECENT_MONTHS).forEach {
      add(V3Choice(key(SalesPeriods.Unit.MONTH, it.key), PeriodLabel.full(it, book.gregorian)))
    }
    add(V3Choice(key(SalesPeriods.Unit.YEAR, null), StrHistory.thisYear, divider = true))
    book.older(SalesPeriods.Unit.YEAR).filter { it.key.isNotEmpty() }.take(RECENT_YEARS).forEach {
      add(V3Choice(key(SalesPeriods.Unit.YEAR, it.key), PeriodLabel.full(it, book.gregorian)))
    }
  }

  /** کلیدِ برگزیده ⇒ واحد و دورهٔ پرسش؛ بخش‌های باز و برگه‌های پیشین همان می‌مانند */
  fun apply(query: HistoryQuery, key: String) {
    val unit = SalesPeriods.Unit.entries.firstOrNull { it.name == key.substringBefore(':') } ?: return
    query.unit = unit
    query.picked = key.substringAfter(':', "").ifEmpty { null }
  }

  /** نوشتهٔ کادر: «امروز»، «دیروز»، «این ماه»، «میزان ۱۴۰۵»… */
  fun label(book: HistoryBook?, query: HistoryQuery): String {
    val k = selected(query)
    book?.let { b -> choices(b).firstOrNull { it.key == k }?.let { return it.label } }
    val p = query.picked
    if (book != null && p != null) return PeriodLabel.title(book.pick(query.unit, p), book.today, book.gregorian)
    return when (query.unit) {
      SalesPeriods.Unit.DAY -> Str.today
      SalesPeriods.Unit.MONTH -> StrHistory.thisMonth
      SalesPeriods.Unit.YEAR -> StrHistory.thisYear
    }
  }
}

/** کادرِ کشوییِ دوره — ۶۵s: در گوشهٔ کارتِ خلاصهٔ تاریخچه (`PeriodSummary`، مقابلِ پیکان)، نه بالای آن */
@Composable
internal fun PeriodDropdown(book: HistoryBook?, query: HistoryQuery) {
  val choices = remember(book, I18n.lang) { book?.let(HistoryPeriods::choices).orEmpty() }
  V3Dropdown(
    value = HistoryPeriods.label(book, query),
    choices = choices,
    selected = HistoryPeriods.selected(query),
    onSelect = { HistoryPeriods.apply(query, it) },
    label = StrLook.period,
  )
}

/** زیرِ عنوان فقط با فیلترِ روشن: تراشه‌های همان فیلتر و «پاک کردن» */
@Composable
internal fun FilterChipsBar(query: HistoryQuery, onFilter: () -> Unit) {
  V3ChipRow {
    if (query.filter != HistoryFilter.ALL) V3Chip(filterName(query.filter), true, onFilter, icon = Icons.Outlined.Tune)
    if (query.channel != HistoryChannel.ALL) V3Chip(channelName(query.channel), true, onFilter, icon = Icons.Outlined.Tune)
    V3Chip(StrHistory.clearFilters, false, { query.filter = HistoryFilter.ALL; query.channel = HistoryChannel.ALL }, icon = Icons.Outlined.Close)
  }
}
