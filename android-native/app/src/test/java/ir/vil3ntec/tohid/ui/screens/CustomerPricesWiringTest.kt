package ir.vil3ntec.tohid.ui.screens

import ir.vil3ntec.tohid.data.SpecialPrice
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 *  POS-18 «قیمتِ خاصِ مشتری»: مدلِ فرمِ پنجره (`CustomerPriceEditor`) و سیم‌کشی (Compose، پس متن سنجیده می‌شود) —
 *  یک جزء در پروندهٔ مشتریِ گوشیِ نسخهٔ ۳ و کامپیوتر، پشتِ پرچمِ خاموش؛ صندوقِ پرچم‌خاموش همان `repriceCart` ِ لیست؛
 *  هر دو صفحهٔ تنظیم همان کلید را می‌نویسند.
 */
class CustomerPricesWiringTest {

  private fun src(rel: String): String =
    listOf("src/main/java", "../android-native/app/src/main/java", "android-native/app/src/main/java")
      .map { File(it, rel) }.firstOrNull { it.isFile }?.readText() ?: error("$rel پیدا نشد (cwd=${File(".").absoluteFile})")

  @Test fun `فرمِ ردیف قیمت و تاریخ را می‌خواند و نادرست را نمی‌پذیرد`() {
    val ed = CustomerPriceEditor(productId = "tea", price = "95.5", from = "2026-11-01", to = "")
    assertTrue(ed.ready())
    assertEquals(SpecialPrice("tea", 9_550, "2026-11-01", null), ed.entry())
    assertFalse(ed.copy(price = "0").ready())
    assertFalse(ed.copy(price = "").ready())
    assertFalse(ed.copy(productId = "").ready())
    assertTrue(ed.copy(to = "abc").dateBroken("abc"))
    assertFalse(ed.copy(to = "abc").ready())
    assertNull(ed.copy(to = "abc").entry())
    //  ویرایش: همان کالا قفل، قیمتِ گرد بی ممیز
    assertEquals("95", CustomerPriceEditor.of(SpecialPrice("tea", 9_500)).price)
    assertEquals("tea", CustomerPriceEditor.of(SpecialPrice("tea", 9_500)).replacing)
  }

  @Test fun `بازه با گذشته و هنوز نه`() {
    assertNull(customerPriceRange(SpecialPrice("tea", 9_500), "2026-11-08"))
    val past = customerPriceRange(SpecialPrice("tea", 9_500, null, "2026-11-07"), "2026-11-08")!!
    val future = customerPriceRange(SpecialPrice("tea", 9_500, "2026-12-01", null), "2026-11-08")!!
    val now = customerPriceRange(SpecialPrice("tea", 9_500, "2026-11-01", "2026-11-30"), "2026-11-08")!!
    assertTrue(past, past.endsWith("گذشته"))
    assertTrue(future, future.endsWith("هنوز نه"))
    assertFalse(now, now.contains(" · "))
  }

  @Test fun `یک جزء در پروندهٔ مشتریِ گوشی و کامپیوتر، پشتِ پرچم`() {
    assertTrue(src("ir/vil3ntec/tohid/ui/v3/money/DebtorFileExtras.kt").contains("CustomerPricesButton(store, d, debtorId, toast,"))
    assertTrue(src("ir/vil3ntec/tohid/ui/screens/DeskCustomers.kt").contains("CustomerPricesButton(store, d, id, ::toast,"))
    val b = src("ir/vil3ntec/tohid/ui/screens/CustomerPricesAndroid.kt")
    val gate = b.indexOf("if (!on) return")
    assertTrue(b.contains("val on = remember { CustomerPricePrefs.on(context) }"))
    assertTrue(gate in 0 until b.indexOf("OutlinedButton("))
    //  مجوز همان هسته، و همان مجوز به نوشتن می‌رود
    assertTrue(b.contains("CustomerPrice.canEdit(ShopRole.current(context), ShopRole.layers(context))"))
    assertEquals(2, Regex("""entry, allowed, by|productId, allowed, by""").findAll(b).count())
  }

  @Test fun `صندوقِ پرچم‌خاموش همان بازقیمت‌گذاریِ لیست را دارد`() {
    val s = src("ir/vil3ntec/tohid/ui/components/pos/PriceListAtPosAndroid.kt")
    val listGate = s.indexOf("if (!on) return")
    val flag = s.indexOf("val special = remember { CustomerPricePrefs.on(context) }")
    val off = s.indexOf("if (!special) {")
    val legacy = s.indexOf("val next = repriceCart(d, cart, current().list)")
    val ret = s.indexOf("    return\n  }", off)
    val on = s.indexOf("CustomerPriceCart.reprice(d, cart, current(), debtorId,")
    assertTrue(listGate in 0 until flag)
    assertTrue(flag < off && off < legacy && legacy < ret && ret < on)
    assertEquals(1, Regex(Regex.escape("repriceCart(d, cart, current().list)")).findAll(s).count())
    //  POS-12/18: هر دو راه با لیستِ همان فاکتور (انتخابِ دستیِ نگه‌داشته در سبد)، نه حالتِ صفحه از فاکتورِ پیشین
    val current = s.indexOf("fun current(): PriceList.State = CartPriceList.effective(state, cartStore.priceList.value)")
    assertTrue(current in (listGate + 1) until off)
    //  فیِ دستی فقط با اجازهٔ تغییرِ قیمت
    assertTrue(s.contains("ShopRole.decide(context, \"sales.price\").decision != \"deny\""))
  }

  @Test fun `هر دو صفحهٔ تنظیم همان کلید را می‌نویسند و قلم فقط از ردیفِ صندوق مُهر می‌خورد`() {
    assertTrue(src("ir/vil3ntec/tohid/ui/v3/settings/sections/PosToggleScreensAndroid.kt").contains("CustomerPricePrefs.set(context, special)"))
    assertTrue(src("ir/vil3ntec/tohid/ui/components/pos/PriceListAtPosAndroid.kt").contains("CustomerPricePrefs.set(context, special)"))
    val engine = src("ir/vil3ntec/tohid/data/SalesEngine.kt")
    assertEquals(1, Regex("""priceSource = """).findAll(engine).count())
    assertTrue(engine.contains("priceSource = line.specialPrice?.let { ir.vil3ntec.tohid.core.sale.CustomerPrice.stamp("))
  }
}
