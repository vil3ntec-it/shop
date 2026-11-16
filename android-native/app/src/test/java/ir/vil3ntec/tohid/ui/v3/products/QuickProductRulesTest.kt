package ir.vil3ntec.tohid.ui.v3.products

import ir.vil3ntec.tohid.core.i18n.I18n
import ir.vil3ntec.tohid.core.i18n.Lang
import ir.vil3ntec.tohid.core.i18n.StrNewProduct
import ir.vil3ntec.tohid.data.OpeningStock
import ir.vil3ntec.tohid.data.ShopData
import ir.vil3ntec.tohid.data.WarehouseEngine
import ir.vil3ntec.tohid.ui.theme.PhoneV3Palette.Hue
import ir.vil3ntec.tohid.ui.theme.SectionColors
import ir.vil3ntec.tohid.ui.v3.V3ThumbHues
import ir.vil3ntec.tohid.ui.v3.products.ProductFormModel.Field
import ir.vil3ntec.tohid.ui.v3.products.ProductFormModel.Mode
import ir.vil3ntec.tohid.ui.v3.products.ProductFormModel.Problem
import ir.vil3ntec.tohid.ui.v3.v3ThumbHue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 *  فرمِ سادهٔ «کالای تازه» ِ گوشی (۱۴۰۵/۰۸/۲۴، `Mode.QUICK`): فقط نام و قیمتِ فروش لازم؛ قیمتِ خرید، دسته، واحد، کد، موجودی و
 *  تاریخ‌ها اختیاری (نوشته‌شده ⇒ درست)؛ واحدِ خالی ⇐ نخستین واحدِ دکان و قیمتِ خریدِ خالی ⇐ ۰؛ نخستین خطا به ترتیبِ دیده شدن؛
 *  فرمِ چندردیفی (`Mode.NEW`) همان قاعدهٔ سخت‌گیرِ پیشین. و رنگِ کاشیِ حرفِ کالا فقط از پالتِ آرامِ برند.
 */
class QuickProductRulesTest {

  private val minimal = ProductRowDraft(name = "چای سیاه", sale = "85")

  @Test fun `فقط نام و قیمتِ فروش لازم است`() {
    assertTrue(ProductFormModel.quickReady(minimal))
    assertEquals(mapOf(Field.NAME to Problem.REQUIRED, Field.SALE to Problem.REQUIRED), ProductFormModel.errors(ProductRowDraft(), Mode.QUICK))
    assertFalse(ProductFormModel.quickReady(minimal.copy(name = " ")))
    assertFalse(ProductFormModel.quickReady(minimal.copy(sale = "")))
    assertFalse(ProductFormModel.quickReady(minimal.copy(sale = "-3")))
    //  همان ردیف در فرمِ چندردیفی ناقص است (دسته، واحد و قیمتِ خرید لازم‌اند) — قاعدهٔ آن فرم دست نخورد
    assertFalse(ProductFormModel.ready(minimal))
    assertTrue(Field.CATEGORY in ProductFormModel.errors(minimal, Mode.NEW))
  }

  @Test fun `کادرهای اختیاری اگر نوشته شوند باید درست باشند`() {
    assertEquals(mapOf(Field.PURCHASE to Problem.NOT_A_NUMBER), ProductFormModel.errors(minimal.copy(purchase = "ده"), Mode.QUICK))
    assertEquals(mapOf(Field.STOCK to Problem.NEEDS_STOCK), ProductFormModel.errors(minimal.copy(expiry = "14051210"), Mode.QUICK))
    assertEquals(mapOf(Field.EXPIRY to Problem.BAD_DATE), ProductFormModel.errors(minimal.copy(stock = "3", expiry = "1405"), Mode.QUICK))
    assertTrue(ProductFormModel.quickReady(minimal.copy(purchase = "70", stock = "12", made = "14050610", expiry = "14051210", category = "خوراکی")))
  }

  @Test fun `پیش‌نویس — واحدِ خالی واحدِ نخستِ دکان، خریدِ خالی صفر، بی حداقل و عمده`() {
    val d = ProductFormModel.draftQuick(minimal, "عدد")
    assertEquals("عدد", d.unit)
    assertEquals(0.0, d.purchasePrice, 0.0)
    assertEquals(85.0, d.salePrice, 0.0)
    assertEquals("", d.category)
    assertEquals(0.0, d.minStock, 0.0)
    assertNull(d.wholesalePrice)
    assertEquals("کیلو", ProductFormModel.draftQuick(minimal.copy(unit = "کیلو"), "عدد").unit)
  }

