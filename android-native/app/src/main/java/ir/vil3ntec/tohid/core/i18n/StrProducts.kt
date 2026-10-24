package ir.vil3ntec.tohid.core.i18n

/**
 *  نوشته‌های گروهِ G2 کالاها (products.md) — صاحبش همان گروه است.
 *  هر ویژگی سه‌زبانه: `val x: String get() = t("…", "…", "…")`؛ قاعده‌ها سرِ `I18n.kt`.
 *  پیام‌های موتور (`WarehouseEngine`) داده‌اند و همان‌طور که هستند نشان داده می‌شوند.
 */
@Suppress("unused")
object StrProducts {

  // فهرست
  val listTitle: String get() = t("کالاها", "Products", "توکي")
  val searchHint: String get() = t("جستجوی نام، دسته یا بارکد", "Search name, category or barcode", "نوم، ډله یا بارکوډ ولټوئ")
  val statItems: String get() = t("قلم کالا", "Items", "توکي")
  val statPotential: String get() = t("سود بالقوه", "Potential profit", "احتمالي ګټه")
  val statPotentialCaption: String get() = t("اگر همه فروخته شود", "If everything sells", "که ټول وپلورل شي")
  val filterLow: String get() = t("کم", "Low", "لږ")
  val filterOut: String get() = t("تمام‌شده", "Out", "پای ته رسېدلی")
  val filterInactive: String get() = t("غیرفعال", "Inactive", "غیرفعال")
  val sort: String get() = t("مرتب‌سازی", "Sort", "ترتیب")
  val sortName: String get() = t("نام", "Name", "نوم")
  val sortLowFirst: String get() = t("کم‌موجودترین", "Lowest stock", "لږ موجودي")
  val sortMostStock: String get() = t("بیشترین موجودی", "Most stock", "ډېره موجودي")
  val sortPriciest: String get() = t("گران‌ترین", "Highest price", "ګران‌ترین")
  val sortNewest: String get() = t("تازه‌ترین", "Newest", "نوي")
  val viewList: String get() = t("نمای فهرست", "List view", "د لړلیک بڼه")
  val viewGrid: String get() = t("نمای شبکه‌ای", "Grid view", "د جال بڼه")
  val newProduct: String get() = t("کالای تازه", "New product", "نوی توکی")
  val swipeEntry: String get() = t("ورود", "Stock in", "راوړل")
  val edit: String get() = t("ویرایش", "Edit", "سمول")
  val delete: String get() = t("حذف", "Delete", "ړنګول")
  fun selectedTitle(n: Int): String = t("$n کالا انتخاب شد", "$n ${pl(n, "item", "items")} selected", "$n توکي وټاکل شول")
  val activate: String get() = t("فعال", "Activate", "فعال")
  val deactivate: String get() = t("غیرفعال", "Deactivate", "غیرفعال")
  val bulkCategory: String get() = t("دسته", "Category", "ډله")
  val cancelSelection: String get() = t("لغو", "Cancel", "لغوه")
  fun bulkDeleteTitle(n: Int): String = t("حذفِ $n کالا؟", "Delete $n ${pl(n, "item", "items")}?", "$n توکي ړنګ شي؟")
  val bulkDeleteBody: String get() = t(
    "کالاها و سوابقِ ورودشان از انبار حذف می‌شوند. فاکتورهای قبلی می‌مانند ولی نامِ کالا در آن‌ها «حذف‌شده» نشان داده می‌شود. برگشت ندارد.",
    "The items and their stock entries are removed. Past invoices stay, but show the item as “deleted”. This can't be undone.",
    "توکي او د ګدام ثبتونه یې ړنګېږي. پخواني بلونه پاتې کېږي خو د توکي نوم «ړنګ شوی» ښيي. بېرته نه راګرځي.",
  )
  fun activated(n: Int): String = t("$n کالا فعال شد", "$n ${pl(n, "item", "items")} activated", "$n توکي فعال شول")
  fun deactivated(n: Int): String = t("$n کالا غیرفعال شد", "$n ${pl(n, "item", "items")} deactivated", "$n توکي غیرفعال شول")
  fun deleted(n: Int): String = t("$n کالا حذف شد", "$n ${pl(n, "item", "items")} deleted", "$n توکي ړنګ شول")
  fun categoryChanged(n: Int): String = t("دستهٔ $n کالا عوض شد", "Category changed for $n ${pl(n, "item", "items")}", "د $n توکو ډله بدله شوه")
  val emptyTitle: String get() = t("هنوز کالایی ثبت نشده", "No products yet", "تر اوسه هېڅ توکی نه دی ثبت شوی")
  val emptyBody: String get() = t(
    "کالاهای دکان را یک‌بار ثبت کنید تا در فروش، انبار و گزارش‌ها بیایند.",
    "Add your shop's products once and they appear in sales, stock and reports.",
    "د دوکان توکي یو ځل ثبت کړئ چې په پلور، ګدام او راپورونو کې راشي.",
  )
  val emptyAction: String get() = t("ثبت کالا", "Add products", "توکي ثبت کړئ")
  val clearFilters: String get() = t("پاک کردنِ فیلترها", "Clear filters", "فلټرونه پاک کړئ")
  fun deleteTitle(name: String): String = t("حذف «$name»؟", "Delete “$name”?", "«$name» ړنګ شي؟")
  val productDeleted: String get() = t("محصول حذف شد", "Product deleted", "توکی ړنګ شو")

