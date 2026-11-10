package ir.vil3ntec.tohid.data

import ir.vil3ntec.tohid.normalizeDigits

/**
 *  کادرِ صندوقِ کامپیوتر: «۳*کد» یعنی سه تا از همان کد.
 *
 *  F0-06 — پیش از این الگو فقط `[0-9]` را می‌شناخت. با چیدمانِ فارسی یا
 *  پشتو «۳*…» نامِ کالا حساب می‌شد، نه «سه تا»، و بارکدِ فارسی‌رقم در
 *  پیشنهادها پیدا نمی‌شد. حالا عدد پس از `normalizeDigits` خوانده می‌شود.
 *
 *  بخشِ پس از ستاره **دست نمی‌خورد** (فقط جایش از روی متنِ یک‌شکل‌شده
 *  پیدا می‌شود؛ `normalizeDigits` یک نویسه به یک نویسه است)، تا نامِ کالا
 *  با رقمِ فارسی — «برنج ۵ کیلویی» — هم پیدا شود. بارکد را خودِ
 *  `BarcodeMatch` یک‌شکل می‌کند.
 */
object CartQuery {

  data class Parsed(val count: Double, val term: String)

  private val COUNT = Regex("^\\s*([0-9]+(?:\\.[0-9]+)?)\\s*[*×xX]\\s*(.*)$")

  fun parse(query: String): Parsed {
    val m = COUNT.find(normalizeDigits(query)) ?: return Parsed(1.0, query)
    val count = m.groupValues[1].toDoubleOrNull() ?: return Parsed(1.0, query)
    val rest = m.groups[2]?.range?.first ?: query.length
    return Parsed(count, query.substring(rest.coerceAtMost(query.length)))
  }

  /**
   *  POS-03 — متنی که **خودش** بارکدِ کامل است («12x34») بر پیشوند پیش است: همان `decide` ِ صندوقِ وب
   *  (`license/cart-query.js`؛ بخشِ `fullCode` ِ بردارِ `cart-query-v1.json`). [isCode] فقط وقتی پرسیده می‌شود که پیشوندی
   *  خوانده شد، با متنِ trim‌شده؛ بارکد ⇒ (۱، همان متن). پیش از این «12x34» ِ کالای «باتری» دوازده تا از کالای «34» می‌شد.
   */
  fun parse(query: String, isCode: (String) -> Boolean): Parsed {
    val p = parse(query)
    if (p.term.length == query.length) return p
    val whole = query.trim()
    return if (whole.isNotEmpty() && isCode(whole)) Parsed(1.0, query) else p
  }

  /** پیشنهادِ کالا: آیا یکی از بارکدها با این نوشته شروع می‌شود — رقمِ فارسی و لاتین یکی است */
  fun barcodeStarts(barcodes: List<String>, term: String): Boolean {
    if (barcodes.any { it.startsWith(term) }) return true
    val t = normalizeDigits(term)
    return barcodes.any { normalizeDigits(it).startsWith(t) }
  }
}
