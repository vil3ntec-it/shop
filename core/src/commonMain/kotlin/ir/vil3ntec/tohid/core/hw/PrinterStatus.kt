package ir.vil3ntec.tohid.core.hw

/**
 *  وضعیتِ چاپگر از پاسخ‌های `DLE EOT n` (HW-02 هشدارِ خاموش/بی‌کاغذ، HW-11 بررسیِ سلامت).
 *
 *  ── چهار پرسش، هر کدام یک بایت پاسخ ──────────────────────────────────
 *    `DLE EOT 1` وضعیتِ چاپگر   · بیتِ ۳ = آفلاین
 *    `DLE EOT 2` علتِ آفلاین     · بیتِ ۲ = درِ چاپگر باز · بیتِ ۵ = ایستاده برای تمام شدنِ کاغذ · بیتِ ۶ = خطا
 *    `DLE EOT 3` علتِ خطا        · بیتِ ۳ = تیغهٔ برش · بیتِ ۵ = خطای بی‌بازگشت · بیتِ ۶ = خطای خودبه‌خود‌برطرف‌شونده (مثلاً داغی)
 *    `DLE EOT 4` حسگرِ کاغذ      · بیت‌های ۲/۳ = رو به پایان · بیت‌های ۵/۶ = تمام (همان `EscPos.parsePaperStatus`)
 *  در هر چهار پاسخ بیت‌های ۱ و ۴ همیشه ۱ و بیت‌های ۰ و ۷ همیشه ۰اند؛ بایتی جز این پاسخِ وضعیت نیست.
 *
 *  ── چرا یکی‌یکی ─────────────────────────────────────────────────────
 *  بعضی چاپگرها به بعضی پرسش‌ها پاسخ نمی‌دهند. اگر هر چهار پرسش با هم فرستاده شود و یکی بی‌پاسخ
 *  بماند، بایتِ پرسشِ بعدی به‌جای او خوانده می‌شود و «درِ باز» با «کاغذِ تمام» جابه‌جا می‌شود.
 *  پس `Probe` هر بار فقط یک پرسش می‌دهد و پاسخش را می‌گیرد؛ بی‌پاسخ ماندنِ نخستین پرسش یعنی
 *  «این چاپگر وضعیت نمی‌گوید» و چیزی بیشتر پرسیده نمی‌شود.
 *
 *  خالص است: خواندن و نوشتنِ سوکت/بلوتوث با صدازننده (`ThermalPrinter.probe`) و روی چاپگرِ
 *  واقعی آزموده نشده است.
 */
object PrinterStatus {

  /** پرسشِ `DLE EOT n` */
  fun query(n: Int): ByteArray {
    require(n in 1..4) { "DLE EOT فقط ۱ تا ۴" }
    return byteArrayOf(0x10, 0x04, n.toByte())
  }

  /** پاسخِ معتبرِ وضعیت؟ (بیت‌های ثابتِ ۰۱۰۰۱۰ در ۰، ۱، ۴ و ۷) */
  fun valid(b: Int): Boolean = (b and 0xFF and 0x93) == 0x12

  /** آنچه از پاسخ‌ها درآمد؛ `null` یعنی آن پرسش پاسخ نگرفت */
  data class State(
    /** هیچ پاسخِ معتبری نیامد — چاپگر وضعیت نمی‌گوید (هنوز ممکن است چاپ کند) */
    val silent: Boolean,
    val offline: Boolean? = null,
    val coverOpen: Boolean? = null,
    val stoppedForPaper: Boolean? = null,
    val error: Boolean? = null,
    val cutterError: Boolean? = null,
    val unrecoverable: Boolean? = null,
    val autoRecoverable: Boolean? = null,
    val paper: PaperStatus? = null,
  ) {
    val paperOut: Boolean get() = paper?.out == true || stoppedForPaper == true
  }

