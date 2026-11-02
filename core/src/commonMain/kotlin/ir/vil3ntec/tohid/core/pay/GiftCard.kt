package ir.vil3ntec.tohid.core.pay

/**
 *  PAY-24 — کارتِ هدیه و اعتبارِ فروشگاه به‌عنوانِ روشِ پرداخت. منطقِ خالص؛ پول به یک‌صدمِ افغانی (`Long`).
 *
 *  ── کارتِ هدیه: دفترِ فقط‌افزودنی ──────────────────────────────────
 *  هر کارت یک کد دارد ([CODE_LEN] نویسه از [ALPHABET]، نویسهٔ آخر وارسیِ Luhn mod 32 که هر خطای یک‌نویسه‌ای و
 *  بیشترِ جابه‌جاییِ دو نویسهٔ کنارِ هم را می‌گیرد). رویدادها: صدور ([ISSUE]، مبلغِ اول)، خرج ([REDEEM]، با شناسهٔ
 *  فاکتور)، برگشت به کارت ([REFUND]) و باطل ([VOID]، با دلیل). هیچ رویدادی ویرایش یا پاک نمی‌شود؛ مانده از روی
 *  رویدادها ساخته می‌شود ([view])، به ترتیبِ (`at`، `id`).
 *
 *  ── بی دوبار خرج کردن ─────────────────────────────────────────────
 *    • روی یک دستگاه: [build] خرج را فقط تا ماندهٔ همان لحظه می‌پذیرد، و شناسهٔ تکراری با همان محتوا `duplicate`
 *      است (دو ضربه یک خرج) و با محتوای دیگر `ID_CONFLICT`.
 *    • میانِ دو دستگاهِ بی‌خبر از هم (بی‌اینترنت): هیچ دفترِ محلی‌ای نمی‌تواند جلویش را بگیرد؛ [view] ماندهٔ منفی را
 *      هرگز به صفر نمی‌بُرد و کارت را «نیازمندِ بررسی» نشان می‌دهد ([Card.review]) — همان قاعدهٔ گاوصندوق و سفارشِ ویژه.
 *    • کارتِ باطل خرج نمی‌شود؛ خرج یا برگشتی که پس از باطل (از دستگاهِ دیگر) رسیده هم «نیازمندِ بررسی» است.
 *
 *  ── اعتبارِ فروشگاه ([StoreCredit]) ───────────────────────────────
 *  اعتبار همان طلبِ مشتری در دفترِ قرض‌دار است (ماندهٔ منفی)؛ جدولِ تازه‌ای نیست. پرداخت با آن فقط تا همان طلب.
 *
 *  ردیفِ نادرست، شناسهٔ تکراری، صدورِ دوبارهٔ همان کد یا رویدادی که جمعی را از ±(۲^۵۳−۱) بیرون ببرد کنار می‌رود
 *  ([View.invalid]) — نه عددِ گردشده، نه خطایی که خواندنِ کلِ دفتر را بشکند. کاتلینِ خالص، بی ساعت و بی تصادف (تصادف
 *  از بیرون: [make]). بردار: `core/test-vectors/gift-v1.json` (`GiftCardVectorsTest`).
 */
object GiftCard {

  const val VERSION = 1

  const val ISSUE = "issue"
  const val REDEEM = "redeem"
  const val REFUND = "refund"
  const val VOID = "void"
  val KINDS: List<String> = listOf(ISSUE, REDEEM, REFUND, VOID)

  /** ۳۲ نویسه، بی 0/O و 1/I */
  const val ALPHABET = "23456789ABCDEFGHJKLMNPQRSTUVWXYZ"
  const val CODE_LEN = 12

  const val MAX_EXACT: Long = 9_007_199_254_740_991L
  /** سقفِ هر رویداد: ۱۰۰ میلیون افغانی — همان `SpecialOrder.MAX_MINOR` */
  const val MAX_AMOUNT: Long = 10_000_000_000L
  const val MAX_ID = 64
  const val MAX_TEXT = 200
  const val REASON_MIN = 3

