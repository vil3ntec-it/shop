package ir.vil3ntec.tohid.core.storefront

import ir.vil3ntec.tohid.core.net.ApiClient
import ir.vil3ntec.tohid.core.net.HttpEngine
import ir.vil3ntec.tohid.core.net.TestHttpServer
import ir.vil3ntec.tohid.core.net.TokenStorage
import ir.vil3ntec.tohid.core.storefront.BulkPublish.Change
import ir.vil3ntec.tohid.core.storefront.BulkPublish.Outcome
import ir.vil3ntec.tohid.data.repo.StorefrontRepository
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** M2 — انتشارِ گروهی: بخش‌بندی، کلیدِ پایدار، صفِ دوباره‌فرستادن، دلیلِ نیامدن. */
class BulkPublishTest {

  @Test fun `پانصد کالا یک بخش، پانصد و یک دو بخش`() {
    val five = (0 until 500).map { Change("p$it", published = true) }
    assertEquals(1, BulkPublish.chunks(five).size)
    val more = BulkPublish.chunks(five + Change("x", published = true))
    assertEquals(listOf(500, 1), more.map { it.size })
  }

  @Test fun `توضیحِ بلند بدنه را از سقفِ سرور بیرون نمی‌برد`() {
    val long = "ب".repeat(2000)
    val parts = BulkPublish.chunks((0 until 500).map { Change("p$it", description = long) })
    assertTrue(parts.size > 1)
    assertTrue(parts.all { p -> p.sumOf { BulkPublish.estimateBytes(it) } <= BulkPublish.MAX_BODY_BYTES })
    assertEquals(500, parts.sumOf { it.size })
  }

  @Test fun `شناسهٔ تکراری ⇒ آخرین تغییر، توضیح بریده`() {
    val parts = BulkPublish.chunks(listOf(
      Change("a", published = true), Change("b", published = true), Change("a", published = false, description = "  x".padEnd(2100, 'y')),
    ))
    assertEquals(listOf("b", "a"), parts.single().map { it.productId })
    assertEquals(false, parts.single()[1].published)
    assertEquals(2000, parts.single()[1].description!!.length)
  }

  @Test fun `سنجش همان بازه‌های سرور`() {
    assertNull(BulkPublish.validate(Change("rice", published = true)))
    assertNull(BulkPublish.validate(Change("rice", setOnlinePrice = true, onlinePriceMinor = null)))
    assertEquals("شناسهٔ کالا معتبر نیست", BulkPublish.validate(Change("../x", published = true)))
    assertEquals("تغییری نیامده است", BulkPublish.validate(Change("rice")))
    assertTrue(BulkPublish.validate(Change("rice", setOnlinePrice = true, onlinePriceMinor = -1)) != null)
    assertTrue(BulkPublish.validate(Change("rice", setMaxQty = true, maxQtyMilli = 0)) != null)
  }

  @Test fun `کلیدِ هر بخش پایدار و در بازهٔ سرور`() {
    assertEquals("sfb-01HZX-0", BulkPublish.keyOf("01HZX", 0))
    assertEquals(BulkPublish.keyOf("01HZX", 3), BulkPublish.keyOf("01HZX", 3))
    assertTrue(Regex("^[A-Za-z0-9_.:-]{8,128}$").matches(BulkPublish.keyOf("01HZX0ABCDEFGHJKMNPQRSTVWX", 12)))
  }

  @Test fun `صف: نرسیده با همان کلید می‌ماند، رسیده دوباره نمی‌رود`() {
    val (q0, invalid) = BulkPublish.queueOf("batch1", (0 until 501).map { Change("p$it", published = true) } + Change("../bad", published = true))
    assertEquals(listOf("../bad"), invalid.map { it.first })
    assertEquals(2, q0.pending.size)
    val first = q0.next!!
    val q1 = q0.after(first, Outcome.Retry("اینترنت وصل نیست"))
    assertEquals(first.key, q1.next!!.key)
    assertEquals("اینترنت وصل نیست", q1.lastError)
    val q2 = q1.after(first, Outcome.Done(first.items.map { BulkPublish.ItemResult(it.productId, true) }))
    assertEquals(1, q2.pending.size)
    assertEquals("sfb-batch1-1", q2.next!!.key)
    val q3 = q2.after(q2.next!!, Outcome.Rejected("اشتراک لازم است"))
    assertTrue(q3.done)
    val s = BulkPublish.summarize(q3)
    assertEquals(500, s.ok)
    assertEquals(1, s.failed)
    assertEquals(0, s.waiting)
  }

  @Test fun `دلیلِ نیامدن به فارسی و به ترتیبِ اهمیت`() {
    assertNull(BulkPublish.reasonLine(emptyList()))
    assertNull(BulkPublish.reasonLine(listOf("not_published")))
    assertEquals("در بازار نمی‌آید: قیمت ندارد", BulkPublish.reasonLine(listOf("not_published", "no_price")))
    assertEquals(
      "در بازار نمی‌آید: سامانه با جریمه پنهانش کرده، در دفتر غیرفعال است، قیمت ندارد",
      BulkPublish.reasonLine(listOf("no_price", "inactive", "hidden_listing")),
    )
  }

  @Test fun `مخزن: فقط فیلدهای خواسته‌شده، با کلید؛ ۵xx ⇒ دوباره، ۴۰۳ ⇒ رد`() {
    val server = TestHttpServer()
    try {
      var sent: TestHttpServer.Request? = null
      var status = 200
      server.on("/api/v1/shop/storefront/products") { r ->
        sent = r
        if (status != 200) status to """{"error":{"code":"x","message":"نه"}}"""
        else 200 to """{"results":[{"productId":"rice","ok":true,"published":true,"notLive":["no_price"]},{"productId":"ghost","ok":false,"error":"product_not_found"}],"applied":1,"notFound":1,"published":1,"replayed":false}"""
      }
      val repo = repo(server)
      val part = BulkPublish.Part(0, "sfb-test-0", listOf(
        Change("rice", published = true, setOnlinePrice = true, onlinePriceMinor = null),
        Change("ghost", published = true),
      ))
      val done = runBlocking { repo.setProducts(part) } as Outcome.Done
      assertEquals(listOf("no_price"), done.results[0].notLive)
      assertEquals("product_not_found", done.results[1].error)
      assertEquals("PUT", sent!!.method)
      assertEquals("sfb-test-0", sent!!.headers["idempotency-key"])
      val items = Json.parseToJsonElement(sent!!.body).jsonObject["items"]!!.jsonArray
      assertEquals(JsonNull, items[0].jsonObject["onlinePriceMinor"])
      assertFalse("سقف نیامده ⇒ نفرستاده", items[0].jsonObject.containsKey("maxQtyMilli"))
      assertEquals("true", items[1].jsonObject["published"]!!.jsonPrimitive.content)

      status = 403
      assertTrue(runBlocking { repo.setProducts(part) } is Outcome.Rejected)
      status = 503
      assertTrue(runBlocking { repo.setProducts(part) } is Outcome.Retry)
    } finally {
      server.close()
    }
  }

  private fun repo(server: TestHttpServer): StorefrontRepository {
    val tokens = object : TokenStorage {
      override var accessToken: String? = "t"
      override var refreshToken: String? = null
      override var accessExpiresAt: Long = Long.MAX_VALUE
      override fun save(access: String?, refresh: String?, expiresAt: Long) {}
      override fun clear() {}
    }
    return StorefrontRepository(ApiClient(HttpEngine(baseUrl = { server.baseUrl }, allowInsecure = true), tokens))
  }
}
