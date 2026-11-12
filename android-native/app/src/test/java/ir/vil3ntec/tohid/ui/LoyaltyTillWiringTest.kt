package ir.vil3ntec.tohid.ui

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 *  PRM-13 — صندوقِ گوشیِ نسخهٔ ۳ (`SaleRoute`) و فروشِ سریع (`QuickSaleRoute`) هر دو به همان برگهٔ پرداخت (`CheckoutRoute`)
 *  می‌روند و آن همان راهِ صندوقِ `DeskSale` را دارد (Compose، پس آزمونِ JVM آن را نمی‌سازد؛ منطقِ خالص در `LoyaltyPointsTest`
 *  و `LoyaltyCompleteTest`): همان `rememberLoyaltyTill` ⇐ سبدِ `loyal` در جمع، اعتبار و نوارِ امتیاز ⇐ ثبت با امتیازِ
 *  `LoyaltyAtTill.points` (`TillSale.record`). خاموش ⇒ `rememberLoyaltyTill` `null` است و `loyal` همان `priced`.
 *  (`ui/screens/Sale.kt` و `QuickSale.kt` ِ پیشین فقط پیش‌نمایشِ گوشیِ DevShot‌اند — `claude/e-rows-28l`.)
 */
class LoyaltyTillWiringTest {

  private fun src(rel: String): String =
    listOf("src/main/java", "../android-native/app/src/main/java", "android-native/app/src/main/java")
      .map { File(it, rel) }.firstOrNull { it.isFile }?.readText() ?: error("$rel پیدا نشد (cwd=${File(".").absoluteFile})")

  private val v3 = "ir/vil3ntec/tohid/ui/v3/sales/"

  @Test fun `صندوق و فروشِ سریعِ گوشی هر دو از همان برگهٔ پرداخت می‌گذرند`() {
    assertTrue(src(v3 + "cart/SaleRoute.kt").contains("pay = { CheckoutSession.reset(); nav.open(SalesPaths.checkout()) }"))
    assertTrue(src(v3 + "quick/QuickSaleRoute.kt").contains("pay = { CheckoutSession.reset(); nav.open(SalesPaths.checkout(quick = true)) }"))
  }

  @Test fun `برگهٔ پرداخت امتیاز را از همان راهِ صندوقِ پیشین می‌گیرد`() {
    val s = src(v3 + "checkout/CheckoutRoute.kt")
    assertTrue("rememberLoyaltyTill", s.contains("val loyalty = rememberLoyaltyTill(data, priced, form.debtorId)"))
    assertTrue("خاموش ⇒ همان priced", s.contains("val loyal = loyalty?.cart ?: priced"))
    assertTrue("جمع با سبدِ loyal", s.contains("CheckoutModel.numbers(data, loyal, form, checkout)"))
    assertTrue("سقفِ اعتبار با جمعِ پس از امتیاز", s.contains("SalesEngine.creditOver(data, loyal, gated)"))
    assertTrue("ردیف‌های امتیاز روی همان ثبت", s.contains("val points = LoyaltyAtTill.points(context, loyalty)"))
    assertTrue("ثبت با امتیاز", Regex("""TillSale\.record\(\s*d, priced, sale, note, promo, points,""").containsMatchIn(s))
    assertTrue("نوارِ امتیاز", s.contains("points = if (loyalty != null) ({ LoyaltyBar(loyalty) }) else null,"))
    //  هیچ ثبت یا جمعی بی امتیاز نمانده
    assertTrue("ثبتِ مستقیم", !Regex("""SalesEngine\.record\(""").containsMatchIn(s))
    assertTrue("جمعِ priced", !s.contains("cartTotal(d, priced)") && !s.contains("numbers(data, priced"))
  }
}
