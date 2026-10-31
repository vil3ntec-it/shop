package ir.vil3ntec.tohid.print

// i18n: سه‌زبانه

import android.content.Context
import android.text.Layout
import ir.vil3ntec.tohid.Jalali
import ir.vil3ntec.tohid.core.hw.EscPos
import ir.vil3ntec.tohid.core.hw.PrintQueue
import ir.vil3ntec.tohid.core.i18n.StrOrderSlip
import ir.vil3ntec.tohid.core.model.OrderDto
import ir.vil3ntec.tohid.core.orders.OrderAutoPrint
import ir.vil3ntec.tohid.core.orders.OrderSlip
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 *  ORD-10 — سمتِ پلتفرمِ `core/orders/OrderSlip`: تنظیمِ «چاپِ خودکارِ برگهٔ سفارش پس از قبول»
 *  روی **همین دستگاه** (`SharedPreferences`ِ «tohid»، همگام نمی‌شود، پیش‌فرض خاموش)، حافظهٔ
 *  سفارش‌های چاپ‌شده (`OrderAutoPrint`)، کشیدنِ ردیف‌ها با قلمِ برنامه (`HwPages.text`) و
 *  گذاشتن در صفِ چاپ (`PrintSpooler`، نوعِ گزارش: بی فروش و بی نسخهٔ «چاپِ دوباره»).
 *
 *  خاموش ⇒ هیچ: نه خواندنِ چاپگر، نه کاری در صف — همان رفتارِ امروز.
 */
object OrderSlipPrint {

  private const val KEY_AUTO = "order_slip_auto"
  private const val KEY_PRINTED = "order_slip_printed"

  private fun prefs(context: Context) = context.getSharedPreferences("tohid", Context.MODE_PRIVATE)

  fun auto(context: Context): Boolean = runCatching { prefs(context).getBoolean(KEY_AUTO, false) }.getOrDefault(false)

  fun setAuto(context: Context, on: Boolean) = prefs(context).edit().putBoolean(KEY_AUTO, on).apply()

  /** سفارشِ سرور ⇐ مدلِ خالصِ برگه (بی شماره و بی نشانیِ کامل) */
  fun slipOf(o: OrderDto): OrderSlip.Order = OrderSlip.Order(
    orderNo = o.orderNo, fulfillment = o.fulfillment, paymentMethod = o.paymentMethod, paymentStatus = o.paymentStatus,
    customerName = o.customer.name, landmark = o.address.landmark, note = o.note,
    scheduledFor = o.scheduledFor, readyAt = o.readyAt, isTest = o.isTest,
    lines = o.lines.map { OrderSlip.Line(it.name, it.unit, it.qtyMilli, it.unitPriceMinor, it.lineTotalMinor, it.note) },
    subtotalMinor = o.subtotalMinor, discountMinor = o.discountMinor, deliveryFeeMinor = o.deliveryFeeMinor,
    totalMinor = o.totalMinor,
  )

  /** زمانِ محلی به تقویمِ خورشیدی: «7/25 14:05» (همان نوشتهٔ صفحهٔ سفارش‌ها) */
  private fun stamp(ms: Long): String {
    val cal = java.util.Calendar.getInstance().apply { timeInMillis = ms }
    val j = Jalali.of(cal.get(java.util.Calendar.YEAR), cal.get(java.util.Calendar.MONTH) + 1, cal.get(java.util.Calendar.DAY_OF_MONTH))
    val hh = cal.get(java.util.Calendar.HOUR_OF_DAY).toString().padStart(2, '0')
    val mm = cal.get(java.util.Calendar.MINUTE).toString().padStart(2, '0')
    return "${j.month}/${j.day} $hh:$mm"
  }

  private fun lineOf(r: OrderSlip.Row): HwPages.Line = HwPages.Line(
    r.text,
    when (r.kind) {
      OrderSlip.Kind.TITLE -> HwPages.Style.TITLE
      OrderSlip.Kind.HEAD -> HwPages.Style.HEAD
      OrderSlip.Kind.BODY -> HwPages.Style.BODY
      OrderSlip.Kind.SMALL -> HwPages.Style.SMALL
    },
    if (r.center) Layout.Alignment.ALIGN_CENTER else Layout.Alignment.ALIGN_NORMAL,
  )

  /**
   *  پس از پاسخِ **موفقِ** گذار روی همین دستگاه (`from` = وضعیتِ پیش از فرستادن). کاری نبود ⇒ `null`؛
   *  وگرنه پیامی برای صفحه («چاپ شد» یا چرا نشد). هر سفارش یک بار روی هر دستگاه.
   */
  suspend fun afterTransition(context: Context, from: String, after: OrderDto): String? {
    if (!auto(context)) return null
    val printed = synchronized(this) { OrderAutoPrint.parse(prefs(context).getString(KEY_PRINTED, null)) }
    if (!OrderAutoPrint.shouldPrint(true, from, after.status, after.id, printed)) return null
    val printer = PrinterRegistry.defaultSaved(context) ?: return StrOrderSlip.noPrinter
    val bytes = withContext(Dispatchers.IO) {
      runCatching {
        val p = printer.profile()
        val rows = OrderSlip.rows(slipOf(after), StrOrderSlip, System.currentTimeMillis(), ::stamp)
        EscPos.document(HwPages.text(context, p.paperDots, rows.map(::lineOf)), p)
      }
    }.getOrElse { return StrOrderSlip.failed(it.message ?: StrOrderSlip.unknown) }
    //  پیش از صف به حافظه می‌رود: پاسخِ دوباره یا ضربهٔ دوم برگهٔ دوم نمی‌سازد؛ کارِ صف خودش دوباره تلاش می‌شود
    synchronized(this) {
      val now = OrderAutoPrint.parse(prefs(context).getString(KEY_PRINTED, null))
      prefs(context).edit().putString(KEY_PRINTED, OrderAutoPrint.encode(OrderAutoPrint.remember(now, after.id))).apply()
    }
    return PrintSpooler.submitAndWait(
      context, PrintQueue.Kind.REPORT, printer.link, StrOrderSlip.jobTitle(after.orderNo.toString()), bytes,
      done = StrOrderSlip.printed,
    )
  }
}
