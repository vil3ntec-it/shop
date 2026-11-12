package ir.vil3ntec.tohid.core.i18n

/**
 *  صندوق، فاکتور و ابزارهای صندوق در صفحه‌ها و جزءهای قدیمیِ `ui/screens` و `ui/components/pos` که نسخهٔ ۳ هنوز صدا
 *  می‌زند: برگه‌های فروش و چاپ (`SaleDialogs`)، دریافتِ وجه از مشتری (`TillReceiveDialog`، `TillReceivePhoneAndroid`،
 *  `DeskSale`)، ثبتِ سریع و واحدِ بزرگ (`TillGoodsSettingsAndroid`، `QuickRegisterDialog`)، یادداشتِ فاکتور، ترازو، لیستِ قیمت و
 *  صفحه‌کلیدِ عدد. این نوشته‌ها پیش‌تر متنِ خامِ فارسی بودند و از هیچ زبانی نمی‌گذشتند. فارسی کلمه‌به‌کلمه همان پیشین است؛
 *  هر جا همان جمله در جدولِ دیگری بود، همان دوباره به کار رفته. قاعده‌ها سرِ `I18n.kt`.
 */
@Suppress("unused")
object StrSalePages {
  // ── `SaleDialogs.kt` ──

  // برگه‌های فروش و چاپ
  val settleSale: String get() = t("تسویه فروش", "Settle sale", "د پلور تصفیه")
  val discountType: String get() = t("نوع تخفیف", "Discount type", "د تخفیف ډول")
  val byAmount: String get() = t("مبلغی", "Amount", "مبلغ")
  fun overYourCap(pct: Any?): String = t("بیش از سقفِ ${asIs(pct)}٪ِ شما", "Above your $pct% limit", "ستاسو له $pct٪ حد څخه زیات")
  val paymentType: String get() = t("نوع پرداخت", "Payment type", "د تادیې ډول")
  val nobodyYet: String get() = t("— هنوز کسی ثبت نشده —", "— nobody added yet —", "— لا څوک نه دی ثبت شوی —")
  val chooseDebtor: String get() = t("انتخاب قرض‌دار", "Choose debtor", "پوروړی وټاکئ")
  val chooseCustomer: String get() = t("انتخاب مشتری", "Choose customer", "پېرودونکی وټاکئ")
  fun discountOnInvoice(discount: Any?, subtotal: Any?): String = t(
    "تخفیفِ ${asIs(discount)} ؋ روی فاکتورِ ${asIs(subtotal)} ؋",
    "Discount $discount ؋ on an invoice of $subtotal ؋",
    "د $subtotal ؋ په بل $discount ؋ تخفیف",
  )
  fun salesInvoiceNo(no: Any?): String = t("فاکتور فروش #${asIs(no)}", "Sales invoice #$no", "د پلور بل #$no")
  val linkCopied: String get() = t("پیوندِ فاکتورِ آنلاین کپی شد", "Online invoice link copied", "د آنلاین بل لینک کاپي شو")
  val copyLink: String get() = t("کپیِ پیوند", "Copy link", "لینک کاپي کول")
  val paper58: String get() = t("۵۸ میلی‌متر", "58 mm", "۵۸ ملي متره")
  val paper80: String get() = t("۸۰ میلی‌متر", "80 mm", "۸۰ ملي متره")
  fun qtyIn(unit: Any?): String = t("تعداد (${asIs(unit)})", "Qty ($unit)", "شمېر ($unit)")
  fun listPriceTail(unit: Any?, price: Any?): String = t(
    " · ${asIs(unit)} ${asIs(price)} ؋ (لیستِ قیمت)",
    " · $unit $price ؋ (price list)",
    " · $unit $price ؋ (د بیې لېست)",
  )
  val manualPrice: String get() = t("قیمت دستی برای این ردیف", "Manual price for this line", "د دې کرښې لاسي بیه")
  fun ownPriceNote(price: Any?): String = t(
    "قیمت خودِ کالا ${asIs(price)} ؋ است. این تغییر فقط روی همین فاکتور اثر دارد.",
    "The item's own price is $price ؋. This change affects only this invoice.",
    "د توکي خپله بیه $price ؋ ده. دا بدلون یوازې په همدې بل اغېز لري.",
  )
  fun lineTotal(total: Any?): String = t("جمع این ردیف: ${asIs(total)} ؋", "Line total: $total ؋", "د دې کرښې ټولګه: $total ؋")
  val lineNote: String get() = t("یادداشتِ ردیف", "Line note", "د کرښې یادښت")
  val record: String get() = t("ثبت", "Save", "ثبت")
  fun total(total: Any?): String = t("جمع: ${asIs(total)} ؋", "Total: $total ؋", "ټولګه: $total ؋")

