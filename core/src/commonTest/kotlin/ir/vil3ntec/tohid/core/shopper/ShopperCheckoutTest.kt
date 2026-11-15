package ir.vil3ntec.tohid.core.shopper

import ir.vil3ntec.tohid.core.market.StoreOrdering
import ir.vil3ntec.tohid.core.shopper.CheckoutProblem.ADDRESS
import ir.vil3ntec.tohid.core.shopper.CheckoutProblem.BELOW_MIN
import ir.vil3ntec.tohid.core.shopper.CheckoutProblem.CLOSED
import ir.vil3ntec.tohid.core.shopper.CheckoutProblem.EMPTY
import ir.vil3ntec.tohid.core.shopper.CheckoutProblem.NAME
import ir.vil3ntec.tohid.core.shopper.CheckoutProblem.EMAIL
import ir.vil3ntec.tohid.core.shopper.CheckoutProblem.PHONE
import ir.vil3ntec.tohid.core.shopper.CheckoutProblem.SLOT
import ir.vil3ntec.tohid.core.shopper.CheckoutProblem.UNAVAILABLE_LINES
import ir.vil3ntec.tohid.core.shopper.CheckoutProblem.VERIFY
import ir.vil3ntec.tohid.core.shopper.CheckoutProblem.ZONE
import ir.vil3ntec.tohid.core.shopper.ShopperSamples.ZONE_A
import ir.vil3ntec.tohid.core.shopper.ShopperSamples.ZONE_B
import ir.vil3ntec.tohid.core.shopper.ShopperSamples.line
import ir.vil3ntec.tohid.core.shopper.ShopperSamples.page
import ir.vil3ntec.tohid.core.shopper.ShopperSamples.shop
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/** برگهٔ ثبت: همان حسابِ وب (`zoneFee`، `feeFor`، `minFor`) و سرور (`forCheckout`، `parseCheckout`). */
class ShopperCheckoutTest {

  private val ticket = CustomerTicket("c700000002@test.local", "v2.t", expiresAt = 10_000)
  //  «فقط ایمیل»: ایمیل هویت است (بلیت)، شماره فقط تماسِ پیک (فقط شکل)
  private val ready = CheckoutForm(name = "احمد", phone = "۰۷۰۰ ۰۰۰ ۰۰۲", email = " C700000002@Test.Local ", addressText = "کارتهٔ سه، سرکِ اول")

  private fun cart(vararg l: CartLine) = Cart("golestan", l.toList())

  @Test fun fixedFeeAndMinimumWithoutZones() {
    val p = page(shop(fee = 5000, min = 20000))
    val c = cart(line("rice", 12050, 1000))
    val s = CheckoutRules.summary(p, c, ready, null)
    assertEquals(12100, s.subtotalMinor)
    assertEquals(5000, s.deliveryFeeMinor)
    assertEquals(17100, s.totalMinor)
    assertEquals(7900, s.shortOfMinMinor)
    assertEquals(listOf(BELOW_MIN), CheckoutRules.problems(p, c, ready, ticket, null, now = 1))
    val pickup = CheckoutRules.summary(p, c, ready.copy(fulfillment = "pickup"), null)
    assertEquals(0, pickup.deliveryFeeMinor)
    assertEquals(20000, pickup.minOrderMinor, "دریافت از دکان همان حداقلِ دکان (سرور: `sf.min_order_minor`)")
  }

  @Test fun zoneFeeFreeOverAndSmallBasketLikeTheServer() {
    //  CHK-06: زیرِ حداقل با هزینهٔ سبدِ کوچک پذیرفته؛ «رایگان از…» فقط هزینهٔ پایه را برمی‌دارد
    assertEquals(3000 + 1500, CheckoutRules.zoneFee(ZONE_A, 50000))
    assertEquals(3000, CheckoutRules.zoneFee(ZONE_A, 60000))
    assertEquals(0, CheckoutRules.zoneFee(ZONE_A, 100000))
    assertEquals(8000, CheckoutRules.zoneFee(ZONE_B, 1_000_000), "بی «رایگان از» همیشه هزینهٔ ناحیه")
    val p = page(zones = listOf(ZONE_A, ZONE_B))
    assertEquals(0, CheckoutRules.minFor(p, "delivery", ZONE_A), "سبدِ کوچک ⇒ حداقل نمی‌بندد")
    assertEquals(40000, CheckoutRules.minFor(p, "delivery", ZONE_B))
    assertEquals(0, CheckoutRules.minFor(p, "delivery", null), "ناحیهٔ ندانسته ⇒ «پس از ناحیه»، نه بستن")
  }

