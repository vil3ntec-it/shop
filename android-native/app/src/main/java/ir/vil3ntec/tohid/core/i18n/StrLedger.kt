package ir.vil3ntec.tohid.core.i18n

/**
 *  پیام‌های لایهٔ دفتر (`data/`) — فروش، قرض و مشتری، پرداخت و صندوق، برگشت، فاکتورِ نگه‌داشته، فروشِ امانی و سفارشِ اینترنتی.
 *  پیش‌تر متنِ خامِ فارسی بودند و از هیچ زبانی نمی‌گذشتند (LOC-08 دری، دورِ ۷). فارسی بایت‌به‌بایت همان پیشین؛ هر مقدار با `asIs`.
 *  یادداشتِ سابقه، برگه و نامِ پروندهٔ خروجی و کلیدِ منطق داده‌اند و در خودِ پرونده با «// داده: …» مانده‌اند. قاعده‌ها سرِ `I18n.kt`.
 */
@Suppress("unused")
object StrLedger {

  // ── صندوق و کشو (`CashShift`) ──
  val openingBad: String get() = t("مبلغِ اولِ صندوق درست نیست", "The opening till amount isn't valid", "د دخل لومړنی مبلغ سم نه دی")
  val tillAlreadyOpen: String get() = t("صندوق از قبل باز است", "The till is already open", "دخل له مخکې خلاص دی")
  fun drawerNameNeeded(max: Any?): String = t(
    "نامِ کشو (۱ تا ${asIs(max)} نویسه) لازم است",
    "A drawer name (1 to $max characters) is required",
    "د دخل نوم (له ۱ تر $max تورو) پکار دی",
  )
  fun drawerAlreadyOpen(name: Any?): String = t(
    "کشوی «${asIs(name)}» از قبل باز است",
    "Drawer «$name» is already open",
    "«$name» دخل له مخکې خلاص دی",
  )
  val countedBad: String get() = t("مبلغِ شمرده‌شده درست نیست", "The counted amount isn't valid", "شمېرل شوی مبلغ سم نه دی")
  val noOpenTill: String get() = t("صندوقِ بازی نیست", "No till is open", "هېڅ دخل خلاص نه دی")

  // ── قرض‌دار و مشتری ──
  val debtorNotFound: String get() = t("قرض‌دار پیدا نشد", "Debtor not found", "پوروړی پیدا نه شو")
  fun debtorMergedInto(name: Any?, keep: Any?): String = t(
    "«${asIs(name)}» با «${asIs(keep)}» یکی شده است؛ برای نسیه «${asIs(keep)}» را انتخاب کنید",
    "«$name» was merged into «$keep»; choose «$keep» for credit",
    "«$name» له «$keep» سره یو شوی؛ د پور لپاره «$keep» وټاکئ",
  )
  val customerNotFound: String get() = t("مشتری پیدا نشد", "Customer not found", "پېرودونکی پیدا نه شو")
  val customerMerged: String get() = t(
    "این مشتری با مشتریِ دیگری ادغام شده است",
    "This customer was merged into another customer",
    "دا پېرودونکی له بل پېرودونکي سره یو شوی",
  )
  val secondPhoneSame: String get() = t(
    "شمارهٔ دوم همان شمارهٔ اول است",
    "The second number is the same as the first",
    "دوهمه شمېره له لومړۍ سره یوه ده",
  )
  fun maxTags(n: Any?): String = t("حداکثر ${asIs(n)} برچسب", "At most $n tags", "تر ټولو ډېر $n نښانونه")
  fun tagTooLong(n: Any?): String = t("هر برچسب حداکثر ${asIs(n)} حرف", "Each tag can have at most $n characters", "هره نښه تر ټولو ډېر $n توري")
  fun groupTooLong(n: Any?): String = t(
    "نامِ گروه حداکثر ${asIs(n)} حرف",
    "The group name can have at most $n characters",
    "د ډلې نوم تر ټولو ډېر $n توري",
  )
  val unknownPriceList: String get() = t("لیستِ قیمتِ ناشناخته", "Unknown price list", "نامعلوم د بیو لېست")
  fun addressPartTooLong(n: Any?): String = t(
    "هر بخشِ نشانی حداکثر ${asIs(n)} حرف",
    "Each part of the address can have at most $n characters",
    "د پتې هره برخه تر ټولو ډېر $n توري",
  )
  fun gateTooLong(n: Any?): String = t(
    "رنگِ دروازه حداکثر ${asIs(n)} حرف",
    "The gate colour can have at most $n characters",
    "د دروازې رنګ تر ټولو ډېر $n توري",
  )
  fun floorTooLong(n: Any?): String = t("طبقه حداکثر ${asIs(n)} حرف", "The floor can have at most $n characters", "پوړ تر ټولو ډېر $n توري")
  val plusCodeBad: String get() = t(
    "Plus Code درست نیست (مثلاً V75V+8Q)",
    "The Plus Code isn't valid (for example V75V+8Q)",
    "Plus Code سم نه دی (لکه V75V+8Q)",
  )
  /** CRM-09 — پینِ نشانیِ تحویل */
  val addressPinBad: String get() = t(
    "از این متن پین پیدا نشد؛ مختصات (مثلاً 34.5281, 69.1723) یا پیوندِ کاملِ برنامهٔ نقشه را بچسبانید",
    "No pin found in this text; paste coordinates (for example 34.5281, 69.1723) or the full link from a maps app",
    "له دې متن څخه پن ونه موندل شو؛ مختصات (لکه 34.5281, 69.1723) یا د نقشې له اپ څخه بشپړ لینک کېږدئ",
  )
  val postalDigitsOnly: String get() = t("کدِ پستی فقط رقم است", "The postal code is digits only", "پوستي کوډ یوازې عددونه دي")
  val consentSourceNeeded: String get() = t(
    "برای «بله» منبعِ رضایت را بنویسید (مثلاً «حضوری» یا «تلفنی»)",
    "For «Yes», write where the consent came from (for example «in person» or «by phone»)",
    "د «هو» لپاره د رضا سرچینه ولیکئ (لکه «حضوري» یا «تلیفوني»)",
  )
  val mergeSelf: String get() = t("یک مشتری با خودش ادغام نمی‌شود", "A customer can't be merged with itself", "یو پېرودونکی له ځان سره نه یوځای کېږي")
  val mergeKeepMissing: String get() = t("مشتریِ ماندگار پیدا نشد", "The customer to keep was not found", "پاتې کېدونکی پېرودونکی پیدا نه شو")
  val mergeDropMissing: String get() = t("مشتریِ تکراری پیدا نشد", "The duplicate customer was not found", "تکراري پېرودونکی پیدا نه شو")
  fun mergeKeepGone(name: Any?): String = t(
    "«${asIs(name)}» خودش پیش‌تر ادغام شده است",
    "«$name» was itself merged earlier",
    "«$name» پخپله مخکې یوځای شوی",
  )
  fun mergeDropGone(name: Any?): String = t("«${asIs(name)}» پیش‌تر ادغام شده است", "«$name» was merged earlier", "«$name» مخکې یوځای شوی")

