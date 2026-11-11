package ir.vil3ntec.tohid.core.i18n

/**
 *  خرید در صفحه‌های قدیمیِ `ui/screens` که نسخهٔ ۳ صدا می‌زند: سفارشِ خرید (`PurchaseOrders.kt`: فهرست، جزئیات، ویرایشگر، دریافتِ
 *  کالا)، فاکتورِ تأمین‌کننده (`PurchaseBills.kt`)، پیشنهادِ سفارش (`PurchaseSuggestView.kt`) و خرید و تأمین‌کنندگانِ کامپیوتر
 *  (`DeskPurchasing.kt`). این نوشته‌ها پیش‌تر متنِ خامِ فارسی بودند و از هیچ زبانی نمی‌گذشتند (LOC-08 دری، دورِ ۳). فارسی
 *  کلمه‌به‌کلمه همان پیشین است؛ هر جا همان جمله در جدولِ دیگری بود، همان دوباره به کار رفته. یادداشتی که با سفارش یا ردیفِ دفتر
 *  ذخیره می‌شود داده است و در صفحه «// داده:» دارد. قاعده‌ها سرِ `I18n.kt`.
 */
@Suppress("unused")
object StrPurchasePages {
  // ── `PurchaseOrders.kt` ──

  // فهرستِ سفارش‌های خرید
  val purchaseOrders: String get() = t("سفارش‌های خرید", "Purchase orders", "د پېرود فرمایشونه")
  val list: String get() = t("فهرست", "List", "لړلیک")
  val draft: String get() = t("پیش‌نویس", "Draft", "مسوده")
  val sentOpen: String get() = t("فرستاده/باز", "Sent/open", "لېږل شوی/پرانیستی")
  val newPo: String get() = t("+ سفارشِ خریدِ تازه", "+ New purchase order", "+ نوی د پېرود فرمایش")
  val reorderSuggest: String get() = t("پیشنهادِ سفارش", "Reorder suggestions", "د فرمایش وړاندیز")
  val noPoYet: String get() = t("هنوز سفارشِ خریدی نیست.", "No purchase orders yet.", "لا د پېرود فرمایش نشته.")
  fun poLine(rows: Any?, ordered: Any?, remaining: Any?): String = t(
    "${asIs(rows)} ردیف · سفارش ${asIs(ordered)} ؋ · باقی‌مانده ${asIs(remaining)} ؋",
    "$rows ${pl(rows ?: 0, "row", "rows")} · ordered $ordered ؋ · remaining $remaining ؋",
    "$rows قطارونه · فرمایش $ordered ؋ · پاتې $remaining ؋",
  )
  fun arrivesSuffix(date: Any?): String = t(" · رسیدن ${asIs(date)}", " · arrives $date", " · رسېدل $date")

