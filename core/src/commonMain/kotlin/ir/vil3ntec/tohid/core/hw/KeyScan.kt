package ir.vil3ntec.tohid.core.hw

/**
 *  HW-05 — بارکدخوانِ USB/بلوتوث در حالتِ صفحه‌کلید، و سنجشِ سلامتش (HW-11).
 *
 *  بارکدخوان نویسه‌ها را تند پشتِ هم «تایپ» می‌کند و Enter می‌زند؛ آدم کندتر است. این‌جا:
 *    • **آستانهٔ قابلِ تنظیم** (`maxGapMs`): بیشترین فاصلهٔ دو نویسهٔ یک اسکن. پیش‌فرض ۵۰ میلی‌ثانیه
 *      (همان `HidScan`ِ پیشین)؛ بارکدخوانِ بلوتوثیِ کند تا ۳۰۰.
 *    • **پیشوند و پسوند** (`prefix`، `suffix`): نویسه‌هایی که خودِ بارکدخوان دورِ کد می‌گذارد. از کد
 *      برداشته می‌شوند. کدِ پیشونددار حتی اگر کند برسد (بلوتوثِ پرتأخیر) اسکن است، تا `PREFIXED_WINDOW_MS`.
 *      پسوندِ تنظیم‌شده خودش پایانِ اسکن است (بارکدخوانی که Enter نمی‌زند).
 *    • **مستقل از چیدمان**: نویسه از کلیدِ فیزیکی می‌آید (`ScanKeys`، F0-06) و این‌جا فقط نویسهٔ لاتین می‌رسد.
 *    • **هرگز در کادرِ قیمت یا تعداد**: `FieldScanGuard` مقدارِ کادر را پیش از هجومِ نویسه‌ها نگه می‌دارد و
 *      وقتی معلوم شد اسکن بود، همان را برمی‌گرداند و کد را جدا تحویل می‌دهد.
 *
 *  خالص است: زمان از بیرون می‌آید تا بی بارکدخوان سنجیده شود. روی بارکدخوانِ واقعی آزموده نشده.
 */
data class ScanConfig(
  val maxGapMs: Long = DEFAULT_GAP_MS,
  val minLength: Int = DEFAULT_MIN_LENGTH,
  val prefix: String = "",
  val suffix: String = "",
) {
  /** مقدارِ امن: آستانه در بازه، فقط نویسهٔ دیدنیِ ASCII در پیشوند/پسوند، هر کدام تا ۸ نویسه */
  fun normalized(): ScanConfig = ScanConfig(
    maxGapMs = maxGapMs.coerceIn(MIN_GAP_MS, MAX_GAP_MS),
    minLength = minLength.coerceIn(1, 64),
    prefix = clean(prefix),
    suffix = clean(suffix),
  )

  companion object {
    const val DEFAULT_GAP_MS = 50L
    const val DEFAULT_MIN_LENGTH = 4
    const val MIN_GAP_MS = 10L
    const val MAX_GAP_MS = 300L
    /** کدِ پیشونددار تا این مدت از نویسهٔ اول اسکن شمرده می‌شود، هر قدر هم کند */
    const val PREFIXED_WINDOW_MS = 3_000L

    private fun clean(s: String) = s.filter { it.code in 33..126 }.take(8)
  }
}

/** همان رفتارِ `HidScan`، با آستانه، پیشوند و پسوندِ تنظیم‌شده */
class KeyScan(config: ScanConfig = ScanConfig()) {
  val config: ScanConfig = config.normalized()
  private val buf = StringBuilder()
  private var lastAt = 0L
  private var startAt = 0L

  private fun prefixed(): Boolean = config.prefix.isNotEmpty() && buf.startsWith(config.prefix)
  private fun withinPrefixWindow(now: Long) = prefixed() && now - startAt <= ScanConfig.PREFIXED_WINDOW_MS

  /** نویسهٔ بعدی اسکنِ تازه‌ای را آغاز می‌کند؟ (آنچه در بافر است کهنه یا تایپِ آدم بوده) */
  fun startsNew(now: Long): Boolean = buf.isEmpty() || (now - lastAt > config.maxGapMs && !withinPrefixWindow(now))

  /**
   *  یک نویسه رسید. اگر پسوندِ تنظیم‌شده اسکن را تمام کرد، کد (بی پیشوند و پسوند)؛ وگرنه `null`.
   */
  fun feed(char: Char, now: Long): String? {
    if (startsNew(now)) { buf.setLength(0); startAt = now }
    lastAt = now
    buf.append(char)
    val sfx = config.suffix
    if (sfx.isNotEmpty() && buf.length > sfx.length && buf.endsWith(sfx)) {
      val raw = buf.toString()
      buf.setLength(0)
      return strip(raw)
    }
    return null
  }

  /** Enter رسید: کدِ کامل اگر تند (یا پیشونددار در مهلت) و به‌قدرِ کافی بلند بود؛ وگرنه `null` */
  fun submit(now: Long): String? {
    val raw = buf.toString()
    val ok = raw.isNotEmpty() && (now - lastAt <= config.maxGapMs || withinPrefixWindow(now))
    buf.setLength(0)
    lastAt = now
    return if (ok) strip(raw) else null
  }

