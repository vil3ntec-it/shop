package ir.vil3ntec.tohid.core.pos

/**
 *  قیمتِ ردیفِ صندوق — تخفیفِ ردیف (POS-20)، «قاعدهٔ جهت»ِ تغییرِ قیمت (POS-08)، سقفِ
 *  قلمِ آزاد (POS-10) و پایهٔ سنجشِ سقفِ تخفیفِ نقش (POS-21) — بخشِ ۶.۱ِ مشخصات.
 *
 *  هر مبلغ `Long` به واحدِ کوچک (پول = یک‌صدمِ افغانی)، هر مقدار به هزارم (`qtyMilli`).
 *  گردکردن صریح و یک‌جا: **نیمه رو به بالا به پلهٔ ارز** (`STEP_MINOR` = یک افغانی) — همان
 *  `SalesEngine.lineTotal` که مبلغِ هر ردیف را به افغانیِ کامل گرد می‌کند. هر عددِ میانی در
 *  بازهٔ ۰ تا ۲^۵۳−۱ می‌ماند (همان سقفِ `PromoCore`)، وگرنه `ArithmeticException`.
 *
 *  ── چرا «کاهشِ ردیف» هم تخفیف است (POS-21) ─────────────────────────────
 *  سقفِ تخفیفِ نقش (`sales.discount`، `PermCore`) تا امروز فقط تخفیفِ کلِ فاکتور را می‌سنجید.
 *  قیمتِ دستیِ ردیف راهِ دورزدنش بود: کارمندی که تخفیف برایش «ممنوع» است قیمتِ یک ردیف را
 *  از ۱۰۰۰ به ۵۰۰ می‌گذاشت و هیچ سقفی، تأییدی یا ردی نمی‌دید. با پرچم، هر ردیفی که زیرِ
 *  قیمتِ فهرست فروخته شود (قیمتِ دستی یا تخفیفِ ردیف) همان‌قدر به تخفیفِ فاکتور افزوده و با
 *  همان سقف و همان «درخواستِ تأیید» سنجیده می‌شود (`basis`). تخفیفِ خودکار (PRM) را صاحب و
 *  مدیر از پیش تعریف کرده‌اند و دلخواهِ صندوقدار نیست؛ صدازننده ردیفِ تخفیف‌خورده را نمی‌شمارد.
 *
 *  ── چرا برگهٔ تأییدِ صندوق سرور را هم می‌پوشاند ─────────────────────────
 *  سرور فقط تخفیفِ فاکتور را می‌بیند: نیاز = (D، D÷S) با S جمعِ ثبت‌شده. صندوق (D+R، (D+R)÷(S+R))
 *  می‌خواهد. چون D ≤ S، (D+R)÷(S+R) ≥ D÷S و D+R ≥ D؛ پس برگه‌ای که صندوق گرفت همیشه نیازِ
 *  سرور را هم می‌پوشاند (با بخششِ نیم‌افغانیِ `PermCore.discountBp` هم، چون S ≥ D−۵۰).
 *  R = ۰ ⇒ دقیقاً همان عددهای امروز.
 *
 *  ⚠️ سرور قیمتِ هر قلم را با فهرست نمی‌سنجد؛ این سنجش در صندوق است (مثلِ امروزِ قیمتِ دستی).
 *
 *  Kotlin خالص است — بی java.*، بی android.*، بی کتابخانه.
 */
object LinePricing {

  const val VERSION = 1

  /** یک افغانی — مبلغِ هر ردیف به همین گرد می‌شود (`SalesEngine.lineTotal`) */
  const val STEP_MINOR = 100L
  const val FULL_BP = 10_000L
  const val MAX_EXACT = 9_007_199_254_740_991L

  /** کدِ ردِ سنجش‌ها — صفحه متنِ خودش را می‌گذارد */
  const val PRICE_UP_BLOCKED = "price_up_blocked"
  const val PRICE_DOWN_BLOCKED = "price_down_blocked"
  const val REDUCTION_DENIED = "reduction_denied"
  const val FREE_LINE_CLOSED = "free_line_closed"
  const val FREE_LINE_OVER_CAP = "free_line_over_cap"

