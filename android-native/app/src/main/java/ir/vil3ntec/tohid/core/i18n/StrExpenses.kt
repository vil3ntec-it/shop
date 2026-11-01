package ir.vil3ntec.tohid.core.i18n

/**
 *  مصارف و سابقهٔ عملیات (`Expenses`، `AuditLog`) و پیام‌های مصارفِ `LedgerEngine`.
 *
 *  ساخته‌شده از جدولِ سه‌زبانه؛ دستی هم ویرایش‌پذیر است. قاعده‌ها سرِ `I18n.kt`.
 *  ترجمه‌های نامطمئن در `docs/i18n-glossary.md` برای بازبینیِ گویشور.
 *  ⛔ یادداشتِ سابقه (`AuditEntry.notes`)، نامِ دسته و عنوانِ مصرف داده‌اند و ترجمه نمی‌شوند.
 */
@Suppress("unused")
object StrExpenses {

  // صفحهٔ مصارف
  val subtitle: String get() = t("هزینه‌های ثبت‌شده فروشگاه", "Recorded shop expenses", "د دوکان ثبت شوي لګښتونه")
  val month: String get() = t("ماه", "Month", "میاشت")
  fun monthTotal(m: Any): String = t("جمع مصارف $m", "Total expenses for $m", "د $m ټول لګښتونه")
  fun rows(n: Any): String = t("$n ردیف", "$n ${pl(n, "entry", "entries")}", "$n ${pl(n, "کرښه", "کرښې")}")
  val byCategory: String get() = t("به تفکیک دسته‌بندی", "By category", "د ډلې له مخې")
  fun categoriesIn(n: Any, m: Any): String = t("$n دسته در $m", "$n ${pl(n, "category", "categories")} in $m", "په $m کې $n ${pl(n, "ډله", "ډلې")}")
  val allExpenses: String get() = t("همهٔ مصارف", "All expenses", "ټول لګښتونه")
  fun expensesOf(c: Any): String = t("مصارف «$c»", "“$c” expenses", "د «$c» لګښتونه")
  val noneThisMonth: String get() = t("در این ماه مصرفی ثبت نشده است.", "No expenses recorded this month.", "په دې میاشت کې لګښت نه دی ثبت شوی.")
  val newExpense: String get() = t("مصرف تازه", "New expense", "نوی لګښت")
  val editExpense: String get() = t("ویرایش مصرف", "Edit expense", "لګښت سمول")
  fun byCategoryTitle(m: Any): String = t("مصارف $m به تفکیک دسته", "Expenses for $m by category", "د $m لګښتونه د ډلې له مخې")
  val tapCategory: String get() = t("روی هر دسته بزنید تا فقط مصارفِ همان دیده شود", "Tap a category to see only its expenses", "پر یوې ډلې ټک وکړئ چې یوازې د هماغې لګښتونه ښکاره شي")
  val showAllCategories: String get() = t("نمایش همهٔ دسته‌ها", "Show all categories", "ټولې ډلې وښایاست")

  // فرم و پیام‌ها
  val forWhat: String get() = t("بابت چی؟ (اختیاری)", "What for? (optional)", "د څه لپاره؟ (اختیاري)")
  val amountAfn: String get() = t("مبلغ (افغانی)", "Amount (AFN)", "مبلغ (افغانۍ)")
  val save: String get() = t("ذخیره", "Save", "خوندي کول")
  val edit: String get() = t("ویرایش", "Edit", "سمول")
  val delete: String get() = t("حذف", "Delete", "لرې کول")
  val saved: String get() = t("با موفقیت ثبت شد", "Saved", "ثبت شو")
  val edited: String get() = t("با موفقیت ویرایش شد", "Changes saved", "بدلونونه خوندي شول")
  val deleted: String get() = t("با موفقیت حذف شد", "Expense removed", "لګښت لرې شو")
  val deleteTitle: String get() = t("حذف مصرف؟", "Delete this expense?", "دا لګښت لرې کړئ؟")
  fun deleteBody(title: Any, amount: Any): String = t(
    "«$title» به مبلغ $amount ؋ حذف می‌شود.",
    "“$title” for $amount ؋ will be deleted.",
    "«$title» د $amount ؋ په مبلغ لرې کېږي.",
  )
  val amountInvalid: String get() = t("مبلغ معتبر وارد کنید", "The amount isn't valid", "مبلغ سم نه دی")
  val pickCategory: String get() = t("دسته‌بندی را انتخاب کنید", "Choose a category", "ډله وټاکئ")
  val expenseNotFound: String get() = t("مصرف پیدا نشد", "Expense not found", "لګښت ونه موندل شو")

