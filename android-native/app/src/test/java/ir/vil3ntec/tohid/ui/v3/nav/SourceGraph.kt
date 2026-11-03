package ir.vil3ntec.tohid.ui.v3.nav

import java.io.File

/**
 *  نقشهٔ ایستای «چه کسی چه کسی را صدا می‌زند» در کدِ مشترکِ برنامه (`android-native/app/src/main/java`)،
 *  برای `V3ReachabilityTest`: هر اعلانِ سطحِ بالا (fun، class، object، val…) یک گره با نامِ `بسته.نام` است و
 *  هر نامی که در بدنه‌اش آمده و به اعلانِ دیگری می‌رسد یک یال. رسیدن = از ریشه (`AppRoot`) با یال‌ها.
 *
 *  • توضیح‌ها و متنِ رشته‌ها کنار می‌روند (جز `${…}`/`$name` ِ درونِ رشته که کد است).
 *  • نام از بستهٔ خودِ پرونده، `import` ِ صریح (با `as`)، `import …*` و نامِ کاملِ `a.b.c.Name` پیدا می‌شود.
 *  • گره «بسته + نام» است، پس `TeamScreen` ِ نسخهٔ ۳ با `TeamScreen` ِ قدیمِ `ui/screens` یکی نمی‌شود.
 *  • خطای این نقشه به سوی «رسیده» است (نامِ هم‌نام در بستهٔ واردشده هم یال است) — پس «نرسیده» یعنی واقعاً
 *    هیچ کدِ رسیده‌ای نامش را نمی‌برد.
 */
