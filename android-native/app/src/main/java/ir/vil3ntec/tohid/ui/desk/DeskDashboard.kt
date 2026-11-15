package ir.vil3ntec.tohid.ui.desk

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.CallReceived
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.MoveToInbox
import androidx.compose.material.icons.outlined.NorthEast
import androidx.compose.material.icons.outlined.PeopleOutline
import androidx.compose.material.icons.outlined.PointOfSale
import androidx.compose.material.icons.outlined.Savings
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.ShoppingBasket
import androidx.compose.material.icons.outlined.SouthWest
import androidx.compose.material.icons.automirrored.outlined.TrendingUp
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.LocaleChoice
import ir.vil3ntec.tohid.core.calendar.CivilDays
import ir.vil3ntec.tohid.core.calendar.SolarHijri
import ir.vil3ntec.tohid.core.i18n.I18n
import ir.vil3ntec.tohid.core.i18n.Str
import ir.vil3ntec.tohid.core.i18n.StrDesk
import ir.vil3ntec.tohid.core.i18n.StrHome
import ir.vil3ntec.tohid.core.i18n.StrMoney
import ir.vil3ntec.tohid.core.i18n.StrNav
import ir.vil3ntec.tohid.data.ReportEngine
import ir.vil3ntec.tohid.data.Sale
import ir.vil3ntec.tohid.data.SalesEngine
import ir.vil3ntec.tohid.data.ShopData
import ir.vil3ntec.tohid.money
import ir.vil3ntec.tohid.plain
import ir.vil3ntec.tohid.qty
import ir.vil3ntec.tohid.ui.AppNav
import ir.vil3ntec.tohid.ui.screens.HomeAlerts
import ir.vil3ntec.tohid.ui.v3.RowWhen
import ir.vil3ntec.tohid.ui.theme.tabular
import ir.vil3ntec.tohid.ui.v3.V3EmptyState
import ir.vil3ntec.tohid.ui.v3.analytics.AnalyticsPaths
import ir.vil3ntec.tohid.ui.v3.analytics.StatsModel
import ir.vil3ntec.tohid.ui.v3.sales.history.SaleTime
import ir.vil3ntec.tohid.ui.v3.shell.DashboardNumbers
import ir.vil3ntec.tohid.ui.v3.shell.HomeTrend
import ir.vil3ntec.tohid.ui.v3.shell.ShellTargets
import ir.vil3ntec.tohid.ui.v3.shell.TransactionPaths
import ir.vil3ntec.tohid.ui.v3.shell.Transactions
import ir.vil3ntec.tohid.ui.v3.shell.TxSheet
import ir.vil3ntec.tohid.ui.v3.shell.rememberHomeView

/**
 *  داشبوردِ خانهٔ کامپیوتر (`home` با `LocalDeskLayout`، e-desk-d1) — جای دو کادرِ گرادیانیِ گوشی‌گونه، چهار کاشیِ کوچک و
 *  فهرستِ «امروز»: سه ردیفِ خوانا روی پهنای مانیتور.
 *   1. چهار کارتِ عددِ کلیدی (فروش، سود، نقدِ صندوق، طلب از قرض‌داران) — تخت با رنگِ بخش، عددِ درشتِ هم‌پهنا، تغییر و خطِ ۷ روز.
 *   2. نمودارِ فروشِ روزانه (۷/۳۰ روز، هاور ⇒ روز و مبلغ) و «پرفروش‌ترین کالاها» (امروز/این هفته).
 *   3. جدولِ تراکنش‌های امروز (ساعت، فاکتور، مشتری، اقلام، مبلغ، پرداخت، وضعیت؛ زدن ⇒ برگهٔ اقلام) و ستونِ کارهای سریع با
 *      میان‌برِ همان `AppNav.SHORTCUTS`/Ctrl+K، و «کارهای فوری» فقط وقتی کاری فوری هست.
 *  همهٔ عددها از مدل‌های امروزِ خانه (`DashboardNumbers`، `HomeTrend`، `rememberHomeView`، `Transactions`، `ReportEngine`) — هیچ
 *  حسابِ تازه‌ای روی پول نیست ([DeskDashModel] فقط گروه‌بندی و برش). خانهٔ گوشی و پرونده‌های داشبوردِ گوشی دست نخوردند.
 */
