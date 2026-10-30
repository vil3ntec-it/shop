package ir.vil3ntec.tohid.core.i18n

/**
 *  نوشته‌های گروهِ G4 پول — قرض‌داران، رسیدها، خرید، مصارف (money.md) — صاحبش همان گروه است.
 *  هر ویژگی سه‌زبانه: `val x: String get() = t("…", "…", "…")`؛ قاعده‌ها سرِ `I18n.kt`.
 *  عدد و پول از بیرون قالب‌بندی‌شده می‌آیند (`money`، `DebtBook.show`)؛ این‌جا فقط جمله است.
 *  پیامِ رد/موفقیتِ موتورها (`DebtorEngine`، `LedgerEngine`) مالِ خودِ موتور است و از این‌جا نمی‌گذرد.
 */
@Suppress("unused")
object StrMoney {

  /* ------------------------------ مشترک ------------------------------ */

  val edit: String get() = t("ویرایش", "Edit", "سمول")
  val delete: String get() = t("حذف", "Delete", "ړنګول")
  val call: String get() = t("زنگ", "Call", "زنګ")
  val sms: String get() = t("پیامک", "SMS", "پیغام")
  val whatsapp: String get() = t("واتساپ", "WhatsApp", "واټساپ")
  val print: String get() = t("چاپ", "Print", "چاپ")
  val notes: String get() = t("یادداشت", "Note", "یادښت")
  val notesOptional: String get() = t("یادداشت (اختیاری)", "Note (optional)", "یادښت (اختیاري)")
  val amount: String get() = t("مبلغ", "Amount", "مبلغ")
  val date: String get() = t("تاریخ", "Date", "نېټه")
  val settleAll: String get() = t("تسویهٔ کامل", "Settle in full", "بشپړ تصفیه")
  val showAll: String get() = t("نمایشِ همه", "Show all", "ټول ښکاره کړئ")
  fun rows(n: Any): String = t("$n ردیف", "$n ${pl(n, "row", "rows")}", "$n ${pl(n, "کرښه", "کرښې")}")
  val deletedDebtor: String get() = t("قرض‌دار حذف‌شده", "Deleted debtor", "ړنګ شوی پوروړی")
  val saved: String get() = t("با موفقیت ثبت شد", "Saved", "په بریالیتوب ثبت شو")
  val edited: String get() = t("با موفقیت ویرایش شد", "Changes saved", "په بریالیتوب سم شو")
  val deleted: String get() = t("با موفقیت حذف شد", "Deleted", "په بریالیتوب ړنګ شو")

  /* ------------------------------ قرض‌داران ------------------------------ */

  val newDebtor: String get() = t("قرض‌دار تازه", "New debtor", "نوی پوروړی")
  val editDebtor: String get() = t("ویرایش قرض‌دار", "Edit debtor", "پوروړی سمول")
  val emptyDebtorsTitle: String get() = t("هنوز قرض‌داری ثبت نشده", "No debtors yet", "تر اوسه پوروړی نشته")
  val emptyDebtorsBody: String get() = t(
    "کسانی که از شما نسیه می‌برند را اینجا اضافه کنید تا حساب هرکدام جدا بماند.",
    "Add the people who buy on credit so each account stays separate.",
    "هغه کسان دلته ورزیات کړئ چې پور اخلي، څو د هر یوه حساب جلا پاتې شي.",
  )
  val noDebtorMatchTitle: String get() = t("چیزی پیدا نشد", "Nothing found", "هېڅ ونه موندل شو")
  val noDebtorMatchBody: String get() = t("قرض‌داری با این نام یا شماره نیست.", "No debtor has this name or number.", "په دې نوم یا شمېره پوروړی نشته.")
  val debtorGone: String get() = t("این قرض‌دار دیگر نیست", "This debtor no longer exists", "دا پوروړی نور نشته")
  val debtorGoneBody: String get() = t("شاید روی دستگاهِ دیگری حذف شده باشد.", "It may have been deleted on another device.", "کېدای شي په بله وسیله ړنګ شوی وي.")

