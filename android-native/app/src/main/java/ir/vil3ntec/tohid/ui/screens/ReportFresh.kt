package ir.vil3ntec.tohid.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.ShopDayChoice
import ir.vil3ntec.tohid.core.calendar.ShopDay
import ir.vil3ntec.tohid.core.cost.CostMethod
import ir.vil3ntec.tohid.core.cost.CostPolicy
import ir.vil3ntec.tohid.core.i18n.Str
import ir.vil3ntec.tohid.core.i18n.StrBranches
import ir.vil3ntec.tohid.core.i18n.StrHome
import ir.vil3ntec.tohid.core.i18n.StrInvoices
import ir.vil3ntec.tohid.core.i18n.StrMoney
import ir.vil3ntec.tohid.core.i18n.StrOnline
import ir.vil3ntec.tohid.core.i18n.StrReach
import ir.vil3ntec.tohid.core.i18n.StrReportPages
import ir.vil3ntec.tohid.core.i18n.StrReports
import ir.vil3ntec.tohid.core.i18n.StrRisk
import ir.vil3ntec.tohid.core.i18n.StrSale
import ir.vil3ntec.tohid.core.i18n.StrSimpleAsk
import ir.vil3ntec.tohid.core.i18n.StrStock
import ir.vil3ntec.tohid.core.i18n.StrStockPages
import ir.vil3ntec.tohid.core.keymap.KeyMap
import ir.vil3ntec.tohid.core.report.ReportBoard
import ir.vil3ntec.tohid.core.report.ReportCore
import ir.vil3ntec.tohid.core.report.ReportTables
import ir.vil3ntec.tohid.core.calendar.CivilDays
import ir.vil3ntec.tohid.core.risk.Anomalies
import ir.vil3ntec.tohid.core.risk.CashierAlerts
import ir.vil3ntec.tohid.data.AnomalyBook
import ir.vil3ntec.tohid.data.ReportBridge
import ir.vil3ntec.tohid.data.ReportCsv
import ir.vil3ntec.tohid.data.ShopData
import ir.vil3ntec.tohid.data.Xlsx
import ir.vil3ntec.tohid.formatDate
import ir.vil3ntec.tohid.money
import ir.vil3ntec.tohid.ui.Platform
import ir.vil3ntec.tohid.ui.components.common.PillTone
import ir.vil3ntec.tohid.ui.components.common.ScreenKind
import ir.vil3ntec.tohid.ui.components.common.SegmentTab
import ir.vil3ntec.tohid.ui.components.common.SegmentedTabs
import ir.vil3ntec.tohid.ui.components.common.StatCard
import ir.vil3ntec.tohid.ui.components.common.StatDelta
import ir.vil3ntec.tohid.ui.components.common.StatusPill
import ir.vil3ntec.tohid.ui.components.common.Trend
import ir.vil3ntec.tohid.ui.components.common.ViewMode
import ir.vil3ntec.tohid.ui.components.common.ViewModeToggle
import ir.vil3ntec.tohid.ui.components.common.rememberViewMode
import ir.vil3ntec.tohid.ui.theme.Shape
import ir.vil3ntec.tohid.ui.theme.Shop
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive

/**
 *  «گزارشِ تازه» — صفحه‌های گزارش و کارت‌های خانه روی هستهٔ مشترکِ گزارش
 *  (`core/report`، همان منطقِ وب) — RPT-01/02/03/06/09/11، v3 §۱۹.۵ بند ۲.
 *
 *  پشتِ تنظیمِ «گزارشِ تازه» ِ هر دستگاه (همگام نمی‌شود؛ دفتر دست نمی‌خورد)؛ تصمیمِ ۳-۲۲: **پیش‌فرض روشن** ([stored])، خاموشِ
 *  خودِ دستگاه می‌ماند. خاموش ⇒ همان صفحه‌های `ReportEngine` و داشبوردِ پیشین، بی هیچ تغییری.
 */
object ReportFresh {
  private const val PREFS = "tohid-ui"
  private const val KEY = "report_fresh"
  private const val COST_KEY = "report_cost"

  val on = mutableStateOf(true)
  fun stored(saved: Boolean?): Boolean = saved ?: true // تصمیمِ ۳-۲۲: هرگز ذخیره نشده (نصبِ تازه یا پیش از این نسخه) ⇒ روشن

  /**
   *  RPT-03: روشِ بهای سود و حاشیه (`CostPolicy` ِ core) — همین دستگاه، همگام نمی‌شود؛ پیش‌فرض
   *  روشِ مصوبِ D-23 (میانگینِ موزون)؛ «قیمت خرید روی قلم» انتخابی و «تقریبی».
   */
  val cost = mutableStateOf(CostPolicy.DEFAULT)
  private var saveCost: (CostMethod) -> Unit = {}

  /** مقصدِ کلیکِ کارت/نشانِ خانه — صفحهٔ گزارش آن را برمی‌دارد و پاک می‌کند */
  val preset = mutableStateOf<ReportBoard.Preset?>(null)
  private var loaded = false

  fun load(context: android.content.Context) {
    if (loaded) return
    loaded = true
    on.value = runCatching { context.getSharedPreferences(PREFS, 0).let { stored(if (it.contains(KEY)) it.getBoolean(KEY, true) else null) } }.getOrDefault(true)
    val app = context.applicationContext
    cost.value = CostPolicy.parse(runCatching { app.getSharedPreferences(PREFS, 0).getString(COST_KEY, null) }.getOrNull())
    saveCost = { m -> runCatching { app.getSharedPreferences(PREFS, 0).edit().putString(COST_KEY, m.id).apply() } }
  }

  fun setCost(m: CostMethod) {
    cost.value = m
    saveCost(m)
  }

  fun set(context: android.content.Context, value: Boolean) {
    on.value = value
    runCatching { context.getSharedPreferences(PREFS, 0).edit().putBoolean(KEY, value).apply() }
  }