@Composable
fun DeskDashboard(d: ShopData, onOpen: (String) -> Unit) {
  val c = Desk.colors
  val view = rememberHomeView(d)
  val today = ir.vil3ntec.tohid.todayIso()
  val nums = remember(d, today) { DashboardNumbers.of(d, today) }
  val trend = remember(d, today) { HomeTrend.of(d.sales, today, System.currentTimeMillis()) }
  val profitSeries = remember(d, today, view.canSeeMoney) { if (view.canSeeMoney) DeskDashModel.profitSeries(d, today, HomeTrend.DAYS) else emptyList() }
  val pressing = HomeAlerts.pressing(view.alerts)

  BoxWithConstraints(Modifier.fillMaxSize().background(c.app), contentAlignment = Alignment.TopCenter) {
    val wide = maxWidth >= 980.dp
    Column(
      Modifier
        .widthIn(max = DeskSize.contentMax)
        .fillMaxWidth()
        .verticalScroll(rememberScrollState())
        .padding(DeskSpace.page),
      verticalArrangement = Arrangement.spacedBy(DeskSpace.gap),
    ) {
      //  ── ۱: عددهای کلیدی ──
      val kpis = kpiCards(view, nums, trend, profitSeries)
      val targets = listOf(
        if (view.canSeeMoney) AnalyticsPaths.REPORTS else ShellTargets.HISTORY,
        if (view.canSeeMoney) AnalyticsPaths.REPORTS else ShellTargets.HISTORY,
        ShellTargets.heroMoney(safe = false),
        ShellTargets.MOST_DEBT,
      )
      if (wide) Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(DeskSpace.gap)) {
        kpis.forEachIndexed { i, k -> KpiSlot(k, i, view, targets[i], onOpen, Modifier.weight(1f).fillMaxHeight()) }
      } else Column(verticalArrangement = Arrangement.spacedBy(DeskSpace.gap)) {
        kpis.chunked(2).forEachIndexed { r, pair ->
          Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(DeskSpace.gap)) {
            pair.forEachIndexed { j, k -> KpiSlot(k, r * 2 + j, view, targets[r * 2 + j], onOpen, Modifier.weight(1f).fillMaxHeight()) }
          }
        }
      }

      //  ── ۲: نمودار و پرفروش‌ها ──
      if (wide) Row(Modifier.fillMaxWidth().height(344.dp), horizontalArrangement = Arrangement.spacedBy(DeskSpace.gap)) {
        SalesChartCard(d, today, onOpen, view.canSeeMoney, Modifier.weight(2f).fillMaxHeight())
        TopProductsCard(d, today, Modifier.weight(1f).fillMaxHeight())
      } else {
        SalesChartCard(d, today, onOpen, view.canSeeMoney, Modifier.fillMaxWidth().height(340.dp))
        TopProductsCard(d, today, Modifier.fillMaxWidth().height(368.dp))
      }

      //  ── ۳: تراکنش‌های امروز و کارهای سریع ──
      if (wide) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(DeskSpace.gap), verticalAlignment = Alignment.Top) {
        TodayTable(d, today, view.canSeeMoney, onOpen, Modifier.weight(2f))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(DeskSpace.gap)) {
          QuickActions(onOpen)
          if (pressing.isNotEmpty()) UrgentCard(pressing, onOpen)
        }
      } else {
        if (pressing.isNotEmpty()) UrgentCard(pressing, onOpen)
        TodayTable(d, today, view.canSeeMoney, onOpen, Modifier.fillMaxWidth())
        QuickActions(onOpen)
      }
    }
  }
}

