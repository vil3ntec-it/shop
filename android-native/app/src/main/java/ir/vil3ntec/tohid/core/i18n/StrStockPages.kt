package ir.vil3ntec.tohid.core.i18n

/**
 *  دفترِ موجودی (`ui/screens/StockBook.kt`): جایگاه‌ها، دفترِ حرکت، بررسی، شمارشِ موجودی با بازبینی، انتقال بین محل‌ها و نوارِ
 *  خلاصه. این نوشته‌ها پیش‌تر متنِ خامِ فارسی بودند و از هیچ زبانی نمی‌گذشتند (LOC-08 دری، دورِ ۳). فارسی کلمه‌به‌کلمه همان
 *  پیشین است؛ هر جا همان جمله در جدولِ دیگری بود، همان دوباره به کار رفته. نامِ پروندهٔ CSV (خروجی) و کلیدِ بخشِ تنظیمات در
 *  خودِ صفحه «// داده:» است. همچنین پنجره‌های قدیمِ کالا، ورود و اصلاحِ موجودی (`WarehouseDialogs.kt`). قاعده‌ها سرِ `I18n.kt`.
 */
@Suppress("unused")
object StrStockPages {
  // ── `StockBook.kt` ──

  // زبانه‌ها و سرِ صفحه
  val tabPositions: String get() = t("جایگاه‌ها", "Positions", "ځایونه")
  val tabLedger: String get() = t("دفترِ حرکت", "Movement ledger", "د خوځښت دفتر")
  val engineOff: String get() = t("موتورِ تازهٔ موجودی و بها خاموش است", "The new stock and cost engine is off", "د موجودۍ او بیې نوی انجن بند دی")
  val engineOffBody: String get() = t(
    "بهای میانگینِ موزون، سری و انقضا، دفترِ حرکت، شمارش با بازبینی و انتقال بین محل‌ها با روشن کردنِ «موجودی و بهای تمام‌شده» در تنظیمات می‌آیند.",
    "Weighted average cost, lots and expiry, the movement ledger, counts with review and moves between places arrive when you turn on “Stock and cost” in Settings.",
    "منځنۍ وزن لرونکې بیه، لړۍ او د پای نېټه، د خوځښت دفتر، له کتنې سره شمېرنه او د ځایونو ترمنځ لېږد هغه وخت راځي چې په تنظیماتو کې «موجودي او تمامه بیه» روښانه کړئ.",
  )
  val notOpenYet: String get() = t(
    "این بخش هنوز برای دکانِ شما باز نشده است؛ انبار و فروش مثلِ همیشه کار می‌کنند.",
    "This section isn't open for your shop yet; stock and sales work as always.",
    "دا برخه لا ستاسو د دوکان لپاره نه ده پرانیستل شوې؛ ګدام او پلور د تل په څېر کار کوي.",
  )
  val exported: String get() = t("خروجی ساخته شد", "Export created", "وتنه جوړه شوه")
  val costNote: String get() = t(
    "بها به میانگینِ موزون و «تقریبی» است تا روشِ بها قطعی شود (D-23).",
    "Cost is a weighted average and “approximate” until the costing method is decided (D-23).",
    "بیه منځنۍ وزن لرونکې او «نږدې» ده تر هغه چې د بیې طریقه ټاکل شي (D-23).",
  )
  val stockValue: String get() = t("ارزشِ موجودی", "Stock value", "د موجودۍ ارزښت")
  val atAvgApprox: String get() = t("به بهای میانگین، تقریبی", "At average cost, approximate", "په منځنۍ بیه، نږدې")
  val negativeItems: String get() = t("کالای با موجودیِ منفی", "Items with negative stock", "منفي موجودي لرونکي توکي")
  val openTasks: String get() = t("وظیفهٔ بررسیِ باز", "Open review tasks", "پرانیستې د کتنې دندې")
  val negOrExpired: String get() = t("موجودیِ منفی یا فروشِ منقضی", "Negative stock or expired sale", "منفي موجودي یا تېر شوی پلور")
  val expiredLots: String get() = t("سریِ منقضیِ دارای موجودی", "Expired lots with stock", "تېرې شوې لړۍ چې موجودي لري")
  val reviewedSaved: String get() = t("ثبت شد: بررسی شد", "Saved: reviewed", "ثبت شو: وکتل شو")
  fun countSaved(n: Any?): String = t(
    "شمارش ثبت شد — ${asIs(n)} اختلاف اصلاح شد",
    "Count saved — $n ${pl(n ?: 0, "difference", "differences")} corrected",
    "شمېرنه ثبت شوه — $n توپیرونه سم شول",
  )

