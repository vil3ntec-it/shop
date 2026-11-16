package ir.vil3ntec.tohid.core.support

import ir.vil3ntec.tohid.pay.HesabPayIntentFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 *  p0r3 — دو درِ «از پشتیبانی بخواه» که پیش‌تر نبود: صفحهٔ قفلِ بخشِ پولی کلیدِ خودِ قابلیت را می‌فرستد (`plan_required:<قابلیت>`)،
 *  نه همیشه `subscription_expired`؛ و «حساب‌پی برای این دکان روشن نیست» ِ صندوق `flag_off:pay.hesabpay` است.
 */
class SupportLockedAskTest {

  @Test fun `بخشِ بیرون از اشتراک قابلیتِ خودش را می‌فرستد و اشتراکِ تمام‌شده همان پیشین`() {
    assertEquals("plan_required:debtors", SupportProblem.forLocked("debtors", expired = false))
    assertEquals("plan_required:sales", SupportProblem.forLocked("sales", expired = false))
    assertEquals("subscription_expired", SupportProblem.forLocked("debtors", expired = true))
    assertEquals("subscription_expired", SupportProblem.forLocked(null, expired = false))
    //  کلیدِ ناشناخته هرگز کدِ نامجاز نمی‌سازد (سرور ۴۰۰ می‌دهد)
    assertEquals("subscription_expired", SupportProblem.forLocked("nope", expired = false))
  }

  @Test fun `حساب‌پیِ خاموشِ صندوق همان پرچمِ pay_hesabpay است`() {
    assertEquals("flag_off:pay.hesabpay", HesabPayIntentFlow.HESAB_OFF)
    assertNotNull(SupportProblem.parse(HesabPayIntentFlow.HESAB_OFF))
    assertEquals(HesabPayIntentFlow.HESAB_OFF, HesabPayIntentFlow.Step.Stop("x", false, HesabPayIntentFlow.HESAB_OFF).support)
  }

  private val main: File = listOf("src/main/java", "../android-native/app/src/main/java", "android-native/app/src/main/java")
    .map { File(it, "ir/vil3ntec/tohid") }.first { it.isDirectory }

  @Test fun `هر درِ قفلِ بخشِ پولی کلیدِ قابلیتش را دارد`() {
    val gates = main.walkTopDown().filter { it.isFile && it.extension == "kt" }.flatMap { f ->
      Regex("""VipGate\(Str\.\w+[^)]*\)""").findAll(f.readText()).map { f.name to it.value }
    }.toList()
    assertTrue("دست‌کم یک در: $gates", gates.isNotEmpty())
    val bare = gates.filter { (_, call) -> !Regex("""feature = "(\w+)"""").containsMatchIn(call) }
    assertTrue("درِ بی کلیدِ قابلیت: $bare", bare.isEmpty())
    for ((_, call) in gates) {
      val key = Regex("""feature = "(\w+)"""").find(call)!!.groupValues[1]
      assertNotNull("کلیدِ ناشناختهٔ $key", SupportProblem.parse("${SupportProblem.PLAN_REQUIRED}:$key"))
    }
    //  صندوق: «روشن نیست» با کدِ پشتیبانی، و صفحهٔ صندوق دکمه‌اش را کنارِ همان پیام دارد
    val flow = File(main, "pay/HesabPayIntentFlow.kt").readText()
    assertEquals(2, Regex("""stop\(StrPay\.disabled, false, HESAB_OFF\)""").findAll(flow).count())
    assertTrue(File(main, "ui/v3/sales/checkout/HesabTillRoute.kt").readText().contains("stop.support?.let"))
  }
}