/** چهار کارتِ بالا — نقش‌ها همان خانهٔ گوشی: کسی که پول را نمی‌بیند جای سود شمارِ فاکتورِ امروز را می‌بیند */
@Composable
private fun kpiCards(view: ir.vil3ntec.tohid.ui.v3.shell.HomeView, nums: DashboardNumbers, trend: HomeTrend.View, profitSeries: List<Double>): List<DeskKpi> {
  val sales = DeskKpi(
    label = StrHome.todaySales,
    value = money(trend.today),
    unit = "؋",
    icon = Icons.Outlined.PointOfSale,
    tone = DeskHues.tone("sale"),
    pct = trend.pct,
    versus = StrDesk.versusYesterdayNow,
    fallback = if (trend.today > 0) StrDesk.invoicesToday(plain(view.todayCount)) else StrDesk.noYesterday,
    spark = trend.series,
  )
  val profit = view.profit
  val second = if (profit != null) DeskKpi(
    label = if (profit < 0) StrHome.todayLoss else StrHome.todayProfit,
    value = money(kotlin.math.abs(profit)),
    unit = "؋",
    icon = Icons.AutoMirrored.Outlined.TrendingUp,
    tone = DeskHues.tone("reports"),
    fallback = DeskDashModel.marginPct(profit, trend.today)?.let { StrDesk.margin(plain(it)) } ?: StrDesk.noSalesNoMargin,
    spark = profitSeries,
  ) else DeskKpi(
    label = StrHome.todaySalesCount,
    value = plain(view.todayCount),
    icon = Icons.AutoMirrored.Outlined.ReceiptLong,
    tone = DeskHues.tone("sales"),
    fallback = StrDesk.invoicesToday(plain(view.todayCount)),
  )
  val cash = DeskKpi(
    label = StrDesk.cashNow,
    value = money(view.cash),
    unit = "؋",
    icon = Icons.Outlined.AccountBalanceWallet,
    tone = DeskHues.tone("receipts"),
    fallback = if (view.shiftOpen) StrDesk.shiftOpen else StrDesk.shiftClosed,
  )
  val owed = DeskKpi(
    label = StrHome.receivables,
    value = money(nums.totalDebt),
    unit = "؋",
    icon = Icons.Outlined.PeopleOutline,
    tone = DeskHues.tone("debtors"),
    fallback = if (nums.overdue.isNotEmpty()) StrDesk.overduePeople(plain(nums.overdue.size)) else StrDesk.debtorsPeople(plain(nums.owing.size)),
  )
  return listOf(sales, second, cash, owed)
}

/**
 *  یک کارتِ KPI در جای خودش. کارتِ «نقدِ صندوق» همان سه کارِ کارتِ صندوقِ پیشین را زیرِ عدد دارد (دریافت، فروشِ تازه،
 *  پرداخت) — هیچ کاری از خانه گم نمی‌شود.
 */
@Composable
private fun KpiSlot(k: DeskKpi, index: Int, view: ir.vil3ntec.tohid.ui.v3.shell.HomeView, target: String, onOpen: (String) -> Unit, modifier: Modifier) {
  if (index != 2) {
    DeskKpiCard(k, modifier, onClick = { onOpen(target) })
    return
  }
  DeskKpiCard(k, modifier, onClick = { onOpen(target) }) {
    MiniAction(StrHome.receive, Icons.Outlined.SouthWest, Modifier.weight(1f)) { onOpen(ShellTargets.RECEIVE) }
    MiniAction(StrHome.pay, Icons.Outlined.NorthEast, Modifier.weight(1f)) { onOpen(ShellTargets.PAY) }
  }
}

@Composable
private fun MiniAction(text: String, icon: androidx.compose.ui.graphics.vector.ImageVector, modifier: Modifier, onClick: () -> Unit) {
  val c = Desk.colors
  Row(
    modifier.height(DeskSize.controlSmall).deskClickable(label = text, rest = c.surfaceAlt, onClick = onClick).padding(horizontal = DeskSpace.x2),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.Center,
  ) {
    Icon(icon, contentDescription = null, tint = c.icon, modifier = Modifier.size(16.dp))
    Spacer(Modifier.width(6.dp))
    Text(text, style = DeskType.label, color = c.ink, maxLines = 1)
  }
}

/* ═══════════════════════════════ نمودارِ فروش ═══════════════════════════════ */

