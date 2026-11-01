package ir.vil3ntec.tohid.core.i18n

/**
 *  حالتِ لمسیِ کامپیوتر و صفحه‌کلیدِ عددیِ صندوق (POS-31، POS-07؛ §۵.۳).
 *  قاعده‌ها سرِ `I18n.kt`؛ ترجمه‌های نامطمئن در `docs/i18n-glossary.md`.
 */
@Suppress("unused")
object StrTouch {

  // تنظیمات
  val section: String get() = t("حالتِ لمسیِ صندوق (کامپیوتر)", "Touch mode for the till (computer)", "د صندوق لمسي حالت (کمپیوټر)")
  val note: String get() = t(
    "روشن: کلیدِ «حالتِ لمسی» در نوارِ بالای صندوق دیده می‌شود. در حالتِ لمسی دکمه‌ها و ردیف‌ها دست‌کم ۴۸ واحدند، فهرستِ کالاها و سبد دکمهٔ «صفحهٔ بعد/قبل» دارند و صفحه‌کلیدِ عددی (تعداد، قیمت، تخفیف) زیرِ سبد است. قیمت و تخفیف همان قاعده‌های «قیمت و تخفیفِ ردیف» را دارند. فقط روی همین دستگاه.",
    "On: a \"Touch mode\" switch appears on the till's top bar. In touch mode, buttons and rows are at least 48 units, the item list and the cart get \"Next/Previous page\" buttons, and a number pad (quantity, price, discount) sits under the cart. Price and discount follow the same \"Line prices and discounts\" rules. This device only.",
    "روښانه: د صندوق په پورتنۍ پټه کې د «لمسي حالت» کیلي ښکاري. په لمسي حالت کې تڼۍ او کرښې لږ تر لږه ۴۸ واحده دي، د توکو لړلیک او ټوکرۍ د «بل/مخکینی مخ» تڼۍ لري او د شمېرو تخته (شمېر، بیه، تخفیف) د ټوکرۍ لاندې ده. بیه او تخفیف د «د کرښې بیه او تخفیف» همغه قاعدې لري. یوازې همدا وسیله.",
  )

  // نوارِ صندوق
  val toggle: String get() = t("حالتِ لمسی", "Touch mode", "لمسي حالت")
  val toggleOn: String get() = t("حالتِ لمسی روشن است — زدن: موشی", "Touch mode is on — tap for mouse mode", "لمسي حالت روښانه دی — د موږک حالت لپاره کلیک")
  val toggleOff: String get() = t("حالتِ لمسی خاموش است — زدن: لمسی", "Touch mode is off — tap for touch mode", "لمسي حالت بند دی — د لمسي حالت لپاره کلیک")
  val pageNext: String get() = t("صفحهٔ بعد", "Next page", "بل مخ")
  val pagePrev: String get() = t("صفحهٔ قبل", "Previous page", "مخکینی مخ")

  // صفحه‌کلیدِ عددی
  val keypad: String get() = t("صفحه‌کلیدِ عددی", "Number pad", "د شمېرو تخته")
  val modeQty: String get() = t("تعداد", "Quantity", "شمېر")
  val modePrice: String get() = t("قیمت", "Price", "بیه")
  val modeDiscount: String get() = t("تخفیف", "Discount", "تخفیف")
  val apply: String get() = t("ثبت", "Apply", "ثبتول")
  val clear: String get() = t("پاک", "Clear", "پاکول")
  val back: String get() = t("پاک کردنِ رقمِ آخر", "Delete last digit", "وروستۍ شمېره پاکول")
  val pickLine: String get() = t("نخست ردیفی از سبد را انتخاب کنید", "Select a cart line first", "لومړی د ټوکرۍ یوه کرښه وټاکئ")
  fun forLine(name: String): String = t("برای: $name", "For: $name", "لپاره: $name")
  val discountOff: String get() = t(
    "تخفیفِ ردیف خاموش است یا برای شما مجاز نیست",
    "Line discounts are off or not allowed for you",
    "د کرښې تخفیف بند دی یا تاسو ته اجازه نشته",
  )
  val zero: String get() = t("صفر پذیرفته نیست", "Zero isn't accepted", "صفر نه منل کېږي")
  val percentOver: String get() = t("تخفیف بیش از ۱۰۰٪ نمی‌شود", "A discount can't be over 100%", "تخفیف له ۱۰۰٪ نه زیات نه شي")
  val badNumber: String get() = t("عدد درست نیست", "Not a valid number", "شمېره سمه نه ده")
}
