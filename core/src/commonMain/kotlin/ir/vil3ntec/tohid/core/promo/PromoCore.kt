package ir.vil3ntec.tohid.core.promo

/**
 *  هستهٔ تخفیف و قاعده‌های قیمت — PRM-01..09، 11، 12، 17 (بخشِ ۶.۴ِ مشخصات).
 *
 *  همتای دقیقِ `license/promo-core.js` (وب). هر دو با یک فایلِ مرجع سنجیده
 *  می‌شوند: `core/test-vectors/promo-v1.json` (آزمونش:
 *  `android-native/app/src/test/.../core/promo/PromoCoreTest.kt` و
 *  `server/test/promo-core.test.js`).
 *  ⛔ رفتارِ یکی را بی دیگری و بی فایلِ مرجع عوض نکنید.
 *
 *  هر مبلغ `Long` به واحدِ کوچک (پول = یک‌صدمِ افغانی)، هر مقدار به هزارم.
 *  هر عددِ میانی در بازهٔ ±(۲^۵۳−۱) می‌ماند — همان سقفِ عددِ JS — وگرنه خطا.
 *
 *  ── ترکیب و اولویت (PRM-12) ──────────────────────────────────
 *  ۱. واجدِ شرط: فعال، معتبر، در بازهٔ زمانی، گروه/کانالِ درست، کدِ واردشده
 *     (اگر کد دارد) و ظرفیتِ مصرف.
 *  ۲. اثرِ هر قاعده «تنها» روی سبدِ دست‌نخورده (D).
 *  ۳. اگر «انحصاری» با D>0 هست فقط یکی: اولویت ⇒ D ⇒ شناسهٔ کوچک‌تر.
 *  ۴. وگرنه از «بهترین قیمت»ها یکی با D بیشتر (⇒ اولویت ⇒ شناسه) + همهٔ «ترکیبی»ها.
 *  ۵. اعمال: اول ردیفی‌ها، بعد آستانهٔ مبلغ؛ هر دسته اولویت ⇒ شناسه؛ روی مانده.
 *
 *  ── گردکردن (۳.۴) و کف ──────────────────────────────────────
 *  هر تخفیف مضربِ پلهٔ ارز، نیمه رو به بالا. تخفیفِ فاکتور و بسته با «بزرگ‌ترین
 *  باقی‌مانده» پخش می‌شود. هیچ ردیفی زیرِ بهای خرید (گرد رو به بالا) نمی‌رود مگر
 *  قاعده `allowBelowCost` داشته باشد؛ زیرِ صفر هرگز.
 *
 *  Kotlin خالص است — بی android.*، بی کتابخانه.
 */
object PromoCore {

  const val VERSION = 1

  val KINDS: List<String> = listOf("percent", "amount", "price", "tiers", "bxgy", "bundle", "threshold")
  val STACKS: List<String> = listOf("exclusive", "best", "combine")
  val CHANNELS: List<String> = listOf("pos", "online")

  private const val FULL_BP = 10_000L
  private const val MAX = 9_007_199_254_740_991L

  // ── مدل ─────────────────────────────────────────────────

  data class Scope(
    val products: List<String> = emptyList(),
    val categories: List<String> = emptyList(),
    val suppliers: List<String> = emptyList(),
    val brands: List<String> = emptyList(),
    val exProducts: List<String> = emptyList(),
    val exCategories: List<String> = emptyList(),
    val exSuppliers: List<String> = emptyList(),
    val exBrands: List<String> = emptyList(),
  )

  /** روزها ISO (دوشنبه ۱ … یکشنبه ۷)؛ دقیقه‌ها از نیمه‌شب؛ -1 یعنی «ندارد». */
  data class Window(
    val from: String = "",
    val to: String = "",
    val days: List<Long> = emptyList(),
    val start: Long = -1,
    val end: Long = -1,
    val except: List<String> = emptyList(),
  )

  /** `min` = مقدار به هزارم (tiers) یا مبلغ (threshold). */
  data class Tier(val min: Long = -1, val bp: Long = 0, val amountMinor: Long = 0)

  data class Price(val productId: String = "", val unitMinor: Long = -1)

