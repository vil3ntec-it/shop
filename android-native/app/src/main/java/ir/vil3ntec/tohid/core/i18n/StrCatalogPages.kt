package ir.vil3ntec.tohid.core.i18n

/**
 *  کارِ گروهی روی کالا در صفحه‌های قدیمیِ `ui/screens` که نسخهٔ ۳ صدا می‌زند: ثبت و ورودِ چندتایی و شمارش (`BulkSheets.kt`)، قیمتِ
 *  گروهی (`BulkPriceDialog.kt`)، فعال/غیرفعال و حذفِ گروهی (`ProductListModel.kt`)، ورودِ کالا از فایل (`ProductImportDialog.kt`) و
 *  سررسید و برگشت به تأمین‌کننده (`SupplierExtras.kt`). این نوشته‌ها پیش‌تر متنِ خامِ فارسی بودند و از هیچ زبانی نمی‌گذشتند (LOC-08
 *  دری، دورِ ۴). فارسی کلمه‌به‌کلمه همان پیشین است؛ هر جا همان جمله در جدولِ دیگری بود، همان دوباره به کار رفته. واحدِ پیش‌فرضِ کالای
 *  تازه، دلیلِ حذف در سابقه و نامِ پروندهٔ خروجی داده‌اند و در صفحه «// داده:» دارند. قاعده‌ها سرِ `I18n.kt`.
 */
@Suppress("unused")
object StrCatalogPages {
  // ── `BulkSheets.kt` ──

  // ثبتِ چند کالا
  val newProductTitle: String get() = t("محصول جدید", "New product", "نوی توکی")
  val newProductsSub: String get() = t(
    "چند کالا را با هم ثبت کنید — هر ردیف یک کالا",
    "Record several items at once — one item per row",
    "څو توکي یوځای ثبت کړئ — هر قطار یو توکی",
  )
  val readyItems: String get() = t("کالای آماده", "Ready items", "چمتو توکي")
  val allRows: String get() = t("کل ردیف‌ها", "All rows", "ټول قطارونه")
  val productName: String get() = t("نام محصول", "Product name", "د توکي نوم")
  val newUnit: String get() = t("واحد تازه", "New unit", "نوی واحد")
  fun eachUnit(u: Any?): String = t(" — هر ${asIs(u)}", " — per $u", " — هر $u")
  fun profitPer(u: Any?, m: Any?): String = t(" · سود هر ${asIs(u)}: ${asIs(m)} ؋", " · profit per $u: $m ؋", " · د هر $u ګټه: $m ؋")
  val scanProductBarcode: String get() = t("اسکن بارکد کالا", "Scan product barcode", "د توکي بارکوډ سکن کړئ")

  // ورودِ چند قلم به انبار
  val entrySub: String get() = t(
    "چند قلم را با هم وارد کنید — تاریخ برای همهٔ ردیف‌ها یکی است",
    "Enter several items at once — the date is the same for every row",
    "څو توکي یوځای داخل کړئ — نېټه د ټولو قطارونو لپاره یوه ده",
  )
  val readyRows: String get() = t("ردیف آماده", "Ready rows", "چمتو قطارونه")
  val totalQty: String get() = t("مجموع مقدار", "Total quantity", "ټول مقدار")
  val totalValue: String get() = t("ارزش کل", "Total value", "ټول ارزښت")
  val dateAllRows: String get() = t("تاریخ همهٔ ردیف‌ها", "Date for all rows", "د ټولو قطارونو نېټه")
  val plusNewProduct: String get() = t("+ محصول جدید", "+ New product", "+ نوی توکی")
  val newProductName: String get() = t("نام محصول جدید", "New product name", "د نوي توکي نوم")
  val purchasePriceAf: String get() = t("قیمت خرید (افغانی)", "Purchase price (afghani)", "د پېر بیه (افغانۍ)")
  fun valueOf(m: Any?): String = t(" · ارزش ${asIs(m)} ؋", " · value $m ؋", " · ارزښت $m ؋")
  val saveAll: String get() = t("ذخیره همه", "Save all", "ټول خوندي کړئ")

  // شمارش
  val diffValue: String get() = t("ارزش اختلاف", "Difference value", "د توپیر ارزښت")
  val saveDiffsAll: String get() = t("ثبت اختلاف‌ها", "Save differences", "توپیرونه ثبت کړئ")