  //  فهرستِ r9
  val statPeople: String get() = t("تعداد قرض‌داران", "Debtors", "د پوروړو شمېر")
  val statOwed: String get() = t("جمع تمام قرض‌ها", "Total owed", "ټول پورونه")
  val statReceived: String get() = t("رسیدهای همه", "All received", "ټول ترلاسه شوي")
  fun itemsCount(n: Any): String = t("$n مورد", "$n ${pl(n, "person", "people")}", "$n کسان")
  val openAccount: String get() = t("باز کردنِ حساب", "Open account", "حساب پرانیستل")
  val noName: String get() = t("بی‌نام", "No name", "بې نومه")
  val sortMost: String get() = t("بیشترین قرض", "Most owed", "تر ټولو ډېر پور")
  val sortLeast: String get() = t("کمترین قرض", "Least owed", "تر ټولو لږ پور")
  val sortOldest: String get() = t("قدیمی‌ترین قرض", "Oldest debt", "تر ټولو زوړ پور")
  val sortName: String get() = t("بر اساس نام", "By name", "د نوم له مخې")

  //  حسابِ یک قرض‌دار (#354)
  val actionsAndInfo: String get() = t("کارها و اطلاعات", "Actions & info", "کړنې او معلومات")
  val statementCsv: String get() = t("صورت‌حساب CSV", "Statement CSV", "صورتحساب CSV")
  fun printTitle(name: Any): String = t("صورت‌حسابِ $name", "Statement — $name", "صورتحساب — $name")
  fun daysInDebt(span: Any, since: Any): String =
    t("$span است که بدهکار است — از $since", "In debt for $span — since $since", "$span راهیسې پوروړی دی — له $since")
  fun inDebtShort(span: Any): String = t("$span بدهکار", "$span in debt", "$span پوروړی")
  val noDebt: String get() = t("بدهی ندارد", "No debt", "پور نه لري")
  fun overdue(n: Any, amount: Any): String =
    t("$n برد از سررسید گذشته — $amount افغانی", "$n overdue ${pl(n, "give", "gives")} — $amount AFN", "$n ورکړې له نېټې تېرې — $amount افغانۍ")
  fun nextDue(date: Any, amount: Any): String =
    t("سررسیدِ بعدی $date — $amount افغانی", "Next due $date — $amount AFN", "راتلونکې نېټه $date — $amount افغانۍ")
  fun overLimitBy(excess: Any, limit: Any): String =
    t("$excess افغانی بیش از سقفِ اعتبار ($limit)", "$excess AFN over the credit limit ($limit)", "$excess افغانۍ د پور له حد ($limit) زیات")
  fun currentBalance(amount: Any): String = t("ماندهٔ فعلی $amount", "current balance $amount", "اوسنی پاتې $amount")
  fun limitHeadroom(limit: Any, headroom: Any): String =
    t("سقفِ اعتبار $limit — تا سقف $headroom افغانی", "Credit limit $limit — $headroom AFN left", "د پور حد $limit — $headroom افغانۍ پاتې")
  val given: String get() = t("کل برد", "Total given", "ټول ورکړل شوي")
  val received: String get() = t("کل رسید", "Total received", "ټول ترلاسه شوي")
  val balance: String get() = t("الباقی", "Balance", "پاتې")
  val settled: String get() = t("تسویه", "Settled", "تصفیه")
  val transactions: String get() = t("تراکنش‌ها", "Transactions", "راکړې ورکړې")
  val details: String get() = t("جزئیات", "Details", "جزئیات")
  fun balanceAfterShort(amount: Any): String = t("مانده $amount", "Bal. $amount", "پاتې $amount")
  val giveTag: String get() = t("برد", "Given", "ورکړه")
  val receiveTag: String get() = t("رسید", "Received", "رسید")
  val noTx: String get() = t("هنوز تراکنشی برای این حساب ثبت نشده.", "No transactions on this account yet.", "تر اوسه پر دې حساب راکړه ورکړه نشته.")
  val noTxHint: String get() = t("نخستین برد یا رسیدِ این حساب را ثبت کنید.", "Record this account's first give or payment.", "د دې حساب لومړۍ ورکړه یا رسید ثبت کړئ.")
  val firstTx: String get() = t("ثبتِ نخستین تراکنش", "Add first transaction", "لومړۍ راکړه ورکړه ثبت کړئ")
  fun invoices(n: Any): String = t("فاکتورهای این مشتری ($n)", "This customer's invoices ($n)", "د دې پیرودونکي بلونه ($n)")
  fun olderInvoices(n: Any): String = t("$n فاکتورِ قدیمی‌تر در «فروش‌ها»", "$n older ${pl(n, "invoice", "invoices")} in Sales", "$n زاړه بلونه په «پلور» کې")
  fun invoiceNo(label: Any): String = t("فاکتور #$label", "Invoice #$label", "بل #$label")
  val cancelled: String get() = t("لغوشده", "Cancelled", "لغوه شوی")
  fun remaining(amount: Any): String = t("مانده $amount", "$amount due", "پاتې $amount")
  val newTx: String get() = t("ثبتِ تراکنش", "New transaction", "نوې راکړه ورکړه")
  val newTxHint: String get() = t("مبلغ را بنویسید و برد یا رسید را انتخاب کنید", "Enter an amount, then pick give or receive", "مبلغ ولیکئ او ورکړه یا رسید وټاکئ")
  val txDeleted: String get() = t("تراکنش حذف شد", "Transaction deleted", "راکړه ورکړه ړنګه شوه")
  fun debtorDeleted(name: Any): String = t("«$name» حذف شد", "“$name” deleted", "«$name» ړنګ شو")
  val deleteDebtorTitle: String get() = t("حذفِ قرض‌دار؟", "Delete this debtor?", "پوروړی ړنګ شي؟")
  val deleteTxTitle: String get() = t("حذفِ تراکنش؟", "Delete this transaction?", "راکړه ورکړه ړنګه شي؟")
  val deleteTxBody: String get() = t(
    "این تراکنش پاک می‌شود و ماندهٔ حساب دوباره حساب می‌شود.",
    "This transaction is removed and the balance is recalculated.",
    "دا راکړه ورکړه ړنګېږي او پاتې حساب بیا شمېرل کېږي.",
  )