  @Test fun zonedShopNeedsAZoneAndShowsFeeOnlyAfterIt() {
    val p = page(zones = listOf(ZONE_A, ZONE_B))
    val c = cart(line("rice", 25000, 2000))
    val before = CheckoutRules.summary(p, c, ready, null)
    assertNull(before.deliveryFeeMinor)
    assertEquals(50000, before.totalMinor)
    assertEquals(listOf(ZONE), CheckoutRules.problems(p, c, ready, ticket, null, 1))
    val withA = ready.copy(zoneId = "zone_a")
    val s = CheckoutRules.summary(p, c, withA, null)
    assertEquals(4500, s.deliveryFeeMinor)
    assertEquals(1500, s.smallBasketFeeMinor)
    assertEquals(50000, s.freeLeftMinor)
    assertEquals(54500, s.totalMinor)
    assertTrue(CheckoutRules.problems(p, c, withA, ticket, null, 1).isEmpty())
    val gone = ready.copy(zoneId = "zone_removed")
    assertEquals(listOf(ZONE), CheckoutRules.problems(p, c, gone, ticket, null, 1), "ناحیهٔ دیگر-نیست ⇒ دوباره برگزینید")
  }

  @Test fun everyMissingPieceIsNamedInFormOrder() {
    val p = page(shop(min = 0))
    val empty = CheckoutForm()
    assertEquals(listOf(EMPTY, NAME, PHONE, EMAIL, ADDRESS), CheckoutRules.problems(p, null, empty, null, null, 1))
    val c = cart(line("a", 100, 1000).copy(unavailable = true))
    val closed = page(shop(min = 0).copy(ordering = StoreOrdering(accepting = false)))
    assertEquals(listOf(CLOSED, UNAVAILABLE_LINES), CheckoutRules.problems(closed, c, ready, ticket, null, 1))
  }

  @Test fun emailIsTheIdentityAndTicketMustMatchAndBeFresh_phoneIsContactOnly() {
    val p = page(shop(min = 0))
    val c = cart(line("a", 100, 1000))
    assertEquals("+93700000002", CheckoutRules.phoneOf(ready), "رقمِ فارسی، فاصله و ۰ ِ آغاز ⇒ ‎+93")
    assertEquals("c700000002@test.local", CheckoutRules.emailOf(ready), "فاصله و بزرگیِ حرف ⇒ همان ایمیلِ بلیت")
    assertTrue(CheckoutRules.problems(p, c, ready, ticket, null, now = 9_999).isEmpty())
    assertEquals(listOf(VERIFY), CheckoutRules.problems(p, c, ready, ticket, null, now = 10_000), "بلیتِ منقضی")
    assertEquals(listOf(VERIFY), CheckoutRules.problems(p, c, ready.copy(email = "other@test.local"), ticket, null, 1), "ایمیلِ دیگر")
    //  شمارهٔ تماسِ دیگر (بی کد) ⇒ هیچ مانعی: شماره هویت نیست
    assertTrue(CheckoutRules.problems(p, c, ready.copy(phone = "0700000003"), ticket, null, 1).isEmpty(), "شمارهٔ تماس آزاد")
    assertEquals(listOf(EMAIL), CheckoutRules.problems(p, c, ready.copy(email = "نه-ایمیل"), ticket, null, 1))
    //  بلیتِ کهنهٔ شماره‌ای (پیش از «فقط ایمیل»، بی ایمیل) هیچ‌وقت تأییدشده نیست
    assertEquals(listOf(VERIFY), CheckoutRules.problems(p, c, ready, CustomerTicket("", "v2.t", 10_000), null, 1))
    assertEquals(listOf(PHONE), CheckoutRules.problems(p, c, ready.copy(phone = "12"), ticket, null, 1))
  }

