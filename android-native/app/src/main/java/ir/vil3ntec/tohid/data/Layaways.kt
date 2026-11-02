package ir.vil3ntec.tohid.data

import ir.vil3ntec.tohid.core.pos.Layaway
import ir.vil3ntec.tohid.core.pos.SpecialOrder

/**
 *  POS-27 گام ۲ — فروشِ امانی (کالا تا تسویه نزدِ دکان) با پیش‌پرداخت و جدولِ قسط، روی دفترِ دکان. خالص: نه فایل، نه
 *  ساعت، نه Android؛ ثبت فقط با `LayawayPref.record` (دفترِ همین لحظهٔ دیسک، کلیدِ یکتا). قاعده‌ها در هستهٔ
 *  `core/pos/Layaway`؛ الگو همان `SpecialOrders` (POS-26).
 *
 *  ── بی جدولِ تازه ───────────────────────────────────────────────────
 *    • سند: ردیفِ سابقهٔ [TYPE] (شناسه = شناسهٔ سند) با `AuditEntry.layaway`: مشتری، ردیف‌های سبد با قیمتِ همان لحظه،
 *      جمع، پیش‌پرداخت و جدولِ قسط (`Layaway.schedule`، جمعِ قسط‌ها = جمع − پیش‌پرداخت).
 *    • پیش‌پرداخت: رسیدِ قرض‌دار (`receive`، `kind` = [DEPOSIT]، شناسه `<سند>-p0`).
 *    • هر قسط: رسیدِ قرض‌دار (`receive`، `kind` = [INSTALLMENT]، `refId` = سند، شناسه `<سند>-p<n>` — n شمارهٔ پرداخت
 *      از ۱، [payKey]) ⇒ یک ثبتِ اتمیک زیرِ قفلِ دفتر؛ تکرارِ همان کلید و همان مبلغ چیزی نمی‌نویسد، همان کلید با مبلغِ
 *      دیگر رد می‌شود (STALE). مبلغِ بیش از مانده هرگز گرفته نمی‌شود (`Layaway.payProblem` ⇒ OVER_REMAINING).
 *      رسیدها مثلِ هر رسید نقدِ دخل‌اند (`CashShift.isCashReceipt`) و تا تحویل پیش‌پرداختِ حسابِ مشتری.
 *    • تحویل (فقط پس از تسویهٔ کامل، یک بار): فروشِ عادیِ صندوق (`SalesEngine.record`، شناسهٔ فاکتور `<سند>-d<k>`،
 *      [deliverKey]) با همان ردیف‌ها و قیمت‌ها، همه «به حساب» (`sale_credit`) که پرداختی‌ها را می‌بندد ⇒ موجودی همین
 *      لحظه کم می‌شود، نه پیش‌تر. فاکتورِ تحویلی که بعد لغو شود رویدادِ تحویل را بی‌اثر می‌کند (سند دوباره باز).
 *    • لغو (فقط سندِ باز، با دلیل): بردِ [REFUND] (`give`، شناسه `<سند>-r`) دقیقاً به اندازهٔ همهٔ پرداختی
 *      (`Layaway.cancelRefund`) و به همان روش — همهٔ رسیدهای امانی نقدند، پس بازپرداخت هم نقد از دخل
 *      (`CashShift.totals` و `PayFlow.ledgerEvents` آن را پولِ پس‌داده می‌شمارند).
 *  مشتریِ ادغام‌شده (CRM-10) دنبال می‌شود: پول همیشه به حسابِ مشتریِ ماندگار می‌رود (`SpecialOrders.owner`).
 */
object Layaways {

  const val TYPE = "layaway"
  const val MOVE = "layaway_status"
  /** `DebtTransaction.kind` ِ رسیدِ پیش‌پرداخت (نقدِ دخل) */
  const val DEPOSIT = "layaway_deposit"
  /** `DebtTransaction.kind` ِ رسیدِ هر قسط (نقدِ دخل) */
  const val INSTALLMENT = "layaway_installment"
  /** `DebtTransaction.kind` ِ بردِ بازپرداختِ همهٔ پرداختی هنگامِ لغو (خروجِ نقد از دخل) */
  const val REFUND = "layaway_refund"
  const val DELIVERED = "delivered"
  const val CANCELLED = "cancelled"
  /** سقفِ ردیف‌های سبد — سند در یک op ِ همگام‌سازی (۶۴KB) جا می‌شود */
  const val MAX_LINES = 60

