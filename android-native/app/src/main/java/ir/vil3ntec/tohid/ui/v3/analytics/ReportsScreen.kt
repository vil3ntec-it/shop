package ir.vil3ntec.tohid.ui.v3.analytics

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Print
import androidx.compose.material.icons.outlined.TableChart
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.LocaleChoice
import ir.vil3ntec.tohid.core.calendar.SolarHijri
import ir.vil3ntec.tohid.core.i18n.I18n
import ir.vil3ntec.tohid.core.i18n.Str
import ir.vil3ntec.tohid.core.i18n.StrReports
import ir.vil3ntec.tohid.data.ReportCsv
import ir.vil3ntec.tohid.data.ReportEngine
import ir.vil3ntec.tohid.data.ShopData
import ir.vil3ntec.tohid.print.ReportPrint
import ir.vil3ntec.tohid.todayIso
import ir.vil3ntec.tohid.ui.screens.Haptics
import ir.vil3ntec.tohid.ui.theme.PhoneV3
import ir.vil3ntec.tohid.ui.theme.V3Tone
import ir.vil3ntec.tohid.ui.v3.LocalV3Toasts
import ir.vil3ntec.tohid.ui.v3.V3Action
import ir.vil3ntec.tohid.ui.v3.V3Chip
import ir.vil3ntec.tohid.ui.v3.V3ChipRow
import ir.vil3ntec.tohid.ui.v3.V3MenuItem
import ir.vil3ntec.tohid.ui.v3.V3Message
import ir.vil3ntec.tohid.ui.v3.V3PageSwitch
import ir.vil3ntec.tohid.ui.v3.V3StaggerState
import ir.vil3ntec.tohid.ui.v3.rememberV3Stagger
import ir.vil3ntec.tohid.ui.v3.v3Shake
import ir.vil3ntec.tohid.ui.v3.v3Stagger
import ir.vil3ntec.tohid.ui.v3.V3Scaffold
import ir.vil3ntec.tohid.ui.v3.V3Type
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** تاریخِ خورشیدیِ عددی (`1405/07/28`) — گزارش‌ها روی ماه و سالِ خورشیدی‌اند */
internal fun uiDate(iso: String): String = I18n.dateNumeric(iso, I18n.lang, I18n.CAL_SOLAR)

/** تاریخِ بلند: «۲۸ میزان ۱۴۰۵» */
internal fun uiDateLong(iso: String): String = I18n.dateLong(iso, I18n.lang, I18n.CAL_SOLAR, LocaleChoice.months)

/**
 *  «گزارش‌ها» (`reports?tab=&range=`): شش زبانه با نشانگرِ لغزان، ردیفِ بازه برای زبانه‌های
 *  بازه‌دار (ماه…/سال… ⇐ برگهٔ انتخاب)، چاپ در سربرگ و خروجیِ CSV در «…». همهٔ عددها از
 *  `ReportEngine`/`ExpiryEngine`؛ خروجی و چاپ همان برگهٔ `ReportCsv` ِ امروز.
 */
@Composable
fun ReportsScreen(
  d: ShopData,
  canSeeMoney: Boolean,
  tabArg: String?,
  rangeArg: String?,
  onBack: (() -> Unit)?,
  bottomReserve: Dp,
  onOpen: (String) -> Unit,
  today: String = todayIso(),
  startPicker: Boolean = false,
) {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  val toasts = LocalV3Toasts.current
  val tabs = ReportsModel.tabs(canSeeMoney)
  var tabId by rememberSaveable(tabArg) { mutableStateOf(ReportsModel.tabOf(tabArg, canSeeMoney).id) }
  val tab = ReportsModel.tabOf(tabId, canSeeMoney)
  val now = remember(today) { SolarHijri.toSolar(today) }
  var rangeId by rememberSaveable(rangeArg) { mutableStateOf(ReportsModel.rangeId(ReportsModel.rangeOf(rangeArg))) }
  var year by rememberSaveable { mutableStateOf(now?.year ?: 1405) }
  var month by rememberSaveable { mutableStateOf(now?.month ?: 1) }
  val period = ReportPeriod(ReportsModel.rangeOf(rangeId), year, month)
  var picker by remember { mutableStateOf(if (startPicker) period.range else null) }
  var csvFailed by remember { mutableStateOf(false) }
  var failures by remember { mutableStateOf(0) }
  val (from, to) = ReportsModel.bounds(period, today)

  fun sheet() = ReportCsv.of(tab.csv, d, from, to, today)
  var pending by remember { mutableStateOf<ReportCsv.Sheet?>(null) }
  //  نوشتن با انتخابگرِ خودِ اندروید — بی اجازهٔ ذخیره‌سازی (همان راهِ امروز)
  val saveCsv = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
    val s = pending
    pending = null
    if (uri == null || s == null) return@rememberLauncherForActivityResult
    scope.launch {
      val ok = runCatching {
        withContext(Dispatchers.IO) {
          context.contentResolver.openOutputStream(uri)?.use { it.write(ReportCsv.text(s).toByteArray(Charsets.UTF_8)) } ?: error("no stream")
        }
      }.isSuccess
      csvFailed = !ok
      if (!ok) failures++
      if (ok) { Haptics.success(context); toasts?.show(StrReports.csvSaved, V3Tone.SUCCESS) }
    }
  }
  fun exportCsv() {
    val s = sheet()
    if (s.isEmpty) { toasts?.show(StrReports.nothingToExport, V3Tone.INFO); return }
    pending = s
    saveCsv.launch("${s.name}.csv")
  }
  fun print() = ReportPrint.print(context, sheet(), tabLabel(tab))

  V3Scaffold(
    title = Str.reports,
    onBack = onBack,
    actions = listOf(V3Action(Icons.Outlined.Print, StrReports.print) { print() }),
    overflow = listOf(V3MenuItem(Icons.Outlined.TableChart, StrReports.exportCsv) { exportCsv() }),
    chips = {
      DraftTabStrip(tabs.map { tabLabel(it) }, tabs.indexOf(tab).coerceAtLeast(0), { tabId = tabs[it].id })
    },
    bottomReserve = bottomReserve,
  ) { pad ->
    fun header(t: ReportTab): LazyListScope.() -> Unit = {
      item(key = "range") {
        if (t.ranged) RangeRow(period, from, to) { r ->
          rangeId = ReportsModel.rangeId(r)
          if (r == ReportEngine.Range.JMONTH || r == ReportEngine.Range.JYEAR) picker = r
        } else Text(
          if (t == ReportTab.STOCK) StrReports.allMovements else StrReports.rightNow,
          style = V3Type.footnote, color = PhoneV3.colors.inkMuted, modifier = Modifier.padding(top = 4.dp),
        )
      }
      if (csvFailed) item(key = "csv-error") {
        V3Message(V3Tone.ERROR, StrReports.csvFailed, Modifier.padding(top = 12.dp).v3Shake(failures), body = StrReports.csvFailedBody, action = Str.close, onAction = { csvFailed = false })
      }
    }
    V3PageSwitch(tab, { it.ordinal }, Modifier.fillMaxSize()) { t ->
      val list = ReportListSpec(pad, header(t), onOpen, rememberV3Stagger(t))
      when (t) {
        ReportTab.PROFIT -> ProfitTab(d, from, to, list)
        ReportTab.PRODUCTS -> ProductsTab(d, canSeeMoney, list)
        ReportTab.DEBTORS -> DebtorsTab(d, list)
        ReportTab.PURCHASES -> PurchasesTab(d, from, to, list)
        ReportTab.EXPIRY -> ExpiryTab(d, today, list)
        ReportTab.STOCK -> StockTab(d, list)
      }
    }
  }

  picker?.let { r ->
    PeriodSheet(period.copy(range = r), onDismiss = { picker = null }) { p -> year = p.year; month = p.month; rangeId = ReportsModel.rangeId(p.range) }
  }
}

