package ir.vil3ntec.tohid.core.gateway

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** «حساب‌پیِ دکان» — شماره، QR، کدِ فاکتور، کارگزارِ درها و پیشنهادِ اعلان (هرگز تأییدِ خودکار) */
class HesabPayShopTest {

  private fun ok(body: Map<String, Any?>) = GatewayReply(200, body)

  @Test
  fun accountNumberIsNormalizedLikeTheServer() {
    for ((given, want) in listOf(
      "0799123456" to "0799123456", "+93 799 123 456" to "0799123456", "۰۷۹۹۱۲۳۴۵۶" to "0799123456",
      "0093799123456" to "0799123456", "799-123-456" to "0799123456", "٠٧٩٩١٢٣٤٥٦" to "0799123456",
    )) assertEquals(want, HesabPayShop.normalizeAccount(given), given)
    for (bad in listOf("0699123456", "07991234567", "abc", "", "+98 912 123 4567")) assertNull(HesabPayShop.normalizeAccount(bad), bad)
  }

  @Test
  fun scannedQrIsKeptAsIsAndItsNumberFound() {
    val q = HesabPayShop.parseQr("  hesabpay://pay?to=+93799123456&n=shop  ")!!
    assertEquals("hesabpay://pay?to=+93799123456&n=shop", q.payload)
    assertEquals("0799123456", q.account)
    assertEquals("", HesabPayShop.parseQr("OPAQUE-QR-123")!!.account)
    assertNull(HesabPayShop.parseQr("x".repeat(513)))
    assertNull(HesabPayShop.parseQr("a\u0001b"))
    assertNull(HesabPayShop.parseQr("   "))
  }

  @Test
  fun invoiceRefIsStableShortAndDiffersPerSale() {
    val a = HesabPayShop.invoiceRef("01JABCDEF0000000000000001")
    assertEquals(a, HesabPayShop.invoiceRef("01JABCDEF0000000000000001"))
    assertTrue(Regex("^HP-[0-9A-HJKMNP-TV-Z]{4}-[0-9A-HJKMNP-TV-Z]{4}$").matches(a), a)
    val seen = (1..2000).map { HesabPayShop.invoiceRef("sale-$it") }.toSet()
    assertEquals(2000, seen.size)
    assertNotEquals(a, HesabPayShop.invoiceRef("01JABCDEF0000000000000002"))
    assertEquals("HPAB12CD34", HesabPayShop.refKey("hp-ab12 cd34"))
  }

  @Test
  fun newPinMustBeSixDigitsAndNotSimple() {
    assertNull(HesabPayShop.pinProblem("258014"))
    assertNull(HesabPayShop.pinProblem("۲۵۸۰۱۴"))
    assertEquals("length", HesabPayShop.pinProblem("2580"))
    assertEquals("weak", HesabPayShop.pinProblem("111111"))
    assertEquals("weak", HesabPayShop.pinProblem("123456"))
  }

  @Test
  fun tillConfigReadsSimpleAccountOnlyInSimpleMode() {
    val simple = TillHesab.fromMap(mapOf("hesabpay" to mapOf("enabled" to false, "mode" to "simple",
      "simple" to mapOf("account" to "0799123456", "qrPayload" to ""))))
    assertEquals(SimpleAccount("0799123456", ""), simple.simple)
    assertEquals("0799123456", simple.simple!!.qrText)
    assertFalse(simple.gateway)
    assertTrue(simple.any)
    val auto = TillHesab.fromMap(mapOf("hesabpay" to mapOf("enabled" to true, "mode" to "auto",
      "simple" to mapOf("account" to "0799123456"))))
    assertNull(auto.simple)
    assertTrue(auto.gateway)
    assertEquals(TillHesab.OFF, TillHesab.fromMap(null))
    assertEquals(TillHesab.OFF, TillHesab.fromMap(mapOf("hesabpay" to "x")))
    //  سرورِ کهنه بی mode: فقط درگاه
    assertEquals(TillHesab(true, "off", null), TillHesab.fromMap(mapOf("hesabpay" to mapOf("enabled" to true))))
    val t = FakeTransport(GatewayReply(500))
    assertEquals(TillHesab.OFF, runSync { HesabPayClient(t).till() })
  }

