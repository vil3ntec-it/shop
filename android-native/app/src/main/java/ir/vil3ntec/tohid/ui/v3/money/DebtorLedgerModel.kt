package ir.vil3ntec.tohid.ui.v3.money

import ir.vil3ntec.tohid.core.i18n.StrMoney
import ir.vil3ntec.tohid.data.DebtBook
import ir.vil3ntec.tohid.data.DebtTransaction
import ir.vil3ntec.tohid.data.DebtorEngine

/**
 *  منطقِ خالصِ صفحهٔ حسابِ یک قرض‌دار (`DebtorLedgerScreen`) — بی Compose، زیرِ آزمون.
 *
 *  عددها همان `DebtorEngine.Account` است (مانده هیچ‌جا ذخیره نمی‌شود)؛ این‌جا فقط
 *  گروه‌بندیِ تراکنش‌ها زیرِ سرخطِ روز، پنهان کردنِ ردیفی که «حذف با برگرداندن» در
 *  انتظار دارد، کارهای کشو، و متن‌های آماده.
 */
object DebtorLedgerModel {

  /** یک روز در فهرست: سرخطِ چسبان و ردیف‌هایش، تازه‌ترین بالا */
  data class Day(val date: String, val rows: List<DebtTransaction>)

  /**
   *  تراکنش‌ها زیرِ سرخطِ روز. روزها تازه‌ترین اول؛ در هر روز همان ترتیبِ
   *  `Account.transactions` (تازه‌ترین ثبت اول). ردیف‌های `hidden` (حذفی که هنوز
   *  می‌شود برگرداند) نشان داده نمی‌شوند؛ روزی که خالی بماند هم نه.
   */
  fun days(transactions: List<DebtTransaction>, hidden: Set<String> = emptySet()): List<Day> =
    transactions
      .filter { it.id !in hidden }
      .groupBy { it.date.ifBlank { UNDATED } }
      .map { (date, rows) -> Day(date, rows) }
      .sortedWith(compareByDescending<Day> { if (it.date == UNDATED) "" else it.date })

  /** همهٔ ردیف‌ها در یک فهرست، به همان ترتیبِ `days` (روزِ تازه‌تر اول) — بی سرخطِ روز */
  fun rows(transactions: List<DebtTransaction>, hidden: Set<String> = emptySet()): List<DebtTransaction> =
    days(transactions, hidden).flatMap { it.rows }

  /** ردیفِ بی‌تاریخ (پشتیبانِ خیلی قدیمی) — ته فهرست */
  const val UNDATED = "—"

  /** سه عددِ بالای حساب */
  data class Totals(val given: Double, val received: Double, val balance: Double) {
    val owes: Boolean get() = balance > EPSILON
    val settled: Boolean get() = kotlin.math.abs(balance) <= EPSILON
  }

  fun totals(account: DebtorEngine.Account): Totals = Totals(account.given, account.received, account.balance)

  /** صفر (با کسرِ ریزِ اعشار) — عددِ خنثی، بی رنگِ بدهی یا رسید */
  fun isZero(value: Double): Boolean = kotlin.math.abs(value) <= EPSILON

  private const val EPSILON = 0.001

  /* ------------------------------ کشو ------------------------------ */

  /** کارهای کشوی حساب، به همان ترتیبی که دیده می‌شوند */
  enum class Action { CALL, SMS, WHATSAPP, EDIT, STATEMENT_CSV, PRINT, STATEMENT_IMAGE, DELETE }

  /**
   *  کدام کارها برای این حساب معنی دارد: تماس/پیامک/واتساپ فقط با شمارهٔ
   *  گرفتنی؛ صورت‌حساب و چاپ فقط وقتی تراکنشی هست. ویرایش و حذف همیشه.
   *  [image] (CRM-05، «گزارش‌های مشتری» روشن) ⇒ «تصویرِ صورت‌حساب» هم، کنارِ صورت‌حساب و چاپ.
   */
  fun actions(phone: String, hasTransactions: Boolean, image: Boolean = false): List<Action> = buildList {
    if (dialable(phone).isNotEmpty()) addAll(listOf(Action.CALL, Action.SMS, Action.WHATSAPP))
    add(Action.EDIT)
    if (hasTransactions) addAll(listOf(Action.STATEMENT_CSV, Action.PRINT))
    if (hasTransactions && image) add(Action.STATEMENT_IMAGE)
    add(Action.DELETE)
  }

