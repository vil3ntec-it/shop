package ir.vil3ntec.tohid.data

import ir.vil3ntec.tohid.core.i18n.StrGiftCard
import ir.vil3ntec.tohid.core.pay.GiftCard
import ir.vil3ntec.tohid.core.pay.Settlement
import ir.vil3ntec.tohid.core.pay.StoreCredit
import ir.vil3ntec.tohid.core.pay.StoredPay

/**
 *  PAY-24 گام ۲ — کارتِ هدیه و اعتبارِ فروشگاه روی دفترِ دکان. خالص: نه فایل، نه ساعت، نه Android. قاعده‌ها در هستهٔ
 *  `core/pay/{GiftCard,StoredPay}`؛ این‌جا فقط دفتر ⇄ رویداد.
 *
 *  ── بی جدولِ تازه و بی مهاجرت (همان الگوی سفارشِ ویژه، POS-26) ──────────
 *    • کارت: ردیف‌های سابقهٔ [ISSUE]/[REDEEM]/[REFUND]/[VOID]، هر کدام یک رویدادِ `GiftCard.Event`: شناسهٔ ردیف =
 *      شناسهٔ رویداد، `payRef` = کدِ کارت، `refId` = فاکتور (خرج) یا سندِ برگشت/لغو، `amount` = مبلغ، `by`، `reason`.
 *      مانده از رویدادها ([view])؛ هیچ ردیفی ویرایش نمی‌شود.
 *    • اعتبار: همان طلبِ مشتری در دفترِ قرض‌دار؛ خرج «برد» با `kind` = [CREDIT] (`refId` = فاکتور)، برگشت به اعتبار
 *      «رسید» با همان `kind` (`refId` = سندِ برگشت یا فاکتورِ لغوشده). هیچ‌کدام نقدِ دخل نیست (`CashShift.isCashReceipt`).
 *    • فروشِ کارت ([issue]) نقد به دخل می‌آورد: `CashShift.totals` و `PayFlow.ledgerEvents` آن را رسیدِ نقد می‌شمارند.
 *
 *  ── هم‌اتم و بی خرجِ دوم ─────────────────────────────────────────────
 *  ردیف‌های خرج داخلِ همان `SalesEngine.record` ِ فاکتور ساخته و با همان دفتر نوشته می‌شوند (همه یا هیچ)؛ شناسه‌شان از
 *  شناسهٔ فاکتور (کلیدِ پیش‌نویس، POS-14) است، پس تلاشِ دوباره هرگز خرجِ دوم نمی‌سازد. سنجش روی دفترِ همان لحظه:
 *  یک دستگاه هرگز کارت را منفی نمی‌کند؛ دو دستگاهِ بی‌خبر ⇒ ماندهٔ منفی و «نیازمندِ بررسی» (`GiftCard.view`).
 *  بی ردیفِ کارت/اعتبار در تسویه هیچ چیز این‌جا ساخته نمی‌شود — صندوق و دفتر همان امروز.
 */
object GiftCards {

  const val ISSUE = "gift_card_issue"
  const val REDEEM = "gift_card_redeem"
  const val REFUND = "gift_card_refund"
  const val VOID = "gift_card_void"

  /** `DebtTransaction.kind` ِ خرج (برد) و برگشت (رسید) ِ اعتبارِ فروشگاه — همان `CashShift.STORE_CREDIT_RECEIPT` */
  const val CREDIT = CashShift.STORE_CREDIT_RECEIPT

  /** کدهای ردِ همین‌جا (کنارِ کدهای `GiftCard.Code` و `StoreCredit`) */
  const val NO_CARD_ROWS = "NO_CARD_ROWS"

  private val TYPE = mapOf(GiftCard.ISSUE to ISSUE, GiftCard.REDEEM to REDEEM, GiftCard.REFUND to REFUND, GiftCard.VOID to VOID)
  private val KIND = TYPE.entries.associate { (k, v) -> v to k }

  fun isRow(a: AuditEntry): Boolean = a.type in KIND

