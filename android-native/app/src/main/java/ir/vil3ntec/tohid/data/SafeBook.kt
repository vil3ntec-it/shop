package ir.vil3ntec.tohid.data

import ir.vil3ntec.tohid.core.money.MoneyInput
import ir.vil3ntec.tohid.core.pay.Pay
import ir.vil3ntec.tohid.core.safe.SafeCore
import ir.vil3ntec.tohid.money

/**
 *  گاوصندوق در دفترِ دکان — PAY-18 (طرحِ SAFE-V1). همهٔ قاعده در `core/safe/SafeCore`؛
 *  این‌جا فقط دفتر ⇄ هسته و ردیفِ تازه. خالص است: نه Context، نه ساعت، نه دیسک —
 *  ساعت و شناسه را صفحه می‌دهد و `Ok.data` را از همان راهِ همیشگی ذخیره می‌کند.
 *
 *  آستانهٔ اختلافِ شمارش همان آستانهٔ صندوق است ([Pay.DEFAULT_VARIANCE_THRESHOLD]، D-18).
 *  انتقال از/به دخل ([transfer]) دو ردیف با هم است: ردیفِ گاوصندوق و ردیفِ جفتِ `cash_out`/`cash_in` در سابقهٔ صندوق.
 */
object SafeBook {

  sealed interface Result {
    /** [duplicate]: همان عملیات پیش‌تر ثبت شده بود و دفتر عوض نشد */
    data class Ok(val data: ShopData, val entry: SafeEntry, val duplicate: Boolean = false) : Result
    data class Failed(val code: String, val message: String, val varianceMinor: Long? = null) : Result
  }

  fun toCore(e: SafeEntry): SafeCore.Entry = SafeCore.Entry(
    id = e.id, kind = e.kind, amountMinor = e.amountMinor, at = e.createdAt, by = e.by, reason = e.reason,
    note = e.note, bagNo = e.bagNo, expectedMinor = e.expectedMinor, shiftId = e.shiftId, auditId = e.auditId,
  )

  private fun toRow(e: SafeCore.Entry): SafeEntry = SafeEntry(
    id = e.id, kind = e.kind, amountMinor = e.amountMinor, createdAt = e.at, by = e.by, reason = e.reason,
    note = e.note, bagNo = e.bagNo, expectedMinor = e.expectedMinor, shiftId = e.shiftId, auditId = e.auditId,
  )

  /** مانده، آخرین شمارش و تاریخچه (تازه‌ترین اول) */
  fun view(d: ShopData): SafeCore.View = SafeCore.view(d.safeEntries.map(::toCore))

  /** شمارش: مبلغِ شمرده؛ اختلاف با مانده کنارش ثبت می‌شود، هرگز بی‌صدا یکی نمی‌شود */
  fun count(d: ShopData, countedMinor: Long, reason: String?, by: String, now: Long, newId: () -> String): Result =
    record(d, SafeCore.Input(newId(), SafeCore.COUNT, countedMinor, now, by = by, reason = reason))

  /** ورودِ پول به گاوصندوق — `reason` از `SafeCore.REASONS[IN]`؛ `other` یادداشت می‌خواهد */
  fun deposit(d: ShopData, amountMinor: Long, reason: String, note: String?, by: String, now: Long, newId: () -> String): Result =
    record(d, SafeCore.Input(newId(), SafeCore.IN, amountMinor, now, by = by, reason = reason, note = note))

  /** خروجِ پول — `reason` از `SafeCore.REASONS[OUT]`؛ بیش از مانده رد می‌شود */
  fun withdraw(d: ShopData, amountMinor: Long, reason: String, note: String?, by: String, now: Long, newId: () -> String): Result =
    record(d, SafeCore.Input(newId(), SafeCore.OUT, amountMinor, now, by = by, reason = reason, note = note))

  /** تحویل به بانک با شمارهٔ کیسه (PAY-18) */
  fun toBank(d: ShopData, amountMinor: Long, bagNo: String, note: String?, by: String, now: Long, newId: () -> String): Result =
    record(d, SafeCore.Input(newId(), SafeCore.TO_BANK, amountMinor, now, by = by, note = note, bagNo = bagNo))

  /** کدِ «صندوق باز نیست» برای [transfer] — همان کدِ `ShiftCore` */
  const val SHIFT_CLOSED = "SHIFT_CLOSED"

  /** یادداشتِ ردیفِ جفتِ سابقهٔ صندوق (داده، همیشه فارسی) */
  const val DRAWER_NOTE = "گاوصندوق"

