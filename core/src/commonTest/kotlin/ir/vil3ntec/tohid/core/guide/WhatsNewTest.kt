package ir.vil3ntec.tohid.core.guide

import ir.vil3ntec.tohid.core.guide.WhatsNew.Kind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 *  OPS-05 — «تازه‌ها» از `docs/WHATS-NEW-fa.md`: خواندنِ پرونده، «سطرهای ندیده از آخرین بار» (۶۰ ⇐ ۶۴ = ۶۱ تا ۶۴ با هم)،
 *  نمایشِ یک‌باره و برداشته‌ای که هرگز بی «به‌جایش» نیست. پروندهٔ واقعی روی دیسک: `WhatsNewFileTest` (jvmTest).
 */
class WhatsNewTest {

  private val sample = """
    # تازه‌ها
    شرح و قاعده‌ها؛ سطرِ زیر پیش از نخستین بخش است و کنار می‌رود:
    - تازه: این سطرِ شرح است، نه یادداشت

    ## 3.2 · بی شماره (تازه‌تر از همه)
    - تازه: کارِ تازهٔ بی شماره

    ## 3.2 · ۶۴
    <!-- shipped: preview=64 beta=1911 -->
    - تغییر: کارِ ۶۴

    ## 3.2 · ۶۳
    <!-- shipped: preview=63 beta=1905 -->
    - تازه: کارِ ۶۳
    - رفع: درست شدنِ ۶۳

    ## 3.2 · ۶۲
    <!-- shipped: preview=62 beta=1900 -->
    - تازه: کارِ ۶۲

    ## 3.2 · ۶۱
    <!--
      توضیحِ چندسطری
    -->
    <!-- shipped: preview=61 beta=1896 -->
    - تازه: کارِ ۶۱
    - برداشته: شکلِ قدیم ⇐ به‌جایش: شکلِ تازه

    ## 3.2 · ۶۰
    <!-- shipped: preview=60 beta=1890 -->
    - تازه: کارِ ۶۰
  """.trimIndent()

  private val log = WhatsNew.parse(sample)
  private val all = log.sections
  private fun texts(s: List<WhatsNew.Section>) = s.flatMap { sec -> sec.entries.map { it.text } }

  @Test
  fun parsesSectionsKindsInsteadAndShippedSkippingThePreamble() {
    assertEquals(emptyList(), log.problems)
    assertEquals(listOf("بی شماره (تازه‌تر از همه)", "۶۴", "۶۳", "۶۲", "۶۱", "۶۰"), all.map { it.title })
    assertTrue(all.all { it.line == "3.2" })
    assertTrue("این سطرِ شرح است، نه یادداشت" !in texts(all))
    assertEquals(mapOf("preview" to 64, "beta" to 1911), all[1].shipped)
    assertEquals(emptyMap(), all[0].shipped)
    assertEquals(listOf(Kind.NEW, Kind.FIXED), all[2].entries.map { it.kind })
    //  برداشته اولِ بخشِ خودش، با «به‌جایش»
    val removed = all[4].entries.first()
    assertEquals(Kind.REMOVED, removed.kind)
    assertEquals("شکلِ قدیم", removed.text)
    assertEquals("شکلِ تازه", removed.instead)
  }

  @Test
  fun badLinesAreProblemsAndNeverShown() {
    val bad = WhatsNew.parse(
      """
      ## 3.2 · خوب
      - تازه: درست
      - برداشته: بی جای تازه
      - تازه: با به‌جایش ⇐ به‌جایش: نه
      - شاید: نوعِ ناشناخته
      متنِ آزاد
      - تازه: درست
      - تازه:
      <!-- shipped: preview=x -->
      ## بی نسخه
      - تازه: زیرِ سرخطِ بد
      ## 3.2 · خالی
      """.trimIndent(),
    )
    assertEquals(9, bad.problems.size, bad.problems.joinToString("\n"))
    //  فقط «درست» (یک بار) به کاربر می‌رسد؛ بخشِ سرخطِ بد هیچ
    assertEquals(listOf("درست"), texts(bad.sections))
    assertEquals(listOf("3.2"), bad.sections.map { it.line })
    assertTrue(WhatsNew.parse("فقط شرح، بی بخش").problems.isNotEmpty())
  }