  enum class Status { OPEN, DELIVERED, CANCELLED }

  data class View(
    val id: String,
    val createdAt: Long,
    val day: String,
    val debtorId: String,
    val customer: String,
    val lines: List<LayawayLine>,
    val totalMinor: Long,
    val depositMinor: Long,
    val schedule: List<Layaway.Installment>,
    val depositsMinor: Long,
    val paymentsMinor: Long,
    val refundsMinor: Long,
    /** شمارِ رسیدهای قسط (بی پیش‌پرداخت) */
    val payCount: Int,
    /** شمارِ رویدادهای تحویل، بی‌اثرشده‌ها هم */
    val deliveries: Int,
    val progress: Layaway.Progress,
    val status: Status,
    /** فاکتورِ تحویل (فقط تحویل‌شده) */
    val saleId: String?,
    /** سندِ ناسازگار (جدول یا ردیف‌ها با جمع نمی‌خوانند) ⇒ نه قسط، نه تحویل */
    val broken: Boolean,
    /** چیزی برای بازبینیِ مالک: ناسازگاری، پیش‌پرداختِ دوباره، بیش‌پرداختِ دو دستگاه، یا لغو و تحویلِ هم‌زمان */
    val review: Boolean,
    val by: String?,
  ) {
    /** همهٔ پرداختیِ مشتری برای همین سند (پیش‌پرداخت + قسط‌ها) */
    val collectedMinor: Long get() = depositsMinor + paymentsMinor
    /** پرداختی که هنوز نزدِ دکان است */
    val heldMinor: Long get() = collectedMinor - refundsMinor
    val active: Boolean get() = status == Status.OPEN
    val late: Boolean get() = active && progress.lateMinor > 0
    /** تسویهٔ کامل: همهٔ قسط‌ها و پیش‌پرداخت رسیده‌اند */
    val paidUp: Boolean get() = progress.settled && heldMinor >= totalMinor
  }

  /** ردیفِ بازپرداختِ لغو — پولی که از دخل به مشتری پس رفت (`CashShift`، `PayFlow`) */
  fun isRefund(t: DebtTransaction): Boolean = t.type == "give" && t.kind == REFUND

  fun isDoc(a: AuditEntry): Boolean = a.type == TYPE && a.layaway?.debtorId != null

  /** دفتر ⇐ ردیف‌های هر سند، یک بار برای همهٔ فهرست */
  private class Index(d: ShopData, only: Set<String>?) {
    val deposits = HashMap<String, Long>()
    val payments = HashMap<String, Long>()
    val payCount = HashMap<String, Int>()
    val refunds = HashMap<String, Long>()
    val moves = HashMap<String, MutableList<AuditEntry>>()
    val cancelledSales: Set<String> = d.sales.filter { it.status == "cancelled" }.mapTo(HashSet()) { it.id }

    init {
      for (t in d.transactions) {
        val ref = t.refId ?: continue
        if (only != null && ref !in only) continue
        val m = PayFlow.minor(t.amount)
        when {
          t.type == "receive" && t.kind == DEPOSIT -> deposits[ref] = (deposits[ref] ?: 0L) + m
          t.type == "receive" && t.kind == INSTALLMENT -> {
            payments[ref] = (payments[ref] ?: 0L) + m
            payCount[ref] = (payCount[ref] ?: 0) + 1
          }
          isRefund(t) -> refunds[ref] = (refunds[ref] ?: 0L) + m
        }
      }
      for (a in d.auditLog) {
        if (a.type != MOVE) continue
        val ref = a.refId ?: continue
        if (only != null && ref !in only) continue
        moves.getOrPut(ref) { ArrayList() } += a
      }
    }
  }

  private fun scheduleOf(doc: LayawayDoc): List<Layaway.Installment> =
    doc.schedule.orEmpty().map { Layaway.Installment(it.no, it.due, it.amountMinor) }

  /** جمعِ ردیف‌ها همان‌طور که فاکتورِ تحویل حساب می‌کند (`SalesEngine.lineTotal`) */
  fun linesTotal(lines: List<LayawayLine>): Long =
    lines.sumOf { PayFlow.minor(SalesEngine.lineTotal(PayFlow.major(it.unitMinor), it.qtyMilli / 1000.0)) }

