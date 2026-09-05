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
import ir.vil3ntec.tohid.update.UpdateManager
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material.icons.filled.ManageSearch
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.ReceiptLong
import ir.vil3ntec.tohid.ui.theme.Shop

/**
 *  بیشتر — وضعیت برنامه، به‌روزرسانی، و راهِ ورود به بخش‌هایی که در
 *  نوارِ پایین جا نمی‌شوند.
 */
@Composable
fun MoreScreen(store: ShopStore, d: ShopData, onOpen: (String) -> Unit) {
  val context = LocalContext.current
  val prefs = remember { context.getSharedPreferences("tohid", android.content.Context.MODE_PRIVATE) }
  //  شاگرد این ردیف‌ها را اصلاً نمی‌بیند
  val canManage = remember { ir.vil3ntec.tohid.data.ShopRole.canOpenSettings(context) }

  // آدرس مخزنِ به‌روزرسانی ثابت است و به کاربر نشان داده نمی‌شود؛ فقط
  // برای عیب‌یابی می‌شود با همین کلید در تنظیماتِ برنامه عوضش کرد.
  val repo = remember { prefs.getString("update_repo", "vil3ntec-it/shop") ?: "vil3ntec-it/shop" }

  /*
   *  وضعیتِ به‌روزرسانی مالِ این صفحه نیست، مالِ خودِ برنامه است.
   *
   *  وقتی اینجا نگه داشته می‌شد، بیرون رفتن از صفحه هم دانلود را می‌کشت و
   *  هم نوارِ پیشرفت را پاک می‌کرد. حالا با برگشتن، همان نوار سرِ جایش
   *  است و کار ادامه دارد.
   */
  val status = UpdateManager.message
  val found = UpdateManager.release
  val progress = UpdateManager.progress
  val busy = UpdateManager.busy
  val ready = UpdateManager.ready
  val needsPermission = UpdateManager.needsPermission

  Column(
    Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)
  ) {
    Spacer(Modifier.height(16.dp))
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
      /*
       *  سابقهٔ عملیات هم مالِ صاحب و مدیر است، نه شاگرد.
       *
       *  سرور از اول همین را می‌گفت (`audit.view` فقط owner و manager)
       *  ولی ردیفش در برنامه برای همه بود؛ یعنی برنامه چیزی را نشان
       *  می‌داد که سرور برای شاگرد بسته بود.
       */
      if (canManage) MoreCard(
        title = "سابقه عملیات",
        icon = Icons.Filled.ManageSearch,
        tint = Shop.colors.muted,
        subtitle = "هر کاری که در برنامه انجام شده",
        onClick = { onOpen("audit") },
      )
      if (canManage) MoreCard(
        title = "کارمندان دکان",
        icon = Icons.Filled.Groups,
        tint = Shop.colors.accent,
        subtitle = "کد پیوستن بسازید و دسترسی شاگردها را ببندید",
        onClick = { onOpen("team") },
      )
      /*
       *  پشتیبانی — برای همه، نه فقط صاحب دکان.
       *
       *  شاگردی که گیر کرده هم باید بتواند بپرسد؛ و کسی که هنوز حساب
       *  نساخته، بیشتر از همه. سرور برای هر دو باز است.
       */
      MoreCard(
        title = "پشتیبانی",
        icon = Icons.AutoMirrored.Filled.Chat,
        tint = Shop.colors.primary,
        subtitle = "سؤال یا مشکلتان را بنویسید — همین‌جا جواب می‌گیرید",
        onClick = { onOpen("support") },
      )
      /*
       *  «تنظیمات» اینجا نیست — عمداً.
       *
       *  گزارشِ صاحب مخزن: «از بخش بیشتر کادر تنظیمات را بردار، آن بالا
       *  است و این‌جا هم بدهند؛ این‌جا را فقط می‌گیرد». درست است: چرخ‌دنده
       *  همیشه در سربرگِ هر صفحه هست و همان یک راه بس است. دو راه به یک
       *  صفحه، فقط فهرست را بلندتر می‌کند.
       *
       *  «کارمندان دکان» بالاتر همچنان فقط برای صاحب و مدیر است — شاگرد
       *  نه ردیفش را می‌بیند و نه سرور برایش بازش می‌کند.
       */
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
        Row(
          Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
        ) {
          Text(
            "در حال دانلود… ${progress.fa()}٪",
            style = MaterialTheme.typography.labelSmall,
            color = Shop.colors.muted,
          )
          Text(
            "توقف",
            style = MaterialTheme.typography.labelSmall,
            color = Shop.colors.danger,
            modifier = Modifier.clickable { UpdateManager.cancel() },
          )
        }
        Spacer(Modifier.height(4.dp))
        Text(
          "می‌توانید به بخش‌های دیگر بروید؛ دانلود قطع نمی‌شود.",
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
          // فایلی که از قبل کامل گرفته شده دوباره گرفته نمی‌شود؛ کسی که
          // سرِ پرسشِ نصب «نه» زده، بارِ بعد یک‌راست به نصب می‌رسد
          onClick = { UpdateManager.download(context) },
          modifier = Modifier.fillMaxWidth(),
        ) { Text(if (ready != null) "نصب نسخهٔ ${release.version}" else "دانلود و نصب") }

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
          ) { Text("باز کردنِ صفحهٔ اجازهٔ نصب") }
        }

        // راهِ آخر، وقتی دانلودِ داخلی نمی‌گیرد
        Spacer(Modifier.height(4.dp))
        TextButton(
          onClick = { UpdateManager.openInBrowser(context, repo) },
          modifier = Modifier.fillMaxWidth(),
        ) {
          Text(
            "گرفتن با مرورگر",
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

    /*
     *  «وضعیت» ته صفحه است، نه سرِ آن.
     *
     *  شمارشِ اجناس و فاکتورها خواندنی است ولی کاری با آن نمی‌شود کرد؛
     *  چیزی که کاربر برای آن این صفحه را باز می‌کند، خودِ بخش‌هاست.
     *  وقتی این پنل بالا بود، فهرستِ بخش‌ها زیرِ خطِ دید می‌افتاد و برای
     *  رسیدن به «انبار» باید از روی شش عدد رد می‌شد.
     */
    Spacer(Modifier.height(20.dp))
    SectionTitle("وضعیت")
    Panel {
      InfoRow("نسخه", BuildConfig.VERSION_NAME)
      InfoRow("اجناس", d.products.size.fa())
      InfoRow("فاکتورها", d.sales.size.fa())
      InfoRow("قرض‌داران", d.debtors.size.fa())
      InfoRow("مصارف", d.expenses.size.fa())
      InfoRow("تأمین‌کننده‌ها", d.suppliers.size.fa())
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