  // جایگاه‌ها
  val avgUnitCost: String get() = t("بهای میانگینِ واحد", "Average unit cost", "د یوې دانې منځنۍ بیه")
  val value: String get() = t("ارزش", "Value", "ارزښت")
  val places: String get() = t("محل‌ها", "Places", "ځایونه")
  val lotsFefo: String get() = t("سری‌ها (FEFO)", "Lots (FEFO)", "لړۍ (FEFO)")
  val searchItem: String get() = t("جستجوی کالا…", "Search items…", "توکي ولټوئ…")
  val allPlaces: String get() = t("همهٔ محل‌ها", "All places", "ټول ځایونه")
  val fefoNote: String get() = t(
    "ترتیبِ برداشتِ فروش (FEFO): انقضای زودتر اول؛ سری‌های بی‌تاریخ بعد از تاریخ‌دارها و به ترتیبِ دریافت. سریِ منقضی فروخته نمی‌شود؛ سریِ اسکن‌شده سرِ صندوق اول برداشته می‌شود.",
    "Sale pick order (FEFO): earliest expiry first; lots without a date come after dated ones, in order received. Expired lots aren't sold; a lot scanned at the till is taken first.",
    "د پلور د اخیستلو ترتیب (FEFO): لومړی هغه چې ژر پای ته رسېږي؛ بې نېټې لړۍ له نېټه لرونکو وروسته او د ترلاسه کېدو په ترتیب. تېره شوې لړۍ نه پلورل کېږي؛ په دخل کې سکن شوې لړۍ لومړی اخیستل کېږي.",
  )
  val noItemsFilter: String get() = t("کالایی با این فیلتر نیست", "No items match this filter", "له دې چاڼ سره توکی نشته")
  val negative: String get() = t("منفی", "Negative", "منفي")

  // دفترِ حرکت
  val provisionalCost: String get() = t("بهای موقت", "Provisional cost", "لنډمهاله بیه")
  fun adjusted(amount: Any?): String = t(" (تعدیل ${asIs(amount)})", " (adjusted $amount)", " (سمون $amount)")
  val allItems: String get() = t("همهٔ کالاها", "All items", "ټول توکي")
  val allTypes: String get() = t("همهٔ نوع‌ها", "All types", "ټول ډولونه")
  val allUsers: String get() = t("همهٔ کاربرها", "All users", "ټول کاروونکي")
  fun ledgerNote(n: Any?): String = t(
    "${asIs(n)} حرکت · فقط افزودنی: هیچ ردیفی ویرایش یا پاک نمی‌شود؛ اصلاح یک سندِ تازه است.",
    "$n ${pl(n ?: 0, "movement", "movements")} · append-only: no row is edited or deleted; a correction is a new document.",
    "$n خوځښتونه · یوازې زیاتېدونکی: هېڅ قطار نه سمېږي او نه پاکېږي؛ سمون یو نوی سند دی.",
  )
  val noMovesFilter: String get() = t("حرکتی با این فیلتر نیست", "No movements match this filter", "له دې چاڼ سره خوځښت نشته")
  fun moreRows(n: Any?): String = t("${asIs(n)} ردیفِ دیگر", "$n more ${pl(n ?: 0, "row", "rows")}", "$n نور قطارونه")

