package ir.vil3ntec.tohid.ui.v3

import ir.vil3ntec.tohid.core.i18n.DesktopPhrases
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 *  نگهبانِ دسترس‌پذیریِ رابطِ نسخهٔ ۳ (ممیزیِ WCAG 2.1 AA و راهنمای دسترس‌پذیریِ اندروید) — روی متنِ کد، پس بی
 *  صفحه و بی ناپایداری؛ همان کد روی اندروید، کامپیوتر و آیفون اجرا می‌شود:
 *
 *  1. **دکمهٔ تک‌آیکنی بی نام نه**: هر جزءِ زدنی (`v3Pressable`، `clickable`، `combinedClickable`، `selectable`،
 *     `toggleable`) که درونش فقط آیکن/تصویر است، یا خودِ `Icon`/`Image` ِ زدنی، باید نامی برای صفحه‌خوان داشته باشد
 *     (`contentDescription` ِ آیکن یا `semantics`). `onClickLabel` ِ `v3Pressable` نامِ **کار** است («دوبار بزنید
 *     تا …»)، نه نامِ دکمه؛ بی نام TalkBack می‌گوید «دکمهٔ بی‌برچسب» (نمونه: «+» ِ کادرِ صندوقِ خانه، بستنِ اسکن).
 *  2. **کشیدن جایگزین دارد**: هر کشیدن/ورقِ دست‌ساز (`draggable`، `SwipeToDismissBox`، `detectDragGestures`، …)
 *     یا «کارِ سفارشی» (`customActions`) ِ صفحه‌خوان در همان پرونده دارد، یا در `ALTERNATIVES` با راهِ جایگزینِ
 *     نوشته (دکمه یا زدن). فهرست فقط کوتاه می‌شود: ردیفی که دیگر کشیدن ندارد یا کارِ سفارشی گرفته، سرخ می‌شود.
 *  3. **نوشتهٔ یک‌خطی با پهنای ثابت نه**: `Text(… maxLines = 1 … Modifier.width(N.dp))` با درشتیِ نوشتهٔ ×۱٫۳/×۲
 *     بریده می‌شود؛ `widthIn(min = …)` همان هم‌ترازی را نگه می‌دارد.
 *  4. سیم‌کشی‌های همین ممیزی در اجزای مشترک برنگردند (برگه، ردیف، کلید، پیام).
 */
class V3A11yGuardTest {

  private val ui: File get() = DesktopPhrases.repo().resolve("android-native/app/src/main/java/ir/vil3ntec/tohid/ui")

  /** صفحه‌های نسخهٔ ۳ و اجزای مشترکی که صندوق و فهرست‌ها از آن‌ها ساخته می‌شوند */
  private fun files(vararg dirs: String): List<File> = dirs.flatMap { d ->
    ui.resolve(d).walkTopDown().filter { it.isFile && it.extension == "kt" }.sortedBy { it.path }.toList()
  }

  private fun rel(f: File) = f.relativeTo(ui).invariantSeparatorsPath

  /* ------------------------------- ۱. دکمهٔ تک‌آیکنی ------------------------------- */

  @Test fun `هر دکمهٔ تک‌آیکنی نامی برای صفحه‌خوان دارد`() {
    val bad = ArrayList<String>()
    for (f in files("v3", "components")) for (hit in iconOnlyClickables(mask(f.readText()))) bad += "${rel(f)} ← $hit"
    assertTrue(
      "دکمهٔ تک‌آیکنیِ بی نام (contentDescription ِ آیکن یا semantics { contentDescription } بدهید):\n" + bad.joinToString("\n"),
      bad.isEmpty(),
    )
  }