  fun open(target: ReportBoard.Preset, onOpen: (String) -> Unit) {
    preset.value = target
    onOpen("reports")
  }
}

/** کلیدِ «گزارشِ تازه» — روی صفحهٔ گزارشِ قدیم و تازه، همان یک تنظیم */
@Composable
fun ReportFreshSwitch(modifier: Modifier = Modifier) {
  val context = LocalContext.current
  LaunchedEffect(Unit) { ReportFresh.load(context) }
  Row(modifier, verticalAlignment = Alignment.CenterVertically) {
    Column(Modifier.padding(end = 8.dp)) {
      Text(StrReach.freshOn, style = MaterialTheme.typography.labelLarge, color = Shop.colors.text, fontWeight = FontWeight.SemiBold)
      Text(StrReportPages.trialThisDevice, style = MaterialTheme.typography.labelSmall, color = Shop.colors.muted)
    }
    Switch(
      checked = ReportFresh.on.value,
      onCheckedChange = { ReportFresh.set(context, it) },
      modifier = Modifier.semantics { contentDescription = StrReach.freshOn },
    )
  }
}

private fun tone(t: String) = when (t) {
  "success" -> PillTone.SUCCESS
  "warning" -> PillTone.WARNING
  "danger" -> PillTone.DANGER
  else -> PillTone.NEUTRAL
}

private fun kpiText(k: ReportBoard.Kpi) = if (k.kind == "money") "${money(k.value / 100.0)} ؋" else k.value.toString()

private fun kpiDelta(k: ReportBoard.Kpi): StatDelta? {
  val text = k.deltaText ?: return null
  val trend = when (k.trend) { "up" -> Trend.UP; "down" -> Trend.DOWN; else -> Trend.FLAT }
  return StatDelta(text, trend, good = k.good == true)
}

/** تابلوی امروز از همان هسته — یک بار به ازای هر دفتر و روز */
@Composable
private fun rememberHome(d: ShopData, showMoney: Boolean): ReportBoard.Home {
  val today = ir.vil3ntec.tohid.todayIso()
  val cost = ReportFresh.cost.value
  return remember(d, today, showMoney, cost) {
    ReportBoard.build(ReportBridge.ledgerOf(d), today, ReportBridge.deviceClock(), ReportFreshModel.costingOf(cost), CostPolicy.approved(cost), money = showMoney)
  }
}

/**
 *  کارت‌های آماری و نشان‌های وضعیتِ خانه (v3 §۱۹.۵ بند ۲). هر کارت و نشان با کلیک
 *  صفحهٔ گزارش را روی همان فهرستِ فیلترشده باز می‌کند؛ عددِ کارت همان جمعِ آن فهرست است.
 *  دفترِ خالی ⇒ صفر، نه عددِ نمونه.
 */
@Composable
fun HomeKpis(d: ShopData, onOpen: (String) -> Unit, showMoney: Boolean, columns: Int, modifier: Modifier = Modifier) {
  val home = rememberHome(d, showMoney)
  Column(modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
    home.kpis.chunked(columns).forEach { line ->
      Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        line.forEach { k ->
          StatCard(
            title = k.title, value = kpiText(k), period = k.period, delta = kpiDelta(k), modifier = Modifier.weight(1f),
            onClick = { ReportFresh.open(k.target, onOpen) },
          )
        }
        repeat(columns - line.size) { Spacer(Modifier.weight(1f)) }
      }
    }
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
      home.pills.forEach { p ->
        StatusPill(
          p.text, tone(p.tone),
          Modifier.clip(Shape.badge).clickable(role = Role.Button, onClickLabel = Str.showList) { ReportFresh.open(p.target, onOpen) },
        )
      }
    }
  }
}

/* ══════════════════════════ کامپیوتر ══════════════════════════ */

/**
 *  صفحهٔ گزارشِ تازه — کامپیوتر. زبانه‌ها: امروز، فروش (شش تفکیک)، سود و حاشیه (تقریبی)،
 *  سنِ بدهی، ضدتقلب. جدول (مرتب‌شدنی با کلیک روی سرستون) یا کارت، اکسل و چاپ با همان
 *  کلیدهای مرکزی (Ctrl+E / Ctrl+P).
 */
