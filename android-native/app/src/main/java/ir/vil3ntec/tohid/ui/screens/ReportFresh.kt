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
import ir.vil3ntec.tohid.core.cost.CostMethod
import ir.vil3ntec.tohid.core.cost.CostPolicy
import ir.vil3ntec.tohid.core.keymap.KeyMap
import ir.vil3ntec.tohid.core.report.ReportBoard
import ir.vil3ntec.tohid.core.report.ReportTables
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

/**
 *  «گزارشِ تازه» — صفحه‌های گزارش و کارت‌های خانه روی هستهٔ مشترکِ گزارش
 *  (`core/report`، همان منطقِ وب) — RPT-01/02/03/06/09/11، v3 §۱۹.۵ بند ۲.
 *
 *  پشتِ تنظیمِ «گزارشِ تازه»، **پیش‌فرض خاموش**: خاموش ⇒ همان صفحه‌های امروزِ
 *  `ReportEngine` و داشبوردِ امروز، بی هیچ تغییری. انتخابِ هر دستگاه است (مثلِ
 *  نمایشِ عکس) و همگام نمی‌شود؛ دفتر و فرمتِ داده دست نمی‌خورد.
 */
object ReportFresh {
  private const val PREFS = "tohid-ui"
  private const val KEY = "report_fresh"
  private const val COST_KEY = "report_cost"

  val on = mutableStateOf(false)

  /**
   *  RPT-03: روشِ بهای سود و حاشیه (`CostPolicy` ِ core) — همین دستگاه، همگام نمی‌شود؛ پیش‌فرض
   *  همان رفتارِ امروز (قیمتِ خریدِ روی قلم). تا D-23 هر روشی «تقریبی» است.
   */
  val cost = mutableStateOf(CostPolicy.DEFAULT)
  private var saveCost: (CostMethod) -> Unit = {}

  /** مقصدِ کلیکِ کارت/نشانِ خانه — صفحهٔ گزارش آن را برمی‌دارد و پاک می‌کند */
  val preset = mutableStateOf<ReportBoard.Preset?>(null)
  private var loaded = false