  //  برگهٔ جزئیاتِ یک تراکنش
  val giveTitle: String get() = t("جنس یا پول برد", "Took goods or cash", "توکي یا پیسې یې یوړې")
  val receiveTitle: String get() = t("پول رساند", "Paid back", "پیسې یې راکړې")
  val status: String get() = t("وضعیت", "Status", "حالت")
  val due: String get() = t("سررسید", "Due date", "د ورکړې نېټه")
  val noDue: String get() = t("سررسید ندارد", "No due date", "د ورکړې نېټه نه لري")
  fun openAmount(amount: Any): String = t("باز $amount", "$amount open", "$amount پاتې")
  val paidOff: String get() = t("تسویه شد", "Paid off", "تصفیه شو")
  val pastDue: String get() = t("گذشته", "overdue", "تېره")
  val forWhat: String get() = t("بابتِ", "Applied to", "د")
  val prepaid: String get() = t("پیش‌پرداخت", "Prepaid", "مخکې ورکړه")
  val balanceAfter: String get() = t("مانده پس از این ردیف", "Balance after this entry", "له دې وروسته پاتې")
  val items: String get() = t("اقلام", "Items", "توکي")
  fun returned(q: Any): String = t("مرجوعی $q", "returned $q", "بېرته $q")
  fun discount(amount: Any): String = t("تخفیف $amount", "Discount $amount", "تخفیف $amount")
  fun paidAtOnce(amount: Any): String = t("پرداختِ همان لحظه $amount", "Paid at sale $amount", "سمدستي ورکړه $amount")
  val showInvoice: String get() = t("نمایشِ فاکتور", "Show invoice", "بل ښکاره کړئ")
  val printPdf: String get() = t("چاپ / PDF", "Print / PDF", "چاپ / PDF")
  val sendReceipt: String get() = t("فرستادن", "Send", "لېږل")
  val sendReceiptTitle: String get() = t("فرستادنِ رسید", "Send receipt", "رسید لېږل")
  val copyText: String get() = t("کپیِ متن", "Copy text", "متن کاپي")
  val copied: String get() = t("کپی شد", "Copied", "کاپي شو")
  val setDue: String get() = t("سررسید", "Due date", "د ورکړې نېټه")
  val dueTitle: String get() = t("سررسیدِ برد", "Due date", "د ورکړې نېټه")
  val dueField: String get() = t("سررسید (اختیاری)", "Due date (optional)", "د ورکړې نېټه (اختیاري)")
  val dueHelper: String get() = t("خالی یعنی سررسید ندارد", "Empty means no due date", "تش یعنې نېټه نه لري")
  val dueWeek: String get() = t("یک هفته", "1 week", "یوه اوونۍ")
  val dueTwoWeeks: String get() = t("دو هفته", "2 weeks", "دوه اوونۍ")
  val dueMonth: String get() = t("یک ماه", "1 month", "یوه میاشت")
  val dueNone: String get() = t("بی سررسید", "No due date", "بې نېټې")
  fun dueUnreadable(sample: Any): String = t("تاریخ خوانده نشد — مثل $sample بنویسید", "Couldn't read the date — write it like $sample", "نېټه ونه لوستل شوه — داسې یې ولیکئ: $sample")
  val dueSaved: String get() = t("سررسید ثبت شد", "Due date saved", "نېټه ثبت شوه")

