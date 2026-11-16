package ir.vil3ntec.tohid.ui.v3.settings.sections

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import ir.vil3ntec.tohid.core.i18n.StrPurchaseFx
import ir.vil3ntec.tohid.core.model.ShopDeviceDto
import ir.vil3ntec.tohid.core.staff.DeviceBuilds
import ir.vil3ntec.tohid.data.PurchaseFxPref
import ir.vil3ntec.tohid.data.ShopRole
import ir.vil3ntec.tohid.data.repo.Backend
import ir.vil3ntec.tohid.ui.screens.SettingsLayout
import ir.vil3ntec.tohid.ui.v3.account.team.DeviceVersionModel
import ir.vil3ntec.tohid.ui.v3.nav.RouteScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/** بیش از این منتظرِ فهرستِ دستگاه‌ها نمی‌ماند ⇒ همان «فهرست نرسید» (هشدار و تأیید) */
private const val DEVICES_WAIT_MS = 8_000L

/**
 *  `settings/goods/purchase-fx`: کلیدِ دکانِ «خرید به ارزِ دیگر» (PUR-11، `PurchaseFxPref`، پیش‌فرض خاموش) با هشدارِ
 *  «پیش از روشن کردن، همهٔ دستگاه‌ها را به‌روز کنید». بخشِ تنظیمات صاحب و مدیر (`Need.MANAGE`)؛ نشانیِ مستقیمِ نقشِ دیگر صفحه
 *  را می‌بیند ولی چیزی نمی‌نویسد.
 *  روشن کردن ⇐ فهرستِ دستگاه‌های دکان (`GET /api/shop/devices` ⇒ `appVersion`، `DeviceBuilds.gate`): همه به‌روز ⇒ همان دم؛
 *  دستگاهِ کهنه، نامعلوم یا سنجیده‌نشده ⇒ فهرستشان و «با این حال روشن کن»؛ فهرست نرسید (بی اینترنت، بی اجازه، سرورِ کهنه) ⇒
 *  همان هشدار و همان تأیید (`PurchaseFxDevicesSheet`). خاموش کردن همیشه همان دم.
 */
@Composable
internal fun RouteScope.PurchaseFxScreen(onBack: (() -> Unit)?) {
  val context = LocalContext.current
  val manager = !ShopRole.isStaff(context)
  val scope = rememberCoroutineScope()
  val self = DeviceVersionModel.LocalSelf.current
  var checking by remember { mutableStateOf(false) }
  //  برگهٔ پرسش: null = بسته؛ `FxAsk.rows` = null ⇒ «فهرست نرسید»
  var ask by remember { mutableStateOf<FxAsk?>(null) }
  //  «خاموش» در میانِ سنجش ⇒ پاسخِ دیرِ همان سنجش کلید را روشن نمی‌کند
  var attempt by remember { mutableStateOf(0) }
  ToggleScreen(
    SettingsLayout.PURCHASE_FX, onBack, PurchaseFxPref.on(context), StrPurchaseFx.settingOn, StrPurchaseFx.updateFirst,
    more = listOf(StrPurchaseFx.settingNote, StrPurchaseFx.managersOnly),
    askOn = if (!manager) null else { turnOn ->
      if (!checking) {
        checking = true
        val mine = ++attempt
        scope.launch {
          val list: List<ShopDeviceDto>? =
            withTimeoutOrNull(DEVICES_WAIT_MS) { Backend.shop(context).devicePage().valueOrNull()?.devices }
          checking = false
          if (mine != attempt) return@launch
          when (val g = DeviceVersionModel.gate(list, self)) {
            DeviceBuilds.Gate.Direct -> turnOn()
            is DeviceBuilds.Gate.Confirm -> ask = FxAsk(DeviceVersionModel.gateRows(g, list.orEmpty(), self), turnOn)
            DeviceBuilds.Gate.NoList -> ask = FxAsk(null, turnOn)
          }
        }
      }
    },
    status = if (checking) StrPurchaseFx.checking else null,
    content = {
      ask?.let { a -> PurchaseFxDevicesSheet(a.rows, onConfirm = { ask = null; a.turnOn() }, onDismiss = { ask = null }) }
    },
  ) {
    if (!it) attempt++
    if (manager) PurchaseFxPref.set(context, it)
  }
}

private class FxAsk(val rows: List<DeviceVersionModel.GateRow>?, val turnOn: () -> Unit)
