package ir.vil3ntec.tohid.ui.v3.shell

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import ir.vil3ntec.tohid.core.gateway.AccountCall
import ir.vil3ntec.tohid.core.gateway.PendingPayee
import ir.vil3ntec.tohid.core.i18n.StrPay
import ir.vil3ntec.tohid.ui.AppNav
import ir.vil3ntec.tohid.ui.screens.SettingsLayout
import ir.vil3ntec.tohid.ui.screens.rememberHesabPayDeps
import ir.vil3ntec.tohid.ui.theme.V3Tone
import ir.vil3ntec.tohid.ui.v3.nav.LocalV3Nav
import ir.vil3ntec.tohid.ui.v3.settings.SettingsPaths
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** هر چند وقت حالِ حساب‌پی دوباره پرسیده شود (فقط صاحب و با اینترنت؛ یک GET ِ سبک) */
private const val POLL_MS = 5 * 60_000L

/**
 *  «این من نبودم — لغو کن» در پوسته — نگهبانِ حسابِ گیرندهٔ حساب‌پی (سرور: `lib/hesabpay-guard.js`، مهاجرتِ ۲۰۵).
 *
 *  فقط برای صاحب و فقط وقتی حسابِ گیرندهٔ پولِ دکان «در انتظار» است (۲۴ ساعت پس از تغییر): روی هر صفحه و هر دستگاهِ
 *  صاحب، کنارِ پوشی که سرور به همهٔ دستگاه‌هایش فرستاده. یک لمس ⇐ لغو (سرور تأییدِ دوبارهٔ ورود می‌خواهد ⇐ همان پنجرهٔ
 *  `ReauthHost` خودش باز می‌شود؛ رمزِ حساب‌پی نه — کسی که حساب را عوض کرده شاید آن را هم دارد). زدنِ نوار ⇐ صفحهٔ
 *  «حساب‌پیِ دکان». غیرِ صاحب، بی اینترنت یا خطا ⇐ هیچ (سرور باز هم حسابِ کهنه را فعال نگه می‌دارد).
 */
@Composable
fun HesabPayChangeNotice() {
  val deps = rememberHesabPayDeps()
  val nav = LocalV3Nav.current
  val scope = rememberCoroutineScope()
  var pending by remember { mutableStateOf<PendingPayee?>(null) }
  var busy by remember { mutableStateOf(false) }
  var note by remember { mutableStateOf<String?>(null) }
  LaunchedEffect(Unit) {
    while (true) {
      if (deps.ownerReady(signedIn = true) && deps.env.online()) {
        (deps.env.account.status() as? AccountCall.Ok)?.let { pending = it.view.pending }
      }
      delay(POLL_MS)
    }
  }
  val p = pending ?: return
  DraftNoticeBar(
    tone = V3Tone.ERROR,
    text = StrPay.changeNotice(p.name.ifBlank { "—" }, p.last4),
    detail = note ?: StrPay.changeNoticeDetail,
    action = if (busy) null else StrPay.notMe,
    onAction = {
      busy = true
      scope.launch {
        try {
          when (val r = deps.env.account.cancelPending()) {
            is AccountCall.Ok -> { pending = r.view.pending; deps.env.forget(); note = null }
            is AccountCall.Refused -> note = r.message.ifBlank { null }
            is AccountCall.Offline -> note = r.message.ifBlank { null }
          }
        } finally {
          busy = false
        }
      }
    },
    onClick = {
      val route = SettingsPaths.section(SettingsLayout.HESAB_SHOP)
      if (nav != null) nav.open(route) else AppNav.go(route)
    },
  )
}