  /**
   *  «قاعدهٔ جهت»ِ تغییرِ قیمتِ ردیف (POS-08): فقط بالا، فقط پایین، هر دو، یا هیچ.
   *  نبود ⇒ `BOTH` (رفتارِ امروز)؛ کدِ ناشناخته (مثلاً از نسخهٔ تازه‌تر) ⇒ `NONE`، بسته‌ترین.
   */
  enum class Direction(val code: String) {
    BOTH("both"), UP("up"), DOWN("down"), NONE("none");

    companion object {
      fun of(code: String?): Direction {
        val c = code?.trim().orEmpty()
        if (c.isEmpty()) return BOTH
        return entries.firstOrNull { it.code == c } ?: NONE
      }
    }
  }

  /**
   *  سیاستِ دکان برای ویرایشِ قیمتِ ردیف — همه با پرچمِ خاموش بی‌اثر.
   *
   *  @param on پرچم (پیش‌فرض خاموش): خاموش ⇒ هر سنجش «مجاز» و هیچ کاهشی شمرده نمی‌شود
   *  @param exempt صاحبِ دکان: قاعدهٔ جهت و سقفِ قلمِ آزاد او را نمی‌بندد (سقفِ تخفیف را
   *    `PermCore` خودش به مالک می‌بخشد، پس کاهشِ ردیفِ او هم شمرده می‌شود و بی‌اثر می‌ماند)
   *  @param mayReduce `sales.discount` برای این کاربر «ممنوع» نیست — «نیازمندِ تأیید» هم کاهش را
   *    می‌پذیرد چون سنجشِ پایانی با `basis` و برگهٔ تأیید است
   *  @param freeLineCapMinor سقفِ قیمتِ واحدِ قلمِ آزاد: `null` بی سقف، ۰ بسته، بیشتر ⇒ تا همین
   */
  data class Policy(
    val on: Boolean = false,
    val exempt: Boolean = false,
    val direction: Direction = Direction.BOTH,
    val mayReduce: Boolean = true,
    val freeLineCapMinor: Long? = null,
  )

  // ── گردکردن و حسابِ بی‌سرریز ─────────────────────────────────

  private fun check(v: Long): Long {
    if (v < 0 || v > MAX_EXACT) throw ArithmeticException("out of exact range")
    return v
  }

  private fun mul(a: Long, b: Long): Long {
    if (a != 0L && b > MAX_EXACT / a) throw ArithmeticException("out of exact range")
    return a * b
  }

  /** round(x × y ÷ c)، نیمه رو به بالا؛ همه نامنفی و c > ۰ */
  private fun mulDivHalfUp(x: Long, y: Long, c: Long): Long {
    check(x); check(y)
    if (c <= 0) throw ArithmeticException("bad divisor")
    //  بزرگ‌تر تقسیم می‌شود تا باقی‌ماندهٔ کوچک × کوچک‌تر سرریز نکند
    val a = maxOf(x, y)
    val b = minOf(x, y)
    val hi = mul(a / c, b)
    val lo = mul(a % c, b)
    val up = if ((lo % c) * 2 >= c) 1L else 0L
    return check(check(hi + lo / c) + up)
  }

  /** مضربِ نزدیکِ [step]، نیمه رو به بالا */
  fun roundStep(v: Long, step: Long = STEP_MINOR): Long = mul(mulDivHalfUp(v, 1, step), step)

  /**
   *  مبلغِ ردیف = قیمتِ واحد × مقدار، گرد به پله نیمه رو به بالا — همان `SalesEngine.lineTotal`
   *  (۱٫۵ × ۱۱۵ = ۱۷۲٫۵ ⇒ ۱۷۳ افغانی).
   */
  fun grossMinor(unitMinor: Long, qtyMilli: Long, step: Long = STEP_MINOR): Long =
    mul(mulDivHalfUp(unitMinor, qtyMilli, mul(1000, step)), step)

  // ── تخفیفِ ردیف (POS-20) ───────────────────────────────────