  //  برگهٔ قرض‌دار و برگهٔ تراکنش
  val name: String get() = t("نام", "Name", "نوم")
  val phoneOptional: String get() = t("تلفن (اختیاری)", "Phone (optional)", "تلیفون (اختیاري)")
  val creditLimitField: String get() = t("سقفِ اعتبار (اختیاری)", "Credit limit (optional)", "د پور حد (اختیاري)")
  val creditLimitHelper: String get() = t(
    "خالی یعنی بی سقف. بردِ بیش از سقف فقط با تأیید ثبت می‌شود.",
    "Empty means no limit. A give over the limit needs your confirmation.",
    "تش یعنې بې حده. له حد زیاته ورکړه یوازې په تایید ثبتېږي.",
  )
  //  پروندهٔ CRM (#330)
  val phone2Optional: String get() = t("شمارهٔ دوم (اختیاری)", "Second phone (optional)", "دویمه شمېره (اختیاري)")
  val tagsField: String get() = t("برچسب‌ها (اختیاری)", "Tags (optional)", "نښانونه (اختیاري)")
  val tagsHelper: String get() = t("با «،» جدا کنید — مثلاً عمده، همسایه", "Separate with commas — e.g. wholesale, neighbour", "په «،» جلا کړئ — لکه عمده، ګاونډی")
  val consentTitle: String get() = t("رضایت برای پیامِ تبلیغاتی", "Consent to promotional messages", "د تبلیغي پیغام لپاره رضایت")
  val consentUnknown: String get() = t("نپرسیده", "Not asked", "نه دی پوښتل شوی")
  val consentYes: String get() = t("بله", "Yes", "هو")
  val consentNo: String get() = t("نه", "No", "نه")
  val consentSource: String get() = t("منبعِ رضایت", "Where they agreed", "د رضایت سرچینه")
  val consentSourceHint: String get() = t("حضوری، تلفنی…", "In person, by phone…", "حضوري، تلیفوني…")
  val giveKind: String get() = t("برد — جنس یا پول برد", "Give — took goods or cash", "ورکړه — توکي یا پیسې یې یوړې")
  val receiveKind: String get() = t("رسید — پول رساند", "Receive — paid back", "رسید — پیسې یې راکړې")
  val recordGive: String get() = t("ثبت برد", "Record give", "ورکړه ثبت کړئ")
  val recordReceive: String get() = t("ثبت رسید", "Record receipt", "رسید ثبت کړئ")
  fun balanceAfterSave(state: Any): String = t("مانده پس از ثبت: $state", "Balance after saving: $state", "له ثبت وروسته پاتې: $state")
  val overLimitTitle: String get() = t("بیش از سقفِ اعتبار", "Over the credit limit", "له پور حد زیات")
  val overLimitNote: String get() = t(
    "اگر ثبت شود، در «دفترچهٔ ثبت» با همین مبلغ می‌ماند.",
    "If you record it, the audit log keeps it with this amount.",
    "که ثبت شي، په «ثبت کتاب» کې په همدې مبلغ پاتې کېږي.",
  )
  val recordAnyway: String get() = t("باز هم ثبت کن", "Record anyway", "بیا هم ثبت کړه")
  val newCategory: String get() = t("دستهٔ تازه", "New category", "نوې ډله")

