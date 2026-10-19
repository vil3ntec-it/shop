package ir.vil3ntec.tohid.core.storefront

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.TimeZone

/**
 *  منطقِ صفحهٔ «ویترین» ِ صاحب دکان — همان قاعده‌های سرور
 *  (`server/src/lib/storefront.js`) و همان برچسب‌هایی که مشتری می‌بیند (§۳۱.۱).
 */
class StorefrontOwnerTest {
  private val S = StorefrontOwner
  private val KABUL = "Asia/Kabul"

  @Test fun slug_rules_match_server() {
    assertNull(S.slugError("my-shop"))
    assertNull(S.slugError(" My-Shop "))   // کوچک می‌شود
    assertNotNull(S.slugError(""))
    assertNotNull(S.slugError("ab"))
    assertNotNull(S.slugError("-shop"))
    assertNotNull(S.slugError("a--b"))
    assertNotNull(S.slugError("bad_slug"))
    assertEquals("این نشانی رزرو است", S.slugError("api"))
    assertEquals("این نشانی رزرو است", S.slugError("www"))
  }

  @Test fun money_round_trips_in_minor_units_with_persian_digits() {
    assertEquals(12050L, S.parseMinor("۱۲۰٫۵"))
    assertEquals(12050L, S.parseMinor("120.50"))
    assertEquals(3000000L, S.parseMinor("30,000"))
    assertNull(S.parseMinor("  "))
    assertEquals("120.5", S.formatMinor(12050))
    assertEquals("300", S.formatMinor(30000))
    assertEquals("0.05", S.formatMinor(5))
    for (bad in listOf("-1", "1.234", "abc", "1e5")) {
      assertTrue(bad, runCatching { S.parseMinor(bad) }.isFailure)
    }
    assertTrue(runCatching { S.parseMinor("2000000", max = S.MAX_FEE_MINOR) }.isFailure)
  }

  @Test fun qty_in_milli() {
    assertEquals(2500L, S.parseQtyMilli("۲٫۵"))
    assertEquals(5000L, S.parseQtyMilli("5"))
    assertNull(S.parseQtyMilli(""))
    assertTrue(runCatching { S.parseQtyMilli("0") }.isFailure)
    assertEquals("2.5", S.formatQtyMilli(2500))
    assertEquals("5", S.formatQtyMilli(5000))
  }

  @Test fun hours_parse_validate_and_format() {
    assertEquals(510, S.parseHm("8:30"))
    assertEquals(1440, S.parseHm("24:00"))
    assertNull(S.parseHm("24:30"))
    assertNull(S.parseHm("7:60"))
    assertEquals("08:30", S.formatHm(510))

    val d = S.parseDay("14:00-20:00، ۰۸:۰۰-۱۲:۰۰")
    assertNull(d.error)
    assertEquals(listOf(480 to 720, 840 to 1200), d.ranges)
    assertEquals("08:00-12:00، 14:00-20:00", S.formatDay(d.ranges))
    assertEquals(emptyList<Pair<Int, Int>>(), S.parseDay("").ranges)   // تعطیل
    assertNotNull(S.parseDay("08:00-12:00, 11:00-13:00").error)       // هم‌پوشانی
    assertNotNull(S.parseDay("20:00-02:00").error)                    // عبور از نیمه‌شب در روزِ بعد
    assertNotNull(S.parseDay("8-12").error)
    assertNotNull(S.parseDay("1:00-2:00,3:00-4:00,5:00-6:00,7:00-8:00,9:00-10:00").error)
  }

  @Test fun closed_dates_are_real_calendar_days() {
    val (ok, err) = S.parseClosedDates("2026-10-20، ۲۰۲۶-۱۱-۰۱ 2026-10-20")
    assertNull(err)
    assertEquals(listOf("2026-10-20", "2026-11-01"), ok)
    assertNotNull(S.parseClosedDates("2026-02-30").second)
    assertNotNull(S.parseClosedDates("20-10-2026").second)
    assertTrue(S.validDate("2028-02-29"))
  }

