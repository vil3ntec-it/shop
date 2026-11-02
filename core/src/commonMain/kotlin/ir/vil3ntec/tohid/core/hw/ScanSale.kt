package ir.vil3ntec.tohid.core.hw

import ir.vil3ntec.tohid.core.audit.HmacSha256
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
 *  ([CommandCard.PREFIX])، نسلِ کارت و امضای HMAC-SHA256 با رازِ تصادفیِ همین دستگاه را دارد؛ پس هرگز با بارکدِ کالا
 *  یکی نمی‌شود، با چاپِ «شبیهِ» کارت ساخته نمی‌شود و کارتِ دستگاه یا دکانِ دیگر این‌جا فرمان نیست. «کارتِ تازه» راز و
 *  نسل را عوض می‌کند و همهٔ کارت‌های پیشین باطل می‌شوند. کدِ فضای‌نامِ فرمان که درست نیست **هرگز کالا نیست** و فقط
 *  نادیده و یادداشت می‌شود. این پرونده منطق است: شکستنِ دنبالهٔ اسکن به فاکتورها ([CommandSequence]) و قاعدهٔ «ثبت یا
 *  نگه‌داشتن» ([ScanSaleRule])؛ ثبت از `data/ScanCashSale.kt` با همان `SalesEngine.record` ِ صندوق.
 */
object CommandCard {
  /** فضای‌نامِ بارکدهای فرمان — کالایی با این آغاز ثبت نمی‌شود (`isCommandLike`) */
  const val PREFIX = "TOHID-CMD-"
  /** رقم‌های امضا (۴۰ بیت؛ حدسش فقط با اسکن روی همان دستگاه و هر خطا یادداشت می‌شود) */
  const val MAC_DIGITS = 12
  /** بایت‌های رازِ تازه */
  const val SECRET_BYTES = 32

  enum class Command(val word: String) { NEW("NEW"), CASH("CASH"), UNDO("UNDO") }

  /** رازِ همین دستگاه (hex) و نسلِ کارت؛ «کارتِ تازه» هر دو را عوض می‌کند */
  data class Key(val secret: String, val gen: Int) {
    val usable: Boolean get() = gen >= 1 && (HmacSha256.bytesOfHex(secret)?.size ?: 0) >= 16
  }

  /** نتیجهٔ خواندنِ یک کد */
  enum class Verdict { OK, NOT_COMMAND, FORGED, OLD_CARD }

  data class Read(val verdict: Verdict, val command: Command? = null)

  /** رازِ تازه از [random] (بایت‌های تصادفیِ پلتفرم) با نسلِ [gen] */
  fun newKey(random: ByteArray, gen: Int): Key = Key(Sha256.hex(random), gen.coerceAtLeast(1))

  /** کارتِ تازه پس از [old]: رازِ تازه، نسلِ بعدی */
  fun rotate(old: Key?, random: ByteArray): Key = newKey(random, (old?.gen ?: 0) + 1)

  private fun mac(cmd: Command, key: Key): String {
    val raw = HmacSha256.mac(HmacSha256.bytesOfHex(key.secret)!!, "tohid-command-card|v1|${key.gen}|${cmd.word}".encodeToByteArray())
    var n = 0L
    for (i in 0 until 8) n = (n shl 8) or (raw[i].toLong() and 0xff)
    return ((n ushr 1) % 1_000_000_000_000L).toString().padStart(MAC_DIGITS, '0')
  }

  /** محتوای بارکدِ یک فرمان برای این راز و نسل */
  fun payload(cmd: Command, key: Key): String {
    require(key.usable) { "command card key" }
    return PREFIX + cmd.word + "-" + key.gen + "-" + mac(cmd, key)
  }

  /** این کد در فضای‌نامِ فرمان است (درست یا نادرست) — هرگز کالا حساب نمی‌شود */
  fun isCommandLike(code: String): Boolean = code.trim().uppercase().startsWith(PREFIX)

  /** هم‌سنجیِ بی میان‌بُر (زمانِ ثابت برای درازای برابر) */
  private fun same(a: String, b: String): Boolean {
    if (a.length != b.length) return false
    var x = 0
    for (i in a.indices) x = x or (a[i].code xor b[i].code)
    return x == 0
  }

