package ir.vil3ntec.tohid.ui.screens

import ir.vil3ntec.tohid.core.stock.StockLevelRules
import ir.vil3ntec.tohid.qty

/*
 *  تکه‌های خالصِ صفحهٔ کالاهای کامپیوتر (`DeskProducts`) — بیرون آمده تا آن پرونده از سقفِ اندازه‌اش (`tools/quality`) نگذرد.
 */

/**
 *  PRD-12 — کادرِ «قیمتِ عمده» ِ کامپیوتر (ردیفِ «کالای تازه» و پنلِ ویرایش، فقط با «لیستِ قیمت در صندوق»): خالی ⇒ ۰ (= عمده
 *  ندارد، همان `ProductFormModel.draftEdit`)، نادرست ⇒ NaN تا موتور با پیامِ روشن (`StrGoods.wholesaleBad`) رد کند.
 */
internal fun deskWholesale(text: String): Double = text.trim().takeIf { it.isNotEmpty() }?.let { it.toDoubleOrNull() ?: Double.NaN } ?: 0.0

/** متنِ همان کادر از کالا: ۰ (= ندارد) ⇒ خالی */
internal fun deskWholesaleText(price: Double): String = if (price == 0.0) "" else qty(price)

/** «کمینه ۵ ⇐ ۱۰، نقطهٔ سفارش — ⇐ ۲۰» یا ایرادِ ردیف */
internal fun levelsNote(p: StockLevelRules.Planned): String {
  if (!p.ok) return p.problems.joinToString("، ") { it.fa }
  fun t(m: Long?) = m?.let { qty(it / 1000.0) } ?: "—"
  val parts = buildList {
    if (p.before.minMilli != p.after.minMilli) add(ir.vil3ntec.tohid.core.i18n.StrProductPages.minChange(t(p.before.minMilli), t(p.after.minMilli)))
    if (p.before.reorderMilli != p.after.reorderMilli) add(ir.vil3ntec.tohid.core.i18n.StrProductPages.reorderChange(t(p.before.reorderMilli), t(p.after.reorderMilli)))
    if (p.before.maxMilli != p.after.maxMilli) add(ir.vil3ntec.tohid.core.i18n.StrProductPages.maxChange(t(p.before.maxMilli), t(p.after.maxMilli)))
  }
  return if (parts.isEmpty()) ir.vil3ntec.tohid.core.i18n.StrProductPages.alreadySo else parts.joinToString("، ")
}

/** متنِ کادر ⇒ تغییرِ حد؛ `null` یعنی خالی (بی‌تغییر) یا نادرست — `bad` این دو را جدا می‌کند */
internal fun levelEdit(text: String, clear: Boolean, zeroClears: Boolean = false): StockLevelRules.Edit? {
  if (clear) return StockLevelRules.Edit.Clear
  val v = text.trim().takeIf { it.isNotEmpty() }?.toDoubleOrNull()?.takeIf { it.isFinite() && it >= 0 } ?: return null
  if (zeroClears && v == 0.0) return StockLevelRules.Edit.Clear
  return runCatching { StockLevelRules.Edit.To(ir.vil3ntec.tohid.core.money.Qty.fromLegacy(v).milli) }.getOrNull()
}
