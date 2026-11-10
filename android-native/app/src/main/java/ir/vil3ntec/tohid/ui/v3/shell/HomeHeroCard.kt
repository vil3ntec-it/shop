package ir.vil3ntec.tohid.ui.v3.shell

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.NorthEast
import androidx.compose.material.icons.outlined.SouthWest
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.ShopDayChoice
import ir.vil3ntec.tohid.core.i18n.StrHero
import ir.vil3ntec.tohid.core.i18n.StrHome
import ir.vil3ntec.tohid.data.ShopData
import ir.vil3ntec.tohid.money
import ir.vil3ntec.tohid.ui.screens.HomeAlerts
import ir.vil3ntec.tohid.ui.screens.Motion
import ir.vil3ntec.tohid.ui.screens.MotionSpec
import ir.vil3ntec.tohid.ui.screens.v3Text
import ir.vil3ntec.tohid.ui.theme.HeroColors
import ir.vil3ntec.tohid.ui.theme.PhoneV3
import ir.vil3ntec.tohid.ui.v3.rememberCountUp
import ir.vil3ntec.tohid.ui.v3.v3PagerItem
import ir.vil3ntec.tohid.ui.v3.v3Pressable
import kotlinx.coroutines.launch

/** بلندیِ کادرِ بالای خانه پیش از درشتیِ نوشته — کششِ پنهان همین را باز می‌کند (`HomeScreen`) */
val HERO_CARD_HEIGHT: Dp = 440.dp

private val HERO_RADIUS = 28.dp

/** یک خانهٔ آمارِ پایین؛ `up` = پیکانِ سبزِ ↗، `false` = سرخِ ↙، `null` = خنثی */
internal data class HeroStat(val label: String, val value: String, val up: Boolean?)

/** هر صفحهٔ کادر، آماده برای کشیدن */
internal data class HeroFace(
  val page: HomeHero.Page,
  val title: String,
  val label: String,
  /** `null` = پنهان (شمارشِ کورِ شاگرد) */
  val amount: Double?,
  val series: HomeHero.Series,
  /** عنوانِ بازه: امروز، هفته، ماه */
  val titles: List<String>,
  val empty: String,
  val first: HeroStat,
  val second: HeroStat,
)

/** دو صفحهٔ «صندوق» و «گاوصندوق» از دادهٔ واقعی (`HomeView`، `HeroInput`) — عددِ تازه‌ای این‌جا ساخته نمی‌شود */
@Composable
internal fun rememberHeroFaces(d: ShopData, view: HomeView, input: HeroInput, range: HomeHero.Range): List<HeroFace> {
  val letters = StrHero.weekLetters.split(',')
  //  LOC-03: بازهٔ «هفته» از آغازِ هفتهٔ دکان («تاریخ و رقم»؛ تنظیم‌نشده شنبه)
  val week = ShopDayChoice.rules
  val sales = remember(d.sales, range, input.today, input.dayStartMs, input.hour, letters, week) {
    HomeHero.sales(d.sales, range, input.today, input.dayStartMs, input.nowMs, letters, week)
  }
  val till = HeroFace(
    page = HomeHero.Page.TILL,
    title = StrHome.drawer,
    //  همان عددِ کادرِ پیشینِ «صندوق»: صندوقِ باز ⇐ «باید باشد» ِ همان صندوق؛ بسته ⇐ گردشِ نقدِ امروز (با همان برچسب)
    label = if (view.shiftOpen) StrHero.tillBalance else StrHome.todayCashClosed,
    amount = if (input.blind) null else view.cash,
    series = sales,
    titles = listOf(StrHome.todaySales, StrHero.salesWeek, StrHero.salesMonth),
    empty = StrHero.emptySales,
    first = HeroStat(StrHome.todaySales, "${money(view.todaySales)} ؋", up = true),
    //  همان عددِ کادرِ پیشینِ «صندوق»: سودِ خالصِ امروز؛ نقشی که سود نمی‌بیند ⇐ شمارِ فاکتورها
    second = view.profit?.let { HeroStat(StrHome.todayLoss, "${money(HomeHero.loss(it))} ؋", up = false) }
      ?: HeroStat(StrHome.todaySalesCount, view.todayCount.toString(), up = null),
  )
  val safeView = input.safe ?: return listOf(till)
  val safeSeries = remember(safeView, range, input.today, input.dayStartMs, input.hour, letters, week) {
    HomeHero.safe(safeView.history, range, input.today, input.dayStartMs, input.nowMs, letters, week)
  }
  val flow = remember(safeView, input.dayStartMs) { HomeHero.safeToday(safeView.history, input.dayStartMs) }
  val safe = HeroFace(
    page = HomeHero.Page.SAFE,
    title = StrHome.safe,
    label = StrHero.safeBalance,
    amount = safeView.balanceMinor / 100.0,
    series = safeSeries,
    titles = listOf(StrHero.safeToday, StrHero.safeWeek, StrHero.safeMonth),
    empty = StrHero.emptySafe,
    first = HeroStat(StrHero.safeIn, "${money(flow.inMinor / 100.0)} ؋", up = true),
    second = HeroStat(StrHero.safeOut, "${money(flow.outMinor / 100.0)} ؋", up = false),
  )
  return listOf(till, safe)
}

