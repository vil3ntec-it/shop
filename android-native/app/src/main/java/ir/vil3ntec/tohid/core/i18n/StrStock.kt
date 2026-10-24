package ir.vil3ntec.tohid.core.i18n

/**
 *  نوشته‌های گروهِ G3 انبار (stock.md) — صاحبش همان گروه است.
 *  هر ویژگی سه‌زبانه: `val x: String get() = t("…", "…", "…")`؛ قاعده‌ها سرِ `I18n.kt`.
 *  عددها از بیرون قالب‌بندی‌شده می‌آیند (`money`، `qty`)؛ این‌جا فقط جمله است.
 */
@Suppress("unused")
object StrStock {

  // فهرستِ انبار
  val searchHint: String get() = t("جستجوی کالا یا بارکد…", "Search items or barcodes…", "توکي یا بارکوډ ولټوئ…")
  val menuCount: String get() = t("انبارگردانی", "Stock count", "د ګدام شمېرنه")
  val menuImport: String get() = t("ورود از فایل اکسل (CSV)", "Import from Excel (CSV)", "له اکسل (CSV) څخه راوړل")
  val menuBulk: String get() = t("ورودِ چند قلم با هم", "Receive several items", "څو توکي یوځای راوړل")
  val statItems: String get() = t("قلم کالا", "Items", "توکي")
  val statCartons: String get() = t("کارتن", "Cartons", "کارټنونه")
  val statUnits: String get() = t("واحد", "Units", "واحدونه")
  val statValue: String get() = t("ارزشِ تقریبی", "Approx. value", "نږدې ارزښت")
  fun alertOut(n: Any): String = t("$n تمام‌شده", "$n out of stock", "$n خلاص شوي")
  fun alertLow(n: Any): String = t("$n کم", "$n low", "$n کم")
  val filterAll: String get() = t("همه", "All", "ټول")
  val filterLow: String get() = t("کم", "Low", "کم")
  val filterOut: String get() = t("تمام‌شده", "Out of stock", "خلاص شوي")
  val filterExpiring: String get() = t("نزدیک به انقضا", "Expiring soon", "د پای ته نږدې")
  val filterExpired: String get() = t("منقضی", "Expired", "تېر شوي")
  fun cartons(n: Any): String = t("$n کارتن", "$n ${pl(n, "carton", "cartons")}", "$n کارټنه")
  fun salePrice(a: Any): String = t("فروش $a ؋", "Sells at $a ؋", "پلور $a ؋")
  fun expiresIn(days: Any): String = t("$days روز تا انقضا", "Expires in $days ${pl(days, "day", "days")}", "$days ورځې پاتې")
  val expiresToday: String get() = t("امروز منقضی می‌شود", "Expires today", "نن پای ته رسي")
  val pillLow: String get() = t("کم", "Low", "کم")
  val pillOut: String get() = t("تمام‌شده", "Out", "خلاص")
  val pillExpired: String get() = t("منقضی", "Expired", "تېر شوی")
  val pillExpiring: String get() = t("نزدیکِ انقضا", "Expiring", "پای ته نږدې")
  val swipeEntry: String get() = t("ورود", "Receive", "راوړل")
  val swipeAdjust: String get() = t("اصلاح", "Adjust", "سمول")
  val fabEntry: String get() = t("ورود کالا", "Receive stock", "توکي راوړل")
  val emptyTitle: String get() = t("انبار خالی است", "The warehouse is empty", "ګدام تش دی")
  val emptyBody: String get() = t(
    "کالاهای دکان را ثبت کنید تا موجودی، ارزش انبار و هشدار کم‌بودن کار کند.",
    "Add your shop's items so stock levels, stock value and low-stock alerts can work.",
    "د دوکان توکي ثبت کړئ چې موجودي، د ګدام ارزښت او د کمښت خبرتیا کار وکړي.",
  )
  val emptyAction: String get() = t("ثبت کالای تازه", "Add a new item", "نوی توکی ثبتول")
  val noMatchBody: String get() = t("کالایی مطابق این جستجو یا فیلتر نیست.", "No item matches this search or filter.", "له دې لټون یا چاڼ سره هېڅ توکی سمون نه خوري.")
  val clearFilters: String get() = t("پاک کردنِ فیلترها", "Clear filters", "چاڼونه پاکول")
  val pickOne: String get() = t("یک کالا را از فهرست انتخاب کنید", "Pick an item from the list", "له لېست څخه یو توکی وټاکئ")

