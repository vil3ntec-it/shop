package ir.vil3ntec.tohid.core.i18n

/**
 *  PUR-12 / AI-10 — «از عکسِ فاکتور» در فرمِ «فاکتورِ خریدِ تازه» ِ گوشی و کامپیوتر (`ui/screens/InvoiceOcrUi.kt`، منطق در
 *  `core/purchase/InvoiceOcr`). ⛔ هیچ نوشته‌ای وعدهٔ «خودکار» نمی‌دهد: هر ردیف را کاربر می‌سنجد و تأیید می‌کند.
 *  قاعده‌ها سرِ `I18n.kt`؛ دریِ جمله‌ها در `DariText.PHRASES` و `docs/DARI-REVIEW-fa.md`.
 */
@Suppress("unused")
object StrInvoiceOcr {

  val button: String get() = t("از عکسِ فاکتور", "From invoice photo", "د بل له انځوره")
  val title: String get() = t("خواندنِ فاکتورِ کاغذی", "Read a paper invoice", "د کاغذي بل لوستل")
  val pickNote: String get() = t(
    "از فاکتورِ تأمین‌کننده صاف و روشن عکس بگیرید. عکس فقط برای خواندن فرستاده می‌شود و روی سرور نگه داشته نمی‌شود.",
    "Take a straight, well-lit photo of the supplier invoice. It is sent only to be read and is not kept on the server.",
    "د برابروونکي له بل څخه سم او روښانه انځور واخلئ. انځور یوازې د لوستلو لپاره لېږل کېږي او په سرور کې نه ساتل کېږي.",
  )
  val takePhoto: String get() = t("انتخابِ عکس", "Choose photo", "انځور وټاکئ")
  val busy: String get() = t("در حالِ خواندنِ فاکتور…", "Reading the invoice…", "بل لوستل کېږي…")
  val unavailableTitle: String get() = t("خواندنِ عکس روی سرور نصب نیست", "Photo reading is not installed on the server", "د انځور لوستل په سرور کې نه دي لګول شوي")
  val unavailable: String get() = t(
    "صاحبِ سرور باید روی همان سرور tesseract-ocr و tesseract-ocr-fas را نصب کند؛ تا آن وقت ردیف‌ها را دستی بنویسید.",
    "The server owner must install tesseract-ocr and tesseract-ocr-fas on that server; until then, type the rows by hand.",
    "د سرور خاوند باید په همدې سرور کې tesseract-ocr او tesseract-ocr-fas ولګوي؛ تر هغه کرښې په لاس ولیکئ.",
  )
  val needsPlan: String get() = t(
    "خواندنِ عکسِ فاکتور اشتراکِ فعال می‌خواهد؛ فاکتور را همچنان می‌توانید دستی بنویسید.",
    "Reading invoice photos needs an active subscription; you can still type the invoice by hand.",
    "د بل د انځور لوستل فعال ګډون غواړي؛ بل لا هم په لاس لیکلی شئ.",
  )
  val nothing: String get() = t(
    "در این عکس ردیفی خوانده نشد. عکسِ صاف‌تر و روشن‌تری بگیرید یا ردیف‌ها را دستی بنویسید.",
    "No rows could be read in this photo. Take a straighter, brighter photo or type the rows by hand.",
    "په دې انځور کې کومه کرښه ونه لوستل شوه. سم او روښانه انځور واخلئ یا کرښې په لاس ولیکئ.",
  )
  val unreadable: String get() = t("عکس باز نشد؛ عکسِ دیگری بگیرید.", "The photo could not be opened; take another one.", "انځور خلاص نه شو؛ بل انځور واخلئ.")
  val notice: String get() = t(
    "هر ردیف را با عکس بسنجید و تأیید کنید؛ فقط ردیف‌های تأییدشده به فاکتور می‌روند و هیچ چیز خودکار ثبت نمی‌شود.",
    "Check each row against the photo and confirm it; only confirmed rows go into the invoice and nothing is saved automatically.",
    "هره کرښه له انځور سره پرتله او تایید کړئ؛ یوازې تایید شوې کرښې بل ته ځي او هېڅ شی پخپله نه ثبتېږي.",
  )
  fun read(raw: Any): String = t("خوانده‌شده: ${asIs(raw)}", "Read: $raw", "لوستل شوي: $raw")
  val product: String get() = t("کالا", "Item", "توکی")
  val pickProduct: String get() = t("کالا را انتخاب کنید", "Choose the item", "توکی وټاکئ")
  val otherProduct: String get() = t("کالای دیگر…", "Another item…", "بل توکی…")
  val confirm: String get() = t("تأیید", "Confirm", "تایید")
  val confirmed: String get() = t("تأیید شد", "Confirmed", "تایید شو")
  val skip: String get() = t("رد", "Skip", "پرېږده")
  val skipped: String get() = t("رد شد", "Skipped", "پرېښودل شو")
  val undo: String get() = t("برگرداندن", "Undo", "بېرته")
  val flagMismatch: String get() = t(
    "عددهای این ردیف با هم جور نیستند؛ مقدار و فی را از روی عکس بنویسید",
    "The numbers in this row do not add up; type the quantity and price from the photo",
    "د دې کرښې شمېرې سره برابرې نه دي؛ شمېر او بیه له انځوره ولیکئ",
  )
  val flagOrder: String get() = t("جای مقدار و فی حدسی است؛ بسنجید", "Quantity and price may be swapped; check them", "د شمېر او بیې ځای اټکلي دی؛ یې وګورئ")
  val flagLow: String get() = t("این خط کم‌رنگ خوانده شد", "This line was read faintly", "دا کرښه کمرنګه ولوستل شوه")
  val flagNoMatch: String get() = t(
    "کالایی با این نام پیدا نشد؛ کالا را انتخاب کنید یا رد کنید",
    "No item with this name was found; choose an item or skip the row",
    "په دې نوم توکی ونه موندل شو؛ توکی وټاکئ یا کرښه پرېږدئ",
  )
  val flagWeak: String get() = t("نامِ کالا فقط شبیه است؛ مطمئن شوید", "The item name only looks similar; make sure", "د توکي نوم یوازې ورته دی؛ ډاډ ترلاسه کړئ")
  fun addRows(n: Any): String = t("افزودنِ ${asIs(n)} ردیف به فاکتور", "Add $n ${pl(n, "row", "rows")} to the invoice", "$n کرښې بل ته ورزیاتې کړئ")
  fun confirmedTotal(sum: Any): String = t("جمعِ ردیف‌های تأییدشده: ${asIs(sum)}", "Confirmed rows total: $sum", "د تایید شویو کرښو ټول: $sum")
  fun printedTotal(sum: Any): String = t("جمعِ چاپیِ فاکتور: ${asIs(sum)}", "Printed invoice total: $sum", "د بل چاپي ټول: $sum")
  fun dropped(n: Any): String = t(
    "${asIs(n)} ردیفِ دیگر جا نشد؛ آن‌ها را دستی بنویسید.",
    "$n more ${pl(n, "row", "rows")} did not fit; type them by hand.",
    "$n نورې کرښې ځای نه شوې؛ هغه په لاس ولیکئ.",
  )
  fun added(n: Any): String = t(
    "${asIs(n)} ردیف به فاکتور افزوده شد؛ پیش از ثبت دوباره ببینید.",
    "$n ${pl(n, "row was", "rows were")} added to the invoice; check again before saving.",
    "$n کرښې بل ته ورزیاتې شوې؛ له ثبت مخکې یې بیا وګورئ.",
  )
  fun notAdded(n: Any): String = t(
    "فاکتور به سقفِ ردیف رسید؛ ${asIs(n)} ردیف افزوده نشد.",
    "The invoice reached its row limit; $n ${pl(n, "row was", "rows were")} not added.",
    "بل د کرښو حد ته ورسېد؛ $n کرښې ورزیاتې نه شوې.",
  )
  val again: String get() = t("عکسِ دیگر", "Another photo", "بل انځور")
  val close: String get() = t("بستن", "Close", "بندول")
}