  /**
   *  تخفیفِ یک ردیف به Minor. [percent] ⇒ [value] صدمِ درصد (۱۰٪ = ۱۰۰۰)، تا ۱۰۰٪؛ وگرنه
   *  [value] مبلغ به Minor. نتیجه مضربِ پله (نیمه رو به بالا) و هرگز بیش از [grossMinor].
   *  منفی یا صفر ⇒ ۰.
   */
  fun discountMinor(grossMinor: Long, percent: Boolean, value: Long, step: Long = STEP_MINOR): Long {
    check(grossMinor)
    if (grossMinor == 0L || value <= 0) return 0
    val raw = if (percent) mul(mulDivHalfUp(grossMinor, minOf(value, FULL_BP), mul(FULL_BP, step)), step)
    else roundStep(minOf(value, MAX_EXACT), step)
    return minOf(raw, grossMinor)
  }

  /**
   *  قیمتِ واحدی (افغانی) که ردیف با آن دقیقاً [netMinor] می‌شود: `round(قیمت × مقدار)` همان
   *  خالص است — همان راهی که تخفیفِ خودکار قیمتِ ردیف را پایین می‌آورد (`PromoEngine.evaluate`).
   *  [netMinor] باید مضربِ پله باشد (`grossMinor − discountMinor` همیشه هست).
   */
  fun unitForNet(netMinor: Long, qtyMilli: Long): Double {
    check(netMinor)
    if (qtyMilli <= 0) throw ArithmeticException("bad quantity")
    return (netMinor / 100.0) / (qtyMilli / 1000.0)
  }

  // ── کاهشِ ردیف و پایهٔ سقفِ تخفیف (POS-21) ─────────────────────

  /**
   *  کاهشِ دلخواهِ یک ردیف: مبلغ با قیمتِ فهرست − مبلغ با قیمتِ ثبت‌شده، کفِ صفر. ردیفِ
   *  گران‌تر از فهرست کاهشِ ردیفِ دیگر را جبران نمی‌کند.
   */
  fun reductionMinor(listUnitMinor: Long, unitMinor: Long, qtyMilli: Long, step: Long = STEP_MINOR): Long {
    val list = grossMinor(listUnitMinor, qtyMilli, step)
    val sold = grossMinor(unitMinor, qtyMilli, step)
    return if (sold >= list) 0 else list - sold
  }

  /** آنچه با `sales.discount` سنجیده می‌شود: مبلغِ تخفیف و جمعی که درصد از آن حساب می‌شود */
  data class Basis(val discountMinor: Long, val baseMinor: Long)

  /**
   *  تخفیفِ فاکتور + کاهش‌های ردیف، روی جمعِ پیش از آن کاهش‌ها. پرچمِ خاموش ⇒ همان
   *  (تخفیف، جمع)ِ امروز. چرا برگه نیازِ سرور را هم می‌پوشاند: شرحِ سرِ همین فایل.
   */
  fun basis(p: Policy, invoiceDiscountMinor: Long, subtotalMinor: Long, reductionMinor: Long): Basis {
    check(invoiceDiscountMinor); check(subtotalMinor); check(reductionMinor)
    if (!p.on || reductionMinor == 0L) return Basis(invoiceDiscountMinor, subtotalMinor)
    return Basis(check(invoiceDiscountMinor + reductionMinor), check(subtotalMinor + reductionMinor))
  }

  // ── سنجشِ ویرایشِ ردیف (POS-08، POS-10) ────────────────────────

  /**
   *  قیمتِ دستیِ [newMinor] برای ردیفی با قیمتِ فهرستِ [listMinor] پذیرفته است؟ `null` یعنی بله.
   *  همان قیمت همیشه پذیرفته است (برگشت به فهرست). کاهش برای کسی که `sales.discount` ِ او
   *  «ممنوع» است رد می‌شود، چون کاهشِ ردیف هم تخفیف است.
   */
  fun checkPrice(p: Policy, listMinor: Long, newMinor: Long): String? {
    if (!p.on || p.exempt || newMinor == listMinor) return null
    val up = newMinor > listMinor
    val blocked = when (p.direction) {
      Direction.BOTH -> false
      Direction.UP -> !up
      Direction.DOWN -> up
      Direction.NONE -> true
    }
    if (blocked) return if (up) PRICE_UP_BLOCKED else PRICE_DOWN_BLOCKED
    if (!up && !p.mayReduce) return REDUCTION_DENIED
    return null
  }