  // صفحهٔ یک کالا
  val edit: String get() = t("ویرایش", "Edit", "سمول")
  val viewProduct: String get() = t("دیدنِ کالا", "View item", "توکی کتل")
  val deleteProduct: String get() = t("حذف محصول", "Delete item", "توکی ړنګول")
  val deleteProductTitle: String get() = t("حذف محصول؟", "Delete this item?", "دا توکی ړنګ شي؟")
  val delete: String get() = t("حذف", "Delete", "ړنګول")
  val deleted: String get() = t("حذف شد", "Deleted", "ړنګ شو")
  val stock: String get() = t("موجودی", "In stock", "موجودي")
  val purchasePrice: String get() = t("قیمت خرید", "Purchase price", "د پېر بیه")
  val salePriceLabel: String get() = t("قیمت فروش", "Sale price", "د پلور بیه")
  val minStock: String get() = t("حد کم بودن", "Low-stock level", "د کمښت کچه")
  val barcode: String get() = t("بارکد", "Barcode", "بارکوډ")
  val noBarcode: String get() = t("ندارد", "None", "نشته")
  val copyBarcode: String get() = t("کپیِ بارکد", "Copy barcode", "بارکوډ کاپي کول")
  val barcodeCopied: String get() = t("بارکد کپی شد", "Barcode copied", "بارکوډ کاپي شو")
  val entryAction: String get() = t("ورود کالا", "Receive stock", "توکي راوړل")
  val adjustAction: String get() = t("اصلاح موجودی", "Adjust stock", "موجودي سمول")
  val entriesTitle: String get() = t("ورودهای این کالا", "Deliveries of this item", "د دې توکي راوړنې")
  fun olderEntries(n: Any): String = t("و $n ورودِ قدیمی‌تر", "and $n older ${pl(n, "delivery", "deliveries")}", "او $n زړې راوړنې")
  val showFewer: String get() = t("نمایشِ کمتر", "Show fewer", "لږ ښودل")
  fun entryCartons(c: Any, per: Any): String = t("$c کارتن × $per", "$c ${pl(c, "carton", "cartons")} × $per", "$c کارټنه × $per")
  fun expiryOn(date: Any): String = t("انقضا $date", "Expires $date", "پای $date")
  fun batchOf(b: Any): String = t("سری $b", "Batch $b", "لړۍ $b")
  fun lotLeft(q: Any): String = t("مانده $q", "$q left", "$q پاتې")
  val adjustmentEntry: String get() = t("اصلاح موجودی", "Stock adjustment", "د موجودي سمون")
  val noEntries: String get() = t("هنوز ورودی برای این کالا ثبت نشده", "No deliveries recorded for this item yet", "د دې توکي لپاره تر اوسه راوړنه نه ده ثبت شوې")
  val movementsRow: String get() = t("حرکات انبار", "Stock movements", "د ګدام خوځښتونه")
  val deleteEntryTitle: String get() = t("حذف این ورودی انبار؟", "Delete this delivery?", "دا راوړنه ړنګه شي؟")
  val productMissing: String get() = t("این کالا پیدا نشد", "This item wasn't found", "دا توکی ونه موندل شو")
  val productMissingBody: String get() = t("شاید حذف شده باشد.", "It may have been deleted.", "کېدای شي ړنګ شوی وي.")

