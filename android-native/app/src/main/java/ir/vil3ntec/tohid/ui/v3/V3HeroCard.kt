package ir.vil3ntec.tohid.ui.v3

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.core.i18n.StrKit
import ir.vil3ntec.tohid.ui.screens.MotionSpec
import ir.vil3ntec.tohid.ui.screens.v3Text
import ir.vil3ntec.tohid.ui.theme.Shop

/** یک تراشهٔ عمل روی کادرِ بزرگ (شیشه‌ایِ سفید) */
data class V3HeroChip(val label: String, val icon: ImageVector? = null, val onClick: () -> Unit)

/**
 *  کادرِ بزرگِ خانه — **یک قالب** برای همهٔ صفحه‌هایش («فروشِ امروز»، «صندوق»، «گاوصندوق») و کادری
 *  که با کشیدن پایین می‌آید (THEME-V2 «هم‌خوانیِ کادرها»): شعاعِ ۲۸، حاشیهٔ ۱۶ از لبه‌ها (صدازننده)،
 *  بلندیِ یکسان، سرخط (نامِ دکان + زنگ)، برچسب، عددِ بزرگ، زیرنویس، تراشه‌های شیشه‌ایِ سفید و نشانگرِ صفحه.
 *
 *  زمینه گرادیانِ تم (۱۳۵°، آینه در RTL) با دو دایرهٔ نرمِ تزئینی از رنگ‌های همراه — فقط رسم، بی blur —
 *  و پردهٔ خواناییِ تم (`heroScrim`) تا متنِ سفید AA بماند. هیچ رنگِ خامی این‌جا نیست.
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
) {
  val s = Shop.colors
  val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
  val fontScale = LocalDensity.current.fontScale.coerceIn(1f, 1.3f)
  val white = Color.White
  val glass = white.copy(alpha = 0.16f)
  Box(
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
        val r1 = h * 0.62f
        val r2 = h * 0.48f
        val c1 = Offset(if (rtl) w * 0.06f else w * 0.94f, -h * 0.08f)
        val c2 = Offset(if (rtl) w * 0.92f else w * 0.08f, h * 1.02f)
        onDrawBehind {
          drawRect(brush)
          drawCircle(dotA, r1, c1)
          drawCircle(dotB, r2, c2)
          if (s.heroScrim > 0f) drawRect(scrim)
        }
      }
      .padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 14.dp),
  ) {
    Column(Modifier.fillMaxSize()) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = v3Text(15, FontWeight.SemiBold, 22), color = white, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        if (onBell != null) {
          Box(
            Modifier.size(36.dp).clip(CircleShape).background(glass).v3Pressable(label = StrKit.notifications, scale = 0.92f, onClick = onBell),
            contentAlignment = Alignment.Center,
          ) {
            Icon(Icons.Outlined.Notifications, contentDescription = null, tint = white, modifier = Modifier.size(20.dp))
            if (bellDot) Box(Modifier.align(Alignment.TopEnd).padding(7.dp).size(8.dp).clip(CircleShape).background(s.status.cancelled.fg))
          }
        }
      }
      Spacer(Modifier.height(6.dp))
      //  برچسب و زیرنویس سفیدِ کامل (نه کم‌رنگ): روی گرادیانِ روشن کم‌رنگش زیرِ ۴٫۵:۱ می‌رفت
      if (label != null) Text(label, style = v3Text(13, FontWeight.Medium, 18), color = white, maxLines = 1)
      Text(value, style = v3Text(32, FontWeight.Bold, 42), color = white, maxLines = 1, overflow = TextOverflow.Ellipsis)
      if (caption != null) Text(caption, style = v3Text(12, FontWeight.Normal, 17), color = white, maxLines = 1, overflow = TextOverflow.Ellipsis)
      Spacer(Modifier.weight(1f))
      if (chips.isNotEmpty()) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          chips.take(3).forEach { c ->
            Row(
              Modifier
                .clip(RoundedCornerShape(16.dp))
                //  سایهٔ تم زیرِ شیشه (`heroChipShade`) تا برچسبِ ۱۲sp همه‌جای گرادیان ≥ ۴٫۵:۱ بماند
                .background(Color.Black.copy(alpha = s.heroChipShade))
                .background(glass)
                .border(1.dp, white.copy(alpha = 0.22f), RoundedCornerShape(16.dp))
                .v3Pressable(label = c.label, onClick = c.onClick)
                .padding(horizontal = 12.dp, vertical = 6.dp),
              verticalAlignment = Alignment.CenterVertically,
            ) {
              if (c.icon != null) {
                Icon(c.icon, contentDescription = null, tint = white, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
              }
              Text(c.label, style = v3Text(12, FontWeight.SemiBold, 16), color = white, maxLines = 1)
            }
          }
        }
      }
      if (pages > 1) {
        Spacer(Modifier.height(10.dp))
        Row(
          Modifier.fillMaxWidth().semantics { contentDescription = "${page + 1}/$pages" },
          horizontalArrangement = Arrangement.Center,
        ) {
          repeat(pages) { i ->
            val wDot by animateDpAsState(if (i == page) 18.dp else 6.dp, MotionSpec.indicator(), label = "heroDot")
            if (i > 0) Spacer(Modifier.width(4.dp))
            Box(Modifier.size(width = wDot, height = 6.dp).clip(CircleShape).background(if (i == page) white else white.copy(alpha = 0.45f)))
          }
        }
      }
    }
  }
}

/** بلندیِ یکسانِ همهٔ صفحه‌های کادرِ بزرگ (با درشتیِ نوشته تا ۱٫۳ بزرگ می‌شود) */
val HERO_HEIGHT: Dp = 212.dp

/** گوشهٔ کادرِ بزرگ */
val HERO_RADIUS: Dp = 28.dp
