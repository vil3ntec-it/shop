package ir.vil3ntec.tohid.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.core.i18n.StrLocalNet
import ir.vil3ntec.tohid.hw.LocalNetworkAccess
import ir.vil3ntec.tohid.ui.theme.Shop

// i18n: سه‌زبانه

/**
 *  HW-13 — در برگهٔ «وای‌فای» ِ چاپگر (`PrinterDialog`): اگر اندروید ۱۷ اجازهٔ «شبکهٔ محلی» می‌خواهد و
 *  داده نشده، یک خط و دکمهٔ «اجازه دادن». لازم نیست (امروز، با هدفِ ۳۵، همیشه) ⇒ فقط همان فاصلهٔ ۸
 *  که پیش‌تر همین‌جا بود. فایلِ مرزی: اجازه از `LocalNetworkAccess`.
 */
@Composable
internal fun LocalNetworkNotice(context: android.content.Context) {
  //  «لازم بودن» در عمرِ برنامه عوض نمی‌شود (هدف و نسخهٔ اندروید)، پس شاخه پایدار است
  if (LocalNetworkAccess.needed(context)) {
    var granted by remember { mutableStateOf(LocalNetworkAccess.granted(context)) }
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it }
    if (!granted) {
      Spacer(Modifier.height(8.dp))
      Text(StrLocalNet.needed, style = MaterialTheme.typography.bodySmall, color = Shop.colors.warning)
      Spacer(Modifier.height(6.dp))
      Button(onClick = { ask.launch(LocalNetworkAccess.PERMISSION) }) { Text(StrLocalNet.allow) }
    }
  }
  Spacer(Modifier.height(8.dp))
}
