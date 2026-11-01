package ir.vil3ntec.tohid.data

import ir.vil3ntec.tohid.core.pos.SpecialOrder
import ir.vil3ntec.tohid.core.pos.SpecialOrder.Status
import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable

/**
 *  POS-26 — سفارشِ ویژهٔ مشتری با پیش‌پرداخت، روی دفترِ دکان. خالص: نه فایل، نه ساعت، نه Android؛ ثبت فقط با
 *  `SpecialOrderPref.record` (دفترِ همین لحظهٔ دیسک، کلیدِ یکتا). قاعده‌ها در هستهٔ `core/pos/SpecialOrder`.
 *
 *  ── بی جدولِ تازه ───────────────────────────────────────────────────
 *    • سند: ردیفِ سابقهٔ [TYPE] (شناسه = شناسهٔ سفارش) با `AuditEntry.specialOrder`.
 *    • وضعیت: هر تغییر ردیفِ تازهٔ [MOVE] (`refId` = سفارش، `specialOrder.to`)؛ وضعیت = `SpecialOrder.fold`.
 *    • پیش‌پرداخت: رسیدِ قرض‌دار (`receive`، `kind` = [DEPOSIT]، `refId` = سفارش) ⇒ مثلِ هر رسید نقدِ دخل
 *      (`CashShift.isCashReceipt`) و پیش‌پرداختِ حسابِ مشتری تا تحویل.
 *    • لغو: بردِ [REFUND] (`give`، همان `refId`) به اندازهٔ پیش‌پرداختِ نگه‌داشته ⇒ حسابِ مشتری صفر و دخل کم
 *      (`CashShift.totals` و `PayFlow.ledgerEvents` آن را پولِ پس‌داده می‌شمارند).
 *    • تحویل: فروشِ عادیِ صندوق (`SalesEngine.record`، شمارهٔ سریِ همین دستگاه) با مشتریِ سفارش؛ پیش‌پرداخت تا
 *      جمعِ فاکتور «به حساب» (`sale_credit`) می‌رود و همان رسید را می‌بندد، باقی نقد. فروشِ تحویلی که بعد لغو شود
 *      رویدادِ تحویل را بی‌اثر می‌کند (سفارش دوباره باز است و پیش‌پرداخت با رسیدِ لغو به حساب برگشته).
 *  مشتریِ ادغام‌شده (CRM-10) دنبال می‌شود: پول همیشه به حسابِ مشتریِ ماندگار می‌رود.
 */
object SpecialOrders {

  const val TYPE = "special_order"
  const val MOVE = "special_order_status"
  /** `DebtTransaction.kind` ِ رسیدِ پیش‌پرداخت (نقدِ دخل) */
  const val DEPOSIT = "special_order_deposit"
  /** `DebtTransaction.kind` ِ بردِ بازپرداختِ پیش‌پرداخت هنگامِ لغو (خروجِ نقد از دخل) */
  const val REFUND = "special_order_refund"

  data class View(
    val id: String,
    val createdAt: Long,
    val day: String,
    val debtorId: String,
    val customer: String,
    val productId: String?,
    val item: String,
    val qtyMilli: Long,
    val totalMinor: Long,
    val depositsMinor: Long,
    val refundsMinor: Long,
    val due: String,
    val status: Status,
    /** فاکتورِ تحویل (فقط تحویل‌شده) */
    val saleId: String?,
    val review: Boolean,
    val by: String?,
  ) {
    /** پیش‌پرداختی که هنوز نزدِ دکان است (برای تحویل‌شده: همان که از فاکتور کم شد) */
    val heldMinor: Long get() = SpecialOrder.held(depositsMinor, refundsMinor)
    val active: Boolean get() = !status.final
  }

  /** ردیفِ بازپرداختِ پیش‌پرداخت — پولی که از دخل به مشتری پس رفت (`CashShift`، `PayFlow`) */
  fun isRefund(t: DebtTransaction): Boolean = t.type == "give" && t.kind == REFUND

  fun isOrder(a: AuditEntry): Boolean = a.type == TYPE && a.specialOrder?.debtorId != null

  /** دفتر ⇐ ردیف‌های هر سفارش، یک بار برای همهٔ فهرست (نه یک گشتِ کاملِ دفتر برای هر سفارش) */
  private class Index(d: ShopData, only: Set<String>?) {
    val deposits = HashMap<String, Long>()
    val refunds = HashMap<String, Long>()
    val moves = HashMap<String, MutableList<AuditEntry>>()
    val cancelledSales: Set<String> = d.sales.filter { it.status == "cancelled" }.mapTo(HashSet()) { it.id }