  //  «دریافت» (خانه، کارتِ کشیدنی): انتخابِ قرض‌دار
  val receiveTitlePick: String get() = t("دریافت از قرض‌دار", "Receive a payment", "له پوروړي ترلاسه")
  val receivePickHint: String get() = t("جستجوی نام یا شماره", "Search name or number", "نوم یا شمېره ولټوئ")
  val noOneOwes: String get() = t("هیچ‌کس بدهکار نیست", "Nobody owes you", "هېڅوک پوروړی نه دی")

  /* ------------------------------ ادغامِ تکراری‌ها (CRM-10) ------------------------------ */

  val mergeTitle: String get() = t("ادغامِ تکراری‌ها", "Merge duplicates", "تکراري یوځای کول")
  fun mergeEntry(n: Any): String = t("$n گروه مشتریِ تکراری", "$n duplicate ${pl(n, "group", "groups")}", "$n تکراري ${pl(n, "ډله", "ډلې")}")
  val mergeEntrySub: String get() = t("ادغام با حفظِ همهٔ سوابق", "Merge and keep every record", "یوځای کول، ټول سوابق پاتې کېږي")
  val mergeSamePhone: String get() = t("شمارهٔ یکسان", "Same number", "یوه شمېره")
  val mergeSameName: String get() = t("نامِ یکسان — شاید دو نفرِ جدا", "Same name — may be two people", "یو نوم — کېدای شي دوه کسان وي")
  val mergeKeep: String get() = t("ماندگار", "Kept", "پاتې کېږي")
  val mergeAction: String get() = t("ادغام", "Merge", "یوځای کول")
  val noDuplicates: String get() = t("مشتریِ تکراری‌ای نمانده", "No duplicates left", "هېڅ تکراري نه دی پاتې")
  fun mergeConfirmTitle(drop: Any, keep: Any): String = t("ادغامِ «$drop» در «$keep»؟", "Merge “$drop” into “$keep”?", "«$drop» په «$keep» کې یوځای شي؟")
  fun mergeConfirmBody(tx: Any, sales: Any, drop: Any, keep: Any, after: Any): String = t(
    "$tx تراکنش و $sales فاکتورِ «$drop» به «$keep» می‌رود؛ ماندهٔ «$keep» پس از ادغام: $after. «$drop» پاک نمی‌شود و پیوندهای کهنه‌اش «$keep» را باز می‌کنند. این کار برگشت ندارد.",
    "$tx ${pl(tx, "transaction", "transactions")} and $sales ${pl(sales, "invoice", "invoices")} of “$drop” move to “$keep”; balance of “$keep” after the merge: $after. “$drop” isn't deleted and old links open “$keep”. This can't be undone.",
    "د «$drop» $tx راکړې ورکړې او $sales بلونه «$keep» ته ځي؛ له یوځای کېدو وروسته د «$keep» پاتې: $after. «$drop» نه ړنګېږي او زاړه لینکونه «$keep» پرانیزي. دا کار بېرته نه راګرځي.",
  )
  fun merged(drop: Any, keep: Any): String = t("«$drop» در «$keep» ادغام شد", "“$drop” merged into “$keep”", "«$drop» په «$keep» کې یوځای شو")

