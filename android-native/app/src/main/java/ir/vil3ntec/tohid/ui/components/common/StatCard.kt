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
import ir.vil3ntec.tohid.ui.theme.Shape
import ir.vil3ntec.tohid.ui.theme.Shop
import ir.vil3ntec.tohid.ui.theme.softCard
import ir.vil3ntec.tohid.ui.theme.tabular
import ir.vil3ntec.tohid.core.i18n.Str

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
  //  VILL3N: کارتِ سطحِ ۱ با خطِ دورِ دیدنی و سایهٔ سبک؛ کارتِ انتخاب‌شده خطِ برند
  Column(
    modifier
      .softCard(c, Shape.card)
      .then(if (selected) Modifier.border(2.dp, c.focus, Shape.card) else Modifier)
      .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClickLabel = Str.showList, onClick = onClick) else Modifier)
      .semantics {
        contentDescription = listOfNotNull(title, value, delta?.text, period).joinToString(Str.listSep + " ")
        if (onClick != null) this.selected = selected
      }
      .padding(16.dp),
  ) {
    Row(verticalAlignment = Alignment.CenterVertically) {
      if (icon != null) {
        Box(Modifier.size(32.dp).clip(Shape.icon).background(c.surfaceSelected), contentAlignment = Alignment.Center) {
          Icon(icon, null, tint = c.iconActive, modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.width(10.dp))
      }
      Text(title, style = MaterialTheme.typography.labelLarge, color = c.muted, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
    Spacer(Modifier.height(12.dp))
    Text(value, style = MaterialTheme.typography.headlineMedium.tabular(), color = c.text, fontWeight = FontWeight.Bold, maxLines = 1)
    Spacer(Modifier.height(8.dp))
    //  تغییر تا دو خط می‌شکند و بریده نمی‌شود (بازبینی: «کالای تازهٔ این ت…»)؛ بازه کنارش می‌ماند
    Row(verticalAlignment = Alignment.Top) {
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
        Icon(arrow, null, tint = tint, modifier = Modifier.padding(top = 1.dp).size(14.dp))
        Spacer(Modifier.width(3.dp))
        Text(delta.text, style = MaterialTheme.typography.labelMedium, color = tint, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        Spacer(Modifier.width(8.dp))
      } else Spacer(Modifier.weight(1f))
      Text(period, style = MaterialTheme.typography.labelMedium, color = c.muted, maxLines = 1)
    }
  }
}