  // ── رسیدِ قرض و دریافت (`DebtBook.receipt` — چاپ، PDF و پیام) ──
  fun overLimit(excess: Any?, limit: Any?, balance: Any?): String = t(
    "${asIs(excess)} افغانی بیش از سقفِ اعتبار (${asIs(limit)}) — ماندهٔ فعلی ${asIs(balance)}",
    "$excess afghani over the credit limit ($limit) — current balance $balance",
    "$excess افغانۍ د پور له حد ($limit) ډېر — اوسنی پاتې $balance",
  )
  val afnSuffix: String get() = t(" افغانی", " afghani", " افغانۍ")
  val rcCustomer: String get() = t("مشتری", "Customer", "پېرودونکی")
  val rcPhone: String get() = t("تلفن", "Phone", "تلیفون")
  val rcDate: String get() = t("تاریخ", "Date", "نېټه")
  val rcInvoice: String get() = t("فاکتور", "Invoice", "بل")
  fun rcReturned(qty: Any?, unit: Any?): String = t("   مرجوعی: ${asIs(qty)}${asIs(unit)}", "   returned: $qty$unit", "   بېرته: $qty$unit")
  val rcSubtotal: String get() = t("جمعِ اقلام", "Items total", "د توکو ټولګه")
  val rcDiscount: String get() = t("تخفیف", "Discount", "تخفیف")
  val rcPayable: String get() = t("قابلِ پرداخت", "Payable", "د ورکړې وړ")
  val rcPaidNow: String get() = t("پرداختِ همان لحظه", "Paid at the time", "په هماغه شېبه ورکړه")
  val rcThisDebt: String get() = t("قرضِ این ردیف", "Debt of this entry", "د دې کرښې پور")
  val rcDetail: String get() = t("شرح", "Details", "تشریح")
  val rcDue: String get() = t("سررسید", "Due date", "د ورکړې نېټه")
  val rcNoDue: String get() = t("ندارد", "None", "نشته")
  fun rcPayment(date: Any?): String = t("پرداخت ${asIs(date)}", "Payment $date", "ورکړه $date")
  val rcDebtLeft: String get() = t("باقیِ این قرض", "Left of this debt", "د دې پور پاتې")
  val rcSettled: String get() = t("تسویه شد", "Settled", "تصفیه شو")
  val rcStatus: String get() = t("وضعیت", "Status", "حالت")
  val rcOverdue: String get() = t("سررسید گذشته", "Overdue", "نېټه تېره شوې")
  val rcReceived: String get() = t("مبلغِ دریافتی", "Amount received", "ترلاسه شوی مبلغ")
  val rcBalanceBefore: String get() = t("مانده پیش از دریافت", "Balance before receipt", "له ترلاسه کولو مخکې پاتې")
  fun rcInvoiceNo(no: Any?): String = t("فاکتور #${asIs(no)}", "Invoice #$no", "بل #$no")
  fun rcGiven(date: Any?): String = t("برد ${asIs(date)}", "Credit given $date", "پور ورکړه $date")
  fun rcFor(what: Any?): String = t("بابتِ ${asIs(what)}", "For $what", "د $what لپاره")
  val rcPrepaid: String get() = t("پیش‌پرداخت (روی قرضِ بعدی)", "Prepayment (toward the next debt)", "مخکې ورکړه (د راتلونکي پور لپاره)")
  val rcBalanceAfter: String get() = t("مانده پس از این ردیف", "Balance after this entry", "له دې کرښې وروسته پاتې")
  val rcBalanceToday: String get() = t("ماندهٔ امروزِ حساب", "Today's account balance", "د حساب د نن ورځې پاتې")
  fun rcTitle(shop: Any?, kind: Any?): String = t("${asIs(shop)} — ${asIs(kind)}", "$shop — $kind", "$shop — $kind")
  val rcDebtReceipt: String get() = t("رسیدِ قرض", "Debt receipt", "د پور رسید")
  val rcPayReceipt: String get() = t("رسیدِ دریافت", "Payment receipt", "د ترلاسه کولو رسید")

