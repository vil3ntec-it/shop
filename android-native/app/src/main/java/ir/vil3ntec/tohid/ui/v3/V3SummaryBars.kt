package ir.vil3ntec.tohid.ui.v3

import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.platform.testTag
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.BoxWithConstraints
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
 *  نمودارِ ستونیِ فشردهٔ درونِ کارتِ خلاصه (p0r3، صاحب‌کار: «ردیف‌های تفکیک زیرِ هم کارت را خیلی بلند می‌کند») — ستون‌ها کنارِ هم:
 *  مبلغ بالای ستون، ستونِ رنگی (بلندی نسبت به بزرگ‌ترین) و نام زیرش؛ اگر جا نشد، ردیف به پهنا پیمایش می‌شود (هیچ دسته‌ای پنهان
 *  نیست). رنگ هرگز تنها نیست (نام و مبلغ کنارش). [onPick] ⇒ زدنِ ستون همان پالایه (دوباره ⇒ همه)؛ ستونِ برگزیده پررنگ و بقیه
 *  کم‌رنگ. ستون با `rememberV3Grow` از پایین می‌روید (کاهشِ حرکت ⇒ همان دم). [top] دیگر به کار نمی‌رود (همهٔ ستون‌ها پیدا).
 */
@Composable
fun V3SummaryBars(
  bars: List<V3Bar>,
  modifier: Modifier = Modifier,
  selected: String? = null,
  onPick: ((String) -> Unit)? = null,
  @Suppress("UNUSED_PARAMETER") top: Int = 5,
  track: Color = PhoneV3.colors.tile,
) {
  if (bars.isEmpty()) return
  val max = bars.maxOf { it.value }.takeIf { it > 0.0 } ?: 1.0
  val grow = rememberV3Grow(bars.map { it.key to it.value })
  val c = PhoneV3.colors
  BoxWithConstraints(modifier.fillMaxWidth()) {
    val full = maxWidth
    Row(
      Modifier
        .horizontalScroll(rememberScrollState())
        .widthIn(min = full)
        .padding(top = 4.dp),
      horizontalArrangement = Arrangement.spacedBy(V3SummaryBarsSpec.GAP, Alignment.CenterHorizontally),
      verticalAlignment = Alignment.Bottom,
    ) {
      bars.forEach { b ->
        val on = selected == null || selected == b.key
        Column(
          Modifier
            .width(V3SummaryBarsSpec.COLUMN)
            .then(if (onPick != null) Modifier.v3Pressable(label = b.label, role = Role.Tab, scale = 1f) { onPick(b.key) } else Modifier)
            .semantics(mergeDescendants = true) {
              contentDescription = "${b.label}: ${b.amount}"
              if (onPick != null) this.selected = selected == b.key
            }
            .alpha(if (on) 1f else 0.45f)
            .testTag(V3SummaryBarsSpec.COLUMN_TAG),
          horizontalAlignment = Alignment.CenterHorizontally,
        ) {
          Text(b.amount, style = v3Text(11, FontWeight.SemiBold, 14), color = c.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
          Spacer(Modifier.height(3.dp))
          Box(
            Modifier.width(V3SummaryBarsSpec.BAR).height(V3SummaryBarsSpec.HEIGHT).clip(RoundedCornerShape(6.dp)).background(track),
            contentAlignment = Alignment.BottomCenter,
          ) {
            Box(
              Modifier
                .fillMaxWidth()
                //  صفر ⇒ بی ستون (نه تکهٔ گمراه‌کننده)؛ مقدارِ بسیار کوچک دست‌کم یک تکهٔ دیدنی
                .fillMaxHeight(if (b.value <= 0.0) 0f else (b.value / max).toFloat().coerceIn(0.04f, 1f))
                .graphicsLayer { scaleY = grow; transformOrigin = TransformOrigin(0.5f, 1f) }
                .clip(RoundedCornerShape(6.dp))
                .background(b.color),
            )
          }
          Spacer(Modifier.height(4.dp))
          Text(
            b.label,
            style = v3Text(12, if (selected == b.key) FontWeight.SemiBold else FontWeight.Medium, 16),
            color = c.ink, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center,
          )
        }
      }
    }
  }
}

/** اندازه‌های نمودارِ ستونیِ خلاصه — پهنای ستون (جای زدن)، پهنای ستونِ رنگی، بلندیِ بیشینه و فاصله */
object V3SummaryBarsSpec {
  val COLUMN = 60.dp
  val BAR = 22.dp
  val HEIGHT = 64.dp
  val GAP = 8.dp
  const val COLUMN_TAG = "v3-summary-bar"
}
