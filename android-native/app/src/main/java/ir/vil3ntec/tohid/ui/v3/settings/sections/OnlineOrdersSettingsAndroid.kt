package ir.vil3ntec.tohid.ui.v3.settings.sections

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import ir.vil3ntec.tohid.core.i18n.StrOrderFlow
import ir.vil3ntec.tohid.core.i18n.StrSettings
import ir.vil3ntec.tohid.core.net.ApiClient
import ir.vil3ntec.tohid.data.OrderNotify
import ir.vil3ntec.tohid.data.repo.StorefrontConfigDto
import ir.vil3ntec.tohid.data.repo.StorefrontRepository
import ir.vil3ntec.tohid.ui.OrdersLive
import ir.vil3ntec.tohid.ui.screens.SettingsLayout
import ir.vil3ntec.tohid.ui.v3.V3ToggleRow
import ir.vil3ntec.tohid.ui.v3.nav.RouteScope
import ir.vil3ntec.tohid.ui.v3.online.orders.OrderArrivalCardV3
import ir.vil3ntec.tohid.ui.v3.online.orders.openFix
import ir.vil3ntec.tohid.ui.v3.settings.SettingsBlock
import ir.vil3ntec.tohid.ui.v3.settings.SettingsNote
import ir.vil3ntec.tohid.ui.v3.settings.SettingsPageFrame
import kotlinx.coroutines.launch
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/*
 *  پروندهٔ سکو (`*Android.kt`): `Context` برای بارِ «روشن است؟» (`OrdersLive.refreshConfig`) و اجازهٔ اعلانِ گوشی.
 */

/**
 *  ردیفِ «سفارشِ آنلاین» — هر حسابِ واردشده‌ای که `/shop/orders/config` به آن جواب داد، **حتی وقتی بخشِ «سفارش‌ها»
 *  خاموش است** (همان‌جا که پیش‌تر خاموشی بی‌صدا بود). نقشِ بی `orders.view` (۴۰۳) ⇒ ردیفی نیست.
 */
@Composable
internal fun onlineOrdersRow(api: ApiClient?): Boolean {
  val context = LocalContext.current
  LaunchedEffect(api) { if (api != null && OrdersLive.arrival.value == null) OrdersLive.refreshConfig(context) }
  return api != null && OrdersLive.arrival.value != null
}

/**
 *  `settings/sales/online-orders`: «چرا سفارشِ آنلاین نمی‌رسد؟» (هر دروازه با «چه باید کرد» و دکمهٔ همان کار — هیچ
 *  دروازه‌ای این‌جا باز نمی‌شود)، حالِ همین دستگاه (برنامهٔ بسته زنگ نمی‌زند؛ اجازهٔ اعلان) و تنظیمِ دکانِ «پرداخت پس
 *  از پذیرش» (`PUT /shop/storefront` ِ `payAfterAccept`، فقط صاحب/مدیر و پس از ساختنِ ویترین).
 */
@Composable
internal fun RouteScope.OnlineOrdersScreen(onBack: (() -> Unit)?) {
  val context = LocalContext.current
  val api = rememberSettingsApi(ownerOnly = false)
  val scope = rememberCoroutineScope()
  var sf by remember { mutableStateOf<StorefrontConfigDto?>(null) }
  var busy by remember { mutableStateOf(false) }
  LaunchedEffect(api) {
    if (api != null) {
      OrdersLive.refreshConfig(context)
      sf = StorefrontRepository(api).config().valueOrNull()
    }
  }
  val arrival = OrdersLive.arrival.value
  val notificationsOff = remember { !OrderNotify.allowed(context) }
  SettingsPageFrame(
    title = SettingsLayout.labelOf(SettingsLayout.ONLINE_ORDERS),
    subtitle = arrival?.let { if (it.ok) StrOrderFlow.settingsLine else StrOrderFlow.blocker(it.blockers.firstOrNull()?.code.orEmpty()) },
    onBack = onBack,
  ) {
    if (api == null || arrival == null) {
      item(key = "off") { SettingsNote(StrSettings.off) }
      return@SettingsPageFrame
    }
    item(key = "arrival") { OrderArrivalCardV3(arrival, onFix = { openFix(it) }, showOk = true) }
    item(key = "device") {
      SettingsNote(StrOrderFlow.foregroundOnly)
      if (notificationsOff) SettingsNote(StrOrderFlow.notificationsBlocked)
    }
    val cfg = sf
    if (cfg != null && cfg.flag && cfg.permissions.manage) {
      item(key = "pay") {
        val s = cfg.settings
        if (s == null) {
          SettingsNote(StrOrderFlow.payAfterAcceptNoStore)
        } else {
          SettingsBlock {
            V3ToggleRow(
              StrOrderFlow.payAfterAccept, s.payAfterAccept,
              { on ->
                if (!busy) {
                  busy = true
                  scope.launch {
                    StorefrontRepository(api).save(buildJsonObject { put("payAfterAccept", on) })
                      .onSuccess { saved -> sf = cfg.copy(settings = saved); toasts.show(StrOrderFlow.saved) }
                      .onFailure { f -> toasts.show(f.userMessage) }
                    busy = false
                  }
                }
              },
              subtitle = StrOrderFlow.payAfterAcceptHelp, enabled = !busy,
            )
          }
        }
      }
    }
  }
}
