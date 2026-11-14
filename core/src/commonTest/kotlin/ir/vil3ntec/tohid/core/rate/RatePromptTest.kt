package ir.vil3ntec.tohid.core.rate

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 *  RATE-01 — قاعده‌های برگهٔ «ستاره بدهید» (`RatePrompt`): آستانه‌ها، فاصله، سقفِ سه بار، «بعداً»، «دیگر نپرس»،
 *  «فروشگاه باز شد»، حالتِ آموزشی، وسطِ فروش، پس از خطا، آغازِ همین بار و ساختِ بی فروشگاه (سایت/پیش‌نمایش/کامپیوتر).
 */
class RatePromptTest {

  private val day = RatePrompt.DAY_MS
  private val t0 = 1_800_000_000_000L
  /** نصب روزِ ۰؛ امروز روزِ ۱۰ */
  private val now = t0 + 10 * day
  private val ready = RateState(firstSeenAt = t0)

  private fun moment(
    at: Long = now,
    target: RateTarget = RateTarget.PLAY,
    usage: Int = 25,
    minUsage: Int = RatePrompt.SHOP_MIN_SALES,
    startedAt: Long = at - 10 * 60_000L,
    used: Boolean = true,
    busy: Boolean = false,
    lastErrorAt: Long = 0,
    training: Boolean = false,
  ) = RateMoment(at, target, usage, minUsage, startedAt, used, busy, lastErrorAt, training)

  @Test fun asksWhenEveryRuleHolds() {
    assertNull(RatePrompt.block(ready, moment()))
    assertTrue(RatePrompt.shouldAsk(ready, moment()))
  }

  @Test fun directPreviewAndDesktopBuildsNeverAsk() {
    assertEquals(RateTarget.NONE, RateTarget.of("direct"))
    assertEquals(RateTarget.NONE, RateTarget.of(""))
    assertEquals(RateTarget.NONE, RateTarget.of("play", preview = true), "پیش‌نمایش از گیت‌هاب است، فروشگاه ندارد")
    assertEquals(RateTarget.PLAY, RateTarget.of("play"))
    assertEquals(RateTarget.BAZAAR, RateTarget.of("bazaar"))
    assertEquals(RateTarget.APP_STORE, RateTarget.of("appstore"))
    assertEquals(RateBlock.NO_STORE, RatePrompt.block(ready, moment(target = RateTarget.NONE)))
  }

  @Test fun needsSevenDaysSinceFirstSeen() {
    assertEquals(RateBlock.TOO_NEW, RatePrompt.block(RateState(), moment()), "هنوز هیچ روزی شمرده نشده")
    val fresh = RateState(firstSeenAt = now - 6 * day)
    assertEquals(RateBlock.TOO_NEW, RatePrompt.block(fresh, moment()))
    assertNull(RatePrompt.block(RateState(firstSeenAt = now - 7 * day), moment()))
    assertEquals(RateBlock.TOO_NEW, RatePrompt.block(RateState(firstSeenAt = now + day), moment()), "ساعتِ عقب‌رفته ⇒ هنوز نه")
  }

  @Test fun seenStartsTheCountOnlyOnce() {
    val first = RatePrompt.seen(RateState(), t0)
    assertEquals(t0, first.firstSeenAt)
    assertEquals(t0, RatePrompt.seen(first, now).firstSeenAt)
  }

  @Test fun needsRealUsagePerApp() {
    assertEquals(RateBlock.LOW_USAGE, RatePrompt.block(ready, moment(usage = 19)))
    assertNull(RatePrompt.block(ready, moment(usage = 20)))
    val shopper = moment(usage = 1, minUsage = RatePrompt.SHOPPER_MIN_ORDERS)
    assertEquals(RateBlock.LOW_USAGE, RatePrompt.block(ready, shopper))
    assertNull(RatePrompt.block(ready, shopper.copy(usage = 2)))
  }

  @Test fun neverOnSessionStart() {
    assertEquals(RateBlock.SESSION_START, RatePrompt.block(ready, moment(startedAt = now)), "همان لحظهٔ باز شدن")
    assertEquals(RateBlock.SESSION_START, RatePrompt.block(ready, moment(startedAt = now - 2 * 60_000L)), "دو دقیقه پس از باز شدن")
    assertEquals(RateBlock.SESSION_START, RatePrompt.block(ready, moment(startedAt = now - 60 * 60_000L, used = false)), "این بار هنوز هیچ فروشی نشده")
    assertNull(RatePrompt.block(ready, moment(startedAt = now - RatePrompt.SESSION_WARMUP_MS)))
  }

  @Test fun neverDuringSaleOrCheckout() {
    assertEquals(RateBlock.BUSY, RatePrompt.block(ready, moment(busy = true)))
  }