  /**
   *  کدِ کالا ⇒ `NOT_COMMAND`؛ فرمانِ درستِ همین راز و نسل ⇒ `OK`؛ کارتِ نسلِ پیشین ⇒ `OLD_CARD`؛ هر چیزِ دیگرِ
   *  فضای‌نامِ فرمان (امضای نادرست، واژهٔ ناشناس، شکلِ بد، دستگاهِ بی راز) ⇒ `FORGED`.
   */
  fun read(code: String, key: Key?): Read {
    val c = code.trim().uppercase()
    if (!c.startsWith(PREFIX)) return Read(Verdict.NOT_COMMAND)
    val parts = c.removePrefix(PREFIX).split('-')
    if (parts.size != 3 || parts[2].length != MAC_DIGITS || !parts[2].all { it in '0'..'9' }) return Read(Verdict.FORGED)
    val cmd = Command.entries.firstOrNull { it.word == parts[0] } ?: return Read(Verdict.FORGED)
    val gen = parts[1].toIntOrNull() ?: return Read(Verdict.FORGED)
    if (key == null || !key.usable) return Read(Verdict.FORGED)
    if (gen in 1 until key.gen) return Read(Verdict.OLD_CARD)
    if (gen != key.gen) return Read(Verdict.FORGED)
    return if (same(parts[2], mac(cmd, key))) Read(Verdict.OK, cmd) else Read(Verdict.FORGED)
  }

  /** فرمانِ معتبرِ همین راز و نسل؛ کدِ کالا، امضای نادرست، کارتِ کهنه یا واژهٔ ناشناس ⇒ `null` */
  fun parse(code: String, key: Key?): Command? = read(code, key).command
}

/** یک فاکتورِ ساخته از دنبالهٔ اسکن */
data class ScanBatch(
  val items: List<QueuedScan>,
  /** با چه بسته شد: «نقد پرداخت شد»، «فاکتورِ تازه»، یا هنوز باز (تهِ دنباله) */
  val end: End,
  /** همهٔ اسکن‌های این فاکتور به ترتیب — کالاها، کالاهای حذف‌شده، کدهای نادیده و فرمانِ بستن — برای برداشتن از صف */
  val span: List<QueuedScan> = items,
) {
  /** `GAP`: میانِ دو کالا بیش از فاصلهٔ داده‌شده گذشت (بستنِ ضمنی، مثلِ «فاکتورِ تازه» بی پرداخت) */
  enum class End { CASH, NEW, GAP, OPEN }

  /** با «نقد پرداخت شد» بسته شد */
  val cashClosed: Boolean get() = end == End.CASH
  val first: QueuedScan get() = items.first()
  val last: QueuedScan get() = items.last()
  /** دست‌کم یک کالا از حافظهٔ بارکدخوان (دستگاه خاموش بود) */
  val fromMemory: Boolean get() = items.any { it.source == ScanSource.MEMORY }
  /** بیشترین فاصلهٔ دو کالای پیاپی (زمانِ رسیدن) */
  val maxGapMs: Long get() = items.zipWithNext { a, b -> b.atMs - a.atMs }.maxOrNull()?.coerceAtLeast(0) ?: 0
}

object CommandSequence {
  enum class Note { DOUBLE_CASH, EMPTY_UNDO, FOREIGN_CODE }

  /**
   *  @param stray اسکن‌هایی که به هیچ فاکتوری نرسیدند (نقدِ دوباره، حذف روی فاکتورِ خالی، کدِ نادیده، فرمانِ بی کالا)
   *    — بی اثر؛ برداشتنشان چیزی را عوض نمی‌کند
   */
  data class Result(val batches: List<ScanBatch>, val notes: List<Pair<Note, QueuedScan>>, val stray: List<QueuedScan> = emptyList())

  /**
   *  دنبالهٔ اسکن‌های فروش (به ترتیبِ رسیدن) ⇒ فاکتورها:
   *  - «فاکتورِ تازه»: فاکتورِ باز (اگر چیزی دارد) بی پرداخت بسته می‌شود و فاکتورِ تازه آغاز
   *  - «حذفِ آخرین کالا»: آخرین کالای فاکتورِ باز می‌رود؛ فاکتورِ خالی ⇒ یادداشت
   *  - «نقد پرداخت شد»: فاکتورِ باز «نقد» بسته می‌شود؛ پشتِ هم یا بی کالا ⇒ نادیده با یادداشت
   *  - کدِ فضای‌نامِ فرمان که فرمانِ این دستگاه نیست ⇒ نادیده با یادداشت، **هرگز کالا**
   *  - پایانِ دنباله با فاکتورِ باز ⇒ `OPEN` (شاید هنوز اسکن می‌شود؛ هرگز خودکار ثبت نمی‌شود)
   *  - [gapMs]: کالایی که بیش از این پس از کالای پیشینِ فاکتورِ باز رسید فاکتورِ تازه آغاز می‌کند و پیشین `GAP` (بی
   *    پرداخت) بسته می‌شود — اسکن‌های کهنهٔ مانده (انبارگردانی) هرگز به «نقد» ِ مشتریِ بعدی نمی‌چسبند. پیش‌فرض: بی مرز
   */
  fun split(scans: List<QueuedScan>, gapMs: Long = Long.MAX_VALUE, commandOf: (String) -> CommandCard.Command?): Result {
    val batches = ArrayList<ScanBatch>()
    val notes = ArrayList<Pair<Note, QueuedScan>>()
    val stray = ArrayList<QueuedScan>()
    val open = ArrayList<QueuedScan>()
    val span = ArrayList<QueuedScan>()
    fun close(end: ScanBatch.End) {
      if (open.isNotEmpty()) batches += ScanBatch(open.toList(), end, span.toList()) else stray += span
      open.clear()
      span.clear()
    }
    scans.forEach { s ->
      val c = commandOf(s.code)
      if (c == null && open.isNotEmpty() && !CommandCard.isCommandLike(s.code) && s.atMs - open.last().atMs > gapMs) close(ScanBatch.End.GAP)
      span += s
      when (c) {
        CommandCard.Command.NEW -> close(ScanBatch.End.NEW)
        CommandCard.Command.UNDO -> if (open.isEmpty()) notes += Note.EMPTY_UNDO to s else open.removeAt(open.lastIndex)
        CommandCard.Command.CASH -> if (open.isEmpty()) notes += Note.DOUBLE_CASH to s else close(ScanBatch.End.CASH)
        null -> if (CommandCard.isCommandLike(s.code)) notes += Note.FOREIGN_CODE to s else open += s
      }
    }
    close(ScanBatch.End.OPEN)
    return Result(batches, notes, stray)
  }
}