  @Test fun `نگهبانِ دکمهٔ تک‌آیکنی همان نمونه‌های بد را می‌گیرد و خوب‌ها را نه`() {
    val bad = """
      Box(
        Modifier.size(38.dp).clip(CircleShape)
          .v3Pressable(label = StrHero.addOrTake, onClick = onPlus),
        contentAlignment = Alignment.Center,
      ) { Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(22.dp)) }
      Icon(Icons.Outlined.Close, null, modifier = Modifier.size(32.dp).clickable(onClick = onClose))
    """.trimIndent()
    assertTrue(iconOnlyClickables(mask(bad)).size == 2)
    val good = """
      Box(Modifier.v3Pressable(label = x, onClick = a)) { Icon(Icons.Rounded.Add, contentDescription = x) }
      Box(Modifier.v3Pressable(label = x, onClick = a).semantics { contentDescription = x }) { Icon(Icons.Rounded.Add, null) }
      Row(Modifier.clickable(onClick = a)) { Icon(Icons.Rounded.Add, null); Text("افزودن") }
      Icon(Icons.Outlined.Close, null, modifier = Modifier.clickable(onClick = c).semantics { contentDescription = y })
      Box(Modifier.clickable(onClick = a)) { ProfileAvatar(id, 88.dp) }
    """.trimIndent()
    assertTrue(iconOnlyClickables(mask(good)).isEmpty())
  }

  /* ------------------------------- ۲. کشیدن و جایگزین ------------------------------- */

  /** کشیدنی که کارِ سفارشیِ صفحه‌خوان ندارد، با راهِ جایگزینش (فهرست فقط کوتاه می‌شود) */
  private val ALTERNATIVES = mapOf(
    "v3/shell/HomeScreen.kt" to "کششِ کادرِ صندوقِ خانه: دستگیرهٔ زدنیِ «صندوق، باز/بسته» (PullHandle) و کارهای سفارشیِ «پنهان کردن/صفحهٔ بعد/پیش» ِ خودِ کادر (HomeHeroCard)",
    "v3/settings/sections/NavSlotList.kt" to "جابه‌جاییِ میان‌برهای نوار با کشیدنِ دستگیره: زدنِ ردیف برگهٔ «جای X» را باز می‌کند و همان جابه‌جایی را می‌کند",
    "components/pos/PhoneCart.kt" to "کشیدنِ سبدِ صندوق: زدنِ نوارِ سبد بازش می‌کند و زدنِ تکهٔ پیدای کالاها («بستنِ سبد») می‌بنددش",
  )

  private val GESTURE = Regex(
    """SwipeToDismissBox\(|\.draggable\(|\.anchoredDraggable\(|\.swipeable\(|detectDragGestures|detectHorizontalDragGestures|detectVerticalDragGestures|detectTransformGestures|awaitEachGesture""",
  )

  @Test fun `هر کشیدنِ دست‌ساز جایگزینی برای صفحه‌خوان دارد`() {
    val missing = ArrayList<String>()
    val users = HashSet<String>()
    for (f in files("v3", "components/pos")) {
      val code = mask(f.readText())
      if (!GESTURE.containsMatchIn(code)) continue
      val path = rel(f)
      users += path
      if ("customActions" !in code && path !in ALTERNATIVES) missing += path
    }
    assertTrue(
      "کشیدن بی جایگزینِ صفحه‌خوان — `semantics { customActions = … }` با همان کار بدهید (یا دکمه/زدن و ردیفِ ALTERNATIVES با دلیل):\n" +
        missing.joinToString("\n"),
      missing.isEmpty(),
    )
    val stale = ALTERNATIVES.keys.filter { it !in users || "customActions" in mask(ui.resolve(it).readText()) }
    assertTrue("ردیفِ کهنهٔ ALTERNATIVES (دیگر کشیدن ندارد یا کارِ سفارشی گرفته) — بردارید: $stale", stale.isEmpty())
  }

  /* ------------------------------- ۳. پهنای ثابتِ نوشته ------------------------------- */

