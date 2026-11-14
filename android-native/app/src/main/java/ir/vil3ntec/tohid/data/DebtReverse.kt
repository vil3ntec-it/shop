package ir.vil3ntec.tohid.data

import ir.vil3ntec.tohid.core.debt.DebtReversal

/**
 *  S89 — مرزِ دفترِ برنامه برای `core/debt/DebtReversal` (منطق فقط آن‌جاست): ردیفِ `DebtTransaction` ⇐ [DebtReversal.Row]،
 *  نمای مؤثر (جفتِ «اصل + معکوس» بیرون) و معکوس‌های رسیدِ نقد برای دخل.
 *
 *  دفترِ بی ردیفِ معکوس (کلیدِ `shop.doc_chain` خاموش، یعنی امروز) ⇒ **همان فهرست** بی هیچ کار ([effective])، پس
 *  کهنگی، سررسید، امتیاز و گزارش‌ها دقیقاً همان امروزند.
 */
object DebtReverse {

  fun row(t: DebtTransaction): DebtReversal.Row = DebtReversal.Row(
    id = t.id,
    type = t.type,
    amountMinor = PayFlow.minor(t.amount),
    kind = t.kind,
    refId = t.refId,
    cash = t.type == DebtReversal.RECEIVE && CashShift.isCashReceipt(t),
  )

  fun isReversal(t: DebtTransaction): Boolean = DebtReversal.isReversal(t.kind)

  /** ردیف‌ها بی جفت‌های معکوس — بی ردیفِ معکوس همان شیء */
  fun effective(txs: List<DebtTransaction>): List<DebtTransaction> {
    if (txs.none(::isReversal)) return txs
    val drop = DebtReversal.neutral(txs.map(::row))
    return if (drop.isEmpty()) txs else txs.filter { it.id !in drop }
  }

  /** اصل‌هایی که معکوس شده‌اند (برای برچسبِ «معکوس‌شده» در فهرست) */
  fun reversed(txs: List<DebtTransaction>): Set<String> =
    if (txs.none(::isReversal)) emptySet() else DebtReversal.pairs(txs.map(::row)).keys

  /**
   *  ردیفِ معکوسِ [tx] طبقِ [p] (`DebtReversal.plan`): همان مشتری و مبلغ، تاریخ و ساعتِ **امروز** (روزِ معکوس)، مهرِ شیفتِ
   *  همین دستگاه (`TillDrawer.stampNew`، مثلِ هر رسیدِ تازه)، یک ردیفِ سابقهٔ `debt_tx_reverse`، و برای رسیدِ دستی همان
   *  پس‌گرفتنِ امتیازِ حذف (`LoyaltyPoints.afterPaymentDeleted`، PRM-13).
   */
  fun reverse(d: ShopData, tx: DebtTransaction, p: DebtReversal.Plan.Reverse, today: String, now: Long, newId: () -> String): ShopData {
    val who = d.debtors.find { it.id == tx.debtorId }?.name.orEmpty()
    val kind = if (tx.type == DebtReversal.RECEIVE) "دریافت" else "قرض"  // داده: برچسبِ درونِ یادداشتِ سابقه
    val note = "سندِ معکوسِ $kind ${ir.vil3ntec.tohid.money(tx.amount)} افغانی از حسابِ «$who» (تاریخ ${tx.date})"  // داده: یادداشتِ سابقهٔ دفتر (ذخیره و همگام می‌شود)
    val row = DebtTransaction(
      id = p.id, debtorId = tx.debtorId, type = p.type, amount = tx.amount, date = today, notes = note, createdAt = now,
      refId = p.refId, kind = DebtReversal.KIND,
    )
    val trace = AuditEntry(id = newId(), type = AUDIT, date = today, refId = tx.debtorId, createdAt = now, notes = note)
    var out = d.copy(transactions = d.transactions + row, auditLog = d.auditLog + trace)
    if (tx.type == DebtReversal.RECEIVE && tx.kind == null) out = LoyaltyPoints.afterPaymentDeleted(out, tx.id, today, now)
    return TillDrawer.stampNew(d, out)
  }

  /** نوعِ ردیفِ سابقهٔ معکوس (`AuditModel`) */
  const val AUDIT = "debt_tx_reverse"

  /** با کلیدِ روشن این ردیف معکوس نمی‌شود؟ ⇒ جملهٔ دلیل؛ معکوس‌شدنی ⇒ `null` (همان `DebtReversal.plan`) */
  fun refusalOf(d: ShopData, txId: String): String? =
    (DebtReversal.plan(true, d.transactions.find { it.id == txId }?.let(::row), d.transactions.map(::row)) as? DebtReversal.Plan.Refused)?.let { refusal(it.why) }

  fun refusal(why: DebtReversal.Refusal): String = when (why) {
    DebtReversal.Refusal.NOT_FOUND -> ir.vil3ntec.tohid.core.i18n.StrLedger.txNotFound
    DebtReversal.Refusal.IS_REVERSAL -> ir.vil3ntec.tohid.core.i18n.StrLedger.reverseIsReversal
    DebtReversal.Refusal.ALREADY_REVERSED -> ir.vil3ntec.tohid.core.i18n.StrLedger.reverseAlready
    DebtReversal.Refusal.SYSTEM_ROW -> ir.vil3ntec.tohid.core.i18n.StrLedger.reverseFromSource
  }

  /** ردیف‌های معکوسی که رسیدِ نقد را برگرداندند — دخلِ روزِ خودشان را کم می‌کنند */
  fun cashBack(txs: List<DebtTransaction>): List<DebtTransaction> {
    if (txs.none(::isReversal)) return emptyList()
    val ids = DebtReversal.cashBack(txs.map(::row))
    return if (ids.isEmpty()) emptyList() else txs.filter { it.id in ids }
  }
}
