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
 *    • **بارکدخوانِ لرزان** (`steady`، فقط با «ابزارهای تازهٔ دستگاه»، پیش‌فرض خاموش): وقفهٔ کوتاهِ بلوتوث وسطِ
 *      کد آن را نمی‌شکند، تهِ اسکنِ شکسته هرگز کد نیست (کالای اشتباه بدتر از اسکنِ دوباره است) و پژواکِ
 *      بارکدخوان (همان کد در کمتر از `ECHO_MS`) با `ScanEcho` شناخته می‌شود.
 *
 *  خالص است: زمان از بیرون می‌آید تا بی بارکدخوان سنجیده شود. روی بارکدخوانِ واقعی آزموده نشده.
 */
data class ScanConfig(
  val maxGapMs: Long = DEFAULT_GAP_MS,
  val minLength: Int = DEFAULT_MIN_LENGTH,
  val prefix: String = "",
  val suffix: String = "",
  /**
   *  «بارکدخوانِ لرزان»: اسکنی که دست‌کم `STEADY_RUN` نویسهٔ تند آمده (آدم چنین نمی‌نویسد) از وقفه‌ای تا
   *  [bridgeMs] نمی‌شکند؛ اسکنِ جاافتاده‌ای که بی Enter رها شد هجومِ پس از خودش را تا `TAIL_MS` «ته» می‌کند
   *  (کد نیست)؛ و پژواک با `ScanEcho`. خاموش ⇒ دقیقاً رفتارِ پیشین.
   */
  val steady: Boolean = false,
) {
  /** مقدارِ امن: آستانه در بازه، فقط نویسهٔ دیدنیِ ASCII در پیشوند/پسوند، هر کدام تا ۸ نویسه */
  fun normalized(): ScanConfig = ScanConfig(
    maxGapMs = maxGapMs.coerceIn(MIN_GAP_MS, MAX_GAP_MS),
    minLength = minLength.coerceIn(1, 64),
    prefix = clean(prefix),
    suffix = clean(suffix),
    steady = steady,
  )

  /** بیشترین وقفهٔ پذیرفته وسطِ اسکنِ جاافتاده: چهار برابرِ آستانه تا `MAX_GAP_MS`، هرگز کمتر از خودِ آستانه */
  val bridgeMs: Long get() = maxOf(maxGapMs, minOf(maxGapMs * 4, MAX_GAP_MS))

  companion object {
    const val DEFAULT_GAP_MS = 50L
    const val DEFAULT_MIN_LENGTH = 4
    const val MIN_GAP_MS = 10L
    const val MAX_GAP_MS = 300L
    /** کدِ پیشونددار تا این مدت از نویسهٔ اول اسکن شمرده می‌شود، هر قدر هم کند */
    const val PREFIXED_WINDOW_MS = 3_000L
    /** `steady`: چند نویسهٔ تندِ پیاپی اسکن را «جاافتاده» می‌کند (بیست نویسه در ثانیه از دستِ آدم نمی‌آید) */
    const val STEADY_RUN = 4
    /** `steady`: هجومی که تا این مدت پس از رها شدنِ اسکنِ جاافتاده آغاز شود، تهِ همان است */
    const val TAIL_MS = 1_000L
    /** `steady`: همان کد دوباره تا این مدت (پنجرهٔ لغزان) پژواکِ بارکدخوان است */
    const val ECHO_MS = 300L

    private fun clean(s: String) = s.filter { it.code in 33..126 }.take(8)
  }
}

/** همان رفتارِ `HidScan`، با آستانه، پیشوند و پسوندِ تنظیم‌شده */
class KeyScan(config: ScanConfig = ScanConfig()) {
  val config: ScanConfig = config.normalized()
  private val buf = StringBuilder()
  private var lastAt = 0L
  private var startAt = 0L
  /** `steady`: هجومِ کنونی تهِ اسکنی است که شکست — هرگز کد نمی‌دهد */
  private var tail = false

  private fun prefixed(): Boolean = config.prefix.isNotEmpty() && buf.startsWith(config.prefix)
  private fun withinPrefixWindow(now: Long) = prefixed() && now - startAt <= ScanConfig.PREFIXED_WINDOW_MS

  /** `steady`: اسکنِ جاافتاده (چند نویسهٔ تند) از وقفهٔ کوتاه نمی‌شکند */
  private fun settled(): Boolean = config.steady && buf.length >= ScanConfig.STEADY_RUN
  private fun bridges(gap: Long): Boolean = settled() && gap <= config.bridgeMs

  /** نویسهٔ بعدی اسکنِ تازه‌ای را آغاز می‌کند؟ (آنچه در بافر است کهنه یا تایپِ آدم بوده) */
  fun startsNew(now: Long): Boolean =
    buf.isEmpty() || (now - lastAt > config.maxGapMs && !withinPrefixWindow(now) && !bridges(now - lastAt))