  // بررسی
  val reviewNote: String get() = t(
    "فروشِ ثبت‌شده هرگز رد نمی‌شود: وقتی موجودی منفی شد یا از سریِ منقضی فروخته شد، این‌جا یک وظیفه ساخته می‌شود تا مدیر بشمارد یا ورود را ثبت کند.",
    "A recorded sale is never rejected: when stock goes negative or an expired lot is sold, a task is created here so a manager can count or record the receipt.",
    "ثبت شوی پلور هېڅکله نه ردېږي: کله چې موجودي منفي شي یا له تېرې شوې لړۍ وپلورل شي، دلته یوه دنده جوړېږي چې مدیر یې وشمېري یا راتګ ثبت کړي.",
  )
  val alsoReviewed: String get() = t("بررسی‌شده‌ها هم", "Reviewed ones too", "کتل شوي هم")
  val noOpenTasks: String get() = t(
    "وظیفهٔ بازی نیست — موجودیِ منفی یا فروشِ منقضی پیدا نشد",
    "No open tasks — no negative stock or expired sales found",
    "پرانیستې دنده نشته — منفي موجودي یا تېر شوی پلور ونه موندل شو",
  )
  val shortage: String get() = t("کمبود", "Shortage", "کمښت")
  val stockAfterDoc: String get() = t("موجودی پس از سند", "Stock after document", "له سند وروسته موجودي")

  // شمارش
  val countTitle: String get() = t("شمارشِ موجودی", "Stock count", "د موجودۍ شمېرنه")
  val countNote: String get() = t(
    "موجودیِ برنامهٔ همین لحظه یادداشت می‌شود و اختلاف نسبت به همان حساب می‌شود؛ پس فروش یا خریدِ وسطِ شمارش اختلافِ دروغ نمی‌سازد. می‌شود فقط یک دسته یا چند کالا را شمرد. کالاهای شمرده‌نشده دست نمی‌خورند.",
    "The app's stock at this moment is noted and differences are measured against it, so a sale or purchase in the middle of the count doesn't create false differences. You can count just one category or a few items. Uncounted items aren't touched.",
    "د همدې شېبې د اپ موجودي یادېږي او توپیر له همغې سره حسابېږي، نو د شمېرنې په منځ کې پلور یا پېرود دروغجن توپیر نه جوړوي. یوازې یوه ډله یا څو توکي هم شمېرل کېدای شي. نه شمېرل شوي توکي نه بدلېږي.",
  )
  val startCount: String get() = t("شروعِ شمارش", "Start count", "شمېرنه پیل کړئ")
  fun startedAt(time: Any?): String = t("شروع: ${asIs(time)}", "Started: $time", "پیل: $time")
  val matched: String get() = t("منطبق", "Matched", "سمون لري")
  val variance: String get() = t("مغایر", "Different", "توپیر لري")
  val willSave: String get() = t("ثبت می‌شود", "Will be saved", "ثبتېږي")
  val uncounted: String get() = t("شمرده‌نشده", "Uncounted", "نه شمېرل شوي")
  val untouched: String get() = t("دست نمی‌خورند", "Not touched", "نه بدلېږي")
  val financialEffect: String get() = t("اثرِ مالی (تقریبی)", "Financial effect (approx.)", "مالي اغېز (نږدې)")
  val searchNameBarcode: String get() = t("جستجو با نام یا بارکد…", "Search by name or barcode…", "په نوم یا بارکوډ لټون…")
  val uncountedNote: String get() = t(
    "کالاهای شمرده‌نشده دست نمی‌خورند؛ صفر کردنشان فقط با شمردنِ صفر.",
    "Uncounted items aren't touched; zeroing them only by counting zero.",
    "نه شمېرل شوي توکي نه بدلېږي؛ صفر کول یې یوازې د صفر په شمېرلو.",
  )
  val increase: String get() = t("افزایش", "Increase", "زیاتوالی")
  val countFix: String get() = t("اصلاحِ شمارش", "Count correction", "د شمېرنې سمون")
  val dropCount: String get() = t("کنار گذاشتنِ شمارش", "Set the count aside", "شمېرنه یوې خوا ته کړئ")
  fun saveDiffs(n: Any?): String = t("ثبتِ ${asIs(n)} اختلاف", "Save $n ${pl(n ?: 0, "difference", "differences")}", "$n توپیرونه ثبت کړئ")
  fun saveDiffsAsk(n: Any?): String = t(
    "ثبتِ ${asIs(n)} اختلافِ شمارش؟",
    "Save $n count ${pl(n ?: 0, "difference", "differences")}?",
    "د شمېرنې $n توپیرونه ثبت شي؟",
  )
  val dropAsk: String get() = t("شمارش کنار گذاشته شود؟", "Set the count aside?", "شمېرنه یوې خوا ته شي؟")
  fun saveDiffsBody(value: Any?, uncounted: Any?): String = t(
    "اثرِ مالیِ تقریبی ${asIs(value)} افغانی. هر اختلاف یک سندِ اصلاح با کاربر، دلیل و زمان می‌شود. ${asIs(uncounted)} کالای شمرده‌نشده دست نمی‌خورد.",
    "Approximate financial effect $value AFN. Each difference becomes a correction document with user, reason and time. $uncounted uncounted ${pl(uncounted ?: 0, "item isn't", "items aren't")} touched.",
    "نږدې مالي اغېز $value افغانۍ. هر توپیر د کاروونکي، دلیل او وخت سره د سمون سند کېږي. $uncounted نه شمېرل شوي توکي نه بدلېږي.",
  )
  val dropBody: String get() = t(
    "عددهای شمرده‌شده پاک می‌شوند؛ چیزی در دفتر ثبت نشده است.",
    "The counted numbers are cleared; nothing has been recorded in the ledger.",
    "شمېرل شوې شمېرې پاکېږي؛ په دفتر کې څه نه دي ثبت شوي.",
  )
  val saveIt: String get() = t("ثبت کن", "Save", "ثبت یې کړئ")
  val setAside: String get() = t("کنار بگذار", "Set aside", "یوې خوا ته یې کړئ")

