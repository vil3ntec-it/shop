package ir.vil3ntec.tohid.core.storefront

import ir.vil3ntec.tohid.core.storefront.StorefrontDelivery.LatLng
import ir.vil3ntec.tohid.core.storefront.StorefrontDelivery.RuleDraft
import ir.vil3ntec.tohid.core.storefront.StorefrontDelivery.ZoneDraft
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 *  «ارسال و زمان» ِ ویترین — همان قاعده‌های سرور (`server/src/lib/storefront-delivery.js`)
 *  و نسخهٔ وب (`license/storefront-owner.js`).
 */
class StorefrontDeliveryTest {
  private val D = StorefrontDelivery

  @Test fun lat_lng_accepts_persian_digits_and_separators() {
    assertEquals(LatLng(34.53, 69.17), D.parseLatLng("34.53, 69.17"))
    assertEquals(LatLng(34.53, 69.17), D.parseLatLng("۳۴٫۵۳،۶۹٫۱۷"))
    assertEquals(LatLng(34.53, 69.17), D.parseLatLng(" 34.53 69.17 "))
    assertEquals(LatLng(34.123457, 69.0), D.parseLatLng("34.1234567, 69"))
    assertNull(D.parseLatLng("95, 69"))
    assertNull(D.parseLatLng("34.5"))
    assertNull(D.parseLatLng("abc, def"))
  }

  @Test fun polygon_needs_three_to_hundred_valid_points() {
    val (pts, err) = D.parsePolygon("34.50, 69.12\n34.50, 69.22\n34.56, 69.22؛ 34.56, 69.12")
    assertNull(err)
    assertEquals(4, pts!!.size)
    assertEquals("34.5, 69.12\n34.5, 69.22\n34.56, 69.22\n34.56, 69.12", D.formatPolygon(pts))
    assertTrue(D.parsePolygon("1,1\n2,2").second!!.contains("۳"))
    assertTrue(D.parsePolygon("1,1\n2,2\nxx").second!!.contains("xx"))
    assertTrue(D.parsePolygon((1..101).joinToString("\n") { "1, $it" }).second!!.contains("۱۰۰"))
  }

  @Test fun range_is_one_window_in_local_minutes() {
    assertEquals(600 to 720, D.parseRange("10:00-12:00").first)
    assertEquals(1320 to 1440, D.parseRange("۲۲:۰۰-۲۴:۰۰").first)
    assertTrue(D.parseRange("12:00-10:00").second != null)
    assertTrue(D.parseRange("08:00-10:00، 12:00-14:00").second != null)
  }

  @Test fun check_builds_server_body_or_errors_per_field() {
    val ok = D.check(
      listOf(
        ZoneDraft(name = "کارته‌سه", kind = "polygon", fee = "30", min = "0", free = "1500", polygon = "34.50,69.12\n34.50,69.22\n34.56,69.22"),
        ZoneDraft(name = "دارالامان", kind = "radius", fee = "۸۰", min = "۶۰۰", center = "34.45, 69.10", radiusKm = "1.5"),
      ),
      listOf(RuleDraft(fulfillment = "pickup", weekdays = setOf(0, 6), range = "10:00-12:00", capacity = "۳", cutoff = "")),
      "۳",
    )
    assertTrue(ok.errors.toString(), ok.errors.isEmpty())
    assertEquals(3000L, ok.zones[0].feeMinor)
    assertEquals(150000L, ok.zones[0].freeOverMinor)
    assertEquals(1500, ok.zones[1].radiusM)
    assertEquals(60000L, ok.zones[1].minOrderMinor)
    assertEquals(listOf(0, 6), ok.rules[0].weekdays)
    assertEquals(0, ok.rules[0].cutoffMin)
    assertEquals(3, ok.slotDays)

    val bad = D.check(
      listOf(ZoneDraft(name = "", kind = "radius", center = "x", radiusKm = "0.01", fee = "-1")),
      listOf(RuleDraft(weekdays = emptySet(), range = "x", capacity = "0", cutoff = "99999")),
      "30",
    )
    for (k in listOf("z0.name", "z0.center", "z0.radius", "z0.fee", "r0.days", "r0.range", "r0.capacity", "r0.cutoff", "slotDays")) {
      assertTrue("$k در ${bad.errors}", bad.errors.containsKey(k))
    }
  }

  @Test fun overlapping_active_rules_of_same_method_and_day_are_refused() {
    val two = listOf(
      RuleDraft(fulfillment = "delivery", weekdays = setOf(1, 2), range = "10:00-12:00"),
      RuleDraft(fulfillment = "delivery", weekdays = setOf(2), range = "11:00-13:00"),
    )
    assertTrue(D.check(emptyList(), two, "3").errors.containsKey("r1.range"))
    //  روشِ دیگر، روزِ دیگر یا بازهٔ خاموش ⇒ اشکالی نیست
    assertTrue(D.check(emptyList(), listOf(two[0], two[1].copy(fulfillment = "pickup")), "3").errors.isEmpty())
    assertTrue(D.check(emptyList(), listOf(two[0], two[1].copy(weekdays = setOf(3))), "3").errors.isEmpty())
    assertTrue(D.check(emptyList(), listOf(two[0], two[1].copy(active = false)), "3").errors.isEmpty())
    //  پشتِ هم (۱۲:۰۰ پایان و آغاز) روی هم نیست
    assertTrue(D.check(emptyList(), listOf(two[0], two[1].copy(range = "12:00-14:00")), "3").errors.isEmpty())
  }

