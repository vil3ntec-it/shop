package ir.vil3ntec.tohid.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import ir.vil3ntec.tohid.core.net.ApiClient
import ir.vil3ntec.tohid.data.ShopRole
import ir.vil3ntec.tohid.data.repo.Backend

/*
 *  مرزِ پلتفرمِ سه صفحهٔ سروری (RSK-08، NTF-10، SEC-10): ساعت، کلیدِ یکتا و درِ سرورِ کاربرِ واردشده — تا حالت‌ها
 *  (`OrderEvidenceState`، `CustomerErasureState`، `LateApologySettings`) بی وابستگیِ پلتفرم بمانند.
 */

internal fun platformNow(): Long = System.currentTimeMillis()

/** کلیدِ یکتای یک نوشتنِ اختلاف (۸ تا ۶۴ نویسه، همان `KEY_RE` ِ سرور) */
internal fun evidenceRequestKey(): String = "ev-" + java.util.UUID.randomUUID().toString()

/** درِ سرور برای کاربرِ واردشده؛ بیرون از حساب ⇒ null (هیچ‌چیز) */
@Composable
fun lateApologyApi(): ApiClient? {
  val context = LocalContext.current
  return if (Backend.isReady(context)) Backend.api(context) else null
}

/** درِ سرورِ حذفِ دادهٔ مشتری فقط برای صاحبِ واردشده؛ وگرنه null (هیچ دکمه‌ای) */
@Composable
internal fun rememberErasureApi(): ApiClient? {
  val context = LocalContext.current
  val owner = ShopRole.current(context) == ShopRole.OWNER && Backend.isReady(context)
  return remember(context, owner) { if (owner) Backend.api(context) else null }
}
