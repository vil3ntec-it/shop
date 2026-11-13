package ir.vil3ntec.tohid.data

import ir.vil3ntec.tohid.core.i18n.StrFx
import ir.vil3ntec.tohid.core.i18n.StrLedger
import ir.vil3ntec.tohid.core.pay.CloseShiftInput
import ir.vil3ntec.tohid.core.pay.EventCounts
import ir.vil3ntec.tohid.core.pay.MethodTotals
import ir.vil3ntec.tohid.core.pay.Moves
import ir.vil3ntec.tohid.core.pay.OpenShiftInput
import ir.vil3ntec.tohid.core.pay.Pay
import ir.vil3ntec.tohid.core.pay.PayFix
import ir.vil3ntec.tohid.core.pay.PayMethod
import ir.vil3ntec.tohid.core.pay.PayResult
import ir.vil3ntec.tohid.core.pay.SettleInput
import ir.vil3ntec.tohid.core.pay.Settlement
import ir.vil3ntec.tohid.core.pay.Shift
import ir.vil3ntec.tohid.core.pay.ShiftCore
import ir.vil3ntec.tohid.core.pay.ShiftEventInput
import ir.vil3ntec.tohid.core.pay.ShiftReport
import ir.vil3ntec.tohid.core.pay.ShiftRow
import ir.vil3ntec.tohid.core.pay.ShiftSlip
import ir.vil3ntec.tohid.core.pay.StoredPay
import ir.vil3ntec.tohid.core.pay.Tender
import ir.vil3ntec.tohid.core.pay.TillRounding
import ir.vil3ntec.tohid.core.pay.ZSeq
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put

/**
 *  سیم‌کشیِ پرداخت و صندوق — PAY-01..07، 11..15، 19 (v3 A4).
 *
 *  فقط برنامه‌های بومی (اندروید و کامپیوتر؛ دستورِ صاحبِ مخزن: سامانهٔ دکان با برنامه‌هاست،
 *  نه وب). روی هستهٔ `core/pay/{Payment,Shift}.kt` و با همان بردارِ مرجع
 *  (`core/test-vectors/pay-v1.json`) سنجیده می‌شود (`PayFlowTest`).
 *
 *  خالص است: نه Context، نه ساعت، نه دیسک. دفتر، ساعت و شناسه را صفحه می‌دهد.
 *
 *  ── پشتِ پرچم ─────────────────────────────────────────────────────
 *  هیچ‌کدام بی `ShopPolicy.payV2` صدا زده نمی‌شود؛ پیش‌فرض خاموش است و فروش و صندوقِ
 *  امروز (`SalesEngine` بی `settlement`، `CashShift`) دست نمی‌خورد.
 *
 *  ── شکلِ داده: فقط افزودن ────────────────────────────────────────
 *  فروش: `payments`، `tenderedMinor`، `changeMinor`؛ معنای `paidAmount`، `remaining`،
 *  `debtGiven` همان امروز. `paymentMethod` برای خوانندهٔ کهنه خلاصه است ([legacyMethod]):
 *  فروشی که پولِ غیرنقد دارد دیگر `cash` نیست. صندوق: ردیف‌های سابقهٔ
 *  `cash_in`/`cash_out`/`no_sale` با `refId` = همان `shift_open`، و `shift_close`
 *  با `amount` = نقدِ شمرده (همان معنای امروز) و `z` = گزارشِ Zِ هسته در لحظهٔ بستن.
 */
object PayFlow {

  const val CASH = "cash"
  const val ACCOUNT = Pay.ACCOUNT

  // ── واحد ──────────────────────────────────────────────────
  /** افغانیِ دفتر ⇒ پول؛ همان `Money.fromLegacy` / `moneyFromLegacy` (Math.round(x × 100)) */
  fun minor(afn: Double): Long = if (afn.isNaN() || afn.isInfinite()) 0L else Math.round(afn * 100)

  fun major(m: Long): Double = m / 100.0

  /** null = خالی؛ NaN = نامعتبر؛ وگرنه عدد (افغانی) */
  fun amountOf(text: String?): Double? {
    val t = text?.trim().orEmpty()
    if (t.isEmpty()) return null
    val n = t.toDoubleOrNull() ?: return Double.NaN
    return if (n.isNaN() || n.isInfinite() || n < 0) Double.NaN else n
  }

  // ── روش‌ها ────────────────────────────────────────────────
  fun method(id: String): PayMethod? =
    if (id == ACCOUNT) PayMethod(ACCOUNT, ACCOUNT, "به حسابِ مشتری") else Pay.DEFAULT_METHODS.firstOrNull { it.id == id }  // داده: نامِ روش هم‌شکلِ `Pay.DEFAULT_METHODS` ِ هسته (یادداشتِ سابقه)
      ?: if (id == Pay.FX) PayMethod(Pay.FX, Pay.FX, "ارز") else null  // داده: PAY-10 — روشِ رزروِ ارزِ نقد، هم‌شکلِ `KIND_LABELS`

  fun methodName(id: String): String = method(id)?.name ?: id

  /**
   *  روش‌های ردیفِ پنل: روشن و غیرنقد (کیف پول، کارت، حواله). [stored] (PAY-24، `GiftCardPref`) ⇒ کارتِ هدیه و اعتبارِ
   *  فروشگاه هم؛ پیش‌فرض خاموش ⇒ همان فهرستِ امروز.
   */
  fun panelMethods(stored: Boolean = false, off: Set<String> = emptySet()): List<PayMethod> =
    StoredPay.methods(stored).filter { it.enabled && it.kind != CASH && it.id !in off }

  /**
   *  PAY-06 — روش‌هایی که دکان می‌تواند از پنل بردارد (`PayMethodsPref`): غیرنقد و جز کارتِ هدیه/اعتبار (که تنظیمِ
   *  خودشان را دارند، PAY-24)، به ترتیبِ جدولِ هسته.
   */
  fun toggleableMethods(): List<PayMethod> = Pay.DEFAULT_METHODS.filter { it.kind != CASH && it.kind !in StoredPay.KINDS }

  /** فهرستِ «خاموش» ِ ذخیره‌شده، سنجیده: فقط شناسه‌های [toggleableMethods]؛ نقد، ناشناس یا خراب نادیده ⇒ همان پنلِ امروز */
  fun offMethods(raw: Set<String>?): Set<String> {
    if (raw.isNullOrEmpty()) return emptySet()
    val known = toggleableMethods().map { it.id }.toSet()
    return raw.filterTo(LinkedHashSet()) { it in known }
  }