  // ── `TillReceiveDialog.kt` ──

  // دریافتِ وجه یا قسط از مشتری
  val afnSuffix: String get() = t(" افغانی", " AFN", " افغانۍ")
  fun receiveFrom(name: Any?): String = t("دریافت از «${asIs(name)}»", "Receive from \"$name\"", "له «$name» څخه ترلاسه کول")
  fun owes(amount: Any?): String = t("بدهی: ${asIs(amount)}", "Owes: $amount", "پور: $amount")
  fun prepaidTail(amount: Any?): String = t(" — پیش‌پرداخت: ${asIs(amount)}", " — prepaid: $amount", " — مخکې تادیه: $amount")
  val amountReceived: String get() = t("مبلغِ دریافتی", "Amount received", "ترلاسه شوی مبلغ")
  val descOptional: String get() = t("شرح (اختیاری)", "Description (optional)", "تشریح (اختیاري)")
  fun overDebt(amount: Any?): String = t(
    "${asIs(amount)} بیش از بدهی است و پیش‌پرداختِ مشتری ثبت می‌شود.",
    "$amount is more than the debt and is recorded as the customer's prepayment.",
    "$amount له پور څخه زیات دی او د پېرودونکي د مخکې تادیې په توګه ثبتېږي.",
  )
  fun leftAfter(amount: Any?): String = t(
    "مانده پس از دریافت: ${asIs(amount)}",
    "Balance after receiving: $amount",
    "تر ترلاسه کولو وروسته پاتې: $amount",
  )
  val recordPrepay: String get() = t("ثبتِ پیش‌پرداخت", "Record prepayment", "مخکې تادیه ثبت کړئ")
  val recordReceipt: String get() = t("ثبتِ دریافت", "Record payment", "ترلاسه کول ثبت کړئ")

  // ── `TillReceivePhoneAndroid.kt` ──
  val receiveTitle: String get() = t(
    "دریافتِ وجه یا قسط از مشتری",
    "Receive money or an instalment from a customer",
    "له پېرودونکي پیسې یا قسط ترلاسه کول",
  )
  fun received(amount: Any?, name: Any?): String = t(
    "دریافتِ ${asIs(amount)} ؋ از «${asIs(name)}» ثبت شد",
    "Received $amount ؋ from \"$name\"",
    "له «$name» څخه د $amount ؋ ترلاسه کول ثبت شو",
  )

  // ── `DeskSale.kt` ──
  fun lotAdded(name: Any?, lot: Any?): String = t(
    "${asIs(name)} — سری ${asIs(lot)} به سبد اضافه شد",
    "$name — batch $lot added to the cart",
    "$name — سري $lot ټوکرۍ ته زیات شو",
  )

  // ── `TillGoodsSettingsAndroid.kt` ──

