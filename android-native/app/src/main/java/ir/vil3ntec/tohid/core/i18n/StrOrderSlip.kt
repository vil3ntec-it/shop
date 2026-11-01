package ir.vil3ntec.tohid.core.i18n

import ir.vil3ntec.tohid.core.orders.OrderSlip

/**
 *  ORD-10 — «برگهٔ سفارش» پس از قبول و تنظیمِ چاپِ خودکارش (`core/orders/OrderSlip`).
 *  قاعده‌ها سرِ `I18n.kt`؛ ترجمه‌های نامطمئن در `docs/i18n-glossary.md`.
 */
@Suppress("unused")
object StrOrderSlip : OrderSlip.Texts {

  // برگه
  override fun title(orderNo: String): String = t("سفارش #$orderNo", "Order #$orderNo", "فرمایش #$orderNo")
  override val test: String get() = t("— سفارشِ آزمایشی —", "— Test order —", "— آزمایښتي فرمایش —")
  override val delivery: String get() = t("ارسال", "Delivery", "لېږل")
  override val pickup: String get() = t("تحویلِ حضوری", "Pickup in store", "په دوکان کې سپارل")
  override fun scheduled(at: String): String = t("زمان‌بندی‌شده: $at", "Scheduled: $at", "مهالوېشل شوی: $at")
  override fun readyBy(at: String): String = t("آماده تا: $at", "Ready by: $at", "تر دې وخته چمتو: $at")
  override fun customer(name: String): String = t("مشتری: $name", "Customer: $name", "پېرودونکی: $name")
  override fun landmark(text: String): String = t("نشانه: $text", "Landmark: $text", "نښه: $text")
  override val cod: String get() = t("نقد هنگامِ تحویل", "Cash on delivery", "د سپارلو پر وخت نغدې")
  override val paid: String get() = t("پرداخت‌شده", "Paid", "ورکړل شوی")
  override val unpaid: String get() = t("پرداخت‌نشده", "Unpaid", "نه دی ورکړل شوی")
  override fun collect(amount: String): String = t("دریافتی: $amount ؋", "To collect: $amount ؋", "اخیستل کېږي: $amount ؋")
  override fun line(name: String, qty: String, total: String): String = t("$name — $qty — $total", "$name — $qty — $total", "$name — $qty — $total")
  override fun lineNote(note: String): String = t("   یادداشت: $note", "   Note: $note", "   یادښت: $note")
  override fun subtotal(amount: String): String = t("جمعِ اقلام: $amount", "Items: $amount", "د توکو ټولګه: $amount")
  override fun discount(amount: String): String = t("تخفیف: -$amount", "Discount: -$amount", "تخفیف: -$amount")
  override fun deliveryFee(amount: String): String = t("هزینهٔ ارسال: $amount", "Delivery fee: $amount", "د لېږلو لګښت: $amount")
  override fun total(amount: String): String = t("جمعِ کل: $amount ؋", "Total: $amount ؋", "ټوله: $amount ؋")
  override fun orderNote(note: String): String = t("یادداشتِ سفارش: $note", "Order note: $note", "د فرمایش یادښت: $note")
  override fun printedAt(at: String): String = t("چاپ: $at", "Printed: $at", "چاپ: $at")

  // صفِ چاپ و تنظیم
  fun jobTitle(orderNo: String): String = t("برگهٔ سفارش #$orderNo", "Order slip #$orderNo", "د فرمایش پاڼه #$orderNo")
  val printed: String get() = t("برگهٔ سفارش چاپ شد", "Order slip printed", "د فرمایش پاڼه چاپ شوه")
  fun failed(why: String): String = t("برگهٔ سفارش چاپ نشد: $why", "Order slip not printed: $why", "د فرمایش پاڼه چاپ نه شوه: $why")
  val unknown: String get() = t("دلیل نامعلوم", "unknown reason", "نامعلوم لامل")
  val noPrinter: String get() = t(
    "برگهٔ سفارش چاپ نشد: چاپگرِ فاکتور تعریف نشده — تنظیمات › دستگاه‌ها",
    "Order slip not printed: no receipt printer set — Settings › Devices",
    "د فرمایش پاڼه چاپ نه شوه: د رسید چاپګر نه دی ټاکل شوی — تنظیمات › وسیلې",
  )
  val setting: String get() = t("چاپِ خودکارِ برگهٔ سفارش پس از قبول", "Print the order slip after accepting", "له منلو وروسته د فرمایش پاڼه پخپله چاپول")
  val settingNote: String get() = t(
    "روی چاپگرِ پیش‌فرضِ همین دستگاه، یک بار برای هر سفارشی که این‌جا قبول شد. خاموش = بی برگه.",
    "On this device's default printer, once for each order accepted here. Off = no slip.",
    "د همدې وسیلې پر ډیفالټ چاپګر، د هر هغه فرمایش لپاره یو ځل چې دلته ومنل شو. بند = پاڼه نشته.",
  )
}
