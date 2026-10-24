package ir.vil3ntec.tohid.ui.v3.shell

import ir.vil3ntec.tohid.data.Debtor
import ir.vil3ntec.tohid.data.Product
import ir.vil3ntec.tohid.data.Sale
import ir.vil3ntec.tohid.data.ShopData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchIndexTest {
  private val d = ShopData(
    products = listOf(
      Product("p1", name = "چای سبز", salePrice = 120.0, barcodes = listOf("6261234")),
      Product("p2", name = "قند", salePrice = 80.0),
    ),
    debtors = listOf(Debtor("d1", name = "احمد کریمی", phone = "0799123456")),
    sales = listOf(
      Sale("s1", invoiceNumber = 154, finalTotal = 1200.0, debtorId = "d1", createdAt = 2, date = "2026-10-20"),
      Sale("s2", invoiceNumber = 155, finalTotal = 300.0, createdAt = 3, date = "2026-10-21"),
    ),
  )

  @Test
  fun `نتیجه‌ها همان سند را باز می‌کنند نه فهرست را`() {
    assertEquals("products/p1", SearchIndex.search(d, "چای").of(SearchIndex.Kind.PRODUCT)!!.hits.single().route)
    assertEquals("debtors/d1", SearchIndex.search(d, "احمد").of(SearchIndex.Kind.DEBTOR)!!.hits.single().route)
    assertEquals("sales/s1", SearchIndex.search(d, "154").of(SearchIndex.Kind.INVOICE)!!.hits.single().route)
  }

  @Test
  fun `همان قاعده‌های تطبیق — بارکد، تلفن، نامِ مشتری، مبلغ، ی و ک ِ عربی`() {
    assertEquals("p1", SearchIndex.search(d, "6261").of(SearchIndex.Kind.PRODUCT)!!.hits.single().id)
    assertEquals("d1", SearchIndex.search(d, "0799").of(SearchIndex.Kind.DEBTOR)!!.hits.single().id)
    assertEquals("s1", SearchIndex.search(d, "كريمي").of(SearchIndex.Kind.INVOICE)!!.hits.single().id)
    assertEquals("s1", SearchIndex.search(d, "1,200").of(SearchIndex.Kind.INVOICE)!!.hits.single().id)
  }

  @Test
  fun `کادرِ خالی فقط بخش‌ها و Enter نخستین سند`() {
    val empty = SearchIndex.search(d, "")
    assertEquals(listOf(SearchIndex.Kind.SECTION), empty.groups.map { it.kind })
    assertNull(empty.first)
    assertEquals("products/p1", SearchIndex.search(d, "چای").first!!.route)
    assertTrue(SearchIndex.search(d, "zzzz").isEmpty)
  }

  @Test
  fun `بخش با نام یا شناسهٔ قدیم و بازهٔ تطبیق`() {
    val hits = SearchIndex.search(d, "warehouse").of(SearchIndex.Kind.SECTION)!!.hits
    assertEquals("stock", hits.single().route)
    assertEquals(0 until 2, SearchIndex.matchIn("چای سبز", SearchIndex.norm("چا")))
    assertEquals(4 until 7, SearchIndex.matchIn("چای سبز", "سبز"))
    assertNull(SearchIndex.matchIn("قند", "چای"))
  }

  @Test
  fun `اسکن ⇐ کالای ثبت‌شده یا فرمِ کالای تازه`() {
    assertEquals("products/p1", SearchIndex.scanTarget(d, "6261234"))
    assertEquals("products/new?barcode=999", SearchIndex.scanTarget(d, "999"))
  }

  @Test
  fun `فهرستِ فرمان سقفِ چهل دارد و بخش‌ها را هم می‌آورد`() {
    val many = ShopData(products = (1..80).map { Product("x$it", name = "کالا $it") })
    assertEquals(40, paletteHits(SearchIndex.search(many, "کالا")).size)
    assertTrue(paletteHits(SearchIndex.search(d, "")).all { it.kind == SearchIndex.Kind.SECTION })
  }
}
