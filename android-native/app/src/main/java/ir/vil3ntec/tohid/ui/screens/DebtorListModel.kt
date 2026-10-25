package ir.vil3ntec.tohid.ui.screens

import ir.vil3ntec.tohid.LocaleText
import ir.vil3ntec.tohid.core.text.TextNormalizer
import ir.vil3ntec.tohid.data.Debtor
import ir.vil3ntec.tohid.data.DebtorEngine
import ir.vil3ntec.tohid.data.ShopData
import ir.vil3ntec.tohid.data.ShopStore

/**
 *  منطقِ خالصِ فهرستِ قرض‌داران (`DebtorsScreen`) — بی Compose، زیرِ آزمون.
 *
 *  جست‌وجو، چهار ترتیبِ تراشه‌ها، سه عددِ کارتِ بالا، متنِ زیرِ مبلغ و
 *  «چند روز» از همین‌جا می‌آیند تا ظاهرِ گوشی (`PhoneDebtors.kt`) فقط بکشد.
 *  مانده هیچ‌جا ذخیره نمی‌شود: همان `ShopStore.debt` (گرفته منهای پس‌داده).
 */
object DebtorListModel {

  /** یک کارت: خودِ قرض‌دار، مانده‌اش، و چند روز است که بدهکار است */
  data class Row(
    val debtor: Debtor,
    val balance: Double,
    /** `null` یعنی بدهکار نیست — قرصِ «N روز» کشیده نمی‌شود */
    val days: Long?,
  ) {
    /** قرمز (بدهکار) یا سبز (صاف یا پیش ما موجودی دارد) */
    val owes: Boolean get() = balance > 0
  }

  /** سه عددِ کارتِ بالا — از همهٔ دفتر، نه فقط آنچه جست‌وجو نشان می‌دهد */
  data class Summary(
    /** «تعداد قرض‌داران» */
    val people: Int,
    /** «جمع تمام قرض‌ها» — فقط مانده‌های بدهکار؛ موجودیِ یکی از قرضِ دیگری کم نمی‌شود */
    val owed: Double,
    /** «رسیدهای همه» — جمعِ همهٔ پول‌هایی که پس گرفته شده */
    val received: Double,
  )

  /**
   *  ترتیبِ فهرست — چهار تا، نه بیشتر: هر ترتیبِ اضافه یک تراشهٔ دیگر در
   *  ردیف است و انتخاب را سخت‌تر می‌کند.
   *
   *  هر ترتیب تا آخر یکتاست (مانده ⇐ نام ⇐ شناسه): با دو قرض‌دارِ هم‌مبلغ،
   *  جای کارت‌ها با هر بار باز شدنِ صفحه عوض نمی‌شود.
   */
  enum class Sort {
    /** بیشترین مبلغ اول — همان ترتیبی که فهرست از روزِ اول داشت */
    MOST,

    /** کمترین اول؛ کسی که پیشِ ما موجودی دارد (مانده‌ی منفی) سرِ فهرست */
    LEAST,

    /** قرضی که بیشتر مانده اول؛ کسی که بدهکار نیست ته می‌رود */
    OLDEST,

    /** الفبای فارسی (و پشتو)، بعد لاتین — نه ترتیبِ کدِ یونی‌کد */
    NAME;

    /** برچسبِ تراشه — از جدولِ نوشته‌ها (`StrMoney`) */
    val label: String get() = when (this) {
      MOST -> ir.vil3ntec.tohid.core.i18n.StrMoney.sortMost
      LEAST -> ir.vil3ntec.tohid.core.i18n.StrMoney.sortLeast
      OLDEST -> ir.vil3ntec.tohid.core.i18n.StrMoney.sortOldest
      NAME -> ir.vil3ntec.tohid.core.i18n.StrMoney.sortName
    }

    companion object {
      /** نامِ ذخیره‌شده ⇐ ترتیب؛ نامِ ناشناس (نسخهٔ دیگر) همان پیش‌فرض */
      fun parse(name: String?): Sort = entries.firstOrNull { it.name == name } ?: MOST
    }
  }

  private const val EPSILON = 0.001

  /* ------------------------------ جست‌وجو ------------------------------ */

  /**
   *  نام یا شماره. «contains» ِ دیروز هنوز شرطِ اول است (هر چه پیدا می‌شد
   *  باز پیدا می‌شود)؛ بعد کلیدِ نرمال‌شده — «علي» = «علی»، نیم‌فاصله و
   *  اعراب بی‌اثر — که جست‌وجوی صوتی بیشتر از همه به آن نیاز دارد.
   */
  fun matches(debtor: Debtor, term: String): Boolean {
    val q = term.trim()
    if (q.isEmpty()) return true
    return debtor.name.contains(q, ignoreCase = true) ||
      TextNormalizer.matches(debtor.name, q) ||
      LocaleText.phoneMatches(debtor.phone, q)
  }

