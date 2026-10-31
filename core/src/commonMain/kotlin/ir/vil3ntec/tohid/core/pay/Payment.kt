package ir.vil3ntec.tohid.core.pay

import ir.vil3ntec.tohid.core.money.Currency
import ir.vil3ntec.tohid.core.money.MAX_EXACT
import ir.vil3ntec.tohid.core.money.Rounding
import ir.vil3ntec.tohid.core.money.addExact
import ir.vil3ntec.tohid.core.money.mulExact

/*
 *  هستهٔ پرداخت — PAY-01..07، 09، 10، 23 (docs/spec/06-PAY.md، بخشِ ۳.۴ و ۴.۲).
 *
 *  ⛔ هم‌زادِ `license/pay-core.js`؛ هر دو با `core/test-vectors/pay-v1.json`
 *  سنجیده می‌شوند (`PayCoreTest`). یکی را عوض کنی، دیگری هم.
 *
 *  کاتلینِ خالص است: نه اندروید، نه ساعت، نه ذخیره. هر مبلغ Long به واحدِ کوچک
 *  (پول = یک‌صدمِ افغانی). هنوز هیچ صفحه یا موتوری به این وصل نیست؛
 *  `SalesEngine` و `CashShift` همان حسابِ امروز را می‌کنند.
 */

/** نتیجه: مقدار یا کدِ خطا (همان کدهای JS) با جزئیاتِ اختیاری */
sealed interface PayResult<out T> {
  /** `duplicate`: همان عملیات (همان op_id و همان محتوا) پیش‌تر ثبت شده بود؛ اثرِ دومی نیست */
  data class Ok<T>(val value: T, val duplicate: Boolean = false) : PayResult<T>

  data class Err(
    val code: String,
    val index: Int? = null,
    val field: String? = null,
    val method: String? = null,
    val denomination: String? = null,
    val refundable: Long? = null,
    /** فقط وقتی شمارش کور نیست */
    val variance: Map<String, Long?>? = null,
  ) : PayResult<Nothing>
}

/** روشِ پرداخت (PAY-06). `kind` یکی از [Pay.KINDS] است. */
data class PayMethod(
  val id: String,
  val kind: String,
  val name: String,
  val needsRef: Boolean = false,
  /** سقفِ هر تراکنش به پول؛ null = بی سقف */
  val limit: Long? = null,
  val enabled: Boolean = true,
)

/** جدولِ روش‌ها، سنجیده: یک و فقط یک روشِ نقد، شناسه‌های یکتا */
class MethodTable internal constructor(val methods: List<PayMethod>) {
  private val byId: Map<String, PayMethod> = methods.associateBy { it.id }
  val cashId: String = methods.first { it.kind == Pay.CASH }.id
  val creditId: String? = methods.firstOrNull { it.kind == Pay.STORE_CREDIT }?.id

  operator fun get(id: String): PayMethod? = byId[id]

  /** نوعِ روش؛ `account` رزرو است (باقی‌ماندهٔ پرداختِ جزئی) */
  fun kindOf(id: String): String? = if (id == Pay.ACCOUNT) Pay.ACCOUNT else byId[id]?.kind
}

data class Tender(val method: String, val amount: Long, val currency: String? = null, val ref: String? = null)

data class SettleInput(
  val total: Long,
  val tenders: List<Tender> = emptyList(),
  val currency: String = "AFN",
  /** پلهٔ گردکردنِ نقدی به پول (۵۰۰ = ۵ افغانی)؛ ۰ = خاموش */
  val cashRounding: Long = 0,
  val customerId: String? = null,
  val methods: List<PayMethod>? = null,
)

data class PaymentRow(
  val method: String,
  val kind: String,
  val amount: Long,
  val ref: String?,
  val status: String,
  /** سطحِ تأیید (PAY-08): `manual` = ثبتِ دستی با شمارهٔ مرجع؛ نقد و حساب ندارند */
  val verification: String?,
)

data class Settlement(
  /** ارزِ همهٔ مبلغ‌ها (بخشِ ۱۹: ارزِ هر مبلغ صریح) */
  val currency: String,
  /** paid | on_account | short */
  val status: String,
  val total: Long,
  /** ردیفِ جدای «گردکردنِ نقدی» (نوعِ `rounding`)؛ ۰ = ندارد */
  val rounding: Long,
  val due: Long,
  val tendered: Long,
  val change: Long,
  val remaining: Long,
  val toAccount: Long,
  val payments: List<PaymentRow>,
)