@Composable
private fun SalesChartCard(d: ShopData, today: String, onOpen: (String) -> Unit, canSeeMoney: Boolean, modifier: Modifier) {
  val c = Desk.colors
  var days by rememberSaveable { mutableStateOf(7) }
  val series = remember(d, today, days) { DeskDashModel.days(d, today, days) }
  val total = series.sumOf { it.total }
  val best = series.maxOfOrNull { it.total } ?: 0.0
  DeskCard(
    modifier,
    title = StrDesk.salesChart,
    action = {
      DeskSegmented(listOf(7 to StrDesk.days7, 30 to StrDesk.days30), days, { days = it })
      if (canSeeMoney) {
        Spacer(Modifier.width(DeskSpace.x2))
        DeskLink(Str.all, onClick = { onOpen(AnalyticsPaths.REPORTS) })
      }
    },
  ) {
    Row(verticalAlignment = Alignment.CenterVertically) {
      Text(StrDesk.rangeTotal(money(total)), style = DeskType.number, color = c.ink, maxLines = 1)
      Spacer(Modifier.width(DeskSpace.x4))
      if (best > 0) Text(StrDesk.bestDayLine(money(best)), style = DeskType.secondary, color = c.inkSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
    Spacer(Modifier.height(DeskSpace.x3))
    if (series.none { it.total > 0.0 }) {
      Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
        Text(StrHome.noSalesYet, style = DeskType.body, color = c.inkSecondary)
      }
    } else BarChart(series, today, Modifier.fillMaxWidth().weight(1f))
  }
}

/**
 *  ستون‌های فروشِ هر روز — زمان از چپ به راست (مثلِ هر نمودارِ زمانی، در راست‌به‌چپ هم)، امروز پررنگ، سه خطِ راهنمای افقی با
 *  برچسبِ کوتاه (12K)، و هاور ⇒ روز، مبلغ و شمارِ فاکتورِ همان روز. بی انیمیشن.
 */
@Composable
private fun BarChart(series: List<DeskDashModel.Day>, today: String, modifier: Modifier) {
  val c = Desk.colors
  val s = c.source
  val bar = s.chart.sales
  val density = LocalDensity.current
  var hover by remember(series) { mutableStateOf<Int?>(null) }
  val axisW = 44.dp
  val labelH = 20.dp
  val max = StatsModel.axisMax(series.maxOf { it.total })
  val labels = remember(series, I18n.lang) { series.map { DeskDashModel.dayLabel(it.iso, today, series.size) } }
  BoxWithConstraints(modifier) {
    val boxW = maxWidth
    val plotW = maxWidth - axisW
    val plotH = maxHeight - labelH
    //  محورِ عدد (سه خط) — راستِ نمودار در راست‌به‌چپ هم چپ می‌ماند تا با ستون‌ها یکی باشد
    androidx.compose.runtime.CompositionLocalProvider(androidx.compose.ui.platform.LocalLayoutDirection provides androidx.compose.ui.unit.LayoutDirection.Ltr) {
      Box(Modifier.fillMaxSize()) {
        for (k in 0..2) {
          val v = max * (2 - k) / 2.0
          Text(
            StatsModel.compact(v),
            Modifier.width(axisW - 6.dp).offset(y = plotH * (k / 2f) - 8.dp),
            style = DeskType.caption,
            color = c.inkSecondary,
            textAlign = TextAlign.End,
            maxLines = 1,
          )
        }
        Canvas(
          Modifier
            .offset(x = axisW)
            .width(plotW)
            .height(plotH)
            .pointerInput(series) {
              awaitPointerEventScope {
                while (true) {
                  val e = awaitPointerEvent()
                  val p = e.changes.firstOrNull()?.position
                  hover = when {
                    e.type == PointerEventType.Exit || p == null -> null
                    else -> (p.x / (size.width / series.size)).toInt().coerceIn(0, series.size - 1)
                  }
                }
              }
            }
            .semantics { contentDescription = StrDesk.salesChart },
        ) {
          val n = series.size
          val slot = size.width / n
          val bw = (slot * if (n > 10) 0.62f else 0.5f).coerceAtMost(48.dp.toPx())
          for (k in 0..2) {
            val y = size.height * k / 2f
            drawLine(c.grid, Offset(0f, y), Offset(size.width, y), strokeWidth = 1.dp.toPx())
          }
          series.forEachIndexed { i, day ->
            val h = (day.total / max * size.height).toFloat().coerceAtLeast(if (day.total > 0) 2.dp.toPx() else 0f)
            val x = i * slot + (slot - bw) / 2
            val isToday = day.iso == today
            val color = when {
              hover == i -> bar
              isToday -> bar
              else -> bar.copy(alpha = if (c.isDark) 0.55f else 0.45f)
            }
            drawRoundRect(color, topLeft = Offset(x, size.height - h), size = Size(bw, h), cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx()))
          }
        }
        //  برچسبِ روزها: ۷ روز همه؛ ۳۰ روز هر پنج روز و امروز
        series.forEachIndexed { i, day ->
          val text = labels[i] ?: return@forEachIndexed
          val slot = plotW / series.size
          Text(
            text,
            Modifier.offset(x = axisW + slot * i - 20.dp + slot / 2, y = plotH + 4.dp).width(40.dp),
            style = DeskType.caption,
            color = if (day.iso == today) c.ink else c.inkSecondary,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Clip,
          )
        }
        hover?.let { i ->
          val day = series[i]
          val slot = plotW / series.size
          val tip = StrDesk.chartTip(RowWhen.of(day.iso, today), money(day.total), plain(day.count))
          val centerX = with(density) { (axisW + slot * i + slot / 2).toPx() }
          val tipW = with(density) { 220.dp.toPx() }
          val totalW = with(density) { boxW.toPx() }
          val left = (centerX - tipW / 2).coerceIn(0f, (totalW - tipW).coerceAtLeast(0f))
          Box(Modifier.offset { IntOffset(left.toInt(), 0) }.width(220.dp), contentAlignment = Alignment.Center) {
            Text(
              tip,
              Modifier.clip(DeskRadius.controlShape).background(s.inverse).padding(horizontal = DeskSpace.x2, vertical = DeskSpace.x1),
              style = DeskType.secondary,
              color = s.onInverse,
              maxLines = 1,
            )
          }
        }
      }
    }
  }
}

