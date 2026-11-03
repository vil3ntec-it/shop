package ir.vil3ntec.tohid.core.storefront

import ir.vil3ntec.tohid.core.calendar.CivilDays
import ir.vil3ntec.tohid.core.storefront.StorefrontBanners.Draft
import ir.vil3ntec.tohid.core.storefront.StorefrontBanners.Err
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** WEB-24 — فرمِ بنرِ برنامهٔ مدیریت: همان قاعده‌های `storefront-extras.parseBanner` ِ سرور */
class StorefrontBannersTest {

  private val B = StorefrontBanners
  private val day = 24L * 60 * 60 * 1000
  private val epoch = CivilDays.jdnOfIso("1970-01-01")!!

  /** آغازِ روز به وقتِ کابل (UTC+4:30، بی تابستانی) */
  private val kabul: (String) -> Long? = { iso -> CivilDays.jdnOfIso(iso)?.let { (it - epoch) * day - 270L * 60 * 1000 } }
  private val dayOf: (Long) -> String = { ms -> CivilDays.isoOf(epoch + ((ms + 270L * 60 * 1000) / day).toInt()) }
  private val Z1 = "01J0000000000000000000ZONE"
  private val Z2 = "01J0000000000000000000ZTWO"

  private fun ok(vararg d: Draft) = B.check(d.toList(), setOf(Z1, Z2), kabul)

  @Test fun rangeIsWholeShopDaysBothEndsIncluded() {
    val c = ok(Draft(title = " تخفیفِ هفته ", body = "همه‌چیز", startDay = "2026-11-02", endDay = "2026-11-08"))
    val b = assertNotNull(c.banners).single()
    assertEquals("تخفیفِ هفته", b.title)
    assertEquals(kabul("2026-11-02"), b.startAt)
    assertEquals(kabul("2026-11-09"), b.endAt, "پایان = آغازِ روزِ پس از «تا»")
    assertEquals(7 * day, b.endAt - b.startAt)
    assertEquals(listOf<String>(), b.zoneIds, "خالی = همه‌جا")
    assertEquals("none", b.linkType)
    assertEquals(0, b.sort)
    //  یک روز: «از» = «تا»
    assertEquals(day, ok(Draft(title = "امروز", startDay = "2026-11-02", endDay = "2026-11-02")).banners!!.single().let { it.endAt - it.startAt })
  }

  @Test fun serverLimitsAreCheckedBeforeSending() {
    val c = ok(
      Draft(title = "", startDay = "2026-11-02", endDay = "2026-11-01"),
      Draft(title = "ب".repeat(81), body = "م".repeat(201), startDay = "۱۴۰۵", endDay = "2026-11-02", sort = "101"),
      Draft(title = "بلند", startDay = "2026-01-01", endDay = "2026-04-03"),
    )
    assertNull(c.banners)
    assertEquals(Err.TITLE_REQUIRED, c.errors["b0.title"])
    assertEquals(Err.END_BEFORE_START, c.errors["b0.end"])
    assertEquals(Err.TITLE_LONG, c.errors["b1.title"])
    assertEquals(Err.BODY_LONG, c.errors["b1.body"])
    assertEquals(Err.START_BAD, c.errors["b1.start"])
    assertEquals(Err.SORT_BAD, c.errors["b1.sort"])
    //  ۲۰۲۶-۰۱-۰۱ تا ۲۰۲۶-۰۴-۰۲ = ۹۲ روز ⇒ درست؛ یک روز بیشتر ⇒ خطا (همان ۹۲ روزِ سرور)
    assertEquals(Err.SPAN_TOO_LONG, c.errors["b2.end"])
    assertEquals(92, B.days("2026-01-01", "2026-04-02"))
    assertNotNull(ok(Draft(title = "سقف", startDay = "2026-01-01", endDay = "2026-04-02")).banners)
    val many = B.check(List(StorefrontBanners.MAX + 1) { Draft(title = "ب$it", startDay = "2026-11-02", endDay = "2026-11-02") }, emptySet(), kabul)
    assertEquals(Err.TOO_MANY, many.errors["list"])
    //  اولویت با رقمِ فارسی
    assertEquals(50, ok(Draft(title = "الف", startDay = "2026-11-02", endDay = "2026-11-02", sort = "۵۰")).banners!!.single().sort)
  }

