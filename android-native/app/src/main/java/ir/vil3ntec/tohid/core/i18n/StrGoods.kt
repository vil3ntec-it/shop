package ir.vil3ntec.tohid.core.i18n

/**
 *  پیام‌های لایهٔ دفتر (`data/`) برای کالا، موجودی و گدام، خرید و تأمین‌کننده، ورود از فایل، عکس و ترازو — پیش‌تر متنِ خامِ فارسی
 *  که از هیچ زبانی نمی‌گذشت (LOC-08 دری، دورِ ۷). فارسی بایت‌به‌بایت همان پیشین؛ هر مقدار با `asIs`. یادداشتِ سابقه، برگهٔ خروجی و
 *  کلیدِ منطق در خودِ پرونده با «// داده: …» مانده‌اند. قاعده‌ها سرِ `I18n.kt`.
 */
@Suppress("unused")
object StrGoods {

  // ── کالا، ورود و اصلاحِ موجودی (`WarehouseEngine`) ──
  val madeAfterExpiry: String get() = t("تاریخِ تولید بعد از انقضا است", "The production date is after expiry", "د جوړېدو نېټه له ختمېدو وروسته ده")
  val countedNegative: String get() = t(
    "مقدارِ شمرده‌شده نمی‌تواند منفی باشد",
    "The counted quantity can't be negative",
    "شمېرل شوې اندازه منفي نه شي کېدای",
  )
  val noDifference: String get() = t(
    "اختلافی نیست — موجودیِ شمرده‌شده با برنامه یکی است",
    "No difference — the counted stock matches the app",
    "توپیر نشته — شمېرل شوې موجودي له پروګرام سره یوه ده",
  )
  val saleUnitSame: String get() = t(
    "واحدِ فروش همان واحدِ موجودی است — خالی بگذارید",
    "The sale unit is the same as the stock unit — leave it empty",
    "د پلور واحد د موجودۍ همغه واحد دی — تش یې پرېږدئ",
  )
  val factorNeeded: String get() = t("ضریبِ تبدیل را بنویسید", "Write the conversion factor", "د اړونې ضریب ولیکئ")
  val factorPositive: String get() = t(
    "ضریبِ تبدیل باید عددی بزرگ‌تر از صفر باشد",
    "The conversion factor must be a number above zero",
    "د اړونې ضریب باید له صفر لوی عدد وي",
  )
  val factorExact: String get() = t(
    "ضریب را دقیق و حداکثر با سه رقمِ اعشار بنویسید (مثلاً ۰٫۰۰۱ یا ۲۴)؛ اگر واحدِ فروش کسرِ ناتمامی از واحدِ موجودی است (یک دانه از کارتنِ ۲۴تایی)، واحدِ کوچک‌تر را واحدِ موجودی بگیرید و بزرگ‌تر را بسته",
    "Write the factor exactly, with at most three decimals (e.g. 0.001 or 24); if the sale unit is an endless fraction of the stock unit (one piece of a 24-piece carton), make the smaller unit the stock unit and the bigger one a pack",
    "ضریب سم او تر درېیو اعشاري رقمونو پورې ولیکئ (لکه ۰٫۰۰۱ یا ۲۴)؛ که د پلور واحد د موجودۍ د واحد نه ختمېدونکې برخه وي (د ۲۴ دانه‌یي کارتن یوه دانه)، کوچنی واحد د موجودۍ واحد او لوی یې بسته کړئ",
  )
  fun unitHasHistory(unit: Any?): String = t(
    "واحدِ موجودیِ این کالا «${asIs(unit)}» است و ورود یا فروش دارد؛ با عوض کردنش همهٔ گذشته به واحدِ تازه خوانده می‌شد. برای فروش به واحدِ دیگر «واحدِ فروشِ دوم» یا بسته بگذارید",
    "This item's stock unit is «$unit» and it has entries or sales; changing it would re-read all of its past in the new unit. To sell in another unit, add a second sale unit or a pack",
    "د دې توکي د موجودۍ واحد «$unit» دی او راتګ یا پلور لري؛ په بدلولو یې ټول تېر په نوي واحد لوستل کېده. په بل واحد د پلور لپاره «دوهم د پلور واحد» یا بسته ورزیات کړئ",
  )
  val wholesaleBad: String get() = t("قیمتِ عمده درست نیست", "The wholesale price isn't valid", "د عمده بیه سمه نه ده")
  val nameNeeded: String get() = t("نام کالا را بنویسید", "Write the item's name", "د توکي نوم ولیکئ")
  fun nameTaken(name: Any?): String = t(
    "کالایی به نامِ «${asIs(name)}» از قبل هست",
    "An item named «$name» already exists",
    "د «$name» په نوم توکی له مخکې شته",
  )
  val salePriceBad: String get() = t("قیمت فروش درست نیست", "The sale price isn't valid", "د پلور بیه سمه نه ده")
  val buyPriceBad: String get() = t("قیمت خرید درست نیست", "The purchase price isn't valid", "د پېرود بیه سمه نه ده")
  fun maxBarcodes(n: Any?): String = t(
    "حداکثر ${asIs(n)} بارکد برای یک کالا",
    "At most $n barcodes for one item",
    "د یو توکي لپاره تر ټولو ډېر $n بارکوډونه",
  )
  val barcodeTaken: String get() = t(
    "این بارکد قبلاً برای کالای دیگری ثبت شده",
    "This barcode is already registered for another item",
    "دا بارکوډ له مخکې د بل توکي لپاره ثبت شوی",
  )
  fun barcodeNTaken(code: Any?): String = t(
    "بارکدِ ${asIs(code)} قبلاً برای کالای دیگری ثبت شده",
    "Barcode $code is already registered for another item",
    "بارکوډ $code له مخکې د بل توکي لپاره ثبت شوی",
  )
  val productNotFound: String get() = t("کالا پیدا نشد", "Item not found", "توکی پیدا نه شو")
  fun barcodeIsPack(code: Any?, pack: Any?): String = t(
    "بارکدِ ${asIs(code)} بارکدِ بستهٔ «${asIs(pack)}» همین کالاست",
    "Barcode $code is the barcode of this item's pack «$pack»",
    "بارکوډ $code د همدې توکي د «$pack» بستې بارکوډ دی",
  )
  val hasPacks: String get() = t(
    "این کالا بسته دارد؛ پیش از عوض کردنِ واحد، بسته‌ها را بردارید یا دوباره بنویسید",
    "This item has packs; before changing the unit, remove the packs or write them again",
    "دا توکی بستې لري؛ د واحد له بدلولو مخکې بستې لرې یا بیا ولیکئ",
  )
  val noneSelected: String get() = t("کالایی انتخاب نشده", "No item is selected", "هېڅ توکی نه دی ټاکل شوی")
  val nothingChanged: String get() = t("چیزی عوض نشد", "Nothing changed", "هېڅ شی بدل نه شو")
  val qtyBad: String get() = t("مقدار درست نیست", "The quantity isn't valid", "اندازه سمه نه ده")
  val priceReasonNeeded: String get() = t("دلیلِ تغییرِ قیمت را بنویسید", "Write the reason for the price change", "د بیې د بدلون لامل ولیکئ")
  val confirmPreview: String get() = t(
    "هشدارهای پیش‌نمایش را ببینید و تأیید کنید",
    "Look at the preview warnings and confirm",
    "د مخکتنې خبرتیاوې وګورئ او تایید کړئ",
  )
  val levelBad: String get() = t("عددِ حدِ موجودی درست نیست", "The stock-level number isn't valid", "د موجودۍ د حد عدد سم نه دی")
  fun deleteSoldWarn(name: Any?, sold: Any): String = t(
    "از «${asIs(name)}» تا کنون ${asIs(sold)} واحد فروخته شده است. فاکتورهای قبلی حفظ می‌شوند ولی نام این کالا در آن‌ها «حذف‌شده» نشان داده می‌شود و از گزارش سود محصولات کنار می‌رود.",
    "$sold ${pl(sold, "unit", "units")} of «$name» have been sold so far. Earlier invoices are kept, but this item's name shows as «deleted» in them and it leaves the product profit report.",
    "له «$name» څخه تر اوسه $sold واحده پلورل شوي. پخواني بلونه ساتل کېږي، خو د دې توکي نوم پکې «ړنګ شوی» ښکاري او د توکو د ګټې له راپور وځي.",
  )
  fun deleteWarn(name: Any?): String = t(
    "محصول «${asIs(name)}» و سوابق ورود آن از انبار حذف خواهد شد.",
    "Product «$name» and its stock-entry records will be deleted from the warehouse.",
    "توکی «$name» او د هغه د ننوتو سوابق به له ګدام ړنګ شي.",
  )
  val entryNotFound: String get() = t("ورودی پیدا نشد", "Entry not found", "ننوتنه پیدا نه شوه")
  fun wouldGoNegative(n: Any): String = t(
    "این تغییر موجودی را منفی می‌کند: ${asIs(n)} واحد از این ورودی فروخته شده است",
    "This change makes the stock negative: $n ${pl(n, "unit", "units")} of this entry have been sold",
    "دا بدلون موجودي منفي کوي: له دې ننوتنې $n واحده پلورل شوي",
  )
  fun entryInUse(n: Any): String = t(
    "این ورودی قابل حذف نیست: ${asIs(n)} واحد از آن فروخته شده است. ابتدا فروش‌های مربوطه را لغو یا مرجوع کنید.",
    "This entry can't be deleted: $n ${pl(n, "unit", "units")} of it have been sold. First cancel or return those sales.",
    "دا ننوتنه نه ړنګېږي: له هغې $n واحده پلورل شوي. لومړی اړوند پلورونه لغوه یا بېرته کړئ.",
  )
  fun entryDeleteWarn(name: Any?, from: Any?, to: Any?): String = t(
    "موجودی «${asIs(name)}» از ${asIs(from)} به ${asIs(to)} کاهش می‌یابد.",
    "The stock of «$name» goes down from $from to $to.",
    "د «$name» موجودي له $from څخه $to ته کمېږي.",
  )
  val productWord: String get() = t("محصول", "item", "توکی")
  val addProductFirst: String get() = t("ابتدا یک محصول اضافه کنید", "Add a product first", "لومړی یو توکی ور ګډ کړئ")
  fun onlyInStock(qty: Any?, unit: Any?): String = t(
    "فقط ${asIs(qty)} ${asIs(unit)} موجود است",
    "Only $qty $unit in stock",
    "یوازې $qty $unit موجود دي",
  )
  fun rowWhy(row: Any?, why: Any?): String = t("ردیف ${asIs(row)}: ${asIs(why)}", "Row $row: $why", "کرښه $row: $why")
  val goodsChanged: String get() = t(
    "کالاهای دکان در این فاصله عوض شد — پیش‌نمایش را دوباره ببینید",
    "The shop's items changed in the meantime — look at the preview again",
    "په دې منځ کې د دوکان توکي بدل شول — مخکتنه بیا وګورئ",
  )
  val nothingToSave: String get() = t(
    "چیزی برای ثبت نیست — کالاهای این فایل از قبل در دکان‌اند",
    "There's nothing to save — the items in this file are already in the shop",
    "د ثبت لپاره څه نشته — د دې فایل توکي له مخکې په دوکان کې دي",
  )
  fun rowNoProduct(row: Any?): String = t("ردیفِ ${asIs(row)}: کالا پیدا نشد", "Row $row: item not found", "کرښه $row: توکی پیدا نه شو")
  fun rowOfWhy(row: Any?, why: Any?): String = t("ردیفِ ${asIs(row)}: ${asIs(why)}", "Row $row: $why", "کرښه $row: $why")
  fun rowNoWhy(row: Any?, why: Any?): String = t("ردیف ${asIs(row)}: ${asIs(why)}", "Row $row: $why", "کرښه $row: $why")
  val categoryNeeded: String get() = t("نام دسته‌بندی را بنویسید", "Write the category's name", "د ډلې نوم ولیکئ")
  val categoryMissing: String get() = t("این دسته‌بندی وجود ندارد", "This category doesn't exist", "دا ډله نشته")

