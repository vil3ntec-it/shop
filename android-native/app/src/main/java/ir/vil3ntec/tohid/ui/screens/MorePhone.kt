package ir.vil3ntec.tohid.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.BuildConfig
import ir.vil3ntec.tohid.core.i18n.Str
import ir.vil3ntec.tohid.core.i18n.StrNav
import ir.vil3ntec.tohid.core.platform.StoreChannel
import ir.vil3ntec.tohid.data.ShopData
import ir.vil3ntec.tohid.plain
import ir.vil3ntec.tohid.ui.theme.PhoneV3
import ir.vil3ntec.tohid.ui.v3.V3Divider
import ir.vil3ntec.tohid.ui.v3.V3NavRow
import ir.vil3ntec.tohid.ui.v3.V3NavRowSpec
import ir.vil3ntec.tohid.ui.v3.V3Scaffold
import ir.vil3ntec.tohid.ui.v3.V3Section
import ir.vil3ntec.tohid.ui.v3.nav.ModuleFlags
import ir.vil3ntec.tohid.ui.v3.rememberV3Stagger
import ir.vil3ntec.tohid.ui.v3.v3Stagger
import kotlinx.coroutines.flow.first

/** پیش‌نمایشِ عکس‌ها: «بیشتر» را تا ته (گروهِ «تنظیمات») ببر */
object MoreShot {
  val scrollToEnd = mutableStateOf(false)
}

/**
 *  «بیشتر»ِ گوشی (نسخهٔ ۳): همهٔ بخش‌هایی که در نوار نیستند — از روی ماژول‌ها
 *  (`MoreLayout` ⇐ `V3Modules`) — در کارت‌های گروهی با یک نوع ردیف (`V3ListRow`،
 *  کاشیِ رنگیِ VILL3N)، زیرِ عنوانِ درشتِ جمع‌شونده. هر ردیف مسیرِ **دقیقِ** صفحهٔ
 *  خودش را باز می‌کند (`onOpen`)، و ردیف‌های «تنظیمات» صفحهٔ همان گروه را
 *  (`settings/security`، …) — نه ریشهٔ تنظیمات.
 */
@Composable
fun PhoneMoreScreen(
  d: ShopData,
  /** جای نوارِ شناورِ پایین وقتی صفحه زیرِ آن کشیده می‌شود */
  bottomReserve: androidx.compose.ui.unit.Dp = 0.dp,
  onOpen: (String) -> Unit,
) {
  val context = LocalContext.current
  //  شاگرد سابقه، کارمندان و تنظیمات را نمی‌بیند (همان سدِ `canOpenSettings`)
  val canManage = remember { ir.vil3ntec.tohid.data.ShopRole.canOpenSettings(context) }
  LaunchedEffect(Unit) {
    ir.vil3ntec.tohid.data.ShopChatSync.refreshConfig(context)
    ModuleFlags.load(context)
  }
  val groups = MoreLayout.groups(
    canManage = canManage,
    ordersOn = ir.vil3ntec.tohid.ui.OrdersLive.enabled,
    newOrders = ir.vil3ntec.tohid.ui.OrdersLive.newCount,
    chatOn = ir.vil3ntec.tohid.data.ShopChatSync.config.value?.enabled == true,
    version = ir.vil3ntec.tohid.ui.previewVersion(BuildConfig.VERSION_NAME),
    storefrontOn = StorefrontGate.visible,
    deliveryOn = DeliveryGate.visible,
    flags = ModuleFlags.on.value,
  )
  val scroll = rememberScrollState()
  val stagger = rememberV3Stagger()

  val toEnd = MoreShot.scrollToEnd.value
  LaunchedEffect(toEnd) {
    if (toEnd) {
      snapshotFlow { scroll.maxValue }.first { it in 1 until Int.MAX_VALUE }
      scroll.scrollTo(scroll.maxValue)
    }
  }

  V3Scaffold(title = Str.more, bottomReserve = bottomReserve) { pad ->
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
      Column(
        Modifier
          .widthIn(max = 560.dp)
          .fillMaxWidth()
          .verticalScroll(scroll)
          .padding(start = V3NavRowSpec.GUTTER, end = V3NavRowSpec.GUTTER, bottom = pad.calculateBottomPadding()),
      ) {
        var index = 0
        groups.forEach { g ->
          V3Section(g.title, modifier = Modifier.v3Stagger(stagger, g.title, index++)) {
            g.items.forEachIndexed { i, item ->
              if (i > 0) V3Divider(start = V3NavRowSpec.DIVIDER_START)
              MoreRow(item) { onOpen(item.route) }
            }
          }
        }
      }
    }
  }
}

/** یک ردیفِ «بیشتر» — همان `V3NavRow` که تنظیمات، حساب و مرکزِ مدیریت هم می‌کشند (۶۵r، `V3NavRowGuardTest`) */
@Composable
internal fun MoreRow(item: MoreLayout.Item, onClick: () -> Unit) {
  V3NavRow(title = item.label, icon = item.icon, hue = item.hue, value = item.value, onClick = onClick)
}

/**
 *  «نسخه و به‌روزرسانی» (`app/updates`): کنترل‌های به‌روزرسانی (`UpdateControls`) و
 *  شمارِ دفتر — برای همه، حتی کارمندی که تنظیمات را باز نمی‌کند.
 */
@Composable
internal fun MoreAppPage(d: ShopData, onBack: () -> Unit) {
  val c = PhoneV3.colors
  V3Scaffold(title = StrNav.appUpdate, onBack = onBack) { pad ->
    Column(
      Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(start = V3NavRowSpec.GUTTER, end = V3NavRowSpec.GUTTER, bottom = pad.calculateBottomPadding()),
    ) {
      //  نسخهٔ بازار خودش را به‌روز نمی‌کند — به‌روزرسانی فقط از خودِ بازار (قانونِ بازار)
      if (!StoreChannel.storeManaged(BuildConfig.STORE)) {
        V3Section(StrNav.appUpdate, padding = PaddingValues(16.dp)) {
          //  دکمه‌های `UpdateControls` با رنگِ برندِ کیت
          MaterialTheme(colorScheme = MaterialTheme.colorScheme.copy(primary = c.accentDeep, onPrimary = c.onAccent)) {
            UpdateControls(rememberUpdateRepo())
          }
        }
      }
      V3Section(StrNav.status) {
        listOf(
          StrNav.versionLabel to ir.vil3ntec.tohid.ui.previewVersion(BuildConfig.VERSION_NAME),
          StrNav.goods to plain(d.products.size),
          Str.invoices to plain(d.sales.size),
          Str.debtors to plain(d.debtors.size),
          Str.expenses to plain(d.expenses.size),
          StrNav.suppliers to plain(d.suppliers.size),
        ).forEachIndexed { i, (label, value) ->
          if (i > 0) V3Divider(start = V3NavRowSpec.PAD_START_PLAIN)
          V3NavRow(label, icon = null, hue = null, value = value, chevron = false)
        }
      }
    }
  }
}
