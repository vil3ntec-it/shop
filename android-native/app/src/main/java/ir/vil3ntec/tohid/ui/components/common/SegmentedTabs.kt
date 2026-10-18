package ir.vil3ntec.tohid.ui.components.common

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.ui.Platform
import ir.vil3ntec.tohid.ui.theme.Shape
import ir.vil3ntec.tohid.ui.theme.Shop

/** یک زبانه: شناسه، برچسب و شمار (اختیاری) */
data class SegmentTab<T>(val id: T, val label: String, val count: Int? = null)

/**
 *  زبانه‌های قطعه‌ای — «همه · فعال · ناموجود …» (VISUAL-DIRECTION §۳ بند ۳).
 *
 *  زبانهٔ انتخاب‌شده سطحِ سفید با متنِ پررنگ می‌گیرد، نه رنگِ اصلی روی
 *  تینت (آن جفت در روز زیرِ ۴٫۵:۱ است). با Tab و Enter/Space هم کار می‌کند.
 */
@Composable
fun <T> SegmentedTabs(
  tabs: List<SegmentTab<T>>,
  selected: T,
  onSelect: (T) -> Unit,
  modifier: Modifier = Modifier,
) {
  val c = Shop.colors
  val minH = if (Platform.DESKTOP) 30.dp else 44.dp
  Row(
    modifier
      .clip(Shape.button)
      .background(c.bg)
      .border(1.dp, c.border, Shape.button)
      .padding(3.dp)
      .selectableGroup(),
    horizontalArrangement = Arrangement.spacedBy(2.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    for (t in tabs) {
      val on = t.id == selected
      Box(
        Modifier
          .heightIn(min = minH)
          .clip(Shape.button)
          .then(if (on) Modifier.background(c.surfaceSolid).border(1.dp, c.border, Shape.button) else Modifier)
          .selectable(selected = on, role = Role.Tab) { onSelect(t.id) }
          .padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center,
      ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Text(
            t.label,
            style = MaterialTheme.typography.labelLarge,
            color = if (on) c.text else c.muted,
            fontWeight = if (on) FontWeight.Bold else FontWeight.Medium,
            maxLines = 1,
          )
          if (t.count != null) {
            Spacer(Modifier.width(6.dp))
            Text(
              t.count.toString(),
              style = MaterialTheme.typography.labelMedium,
              color = if (on) c.text else c.muted,
              modifier = Modifier.clip(Shape.badge).background(c.muted.copy(alpha = NEUTRAL_TINT)).padding(horizontal = 6.dp),
              maxLines = 1,
            )
          }
        }
      }
    }
  }
}
