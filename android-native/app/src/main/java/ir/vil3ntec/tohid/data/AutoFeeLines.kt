package ir.vil3ntec.tohid.data

import ir.vil3ntec.tohid.core.sale.AutoFees

/**
 *  POS-36 — هزینهٔ خودکار در سبد (منطقِ خالص: `core/sale/AutoFees`). هر هزینه یک قلمِ آزاد با کلیدِ
 *  `fee:…` است؛ با هر تغییرِ سبد (`CartStore.set`) قلم‌های هزینهٔ پیشین بیرون و تازه‌ها ته سبد.
 *
 *  ⛔ بی قاعده (پیش‌فرض، `AutoFeesPref`) ⇒ همان فهرست، همان شیء: رفتارِ امروز بایت‌به‌بایت.
 */
object AutoFeeLines {

  fun isFee(line: SalesEngine.CartLine): Boolean = line.free && AutoFees.isFee(line.productId)

  fun apply(lines: List<SalesEngine.CartLine>, rules: List<AutoFees.Rule>): List<SalesEngine.CartLine> {
    if (rules.isEmpty()) return lines
    val base = lines.filterNot(::isFee)
    val fees = AutoFees.fees(base.map { AutoFees.Line(it.productId, Math.round(it.quantity * 1000.0), if (it.free) null else it.productId) }, rules)
    val out = base + fees.map { SalesEngine.CartLine(productId = it.key, quantity = it.qtyMilli / 1000.0, unitPrice = it.unitMinor / 100.0, label = it.label) }
    return if (out == lines) lines else out
  }
}
