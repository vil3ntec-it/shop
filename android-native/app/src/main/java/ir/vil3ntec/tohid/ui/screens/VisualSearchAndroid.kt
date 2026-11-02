package ir.vil3ntec.tohid.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import ir.vil3ntec.tohid.core.net.ApiClient
import ir.vil3ntec.tohid.data.repo.Backend

/**
 *  مرزِ پلتفرمِ «جست‌وجو با عکس» (AI-11، `VisualSearchUi.kt`): کارگزارِ API فقط وقتی نشانیِ سرور و حساب هست؛
 *  وگرنه null ⇒ نه درخواستی، نه دکمه‌ای.
 */
@Composable
internal fun rememberVisualSearchApi(): ApiClient? {
  val context = LocalContext.current
  return remember(context) { if (Backend.isReady(context)) Backend.api(context) else null }
}
