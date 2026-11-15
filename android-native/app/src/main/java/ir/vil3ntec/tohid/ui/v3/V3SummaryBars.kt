package ir.vil3ntec.tohid.ui.v3

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.core.i18n.StrLook
import ir.vil3ntec.tohid.ui.screens.v3Text
import ir.vil3ntec.tohid.ui.theme.PhoneV3

/** یک ستونِ نمودارِ درونِ کارتِ خلاصه: نام، مبلغِ نوشته، مقدار (برای درازای نوار) و رنگ */
@Immutable
data class V3Bar(val key: String, val label: String, val amount: String, val value: Double, val color: Color)

/**
 *  نمودارِ افقیِ درونِ کارتِ خلاصه (۶۵s، صاحب‌کار: تفکیک درونِ همان کارت، نه کارتِ جدا زیرش): هر ردیف نقطهٔ رنگ، نام،
 *  نوارِ سهم (درازی نسبت به بزرگ‌ترین) و مبلغ — رنگ هرگز تنها نیست. [top] ردیفِ نخست پیدا، بقیه زیرِ «… N مورد دیگر» که
 *  با زدن همه را نشان می‌دهد. [onPick] ⇒ زدنِ ردیف همان پالایه (دوباره ⇒ همه)؛ ردیفِ برگزیده پررنگ و بقیه کم‌رنگ.
 *  نوار با `rememberV3Grow` از آغاز می‌روید (کاهشِ حرکت ⇒ همان دم).
 */
@Composable
fun V3SummaryBars(
  bars: List<V3Bar>,
  modifier: Modifier = Modifier,
  selected: String? = null,
  onPick: ((String) -> Unit)? = null,
  top: Int = 5,
  track: Color = PhoneV3.colors.tile,
) {
  if (bars.isEmpty()) return
  var all by rememberSaveable { mutableStateOf(false) }
  val hidden = (bars.size - top).coerceAtLeast(0)
  val shown = if (all || hidden == 0) bars else bars.take(top)
  val max = bars.maxOf { it.value }.takeIf { it > 0.0 } ?: 1.0
  val grow = rememberV3Grow(bars.map { it.key to it.value })
  val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
  val c = PhoneV3.colors
  Column(modifier.fillMaxWidth()) {
    shown.forEach { b ->
      val on = selected == null || selected == b.key
      Row(
        Modifier
          .fillMaxWidth()
          .heightIn(min = 44.dp)
          .then(if (onPick != null) Modifier.v3Pressable(label = b.label, role = Role.Tab, scale = 1f) { onPick(b.key) } else Modifier)
          .semantics(mergeDescendants = true) {
            contentDescription = "${b.label}: ${b.amount}"
            if (onPick != null) this.selected = selected == b.key
          }
          .alpha(if (on) 1f else 0.45f),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Box(Modifier.size(9.dp).clip(CircleShape).background(b.color))
        Spacer(Modifier.width(8.dp))
        Text(
          b.label,
          style = v3Text(13, if (selected == b.key) FontWeight.SemiBold else FontWeight.Medium, 18),
          color = c.ink,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
          modifier = Modifier.weight(0.34f),
        )
        Spacer(Modifier.width(8.dp))
        Box(Modifier.weight(0.4f).height(8.dp).clip(RoundedCornerShape(50)).background(track)) {
          Box(
            Modifier
              //  صفر ⇒ بی نوار (نه نقطهٔ گمراه‌کننده)؛ مقدارِ بسیار کوچک دست‌کم یک تکهٔ دیدنی
              .fillMaxWidth(if (b.value <= 0.0) 0f else (b.value / max).toFloat().coerceIn(0.03f, 1f))
              .fillMaxHeight()
              .graphicsLayer { scaleX = grow; transformOrigin = TransformOrigin(if (rtl) 1f else 0f, 0.5f) }
              .clip(RoundedCornerShape(50))
              .background(b.color),
          )
        }
        Spacer(Modifier.width(8.dp))
        Text(b.amount, style = v3Text(13, FontWeight.SemiBold, 18), color = c.ink, maxLines = 1, modifier = Modifier.weight(0.26f, fill = false))
      }
    }
    if (hidden > 0) {
      Row(
        Modifier
          .fillMaxWidth()
          .heightIn(min = 44.dp)
          .v3Pressable(label = if (all) StrLook.fewerMetrics else StrLook.moreRows(hidden), role = Role.Button, scale = 1f) { all = !all }
          .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
      ) {
        Text(if (all) StrLook.fewerMetrics else StrLook.moreRows(hidden), style = v3Text(13, FontWeight.SemiBold, 18), color = c.inkMuted)
        Spacer(Modifier.width(2.dp))
        Icon(if (all) Icons.Rounded.KeyboardArrowUp else Icons.Rounded.KeyboardArrowDown, contentDescription = null, tint = c.inkMuted, modifier = Modifier.size(18.dp))
      }
    }
  }
}
