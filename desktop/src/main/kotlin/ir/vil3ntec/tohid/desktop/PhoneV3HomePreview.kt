package ir.vil3ntec.tohid.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.data.ShopStore
import ir.vil3ntec.tohid.ui.components.common.NavCatalog
import ir.vil3ntec.tohid.ui.components.common.NavLayout
import ir.vil3ntec.tohid.ui.components.common.NavLayouts
import ir.vil3ntec.tohid.ui.components.common.PhoneNav
import ir.vil3ntec.tohid.ui.components.common.PhoneNavBar
import ir.vil3ntec.tohid.ui.screens.NavEditorScreen
import ir.vil3ntec.tohid.ui.screens.PhoneHomeScreen
import ir.vil3ntec.tohid.ui.screens.PhoneHomeShot
import ir.vil3ntec.tohid.ui.screens.PhoneSearchScreen
import ir.vil3ntec.tohid.ui.theme.PhoneV3

/**
 *  عکس‌های phone-v3-home (`phone:v3home…` در `desktop-shots.yml`) — خانهٔ r7، کارتِ
 *  کششیِ r6، آمارِ r8، نوارِ r10 با هر موردِ فعال، قرصِ تمدید، ویرایشگرِ نوار، نوارِ
 *  سفارشی و جست‌وجو. همان کامپوننت‌های گوشی با دفترِ نمایشی؛ فقط حالت‌ها ثابت‌اند.
 *
 *  نام‌ها: `v3home`، `v3home-pull`، `v3home-stats`، `v3home-big`، `v3home-big-pull`،
 *  `v3home-renew`، `v3home-navs`، `v3home-editor`، `v3home-custom`، `v3home-search`.
 */
@Composable
fun PhoneV3HomePreview(screen: String, store: ShopStore) {
  val data by store.data.collectAsState()
  val allowed = NavCatalog.allowed(canManage = true, ordersLive = false)
  val custom = NavLayout(NavCatalog.SCAN, listOf(NavCatalog.HOME, NavCatalog.DEBTORS, NavCatalog.REPORTS, NavCatalog.SALE, NavCatalog.MORE), NavCatalog.ACCOUNT)
  val shot = when (screen) {
    "v3home-pull" -> PhoneHomeShot(pull = true)
    "v3home-stats" -> PhoneHomeShot(page = 1)
    "v3home-big" -> PhoneHomeShot(big = true)
    "v3home-big-pull" -> PhoneHomeShot(big = true, pull = true)
    "v3home-big-stats" -> PhoneHomeShot(big = true, page = 1)
    else -> PhoneHomeShot.NONE
  }
  //  هر نام حالتِ تازهٔ خودش را می‌گیرد (ورقهٔ پیجر، کارتِ کششی)
  key(screen) { when (screen) {
    //  هر موردِ نوار یک بار فعال، و قرصِ «تمدیدِ اشتراک» — روی هم
    "v3home-navs" -> Column(
      Modifier.fillMaxSize().background(PhoneV3.colors.bg).padding(top = 8.dp),
      verticalArrangement = Arrangement.SpaceEvenly,
    ) {
      listOf(
        "search" to "جست‌وجو", "dashboard" to "خانه", "sale" to "فروش",
        "products" to "کالاها", "more" to "بیشتر", "profile" to "حساب",
      ).forEach { (route, label) ->
        Text("فعال: $label", color = PhoneV3.colors.inkMuted, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(start = 16.dp))
        PhoneNavBar(NavLayouts.DEFAULT, route, onPick = {}, onEdit = null, systemInsets = false, scrim = false)
      }
      Text("سفارشی (اسکن، قرض‌داران، گزارش‌ها) + تمدیدِ اشتراک", color = PhoneV3.colors.inkMuted, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(start = 16.dp))
      PhoneNavBar(custom, "reports", onPick = {}, onEdit = null, renewDays = 1, systemInsets = false, scrim = false)
    }
    "v3home-editor" -> NavEditorScreen(allowed) {}
    "v3home-search" -> Box(Modifier.fillMaxSize()) {
      PhoneSearchScreen(data, onOpen = {}, onProduct = {}, onBack = {}, initialQuery = "ر")
      PhoneNavBar(NavLayouts.DEFAULT, PhoneNav.SEARCH, onPick = {}, onEdit = null, modifier = Modifier.align(Alignment.BottomCenter))
    }
    else -> Box(Modifier.fillMaxSize()) {
      val layout = if (screen == "v3home-custom") custom else NavLayouts.DEFAULT
      val renew = if (screen == "v3home-renew") 1 else null
      val reserve = PhoneNav.RESERVE + if (renew != null) PhoneNav.RENEW_RESERVE else 0.dp
      PhoneHomeScreen(data, onOpen = {}, bottomReserve = reserve, shot = shot)
      PhoneNavBar(layout, "dashboard", onPick = {}, onEdit = null, modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth(), renewDays = renew)
    }
  } }
}
