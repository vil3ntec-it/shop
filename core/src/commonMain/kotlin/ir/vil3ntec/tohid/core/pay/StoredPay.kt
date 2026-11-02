package ir.vil3ntec.tohid.core.pay

/**
 *  PAY-24 گام ۲ — کارتِ هدیه و اعتبارِ فروشگاه در تسویه، لغو و بازپرداخت. منطقِ خالص روی [Pay] و [GiftCard]؛ دفترش
 *  در برنامه (`data/GiftCards`: ردیف‌های سابقه و رسیدِ قرض‌دار، بی جدولِ تازه). پول به یک‌صدمِ افغانی (`Long`).
 *
 *  `Pay.settle` و `Pay.refund` (هم‌زادِ `license/pay-core.js` با بردارِ `pay-v1.json`) دست نمی‌خورند؛ این‌جا فقط
 *  روی خروجیِ آن‌ها کار می‌شود:
 *    • [methods] جدولِ روش‌ها با دو روشِ خاموشِ پیش‌فرض روشن (فقط با تنظیمِ دکان)؛ خاموش ⇒ همان [Pay.DEFAULT_METHODS].
 *    • [confirm] ردیفِ کارت/اعتبار «در انتظارِ تأییدِ دستی» نیست: دفترِ دکان همان لحظه آن را سنجیده ([LEDGER]).
 *    • [redeem] رویدادهای خرجِ کارت برای یک فاکتور با شناسهٔ ساخته از همان فاکتور (`<فاکتور>-g<n>`): تلاشِ دوباره
 *      همان رویداد است (`duplicate`)، هرگز خرجِ دوم؛ چند ردیفِ یک کارت روی هم سنجیده می‌شوند (نه هر کدام جدا).
 *    • [route] سهمِ ردیف‌های کارت و اعتبار از یک بازپرداخت همیشه به همان روش برمی‌گردد — حتی وقتی حالتِ برگشت
 *      «نقد» یا «اعتبار» است — پس کارتِ هدیه با برگشت نقد نمی‌شود و هر روش تا همان که گرفت پس می‌دهد.
 *    • [refundSplit] آن سهم میانِ کارت‌های همان فاکتور: هر کارت تا خرجش منهای برگشت‌های پیشینش، از آخر به اول.
 */
object StoredPay {

  const val GIFT = "gift_card"
  const val CREDIT = Pay.STORE_CREDIT
  val KINDS: Set<String> = setOf(GIFT, CREDIT)

  /** سطحِ تأییدِ ردیفِ کارت/اعتبار: دفترِ خودِ دکان، نه ثبتِ دستی (`manual`) */
  const val LEDGER = "ledger"

  const val NO_CUSTOMER = StoreCredit.NO_CUSTOMER
  const val INSUFFICIENT_CREDIT = StoreCredit.INSUFFICIENT_CREDIT

  /** جدولِ روش‌ها؛ [on] ⇒ کارتِ هدیه و اعتبارِ فروشگاه روشن، وگرنه همان پیش‌فرض (همان شیء) */
  fun methods(on: Boolean): List<PayMethod> =
    if (!on) Pay.DEFAULT_METHODS else Pay.DEFAULT_METHODS.map { if (it.kind in KINDS) it.copy(enabled = true) else it }

  fun has(s: Settlement): Boolean = s.payments.any { it.kind in KINDS && it.amount > 0 }

  /** ردیف‌های کارت/اعتبار ⇒ `confirmed` با [LEDGER]؛ تسویهٔ بی آن‌ها همان شیء */
  fun confirm(s: Settlement): Settlement =
    if (!has(s)) s else s.copy(payments = s.payments.map { if (it.kind in KINDS) it.copy(status = "confirmed", verification = LEDGER) else it })

  /** پولِ اعتبارِ فروشگاه در این تسویه */
  fun creditMinor(s: Settlement): Long = s.payments.filter { it.kind == CREDIT }.sumOf { it.amount }

  /** null یعنی سهمِ اعتبارِ این تسویه از طلبِ مشتری ([balanceMinor]، ماندهٔ دفترِ قرض‌دار) پرداخت‌شدنی است */
  fun creditProblem(s: Settlement, hasCustomer: Boolean, balanceMinor: Long): String? {
    val c = creditMinor(s)
    return if (c == 0L) null else StoreCredit.problem(hasCustomer, balanceMinor, c)
  }

  data class Spend(val code: String, val amountMinor: Long)

  /** ردیف‌های کارتِ هدیهٔ تسویه به ترتیب، با کدِ یک‌دست؛ کدِ نادرست ⇒ null */
  fun gifts(s: Settlement): List<Spend>? = s.payments.filter { it.kind == GIFT }.map { p ->
    Spend(GiftCard.normalize(p.ref) ?: return null, p.amount)
  }