  // ── قرض و قرض‌دار (`DebtEntry`، `DebtorEngine`، `DebtReminder`) ──
  val notSaved: String get() = t("ثبت نشد", "Not saved", "ثبت نه شو")
  val notReread: String get() = t(
    "دفترِ روی دیسک دوباره خوانده نشد، پس این ثبت انجام نشد تا چیزی از دفتر رونویسی نشود. دوباره امتحان کنید.",
    "The ledger on disk couldn't be read again, so this entry wasn't saved so nothing in the ledger is overwritten. Try again.",
    "په ډیسک کې دفتر بیا ونه لوستل شو، نو دا ثبت ونه شو چې په دفتر کې څه پرې ونه لیکل شي. بیا هڅه وکړئ.",
  )
  val reminderEmpty: String get() = t("متنِ یادآوری خالی است", "The reminder text is empty", "د یادونې متن تش دی")
  fun reminderTooLong(n: Any?): String = t(
    "متنِ یادآوری حداکثر ${asIs(n)} حرف",
    "The reminder text can have at most $n characters",
    "د یادونې متن تر ټولو ډېر $n توري",
  )
  fun unknownSlots(slots: Any?): String = t("جای‌نگهدارِ ناشناخته: ${asIs(slots)}", "Unknown placeholder: $slots", "نامعلوم ځای‌نیوونکی: $slots")
  val unknownChannel: String get() = t("راهِ یادآوریِ ناشناخته", "Unknown reminder channel", "د یادونې نامعلومه لاره")
  val debtorNameNeeded: String get() = t("نام قرض‌دار را بنویسید", "Write the debtor's name", "د پوروړي نوم ولیکئ")
  val creditLimitBad: String get() = t("سقفِ اعتبار معتبر وارد کنید", "Enter a valid credit limit", "د پور سم حد ولیکئ")
  fun deleteWarnDebt(name: Any?, amount: Any?, count: Any?): String = t(
    "«${asIs(name)}» هنوز ${asIs(amount)} افغانی بدهی دارد. با حذف او، این طلب و ${asIs(count)} تراکنش او برای همیشه از دفتر پاک می‌شود.",
    "«$name» still owes $amount afghani. Deleting them erases this debt and their $count transactions from the ledger for good.",
    "«$name» لا هم $amount افغانۍ پور لري. د هغه په ړنګولو دا پور او د هغه $count راکړې ورکړې د تل لپاره له دفتر څخه پاکېږي.",
  )
  fun deleteWarn(name: Any?, count: Any?): String = t(
    "«${asIs(name)}» و ${asIs(count)} تراکنش او حذف خواهند شد. این عملیات قابل بازگشت نیست.",
    "«$name» and their $count transactions will be deleted. This can't be undone.",
    "«$name» او د هغه $count راکړې ورکړې به ړنګې شي. دا کار بېرته نه راګرځي.",
  )
  val amountBad: String get() = t("مبلغ معتبر وارد کنید", "Enter a valid amount", "سم مبلغ ولیکئ")
  val dueUnreadable: String get() = t("سررسید خوانده نشد", "The due date couldn't be read", "د ورکړې نېټه ونه لوستل شوه")
  val dueBeforeGive: String get() = t(
    "سررسید نمی‌تواند پیش از تاریخِ برد باشد",
    "The due date can't be before the date the credit was given",
    "د ورکړې نېټه د پور له ورکولو مخکې نه شي کېدای",
  )
  val txNotFound: String get() = t("تراکنش پیدا نشد", "Transaction not found", "راکړه ورکړه پیدا نه شوه")
  val dueOnlyGive: String get() = t("سررسید فقط برای برد است", "A due date is only for credit given", "د ورکړې نېټه یوازې د ورکړل شوي پور لپاره ده")

  // ── کشو بی فروش (`DrawerNoSale`) ──
  val needReason: String get() = t("دلیل را بنویسید", "Write the reason", "لامل ولیکئ")
  val drawerNeedsApproval: String get() = t(
    "باز کردنِ کشو بی فروش تأییدِ صاحب یا مدیر می‌خواهد (APPROVAL_REQUIRED)",
    "Opening the drawer without a sale needs the owner's or a manager's approval (APPROVAL_REQUIRED)",
    "پرته له پلور د دخل خلاصول د خاوند یا مدیر تایید غواړي (APPROVAL_REQUIRED)",
  )
  val noDrawerPrinter: String get() = t(
    "چاپگرِ کشودار تعریف نشده — تنظیمات › دستگاه‌ها",
    "No printer with a cash drawer is set up — Settings › Devices",
    "د دخل لرونکی چاپګر نه دی ټاکل شوی — امستنې › وسایل",
  )

