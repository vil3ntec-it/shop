package ir.vil3ntec.tohid.core.perm

import ir.vil3ntec.tohid.core.pos.LinePricing

/**
 *  STF-03 — سقف‌های فاکتورِ کارمند که سرور می‌سنجد، با **همان** قاعده در صندوق (پیش‌سنجی) و سرور.
 *
 *  همتای دقیقِ `server/src/lib/sale-caps.js`؛ هر دو با یک فایلِ مرجع سنجیده می‌شوند:
 *  `core/test-vectors/sale-caps-v1.json` (core: `SaleCapsVectorsTest`، سرور: `test/sale-caps.test.js`).
 *  ⛔ یکی را بی دیگری و بی فایلِ مرجع عوض نکنید.
 *
 *  سه نیاز، به همین ترتیب (همان ترتیبِ داوریِ سرور):
 *   1. `sales.discount` — تخفیفِ فاکتور. با پرچمِ `shop.price_cap` «تخفیفِ پنهان» هم تخفیف است ([basis]):
 *      کاهشِ قیمتِ ردیف زیرِ فهرست ([line]) و «جمعِ نهایی کمتر از جمع − تخفیف» ([basis] ⇒ `gapMinor`).
 *      پرچمِ خاموش ⇒ همان (تخفیف، جمع) ِ امروز (`ApprovalSlip.discountNeed`).
 *   2. `sales.price` — بیشترین فاصلهٔ قیمتِ یک ردیف از فهرست، بالا یا پایین (صدمِ درصد)؛ فقط با `shop.price_cap`.
 *   3. `sales.free_line` — قلمِ آزاد ([isFree]: ردیفِ بی کارتِ کالا) با بیشترین قیمتِ واحدِ قلم‌های آزاد (Minor)؛ فقط با
 *      `shop.price_cap` (تصمیمِ ۱-۱۱ (۲) الف، ۱۴۰۵/۰۸/۱۹).
 *   4. `sales.credit` — قرضِ فاکتور (`debtGiven`، Minor) پس از همین سند ([creditAmount])؛ فقط با `shop.credit_cap`.
 *  تصمیم همان `PermCore.evaluate(role, layers, perm, req(need))` است.
 *
 *  ── قیمتِ فهرستِ یک ردیف ([refUnits]) ────────────────────────────────
 *  قیمت‌هایی که صاحب/مدیر برای کالا گذاشته‌اند و دلخواهِ صندوقدار نیستند: قیمتِ امروز (بازهٔ زمان‌بندی‌شدهٔ فعال،
 *  PRD-13، وگرنه `salePrice`)، قیمتِ عمده (`wholesalePrice` > ۰؛ **فقط در فاکتورِ لیستِ عمده** — [Sale.wholesale]، تصمیمِ
 *  ۱-۱۱ (۳): وگرنه فروش به قیمتِ عمده کاهشِ ردیف است) و قیمتِ هر واحدِ بستهٔ فهرست‌دار (S05). ردیف با
 *  **نزدیک‌ترین** آن‌ها سنجیده می‌شود (روی مبلغِ گردشدهٔ ردیف، `LinePricing.grossMinor`). تخفیفِ خودکار ([Line.promoMinor]،
 *  PRM) را صاحب تعریف کرده و پیش از سنجش به مبلغ برمی‌گردد. مصرفِ امتیازِ مشتری ([Sale.loyaltyMinor]، PRM-13) قیمتِ ردیف‌ها
 *  را پایین می‌آورد؛ همان مبلغ به نسبتِ مبلغِ ردیف‌ها (همان `Loyalty.split` ِ صندوق) پیش از سنجش برمی‌گردد. مصرفِ نادرست
 *  (بیش از جمع، یا نه افغانیِ کامل) ⇒ هیچ برنمی‌گردد. فاصلهٔ تا یک پله ([TOL_MINOR]) گردکردن است، نه تغییر.
 *  بی‌سنجش (در [line]): قلمِ آزاد/کالای ناشناخته (مجوزِ خودش را دارد: [FREE_LINE])، مقدارِ نامثبت، و کالای بی قیمت.
 *
 *  Kotlin خالص — بی java.*، بی android.*.
 */
object SaleCaps {

  const val VERSION = 1
  const val DISCOUNT = "sales.discount"
  const val PRICE = "sales.price"
  const val FREE_LINE = "sales.free_line"
  const val CREDIT = "sales.credit"
  const val PRICE_FLAG = "shop.price_cap"
  const val CREDIT_FLAG = "shop.credit_cap"

