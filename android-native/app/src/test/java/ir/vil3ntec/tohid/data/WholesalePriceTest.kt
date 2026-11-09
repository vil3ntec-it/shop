package ir.vil3ntec.tohid.data

import ir.vil3ntec.tohid.core.sale.PriceList
import ir.vil3ntec.tohid.money
import ir.vil3ntec.tohid.ui.screens.deskWholesale
import ir.vil3ntec.tohid.ui.screens.deskWholesaleText
import ir.vil3ntec.tohid.ui.v3.products.ProductFormModel
import ir.vil3ntec.tohid.ui.v3.products.ProductRowDraft
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 *  PRD-12 — قیمتِ عمده از فرمِ کالا نوشته می‌شود. تا امروز هیچ فرمی آن را نداشت (`addProduct` همیشه ۰ می‌گذاشت و
 *  `editProduct` دست نمی‌زد) ⇒ لیستِ «عمده» ِ صندوق (POS-18، `PriceList.unitMinor`: فقط عمدهٔ مثبت) همیشه همان خرده بود.
 *  `null` در پیش‌نویس ⇒ دست نمی‌خورد (فرم‌هایی که کادرِ عمده ندارند، یعنی تنظیمِ «لیستِ قیمت» خاموش)؛ خالی در فرم ⇒ ۰ = ندارد.
 */
class WholesalePriceTest {

  private var seq = 0
  private fun newId(): String = "q${seq++}"
  private val today = "2026-11-09"

  private val tea = Product(id = "tea", name = "چای", category = "خوراکی", unit = "بسته", purchasePrice = 80.0, salePrice = 120.0, wholesalePrice = 100.0)
  private val d = ShopData(products = listOf(tea))

  private fun data(r: WarehouseEngine.Result): ShopData = (r as? WarehouseEngine.Result.Ok)?.data ?: error("رد شد: $r")
  private fun ok(r: WarehouseEngine.Result): Product = data(r).products.single { it.id == "tea" }
  private fun added(r: WarehouseEngine.Result): Product = data(r).products.single { it.name == "قند" }
  private fun failed(r: WarehouseEngine.Result): String = (r as? WarehouseEngine.Result.Failed)?.message ?: error("پذیرفته شد")
  private fun edit(wholesale: Double?, name: String = "چای") =
    WarehouseEngine.editProduct(d, "tea", WarehouseEngine.ProductDraft(name = name, unit = "بسته", purchasePrice = 80.0, salePrice = 120.0, wholesalePrice = wholesale), today, 0, ::newId)

  @Test
  fun addProduct_writesWholesale_orZeroWhenAbsent() {
    val draft = WarehouseEngine.ProductDraft(name = "قند", unit = "کیلو", purchasePrice = 50.0, salePrice = 70.0)
    assertEquals(60.0, added(WarehouseEngine.addProduct(d, draft.copy(wholesalePrice = 60.0), 0, ::newId)).wholesalePrice, 0.0)
    assertEquals(0.0, added(WarehouseEngine.addProduct(d, draft, 0, ::newId)).wholesalePrice, 0.0)
    for (bad in listOf(-1.0, Double.NaN)) {
      assertEquals(ir.vil3ntec.tohid.core.i18n.StrGoods.wholesaleBad, failed(WarehouseEngine.addProduct(d, draft.copy(wholesalePrice = bad), 0, ::newId)))
    }
  }

  @Test
  fun editProduct_setsClearsOrKeepsWholesale() {
    assertEquals(95.0, ok(edit(95.0)).wholesalePrice, 0.0)
    assertEquals(0.0, ok(edit(0.0)).wholesalePrice, 0.0)
    //  فرمی که کادرِ عمده ندارد (`null`) ⇒ همان ۱۰۰ ِ پیشین؛ تغییرِ نام آن را پاک نمی‌کند
    assertEquals(100.0, ok(edit(null, name = "چای سبز")).wholesalePrice, 0.0)
    assertEquals(ir.vil3ntec.tohid.core.i18n.StrGoods.wholesaleBad, failed(edit(-5.0)))
  }

  @Test
  fun formRoundTrip_andInlineError() {
    val row = ProductFormModel.of(tea)
    assertEquals("100", row.wholesale)
    assertEquals(100.0, ProductFormModel.draftEdit(row).wholesalePrice!!, 0.0)
    assertEquals(0.0, ProductFormModel.draftEdit(row.copy(wholesale = "")).wholesalePrice!!, 0.0)
    assertEquals("", ProductFormModel.of(tea.copy(wholesalePrice = 0.0)).wholesale)
    assertEquals(ProductFormModel.Problem.NOT_A_NUMBER, ProductFormModel.errors(row.copy(wholesale = "x"), ProductFormModel.Mode.EDIT)[ProductFormModel.Field.WHOLESALE])
    assertNull(ProductFormModel.errors(row.copy(wholesale = ""), ProductFormModel.Mode.EDIT)[ProductFormModel.Field.WHOLESALE])
    assertTrue(ProductFormModel.draftEdit(row.copy(wholesale = "x")).wholesalePrice!!.isNaN())
  }

