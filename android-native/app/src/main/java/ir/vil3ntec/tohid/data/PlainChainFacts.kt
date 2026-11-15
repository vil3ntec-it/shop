package ir.vil3ntec.tohid.data

import ir.vil3ntec.tohid.core.audit.DebtFacts
import ir.vil3ntec.tohid.core.audit.PlainFacts
import ir.vil3ntec.tohid.core.audit.PurchaseFacts
import ir.vil3ntec.tohid.core.audit.ReturnFacts
import ir.vil3ntec.tohid.core.audit.SupplierPayFacts
import ir.vil3ntec.tohid.core.money.Currency
import ir.vil3ntec.tohid.core.money.Money
import ir.vil3ntec.tohid.core.money.Qty

/**
 *  SEC-02 — اسنادِ بی‌شمارهٔ دفترِ امروز ⇐ `PlainFacts` ِ زنجیرهٔ اسناد (`core/audit/DocChainDocs`): هر ردیفِ برگشت
 *  (`returns`)، قرض/رسید (`transactions`)، خرید (`purchases`) و پرداخت به تأمین‌کننده (`supplierPayments`) یک سند.
 *
 *  فقط بخش‌های تغییرناپذیر و بی دادهٔ شخصی (شرح سرِ `DocChainDocs`): نه یادداشت، نه دلیل، نه سررسید، نه شمارهٔ فاکتورِ
 *  خرید، نه هزینهٔ جانبی، نه مُهرِ شیفت. مبلغ با `Money.fromLegacy` (پول) و مقدار با `Qty.fromLegacy` (هزارم) — همان
 *  قاعدهٔ `SaleChainFacts`. ردیفی که عددش نامعتبر است کنار می‌ماند (زنجیره نمی‌ایستد).
 */
object PlainChainFacts {

  private val AFN: Currency = Currency.AFN

  private fun minor(v: Double): Long = Money.fromLegacy(v, AFN).minor
  private fun milli(v: Double): Long = Qty.fromLegacy(v).milli

  fun of(d: ShopData): List<PlainFacts> {
    val out = ArrayList<PlainFacts>(d.saleReturns.size + d.transactions.size + d.purchases.size + d.supplierPayments.size)
    d.saleReturns.forEach { r -> runCatching { returnOf(r) }.getOrNull()?.let(out::add) }
    d.transactions.forEach { t -> runCatching { debtOf(t) }.getOrNull()?.let(out::add) }
    d.purchases.forEach { p -> runCatching { purchaseOf(p) }.getOrNull()?.let(out::add) }
    d.supplierPayments.forEach { p -> runCatching { payOf(p) }.getOrNull()?.let(out::add) }
    return out
  }

  fun returnOf(r: SaleReturn): ReturnFacts = ReturnFacts(
    id = r.id, postedAt = r.createdAt, currency = AFN.code,
    saleId = r.saleId, saleItemId = r.saleItemId, productId = r.productId,
    qty = milli(r.quantity), amount = minor(r.amount),
    docId = r.docId, disposition = r.disposition, refund = r.refund,
    toAccount = r.toAccount?.let(::minor), toCredit = r.toCredit?.let(::minor), creditTo = r.creditTo,
    unitPrice = r.unitPrice?.let(::minor),
  )

  fun debtOf(t: DebtTransaction): DebtFacts = DebtFacts(
    id = t.id, postedAt = t.createdAt, currency = AFN.code,
    customerId = t.debtorId, direction = t.type, amount = minor(t.amount), refId = t.refId, kind = t.kind,
  )

  fun purchaseOf(p: Purchase): PurchaseFacts = PurchaseFacts(
    id = p.id, postedAt = p.createdAt, currency = AFN.code,
    supplierId = p.supplierId, productId = p.productId, qty = milli(p.quantity), unitPrice = minor(p.purchasePrice),
    total = minor(p.totalAmount), paid = minor(p.paidAmount), debt = minor(p.debt),
  )

  fun payOf(p: SupplierPayment): SupplierPayFacts =
    SupplierPayFacts(id = p.id, postedAt = p.createdAt, currency = AFN.code, supplierId = p.supplierId, amount = minor(p.amount))
}
