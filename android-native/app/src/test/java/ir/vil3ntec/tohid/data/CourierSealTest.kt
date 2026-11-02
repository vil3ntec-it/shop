package ir.vil3ntec.tohid.data

import ir.vil3ntec.tohid.core.courier.MissionCard
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 *  DLV-10 — کارت‌های مأموریتِ پیک روی گوشی با رمزِ دفترِ دستگاه (`CourierSeal` ⇐ `AtRest`): خاموش همان JSON ِ امروز،
 *  روشن بی هیچ نشانی/نامِ خوانا، خواندنِ هر دو شکل، و پاکتِ ناگشوده ⇒ «کارتی نیست» (نه متنِ خراب).
 */
class CourierSealTest {

  private val card = MissionCard(
    id = "A", orderNo = 12, status = "moving", landmark = "مسجدِ جامع", addressText = "کوچهٔ سوم", lat = 34.5, lng = 69.1,
    customerName = "احمد", phoneMasked = "0791•••567", itemsCount = 2, paymentMethod = "cod",
    expectedCollectMinor = 1_240_000, cashCollectedMinor = null, fareMinor = 5_000, requiresCode = true,
    photoAllowed = true, recallRequested = false, updatedAt = 99,
  )
  private val json = CourierCodec.cardsJson(listOf(card))

  /** رمزِ ساختگیِ آزمون: سرآیندِ پاکت + xor (رمزِ واقعی `AtRest`/AES-GCM است و آزمونِ خودش را دارد) */
  private val magic = byteArrayOf(0x89.toByte(), 0x54, 0x45, 0x4E, 0x43)
  private val seal: (ByteArray) -> ByteArray = { p -> magic + p.map { (it.toInt() xor 0x5A).toByte() } }
  private val open: (ByteArray) -> ByteArray = { b ->
    require(b.size >= magic.size && b.copyOfRange(0, magic.size).contentEquals(magic)) { "پاکت نیست" }
    b.copyOfRange(magic.size, b.size).map { (it.toInt() xor 0x5A).toByte() }.toByteArray()
  }

  @Test fun `خاموش یا بی کلید ⇒ همان JSON ِ امروز، بایت‌به‌بایت`() {
    assertSame(json, CourierSeal.wrap(json) { it })
    //  رمزگذار شکست خورد ⇒ ساده (رمز هرگز جلوی نگه‌داریِ کارت را نمی‌گیرد)
    assertSame(json, CourierSeal.wrap(json) { error("کلید نیست") })
    assertEquals(json, CourierSeal.unwrap(json, open))
    assertNull(CourierSeal.unwrap(null, open))
  }

  @Test fun `روشن ⇒ هیچ نشانی، نشانه یا نامِ خوانا؛ رفت‌وبرگشت همان کارت`() {
    val stored = CourierSeal.wrap(json, seal)
    assertTrue(stored.startsWith(CourierSeal.PREFIX))
    for (plain in listOf("کوچهٔ سوم", "مسجدِ جامع", "احمد", "0791", "addressText")) assertFalse(plain, stored.contains(plain))
    assertEquals(listOf(card), CourierCodec.cards(CourierSeal.unwrap(stored, open)))
  }

  @Test fun `پاکتِ ناگشوده یا دست‌خورده ⇒ کارتی نیست، نه متنِ خراب`() {
    val stored = CourierSeal.wrap(json, seal)
    assertNull(CourierSeal.unwrap(stored) { error("کلید نیست") })
    assertNull(CourierSeal.unwrap(CourierSeal.PREFIX + "!!نه-base64", open))
    assertTrue(CourierCodec.cards(CourierSeal.unwrap(stored) { error("کلید نیست") }).isEmpty())
  }
}
