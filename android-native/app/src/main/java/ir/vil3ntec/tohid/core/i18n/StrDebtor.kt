package ir.vil3ntec.tohid.core.i18n

/**
 *  نوشته‌های صفحهٔ حسابِ قرض‌دار (فرمِ قرض‌دارِ نسخهٔ ۳، دفترِ مشتریِ کامپیوتر و صفحه‌های قدیمیِ قرض‌داران).
 *
 *  تا دورِ ۵ ِ دری این جدول در `ui/screens` بود با یک `t` ِ محلی که همیشه فارسی برمی‌گرداند: دری، انگلیسی و
 *  پشتو هم فارسی می‌دیدند و هیچ مقداری از `asIs` نمی‌گذشت. از دورِ ۶ (LOC-08) همان `t` ِ همهٔ جدول‌هاست و در
 *  `I18nTables` ثبت است؛ فارسی همان متنِ پیشین است.
 */
object StrDebtor {
  val back: String get() = t("بازگشت", "Back", "شاته")
  val actions: String get() = t("کارها", "Actions", "کړنې")
  val actionsAndInfo: String get() = t("کارها و اطلاعات", "Actions & info", "کړنې او معلومات")
  val call: String get() = t("زنگ", "Call", "زنګ")
  val sms: String get() = t("پیامک", "SMS", "پیغام")
  val whatsapp: String get() = t("واتساپ", "WhatsApp", "واټساپ")
  val edit: String get() = t("ویرایش", "Edit", "سمول")
  val delete: String get() = t("حذف", "Delete", "ړنګول")
  val statementCsv: String get() = t("صورت‌حساب CSV", "Statement CSV", "صورتحساب CSV")
  val statementImage: String get() = t("تصویرِ صورت‌حساب", "Statement image", "د صورتحساب انځور")
  val print: String get() = t("چاپ", "Print", "چاپ")
  fun printTitle(name: String): String = t("صورت‌حسابِ ${asIs(name)}", "Statement — $name", "صورتحساب — $name")
  val notes: String get() = t("یادداشت", "Note", "یادښت")

  fun daysInDebt(span: String, since: String): String =
    t("${asIs(span)} است که بدهکار است — از ${asIs(since)}", "In debt for $span — since $since", "$span راهیسې پوروړی دی — له $since")
  fun inDebtShort(span: String): String = t("${asIs(span)} بدهکار", "$span in debt", "$span پوروړی")
  val noDebt: String get() = t("بدهی ندارد", "No debt", "پور نه لري")

  val given: String get() = t("کل برد", "Total given", "ټول ورکړل شوي")
  val received: String get() = t("کل رسید", "Total received", "ټول ترلاسه شوي")
  val balance: String get() = t("الباقی", "Balance", "پاتې")
  val settled: String get() = t("تسویه", "Settled", "تصفیه")

  val transactions: String get() = t("تراکنش‌ها", "Transactions", "راکړې ورکړې")
  val details: String get() = t("جزئیات", "Details", "جزئیات")
  val receiptPrint: String get() = t("رسید / چاپ", "Receipt / print", "رسید / چاپ")
  fun balanceAfterShort(amount: String): String = t("مانده ${asIs(amount)}", "Bal. $amount", "پاتې $amount")
  val balanceAfter: String get() = t("مانده پس از این ردیف", "Balance after this entry", "له دې وروسته پاتې")
  val date: String get() = t("تاریخ", "Date", "نېټه")
  val due: String get() = t("سررسید", "Due date", "د ورکړې نېټه")
  val noDue: String get() = t("سررسید ندارد", "No due date", "د ورکړې نېټه نه لري")
  val setDue: String get() = t("سررسید", "Due date", "د ورکړې نېټه")
  val status: String get() = t("وضعیت", "Status", "حالت")
  fun openAmount(amount: String): String = t("باز ${asIs(amount)}", "$amount open", "$amount پاتې")
  val paidOff: String get() = t("تسویه شد", "Paid off", "تصفیه شو")
  val items: String get() = t("اقلام", "Items", "توکي")
  fun invoiceNo(label: String): String = t("فاکتور #${asIs(label)}", "Invoice #$label", "بل #$label")
  val showInvoice: String get() = t("نمایشِ فاکتور", "Show invoice", "بل ښکاره کړئ")
  val forWhat: String get() = t("بابتِ", "Applied to", "د")
  val prepaid: String get() = t("پیش‌پرداخت", "Prepaid", "مخکې ورکړه")
  val giveTag: String get() = t("برد", "Given", "ورکړه")
  val receiveTag: String get() = t("رسید", "Received", "رسید")
  val giveTitle: String get() = t("جنس یا پول برد", "Took goods or cash", "توکي یا پیسې یې یوړې")
  val receiveTitle: String get() = t("پول رساند", "Paid back", "پیسې یې راکړې")
  val noTx: String get() = t("هنوز تراکنشی ثبت نشده", "No transactions yet", "تر اوسه راکړه ورکړه نشته")
  val noTxHint: String get() = t("نخستین برد یا رسیدِ این حساب را ثبت کنید.", "Record this account's first give or payment.", "د دې حساب لومړۍ ورکړه یا رسید ثبت کړئ.")
  val firstTx: String get() = t("ثبتِ نخستین تراکنش", "Add first transaction", "لومړۍ راکړه ورکړه ثبت کړئ")