  // ── `BulkPriceDialog.kt` ──

  // قیمتِ گروهی
  fun priceOfN(n: Any?): String = t("قیمتِ ${asIs(n)} کالا", "Price of $n ${pl(n ?: 0, "item", "items")}", "د $n توکو بیه")
  val addOrSubtract: String get() = t("افزودن/کاستن", "Add/subtract", "زیاتول/کمول")
  val allOneAmount: String get() = t("همه یک مبلغ", "All one amount", "ټول یو مبلغ")
  val pctField: String get() = t("درصد (مثلاً ۱۰ یا -۵)", "Percent (e.g. 10 or -5)", "سلنه (لکه ۱۰ یا -۵)")
  val afField: String get() = t("افغانی (منفی یعنی کم)", "Afghani (negative means less)", "افغانۍ (منفي یعنې کم)")
  val roundNote: String get() = t(
    "گرد به یک افغانی. ردیفِ منفی اعمال نمی‌شود.",
    "Rounded to one afghani. A row that goes negative isn't applied.",
    "یوې افغانۍ ته ګردېږي. منفي قطار نه پلي کېږي.",
  )
  fun changesN(n: Any?): String = t("${asIs(n)} عوض می‌شود", "$n will change", "$n بدلېږي")
  fun warningsN(n: Any?): String = t("${asIs(n)} هشدار", "$n ${pl(n ?: 0, "warning", "warnings")}", "$n خبرداری")
  fun rejectedN(n: Any?): String = t("${asIs(n)} رد", "$n rejected", "$n رد")
  val sawWarnings: String get() = t(
    "هشدارها را دیدم؛ همین قیمت‌ها را بگذار",
    "I've seen the warnings; set these prices",
    "خبرداری مې ولید؛ همدا بیې کېږدئ",
  )
  val reasonRequired: String get() = t(
    "دلیل (اجباری؛ با نامِ شما در سابقه می‌ماند)",
    "Reason (required; stays in the history with your name)",
    "لامل (اړین؛ ستاسو له نوم سره په تاریخچه کې پاتې کېږي)",
  )
  val warnNegative: String get() = t("منفی می‌شود — اعمال نمی‌شود", "Goes negative — not applied", "منفي کېږي — نه پلي کېږي")
  val warnZero: String get() = t("صفر می‌شود", "Becomes zero", "صفر کېږي")
  val warnBelowCost: String get() = t("زیرِ بهای خرید", "Below purchase cost", "د پېر له بیې ټیټ")
  val warnBigChange: String get() = t("تغییرِ ۵۰٪ یا بیشتر", "A change of 50% or more", "۵۰٪ یا ډېر بدلون")

  // ── `ProductListModel.kt` ──

  // کارِ گروهی روی فهرست
  val alreadyActive: String get() = t("از قبل فعال است", "Already active", "دمخه فعال دی")
  val alreadyInactive: String get() = t("از قبل غیرفعال است", "Already inactive", "دمخه غیرفعال دی")
  val willActivate: String get() = t("فعال می‌شود", "Will be activated", "فعالېږي")
  val willDeactivate: String get() = t(
    "غیرفعال می‌شود — در فروش دیده نمی‌شود",
    "Will be deactivated — not shown in sales",
    "غیرفعالېږي — په پلور کې نه ښکاري",
  )
  val activated: String get() = t("فعال شد", "Activated", "فعال شو")
  val deactivated: String get() = t("غیرفعال شد", "Deactivated", "غیرفعال شو")
  fun unchangedNote(note: Any?): String = t("بی‌تغییر — ${asIs(note)}", "Unchanged — $note", "بې بدلونه — $note")
  val hadSales: String get() = t(
    "فروش داشته — فاکتورهای قبلی می‌مانند و نامش «حذف‌شده» نشان داده می‌شود",
    "Has sales — earlier invoices stay and its name shows as “deleted”",
    "پلور یې درلود — پخواني بلونه پاتې کېږي او نوم یې «ړنګ شوی» ښکاري",
  )
  val deletesWithEntries: String get() = t(
    "کالا و سوابقِ ورودِ انبارش حذف می‌شود",
    "The item and its stock-entry records are deleted",
    "توکی او د ګدام د ننوتو سوابق یې ړنګېږي",
  )