  data class Rule(
    val id: String = "",
    val name: String = "",
    val kind: String = "",
    val active: Boolean = true,
    val stack: String = "best",
    val priority: Long = 0,
    val code: String = "",
    val maxUses: Long = 0,
    val uses: Long = 0,
    val allowBelowCost: Boolean = false,
    val scope: Scope = Scope(),
    val groups: List<String> = emptyList(),
    val channels: List<String> = emptyList(),
    val window: Window = Window(),
    val bp: Long = 0,
    val amountMinor: Long = 0,
    val prices: List<Price> = emptyList(),
    val tiers: List<Tier> = emptyList(),
    val buy: Long = 0,
    val get: Long = 0,
    val qty: Long = 0,
    val priceMinor: Long = 0,
  )

  /** `amountMinor`: مبلغِ ردیف از موتورِ صندوق؛ null ⇒ مقدار × قیمت با گردکردنِ ۳.۴. */
  data class Line(
    val key: String,
    val productId: String,
    val qtyMilli: Long,
    val unitMinor: Long,
    val amountMinor: Long? = null,
    val costMinor: Long = 0,
    val category: String = "",
    val supplierId: String = "",
    val brand: String = "",
    val noPromo: Boolean = false,
  )

  /** زمانِ محلیِ دکان: تاریخِ «YYYY-MM-DD» و دقیقه از نیمه‌شب. */
  data class At(val date: String, val minute: Long)

  data class Cart(
    val lines: List<Line>,
    val coupons: List<String> = emptyList(),
    val group: String = "",
    val channel: String = "pos",
    val at: At? = null,
    val stepMinor: Long = 100,
  )

  data class LineOut(val key: String, val amountMinor: Long, val discountMinor: Long, val netMinor: Long)
  data class Applied(val id: String, val name: String, val discountMinor: Long)
  /** status: applied | no_effect | used_up | inactive | unknown */
  data class CodeStatus(val code: String, val status: String)

  data class Result(
    val subtotalMinor: Long,
    val discountMinor: Long,
    val totalMinor: Long,
    val lines: List<LineOut>,
    val applied: List<Applied>,
    val codes: List<CodeStatus>,
  )

  // ── حسابِ صحیحِ امن ─────────────────────────────────────

  private fun safe(x: Long): Long {
    if (x > MAX || x < -MAX) throw ArithmeticException("promo: عدد از بازهٔ امن بیرون است")
    return x
  }

  private fun mul(a: Long, b: Long): Long {
    val r = a * b
    if (a != 0L && (r / a != b || (a == -1L && b == Long.MIN_VALUE))) throw ArithmeticException("promo: سرریز")
    return safe(r)
  }

  private fun add(a: Long, b: Long): Long = safe(a + b)
  private fun div(a: Long, b: Long): Long = a.floorDiv(b)
  private fun roundStep(n: Long, d: Long, step: Long): Long {
    val q = mul(d, step)
    return mul(div(add(n, div(q, 2)), q), step)
  }
  private fun ceilStep(n: Long, d: Long, step: Long): Long {
    val q = mul(d, step)
    return mul(div(add(n, q - 1), q), step)
  }
  private fun floorStep(n: Long, step: Long): Long = if (n <= 0) 0 else mul(div(n, step), step)

  // ── خواندنِ درختِ JSON (Map / List / String / Number / Boolean / null) ──

  private fun int(x: Any?, dflt: Long): Long = intOrNull(x) ?: dflt
  private fun intOrNull(x: Any?): Long? {
    if (x !is Number) return null
    if (x is Long || x is Int || x is Short || x is Byte) {
      val v = x.toLong()
      return if (v in -MAX..MAX) v else null
    }
    val d = x.toDouble()
    if (d.isNaN() || d.isInfinite() || d != kotlin.math.floor(d) || kotlin.math.abs(d) > MAX.toDouble()) return null
    return d.toLong()
  }
  private fun str(x: Any?): String = x as? String ?: ""
  private fun bool(x: Any?, dflt: Boolean): Boolean = x as? Boolean ?: dflt
  private fun strs(x: Any?): List<String> = (x as? List<*>)?.filterIsInstance<String>() ?: emptyList()
  private fun ints(x: Any?): List<Long> = (x as? List<*>)?.map { int(it, -1) } ?: emptyList()
  private fun objs(x: Any?): List<Map<*, *>> = (x as? List<*>)?.map { it as? Map<*, *> ?: emptyMap<String, Any?>() } ?: emptyList()
  private fun obj(x: Any?): Map<*, *> = x as? Map<*, *> ?: emptyMap<String, Any?>()