  /** شمارهٔ گرفتنی: رقم‌های لاتین و «+» — همان پاک‌سازیِ دکمه‌های یادآوری */
  fun dialable(phone: String): String =
    ir.vil3ntec.tohid.normalizeDigits(phone).filter { it in '0'..'9' || it == '+' }

  /** شمارهٔ واتساپ (`wa.me`): بی «+» */
  fun whatsappNumber(phone: String): String = dialable(phone).removePrefix("+")

  /**
   *  متنِ آمادهٔ پیامک و واتساپ — نوشتنِ «سلام، … افغانی طلب دارید» برای هر
   *  مشتری، همان کاری است که فروشنده از انجامش طفره می‌رود. قالب همان `DebtReminder` (#330).
   */
  fun reminder(name: String, balance: Double): String =
    ir.vil3ntec.tohid.data.DebtReminder.message(ir.vil3ntec.tohid.data.DebtReminder.Vars(name, balance))

  /* ------------------------------ اطلاعاتِ کشو ------------------------------ */

  /** رنگِ یک خطِ اطلاعات: بدهی/عقب‌افتاده سرخ، سررسیدِ بعدی آبیِ اطلاع، سقفِ اعتبار خنثی */
  enum class InfoTone { OWE, INFO, NEUTRAL }

  data class Info(val text: String, val tone: InfoTone)

  /**
   *  خط‌های زیرِ کارهای کشو: «۳ ماه است که بدهکار است — از …»، بردهای عقب‌افتاده، سررسیدِ بعدی،
   *  و سقفِ اعتبار (بیش از سقف یا جای مانده). همه از `DebtorEngine.Account` و `DebtBook.Book`.
   */
  fun info(account: DebtorEngine.Account, book: DebtBook.Book?, now: Long = System.currentTimeMillis()): List<Info> = buildList {
    account.since?.let { since ->
      add(Info(StrMoney.daysInDebt(ir.vil3ntec.tohid.spanText(since, now), ir.vil3ntec.tohid.displayMillis(since)), InfoTone.OWE))
    }
    book ?: return@buildList
    if (book.overdue.isNotEmpty()) add(Info(StrMoney.overdue(book.overdue.size, DebtBook.show(book.overdueAmount)), InfoTone.OWE))
    book.nextDue?.let { e -> e.dueDate?.let { due -> add(Info(StrMoney.nextDue(ir.vil3ntec.tohid.displayDate(due), DebtBook.show(e.open)), InfoTone.INFO)) } }
    book.credit?.let { cr ->
      if (cr.over) add(Info(StrMoney.overLimitBy(DebtBook.show(cr.excess), DebtBook.show(cr.limit)), InfoTone.OWE))
      else add(Info(StrMoney.limitHeadroom(DebtBook.show(cr.limit), DebtBook.show(cr.headroom)), InfoTone.NEUTRAL))
    }
  }

  /** خلاصهٔ کشوی بسته: شماره (اگر هست) و «۶ روز بدهکار» / «بدهی ندارد» */
  fun summary(phone: String, since: Long?, now: Long = System.currentTimeMillis()): String {
    val debt = if (since != null) StrMoney.inDebtShort(ir.vil3ntec.tohid.spanText(since, now)) else StrMoney.noDebt
    val number = ir.vil3ntec.tohid.LocaleText.phoneDisplay(phone).trim()
    return if (number.isEmpty()) debt else "\u2066$number\u2069 · $debt"
  }

  /* ------------------------------ برگرداندن ------------------------------ */

  /**
   *  حذفی که هنوز می‌شود برگرداند: ردیف همین حالا از فهرست می‌رود، ولی دفتر فقط
   *  وقتی عوض می‌شود که پیامِ «برگرداندن» بی‌پاسخ بسته شود (یا صفحه برود).
   *  پس «برگرداندن» هیچ داده‌ای را بازنمی‌نویسد — چیزی هنوز پاک نشده است.
   */
  sealed interface PendingDelete {
    data class Transaction(val txId: String) : PendingDelete
    data class Debtor(val debtorId: String) : PendingDelete
  }

  /** ردیف‌هایی که فهرست نباید نشان دهد */
  fun hiddenTx(pending: List<PendingDelete>): Set<String> =
    pending.filterIsInstance<PendingDelete.Transaction>().mapTo(HashSet()) { it.txId }

  fun hiddenDebtors(pending: List<PendingDelete>): Set<String> =
    pending.filterIsInstance<PendingDelete.Debtor>().mapTo(HashSet()) { it.debtorId }
}
