package ir.vil3ntec.tohid.ui.screens

/**
 *  نوشته‌های صفحهٔ حسابِ قرض‌دار — به شکلِ جدولِ سه‌زبانهٔ `core/i18n` (`object StrX`،
 *  هر نوشته `t(fa, en, ps)`).
 *
 *  تا جدولِ مشترک (#348) به این شاخه برسد، `t` ِ پایینِ همین فایل فارسی را برمی‌گرداند؛
 *  پس از ادغام فقط آن سه خط حذف و `ir.vil3ntec.tohid.core.i18n.t` وارد می‌شود و
 *  `StrDebtor` به `I18nTables.all` اضافه می‌شود — هیچ صفحه‌ای عوض نمی‌شود.
 */
@Suppress("unused")
internal object StrDebtor {
  val back: String get() = t("بازگشت", "Back", "شاته")
  val actions: String get() = t("کارها", "Actions", "کړنې")
  val actionsAndInfo: String get() = t("کارها و اطلاعات", "Actions & info", "کړنې او معلومات")
  val call: String get() = t("زنگ", "Call", "زنګ")
  val sms: String get() = t("پیامک", "SMS", "پیغام")
  val whatsapp: String get() = t("واتساپ", "WhatsApp", "واټساپ")
  val edit: String get() = t("ویرایش", "Edit", "سمول")
  val delete: String get() = t("حذف", "Delete", "ړنګول")
  val statementCsv: String get() = t("صورت‌حساب CSV", "Statement CSV", "صورتحساب CSV")
  val print: String get() = t("چاپ", "Print", "چاپ")
  fun printTitle(name: String): String = t("صورت‌حسابِ $name", "Statement — $name", "صورتحساب — $name")
  val notes: String get() = t("یادداشت", "Note", "یادښت")

  fun daysInDebt(span: String, since: String): String =
    t("$span است که بدهکار است — از $since", "In debt for $span — since $since", "$span راهیسې پوروړی دی — له $since")
  fun inDebtShort(span: String): String = t("$span بدهکار", "$span in debt", "$span پوروړی")
  val noDebt: String get() = t("بدهی ندارد", "No debt", "پور نه لري")

  val given: String get() = t("کل برد", "Total given", "ټول ورکړل شوي")
  val received: String get() = t("کل رسید", "Total received", "ټول ترلاسه شوي")
  val balance: String get() = t("الباقی", "Balance", "پاتې")
  val settled: String get() = t("تسویه", "Settled", "تصفیه")

  val transactions: String get() = t("تراکنش‌ها", "Transactions", "راکړې ورکړې")
  val giveTag: String get() = t("برد", "Given", "ورکړه")
  val receiveTag: String get() = t("رسید", "Received", "رسید")
  val giveTitle: String get() = t("جنس یا پول برد", "Took goods or cash", "توکي یا پیسې یې یوړې")
  val receiveTitle: String get() = t("پول رساند", "Paid back", "پیسې یې راکړې")
  val noTx: String get() = t("هنوز تراکنشی ثبت نشده", "No transactions yet", "تر اوسه راکړه ورکړه نشته")
  val noTxHint: String get() = t("نخستین برد یا رسیدِ این حساب را ثبت کنید.", "Record this account's first give or payment.", "د دې حساب لومړۍ ورکړه یا رسید ثبت کړئ.")
  val firstTx: String get() = t("ثبتِ نخستین تراکنش", "Add first transaction", "لومړۍ راکړه ورکړه ثبت کړئ")

  fun invoices(n: Int): String = t("فاکتورهای این مشتری ($n)", "This customer's invoices ($n)", "د دې پیرودونکي بلونه ($n)")
  fun olderInvoices(n: Int): String = t("$n فاکتورِ قدیمی‌تر در «فروش‌ها»", "$n older invoices in Sales", "$n زاړه بلونه په «پلور» کې")
  val invoice: String get() = t("فاکتور", "Invoice", "بل")
  val cancelled: String get() = t("لغوشده", "cancelled", "لغوه شوی")
  fun remaining(amount: String): String = t("مانده $amount", "$amount due", "پاتې $amount")

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
  fun debtorDeleted(name: String): String = t("«$name» حذف شد", "“$name” deleted", "«$name» ړنګ شو")
  val undo: String get() = t("برگرداندن", "Undo", "بېرته")
  val deleteDebtorTitle: String get() = t("حذفِ قرض‌دار؟", "Delete this debtor?", "پوروړی ړنګ شي؟")
  val cancel: String get() = t("انصراف", "Cancel", "لغوه")

  val newDebtor: String get() = t("قرض‌دار تازه", "New debtor", "نوی پوروړی")
  val editDebtor: String get() = t("ویرایش قرض‌دار", "Edit debtor", "پوروړی سمول")
  val name: String get() = t("نام", "Name", "نوم")
  val phoneOptional: String get() = t("تلفن (اختیاری)", "Phone (optional)", "تلیفون (اختیاري)")
  val save: String get() = t("ذخیره", "Save", "خوندي کول")
}

/** همان شکلِ `ir.vil3ntec.tohid.core.i18n.t` — تا ادغامِ جدولِ مشترک، فارسی */
@Suppress("UNUSED_PARAMETER")
private fun t(fa: String, en: String, ps: String): String = fa