  @Test fun pickupNeedsNoAddressAndLandmarkAloneIsEnough() {
    val p = page(shop(min = 0))
    val c = cart(line("a", 100, 1000))
    assertTrue(CheckoutRules.problems(p, c, ready.copy(addressText = "", fulfillment = "pickup"), ticket, null, 1).isEmpty())
    assertTrue(CheckoutRules.problems(p, c, ready.copy(addressText = " ", landmark = "پشتِ مسجد"), ticket, null, 1).isEmpty())
  }

  @Test fun onlyOfferedFulfillmentsAreUsed() {
    val pickupOnly = shop(delivery = false, pickup = true)
    assertEquals(listOf("pickup"), CheckoutRules.fulfillments(pickupOnly))
    assertEquals("pickup", CheckoutRules.fulfillmentOf(pickupOnly, CheckoutForm(fulfillment = "delivery")))
    assertEquals("delivery", CheckoutRules.fulfillmentOf(shop(), CheckoutForm()))
    assertNull(CheckoutRules.fulfillmentOf(shop(delivery = false, pickup = false), CheckoutForm()))
  }

  @Test fun scheduledShopNeedsASlotUnlessAsapIsAllowed() {
    val p = page(shop(min = 0), scheduling = Scheduling(delivery = true))
    val c = cart(line("a", 10000, 1000))
    val slots = SlotsPage("delivery", scheduling = true, allowAsap = false, slots = listOf(
      DeliverySlot(startsAt = 100, endsAt = 200, available = true, feeMinor = 2000),
      DeliverySlot(startsAt = 300, endsAt = 400, available = false),
    ))
    assertEquals(listOf(SLOT), CheckoutRules.problems(p, c, ready, ticket, null, 1), "بازه‌ها هنوز نیامده ⇒ بسته")
    assertEquals(listOf(SLOT), CheckoutRules.problems(p, c, ready, ticket, slots, 1))
    assertEquals(listOf(SLOT), CheckoutRules.problems(p, c, ready.copy(slotStartsAt = 300), ticket, slots, 1), "بازهٔ پُر")
    val picked = ready.copy(slotStartsAt = 100)
    assertTrue(CheckoutRules.problems(p, c, picked, ticket, slots, 1).isEmpty())
    val s = CheckoutRules.summary(p, c, picked, slots)
    assertEquals(2000, s.slotFeeMinor)
    assertEquals(5000 + 2000, s.deliveryFeeMinor, "هزینهٔ بازه روی هزینهٔ ارسال")
    assertTrue(CheckoutRules.problems(p, c, ready, ticket, slots.copy(allowAsap = true), 1).isEmpty(), "«هرچه زودتر» باز")
    assertEquals(0, CheckoutRules.summary(p, c, picked.copy(fulfillment = "pickup"), slots).slotFeeMinor, "هزینهٔ بازه فقط ارسال")
  }

  @Test fun requestBodyMatchesTheWebOrderBody() {
    val p = page(zones = listOf(ZONE_A))
    val c = cart(line("rice", 25000, 2000), line("oil", 30000, 1000))
    val f = ready.copy(zoneId = "zone_a", landmark = " پشتِ مسجد ", note = " زنگ نزنید ", callBeforeArrival = true, leaveWith = "دربان")
    val r = CheckoutRules.request(p, c, f, "+93700000002", "prs", null)
    assertEquals("delivery", r.fulfillment)
    assertEquals("cod", r.paymentMethod)
    assertEquals(CheckoutCustomer("احمد", "+93700000002"), r.customer)
    assertEquals(listOf(CheckoutLine("rice", 2000, 25000), CheckoutLine("oil", 1000, 30000)), r.lines)
    assertEquals(80000 + 3000, r.expectedTotalMinor)
    assertEquals("پشتِ مسجد", r.address?.landmark)
    assertEquals("zone_a", r.address?.zoneId)
    assertEquals("زنگ نزنید", r.note)
    assertEquals(CheckoutDelivery(true, "دربان"), r.delivery)
    assertEquals("prs", r.lang)
    val pickup = CheckoutRules.request(p, c, f.copy(fulfillment = "pickup"), "+93700000002", "fa", null)
    assertNull(pickup.address)
    assertNull(pickup.delivery)
    assertEquals(80000, pickup.expectedTotalMinor)
  }