  // ── موجودی، محل و جابه‌جایی (`StockActions`، `StockLedger`، `StockPlaces`، `StockTransferBook`، `Repack`) ──
  val mainPlace: String get() = t("فروشگاه (محلِ اصلی)", "Shop (main place)", "دوکان (اصلي ځای)")
  val shipment: String get() = t("محموله", "Shipment", "لېږد")
  fun lotN(lot: Any?): String = t("سری ${asIs(lot)}", "Lot $lot", "لړۍ $lot")
  val mvPurchase: String get() = t("دریافتِ خرید", "Purchase received", "د پېرود ترلاسه کول")
  val mvSale: String get() = t("فروش", "Sale", "پلور")
  val mvCustomerReturn: String get() = t("برگشت از مشتری", "Customer return", "له پېرودونکي بېرته")
  val mvSupplierReturn: String get() = t("برگشت به تأمین‌کننده", "Return to supplier", "برابروونکي ته بېرته")
  val mvAdjust: String get() = t("اصلاح", "Correction", "سمون")
  val mvCountAdjust: String get() = t("تعدیلِ شمارش", "Count adjustment", "د شمېر سمون")
  val mvDamage: String get() = t("خراب", "Damaged", "خراب")
  val mvExpired: String get() = t("منقضی", "Expired", "تاریخ تېر")
  val mvTransferOut: String get() = t("انتقالِ خروجی", "Transfer out", "وتونکی لېږد")
  val mvTransferIn: String get() = t("انتقالِ ورودی", "Transfer in", "راتلونکی لېږد")
  val mvProduction: String get() = t("بسته‌بندیِ دوباره و تولید", "Repacking and production", "بیا بسته‌بندي او تولید")
  val whyNegative: String get() = t("موجودیِ منفی", "Negative stock", "منفي موجودي")
  val whyExpiredSold: String get() = t("فروشِ منقضی", "Expired sold", "د تاریخ تېر پلور")
  val whyUnknownCost: String get() = t("بهای نامعلوم", "Unknown cost", "نامعلومه بیه")
  val mvCountFix: String get() = t("اصلاحِ شمارش", "Count correction", "د شمېر سمون")
  val mvLost: String get() = t("گم‌شده", "Lost", "ورک")
  val mvOther: String get() = t("دیگر", "Other", "نور")
  val legacyEntry: String get() = t("ثبتِ قدیمی", "Old entry", "زوړ ثبت")
  val pickItem: String get() = t("کالا را انتخاب کنید", "Choose the item", "توکی وټاکئ")
  val destSame: String get() = t("مقصد باید با مبدأ فرق کند", "The destination must differ from the origin", "منزل باید له سرچینې توپیر ولري")
  fun onlyAt(place: Any?, qty: Any?, unit: Any?): String = t(
    "در «${asIs(place)}» فقط ${asIs(qty)} ${asIs(unit)} هست",
    "Only $qty $unit at «$place»",
    "په «$place» کې یوازې $qty $unit شته",
  )
  val transferImpossible: String get() = t("انتقال ممکن نیست", "The transfer isn't possible", "لېږد شونی نه دی")
  /** INV-20 — سریِ برگزیده در مبدأ کم است (`StockPlaces.transfer`) */
  fun lotOnlyAt(lot: Any?, place: Any?, qty: Any?, unit: Any?): String = t(
    "از «${asIs(lot)}» در «${asIs(place)}» فقط ${asIs(qty)} ${asIs(unit)} هست",
    "Only $qty $unit of «$lot» at «$place»",
    "د «$lot» څخه په «$place» کې یوازې $qty $unit شته",
  )
  /** INV-20 — بی سری فقط پس از همهٔ سری‌های مبدأ (همان FEFO ِ موتورِ تازه) */
  val lotlessLast: String get() = t(
    "کالای بی سری فقط پس از همهٔ سری‌های این محل جابه‌جا می‌شود",
    "Stock without a lot moves only after every lot at this place",
    "بې لړۍ توکی یوازې د دې ځای له ټولو لړیو وروسته لېږدول کېږي",
  )
  val countOneBad: String get() = t("عددِ شمرده‌شدهٔ یک کالا درست نیست", "One item's counted number isn't valid", "د یو توکي شمېرل شوی عدد سم نه دی")
  val opIdEmpty: String get() = t("شناسهٔ کار خالی است", "The operation id is empty", "د کار پېژندنه تشه ده")
  val placeNameNeeded: String get() = t("نامِ محل را بنویسید", "Write the place's name", "د ځای نوم ولیکئ")
  fun placeNameTooLong(n: Any?): String = t(
    "نامِ محل حداکثر ${asIs(n)} حرف است",
    "The place name can have at most $n characters",
    "د ځای نوم تر ټولو ډېر $n توري دی",
  )
  fun placeExists(name: Any?): String = t("«${asIs(name)}» از پیش هست", "«$name» already exists", "«$name» له مخکې شته")
  val placeNotListed: String get() = t("این محل در فهرست نیست", "This place isn't on the list", "دا ځای په لېست کې نشته")
  fun placeNotEmpty(name: Any?, left: Any): String = t(
    "در «${asIs(name)}» هنوز ${asIs(left)} کالا هست — اول جابه‌جا کنید",
    "There are still $left ${pl(left, "item", "items")} at «$name» — move them first",
    "په «$name» کې لا $left توکي شته — لومړی یې ولېږدوئ",
  )
  fun isKit(name: Any?): String = t(
    "«${asIs(name)}» کیت است و موجودیِ خودش را ندارد",
    "«$name» is a kit and has no stock of its own",
    "«$name» کټ دی او خپله موجودي نه لري",
  )
  fun isService(name: Any?): String = t(
    "«${asIs(name)}» خدماتی است و موجودی ندارد",
    "«$name» is a service and has no stock",
    "«$name» خدمتي دی او موجودي نه لري",
  )
  fun pickRole(role: Any?): String = t("${asIs(role)} را انتخاب کنید", "Choose the $role", "$role وټاکئ")
  fun isInactive(name: Any?): String = t("«${asIs(name)}» غیرفعال است", "«$name» is turned off", "«$name» غیرفعال دی")
  fun atPlace(place: Any?): String = t(" در «${asIs(place)}»", " at «$place»", " په «$place» کې")
  fun repackShort(name: Any?, where: Any?, have: Any?, unit: Any?, need: Any?): String = t(
    "از «${asIs(name)}»${asIs(where)} فقط ${asIs(have)} ${asIs(unit)} هست، این کار ${asIs(need)} می‌خواهد",
    "Of «$name»$where there is only $have $unit, this job needs $need",
    "له «$name»$where یوازې $have $unit شته، دا کار $need غواړي",
  )
  /** INV-21 — دستورِ ساخت (`Bom`): کالایی که دستورِ ثبت‌شده ندارد */
  fun noBom(name: Any?): String = t(
    "برای «${asIs(name)}» دستورِ ساخت ثبت نشده است",
    "No recipe is saved for «$name»",
    "د «$name» لپاره د جوړولو لارښود نه دی ثبت شوی",
  )
  fun bomLossBad(max: Any?): String = t(
    "اتلاف بیش از ${asIs(max)} درصد نمی‌شود",
    "Expected loss can't be over $max percent",
    "ضایع له $max سلنې زیات نه شي",
  )
  val packedItemBox: String get() = t("کالای بسته (کارتن)", "packed item (carton)", "بسته توکی (کارتن)")
  val looseItem: String get() = t("کالای دانه", "single item", "دانه‌يي توکی")
  val looseSame: String get() = t(
    "کالای دانه باید با کالای بسته فرق کند",
    "The single item must differ from the packed item",
    "دانه‌يي توکی باید له بسته توکي توپیر ولري",
  )
  val packCountBad: String get() = t("تعدادِ بسته درست نیست", "The number of packs isn't valid", "د بستو شمېر سم نه دی")
  val perPackBad: String get() = t(
    "تعدادِ دانهٔ هر بسته درست نیست",
    "The number of pieces per pack isn't valid",
    "په هره بسته کې د دانو شمېر سم نه دی",
  )
  val piecesBad: String get() = t("مقدارِ دانه‌ها درست نیست", "The quantity of pieces isn't valid", "د دانو اندازه سمه نه ده")
  val packedItem: String get() = t("کالای بسته", "packed item", "بسته توکی")
  val addOnePart: String get() = t("دست‌کم یک جزء بیفزایید", "Add at least one part", "لږ تر لږه یوه برخه ور ګډه کړئ")
  fun maxParts(n: Any?): String = t("حداکثر ${asIs(n)} جزء", "At most $n parts", "تر ټولو ډېر $n برخې")
  val partTwice: String get() = t("یک کالا دو بار در اجزا آمده", "One item appears twice in the parts", "یو توکی دوه ځله په برخو کې راغلی")
  val packSelfPart: String get() = t("بسته جزءِ خودش نمی‌شود", "A pack can't be a part of itself", "بسته د ځان برخه نه شي کېدای")
  val part: String get() = t("جزء", "part", "برخه")

