package ir.vil3ntec.tohid.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 *  S1 — سیم‌کشیِ `LedgerOwner` ⇒ `PromoLive.reset`: فقط پس از `align` و فقط وقتی دفتر
 *  واقعاً به حساب یا دکانِ دیگری رسید. شروعِ سردِ همان حساب (`SAME`، هر بار با اینترنت)
 *  کدهای سبد و پیش‌نویس را پاک نمی‌کند — وگرنه مشتری بیش از قیمتِ گفته‌شده می‌پرداخت.
 */
class LedgerOwnerPromoTest {

  @Test fun `فقط SWITCHED و ADOPTED تخفیف‌های حافظه را دور می‌ریزند`() {
    assertFalse(LedgerOwner.promoReset(LedgerOwner.Result.SAME))
    assertTrue(LedgerOwner.promoReset(LedgerOwner.Result.SWITCHED))
    assertTrue(LedgerOwner.promoReset(LedgerOwner.Result.ADOPTED))
  }

  private fun source(): String {
    val rel = "ir/vil3ntec/tohid/data/LedgerOwner.kt"
    val f = listOf("src/main/java", "../android-native/app/src/main/java", "android-native/app/src/main/java")
      .map { File(it, rel) }.firstOrNull { it.isFile } ?: error("LedgerOwner.kt پیدا نشد (cwd=${File(".").absoluteFile})")
    return f.readText()
  }

  @Test fun `reset فقط از afterAlign و afterAlign فقط پس از align`() {
    val s = source()
    //  تنها جای صدا زدن، داخلِ afterAlign و با شرطِ promoReset
    assertEquals(1, Regex("""PromoLive\.reset\(""").findAll(s).count())
    assertTrue(s.contains("if (promoReset(r)) runCatching { ir.vil3ntec.tohid.ui.PromoLive.reset(context) }"))
    //  ورود و عوض شدنِ دکان: نتیجهٔ همان align به afterAlign می‌رود
    assertEquals(2, Regex("""return align\(context, store, user, shopId\.trim\(\)\)\.also \{ afterAlign\(context, it\) \}""").findAll(s).count())
  }
}