  // جزئیاتِ سفارش
  val noLabelPrinterShort: String get() = t(
    "هنوز چاپگری «برچسبِ کالا» تعریف نشده — «A4» یا «دستگاه‌ها»",
    "No “Product labels” printer is set yet — “A4” or “Devices”",
    "لا د «توکي لیبل» چاپګر نه دی ټاکل شوی — «A4» یا «وسیلې»",
  )
  val noAcceptedForLabel: String get() = t("کالای پذیرفته‌ای برای برچسب نیست", "No accepted items to label", "د لیبل لپاره منل شوی توکی نشته")
  fun billPosted(no: Any?): String = t(
    "فاکتورِ خریدِ #${asIs(no)} در دفتر ثبت شد",
    "Purchase invoice #$no posted to the ledger",
    "د پېرود بل #$no په دفتر کې ثبت شو",
  )
  val alreadyPosted: String get() = t(
    "این رسید پیش‌تر در دفتر ثبت شده است",
    "This receipt was already posted to the ledger",
    "دا رسید مخکې په دفتر کې ثبت شوی دی",
  )
  val ledgerNotWritten: String get() = t(
    "دفتر نوشته نشد (اشتراک یا دیسک)",
    "The ledger wasn't written (subscription or disk)",
    "دفتر ونه لیکل شو (ګډون یا ډیسک)",
  )
  /** ۳-۱۷: زیرِ ردیفی که از «پیشنهاد با فروش و زمانِ تأمین» آمد — اطمینان و دلیلِ همان پیشنهادِ صفحهٔ «پیشنهادِ سفارش» */
  fun planWhy(confidence: String, why: String): String = t(
    "پیشنهاد (${asIs(confidence)}): ${asIs(why)}", "Suggested ($confidence): $why", "وړاندیز ($confidence): $why",
  )
  fun arrivesOn(date: Any?): String = t("رسیدن: ${asIs(date)}", "Arrives: $date", "رسېدل: $date")
  fun cancelReasonIs(reason: Any?): String = t("دلیلِ لغو: ${asIs(reason)}", "Cancel reason: $reason", "د لغوې دلیل: $reason")
  val accepted: String get() = t("پذیرفته", "Accepted", "منل شوی")
  val damaged: String get() = t("خراب", "Damaged", "خراب")
  fun poTotals(ordered: Any?, received: Any?, remaining: Any?): String = t(
    "سفارش ${asIs(ordered)} ؋ · رسیده ${asIs(received)} ؋ · باقی‌مانده ${asIs(remaining)} ؋",
    "Ordered $ordered ؋ · received $received ؋ · remaining $remaining ؋",
    "فرمایش $ordered ؋ · رسېدلي $received ؋ · پاتې $remaining ؋",
  )
  val receiveGoods: String get() = t("دریافتِ کالا", "Receive goods", "د توکو ترلاسه کول")
  val poSent: String get() = t("سفارش فرستاده شد", "Order sent", "فرمایش ولېږل شو")
  val sendToSupplier: String get() = t("فرستادن به تأمین‌کننده", "Send to supplier", "عرضه‌کوونکي ته لېږل")
  val supplierBill: String get() = t("فاکتورِ تأمین‌کننده", "Supplier bill", "د عرضه‌کوونکي بل")
  val damagedReturned: String get() = t(
    "خراب و برگشت در همان رسید: ",
    "Damaged and returned on the same receipt: ",
    "په همدې رسید کې خراب او بېرته: ",
  )
  val labelBtn: String get() = t("برچسب", "Label", "لیبل")
  val inLedger: String get() = t("در دفتر", "In ledger", "په دفتر کې")
  val postToLedger: String get() = t("ثبت در دفتر", "Post to ledger", "په دفتر کې ثبت")
  fun cancelPoAsk(no: Any?): String = t("لغوِ سفارشِ #${asIs(no)}؟", "Cancel order #$no?", "د #$no فرمایش لغوه شي؟")
  val cancelOpenNote: String get() = t(
    "باقی‌ماندهٔ سفارش بسته می‌شود؛ رسیدهای ثبت‌شده می‌مانند.",
    "The rest of the order is closed; recorded receipts remain.",
    "د فرمایش پاتې برخه تړل کېږي؛ ثبت شوي رسیدونه پاتې کېږي.",
  )
  val cancelDraftNote: String get() = t("پیش‌نویس کنار می‌رود.", "The draft is set aside.", "مسوده یوې خوا ته کېږي.")
  val poCancelled: String get() = t("سفارش لغو شد", "Order cancelled", "فرمایش لغوه شو")
  val cancelIt: String get() = t("لغو کن", "Cancel it", "لغوه یې کړئ")

