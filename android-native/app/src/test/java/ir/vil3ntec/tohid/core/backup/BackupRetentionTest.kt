package ir.vil3ntec.tohid.core.backup

import ir.vil3ntec.tohid.core.backup.BackupRetention.Policy
import ir.vil3ntec.tohid.core.backup.BackupRetention.Snap
import ir.vil3ntec.tohid.core.calendar.WeekRules
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Random

/**
 *  OPS-02 — «۷ نسخهٔ روزانه + ۴ نسخهٔ هفتگی» (به‌علاوهٔ ۶ تای آخر، که
 *  همان ضمانتِ پیشینِ `AutoBackup.KEEP = 6` است و کم نمی‌شود).
 *
 *  زمان‌ها همه UTC نوشته شده‌اند و اختلافِ ساعتِ دکان جدا داده می‌شود
 *  (کابل ۲۷۰ دقیقه، تهران ۲۱۰)، تا مرزِ روز و هفته دقیق سنجیده شود.
 */
class BackupRetentionTest {

  private val kabul: (Long) -> Int = { 270 }
  private val utc: (Long) -> Int = { 0 }

  /** «2026-10-17T10:00» به وقتِ UTC ⇐ میلی‌ثانیه */
  private fun t(utcLocal: String): Long {
    val (date, time) = utcLocal.split("T")
    val (y, m, d) = date.split("-").map { it.toInt() }
    val (hh, mm) = time.split(":").map { it.toInt() }
    val days = ir.vil3ntec.tohid.core.calendar.CivilDays.jdnOf(y, m, d) - 2440588L
    return days * 86_400_000L + (hh * 60 + mm) * 60_000L
  }

  private fun snaps(vararg at: String) = at.map { Snap(it, t(it)) }

  @Test fun `بی نسخه، چیزی نمی‌ماند و خطایی نیست`() {
    assertEquals(emptySet<String>(), BackupRetention.keep(emptyList(), kabul))
  }

  @Test fun `نسخه‌های کم همه می‌مانند`() {
    val s = snaps("2026-10-17T05:00", "2026-10-17T05:30", "2026-10-16T05:00")
    assertEquals(s.map { it.id }.toSet(), BackupRetention.keep(s, kabul))
  }

  @Test fun `هر نیم ساعت، چهل روز ⇐ ۶ آخر، تازه‌ترینِ ۷ روز، تازه‌ترینِ ۴ هفته`() {
    //  از ۲۰۲۶/۰۹/۰۸ تا ۲۰۲۶/۱۰/۱۷، هر نیم ساعت از ۰۳:۳۰ تا ۱۷:۳۰ به وقتِ UTC (۸ صبح تا ۱۰ شب کابل)
    val all = ArrayList<Snap>()
    for (day in 0 until 40) {
      for (half in 0..28) {
        val at = t("2026-09-08T03:30") + day * 86_400_000L + half * 1_800_000L
        all += Snap("s$at", at)
      }
    }
    val kept = BackupRetention.keep(all, kabul).map { id -> all.first { it.id == id } }.sortedByDescending { it.at }
    //  ۱۰/۱۷ شنبه است (آغازِ هفته). ۶ آخر (همه از امروز) + ۶ روزِ دیگر (۱۰/۱۱ تا ۱۰/۱۶)
    //  + دو هفتهٔ پیش‌تر (۱۰/۰۹ و ۱۰/۰۲)؛ تازه‌ترینِ هفتهٔ ۱۰/۱۰ همان ۱۰/۱۶ است که روزانه مانده
    assertEquals(6 + 6 + 2, kept.size)
    assertEquals(all.maxOf { it.at }, kept.first().at)
    //  تازه‌ترینِ هر روز، ساعتِ ۱۷:۳۰ UTC (۱۰ شبِ کابل) است
    val dailies = kept.drop(6)
    assertTrue(dailies.all { (it.at - t("2026-09-08T17:30")) % 86_400_000L == 0L })
    assertEquals(listOf(t("2026-10-09T17:30"), t("2026-10-02T17:30")), kept.takeLast(2).map { it.at })
  }

  @Test fun `مرزِ روز به وقتِ دکان است، نه UTC`() {
    //  ۱۹:۲۰ و ۱۹:۴۰ ِ UTC یعنی ۲۳:۵۰ و ۰۰:۱۰ ِ کابل ⇐ دو روزِ جدا در کابل، یک روز در UTC
    val s = snaps("2026-10-16T19:20", "2026-10-16T19:40")
    val p = Policy(last = 0, daily = 7, weekly = 0)
    assertEquals(2, BackupRetention.keep(s, kabul, p).size)
    assertEquals(setOf("2026-10-16T19:40"), BackupRetention.keep(s, utc, p))
  }

  @Test fun `روزِ کاری که از نیمه‌شب می‌گذرد — یک‌ونیمِ بامداد به نامِ دیروز است`() {
    //  کابل: ۲۲:۰۰ (۱۷:۳۰ UTC) و ۰۱:۳۰ بامدادِ فردا (۲۱:۰۰ UTC)
    val s = snaps("2026-10-16T17:30", "2026-10-16T21:00")
    val closesAt2 = Policy(last = 0, daily = 7, weekly = 0, dayStartMinutes = 120)
    assertEquals(setOf("2026-10-16T21:00"), BackupRetention.keep(s, kabul, closesAt2))
    val midnight = Policy(last = 0, daily = 7, weekly = 0, dayStartMinutes = 0)
    assertEquals(2, BackupRetention.keep(s, kabul, midnight).size)
  }