data class ExchangeInput(
  val fromCurrency: String,
  val fromAmount: Long,
  val to: String = "AFN",
  /** چند واحدِ کوچکِ ارزِ مقصد برای یک واحدِ اصلیِ مبدأ (۷۰٫۲۵ افغانی ⇐ 7025) */
  val rate: Long,
  val source: String?,
  val at: Long?,
  val approvedBy: String?,
)

data class ExchangeRecord(
  val fromCurrency: String,
  val fromAmount: Long,
  val toCurrency: String,
  val toAmount: Long,
  val rate: Long,
  val source: String,
  val at: Long,
  val approvedBy: String,
)

/** ردیفِ پرداختِ یک فاکتور، از نگاهِ بازپرداخت */
data class PaidRow(val method: String, val amount: Long, val refunded: Long = 0, val status: String)

data class RefundInput(
  val payments: List<PaidRow>,
  val amount: Long,
  /** original | cash | store_credit */
  val mode: String,
  val approvedBy: String?,
  val customerId: String? = null,
  /** بدهیِ هنوز بازِ همین فاکتور؛ null = همهٔ سهمِ حساب */
  val accountOutstanding: Long? = null,
  val methods: List<PayMethod>? = null,
  val currency: String = "AFN",
)

data class Payout(val method: String, val kind: String, val amount: Long)

data class Refund(val currency: String, val amount: Long, val approvedBy: String, val payouts: List<Payout>, val payments: List<PaidRow>)

/** عددِ صحیحِ امن (۰ تا ۲^۵۳−۱) — همان `Number.isSafeInteger`ِ JS، تا هر دو زبان یک ورودی را رد کنند (بخشِ ۲۸.۴) */
internal fun nonNegExact(v: Long): Boolean = v in 0..MAX_EXACT
internal fun posExact(v: Long): Boolean = v in 1..MAX_EXACT

object Pay {
  const val ACCOUNT = "account"
  const val CASH = "cash"
  const val STORE_CREDIT = "store_credit"
  val KINDS = listOf("cash", "wallet", "card", "transfer", "store_credit", "gift_card", "other")
  internal val COUNTABLE = setOf("cash", "wallet", "card", "transfer", "other")

  /** اسکناس‌های ۱۰ تا ۱۰۰۰ افغانی و سکه‌های ۱، ۲ و ۵ — به پول (بخشِ ۴.۲) */
  val NOTES: List<Long> = listOf(1000, 2000, 5000, 10000, 50000, 100000)
  val COINS: List<Long> = listOf(100, 200, 500)

  /** آستانهٔ پیش‌فرضِ اختلافِ صندوق: ۵۰ افغانی (D-18) */
  const val DEFAULT_VARIANCE_THRESHOLD: Long = 5000

  val STATUSES = listOf("pending", "confirmed", "failed", "cancelled", "needs_review", "refunded", "partially_refunded")
  val TRANSITIONS: Map<String, List<String>> = mapOf(
    "pending" to listOf("confirmed", "failed", "cancelled", "needs_review"),
    "needs_review" to listOf("confirmed", "failed", "cancelled"),
    "confirmed" to listOf("partially_refunded", "refunded"),
    "partially_refunded" to listOf("partially_refunded", "refunded"),
    "failed" to emptyList(), "cancelled" to emptyList(), "refunded" to emptyList(),
  )
  private val REFUNDABLE = setOf("confirmed", "partially_refunded")

  /** PAY-06 — جدولِ پیش‌فرض. اعتبارِ فروشگاه و کارتِ هدیه تا دفترشان (PAY-24) خاموش‌اند. */
  val DEFAULT_METHODS: List<PayMethod> = listOf(
    PayMethod("cash", "cash", "نقد"),
    PayMethod("hesabpay", "wallet", "HesabPay", needsRef = true),
    PayMethod("e_money", "wallet", "e& money", needsRef = true),
    PayMethod("my_money", "wallet", "My Money", needsRef = true),
    PayMethod("atoma_pay", "wallet", "ATOMA Pay", needsRef = true, limit = 1_500_000),
    PayMethod("m_paisa", "wallet", "M-Paisa", needsRef = true),
    PayMethod("afpay_card", "card", "کارت AfPay", needsRef = true),
    PayMethod("hawala", "transfer", "حواله", needsRef = true),
    PayMethod("store_credit", "store_credit", "اعتبار فروشگاه", enabled = false),
    PayMethod("gift_card", "gift_card", "کارت هدیه", needsRef = true, enabled = false),
  )

