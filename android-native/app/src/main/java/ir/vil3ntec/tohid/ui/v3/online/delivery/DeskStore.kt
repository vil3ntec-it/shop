package ir.vil3ntec.tohid.ui.v3.online.delivery

import android.content.Context
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import ir.vil3ntec.tohid.ui.OrdersLive
import ir.vil3ntec.tohid.ui.screens.DeliveryGate

/**
 *  حالِ مشترکِ میزِ تحویل و برگه‌هایش (`delivery`، `delivery/assign/{orderId}`، …): آخرین داده، خطا و
 *  دو چیزِ یک‌باره — کدِ دریافت/تحویل که فقط همین یک بار دیده می‌شود (`delivery/code`، هرگز در پیوند
 *  یا پشته) و سفارش‌های «سپردنِ خودکار». بار همان `loadDesk` ِ میزِ پیشین است.
 */
internal object DeskStore {
  val data = mutableStateOf<DeskData?>(null)
  val error = mutableStateOf<String?>(null)
  val loading = mutableStateOf(false)
  /** یکی بالا ⇐ میز دوباره می‌خواند (پس از هر کار) */
  val reload = mutableIntStateOf(0)
  /** شمارهٔ سفارش و کدی که یک بار نشان داده می‌شود */
  val code = mutableStateOf<Pair<Long, String>?>(null)
  /** سفارش‌های برگهٔ «سپردنِ خودکار»؛ null ⇐ همهٔ آماده‌ها */
  val autoIds = mutableStateOf<List<String>?>(null)

  fun refresh() { reload.intValue++ }

  /** @return false اگر سرور گفت تحویل برای این دکان خاموش است */
  suspend fun load(context: Context): Boolean {
    val cfg = DeliveryGate.config.value ?: return false
    loading.value = true
    var on = true
    loadDesk(DeliveryGate.repository(context), OrdersLive.repository(context), withCompanies = cfg.perms.assign)
      .onSuccess { data.value = it; error.value = null }
      .onFailure { f ->
        if (f.code == "delivery_disabled") { DeliveryGate.refresh(context); on = false }
        error.value = f.userMessage
      }
    loading.value = false
    return on
  }

  fun showCode(orderNo: Long, code: String) { this.code.value = orderNo to code }
}
