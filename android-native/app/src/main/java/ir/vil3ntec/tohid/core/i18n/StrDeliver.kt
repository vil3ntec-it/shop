package ir.vil3ntec.tohid.core.i18n

/**
 *  صندوق — فروش با تحویلِ بعدی (POS-38): سبد ⇐ سفارشِ ارسال در بخشِ سفارش‌ها و تحویل.
 *  قاعده‌ها سرِ `I18n.kt`؛ ترجمه‌های نامطمئن در `docs/i18n-glossary.md`.
 */
@Suppress("unused")
object StrDeliver {

  // تنظیمات
  val section: String get() = t("فروش با تحویلِ بعدی", "Sell now, deliver later", "اوس پلور، وروسته سپارل")
  val note: String get() = t(
    "روشن: در صندوق دکمهٔ «تحویلِ بعدی» دیده می‌شود. سبد به‌جای فروش، سفارشِ ارسال (نقد هنگامِ تحویل) می‌شود و به سفارش‌ها و میزِ تحویل می‌رود؛ فروش و کمِ موجودی وقتی ثبت می‌شود که کالا از دکان بیرون رفت. فقط وقتی بخشِ سفارش‌ها برای دکان روشن است و شما اجازهٔ ثبتِ سفارش دارید. فقط روی همین دستگاه.",
    "On: the till shows a \"Deliver later\" button. Instead of a sale, the cart becomes a delivery order (cash on delivery) that goes to Orders and the delivery desk; the sale and the stock change are recorded when the goods leave the shop. Only when Orders is on for the shop and you may create orders. This device only.",
    "روښانه: په صندوق کې د «وروسته سپارل» تڼۍ ښکاري. ټوکرۍ د پلور پر ځای د لېږد فرمایش (د سپارلو پر وخت نغدې) کېږي او فرمایشونو او د سپارلو مېز ته ځي؛ پلور او د موجودي کمښت هغه وخت ثبتېږي چې توکي له دوکان ووځي. یوازې کله چې د دوکان لپاره فرمایشونه روښانه وي او تاسو د فرمایش د ثبت اجازه ولرئ. یوازې همدا وسیله.",
  )

  // دکمه و پنجره
  val button: String get() = t("تحویلِ بعدی", "Deliver later", "وروسته سپارل")
  val title: String get() = t("فروش با تحویلِ بعدی", "Sell now, deliver later", "اوس پلور، وروسته سپارل")
  val explain: String get() = t(
    "سبد سفارشِ ارسال می‌شود و به میزِ تحویل می‌رود. پول هنگامِ تحویل گرفته می‌شود و فروش همان وقت ثبت می‌شود.",
    "The cart becomes a delivery order for the delivery desk. Payment is taken on delivery and the sale is recorded then.",
    "ټوکرۍ د لېږد فرمایش کېږي او د سپارلو مېز ته ځي. پیسې د سپارلو پر وخت اخیستل کېږي او پلور هماغه وخت ثبتېږي.",
  )
  val name: String get() = t("نامِ مشتری", "Customer name", "د پیرودونکي نوم")
  val phone: String get() = t("شمارهٔ مشتری", "Customer phone", "د پیرودونکي شمېره")
  val address: String get() = t("نشانی", "Address", "پته")
  val landmark: String get() = t("نشانه", "Landmark", "نښه")
  val fee: String get() = t("هزینهٔ ارسال (؋)", "Delivery fee (؋)", "د لېږد لګښت (؋)")
  val noteField: String get() = t("یادداشت", "Note", "یادښت")
  fun items(a: Any): String = t("اقلام: $a", "Items: $a", "توکي: $a")
  fun total(a: Any): String = t("جمع هنگامِ تحویل: $a ؋", "Due on delivery: $a ؋", "د سپارلو پر وخت ټولټال: $a ؋")
  val submit: String get() = t("ثبتِ سفارشِ ارسال", "Create delivery order", "د لېږد فرمایش ثبت کړئ")
  fun created(a: Any): String = t("سفارشِ #$a ثبت شد و به میزِ تحویل رفت", "Order #$a was created and sent to the delivery desk", "فرمایش #$a ثبت شو او د سپارلو مېز ته ولاړ")
  val retry: String get() = t(
    "اتصال قطع شد. دوباره «ثبتِ سفارشِ ارسال» را بزنید — سفارش دو بار ثبت نمی‌شود.",
    "The connection dropped. Press \"Create delivery order\" again — the order won't be created twice.",
    "اړیکه پرې شوه. بیا «د لېږد فرمایش ثبت کړئ» ووهئ — فرمایش دوه ځله نه ثبتېږي.",
  )

  // رد
  val emptyCart: String get() = t("سبد خالی است", "The cart is empty", "ټوکرۍ تشه ده")
  val tooManyLines: String get() = t("قلم‌های سبد برای یک سفارش بیش از اندازه است", "Too many lines for one order", "د یوه فرمایش لپاره ډېرې کرښې دي")
  val needName: String get() = t("نامِ مشتری لازم است", "Customer name is required", "د پیرودونکي نوم اړین دی")
  val needPhone: String get() = t("برای ارسال، شمارهٔ مشتری لازم است", "A phone number is required for delivery", "د لېږد لپاره د پیرودونکي شمېره اړینه ده")
  val needAddress: String get() = t("نشانی یا نشانهٔ تحویل لازم است", "An address or landmark is required", "پته یا د سپارلو نښه اړینه ده")
  val badFee: String get() = t("هزینهٔ ارسال درست نیست", "The delivery fee is not valid", "د لېږد لګښت سم نه دی")
  fun badLine(a: Any): String = t("مقدار یا قیمتِ «$a» درست نیست", "The quantity or price of \"$a\" is not valid", "د «$a» اندازه یا بیه سمه نه ده")
}