@Composable
fun DeskFreshReports(d: ShopData, snackbar: SnackbarHostState) {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  val money = remember { ir.vil3ntec.tohid.data.ShopRole.canSeeMoney(context) }
  //  RPT-17: «شماره‌های جاافتاده» با همان تنظیمِ زبانهٔ «گزارش‌ها» (`DocGapsPref`)
  val gapsOn = remember { ir.vil3ntec.tohid.data.DocGapsPref.on(context) }
  //  PRM-16: «اثرِ تخفیف» فقط وقتی دفتر قلمِ تخفیفِ خودکار دارد؛ PRD-15: «بهای میانگین» — هر دو فقط برای کسی که پول را می‌بیند
  val promoHas = remember(d) { ir.vil3ntec.tohid.data.PromoEffectBook.has(d) }
  val tabs = remember(money, gapsOn, promoHas) {
    StockCostFresh.tabs(
      PromoFresh.tabs(DocGapsFresh.tabs(HeatmapModel.tabs(DayEndModel.tabs(ReportFreshModel.tabs(money), money), money), gapsOn), money, promoHas),
      money,
    )
  }
  val today = ir.vil3ntec.tohid.todayIso()
  val clock = remember { ReportBridge.deviceClock() }
  val ledger = remember(d) { ReportBridge.ledgerOf(d) }

  var tab by remember { mutableStateOf(tabs.first().id) }
  var day by remember { mutableStateOf(today) }
  var span by remember { mutableStateOf("month") }
  var dim by remember { mutableStateOf("product") }
  //  کلیکِ کارتِ خانه ⇒ همان زبانه، بازه و تفکیک
  val pending = ReportFresh.preset.value
  LaunchedEffect(pending) {
    if (pending != null) {
      if (tabs.any { it.id == pending.tab }) tab = pending.tab
      if (pending.span in ReportBoard.SPANS) span = pending.span
      if (pending.dim in ReportBoard.DIMS) dim = pending.dim
      ReportFresh.preset.value = null
    }
  }
  val def = tabs.firstOrNull { it.id == tab } ?: tabs.first()
  val cost = ReportFresh.cost.value
  val lineSeller = remember { ir.vil3ntec.tohid.data.LineSellerPref.on(context) } // POS-23: «کاربر» به فروشندهٔ هر ردیف
  //  RPT-13: − / + دوره و Ctrl+Shift+C «مقایسه با دورهٔ قبل» — همان `ReportKeysPref` ِ «گزارش‌ها» (کلاسِ A، پیش‌فرض روشن)؛
  //  فقط زبانه‌ای که بازه‌اش صریح ساخته می‌شود (`ReportFreshModel.SHIFTABLE`) و بازه‌ای که دورهٔ قبل دارد (نه سه‌ماهه و «همه»)
  val keys = remember { ir.vil3ntec.tohid.data.ReportKeysPref.on(context) }
  var shift by remember(span) { mutableStateOf(0) }
  var compare by remember { mutableStateOf(false) }
  val shifting = keys && def.ranged && def.id in ReportFreshModel.SHIFTABLE && ReportFreshModel.canShift(span, today, -1)
  val rng = remember(span, today, shift, shifting) { ReportFreshModel.rangeOf(span, today, if (shifting) shift else 0) }
  val comparing = keys && def.id in ReportFreshModel.COMPARABLE
  fun step(by: Int) { if (ReportFreshModel.canShift(span, today, shift + by)) shift += by }
  val view = remember(ledger, def.id, span, dim, today, cost, lineSeller, rng) {
    ReportFreshModel.view(ledger, def.id, span, dim, today, clock, ReportFreshModel.costingOf(cost), lineSeller, rng)
  }
  //  RPT-04: «پایانِ روز» از شیفت‌ها و دفتر (نه فقط `ReportLedger`)
  val dayEnd = remember(d, def.id, day) {
    if (def.id == DayEndModel.TAB_ID) DayEndModel.build(d, day, clock, ReportBridge.nowMs(), ReportBridge.offsetMinutes()) else null
  }
  //  RPT-08: «ساعت و روز» از فروش‌ها و برگشت‌های دفتر در همان بازه
  val heat = remember(d, def.id, span, today, ShopDayChoice.weekStart, rng) {
    if (def.id == HeatmapModel.TAB_ID) {
      HeatmapModel.build(d, rng, ReportBridge.offsetMinutes(), ShopDay.dayStartMinutes(ShopDayChoice.hours), ShopDayChoice.weekStart)
    } else null
  }
  //  RPT-04 / S48: ریزِ تراکنش‌های یک روش (کلیک یا Enter روی ردیفِ «نقد» ِ «پایانِ روز»)
  var drill by remember(day) { mutableStateOf<String?>(null) }
  //  RPT-13: ریزِ ردیفِ تفکیکِ فروش («فروش» و «سود و حاشیه»؛ کلیک یا Enter) ⇐ فاکتورهای همان ردیف
  var salesDrill by remember(def.id, span, dim) { mutableStateOf<ReportTables.Table?>(null) }
  //  RPT-13: ریزِ ردیفِ «امروز» (فاکتورهای همان روز)، «بدهی‌ها» (سندهای باز) و «تقلب» (رویدادهای همان کاربر)؛ جدول و یادداشت
  var rowDrill by remember(def.id, span) { mutableStateOf<Pair<ReportTables.Table, String>?>(null) }
  fun saleDoc(id: String) = DayEndModel.docLabel(d, "sale:$id")
  fun debtDoc(id: String): String =
    d.transactions.find { it.id == id }?.let { t -> t.refId?.let { r -> d.sales.find { it.id == r } }?.let { saleDoc(it.id) } ?: t.notes.trim().ifEmpty { StrReportPages.docDebt } }
      ?: d.purchases.find { it.id == id }?.let { p -> StrReportPages.docPurchase + p.notes.trim().let { if (it.isEmpty()) "" else " — $it" } }
      ?: ""
  val gaps = remember(d, def.id, today) { if (def.id == DocGapsFresh.TAB_ID) DocGapsFresh.tables(d, today) else null }
  //  PRM-16: اثرِ هر تخفیف با همان روشِ بهای «سود و حاشیه» (`ReportFresh.cost`)
  val promo = remember(d, ledger, def.id, span, today, cost) {
    if (def.id == PromoFresh.TAB_ID) PromoFresh.view(d, ledger, span, today, clock, cost) else null
  }
  //  PRD-15: بهای میانگینِ هر کالا و ارزشِ موجودی از همان بازپخشِ میانگینِ موزون؛ فقط خواندنی
  val stockCost = remember(ledger, def.id) {
    if (def.id == StockCostFresh.TAB_ID) ir.vil3ntec.tohid.core.report.ReportCore.stockCost(ledger) else null
  }
  val tables = dayEnd?.tables ?: heat?.let { HeatmapModel.tables(it) } ?: gaps ?: promo?.let { PromoFresh.tables(it) }
    ?: stockCost?.let { StockCostFresh.tables(it) } ?: view.tables
  val viewMode = rememberViewMode("report-fresh", ScreenKind.MANAGEMENT)
  val name = "گزارش-${def.label}-${if (dayEnd != null) day else today}".replace(' ', '-') // داده: نامِ پروندهٔ خروجی

  fun sheet() = ReportCsv.Sheet(name, ReportFreshModel.exportRows(tables))
  fun print() = ir.vil3ntec.tohid.print.ReportPrint.print(context, sheet(), name)
  fun excel() { Platform.saveFile("$name.xlsx", Xlsx.of(sheet()))?.let { scope.launch { snackbar.showSnackbar(StrSale.savedTo(it)) } } }
  //  RPT-11: PDF ِ مستقیم — همان جدول‌های چاپ، بی پنجرهٔ چاپ (فقط در «گزارشِ تازه»، پشتِ `report_fresh`)
  //  کامپیوتر: پنجرهٔ ذخیره؛ گوشی: «فرستادن» (همان راهِ PDF ِ فاکتور). ساختنِ صفحه‌ها بیرون از نخِ صفحه.
  fun pdf() {
    val s = sheet()
    scope.launch {
      val msg = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { ir.vil3ntec.tohid.print.ReportPrint.savePdf(context, s, name) }
      snackbar.showSnackbar(msg)
    }
  }

  Column(
    Modifier
      .fillMaxSize()
      .onPreviewKeyEvent { e ->
        when {
          e.matches(KeyMap.GLOBAL_PRINT) -> { print(); true }
          e.matches(KeyMap.GLOBAL_EXPORT) -> { excel(); true }
          shifting && e.matches(KeyMap.REPORTS_PREV_PERIOD) -> { step(-1); true }
          shifting && e.matches(KeyMap.REPORTS_NEXT_PERIOD) -> { step(1); true }
          comparing && e.matches(KeyMap.REPORTS_COMPARE) -> { compare = !compare; true }
          else -> false
        }
      }
      .padding(16.dp),
    verticalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    /* ۱ ─ عنوان، توضیح، خروجی و عملِ اصلی */
    Row(verticalAlignment = Alignment.CenterVertically) {
      Column(Modifier.weight(1f)) {
        Text(Str.reports, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = Shop.colors.text)
        Text(
          StrReportPages.sourceNote,
          style = MaterialTheme.typography.bodySmall, color = Shop.colors.muted,
        )
      }
      ReportFreshSwitch()
      Spacer(Modifier.width(12.dp))
      OutlinedButton(onClick = ::print) { Text(StrReportPages.printKey(KeyMap.GLOBAL_PRINT.hint)) }
      Spacer(Modifier.width(6.dp))
      OutlinedButton(onClick = ::pdf) { Text("PDF") }
      Spacer(Modifier.width(6.dp))
      Button(onClick = ::excel) { Text(StrReportPages.excelKey(KeyMap.GLOBAL_EXPORT.hint)) }
    }

    /* ۲ ─ نوارِ ابزار: زبانه، بازه، تفکیک، نما */
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
      SegmentedTabs(tabs.map { SegmentTab(it.id, it.label) }, def.id, { tab = it })
      if (def.id == DayEndModel.TAB_ID) {
        SegmentedTabs(DayEndModel.days(today).map { SegmentTab(it, DayEndModel.dayLabel(it, today, ::formatDate)) }, day, { day = it })
      }
      Spacer(Modifier.weight(1f))
      ViewModeToggle(viewMode)
    }
    //  بازه در ردیفِ خودش: با دو سه‌ماههٔ مالی (LOC-02) شش گزینه است و کنارِ پنج زبانه در ۱۲۸۰ جا نمی‌شد
    if (def.ranged) SegmentedTabs(ReportBoard.SPANS.map { SegmentTab(it, ReportBoard.SPAN_LABELS.getValue(it)) }, span, { span = it })
    //  RPT-13: دورهٔ قبل/بعد با برچسبِ کلید، «از … تا …» ِ بازهٔ جابه‌جاشده، «مقایسه» و خطِ «نسبت به دورهٔ قبل» (`ReportKeys.kt`)
    FreshPeriodBar(shifting, comparing, compare, rng, onStep = ::step, onCompare = { compare = !compare })
    if (comparing && compare) ReportCompareLine(d, def.id, ReportFreshModel.keysSpan(span), rng.from, rng.to, today)
    if (def.id == "sales" || def.id == "profit") {
      SegmentedTabs(ReportBoard.DIMS.map { SegmentTab(it, ReportBoard.DIM_LABELS.getValue(it)) }, dim, { dim = it })
    }

    /* ۳ ─ کارت‌های آماریِ همین زبانه */
    when {
      dayEnd != null -> DayEndSummary(dayEnd)
      heat != null -> HeatmapSummary(heat)
      gaps != null -> Unit   // RPT-17: شمارها در خودِ جدول‌اند
      promo != null -> PromoSummary(promo)
      stockCost != null -> StockCostSummary(stockCost)
      else -> Summary(view, def.id, d, money)
    }

    /* ۴ ─ جدول‌ها */
    if (tables.isEmpty()) Text(StrReportPages.noReport, color = Shop.colors.muted)
    tables.forEach { t ->
      if (tables.size > 1) Text(t.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, color = Shop.colors.text)
      val sales = view.sales
      val onRow: ((List<JsonPrimitive>) -> Unit)? = when {
        dayEnd != null && t.id == DayEndModel.METHODS_ID -> { row -> DayEndModel.methodOf(dayEnd.report, row.first().content)?.let { drill = it } }
        sales != null && (t.id.startsWith("sales.") || t.id == "profit") -> { row ->
          val dimNow = if (dim in ReportBoard.DIMS) dim else "product"
          //  POS-23: «فروشندهٔ هر ردیف» ⇒ ریزِ «کاربر» به فروشندهٔ ردیف؛ خاموش ⇒ همان فراخوانِ امروز
          salesDrill = if (lineSeller) ReportFreshModel.drill(ledger, sales, dimNow, t.rows.indexOf(row), row.first().content, clock, lineSeller = true, doc = ::saleDoc)
          else ReportFreshModel.drill(ledger, sales, dimNow, t.rows.indexOf(row), row.first().content, clock, ::saleDoc)
        }
        t.id == "today" -> { row ->
          ReportFreshModel.dayDrill(ledger, row, clock, ::saleDoc)?.let { rowDrill = it to StrReportPages.dayDrillNote }
        }
        view.debts != null && t.id.startsWith("debts.") -> { row ->
          ReportFreshModel.debtDrill(ledger, view.debts, t.id == "debts.suppliers", t.rows.indexOf(row), row.first().content, clock, ::debtDoc)
            ?.let { rowDrill = it to StrReportPages.debtDrillNote }
        }
        view.fraud != null && t.id == "fraud" -> { row ->
          ReportFreshModel.fraudDrill(ledger, view.fraud, t.rows.indexOf(row), clock, ::saleDoc)
            ?.let { rowDrill = it to StrReportPages.fraudDrillNote }
        }
        else -> null
      }
      if (viewMode.mode == ViewMode.TABLE) FreshTable(t, Modifier.weight(1f).fillMaxWidth(), onRow)
      else FreshCards(t, Modifier.weight(1f).fillMaxWidth())
    }
  }
  drill?.let { m ->
    val detail = remember(d, day, m) { DayEndModel.details(d, day, m, clock, ReportBridge.offsetMinutes()) }
    DayEndDetailDialog(detail) { drill = null }
  }
  salesDrill?.let { t ->
    DayEndDetailDialog(t, note = StrReportPages.salesDrillNote) { salesDrill = null }
  }
  rowDrill?.let { (t, note) -> DayEndDetailDialog(t, note = note) { rowDrill = null } }
}