  // ── فاکتورِ نگه‌داشته و سبد (`HeldInvoices`، `CartStore`، `SaleDraft`) ──
  val heldEmpty: String get() = t(
    "سبد خالی است — چیزی برای نگه‌داشتن نیست",
    "The cart is empty — nothing to hold",
    "ټوکرۍ تشه ده — د ساتلو لپاره څه نشته",
  )
  fun heldFull(n: Any?): String = t(
    "حداکثر ${asIs(n)} فاکتورِ باز با هم. یکی را تمام کنید، باز کنید یا دور بیندازید.",
    "At most $n open invoices at once. Finish, open or discard one.",
    "په یو وخت تر ټولو ډېر $n خلاص بلونه. یو یې بشپړ، خلاص یا لرې کړئ.",
  )
  val heldGone: String get() = t("این فاکتور دیگر نگه‌داشته نیست", "This invoice is no longer on hold", "دا بل نور نه دی ساتل شوی")
  val heldNothingLeft: String get() = t(
    "کالاهای این فاکتور دیگر فروختنی نیستند (پاک یا غیرفعال شده‌اند). فاکتور نگه‌داشته ماند.",
    "This invoice's items can no longer be sold (deleted or turned off). The invoice stays on hold.",
    "د دې بل توکي نور نه پلورل کېږي (ړنګ یا غیرفعال شوي). بل ساتل شوی پاتې شو.",
  )
  fun heldTitle(no: Any?): String = t("فاکتورِ #${asIs(no)}", "Invoice #$no", "بل #$no")
  fun heldTitleNamed(no: Any?, label: Any?): String = t("فاکتورِ #${asIs(no)} «${asIs(label)}»", "Invoice #$no «$label»", "بل #$no «$label»")
  fun heldDone(title: Any?): String = t("${asIs(title)} نگه داشته شد", "$title put on hold", "$title وساتل شو")
  fun heldDropped(n: Any): String = t(
    " — ${asIs(n)} ردیف که کالایش دیگر فروختنی نیست کنار رفت",
    " — $n ${pl(n, "row", "rows")} whose item can no longer be sold removed",
    " — $n کرښې چې توکي یې نور نه پلورل کېږي لرې شوې",
  )
  fun heldOpened(title: Any?, dropped: Any?): String = t("${asIs(title)} باز شد${asIs(dropped)}", "$title opened$dropped", "$title خلاص شو$dropped")
  val heldNone: String get() = t("فاکتورِ نگه‌داشته‌ای نیست", "No invoice is on hold", "هېڅ ساتل شوی بل نشته")
  fun heldDiscarded(title: Any?): String = t("${asIs(title)} دور انداخته شد", "$title discarded", "$title لرې شو")
  val saved: String get() = t("ذخیره شد", "Saved", "خوندي شو")
  val heldNoOld: String get() = t("فاکتورِ کهنه‌ای نیست", "There are no old invoices", "هېڅ زوړ بل نشته")
  fun heldOldCleared(n: Any): String = t("${asIs(n)} فاکتورِ کهنه پاک شد", "$n old ${pl(n, "invoice", "invoices")} cleared", "$n زاړه بلونه پاک شول")
  val saleBusy: String get() = t("فروش در حالِ ثبت است — لحظه‌ای بعد", "A sale is being saved — in a moment", "پلور ثبتېږي — یوه شېبه وروسته")
  val heldNotSaved: String get() = t(
    "روی دستگاه نوشته نشد — چیزی جابه‌جا نشد؛ دوباره بزنید.",
    "It wasn't written on the device — nothing was moved; tap again.",
    "په وسیله ونه لیکل شو — هېڅ شی ونه خوځېد؛ بیا یې ووهئ.",
  )
  val cartNotAdded: String get() = t(
    "فاکتور در حالِ جابه‌جایی بود — کالا اضافه نشد؛ دوباره اسکن کنید",
    "The invoice was being switched — the item wasn't added; scan again",
    "بل بدلېده — توکی ور ونه ګډ شو؛ بیا یې سکن کړئ",
  )
  val saleNotSaved: String get() = t(
    "فروش ثبت نشد — دفتر روی دستگاه نوشته نشد. سبد سرِ جایش است؛ دوباره بزنید.",
    "The sale wasn't saved — the ledger couldn't be written on the device. The cart is still there; tap again.",
    "پلور ثبت نه شو — دفتر په وسیله ونه لیکل شو. ټوکرۍ پر خپل ځای ده؛ بیا یې ووهئ.",
  )

  // ── فروشِ امانی (`Layaways`) ──
  fun layawayHeld(name: Any?, held: Any?, free: Any?, wanted: Any?, unit: Any?): String = t(
    "«${asIs(name)}»: ${asIs(held)}${asIs(unit)} برای فروشِ امانی کنار گذاشته شده و فروخته نمی‌شود؛ فقط ${asIs(free)}${asIs(unit)} آزاد است، شما ${asIs(wanted)}${asIs(unit)} خواستید.",
    "«$name»: $held$unit is set aside for layaway and is not sold; only $free$unit is free, you asked for $wanted$unit.",
    "«$name»: $held$unit د امانتي پلور لپاره ساتل شوی او نه پلورل کېږي؛ یوازې $free$unit ازاد دی، تاسو $wanted$unit وغوښتل.",
  )

  // ── تأمین‌کننده و خرید (`LedgerEngine`) ──
  val supplierNameNeeded: String get() = t("نام تأمین‌کننده را بنویسید", "Write the supplier's name", "د برابروونکي نوم ولیکئ")
  val supplierNotFound: String get() = t("تأمین‌کننده پیدا نشد", "Supplier not found", "برابروونکی پیدا نه شو")
  fun supplierInUse(name: Any?): String = t(
    "«${asIs(name)}» خرید یا پرداخت ثبت‌شده دارد و حذف نمی‌شود",
    "«$name» has recorded purchases or payments and can't be deleted",
    "«$name» ثبت شوي پېرودونه یا ورکړې لري او نه ړنګېږي",
  )
  val pickProduct: String get() = t("محصول را انتخاب کنید", "Choose the product", "توکی وټاکئ")
  val dueBad: String get() = t("سررسید درست نیست", "The due date isn't valid", "د ورکړې نېټه سمه نه ده")
  val alreadyPaid: String get() = t("همین پرداخت پیش‌تر ثبت شده است", "This payment was already recorded", "دا ورکړه له مخکې ثبت شوې")

