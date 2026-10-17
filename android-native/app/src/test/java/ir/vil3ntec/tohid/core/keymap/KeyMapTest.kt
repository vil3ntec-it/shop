package ir.vil3ntec.tohid.core.keymap

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 *  ثبتِ مرکزیِ کلیدها — MASTER-SPEC ۳.۱۶.
 *
 *  ۱. `KeyMap.kt` همان `keymap.json` است (json عوض شده و `gen-keymap.mjs` اجرا نشده ⇒ رد).
 *  ۲. در هیچ دامنه کلیدی دو بار نیامده — جدا برای برنامه و برای وب.
 *  ۳. کلیدِ صفحه کلیدِ سراسری را بی‌اعلام نمی‌پوشاند (`overrides`)، و اعلامِ کهنه هم نمی‌ماند.
 *  ۴. هیچ کلیدی روی کلیدهای رزروِ ویندوز، مک یا مرورگرها (Chrome، Edge، Safari، Firefox) نیست.
 *  ۵. صفحه‌هایی که به ثبتِ مرکزی رفته‌اند دوباره `Key.*`ِ دستی نمی‌گیرند.
 *
 *  همین پوشهٔ آزمون در CIِ اندروید (`app/`) و کامپیوتر (`desktop/`) اجرا می‌شود.
 */
class KeyMapTest {

  private fun file(vararg candidates: String): File? = candidates.map { File(it) }.firstOrNull { it.exists() }

  private val src = "ir/vil3ntec/tohid"
  private val json = file(
    "src/main/java/$src/core/keymap/keymap.json",                         // android-native/app
    "../android-native/app/src/main/java/$src/core/keymap/keymap.json",   // desktop
    "android-native/app/src/main/java/$src/core/keymap/keymap.json",      // ریشهٔ مخزن
  )
  private val screens = file(
    "src/main/java/$src/ui/screens",
    "../android-native/app/src/main/java/$src/ui/screens",
    "android-native/app/src/main/java/$src/ui/screens",
  )

  /** صفحه‌هایی که همهٔ میان‌برهایشان از `KeyMap` می‌آید — فقط زیاد شود */
  private val migrated = listOf("DeskReports.kt")

  private val scopes = KeyMap.SCOPES.associateBy { it.id }

  @Test
  fun `KeyMap_kt همان keymap_json است`() {
    assertTrue("keymap.json پیدا نشد (cwd=${File(".").absoluteFile})", json != null)
    val root = Json.parseToJsonElement(json!!.readText()).jsonObject
    fun JsonObject.str(k: String) = this[k]?.jsonPrimitive?.content
    fun JsonObject.chords(k: String) = (this[k] as? JsonArray).orEmpty().map { Chord.parse(it.jsonPrimitive.content) }

    val expScopes = root.getValue("scopes").jsonArray.map { it.jsonObject }.map {
      KeyScope(it.str("id")!!, KeyScope.Kind.valueOf(it.str("kind")!!.uppercase()), it.str("title")!!, it.str("parent"))
    }
    assertEquals("دامنه‌ها — node gen-keymap.mjs", expScopes, KeyMap.SCOPES)

    val expActions = root.getValue("actions").jsonArray.map { it.jsonObject }.map {
      KeyAction(
        id = it.str("id")!!, scope = it.str("scope")!!, title = it.str("title")!!,
        status = KeyStatus.valueOf(it.str("status")!!.uppercase()),
        keys = it.chords("keys"), web = it.chords("web"),
        overrides = (it["overrides"] as? JsonArray).orEmpty().map { o -> o.jsonPrimitive.content },
        page = it.str("page"),
      )
    }
    //  فقط کارهای ناهم‌خوان در پیام، نه همهٔ فهرست
    val now = KeyMap.ALL.associateBy { it.id }
    val stale = (expActions.filter { now[it.id] != it }.map { it.id } + (now.keys - expActions.map { it.id }.toSet()))
    assertEquals("keymap.json عوض شده و KeyMap.kt کهنه است — node gen-keymap.mjs", emptyList<String>(), stale)
    assertEquals("ترتیبِ کارها", expActions.map { it.id }, KeyMap.ALL.map { it.id })

    val reserved = root.getValue("reserved").jsonObject
    fun res(k: String) = reserved.getValue(k).jsonArray.map { it.jsonObject }.map { ReservedKey(Chord.parse(it.str("key")!!), it.str("why")!!) }
    assertEquals("رزروِ برنامه", res("native"), KeyMap.RESERVED_NATIVE)
    assertEquals("رزروِ وب", res("web"), KeyMap.RESERVED_WEB)
  }

