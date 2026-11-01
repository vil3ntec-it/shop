package ir.vil3ntec.tohid.ui.v3.online.delivery

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ir.vil3ntec.tohid.core.i18n.StrOnline
import ir.vil3ntec.tohid.ui.screens.Haptics
import ir.vil3ntec.tohid.ui.theme.PhoneV3
import ir.vil3ntec.tohid.ui.theme.PhoneV3Palette
import ir.vil3ntec.tohid.ui.theme.V3ButtonKind
import ir.vil3ntec.tohid.ui.v3.LocalV3Toasts
import ir.vil3ntec.tohid.ui.v3.V3Button
import ir.vil3ntec.tohid.ui.v3.V3Sheet
import ir.vil3ntec.tohid.ui.v3.V3Type

/**
 *  کدِ دریافت/تحویل — فقط همین یک بار دیده می‌شود (سرور فقط HMACش را دارد): برگهٔ بسته‌نشدنی
 *  (`dismissible = false`) تا با یک لمسِ بیرون گم نشود؛ فقط «دیدم» می‌بندد. «کپی» با لرزش.
 */
@Composable
fun CodeOnceSheet(orderNo: Long, code: String, onDismiss: () -> Unit) {
  val c = PhoneV3.colors
  val context = LocalContext.current
  val clipboard = LocalClipboardManager.current
  val toasts = LocalV3Toasts.current
  V3Sheet(onDismiss = onDismiss, title = StrOnline.dlCodeTitle(orderNo), dismissible = false, detents = false) { closer ->
    val shape = RoundedCornerShape(PhoneV3.groupRadius)
    Box(
      Modifier.fillMaxWidth().clip(shape).background(PhoneV3Palette.selected(c)).border(1.dp, c.hairline, shape).padding(vertical = 20.dp),
      contentAlignment = Alignment.Center,
    ) {
      Text(code, style = V3Type.largeTitle.copy(fontSize = 38.sp, letterSpacing = 8.sp), color = c.ink, textAlign = TextAlign.Center)
    }
    Spacer(Modifier.height(12.dp))
    Text(StrOnline.dlCodeHelp, style = V3Type.footnote, color = c.inkMuted, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
    Spacer(Modifier.height(20.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
      V3Button(StrOnline.dlCopy, {
        clipboard.setText(AnnotatedString(code))
        Haptics.confirm(context)
        toasts?.show(StrOnline.dlCopied)
      }, Modifier.weight(1f), kind = V3ButtonKind.SECONDARY, icon = Icons.Outlined.ContentCopy)
      V3Button(StrOnline.dlSeen, { closer.close() }, Modifier.weight(1.4f), haptic = true)
    }
  }
}
