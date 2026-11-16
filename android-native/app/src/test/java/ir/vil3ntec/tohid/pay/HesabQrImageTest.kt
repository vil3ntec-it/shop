package ir.vil3ntec.tohid.pay

import ir.vil3ntec.tohid.core.audit.Sha256
import ir.vil3ntec.tohid.core.gateway.GatewayReply
import ir.vil3ntec.tohid.core.gateway.GatewayTransport
import ir.vil3ntec.tohid.core.gateway.HesabAccountView
import ir.vil3ntec.tohid.core.gateway.HesabPayAccountClient
import ir.vil3ntec.tohid.core.gateway.HesabPayClient
import ir.vil3ntec.tohid.core.gateway.TillHesab
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 *  e-paystep — عکسِ QR ِ حساب‌پیِ دکان در هسته (صاحب‌کار ۱۴۰۵/۰۸/۲۶): هشِ عکسِ فعال از `/config` فقط همراهِ متنِ QR و فقط هشِ
 *  درست؛ صاحب هشِ عکس و «عکسِ تازه در انتظار» را می‌بیند؛ ذخیره عکس را base64 یا «همان عکس» را می‌فرستد؛ صندوق بایت‌ها را فقط با
 *  همان هش می‌پذیرد.
 */
class HesabQrImageTest {

  private val hash = "a".repeat(64)

  private class Fake(private val reply: GatewayReply) : GatewayTransport {
    val sent = mutableListOf<Pair<String, Map<String, Any?>?>>()
    override suspend fun get(path: String): GatewayReply { sent += path to null; return reply }
    override suspend fun post(path: String, body: Map<String, Any?>, idempotencyKey: String?): GatewayReply { sent += path to body; return reply }
    override suspend fun put(path: String, body: Map<String, Any?>): GatewayReply { sent += path to body; return reply }
  }

  private fun config(simple: Map<String, Any?>) = mapOf(
    "hesabpay" to mapOf("enabled" to false, "flag" to true, "mode" to "simple", "simple" to simple, "payee" to mapOf("name" to "دکان", "last4" to "1222", "fp" to "hp1_x")),
  )

  @Test fun theTillGetsTheActivePictureHashOnlyWithTheQrTextAndOnlyAValidHash() {
    assertEquals(hash, TillHesab.fromMap(config(mapOf("account" to "0799111222", "qrPayload" to "HP|0799111222", "qrImage" to hash))).simple!!.qrImage)
    //  بی متنِ QR عکس نیست (سرور هم نمی‌پذیرد)؛ هشِ بد ⇒ بی عکس؛ سرورِ کهنه ⇒ بی عکس
    assertEquals("", TillHesab.fromMap(config(mapOf("account" to "0799111222", "qrPayload" to "", "qrImage" to hash))).simple!!.qrImage)
    assertEquals("", TillHesab.fromMap(config(mapOf("account" to "0799111222", "qrPayload" to "HP|0799111222", "qrImage" to "../etc/passwd"))).simple!!.qrImage)
    assertEquals("", TillHesab.fromMap(config(mapOf("account" to "0799111222", "qrPayload" to "HP|0799111222"))).simple!!.qrImage)
  }

  @Test fun theOwnerSeesThePictureAndAPendingNewPictureWithItsTime() {
    val v = HesabAccountView.fromMap(
      mapOf("pinSet" to true, "mode" to "simple", "connected" to true, "qrPayload" to "HP|0799111222", "qrImage" to hash,
        "pending" to mapOf("name" to "دکان", "last4" to "1222", "until" to 1_800_000_000_000L, "qrImage" to true)),
      opened = true,
    )!!
    assertEquals(hash, v.qrImage)
    assertTrue(v.pending!!.qrImage)
    assertEquals(1_800_000_000_000L, v.pending!!.until)
    assertFalse(HesabAccountView.fromMap(mapOf("pinSet" to true, "pending" to mapOf("until" to 5L)), opened = false)!!.pending!!.qrImage)
  }

  @Test fun savingSendsThePictureAsBase64OrKeepsTheActiveOne() = runBlocking {
    val t = Fake(GatewayReply(200, mapOf("pinSet" to true, "mode" to "simple")))
    val acc = HesabPayAccountClient(t)
    val jpeg = byteArrayOf(-1, -40, -1, -32, 1, 2, 3)
    acc.saveSimple("258014", "دکان", "", "HP|0799111222", qrImage = jpeg)
    acc.saveSimple("258014", "دکان", "", "HP|0799111222", keepQrImage = true)
    acc.saveSimple("258014", "دکان", "0799111222", "", qrImage = jpeg)
    val (a, b, c) = t.sent.map { it.second!! }
    assertArrayEquals(jpeg, kotlin.io.encoding.Base64.decode(a["qrImage"] as String))
    assertNull(a["keepQrImage"])
    assertEquals(listOf(null, true), listOf(b["qrImage"], b["keepQrImage"]))
    //  بی متنِ QR هیچ عکسی نمی‌رود
    assertEquals(listOf(null, null), listOf(c["qrImage"], c["keepQrImage"]))
  }

  @Test fun theTillAcceptsThePictureBytesOnlyWhenTheyHashToTheAddress() = runBlocking {
    val bytes = "QR-JPEG-BYTES".encodeToByteArray()
    val h = Sha256.hex(Sha256.digest(bytes))
    val ok = Fake(GatewayReply(200, mapOf("hash" to h, "data" to kotlin.io.encoding.Base64.encode(bytes))))
    assertArrayEquals(bytes, HesabPayClient(ok).qrImage(h))
    assertEquals("/shop/payments/qr-image/$h", ok.sent.single().first)
    //  بایت‌های دیگر زیرِ همان نشانی، ۴۰۴، یا نشانیِ بد ⇒ هیچ (و نشانیِ بد هرگز فرستاده نمی‌شود)
    assertNull(HesabPayClient(Fake(GatewayReply(200, mapOf("data" to kotlin.io.encoding.Base64.encode("other".encodeToByteArray()))))).qrImage(h))
    assertNull(HesabPayClient(Fake(GatewayReply(404))).qrImage(h))
    val bad = Fake(GatewayReply(200))
    assertNull(HesabPayClient(bad).qrImage("../config"))
    assertTrue(bad.sent.isEmpty())
  }
}
