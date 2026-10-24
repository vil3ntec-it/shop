package ir.vil3ntec.tohid.ui.v3.settings.sections

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.SystemUpdate
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.BuildConfig
import ir.vil3ntec.tohid.core.i18n.StrNav
import ir.vil3ntec.tohid.ui.theme.PhoneV3
import ir.vil3ntec.tohid.ui.theme.PhoneV3Roles
import ir.vil3ntec.tohid.ui.theme.V3ButtonKind
import ir.vil3ntec.tohid.ui.v3.V3Button
import ir.vil3ntec.tohid.ui.v3.V3TextButton
import ir.vil3ntec.tohid.ui.v3.V3Type
import ir.vil3ntec.tohid.update.UpdateManager

/**
 *  بررسی، دانلود و نصبِ نسخهٔ تازه — همان کارِ `UpdateControls` ِ پیشین روی کیتِ
 *  نسخهٔ ۳. وضعیت مالِ `UpdateManager` است: بیرون رفتن از صفحه دانلود را نمی‌کشد.
 */
@Composable
internal fun UpdateCard(repo: String) {
  val context = LocalContext.current
  val c = PhoneV3.colors
  val progress = UpdateManager.progress
  val busy = UpdateManager.busy
  Column(Modifier.fillMaxWidth()) {
    Text(StrNav.updateNote, style = V3Type.footnote, color = c.inkMuted)
    Spacer(Modifier.height(12.dp))
    if (progress in 0..100) {
      LinearProgressIndicator(progress = { progress / 100f }, modifier = Modifier.fillMaxWidth(), color = PhoneV3Roles.focus(c), trackColor = c.tile)
      Spacer(Modifier.height(6.dp))
      Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(StrNav.downloading(progress) + (UpdateManager.note?.let { " · $it" } ?: ""), style = V3Type.footnote, color = c.inkMuted, modifier = Modifier.weight(1f))
        V3TextButton(StrNav.stop, { UpdateManager.cancel() }, danger = true)
      }
      Text(StrNav.downloadContinues, style = V3Type.footnote, color = c.inkMuted)
      Spacer(Modifier.height(12.dp))
    }
    val release = UpdateManager.release
    if (release == null) {
      V3Button(StrNav.checkUpdate, { UpdateManager.check(context, repo, BuildConfig.VERSION_NAME) }, Modifier.fillMaxWidth(), kind = V3ButtonKind.SECONDARY, icon = Icons.Outlined.SystemUpdate, loading = busy)
    } else {
      Text(StrNav.versionReady(release.version), style = V3Type.headline, color = PhoneV3Roles.brandText(c))
      if (release.notes.isNotBlank()) {
        Spacer(Modifier.height(6.dp))
        Text(release.notes.take(400), style = V3Type.footnote, color = c.inkMuted)
      }
      Spacer(Modifier.height(12.dp))
      //  فایلی که کامل گرفته شده دوباره گرفته نمی‌شود؛ یک‌راست به نصب
      V3Button(
        if (UpdateManager.ready != null) StrNav.installVersion(release.version) else StrNav.downloadInstall,
        { UpdateManager.download(context) },
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
      V3TextButton(StrNav.viaBrowser, { UpdateManager.openInBrowser(context, repo) }, Modifier.align(Alignment.CenterHorizontally))
    }
    UpdateManager.message?.let {
      Spacer(Modifier.height(8.dp))
      Text(it, style = V3Type.footnote, color = c.inkMuted)
    }
  }
}