  /** فرمِ «کالای تازه» ِ نسخهٔ ۳ (و ردیفِ «کالای تازه» ِ کامپیوتر) هم کادرِ عمده دارد — پیش‌تر فقط ویرایش */
  @Test
  fun newForm_writesWholesale_blankIsZero_badIsInline() {
    val row = ProductRowDraft(key = 1, name = "قند", category = "خوراکی", unit = "کیلو", purchase = "50", sale = "70")
    val drafts = (ProductFormModel.check(listOf(row.copy(wholesale = "60"))) as ProductFormModel.Check.Ok).drafts
    assertEquals(60.0, drafts.single().wholesalePrice!!, 0.0)
    assertEquals(60.0, added(WarehouseEngine.addProducts(d, drafts, 0, ::newId)).wholesalePrice, 0.0)
    //  خالی ⇒ null ⇒ ۰ (همان کالای تازهٔ امروز؛ تنظیمِ خاموش کادر را نشان نمی‌دهد ⇒ همیشه خالی)
    val plain = (ProductFormModel.check(listOf(row)) as ProductFormModel.Check.Ok).drafts.single()
    assertNull(plain.wholesalePrice)
    assertEquals(ProductFormModel.draftNew(row), plain)
    assertEquals(0.0, added(WarehouseEngine.addProducts(d, listOf(plain), 0, ::newId)).wholesalePrice, 0.0)
    //  نادرست ⇒ خطای همان کادر، ردیف آماده نیست (نه پیامِ کلیِ موتور پس از «ذخیره»)
    val bad = row.copy(wholesale = "x")
    assertEquals(ProductFormModel.Problem.NOT_A_NUMBER, ProductFormModel.errors(bad, ProductFormModel.Mode.NEW)[ProductFormModel.Field.WHOLESALE])
    assertFalse(ProductFormModel.ready(bad))
    assertEquals(ProductFormModel.Check.Broken(0), ProductFormModel.check(listOf(bad)))
    //  ردیفی که فقط عمده دارد دست‌نخورده نیست ⇒ بی‌صدا دور ریخته نمی‌شود
    assertFalse(ProductFormModel.untouched(ProductRowDraft(key = 2, wholesale = "60")))
    //  کادرِ کامپیوتر: خالی ⇒ ۰، نادرست ⇒ NaN (موتور رد می‌کند)؛ ۰ ِ کالا ⇒ کادرِ خالی
    assertEquals(0.0, deskWholesale(" "), 0.0)
    assertEquals(95.5, deskWholesale("95.5"), 0.0)
    assertTrue(deskWholesale("x").isNaN())
    assertEquals("", deskWholesaleText(0.0))
    assertEquals("100", deskWholesaleText(100.0))
  }

  /** PRD-14 — تغییرِ قیمتِ عمده ردِ سابقه دارد (کاربر و دلیل)، مثلِ قیمتِ فروش؛ پیش‌تر بی‌صدا عوض می‌شد */
  @Test
  fun wholesaleChange_writesHistoryRow_withUserAndReason() {
    val draft = WarehouseEngine.ProductDraft(name = "چای", unit = "بسته", purchasePrice = 80.0, salePrice = 120.0, wholesalePrice = 95.0, changedBy = "احمد", reason = "عمدهٔ تازه")
    val out = data(WarehouseEngine.editProduct(d, "tea", draft, today, 7, ::newId))
    val row = out.auditLog.single { it.type == "product_edit" }
    assertEquals("احمد", row.by)
    assertTrue(row.notes, row.notes.contains("قیمت عمده ${money(100.0)} ⇐ ${money(95.0)}") && row.notes.contains("دلیل: عمدهٔ تازه"))
    //  `priceHistory` فقط قیمتِ خرید است (سودِ گذشته از آن)؛ عمده آن را نمی‌آلاید
    assertTrue(out.priceHistory.isEmpty())
    //  پاک کردن (۰ = ندارد) هم ردیف دارد؛ فرمِ بی کادرِ عمده (`null`) یا همان عدد ⇒ بی تکهٔ عمده (`edit` دسته را هم عوض می‌کند)
    assertTrue(data(edit(0.0)).auditLog.single().notes.contains("قیمت عمده ${money(100.0)} ⇐ ${money(0.0)}"))
    for (same in listOf(null, 100.0)) assertFalse("$same", data(edit(same)).auditLog.any { it.notes.contains("قیمت عمده") })
  }

  @Test
  fun wiring_newFormAndDesktopFields_onlyWithThePriceListSetting() {
    fun src(rel: String) = listOf("src/main/java", "../android-native/app/src/main/java", "android-native/app/src/main/java")
      .map { File(it, rel) }.firstOrNull { it.isFile }?.readText() ?: error("$rel پیدا نشد")
    val form = src("ir/vil3ntec/tohid/ui/v3/products/NewProductsScreen.kt")
    assertTrue(form.contains("val wholesaleOn = remember { ir.vil3ntec.tohid.ui.components.pos.PriceListPrefs.on(context) }"))
    assertTrue(form.contains("wholesale = wholesaleOn,"))
    assertTrue(form.contains("check.drafts.map { it.copy(wholesalePrice = it.wholesalePrice.takeIf { wholesaleOn }) }"))
    val desk = src("ir/vil3ntec/tohid/ui/screens/DeskProducts.kt")
    assertEquals(2, Regex(Regex.escape("remember { ir.vil3ntec.tohid.ui.components.pos.PriceListPrefs.on(context) }")).findAll(desk).count())
    assertEquals(2, Regex(Regex.escape("wholesalePrice = if (wholesaleOn) deskWholesale(wholesale) else null,")).findAll(desk).count())
  }

  @Test
  fun wholesaleWrittenByTheForm_reachesTheTillPriceList() {
    val saved = ok(WarehouseEngine.editProduct(d, "tea", ProductFormModel.draftEdit(ProductFormModel.of(tea).copy(wholesale = "90")), today, 0, ::newId))
    val prices = PriceList.Prices(Math.round(saved.salePrice * 100.0), Math.round(saved.wholesalePrice * 100.0))
    assertEquals(9000L, PriceList.unitMinor(prices, PriceList.WHOLESALE))
    assertEquals(12000L, PriceList.unitMinor(prices, PriceList.RETAIL))
  }
}
