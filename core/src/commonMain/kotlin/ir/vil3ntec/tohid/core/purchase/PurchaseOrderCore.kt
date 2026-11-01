package ir.vil3ntec.tohid.core.purchase

/**
 *  سفارشِ خرید از تأمین‌کننده — PUR-02 (وضعیت‌ها)، PUR-04 (دریافتِ کامل/جزئی، اختلاف و خراب،
 *  باقی‌ماندهٔ باز) و PUR-14 (تطبیقِ سفارش، رسید و فاکتور) — §۶.۶ و سناریوی S19.
 *
 *  ── چرا این‌جا ───────────────────────────────────────────────────
 *  Kotlinِ خالص در commonMain (§۴۰.۳): بی android.*، بی java.*، بی دیسک و شبکه. همتای JSاش
 *  `server/src/lib/purchase-order-core.js` است که سرور با آن تصمیم می‌گیرد؛ هر دو یک فایلِ
 *  بردارِ مرجع را می‌گذرانند: `core/test-vectors/purchase-orders-v1.json`. یکی را بی دیگری عوض نکن.
 *
 *  ── واحدها ──────────────────────────────────────────────────────
 *  پول = عددِ صحیحِ یک‌صدمِ افغانی (`…Minor`)، مقدار = عددِ صحیحِ هزارم (`…Milli`)، هر دو در
 *  ±(2^53−1). جمعِ هر ردیف = `mulDivRound(مقدار، فی، ۱۰۰۰)` (نیمه به +∞، همان قاعدهٔ #231).
 *
 *  ── قاعده‌ها ────────────────────────────────────────────────────
 *  ۱) آنچه ذخیره می‌شود فقط `draft`، `sent` یا `cancelled` است. «بخشی دریافت‌شده» و «کامل» از
 *     رسیدها درمی‌آیند، نه از فیلدی که دو دستگاه بتوانند روی هم بنویسند.
 *  ۲) پذیرفته = رسیده − خراب. باقی‌مانده = سفارش − جمعِ پذیرفته (کمتر از صفر نه). خراب از
 *     باقی‌مانده کم **نمی‌کند**: تأمین‌کننده هنوز آن را بدهکار است و ردیف باز می‌ماند (S19).
 *  ۳) کامل = باقی‌ماندهٔ همهٔ ردیف‌ها صفر. بخشی = دست‌کم یک واحد پذیرفته و هنوز کامل نه.
 *  ۴) ویرایش فقط در پیش‌نویس. فرستادن فقط از پیش‌نویس. دریافت فقط در فرستاده/بخشی.
 *     لغو از پیش‌نویس، فرستاده یا بخشی (دو تای آخر با دلیل)؛ رسیدهای ثبت‌شده می‌مانند.
 *  ۵) رسید کلیدِ یکتا دارد: همان کلید با همان ردیف‌ها ⇒ «تکراری» (بی اثرِ دوم، حتی اگر سفارش
 *     در این میان کامل شده باشد)؛ همان کلید با ردیف‌های دیگر ⇒ `id_conflict`.
 *  ۶) بیش از باقی‌مانده فقط با `allowOver` (اختلافِ «اضافه» ثبت می‌شود).
 */
object PurchaseOrderCore {

  const val MAX_SAFE = 9_007_199_254_740_991L
  const val MAX_LINES = 500
  /** شناسه‌های خودِ سفارش (ردیف، کلیدِ رسید) */
  const val MAX_ID = 64
  /** شناسهٔ تأمین‌کننده و کالا از دفترِ دستگاه */
  const val MAX_REF = 128
  const val MILLI = 1000L
  const val BP = 10_000L

  /** وضعیتِ نمایشی (PUR-02) */
  enum class Status(val wire: String) {
    DRAFT("draft"), SENT("sent"), PARTIAL("partial"), COMPLETE("complete"), CANCELLED("cancelled");

    companion object {
      fun of(wire: String?): Status? = entries.firstOrNull { it.wire == wire }
    }
  }

  /** آنچه روی سفارش ذخیره می‌شود */
  object State {
    const val DRAFT = "draft"
    const val SENT = "sent"
    const val CANCELLED = "cancelled"
    val ALL = listOf(DRAFT, SENT, CANCELLED)
  }

