package ir.vil3ntec.tohid.core.shopper

import ir.vil3ntec.tohid.core.market.PublicOrder
import ir.vil3ntec.tohid.core.shopper.ShopperSamples.FakeHttp
import ir.vil3ntec.tohid.core.shopper.ShopperSamples.ORDER_JSON
import ir.vil3ntec.tohid.core.shopper.ShopperSamples.fail
import ir.vil3ntec.tohid.core.shopper.ShopperSamples.ok
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** CHK-24 در برنامهٔ خرید: کارتِ «دستورِ تحویل» ِ پیگیری و «با کدام اسکناس…» پس از ثبت ([PrefsRules]، [ShopperApi.editDelivery]). */
class ShopperPrefsTest {

  @AfterTest fun reset() {
    ShopperText.lang = ShopperLang.FA
  }

  /** ۱۲۱ + ۵۰ = ۱۷۱ افغانی ⇒ گزینه‌های بردار: ۲۰۰، ۵۰۰، ۱۰۰۰ */
  private fun order(total: Long = 17_100, status: String = "accepted", fulfillment: String = "delivery", pay: String = "cod") = PublicOrder(
    token = "v1.abc", orderNo = 12, status = status, fulfillment = fulfillment, paymentMethod = pay, totalMinor = total, createdAt = 1, updatedAt = 2,
  )

  private fun prefs(tender: Long? = null, editable: Boolean = true, version: Long = 3, call: Boolean = false, leave: String = "") =
    CustomerDeliveryPrefs(editable = editable, version = version, callBeforeArrival = call, leaveWith = leave, cashTenderMinor = tender)

  @Test fun cardOnlyWithTheServerKeyForALiveDeliveryAndBanknoteOnlyForCashOnDelivery() {
    assertFalse(PrefsRules.shown(order(), null), "پرچمِ خاموش ⇒ کلید نیست ⇒ کارت نیست")
    assertTrue(PrefsRules.shown(order(), prefs()))
    assertTrue(PrefsRules.shown(order(status = "out_for_delivery"), prefs(editable = false)), "پس از حرکت دیده ولی بسته")
    assertFalse(PrefsRules.shown(order(fulfillment = "pickup"), prefs()), "دریافت از دکان دستورِ تحویل ندارد")
    for (s in listOf("rejected", "cancelled_by_customer", "cancelled")) assertFalse(PrefsRules.shown(order(status = s), prefs()), s)
    assertTrue(PrefsRules.tenderShown(order(), prefs()))
    assertFalse(PrefsRules.tenderShown(order(pay = "hesabpay"), prefs()), "همان شرطِ `checkTender` ِ سرور: فقط نقد هنگامِ تحویل")
    assertTrue(PrefsRules.tenders(order(pay = "hesabpay"), prefs(50_000)).isEmpty())
    assertTrue(PrefsRules.tenders(order(fulfillment = "pickup"), prefs()).isEmpty())
  }

  @Test fun banknoteOptionsAreTheCheckoutOnesAndKeepAStillValidCurrentAmount() {
    val o = order()
    assertEquals(listOf(20_000L, 50_000L, 100_000L), PrefsRules.tenders(o, prefs()))
    assertEquals(CheckoutRules.tenderOptions(17_100), PrefsRules.tenders(o, prefs()))
    assertEquals(50_000L, PrefsRules.formOf(o, prefs(50_000)).cashTenderMinor)
    //  مبلغی که دکان نوشته (برابرِ جمع) هنوز درست است ⇒ گزینه می‌ماند و ذخیرهٔ کادرِ دیگر پاکش نمی‌کند
    assertEquals(listOf(17_100L, 20_000L, 50_000L, 100_000L), PrefsRules.tenders(o, prefs(17_100)))
    assertEquals(17_100L, PrefsRules.formOf(o, prefs(17_100)).cashTenderMinor)
    //  جمع عوض شد و اسکناس دیگر درست نیست (کم، پول خردِ ۱۰۰۰، نیمه‌افغانی) ⇒ «پول خرد لازم نیست»، نه گزینه
    for (bad in listOf(10_000L, 17_100L + 100_000L, 17_150L)) {
      assertEquals(listOf(20_000L, 50_000L, 100_000L), PrefsRules.tenders(o, prefs(bad)), "$bad")
      assertNull(PrefsRules.formOf(o, prefs(bad)).cashTenderMinor, "$bad")
    }
    val f = PrefsRules.formOf(o, prefs(50_000, call = true, leave = "دربان"))
    assertEquals(PrefsForm(true, "دربان", "", 50_000), f, "نشانه همیشه خالی آغاز می‌شود (نمای مشتری نشانی ندارد)")
  }