  fun eventOf(a: AuditEntry): GiftCard.Event? {
    val kind = KIND[a.type] ?: return null
    return GiftCard.Event(
      id = a.id, kind = kind, code = a.payRef.orEmpty(), amountMinor = if (kind == GiftCard.VOID) 0L else PayFlow.minor(a.amount),
      at = a.createdAt, by = a.by, refId = a.refId, reason = a.reason,
    )
  }

  fun events(d: ShopData): List<GiftCard.Event> = d.auditLog.mapNotNull(::eventOf)

  fun view(d: ShopData): GiftCard.View = GiftCard.view(events(d))

  /** «ABCD-…-JKLM» ⇒ «••••JKLM» — کد خودش پول است؛ روی رسید و سابقه فقط چهار نویسهٔ آخر */
  fun mask(code: String?): String = "••••" + code.orEmpty().takeLast(4)

  private fun entry(e: GiftCard.Event, today: String, notes: String) = AuditEntry(
    id = e.id, type = TYPE.getValue(e.kind), date = today, refId = e.refId, notes = notes, createdAt = e.at,
    amount = PayFlow.major(e.amountMinor), by = e.by, reason = e.reason, payRef = e.code,
  )

  private fun fmt(minor: Long): String = ir.vil3ntec.tohid.money(PayFlow.major(minor))

  // ── اعتبارِ فروشگاه ─────────────────────────────────────────────
  /** ماندهٔ دفترِ قرض‌دار به پول (مثبت = بدهکار، منفی = طلب) — همان حسابِ `DebtBook.creditCheck` */
  fun balanceMinor(d: ShopData, debtorId: String): Long =
    d.transactions.filter { it.debtorId == debtorId }.sumOf { if (it.type == "give") PayFlow.minor(it.amount) else -PayFlow.minor(it.amount) }

  /** طلبِ خرج‌شدنیِ مشتری (۰ اگر طلبی ندارد) */
  fun creditOf(d: ShopData, debtorId: String?): Long = debtorId?.let { StoreCredit.available(balanceMinor(d, it)) } ?: 0L

  // ── فاکتور (`SalesEngine.record`) ─────────────────────────────────
  data class Rows(val audit: List<AuditEntry> = emptyList(), val transactions: List<DebtTransaction> = emptyList()) {
    val empty: Boolean get() = audit.isEmpty() && transactions.isEmpty()
  }

  sealed interface Out {
    data class Ok(val rows: Rows) : Out
    data class Bad(val code: String) : Out {
      val message: String get() = message(code)
    }
  }

  /**
   *  ردیف‌های خرجِ کارت و اعتبارِ فاکتورِ [saleId] روی دفترِ [d] (همین لحظه). تسویهٔ بی کارت/اعتبار ⇒ ردیفِ تهی.
   *  کارت: `StoredPay.redeem` (فقط تا مانده؛ تکرار بی خرجِ دوم). اعتبار: فقط با مشتریِ فاکتور و تا طلبش.
   */
  fun forSale(d: ShopData, st: Settlement, saleId: String, debtorId: String?, label: String, today: String, now: Long, by: String?): Out {
    if (!StoredPay.has(st)) return Out.Ok(Rows())
    val audit = ArrayList<AuditEntry>()
    if (st.payments.any { it.kind == StoredPay.GIFT }) {
      StoredPay.gifts(st) ?: return Out.Bad(GiftCard.Code.BAD_CODE)
      when (val r = StoredPay.redeem(events(d), st, saleId, now, by)) {
        is GiftCard.Result.Err -> return Out.Bad(r.code)
        is GiftCard.Result.Ok -> r.value.forEach { e ->
          audit += entry(e, today, "خرج از کارتِ هدیهٔ ${mask(e.code)} — فاکتور #$label — ${fmt(e.amountMinor)} افغانی")
        }
      }
    }
    val tx = ArrayList<DebtTransaction>()
    val credit = StoredPay.creditMinor(st)
    if (credit > 0) {
      StoredPay.creditProblem(st, debtorId != null, debtorId?.let { balanceMinor(d, it) } ?: 0L)?.let { return Out.Bad(it) }
      tx += DebtTransaction(
        id = "$saleId-sc", debtorId = debtorId!!, type = "give", amount = PayFlow.major(credit), date = today,
        notes = "پرداخت با اعتبارِ فروشگاه — فاکتور #$label", createdAt = now, refId = saleId, kind = CREDIT,
      )
    }
    return Out.Ok(Rows(audit, tx))
  }