/** آنچه هر زبانه برای فهرستش می‌گیرد: فاصلهٔ پایین، سرِ مشترک (بازه/پیام)، ناوبری و آمدنِ پلکانی */
internal class ReportListSpec(
  val pad: PaddingValues,
  val header: LazyListScope.() -> Unit,
  val onOpen: (String) -> Unit,
  private val stagger: V3StaggerState,
) {
  /** آمدنِ پلکانیِ بخش‌های زبانه (محو + بالا آمدن، فقط بارِ اول) */
  fun appear(key: String, index: Int): Modifier = Modifier.v3Stagger(stagger, key, index)
}

/** فهرستِ یک زبانه: سرِ مشترک و سپس محتوا */
@Composable
internal fun ReportList(spec: ReportListSpec, content: LazyListScope.() -> Unit) {
  LazyColumn(
    Modifier.fillMaxSize(),
    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = spec.pad.calculateBottomPadding()),
    verticalArrangement = Arrangement.spacedBy(0.dp),
  ) {
    spec.header(this)
    content()
  }
}

internal fun tabLabel(t: ReportTab): String = when (t) {
  ReportTab.PROFIT -> StrReports.tabProfit
  ReportTab.PRODUCTS -> StrReports.tabProducts
  ReportTab.DEBTORS -> StrReports.tabDebtors
  ReportTab.PURCHASES -> StrReports.tabPurchases
  ReportTab.EXPIRY -> StrReports.tabExpiry
  ReportTab.STOCK -> StrReports.tabStock
}

/** ردیفِ بازه و زیرنویسِ «از … تا …» */
@Composable
private fun RangeRow(period: ReportPeriod, from: String, to: String, onPick: (ReportEngine.Range) -> Unit) {
  val c = PhoneV3.colors
  val months = I18n.solarMonths(I18n.lang, LocaleChoice.months)
  Column(Modifier.padding(top = 4.dp)) {
    V3ChipRow(contentPadding = PaddingValues(0.dp)) {
      ReportsModel.RANGES.forEach { r ->
        val on = r == period.range
        val label = when (r) {
          ReportEngine.Range.TODAY -> StrReports.rangeToday
          ReportEngine.Range.WEEK -> StrReports.rangeWeek
          ReportEngine.Range.MONTH -> StrReports.rangeMonth
          ReportEngine.Range.JMONTH -> if (on) "${months.getOrElse(period.month - 1) { "" }} ${period.year}" else StrReports.rangePickMonth
          ReportEngine.Range.JYEAR -> if (on) StrReports.yearLabel(period.year) else StrReports.rangePickYear
          ReportEngine.Range.ALL -> StrReports.rangeAll
        }
        V3Chip(label, on, { onPick(r) })
      }
    }
    Text(
      if (ReportsModel.isAllTime(from)) StrReports.allDates else StrReports.rangeSpan(uiDateLong(from), uiDateLong(to)),
      style = V3Type.footnote,
      color = c.inkMuted,
      modifier = Modifier.padding(top = 8.dp, start = 4.dp, end = 4.dp),
    )
  }
}
