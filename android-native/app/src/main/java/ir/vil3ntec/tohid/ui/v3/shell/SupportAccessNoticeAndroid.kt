package ir.vil3ntec.tohid.ui.v3.shell

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import ir.vil3ntec.tohid.data.ShopRole
import ir.vil3ntec.tohid.data.repo.Backend
import ir.vil3ntec.tohid.data.repo.SupportAccessRepository
import ir.vil3ntec.tohid.ui.v3.LocalV3Toasts

/*
 *  پروندهٔ سکو (`*Android.kt`): مخزنِ دسترسیِ پشتیبانی و نقشِ همین دستگاه `Context` می‌خواهند؛ نوار و برگه بی‌پلتفرم در
 *  `SupportAccessNotice.kt` است.
 */

/** TEN-04 — نوارِ «پشتیبانی در حالِ مشاهده» (`ShellNotice`): فقط برای صاحبِ دکانِ واردشده؛ سرور هم فقط به صاحب جواب می‌دهد. */
@Composable
fun SupportAccessNotice() {
  val context = LocalContext.current
  val repo = remember(context) { SupportAccessRepository(Backend.api(context)) }
  val owner = Backend.isReady(context) && ShopRole.current(context) == ShopRole.OWNER
  val toasts = LocalV3Toasts.current
  SupportAccessNoticeContent(repo, now = { System.currentTimeMillis() }, enabled = owner, say = { toasts?.show(it) })
}