  // ورودِ یک کالا
  val entryTitle: String get() = t("ورود کالا به انبار", "Receive stock", "ګدام ته توکي راوړل")
  val productField: String get() = t("کالا", "Item", "توکی")
  val pickProduct: String get() = t("انتخابِ کالا", "Choose an item", "توکی وټاکئ")
  val newProduct: String get() = t("محصولِ تازه", "New item", "نوی توکی")
  val newProductHint: String get() = t("کالایی که هنوز در دکان نیست", "An item that isn't in the shop yet", "هغه توکی چې لا په دوکان کې نشته")
  fun inStock(q: Any): String = t("موجودی $q", "$q in stock", "موجودي $q")
  fun unknownBarcode(code: Any): String = t("بارکدِ $code در دکان نبود", "Barcode $code isn't in the shop", "بارکوډ $code په دوکان کې نه و")
  val quantity: String get() = t("مقدار", "Quantity", "اندازه")
  val modeCartons: String get() = t("کارتن", "Cartons", "کارټن")
  val modeUnits: String get() = t("واحد", "Units", "واحد")
  val cartonCount: String get() = t("تعداد کارتن", "Cartons", "د کارټنونو شمېر")
  val perCarton: String get() = t("در هر کارتن", "Per carton", "په هر کارټن کې")
  val unitCount: String get() = t("تعداد واحد", "Units", "د واحدونو شمېر")
  fun equalsUnits(q: Any): String = t("= $q", "= $q", "= $q")
  val priceField: String get() = t("قیمت خرید (هر واحد)", "Purchase price (per unit)", "د پېر بیه (هر واحد)")
  fun total(a: Any): String = t("جمع: $a ؋", "Total: $a ؋", "ټول: $a ؋")
  val details: String get() = t("جزئیاتِ محموله (اختیاری)", "Batch details (optional)", "د بار جزئیات (اختیاري)")
  val expiry: String get() = t("تاریخ انقضا", "Expiry date", "د پای نېټه")
  val batch: String get() = t("سری ساخت", "Batch number", "د جوړښت لړۍ")
  val made: String get() = t("تاریخ تولید", "Production date", "د تولید نېټه")
  val notes: String get() = t("توضیحات", "Notes", "یادښتونه")
  val unitField: String get() = t("واحد اندازه‌گیری", "Unit of measure", "د اندازې واحد")
  val datePlaceholder: String get() = t("1406-01-30", "1406-01-30", "1406-01-30")
  val dateHelper: String get() = t("خورشیدی یا میلادی", "Solar Hijri or Gregorian", "لمریز یا میلادي")
  val submitEntry: String get() = t("ثبت ورود", "Save delivery", "راوړنه ثبتول")
  val entrySaved: String get() = t("ورود کالا ثبت شد", "Delivery saved", "راوړنه ثبت شوه")
  val errPickProduct: String get() = t("ابتدا یک محصول انتخاب یا اضافه کنید", "Choose or add an item first", "لومړی یو توکی وټاکئ یا زیات کړئ")
  val errUnits: String get() = t("تعداد واحد معتبر وارد کنید", "Enter a valid quantity", "سم شمېر ولیکئ")
  val errPrice: String get() = t("قیمت خرید معتبر وارد کنید", "Enter a valid purchase price", "سمه د پېر بیه ولیکئ")
  val errExpiry: String get() = t("تاریخِ انقضا درست نیست", "The expiry date isn't valid", "د پای نېټه سمه نه ده")
  val errMade: String get() = t("تاریخِ تولید درست نیست", "The production date isn't valid", "د تولید نېټه سمه نه ده")

  // ورودِ چند قلم
  val bulkTitle: String get() = t("ورودِ چند قلم", "Receive several items", "څو توکي راوړل")
  val bulkDate: String get() = t("تاریخِ همهٔ ردیف‌ها", "Date for all rows", "د ټولو کرښو نېټه")
  fun rowN(n: Any): String = t("ردیف $n", "Row $n", "کرښه $n")
  val duplicateRow: String get() = t("تکثیر", "Duplicate", "دوه ځله کول")
  val removeRow: String get() = t("حذفِ ردیف", "Remove row", "کرښه لرې کول")
  val newProductName: String get() = t("نامِ محصولِ تازه", "New item name", "د نوي توکي نوم")
  val newProductNameHint: String get() = t("مثلاً برنج", "e.g. Rice", "لکه وریجې")
  val category: String get() = t("دسته‌بندی", "Category", "ډله")
  val unit: String get() = t("واحد", "Unit", "واحد")
  fun rowValue(a: Any): String = t("ارزش $a ؋", "Value $a ؋", "ارزښت $a ؋")
  val addRow: String get() = t("افزودنِ ردیف", "Add row", "کرښه زیاتول")
  fun bulkSummary(ready: Any, amount: Any, value: Any): String = t(
    "ردیفِ آماده $ready · مجموعِ مقدار $amount · ارزشِ کل $value ؋",
    "$ready ready · total quantity $amount · total value $value ؋",
    "چمتو کرښې $ready · ټوله اندازه $amount · ټول ارزښت $value ؋",
  )
  val saveAll: String get() = t("ذخیرهٔ همه", "Save all", "ټول خوندي کول")
  fun errRow(n: Any): String = t(
    "ردیف $n کامل نیست — محصول، مقدار و قیمت خرید لازم است",
    "Row $n isn't complete — item, quantity and purchase price are required",
    "کرښه $n بشپړه نه ده — توکی، اندازه او د پېر بیه اړین دي",
  )
  val errNoRows: String get() = t("هیچ ردیف کاملی برای ثبت نیست", "There's no complete row to save", "د ثبت لپاره هېڅ بشپړه کرښه نشته")
  fun rowsSaved(n: Any): String = t("$n ردیف ثبت شد", "$n ${pl(n, "row", "rows")} saved", "$n کرښې ثبت شوې")
  val scanMiss: String get() = t("این بارکد در دکان نبود — نامش را بنویسید تا ساخته شود", "This barcode isn't in the shop — type a name to create it", "دا بارکوډ په دوکان کې نه و — نوم یې ولیکئ چې جوړ شي")

