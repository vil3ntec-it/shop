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

  // میزِ سفارش (ORD-03/04/08/09، `ui/screens/OrderDeskPanel.kt`)
  val deskOpen: String get() = t("باز — سفارش می‌گیریم", "Open — taking orders", "پرانیستی — فرمایش اخلو")
  fun deskBusy(min: Int): String = t("شلوغ — $min دقیقهٔ دیگر", "Busy — $min more minutes", "بوخت — نورې $min دقیقې")
  fun deskPaused(min: Int): String = t("بسته — $min دقیقهٔ دیگر", "Paused — $min more minutes", "تړلی — نورې $min دقیقې")
  val deskAutoPaused: String get() = t("توقفِ خودکار: صف پر یا سفارش بی‌پاسخ", "Auto-paused: queue full or orders unanswered", "په خپله ودرول شو: کتار ډک یا فرمایش بې ځوابه")
  fun deskQueue(n: Int): String = t("صفِ باز: $n", "Open queue: $n", "پرانیستی کتار: $n")
  fun deskBusyFor(min: Int): String = t("شلوغم $min دقیقه", "Busy $min min", "بوخت یم $min دقیقې")
  val deskPause: String get() = t("توقفِ سفارش", "Pause orders", "فرمایشونه ودروئ")
  val deskResume: String get() = t("ادامهٔ سفارش‌گیری", "Resume orders", "فرمایشونه بیا پیل کړئ")
  fun deskMinutes(min: Int): String = t("$min دقیقه", "$min min", "$min دقیقې")
  val deskReasonBusy: String get() = t("سرمان شلوغ است", "Too busy", "ډېر بوخت یو")
  val deskReasonStock: String get() = t("کالا تمام شده", "Out of stock", "توکي خلاص شوي")
  val deskReasonStaff: String get() = t("کارمند کم داریم", "Short of staff", "کارکوونکي لږ دي")
  val deskReasonTech: String get() = t("مشکلِ فنی", "Technical problem", "تخنیکي ستونزه")
  val deskReasonOther: String get() = t("دلیلِ دیگر", "Other reason", "بل دلیل")
  val deskNote: String get() = t("توضیح", "Note", "یادښت")
  val deskSettings: String get() = t("قبول و توقفِ خودکار", "Auto-accept and auto-pause", "په خپله منل او ودرول")
  val deskAutoAccept: String get() = t("قبولِ خودکار", "Auto-accept", "په خپله منل")
  val deskAutoPause: String get() = t("توقفِ خودکار", "Auto-pause", "په خپله ودرول")
  val deskMaxOpen: String get() = t("سقفِ صف", "Queue limit", "د کتار حد")
  val deskReadyMinutes: String get() = t("زمانِ آماده (دقیقه)", "Ready time (min)", "د چمتو کېدو وخت (دقیقې)")
  val deskOldestMinutes: String get() = t("بی‌پاسخ تا (دقیقه)", "Unanswered for (min)", "بې ځوابه تر (دقیقې)")
  val deskPauseMinutes: String get() = t("مدتِ توقف (دقیقه)", "Pause length (min)", "د ودرولو موده (دقیقې)")
  val deskSave: String get() = t("ذخیره", "Save", "خوندي کول")
  val deskSaved: String get() = t("ذخیره شد", "Saved", "خوندي شو")
  val deskChanged: String get() = t("تنظیم‌ها جای دیگری عوض شد؛ دوباره ببینید", "Settings changed elsewhere; check again", "تنظیمات بل ځای بدل شول؛ بیا یې وګورئ")
}