    init {
      for (t in d.transactions) {
        val ref = t.refId ?: continue
        if (only != null && ref !in only) continue
        if (t.type == "receive" && t.kind == DEPOSIT) deposits[ref] = (deposits[ref] ?: 0L) + PayFlow.minor(t.amount)
        if (isRefund(t)) refunds[ref] = (refunds[ref] ?: 0L) + PayFlow.minor(t.amount)
      }
      for (a in d.auditLog) {
        if (a.type != MOVE) continue
        val ref = a.refId ?: continue
        if (only != null && ref !in only) continue
        moves.getOrPut(ref) { ArrayList() } += a
      }
    }
  }

  private fun view(d: ShopData, row: AuditEntry, ix: Index): View? {
    val doc = row.specialOrder ?: return null
    if (row.type != TYPE) return null
    val debtorId = doc.debtorId ?: return null
    val deposits = ix.deposits[row.id] ?: 0L
    val refunds = ix.refunds[row.id] ?: 0L
    val moves = ix.moves[row.id].orEmpty()
    val events = moves.map { m ->
      val to = m.specialOrder?.to.orEmpty()
      val sale = m.specialOrder?.saleId
      SpecialOrder.Event(m.id, m.createdAt, to, void = to == Status.DELIVERED.code && sale != null && sale in ix.cancelledSales)
    }
    val folded = SpecialOrder.fold(events)
    val saleId = if (folded.status == Status.DELIVERED) moves.firstOrNull { it.id == folded.applied.lastOrNull() }?.specialOrder?.saleId else null
    val who = owner(d, debtorId) ?: debtorId
    return View(
      id = row.id, createdAt = row.createdAt, day = row.date, debtorId = who,
      customer = d.debtors.find { it.id == who }?.name.orEmpty(),
      productId = doc.productId, item = doc.item.orEmpty(), qtyMilli = doc.qtyMilli ?: 1000L,
      totalMinor = doc.totalMinor ?: 0L, depositsMinor = deposits, refundsMinor = refunds,
      due = doc.due.orEmpty(), status = folded.status, saleId = saleId,
      review = SpecialOrder.review(folded, deposits, refunds), by = row.by,
    )
  }

  /** همهٔ سفارش‌ها: باز و آماده اول (زودترین روزِ تحویل)، بعد بسته‌ها (تازه‌ترین اول) */
  fun all(d: ShopData): List<View> {
    val ix = Index(d, null)
    val views = d.auditLog.filter(::isOrder).mapNotNull { view(d, it, ix) }
    val (open, closed) = views.partition { it.active }
    return open.sortedWith(compareBy<View>({ it.due }, { it.createdAt })) + closed.sortedByDescending { it.createdAt }
  }

  fun find(d: ShopData, id: String): View? =
    d.auditLog.firstOrNull { it.id == id && isOrder(it) }?.let { view(d, it, Index(d, setOf(id))) }

  /** مشتریِ ماندگار: ادغام‌شده (CRM-10) ⇒ مشتریِ مقصد؛ نبود ⇒ null */
  fun owner(d: ShopData, debtorId: String): String? {
    var id = debtorId
    repeat(8) {
      val x = d.debtors.find { it.id == id } ?: return null
      id = x.mergedInto ?: return x.id
    }
    return null
  }

  /* ───────────────────────────── ثبت (خالص، روی دفترِ همین لحظه) ───────────────────────────── */

  sealed interface Built {
    data class Ok(val data: ShopData, val saleId: String? = null) : Built
    /** کدِ هسته یا NOT_FOUND، NO_PRODUCT، SALE (پیامِ صندوق در [detail])، TOTAL_MISMATCH */
    data class Bad(val code: String, val detail: String? = null) : Built
  }

  data class NewOrder(
    val debtorId: String?,
    val productId: String?,
    val item: String,
    val qtyMilli: Long?,
    val totalMinor: Long?,
    val depositMinor: Long?,
    val due: String,
  )

  fun hasRow(d: ShopData, key: String): Boolean = d.auditLog.any { it.id == key }

  fun hasSale(d: ShopData, key: String): Boolean = d.sales.any { it.id == key }

