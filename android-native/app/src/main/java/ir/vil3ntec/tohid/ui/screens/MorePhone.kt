package ir.vil3ntec.tohid.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ManageSearch
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.AdminPanelSettings
import androidx.compose.material.icons.outlined.Badge
import androidx.compose.material.icons.outlined.Devices
import androidx.compose.material.icons.outlined.Forum
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.InsertChart
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.LocalShipping
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material.icons.outlined.ShoppingBasket
import androidx.compose.material.icons.outlined.SupportAgent
import androidx.compose.material.icons.outlined.SystemUpdate
import androidx.compose.material.icons.outlined.WorkspacePremium
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.BuildConfig
import ir.vil3ntec.tohid.data.ShopData
import ir.vil3ntec.tohid.plain
import ir.vil3ntec.tohid.ui.theme.PhoneV3
import kotlinx.coroutines.flow.first

/** پیش‌نمایشِ عکس‌ها: «بیشتر» را تا ته (گروهِ «تنظیمات») ببر */
object MoreShot {
  val scrollToEnd = mutableStateOf(false)
}

/**
 *  «بیشتر»ِ گوشی (نسخهٔ ۳، بندِ F — دکمهٔ ≡ نوار): هر بخشی که در نوار نیست، در
 *  کارت‌های گروهیِ سفیدِ r5 با آیکنِ خطیِ تک‌رنگ و فلش، و «تنظیمات» آخرین گروه.
 *  گروه‌ها و دسترسی‌ها در `MoreLayout` است (خالص و آزمون‌شده).
 *
 *  «نسخه و به‌روزرسانی» زیرصفحهٔ همین‌جاست: همان بررسی/دانلود/نصبِ پیشین
 *  (`UpdateControls`) و شمارِ دفتر.
 */
@Composable
fun PhoneMoreScreen(d: ShopData, onOpen: (String) -> Unit) {
  val context = LocalContext.current
  val c = PhoneV3.colors
  //  شاگرد سابقه، کارمندان و تنظیمات را نمی‌بیند (همان سدِ `canOpenSettings`)
  val canManage = remember { ir.vil3ntec.tohid.data.ShopRole.canOpenSettings(context) }
  LaunchedEffect(Unit) { ir.vil3ntec.tohid.data.ShopChatSync.refreshConfig(context) }
  val groups = MoreLayout.groups(
    canManage = canManage,
    ordersOn = ir.vil3ntec.tohid.ui.OrdersLive.enabled,
    newOrders = ir.vil3ntec.tohid.ui.OrdersLive.newCount,
    chatOn = ir.vil3ntec.tohid.data.ShopChatSync.config.value?.enabled == true,
    version = BuildConfig.VERSION_NAME,
  )
  var page by rememberSaveable { mutableStateOf<String?>(null) }
  BackHandler(enabled = page != null) { page = null }
  val scroll = rememberScrollState()

  val toEnd = MoreShot.scrollToEnd.value
  LaunchedEffect(toEnd) {
    if (toEnd) {
      snapshotFlow { scroll.maxValue }.first { it in 1 until Int.MAX_VALUE }
      scroll.scrollTo(scroll.maxValue)
    }
  }

  Box(Modifier.fillMaxSize().background(c.bg).windowInsetsPadding(WindowInsets.statusBars)) {
    V3Pages(page, depth = { if (it == null) 0 else 1 }) { p ->
      if (p == null) {
        Column(Modifier.fillMaxSize().verticalScroll(scroll), horizontalAlignment = Alignment.CenterHorizontally) {
          Column(Modifier.widthIn(max = 560.dp).fillMaxWidth()) {
            Spacer(Modifier.height(6.dp))
            V3LargeHeader("بیشتر")
            Column(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 32.dp)) {
              groups.forEach { g ->
                V3GroupTitle(g.title, Modifier.padding(top = 18.dp))
                V3Card {
                  g.items.forEach { item ->
                    V3Row(
                      icon = moreIcon(item),
                      label = item.label,
                      value = item.value,
                      onClick = {
                        when {
                          item.route == MoreLayout.APP -> page = MoreLayout.APP
                          item.settingsGroup != null -> {
                            SettingsNav.origin = "more"
                            SettingsNav.pending.value = SettingsNav.Request(group = item.settingsGroup)
                            onOpen("settings")
                          }
                          else -> onOpen(item.route)
                        }
                      },
                    )
                  }
                }
              }
            }
          }
        }
      } else {
        MoreAppPage(d, onBack = { page = null })
      }
    }
  }
}

private fun moreIcon(item: MoreLayout.Item): ImageVector? = item.settingsGroup?.let(::settingsIcon) ?: when (item.id) {
  "orders" -> Icons.Outlined.ShoppingBasket
  "debtors" -> Icons.Outlined.Groups
  "sales" -> Icons.Outlined.History
  "receipts" -> Icons.AutoMirrored.Outlined.ReceiptLong
  "chat" -> Icons.Outlined.Forum
  "warehouse" -> Icons.Outlined.Inventory2
  "purchasing" -> Icons.Outlined.LocalShipping
  "expenses" -> Icons.Outlined.Payments
  "reports" -> Icons.Outlined.InsertChart
  "admin" -> Icons.Outlined.AdminPanelSettings
  "team" -> Icons.Outlined.Badge
  "audit" -> Icons.AutoMirrored.Outlined.ManageSearch
  "devices" -> Icons.Outlined.Devices
  "myplan" -> Icons.Outlined.WorkspacePremium
  "support" -> Icons.Outlined.SupportAgent
  MoreLayout.APP -> Icons.Outlined.SystemUpdate
  else -> null
}

/** زیرصفحهٔ «نسخه و به‌روزرسانی»: کنترل‌های به‌روزرسانی و شمارِ دفتر */
@Composable
private fun MoreAppPage(d: ShopData, onBack: () -> Unit) {
  val c = PhoneV3.colors
  Column(Modifier.fillMaxSize()) {
    V3Header("نسخه و به‌روزرسانی", onBack)
    Column(
      Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(start = 16.dp, end = 16.dp, bottom = 32.dp),
    ) {
      //  نسخهٔ بازار خودش را به‌روز نمی‌کند — به‌روزرسانی فقط از خودِ بازار (قانونِ بازار)
      if (BuildConfig.STORE != "bazaar") {
        V3GroupTitle("به‌روزرسانی برنامه", Modifier.padding(top = 8.dp))
        //  دکمه‌ها تک‌رنگ (سیاه)، مثلِ بقیهٔ صفحه‌های نسخهٔ ۳
        MaterialTheme(colorScheme = MaterialTheme.colorScheme.copy(primary = c.ink, onPrimary = c.card)) {
          V3Card(padding = PaddingValues(16.dp)) { UpdateControls(rememberUpdateRepo()) }
        }
      }
      V3GroupTitle("وضعیت", Modifier.padding(top = 18.dp))
      V3Card {
        V3Row(null, "نسخه", value = BuildConfig.VERSION_NAME)
        V3Row(null, "اجناس", value = plain(d.products.size))
        V3Row(null, "فاکتورها", value = plain(d.sales.size))
        V3Row(null, "قرض‌داران", value = plain(d.debtors.size))
        V3Row(null, "مصارف", value = plain(d.expenses.size))
        V3Row(null, "تأمین‌کننده‌ها", value = plain(d.suppliers.size))
      }
    }
  }
}
