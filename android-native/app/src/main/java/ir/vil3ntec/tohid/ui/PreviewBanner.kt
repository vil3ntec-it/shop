package ir.vil3ntec.tohid.ui

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import ir.vil3ntec.tohid.BuildConfig
import ir.vil3ntec.tohid.data.repo.Backend
import ir.vil3ntec.tohid.core.i18n.StrShell

/**
 *  «نسخهٔ پیش‌نمایش» — فقط در buildTypeِ `preview` (`BuildConfig.PREVIEW`).
 *
 *  ⛔ نوارِ زردِ بالای هر صفحه برداشته شد (صاحب‌کار ۱۴۰۵/۰۸/۲۴، کارِ `claude/e-phone-p0`: کالبدِ یکسانِ صفحه‌ها — هیچ نوار و کارتِ
 *  سرخطی در بدنه). نشانِ پیش‌نمایش فقط کنارِ نسخه در «بیشتر» و «درباره برنامه» است ([previewVersion])، و هشدارِ یک‌باره همان:
 *
 *  پیش‌نمایش بی حساب و فقط روی همین دستگاه هم کار می‌کند. اگر کسی با آن وارد حسابِ واقعی شد (سرور تنظیم و نشست باز)، **یک بار**
 *  هشدار می‌دهد که این نسخه برای دیدن است، نه کارِ روزانه. «یک بار» در `tohid_preview` می‌ماند. پوسته (`AppRoot`) یک بار سوارش
 *  می‌کند، نه هر صفحه.
 */
@Composable
fun PreviewAccountWarning() {
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
  if (warn) {
    AlertDialog(
      onDismissRequest = { warn = false },
      confirmButton = { TextButton(onClick = { warn = false }) { Text(StrShell.gotIt) } },
      title = { Text(StrShell.previewBuild) },
      text = { Text(StrShell.previewNote) },
    )
  }
}

/** نسخهٔ برنامه با نشانِ «نسخهٔ پیش‌نمایش» در buildTypeِ `preview` — تنها جای نشانِ پیش‌نمایش («بیشتر»، «درباره برنامه») */
fun previewVersion(version: String, preview: Boolean = BuildConfig.PREVIEW): String =
  if (preview) "$version · ${StrShell.previewBuild}" else version
