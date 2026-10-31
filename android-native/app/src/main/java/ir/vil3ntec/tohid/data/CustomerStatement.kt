package ir.vil3ntec.tohid.data

import ir.vil3ntec.tohid.core.receipt.StatementLine

/**
 *  ردیف‌های صورت‌حسابِ یک مشتری برای تصویر (CRM-05؛ `core/receipt/StatementLayout`) — همان
 *  ترتیب و مانده‌های `ReportCsv.statement` (تاریخ، سپس زمانِ ثبت)، ولی به **پول** (عددِ صحیح):
 *  هر ردیف با `DebtBook.minor` و مانده جمعِ همین پول‌ها، پس ماندهٔ آخر دقیقاً همان دفترِ قرض است.
 *  Kotlin خالص، بی android.*.
 */
object CustomerStatement {

  fun lines(d: ShopData, debtorId: String): List<StatementLine> {
    var balance = 0L
    return d.transactions.filter { it.debtorId == debtorId }
      .sortedWith(compareBy<DebtTransaction> { it.date }.thenBy { it.createdAt })
      .map { tx ->
        val m = DebtBook.minor(tx.amount).minor
        val give = tx.type == "give"
        balance += if (give) m else -m
        StatementLine(
          dateIso = tx.date,
          note = tx.notes,
          giveMinor = if (give) m else 0L,
          receiveMinor = if (give) 0L else m,
          balanceMinor = balance,
        )
      }
  }

  /** نامِ فایلِ تصویر — همان نامِ صورت‌حسابِ CSV (`ReportCsv.statement`) */
  fun fileName(d: ShopData, debtorId: String, today: String): String =
    ReportCsv.statement(d, debtorId, today).name + ".png"
}