  // ── خرید و تأمین‌کننده (`PurchaseBook`، `PurchaseOrderBook`) ──
  val oneRowNeeded: String get() = t("دست‌کم یک ردیف کالا لازم است", "At least one item row is needed", "لږ تر لږه د توکي یوه کرښه پکار ده")
  fun rowQtyBad(row: Any?): String = t("ردیفِ ${asIs(row)}: مقدار معتبر نیست", "Row $row: the quantity isn't valid", "کرښه $row: اندازه سمه نه ده")
  fun rowPriceBad(row: Any?): String = t("ردیفِ ${asIs(row)}: قیمت معتبر نیست", "Row $row: the price isn't valid", "کرښه $row: بیه سمه نه ده")
  val returnPriceBad2: String get() = t("قیمتِ برگشت معتبر نیست", "The return price isn't valid", "د بېرته ورکړې بیه سمه نه ده")
  val returnReasonNeeded: String get() = t("دلیلِ برگشت را بنویسید", "Write the reason for the return", "د بېرته ورکړې لامل ولیکئ")
  fun returnCap(name: Any?, qty: Any?, unit: Any?): String = t(
    "از «${asIs(name)}» فقط ${asIs(qty)} ${asIs(unit)} خریده شده که هنوز برنگشته",
    "Only $qty $unit bought from «$name» haven't been returned yet",
    "له «$name» څخه یوازې $qty $unit پېرل شوي چې لا بېرته شوي نه دي",
  )
  val dateBad: String get() = t("تاریخ درست نیست", "The date isn't valid", "نېټه سمه نه ده")
  val alreadyReturned: String get() = t("همین برگشت پیش‌تر ثبت شده است", "This return was already recorded", "دا بېرته ورکړه له مخکې ثبت شوې")
  val purchaseEntryFixed: String get() = t(
    "این ورود از فاکتورِ خرید آمده و جدا حذف یا عوض نمی‌شود — برای اصلاح «برگشت به تأمین‌کننده» بزنید تا موجودی و بدهی با هم کم شوند",
    "This entry came from a purchase invoice and can't be deleted or changed on its own — to correct it, tap «Return to supplier» so stock and debt go down together",
    "دا ننوتنه له پېرود بل راغلې او جلا نه ړنګېږي او نه بدلېږي — د سمون لپاره «برابروونکي ته بېرته» ووهئ چې موجودي او پور یوځای کم شي",
  )
  /** INV-21 — یک ردیفِ «بسته‌بندیِ دوباره» یا «ساختِ بسته» تنها پاک یا عوض نمی‌شود (`Repack.isDocRow`) */
  val repackEntryFixed: String get() = t(
    "این ردیف بخشی از یک «بسته‌بندیِ دوباره» یا «ساختِ بسته» است و تنها حذف یا عوض نمی‌شود — برای اصلاح همان کار را برعکس ثبت کنید",
    "This row is part of a repack or an assembly and can't be deleted or changed on its own — to correct it, record the same job in reverse",
    "دا کرښه د «بیا بسته‌بندي» یا «د بستې جوړول» برخه ده او یوازې نه ړنګېږي او نه بدلېږي — د سمون لپاره همغه کار برعکس ثبت کړئ",
  )
  val purchaseRecorded: String get() = t(
    "همین فاکتورِ خرید پیش‌تر ثبت شده است",
    "This purchase invoice was already recorded",
    "دا د پېرود بل له مخکې ثبت شوی",
  )
  val dueBeforePurchase: String get() = t(
    "سررسید نباید پیش از تاریخِ خرید باشد",
    "The due date mustn't be before the purchase date",
    "د ورکړې نېټه باید د پېرود له نېټې مخکې نه وي",
  )
  val poDraft: String get() = t("پیش‌نویس", "Draft", "مسوده")
  val poSent: String get() = t("فرستاده", "Sent", "لېږل شوی")
  val poPartial: String get() = t("بخشی دریافت‌شده", "Partly received", "یوه برخه ترلاسه شوې")
  val poComplete: String get() = t("کامل", "Complete", "بشپړ")
  val poCancelled: String get() = t("لغو", "Cancelled", "لغوه")
  fun poSupplierMissing(name: Any?): String = t(
    "تأمین‌کنندهٔ «${asIs(name)}» در دفترِ این دستگاه نیست — اول همگام‌سازی کنید",
    "Supplier «$name» isn't in this device's ledger — sync first",
    "برابروونکی «$name» د دې وسیلې په دفتر کې نشته — لومړی همغږي وکړئ",
  )
  fun poLineMissing(line: Any?): String = t("ردیفِ «${asIs(line)}» در سفارش نیست", "Row «$line» isn't in the order", "کرښه «$line» په فرمایش کې نشته")
  fun billNo(no: Any?): String = t("فاکتورِ «${asIs(no)}»", "Invoice «$no»", "بل «$no»")
  val voided: String get() = t(" · باطل", " · void", " · باطل")
  val matchOkSupplier: String get() = t(
    "تطبیقِ سفارش، رسید و فاکتورِ تأمین‌کننده: درست",
    "Order, receipt and supplier's invoice match: OK",
    "د فرمایش، رسید او د برابروونکي د بل سمون: سم",
  )
  val matchOk: String get() = t("تطبیقِ سفارش، رسید و فاکتور: درست", "Order, receipt and invoice match: OK", "د فرمایش، رسید او بل سمون: سم")
  val overReceived: String get() = t("بیش از سفارش رسیده", "More arrived than ordered", "له فرمایش ډېر راغلي")
  val billedOverSupplier: String get() = t(
    "فاکتورِ تأمین‌کننده بیش از رسید",
    "Supplier's invoice is more than received",
    "د برابروونکي بل له ترلاسه شوي ډېر دی",
  )
  val billedOver: String get() = t("فاکتور بیش از رسید", "Invoice is more than received", "بل له ترلاسه شوي ډېر دی")
  val billedUnderSupplier: String get() = t(
    "رسیده ولی در فاکتورِ تأمین‌کننده نیست",
    "Arrived but not on the supplier's invoice",
    "راغلی خو د برابروونکي په بل کې نشته",
  )
  val billedUnder: String get() = t("رسیده ولی فاکتورش در دفتر نیست", "Arrived but its invoice isn't in the ledger", "راغلی خو بل یې په دفتر کې نشته")
  val priceOver: String get() = t("فی بالاتر از سفارش", "Unit price above the order", "د واحد بیه له فرمایش لوړه")
  val priceUnder: String get() = t("فی پایین‌تر از سفارش", "Unit price below the order", "د واحد بیه له فرمایش ټیټه")
  val notOnOrder: String get() = t("بیرون از سفارش", "Not on the order", "له فرمایش بهر")
  val unchanged: String get() = t(" · بی تغییر", " · unchanged", " · بې بدلون")
  fun lastPrice(price: Any?, date: Any?): String = t(
    "آخرین فیِ این تأمین‌کننده: ${asIs(price)} ؋ (${asIs(date)})",
    "This supplier's last unit price: $price ؋ ($date)",
    "د دې برابروونکي وروستۍ بیه: $price ؋ ($date)",
  )
  val noBuyYet: String get() = t(
    "از این تأمین‌کننده هنوز خریده نشده",
    "Nothing bought from this supplier yet",
    "له دې برابروونکي لا څه نه دي پېرل شوي",
  )