  // ثبتِ سریع و واحدِ بزرگ در صندوق (عنوان و خطِ صفحهٔ تنظیماتِ نسخهٔ ۳)
  val tillGoodsNote: String get() = t(
    "ثبتِ سریع (S03): بارکدِ ناشناس در صندوق ⇒ «ثبتِ سریعِ کالا» با همان بارکد، و کالا همان‌جا به فاکتور می‌رود؛ فقط برای نقشی که کالا ثبت می‌کند — صندوق‌دار فقط «قلمِ آزاد». واحدِ بزرگ (S05): Ctrl+U و برگهٔ ردیف واحدهای کالا (واحدِ دوم و بسته‌ها) را با قیمتِ بسته از لیستِ قیمت نشان می‌دهند؛ اگر قیمتِ بسته نوشته نشده، اندازه × قیمتِ واحد.",
    "Quick add (S03): an unknown barcode at the till ⇒ \"Quick add item\" with the same barcode, and the item goes straight onto the invoice; only for roles that add items — a cashier gets only \"custom item\". Big unit (S05): Ctrl+U and the line sheet show the item's units (second unit and packs) with the pack price from the price list; if no pack price is set, size × unit price.",
    "چټک ثبت (S03): په صندوق کې نامعلوم بارکوډ ⇒ «د توکي چټک ثبت» په همغه بارکوډ، او توکی سمدستي بل ته ځي؛ یوازې د هغو رولونو لپاره چې توکي ثبتوي — صندوقدار یوازې «ازاد توکی». لوی واحد (S05): Ctrl+U او د کرښې پاڼه د توکي واحدونه (دویم واحد او بستې) د بیې له لېست څخه د بستې له بیې سره ښيي؛ که د بستې بیه نه وي لیکل شوې، اندازه × د واحد بیه.",
  )
  val tillGoodsUnits: String get() = t("واحدِ بزرگ (Ctrl+U) و قیمتِ بسته", "Big unit (Ctrl+U) and pack price", "لوی واحد (Ctrl+U) او د بستې بیه")
  val tillGoodsPart: String get() = t(
    "فروشِ بخشی از بسته (مثلاً ۱٫۵ کارتن)",
    "Selling part of a pack (e.g. 1.5 cartons)",
    "د بستې د یوې برخې پلور (لکه ۱٫۵ کارټنه)",
  )
  val tillGoodsPartOk: String get() = t("مجاز", "Allowed", "اجازه شته")
  val tillGoodsPartNo: String get() = t("فقط بستهٔ کامل", "Whole packs only", "یوازې بشپړه بسته")

  // ── `PosNotesAndroid.kt` ──

  // یادداشتِ فاکتور
  fun noteLine(note: Any?): String = t("یادداشت: ${asIs(note)}", "Note: $note", "یادښت: $note")
  val invoiceNote: String get() = t("یادداشتِ فاکتور", "Invoice note", "د بل یادښت")
  val printedOnReceipt: String get() = t("روی رسید چاپ می‌شود", "Printed on the receipt", "په رسید چاپېږي")

  // ── `ScaleAtPosAndroid.kt` ──