  /** پیش‌سنجشِ پنلِ پرداخت (همان قاعدهٔ [forSale]، بی نوشتن) — null یعنی پذیرفتنی */
  fun problem(d: ShopData, st: Settlement, debtorId: String?): String? =
    (forSale(d, st, "preview", debtorId, "", "", 1, null) as? Out.Bad)?.code

  // ── برگشت و لغو ────────────────────────────────────────────────
  private fun spends(d: ShopData, saleId: String): List<StoredPay.Spend> =
    events(d).filter { it.kind == GiftCard.REDEEM && it.refId == saleId }.map { StoredPay.Spend(it.code, it.amountMinor) }

  /** آنچه پیش‌تر به کارت‌ها/اعتبارِ این فاکتور برگشت (سندهای برگشتِ همان فاکتور) */
  private fun docsOf(d: ShopData, saleId: String): Set<String> = d.saleReturns.filter { it.saleId == saleId }.mapTo(HashSet()) { it.docId ?: it.id }

  private fun refundRows(
    d: ShopData, saleId: String, refId: String, idBase: String, giftMinor: Long, creditMinor: Long, creditTo: String?,
    notes: String, today: String, now: Long, by: String?, prior: Set<String>,
  ): Out {
    val audit = ArrayList<AuditEntry>()
    if (giftMinor > 0) {
      val all = events(d)
      val back = HashMap<String, Long>()
      all.filter { it.kind == GiftCard.REFUND && it.refId in prior }.forEach { back[it.code] = (back[it.code] ?: 0L) + it.amountMinor }
      val parts = StoredPay.refundSplit(spends(d, saleId), back, giftMinor) ?: return Out.Bad(NO_CARD_ROWS)
      val work = ArrayList(all)
      parts.forEachIndexed { n, p ->
        val input = GiftCard.Input("$idBase-g$n", GiftCard.REFUND, p.code, p.amountMinor, now, by = by, refId = refId)
        when (val r = GiftCard.build(work, input)) {
          is GiftCard.Result.Err -> return Out.Bad(r.code)
          is GiftCard.Result.Ok -> if (!r.duplicate) {
            work += r.value
            audit += entry(r.value, today, "برگشت به کارتِ هدیهٔ ${mask(p.code)} — $notes — ${fmt(p.amountMinor)} افغانی")
          }
        }
      }
    }
    val tx = ArrayList<DebtTransaction>()
    if (creditMinor > 0) {
      val to = creditTo ?: return Out.Bad(StoreCredit.NO_CUSTOMER)
      tx += DebtTransaction(
        id = "$idBase-sc", debtorId = to, type = "receive", amount = PayFlow.major(creditMinor), date = today,
        notes = "برگشت به اعتبارِ فروشگاه — $notes", createdAt = now, refId = refId, kind = CREDIT,
      )
    }
    return Out.Ok(Rows(audit, tx))
  }

  /** `ReturnFlow.post`: سهمِ کارت و اعتبارِ همین برگشت (`StoredPay.route`) به همان کارت‌ها و اعتبارِ مشتریِ فاکتور */
  fun forReturn(d: ShopData, sale: Sale, docId: String, giftMinor: Long, creditMinor: Long, notes: String, today: String, now: Long, by: String?): Out {
    if (giftMinor <= 0 && creditMinor <= 0) return Out.Ok(Rows())
    return refundRows(d, sale.id, docId, docId, giftMinor, creditMinor, sale.debtorId, notes, today, now, by, docsOf(d, sale.id))
  }