/**
 *  کادرِ بالای خانه (HOME-V3): ورق‌خورِ افقیِ «صندوق» و «گاوصندوق» (گاوصندوق فقط با تنظیم و مجوز)، هر صفحه یک کادرِ
 *  شیشه‌ای مثلِ تصویرِ انتخابیِ صاحب‌کار. ورق با انگشت ۱:۱ و فنرِ کیت؛ در راست‌به‌چپ صفحهٔ دوم از چپ می‌آید.
 *  TalkBack: «پنهان کردن» و «صفحهٔ بعد/پیش» کارِ سفارشیِ همین کادرند.
 */
@Composable
internal fun HomeHeroPager(
  faces: List<HeroFace>,
  sync: HomeAlerts.Sync,
  state: PagerState,
  range: HomeHero.Range,
  onRange: (HomeHero.Range) -> Unit,
  onPlus: (HomeHero.Page) -> Unit,
  onHide: () -> Unit,
  height: Dp,
  modifier: Modifier = Modifier,
  inset: Dp = 16.dp,
) {
  val hc = HeroColors.from(PhoneV3.colors.source)
  val scope = rememberCoroutineScope()
  val actions = buildList {
    add(CustomAccessibilityAction(StrHero.hideHero) { onHide(); true })
    if (state.currentPage < faces.size - 1) add(CustomAccessibilityAction(StrHero.nextPage) { scope.launch { state.animateScrollToPage(state.currentPage + 1) }; true })
    if (state.currentPage > 0) add(CustomAccessibilityAction(StrHero.prevPage) { scope.launch { state.animateScrollToPage(state.currentPage - 1) }; true })
  }
  HorizontalPager(
    state = state,
    modifier = modifier.fillMaxWidth().semantics { customActions = actions },
    contentPadding = PaddingValues(horizontal = inset),
    pageSpacing = 10.dp,
    flingBehavior = PagerDefaults.flingBehavior(state, snapAnimationSpec = MotionSpec.sheet()),
    key = { it },
  ) { page ->
    val face = faces[page.coerceIn(0, faces.size - 1)]
    HeroCard(face, hc, sync, range, onRange, faces.size, page, { onPlus(face.page) }, height, Modifier.v3PagerItem(state, page))
  }
}

