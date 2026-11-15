package ir.vil3ntec.tohid.data

/**
 *  PERF-SCREEN — بدهیِ ما به **هر** تأمین‌کننده در یک گذر، با همان قاعدهٔ یک‌خانه‌ایِ `DebtRows` روی همان دو فهرست
 *  (`purchases`، `supplierPayments`). پیش‌تر `ShopStore.supplierDebt` برای هر تأمین‌کننده هر دو فهرست را می‌گشت و خانه
 *  (عددها و هشدارها) و «خرید» آن را برای همهٔ تأمین‌کنندگان صدا می‌زدند (۲۰۰ × ۶ هزار، دو بار در هر باز شدنِ خانه).
 *
 *  جمع‌ها همان جمعِ `filter { … }.sumOf { … }` اند: ردیف‌های هر تأمین‌کننده به همان ترتیبِ دفتر، از ۰ و پشتِ هم (بیت‌به‌بیت
 *  همان عدد، `ScreenSpeedEquivalenceTest`). تأمین‌کننده‌ای که ردیفی ندارد ⇒ صفر، همان `0.0 − (0.0 + 0.0)`.
 */
internal object SupplierDebts {

  private class Key(val purchases: List<Purchase>, val payments: List<SupplierPayment>, val debts: Map<String, Double>)

  @Volatile private var last: Key? = null

  fun of(d: ShopData): Map<String, Double> {
    last?.let { if (it.purchases === d.purchases && it.payments === d.supplierPayments) return it.debts }
    val billed = HashMap<String, Double>()
    val paidRows = HashMap<String, Double>()
    val paidMore = HashMap<String, Double>()
    for (p in d.purchases) {
      billed[p.supplierId] = (billed[p.supplierId] ?: 0.0) + p.totalAmount
      paidRows[p.supplierId] = (paidRows[p.supplierId] ?: 0.0) + p.paidAmount
    }
    for (x in d.supplierPayments) paidMore[x.supplierId] = (paidMore[x.supplierId] ?: 0.0) + x.amount
    val out = HashMap<String, Double>(billed.size + paidMore.size)
    for (id in billed.keys + paidMore.keys) out[id] = (billed[id] ?: 0.0) - ((paidRows[id] ?: 0.0) + (paidMore[id] ?: 0.0))
    return out.also { last = Key(d.purchases, d.supplierPayments, it) }
  }
}
