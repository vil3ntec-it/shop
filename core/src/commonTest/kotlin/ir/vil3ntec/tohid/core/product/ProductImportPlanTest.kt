package ir.vil3ntec.tohid.core.product

import ir.vil3ntec.tohid.core.product.ProductImportPlan.Change
import ir.vil3ntec.tohid.core.product.ProductImportPlan.Col
import ir.vil3ntec.tohid.core.product.ProductImportPlan.Existing
import ir.vil3ntec.tohid.core.product.ProductImportPlan.Status
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 *  PRD-20: ورودِ کالا از Excel/CSV — سنجشِ سرستون، خطای هر ردیف، تکراری، ورودِ دوباره بی اثر،
 *  به‌روزرسانیِ کالای موجود فقط با انتخاب و بی پاک کردن، و فایلِ «ردیف‌های رد» برای اصلاح و تلاشِ دوباره.
 */
class ProductImportPlanTest {

  private val shop = listOf(
    Existing(id = "p1", name = "برنج", category = "خوراکی", unit = "کیلوگرم", purchaseMinor = 10_000, saleMinor = 12_000,
      minMilli = 2_000, barcodes = listOf("111")),
    //  قیمتِ خرید از کارتن: ۱۰۰۰ ÷ ۲۴ = ۴۱٫۶۷ — خروجیِ «گزارشِ محصولات» آن را ۴۲ می‌نویسد
    Existing(id = "p2", name = "روغن ۵ لیتری", category = "", unit = "عدد", purchaseMinor = 4_167, saleMinor = 5_000,
      minMilli = 0, barcodes = listOf("222", "223")),
  )

  private val head = listOf("نام محصول", "دسته", "واحد", "قیمت خرید", "قیمت فروش", "حداقل موجودی", "بارکد")
  private fun plan(vararg rows: List<String>, update: Boolean = false, header: List<String> = head) =
    ProductImportPlan.plan(listOf(header) + rows.toList(), shop, update)

  private fun ProductImportPlan.Plan.at(line: Int) = rows.first { it.line == line }

  @Test fun newRowsGetDefaultsExactMoneyAndSeveralBarcodes() {
    val p = plan(listOf("چای سبز", "", "", "۱٬۲۵۰٫۵", "12.345", "1.2345", "۳۳۳، 444;555"))
    val r = p.at(2)
    assertEquals(Status.NEW, r.status)
    assertEquals("عدد", r.unit)
    assertEquals(125_050L, r.purchaseMinor)
    //  نیمه به +∞، با رشته نه Double: ۱۲٫۳۴۵ ⇒ ۱۲٫۳۵
    assertEquals(1_235L, r.saleMinor)
    assertEquals(1_235L, r.minMilli)
    assertEquals(listOf("333", "444", "555"), r.barcodes)
    assertEquals(1, p.count(Status.NEW))
  }

  @Test fun importingTheSameFileAgainChangesNothing() {
    val p = plan(listOf("برنج", "خوراکی", "کیلوگرم", "100", "120", "2", "111"), listOf(" برنج ", "", "", "", "", "", ""))
    assertEquals(Status.SAME, p.at(2).status)
    assertEquals("p1", p.at(2).existingId)
    //  نامِ تکراری در همین فایل همچنان خطاست، حتی اگر کالا در دکان باشد
    assertEquals(Status.ERROR, p.at(3).status)
    assertTrue(p.at(3).reason.contains("ردیفِ 2"), p.at(3).reason)
    assertEquals(0, p.count(Status.NEW) + p.count(Status.UPDATE))
  }

  @Test fun differencesOnAnExistingProductAreKeptUnlessUpdateIsChosen() {
    val row = listOf("برنج", "برنج‌ها", "کیسه", "110", "", "", "111، 999")
    val kept = plan(row).at(2)
    assertEquals(Status.KEPT, kept.status)
    assertEquals(
      listOf(Change.Text(Col.CATEGORY, "خوراکی", "برنج‌ها"), Change.Price(Col.PURCHASE, 10_000, 11_000), Change.AddBarcodes(listOf("999"))),
      kept.changes,
    )
    //  واحد هرگز با فایل عوض نمی‌شود — معنای موجودیِ گذشته عوض می‌شد
    assertTrue(kept.notes.single().contains("کیسه"))
    val upd = plan(row, update = true).at(2)
    assertEquals(Status.UPDATE, upd.status)
    assertEquals(kept.changes, upd.changes)
  }

  @Test fun updateNeverClearsAFieldFromABlankCell() {
    val r = plan(listOf("برنج", "", "", "", "", "", ""), update = true).at(2)
    assertEquals(Status.SAME, r.status)
    assertNull(r.saleMinor)
    assertNull(r.category)
    assertEquals(emptyList(), r.changes)
  }