  // ترازو در صندوق
  val scaleNotWeighed: String get() = t(
    "این کالا کیلویی یا گرمی نیست",
    "This item isn't sold by kilo or gram",
    "دا توکی په کیلو یا ګرام نه پلورل کېږي",
  )
  val scaleError: String get() = t("ترازو خطا می‌دهد", "The scale reports an error", "تله تېروتنه ښيي")
  val scaleOver: String get() = t("بیش از ظرفیتِ ترازو", "Over the scale's capacity", "د تلې له ظرفیت څخه زیات")
  val scaleUnder: String get() = t(
    "زیرِ ظرفیتِ ترازو — ترازو را صفر کنید",
    "Under the scale's range — zero the scale",
    "د تلې له ظرفیت لاندې — تله صفر کړئ",
  )
  val scaleMotion: String get() = t(
    "ترازو هنوز ایستا نیست — صبر کنید و دوباره بزنید",
    "The scale isn't steady yet — wait and tap again",
    "تله لا ولاړه نه ده — صبر وکړئ او بیا یې ووهئ",
  )
  val scaleEmpty: String get() = t("وزنی روی ترازو نیست", "Nothing on the scale", "په تله کې وزن نشته")
  val scaleReading: String get() = t("در حالِ خواندن…", "Reading…", "لوستل کېږي…")
  val scaleWeigh: String get() = t("وزن از ترازو", "Weight from scale", "وزن له تلې")
  val labelWeight: String get() = t("برچسبِ وزنی", "Weight label", "د وزن لیبل")
  val labelPrice: String get() = t("برچسبِ قیمتی", "Price label", "د بیې لیبل")
  fun itemDigits(n: Any?): String = t("کدِ کالا ${asIs(n)} رقم", "Item code $n digits", "د توکي کوډ $n رقمه")
  val priceLabelNote: String get() = t(
    "مقدار = مبلغ ÷ قیمتِ کالا، و برچسبی که مبلغِ ردیف را دقیقاً همان نکند رد می‌شود. ",
    "Quantity = amount ÷ item price, and a label that doesn't give exactly the line amount is rejected. ",
    "مقدار = مبلغ ÷ د توکي بیه، او هغه لیبل چې د کرښې مبلغ دقیقاً همغه نه کړي ردېږي. ",
  )
  fun digitsLayout(value: Any?, code: Any?): String = t(
    "${asIs(value)} رقمِ مقدار پس از ${asIs(code)} رقمِ کد",
    "$value value digits after $code code digits",
    "د کوډ له $code رقمونو وروسته د مقدار $value رقمونه",
  )
  fun sampleTail(sample: Any?): String = t("؛ نمونه: ${asIs(sample)}", "; sample: $sample", "؛ بېلګه: $sample")
  val scaleAtTill: String get() = t("وزن از ترازو در صندوق", "Weight from the scale at the till", "په صندوق کې وزن له تلې")
  val scaleAtTillNote: String get() = t(
    "دکمهٔ «وزن از ترازو» در کادرِ ردیفِ کالای کیلویی و گرمی؛ فقط وزنِ ایستا. انتخابِ همین دستگاه است.",
    "A \"Weight from scale\" button in the line box of kilo and gram items; steady weight only. This device's choice.",
    "د کیلو او ګرام توکو د کرښې په خانه کې د «وزن له تلې» تڼۍ؛ یوازې ولاړ وزن. د همدې وسیلې انتخاب دی.",
  )
  val valueAfn: String get() = t("مبلغ به افغانی", "Amount in afghanis", "مبلغ په افغانیو")
  val valueMoney2: String get() = t("مبلغ با دو رقمِ پول", "Amount with two money decimals", "مبلغ د پیسو له دوو رقمونو سره")
  val valueGram: String get() = t("وزن به گرم", "Weight in grams", "وزن په ګرامو")
  val valueTenGram: String get() = t("وزن به ده گرم", "Weight in tens of grams", "وزن په لسو ګرامو")

  // ── `PriceListAtPosAndroid.kt` ──

  // لیستِ قیمت در صندوق (عنوانِ بخش همان کلیدِ `SettingsLayout.PRICE_LIST` است و می‌ماند)
  val priceListNote: String get() = t(
    "روشن ⇒ مشتریِ «عمده» قیمتِ عمده می‌گیرد و نشانِ «لیستِ قیمت» در صندوق لیستِ همین فاکتور را عوض می‌کند؛ فیِ دستی دست نمی‌خورد.",
    "On ⇒ a \"wholesale\" customer gets the wholesale price and the \"price list\" badge at the till switches this invoice's list; a manual unit price is not touched.",
    "روښانه ⇒ «عمده» پېرودونکی عمده بیه اخلي او په صندوق کې د «بیې لېست» نښه د همدې بل لېست بدلوي؛ لاسي بیه نه بدلېږي.",
  )

  // ── `PhoneKeypad.kt` ──

  // ── `QuickRegisterDialog.kt` ──

  // ثبتِ سریعِ کالا
  val quickUnknown: String get() = t("این بارکد ثبت نیست", "This barcode isn't registered", "دا بارکوډ نه دی ثبت شوی")
  fun barcodeLine(code: Any?): String = t("بارکد: ${asIs(code)}", "Barcode: $code", "بارکوډ: $code")
  val saveAndAdd: String get() = t("ثبت و افزودن (Ctrl+S)", "Save and add (Ctrl+S)", "ثبت او زیاتول (Ctrl+S)")