  // ── خطا و پیام ─────────────────────────────────────────
  data class Problem(
    val code: String,
    /** ردیفِ پنل که خطا دارد؛ ‎-1 یعنی نقد یا کلِ فاکتور */
    val index: Int = -1,
    val method: String? = null,
    val remaining: Long = 0,
    val variance: Map<String, Long?>? = null,
  )

  fun message(p: Problem): String {
    val row = p.method?.let { "«${methodName(it)}»" }
    return when (p.code) {
      "REF_REQUIRED" -> StrLedger.refRequired(row ?: StrLedger.thisMethod)
      "NON_CASH_OVERPAY" -> StrLedger.nonCashOverpay
      "METHOD_LIMIT" -> StrLedger.methodLimit(row ?: StrLedger.thisMethod)
      "METHOD_DISABLED", "UNKNOWN_METHOD" -> StrLedger.methodUnknown
      "SHORT" -> StrLedger.shortBy(fmt(p.remaining))
      "TOTAL_MISMATCH" -> StrLedger.payMismatch
      "INSUFFICIENT_CASH" -> StrLedger.cashShort
      "REASON_REQUIRED" -> StrLedger.reasonNeeded
      "NOTE_REQUIRED" -> StrLedger.otherNeedsNote
      "BAD_REASON" -> StrLedger.reasonBad
      "APPROVAL_REQUIRED" -> StrLedger.drawerOnlyApproved
      "SHIFT_CLOSED" -> StrLedger.noOpenTill
      "SHIFT_OPEN_ALREADY" -> StrLedger.tillAlreadyOpen
      "INVALID_TIME" -> StrLedger.clockBehind
      "BAD_DENOMINATION", "INVALID_COUNT" -> StrLedger.notesCountBad
      //  PAY-10: ارزِ نقد (کادرِ ارز، `FxTillFlow`)
      "FX_NO_RATE" -> StrFx.noRate
      "FX_STALE" -> StrFx.stale
      "FX_EXCESS" -> StrFx.excess
      "FX_INVALID", "CURRENCY_MISMATCH" -> StrFx.invalid
      "INVALID_AMOUNT" -> if (p.method == Pay.FX) StrFx.amountBad else StrLedger.amountInvalid
      //  PAY-24: کارتِ هدیه و اعتبارِ فروشگاه (فقط با تنظیم)
      "BAD_CODE", "UNKNOWN_CARD", "CARD_VOID", "INSUFFICIENT_BALANCE", "NO_CUSTOMER", "INSUFFICIENT_CREDIT" -> GiftCards.message(p.code)
      else -> StrLedger.amountInvalid
    }
  }

  private fun fmt(m: Long): String = (if (m < 0) "−" else "") + ir.vil3ntec.tohid.money(kotlin.math.abs(m) / 100.0)

  // ── پنلِ پرداخت (PAY-01، 02، 04، 05، 06، 07) ─────────────
  data class DraftRow(val method: String, val ref: String = "", val amount: String = "")

  /** متنِ همان کادرها؛ `total` همان `finalTotal`ِ فاکتور (افغانی) */
  data class Draft(
    val total: Double,
    val cash: String = "",
    val rows: List<DraftRow> = emptyList(),
    val customerId: String? = null,
    /** PAY-24 — کارتِ هدیه و اعتبارِ فروشگاه روشن (`GiftCardPref`)؛ `false` ⇒ همان جدولِ امروز (هر دو خاموش) */
    val stored: Boolean = false,
    /** PAY-09 — پلهٔ گردکردنِ نقدی به پول (`CashRoundPref`)؛ ۰ ⇒ همان امروز. هسته فقط سهمِ نقد را گرد می‌کند */
    val cashRounding: Long = 0,
    /** PAY-10 — کادرِ ارزِ نقد (`FxTillPref`، پیش‌فرض خاموش)؛ `null` یا مبلغِ خالی ⇒ همان تسویهٔ امروز */
    val fx: FxTillFlow.Tender? = null,
  )

  sealed interface Outcome<out T> {
    data class Ok<T>(val value: T) : Outcome<T>
    data class Bad(val problem: Problem) : Outcome<Nothing>

    /** مقدار، یا `null` اگر خطا بود */
    fun valueOrNull(): T? = if (this is Ok) value else null
  }

  private class Built(val input: SettleInput, val at: List<Int>)

  /** ردیفِ بی مبلغ و بی مرجع نادیده گرفته می‌شود (ردیفِ خالیِ تازه) */
  private fun input(d: Draft, withCash: Boolean): Outcome<Built> {
    val tenders = ArrayList<Tender>()
    val at = ArrayList<Int>()
    if (withCash) {
      val c = amountOf(d.cash)
      if (c != null && c.isNaN()) return Outcome.Bad(Problem("INVALID_AMOUNT"))
      if (c != null && c > 0) { tenders += Tender(CASH, minor(c)); at += -1 }
    }
    d.rows.forEachIndexed { i, r ->
      val a = amountOf(r.amount)
      val ref = r.ref.trim()
      if (a != null && a.isNaN()) return Outcome.Bad(Problem("INVALID_AMOUNT", i, r.method))
      if (a == null || a == 0.0) {
        if (ref.isEmpty()) return@forEachIndexed
        return Outcome.Bad(Problem("INVALID_AMOUNT", i, r.method))
      }
      //  PAY-24: کدِ کارت یک‌دست (فاصله، خط‌تیره، رقمِ فارسی)؛ اعتبار فقط با مشتری
      val kind = if (d.stored) method(r.method)?.kind else null
      val code = if (kind == StoredPay.GIFT) ir.vil3ntec.tohid.core.pay.GiftCard.normalize(ref) ?: return Outcome.Bad(Problem("BAD_CODE", i, r.method)) else ref
      if (kind == StoredPay.CREDIT && d.customerId.isNullOrBlank()) return Outcome.Bad(Problem("NO_CUSTOMER", i, r.method))
      tenders += Tender(r.method, minor(a), ref = code.ifEmpty { null })
      at += i
    }
    val methods = if (d.stored) StoredPay.methods(true) else null
    val total = minor(d.total)
    //  PAY-10: ارزِ نقد ⇒ رکوردِ تبادلِ همین لحظه با نرخِ دکان (کهنه ⇒ رد)؛ هسته از نو می‌سازد و برابر می‌سنجد
    val fx: List<ir.vil3ntec.tohid.core.pay.ExchangeRecord> = when (val r = FxTillFlow.record(d.fx)) {
      null -> emptyList()
      is PayResult.Ok -> listOf(r.value)
      is PayResult.Err -> return Outcome.Bad(Problem(r.code, method = Pay.FX))
    }
    return Outcome.Ok(Built(SettleInput(total = total, tenders = tenders, cashRounding = TillRounding.stepFor(total, d.cashRounding), customerId = d.customerId?.trim()?.ifEmpty { null }, methods = methods, fx = fx), at))
  }

