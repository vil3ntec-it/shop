package ir.vil3ntec.tohid.core.shopper

import ir.vil3ntec.tohid.core.shopper.ShopperSamples.line
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertSame
import kotlin.test.assertTrue

/** ردِ ثبت ⇒ کارِ روشن روی سبد و برگه، هرگز ثبتِ بی‌صدا. */
class ShopperFlowTest {

  @AfterTest fun reset() {
    ShopperText.lang = ShopperLang.FA
  }

  private val cart = Cart("golestan", listOf(line("rice", 12050, 2000), line("oil", 30000, 1000)))

  private fun err(status: Int, code: String, data: String = "{}") =
    ShopperError(status, code, "x", Json.parseToJsonElement(data).jsonObject)

  @Test fun priceChangeRepricesAndExplainsWithTheNewTotal() {
    val e = err(409, "price_changed", """{"quote":{"lines":[{"productId":"rice","unitPriceMinor":13000}],"totalMinor":56000}}""")
    val a = CheckoutFlow.after(cart, e, 5)
    assertIs<CheckoutFlow.After.Repriced>(a)
    assertEquals(13000, a.cart.lines[0].priceMinor)
    assertEquals(56000, a.totalMinor)
    val note = CheckoutFlow.note(a, e)
    assertTrue("rice" in note && "۵۶۰ افغانی" in note, note)
    ShopperText.lang = ShopperLang.EN
    assertTrue("rice: 120.5 AFN → 130 AFN." in CheckoutFlow.note(a, e), CheckoutFlow.note(a, e))
  }

  @Test fun priceChangeWithoutQuoteIsJustAMessage() {
    assertSame(CheckoutFlow.After.Message, CheckoutFlow.after(cart, err(409, "price_changed"), 1))
  }

  @Test fun unavailableCappedReverifySlotsAndZones() {
    val u = CheckoutFlow.after(cart, err(409, "product_unavailable", """{"unavailable":["oil"]}"""), 1)
    assertIs<CheckoutFlow.After.Unavailable>(u)
    assertEquals(listOf(false, true), u.cart.lines.map { it.unavailable })
    val c = CheckoutFlow.after(cart, err(400, "qty_over_limit", """{"productId":"rice","maxQtyMilli":1000}"""), 1)
    assertIs<CheckoutFlow.After.Capped>(c)
    assertEquals(1000, CartRules.qtyOf(c.cart, "rice"))
    assertSame(CheckoutFlow.After.Reverify, CheckoutFlow.after(cart, err(401, "verification_required"), 1))
    assertSame(CheckoutFlow.After.Reverify, CheckoutFlow.after(cart, err(403, "phone_not_verified"), 1))
    val s = CheckoutFlow.after(cart, err(409, "slot_full", """{"slots":[{"startsAt":5,"endsAt":6,"available":true}]}"""), 1)
    assertIs<CheckoutFlow.After.Slots>(s)
    assertEquals(5L, s.slots?.single()?.startsAt)
    assertSame(CheckoutFlow.After.Rezone, CheckoutFlow.after(cart, err(400, "bad_zone"), 1))
    assertSame(CheckoutFlow.After.Message, CheckoutFlow.after(cart, ShopperError(0, ShopperError.NETWORK, data = JsonObject(emptyMap())), 1))
  }
}