  // ── سفارشِ اینترنتی در دفتر (`OrderFulfillment`، `OrderNotify`) ──
  val reviewBeforeFloor: String get() = t(
    "پیش از راه‌اندازی تحویل شده؛ اگر دستی ثبت نکرده‌اید، ثبت کنید",
    "Delivered before setup; if you haven't recorded it by hand, record it",
    "له چالانولو مخکې سپارل شوی؛ که مو په لاس نه وي ثبت کړی، ثبت یې کړئ",
  )
  fun orderBackManualReturn(no: Any?): String = t(
    "سفارش #${asIs(no)} به دکان برگشت ولی برای فاکتورش پیش‌تر «مرجوعیِ» دستی ثبت شده — لغوِ خودکار نشد. مرجوعی پول را از دخل برداشته و کالا را یک بار برگردانده؛ صاحب دخل و موجودی را بررسی کند.",
    "Order #$no came back to the shop, but a manual «return» was already recorded for its invoice — it was not cancelled automatically. The return took the money from the till and brought the goods back once; the owner should check the till and stock.",
    "فرمایش #$no بېرته دوکان ته راغی، خو د هغه د بل لپاره مخکې لاسي «بېرته ورکړه» ثبت شوې — په خپله لغوه نه شو. بېرته ورکړې پیسې له دخل واخیستې او توکي یو ځل بېرته راوړل؛ خاوند دې دخل او موجودي وګوري.",
  )
  val orderBad: String get() = t("سفارش معتبر نیست", "The order isn't valid", "فرمایش سم نه دی")
  val orderTest: String get() = t(
    "سفارشِ آزمایشی فروش و کسرِ موجودی ندارد",
    "A test order has no sale and takes nothing from stock",
    "آزمایښتي فرمایش پلور او د موجودۍ کمښت نه لري",
  )
  fun orderNoLines(no: Any?): String = t(
    "قلم‌های سفارش #${asIs(no)} نیامد؛ سفارش را دوباره باز کنید",
    "The items of order #$no didn't arrive; open the order again",
    "د فرمایش #$no توکي رانغلل؛ فرمایش بیا خلاص کړئ",
  )
  fun orderTag(no: Any?): String = t("سفارش #${asIs(no)}: ", "Order #$no: ", "فرمایش #$no: ")
  fun qtyOfBad(name: Any?): String = t("مقدارِ «${asIs(name)}» درست نیست", "The quantity of «$name» isn't valid", "د «$name» اندازه سمه نه ده")
  val orderLinesMismatch: String get() = t(
    "قلم‌های فاکتور با سفارش نمی‌خواند",
    "The invoice items don't match the order",
    "د بل توکي له فرمایش سره سمون نه خوري",
  )
  val orderMovesMismatch: String get() = t(
    "حرکت‌های انبار با سفارش نمی‌خواند",
    "The stock movements don't match the order",
    "د ګدام خوځښتونه له فرمایش سره سمون نه خوري",
  )
  fun orderSaleMissing(no: Any?): String = t(
    "سفارش #${asIs(no)}: فروشِ تحویلِ این سفارش هنوز در این دستگاه نیست؛ پس از همگام‌سازی ثبت می‌شود",
    "Order #$no: the delivery sale of this order isn't on this device yet; it will be recorded after syncing",
    "فرمایش #$no: د دې فرمایش د سپارلو پلور لا په دې وسیله کې نشته؛ له همغږۍ وروسته ثبتېږي",
  )
  fun orderWhy(no: Any?, why: Any?): String = t("سفارش #${asIs(no)}: ${asIs(why)}", "Order #$no: $why", "فرمایش #$no: $why")
  fun orderRefundNotAccount(no: Any?): String = t(
    "سفارش #${asIs(no)}: بازپرداخت به حسابِ مشتری نمی‌رود؛ دستی بررسی کنید",
    "Order #$no: the refund doesn't go to the customer's account; check it by hand",
    "فرمایش #$no: بېرته ورکړه د پېرودونکي حساب ته نه ځي؛ په لاس یې وګورئ",
  )
  fun orderRefundNothing(no: Any?, label: Any?): String = t(
    "سفارشِ اینترنتی #${asIs(no)}: بازپرداخت رسید ولی از فاکتور #${asIs(label)} چیزی برای برگشت نمانده بود",
    "Online order #$no: the refund arrived but nothing was left to return from invoice #$label",
    "انټرنېټي فرمایش #$no: بېرته ورکړه راورسېده خو له بل #$label څخه د بېرته کولو لپاره څه پاتې نه وو",
  )
  fun orderSaleNotHere(no: Any?): String = t(
    "فروشِ سفارش #${asIs(no)} در این دفتر نیست",
    "The sale of order #$no isn't in this ledger",
    "د فرمایش #$no پلور په دې دفتر کې نشته",
  )
  val orderChannelNote: String get() = t(
    "سفارشِ تازهٔ مشتری از ویترین",
    "A customer's new order from the storefront",
    "له ویترین څخه د پېرودونکي نوی فرمایش",
  )
  fun newOrderNo(no: Any?): String = t("سفارشِ تازه #${asIs(no)}", "New order #$no", "نوی فرمایش #$no")
  val newOrder: String get() = t("سفارشِ تازه", "New order", "نوی فرمایش")
  val tapToView: String get() = t("برای دیدن و پذیرفتن لمس کنید", "Tap to view and accept", "د لیدو او منلو لپاره یې وټکوئ")

