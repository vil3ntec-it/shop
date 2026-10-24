package ir.vil3ntec.tohid.ui.v3.online.orders

import ir.vil3ntec.tohid.core.i18n.StrOnline
import ir.vil3ntec.tohid.core.orders.OrderDraft

/**
 *  حالتِ خالصِ فرمِ «سفارشِ دستی» (`orders/new`، ORD-21) — خطای هر کادر زیرِ خودش، با
 *  همان قاعده‌های `OrderDraft.build` (همان `scaled` برای مقدار و پول). داوریِ نهایی همچنان
 *  `OrderDraft.build` است؛ این فقط می‌گوید کدام کادر. مبلغ را سرور می‌سازد، نه این‌جا.
 *  آزمون: `NewOrderModelTest`.
 */
object NewOrderModel {

  enum class Field { NAME, PHONE, ADDRESS, FEE, LINES }
  enum class LineField { NAME, QTY, PRICE }

  data class Errors(
    val fields: Map<Field, String> = emptyMap(),
    val lines: Map<Int, Map<LineField, String>> = emptyMap(),
  ) {
    val isEmpty: Boolean get() = fields.isEmpty() && lines.isEmpty()
    fun line(i: Int): String? = lines[i]?.values?.firstOrNull()
  }

  /** بیشینهٔ نویسه‌ها — همان فرمِ پیشین */
  const val NAME_MAX = 80
  const val PHONE_MAX = 20
  const val ADDRESS_MAX = 300
  const val LANDMARK_MAX = 200
  const val NUMBER_MAX = 14
  const val PRICE_MAX = 16
  const val LINE_NAME_MAX = 200
  const val NOTE_MAX = 500

  val DEFAULT_SOURCES = listOf("phone", "telegram", "whatsapp", "manual")

  fun sources(server: List<String>): List<String> = server.ifEmpty { DEFAULT_SOURCES }

  fun isBlank(l: OrderDraft.LineInput): Boolean = l.name.isBlank() && l.qty.isBlank() && l.price.isBlank()

  fun lineErrors(l: OrderDraft.LineInput): Map<LineField, String> {
    if (isBlank(l)) return emptyMap()
    val out = LinkedHashMap<LineField, String>()
    if (l.name.isBlank()) out[LineField.NAME] = StrOnline.errLineName
    val q = OrderDraft.scaled(l.qty.trim().ifEmpty { "1" }, 3)
    if (q == null || q == 0L) out[LineField.QTY] = StrOnline.errQty
    if (OrderDraft.scaled(l.price.trim(), 2) == null) out[LineField.PRICE] = StrOnline.errPrice
    return out
  }

  fun errors(f: OrderDraft.Form): Errors {
    val delivery = f.fulfillment == "delivery"
    val fields = LinkedHashMap<Field, String>()
    if (f.name.isBlank()) fields[Field.NAME] = StrOnline.errName
    if (delivery && f.phone.isBlank()) fields[Field.PHONE] = StrOnline.errPhoneDelivery
    if (delivery && f.addressText.isBlank() && f.landmark.isBlank()) fields[Field.ADDRESS] = StrOnline.errAddress
    if (delivery && f.deliveryFee.isNotBlank() && OrderDraft.scaled(f.deliveryFee, 2) == null) fields[Field.FEE] = StrOnline.errFee
    val lines = f.lines.withIndex().associate { (i, l) -> i to lineErrors(l) }.filterValues { it.isNotEmpty() }
    if (f.lines.all(::isBlank)) fields[Field.LINES] = StrOnline.errNoLines
    return Errors(fields, lines)
  }

  /** چند قلمِ پُرشده — برای خطِ خلاصهٔ نوارِ پایین */
  fun filled(f: OrderDraft.Form): Int = f.lines.count { !isBlank(it) }

  fun dirty(f: OrderDraft.Form): Boolean = f != OrderDraft.Form()
}