  // انتقال بین محل‌ها
  val transferNote: String get() = t(
    "موجودیِ کلِ کالا عوض نمی‌شود؛ فقط جایش. بها به میانگینِ مبدأ می‌رود.",
    "The item's total stock doesn't change, only its place. Cost moves at the source's average.",
    "د توکي ټوله موجودي نه بدلېږي؛ یوازې ځای یې. بیه د سرچینې په منځنۍ بیه ځي.",
  )
  val fromList: String get() = t("از فهرست", "From list", "له لړلیک څخه")
  val newPlaceBtn: String get() = t("+ محلِ تازه", "+ New place", "+ نوی ځای")
  val byFefo: String get() = t("به ترتیبِ FEFO", "In FEFO order", "د FEFO په ترتیب")

  // نوارِ خلاصه
  val stockValueAvg: String get() = t("ارزشِ موجودی به بهای میانگین", "Stock value at average cost", "په منځنۍ بیه د موجودۍ ارزښت")
  val approxD23: String get() = t("تقریبی (D-23)", "Approximate (D-23)", "نږدې (D-23)")
  val allReviewed: String get() = t("همه بررسی شده", "All reviewed", "ټول کتل شوي")
  val settingsTitle: String get() = t("موجودی و بها", "Stock & cost", "موجودي او بیه")