  @Test fun badge_labels_are_the_customer_sentences() {
    // ۲۰۲۶-۱۰-۱۹ ساعتِ ۲۰:۳۰ ِ کابل = ۱۶:۰۰ UTC
    val until = java.util.Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply { clear(); set(2026, 9, 19, 16, 0) }.timeInMillis
    assertEquals("تا ساعتِ ۲۰:۳۰ آنلاین اعلام شده", S.badgeLabel("declared_until", until, KABUL))
    assertEquals("باز است · آنلاین", S.badgeLabel("open_online", null, KABUL))
    assertEquals("باز است · اتصال تأیید نشده", S.badgeLabel("open_unconfirmed", null, KABUL))
    assertEquals("بسته است", S.badgeLabel("closed", null, KABUL))
    assertEquals("موقتاً آفلاین", S.badgeLabel("offline", null, KABUL))
    assertEquals(StorefrontOwner.Tone.WARN, S.badgeTone("open_unconfirmed"))
    assertEquals(StorefrontOwner.Tone.OFF, S.badgeTone("offline"))
    //  بی شاهد هیچ «همین حالا» یی نیست
    assertEquals("اتصالِ دستگاه‌های دکان هنوز تأیید نشده", S.connectionLabel(null, KABUL))
    assertEquals("آخرین تأییدِ اتصال: ۲۰:۳۰", S.connectionLabel(until, KABUL))
  }

  @Test fun online_until_is_the_next_occurrence_in_store_time() {
    // اکنون: ۱۹ اکتبر، ۱۰:۰۰ ِ کابل (۰۵:۳۰ UTC)
    val now = java.util.Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply { clear(); set(2026, 9, 19, 5, 30) }.timeInMillis
    val today = S.declaredUntilFor(20 * 60 + 30, now, KABUL)
    assertEquals("20:30", S.clock(today, KABUL))
    assertTrue(today - now < 24 * 3600_000L)
    val tomorrow = S.declaredUntilFor(9 * 60, now, KABUL)   // ۰۹:۰۰ گذشته ⇒ فردا
    assertEquals("09:00", S.clock(tomorrow, KABUL))
    assertEquals(23 * 3600_000L, tomorrow - now)
    // منطقهٔ زمانیِ دیگر، همان «ساعتِ دکان»
    assertEquals("20:30", S.clock(S.declaredUntilFor(20 * 60 + 30, now, "Asia/Tehran"), "Asia/Tehran"))
  }

  @Test fun pending_declaration_survives_restart_with_same_event_id() {
    val p = S.pending("online_until", 1_800_000_000_000L, now = 1_760_000_000_000L, random = 0xabcdefL)
    val back = S.decode(S.encode(p))
    assertEquals(p, back)
    assertTrue(Regex("^[A-Za-z0-9_.:-]{8,80}$").matches(p.eventId))
    assertNull(S.pending("closed", 123L, 1L, 1L).declaredUntil)   // فقط «تا ساعتِ» زمان دارد
    assertNull(S.decode(null))
    assertNull(S.decode("x|online|"))
    assertNull(S.decode("pres-aaaaaaaa|online_until||1"))        // «تا ساعتِ» بی زمان
    assertNull(S.decode("pres-aaaaaaaa|hack||1"))
    assertTrue(runCatching { S.pending("online_until", null, 1L, 1L) }.isFailure)
  }

  @Test fun bulk_report_counts_each_row() {
    val r = S.bulkReport(listOf("a" to null, "b" to "این کالا در دفترِ دکان نیست", "c" to null))
    assertEquals(2, r.ok)
    assertEquals(listOf("b" to "این کالا در دفترِ دکان نیست"), r.failed)
    assertEquals("۲ انجام شد، ۱ نشد", r.summary)
    assertEquals("۳ کالا انجام شد", S.bulkReport(listOf("a" to null, "b" to null, "c" to null)).summary)
  }
}
