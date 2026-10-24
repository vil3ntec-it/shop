package ir.vil3ntec.tohid.ui.v3.shell

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.ui.theme.PhoneV3
import ir.vil3ntec.tohid.ui.v3.V3Chevron
import ir.vil3ntec.tohid.ui.v3.V3Hue
import ir.vil3ntec.tohid.ui.v3.V3IconTile
import ir.vil3ntec.tohid.ui.v3.V3Type
import ir.vil3ntec.tohid.ui.v3.v3Pressable

/** عنوانی که تکهٔ پیداشده‌اش برندرنگ و پررنگ است */
internal fun highlighted(text: String, match: IntRange?, brand: Color): AnnotatedString = buildAnnotatedString {
  if (match == null || match.first < 0 || match.last >= text.length) { append(text); return@buildAnnotatedString }
  append(text.substring(0, match.first))
  withStyle(SpanStyle(color = brand, fontWeight = FontWeight.Bold)) { append(text.substring(match.first, match.last + 1)) }
  append(text.substring(match.last + 1))
}

/**
 *  پیش‌نویسِ کیت (برای بردن به `V3ListRow` با عنوانِ `AnnotatedString`): همان ردیفِ نسخهٔ ۳ —
 *  کاشیِ آیکن، عنوان و زیرعنوان، فلشِ «جلو» و فشارِ فنری — با عنوانِ برجسته‌شدهٔ جست‌وجو.
 */
@Composable
internal fun DraftHitRow(
  title: AnnotatedString,
  subtitle: String?,
  icon: ImageVector,
  hue: V3Hue?,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
  selected: Boolean = false,
) {
  val c = PhoneV3.colors
  Row(
    modifier
      .fillMaxWidth()
      .heightIn(min = PhoneV3.rowHeight)
      .background(if (selected) ir.vil3ntec.tohid.ui.theme.PhoneV3Palette.selected(c) else c.card)
      .v3Pressable(label = title.text, onClick = onClick)
      .padding(start = 12.dp, end = 16.dp, top = 8.dp, bottom = 8.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    V3IconTile(icon, hue)
    Spacer(Modifier.width(12.dp))
    Column(Modifier.weight(1f)) {
      Text(title, style = V3Type.body, color = c.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
      if (subtitle != null) Text(subtitle, style = V3Type.footnote, color = c.inkMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
    Spacer(Modifier.width(4.dp))
    V3Chevron()
  }
}
