package ir.vil3ntec.tohid.core.guide

/*
 *  OPS-05 — «تازه‌ها» (چه چیزی تازه است) داخلِ برنامه؛ هیچ قابلیتی بی‌صدا حذف نمی‌شود (§۶.۲۴، §۱۱).
 *
 *  ── منبع ──────────────────────────────────────────────────────────────
 *  تنها منبع `docs/WHATS-NEW-fa.md` است (خواستِ صاحب‌کار ۱۴۰۵/۰۸/۱۲: «هر نسخهٔ تازه بگوید چه عوض شد»).
 *  `core/build.gradle.kts` (کارِ `generateWhatsNew`) همان متن را هنگامِ ساخت در [WhatsNewBundled] می‌گذارد ⇒
 *  بی شبکه دیده می‌شود و متنش همان است که با همین کد ساخته شد. فارسی؛ دری با لایهٔ واژه ([GuideText.of])،
 *  انگلیسی و پشتو همان فارسی (سطرها برای صاحب‌کار نوشته می‌شوند، نه ترجمه).
 *
 *  ── قاعده‌ها ──────────────────────────────────────────────────────────
 *  - «دیده‌شده» با کلیدِ هر سطر ([Entry.key]) نگه داشته می‌شود، نه با شمارهٔ نسخه: پیش‌نمایش (۳٫۲٫۶۵) و آزمایشی
 *    (۳٫۲٫۱۹۱۶) از یک کد شمارهٔ ساختِ جدا دارند، پس شماره مرزِ درستی نیست. هر سطری که این دستگاه ندیده «تازه» است —
 *    از ۶۰ به ۶۴ ⇐ همهٔ سطرهای ۶۱ تا ۶۴ با هم ([plan]).
 *  - نصبِ تازه (هیچ چیز دیده نشده) هیچ نشان نمی‌دهد و همه را «دیده‌شده» می‌گذارد.
 *  - برگشت به نسخهٔ کهنه‌تر هیچ نشان نمی‌دهد (سطرهایش زیرمجموعهٔ دیده‌شده‌اند) و [ack] دیده‌شده را کم نمی‌کند.
 *  - دستگاهی که پیش از این سامانه فقط «آخرین نسخهٔ پایهٔ دیده‌شده» دارد ([plan]، `legacySeen`): بخش‌هایی که
 *    شمارهٔ ساختِ همین کانالشان (`shipped`) از آن بزرگ‌تر است، به‌علاوهٔ هر بخشِ تازه‌ترِ بی شماره؛ بی هیچ نشانه
 *    (مثلاً کامپیوتر) فقط تازه‌ترین بخش.
 *  - **برداشته** ([Kind.REMOVED]) همیشه اولِ بخشِ خودش می‌آید و بی «به‌جایش» پذیرفته نیست ([Log.problems]).
 *  - سطرِ مشکل‌دار نشان داده نمی‌شود؛ آزمونِ `WhatsNewTest`/`WhatsNewFileTest` همان را سرخ می‌کند.
 *
 *  Kotlin ِ خالص (commonMain)، بی android.* و java.* — گوشی، کامپیوتر و آیفون همین را می‌خوانند.
 */
object WhatsNew {

  /** نوعِ هر سطر؛ [word] همان واژهٔ سرِ سطر در پرونده */
  enum class Kind(val word: String) {
    REMOVED("برداشته"),
    CHANGED("تغییر"),
    FIXED("رفع"),
    NEW("تازه"),
  }

  /** یک سطرِ «تازه‌ها»؛ [instead] برای [Kind.REMOVED]: همان کار حالا از کجا */
  data class Entry(val kind: Kind, val text: String, val instead: String? = null) {
    /** کلیدِ پایدارِ «دیده‌شده» — از نوع و متن، نه از جای سطر (جابه‌جایی میانِ بخش‌ها «تازه» نمی‌کند) */
    val key: String get() = keyOf(kind, text, instead)

