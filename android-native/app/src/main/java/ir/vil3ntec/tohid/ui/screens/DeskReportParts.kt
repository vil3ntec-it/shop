package ir.vil3ntec.tohid.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.TrendingUp
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material.icons.outlined.Print
import androidx.compose.material.icons.outlined.TableChart
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.core.i18n.StrDesk
import ir.vil3ntec.tohid.core.i18n.StrDeskData
import ir.vil3ntec.tohid.core.i18n.StrLook
import ir.vil3ntec.tohid.core.i18n.StrReportPages
import ir.vil3ntec.tohid.core.i18n.StrReports
import ir.vil3ntec.tohid.core.keymap.KeyMap
import ir.vil3ntec.tohid.core.report.ReportBoard
import ir.vil3ntec.tohid.core.report.ReportCore
import ir.vil3ntec.tohid.core.report.ReportTables
import ir.vil3ntec.tohid.data.ShopData
import ir.vil3ntec.tohid.formatDate
import ir.vil3ntec.tohid.money
import ir.vil3ntec.tohid.ui.components.common.ViewMode
import ir.vil3ntec.tohid.ui.components.common.ViewModeState
import ir.vil3ntec.tohid.ui.desk.Desk
import ir.vil3ntec.tohid.ui.desk.DeskBarChart
import ir.vil3ntec.tohid.ui.desk.DeskButton
import ir.vil3ntec.tohid.ui.desk.DeskButtonKind
import ir.vil3ntec.tohid.ui.desk.DeskCard
import ir.vil3ntec.tohid.ui.desk.DeskDivider
import ir.vil3ntec.tohid.ui.desk.DeskDropdown
import ir.vil3ntec.tohid.ui.desk.DeskHues
import ir.vil3ntec.tohid.ui.desk.DeskNote
import ir.vil3ntec.tohid.ui.desk.DeskOption
import ir.vil3ntec.tohid.ui.desk.DeskRadius
import ir.vil3ntec.tohid.ui.desk.DeskSegmented
import ir.vil3ntec.tohid.ui.desk.DeskShareBars
import ir.vil3ntec.tohid.ui.desk.DeskSize
import ir.vil3ntec.tohid.ui.desk.DeskSpace
import ir.vil3ntec.tohid.ui.desk.DeskToolbar
import ir.vil3ntec.tohid.ui.desk.DeskType
import ir.vil3ntec.tohid.ui.desk.deskCard
import ir.vil3ntec.tohid.ui.desk.deskClickable
import ir.vil3ntec.tohid.ui.v3.RowWhen
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive

/*
 *  تکه‌های «گزارش‌ها» ِ کامپیوتر (`claude/e-desk-d3b`): نوارِ ابزارِ یک‌خطی با کادرهای کشویی، نمودارِ روند و سهمِ روش‌ها، و
 *  جدولِ آرامِ برگه (`DeskFreshTable`) — همه فقط روی کامپیوتر؛ گوشی همان `FreshTable` ِ پیشین.
 */

/** نوارِ ابزارِ «گزارشِ تازه»: گزارش، روز، دوره و تفکیک (کادرِ کشویی) | کلیدِ «گزارشِ تازه»، نما، چاپ، PDF و اکسل */
@Composable
internal fun DeskReportToolbar(
  tabs: List<Pair<String, String>>, tab: String, onTab: (String) -> Unit,
  days: List<Pair<String, String>>?, day: String, onDay: (String) -> Unit,
  spans: List<Pair<String, String>>?, span: String, onSpan: (String) -> Unit,
  dims: List<Pair<String, String>>?, dim: String, onDim: (String) -> Unit,
  view: ViewModeState,
  onPrint: () -> Unit, onPdf: () -> Unit, onExcel: () -> Unit,
) {
  DeskToolbar(
    end = {
      if (view.toggleVisible) DeskSegmented(listOf(ViewMode.TABLE to ir.vil3ntec.tohid.core.i18n.Str.tableView, ViewMode.CARD to ir.vil3ntec.tohid.core.i18n.Str.cardView), view.mode, { view.set(it) })
      DeskButton(StrReports.printShort, onPrint, icon = Icons.Outlined.Print, shortcut = KeyMap.GLOBAL_PRINT.hint)
      DeskButton("PDF", onPdf, icon = Icons.Outlined.PictureAsPdf)
      DeskButton(StrReports.excel, onExcel, kind = DeskButtonKind.PRIMARY, icon = Icons.Outlined.TableChart, shortcut = KeyMap.GLOBAL_EXPORT.hint)
    },
  ) {
    DeskDropdown(tab, tabs.map { DeskOption(it.first, it.second) }, onTab, label = StrReports.reportType)
    if (days != null) DeskDropdown(day, days.map { DeskOption(it.first, it.second) }, onDay, label = StrDeskData.dayLabel)
    if (spans != null) DeskDropdown(span, spans.map { DeskOption(it.first, it.second) }, onSpan, label = StrLook.period)
    if (dims != null) DeskDropdown(dim, dims.map { DeskOption(it.first, it.second) }, onDim, label = StrDeskData.breakdown)
  }
}

