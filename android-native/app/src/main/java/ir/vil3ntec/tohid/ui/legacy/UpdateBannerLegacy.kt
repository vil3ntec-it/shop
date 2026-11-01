/*
 *  ⛔ رابطِ امروز (پیش از نسخهٔ ۳) — رونوشتِ همان `ui/UpdateBanner.kt` ِ main، فقط با پسوندِ `Legacy`.
 *  وقتی پرچمِ `UiV3` خاموش است (پیش‌فرض) همین دیده می‌شود؛ نسخهٔ ۳ در فایلِ اصلی است.
 *  دستی ویرایش نکنید مگر برای همان رابطِ امروز؛ با روشن‌شدنِ نسخهٔ ۳ برای همه، این پوشه برداشته می‌شود.
 */
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
import ir.vil3ntec.tohid.ui.theme.Shape
import ir.vil3ntec.tohid.ui.components.common.PillTone
import ir.vil3ntec.tohid.ui.components.common.pillColors
import androidx.compose.foundation.clickable
import androidx.compose.ui.text.font.FontWeight
import ir.vil3ntec.tohid.update.ReleaseFeed
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
fun UpdateBannerLegacy() {
  //  ⛔ نسخهٔ کافه‌بازار: «برنامه‌ای که از بازار گرفته شده فقط از بازار به‌روز
  //  می‌شود» — نه بررسی، نه دانلود، نه نصب (و اجازهٔ نصب هم در مانیفستش نیست)
  if (BuildConfig.STORE == "bazaar") return
  val context = LocalContext.current
  AutoCheckLegacy()
  val found = UpdateManager.release ?: return
  //  §۳۸.۹: زیرِ حداقلِ پشتیبانی‌شده ⇒ پیامِ صادقانه و بی «بعداً». برنامه قفل
  //  نمی‌شود و دفتر سرِ جایش است (D-05)؛ فقط پنهان کردنِ نوار ممکن نیست.
  val required = ReleaseFeed.belowMinimum(BuildConfig.VERSION_NAME, found.minVersion)
  var hidden by remember(found.version) { mutableStateOf(false) }
  if (hidden && !required) return
  val progress = UpdateManager.progress
  val ready = UpdateManager.ready
  val busy = UpdateManager.busy
  val tint = if (required) Shop.colors.danger else Shop.colors.primary
  Row(
    Modifier
      .fillMaxWidth()
      .padding(horizontal = 14.dp, vertical = 8.dp)
      .clip(RoundedCornerShape(14.dp))
      .background(if (required) Shop.colors.dangerTint else Shop.colors.primaryTint)
      .padding(horizontal = 12.dp, vertical = 8.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Column(Modifier.weight(1f)) {
      Text(
        updateMessageLegacy(found.version, required, progress, ready != null),
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
      if (!required) TextButton(onClick = { hidden = true }) { Text("بعداً") }
      Button(
        enabled = !busy,
        onClick = { if (ready != null) UpdateManager.install(context) else UpdateManager.download(context) },
      ) { Text(if (ready != null && !Platform.DESKTOP) "نصب" else "به‌روزرسانی") }
    }
  }
}

/** بررسیِ خودکار (`UpdateManager.startAuto`) — هر جا نوار یا تراشه دیده شود همین یک بار */
@Composable
private fun AutoCheckLegacy() {
  val context = LocalContext.current
  LaunchedEffect(Unit) {
    val prefs = context.getSharedPreferences("tohid", android.content.Context.MODE_PRIVATE)
    val repo = prefs.getString("update_repo", "vil3ntec-it/shop") ?: "vil3ntec-it/shop"
    UpdateManager.startAuto(context, repo, BuildConfig.VERSION_NAME)
  }
}

/** متنِ وضعیتِ به‌روزرسانی — همان برای نوارِ گوشی و تراشهٔ کامپیوتر */
private fun updateMessageLegacy(version: String, required: Boolean, progress: Int, ready: Boolean): String = when {
  progress >= 0 -> "در حالِ گرفتنِ نسخهٔ $version… ${progress}٪" + (UpdateManager.note?.let { " · $it" } ?: "")
  ready && Platform.DESKTOP -> "نسخهٔ $version آماده است. «به‌روزرسانی» برنامه را چند ثانیه می‌بندد و دوباره باز می‌کند؛ دفتر سرِ جایش می‌ماند."
  ready -> "نسخهٔ $version گرفته شد — آمادهٔ نصب."
  required -> "این نسخه (${BuildConfig.VERSION_NAME}) دیگر پشتیبانی نمی‌شود. لطفاً به $version به‌روز کنید؛ دفترتان سرِ جایش می‌ماند."
  else -> "نسخهٔ تازهٔ فروشگاه من آمد: $version"
}

/**
 *  نسخهٔ تازه در سربرگِ کامپیوتر — تراشهٔ کوچک، نه نوارِ تمام‌عرض (نسخهٔ ۲،
 *  نقدِ ۱۴۰۵/۰۷/۲۷). «متن + رنگ + نقطه»: آبی = تازه/در حالِ گرفتن، سبز = آماده،
 *  سرخ = زیرِ حداقلِ پشتیبانی‌شده (§۳۸.۹؛ آن‌وقت «بعداً» ندارد و پنهان نمی‌شود).
 *  کلیک ⇒ همان متن، نوارِ پیشرفت، خطا و دکمه‌های «بعداً» و «به‌روزرسانی» در یک منو.
 */
@Composable
fun UpdateChipLegacy() {
  if (BuildConfig.STORE == "bazaar") return
  val context = LocalContext.current
  AutoCheckLegacy()
  val found = UpdateManager.release ?: return
  val required = ReleaseFeed.belowMinimum(BuildConfig.VERSION_NAME, found.minVersion)
  var hidden by remember(found.version) { mutableStateOf(false) }
  if (hidden && !required) return
  var open by remember { mutableStateOf(false) }
  val progress = UpdateManager.progress
  val ready = UpdateManager.ready
  val busy = UpdateManager.busy
  val (tone, label) = when {
    required -> PillTone.DANGER to "به‌روزرسانیِ لازم"
    progress >= 0 -> PillTone.INFO to "در حالِ گرفتن ${progress}٪"
    ready != null -> PillTone.SUCCESS to "نسخهٔ ${found.version} آماده"
    else -> PillTone.INFO to "نسخهٔ تازه ${found.version}"
  }
  val (fg, bg) = pillColors(Shop.colors, tone)
  Box {
    Row(
      Modifier
        .clip(Shape.chip)
        .background(bg)
        .clickable(onClickLabel = "جزئیاتِ به‌روزرسانی") { open = true }
        .padding(horizontal = 12.dp, vertical = 6.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Box(Modifier.size(7.dp).clip(Shape.chip).background(fg))
      Spacer(Modifier.width(7.dp))
      Text(label, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, color = fg, maxLines = 1)
    }
    DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
      Column(Modifier.widthIn(min = 280.dp, max = 380.dp).padding(horizontal = 16.dp, vertical = 10.dp)) {
        Text(
          updateMessageLegacy(found.version, required, progress, ready != null),
          style = MaterialTheme.typography.bodyMedium,
          color = if (required) Shop.colors.danger else Shop.colors.text,
        )
        if (progress >= 0) {
          LinearProgressIndicator(progress = { progress / 100f }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
        }
        UpdateManager.message?.takeIf { progress < 0 && ready == null && !busy }?.let {
          Spacer(Modifier.height(6.dp))
          Text(it, style = MaterialTheme.typography.labelSmall, color = Shop.colors.danger)
        }
        if (progress < 0) {
          Spacer(Modifier.height(12.dp))
          Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
            if (!required) TextButton(onClick = { hidden = true; open = false }) { Text("بعداً") }
            Spacer(Modifier.width(6.dp))
            Button(
              enabled = !busy,
              onClick = { open = false; if (ready != null) UpdateManager.install(context) else UpdateManager.download(context) },
            ) { Text(if (ready != null && !Platform.DESKTOP) "نصب" else "به‌روزرسانی") }
          }
        }
      }
    }
  }
}