  /* ------------------------------ رسیدها ------------------------------ */

  val tabDebtorReceipts: String get() = t("رسیدهای قرض‌داران", "Debtor receipts", "د پوروړو رسیدونه")
  val tabExpenses: String get() = t("مصارف", "Expenses", "لګښتونه")
  val total: String get() = t("جمع", "Total", "ټول")
  val allYears: String get() = t("همهٔ سال‌ها", "All years", "ټول کلونه")
  val allMonths: String get() = t("همهٔ ماه‌ها", "All months", "ټولې میاشتې")
  val allDebtors: String get() = t("همهٔ قرض‌داران", "All debtors", "ټول پوروړي")
  val year: String get() = t("سال", "Year", "کال")
  val month: String get() = t("ماه", "Month", "میاشت")
  val debtor: String get() = t("قرض‌دار", "Debtor", "پوروړی")
  val noReceipts: String get() = t("رسیدی با این فیلترها پیدا نشد.", "No receipts match these filters.", "له دې فلټرونو سره رسید ونه موندل شو.")
  val noExpensesFiltered: String get() = t("مصرفی با این فیلترها پیدا نشد.", "No expenses match these filters.", "له دې فلټرونو سره لګښت ونه موندل شو.")
  val expenseFallback: String get() = t("مصرف", "Expense", "لګښت")

  /* --------------------------- خرید و تأمین‌کننده --------------------------- */