  // جزئیات
  val missingTitle: String get() = t("این کالا دیگر نیست", "This product is gone", "دا توکی نور نشته")
  val missingBody: String get() = t("شاید حذف شده باشد. به فهرست برگردید.", "It may have been deleted. Go back to the list.", "کېدای شي ړنګ شوی وي. لړلیک ته ورګرځئ.")
  val productPhoto: String get() = t("عکس محصول", "Product photo", "د توکي انځور")
  val phoneCamera: String get() = t("عکس با دوربینِ گوشی", "Photo with the phone camera", "د ګرځنده کمرې انځور")
  val deleteProduct: String get() = t("حذف محصول", "Delete product", "توکی ړنګول")
  val stockNow: String get() = t("موجودی فعلی", "In stock", "اوسنۍ موجودي")
  val salePrice: String get() = t("قیمت فروش", "Sale price", "د پلور بیه")
  val purchasePrice: String get() = t("قیمت خرید", "Purchase price", "د پېرود بیه")
  fun unitProfit(unit: String): String = t("سود هر $unit", "Profit per $unit", "د هر $unit ګټه")
  val unitWord: String get() = t("واحد", "unit", "واحد")
  val stockProfit: String get() = t("سود موجودی فعلی", "Profit on current stock", "د اوسنۍ موجودۍ ګټه")
  val minStock: String get() = t("حداقل موجودی", "Minimum stock", "لږترلږه موجودي")
  val sold: String get() = t("فروخته‌شده", "Sold", "پلورل شوي")
  val soldCaption: String get() = t("از آغاز", "All time", "له پیله")
  val earned: String get() = t("سود کسب‌شده", "Profit earned", "ترلاسه شوې ګټه")
  val earnedCaption: String get() = t("پس از مرجوعی", "After returns", "له بېرته راګرځېدو وروسته")
  val statusOk: String get() = t("موجودی کافی", "In stock", "بسنه موجودي")
  val statusLow: String get() = t("موجودی کم", "Low stock", "لږه موجودي")
  val statusOut: String get() = t("تمام‌شده", "Out of stock", "پای ته رسېدلی")
  val statusInactive: String get() = t("غیرفعال", "Inactive", "غیرفعال")
  val recentMoves: String get() = t("حرکت‌های اخیر", "Recent movements", "وروستي خوځښتونه")
  val movesEmpty: String get() = t("هنوز حرکتی ثبت نشده", "No movements yet", "تر اوسه خوځښت نشته")
  val movesEmptyBody: String get() = t(
    "ورود کالا، فروش و مرجوعیِ این کالا اینجا فهرست می‌شود.",
    "Stock-ins, sales and returns of this product appear here.",
    "د دې توکي راوړل، پلور او بېرته راګرځېدل دلته ښکاري.",
  )
  val moveIn: String get() = t("ورود خرید", "Purchase in", "د پېرود راوړل")
  val moveSale: String get() = t("فروش", "Sale", "پلور")
  val moveReturn: String get() = t("مرجوعی مشتری", "Customer return", "د پېرودونکي بېرته راوړل")
  val moveCancel: String get() = t("لغو فروش", "Sale cancelled", "پلور لغوه شو")
  val moveSupplierReturn: String get() = t("برگشت به تأمین‌کننده", "Returned to supplier", "عرضه کوونکي ته بېرته")
  val moveAdjust: String get() = t("اصلاح موجودی", "Stock adjustment", "د موجودۍ سمون")
  val history: String get() = t("تاریخچهٔ تغییرات", "Change history", "د بدلونونو تاریخچه")
  val entryCta: String get() = t("ورود کالا به انبار", "Add stock", "ګدام ته توکي راوړل")
  fun pairCode(code: String): String = t(
    "کدِ جفت‌شدن: $code — در گوشی ⇐ دستگاه‌های جانبی همین را بزنید، بعد دوباره «عکس با دوربینِ گوشی»",
    "Pairing code: $code — enter it on the phone under Devices, then tap “Photo with the phone camera” again",
    "د جوړې کوډ: $code — په ګرځنده کې یې د وسیلو برخه کې ولیکئ، بیا بیا «د ګرځنده کمرې انځور»",
  )
  val phoneAsk: String get() = t("در گوشی «گرفتنِ عکس» را بزنید…", "Tap “Take photo” on the phone…", "په ګرځنده کې «انځور اخیستل» کېکاږئ…")
  val phoneOffline: String get() = t("گوشی وصل نیست — اینترنت و ورود به حساب را ببینید", "The phone isn't connected — check internet and sign-in", "ګرځنده نه دی نښلول شوی — انټرنېټ او ننوتل وګورئ")
  val photoSaved: String get() = t("عکس محصول ثبت شد", "Product photo saved", "د توکي انځور خوندي شو")
  val photoFailed: String get() = t("عکس ذخیره نشد", "The photo wasn't saved", "انځور خوندي نه شو")

