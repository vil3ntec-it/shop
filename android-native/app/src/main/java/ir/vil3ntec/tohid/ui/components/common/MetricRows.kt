package ir.vil3ntec.tohid.ui.components.common

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ir.vil3ntec.tohid.core.i18n.Str
import ir.vil3ntec.tohid.core.i18n.StrLook
import ir.vil3ntec.tohid.ui.theme.Shape
import ir.vil3ntec.tohid.ui.theme.Shop
import ir.vil3ntec.tohid.ui.theme.tabular
import androidx.compose.material3.MaterialTheme

/** یک عدد در فهرستِ فشرده: برچسب (آغاز)، عدد (پایان)، زیرنویسِ کوچک و تغییرِ اختیاری؛ [onClick] ⇒ ردیف زدنی */
@Immutable
data class Metric(
  val label: String,
  val value: String,
  val note: String? = null,
  val delta: StatDelta? = null,
  val selected: Boolean = false,
  /** همان آیکن و همان کاشیِ ۳۲dp ِ `StatCard` (آیکن‌ها عوض نمی‌شوند) */
  val icon: ImageVector? = null,
  val onClick: (() -> Unit)? = null,
)

/**
 *  فهرستِ فشردهٔ عددها — جای شبکهٔ کارت‌های آماریِ بزرگ (صاحب‌کار ۱۴۰۵/۰۸/۲۴: «نمی‌خواهم چنین کادرهای بزرگی باشد»؛
 *  مثلِ داشبوردِ Square / Shopify / Stripe): یک کارت با ردیف‌های ۵۲dp، برچسب در آغاز و عدد در پایان، تغییر کوچک زیرِ عدد
 *  (همیشه پیکان + متن، نه رنگِ تنها). [top] ردیفِ اول پیداست و بقیه زیرِ «… عددِ دیگر» باز می‌شود.
 */
@Composable
fun MetricRows(metrics: List<Metric>, modifier: Modifier = Modifier, top: Int = 4) {
  if (metrics.isEmpty()) return
  val c = Shop.colors
  var open by rememberSaveable { mutableStateOf(false) }
  val hidden = (metrics.size - top).coerceAtLeast(0)
  val shown = if (open || hidden == 0) metrics else metrics.take(top)
  Column(
    modifier
      .fillMaxWidth()
      .clip(Shape.card)
      .background(c.surfaceSolid)
      .border(1.dp, c.border, Shape.card),
  ) {
    shown.forEachIndexed { i, m ->
      if (i > 0) Box(Modifier.fillMaxWidth().padding(start = 16.dp).height(1.dp).background(c.border))
      MetricRow(m)
    }
    if (hidden > 0) {
      Box(Modifier.fillMaxWidth().height(1.dp).background(c.border))
      Row(
        Modifier
          .fillMaxWidth()
          .heightIn(min = 48.dp)
          .clickable(role = Role.Button) { open = !open }
          .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
      ) {
        Text(if (open) StrLook.fewerMetrics else StrLook.moreMetrics(ir.vil3ntec.tohid.plain(hidden)), color = c.brandPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.width(4.dp))
        Icon(if (open) Icons.Rounded.KeyboardArrowUp else Icons.Rounded.KeyboardArrowDown, contentDescription = null, tint = c.brandPrimary, modifier = Modifier.size(18.dp))
      }
    }
  }
}

@Composable
private fun MetricRow(m: Metric) {
  val c = Shop.colors
  Row(
    Modifier
      .fillMaxWidth()
      .heightIn(min = 52.dp)
      .then(if (m.selected) Modifier.background(c.surfaceSelected) else Modifier)
      .then(if (m.onClick != null) Modifier.clickable(role = Role.Button, onClickLabel = Str.showList, onClick = m.onClick) else Modifier)
      .semantics(mergeDescendants = true) {
        contentDescription = listOfNotNull(m.label, m.value, m.delta?.text, m.note).joinToString(Str.listSep + " ")
        if (m.onClick != null) selected = m.selected
      }
      .padding(horizontal = 16.dp, vertical = 8.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    m.icon?.let { icon ->
      Box(Modifier.size(32.dp).clip(Shape.icon).background(c.surfaceSelected), contentAlignment = Alignment.Center) {
        Icon(icon, null, tint = c.iconActive, modifier = Modifier.size(18.dp))
      }
      Spacer(Modifier.width(12.dp))
    }
    Column(Modifier.weight(1f)) {
      Text(m.label, color = c.text, fontSize = 14.sp, fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis)
      if (m.note != null) Text(m.note, color = c.muted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
    Spacer(Modifier.width(12.dp))
    Column(horizontalAlignment = Alignment.End) {
      Text(m.value, style = MaterialTheme.typography.titleMedium.tabular(), color = c.text, fontWeight = FontWeight.SemiBold, maxLines = 1, textAlign = TextAlign.End)
      m.delta?.let { d ->
        val tint = when {
          d.trend == Trend.FLAT -> c.muted
          d.good -> c.success
          else -> c.danger
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
          Icon(
            when (d.trend) { Trend.UP -> Icons.Filled.ArrowUpward; Trend.DOWN -> Icons.Filled.ArrowDownward; Trend.FLAT -> Icons.Filled.Remove },
            contentDescription = null, tint = tint, modifier = Modifier.size(12.dp),
          )
          Spacer(Modifier.width(2.dp))
          Text(d.text, color = tint, fontSize = 12.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
      }
    }
  }
}
