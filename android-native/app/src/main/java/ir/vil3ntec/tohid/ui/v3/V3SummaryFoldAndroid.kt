package ir.vil3ntec.tohid.ui.v3

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import ir.vil3ntec.tohid.ui.components.common.ViewModePref

/** انبارِ «بسته» ِ خلاصه‌ها: همان `tohid-ui` ِ همین دستگاه (کنارِ `view_mode`) */
fun summaryFoldStore(context: Context): ViewModePref.Store =
  ViewModePref.Store.of(context.getSharedPreferences(ViewModePref.PREFS, Context.MODE_PRIVATE))

/** حالتِ صفحهٔ [key] روی همین دستگاه — بازترکیب و برگشت به صفحه همان را می‌خوانند */
@Composable
fun rememberSummaryFold(key: String): SummaryFoldState {
  val context = LocalContext.current
  return remember(key, context) {
    val store = summaryFoldStore(context)
    SummaryFoldState(store, key, SummaryFold.folded(store, key))
  }
}