  // ── `ProductImportDialog.kt` ──

  // ورودِ کالا از فایل
  fun alreadyImported(date: Any?): String = t(
    "همین داده ${asIs(date)} وارد شده بود — آنچه ثبت شده دوباره ثبت نمی‌شود.",
    "This same data was imported on $date — what's recorded isn't recorded again.",
    "همدا معلومات په $date راوړل شوي وو — څه چې ثبت شوي بیا نه ثبتېږي.",
  )
  fun freshN(n: Any?): String = t("${asIs(n)} تازه", "$n new", "$n نوي")
  fun updatedN(n: Any?): String = t("${asIs(n)} به‌روز", "$n updated", "$n تازه شوي")
  fun keptN(n: Any?): String = t("${asIs(n)} در دکان، دست نمی‌خورد", "$n in the shop, left untouched", "$n په دوکان کې، لاس نه ورته وړل کېږي")
  fun sameN(n: Any?): String = t("${asIs(n)} بی تغییر", "$n unchanged", "$n بې بدلونه")
  fun ignoredCols(c: Any?): String = t("ستون‌های نادیده: ${asIs(c)}", "Ignored columns: $c", "پرېښودل شوې ستنې: $c")
  val alsoUpdate: String get() = t("کالاهای موجود را هم به‌روز کن", "Also update existing items", "موجود توکي هم تازه کړئ")
  val updateNote1: String get() = t(
    "فقط قیمت، دسته، حداقلِ موجودی و بارکدِ تازه؛ خانهٔ خالی، واحد و نام دست نمی‌خورند و چیزی پاک نمی‌شود. ",
    "Only price, category, minimum stock and new barcode; empty cells, unit and name aren't touched and nothing is deleted. ",
    "یوازې بیه، ډله، لږ تر لږه موجودي او نوی بارکوډ؛ تشو خونو، واحد او نوم ته لاس نه وړل کېږي او څه نه پاکېږي. ",
  )
  val updateNote2: String get() = t(
    "هر تغییرِ قیمت با نامِ شما و نامِ فایل در سابقه می‌ماند.",
    "Every price change stays in the history with your name and the file name.",
    "هر د بیې بدلون ستاسو او د فایل له نوم سره په تاریخچه کې پاتې کېږي.",
  )
  val existingChanges: String get() = t("تغییرهای کالاهای موجود:", "Changes to existing items:", "د موجودو توکو بدلونونه:")
  val conflictKept: String get() = t(
    "در دکان هست و فایل چیزِ دیگری می‌گوید — دست نمی‌خورد:",
    "In the shop and the file says something else — left untouched:",
    "په دوکان کې شته او فایل بل څه وايي — لاس نه ورته وړل کېږي:",
  )
  fun rowChanges(n: Any?, name: Any?, what: Any?): String = t(
    "ردیف ${asIs(n)} «${asIs(name)}»: ${asIs(what)}",
    "Row $n “$name”: $what",
    "$n قطار «$name»: $what",
  )
  val sawChanges: String get() = t("تغییرها را دیدم؛ همین‌ها را بگذار", "I've seen the changes; apply these", "بدلونونه مې ولیدل؛ همدا کېږدئ")
  fun rowsNotSaved(n: Any?): String = t(
    "${asIs(n)} ردیف ثبت نمی‌شود:",
    "$n ${pl(n ?: 0, "row isn't", "rows aren't")} saved:",
    "$n قطارونه نه ثبتېږي:",
  )
  fun rowReason(n: Any?, name: Any?, why: Any?): String = t("ردیف ${asIs(n)}${asIs(name)}: ${asIs(why)}", "Row $n$name: $why", "$n قطار$name: $why")
  val rejectedFile: String get() = t(
    "دریافتِ فایلِ ردیف‌های رد (برای اصلاح و ورودِ دوباره)",
    "Download the rejected rows file (to fix and import again)",
    "د رد شویو قطارونو فایل ترلاسه کړئ (د سمولو او بیا راوړلو لپاره)",
  )
  fun similarRows(n: Any?): String = t(
    "${asIs(n)} ردیف ثبت می‌شود ولی نامش مشابه است — اگر همان کالاست، از فایل بردارید:",
    "$n ${pl(n ?: 0, "row is", "rows are")} saved but the name is similar — if it's the same item, remove it from the file:",
    "$n قطارونه ثبتېږي خو نوم یې ورته دی — که همغه توکی وي، له فایل څخه یې لرې کړئ:",
  )
  fun similarRow(n: Any?, name: Any?, other: Any?, inFile: Any?): String = t(
    "ردیف ${asIs(n)} «${asIs(name)}» ≈ «${asIs(other)}»${asIs(inFile)}",
    "Row $n “$name” ≈ “$other”$inFile",
    "$n قطار «$name» ≈ «$other»$inFile",
  )
  val inThisFile: String get() = t(" (در همین فایل)", " (in this same file)", " (په همدې فایل کې)")
  fun saveAndUpdate(n: Any?, u: Any?): String = t(
    "ثبت ${asIs(n)} کالا و به‌روزرسانیِ ${asIs(u)}",
    "Save $n ${pl(n ?: 0, "item", "items")} and update $u",
    "$n توکي ثبتول او $u تازه کول",
  )
  fun updateN(n: Any?): String = t("به‌روزرسانیِ ${asIs(n)} کالا", "Update $n ${pl(n ?: 0, "item", "items")}", "$n توکي تازه کول")
  val noImportYet: String get() = t(
    "هنوز کالایی از فایل وارد نشده",
    "No item has been imported from a file yet",
    "لا کوم توکی له فایل څخه نه دی راوړل شوی",
  )