  // فرمِ کالای تازه و ویرایش
  val editTitle: String get() = t("ویرایش کالا", "Edit product", "توکی سمول")
  val groupMain: String get() = t("اصلی", "Basics", "اصلي")
  val groupPrice: String get() = t("قیمت", "Price", "بیه")
  val groupStock: String get() = t("انبار", "Stock", "ګدام")
  val groupMore: String get() = t("بیشتر", "More", "نور")
  val name: String get() = t("نامِ کالا", "Product name", "د توکي نوم")
  val namePlaceholder: String get() = t("مثلاً بیسکویت شکلاتی", "e.g. Chocolate biscuits", "لکه چاکلېټي بسکټ")
  val firstRowHint: String get() = t("چند کالا را با هم ثبت کنید — هر کارت یک کالا", "Add several products at once — one card per product", "څو توکي یوځای ثبت کړئ — هر کارت یو توکی")
  val category: String get() = t("دسته‌بندی", "Category", "ډله")
  val newCategory: String get() = t("دستهٔ تازه", "New category", "نوې ډله")
  fun profitLine(unit: String, amount: String): String = t("سودِ هر $unit: $amount ؋", "Profit per $unit: $amount ؋", "د هر $unit ګټه: $amount ؋")
  val loss: String get() = t("زیان", "Loss", "تاوان")
  val unit: String get() = t("واحد", "Unit", "واحد")
  val newUnit: String get() = t("واحدِ تازه", "New unit", "نوی واحد")
  val barcode: String get() = t("بارکد", "Barcode", "بارکوډ")
  val barcodePlaceholder: String get() = t("اسکن یا تایپ", "Scan or type", "سکن یا ولیکئ")
  val subcategory: String get() = t("زیردسته", "Subcategory", "فرعي ډله")
  val saleUnit: String get() = t("واحدِ فروشِ دوم", "Second sale unit", "د پلور دویم واحد")
  val saleUnitPlaceholder: String get() = t("گرم", "gram", "ګرام")
  val factor: String get() = t("ضریب", "Factor", "ضریب")
  fun factorHelper(unit: String): String = t("هر واحد = چند $unit", "One sale unit = how many $unit", "یو واحد = څو $unit")
  val supplier: String get() = t("تأمین‌کننده", "Supplier", "عرضه کوونکی")
  val noSupplier: String get() = t("هیچ‌کدام", "None", "هېڅ")
  val activeInSale: String get() = t("فعال در فروش", "Active in sales", "په پلور کې فعال")
  val activeHint: String get() = t("کالای غیرفعال در فروش نمی‌آید؛ سابقه‌اش می‌ماند", "Inactive products don't show in sales; their history stays", "غیرفعال توکی په پلور کې نه راځي؛ تاریخچه یې پاتې کېږي")
  val ready: String get() = t("آماده", "Ready", "چمتو")
  val incomplete: String get() = t("ناقص", "Incomplete", "نیمګړی")
  val untitled: String get() = t("بی‌نام", "Untitled", "بې نومه")
  val rowActions: String get() = t("کارهای ردیف", "Row actions", "د کتار کارونه")
  val duplicateRow: String get() = t("تکثیر ردیف", "Duplicate row", "کتار دوه ځله کول")
  val removeRow: String get() = t("حذف ردیف", "Remove row", "کتار لرې کول")
  val rowRemoved: String get() = t("ردیف حذف شد", "Row removed", "کتار لرې شو")
  val addRow: String get() = t("افزودن ردیف", "Add row", "کتار زیاتول")
  fun readySummary(ready: Int, total: Int): String = t("کالای آماده: $ready از $total", "Ready: $ready of $total", "چمتو توکي: $ready له $total")
  val saveAll: String get() = t("ذخیرهٔ همه", "Save all", "ټول خوندي کړئ")
  val saveChanges: String get() = t("ذخیرهٔ تغییرات", "Save changes", "بدلونونه خوندي کړئ")
  fun rowIncomplete(n: Int): String = t(
    "ردیف $n کامل نیست — نام، دسته، واحد و هر دو قیمت لازم است",
    "Row $n is incomplete — name, category, unit and both prices are required",
    "$n کتار بشپړ نه دی — نوم، ډله، واحد او دواړه بیې اړینې دي",
  )
  val needOneRow: String get() = t("حداقل یک ردیف کامل لازم است", "At least one complete row is needed", "لږترلږه یو بشپړ کتار اړین دی")
  val saveFailed: String get() = t("ثبت نشد", "Not saved", "ثبت نه شو")
  fun savedMany(n: Int): String = t("$n کالا ثبت شد", "$n ${pl(n, "product", "products")} saved", "$n توکي ثبت شول")
  val editSaved: String get() = t("با موفقیت ویرایش شد", "Changes saved", "بدلونونه خوندي شول")

