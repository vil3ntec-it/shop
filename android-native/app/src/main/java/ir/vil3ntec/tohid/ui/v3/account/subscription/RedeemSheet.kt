package ir.vil3ntec.tohid.ui.v3.account.subscription

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.core.i18n.Str
import ir.vil3ntec.tohid.core.i18n.StrAccount
import ir.vil3ntec.tohid.data.repo.Backend
import ir.vil3ntec.tohid.sync.LicenseGuard
import ir.vil3ntec.tohid.sync.SyncStore
import ir.vil3ntec.tohid.sync.v1.HeartbeatCache
import ir.vil3ntec.tohid.ui.screens.Haptics
import ir.vil3ntec.tohid.ui.screens.SubscriptionPulse
import ir.vil3ntec.tohid.ui.theme.PhoneV3
import ir.vil3ntec.tohid.ui.theme.V3Tone
import ir.vil3ntec.tohid.ui.v3.V3Button
import ir.vil3ntec.tohid.ui.v3.V3IconButton
import ir.vil3ntec.tohid.ui.v3.V3Message
import ir.vil3ntec.tohid.ui.v3.V3PageSwitch
import ir.vil3ntec.tohid.ui.v3.V3SuccessCheck
import ir.vil3ntec.tohid.ui.v3.V3Segmented
import ir.vil3ntec.tohid.ui.v3.V3Sheet
import ir.vil3ntec.tohid.ui.v3.V3TextField
import ir.vil3ntec.tohid.ui.v3.V3Type
import ir.vil3ntec.tohid.ui.v3.account.AccountLinks
import ir.vil3ntec.tohid.ui.v3.account.AccountSession
import ir.vil3ntec.tohid.ui.v3.account.DraftCodeCells
import ir.vil3ntec.tohid.ui.v3.nav.RouteScope
import kotlinx.coroutines.launch

/** حالتِ برگهٔ «کد اشتراک دارم» */
internal data class RedeemUi(
  val tab: Int = 0,
  val code: String = "",
  val signedIn: Boolean,
  val busy: Boolean = false,
  /** پیامِ نتیجه: (موفق؟، متن) */
  val result: Pair<Boolean, String>? = null,
  val computerId: String = "—",
  val offline: String = "",
  val offlineActive: String? = null,
  val offlineResult: Pair<Boolean, String>? = null,
)

internal class RedeemActions(
  val tab: (Int) -> Unit,
  val code: (String) -> Unit,
  val activate: () -> Unit,
  val signIn: () -> Unit,
  val copyId: () -> Unit,
  val offline: (String) -> Unit,
  val applyOffline: () -> Unit,
)

/**
 *  «کد اشتراک دارم»: دو راه در یک برگه — کدِ شش‌رقمیِ ایمیل (`redeemVip`، حساب
 *  لازم است) و کدِ آفلاینِ همین کامپیوتر (`LicenseGuard.applyOffline`، بی اینترنت).
 */
