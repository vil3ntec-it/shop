package ir.vil3ntec.tohid.core.i18n

import ir.vil3ntec.tohid.core.product.PurchaseUnits

/**
 *  PRD-07 — واحدهای خریدِ چندگانه: بخشِ «واحدهای خرید» ِ فرمِ کالا (گوشیِ نسخهٔ ۳ و کامپیوتر)، انتخابِ واحدِ ردیفِ
 *  فاکتورِ خرید و سفارشِ خرید، و پیامِ ردِ هر کدِ `core/product/PurchaseUnits.Code`. هر جای خالیِ فارسی با `asIs`.
 */
@Suppress("unused")
object StrPurchaseUnits {

  // فرمِ کالا
  val title: String get() = t("واحدهای خرید (اختیاری)", "Purchase units (optional)", "د پېرلو واحدونه (اختیاري)")
  fun help(base: String): String = t(
    "هر واحدی که از تأمین‌کننده می‌خرید (کارتنِ ۱۲، کارتنِ ۲۴) با چند ${asIs(base)} بودنش. موجودی و قیمت همیشه به ${asIs(base)} می‌مانند.",
    "Each unit you buy from a supplier (carton of 12, carton of 24) and how many $base it holds. Stock and prices stay in $base.",
    "هر واحد چې له عرضه کوونکي یې اخلئ (۱۲ یي کارتن، ۲۴ یي کارتن) او څو $base لري. موجودي او بیه تل په $base پاتې کېږي.",
  )
  val name: String get() = t("نامِ واحد (مثلاً کارتن)", "Unit name (e.g. carton)", "د واحد نوم (لکه کارتن)")
  fun factor(base: String): String = t("چند ${asIs(base)}", "How many $base", "څو $base")
  val supplier: String get() = t("تأمین‌کنندهٔ پیش‌فرض", "Default supplier", "اصلي عرضه کوونکی")
  val anySupplier: String get() = t("هر تأمین‌کننده", "Any supplier", "هر عرضه کوونکی")
  val barcode: String get() = t("بارکدِ همین واحد (اختیاری)", "Barcode of this unit (optional)", "د همدې واحد بارکوډ (اختیاري)")
  val add: String get() = t("افزودنِ واحدِ خرید", "Add purchase unit", "د پېرلو واحد زیات کړئ")
  val remove: String get() = t("برداشتن", "Remove", "لرې کول")
  val readOnly: String get() = t(
    "ویرایشِ واحدهای خرید در حدِ نقشِ شما نیست.",
    "Your role can't edit purchase units.",
    "ستاسو رول د پېرلو واحدونه نه شي سمولی.",
  )
  val hasUnits: String get() = t(
    "این کالا واحدِ خرید دارد — پیش از عوض کردنِ واحد، واحدهای خرید را با همان فرم درست کنید.",
    "This product has purchase units — fix them in the same form before changing the unit.",
    "دا توکی د پېرلو واحدونه لري — د واحد له بدلولو مخکې یې په همدې فورمه کې سم کړئ.",
  )

  // ردیفِ خرید و سفارش
  val unit: String get() = t("واحدِ خرید", "Purchase unit", "د پېرلو واحد")
  fun baseOnly(base: String): String = t("${asIs(base)} (پایه)", "$base (base)", "$base (اساسي)")
  fun lineBase(qty: String, base: String, cost: String): String = t(
    "= ${asIs(qty)} ${asIs(base)} · بهای هر ${asIs(base)} ${asIs(cost)}",
    "= $qty $base · cost per $base $cost",
    "= $qty $base · د هر $base بیه $cost",
  )
  fun priceIn(name: String): String = t("قیمتِ یک ${asIs(name)}", "Price of one $name", "د یو $name بیه")
  fun scanned(product: String, unit: String): String = t(
    "«${asIs(product)}» با واحدِ «${asIs(unit)}»",
    "“$product” in “$unit”",
    "«$product» په «$unit» واحد",
  )
  val unknownUnit: String get() = t("این واحدِ خرید دیگر روی کالا نیست", "This purchase unit is no longer on the product", "دا د پېرلو واحد نور په توکي نشته")
  fun wholeBase(base: String): String = t(
    "این مقدار به «${asIs(base)}» عددِ درست نمی‌شود — «${asIs(base)}» اعشار نمی‌گیرد",
    "This quantity is not a whole number of “$base” — “$base” takes no decimals",
    "دا مقدار په «$base» پوره عدد نه کېږي — «$base» اعشار نه اخلي",
  )