  // دسته‌ها
  val categoriesTitle: String get() = t("دسته‌بندی‌ها", "Categories", "ډلې")
  val pickCategory: String get() = t("انتخاب دسته", "Choose category", "ډله وټاکئ")
  fun bulkCategoryTitle(n: Int): String = t("دستهٔ $n کالا", "Category for $n ${pl(n, "item", "items")}", "د $n توکو ډله")
  val categoryAdded: String get() = t("دسته‌بندی اضافه شد", "Category added", "ډله زیاته شوه")
  val deleteCategoryTitle: String get() = t("حذف دسته‌بندی؟", "Delete category?", "ډله ړنګه شي؟")
  fun deleteCategoryBody(name: String): String = t(
    "دسته‌بندی «$name» حذف می‌شود. محصولات این دسته حذف نمی‌شوند، فقط دیگر در این دسته‌بندی فیلتر نمی‌شوند.",
    "The category “$name” is removed. Its products stay; they just no longer filter under it.",
    "د «$name» ډله ړنګېږي. د دې ډلې توکي نه ړنګېږي، یوازې نور پر دې ډله نه فلټر کېږي.",
  )
  val categoryDeleted: String get() = t("دسته‌بندی حذف شد", "Category deleted", "ډله ړنګه شوه")
  val noCategories: String get() = t("هنوز دسته‌ای نیست", "No categories yet", "تر اوسه ډله نشته")