  private val DIGITS: Map<Char, Char> = buildMap {
    "۰۱۲۳۴۵۶۷۸۹".forEachIndexed { i, c -> put(c, '0' + i) }
    "٠١٢٣٤٥٦٧٨٩".forEachIndexed { i, c -> put(c, '0' + i) }
  }
  private const val CODE_SKIP = " -\t\n\r‌"

  /** کدِ تخفیف: ارقامِ فارسی/عربی ⇒ لاتین، a-z ⇒ A-Z، فاصله و خط‌تیره حذف. */
  fun normalizeCode(s: String): String {
    val sb = StringBuilder()
    for (c in s) {
      val dg = DIGITS[c]
      when {
        dg != null -> sb.append(dg)
        c in 'a'..'z' -> sb.append(c - 32)
        CODE_SKIP.indexOf(c) >= 0 -> {}
        else -> sb.append(c)
      }
    }
    return sb.toString()
  }

  private fun scopeOf(x: Any?): Scope {
    val o = obj(x)
    return Scope(
      strs(o["products"]), strs(o["categories"]), strs(o["suppliers"]), strs(o["brands"]),
      strs(o["exProducts"]), strs(o["exCategories"]), strs(o["exSuppliers"]), strs(o["exBrands"]),
    )
  }

  fun windowOf(x: Any?): Window {
    val o = obj(x)
    return Window(str(o["from"]), str(o["to"]), ints(o["days"]), int(o["start"], -1), int(o["end"], -1), strs(o["except"]))
  }

  /** هر درختِ JSON ⇒ قاعدهٔ کامل با پیش‌فرض‌ها؛ نوعِ نادرست ⇒ پیش‌فرض (که `validate` می‌گیرد). */
  fun ruleOf(x: Any?): Rule {
    val o = obj(x)
    return Rule(
      id = str(o["id"]), name = str(o["name"]), kind = str(o["kind"]),
      active = bool(o["active"], true), stack = o["stack"] as? String ?: "best", priority = int(o["priority"], 0),
      code = str(o["code"]), maxUses = int(o["maxUses"], 0), uses = int(o["uses"], 0),
      allowBelowCost = bool(o["allowBelowCost"], false),
      scope = scopeOf(o["scope"]), groups = strs(o["groups"]), channels = strs(o["channels"]), window = windowOf(o["window"]),
      bp = int(o["bp"], 0), amountMinor = int(o["amountMinor"], 0),
      prices = objs(o["prices"]).map { Price(str(it["productId"]), int(it["unitMinor"], -1)) },
      tiers = objs(o["tiers"]).map { Tier(int(it["min"], -1), int(it["bp"], 0), int(it["amountMinor"], 0)) },
      buy = int(o["buy"], 0), get = int(o["get"], 0), qty = int(o["qty"], 0), priceMinor = int(o["priceMinor"], 0),
    )
  }

  /** درختِ JSON ⇒ سبد؛ همان ورودیِ `evaluate`ِ نسخهٔ JS. */
  fun cartOf(x: Any?): Cart {
    val o = obj(x)
    val at = o["at"] as? Map<*, *>
    return Cart(
      lines = objs(o["lines"]).map {
        Line(
          key = str(it["key"]), productId = str(it["productId"]), qtyMilli = int(it["qtyMilli"], 0),
          unitMinor = int(it["unitMinor"], 0), amountMinor = intOrNull(it["amountMinor"]), costMinor = int(it["costMinor"], 0),
          category = str(it["category"]), supplierId = str(it["supplierId"]), brand = str(it["brand"]),
          noPromo = bool(it["noPromo"], false),
        )
      },
      coupons = strs(o["coupons"]), group = str(o["group"]),
      channel = o["channel"] as? String ?: "pos",
      at = at?.let { At(str(it["date"]), int(it["minute"], -1)) },
      stepMinor = int(o["stepMinor"], 100),
    )
  }

  // ── تاریخ ───────────────────────────────────────────────

  private val DATE_RE = Regex("^(\\d{4})-(\\d{2})-(\\d{2})$")

