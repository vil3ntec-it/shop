package ir.vil3ntec.tohid.ui.screens

import ir.vil3ntec.tohid.data.DebtTransaction
import ir.vil3ntec.tohid.data.DebtorEngine

/**
 *  منطقِ خالصِ صفحهٔ حسابِ یک قرض‌دار (`DebtorDetailScreen`) — بی Compose، زیرِ آزمون.
 *
 *  عددها همان `DebtorEngine.Account` است (مانده هیچ‌جا ذخیره نمی‌شود)؛ این‌جا فقط
 *  گروه‌بندیِ تراکنش‌ها زیرِ سرخطِ روز، پنهان کردنِ ردیفی که «حذف با برگرداندن» در
 *  انتظار دارد، کارهای کشو، و متن‌های آماده.
 */
object DebtorDetailModel {

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
  enum class Action { CALL, SMS, WHATSAPP, EDIT, STATEMENT_CSV, PRINT, DELETE }

  /**
   *  کدام کارها برای این حساب معنی دارد: تماس/پیامک/واتساپ فقط با شمارهٔ
   *  گرفتنی؛ صورت‌حساب و چاپ فقط وقتی تراکنشی هست. ویرایش و حذف همیشه.
   */
  fun actions(phone: String, hasTransactions: Boolean): List<Action> = buildList {
    if (dialable(phone).isNotEmpty()) addAll(listOf(Action.CALL, Action.SMS, Action.WHATSAPP))
    add(Action.EDIT)
    if (hasTransactions) addAll(listOf(Action.STATEMENT_CSV, Action.PRINT))
    add(Action.DELETE)
  }

  /** شمارهٔ گرفتنی: رقم‌های لاتین و «+» — همان پاک‌سازیِ دکمه‌های یادآوری */
  fun dialable(phone: String): String =
    ir.vil3ntec.tohid.normalizeDigits(phone).filter { it in '0'..'9' || it == '+' }

  /** شمارهٔ واتساپ (`wa.me`): بی «+» */
  fun whatsappNumber(phone: String): String = dialable(phone).removePrefix("+")

  /**
   *  متنِ آمادهٔ پیامک و واتساپ — نوشتنِ «سلام، … افغانی طلب دارید» برای هر
   *  مشتری، همان کاری است که فروشنده از انجامش طفره می‌رود.
   */
  fun reminder(name: String, balance: Double): String =
    if (balance > 0) "سلام $name عزیز، یادآوری می‌کنم که ${ir.vil3ntec.tohid.money(balance)} افغانی از حساب شما باقی مانده. ممنون."
    else "سلام $name عزیز،"

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