  @Test fun bodyCarriesTheSeenVersionAndOnlyWhatTheServerReads() {
    val o = order()
    val p = prefs(50_000, version = 7)
    val sent = PrefsRules.sent(o, p, PrefsForm(true, "  دربان  ", "   ", 20_000))
    assertEquals(PrefsForm(true, "دربان", "", 20_000), sent)
    val b = PrefsRules.body(o, p, sent)
    assertEquals(setOf("delivery", "expectedVersion"), b.keys, "نشانهٔ خالی نمی‌رود")
    val d = b.getValue("delivery").jsonObject
    assertEquals(true, d.getValue("callBeforeArrival").jsonPrimitive.boolean)
    assertEquals("دربان", d.getValue("leaveWith").jsonPrimitive.content)
    assertEquals(20_000L, d.getValue("cashTenderMinor").jsonPrimitive.long)
    assertEquals(7L, b.getValue("expectedVersion").jsonPrimitive.long)

    //  «پول خرد لازم نیست» ⇒ `null` (برداشته)، و همان در متنِ بدنه
    val none = PrefsRules.body(o, p, PrefsRules.sent(o, p, PrefsForm(cashTenderMinor = null, landmark = " پشتِ مسجد ")))
    assertEquals(JsonNull, none.getValue("delivery").jsonObject["cashTenderMinor"])
    assertTrue(none.toString().contains("\"cashTenderMinor\":null"), none.toString())
    assertEquals("پشتِ مسجد", none.getValue("address").jsonObject.getValue("landmark").jsonPrimitive.content)
    //  ⛔ هرگز مبلغی که سرور رد می‌کند: بیرون از گزینه‌ها ⇒ null
    assertNull(PrefsRules.sent(o, p, PrefsForm(cashTenderMinor = 200_000)).cashTenderMinor)
    //  بی «نقد هنگامِ تحویل» کلیدِ اسکناس اصلاً نمی‌رود (سرور `bad_cash_tender` می‌داد)
    val online = order(pay = "hesabpay")
    assertFalse("cashTenderMinor" in PrefsRules.body(online, p, PrefsRules.sent(online, p, PrefsForm(cashTenderMinor = 20_000))).getValue("delivery").jsonObject)
    //  همان برش‌های سرور (`text(v, 80)`/`text(v, 200)`)
    val long = PrefsRules.sent(o, p, PrefsForm(leaveWith = "ا".repeat(120), landmark = "ب".repeat(300)))
    assertEquals(PrefsRules.LEAVE_MAX, long.leaveWith.length)
    assertEquals(PrefsRules.LANDMARK_MAX, long.landmark.length)
  }