  /** تخفیفِ ردیف پذیرفته است؟ همان «ممنوع»ِ `sales.discount` (سقف و تأیید را `basis` می‌سنجد) */
  fun checkDiscount(p: Policy, discountMinor: Long): String? =
    if (!p.on || p.exempt || discountMinor <= 0 || p.mayReduce) null else REDUCTION_DENIED

  /**
   *  نتیجهٔ کادرِ «ردیفِ سبد».
   *  @param problem کدِ رد (یکی از ثابت‌های بالا) یا `null`
   *  @param grossMinor مبلغِ ردیف با قیمتِ نوشته‌شده، پیش از تخفیفِ ردیف
   *  @param discountMinor تخفیفِ ردیف (پرچمِ خاموش ⇒ همیشه ۰)
   *  @param netUnit قیمتِ واحدِ پس از تخفیف (افغانی) برای `CartLine.unitPrice`؛ `null` ⇒ تخفیفی نیست
   *    و صفحه همان قیمتِ نوشته‌شده (یا قیمتِ کالا) را می‌گذارد
   */
  data class Edit(val problem: String?, val grossMinor: Long, val discountMinor: Long, val netUnit: Double?)

  /**
   *  کادرِ «تعداد، قیمت و تخفیفِ ردیف» (POS-08، POS-10، POS-20).
   *
   *  قاعدهٔ جهت (و سقفِ قلمِ آزاد) فقط **تغییرِ تازه** را می‌سنجد: قیمتی که ردیف همین حالا دارد
   *  ([currentMinor]) پیش‌تر پذیرفته شده — مثلاً قیمتِ پس از تخفیفِ ردیف — و دست‌نزدنش (فقط
   *  تعداد عوض شد) رد نمی‌شود.
   *
   *  @param free قلمِ آزاد: سقفِ قیمت (`checkFreeLine`) به‌جای قاعدهٔ جهت؛ فهرست ندارد
   *  @param listMinor قیمتِ کالا (Minor)
   *  @param priceMinor قیمتِ واحدِ نوشته‌شده؛ `null` ⇒ «قیمتِ خودِ کالا»
   *  @param percent [value] صدمِ درصد است، وگرنه Minor؛ ۰ یا کمتر ⇒ بی تخفیف
   */
  fun edit(
    p: Policy,
    free: Boolean,
    listMinor: Long,
    currentMinor: Long,
    priceMinor: Long?,
    qtyMilli: Long,
    percent: Boolean,
    value: Long,
  ): Edit {
    val unit = priceMinor ?: listMinor
    val changed = priceMinor != null && priceMinor != currentMinor
    val priceProblem = when {
      !changed -> null
      free -> checkFreeLine(p, priceMinor!!)
      else -> checkPrice(p, listMinor, priceMinor!!)
    }
    val gross = if (qtyMilli > 0) grossMinor(unit, qtyMilli) else 0L
    val discount = if (p.on && value > 0) discountMinor(gross, percent, value) else 0L
    val problem = priceProblem ?: checkDiscount(p, discount)
    val net = if (discount > 0) unitForNet(gross - discount, qtyMilli) else null
    return Edit(problem, gross, discount, net)
  }

  /** قلمِ آزاد با قیمتِ واحدِ [unitMinor] پذیرفته است؟ `null` یعنی بله */
  fun checkFreeLine(p: Policy, unitMinor: Long): String? {
    if (!p.on || p.exempt) return null
    val cap = p.freeLineCapMinor ?: return null
    if (cap <= 0) return FREE_LINE_CLOSED
    return if (unitMinor > cap) FREE_LINE_OVER_CAP else null
  }
}