  // ── `WarehouseDialogs.kt` (پنجره‌های قدیمِ کالا، ورود و اصلاح؛ «کمبودهای آشکارشده» تا جای‌گیریِ کاملِ نسخهٔ ۳ ماندند) ──
  val editProduct: String get() = t("ویرایش محصول", "Edit product", "د توکي سمون")
  val itemName: String get() = t("نام کالا", "Item name", "د توکي نوم")
  val sameItemTail: String get() = t(" — اگر همان کالاست، ثبتِ دوباره نکنید.", " — if it's the same item, don't add it again.", " — که همغه توکی وي، بیا یې مه ثبتوئ.")
  val priceReasonKept: String get() = t(
    "دلیلِ تغییرِ قیمت (با نامِ شما در سابقه می‌ماند)",
    "Reason for the price change (kept in the history with your name)",
    "د بیې د بدلون لامل (ستاسو له نوم سره په تاریخچه کې پاتې کېږي)",
  )
  val subcategoryOpt: String get() = t("زیردسته (اختیاری)", "Subcategory (optional)", "فرعي ډله (اختیاري)")
  val brandOpt: String get() = t("برند (اختیاری)", "Brand (optional)", "برانډ (اختیاري)")
  val secondUnitOpt: String get() = t("واحدِ فروشِ دوم (اختیاری)", "Second sale unit (optional)", "د پلور دویم واحد (اختیاري)")
  val supplierOpt: String get() = t("تأمین‌کننده (اختیاری)", "Supplier (optional)", "عرضه کوونکی (اختیاري)")
  val saleStatus: String get() = t("وضعیت در فروش", "Sale status", "په پلور کې حالت")
  val receiveGoods: String get() = t("ثبت ورود کالا", "Record goods in", "د توکو راتګ ثبتول")
  fun makesQty(q: Any?): String = t("${asIs(q)} می‌شود", "Makes $q", "$q کېږي")
  val expiryOpt: String get() = t("تاریخ انقضا (اختیاری)", "Expiry date (optional)", "د پای نېټه (اختیاري)")
  val batchOpt: String get() = t("سری ساخت (اختیاری)", "Batch number (optional)", "د جوړښت لړۍ (اختیاري)")
  val madeOpt: String get() = t("تاریخ تولید (اختیاری)", "Production date (optional)", "د تولید نېټه (اختیاري)")
  val landedOpt: String get() = t(
    "هزینهٔ جانبی (کرایه، گمرک) — اختیاری، کلِ همین ورود",
    "Extra costs (freight, customs) — optional, for this whole delivery",
    "اضافي لګښت (کرایه، ګمرک) — اختیاري، د همدې ټول راتګ",
  )
  val landedHint: String get() = t(
    "به بهای همین کالا اضافه می‌شود (میانگینِ موزون)؛ جدا در «مصارف» ثبتش نکنید.",
    "It is added to this item's cost (weighted average); don't record it separately under “Expenses”.",
    "د همدې توکي په بیه ورزیاتېږي (وزن لرونکې منځنۍ)؛ جلا یې په «لګښتونه» کې مه ثبتوئ.",
  )
  val storagePlace: String get() = t("محلِ نگه‌داری", "Storage place", "د ساتنې ځای")
  val newPlace: String get() = t("محلِ تازه", "New place", "نوی ځای")
  val supplierReturnHint: String get() = t(
    "برگشت به تأمین‌کننده: از «خرید و تأمین‌کننده» ⇐ همان تأمین‌کننده ⇐ «برگشت (سندِ کسر)»؛ هم کالا کم می‌شود هم بدهی.",
    "Return to supplier: from “Purchases & suppliers” ⇐ that supplier ⇐ “Return (credit note)”; both the stock and the debt go down.",
    "عرضه کوونکي ته بېرته: له «پېرود او عرضه‌کوونکي» ⇐ همغه عرضه کوونکی ⇐ «بېرته (د کسر سند)»؛ توکي هم کمېږي او پور هم.",
  )
  val adjustKind: String get() = t("نوعِ اصلاح", "Adjustment type", "د سمون ډول")
  val wasteHint: String get() = t(
    "خراب و منقضی در گزارشِ ضایعات جمع می‌شوند.",
    "Damaged and expired items add up in the waste report.",
    "خراب او تېرنېټه توکي د زیان په راپور کې راټولېږي.",
  )
}