  object Code {
    const val ID_REQUIRED = "ID_REQUIRED"
    const val BAD_KIND = "BAD_KIND"
    const val BAD_CODE = "BAD_CODE"
    const val INVALID_AMOUNT = "INVALID_AMOUNT"
    const val INVALID_TIME = "INVALID_TIME"
    const val TEXT_TOO_LONG = "TEXT_TOO_LONG"
    const val REF_REQUIRED = "REF_REQUIRED"
    const val REASON_REQUIRED = "REASON_REQUIRED"
    const val BAD_FIELD = "BAD_FIELD"
    const val CODE_TAKEN = "CODE_TAKEN"
    const val UNKNOWN_CARD = "UNKNOWN_CARD"
    const val CARD_VOID = "CARD_VOID"
    const val INSUFFICIENT_BALANCE = "INSUFFICIENT_BALANCE"
    const val ID_CONFLICT = "ID_CONFLICT"
  }

  // ── کد ─────────────────────────────────────────────────────────

  private fun index(c: Char): Int = ALPHABET.indexOf(c)

  /** نویسهٔ وارسیِ Luhn mod 32 برای [payload] (همه از [ALPHABET]) */
  fun checkChar(payload: String): Char {
    val n = ALPHABET.length
    var factor = 2
    var sum = 0
    for (i in payload.indices.reversed()) {
      val a = factor * index(payload[i])
      sum += a / n + a % n
      factor = if (factor == 2) 1 else 2
    }
    return ALPHABET[(n - sum % n) % n]
  }

  /**
   *  متنِ کادر ⇒ کد: فاصله و خط‌تیره کنار، حروفِ کوچک بزرگ، رقمِ فارسی/عربی لاتین. طولِ نادرست، نویسهٔ بیرون از
   *  [ALPHABET] یا نویسهٔ وارسیِ نادرست ⇒ null.
   */
  fun normalize(text: String?): String? {
    if (text == null) return null
    val sb = StringBuilder()
    for (ch in text) {
      val c = when (ch) {
        in '۰'..'۹' -> '0' + (ch - '۰')
        in '٠'..'٩' -> '0' + (ch - '٠')
        in 'a'..'z' -> ch - ('a' - 'A')
        else -> ch
      }
      if (c == ' ' || c == '-' || c == '‌' || c == '\t') continue
      if (index(c) < 0) return null
      sb.append(c)
      if (sb.length > CODE_LEN) return null
    }
    if (sb.length != CODE_LEN) return null
    val code = sb.toString()
    return code.takeIf { checkChar(it.dropLast(1)) == it.last() }
  }

  /** کدِ تازه از [next] (`next(32)` ⇒ ۰ تا ۳۱، تصادفِ امنِ سکو) */
  fun make(next: (Int) -> Int): String {
    val payload = buildString { repeat(CODE_LEN - 1) { append(ALPHABET[next(ALPHABET.length).mod(ALPHABET.length)]) } }
    return payload + checkChar(payload)
  }

  /** نمایش: «ABCD-EFGH-JKLM» */
  fun display(code: String): String = code.chunked(4).joinToString("-")

  // ── رویداد و شکلِ آن ────────────────────────────────────────────

  data class Event(
    val id: String,
    val kind: String,
    val code: String,
    val amountMinor: Long,
    val at: Long,
    val by: String? = null,
    /** خرج: شناسهٔ فاکتور (لازم)؛ برگشت: سندِ برگشت (اختیاری) */
    val refId: String? = null,
    /** باطل: دلیل (لازم) */
    val reason: String? = null,
  )

  private fun tooLong(s: String?, max: Int) = s != null && s.length > max

  /** شکلِ یک رویداد، بی نگاه به بقیهٔ دفتر — null یعنی درست */
  fun check(e: Event): String? {
    if (e.id.isBlank()) return Code.ID_REQUIRED
    if (e.kind !in KINDS) return Code.BAD_KIND
    if (normalize(e.code) != e.code) return Code.BAD_CODE
    if (e.kind == VOID) {
      if (e.amountMinor != 0L) return Code.INVALID_AMOUNT
    } else if (e.amountMinor < 1 || e.amountMinor > MAX_AMOUNT) return Code.INVALID_AMOUNT
    if (e.at <= 0 || e.at > MAX_EXACT) return Code.INVALID_TIME
    if (e.id.length > MAX_ID || tooLong(e.by, MAX_TEXT) || tooLong(e.refId, MAX_ID) || tooLong(e.reason, MAX_TEXT)) return Code.TEXT_TOO_LONG
    if (e.kind == REDEEM && e.refId.isNullOrBlank()) return Code.REF_REQUIRED
    if (e.kind == VOID) {
      if ((e.reason?.trim()?.length ?: 0) < REASON_MIN) return Code.REASON_REQUIRED
    } else if (e.reason != null) return Code.BAD_FIELD
    return null
  }