  enum class Action(val wire: String) { EDIT("edit"), SEND("send"), CANCEL("cancel"), RECEIVE("receive") }

  /** کدهای رد — همان رشته‌ها در سرور و برنامه */
  object Code {
    const val BAD_STATE = "bad_state"
    const val SUPPLIER_REQUIRED = "supplier_required"
    const val LINES_REQUIRED = "lines_required"
    const val TOO_MANY_LINES = "too_many_lines"
    const val LINE_ID_REQUIRED = "line_id_required"
    const val DUPLICATE_LINE = "duplicate_line"
    const val PRODUCT_REQUIRED = "product_required"
    const val DUPLICATE_PRODUCT = "duplicate_product"
    const val BAD_QTY = "bad_qty"
    const val BAD_PRICE = "bad_price"
    const val OVERFLOW = "overflow"
    const val NOT_DRAFT = "not_draft"
    const val NOT_SENT = "not_sent"
    const val CANCELLED = "cancelled"
    const val COMPLETE = "complete"
    const val REASON_REQUIRED = "reason_required"
    const val RECEIPT_ID_REQUIRED = "receipt_id_required"
    const val ID_CONFLICT = "id_conflict"
    const val UNKNOWN_LINE = "unknown_line"
    const val DAMAGED_OVER_RECEIVED = "damaged_over_received"
    const val NOTHING_RECEIVED = "nothing_received"
    const val OVER_ORDERED = "over_ordered"
  }

  data class Line(val id: String, val productId: String, val qtyMilli: Long, val priceMinor: Long)

  /** سفارش: تأمین‌کننده، وضعیتِ ذخیره‌شده (`State`) و ردیف‌ها */
  data class Order(val supplierId: String, val state: String, val lines: List<Line>)

  /** یک ردیفِ رسید، همان‌طور که کاربر وارد کرده: رسیده، خراب (پذیرفته‌نشده) و فیِ همین رسید */
  data class ReceiptLine(val lineId: String, val receivedMilli: Long, val damagedMilli: Long, val priceMinor: Long)

  data class Receipt(val id: String, val lines: List<ReceiptLine>)

  /** دلیلِ رد؛ `lineId`/`index` اگر به یک ردیف برمی‌گردد */
  data class Failure(val code: String, val lineId: String? = null, val index: Int = -1)

  /* ── عدد ─────────────────────────────────────────────────────────── */

  private fun safe(x: Long) = x in -MAX_SAFE..MAX_SAFE

  private fun overflow(): Nothing = throw ArithmeticException("purchase-order-core: overflow")

  /** جمعِ دقیق در Long (سرریز ⇒ خطا)؛ سقفِ امن را نمی‌سنجد — برای گام‌های میانی */
  private fun addExact(a: Long, b: Long): Long {
    val r = a + b
    //  سرریزِ Long فقط وقتی دو عددِ هم‌علامت حاصلِ ناهم‌علامت بدهند
    if (((a xor r) and (b xor r)) < 0) overflow()
    return r
  }

  /** جمع با سقفِ ±(2^53−1) */
  internal fun add(a: Long, b: Long): Long {
    val r = addExact(a, b)
    if (!safe(r)) overflow()
    return r
  }

  private fun mul(a: Long, b: Long): Long {
    if (a == 0L || b == 0L) return 0L
    val r = a * b
    if (r / b != a || (a == -1L && b == Long.MIN_VALUE) || (b == -1L && a == Long.MIN_VALUE)) overflow()
    return r
  }

