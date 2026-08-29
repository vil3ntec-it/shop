package ir.vil3ntec.tohid.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.data.BackupClock
import ir.vil3ntec.tohid.data.ShopData
import ir.vil3ntec.tohid.data.ShopStore
import ir.vil3ntec.tohid.fa
import ir.vil3ntec.tohid.money
import ir.vil3ntec.tohid.qty
import ir.vil3ntec.tohid.ui.theme.Shop
import ir.vil3ntec.tohid.ui.theme.ThemeChoice

/**
 *  سربرگِ همیشگی — همان نواری که در نسخهٔ وب بالای هر صفحه است.
 *
 *  تا حالا این دکمه‌ها فقط روی داشبورد بودند و در بقیهٔ صفحه‌ها ناپدید
 *  می‌شدند؛ کاربری که وسط فروش می‌خواست تم را عوض کند یا هشدارها را ببیند،
 *  باید اول به داشبورد برمی‌گشت. حالا مثل وب، همه‌جا هست: نام صفحه در یک
 *  طرف، و اشتراک، زنگ، روشن/تاریک، حساب، تنظیمات و نشانِ کاربر در طرف دیگر.
 *
 *  روی صفحه‌های باریک، ردیفِ دکمه‌ها افقی اسکرول می‌شود تا هیچ‌کدام بریده
 *  نشوند.
 */

/** یک هشدارِ زنگ */
data class Alert(
  val value: String,
  val title: String,
  val detail: String,
  val tint: Color,
  /** صفحه‌ای که این هشدار به آن مربوط است — با زدن روی هشدار همان‌جا باز می‌شود */
  val target: String,
)

