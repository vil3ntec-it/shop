package ir.vil3ntec.tohid.core.i18n

/**
 *  فهرستِ کالا در کامپیوتر (`ui/screens/DeskProducts.kt`): کارت‌های بالا، جست‌وجو و فیلتر، کارِ گروهی (فعال/غیرفعال، حذف، قیمت،
 *  حدِ موجودی)، ردیفِ «کالای تازه» و برگهٔ ویرایشِ کنارِ فهرست. این نوشته‌ها پیش‌تر متنِ خامِ فارسی بودند و از هیچ زبانی نمی‌گذشتند
 *  (LOC-08 دری، دورِ ۳). فارسی کلمه‌به‌کلمه همان پیشین است؛ هر جا همان جمله در جدولِ دیگری بود، همان دوباره به کار رفته. واحدِ
 *  پیش‌فرضِ کالای تازه («دانه») داده است و در صفحه «// داده:» دارد. قاعده‌ها سرِ `I18n.kt`.
 */
@Suppress("unused")
object StrProductPages {
  // ── `DeskProducts.kt` ──

  // سرِ صفحه و کارت‌ها
  val newBarcode: String get() = t(
    "بارکدِ تازه — نام و قیمت را بنویسید و Enter بزنید",
    "New barcode — type the name and price and press Enter",
    "نوی بارکوډ — نوم او بیه ولیکئ او Enter ووهئ",
  )
  val subtitle: String get() = t(
    "موجودی، قیمت و وضعیتِ کالاهای دکان — جست‌وجو، فیلتر و کارِ گروهی",
    "Stock, price and status of the shop's items — search, filter and bulk actions",
    "د دوکان د توکو موجودي، بیه او حالت — لټون، چاڼ او ډله‌ییز کار",
  )
  val stockIn: String get() = t("ورودِ انبار", "Stock in", "ګدام ته راتګ")
  val excelExport: String get() = t("خروجیِ اکسل", "Excel export", "د اکسل وتنه")
  val addItemKey: String get() = t("افزودن کالا  Ctrl+N", "Add item  Ctrl+N", "توکی زیاتول  Ctrl+N")
  fun newItems(n: Any?): String = t("${asIs(n)} کالای تازه", "$n new ${pl(n ?: 0, "item", "items")}", "$n نوي توکي")
  val noNewItems: String get() = t("کالای تازه‌ای نیامده", "No new items", "نوی توکی نه دی راغلی")
  val nowBelowAlert: String get() = t("اکنون — زیرِ حدِ هشدار", "Now — below the alert level", "اوس — د خبرتیا له حد لاندې")
  val nowActiveItems: String get() = t("اکنون — کالای فعال", "Now — active items", "اوس — فعال توکي")
  val searchHint: String get() = t(
    "جست‌وجو در نام، دسته یا بارکد (Ctrl+F)",
    "Search name, category or barcode (Ctrl+F)",
    "په نوم، ډله یا بارکوډ کې لټون (Ctrl+F)",
  )
  val noItemsYet: String get() = t("هنوز کالایی نیست", "No items yet", "لا توکی نشته")
  val noItemsHint: String get() = t(
    "در ردیفِ «کالای تازه» نام و قیمت را بنویسید و Enter بزنید، یا بارکد را بخوانید.",
    "In the “New item” row, type the name and price and press Enter, or scan a barcode.",
    "د «نوی توکی» په قطار کې نوم او بیه ولیکئ او Enter ووهئ، یا بارکوډ ولولئ.",
  )
  val addItem: String get() = t("افزودن کالا", "Add item", "توکی زیاتول")
  val noMatch: String get() = t(
    "کالایی با این جست‌وجو، دسته یا زبانه نیست.",
    "No item matches this search, category or tab.",
    "له دې لټون، ډلې یا ټب سره توکی نشته.",
  )
  val clearSearchFilter: String get() = t("پاک‌کردنِ جست‌وجو و فیلتر", "Clear search and filter", "لټون او چاڼ پاکول")