/** روندِ فروشِ خالصِ بازه برای نمودار — یک روز ⇒ ساعت‌به‌ساعت، تا دو ماه ⇒ روزانه، بیشتر ⇒ ماهانه (کهنه‌ترین اول) */
internal object DeskReportTrend {
  /** برچسبِ نمودار برای روند */
  fun title(trend: List<Pair<String, Double>>, from: String, to: String): String = when {
    from == to -> StrDeskData.salesByHour
    days(from, to) > 62 -> StrDeskData.salesByMonth
    else -> StrDesk.salesChart
  }

  private fun days(from: String, to: String): Long {
    val a = ir.vil3ntec.tohid.core.calendar.CivilDays.jdnOfIso(from) ?: return 0
    val b = ir.vil3ntec.tohid.core.calendar.CivilDays.jdnOfIso(to) ?: return 0
    return (b - a).toLong()
  }

  fun of(d: ShopData, s: ReportCore.SalesReport, from: String, to: String, today: String): List<Pair<String, Double>> {
    if (from == to) return s.hour.sortedBy { it.key }.map { it.key to it.netSales / 100.0 }
    val live = d.sales.filter { it.status != "cancelled" && it.date in from..to }
    if (live.isEmpty()) return emptyList()
    val first = maxOf(from, live.minOf { it.date })
    val last = minOf(to, today)
    return if (days(first, last) > 62) {
      live.groupBy { it.date.take(7) }.toSortedMap().map { (m, l) -> ir.vil3ntec.tohid.formatMonth(m) to l.sumOf { it.finalTotal } }
    } else {
      val by = live.groupBy { it.date }.mapValues { (_, l) -> l.sumOf { it.finalTotal } }
      val out = ArrayList<Pair<String, Double>>()
      var day = first
      var guard = 0
      while (day <= last && guard++ < 100) {
        out += RowWhen.of(day, today) to (by[day] ?: 0.0)
        day = ir.vil3ntec.tohid.core.calendar.CivilDays.addDays(day, 1) ?: break
      }
      out
    }
  }
}

/** ستونِ کنارِ جدولِ «فروش»/«سود»: نمودارِ روند و سهمِ روش‌های پرداخت — یک رنگِ «گزارش‌ها»، بی انیمیشن */
@Composable
internal fun DeskReportCharts(trend: List<Pair<String, Double>>, sales: ReportCore.SalesReport, modifier: Modifier) {
  val tone = DeskHues.tone("reports")
  Column(modifier, verticalArrangement = Arrangement.spacedBy(DeskSpace.gap)) {
    DeskCard(Modifier.fillMaxWidth().weight(1f), title = DeskReportTrend.title(trend, sales.from, sales.to), icon = Icons.AutoMirrored.Outlined.TrendingUp, tone = tone) {
      if (trend.none { it.second > 0 }) DeskNote(StrReports.tableEmpty)
      else DeskBarChart(
        trend.map { it.second }, trend.map { it.first }, tone.solid, Modifier.fillMaxWidth().weight(1f).heightIn(min = 80.dp),
        format = ir.vil3ntec.tohid.ui.v3.money.MoneyText::afn, description = trend.joinToString("، ") { "${it.first}: ${ir.vil3ntec.tohid.ui.v3.money.MoneyText.afn(it.second)}" },
      )
    }
    DeskCard(Modifier.fillMaxWidth().weight(1f), title = ReportBoard.DIM_LABELS.getValue("payment"), icon = Icons.Outlined.Payments, tone = tone) {
      //  نامِ روش به زبانِ برنامه («نقد»، «نسیه»…)، نه کلیدِ دفتر
      val rows = sales.payment.sortedByDescending { it.netSales }.take(6).map { ir.vil3ntec.tohid.data.PayFlow.methodName(it.name.ifBlank { it.key }) to it.netSales / 100.0 }
      if (rows.isEmpty()) DeskNote(StrReports.tableEmpty)
      else DeskShareBars(rows, tone.solid, Modifier.fillMaxWidth(), format = ir.vil3ntec.tohid.ui.v3.money.MoneyText::afn)
    }
  }
}

