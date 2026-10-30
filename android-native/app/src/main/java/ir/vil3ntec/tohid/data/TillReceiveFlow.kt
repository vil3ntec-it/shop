package ir.vil3ntec.tohid.data

import ir.vil3ntec.tohid.core.debt.TillReceive

/**
 *  POS-39 — ثبتِ دریافتِ قسط/بدهی از صندوق روی دفترِ **همین لحظه** (`ShopStore.update`). خالص:
 *  همان `DebtorEngine.addTransaction(RECEIVE)` ِ صفحهٔ قرض‌داران (همان ردیفِ `receive` و سابقهٔ
 *  `customer_payment`، پس دخلِ شیفت آن را مثلِ هر رسیدِ قرض می‌شمارد)، با دو نگهبان از `core`:
 *    • مبلغ با `TillReceive.judge`؛ پیش‌پرداخت فقط با `prepayConfirmed`.
 *    • کلیدِ یکتا [key] = شناسهٔ همان ردیف؛ کلیدی که در دفتر هست ⇒ [Outcome.Already]، بی نوشتنِ دوم.
 */
object TillReceiveFlow {

  sealed interface Outcome {
    data class Ok(val data: ShopData, val txId: String, val verdict: TillReceive.Verdict.Ok) : Outcome
    data class Already(val txId: String) : Outcome
    data class NeedsConfirm(val verdict: TillReceive.Verdict.Ok) : Outcome
    data class Failed(val message: String) : Outcome
  }

  fun balanceMinor(d: ShopData, debtorId: String): Long = PayFlow.minor(ShopStore.debt(d, debtorId))

  fun message(p: TillReceive.Problem): String = when (p) {
    TillReceive.Problem.NOT_A_NUMBER -> "مبلغ را درست بنویسید"
    TillReceive.Problem.NOT_POSITIVE -> "مبلغ باید بیشتر از صفر باشد"
    TillReceive.Problem.TOO_LARGE -> "مبلغ بیش از حد است"
  }

  fun apply(
    cur: ShopData,
    debtorId: String,
    amountMinor: Long?,
    notes: String,
    key: String,
    prepayConfirmed: Boolean,
    today: String,
    now: Long,
    newId: () -> String,
  ): Outcome {
    if (TillReceive.alreadyRecorded(cur.transactions.map { it.id }, key)) return Outcome.Already(key)
    if (cur.debtors.none { it.id == debtorId }) return Outcome.Failed("مشتری پیدا نشد")
    val v = when (val j = TillReceive.judge(balanceMinor(cur, debtorId), amountMinor)) {
      is TillReceive.Verdict.Bad -> return Outcome.Failed(message(j.problem))
      is TillReceive.Verdict.Ok -> j
    }
    if (TillReceive.needsConfirm(v) && !prepayConfirmed) return Outcome.NeedsConfirm(v)
    //  نخستین شناسه همان کلید است (ردیفِ `receive`)؛ بقیه (سابقه) تازه
    var first = true
    val ids = { if (first) { first = false; key } else newId() }
    val text = notes.trim().ifEmpty { "دریافت از صندوق" }
    return when (val r = DebtorEngine.addTransaction(cur, debtorId, DebtorEngine.Kind.RECEIVE, PayFlow.major(v.amountMinor), today, text, today, now, ids)) {
      is DebtorEngine.Result.Failed -> Outcome.Failed(r.message)
      is DebtorEngine.Result.Ok -> Outcome.Ok(r.data, r.id, v)
    }
  }
}
