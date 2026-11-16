package ir.vil3ntec.tohid.ui

import ir.vil3ntec.tohid.ui.components.common.ShopAlertDialog
import ir.vil3ntec.tohid.ui.components.common.ShopTextButton
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.BuildConfig
import ir.vil3ntec.tohid.data.repo.Backend
import ir.vil3ntec.tohid.ui.theme.Shop
import ir.vil3ntec.tohid.core.i18n.StrShell

/** نسخه با نشانِ «نسخهٔ پیش‌نمایش» (فقط buildTypeِ `preview`) — «درباره» و ردیفِ نسخهٔ تنظیمات */
fun previewVersionLabel(version: String): String = if (BuildConfig.PREVIEW) "$version · ${StrShell.previewBuild}" else version

/**
 *  نوارِ «نسخهٔ پیش‌نمایش» — فقط در buildTypeِ `preview` (`BuildConfig.PREVIEW`).
 *
 *  پیش‌نمایش بی حساب و فقط روی همین دستگاه هم کار می‌کند. اگر کسی با آن وارد
 *  حسابِ واقعی شد (سرور تنظیم و نشست باز)، **یک بار** هشدار می‌دهد که این
 *  نسخه برای دیدن است، نه کارِ روزانه. «یک بار» در `tohid_preview` می‌ماند.
 *
 *  [strip] = نوارِ زردِ بالای صفحه. گوشی (۱۴۰۵/۰۸/۲۴، صاحب‌کار: صفحه‌ها «خیلی شلوغ») نوار را روی هیچ صفحه‌ای نمی‌کشد —
 *  `PreviewBanner(strip = false)` یک بار در پوسته فقط هشدار را نگه می‌دارد و نشانِ پیش‌نمایش کنارِ شمارهٔ نسخه در «درباره» و
 *  ردیفِ نسخهٔ تنظیمات است ([previewVersionLabel]). کامپیوتر همان نوارِ زیرِ سربرگ.
 */
@Composable
fun PreviewBanner(strip: Boolean = true) {
  if (!BuildConfig.PREVIEW) return
  val context = LocalContext.current
  var warn by remember { mutableStateOf(false) }
  LaunchedEffect(Unit) {
    val prefs = context.getSharedPreferences("tohid_preview", android.content.Context.MODE_PRIVATE)
    //  ورود در صفحهٔ دیگری رخ می‌دهد؛ نگاهِ سبک هر چند ثانیه تا اولین بار
    while (!prefs.getBoolean("account_warned", false)) {
      if (runCatching { Backend.isReady(context) }.getOrDefault(false)) {
        prefs.edit().putBoolean("account_warned", true).apply()
        warn = true
        break
      }
      kotlinx.coroutines.delay(4_000)
    }
  }
  if (strip) Text(
    StrShell.previewBuild,
    modifier = Modifier
      .fillMaxWidth()
      .background(Shop.colors.warningTint)
      .padding(vertical = 2.dp),
    style = MaterialTheme.typography.labelSmall,
    color = Shop.colors.warning,
    textAlign = TextAlign.Center,
  )
  if (warn) {
    ShopAlertDialog(
      onDismissRequest = { warn = false },
      confirmButton = { ShopTextButton(onClick = { warn = false }) { Text(StrShell.gotIt) } },
      title = { Text(StrShell.previewBuild) },
      text = { Text(StrShell.previewNote) },
    )
  }
}
