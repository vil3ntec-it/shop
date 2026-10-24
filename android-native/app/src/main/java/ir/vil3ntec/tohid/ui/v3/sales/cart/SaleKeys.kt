package ir.vil3ntec.tohid.ui.v3.sales.cart

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Backspace
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ir.vil3ntec.tohid.core.i18n.StrSale
import ir.vil3ntec.tohid.ui.components.pos.KeypadInput
import ir.vil3ntec.tohid.ui.theme.PhoneV3
import ir.vil3ntec.tohid.ui.v3.V3Type
import ir.vil3ntec.tohid.ui.v3.v3Pressable

/**
 *  کلیدهای عددیِ صندوق (۳ در هر ردیف): صفحه‌کلیدِ مقدار و PINِ تأیید. کلیدِ گردِ ۱۴ روی
 *  سطحِ درونی با فشارِ ۰٫۹۴؛ `' '` جای خالی؛ `KeypadInput.BACKSPACE` پاک کردن. رقم‌ها مثلِ
 *  هر صفحه‌کلیدِ گوشی همیشه چپ‌به‌راست‌اند.
 */
@Composable
fun SaleKeyGrid(keys: List<Char>, onPress: (Char) -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
  CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
    Column(modifier.alpha(if (enabled) 1f else 0.45f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
      keys.chunked(3).forEach { row ->
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          row.forEach { k -> SaleKey(k, Modifier.weight(1f), enabled) { onPress(k) } }
        }
      }
    }
  }
}

@Composable
private fun SaleKey(k: Char, modifier: Modifier, enabled: Boolean, onPress: () -> Unit) {
  val c = PhoneV3.colors
  val shape = RoundedCornerShape(PhoneV3.tileRadius)
  if (k == ' ') { Spacer(modifier.height(KEY_HEIGHT)); return }
  val label = if (k == KeypadInput.BACKSPACE) StrSale.backspace else k.toString()
  Box(
    modifier.height(KEY_HEIGHT).clip(shape).background(c.tile)
      .v3Pressable(label = label, scale = 0.94f, onClick = { if (enabled) onPress() }),
    contentAlignment = Alignment.Center,
  ) {
    if (k == KeypadInput.BACKSPACE) {
      Icon(Icons.AutoMirrored.Outlined.Backspace, contentDescription = null, tint = c.icon, modifier = Modifier.size(22.dp))
    } else {
      Text(k.toString(), style = V3Type.title.copy(fontSize = 22.sp, fontWeight = FontWeight.SemiBold), color = c.ink)
    }
  }
}

private val KEY_HEIGHT = 54.dp