/* ═══════════════════════════════ پرفروش‌ها ═══════════════════════════════ */

@Composable
private fun TopProductsCard(d: ShopData, today: String, modifier: Modifier) {
  val c = Desk.colors
  var week by rememberSaveable { mutableStateOf(false) }
  val rows = remember(d, today, week) { DeskDashModel.top(d, if (week) CivilDays.addDays(today, -6) ?: today else today, today, 5) }
  DeskCard(
    modifier,
    title = StrDesk.topProducts,
    action = { DeskSegmented(listOf(false to Str.today, true to StrDesk.thisWeek), week, { week = it }) },
  ) {
    if (rows.isEmpty()) {
      Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
        V3EmptyState(icon = Icons.Outlined.ShoppingBasket, title = if (week) StrDesk.noTopWeek else StrDesk.noTopToday)
      }
      return@DeskCard
    }
    //  ردیفِ آرام (قاعدهٔ فهرست‌ها): نام یک خط، زیرش «N فروخته» کم‌رنگ، مبلغ کم‌رنگ در پایان — بی نشانِ رنگی و بی نوارِ رنگی
    Column {
      rows.forEachIndexed { i, r ->
        if (i > 0) DeskDivider()
        Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
          Text(plain(i + 1), Modifier.width(20.dp), style = DeskType.secondary.tabular(), color = c.inkSecondary, maxLines = 1)
          Spacer(Modifier.width(DeskSpace.x2))
          Column(Modifier.weight(1f)) {
            Text(r.name, style = DeskType.body, color = c.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(StrDesk.soldQty(qty(r.qty) + (if (r.unit.isNotBlank()) " " + r.unit else "")), style = DeskType.secondary, color = c.inkSecondary, maxLines = 1)
          }
          Spacer(Modifier.width(DeskSpace.x2))
          Text("${money(r.total)} ؋", style = DeskType.table, color = c.inkSecondary, maxLines = 1)
        }
      }
    }
  }
}

/* ═══════════════════════════════ جدولِ امروز ═══════════════════════════════ */