@Composable
private fun HeroCard(
  face: HeroFace,
  hc: HeroColors,
  sync: HomeAlerts.Sync,
  range: HomeHero.Range,
  onRange: (HomeHero.Range) -> Unit,
  pages: Int,
  page: Int,
  onPlus: () -> Unit,
  height: Dp,
  modifier: Modifier,
) {
  val shape = RoundedCornerShape(HERO_RADIUS)
  val fs = LocalDensity.current.fontScale.coerceIn(1f, 1.3f)
  Column(
    modifier
      .fillMaxWidth()
      .height(height)
      .shadow(if (hc.dark) 20.dp else 12.dp, shape, ambientColor = hc.shadow, spotColor = hc.shadow)
      .clip(shape)
      .background(Brush.verticalGradient(listOf(hc.top, hc.bottom)))
      .drawBehind {
        //  برقِ نرمِ بالای کادر (شیشه)
        drawRect(Brush.radialGradient(listOf(hc.sheen, Color.Transparent), center = Offset(size.width / 2f, 0f), radius = size.width * 0.8f))
      }
      .border(1.dp, hc.edge, shape)
      .padding(start = 18.dp, end = 18.dp, top = 14.dp, bottom = 16.dp),
  ) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
      Text(face.title, color = hc.ink, style = v3Text(17, FontWeight.Bold, 24), maxLines = 1)
      if (pages > 1) {
        Spacer(Modifier.width(10.dp))
        PageDots(pages, page, hc)
      }
      Spacer(Modifier.weight(1f))
      Box(
        Modifier.size(38.dp).clip(CircleShape).background(hc.track).border(1.dp, hc.edge, CircleShape)
          .v3Pressable(label = StrHero.addOrTake, scale = 0.9f, haptic = true, onClick = onPlus),
        contentAlignment = Alignment.Center,
      ) { Icon(Icons.Rounded.Add, contentDescription = StrHero.addOrTake, tint = hc.ink, modifier = Modifier.size(22.dp)) }
    }
    Spacer(Modifier.height(4.dp))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
      Text(face.label, color = hc.muted, style = v3Text(14, FontWeight.Medium, 20), maxLines = 1)
      Spacer(Modifier.width(8.dp))
      SyncChip(sync, hc)
    }
    HeroAmount(face.amount, hc)
    Spacer(Modifier.height(8.dp))
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
      Text(
        face.titles[range.ordinal], color = hc.ink, style = v3Text(15, FontWeight.Bold, 22),
        maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
      )
      Spacer(Modifier.width(8.dp))
      HeroRange(range, onRange, hc)
    }
    HeroChart(
      face.series, hc, face.empty, face.titles[range.ordinal], key = face.page to range,
      modifier = Modifier.fillMaxWidth().weight(1f),
    )
    Spacer(Modifier.height(8.dp))
    HeroStats(face.first, face.second, hc, Modifier.height(62.dp * fs))
  }
}

