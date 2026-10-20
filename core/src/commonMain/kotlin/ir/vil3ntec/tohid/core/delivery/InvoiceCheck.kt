package ir.vil3ntec.tohid.core.delivery

import ir.vil3ntec.tohid.core.orders.OrderDraft

/**
 *  تطبیقِ صورت‌حسابِ شرکتِ پیکِ بیرونی با دفترِ خودِ دکان (DLV-19).
 *
 *  دفترِ دکان = مأموریت‌های همان شرکت در همان بازه (`externalStatement`ِ
 *  سرور). صورت‌حساب = مبلغِ کل (لازم)، شمارِ سفارش‌ها (اختیاری) و — اگر
 *  شرکت ریز داده — هر خط: «شمارهٔ رهگیری یا #شمارهٔ سفارش» و مبلغ.
 *
 *  ⛔ دو پولِ جدا در دو جهت: کرایه را دکان به شرکت بدهکار است؛ نقدِ «پرداخت
 *     هنگامِ تحویل» را که شرکت از مشتری گرفته، شرکت به دکان بدهکار است.
 *     پس «صورت‌حساب درست است» هرگز یعنی «همهٔ صورت‌حساب را بپرداز» نیست
 *     وقتی نقدِ دکان نزدِ شرکت است — خالص در هر دو جهت گفته می‌شود.
 *  ⛔ هیچ اختلافی خودکار «درست» حساب نمی‌شود: هر خط یکی از این‌هاست و
 *     جمع‌ها جدا نشان داده می‌شوند (DEL-02: اختلاف پنهان نمی‌ماند).
 */
object InvoiceCheck {

  enum class Kind {
    /** در دفترِ ما «تحویل شد» و مبلغ همان کرایهٔ ثبت‌شده */
    MATCHED,
    /** تحویل شده ولی مبلغِ صورت‌حساب با کرایهٔ ثبت‌شده فرق دارد */
    AMOUNT_DIFF,
    /** شرکت حساب کرده ولی در دفترِ ما تحویل نشده (ناموفق، برگشت یا هنوز زنده) */
    NOT_DELIVERED,
    /** در دفترِ ما برای این شرکت و این بازه چنین سفارشی نیست */
    UNKNOWN,
    /** یک سفارش دو بار در صورت‌حساب آمده */
    DUPLICATE,
    /** تحویل شده ولی در صورت‌حساب نیامده */
    NOT_BILLED,
  }

  data class Line(val key: String, val amountMinor: Long?)

  data class Parsed(val lines: List<Line>, val errors: List<String>)

  data class Item(
    val kind: Kind,
    val key: String,
    val orderNo: Long?,
    val status: String,
    val ourFareMinor: Long?,
    val billedMinor: Long?,
    /** نقدی که شرکت برای همین سفارش از مشتری گرفت (مالِ دکان) */
    val cashMinor: Long? = null,
  )

  data class Result(
    val items: List<Item>,
    val ourDeliveredCount: Int,
    val ourDeliveredFareMinor: Long,
    val billedCount: Int?,
    val billedTotalMinor: Long,
    /** صورت‌حساب − دفترِ ما؛ مثبت یعنی شرکت بیشتر خواسته */
    val amountDiffMinor: Long,
    val countDiff: Int?,
    /** نقدِ COD که شرکت از مشتری‌ها گرفت و باید به دکان بدهد */
    val cashHeldMinor: Long,
  ) {
    val problems: List<Item> get() = items.filter { it.kind != Kind.MATCHED }

    /** کرایه‌ها، شمار و ریز با دفترِ ما یکی است (دربارهٔ نقد چیزی نمی‌گوید) */
    val invoiceMatches: Boolean get() = amountDiffMinor == 0L && (countDiff ?: 0) == 0 && problems.isEmpty()

    /** خالص طبقِ صورت‌حساب: مثبت ⇒ دکان به شرکت می‌دهد؛ منفی ⇒ شرکت به دکان */
    val netToCompanyByInvoiceMinor: Long get() = billedTotalMinor - cashHeldMinor

    /** خالص طبقِ دفترِ ما: کرایهٔ تحویل‌شده‌ها − نقدِ نزدِ شرکت */
    val netToCompanyByLedgerMinor: Long get() = ourDeliveredFareMinor - cashHeldMinor

    /** «همهٔ صورت‌حساب را بپرداز» فقط وقتی درست است که همه یکی است و نقدی نزدِ شرکت نیست */
    val ok: Boolean get() = invoiceMatches && cashHeldMinor == 0L
  }