  // ویرایشگرِ سفارش
  fun alreadyInPo(name: Any?): String = t("«${asIs(name)}» پیش‌تر در سفارش هست", "“$name” is already in the order", "«$name» مخکې په فرمایش کې شته")
  val noLowFromSupplier: String get() = t(
    "کالای کم‌موجودی از این تأمین‌کننده نیست",
    "No low-stock items from this supplier",
    "له دې عرضه‌کوونکي کم موجودي لرونکی توکی نشته",
  )
  val noNewFromSupplier: String get() = t(
    "کالای تازه‌ای از این تأمین‌کننده نیست",
    "No new items from this supplier",
    "له دې عرضه‌کوونکي نوی توکی نشته",
  )
  val nothingToOrder: String get() = t(
    "با فروشِ ۳۰ روز و زمانِ تأمین، کالایی از این تأمین‌کننده سفارش نمی‌خواهد",
    "With 30 days of sales and lead time, no item from this supplier needs ordering",
    "د ۳۰ ورځو پلور او د رسولو وخت سره، له دې عرضه‌کوونکي هېڅ توکی فرمایش نه غواړي",
  )
  val chooseSupplier: String get() = t("تأمین‌کننده را انتخاب کنید", "Choose a supplier", "عرضه‌کوونکی وټاکئ")
  fun rowBadQty(row: Any?): String = t("ردیفِ ${asIs(row)}: مقدار درست نیست", "Row $row: quantity isn't valid", "$row قطار: اندازه سمه نه ده")
  fun rowBadPrice(row: Any?): String = t("ردیفِ ${asIs(row)}: فی درست نیست", "Row $row: unit price isn't valid", "$row قطار: د یوه بیه سمه نه ده")
  val needOneItem: String get() = t("دست‌کم یک کالا لازم است", "At least one item is needed", "لږ تر لږه یو توکی اړین دی")
  fun poInvalid(code: Any?): String = t("سفارش درست نیست (${asIs(code)})", "The order isn't valid ($code)", "فرمایش سم نه دی ($code)")
  val arrivalDateFormat: String get() = t(
    "تاریخِ رسیدن را به شکلِ ۲۰۲۶-۱۱-۰۵ بنویسید",
    "Write the arrival date like 2026-11-05",
    "د رسېدو نېټه داسې ولیکئ: ۲۰۲۶-۱۱-۰۵",
  )
  fun draftCreated(no: Any?): String = t("پیش‌نویسِ سفارشِ #${asIs(no)} ساخته شد", "Draft order #$no created", "د #$no فرمایش مسوده جوړه شوه")
  val newPoTitle: String get() = t("سفارشِ خریدِ تازه", "New purchase order", "نوی د پېرود فرمایش")
  fun editPo(no: Any?): String = t("ویرایشِ سفارشِ #${asIs(no)}", "Edit order #$no", "د #$no فرمایش سمون")
  val makeSupplierFirst: String get() = t("اول یک تأمین‌کننده بسازید.", "Create a supplier first.", "لومړی یو عرضه‌کوونکی جوړ کړئ.")
  fun leadManual(days: Any?): String = t(
    "زمانِ تأمینِ این تأمین‌کننده: ${asIs(days)} روز (دستی)",
    "This supplier's lead time: $days ${pl(days ?: 0, "day", "days")} (manual)",
    "د دې عرضه‌کوونکي د رسولو وخت: $days ورځې (لاسي)",
  )
  fun leadMeasured(days: Any?, samples: Any?): String = t(
    "زمانِ تأمینِ این تأمین‌کننده: ${asIs(days)} روز (میانهٔ ${asIs(samples)} سفارشِ رسیده)",
    "This supplier's lead time: $days ${pl(days ?: 0, "day", "days")} (median of $samples received ${pl(samples ?: 0, "order", "orders")})",
    "د دې عرضه‌کوونکي د رسولو وخت: $days ورځې (د $samples رسېدلو فرمایشونو منځنی)",
  )
  fun leadUnknown(days: Any?): String = t(
    "زمانِ تأمینِ این تأمین‌کننده هنوز سنجیده نشده — ${asIs(days)} روز فرض می‌شود",
    "This supplier's lead time isn't measured yet — $days days assumed",
    "د دې عرضه‌کوونکي د رسولو وخت لا نه دی اندازه شوی — $days ورځې فرض کېږي",
  )
  val suggestBySales: String get() = t("پیشنهاد با فروش و زمانِ تأمین", "Suggest from sales and lead time", "د پلور او د رسولو وخت له مخې وړاندیز")
  val lowFromSupplier: String get() = t("کم‌موجودهای این تأمین‌کننده", "This supplier's low-stock items", "د دې عرضه‌کوونکي کم موجودي توکي")
  val allFromSupplier: String get() = t("همهٔ کالاهای این تأمین‌کننده", "All items from this supplier", "د دې عرضه‌کوونکي ټول توکي")
  fun qtyIn(unit: Any?): String = t("مقدار ${asIs(unit)}", "Quantity $unit", "اندازه $unit")
  val arrivalOptional: String get() = t("تاریخِ رسیدن (اختیاری)", "Arrival date (optional)", "د رسېدو نېټه (اختیاري)")
  fun poTotal(total: Any?, rows: Any?): String = t(
    "جمعِ سفارش: ${asIs(total)} ؋ · ${asIs(rows)} ردیف",
    "Order total: $total ؋ · $rows ${pl(rows ?: 0, "row", "rows")}",
    "د فرمایش ټولټال: $total ؋ · $rows قطارونه",
  )
  val saveDraft: String get() = t("ذخیرهٔ پیش‌نویس", "Save draft", "مسوده خوندي کول")
  fun priceHistoryOf(name: Any?): String = t("تاریخچهٔ قیمتِ «${asIs(name)}»", "Price history of “$name”", "د «$name» د بیې تاریخچه")
  val noPurchaseYet: String get() = t("هنوز خریدی ثبت نشده است.", "No purchase recorded yet.", "لا هېڅ پېرود نه دی ثبت شوی.")
  fun cheapestLast(supplier: Any?, price: Any?): String = t(
    "ارزان‌ترین آخرین فی: ${asIs(supplier)} ${asIs(price)} ؋",
    "Cheapest last unit price: $supplier $price ؋",
    "تر ټولو ارزانه وروستۍ بیه: $supplier $price ؋",
  )
  fun stockOf(qty: Any?, unit: Any?): String = t("موجودی ${asIs(qty)} ${asIs(unit)}", "Stock $qty $unit", "موجودي $qty $unit")

