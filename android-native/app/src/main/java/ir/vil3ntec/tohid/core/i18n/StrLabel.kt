package ir.vil3ntec.tohid.core.i18n

/**
 *  چاپِ برچسبِ کالا و قفسه (PRD-23) و بارکدِ داخلی (PRD-05) — پنجرهٔ «چاپِ برچسب» ِ انبار، پیام‌های
 *  `InternalBarcodes` و `ProductLabelPrint`، و کلیدِ «تنظیمات».
 *
 *  قاعده‌ها سرِ `I18n.kt`. ⛔ نامِ کالا و بارکد داده‌اند و ترجمه نمی‌شوند.
 */
@Suppress("unused")
object StrLabel {

  val title: String get() = t("چاپِ برچسب", "Print labels", "لیبل چاپول")
  val subtitle: String get() = t(
    "کالاها را انتخاب کنید و تعدادِ برچسبِ هر کدام را بنویسید",
    "Pick the products and how many labels each needs",
    "توکي وټاکئ او د هر یوه د لیبلونو شمېر ولیکئ",
  )
  val openButton: String get() = t("چاپِ برچسب و بارکدِ داخلی", "Labels and in-store barcodes", "لیبلونه او دننني بارکوډونه")
  val close: String get() = t("بستن", "Close", "بندول")
  val search: String get() = t("جست‌وجوی کالا یا بارکد…", "Search products or barcodes…", "توکي یا بارکوډ ولټوئ…")
  val selectShown: String get() = t("انتخابِ همهٔ فهرست", "Select all listed", "ټول لیست شوي وټاکئ")
  val clear: String get() = t("هیچ", "None", "هېڅ")
  val copies: String get() = t("تعداد", "Copies", "شمېر")
  val noBarcode: String get() = t("بی بارکد", "No barcode", "بې بارکوډه")
  val weighedOne: String get() = t("وزنی — یک برچسب", "Sold by weight — one label", "وزني — یو لیبل")

  // نوع و جای چاپ
  val kindBarcode: String get() = t("برچسبِ بارکد", "Barcode label", "د بارکوډ لیبل")
  val kindShelf: String get() = t("برچسبِ قفسه", "Shelf label", "د المارۍ لیبل")
  val toPrinter: String get() = t("چاپگرِ برچسب", "Label printer", "د لیبل چاپګر")
  val toA4: String get() = t("برگهٔ A4 (۳×۸)", "A4 sheet (3×8)", "A4 پاڼه (۳×۸)")
  val noLabelPrinter: String get() = t(
    "هنوز چاپگرِ «برچسبِ کالا» تعریف نشده — برگهٔ A4 چاپ می‌شود؛ چاپگر را در «دستگاه‌ها» بیفزایید.",
    "No “product label” printer is set up yet — an A4 sheet will be printed; add the printer under “Devices”.",
    "لا تر اوسه د «توکي لیبل» چاپګر نه دی ټاکل شوی — A4 پاڼه چاپېږي؛ چاپګر په «وسایلو» کې ورزیات کړئ.",
  )

  // بارکدِ داخلی
  fun makeCodes(n: Any): String = t(
    "ساختِ بارکدِ داخلی برای $n کالای بی‌بارکد",
    "Create in-store barcodes for $n ${pl(n, "product", "products")} without one",
    "د $n بې بارکوډه ${pl(n, "توکي", "توکو")} لپاره دننني بارکوډ جوړول",
  )
  fun codesMade(n: Any): String = t("$n بارکدِ داخلی ساخته شد", "$n in-store ${pl(n, "barcode", "barcodes")} created", "$n دننني ${pl(n, "بارکوډ", "بارکوډونه")} جوړ شول")
  val codesNote: String get() = t(
    "بارکدِ داخلی EAN-13 با پیشوندِ ۲۰ یا ۲۹ است و رقمِ کنترل دارد؛ هرگز با قاعده‌های ترازو یکی نمی‌شود. هر کالا روی هر دستگاه همان کد را می‌گیرد.",
    "In-store barcodes are EAN-13 starting with 20 or 29, with a check digit; they never overlap the scale rules. A product gets the same code on every device.",
    "دننني بارکوډ EAN-13 دی چې په ۲۰ یا ۲۹ پیلېږي او د کنټرول عدد لري؛ هېڅکله د تلې له قاعدو سره یو نه کېږي. هر توکی په هره وسیله کې هماغه کوډ اخلي.",
  )
  val noFreePrefix: String get() = t(
    "پیشوندهای ۲۰ و ۲۹ را قاعدهٔ ترازو گرفته است؛ یکی را در «دستگاه‌ها ⇐ ترازو» بردارید تا بارکدِ داخلی ساخته شود.",
    "The scale rules already use both 20 and 29; remove one under “Devices ⇐ Scale” to create in-store barcodes.",
    "د تلې قاعدو ۲۰ او ۲۹ دواړه نیولي؛ یو یې په «وسایل ⇐ تله» کې لرې کړئ چې دننني بارکوډ جوړ شي.",
  )
  val noFreeCode: String get() = t("بارکدِ آزاد پیدا نشد؛ دوباره امتحان کنید.", "No free barcode was found; try again.", "خلاص بارکوډ ونه موندل شو؛ بیا هڅه وکړئ.")
  val allHaveCodes: String get() = t("همهٔ کالاهای انتخاب‌شده بارکد دارند", "All the selected products already have a barcode", "ټول ټاکل شوي توکي بارکوډ لري")

