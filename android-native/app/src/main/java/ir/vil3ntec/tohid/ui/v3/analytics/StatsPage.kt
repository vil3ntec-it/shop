package ir.vil3ntec.tohid.ui.v3.analytics

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.LocaleChoice
import ir.vil3ntec.tohid.core.calendar.CivilDays
import ir.vil3ntec.tohid.core.calendar.SolarHijri
import ir.vil3ntec.tohid.core.i18n.I18n
import ir.vil3ntec.tohid.core.i18n.Str
import ir.vil3ntec.tohid.core.i18n.StrReports
import ir.vil3ntec.tohid.data.ShopData
import ir.vil3ntec.tohid.todayIso
import ir.vil3ntec.tohid.ui.screens.Haptics
import ir.vil3ntec.tohid.ui.theme.PhoneV3
import ir.vil3ntec.tohid.ui.theme.PhoneV3Palette
import ir.vil3ntec.tohid.ui.theme.PhoneV3Roles
import ir.vil3ntec.tohid.ui.theme.V3ButtonKind
import ir.vil3ntec.tohid.ui.v3.V3Card
import ir.vil3ntec.tohid.ui.v3.V3EmptyState
import ir.vil3ntec.tohid.ui.v3.V3Hue
import ir.vil3ntec.tohid.ui.v3.V3Scaffold
import ir.vil3ntec.tohid.ui.v3.V3Segmented
import ir.vil3ntec.tohid.ui.v3.V3Stat
import ir.vil3ntec.tohid.ui.v3.V3Type
import ir.vil3ntec.tohid.ui.v3.money.MoneyPaths
import ir.vil3ntec.tohid.ui.v3.rememberCountUp
import ir.vil3ntec.tohid.ui.v3.rememberV3Stagger
import ir.vil3ntec.tohid.ui.v3.v3Stagger
import java.util.Calendar

/**
 *  «آمار» — صفحهٔ دومِ خانه (`stats?period=`) به شکلِ stats-chart-ref با رنگ‌های VILL3N:
 *  عددِ درشت و تاریخِ ستونِ انتخاب‌شده، روز/هفته/ماه/سال، نمودارِ ستونی (ستونِ انتخاب‌شده برند،
 *  بقیه خنثی، زدن ⇐ عددِ دقیق)، فروش و «مصارف و خرید» ِ همان بازه، روزهای فروشِ این هفته و
 *  پخشِ ساعتی با پُرفروش‌ترین ساعت. همه از `ReportEngine` (`StatsModel`).
 *
 *  `embedded` ⇒ درونِ ورقه‌های خانه (عنوانِ وسط و `dots`)؛ وگرنه صفحهٔ خودش با برگشت.
 */
@Composable
fun StatsPage(
  d: ShopData,
  canSeeMoney: Boolean,
  periodArg: String?,
  onOpen: (String) -> Unit,
  bottomReserve: Dp,
  embedded: Boolean,
  onBack: (() -> Unit)? = null,
  dots: (@Composable () -> Unit)? = null,
  today: String = todayIso(),
  big: Boolean = false,
) {
  if (!embedded) {
    V3Scaffold(title = StrReports.statsTitle, onBack = onBack, largeTitle = false, bottomReserve = bottomReserve) { pad ->
      if (!canSeeMoney) V3EmptyState(Icons.Outlined.Lock, StrReports.moneyLocked, body = StrReports.moneyLockedBody, action = Str.goBack, onAction = onBack)
      else StatsBody(d, periodArg, onOpen, today, big, Modifier.padding(bottom = pad.calculateBottomPadding()), null)
    }
    return
  }
  val top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
  Box(Modifier.fillMaxSize().background(PhoneV3.colors.bg).padding(top = top + 10.dp, bottom = bottomReserve)) {
    StatsBody(d, periodArg, onOpen, today, big, Modifier, dots)
  }
}