internal class SourceGraph private constructor(
  private val edges: Map<String, Set<String>>,
  val declared: Set<String>,
) {
  /** همهٔ گره‌های رسیده از ریشه‌ها (خودِ ریشه‌ها هم) */
  fun reach(roots: Collection<String>): Set<String> {
    val seen = LinkedHashSet<String>()
    val todo = ArrayDeque<String>()
    roots.filter { it in declared }.forEach { if (seen.add(it)) todo.add(it) }
    while (todo.isNotEmpty()) {
      for (next in edges[todo.removeFirst()].orEmpty()) if (seen.add(next)) todo.add(next)
    }
    return seen
  }

  companion object {
    fun of(root: File): SourceGraph =
      parse(root.walkTopDown().filter { it.isFile && it.extension == "kt" }.map { it.readText() }.toList())

    fun parse(sources: List<String>): SourceGraph {
      val units = sources.map(::unitOf)
      val names = HashMap<String, MutableSet<String>>()
      units.forEach { u -> u.decls.forEach { (n, _) -> names.getOrPut(u.pkg) { HashSet() }.add(n) } }
      val packages = names.keys
      fun has(pkg: String, name: String) = names[pkg]?.contains(name) == true
      val edges = HashMap<String, MutableSet<String>>()
      for (u in units) {
        //  نامِ کوتاه ⇐ گره‌های ممکن: import ِ صریح (یا عضوِ یک object ِ واردشده: `import a.Obj.member` ⇐ `a.Obj`)
        val explicit = HashMap<String, MutableList<String>>()
        val wild = mutableListOf(u.pkg)
        for (imp in u.imports) {
          if (imp.star) { wild += imp.path; continue }
          val pkg = imp.path.substringBeforeLast('.', "")
          val name = imp.path.substringAfterLast('.')
          val key = imp.alias ?: name
          if (has(pkg, name)) explicit.getOrPut(key) { mutableListOf() }.add("$pkg.$name")
          val outer = pkg.substringBeforeLast('.', "")
          val obj = pkg.substringAfterLast('.')
          if (has(outer, obj)) explicit.getOrPut(key) { mutableListOf() }.add("$outer.$obj")
        }
        for ((name, body) in u.decls) {
          val self = "${u.pkg}.$name"
          val out = edges.getOrPut(self) { HashSet() }
          for (m in TOKEN.findAll(body)) {
            val parts = m.value.split('.')
            //  نامِ کامل: بلندترین پیشوندی که بسته است
            for (cut in parts.size - 1 downTo 1) {
              val pkg = parts.subList(0, cut).joinToString(".")
              if (pkg in packages) {
                if (has(pkg, parts[cut])) out += "$pkg.${parts[cut]}"
                break
              }
            }
            //  نامِ کوتاه یا پس از نقطه (تابعِ افزوده `x.foo()`، عضوِ واردشده) — بیش‌شماری به سوی «رسیده» است
            for (part in parts) {
              explicit[part]?.let { out += it }
              for (w in wild) if (has(w, part)) out += "$w.$part"
            }
          }
          out -= self
        }
      }
      val declared = names.flatMap { (p, ns) -> ns.map { "$p.$it" } }.toSet()
      return SourceGraph(edges, declared)
    }

    private class Import(val path: String, val star: Boolean, val alias: String?)
    private class Unit(val pkg: String, val imports: List<Import>, val decls: List<Pair<String, String>>)

    private val TOKEN = Regex("""[A-Za-z_]\w*(?:\.[A-Za-z_]\w*)*""")
    private val PACKAGE = Regex("""^package\s+([\w.]+)""", RegexOption.MULTILINE)
    private val IMPORT = Regex("""^import\s+(\w+(?:\.\w+)*)(\.\*)?(?:\s+as\s+(\w+))?""", RegexOption.MULTILINE)
    private val DECL = Regex(
      """^(?:@[\w.]+(?:\([^)]*\))?\s*)*""" +
        """(?:(?:private|internal|public|protected|inline|suspend|actual|expect|operator|infix|tailrec|data|sealed|enum|abstract|open|annotation|value|const|lateinit|override|external)\s+)*""" +
        """(?:fun\s+interface|fun|class|object|val|var|interface|typealias)\s+(?:<[^>]*>\s*)?""" +
        """(?:[\w.]+(?:<[^>]*>)?\??\.)?`?(\w+)`?""",
    )

    private fun unitOf(text: String): Unit {
      val code = strip(text)
      val pkg = PACKAGE.find(code)?.groupValues?.get(1).orEmpty()
      val imports = IMPORT.findAll(code).map { Import(it.groupValues[1], it.groupValues[2].isNotEmpty(), it.groupValues[3].ifEmpty { null }) }.toList()
      val lines = code.split('\n')
      //  اعلانِ سطحِ بالا = سطری که از ستونِ صفر با کلیدواژهٔ اعلان شروع می‌شود؛ بدنه تا اعلانِ بعدی
      val starts = lines.indices.mapNotNull { i ->
        val l = lines[i]
        if (l.isEmpty() || l[0].isWhitespace() || l.startsWith("package ") || l.startsWith("import ")) null
        else DECL.find(l)?.takeIf { it.range.first == 0 }?.let { i to it.groupValues[1] }
      }
      val decls = starts.mapIndexed { k, (i, name) ->
        val end = if (k + 1 < starts.size) starts[k + 1].first else lines.size
        name to lines.subList(i, end).joinToString("\n")
      }
      return Unit(pkg, imports, decls)
    }

    /**
     *  توضیح‌ها و متنِ رشته‌ها و نویسه‌ها ⇒ فاصله؛ کدِ درونِ `${…}` و نامِ `$x` می‌ماند. سطرها سرِ جایشان
     *  می‌مانند (پایانِ سطر هرگز برداشته نمی‌شود) تا ستونِ صفر همان ستونِ صفرِ پرونده باشد.
     */
    internal fun strip(src: String): String {
      val out = StringBuilder(src.length)
      //  پشتهٔ حالت‌ها: عدد ≥ ۰ = کد با این عمقِ `{` (بالاترین کد درونِ `${`)، -1 = رشتهٔ ساده، -2 = رشتهٔ خام
      val stack = ArrayDeque<Int>().apply { add(0) }
      var i = 0
      val n = src.length
      fun keepNewlines(from: Int, to: Int) { for (j in from until minOf(to, n)) out.append(if (src[j] == '\n') '\n' else ' ') }
      while (i < n) {
        val mode = stack.last()
        val ch = src[i]
        if (mode >= 0) {
          when {
            src.startsWith("//", i) -> { val e = src.indexOf('\n', i).let { if (it < 0) n else it }; keepNewlines(i, e); i = e }
            src.startsWith("/*", i) -> {
              var depth = 0
              var j = i
              while (j < n) {
                if (src.startsWith("/*", j)) { depth++; j += 2 } else if (src.startsWith("*/", j)) { depth--; j += 2; if (depth == 0) break } else j++
              }
              keepNewlines(i, j); i = j
            }
            src.startsWith("\"\"\"", i) -> { stack.addLast(-2); out.append("   "); i += 3 }
            ch == '"' -> { stack.addLast(-1); out.append(' '); i++ }
            ch == '\'' -> {
              //  نویسه: 'a'، '\n'، 'A'، '\''
              var j = i + 1
              while (j < n && src[j] != '\'' && src[j] != '\n') j += if (src[j] == '\\') 2 else 1
              keepNewlines(i, j + 1); i = j + 1
            }
            ch == '{' -> { stack.removeLast(); stack.addLast(mode + 1); out.append(ch); i++ }
            ch == '}' -> {
              if (mode == 0 && stack.size > 1) { stack.removeLast(); out.append(' ') } else { stack.removeLast(); stack.addLast(maxOf(0, mode - 1)); out.append(ch) }
              i++
            }
            else -> { out.append(ch); i++ }
          }
        } else {
          val raw = mode == -2
          when {
            raw && src.startsWith("\"\"\"", i) -> {
              var j = i
              while (j < n && src[j] == '"') j++
              keepNewlines(i, j); i = j; stack.removeLast()
            }
            !raw && ch == '\\' -> { keepNewlines(i, i + 2); i += 2 }
            !raw && (ch == '"' || ch == '\n') -> { out.append(if (ch == '\n') '\n' else ' '); i++; stack.removeLast() }
            src.startsWith("\${", i) -> { stack.addLast(0); out.append("  "); i += 2 }
            ch == '$' && i + 1 < n && (src[i + 1].isLetter() || src[i + 1] == '_') -> {
              var j = i + 1
              while (j < n && (src[j].isLetterOrDigit() || src[j] == '_')) j++
              out.append(' ').append(src, i + 1, j); i = j
            }
            else -> { out.append(if (ch == '\n') '\n' else ' '); i++ }
          }
        }
      }
      return out.toString()
    }
  }
}
