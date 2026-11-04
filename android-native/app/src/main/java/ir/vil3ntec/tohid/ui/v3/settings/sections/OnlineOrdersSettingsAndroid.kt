package ir.vil3ntec.tohid.ui.v3.settings.sections

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import ir.vil3ntec.tohid.core.i18n.StrOrderFlow
import ir.vil3ntec.tohid.core.i18n.StrPush
import ir.vil3ntec.tohid.core.i18n.StrSettings
import ir.vil3ntec.tohid.core.net.ApiClient
import ir.vil3ntec.tohid.core.push.PushPolicy
import ir.vil3ntec.tohid.data.OrderNotify
import ir.vil3ntec.tohid.data.push.DevicePush
import ir.vil3ntec.tohid.data.repo.StorefrontConfigDto
import ir.vil3ntec.tohid.data.repo.StorefrontRepository
import ir.vil3ntec.tohid.ui.OrdersLive
import ir.vil3ntec.tohid.ui.screens.SettingsLayout
import ir.vil3ntec.tohid.ui.v3.V3ListRow
import ir.vil3ntec.tohid.ui.v3.V3Section
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
 *  پروندهٔ سکو (`*Android.kt`): `Context` برای بارِ «روشن است؟» (`OrdersLive.refreshConfig`)، اجازهٔ اعلانِ گوشی
 *  (اندروید ۱۳ به بعد: پرسش، و پس از «نه» ِ همیشگی تنظیماتِ اعلانِ همین برنامه) و حالِ پوشِ گوشی (`DevicePush`، O-01).
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
  var notificationsOff by remember { mutableStateOf(!OrderNotify.allowed(context)) }
  //  پوشِ گوشی: با هر باز شدنِ همین صفحه تازه (ورود، پرچم، کلیدِ سرور)؛ بازگشت از تنظیماتِ گوشی ⇒ اجازه دوباره
  val push by DevicePush.status.collectAsState()
  LaunchedEffect(api) { if (api != null) DevicePush.sync(context, force = true) }
  LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { notificationsOff = !OrderNotify.allowed(context) }
  val askNotify = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
    notificationsOff = !granted
    if (granted) DevicePush.syncSoon(context, force = true) else openNotificationSettings(context)
  }
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
      val pushLine = pushLine(push.state)
      if (push.state != PushPolicy.State.ACTIVE) SettingsNote(StrOrderFlow.foregroundOnly)
      if (pushLine != null) SettingsNote(pushLine)
      if (notificationsOff) SettingsNote(StrOrderFlow.notificationsBlocked)
    }
    if (notificationsOff) {
      item(key = "allow") {
        V3Section(null) {
          V3ListRow(
            StrPush.allowNotifications,
            subtitle = StrPush.allowNotificationsHelp,
            icon = Icons.Outlined.NotificationsActive,
            tile = false,
            onClick = {
              if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                runCatching { askNotify.launch(android.Manifest.permission.POST_NOTIFICATIONS) }
                  .onFailure { openNotificationSettings(context) }
              } else {
                openNotificationSettings(context)
              }
            },
          )
        }
      }
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

/** حالِ پوشِ گوشی ⇒ یک خط؛ null ⇒ فقط همان «برنامهٔ بسته زنگ نمی‌زند» (کامپیوتر، بیرون از حساب، هنوز نامعلوم) */
private fun pushLine(state: PushPolicy.State): String? = when (state) {
  PushPolicy.State.ACTIVE -> StrPush.active
  PushPolicy.State.NOT_BUNDLED -> StrPush.notBundled
  PushPolicy.State.FLAG_OFF -> StrPush.flagOff
  PushPolicy.State.SERVER_OFF -> StrPush.serverOff
  PushPolicy.State.NO_TOKEN -> StrPush.noToken
  PushPolicy.State.UNSUPPORTED, PushPolicy.State.SIGNED_OUT, PushPolicy.State.UNKNOWN -> null
}

/** تنظیماتِ اعلانِ همین برنامه در گوشی — وقتی اندروید دیگر نمی‌پرسد (پس از دو «نه») یا نسخهٔ پیش از ۱۳ */
private fun openNotificationSettings(context: android.content.Context) {
  runCatching {
    context.startActivity(
      Intent("android.settings.APP_NOTIFICATION_SETTINGS")
        .putExtra("android.provider.extra.APP_PACKAGE", context.packageName)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
    )
  }
}