  // چاپ
  fun printN(n: Any): String = t("چاپِ $n برچسب", "Print $n ${pl(n, "label", "labels")}", "د $n ${pl(n, "لیبل", "لیبلونو")} چاپول")
  fun missingCodes(n: Any): String = t(
    "$n کالا بارکد ندارد و برچسبِ بارکد نمی‌گیرد — اول بارکدِ داخلی بسازید یا «برچسبِ قفسه» را بزنید.",
    "$n ${pl(n, "product has", "products have")} no barcode and won't get a barcode label — create in-store barcodes first or choose “Shelf label”.",
    "$n ${pl(n, "توکی", "توکي")} بارکوډ نه لري او د بارکوډ لیبل نه اخلي — لومړی دننني بارکوډ جوړ کړئ یا «د المارۍ لیبل» وټاکئ.",
  )
  fun dropped(n: Any, max: Any): String = t(
    "$n برچسب از سقفِ $max برچسب در هر بار بیشتر است و چاپ نمی‌شود.",
    "$n ${pl(n, "label is", "labels are")} over the limit of $max per job and won't be printed.",
    "$n ${pl(n, "لیبل", "لیبلونه")} په هر ځل کې له $max حد څخه زیات دي او نه چاپېږي.",
  )
  val nothingToPrint: String get() = t("برچسبی برای چاپ نیست", "There are no labels to print", "د چاپ لپاره لیبل نشته")
  val printTitle: String get() = t("برچسبِ کالا", "Product labels", "د توکو لیبلونه")
  fun printed(n: Any): String = t("$n برچسب چاپ شد", "$n ${pl(n, "label", "labels")} printed", "$n ${pl(n, "لیبل", "لیبلونه")} چاپ شول")
  fun a4Opened(pages: Any): String = t(
    "پنجرهٔ چاپ باز شد — $pages برگه؛ «اندازهٔ واقعی / بی حاشیه» را بزنید.",
    "The print dialog is open — $pages ${pl(pages, "sheet", "sheets")}; choose “Actual size / no margins”.",
    "د چاپ کړکۍ پرانیستل شوه — $pages ${pl(pages, "پاڼه", "پاڼې")}؛ «اصلي اندازه / بې څنډې» وټاکئ.",
  )
  fun labelFailed(name: Any, why: Any): String = t("برچسبِ «$name» ساخته نشد: $why", "The label for “$name” couldn't be made: $why", "د «$name» لیبل جوړ نه شو: $why")
  fun perUnit(unit: Any): String = t("هر $unit", "per $unit", "هر $unit")

  // تنظیمات
  val settingsTitle: String get() = t("برچسب و بارکدِ داخلی", "Labels and in-store barcodes", "لیبلونه او دننني بارکوډونه")
  val settingsOn: String get() = t("در «انبار» دیده می‌شود", "Shown under “Warehouse”", "په «ګودام» کې ښکاري")
  val settingsNote: String get() = t(
    "دکمهٔ تازه در «انبار»: برچسبِ بارکد (۴۰×۳۰: نام، قیمت، بارکد) یا برچسبِ قفسه (قیمتِ درشت) برای چند کالا با تعدادِ هر کدام، روی چاپگرِ برچسب (TSPL یا ESC/POS) یا برگهٔ A4 ِ ۳×۸؛ و ساختِ بارکدِ داخلی (۲۰/۲۹) برای کالای بی‌بارکد. روی چاپگر و بارکدخوانِ واقعی آزموده نشده است؛ اولین برچسب را اسکن کنید. پس از تغییر، «انبار» را دوباره باز کنید.",
    "A new button under “Warehouse”: barcode labels (40×30: name, price, barcode) or shelf labels (large price) for several products with a count for each, on a label printer (TSPL or ESC/POS) or an A4 3×8 sheet; and in-store barcodes (20/29) for products without one. It has not been tested on a real printer or scanner; scan the first label. After changing this, reopen “Warehouse”.",
    "په «ګودام» کې نوې تڼۍ: د بارکوډ لیبل (۴۰×۳۰: نوم، بیه، بارکوډ) یا د المارۍ لیبل (لویه بیه) د څو توکو لپاره د هر یوه له شمېر سره، د لیبل په چاپګر (TSPL یا ESC/POS) یا د ۳×۸ A4 پاڼه؛ او د بې بارکوډه توکو لپاره دننني بارکوډ (۲۰/۲۹). په ریښتیني چاپګر او بارکوډ لوستونکي نه دی ازمویل شوی؛ لومړی لیبل سکن کړئ. له بدلون وروسته «ګودام» بیا پرانیزئ.",
  )
  val off: String get() = t("خاموش", "Off", "بند")
  val on: String get() = t("روشن", "On", "روښانه")
}