private fun colWidth(kind: String): Dp = when (kind) {
  "text" -> 200.dp
  "date" -> 112.dp
  "flag" -> 90.dp
  else -> 124.dp
}

/**
 *  جدولِ برگهٔ گزارش روی کامپیوتر — همان `FreshTable` (مرتب‌سازی با زدنِ سرستون، «ریز» ِ ردیف با کلیک یا Tab تا ردیف و
 *  Enter = `KeyMap.REPORTS_DRILL`، «فاکتور #۱۲» ِ زدنی، ردیفِ جمع)، بی خطِ دورِ هر خانه: سرِ جدولِ سطحِ دوم، ردیفِ ۴۰dp با
 *  خطِ مویی، کارتِ کامپیوتر. ستونی که جا نشد ⇒ پیمایشِ افقی، نه بریدن.
 */
@Composable
internal fun DeskFreshTable(t: ReportTables.Table, modifier: Modifier, onRow: ((List<JsonPrimitive>) -> Unit)? = null) {
  val c = Desk.colors
  var sort by remember(t.id) { mutableStateOf<Pair<Int, Boolean>?>(null) }
  val rows = remember(t, sort) { sort?.let { (col, desc) -> ReportBoard.sortRows(t.rows, col, desc) } ?: t.rows }
  BoxWithConstraints(modifier.deskCard(c)) {
    val natural = t.columns.fold(0.dp) { a, col -> a + colWidth(col.kind) }
    //  جدولِ باریک‌تر از کارت تا پهنای کارت کشیده می‌شود (ستونِ نام بزرگ‌تر)، پهن‌تر پیمایشِ افقی
    val extra = (maxWidth - natural - DeskSpace.x4).coerceAtLeast(0.dp)
    fun w(i: Int): Dp = colWidth(t.columns[i].kind) + if (i == 0) extra else 0.dp
    val total = natural + extra
    Box(Modifier.fillMaxSize().horizontalScroll(rememberScrollState())) {
      Column(Modifier.width(total).padding(DeskSpace.x2)) {
        Row(Modifier.fillMaxWidth().height(DeskSize.tableHead).clip(DeskRadius.controlShape).background(c.surfaceAlt), verticalAlignment = Alignment.CenterVertically) {
          t.columns.forEachIndexed { i, col ->
            val mark = when { sort?.first != i -> ""; sort?.second == true -> " ▼"; else -> " ▲" }
            Box(
              Modifier.width(w(i)).fillMaxHeight()
                .deskClickable(label = StrReportPages.sortBy(col.label), role = Role.Button) { sort = if (sort?.first == i) i to !(sort?.second ?: false) else i to (col.kind != "text") }
                .padding(horizontal = DeskSpace.x3),
              contentAlignment = if (col.kind == "text" || col.kind == "date") Alignment.CenterStart else Alignment.CenterEnd,
            ) { Text(col.label + mark, style = DeskType.tableHead, color = c.inkSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis) }
          }
        }
        LazyColumn(Modifier.weight(1f, fill = false)) {
          if (rows.isEmpty()) item { DeskNote(StrReports.tableEmpty, Modifier.padding(DeskSpace.x6)) }
          items(rows.size) { r ->
            val open = onRow
            Column {
              Row(
                Modifier.fillMaxWidth().heightIn(min = 40.dp)
                  .then(
                    if (open != null) Modifier
                      .onPreviewKeyEvent { e -> if (e.matches(KeyMap.REPORTS_DRILL)) { open(rows[r]); true } else false }
                      .deskClickable(label = StrReportPages.detailOf(ReportFreshModel.display(rows[r].first(), t.columns[0].kind, ::formatDate))) { open(rows[r]) }
                    else Modifier,
                  ),
                verticalAlignment = Alignment.CenterVertically,
              ) {
                rows[r].forEachIndexed { i, v ->
                  Box(Modifier.width(w(i)).padding(horizontal = DeskSpace.x3), contentAlignment = if (t.columns[i].kind == "text" || t.columns[i].kind == "date") Alignment.CenterStart else Alignment.CenterEnd) {
                    val shown = ReportFreshModel.display(v, t.columns[i].kind, ::formatDate)
                    //  «فاکتور #۱۲» ِ ستونِ سند زدنی ⇒ همان فاکتور (`InvoiceRef`)
                    if (t.columns[i].kind == "text") ir.vil3ntec.tohid.ui.v3.InvoiceRefText(ir.vil3ntec.tohid.ui.v3.InvoiceRefs.split(shown), style = DeskType.table, color = c.ink, maxLines = 1)
                    else Text(shown, maxLines = 1, overflow = TextOverflow.Ellipsis, style = DeskType.table, color = c.ink)
                  }
                }
              }
              DeskDivider()
            }
          }
        }
        t.total?.let { tot ->
          Row(Modifier.fillMaxWidth().padding(top = DeskSpace.x1).height(DeskSize.tableHead).clip(DeskRadius.controlShape).background(c.surfaceAlt), verticalAlignment = Alignment.CenterVertically) {
            tot.forEachIndexed { i, v ->
              Box(Modifier.width(w(i)).padding(horizontal = DeskSpace.x3), contentAlignment = if (t.columns[i].kind == "text" || t.columns[i].kind == "date") Alignment.CenterStart else Alignment.CenterEnd) {
                Text(if (v is JsonNull) "" else ReportFreshModel.display(v, t.columns[i].kind, ::formatDate), style = DeskType.table.copy(fontWeight = DeskType.bodyStrong.fontWeight), color = c.ink, maxLines = 1)
              }
            }
          }
        }
      }
    }
  }
}

