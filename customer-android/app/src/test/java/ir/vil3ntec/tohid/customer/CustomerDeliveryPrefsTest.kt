package ir.vil3ntec.tohid.customer

import ir.vil3ntec.tohid.core.shopper.PrefsForm
import ir.vil3ntec.tohid.core.shopper.ShopperApi
import ir.vil3ntec.tohid.core.shopper.ShopperError
import ir.vil3ntec.tohid.core.shopper.ShopperHttp
import ir.vil3ntec.tohid.core.shopper.ShopperKeyValue
import ir.vil3ntec.tohid.core.shopper.ShopperLang
import ir.vil3ntec.tohid.core.shopper.ShopperRequest
import ir.vil3ntec.tohid.core.shopper.ShopperResponse
import ir.vil3ntec.tohid.core.shopper.ShopperText
import ir.vil3ntec.tohid.customer.ui.CustomerModel
import ir.vil3ntec.tohid.customer.ui.Route
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 *  CHK-24 در برنامهٔ خرید روی فرستندهٔ جعلی: کارتِ «دستورِ تحویل» ِ پیگیری با «با کدام اسکناس…» — فقط با کلیدِ
 *  `deliveryPrefs` ِ سرور؛ نسخهٔ دیده‌شده در هر بدنه؛ دو بار زدن ⇒ یک درخواست؛ پاسخِ گم‌شده و زدنِ دوباره ⇒ همان
 *  ویرایش (۴۰۹ ِ نسخه + نمای تازهٔ همان ⇒ «ثبت شد»)، هرگز دو ویرایشِ گوناگون؛ پس از حرکت بسته.
 */
class CustomerDeliveryPrefsTest {

  @After fun reset() {
    ShopperText.lang = ShopperLang.FA
  }

  private class MapKv : ShopperKeyValue {
    val map = HashMap<String, String>()
    override fun get(key: String): String? = map[key]
    override fun put(key: String, value: String?) {
      if (value == null) map.remove(key) else map[key] = value
    }
  }

  /** نمای سرور (`customerPrefs`)؛ null ⇒ پرچمِ خاموش ⇒ کلید نیست */
  private var prefs: String? = prefsJson(3, call = false, leave = "", tender = 50_000)

  private fun prefsJson(v: Long, call: Boolean, leave: String, tender: Long?, editable: Boolean = true) =
    """{"editable":$editable,"version":$v,"callBeforeArrival":$call,"leaveWith":"$leave","cashTenderMinor":${tender ?: "null"}}"""

  /** ۱۲۱ + ۵۰ = ۱۷۱ افغانی، ارسال و نقد ⇒ گزینه‌ها ۲۰۰، ۵۰۰، ۱۰۰۰ (بردارِ cash-tender-v1) */
  private fun order(p: String?) = """{"token":"v2.tok","orderNo":5,"status":"accepted","fulfillment":"delivery","paymentMethod":"cod",
    "totalMinor":17100,"createdAt":10,"updatedAt":20,"canCancel":false,"timeline":[{"status":"new","at":10}]${if (p == null) "" else ",\"deliveryPrefs\":$p"}}"""

  private val seen = mutableListOf<ShopperRequest>()
  /** پاسخِ `POST …/delivery` (پیش‌فرض: پذیرفته، نسخهٔ بعد، همان فرستاده) */
  private var edit: (ShopperRequest) -> ShopperResponse = { r -> accepted(r) }

  private fun accepted(r: ShopperRequest): ShopperResponse {
    val d = Json.parseToJsonElement(r.body!!).jsonObject.getValue("delivery").jsonObject
    val v = Json.parseToJsonElement(r.body!!).jsonObject.getValue("expectedVersion").jsonPrimitive.long + 1
    val t = d["cashTenderMinor"]?.takeIf { it != JsonNull }?.jsonPrimitive?.long
    prefs = prefsJson(v, d.getValue("callBeforeArrival").jsonPrimitive.boolean, d.getValue("leaveWith").jsonPrimitive.content, t)
    return ShopperResponse(200, """{"order":${order(prefs)},"unchanged":false}""")
  }

