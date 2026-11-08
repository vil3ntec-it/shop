package ir.vil3ntec.tohid.core.staff

import ir.vil3ntec.tohid.core.money.addExact
import ir.vil3ntec.tohid.core.money.mulExact

/**
 *  POS-23 / STF-08 — فروشندهٔ هر ردیف (پایهٔ پورسانت). کاتلینِ خالص، پول به `Long` ِ واحدِ خرد.
 *
 *  • `SaleItem.seller` اختیاری است: نبودش (یا خالی، یا همان فروشندهٔ فاکتور) یعنی فروشندهٔ ردیف همان فروشندهٔ
 *    فاکتور است — **دقیقاً** رفتارِ امروز ([of]).
 *  • همه پشتِ تنظیمِ پیش‌فرض‌خاموشِ دکان (`line_seller`): خاموش ⇒ ردیف هرگز فروشنده نمی‌گیرد ([stored] ⇒ `null`)
 *    و گزارش و پورسانت فروشندهٔ ردیف را نمی‌بینند ([of] با `on = false`)، حتی اگر دفتر آن را داشته باشد.
 *  • مجوز ([mayAssign]): هر کس ردیف را به نامِ **خودش** (یا برگشت به فروشندهٔ فاکتور) می‌زند؛ نامِ **دیگری** فقط
 *    با [othersAllowed] (صاحب و مدیر — همان دو نقشی که «پورسانتِ این ماه» را می‌بینند).
 *  • پخشِ مبلغِ فاکتور میانِ فروشنده‌ها ([split]): خالصِ هر ردیف = مبلغِ ردیف − سهمش از تفاوتِ جمعِ ردیف‌ها با
 *    `finalTotal` (تخفیفِ فاکتور)، به «بزرگ‌ترین باقی‌مانده» و واحدِ خرد — همان روشِ `Commission.saleLines`؛ پس جمعِ
 *    سهم‌ها همیشه دقیقاً `finalTotal` است. فاکتورِ تک‌فروشنده همان یک ردیفِ پیشین است.
 */
object LineSeller {

  /** فروشندهٔ یک ردیف: نامِ پیراستهٔ ردیف، وگرنه فروشندهٔ فاکتور (همان امروز) */
  fun of(line: String?, invoice: String): String = line?.trim()?.takeIf { it.isNotEmpty() } ?: invoice.trim()

  /** همان [of] پشتِ کلید: خاموش ⇒ همیشه فروشندهٔ فاکتور */
  fun of(on: Boolean, line: String?, invoice: String): String = if (on) of(line, invoice) else invoice.trim()

  /**
   *  مقداری که روی `SaleItem.seller` نوشته می‌شود: کلیدِ خاموش، نامِ خالی یا همان فروشندهٔ فاکتور ⇒ `null`
   *  (فیلد نوشته نمی‌شود و قلم بیت‌به‌بیت همان امروز است).
   */
  fun stored(on: Boolean, line: String?, invoice: String): String? {
    if (!on) return null
    val v = line?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    return if (v == invoice.trim()) null else v
  }

  /**
   *  [me] می‌تواند فروشندهٔ ردیف را [target] بگذارد؟ `null`/خالی (برگشت به فروشندهٔ فاکتور) و نامِ خودش همیشه؛
   *  نامِ دیگری فقط با [othersAllowed].
   */
  fun mayAssign(me: String, target: String?, othersAllowed: Boolean): Boolean {
    val t = target?.trim().orEmpty()
    return t.isEmpty() || t == me.trim() || othersAllowed
  }

  /**
   *  گزینه‌های برگهٔ «فروشندهٔ ردیف»: خودم اول، سپس کارمندانِ فعالِ دکان ([staff]) پیراسته، یکتا و به ترتیبِ الفبا —
   *  فقط با [othersAllowed]؛ بی آن فقط خودم.
   */
  fun choices(me: String, staff: List<String>, othersAllowed: Boolean): List<String> {
    val self = me.trim()
    val others = if (!othersAllowed) emptyList() else staff.map { it.trim() }.filter { it.isNotEmpty() && it != self }.distinct().sorted()
    return listOfNotNull(self.takeIf { it.isNotEmpty() }) + others
  }

