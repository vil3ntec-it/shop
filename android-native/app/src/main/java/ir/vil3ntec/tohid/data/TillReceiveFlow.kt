package ir.vil3ntec.tohid.data

import ir.vil3ntec.tohid.core.debt.TillReceive
import ir.vil3ntec.tohid.money

/**
 *  POS-39 — دریافتِ قسط/بدهی از صندوق. ثبت فقط با `DebtEntry.record` (`TillReceivePref.record`، همان راهِ #567):
 *  زیرِ قفلِ موتور روی دفترِ همین لحظهٔ **دیسک** — نه `d` ِ صفحه و نه حافظهٔ همان نمونهٔ `ShopStore`، که پس از
 *  Pull ِ کارگرِ همگام‌سازی کهنه است. همان ردیفِ `receive` ِ قرض‌داران ([entry] ⇐ `DebtorEngine.addTransaction(RECEIVE)`،
 *  سابقهٔ `customer_payment`، پس دخلِ شیفت آن را مثلِ هر رسیدِ قرض می‌شمارد)، با نگهبان‌های `core` روی **همان** دفتر ([check]):
 *    • مبلغ با `TillReceive.judge`؛ پیش‌پرداخت فقط با `prepayConfirmed`.
 *    • کلیدِ یکتا = شناسهٔ همان ردیف؛ کلیدی که در دفتر هست ⇒ `DebtEntry.Result.Ok(again = true)`، بی نوشتنِ دوم.
 */
object TillReceiveFlow {

  fun balanceMinor(d: ShopData, debtorId: String): Long = PayFlow.minor(ShopStore.debt(d, debtorId))

  fun message(p: TillReceive.Problem): String = when (p) {
    TillReceive.Problem.NOT_A_NUMBER -> "مبلغ را درست بنویسید"
    TillReceive.Problem.NOT_POSITIVE -> "مبلغ باید بیشتر از صفر باشد"
    TillReceive.Problem.TOO_LARGE -> "مبلغ بیش از حد است"
  }

  /** پیش‌پرداختِ بی تأیید روی دفترِ همین لحظه: پنجره بدهیِ دفترِ صفحه را نشان داده بود و رسیدی که Pull آورد آن را کم کرد */
  fun prepayMessage(balanceMinor: Long): String =
    "بدهیِ همین لحظهٔ مشتری ${money(PayFlow.major(TillReceive.suggestedMinor(balanceMinor)))} افغانی است و این مبلغ بیش از آن است؛ " +
      "بی تأییدِ پیش‌پرداخت ثبت نشد."

  /** همان ردیفِ `receive` ِ قرض‌داران؛ مبلغ را [check] روی دفترِ همین لحظه می‌سنجد (خالی ⇒ ردِ «درست بنویسید») */
  fun entry(debtorId: String, amountMinor: Long?, notes: String, today: String): DebtEntry.Entry =
    DebtEntry.Entry(debtorId, DebtorEngine.Kind.RECEIVE, PayFlow.major(amountMinor ?: 0L), today, notes.trim().ifEmpty { "دریافت از صندوق" })

  /**
   *  قاعدهٔ صندوق روی دفترِ همین لحظه [cur] — `DebtEntry` آن را زیرِ قفل و پس از «همین کلید هست؟» می‌دواند.
   *  `null` ⇒ پذیرفته؛ وگرنه پیامِ رد و هیچ نوشتنی.
   */
  fun check(cur: ShopData, debtorId: String, amountMinor: Long?, prepayConfirmed: Boolean): String? {
    if (cur.debtors.none { it.id == debtorId }) return "مشتری پیدا نشد"
    val balance = balanceMinor(cur, debtorId)
    return when (val v = TillReceive.judge(balance, amountMinor)) {
      is TillReceive.Verdict.Bad -> message(v.problem)
      is TillReceive.Verdict.Ok -> if (TillReceive.needsConfirm(v) && !prepayConfirmed) prepayMessage(balance) else null
    }
  }
}