  @Test fun `روزِ بی کار شمرده نمی‌شود — ۷ روزانه یعنی ۷ روزی که نسخه دارند`() {
    //  دکان جمعه‌ها بسته است و نسخه‌ای ساخته نمی‌شود؛ ده روزِ پشتِ هم با یک روزِ خالی در میان
    val days = (0 until 10).filter { it != 3 && it != 7 }
    val s = days.map { d -> val at = t("2026-10-01T10:00") + d * 86_400_000L; Snap("d$d", at) }
    val kept = BackupRetention.keep(s, kabul, Policy(last = 0, daily = 7, weekly = 0))
    assertEquals(setOf("d9", "d8", "d6", "d5", "d4", "d2", "d1"), kept)
  }

  @Test fun `مرزِ هفته از شنبه است (پیش‌فرض) و قابلِ تنظیم`() {
    //  ۲۰۲۶/۱۰/۱۶ جمعه، ۲۰۲۶/۱۰/۱۷ شنبه، ۲۰۲۶/۱۰/۱۹ دوشنبه
    val s = snaps("2026-10-16T10:00", "2026-10-17T10:00", "2026-10-19T10:00")
    val weeklyOnly = Policy(last = 0, daily = 0, weekly = 4)
    //  هفته از شنبه: جمعه هفتهٔ قبل است ⇐ دو هفته، تازه‌ترینِ هر کدام
    assertEquals(setOf("2026-10-19T10:00", "2026-10-16T10:00"), BackupRetention.keep(s, kabul, weeklyOnly))
    //  هفته از دوشنبه: جمعه و شنبه یک هفته‌اند
    val monday = weeklyOnly.copy(week = WeekRules(weekStart = 1))
    assertEquals(setOf("2026-10-19T10:00", "2026-10-17T10:00"), BackupRetention.keep(s, kabul, monday))
  }

  @Test fun `تغییرِ اختلافِ ساعت (ساعتِ تابستانی) هر لحظه را با اختلافِ خودش می‌سنجد`() {
    //  منطقه‌ای که ساعتِ ۲۰:۰۰ UTC از +۲۷۰ به +۲۱۰ برمی‌گردد
    val back = t("2026-09-21T20:00")
    val tz: (Long) -> Int = { if (it < back) 270 else 210 }
    //  ۱۹:۰۰ UTC ⇐ ۲۳:۳۰ محلی (۲۱م)؛ ۲۰:۴۰ UTC ⇐ ۰۰:۱۰ محلی (۲۲م)؛ ۲۰:۰۵ UTC ⇐ ۲۳:۳۵ محلی (۲۱م)
    val s = snaps("2026-09-21T19:00", "2026-09-21T20:05", "2026-09-21T20:40")
    val kept = BackupRetention.keep(s, tz, Policy(last = 0, daily = 7, weekly = 0))
    assertEquals(setOf("2026-09-21T20:05", "2026-09-21T20:40"), kept)
  }

  @Test fun `ساعتِ جلوافتاده — نسخهٔ آینده می‌ماند ولی بقیه را پاک نمی‌کند`() {
    val now = t("2026-10-17T10:00")
    val s = (0 until 20).map { Snap("n$it", now - it * 86_400_000L) } + Snap("future", t("2030-01-01T00:00"))
    val kept = BackupRetention.keep(s, kabul)
    assertTrue("future" in kept)
    //  ۶ آخر: آینده + ۵ تای تازه؛ روزانه: آینده + ۶ روزِ تازه؛ هفتگی: ۴ هفتهٔ تازه (آینده یکی‌شان)
    assertTrue((0 until 6).all { "n$it" in kept })
    assertTrue(kept.size >= 7)
  }

  @Test fun `هم‌زمانی — ترتیبِ یکسان هر بار یک جواب`() {
    val s = listOf(Snap("b", 1000), Snap("a", 1000), Snap("c", 1000))
    val p = Policy(last = 1, daily = 0, weekly = 0)
    assertEquals(setOf("c"), BackupRetention.keep(s, utc, p))
    assertEquals(setOf("c"), BackupRetention.keep(s.reversed(), utc, p))
  }

  @Test fun `ویژگی — ۶ تای آخر همیشه می‌مانند و هیچ‌وقت بیش از ۱۷ نمی‌ماند`() {
    val r = Random(7)
    repeat(200) {
      val n = r.nextInt(80)
      val base = t("2026-01-01T00:00")
      val s = (0 until n).map { Snap("x$it", base + (r.nextDouble() * 120 * 86_400_000L).toLong()) }
      val kept = BackupRetention.keep(s, kabul)
      val newest = s.sortedWith(compareByDescending<Snap> { it.at }.thenByDescending { it.id }).take(6).map { it.id }
      assertTrue(kept.containsAll(newest))
      assertTrue(kept.size <= 6 + 7 + 4)
      assertTrue(s.map { it.id }.containsAll(kept))
    }
  }
}