object ScanSaleRule {
  enum class Action { RECORD_CASH, HOLD }

  /** چرا خودکار ثبت نشد — یادداشتِ فاکتورِ نگه‌داشته */
  enum class Why { CARD_OFF, NOT_PAID, TILL_CLOSED, NO_PERMISSION, UNKNOWN_CODE, LONG_GAP }

  /** دو کالای پیاپیِ یک فاکتور بیش از این از هم دور ⇒ شاید اسکنِ کارِ دیگری (انبارگردانی) قاطی شده ⇒ نگه‌داشته */
  const val MAX_GAP_MS: Long = 10 * 60_000L

  /** یادداشتِ فاکتورِ ثبت‌شده — **داده** (همان در هر زبانِ برنامه)، تا گزارش‌ها از رویش بشمارند */
  const val NOTE_LIVE = "ثبت از اسکنر"
  const val NOTE_MEMORY = "ثبت از اسکنر — از حافظه"

  /** آنچه پیش از ثبتِ خودکار باید درست باشد */
  data class Gate(
    /** «کارتِ فرمان» روشن است (دستهٔ B؛ پیش‌فرض خاموش) */
    val cardOn: Boolean,
    /** صندوق/شیفتِ دکان باز است */
    val tillOpen: Boolean,
    /** کاربرِ واردشدهٔ صندوقِ همین دستگاه اجازهٔ فروش دارد */
    val canSell: Boolean,
  )

  /** `null` ⇒ ثبتِ نقدیِ خودکار؛ وگرنه نخستین دلیلِ نگه‌داشتن */
  fun why(batch: ScanBatch, gate: Gate, unknownCodes: Int): Why? = when {
    !gate.cardOn -> Why.CARD_OFF
    !batch.cashClosed -> Why.NOT_PAID
    !gate.tillOpen -> Why.TILL_CLOSED
    !gate.canSell -> Why.NO_PERMISSION
    unknownCodes > 0 -> Why.UNKNOWN_CODE
    batch.maxGapMs > MAX_GAP_MS -> Why.LONG_GAP
    else -> null
  }

  /**
   *  فقط وقتی همه درست است فروشِ نقد خودکار ثبت می‌شود: کارت روشن، فاکتور با «نقد» بسته، صندوق باز، اجازهٔ فروش،
   *  همهٔ کدها کالای شناخته و کالاها پشتِ هم. هر چیزِ دیگر ⇒ نگه‌داشته (راهِ الف). قرض و مشتری همیشه از راهِ الف است.
   */
  fun decide(batch: ScanBatch, gate: Gate, unknownCodes: Int): Action =
    if (why(batch, gate, unknownCodes) == null) Action.RECORD_CASH else Action.HOLD

  /** کلیدِ یکتای فاکتورِ ساخته از اسکن — همان فاکتور با واردکردنِ دوباره دوباره ساخته/ثبت نمی‌شود */
  fun key(batch: ScanBatch): String =
    "scan-" + Sha256.hexOf(Discovery.key(batch.first.device) + "|" + batch.first.key + "|" + batch.last.key + "|" + batch.items.size).take(20)

  fun note(batch: ScanBatch): String = if (batch.fromMemory) NOTE_MEMORY else NOTE_LIVE

  /** فاکتوری که از بارکدخوان ثبت شد (زنده یا از حافظه) */
  fun isScannerSale(note: String?): Boolean = note != null && note.startsWith(NOTE_LIVE)
}
