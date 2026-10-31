package ir.vil3ntec.tohid.core.guide

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 *  OPS-06 — ساختارِ راهنمای هر صفحه: جای خالی هرگز به کاربر نشان داده نمی‌شود، ردیفِ READY ِ
 *  ناقص هم نه، و فهرستِ «چه چیزی را صاحب محصول بدهد» از خودِ داده می‌آید.
 */
class ScreenGuidesTest {

  private val ready = ScreenGuide(
    screen = "sale", title = GuideText("صندوقِ فروش", "Sale"),
    steps = listOf(GuideText("کالا را اسکن کنید", "Scan the item"), GuideText("ثبتِ فروش را بزنید")),
    video = GuideVideo("/guides/sale-basics.mp4", 45, 3_000_000),
    status = GuideStatus.READY,
  )

  @Test
  fun everyScreenHasOneUniqueValidRowAndTodayAllArePlaceholders() {
    val ids = ScreenGuides.ALL.map { it.screen }
    assertEquals(ids.size, ids.toSet().size, "شناسهٔ تکراری")
    for (g in ScreenGuides.ALL) assertEquals(emptyList(), ScreenGuides.problems(g), g.screen)
    //  میان‌برهای Ctrl+1..9 ِ کامپیوتر (`AppNav.SHORTCUTS`) همه راهنما دارند
    for (s in listOf("dashboard", "sale", "debtors", "warehouse", "products", "sales", "purchasing", "reports", "expenses")) {
      assertTrue(s in ids, s)
    }
    assertTrue(ScreenGuides.ALL.all { it.status == GuideStatus.PLACEHOLDER })
    assertEquals(ScreenGuides.ALL.size, ScreenGuides.pending().size)
  }

  @Test
  fun placeholderAndUnknownScreensShowNothing() {
    for (g in ScreenGuides.ALL) assertNull(ScreenGuides.forScreen(g.screen), g.screen)
    assertNull(ScreenGuides.forScreen(null))
    assertNull(ScreenGuides.forScreen("nope"))
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