  // اصلاحِ موجودی
  fun adjustTitle(name: Any): String = t("اصلاح موجودی — $name", "Adjust stock — $name", "د موجودي سمون — $name")
  val kindUp: String get() = t("زیاد شد", "Went up", "زیات شو")
  val kindDown: String get() = t("کم شد", "Went down", "کم شو")
  /** برچسبِ کوتاهِ همان گونه در بخش‌بندیِ سه‌تایی؛ عنوانِ برگه نامِ کاملش را می‌گوید */
  val kindReturnShort: String get() = t("برگشت", "Return", "ستنول")
  fun returnTitle(name: Any): String = t("برگشت به تأمین‌کننده — $name", "Return to supplier — $name", "عرضه کوونکي ته ستنول — $name")
  val kindReturn: String get() = t("برگشت به تأمین‌کننده", "Return to supplier", "عرضه کوونکي ته ستنول")
  val reason: String get() = t("دلیل", "Reason", "لامل")
  val reasonHelper: String get() = t("بدون دلیل، فردا معلوم نمی‌شود جنس کجا رفته.", "Without a reason, nobody will know tomorrow where the goods went.", "بې لامله، سبا به معلومه نه وي چې توکي چېرته تللي.")
  fun preview(from: Any, to: Any, unit: Any): String = t("$from ← $to $unit", "$from → $to $unit", "$from ← $to $unit")
  val submit: String get() = t("ثبت", "Save", "ثبتول")
  val errAmount: String get() = t("مقدار معتبر وارد کنید", "Enter a valid quantity", "سمه اندازه ولیکئ")
  val errReason: String get() = t("دلیل اصلاح را بنویسید", "Write the reason for the adjustment", "د سمون لامل ولیکئ")
  val adjusted: String get() = t("اصلاح موجودی ثبت شد", "Stock adjustment saved", "د موجودي سمون ثبت شو")
  val returned: String get() = t("برگشت به تأمین‌کننده ثبت شد", "Return to supplier saved", "عرضه کوونکي ته ستنېدنه ثبت شوه")

  // انبارگردانی
  val countHelper: String get() = t("آنچه در قفسه هست را بنویسید؛ خانهٔ خالی یعنی شمرده نشد", "Type what's on the shelf; an empty box means not counted", "څه چې په الماری کې دي ولیکئ؛ تش خانه یعني نه دي شمېرل شوي")
  val countNote: String get() = t("یادداشت (مثلاً: قفسهٔ برنج)", "Note (e.g. rice shelf)", "یادښت (لکه د وریجو الماری)")
  fun systemQty(q: Any): String = t("برنامه: $q", "App: $q", "اپ: $q")
  val counted: String get() = t("شمرده", "Counted", "شمېرل شوی")
  val countField: String get() = t("شمرده", "Count", "شمېر")
  fun countSummary(counted: Any, diffs: Any, value: Any): String = t(
    "شمرده $counted · اختلاف $diffs · ارزشِ اختلاف $value ؋",
    "Counted $counted · differences $diffs · difference value $value ؋",
    "شمېرل شوي $counted · توپیرونه $diffs · د توپیر ارزښت $value ؋",
  )
  val saveDiffs: String get() = t("ثبتِ اختلاف‌ها", "Save differences", "توپیرونه ثبتول")
  val confirmTitle: String get() = t("این اختلاف‌ها ثبت شوند؟", "Save these differences?", "دا توپیرونه ثبت شي؟")
  val confirmBody: String get() = t("هر اختلاف یک «اصلاح موجودی» با دلیلِ «انبارگردانی» می‌شود.", "Each difference becomes a stock adjustment with the reason “stock count”.", "هر توپیر د «ګدام شمېرنې» په لامل یو «د موجودي سمون» کېږي.")
  fun badNumber(name: Any): String = t("عددِ «$name» درست نیست", "The number for “$name” isn't valid", "د «$name» شمېر سم نه دی")
  fun countSaved(n: Any): String = t("انبارگردانی ثبت شد — $n اختلاف اصلاح شد", "Stock count saved — $n ${pl(n, "difference", "differences")} fixed", "د ګدام شمېرنه ثبت شوه — $n توپیرونه سم شول")
  val exportCsv: String get() = t("خروجی CSV", "Export CSV", "CSV ایستل")
  val fileSaved: String get() = t("فایل ذخیره شد", "File saved", "دوتنه خوندي شوه")
  val fileFailed: String get() = t("فایل ساخته نشد", "The file couldn't be created", "دوتنه جوړه نه شوه")

