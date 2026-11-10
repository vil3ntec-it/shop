package ir.vil3ntec.tohid.core.image

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 *  PRD-17/PRD-27 — عکس‌های تکمیلی و عکسِ جدای ویترین روی همان قراردادِ بانکِ تصاویر
 *  (`server/test/image-bank-extras.test.js`): نشانی‌ها، بدنه‌ها، خواندنِ `/mine` و تصمیم‌ها.
 */
class ProductGalleryTest {

  private fun json(s: String): JsonObject = Json.parseToJsonElement(s).jsonObject

  private val mine = json(
    """{"items":[
      {"id":"i4","product_id":"p1","role":"promo","version":1,"urls":{"thumb":"/api/shop/images/file/i4/1/thumb","display":"/api/shop/images/file/i4/1/display"}},
      {"id":"i3","product_id":"p1","role":"detail","version":2,"urls":{"thumb":"/api/shop/images/file/i3/2/thumb"}},
      {"id":"i2","product_id":"p1","role":"main","version":1,"urls":{}},
      {"id":"i1","product_id":"p1","role":"back","version":1,"urls":{"thumb":"https://evil.example/x.jpg"}},
      {"role":"detail"}, 7
    ],"next_cursor":null}""",
  )

  @Test
  fun `خواندنِ عکس‌های کالا — نشانی فقط از خودِ بانک، ردیفِ بی شناسه کنار`() {
    val items = ProductGallery.itemsOf(mine)
    assertEquals(listOf("i4", "i3", "i2", "i1"), items.map { it.id })
    assertEquals(ProductGallery.Item("i3", "detail", 2, "/api/shop/images/file/i3/2/thumb", null), items[1])
    assertNull(items[3].thumbUrl, "نشانیِ بیرونی هرگز خوانده نمی‌شود")
    assertEquals(emptyList<ProductGallery.Item>(), ProductGallery.itemsOf(null))
    assertEquals(emptyList<ProductGallery.Item>(), ProductGallery.itemsOf(json("""{"error":{"code":"x"}}""")))
  }

  @Test
  fun `تکمیلی‌ها فقط نقش‌های تکمیلی‌اند؛ اصلی‌های پیشین و عکسِ ویترین نه؛ سقفِ هشت`() {
    assertEquals(listOf("i3", "i1"), ProductGallery.extras(ProductGallery.itemsOf(mine)).map { it.id })
    assertTrue(ProductGallery.canAdd(7))
    assertFalse(ProductGallery.canAdd(8))
    //  نقش‌ها زیرمجموعهٔ `ROLES` ِ سرور
    val server = setOf("main", "back", "label", "variant", "promo", "detail")
    assertTrue(server.containsAll(ProductGallery.EXTRA_ROLES + ProductGallery.STOREFRONT_ROLE))
    assertFalse("main" in ProductGallery.EXTRA_ROLES)
  }

  @Test
  fun `نشانی‌ها و بدنه‌ها همان قراردادِ سرور`() {
    assertEquals("/shop/images?product_id=p%201&role=detail", ProductGallery.uploadPath("p 1", "detail"))
    assertEquals("/shop/images?product_id=p1&role=promo", ProductGallery.uploadPath("p1", ProductGallery.STOREFRONT_ROLE))
    assertFailsWith<IllegalArgumentException> { ProductGallery.uploadPath("p1", "main") }
    assertFailsWith<IllegalArgumentException> { ProductGallery.uploadPath("p1", "x&role=main") }
    assertEquals("/shop/images/i3/v/2/thumb", ProductGallery.derivativePath("i3", 2, "thumb"))
    assertEquals("/shop/images/i3/v/1/display", ProductGallery.derivativePath("i3", 0, "display"))
    assertFailsWith<IllegalArgumentException> { ProductGallery.derivativePath("i3", 1, "original") }
    assertEquals("/shop/images/a%2Fb/detach", ProductGallery.detachPath("a/b"))
    assertEquals("/shop/images/mine?product_id=p1&limit=50", ProductGallery.minePath("p1"))
    assertEquals("/shop/images/select", ProductGallery.SELECT_PATH)
    assertEquals(
      json("""{"product_id":"p1","source":"shop","image_id":"i4","version":1}"""),
      ProductGallery.selectBody("p1", "i4", 1),
    )
    assertEquals("i9" to 1, ProductGallery.uploadedOf(json("""{"id":"i9","version":1,"width":800}""")))
    assertEquals("i9" to 1, ProductGallery.uploadedOf(json("""{"id":"i9"}""")))
    assertNull(ProductGallery.uploadedOf(json("""{"version":1}""")))
  }

  @Test
  fun `اندازه‌ها همان DERIV ِ سرور`() {
    assertEquals(1200, ProductGallery.DISPLAY_SIDE)
    assertEquals(600 * 1024, ProductGallery.DISPLAY_MAX_BYTES)
    assertEquals(400, ProductGallery.THUMB_SIDE)
    assertEquals(150 * 1024, ProductGallery.THUMB_MAX_BYTES)
  }

  private fun shown(source: String, id: String = "") = ImageQuality.Shown(source, id, 1, null, null, null)

  @Test
  fun `ویترین کدام عکس — جدا، همان عکسِ کالا، مرکزی یا هیچ`() {
    val items = ProductGallery.itemsOf(mine)
    assertEquals(ProductGallery.Storefront.OWN, ProductGallery.storefrontOf(shown("shop", "i4"), items))
    assertEquals(ProductGallery.Storefront.DEVICE, ProductGallery.storefrontOf(shown("shop", "i2"), items))
    assertEquals(ProductGallery.Storefront.DEVICE, ProductGallery.storefrontOf(shown("legacy_photo"), items))
    assertEquals(ProductGallery.Storefront.CENTRAL, ProductGallery.storefrontOf(shown("central", "c1"), items))
    assertEquals(ProductGallery.Storefront.NONE, ProductGallery.storefrontOf(shown("none"), items))
  }

