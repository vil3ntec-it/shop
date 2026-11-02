package ir.vil3ntec.tohid.core.hw

import ir.vil3ntec.tohid.core.audit.Sha256

/**
 *  «پول پس از اسکنِ پس‌زمینه چطور ثبت می‌شود؟» (پرسشِ صاحب‌کار ۱۴۰۵/۰۸/۱۱).
 *
 *  ── الف) پیش‌فرض و امن ─────────────────────────────────────────────
 *  اسکن‌هایی که برای فروش در صف ماندند **فاکتورِ نگه‌داشته** می‌شوند (همان POS-12)؛ همان کد N بار ⇒ مقدارِ N. با باز
 *  شدنِ برنامه جمعش دیده می‌شود و فقط با زدنِ «پرداخت» همان پنلِ پرداختِ همیشه باز می‌شود و فروش از راهِ عادیِ
 *  `SalesEngine.record` ثبت می‌شود. بی آن زدن **هیچ** فروشی ثبت نمی‌شود.
 *
 *  ── ب) «کارتِ فرمان» (دستهٔ B، پیش‌فرض خاموش) ──────────────────────
 *  برگه‌ای با سه بارکدِ فرمان: «فاکتورِ تازه»، «نقد پرداخت شد»، «حذفِ آخرین کالا». محتوای هر بارکد فضای‌نامِ خودش
 *  ([CommandCard.PREFIX]) و امضای کوتاهِ همان دکان را دارد؛ پس هرگز با بارکدِ کالا یکی نمی‌شود و کارتِ دکانِ دیگر
 *  این‌جا فرمان نیست. این پرونده فقط منطق است: شکستنِ دنبالهٔ اسکن به فاکتورها ([CommandSequence]) و قاعدهٔ
 *  «ثبت یا نگه‌داشتن» ([ScanSaleRule]). تا کارت روشن نشده، هر فاکتور نگه‌داشته می‌شود.
 */
object CommandCard {
  /** فضای‌نامِ بارکدهای فرمان — کالایی با این آغاز ثبت نمی‌شود (`isCommandLike`) */
  const val PREFIX = "TOHID-CMD-"
  const val SIG_LEN = 8

  enum class Command(val word: String) { NEW("NEW"), CASH("CASH"), UNDO("UNDO") }

  private fun sig(word: String, shopKey: String): String = Sha256.hexOf("tohid-command-card|$shopKey|$word").take(SIG_LEN).uppercase()

  /** محتوای بارکدِ یک فرمان برای این دکان */
  fun payload(cmd: Command, shopKey: String): String = PREFIX + cmd.word + "-" + sig(cmd.word, shopKey)

  /** این کد در فضای‌نامِ فرمان است (درست یا نادرست) — هرگز کالا حساب نمی‌شود */
  fun isCommandLike(code: String): Boolean = code.trim().uppercase().startsWith(PREFIX)

  /** فرمانِ معتبرِ همین دکان؛ کدِ کالا، امضای نادرست یا واژهٔ ناشناس ⇒ `null` */
  fun parse(code: String, shopKey: String): Command? {
    val c = code.trim().uppercase()
    if (!c.startsWith(PREFIX)) return null
    val rest = c.removePrefix(PREFIX)
    val word = rest.substringBefore('-')
    val cmd = Command.entries.firstOrNull { it.word == word } ?: return null
    return if (rest.substringAfter('-', "") == sig(cmd.word, shopKey)) cmd else null
  }
}

/** یک فاکتورِ ساخته از دنبالهٔ اسکن */
data class ScanBatch(
  val items: List<QueuedScan>,
  /** با «نقد پرداخت شد» بسته شد */
  val cashClosed: Boolean,
) {
  val first: QueuedScan get() = items.first()
  val last: QueuedScan get() = items.last()
}

object CommandSequence {
  enum class Note { DOUBLE_CASH, EMPTY_UNDO, FOREIGN_CODE }

  data class Result(val batches: List<ScanBatch>, val notes: List<Pair<Note, QueuedScan>>)

  /**
   *  دنبالهٔ اسکن‌های فروش (به ترتیبِ رسیدن) ⇒ فاکتورها:
   *  - «فاکتورِ تازه»: فاکتورِ باز (اگر چیزی دارد) بی پرداخت بسته می‌شود و فاکتورِ تازه آغاز
   *  - «حذفِ آخرین کالا»: آخرین کالای فاکتورِ باز می‌رود؛ فاکتورِ خالی ⇒ یادداشت
   *  - «نقد پرداخت شد»: فاکتورِ باز «نقد» بسته می‌شود؛ پشتِ هم یا بی کالا ⇒ نادیده با یادداشت
   *  - کدِ فضای‌نامِ فرمان که فرمانِ این دکان نیست ⇒ نادیده با یادداشت، **هرگز کالا**
   *  - پایانِ دنباله با فاکتورِ باز ⇒ بی پرداخت (نگه‌داشته)
   */
  fun split(scans: List<QueuedScan>, commandOf: (String) -> CommandCard.Command?): Result {
    val batches = ArrayList<ScanBatch>()
    val notes = ArrayList<Pair<CommandSequence.Note, QueuedScan>>()
    val open = ArrayList<QueuedScan>()
    fun close(cash: Boolean) { if (open.isNotEmpty()) { batches += ScanBatch(open.toList(), cash); open.clear() } }
    scans.forEach { s ->
      when (commandOf(s.code)) {
        CommandCard.Command.NEW -> close(false)
        CommandCard.Command.UNDO -> if (open.isEmpty()) notes += Note.EMPTY_UNDO to s else open.removeAt(open.lastIndex)
        CommandCard.Command.CASH -> if (open.isEmpty()) notes += Note.DOUBLE_CASH to s else close(true)
        null -> if (CommandCard.isCommandLike(s.code)) notes += Note.FOREIGN_CODE to s else open += s
      }
    }
    close(false)
    return Result(batches, notes)
  }
}

object ScanSaleRule {
  enum class Action { RECORD_CASH, HOLD }

  /** آنچه پیش از ثبتِ خودکار باید درست باشد */
  data class Gate(
    /** «کارتِ فرمان» روشن است (دستهٔ B؛ پیش‌فرض خاموش) */
    val cardOn: Boolean,
    /** صندوق/شیفتِ همین دستگاه باز است */
    val tillOpen: Boolean,
    /** کاربرِ واردشدهٔ صندوق اجازهٔ فروش دارد */
    val canSell: Boolean,
  )

  /**
   *  فقط وقتی همه درست است فروشِ نقد خودکار ثبت می‌شود: کارت روشن، فاکتور با «نقد» بسته، صندوق باز، اجازهٔ فروش،
   *  همهٔ کدها کالای شناخته. هر چیزِ دیگر ⇒ نگه‌داشته (راهِ الف). قرض و مشتری همیشه از راهِ الف است.
   */
  fun decide(batch: ScanBatch, gate: Gate, unknownCodes: Int): Action =
    if (gate.cardOn && batch.cashClosed && gate.tillOpen && gate.canSell && unknownCodes == 0) Action.RECORD_CASH else Action.HOLD

  /** کلیدِ یکتای فاکتورِ ساخته از اسکن — همان فاکتور با واردکردنِ دوباره دوباره ساخته/ثبت نمی‌شود */
  fun key(batch: ScanBatch): String =
    "scan-" + Sha256.hexOf(Discovery.key(batch.first.device) + "|" + batch.first.key + "|" + batch.last.key + "|" + batch.items.size).take(20)
}