  /** بیشترین گردکردنِ نقدیِ پذیرفته در «جمعِ نهایی»: نیمِ بزرگ‌ترین پلهٔ صندوق (`TillRounding.STEPS` = ۱۰ افغانی) */
  const val ROUND_MAX_MINOR = 500L
  /** فاصلهٔ مبلغِ ردیف تا یک پله (یک افغانی) گردکردنِ قیمتِ واحد است، نه تغییرِ قیمت */
  const val TOL_MINOR = 100L
  const val FULL_BP = 10_000L
  private const val LIM = Long.MAX_VALUE / FULL_BP

  // ── قرض ──────────────────────────────────────────────────────────

  /** جمعِ قرضِ فاکتور پس از همین درج: قرضِ تازه + قرضِ پیشینِ همان سند (کفِ صفر)؛ بی قرضِ تازه ⇒ ۰ */
  fun creditAmount(debtGivenMinor: Long, priorMinor: Long = 0): Long =
    if (debtGivenMinor > 0) debtGivenMinor + maxOf(0L, priorMinor) else 0L

  // ── قیمتِ فهرست ─────────────────────────────────────────────────

  data class Window(val from: String, val to: String, val priceMinor: Long)
  data class Pack(val sizeMilli: Long, val priceMinor: Long)

  /** آنچه از کالا لازم است (Minor/Milli) */
  data class Ref(
    val saleMinor: Long,
    val wholesaleMinor: Long = 0,
    val windows: List<Window> = emptyList(),
    val packs: List<Pack> = emptyList(),
  )

  /** قیمت‌های واحدِ فهرستِ کالا در روزِ [date] (`yyyy-MM-dd`)، صعودی و بی تکرار؛ [wholesale] نادرست ⇒ عمده فهرست نیست */
  fun refUnits(p: Ref, date: String, wholesale: Boolean = true): List<Long> {
    val base = p.windows.firstOrNull { date.isNotEmpty() && date >= it.from && date <= it.to }?.priceMinor ?: p.saleMinor
    val out = mutableSetOf(maxOf(0L, base))
    if (wholesale && p.wholesaleMinor > 0) out += p.wholesaleMinor
    for (k in p.packs) {
      if (k.sizeMilli <= 0 || k.priceMinor <= 0 || k.priceMinor > LinePricing.MAX_EXACT / 1000) continue
      out += divHalfUp(k.priceMinor * 1000, k.sizeMilli)
    }
    return out.sorted()
  }

  private fun divHalfUp(a: Long, b: Long): Long = a / b + if ((a % b) * 2 >= b) 1 else 0

  // ── سنجشِ ردیف ─────────────────────────────────────────────────

  /** یک ردیفِ فاکتور: قیمتِ واحدِ ثبت‌شده و مقدار؛ [promoMinor] جمعِ تخفیفِ خودکارِ همین ردیف */
  data class Line(val productId: String, val unitMinor: Long, val qtyMilli: Long, val promoMinor: Long = 0)

  /**
   *  @param listMinor مبلغِ ردیف با نزدیک‌ترین قیمتِ فهرست
   *  @param soldMinor مبلغِ ردیف با قیمتِ ثبت‌شده (+ تخفیفِ خودکار)
   *  @param deviationBp فاصلهٔ آن دو، صدمِ درصد از فهرست (سقف ۱۰۰٪)
   *  @param reductionMinor کاهش زیرِ فهرست (تخفیفِ پنهان)؛ گران‌تر از فهرست ⇒ ۰
   */
  data class LineCheck(val exempt: Boolean, val listMinor: Long, val soldMinor: Long, val deviationBp: Long, val reductionMinor: Long)

  private val EXEMPT = LineCheck(true, 0, 0, 0, 0)

  fun gross(unitMinor: Long, qtyMilli: Long): Long = LinePricing.grossMinor(unitMinor, qtyMilli)

  fun line(l: Line, p: Ref?, date: String, wholesale: Boolean = true): LineCheck {
    if (p == null || l.productId.isBlank() || l.qtyMilli <= 0 || l.unitMinor < 0) return EXEMPT
    return runCatching {
      val sold = gross(l.unitMinor, l.qtyMilli) + maxOf(0L, l.promoMinor)
      var list = -1L
      for (r in refUnits(p, date, wholesale)) {
        val g = gross(r, l.qtyMilli)
        if (list < 0 || dist(sold, g) < dist(sold, list)) list = g
      }
      if (list <= 0) return EXEMPT
      val diff = dist(sold, list)
      if (diff <= TOL_MINOR) LineCheck(false, list, sold, 0, 0)
      else LineCheck(false, list, sold, bpCeil(diff, list), if (sold < list) list - sold else 0)
    }.getOrElse {
      //  عددِ بیرون از بازهٔ امن ⇒ «همهٔ ردیف تغییر است» تا بی تأیید نرود، نه آزاد
      LineCheck(false, 0, 0, FULL_BP, 0)
    }
  }

