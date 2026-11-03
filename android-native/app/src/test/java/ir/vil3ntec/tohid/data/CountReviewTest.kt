package ir.vil3ntec.tohid.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 *  INV-09 — بازبینیِ شمارش: «مستثنا» (کالایی که صریحاً بیرون گذاشته شد؛ هرگز اصلاح و هرگز صفر نمی‌شود) و «شمارشِ دوباره»
 *  (عددِ پیشینِ مغایرها کنار می‌رود تا دوباره شمرده شوند؛ تا شمرده نشده دست نمی‌خورد).
 */
class CountReviewTest {

  private var seq = 0
  private fun newId() = "c${seq++}"

  private val x = Product(id = "x", name = "نوشابه", unit = "عدد", purchasePrice = 40.0, salePrice = 60.0)
  private val y = Product(id = "y", name = "آب‌میوه", unit = "عدد", purchasePrice = 50.0, salePrice = 80.0)
  private val z = Product(id = "z", name = "آب", unit = "عدد", purchasePrice = 10.0, salePrice = 15.0)
  private val d = ShopData(
    products = listOf(x, y, z),
    warehouseEntries = listOf(
      WarehouseEntry(id = "wx", productId = "x", units = 20.0, createdAt = 100),
      WarehouseEntry(id = "wy", productId = "y", units = 6.0, createdAt = 100),
      WarehouseEntry(id = "wz", productId = "z", units = 12.0, createdAt = 100),
    ),
  )

  @Test
  fun `مستثنا نه شمرده‌نشده است نه مغایر و ثبت نمی‌شود حتی با عددِ نوشته‌شده`() {
    val counted = mapOf("x" to 20.0, "y" to 5.0, "z" to 0.0)
    val review = CountReview.of(d, d.products, counted, excluded = setOf("z"))
    assertEquals(listOf("z"), review.of(CountReview.Kind.EXCLUDED).map { it.product.id })
    assertEquals(1, review.excluded)
    assertEquals(0, review.uncounted)
    assertEquals(listOf("y"), review.of(CountReview.Kind.DIFF).map { it.product.id })
    //  اثرِ مالی فقط مغایرها (−۱ × ۵۰)؛ مستثنا ۰
    assertEquals(-50.0, review.value, 1e-9)
    assertNull(review.rows.single { it.product.id == "z" }.counted)

    val saved = CountReview.saved(counted, setOf("z"))
    assertEquals(setOf("x", "y"), saved.keys)
    val out = (WarehouseEngine.stockCount(d, saved, "", "2026-11-03", 3_000, ::newId) as WarehouseEngine.Result.Ok).data
    //  مستثنا با «۰» ِ نوشته‌شده هم صفر نمی‌شود (D-19)
    assertEquals(12.0, ShopStore.stock(out, "z"), 1e-9)
    assertEquals(5.0, ShopStore.stock(out, "y"), 1e-9)
    //  بی مستثنا همان شمرده‌ها (همان شیء)
    assertTrue(CountReview.saved(counted, emptySet()) === counted)
  }

  @Test
  fun `شمارشِ دوباره عددِ پیشینِ مغایرها را کنار می‌گذارد و فقط عددِ تازه ثبت می‌شود`() {
    //  دو دستگاه: «y» ۵ شمرده شد (مغایر)، «x» منطبق
    val merged = mapOf("x" to 20.0, "y" to 5.0)
    val first = CountReview.of(d, d.products, merged)
    val again = CountReview.recount(first)
    assertEquals(setOf("y"), again)
    //  هنوز دوباره شمرده نشده ⇒ شمرده‌نشده و دست نمی‌خورد
    val waiting = CountReview.afterRecount(merged, fresh = emptyMap(), recount = again)
    assertEquals(mapOf("x" to 20.0), waiting)
    assertEquals(setOf("y", "z"), CountReview.of(d, d.products, waiting).of(CountReview.Kind.UNCOUNTED).map { it.product.id }.toSet())
    //  شمارشِ تازه (۶) جای عددِ پیشین را می‌گیرد ⇒ منطبق و بی اصلاح
    val after = CountReview.afterRecount(merged, fresh = mapOf("y" to 6.0, "x" to 99.0), recount = again)
    assertEquals(mapOf("x" to 20.0, "y" to 6.0), after)
    assertEquals(0, CountReview.of(d, d.products, after).differing)
    //  بی شمارشِ دوباره همان شمرده‌ها
    assertTrue(CountReview.afterRecount(merged, mapOf("y" to 1.0), emptySet()) === merged)
  }
}
