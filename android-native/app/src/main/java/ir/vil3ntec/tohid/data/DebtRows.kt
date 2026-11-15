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
 *
 *  CRM-10: کلید پروندهٔ **ماندگار** است (`CustomerAlias`) — قرضِ مانده روی مشتریِ ادغام‌شده (ادغامِ بی ویرایشِ سندِ v3، یا ردیفِ
 *  دیررسیده پیش از `sweep`) در ماندهٔ ماندگار است و ادغام‌شده ماندهٔ جدا ندارد؛ بی ادغام همان `groupBy` ِ پیشین.
 */
internal object DebtRows {

  private class Key(val txs: List<DebtTransaction>, val debtors: List<Debtor>, val rows: Map<String, List<DebtTransaction>>)

  @Volatile private var last: Key? = null

  fun of(d: ShopData): Map<String, List<DebtTransaction>> {
    last?.let { if (it.txs === d.transactions && it.debtors === d.debtors) return it.rows }
    return CustomerAlias.groupByKeeper(d, d.transactions) { it.debtorId }.also { last = Key(d.transactions, d.debtors, it) }
  }
}
