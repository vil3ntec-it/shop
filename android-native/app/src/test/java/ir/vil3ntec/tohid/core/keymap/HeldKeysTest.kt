package ir.vil3ntec.tohid.core.keymap

import ir.vil3ntec.tohid.ui.components.pos.PosKeys
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 *  POS-12 — کلیدهای فاکتورهای نگه‌داشته با هیچ کلیدِ دیگری که روی صندوق
 *  زنده است برخورد نمی‌کنند: سراسری، صندوق، پنجرهٔ پرداخت، `PosKeys`ِ خودِ
 *  صفحه، کلیدهای پنجرهٔ کامپیوتر (`desktop/Main.kt`) و کلیدهای رزرو.
 *
 *  Ctrl+Tabِ ۵.۱ بند ۱۴ روی مک همان Cmd+Tab (جابه‌جاییِ برنامه‌ها، رزرو)
 *  است؛ پس «فاکتورِ بعدی/قبلی» Ctrl+PgDn / Ctrl+PgUp است. Ctrl+۱ تا ۹ هم
 *  مالِ صفحه‌هاست (AppNav) و این‌جا به کار نمی‌رود.
 */
class HeldKeysTest {

  private val held = listOf(KeyMap.SALE_HOLD, KeyMap.SALE_HELD_LIST, KeyMap.SALE_NEXT_OPEN, KeyMap.SALE_PREV_OPEN)

  @Test
  fun `کلیدها همان جدولِ ۵_۲ و جایگزینِ Ctrl+Tab`() {
    assertEquals(listOf(Chord(KeyName.F6)), KeyMap.SALE_HOLD.keys)
    assertEquals(listOf(Chord(KeyName.F6, shift = true)), KeyMap.SALE_HELD_LIST.keys)
    assertEquals(listOf(Chord(KeyName.PageDown, mod = true)), KeyMap.SALE_NEXT_OPEN.keys)
    assertEquals(listOf(Chord(KeyName.PageUp, mod = true)), KeyMap.SALE_PREV_OPEN.keys)
    for (a in held) {
      assertEquals(a.id, "sale", a.scope)
      assertTrue("${a.id} باید در راهنمای F1 بیاید", a.status != KeyStatus.PLANNED)
    }
  }

  @Test
  fun `با هیچ کلیدِ زنده روی صندوق برخورد ندارند`() {
    //  روی صندوق: سراسری + صفحهٔ فروش + پنجره‌های آن (پرداخت)
    val scopes = KeyMap.SCOPES.filter { it.kind == KeyScope.Kind.GLOBAL || it.id == "sale" || it.parent == "sale" }.map { it.id }.toSet()
    val ids = held.map { it.id }.toSet()
    val bad = held.flatMap { h ->
      KeyMap.ALL.filter { it.scope in scopes && it.id !in ids }.flatMap { other ->
        h.keys.filter { it in other.keys }.map { "${h.id} و ${other.id}: $it" }
      }
    } + held.flatMap { h -> held.filter { it !== h }.flatMap { o -> h.keys.filter { it in o.keys }.map { "${h.id} و ${o.id}: $it" } } }
    assertEquals(emptyList<String>(), bad)
  }

  @Test
  fun `رزرو نیستند و Ctrl+Tab یا Ctrl+رقم نمی‌گیرند`() {
    val reserved = KeyMap.RESERVED_NATIVE.map { it.chord }.toSet()
    for (a in held) for (c in a.keys) {
      assertTrue("${a.id}: $c رزرو است", c !in reserved)
      assertTrue("${a.id}: Ctrl+رقم مالِ صفحه‌هاست", !(c.mod && c.key.name.matches(Regex("D[0-9]"))))
      assertTrue("${a.id}: Ctrl+Tab روی مک Cmd+Tab است", !(c.mod && c.key == KeyName.Tab))
    }
    //  کلیدهای پنجرهٔ کامپیوتر پیش از صفحه می‌رسند (desktop/Main.kt)
    val window = setOf(Chord(KeyName.F1), Chord(KeyName.F11), Chord(KeyName.Escape), Chord(KeyName.K, mod = true), Chord(KeyName.M, mod = true), Chord(KeyName.N, mod = true))
    for (a in held) assertTrue("${a.id} زیرِ کلیدِ پنجره می‌ماند", a.keys.none { it in window })
  }

  @Test
  fun `جدولِ خودِ صندوق (PosKeys) این کلیدها را نمی‌خورد`() {
    for (a in held) for (c in a.keys) {
      assertNull("${a.id}: PosKeys.screen", PosKeys.screen(c.key, c.mod))
      for (empty in listOf(true, false)) for (sel in listOf(true, false)) for (m in listOf(true, false)) {
        assertNull("${a.id}: PosKeys.field", PosKeys.field(c.key, c.mod, empty, m, sel))
      }
    }
  }
}