  @Test fun `نوشتهٔ یک‌خطی پهنای ثابت ندارد تا با درشتیِ نوشته بریده نشود`() {
    val fixed = Regex("""\bText\(.*maxLines\s*=\s*1\b.*Modifier\.width\(\d+(\.\d+)?\.dp\)|\bText\(.*Modifier\.width\(\d+(\.\d+)?\.dp\).*maxLines\s*=\s*1\b""")
    val bad = files("v3").flatMap { f ->
      mask(f.readText()).lines().mapIndexedNotNull { i, l -> if (fixed.containsMatchIn(l)) "${rel(f)}:${i + 1}" else null }
    }
    assertTrue("نوشتهٔ یک‌خطی با پهنای ثابت — widthIn(min = …) بدهید:\n" + bad.joinToString("\n"), bad.isEmpty())
  }

  /* ------------------------------- ۴. سیم‌کشیِ اجزای مشترک ------------------------------- */

  @Test fun `برگه و ردیف و کلید و پیامِ کیت معنای صفحه‌خوان را نگه می‌دارند`() {
    fun src(p: String) = mask(ui.resolve(p).readText())
    val sheet = src("v3/V3Sheet.kt")
    assertTrue("برگه با نامش اعلام شود (paneTitle)", "paneTitle = title" in sheet)
    assertTrue("عنوانِ برگه سرخط است", "heading()" in sheet)
    assertTrue("دستگیرهٔ برگه برای صفحه‌خوان «بستن» است", Regex("""onClick\(StrKit\.close\)""").containsMatchIn(sheet))
    val row = src("v3/V3ListRow.kt")
    assertTrue("کارهای پنهانِ زیرِ ردیف به صفحه‌خوان نمی‌رسند", "clearAndSetSemantics" in row && "enabled = !covered" in row)
    assertTrue("نگه‌داشتنِ ردیف نامِ کار دارد", "onLongClickLabel" in row)
    val inputs = src("v3/V3Inputs.kt")
    assertTrue("ردیفِ کلید برای صفحه‌خوان یک کلید با حالت است", "role = Role.Switch" in inputs && "toggleableState" in inputs)
    val states = src("v3/V3States.kt")
    assertTrue("خطای کادرِ پیام خوانده می‌شود (WCAG 4.1.3)", "liveRegion = LiveRegionMode.Polite" in states)
  }

  /* ------------------------------- ابزار ------------------------------- */

  /**
   *  توضیح‌ها با فاصله و درونِ رشته‌ها با `_` (طول و شمارهٔ خط همان) — تا آکولاد و پرانتزِ درونِ متن شمرده نشود.
   *  ماشینِ حالت: کد، توضیحِ خطی، توضیحِ بلوکی، رشته، رشتهٔ خام و حرف.
   */
  private fun mask(src: String): String {
    val out = StringBuilder(src.length)
    var i = 0
    fun keep(ch: Char) = if (ch == '\n') '\n' else ' '
    while (i < src.length) {
      val ch = src[i]
      val next = src.getOrNull(i + 1)
      when {
        ch == '/' && next == '/' -> while (i < src.length && src[i] != '\n') { out.append(' '); i++ }
        ch == '/' && next == '*' -> {
          out.append("  "); i += 2
          while (i < src.length && !(src[i] == '*' && src.getOrNull(i + 1) == '/')) { out.append(keep(src[i])); i++ }
          if (i < src.length) { out.append("  "); i += 2 }
        }
        src.startsWith("\"\"\"", i) -> {
          out.append("\"\"\""); i += 3
          while (i < src.length && !src.startsWith("\"\"\"", i)) { out.append(if (src[i] == '\n') '\n' else '_'); i++ }
          if (i < src.length) { out.append("\"\"\""); i += 3 }
        }
        ch == '"' -> {
          out.append('"'); i++
          while (i < src.length && src[i] != '"' && src[i] != '\n') {
            if (src[i] == '\\' && i + 1 < src.length) { out.append("__"); i += 2 } else { out.append('_'); i++ }
          }
          if (i < src.length && src[i] == '"') { out.append('"'); i++ }
        }
        ch == '\'' && (next == '\\' || src.getOrNull(i + 2) == '\'') -> {
          val end = src.indexOf('\'', i + if (next == '\\') 3 else 2).let { if (it < 0) src.length - 1 else it }
          repeat(end - i + 1) { out.append(' ') }
          i = end + 1
        }
        else -> { out.append(ch); i++ }
      }
    }
    return out.toString()
  }

