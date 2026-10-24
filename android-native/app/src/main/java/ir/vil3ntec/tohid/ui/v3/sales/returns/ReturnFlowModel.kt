package ir.vil3ntec.tohid.ui.v3.sales.returns

import ir.vil3ntec.tohid.core.i18n.StrSale
import ir.vil3ntec.tohid.core.text.TextNormalizer
import ir.vil3ntec.tohid.data.Product
import ir.vil3ntec.tohid.data.ReturnFlow
import ir.vil3ntec.tohid.data.Sale
import ir.vil3ntec.tohid.data.SaleItem
import ir.vil3ntec.tohid.data.ShopData
import ir.vil3ntec.tohid.formatDate
import ir.vil3ntec.tohid.money
import ir.vil3ntec.tohid.qty
import ir.vil3ntec.tohid.ui.components.pos.PhoneSaleText

/** یک ردیفِ برگشتِ بی رسید (نوشته‌ها) */
data class ReturnFree(val productId: String, val qty: String = "1", val price: String, val disposition: String = ReturnFlow.RESTOCK)

/**
 *  متن‌ها و تبدیل‌های برگهٔ «برگشت و تعویض» — خالص. حساب همه `ReturnFlow` است؛ این‌جا فقط
 *  نوشته ⇐ عدد، برچسبِ بازپرداخت و سد، و جست‌وجوی کالا (همان شش نتیجهٔ پیشین).
 */
object ReturnFlowModel {

  fun number(text: String?): Double = text?.let { ir.vil3ntec.tohid.latinDigits(it).toDoubleOrNull() } ?: 0.0

  fun freeLines(free: List<ReturnFree>): List<ReturnFlow.FreeLine> =
    free.map { ReturnFlow.FreeLine(it.productId, number(it.qty), number(it.price), it.disposition) }

  fun freeRow(p: Product): ReturnFree = ReturnFree(p.id, price = money(p.salePrice).replace(",", ""))

  fun exchangeNote(due: Double): String =
    if (due >= 0) StrSale.exchangeReceive(PhoneSaleText.amount(due)) else StrSale.exchangePay(PhoneSaleText.amount(-due))

  /** «۱۴۰۵/۰۷/۲۸ · ۱٬۲۰۰ ؋ · نامِ مشتری» */
  fun saleLine(d: ShopData, s: Sale): String =
    listOfNotNull(formatDate(s.date), PhoneSaleText.amount(s.finalTotal), s.debtorId?.let { id -> d.debtors.find { it.id == id }?.name }).joinToString(" · ")

  fun itemLine(item: SaleItem, unit: String): String =
    StrSale.returnableEach(qty(ReturnFlow.returnable(item)) + if (unit.isNotBlank()) " $unit" else "", PhoneSaleText.amount(item.totalPrice / item.quantity.coerceAtLeast(1e-9)))

  /** سه راهِ بازپرداخت؛ بی رسید «روشِ اصلی» همان نقد است و «نقد» جدا ندارد */
  fun modes(hasSale: Boolean): List<Pair<String, String>> = buildList {
    add(ReturnFlow.ORIGINAL to if (hasSale) StrSale.originalMethod else StrSale.cashWord)
    if (hasSale) add(ReturnFlow.CASH to StrSale.cashWord)
    add(ReturnFlow.STORE_CREDIT to StrSale.storeCredit)
  }

  fun splitRows(s: ReturnFlow.Split): List<Pair<String, Double>> = buildList {
    if (s.account > 0) add(StrSale.offInvoiceDebt to s.account)
    if (s.credit > 0) add(StrSale.storeCreditOwed to s.credit)
    s.methods.forEach { (m, a) -> add(StrSale.backToMethod(ReturnFlow.methodName(m)) to a) }
    if (s.cash > 0) add(StrSale.cashFromDrawer to s.cash)
  }

  fun approverText(role: String): String = if (role == "owner") StrSale.shopOwner else StrSale.managerOrOwner

  fun whyText(reasons: List<String>): String = reasons.joinToString("، ") {
    when (it) {
      ReturnFlow.WHY_NO_RECEIPT -> StrSale.whyNoReceipt
      ReturnFlow.WHY_DAMAGED -> StrSale.damagedWord
      ReturnFlow.WHY_OVER_LIMIT -> StrSale.whyOverLimit
      else -> StrSale.whyPermission
    }
  }

  fun gateText(gate: ReturnFlow.Gate, approved: Boolean, approval: ReturnFlow.Approval?): String = when {
    gate.decision == "deny" -> StrSale.returnDenied
    approved -> StrSale.approvedBy(approval?.by.orEmpty())
    else -> StrSale.approvalNeededFor(approverText(gate.approver), whyText(gate.reasons))
  }

  fun hits(d: ShopData, term: String): List<Product> {
    val t = ir.vil3ntec.tohid.latinDigits(term).trim()
    if (t.isEmpty()) return emptyList()
    return d.products.filter { it.active && (TextNormalizer.matches(it.name, t) || t in it.barcodes) }.take(6)
  }
}