  fun invoices(n: Int): String = t("فاکتورهای این مشتری (${asIs(n)})", "This customer's invoices ($n)", "د دې پیرودونکي بلونه ($n)")
  fun olderInvoices(n: Int): String = t("${asIs(n)} فاکتورِ قدیمی‌تر در «فروش‌ها»", "$n older invoices in Sales", "$n زاړه بلونه په «پلور» کې")
  val invoice: String get() = t("فاکتور", "Invoice", "بل")
  val cancelled: String get() = t("لغوشده", "cancelled", "لغوه شوی")
  /** CRM-07: نشانِ فاکتورِ سفارشِ اینترنتی (`Sale.isOnlineOrder`) در پروندهٔ مشتری */
  val online: String get() = t("اینترنتی", "online", "انلاین")
  /** سطرِ فاکتور در «فاکتورهای این مشتری»: شماره، نشانِ «اینترنتی» و «لغوشده» (هر حالت یک نوشته، همان متنِ پیشینِ کنارِ هم) */
  fun invoiceTitle(label: String, online: Boolean, cancelled: Boolean): String = when {
    online && cancelled -> t("فاکتور ${asIs(label)} · اینترنتی (لغوشده)", "Invoice $label · online (cancelled)", "بل $label · انلاین (لغوه شوی)")
    online -> t("فاکتور ${asIs(label)} · اینترنتی", "Invoice $label · online", "بل $label · انلاین")
    cancelled -> t("فاکتور ${asIs(label)} (لغوشده)", "Invoice $label (cancelled)", "بل $label (لغوه شوی)")
    else -> t("فاکتور ${asIs(label)}", "Invoice $label", "بل $label")
  }
  fun remaining(amount: String): String = t("مانده ${asIs(amount)}", "$amount due", "پاتې $amount")

  val newTx: String get() = t("ثبتِ تراکنش", "New transaction", "نوې راکړه ورکړه")
  val newTxHint: String get() = t("مبلغ را بنویسید و برد یا رسید را انتخاب کنید", "Enter an amount, then pick give or receive", "مبلغ ولیکئ او ورکړه یا رسید وټاکئ")
  val newGive: String get() = t("ثبتِ برد", "Record a give", "ورکړه ثبت کړئ")
  val newReceive: String get() = t("ثبتِ رسید", "Record a payment", "رسید ثبت کړئ")
  val giveKind: String get() = t("برد — افزایش بدهی", "Give — adds to the debt", "ورکړه — پور زیاتوي")
  val receiveKind: String get() = t("رسید — کاهش بدهی", "Payment — reduces the debt", "رسید — پور کموي")
  val amount: String get() = t("مبلغ", "Amount", "مبلغ")
  val notesOptional: String get() = t("یادداشت (اختیاری)", "Note (optional)", "یادښت (اختیاري)")
  val settleAll: String get() = t("تسویهٔ کامل", "Settle in full", "بشپړ تصفیه")

  val txDeleted: String get() = t("تراکنش حذف شد", "Transaction deleted", "راکړه ورکړه ړنګه شوه")
  fun debtorDeleted(name: String): String = t("«${asIs(name)}» حذف شد", "“$name” deleted", "«$name» ړنګ شو")
  val undo: String get() = t("برگرداندن", "Undo", "بېرته")
  val deleteDebtorTitle: String get() = t("حذفِ قرض‌دار؟", "Delete this debtor?", "پوروړی ړنګ شي؟")
  val cancel: String get() = t("انصراف", "Cancel", "لغوه")

  val newDebtor: String get() = t("قرض‌دار تازه", "New debtor", "نوی پوروړی")
  val editDebtor: String get() = t("ویرایش قرض‌دار", "Edit debtor", "پوروړی سمول")
  val name: String get() = t("نام", "Name", "نوم")
  val phoneOptional: String get() = t("تلفن (اختیاری)", "Phone (optional)", "تلیفون (اختیاري)")
  val save: String get() = t("ذخیره", "Save", "خوندي کول")
}