  /** پیش‌نمایشِ زنده: همان `settle`ِ هسته؛ خطا با شمارهٔ ردیف و روش */
  fun preview(d: Draft): Outcome<Settlement> {
    val b = when (val x = input(d, true)) { is Outcome.Ok -> x.value; is Outcome.Bad -> return x }
    return when (val s = Pay.settle(b.input)) {
      //  PAY-24: ردیفِ کارت/اعتبار «تأییدشده با دفتر» است (`StoredPay.confirm`)؛ تسویهٔ بی آن همان شیء
      is PayResult.Ok -> Outcome.Ok(StoredPay.confirm(s.value))
      is PayResult.Err -> {
        //  PAY-10: خطای رکوردِ ارز شمارهٔ ردیفِ ارز دارد، نه ردیفِ پنل
        if (s.field == "fx") return Outcome.Bad(Problem(s.code, method = Pay.FX))
        val i = s.index?.takeIf { it >= 0 }?.let { b.at[it] } ?: -1
        Outcome.Bad(Problem(s.code, i, if (i >= 0) d.rows[i].method else null))
      }
    }
  }

  /** PAY-04 — «نقدِ دقیق»: نقدی که با ردیف‌های غیرنقدِ فعلی فاکتور را می‌بندد (افغانی؛ ۰ یعنی لازم نیست) */
  fun exactCash(d: Draft): Outcome<Double> {
    val b = when (val x = input(d, false)) { is Outcome.Ok -> x.value; is Outcome.Bad -> return x }
    return when (val r = Pay.exactCash(b.input)) {
      is PayResult.Ok -> Outcome.Ok(major(r.value))
      is PayResult.Err -> Outcome.Bad(Problem(r.code))
    }
  }

  /** فیلدهای فروش از تسویه — معنای امروزِ فیلدهای قدیمی (`paymentMethod`: [legacyMethod]) + سه فیلدِ تازه */
  data class SaleFields(
    val paymentMethod: String,
    val paidAmount: Double,
    val remaining: Double,
    val debtGiven: Double,
    val payments: List<SalePayment>,
    val tenderedMinor: Long,
    val changeMinor: Long,
  )

  /** `paymentMethod`ِ فروشی که نقد و غیرنقد (یا چند نوعِ غیرنقد) با هم دارد */
  const val MIXED = "mixed"

  /**
   *  `paymentMethod`ِ قدیمی از تسویه — برای خواننده‌های کهنه (فیلترِ «نقدی»ِ تاریخچه،
   *  برچسب‌ها، گزارشِ «روش پرداخت»، برنامه‌های قدیمی و نسخهٔ وب). ردیف‌های دقیق در `payments`.
   *   - چیزی به حساب رفت ⇒ `credit` (همان امروز؛ `SalesEngine` بدهی را با همین می‌سازد)
   *   - فقط نقد (یا فاکتورِ صفر) ⇒ `cash`
   *   - فقط یک نوعِ غیرنقد ⇒ همان نوع: `wallet`، `card`، `transfer`، …
   *   - نقد + غیرنقد، یا چند نوعِ غیرنقد ⇒ [MIXED]
   *  هر مقدارِ جز `cash`/`credit` را خوانندهٔ کهنه «نقدِ کامل» نمی‌شمارد (فیلترش `== "cash"` است)
   *  و نسیه هم نمی‌داند (`== "credit"`)؛ پس هیچ فروشِ کارتی/کیف‌پولی به فیلترِ نقد نمی‌رود.
   */
  fun legacyMethod(s: Settlement): String {
    if (s.toAccount > 0) return "credit"
    //  PAY-10: ارزِ نقد برای خوانندهٔ کهنه نقد است (کشو، فیلترِ «نقدی»، نوعِ رسید)؛ ریزش در `payments`
    val kinds = s.payments.filter { it.kind != ACCOUNT && it.amount > 0 }.map { if (it.kind == Pay.FX) CASH else it.kind }.toSet()
    return when {
      kinds.isEmpty() || kinds == setOf(Pay.CASH) -> CASH
      kinds.size == 1 -> kinds.single()
      else -> MIXED
    }
  }

  fun saleFields(s: Settlement): SaleFields = SaleFields(
    paymentMethod = legacyMethod(s),
    paidAmount = major(s.due - s.toAccount), // PAY-09: `due` = جمع + ردیفِ گرد؛ بی گرد همان `total`
    remaining = major(s.toAccount),
    debtGiven = major(s.toAccount),
    payments = s.payments.map { SalePayment(it.method, it.kind, it.amount, it.status, it.ref, it.verification, payee = it.payee, fx = it.fx?.let(FxTillFlow::saleFx)) },
    tenderedMinor = s.tendered,
    changeMinor = s.change,
  )

  /** تسویهٔ نهایی برای ثبت: فروشِ ناتمام (باقی‌ماندهٔ بی مشتری) رد می‌شود */
  fun checkout(d: Draft): Outcome<Settlement> = when (val s = preview(d)) {
    is Outcome.Bad -> s
    is Outcome.Ok -> if (s.value.status == "short") Outcome.Bad(Problem("SHORT", remaining = s.value.remaining)) else s
  }