  private fun view(d: ShopData, row: AuditEntry, ix: Index, today: String): View? {
    val doc = row.layaway ?: return null
    if (row.type != TYPE) return null
    val debtorId = doc.debtorId ?: return null
    val total = doc.totalMinor ?: 0L
    val deposit = doc.depositMinor ?: 0L
    val lines = doc.lines.orEmpty()
    val schedule = scheduleOf(doc)
    val deposits = ix.deposits[row.id] ?: 0L
    val payments = ix.payments[row.id] ?: 0L
    val moves = ix.moves[row.id].orEmpty().sortedBy { it.createdAt }
    val live = moves.lastOrNull { m -> m.layaway?.let { it.to == DELIVERED && it.saleId != null && it.saleId !in ix.cancelledSales } == true }
    val cancelled = moves.any { it.layaway?.to == CANCELLED }
    val status = when {
      cancelled -> Status.CANCELLED
      live != null -> Status.DELIVERED
      else -> Status.OPEN
    }
    val progress = Layaway.progress(schedule, payments, today)
    val broken = schedule.isEmpty() || lines.isEmpty() || schedule.sumOf { it.amountMinor } != total - deposit || linesTotal(lines) != total
    val who = SpecialOrders.owner(d, debtorId) ?: debtorId
    return View(
      id = row.id, createdAt = row.createdAt, day = row.date, debtorId = who,
      customer = d.debtors.find { it.id == who }?.name.orEmpty(),
      lines = lines, totalMinor = total, depositMinor = deposit, schedule = schedule,
      depositsMinor = deposits, paymentsMinor = payments, refundsMinor = ix.refunds[row.id] ?: 0L,
      payCount = ix.payCount[row.id] ?: 0, deliveries = moves.count { it.layaway?.to == DELIVERED },
      progress = progress, status = status, saleId = live?.layaway?.saleId,
      broken = broken,
      review = broken || deposits > deposit || progress.overMinor > 0 || (cancelled && live != null),
      by = row.by,
    )
  }

  /** همهٔ سندها: بازها اول (دیرها، بعد زودترین قسطِ بعدی)، بعد بسته‌ها (تازه‌ترین اول) */
  fun all(d: ShopData, today: String): List<View> {
    val ix = Index(d, null)
    val views = d.auditLog.filter(::isDoc).mapNotNull { view(d, it, ix, today) }
    val (open, closed) = views.partition { it.active }
    return open.sortedWith(compareBy<View>({ !it.late }, { it.progress.next?.due ?: "9999" }, { it.createdAt })) +
      closed.sortedByDescending { it.createdAt }
  }

  /** سندهای یک مشتری (ادغام‌شده هم به مشتریِ ماندگار) */
  fun ofCustomer(d: ShopData, debtorId: String, today: String): List<View> {
    val who = SpecialOrders.owner(d, debtorId) ?: debtorId
    return all(d, today).filter { it.debtorId == who }
  }

  fun find(d: ShopData, id: String, today: String): View? =
    d.auditLog.firstOrNull { it.id == id && isDoc(it) }?.let { view(d, it, Index(d, setOf(id)), today) }

  /* ───────────────────────────── ثبت (خالص، روی دفترِ همین لحظه) ───────────────────────────── */

  sealed interface Built {
    data class Ok(val data: ShopData, val saleId: String? = null) : Built
    /** کدِ هسته یا EMPTY، TOO_MANY، NO_PRODUCT، NOT_FOUND، NOT_ALLOWED، REVIEW، STALE، NOT_SETTLED، BAD_REASON، SALE (پیامِ صندوق در [detail])، TOTAL_MISMATCH */
    data class Bad(val code: String, val detail: String? = null) : Built
  }

  data class NewLayaway(
    val debtorId: String?,
    val cart: List<SalesEngine.CartLine>,
    val depositMinor: Long?,
    val count: Int?,
    val everyDays: Int?,
    val firstDue: String,
  )

  /** ردیف‌های سبد با قیمتِ همین لحظه (`SalesEngine.linePrice`)؛ کالای ناموجود در فهرست ⇒ null */
  fun linesOf(d: ShopData, cart: List<SalesEngine.CartLine>): List<LayawayLine>? = cart.map { l ->
    val p = if (l.free) null else d.products.find { it.id == l.productId } ?: return null
    LayawayLine(
      productId = l.productId,
      label = if (l.free) l.label.trim() else null,
      name = p?.name ?: l.label.trim(),
      qtyMilli = Math.round(l.quantity * 1000),
      unitMinor = PayFlow.minor(SalesEngine.linePrice(d, l)),
    )
  }

