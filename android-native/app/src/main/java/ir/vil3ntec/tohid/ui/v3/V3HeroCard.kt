package ir.vil3ntec.tohid.ui.v3

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Autorenew
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.core.i18n.StrKit
import ir.vil3ntec.tohid.ui.screens.PagerDots
import ir.vil3ntec.tohid.ui.screens.v3Text
import ir.vil3ntec.tohid.ui.theme.Shop

/**
 *  یک تراشهٔ عمل روی کادرِ بزرگ (شیشه‌ایِ سفید). `onClick == null` ⇒ فقط نوشته (جای‌نگهدار، «به‌زودی»);
 *  `haptic` برای کارِ اصلی (مثلاً «فروشِ تازه»).
 */
data class V3HeroChip(
  val label: String,
  val icon: ImageVector? = null,
  val haptic: Boolean = false,
  val onClick: (() -> Unit)? = null,
)

/**
 *  کادرِ بزرگِ خانه — **یک قالب** برای همهٔ صفحه‌هایش («فروشِ امروز»، «صندوق»، «گاوصندوق») و کادری
 *  که با کشیدن پایین می‌آید (THEME-V2 «هم‌خوانیِ کادرها»): شعاعِ ۲۸، بلندیِ یکسان (`HERO_HEIGHT`)،
 *  حاشیهٔ ۱۶ از لبه‌ها را صدازننده (ورق‌زن) می‌دهد.
 *
 *  سرخط: نامِ دکان (با `onTitle` زدنی و ˅) + زنگ با نقطه؛ برچسب، عددِ بزرگ (۳۶sp، و ۳۰sp وقتی بلند است —
 *  اندازه از `valueForSize` تا هنگامِ شمارشِ عدد نپرد) و زیرنویس (با `live` آیکنِ «زنده از دفتر»)؛ پایین
 *  تراشه‌های شیشه‌ای و نشانگرِ صفحه در یک ردیف.
 *
 *  زمینه گرادیانِ تم (۱۳۵°، آینه در RTL) با دو دایرهٔ نرمِ تزئینی از رنگ‌های همراه — فقط رسم، بی blur —
 *  و پردهٔ خوانایی (`heroScrim`). متن همیشه سفیدِ کامل؛ زیرِ شیشهٔ تراشه سایهٔ تم (`heroChipShade`) تا
 *  برچسبِ ۱۲sp همه‌جا ≥ ۴٫۵:۱ بماند (`ThemeV2PaletteTest`). هیچ رنگِ خامی این‌جا نیست.
 */