    fun textIn(lang: String): String = GuideText(text).of(lang)
    fun insteadIn(lang: String): String? = instead?.let { GuideText(it).of(lang) }
  }

  /**
   *  یک بخش (یک دسته یا یک مرج به main): [line] دو عددِ نخستِ نسخه («3.2»)، [title] عنوانِ کوتاه، [shipped] شمارهٔ ساختِ
   *  همان بخش در هر کانال («preview»، «beta») فقط برای بخش‌های پیش از این سامانه.
   */
  data class Section(
    val line: String,
    val title: String,
    val entries: List<Entry>,
    val shipped: Map<String, Int> = emptyMap(),
  ) {
    fun titleIn(lang: String): String = GuideText(title).of(lang)
  }

  /** پروندهٔ خوانده‌شده: بخش‌ها (تازه‌ترین اول، فقط سطرهای درست) و مشکل‌ها (خالی ⇒ درست) */
  class Log(val sections: List<Section>, val problems: List<String>)

  /** همان پرونده‌ای که همراهِ همین برنامه ساخته شد */
  val bundled: Log by lazy { parse(WhatsNewBundled.text) }

  const val CHANNEL_PREVIEW = "preview"
  /** آزمایشی و پایدار یک شمارنده دارند (`tohid-native.yml`) */
  const val CHANNEL_BETA = "beta"

  private const val HEADING = "## "
  /** سرخطِ بد: سطرهای زیرش سنجیده می‌شوند ولی بخش به کاربر نمی‌رسد */
  private const val BAD = "?"
  private const val INSTEAD = "⇐ به‌جایش:"
  private val heading = Regex("""^##\s+(\d+\.\d+)\s+·\s+(\S.*)$""")
  private val shippedLine = Regex("""^<!--\s*shipped:\s*(.*?)\s*-->$""")
  private val shippedPair = Regex("""^([a-z]+)=(\d+)$""")
  private val entryLine = Regex("""^-\s+(\S+?):\s*(.*)$""")

