package ir.vil3ntec.tohid.ui.v3.analytics

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.InsertChart
import androidx.compose.material.icons.outlined.Storefront
import androidx.compose.material.icons.outlined.Print
import androidx.compose.material.icons.outlined.TableChart
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.Jalali
import ir.vil3ntec.tohid.core.i18n.Str
import ir.vil3ntec.tohid.core.i18n.StrBranches
import ir.vil3ntec.tohid.core.i18n.StrOnlineReport
import ir.vil3ntec.tohid.core.i18n.StrReach
import ir.vil3ntec.tohid.core.i18n.StrReportPages
import ir.vil3ntec.tohid.core.i18n.StrReports
import ir.vil3ntec.tohid.core.i18n.StrShrink
import ir.vil3ntec.tohid.core.i18n.StrSimpleAsk
import ir.vil3ntec.tohid.data.DebtReminderPref
import ir.vil3ntec.tohid.data.DocGapsPref
import ir.vil3ntec.tohid.data.DocGapsSheet
import ir.vil3ntec.tohid.data.ReportKeysPref
import ir.vil3ntec.tohid.data.ShopPolicy
import ir.vil3ntec.tohid.data.ShopRole
import ir.vil3ntec.tohid.data.ShrinkLoss
import ir.vil3ntec.tohid.data.ShrinkReportPref
import ir.vil3ntec.tohid.data.SimpleAskPref
import ir.vil3ntec.tohid.ui.screens.DeskSimpleAsk
import ir.vil3ntec.tohid.ui.screens.ReportCompareLine
import ir.vil3ntec.tohid.ui.screens.ReportKeys
import ir.vil3ntec.tohid.core.keymap.KeyMap
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material.icons.outlined.WarningAmber
import ir.vil3ntec.tohid.ui.desk.Desk
import ir.vil3ntec.tohid.ui.desk.DeskButton
import ir.vil3ntec.tohid.ui.desk.DeskButtonKind
import ir.vil3ntec.tohid.ui.desk.DeskDropdown
import ir.vil3ntec.tohid.ui.desk.DeskHues
import ir.vil3ntec.tohid.ui.desk.DeskKpi
import ir.vil3ntec.tohid.ui.desk.DeskKpiCard
import ir.vil3ntec.tohid.ui.desk.DeskNote
import ir.vil3ntec.tohid.ui.desk.DeskOption
import ir.vil3ntec.tohid.ui.desk.DeskSize
import ir.vil3ntec.tohid.ui.desk.DeskSkeleton
import ir.vil3ntec.tohid.ui.desk.DeskSpace
import ir.vil3ntec.tohid.ui.desk.DeskTextField
import ir.vil3ntec.tohid.ui.desk.DeskToolbar
import ir.vil3ntec.tohid.ui.desk.DeskType
import ir.vil3ntec.tohid.ui.desk.deskCard
import ir.vil3ntec.tohid.ui.desk.deskClickable
import ir.vil3ntec.tohid.ui.desk.rememberDeskLoad
import ir.vil3ntec.tohid.data.ReportCsv
import ir.vil3ntec.tohid.data.ReportDrillSheet
import ir.vil3ntec.tohid.data.ReportEngine
import ir.vil3ntec.tohid.data.ShopData
import ir.vil3ntec.tohid.data.ShopStore
import ir.vil3ntec.tohid.data.Xlsx
import ir.vil3ntec.tohid.formatDate
import ir.vil3ntec.tohid.print.ReportPrint
import ir.vil3ntec.tohid.todayIso
import ir.vil3ntec.tohid.ui.Platform
import ir.vil3ntec.tohid.ui.screens.DeskFreshReports
import ir.vil3ntec.tohid.ui.screens.MotionSpec
import ir.vil3ntec.tohid.ui.screens.MotionTokens
import ir.vil3ntec.tohid.ui.screens.ReportFresh
import ir.vil3ntec.tohid.ui.screens.hint
import ir.vil3ntec.tohid.ui.screens.matches
import ir.vil3ntec.tohid.ui.theme.PhoneV3
import ir.vil3ntec.tohid.ui.theme.PhoneV3Palette
import ir.vil3ntec.tohid.ui.theme.PhoneV3Roles
import ir.vil3ntec.tohid.ui.theme.V3ButtonKind
import ir.vil3ntec.tohid.ui.v3.V3Button
import ir.vil3ntec.tohid.ui.v3.V3Card
import ir.vil3ntec.tohid.ui.v3.V3Chip
import ir.vil3ntec.tohid.ui.v3.V3ChipRow
import ir.vil3ntec.tohid.ui.v3.V3Hue
import ir.vil3ntec.tohid.ui.v3.V3Sheet
import ir.vil3ntec.tohid.ui.v3.V3Stat
import ir.vil3ntec.tohid.ui.v3.V3TextField
import ir.vil3ntec.tohid.ui.v3.V3Toggle
import ir.vil3ntec.tohid.ui.v3.V3Type
import ir.vil3ntec.tohid.ui.v3.v3Pressable
import kotlinx.coroutines.launch