  /** RSK-04/MKT-02: شناسهٔ تصادفیِ همین نصب در هر ثبت (مثلِ وب)؛ شناسهٔ تخفیف بر آن مقدم؛ ناجور ⇒ در بدنه نمی‌رود */
  @Test fun installDeviceIdGoesWithEveryOrderButOnlyInTheServerShape() {
    val p = page(shop(min = 0))
    val c = cart(line("a", 100, 1000))
    val dev = "0123456789abcdef0123456789abcdef"
    assertEquals(dev, CheckoutRules.request(p, c, ready, "+93700000002", "fa", null, deviceId = dev).deviceId)
    assertNull(CheckoutRules.request(p, c, ready, "+93700000002", "fa", null).deviceId, "بی شناسه ⇒ همان بدنهٔ پیشین")
    assertNull(CheckoutRules.request(p, c, ready, "+93700000002", "fa", null, deviceId = "bad id!").deviceId)
    assertNull(CheckoutRules.request(p, c, ready, "+93700000002", "fa", null, deviceId = "short").deviceId)
    val promo = AppliedPromo(0, "", "promo-device-0001")
    assertEquals("promo-device-0001", CheckoutRules.request(p, c, ready, "+93700000002", "fa", null, promo, deviceId = dev).deviceId)
  }

  @Test fun idempotencyKeyStaysForTheSameBodyAndChangesWithIt() {
    val p = page(shop(min = 0))
    val c = cart(line("a", 100, 1000))
    val r1 = CheckoutRules.request(p, c, ready, "+93700000002", "fa", null)
    var n = 0
    val fresh = { "app-key-${++n}" }
    val m1 = CheckoutKeys.keyFor(null, r1, fresh)
    assertEquals("app-key-1", m1.key)
    assertSame(m1, CheckoutKeys.keyFor(m1, r1.copy(), fresh), "زدنِ دوباره پس از قطعِ شبکه ⇒ همان کلید")
    val m2 = CheckoutKeys.keyFor(m1, r1.copy(note = "تازه"), fresh)
    assertEquals("app-key-2", m2.key)
    val real = CheckoutKeys.keyFor(null, r1)
    assertTrue(Regex("^app-[0-9A-Z]{26}$").matches(real.key), real.key)
    assertTrue(Regex("^[A-Za-z0-9_.:-]{8,128}$").matches(real.key), "KEY_RE ِ سرور")
    assertNotEquals(real.key, CheckoutKeys.keyFor(null, r1).key)
  }

  // ── CHK-14: «با کدام اسکناس…» (همان `tenders` ِ `checkout.js` و `delivery-prefs.checkTender` ِ سرور) ──

  private fun prefs(p: StorePage) = StorePage(p.shop, p.delivery, true, false, StoreFeatures(deliveryPrefs = true))

  @Test fun tenderOptionsAreTheWebListAndAlwaysPassTheServerRule() {
    assertEquals(listOf(100_000L), CheckoutRules.tenderOptions(95_000), "۹۵۰ ⇒ فقط ۱۰۰۰")
    assertEquals(listOf(110_000L, 150_000L), CheckoutRules.tenderOptions(100_000), "جمعِ گرد ⇒ اسکناسِ بالاتر؛ ۲۰۰۰ پول خردِ ۱۰۰۰ است، نه")
    assertEquals(listOf(130_000L, 150_000L, 200_000L), CheckoutRules.tenderOptions(123_450))
    assertEquals(emptyList(), CheckoutRules.tenderOptions(0))
    assertEquals(emptyList(), CheckoutRules.tenderOptions(-500))
    //  هر گزینه همان قاعدهٔ سرور: بیش از جمع، افغانیِ کامل، پول خرد کمتر از یک اسکناسِ ۱۰۰۰
    var total = 1L
    while (total < 2_500_000) {
      val list = CheckoutRules.tenderOptions(total)
      assertTrue(list.isNotEmpty() && list == list.sorted() && list.distinct() == list, "جمع $total: $list")
      for (x in list) assertTrue(x > total && x % 100 == 0L && x - total < CheckoutRules.NOTE_MINOR, "جمع $total، اسکناس $x")
      total += 3_337
    }
  }

