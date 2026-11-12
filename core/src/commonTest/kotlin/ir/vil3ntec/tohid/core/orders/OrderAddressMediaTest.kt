package ir.vil3ntec.tohid.core.orders

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 *  CRM-09/CHK-08 — نمای «عکسِ دروازه و یادداشتِ صوتی» ِ نشانیِ سفارش در برنامهٔ دکان ([OrderAddressMedia]): همان پاسخِ
 *  `GET /shop/orders/:id/address-media` ِ سرور؛ پرچمِ خاموش، پاسخِ کهنه/ناجور یا نبودنِ هر دو ⇒ هیچ بخشی؛ نوعِ ناشناخته (نه عکس،
 *  نه صدا) ⇒ همان «نیست»؛ نشانیِ بایت فقط برای شناسهٔ درستِ سفارش.
 */
class OrderAddressMediaTest {
  private fun obj(s: String): JsonObject = Json.parseToJsonElement(s).jsonObject

  @Test fun flagOffOrNothingSavedShowsNothing() {
    assertFalse(OrderAddressMedia.visible(OrderAddressMedia.parse(obj("""{"enabled":false}"""))))
    assertFalse(OrderAddressMedia.visible(OrderAddressMedia.parse(obj("""{"enabled":true,"gatePhoto":null,"voiceNote":null}"""))))
    assertFalse(OrderAddressMedia.visible(OrderAddressMedia.parse(null)))
    assertFalse(OrderAddressMedia.visible(OrderAddressMedia.parse(obj("""{"error":{"code":"x"}}"""))))
  }

  @Test fun photoAndVoiceAreReadWithTheirTypes() {
    val v = OrderAddressMedia.parse(obj(
      """{"enabled":true,"gatePhoto":{"kind":"gatePhoto","mime":"image/jpeg","size":1407,"createdAt":5},
          "voiceNote":{"kind":"voiceNote","mime":"audio/webm","size":3625,"createdAt":6},"later":1}""",
    ))
    assertTrue(OrderAddressMedia.visible(v))
    assertEquals("image/jpeg", v.gatePhoto?.mime)
    assertEquals(3625L, v.voiceNote?.size)
    //  صدا جای عکس یا عکس جای صدا ⇒ «نیست»
    val swapped = OrderAddressMedia.parse(obj(
      """{"enabled":true,"gatePhoto":{"mime":"audio/ogg","size":9},"voiceNote":{"mime":"image/png","size":9}}""",
    ))
    assertNull(swapped.gatePhoto)
    assertNull(swapped.voiceNote)
    assertFalse(OrderAddressMedia.visible(swapped))
  }

  @Test fun pathsOnlyForAValidOrderId() {
    assertEquals("/shop/orders/ord_01HZX/address-media", OrderAddressMedia.path("ord_01HZX"))
    assertEquals("/shop/orders/ord_01HZX/address-media/gate-photo", OrderAddressMedia.bytesPath("ord_01HZX", OrderAddressMedia.Kind.GATE_PHOTO))
    assertEquals("/shop/orders/ord_01HZX/address-media/voice-note", OrderAddressMedia.bytesPath("ord_01HZX", OrderAddressMedia.Kind.VOICE_NOTE))
    assertNull(OrderAddressMedia.path("../../admin"))
    assertNull(OrderAddressMedia.bytesPath("", OrderAddressMedia.Kind.GATE_PHOTO))
  }
}