  private val DEFAULT_TABLE = MethodTable(DEFAULT_METHODS)

  fun denominations(coins: Boolean): List<Long> = if (coins) COINS + NOTES else NOTES
  fun isDenomination(v: Long, coins: Boolean): Boolean = v in NOTES || (coins && v in COINS)

  // ── روش‌ها ───────────────────────────────────────────────
  fun defineMethods(list: List<PayMethod>): PayResult<List<PayMethod>> {
    val seen = HashSet<String>()
    val out = ArrayList<PayMethod>()
    list.forEachIndexed { i, x ->
      val id = x.id.trim()
      val name = x.name.trim()
      val limit = x.limit
      if (id.isEmpty() || id == ACCOUNT || id in seen || x.kind !in KINDS || name.isEmpty() ||
        (limit != null && !posExact(limit))
      ) return PayResult.Err("BAD_METHOD", index = i)
      seen += id
      out += PayMethod(id, x.kind, name, if (x.kind == CASH) false else x.needsRef, limit, x.enabled)
    }
    if (out.count { it.kind == CASH } != 1) return PayResult.Err("BAD_METHOD", index = -1)
    return PayResult.Ok(out)
  }

  //  عمومی (نه internal): `data/PayFlow.kt`ِ برنامه جدولِ پیش‌فرض را از این‌جا می‌گیرد و
  //  این هسته حالا ماژولِ جدای core است
  fun table(list: List<PayMethod>?): PayResult<MethodTable> {
    if (list == null) return PayResult.Ok(DEFAULT_TABLE)
    return when (val r = defineMethods(list)) {
      is PayResult.Ok -> PayResult.Ok(MethodTable(r.value))
      is PayResult.Err -> r
    }
  }

  // ── وضعیت (PAY-07) ───────────────────────────────────────
  fun canTransition(from: String, to: String): Boolean = TRANSITIONS[from]?.contains(to) == true

  fun transition(from: String, to: String): PayResult<String> = when {
    from !in STATUSES || to !in STATUSES -> PayResult.Err("BAD_STATUS")
    canTransition(from, to) -> PayResult.Ok(to)
    else -> PayResult.Err("BAD_TRANSITION")
  }

  // ── تسویه (PAY-01، 02، 04، 05، 09، 10) ──────────────────
  private class Analysis(
    val currency: String, val t: MethodTable, val total: Long, val cash: Long, val nonCash: Long, val rounded: Long,
    val rounding: Long, val change: Long, val kept: Long, val remaining: Long, val rows: List<PaymentRow?>,
  )