  /** سند با شناسهٔ [key] و رسیدِ پیش‌پرداخت (اگر هست) */
  fun create(cur: ShopData, key: String, o: NewOrder, today: String, now: Long, by: String, newId: () -> String): Built {
    val debtor = o.debtorId?.let { owner(cur, it) }
    val item = SpecialOrder.item(o.item)
    SpecialOrder.draftProblem(SpecialOrder.Draft(debtor != null, item, o.qtyMilli, o.totalMinor, o.depositMinor, o.due, today))
      ?.let { return Built.Bad(it) }
    if (o.productId != null && cur.products.none { it.id == o.productId }) return Built.Bad("NO_PRODUCT")
    val dep = o.depositMinor!!
    val row = AuditEntry(
      id = key, type = TYPE, date = today, createdAt = now, amount = PayFlow.major(dep),
      notes = "سفارشِ ویژه: $item — جمع ${ir.vil3ntec.tohid.money(PayFlow.major(o.totalMinor!!))}، " +
        "پیش‌پرداخت ${ir.vil3ntec.tohid.money(PayFlow.major(dep))} افغانی، تحویل ${o.due}",
      by = by.trim().ifEmpty { null },
      specialOrder = SpecialOrderDoc(
        debtorId = debtor, productId = o.productId, item = item, qtyMilli = o.qtyMilli,
        totalMinor = o.totalMinor, depositMinor = dep, due = o.due,
      ),
    )
    val deposit = if (dep <= 0) null else DebtTransaction(
      id = newId(), debtorId = debtor!!, type = "receive", amount = PayFlow.major(dep), date = today,
      notes = "پیش‌پرداختِ سفارشِ ویژه — $item", createdAt = now, refId = key, kind = DEPOSIT,
    )
    return Built.Ok(cur.copy(auditLog = cur.auditLog + row, transactions = cur.transactions + listOfNotNull(deposit)))
  }

  private fun move(key: String, v: View, to: Status, today: String, now: Long, by: String, notes: String, amountMinor: Long = 0, reason: String? = null, saleId: String? = null) =
    AuditEntry(
      id = key, type = MOVE, date = today, refId = v.id, createdAt = now, amount = PayFlow.major(amountMinor),
      notes = notes, by = by.trim().ifEmpty { null }, reason = reason,
      specialOrder = SpecialOrderDoc(to = to.code, saleId = saleId),
    )

  fun ready(cur: ShopData, key: String, orderId: String, today: String, now: Long, by: String): Built {
    val v = find(cur, orderId) ?: return Built.Bad("NOT_FOUND")
    SpecialOrder.moveProblem(v.status, Status.READY, null)?.let { return Built.Bad(it) }
    return Built.Ok(cur.copy(auditLog = cur.auditLog + move(key, v, Status.READY, today, now, by, "سفارشِ ویژه آماده شد: ${v.item}")))
  }

  /** لغو با دلیل؛ همهٔ پیش‌پرداختِ نگه‌داشته با بردِ [REFUND] به مشتری پس داده می‌شود (نقد از دخل) */
  fun cancel(cur: ShopData, key: String, orderId: String, reason: String, today: String, now: Long, by: String, newId: () -> String): Built {
    val v = find(cur, orderId) ?: return Built.Bad("NOT_FOUND")
    SpecialOrder.moveProblem(v.status, Status.CANCELLED, reason)?.let { return Built.Bad(it) }
    val why = SpecialOrder.reason(reason)!!
    val refund = v.heldMinor
    val row = move(
      key, v, Status.CANCELLED, today, now, by,
      "لغوِ سفارشِ ویژه: ${v.item} — بازپرداختِ ${ir.vil3ntec.tohid.money(PayFlow.major(refund))} افغانی — دلیل: $why",
      amountMinor = refund, reason = why,
    )
    val back = if (refund <= 0) null else DebtTransaction(
      id = newId(), debtorId = v.debtorId, type = "give", amount = PayFlow.major(refund), date = today,
      notes = "بازپرداختِ پیش‌پرداختِ سفارشِ ویژه — ${v.item}", createdAt = now, refId = v.id, kind = REFUND,
    )
    return Built.Ok(cur.copy(auditLog = cur.auditLog + row, transactions = cur.transactions + listOfNotNull(back)))
  }

  /** ردیفِ فاکتورِ تحویل: کالای فهرست، یا قلمِ آزاد با همان شرح (کالای پاک‌شده هم) — جمعِ ردیف همان جمعِ سفارش */
  fun cartLine(cur: ShopData, v: View, newId: () -> String): SalesEngine.CartLine {
    val qty = v.qtyMilli / 1000.0
    val unit = PayFlow.major(v.totalMinor) / qty
    val p = v.productId?.let { id -> cur.products.find { it.id == id } }
    return if (p != null) SalesEngine.CartLine(p.id, qty, unit) else SalesEngine.CartLine(newId(), qty, unit, v.item)
  }

  /** پیش‌نمایشِ پنجرهٔ تحویل: به حساب (از پیش‌پرداخت)، نقدِ همین لحظه، پیش‌پرداختی که در حساب می‌ماند */
  fun settle(v: View): SpecialOrder.Settle = SpecialOrder.settle(v.totalMinor, v.heldMinor)