  /**
   *  round(a × b / c) با نیمه به سمتِ +∞ = floorDiv(2ab + c, 2c)، دقیق و بی BigInteger:
   *  a = qc + r و b = sc + t (۰ ≤ r، t < c) ⇒ ab/c = qsc + qt + rs + rt/c. فقط rt/c کسر دارد.
   *  شرط: |a|، |b| ≤ 2^53−1 و ۰ < c ≤ 2^31−1؛ حاصلِ بیرون از ±(2^53−1) ⇒ ArithmeticException.
   */
  fun mulDivRound(a: Long, b: Long, c: Long): Long {
    require(c in 1..Int.MAX_VALUE.toLong()) { "purchase-order-core: mulDivRound needs 0 < c < 2^31" }
    if (!safe(a) || !safe(b)) overflow()
    val q = a.floorDiv(c)
    val r = a.mod(c)
    val s = b.floorDiv(c)
    val t = b.mod(c)
    //  گام‌های میانی فقط سرریزِ Long را می‌سنجند؛ سقفِ امن فقط روی حاصل (همان BigInt ِ JS)
    val whole = addExact(addExact(mul(mul(q, s), c), mul(q, t)), mul(r, s))
    val out = addExact(whole, (2 * r * t + c).floorDiv(2 * c))
    if (!safe(out)) overflow()
    return out
  }

  /** جمعِ یک ردیف به یک‌صدمِ افغانی */
  fun lineTotalMinor(qtyMilli: Long, priceMinor: Long): Long = mulDivRound(qtyMilli, priceMinor, MILLI)

  private fun totalOrNull(qtyMilli: Long, priceMinor: Long): Long? =
    try { lineTotalMinor(qtyMilli, priceMinor) } catch (e: ArithmeticException) { null }

  private val ID = Regex("^[A-Za-z0-9._:-]{1,$MAX_ID}$")

  /** شناسهٔ خودِ سفارش: فقط حرف و رقمِ لاتین و `._:-`، تا ۶۴ — همان الگوی JS */
  private fun idOk(id: String) = ID.matches(id)

  /** شناسهٔ دفتر (کالا/تأمین‌کننده): ۱ تا ۱۲۸ نویسه و نه فقط فاصله/خطِ تازه (تعریفِ یکسان در JS) */
  private fun refOk(x: String) = x.length in 1..MAX_REF && !blank(x)

  /** «خالی» = فقط فاصله، تب و خطِ تازه (نه `isBlank` که با `trim` ِ JS یکی نیست) */
  fun blank(x: String?): Boolean = x == null || x.all { it == ' ' || it == '\t' || it == '\n' || it == '\r' }

  /* ── سفارش (PUR-02) ──────────────────────────────────────────────── */

  /** سفارشِ درست؟ `null` یعنی درست؛ ترتیبِ سنجش همان سرور است. */
  fun checkOrder(o: Order): Failure? {
    if (o.state !in State.ALL) return Failure(Code.BAD_STATE)
    if (!refOk(o.supplierId)) return Failure(Code.SUPPLIER_REQUIRED)
    if (o.lines.isEmpty()) return Failure(Code.LINES_REQUIRED)
    if (o.lines.size > MAX_LINES) return Failure(Code.TOO_MANY_LINES)
    val ids = HashSet<String>()
    val products = HashSet<String>()
    var total = 0L
    o.lines.forEachIndexed { i, l ->
      if (!idOk(l.id)) return Failure(Code.LINE_ID_REQUIRED, l.id, i)
      if (!ids.add(l.id)) return Failure(Code.DUPLICATE_LINE, l.id, i)
      if (!refOk(l.productId)) return Failure(Code.PRODUCT_REQUIRED, l.id, i)
      if (!products.add(l.productId)) return Failure(Code.DUPLICATE_PRODUCT, l.id, i)
      if (l.qtyMilli <= 0 || l.qtyMilli > MAX_SAFE) return Failure(Code.BAD_QTY, l.id, i)
      if (l.priceMinor < 0 || l.priceMinor > MAX_SAFE) return Failure(Code.BAD_PRICE, l.id, i)
      val lt = totalOrNull(l.qtyMilli, l.priceMinor) ?: return Failure(Code.OVERFLOW, l.id, i)
      total = try { add(total, lt) } catch (e: ArithmeticException) { return Failure(Code.OVERFLOW, l.id, i) }
    }
    return null
  }

  /** جمعِ سفارش به فیِ سفارش (سفارشِ درست؛ وگرنه ArithmeticException) */
  fun orderTotalMinor(o: Order): Long = o.lines.fold(0L) { acc, l -> add(acc, lineTotalMinor(l.qtyMilli, l.priceMinor)) }