  @Test fun tenderGoesOnlyWithTheFlagForDeliveryAndTodaysTotal() {
    val c = cart(line("rice", 45000, 2000))
    val f = ready.copy(cashTenderMinor = 100_000)
    //  پرچمِ خاموش ⇒ نه پرسش، نه کلید در بدنه (همان سفارشِ پیشین، بایت‌به‌بایت)
    val off = page()
    assertTrue(CheckoutRules.tenders(off, c, f, null).isEmpty())
    val plain = CheckoutRules.request(off, c, f, "+93700000002", "fa", null)
    assertEquals(CheckoutDelivery(false, ""), plain.delivery)
    assertTrue("cashTenderMinor" !in CheckoutKeys.json(plain), CheckoutKeys.json(plain))
    //  روشن: جمع ۹۰۰ + ارسال ۵۰ = ۹۵۰ ⇒ فقط ۱۰۰۰
    val on = prefs(page())
    assertTrue(CheckoutRules.tenderShown(on, c, f, null))
    assertEquals(listOf(100_000L), CheckoutRules.tenders(on, c, f, null))
    assertEquals(100_000L, CheckoutRules.tenderOf(on, c, f, null))
    assertEquals(CheckoutDelivery(false, "", 100_000), CheckoutRules.request(on, c, f, "+93700000002", "fa", null).delivery)
    assertTrue("\"cashTenderMinor\":100000" in CheckoutKeys.json(CheckoutRules.request(on, c, f, "+93700000002", "fa", null)))
    //  جمع عوض شد و اسکناس دیگر درست نیست ⇒ برداشته می‌شود؛ هرگز مبلغی که سرور رد می‌کند
    val bigger = cart(line("rice", 45000, 3000))
    assertNull(CheckoutRules.tenderOf(on, bigger, f, null))
    assertNull(CheckoutRules.request(on, bigger, f, "+93700000002", "fa", null).delivery?.cashTenderMinor)
    //  همان جمعِ `expectedTotalMinor` (پس از تخفیفِ سرور): ۹۵۰ − ۱۰۰ = ۸۵۰ ⇒ ۹۰۰ یا ۱۰۰۰
    val promo = AppliedPromo(discountMinor = 10_000)
    assertEquals(listOf(90_000L, 100_000L), CheckoutRules.tenders(on, c, f, null, 10_000))
    val r = CheckoutRules.request(on, c, f.copy(cashTenderMinor = 90_000), "+93700000002", "fa", null, promo)
    assertEquals(85_000, r.expectedTotalMinor)
    assertEquals(90_000L, r.delivery?.cashTenderMinor)
    assertNull(CheckoutRules.tenderOf(on, c, f.copy(cashTenderMinor = 90_000), null), "بی تخفیف ۹۰۰ کمتر از جمع است")
    //  فقط ارسال: «دریافت از دکان» ⇒ نه پرسش، نه دستورِ تحویل
    val pickup = f.copy(fulfillment = "pickup")
    assertTrue(!CheckoutRules.tenderShown(on, c, pickup, null) && CheckoutRules.tenders(on, c, pickup, null).isEmpty())
    assertNull(CheckoutRules.request(on, c, pickup, "+93700000002", "fa", null).delivery)
    //  ناحیه هنوز گزیده نشده (هزینه نادانسته) ⇒ پرسش پنهان (همان وب)؛ با ناحیه ⇒ گزینه‌های جمعِ همان ناحیه
    val zoned = prefs(page(zones = listOf(ZONE_B)))
    assertTrue(!CheckoutRules.tenderShown(zoned, c, f, null))
    assertEquals(listOf(100_000L), CheckoutRules.tenders(zoned, c, f.copy(zoneId = "zone_b"), null), "۹۰۰ + ۸۰ = ۹۸۰ ⇒ ۱۰۰۰")
    assertEquals(listOf(110_000L, 150_000L), CheckoutRules.tenders(zoned, cart(line("rice", 46000, 2000)), f.copy(zoneId = "zone_b"), null), "۹۲۰ + ۸۰ = ۱۰۰۰")
  }
}
