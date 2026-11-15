package ir.vil3ntec.tohid.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.BuildConfig
import ir.vil3ntec.tohid.core.platform.StoreChannel
import ir.vil3ntec.tohid.data.ShopData
import ir.vil3ntec.tohid.data.ShopStore
import ir.vil3ntec.tohid.fa
import ir.vil3ntec.tohid.update.UpdateManager
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.DeliveryDining
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.LocalOffer
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material.icons.filled.NewReleases
import androidx.compose.material.icons.filled.ManageSearch
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.ShoppingBasket
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.WorkspacePremium
import ir.vil3ntec.tohid.ui.theme.Shop
import ir.vil3ntec.tohid.core.i18n.StrNav
import ir.vil3ntec.tohid.core.i18n.Str
import ir.vil3ntec.tohid.core.i18n.StrShell

/**
 *  بیشتر — وضعیت برنامه، به‌روزرسانی، و راهِ ورود به بخش‌هایی که در
 *  نوارِ پایین جا نمی‌شوند.
 */
@Composable
fun MoreScreen(store: ShopStore, d: ShopData, onOpen: (String) -> Unit) {
  //  گوشی نسخهٔ ۳ (بندِ F): کارت‌های گروهیِ r5 و «تنظیمات» در ته — `PhoneMoreScreen`.
  if (!ir.vil3ntec.tohid.ui.v3.nav.LocalDeskLayout.current) {
    PhoneMoreScreen(d, onOpen = onOpen)
    return
  }
  //  کامپیوتر (e-desk-d1): شبکهٔ کارت‌های بخش در گروه‌های منوی کناری، نه فهرستِ کشیدهٔ پیشین — همان بخش‌ها و همان «وضعیت»؛
  //  به‌روزرسانی در کارتِ «نسخه و به‌روزرسانی» (`app/updates`، همان `UpdateControls`)
  ir.vil3ntec.tohid.ui.desk.DeskMoreScreen(d, onOpen)
}

/** نشانیِ مخزنِ به‌روزرسانی — ثابت؛ فقط برای عیب‌یابی با همین کلید عوض می‌شود */
@Composable
internal fun rememberUpdateRepo(): String {
  val context = LocalContext.current
  return remember {
    context.getSharedPreferences("tohid", android.content.Context.MODE_PRIVATE)
      .getString("update_repo", "vil3ntec-it/shop") ?: "vil3ntec-it/shop"
  }
}

/**
 *  بررسی، دانلود و نصبِ نسخهٔ تازه — همان کارِ پیشینِ «بیشتر»، جدا شده تا
 *  صفحهٔ «بیشتر»ِ گوشی (زیرصفحهٔ «نسخه و به‌روزرسانی») هم همین را بکشد.
 *  وضعیت مالِ `UpdateManager` است، نه این صفحه: بیرون رفتن دانلود را نمی‌کشد.
 */
@Composable
internal fun UpdateControls(repo: String) {
  val context = LocalContext.current
  val status = UpdateManager.message
  val found = UpdateManager.release
  val progress = UpdateManager.progress
  val busy = UpdateManager.busy
  val ready = UpdateManager.ready
  val needsPermission = UpdateManager.needsPermission
  Column {
      Text(
        StrNav.updateNote,
        style = MaterialTheme.typography.bodySmall,
        color = Shop.colors.muted,
      )
      Spacer(Modifier.height(12.dp))


      if (progress in 0..100) {
        LinearProgressIndicator(
          progress = { progress / 100f },
          modifier = Modifier.fillMaxWidth(),
          color = Shop.colors.primary,
        )
        Spacer(Modifier.height(6.dp))
        Row(
          Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
        ) {
          Text(
            StrNav.downloading(progress.fa()) + (UpdateManager.note?.let { " · $it" } ?: ""),
            style = MaterialTheme.typography.labelSmall,
            color = Shop.colors.muted,
          )
          Text(
            StrNav.stop,
            style = MaterialTheme.typography.labelSmall,
            color = Shop.colors.danger,
            modifier = Modifier.clickable { UpdateManager.cancel() },
          )
        }
        Spacer(Modifier.height(4.dp))
        Text(
          StrNav.downloadContinues,
          style = MaterialTheme.typography.labelSmall,
          color = Shop.colors.muted2,
        )
        Spacer(Modifier.height(12.dp))
      }

      val release = found
      if (release == null) {
        Button(
          enabled = !busy,
          onClick = { UpdateManager.check(context, repo, BuildConfig.VERSION_NAME) },
          modifier = Modifier.fillMaxWidth(),
        ) { Text(StrNav.checkUpdate) }
      } else {
        Text(
          StrNav.versionReady(release.version),
          style = MaterialTheme.typography.titleSmall,
          color = Shop.colors.success,
        )
        if (release.notes.isNotBlank()) {
          Spacer(Modifier.height(6.dp))
          Text(
            release.notes.take(400),
            style = MaterialTheme.typography.bodySmall,
            color = Shop.colors.muted,
          )
        }
        Spacer(Modifier.height(12.dp))
        Button(
          enabled = !busy,
          // فایلی که از قبل کامل گرفته شده دوباره گرفته نمی‌شود؛ کسی که
          // سرِ پرسشِ نصب «نه» زده، بارِ بعد یک‌راست به نصب می‌رسد
          onClick = { UpdateManager.download(context) },
          modifier = Modifier.fillMaxWidth(),
        ) { Text(if (ready != null) StrNav.installVersion(release.version) else StrNav.downloadInstall) }

        /*
         *  اجازهٔ نصب.
         *
         *  از اندروید ۸ به بعد، فایل هرچقدر هم سالم گرفته شده باشد، تا
         *  کاربر در تنظیماتِ گوشی «نصب از این برنامه» را روشن نکند
         *  پنجرهٔ نصب باز نمی‌شود — و هیچ خطایی هم دیده نمی‌شود. این
         *  دکمه یک‌راست همان صفحه را باز می‌کند.
         */
        if (needsPermission) {
          Spacer(Modifier.height(8.dp))
          Button(
            onClick = { UpdateManager.allowInstall(context) },
            modifier = Modifier.fillMaxWidth(),
          ) { Text(StrNav.openInstallPermission) }
        }

        // راهِ آخر، وقتی دانلودِ داخلی نمی‌گیرد
        Spacer(Modifier.height(4.dp))
        TextButton(
          onClick = { UpdateManager.openInBrowser(context, repo) },
          modifier = Modifier.fillMaxWidth(),
        ) {
          Text(
            StrNav.viaBrowser,
            style = MaterialTheme.typography.labelLarge,
            color = Shop.colors.muted,
          )
        }
      }

      status?.let {
        Spacer(Modifier.height(8.dp))
        Text(it, style = MaterialTheme.typography.bodySmall, color = Shop.colors.muted)
      }
    }
}