  @Test fun landedOnlyWhenTheFreshViewIsWhatWasSentAndNoLandmark() {
    val o = order()
    val sent = PrefsForm(true, "دربان", "", 20_000)
    assertTrue(PrefsRules.landed(sent, o, prefs(20_000, version = 4, call = true, leave = "دربان")))
    assertFalse(PrefsRules.landed(sent, o, prefs(50_000, version = 4, call = true, leave = "دربان")), "دستگاهِ دیگر اسکناسِ دیگری گذاشت")
    assertFalse(PrefsRules.landed(sent, o, prefs(20_000, version = 4, call = true, leave = "همسایه")))
    assertFalse(PrefsRules.landed(sent, o, prefs(20_000, version = 4, call = false, leave = "دربان")))
    assertFalse(PrefsRules.landed(sent.copy(landmark = "پشتِ مسجد"), o, prefs(20_000, version = 4, call = true, leave = "دربان")),
      "نشانه در نمای مشتری نیست ⇒ هرگز «ثبت شد» ِ حدسی")
    val online = order(pay = "hesabpay")
    assertTrue(PrefsRules.landed(PrefsForm(true, "دربان"), online, prefs(null, call = true, leave = "دربان")))
  }

  @Test fun apiPostsTheBodyAndReadsTheFreshPrefs() {
    val fresh = """{"editable":true,"version":4,"callBeforeArrival":true,"leaveWith":"دربان","cashTenderMinor":null}"""
    val http = FakeHttp { r ->
      when {
        r.method == "GET" -> ok("""{"order":${ORDER_JSON.dropLast(1)},"deliveryPrefs":$fresh}}""")
        else -> ok("""{"order":${ORDER_JSON.dropLast(1)},"deliveryPrefs":$fresh},"unchanged":false}""")
      }
    }
    val api = ShopperApi(http) { "fa" }
    val t = assertIs<ShopperResult.Ok<TrackPage>>(api.trackPage("golestan", "v1.abc")).value
    assertEquals(4L, t.extras.deliveryPrefs?.version)
    val o = t.order
    val p = assertNotNull(t.extras.deliveryPrefs)
    val body = PrefsRules.body(o, p, PrefsRules.sent(o, p, PrefsForm(true, "دربان", "", null)))
    val r = assertIs<ShopperResult.Ok<PrefsSaved>>(api.editDelivery("golestan", "v1.abc", body)).value
    assertEquals(p, r.prefs)
    assertFalse(r.unchanged)
    val sent = http.seen.last()
    assertEquals("POST", sent.method)
    assertEquals("/api/store/golestan/orders/v1.abc/delivery", sent.path)
    assertEquals("application/json", sent.headers["Content-Type"])
    val json = Json.parseToJsonElement(sent.body!!).jsonObject
    assertEquals(JsonNull, json.getValue("delivery").jsonObject["cashTenderMinor"], "null در متنِ بدنه می‌ماند")
    assertEquals(4L, json.getValue("expectedVersion").jsonPrimitive.long)

    //  پرچمِ خاموش ⇒ کلید نیست
    http.reply = { ok("""{"order":$ORDER_JSON}""") }
    assertNull(assertIs<ShopperResult.Ok<TrackPage>>(api.trackPage("golestan", "v1.abc")).value.extras.deliveryPrefs)
    //  نسخهٔ کهنه ⇒ همان کدِ سرور
    http.reply = { fail(409, "version_conflict", ",\"version\":5") }
    val e = assertIs<ShopperResult.Fail>(api.editDelivery("golestan", "v1.abc", body)).error
    assertEquals(409 to "version_conflict", e.status to e.code)
  }

  @Test fun editErrorsHaveTheirOwnWords() {
    val generic = ShopperText.error(ShopperError(400, "something_else"))
    for (code in listOf("version_conflict", "delivery_locked", "delivery_prefs_disabled", "cash_tender_short", "cash_tender_excess", "bad_cash_tender")) {
      assertNotEquals(generic, ShopperText.error(ShopperError(409, code)), code)
    }
    assertEquals(ShopperText.prefsLocked, ShopperText.error(ShopperError(409, "delivery_locked")))
    ShopperText.lang = ShopperLang.EN
    assertEquals("This order was just changed elsewhere — please look again.", ShopperText.error(ShopperError(409, "version_conflict")))
  }
}