  // ── ورودِ کالا از فایل (`ProductImport`) ──
  val noName: String get() = t("نام ندارد", "Has no name", "نوم نه لري")
  val dupName: String get() = t("نامِ تکراری در همین فایل", "Duplicate name in this file", "په همدې فایل کې تکراري نوم")
  val alreadyInShop: String get() = t("این کالا از قبل در دکان هست", "This item is already in the shop", "دا توکی له مخکې په دوکان کې شته")
  val buyPriceBad2: String get() = t("قیمتِ خرید درست نیست", "The purchase price isn't valid", "د پېرود بیه سمه نه ده")
  val salePriceBad2: String get() = t("قیمتِ فروش درست نیست", "The sale price isn't valid", "د پلور بیه سمه نه ده")
  val minStockBad: String get() = t("حداقلِ موجودی درست نیست", "The minimum stock isn't valid", "لږ تر لږه موجودي سمه نه ده")
  val dupBarcode: String get() = t("بارکدِ تکراری", "Duplicate barcode", "تکراري بارکوډ")
  fun fileTooBigSplit(mb: Any?): String = t(
    "فایل بزرگ‌تر از ${asIs(mb)} مگابایت است — آن را چند تکه کنید",
    "The file is larger than $mb MB — split it into a few parts",
    "فایل له $mb مېګابایټه لوی دی — په څو برخو یې ووېشئ",
  )
  val fileEmpty: String get() = t("فایل خالی است", "The file is empty", "فایل تش دی")
  val oldXls: String get() = t(
    "فایلِ xls ِ قدیمی (یا اکسلِ رمزدار) خوانده نمی‌شود — در اکسل «ذخیره به xlsx» کنید",
    "An old xls file (or a password-protected Excel file) can't be read — in Excel, use «Save as xlsx»",
    "زوړ xls فایل (یا د پټنوم لرونکی اکسل) نه لوستل کېږي — په اکسل کې «د xlsx په توګه خوندي کول» وکړئ",
  )
  val notUtf8: String get() = t(
    "فایل UTF-8 نیست — در اکسل «CSV UTF-8» ذخیره کنید یا خودِ فایلِ xlsx را بدهید",
    "The file isn't UTF-8 — in Excel save it as «CSV UTF-8» or give the xlsx file itself",
    "فایل UTF-8 نه دی — په اکسل کې یې د «CSV UTF-8» په توګه خوندي کړئ یا پخپله xlsx فایل ورکړئ",
  )
  val notTable: String get() = t(
    "این فایل جدول نیست — xlsx یا CSV بدهید",
    "This file isn't a table — give xlsx or CSV",
    "دا فایل جدول نه دی — xlsx یا CSV ورکړئ",
  )
  val xlsxTooBig: String get() = t(
    "فایلِ اکسل پس از باز شدن بیش از حد بزرگ است — آن را چند تکه کنید",
    "The Excel file is too large once opened — split it into a few parts",
    "اکسل فایل له خلاصېدو وروسته ډېر لوی دی — په څو برخو یې ووېشئ",
  )
  val xlsxBroken: String get() = t(
    "فایلِ اکسل خراب است و باز نمی‌شود",
    "The Excel file is damaged and doesn't open",
    "اکسل فایل خراب دی او نه خلاصېږي",
  )
  val notXlsx: String get() = t(
    "این ZIP اکسل (xlsx) نیست — فایلِ ods یا دیگر را «ذخیره به xlsx» کنید",
    "This ZIP isn't an Excel (xlsx) file — use «Save as xlsx» for an ods or other file",
    "دا ZIP اکسل (xlsx) نه دی — ods یا بل فایل «د xlsx په توګه خوندي» کړئ",
  )
  val noFirstSheet: String get() = t(
    "برگهٔ اولِ فایلِ اکسل پیدا نشد",
    "The Excel file's first sheet wasn't found",
    "د اکسل فایل لومړۍ پاڼه پیدا نه شوه",
  )
  fun kitNotYet(name: Any?): String = t(
    "«${asIs(name)}» کیت است و با موتورِ تازهٔ موجودی هنوز فروخته نمی‌شود",
    "«$name» is a kit and isn't sold with the new stock engine yet",
    "«$name» کټ دی او د موجودۍ له نوي انجن سره لا نه پلورل کېږي",
  )
  fun kitPartShort(kit: Any?, part: Any?, have: Any?, want: Any?): String = t(
    "«${asIs(kit)}»: از جزءِ «${asIs(part)}» ${asIs(have)} مانده، این سبد ${asIs(want)} می‌خواهد",
    "«$kit»: $have of part «$part» left, this cart needs $want",
    "«$kit»: د «$part» برخې $have پاتې، دا ټوکرۍ $want غواړي",
  )