  // ── پرداخت و صندوق (`PayFlow`، `PayFixFlow`، `PayStatusFlow`) ──
  fun refRequired(method: Any?): String = t(
    "شمارهٔ مرجعِ ${asIs(method)} لازم است",
    "The reference number of $method is required",
    "د $method د سرچینې شمېره پکار ده",
  )
  val thisMethod: String get() = t("این روش", "this method", "دې لارې")
  val nonCashOverpay: String get() = t(
    "پرداختِ غیرنقد بیش از مبلغِ فاکتور است؛ پولِ پس فقط از نقد داده می‌شود",
    "The non-cash payment is more than the invoice amount; change is given only from cash",
    "غیرنغدي ورکړه د بل له مبلغ ډېره ده؛ پاتې پیسې یوازې له نغدو ورکول کېږي",
  )
  fun methodLimit(method: Any?): String = t(
    "مبلغِ ${asIs(method)} از سقفِ همان روش بیشتر است",
    "The amount of $method is above that method's limit",
    "د $method مبلغ د همغې لارې له حد ډېر دی",
  )
  val methodUnknown: String get() = t("روشِ پرداخت شناخته نیست", "The payment method is not known", "د ورکړې لاره نه پېژندل کېږي")
  fun shortBy(amount: Any?): String = t(
    "باقی‌مانده ${asIs(amount)} افغانی است — مشتری را انتخاب کنید یا مبلغ را کامل کنید",
    "$amount afghani is left — choose the customer or pay the full amount",
    "$amount افغانۍ پاتې دي — پېرودونکی وټاکئ یا مبلغ بشپړ کړئ",
  )
  val payMismatch: String get() = t(
    "مبلغِ پرداخت با فاکتور نمی‌خواند؛ دوباره باز کنید",
    "The paid amount doesn't match the invoice; open it again",
    "د ورکړې مبلغ له بل سره سمون نه خوري؛ بیا یې خلاص کړئ",
  )
  val cashShort: String get() = t(
    "نقدِ دخل برای این خروج کافی نیست",
    "There isn't enough cash in the till for this payout",
    "په دخل کې د دې وتلو لپاره بس نغدې نشته",
  )
  val reasonNeeded: String get() = t("دلیل لازم است", "A reason is required", "لامل پکار دی")
  val otherNeedsNote: String get() = t("برای «دیگر» توضیح بنویسید", "Write an explanation for «Other»", "د «نور» لپاره تشریح ولیکئ")
  val reasonBad: String get() = t("دلیل درست نیست", "The reason isn't valid", "لامل سم نه دی")
  val drawerOnlyApproved: String get() = t(
    "باز کردنِ کشو بی فروش فقط با تأییدِ صاحب یا مدیر",
    "Opening the drawer without a sale only with the owner's or a manager's approval",
    "پرته له پلور د دخل خلاصول یوازې د خاوند یا مدیر په تایید",
  )
  val clockBehind: String get() = t(
    "ساعتِ این دستگاه از زمانِ باز شدنِ صندوق عقب‌تر است",
    "This device's clock is behind the time the till was opened",
    "د دې وسیلې ساعت د دخل د خلاصېدو له وخت شاته دی",
  )
  val notesCountBad: String get() = t("شمارِ اسکناس درست نیست", "The banknote count isn't valid", "د نوټونو شمېر سم نه دی")
  val amountInvalid: String get() = t("مبلغ درست نیست", "The amount isn't valid", "مبلغ سم نه دی")
  val pendingManual: String get() = t(" (ثبتِ دستی)", " (entered by hand)", " (په لاس ثبت)")
  val pendingWait: String get() = t(" (در انتظار)", " (pending)", " (په تمه)")
  val tendered: String get() = t("دریافتی", "Received", "ترلاسه شوې")
  val change: String get() = t("پولِ پس", "Change", "پاتې پیسې")
  fun tillClosed(counted: Any?, who: Any?): String = t(
    "بستنِ صندوق — شمرده: ${asIs(counted)}${asIs(who)}",
    "Till closed — counted: $counted$who",
    "دخل وتړل شو — شمېرل شوي: $counted$who",
  )
  fun tillClosedBlind(counted: Any?, who: Any?): String = t(
    "بستنِ صندوق (شمارشِ کور) — شمرده: ${asIs(counted)}${asIs(who)}",
    "Till closed (blind count) — counted: $counted$who",
    "دخل وتړل شو (ړوند شمېر) — شمېرل شوي: $counted$who",
  )