/** گزارش‌های کامپیوتر — هر کدام یک برگهٔ `ReportCsv` (همان فایلِ اکسل و چاپ) */
private enum class DeskRep(val key: String, val ranged: Boolean) {
  SALES("sales", true), PROFIT("profit", true), PURCHASES("purchases", true), EXPENSES("expenses", true),
  SELLERS("sellers", true), PRODUCTS("products", false), DEBTORS("debtors", false), STOCK("stock", false), EXPIRY("expiry", false),
  //  زبانه‌های اختیاریِ گزارشِ کامپیوترِ پیشین (همان کلیدهای `ReportCsv`)، هر کدام پشتِ تنظیمِ خودش (`ReportsDesk`)؛
  //  «پرسشِ ساده» جای جدول پانلِ پرسش است
  INSIGHT("insight", true), REORDER("reorder", false), ASK("ask", false),
  AGING("aging", false), RFM("rfm", false), DUPLICATES("duplicates", false), REMINDERS("reminders", false),
  GAPS("gaps", false), SHRINK("shrink", true),
  ;

  /** همیشه هست (بی تنظیم) */
  val base: Boolean get() = ordinal <= EXPIRY.ordinal
}

private enum class DeskSpan { TODAY, WEEK, MONTH, JMONTH, JYEAR, ALL, CUSTOM }

private fun DeskRep.label() = when (this) {
  DeskRep.SALES -> StrReports.deskSales
  DeskRep.PROFIT -> StrReports.deskProfit
  DeskRep.PURCHASES -> StrReports.tabPurchases
  DeskRep.EXPENSES -> StrReports.deskExpenses
  DeskRep.SELLERS -> StrReports.deskSellers
  DeskRep.PRODUCTS -> StrReports.deskProducts
  DeskRep.DEBTORS -> StrReports.tabDebtors
  DeskRep.STOCK -> StrReports.tabStock
  DeskRep.EXPIRY -> StrReports.tabExpiry
  DeskRep.INSIGHT -> StrReach.tabInsight
  DeskRep.REORDER -> StrReach.tabReorder
  DeskRep.ASK -> StrSimpleAsk.tab
  DeskRep.AGING -> StrReach.tabAging
  DeskRep.RFM -> StrReach.tabRfm
  DeskRep.DUPLICATES -> StrReach.tabDuplicates
  DeskRep.REMINDERS -> StrReach.tabReminders
  DeskRep.GAPS -> StrReports.gapsTab
  DeskRep.SHRINK -> StrShrink.title
}

private fun DeskSpan.label() = when (this) {
  DeskSpan.TODAY -> StrReports.rangeToday
  DeskSpan.WEEK -> StrReports.rangeWeek
  DeskSpan.MONTH -> StrReports.rangeMonth
  DeskSpan.JMONTH -> StrReports.rangeJMonth
  DeskSpan.JYEAR -> StrReports.rangeJYear
  DeskSpan.ALL -> StrReports.rangeEverything
  DeskSpan.CUSTOM -> StrReports.rangeCustom
}

/**
 *  «گزارش‌ها» روی کامپیوتر: زیرنوارِ نوعِ گزارش در سمتِ آغاز، بازه (یا «وضعیتِ همین حالا»)،
 *  عددهای کلِ همان بازه، و جدولِ برگه. Ctrl+P چاپ و Ctrl+E اکسل از ثبتِ مرکزیِ کلیدها
 *  (`KeyMap.GLOBAL_PRINT`/`GLOBAL_EXPORT`). «گزارشِ تازه» همان کلیدِ امروز است (فقط کامپیوتر).
 */