  /**
   *  `docs/WHATS-NEW-fa.md` ⇒ بخش‌ها. هر چه پیش از نخستین «## » است (شرح و قاعده‌ها) کنار می‌رود. در هر بخش فقط
   *  سطرِ خالی، توضیحِ HTML (و سطرِ `shipped`) و سطرِ «- نوع: متن» پذیرفته است؛ هر چیزِ دیگر مشکل است.
   */
  fun parse(text: String): Log {
    val problems = ArrayList<String>()
    val sections = ArrayList<Section>()
    var line: String? = null
    var title = ""
    var shipped = emptyMap<String, Int>()
    var entries = ArrayList<Entry>()
    var at = 0
    val seenKeys = HashSet<String>()

    fun close() {
      val l = line ?: return
      if (l == BAD) return
      if (entries.isEmpty()) problems += "سطرِ $at: بخشِ «$title» سطری ندارد"
      else sections += Section(l, title, entries.sortedBy { if (it.kind == Kind.REMOVED) 0 else 1 }, shipped)
    }

    var inComment = false
    text.replace("\r\n", "\n").split('\n').forEachIndexed { i, raw ->
      val n = i + 1
      val s = raw.trim()
      if (line == null && !s.startsWith(HEADING)) return@forEachIndexed
      if (inComment) { if (s.endsWith("-->")) inComment = false; return@forEachIndexed }
      when {
        s.startsWith(HEADING) -> {
          close()
          val m = heading.matchEntire(s)
          at = n
          if (m == null) {
            problems += "سطرِ $n: سرخطِ بخش باید «## <دو عدد> · <عنوان>» باشد: $s"
            line = BAD; title = s.removePrefix(HEADING)
          } else {
            line = m.groupValues[1]; title = m.groupValues[2].trim()
          }
          shipped = emptyMap(); entries = ArrayList()
        }
        s.isEmpty() -> Unit
        shippedLine.matchEntire(s) != null -> {
          val pairs = shippedLine.matchEntire(s)!!.groupValues[1].split(Regex("""\s+""")).filter { it.isNotEmpty() }
          val map = LinkedHashMap<String, Int>()
          for (p in pairs) {
            val m = shippedPair.matchEntire(p)
            val v = m?.groupValues?.get(2)?.toIntOrNull()
            if (m == null || v == null || v <= 0) problems += "سطرِ $n: «$p» در shipped باید «کانال=شماره» باشد"
            else map[m.groupValues[1]] = v
          }
          shipped = map
        }
        s.startsWith("<!--") -> if (!s.endsWith("-->")) inComment = true
        else -> {
          val m = entryLine.matchEntire(s)
          val kind = m?.let { r -> Kind.entries.firstOrNull { it.word == r.groupValues[1] } }
          if (m == null || kind == null) {
            problems += "سطرِ $n: سطرِ بخش باید «- تازه|تغییر|رفع|برداشته: متن» باشد: $s"
            return@forEachIndexed
          }
          var body = m.groupValues[2].trim()
          var instead: String? = null
          val cut = body.indexOf(INSTEAD)
          if (cut >= 0) {
            instead = body.substring(cut + INSTEAD.length).trim().ifEmpty { null }
            body = body.substring(0, cut).trim()
          }
          when {
            body.isEmpty() -> problems += "سطرِ $n: متن خالی است"
            kind == Kind.REMOVED && instead == null -> problems += "سطرِ $n: «برداشته» بی «$INSTEAD …» پذیرفته نیست"
            kind != Kind.REMOVED && cut >= 0 -> problems += "سطرِ $n: «به‌جایش» فقط برای «برداشته» است"
            else -> {
              val e = Entry(kind, body, instead)
              if (!seenKeys.add(e.key)) problems += "سطرِ $n: همین سطر پیش‌تر آمده (دو سطرِ یکسان یک کلید دارند)"
              else entries += e
            }
          }
        }
      }
    }
    close()
    if (sections.isEmpty() && problems.isEmpty()) problems += "هیچ بخشی نیست"
    return Log(sections, problems)
  }

  /** کلیدِ پایدار: FNV-1a ِ ۶۴بیتی روی نوع و متنِ یکدست‌شده (روی هر سکو یکی) */
  fun keyOf(kind: Kind, text: String, instead: String? = null): String {
    val norm = kind.word + "|" + squash(text) + "|" + squash(instead.orEmpty())
    var h = -0x340d631b7bdddcdbL // 0xcbf29ce484222325
    for (c in norm) {
      h = h xor c.code.toLong()
      h *= 0x100000001b3L
    }
    return h.toULong().toString(16).padStart(16, '0')
  }

  private fun squash(s: String) = s.trim().split(Regex("""\s+""")).filter { it.isNotEmpty() }.joinToString(" ")

  /** «2.14.5.1234-preview» ⇒ [2, 14, 5]؛ کمتر از سه بخشِ عددی ⇒ null */
  fun base(version: String?): List<Int>? {
    val core = version?.trim()?.substringBefore('-')?.substringBefore('+') ?: return null
    val parts = core.split('.')
    if (parts.size < 3) return null
    val nums = parts.take(3).map { p -> p.toIntOrNull()?.takeIf { it >= 0 && p.isNotEmpty() && p.all(Char::isDigit) } ?: return null }
    return nums
  }

  /** «3.2.64-preview» ⇒ «3.2.64»؛ نسخهٔ نامعتبر ⇒ null */
  fun baseText(version: String?): String? = base(version)?.joinToString(".")

  /** کانالِ ساخت از نامِ نسخه: پیش‌نمایش «-preview» دارد؛ آزمایشی و پایدار یک شمارنده */
  fun channel(version: String): String = if (version.trim().endsWith("-preview")) CHANNEL_PREVIEW else CHANNEL_BETA