  /**
   *  هر خط: شناسه و (اختیاری) مبلغ، جدا با فاصله، تب، «;» یا «|».
   *  رقمِ فارسی پذیرفته است؛ «٬» و «,» جداکنندهٔ هزارگان‌اند.
   */
  fun parse(text: String): Parsed {
    val lines = ArrayList<Line>()
    val errors = ArrayList<String>()
    text.lines().forEachIndexed { i, raw ->
      val t = raw.trim()
      if (t.isEmpty()) return@forEachIndexed
      val parts = t.split(Regex("[\\s;|]+")).filter { it.isNotEmpty() }
      val key = parts.first()
      val amount = if (parts.size >= 2) OrderDraft.scaled(parts[1], 2) else null
      if (parts.size >= 2 && amount == null) {
        errors += "خطِ ${i + 1}: مبلغِ «${parts[1]}» خوانده نشد"
        return@forEachIndexed
      }
      if (parts.size > 2) {
        errors += "خطِ ${i + 1}: فقط «شناسه مبلغ» — بیش از دو بخش آمده"
        return@forEachIndexed
      }
      lines += Line(key, amount)
    }
    return Parsed(lines, errors)
  }

  /**
   *  @param invoiceTotalMinor مبلغِ کلِ صورت‌حساب
   *  @param invoiceCount شمارِ سفارش‌های صورت‌حساب (اگر شرکت گفته)
   *  @param lines ریزِ صورت‌حساب؛ خالی ⇒ فقط جمع‌ها سنجیده می‌شوند
   */
  fun check(rows: List<StatementRow>, invoiceTotalMinor: Long, invoiceCount: Int?, lines: List<Line>): Result {
    val delivered = rows.filter { it.status == "delivered" }
    val items = ArrayList<Item>()
    if (lines.isNotEmpty()) {
      val seen = HashSet<String>()
      val billed = HashSet<String>()
      for (l in lines) {
        val row = find(rows, l.key)
        if (row == null) {
          items += Item(Kind.UNKNOWN, l.key, null, "", null, l.amountMinor)
          continue
        }
        if (!seen.add(row.assignmentId)) {
          items += Item(Kind.DUPLICATE, l.key, row.orderNo, row.status, row.fareMinor, l.amountMinor, row.cashCollectedMinor)
          continue
        }
        billed += row.assignmentId
        val kind = when {
          row.status != "delivered" -> Kind.NOT_DELIVERED
          l.amountMinor != null && l.amountMinor != row.fareMinor -> Kind.AMOUNT_DIFF
          else -> Kind.MATCHED
        }
        items += Item(kind, l.key, row.orderNo, row.status, row.fareMinor, l.amountMinor, row.cashCollectedMinor)
      }
      for (r in delivered) if (r.assignmentId !in billed) {
        items += Item(Kind.NOT_BILLED, r.externalRef.ifEmpty { "#${r.orderNo}" }, r.orderNo, r.status, r.fareMinor, null, r.cashCollectedMinor)
      }
    }
    val ourFare = delivered.sumOf { it.fareMinor }
    return Result(
      items = items,
      ourDeliveredCount = delivered.size,
      ourDeliveredFareMinor = ourFare,
      billedCount = invoiceCount,
      billedTotalMinor = invoiceTotalMinor,
      amountDiffMinor = invoiceTotalMinor - ourFare,
      countDiff = invoiceCount?.let { it - delivered.size },
      cashHeldMinor = delivered.sumOf { it.cashCollectedMinor ?: 0L },
    )
  }

  /**
   *  اول شمارهٔ رهگیریِ شرکت؛ اگر نبود «#۱۲» یا «۱۲» = شمارهٔ سفارش. سفارشی
   *  که دوباره سپرده شده چند ردیف دارد ⇒ ردیفِ «تحویل شد» مقدم است، نه
   *  نخستین تلاشِ ناموفق.
   */
  private fun find(rows: List<StatementRow>, rawKey: String): StatementRow? {
    val key = OrderDraft.latin(rawKey).trim()
    val byRef = rows.filter { it.externalRef.isNotEmpty() && it.externalRef.equals(key, ignoreCase = true) }
    if (byRef.isNotEmpty()) return preferDelivered(byRef)
    val no = key.removePrefix("#").toLongOrNull() ?: return null
    return preferDelivered(rows.filter { it.orderNo == no })
  }

  private fun preferDelivered(candidates: List<StatementRow>): StatementRow? =
    candidates.firstOrNull { it.status == "delivered" } ?: candidates.firstOrNull()
}
