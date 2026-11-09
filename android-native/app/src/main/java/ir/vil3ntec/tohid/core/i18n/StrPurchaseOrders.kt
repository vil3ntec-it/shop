package ir.vil3ntec.tohid.core.i18n

/**
 *  PUR-02/03/04/14 روی گوشیِ نسخهٔ ۳ — «خرید ⇐ سفارش‌های خرید» (`ui/v3/money/PurchaseOrderPages.kt`): حالت‌های صفحه، فهرست
 *  با پالایهٔ وضعیت، جزئیات، فرم، دریافت با اسکن و تطبیق. نوشته‌های مشترک با پنجرهٔ کامپیوتر همان `StrPurchasePages` است؛
 *  این‌جا فقط آنچه گوشی تازه می‌خواهد. قاعده‌ها سرِ `I18n.kt` (هر مقدار در فارسی از `asIs`).
 */
@Suppress("unused")
object StrPurchaseOrders {
  val note: String get() = t(
    "سفارش به تأمین‌کننده، دریافتِ کالا با اسکن و تطبیق با فاکتور. ساختن، فرستادن و لغو فقط کارِ مدیر و صاحب است.",
    "Orders to suppliers, receiving goods by scan and matching with the bill. Only managers and the owner create, send or cancel.",
    "عرضه‌کوونکي ته فرمایش، د توکو ترلاسه کول په سکن او له بل سره پرتله. جوړول، لېږل او لغوه یوازې د مدیر او خاوند کار دی.",
  )
  val offline: String get() = t("اینترنت وصل نیست", "No internet connection", "انټرنېټ نښلول شوی نه دی")
  val offlineBody: String get() = t(
    "سفارش‌های خرید روی سرور نگه‌داری می‌شوند. پس از وصل شدن، «دوباره» را بزنید.",
    "Purchase orders are kept on the server. Once connected, tap “Retry”.",
    "د پېرود فرمایشونه په سرور کې ساتل کېږي. له نښلېدو وروسته «بیا» کېکاږئ.",
  )
  val failedTitle: String get() = t("از سرور نیامد", "Could not load from the server", "له سرور څخه رانغی")
  val noneFiltered: String get() = t("سفارشی با این وضعیت نیست.", "No orders with this status.", "په دې حالت کې فرمایش نشته.")
  val emptyBody: String get() = t(
    "سفارشِ خرید را این‌جا بسازید و به تأمین‌کننده بفرستید؛ کالا که رسید، با اسکن دریافت کنید.",
    "Create a purchase order here and send it to the supplier; when the goods arrive, receive them by scan.",
    "د پېرود فرمایش دلته جوړ کړئ او عرضه‌کوونکي ته یې ولېږئ؛ کله چې توکي ورسېدل، په سکن یې ترلاسه کړئ.",
  )
  val manageOnly: String get() = t(
    "ساختن و ویرایشِ سفارشِ خرید فقط کارِ مدیر و صاحبِ دکان است.",
    "Only managers and the shop owner create or edit purchase orders.",
    "د پېرود فرمایش جوړول او سمول یوازې د مدیر او د دوکان د خاوند کار دی.",
  )
  val receiveDenied: String get() = t(
    "دریافتِ کالا برای نقشِ شما باز نیست.",
    "Receiving goods isn't open for your role.",
    "د توکو ترلاسه کول ستاسو د رول لپاره پرانیستي نه دي.",
  )
  val notDraft: String get() = t(
    "این سفارش دیگر پیش‌نویس نیست؛ ویرایش نمی‌شود.",
    "This order is no longer a draft; it can't be edited.",
    "دا فرمایش نور مسوده نه ده؛ نه سمېږي.",
  )
  val notReceivable: String get() = t(
    "این سفارش دریافت نمی‌گیرد: پیش‌نویس، کامل یا لغوشده است.",
    "This order can't take a receipt: it is a draft, complete or cancelled.",
    "دا فرمایش رسید نه اخلي: مسوده، بشپړ یا لغوه شوی دی.",
  )
  val rowsTitle: String get() = t("قلم‌های سفارش", "Order items", "د فرمایش توکي")
  val noRows: String get() = t(
    "هنوز قلمی نیست؛ کالا را بیفزایید یا از پیشنهادها بردارید.",
    "No items yet; add a product or take them from the suggestions.",
    "لا توکی نشته؛ توکی ورزیات کړئ یا یې له وړاندیزونو واخلئ.",
  )
  val addItem: String get() = t("افزودنِ کالا", "Add a product", "توکی ورزیاتول")
  fun removeRow(name: Any?): String = t("حذفِ «${asIs(name)}» از سفارش", "Remove “$name” from the order", "«$name» له فرمایش لرې کول")
  val arrivalHint: String get() = t("مثلاً 2026-11-20", "e.g. 2026-11-20", "لکه 2026-11-20")
  fun lineSub(ordered: Any?, unit: Any?, accepted: Any?, damaged: Any?, remaining: Any?, price: Any?): String = t(
    "سفارش ${asIs(ordered)} ${asIs(unit)} · پذیرفته ${asIs(accepted)} · خراب ${asIs(damaged)} · مانده ${asIs(remaining)} · فی ${asIs(price)} ؋",
    "Ordered $ordered $unit · accepted $accepted · damaged $damaged · left $remaining · unit price $price AFN",
    "فرمایش $ordered $unit · منل شوي $accepted · خراب $damaged · پاتې $remaining · د یوه بیه $price ؋",
  )
  fun receiptLine(at: Any?, total: Any?): String = t("${asIs(at)} · ${asIs(total)} ؋", "$at · $total AFN", "$at · $total ؋")
  val sendNote: String get() = t(
    "پس از فرستادن، سفارش ویرایش نمی‌شود؛ فقط دریافت یا لغو با دلیل.",
    "Once sent, the order can't be edited; only received or cancelled with a reason.",
    "له لېږلو وروسته فرمایش نه سمېږي؛ یوازې ترلاسه کېږي یا په دلیل لغوه کېږي.",
  )
  val reasonNeeded: String get() = t("دلیل لازم است", "A reason is required", "دلیل اړین دی")