  // ── برگشتِ کالا (`ReturnFlow`) ──
  val returnPosted: String get() = t(
    "این برگشت یک بار ثبت شده است و دوباره ثبت نمی‌شود",
    "This return was already recorded and won't be recorded again",
    "دا بېرته ورکړه یو ځل ثبت شوې او بیا نه ثبتېږي",
  )
  val returnPickReason: String get() = t("دلیلِ برگشت را انتخاب کنید", "Choose the reason for the return", "د بېرته ورکړې لامل وټاکئ")
  val returnReasonListed: String get() = t(
    "دلیلِ برگشت را از فهرستِ دکان انتخاب کنید",
    "Choose the reason for the return from the shop's list",
    "د بېرته ورکړې لامل د دوکان له لېست څخه وټاکئ",
  )
  val returnNothing: String get() = t(
    "مقدار معتبری برای برگشت وارد نشده",
    "No valid quantity was entered for the return",
    "د بېرته ورکړې لپاره سمه اندازه نه ده ولیکل شوې",
  )
  val returnTooMuch: String get() = t(
    "بیش از مقدارِ فروخته‌شده برنمی‌گردد",
    "More than the quantity sold can't be returned",
    "له پلورل شوې اندازې ډېر نه بېرته کېږي",
  )
  val returnPriceBad: String get() = t("قیمتِ برگشت درست نیست", "The return price isn't valid", "د بېرته ورکړې بیه سمه نه ده")
  val productNotFound: String get() = t("کالا پیدا نشد", "Item not found", "توکی پیدا نه شو")
  val returnPickDisposition: String get() = t("مقصدِ کالا را انتخاب کنید", "Choose where the item goes", "د توکي ځای وټاکئ")
  val refundModeBad: String get() = t("روشِ بازپرداخت درست نیست", "The refund method isn't valid", "د بېرته ورکړې لاره سمه نه ده")
  val returnNeedsApproval: String get() = t("این برگشت تأیید لازم دارد", "This return needs approval", "دا بېرته ورکړه تایید غواړي")
  val refundNotCounted: String get() = t("بازپرداخت حساب نشد", "The refund couldn't be worked out", "بېرته ورکړه حساب نه شوه")
  val refundAwaitsPayment: String get() = t(
    "بخشی از پولِ این فاکتور هنوز «در انتظار» است؛ اول «پول رسید» را بزنید",
    "Part of this invoice's payment is still pending; tap \"Money received\" first",
    "د دې بل یوه برخه پیسې لا «په تمه» دي؛ لومړی «پیسې ورسېدې» ووهئ",
  )
  val exchangeNotSaved: String get() = t("فروشِ تعویض ثبت نشد", "The exchange sale wasn't recorded", "د بدلون پلور ثبت نه شو")
  val returnBad: String get() = t("درخواستِ برگشت درست نیست", "The return request isn't valid", "د بېرته ورکړې غوښتنه سمه نه ده")
  val foreignReceipt: String get() = t("این رسیدِ دکانِ دیگری است", "This is another shop's receipt", "دا د بل دوکان رسید دی")
  val cash: String get() = t("نقد", "Cash", "نغدې")

  // ── گاوصندوق (`SafeBook`) ──
  fun varianceOver(amount: Any?, limit: Any?): String = t(
    "اختلافِ شمارش اضافه ${asIs(amount)} افغانی است — بیش از ${asIs(limit)} افغانی دلیل می‌خواهد",
    "The count is $amount afghani over — more than $limit afghani needs a reason",
    "په شمېر کې $amount افغانۍ زیات دي — له $limit افغانیو ډېر لامل غواړي",
  )
  fun varianceShort(amount: Any?, limit: Any?): String = t(
    "اختلافِ شمارش کسری ${asIs(amount)} افغانی است — بیش از ${asIs(limit)} افغانی دلیل می‌خواهد",
    "The count is $amount afghani short — more than $limit afghani needs a reason",
    "په شمېر کې $amount افغانۍ کم دي — له $limit افغانیو ډېر لامل غواړي",
  )
  fun notSavedCode(code: Any?): String = t("ثبت نشد (${asIs(code)})", "Not saved ($code)", "ثبت نه شو ($code)")