  @Test fun theExportsWholeAfghaniRoundingIsNotAPriceChange() {
    //  ۴۱٫۶۷ در خروجی ۴۲ نوشته شد ⇒ برگشتِ همان فایل «بی تغییر» است، نه ۴۱٫۶۷ ⇐ ۴۲
    assertEquals(Status.SAME, plan(listOf("روغن ۵ لیتری", "", "عدد", "42", "50", "0", "222")).at(2).status)
    //  ولی ۴۲٫۰۰ ِ صریح یا ۴۳ تغییر است
    assertEquals(listOf<Change>(Change.Price(Col.PURCHASE, 4_167, 4_300)),
      plan(listOf("روغن ۵ لیتری", "", "", "43", "", "", "")).at(2).changes)
    assertEquals(listOf<Change>(Change.Price(Col.PURCHASE, 4_167, 4_200)),
      plan(listOf("روغن ۵ لیتری", "", "", "42.00", "", "", "")).at(2).changes)
  }

  @Test fun everyRejectedRowSaysWhy() {
    val p = plan(
      listOf("", "خوراکی", "", "1", "", "", ""),                       // ۲ بی نام
      listOf("ش".repeat(ProductImportPlan.MAX_NAME + 1), "", "", "", "", "", ""),
      listOf("قند", "", "", "abc", "", "", ""),                         // ۴
      listOf("نمک", "", "", "", "-5", "", ""),                          // ۵
      listOf("صابون", "", "", "", "", "", "6.26E+12"),                  // ۶ بارکد در نمادِ علمی
      listOf("شامپو", "", "", "", "", "", "222"),                       // ۷ بارکدِ کالای دیگر
      listOf("مسواک", "", "", "", "", "", "777"),                       // ۸
      listOf("خمیردندان", "", "", "", "", "", "777"),                   // ۹ همان بارکدِ ردیفِ ۸
      listOf("کبریت", "", "", "", "", "x".repeat(3), ""),                // ۱۰
      listOf("شمع", "", "", "", "", "", (1..ProductBarcodes.MAX + 1).joinToString("،") { "9$it" }),
    )
    assertEquals(listOf(2, 3, 4, 5, 6, 7, 9, 10, 11), p.rows.filter { it.status == Status.ERROR }.map { it.line })
    assertEquals(Status.NEW, p.at(8).status)
    assertTrue(p.at(2).reason.contains("نام"))
    assertTrue(p.at(4).reason.contains("قیمتِ خرید") && p.at(4).reason.contains("abc"), p.at(4).reason)
    assertTrue(p.at(5).reason.contains("قیمتِ فروش"))
    assertTrue(p.at(6).reason.contains("علمی"))
    assertTrue(p.at(7).reason.contains("روغن ۵ لیتری"), p.at(7).reason)
    assertTrue(p.at(9).reason.contains("ردیفِ 8"), p.at(9).reason)
    assertTrue(p.at(10).reason.contains("حداقل"))
    assertTrue(p.at(11).reason.contains("بارکد"))
  }

  @Test fun anExistingProductMayNotTakeAnotherProductsBarcodeEvenInUpdate() {
    val r = plan(listOf("برنج", "", "", "", "", "", "222"), update = true).at(2)
    assertEquals(Status.ERROR, r.status)
    val many = plan(listOf("برنج", "", "", "", "", "", (1..ProductBarcodes.MAX).joinToString(",") { "8$it" }), update = true).at(2)
    assertEquals(Status.ERROR, many.status)
  }

  @Test fun packBarcodesAreNeitherAnotherProductsNorAUnitOfTheirOwn() {
    //  PRD-04: کارتنِ «برنج» بارکدِ 777 دارد
    val withPack = shop.map { if (it.id == "p1") it.copy(reserved = listOf("777")) else it }
    val other = ProductImportPlan.plan(listOf(head, listOf("قند", "", "", "", "", "", "777")), withPack, true).at(2)
    assertEquals(Status.ERROR, other.status)
    assertTrue(other.reason.contains("برنج"), other.reason)
    val own = ProductImportPlan.plan(listOf(head, listOf("برنج", "", "", "", "", "", "777")), withPack, true).at(2)
    assertEquals(Status.ERROR, own.status)
    assertTrue(own.reason.contains("بسته"), own.reason)
  }