  /** «YYYY-MM-DD» ⇒ شمارهٔ روز از ۱۹۷۰-۰۱-۰۱؛ نادرست ⇒ null */
  private fun epochDay(s: String): Long? {
    val m = DATE_RE.matchEntire(s) ?: return null
    val y = m.groupValues[1].toLong()
    val mo = m.groupValues[2].toLong()
    val d = m.groupValues[3].toLong()
    if (mo < 1 || mo > 12 || d < 1) return null
    val leap = (y % 4 == 0L && y % 100 != 0L) || y % 400 == 0L
    val dim = longArrayOf(31, if (leap) 29 else 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31)[(mo - 1).toInt()]
    if (d > dim) return null
    val yy = if (mo <= 2) y - 1 else y
    val era = yy.floorDiv(400L)
    val yoe = yy - era * 400
    val mp = (mo + 9) % 12
    val doy = (153 * mp + 2) / 5 + d - 1
    val doe = yoe * 365 + yoe / 4 - yoe / 100 + doy
    return era * 146097 + doe - 719468
  }

  private fun isoDowOf(day: Long): Long = (day + 3).mod(7L) + 1

  /** روزِ هفتهٔ ISO (دوشنبه ۱ … یکشنبه ۷)؛ تاریخِ نادرست ⇒ ۰ */
  fun isoDow(date: String): Int = epochDay(date)?.let { isoDowOf(it).toInt() } ?: 0

  private fun hasWindow(w: Window): Boolean =
    w.from != "" || w.to != "" || w.days.isNotEmpty() || w.start != -1L || w.end != -1L || w.except.isNotEmpty()

  /** PRM-09 — بازه‌ای که از نیمه‌شب رد می‌شود به روزِ شروعش تعلق دارد. */
  fun inWindow(w: Window, at: At?): Boolean {
    if (!hasWindow(w)) return true
    if (at == null) return false
    val today = epochDay(at.date) ?: return false
    val minute = at.minute
    if (minute < 0 || minute > 1439) return false
    var day = today
    if (w.start != -1L) {
      if (w.start < w.end) {
        if (minute < w.start || minute >= w.end) return false
      } else if (minute < w.end) day = today - 1
      else if (minute < w.start) return false
    }
    if (w.from != "" && day < (epochDay(w.from) ?: return false)) return false
    if (w.to != "" && day > (epochDay(w.to) ?: return false)) return false
    if (w.days.isNotEmpty() && isoDowOf(day) !in w.days) return false
    if (w.except.any { epochDay(it) == day }) return false
    return true
  }

  // ── اعتبارسنجی (PRM-17) ─────────────────────────────────

  private val CODE_RE = Regex("^[A-Z0-9_]{3,32}$")
  private const val BLANK = " \t\n\r ‌"

  private fun validBp(bp: Long) = bp in 1..FULL_BP
  private fun blank(s: String) = s.all { BLANK.indexOf(it) >= 0 }

  /** خطاهای یک قاعده به ترتیبِ ثابت؛ فهرستِ خالی یعنی معتبر. */
  fun validate(r: Rule, stepMinor: Long = 100): List<String> {
    val step = if (stepMinor > 0) stepMinor else 100
    val e = ArrayList<String>()
    if (r.id == "") e += "id"
    if (blank(r.name)) e += "name"
    if (r.kind !in KINDS) e += "kind"
    if (r.stack !in STACKS) e += "stack"
    if (r.priority < -1000 || r.priority > 1000) e += "priority"
    if (r.code != "" && !CODE_RE.matches(normalizeCode(r.code))) e += "code"
    if (r.maxUses < 0 || r.uses < 0) e += "uses"
    if (r.channels.any { it !in CHANNELS }) e += "channel"
    val w = r.window
    var wbad = false
    if (w.from != "" && epochDay(w.from) == null) wbad = true
    if (w.to != "" && epochDay(w.to) == null) wbad = true
    if (!wbad && w.from != "" && w.to != "" && epochDay(w.from)!! > epochDay(w.to)!!) wbad = true
    if (w.days.any { it < 1 || it > 7 }) wbad = true
    if (w.except.any { epochDay(it) == null }) wbad = true
    if ((w.start == -1L) != (w.end == -1L)) wbad = true
    if (w.start != -1L && (w.start < 0 || w.start > 1439 || w.end < 0 || w.end > 1440 || w.start == w.end)) wbad = true
    if (wbad) e += "window"

    val k = r.kind
    if (k == "percent" && !validBp(r.bp)) e += "bp"
    if (k == "amount" && (r.amountMinor <= 0 || r.amountMinor % step != 0L)) e += "amount"
    if (k == "price") {
      val seen = HashSet<String>()
      var bad = r.prices.isEmpty()
      for (p in r.prices) {
        if (p.productId == "" || p.unitMinor < 0 || p.productId in seen) bad = true
        seen += p.productId
      }
      if (bad) e += "prices"
    }
    if (k == "tiers" || k == "threshold") {
      var bad = r.tiers.isEmpty()
      val useAmt = r.tiers.isNotEmpty() && r.tiers[0].amountMinor > 0
      val byBp = k == "tiers" || !useAmt
      r.tiers.forEachIndexed { i, t ->
        if (t.min <= 0) bad = true
        if (byBp) {
          if (!validBp(t.bp) || t.amountMinor != 0L) bad = true
        } else if (t.bp != 0L || t.amountMinor <= 0 || t.amountMinor % step != 0L) bad = true
        if (i > 0) {
          val a = r.tiers[i - 1]
          if (t.min <= a.min) bad = true
          if (if (byBp) t.bp <= a.bp else t.amountMinor <= a.amountMinor) bad = true
        }
      }
      if (bad) e += "tiers"
    }
    if (k == "bxgy" && (r.buy < 1 || r.get < 1 || r.buy > 1000 || r.get > 1000 || !validBp(r.bp))) e += "bxgy"
    if (k == "bundle" && (r.qty < 2 || r.qty > 1000 || r.priceMinor <= 0 || r.priceMinor % step != 0L)) e += "bundle"
    return e
  }