  // ── فروش و فاکتور (`SalesEngine`) ──
  val invoicePosted: String get() = t(
    "این فاکتور یک بار ثبت شده است و دوباره ثبت نمی‌شود",
    "This invoice was already recorded and won't be recorded again",
    "دا بل یو ځل ثبت شوی او بیا نه ثبتېږي",
  )
  fun wholeUnit(name: Any?, unit: Any?): String = t(
    "«${asIs(name)}» به «${asIs(unit)}» شمرده می‌شود و اعشار نمی‌گیرد",
    "«$name» is counted in «$unit» and takes no decimals",
    "«$name» په «$unit» شمېرل کېږي او اعشاریه نه اخلي",
  )
  fun noStock(name: Any?): String = t(
    "«${asIs(name)}» در برنامه موجودی ندارد. اگر جنس در دکان هست، اول ورودی انبار را ثبت کنید.",
    "«$name» has no stock in the app. If the goods are in the shop, first record a stock entry.",
    "«$name» په پروګرام کې موجودي نه لري. که توکي په دوکان کې وي، لومړی د ګدام ننوتنه ثبت کړئ.",
  )
  fun shortStock(name: Any?, have: Any?, unit: Any?, want: Any?): String = t(
    "«${asIs(name)}»: ${asIs(have)}${asIs(unit)} موجود است، شما ${asIs(want)}${asIs(unit)} خواستید.",
    "«$name»: $have$unit in stock, you asked for $want$unit.",
    "«$name»: $have$unit موجود دي، تاسو $want$unit وغوښتل.",
  )
  val cartEmpty: String get() = t("سبد خرید خالی است", "The cart is empty", "ټوکرۍ تشه ده")
  fun inactiveNoSale(name: Any?): String = t(
    "«${asIs(name)}» غیرفعال است و فروخته نمی‌شود",
    "«$name» is turned off and isn't sold",
    "«$name» غیرفعال دی او نه پلورل کېږي",
  )
  fun expiredLeft(name: Any?, expired: Any, good: Any): String = t(
    "از «${asIs(name)}» ${asIs(expired)} واحد منقضی است؛ فقط ${asIs(good)} واحدِ سالم مانده",
    "Of «$name», $expired ${pl(expired, "unit is", "units are")} expired; only $good good ${pl(good, "unit", "units")} left",
    "له «$name» څخه $expired واحده تاریخ تېر دي؛ یوازې $good سم واحده پاتې دي",
  )
  val paidBad: String get() = t("مبلغ پرداختی درست نیست", "The amount paid isn't valid", "ورکړل شوی مبلغ سم نه دی")
  val needsManager: String get() = t("تأییدِ مدیر لازم است", "A manager's approval is required", "د مدیر تایید پکار دی")
  val zeroTotalRule: String get() = t(
    "قاعدهٔ دکان: فاکتورِ با مبلغِ صفر ثبت نمی‌شود",
    "Shop rule: an invoice with a zero amount is not recorded",
    "د دوکان قاعده: د صفر مبلغ بل نه ثبتېږي",
  )
  fun customerFromRule(from: Any?): String = t(
    "قاعدهٔ دکان: فاکتورِ ${asIs(from)} و بالاتر مشتری می‌خواهد — مشتری را انتخاب کنید",
    "Shop rule: an invoice of $from or more needs a customer — choose the customer",
    "د دوکان قاعده: د $from یا ډېر بل پېرودونکی غواړي — پېرودونکی وټاکئ",
  )
  val onlineNoCancel: String get() = t(
    "این فاکتورِ سفارشِ اینترنتی است و از صندوق لغو نمی‌شود. کالایی که پیک برگرداند ⇒ همان سفارش را در «سفارش‌ها» «برگشت به دکان» بزنید (فروش خودش لغو می‌شود). «مرجوعی» فقط برای کالایی است که مشتری پس از تحویل پس آورد و پولش را از دخل گرفت",
    "This is an online order's invoice and can't be cancelled from the till. Goods the courier brought back ⇒ tap «Back to shop» on that order in «Orders» (the sale is cancelled by itself). «Return» is only for goods the customer brought back after delivery and got the money from the till",
    "دا د انټرنېټي فرمایش بل دی او له دخل نه لغوه کېږي. هغه توکي چې پیک بېرته راوړل ⇒ په «فرمایشونه» کې د همغه فرمایش «دوکان ته بېرته» ووهئ (پلور پخپله لغوه کېږي). «بېرته ورکړه» یوازې د هغو توکو لپاره ده چې پېرودونکي له سپارلو وروسته بېرته راوړل او پیسې یې له دخل واخیستې",
  )
  fun cancelConfirm(no: Any?): String = t(
    "فروش #${asIs(no)} لغو می‌شود و موجودی کالاهای آن به انبار برمی‌گردد. سابقه فروش حذف نمی‌شود.",
    "Sale #$no will be cancelled and its items go back to stock. The sale history is not deleted.",
    "پلور #$no لغوه کېږي او د هغه توکي بېرته ګدام ته ځي. د پلور تاریخچه نه ړنګېږي.",
  )
  val invoiceCancelled: String get() = t("این فاکتور لغو شده است", "This invoice was cancelled", "دا بل لغوه شوی")
  val saleReturnNothing: String get() = t(
    "مقدار معتبری برای مرجوعی وارد نشده",
    "No valid quantity was entered for the return",
    "د بېرته ورکړې لپاره سمه اندازه نه ده ولیکل شوې",
  )

  // ── دریافتِ پول از صندوق (`TillReceiveFlow`) ──
  val amountAboveZero: String get() = t("مبلغ باید بیشتر از صفر باشد", "The amount must be more than zero", "مبلغ باید له صفر ډېر وي")
  val amountTooBig: String get() = t("مبلغ بیش از حد است", "The amount is too large", "مبلغ له حد ډېر دی")
  fun prepayNotConfirmed(debt: Any?): String = t(
    "بدهیِ همین لحظهٔ مشتری ${asIs(debt)} افغانی است و این مبلغ بیش از آن است؛ بی تأییدِ پیش‌پرداخت ثبت نشد.",
    "The customer's debt right now is $debt afghani and this amount is more than that; it wasn't recorded without confirming the prepayment.",
    "د پېرودونکي اوسنی پور $debt افغانۍ دی او دا مبلغ له هغه ډېر دی؛ د مخکې ورکړې له تایید پرته ثبت نه شو.",
  )
  val barcodeEmpty: String get() = t("بارکد خالی است", "The barcode is empty", "بارکوډ تش دی")
  val salePriceNeeded: String get() = t("قیمتِ فروش را بنویسید", "Write the sale price", "د پلور بیه ولیکئ")
  val unitNeeded: String get() = t("واحد را بنویسید", "Write the unit", "واحد ولیکئ")
  val stockAsNumber: String get() = t("موجودی را با عدد بنویسید", "Write the stock as a number", "موجودي په عدد ولیکئ")
  /** روی هر فاکتور و رسیدِ آموزشی چاپ می‌شود */
  val trainingMark: String get() = t("آموزشی", "Training", "زده‌کړه")
}
