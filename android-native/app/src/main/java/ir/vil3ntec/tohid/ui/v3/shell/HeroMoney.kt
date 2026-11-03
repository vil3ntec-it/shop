package ir.vil3ntec.tohid.ui.v3.shell

import ir.vil3ntec.tohid.core.money.MoneyInput
import ir.vil3ntec.tohid.core.pay.CashKeys
import ir.vil3ntec.tohid.core.pay.ShiftCore
import ir.vil3ntec.tohid.core.safe.SafeCore
import ir.vil3ntec.tohid.data.AuditEntry
import ir.vil3ntec.tohid.data.CashShift
import ir.vil3ntec.tohid.data.PayFlow
import ir.vil3ntec.tohid.data.SafeBook
import ir.vil3ntec.tohid.data.ShopData
import ir.vil3ntec.tohid.ui.v3.sales.drawer.DrawerNotes

/**
 *  دکمهٔ «+» ِ کادرِ بالای خانه — **راهِ تازهٔ پول نیست**؛ فقط همان دو عملیاتِ امروز را با یک برگهٔ کوچک صدا می‌زند:
 *
 *  • صندوق: «ورود/خروجِ نقد» ِ برگهٔ صندوق (PAY-12) — `PayFlow.moveRow` (سنجشِ هستهٔ `ShiftCore`: صندوقِ باز، دلیل،
 *    «دیگر» با یادداشت، خروج نه بیش از نقدِ دخل) و همان یادداشتِ سابقه (`DrawerNotes.move`).
 *  • گاوصندوق: «ورود»/«خروج» ِ پنجرهٔ گاوصندوق (PAY-18) — `SafeBook.deposit`/`withdraw` (سنجشِ `SafeCore`: دلیل،
 *    «دیگر» با یادداشت، برداشت نه بیش از مانده) که صفحه از راهِ `SafeBookPref.record` ثبت می‌کند.
 *
 *  خالص است (بی Context و ساعت): شناسه، زمان و نام را صفحه می‌دهد. شناسه هنگامِ باز شدنِ برگه ساخته می‌شود تا دو
 *  ضربه یک ثبت باشد (`HeroMoneySheetAndroid`).
 */
object HeroMoney {

  enum class Dir { ADD, TAKE }

  /** مبلغ خالی */
  const val AMOUNT_REQUIRED = "AMOUNT_REQUIRED"

  /** همان کدِ هسته‌ها */
  const val INVALID_AMOUNT = "INVALID_AMOUNT"

  /** همان کدِ `PayFlow` برای «صندوقِ بازی نیست» */
  const val SHIFT_CLOSED = "SHIFT_CLOSED"

  fun tillType(dir: Dir): String = if (dir == Dir.ADD) "cash_in" else "cash_out"

  fun safeKind(dir: Dir): String = if (dir == Dir.ADD) SafeCore.IN else SafeCore.OUT

  /** دلیل‌های همان برگه‌های امروز، به همان ترتیب؛ نخستین پیش‌فرض است */
  fun reasons(page: HomeHero.Page, dir: Dir): List<String> = when (page) {
    HomeHero.Page.TILL -> ShiftCore.MOVE_REASONS[tillType(dir)].orEmpty()
    HomeHero.Page.SAFE -> SafeCore.REASONS[safeKind(dir)].orEmpty()
  }

  sealed interface Amount {
    data class Ok(val minor: Long) : Amount
    data class Bad(val code: String) : Amount
  }

  /** مبلغِ کادر ⇒ پول (`CashKeys.cashMinor`، همان خواندنِ پنجرهٔ گاوصندوق)؛ خالی یا صفر رد، بیش از سقف رد */
  fun amount(text: String): Amount {
    val t = ir.vil3ntec.tohid.latinDigits(text).replace(",", "").replace("٬", "").trim()
    if (t.isEmpty()) return Amount.Bad(AMOUNT_REQUIRED)
    val m = CashKeys.cashMinor(t) ?: return Amount.Bad(INVALID_AMOUNT)
    if (m <= 0L || m > SafeCore.MAX_AMOUNT) return Amount.Bad(INVALID_AMOUNT)
    return Amount.Ok(m)
  }

  /**
   *  ردیفِ سابقهٔ صندوق، درست همان که برگهٔ «ورود/خروجِ نقد» (`DrawerMoveRoute`) می‌سازد: `PayFlow.moveRow` روی
   *  صندوقِ بازِ همین دفتر، سپس `date` = امروز و `notes` = `DrawerNotes.move`. ثبت با `ShopStore.appendAudits`.
   */
  fun tillRow(d: ShopData, dir: Dir, minor: Long, reason: String?, note: String, me: String, now: Long, today: String, id: String): PayFlow.Outcome<AuditEntry> {
    val shift = CashShift.current(d) ?: return PayFlow.Outcome.Bad(PayFlow.Problem(SHIFT_CLOSED))
    val text = MoneyInput.ofMinor(minor)?.takeIf { minor > 0L } ?: return PayFlow.Outcome.Bad(PayFlow.Problem(INVALID_AMOUNT))
    val move = PayFlow.Move(tillType(dir), text, reason, note, by = me)
    return when (val r = PayFlow.moveRow(d, shift.open, move, now) { id }) {
      is PayFlow.Outcome.Bad -> r
      is PayFlow.Outcome.Ok -> PayFlow.Outcome.Ok(
        r.value.copy(date = today, notes = DrawerNotes.move(move.type, r.value.reason, r.value.amount, r.value.note, me)),
      )
    }
  }

  /** نتیجهٔ ثبتِ صندوق روی دفترِ همان لحظه: ردیف‌های تازه (یکی یا هیچ)، خطای هسته، یا «همین شناسه پیش‌تر نشسته بود» */
  data class TillWrite(val rows: List<AuditEntry>, val problem: PayFlow.Problem?, val again: Boolean)

  /**
   *  همان `tillRow` برای `ShopStore.appendAudits`: ردیفی با همین شناسه در دفتر هست ⇒ هیچ (دو ضربه یا تلاشِ دوباره
   *  یک ثبت است)؛ ردِ هسته ⇒ هیچ و خطا.
   */
  fun tillWrite(cur: ShopData, dir: Dir, minor: Long, reason: String?, note: String, me: String, now: Long, today: String, id: String): TillWrite {
    if (cur.auditLog.any { it.id == id }) return TillWrite(emptyList(), null, again = true)
    return when (val r = tillRow(cur, dir, minor, reason, note, me, now, today, id)) {
      is PayFlow.Outcome.Ok -> TillWrite(listOf(r.value), null, again = false)
      is PayFlow.Outcome.Bad -> TillWrite(emptyList(), r.problem, again = false)
    }
  }

  /** ردیفِ گاوصندوق با همان `SafeBook.deposit`/`withdraw` ِ پنجرهٔ گاوصندوق (یادداشتِ خالی ⇒ بی یادداشت) */
  fun safeEntry(d: ShopData, dir: Dir, minor: Long, reason: String, note: String, me: String, now: Long, id: String): SafeBook.Result {
    val text = note.trim().ifEmpty { null }
    return if (dir == Dir.ADD) SafeBook.deposit(d, minor, reason, text, me, now) { id }
    else SafeBook.withdraw(d, minor, reason, text, me, now) { id }
  }
}