@Composable
private fun TodayTable(d: ShopData, today: String, canSeeMoney: Boolean, onOpen: (String) -> Unit, modifier: Modifier) {
  val c = Desk.colors
  val rows = remember(d, today, canSeeMoney, I18n.lang) { DeskDashModel.rows(d, today, canSeeMoney, DeskDashModel.TABLE_ROWS) }
  var open by remember { mutableStateOf<Transactions.Tx?>(null) }
  val cols = remember(I18n.lang) {
    listOf(
      DeskColumn(StrDesk.colTime, width = 64.dp),
      DeskColumn(StrDesk.colInvoice, width = 92.dp),
      DeskColumn(StrDesk.colCustomer, weight = 1.6f),
      DeskColumn(StrDesk.colItems, width = 56.dp, end = true),
      DeskColumn(StrDesk.colAmount, width = 120.dp, end = true),
      DeskColumn(StrDesk.colPayment, width = 84.dp),
      DeskColumn(StrDesk.colStatus, width = 96.dp),
    )
  }
  DeskCard(
    modifier,
    title = StrDesk.todayTable,
    padding = PaddingValues(top = DeskSpace.card, bottom = DeskSpace.x2),
    action = { DeskLink(Str.all, onClick = { onOpen(TransactionPaths.LIST) }) },
  ) {
    if (rows.isEmpty()) {
      V3EmptyState(
        icon = Icons.AutoMirrored.Outlined.ReceiptLong,
        title = StrDesk.noTxToday,
        body = StrDesk.noTxTodayBody,
        action = StrHome.newSale,
        onAction = { onOpen(ShellTargets.NEW_SALE) },
        modifier = Modifier.fillMaxWidth().padding(vertical = DeskSpace.x4),
      )
      return@DeskCard
    }
    DeskTableHead(cols, Modifier.padding(horizontal = DeskSpace.x2))
    rows.forEach { r ->
      DeskTableRow(cols, label = r.tx.who, onClick = { open = r.tx }, modifier = Modifier.padding(horizontal = DeskSpace.x2)) { i ->
        //  قاعدهٔ فهرست‌های آرام: فقط نامِ طرف پررنگ؛ ساعت، شماره، مبلغ، پرداخت و وضعیت نوشتهٔ کم‌رنگ؛ تنها نشانِ رنگی نقطهٔ
        //  کوچکِ «نسیه» (کاری مانده: گرفتنِ پول)؛ لغوشده مبلغِ خط‌خورده — بی قرص و بی خطِ رنگارنگ
        when (i) {
          0 -> Text(r.time, style = DeskType.table, color = c.inkSecondary, maxLines = 1)
          1 -> Text(r.tx.invoice?.let { "#$it" } ?: "—", style = DeskType.table, color = c.inkSecondary, maxLines = 1)
          2 -> Text(r.tx.who, style = DeskType.body, color = if (r.tx.cancelled) c.inkSecondary else c.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
          3 -> Text(plain(r.tx.itemCount), style = DeskType.table, color = c.inkSecondary, maxLines = 1)
          4 -> Text(
            (if (r.tx.incoming) "" else "−") + "${money(r.tx.amount)} ؋",
            style = DeskType.table,
            color = c.inkSecondary,
            textDecoration = if (r.tx.cancelled) androidx.compose.ui.text.style.TextDecoration.LineThrough else null,
            maxLines = 1,
          )
          5 -> Row(verticalAlignment = Alignment.CenterVertically) {
            if (r.credit && !r.tx.cancelled) {
              Box(Modifier.size(6.dp).clip(DeskRadius.pillShape).background(c.attention))
              Spacer(Modifier.width(6.dp))
            }
            Text(r.payment, style = DeskType.body, color = c.inkSecondary, maxLines = 1)
          }
          else -> Text(statusText(r.status), style = DeskType.body, color = c.inkSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
      }
    }
  }
  open?.let { tx -> TxSheet(d, tx, onDismiss = { open = null }, onOpen = onOpen) }
}

/** وضعیتِ ردیف به نوشتهٔ کم‌رنگ (بی قرصِ رنگی) */
private fun statusText(s: DeskDashModel.Status): String = when (s) {
  //  حالتِ عادی هیچ (قاعدهٔ «وضعیت به متنِ آرام»)
  DeskDashModel.Status.DONE -> ""
  DeskDashModel.Status.CANCELLED -> StrMoney.cancelled
  DeskDashModel.Status.ONLINE -> StrHome.txOnline
  DeskDashModel.Status.PURCHASE -> StrDesk.purchase
}

/* ═══════════════════════════════ کارهای سریع و فوری ═══════════════════════════════ */

@Composable
private fun QuickActions(onOpen: (String) -> Unit) {
  val orders = ir.vil3ntec.tohid.ui.OrdersLive.enabled
  val newOrders = ir.vil3ntec.tohid.ui.OrdersLive.newCount
  DeskCard(Modifier.fillMaxWidth(), title = StrDesk.quickActions) {
    Column(verticalArrangement = Arrangement.spacedBy(DeskSpace.x2)) {
      DeskButton(StrHome.newSale, onClick = { onOpen(ShellTargets.NEW_SALE) }, modifier = Modifier.fillMaxWidth(), icon = Icons.Outlined.Add, kind = DeskButtonKind.PRIMARY, shortcut = "Ctrl+2")
      DeskButton(StrDesk.searchAll, onClick = { AppNav.palette.value = true }, modifier = Modifier.fillMaxWidth(), icon = Icons.Outlined.Search, shortcut = "Ctrl+K")
      Row(horizontalArrangement = Arrangement.spacedBy(DeskSpace.x2)) {
        DeskButton(StrHome.receive, onClick = { onOpen(ShellTargets.RECEIVE) }, modifier = Modifier.weight(1f), icon = Icons.Outlined.CallReceived)
        DeskButton(StrHome.stockEntry, onClick = { onOpen(ShellTargets.STOCK_ENTRY) }, modifier = Modifier.weight(1f), icon = Icons.Outlined.MoveToInbox)
      }
      Row(horizontalArrangement = Arrangement.spacedBy(DeskSpace.x2)) {
        DeskButton(StrHome.history, onClick = { onOpen(ShellTargets.HISTORY) }, modifier = Modifier.weight(1f), icon = Icons.Outlined.History, shortcut = "Ctrl+6")
        DeskButton(StrHome.pay, onClick = { onOpen(ShellTargets.PAY) }, modifier = Modifier.weight(1f), icon = Icons.Outlined.Savings)
      }
      //  همان کاشیِ «سفارش‌های آنلاین» ِ خانه: روشن ⇒ صف (با شمارِ تازه)، خاموش ⇒ «تنظیمات ← سفارشِ آنلاین»
      Box {
        DeskButton(
          StrNav.onlineOrders,
          onClick = { onOpen(if (orders) ShellTargets.ORDERS else ShellTargets.ORDERS_SETUP) },
          modifier = Modifier.fillMaxWidth(),
          icon = Icons.Outlined.ShoppingBasket,
        )
        if (orders && newOrders > 0) DeskBadge(newOrders, Modifier.align(Alignment.CenterEnd).padding(end = DeskSpace.x3), kind = DeskBadgeKind.ALERT)
      }
    }
  }
}

@Composable
private fun UrgentCard(alerts: List<HomeAlerts.Alert>, onOpen: (String) -> Unit) {
  val c = Desk.colors
  DeskCard(
    Modifier.fillMaxWidth(),
    title = StrHome.urgentNow,
    action = { DeskLink(Str.all, onClick = { onOpen(ShellTargets.notifications()) }) },
  ) {
    Column(verticalArrangement = Arrangement.spacedBy(DeskSpace.x1)) {
      alerts.take(4).forEach { a ->
        Row(
          Modifier.fillMaxWidth().heightIn(min = 44.dp).deskClickable(label = a.title) { onOpen(a.route) }.padding(horizontal = DeskSpace.x2, vertical = 6.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Box(Modifier.size(8.dp).clip(DeskRadius.pillShape).background(if (a.urgent) c.negative else c.attention))
          Spacer(Modifier.width(DeskSpace.x3))
          Column(Modifier.weight(1f)) {
            Text(a.title, style = DeskType.bodyStrong, color = c.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (a.body.isNotBlank()) Text(a.body, style = DeskType.secondary, color = c.inkSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
          }
          Spacer(Modifier.width(DeskSpace.x2))
          DeskLink(a.action, onClick = { onOpen(a.actionRoute) })
        }
      }
    }
  }
}

/* ═══════════════════════════════ مدل ═══════════════════════════════ */

/** گروه‌بندی و برشِ دادهٔ امروزِ خانه برای داشبوردِ کامپیوتر — خالص، بی Compose (آزمون: `DeskDashboardUiTest`) */
internal object DeskDashModel {
  const val TABLE_ROWS = 8

  @Immutable data class Day(val iso: String, val total: Double, val count: Int)

  @Immutable data class Top(val productId: String, val name: String, val qty: Double, val total: Double, val unit: String)

  enum class Status { DONE, CANCELLED, ONLINE, PURCHASE }

  @Immutable data class Row(val tx: Transactions.Tx, val time: String, val payment: String, val credit: Boolean, val status: Status)

  /** فروشِ هر روز در [n] روزِ تا امروز (کهنه‌ترین اول)؛ لغوشده نه — همان قاعدهٔ `HomeTrend` */
  fun days(d: ShopData, today: String, n: Int): List<Day> {
    val keys = (n - 1 downTo 0).map { CivilDays.addDays(today, -it) ?: today }
    val set = keys.toSet()
    val byDay = d.sales.asSequence().filter { it.status != "cancelled" && it.date in set }.groupBy { it.date }
    return keys.map { k -> byDay[k].orEmpty().let { Day(k, it.sumOf { s -> s.finalTotal }, it.size) } }
  }

  /** سودِ خالصِ هر روز در [n] روزِ تا امروز — همان `ReportEngine.sales` ِ گزارش‌ها، روز به روز */
  fun profitSeries(d: ShopData, today: String, n: Int): List<Double> =
    (n - 1 downTo 0).map { CivilDays.addDays(today, -it) ?: today }.map { ReportEngine.sales(d, it, it).netProfit }

  /** درصدِ سود از فروشِ امروز؛ فروشِ صفر ⇒ null */
  fun marginPct(profit: Double, sales: Double): Int? = if (sales <= 0.0) null else kotlin.math.round(profit / sales * 100.0).toInt()

  /** پرفروش‌ترین کالاهای بازهٔ [from]..[to] به مبلغ (فروشِ لغوشده نه، برگشتی کم می‌شود) */
  fun top(d: ShopData, from: String, to: String, n: Int): List<Top> {
    val sales = d.sales.asSequence().filter { it.status != "cancelled" && it.date in from..to }.map { it.id }.toSet()
    if (sales.isEmpty()) return emptyList()
    val units = d.products.associate { it.id to it.unit }
    return d.saleItems.asSequence().filter { it.saleId in sales }
      .groupBy { it.productId.ifBlank { "free:" + it.name } }
      .map { (key, items) ->
        val q = items.sumOf { it.quantity - it.returnedQty }
        val t = items.sumOf { it.totalPrice - it.returnedQty * it.unitPrice }
        Top(key, SalesEngine.itemName(d, items.first()), q, t, units[items.first().productId].orEmpty())
      }
      .filter { it.qty > 0.0 }
      .sortedByDescending { it.total }
      .take(n)
  }

  /** ردیف‌های جدولِ امروز — همان `Transactions.today` ِ خانه، با ساعت، روشِ پرداخت و وضعیت */
  fun rows(d: ShopData, today: String, purchases: Boolean, n: Int): List<Row> {
    val txs = Transactions.today(d, today, n = n, purchases = purchases)
    if (txs.isEmpty()) return emptyList()
    val ids = txs.mapNotNull { if (it.kind == Transactions.Kind.PURCHASE) null else it.refId }.toSet()
    val byId = d.sales.asSequence().filter { it.id in ids }.associateBy { it.id }
    return txs.map { tx ->
      val sale = if (tx.kind == Transactions.Kind.PURCHASE) null else byId[tx.refId]
      Row(
        tx = tx,
        time = SaleTime.clock(tx.at),
        payment = sale?.let(::paymentOf) ?: "—",
        credit = sale != null && sale.paymentMethod == "credit",
        status = when {
          tx.cancelled -> Status.CANCELLED
          tx.kind == Transactions.Kind.PURCHASE -> Status.PURCHASE
          tx.kind == Transactions.Kind.ONLINE -> Status.ONLINE
          else -> Status.DONE
        },
      )
    }
  }

  private fun paymentOf(s: Sale): String = when {
    (s.payments?.size ?: 0) > 1 -> StrDesk.payMixed
    s.paymentMethod == "credit" -> Str.credit
    s.paymentMethod == "card" -> StrDesk.payCard
    else -> Str.cash
  }

  /** برچسبِ زیرِ ستونِ روز: ۷ روز ⇒ «امروز»، «دیروز» یا شمارهٔ روز؛ ۳۰ روز ⇒ هر پنج روز و امروز (null ⇒ بی برچسب) */
  fun dayLabel(iso: String, today: String, n: Int): String? {
    val back = (0 until n).firstOrNull { CivilDays.addDays(today, -it) == iso } ?: return null
    if (n > 10 && back != 0 && back % 5 != 0) return null
    if (back == 0) return Str.today
    if (n <= 10 && back == 1) return ir.vil3ntec.tohid.core.i18n.StrKit.yesterday
    val gregorian = I18n.gregorian(I18n.lang, LocaleChoice.calendar)
    val day = if (gregorian) CivilDays.jdnOfIso(iso)?.let { CivilDays.gregorianOf(it).day } else SolarHijri.toSolar(iso)?.day
    return day?.toString()
  }
}
