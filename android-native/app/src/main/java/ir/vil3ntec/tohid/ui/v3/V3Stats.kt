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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.ui.theme.PhoneV3
import ir.vil3ntec.tohid.ui.theme.PhoneV3Palette
import ir.vil3ntec.tohid.ui.theme.V3Space

/**
 *  کارتِ آمار: برچسبِ کوچک، عددِ درشت که با `rememberCountUp` می‌شمارد (عددِ
 *  پایانی همیشه دقیق است — قالب‌بندی با `format` ِ صدازننده، مثلِ `money`)، و
 *  نقطهٔ رنگیِ اختیاری از پالتِ پشتیبان. `onClick` ⇐ همان بخش (فیلترِ دقیق).
 */
@Composable
fun V3Stat(
  label: String,
  value: Double,
  format: (Double) -> String,
  modifier: Modifier = Modifier,
  hue: V3Hue? = null,
  caption: String? = null,
  valueColor: Color? = null,
  onClick: (() -> Unit)? = null,
) {
  val c = PhoneV3.colors
  val shown = rememberCountUp(value)
  val shape = RoundedCornerShape(V3Space.featuredRadius)
  Column(
    modifier
      .then(if (onClick != null) Modifier.v3Pressable(label = label, onClick = onClick) else Modifier)
      .heightIn(min = 84.dp)
      .clip(shape)
      .background(c.card)
      .border(1.dp, c.hairline, shape)
      .padding(horizontal = 14.dp, vertical = 12.dp)
      .semantics(mergeDescendants = true) { contentDescription = "$label: ${format(value)}" },
  ) {
    Row(verticalAlignment = Alignment.CenterVertically) {
      if (hue != null) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(PhoneV3Palette.glyph(c, hue)))
        Spacer(Modifier.width(6.dp))
      }
      Text(label, style = V3Type.footnote, color = c.inkMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
    Spacer(Modifier.height(6.dp))
    Text(format(shown), style = V3Type.figure, color = valueColor ?: c.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
    if (caption != null) Text(caption, style = V3Type.caption, color = c.inkMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
  }
}

/** شبکهٔ کارت‌های آمار: دو ستون روی گوشی (`columns` برای پهن) */
@Composable
fun V3StatGrid(
  modifier: Modifier = Modifier,
  columns: Int = 2,
  items: List<@Composable (Modifier) -> Unit>,
) {
  Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
    items.chunked(columns).forEach { row ->
      Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        row.forEach { cell -> cell(Modifier.weight(1f)) }
        repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
      }
    }
  }
}