  private val http = ShopperHttp { r ->
    seen += r
    when (r.path) {
      "/api/store/golestan/orders/v2.tok/delivery" -> edit(r)
      "/api/store/golestan/orders/v2.tok" -> ShopperResponse(200, """{"order":${order(prefs)}}""")
      else -> ShopperResponse(404, """{"error":{"code":"not_found"}}""")
    }
  }

  private fun model() = CustomerModel(
    ShopperApi(http) { ShopperText.lang.code }, MapKv(), CoroutineScope(Dispatchers.Unconfined), Dispatchers.Unconfined, { 1_000L }, debounceMs = 0,
  )

  private fun posts() = seen.filter { it.method == "POST" && it.path.endsWith("/delivery") }
  private fun gets() = seen.count { it.method == "GET" && it.path == "/api/store/golestan/orders/v2.tok" }
  private fun body(r: ShopperRequest) = Json.parseToJsonElement(r.body!!).jsonObject

  private fun opened(): CustomerModel {
    val m = model()
    m.go(Route.Track("golestan", "v2.tok"))
    return m
  }

  @Test fun flagOffShowsNoCardAndSendsNothing() {
    prefs = null
    val m = opened()
    val o = m.order.valueOrNull()!!
    assertNull(m.deliveryPrefs.prefs)
    assertFalse(m.deliveryPrefs.shown(o))
    m.deliveryPrefs.save("golestan")
    assertTrue(posts().isEmpty())
  }

  @Test fun editSendsTheSeenVersionAndTheBanknoteAndRebindsFromTheServer() {
    val m = opened()
    val o = m.order.valueOrNull()!!
    val p = m.deliveryPrefs
    assertTrue(p.shown(o))
    assertEquals(PrefsForm(false, "", "", 50_000), p.form)
    assertEquals(listOf(20_000L, 50_000L, 100_000L), p.tenders(o))

    p.form = p.form.copy(callBeforeArrival = true, leaveWith = "  دربان ", cashTenderMinor = 20_000)
    p.save("golestan")
    val b = body(posts().single())
    val d = b.getValue("delivery").jsonObject
    assertEquals(3L, b.getValue("expectedVersion").jsonPrimitive.long)
    assertEquals(true, d.getValue("callBeforeArrival").jsonPrimitive.boolean)
    assertEquals("دربان", d.getValue("leaveWith").jsonPrimitive.content)
    assertEquals(20_000L, d.getValue("cashTenderMinor").jsonPrimitive.long)
    assertFalse("نشانهٔ خالی نمی‌رود", "address" in b)
    assertEquals(4L, p.prefs!!.version)
    assertEquals(PrefsForm(true, "دربان", "", 20_000), p.form)
    assertEquals(ShopperText.prefsSaved, p.note)
    assertTrue(p.ok)

    //  «پول خرد لازم نیست» ⇒ null (برداشته) و نشانهٔ تازه؛ نسخهٔ تازه
    p.form = p.form.copy(cashTenderMinor = null, landmark = "پشتِ مسجد")
    p.save("golestan")
    val b2 = body(posts().last())
    assertEquals(4L, b2.getValue("expectedVersion").jsonPrimitive.long)
    assertEquals(JsonNull, b2.getValue("delivery").jsonObject["cashTenderMinor"])
    assertEquals("پشتِ مسجد", b2.getValue("address").jsonObject.getValue("landmark").jsonPrimitive.content)
    assertEquals("نشانه پس از ثبت خالی می‌شود (مثلِ وب)", "", p.form.landmark)
  }

