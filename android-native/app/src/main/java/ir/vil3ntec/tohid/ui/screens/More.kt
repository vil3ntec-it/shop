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
import androidx.compose.material.icons.filled.DeliveryDining
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.LocalOffer
import androidx.compose.material.icons.filled.LocalShipping
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
  //  کامپیوتر همین صفحهٔ پیشین را نگه می‌دارد.
  if (!ir.vil3ntec.tohid.ui.v3.nav.LocalDeskLayout.current) {
    PhoneMoreScreen(d, onOpen = onOpen)
    return
  }
  val context = LocalContext.current
  val prefs = remember { context.getSharedPreferences("tohid", android.content.Context.MODE_PRIVATE) }
  //  شاگرد این ردیف‌ها را اصلاً نمی‌بیند
  val canManage = remember { ir.vil3ntec.tohid.data.ShopRole.canOpenSettings(context) }
  LaunchedEffect(Unit) { ir.vil3ntec.tohid.data.ShopChatSync.refreshConfig(context) }

  // آدرس مخزنِ به‌روزرسانی ثابت است و به کاربر نشان داده نمی‌شود؛ فقط
  // برای عیب‌یابی می‌شود با همین کلید در تنظیماتِ برنامه عوضش کرد.
  val repo = remember { prefs.getString("update_repo", "vil3ntec-it/shop") ?: "vil3ntec-it/shop" }

  //  وضعیتِ به‌روزرسانی مالِ خودِ برنامه است (`UpdateManager`)، نه این صفحه — `UpdateControls`

  Column(
    Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)
  ) {
    Spacer(Modifier.height(16.dp))
    SectionTitle(StrNav.otherSections)
    Panel {
      // قرض‌داران و محصولات از اینجا رفتند به نوارِ پایین، و انبار و
      // گزارش جایشان آمدند
      //  «سفارش‌ها» فقط وقتی سرور برای این دکان روشن گفته (پیش‌فرض خاموش)
      if (ir.vil3ntec.tohid.ui.OrdersLive.enabled) MoreCard(
        title = Str.orders + ir.vil3ntec.tohid.ui.OrdersLive.newCount.let { if (it > 0) " " + StrNav.ordersNew(it) else "" },
        icon = Icons.Filled.ShoppingBasket,
        tint = Shop.colors.primary,
        subtitle = StrNav.ordersSub,
        onClick = { onOpen("orders") },
      )
      MoreCard(
        title = Str.adminCenter,
        icon = Icons.Filled.AdminPanelSettings,
        tint = Shop.colors.primary,
        subtitle = StrNav.adminSub,
        onClick = { onOpen("admin") },
      )
      //  ویترین (فاز ۴): فقط وقتی سرور روشنش کرده و کاربر صاحب/مدیر است
      if (StorefrontGate.visible) {
        MoreCard(
          title = Str.storefront,
          icon = Icons.Filled.Storefront,
          tint = Shop.colors.primary,
          subtitle = StrNav.storefrontSub,
          onClick = { onOpen("storefront") },
        )
      }
      MoreCard(
        title = Str.devices,
        icon = Icons.Filled.Devices,
        tint = Shop.colors.accent,
        subtitle = StrNav.devicesSub,
        onClick = { onOpen("devices") },
      )
      MoreCard(
        title = Str.warehouse,
        icon = Icons.Filled.Inventory2,
        tint = Shop.colors.accent,
        subtitle = StrNav.warehouseSub,
        onClick = { onOpen("warehouse") },
      )
      MoreCard(
        title = Str.expenses,
        icon = Icons.Filled.Payments,
        tint = Shop.colors.danger,
        subtitle = StrNav.expensesSub,
        onClick = { onOpen("expenses") },
      )
      MoreCard(
        title = Str.receipts,
        icon = Icons.Filled.ReceiptLong,
        tint = Shop.colors.success,
        subtitle = StrNav.receiptsSub,
        onClick = { onOpen("receipts") },
      )
      MoreCard(
        title = Str.salesHistory,
        icon = Icons.Filled.History,
        tint = Shop.colors.primary,
        subtitle = StrNav.salesHistorySub,
        onClick = { onOpen("sales") },
      )
      MoreCard(
        title = Str.purchasing,
        icon = Icons.Filled.LocalShipping,
        tint = Shop.colors.accent,
        subtitle = StrNav.purchasingSub,
        onClick = { onOpen("purchasing") },
      )
      MoreCard(
        title = Str.reportsAlt,
        icon = Icons.Filled.BarChart,
        tint = Shop.colors.success,
        subtitle = StrNav.reportsSub,
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
        title = Str.auditLog,
        icon = Icons.Filled.ManageSearch,
        tint = Shop.colors.muted,
        subtitle = StrNav.auditSub,
        onClick = { onOpen("audit") },
      )
      if (canManage) MoreCard(
        title = Str.staff,
        icon = Icons.Filled.Groups,
        tint = Shop.colors.accent,
        subtitle = StrNav.staffSub,
        onClick = { onOpen("team") },
      )
      //  تخفیف‌ها (PRM) — صاحب و مدیر؛ کلیدِ دکان پیش‌فرض خاموش است
      if (canManage) MoreCard(
        title = StrShell.promos,
        icon = Icons.Filled.LocalOffer,
        tint = Shop.colors.success,
        subtitle = StrShell.promosSub,
        onClick = { onOpen("promos") },
      )
      /*
       *  پشتیبانی — برای همه، نه فقط صاحب دکان.
       *
       *  شاگردی که گیر کرده هم باید بتواند بپرسد؛ و کسی که هنوز حساب
       *  نساخته، بیشتر از همه. سرور برای هر دو باز است.
       */
      /*
       *  «اشتراکِ من» — حالِ اشتراکِ همین حساب، از تپشِ کش‌شده.
       *
       *  با «اشتراک و قیمت‌ها»ی سربرگ یکی نیست: آن یکی پلن‌ها و
       *  قیمت‌های سرور را نشان می‌دهد، این یکی روزهای مانده، دستگاه‌ها،
       *  پرداخت‌ها و پیام‌های مدیر را.
       */
      MoreCard(
        title = Str.mySubscription,
        icon = Icons.Filled.WorkspacePremium,
        tint = Shop.colors.warning,
        subtitle = StrNav.mySubscriptionSub,
        onClick = { onOpen("myplan") },
      )
      /*
       *  گفت‌وگو با مشتری (v3 §۳۳) — ⛔ فقط وقتی سرور برای همین دکان پرچمِ
       *  `shop.chat` را روشن گفته. خاموش ⇒ نه ردیف و نه دکمه‌ای بی‌اثر.
       */
      //  میزِ تحویل (C01): فقط وقتی سرور پرچمِ `shop.delivery` را روشن گفته
      if (DeliveryGate.visible) MoreCard(
        title = StrNav.deliveryDesk,
        icon = Icons.Filled.DeliveryDining,
        tint = Shop.colors.primary,
        subtitle = StrNav.deliverySub,
        onClick = { onOpen("delivery") },
      )
      if (ir.vil3ntec.tohid.data.ShopChatSync.config.value?.enabled == true) MoreCard(
        title = Str.customerChat,
        icon = Icons.Filled.Forum,
        tint = Shop.colors.accent,
        subtitle = StrNav.customerChatSub,
        onClick = { onOpen("chat") },
      )
      MoreCard(
        title = Str.support,
        icon = Icons.AutoMirrored.Filled.Chat,
        tint = Shop.colors.primary,
        subtitle = StrNav.supportSub,
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

    //  نسخهٔ بازار خودش را به‌روز نمی‌کند — به‌روزرسانی فقط از خودِ بازار (قانونِ بازار)
    if (BuildConfig.STORE != "bazaar") {
    Spacer(Modifier.height(20.dp))
    SectionTitle(StrNav.appUpdate)
    Panel { UpdateControls(repo) }
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
    SectionTitle(StrNav.status)
    Panel {
      InfoRow(StrNav.versionLabel, BuildConfig.VERSION_NAME)
      InfoRow(StrNav.goods, d.products.size.fa())
      InfoRow(Str.invoices, d.sales.size.fa())
      InfoRow(Str.debtors, d.debtors.size.fa())
      InfoRow(Str.expenses, d.expenses.size.fa())
      InfoRow(StrNav.suppliers, d.suppliers.size.fa())
    }

    Spacer(Modifier.height(24.dp))
  }
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