  /** پیشرفتِ یک ردیف: همهٔ رسیدها با هم */
  data class Progress(
    val lineId: String,
    val productId: String,
    val orderedMilli: Long,
    val receivedMilli: Long,
    val damagedMilli: Long,
    val acceptedMilli: Long,
    val remainingMilli: Long,
    val overMilli: Long,
  )

  fun progress(o: Order, receipts: List<Receipt>): List<Progress> {
    val rec = HashMap<String, Long>()
    val dmg = HashMap<String, Long>()
    for (r in receipts) for (l in r.lines) {
      rec[l.lineId] = add(rec[l.lineId] ?: 0L, l.receivedMilli)
      dmg[l.lineId] = add(dmg[l.lineId] ?: 0L, l.damagedMilli)
    }
    return o.lines.map { l ->
      val received = rec[l.id] ?: 0L
      val damaged = dmg[l.id] ?: 0L
      val accepted = add(received, -damaged)
      Progress(
        lineId = l.id, productId = l.productId, orderedMilli = l.qtyMilli,
        receivedMilli = received, damagedMilli = damaged, acceptedMilli = accepted,
        remainingMilli = maxOf(0L, l.qtyMilli - accepted), overMilli = maxOf(0L, accepted - l.qtyMilli),
      )
    }
  }

  /** وضعیتِ نمایشی از وضعیتِ ذخیره‌شده و رسیدها */
  fun status(o: Order, receipts: List<Receipt>): Status = when (o.state) {
    State.CANCELLED -> Status.CANCELLED
    State.DRAFT -> Status.DRAFT
    else -> {
      val p = progress(o, receipts)
      when {
        p.all { it.remainingMilli == 0L } -> Status.COMPLETE
        p.any { it.acceptedMilli > 0 } -> Status.PARTIAL
        else -> Status.SENT
      }
    }
  }

  /** کارهای مجاز در هر وضعیت */
  fun allowed(s: Status): Set<Action> = when (s) {
    Status.DRAFT -> setOf(Action.EDIT, Action.SEND, Action.CANCEL)
    Status.SENT, Status.PARTIAL -> setOf(Action.CANCEL, Action.RECEIVE)
    Status.COMPLETE, Status.CANCELLED -> emptySet()
  }

  sealed interface Outcome {
    /** وضعیتِ ذخیره‌شدهٔ تازه */
    data class Changed(val state: String) : Outcome
    /** همین حالا همان است (تلاشِ دوباره بی اثر) */
    data object Unchanged : Outcome
    data class Failed(val failure: Failure) : Outcome
  }

  private fun refusal(s: Status): Failure = when (s) {
    Status.DRAFT -> Failure(Code.NOT_SENT)
    Status.CANCELLED -> Failure(Code.CANCELLED)
    Status.COMPLETE -> Failure(Code.COMPLETE)
    Status.SENT, Status.PARTIAL -> Failure(Code.NOT_DRAFT)
  }

  /** ویرایشِ ردیف‌ها/تأمین‌کننده: فقط پیش‌نویس، و سفارشِ تازه هم باید درست باشد */
  fun edit(current: Order, receipts: List<Receipt>, next: Order): Outcome {
    val s = status(current, receipts)
    if (s != Status.DRAFT) return Outcome.Failed(if (s == Status.CANCELLED) Failure(Code.CANCELLED) else Failure(Code.NOT_DRAFT))
    if (next.state != State.DRAFT) return Outcome.Failed(Failure(Code.BAD_STATE))
    checkOrder(next)?.let { return Outcome.Failed(it) }
    return Outcome.Changed(State.DRAFT)
  }

  /** فرستادن به تأمین‌کننده: پیش‌نویس ⇒ فرستاده؛ فرستاده/بخشی/کامل ⇒ بی اثر */
  fun send(o: Order, receipts: List<Receipt>): Outcome = when (status(o, receipts)) {
    Status.DRAFT -> checkOrder(o)?.let { Outcome.Failed(it) } ?: Outcome.Changed(State.SENT)
    Status.SENT, Status.PARTIAL, Status.COMPLETE -> Outcome.Unchanged
    Status.CANCELLED -> Outcome.Failed(Failure(Code.CANCELLED))
  }

