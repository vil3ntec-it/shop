package ir.vil3ntec.tohid.ui

import android.content.Context
import androidx.compose.runtime.mutableStateOf
import ir.vil3ntec.tohid.core.model.OrdersSummaryDto
import ir.vil3ntec.tohid.core.net.ApiFailure
import ir.vil3ntec.tohid.core.orders.OrderAlerts
import ir.vil3ntec.tohid.scan.ScanFeedback

/**
 *  حالِ هشدارهای درون‌برنامه‌ایِ «سفارش‌ها» برای کلِ برنامه — ORD-18 (شروعِ سفارشِ زمان‌بندی‌شده) و ORD-03 (قطعِ
 *  اتصال)، از همان پرسشِ ۲۰ثانیه‌ایِ `OrdersLive.pollSummary` (فقط وقتی برنامه جلوی چشم است).
 *
 *  منطق در `core/orders/OrderAlerts`؛ این پروندهٔ مرزی فقط ساعتِ دستگاه را برای «آخرین به‌روزرسانی» می‌خواند و
 *  صدای یک‌باره را می‌زند (همان بوقِ «هشدار» ِ اسکن، `ScanFeedback.unknown`؛ کامپیوتر: بوقِ خودش). هر سفارش یک بار
 *  روی هر دستگاه در هر اجرا؛ فهرست را فقط سرور می‌سازد (پرچمِ خاموش ⇒ تهی ⇒ هیچ صدا و نشانی).
 */
object OrderAlertsLive {

  val start = mutableStateOf<List<OrderAlerts.Start>>(emptyList())

  /** زمانِ سرورِ همان پاسخ — «n دقیقهٔ دیگر» با این، نه ساعتِ دستگاه */
  val serverNow = mutableStateOf(0L)

  /** «دیدم» ِ همین دستگاه */
  val dismissed = mutableStateOf<Set<String>>(emptySet())

  val net = mutableStateOf(OrderAlerts.Net())

  /** میزِ سفارش (`OrderDeskPanel`، پرچمِ `shop.orders.desk`) روشن است ⇒ هشدارِ قطع حتی بی `presence` */
  val deskOn = mutableStateOf(false)

  @Volatile private var announced: Set<String> = emptySet()

  fun onSummary(context: Context, s: OrdersSummaryDto) {
    net.value = OrderAlerts.ok(System.currentTimeMillis())
    val list = s.startAlerts.map { OrderAlerts.Start(it.id, it.orderNo, it.status, it.scheduledFor, it.alertAt) }
    val fresh = OrderAlerts.fresh(list, announced)
    announced = OrderAlerts.keep(announced, list, fresh.map { it.id })
    dismissed.value = OrderAlerts.keep(dismissed.value, list)
    serverNow.value = s.serverTime
    start.value = list
    if (fresh.isNotEmpty()) runCatching { ScanFeedback.unknown(context) }
  }

  fun onFailure(f: ApiFailure) {
    net.value = OrderAlerts.failed(net.value, f.retryable, System.currentTimeMillis())
  }

  fun dismiss(id: String) {
    dismissed.value = dismissed.value + id
  }

  /** بخشِ «سفارش‌ها» خاموش شد ⇒ هیچ نشانی نمی‌ماند */
  fun reset() {
    start.value = emptyList(); dismissed.value = emptySet(); net.value = OrderAlerts.Net(); deskOn.value = false
    announced = emptySet()
  }
}