  /** ردیفِ فاکتورِ تحویل: کالای فهرست، یا قلمِ آزاد با همان نام (کالای پاک‌شده هم) — قیمتِ ثابتِ سند */
  fun cartLine(cur: ShopData, l: LayawayLine): SalesEngine.CartLine {
    val qty = l.qtyMilli / 1000.0
    val unit = PayFlow.major(l.unitMinor)
    val known = l.label == null && cur.products.any { it.id == l.productId }
    return if (known) SalesEngine.CartLine(l.productId, qty, unit) else SalesEngine.CartLine(l.productId, qty, unit, (l.label ?: l.name).ifBlank { "قلمِ امانی" })
  }

  fun hasRow(d: ShopData, key: String): Boolean = d.auditLog.any { it.id == key }

  fun hasSale(d: ShopData, key: String): Boolean = d.sales.any { it.id == key }

  /** کلیدِ رسیدِ قسطِ بعدی: از شناسهٔ سند و شمارهٔ پرداخت — دو ضربه، یا تکرار پس از قطع، همان رسید */
  fun payKey(v: View): String = "${v.id}-p${v.payCount + 1}"

  /** شناسهٔ فاکتورِ تحویل: از شناسهٔ سند و شمارهٔ تحویل — دو دستگاه هم یک فروش می‌سازند */
  fun deliverKey(v: View): String = "${v.id}-d${v.deliveries + 1}"

  /** شناسهٔ ردیفِ لغو (لغو پایانی است، پس یکی) */
  fun cancelKey(v: View): String = "${v.id}-x"

  /** همان کلید و همان مبلغ پیش‌تر نشسته ⇒ تکرار (هیچ نوشتنی) */
  fun hasPayment(docId: String, amountMinor: Long): (ShopData, String) -> Boolean = { d, key ->
    d.transactions.any { it.id == key && it.refId == docId && it.kind == INSTALLMENT && PayFlow.minor(it.amount) == amountMinor }
  }

  /** ردیف‌های سبد: EMPTY، TOO_MANY، BAD_QTY (مقدار یا قیمتِ منفی/صفر)؛ null ⇒ پذیرفته */
  private fun linesProblem(lines: List<LayawayLine>): String? = when {
    lines.isEmpty() -> "EMPTY"
    lines.size > MAX_LINES -> "TOO_MANY"
    lines.any { it.qtyMilli <= 0 || it.unitMinor < 0 } -> "BAD_QTY"
    else -> null
  }

  /** پیش‌نمایشِ جدولِ پنجرهٔ «امانیِ تازه»؛ پیش‌نویسِ نادرست ⇒ کدِ هسته */
  fun plan(cur: ShopData, o: NewLayaway, today: String): Pair<List<Layaway.Installment>, String?> {
    val lines = linesOf(cur, o.cart) ?: return emptyList<Layaway.Installment>() to "NO_PRODUCT"
    val draft = Layaway.Draft(Layaway.LAYAWAY, o.debtorId != null, linesTotal(lines), o.depositMinor, o.count, o.everyDays, o.firstDue, today)
    val problem = linesProblem(lines) ?: Layaway.draftProblem(draft)
    return (if (problem == null) Layaway.schedule(draft) else emptyList()) to problem
  }