  // سابقهٔ عملیات
  fun eventsCount(n: Any): String = t("$n رویداد ثبت‌شده", "$n recorded ${pl(n, "event", "events")}", "$n ثبت شوې ${pl(n, "پېښه", "پېښې")}")
  val noEvents: String get() = t("هنوز رویدادی ثبت نشده است.", "No events recorded yet.", "لا تر اوسه هېڅ پېښه نه ده ثبت شوې.")

  // نوعِ رویداد (همان AUDIT_TYPE_LABELS ِ نسخهٔ وب)
  val auditSale: String get() = t("ثبت فروش", "Sale", "پلور")
  val auditCancelSale: String get() = t("لغو فروش", "Sale cancelled", "لغوه شوی پلور")
  val auditReturn: String get() = t("مرجوعی", "Return", "بېرته ورکړه")
  val auditReturnNoReceipt: String get() = t("برگشتِ بی رسید", "Return without receipt", "بې رسیده بېرته ورکړه")
  val auditPurchase: String get() = t("ثبت خرید", "Purchase", "پېرود")
  val auditCustomerPayment: String get() = t("پرداخت مشتری", "Customer payment", "د پیرودونکي ورکړه")
  val auditSupplierPayment: String get() = t("پرداخت تأمین‌کننده", "Supplier payment", "عرضه‌کوونکي ته ورکړه")
  val auditStockAdjustment: String get() = t("اصلاح موجودی", "Stock adjustment", "د موجودي سمون")
  val auditSupplierReturn: String get() = t("برگشت به تأمین‌کننده", "Return to supplier", "عرضه‌کوونکي ته بېرته ورکړه")
  val auditPriceChange: String get() = t("تغییر قیمت", "Price change", "د بیې بدلون")
  val auditProductEdit: String get() = t("ویرایش کالا", "Item edited", "د توکي سمون")
  val auditShiftOpen: String get() = t("باز کردن صندوق", "Register opened", "د صندوق پرانیستل")
  val auditShiftClose: String get() = t("بستن صندوق", "Register closed", "د صندوق بندول")
  val auditShiftReview: String get() = t("بررسیِ اختلافِ صندوق", "Register difference reviewed", "د صندوق د توپیر کتنه")
  val auditDeleteProduct: String get() = t("حذف محصول", "Item deleted", "د توکي لرې کول")
  val auditDebtorDelete: String get() = t("حذف قرض‌دار", "Debtor deleted", "د پوروړي لرې کول")
  val auditDebtTxDelete: String get() = t("حذف تراکنش بدهی", "Debt entry deleted", "د پور د راکړې ورکړې لرې کول")
  val auditExpenseDelete: String get() = t("حذف مصرف", "Expense deleted", "د لګښت لرې کول")
  val auditDataExport: String get() = t("خروجی کامل داده", "Full data export", "د ټولو معلوماتو ایستل")
  val auditDebtDueSet: String get() = t("سررسیدِ قرض", "Debt due date", "د پور د ورکړې نېټه")
  val auditCreditOverLimit: String get() = t("برد بیش از سقفِ اعتبار", "Credit over the limit", "له حد زیات پور")
  val auditCreditLimitSet: String get() = t("سقفِ اعتبار", "Credit limit", "د پور حد")
  val auditCustomerMerge: String get() = t("ادغامِ مشتری", "Customer merge", "د پیرودونکو یوځای کول")
  val auditMarketingConsent: String get() = t("رضایتِ پیامِ تبلیغاتی", "Marketing consent", "د تبلیغاتي پیغام رضایت")
  val auditPriceListSet: String get() = t("لیستِ قیمتِ مشتری", "Customer price list", "د پیرودونکي د بیو لړلیک")
  val auditDebtReminder: String get() = t("یادآوریِ بدهی", "Debt reminder", "د پور یادونه")
  /** RPT-09 — ردیفی که از فاکتورِ باز حذف شد («سابقهٔ ضدتقلب») */
  val auditLineDelete: String get() = t("حذفِ ردیف از فاکتور", "Line removed from invoice", "له بل څخه د کرښې لرې کول")
}