@Composable
fun V3HeroCard(
  title: String,
  value: String,
  modifier: Modifier = Modifier,
  label: String? = null,
  caption: String? = null,
  chips: List<V3HeroChip> = emptyList(),
  pages: Int = 1,
  page: Int = 0,
  onBell: (() -> Unit)? = null,
  bellDot: Boolean = false,
  onTitle: (() -> Unit)? = null,
  live: Boolean = false,
  valueForSize: String = value,
) {
  val s = Shop.colors
  val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
  val fontScale = LocalDensity.current.fontScale.coerceIn(1f, 1.3f)
  val white = Color.White
  val glass = white.copy(alpha = 0.16f)
  val glassEdge = white.copy(alpha = 0.24f)
  Column(
    modifier
      .fillMaxWidth()
      .height(HERO_HEIGHT * fontScale)
      .clip(RoundedCornerShape(HERO_RADIUS))
      .drawWithCache {
        val w = size.width
        val h = size.height
        //  ۱۳۵°: از بالا-آغاز تا پایین-پایان (در RTL آینه)
        val start = if (rtl) Offset(w, 0f) else Offset.Zero
        val end = if (rtl) Offset(0f, h) else Offset(w, h)
        val brush = Brush.linearGradient(s.heroGradient, start, end)
        val scrim = Brush.linearGradient(listOf(Color.Transparent, Color.Black.copy(alpha = s.heroScrim)), start, end)
        val dotA = s.accent(0).copy(alpha = 0.18f)
        val dotB = s.accent(5).copy(alpha = 0.18f)
        val c1 = Offset(if (rtl) w * 0.06f else w * 0.94f, -h * 0.08f)
        val c2 = Offset(if (rtl) w * 0.92f else w * 0.08f, h * 1.02f)
        onDrawBehind {
          drawRect(brush)
          drawCircle(dotA, h * 0.62f, c1)
          drawCircle(dotB, h * 0.48f, c2)
          if (s.heroScrim > 0f) drawRect(scrim)
        }
      }
      .padding(start = 18.dp, end = 18.dp, top = 14.dp, bottom = 14.dp),
  ) {
    Row(verticalAlignment = Alignment.CenterVertically) {
      Row(
        Modifier
          .weight(1f, fill = false)
          .clip(RoundedCornerShape(10.dp))
          .then(if (onTitle != null) Modifier.v3Pressable(label = title, onClick = onTitle) else Modifier)
          .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Text(
          title, style = v3Text(15, FontWeight.SemiBold, 21), color = white, maxLines = 1, overflow = TextOverflow.Ellipsis,
          modifier = Modifier.weight(1f, fill = false),
        )
        if (onTitle != null) Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = null, tint = white, modifier = Modifier.size(18.dp))
      }
      Spacer(Modifier.weight(1f))
      if (onBell != null) {
        Box(
          Modifier
            .v3Pressable(label = StrKit.notifications, scale = 0.92f, onClick = onBell)
            .size(38.dp).clip(CircleShape).background(glass).border(1.dp, glassEdge, CircleShape),
          contentAlignment = Alignment.Center,
        ) {
          Icon(Icons.Outlined.Notifications, contentDescription = StrKit.notifications, tint = white, modifier = Modifier.size(20.dp))
          if (bellDot) Box(Modifier.align(Alignment.TopEnd).padding(top = 8.dp, end = 9.dp).size(8.dp).clip(CircleShape).background(s.status.cancelled.fg))
        }
      }
    }
    Spacer(Modifier.height(10.dp))
    //  برچسب و زیرنویس سفیدِ کامل (نه کم‌رنگ): روی گرادیانِ روشن کم‌رنگش زیرِ ۴٫۵:۱ می‌رفت
    if (label != null) Text(label, style = v3Text(13, FontWeight.Medium, 18), color = white, maxLines = 1, overflow = TextOverflow.Ellipsis)
    Text(
      value, color = white, maxLines = 1, overflow = TextOverflow.Ellipsis,
      style = v3Text(if (valueForSize.length > 12) 30 else 36, FontWeight.Bold, 44),
    )
    if (caption != null) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
          caption, style = v3Text(13, FontWeight.Medium, 18), color = white, maxLines = 1, overflow = TextOverflow.Ellipsis,
          modifier = Modifier.weight(1f, fill = false),
        )
        if (live) {
          Spacer(Modifier.width(6.dp))
          Icon(Icons.Outlined.Autorenew, contentDescription = null, tint = white, modifier = Modifier.size(14.dp))
        }
      }
    }
    Spacer(Modifier.weight(1f))
    Row(verticalAlignment = Alignment.CenterVertically) {
      Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        chips.take(3).forEach { chip -> HeroChipView(chip, glass, s.heroChipShade) }
      }
      PagerDots(pages, page, white, white.copy(alpha = 0.35f))
    }
  }
}

/** تراشهٔ شیشه‌ایِ سفید (سفید با آلفای ۱۶٪) روی سایهٔ تم */
@Composable
private fun HeroChipView(chip: V3HeroChip, glass: Color, shade: Float) {
  val white = Color.White
  val pill = RoundedCornerShape(percent = 50)
  val onClick = chip.onClick
  Row(
    Modifier
      .then(if (onClick != null) Modifier.v3Pressable(label = chip.label, scale = 0.94f, haptic = chip.haptic, onClick = onClick) else Modifier)
      .height(34.dp)
      .clip(pill)
      .background(Color.Black.copy(alpha = shade))
      .background(glass)
      .border(1.dp, white.copy(alpha = 0.22f), pill)
      .padding(horizontal = 12.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    chip.icon?.let { Icon(it, contentDescription = null, tint = white, modifier = Modifier.size(16.dp)); Spacer(Modifier.width(6.dp)) }
    Text(chip.label, style = v3Text(12, FontWeight.SemiBold, 16), color = white, maxLines = 1)
  }
}

/** بلندیِ یکسانِ همهٔ صفحه‌های کادرِ بزرگ (با درشتیِ نوشته تا ×۱٫۳ بزرگ می‌شود) */
val HERO_HEIGHT: Dp = 228.dp

/** گوشهٔ کادرِ بزرگ */
val HERO_RADIUS: Dp = 28.dp
