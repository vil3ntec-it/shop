package ir.vil3ntec.tohid.core.i18n

/**
 *  دفترِ مشتری در کامپیوتر (`ui/screens/DeskCustomers.kt`): کارت‌ها و جدولِ مشتری‌ها، پروندهٔ هر مشتری (صورت‌حساب، فاکتورها،
 *  اقلامِ برداشته)، دریافت و قرضِ دستی، سقفِ اعتبار و نگاهِ فاکتور. این نوشته‌ها پیش‌تر متنِ خامِ فارسی بودند و از هیچ زبانی
 *  نمی‌گذشتند (LOC-08 دری، دورِ ۳). فارسی کلمه‌به‌کلمه همان پیشین است؛ هر جا همان جمله در جدولِ دیگری بود، همان دوباره به کار
 *  رفته. برگهٔ اکسل (خروجی) زبانِ صفحه را نمی‌گیرد و در خودِ صفحه «// داده:» است. قاعده‌ها سرِ `I18n.kt`.
 */
@Suppress("unused")
object StrCustomerPages {
  // ── `DeskCustomers.kt` ──

  // کارت‌ها و جدولِ مشتری‌ها
  val owing: String get() = t("بدهکار", "Owing", "پوروړي")
  val lateFilter: String get() = t("دیرکرد +۶۰ روز", "Late 60+ days", "ځنډ +۶۰ ورځې")
  val buyers: String get() = t("خریدار", "Buyers", "پېرودونکي")
  val noBalance: String get() = t("بی‌حساب", "No balance", "بې پاتې حسابه")
  val sortMostOwed: String get() = t("بیشترین بدهی", "Most owed", "ډېر پور")
  val sortLatest: String get() = t("تازه‌ترین خرید", "Latest purchase", "وروستی پېرود")
  val sortMostBought: String get() = t("بیشترین خرید", "Most bought", "ډېر پېرود")
  val lateKpi: String get() = t("دیرکرد بیش از ۶۰ روز", "Over 60 days late", "له ۶۰ ورځو زیات ځنډ")
  val monthKpi: String get() = t("خریدِ مشتری‌ها در این ماه", "Customer purchases this month", "پدې میاشت کې د پېرودونکو پېرود")
  val searchHint: String get() = t("جست‌وجوی نام یا تلفن", "Search name or phone", "نوم یا ټیلیفون ولټوئ")
  fun sortBy(s: Any?): String = t("مرتب: ${asIs(s)}", "Sort: $s", "ترتیب: $s")
  fun listHint(shown: Any?, total: Any?, what: Any?): String = t(
    "${asIs(shown)} از ${asIs(total)} مشتری — روی ${asIs(what)} بزنید تا صورت‌حساب، فاکتورها و اقلامش باز شود.",
    "$shown of $total customers — click a $what to open its statement, invoices and items.",
    "$shown له $total پېرودونکو — پر $what کلیک وکړئ چې صورت‌حساب، بلونه او توکي یې پرانیستل شي.",
  )
  val noneYet: String get() = t(
    "هنوز مشتری‌ای نیست — نام را در «مشتریِ تازه» بنویسید و Enter بزنید.",
    "No customers yet — type a name in “New customer” and press Enter.",
    "لا پېرودونکی نشته — نوم په «نوی پېرودونکی» کې ولیکئ او Enter ووهئ.",
  )
  val noneMatch: String get() = t("با این صافی کسی نیست.", "No one matches this filter.", "له دې چاڼ سره هېڅوک نشته.")
  val phone: String get() = t("تلفن", "Phone", "ټیلیفون")
  val debtDays: String get() = t("روزِ بدهی", "Days owed", "د پور ورځې")
  val lastPaid: String get() = t("آخرین پرداخت", "Last payment", "وروستۍ تادیه")
  val settled: String get() = t("صاف", "Settled", "تصفیه")
  fun totalOf(n: Any?): String = t("جمع (${asIs(n)})", "Total ($n)", "ټول ($n)")
  val noPhone: String get() = t("بی تلفن", "No phone", "بې ټیلیفونه")
  val inCredit: String get() = t("طلبکار (پیش‌پرداخت)", "In credit (prepaid)", "طلبکار (مخکې تادیه)")
  fun cardStats(n: Any?, amount: Any?): String = t(
    "${asIs(n)} فاکتور · ${asIs(amount)} ؋",
    "$n ${pl(n ?: 0, "invoice", "invoices")} · $amount ؋",
    "$n بلونه · $amount ؋",
  )
  fun lastBoughtOn(date: Any?): String = t("آخرین خرید ${asIs(date)}", "Last purchase $date", "وروستی پېرود $date")
  val noPurchases: String get() = t("هنوز خریدی نیست", "No purchases yet", "لا پېرود نشته")

