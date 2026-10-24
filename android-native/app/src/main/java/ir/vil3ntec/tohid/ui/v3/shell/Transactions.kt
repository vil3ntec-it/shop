package ir.vil3ntec.tohid.ui.v3.shell

import ir.vil3ntec.tohid.core.i18n.Str
import ir.vil3ntec.tohid.core.i18n.StrHome
import ir.vil3ntec.tohid.data.Purchase
import ir.vil3ntec.tohid.data.Sale
import ir.vil3ntec.tohid.data.SalesEngine
import ir.vil3ntec.tohid.data.ShopData
import ir.vil3ntec.tohid.data.invoiceLabel
import ir.vil3ntec.tohid.data.isOnlineOrder
import ir.vil3ntec.tohid.isoMillis

/**
 *  «تراکنش‌ها» ِ خانه (دستورِ صاحب محصول، owner-feedback-3 B، شکلِ عکسِ ۳): هر فروش، هر
 *  سفارشِ آنلاین و هر خرید یک ردیف — جهت (پول آمد/رفت)، چه کسی/چه چیزی، زمان، مبلغ — و
 *  زدنِ ردیف ⇐ برگهٔ اقلام. منطقِ خالص و فقط خواندنِ دفتر: مبلغ‌ها همان `finalTotal` ِ فاکتور
 *  و `totalAmount` ِ خرید، اقلام همان `SaleItem` ِ لحظهٔ فروش (همان که برگهٔ قرض‌دار نشان
 *  می‌دهد). خریدِ چندردیفه (`invoiceNo`) یک تراکنش است. آزمون: `TransactionsTest`.
 */
object Transactions {
  enum class Kind { SALE, ONLINE, PURCHASE }

  /** یک قلم — همان شکلِ `DebtBook.Line` (نام، مقدار، واحد، قیمتِ یک، جمع، مرجوعی) */
  data class Line(val name: String, val quantity: Double, val unit: String, val unitPrice: Double, val total: Double, val returned: Double = 0.0)

  data class Tx(
    val key: String,
    val kind: Kind,
    /** پول به دکان آمد (فروش/سفارش) یا رفت (خرید) */
    val incoming: Boolean,
    val who: String,
    val at: Long,
    val date: String,
    val amount: Double,
    val itemCount: Int,
    val invoice: String?,
    val cancelled: Boolean,
    /** فروش ⇐ شناسهٔ فاکتور؛ خرید ⇐ شناسهٔ تأمین‌کننده (برای «دیدن») */
    val refId: String?,
    /** ردیف‌های خریدِ همین تراکنش */
    val purchaseIds: List<String> = emptyList(),
  ) {
    /** مقصدِ «دیدنِ فاکتور / تأمین‌کننده» */
    val route: String? get() = when {
      refId == null -> null
      kind == Kind.PURCHASE -> ShellTargets.supplier(refId)
      else -> ShellTargets.invoice(refId)
    }
  }

  fun kindLabel(k: Kind): String = when (k) {
    Kind.SALE -> Str.sale
    Kind.ONLINE -> StrHome.txOnline
    Kind.PURCHASE -> Str.purchases
  }

  private fun stamp(createdAt: Long, date: String): Long = if (createdAt > 0) createdAt else isoMillis(date)

  private fun ofSale(d: ShopData, s: Sale, names: Map<String, String>, counts: Map<String, Int>): Tx = Tx(
    key = "s:" + s.id,
    kind = if (s.isOnlineOrder()) Kind.ONLINE else Kind.SALE,
    incoming = true,
    who = s.debtorId?.let { names[it] } ?: Str.walkIn,
    at = stamp(s.createdAt, s.date),
    date = s.date,
    amount = s.finalTotal,
    itemCount = counts[s.id] ?: 0,
    invoice = s.invoiceLabel(),
    cancelled = s.status == "cancelled",
    refId = s.id,
  )

  /** خریدهای یک فاکتور (`invoiceNo` و تأمین‌کنندهٔ یکسان) یک تراکنش؛ خریدِ تک‌ردیفهٔ قدیمی جدا */
  private fun purchaseGroups(d: ShopData): List<List<Purchase>> =
    d.purchases.groupBy { p -> if (p.invoiceNo > 0) "${p.supplierId}#${p.invoiceNo}" else "id:${p.id}" }.values.toList()

  private fun ofPurchase(d: ShopData, rows: List<Purchase>, suppliers: Map<String, String>, products: Map<String, String>): Tx {
    val first = rows.first()
    val supplier = suppliers[first.supplierId]
    return Tx(
      key = "p:" + first.id,
      kind = Kind.PURCHASE,
      incoming = false,
      who = supplier ?: products[first.productId] ?: Str.purchases,
      at = rows.maxOf { stamp(it.createdAt, it.date) },
      date = first.date,
      amount = rows.sumOf { it.totalAmount },
      itemCount = rows.size,
      invoice = first.invoiceNo.takeIf { it > 0 }?.toString(),
      cancelled = false,
      refId = first.supplierId.takeIf { it.isNotBlank() && supplier != null },
      purchaseIds = rows.map { it.id },
    )
  }

  /**
   *  همهٔ تراکنش‌ها، تازه‌ترین بالا (`kind` ⇐ فقط همان نوع). `purchases = false` برای کسی که
   *  عددهای مالی را نمی‌بیند (`ShopRole.canSeeMoney`): قیمتِ خرید سود را لو می‌دهد.
   */
  fun list(d: ShopData, kind: Kind? = null, purchases: Boolean = true): List<Tx> {
    val names = d.debtors.associate { it.id to it.name }
    val counts = d.saleItems.groupingBy { it.saleId }.eachCount()
    val sales = if (kind == Kind.PURCHASE) emptyList() else d.sales.map { ofSale(d, it, names, counts) }.filter { kind == null || it.kind == kind }
    val bought = if (purchases && (kind == null || kind == Kind.PURCHASE)) {
      val suppliers = d.suppliers.associate { it.id to it.name }
      val products = d.products.associate { it.id to it.name }
      purchaseGroups(d).map { ofPurchase(d, it, suppliers, products) }
    } else emptyList()
    return (sales + bought).sortedByDescending { it.at }
  }

  /** چند تراکنشِ تازه برای خانه */
  fun recent(d: ShopData, n: Int = 5, purchases: Boolean = true): List<Tx> = list(d, purchases = purchases).take(n)

  /** اقلامِ یک تراکنش — فروش: اقلامِ فاکتور با قیمتِ همان لحظه؛ خرید: ردیف‌های فاکتورِ خرید */
  fun lines(d: ShopData, tx: Tx): List<Line> {
    val units = d.products.associate { it.id to it.unit }
    return if (tx.kind == Kind.PURCHASE) {
      val ids = tx.purchaseIds.toSet()
      val names = d.products.associate { it.id to it.name }
      d.purchases.filter { it.id in ids }.map { p ->
        Line(names[p.productId] ?: StrHome.txDeletedItem, p.quantity, p.unit.ifBlank { units[p.productId].orEmpty() }, p.purchasePrice, p.totalAmount)
      }
    } else d.saleItems.filter { it.saleId == tx.refId }.map { i ->
      Line(SalesEngine.itemName(d, i), i.quantity, units[i.productId].orEmpty(), i.unitPrice, i.totalPrice, i.returnedQty)
    }
  }

  /** فاکتورِ پشتِ تراکنشِ فروش (تخفیف و پرداختِ همان لحظه در برگه) */
  fun sale(d: ShopData, tx: Tx): Sale? = if (tx.kind == Kind.PURCHASE) null else d.sales.firstOrNull { it.id == tx.refId }
}
