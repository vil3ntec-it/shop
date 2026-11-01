package ir.vil3ntec.tohid.core.i18n

/**
 *  جدولِ ویرایشِ درجای کالا (PRD-22، `ProductGridSheet`) — سرستون‌ها، دکمه‌ها، ایرادِ خانه و پیام‌ها.
 *
 *  قاعده‌ها سرِ `I18n.kt`. ⛔ نامِ کالا و متنِ خانه داده‌اند و ترجمه نمی‌شوند.
 */
@Suppress("unused")
object StrGrid {

  val open: String get() = t("جدول", "Grid", "جدول")
  val title: String get() = t("جدولِ ویرایشِ کالا", "Item edit grid", "د توکو د سمون جدول")
  fun changed(a: Any): String = t("$a ردیف عوض شده", "$a rows changed", "$a کتارونه بدل شوي")
  val undo: String get() = t("برگرداندن", "Undo", "بېرته")
  fun save(a: Any): String = t("ثبتِ $a تغییر", "Save $a changes", "$a بدلونونه ثبت کړئ")
  val close: String get() = t("بستن", "Close", "تړل")

  // سرستون‌ها
  val name: String get() = t("نام", "Name", "نوم")
  val category: String get() = t("دسته", "Category", "ډله")
  val unit: String get() = t("واحد", "Unit", "واحد")
  val buy: String get() = t("قیمتِ خرید", "Cost price", "د پېرلو بیه")
  val sell: String get() = t("قیمتِ فروش", "Sale price", "د پلور بیه")
  val min: String get() = t("حدِ کم بودن", "Low-stock level", "د کمښت حد")
  val barcode: String get() = t("بارکد", "Barcode", "بارکوډ")

  // ایرادِ خانه
  val nameEmpty: String get() = t("نام خالی است", "Name is empty", "نوم خالي دی")
  val nameTaken: String get() = t("این نام برای کالای دیگری است", "Another item has this name", "دا نوم د بل توکي دی")
  val badNumber: String get() = t("عدد درست نیست", "Not a valid number", "سم عدد نه دی")
  val negative: String get() = t("منفی نمی‌شود", "Cannot be negative", "منفي کېدای نه شي")
  val barcodeTaken: String get() = t("این بارکد برای کالای دیگری است", "Another item has this barcode", "دا بارکوډ د بل توکي دی")
  fun cellProblem(row: Any, col: Any, why: Any): String = t("ردیفِ $row · $col: $why", "Row $row · $col: $why", "کتار $row · $col: $why")
  fun moreProblems(a: Any): String = t("و $a ایرادِ دیگر", "and $a more problems", "او $a نورې ستونزې")

  // پیام‌ها
  fun pasted(a: Any): String = t("$a خانه چسبانده شد", "$a cells pasted", "$a خانې ونښلول شوې")
  fun cutRows(a: Any): String = t("$a ردیف بیرون از جدول ماند", "$a rows fell outside the grid", "$a کتارونه له جدول بهر پاتې شول")
  fun cutCols(a: Any): String = t("$a خانه بیرون از ستون‌ها ماند", "$a cells fell outside the columns", "$a خانې له ستنو بهر پاتې شوې")
  val clipEmpty: String get() = t("کلیپ‌بورد خالی است", "The clipboard is empty", "کلیپ بورډ تش دی")
  fun saved(a: Any): String = t("$a کالا ذخیره شد", "$a items saved", "$a توکي خوندي شول")
  val fixFirst: String get() = t("اول ایرادهای سرخ را درست کنید", "Fix the red problems first", "لومړی سرې ستونزې سمې کړئ")
  val gone: String get() = t("این کالا دیگر در دکان نیست", "This item is no longer in the shop", "دا توکی نور په دوکان کې نشته")
  fun rowFailed(name: Any, why: Any): String = t("«$name»: $why — هیچ تغییری ثبت نشد", "\"$name\": $why — nothing was saved", "«$name»: $why — هېڅ بدلون ثبت نه شو")
  val discardTitle: String get() = t("تغییرها ثبت نشده‌اند", "Changes are not saved", "بدلونونه نه دي ثبت شوي")
  val discardText: String get() = t("بستنِ جدول تغییرهای ثبت‌نشده را دور می‌ریزد.", "Closing the grid discards the unsaved changes.", "د جدول تړل ناثبت شوي بدلونونه لرې کوي.")
  val discard: String get() = t("دور بریز و ببند", "Discard and close", "لرې یې کړه او وتړه")
  val keep: String get() = t("ادامهٔ ویرایش", "Keep editing", "سمون ته دوام")
  val hint: String get() = t(
    "F2 ویرایش · Enter ثبت و پایین · Tab خانهٔ بعد · Ctrl+V چسباندن از Excel · Ctrl+D تکرارِ ردیفِ بالا · Ctrl+Z برگرداندن · Ctrl+S ثبت · Esc بستن",
    "F2 edit · Enter save and down · Tab next cell · Ctrl+V paste from Excel · Ctrl+D repeat row above · Ctrl+Z undo · Ctrl+S save · Esc close",
    "F2 سمون · Enter ثبت او ښکته · Tab بله خانه · Ctrl+V له Excel نښلول · Ctrl+D د پاسني کتار تکرار · Ctrl+Z بېرته · Ctrl+S ثبت · Esc تړل",
  )
}