@Composable
fun ReportsDesk(d: ShopData, snackbar: SnackbarHostState, onOpen: (String) -> Unit = {}) {
  val context = LocalContext.current
  //  CRM-15: چاپ یا اکسلِ برگه (کلیدِ `ReportCsv`) — برگهٔ مشتریان ⇐ «چه کسی پرونده را دید» (`AnalyticsRoutes`)
  val onExport = ir.vil3ntec.tohid.ui.screens.LocalReportExport.current
  //  RPT-14: «گزارشِ فروشِ اینترنتی» فقط با پرچمِ روشنِ سرور
  val online = rememberOnlineReportVisible()
  //  RPT-18: «گزارشِ همهٔ شعبه‌ها» فقط با پرچمِ روشنِ سرور و فقط برای صاحب
  val branches = rememberBranchesReportVisible()
  ReportFresh.load(context)
  if (ReportFresh.on.value) { DeskFreshReports(d, snackbar); return }
  val c = PhoneV3.colors
  val scope = rememberCoroutineScope()
  val today = todayIso()
  val j = remember(today) { Jalali.ofIso(today) }
  //  کامپیوتر امروز سدِ پول ندارد (همهٔ گزارش‌ها برای همه) — دست نخورد؛ زبانه‌های اختیاری همان تنظیم‌های گزارشِ
  //  کامپیوترِ پیشین (ضایعات قیمتِ خرید است ⇒ فقط با `canSeeMoney`)
  val canSeeMoney = remember { ShopRole.canSeeMoney(context) }
  val enabled = remember(canSeeMoney) {
    buildSet {
      if (ShopPolicy.stockInsight(context)) add(DeskRep.INSIGHT)
      if (ShopPolicy.stockLevels(context)) add(DeskRep.REORDER)
      if (SimpleAskPref.on(context)) add(DeskRep.ASK)
      if (ShopPolicy.crmReports(context)) addAll(listOf(DeskRep.AGING, DeskRep.RFM, DeskRep.DUPLICATES, DeskRep.REMINDERS))
      if (DocGapsPref.on(context)) add(DeskRep.GAPS)
      if (ShrinkReportPref.on(context) && canSeeMoney) add(DeskRep.SHRINK)
    }
  }
  val reps = DeskRep.entries.filter { it.base || it in enabled }
  var rep by remember { mutableStateOf(DeskRep.SALES) }
  var span by remember { mutableStateOf(DeskSpan.MONTH) }
  //  RPT-13: − / + دوره و Ctrl+Shift+C مقایسه — با `ReportKeysPref` (کلاسِ A، پیش‌فرض روشن)
  val keys = remember { ReportKeysPref.on(context) }
  var shift by remember(span) { mutableStateOf(0) }
  var compare by remember { mutableStateOf(false) }
  //  CRM-06: متنِ یادآوریِ همین دستگاه (نبودن = همان جملهٔ امروز)
  val reminderTemplate = remember { DebtReminderPref.template(context) }
  var fromText by remember { mutableStateOf(j?.let { "%d/%02d/01".format(it.year, it.month) }.orEmpty()) }
  var toText by remember { mutableStateOf(formatDate(today)) }
  val (baseFrom, baseTo) = when (span) {
    DeskSpan.TODAY -> ReportEngine.rangeOf(ReportEngine.Range.TODAY, today)
    DeskSpan.WEEK -> ReportEngine.rangeOf(ReportEngine.Range.WEEK, today)
    DeskSpan.MONTH -> ReportEngine.rangeOf(ReportEngine.Range.MONTH, today)
    DeskSpan.JMONTH -> ReportEngine.rangeOf(ReportEngine.Range.JMONTH, today, j?.year ?: 1405, j?.month ?: 1)
    DeskSpan.JYEAR -> ReportEngine.rangeOf(ReportEngine.Range.JYEAR, today, j?.year ?: 1405)
    DeskSpan.ALL -> ReportEngine.rangeOf(ReportEngine.Range.ALL, today)
    DeskSpan.CUSTOM -> (Jalali.parseTyped(fromText) ?: "0000-01-01") to (Jalali.parseTyped(toText) ?: "9999-12-31")
  }
  val (from, to) = if (keys && shift != 0) ReportKeys.range(span.name, baseFrom, baseTo, shift, today) else baseFrom to baseTo
  val lineSeller = remember { ir.vil3ntec.tohid.data.LineSellerPref.on(context) } // POS-23: فروشندهٔ هر ردیف
  val contacts = ir.vil3ntec.tohid.ui.components.rememberCanSeeContacts() // CRM-15: شمارهٔ مشتری در برگه
  val phoneOf: (String) -> String = { ir.vil3ntec.tohid.ui.components.customerPhoneExport(it, contacts) }
  //  PERF (e-desk-d3b): برگه و برگهٔ «دورهٔ قبل» بیرون از رشتهٔ صفحه — دفترِ بزرگ صفحه را نمی‌خواباند
  val base = rememberDeskLoad(d, rep, from, to, contacts) { rep to ReportCsv.of(rep.key, d, from, to, today, reminderTemplate, phone = phoneOf).let { ReportCsv.lineSellers(it, rep.key, d, from, to, lineSeller) } }
    ?.takeIf { it.first == rep }?.second
  //  RPT-13: «مقایسه» ⇒ ستونِ «دورهٔ قبل» و «تغییر» در خودِ جدول (چاپ و اکسل همان جدول): برگهٔ کلیددار ردیف‌به‌ردیف، برگهٔ
  //  فهرستیِ سند (فاکتور، خرید، مصرف) گروه‌شده با روشِ پرداخت/تأمین‌کننده/دسته (`ReportCsv.compareGroup`)
  val sheet = rememberDeskLoad(base, keys, compare, span, from, to) {
    val base = base ?: return@rememberDeskLoad null
    val keyed = ReportCsv.compareColumn(rep.key) != null
    val prev = if (keys && compare && rep.ranged && (keyed || ReportCsv.compareGroup(rep.key) != null))
      ir.vil3ntec.tohid.core.report.ReportPeriod.shift(ReportKeys.unitOf(span.name), from, to, -1, today) else null
    if (prev == null) base
    else {
      val past = ReportCsv.of(rep.key, d, prev.from, prev.to, today, reminderTemplate, phone = phoneOf).let { ReportCsv.lineSellers(it, rep.key, d, prev.from, prev.to, lineSeller) }
      if (keyed) ReportCsv.compared(rep.key, base, past, StrReach.prevColumn, StrReach.changeColumn)
      else ReportCsv.comparedList(rep.key, base, past, StrReports.colCount, StrReach.prevColumn, StrReach.changeColumn)
    }
  }?.takeIf { base != null }
  //  RPT-13: «ریز» ِ ردیف (کلیک یا Enter ِ `reports.drill`) در هر برگهٔ `ReportDrillSheet.KEYS` (فروش، فروشِ هر کاربر، سود و زیان،
  //  ردیفِ کالا، ردیفِ مشتری؛ برگه‌های تک‌سند `RECORD_ROWS` ریز ندارند) — فقط برگهٔ همان
  //  دوره بی «مقایسه» (ستون‌ها و ردیف‌های مقایسه جای ردیف را عوض می‌کنند) و بی «فروشندهٔ هر ردیف» در «فروشِ هر کاربر»
  var rowDrill by remember(rep, from, to) { mutableStateOf<ReportCsv.Sheet?>(null) }
  val onRow: ((Int) -> Unit)? =
    if (sheet != null && sheet === base && rep.key in ReportDrillSheet.KEYS && !(rep.key == "sellers" && lineSeller)) ({ i: Int ->
      ReportDrillSheet.of(rep.key, d, from, to, i, lineSeller, today)?.let { rowDrill = it }
    }) else null
  //  «پرسشِ ساده» برگه ندارد: Ctrl+P/Ctrl+E آن‌جا کاری نمی‌کنند
  fun print() { val sheet = sheet ?: return; if (rep != DeskRep.ASK) { onExport(rep.key); ReportPrint.print(context, sheet, sheet.name) } }
  fun excel() {
    val sheet = sheet ?: return
    if (rep == DeskRep.ASK) return
    Platform.saveFile("${sheet.name}.xlsx", Xlsx.of(sheet))?.let { onExport(rep.key); scope.launch { snackbar.showSnackbar(StrReports.savedTo(it)) } }
  }
  val shifting = keys && rep.ranged && span != DeskSpan.CUSTOM
  fun step(by: Int) { if (ReportKeys.can(span.name, baseFrom, baseTo, shift + by, today)) shift += by }

  Row(
    Modifier.fillMaxSize().background(Desk.colors.app).onPreviewKeyEvent { e ->
      when {
        e.matches(KeyMap.GLOBAL_PRINT) -> { print(); true }
        e.matches(KeyMap.GLOBAL_EXPORT) -> { excel(); true }
        //  «از … تا …» کادرِ نوشتنِ تاریخ دارد: − و + آن‌جا نوشته می‌شوند، نه دوره
        shifting && e.matches(KeyMap.REPORTS_PREV_PERIOD) -> { step(-1); true }
        shifting && e.matches(KeyMap.REPORTS_NEXT_PERIOD) -> { step(1); true }
        keys && rep.ranged && e.matches(KeyMap.REPORTS_COMPARE) -> { compare = !compare; true }
        else -> false
      }
    }.padding(horizontal = DeskSpace.page, vertical = DeskSpace.x4),
    horizontalArrangement = Arrangement.spacedBy(DeskSpace.gap),
  ) {
    //  زیرنوارِ نوعِ گزارش (سمتِ آغاز) — کارتِ کامپیوتر با ردیفِ ۳۶dp
    Column(Modifier.width(220.dp).fillMaxHeight().deskCard(Desk.colors).padding(DeskSpace.x2)) {
      Text(StrReports.reportType, style = DeskType.caption, color = Desk.colors.inkSecondary, modifier = Modifier.padding(start = 10.dp, top = 6.dp, bottom = 6.dp))
      reps.forEach { r -> RailItem(r.label(), r == rep) { rep = r } }
    }
    Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(DeskSpace.x3)) {
      DeskToolbar(
        end = {
          Text(StrReports.freshReport, style = DeskType.secondary, color = Desk.colors.inkSecondary)
          V3Toggle(ReportFresh.on.value, StrReports.freshReport) { ReportFresh.set(context, it) }
          if (online) {
            DeskButton(StrOnlineReport.title, { onOpen(AnalyticsPaths.ONLINE_REPORT) }, icon = Icons.Outlined.InsertChart)
          }
          if (branches) {
            DeskButton(StrBranches.title, { onOpen(AnalyticsPaths.BRANCHES_REPORT) }, icon = Icons.Outlined.Storefront)
          }
          DeskButton(StrReports.printShort, ::print, icon = Icons.Outlined.Print, shortcut = KeyMap.GLOBAL_PRINT.hint)
          DeskButton(StrReports.excel, ::excel, kind = DeskButtonKind.PRIMARY, icon = Icons.Outlined.TableChart, shortcut = KeyMap.GLOBAL_EXPORT.hint)
        },
      ) {
        if (rep.ranged && rep != DeskRep.ASK) {
          DeskDropdown(span, DeskSpan.entries.map { DeskOption(it, it.label()) }, { span = it }, label = ir.vil3ntec.tohid.core.i18n.StrLook.period)
          if (span == DeskSpan.CUSTOM) {
            DeskTextField(fromText, { fromText = it }, StrReports.from, Modifier.width(150.dp))
            DeskTextField(toText, { toText = it }, StrReports.to, Modifier.width(150.dp))
          }
          if (shifting) {
            DeskButton(StrReach.prevPeriod, { step(-1) }, kind = DeskButtonKind.GHOST, shortcut = KeyMap.REPORTS_PREV_PERIOD.hint)
            DeskButton(StrReach.nextPeriod, { step(1) }, kind = DeskButtonKind.GHOST, shortcut = KeyMap.REPORTS_NEXT_PERIOD.hint)
          }
          if (keys) DeskButton(StrReach.compare, { compare = !compare }, kind = if (compare) DeskButtonKind.PRIMARY else DeskButtonKind.SECONDARY, shortcut = KeyMap.REPORTS_COMPARE.hint)
        }
      }
      if (rep == DeskRep.ASK) {
        DeskSimpleAsk(d, canSeeMoney, Modifier.weight(1f))
        return@Column
      }
      DeskNote(if (!rep.ranged) StrReports.rightNow else if (ReportsModel.isAllTime(from)) StrReports.allDates else StrReports.rangeSpan(uiDateLong(from), uiDateLong(to)))
      if (keys && compare && rep.ranged) ReportCompareLine(d, rep.key, span.name, from, to, today)
      DeskKpis(d, rep, from, to)
      val s = sheet
      if (s == null) DeskSkeleton(Modifier.weight(1f).fillMaxWidth())
      else SheetTable(s, Modifier.weight(1f).fillMaxWidth(), onRow)
    }
  }
  rowDrill?.let { s ->
    V3Sheet(onDismiss = { rowDrill = null }, title = StrReportPages.detailOf(s.name), scroll = false) {
      SheetTable(s, Modifier.fillMaxWidth().heightIn(max = 480.dp).padding(horizontal = 16.dp, vertical = 8.dp))
    }
  }
}

