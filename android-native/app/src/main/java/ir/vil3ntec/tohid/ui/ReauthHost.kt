package ir.vil3ntec.tohid.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import ir.vil3ntec.tohid.data.repo.Backend
import ir.vil3ntec.tohid.ui.v3.onboarding.ReauthSheet

/**
 *  میزبانِ «تأییدِ دوبارهٔ هویت» — یک بار در `AppRoot`، پس اندروید و کامپیوتر هر دو.
 *
 *  سرور کارِ حساس (گذاشتنِ رمز برای حسابِ بی‌رمز، افزودنِ شماره، مجوز و نقشِ
 *  کارمندان…) را از نشستِ کهنه با `reauth_required` رد می‌کند. `ApiClient`
 *  آن را به `ReauthGate` می‌دهد، این‌جا برگه باز می‌شود و پس از تأییدِ سرور
 *  همان درخواست یک بار دوباره می‌رود — صفحه‌ها هیچ کارِ جدایی ندارند.
 *
 *  برگه (`ui/v3/onboarding/ReauthSheet`، G1) راهِ اثباتی را می‌خواهد که سرور می‌پذیرد
 *  (`POST /auth/reauth`): رمزِ حساب، یا برای حسابِ بی‌رمز کدی که به ایمیل (وگرنه شمارهٔ)
 *  خودِ حساب می‌رود. «انصراف» یعنی «انجام نده» و صفحه همان پیامِ فارسیِ سرور را نشان می‌دهد.
 */
@Composable
fun ReauthHost() {
  val context = LocalContext.current
  val gate = remember(context) { Backend.api(context).reauth }
  DisposableEffect(gate) {
    val detach = gate.attach()
    onDispose { detach() }
  }
  val ask by gate.asking.collectAsState()
  ask?.let { open -> key(open) { ReauthSheet(onDone = { ok -> open.done(ok) }) } }
}
