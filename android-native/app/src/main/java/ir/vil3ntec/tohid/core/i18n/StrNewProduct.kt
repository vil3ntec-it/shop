package ir.vil3ntec.tohid.core.i18n

/**
 *  فرمِ جمع‌وجورِ «کالای جدید» (۱۴۰۵/۰۸/۲۴): کادرهای کشویی، بخشِ «اختیاری» (موجودی در انبار، تاریخِ تولید و انقضا) و
 *  پیامِ ثبت. نوشته‌های مشترک با ویرایش در `StrProducts`، تاریخ‌ها در `StrStock`. قاعده‌ها سرِ `I18n.kt`.
 */
@Suppress("unused")
object StrNewProduct {
  val code: String get() = t("کد / بارکد", "Code / barcode", "کوډ / بارکوډ")
  val choose: String get() = t("انتخاب کنید", "Choose", "وټاکئ")
  val selected: String get() = t("انتخاب‌شده", "Selected", "ټاکل شوی")

  // بخشِ «اختیاری»
  val optional: String get() = t("اختیاری", "Optional", "اختیاري")
  val optionalNote: String get() = t(
    "موجودی و تاریخ‌ها — با ذخیره به انبار هم ثبت می‌شود",
    "Stock and dates — saving also records them in stock",
    "موجودي او نېټې — په خوندي کولو سره ګدام ته هم ثبتېږي",
  )
  val stock: String get() = t("موجودی در انبار", "Stock on hand", "په ګدام کې موجودي")
  fun stockHelper(qty: Any, unit: Any, total: Any): String = t(
    "با ذخیره، ${asIs(qty)} ${asIs(unit)} به انبار وارد می‌شود · ${asIs(total)} ؋",
    "Saving adds $qty $unit to stock · $total ؋",
    "په خوندي کولو سره $qty $unit ګدام ته ورځي · $total ؋",
  )
  val stockForDates: String get() = t(
    "تاریخ روی همین موجودی می‌نشیند — مقدار را بنویسید",
    "Dates go on this stock — enter a quantity",
    "نېټې پر همدې موجودۍ کېني — اندازه ولیکئ",
  )
  val badDate: String get() = t("سال، ماه و روز را کامل بنویسید", "Type the full year, month and day", "کال، میاشت او ورځ بشپړ ولیکئ")
  fun daysLeft(n: Any): String = t("${asIs(n)} روز مانده", "$n ${pl(n, "day", "days")} left", "$n ورځې پاتې")
  fun nearExpiry(n: Any): String = t(
    "نزدیکِ انقضا · ${asIs(n)} روز مانده",
    "Near expiry · $n ${pl(n, "day", "days")} left",
    "د ختمېدو ته نږدې · $n ورځې پاتې",
  )
  val expiresToday: String get() = t("امروز منقضی می‌شود", "Expires today", "نن ختمېږي")
  val alreadyExpired: String get() = t("این تاریخ گذشته — کالا منقضی است", "This date has passed — already expired", "دا نېټه تېره ده — توکی ختم شوی")

  // فرمِ سادهٔ یک‌کالایی (۱۴۰۵/۰۸/۲۴، دکمهٔ «کالای تازه»): نام و قیمتِ فروش، بقیه زیرِ «بیشتر»
  val moreHint: String get() = t(
    "قیمتِ خرید، دسته، واحد، بارکد، موجودی و تاریخ",
    "Purchase price, category, unit, barcode, stock and dates",
    "د پېرود بیه، ډله، واحد، بارکوډ، موجودي او نېټې",
  )
  fun savedOne(name: Any): String = t("«${asIs(name)}» ثبت شد", "“$name” saved", "«$name» ثبت شو")
  fun savedOneWithStock(name: Any): String = t(
    "«${asIs(name)}» ثبت شد · موجودی به انبار رفت",
    "“$name” saved · stock recorded",
    "«$name» ثبت شو · موجودي ګدام ته ولاړه",
  )

  fun savedWithStock(n: Int, stocked: Int): String = t(
    "${asIs(n)} کالا ثبت شد · موجودیِ ${asIs(stocked)} کالا به انبار رفت",
    "$n ${pl(n, "product", "products")} saved · stock recorded for $stocked",
    "$n توکي ثبت شول · د $stocked توکو موجودي ګدام ته ولاړه",
  )
}