  /**
   *  یک نویسه رسید. اگر پسوندِ تنظیم‌شده اسکن را تمام کرد، کد (بی پیشوند و پسوند)؛ وگرنه `null`.
   */
  fun feed(char: Char, now: Long): String? {
    if (startsNew(now)) {
      //  `steady`: اسکنِ جاافتاده‌ای که بی Enter رها می‌شود ⇒ هجومِ پسِ آن (تا `TAIL_MS`) تهِ همان است، نه کد
      tail = settled() && now - lastAt <= ScanConfig.TAIL_MS
      buf.setLength(0); startAt = now
    }
    lastAt = now
    buf.append(char)
    val sfx = config.suffix
    if (sfx.isNotEmpty() && buf.length > sfx.length && buf.endsWith(sfx)) {
      val raw = buf.toString()
      val broken = tail
      buf.setLength(0); tail = false
      return if (broken) null else strip(raw)
    }
    return null
  }

  /** Enter رسید: کدِ کامل اگر تند (یا پیشونددار در مهلت) و به‌قدرِ کافی بلند بود؛ وگرنه `null` */
  fun submit(now: Long): String? {
    val raw = buf.toString()
    val gap = now - lastAt
    val ok = raw.isNotEmpty() && !tail && (gap <= config.maxGapMs || withinPrefixWindow(now) || bridges(gap))
    buf.setLength(0)
    lastAt = now
    tail = false
    return if (ok) strip(raw) else null
  }

  private fun strip(raw: String): String? {
    var s = raw
    if (config.prefix.isNotEmpty() && s.startsWith(config.prefix)) s = s.substring(config.prefix.length)
    if (config.suffix.isNotEmpty() && s.endsWith(config.suffix)) s = s.substring(0, s.length - config.suffix.length)
    return s.takeIf { it.length >= config.minLength }
  }

  fun reset() { buf.setLength(0); lastAt = 0; startAt = 0; tail = false }

  /** وسطِ یک اسکن؟ — صفحه با این تصمیم می‌گیرد نویسه را بخورد یا به کادر بدهد */
  val busy: Boolean get() = buf.isNotEmpty()
}

/**
 *  نگهبانِ کادرِ عدد (قیمت، تعداد، مبلغ): نخستین نویسهٔ هر هجوم مقدارِ کادر را **پیش از** خودش ثبت
 *  می‌کند؛ اگر هجوم اسکن از آب درآمد، `Hit.restore` همان مقدار است و `Hit.code` بارکد. تایپِ آدم
 *  (کند) هیچ‌وقت `Hit` نمی‌سازد و کادر دست نمی‌خورد.
 */
class FieldScanGuard(config: ScanConfig = ScanConfig()) {
  /** @property echo پژواکِ بارکدخوان (`steady`): کادر برمی‌گردد ولی کد دوباره به سبد نمی‌رود */
  data class Hit(val code: String, val restore: String, val echo: Boolean = false)

  private val scan = KeyScan(config)
  private val echo = ScanEcho()
  private var before = ""

  private fun hit(code: String, now: Long) = Hit(code, before, echo = scan.config.steady && echo.echo(code, now))

  /** نویسه پیش از رسیدن به کادر؛ `value` متنِ کادر در همین لحظه (پیش از این نویسه) */
  fun key(char: Char, now: Long, value: String): Hit? {
    if (scan.startsNew(now)) before = value
    return scan.feed(char, now)?.let { hit(it, now) }
  }

  /** Enter پیش از رسیدن به کادر */
  fun enter(now: Long): Hit? = scan.submit(now)?.let { hit(it, now) }

  fun reset() = scan.reset()
}

/**
 *  HW-05 — پژواکِ بارکدخوان: بعضی بارکدخوان‌ها (به‌ویژه حالتِ خودکار/ایستاده) همان کد را پشتِ هم دو بار
 *  می‌فرستند. همان کد تا `ScanConfig.ECHO_MS` پس از خواندنِ قبلیِ همان کد پژواک است؛ پنجره لغزان است تا کالای
 *  ایستاده پیشِ بارکدخوان هر چند بار خوانده شود یک بار بشمارد. کدِ دیگر یا ساعتِ پس‌رفته هرگز پژواک نیست.
 *  فقط با `ScanConfig.steady` به کار می‌رود.
 */
class ScanEcho(private val windowMs: Long = ScanConfig.ECHO_MS) {
  private var last: String? = null
  private var at = 0L

  /** `true` ⇒ پژواک؛ هر خواندن (پژواک یا نه) لحظهٔ تازهٔ همان کد است */
  fun echo(code: String, now: Long): Boolean {
    val dup = code == last && now - at in 0..windowMs
    last = code
    at = now
    return dup
  }
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