  // کارِ گروهی
  fun itemsSaved(n: Any?, path: Any?): String = t(
    "${asIs(n)} کالا ذخیره شد: ${asIs(path)}",
    "$n ${pl(n ?: 0, "item", "items")} saved: $path",
    "$n توکي خوندي شول: $path",
  )
  val stockLevels: String get() = t("حدِ موجودی", "Stock levels", "د موجودۍ حدونه")
  val keysHint: String get() = t(
    "ردیفِ «کالای تازه»: Tab خانهٔ بعد · Enter ثبت · کلیک روی کالا ⇐ ویرایش کنارِ فهرست · Ctrl+F جست‌وجو · Esc لغوِ انتخاب",
    "“New item” row: Tab next cell · Enter save · click an item ⇐ edit beside the list · Ctrl+F search · Esc clear selection",
    "د «نوی توکی» قطار: Tab بل خونه · Enter ثبت · پر توکي کلیک ⇐ د لړلیک تر څنګ سمون · Ctrl+F لټون · Esc د ټاکنې لغوه",
  )
  fun priceChanged(n: Any?): String = t(
    "قیمتِ ${asIs(n)} کالا عوض شد",
    "Price of $n ${pl(n ?: 0, "item", "items")} changed",
    "د $n توکو بیه بدله شوه",
  )
  fun hiddenSelected(n: Any?): String = t(
    "${asIs(n)} مورد از انتخاب‌ها در فیلترِ فعلی دیده نمی‌شوند ولی در این کار هستند.",
    "$n of the selected ${pl(n ?: 0, "item isn't", "items aren't")} visible in the current filter but ${pl(n ?: 0, "is", "are")} part of this action.",
    "$n ټاکل شوي توکي په اوسني چاڼ کې نه ښکاري خو په دې کار کې شامل دي.",
  )
  fun activateN(n: Any?): String = t("فعال‌کردنِ ${asIs(n)} کالا", "Activate $n ${pl(n ?: 0, "item", "items")}", "د $n توکو فعالول")
  fun deactivateN(n: Any?): String = t("غیرفعال‌کردنِ ${asIs(n)} کالا", "Deactivate $n ${pl(n ?: 0, "item", "items")}", "د $n توکو غیرفعالول")
  val activateIt: String get() = t("فعال کن", "Activate", "فعال یې کړئ")
  val deactivateIt: String get() = t("غیرفعال کن", "Deactivate", "غیرفعال یې کړئ")
  val activeNote: String get() = t(
    "کالای فعال در فروش و جست‌وجوی فروش دیده می‌شود. برگشت‌پذیر است.",
    "Active items are seen at sale and in sale search. Reversible.",
    "فعال توکي په پلور او د پلور په لټون کې ښکاري. بېرته کېدونکی دی.",
  )
  val inactiveNote: String get() = t(
    "کالای غیرفعال فروخته نمی‌شود ولی سابقه و موجودی‌اش می‌ماند. برگشت‌پذیر است.",
    "Inactive items aren't sold but their history and stock remain. Reversible.",
    "غیرفعال توکي نه پلورل کېږي خو تاریخچه او موجودي یې پاتې کېږي. بېرته کېدونکی دی.",
  )
  val activateResult: String get() = t("نتیجهٔ فعال‌کردن", "Activation result", "د فعالولو پایله")
  val deactivateResult: String get() = t("نتیجهٔ غیرفعال‌کردن", "Deactivation result", "د غیرفعالولو پایله")
  fun deleteN(n: Any?): String = t("حذفِ ${asIs(n)} کالا", "Delete $n ${pl(n ?: 0, "item", "items")}", "د $n توکو ړنګول")
  val deleteIt: String get() = t("حذف کن", "Delete", "ړنګ یې کړئ")
  val deleteNote: String get() = t(
    "کالاها و سوابقِ ورودِ انبارشان حذف می‌شوند و برگشت ندارد. فاکتورهای قبلی می‌مانند. دلیل در سابقهٔ عملیات ثبت می‌شود.",
    "The items and their stock-in records are deleted and can't be restored. Earlier invoices remain. The reason is recorded in the activity log.",
    "توکي او د ګدام د راتګ سوابق یې ړنګېږي او بېرته نه راځي. پخواني بلونه پاتې کېږي. دلیل د کړنو په تاریخچه کې ثبتېږي.",
  )
  val deleteResult: String get() = t("نتیجهٔ حذف", "Delete result", "د ړنګولو پایله")
  fun levelsN(n: Any?): String = t("حدِ موجودیِ ${asIs(n)} کالا", "Stock levels of $n ${pl(n ?: 0, "item", "items")}", "د $n توکو د موجودۍ حدونه")
  val saveIt: String get() = t("ذخیره کن", "Save", "خوندي یې کړئ")
  val levelsNote: String get() = t(
    "ترتیبِ درست: حد کم بودن ≤ نقطهٔ سفارش < بیشینه. کالایی که با این عددها جور نیست عوض نمی‌شود. برگشت‌پذیر است.",
    "Correct order: low level ≤ reorder point < maximum. Items that don't fit these numbers aren't changed. Reversible.",
    "سم ترتیب: د کمښت حد ≤ د فرمایش ټکی < اعظمي. هغه توکی چې له دې شمېرو سره سم نه وي نه بدلېږي. بېرته کېدونکی دی.",
  )
  val levelsResult: String get() = t("نتیجهٔ حدِ موجودی", "Stock levels result", "د موجودۍ د حدونو پایله")
  fun savedNote(note: Any?): String = t("ذخیره شد — ${asIs(note)}", "Saved — $note", "خوندي شو — $note")
  val unchangedAlready: String get() = t("بی‌تغییر — از قبل همین است", "Unchanged — already like this", "بې بدلونه — له مخکې همداسې دی")
  fun minChange(before: Any?, after: Any?): String = t(
    "حد کم بودن ${asIs(before)} ⇐ ${asIs(after)}",
    "Low level $before ⇐ $after",
    "د کمښت حد $before ⇐ $after",
  )
  fun reorderChange(before: Any?, after: Any?): String = t(
    "نقطهٔ سفارش ${asIs(before)} ⇐ ${asIs(after)}",
    "Reorder point $before ⇐ $after",
    "د فرمایش ټکی $before ⇐ $after",
  )
  fun maxChange(before: Any?, after: Any?): String = t(
    "بیشینه ${asIs(before)} ⇐ ${asIs(after)}",
    "Maximum $before ⇐ $after",
    "اعظمي $before ⇐ $after",
  )
  val alreadySo: String get() = t("از قبل همین است", "Already like this", "له مخکې همداسې دی")
  fun levelsFor(n: Any?): String = t(
    "حدِ موجودی برای ${asIs(n)} کالا",
    "Stock levels for $n ${pl(n ?: 0, "item", "items")}",
    "د $n توکو لپاره د موجودۍ حدونه",
  )
  val emptyUntouched: String get() = t(
    "کادرِ خالی دست نمی‌خورد. مقدار به واحدِ خودِ هر کالا است.",
    "An empty box isn't touched. Amounts are in each item's own unit.",
    "تش بکس نه بدلېږي. اندازه د هر توکي په خپل واحد ده.",
  )
  val reorderPoint: String get() = t("نقطهٔ سفارش", "Reorder point", "د فرمایش ټکی")
  val maxStock: String get() = t("بیشینهٔ موجودی", "Maximum stock", "اعظمي موجودي")