  // ── `SupplierExtras.kt` ──

  // سررسید و برگشت به تأمین‌کننده
  val supplierDue: String get() = t("سررسیدِ پرداخت به تأمین‌کننده", "Payments due to suppliers", "عرضه‌کوونکو ته د ورکړې نېټې")
  fun lateN(n: Any?, m: Any?): String = t("${asIs(n)} گذشته — ${asIs(m)} ؋", "$n overdue — $m ؋", "$n تېرې — $m ؋")
  fun soonN(n: Any?): String = t("${asIs(n)} تا هفتهٔ بعد", "$n by next week", "$n تر راتلونکې اونۍ")
  fun dueRow(who: Any?, doc: Any?, date: Any?): String = t(
    "${asIs(who)} · ${asIs(doc)} · سررسید ${asIs(date)}",
    "$who · $doc · due $date",
    "$who · $doc · نېټه $date",
  )
  fun invoiceNo(n: Any?): String = t("فاکتورِ #${asIs(n)}", "Invoice #$n", "بل #$n")
  val purchaseWord: String get() = t("خرید", "Purchase", "پېرود")
  fun returnNote(name: Any?): String = t(
    "${asIs(name)} — کالا از انبار بیرون می‌رود و سندِ کسر از بدهیِ ما به او کم می‌کند. صندوق دست نمی‌خورد.",
    "$name — the goods leave the stock and the credit note reduces what we owe them. The till isn't touched.",
    "$name — توکي له ګدام وځي او د کمښت سند زموږ پور ورته کموي. دخل ته لاس نه وړل کېږي.",
  )
  val noPurchasesFrom: String get() = t(
    "از این تأمین‌کننده خریدی ثبت نشده است",
    "No purchase from this supplier has been recorded",
    "له دې عرضه‌کوونکي څخه هېڅ پېرود نه دی ثبت شوی",
  )
  fun stockReturnable(q: Any?, u: Any?, r: Any?): String = t(
    "موجودی: ${asIs(q)} ${asIs(u)} · قابلِ برگشت به همین تأمین‌کننده: ${asIs(r)}",
    "Stock: $q $u · returnable to this supplier: $r",
    "موجودي: $q $u · دې عرضه‌کوونکي ته د بېرته ورکولو وړ: $r",
  )
  val unitPrice: String get() = t("قیمتِ هر واحد", "Price per unit", "د هر واحد بیه")
  fun creditNote(m: Any?): String = t("سندِ کسر: ${asIs(m)} ؋", "Credit note: $m ؋", "د کمښت سند: $m ؋")
  val returnReason: String get() = t("دلیل (الزامی) — مثلاً خراب رسید", "Reason (required) — e.g. arrived damaged", "لامل (اړین) — لکه خراب رسېدلی")
}