@Composable
private fun StatsBody(d: ShopData, periodArg: String?, onOpen: (String) -> Unit, today: String, big: Boolean, modifier: Modifier, dots: (@Composable () -> Unit)?) {
  val c = PhoneV3.colors
  val context = LocalContext.current
  var periodId by rememberSaveable(periodArg) { mutableStateOf(StatsPeriod.of(periodArg).id) }
  val period = StatsPeriod.of(periodId)
  val live = remember(d, period, today) { StatsModel.bars(d, period, today) }
  //  عکسِ «عددِ بلند» ِ خانه (PhoneHomeShot.big) — همان ستون‌ها با عددِ میلیاردی
  val bars = if (big) live.map { it.copy(net = it.net * 1000 + 1_234_567_890.0) } else live
  var selected by remember(period, bars.size) { mutableStateOf(bars.lastIndex) }
  val sel = bars.getOrNull(selected)
  val spend = remember(d, sel) { sel?.let { StatsModel.spend(d, it.from, it.to) } ?: 0.0 }
  val streak = remember(d, today) { StatsModel.streak(d, today) }
  val bins = remember(d, sel) { sel?.let { StatsModel.hours(d, it.from, it.to, ::hourOf) }.orEmpty() }
  val peak = StatsModel.peak(bins)
  val grow = rememberGrowth(bars.size, true, period)
  val hourGrow = rememberGrowth(bins.size, true, period, selected)
  val shown = rememberCountUp(sel?.net ?: 0.0)
  val stagger = rememberV3Stagger()
  fun Modifier.appear(i: Int) = v3Stagger(stagger, "stats-$i", i)

  Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
    if (dots != null) {
      Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(StrReports.statsTitle, style = V3Type.headline, color = c.ink, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        dots()
      }
    }
    //  سر: عددِ درشتِ ستونِ انتخاب‌شده و تاریخش
    Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
      Icon(Icons.Outlined.BarChart, contentDescription = null, tint = PhoneV3Palette.iconActive(c), modifier = Modifier.size(26.dp))
      Spacer(Modifier.width(8.dp))
      Column(Modifier.weight(1f)) {
        Text(afn(shown), style = V3Type.largeTitle, color = c.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(StrReports.netSales + " · " + Str.invoicesCount(sel?.count ?: 0), style = V3Type.footnote, color = c.inkMuted)
      }
      Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Outlined.CalendarToday, contentDescription = null, tint = c.icon, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(4.dp))
        Text(sel?.let { bucketTitle(period, it, today) }.orEmpty(), style = V3Type.subhead, color = c.ink, maxLines = 2, textAlign = TextAlign.End, modifier = Modifier.width(120.dp))
      }
    }
    V3Segmented(
      listOf(StrReports.periodDay, StrReports.periodWeek, StrReports.periodMonth, StrReports.periodYear),
      StatsPeriod.entries.indexOf(period),
      { periodId = StatsPeriod.entries[it].id; Haptics.confirm(context) },
    )
    V3Card(Modifier.appear(0), padding = PaddingValues(start = 14.dp, end = 14.dp, top = 14.dp, bottom = 12.dp)) {
      Legend(listOf(chartColor(c, ChartSeries.SALES) to StrReports.legendSelected, chartColor(c, ChartSeries.NEUTRAL) to StrReports.legendOthers))
      Spacer(Modifier.height(10.dp))
      StatsBarChart(bars.map { it.net }, bars.map { bucketLabel(period, it) }, selected, grow, { selected = it; Haptics.confirm(context) }, Modifier.fillMaxWidth().height(230.dp))
      if (bars.all { it.net <= 0.0 }) Text(StrReports.statsEmpty, style = V3Type.footnote, color = c.inkMuted, modifier = Modifier.padding(top = 8.dp))
      else Text(StrReports.tapHint, style = V3Type.caption, color = c.inkMuted, modifier = Modifier.padding(top = 8.dp))
    }
    Row(Modifier.appear(1), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
      V3Stat(StrReports.seriesSales, sel?.net ?: 0.0, ::afn, Modifier.weight(1f), hue = V3Hue.BRAND, onClick = { onOpen(AnalyticsPaths.reports(ReportTab.PROFIT.id)) })
      V3Stat(StrReports.seriesSpend, spend, ::afn, Modifier.weight(1f), hue = V3Hue.PURPLE, onClick = { onOpen(MoneyPaths.EXPENSES) })
    }
    StreakCard(streak, Modifier.appear(2))
    HoursCard(bins, peak, hourGrow, sel?.count ?: 0, Modifier.appear(3))
    Spacer(Modifier.height(12.dp))
  }
}

