package ir.vil3ntec.tohid.ui.v3

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 *  HW-05 در نسخهٔ ۳: اسکن در کادرِ عدد/پول نمی‌نشیند (`V3ScanGuard`، فقط با «ابزارهای تازهٔ دستگاه»)، و صندوقِ
 *  نسخهٔ ۳ و پنجرهٔ کامپیوتر تنظیمِ بارکدخوان را پیش از نخستین کلید می‌خوانند (پیش‌تر فقط با «دستگاه‌ها»).
 *  منطقِ کلید در `core/hw/KeyScan` است (`KeyScanTest`، `KeyScanSteadyTest`)؛ این‌جا تصمیم و سیم‌کشی.
 */
class V3ScanGuardTest {

  private fun src(rel: String, vararg roots: String): String =
    roots.map { File(it, rel) }.firstOrNull { it.isFile }?.readText() ?: error("$rel پیدا نشد (cwd=${File(".").absoluteFile})")

  private fun app(rel: String) =
    src(rel, "src/main/java", "../android-native/app/src/main/java", "android-native/app/src/main/java")

  private fun desk(rel: String) =
    src(rel, "src/main/kotlin", "../../desktop/src/main/kotlin", "desktop/src/main/kotlin")

  @Test fun `فقط کادر عدد و پول و فقط با ابزارهای تازهٔ دستگاه`() {
    for (k in V3FieldKind.entries) {
      assertEquals(k.name, k == V3FieldKind.NUMBER || k == V3FieldKind.MONEY, scanGuarded(k, hwTools = true))
      assertFalse("خاموش ⇒ کادر همان امروز", scanGuarded(k, hwTools = false))
    }
  }

  @Test fun `کد به صفحهٔ گوش‌دهنده می‌رود و وگرنه به سبد`() {
    val cart = mutableListOf<String>()
    toListenerOrCart("6260100120014", offer = { true }, cart = { cart += it })
    assertTrue("صفحه برداشت ⇒ سبد نه", cart.isEmpty())
    toListenerOrCart("5901234123457", offer = { false }, cart = { cart += it })
    assertEquals(listOf("5901234123457"), cart)
  }

  @Test fun `سیم‌کشی - کادر نسخهٔ ۳ نگهبان دارد و پژواک به سبد نمی‌رود`() {
    val fields = app("ir/vil3ntec/tohid/ui/v3/V3Fields.kt")
    assertEquals(1, Regex(Regex.escape(".v3ScanGuard(kind, value, onValueChange)")).findAll(fields).count())
    val g = app("ir/vil3ntec/tohid/ui/v3/V3ScanGuard.kt")
    val gate = g.indexOf("if (!scanGuarded(kind, HwTools.on.value)) return this")
    assertTrue(gate in 0 until g.indexOf("FieldScanGuard(ScanPrefs.current())"))
    //  زمانِ یکنواخت، نه ساعتِ دیواری (و بی وابستگیِ سکو، راهِ آیفون)
    assertTrue(g.contains("val now = clock.elapsedNow().inWholeMilliseconds"))
    assertTrue(g.contains("restore(h.restore)\n    if (!h.echo) { if (toCart) toListenerOrCart(h.code) else ScanRouter.offer(h.code) }"))
    //  همان راهِ بارکدخوانِ وصل‌شده در `AppRoot` (`LiveScans`)
    assertTrue(g.contains("SaleScan.reads.trySend(SaleScan.Read(c))"))
  }

  @Test fun `هر کادرِ عددیِ نسخهٔ ۳ - حتی کادرِ کوچکِ شمارش و انتقال - نگهبانِ اسکن دارد`() {
    //  کادرِ مقدار بیرون از `V3TextField` (`DraftCountField` ِ شمارشِ انبار و انتقال) هم «تعداد» است: اسکن در آن نمی‌نشیند
    val root = listOf("src/main/java", "../android-native/app/src/main/java", "android-native/app/src/main/java")
      .map { File(it, "ir/vil3ntec/tohid/ui/v3") }.first { it.isDirectory }
    val numeric = Regex("""KeyboardType\.(Decimal|Number)\b(?!Password)""")
    val bare = root.walkTopDown().filter { it.isFile && it.extension == "kt" }
      .filter { numeric.containsMatchIn(it.readText()) && !it.readText().contains(".v3ScanGuard(") }
      .map { it.relativeTo(root).invariantSeparatorsPath }.toList()
    assertEquals("کادرِ عددیِ بی نگهبان: $bare", emptyList<String>(), bare)
    val count = app("ir/vil3ntec/tohid/ui/v3/stock/StockCountScreen.kt")
    val fn = count.substring(count.indexOf("internal fun DraftCountField("))
    //  بی رفتن به صندوق: شمارشِ نوشته‌نشده در `remember` است و با بسته شدنِ صفحه گم می‌شد
    assertTrue(fn.substring(0, fn.indexOf("decorationBox")).contains(".v3ScanGuard(V3FieldKind.NUMBER, value, onValue, toCart = false)"))
  }

  @Test fun `سیم‌کشی - صندوق نسخهٔ ۳ و پنجرهٔ کامپیوتر تنظیم را پیش از نخستین کلید می‌خوانند`() {
    //  `AppRoot` (گوشی و کامپیوتر) در آغاز؛ `HidScan` تنظیم را در نخستین کلید برمی‌دارد
    val root = app("ir/vil3ntec/tohid/ui/AppRoot.kt")
    val load = root.indexOf("ir.vil3ntec.tohid.scan.ScanPrefs.load(context)")
    assertTrue(load > 0)
    val block = root.lastIndexOf("LaunchedEffect(Unit) {", load)
    assertTrue("در همان LaunchedEffect ِ آغاز که گوشیِ جفت‌شده را می‌خواند", root.indexOf("ir.vil3ntec.tohid.sync.PhoneScanner.load(context)") in block until load)
    assertTrue(desk("ir/vil3ntec/tohid/desktop/Main.kt").contains("remember { ir.vil3ntec.tohid.scan.ScanPrefs.load(context); ir.vil3ntec.tohid.scan.HidScan() }"))
    //  «بارکدخوانِ لرزان» فقط با «ابزارهای تازهٔ دستگاه»
    assertTrue(app("ir/vil3ntec/tohid/scan/ScanPrefs.kt").contains("if (HwTools.on.value) saved.value.copy(steady = true) else ScanConfig()"))
  }
}
