package ir.vil3ntec.tohid.core.guide

/*
 *  OPS-05 — «چه چیزی تازه است» داخلِ برنامه؛ هیچ قابلیتی بی‌صدا حذف نمی‌شود (§۶.۲۴، §۱۱: «هر نسخه
 *  چه چیزی تازه است به دری دارد»).
 *
 *  فهرستِ یادداشت‌ها همراهِ خودِ برنامه می‌آید ([ALL])، نه از شبکه: آفلاین هم دیده می‌شود و متنش با
 *  کدی که واقعاً نصب شده یکی است. هر یادداشت مالِ یک نسخهٔ پایه (`X.Y.Z`، همان `versionBase` ِ
 *  ساخت) است و سه‌زبانه ([GuideText]، مثلِ راهنمای صفحه‌ها).
 *
 *  ── قاعده‌ها ──────────────────────────────────────────────────────────
 *  - پس از به‌روزرسانی فقط یادداشت‌های نسخه‌های **میانِ** آخرین نسخهٔ دیده‌شده و نسخهٔ نصب‌شده
 *    ([pending])؛ نخستین اجرا (هیچ نسخه‌ای دیده نشده) هیچ — نصبِ تازه «تازه» ندارد.
 *  - شمارهٔ ساخت (`2.14.5.1234`) و پسوند (`-preview`) در مقایسه نیستند؛ فقط `X.Y.Z`.
 *  - برگشت به نسخهٔ کهنه‌تر هیچ نشان نمی‌دهد.
 *  - **برداشته‌شده** ([Kind.REMOVED]) همیشه اولِ همان نسخه می‌آید و بی «به‌جایش» ([Note.instead])
 *    پذیرفته نیست ([problems]): کاربر می‌داند چه رفت و حالا از کجا همان کار را بکند.
 *  - هر یادداشت سه زبانِ کامل می‌خواهد؛ یادداشتِ مشکل‌دار نشان داده نمی‌شود ([shown]).
 *
 *  افزودنِ یادداشت: یک ردیف در [ALL] با نسخهٔ پایهٔ همان PR. آزمون: `WhatsNewTest`.
 *  Kotlin ِ خالص (commonMain)، بی android.* و java.*.
 */
object WhatsNew {

  enum class Kind { REMOVED, CHANGED, NEW }

  data class Note(
    val id: String,
    /** نسخهٔ پایه، مثلاً «2.14.5» */
    val version: String,
    val kind: Kind,
    val text: GuideText,
    /** برای [Kind.REMOVED]: همان کار حالا از کجا (اجباری) */
    val instead: GuideText? = null,
  )

  val ALL: List<Note> = listOf(
    Note(
      id = "ops05.intro",
      version = "2.14.5",
      kind = Kind.NEW,
      text = GuideText(
        fa = "«چه چیزی تازه است»: پس از هر به‌روزرسانی، تغییرهای همان نسخه یک بار همین‌جا نشان داده می‌شوند. هر قابلیتی که برداشته شود هم با جای تازه‌اش همین‌جا گفته می‌شود. فهرستِ همه در «تنظیمات ← دربارهٔ برنامه» است.",
        en = "“What's new”: after each update, the changes in that version are shown here once. Any feature that is removed is announced here too, together with where to find it now. The full list is in “Settings → About”.",
        ps = "«څه نوي دي»: له هر تازه کېدو وروسته، د هماغې نسخې بدلونونه یو ځل همدلته ښودل کېږي. هر هغه ځانګړنه چې لرې شي هم د خپل نوي ځای سره همدلته ویل کېږي. د ټولو لېست په «تنظیمات ← د اپ په اړه» کې دی.",
      ),
    ),
  )

  /** «2.14.5.1234-preview» ⇒ [2, 14, 5]؛ کمتر از سه بخشِ عددی ⇒ null */
  fun base(version: String?): List<Int>? {
    val core = version?.trim()?.substringBefore('-')?.substringBefore('+') ?: return null
    val parts = core.split('.')
    if (parts.size < 3) return null
    val nums = parts.take(3).map { p -> p.toIntOrNull()?.takeIf { it >= 0 && p.isNotEmpty() && p.all(Char::isDigit) } ?: return null }
    return nums
  }

