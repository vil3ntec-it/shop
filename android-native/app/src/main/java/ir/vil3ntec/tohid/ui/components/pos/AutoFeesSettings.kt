package ir.vil3ntec.tohid.ui.components.pos

// i18n: سه‌زبانه (`StrSale`)

import ir.vil3ntec.tohid.core.product.ProductAlternatives
import ir.vil3ntec.tohid.core.text.TextNormalizer

/** نام یا بارکد ⇒ شناسه: بارکدِ کامل، سپس نامِ دقیق با کلیدِ مقایسه — همان برای صفحهٔ نسخهٔ ۳ (`AutoFeesScreen`) */
internal fun resolver(refs: List<ProductAlternatives.Ref>): (String) -> String? {
  val byCode = HashMap<String, String>()
  val byName = HashMap<String, String>()
  refs.forEach { r -> r.barcodes.forEach { byCode.putIfAbsent(it.trim(), r.id) }; byName.putIfAbsent(TextNormalizer.compareKey(r.name), r.id) }
  return { t -> byCode[ir.vil3ntec.tohid.latinDigits(t.trim())] ?: byName[TextNormalizer.compareKey(t)] }
}
