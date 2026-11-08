package ir.vil3ntec.tohid.core.shopper

import ir.vil3ntec.tohid.core.shopper.ShopperSamples.ZONE_A
import ir.vil3ntec.tohid.core.shopper.ShopperSamples.line
import ir.vil3ntec.tohid.core.shopper.ShopperSamples.page
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** نشانی‌های ذخیره‌شده با پین (CHK-08/09/10) — همان قاعده‌های `parseAddress` ِ `lib/store-customers.js`. */
class ShopperAddressTest {

  private val pin = GeoPin(34.528123456, 69.172345678)

  private fun cart(vararg l: CartLine) = Cart("golestan", l.toList())

  @Test fun rulesMatchTheServerParseAddress() {
    val ok = AddressRules.clean("  خانه ", " کارتهٔ سه ", "", pin)
    assertEquals(AddressInput("خانه", "کارتهٔ سه", "", 34.528123, 69.172346), ok, "گردِ شش رقمیِ `coord` و بی فاصلهٔ دو سر")
    assertNull(AddressRules.problem(ok))
    assertEquals(AddressProblem.LABEL, AddressRules.problem(AddressRules.clean(" ", "x", "", null)))
    assertEquals(AddressProblem.EMPTY, AddressRules.problem(AddressRules.clean("خانه", "", " ", null)), "بی پین، نشانی یا نشانه لازم است")
    assertNull(AddressRules.problem(AddressRules.clean("خانه", "", "", pin)), "پین تنها بس است")
    assertEquals(AddressProblem.PIN, AddressRules.problem(AddressInput("خانه", "x", lat = 34.5)), "پینِ ناقص")
    assertEquals(AddressProblem.PIN, AddressRules.problem(AddressInput("خانه", "x", lat = 95.0, lng = 69.0)), "بیرونِ بازه")
    assertEquals(40, AddressRules.clean("ن".repeat(60), "x", "", null).label.length, "سقفِ نام")
    assertEquals("ab", AddressRules.text("a\u0000b\u007F", 10), "نویسهٔ کنترلی می‌رود")
  }

  @Test fun bookKeepsTenAddressesAndEditsInPlace() {
    var n = 0
    val next = { "a${++n}" }
    var list = emptyList<SavedAddress>()
    repeat(AddressRules.MAX) { i ->
      val r = AddressBook.save(list, null, AddressRules.clean("نشانی $i", "متن", "", null), 100L + i, next)
      list = assertIs<AddressBook.Saved.Ok>(r).list
    }
    assertEquals(10, list.size)
    assertEquals(AddressBook.Saved.Rejected(AddressProblem.FULL), AddressBook.save(list, null, AddressRules.clean("یازدهم", "x", "", null), 200, next))
    val edited = assertIs<AddressBook.Saved.Ok>(AddressBook.save(list, "a3", AddressRules.clean("دفتر", "", "", pin), 300, next))
    assertEquals(10, edited.list.size, "ویرایش سقف را نمی‌شکند")
    assertEquals("a3", edited.list[2].id, "جایش می‌ماند")
    assertEquals(GeoPin(34.528123, 69.172346), edited.list[2].pin)
    assertEquals(9, AddressBook.remove(edited.list, "a3").size)
    assertEquals(AddressBook.Saved.Rejected(AddressProblem.EMPTY), AddressBook.save(list, "a1", AddressRules.clean("x", "", "", null), 1, next))
  }

  @Test fun savedAddressFillsCheckoutAndPinReplacesZoneChoice() {
    val a = SavedAddress("a1", "خانه", "کوچهٔ دوم", "پشتِ مسجد", 34.53, 69.17)
    val f = AddressBook.fill(CheckoutForm(name = "احمد", phone = "0700000002", zoneId = "zone_a"), a)
    assertEquals(GeoPin(34.53, 69.17), f.pin)
    assertNull(f.zoneId, "ناحیه از نو، از پین")
    assertEquals("a1", f.addressId)
    val p = page(zones = listOf(ZONE_A))
    val c = cart(line("rice", 25000, 4000))
    //  پینِ تنها نشانی بس است (همان سرور)؛ ناحیهٔ گفتهٔ سرور فقط برای برآوردِ هزینه
    val pinOnly = CheckoutForm(name = "احمد", phone = "0700000002", fulfillment = "delivery", pinLat = 34.53, pinLng = 69.17, zoneId = "zone_a")
    val problems = CheckoutRules.problems(p, c, pinOnly, null, null, 1)
    assertTrue(CheckoutProblem.ADDRESS !in problems, problems.toString())
    val r = CheckoutRules.request(p, c, pinOnly, "+93700000002", "fa", null)
    assertEquals(34.53, r.address?.lat)
    assertEquals(69.17, r.address?.lng)
    assertNull(r.address?.zoneId, "با پین `zoneId` نمی‌رود (ناحیه را سرور از پین می‌گوید)")
    assertEquals(100000 + 0, r.expectedTotalMinor, "رایگان از ۱۰۰٬۰۰۰ ِ همان ناحیه")
    val json = CheckoutKeys.json(r)
    assertTrue("\"lat\":34.53" in json && "\"lng\":69.17" in json, json)
    val noPin = CheckoutRules.request(p, c, pinOnly.copy(pinLat = null, pinLng = null, addressText = "کوچه"), "+93700000002", "fa", null)
    assertEquals("zone_a", noPin.address?.zoneId)
    assertTrue("lat" !in CheckoutKeys.json(noPin), "بی پین، کلیدِ پین در بدنه نیست (همان بدنهٔ مرحلهٔ ۱)")
  }

  @Test fun pinFromPastedMapLinksAndCoordinates() {
    val want = GeoPin(34.5281, 69.1723)
    val cases = listOf(
      "34.5281, 69.1723",
      "۳۴٫۵۲۸۱، ۶۹٫۱۷۲۳",
      "geo:34.5281,69.1723?q=34.5281,69.1723(خانه)",
      "https://www.google.com/maps/place/X/@34.50,69.10,17z/data=!3m1!4b1!4m5!3m4!1s0x0:0x0!8m2!3d34.5281!4d69.1723",
      "https://maps.google.com/?q=34.5281,69.1723",
      "https://www.google.com/maps/search/?api=1&query=34.5281%2C69.1723",
      "https://www.google.com/maps/@34.5281,69.1723,15z",
      "https://www.openstreetmap.org/?mlat=34.5281&mlon=69.1723#map=17/34.5/69.1",
      "https://www.openstreetmap.org/#map=17/34.5281/69.1723",
      "https://maps.apple.com/?ll=34.5281,69.1723&q=Pin",
    )
    for (c in cases) assertEquals(want, PinText.parse(c), c)
    for (bad in listOf("", "خانه", "12 5", "https://maps.app.goo.gl/AbCdEf", "0.0, 0.0", "95.1, 69.2", "geo:34.5")) {
      assertNull(PinText.parse(bad), bad)
    }
    assertEquals("geo:34.5281,69.1723?q=34.5281,69.1723", PinText.geoUri(want), "فقط مختصات؛ نه نام و نه نشانه")
    assertEquals("34.52810, 69.17230", PinText.short(want))
    assertEquals("-1.50000, -0.25000", PinText.short(GeoPin(-1.5, -0.25)))
  }
}