  fun redeemId(saleId: String, n: Int): String = "$saleId-g$n"

  /**
   *  رویدادهای خرجِ کارتِ هدیه برای فاکتورِ [saleId] روی دفترِ [events] (همین لحظه). هر ردیف با [GiftCard.build]:
   *  خرج فقط تا ماندهٔ خرج‌شدنی، روی رویدادهای ردیف‌های پیشینِ همین فاکتور؛ رویدادِ همان شناسه با همان محتوا
   *  (تلاشِ دوباره) کنار می‌رود. خروجی فقط رویدادهای تازه.
   */
  fun redeem(events: List<GiftCard.Event>, s: Settlement, saleId: String, at: Long, by: String?): GiftCard.Result<List<GiftCard.Event>> {
    val rows = s.payments.filter { it.kind == GIFT }
    val work = ArrayList(events)
    val out = ArrayList<GiftCard.Event>()
    rows.forEachIndexed { n, p ->
      val input = GiftCard.Input(redeemId(saleId, n), GiftCard.REDEEM, p.ref, p.amount, at, by = by, refId = saleId)
      when (val r = GiftCard.build(work, input)) {
        is GiftCard.Result.Err -> return r
        is GiftCard.Result.Ok -> if (!r.duplicate) { work += r.value; out += r.value }
      }
    }
    return GiftCard.Result.Ok(out)
  }

  /**
   *  [amountMinor] میانِ کارت‌های [paid] (خرج‌های همان فاکتور به ترتیب): هر کارت تا خرجش منهای [back] (کد ⇒ آنچه
   *  پیش‌تر به آن برگشت)، از آخرین ردیف به اول — همان ترتیبِ `Pay.refund`. جا کم باشد ⇒ null (هرگز بیش از خرج).
   */
  fun refundSplit(paid: List<Spend>, back: Map<String, Long>, amountMinor: Long): List<Spend>? {
    if (amountMinor < 0) return null
    val used = HashMap(back)
    val room = paid.map { p ->
      val b = minOf(used[p.code] ?: 0L, p.amountMinor)
      used[p.code] = (used[p.code] ?: 0L) - b
      p.amountMinor - b
    }
    var rest = amountMinor
    val out = LinkedHashMap<String, Long>()
    for (i in paid.indices.reversed()) {
      val take = minOf(rest, room[i])
      if (take <= 0) continue
      out[paid[i].code] = (out[paid[i].code] ?: 0L) + take
      rest -= take
    }
    if (rest > 0) return null
    return out.map { (c, a) -> Spend(c, a) }
  }

  /**
   *  @property payouts بازپرداخت بی سهمِ کارت و اعتبار (همان روش‌ها و ترتیبِ `Refund.payouts`)
   *  @property giftMinor سهمِ ردیف‌های کارتِ هدیه — به همان کارت‌ها ([refundSplit])
   *  @property creditMinor سهمِ ردیف‌های اعتبارِ فروشگاه — به اعتبارِ همان مشتریِ فاکتور
   */
  data class Routed(val payouts: List<Payout>, val giftMinor: Long, val creditMinor: Long)

  /**
   *  `Pay.refund` هر برداشت از ردیف را در حالتِ `original` به همان روش و در `cash`/`store_credit` به جایگزین می‌دهد.
   *  برداشت از ردیفِ کارت/اعتبار ([before] ⇒ `Refund.payments`، ردیف‌به‌ردیف) از همان مقصد کم و جدا برمی‌گردد.
   *  بی چنین برداشتی همان `payouts` (همان شیء).
   */
  fun route(r: Refund, before: List<PaidRow>, mode: String, t: MethodTable): Routed {
    var gift = 0L
    var credit = 0L
    val off = LinkedHashMap<String, Long>()
    before.forEachIndexed { i, p ->
      val kind = t.kindOf(p.method)
      if (kind !in KINDS) return@forEachIndexed
      val take = (r.payments.getOrNull(i)?.refunded ?: p.refunded) - p.refunded
      if (take <= 0) return@forEachIndexed
      if (kind == GIFT) gift += take else credit += take
      val to = when (mode) { "cash" -> t.cashId; "store_credit" -> t.creditId ?: p.method; else -> p.method }
      off[to] = (off[to] ?: 0L) + take
    }
    if (gift == 0L && credit == 0L) return Routed(r.payouts, 0, 0)
    val rest = r.payouts.mapNotNull { o ->
      val a = o.amount - (off[o.method] ?: 0L).also { off.remove(o.method) }
      if (a > 0) o.copy(amount = a) else null
    }
    return Routed(rest, gift, credit)
  }
}