  /**
   *  سند با شناسهٔ [key] و رسیدِ پیش‌پرداخت (اگر هست). سبد پیش از ثبت با همان راهِ تحویل آزموده می‌شود (فروشی که ثبت
   *  نمی‌شود — کالای غیرفعال، سریال، کیت — امانی هم نمی‌شود)؛ موجودی دست نمی‌خورد تا تحویل.
   */
  fun create(cur: ShopData, key: String, o: NewLayaway, today: String, now: Long, by: String, blockExpired: Boolean = false): Built {
    val debtor = o.debtorId?.let { SpecialOrders.owner(cur, it) }
    val lines = linesOf(cur, o.cart) ?: return Built.Bad("NO_PRODUCT")
    linesProblem(lines)?.let { return Built.Bad(it) }
    val total = linesTotal(lines)
    val draft = Layaway.Draft(Layaway.LAYAWAY, debtor != null, total, o.depositMinor, o.count, o.everyDays, o.firstDue, today)
    Layaway.draftProblem(draft)?.let { return Built.Bad(it) }
    val schedule = Layaway.schedule(draft)
    //  نگهبان: جدولِ دقیق — جمعِ قسط‌ها همان باقی
    val dep = o.depositMinor!!
    if (schedule.sumOf { it.amountMinor } != total - dep) return Built.Bad("TOTAL_MISMATCH")
    var n = 0
    val probe = SalesEngine.Checkout(seller = by, payment = SalesEngine.Payment.CREDIT, paidAmount = 0.0, debtorId = debtor, blockExpired = blockExpired)
    when (val r = SalesEngine.record(cur, lines.map { cartLine(cur, it) }, probe, today, now, { "probe${n++}" }, saleId = "$key-probe")) {
      is SalesEngine.Result.Failed -> return Built.Bad("SALE", r.message)
      is SalesEngine.Result.Ok -> if (PayFlow.minor(r.data.sales.last().finalTotal) != total) return Built.Bad("TOTAL_MISMATCH")
    }
    val afn = { m: Long -> ir.vil3ntec.tohid.money(PayFlow.major(m)) }
    val row = AuditEntry(
      id = key, type = TYPE, date = today, createdAt = now, amount = PayFlow.major(dep),
      notes = "فروشِ امانی: ${lines.size} قلم — جمع ${afn(total)}، پیش‌پرداخت ${afn(dep)} افغانی، ${schedule.size} قسط از ${o.firstDue}",
      by = by.trim().ifEmpty { null },
      layaway = LayawayDoc(
        debtorId = debtor, kind = Layaway.LAYAWAY, totalMinor = total, depositMinor = dep, lines = lines,
        schedule = schedule.map { LayawayDue(it.no, it.due, it.amountMinor) },
      ),
    )
    val deposit = if (dep <= 0) null else DebtTransaction(
      id = "$key-p0", debtorId = debtor!!, type = "receive", amount = PayFlow.major(dep), date = today,
      notes = "پیش‌پرداختِ فروشِ امانی", createdAt = now, refId = key, kind = DEPOSIT,
    )
    return Built.Ok(cur.copy(auditLog = cur.auditLog + row, transactions = cur.transactions + listOfNotNull(deposit)))
  }

  /** رسیدِ یک قسط با شناسهٔ [key] ([payKey]) — نقد، نه بیش از مانده */
  fun pay(cur: ShopData, key: String, docId: String, amountMinor: Long, today: String, now: Long, by: String): Built {
    val v = find(cur, docId, today) ?: return Built.Bad("NOT_FOUND")
    if (!v.active) return Built.Bad("NOT_ALLOWED")
    if (v.review) return Built.Bad("REVIEW")
    //  همان کلید با مبلغِ دیگر (دستگاهِ دیگر همین قسط را گرفت) ⇒ نه دو رسید با یک شناسه
    if (cur.transactions.any { it.id == key }) return Built.Bad("STALE")
    Layaway.payProblem(v.progress, amountMinor)?.let { return Built.Bad(it) }
    if (cur.debtors.none { it.id == v.debtorId }) return Built.Bad("NO_CUSTOMER")
    val left = v.progress.remainingMinor - amountMinor
    val tx = DebtTransaction(
      id = key, debtorId = v.debtorId, type = "receive", amount = PayFlow.major(amountMinor), date = today,
      notes = "قسطِ ${v.payCount + 1} ِ فروشِ امانی — مانده ${ir.vil3ntec.tohid.money(PayFlow.major(left))} افغانی",
      createdAt = now, refId = v.id, kind = INSTALLMENT,
    )
    return Built.Ok(cur.copy(transactions = cur.transactions + tx))
  }

  private fun move(id: String, v: View, to: String, today: String, now: Long, by: String, notes: String, amountMinor: Long, reason: String? = null, saleId: String? = null) =
    AuditEntry(
      id = id, type = MOVE, date = today, refId = v.id, createdAt = now, amount = PayFlow.major(amountMinor),
      notes = notes, by = by.trim().ifEmpty { null }, reason = reason,
      layaway = LayawayDoc(to = to, saleId = saleId),
    )