  // ── برچسبِ روشِ پرداختِ فاکتور (تاریخچه، فاکتورها، جزئیات، رسید) ──
  /**
   *  نامِ فارسیِ هر نوعِ پرداخت و هر مقدارِ `paymentMethod` (قدیمی و [legacyMethod]).
   *  همان برچسب‌های جدولِ «فروش به تفکیک روش پرداخت» (`ReportTables` و `report-core.js`).
   */
  val KIND_LABELS: Map<String, String> = linkedMapOf(
    //  «به حساب» همان نسیه است (واژهٔ دکان)؛ «به حسابِ مشتری» فقط متنِ ردیفِ رسید/جزئیات ([receiptLines])
    CASH to "نقد", "credit" to "نسیه", ACCOUNT to "نسیه", "card" to "کارت", "transfer" to "حواله",  // داده: برچسبِ روشِ پرداخت — هم‌شکلِ `ReportTables`/`report-core.js` و ستونِ خروجی (منتظرِ تصمیمِ صاحب‌کار)
    "wallet" to "کیف پول", "store_credit" to "اعتبار فروشگاه", "gift_card" to "کارت هدیه", "other" to "سایر", MIXED to "ترکیبی",  // داده: برچسبِ روشِ پرداخت — هم‌شکلِ `ReportTables`/`report-core.js` و ستونِ خروجی (منتظرِ تصمیمِ صاحب‌کار)
    Pay.FX to "ارز",  // داده: PAY-10 — ردیفِ ارزِ نقد (ریزِ ارز و نرخ کنارش، `FxTillFlow.detail`)
    //  فاکتورِ W ِ سفارشِ اینترنتی (#321): روشِ سفارش، نه نوعِ ردیف
    ONLINE to "پرداختِ آنلاین", COD to "نقد هنگامِ تحویل",  // داده: برچسبِ روشِ پرداخت — هم‌شکلِ `ReportTables`/`report-core.js` و ستونِ خروجی (منتظرِ تصمیمِ صاحب‌کار)
  )

  /** `paymentMethod`ِ فاکتورِ W: پرداختِ آنلاین و نقد هنگامِ تحویل (دستِ پیک) — هیچ‌کدام نقدِ دخل نیست */
  const val ONLINE = "online"
  const val COD = "cod"

  /** گروهِ فیلترِ فهرست: [CASH] (فقط نقد)، `credit` (چیزی به حساب رفت)، [NON_CASH] (کارت، کیف پول، حواله یا ترکیبی) */
  const val NON_CASH = "noncash"

  /** نوع‌های پرداختِ این فاکتور (ردیف‌های با مبلغ)؛ خالی یعنی فاکتورِ بی `payments` */
  private fun kinds(s: Sale): Set<String> = s.payments.orEmpty().filter { it.amountMinor > 0 }.mapTo(LinkedHashSet()) { it.kind }

  /**
   *  گروهِ پرداختِ فاکتور برای فیلتر. با `payments` از ردیف‌ها؛ بی آن از `paymentMethod`
   *  (`credit` ⇒ نسیه، نوع‌های غیرنقد و `mixed` ⇒ غیرنقد، بقیه مثلِ امروز نقد).
   */
  fun payGroup(s: Sale): String {
    //  سفارشِ اینترنتی (آنلاین یا نقد هنگامِ تحویل) پولِ دخل نیست ⇒ غیرنقد
    if (s.paymentMethod == ONLINE || s.paymentMethod == COD || CashShift.isOnline(s)) return NON_CASH
    val k = kinds(s)
    if (k.isEmpty()) return when (s.paymentMethod) {
      "credit" -> "credit"
      in KIND_LABELS.keys - setOf(CASH, "credit", ACCOUNT) -> NON_CASH
      else -> CASH
    }
    return when {
      ACCOUNT in k -> "credit"
      k.all { it == CASH || it == Pay.FX } -> CASH // PAY-10: ارزِ نقد هم نقد است
      else -> NON_CASH
    }
  }

  /**
   *  برچسبِ کوتاهِ روشِ پرداختِ فاکتور: نقد / کارت / حواله / کیف پول / نسیه / ترکیبی.
   *  با `payments` از ردیف‌ها (یک نوع ⇒ همان، بیشتر ⇒ «ترکیبی»)؛ بی آن از `paymentMethod`
   *  (`credit` ⇒ «نسیه»؛ مقدارِ ناشناخته مثلِ امروز نقد). `cash` واژهٔ نقدِ همان صفحه است
   *  («نقد» یا «نقدی») تا متنِ فاکتورِ امروز عوض نشود.
   */
  fun payLabel(s: Sale, cash: String = KIND_LABELS.getValue(CASH)): String {
    //  فاکتورِ W: روشِ سفارش (ردیفش `other` یا `cash`ِ دستِ پیک است، نه کارت یا نقدِ دخل)
    if (s.paymentMethod == ONLINE || s.paymentMethod == COD) return KIND_LABELS.getValue(s.paymentMethod)
    val k = kinds(s)
    val key = when {
      k.isEmpty() -> s.paymentMethod
      k.size == 1 -> k.single()
      else -> MIXED
    }
    return if (key == CASH) cash else KIND_LABELS[key] ?: if (k.isEmpty()) cash else key
  }

  /** نامِ یک ردیف: نامِ روش (یا `online`/`cod`ِ فاکتورِ W)؛ روشِ ناشناخته (جدولِ دکان) ⇒ نامِ نوعش */
  private fun rowName(p: SalePayment): String =
    method(p.method)?.name ?: (if (p.method == ONLINE || p.method == COD) KIND_LABELS[p.method] else null) ?: KIND_LABELS[p.kind] ?: p.method

  /** «در انتظار»: ثبتِ دستی تا تأیید (PAY-08)، یا پولِ سفارش تا تأییدِ درگاه/تسویهٔ پیک */
  private fun pendingNote(p: SalePayment): String = when {
    p.status != "pending" -> ""
    p.verification == "manual" -> StrLedger.pendingManual
    else -> StrLedger.pendingWait
  }

  /**
   *  PAY-09 — ردیفِ جدای «گردکردنِ نقدی» ِ فاکتور (افغانیِ علامت‌دار) برای فاکتور، رسید و گزارش؛ بی گرد ⇒ null.
   *  مبلغِ نهایی همین حالا آن را دارد (`Sale.roundingMinor`)؛ این ردیف فقط می‌گوید از کجا آمد.
   */
  fun rounding(s: Sale): Double? = s.roundingMinor?.takeIf { it != 0L }?.let(::major)

  /** PAY-09 — ردیفِ گردِ نقدِ کاملِ صندوقِ ساده (افغانی)، پیش از ثبت: همان حسابِ `SalesEngine.record` */
  fun cashRounding(finalTotal: Double, step: Long): Double = major(TillRounding.cash(minor(finalTotal), step))