  private fun dist(a: Long, b: Long): Long = if (a >= b) a - b else b - a

  /** ceil(a × ۱۰۰۰۰ ÷ c)، سقفِ ۱۰۰۰۰؛ عددِ بسیار بزرگ با نیم کردنِ هر دو (همان `sale-caps.js`) */
  private fun bpCeil(a0: Long, c0: Long): Long {
    if (a0 >= c0) return FULL_BP
    var a = a0
    var c = c0
    while (c > LIM) { a = a shr 1; c = c shr 1 }
    if (c <= 0) return FULL_BP
    val n = a * FULL_BP
    return minOf(FULL_BP, n / c + if (n % c != 0L) 1 else 0)
  }

  // ── پایهٔ تخفیف ────────────────────────────────────────────────

  /**
   *  @param discountMinor تخفیفِ فاکتور با تخفیفِ پنهان
   *  @param baseMinor جمعی که درصد از آن حساب می‌شود (جمع + کاهش‌ها، همان `LinePricing.basis`)
   *  @param gapMinor «جمعِ نهایی کمتر از جمع − تخفیف» (پس از گردکردنِ نقدیِ تا [ROUND_MAX_MINOR])
   */
  data class Basis(val discountMinor: Long, val baseMinor: Long, val reductionMinor: Long, val gapMinor: Long)

  /**
   *  [finalTotalMinor] `null` ⇒ نامعلوم، بی شکاف. POS-37: [taxMinor] (مالیاتِ ثبت‌شدهٔ فاکتور؛ فقط با پرچمِ `shop.sale_tax`،
   *  وگرنه ۰ و همان امروز) بخشی از نهاییِ «باید» است: مالیاتِ ادعاشده‌ای که در نهایی نیامد همان تخفیفِ پنهان است.
   */
  fun basis(discountMinor: Long, totalMinor: Long, finalTotalMinor: Long?, roundingMinor: Long, reductionMinor: Long, taxMinor: Long = 0): Basis {
    val d = maxOf(0L, discountMinor)
    val s = maxOf(0L, totalMinor)
    val r = roundingMinor.coerceIn(-ROUND_MAX_MINOR, ROUND_MAX_MINOR)
    val t = maxOf(0L, taxMinor)
    val gap = if (finalTotalMinor == null) 0L else maxOf(0L, s - d + r + t - finalTotalMinor)
    val red = maxOf(0L, reductionMinor)
    return Basis(d + red + gap, s + red, red, gap)
  }

  // ── فاکتور ⇒ نیازها ───────────────────────────────────────────

  /**
   *  سرِ فاکتور (Minor)؛ [priorDebtMinor] قرضِ پیشینِ همان سند روی سرور؛ [loyaltyMinor] امتیازِ مصرف‌شدهٔ همین فاکتور (ردیفِ
   *  `loyalty_spend` ِ همان ثبت)
   */
  data class Sale(
    val id: String,
    val totalMinor: Long,
    val discountMinor: Long,
    val finalTotalMinor: Long?,
    val roundingMinor: Long = 0,
    val debtGivenMinor: Long = 0,
    val date: String = "",
    val priorDebtMinor: Long = 0,
    val loyaltyMinor: Long = 0,
    /** تصمیمِ ۱-۱۱ (۳): لیستِ فاکتور عمده است (مشتریِ فاکتور لیستِ پیش‌فرضِ عمده دارد — `PriceList.WHOLESALE`) */
    val wholesale: Boolean = false,
    /** POS-37: مالیاتِ ثبت‌شده — فقط وقتی پرچمِ `shop.sale_tax` روشن است، وگرنه ۰ */
    val taxMinor: Long = 0,
  )

  /** خلاصهٔ ردیف‌ها: بیشترین فاصله، جمعِ کاهش‌ها و بیشترین قیمتِ واحدِ قلمِ آزاد (`-1` = بی قلمِ آزاد) */
  data class Lines(val maxDeviationBp: Long, val reductionMinor: Long, val freeUnitMaxMinor: Long = -1)

  /**
   *  قلمِ آزاد (تصمیمِ ۱-۱۱ (۲)): ردیفِ بی کارتِ کالا — `productId` ِ خالی یا کالایی که در [refs] نیست — با مقدارِ مثبت و
   *  قیمتِ نامنفی (همان `sale-caps.isFree` ِ سرور)
   */
  fun isFree(l: Line, refs: Map<String, Ref>): Boolean =
    l.qtyMilli > 0 && l.unitMinor >= 0 && (l.productId.isBlank() || refs[l.productId] == null)