@Composable
private fun Summary(view: ReportFreshModel.View, tab: String, d: ShopData, showMoney: Boolean) {
  @Composable fun card(title: String, value: String, period: String, m: Modifier) = StatCard(title, value, period, m)
  fun af(v: Long) = "${money(v / 100.0)} ؋"
  when (tab) {
    "today" -> HomeKpis(d, {}, showMoney, columns = 5)
    "sales" -> view.sales?.let { s ->
      val p = if (s.from == s.to) formatDate(s.from) else if (s.from == "0000-01-01") StrReports.allDates else StrSimpleAsk.range(formatDate(s.from), formatDate(s.to))
      Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        card(StrReports.netSales, af(s.totals.netSales), p, Modifier.weight(1f))
        card(StrInvoices.colInvoice, s.totals.invoices.toString(), p, Modifier.weight(1f))
        card(Str.discount, af(s.totals.discount), p, Modifier.weight(1f))
        card(StrBranches.returns, af(s.totals.returns), p, Modifier.weight(1f))
        card(StrReportPages.avgTicket, af(s.totals.avgTicket), p, Modifier.weight(1f))
      }
    }
    "profit" -> view.sales?.let { s ->
      val p = if (s.from == s.to) formatDate(s.from) else if (s.from == "0000-01-01") StrReports.allDates else StrSimpleAsk.range(formatDate(s.from), formatDate(s.to))
      //  RPT-03: روشِ بها همان که هسته با آن حساب کرد (`s.costing`)، نه حدسِ صفحه
      val m = CostPolicy.parse(s.costing)
      Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
          card(StrReports.netSales, af(s.totals.netSales), p, Modifier.weight(1f))
          card(CostPolicy.label(StrReportPages.cogs, m), af(s.totals.cogs), p, Modifier.weight(1f))
          card(CostPolicy.label(StrReports.grossProfit, m), af(s.totals.grossProfit), p, Modifier.weight(1f))
          card(CostPolicy.label(StrReportPages.margin, m), s.totals.marginBp?.let(ReportFreshModel::percent) ?: "—", p, Modifier.weight(1f))
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          Text(StrReportPages.costMethod, style = MaterialTheme.typography.labelLarge, color = Shop.colors.text)
          SegmentedTabs(CostMethod.entries.map { SegmentTab(it, it.label) }, m, { ReportFresh.setCost(it) })
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
          if (s.totals.profitApproximate) StatusPill(StrReportPages.profitApprox, PillTone.WARNING) else StatusPill(StrReportPages.profitFinal, PillTone.SUCCESS)
          Spacer(Modifier.width(8.dp))
          Text(
            CostPolicy.explain(m) + " " +
              (if (s.totals.costEstimatedLines > 0) StrReportPages.noCostLines(s.totals.costEstimatedLines) else ""),
            style = MaterialTheme.typography.bodySmall, color = Shop.colors.muted,
          )
        }
      }
    }
    "debts" -> view.debts?.let { x ->
      val late = x.customers.rows.count { (it.buckets.lastOrNull() ?: 0L) > 0 }
      Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        card(StrHome.receivablesCustomers, af(x.customers.totals.balance), StrReportPages.untilToday, Modifier.weight(1f))
        card(StrReportPages.owingCustomers, x.customers.rows.count { it.balance > 0 }.toString(), StrReportPages.untilToday, Modifier.weight(1f))
        card(StrReportPages.creditOver(x.buckets.lastOrNull() ?: 0), late.toString(), StrOnline.customer, Modifier.weight(1f))
        card(StrReportPages.owedToSuppliers, af(x.suppliers.totals.balance), StrReportPages.untilToday, Modifier.weight(1f))
      }
    }
    "fraud" -> view.fraud?.let { f ->
      val p = if (f.from == f.to) formatDate(f.from) else if (f.from == "0000-01-01") StrReports.allDates else StrSimpleAsk.range(formatDate(f.from), formatDate(f.to))
      val alerts = remember(f) { ReportBoard.alertsOf(f) }
      //  AI-08: تنظیمِ خاموشِ «تشخیصِ ناهنجاری» (`AnomalyPref`) ⇒ `null` و همان کادرِ RSK-10
      val anomalyOn = rememberAnomalyOn()
      val findings = remember(d, f, alerts, anomalyOn) {
        AnomalyBook.view(anomalyOn, d, f.from, f.to, alerts, ReportBridge.offsetMinutes(), ir.vil3ntec.tohid.todayIso())
      }
      Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
          card(StrReportPages.invoiceVoids, f.totals.voidCount.toString(), p, Modifier.weight(1f))
          card(StrBranches.returns, f.totals.returnCount.toString(), p, Modifier.weight(1f))
          card(StrStock.removeRow, f.totals.lineDeleteCount.toString(), p, Modifier.weight(1f))
          card(StrSale.noSaleOpens, f.totals.noSaleCount.toString(), p, Modifier.weight(1f))
          card(StrReportPages.needApproval, f.totals.overrideCount.toString(), p, Modifier.weight(1f))
        }
        if (findings != null) AnomalyBox(findings) else if (alerts.isNotEmpty()) CashierAlertBox(alerts)
      }
    }
  }
}