  private fun matching(s: String, from: Int, open: Char, close: Char): Int {
    var depth = 0
    for (j in from until s.length) {
      if (s[j] == open) depth++ else if (s[j] == close) { depth--; if (depth == 0) return j }
    }
    return -1
  }

  /** نزدیک‌ترین براکتِ بازِ بسته‌نشده پیش از `at` */
  private fun opener(s: String, at: Int): Int {
    var depth = 0
    for (j in at - 1 downTo 0) {
      when (s[j]) {
        ')', ']', '}' -> depth++
        '(', '[', '{' -> { if (depth == 0) return j; depth-- }
      }
    }
    return -1
  }

  private val CLICK = Regex("""v3Pressable\s*[({]|\.clickable\s*[({]|combinedClickable\s*\(|\.selectable\s*\(|\.toggleable\s*\(""")
  private val NAME_BEFORE = Regex("""([A-Za-z_][A-Za-z0-9_]*)\s*$""")
  private val NAMED = Regex("""contentDescription\s*=(?!\s*null)|semantics""")
  private val CONTAINERS = setOf("Box", "Row", "Column")
  private val ICONS = setOf("Icon", "Image")
  private val NOT_CONTENT = setOf("Modifier", "RoundedCornerShape", "Color", "PaddingValues", "Offset", "IntOffset", "Size", "CornerRadius", "Stroke", "TextStyle")

  /** جاهای زدنیِ بی نام: «شمارهٔ خط: نامِ جزء» */
  private fun iconOnlyClickables(s: String): List<String> {
    val out = ArrayList<String>()
    for (m in CLICK.findAll(s)) {
      val lineStart = s.lastIndexOf('\n', m.range.first) + 1
      if ("fun Modifier." in s.substring(lineStart, m.range.first)) continue
      //  جزءِ Compose ای که این Modifier مالِ اوست: نخستین «Name(» با حرفِ بزرگ رو به بیرون
      var at = m.range.first
      var open: Int
      var name: String
      while (true) {
        open = opener(s, at)
        if (open < 0) break
        name = if (s[open] == '(') NAME_BEFORE.find(s.substring(0, open))?.groupValues?.get(1).orEmpty() else ""
        if (name.firstOrNull()?.isUpperCase() == true) break
        at = open
      }
      if (open < 0) continue
      name = NAME_BEFORE.find(s.substring(0, open))?.groupValues?.get(1).orEmpty()
      val close = matching(s, open, '(', ')')
      if (close < 0) continue
      val args = s.substring(open, close + 1)
      val line = s.substring(0, m.range.first).count { it == '\n' } + 1
      if (name in ICONS) {
        val nullName = Regex("""contentDescription\s*=\s*null|^\(\s*[^,]+,\s*null\b""").containsMatchIn(args)
        if (nullName && !NAMED.containsMatchIn(args)) out += "$line: $name"
        continue
      }
      if (name !in CONTAINERS || NAMED.containsMatchIn(args)) continue
      val lambda = Regex("""^\s*\{""").find(s.substring(close + 1)) ?: continue
      val b0 = close + 1 + lambda.range.last
      val b1 = matching(s, b0, '{', '}')
      if (b1 < 0) continue
      val body = s.substring(b0, b1 + 1)
      if (NAMED.containsMatchIn(body)) continue
      val calls = Regex("""\b([A-Z][A-Za-z0-9_]*)\s*\(""").findAll(body).map { it.groupValues[1] }.toSet() - NOT_CONTENT
      if (calls.none { it in ICONS } || calls.any { it !in ICONS && it !in CONTAINERS && it != "Spacer" }) continue
      out += "$line: $name"
    }
    return out
  }
}