/** «روزهای فروشِ این هفته»: هر روزِ هفتهٔ دکان با تیک اگر فاکتوری داشت */
@Composable
private fun StreakCard(days: List<StreakDay>, modifier: Modifier = Modifier) {
  val c = PhoneV3.colors
  val on = PhoneV3Roles.button(c, V3ButtonKind.PRIMARY)
  val sold = days.count { it.sold }
  val past = days.count { !it.future }
  V3Card(modifier, padding = PaddingValues(16.dp)) {
    CardTitle(Icons.Outlined.CalendarToday, StrReports.streakTitle)
    Spacer(Modifier.height(14.dp))
    Row(Modifier.fillMaxWidth()) {
      days.forEach { day ->
        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
          val wd = CivilDays.isoWeekday(day.date) ?: 1
          Text(StrReports.weekdaysShort[wd - 1], style = V3Type.caption, color = if (day.future) c.inkMuted else c.ink, maxLines = 1)
          Spacer(Modifier.height(8.dp))
          Box(
            Modifier.size(34.dp).clip(CircleShape).background(if (day.sold) on.bg else c.tile),
            contentAlignment = Alignment.Center,
          ) { if (day.sold) Icon(Icons.Outlined.Check, contentDescription = StrReports.soldThatDay, tint = on.fg, modifier = Modifier.size(18.dp)) }
        }
      }
    }
    Spacer(Modifier.height(12.dp))
    Text(
      if (sold == 0) StrReports.streakNone else StrReports.streakCaption(sold, past),
      style = V3Type.callout, color = c.inkMuted, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(),
    )
  }
}

/** پخشِ ساعتیِ فاکتورهای ستونِ انتخاب‌شده با پُرفروش‌ترین ساعت */
@Composable
private fun HoursCard(bins: List<HourBin>, peak: HourBin?, grow: Growth, count: Int, modifier: Modifier = Modifier) {
  val c = PhoneV3.colors
  V3Card(modifier, padding = PaddingValues(16.dp)) {
    Row(verticalAlignment = Alignment.CenterVertically) {
      Box(Modifier.weight(1f)) { CardTitle(Icons.Outlined.Schedule, StrReports.hoursTitle) }
      Legend(listOf(chartColor(c, ChartSeries.SALES) to StrReports.peakHour))
    }
    Text(Str.invoicesCount(count), style = V3Type.headline, color = c.ink, modifier = Modifier.padding(top = 6.dp))
    Spacer(Modifier.height(12.dp))
    if (bins.isEmpty()) Text(StrReports.noSalesInRange, style = V3Type.callout, color = c.inkMuted)
    else {
      HourStrip(bins, peak, grow)
      if (peak != null) {
        Spacer(Modifier.height(10.dp))
        Text(StrReports.peakLine(hourSpan(peak), Str.invoicesCount(peak.count), afn(peak.net)), style = V3Type.callout, color = c.ink)
      }
    }
  }
}

@Composable
private fun CardTitle(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String) {
  val c = PhoneV3.colors
  Row(verticalAlignment = Alignment.CenterVertically) {
    Icon(icon, contentDescription = null, tint = c.icon, modifier = Modifier.size(20.dp))
    Spacer(Modifier.width(8.dp))
    Text(text, style = V3Type.headline, color = c.ink)
  }
}

/** برچسبِ زیرِ ستون: روزِ هفته، آغازِ هفته (ماه/روز)، نامِ ماه یا سال */
private fun bucketLabel(period: StatsPeriod, b: StatsBar): String = when (period) {
  StatsPeriod.DAY -> StrReports.weekdaysInitial[(CivilDays.isoWeekday(b.from) ?: 1) - 1]
  StatsPeriod.WEEK -> SolarHijri.toSolar(b.from)?.let { "${it.month}/${it.day}" }.orEmpty()
  StatsPeriod.MONTH -> SolarHijri.toSolar(b.from)?.let { I18n.solarMonths(I18n.lang, LocaleChoice.months)[it.month - 1] }.orEmpty()
  StatsPeriod.YEAR -> SolarHijri.toSolar(b.from)?.year?.toString().orEmpty()
}

/** تاریخِ سر: «امروز · ۲ عقرب ۱۴۰۵»، «۲۶ میزان تا ۲ عقرب»، «عقرب ۱۴۰۵»، «سال ۱۴۰۵» */
private fun bucketTitle(period: StatsPeriod, b: StatsBar, today: String): String = when (period) {
  StatsPeriod.DAY -> if (b.from == today) "${Str.today} · ${uiDateLong(b.from)}" else uiDateLong(b.from)
  StatsPeriod.WEEK -> StrReports.rangeSpan(uiDateLong(b.from), uiDateLong(b.to))
  StatsPeriod.MONTH -> SolarHijri.toSolar(b.from)?.let { "${I18n.solarMonths(I18n.lang, LocaleChoice.months)[it.month - 1]} ${it.year}" }.orEmpty()
  StatsPeriod.YEAR -> SolarHijri.toSolar(b.from)?.let { StrReports.yearLabel(it.year) }.orEmpty()
}

/** ساعتِ محلیِ ثبتِ فاکتور */
private fun hourOf(ms: Long): Int = Calendar.getInstance().apply { timeInMillis = ms }.get(Calendar.HOUR_OF_DAY)