  @Test
  fun keyIsStableAndIgnoresSpacingButNotWords() {
    val k = WhatsNew.keyOf(Kind.NEW, "کارِ  تازه ")
    assertEquals(k, WhatsNew.keyOf(Kind.NEW, "کارِ تازه"))
    assertEquals(16, k.length)
    assertTrue(k != WhatsNew.keyOf(Kind.FIXED, "کارِ تازه"))
    assertTrue(k != WhatsNew.keyOf(Kind.NEW, "کارِ تازهٔ دیگر"))
    //  مقدارِ ثابت: گوشی، کامپیوتر و آیفون همان کلید را می‌سازند (FNV-1a ِ ۶۴بیتی)
    assertEquals("e878dfc8e18ec3e9", WhatsNew.keyOf(Kind.NEW, ""))
    assertEquals(WhatsNew.decode(WhatsNew.encode(listOf(k))), setOf(k))
    assertEquals(emptySet(), WhatsNew.decode("نه,123,"))
  }

  @Test
  fun freshInstallShowsNothingAndMarksEverythingSeen() {
    val p = WhatsNew.plan(all, stored = null, legacySeen = null, current = "3.2.64-preview")
    assertEquals(emptyList(), p.show)
    val stored = assertNotNull(p.storeNow)
    assertEquals(emptyList(), WhatsNew.plan(all, stored, null, "3.2.64-preview").show)
  }

  @Test
  fun previewSixtyToSixtyFourShowsSixtyOneToSixtyFourTogether() {
    //  گوشیِ صاحب‌کار: سامانهٔ پیشین «3.2.60» را دیده‌شده گذاشته بود؛ برنامهٔ تازه ۶۱ تا ۶۴ را دارد
    val upTo64 = all.drop(1)
    val p = WhatsNew.plan(upTo64, stored = null, legacySeen = "3.2.60", current = "3.2.64-preview")
    assertEquals(listOf("۶۴", "۶۳", "۶۲", "۶۱"), p.show.map { it.title })
    assertNull(p.storeNow, "ذخیره پس از «فهمیدم»، نه پیش از دیدن")
    //  بخشِ تازه‌ترِ بی شماره هم می‌آید
    assertEquals("بی شماره (تازه‌تر از همه)", WhatsNew.plan(all, null, "3.2.60", "3.2.65-preview").show.first().title)
  }

  @Test
  fun betaUsesItsOwnBuildNumbers() {
    //  همان کد در آزمایشی ۳٫۲٫۱۹۰۵ ساخته شد ⇒ ۶۴ و بخشِ بی شماره
    val p = WhatsNew.plan(all, stored = null, legacySeen = "3.2.1905", current = "3.2.1920")
    assertEquals(listOf("بی شماره (تازه‌تر از همه)", "۶۴"), p.show.map { it.title })
    //  پایدارِ کهنه (۳٫۲٫۳۳۷) زیرِ همهٔ شماره‌ها ⇒ همه
    assertEquals(all.size, WhatsNew.plan(all, null, "3.2.337", "3.2.1920").show.size)
    //  شماره‌ای که هیچ بخشی ندارد (کامپیوتر ۱٫۰) ⇒ فقط تازه‌ترین بخش
    assertEquals(listOf(all.first().title), WhatsNew.plan(all, null, "1.0.1600", "1.0.1674").show.map { it.title })
    //  به‌روز بودن ⇒ هیچ، و همه دیده‌شده همان دم
    val same = WhatsNew.plan(all.drop(1), null, "3.2.64", "3.2.64-preview")
    assertEquals(emptyList(), same.show)
    assertNotNull(same.storeNow)
  }

