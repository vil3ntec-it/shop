package ir.vil3ntec.tohid.core.backup

/**
 *  OPS-02 — صفحهٔ «عبارتِ بازیابی»: یک بار نشان بده، با نوشتنِ دوباره تأیید بگیر، چاپ کن.
 *
 *  ── گام‌ها ───────────────────────────────────────────────────────────
 *  [Step.INTRO] (چرا و چه وقت لازم است) ⇒ [Step.SHOW] (عبارت و «چاپ») ⇒ [Step.CONFIRM] (همان عبارت را بنویسید)
 *  ⇒ [Step.DONE]. از CONFIRM می‌شود یک گام برگشت و دوباره دید (هنوز چیزی نشسته)؛ پس از DONE یا [cancel] عبارت
 *  از حافظهٔ همین شیء پاک می‌شود و **هیچ راهی برای دیدنِ دوباره‌اش نیست** — نه در دیسک، نه در تنظیمات. عبارتِ
 *  تازه یعنی کلیدِ تازه برای نسخه‌های بعدی (نسخه‌های پیشین با عبارتِ خودشان باز می‌شوند؛ کلیدشان روی همین
 *  دستگاه می‌ماند — `SnapshotSeal`).
 *
 *  ── چرا تأییدِ نوشتنی ────────────────────────────────────────────────
 *  عبارتی که صاحبِ دکان هرگز ننوشت یعنی پشتیبانِ رمزداری که روزِ گم شدنِ گوشی باز نمی‌شود. پس رمز کردنِ
 *  نسخه‌ها فقط پس از [confirm] ِ درست روشن می‌شود ([takeConfirmed] یک بار شکلِ استاندارد را برای ساختنِ کلید
 *  می‌دهد). خواندن همان [RecoveryPhrase.normalize] است: کوچک/بزرگ، خط‌تیره، رقمِ فارسی، O⇐0.
 *
 *  خالص (commonMain)؛ صفحه‌ها (گوشیِ نسخهٔ ۳ و کامپیوتر، یک کد) فقط همین را می‌کشند. آزمون: `RecoveryPhraseFlowTest`.
 */
class RecoveryPhraseFlow(private val generate: () -> String = { RecoveryPhrase.generate() }) {

  enum class Step {
    INTRO, SHOW, CONFIRM, DONE, CANCELLED;

    companion object {
      /** عبارت روی صفحه است (نشان داده یا دارد نوشته می‌شود) ⇒ پنجره در برابرِ عکسِ صفحه امن (`Platform.SecureWindow`) */
      fun secret(s: Step): Boolean = s == SHOW || s == CONFIRM || s == DONE
    }
  }

  /** نتیجهٔ تأیید */
  enum class Check { OK, TYPO, MISMATCH, NOT_ASKED }

  var step: Step = Step.INTRO
    private set

  private var canonical: String? = null
  private var handedOut = false

  /** عبارت به شکلِ نمایش — **فقط** در [Step.SHOW] */
  val shown: String? get() = if (step == Step.SHOW) canonical?.let(RecoveryPhrase::format) else null

  /** پنج دستهٔ پنج‌تایی برای نمایشِ درشت — فقط در [Step.SHOW] */
  val groups: List<String> get() = shown?.split('-').orEmpty()

  /** INTRO ⇒ SHOW با عبارتِ تازه (یک بار در عمرِ این شیء) */
  fun start() {
    if (step != Step.INTRO) return
    canonical = RecoveryPhrase.normalize(generate()) ?: error("عبارتِ ساخته‌شده نادرست است")
    step = Step.SHOW
  }

  /** «نوشتم / چاپ کردم» ⇒ CONFIRM؛ عبارت دیگر دیده نمی‌شود */
  fun written() { if (step == Step.SHOW) step = Step.CONFIRM }

  /** از CONFIRM یک گام برگشت (پیش از تأیید) */
  fun back() { if (step == Step.CONFIRM) step = Step.SHOW }

  /** نوشتهٔ کاربر با همان عبارت سنجیده می‌شود */
  fun confirm(typed: String): Check {
    if (step != Step.CONFIRM) return Check.NOT_ASKED
    val norm = RecoveryPhrase.normalize(typed) ?: return Check.TYPO
    if (norm != canonical) return Check.MISMATCH
    step = Step.DONE
    return Check.OK
  }

  /** شکلِ استانداردِ عبارتِ تأییدشده — **یک بار**، برای ساختنِ کلید؛ بعد از حافظه پاک می‌شود */
  fun takeConfirmed(): String? {
    if (step != Step.DONE || handedOut) return null
    handedOut = true
    return canonical.also { canonical = null }
  }

  /** بستنِ صفحه پیش از پایان: عبارت دور ریخته می‌شود و هیچ چیزی روشن نمی‌شود */
  fun cancel() {
    canonical = null
    if (step != Step.DONE) step = Step.CANCELLED
  }

  /**
   *  برگهٔ چاپ — جدولِ همان «چاپِ گزارش» (A4، تک‌رنگ، `ReportPrint`)، یک ستونِ تمام‌عرض: سرِ جدول خودِ عبارت (درشت)،
   *  بعد دکان، تاریخ و دستورِ نگه‌داری و بازکردن (متن‌ها به زبانِ صفحه، `StrBackupSafety`). فقط در [Step.SHOW].
   */
  fun printRows(shop: String, date: String, keep: String, how: String): List<List<String>> {
    val phrase = shown ?: return emptyList()
    return buildList {
      add(listOf(phrase))
      if (shop.isNotBlank()) add(listOf(shop))
      add(listOf(date))
      add(listOf(keep))
      add(listOf(how))
    }
  }
}