  private fun analyse(input: SettleInput): PayResult<Analysis> {
    val cur = try { Currency.of(input.currency) } catch (e: IllegalArgumentException) { return PayResult.Err("UNKNOWN_CURRENCY") }
    if (!nonNegExact(input.total)) return PayResult.Err("INVALID_AMOUNT")
    val step = input.cashRounding
    if (!nonNegExact(step) || (step > 0 && step % cur.stepMinor != 0L)) return PayResult.Err("INVALID_STEP")
    val t = when (val r = table(input.methods)) { is PayResult.Ok -> r.value; is PayResult.Err -> return r }

    var cash = 0L
    var nonCash = 0L
    val rows = ArrayList<PaymentRow?>()
    var cashSeen = false
    input.tenders.forEachIndexed { i, x ->
      val m = t[x.method] ?: return PayResult.Err("UNKNOWN_METHOD", index = i)
      if (!m.enabled) return PayResult.Err("METHOD_DISABLED", index = i)
      if (!posExact(x.amount)) return PayResult.Err("INVALID_AMOUNT", index = i)
      if ((x.currency ?: cur.code) != cur.code) return PayResult.Err("CURRENCY_MISMATCH", index = i)
      val ref = x.ref?.trim().orEmpty()
      if (m.needsRef && ref.isEmpty()) return PayResult.Err("REF_REQUIRED", index = i)
      if (m.limit != null && x.amount > m.limit) return PayResult.Err("METHOD_LIMIT", index = i)
      if (m.kind == CASH) {
        cash = addExact(cash, x.amount)
        if (!cashSeen) { cashSeen = true; rows += null }
      } else {
        nonCash = addExact(nonCash, x.amount)
        rows += PaymentRow(m.id, m.kind, x.amount, ref.ifEmpty { null }, "pending", "manual")
      }
    }
    // پولِ پس فقط از نقد: بیش‌پرداختِ کیف پول یا کارت پس داده نمی‌شود
    if (nonCash > input.total) return PayResult.Err("NON_CASH_OVERPAY")

    val cashDue = input.total - nonCash
    val rounded = if (step > 0) mulExact(Rounding.roundHalfUp(cashDue, step), step) else cashDue
    // گردکردنِ نقدی فقط وقتی نقد فاکتور را می‌بندد؛ هرگز بیش از مبلغِ دقیق نمی‌خواهد
    val rounding = if (cash > 0 && step > 0 && cash >= rounded) rounded - cashDue else 0L
    val due = addExact(cashDue, rounding)
    val change = if (cash >= due) cash - due else 0L
    val kept = cash - change
    return PayResult.Ok(Analysis(cur.code, t, input.total, cash, nonCash, rounded, rounding, change, kept, due - kept, rows))
  }

  fun settle(input: SettleInput): PayResult<Settlement> {
    val a = when (val r = analyse(input)) { is PayResult.Ok -> r.value; is PayResult.Err -> return r }
    val customer = input.customerId?.trim().orEmpty()
    val toAccount = if (customer.isNotEmpty()) a.remaining else 0L
    val payments = ArrayList<PaymentRow>()
    a.rows.forEach { r ->
      if (r != null) payments += r
      else if (a.kept > 0) payments += PaymentRow(a.t.cashId, CASH, a.kept, null, "confirmed", null)
    }
    if (toAccount > 0) payments += PaymentRow(ACCOUNT, ACCOUNT, toAccount, null, "confirmed", null)
    return PayResult.Ok(
      Settlement(
        currency = a.currency,
        status = when {
          a.remaining == 0L -> "paid"
          customer.isNotEmpty() -> "on_account"
          else -> "short"
        },
        total = a.total,
        rounding = a.rounding,
        due = addExact(a.total, a.rounding),
        tendered = addExact(a.cash, a.nonCash),
        change = a.change,
        remaining = a.remaining,
        toAccount = toAccount,
        payments = payments,
      )
    )
  }

  /** PAY-04 — نقدی که با آن فاکتور دقیق بسته می‌شود (با گردکردن، اگر روشن باشد) */
  fun exactCash(input: SettleInput): PayResult<Long> = when (val r = analyse(input)) {
    is PayResult.Ok -> PayResult.Ok(if (r.value.remaining == 0L) 0L else r.value.rounded - r.value.cash)
    is PayResult.Err -> r
  }

  // ── تبادلِ ارز (PAY-10) ─────────────────────────────────
  /** مبلغِ مقصد به پلهٔ ارزِ مقصد گرد و در خودِ رکورد ثابت می‌شود؛ نرخِ بعدی آن را عوض نمی‌کند. */
  fun exchange(op: ExchangeInput): PayResult<ExchangeRecord> {
    val src: Currency
    val dst: Currency
    try { src = Currency.of(op.fromCurrency); dst = Currency.of(op.to) } catch (e: IllegalArgumentException) {
      return PayResult.Err("UNKNOWN_CURRENCY")
    }
    if (src == dst) return PayResult.Err("EXCHANGE_SAME_CURRENCY")
    if (!posExact(op.fromAmount)) return PayResult.Err("INVALID_AMOUNT")
    if (!posExact(op.rate)) return PayResult.Err("INVALID_RATE")
    val source = op.source?.trim().orEmpty()
    val approver = op.approvedBy?.trim().orEmpty()
    if (source.isEmpty()) return PayResult.Err("EXCHANGE_FIELDS", field = "source")
    if (op.at == null || !posExact(op.at)) return PayResult.Err("EXCHANGE_FIELDS", field = "at")
    if (approver.isEmpty()) return PayResult.Err("EXCHANGE_FIELDS", field = "approvedBy")
    val amount = mulExact(
      Rounding.roundHalfUp(mulExact(op.fromAmount, op.rate), mulExact(src.minorPerMajor, dst.stepMinor)),
      dst.stepMinor,
    )
    return PayResult.Ok(ExchangeRecord(src.code, op.fromAmount, dst.code, amount, op.rate, source, op.at, approver))
  }