  // جدول و کارت‌ها
  val selectAllVisible: String get() = t("انتخابِ همهٔ ردیف‌های دیده‌شده", "Select all visible rows", "ټول ښکاره قطارونه ټاکل")
  val slashCategory: String get() = t("/ دسته", "/ Category", "/ ډله")
  val barcodeDate: String get() = t("بارکد · تاریخ", "Barcode · date", "بارکوډ · نېټه")
  val buyAfn: String get() = t("خرید (؋)", "Cost (؋)", "پېر (؋)")
  val sellAfn: String get() = t("فروش (؋)", "Sale (؋)", "پلور (؋)")
  fun selectName(name: Any?): String = t("انتخابِ ${asIs(name)}", "Select $name", "$name ټاکل")
  fun buyPrice(price: Any?): String = t("خرید: ${asIs(price)} ؋", "Cost: $price ؋", "پېر: $price ؋")

  // ردیفِ کالای تازه
  val similarName: String get() = t("نامِ مشابه در دکان: ", "Similar name in the shop: ", "په دوکان کې ورته نوم: ")
  val enterAgain: String get() = t(
    " — اگر کالای دیگری است، دوباره Enter بزنید",
    " — if it's a different item, press Enter again",
    " — که بل توکی وي، بیا Enter ووهئ",
  )
  fun nameRecorded(name: Any?): String = t("«${asIs(name)}» ثبت شد", "“$name” saved", "«$name» ثبت شو")
  val barcodesComma: String get() = t("بارکدها (،)", "Barcodes (,)", "بارکوډونه (،)")
  val buyShort: String get() = t("خرید", "Cost", "پېر")
  val sellShort: String get() = t("فروش", "Sale", "پلور")

  // برگهٔ ویرایش
  fun nameSaved(name: Any?): String = t("«${asIs(name)}» ذخیره شد", "“$name” saved", "«$name» خوندي شو")
  val editItem: String get() = t("ویرایشِ کالا", "Edit item", "د توکي سمون")
  val closeEsc: String get() = t("بستن Esc", "Close Esc", "بندول Esc")
  fun stockIs(qty: Any?, unit: Any?): String = t("موجودی: ${asIs(qty)} ${asIs(unit)}", "Stock: $qty $unit", "موجودي: $qty $unit")
  val barcodesSep: String get() = t("بارکدها (با «،» جدا)", "Barcodes (separate with “,”)", "بارکوډونه (په «،» جلا)")
  val brand: String get() = t("برند", "Brand", "برانډ")
  val tagsSep: String get() = t("برچسب‌ها (با «،» جدا)", "Tags (separate with “,”)", "ټګونه (په «،» جلا)")
  val priceReason: String get() = t(
    "دلیلِ تغییرِ قیمت (با نامِ شما در سابقه)",
    "Reason for price change (with your name in the history)",
    "د بیې د بدلون دلیل (ستاسو له نوم سره په تاریخچه کې)",
  )
  val alertLevel: String get() = t("حدِ هشدارِ موجودی", "Stock alert level", "د موجودۍ د خبرتیا حد")
  val activeSeen: String get() = t("فعال (در فروش دیده می‌شود)", "Active (seen at sale)", "فعال (په پلور کې ښکاري)")
  val saveKeys: String get() = t("ذخیره  (Enter / Ctrl+S)", "Save  (Enter / Ctrl+S)", "خوندي کول  (Enter / Ctrl+S)")
  val detailsPhoto: String get() = t("جزئیات و عکس", "Details & photo", "جزئیات او انځور")
}