  // ── `HesabPayAccountAndroid.kt` ──
  val cameraForScan: String get() = t(
    "برای اسکن، اجازهٔ دوربین لازم است.",
    "Camera permission is needed to scan.",
    "د سکن لپاره د کمرې اجازه اړینه ده.",
  )

  // ── `DayEndPanel.kt` ──

  // پایانِ روز (خلاصهٔ شیفت‌های روز)
  val dayEndNote: String get() = t(
    "هر شیفت مالِ روزی است که باز شد؛ Z ِ بسته ثابت است و پولی که هیچ شیفتی نشمرده جدا، در «بیرون از شیفت‌ها»، می‌آید.",
    "Each shift belongs to the day it was opened; a closed Z stays fixed, and money no shift counted comes separately under \"outside shifts\".",
    "هر شیفټ د هغې ورځې دی چې پکې خلاص شو؛ تړلی Z نه بدلېږي، او هغه پیسې چې هېڅ شیفټ نه دي شمېرلې جلا، په «له شیفټونو بهر» کې راځي.",
  )
  val shiftsNet: String get() = t("خالص شیفت‌ها", "Shifts net", "د شیفټونو خالص")
  val shiftsNetHow: String get() = t("فروش + رسید − برگشت", "Sales + receipts − returns", "پلور + رسید − ستنېدنې")
  val tillVariance: String get() = t("اختلاف صندوق", "Till difference", "د صندوق توپیر")
  val varianceHow: String get() = t("شمرده − مورد انتظار", "Counted − expected", "شمېرل شوي − تمه شوي")
  val outsideShifts: String get() = t("بیرون از شیفت‌ها", "Outside shifts", "له شیفټونو بهر")
  val outsideHow: String get() = t("دفتر − شیفت‌ها", "Books − shifts", "دفتر − شیفټونه")
  val shifts: String get() = t("شیفت‌ها", "Shifts", "شیفټونه")
  fun openCount(n: Any?): String = t("${asIs(n)} باز", "$n open", "$n خلاص")
  val allClosed: String get() = t("همه بسته", "All closed", "ټول تړلي")
  val endOfToday: String get() = t("پایان امروز", "End of today", "د نن پای")
  val detailNote: String get() = t(
    "جمعِ این ریز همان «خالص دفتر» ِ همین روش است.",
    "The total of this detail equals the \"books net\" of this method.",
    "د دې جزئیاتو ټولګه د همدې لارې «د دفتر خالص» دی.",
  )

  // ── `Sale.kt`/`QuickSale.kt` (فقط پیش‌نمایشِ گوشیِ `DevShot`؛ نسخهٔ ۳ صندوقِ `ui/v3/sales` را دارد) ──
  val addFreeItem: String get() = t("+ قلم آزاد", "+ Custom item", "+ آزاد توکی")
  val pickDebtorForCreditHint: String get() = t("برای فروشِ نسیه، قرض‌دار را انتخاب کنید", "For a credit sale, choose the debtor", "د پور پلور لپاره پوروړی وټاکئ")
  val removeDebtor: String get() = t("برداشتنِ قرض‌دار", "Remove debtor", "پوروړی لرې کول")
  val newCategory: String get() = t("دسته‌بندیِ تازه", "New category", "نوې ډله")
  val categoryNameHint: String get() = t("نام دسته‌بندی را بنویسید…", "Write the category name…", "د ډلې نوم ولیکئ…")
  val tapToAddOne: String get() = t("روی محصول بزنید تا یک عدد به سبد خرید اضافه شود", "Tap an item to add one to the cart", "پر توکي ټک وکړئ چې یو دانه ټوکرۍ ته زیات شي")
  val searchNameOrBarcode: String get() = t("جستجوی نام کالا یا بارکد", "Search item name or barcode", "د توکي نوم یا بارکوډ ولټوئ")
  val allCategories: String get() = t("همه دسته‌بندی‌ها", "All categories", "ټولې ډلې")
  val addCategoryChip: String get() = t("+ دسته‌بندی", "+ Category", "+ ډله")
  val deleteThisCategory: String get() = t("حذف این دسته", "Delete this category", "دا ډله ړنګول")
}