/** عددِ درشت با شمارنده؛ عددِ دراز کوچک‌تر می‌شود تا در یک خط بماند. بلندی کمینه است تا با درشتیِ نوشته (×۱٫۳) بریده نشود */
@Composable
private fun HeroAmount(amount: Double?, hc: HeroColors) {
  Row(Modifier.fillMaxWidth().heightIn(min = 54.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
    if (amount == null) {
      Text(StrHero.blindHidden, color = hc.muted, style = v3Text(15, FontWeight.SemiBold, 22), maxLines = 1)
      return@Row
    }
    val text = money(rememberCountUp(amount))
    val size = when {
      text.length > 13 -> 28
      text.length > 10 -> 34
      else -> 42
    }
    Text(text, color = hc.ink, style = v3Text(size, FontWeight.ExtraBold, size + 8), maxLines = 1, softWrap = false)
    Spacer(Modifier.width(6.dp))
    Text("؋", color = hc.muted, style = v3Text(size / 2 + 2, FontWeight.Bold, size), maxLines = 1)
  }
}

/** نشانِ همگام‌سازی کنارِ موجودی — متن + نقطهٔ رنگی (همان حالِ موتور، `HomeView.sync`) */
@Composable
private fun SyncChip(sync: HomeAlerts.Sync, hc: HeroColors) {
  val (label, dot) = when (sync) {
    HomeAlerts.Sync.OK -> StrHero.live to hc.live
    HomeAlerts.Sync.QUEUED -> StrHero.queued to hc.wait
    HomeAlerts.Sync.ERROR -> StrHero.syncError to hc.error
    HomeAlerts.Sync.OFFLINE -> StrHero.offline to hc.idle
    HomeAlerts.Sync.LOCAL_ONLY -> StrHero.localOnly to hc.idle
  }
  Row(
    Modifier.clip(CircleShape).background(hc.pill).padding(start = 7.dp, end = 9.dp, top = 2.dp, bottom = 2.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Box(Modifier.size(7.dp).clip(CircleShape).background(dot))
    Spacer(Modifier.width(5.dp))
    Text(label, color = hc.onPill, style = v3Text(11, FontWeight.Bold, 16), maxLines = 1)
  }
}

@Composable
private fun PageDots(n: Int, at: Int, hc: HeroColors) {
  Row(
    Modifier.semantics { contentDescription = StrHero.pageOf(at + 1, n) },
    horizontalArrangement = Arrangement.spacedBy(4.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    repeat(n) { i ->
      Box(
        Modifier.size(width = if (i == at) 16.dp else 6.dp, height = 6.dp).clip(CircleShape)
          .background(if (i == at) hc.ink else hc.faint.copy(alpha = 0.45f)),
      )
    }
  }
}

/** «امروز / هفته / ماه» — شیارِ قرصی با قرصِ روشن (شب) یا تیره (روز) زیرِ گزینهٔ انتخاب‌شده */
@Composable
private fun HeroRange(range: HomeHero.Range, onRange: (HomeHero.Range) -> Unit, hc: HeroColors) {
  val labels = listOf(StrHero.rangeToday, StrHero.rangeWeek, StrHero.rangeMonth)
  Row(Modifier.clip(CircleShape).background(hc.track).padding(3.dp).selectableGroup()) {
    HomeHero.Range.entries.forEachIndexed { i, r ->
      val on = r == range
      val bg by animateColorAsState(if (on) hc.pill else Color.Transparent, MotionSpec.tweenOf(180), label = "heroRangeBg")
      val fg by animateColorAsState(if (on) hc.onPill else hc.muted, MotionSpec.tweenOf(180), label = "heroRangeFg")
      Box(
        Modifier.clip(CircleShape).background(bg)
          .selectable(selected = on, role = Role.Tab, onClick = { onRange(r) })
          .heightIn(min = 30.dp).padding(horizontal = 12.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center,
      ) {
        Text(labels[i], color = fg, style = v3Text(13, if (on) FontWeight.Bold else FontWeight.Medium, 18), maxLines = 1)
      }
    }
  }
}

/** آمارِ پایین: دو خانه با پیکانِ رنگی و جداکنندهٔ مویی */
@Composable
private fun HeroStats(a: HeroStat, b: HeroStat, hc: HeroColors, modifier: Modifier) {
  val shape = RoundedCornerShape(20.dp)
  Row(
    modifier.fillMaxWidth().clip(shape).background(hc.tile).border(1.dp, hc.tileEdge, shape).padding(horizontal = 12.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    StatCell(a, hc, Modifier.weight(1f))
    Box(Modifier.width(1.dp).height(30.dp).background(hc.tileEdge))
    Spacer(Modifier.width(12.dp))
    StatCell(b, hc, Modifier.weight(1f))
  }
}

@Composable
private fun StatCell(s: HeroStat, hc: HeroColors, modifier: Modifier) {
  val (soft, tint) = when (s.up) {
    true -> hc.upSoft to hc.up
    false -> hc.downSoft to hc.down
    null -> hc.track to hc.muted
  }
  Row(modifier, verticalAlignment = Alignment.CenterVertically) {
    Box(Modifier.size(38.dp).clip(CircleShape).background(soft), contentAlignment = Alignment.Center) {
      Icon(
        when (s.up) {
          true -> Icons.Outlined.NorthEast
          false -> Icons.Outlined.SouthWest
          null -> Icons.AutoMirrored.Outlined.ReceiptLong
        },
        contentDescription = null, tint = tint, modifier = Modifier.size(18.dp),
      )
    }
    Spacer(Modifier.width(10.dp))
    Column(Modifier.weight(1f)) {
      Text(s.label, color = hc.muted, style = v3Text(12, FontWeight.Medium, 16), maxLines = 1, overflow = TextOverflow.Ellipsis)
      Text(s.value, color = hc.ink, style = v3Text(17, FontWeight.Bold, 22), maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
  }
}

/**
 *  نمودارِ خطیِ نرم با پرکنِ گرادیانی و برقِ کم: از آغازِ بازه تا «همین حالا» خطِ پُر، پس از آن خطِ صافِ نقطه‌چین
 *  (هنوز نیامده). نقطهٔ همین حالا با خطِ راهنمای نقطه‌چین و برچسبِ عدد. راست‌به‌چپ آینه است (آغاز راست). بازه یا
 *  صفحه که عوض شد خط از آغاز کشیده می‌شود (بی حرکت با «کاهشِ حرکت»). دادهٔ خالی ⇒ خطِ صافِ آرام و راهنما.
 */
@Composable
private fun HeroChart(series: HomeHero.Series, hc: HeroColors, emptyText: String, title: String, key: Any, modifier: Modifier) {
  val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
  val measurer = rememberTextMeasurer()
  val tick = v3Text(11, FontWeight.Medium, 14).copy(color = hc.faint)
  val tickNow = tick.copy(color = hc.ink, fontWeight = FontWeight.Bold)
  val tipStyle = v3Text(12, FontWeight.Bold, 16).copy(color = hc.onPill)
  val tip = "${money(series.current)} ؋"
  val reveal = remember { Animatable(if (Motion.enabled) 0f else 1f) }
  LaunchedEffect(key) {
    if (Motion.enabled) {
      reveal.snapTo(0f)
      reveal.animateTo(1f, tween(650, easing = FastOutSlowInEasing))
    } else reveal.snapTo(1f)
  }
  Box(
    modifier
      .clipToBounds()
      .semantics { contentDescription = StrHero.chart(title, money(series.current)) }
      .drawWithCache {
        val n = series.values.size
        val now = series.now.coerceIn(0, (n - 1).coerceAtLeast(0))
        val padX = 12.dp.toPx()
        val labelsH = 18.dp.toPx()
        val top = 30.dp.toPx()
        val bottom = size.height - labelsH - 6.dp.toPx()
        val h = (bottom - top).coerceAtLeast(1f)
        val step = if (n > 1) (size.width - 2 * padX) / (n - 1) else 0f
        fun x(i: Int): Float = if (rtl) size.width - padX - step * i else padX + step * i
        val max = series.values.take(now + 1).maxOrNull() ?: 0.0
        val scale = if (max > 0.0) max * 1.18 else 0.0
        fun y(v: Double): Float = if (scale <= 0.0) top + h * 0.62f else bottom - (v / scale).toFloat() * h
        val line = Path()
        val fill = Path()
        if (n > 0) {
          line.moveTo(x(0), y(series.values[0]))
          fill.moveTo(x(0), bottom)
          fill.lineTo(x(0), y(series.values[0]))
          for (i in 1..now) {
            val ax = x(i - 1); val ay = y(series.values[i - 1])
            val bx = x(i); val by = y(series.values[i])
            val mx = (ax + bx) / 2f
            line.cubicTo(mx, ay, mx, by, bx, by)
            fill.cubicTo(mx, ay, mx, by, bx, by)
          }
          fill.lineTo(x(now), bottom)
          fill.close()
        }
        val point = if (n > 0) Offset(x(now), y(series.current)) else Offset.Zero
        val shade = Brush.verticalGradient(listOf(hc.fillTop, Color.Transparent), startY = top, endY = bottom)
        val labels: List<Pair<Int, TextLayoutResult>> = series.ticks.mapIndexedNotNull { i, t ->
          if (t.isEmpty()) null else i to measurer.measure(t, if (i == now) tickNow else tick)
        }
        val tipText = measurer.measure(tip, tipStyle)
        val stroke = 2.5.dp.toPx()
        val dots = PathEffect.dashPathEffect(floatArrayOf(0.1f, 5.dp.toPx()), 0f)
        val guide = PathEffect.dashPathEffect(floatArrayOf(0.1f, 4.dp.toPx()), 0f)
        onDrawBehind {
          val p = reveal.value
          //  خطوطِ راهنمای افقی
          for (f in listOf(0.08f, 0.5f, 0.92f)) {
            val gy = top + h * f
            drawLine(hc.grid, Offset(0f, gy), Offset(size.width, gy), strokeWidth = 1.dp.toPx())
          }
          labels.forEach { (i, l) ->
            drawText(l, topLeft = Offset((x(i) - l.size.width / 2f).coerceIn(0f, size.width - l.size.width), size.height - l.size.height))
          }
          if (n == 0) return@onDrawBehind
          val pointAlpha = ((p - 0.8f) / 0.2f).coerceIn(0f, 1f)
          //  آینده: خطِ صافِ نقطه‌چین هم‌ترازِ همین حالا
          if (now < n - 1) {
            drawLine(
              hc.line.copy(alpha = 0.45f * pointAlpha), point, Offset(x(n - 1), point.y),
              strokeWidth = stroke, cap = StrokeCap.Round, pathEffect = dots,
            )
          }
          val (cl, cr) = if (rtl) size.width * (1f - p) to size.width else 0f to size.width * p
          clipRect(left = cl, right = cr) {
            if (!series.empty) drawPath(fill, shade)
            if (now > 0) {
              val a = if (series.empty) 0.35f else 1f
              drawPath(line, hc.glow, style = Stroke(width = stroke * 4f, cap = StrokeCap.Round, join = StrokeJoin.Round))
              drawPath(line, hc.glow, style = Stroke(width = stroke * 2.2f, cap = StrokeCap.Round, join = StrokeJoin.Round))
              drawPath(line, hc.line.copy(alpha = a), style = Stroke(width = stroke, cap = StrokeCap.Round, join = StrokeJoin.Round))
            }
          }
          if (series.empty || pointAlpha <= 0f) return@onDrawBehind
          //  نقطهٔ همین حالا، راهنمای عمودی و برچسبِ عدد
          drawLine(hc.line.copy(alpha = 0.35f * pointAlpha), point, Offset(point.x, bottom), strokeWidth = 1.5.dp.toPx(), cap = StrokeCap.Round, pathEffect = guide)
          drawCircle(hc.glow.copy(alpha = (hc.glow.alpha * 1.6f).coerceAtMost(1f) * pointAlpha), 13.dp.toPx(), point)
          drawCircle(hc.pill.copy(alpha = pointAlpha), 6.5.dp.toPx(), point)
          drawCircle(hc.bottom.copy(alpha = pointAlpha), 6.5.dp.toPx(), point, style = Stroke(2.5.dp.toPx()))
          val tw = tipText.size.width + 20.dp.toPx()
          val th = tipText.size.height + 8.dp.toPx()
          val tx = (point.x - tw / 2f).coerceIn(2.dp.toPx(), size.width - tw - 2.dp.toPx())
          val ty = (point.y - th - 12.dp.toPx()).coerceAtLeast(0f)
          drawRoundRect(hc.pill.copy(alpha = pointAlpha), Offset(tx, ty), Size(tw, th), CornerRadius(th / 2f))
          drawText(tipText, topLeft = Offset(tx + 10.dp.toPx(), ty + 4.dp.toPx()), alpha = pointAlpha)
        }
      },
  ) {
    if (series.empty) {
      Text(
        emptyText, color = hc.muted, style = v3Text(12, FontWeight.Medium, 16), textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxSize().padding(top = 8.dp).align(Alignment.TopCenter),
      )
    }
  }
}
