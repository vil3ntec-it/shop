package ir.vil3ntec.tohid.ui.v3.analytics

import ir.vil3ntec.tohid.data.AuditEntry
import ir.vil3ntec.tohid.data.ShopData
import ir.vil3ntec.tohid.ui.v3.money.MoneyPaths
import ir.vil3ntec.tohid.ui.v3.products.ProductsPaths
import ir.vil3ntec.tohid.ui.v3.sales.SalesPaths
import ir.vil3ntec.tohid.ui.v3.stock.StockPaths

/**
 *  سابقهٔ عملیات (`audit?kind=`): فیلترِ نوع، گروه‌بندیِ روزانه و مقصدِ هر ردیف — خالص،
 *  با آزمون (`AuditModelTest`). دفتر فقط خوانده می‌شود؛ هیچ ردیفی ساخته یا عوض نمی‌شود.
 */
object AuditModel {
  /** همهٔ نوع‌ها به ترتیبِ برگهٔ «نوعِ رویداد» (همان AUDIT_TYPE_LABELS ِ وب) */
  val KINDS = listOf(
    "sale", "cancel_sale", "return", "purchase", "customer_payment", "supplier_payment", "stock_adjustment",
    "supplier_return", "price_change", "product_edit", "shift_open", "shift_close", "delete_product",
    "debtor_delete", "debt_tx_delete", "expense_delete", "delete_entry", "data_export", "debt_due_set",
    "credit_over_limit", "credit_limit_set",
  )

  /** پاک‌کردن‌ها — قرصِ قرمزِ «حذف» */
  val DELETIONS = setOf("delete_product", "debtor_delete", "debt_tx_delete", "expense_delete", "delete_entry")

  fun isDeletion(type: String): Boolean = type in DELETIONS

  /** شمارِ هر نوع در دفتر */
  fun counts(log: List<AuditEntry>): Map<String, Int> = log.groupingBy { it.type }.eachCount()

  /** پنج تراشهٔ سریع: پرتکرارترین نوع‌های موجود (برابر ⇒ ترتیبِ `KINDS`) */
  fun quickKinds(log: List<AuditEntry>, n: Int = 5): List<String> =
    counts(log).entries.sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { order(it.key) })
      .take(n).map { it.key }

  /** همهٔ نوع‌ها برای برگه: نوع‌های شناخته + هر نوعِ ناشناخته‌ای که در دفتر هست */
  fun sheetKinds(log: List<AuditEntry>): List<Pair<String, Int>> {
    val c = counts(log)
    return (KINDS + c.keys.filter { it !in KINDS }.sorted()).map { it to (c[it] ?: 0) }
  }

  /** ردیف‌های یک نوع (null = همه)، تازه‌ترین اول */
  fun filter(log: List<AuditEntry>, kind: String?): List<AuditEntry> =
    log.filter { kind == null || it.type == kind }.sortedWith(compareByDescending<AuditEntry> { it.date }.thenByDescending { it.createdAt })

  /** گروهِ روزانه به ترتیبِ همان فهرست */
  fun byDay(rows: List<AuditEntry>): List<Pair<String, List<AuditEntry>>> =
    rows.groupBy { it.date }.toList()

  /**
   *  مقصدِ ردیف وقتی همان مورد هنوز در دفتر هست: فاکتور، کالا، قرض‌دار، تأمین‌کننده، انبارِ
   *  کالا یا صندوق. مورد رفته (پاک‌شده) ⇒ null و صفحه جزئیاتِ فقط‌خواندنی را نشان می‌دهد.
   */
  fun target(e: AuditEntry, d: ShopData): String? {
    val ref = e.refId
    return when (e.type) {
      "shift_open", "shift_close" -> SalesPaths.DRAWER
      else -> if (ref.isNullOrBlank()) null else resolve(e.type, ref, d)
    }
  }

  private fun resolve(type: String, ref: String, d: ShopData): String? {
    fun sale() = d.sales.firstOrNull { it.id == ref }?.let { SalesPaths.invoice(it.id) }
    fun product() = d.products.firstOrNull { it.id == ref }?.let { ProductsPaths.item(it.id) }
    fun debtor(id: String = ref) = d.debtors.firstOrNull { it.id == id }?.let { MoneyPaths.debtor(it.id) }
    fun supplier(id: String) = d.suppliers.firstOrNull { it.id == id }?.let { MoneyPaths.supplier(it.id) }
    return when (type) {
      "sale", "cancel_sale", "return" -> sale()
      "price_change", "product_edit" -> product()
      "customer_payment", "credit_over_limit", "credit_limit_set", "debt_tx_delete" -> debtor()
      "debt_due_set" -> d.transactions.firstOrNull { it.id == ref }?.let { debtor(it.debtorId) }
      "purchase" -> d.purchases.firstOrNull { it.id == ref }?.let { supplier(it.supplierId) }
      "supplier_payment" -> d.supplierPayments.firstOrNull { it.id == ref }?.let { supplier(it.supplierId) }
      "stock_adjustment", "supplier_return" ->
        d.warehouseEntries.firstOrNull { it.id == ref }?.productId?.takeIf { id -> d.products.any { it.id == id } }?.let { StockPaths.item(it) }
      else -> null
    }
  }

  private fun order(type: String): Int = KINDS.indexOf(type).let { if (it < 0) KINDS.size else it }
}