  /** «2.14.5» برای نگه داشتن به‌عنوانِ آخرین نسخهٔ دیده‌شده؛ نسخهٔ نامعتبر ⇒ null */
  fun baseText(version: String?): String? = base(version)?.joinToString(".")

  private fun cmp(a: List<Int>, b: List<Int>): Int {
    for (i in 0 until 3) {
      val c = a[i].compareTo(b[i])
      if (c != 0) return c
    }
    return 0
  }

  private val order = compareBy<Note>({ it.kind.ordinal }, { it.id })

  /** مشکل‌های هر یادداشت (خالی ⇒ درست) */
  fun problems(notes: List<Note>): List<String> {
    val out = ArrayList<String>()
    val seen = HashSet<String>()
    for (n in notes) {
      val tag = n.id.ifBlank { "?" }
      if (n.id.isBlank()) out += "$tag: id"
      else if (!seen.add(n.id)) out += "$tag: تکراری"
      if (base(n.version) == null || n.version.split('.').size != 3) out += "$tag: نسخه «${n.version}»"
      if (listOf(n.text.fa, n.text.en, n.text.ps).any { it.isBlank() }) out += "$tag: متنِ سه‌زبانه"
      if (n.kind == Kind.REMOVED) {
        val i = n.instead
        if (i == null || listOf(i.fa, i.en, i.ps).any { it.isBlank() }) out += "$tag: برداشته‌شده بی «به‌جایش»"
      }
    }
    return out
  }

  /** یادداشت‌های درست (هر یادداشتِ مشکل‌دار بیرون) */
  fun shown(notes: List<Note>): List<Note> = notes.filter { problems(listOf(it)).isEmpty() }
    .distinctBy { it.id }

  /**
   *  یادداشت‌هایی که پس از به‌روزرسانی یک بار نشان داده می‌شوند: نسخه‌شان بزرگ‌تر از `lastSeen` و
   *  نه بزرگ‌تر از `current`؛ تازه‌ترین نسخه اول، و در هر نسخه برداشته‌شده ⇒ تغییر ⇒ تازه.
   */
  fun pending(notes: List<Note>, lastSeen: String?, current: String): List<Note> {
    val seen = base(lastSeen) ?: return emptyList()
    val cur = base(current) ?: return emptyList()
    if (cmp(cur, seen) <= 0) return emptyList()
    return shown(notes)
      .filter { n -> base(n.version)!!.let { cmp(it, seen) > 0 && cmp(it, cur) <= 0 } }
      .sortedWith(compareByDescending<Note> { base(it.version)!!.let { v -> v[0] * 1_000_000L + v[1] * 1_000L + v[2] } }.then(order))
  }

  /** همهٔ یادداشت‌های نسخهٔ نصب‌شده و پیش از آن، دسته‌شده به نسخه (تازه‌ترین اول) */
  fun history(notes: List<Note>, current: String): List<Pair<String, List<Note>>> {
    val cur = base(current) ?: return emptyList()
    return shown(notes)
      .filter { cmp(base(it.version)!!, cur) <= 0 }
      .groupBy { baseText(it.version)!! }
      .toList()
      .sortedWith { a, b -> cmp(base(b.first)!!, base(a.first)!!) }
      .map { (v, l) -> v to l.sortedWith(order) }
  }

  /**
   *  آخرین نسخهٔ دیده‌شده پس از این اجرا: نسخهٔ پایهٔ نصب‌شده، ولی هرگز پایین‌تر از آنچه بود
   *  (برگشت به نسخهٔ کهنه یادداشت‌ها را دوباره «تازه» نمی‌کند). نسخهٔ نامعتبر ⇒ همان قبلی.
   */
  fun seenAfter(lastSeen: String?, current: String): String? {
    val cur = base(current) ?: return baseText(lastSeen)
    val seen = base(lastSeen) ?: return baseText(current)
    return (if (cmp(cur, seen) >= 0) cur else seen).joinToString(".")
  }
}