  @Test fun headerSchemaIsChecked() {
    assertTrue(ProductImportPlan.plan(listOf(listOf("a", "b"), listOf("1", "2")), shop, false).problem!!.contains("نام"))
    assertTrue(ProductImportPlan.plan(emptyList(), shop, false).problem!!.contains("نام"))
    val twice = ProductImportPlan.plan(listOf(listOf("name", "price", "قیمت فروش")), shop, false)
    assertTrue(twice.problem!!.contains("دو بار"), twice.problem)
    //  ستونِ ناشناس نادیده و نامش در پیش‌نمایش؛ ستون‌های خودِ «فایلِ خطاها» بی‌صدا
    val p = plan(listOf("زعفران", "5"), header = listOf("Name", "موجودی", ProductImportPlan.LINE_HEADER, ProductImportPlan.REASON_HEADER))
    assertNull(p.problem)
    assertEquals(listOf("موجودی"), p.ignored)
    assertEquals(Status.NEW, p.at(2).status)
  }

  @Test fun blankRowsKeepExcelNumbering() {
    val p = plan(listOf(), listOf("", " ", ""), listOf("زیره"))
    assertEquals(listOf(4), p.rows.map { it.line })
  }

  @Test fun errorReportCarriesTheOriginalCellsAndRetryIgnoresTheReason() {
    val p = plan(listOf("قند", "", "", "abc", "", "", ""), listOf("نمک", "", "", "", "", "", ""), listOf("", "x"))
    val report = ProductImportPlan.errorReport(p)
    assertEquals(head + listOf(ProductImportPlan.LINE_HEADER, ProductImportPlan.REASON_HEADER), report.first())
    assertEquals(listOf("2", "4"), report.drop(1).map { it[head.size] })
    assertEquals("abc", report[1][3])
    //  کاربر قیمت را در همان فایل درست می‌کند و دوباره می‌دهد
    val fixed = report.mapIndexed { i, r -> if (i == 1) r.toMutableList().also { it[3] = "40" } else r }
    val again = ProductImportPlan.plan(fixed, shop, false)
    assertEquals(Status.NEW, again.at(2).status)
    assertEquals(4_000L, again.at(2).purchaseMinor)
    assertEquals(Status.ERROR, again.at(3).status)
    //  گزارشِ دوم شمارهٔ ردیفِ فایلِ **اصلی** را نگه می‌دارد (این‌جا ردیفِ ۳، در اصل ۴)
    assertEquals(listOf("4"), ProductImportPlan.errorReport(again).drop(1).map { it[head.size] })
    assertEquals(head.size + 2, ProductImportPlan.errorReport(again).first().size)
    //  نه خطایی ⇒ نه گزارشی
    assertEquals(1, ProductImportPlan.errorReport(plan(listOf("زیره"))).size)
  }

  @Test fun similarNamesAreAWarningOnlyForNewRows() {
    val p = plan(listOf("برنج‌", "", "", "", "", "", ""), listOf("چای سبز"), listOf("چای‌سبز"), listOf("سبز چای "), listOf(" برنج"))
    //  «برنج‌» (نیم‌فاصلهٔ آخر) برای موتور نامِ دیگری است ⇒ تازه، با هشدارِ مشابهِ کالای دکان
    assertEquals(Status.NEW, p.at(2).status)
    assertEquals(listOf(2, 4, 5), p.similar.map { it.line })
    assertEquals(ProductImportPlan.Similar(2, "برنج‌", "برنج", inFile = false), p.similar[0])
    assertTrue(p.similar.drop(1).all { it.inFile && (it.other == "چای سبز" || it.other == "چای‌سبز") }, p.similar.toString())
    //  همان نام (فاصلهٔ اضافه) کالای موجود است، نه «مشابه»
    assertEquals(Status.SAME, p.at(6).status)
  }

  @Test fun moneyAndQuantityParsing() {
    assertEquals(0L, ProductImportPlan.scaled("0", 2))
    assertEquals(150L, ProductImportPlan.scaled("1.5", 2))
    assertEquals(100_000L, ProductImportPlan.scaled("1e3", 2))
    assertEquals(120_000L, ProductImportPlan.scaled("1.2E+3", 2))
    assertEquals(1L, ProductImportPlan.scaled("0.005", 2))
    assertEquals(0L, ProductImportPlan.scaled("0.0049", 2))
    assertEquals(-150L, ProductImportPlan.scaled("-1.5", 2))
    assertEquals(25_000L, ProductImportPlan.scaled("250 ؋", 2))
    assertNull(ProductImportPlan.scaled("1.2.3", 2))
    assertNull(ProductImportPlan.scaled("12a", 2))
    assertNull(ProductImportPlan.scaled(".", 2))
    assertNull(ProductImportPlan.scaled("9".repeat(20), 2))
    assertNull(ProductImportPlan.scaled("1e400", 2))
  }

  @Test fun fingerprintIsCarriedForIdempotency() {
    val a = plan(listOf("زیره", "", "", "", "", "", ""))
    assertEquals(a.fingerprint, plan(listOf("زیره ")).fingerprint)
    assertTrue(a.fingerprint != plan(listOf("زیرهٔ سبز")).fingerprint)
  }
}