/** RSK-10 — هشدارِ رفتارِ غیرعادیِ صندوقدار: فقط نشان می‌دهد، چیزی را نمی‌بندد */
@Composable
private fun CashierAlertBox(alerts: List<CashierAlerts.Alert>) {
  fun kind(k: CashierAlerts.Kind) = when (k) {
    CashierAlerts.Kind.VOID -> StrRisk.voids
    CashierAlerts.Kind.DISCOUNT -> StrRisk.discounts
    CashierAlerts.Kind.NO_SALE -> StrRisk.noSales
  }
  Column(
    Modifier.fillMaxWidth().clip(Shape.card).border(1.dp, Shop.colors.danger, Shape.card).padding(12.dp),
    verticalArrangement = Arrangement.spacedBy(6.dp),
  ) {
    Row(verticalAlignment = Alignment.CenterVertically) {
      StatusPill(StrRisk.title(alerts.size), PillTone.DANGER)
      Spacer(Modifier.width(8.dp))
      Text(StrRisk.rule, style = MaterialTheme.typography.bodySmall, color = Shop.colors.muted)
    }
    alerts.forEach { a ->
      Text(
        StrRisk.line(a.key, kind(a.kind), a.count, a.invoices, a.peerCount, a.peerInvoices),
        style = MaterialTheme.typography.bodyMedium,
      )
    }
  }
}

