package ir.vil3ntec.tohid.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 *  S1 — سیم‌کشیِ `PromoLive` (حالتِ Compose، پس آزمونِ JVM آن را نمی‌سازد): همان منطقِ خالصِ
 *  آزموده (`PromoEngine.forAccount`، `PromoEngine.accepts`، `PromoEngine.Binding`) واقعاً در
 *  همان جاهایی صدا زده می‌شود که حالِ حسابِ قبلی می‌توانست بنشیند.
 */
class PromoLiveWiringTest {

  private fun src(rel: String): String =
    listOf("src/main/java", "../android-native/app/src/main/java", "android-native/app/src/main/java")
      .map { File(it, rel) }.firstOrNull { it.isFile }?.readText() ?: error("$rel پیدا نشد (cwd=${File(".").absoluteFile})")

  private val live = src("ir/vil3ntec/tohid/ui/PromoLive.kt")

  private fun body(name: String): String {
    val i = live.indexOf("fun $name(")
    assertTrue("$name پیدا نشد", i >= 0)
    val j = live.indexOf("\n  }\n", i)
    return live.substring(i, j)
  }

  @Test fun `صندوق فقط حالِ حسابِ همین لحظه را می‌بیند`() {
    val b = body("rememberOutcome")
    assertTrue(b.contains("val key = account(context)"))
    assertTrue(b.contains("PromoEngine.forAccount(snap.value, key)"))
  }

  @Test fun `پاسخِ سرور و نسخهٔ دستگاه فقط برای همان حساب`() {
    assertTrue(body("publish").contains("if (!PromoEngine.accepts(bound.key, key, account(context))) return"))
    assertTrue(body("saveCache").contains("if (account(context) != key) return"))
    assertTrue(body("loadCache").contains("""if (o["account"]?.jsonPrimitive?.content != key) return"""))
  }

  @Test fun `reset همه چیزِ حسابِ قبلی را پاک می‌کند — حافظه، کدها، پیش‌نویس و نسخهٔ دستگاه`() {
    val b = body("reset")
    for (x in listOf("bound.reset()", "snap.value = PromoEngine.Snapshot()", "coupons.value = emptyList()", "draft?.forgetCoupons()", "remove(KEY)", "epoch.intValue++")) {
      assertTrue(x, b.contains(x))
    }
  }

  @Test fun `خروج از حساب reset را پس از signOut صدا می‌زند`() {
    val p = src("ir/vil3ntec/tohid/ui/screens/Profile.kt")
    val out = p.indexOf("state.signOut()")
    val reset = p.indexOf("ir.vil3ntec.tohid.ui.PromoLive.reset(context)")
    assertTrue(out in 0 until reset)
    assertEquals(1, Regex("""PromoLive\.reset\(""").findAll(p).count())
  }
}
