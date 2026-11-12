package ir.vil3ntec.tohid.core.stock

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** POS-05 / INV-03 — رزروِ سفارشِ اینترنتی در صندوق (پاسخِ `GET /api/shop/catalog/reserved`، `stock-reserve.posView`) */
class OnlineHoldsTest {

  private val server = 1_760_000_000_000L
  /** ساعتِ دستگاه عمداً ده دقیقه با سرور فرق دارد — مهلت هرگز با ساعتِ سرور مقایسه نمی‌شود */
  private val device = server - 600_000L
  private fun obj(s: String): JsonObject = Json.parseToJsonElement(s).jsonObject

  private val body = obj(
    """{"enabled":true,"now":$server,"truncated":false,"lines":[
      {"productId":"cola","qtyMilli":2000,"saleItemId":"o-A-1","expiresAt":${server + 120_000}},
      {"productId":"rice","qtyMilli":1250,"saleItemId":"o-A-2","expiresAt":null},
      {"productId":"cola","qtyMilli":1000,"saleItemId":"o-B-1","expiresAt":${server + 1_800_000}},
      {"productId":"","qtyMilli":1000,"saleItemId":"o-C-1","expiresAt":null},
      {"productId":"tea","qtyMilli":0,"saleItemId":"o-C-2","expiresAt":null},
      {"productId":"tea","qtyMilli":"5","saleItemId":"o-C-3","expiresAt":null},
      {"productId":"tea","qtyMilli":500,"expiresAt":null}
    ]}""",
  )

  @Test fun `پاسخِ سرور ⇒ قلم‌های درست؛ ناجورها کنار؛ مهلت نسبی به ساعتِ سرور`() {
    val s = OnlineHolds.parse(body, device)!!
    assertEquals(
      listOf(
        OnlineHolds.Line("cola", 2000, "o-A-1", 120_000),
        OnlineHolds.Line("rice", 1250, "o-A-2", null),
        OnlineHolds.Line("cola", 1000, "o-B-1", 1_800_000),
      ),
      s.lines,
    )
    assertEquals(device, s.fetchedAt)
  }

  @Test fun `پرچمِ خاموش، سرورِ پیشین یا پاسخِ بی ساعت ⇒ هیچ`() {
    assertNull(OnlineHolds.parse(obj("""{"enabled":false,"now":$server,"lines":[]}"""), device))
    assertNull(OnlineHolds.parse(obj("""{"lines":[{"productId":"x","qtyMilli":1000,"saleItemId":"o-x-1"}]}"""), device))
    assertNull(OnlineHolds.parse(obj("""{"enabled":true,"lines":[]}"""), device))
    assertTrue(OnlineHolds.reserved(null, device) { false }.isEmpty())
  }

  @Test fun `جمعِ هر کالا به واحد؛ مهلتِ گذشته با گذشتِ زمانِ همین دستگاه کنار می‌رود`() {
    val s = OnlineHolds.parse(body, device)!!
    assertEquals(mapOf("cola" to 3.0, "rice" to 1.25), OnlineHolds.reserved(s, device) { false })
    //  دو دقیقه بعد (ساعتِ دستگاه): مهلتِ دو دقیقه‌ای گذشت
    assertEquals(mapOf("cola" to 1.0, "rice" to 1.25), OnlineHolds.reserved(s, device + 120_000) { false })
    //  نیم ساعت بعد: فقط پس از قبول (بی مهلت) می‌ماند
    assertEquals(mapOf("rice" to 1.25), OnlineHolds.reserved(s, device + 1_800_000) { false })
  }

  @Test fun `قلمی که فروشِ تحویلش در دفترِ دستگاه هست دو بار کم نمی‌شود`() {
    val s = OnlineHolds.parse(body, device)!!
    val sold = setOf("o-A-1", "o-A-2")
    assertEquals(mapOf("cola" to 1.0), OnlineHolds.reserved(s, device) { it in sold })
  }

  @Test fun `نمای کهنه (بی اینترنتِ دراز) یا ساعتِ برگشته ⇒ هیچ؛ لرزشِ کوچکِ ساعت ⇒ همان`() {
    val s = OnlineHolds.parse(body, device)!!
    assertTrue(OnlineHolds.reserved(s, device + OnlineHolds.MAX_AGE_MS + 1) { false }.isEmpty())
    assertTrue(OnlineHolds.reserved(s, device - OnlineHolds.CLOCK_SLACK_MS - 1) { false }.isEmpty())
    assertEquals(mapOf("cola" to 3.0, "rice" to 1.25), OnlineHolds.reserved(s, device - 1_000) { false })
  }

  @Test fun `پرسشِ دوباره هر دقیقه وقتی روشن، ده دقیقه وقتی خاموش؛ ساعتِ برگشته ⇒ همین حالا`() {
    assertTrue(OnlineHolds.due(null, device, on = false))
    assertTrue(!OnlineHolds.due(device, device + OnlineHolds.REFRESH_MS - 1, on = true))
    assertTrue(OnlineHolds.due(device, device + OnlineHolds.REFRESH_MS, on = true))
    assertTrue(!OnlineHolds.due(device, device + OnlineHolds.REFRESH_MS, on = false))
    assertTrue(OnlineHolds.due(device, device + OnlineHolds.IDLE_MS, on = false))
    assertTrue(OnlineHolds.due(device, device - 1, on = true))
  }

  @Test fun `جمعِ نگه‌داشته‌های صندوق و رزروِ اینترنتی`() {
    assertEquals(mapOf("cola" to 4.0, "rice" to 1.0, "tea" to 2.0), OnlineHolds.merge(mapOf("cola" to 1.0, "rice" to 1.0), mapOf("cola" to 3.0, "tea" to 2.0)))
    val a = mapOf("cola" to 1.0)
    assertEquals(a, OnlineHolds.merge(a, emptyMap()))
    assertEquals(a, OnlineHolds.merge(emptyMap(), a))
  }

  @Test fun `نگه‌داشتن روی دستگاه رفت‌وبرگشت دارد؛ خراب یا نسخهٔ ناشناخته ⇒ هیچ`() {
    val s = OnlineHolds.parse(body, device)!!
    assertEquals(s, OnlineHolds.decode(OnlineHolds.encode(s)))
    assertNull(OnlineHolds.decode(null))
    assertNull(OnlineHolds.decode("{خراب"))
    assertNull(OnlineHolds.decode("""{"v":2,"at":1,"lines":[]}"""))
  }

  @Test fun `بیش از سقفِ سرور بریده می‌شود`() {
    val many = (1..OnlineHolds.MAX_LINES + 5).joinToString(",") { """{"productId":"p","qtyMilli":1000,"saleItemId":"o-$it-1","expiresAt":null}""" }
    val s = OnlineHolds.parse(obj("""{"enabled":true,"now":$server,"lines":[$many]}"""), device)!!
    assertEquals(OnlineHolds.MAX_LINES, s.lines.size)
  }
}