  // حرکاتِ انبار
  fun movementsTitle(name: Any): String = t("حرکات انبار — $name", "Stock movements — $name", "د ګدام خوځښتونه — $name")
  val movePurchase: String get() = t("ورود خرید", "Purchase in", "د پېر راوړنه")
  val moveSale: String get() = t("فروش", "Sale", "پلور")
  val moveCustomerReturn: String get() = t("مرجوعی مشتری", "Customer return", "د پېرودونکي ستنول")
  val moveSaleCancel: String get() = t("لغو فروش", "Sale cancelled", "پلور لغوه شو")
  val moveSupplierReturn: String get() = t("برگشت به تأمین‌کننده", "Return to supplier", "عرضه کوونکي ته ستنول")
  val moveAdjustment: String get() = t("اصلاح موجودی", "Stock adjustment", "د موجودي سمون")
  val noMovements: String get() = t("هنوز حرکتی برای این کالا ثبت نشده", "No movements recorded for this item yet", "د دې توکي لپاره تر اوسه خوځښت نه دی ثبت شوی")

  // ورود از فایل
  val importTitle: String get() = t("ورود کالا از فایل", "Import items", "له دوتنې توکي راوړل")
  val pickFile: String get() = t("انتخابِ فایل", "Choose a file", "دوتنه وټاکئ")
  val pickOtherFile: String get() = t("انتخابِ فایلِ دیگر", "Choose another file", "بله دوتنه وټاکئ")
  val importIntroTitle: String get() = t("یک فایلِ CSV انتخاب کنید", "Choose a CSV file", "یوه CSV دوتنه وټاکئ")
  val importIntroBody: String get() = t(
    "سطرِ اول نامِ ستون‌هاست و ستونِ «نام محصول» لازم است؛ خروجیِ گزارشِ محصولات بی‌دست‌کاری پذیرفته می‌شود.",
    "The first row holds column names and a “Product name” column is required; the products report export works as is.",
    "لومړۍ کرښه د ستنو نومونه لري او د «د توکي نوم» ستنه اړینه ده؛ د توکو راپور همداسې منل کېږي.",
  )
  val readFailed: String get() = t("فایل خوانده نشد", "The file couldn't be read", "دوتنه ونه لوستل شوه")
  val noNameColumn: String get() = t("ستونِ «نام محصول» در سطرِ اولِ فایل پیدا نشد", "No “Product name” column was found in the file's first row", "د دوتنې په لومړۍ کرښه کې د «د توکي نوم» ستنه ونه موندل شوه")
  fun importReady(n: Any): String = t("$n کالا آمادهٔ ثبت", "$n ${pl(n, "item", "items")} ready to save", "$n توکي د ثبت لپاره چمتو")
  fun importSkipped(n: Any): String = t("$n ردیف ثبت نمی‌شود", "$n ${pl(n, "row", "rows")} won't be saved", "$n کرښې نه ثبتېږي")
  val skippedTitle: String get() = t("ردیف‌هایی که ثبت نمی‌شوند", "Rows that won't be saved", "هغه کرښې چې نه ثبتېږي")
  fun skippedRow(row: Any): String = t("ردیف $row", "Row $row", "کرښه $row")
  fun moreSkipped(n: Any): String = t("و $n ردیفِ دیگر", "and $n more ${pl(n, "row", "rows")}", "او $n نورې کرښې")
  fun moreDrafts(n: Any): String = t("و $n کالای دیگر", "and $n more ${pl(n, "item", "items")}", "او $n نور توکي")
  val previewTitle: String get() = t("کالاهای آمادهٔ ثبت", "Items ready to save", "د ثبت لپاره چمتو توکي")
  fun importSave(n: Any): String = t("ثبت $n کالا", "Save $n ${pl(n, "item", "items")}", "$n توکي ثبتول")
  fun imported(n: Any): String = t("$n کالا از فایل ثبت شد", "$n ${pl(n, "item", "items")} imported", "$n توکي له دوتنې ثبت شول")
}
