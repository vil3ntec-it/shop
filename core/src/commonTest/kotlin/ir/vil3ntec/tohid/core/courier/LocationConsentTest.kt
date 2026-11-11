package ir.vil3ntec.tohid.core.courier

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 *  S94 — رضایتِ خودِ پیک برای موقعیتِ زنده (`LocationConsent`): پرچمِ خاموشِ دکان ⇒ همان پیشین و بی کلید؛ پرچمِ روشن ⇒
 *  فقط با «بله» ِ پیک؛ ۴۰۳ ِ سرور ⇒ دیگر نه تا «بله». همان کدِ `server/src/lib/courier-location-consent.js`.
 */
class LocationConsentTest {

  @Test fun flagOffKeepsTodayAndHidesTheToggle() {
    val off = LocationConsent()
    assertTrue(off.mayShare, "پرچمِ خاموش ⇒ همان رفتارِ امروز (پرچم‌های دکان بس است)")
    assertFalse(off.showToggle, "کلیدی که اثری ندارد دیده نمی‌شود")
    //  پرچمِ خاموش، «بله» ِ پیشین یا نه، همان
    assertTrue(LocationConsent(required = false, granted = false).mayShare)
    assertTrue(LocationConsent(required = false, granted = true, grantedAt = 5L).mayShare)
  }

  @Test fun flagOnSharesOnlyAfterTheCourierSaysYes() {
    val asked = LocationConsent(required = true)
    assertFalse(asked.mayShare, "بی «بله» هیچ نقطه‌ای")
    assertTrue(asked.showToggle)
    val yes = asked.answered(granted = true, grantedAt = 1_800_000_000_000L)
    assertTrue(yes.mayShare)
    assertEquals(1_800_000_000_000L, yes.grantedAt)
    assertTrue(yes.required, "پرچم همان پیشین می‌ماند")
    val no = yes.answered(granted = false, grantedAt = 1_800_000_000_000L)
    assertFalse(no.mayShare, "«نه» ⇒ اثرِ بعدی همان دم بسته")
    assertNull(no.grantedAt, "«نه» زمانِ «بله» ِ پیشین را نگه نمی‌دارد")
  }

  @Test fun serverRefusalStopsUntilTheNextYes() {
    assertEquals("location_consent_required", LocationConsent.REQUIRED_CODE)
    for (prev in listOf(LocationConsent(), LocationConsent(required = true, granted = true, grantedAt = 9L))) {
      val r = prev.refusedByServer()
      assertFalse(r.mayShare, "۴۰۳ ِ سرور ⇒ گوشی دیگر نمی‌فرستد")
      assertTrue(r.showToggle, "کلید دیده می‌شود تا پیک دوباره «بله» بگوید")
      assertTrue(r.answered(granted = true, grantedAt = 10L).mayShare)
    }
  }
}