  private fun strip(raw: String): String? {
    var s = raw
    if (config.prefix.isNotEmpty() && s.startsWith(config.prefix)) s = s.substring(config.prefix.length)
    if (config.suffix.isNotEmpty() && s.endsWith(config.suffix)) s = s.substring(0, s.length - config.suffix.length)
    return s.takeIf { it.length >= config.minLength }
  }

  fun reset() { buf.setLength(0); lastAt = 0; startAt = 0 }

  /** وسطِ یک اسکن؟ — صفحه با این تصمیم می‌گیرد نویسه را بخورد یا به کادر بدهد */
  val busy: Boolean get() = buf.isNotEmpty()
}

/**
 *  نگهبانِ کادرِ عدد (قیمت، تعداد، مبلغ): نخستین نویسهٔ هر هجوم مقدارِ کادر را **پیش از** خودش ثبت
 *  می‌کند؛ اگر هجوم اسکن از آب درآمد، `Hit.restore` همان مقدار است و `Hit.code` بارکد. تایپِ آدم
 *  (کند) هیچ‌وقت `Hit` نمی‌سازد و کادر دست نمی‌خورد.
 */
class FieldScanGuard(config: ScanConfig = ScanConfig()) {
  data class Hit(val code: String, val restore: String)

  private val scan = KeyScan(config)
  private var before = ""

  /** نویسه پیش از رسیدن به کادر؛ `value` متنِ کادر در همین لحظه (پیش از این نویسه) */
  fun key(char: Char, now: Long, value: String): Hit? {
    if (scan.startsNew(now)) before = value
    return scan.feed(char, now)?.let { Hit(it, before) }
  }

  /** Enter پیش از رسیدن به کادر */
  fun enter(now: Long): Hit? = scan.submit(now)?.let { Hit(it, before) }

  fun reset() = scan.reset()
}

/**
 *  HW-11 — سلامتِ بارکدخوان از یک اسکنِ آزمون: نویسه‌ها با زمانِ رسیدن و Enter (یا پسوند).
 *  می‌گوید با تنظیمِ فعلی اسکن شناخته می‌شود یا نه، و چه چیزی را عوض کنیم.
 */
object ScanCheck {
  data class Report(
    val ok: Boolean,
    val code: String,
    /** بیشترین فاصلهٔ دو نویسه (و نویسهٔ آخر تا Enter) */
    val maxGapMs: Long,
    val message: String,
    /** آستانهٔ پیشنهادی اگر کند بود (`null` یعنی همان خوب است) */
    val suggestGapMs: Long? = null,
  )

  /** `keys` = نویسه و زمانش؛ `enterAt` = زمانِ Enter (یا `null` اگر بارکدخوان Enter نزد) */
  fun evaluate(keys: List<Pair<Char, Long>>, enterAt: Long?, config: ScanConfig): Report {
    val c = config.normalized()
    if (keys.isEmpty()) return Report(false, "", 0, "چیزی نرسید — بارکدخوان وصل است و کادر انتخاب شده؟")
    val times = keys.map { it.second } + listOfNotNull(enterAt)
    val gap = times.zipWithNext { a, b -> b - a }.maxOrNull() ?: 0L
    val text = keys.map { it.first }.joinToString("")
    val k = KeyScan(c)
    var code: String? = null
    for ((ch, t) in keys) k.feed(ch, t)?.let { code = it }
    if (code == null && enterAt != null) code = k.submit(enterAt)
    if (code != null) return Report(true, code!!, gap, "درست است — «$code» با فاصلهٔ بیشینهٔ $gap میلی‌ثانیه")
    return when {
      enterAt == null && c.suffix.isEmpty() ->
        Report(false, text, gap, "بارکدخوان Enter نزد — آن را روی «پسوندِ Enter (CR)» بگذارید یا پسوندِ خودش را این‌جا بنویسید")
      c.prefix.isNotEmpty() && !text.startsWith(c.prefix) ->
        Report(false, text, gap, "پیشوندِ «${c.prefix}» نیامد — پیشوندِ بارکدخوان را درست کنید یا خالی بگذارید")
      gap > c.maxGapMs -> {
        val s = ((gap * 3 / 2 + 9) / 10 * 10).coerceIn(ScanConfig.MIN_GAP_MS, ScanConfig.MAX_GAP_MS)
        if (gap > ScanConfig.MAX_GAP_MS) Report(false, text, gap, "خیلی کند است ($gap میلی‌ثانیه) — با تایپِ آدم فرق ندارد؛ پیشوند بگذارید")
        else Report(false, text, gap, "کندتر از آستانه است ($gap میلی‌ثانیه) — آستانه را $s کنید", suggestGapMs = s)
      }
      else -> Report(false, text, gap, "کوتاه‌تر از ${c.minLength} نویسه است")
    }
  }
}