  val supplierDebtTotal: String get() = t("بدهیِ ما به تأمین‌کننده‌ها", "We owe suppliers", "موږ عرضه‌کوونکو ته پوروړي یو")
  fun suppliersCaption(suppliers: Any, purchases: Any): String =
    t("$suppliers تأمین‌کننده — $purchases خرید", "$suppliers ${pl(suppliers, "supplier", "suppliers")} — $purchases ${pl(purchases, "purchase", "purchases")}", "$suppliers عرضه‌کوونکي — $purchases پېرودونه")
  val newSupplier: String get() = t("تأمین‌کنندهٔ تازه", "New supplier", "نوی عرضه‌کوونکی")
  val editSupplier: String get() = t("ویرایش تأمین‌کننده", "Edit supplier", "عرضه‌کوونکی سمول")
  val newPurchase: String get() = t("ثبت خرید", "Record purchase", "پېرود ثبت کړئ")
  val pay: String get() = t("پرداخت", "Pay", "ورکړه")
  val weOwe: String get() = t("بدهکاریم", "We owe", "پوروړي یو")
  val prepaidToSupplier: String get() = t("پیش‌پرداخت داریم", "Prepaid", "مخکې مو ورکړي")
  val accountSettled: String get() = t("حساب صاف است", "Settled", "حساب صاف دی")
  fun owesAfn(amount: Any): String = t("$amount افغانی بدهکار", "Owes $amount AFN", "$amount افغانۍ پوروړی")
  fun creditAfn(amount: Any): String = t("$amount افغانی موجودی دارد", "$amount AFN in credit", "$amount افغانۍ زېرمه لري")
  val emptySuppliers: String get() = t("هنوز تأمین‌کننده‌ای ثبت نشده است.", "No suppliers yet.", "تر اوسه عرضه‌کوونکی نشته.")
  val emptySuppliersBody: String get() = t(
    "کسانی که از آن‌ها جنس می‌خرید را ثبت کنید تا خرید و بدهی به هرکدام جدا بماند.",
    "Add the people you buy from so each one's purchases and balance stay separate.",
    "هغه کسان ثبت کړئ چې له هغوی توکي اخلئ، څو د هر یوه پېرود او پور جلا پاتې شي.",
  )
  val deleteSupplierTitle: String get() = t("حذف تأمین‌کننده؟", "Delete this supplier?", "عرضه‌کوونکی ړنګ شي؟")
  fun deleteSupplierBody(name: Any): String = t(
    "«$name» حذف می‌شود. اگر خرید یا پرداختی به نامش ثبت شده باشد، حذف نمی‌شود.",
    "“$name” will be deleted. A supplier with recorded purchases or payments is kept.",
    "«$name» ړنګېږي. که پېرود یا ورکړه یې ثبت وي، نه ړنګېږي.",
  )
  val ourDebt: String get() = t("بدهیِ ما", "We owe", "زموږ پور")
  val totalBought: String get() = t("کل خرید", "Total bought", "ټول پېرود")
  val totalPaid: String get() = t("کل پرداخت", "Total paid", "ټوله ورکړه")
  val addressOptional: String get() = t("آدرس (اختیاری)", "Address (optional)", "پته (اختیاري)")
  val copyAddress: String get() = t("کپیِ آدرس", "Copy address", "پته کاپي")
  val history: String get() = t("خریدها و پرداخت‌ها", "Purchases & payments", "پېرودونه او ورکړې")
  val noHistory: String get() = t("هنوز خرید یا پرداختی ثبت نشده.", "No purchases or payments yet.", "تر اوسه پېرود یا ورکړه نشته.")
  val payment: String get() = t("پرداخت", "Payment", "ورکړه")
  val deletedProduct: String get() = t("(محصول حذف‌شده)", "(deleted item)", "(ړنګ شوی توکی)")
  fun leftToPay(amount: Any): String = t("$amount ؋ باقی", "$amount ؋ left", "$amount ؋ پاتې")
  val purchaseSettled: String get() = t("تسویه", "Paid", "تصفیه")
  val supplierPayHint: String get() = t("پرداخت", "Pay", "ورکړه")
  fun owedLine(name: Any, amount: Any): String = t("$name — $amount ؋ بدهکاریم", "$name — we owe $amount ؋", "$name — $amount ؋ پوروړي یو")
  val payAmount: String get() = t("مبلغ پرداختی", "Amount paid", "ورکړل شوې پیسې")
  val recordPayment: String get() = t("ثبت پرداخت", "Record payment", "ورکړه ثبت کړئ")

  //  فرمِ خرید
  val supplierLabel: String get() = t("تأمین‌کننده", "Supplier", "عرضه‌کوونکی")
  val pickSupplier: String get() = t("انتخاب کنید", "Choose", "وټاکئ")
  val item: String get() = t("کالا", "Item", "توکی")
  val pickItem: String get() = t("کالا را انتخاب کنید", "Choose an item", "توکی وټاکئ")
  val noProductsHint: String get() = t("ابتدا یک کالا در انبار ثبت کنید", "Add an item to the catalog first", "لومړی یو توکی ثبت کړئ")
  val addProduct: String get() = t("کالای تازه", "New item", "نوی توکی")
  val qtyAndPrice: String get() = t("مقدار و قیمت", "Quantity & price", "مقدار او بیه")
  val quantity: String get() = t("مقدار", "Quantity", "مقدار")
  val unitPrice: String get() = t("قیمت هر واحد", "Unit price", "د یو واحد بیه")
  val purchaseTotal: String get() = t("جمع خرید", "Purchase total", "د پېرود ټولګه")
  val paidSection: String get() = t("پرداخت", "Payment", "ورکړه")
  val paidField: String get() = t("پرداخت‌شده", "Paid now", "ورکړل شوي")
  val paidHelper: String get() = t("خالی یعنی نسیه", "Empty means on credit", "تش یعنې پور")
  val remainingLabel: String get() = t("باقی‌مانده", "Remaining", "پاتې")
  val notesSection: String get() = t("توضیحات", "Notes", "یادښتونه")
  val notesField: String get() = t("توضیحات (اختیاری)", "Notes (optional)", "یادښتونه (اختیاري)")
  val purchaseSaved: String get() = t("خرید با موفقیت ثبت شد", "Purchase recorded", "پېرود ثبت شو")
  val invalidNumber: String get() = t("عدد درست بنویسید", "Enter a valid number", "سم شمېر ولیکئ")