  /**
   *  تحویل = فروشِ عادیِ صندوق با شناسهٔ [key] (کلیدِ همان پنجره ⇒ دو ضربه یک فروش). ردِ صندوق (کالای غیرفعال،
   *  کمبودِ موجودی، منقضی) همان پیامِ `SalesEngine` است و هیچ چیز نوشته نمی‌شود.
   */
  fun deliver(
    cur: ShopData, key: String, orderId: String, today: String, now: Long, by: String,
    series: String, blockExpired: Boolean, newId: () -> String,
  ): Built {
    val v = find(cur, orderId) ?: return Built.Bad("NOT_FOUND")
    SpecialOrder.moveProblem(v.status, Status.DELIVERED, null)?.let { return Built.Bad(it) }
    if (cur.debtors.none { it.id == v.debtorId }) return Built.Bad("NO_CUSTOMER")
    val s = settle(v)
    val checkout = SalesEngine.Checkout(
      seller = by,
      payment = if (s.applyMinor > 0) SalesEngine.Payment.CREDIT else SalesEngine.Payment.CASH,
      paidAmount = PayFlow.major(s.payNowMinor),
      debtorId = v.debtorId,
      blockExpired = blockExpired,
    )
    val data = when (val r = SalesEngine.record(cur, listOf(cartLine(cur, v, newId)), checkout, today, now, newId, series, saleId = key)) {
      is SalesEngine.Result.Failed -> return Built.Bad("SALE", r.message)
      is SalesEngine.Result.Ok -> r.data
    }
    val sale = data.sales.last()
    //  نگهبان: فاکتور همان جمعِ سفارش و همان «به حساب» ِ پیش‌پرداخت — وگرنه هیچ نوشتنی
    if (PayFlow.minor(sale.finalTotal) != v.totalMinor || PayFlow.minor(sale.debtGiven) != s.applyMinor) return Built.Bad("TOTAL_MISMATCH")
    val label = sale.invoiceLabel() ?: "0"
    val txs = data.transactions.map {
      if (it.refId == key && it.kind == SalesEngine.SALE_CREDIT) it.copy(notes = "کسرِ پیش‌پرداختِ سفارشِ ویژه — فاکتور #$label") else it
    }
    val row = move(
      newId(), v, Status.DELIVERED, today, now, by,
      "تحویلِ سفارشِ ویژه: ${v.item} — فاکتور #$label، از پیش‌پرداخت ${ir.vil3ntec.tohid.money(PayFlow.major(s.applyMinor))} افغانی",
      amountMinor = s.applyMinor, saleId = key,
    )
    return Built.Ok(data.copy(transactions = txs, auditLog = data.auditLog + row), saleId = key)
  }
}

/**
 *  POS-26 — سفارشِ ویژه روی ردیفِ سابقه (`SpecialOrders`): سند روی `special_order` (مشتری، کالا یا شرح، مقدار، جمع،
 *  پیش‌پرداخت، روزِ تحویل) و وضعیتِ تازه روی `special_order_status` (`to`، و فاکتورِ تحویل در `saleId`). پول به پول
 *  (یک‌صدمِ افغانی). هر فیلدِ خالی نوشته نمی‌شود — ردیفِ وضعیت فقط `to` (و `saleId`) دارد.
 */
@Serializable
@OptIn(ExperimentalSerializationApi::class)
data class SpecialOrderDoc(
  @EncodeDefault(EncodeDefault.Mode.NEVER) val debtorId: String? = null,
  /** کالای فهرست؛ `null` ⇒ شرحِ دلخواه (قلمِ آزادِ فاکتورِ تحویل) */
  @EncodeDefault(EncodeDefault.Mode.NEVER) val productId: String? = null,
  @EncodeDefault(EncodeDefault.Mode.NEVER) val item: String? = null,
  @EncodeDefault(EncodeDefault.Mode.NEVER) val qtyMilli: Long? = null,
  @EncodeDefault(EncodeDefault.Mode.NEVER) val totalMinor: Long? = null,
  @EncodeDefault(EncodeDefault.Mode.NEVER) val depositMinor: Long? = null,
  /** روزِ تحویل (`yyyy-MM-dd`) */
  @EncodeDefault(EncodeDefault.Mode.NEVER) val due: String? = null,
  /** وضعیتِ تازه (`SpecialOrder.Status.code`) — فقط ردیفِ `special_order_status` */
  @EncodeDefault(EncodeDefault.Mode.NEVER) val to: String? = null,
  @EncodeDefault(EncodeDefault.Mode.NEVER) val saleId: String? = null,
)