  @Test fun specs_parse_like_server() {
    val (ok, err) = StorefrontSpecs.parse("وزن: ۱ کیلو\nکشور: افغانستان؛ رنگ:سبز")
    assertNull(err)
    assertEquals(listOf("وزن" to "۱ کیلو", "کشور" to "افغانستان", "رنگ" to "سبز"), ok)
    assertEquals("وزن: ۱ کیلو\nکشور: افغانستان\nرنگ: سبز", StorefrontSpecs.format(ok!!))
    assertEquals(emptyList<Pair<String, String>>(), StorefrontSpecs.parse("  ").first)
    assertTrue(StorefrontSpecs.parse("بی‌دونقطه").second != null)
    assertTrue(StorefrontSpecs.parse("وزن:").second != null)
    assertTrue(StorefrontSpecs.parse((1..21).joinToString("\n") { "n$it: v" }).second != null)
  }

  @Test fun small_basket_fee_needs_a_minimum_and_blank_means_refuse() {
    val poly = "34.50,69.12\n34.50,69.22\n34.56,69.22"
    val ok = D.check(
      listOf(
        ZoneDraft(name = "با هزینه", fee = "30", min = "600", small = "۲۵", polygon = poly),
        ZoneDraft(name = "بی هزینه", fee = "30", min = "600", small = "", polygon = poly),
        ZoneDraft(name = "صفر", fee = "30", min = "600", small = "0", polygon = poly),
      ),
      emptyList(), "3",
    )
    assertTrue(ok.errors.toString(), ok.errors.isEmpty())
    assertEquals(2500L, ok.zones[0].smallBasketFeeMinor)
    assertNull(ok.zones[1].smallBasketFeeMinor)
    //  صفر یعنی «نه» — سرور صفر را رد می‌کند، پس null فرستاده می‌شود
    assertNull(ok.zones[2].smallBasketFeeMinor)

    val bad = D.check(
      listOf(
        ZoneDraft(name = "بی حداقل", fee = "30", min = "0", small = "25", polygon = poly),
        ZoneDraft(name = "منفی", fee = "30", min = "600", small = "-5", polygon = poly),
        ZoneDraft(name = "بزرگ", fee = "30", min = "600", small = "1000001", polygon = poly),
      ),
      emptyList(), "3",
    )
    for (k in listOf("z0.small", "z1.small", "z2.small")) assertTrue("$k در ${bad.errors}", bad.errors.containsKey(k))
  }

  @Test fun slot_fee_only_for_delivery_and_pickup_always_zero() {
    val c = D.check(
      emptyList(),
      listOf(
        RuleDraft(fulfillment = "delivery", weekdays = setOf(5), range = "18:00-20:00", fee = "۲۰"),
        RuleDraft(fulfillment = "pickup", weekdays = setOf(5), range = "18:00-20:00", fee = "50"),
        RuleDraft(fulfillment = "delivery", weekdays = setOf(4), range = "10:00-12:00", fee = ""),
      ),
      "3",
    )
    assertTrue(c.errors.toString(), c.errors.isEmpty())
    assertEquals(listOf(2000L, 0L, 0L), c.rules.map { it.feeMinor })
    val bad = D.check(emptyList(), listOf(RuleDraft(fulfillment = "delivery", fee = "x")), "3")
    assertTrue(bad.errors.toString(), bad.errors.containsKey("r0.fee"))
  }

  @Test fun server_body_always_carries_fee_keys_so_null_means_clear() {
    val c = D.check(
      listOf(ZoneDraft(name = "الف", kind = "radius", fee = "30", min = "600", center = "34.5,69.1")),
      listOf(RuleDraft(fulfillment = "delivery", weekdays = setOf(1), range = "10:00-12:00", fee = "15")),
      "3",
    )
    val body = ir.vil3ntec.tohid.data.repo.StorefrontDeliveryRepository.body(c, allowAsap = true).toString()
    assertTrue(body, body.contains("\"smallBasketFeeMinor\":null"))
    assertTrue(body, body.contains("\"feeMinor\":1500"))
    //  و بازگشت از پاسخِ سرور همان کادرها را پر می‌کند
    val z = ir.vil3ntec.tohid.data.repo.StorefrontZoneDto(name = "الف", minOrderMinor = 60000, smallBasketFeeMinor = 2550).draft()
    assertEquals("25.5", z.small)
    assertEquals("20", ir.vil3ntec.tohid.data.repo.StorefrontSlotRuleDto(feeMinor = 2000).draft().fee)
    assertEquals("", ir.vil3ntec.tohid.data.repo.StorefrontZoneDto().draft().small)
  }

  @Test fun zone_summary_is_persian() {
    val z = D.check(listOf(ZoneDraft(name = "مرکز", kind = "radius", fee = "30", min = "200", free = "1000", center = "34.5,69.1")), emptyList(), "3").zones[0]
    assertEquals("مرکز · ارسال ۳۰ افغانی · حداقل ۲۰۰ · رایگان از ۱۰۰۰", D.zoneSummary(z))
    val small = D.check(listOf(ZoneDraft(name = "مرکز", kind = "radius", fee = "30", min = "200", small = "15", center = "34.5,69.1")), emptyList(), "3").zones[0]
    assertEquals("مرکز · ارسال ۳۰ افغانی · حداقل ۲۰۰ · سبدِ کوچک ۱۵", D.zoneSummary(small))
  }
}