  /**
   *  انتقالِ دخل ⇄ گاوصندوق: [SafeCore.FROM_DRAWER] (خروج از دخل، ورود به گاوصندوق) یا [SafeCore.TO_DRAWER].
   *  دو ردیف با هم یا هیچ — ردیفِ گاوصندوق با `shiftId` = همان `shift_open` و `auditId` = ردیفِ جفتِ
   *  `cash_out`/`cash_in` ِ همان صندوق. هر دو را هسته می‌سنجد (`SafeCore.build` و `ShiftCore.record` از راهِ
   *  [PayFlow.moveRow]): خروجِ بیش از نقدِ دخل یا بیش از ماندهٔ گاوصندوق هیچ ردیفی نمی‌نویسد. دلیلِ ردیفِ صندوق
   *  «دیگر» با یادداشتِ [DRAWER_NOTE] است، پس فهرستِ دلیل‌های صندوق (`ShiftCore.MOVE_REASONS`، pay-core) دست نمی‌خورد.
   *  [id] و [auditId] را صفحه پیش از ثبت می‌سازد، تا تکرارِ همان عملیات همان دو ردیف باشد.
   */
  fun transfer(d: ShopData, kind: String, amountMinor: Long, by: String, today: String, now: Long, id: String, auditId: String): Result {
    if (kind != SafeCore.FROM_DRAWER && kind != SafeCore.TO_DRAWER) return failed(SafeCore.Code.BAD_KIND)
    //  همان شناسه پیش‌تر نشسته ⇒ فقط داوریِ تکرار/تعارض، بی ردیفِ تازهٔ صندوق
    d.safeEntries.firstOrNull { it.id == id }?.let { prior ->
      return record(d, SafeCore.Input(id, kind, amountMinor, now, by = by, shiftId = prior.shiftId, auditId = auditId))
    }
    val shift = CashShift.current(d) ?: return failed(SHIFT_CLOSED)
    val text = MoneyInput.ofMinor(amountMinor)
    if (amountMinor <= 0 || amountMinor > SafeCore.MAX_AMOUNT || text == null) return failed(SafeCore.Code.INVALID_AMOUNT)
    val input = SafeCore.Input(id, kind, amountMinor, now, by = by, shiftId = shift.open.id, auditId = auditId)
    val safe = when (val r = SafeCore.build(d.safeEntries.map(::toCore), input, Pay.DEFAULT_VARIANCE_THRESHOLD)) {
      is SafeCore.Result.Err -> return Result.Failed(r.code, message(r.code))
      is SafeCore.Result.Ok -> toRow(r.value)
    }
    val out = kind == SafeCore.FROM_DRAWER
    val move = PayFlow.Move(if (out) "cash_out" else "cash_in", text, "other", DRAWER_NOTE, by)
    val cash = when (val r = PayFlow.moveRow(d, shift.open, move, now) { auditId }) {
      is PayFlow.Outcome.Bad -> return Result.Failed(r.problem.code, PayFlow.message(r.problem))
      is PayFlow.Outcome.Ok -> r.value
    }
    //  ردیفِ صندوق مبلغ را افغانی (Double) نگه می‌دارد؛ اگر دقیقاً همان پول نشد، هیچ
    if (PayFlow.minor(cash.amount) != amountMinor) return failed(SafeCore.Code.INVALID_AMOUNT)
    val who = by.trim()
    val notes = "${if (out) "خروج از دخل به گاوصندوق" else "ورود به دخل از گاوصندوق"} — ${money(cash.amount)} افغانی" +  // داده: یادداشتِ سابقهٔ صندوق
      (if (who.isNotEmpty()) " — $who" else "")
    return Result.Ok(d.copy(safeEntries = d.safeEntries + safe, auditLog = d.auditLog + cash.copy(date = today, notes = notes)), safe)
  }

  private fun failed(code: String): Result = Result.Failed(code, message(code))

  private fun record(d: ShopData, input: SafeCore.Input): Result =
    when (val r = SafeCore.build(d.safeEntries.map(::toCore), input, Pay.DEFAULT_VARIANCE_THRESHOLD)) {
      is SafeCore.Result.Err -> Result.Failed(r.code, message(r.code, r.varianceMinor), r.varianceMinor)
      is SafeCore.Result.Ok -> {
        val row = toRow(r.value)
        if (r.duplicate) Result.Ok(d, row, duplicate = true)
        else Result.Ok(d.copy(safeEntries = d.safeEntries + row), row)
      }
    }

  fun message(code: String, varianceMinor: Long? = null): String = when (code) {
    SafeCore.Code.REASON_REQUIRED -> varianceMinor?.let {
      "اختلافِ شمارش ${if (it > 0) "اضافه" else "کسری"} ${money(kotlin.math.abs(it) / 100.0)} افغانی است — " +
        "بیش از ${money(Pay.DEFAULT_VARIANCE_THRESHOLD / 100.0)} افغانی دلیل می‌خواهد"
    } ?: "دلیل را بنویسید"
    SafeCore.Code.INSUFFICIENT_FUNDS -> "این مبلغ بیش از ماندهٔ گاوصندوق است"
    SafeCore.Code.INVALID_AMOUNT -> "مبلغ درست نیست"
    SafeCore.Code.NOTE_REQUIRED -> "برای «دیگر» یادداشت بنویسید"
    SafeCore.Code.BAG_REQUIRED -> "شمارهٔ کیسه را بنویسید"
    SafeCore.Code.BAD_REASON -> "دلیل درست نیست"
    SafeCore.Code.TEXT_TOO_LONG -> "متن بیش از اندازه بلند است"
    SafeCore.Code.ID_CONFLICT -> "این عملیات با محتوای دیگری ثبت شده بود"
    SafeCore.Code.INVALID_TIME -> "زمانِ دستگاه درست نیست"
    SHIFT_CLOSED -> "صندوق باز نیست؛ اول صندوق را باز کنید"
    else -> "ثبت نشد ($code)"
  }
}
