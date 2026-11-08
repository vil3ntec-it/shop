package ir.vil3ntec.tohid.data

/**
 *  PERF-LEDGER — تراکنش‌های هر قرض‌دار در **یک** گذر، با همان قاعدهٔ یک‌خانه‌ایِ `ShopStore.index`، روی **همان فهرستِ**
 *  `transactions` (بدهی جز آن به چیزی تکیه ندارد؛ پرچمِ `shop.debt_minor` هر بار در `DebtMinorFlag.balance` خوانده
 *  می‌شود، نه این‌جا). شرح در `docs/PERF-LEDGER-fa.md`.
 *
 *  ⛔ پیش‌تر هر `ShopStore.debt` کلِ تراکنش‌ها را می‌گشت و فهرستِ مشتری‌ها (`CustomerPickerModel`، `ShopAlertsSource`،
 *  `SearchIndex`، جمعِ گزارش‌ها) آن را برای هر مشتری روی رشتهٔ صفحه صدا می‌زد: ۵هزار مشتری × ۳۰هزار تراکنش = ۲٫۵ ثانیه
 *  روی JVM در هر ترسیم — روی گوشیِ ضعیف بالای ۵ ثانیه (ANR). `groupBy` همان ردیف‌ها را به همان ترتیبِ `filter` نگه
 *  می‌دارد، پس جمع همان جمع است (`LedgerPerfEquivalenceTest`).
 */
internal object DebtRows {

  @Volatile private var last: Pair<List<DebtTransaction>, Map<String, List<DebtTransaction>>>? = null

  fun of(d: ShopData): Map<String, List<DebtTransaction>> {
    val src = d.transactions
    last?.let { if (it.first === src) return it.second }
    return src.groupBy { it.debtorId }.also { last = src to it }
  }
}