  // ── مانده ──────────────────────────────────────────────────────

  data class Line(val event: Event, val balanceMinor: Long)

  data class Card(
    val code: String,
    val issuedAt: Long,
    val issuedMinor: Long,
    val redeemedMinor: Long,
    val refundedMinor: Long,
    /** ماندهٔ دفتر؛ زیرِ صفر یعنی دوبار خرج از دو دستگاه — هرگز به صفر بریده نمی‌شود */
    val balanceMinor: Long,
    val void: Boolean,
    /** ماندهٔ منفی یا خرج/برگشت پس از باطل */
    val review: Boolean,
    /** تازه‌ترین اول */
    val history: List<Line>,
  ) {
    /** آنچه هنوز خرج‌شدنی است */
    val spendableMinor: Long get() = if (void || balanceMinor < 0) 0 else balanceMinor
  }

  data class View(
    /** به ترتیبِ صدور */
    val cards: List<Card>,
    /** شناسهٔ رویدادهایی که در مانده نیستند */
    val invalid: List<String>,
  ) {
    fun card(code: String?): Card? = code?.let { c -> cards.firstOrNull { it.code == c } }
    /** جمعِ ماندهٔ کارت‌های نه‌باطل — بدهیِ دکان به دارندگان */
    val liabilityMinor: Long get() = cards.filter { !it.void && it.balanceMinor > 0 }.sumOf { it.balanceMinor }
    val review: List<String> get() = cards.filter { it.review }.map { it.code }
  }

  private val ORDER = compareBy<Event>({ it.at }, { it.id })

  private fun sum(a: Long, b: Long): Long? = (a + b).takeIf { it in -MAX_EXACT..MAX_EXACT }

  private class Acc(val code: String, val issuedAt: Long, var issued: Long) {
    var redeemed = 0L
    var refunded = 0L
    var balance = issued
    var void = false
    var review = false
    val lines = ArrayList<Line>()
  }

  fun view(events: List<Event>): View {
    val seen = HashSet<String>()
    val bad = ArrayList<String>()
    val ok = ArrayList<Event>()
    for (e in events) if (check(e) != null || !seen.add(e.id)) bad += e.id else ok += e
    val cards = LinkedHashMap<String, Acc>()
    for (e in ok.sortedWith(ORDER)) {
      val c = cards[e.code]
      if (e.kind == ISSUE) {
        if (c != null) { bad += e.id; continue }
        cards[e.code] = Acc(e.code, e.at, e.amountMinor).also { it.lines += Line(e, e.amountMinor) }
        continue
      }
      if (c == null) { bad += e.id; continue }
      when (e.kind) {
        REDEEM -> {
          val r = sum(c.redeemed, e.amountMinor)
          val b = sum(c.balance, -e.amountMinor)
          if (r == null || b == null) { bad += e.id; continue }
          c.redeemed = r; c.balance = b
          if (c.void || b < 0) c.review = true
        }
        REFUND -> {
          val r = sum(c.refunded, e.amountMinor)
          val b = sum(c.balance, e.amountMinor)
          if (r == null || b == null) { bad += e.id; continue }
          c.refunded = r; c.balance = b
          if (c.void) c.review = true
        }
        VOID -> {
          if (c.void) { bad += e.id; continue }
          c.void = true
        }
      }
      c.lines += Line(e, c.balance)
    }
    return View(
      cards = cards.values.map {
        Card(it.code, it.issuedAt, it.issued, it.redeemed, it.refunded, it.balance, it.void, it.review, it.lines.asReversed().toList())
      },
      invalid = bad,
    )
  }

  // ── سنجشِ خرج و ساختِ رویدادِ تازه ──────────────────────────────

  sealed interface Result<out T> {
    data class Ok<T>(val value: T, val duplicate: Boolean = false) : Result<T>
    data class Err(val code: String) : Result<Nothing>
  }

