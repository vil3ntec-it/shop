package ir.vil3ntec.tohid.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.BuildConfig
import ir.vil3ntec.tohid.ui.theme.Shop
import ir.vil3ntec.tohid.update.UpdateManager

/**
 *  نوارِ «نسخهٔ تازه» بالای همهٔ صفحه‌ها — همان جای `SubscriptionBanner`.
 *
 *  بررسی خودکار است (`UpdateManager.startAuto`): چند ثانیه پس از باز شدن و
 *  بعد هر ۶ ساعت، از سرورِ خودمان و اگر نشد GitHub. روی کامپیوتر فایل در
 *  پس‌زمینه گرفته می‌شود و دکمه فقط نصب است: نصابِ بی‌صدا برنامه را
 *  می‌بندد، عوض می‌کند و دوباره باز می‌کند. فایلی که از داخلِ برنامه گرفته
 *  شود پنجرهٔ SmartScreenِ ویندوز را هم نمی‌آورد (آن پنجره فقط برای فایلِ
 *  دانلودشده با مرورگر است).
 *  «بعداً» نوار را تا بازکردنِ بعدیِ برنامه پنهان می‌کند.
 */
@Composable
fun UpdateBanner() {
  //  ⛔ نسخهٔ کافه‌بازار: «برنامه‌ای که از بازار گرفته شده فقط از بازار به‌روز
  //  می‌شود» — نه بررسی، نه دانلود، نه نصب (و اجازهٔ نصب هم در مانیفستش نیست)
  if (BuildConfig.STORE == "bazaar") return
  val context = LocalContext.current
  LaunchedEffect(Unit) {
    val prefs = context.getSharedPreferences("tohid", android.content.Context.MODE_PRIVATE)
    val repo = prefs.getString("update_repo", "vil3ntec-it/shop") ?: "vil3ntec-it/shop"
    UpdateManager.startAuto(context, repo, BuildConfig.VERSION_NAME)
  }
  val found = UpdateManager.release ?: return
  var hidden by remember(found.version) { mutableStateOf(false) }
  if (hidden) return
  val progress = UpdateManager.progress
  val ready = UpdateManager.ready
  val busy = UpdateManager.busy
  val tint = Shop.colors.primary
  Row(
    Modifier
      .fillMaxWidth()
      .padding(horizontal = 14.dp, vertical = 8.dp)
      .clip(RoundedCornerShape(14.dp))
      .background(Shop.colors.primaryTint)
      .padding(horizontal = 12.dp, vertical = 8.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Column(Modifier.weight(1f)) {
      Text(
        when {
          progress >= 0 -> "در حالِ گرفتنِ نسخهٔ ${found.version}… ${progress}٪" +
            (UpdateManager.note?.let { " · $it" } ?: "")
          ready != null && Platform.DESKTOP -> "نسخهٔ ${found.version} آماده است. «به‌روزرسانی» برنامه را چند ثانیه می‌بندد و دوباره باز می‌کند؛ دفتر سرِ جایش می‌ماند."
          ready != null -> "نسخهٔ ${found.version} گرفته شد — آمادهٔ نصب."
          else -> "نسخهٔ تازهٔ فروشگاه من آمد: ${found.version}"
        },
        style = MaterialTheme.typography.labelMedium,
        color = tint,
      )
      if (progress >= 0) {
        LinearProgressIndicator(
          progress = { progress / 100f },
          modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
        )
      }
      UpdateManager.message?.takeIf { progress < 0 && ready == null && !busy }?.let {
        Text(it, style = MaterialTheme.typography.labelSmall, color = Shop.colors.danger)
      }
    }
    if (progress < 0) {
      TextButton(onClick = { hidden = true }) { Text("بعداً") }
      Button(
        enabled = !busy,
        onClick = { if (ready != null) UpdateManager.install(context) else UpdateManager.download(context) },
      ) { Text(if (ready != null && !Platform.DESKTOP) "نصب" else "به‌روزرسانی") }
    }
  }
}