  /**
   *  ردیف‌های پرداختِ ترکیبی روی فاکتور و رسید (برچسب، افغانی) — جمعشان همان مبلغِ فاکتور.
   *  فاکتورِ بی `payments` (امروز، یا پرچمِ خاموش) هیچ ردیفی نمی‌گیرد.
   *  [name] نامِ روش را برای کاغذ به زبانِ برنامه می‌برد (#348، `Receipts.payName`)؛ پیش‌فرض همان نام.
   */
  fun receiptLines(s: Sale, name: (String) -> String = { it }): List<Pair<String, Double>> {
    val list = s.payments?.takeIf { it.isNotEmpty() } ?: return emptyList()
    return buildList {
      list.filter { it.amountMinor > 0 }.forEach { p ->
        //  PAY-24: کدِ کارتِ هدیه خودش پول است ⇒ روی رسید فقط چهار نویسهٔ آخر
        //  PAY-10: ردیفِ ارز مبلغِ اصلیِ ارز و نرخِ همان لحظه را کنارش دارد (مبلغِ ردیف سهمِ افغانیِ فاکتور است)
        add(name(rowName(p)) + pendingNote(p) + (p.fx?.let { " — " + FxTillFlow.detail(it) } ?: "") +
          (p.ref?.let { " — " + if (p.kind == StoredPay.GIFT) GiftCards.mask(it) else it } ?: "") to major(p.amountMinor))
      }
      s.tenderedMinor?.takeIf { it > 0 }?.let { add(StrLedger.tendered to major(it)) }
      s.changeMinor?.takeIf { it > 0 }?.let { add(StrLedger.change to major(it)) }
    }
  }

  // ── صندوق (PAY-11..15، 19) ───────────────────────────────
  private val AUDIT_TYPES = setOf("cash_in", "cash_out", "no_sale")

  /** PAY-10 — یادداشتِ خروجِ نقدِ «پولِ پسِ ارز» در شیفت */
  private const val FX_CHANGE_NOTE = "پولِ پسِ ارز"  // داده: دلیلِ رویدادِ شیفت درونِ گزارشِ Z ِ ذخیره‌شده

  /**
   *  ردیف‌های فروشِ یک فاکتور: `payments` اگر هست، وگرنه همان حسابِ امروز. با [fixes] (POS-40، `PayFixFlow.bySale`)
   *  اصلاح‌های روشِ پرداختِ تا [until] روی همان ردیف‌ها می‌نشینند (`PayFix.apply`)؛ بی اصلاح همان ردیف‌های امروز.
   */
  fun saleRows(s: Sale, fixes: List<PayFix.Fix> = emptyList(), until: Long = Long.MAX_VALUE): List<ShiftRow> {
    val base = saleRowsAsPosted(s)
    if (fixes.isEmpty()) return base
    return PayFix.apply(s.id, base.map { PayFix.Row(it.method, it.amount) }, fixes, until)
      .rows.map { ShiftRow(it.method, it.amount) }
  }

  private fun saleRowsAsPosted(s: Sale): List<ShiftRow> {
    s.payments?.takeIf { it.isNotEmpty() }?.let { list ->
      //  ردیفی که امانتِ کسِ دیگری است (`custody`، مثلاً پیک) پولِ این صندوق نیست
      return list.filter { it.amountMinor > 0 && it.custody == null }.map { ShiftRow(it.method, it.amountMinor) }
    }
    val debt = s.debtGiven.coerceAtLeast(0.0)
    val cash = (s.finalTotal - debt).coerceAtLeast(0.0)
    return buildList {
      if (minor(cash) > 0) add(ShiftRow(CASH, minor(cash)))
      if (minor(debt) > 0) add(ShiftRow(ACCOUNT, minor(debt)))
    }
  }

  /**
   *  دفتر ⇒ رویدادهای هسته. ترتیب: ورودی‌ها، خروجِ ثبت‌شده در صندوق، برگشت، خروج‌های دفتر.
   *  هسته ترتیبِ زمانی نمی‌خواهد؛ این ترتیب فقط نمی‌گذارد خروجی که در لحظهٔ خودش سنجیده و
   *  پذیرفته شده، این‌جا به‌خاطرِ رویدادِ دیرتر رد شود. همان `ledgerEvents`ِ JS.
   */
  /** جدولِ روش‌های شیفتی که [replay] باز می‌کند (`OpenShiftInput` بی `methods` ⇒ پیش‌فرض) */
  private val SHIFT_TABLE = (Pay.table(null) as PayResult.Ok).value