  // ── دریافت با اسکن (PUR-04) ──
  val scanHint: String get() = t("بارکد را اسکن کنید یا بنویسید", "Scan or type a barcode", "بارکوډ سکن یا ولیکئ")
  val receiveAll: String get() = t("دریافتِ همهٔ باقی‌مانده", "Receive all remaining", "ټول پاتې ترلاسه کړئ")
  val filledAll: String get() = t(
    "«رسیده» ِ هر قلمِ باز همان باقی‌مانده شد؛ خراب را بنویسید و پیش از ثبت ببینید.",
    "Each open item's received is now its remaining; enter any damaged and check before saving.",
    "د هر پرانیستي توکي رسېدلی اوس د هغه پاتې دی؛ خراب ولیکئ او له ثبت مخکې یې وګورئ.",
  )
  fun scanned(name: Any?, received: Any?): String = t("«${asIs(name)}»: رسیده ${asIs(received)}", "“$name”: received $received", "«$name»: رسېدلي $received")
  fun leftAfter(left: Any?, unit: Any?): String = t(
    "پس از این رسید مانده ${asIs(left)} ${asIs(unit)} (باز می‌ماند)",
    "After this receipt $left $unit left (stays open)",
    "له دې رسید وروسته $left $unit پاتې (پرانیستی پاتې کېږي)",
  )
  val doneAfter: String get() = t("با این رسید این قلم کامل می‌شود", "This receipt completes the item", "دا رسید دا توکی بشپړوي")
  fun overAfter(over: Any?, unit: Any?): String = t(
    "${asIs(over)} ${asIs(unit)} بیش از باقی‌ماندهٔ سفارش",
    "$over $unit more than what is left on the order",
    "$over $unit د فرمایش له پاتې برخې زیات",
  )
  fun rowOver(row: Any?, left: Any?): String = t(
    "ردیفِ ${asIs(row)}: بیش از باقی‌ماندهٔ سفارش (${asIs(left)}) است؛ بیشتر را فقط مدیر با تیکِ «بیش از سفارش» می‌پذیرد",
    "Row $row: more than the order's remainder ($left); only a manager can accept extra, with the “more than ordered” tick",
    "$row قطار: د فرمایش له پاتې برخې ($left) زیات دی؛ زیات یوازې مدیر د «له فرمایش زیات» په نښه مني",
  )
  fun rowRefused(row: Any?, code: Any?): String = t("ردیفِ ${asIs(row)} پذیرفته نشد (${asIs(code)})", "Row $row was refused ($code)", "$row قطار ونه منل شو ($code)")