  fun load(context: android.content.Context) {
    if (loaded) return
    loaded = true
    on.value = runCatching { context.getSharedPreferences(PREFS, 0).getBoolean(KEY, false) }.getOrDefault(false)
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
      Text("گزارشِ تازه", style = MaterialTheme.typography.labelLarge, color = Shop.colors.text, fontWeight = FontWeight.SemiBold)
      Text("آزمایشی · فقط روی همین دستگاه", style = MaterialTheme.typography.labelSmall, color = Shop.colors.muted)
    }
    Switch(
      checked = ReportFresh.on.value,
      onCheckedChange = { ReportFresh.set(context, it) },
      modifier = Modifier.semantics { contentDescription = "گزارشِ تازه" },
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
    ReportBoard.build(ReportBridge.ledgerOf(d), today, ReportBridge.deviceClock(), ReportFreshModel.costingOf(cost), money = showMoney)
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
          Modifier.clip(Shape.badge).clickable(role = Role.Button, onClickLabel = "نمایشِ فهرست") { ReportFresh.open(p.target, onOpen) },
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
  val tabs = remember(money) { ReportFreshModel.tabs(money) }
  val today = ir.vil3ntec.tohid.todayIso()
  val clock = remember { ReportBridge.deviceClock() }
  val ledger = remember(d) { ReportBridge.ledgerOf(d) }

  var tab by remember { mutableStateOf(tabs.first().id) }
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
  val view = remember(ledger, def.id, span, dim, today, cost) {
    ReportFreshModel.view(ledger, def.id, span, dim, today, clock, ReportFreshModel.costingOf(cost))
  }
  val viewMode = rememberViewMode("report-fresh", ScreenKind.MANAGEMENT)
  val name = "گزارش-${def.label}-$today".replace(' ', '-')

  fun sheet() = ReportCsv.Sheet(name, ReportFreshModel.exportRows(view.tables))
  fun print() = ir.vil3ntec.tohid.print.ReportPrint.print(context, sheet(), name)
  fun excel() { Platform.saveFile("$name.xlsx", Xlsx.of(sheet()))?.let { scope.launch { snackbar.showSnackbar("ذخیره شد: $it") } } }

  Column(
    Modifier
      .fillMaxSize()
      .onPreviewKeyEvent { e ->
        when {
          e.matches(KeyMap.GLOBAL_PRINT) -> { print(); true }
          e.matches(KeyMap.GLOBAL_EXPORT) -> { excel(); true }
          else -> false
        }
      }
      .padding(16.dp),
    verticalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    /* ۱ ─ عنوان، توضیح، خروجی و عملِ اصلی */
    Row(verticalAlignment = Alignment.CenterVertically) {
      Column(Modifier.weight(1f)) {
        Text("گزارش‌ها", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = Shop.colors.text)
        Text(
          "از هستهٔ مشترکِ گزارش (همان عددهای وب). سود تا تعیینِ روشِ بهای تمام‌شده تقریبی است.",
          style = MaterialTheme.typography.bodySmall, color = Shop.colors.muted,
        )
      }
      ReportFreshSwitch()
      Spacer(Modifier.width(12.dp))
      OutlinedButton(onClick = ::print) { Text("چاپ  ${KeyMap.GLOBAL_PRINT.hint}") }
      Spacer(Modifier.width(6.dp))
      Button(onClick = ::excel) { Text("اکسل  ${KeyMap.GLOBAL_EXPORT.hint}") }
    }

    /* ۲ ─ نوارِ ابزار: زبانه، بازه، تفکیک، نما */
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
      SegmentedTabs(tabs.map { SegmentTab(it.id, it.label) }, def.id, { tab = it })
      if (def.ranged) SegmentedTabs(ReportBoard.SPANS.map { SegmentTab(it, ReportBoard.SPAN_LABELS.getValue(it)) }, span, { span = it })
      Spacer(Modifier.weight(1f))
      ViewModeToggle(viewMode)
    }
    if (def.id == "sales" || def.id == "profit") {
      SegmentedTabs(ReportBoard.DIMS.map { SegmentTab(it, ReportBoard.DIM_LABELS.getValue(it)) }, dim, { dim = it })
    }

    /* ۳ ─ کارت‌های آماریِ همین زبانه */
    Summary(view, def.id, d, money)

    /* ۴ ─ جدول‌ها */
    if (view.tables.isEmpty()) Text("گزارشی برای نمایش نیست.", color = Shop.colors.muted)
    view.tables.forEach { t ->
      if (view.tables.size > 1) Text(t.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, color = Shop.colors.text)
      if (viewMode.mode == ViewMode.TABLE) FreshTable(t, Modifier.weight(1f).fillMaxWidth())
      else FreshCards(t, Modifier.weight(1f).fillMaxWidth())
    }
  }
}

