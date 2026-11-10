package ir.vil3ntec.tohid.core.guide

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 *  OPS-06 — راهنمای هر صفحه: هر ۱۶ صفحه متنِ آمادهٔ فارسی و انگلیسی دارد (O-14 ب)، جای خالی هرگز به
 *  کاربر نشان داده نمی‌شود، ردیفِ READY ِ ناقص هم نه، و فهرستِ «چه چیزی هنوز مانده» (ویدیو) از خودِ داده می‌آید.
 *  نام‌های میانِ «» با جدولِ `Str*` در `ScreenGuidesTextTest` ِ اندروید سنجیده می‌شوند.
 */
class ScreenGuidesTest {

  private val ready = ScreenGuide(
    screen = "sale", title = GuideText("صندوقِ فروش", "Sale"),
    steps = listOf(GuideText("کالا را اسکن کنید", "Scan the item"), GuideText("ثبتِ فروش را بزنید")),
    video = GuideVideo("/guides/sale-basics.mp4", 45, 3_000_000),
    status = GuideStatus.READY,
  )

  @Test
  fun everyScreenHasOneUniqueReadyRowInPersianAndEnglish() {
    val ids = ScreenGuides.ALL.map { it.screen }
    assertEquals(16, ids.size)
    assertEquals(ids.size, ids.toSet().size, "شناسهٔ تکراری")
    for (g in ScreenGuides.ALL) assertEquals(emptyList(), ScreenGuides.problems(g), g.screen)
    //  میان‌برهای Ctrl+1..9 ِ کامپیوتر (`AppNav.SHORTCUTS`) همه راهنما دارند
    for (s in listOf("dashboard", "sale", "debtors", "warehouse", "products", "sales", "purchasing", "reports", "expenses")) {
      assertTrue(s in ids, s)
    }
    for (g in ScreenGuides.ALL) {
      assertEquals(GuideStatus.READY, g.status, g.screen)
      assertTrue(g.title.fa.isNotBlank() && g.title.en.isNotBlank(), g.screen)
      assertTrue(g.steps.size >= 3, "${g.screen}: کمتر از ۳ گام")
      g.steps.forEachIndexed { i, s ->
        assertTrue(s.fa.isNotBlank() && s.en.isNotBlank(), "${g.screen} #${i + 1}")
        //  انگلیسی بی حرفِ فارسی (؋ نمادِ پول است)
        assertTrue(s.en.none { it in '\u0600'..'\u06FF' && it != '؋' }, "${g.screen} #${i + 1}: ${s.en}")
        //  پشتوی گام هنوز نیست ⇒ همان فارسی، مثلِ هر نوشتهٔ بی پشتوی جدول‌ها
        assertEquals(s.fa, s.of("ps"), "${g.screen} #${i + 1}")
      }
    }
    //  ویدیو هنوز هیچ صفحه‌ای ندارد ⇒ همهٔ ۱۶ ردیف در فهرستِ مانده
    assertEquals(ScreenGuides.ALL.size, ScreenGuides.pending().size)
  }

  @Test
  fun readyScreensShowAndPlaceholderAndUnknownShowNothing() {
    for (g in ScreenGuides.ALL) assertEquals(g, ScreenGuides.forScreen(g.screen), g.screen)
    //  ردیفِ جای خالی (صفحهٔ تازه‌ای که هنوز متن ندارد) هیچ چیز نشان نمی‌دهد
    val draft = ScreenGuide("loyalty", GuideText("وفاداری", "Loyalty"))
    assertEquals(emptyList(), ScreenGuides.problems(draft))
    assertNull(ScreenGuides.forScreen("loyalty", ScreenGuides.ALL + draft))
    assertNull(ScreenGuides.forScreen(null))
    assertNull(ScreenGuides.forScreen("nope"))
  }

  @Test
  fun v3RouteBaseMapsToTheGuideId() {
    assertEquals("dashboard", ScreenGuides.screenOf("home"))
    assertEquals("warehouse", ScreenGuides.screenOf("stock"))
    for (g in ScreenGuides.ALL) if (g.screen != "dashboard" && g.screen != "warehouse") assertEquals(g.screen, ScreenGuides.screenOf(g.screen))
    //  مسیرِ بی راهنما همان می‌ماند و چیزی نشان نمی‌دهد
    assertEquals("chat", ScreenGuides.screenOf("chat"))
    assertNull(ScreenGuides.forScreen(ScreenGuides.screenOf("chat")))
  }

  @Test
  fun readyRowIsCheckedBeforeItIsShown() {
    assertEquals(emptyList(), ScreenGuides.problems(ready))
    val bad = listOf(
      ready.copy(steps = emptyList()),
      ready.copy(steps = List(ScreenGuides.MAX_STEPS + 1) { GuideText("گام") }),
      ready.copy(steps = listOf(GuideText(""))),
      ready.copy(steps = listOf(GuideText("x".repeat(ScreenGuides.MAX_STEP + 1)))),
      ready.copy(steps = listOf(GuideText("گام", ps = "x".repeat(ScreenGuides.MAX_STEP + 1)))),
      ready.copy(video = GuideVideo("https://youtube.com/x", 45, 1)),
      ready.copy(video = GuideVideo("/guides/../secret.mp4", 45, 1)),
      ready.copy(video = GuideVideo("/guides/a.mp4", ScreenGuides.MAX_VIDEO_SECONDS + 1, 1)),
      ready.copy(video = GuideVideo("/guides/a.mp4", ScreenGuides.MIN_VIDEO_SECONDS - 1, 1)),
      ready.copy(video = GuideVideo("/guides/a.mp4", 45, ScreenGuides.MAX_VIDEO_BYTES + 1)),
      ready.copy(video = GuideVideo("/guides/a.mp4", 45, 0)),
      ready.copy(title = GuideText("")),
      ready.copy(screen = "Sale Screen"),
    )
    for (g in bad) assertTrue(ScreenGuides.problems(g).isNotEmpty(), g.toString())
    //  بی ویدیو هم درست است (ویدیو اختیاری)
    assertEquals(emptyList(), ScreenGuides.problems(ready.copy(video = null)))
    //  آماده و درست ⇒ نشان داده می‌شود؛ آماده ولی ناقص ⇒ هیچ
    assertEquals(ready, ScreenGuides.forScreen("sale", listOf(ready)))
    for (g in bad) assertNull(ScreenGuides.forScreen(g.screen, listOf(g)), g.toString())
    assertNull(ScreenGuides.forScreen("sale", listOf(ready.copy(status = GuideStatus.PLACEHOLDER))))
  }

  @Test
  fun textFallsBackToPersian() {
    val t = GuideText("فارسی", en = "English")
    assertEquals("English", t.of("en"))
    assertEquals("فارسی", t.of("ps"))
    assertEquals("فارسی", t.of("fa"))
    assertEquals("فارسی", t.of("xx"))
  }
}