  /* ------------------------------ ترتیب ------------------------------ */

  /**
   *  الفبا. کلید از `TextNormalizer.compareKey` می‌آید (ي/ی، ك/ک، آ/ا یکی؛
   *  نیم‌فاصله و فاصله بی‌اثر)، بعد هر حرف جایِ خودش را در الفبا می‌گیرد:
   *  رقم، حروفِ فارسی/پشتو به ترتیبِ الفبا، لاتین، و بقیه به ترتیبِ کد.
   */
  private const val ALPHABET =
    "ءابپتټثجځچڅحخدډذرړزژږسشښصضطظعغفقکګگلمنڼوهۀةیيېۍئ"

  private fun rank(c: Char): Int = when {
    c in '0'..'9' -> c - '0'
    ALPHABET.indexOf(c) >= 0 -> 100 + ALPHABET.indexOf(c)
    c in 'a'..'z' -> 200 + (c - 'a')
    else -> 1000 + c.code
  }

  /** مقایسهٔ دو نام به الفبا — خالی (بی‌نام) آخر */
  val byName: Comparator<String> = Comparator { a, b ->
    val ka = TextNormalizer.compareKey(a)
    val kb = TextNormalizer.compareKey(b)
    if (ka.isEmpty() != kb.isEmpty()) return@Comparator if (ka.isEmpty()) 1 else -1
    val n = minOf(ka.length, kb.length)
    for (i in 0 until n) {
      val d = rank(ka[i]).compareTo(rank(kb[i]))
      if (d != 0) return@Comparator d
    }
    ka.length.compareTo(kb.length)
  }

  private val tieBreak: Comparator<Row> =
    Comparator<Row> { a, b -> byName.compare(a.debtor.name, b.debtor.name) }.thenBy { it.debtor.id }

  fun comparator(sort: Sort): Comparator<Row> = when (sort) {
    Sort.MOST -> compareByDescending<Row> { it.balance }.then(tieBreak)
    Sort.LEAST -> compareBy<Row> { it.balance }.then(tieBreak)
    //  `-1` کسی را که بدهکار نیست از هر مدتی (حتی «امروز» = ۰) کوچک‌تر می‌کند
    Sort.OLDEST -> compareByDescending<Row> { it.days ?: -1L }.thenByDescending { it.balance }.then(tieBreak)
    Sort.NAME -> tieBreak.thenByDescending { it.balance }
  }

  fun sorted(rows: List<Row>, sort: Sort): List<Row> = rows.sortedWith(comparator(sort))

  /* ------------------------------ ردیف‌ها ------------------------------ */

  /** چند روز است که بدهکار است (قدیمی‌ترین قرضِ پس‌نداده، اول‌آمده اول‌رفته) — `null` یعنی نیست */
  fun days(d: ShopData, debtorId: String, now: Long): Long? = DebtorEngine.debtDays(d, debtorId, now)

  /** کسری‌های ریزِ اعشار (۰٫۱+۰٫۲−۰٫۳) «صفر»اند: کارتِ سبز و «حساب صاف است»، نه «۰ افغانی بدهکار» */
  fun row(d: ShopData, debtor: Debtor, now: Long): Row {
    val balance = ShopStore.debt(d, debtor.id).let { if (kotlin.math.abs(it) < EPSILON) 0.0 else it }
    return Row(debtor, balance, days(d, debtor.id, now))
  }

  /** کارت‌های صفحه: جست‌وجو، بعد ترتیب */
  fun rows(d: ShopData, term: String, sort: Sort, now: Long): List<Row> =
    sorted(d.debtors.filter { matches(it, term) }.map { row(d, it, now) }, sort)

  fun summary(d: ShopData): Summary = Summary(
    people = d.debtors.size,
    owed = d.debtors.sumOf { ShopStore.debt(d, it.id).coerceAtLeast(0.0) },
    received = d.transactions.filter { it.type == "receive" }.sumOf { it.amount },
  )

  /* ------------------------------ متنِ کارت ------------------------------ */

  /** زیرِ مبلغ: شماره اگر هست، وگرنه «۱٬۰۰۰ افغانی بدهکار» / «حساب صاف است» */
  fun subtitle(row: Row): String =
    if (row.debtor.phone.isNotBlank()) LocaleText.phoneDisplay(row.debtor.phone)
    else ir.vil3ntec.tohid.ui.v3.money.MoneyText.state(row.balance)

  /** قرصِ ساعت: فقط وقتی بدهکار است */
  fun daysLabel(row: Row): String? = row.days?.takeIf { row.owes }?.let { ir.vil3ntec.tohid.ui.v3.money.MoneyText.days(it) }
}
