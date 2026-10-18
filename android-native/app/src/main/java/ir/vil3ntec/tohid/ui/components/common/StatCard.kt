package ir.vil3ntec.tohid.ui.components.common

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.ui.Platform
import ir.vil3ntec.tohid.ui.theme.Shape
import ir.vil3ntec.tohid.ui.theme.Shop

enum class Trend { UP, DOWN, FLAT }

/**
 *  تغییر نسبت به دورهٔ قبل — همیشه **فلش + متن** (نه رنگِ تنها).
 *  `good` می‌گوید این جهت خوب است یا بد (افزایشِ «ناموجود» بد است).
 */
data class StatDelta(val text: String, val trend: Trend, val good: Boolean = trend == Trend.UP)

/**
 *  کارتِ آماری — VISUAL-DIRECTION §۳ بند ۲، v3 §۱۹.۵ بند ۲ و §۲۰.۳.
 *
 *  عنوان، عدد، تغییر (اختیاری؛ فقط وقتی دادهٔ دورهٔ قبل واقعاً هست) و
 *  بازهٔ زمانی. با کلیک فهرستِ فیلترشدهٔ همان عدد باز می‌شود؛ کارتِ بی
 *  `onClick` کلیک‌پذیر نیست (دکمهٔ بی‌اثر ممنوع).
 */
@Composable
fun StatCard(
  title: String,
  value: String,
  period: String,
  modifier: Modifier = Modifier,
  icon: ImageVector? = null,
  delta: StatDelta? = null,
  selected: Boolean = false,
  onClick: (() -> Unit)? = null,
) {
  val c = Shop.colors
  val pad = if (Platform.DESKTOP) 14.dp else 16.dp
  Column(
    modifier
      .clip(Shape.card)
      .background(c.surfaceSolid)
      .border(if (selected) 2.dp else 1.dp, if (selected) c.primary else c.border, Shape.card)
      .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClickLabel = "نمایشِ فهرست", onClick = onClick) else Modifier)
      .semantics {
        contentDescription = listOfNotNull(title, value, delta?.text, period).joinToString("، ")
        if (onClick != null) this.selected = selected
      }
      .padding(pad),
  ) {
    Row(verticalAlignment = Alignment.CenterVertically) {
      if (icon != null) {
        Box(Modifier.size(26.dp).clip(Shape.icon).background(c.primaryTint), contentAlignment = Alignment.Center) {
          Icon(icon, null, tint = c.primary, modifier = Modifier.size(16.dp))
        }
        Spacer(Modifier.width(8.dp))
      }
      Text(title, style = MaterialTheme.typography.labelLarge, color = c.muted, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
    Spacer(Modifier.height(8.dp))
    Text(value, style = MaterialTheme.typography.headlineSmall, color = c.text, fontWeight = FontWeight.Bold, maxLines = 1)
    Spacer(Modifier.height(6.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
      if (delta != null) {
        val tint = when {
          delta.trend == Trend.FLAT -> c.muted
          delta.good -> c.success
          else -> c.danger
        }
        val arrow = when (delta.trend) {
          Trend.UP -> Icons.Filled.ArrowUpward
          Trend.DOWN -> Icons.Filled.ArrowDownward
          Trend.FLAT -> Icons.Filled.Remove
        }
        Icon(arrow, null, tint = tint, modifier = Modifier.size(14.dp))
        Spacer(Modifier.width(3.dp))
        Text(delta.text, style = MaterialTheme.typography.labelMedium, color = tint, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
        Spacer(Modifier.width(8.dp))
      }
      Spacer(Modifier.weight(1f))
      Text(period, style = MaterialTheme.typography.labelMedium, color = c.muted, maxLines = 1)
    }
  }
}
