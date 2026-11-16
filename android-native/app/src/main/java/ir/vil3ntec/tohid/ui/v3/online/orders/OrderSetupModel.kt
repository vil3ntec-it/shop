package ir.vil3ntec.tohid.ui.v3.online.orders

import ir.vil3ntec.tohid.core.model.OrderArrivalDto
import ir.vil3ntec.tohid.core.orders.OrderFlow

/**
 *  ردیفِ تنهای «راه‌اندازیِ سفارشِ آنلاین» زیرِ برگه‌های صف (صاحب‌کار ۱۴۰۵/۰۸/۲۴: «صد تا هشدار اومده… همهٔ این‌ها رو
 *  فعال‌شده بذار») — از `arrival` ِ `/shop/orders/config` (`lib/order-arrival.js`)، خالص (آزمون: `OrderSetupModelTest`).
 *
 *  • کاری که خودِ دکان می‌کند (ساختن/روشن کردنِ ویترین، گذاشتنِ کالا، اشتراک) ⇒ [Status.Todo]: «۲ کار برای راه‌اندازی…»
 *    و برگهٔ فهرست، هر کار یک دکمه.
 *  • دروازه‌ای که فقط پشتیبانی یا سرور باز می‌کند (پرچمِ خاموشِ صریح، دکانِ غیرفعال، تعلیق، سرورِ ناآماده، کدِ ناشناخته)
 *    ⇒ [Status.Off]: یک ردیفِ «سفارشِ آنلاین برای این دکان خاموش است» با «پشتیبانی» — بر کارهای خودِ دکان جلو.
 *  • هشدارها (`alerts_off`) دروازه نیستند (سفارش می‌رسد) ⇒ این‌جا هیچ؛ تنظیمات («سفارشِ آنلاین») همه را می‌گوید.
 */
object OrderSetupModel {

  data class Task(val code: String, val fix: OrderFlow.Fix)

  sealed interface Status {
    data object Ready : Status
    data class Todo(val tasks: List<Task>) : Status
    data class Off(val code: String) : Status
  }

  /** کارهایی که خودِ دکان در برنامه انجام می‌دهد */
  private val OWN = setOf(OrderFlow.Fix.STOREFRONT, OrderFlow.Fix.PRODUCTS, OrderFlow.Fix.SUBSCRIBE)

  fun of(arrival: OrderArrivalDto?): Status {
    val a = arrival ?: return Status.Ready
    val blockers = a.blockers.map { Task(it.code, OrderFlow.fixOf(it.fix)) }
    blockers.firstOrNull { it.fix !in OWN }?.let { return Status.Off(it.code) }
    val tasks = blockers.distinctBy { it.code }
    return if (tasks.isEmpty()) Status.Ready else Status.Todo(tasks)
  }
}