  @Test
  fun `شناسه‌ها یکتا و دامنه‌ها درست`() {
    assertEquals(KeyMap.ALL.size, KeyMap.ALL.map { it.id }.toSet().size)
    for (a in KeyMap.ALL) {
      assertTrue("${a.id}: دامنهٔ ناشناخته ${a.scope}", a.scope in scopes)
      assertTrue("${a.id}: کلید ندارد", a.keys.isNotEmpty())
    }
    for (s in KeyMap.SCOPES) {
      val parent = s.parent?.let { scopes[it] }
      when (s.kind) {
        KeyScope.Kind.WINDOW -> assertTrue("پنجرهٔ ${s.id} باید صفحهٔ مادر داشته باشد", parent?.kind == KeyScope.Kind.SCREEN)
        else -> assertEquals("فقط پنجره مادر دارد: ${s.id}", null, s.parent)
      }
    }
    assertEquals("دقیقاً یک دامنهٔ سراسری", 1, KeyMap.SCOPES.count { it.kind == KeyScope.Kind.GLOBAL })
  }

  /** کلید ⇒ کارهایی که در همین دامنه رویش نشسته‌اند */
  private fun clashes(pick: (KeyAction) -> List<Chord>): List<String> =
    KeyMap.ALL.groupBy { it.scope }.flatMap { (scope, actions) ->
      actions.flatMap { a -> pick(a).map { it to a.id } }
        .groupBy({ it.first }, { it.second })
        .filter { it.value.size > 1 }
        .map { (c, ids) -> "$scope: $c ⇐ $ids" }
    }

  @Test
  fun `در هیچ دامنه کلیدی دو بار نیامده`() {
    assertEquals("کلیدِ تکراری در برنامه", emptyList<String>(), clashes { it.keys })
    assertEquals("کلیدِ تکراری در وب", emptyList<String>(), clashes { it.web })
  }

  @Test
  fun `کلیدِ صفحه کلیدِ سراسری را بی‌اعلام نمی‌پوشاند`() {
    val global = KeyMap.ALL.filter { scopes[it.scope]?.kind == KeyScope.Kind.GLOBAL }
    val bad = mutableListOf<String>()
    for (a in KeyMap.ALL.filter { scopes[it.scope]?.kind == KeyScope.Kind.SCREEN }) {
      val shadowed = global.filter { g -> g.keys.any { it in a.keys } || g.web.any { it in a.web } }.map { it.id }.toSet()
      (shadowed - a.overrides.toSet()).forEach { bad += "${a.id} کلیدِ $it را می‌پوشاند — در overrides بیاورید یا کلید را عوض کنید" }
      (a.overrides.toSet() - shadowed).forEach { bad += "${a.id}: overrides کهنه — $it را نمی‌پوشاند" }
    }
    //  پنجره بی‌اعلام مقدم است (۳.۱۶)، ولی اعلامش بی‌معناست
    KeyMap.ALL.filter { scopes[it.scope]?.kind != KeyScope.Kind.SCREEN && it.overrides.isNotEmpty() }
      .forEach { bad += "${it.id}: overrides فقط برای کلیدِ صفحه است" }
    assertEquals(emptyList<String>(), bad)
  }

  @Test
  fun `هیچ کلیدی روی کلیدهای رزروِ سیستم و مرورگر نیست`() {
    val native = KeyMap.RESERVED_NATIVE.associateBy { it.chord }
    val web = KeyMap.RESERVED_WEB.associateBy { it.chord }
    val bad = KeyMap.ALL.flatMap { a ->
      a.keys.mapNotNull { c -> native[c]?.let { "${a.id}: $c — ${it.why}" } } +
        a.web.mapNotNull { c -> web[c]?.let { "${a.id} (وب): $c — ${it.why}" } }
    }
    assertEquals(emptyList<String>(), bad)
    //  F10 و Alt تنها در ویندوز منو را باز می‌کنند (۵.۱ بند ۴)؛ Alt تنها در Chord نوشتنی نیست
    assertTrue(Chord(KeyName.F10) in native)
  }