  @Test fun doubleTapSendsOneEdit() {
    val m = opened()
    val p = m.deliveryPrefs
    edit = { r -> p.save("golestan"); p.save("golestan"); accepted(r) }   //  زدن‌های دوم و سوم وسطِ همان درخواست
    p.form = p.form.copy(leaveWith = "دربان")
    p.save("golestan")
    assertEquals(1, posts().size)
    assertEquals(ShopperText.prefsSaved, p.note)
  }

  @Test fun lostResponseThenRetryIsTheSameEditAndReadsAsSaved() {
    val m = opened()
    val p = m.deliveryPrefs
    //  سرور ثبت کرد، پاسخ در راه گم شد
    edit = { r -> accepted(r); throw RuntimeException("connection reset") }
    p.form = p.form.copy(callBeforeArrival = true, cashTenderMinor = 100_000)
    p.save("golestan")
    assertEquals(ShopperText.error(ShopperError(0, ShopperError.NETWORK)), p.note)
    assertFalse(p.ok)
    assertEquals("کادرها همان می‌مانند", PrefsForm(true, "", "", 100_000), p.form)
    assertEquals("بی تکرارِ خودکار", 1, posts().size)
    assertEquals("شبکه ⇒ بی پیگیریِ بی‌جا (فقط بارِ نخست)", 1, gets())

    //  زدنِ دوباره: همان بدنه با همان نسخه ⇒ ۴۰۹ ⇒ پیگیریِ تازه همان فرستاده است ⇒ «ثبت شد»
    edit = { ShopperResponse(409, """{"error":{"code":"version_conflict","message":"…","version":4}}""") }
    p.save("golestan")
    assertEquals(2, posts().size)
    val (a, b) = posts()
    assertEquals("همان ویرایش، نه ویرایشِ دوم", a.body, b.body)
    assertEquals(3L, body(b).getValue("expectedVersion").jsonPrimitive.long)
    assertEquals(2, gets())
    assertEquals(4L, p.prefs!!.version)
    assertEquals(ShopperText.prefsSaved, p.note)
    assertTrue(p.ok)
  }

  @Test fun conflictWithAnotherDeviceRefillsTheFormAndSaysSo() {
    val m = opened()
    val p = m.deliveryPrefs
    prefs = prefsJson(4, call = false, leave = "همسایه", tender = 50_000)   //  دستگاهِ دیگر
    edit = { ShopperResponse(409, """{"error":{"code":"version_conflict","message":"…","version":4}}""") }
    p.form = p.form.copy(leaveWith = "دربان")
    p.save("golestan")
    assertEquals(1, posts().size)
    assertEquals(ShopperText.error(ShopperError(409, "version_conflict")), p.note)
    assertFalse(p.ok)
    assertEquals("کادرها همان نمای تازهٔ سرور (هرگز رونویسیِ بی‌صدا)", PrefsForm(false, "همسایه", "", 50_000), p.form)
    //  دورِ بعد با نسخهٔ تازه
    edit = { r -> accepted(r) }
    p.save("golestan")
    assertEquals(4L, body(posts().last()).getValue("expectedVersion").jsonPrimitive.long)
  }

  @Test fun afterLeavingTheShopTheCardIsLockedAndNothingIsSent() {
    val m = opened()
    val p = m.deliveryPrefs
    //  وسطِ ویرایش بسته از دکان بیرون رفت ⇒ ۴۰۹ ِ قفل ⇒ پیگیری ⇒ کارتِ بسته
    prefs = prefsJson(4, call = false, leave = "", tender = 50_000, editable = false)
    edit = { ShopperResponse(409, """{"error":{"code":"delivery_locked","message":"…","status":"out_for_delivery"}}""") }
    p.form = p.form.copy(leaveWith = "دربان")
    p.save("golestan")
    assertEquals(1, posts().size)
    assertFalse(p.prefs!!.editable)
    assertEquals(ShopperText.prefsLocked, p.note)
    assertTrue(p.shown(m.order.valueOrNull()!!))
    p.save("golestan")
    assertEquals("بسته ⇒ هیچ درخواستی", 1, posts().size)
  }
}