  // دریافتِ کالا
  fun notInPo(code: Any?): String = t("«${asIs(code)}» در این سفارش نیست", "“$code” isn't in this order", "«$code» په دې فرمایش کې نشته")
  fun rowDamagedOver(row: Any?): String = t(
    "ردیفِ ${asIs(row)}: خراب بیش از رسیده است",
    "Row $row: damaged is more than received",
    "$row قطار: خراب له رسېدلي زیات دی",
  )
  val nothingEntered: String get() = t("هیچ مقداری وارد نشده است", "No quantity entered", "هېڅ اندازه نه ده ورکړل شوې")
  val freightBad: String get() = t("کرایه درست نیست", "Freight isn't valid", "کرایه سمه نه ده")
  val receiptSaved: String get() = t("رسید ثبت شد", "Receipt saved", "رسید ثبت شو")
  fun receiptPosted(no: Any?): String = t(
    "رسید ثبت شد · فاکتورِ خریدِ #${asIs(no)} در دفتر",
    "Receipt saved · purchase invoice #$no in the ledger",
    "رسید ثبت شو · د پېرود بل #$no په دفتر کې",
  )
  fun receiptNoLedger(error: Any?): String = t(
    "رسید ثبت شد، ولی دفتر نه: ${asIs(error)}",
    "Receipt saved, but not the ledger: $error",
    "رسید ثبت شو، خو دفتر نه: $error",
  )
  val receiptLedgerFailed: String get() = t(
    "رسید ثبت شد، ولی دفتر نوشته نشد (اشتراک یا دیسک) — از «ثبت در دفتر» دوباره",
    "Receipt saved, but the ledger wasn't written (subscription or disk) — retry from “Post to ledger”",
    "رسید ثبت شو، خو دفتر ونه لیکل شو (ګډون یا ډیسک) — له «په دفتر کې ثبت» بیا هڅه وکړئ",
  )
  fun receiveTitle(no: Any?, supplier: Any?): String = t(
    "دریافتِ کالا — سفارشِ #${asIs(no)} · ${asIs(supplier)}",
    "Receive goods — order #$no · $supplier",
    "د توکو ترلاسه کول — فرمایش #$no · $supplier",
  )
  val receiveNote: String get() = t(
    "کالا را اسکن کنید یا مقدار را بنویسید. خراب‌ها پذیرفته نمی‌شوند و باقی‌ماندهٔ سفارش باز می‌ماند.",
    "Scan items or type the quantity. Damaged items aren't accepted and the rest of the order stays open.",
    "توکي سکن کړئ یا اندازه ولیکئ. خراب توکي نه منل کېږي او د فرمایش پاتې برخه پرانیستې پاتې کېږي.",
  )
  fun remainingOf(name: Any?, qty: Any?, unit: Any?): String = t(
    "${asIs(name)} — باقی‌مانده ${asIs(qty)} ${asIs(unit)}",
    "$name — remaining $qty $unit",
    "$name — پاتې $qty $unit",
  )
  val received: String get() = t("رسیده", "Received", "رسېدلي")
  val acceptOver: String get() = t(
    "بیش از سفارش را هم می‌پذیرم (اختلافِ «اضافه» ثبت می‌شود)",
    "I also accept more than ordered (an “extra” difference is recorded)",
    "له فرمایش زیات هم منم (د «اضافه» توپیر ثبتېږي)",
  )
  val freightField: String get() = t("کرایهٔ این رسید (؋)", "Freight for this receipt (؋)", "د دې رسید کرایه (؋)")
  val freightNote: String get() = t(
    "به حمل‌کننده، نه تأمین‌کننده: به نسبتِ ارزشِ کالاهای پذیرفته روی بهایشان پخش می‌شود و بدهیِ تأمین‌کننده را بالا نمی‌برد.",
    "To the carrier, not the supplier: spread over the accepted items' cost by value; it doesn't raise the supplier's balance.",
    "حمل‌کوونکي ته، نه عرضه‌کوونکي ته: د منل شوو توکو د ارزښت په تناسب د هغوی پر بیه وېشل کېږي او د عرضه‌کوونکي پور نه لوړوي.",
  )