/** AI-08 — یافته‌های تشخیصِ ناهنجاری (`core/risk/Anomalies`، هشدارهای RSK-10 هم در همین فهرست): فقط «نیازمندِ بررسی» */
@Composable
private fun AnomalyBox(findings: List<Anomalies.Finding>) {
  val shown = 40
  fun tone(s: Anomalies.Severity) = when (s) {
    Anomalies.Severity.HIGH -> PillTone.DANGER
    Anomalies.Severity.MEDIUM -> PillTone.WARNING
    Anomalies.Severity.LOW -> PillTone.INFO
  }
  Column(
    Modifier.fillMaxWidth().clip(Shape.card).border(1.dp, if (findings.isEmpty()) Shop.colors.border else Shop.colors.warning, Shape.card).padding(12.dp),
    verticalArrangement = Arrangement.spacedBy(6.dp),
  ) {
    Row(verticalAlignment = Alignment.CenterVertically) {
      StatusPill(if (findings.isEmpty()) StrReportPages.nothingToReview else StrReportPages.needReview(findings.size), if (findings.isEmpty()) PillTone.SUCCESS else PillTone.WARNING)
      Spacer(Modifier.width(8.dp))
      Text(
        StrReportPages.anomalyNote,
        style = MaterialTheme.typography.bodySmall, color = Shop.colors.muted,
      )
    }
    findings.take(shown).forEach { x ->
      Row(verticalAlignment = Alignment.CenterVertically) {
        StatusPill(Anomalies.severityText(x.severity), tone(x.severity))
        Spacer(Modifier.width(8.dp))
        Text(
          (x.dayJdn?.let { formatDate(CivilDays.isoOf(it)) + " · " } ?: "") + x.reason,
          style = MaterialTheme.typography.bodyMedium,
        )
      }
    }
    if (findings.size > shown) Text(StrReportPages.andMore(findings.size - shown), style = MaterialTheme.typography.bodySmall, color = Shop.colors.muted)
  }
}

private fun colWidth(kind: String): Dp = when (kind) {
  "text" -> 190.dp
  "date" -> 110.dp
  "flag" -> 90.dp
  else -> 120.dp
}

/**
 *  جدولِ خط‌دار با سرستونِ مرتب‌کننده (کلیکِ دوم نزولی/صعودی) و ردیفِ جمعِ ثابت. [onRow] ⇒ هر ردیف دکمه است:
 *  کلیک، یا Tab تا ردیف و Enter (`KeyMap.REPORTS_DRILL`)، «ریز» ِ همان ردیف را باز می‌کند (RPT-04/RPT-13).
 */
@Composable
internal fun FreshTable(t: ReportTables.Table, modifier: Modifier, onRow: ((List<JsonPrimitive>) -> Unit)? = null) {
  var sort by remember(t.id) { mutableStateOf<Pair<Int, Boolean>?>(null) }
  val rows = remember(t, sort) { sort?.let { (c, desc) -> ReportBoard.sortRows(t.rows, c, desc) } ?: t.rows }
  val line = Shop.colors.border
  val width = t.columns.fold(0.dp) { a, c -> a + colWidth(c.kind) }
  @Composable fun RowScope.cell(i: Int, onClick: (() -> Unit)?, content: @Composable () -> Unit) {
    val c = t.columns[i]
    Box(
      Modifier.width(colWidth(c.kind)).fillMaxHeight().border(0.5.dp, line)
        .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClickLabel = StrReportPages.sortBy(c.label), onClick = onClick) else Modifier)
        .padding(horizontal = 8.dp, vertical = 6.dp),
      contentAlignment = if (c.kind == "text" || c.kind == "date") Alignment.CenterStart else Alignment.CenterEnd,
    ) { content() }
  }
  Box(modifier.clip(Shape.card).border(1.dp, line, Shape.card).horizontalScroll(rememberScrollState())) {
    Column(Modifier.width(width)) {
      Row(Modifier.height(IntrinsicSize.Min).background(Shop.colors.surface2)) {
        t.columns.forEachIndexed { i, c ->
          val mark = when { sort?.first != i -> ""; sort?.second == true -> " ▼"; else -> " ▲" }
          cell(i, onClick = { sort = if (sort?.first == i) i to !(sort?.second ?: false) else i to (c.kind != "text") }) {
            Text(c.label + mark, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = Shop.colors.text, maxLines = 1)
          }
        }
      }
      LazyColumn(Modifier.weight(1f, fill = false)) {
        if (rows.isEmpty()) item {
          Text(StrReports.tableEmpty, Modifier.width(width).padding(24.dp), color = Shop.colors.muted, textAlign = TextAlign.Center)
        }
        items(rows.size) { r ->
          val drill = onRow?.let { open ->
            Modifier
              .onPreviewKeyEvent { e -> if (e.matches(KeyMap.REPORTS_DRILL)) { open(rows[r]); true } else false }
              .clickable(role = Role.Button, onClickLabel = StrReportPages.detailOf(ReportFreshModel.display(rows[r].first(), t.columns[0].kind, ::formatDate))) { open(rows[r]) }
          } ?: Modifier
          Row(Modifier.height(IntrinsicSize.Min).background(if (r % 2 == 1) Shop.colors.surface2.copy(alpha = 0.4f) else Shop.colors.surface).then(drill)) {
            rows[r].forEachIndexed { i, v ->
              cell(i, null) {
                Text(
                  ReportFreshModel.display(v, t.columns[i].kind, ::formatDate), maxLines = 1, overflow = TextOverflow.Ellipsis,
                  style = MaterialTheme.typography.bodyMedium, color = Shop.colors.text,
                )
              }
            }
          }
        }
      }
      t.total?.let { tot ->
        Row(Modifier.height(IntrinsicSize.Min).background(Shop.colors.surface2)) {
          tot.forEachIndexed { i, v ->
            cell(i, null) { Text(if (v is JsonNull) "" else ReportFreshModel.display(v, t.columns[i].kind, ::formatDate), fontWeight = FontWeight.Bold, color = Shop.colors.text, maxLines = 1) }
          }
        }
      }
    }
  }
}