@Composable
internal fun RedeemContent(ui: RedeemUi, actions: RedeemActions, onDismiss: () -> Unit) {
  val c = PhoneV3.colors
  V3Sheet(onDismiss = onDismiss, title = StrAccount.haveCode) { _ ->
    V3Segmented(listOf(StrAccount.sixDigitCode, StrAccount.offlineCode), ui.tab, actions.tab)
    Spacer(Modifier.height(16.dp))
    //  دو راه با همان حرکتِ صفحهٔ کیت (به سوی تبِ انتخاب‌شده) و بلندیِ فنری
    V3PageSwitch(ui.tab, depth = { it }, modifier = Modifier.fillMaxWidth()) { tab ->
      Column(Modifier.fillMaxWidth()) {
        if (tab == 0) {
          Text(StrAccount.sixDigitHint, style = V3Type.callout, color = c.inkMuted)
          Spacer(Modifier.height(12.dp))
          DraftCodeCells(ui.code, actions.code, StrAccount.sixDigitCode)
          Spacer(Modifier.height(16.dp))
          V3Button(StrAccount.activate, actions.activate, Modifier.fillMaxWidth(), enabled = PlansModel.canRedeem(ui.code, ui.signedIn), loading = ui.busy, haptic = true)
          if (!ui.signedIn) {
            Spacer(Modifier.height(12.dp))
            V3Message(V3Tone.INFO, StrAccount.redeemSignIn, action = Str.signIn, onAction = actions.signIn)
          }
          V3SuccessCheck(ui.result?.first == true, Modifier.align(Alignment.CenterHorizontally).padding(top = 12.dp))
          ui.result?.let { (ok, text) -> Spacer(Modifier.height(12.dp)); V3Message(if (ok) V3Tone.SUCCESS else V3Tone.ERROR, text) }
        } else {
          Text(ui.offlineActive?.let { StrAccount.offlineActive(it) } ?: StrAccount.offlineHint, style = V3Type.callout, color = c.inkMuted)
          Spacer(Modifier.height(12.dp))
          Text(StrAccount.computerId, style = V3Type.footnote, color = c.inkMuted)
          Spacer(Modifier.height(6.dp))
          Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(c.tile).padding(start = 14.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
              Text(ui.computerId, style = V3Type.headline.copy(fontFamily = FontFamily.Monospace), color = c.ink, modifier = Modifier.weight(1f))
            }
            V3IconButton(Icons.Outlined.ContentCopy, StrAccount.copyAction, onClick = actions.copyId)
          }
          Spacer(Modifier.height(12.dp))
          V3TextField(ui.offline, actions.offline, StrAccount.offlineField, singleLine = false)
          Spacer(Modifier.height(12.dp))
          V3Button(StrAccount.applyCode, actions.applyOffline, Modifier.fillMaxWidth(), enabled = ui.offline.isNotBlank(), haptic = true)
          V3SuccessCheck(ui.offlineResult?.first == true, Modifier.align(Alignment.CenterHorizontally).padding(top = 12.dp))
          ui.offlineResult?.let { (ok, text) -> Spacer(Modifier.height(12.dp)); V3Message(if (ok) V3Tone.SUCCESS else V3Tone.ERROR, text) }
          Text(StrAccount.offlineOnce, style = V3Type.footnote, color = c.inkMuted, modifier = Modifier.padding(top = 10.dp))
        }
      }
    }
  }
}

/** مسیرِ `subscription/redeem`: همان دو کارِ `VipScreen`/`OfflineCodeCard` ِ پیشین */
@Composable
internal fun RouteScope.RedeemRoute() {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  val clipboard = LocalClipboardManager.current
  val store = remember { SyncStore(context) }
  var ui by remember {
    mutableStateOf(
      RedeemUi(
        signedIn = !store.accessToken.isNullOrBlank(),
        computerId = runCatching { LicenseGuard.computerId(context) }.getOrDefault("—"),
        offlineActive = LicenseGuard.offline(context, store)?.let { it.payload?.planTitle.orEmpty() },
      ),
    )
  }
  RedeemContent(
    ui,
    RedeemActions(
      tab = { ui = ui.copy(tab = it) },
      code = { ui = ui.copy(code = it, result = null) },
      activate = {
        ui = ui.copy(busy = true, result = null)
        scope.launch {
          Backend.support(context).redeemVip(ui.code)
            .onSuccess {
              ui = ui.copy(code = "", result = true to it.message.ifBlank { StrAccount.activated })
              Haptics.success(context)
              //  وضعیتِ اشتراک همان لحظه تازه می‌شود، وگرنه صفحه هنوز «اشتراک ندارید» می‌گفت
              SubscriptionPulse.refresh(context)
              runCatching { HeartbeatCache(context).forget() }
              LicenseGuard.changed()
              AccountSession.changed()
            }
            .onFailure { ui = ui.copy(result = false to it.userMessage) }
          ui = ui.copy(busy = false)
        }
      },
      signIn = { nav.open(AccountLinks.SIGN_IN) },
      copyId = {
        clipboard.setText(AnnotatedString(ui.computerId))
        Haptics.confirm(context)
        toasts.show(StrAccount.idCopied, V3Tone.SUCCESS)
      },
      offline = { ui = ui.copy(offline = it.take(400), offlineResult = null) },
      applyOffline = {
        val err = LicenseGuard.applyOffline(context, store, ui.offline)
        if (err == null) {
          val active = LicenseGuard.offline(context, store)
          ui = ui.copy(offline = "", offlineActive = active?.payload?.planTitle.orEmpty(), offlineResult = true to PlansModel.offlineOk(active?.daysLeft()))
          Haptics.success(context)
          AccountSession.changed()
        } else {
          ui = ui.copy(offlineResult = false to err)
        }
      },
    ),
    onDismiss = { back() },
  )
}