  @Test
  fun onlyEntriesNotSeenOnThisDevice() {
    //  دستگاه ۶۲ را دیده بود؛ سطرِ تازه‌ای هم بعداً زیرِ بخشِ ۶۳ آمد
    val seenAt62 = WhatsNew.ack(all.drop(3), stored = null)
    val p = WhatsNew.plan(all, seenAt62, legacySeen = "3.2.10", current = "3.2.66-preview")
    assertEquals(listOf("کارِ تازهٔ بی شماره", "کارِ ۶۴", "کارِ ۶۳", "درست شدنِ ۶۳"), texts(p.show))
    val extra = all.map { s -> if (s.title == "۶۲") s.copy(entries = s.entries + WhatsNew.Entry(Kind.FIXED, "سطرِ دیرآمده")) else s }
    assertEquals(listOf("سطرِ دیرآمده"), texts(WhatsNew.plan(extra, WhatsNew.ack(all, null), null, "3.2.66").show))
  }

  @Test
  fun shownOnceThenNeverAgainAndDowngradeShowsNothing() {
    val first = WhatsNew.plan(all.drop(2), WhatsNew.ack(all.drop(3), null), null, "3.2.63-preview")
    assertEquals(listOf("۶۳"), first.show.map { it.title })
    //  «فهمیدم»
    val stored = WhatsNew.ack(all.drop(2), WhatsNew.ack(all.drop(3), null))
    assertEquals(emptyList(), WhatsNew.plan(all.drop(2), stored, null, "3.2.63-preview").show)
    //  به‌روزرسانیِ بعدی فقط تازه‌ها
    val newer = WhatsNew.ack(all, null)
    //  برگشت به ۶۲ هیچ نشان نمی‌دهد و «فهمیدم» ِ آن‌جا دیده‌شدهٔ تازه‌تر را کم نمی‌کند
    assertEquals(emptyList(), WhatsNew.plan(all.drop(3), newer, null, "3.2.62-preview").show)
    assertEquals(WhatsNew.decode(newer), WhatsNew.decode(WhatsNew.ack(all.drop(3), newer)))
  }

  @Test
  fun buildNumberAndSuffixAreIgnoredForTheLegacyVersion() {
    assertEquals(listOf(2, 14, 5), WhatsNew.base("2.14.5.1234-preview"))
    assertEquals("3.2.64", WhatsNew.baseText("3.2.64-preview"))
    assertNull(WhatsNew.base("2.14"))
    assertNull(WhatsNew.base("2.x.5"))
    assertEquals(WhatsNew.CHANNEL_PREVIEW, WhatsNew.channel("3.2.64-preview"))
    assertEquals(WhatsNew.CHANNEL_BETA, WhatsNew.channel("3.2.1916"))
    assertEquals("3.2.61", WhatsNew.versionOf(all[4], "3.2.70-preview"))
    assertEquals("3.2.1896", WhatsNew.versionOf(all[4], "3.2.1930"))
    assertNull(WhatsNew.versionOf(all[0], "3.2.70-preview"))
    //  نسخهٔ پیشینِ بد ⇒ مثلِ نصبِ تازه
    assertEquals(emptyList(), WhatsNew.plan(all, null, "nonsense", "3.2.64").show)
  }

  /** «هیچ قابلیتی بی‌صدا حذف نمی‌شود» (#865): برداشته شدنِ شکلِ قدیم در «تازه‌ها» ی همراهِ برنامه، با جای تازه */
  @Test
  fun bundledLogParsesAndAnnouncesTheOldLookRemovalWithWhereToGoNow() {
    val bundled = WhatsNew.bundled
    assertEquals(emptyList(), bundled.problems)
    assertTrue(bundled.sections.isNotEmpty())
    val removed = bundled.sections.flatMap { it.entries }.single { it.kind == Kind.REMOVED && it.text.contains("شکلِ قدیم") }
    assertTrue(removed.text.contains("رابطِ تازه"), removed.text)
    assertTrue(!removed.instead.isNullOrBlank())
    //  گوشیِ صاحب‌کار (پیش‌نمایشِ ۶۰): همین سطر اولِ بخشِ ۶۱ دیده می‌شود
    val p = WhatsNew.plan(bundled.sections, null, "3.2.60", "3.2.99-preview")
    val s61 = p.show.single { WhatsNew.versionOf(it, "3.2.99-preview") == "3.2.61" }
    assertEquals(removed, s61.entries.first())
    //  دری همان فارسی با لایهٔ واژه، خالی نه
    assertTrue(removed.textIn("prs").isNotBlank() && !removed.insteadIn("prs").isNullOrBlank())
  }
}