  /**
   *  سهمِ امتیازِ هر ردیف (Minor): [loyaltyMinor] به نسبتِ مبلغِ ثبت‌شدهٔ ردیف‌ها (`Loyalty.split`، بیشترین باقی‌مانده)؛
   *  نادرست ⇒ همه صفر (کاهش شمرده می‌شود، نه بخشیده)
   */
  fun loyaltyShares(lines: List<Line>, loyaltyMinor: Long): List<Long> {
    val zero = lines.map { 0L }
    if (loyaltyMinor <= 0 || loyaltyMinor % LinePricing.STEP_MINOR != 0L) return zero
    val afn = lines.map { l -> if (l.qtyMilli > 0 && l.unitMinor >= 0) runCatching { gross(l.unitMinor, l.qtyMilli) / LinePricing.STEP_MINOR }.getOrDefault(0L) else 0L }
    val s = runCatching { ir.vil3ntec.tohid.core.promo.Loyalty.split(afn, loyaltyMinor / LinePricing.STEP_MINOR) }.getOrNull() ?: return zero
    return s.map { it * LinePricing.STEP_MINOR }
  }

  fun lines(lines: List<Line>, refs: Map<String, Ref>, date: String, loyaltyMinor: Long = 0, wholesale: Boolean = true): Lines {
    var dev = 0L
    var red = 0L
    var free = -1L
    val shares = loyaltyShares(lines, loyaltyMinor)
    for ((i, l0) in lines.withIndex()) {
      val l = if (shares[i] > 0) l0.copy(promoMinor = maxOf(0L, l0.promoMinor) + shares[i]) else l0
      if (isFree(l0, refs)) free = maxOf(free, l0.unitMinor)
      val c = line(l, refs[l.productId], date, wholesale)
      if (c.deviationBp > dev) dev = c.deviationBp
      red += c.reductionMinor
    }
    return Lines(dev, red, free)
  }

  /**
   *  نیازهای فاکتور به همان ترتیبِ سرور. [enforcePrice] = سرور `sales.price` را اجرا می‌کند (پرچمِ `shop.price_cap`) —
   *  تخفیفِ پنهان هم فقط با همین؛ [enforceCredit] = `shop.credit_cap`. هر دو خاموش ⇒ فقط تخفیفِ امروز.
   */
  fun needs(sale: Sale, lines: List<Line>, refs: Map<String, Ref>, enforcePrice: Boolean, enforceCredit: Boolean): List<ApprovalSlip.Need> {
    val doc = "sales/${sale.id}"
    val out = ArrayList<ApprovalSlip.Need>(4)
    val sum = if (enforcePrice) SaleCaps.lines(lines, refs, sale.date, sale.loyaltyMinor, sale.wholesale) else Lines(0, 0)
    val b = if (enforcePrice) basis(sale.discountMinor, sale.totalMinor, sale.finalTotalMinor, sale.roundingMinor, sum.reductionMinor, sale.taxMinor)
    else Basis(sale.discountMinor, sale.totalMinor, 0, 0)
    if (b.discountMinor > 0) out += ApprovalSlip.discountNeed(sale.id, b.discountMinor, b.baseMinor)
    if (enforcePrice && sum.maxDeviationBp > 0) out += ApprovalSlip.Need(PRICE, doc, sum.maxDeviationBp, null)
    if (enforcePrice && sum.freeUnitMaxMinor >= 0) out += ApprovalSlip.Need(FREE_LINE, doc, null, sum.freeUnitMaxMinor)
    if (enforceCredit) {
      val a = creditAmount(sale.debtGivenMinor, sale.priorDebtMinor)
      if (a > 0) out += ApprovalSlip.Need(CREDIT, doc, null, a)
    }
    return out
  }

  /** درخواستِ عددی برای `PermCore.evaluate` — همان `req` ِ سرور */
  fun req(n: ApprovalSlip.Need): Map<String, Double> = buildMap {
    n.pctBp?.let { put("pct", it / 100.0) }
    n.amount?.let { put("amount", it.toDouble()) }
  }

  /** حالِ یک نیاز در صندوق — همان چهار حالِ `DiscountGate` */
  enum class State { FREE, NEEDS_APPROVAL, APPROVED, DENIED }

  /**
   *  @param decision `PermCore.evaluate(role, layers, need.perm, req(need)).decision`
   *  @param slip برگه‌ای که برای همین سند گرفته شد (اگر هست)؛ زمان را سرور می‌سنجد
   */
  fun state(decision: String, need: ApprovalSlip.Need, slip: ApprovalSlip.Slip?): State = when (decision) {
    "allow" -> State.FREE
    "approve" -> if (slip != null && ApprovalSlip.covers(slip, need) == null) State.APPROVED else State.NEEDS_APPROVAL
    else -> State.DENIED
  }
}
