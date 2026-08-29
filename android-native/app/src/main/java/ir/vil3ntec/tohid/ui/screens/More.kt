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
import ir.vil3ntec.tohid.data.ShopData
import ir.vil3ntec.tohid.data.ShopStore
import ir.vil3ntec.tohid.fa
import ir.vil3ntec.tohid.update.Updater
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material.icons.filled.ManageSearch
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Settings
import ir.vil3ntec.tohid.ui.theme.Shop
import kotlinx.coroutines.launch

/**
 *  بیشتر — وضعیت برنامه، به‌روزرسانی، و راهِ ورود به بخش‌هایی که در
 *  نوارِ پایین جا نمی‌شوند.
 */
@Composable
fun MoreScreen(store: ShopStore, d: ShopData, onOpen: (String) -> Unit) {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  val prefs = remember { context.getSharedPreferences("tohid", android.content.Context.MODE_PRIVATE) }

  // آدرس مخزنِ به‌روزرسانی ثابت است و به کاربر نشان داده نمی‌شود؛ فقط
  // برای عیب‌یابی می‌شود با همین کلید در تنظیماتِ برنامه عوضش کرد.
  val repo = remember { prefs.getString("update_repo", "vil3ntec-it/shop") ?: "vil3ntec-it/shop" }
  var status by remember { mutableStateOf<String?>(null) }
  var found by remember { mutableStateOf<Updater.Release?>(null) }
  var progress by remember { mutableStateOf(-1) }
  var busy by remember { mutableStateOf(false) }

  Column(
    Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)
  ) {
    Text("بیشتر", style = MaterialTheme.typography.headlineMedium, color = Shop.colors.text)
    Spacer(Modifier.height(16.dp))

    SectionTitle("وضعیت")
    Panel {
      InfoRow("نسخه", BuildConfig.VERSION_NAME)
      InfoRow("اجناس", d.products.size.fa())
      InfoRow("فاکتورها", d.sales.size.fa())
      InfoRow("قرض‌داران", d.debtors.size.fa())
      InfoRow("مصارف", d.expenses.size.fa())
      InfoRow("تأمین‌کننده‌ها", d.suppliers.size.fa())
    }

    Spacer(Modifier.height(20.dp))
    SectionTitle("بخش‌های دیگر")
    Panel {
      // قرض‌داران و محصولات از اینجا رفتند به نوارِ پایین، و انبار و
      // گزارش جایشان آمدند
      MoreCard(
        title = "انبار",
        icon = Icons.Filled.Inventory2,
        tint = Shop.colors.accent,
        subtitle = "ورود کالا، موجودی و حرکت هر جنس",
        onClick = { onOpen("warehouse") },
      )
      MoreCard(
        title = "مصارف",
        icon = Icons.Filled.Payments,
        tint = Shop.colors.danger,
        subtitle = "خرج‌های دکان به تفکیک دسته",
        onClick = { onOpen("expenses") },
      )
      MoreCard(
        title = "رسیدها",
        icon = Icons.Filled.ReceiptLong,
        tint = Shop.colors.success,
        subtitle = "دریافتی‌های هر قرض‌دار به تفکیک ماه و سال",
        onClick = { onOpen("receipts") },
      )
      MoreCard(
        title = "تاریخچه فروش",
        icon = Icons.Filled.History,
        tint = Shop.colors.primary,
        subtitle = "فاکتورهای ثبت‌شده، مرجوعی و لغو فروش",
        onClick = { onOpen("sales") },
      )
      MoreCard(
        title = "خرید و تأمین‌کننده",
        icon = Icons.Filled.LocalShipping,
        tint = Shop.colors.accent,
        subtitle = "حساب تأمین‌کننده‌ها و بدهی به آن‌ها",
        onClick = { onOpen("purchasing") },
      )
      MoreCard(
        title = "گزارشات",
        icon = Icons.Filled.BarChart,
        tint = Shop.colors.success,
        subtitle = "سود، فروش و مصارف در بازهٔ دلخواه",
        onClick = { onOpen("reports") },
      )
      MoreCard(
        title = "سابقه عملیات",
        icon = Icons.Filled.ManageSearch,
        tint = Shop.colors.muted,
        subtitle = "هر کاری که در برنامه انجام شده",
        onClick = { onOpen("audit") },
      )
      MoreCard(
        title = "تنظیمات",
        icon = Icons.Filled.Settings,
        tint = Shop.colors.primary,
        subtitle = "نام فروشگاه، ظاهر، پشتیبان‌گیری و اتصال به سرور",
        onClick = { onOpen("settings") },
      )
    }

    Spacer(Modifier.height(20.dp))
    SectionTitle("به‌روزرسانی برنامه")
    Panel {
      Text(
        "نسخهٔ تازه از همین‌جا گرفته و نصب می‌شود؛ اطلاعات دکان دست‌نخورده می‌ماند.",
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
        Text(
          "در حال دانلود… ${progress.fa()}٪",
          style = MaterialTheme.typography.labelSmall,
          color = Shop.colors.muted,
        )
        Spacer(Modifier.height(12.dp))
      }

      val release = found
      if (release == null) {
        Button(
          enabled = !busy,
          onClick = {
            busy = true; status = "در حال بررسی…"
            scope.launch {
              Updater.check(repo, BuildConfig.VERSION_NAME)
                .onSuccess {
                  found = it
                  status = if (it == null) "نسخهٔ شما تازه‌ترین است." else null
                }
                .onFailure { status = it.message ?: "بررسی ناموفق بود" }
              busy = false
            }
          },
          modifier = Modifier.fillMaxWidth(),
        ) { Text("بررسی نسخهٔ تازه") }
      } else {
        Text(
          "نسخهٔ ${release.version} آماده است",
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
          onClick = {
            busy = true; progress = 0; status = null
            scope.launch {
              Updater.download(context, release) { progress = it }
                .onSuccess { file ->
                  progress = -1
                  // بارِ اول، گوشی می‌پرسد از این منبع اجازهٔ نصب هست یا نه
                  Updater.install(context, file)
                    .onFailure { status = "نصب‌کنندهٔ اندروید باز نشد" }
                }
                .onFailure { status = it.message ?: "دانلود ناموفق بود"; progress = -1 }
              busy = false
            }
          },
          modifier = Modifier.fillMaxWidth(),
        ) { Text("دانلود و نصب") }
      }

      status?.let {
        Spacer(Modifier.height(8.dp))
        Text(it, style = MaterialTheme.typography.bodySmall, color = Shop.colors.muted)
      }
    }

    Spacer(Modifier.height(24.dp))
  }
}

@Composable
private fun MoreCard(
  title: String,
  subtitle: String,
  icon: androidx.compose.ui.graphics.vector.ImageVector,
  tint: androidx.compose.ui.graphics.Color,
  onClick: () -> Unit,
) {
  // همان شکلِ ردیف‌های تنظیمات: آیکنِ رنگی در ظرفِ گرد، عنوان، توضیح و
  // فلش. ردیفِ فقط‌متنی روی زمینهٔ خالی، فهرست را یک تودهٔ خاکستری
  // می‌کرد که چشم نمی‌توانست اسکنش کند.
  SettingsRow(
    icon = icon,
    title = title,
    description = subtitle,
    tint = tint,
    onClick = onClick,
  )
}

@Composable
private fun InfoRow(label: String, value: String) {
  Row(
    Modifier.fillMaxWidth().padding(vertical = 5.dp),
    horizontalArrangement = Arrangement.SpaceBetween,
  ) {
    Text(label, style = MaterialTheme.typography.bodySmall, color = Shop.colors.muted)
    Text(value, style = MaterialTheme.typography.bodyMedium, color = Shop.colors.text)
  }
}
