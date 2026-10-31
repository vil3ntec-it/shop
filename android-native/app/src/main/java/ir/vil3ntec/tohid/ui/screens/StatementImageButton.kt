package ir.vil3ntec.tohid.ui.screens

import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import ir.vil3ntec.tohid.data.ShopData
import ir.vil3ntec.tohid.data.ShopPolicy
import ir.vil3ntec.tohid.print.StatementImage
import ir.vil3ntec.tohid.todayIso
import ir.vil3ntec.tohid.ui.Platform
import kotlinx.coroutines.launch

/**
 *  «تصویرِ صورت‌حساب» (CRM-05) کنارِ CSV/چاپ/اکسلِ صورت‌حساب — گوشی می‌فرستد، کامپیوتر PNG ذخیره
 *  می‌کند (`StatementImage`). فقط با «گزارش‌های مشتری» روشن (`ShopPolicy.crmReports`، پیش‌فرض
 *  **خاموش**)؛ خاموش ⇒ هیچ دکمه‌ای، همان صفحهٔ امروز. تا کار تمام نشده دوباره زده نمی‌شود.
 */
@Composable
internal fun StatementImageButton(d: ShopData, debtorId: String, onMessage: (String) -> Unit, modifier: Modifier = Modifier) {
  val context = LocalContext.current
  if (!ShopPolicy.crmReports(context)) return
  val scope = rememberCoroutineScope()
  var busy by remember { mutableStateOf(false) }
  OutlinedButton(
    enabled = !busy,
    onClick = {
      busy = true
      scope.launch {
        try {
          onMessage(StatementImage.share(context, d, debtorId, todayIso()))
        } finally {
          busy = false
        }
      }
    },
    modifier = modifier,
  ) { Text(if (Platform.DESKTOP) "تصویرِ صورت‌حساب (PNG)" else "تصویرِ صورت‌حساب") }
}