  @Test
  fun `عکسِ تازهٔ دستگاه عکسِ جدای ویترین را کنار نمی‌زند`() {
    val items = ProductGallery.itemsOf(mine)
    assertTrue(ProductGallery.keepStorefrontPhoto(shown("shop", "i4"), items, "new1"))
    assertFalse(ProductGallery.keepStorefrontPhoto(shown("shop", "i2"), items, "new1"), "عکسِ پیشینِ دستگاه ⇒ همان رفتارِ امروز")
    assertFalse(ProductGallery.keepStorefrontPhoto(shown("shop", "i4"), items, "i4"))
    assertFalse(ProductGallery.keepStorefrontPhoto(shown("central", "c1"), items, "new1"))
    assertFalse(ProductGallery.keepStorefrontPhoto(shown("none"), items, "new1"))
    assertFalse(ProductGallery.keepStorefrontPhoto(shown("shop", "i4"), emptyList(), "new1"), "فهرست نیامد ⇒ همان رفتارِ امروز")
  }

  /** یک صفحهٔ `/mine` از [total] عکسِ هم‌طول (تازه‌ترین اول) با همان `id < cursor` و `next_cursor` ِ `shopList` ِ سرور */
  private fun pageOf(path: String, total: Int, promo: String): JsonObject {
    val ids = (total downTo 1).map { "i" + it.toString().padStart(3, '0') }
    val cursor = path.substringAfter("&cursor=", "").ifEmpty { null }
    val page = ids.filter { cursor == null || it < cursor }.take(ProductGallery.PAGE)
    val rows = page.joinToString(",") { """{"id":"$it","product_id":"p1","role":"${if (it == promo) "promo" else "main"}","version":1,"urls":{}}""" }
    val next = if (page.size == ProductGallery.PAGE) "\"" + page.last() + "\"" else "null"
    return json("""{"items":[$rows],"next_cursor":$next}""")
  }

  @Test
  fun `کالای با بیش از ۵۰ عکس — همهٔ صفحه‌ها خوانده می‌شود و عکسِ ویترین کنار نمی‌رود`() {
    val asked = ArrayList<String>()
    val items = ProductGallery.collect("p1") { asked += it; pageOf(it, 120, "i005") }
    assertEquals(120, items.size)
    assertEquals(
      listOf("", "&cursor=i071", "&cursor=i021").map { "/shop/images/mine?product_id=p1&limit=50$it" },
      asked,
    )
    assertEquals(ProductGallery.Storefront.OWN, ProductGallery.storefrontOf(shown("shop", "i005"), items))
    assertTrue(ProductGallery.keepStorefrontPhoto(shown("shop", "i005"), items, "new1"))
    assertFalse(
      ProductGallery.keepStorefrontPhoto(shown("shop", "i005"), ProductGallery.itemsOf(pageOf(asked[0], 120, "i005")), "new1"),
      "صفحهٔ نخست به‌تنهایی (رفتارِ پیشین) عکسِ ویترین را نداشت",
    )
    asked.clear()
    ProductGallery.collect("p1", enough = { all -> all.any { it.id == "i100" } }) { asked += it; pageOf(it, 120, "i005") }
    assertEquals(1, asked.size, "عکسِ نشان‌داده پیدا شد ⇒ صفحهٔ بعد خوانده نمی‌شود")
  }

  @Test
  fun `صفحه‌ها — cursor ِ درجا، صفحهٔ خالی یا بی پاسخ ⇒ پایان؛ هرگز بیش از MAX_PAGES`() {
    var n = 0
    val stuck = ProductGallery.collect("p1") { n++; json("""{"items":[{"id":"x","role":"main"}],"next_cursor":"x"}""") }
    assertEquals(2, n)
    assertEquals(2, stuck.size)
    n = 0
    assertEquals(emptyList<ProductGallery.Item>(), ProductGallery.collect("p1") { n++; json("""{"items":[],"next_cursor":"z"}""") })
    assertEquals(1, n)
    n = 0
    ProductGallery.collect("p1") { n++; null }
    assertEquals(1, n)
    n = 0
    ProductGallery.collect("p1") { n++; json("""{"items":[{"id":"x$n","role":"main"}],"next_cursor":"c$n"}""") }
    assertEquals(ProductGallery.MAX_PAGES, n)
    assertEquals("/shop/images/mine?product_id=p%201&limit=50&cursor=a%2Fb", ProductGallery.minePath("p 1", "a/b"))
    assertEquals("i9", ProductGallery.nextCursorOf(json("""{"next_cursor":"i9"}""")))
    assertNull(ProductGallery.nextCursorOf(json("""{"next_cursor":null}""")))
  }

  @Test
  fun `پاسخِ برداشتن و خواندن`() {
    assertEquals(ProductGallery.Detach.DONE, ProductGallery.detachOutcome(true, 200, ""))
    assertEquals(ProductGallery.Detach.SELECTED, ProductGallery.detachOutcome(false, 409, "image_selected"))
    assertEquals(ProductGallery.Detach.GONE, ProductGallery.detachOutcome(false, 404, "not_found"))
    assertEquals(ProductGallery.Detach.DENIED, ProductGallery.detachOutcome(false, 403, "image_bank_disabled"))
    assertEquals(ProductGallery.Detach.FAILED, ProductGallery.detachOutcome(false, 0, ""))
    assertTrue(ProductGallery.off(403))
    assertTrue(ProductGallery.off(404))
    assertFalse(ProductGallery.off(0))
    assertFalse(ProductGallery.off(500))
  }
}
