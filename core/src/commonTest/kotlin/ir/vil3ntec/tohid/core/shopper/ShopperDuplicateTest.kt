package ir.vil3ntec.tohid.core.shopper

import ir.vil3ntec.tohid.core.shopper.ShopperSamples.line
import ir.vil3ntec.tohid.core.shopper.ShopperSamples.page
import ir.vil3ntec.tohid.core.shopper.ShopperSamples.shop
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 *  S41 «ادغام یا سفارشِ دوم؟» در برنامهٔ خریدِ مشتری — همان قراردادِ ویترین (`store/checkout.js`، `lib/order-risk.js`):
 *  ۴۰۹ ِ `duplicate_order` با `ask: track_or_second` و توکن ⇒ پرسش با شمارهٔ سفارشِ پیشین؛ «سفارشِ دوم» ⇒ همان سبد با
 *  `secondOrder: true` و کلیدِ یکتاییِ تازه؛ بی پرسش یا بی توکن ⇒ همان پیامِ پیشین. بدنهٔ عادی هیچ فیلدِ تازه‌ای ندارد.
 */
class ShopperDuplicateTest {

  private val ready = CheckoutForm(name = "احمد", phone = "۰۷۰۰ ۰۰۰ ۰۰۲", addressText = "کارتهٔ سه، سرکِ اول")
  private val cart = Cart("golestan", listOf(line("a", 100, 1000)))

  private fun dup(data: String) = ShopperError(409, ShopperError.DUPLICATE_ORDER, "همین سفارش…", Json.parseToJsonElement(data).jsonObject, 300)

  @AfterTest fun back() { ShopperText.lang = ShopperLang.FA }

  @Test fun duplicateRefusalAsksTrackOrSecondWithThePreviousOrderNumber() {
    ShopperText.lang = ShopperLang.FA
    val e = dup("""{"reason":"same_items_same_place","ask":"track_or_second","orderNo":42,"windowMin":10,"retryAfter":300,"token":"tok-42"}""")
    assertEquals(42L to "tok-42", e.duplicate())
    val after = CheckoutFlow.after(cart, e, 1)
    assertEquals(CheckoutFlow.After.Duplicate(42, "tok-42"), after)
    val note = CheckoutFlow.note(after, e)
    assertEquals(ShopperText.dupAsk(42), note)
    assertTrue(note.contains("۴۲"), note)
    assertEquals("همان را دنبال کن", ShopperText.dupTrack)
    assertEquals("سفارشِ دوم ثبت شود", ShopperText.dupSecond)
    ShopperText.lang = ShopperLang.EN
    assertEquals("Follow that order", ShopperText.dupTrack)
    assertTrue(ShopperText.dupAsk(42).contains("number 42"))
  }

  @Test fun duplicateWithoutAskOrTokenStaysAPlainMessage() {
    for (data in listOf("""{"orderNo":42,"token":"tok-42"}""", """{"ask":"track_or_second","orderNo":42}""", """{"ask":"track_or_second","token":""}""")) {
      val e = dup(data)
      assertNull(e.duplicate(), data)
      assertEquals(CheckoutFlow.After.Message, CheckoutFlow.after(cart, e, 1), data)
    }
    //  کدِ دیگر هرگز پرسشِ تکراری نیست
    val other = ShopperError(409, "new_customer_limit", data = Json.parseToJsonElement("""{"ask":"track_or_second","orderNo":1,"token":"t"}""").jsonObject)
    assertNull(other.duplicate())
  }

  @Test fun shopOfflineRefusalHasItsOwnPlainMessage() {
    ShopperText.lang = ShopperLang.FA
    val e = ShopperError(409, "store_offline", "server", Json.parseToJsonElement("""{"reason":"shop_offline","lastConfirmedAt":1}""").jsonObject)
    assertEquals(CheckoutFlow.After.Message, CheckoutFlow.after(cart, e, 1))
    assertTrue(ShopperText.error(e).startsWith("دکان همین حالا به اینترنت وصل نیست"), ShopperText.error(e))
  }

  @Test fun secondOrderIsSentOnlyWhenChosenAndGetsAFreshKey() {
    val r1 = CheckoutRules.request(page(shop(min = 0)), cart, ready, "+93700000002", "fa", null)
    assertNull(r1.secondOrder)
    assertFalse(CheckoutKeys.json(r1).contains("secondOrder"), "بدنهٔ عادی همان بدنهٔ پیشین است")
    val r2 = r1.copy(secondOrder = true)
    assertTrue(Json.parseToJsonElement(CheckoutKeys.json(r2)).jsonObject.getValue("secondOrder").jsonPrimitive.boolean)
    var n = 0
    val fresh = { "app-key-${++n}" }
    val m1 = CheckoutKeys.keyFor(null, r1, fresh)
    val m2 = CheckoutKeys.keyFor(m1, r2, fresh)
    assertNotEquals(m1.key, m2.key, "سفارشِ دوم با کلیدِ یکتاییِ پیشین «replayed» می‌شد")
  }
}
