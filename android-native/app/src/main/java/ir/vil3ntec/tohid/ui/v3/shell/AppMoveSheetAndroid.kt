package ir.vil3ntec.tohid.ui.v3.shell

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.core.i18n.StrAccount
import ir.vil3ntec.tohid.core.i18n.StrNav
import ir.vil3ntec.tohid.core.i18n.StrUpdate
import ir.vil3ntec.tohid.data.repo.Backend
import ir.vil3ntec.tohid.sync.AutoSync
import ir.vil3ntec.tohid.sync.v1.SyncV1Engine
import ir.vil3ntec.tohid.sync.v1.SyncV1Worker
import ir.vil3ntec.tohid.ui.theme.PhoneV3
import ir.vil3ntec.tohid.ui.theme.PhoneV3Roles
import ir.vil3ntec.tohid.ui.theme.V3ButtonKind
import ir.vil3ntec.tohid.ui.theme.V3Tone
import ir.vil3ntec.tohid.ui.v3.V3Button
import ir.vil3ntec.tohid.ui.v3.V3Sheet
import ir.vil3ntec.tohid.ui.v3.V3TextButton
import ir.vil3ntec.tohid.ui.v3.V3Type
import ir.vil3ntec.tohid.ui.v3.account.profile.SignOutModel
import ir.vil3ntec.tohid.update.AppMove
import ir.vil3ntec.tohid.update.UpdateManager

/**
 *  برگهٔ «برنامه با نامِ تازه آمده» (`update/AppMove.kt`) — یک جا در پوسته (`AppShellAndroid`)، روی هر صفحه.
 *  باز می‌شود وقتی `UpdateManager.moveAsk`: یک بار خودش برای هر شناسهٔ تازه، و هر بار که نوار یا کارتِ
 *  «به‌روزرسانی» زده شود. ⛔ پیشنهادِ پاک کردنِ برنامهٔ قدیمی فقط وقتی وارد است، صف خالی است و دفتر یک بار
 *  کامل رفته (`AppMove.advice`، همان شمارشِ قفلِ خروج)؛ کد هیچ داده‌ای پاک نمی‌کند و هیچ برنامه‌ای را برنمی‌دارد.
 */
@Composable
fun AppMoveHost() {
  if (!UpdateManager.moveAsk) return
  val found = UpdateManager.release ?: return
  if (found.movedTo.isEmpty()) return
  AppMoveSheet()
}

@Composable
private fun AppMoveSheet() {
  val context = LocalContext.current
  val c = PhoneV3.colors
  val v1 by SyncV1Engine.of(context).status.collectAsState()
  val pending = SignOutModel.pending(v1.queued, AutoSync.pendingCount)
  val signedIn = remember(v1) { runCatching { Backend.isReady(context) }.getOrDefault(false) }
  val advice = AppMove.advice(signedIn, pending, everSynced = v1.lastOkAt > 0)
  val ready = UpdateManager.ready != null
  val busy = UpdateManager.busy
  V3Sheet(onDismiss = { UpdateManager.closeMove() }, title = StrUpdate.movedTitle) { closer ->
    Text(StrUpdate.movedBody, style = V3Type.body, color = c.ink)
    Spacer(Modifier.height(8.dp))
    Text(StrUpdate.movedSide, style = V3Type.footnote, color = c.inkMuted)
    Spacer(Modifier.height(14.dp))
    //  e-newinstall: گام‌ها، شماره‌دار (`AppMove.steps`)
    Text(StrUpdate.movedStepsTitle, style = V3Type.headline, color = c.ink)
    AppMove.steps(advice).forEachIndexed { i, step ->
      val line = when (step) {
        AppMove.Step.KEEP_OLD -> StrUpdate.movedStepKeep
        AppMove.Step.SAME_ACCOUNT -> StrUpdate.movedStepAccount
        AppMove.Step.BACKUP_IF_STUCK -> StrUpdate.movedStepBackup
      }
      Text("${i + 1}. $line", style = V3Type.callout, color = c.ink, modifier = Modifier.padding(top = 6.dp))
    }
    Spacer(Modifier.height(14.dp))
    val (tone, text) = when (advice) {
      AppMove.Advice.SAFE -> V3Tone.SUCCESS to StrUpdate.movedSafe
      AppMove.Advice.PENDING -> V3Tone.WARNING to (if (pending > 0) StrUpdate.movedPending(pending) else StrUpdate.movedNotSynced)
      AppMove.Advice.NOT_SIGNED_IN -> V3Tone.WARNING to StrUpdate.movedNotSignedIn
    }
    val pair = PhoneV3Roles.tone(c, tone)
    val shape = RoundedCornerShape(12.dp)
    Text(
      text,
      style = V3Type.callout,
      color = pair.fg,
      modifier = Modifier.fillMaxWidth().clip(shape).background(pair.bg).border(1.dp, pair.border, shape).padding(12.dp),
    )
    if (advice == AppMove.Advice.PENDING) {
      Spacer(Modifier.height(8.dp))
      V3Button(StrAccount.syncNow, { runCatching { SyncV1Worker.now(context) } }, Modifier.fillMaxWidth(), kind = V3ButtonKind.SECONDARY)
    }
    Spacer(Modifier.height(16.dp))
    V3Button(
      if (ready) StrUpdate.movedInstallReady else StrUpdate.movedInstall,
      { closer.close { UpdateManager.download(context, moved = true) } },
      Modifier.fillMaxWidth(),
      enabled = !busy,
      haptic = true,
    )
    //  از اندروید ۸ به بعد تا «نصب از این برنامه» روشن نشود پنجرهٔ نصب باز نمی‌شود
    if (UpdateManager.needsPermission) {
      Spacer(Modifier.height(8.dp))
      V3Button(StrNav.openInstallPermission, { UpdateManager.allowInstall(context) }, Modifier.fillMaxWidth(), kind = V3ButtonKind.SECONDARY)
    }
    Spacer(Modifier.height(4.dp))
    V3TextButton(StrUpdate.movedLater, { closer.close() }, Modifier.align(Alignment.CenterHorizontally))
  }
}