  // ── تخفیف و ترازو (`PromoEngine`، `ScaleLabels`) ──
  val promoApplied: String get() = t("اعمال شد", "Applied", "پلی شو")
  val promoNoEffect: String get() = t("روی این سبد اثری ندارد", "Has no effect on this cart", "په دې ټوکرۍ اغېز نه لري")
  val promoUsedUp: String get() = t("ظرفیتِ این کد پر شده است", "This code has been used up", "د دې کوډ ظرفیت ډک شوی")
  val promoInactive: String get() = t("این کد الآن فعال نیست", "This code isn't active now", "دا کوډ اوس فعال نه دی")
  val promoNotFound: String get() = t("این کد پیدا نشد", "This code was not found", "دا کوډ پیدا نه شو")
  fun itemCodeNotFound(code: Any?): String = t("کالای «${asIs(code)}» پیدا نشد", "Item «$code» not found", "توکی «$code» پیدا نه شو")
  fun scaleBadCheck(code: Any?): String = t(
    "برچسبِ ترازو رد شد — رقمِ کنترلِ «${asIs(code)}» درست نیست",
    "Scale label rejected — the check digit of «$code» isn't valid",
    "د تلې نښه رد شوه — د «$code» کنټرولي عدد سم نه دی",
  )
  fun scaleUnknown(code: Any?): String = t(
    "کالای ترازو با کدِ «${asIs(code)}» ثبت نشده — این کد را به بارکدهای کالا بیفزایید",
    "No scale item is registered with code «$code» — add this code to the item's barcodes",
    "د «$code» کوډ سره د تلې توکی نه دی ثبت — دا کوډ د توکي بارکوډونو ته ور ګډ کړئ",
  )
  val scaleZero: String get() = t("وزن یا مبلغِ برچسب صفر است", "The label's weight or amount is zero", "د نښې وزن یا مبلغ صفر دی")
  fun notWeighed(name: Any?, unit: Any?): String = t(
    "«${asIs(name)}» کالای وزنی نیست (واحد: ${asIs(unit)})",
    "«$name» isn't a weighed item (unit: $unit)",
    "«$name» وزني توکی نه دی (واحد: $unit)",
  )
  fun scaleNoPrice(name: Any?): String = t(
    "«${asIs(name)}» قیمتِ فروش ندارد — برچسبِ قیمتی بی قیمتِ کالا خوانده نمی‌شود",
    "«$name» has no sale price — a price label can't be read without the item's price",
    "«$name» د پلور بیه نه لري — د بیې نښه د توکي له بیې پرته نه لوستل کېږي",
  )
  fun scalePriceMismatch(name: Any?): String = t(
    "برچسبِ قیمتیِ «${asIs(name)}» با قیمتِ کالا به همان مبلغ نمی‌رسد — کالا را دوباره روی ترازو بگذارید",
    "The price label of «$name» doesn't come to the same amount with the item's price — put the item on the scale again",
    "د «$name» د بیې نښه د توکي له بیې سره همغه مبلغ ته نه رسېږي — توکی بیا پر تله کېږدئ",
  )