  /** سهمِ یک فروشنده از یک فاکتور: خالص (پس از تخفیفِ فاکتور) و تخفیف، به واحدِ خرد */
  data class Share(val seller: String, val net: Long, val discount: Long)

  /** یک ردیف برای [split]: فروشندهٔ ردیف (`null` = فروشندهٔ فاکتور) و مبلغِ ناخالصِ ردیف به واحدِ خرد */
  data class Item(val seller: String?, val gross: Long)

  /**
   *  پخشِ یک فاکتور میانِ فروشنده‌های ردیف‌ها، به ترتیبِ نخستین ردیفِ هر فروشنده. یک فروشنده (یا بی ردیف) ⇒ همان یک
   *  سهمِ پیشین (`finalTotal` و `discount` ِ خودِ فاکتور). ردیفِ منفی، جمعِ صفر، فاکتورِ بیش از جمعِ ردیف‌ها، تخفیفِ
   *  بیرون از بازه یا عددِ بیرون از بازهٔ دقیق ⇒ همهٔ فاکتور به نامِ فروشندهٔ فاکتور (پخش‌ناپذیر، نه گم).
   *  جمعِ `net` همیشه دقیقاً [finalTotal] و جمعِ `discount` همیشه دقیقاً [discount] است.
   */
  fun split(invoice: String, finalTotal: Long, discount: Long, items: List<Item>): List<Share> {
    val who = items.map { of(it.seller, invoice) }
    val single = who.distinct().singleOrNull()
    if (items.isEmpty() || single != null) return listOf(Share(single ?: invoice.trim(), finalTotal, discount))
    val whole = listOf(Share(invoice.trim(), finalTotal, discount))
    val gross = items.map { it.gross }
    return try {
      val sum = gross.fold(0L) { acc, g -> addExact(acc, g) }
      if (gross.any { it < 0 } || discount < 0 || discount > sum) return whole
      val off = allocate(sum - finalTotal, gross) ?: return whole
      val disc = allocate(discount, gross) ?: return whole
      val net = LinkedHashMap<String, LongArray>()
      who.forEachIndexed { i, s ->
        val a = net.getOrPut(s) { LongArray(2) }
        a[0] = addExact(a[0], gross[i] - off[i]); a[1] = addExact(a[1], disc[i])
      }
      net.map { (s, a) -> Share(s, a[0], a[1]) }
    } catch (_: ArithmeticException) {
      whole
    }
  }

  /**
   *  «بزرگ‌ترین باقی‌مانده» به واحدِ خرد (همان حلقهٔ پیشینِ `Commission.saleLines`): [amount] (≥ ۰) به نسبتِ [weights]
   *  (≥ ۰، جمعِ مثبت)؛ باقی‌ماندهٔ برابر ⇒
   *  ردیفِ جلوتر. جمعِ خروجی دقیقاً [amount]. شرطِ نادرست ⇒ `null`؛ سرریز ⇒ `ArithmeticException`.
   */
  internal fun allocate(amount: Long, weights: List<Long>): LongArray? {
    val sum = weights.fold(0L) { acc, w -> addExact(acc, w) }
    if (amount < 0L || sum <= 0L || weights.any { it < 0 }) return null
    val share = LongArray(weights.size)
    val rem = LongArray(weights.size)
    var given = 0L
    weights.forEachIndexed { i, w ->
      val num = mulExact(amount, w)
      share[i] = num / sum
      rem[i] = num % sum
      given += share[i]
    }
    weights.indices.sortedWith(compareByDescending<Int> { rem[it] }.thenBy { it }).take((amount - given).toInt()).forEach { share[it] += 1 }
    return share
  }
}
