package ir.vil3ntec.tohid.ui.screens

import androidx.compose.animation.core.EaseInOutSine
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
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
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.WorkspacePremium
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

/** نارنجیِ هشدار — از تم نمی‌آید چون در تمِ روشن و تاریک باید همین باشد */
private val ALERT_ORANGE = Color(0xFFF08A24)

/** قهوه‌ایِ تیرهٔ روی طلا — همان که در صفحهٔ اشتراک است */
private val GOLD_INK = Color(0xFF3A2705)

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
  onAccount: () -> Unit,
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
       *  سه کلیدِ اولِ سربرگ از هم جدا شده‌اند.
       *
       *  تا حالا هر پنج کلید یک کادرِ خاکستریِ یکسان داشتند و کاربر باید
       *  آیکنِ ریزشان را می‌خواند تا بفهمد کدام است. حالا هرکدام رنگ و
       *  شکلِ کارِ خودش را دارد: اشتراک طلایی و با نوشتهٔ VIP، حساب آبی و
       *  با تپش، و هشدارها نارنجی.
       */
      VipChip { onOpen("vip") }

      AccountChip(onClick = onAccount)

      AlertChip(count = alerts.size) { alertsOpen = true }

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

/**
 *  کلیدِ اشتراک — طلایی، با نوشتهٔ VIP.
 *
 *  از بقیه کمی بزرگ‌تر است و به‌جای آیکنِ تنها، خودِ کلمه را می‌نویسد:
 *  «اشتراک» چیزی است که کاربر باید پیدایش کند، نه چیزی که دنبالش
 *  بگردد. برقِ روی آن همان برقِ صفحهٔ اشتراک است تا یکی بودنشان
 *  فهمیده شود.
 */
@Composable
private fun VipChip(onClick: () -> Unit) {
  val motion = rememberInfiniteTransition(label = "vipChip")
  val shine by motion.animateFloat(
    initialValue = 0f,
    targetValue = 1f,
    animationSpec = infiniteRepeatable(
      tween(if (Motion.enabled) 2600 else 1, easing = EaseInOutSine),
      RepeatMode.Reverse,
    ),
    label = "shine",
  )
  Row(
    Modifier
      .height(36.dp)
      .clip(RoundedCornerShape(13.dp))
      .background(
        Brush.linearGradient(
          listOf(Color(0xFFFBE08A), Color(0xFFF6C93F), Color(0xFFFFF3C4), Color(0xFFD79A14))
        )
      )
      .drawWithContent {
        drawContent()
        if (!Motion.enabled) return@drawWithContent
        val x = size.width * shine
        drawRect(
          brush = Brush.linearGradient(
            colors = listOf(Color.Transparent, Color.White.copy(alpha = 0.5f), Color.Transparent),
            start = Offset(x - size.width * 0.35f, 0f),
            end = Offset(x + size.width * 0.35f, size.height),
          ),
          size = size,
        )
      }
      .clickable(onClick = onClick)
      .padding(horizontal = 10.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(4.dp),
  ) {
    Icon(
      Icons.Filled.WorkspacePremium,
      contentDescription = "اشتراک و قیمت‌ها",
      tint = GOLD_INK,
      modifier = Modifier.size(15.dp),
    )
    Text(
      "VIP",
      style = MaterialTheme.typography.labelLarge,
      color = GOLD_INK,
      fontWeight = FontWeight.Bold,
    )
  }
}

/** کلیدِ حساب — آبی، با هاله‌ای که نفس می‌کشد */
@Composable
private fun AccountChip(onClick: () -> Unit) {
  val colors = Shop.colors
  val motion = rememberInfiniteTransition(label = "accountChip")
  val breathe by motion.animateFloat(
    initialValue = 0.35f,
    targetValue = 0.85f,
    animationSpec = infiniteRepeatable(
      tween(if (Motion.enabled) 2000 else 1, easing = EaseInOutSine),
      RepeatMode.Reverse,
    ),
    label = "breathe",
  )
  Box(
    Modifier
      .size(34.dp)
      .clip(RoundedCornerShape(12.dp))
      .background(colors.primary.copy(alpha = 0.16f))
      .border(1.2.dp, colors.primary.copy(alpha = breathe), RoundedCornerShape(12.dp))
      .clickable(onClick = onClick),
    contentAlignment = Alignment.Center,
  ) {
    Icon(
      Icons.Filled.Person,
      contentDescription = "حساب",
      tint = colors.primary,
      modifier = Modifier.size(18.dp),
    )
  }
}

/** کلیدِ هشدارها — نارنجی، تا در ردیفِ خاکستری دیده شود */
@Composable
private fun AlertChip(count: Int, onClick: () -> Unit) {
  Box(contentAlignment = Alignment.TopEnd) {
    Box(
      Modifier
        .size(34.dp)
        .clip(RoundedCornerShape(12.dp))
        .background(ALERT_ORANGE.copy(alpha = 0.18f))
        .border(1.2.dp, ALERT_ORANGE.copy(alpha = 0.65f), RoundedCornerShape(12.dp))
        .clickable(onClick = onClick),
      contentAlignment = Alignment.Center,
    ) {
      Icon(
        Icons.Filled.Notifications,
        contentDescription = "هشدارها",
        tint = ALERT_ORANGE,
        modifier = Modifier.size(18.dp),
      )
    }
    if (count > 0) {
      Box(
        Modifier.size(15.dp).clip(RoundedCornerShape(8.dp)).background(Shop.colors.danger),
        contentAlignment = Alignment.Center,
      ) {
        Text(
          count.coerceAtMost(9).fa(),
          style = MaterialTheme.typography.labelSmall,
          color = Color.White,
        )
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
