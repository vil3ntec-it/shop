package ir.vil3ntec.tohid.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.ui.theme.Radius
import ir.vil3ntec.tohid.ui.theme.Shop

/** هشدارِ «تحویل بی فروش» — رنگِ هشدار + متن، با کارِ بعدی (VISUAL-DIRECTION: هرگز رنگِ تنها) */
@Composable
internal fun FulfillNotice(title: String, body: String, action: String?, onAction: () -> Unit) {
  val c = Shop.colors
  Column(
    Modifier.fillMaxWidth().clip(RoundedCornerShape(Radius.md)).background(c.warningTint).padding(12.dp),
  ) {
    Row(verticalAlignment = Alignment.CenterVertically) {
      Box(Modifier.size(8.dp).clip(CircleShape).background(c.warning))
      Spacer(Modifier.width(8.dp))
      Text(title, Modifier.weight(1f), color = c.text, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
    }
    if (body.isNotBlank()) {
      Spacer(Modifier.height(4.dp))
      Text(body, color = c.text, style = MaterialTheme.typography.bodySmall)
    }
    if (action != null) {
      Spacer(Modifier.height(8.dp))
      TohidSecondaryButton(action, onClick = onAction)
    }
  }
}