  /* ------------------------------ مصارف ------------------------------ */

  fun monthTotal(month: Any): String = t("جمع مصارف $month", "Expenses in $month", "د $month لګښتونه")
  val newExpense: String get() = t("مصرف تازه", "New expense", "نوی لګښت")
  val editExpense: String get() = t("ویرایش مصرف", "Edit expense", "لګښت سمول")
  val byCategory: String get() = t("به تفکیک دسته", "By category", "د ډلې له مخې")
  val allCategories: String get() = t("نمایشِ همهٔ دسته‌ها", "Show all categories", "ټولې ډلې ښکاره کړئ")
  fun categoriesIn(n: Any, month: Any): String = t("$n دسته در $month", "$n ${pl(n, "category", "categories")} in $month", "$n ډلې په $month کې")
  val allExpenses: String get() = t("همهٔ مصارف", "All expenses", "ټول لګښتونه")
  fun categoryExpenses(name: Any): String = t("مصارف «$name»", "“$name” expenses", "د «$name» لګښتونه")
  val noExpensesMonth: String get() = t("در این ماه مصرفی ثبت نشده است.", "No expenses this month.", "په دې میاشت کې لګښت نشته.")
  val noExpensesBody: String get() = t("کرایه، برق، معاش و هر خرجِ دیگرِ دکان را این‌جا ثبت کنید.", "Record rent, power, wages and other shop costs here.", "کرایه، برېښنا، معاش او نور لګښتونه دلته ثبت کړئ.")
  val pickMonth: String get() = t("انتخابِ ماه", "Choose month", "میاشت وټاکئ")
  val prevMonth: String get() = t("ماهِ پیش", "Previous month", "تېره میاشت")
  val nextMonth: String get() = t("ماهِ بعد", "Next month", "راتلونکې میاشت")
  fun breakdownTitle(month: Any): String = t("مصارف $month به تفکیک دسته", "$month expenses by category", "د $month لګښتونه د ډلې له مخې")
  val breakdownHint: String get() = t("روی هر دسته بزنید تا فقط مصارفِ همان دیده شود", "Tap a category to see only its expenses", "پر یوې ډلې ووهئ چې یوازې هغه لګښتونه وګورئ")
  val deleteExpenseTitle: String get() = t("حذف مصرف؟", "Delete this expense?", "لګښت ړنګ شي؟")
  fun deleteExpenseBody(title: Any, amount: Any): String =
    t("«$title» به مبلغ $amount ؋ حذف می‌شود.", "“$title” ($amount ؋) will be deleted.", "«$title» په $amount ؋ ړنګېږي.")
  val category: String get() = t("دسته", "Category", "ډله")
  val forWhatOptional: String get() = t("بابت چی؟ (اختیاری)", "What for? (optional)", "د څه لپاره؟ (اختیاري)")
  val expenseAmount: String get() = t("مبلغ (افغانی)", "Amount (AFN)", "مبلغ (افغانۍ)")
  val share: String get() = t("سهم", "Share", "برخه")
}
