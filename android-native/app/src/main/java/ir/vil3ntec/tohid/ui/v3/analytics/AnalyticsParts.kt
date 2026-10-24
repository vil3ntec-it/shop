package ir.vil3ntec.tohid.ui.v3.analytics

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.AbsoluteAlignment
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.absoluteOffset
import ir.vil3ntec.tohid.money
import ir.vil3ntec.tohid.ui.theme.PhoneV3
import ir.vil3ntec.tohid.ui.theme.PhoneV3Colors
import ir.vil3ntec.tohid.ui.theme.PhoneV3Palette
import ir.vil3ntec.tohid.ui.theme.PhoneV3Roles
import ir.vil3ntec.tohid.ui.v3.V3Card
import ir.vil3ntec.tohid.ui.v3.V3Hue
import ir.vil3ntec.tohid.ui.v3.V3Springs
import ir.vil3ntec.tohid.ui.v3.V3Type
import ir.vil3ntec.tohid.ui.v3.rememberCountUp
import ir.vil3ntec.tohid.ui.v3.v3Pressable

/**
 *  مبلغ با نشانِ افغانی — همان `money()` ِ برنامه (رقم را قلم به انتخابِ دکان می‌کشد). منفی با
 *  «−» و نشانهٔ چپ‌به‌راست (LRM) تا در متنِ راست‌به‌چپ «−۸۰» بماند نه «۸۰−»؛ گردکردن قرینه است
 *  (−۲۶۲٬۱۱۷٫۵ و ۲۶۲٬۱۱۷٫۵ هر دو ۲۶۲٬۱۱۸).
 */
internal fun afn(v: Double): String = if (v < 0) "$LRM−${money(-v)} ؋" else "${money(v)} ؋"

/** مبلغِ کم‌شونده در صورتِ حساب: «−» پیش از عدد، صفر بی نشان */
internal fun afnMinus(v: Double): String = if (v > 0) afn(-v) else afn(v)

private const val LRM = "\u200E"

/**
 *  سری‌های نمودار به ترتیبِ COLOR-SYSTEM §۱۰ — فقط از نقش‌های تم (بی HEX). قرمز فقط
 *  برای افت/خطای مهم است، نه دادهٔ عادی.
 */
internal enum class ChartSeries { SALES, COMPARISON, COSTS, DISCOUNT, DROP, STOCK, NEUTRAL }

internal fun chartColor(c: PhoneV3Colors, s: ChartSeries): Color = when (s) {
  ChartSeries.SALES -> PhoneV3Palette.chartSales(c)
  ChartSeries.COMPARISON -> PhoneV3Palette.glyph(c, V3Hue.BLUE)
  ChartSeries.COSTS -> PhoneV3Palette.chartCost(c)
  ChartSeries.DISCOUNT -> PhoneV3Palette.glyph(c, V3Hue.ORANGE)
  ChartSeries.DROP -> c.negative
  ChartSeries.STOCK -> PhoneV3Palette.glyph(c, V3Hue.TEAL)
  ChartSeries.NEUTRAL -> PhoneV3Palette.chartGrid(c)
}

internal fun chartGrid(c: PhoneV3Colors): Color = PhoneV3Palette.chartGrid(c)

/**
 *  میلهٔ افقیِ نازک (رتبه‌ها، سهمِ تأمین‌کننده): زمینهٔ خط‌راهنما و پُرشدگیِ رنگِ سری که از
 *  سمتِ آغاز (آینه در راست‌به‌چپ) با `scaleX` رشد می‌کند — پیشرفت از `rememberV3Grow` ِ کیت.
 */
@Composable
internal fun InlineBar(fraction: Float, color: Color, progress: Float, modifier: Modifier = Modifier, height: Dp = 6.dp) {
  val c = PhoneV3.colors
  val origin = if (LocalLayoutDirection.current == LayoutDirection.Rtl) TransformOrigin(1f, 0.5f) else TransformOrigin(0f, 0.5f)
  Box(modifier.height(height).clip(RoundedCornerShape(50)).background(chartGrid(c))) {
    if (fraction > 0f) Box(
      Modifier.fillMaxSize().graphicsLayer {
        scaleX = (fraction * progress).coerceIn(0f, 1f)
        transformOrigin = origin
      }.clip(RoundedCornerShape(50)).background(color),
    )
  }
}