  // عکس
  val photoTitle: String get() = t("عکس کالا", "Product photo", "د توکي انځور")
  val pickImage: String get() = t("انتخاب تصویر", "Choose a picture", "انځور وټاکئ")
  val camera: String get() = t("دوربین", "Camera", "کمره")
  /** همان ردیفِ «دوربین» روی کامپیوتر (عکسی که از پیش گرفته شده) */
  val cameraDesk: String get() = t("عکسِ آماده", "Ready photo", "چمتو انځور")
  val cameraDetail: String get() = td("گرفتنِ عکسِ تازه", "Take a new photo", "نوی انځور واخلئ", "A photo taken with a phone or webcam", "د ګرځنده یا وېبکم انځور")
  val gallery: String get() = t("گالری", "Gallery", "ګالري")
  val galleryDetail: String get() = td("انتخاب از عکس‌های گوشی", "Pick from the phone's photos", "د ګرځنده له انځورونو وټاکئ", "Pick from the computer's folders", "د کمپیوټر له فولډرونو وټاکئ")
  val photoNote: String get() = td(
    "عکس در خودِ گوشی می‌ماند و به اینترنت نیازی نیست",
    "The photo stays on this phone; no internet needed",
    "انځور په همدې ګرځنده کې پاتې کېږي؛ انټرنېټ ته اړتیا نشته",
    "The photo stays on this computer; no internet needed",
    "انځور په همدې کمپیوټر کې پاتې کېږي؛ انټرنېټ ته اړتیا نشته",
  )
  val studioTitle: String get() = t("استودیوی عکس", "Photo studio", "د انځور سټوډیو")
  val before: String get() = t("پیش", "Before", "مخکې")
  val after: String get() = t("پس", "After", "وروسته")
  val afterCaption: String get() = t("همین ذخیره می‌شود", "This is what gets saved", "همدا خوندي کېږي")
  val groupBackground: String get() = t("زمینه", "Background", "شالید")
  val whiteBackground: String get() = t("زمینهٔ سفید و جدا کردنِ کالا", "White background, product cut out", "سپین شالید او د توکي جلا کول")
  val tolerance: String get() = t("حساسیتِ زمینه", "Background sensitivity", "د شالید حساسیت")
  val groupCrop: String get() = t("برش", "Crop", "پرې کول")
  val autoCrop: String get() = t("برش و هم‌اندازه کردن (مربع)", "Crop and square", "پرې کول او مربع کول")
  val margin: String get() = t("فاصله تا لبهٔ کادر ٪", "Margin to edge %", "تر څنډې واټن ٪")
  val groupQuality: String get() = t("کیفیت", "Quality", "کیفیت")
  val enhance: String get() = t("بهبودِ کیفیت (نور، رنگ، وضوح)", "Enhance (light, colour, clarity)", "کیفیت ښه کول (رڼا، رنګ، روښانتیا)")
  val sharpen: String get() = t("تیزی", "Sharpness", "تېزوالی")
  val brightness: String get() = t("روشنایی", "Brightness", "رڼا")
  val rotate: String get() = t("چرخش", "Rotate", "تاوول")
  val reset: String get() = t("پیش‌فرض", "Defaults", "اصلي حالت")
  val saveRaw: String get() = t("ذخیرهٔ بی ویرایش", "Save without edits", "پرته له سمون خوندي کړئ")
  val processing: String get() = t("در حالِ ساختن…", "Processing…", "جوړېږي…")
  val unreadable: String get() = t(
    "این عکس خوانده نشد. عکسِ دیگری انتخاب کنید (JPG یا PNG).",
    "This photo couldn't be read. Choose another one (JPG or PNG).",
    "دا انځور ونه لوستل شو. بل انځور وټاکئ (JPG یا PNG).",
  )
}