  // ── ضایعات و عکسِ کالا (`ShrinkLoss`، `ImageBank`، `ImageBulk`، `PhotoStore`، `StorefrontMediaPrep`) ──
  val matchBarcode: String get() = t("بارکدِ دقیق", "Exact barcode", "دقیق بارکوډ")
  val matchSku: String get() = t("SKUِ دقیق", "Exact SKU", "دقیق SKU")
  val matchName: String get() = t("نامِ کاملِ یکسان", "Same full name", "یو شان بشپړ نوم")
  val matchWords: String get() = t("همهٔ کلمه‌های نام", "All words of the name", "د نوم ټول لغتونه")
  val matchSpelling: String get() = t("املای نزدیک", "Close spelling", "نږدې املا")
  val photoUrlBad: String get() = t("نشانیِ عکس درست نیست", "The photo address isn't valid", "د انځور پته سمه نه ده")
  fun bankBarcodeDiffers(code: Any?): String = t(
    "بارکدِ این کالا (${asIs(code)}) با بارکدِ کالای بانک فرق دارد",
    "This item's barcode ($code) differs from the bank item's barcode",
    "د دې توکي بارکوډ ($code) د بانک د توکي له بارکوډ توپیر لري",
  )
  fun matchOnlyBy(kind: Any?): String = t(
    "تطبیق فقط با ${asIs(kind)} است، نه بارکد",
    "The match is only by $kind, not by barcode",
    "سمون یوازې د $kind له مخې دی، نه د بارکوډ",
  )
  val nameWord: String get() = t("نام", "name", "نوم")
  fun similarKinds(diff: Any?): String = t(
    "چند نوعِ مشابه هست؛ فرق در: ${asIs(diff)}",
    "There are several similar kinds; they differ in: $diff",
    "څو ورته ډولونه شته؛ توپیر په: $diff",
  )
  val bankNameDiffers: String get() = t(
    "نامِ کالای شما با نامِ کالای بانک یکی نیست",
    "Your item's name isn't the same as the bank item's name",
    "ستاسو د توکي نوم د بانک د توکي له نوم سره یو نه دی",
  )
  val noBarcodeToCheck: String get() = t(
    "بارکدِ معتبری برای سنجش نیست؛ تطبیق را خودتان تأیید کنید",
    "There's no valid barcode to check; confirm the match yourself",
    "د ازموینې لپاره سم بارکوډ نشته؛ سمون پخپله تایید کړئ",
  )
  val noDisplayCopy: String get() = t("این عکس نسخهٔ نمایش ندارد", "This photo has no display copy", "دا انځور د ښودلو نسخه نه لري")
  val noDisplayCopyYet: String get() = t(
    "این عکس هنوز نسخهٔ نمایش ندارد",
    "This photo doesn't have a display copy yet",
    "دا انځور لا د ښودلو نسخه نه لري",
  )
  val notImage: String get() = t("این فایل عکس نیست یا خراب است", "This file isn't a photo or is damaged", "دا فایل انځور نه دی یا خراب دی")
  val imageTooBig: String get() = t(
    "عکس بیش از اندازه بزرگ است (بیش از ۶۰ مگاپیکسل)",
    "The photo is too large (over 60 megapixels)",
    "انځور له اندازې ډېر لوی دی (له ۶۰ مېګاپېکسله ډېر)",
  )
  val notImageExt: String get() = t("پسوندِ فایل عکس نیست", "The file extension isn't a photo", "د فایل پسوند انځور نه دی")
  fun fileOverMb(mb: Any?): String = t("فایل بزرگ‌تر از ${asIs(mb)} مگابایت است", "The file is larger than $mb MB", "فایل له $mb مېګابایټه لوی دی")
  val noMemRead: String get() = t(
    "حافظهٔ دستگاه برای خواندنِ این فایل کافی نبود",
    "The device's memory wasn't enough to read this file",
    "د وسیلې حافظه د دې فایل د لوستلو لپاره بس نه وه",
  )
  val noMemPrep: String get() = t(
    "حافظهٔ دستگاه برای آماده‌سازیِ این عکس کافی نبود",
    "The device's memory wasn't enough to prepare this photo",
    "د وسیلې حافظه د دې انځور د چمتو کولو لپاره بس نه وه",
  )
  val fileGone: String get() = t(
    "فایل روی این دستگاه نماند — دوباره انتخابش کنید",
    "The file didn't stay on this device — choose it again",
    "فایل په دې وسیله پاتې نه شو — بیا یې وټاکئ",
  )
  fun imageSmall(px: Any?): String = t(
    "عکس کوچک است (زیرِ ${asIs(px)} پیکسل)",
    "The photo is small (under $px pixels)",
    "انځور کوچنی دی (له $px پېکسله کم)",
  )
  val bankWaitsNet: String get() = t(
    "روی دستگاه گذاشته شد؛ بانک منتظرِ اینترنت",
    "Put on the device; the bank is waiting for the internet",
    "په وسیله کېښودل شو؛ بانک د انټرنېټ په تمه دی",
  )
  fun bankRefused(why: Any?): String = t(
    "روی دستگاه گذاشته شد؛ بانکِ تصاویر نپذیرفت: ${asIs(why)}",
    "Put on the device; the image bank didn't accept it: $why",
    "په وسیله کېښودل شو؛ د انځورونو بانک ونه مانه: $why",
  )
  val photoUnread: String get() = t("عکس خوانده نشد", "The photo couldn't be read", "انځور ونه لوستل شو")
  val photoBad: String get() = t("عکس درست نیست", "The photo isn't valid", "انځور سم نه دی")
  fun mediaNotShrunk(kind: Any?): String = t(
    "${asIs(kind)} به اندازهٔ لازم کوچک نشد؛ عکسِ ساده‌تری انتخاب کنید",
    "The $kind didn't get small enough; choose a simpler photo",
    "$kind تر اړین حده کوچنی نه شو؛ ساده انځور وټاکئ",
  )
  val unitWord: String get() = t("واحد", "unit", "واحد")
  val unknownSeller: String get() = t("نامعلوم", "Unknown", "نامعلوم")
}