/** همان هشدارهایی که نسخهٔ وب در زنگ نشان می‌دهد */
@Composable
fun rememberAlerts(d: ShopData): List<Alert> {
  val context = LocalContext.current
  val backupStale = remember { BackupClock.isStale(context) }
  // رنگ‌ها از CompositionLocal می‌آیند و خواندنشان فقط داخل بدنهٔ کامپوزبل
  // مجاز است، نه داخل لامبدای remember — پس همین‌جا گرفته می‌شوند.
  val danger = Shop.colors.danger
  val warning = Shop.colors.warning
  return remember(d, backupStale, danger, warning) {
    buildList {
      d.products.filter { ShopStore.stockStatus(d, it) == "out" }.forEach {
        add(Alert("تمام شد", it.name, "کالا موجود نیست", danger, "products"))
      }
      d.products.filter { ShopStore.stockStatus(d, it) == "low" }.forEach {
        add(Alert("موجودی کم", it.name, "${qty(ShopStore.stock(d, it.id))} مانده", warning, "products"))
      }
      val supplierDebt = d.suppliers.sumOf { ShopStore.supplierDebt(d, it.id) }
      if (supplierDebt > 0) {
        add(Alert("بدهی به تأمین‌کننده", "${money(supplierDebt)} افغانی", "پرداخت‌نشده", warning, "purchasing"))
      }
      if (backupStale) {
        add(Alert("پشتیبان", "از اطلاعات دکان پشتیبان بگیرید", BackupClock.text(context), warning, "settings"))
      }
      d.debtors
        .map { it to ShopStore.debt(d, it.id) }
        .filter { it.second > 0 }
        .sortedByDescending { it.second }
        .take(3)
        .forEach { (debtor, amount) ->
          add(Alert("قرض‌دار", debtor.name, "${money(amount)} افغانی", danger, "debtors"))
        }
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TohidTopBar(
  title: String,
  d: ShopData,
  theme: ThemeChoice,
  onTheme: (ThemeChoice) -> Unit,
  onSettings: () -> Unit,
  onOpen: (String) -> Unit,
) {
  val context = LocalContext.current
  val alerts = rememberAlerts(d)
  var alertsOpen by remember { mutableStateOf(false) }

  Row(
    Modifier
      .fillMaxWidth()
      // سربرگ زیرِ نوارِ وضعیتِ گوشی می‌رفت و ساعت و باتری روی دکمه‌ها
      // می‌افتاد. این padding همان بلندیِ نوارِ وضعیت را کنار می‌گذارد،
      // هر اندازه‌ای که روی آن دستگاه باشد — بریدگیِ دوربین هم همین‌طور.
      .windowInsetsPadding(WindowInsets.statusBars)
      .padding(horizontal = 14.dp, vertical = 10.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.SpaceBetween,
  ) {
    Text(
      title,
      style = MaterialTheme.typography.titleMedium,
      color = Shop.colors.text,
      fontWeight = FontWeight.Bold,
    )

    Row(
      Modifier.horizontalScroll(rememberScrollState()),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
      /*
       *  نشانِ اشتراک و دکمهٔ حساب از سربرگ برداشته شدند.
       *
       *  در هر صفحه، دو تا از پهن‌ترین چیزهای سربرگ بودند و نامِ صفحه را
       *  به گوشه می‌راندند؛ روی گوشی جا برای هیچ‌چیزِ دیگر نمی‌ماند. هر
       *  دو سرِ جای خودشان هستند: «بیشتر ← تنظیمات».
       */
      BarButton(Icons.Filled.Notifications, "هشدارها", badge = alerts.size) { alertsOpen = true }

      BarButton(
        if (theme == ThemeChoice.DARK) Icons.Filled.LightMode else Icons.Filled.DarkMode,
        "روشن یا تاریک",
      ) {
        onTheme(if (theme == ThemeChoice.DARK) ThemeChoice.LIGHT else ThemeChoice.DARK)
      }

      BarButton(Icons.Filled.Settings, "تنظیمات", onClick = onSettings)

    }
  }

  if (alertsOpen) {
    ModalBottomSheet(onDismissRequest = { alertsOpen = false }, containerColor = Shop.colors.bg) {
      Column(Modifier.fillMaxWidth().padding(start = 18.dp, end = 18.dp, bottom = 28.dp)) {
        Text("هشدارهای فروشگاه", style = MaterialTheme.typography.titleMedium, color = Shop.colors.text)
        Spacer(Modifier.height(10.dp))
        if (alerts.isEmpty()) {
          EmptyNote("همه‌چیز مرتب است — هشداری نیست.")
        } else {
          alerts.forEach { alert ->
            // هشداری که فقط خبر می‌دهد، نصفِ کار است: با زدن روی آن،
            // همان صفحه‌ای باز می‌شود که کار را می‌شود در آن درست کرد
            Row(
              Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .clickable { alertsOpen = false; onOpen(alert.target) }
                .padding(horizontal = 6.dp, vertical = 9.dp),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.SpaceBetween,
            ) {
              Column(Modifier.weight(1f)) {
                Text(alert.title, style = MaterialTheme.typography.bodyMedium, color = Shop.colors.text)
                Text(alert.detail, style = MaterialTheme.typography.labelSmall, color = Shop.colors.muted2)
              }
              Text(
                alert.value,
                style = MaterialTheme.typography.labelLarge,
                color = alert.tint,
                fontWeight = FontWeight.Bold,
              )
            }
          }
        }
      }
    }
  }
}

/** دکمهٔ کوچکِ گردِ سربرگ، با شمارندهٔ اختیاری */
@Composable
private fun BarButton(
  icon: ImageVector,
  description: String,
  badge: Int = 0,
  onClick: () -> Unit,
) {
  Box(contentAlignment = Alignment.TopEnd) {
    Box(
      Modifier
        .size(34.dp)
        .clip(RoundedCornerShape(12.dp))
        .background(Shop.colors.surface)
        .clickable(onClick = onClick),
      contentAlignment = Alignment.Center,
    ) {
      Icon(icon, contentDescription = description, tint = Shop.colors.muted, modifier = Modifier.size(17.dp))
    }
    if (badge > 0) {
      Box(
        Modifier.size(15.dp).clip(RoundedCornerShape(8.dp)).background(Shop.colors.danger),
        contentAlignment = Alignment.Center,
      ) {
        Text(
          badge.coerceAtMost(9).fa(),
          style = MaterialTheme.typography.labelSmall,
          color = Color.White,
        )
      }
    }
  }
}