  // پروندهٔ مشتری
  val tabStatement: String get() = t("صورت‌حساب", "Statement", "صورت‌حساب")
  val tabItems: String get() = t("اقلامِ برداشته", "Items taken", "اخیستل شوي توکي")
  fun receivedSaved(amount: Any?): String = t("دریافتِ ${asIs(amount)} ؋ ثبت شد", "Received $amount ؋ — saved", "د $amount ؋ ترلاسه ثبت شوه")
  fun creditSaved(amount: Any?): String = t("قرضِ ${asIs(amount)} ؋ ثبت شد", "Credit of $amount ؋ saved", "د $amount ؋ پور ثبت شو")
  val backToCards: String get() = t("برگشت به کارت‌ها", "Back to cards", "کارتونو ته بېرته")
  val escBack: String get() = t("Esc برگشت", "Esc back", "Esc بېرته")
  val receiveKey: String get() = t("دریافتِ وجه  F7", "Receive payment  F7", "پیسې ترلاسه کول  F7")
  val creditKey: String get() = t("قرضِ دستی  F6", "Manual credit  F6", "لاسي پور  F6")
  val secondNumber: String get() = t("شمارهٔ دوم و برچسب", "Second number & tags", "دویمه شمېره او ټګونه")
  fun printTab(tab: Any?): String = t("چاپِ «${asIs(tab)}»  Ctrl+P", "Print “$tab”  Ctrl+P", "«$tab» چاپ  Ctrl+P")
  val colRow: String get() = t("ردیف", "Row", "قطار")
  val colGive: String get() = t("برد (بدهکار)", "Given (owes)", "ورکړه (پوروړی)")
  val colReceive: String get() = t("رسید (بستانکار)", "Received (paid)", "ترلاسه (ورکړې)")
  val colDue: String get() = t("سررسید / باز", "Due / open", "نېټه / پرانیستی")
  val ledgerEmpty: String get() = t(
    "هنوز ردیفی نیست — قرضِ نسیه و دریافت‌ها این‌جا می‌آیند.",
    "No rows yet — credit sales and payments appear here.",
    "لا قطار نشته — نسیه پورونه او ترلاسې دلته راځي.",
  )
  fun overdueAmount(amount: Any?): String = t("گذشته ${asIs(amount)}", "Overdue $amount", "تېر شوی $amount")
  val ledgerNote: String get() = t(
    "اشتباه پاک نمی‌شود: با ردیفِ مخالف («دریافت» یا «قرضِ دستی») و شرحِ «اصلاحِ ردیفِ …» درست کنید تا ردش بماند. روی ردیف بزنید تا اقلام، پرداخت‌ها، رسید و سررسیدش کنارِ جدول بیاید.",
    "Mistakes aren't deleted: fix them with an opposite row (“Receive” or “Manual credit”) and the note “Fix row …” so the trail stays. Click a row to show its items, payments, receipt and due date beside the table.",
    "تېروتنه نه پاکېږي: د مخالف قطار («ترلاسه» یا «لاسي پور») او د «د قطار … سمون» یادښت سره یې سم کړئ چې نښه یې پاتې شي. پر قطار کلیک وکړئ چې توکي، تادیې، رسید او نېټه یې د جدول تر څنګ راشي.",
  )
  fun rowDetail(row: Any?, kind: Any?, amount: Any?): String = t(
    "ردیفِ ${asIs(row)} — ${asIs(kind)} ${asIs(amount)} ؋",
    "Row $row — $kind $amount ؋",
    "$row قطار — $kind $amount ؋",
  )
  val receiptBtn: String get() = t("رسید", "Receipt", "رسید")
  val payable: String get() = t("قابلِ پرداخت", "Payable", "د ورکړې وړ")
  val invoicesEmpty: String get() = t(
    "این مشتری هنوز فاکتوری ندارد. در صندوق با F2 مشتری را روی فاکتور بگذارید — نقدی هم ثبت می‌شود.",
    "This customer has no invoices yet. At the till, press F2 to put the customer on the invoice — cash sales are recorded too.",
    "دې پېرودونکي لا بل نه دی لرلی. په دخل کې د F2 په وسیله پېرودونکی پر بل کېږدئ — نغدې هم ثبتېږي.",
  )
  val colReturned: String get() = t("برگشتی", "Returned", "بېرته راغلي")
  val itemsEmpty: String get() = t("هنوز کالایی نبرده.", "Hasn't taken any items yet.", "لا یې توکی نه دی وړی.")
  fun creditLimitOf(name: Any?): String = t("سقفِ اعتبارِ ${asIs(name)}", "Credit limit — $name", "د $name د پور سقف")
  val creditLimitSaved: String get() = t("سقفِ اعتبار ذخیره شد", "Credit limit saved", "د پور سقف خوندي شو")
  val profileSaved: String get() = t("پرونده ذخیره شد", "Profile saved", "دوسیه خوندي شوه")

  // نگاهِ فاکتور
  fun sellerIs(name: Any?): String = t("فروشنده: ${asIs(name)}", "Seller: $name", "پلورونکی: $name")
  fun unitPriceIs(price: Any?): String = t("فی ${asIs(price)}", "Unit price $price", "د دانې بیه $price")
  fun returnedQty(qty: Any?): String = t(" · برگشتی ${asIs(qty)}", " · returned $qty", " · بېرته $qty")
  val remainingOnAccount: String get() = t("باقی (به حساب)", "Remaining (on account)", "پاتې (پر حساب)")
  fun cancelledWhy(reason: Any?): String = t("لغوشده — ${asIs(reason)}", "Cancelled — $reason", "لغوه شوی — $reason")
  val showPrint: String get() = t("نمایش و چاپِ فاکتور", "Show and print invoice", "بل ښودل او چاپ")

  // مشتریِ تازه، دریافت و قرضِ دستی
  val newCustomerName: String get() = t("مشتریِ تازه — نام", "New customer — name", "نوی پېرودونکی — نوم")
  val addKey: String get() = t("افزودن ↵", "Add ↵", "زیاتول ↵")
  val receivePayment: String get() = t("دریافتِ وجه", "Receive payment", "د پیسو ترلاسه کول")
  val manualCredit: String get() = t("قرضِ دستی", "Manual credit", "لاسي پور")
  val amountAfn: String get() = t("مبلغ (؋)", "Amount (؋)", "اندازه (؋)")
  val saveKey: String get() = t("ثبت ↵", "Save ↵", "ثبت ↵")
  val rowWord: String get() = t("ردیف", "row", "قطار")
  val cardWord: String get() = t("کارت", "card", "کارت")
}