  /** «3.2.61» برای بخشی که در همین کانال شمارهٔ ساخت دارد (عنوانِ بخش برای صاحب‌کار)؛ وگرنه null */
  fun versionOf(section: Section, current: String): String? =
    section.shipped[channel(current)]?.let { "${section.line}.$it" }

  /** کلیدهای ذخیره‌شده ⇐ مجموعه؛ خالی یا بد ⇒ مجموعهٔ خالی (نه «هیچ چیز دیده نشده»، که `null` است) */
  fun decode(stored: String?): Set<String> =
    stored.orEmpty().split(',').map { it.trim() }.filter { it.length == 16 && it.all { c -> c in '0'..'9' || c in 'a'..'f' } }.toSet()

  fun encode(keys: Collection<String>): String = keys.sorted().joinToString(",")

  private fun allKeys(sections: List<Section>): Set<String> = sections.flatMapTo(LinkedHashSet()) { s -> s.entries.map { it.key } }

  /**
   *  [show]: بخش‌ها با فقط سطرهای ندیده (تازه‌ترین اول، بخشِ خالی نه). [storeNow]: چیزی که همین حالا بی نشان دادن
   *  ذخیره می‌شود (نصبِ تازه، یا مهاجرتی که چیزی برای نشان دادن ندارد)؛ وگرنه null و ذخیره با [ack] پس از «فهمیدم».
   */
  data class Plan(val show: List<Section>, val storeNow: String?)

  /**
   *  @param stored کلیدهای دیده‌شدهٔ همین دستگاه؛ `null` یعنی این سامانه هنوز هیچ ننوشته
   *  @param legacySeen «آخرین نسخهٔ پایهٔ دیده‌شده» ِ سامانهٔ پیشین (`tohid-whats-new:seen`)، اگر هست
   *  @param current `BuildConfig.VERSION_NAME` ِ نصب‌شده
   */
  fun plan(sections: List<Section>, stored: String?, legacySeen: String?, current: String): Plan {
    if (stored != null) {
      val seen = decode(stored)
      return Plan(unseen(sections, seen), null)
    }
    val all = encode(allKeys(sections))
    if (legacySeen == null || base(legacySeen) == null) return Plan(emptyList(), all)
    val show = migrate(sections, legacySeen, current)
    return Plan(show, if (show.isEmpty()) all else null)
  }

  private fun unseen(sections: List<Section>, seen: Set<String>): List<Section> =
    sections.mapNotNull { s -> s.entries.filter { it.key !in seen }.takeIf { it.isNotEmpty() }?.let { s.copy(entries = it) } }

  private fun migrate(sections: List<Section>, legacySeen: String, current: String): List<Section> {
    val seen = base(legacySeen) ?: return emptyList()
    val line = "${seen[0]}.${seen[1]}"
    val ch = channel(current)
    val stamped = sections.withIndex().filter { (_, s) -> s.line == line && s.shipped[ch] != null }
    if (stamped.isEmpty()) return sections.take(1)
    val boundary = stamped.firstOrNull { (_, s) -> s.shipped.getValue(ch) <= seen[2] }?.index ?: sections.size
    return sections.take(boundary)
  }

  /**
   *  پس از «فهمیدم»: همهٔ سطرهای این ساخت دیده‌شده‌اند، و آنچه پیش‌تر دیده شده بود می‌ماند (برگشت به نسخهٔ کهنه
   *  سطرهای تازه‌تر را دوباره «تازه» نمی‌کند). بیش از [MAX_KEYS] ⇒ فقط کلیدهای همین ساخت.
   */
  fun ack(sections: List<Section>, stored: String?): String {
    val now = allKeys(sections)
    val merged = decode(stored) + now
    return encode(if (merged.size > MAX_KEYS) now else merged)
  }

  /** «روشن» شدنِ دوباره: از همین نسخه به بعد — گذشته ناگهان سر باز نمی‌کند */
  fun seenAll(sections: List<Section>): String = encode(allKeys(sections))

  const val MAX_KEYS = 4000
}