  // ── سبد ─────────────────────────────────────────────────

  private fun lineTotalMinor(qtyMilli: Long, unitMinor: Long, step: Long) = roundStep(mul(qtyMilli, unitMinor), 1000, step)

  private class L(
    val key: String, val productId: String, val qtyMilli: Long, val unitMinor: Long, val amountMinor: Long,
    val category: String, val supplierId: String, val brand: String,
    val units: Long, val costFloor: Long, val open: Boolean,
  )

  private class Cx(val step: Long, val lines: List<L>, val codes: List<String>, val group: String, val channel: String, val at: At?)

  private fun prepare(cart: Cart): Cx {
    val step = if (cart.stepMinor > 0) cart.stepMinor else 100
    val lines = cart.lines.map { l ->
      val qty = l.qtyMilli
      val unit = l.unitMinor
      val amount = l.amountMinor ?: if (qty > 0 && unit > 0) lineTotalMinor(qty, unit, step) else 0
      val cost = l.costMinor
      L(
        key = l.key, productId = l.productId, qtyMilli = qty, unitMinor = unit, amountMinor = amount,
        category = l.category, supplierId = l.supplierId, brand = l.brand,
        units = if (qty > 0) div(qty, 1000) else 0,
        costFloor = if (cost > 0 && qty > 0) ceilStep(mul(qty, cost), 1000, step) else 0,
        open = l.productId != "" && !l.noPromo && qty > 0 && amount > 0,
      )
    }
    val codes = LinkedHashSet<String>()
    for (c in cart.coupons) normalizeCode(c).takeIf { it != "" }?.let { codes += it }
    return Cx(step, lines, codes.toList(), cart.group, cart.channel, cart.at)
  }

  private fun inScope(s: Scope, l: L): Boolean {
    if (!l.open) return false
    val any = s.products.size + s.categories.size + s.suppliers.size + s.brands.size > 0
    val inc = !any || l.productId in s.products ||
      (l.category != "" && l.category in s.categories) ||
      (l.supplierId != "" && l.supplierId in s.suppliers) ||
      (l.brand != "" && l.brand in s.brands)
    if (!inc) return false
    //  PRM-08 — مستثنا بر شامل مقدم است
    return !(l.productId in s.exProducts ||
      (l.category != "" && l.category in s.exCategories) ||
      (l.supplierId != "" && l.supplierId in s.exSuppliers) ||
      (l.brand != "" && l.brand in s.exBrands))
  }

  private fun roomOf(r: Rule, l: L, remaining: Long, step: Long): Long =
    floorStep(remaining - (if (r.allowBelowCost) 0 else l.costFloor), step)

