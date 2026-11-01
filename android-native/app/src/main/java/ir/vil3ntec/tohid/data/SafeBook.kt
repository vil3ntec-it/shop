package ir.vil3ntec.tohid.data

import ir.vil3ntec.tohid.core.pay.Pay
import ir.vil3ntec.tohid.core.safe.SafeCore
import ir.vil3ntec.tohid.money

/**
 *  گاوصندوق در دفترِ دکان — PAY-18 (طرحِ SAFE-V1). همهٔ قاعده در `core/safe/SafeCore`؛
 *  این‌جا فقط دفتر ⇄ هسته و ردیفِ تازه. خالص است: نه Context، نه ساعت، نه دیسک —
 *  ساعت و شناسه را صفحه می‌دهد و `Ok.data` را از همان راهِ همیشگی ذخیره می‌کند.
 *
 *  آستانهٔ اختلافِ شمارش همان آستانهٔ صندوق است ([Pay.DEFAULT_VARIANCE_THRESHOLD]، D-18).
 *  انتقال از/به دخل (با ردیفِ جفتِ `cash_out`/`cash_in` در سابقهٔ صندوق) پس از PR #286 می‌آید.
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
    else -> "ثبت نشد ($code)"
  }
}