/** یک موردِ زیرنوار: انتخاب‌شده با سطحِ انتخاب و نوارِ برند (همان ردیفِ منوی کامپیوتر)، بی انیمیشن */
@Composable
private fun RailItem(label: String, on: Boolean, onClick: () -> Unit) {
  val c = Desk.colors
  Box(
    Modifier.fillMaxWidth().height(DeskSize.navItem)
      .deskClickable(label = label, role = Role.Tab, selected = on, onClick = onClick).padding(horizontal = DeskSpace.x3),
    contentAlignment = Alignment.CenterStart,
  ) { Text(label, style = if (on) DeskType.bodyStrong else DeskType.navItem, color = if (on) c.brandInk else c.ink, maxLines = 1) }
}

/** عددهای کلِ بازه — همان عددهای امروزِ صفحهٔ کامپیوتر */
@Composable
private fun DeskKpis(d: ShopData, rep: DeskRep, from: String, to: String) {
  val count: (Double) -> String = { it.toLong().toString() }
  Row(Modifier.fillMaxWidth().height(androidx.compose.foundation.layout.IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(DeskSpace.gap)) {
    val m = Modifier.weight(1f)
    when (rep) {
      DeskRep.SALES, DeskRep.PROFIT, DeskRep.SELLERS -> {
        val r = rememberDeskLoad(d, from, to) { ReportEngine.sales(d, from, to) } ?: return@Row
        KpiStat(StrReports.netSales, r.net, ::afn, m, hue = V3Hue.BRAND)
        KpiStat(StrReports.invoices, r.count.toDouble(), count, m)
        KpiStat(if (r.grossProfit < 0) StrReports.grossLoss else StrReports.grossProfit, kotlin.math.abs(r.grossProfit), ::afn, m, hue = V3Hue.TEAL)
        KpiStat(StrReports.expenses, r.expenses, ::afn, m, hue = V3Hue.PURPLE)
        KpiStat(if (r.netProfit < 0) StrReports.netLoss else StrReports.netProfit, kotlin.math.abs(r.netProfit), ::afn, m, valueColor = if (r.netProfit < 0) PhoneV3.colors.negative else null)
      }
      DeskRep.PURCHASES -> {
        val r = rememberDeskLoad(d, from, to) { ReportEngine.purchases(d, from, to) } ?: return@Row
        KpiStat(StrReports.purchasesTotal, r.total, ::afn, m, hue = V3Hue.PURPLE)
        KpiStat(StrReports.paid, r.paid, ::afn, m, hue = V3Hue.BRAND)
        KpiStat(StrReports.onSupplierAccount, r.debt, ::afn, m, hue = V3Hue.ORANGE)
        KpiStat(StrReports.topSupplier, 0.0, { r.bySupplier.firstOrNull()?.first ?: "—" }, m)
        //  PUR-11: اختلافِ نرخِ پرداخت‌های ارزیِ بازه — فقط وقتی هست (دکانِ فقط‌افغانی همان چهار کاشی)
        if (r.fxDiff != 0L) {
          KpiStat(ir.vil3ntec.tohid.core.i18n.StrPurchaseFx.fxDiffCol, r.fxDiff / 100.0, ::afn, m,
            valueColor = if (r.fxDiff > 0) PhoneV3.colors.negative else null,
            caption = ir.vil3ntec.tohid.core.i18n.StrPurchaseFx.fxDiffReportNote)
        }
      }
      DeskRep.EXPENSES -> {
        val rows = remember(d, from, to) { d.expenses.filter { it.date in from..to } }
        val top = remember(rows) { rows.groupBy { it.category }.maxByOrNull { (_, l) -> l.sumOf { it.amount } }?.key }
        KpiStat(StrReports.expensesTotal, rows.sumOf { it.amount }, ::afn, m, hue = V3Hue.PURPLE)
        KpiStat(StrReports.topCategory, 0.0, { top?.ifBlank { StrReports.noCategory } ?: "—" }, m)
      }
      DeskRep.PRODUCTS, DeskRep.STOCK, DeskRep.EXPIRY -> {
        //  ۳-۱۶ (۲): همان ارزشِ میانگینِ موزونِ گزارشِ محصولات (`ReportEngine.stockValue`)، نه «قیمتِ خرید» ِ کارت
        val sv = rememberDeskLoad(d) { ReportEngine.stockValue(d) } ?: return@Row
        val value = sv.valuation.costMinor / 100.0
        val out = remember(d) { d.products.count { ShopStore.stockStatus(d, it) == "out" } }
        val low = remember(d) { d.products.count { ShopStore.stockStatus(d, it) == "low" } }
        KpiStat(StrReports.inventoryValue, value, ::afn, m, hue = V3Hue.BRAND,
          caption = inventoryCaption(sv.approximate, sv.valuation.negative.size, sv.kits))
        KpiStat(StrReports.items, d.products.size.toDouble(), count, m)
        KpiStat(StrReports.soldOut, out.toDouble(), count, m, valueColor = if (out > 0) PhoneV3.colors.negative else null)
        KpiStat(StrReports.lowStock, low.toDouble(), count, m, hue = V3Hue.ORANGE)
      }
      DeskRep.DEBTORS -> {
        val total = rememberDeskLoad(d) { d.debtors.sumOf { ShopStore.debt(d, it.id).coerceAtLeast(0.0) } } ?: return@Row
        KpiStat(StrReports.totalReceivable, total, ::afn, m, hue = V3Hue.BLUE)
        KpiStat(StrReports.debtorsOwing, d.debtors.count { ShopStore.debt(d, it.id) > 0.5 }.toDouble(), count, m)
      }
      DeskRep.GAPS -> {
        val g = remember(d) { DocGapsSheet.report(d) }
        KpiStat(StrReports.kpiMissing, g.missing.toDouble(), count, m, valueColor = if (g.missing > 0) PhoneV3.colors.negative else null)
        KpiStat(StrReports.kpiJumped, g.jumped.toDouble(), count, m)
        KpiStat(StrReports.kpiDuplicates, g.duplicates.toDouble(), count, m, hue = V3Hue.ORANGE)
        KpiStat(StrReports.kpiCancelled, g.cancelled.toDouble(), count, m)
      }
      DeskRep.SHRINK -> {
        val r = rememberDeskLoad(d, from, to) { ShrinkLoss.report(d, from, to) } ?: return@Row
        KpiStat(StrShrink.wasteTotal, r.wasteMinor / 100.0, ::afn, m, hue = V3Hue.ORANGE)
        KpiStat(StrShrink.shrinkTotal, r.shrinkMinor / 100.0, ::afn, m, valueColor = if (r.shrinkMinor > 0) PhoneV3.colors.negative else null)
        KpiStat(StrShrink.netLoss, r.netLossMinor / 100.0, ::afn, m, valueColor = if (r.netLossMinor > 0) PhoneV3.colors.negative else null)
      }
      //  بقیهٔ زبانه‌های اختیاری: همهٔ عددها در جدولِ همان برگه
      DeskRep.INSIGHT, DeskRep.REORDER, DeskRep.ASK, DeskRep.AGING, DeskRep.RFM, DeskRep.DUPLICATES, DeskRep.REMINDERS -> Unit
    }
  }
}

/**
 *  کارتِ KPI ِ کامپیوتر به جای `V3Stat` ِ گوشی (`claude/e-desk-d3b`) — همان پارامترها: رنگ از نقشهٔ یکتای رنگ‌ها، «زیان»/«ته‌کشیده»
 *  ([valueColor]) با رنگِ سرخِ نوشته در خطِ زیر، نه رنگِ عدد.
 */
@Composable
private fun KpiStat(
  label: String,
  value: Double,
  format: (Double) -> String,
  modifier: Modifier,
  hue: V3Hue = V3Hue.SKY,
  valueColor: androidx.compose.ui.graphics.Color? = null,
  caption: String? = null,
) {
  val icon = when (hue) {
    V3Hue.PURPLE, V3Hue.VIOLET -> Icons.Outlined.Payments
    V3Hue.ORANGE, V3Hue.AMBER -> Icons.Outlined.WarningAmber
    else -> Icons.Outlined.InsertChart
  }
  DeskKpiCard(DeskKpi(label, format(value), icon, DeskHues.tone(if (valueColor != null) V3Hue.AMBER else hue), fallback = caption), modifier)
}