/**
 *  زبانهٔ «امروز» ِ گزارشِ کامپیوتر: همان تابلوی هسته (`ReportBoard.build`، بیرون از رشتهٔ صفحه) به شکلِ کارت‌های KPI ِ کامپیوتر —
 *  پنج عددِ نخست در یک ردیف و بقیه در ردیفِ دوم، تغییر نسبت به دیروز با پیکان؛ زدنِ کارت همان زبانه/بازه را باز می‌کند (`ReportFresh.open`).
 *  نشان‌های زیر (سودِ تقریبی، قرضِ دیرکرده…) پیوندِ آرام‌اند، نه قرصِ رنگی.
 */
@Composable
internal fun DeskTodayKpis(d: ShopData, showMoney: Boolean) {
  val today = ir.vil3ntec.tohid.todayIso()
  val cost = ReportFresh.cost.value
  val home = ir.vil3ntec.tohid.ui.desk.rememberDeskLoad(d, today, showMoney, cost) {
    ReportBoard.build(ir.vil3ntec.tohid.data.ReportBridge.ledgerOf(d), today, ir.vil3ntec.tohid.data.ReportBridge.deviceClock(), ReportFreshModel.costingOf(cost), ir.vil3ntec.tohid.core.cost.CostPolicy.approved(cost), money = showMoney)
  } ?: return
  val hues = listOf(ir.vil3ntec.tohid.ui.v3.V3Hue.BRAND, ir.vil3ntec.tohid.ui.v3.V3Hue.SKY, ir.vil3ntec.tohid.ui.v3.V3Hue.MINT, ir.vil3ntec.tohid.ui.v3.V3Hue.AMBER, ir.vil3ntec.tohid.ui.v3.V3Hue.VIOLET)
  Column(verticalArrangement = Arrangement.spacedBy(DeskSpace.gap)) {
    home.kpis.chunked(5).forEachIndexed { r, row ->
      ir.vil3ntec.tohid.ui.desk.DeskKpiRow {
        row.forEachIndexed { i, k ->
          val pct = k.delta?.bp?.let { (it / 100).toInt() }
          val up = k.trend == "up"
          ir.vil3ntec.tohid.ui.desk.DeskKpiCard(
            ir.vil3ntec.tohid.ui.desk.DeskKpi(
              k.title, if (k.kind == "money") "${money(k.value / 100.0)} ؋" else ir.vil3ntec.tohid.plain(k.value.toInt()),
              Icons.AutoMirrored.Outlined.TrendingUp, DeskHues.tone(hues[(r * 5 + i) % hues.size]),
              pct = pct, versus = StrDesk.versusYesterday, fallback = k.period,
              higherIsBetter = if (k.good == null || k.trend == null || k.trend == "flat") true else up == k.good,
            ),
            Modifier.weight(1f).fillMaxHeight(),
            onClick = { ReportFresh.open(k.target) {} },
          )
        }
        repeat(5 - row.size) { Box(Modifier.weight(1f)) }
      }
    }
    if (home.pills.isNotEmpty()) Row(verticalAlignment = Alignment.CenterVertically) {
      home.pills.forEach { p -> ir.vil3ntec.tohid.ui.desk.DeskLink(p.text, { ReportFresh.open(p.target) {} }) }
    }
  }
}