  // ── `PurchaseBills.kt` ──

  // فاکتورِ تأمین‌کننده
  val billNoNeeded: String get() = t("شمارهٔ فاکتورِ تأمین‌کننده را بنویسید", "Write the supplier's invoice number", "د عرضه‌کوونکي د بل شمېره ولیکئ")
  val billRowBad: String get() = t("مقدار یا مبلغِ یک ردیف درست نیست", "A row's quantity or amount isn't valid", "د یوه قطار اندازه یا مبلغ سم نه دی")
  val billNeedRow: String get() = t(
    "دست‌کم یک ردیف در فاکتور لازم است",
    "At least one row is needed on the bill",
    "په بل کې لږ تر لږه یو قطار اړین دی",
  )
  fun billSaved(no: Any?): String = t("فاکتورِ «${asIs(no)}» ثبت شد", "Bill “$no” saved", "«$no» بل ثبت شو")
  fun billTitle(no: Any?, supplier: Any?): String = t(
    "فاکتورِ تأمین‌کننده — سفارشِ #${asIs(no)} · ${asIs(supplier)}",
    "Supplier bill — order #$no · $supplier",
    "د عرضه‌کوونکي بل — فرمایش #$no · $supplier",
  )
  val billNote: String get() = t(
    "همان چیزی را بنویسید که روی فاکتورِ کاغذیِ تأمین‌کننده است؛ برنامه آن را با سفارش و رسیدها تطبیق می‌دهد.",
    "Write exactly what's on the supplier's paper invoice; the app matches it against the order and receipts.",
    "هماغه څه ولیکئ چې د عرضه‌کوونکي پر کاغذي بل دي؛ اپ یې له فرمایش او رسیدونو سره پرتله کوي.",
  )
  val billNo: String get() = t("شمارهٔ فاکتور", "Invoice number", "د بل شمېره")
  val dateIso: String get() = t("تاریخ (YYYY-MM-DD)", "Date (YYYY-MM-DD)", "نېټه (YYYY-MM-DD)")
  fun qtyUnit(unit: Any?): String = t("مقدار (${asIs(unit)})", "Quantity ($unit)", "اندازه ($unit)")
  val rowTotalAfn: String get() = t("جمعِ ردیف (؋)", "Row total (؋)", "د قطار ټولټال (؋)")
  val saveBill: String get() = t("ثبتِ فاکتور", "Save invoice", "بل ثبتول")
  fun voidedWhy(reason: Any?): String = t("باطل: ${asIs(reason)}", "Voided: $reason", "باطل: $reason")
  val voidBtn: String get() = t("باطل", "Void", "باطل")
  fun voidAsk(no: Any?): String = t("باطل کردنِ فاکتورِ «${asIs(no)}»؟", "Void invoice “$no”?", "«$no» بل باطل شي؟")
  val voidNote: String get() = t(
    "فاکتور پاک نمی‌شود؛ با دلیل خط می‌خورد و در تطبیق به حساب نمی‌آید. شماره‌اش برای فاکتورِ درست آزاد می‌شود.",
    "The invoice isn't deleted; it's struck through with a reason and doesn't count in matching. Its number is freed for the correct invoice.",
    "بل نه پاکېږي؛ له دلیل سره کرښه پرې کېږي او په پرتله کې نه حسابېږي. شمېره یې د سم بل لپاره خلاصېږي.",
  )
  val voidIt: String get() = t("باطل کن", "Void it", "باطل یې کړئ")

  // ── `PurchaseSuggestView.kt` ──