@Composable
private fun Summary(view: ReportFreshModel.View, tab: String, d: ShopData, showMoney: Boolean) {
  @Composable fun card(title: String, value: String, period: String, m: Modifier) = StatCard(title, value, period, m)
  fun af(v: Long) = "${money(v / 100.0)} ؋"
  when (tab) {
    "today" -> HomeKpis(d, {}, showMoney, columns = 5)
    "sales" -> view.sales?.let { s ->
      val p = if (s.from == s.to) formatDate(s.from) else if (s.from == "0000-01-01") "همهٔ تاریخ‌ها" else "${formatDate(s.from)} تا ${formatDate(s.to)}"
      Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        card("فروش خالص", af(s.totals.netSales), p, Modifier.weight(1f))
        card("فاکتور", s.totals.invoices.toString(), p, Modifier.weight(1f))
        card("تخفیف", af(s.totals.discount), p, Modifier.weight(1f))
        card("برگشتی", af(s.totals.returns), p, Modifier.weight(1f))
        card("میانگین فاکتور", af(s.totals.avgTicket), p, Modifier.weight(1f))
      }
    }
    "profit" -> view.sales?.let { s ->
      val p = if (s.from == s.to) formatDate(s.from) else if (s.from == "0000-01-01") "همهٔ تاریخ‌ها" else "${formatDate(s.from)} تا ${formatDate(s.to)}"
      //  RPT-03: روشِ بها همان که هسته با آن حساب کرد (`s.costing`)، نه حدسِ صفحه
      val m = CostPolicy.parse(s.costing)
      Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
          card("فروش خالص", af(s.totals.netSales), p, Modifier.weight(1f))
          card(CostPolicy.label("بهای تمام‌شده", m), af(s.totals.cogs), p, Modifier.weight(1f))
          card(CostPolicy.label("سود ناخالص", m), af(s.totals.grossProfit), p, Modifier.weight(1f))
          card(CostPolicy.label("حاشیه", m), s.totals.marginBp?.let(ReportFreshModel::percent) ?: "—", p, Modifier.weight(1f))
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          Text("روش بها", style = MaterialTheme.typography.labelLarge, color = Shop.colors.text)
          SegmentedTabs(CostMethod.entries.map { SegmentTab(it, it.label) }, m, { ReportFresh.setCost(it) })
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
          if (s.totals.profitApproximate) StatusPill("سود تقریبی", PillTone.WARNING) else StatusPill("سود قطعی", PillTone.SUCCESS)
          Spacer(Modifier.width(8.dp))
          Text(
            CostPolicy.explain(m) + " " +
              (if (s.totals.costEstimatedLines > 0) "${s.totals.costEstimatedLines} قلم بی بهای خرید است." else ""),
            style = MaterialTheme.typography.bodySmall, color = Shop.colors.muted,
          )
        }
      }
    }
    "debts" -> view.debts?.let { x ->
      val late = x.customers.rows.count { (it.buckets.lastOrNull() ?: 0L) > 0 }
      Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        card("طلب از مشتریان", af(x.customers.totals.balance), "تا امروز", Modifier.weight(1f))
        card("مشتریانِ بدهکار", x.customers.rows.count { it.balance > 0 }.toString(), "تا امروز", Modifier.weight(1f))
        card("قرضِ بیش از ${x.buckets.lastOrNull() ?: 0} روز", late.toString(), "مشتری", Modifier.weight(1f))
        card("بدهی به تأمین‌کنندگان", af(x.suppliers.totals.balance), "تا امروز", Modifier.weight(1f))
      }
    }
    "fraud" -> view.fraud?.let { f ->
      val p = if (f.from == f.to) formatDate(f.from) else if (f.from == "0000-01-01") "همهٔ تاریخ‌ها" else "${formatDate(f.from)} تا ${formatDate(f.to)}"
      Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        card("لغوِ فاکتور", f.totals.voidCount.toString(), p, Modifier.weight(1f))
        card("برگشتی", f.totals.returnCount.toString(), p, Modifier.weight(1f))
        card("حذفِ ردیف", f.totals.lineDeleteCount.toString(), p, Modifier.weight(1f))
        card("کشو بی فروش", f.totals.noSaleCount.toString(), p, Modifier.weight(1f))
        card("نیازمندِ تأیید", f.totals.overrideCount.toString(), p, Modifier.weight(1f))
      }
    }
  }
}

private fun colWidth(kind: String): Dp = when (kind) {
  "text" -> 190.dp
  "date" -> 110.dp
  "flag" -> 90.dp
  else -> 120.dp
}