  @Test fun neverRightAfterAnError() {
    assertEquals(RateBlock.ERROR, RatePrompt.block(ready, moment(lastErrorAt = now - 60_000L)))
    assertEquals(RateBlock.ERROR, RatePrompt.block(ready, moment(lastErrorAt = now)))
    assertNull(RatePrompt.block(ready, moment(lastErrorAt = now - RatePrompt.ERROR_QUIET_MS)))
  }

  @Test fun neverInTrainingMode() {
    assertEquals(RateBlock.TRAINING, RatePrompt.block(ready, moment(training = true)))
  }

  @Test fun atMostThreeAsksThirtyDaysApart() {
    var s = ready
    var at = now
    repeat(RatePrompt.MAX_ASKS) { i ->
      assertNull(RatePrompt.block(s, moment(at = at)), "پرسشِ ${i + 1}")
      s = RatePrompt.chose(RatePrompt.shown(s, at), RateChoice.LATER, at)
      val soon = at + 29 * day
      assertEquals(RateBlock.SPACING, RatePrompt.block(s, moment(at = soon)))
      at += RatePrompt.SPACING_DAYS * day
    }
    assertEquals(RatePrompt.MAX_ASKS, s.asks)
    assertEquals(RateBlock.MAX_ASKS, RatePrompt.block(s, moment(at = at)))
  }

  @Test fun laterSnoozesFromTheTap() {
    val shown = RatePrompt.shown(ready, now)
    val tappedLater = RatePrompt.chose(shown, RateChoice.LATER, now + 5 * 60_000L)
    assertEquals(now + 5 * 60_000L, tappedLater.lastAskAt)
    assertEquals(1, tappedLater.asks, "«بعداً» یک پرسشِ تازه نیست")
    assertNull(tappedLater.stop)
    val back = now + 30 * day
    assertEquals(RateBlock.SPACING, RatePrompt.block(tappedLater, moment(at = back)))
    val after = now + 31 * day
    assertNull(RatePrompt.block(tappedLater, moment(at = after)))
  }

  @Test fun neverAgainStopsForGood() {
    val s = RatePrompt.chose(RatePrompt.shown(ready, now), RateChoice.NEVER, now)
    assertEquals(RateStop.NEVER, s.stop)
    val years = now + 900 * day
    assertEquals(RateBlock.STOPPED, RatePrompt.block(s, moment(at = years)))
  }

  @Test fun openingTheStoreStopsFutureAsks() {
    val s = RatePrompt.chose(RatePrompt.shown(ready, now), RateChoice.RATE, now)
    assertEquals(RateStop.STORE, s.stop)
    val later = now + 60 * day
    assertEquals(RateBlock.STOPPED, RatePrompt.block(s, moment(at = later)))
  }

  @Test fun stateRoundTripsAndBadValuesAreSafe() {
    val s = RateState(firstSeenAt = t0, asks = 2, lastAskAt = now, stop = RateStop.STORE)
    assertEquals(s, RatePrompt.decode(RatePrompt.encode(s)))
    assertEquals(ready, RatePrompt.decode(RatePrompt.encode(ready)))
    assertEquals(RateState(), RatePrompt.decode(null))
    assertEquals(RateState(), RatePrompt.decode("garbage"))
    assertEquals(RateState(), RatePrompt.decode("2|1|1|1|"), "قالبِ ناشناخته ⇒ از نو")
    assertEquals(RateStop.NEVER, RatePrompt.decode("1|1|1|1|whatever").stop, "ایستِ ناشناخته هرگز «دوباره بپرس» نیست")
    assertEquals(0, RatePrompt.decode("1|x|-4|y|").asks)
  }

  @Test fun storeLinksFollowTheOfficialIntents() {
    val b = RateLinks.bazaar("ir.vil3ntec.tohid")
    assertEquals(RateLink("android.intent.action.EDIT", "bazaar://details?id=ir.vil3ntec.tohid", "com.farsitel.bazaar"), b.first())
    assertEquals("https://cafebazaar.ir/app/ir.vil3ntec.tohid", b.last().uri)
    val p = RateLinks.playListing("ir.vil3ntec.tohid")
    assertEquals(RateLink("android.intent.action.VIEW", "market://details?id=ir.vil3ntec.tohid", "com.android.vending"), p.first())
    assertEquals(RateLink("android.intent.action.VIEW", "https://play.google.com/store/apps/details?id=ir.vil3ntec.tohid"), p.last())
  }

  @Test fun noRuleAsksForAStarCountOrGatesOnHappiness() {
    //  سه دکمه، همه برابر: «ستاره می‌دهم» همیشه فروشگاه است — هیچ گزینه‌ای که کاربرِ ناراضی را جای دیگری بفرستد نیست
    assertEquals(listOf(RateChoice.RATE, RateChoice.LATER, RateChoice.NEVER), RateChoice.entries.toList())
  }
}
