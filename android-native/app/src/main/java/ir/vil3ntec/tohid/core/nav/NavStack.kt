package ir.vil3ntec.tohid.core.nav

/** آخرین جابه‌جایی — جهتِ انیمیشنِ صفحه را می‌سازد */
enum class Move {
  /** همان صفحه (بارِ اول، یا رفتن به صفحه‌ای که همین حالا باز است) */
  NONE,
  /** رفتن به عمق: صفحهٔ تازه از سمتِ پایان می‌آید */
  PUSH,
  /** برگشت: صفحهٔ پیشین از سمتِ آغاز برمی‌گردد */
  POP,
  /** عوض شدنِ تبِ نوارِ پایین (یا منوی کناری): محو و جابه‌جاییِ کوتاه */
  SWITCH,
}

/**
 *  پشتهٔ برگشتِ برنامه — خالص و تغییرناپذیر.
 *
 *  خانهٔ اول همیشه «تب» است (صفحه‌ای از نوارِ پایین)؛ هر چه رویش آمده با «برگشت»
 *  به ترتیب برمی‌گردد، تا هر ورودی (ردیفِ تنظیمات، بخشِ «بیشتر»، کاشیِ خانه،
 *  هشدار، نتیجهٔ جست‌وجو) کاربر را به **همان جایی** برگرداند که از آن آمده.
 *
 *  • رفتن به صفحه‌ای که پایین‌تر در پشته هست، تا همان‌جا برمی‌گرداند (تکراری
 *    روی هم نمی‌نشیند).
 *  • پشته بیش از `MAX_DEPTH` نمی‌شود؛ کهنه‌ترین صفحهٔ میانی کنار می‌رود.
 */
data class NavStack(val entries: List<String>, val move: Move = Move.NONE) {
  init {
    require(entries.isNotEmpty()) { "پشتهٔ خالی" }
  }

  val current: String get() = entries.last()
  val root: String get() = entries.first()
  val depth: Int get() = entries.size
  val canPop: Boolean get() = entries.size > 1

  /** رفتن به عمق */
  fun push(path: String): NavStack {
    val p = RoutePaths.normalize(path)
    if (p == current) return copy(move = Move.NONE)
    val at = entries.indexOf(p)
    if (at >= 0) return NavStack(entries.subList(0, at + 1), Move.POP)
    val grown = entries + p
    val trimmed = if (grown.size > MAX_DEPTH) listOf(grown.first()) + grown.takeLast(MAX_DEPTH - 1) else grown
    return NavStack(trimmed, Move.PUSH)
  }

  /** برگشتِ یک پله؛ در خانهٔ اول بی‌اثر */
  fun pop(): NavStack = if (canPop) NavStack(entries.dropLast(1), Move.POP) else copy(move = Move.NONE)

  /** تبِ تازه: پشته از نو، فقط همان تب */
  fun switchTab(tab: String): NavStack {
    val t = RoutePaths.normalize(tab)
    return if (entries == listOf(t)) copy(move = Move.NONE) else NavStack(listOf(t), Move.SWITCH)
  }

  /**
   *  پرشِ هم‌سطح (منوی کناریِ کامپیوتر): تب سرِ جایش، و فقط همین صفحه رویش —
   *  همان رفتارِ پیشینِ «زیرصفحه»ِ کامپیوتر.
   */
  fun jump(path: String): NavStack {
    val p = RoutePaths.normalize(path)
    return when {
      p == current -> copy(move = Move.NONE)
      p == root -> NavStack(listOf(root), Move.POP)
      else -> NavStack(listOf(root, p), Move.SWITCH)
    }
  }

  /** جایگزینیِ صفحهٔ بالا (مثلاً مسیری که دسترسی ندارد ⇐ «بیشتر») */
  fun replaceTop(path: String): NavStack = NavStack(entries.dropLast(1) + RoutePaths.normalize(path), Move.NONE)

  companion object {
    const val MAX_DEPTH = 16
    fun of(root: String) = NavStack(listOf(RoutePaths.normalize(root)))

    /** برای `rememberSaveable`: فقط فهرستِ مسیرها نگه داشته می‌شود */
    fun restore(saved: List<String>?, fallback: String): NavStack =
      if (saved.isNullOrEmpty()) of(fallback) else NavStack(saved)
  }
}