/** نمای کارتی: هر ردیف یک کارت با نام و ستون‌های کلیدیِ همان جدول (`cardKeys`) */
@Composable
private fun FreshCards(t: ReportTables.Table, modifier: Modifier) {
  val keys = remember(t.id) { ReportFreshModel.cardKeys(t.id).mapNotNull { k -> t.columns.indexOfFirst { it.key == k }.takeIf { it >= 0 } } }
  if (t.rows.isEmpty()) {
    Text(StrReports.tableEmpty, modifier.padding(24.dp), color = Shop.colors.muted, textAlign = TextAlign.Center)
    return
  }
  LazyVerticalGrid(GridCells.Adaptive(220.dp), modifier, horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
    items(t.rows) { row -> RowCard(t, row, keys) }
  }
}

@Composable
private fun RowCard(t: ReportTables.Table, row: List<kotlinx.serialization.json.JsonPrimitive>, keys: List<Int>) {
  Column(
    Modifier.fillMaxWidth().clip(Shape.card).background(Shop.colors.surfaceSolid).border(1.dp, Shop.colors.border, Shape.card).padding(14.dp),
    verticalArrangement = Arrangement.spacedBy(4.dp),
  ) {
    Text(
      ReportFreshModel.display(row[0], t.columns[0].kind, ::formatDate), style = MaterialTheme.typography.titleSmall,
      fontWeight = FontWeight.Bold, color = Shop.colors.text, maxLines = 1, overflow = TextOverflow.Ellipsis,
    )
    keys.forEach { i ->
      Row {
        Text(t.columns[i].label, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, color = Shop.colors.muted, maxLines = 1)
        Text(ReportFreshModel.display(row[i], t.columns[i].kind, ::formatDate), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = Shop.colors.text, maxLines = 1)
      }
    }
  }
}

/* ══════════════════════════ گوشی ══════════════════════════ */

/**
 *  گوشی: خلاصهٔ امروز (همان کارت‌ها و نشان‌های خانه) و سنِ بدهیِ مشتری‌ها به شکلِ کارت
 *  (گوشی جدولِ عریض ندارد). چاپ از همان دو جدولِ هسته.
 */
@Composable
fun PhoneFreshReports(d: ShopData) {
  val context = LocalContext.current
  val money = remember { ir.vil3ntec.tohid.data.ShopRole.canSeeMoney(context) }
  val today = ir.vil3ntec.tohid.todayIso()
  val clock = remember { ReportBridge.deviceClock() }
  val ledger = remember(d) { ReportBridge.ledgerOf(d) }
  val debts = remember(ledger, today) { ReportFreshModel.view(ledger, "debts", "all", "", today, clock) }
  val cost = ReportFresh.cost.value
  val todayTables = remember(ledger, today, money, cost) {
    if (money) ReportFreshModel.view(ledger, "today", "today", "", today, clock, ReportFreshModel.costingOf(cost)).tables else emptyList()
  }
  //  RPT-04: پایانِ امروز فقط برای کسی که پول را می‌بیند
  val dayEnd = remember(d, today, money) {
    if (money) DayEndModel.build(d, today, clock, ReportBridge.nowMs(), ReportBridge.offsetMinutes()) else null
  }
  //  RPT-08: «ساعت و روز» روی گوشی هم — همان نقشهٔ کامپیوتر، فقط برای کسی که پول را می‌بیند
  var heatSpan by remember { mutableStateOf(HeatmapModel.PHONE_SPANS.first()) }
  val heat = remember(d, heatSpan, today, money, ShopDayChoice.weekStart) {
    if (money) HeatmapModel.build(d, ReportBoard.range(heatSpan, today), ReportBridge.offsetMinutes(), ShopDay.dayStartMinutes(ShopDayChoice.hours), ShopDayChoice.weekStart) else null
  }
  //  PRD-15: ارزشِ موجودی به بهای میانگینِ موزون (فقط خواندنی)؛ PRM-16: اثرِ تخفیف‌های این ماه با همان روشِ بها —
  //  هر دو فقط برای کسی که پول را می‌بیند (همان کامپیوتر: «بهای میانگین» و «اثرِ تخفیف»)
  val stockCost = remember(ledger, money) { if (money) ReportCore.stockCost(ledger) else null }
  val promo = remember(d, ledger, today, money, cost) {
    if (money && ir.vil3ntec.tohid.data.PromoEffectBook.has(d)) PromoFresh.view(d, ledger, "month", today, clock, cost) else null
  }
  //  کلیکِ کارتِ خانه روی گوشی همین صفحه را باز می‌کند؛ مقصد این‌جا یکی است
  LaunchedEffect(ReportFresh.preset.value) { ReportFresh.preset.value = null }

  LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
    item {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Text(StrReportPages.todayAging, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Shop.colors.text)
        ReportFreshSwitch()
      }
      Spacer(Modifier.height(8.dp))
      OutlinedButton(
        onClick = {
          val name = "گزارش-امروز-و-بدهی-$today" // داده: نامِ پروندهٔ خروجی
          ir.vil3ntec.tohid.print.ReportPrint.print(context, ReportCsv.Sheet(name, ReportFreshModel.exportRows(todayTables + debts.tables + dayEnd?.tables.orEmpty())), name)
        },
        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
      ) { Text(StrMoney.printPdf) }
    }
    item { HomeKpis(d, {}, money, columns = 2) }
    dayEnd?.let { b -> item { DayEndPhone(b) } }
    heat?.let { h -> item { HeatmapPhone(h, heatSpan) { heatSpan = it } } }
    stockCost?.let { c -> item { StockCostPhone(c) } }
    promo?.let { v -> item { PromoPhone(v) } }
    phoneDebts(this, debts)
  }
}