  private val status = mapOf<String, Any?>(
    "pinSet" to true, "locked" to false, "lockedUntil" to null, "mode" to "auto", "connected" to true,
    "holdUntil" to null, "autoAvailable" to true, "autoReason" to "",
    "account" to "", "qrPayload" to "", "auto" to mapOf("keyLast4" to "1111", "secretSet" to true, "merchantSet" to false, "webhookUrl" to "https://x/api/v1/payments/webhook/hesabpay/t"),
  )

  @Test
  fun accountClientSendsPinToServerAndNeverKeepsSecrets() {
    val t = FakeTransport(ok(status), ok(status), GatewayReply(403, code = "hesabpay_pin_wrong", message = "رمز درست نیست"),
      GatewayReply(423, code = "hesabpay_pin_locked", message = "بسته است"), GatewayReply(0), GatewayReply(503, code = "hesabpay_cred_key_missing", message = "کلید"))
    val c = HesabPayAccountClient(t)
    val saved = runSync { c.saveAuto("258014", " دکانِ احمد ", " KEY-1111 ", "whsec-abc ", " M-1 ") }
    assertIs<AccountCall.Ok>(saved)
    assertEquals("1111", saved.view.keyLast4)
    assertTrue(saved.view.opened)
    assertEquals("PUT /shop/hesabpay/account", t.calls[0])
    assertEquals(
      mapOf("pin" to "258014", "name" to "دکانِ احمد", "mode" to "auto", "apiKey" to "KEY-1111", "webhookSecret" to "whsec-abc", "merchantId" to "M-1"),
      t.bodies[0],
    )
    //  نما هیچ کلید یا رازی ندارد — فقط چهار نویسهٔ آخر
    assertFalse(saved.view.toString().contains("KEY-1111") || saved.view.toString().contains("whsec"))
    assertIs<AccountCall.Ok>(runSync { c.open("258014") })
    assertEquals(AccountCall.Refused(403, "hesabpay_pin_wrong", "رمز درست نیست"), runSync { c.remove("000001") })
    assertEquals(AccountCall.Refused(423, "hesabpay_pin_locked", "بسته است"), runSync { c.open("258014") })
    assertIs<AccountCall.Offline>(runSync { c.status() })
    //  کلیدِ رمزگذاریِ سرور نیست ⇒ پیامِ روشنِ سرور، نه «نرسید»
    assertEquals("hesabpay_cred_key_missing", (runSync { c.saveAuto("258014", "الف", "k".repeat(8), "s".repeat(8), "M") } as AccountCall.Refused).code)
  }

  @Test
  fun tillShowsActivePayeeBesideQrOnlyFromServer() {
    val till = TillHesab.fromMap(mapOf("hesabpay" to mapOf("enabled" to false, "mode" to "simple",
      "simple" to mapOf("account" to "0799123456", "qrPayload" to ""),
      "payee" to mapOf("name" to "دکانِ احمد", "last4" to "3456", "fp" to "hp1_abc"))))
    assertEquals(Payee("دکانِ احمد", "3456", "hp1_abc"), till.payee)
    //  سرورِ کهنه بی `payee`، یا حالتِ خاموش ⇐ هیچ (هرگز گیرنده‌ای از جای دیگر)
    assertNull(TillHesab.fromMap(mapOf("hesabpay" to mapOf("mode" to "simple", "simple" to mapOf("account" to "0799123456")))).payee)
    assertNull(TillHesab.fromMap(mapOf("hesabpay" to mapOf("mode" to "off", "payee" to mapOf("name" to "x", "last4" to "1234")))).payee)
    assertNull(Payee.fromMap(mapOf("name" to "x", "last4" to "")))
  }