  fun ledgerEvents(d: ShopData, open: AuditEntry, to: Long): List<ShiftEventInput> {
    val from = open.createdAt
    //  PAY-17 (`core/pay/Drawers`، همان قاعدهٔ `CashShift.totals`): کشوی اصلی ⇒ ردیف‌های بی مهرِ پنجره (بی «چند کشو» همهٔ
    //  ردیف‌ها، همان امروز)؛ کشوی نام‌دار ⇒ فقط فروش/رسید/برگشتِ مهرِ خودش و ورود/خروجِ صریحش؛ بقیه مالِ کشوی اصلی
    val drawers = TillDrawer.named(d)
    val own = open.id.takeIf { open.drawer != null }
    fun counts(stamp: String?, at: Long): Boolean =
      ir.vil3ntec.tohid.core.pay.Drawers.counts(stamp, at, open.id, isNamed = own != null, named = drawers, from = from, to = to)
    fun inWindow(at: Long) = own == null && at in from..to
    val ev = ArrayList<Pair<Int, ShiftEventInput>>()
    //  فاکتورِ اینترنتیِ سرور (W، #321) و مرجوعیِ سرورِ آن پولِ دخل نیستند — همان قاعدهٔ `CashShift`
    val online = d.sales.filter(CashShift::isOnline).mapTo(HashSet()) { it.id }
    val fixes = PayFixFlow.bySale(d)
    //  PAY-07: فقط پولِ رسیده — ردیفِ «در انتظار»/«ناموفق»/«لغو»/«نیازمند بررسی» (تا `to`) بیرون (`PayStatusFlow`)
    val statuses = PayStatusFlow.bySale(d)
    d.sales.forEach { s ->
      if (s.status == "cancelled" || s.id in online || !counts(s.shiftId, s.createdAt)) return@forEach
      val rows = PayStatusFlow.received(saleRows(s, fixes[s.id].orEmpty(), to), PayStatusFlow.unreceived(s, statuses[s.id].orEmpty(), to))
      if (rows.isNotEmpty()) ev += 0 to ShiftEventInput(id = "sale:${s.id}", type = "sale", at = s.createdAt, rows = rows)
      //  PAY-10: پولِ پسِ افغانیِ ارز از همین دخل رفت ⇒ خروجِ نقد درست پس از همان فروش (ارز «مورد انتظار» ِ افغانی نیست)
      FxTillFlow.changeOut(s).takeIf { it > 0 }?.let {
        ev += 0 to ShiftEventInput(id = "sale:${s.id}:fx", type = "cash_out", at = s.createdAt, amount = it, reason = "other", note = FX_CHANGE_NOTE)
      }
    }
    d.transactions.forEach { t ->
      //  رسیدِ ساختهٔ لغو (فقط بستنِ بدهی) پولِ دخل نیست — همان `CashShift.isCashReceipt`
      if (t.type != "receive" || !CashShift.isCashReceipt(t) || !counts(t.shiftId, t.createdAt) || minor(t.amount) <= 0) return@forEach
      ev += 0 to ShiftEventInput(id = "rcv:${t.id}", type = "receipt", at = t.createdAt, method = CASH, amount = minor(t.amount))
    }
    //  S89: رسیدِ نقدیِ معکوس‌شده در لحظهٔ ردیفِ معکوس از همین دخل بیرون می‌رود — همان `CashShift.totals` (هسته رسیدِ منفی ندارد ⇒ «پولِ پس»)
    DebtReverse.cashBack(d.transactions).forEach { t ->
      if (!counts(t.shiftId, t.createdAt) || minor(t.amount) <= 0) return@forEach
      ev += 2 to ShiftEventInput(id = "rvs:${t.id}", type = "refund", at = t.createdAt, rows = listOf(ShiftRow(CASH, minor(t.amount))))
    }
    //  PAY-24/PRM-14: فروش و شارژِ کارتِ هدیه نقد به دخل می‌آورند — همان `CashShift.totals`
    if (own == null) GiftCards.cashRows(d, from, to).forEach { a ->
      ev += 0 to ShiftEventInput(id = "gci:${a.id}", type = "receipt", at = a.createdAt, method = CASH, amount = minor(a.amount))
    }
    d.auditLog.forEach { a ->
      if (a.type !in AUDIT_TYPES || a.refId != open.id) return@forEach
      val by = a.by?.ifEmpty { null }
      if (a.type == "no_sale") {
        ev += 1 to ShiftEventInput(id = "a:${a.id}", type = "no_sale", at = a.createdAt, by = by, reason = a.reason, approvedBy = a.approvedBy)
        return@forEach
      }
      ev += (if (a.type == "cash_in") 0 else 1) to ShiftEventInput(
        id = "a:${a.id}", type = a.type, at = a.createdAt, by = by,
        amount = minor(a.amount), reason = a.reason, note = a.note?.ifEmpty { null },
      )
    }
    d.saleReturns.forEach { r ->
      if (!counts(r.shiftId, r.createdAt) || minor(r.amount) <= 0 || CashShift.isOnlineReturn(r, online)) return@forEach
      //  نقدِ دخل همان `CashShift` است ([ReturnFlow.drawerAmount]؛ سهمِ حساب و اعتبار را رسیدِ
      //  `customer_return` خنثی می‌کند). بازپرداخت به کیف پول/کارت/حواله (`toMethods`) ردیفِ همان
      //  روش است؛ روشی که جدولِ شیفت ندارد پولِ دخل نیست و ردیف نمی‌گیرد، تا کلِ رویداد رد نشود.
      val rows = buildList {
        val cash = minor(ReturnFlow.drawerAmount(r))
        if (cash > 0) add(ShiftRow(CASH, cash))
        r.toMethods?.forEach { (m, a) -> if (minor(a) > 0 && SHIFT_TABLE.kindOf(m) != null) add(ShiftRow(m, minor(a))) }
      }
      if (rows.isNotEmpty()) ev += 2 to ShiftEventInput(id = "ret:${r.id}", type = "refund", at = r.createdAt, rows = rows)
    }
    //  POS-26: بازپرداختِ نقدِ پیش‌پرداختِ سفارشِ ویژهٔ لغوشده — همان «پولِ پس» ِ `CashShift.totals`
    d.transactions.forEach { t ->
      if (!(SpecialOrders.isRefund(t) || Layaways.isRefund(t)) || !inWindow(t.createdAt) || minor(t.amount) <= 0) return@forEach
      //  POS-27: بازپرداختِ نقدِ امانیِ لغوشده هم همین «پولِ پس» است (`Layaways.REFUND`)
      ev += 2 to ShiftEventInput(id = (if (Layaways.isRefund(t)) "lyr:" else "sor:") + t.id, type = "refund", at = t.createdAt, rows = listOf(ShiftRow(CASH, minor(t.amount))))
    }
    fun out(id: String, at: Long, amount: Double, reason: String, note: String?) {
      if (!inWindow(at) || minor(amount) <= 0) return
      ev += 3 to ShiftEventInput(id = id, type = "cash_out", at = at, amount = minor(amount), reason = reason, note = note)
    }
    d.expenses.forEach { out("exp:${it.id}", it.createdAt, it.amount, "petty_expense", null) }
    d.supplierPayments.forEach { out("sup:${it.id}", it.createdAt, it.amount, "other", "پرداخت به تأمین‌کننده") }  // داده: دلیلِ رویدادِ شیفت درونِ گزارشِ Z ِ ذخیره‌شده
    d.purchases.forEach { out("pur:${it.id}", it.createdAt, it.paidAmount, "other", "پیش‌پرداختِ خرید") }  // داده: دلیلِ رویدادِ شیفت درونِ گزارشِ Z ِ ذخیره‌شده
    val sorted = ev.sortedWith(compareBy<Pair<Int, ShiftEventInput>>({ it.first }, { it.second.at }, { it.second.id })).map { it.second }
    //  PAY-17: کشوی نام‌دار با مهر شمرده می‌شود، نه با ساعت — ردیفِ دستگاهِ دیگری با ساعتِ عقب‌تر (شناور) پیش از بازشدن نمی‌افتد
    return if (own == null) sorted else sorted.map { if (it.at < from) it.copy(at = from) else it }
  }

  data class Skipped(val id: String, val type: String, val code: String, val amount: Long)
  data class Replay(val shift: Shift, val skipped: List<Skipped>)