/** PRD-15 روی گوشی: ارزشِ موجودی به بهای میانگین و قاعده؛ ریزِ هر کالا در صفحهٔ همان کالا */
@Composable
private fun StockCostPhone(c: ReportCore.StockCost) {
  Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
    StatCard(
      StrStockPages.stockValueAvg, "${money(c.totals.valueMinor / 100.0)} ؋",
      if (c.totals.approximate) StrReportPages.colApprox else StrReportPages.untilToday, Modifier.fillMaxWidth(),
    )
    Text(StockCostFresh.note(c), style = MaterialTheme.typography.bodySmall, color = Shop.colors.muted)
  }
}

/** PRM-16 روی گوشی: هر تخفیفِ این ماه یک کارت (جدولِ عریض فقط کامپیوتر) */
@Composable
private fun PromoPhone(v: PromoFresh.View) {
  Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
    Text(StrReportPages.promoTitle, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, color = Shop.colors.text)
    v.report?.rows?.forEach { x ->
      Column(
        Modifier.fillMaxWidth().clip(Shape.card).background(Shop.colors.surfaceSolid).border(1.dp, Shop.colors.border, Shape.card).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
      ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Text(x.name.ifEmpty { x.id }, Modifier.weight(1f), fontWeight = FontWeight.Bold, color = Shop.colors.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
          Text("${money(x.revenueMinor / 100.0)} ؋", fontWeight = FontWeight.Bold, color = Shop.colors.text)
        }
        Text(
          "${StrReportPages.colPromoDiscount}: ${money(x.discountMinor / 100.0)} ؋ · " +
            "${CostPolicy.label(StrReportPages.colProfit, v.method)}: ${x.profitMinor?.let { "${money(it / 100.0)} ؋" } ?: "—"}",
          style = MaterialTheme.typography.bodySmall, color = Shop.colors.muted,
        )
        if (x.profitApproximate) StatusPill(StrReportPages.profitApprox, PillTone.WARNING)
      }
    }
    Text(PromoFresh.note(v), style = MaterialTheme.typography.bodySmall, color = Shop.colors.muted)
  }
}

private fun phoneDebts(scope: LazyListScope, v: ReportFreshModel.View) {
  val x = v.debts ?: return
  val labels = x.buckets.let { b -> var lo = 0L; b.map { hi -> StrReportPages.dayRange(lo, hi).also { lo = hi + 1 } } + StrReportPages.daysPlus(lo) }
  scope.item {
    Text(StrReportPages.customerAging, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, color = Shop.colors.text)
    Spacer(Modifier.height(8.dp))
    labels.indices.chunked(2).forEach { line ->
      Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        line.forEach { i -> StatCard(labels[i], "${money(x.customers.totals.buckets[i] / 100.0)} ؋", StrSimpleAsk.vDebtRemaining, Modifier.weight(1f)) }
        if (line.size == 1) Spacer(Modifier.weight(1f))
      }
      Spacer(Modifier.height(10.dp))
    }
  }
  if (x.customers.rows.isEmpty()) scope.item { Text(StrReportPages.noCustomerOwes, color = Shop.colors.muted) }
  scope.items(x.customers.rows, key = { it.id }) { r ->
    val lastBucket = r.buckets.lastOrNull() ?: 0L
    Column(
      Modifier.fillMaxWidth().clip(Shape.card).background(Shop.colors.surfaceSolid).border(1.dp, Shop.colors.border, Shape.card).padding(14.dp),
    ) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Text(r.name.ifBlank { StrOnline.presShortUnknown }, Modifier.weight(1f), fontWeight = FontWeight.Bold, color = Shop.colors.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text("${money(r.balance / 100.0)} ؋", fontWeight = FontWeight.Bold, color = Shop.colors.text)
      }
      Spacer(Modifier.height(6.dp))
      Row(verticalAlignment = Alignment.CenterVertically) {
        when {
          r.balance <= 0 -> StatusPill(StrMoney.prepaid, PillTone.SUCCESS)
          lastBucket > 0 -> StatusPill(StrReports.laterThan(x.buckets.lastOrNull() ?: 0), PillTone.DANGER)
          else -> StatusPill(StrReportPages.onTime, PillTone.NEUTRAL)
        }
        Spacer(Modifier.width(8.dp))
        if ((r.oldestDays ?: -1) >= 0) Text(StrReportPages.oldestDays(r.oldestDays), style = MaterialTheme.typography.bodySmall, color = Shop.colors.muted)
      }
    }
  }
}