  // پیامِ ردِ هر کدِ `PurchaseUnits.Code` (`n` نامِ واحد، `b` واحدِ پایه) — گزینش در [purchaseUnitProblem]
  val tooMany: String get() = t(
    "حداکثر ${asIs(PurchaseUnits.MAX_UNITS)} واحدِ خرید برای یک کالا",
    "At most ${PurchaseUnits.MAX_UNITS} purchase units per product",
    "د یو توکي لپاره تر ${PurchaseUnits.MAX_UNITS} د پېرلو واحدونه",
  )
  val nameRequired: String get() = t("نامِ واحدِ خرید را بنویسید (مثلاً کارتن)", "Write the purchase unit name (e.g. carton)", "د پېرلو د واحد نوم ولیکئ (لکه کارتن)")
  val nameTooLong: String get() = t(
    "نامِ واحدِ خرید بیش از ${asIs(PurchaseUnits.MAX_NAME)} نویسه است",
    "Purchase unit name is longer than ${PurchaseUnits.MAX_NAME} characters",
    "د پېرلو د واحد نوم له ${PurchaseUnits.MAX_NAME} تورو اوږد دی",
  )
  fun nameIsBase(n: String): String = t("«${asIs(n)}» همان واحدِ پایهٔ کالاست", "“$n” is the product's base unit", "«$n» د توکي اساسي واحد دی")
  fun duplicateName(n: String): String = t("واحدِ خریدِ «${asIs(n)}» دو بار آمده", "Purchase unit “$n” appears twice", "د پېرلو واحد «$n» دوه ځله راغلی")
  fun badFactor(n: String, b: String): String = t(
    "واحدِ «${asIs(n)}» چند ${asIs(b)} است؟ عددِ بیش از صفر بنویسید",
    "How many $b is “$n”? Write a number above zero",
    "«$n» څو $b دی؟ له صفر لوی عدد ولیکئ",
  )
  fun factorTooBig(n: String): String = t("ضریبِ واحدِ «${asIs(n)}» بیش از حد بزرگ است", "The factor of “$n” is too large", "د «$n» ضریب ډېر لوی دی")
  fun wholeOnly(n: String, b: String): String = t(
    "«${asIs(b)}» اعشار نمی‌گیرد — ضریبِ «${asIs(n)}» عددِ درست باشد",
    "“$b” takes no decimals — the factor of “$n” must be a whole number",
    "«$b» اعشار نه اخلي — د «$n» ضریب باید پوره عدد وي",
  )
  fun badSupplier(n: String): String = t("تأمین‌کنندهٔ واحدِ «${asIs(n)}» در دفتر نیست", "The supplier of “$n” is not in the ledger", "د «$n» عرضه کوونکی په دفتر کې نشته")
  fun badBarcode(n: String): String = t("واحدِ «${asIs(n)}» فقط یک بارکدِ درست می‌گیرد", "“$n” takes a single valid barcode", "«$n» یوازې یو سم بارکوډ اخلي")
  fun duplicateBarcode(n: String): String = t("بارکدِ واحدِ «${asIs(n)}» دو بار آمده", "The barcode of “$n” appears twice", "د «$n» بارکوډ دوه ځله راغلی")
  fun barcodeIsProduct(n: String): String = t(
    "بارکدِ واحدِ «${asIs(n)}» همان بارکدِ خودِ کالاست",
    "The barcode of “$n” is the product's own barcode",
    "د «$n» بارکوډ د توکي خپل بارکوډ دی",
  )
  fun barcodeTaken(n: String): String = t(
    "بارکدِ واحدِ «${asIs(n)}» قبلاً برای کالای دیگری ثبت شده",
    "The barcode of “$n” already belongs to another product",
    "د «$n» بارکوډ مخکې د بل توکي لپاره ثبت شوی",
  )
  fun barcodeOtherPack(n: String): String = t(
    "بارکدِ واحدِ «${asIs(n)}» بارکدِ بسته‌ای با اندازهٔ دیگر است",
    "The barcode of “$n” belongs to a pack of another size",
    "د «$n» بارکوډ د بل اندازې د بستې دی",
  )
  val invalid: String get() = t("واحدهای خرید درست نیستند", "Purchase units are not valid", "د پېرلو واحدونه سم نه دي")
  val unitWord: String get() = t("واحد", "unit", "واحد")
}

/**
 *  پیامِ ردِ یک کدِ `PurchaseUnits.Code` ِ هسته — بیرونِ جدول، چون ورودی‌اش شیءِ هسته است (جدول‌ها فقط متن و عدد می‌گیرند،
 *  `I18nCoverageTest`)؛ هر پیام خودش در [StrPurchaseUnits] است.
 */
fun purchaseUnitProblem(f: PurchaseUnits.Failure, base: String): String {
  val n = f.name.ifBlank { (f.index + 1).toString() }
  val b = base.ifBlank { StrPurchaseUnits.unitWord }
  val s = StrPurchaseUnits
  return when (f.code) {
    PurchaseUnits.Code.TOO_MANY -> s.tooMany
    PurchaseUnits.Code.NAME_REQUIRED -> s.nameRequired
    PurchaseUnits.Code.NAME_TOO_LONG -> s.nameTooLong
    PurchaseUnits.Code.NAME_IS_BASE -> s.nameIsBase(n)
    PurchaseUnits.Code.DUPLICATE_NAME -> s.duplicateName(n)
    PurchaseUnits.Code.BAD_FACTOR -> s.badFactor(n, b)
    PurchaseUnits.Code.FACTOR_TOO_BIG -> s.factorTooBig(n)
    PurchaseUnits.Code.WHOLE_ONLY -> s.wholeOnly(n, b)
    PurchaseUnits.Code.BAD_SUPPLIER -> s.badSupplier(n)
    PurchaseUnits.Code.BAD_BARCODE -> s.badBarcode(n)
    PurchaseUnits.Code.DUPLICATE_BARCODE -> s.duplicateBarcode(n)
    PurchaseUnits.Code.BARCODE_IS_PRODUCT -> s.barcodeIsProduct(n)
    PurchaseUnits.Code.BARCODE_TAKEN -> s.barcodeTaken(n)
    PurchaseUnits.Code.BARCODE_IS_OTHER_PACK -> s.barcodeOtherPack(n)
    else -> s.invalid
  }
}