  /** شیفتِ هسته از روی دفتر؛ رویدادی که هسته نپذیرد در `skipped` می‌آید و صفحه هشدار می‌دهد */
  fun replay(d: ShopData, open: AuditEntry, now: Long, threshold: Long = Pay.DEFAULT_VARIANCE_THRESHOLD): Outcome<Replay> {
    var shift = when (
      val o = ShiftCore.open(OpenShiftInput(id = open.id, at = open.createdAt, float = minor(open.amount), by = open.by, threshold = threshold))
    ) {
      is PayResult.Ok -> o.value
      is PayResult.Err -> return Outcome.Bad(Problem(o.code))
    }
    val skipped = ArrayList<Skipped>()
    ledgerEvents(d, open, now).forEach { e ->
      when (val r = ShiftCore.record(shift, e)) {
        is PayResult.Ok -> shift = r.value
        is PayResult.Err -> skipped += Skipped(e.id.orEmpty(), e.type, r.code, e.amount ?: 0)
      }
    }
    return Outcome.Ok(Replay(shift, skipped))
  }

  data class Report(val report: ShiftReport, val stored: Boolean, val skipped: List<Skipped> = emptyList(), val shift: Shift? = null)

  /** گزارشِ X (یا Zِ ذخیره‌شدهٔ صندوقِ بسته) */
  fun report(d: ShopData, open: AuditEntry, close: AuditEntry?, now: Long, by: String? = null): Outcome<Report> {
    if (close != null) {
      val z = close.z?.let { reportOf(it) } ?: return Outcome.Bad(Problem("LEGACY_CLOSE"))
      return Outcome.Ok(Report(z, stored = true))
    }
    val r = when (val x = replay(d, open, now)) { is Outcome.Ok -> x.value; is Outcome.Bad -> return x }
    return when (val x = ShiftCore.xReport(r.shift, maxOf(now, open.createdAt), by?.trim()?.ifEmpty { null })) {
      is PayResult.Ok -> Outcome.Ok(Report(x.value, stored = false, skipped = r.skipped, shift = r.shift))
      is PayResult.Err -> Outcome.Bad(Problem(x.code))
    }
  }

  /** نقدِ مورد انتظار (افغانی) برای سرخطِ صندوق؛ NaN اگر حساب نشد */
  fun expectedCash(d: ShopData, open: AuditEntry, now: Long): Double = when (val r = report(d, open, null, now)) {
    is Outcome.Ok -> major(r.value.report.expected?.get(CASH) ?: 0)
    is Outcome.Bad -> Double.NaN
  }

  /** ورود/خروجِ نقد یا کشو بی فروش — `type` = cash_in | cash_out | no_sale؛ `amount` متنِ کادر */
  data class Move(
    val type: String,
    val amount: String = "",
    val reason: String? = null,
    val note: String = "",
    val by: String = "",
    val approvedBy: String = "",
  )

  /** هسته می‌سنجد؛ ردیفِ سابقه (بی `notes` و `date` — آن‌ها را صفحه می‌گذارد) برمی‌گردد */
  fun moveRow(d: ShopData, open: AuditEntry, m: Move, now: Long, newId: () -> String): Outcome<AuditEntry> {
    val r = when (val x = replay(d, open, now)) { is Outcome.Ok -> x.value; is Outcome.Bad -> return x }
    val id = newId()
    val by = m.by.trim().ifEmpty { null }
    val e: ShiftEventInput
    val row: AuditEntry
    if (m.type == "no_sale") {
      val reason = m.reason?.trim().orEmpty()
      val approver = m.approvedBy.trim()
      e = ShiftEventInput(id = "a:$id", type = "no_sale", at = now, by = by, reason = reason, approvedBy = approver)
      row = AuditEntry(id = id, type = "no_sale", refId = open.id, createdAt = now, by = by, reason = reason, approvedBy = approver)
    } else {
      val a = amountOf(m.amount)
      if (a == null || a.isNaN() || minor(a) <= 0) return Outcome.Bad(Problem("INVALID_AMOUNT"))
      val note = m.note.trim().ifEmpty { null }
      e = ShiftEventInput(id = "a:$id", type = m.type, at = now, by = by, amount = minor(a), reason = m.reason, note = note)
      row = AuditEntry(id = id, type = m.type, refId = open.id, createdAt = now, amount = major(minor(a)), by = by, reason = m.reason, note = note)
    }
    return when (val x = ShiftCore.record(r.shift, e)) {
      is PayResult.Ok -> Outcome.Ok(row)
      is PayResult.Err -> Outcome.Bad(Problem(x.code))
    }
  }

  /** شمارشِ بستن: اسکناس (پول، متنِ دهدهی) ⇒ تعداد */
  data class Count(
    val counts: Map<String, Long>,
    val methodCounts: Map<String, Long> = emptyMap(),
    val by: String = "",
    val blind: Boolean = false,
    val reason: String = "",
  )

  data class Closed(val row: AuditEntry, val report: ShiftReport, val skipped: List<Skipped>)

  /**
   *  PAY-13، 14 — بستن با شمارشِ اسکناس؛ اختلافِ بالای آستانه بی دلیل بسته نمی‌شود؛ اختلاف اصلاح نمی‌شود.
   *  RPT-04: Z شمارهٔ بعدیِ سریِ همین دستگاه را می‌گیرد ([series] = `DocSeries.current`؛ `ZSeq.next` روی Z های دفتر).
   */
  fun closeRow(d: ShopData, open: AuditEntry, c: Count, now: Long, newId: () -> String, series: String = ""): Outcome<Closed> {
    val r = when (val x = replay(d, open, now)) { is Outcome.Ok -> x.value; is Outcome.Bad -> return x }
    val id = newId()
    val by = c.by.trim().ifEmpty { null }
    val res = ShiftCore.close(
      r.shift,
      CloseShiftInput(id = id, at = now, counts = c.counts, methodCounts = c.methodCounts, by = by, blind = c.blind, reason = c.reason.trim().ifEmpty { null }),
    )
    val z = when (res) {
      is PayResult.Ok -> res.value.z ?: return Outcome.Bad(Problem("SHIFT_CLOSED"))
      is PayResult.Err -> return Outcome.Bad(Problem(res.code, variance = res.variance))
    }.copy(no = ZSeq.next(zs(d), series), series = series.trim())
    val row = AuditEntry(
      id = id, type = CashShift.CLOSE, refId = open.id, createdAt = now,
      amount = major(z.counted?.get(CASH) ?: 0), by = by, reason = z.reason, z = reportJson(z),
    )
    return Outcome.Ok(Closed(row, z, r.skipped))
  }