  /** پخشِ `total` به نسبتِ `weights` با بزرگ‌ترین باقی‌مانده، هر سهم تا `caps`؛ اضافهٔ ردیفِ پُر به بقیه. */
  private fun allocate(total: Long, weights: LongArray, caps: LongArray, step: Long): LongArray {
    val n = weights.size
    val shares = LongArray(n)
    var active = ArrayList<Int>()
    var capSum = 0L
    for (i in 0 until n) {
      capSum = add(capSum, div(caps[i], step))
      if (weights[i] > 0 && caps[i] >= step) active += i
    }
    var left = minOf(div(total, step), capSum)
    while (left > 0 && active.isNotEmpty()) {
      var w = 0L
      for (i in active) w = add(w, weights[i])
      val base = LongArray(n)
      val rem = LongArray(n)
      var given = 0L
      for (i in active) {
        val num = mul(left, weights[i])
        base[i] = div(num, w)
        rem[i] = num - base[i] * w
        given += base[i]
      }
      val order = active.sortedWith(compareByDescending<Int> { rem[it] }.thenBy { it })
      for (j in 0 until (left - given).toInt()) base[order[j]] += 1
      var overflow = 0L
      val next = ArrayList<Int>()
      for (x in active) {
        val room = div(caps[x], step) - shares[x]
        val take = minOf(base[x], room)
        shares[x] += take
        overflow += base[x] - take
        if (shares[x] < div(caps[x], step)) next += x
      }
      left = overflow
      active = next
    }
    for (i in 0 until n) shares[i] = mul(shares[i], step)
    return shares
  }

  private fun unitsOf(idx: List<Int>, lines: List<L>, desc: Boolean): List<Int> =
    if (desc) idx.sortedWith(compareByDescending<Int> { lines[it].unitMinor }.thenBy { it })
    else idx.sortedWith(compareBy<Int> { lines[it].unitMinor }.thenBy { it })

  /** اثرِ یک قاعده روی مانده‌ها ⇒ سهمِ هر ردیف (مضربِ پله). */
  private fun shareOf(r: Rule, cx: Cx, rem: LongArray): LongArray {
    val step = cx.step
    val lines = cx.lines
    val n = lines.size
    val out = LongArray(n)
    val idx = (0 until n).filter { inScope(r.scope, lines[it]) }
    if (idx.isEmpty()) return out
    when (r.kind) {
      "percent", "amount", "price", "tiers" -> {
        for (i in idx) {
          val l = lines[i]
          val d = when (r.kind) {
            "percent" -> roundStep(mul(l.amountMinor, r.bp), FULL_BP, step)
            "amount" -> roundStep(mul(r.amountMinor, l.qtyMilli), 1000, step)
            "price" -> r.prices.firstOrNull { it.productId == l.productId }
              ?.let { maxOf(0L, l.amountMinor - lineTotalMinor(l.qtyMilli, it.unitMinor, step)) } ?: 0L
            else -> {
              var bp = 0L
              for (t in r.tiers) if (t.min <= l.qtyMilli) bp = t.bp
              roundStep(mul(l.amountMinor, bp), FULL_BP, step)
            }
          }
          out[i] = minOf(d, roomOf(r, l, rem[i], step))
        }
      }
      "bxgy" -> {
        var total = 0L
        for (i in idx) total += lines[i].units
        var free = mul(div(total, r.buy + r.get), r.get)
        for (i in unitsOf(idx, lines, false)) {
          if (free <= 0) break
          val l = lines[i]
          val cnt = minOf(free, l.units)
          free -= cnt
          if (cnt > 0) out[i] = minOf(roundStep(mul(mul(cnt, l.unitMinor), r.bp), FULL_BP, step), roomOf(r, l, rem[i], step))
        }
      }
      "bundle" -> {
        var all = 0L
        for (i in idx) all += lines[i].units
        val groups = div(all, r.qty)
        var take = mul(groups, r.qty)
        if (groups == 0L) return out
        val weights = LongArray(n)
        val caps = LongArray(n)
        var value = 0L
        for (i in unitsOf(idx, lines, true)) {
          if (take <= 0) break
          val l = lines[i]
          val c = minOf(take, l.units)
          take -= c
          weights[i] = mul(c, l.unitMinor)
          value = add(value, weights[i])
          caps[i] = roomOf(r, l, rem[i], step)
        }
        val disc = value - mul(groups, r.priceMinor)
        if (disc <= 0) return out
        return allocate(roundStep(disc, 1, step), weights, caps, step)
      }
      "threshold" -> {
        val w = LongArray(n)
        val cp = LongArray(n)
        var base = 0L
        for (i in idx) {
          w[i] = maxOf(0L, rem[i])
          cp[i] = roomOf(r, lines[i], rem[i], step)
          base = add(base, w[i])
        }
        var tier: Tier? = null
        for (t in r.tiers) if (t.min <= base) tier = t
        if (tier == null) return out
        val amt = if (tier.amountMinor > 0) minOf(tier.amountMinor, floorStep(base, step))
        else roundStep(mul(base, tier.bp), FULL_BP, step)
        return allocate(amt, w, cp, step)
      }
    }
    return out
  }