  @Test fun linkNeedsItsValue() {
    val c = ok(
      Draft(title = "کالا", link = "product", startDay = "2026-11-02", endDay = "2026-11-02"),
      Draft(title = "دسته", link = "category", category = "  ", startDay = "2026-11-02", endDay = "2026-11-02"),
      Draft(title = "دستهٔ بلند", link = "category", category = "د".repeat(81), startDay = "2026-11-02", endDay = "2026-11-02"),
      Draft(title = "کالای بد", link = "product", productId = "a b", startDay = "2026-11-02", endDay = "2026-11-02"),
    )
    assertEquals(Err.PRODUCT_REQUIRED, c.errors["b0.link"])
    assertEquals(Err.CATEGORY_REQUIRED, c.errors["b1.link"])
    assertEquals(Err.CATEGORY_LONG, c.errors["b2.link"])
    assertEquals(Err.PRODUCT_REQUIRED, c.errors["b3.link"])
    val good = ok(
      Draft(title = "روغن", link = "product", productId = "oil", category = "بی‌اثر", startDay = "2026-11-02", endDay = "2026-11-02"),
      Draft(title = "خوراکی", link = "category", category = " خوراکی ", productId = "بی‌اثر", startDay = "2026-11-02", endDay = "2026-11-02"),
      Draft(title = "ناشناخته", link = "zzz", productId = "oil", startDay = "2026-11-02", endDay = "2026-11-02"),
    ).banners!!
    assertEquals(listOf("product" to "oil", "category" to "خوراکی", "none" to ""), good.map { it.linkType to it.linkValue })
  }

  @Test fun bannerOnlyForRemovedZonesIsAnErrorNeverSilentlyEverywhere() {
    val gone = "01J00000000000000000000OLD"
    val c = ok(Draft(title = "کهنه", startDay = "2026-11-02", endDay = "2026-11-02", zoneIds = setOf(gone)))
    assertEquals(Err.ZONES_GONE, c.errors["b0.zones"])
    //  یک ناحیهٔ زنده هم کنارش باشد ⇒ درست؛ شناسهٔ کهنه دست‌نخورده می‌رود (سرور فقط نمایش را می‌سنجد)
    val b = ok(Draft(title = "دو", startDay = "2026-11-02", endDay = "2026-11-02", zoneIds = setOf(Z2, gone, Z1))).banners!!.single()
    assertEquals(listOf(Z1, Z2, gone).sorted(), b.zoneIds)
  }

  @Test fun loadedBannerKeepsServerTimesUnlessItsDayChanged() {
    //  بنری که برنامهٔ دیگر با ساعتِ ناگرد ساخته: ۱۰:۱۵ تا ۱۸:۴۰ ِ همان روز
    val s = kabul("2026-11-02")!! + 10 * 3600_000L + 15 * 60_000L
    val e = kabul("2026-11-02")!! + 18 * 3600_000L + 40 * 60_000L
    val loaded = StorefrontBanners.Banner("ساعتی", "", "category", "خوراکی", listOf(Z1), s, e, 7)
    val d = B.draftOf(loaded, dayOf)
    assertEquals("2026-11-02" to "2026-11-02", d.startDay to d.endDay)
    assertEquals("خوراکی", d.category)
    assertEquals(loaded, ok(d).banners!!.single(), "دست‌نخورده ⇒ همان بنر، بایت‌به‌بایت")
    //  پایانِ دقیقِ نیمه‌شب (روزِ بعد ۰۰:۰۰) هنوز روزِ پیش نشان داده می‌شود
    assertEquals("2026-11-08", B.draftOf(loaded.copy(endAt = kabul("2026-11-09")!!), dayOf).endDay)
    //  فقط «تا» عوض شد ⇒ آغاز همان ۱۰:۱۵، پایان آخرِ روزِ تازه
    val moved = ok(d.copy(endDay = "2026-11-04")).banners!!.single()
    assertEquals(s, moved.startAt)
    assertEquals(kabul("2026-11-05"), moved.endAt)
  }

  @Test fun freshCardIsTodayToAWeekAndWhenOfMatchesServerWindow() {
    val f = B.fresh("2026-11-02")
    assertEquals("2026-11-02" to "2026-11-08", f.startDay to f.endDay)
    assertTrue(f.zoneIds.isEmpty())
    val b = ok(f.copy(title = "تازه")).banners!!.single()
    assertEquals(StorefrontBanners.When.LATER, B.whenOf(b, b.startAt - 1))
    assertEquals(StorefrontBanners.When.LIVE, B.whenOf(b, b.startAt))
    assertEquals(StorefrontBanners.When.ENDED, B.whenOf(b, b.endAt), "پایان خودش بیرون است (`end_at > now` ِ سرور)")
  }
}