  /**
   *  تحویل = فروشِ عادیِ صندوق با شناسهٔ [key] ([deliverKey]) — فقط پس از تسویهٔ کامل و فقط یک بار. همهٔ فاکتور «به
   *  حساب» می‌رود و پرداختی‌ها را می‌بندد؛ ردِ صندوق (کالای غیرفعال، کمبودِ موجودی، منقضی) همان پیامِ `SalesEngine`
   *  است و هیچ چیز نوشته نمی‌شود.
   */
  fun deliver(cur: ShopData, key: String, docId: String, today: String, now: Long, by: String, series: String, blockExpired: Boolean, newId: () -> String): Built {
    val v = find(cur, docId, today) ?: return Built.Bad("NOT_FOUND")
    if (!v.active) return Built.Bad("NOT_ALLOWED")
    if (v.broken) return Built.Bad("REVIEW")
    Layaway.deliverProblem(Layaway.LAYAWAY, v.progress)?.let { return Built.Bad(it) }
    if (!v.paidUp) return Built.Bad("NOT_SETTLED")
    if (cur.debtors.none { it.id == v.debtorId }) return Built.Bad("NO_CUSTOMER")
    val checkout = SalesEngine.Checkout(seller = by, payment = SalesEngine.Payment.CREDIT, paidAmount = 0.0, debtorId = v.debtorId, blockExpired = blockExpired)
    val data = when (val r = SalesEngine.record(cur, v.lines.map { cartLine(cur, it) }, checkout, today, now, newId, series, saleId = key)) {
      is SalesEngine.Result.Failed -> return Built.Bad("SALE", r.message)
      is SalesEngine.Result.Ok -> r.data
    }
    val sale = data.sales.last()
    //  نگهبان: فاکتور همان جمعِ سند و همه «به حساب» ِ پرداختی‌ها — وگرنه هیچ نوشتنی
    if (PayFlow.minor(sale.finalTotal) != v.totalMinor || PayFlow.minor(sale.debtGiven) != v.totalMinor) return Built.Bad("TOTAL_MISMATCH")
    val label = sale.invoiceLabel() ?: "0"
    val txs = data.transactions.map {
      if (it.refId == key && it.kind == SalesEngine.SALE_CREDIT) it.copy(notes = "تسویه از پرداخت‌های فروشِ امانی — فاکتور #$label") else it
    }
    val row = move(
      "$key-m", v, DELIVERED, today, now, by,
      "تحویلِ فروشِ امانی — فاکتور #$label، ${ir.vil3ntec.tohid.money(PayFlow.major(v.totalMinor))} افغانی از پرداختی‌ها",
      amountMinor = v.totalMinor, saleId = key,
    )
    return Built.Ok(data.copy(transactions = txs, auditLog = data.auditLog + row), saleId = key)
  }

  /** لغوِ سندِ باز با دلیل ([key] = [cancelKey]); همهٔ پرداختی دقیقاً با بردِ نقدِ [REFUND] پس داده می‌شود */
  fun cancel(cur: ShopData, key: String, docId: String, reason: String, today: String, now: Long, by: String): Built {
    val v = find(cur, docId, today) ?: return Built.Bad("NOT_FOUND")
    if (!v.active) return Built.Bad("NOT_ALLOWED")
    val why = SpecialOrder.reason(reason)
    if (why == null || why.length < SpecialOrder.REASON_MIN || why.length > SpecialOrder.REASON_MAX) return Built.Bad("BAD_REASON")
    //  پیش‌پرداخت + قسط‌ها (و بیش‌پرداختِ احتمالی) — هسته؛ منهای بازپرداختِ پیشین که نباید باشد
    val refund = Layaway.cancelRefund(Layaway.LAYAWAY, v.depositsMinor, v.progress)!! - v.refundsMinor
    if (refund != v.heldMinor || refund < 0) return Built.Bad("TOTAL_MISMATCH")
    val afn = ir.vil3ntec.tohid.money(PayFlow.major(refund))
    val row = move(key, v, CANCELLED, today, now, by, "لغوِ فروشِ امانی — بازپرداختِ $afn افغانی — دلیل: $why", amountMinor = refund, reason = why)
    val back = if (refund <= 0) null else DebtTransaction(
      id = "${v.id}-r", debtorId = v.debtorId, type = "give", amount = PayFlow.major(refund), date = today,
      notes = "بازپرداختِ نقدِ فروشِ امانی (لغو)", createdAt = now, refId = v.id, kind = REFUND,
    )
    if (back != null && cur.transactions.any { it.id == back.id }) return Built.Bad("STALE")
    return Built.Ok(cur.copy(auditLog = cur.auditLog + row, transactions = cur.transactions + listOfNotNull(back)))
  }
}