  /** RPT-04 — Z های ذخیره‌شدهٔ دفتر (هر دستگاه) با سری و شماره؛ Z ِ کهنهٔ بی شماره ⇒ ۰ */
  fun zs(d: ShopData): List<ZSeq.Z> = d.auditLog.mapNotNull { a ->
    val z = a.z?.takeIf { a.type == CashShift.CLOSE } ?: return@mapNotNull null
    ZSeq.Z(a.id, (z["series"] as? JsonPrimitive)?.contentOrNull.orEmpty(), (z["no"] as? JsonPrimitive)?.longOrNull ?: 0L)
  }

  /** شمارِ اسکناس ⇒ جمع (پول) — برای جمعِ زندهٔ فرمِ شمارش */
  fun countTotal(counts: Map<String, Long>): Long = counts.entries.sumOf { (k, n) -> (k.toLongOrNull() ?: 0L) * n.coerceAtLeast(0) }

  // ── Zِ ذخیره‌شده: همان شکلِ JSِ گزارشِ هسته ───────────────
  private fun longs(m: Map<String, Long>) = buildJsonObject { m.forEach { (k, v) -> put(k, v) } }
  private fun str(v: String?): JsonElement = v?.let { JsonPrimitive(it) } ?: JsonNull

  fun reportJson(r: ShiftReport): JsonObject = buildJsonObject {
    put("kind", r.kind); put("shiftId", r.shiftId); put("currency", r.currency); put("at", r.at)
    put("by", str(r.by)); put("openedAt", r.openedAt); put("openedBy", str(r.openedBy)); put("float", r.float)
    put("methods", buildJsonObject {
      r.methods.forEach { (k, m) -> put(k, buildJsonObject { put("sales", m.sales); put("refunds", m.refunds); put("receipts", m.receipts); put("net", m.net) }) }
    })
    put("moves", buildJsonObject { put("in", r.moves.cashIn); put("out", r.moves.cashOut) })
    r.expected?.let { put("expected", longs(it)) }
    put("events", buildJsonObject {
      put("sales", r.events.sales); put("refunds", r.events.refunds); put("receipts", r.events.receipts)
      put("cashIn", r.events.cashIn); put("cashOut", r.events.cashOut); put("noSale", r.events.noSale)
    })
    if (r.kind == "Z") {
      r.counted?.let { put("counted", longs(it)) }
      r.denominations?.let { put("denominations", longs(it)) }
      r.variance?.let { v -> put("variance", buildJsonObject { v.forEach { (k, x) -> put(k, x?.let { JsonPrimitive(it) } ?: JsonNull) } }) }
      put("reason", str(r.reason))
      r.needsReview?.let { put("needsReview", it) }
      r.blind?.let { put("blind", it) }
      r.no?.let { put("no", it) }
      r.series?.let { put("series", it) }
    }
  }

  /**
   *  ⛔ PAY-13 — متنِ خطِ سابقهٔ یک بستن برای همین بیننده. شاگرد (بی حقِ دیدنِ «مورد انتظار») هرگز `notes` را
   *  نمی‌بیند: متنِ بستنِ مدیر و بستن‌های پیش از نسخهٔ ۳ «مورد انتظار» و «اختلاف» را دارند، و دلیلِ اختلاف هم
   *  یعنی «شمارش نخواند». برای او فقط شمرده‌ها از Zِ ذخیره‌شده از راهِ `ShiftSlip.forViewer` (بی Z همان `amount` ِ
   *  ردیف = نقدِ شمرده)، کور بودن و نامِ بندنده. صاحب و مدیر همان `notes`.
   */
  fun closeNote(row: AuditEntry, seesExpected: Boolean): String {
    if (seesExpected) return row.notes
    val z = row.z?.let(::reportOf)?.let { ShiftSlip.forViewer(it, seesExpected = false) }
    val counted = z?.counted?.takeIf { it.isNotEmpty() }?.entries
      ?.joinToString("، ") { (id, v) -> "${methodName(id)} ${ir.vil3ntec.tohid.money(major(v))}" }
      ?: "${methodName(CASH)} ${ir.vil3ntec.tohid.money(row.amount)}"
    val who = row.by?.trim()?.takeIf { it.isNotEmpty() }?.let { " — $it" }.orEmpty()
    return if (z?.blind == true) StrLedger.tillClosedBlind(counted, who) else StrLedger.tillClosed(counted, who)
  }

  /** Zِ ذخیره‌شده (از هر دستگاه) ⇒ گزارش؛ `null` اگر خوانا نیست */
  fun reportOf(j: JsonObject): ShiftReport? = runCatching {
    fun JsonObject.l(k: String) = (this[k] as? JsonPrimitive)?.longOrNull ?: 0L
    fun JsonObject.s(k: String) = (this[k] as? JsonPrimitive)?.takeIf { it !is JsonNull }?.contentOrNull
    fun JsonObject.map(k: String): Map<String, Long>? =
      (this[k] as? JsonObject)?.entries?.associate { (key, v) -> key to (v.jsonPrimitive.longOrNull ?: 0L) }
    val ev = j["events"]?.jsonObject ?: JsonObject(emptyMap())
    ShiftReport(
      kind = j.s("kind") ?: "Z", shiftId = j.s("shiftId").orEmpty(), currency = j.s("currency") ?: "AFN",
      at = j.l("at"), by = j.s("by"), openedAt = j.l("openedAt"), openedBy = j.s("openedBy"), float = j.l("float"),
      methods = (j["methods"] as? JsonObject)?.entries?.associate { (k, v) ->
        val o = v.jsonObject
        k to MethodTotals(o.l("sales"), o.l("refunds"), o.l("receipts"), o.l("net"))
      } ?: emptyMap(),
      moves = (j["moves"] as? JsonObject).let { Moves(it?.l("in") ?: 0, it?.l("out") ?: 0) },
      expected = j.map("expected"),
      events = EventCounts(
        ev.l("sales").toInt(), ev.l("refunds").toInt(), ev.l("receipts").toInt(),
        ev.l("cashIn").toInt(), ev.l("cashOut").toInt(), ev.l("noSale").toInt(),
      ),
      counted = j.map("counted"),
      denominations = j.map("denominations"),
      variance = (j["variance"] as? JsonObject)?.entries?.associate { (k, v) -> k to (v as? JsonPrimitive)?.longOrNull },
      reason = j.s("reason"),
      needsReview = (j["needsReview"] as? JsonPrimitive)?.booleanOrNull,
      blind = (j["blind"] as? JsonPrimitive)?.booleanOrNull,
      no = (j["no"] as? JsonPrimitive)?.longOrNull,
      series = j.s("series"),
    )
  }.getOrNull()
}