  /** لغو: پیش‌نویس بی دلیل؛ فرستاده یا بخشی با دلیل (باقی‌مانده بسته می‌شود، رسیدها می‌مانند) */
  fun cancel(o: Order, receipts: List<Receipt>, reason: String?): Outcome = when (status(o, receipts)) {
    Status.DRAFT -> Outcome.Changed(State.CANCELLED)
    Status.SENT, Status.PARTIAL ->
      if (blank(reason)) Outcome.Failed(Failure(Code.REASON_REQUIRED)) else Outcome.Changed(State.CANCELLED)
    Status.COMPLETE -> Outcome.Failed(Failure(Code.COMPLETE))
    Status.CANCELLED -> Outcome.Unchanged
  }

  /* ── دریافت (PUR-04) ─────────────────────────────────────────────── */

  /** ردیفِ رسیدِ ثبت‌شده با اختلاف‌ها: انتظار = باقی‌ماندهٔ پیش از همین رسید */
  data class Posted(
    val lineId: String,
    val expectedMilli: Long,
    val receivedMilli: Long,
    val damagedMilli: Long,
    val acceptedMilli: Long,
    /** کمتر از انتظار رسید (باقی‌مانده باز می‌ماند) */
    val shortMilli: Long,
    /** بیش از انتظار پذیرفته شد (فقط با `allowOver`) */
    val overMilli: Long,
    val priceMinor: Long,
    /** پذیرفته × فی — همان مبلغی که به فاکتورِ خرید و بدهیِ تأمین‌کننده می‌رود */
    val totalMinor: Long,
  )

  sealed interface ReceiveResult {
    data class Ok(val receipt: Receipt, val lines: List<Posted>, val totalMinor: Long, val status: Status) : ReceiveResult
    /** همان کلید و همان ردیف‌ها پیش‌تر ثبت شده؛ چیزی تازه ثبت نمی‌شود */
    data class Duplicate(val receipt: Receipt) : ReceiveResult
    data class Failed(val failure: Failure) : ReceiveResult
  }

  private fun nonZero(lines: List<ReceiptLine>) = lines.filter { it.receivedMilli != 0L || it.damagedMilli != 0L }

  private fun sameLines(a: List<ReceiptLine>, b: List<ReceiptLine>): Boolean =
    nonZero(a).sortedBy { it.lineId } == nonZero(b).sortedBy { it.lineId }

