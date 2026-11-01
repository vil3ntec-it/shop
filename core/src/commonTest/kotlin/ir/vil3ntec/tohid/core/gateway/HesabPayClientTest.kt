package ir.vil3ntec.tohid.core.gateway

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** قراردادِ برنامه با درهای `/shop/payments` (docs/PAYMENTS-HESABPAY-fa.md) */
class HesabPayClientTest {

  @Test
  fun configEnabledOnlyWhenServerSaysTrue() {
    assertTrue(runSync { HesabPayClient(FakeTransport(ok(mapOf("hesabpay" to mapOf("enabled" to true))))).enabled() })
    assertFalse(runSync { HesabPayClient(FakeTransport(ok(mapOf("hesabpay" to mapOf("enabled" to false))))).enabled() })
    //  سرورِ کهنه (۴۰۴)، بی‌نت، پاسخِ بی‌شکل ⇐ پنهان
    assertFalse(runSync { HesabPayClient(FakeTransport(GatewayReply(404, code = "not_found"))).enabled() })
    assertFalse(runSync { HesabPayClient(FakeTransport(GatewayReply(0))).enabled() })
    assertFalse(runSync { HesabPayClient(FakeTransport(ok(mapOf("hesabpay" to "yes")))).enabled() })
    assertFalse(runSync { HesabPayClient(FakeTransport(ok(mapOf("hesabpay" to mapOf("enabled" to "true"))))).enabled() })
  }

  @Test
  fun createSendsExactContractBodyAndKey() {
    val t = FakeTransport(ok(intentJson(), 201))
    val r = runSync { HesabPayClient(t).create("sale1", 145000, "pos-sale1-abcdefgh") } as GatewayCall.Ok<PaymentIntent>
    assertEquals(listOf("POST /shop/payments/intents"), t.calls)
    assertEquals(
      mapOf("kind" to "pos_sale", "refId" to "sale1", "amount" to 145000L, "currency" to "AFN", "idempotencyKey" to "pos-sale1-abcdefgh"),
      t.bodies.single(),
    )
    assertEquals("pos-sale1-abcdefgh", t.keys.single())
    assertEquals(145000L, r.value.amountMinor)
    assertEquals("https://pay.example/c/pi_1", r.value.qrPayload)
  }

  @Test
  fun createRejectsBadInputWithoutCallingServer() {
    val t = FakeTransport()
    val c = HesabPayClient(t)
    assertIs<GatewayCall.Refused>(runSync { c.create("sale 1", 100, "pos-sale1-abcdefgh") })
    assertIs<GatewayCall.Refused>(runSync { c.create("sale1", 0, "pos-sale1-abcdefgh") })
    assertIs<GatewayCall.Refused>(runSync { c.create("sale1", -5, "pos-sale1-abcdefgh") })
    assertIs<GatewayCall.Refused>(runSync { c.create("sale1", 100, "short") })
    assertTrue(t.calls.isEmpty())
  }

  @Test
  fun errorsMapToFeatureStates() {
    fun create(reply: GatewayReply) = runSync { HesabPayClient(FakeTransport(reply)).create("sale1", 100, "pos-sale1-abcdefgh") }
    assertEquals(GatewayCall.Disabled, create(GatewayReply(403, code = "hesabpay_disabled")))
    assertEquals(GatewayCall.Disabled, create(GatewayReply(404, code = "not_found")))
    assertIs<GatewayCall.Offline>(create(GatewayReply(0, message = "اینترنت وصل نیست")))
    assertIs<GatewayCall.Offline>(create(GatewayReply(429, code = "rate_limited")))
    assertIs<GatewayCall.Offline>(create(GatewayReply(503, code = "x")))
    //  ۵۰۲ ِ درگاه: قصد failed شد ⇐ تلاشِ بعدی با کلیدِ تازه، پس «رد» است نه «بی‌نت»
    assertEquals("provider_unavailable", (create(GatewayReply(502, code = "provider_unavailable")) as GatewayCall.Refused).code)
    assertEquals("already_paid", (create(GatewayReply(409, code = "already_paid")) as GatewayCall.Refused).code)
    assertEquals("intent_active", (create(GatewayReply(409, code = "intent_active")) as GatewayCall.Refused).code)
    //  ۲۰۰ با بدنهٔ بی‌شکل هرگز «قصد» نیست
    assertIs<GatewayCall.Offline>(create(ok(mapOf("id" to "pi_1"))))
  }

  @Test
  fun fetchUsesLongPollAndCancelPath() {
    val t = FakeTransport(ok(intentJson()), ok(intentJson(status = "cancelled")), GatewayReply(404, code = "payment_not_found"))
    val c = HesabPayClient(t)
    runSync { c.fetch("pi_1", 20) }
    runSync { c.cancel("pi_1") }
    val lost = runSync { c.fetch("pi_1", 0) }
    assertEquals(listOf("GET /shop/payments/intents/pi_1?wait=20", "POST /shop/payments/intents/pi_1/cancel", "GET /shop/payments/intents/pi_1"), t.calls)
    assertEquals("payment_not_found", (lost as GatewayCall.Refused).code)
    //  شناسهٔ ناامن به نشانی نمی‌رود
    assertIs<GatewayCall.Refused>(runSync { c.fetch("../x", 5) })
    assertEquals(3, t.calls.size)
  }

  @Test
  fun waitIsCappedAtServerMaximum() {
    val t = FakeTransport(ok(intentJson()))
    runSync { HesabPayClient(t).fetch("pi_1", 300) }
    assertEquals("GET /shop/payments/intents/pi_1?wait=25", t.calls.single())
  }

  @Test
  fun intentParsingIgnoresUnknownFieldsAndRefusesBrokenOnes() {
    val i = PaymentIntent.fromMap(intentJson(status = "weird"))!!
    assertEquals(IntentStatus.UNKNOWN, i.status)
    assertFalse(i.status.terminal)
    assertFalse(i.status.live)
    assertEquals(1_000_000L, i.expiresAt)
    assertNull(PaymentIntent.fromMap(intentJson() + ("amount" to 1.5) + ("amountMinor" to 1.5)))
    assertNull(PaymentIntent.fromMap(intentJson() + ("amount" to "145000") + ("amountMinor" to null)))
    assertNull(PaymentIntent.fromMap(intentJson() + ("id" to "")))
    assertNull(PaymentIntent.fromMap(intentJson() + ("currency" to null)))
    //  qrPayload ِ خالی ⇐ همان checkoutUrl
    assertEquals("https://pay.example/c/pi_1", PaymentIntent.fromMap(intentJson() + ("qrPayload" to ""))!!.qrPayload)
  }

  @Test
  fun paidIntentCarriesServerPaymentRow() {
    val row = mapOf(
      "method" to "hesabpay", "kind" to "wallet", "amount" to 145000.0, "ref" to "TX-9",
      "status" to "confirmed", "verification" to "gateway", "intentId" to "pi_1",
    )
    val i = PaymentIntent.fromMap(intentJson(status = "paid", payment = row))!!
    assertEquals(IntentStatus.PAID, i.status)
    assertEquals(GatewayPaymentRow("hesabpay", "wallet", 145000, "TX-9", "confirmed", "gateway", "pi_1"), i.payment)
    //  ردیفِ بی مبلغ پذیرفته نیست
    assertNull(PaymentIntent.fromMap(intentJson(status = "paid", payment = row - "amount"))!!.payment)
  }
}
