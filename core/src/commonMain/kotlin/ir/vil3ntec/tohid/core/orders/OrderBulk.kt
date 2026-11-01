package ir.vil3ntec.tohid.core.orders

/**
 *  ORD-19 — انتخابِ چندتایی در صفِ سفارش‌ها: چاپِ گروهیِ برگه و تغییرِ وضعیتِ گروهی
 *  (`POST /api/shop/orders/bulk/transition`، `lib/order-bulk.js`، پشتِ پرچمِ خاموشِ `shop.orders.bulk`).
 *
 *  ⛔ گروهی فقط گذارهای بی پول و بی «بیرون رفتنِ کالا» ([TARGETS]): تحویل (`picked_up`، `out_for_delivery`،
 *  `delivered`) فروشِ تحویل را در دفترِ همین برنامه می‌سازد و پیش از فرستادن موجودی را می‌سنجد ⇒ تک‌تک؛
 *  بازپرداخت و ردِ خودکار را سرور هم گروهی نمی‌پذیرد. سرور یک `from` برای همه می‌گیرد ⇒ گذار فقط وقتی
 *  همهٔ انتخاب‌شده‌ها یک وضعیت دارند؛ هر سفارش را سرور جدا می‌سنجد و نتیجه جداست.
 */
object OrderBulk {

  /** همان `MAX_IDS` ِ سرور */
  const val MAX: Int = 50

  val TARGETS: List<String> = listOf("accepted", "picking", "packed", "ready_for_pickup", "rejected", "cancelled")

  /** کلیک روی سفارش در حالتِ انتخاب؛ بیش از [MAX] ⇒ همان انتخابِ پیشین */
  fun toggle(selected: Set<String>, id: String): Set<String> = when {
    id in selected -> selected - id
    selected.size >= MAX -> selected
    else -> selected + id
  }

  /** «همه»: سفارش‌های همین برگه تا سقف، انتخاب‌شده‌های پیشین اول */
  fun all(selected: Set<String>, visible: List<String>): Set<String> = (selected + visible).take(MAX).toSet()

  /**
   *  گذارهای مشترک: [selected] = (وضعیت، کارهای مجازِ همان سفارش برای همین کاربر). خالی یا وضعیتِ ناهمسان ⇒ هیچ.
   */
  fun targets(selected: List<Pair<String, List<String>>>): List<String> {
    if (selected.isEmpty()) return emptyList()
    val from = selected.first().first
    if (selected.any { it.first != from }) return emptyList()
    return TARGETS.filter { t -> selected.all { t in it.second } }
  }

  /** نتیجهٔ یک سفارش در پاسخِ سرور */
  data class Result(val id: String, val ok: Boolean, val unchanged: Boolean, val orderNo: Long, val code: String)

  data class Summary(val changed: Int, val unchanged: Int, val failed: List<Result>)

  /** شمارشِ نتیجه: عوض‌شده، از پیش همان (تکرار)، نشده (با کدِ هر کدام) */
  fun summarize(results: List<Result>): Summary = Summary(
    changed = results.count { it.ok && !it.unchanged },
    unchanged = results.count { it.ok && it.unchanged },
    failed = results.filter { !it.ok },
  )

  /** شناسه‌هایی که پس از گذار از انتخاب بیرون می‌روند (رسیده‌ها)؛ نشده‌ها انتخاب می‌مانند تا کاربر ببیند */
  fun remaining(selected: Set<String>, results: List<Result>): Set<String> =
    selected - results.filter { it.ok }.map { it.id }.toSet()
}