  private fun sum(a: LongArray): Long {
    var s = 0L
    for (x in a) s = add(s, x)
    return s
  }

  private fun live(r: Rule, cx: Cx): Boolean =
    r.active && validate(r, cx.step).isEmpty() &&
      (r.groups.isEmpty() || cx.group in r.groups) &&
      (r.channels.isEmpty() || cx.channel in r.channels) &&
      inWindow(r.window, cx.at)

  private fun eligible(r: Rule, cx: Cx): Boolean =
    live(r, cx) &&
      (r.code == "" || normalizeCode(r.code) in cx.codes) &&
      !(r.maxUses > 0 && r.uses >= r.maxUses)

  private val RANKS = listOf("unknown", "inactive", "used_up", "no_effect", "applied")

  /** سبد + قاعده‌ها ⇒ تخفیفِ هر ردیف، قاعده‌های اعمال‌شده و وضعیتِ هر کد. */
  fun evaluate(cart: Cart, rules: List<Rule>): Result {
    val cx = prepare(cart)
    val n = cx.lines.size
    val fresh = LongArray(n) { cx.lines[it].amountMinor }

    class Cand(val r: Rule, val d: Long)
    val cand = ArrayList<Cand>()
    for (r in rules) {
      if (!eligible(r, cx)) continue
      val d = sum(shareOf(r, cx, fresh))
      if (d > 0) cand += Cand(r, d)
    }

    val chosen = ArrayList<Rule>()
    val ex = cand.filter { it.r.stack == "exclusive" }
    if (ex.isNotEmpty()) {
      chosen += ex.sortedWith(
        compareByDescending<Cand> { it.r.priority }.thenByDescending { it.d }.thenBy { it.r.id },
      ).first().r
    } else {
      var best: Cand? = null
      for (c in cand) {
        if (c.r.stack == "combine") chosen += c.r
        else if (c.r.stack == "best") {
          val b = best
          if (b == null || c.d > b.d || (c.d == b.d && (c.r.priority > b.r.priority ||
              (c.r.priority == b.r.priority && c.r.id < b.r.id)))) best = c
        }
      }
      best?.let { chosen += it.r }
    }
    val ordered = chosen.sortedWith(
      compareBy<Rule> { if (it.kind == "threshold") 1 else 0 }.thenByDescending { it.priority }.thenBy { it.id },
    )

    val rem = fresh.copyOf()
    val disc = LongArray(n)
    val applied = ArrayList<Applied>()
    for (r in ordered) {
      val sh = shareOf(r, cx, rem)
      var got = 0L
      for (i in 0 until n) {
        rem[i] -= sh[i]
        disc[i] += sh[i]
        got += sh[i]
      }
      if (got > 0) applied += Applied(r.id, r.name, got)
    }

    var subtotal = 0L
    var discount = 0L
    val lines = cx.lines.mapIndexed { i, l ->
      subtotal = add(subtotal, l.amountMinor)
      discount = add(discount, disc[i])
      LineOut(l.key, l.amountMinor, disc[i], l.amountMinor - disc[i])
    }

    val codes = cx.codes.map { code ->
      var st = "unknown"
      for (r in rules) {
        if (r.code == "" || normalizeCode(r.code) != code) continue
        val s = when {
          applied.any { it.id == r.id } -> "applied"
          eligible(r, cx) -> "no_effect"
          live(r, cx) && r.maxUses > 0 && r.uses >= r.maxUses -> "used_up"
          else -> "inactive"
        }
        if (RANKS.indexOf(s) > RANKS.indexOf(st)) st = s
      }
      CodeStatus(code, st)
    }

    return Result(subtotal, discount, subtotal - discount, lines, applied, codes)
  }
}