  // ── تطبیق (PUR-14) ──
  val match: String get() = t("تطبیقِ سفارش، رسید و فاکتور", "Match order, receipt and bill", "د فرمایش، رسید او بل پرتله")
  val matchNothing: String get() = t(
    "هنوز رسید یا فاکتوری نیست؛ چیزی برای تطبیق نیست.",
    "No receipt or bill yet; nothing to match.",
    "لا رسید یا بل نشته؛ د پرتلې لپاره څه نشته.",
  )
  val sideSupplier: String get() = t(
    "طرفِ سوم: فاکتورهای فعالِ تأمین‌کننده (مرزِ یک افغانی)",
    "Third side: the supplier's active bills (one-afghani margin)",
    "درېیم اړخ: د عرضه‌کوونکي فعال بلونه (د یوې افغانۍ پوله)",
  )
  val sideLedger: String get() = t(
    "طرفِ سوم: فاکتورِ خریدِ دفتر از همین رسیدها، چون تأمین‌کننده فاکتوری نداده",
    "Third side: the ledger's purchase invoice from these receipts, as the supplier gave no bill",
    "درېیم اړخ: د همدې رسیدونو د دفتر د پېرود بل، ځکه عرضه‌کوونکي بل نه دی ورکړی",
  )
  fun matchQty(ordered: Any?, accepted: Any?, billed: Any?, unit: Any?): String = t(
    "سفارش ${asIs(ordered)} · پذیرفته ${asIs(accepted)} · فاکتور ${asIs(billed)} ${asIs(unit)}",
    "Ordered $ordered · accepted $accepted · billed $billed $unit",
    "فرمایش $ordered · منل شوي $accepted · بل $billed $unit",
  )
  fun matchMoney(expected: Any?, billed: Any?): String = t(
    "به فیِ سفارش ${asIs(expected)} ؋ · در فاکتور ${asIs(billed)} ؋",
    "At the order price $expected AFN · on the bill $billed AFN",
    "د فرمایش په بیه $expected ؋ · په بل کې $billed ؋",
  )
  fun matchTotals(ordered: Any?, accepted: Any?, billed: Any?): String = t(
    "سفارش ${asIs(ordered)} ؋ · پذیرفته ${asIs(accepted)} ؋ · فاکتور ${asIs(billed)} ؋",
    "Ordered $ordered AFN · accepted $accepted AFN · billed $billed AFN",
    "فرمایش $ordered ؋ · منل شوي $accepted ؋ · بل $billed ؋",
  )
  fun differences(n: Int): String = t("${asIs(n)} ردیف اختلاف دارد", "$n ${pl(n, "row differs", "rows differ")}", "$n قطارونه توپیر لري")
  val flagged: String get() = t("اختلاف", "Difference", "توپیر")

  // PUR-13: برچسبِ اقلامِ رسیده (پس از ثبتِ رسید و کنارِ هر رسید)
  val labelsNote: String get() = t(
    "برای اقلامِ پذیرفتهٔ همین رسید برچسب چاپ کنید: هر واحد یکی، کالای وزنی یکی؛ «A4» برگهٔ ۳×۸ است.",
    "Print labels for the accepted items of this receipt: one per unit, one for weighed goods; “A4” is a 3×8 sheet.",
    "د دې رسید منل شوو توکو لپاره لیبل چاپ کړئ: هر واحد یو، وزني توکي یو؛ «A4» د ۳×۸ پاڼه ده.",
  )
}
