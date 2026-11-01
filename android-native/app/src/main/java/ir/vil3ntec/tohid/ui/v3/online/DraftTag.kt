package ir.vil3ntec.tohid.ui.v3.online

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.ui.theme.PhoneV3
import ir.vil3ntec.tohid.ui.theme.PhoneV3Roles
import ir.vil3ntec.tohid.ui.theme.V3Tone
import ir.vil3ntec.tohid.ui.v3.V3Type

/**
 *  برچسبِ کوچکِ اطلاع روی کارت («ارسال»، «۴ قلم»، «آزمایشی») — نه انتخاب‌شدنی (`V3Chip`
 *  کیت زدنی و ۳۸dp است). `tone` فقط برای چیزی که باید به چشم بیاید (آزمایشی ⇐ هشدار).
 *  ⇐ `DraftTag`، برای بردن به کیت در PR نام برده شده.
 */
@Composable
fun DraftTag(text: String, icon: ImageVector? = null, tone: V3Tone? = null, modifier: Modifier = Modifier) {
  val c = PhoneV3.colors
  val pair = tone?.let { PhoneV3Roles.tone(c, it) }
  Row(
    modifier
      .clip(RoundedCornerShape(8.dp))
      .background(pair?.bg ?: c.tile)
      .padding(horizontal = 8.dp, vertical = 3.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    if (icon != null) {
      Icon(icon, contentDescription = null, tint = pair?.fg ?: c.icon, modifier = Modifier.size(14.dp))
      Spacer(Modifier.width(4.dp))
    }
    Text(text, style = V3Type.footnote, color = pair?.fg ?: c.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
  }
}
