package ir.vil3ntec.tohid.ui.v3.money

import ir.vil3ntec.tohid.data.ShopData
import ir.vil3ntec.tohid.data.ShopStore
import ir.vil3ntec.tohid.data.Supplier

/**
 *  تأمین‌کننده‌ها (`purchasing`) و حسابِ هر کدام (`purchasing/suppliers/{id}`) — منطقِ خالص، زیرِ
 *  آزمون. بدهیِ ما همیشه `ShopStore.supplierDebt` است (خرید منهای پرداخت‌ها)؛ این‌جا فقط چیدن.
 */
internal object SuppliersModel {

  /** بدهکاریم / پیش‌پرداخت داریم / حساب صاف است */
  enum class State { OWE, PREPAID, SETTLED }

  fun state(debt: Double): State = when {
    debt > EPSILON -> State.OWE
    debt < -EPSILON -> State.PREPAID
    else -> State.SETTLED
  }

  data class Row(val supplier: Supplier, val debt: Double) {
    val state: State get() = state(debt)
  }

  /** همان ترتیبِ دفتر (ترتیبِ ثبت) — مثلِ امروز */
  fun rows(d: ShopData, hidden: Set<String> = emptySet()): List<Row> =
    d.suppliers.filter { it.id !in hidden }.map { Row(it, ShopStore.supplierDebt(d, it.id)) }

  /** «بدهیِ ما به تأمین‌کننده‌ها» — فقط بدهی‌ها؛ پیش‌پرداختِ یکی از بدهیِ دیگری کم نمی‌شود */
  fun owedTotal(d: ShopData): Double = d.suppliers.sumOf { ShopStore.supplierDebt(d, it.id).coerceAtLeast(0.0) }

  /* ------------------------------ حسابِ یک تأمین‌کننده ------------------------------ */

  data class Totals(val bought: Double, val paid: Double, val debt: Double)

  fun totals(d: ShopData, supplierId: String): Totals {
    val mine = d.purchases.filter { it.supplierId == supplierId }
    val paid = mine.sumOf { it.paidAmount } + d.supplierPayments.filter { it.supplierId == supplierId }.sumOf { it.amount }
    return Totals(bought = mine.sumOf { it.totalAmount }, paid = paid, debt = ShopStore.supplierDebt(d, supplierId))
  }

  enum class Kind { PURCHASE, PAYMENT }

  /**
   *  یک ردیفِ تاریخچه: خرید (کالا، مقدار × قیمت، جمع، «۲۰۰ ؋ باقی» / «تسویه») یا پرداخت.
   *  `left` ماندهٔ همان خرید در لحظهٔ ثبت است (`Purchase.debt`) — همان عددِ امروزِ صفحه.
   */
  data class Line(
    val id: String,
    val kind: Kind,
    val date: String,
    val at: Long,
    val productId: String?,
    val quantity: Double,
    val unit: String,
    val unitPrice: Double,
    val amount: Double,
    val left: Double,
    val notes: String,
  )

  /** خریدها و پرداخت‌ها در یک فهرست، تازه‌ترین اول */
  fun lines(d: ShopData, supplierId: String): List<Line> {
    val purchases = d.purchases.filter { it.supplierId == supplierId }.map { p ->
      Line(p.id, Kind.PURCHASE, p.date, p.createdAt, p.productId, p.quantity, p.unit, p.purchasePrice, p.totalAmount, p.debt, p.notes)
    }
    val payments = d.supplierPayments.filter { it.supplierId == supplierId }.map { p ->
      Line(p.id, Kind.PAYMENT, p.date, p.createdAt, null, 0.0, "", 0.0, p.amount, 0.0, p.notes)
    }
    return (purchases + payments).sortedWith(compareByDescending<Line> { it.date }.thenByDescending { it.at })
  }

  /** چند ردیفِ اول؛ بقیه با «نمایشِ همه» */
  const val SHOWN = 10

  private const val EPSILON = 0.001
}
