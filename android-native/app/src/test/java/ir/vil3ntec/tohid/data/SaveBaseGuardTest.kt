package ir.vil3ntec.tohid.data

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 *  نگهبانِ پایهٔ صریحِ ذخیره (`docs/DATA-SAFETY-fa.md` «دو ذخیره از یک صفحهٔ کهنه»).
 *
 *  هر راهِ نوشتنِ دفتر از صفحه یا جریان، دفتری را که دفترِ تازه از آن ساخته شد همراهش می‌دهد:
 *  `ShopStore.save(base, next)`. ذخیرهٔ بی پایه پایه را از شیءهای مشترک حدس می‌زد و دو ذخیره از یک دفترِ کهنه
 *  (بی Pull ِ میانه) دومی اولی را می‌برد. پس:
 *
 *   1. `ShopStore` هیچ `save` ِ تک‌دفتری ندارد (بازتاب) — ذخیرهٔ بی پایه در کدِ برنامه کامپایل نمی‌شود؛
 *      آزمون‌های پیشین همان راهِ پیشین را از پسوندِ آزمونی (`LegacySave`) می‌گیرند.
 *   2. راهِ پیشینِ حدسی (`saveGuessed`) فقط درونِ `ShopStore` است (خاموش‌کنِ اضطراریِ `shop.save_base`).
 *   3. هیچ ذخیره‌ای پایه را **همان لحظهٔ ذخیره** از `….data.value` نمی‌خواند: آن همان حدس است — دفترِ همین حالا
 *      شاید تازه‌تر از دفتری است که صفحه با آن ساخت، و تفاضل ردیف‌های تازه را «حذفِ کاربر» می‌دید.
 *   4. هر `store.save(` در کدِ برنامه دو آرگومان دارد.
 *
 *  ⚠️ سنجه‌های ۲ تا ۴ متنِ کد را می‌خوانند (توضیح‌ها و رشته‌ها پیش از جست‌وجو برداشته می‌شوند)، همان روشِ
 *  `LegacySyncCallTest`.
 */
class SaveBaseGuardTest {

  @Test fun `ShopStore هیچ ذخیرهٔ بی پایه‌ای ندارد`() {
    val saves = ShopStore::class.java.declaredMethods.filter { it.name == "save" || it.name.startsWith("save$") }
    assertTrue("ShopStore.save باید باشد", saves.isNotEmpty())
    val bad = saves.filter { m ->
      val p = m.parameterTypes
      !(p.size == 3 && p[0] == ShopData::class.java && p[1] == ShopData::class.java)
    }.map { m -> m.name + m.parameterTypes.joinToString(prefix = "(", postfix = ")") { it.simpleName } }
    assertEquals("ذخیرهٔ بی پایه — صفحه باید دفتری را که با آن ساخت بدهد: save(base, next)", emptyList<String>(), bad)
  }

  @Test fun `راهِ حدسیِ پیشین فقط درونِ ShopStore است`() {
    val offenders = sources { rel, _ -> !rel.endsWith("data/ShopStore.kt") }
      .filter { (_, src) -> Regex("""\bsaveGuessed\s*\(""").containsMatchIn(src) }
      .map { it.first }
    assertEquals(emptyList<String>(), offenders)
  }

  @Test fun `هیچ ذخیره‌ای پایه را همان لحظه از data_value نمی‌خواند`() {
    val inline = Regex("""\.save\(\s*[A-Za-z_][A-Za-z0-9_.]*\.data\.value\s*,""")
    val offenders = sources { _, _ -> true }
      .filter { (_, src) -> inline.containsMatchIn(src) }
      .map { it.first }
    assertEquals("پایه همان دفتری است که دفترِ تازه از آن ساخته شد، نه دفترِ همین لحظه", emptyList<String>(), offenders)
  }

  @Test fun `هر store_save در کدِ برنامه پایه دارد`() {
    val bad = ArrayList<String>()
    for ((rel, src) in sources { _, _ -> true }) {
      for (m in Regex("""\bstore\s*\.\s*save\s*\(""").findAll(src)) {
        val args = topLevelArgs(src, m.range.last + 1)
        if (args.size != 2 || args.any { it.trim().startsWith("replace") }) {
          bad += "$rel:${src.substring(0, m.range.first).count { it == '\n' } + 1}"
        }
      }
    }
    assertEquals("ذخیرهٔ بی پایه: $bad", emptyList<String>(), bad)
  }

  /** آرگومان‌های سطحِ بالای فراخوانی که پرانتزِ بازش پیش از [from] است */
  private fun topLevelArgs(src: String, from: Int): List<String> {
    val out = ArrayList<String>()
    var depth = 0
    val cur = StringBuilder()
    var i = from
    while (i < src.length) {
      val c = src[i]
      when (c) {
        '(', '[', '{' -> { depth++; cur.append(c) }
        ')', ']', '}' -> {
          //  ویرگولِ پایانی (`a, b,\n)`) آرگومانِ تازه نیست
          if (depth == 0) { if (cur.isNotBlank()) out += cur.toString(); return out }
          depth--; cur.append(c)
        }
        ',' -> if (depth == 0) { out += cur.toString(); cur.setLength(0) } else cur.append(c)
        else -> cur.append(c)
      }
      i++
    }
    return out
  }

  /** پرونده‌های کدِ برنامه، بی توضیح و رشته: (مسیرِ نسبی، متن) */
  private fun sources(keep: (String, File) -> Boolean): List<Pair<String, String>> {
    val root = mainSources()
    return root.walkTopDown()
      .filter { it.isFile && it.extension == "kt" }
      .map { it.relativeTo(root).invariantSeparatorsPath to it }
      .filter { (rel, f) -> keep(rel, f) }
      .map { (rel, f) -> rel to stripped(f.readText()) }
      .toList()
  }

  /** توضیح و رشته برداشته می‌شوند؛ سطرها می‌مانند تا شمارهٔ سطرِ پیام درست باشد */
  private fun stripped(src: String): String {
    fun blank(m: MatchResult) = m.value.filter { it == '\n' }.ifEmpty { " " }
    return src.replace(Regex("""/\*[\s\S]*?\*/"""), ::blank)
      .replace(Regex("\"\"\"[\\s\\S]*?\"\"\"")) { "\"\"" + blank(it).trim(' ') }
      .replace(Regex(""""(?:\\.|[^"\\\n])*""""), "\"\"")
      .replace(Regex("""//[^\n]*"""), " ")
  }

  /** ریشهٔ کدِ اندروید — هم از `android-native/app` (اندروید) و هم از `desktop` (کامپیوتر) */
  private fun mainSources(): File {
    var dir: File? = File("").absoluteFile
    while (dir != null) {
      for (rel in listOf("src/main/java", "app/src/main/java", "android-native/app/src/main/java")) {
        val f = File(dir, "$rel/ir/vil3ntec/tohid")
        if (f.isDirectory && File(f, "data/ShopStore.kt").isFile) return File(dir, rel)
      }
      dir = dir.parentFile
    }
    fail("کدِ اندروید پیدا نشد (از ${File("").absolutePath})")
    error("unreachable")
  }
}
