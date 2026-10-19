package ir.vil3ntec.tohid.ui.components.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.ui.theme.Shape
import ir.vil3ntec.tohid.ui.theme.Shop

/**
 *  نشانِ وضعیت — «متن + رنگ + نقطه»، هرگز رنگِ تنها (v3 §۱۹.۱۱،
 *  VISUAL-DIRECTION §۱ بند ۳).
 */
@Composable
fun StatusPill(text: String, tone: PillTone, modifier: Modifier = Modifier) {
  val (fg, bg) = pillColors(Shop.colors, tone)
  Row(
    modifier
      .clip(Shape.badge)
      .background(bg)
      .padding(horizontal = 9.dp, vertical = 3.dp)
      .semantics { contentDescription = "وضعیت: $text" },
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Box(Modifier.size(6.dp).clip(CircleShape).background(fg))
    Spacer(Modifier.width(6.dp))
    Text(text, style = MaterialTheme.typography.labelMedium, color = fg, fontWeight = FontWeight.SemiBold, maxLines = 1)
  }
}