  @Test fun `یک کالا با موجودیِ آغاز همان زنجیرهٔ «کالای تازه» و «ورودِ کالا» است`() {
    val row = ProductFormModel.quickRow(minimal.copy(purchase = "70", stock = "12"), "عدد")
    assertEquals(12.0, row.stock!!.qty, 0.0)
    var n = 0
    val r = OpeningStock.save(ShopData(), listOf(row), "2026-11-15", 1L) { "id${n++}" } as WarehouseEngine.Result.Ok
    val p = r.data.products.single()
    assertEquals("عدد" to 85.0, p.unit to p.salePrice)
    val e = r.data.warehouseEntries.single()
    assertEquals(p.id to 12.0, e.productId to e.units)
    assertEquals(70.0, e.price, 0.0)
    //  بی موجودی ⇒ فقط کالا
    val bare = OpeningStock.save(ShopData(), listOf(ProductFormModel.quickRow(minimal, "عدد")), "2026-11-15", 1L) { "x${n++}" } as WarehouseEngine.Result.Ok
    assertTrue(bare.data.warehouseEntries.isEmpty())
  }

  @Test fun `نخستین کادرِ خراب به ترتیبِ صفحه و «بیشتر» فقط برای کادرهای اختیاری`() {
    val errs = ProductFormModel.errors(ProductRowDraft(purchase = "x"), Mode.QUICK)
    assertEquals(Field.NAME, ProductFormModel.quickFirst(errs))
    assertEquals(Field.SALE, ProductFormModel.quickFirst(ProductFormModel.errors(ProductRowDraft(name = "ماست", purchase = "x"), Mode.QUICK)))
    assertEquals(Field.PURCHASE, ProductFormModel.quickFirst(ProductFormModel.errors(minimal.copy(purchase = "x"), Mode.QUICK)))
    assertFalse(Field.NAME in ProductFormModel.QUICK_MORE || Field.SALE in ProductFormModel.QUICK_MORE)
    assertTrue(listOf(Field.PURCHASE, Field.CATEGORY, Field.UNIT, Field.STOCK, Field.MADE, Field.EXPIRY).all { it in ProductFormModel.QUICK_MORE })
    //  بارکدِ اسکن‌شده ⇒ «بیشتر» باز دیده می‌شود؛ پیش‌نویسِ خالی (با واحدِ پیش‌فرض) بسته
    assertTrue(ProductFormModel.quickHasMore(ProductRowDraft(barcode = "626")))
    assertFalse(ProductFormModel.quickHasMore(ProductRowDraft(unit = "عدد")))
  }

  @Test fun `خطِ زیرِ «بیشتر» — خالی ⇒ آنچه درونش است؛ پر ⇒ همان مقدارها، بی واحدِ پیش‌فرض`() {
    I18n.source = { Lang.FA }
    assertEquals(StrNewProduct.moreHint, moreSummary(ProductRowDraft(unit = "عدد"), "عدد", "2026-11-15"))
    val filled = moreSummary(ProductRowDraft(unit = "عدد", purchase = "70", category = "خوراکی", stock = "12"), "عدد", "2026-11-15")
    assertTrue(filled, filled.contains("خوراکی") && filled.contains("70") && filled.contains("12"))
    assertFalse(filled, filled.contains(StrNewProduct.moreHint))
    assertTrue(moreSummary(ProductRowDraft(unit = "کیلو"), "عدد", "2026-11-15").contains("کیلو"))
  }

  @Test fun `کاشیِ حرفِ کالا فقط از شش خانوادهٔ آرامِ برند — هرگز عقیقِ خطر، هیچ صورتی، بی تکرار`() {
    assertEquals(6, V3ThumbHues.size)
    assertFalse("عقیقِ سرخ فقط خطر و بدهی است", Hue.CORAL in V3ThumbHues)
    assertEquals("هر خانواده یک بار", V3ThumbHues.size, V3ThumbHues.map(SectionColors::family).toSet().size)
    assertTrue(V3ThumbHues.all { SectionColors.family(it) in SectionColors.ORDER })
    assertTrue(Hue.entries.none { it.name == "PINK" || it.name == "LIME" })
    //  پایدار برای هر نام
    for (n in listOf("شکلات", "برنج", "چای سبز", "")) assertEquals(v3ThumbHue(n), v3ThumbHue(n))
    //  هر دو کاشی (ردیف و شبکه/صفحهٔ کالا) از همین می‌خوانند، نه از همهٔ `Hue`
    val main = listOf("src/main/java", "../android-native/app/src/main/java", "android-native/app/src/main/java")
      .map { File(it, "ir/vil3ntec/tohid/ui/v3") }.first { it.isDirectory }
    for (f in listOf("V3ProductThumb.kt", "products/DraftProductPhoto.kt")) {
      val src = File(main, f).readText()
      assertFalse(f, src.contains("V3Hue.entries["))
      assertTrue(f, src.contains("v3ThumbHue(name)"))
    }
  }
}