  @Test
  fun pendingChangeAndNotMeCancelGoToServer() {
    val pending = status + mapOf(
      "payee" to mapOf("name" to "دکانِ احمد", "last4" to "1111"),
      "pending" to mapOf("name" to "حسابِ تازه", "last4" to "9888", "until" to 1_800_000_000_000L),
      "sectionLockedUntil" to null,
      "device" to mapOf("trusted" to false, "trustedAfter" to 1_700_000_000_000L),
      "trustDays" to 7,
    )
    val v = HesabAccountView.fromMap(pending, opened = false)!!
    assertEquals(PendingPayee("حسابِ تازه", "9888", 1_800_000_000_000L), v.pending)
    assertEquals("1111", v.payee!!.last4)
    assertEquals(false, v.deviceTrusted)
    assertEquals(1_700_000_000_000L, v.trustedAfter)
    //  سرورِ کهنه بی این فیلدها ⇐ بی کارت و بی هشدار
    val old = HesabAccountView.fromMap(status, opened = false)!!
    assertNull(old.pending)
    assertNull(old.deviceTrusted)
    val after = status + mapOf("pending" to null, "sectionLockedUntil" to 1_800_000_000_000L)
    val t = FakeTransport(ok(after), GatewayReply(403, code = "reauth_required", message = "رمز را دوباره بزنید"))
    val c = HesabPayAccountClient(t)
    val r = runSync { c.cancelPending() }
    assertIs<AccountCall.Ok>(r)
    assertNull(r.view.pending)
    assertEquals(1_800_000_000_000L, r.view.sectionLockedUntil)
    assertEquals("POST /shop/hesabpay/pending/cancel", t.calls[0])
    assertEquals(emptyMap<String, Any?>(), t.bodies[0])
    assertEquals(AccountCall.Refused(403, "reauth_required", "رمز را دوباره بزنید"), runSync { c.cancelPending() })
  }

  @Test
  fun simpleSaveSendsHolderName() {
    val t = FakeTransport(ok(status))
    runSync { HesabPayAccountClient(t).saveSimple("258014", " دکانِ احمد ", " 0799123456 ", "") }
    assertEquals(mapOf("pin" to "258014", "name" to "دکانِ احمد", "mode" to "simple", "account" to "0799123456"), t.bodies[0])
  }

  @Test
  fun weakNewPinIsRefusedBeforeSending() {
    val t = FakeTransport()
    val c = HesabPayAccountClient(t)
    assertEquals(AccountCall.Refused(400, "hesabpay_pin_weak", ""), runSync { c.setPin(null, "123456") })
    assertEquals(AccountCall.Refused(400, "hesabpay_pin_format", ""), runSync { c.setPin("258014", "12") })
    assertTrue(t.calls.isEmpty())
    val t2 = FakeTransport(ok(status))
    runSync { HesabPayAccountClient(t2).setPin(null, "۲۵۸۰۱۴") }
    assertEquals(mapOf("newPin" to "258014"), t2.bodies[0])
    assertEquals("PUT /shop/hesabpay/pin", t2.calls[0])
  }

  @Test
  fun notificationOnlySuggestsAndNeverOnAmountMismatchOrStale() {
    val text = "HesabPay: You have received 1,450.00 AFN from 0788000111. Note: HP-AB12-CD34"
    assertEquals(145000L, HesabPayHint.amountOf(text))
    assertEquals(50000L, HesabPayHint.amountOf("۵۰۰ افغانی به حساب‌تان واریز شد"))
    assertEquals(50000L, HesabPayHint.amountOf("دریافت: AFN 500"))
    //  پرداختِ خودِ دکان (بی «دریافت») یا بی ارز ⇐ هیچ
    assertNull(HesabPayHint.amountOf("You paid 500 AFN"))
    assertNull(HesabPayHint.amountOf("received 500"))
    val hint = HesabPayHint.hintOf(text, at = 1_000_000L)!!
    val s = HesabPayHint.suggest(hint, 145000L, "HP-AB12-CD34", openedAt = 990_000L, now = 1_010_000L)!!
    assertEquals(HesabPayHint.Suggestion(145000L, refSeen = true), s)
    //  همان مبلغ، فاکتورِ دیگر ⇒ پیشنهادِ ضعیف (کد نیامده) — باز هم فقط پیشنهاد
    assertEquals(false, HesabPayHint.suggest(hint, 145000L, "HP-ZZZZ-ZZZZ", 990_000L, 1_010_000L)!!.refSeen)
    assertNull(HesabPayHint.suggest(hint, 145001L, "HP-AB12-CD34", 990_000L, 1_010_000L), "مبلغِ دیگر")
    assertNull(HesabPayHint.suggest(hint, 145000L, "HP-AB12-CD34", 990_000L, 1_000_000L + HesabPayHint.FRESH_MS + 1), "کهنه")
    assertNull(HesabPayHint.suggest(hint, 145000L, "HP-AB12-CD34", 1_000_000L + HesabPayHint.BEFORE_OPEN_MS + 1, 1_100_000L), "پیش از برگه")
    assertNull(HesabPayHint.suggest(null, 145000L, "HP-AB12-CD34", 0L, 1L))
  }
}