  // پیشنهادِ سفارش
  val noNeedFromSupplier: String get() = t(
    "از این تأمین‌کننده کالایی سفارش نمی‌خواهد",
    "No item from this supplier needs ordering",
    "له دې عرضه‌کوونکي هېڅ توکی فرمایش نه غواړي",
  )
  fun suggestInvalid(code: Any?): String = t("پیشنهاد درست نیست (${asIs(code)})", "The suggestion isn't valid ($code)", "وړاندیز سم نه دی ($code)")
  fun draftCreatedNotSent(no: Any?): String = t(
    "پیش‌نویسِ سفارشِ #${asIs(no)} ساخته شد — هنوز فرستاده نشده",
    "Draft order #$no created — not sent yet",
    "د #$no فرمایش مسوده جوړه شوه — لا نه ده لېږل شوې",
  )
  val allDaysLeft: String get() = t("همه: چند روز کافی است", "All: how many days is enough", "ټول: څو ورځې بس دي")
  val suggestNote1: String get() = t(
    "از فروشِ ۹۰ روزِ اخیر (روزِ هفته و همین روزهای پارسال)، زمانِ تأمینِ هر تأمین‌کننده و «در راه». ",
    "From the last 90 days of sales (weekday and the same days last year), each supplier's lead time and “on the way”. ",
    "د وروستیو ۹۰ ورځو له پلور (د اونۍ ورځ او د تېر کال همدا ورځې)، د هر عرضه‌کوونکي د رسولو وخت او «په لار کې». ",
  )
  val suggestNote2: String get() = t(
    "«ساختِ پیش‌نویس» فقط پیش‌نویس می‌سازد؛ فرستادن با خودتان است.",
    "“Create draft” only creates a draft; sending is up to you.",
    "«مسوده جوړول» یوازې مسوده جوړوي؛ لېږل ستاسو کار دی.",
  )
  val suggestNoteSmart: String get() = t(
    " پیش‌بینیِ تقاضا روشن است: پرفروش با میانگینِ متحرکِ ۲۸ روز، کم‌فروش با Croston؛ اطمینان از درازای داده و نوسان.",
    " Demand forecast is on: fast sellers by a 28-day moving average, slow sellers by Croston; confidence from data length and variation.",
    " د غوښتنې وړاندوینه روښانه ده: ګړندي پلورل کېدونکي د ۲۸ ورځو د خوځنده منځني له مخې، ورو پلورل کېدونکي د Croston له مخې؛ باور د معلوماتو له اوږدوالي او بدلون څخه.",
  )
  val nothingDueToday: String get() = t("امروز کالایی سفارش نمی‌خواهد.", "No item needs ordering today.", "نن هېڅ توکی فرمایش نه غواړي.")
  val noActiveItems: String get() = t("کالای فعالی نیست.", "No active items.", "فعال توکی نشته.")
  val noSupplier: String get() = t("بی تأمین‌کننده", "No supplier", "بې عرضه‌کوونکي")
  fun createDraftN(n: Any?): String = t(
    "ساختِ پیش‌نویس (${asIs(n)} کالا)",
    "Create draft ($n ${pl(n ?: 0, "item", "items")})",
    "مسوده جوړول ($n توکي)",
  )
  val noSupplierNote: String get() = t(
    "تأمین‌کننده‌ای روی این کالاها نیست و از کسی خریده نشده‌اند؛ سفارش را دستی بسازید.",
    "These items have no supplier and weren't bought from anyone; create the order by hand.",
    "دا توکي عرضه‌کوونکی نه لري او له چا نه دي پېرل شوي؛ فرمایش په لاس جوړ کړئ.",
  )
  fun enoughFor(text: Any?): String = t("کافی برای: ${asIs(text)}", "Enough for: $text", "بس دی د: $text")
  fun suggestQty(qty: Any?, unit: Any?): String = t(" · پیشنهاد: ${asIs(qty)} ${asIs(unit)}", " · suggested: $qty $unit", " · وړاندیز: $qty $unit")
  fun next7Days(text: Any?): String = t("۷ روزِ آینده: ${asIs(text)}", "Next 7 days: $text", "راتلونکې ۷ ورځې: $text")

  // ── `DeskPurchasing.kt` ──