  /**
   *  دریافتِ کامل یا جزئی. ردیفِ صفر (نه رسیده نه خراب) کنار می‌رود. ترتیبِ سنجش همان سرور:
   *  کلید ⇒ تکراری/تعارض ⇒ وضعیت ⇒ ردیف‌ها ⇒ «هیچ» ⇒ اضافه/سرریز.
   */
  fun receive(o: Order, receipts: List<Receipt>, input: Receipt, allowOver: Boolean = false): ReceiveResult {
    if (!idOk(input.id)) return ReceiveResult.Failed(Failure(Code.RECEIPT_ID_REQUIRED))
    receipts.firstOrNull { it.id == input.id }?.let { prior ->
      return if (sameLines(prior.lines, input.lines)) ReceiveResult.Duplicate(prior)
      else ReceiveResult.Failed(Failure(Code.ID_CONFLICT))
    }
    val s = status(o, receipts)
    if (Action.RECEIVE !in allowed(s)) return ReceiveResult.Failed(refusal(s))

    val byId = o.lines.associateBy { it.id }
    val seen = HashSet<String>()
    input.lines.forEachIndexed { i, l ->
      if (l.lineId !in byId) return ReceiveResult.Failed(Failure(Code.UNKNOWN_LINE, l.lineId, i))
      if (!seen.add(l.lineId)) return ReceiveResult.Failed(Failure(Code.DUPLICATE_LINE, l.lineId, i))
      if (l.receivedMilli < 0 || l.receivedMilli > MAX_SAFE || l.damagedMilli < 0 || l.damagedMilli > MAX_SAFE) {
        return ReceiveResult.Failed(Failure(Code.BAD_QTY, l.lineId, i))
      }
      if (l.damagedMilli > l.receivedMilli) return ReceiveResult.Failed(Failure(Code.DAMAGED_OVER_RECEIVED, l.lineId, i))
      if (l.priceMinor < 0 || l.priceMinor > MAX_SAFE) return ReceiveResult.Failed(Failure(Code.BAD_PRICE, l.lineId, i))
    }
    val kept = nonZero(input.lines)
    if (kept.isEmpty()) return ReceiveResult.Failed(Failure(Code.NOTHING_RECEIVED))

    val before = try { progress(o, receipts).associateBy { it.lineId } } catch (e: ArithmeticException) {
      return ReceiveResult.Failed(Failure(Code.OVERFLOW))
    }
    var total = 0L
    val posted = ArrayList<Posted>(kept.size)
    for (l in kept) {
      val i = input.lines.indexOf(l)
      val expected = before.getValue(l.lineId).remainingMilli
      val accepted = l.receivedMilli - l.damagedMilli
      val over = maxOf(0L, accepted - expected)
      if (over > 0 && !allowOver) return ReceiveResult.Failed(Failure(Code.OVER_ORDERED, l.lineId, i))
      val lt = totalOrNull(accepted, l.priceMinor) ?: return ReceiveResult.Failed(Failure(Code.OVERFLOW, l.lineId, i))
      total = try { add(total, lt) } catch (e: ArithmeticException) { return ReceiveResult.Failed(Failure(Code.OVERFLOW, l.lineId, i)) }
      posted += Posted(
        lineId = l.lineId, expectedMilli = expected, receivedMilli = l.receivedMilli, damagedMilli = l.damagedMilli,
        acceptedMilli = accepted, shortMilli = maxOf(0L, expected - l.receivedMilli), overMilli = over,
        priceMinor = l.priceMinor, totalMinor = lt,
      )
    }
    val receipt = Receipt(input.id, kept)
    //  جمع‌های همهٔ رسیدها با هم هم باید در سقف بمانند، تا `status`/`totals` روی دادهٔ ثبت‌شده هرگز نشکنند
    val after = try {
      val all = receipts + receipt
      totals(o, all)
      status(o, all)
    } catch (e: ArithmeticException) {
      return ReceiveResult.Failed(Failure(Code.OVERFLOW))
    }
    return ReceiveResult.Ok(receipt, posted, total, after)
  }

  /** جمع‌ها: سفارش (فیِ سفارش)، پذیرفته‌شده (فیِ هر رسید — همان فاکتورهای خرید)، باقی‌مانده (فیِ سفارش) */
  data class Totals(val orderedMinor: Long, val acceptedMinor: Long, val remainingMinor: Long)

  fun totals(o: Order, receipts: List<Receipt>): Totals {
    val price = o.lines.associate { it.id to it.priceMinor }
    var accepted = 0L
    for (r in receipts) for (l in r.lines) {
      if (l.lineId in price) accepted = add(accepted, lineTotalMinor(l.receivedMilli - l.damagedMilli, l.priceMinor))
    }
    val remaining = progress(o, receipts).fold(0L) { acc, p -> add(acc, lineTotalMinor(p.remainingMilli, price.getValue(p.lineId))) }
    return Totals(orderedMinor = orderTotalMinor(o), acceptedMinor = accepted, remainingMinor = remaining)
  }

  /* ── تطبیقِ سه‌طرفه (PUR-14) ─────────────────────────────────────── */

  /** یک ردیفِ فاکتورِ تأمین‌کننده (در برنامه: ردیفِ خریدِ وصل به سفارش)، با جمعِ همان ردیف */
  data class Bill(val lineId: String?, val productId: String, val qtyMilli: Long, val totalMinor: Long)

  /**
   *  مرزِ پذیرش: اختلافِ مقدار تا `qtyMilli`؛ اختلافِ مبلغ تا بیشترینِ `absMinor` و
   *  `priceBp` (ده‌هزارم) از مبلغِ مورد انتظار. همه صفر ⇒ فقط برابریِ دقیق.
   */
  data class Tolerance(val qtyMilli: Long = 0, val priceBp: Long = 0, val absMinor: Long = 0)

  enum class Issue(val wire: String) {
    RECEIVED_OVER_ORDER("received_over_order"),
    BILLED_OVER_RECEIVED("billed_over_received"),
    BILLED_UNDER_RECEIVED("billed_under_received"),
    PRICE_OVER_ORDER("price_over_order"),
    PRICE_UNDER_ORDER("price_under_order"),
    NOT_ON_ORDER("not_on_order"),
  }