  @Test
  fun `در وب Alt با حرف‌های منوی مرورگر نمی‌آید`() {
    //  ۵.۱ بند ۱۶ — جدا از فهرستِ json تا با پاک شدنِ یک سطر از آن‌جا بی‌صدا نشکند
    val banned = "FEVSBTHD".map { KeyName.valueOf(it.toString()) }.toSet()
    val bad = KeyMap.ALL.flatMap { a -> a.web.filter { it.alt && !it.mod && it.key in banned }.map { "${a.id}: $it" } }
    assertEquals(emptyList<String>(), bad)
  }

  @Test
  fun `میان‌برِ صفحه‌ها همان ترتیبِ AppNav است`() {
    //  از متنِ AppNav.kt، نه خودِ شیء: AppNav وضعیتِ Compose دارد و در آزمونِ واحدِ اندروید بالا نمی‌آید
    val nav = File(screens!!.parentFile, "AppNav.kt").readText().substringAfter("val SHORTCUTS = listOf(").substringBefore(")\n")
    val order = Regex(""""([a-z]+)" to """").findAll(nav).map { it.groupValues[1] }.toList()
    val pages = KeyMap.ALL.filter { it.page != null }
    assertEquals(9, order.size)
    assertEquals(order, pages.map { it.page })
    pages.forEachIndexed { i, a ->
      assertEquals(listOf(Chord(KeyName.valueOf("D${i + 1}"), mod = true)), a.keys)
      assertEquals(listOf(Chord(KeyName.valueOf("D${i + 1}"), alt = true)), a.web)
    }
  }

  @Test
  fun `Chord_parse و برچسب`() {
    val c = Chord.parse("Mod+Shift+N")
    assertEquals(Chord(KeyName.N, mod = true, shift = true), c)
    assertEquals("Ctrl+Shift+N", c.label())
    assertEquals("Cmd+Shift+N", c.label(mac = true))
    assertEquals("Mod+Shift+N", c.toString())
    assertEquals(Chord(KeyName.D1, alt = true), Chord.parse("Alt+1"))
    assertEquals("Ctrl+P", KeyMap.GLOBAL_PRINT.hint())
    for (bad in listOf("Mod+Ctrl+P", "Mod+Mod+P", "Mod+", "Mod+Foo")) {
      assertTrue(bad, runCatching { Chord.parse(bad) }.isFailure)
    }
    //  هر کدِ وب فقط به یک نام می‌رسد — وگرنه دو کلیدِ «جدا» در واقع یکی‌اند
    val codes = KeyName.entries.flatMap { k -> k.codes.map { it to k } }
    assertEquals(codes.size, codes.map { it.first }.toSet().size)
  }

  @Test
  fun `صفحه‌های منتقل‌شده کلید را از KeyMap می‌گیرند`() {
    val dir = screens
    assertTrue("پوشهٔ صفحه‌ها پیدا نشد", dir != null)
    val raw = Regex("""\bKey\.[A-Z]""")
    for (name in migrated) {
      val lines = File(dir, name).readLines().filterNot { it.trimStart().startsWith("//") || it.trimStart().startsWith("*") }
      val bad = lines.withIndex().filter { raw.containsMatchIn(it.value) }.map { "$name:${it.index + 1}" }
      assertEquals("کدِ کلیدِ دستی در صفحهٔ منتقل‌شده — از KeyMap و e.matches(...) استفاده کنید", emptyList<String>(), bad)
      assertTrue("$name از KeyMap نمی‌خواند", lines.any { "KeyMap." in it })
      assertFalse("$name برچسبِ دستیِ Ctrl+… دارد — از KeyMap.….hint", lines.any { Regex(""""[^"]*Ctrl\+""").containsMatchIn(it) })
    }
  }
}
