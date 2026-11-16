package ir.vil3ntec.tohid.ui.v3

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.ui.theme.PhoneV3
import ir.vil3ntec.tohid.ui.theme.PhoneV3Roles

/** یک گزینهٔ کادرِ کشویی؛ [divider] ⇒ خطِ جداکننده پیش از آن (گروهِ تازه: ماه‌ها، سال‌ها) */
@Immutable
data class V3Choice(val key: String, val label: String, val divider: Boolean = false)

/**
 *  کادرِ کشوییِ دوره و تاریخ — **یک** جزء برای همهٔ برنامه (صاحب‌کار ۱۴۰۵/۰۸/۲۴: «یک کادرِ کشویی بس بود»): کادرِ فشرده با
 *  مقدارِ جاری و ▾؛ زدن ⇒ فهرستِ گزینه‌ها زیرش (امروز، دیروز، این ماه، ماه‌های پیشین، …) با تیکِ گزینهٔ برگزیده. جای
 *  ‹ ماه › و کلیدِ «روز / ماه / سال». نامِ ماه‌ها را صدازننده با همان قالبِ برنامه می‌دهد (خورشیدی در فارسی و پشتو).
 *
 *  @param label نامِ کادر برای صفحه‌خوان («دوره»، «ماه»)
 */
/**
 *  p0r3 (صاحب‌کار: «جعبهٔ سفید با مرز نه؛ فقط نوشته و ▾»): درونِ کارتِ خلاصه (`V3SummaryFrame` آن را روشن می‌کند) کادرِ کشویی
 *  دکمهٔ نوشته‌ای است — همان نوشته و ▾، همان فهرست با زدن، بی زمینه و مرز؛ جای زدنِ ۴۴dp همان.
 */
val LocalV3DropdownPlain = androidx.compose.runtime.staticCompositionLocalOf { false }

@Composable
fun V3Dropdown(
  value: String,
  choices: List<V3Choice>,
  selected: String?,
  onSelect: (String) -> Unit,
  label: String,
  modifier: Modifier = Modifier,
) {
  val c = PhoneV3.colors
  var open by remember { mutableStateOf(false) }
  val shape = RoundedCornerShape(12.dp)
  val plain = LocalV3DropdownPlain.current
  Box(modifier) {
    Row(
      Modifier
        .heightIn(min = 44.dp)
        .clip(shape)
        .then(if (plain) Modifier else Modifier.background(c.card).border(1.dp, c.hairline, shape))
        .v3Pressable(label = "$label: $value", role = Role.DropdownList) { open = true }
        .padding(start = if (plain) 6.dp else 14.dp, end = if (plain) 2.dp else 10.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Text(
        value, style = if (plain) V3Type.callout.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold) else V3Type.callout,
        color = c.ink, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false),
      )
      Spacer(Modifier.width(if (plain) 2.dp else 4.dp))
      Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = null, tint = if (plain) c.ink else c.inkMuted, modifier = Modifier.size(18.dp))
    }
    DropdownMenu(
      expanded = open,
      onDismissRequest = { open = false },
      modifier = Modifier.heightIn(max = 420.dp).widthIn(min = 200.dp),
      shape = RoundedCornerShape(14.dp),
      containerColor = c.card,
    ) {
      choices.forEachIndexed { i, ch ->
        if (ch.divider && i > 0) HorizontalDivider(Modifier.padding(vertical = 4.dp), color = c.hairline)
        val on = ch.key == selected
        DropdownMenuItem(
          text = { Text(ch.label, style = V3Type.body, color = c.ink, maxLines = 1, overflow = TextOverflow.Ellipsis) },
          trailingIcon = if (on) ({ Icon(Icons.Rounded.Check, contentDescription = null, tint = PhoneV3Roles.brandText(c), modifier = Modifier.size(18.dp)) }) else null,
          onClick = { open = false; onSelect(ch.key) },
          modifier = Modifier.semantics { this.selected = on },
        )
      }
    }
  }
}
