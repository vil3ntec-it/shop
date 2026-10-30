package ir.vil3ntec.tohid.core.receipt

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 *  توکن و نشانیِ QRِ فاکتور با بردارهای مشترکِ `core/test-vectors/receipt-v1.json` —
 *  همان فایلی که `server/test/receipt-token.test.js` می‌خواند؛ دو پیاده‌سازی یک نشانی می‌سازند.
 */
class ReceiptTokenTest {

  private val v: JsonObject = run {
    var dir: File? = File(System.getProperty("user.dir")).absoluteFile
    while (dir != null) {
      val f = File(dir, "core/test-vectors/receipt-v1.json")
      if (f.isFile) return@run Json.parseToJsonElement(f.readText()).jsonObject
      dir = dir.parentFile
    }
    error("core/test-vectors/receipt-v1.json پیدا نشد")
  }

  private fun list(k: String) = v.getValue(k).jsonArray.map { it.jsonObject }
  private fun JsonObject.s(k: String) = getValue(k).jsonPrimitive.content
  private fun hex(s: String) = ByteArray(s.length / 2) { s.substring(it * 2, it * 2 + 2).toInt(16).toByte() }

  @Test fun `الفبا و رمزگذاریِ ۱۶ بایت همان سرور`() {
    assertEquals(v.s("alphabet"), ReceiptToken.ALPHABET)
    for (c in list("encode")) {
      val t = ReceiptToken.encodeToken(hex(c.s("hex")))
      assertEquals(c.s("hex"), c.s("token"), t)
      assertTrue(t[0] in '0'..'7')
    }
  }

  @Test fun `یکدست‌سازیِ متنِ اسکن‌شده`() {
    for (c in list("normalize")) {
      val want = c.getValue("token").let { if (it is JsonNull) null else it.jsonPrimitive.content }
      assertEquals(c.s("raw"), want, ReceiptToken.normalizeToken(c.s("raw")))
    }
  }

  @Test fun `نشانِ دکان و نشانیِ کامل`() {
    for (c in list("tags")) assertEquals(c.s("shopId"), c.s("tag"), ReceiptTokens.shopTag(c.s("shopId")))
    for (c in list("urls")) {
      assertEquals(c.s("url"), ReceiptTokens.url(c.s("base"), c.s("shopId"), c.s("token")))
      assertEquals(c.s("url"), ReceiptToken.url(c.s("base"), ReceiptTokens.shopTag(c.s("shopId")), c.s("token")))
    }
  }

  @Test fun `بی توکن یا بی دکان ⇒ بی نشانی`() {
    assertNull(ReceiptTokens.url("https://x", "shop", null))
    assertNull(ReceiptTokens.url("https://x", "shop", "not-a-token"))
    assertNull(ReceiptTokens.url("https://x", " ", ReceiptTokens.new()))
    //  ریشهٔ سرورِ خالی یا نسبی ⇒ بی QR، هرگز نشانیِ نسبی روی کاغذ
    assertNull(ReceiptTokens.url("", "shop", ReceiptTokens.new()))
    assertNull(ReceiptTokens.url("api.example.com", "shop", ReceiptTokens.new()))
    assertNull(ReceiptTokens.url("/api", "shop", ReceiptTokens.new()))
  }

  @Test fun `توکنِ تازه — شکلِ درست و هر بار تازه`() {
    val seen = HashSet<String>()
    repeat(2000) {
      val t = ReceiptTokens.new()
      assertEquals(t, ReceiptToken.normalizeToken(t))
      assertTrue("تکراری: $t", seen.add(t))
    }
  }

  @Test fun `اسکنرِ برگشت — نشانیِ QR ⇒ نشان و توکن`() {
    val t = list("encode")[4].s("token")
    val tag = list("tags")[0].s("tag")
    for (url in listOf(
      "https://api.example.com/r/$tag/$t",
      "HTTPS://API.EXAMPLE.COM/R/${tag.uppercase()}/${t.uppercase()}".replace("/R/", "/r/"),
      "http://192.168.1.10:8080/r/$tag/$t/",
      "https://h/r/$tag/$t?utm=x#y",
    )) assertEquals(url, tag to t, ReceiptToken.parse(url))
    for (bad in listOf(null, "", "TR1:01JCA00000000000000000000", "https://h/r/$tag", "https://h/r/$tag/$t/extra", "https://h/x/$tag/$t".replace("/x/", "/q/"))) {
      assertNull(bad, ReceiptToken.parse(bad))
    }
  }
}