  // خرید و تأمین‌کنندگانِ کامپیوتر
  fun barcodeUnknown(code: Any?): String = t(
    "بارکدِ «${asIs(code)}» ثبت نشده — اول در «کالاها» بسازید",
    "Barcode “$code” isn't registered — create it in “Items” first",
    "«$code» بارکوډ نه دی ثبت — لومړی یې په «توکي» کې جوړ کړئ",
  )
  val purchaseInvoices: String get() = t("فاکتورهای خرید", "Purchase invoices", "د پېرود بلونه")
  val suppliers: String get() = t("تأمین‌کنندگان", "Suppliers", "عرضه‌کوونکي")
  val newPurchaseKey: String get() = t("+ فاکتورِ خریدِ تازه  Ctrl+N", "+ New purchase invoice  Ctrl+N", "+ نوی د پېرود بل  Ctrl+N")
  val thisMonthPurchases: String get() = t("خریدِ این ماه", "This month's purchases", "د دې میاشتې پېرود")
  val weOweSuppliers: String get() = t("بدهیِ ما به تأمین‌کنندگان", "What we owe suppliers", "چې موږ یې عرضه‌کوونکو ته پوروړي یو")
  fun supplierFilter(name: Any?): String = t("تأمین‌کننده: ${asIs(name)}", "Supplier: $name", "عرضه‌کوونکی: $name")
  val listExcel: String get() = t("اکسلِ فهرست", "List as Excel", "لړلیک په اکسل")
  val rows: String get() = t("ردیف‌ها", "Rows", "قطارونه")
  val noPurchasesYet: String get() = t(
    "هنوز خریدی ثبت نشده — «فاکتورِ خریدِ تازه» (Ctrl+N).",
    "No purchases yet — “New purchase invoice” (Ctrl+N).",
    "لا پېرود نه دی ثبت شوی — «نوی د پېرود بل» (Ctrl+N).",
  )
  val returnToSupplier: String get() = t("برگشت به تأمین‌کننده (سندِ کسر)", "Return to supplier (credit note)", "عرضه‌کوونکي ته بېرته (د کسر سند)")
  fun purchaseInvoiceNo(no: Any?): String = t("فاکتورِ خرید #${asIs(no)}", "Purchase invoice #$no", "د پېرود بل #$no")
  fun dueOn(date: Any?): String = t("سررسید ${asIs(date)}", "Due $date", "نېټه $date")
  fun unitPriceUnit(price: Any?, unit: Any?): String = t("فی ${asIs(price)} · ${asIs(unit)}", "Unit price $price · $unit", "د یوه بیه $price · $unit")
  val onSupplierAccount: String get() = t("مانده به حسابِ تأمین‌کننده", "Balance on the supplier's account", "د عرضه‌کوونکي پر حساب پاتې")
  fun expiryBad(date: Any?): String = t(
    "تاریخِ انقضای «${asIs(date)}» درست نیست — مثلاً ۱۴۰۶/۰۳/۳۱",
    "Expiry date “$date” isn't valid — e.g. 1406/03/31",
    "د پای نېټه «$date» سمه نه ده — لکه ۱۴۰۶/۰۳/۳۱",
  )
  fun invoiceDateBad(example: Any?): String = t(
    "تاریخِ فاکتور درست نیست — مثلاً ${asIs(example)}",
    "The invoice date isn't valid — e.g. $example",
    "د بل نېټه سمه نه ده — لکه $example",
  )
  val landedBad: String get() = t("هزینهٔ جانبی درست نیست", "The extra cost isn't valid", "اضافي لګښت سم نه دی")
  fun dueBad(example: Any?): String = t(
    "سررسید درست نیست — مثلاً ${asIs(example)}",
    "The due date isn't valid — e.g. $example",
    "د ورکړې نېټه سمه نه ده — لکه $example",
  )
  fun purchaseSaved(no: Any?, total: Any?): String = t(
    "فاکتورِ خرید #${asIs(no)} ثبت شد — ${asIs(total)} ؋، کالاها به انبار رفت",
    "Purchase invoice #$no saved — $total ؋, the items went into stock",
    "د پېرود بل #$no ثبت شو — $total ؋، توکي ګدام ته لاړل",
  )
  fun newPurchaseNo(no: Any?): String = t("فاکتورِ خریدِ تازه #${asIs(no)}", "New purchase invoice #$no", "نوی د پېرود بل #$no")
  val purchaseKeys: String get() = t(
    "Enter ردیفِ بعد · F9 ثبت · Ctrl+I تاریخچهٔ قیمت · Esc انصراف · بارکدخوان ردیف می‌افزاید",
    "Enter next row · F9 save · Ctrl+I price history · Esc cancel · the barcode scanner adds a row",
    "Enter بل قطار · F9 ثبت · Ctrl+I د بیې تاریخچه · Esc لغوه · بارکوډ لوستونکی قطار زیاتوي",
  )
  val supplierColon: String get() = t("تأمین‌کننده: ", "Supplier: ", "عرضه‌کوونکی: ")
  val orNewSupplier: String get() = t("یا تأمین‌کنندهٔ تازه + Enter", "Or a new supplier + Enter", "یا نوی عرضه‌کوونکی + Enter")
  val dateSolar: String get() = t("تاریخ (خورشیدی)", "Date (solar)", "نېټه (لمریز)")
  val itemNameBarcode: String get() = t("کالا (نام یا بارکد)", "Item (name or barcode)", "توکی (نوم یا بارکوډ)")
  val expiryOptional: String get() = t("انقضا (اختیاری)", "Expiry (optional)", "د پای نېټه (اختیاري)")
  val notesSellerNo: String get() = t("یادداشت / شمارهٔ فاکتورِ فروشنده", "Note / seller's invoice number", "یادښت / د پلورونکي د بل شمېره")
  val paidAfn: String get() = t("پرداخت‌شده (؋)", "Paid (؋)", "ورکړل شوي (؋)")
  val landedAfn: String get() = t("هزینهٔ جانبی (؋)", "Extra cost (؋)", "اضافي لګښت (؋)")
  val landedNote: String get() = t(
    "کرایه، گمرک — روی بهای ردیف‌ها؛ جدا در «مصارف» ثبتش نکنید",
    "Freight, customs — added to the rows' cost; don't record it separately in “Expenses”",
    "کرایه، ګمرک — د قطارونو پر بیه؛ په «لګښتونو» کې یې جلا مه ثبتوئ",
  )
  val payDue: String get() = t("سررسیدِ پرداخت", "Payment due date", "د ورکړې نېټه")
  val optionalReminder: String get() = t("اختیاری — یادآور", "Optional — reminder", "اختیاري — یادونه")
  fun invoiceTotal(total: Any?): String = t("جمعِ فاکتور: ${asIs(total)} ؋", "Invoice total: $total ؋", "د بل ټولټال: $total ؋")
  fun toSupplierAccount(amount: Any?): String = t(
    "به حسابِ تأمین‌کننده: ${asIs(amount)} ؋",
    "To the supplier's account: $amount ؋",
    "د عرضه‌کوونکي حساب ته: $amount ؋",
  )
  val saveInvoiceKey: String get() = t("ثبتِ فاکتور  F9", "Save invoice  F9", "بل ثبتول  F9")
  fun pickRow(name: Any?, qty: Any?, unit: Any?): String = t(
    "${asIs(name)}  ·  موجودی ${asIs(qty)} ${asIs(unit)}",
    "$name  ·  stock $qty $unit",
    "$name  ·  موجودي $qty $unit",
  )
  val newSupplierName: String get() = t("تأمین‌کنندهٔ تازه — نام", "New supplier — name", "نوی عرضه‌کوونکی — نوم")
  val paySupplierKey: String get() = t("پرداخت به تأمین‌کننده  F7", "Pay supplier  F7", "عرضه‌کوونکي ته ورکړه  F7")
  val returnCredit: String get() = t("برگشت (سندِ کسر)", "Return (credit note)", "بېرته (د کسر سند)")
  val overdue: String get() = t("سررسیدگذشته", "Overdue", "نېټه تېره")
  val creditNoteReturn: String get() = t("سندِ کسر (برگشت)", "Credit note (return)", "د کسر سند (بېرته)")
  fun paymentSaved(amount: Any?): String = t("پرداختِ ${asIs(amount)} ؋ ثبت شد", "Payment of $amount ؋ saved", "د $amount ؋ ورکړه ثبت شوه")
  fun payTo(name: Any?): String = t("پرداخت به «${asIs(name)}»", "Payment to “$name”", "«$name» ته ورکړه")
  val noBuyOrPay: String get() = t("هنوز خرید یا پرداختی نیست.", "No purchases or payments yet.", "لا پېرود یا ورکړه نشته.")
}