  // ── بازپرداخت (PAY-23) ──────────────────────────────────
  fun refund(input: RefundInput): PayResult<Refund> {
    val approver = input.approvedBy?.trim().orEmpty()
    if (approver.isEmpty()) return PayResult.Err("APPROVAL_REQUIRED")
    if (!posExact(input.amount)) return PayResult.Err("INVALID_AMOUNT")
    val mode = input.mode
    if (mode != "original" && mode != "cash" && mode != "store_credit") return PayResult.Err("BAD_MODE")
    val cur = try { Currency.of(input.currency) } catch (e: IllegalArgumentException) { return PayResult.Err("UNKNOWN_CURRENCY") }
    val t = when (val r = table(input.methods)) { is PayResult.Ok -> r.value; is PayResult.Err -> return r }
    if (mode == "store_credit") {
      if (input.customerId?.trim().isNullOrEmpty()) return PayResult.Err("CUSTOMER_REQUIRED")
      if (t.creditId == null) return PayResult.Err("UNKNOWN_METHOD", index = -1)
    }
    val pays = input.payments
    val left = LongArray(pays.size)
    var total = 0L
    pays.forEachIndexed { i, p ->
      if (t.kindOf(p.method) == null) return PayResult.Err("UNKNOWN_METHOD", index = i)
      if (!nonNegExact(p.amount) || !nonNegExact(p.refunded) || p.refunded > p.amount) return PayResult.Err("INVALID_AMOUNT", index = i)
      if (p.status !in STATUSES) return PayResult.Err("BAD_STATUS", index = i)
      left[i] = if (p.status in REFUNDABLE) p.amount - p.refunded else 0L
      total = addExact(total, left[i])
    }
    if (input.amount > total) return PayResult.Err("REFUND_EXCEEDS", refundable = total)
    if (input.accountOutstanding != null && !nonNegExact(input.accountOutstanding)) return PayResult.Err("INVALID_AMOUNT")
    var outstanding = input.accountOutstanding ?: Long.MAX_VALUE

    // ترتیب: اول بدهیِ همین فاکتور (مثلِ امروز)، بعد بقیه از آخر به اول
    val order = pays.indices.filter { pays[it].method == ACCOUNT } + pays.indices.reversed().filter { pays[it].method != ACCOUNT }
    val fallback = if (mode == "store_credit") t.creditId!! else t.cashId
    val payouts = LinkedHashMap<String, Long>()
    fun pay(method: String, amount: Long) {
      if (amount > 0) payouts[method] = addExact(payouts[method] ?: 0L, amount)
    }
    val used = LongArray(pays.size)
    var rest = input.amount
    for (idx in order) {
      val take = minOf(rest, left[idx])
      if (take <= 0) continue
      used[idx] = take
      rest -= take
      if (pays[idx].method == ACCOUNT) {
        val debt = minOf(take, outstanding)
        outstanding -= debt
        pay(ACCOUNT, debt)
        pay(fallback, take - debt) // بدهیِ تسویه‌شده: پولش جدا آمده، پس نقد یا اعتبار
      } else {
        pay(if (mode == "original") pays[idx].method else fallback, take)
      }
    }
    return PayResult.Ok(
      Refund(
        currency = cur.code,
        amount = input.amount,
        approvedBy = approver,
        payouts = payouts.map { (m, a) -> Payout(m, t.kindOf(m)!!, a) },
        payments = pays.mapIndexed { k, p ->
          val r = p.refunded + used[k]
          p.copy(
            refunded = r,
            status = if (used[k] > 0) (if (r == p.amount) "refunded" else "partially_refunded") else p.status,
          )
        },
      )
    )
  }
}