  /**
   *  `SalesEngine.cancel`: هرچه از کارت و اعتبار خرجِ این فاکتور شد و هنوز با برگشت پس نرفته، به همان‌ها برمی‌گردد
   *  (`refId` = فاکتور). فاکتورِ بی کارت/اعتبار ⇒ ردیفِ تهی (لغوِ امروز).
   */
  fun forCancel(d: ShopData, sale: Sale, notes: String, today: String, now: Long, by: String?): Out {
    val paid = spends(d, sale.id)
    val spent = d.transactions.filter { it.refId == sale.id && it.kind == CREDIT && it.type == "give" }.sumOf { PayFlow.minor(it.amount) }
    if (paid.isEmpty() && spent == 0L) return Out.Ok(Rows())
    val docs = docsOf(d, sale.id)
    val giftBack = events(d).filter { it.kind == GiftCard.REFUND && it.refId in docs }.sumOf { it.amountMinor }
    val creditBack = d.transactions.filter { it.refId in docs && it.kind == CREDIT && it.type == "receive" }.sumOf { PayFlow.minor(it.amount) }
    val gift = (paid.sumOf { it.amountMinor } - giftBack).coerceAtLeast(0)
    val credit = (spent - creditBack).coerceAtLeast(0)
    return refundRows(d, sale.id, sale.id, "${sale.id}-x", gift, credit, sale.debtorId, notes, today, now, by, docs)
  }

  // ── فروشِ کارت ────────────────────────────────────────────────
  sealed interface Issued {
    data class Ok(val data: ShopData, val event: GiftCard.Event) : Issued
    data class Bad(val code: String) : Issued
  }

  fun hasRow(d: ShopData, key: String): Boolean = d.auditLog.any { it.id == key }

  /** کارتِ تازه با مبلغِ اول، با شناسهٔ [key] (پنجره هنگامِ باز شدن می‌سازد)؛ نقدش به دخل می‌آید */
  fun issue(cur: ShopData, key: String, code: String?, amountMinor: Long, today: String, now: Long, by: String?): Issued =
    when (val r = GiftCard.build(events(cur), GiftCard.Input(key, GiftCard.ISSUE, code, amountMinor, now, by = by))) {
      is GiftCard.Result.Err -> Issued.Bad(r.code)
      is GiftCard.Result.Ok -> if (r.duplicate) Issued.Bad(GiftCard.Code.ID_CONFLICT) else Issued.Ok(
        cur.copy(auditLog = cur.auditLog + entry(r.value, today, "فروشِ کارتِ هدیهٔ ${mask(r.value.code)} — ${fmt(r.value.amountMinor)} افغانی (نقد)")),
        r.value,
      )
    }

  /** نقدِ فروشِ کارت در پنجرهٔ صندوق (افغانی) — `CashShift.totals` */
  fun issueCash(d: ShopData, from: Long, to: Long): Double = issueRows(d, from, to).sumOf { it.amount }

  /** ردیف‌های فروشِ کارت در پنجره — `PayFlow.ledgerEvents` (رسیدِ نقد) */
  fun issueRows(d: ShopData, from: Long, to: Long): List<AuditEntry> =
    d.auditLog.filter { it.type == ISSUE && it.createdAt in from..to && PayFlow.minor(it.amount) > 0 }

  fun message(code: String): String = when (code) {
    GiftCard.Code.BAD_CODE -> StrGiftCard.errCode
    GiftCard.Code.UNKNOWN_CARD -> StrGiftCard.errUnknown
    GiftCard.Code.CARD_VOID -> StrGiftCard.errVoid
    GiftCard.Code.INSUFFICIENT_BALANCE -> StrGiftCard.errBalance
    GiftCard.Code.CODE_TAKEN -> StrGiftCard.errTaken
    GiftCard.Code.INVALID_AMOUNT -> StrGiftCard.errAmount
    GiftCard.Code.ID_CONFLICT -> StrGiftCard.errConflict
    StoreCredit.NO_CUSTOMER -> StrGiftCard.errNoCustomer
    StoreCredit.INSUFFICIENT_CREDIT -> StrGiftCard.errCredit
    NO_CARD_ROWS -> StrGiftCard.errRefundOver
    else -> StrGiftCard.errNotSaved
  }
}