  /** آیا [amountMinor] از کارتِ [code] خرج‌شدنی است — null یعنی آری */
  fun spendProblem(v: View, code: String?, amountMinor: Long): String? {
    val c = normalize(code) ?: return Code.BAD_CODE
    if (amountMinor < 1 || amountMinor > MAX_AMOUNT) return Code.INVALID_AMOUNT
    val card = v.card(c) ?: return Code.UNKNOWN_CARD
    if (card.void) return Code.CARD_VOID
    if (amountMinor > card.spendableMinor) return Code.INSUFFICIENT_BALANCE
    return null
  }

  data class Input(
    val id: String?,
    val kind: String,
    val code: String?,
    val amountMinor: Long,
    val at: Long,
    val by: String? = null,
    val refId: String? = null,
    val reason: String? = null,
  )

  private fun clean(s: String?): String? = s?.trim()?.ifEmpty { null }

  /**
   *  سنجش و ساختِ رویدادِ تازه روی دفترِ همین دستگاه. `at` دست‌کم یکی پس از آخرین رویدادِ دیده‌شده است (ساعتِ
   *  عقب‌ماندهٔ دستگاه خرج را پیش از صدوری که همین‌جا دیده شد نمی‌برد). صدورِ کدِ موجود `CODE_TAKEN`؛ خرج فقط تا
   *  ماندهٔ خرج‌شدنی ([spendProblem])؛ برگشت و باطل فقط روی کارتِ موجود و نه‌باطل.
   */
  fun build(events: List<Event>, input: Input): Result<Event> {
    val id = clean(input.id) ?: return Result.Err(Code.ID_REQUIRED)
    if (input.at <= 0) return Result.Err(Code.INVALID_TIME)
    val code = normalize(input.code) ?: return Result.Err(Code.BAD_CODE)
    val prior = events.firstOrNull { it.id == id }
    val last = events.filter { check(it) == null }.maxOfOrNull { it.at } ?: 0L
    val e = Event(
      id = id, kind = input.kind, code = code, amountMinor = input.amountMinor,
      at = prior?.at ?: maxOf(input.at, last + 1),
      by = clean(input.by), refId = clean(input.refId), reason = clean(input.reason),
    )
    check(e)?.let { return Result.Err(it) }
    if (prior != null) return if (prior.copy(at = 0) == e.copy(at = 0)) Result.Ok(prior, duplicate = true) else Result.Err(Code.ID_CONFLICT)
    val v = view(events)
    val card = v.card(code)
    when (e.kind) {
      ISSUE -> if (card != null) return Result.Err(Code.CODE_TAKEN)
      REDEEM -> spendProblem(v, code, e.amountMinor)?.let { return Result.Err(it) }
      else -> {
        if (card == null) return Result.Err(Code.UNKNOWN_CARD)
        if (card.void) return Result.Err(Code.CARD_VOID)
        if (sum(card.balanceMinor, e.amountMinor) == null || sum(card.refundedMinor, e.amountMinor) == null) return Result.Err(Code.INVALID_AMOUNT)
      }
    }
    return Result.Ok(e)
  }
}

/**
 *  PAY-24 — اعتبارِ فروشگاه همان طلبِ مشتری است: ماندهٔ دفترِ قرض‌دار به پول (مثبت = مشتری بدهکار، منفی = طلب).
 *  پرداخت با اعتبار فقط تا همان طلب؛ بیشتر از آن بدهیِ تازه است که راهِ «نسیه» دارد، نه این روش.
 */
object StoreCredit {
  const val INVALID_AMOUNT = "INVALID_AMOUNT"
  const val NO_CUSTOMER = "NO_CUSTOMER"
  const val INSUFFICIENT_CREDIT = "INSUFFICIENT_CREDIT"

  /** طلبِ خرج‌شدنیِ مشتری از ماندهٔ [balanceMinor] */
  fun available(balanceMinor: Long): Long = if (balanceMinor < 0) -balanceMinor else 0L

  /** null یعنی [amountMinor] با اعتبارِ همین مشتری پرداخت‌شدنی است */
  fun problem(hasCustomer: Boolean, balanceMinor: Long, amountMinor: Long): String? = when {
    !hasCustomer -> NO_CUSTOMER
    amountMinor < 1 || amountMinor > GiftCard.MAX_AMOUNT -> INVALID_AMOUNT
    amountMinor > available(balanceMinor) -> INSUFFICIENT_CREDIT
    else -> null
  }
}