  /** نتیجه برای صفحه: چاپ ممکن است یا نه، و چه گفته شود */
  data class Verdict(val ok: Boolean, val message: String, val warn: Boolean = false)

  /**
   *  گفت‌وگوی پرسش و پاسخ. صدازننده:
   *  ```
   *  val p = PrinterStatus.Probe()
   *  while (true) { val q = p.next() ?: break; write(q); p.answer(readOneByteOrMinus1()) }
   *  val state = p.state()
   *  ```
   */
  class Probe {
    private var step = 1
    private var done = false
    private var s1: Int? = null
    private var s2: Int? = null
    private var s3: Int? = null
    private var s4: Int? = null

    /** پرسشِ بعدی، یا `null` اگر بس است */
    fun next(): ByteArray? = if (done) null else query(step)

    /** پاسخِ پرسشِ جاری: بایت (۰ تا ۲۵۵) یا `-1` اگر در مهلت چیزی نیامد */
    fun answer(b: Int) {
      if (done) return
      val ok = b >= 0 && valid(b)
      val v = if (ok) b and 0xFF else null
      when (step) {
        1 -> {
          s1 = v
          //  نخستین پرسش بی‌پاسخ ⇒ این چاپگر وضعیت نمی‌گوید؛ بیشتر نپرس
          if (v == null) { done = true; return }
          //  آنلاین ⇒ علتِ آفلاین و خطا لازم نیست؛ فقط کاغذ
          step = if (v and 0x08 != 0) 2 else 4
        }
        2 -> {
          s2 = v
          step = if (v != null && v and 0x40 != 0) 3 else 4
        }
        3 -> { s3 = v; step = 4 }
        4 -> { s4 = v; done = true }
      }
    }

    fun state(): State {
      val a = s1 ?: return State(silent = true, paper = s4?.let(EscPos::parsePaperStatus))
      return State(
        silent = false,
        offline = a and 0x08 != 0,
        coverOpen = s2?.let { it and 0x04 != 0 },
        stoppedForPaper = s2?.let { it and 0x20 != 0 },
        error = s2?.let { it and 0x40 != 0 },
        cutterError = s3?.let { it and 0x08 != 0 },
        unrecoverable = s3?.let { it and 0x20 != 0 },
        autoRecoverable = s3?.let { it and 0x40 != 0 },
        paper = s4?.let(EscPos::parsePaperStatus),
      )
    }
  }

  /**
   *  پیامِ کوتاه به ترتیبِ اهمیت. `where` نامِ اتصال است (برای «چاپگر وضعیت را نگفت»).
   *  «کاغذ رو به پایان» چاپ را نمی‌بندد ولی هشدار است (`warn`).
   */
  fun verdict(s: State, where: String = ""): Verdict {
    val at = if (where.isEmpty()) "" else " ($where)"
    return when {
      s.silent -> Verdict(true, "وصل شد$at — چاپگر وضعیتش را نگفت", warn = true)
      s.coverOpen == true -> Verdict(false, "درِ چاپگر باز است — ببندید")
      s.paperOut -> Verdict(false, "کاغذ تمام شده است")
      s.cutterError == true -> Verdict(false, "تیغهٔ برش گیر کرده است — کاغذ را آزاد کنید و چاپگر را خاموش و روشن کنید")
      s.unrecoverable == true -> Verdict(false, "خطای چاپگر — خاموش و روشنش کنید")
      s.autoRecoverable == true -> Verdict(false, "چاپگر موقتاً ایستاده (مثلاً داغ شده) — کمی صبر کنید")
      s.offline == true -> Verdict(false, "چاپگر آماده نیست (آفلاین)")
      s.paper?.nearEnd == true -> Verdict(true, "وصل است — کاغذ رو به پایان است", warn = true)
      s.paper == null -> Verdict(true, "وصل است — وضعیتِ کاغذ گفته نشد", warn = true)
      else -> Verdict(true, "وصل است و کاغذ دارد")
    }
  }
}
