package ir.vil3ntec.tohid.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 *  PRM-13 — صندوقِ `Sale.kt` (گوشیِ نسخهٔ ۳ و تبلت) و `QuickSale` همان راهِ صندوقِ `DeskSale` را دارند
 *  (Compose، پس آزمونِ JVM آن را نمی‌سازد؛ منطقِ خالص در `LoyaltyPointsTest` و `LoyaltyCompleteTest`): همان
 *  `rememberLoyaltyTill` ⇐ سبدِ `loyal` در جمع، اعتبار، پنجرهٔ پرداخت و ثبت ⇐ `LoyaltyAtTill.post` روی نتیجهٔ ثبت.
 *  خاموش ⇒ `rememberLoyaltyTill` `null` است و `loyal` همان `priced` (همان سبد و همان ثبت).
 */
class LoyaltyTillWiringTest {

  private fun src(rel: String): String =
    listOf("src/main/java", "../android-native/app/src/main/java", "android-native/app/src/main/java")
      .map { File(it, rel) }.firstOrNull { it.isFile }?.readText() ?: error("$rel پیدا نشد (cwd=${File(".").absoluteFile})")

  private val tills = mapOf(
    "Sale" to src("ir/vil3ntec/tohid/ui/screens/Sale.kt"),
    "QuickSale" to src("ir/vil3ntec/tohid/ui/screens/QuickSale.kt"),
  )

  @Test fun `هر صندوق امتیاز را از همان راهِ صندوقِ پیشین می‌گیرد`() {
    tills.forEach { (name, s) ->
      assertTrue("$name: rememberLoyaltyTill", s.contains("val loyalty = ir.vil3ntec.tohid.ui.components.pos.rememberLoyaltyTill(d, priced, cartDebtorId)"))
      assertTrue("$name: خاموش ⇒ همان priced", s.contains("val loyal = loyalty?.cart ?: priced"))
      assertTrue("$name: ثبت با سبدِ loyal", Regex("""SalesEngine\.record\(\s*d, loyal, """).containsMatchIn(s))
      assertTrue("$name: ردیف‌های امتیاز روی همان ثبت", s.contains("LoyaltyAtTill.post(context, ir.vil3ntec.tohid.data.PromoEngine.stamp("))
      assertTrue("$name: فروشنده همان ثبت‌کنندهٔ ردیف", s.contains("), promo), loyalty, options.seller) })"))
      assertTrue("$name: پنجرهٔ پرداخت", s.contains("cart = loyal, gateCart = priced, pointsOff = loyalty?.off ?: 0.0,"))
      assertTrue("$name: سقفِ اعتبار با جمعِ پس از امتیاز", s.contains("SalesEngine.creditOver(d, loyal, sale)"))
      assertTrue("$name: نوارِ امتیاز", s.contains("ir.vil3ntec.tohid.ui.components.pos.LoyaltyBar(loyalty, "))
      //  هیچ ثبت یا جمعی با `priced` نمانده (جز سفارشِ ارسال و «چرا این قیمت؟» که پیش از امتیازند)
      assertTrue("$name: ثبتِ priced", !Regex("""SalesEngine\.record\(\s*d, priced""").containsMatchIn(s))
      assertTrue("$name: جمعِ priced", !s.contains("cartTotal(d, priced)"))
    }
  }

  @Test fun `گوشی نوار را در سبد و تبلت در فهرستِ سبد دارد`() {
    val s = tills.getValue("Sale")
    assertEquals(2, Regex("""LoyaltyBar\(loyalty, """).findAll(s).count())
  }
}
