package ir.vil3ntec.tohid.ui.v3.account.subscription

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Devices
import androidx.compose.material.icons.outlined.PersonAdd
import androidx.compose.material.icons.outlined.WorkspacePremium
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.core.account.DeviceCap
import ir.vil3ntec.tohid.core.i18n.Str
import ir.vil3ntec.tohid.core.i18n.StrAccount
import ir.vil3ntec.tohid.core.i18n.StrSettings
import ir.vil3ntec.tohid.ui.AppNav
import ir.vil3ntec.tohid.ui.v3.V3Card
import ir.vil3ntec.tohid.ui.v3.V3EmptyState
import ir.vil3ntec.tohid.ui.v3.account.AccountLinks
import ir.vil3ntec.tohid.ui.v3.account.AccountPaths
import ir.vil3ntec.tohid.ui.v3.nav.LocalV3Nav

/**
 *  بخشِ پولیِ بسته (`VipGate`، فروش و قرض‌داران) روی کیتِ نسخهٔ ۳ — بی طلا.
 *  «حساب ندارد» و «اشتراکش تمام شده» دو کارِ بعدیِ متفاوت دارند: اولی ⇐ ساختِ حساب
 *  (`auth?mode=signup`، چون سربرگِ قدیم با کلیدِ حسابش دیگر نیست)، دومی ⇐
 *  «اشتراک و قیمت‌ها». بقیهٔ برنامه همیشه باز است و متن همین را می‌گوید.
 */
@Composable
internal fun LockedFeature(label: String, signedIn: Boolean, serverDenied: String? = null) {
  val nav = LocalV3Nav.current
  fun open(path: String) { nav?.open(path) ?: AppNav.go(path) }
  //  O-09: `device_limit` یعنی اشتراک هست و جای دستگاه نیست ⇒ «دستگاه‌های وصل»، نه «قیمت‌ها» (بن‌بست)
  val next = DeviceCap.lockedNext(signedIn, serverDenied)
  Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
    V3Card(Modifier.widthIn(max = 420.dp).fillMaxWidth(), radius = 16.dp) {
      V3EmptyState(
        icon = when (next) {
          DeviceCap.Next.SIGN_UP -> Icons.Outlined.PersonAdd
          DeviceCap.Next.DEVICES -> Icons.Outlined.Devices
          DeviceCap.Next.PLANS -> Icons.Outlined.WorkspacePremium
        },
        title = when (next) {
          DeviceCap.Next.SIGN_UP -> StrAccount.lockedNeedAccount(label)
          DeviceCap.Next.DEVICES -> StrAccount.lockedDeviceFull(label)
          DeviceCap.Next.PLANS -> StrAccount.lockedNeedPlan(label)
        },
        body = when (next) {
          DeviceCap.Next.SIGN_UP -> StrAccount.lockedAccountBody
          DeviceCap.Next.DEVICES -> StrAccount.lockedDeviceBody
          DeviceCap.Next.PLANS -> StrAccount.lockedPlanBody
        },
        action = when (next) {
          DeviceCap.Next.SIGN_UP -> Str.createAccount
          DeviceCap.Next.DEVICES -> StrSettings.sessionsRow
          DeviceCap.Next.PLANS -> Str.plans
        },
        onAction = {
          open(
            when (next) {
              DeviceCap.Next.SIGN_UP -> AccountLinks.SIGN_UP
              DeviceCap.Next.DEVICES -> AccountLinks.SESSIONS
              DeviceCap.Next.PLANS -> AccountPaths.PLANS
            },
          )
        },
      )
    }
  }
}