  data class MatchLine(
    /** `null` برای ردیفِ فاکتوری که در سفارش نیست */
    val lineId: String?,
    val productId: String,
    val orderedMilli: Long,
    val acceptedMilli: Long,
    val billedMilli: Long,
    val orderPriceMinor: Long,
    /** مقدارِ فاکتورشده × فیِ سفارش */
    val expectedMinor: Long,
    val billedMinor: Long,
    val issues: List<Issue>,
  )

  data class Match(
    val lines: List<MatchLine>,
    val matched: Boolean,
    val orderedMinor: Long,
    /** پذیرفته × فیِ سفارش */
    val acceptedMinor: Long,
    val billedMinor: Long,
  )

  /**
   *  سفارش ⇔ رسید ⇔ فاکتور، ردیف‌به‌ردیف. فاکتور با `lineId` به ردیف می‌رسد، وگرنه با کالا
   *  (کالا در سفارش یکتاست)؛ بقیه «بیرون از سفارش»اند. ردیفی که نه رسیده و نه فاکتور شده
   *  اختلاف نیست (باقی‌ماندهٔ باز است).
   */
  fun match(o: Order, receipts: List<Receipt>, bills: List<Bill>, tol: Tolerance = Tolerance()): Match {
    require(tol.qtyMilli >= 0 && tol.priceBp >= 0 && tol.absMinor >= 0) { "purchase-order-core: negative tolerance" }
    val byId = o.lines.associateBy { it.id }
    val byProduct = o.lines.associateBy { it.productId }
    val billedQty = HashMap<String, Long>()
    val billedSum = HashMap<String, Long>()
    val outQty = LinkedHashMap<String, Long>()
    val outSum = HashMap<String, Long>()
    for (b in bills) {
      val line = b.lineId?.let { byId[it] } ?: byProduct[b.productId]
      if (line != null) {
        billedQty[line.id] = add(billedQty[line.id] ?: 0L, b.qtyMilli)
        billedSum[line.id] = add(billedSum[line.id] ?: 0L, b.totalMinor)
      } else {
        outQty[b.productId] = add(outQty[b.productId] ?: 0L, b.qtyMilli)
        outSum[b.productId] = add(outSum[b.productId] ?: 0L, b.totalMinor)
      }
    }
    val prog = progress(o, receipts).associateBy { it.lineId }
    val lines = ArrayList<MatchLine>()
    var accepted = 0L
    var billed = 0L
    for (l in o.lines) {
      val p = prog.getValue(l.id)
      val bq = billedQty[l.id] ?: 0L
      val bs = billedSum[l.id] ?: 0L
      val expected = lineTotalMinor(bq, l.priceMinor)
      val issues = ArrayList<Issue>()
      if (p.acceptedMilli > l.qtyMilli) issues += Issue.RECEIVED_OVER_ORDER
      if (bq - p.acceptedMilli > tol.qtyMilli) issues += Issue.BILLED_OVER_RECEIVED
      if (p.acceptedMilli - bq > tol.qtyMilli) issues += Issue.BILLED_UNDER_RECEIVED
      val allow = maxOf(tol.absMinor, mulDivRound(if (expected < 0) -expected else expected, tol.priceBp, BP))
      if (bs - expected > allow) issues += Issue.PRICE_OVER_ORDER
      if (expected - bs > allow) issues += Issue.PRICE_UNDER_ORDER
      accepted = add(accepted, lineTotalMinor(p.acceptedMilli, l.priceMinor))
      billed = add(billed, bs)
      lines += MatchLine(l.id, l.productId, l.qtyMilli, p.acceptedMilli, bq, l.priceMinor, expected, bs, issues)
    }
    for (pid in outQty.keys.sorted()) {
      val bs = outSum.getValue(pid)
      billed = add(billed, bs)
      lines += MatchLine(null, pid, 0L, 0L, outQty.getValue(pid), 0L, 0L, bs, listOf(Issue.NOT_ON_ORDER))
    }
    return Match(lines, lines.all { it.issues.isEmpty() }, orderTotalMinor(o), accepted, billed)
  }
}