/** جدولِ خط‌دار با سرستونِ مرتب‌کننده (کلیکِ دوم نزولی/صعودی) و ردیفِ جمعِ ثابت */
@Composable
private fun FreshTable(t: ReportTables.Table, modifier: Modifier) {
  var sort by remember(t.id) { mutableStateOf<Pair<Int, Boolean>?>(null) }
  val rows = remember(t, sort) { sort?.let { (c, desc) -> ReportBoard.sortRows(t.rows, c, desc) } ?: t.rows }
  val line = Shop.colors.border
  val width = t.columns.fold(0.dp) { a, c -> a + colWidth(c.kind) }
  @Composable fun RowScope.cell(i: Int, onClick: (() -> Unit)?, content: @Composable () -> Unit) {
    val c = t.columns[i]
    Box(
      Modifier.width(colWidth(c.kind)).fillMaxHeight().border(0.5.dp, line)
        .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClickLabel = "مرتب‌سازی با ${c.label}", onClick = onClick) else Modifier)
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
          Text("در این بازه چیزی نیست.", Modifier.width(width).padding(24.dp), color = Shop.colors.muted, textAlign = TextAlign.Center)
        }
        items(rows.size) { r ->
          Row(Modifier.height(IntrinsicSize.Min).background(if (r % 2 == 1) Shop.colors.surface2.copy(alpha = 0.4f) else Shop.colors.surface)) {
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
    Text("در این بازه چیزی نیست.", modifier.padding(24.dp), color = Shop.colors.muted, textAlign = TextAlign.Center)
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
  //  کلیکِ کارتِ خانه روی گوشی همین صفحه را باز می‌کند؛ مقصد این‌جا یکی است
  LaunchedEffect(ReportFresh.preset.value) { ReportFresh.preset.value = null }

  LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
    item {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Text("امروز و سنِ بدهی", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Shop.colors.text)
        ReportFreshSwitch()
      }
      Spacer(Modifier.height(8.dp))
      OutlinedButton(
        onClick = {
          val name = "گزارش-امروز-و-بدهی-$today"
          ir.vil3ntec.tohid.print.ReportPrint.print(context, ReportCsv.Sheet(name, ReportFreshModel.exportRows(todayTables + debts.tables)), name)
        },
        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
      ) { Text("چاپ / PDF") }
    }
    item { HomeKpis(d, {}, money, columns = 2) }
    phoneDebts(this, debts)
  }
}

private fun phoneDebts(scope: LazyListScope, v: ReportFreshModel.View) {
  val x = v.debts ?: return
  val labels = x.buckets.let { b -> var lo = 0L; b.map { hi -> "$lo–$hi روز".also { lo = hi + 1 } } + "$lo+ روز" }
  scope.item {
    Text("سنِ بدهیِ مشتری‌ها", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, color = Shop.colors.text)
    Spacer(Modifier.height(8.dp))
    labels.indices.chunked(2).forEach { line ->
      Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        line.forEach { i -> StatCard(labels[i], "${money(x.customers.totals.buckets[i] / 100.0)} ؋", "مانده", Modifier.weight(1f)) }
        if (line.size == 1) Spacer(Modifier.weight(1f))
      }
      Spacer(Modifier.height(10.dp))
    }
  }
  if (x.customers.rows.isEmpty()) scope.item { Text("هیچ مشتری‌ای قرض ندارد.", color = Shop.colors.muted) }
  scope.items(x.customers.rows, key = { it.id }) { r ->
    val lastBucket = r.buckets.lastOrNull() ?: 0L
    Column(
      Modifier.fillMaxWidth().clip(Shape.card).background(Shop.colors.surfaceSolid).border(1.dp, Shop.colors.border, Shape.card).padding(14.dp),
    ) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Text(r.name.ifBlank { "نامعلوم" }, Modifier.weight(1f), fontWeight = FontWeight.Bold, color = Shop.colors.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text("${money(r.balance / 100.0)} ؋", fontWeight = FontWeight.Bold, color = Shop.colors.text)
      }
      Spacer(Modifier.height(6.dp))
      Row(verticalAlignment = Alignment.CenterVertically) {
        when {
          r.balance <= 0 -> StatusPill("پیش‌پرداخت", PillTone.SUCCESS)
          lastBucket > 0 -> StatusPill("بیش از ${x.buckets.lastOrNull() ?: 0} روز", PillTone.DANGER)
          else -> StatusPill("در مهلت", PillTone.NEUTRAL)
        }
        Spacer(Modifier.width(8.dp))
        if ((r.oldestDays ?: -1) >= 0) Text("قدیمی‌ترین: ${r.oldestDays} روز", style = MaterialTheme.typography.bodySmall, color = Shop.colors.muted)
      }
    }
  }
}