/** نقطه و نامِ سری — راهنمای هر نمودار */
@Composable
internal fun Legend(items: List<Pair<Color, String>>, modifier: Modifier = Modifier) {
  val c = PhoneV3.colors
  Row(modifier, verticalAlignment = Alignment.CenterVertically) {
    items.forEachIndexed { i, (color, label) ->
      if (i > 0) Spacer(Modifier.width(16.dp))
      Box(Modifier.size(10.dp).clip(RoundedCornerShape(3.dp)).background(color))
      Spacer(Modifier.width(6.dp))
      Text(label, style = V3Type.footnote, color = c.inkMuted, maxLines = 1)
    }
  }
}

/**
 *  کارتِ عددِ اصلی (hero): برچسب، عددِ درشتِ شمارنده و زیرنویس. `negative` ⇒ رنگِ ضرر —
 *  برچسب هم خودش «ضرر» است، رنگ تنها نیست.
 */
@Composable
internal fun HeroFigure(label: String, value: Double, caption: String?, negative: Boolean = false, format: (Double) -> String = ::afn) {
  val c = PhoneV3.colors
  val shown = rememberCountUp(value)
  V3Card(padding = PaddingValues(horizontal = 18.dp, vertical = 16.dp)) {
    Text(label, style = V3Type.subhead, color = if (negative) PhoneV3Roles.errorText(c) else c.inkMuted)
    Spacer(Modifier.height(4.dp))
    Text(format(shown), style = V3Type.largeTitle, color = if (negative) c.negative else c.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
    if (caption != null) Text(caption, style = V3Type.footnote, color = c.inkMuted)
  }
}

/**
 *  نوارِ زبانه‌های پیمایشی با نشانگرِ لغزان (فنرِ `V3Springs.ui`) — زبانهٔ انتخاب‌شده خودش
 *  به دید می‌آید؛ در راست‌به‌چپ آینه (جای زبانه‌ها از چیدمانِ واقعی خوانده می‌شود).
 *  پیش‌نویسِ کیت (`DraftTabStrip`): کیت نوارِ زبانهٔ پیمایشی ندارد.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun DraftTabStrip(labels: List<String>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
  val c = PhoneV3.colors
  val density = LocalDensity.current
  val lefts = remember(labels.size) { mutableStateListOf<Float>().apply { repeat(labels.size) { add(0f) } } }
  val widths = remember(labels.size) { mutableStateListOf<Float>().apply { repeat(labels.size) { add(0f) } } }
  val requesters = remember(labels.size) { List(labels.size) { BringIntoViewRequester() } }
  val x by animateFloatAsState(lefts.getOrElse(selected) { 0f }, V3Springs.ui(), label = "tabX")
  val w by animateFloatAsState(widths.getOrElse(selected) { 0f }, V3Springs.ui(), label = "tabW")
  LaunchedEffect(selected) { requesters.getOrNull(selected)?.bringIntoView() }
  Column(modifier.fillMaxWidth()) {
    Box(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
      Row(Modifier.padding(horizontal = 8.dp)) {
        labels.forEachIndexed { i, label ->
          val on = i == selected
          Box(
            Modifier
              .bringIntoViewRequester(requesters[i])
              .onPlaced { lc -> lefts[i] = lc.positionInParent().x; widths[i] = lc.size.width.toFloat() }
              .v3Pressable(label = label, role = Role.Tab, haptic = !on) { onSelect(i) }
              .semantics { this.selected = on }
              .padding(horizontal = 12.dp, vertical = 10.dp),
          ) {
            Text(
              label,
              style = V3Type.callout.copy(fontWeight = if (on) FontWeight.SemiBold else FontWeight.Medium),
              color = if (on) PhoneV3Roles.brandText(c) else c.inkMuted,
              maxLines = 1,
            )
          }
        }
      }
      if (w > 0f) {
        val pad = with(density) { 8.dp.toPx() }
        Box(
          Modifier
            .align(AbsoluteAlignment.BottomLeft)
            .absoluteOffset { IntOffset((pad + x + w * 0.15f).toInt(), 0) }
            .width(with(density) { (w * 0.7f).toDp() })
            .height(3.dp)
            .clip(CircleShape)
            .background(PhoneV3Palette.iconActive(c)),
        )
      }
    }
    Box(Modifier.fillMaxWidth().height(1.dp).background(c.hairline))
  }
}
